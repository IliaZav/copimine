package me.copimine.client;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import me.copimine.protocol.BridgeControlBodyCodec;
import me.copimine.protocol.BridgeEnvelope;
import me.copimine.protocol.BridgeEnvelopeCodec;
import me.copimine.protocol.BridgeRequestCodec;
import me.copimine.protocol.BridgeSigningPolicy;
import me.copimine.protocol.EndEventBridgeBodyCodec;
import me.copimine.protocol.NarcoticsBridgeBodyCodec;

/**
 * Client-side v4 authentication and sender handshake. Fabric supplies the executor and transport;
 * the class keeps the trust and connection-fencing rules independently testable.
 */
public final class ClientBridgeProtocolV4 {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long CONTROL_HELLO_REOPEN_DELAY_MILLIS = 3_000L;
    private static final long INITIAL_HELLO_ACK_RECOVERY_DELAY_MILLIS = 31_000L;
    private static final long FIRST_RETRY_DELAY_MILLIS = 1_000L;
    private static final long MAX_RETRY_DELAY_MILLIS = 30_000L;
    private static final long SENDER_HEARTBEAT_TIMEOUT_NANOS = 20_000_000_000L;
    private static final int MAX_NARCOTICS_RECEIPTS = 256;

    private final ClientBridgeConnectionState connectionState;
    private final PublicKey pinnedKey;
    private final Transport transport;
    private final MutationHandler mutationHandler;
    private final ClientBridgeReceiveRateLimiter receiveRateLimiter;
    private final EnvelopeDecoder envelopeDecoder;
    private final EnumMap<BridgeRequestCodec.Sender, SenderFlow> senders =
            new EnumMap<>(BridgeRequestCodec.Sender.class);
    private final EnumMap<BridgeRequestCodec.Sender, SerialOutbox> outboxes =
            new EnumMap<>(BridgeRequestCodec.Sender.class);
    private final LinkedHashMap<Long, NarcoticsReceipt> narcoticsReceipts = new LinkedHashMap<>();

    private EventContext activeEventContext;
    private boolean endEventReassertionRequired;
    private long narcoticsCommandHighWater;
    private long lastControlHelloSentAt;
    private long firstControlHelloSentAt;
    private byte[] controlHelloBytes;

    public ClientBridgeProtocolV4(ClientBridgeConnectionState connectionState, PublicKey pinnedKey,
            Transport transport, MutationHandler mutationHandler) {
        this(connectionState, pinnedKey, transport, mutationHandler, new ClientBridgeReceiveRateLimiter(),
                BridgeEnvelopeCodec::decode);
    }

    ClientBridgeProtocolV4(ClientBridgeConnectionState connectionState, PublicKey pinnedKey,
            Transport transport, MutationHandler mutationHandler,
            ClientBridgeReceiveRateLimiter receiveRateLimiter, EnvelopeDecoder envelopeDecoder) {
        this.connectionState = Objects.requireNonNull(connectionState, "connectionState");
        this.pinnedKey = Objects.requireNonNull(pinnedKey, "pinnedKey");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.mutationHandler = Objects.requireNonNull(mutationHandler, "mutationHandler");
        this.receiveRateLimiter = Objects.requireNonNull(receiveRateLimiter, "receiveRateLimiter");
        this.envelopeDecoder = Objects.requireNonNull(envelopeDecoder, "envelopeDecoder");
        for (BridgeRequestCodec.Sender sender : BridgeRequestCodec.Sender.values()) {
            senders.put(sender, new SenderFlow());
            outboxes.put(sender, new SerialOutbox());
        }
    }

    /** Opens a fresh local binding and discards all state from an earlier play connection. */
    public synchronized ClientBridgeConnectionState.Connection openConnection(UUID playerUuid) {
        receiveRateLimiter.reset();
        ClientBridgeConnectionState.Connection connection = connectionState.open(playerUuid);
        clearProtocolState(true);
        return connection;
    }

    /** Invalidates the prior v3 session while retaining the play-connection nonce and replay history. */
    public synchronized void invalidateSession() {
        connectionState.invalidateVerifiedSession();
        clearSenderFlows(false);
        clearNarcoticsLedger();
        endEventReassertionRequired = activeEventContext != null;
        mutationHandler.clear(BridgeRequestCodec.Sender.END_EVENT);
        mutationHandler.clear(BridgeRequestCodec.Sender.NARCOTICS);
        controlHelloBytes = null;
        lastControlHelloSentAt = 0L;
        firstControlHelloSentAt = 0L;
    }

    /** Starts v4 only after the existing v3 hello/PING exchange accepted this session UUID. */
    public synchronized boolean acceptVerifiedSession(UUID sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        if (connectionState.verifiedSession().filter(sessionId::equals).isPresent()) {
            return false;
        }
        connectionState.verifiedSessionChanged(sessionId);
        clearSenderFlows(false);
        clearNarcoticsLedger();
        endEventReassertionRequired = activeEventContext != null;
        UUID helloId = randomUuid();
        connectionState.setControlHelloId(helloId);
        controlHelloBytes = null;
        firstControlHelloSentAt = 0L;
        sendControlHello();
        return true;
    }

    /** Sends or retransmits the one idempotent control hello for the current session. */
    public synchronized boolean sendControlHello() {
        Optional<ClientBridgeConnectionState.Connection> connection = connectionState.currentConnection();
        Optional<UUID> session = connectionState.verifiedSession();
        Optional<UUID> helloId = connectionState.controlHelloId();
        if (connection.isEmpty() || session.isEmpty() || helloId.isEmpty()) {
            return false;
        }
        if (controlHelloBytes == null) {
            controlHelloBytes = BridgeRequestCodec.encode(new BridgeRequestCodec.ControlHello(
                    connection.get().nonce(), connection.get().playerUuid(), session.get(), uuidBytes(helloId.get())));
        }
        if (!transport.canSendRequest() || !transport.sendRequest(controlHelloBytes.clone())) {
            lastControlHelloSentAt = transport.currentTimeMillis();
            return false;
        }
        long sentAt = transport.currentTimeMillis();
        if (firstControlHelloSentAt == 0L) {
            firstControlHelloSentAt = sentAt;
        }
        lastControlHelloSentAt = sentAt;
        return true;
    }

    /**
     * Verifies the exact raw envelope before any body decoder is called, then queues a second
     * verification and all body/state work on the Minecraft client thread.
     */
    synchronized boolean onEnvelope(BridgeV4Payload payload, UUID currentPlayer, long nowMillis) {
        if (payload == null || !receiveRateLimiter.tryAcquire(System.nanoTime())) {
            return false;
        }
        return processAdmittedEnvelope(payload.exactEnvelopeBytesForProtocol(), currentPlayer, nowMillis);
    }

    public synchronized boolean onEnvelope(byte[] exactEnvelopeBytes, UUID currentPlayer, long nowMillis) {
        if (exactEnvelopeBytes == null || !receiveRateLimiter.tryAcquire(System.nanoTime())) {
            return false;
        }
        return processAdmittedEnvelope(exactEnvelopeBytes, currentPlayer, nowMillis);
    }

    private boolean processAdmittedEnvelope(byte[] exactEnvelopeBytes, UUID currentPlayer, long nowMillis) {
        if (exactEnvelopeBytes.length == 0
                || exactEnvelopeBytes.length > BridgeEnvelopeCodec.MAX_ENVELOPE_BYTES) {
            return false;
        }
        byte[] raw = exactEnvelopeBytes.clone();
        final BridgeEnvelope envelope;
        try {
            envelope = envelopeDecoder.decode(raw);
        } catch (IllegalArgumentException malformed) {
            return false;
        }
        if (BridgeSigningPolicy.verifyV4(pinnedKey, envelope, nowMillis)
                != BridgeSigningPolicy.VerificationResult.VALID
                || !matchesReceiveBinding(envelope.metadata(), currentPlayer)) {
            return false;
        }

        final ClientBridgeConnectionState.QueueTicket ticket;
        try {
            ticket = connectionState.captureQueueTicket();
        } catch (IllegalStateException inactive) {
            return false;
        }
        BridgeRequestCodec.Sender sender = senderForStream(envelope.metadata().streamId());
        transport.executeOnClient(() -> processOnClientThread(raw, ticket, sender));
        return true;
    }

    /** Sends a counted End Event request only on its authenticated, locally ready sender stream. */
    public synchronized boolean sendPrisonerAbility(UUID eventUuid, long generation, String abilityId,
            Optional<UUID> targetUuid) {
        Objects.requireNonNull(targetUuid, "targetUuid");
        if (!isReady(BridgeRequestCodec.Sender.END_EVENT) || eventUuid == null) {
            return false;
        }
        ClientBridgeConnectionState.Connection connection = connectionState.currentConnection().orElse(null);
        UUID session = connectionState.verifiedSession().orElse(null);
        String stream = connectionState.activeStream(ClientBridgeConnectionState.Sender.END_EVENT).orElse(null);
        if (connection == null || session == null || stream == null) {
            return false;
        }
        if (!transport.canSendRequest()) {
            return false;
        }
        long counter;
        try {
            counter = connectionState.nextRequestCounter(ClientBridgeConnectionState.Sender.END_EVENT);
        } catch (IllegalStateException exhausted) {
            retireAndNotify(BridgeRequestCodec.Sender.END_EVENT, stream);
            return false;
        }
        byte[] encoded;
        try {
            encoded = BridgeRequestCodec.encode(new BridgeRequestCodec.PrisonerAbilityRequest(
                    connection.nonce(), connection.playerUuid(), session, counter, stream,
                    eventUuid, generation, abilityId, targetUuid));
        } catch (IllegalArgumentException invalidRequest) {
            connectionState.rollbackUnsentRequestCounter(ClientBridgeConnectionState.Sender.END_EVENT, counter);
            return false;
        }
        if (outboxes.get(BridgeRequestCodec.Sender.END_EVENT).enqueue(encoded, transport)) {
            return true;
        }
        connectionState.rollbackUnsentRequestCounter(ClientBridgeConnectionState.Sender.END_EVENT, counter);
        return false;
    }

    /** Starts a sender-specific reopen using its current retired stream, if any. */
    public synchronized boolean requestReopen(BridgeRequestCodec.Sender sender) {
        Objects.requireNonNull(sender, "sender");
        Optional<String> active = connectionState.activeStream(stateSender(sender));
        return requestReopen(sender, active);
    }

    public synchronized boolean isReady(BridgeRequestCodec.Sender sender) {
        SenderFlow flow = senders.get(sender);
        return connectionState.isReady(stateSender(sender)) && flow.readyConfirmSent && flow.readyConfirmAckReceived;
    }

    /** Retries receipts byte-for-byte and recovers expired or missing sender acknowledgements. */
    public synchronized void tick(long nowMillis) {
        if (connectionState.currentConnection().isEmpty() || connectionState.verifiedSession().isEmpty()) {
            return;
        }
        if (lastControlHelloSentAt != 0L
                && nowMillis - lastControlHelloSentAt >= CONTROL_HELLO_REOPEN_DELAY_MILLIS) {
            boolean awaitingInitialHelloAck = senders.values().stream()
                    .anyMatch(flow -> flow.streamId == null && !flow.reopenDeferred);
            boolean initialHelloAckRecoveryDue = firstControlHelloSentAt > 0L
                    && nowMillis - firstControlHelloSentAt >= INITIAL_HELLO_ACK_RECOVERY_DELAY_MILLIS;
            if (awaitingInitialHelloAck) {
                // Keep the idempotent hello alive while an empty-stream reopen is pending. The
                // reopen only works if the server has already bound this verified client session.
                sendControlHello();
                if (initialHelloAckRecoveryDue) {
                    for (BridgeRequestCodec.Sender sender : BridgeRequestCodec.Sender.values()) {
                        SenderFlow flow = senders.get(sender);
                        if (flow.streamId == null && !flow.reopenPending && !flow.reopenDeferred) {
                            requestReopen(sender, Optional.empty());
                        }
                    }
                }
            } else {
                for (BridgeRequestCodec.Sender sender : BridgeRequestCodec.Sender.values()) {
                    SenderFlow flow = senders.get(sender);
                    if (connectionState.activeStream(stateSender(sender)).isEmpty()
                            && !flow.reopenPending && !flow.reopenDeferred) {
                        requestReopen(sender, Optional.empty());
                    }
                }
            }
            lastControlHelloSentAt = nowMillis;
        }

        for (BridgeRequestCodec.Sender sender : BridgeRequestCodec.Sender.values()) {
            SenderFlow flow = senders.get(sender);
            if (flow.reopenDeferred && transport.canSendRequest()) {
                String expectedStreamId = flow.deferredExpectedStreamId;
                flow.clearDeferredReopen();
                requestRetiredStreamReopen(sender, expectedStreamId);
            }
            if (flow.reopenPending && flow.reopenBytes != null && nowMillis >= flow.nextRetryAt) {
                outboxes.get(sender).enqueue(flow.reopenBytes, transport);
                scheduleNextRetry(flow, nowMillis);
                continue;
            }
            if (flow.reopenDeferred || flow.streamId == null) {
                continue;
            }
            if (flow.heartbeatEnabled && flow.readyConfirmAckReceived
                    && transport.nanoTime() - flow.lastAuthenticatedActivityNanos >= SENDER_HEARTBEAT_TIMEOUT_NANOS) {
                expireAndReopen(sender, flow.streamId);
                continue;
            }
            if (!flow.readyAckAccepted && flow.helloAckExpiresAt > 0 && nowMillis >= flow.helloAckExpiresAt) {
                expireAndReopen(sender, flow.streamId);
                continue;
            }
            if (!flow.readyAckAccepted && flow.readyRequestBytes != null && nowMillis >= flow.nextRetryAt) {
                outboxes.get(sender).enqueue(flow.readyRequestBytes, transport);
                scheduleNextRetry(flow, nowMillis);
                continue;
            }
            if (flow.readyAckAccepted && !flow.readyConfirmAckReceived) {
                if (nowMillis >= flow.readyAckExpiresAt) {
                    expireAndReopen(sender, flow.streamId);
                } else if (flow.readyConfirmBytes != null && nowMillis >= flow.nextRetryAt) {
                    sendReadyConfirm(sender, flow);
                    scheduleNextRetry(flow, nowMillis);
                }
            }
        }
    }

    /** Clears every connection-scoped v4 item, including queued packet tickets. */
    public synchronized void close() {
        receiveRateLimiter.reset();
        connectionState.close();
        clearProtocolState(true);
    }

    private void processOnClientThread(byte[] raw, ClientBridgeConnectionState.QueueTicket ticket,
            BridgeRequestCodec.Sender receivedStreamSender) {
        synchronized (this) {
            final BridgeEnvelope envelope;
            try {
                envelope = BridgeEnvelopeCodec.decode(raw);
            } catch (IllegalArgumentException malformed) {
                return;
            }
            BridgeSigningPolicy.VerificationResult verification = BridgeSigningPolicy.verifyV4(
                    pinnedKey, envelope, transport.currentTimeMillis());
            if (verification == BridgeSigningPolicy.VerificationResult.EXPIRED) {
                BridgeEnvelope.Metadata metadata = envelope.metadata();
                if (receivedStreamSender != null && isCurrentBinding(
                        metadata, ticket, transport.currentPlayerUuid())) {
                    SenderFlow flow = senders.get(receivedStreamSender);
                    if (isCurrentSenderBinding(receivedStreamSender, flow, metadata)) {
                        if (metadata.counter() <= flow.lastServerCounter) {
                            return;
                        }
                        expireAndReopen(receivedStreamSender, metadata.streamId());
                    }
                }
                return;
            }
            if (verification != BridgeSigningPolicy.VerificationResult.VALID
                    || !isCurrentBinding(envelope.metadata(), ticket, transport.currentPlayerUuid())) {
                return;
            }

            BridgeRequestCodec.Sender activeSender = senderForStream(envelope.metadata().streamId());
            if (receivedStreamSender != null && activeSender != receivedStreamSender) {
                return;
            }
            byte[] bodyBytes = envelope.bodyBytes();
            if (bodyBytes == null || bodyBytes.length < 2) {
                retireMalformedStream(activeSender, envelope.metadata().streamId());
                return;
            }

            int schema = Byte.toUnsignedInt(bodyBytes[0]);
            int type = Byte.toUnsignedInt(bodyBytes[1]);
            if (schema == BridgeControlBodyCodec.SCHEMA
                    && (type == BridgeControlBodyCodec.HELLO_ACK_TYPE
                            || type == BridgeControlBodyCodec.SENDER_HEARTBEAT_TYPE
                            || type == BridgeControlBodyCodec.SENDER_READY_CONFIRM_ACK_TYPE
                            || type == BridgeControlBodyCodec.SENDER_READY_ACK_TYPE)) {
                handleControlEnvelope(raw, envelope, activeSender);
                return;
            }
            if (activeSender == null) {
                return;
            }
            handleApplicationEnvelope(raw, envelope, ticket, activeSender);
        }
    }

    private void handleControlEnvelope(byte[] raw, BridgeEnvelope envelope,
            BridgeRequestCodec.Sender activeSender) {
        final BridgeControlBodyCodec.Body body;
        try {
            body = BridgeControlBodyCodec.decode(envelope.bodyBytes());
        } catch (IllegalArgumentException malformed) {
            retireMalformedStream(activeSender, envelope.metadata().streamId());
            return;
        }
        if (body instanceof BridgeControlBodyCodec.HelloAck helloAck) {
            acceptHelloAck(raw, envelope, helloAck, activeSender);
        } else if (body instanceof BridgeControlBodyCodec.SenderHeartbeat heartbeat) {
            acceptSenderHeartbeat(envelope, heartbeat, activeSender);
        } else if (body instanceof BridgeControlBodyCodec.SenderReadyAck readyAck) {
            acceptSenderReadyAck(raw, envelope, readyAck, activeSender);
        } else if (body instanceof BridgeControlBodyCodec.SenderReadyConfirmAck confirmAck) {
            acceptSenderReadyConfirmAck(raw, envelope, confirmAck, activeSender);
        }
    }

    private void acceptHelloAck(byte[] raw, BridgeEnvelope envelope, BridgeControlBodyCodec.HelloAck ack,
            BridgeRequestCodec.Sender activeSender) {
        BridgeRequestCodec.Sender sender = ack.sender();
        SenderFlow flow = senders.get(sender);
        String streamId = envelope.metadata().streamId();
        if (activeSender == sender && streamId.equals(flow.streamId)
                && MessageDigest.isEqual(raw, flow.helloAckEnvelope)) {
            if (flow.readyRequestCounter == 0L) {
                sendSenderReady(sender, flow, ack, raw);
            }
            return;
        }
        if (activeSender != null || envelope.metadata().counter() != 1
                || !ack.sessionUuid().equals(connectionState.verifiedSession().orElse(null))) {
            return;
        }
        byte[] expectedRequestId;
        if (ack.requestKind() == 0) {
            UUID helloId = connectionState.controlHelloId().orElse(null);
            if (helloId == null || flow.reopenPending || flow.initialHelloAckSuperseded
                    || connectionState.activeStream(stateSender(sender)).isPresent()) {
                return;
            }
            expectedRequestId = uuidBytes(helloId);
        } else {
            if (!flow.reopenPending || flow.reopenRequestId == null) {
                return;
            }
            expectedRequestId = uuidBytes(flow.reopenRequestId);
        }
        if (!MessageDigest.isEqual(expectedRequestId, ack.requestId())) {
            return;
        }
        if (!connectionState.installStream(stateSender(sender), streamId)) {
            return;
        }

        flow.clearForNewStream();
        flow.sessionId = ack.sessionUuid();
        flow.streamId = streamId;
        flow.heartbeatEnabled = (ack.featureFlags() & BridgeControlBodyCodec.SENDER_HEARTBEAT_FEATURE) != 0;
        flow.initialHelloAckSuperseded |= ack.requestKind() == 1;
        flow.lastServerCounter = 1;
        flow.helloAckEnvelope = raw.clone();
        flow.helloAckExpiresAt = envelope.metadata().expiresAtMillis();
        flow.reopenPending = false;
        flow.reopenBytes = null;
        if (sender == BridgeRequestCodec.Sender.END_EVENT && activeEventContext != null) {
            endEventReassertionRequired = true;
        }

        sendSenderReady(sender, flow, ack, raw);
    }

    private void sendSenderReady(BridgeRequestCodec.Sender sender, SenderFlow flow,
            BridgeControlBodyCodec.HelloAck ack, byte[] helloAckEnvelope) {
        if (!transport.canSendRequest()) {
            return;
        }
        UUID readyRequestId = randomUuid();
        long requestCounter;
        try {
            requestCounter = connectionState.nextRequestCounter(stateSender(sender));
        } catch (IllegalStateException exhausted) {
            retireAndNotify(sender, flow.streamId);
            return;
        }
        ClientBridgeConnectionState.Connection connection = connectionState.currentConnection().orElseThrow();
        byte[] ready = BridgeRequestCodec.encode(new BridgeRequestCodec.SenderReady(
                connection.nonce(), connection.playerUuid(), ack.sessionUuid(), requestCounter,
                sender, flow.streamId, uuidBytes(readyRequestId), sha256(helloAckEnvelope)));
        if (!outboxes.get(sender).enqueue(ready, transport)) {
            connectionState.rollbackUnsentRequestCounter(stateSender(sender), requestCounter);
            return;
        }
        flow.readyRequestId = readyRequestId;
        flow.readyRequestCounter = requestCounter;
        flow.readyRequestBytes = ready.clone();
        flow.retryDelayMillis = FIRST_RETRY_DELAY_MILLIS;
        flow.nextRetryAt = transport.currentTimeMillis() + FIRST_RETRY_DELAY_MILLIS;
        connectionState.setOutstandingRequestId(stateSender(sender), readyRequestId);
    }

    private void acceptSenderReadyAck(byte[] raw, BridgeEnvelope envelope,
            BridgeControlBodyCodec.SenderReadyAck ack, BridgeRequestCodec.Sender activeSender) {
        BridgeRequestCodec.Sender sender = ack.sender();
        SenderFlow flow = senders.get(sender);
        if (activeSender != sender || flow.streamId == null
                || !flow.streamId.equals(envelope.metadata().streamId())) {
            return;
        }
        if (flow.readyAckAccepted && MessageDigest.isEqual(raw, flow.readyAckEnvelope)) {
            if (!flow.readyConfirmAckReceived && flow.readyConfirmBytes != null) {
                sendReadyConfirm(sender, flow);
            }
            return;
        }
        UUID currentSession = connectionState.verifiedSession().orElse(null);
        UUID outstanding = connectionState.outstandingRequestId(stateSender(sender)).orElse(null);
        if (envelope.metadata().counter() != 2 || flow.lastServerCounter != 1
                || !ack.sessionUuid().equals(currentSession) || !ack.sessionUuid().equals(flow.sessionId)
                || !MessageDigest.isEqual(ack.requestId(), uuidBytes(flow.readyRequestId))
                || ack.readyRequestCounter() != flow.readyRequestCounter
                || !Objects.equals(outstanding, flow.readyRequestId)) {
            retireMalformedStream(sender, envelope.metadata().streamId());
            return;
        }

        flow.lastServerCounter = 2;
        flow.readyAckAccepted = true;
        flow.readyRequestBytes = null;
        flow.readyAckEnvelope = raw.clone();
        flow.readyAckExpiresAt = envelope.metadata().expiresAtMillis();
        flow.nextRetryAt = Math.min(flow.readyAckExpiresAt, transport.currentTimeMillis() + FIRST_RETRY_DELAY_MILLIS);
        flow.retryDelayMillis = FIRST_RETRY_DELAY_MILLIS;
        BridgeRequestCodec.SenderReadyConfirm confirm = new BridgeRequestCodec.SenderReadyConfirm(
                connectionState.currentConnection().orElseThrow().nonce(),
                connectionState.currentConnection().orElseThrow().playerUuid(),
                ack.sessionUuid(), sender, flow.streamId, uuidBytes(flow.readyRequestId),
                flow.readyRequestCounter, sha256(raw));
        flow.readyConfirmBytes = BridgeRequestCodec.encode(confirm);
        // Local readiness is exposed only after the authenticated ACK's confirm is accepted for sending.
        sendReadyConfirm(sender, flow);
    }

    private void acceptSenderReadyConfirmAck(byte[] raw, BridgeEnvelope envelope,
            BridgeControlBodyCodec.SenderReadyConfirmAck ack, BridgeRequestCodec.Sender activeSender) {
        BridgeRequestCodec.Sender sender = ack.sender();
        SenderFlow flow = senders.get(sender);
        if (activeSender != sender || flow.streamId == null
                || !flow.streamId.equals(envelope.metadata().streamId())) {
            return;
        }
        if (flow.readyConfirmAckReceived) {
            if (MessageDigest.isEqual(raw, flow.readyConfirmAckEnvelope)) {
                return;
            }
            retireMalformedStream(sender, envelope.metadata().streamId());
            return;
        }
        UUID currentSession = connectionState.verifiedSession().orElse(null);
        if (!flow.readyAckAccepted || !flow.readyConfirmSent || flow.readyConfirmBytes == null
                || flow.lastServerCounter != 2L || envelope.metadata().counter() != 3L
                || !ack.sessionUuid().equals(currentSession) || !ack.sessionUuid().equals(flow.sessionId)
                || !MessageDigest.isEqual(ack.requestId(), uuidBytes(flow.readyRequestId))
                || ack.readyRequestCounter() != flow.readyRequestCounter
                || !MessageDigest.isEqual(ack.confirmRequestSha256(), sha256(flow.readyConfirmBytes))) {
            retireMalformedStream(sender, envelope.metadata().streamId());
            return;
        }
        flow.lastServerCounter = 3L;
        flow.readyConfirmAckReceived = true;
        flow.readyConfirmAckEnvelope = raw.clone();
        flow.lastAuthenticatedActivityNanos = transport.nanoTime();
        connectionState.setReady(stateSender(sender), true);
    }

    private void acceptSenderHeartbeat(BridgeEnvelope envelope, BridgeControlBodyCodec.SenderHeartbeat heartbeat,
            BridgeRequestCodec.Sender activeSender) {
        if (activeSender == null) {
            return;
        }
        BridgeEnvelope.Metadata metadata = envelope.metadata();
        SenderFlow flow = senders.get(activeSender);
        if (!isCurrentSenderBinding(activeSender, flow, metadata)) {
            return;
        }
        if (heartbeat.sender() != activeSender
                || !heartbeat.sessionUuid().equals(flow.sessionId)
                || !heartbeat.sessionUuid().equals(connectionState.verifiedSession().orElse(null))
                || !flow.heartbeatEnabled
                || !flow.readyConfirmAckReceived
                || !connectionState.isReady(stateSender(activeSender))) {
            retireMalformedStream(activeSender, metadata.streamId());
            return;
        }
        if (metadata.counter() <= flow.lastServerCounter) {
            return;
        }
        if (metadata.counter() != flow.lastServerCounter + 1L) {
            retireMalformedStream(activeSender, metadata.streamId());
            return;
        }
        flow.lastServerCounter = metadata.counter();
        flow.lastAuthenticatedActivityNanos = transport.nanoTime();
    }

    private boolean sendReadyConfirm(BridgeRequestCodec.Sender sender, SenderFlow flow) {
        if (flow.readyConfirmBytes == null || !outboxes.get(sender).enqueue(flow.readyConfirmBytes, transport)) {
            return false;
        }
        flow.readyConfirmSent = true;
        return true;
    }

    private void handleApplicationEnvelope(byte[] raw, BridgeEnvelope envelope,
            ClientBridgeConnectionState.QueueTicket ticket, BridgeRequestCodec.Sender sender) {
        SenderFlow flow = senders.get(sender);
        BridgeEnvelope.Metadata metadata = envelope.metadata();
        if (!metadata.streamId().equals(flow.streamId)) {
            return;
        }
        if (metadata.counter() <= flow.lastServerCounter) {
            return;
        }
        if (metadata.counter() != flow.lastServerCounter + 1) {
            retireMalformedStream(sender, metadata.streamId());
            return;
        }
        if (!flow.readyConfirmAckReceived || !connectionState.isReady(stateSender(sender))) {
            retireMalformedStream(sender, metadata.streamId());
            return;
        }

        final Object mutation;
        try {
            if (sender == BridgeRequestCodec.Sender.END_EVENT) {
                EndEventBridgeBodyCodec.Body body = EndEventBridgeBodyCodec.decode(
                        envelope.bodyBytes(), metadata.recipientUuid());
                EndEventBridgeBodyCodec.validateTimestampMatches(body, metadata.issuedAtMillis());
                mutation = validateEndEventBody(sender, flow, body);
            } else {
                NarcoticsBridgeBodyCodec.Body body = NarcoticsBridgeBodyCodec.decode(envelope.bodyBytes());
                NarcoticsBridgeBodyCodec.validateTimestampMatches(body, metadata.issuedAtMillis());
                if (!body.sessionUuid().equals(flow.sessionId)
                        || !body.sessionUuid().equals(connectionState.verifiedSession().orElse(null))) {
                    throw new IllegalArgumentException("Narcotics body session does not match the verified sender session");
                }
                mutation = validateNarcoticsBody(body, metadata.counter());
            }
        } catch (IllegalArgumentException malformed) {
            retireMalformedStream(sender, metadata.streamId());
            return;
        }

        if (!isCurrentBinding(metadata, ticket, transport.currentPlayerUuid())
                || !isCurrentSenderBinding(sender, flow, metadata)) {
            return;
        }
        if (transport.currentTimeMillis() >= metadata.expiresAtMillis()) {
            expireAndReopen(sender, metadata.streamId());
            return;
        }
        flow.lastServerCounter = metadata.counter();
        if (flow.heartbeatEnabled) {
            flow.lastAuthenticatedActivityNanos = transport.nanoTime();
        }
        if (sender == BridgeRequestCodec.Sender.END_EVENT) {
            flow.helloAckExpiresAt = 0L;
        }
        if (mutation == null) {
            return;
        }
        if (sender == BridgeRequestCodec.Sender.END_EVENT
                && mutation instanceof EndEventBridgeBodyCodec.Body body
                && body.type() == EndEventBridgeBodyCodec.Type.CONTEXT_BIND) {
            mutationHandler.clear(sender);
        }
        if (sender == BridgeRequestCodec.Sender.NARCOTICS) {
            if (mutation instanceof NarcoticsReplay replay) {
                mutationHandler.reportNarcotics(replay.report());
                return;
            }
            NarcoticsBridgeBodyCodec.Body body = (NarcoticsBridgeBodyCodec.Body) mutation;
            BridgePayload report = mutationHandler.applyNarcotics(body, metadata);
            retainNarcoticsReport(body.commandSeq(), report);
            if (report != null) {
                mutationHandler.reportNarcotics(report);
            }
            return;
        }
        mutationHandler.apply(sender, mutation);
    }

    private Object validateEndEventBody(BridgeRequestCodec.Sender sender, SenderFlow flow,
            EndEventBridgeBodyCodec.Body body) {
        if (body.type() == EndEventBridgeBodyCodec.Type.CONTEXT_BIND) {
            EndEventBridgeBodyCodec.ArenaBounds bounds = EndEventBridgeBodyCodec.arenaBoundsFromContextBind(body);
            boolean reassert = activeEventContext != null
                    && activeEventContext.eventUuid().equals(body.eventUuid())
                    && activeEventContext.generation() == body.generation()
                    && endEventReassertionRequired;
            if (reassert) {
                if (!activeEventContext.bounds().equals(bounds)) {
                    throw new IllegalArgumentException("End Event context reassertion changed the active arena bounds");
                }
                endEventReassertionRequired = false;
                return body;
            }
            OptionalLongHighWater previous = generationHighWater(body.eventUuid());
            if (previous.present() && body.generation() <= previous.value()) {
                throw new IllegalArgumentException("End Event bind did not advance its generation high-water mark");
            }
            if (!connectionState.recordGeneration(body.eventUuid(), body.generation())) {
                throw new IllegalArgumentException("End Event generation history is stale or exhausted");
            }
            activeEventContext = new EventContext(body.eventUuid(), body.generation(), bounds);
            endEventReassertionRequired = false;
            return body;
        }

        if (body.type() == EndEventBridgeBodyCodec.Type.CONTEXT_UNBIND) {
            if (activeEventContext == null || !matches(activeEventContext, body)) {
                return null;
            }
            activeEventContext = null;
            endEventReassertionRequired = false;
            return body;
        }

        OptionalLongHighWater highWater = generationHighWater(body.eventUuid());
        if (highWater.present() && body.generation() < highWater.value()) {
            return null;
        }
        if (activeEventContext == null || !matches(activeEventContext, body)) {
            throw new IllegalArgumentException("End Event mutation arrived without its exact active context");
        }
        if (endEventReassertionRequired) {
            throw new IllegalArgumentException("End Event stream mutated before reasserting its active context");
        }
        if (body.type() == EndEventBridgeBodyCodec.Type.END_WORLD_BEAM
                && !EndEventBridgeBodyCodec.beamWithinBounds(body, activeEventContext.bounds())) {
            throw new IllegalArgumentException("End Event beam endpoints exceed the signed arena bounds");
        }
        return body;
    }

    private Object validateNarcoticsBody(NarcoticsBridgeBodyCodec.Body body, long streamCounter) {
        boolean initial = body.commandSeq() > narcoticsCommandHighWater;
        if (!initial) {
            NarcoticsReceipt retained = narcoticsReceipts.get(body.commandSeq());
            if (retained == null) {
                return null;
            }
            byte[] fingerprint = NarcoticsBridgeBodyCodec.logicalFingerprint(body);
            if (!MessageDigest.isEqual(retained.fingerprint(), fingerprint)) {
                throw new IllegalArgumentException("Narcotics command sequence was reused with different fields");
            }
            return retained.report() == null ? null : new NarcoticsReplay(retained.report());
        }
        NarcoticsBridgeBodyCodec.validateFenceForEnvelope(body, streamCounter, true);
        narcoticsCommandHighWater = body.commandSeq();
        narcoticsReceipts.put(body.commandSeq(), new NarcoticsReceipt(
                NarcoticsBridgeBodyCodec.logicalFingerprint(body), null));
        while (narcoticsReceipts.size() > MAX_NARCOTICS_RECEIPTS) {
            Long oldest = narcoticsReceipts.keySet().iterator().next();
            narcoticsReceipts.remove(oldest);
        }
        return body;
    }

    private void retainNarcoticsReport(long commandSeq, BridgePayload report) {
        NarcoticsReceipt receipt = narcoticsReceipts.get(commandSeq);
        if (receipt != null) {
            narcoticsReceipts.put(commandSeq, new NarcoticsReceipt(receipt.fingerprint(), report));
        }
    }

    private boolean requestReopen(BridgeRequestCodec.Sender sender, Optional<String> expectedStream) {
        if (connectionState.currentConnection().isEmpty() || connectionState.verifiedSession().isEmpty()) {
            return false;
        }
        SenderFlow flow = senders.get(sender);
        if (flow.reopenPending && flow.reopenBytes != null) {
            return outboxes.get(sender).enqueue(flow.reopenBytes, transport);
        }
        if (!transport.canSendRequest()) {
            return false;
        }
        String active = connectionState.activeStream(stateSender(sender)).orElse(null);
        if (active != null) {
            connectionState.retireStream(stateSender(sender));
            mutationHandler.clear(sender);
            if (sender == BridgeRequestCodec.Sender.END_EVENT && activeEventContext != null) {
                endEventReassertionRequired = true;
            }
        }
        flow.clearForReopen();
        UUID requestId = randomUuid();
        long requestCounter;
        try {
            requestCounter = connectionState.nextRequestCounter(stateSender(sender));
        } catch (IllegalStateException exhausted) {
            connectionState.retireStream(stateSender(sender));
            mutationHandler.clear(sender);
            return false;
        }
        ClientBridgeConnectionState.Connection connection = connectionState.currentConnection().orElseThrow();
        BridgeRequestCodec.StreamReopenRequest request = new BridgeRequestCodec.StreamReopenRequest(
                connection.nonce(), connection.playerUuid(), connectionState.verifiedSession().orElseThrow(),
                requestCounter, sender, expectedStream, uuidBytes(requestId));
        flow.reopenPending = true;
        flow.reopenRequestId = requestId;
        flow.reopenBytes = BridgeRequestCodec.encode(request);
        flow.retryDelayMillis = FIRST_RETRY_DELAY_MILLIS;
        flow.nextRetryAt = transport.currentTimeMillis() + flow.retryDelayMillis;
        if (outboxes.get(sender).enqueue(flow.reopenBytes, transport)) {
            connectionState.setOutstandingRequestId(stateSender(sender), requestId);
            return true;
        }
        // Keep the exact request and reserved counter pending: a transient send failure must not
        // erase the stream fence or force a new ControlHello for a stream already retired locally.
        return false;
    }

    private void requestRetiredStreamReopen(BridgeRequestCodec.Sender sender, String expectedStreamId) {
        if (requestReopen(sender, Optional.of(expectedStreamId))) {
            return;
        }
        if (connectionState.currentConnection().isPresent()
                && connectionState.verifiedSession().isPresent()
                && !transport.canSendRequest()) {
            senders.get(sender).deferReopen(expectedStreamId);
        }
    }

    private void expireAndReopen(BridgeRequestCodec.Sender sender, String streamId) {
        if (!Objects.equals(connectionState.activeStream(stateSender(sender)).orElse(null), streamId)) {
            return;
        }
        retireAndNotify(sender, streamId);
        requestRetiredStreamReopen(sender, streamId);
    }

    private void retireMalformedStream(BridgeRequestCodec.Sender sender, String streamId) {
        if (sender == null || !Objects.equals(connectionState.activeStream(stateSender(sender)).orElse(null), streamId)) {
            return;
        }
        retireAndNotify(sender, streamId);
        requestRetiredStreamReopen(sender, streamId);
    }

    private void retireAndNotify(BridgeRequestCodec.Sender sender, String streamId) {
        if (!Objects.equals(connectionState.activeStream(stateSender(sender)).orElse(null), streamId)) {
            return;
        }
        connectionState.retireStream(stateSender(sender));
        senders.get(sender).clearForReopen();
        if (sender == BridgeRequestCodec.Sender.END_EVENT && activeEventContext != null) {
            endEventReassertionRequired = true;
        }
        mutationHandler.clear(sender);
    }

    private boolean matchesReceiveBinding(BridgeEnvelope.Metadata metadata, UUID currentPlayer) {
        ClientBridgeConnectionState.Connection connection = connectionState.currentConnection().orElse(null);
        UUID session = connectionState.verifiedSession().orElse(null);
        return connection != null && session != null && currentPlayer != null
                && currentPlayer.equals(connection.playerUuid())
                && metadata.recipientUuid().equals(currentPlayer)
                && metadata.clientNonce().equals(connection.nonce());
    }

    private boolean isCurrentBinding(BridgeEnvelope.Metadata metadata,
            ClientBridgeConnectionState.QueueTicket ticket, UUID currentPlayer) {
        ClientBridgeConnectionState.Connection connection = connectionState.currentConnection().orElse(null);
        UUID currentSession = connectionState.verifiedSession().orElse(null);
        return connection != null && currentSession != null && currentPlayer != null
                && connectionState.isCurrent(ticket)
                && ticket.verifiedSessionId() != null
                && ticket.verifiedSessionId().equals(currentSession)
                && currentPlayer.equals(connection.playerUuid())
                && metadata.recipientUuid().equals(currentPlayer)
                && metadata.clientNonce().equals(connection.nonce());
    }

    private boolean isCurrentSenderBinding(BridgeRequestCodec.Sender sender, SenderFlow flow,
            BridgeEnvelope.Metadata metadata) {
        return flow.sessionId != null
                && flow.sessionId.equals(connectionState.verifiedSession().orElse(null))
                && Objects.equals(flow.streamId, metadata.streamId())
                && Objects.equals(connectionState.activeStream(stateSender(sender)).orElse(null), metadata.streamId());
    }

    private BridgeRequestCodec.Sender senderForStream(String streamId) {
        for (BridgeRequestCodec.Sender sender : BridgeRequestCodec.Sender.values()) {
            if (Objects.equals(connectionState.activeStream(stateSender(sender)).orElse(null), streamId)) {
                return sender;
            }
        }
        return null;
    }

    private OptionalLongHighWater generationHighWater(UUID eventUuid) {
        java.util.OptionalLong highWater = connectionState.generationHighWater(eventUuid);
        return new OptionalLongHighWater(highWater.isPresent(), highWater.orElse(0L));
    }

    private static boolean matches(EventContext context, EndEventBridgeBodyCodec.Body body) {
        return context.eventUuid().equals(body.eventUuid()) && context.generation() == body.generation();
    }

    private void clearProtocolState(boolean clearEventContext) {
        clearSenderFlows(true);
        clearNarcoticsLedger();
        lastControlHelloSentAt = 0L;
        firstControlHelloSentAt = 0L;
        controlHelloBytes = null;
        if (clearEventContext) {
            activeEventContext = null;
            endEventReassertionRequired = false;
        } else {
            endEventReassertionRequired = activeEventContext != null;
        }
    }

    private void clearSenderFlows(boolean notify) {
        for (BridgeRequestCodec.Sender sender : BridgeRequestCodec.Sender.values()) {
            SenderFlow flow = senders.get(sender);
            boolean hadState = flow.streamId != null || flow.readyConfirmSent || flow.reopenPending
                    || flow.reopenDeferred;
            flow.clearAll();
            if (notify && hadState) {
                mutationHandler.clear(sender);
            }
        }
    }

    private void clearNarcoticsLedger() {
        narcoticsCommandHighWater = 0L;
        narcoticsReceipts.clear();
    }

    private static ClientBridgeConnectionState.Sender stateSender(BridgeRequestCodec.Sender sender) {
        return sender == BridgeRequestCodec.Sender.END_EVENT
                ? ClientBridgeConnectionState.Sender.END_EVENT : ClientBridgeConnectionState.Sender.NARCOTICS;
    }

    private static UUID randomUuid() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return bytesUuid(bytes);
    }

    private static byte[] uuidBytes(UUID uuid) {
        return ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
                .putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits()).array();
    }

    private static UUID bytesUuid(byte[] bytes) {
        ByteBuffer input = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        return new UUID(input.getLong(), input.getLong());
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }

    private static void scheduleNextRetry(SenderFlow flow, long nowMillis) {
        long current = Math.max(FIRST_RETRY_DELAY_MILLIS, flow.retryDelayMillis);
        flow.nextRetryAt = nowMillis + current;
        flow.retryDelayMillis = Math.min(MAX_RETRY_DELAY_MILLIS, current * 2L);
    }

    public interface Transport {
        boolean canSendRequest();

        boolean sendRequest(byte[] requestBytes);

        void executeOnClient(Runnable task);

        UUID currentPlayerUuid();

        long currentTimeMillis();

        default long nanoTime() {
            return System.nanoTime();
        }
    }

    public interface MutationHandler {
        void apply(BridgeRequestCodec.Sender sender, Object typedBody);

        default BridgePayload applyNarcotics(NarcoticsBridgeBodyCodec.Body body,
                BridgeEnvelope.Metadata metadata) {
            apply(BridgeRequestCodec.Sender.NARCOTICS, body);
            return BridgePayload.visualAck(body.sessionUuid().toString(), body.commandSeq(), body.effectId(),
                    ClientVisualReportPolicy.ackStatus(body.type()));
        }

        default void reportNarcotics(BridgePayload report) {
        }

        default void clear(BridgeRequestCodec.Sender sender) {
        }
    }

    @FunctionalInterface
    interface EnvelopeDecoder {
        BridgeEnvelope decode(byte[] raw);
    }

    private record EventContext(UUID eventUuid, long generation, EndEventBridgeBodyCodec.ArenaBounds bounds) {
    }

    private record OptionalLongHighWater(boolean present, long value) {
    }

    private record NarcoticsReceipt(byte[] fingerprint, BridgePayload report) {
        private NarcoticsReceipt {
            fingerprint = fingerprint.clone();
        }

        @Override
        public byte[] fingerprint() {
            return fingerprint.clone();
        }
    }

    private record NarcoticsReplay(BridgePayload report) {
    }

    private static final class SenderFlow {
        private UUID sessionId;
        private String streamId;
        private long lastServerCounter;
        private boolean heartbeatEnabled;
        private long lastAuthenticatedActivityNanos;
        private byte[] helloAckEnvelope;
        private long helloAckExpiresAt;
        private UUID readyRequestId;
        private long readyRequestCounter;
        private byte[] readyRequestBytes;
        private byte[] readyAckEnvelope;
        private long readyAckExpiresAt;
        private boolean readyAckAccepted;
        private boolean readyConfirmSent;
        private byte[] readyConfirmBytes;
        private boolean readyConfirmAckReceived;
        private byte[] readyConfirmAckEnvelope;
        private boolean reopenPending;
        private boolean reopenDeferred;
        private String deferredExpectedStreamId;
        private UUID reopenRequestId;
        private byte[] reopenBytes;
        private long nextRetryAt;
        private long retryDelayMillis;
        private boolean initialHelloAckSuperseded;

        private void clearForNewStream() {
            sessionId = null;
            streamId = null;
            lastServerCounter = 0L;
            heartbeatEnabled = false;
            lastAuthenticatedActivityNanos = 0L;
            helloAckEnvelope = null;
            helloAckExpiresAt = 0L;
            readyRequestId = null;
            readyRequestCounter = 0L;
            readyRequestBytes = null;
            readyAckEnvelope = null;
            readyAckExpiresAt = 0L;
            readyAckAccepted = false;
            readyConfirmSent = false;
            readyConfirmBytes = null;
            readyConfirmAckReceived = false;
            readyConfirmAckEnvelope = null;
            reopenPending = false;
            reopenDeferred = false;
            deferredExpectedStreamId = null;
            reopenRequestId = null;
            reopenBytes = null;
            nextRetryAt = 0L;
            retryDelayMillis = FIRST_RETRY_DELAY_MILLIS;
        }

        private void clearForReopen() {
            clearForNewStream();
        }

        private void deferReopen(String expectedStreamId) {
            reopenDeferred = true;
            deferredExpectedStreamId = expectedStreamId;
        }

        private void clearDeferredReopen() {
            reopenDeferred = false;
            deferredExpectedStreamId = null;
        }

        private void clearAll() {
            clearForNewStream();
            initialHelloAckSuperseded = false;
        }
    }

    private static final class SerialOutbox {
        private boolean draining;

        private synchronized boolean enqueue(byte[] exactRequest, Transport transport) {
            if (draining || !transport.canSendRequest()) {
                return false;
            }
            draining = true;
            try {
                return transport.sendRequest(exactRequest.clone());
            } finally {
                draining = false;
            }
        }
    }
}
