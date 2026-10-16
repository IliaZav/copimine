package me.copimine.clientbridge;

import me.copimine.protocol.BridgeControlBodyCodec;
import me.copimine.protocol.BridgeEnvelope;
import me.copimine.protocol.BridgeEnvelopeCodec;
import me.copimine.protocol.BridgeRequestCodec;
import me.copimine.protocol.BridgeSigner;
import me.copimine.protocol.BridgeSigningPolicy;
import me.copimine.protocol.ClientModSessionRegistry;
import me.copimine.protocol.NarcoticsBridgeBodyCodec;
import org.bukkit.entity.Player;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;

/** Owns the Narcotics sender's authenticated v4 streams and visual packet outbox. */
public final class NarcoticsBridgeV4Sender implements AutoCloseable {
    private static final long HEARTBEAT_INTERVAL_MILLIS = 5_000L;
    private static final long ENVELOPE_TTL_MILLIS = 30_000L;
    private static final long REOPEN_INTERVAL_MILLIS = 1_000L;
    private static final long HELLO_ACK_REPLAY_INTERVAL_MILLIS = 1_000L;
    private static final UUID ZERO_UUID = new UUID(0L, 0L);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ClientModSessionRegistry registry;
    private final BridgeSigner signer;
    private final LongSupplier clock;
    private final BiConsumer<UUID, byte[]> packetSink;
    private final Map<UUID, PlayerState> players = new ConcurrentHashMap<>();
    private final ClientModSessionRegistry.InvalidationListener invalidationListener = this::onSessionInvalidated;
    private final ClientModSessionRegistry.ListenerRegistration listenerRegistration;
    private volatile boolean closed;

    public NarcoticsBridgeV4Sender(ClientModSessionRegistry registry, BridgeSigner signer,
                                   BiConsumer<UUID, byte[]> packetSink) {
        this(registry, signer, System::currentTimeMillis, packetSink);
    }

    NarcoticsBridgeV4Sender(ClientModSessionRegistry registry, BridgeSigner signer,
                            LongSupplier clock, BiConsumer<UUID, byte[]> packetSink) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.signer = Objects.requireNonNull(signer, "signer");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.packetSink = Objects.requireNonNull(packetSink, "packetSink");
        this.listenerRegistration = registry.addInvalidationListener(invalidationListener);
    }

    /** Called by the owning bridge on the server thread. */
    public synchronized void tick() {
        if (closed) {
            return;
        }
        long now = positiveNow();
        for (PlayerState state : players.values()) {
            if (!isPlayerOnline(state.playerHandle) || !isCurrent(state.playerUuid, state.session)
                    || state.session == null || !"0.1.3+26.3".equals(state.session.clientVersion())) {
                continue;
            }
            Stream stream = state.current;
            if (stream == null || stream.phase != Phase.READY
                    || now <= 0L || now < stream.nextHeartbeatAtMillis) {
                continue;
            }
            long counter = stream.nextEnvelopeCounter;
            if (counter <= 0L || counter >= Long.MAX_VALUE - 1L) {
                retireStream(state);
                continue;
            }
            byte[] body = BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.SenderHeartbeat(
                    BridgeRequestCodec.Sender.NARCOTICS, state.session.sessionUuid()));
            try {
                byte[] envelope = signAndGet(state, stream, counter, now, body);
                if (envelope == null) {
                    retireStream(state);
                    continue;
                }
                dispatch(state.playerUuid, state, stream, envelope);
                if (state.current == stream) {
                    stream.nextEnvelopeCounter = counter + 1L;
                    stream.nextHeartbeatAtMillis = now + HEARTBEAT_INTERVAL_MILLIS;
                }
            } catch (RuntimeException signingFailure) {
                retireStream(state);
            }
        }
    }

    /** Starts a sender stream only for the registry's current verified session. */
    public synchronized boolean startStream(Player player,
            ClientModSessionRegistry.VerifiedClientSession verifiedSession,
            String clientNonce, byte[] helloId) {
        if (closed || !isPlayerOnline(player) || verifiedSession == null
                || helloId == null || helloId.length != BridgeControlBodyCodec.REQUEST_ID_BYTES
                || !isCanonicalClientNonce(clientNonce)
                || !isCurrent(player.getUniqueId(), verifiedSession)) {
            return false;
        }

        UUID playerUuid = player.getUniqueId();
        PlayerState state = players.compute(playerUuid, (ignored, existing) -> {
            if (existing == null || !existing.connectionEpoch.equals(verifiedSession.connectionEpoch())) {
                return new PlayerState(playerUuid, verifiedSession.connectionEpoch());
            }
            return existing;
        });
        state.playerHandle = player;
        if (state.clientNonce != null && !state.clientNonce.equals(clientNonce)) {
            return false;
        }
        if (state.session != null && state.session.sessionUuid().equals(verifiedSession.sessionUuid())
                && state.current != null) {
            if (!Arrays.equals(state.current.helloId, helloId)) {
                return false;
            }
            if (isFreshCachedResponse(state, state.current, state.current.helloEnvelopeBytes)) {
                long now = positiveNow();
                if (now <= 0L || state.lastHelloAckReplayAtMillis > 0L
                        && now - state.lastHelloAckReplayAtMillis < HELLO_ACK_REPLAY_INTERVAL_MILLIS) {
                    return false;
                }
                if (replayTo(playerUuid, state, state.current, state.current.helloEnvelopeBytes)) {
                    state.lastHelloAckReplayAtMillis = now;
                    return true;
                }
            }
            return false;
        }

        if (!bindSession(state, verifiedSession, clientNonce)) {
            return false;
        }
        return createStream(playerUuid, state, 0, helloId, null);
    }

    /** Parses one bounded client-originated v4 request and applies only Narcotics-routed records. */
    public synchronized void onRequest(Player player, byte[] payload) {
        onRequest(player, payload, true);
    }

    /**
     * Parses a bounded request even when admission denied it, so authenticated counted requests can
     * advance the per-stream client counter without executing their requested action.
     */
    public synchronized void onRequest(Player player, byte[] payload, boolean admitted) {
        if (closed || !isPlayerOnline(player) || payload == null
                || payload.length > BridgeRequestCodec.MAX_REQUEST_BYTES) {
            return;
        }
        final BridgeRequestCodec.Request request;
        try {
            request = BridgeRequestCodec.decode(payload);
        } catch (RuntimeException malformed) {
            return;
        }
        UUID playerUuid = player.getUniqueId();
        if (!playerUuid.equals(request.playerUuid())) {
            return;
        }
        Optional<ClientModSessionRegistry.VerifiedClientSession> current = registry.current(playerUuid);
        if (current.isEmpty() || !isCurrent(playerUuid, current.get())) {
            retireStaleState(playerUuid);
            return;
        }
        PlayerState state = players.get(playerUuid);
        if (state != null && (!state.connectionEpoch.equals(current.get().connectionEpoch())
                || !Objects.equals(state.session, current.get()))) {
            retireStaleState(playerUuid);
            state = players.get(playerUuid);
        }
        if (!current.get().sessionUuid().equals(request.clientModSessionUuid())) {
            return;
        }

        if (request instanceof BridgeRequestCodec.ControlHello hello) {
            if (admitted) {
                startStream(player, current.get(), hello.clientNonce(), hello.helloId());
            }
            return;
        }
        if (state == null || !bindingMatches(state, current.get(), request.clientNonce())) {
            return;
        }
        if (request instanceof BridgeRequestCodec.PrisonerAbilityRequest) {
            return; // This type is routed exclusively to the End Event sender.
        }
        if (request instanceof BridgeRequestCodec.StreamReopenRequest reopen) {
            if (reopen.sender() != BridgeRequestCodec.Sender.NARCOTICS) {
                return;
            }
            if (!consumeCounter(playerUuid, state, payload, reopen, reopen.requestCounter(), admitted, true)) {
                return;
            }
            handleReopen(playerUuid, state, reopen, payload);
            return;
        }
        if (request instanceof BridgeRequestCodec.SenderReady ready) {
            if (ready.sender() != BridgeRequestCodec.Sender.NARCOTICS) {
                return;
            }
            if (!consumeCounter(playerUuid, state, payload, ready, ready.requestCounter(), admitted, true)) {
                return;
            }
            handleReady(playerUuid, state, ready, payload);
            return;
        }
        if (request instanceof BridgeRequestCodec.SenderReadyConfirm confirm) {
            if (admitted && confirm.sender() == BridgeRequestCodec.Sender.NARCOTICS) {
                handleReadyConfirm(playerUuid, state, confirm, payload);
            }
        }
    }

    public synchronized boolean isReady(Player player) {
        if (closed || !isPlayerOnline(player)) {
            return false;
        }
        PlayerState state = players.get(player.getUniqueId());
        if (state == null) {
            return false;
        }
        if (!isCurrent(player.getUniqueId(), state.session)) {
            retireStaleState(player.getUniqueId());
            return false;
        }
        return state.current != null && state.current.phase == Phase.READY;
    }

    /** Signs and sends a visual body, allocating its envelope counter in this synchronized queue. */
    public synchronized boolean sendVisualStart(Player player, NarcoticsBridgeBodyCodec.Body command,
                                                  boolean initialCommand) {
        return sendVisualStartTracked(player, command, initialCommand).isPresent();
    }

    public synchronized Optional<NarcoticsBridgeBodyCodec.Body> sendVisualStartTracked(
            Player player, NarcoticsBridgeBodyCodec.Body command, boolean initialCommand) {
        if (command == null || command.type() != NarcoticsBridgeBodyCodec.Type.VISUAL_START) {
            return Optional.empty();
        }
        return sendVisual(player, command, initialCommand);
    }

    public synchronized boolean stopVisual(Player player, NarcoticsBridgeBodyCodec.Body command) {
        return stopVisualTracked(player, command).isPresent();
    }

    public synchronized Optional<NarcoticsBridgeBodyCodec.Body> stopVisualTracked(
            Player player, NarcoticsBridgeBodyCodec.Body command) {
        if (command == null || command.type() != NarcoticsBridgeBodyCodec.Type.VISUAL_STOP) {
            return Optional.empty();
        }
        return sendVisual(player, command, true);
    }

    public synchronized boolean clearVisuals(Player player, NarcoticsBridgeBodyCodec.Body command,
                                               boolean initialCommand) {
        return clearVisualsTracked(player, command, initialCommand).isPresent();
    }

    public synchronized Optional<NarcoticsBridgeBodyCodec.Body> clearVisualsTracked(
            Player player, NarcoticsBridgeBodyCodec.Body command, boolean initialCommand) {
        if (command == null || command.type() != NarcoticsBridgeBodyCodec.Type.VISUAL_CLEAR_ALL) {
            return Optional.empty();
        }
        return sendVisual(player, command, initialCommand);
    }

    public synchronized long nextEnvelopeCounter(Player player) {
        if (!isPlayerOnline(player)) {
            return -1L;
        }
        PlayerState state = players.get(player.getUniqueId());
        if (state == null) {
            return -1L;
        }
        if (!isCurrent(player.getUniqueId(), state.session)) {
            retireStaleState(player.getUniqueId());
            return -1L;
        }
        if (state.current == null) {
            return -1L;
        }
        return state.current.nextEnvelopeCounter;
    }

    public synchronized Optional<String> activeStreamId(Player player) {
        if (!isPlayerOnline(player)) {
            return Optional.empty();
        }
        PlayerState state = players.get(player.getUniqueId());
        if (state == null) {
            return Optional.empty();
        }
        if (!isCurrent(player.getUniqueId(), state.session)) {
            retireStaleState(player.getUniqueId());
            return Optional.empty();
        }
        if (state.current == null) {
            return Optional.empty();
        }
        return Optional.of(state.current.streamId);
    }

    public synchronized void retire(UUID playerUuid) {
        PlayerState state = players.get(playerUuid);
        if (state != null) {
            retireStream(state);
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        listenerRegistration.close();
        players.clear();
    }

    private Optional<NarcoticsBridgeBodyCodec.Body> sendVisual(
            Player player, NarcoticsBridgeBodyCodec.Body requested, boolean initialCommand) {
        if (closed || !isPlayerOnline(player)) {
            return Optional.empty();
        }
        UUID playerUuid = player.getUniqueId();
        PlayerState state = players.get(playerUuid);
        if (state == null || state.session == null) {
            return Optional.empty();
        }
        if (!isCurrent(playerUuid, state.session)) {
            retireStaleState(playerUuid);
            return Optional.empty();
        }
        if (!state.session.sessionUuid().equals(requested.sessionUuid())
                || state.current == null || state.current.phase != Phase.READY) {
            return Optional.empty();
        }
        Stream stream = state.current;
        long counter = stream.nextEnvelopeCounter;
        if (counter <= 0L || counter >= Long.MAX_VALUE - 1L) {
            retireStream(state);
            return Optional.empty();
        }
        long now = positiveNow();
        if (now <= 0L || now > Long.MAX_VALUE - ENVELOPE_TTL_MILLIS) {
            retireStream(state);
            return Optional.empty();
        }
        long fence = requested.clearThroughCounter();
        if (initialCommand && (requested.type() == NarcoticsBridgeBodyCodec.Type.VISUAL_CLEAR_ALL
                || requested.type() == NarcoticsBridgeBodyCodec.Type.VISUAL_START
                && requested.clearPolicy().equals("REPLACE_ALL_FULLSCREEN"))) {
            fence = counter - 1L;
        }
        NarcoticsBridgeBodyCodec.Body body = copyWithTimestampAndFence(requested, now, fence);
        byte[] exactBody;
        try {
            exactBody = NarcoticsBridgeBodyCodec.encode(body);
            NarcoticsBridgeBodyCodec.validateTimestampMatches(body, now);
            NarcoticsBridgeBodyCodec.validateFenceForEnvelope(body, counter, initialCommand);
        } catch (RuntimeException invalidBody) {
            return Optional.empty();
        }
        if (!signAndDispatch(playerUuid, state, stream, counter, now, exactBody)) {
            return Optional.empty();
        }
        return Optional.of(body);
    }

    private void handleReady(UUID playerUuid, PlayerState state, BridgeRequestCodec.SenderReady request,
                             byte[] exactRequest) {
        Stream stream = state.current;
        if (stream == null || stream.phase != Phase.HELLO_SENT
                || !stream.streamId.equals(request.streamId())
                || !MessageDigestUtil.constantTimeEquals(request.ackEnvelopeSha256(),
                        MessageDigestUtil.sha256(stream.helloEnvelopeBytes))) {
            return;
        }
        // A valid receipt proves the client learned this stream ID, even if signing the
        // following ACK fails. Recovery must then name that known ID rather than use empty.
        stream.clientAcknowledgedHello = true;
        long issuedAt = positiveNow();
        byte[] body = BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.SenderReadyAck(
                BridgeRequestCodec.Sender.NARCOTICS, state.session.sessionUuid(), request.requestId(),
                request.requestCounter()));
        byte[] envelopeBytes = signAndGet(state, stream, 2L, issuedAt, body);
        if (envelopeBytes == null) {
            retireStream(state);
            return;
        }
        stream.nextEnvelopeCounter = 3L;
        stream.phase = Phase.READY_PENDING_CONFIRM;
        stream.readyRequestId = request.requestId();
        stream.readyRequestCounter = request.requestCounter();
        stream.readyAckEnvelopeBytes = envelopeBytes;
        stream.readyAckExpiresAtMillis = issuedAt + ENVELOPE_TTL_MILLIS;
        cacheResponse(state, exactRequest, envelopeBytes);
        dispatch(playerUuid, state, stream, envelopeBytes);
    }

    private void handleReadyConfirm(UUID playerUuid, PlayerState state,
                                    BridgeRequestCodec.SenderReadyConfirm confirm, byte[] exactRequest) {
        Stream stream = state.current;
        if (stream == null || !stream.streamId.equals(confirm.streamId())
                || !Arrays.equals(stream.readyRequestId, confirm.readyRequestId())
                || stream.readyRequestCounter != confirm.readyRequestCounter()
                || !MessageDigestUtil.constantTimeEquals(confirm.ackEnvelopeSha256(),
                        MessageDigestUtil.sha256(stream.readyAckEnvelopeBytes))) {
            return;
        }
        if (stream.phase == Phase.READY) {
            if (Arrays.equals(stream.confirmRequestBytes, exactRequest)
                    && isFreshCachedResponse(state, stream, stream.readyConfirmAckEnvelopeBytes)) {
                replayTo(playerUuid, state, stream, stream.readyConfirmAckEnvelopeBytes);
            }
            return;
        }
        if (stream.phase != Phase.READY_PENDING_CONFIRM || positiveNow() >= stream.readyAckExpiresAtMillis
                || !isCurrent(playerUuid, state.session)) {
            retireStream(state);
            return;
        }
        long issuedAt = positiveNow();
        if (issuedAt <= 0L || issuedAt >= stream.readyAckExpiresAtMillis) {
            retireStream(state);
            return;
        }
        byte[] body = BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.SenderReadyConfirmAck(
                BridgeRequestCodec.Sender.NARCOTICS, state.session.sessionUuid(), stream.readyRequestId,
                stream.readyRequestCounter, MessageDigestUtil.sha256(exactRequest)));
        byte[] receipt = signAndGet(state, stream, 3L, issuedAt, body);
        if (receipt == null) {
            retireStream(state);
            return;
        }
        stream.nextEnvelopeCounter = 4L;
        stream.confirmRequestBytes = exactRequest.clone();
        stream.readyConfirmAckEnvelopeBytes = receipt.clone();
        dispatch(playerUuid, state, stream, receipt);
        if (state.current == stream) {
            stream.phase = Phase.READY;
            stream.nextHeartbeatAtMillis = issuedAt + HEARTBEAT_INTERVAL_MILLIS;
        }
    }

    private void handleReopen(UUID playerUuid, PlayerState state,
                              BridgeRequestCodec.StreamReopenRequest reopen, byte[] exactRequest) {
        Stream current = state.current;
        long now = positiveNow();
        if (state.lastReopenAtMillis > 0 && now - state.lastReopenAtMillis < REOPEN_INTERVAL_MILLIS) {
            return;
        }
        Optional<String> expected = reopen.expectedStreamId();
        boolean expectedCurrent = current != null && expected.filter(current.streamId::equals).isPresent();
        boolean expectedRetired = current == null
                && expected.filter(value -> value.equals(state.mostRecentlyRetiredStreamId)).isPresent();
        boolean unacceptedAckRecovery = expected.isEmpty() && (current != null
                && current.phase == Phase.HELLO_SENT && !current.clientAcknowledgedHello
                || current == null && state.unacceptedHelloRecoveryAllowed);
        if (!(expectedCurrent || expectedRetired || unacceptedAckRecovery)) {
            return;
        }
        String knownStreamId = expected.orElse(current == null ? null : current.streamId);
        state.lastReopenAtMillis = now;
        if (current != null) {
            if (expectedCurrent) {
                current.clientAcknowledgedHello = true;
            }
            retireStream(state);
        }
        boolean created = createStream(playerUuid, state, 1, reopen.requestId(), exactRequest);
        if (!created) {
            // A failed signature/dispatch created a stream id the client never received. Keep the
            // last client-known id as the retry fence instead of replacing it with that phantom id.
            if (knownStreamId != null) {
                state.mostRecentlyRetiredStreamId = knownStreamId;
            }
            cacheResponse(state, exactRequest, null);
        }
    }

    private boolean consumeCounter(UUID playerUuid, PlayerState state, byte[] exactRequest,
                                   BridgeRequestCodec.Request request, long requestCounter,
                                   boolean admitted, boolean retryWhenResponseMissing) {
        if (state.requestExhausted) {
            return false;
        }
        if (requestCounter == state.lastRequestCounter && Arrays.equals(state.lastRequestBytes, exactRequest)) {
            if (!admitted) {
                return false;
            }
            byte[] response = state.lastRequestResponse;
            if (response == null) {
                return retryWhenResponseMissing;
            }
            if (response != null && state.current != null && isCurrent(playerUuid, state.session)) {
                if (isFreshCachedResponse(state, state.current, response)) {
                    if (request instanceof BridgeRequestCodec.StreamReopenRequest reopen
                            && isPendingReopenHelloAck(state, state.current, reopen, response)) {
                        replayPendingHelloAck(playerUuid, state, state.current, response);
                    } else {
                        replayTo(playerUuid, state, state.current, response);
                    }
                } else if (request instanceof BridgeRequestCodec.StreamReopenRequest reopen
                        && renewExpiredReopenHelloAck(playerUuid, state, reopen, exactRequest, response)) {
                    // A counted retry keeps the same stream, request ID, and counter.
                } else if (state.current.phase != Phase.READY) {
                    retireStream(state);
                }
            }
            return false;
        }
        if (requestCounter != state.nextRequestCounter || requestCounter <= 0L) {
            return false;
        }
        state.lastRequestCounter = requestCounter;
        state.lastRequestBytes = exactRequest.clone();
        state.lastRequestResponse = null;
        if (requestCounter == Long.MAX_VALUE) {
            state.requestExhausted = true;
        } else {
            state.nextRequestCounter = requestCounter + 1L;
        }
        return admitted;
    }

    private static int heartbeatFeatures(PlayerState state) {
        return "0.1.3+26.3".equals(state.session.clientVersion())
                ? BridgeControlBodyCodec.SENDER_HEARTBEAT_FEATURE : 0;
    }

    private boolean createStream(UUID playerUuid, PlayerState state, int requestKind,
                                 byte[] requestId, byte[] exactRequest) {
        if (!isCurrent(playerUuid, state.session) || state.clientNonce == null) {
            return false;
        }
        if (state.seenStreamIds.size() >= 256) {
            return false;
        }
        String streamId;
        do {
            byte[] random = new byte[BridgeSigningPolicy.STREAM_ID_BYTES];
            RANDOM.nextBytes(random);
            streamId = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
            Arrays.fill(random, (byte) 0);
        } while (state.seenStreamIds.contains(streamId));
        state.seenStreamIds.add(streamId);
        Stream stream = new Stream(streamId, requestId.clone(), requestKind);
        state.current = stream;
        byte[] body = BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.HelloAck(
                BridgeRequestCodec.Sender.NARCOTICS, state.session.sessionUuid(), requestKind, requestId,
                heartbeatFeatures(state)));
        byte[] envelope = signAndGet(state, stream, 1L, positiveNow(), body);
        if (envelope == null) {
            retireStream(state);
            return false;
        }
        stream.nextEnvelopeCounter = 2L;
        stream.helloEnvelopeBytes = envelope;
        if (exactRequest != null) {
            cacheResponse(state, exactRequest, envelope);
        }
        dispatch(playerUuid, state, stream, envelope);
        boolean dispatched = state.current == stream;
        if (dispatched) {
            state.unacceptedHelloRecoveryAllowed = false;
        }
        return dispatched;
    }

    private boolean signAndDispatch(UUID playerUuid, PlayerState state, Stream stream,
                                    long counter, long issuedAt, byte[] exactBody) {
        byte[] envelope = signAndGet(state, stream, counter, issuedAt, exactBody);
        if (envelope == null) {
            retireStream(state);
            return false;
        }
        stream.nextEnvelopeCounter = counter + 1L;
        dispatch(playerUuid, state, stream, envelope);
        return state.current == stream;
    }

    private byte[] signAndGet(PlayerState state, Stream stream, long counter,
                              long issuedAt, byte[] exactBody) {
        if (counter <= 0 || issuedAt <= 0 || issuedAt > Long.MAX_VALUE - ENVELOPE_TTL_MILLIS
                || !bindingMatches(state, state.session, state.clientNonce)) {
            return null;
        }
        Optional<byte[]> signed = signer.sign(state.clientNonce, state.playerUuid, stream.streamId,
                counter, issuedAt, issuedAt + ENVELOPE_TTL_MILLIS, exactBody);
        return signed.filter(bytes -> bytes.length > 0 && isCurrent(state.playerUuid, state.session))
                .map(byte[]::clone).orElse(null);
    }

    private void dispatch(UUID playerUuid, PlayerState state, Stream stream, byte[] envelope) {
        if (state.current != stream || !isCurrent(playerUuid, state.session)) {
            retireStream(state);
            return;
        }
        try {
            packetSink.accept(playerUuid, envelope.clone());
        } catch (RuntimeException dispatchFailure) {
            retireStream(state);
        }
    }

    private boolean replayTo(UUID playerUuid, PlayerState state, Stream stream, byte[] envelope) {
        if (envelope != null && state.current == stream && isCurrent(playerUuid, state.session)) {
            dispatch(playerUuid, state, stream, envelope);
            return state.current == stream;
        }
        return false;
    }

    private boolean isFreshCachedResponse(PlayerState state, Stream stream, byte[] response) {
        long now = positiveNow();
        if (state == null || stream == null || response == null || now <= 0L) {
            return false;
        }
        try {
            BridgeEnvelope.Metadata metadata = BridgeEnvelopeCodec.decode(response).metadata();
            return metadata.recipientUuid().equals(state.playerUuid)
                    && metadata.clientNonce().equals(state.clientNonce)
                    && metadata.streamId().equals(stream.streamId)
                    && metadata.expiresAtMillis() > now;
        } catch (RuntimeException malformed) {
            return false;
        }
    }

    private void cacheResponse(PlayerState state, byte[] exactRequest, byte[] response) {
        if (exactRequest == null || state.lastRequestBytes == null
                || !Arrays.equals(state.lastRequestBytes, exactRequest)) {
            return;
        }
        state.lastRequestResponse = response == null ? null : response.clone();
    }

    private boolean isPendingReopenHelloAck(PlayerState state, Stream stream,
            BridgeRequestCodec.StreamReopenRequest reopen, byte[] response) {
        if (stream == null || state.current != stream || stream.phase != Phase.HELLO_SENT
                || stream.nextEnvelopeCounter != 2L || stream.clientAcknowledgedHello
                || stream.helloRequestKind != 1 || !Arrays.equals(stream.helloId, reopen.requestId())
                || response == null || !Arrays.equals(stream.helloEnvelopeBytes, response)) {
            return false;
        }
        try {
            BridgeEnvelope envelope = BridgeEnvelopeCodec.decode(response);
            BridgeEnvelope.Metadata metadata = envelope.metadata();
            BridgeControlBodyCodec.HelloAck body = (BridgeControlBodyCodec.HelloAck)
                    BridgeControlBodyCodec.decode(envelope.bodyBytes());
            return metadata.protocolVersion() == BridgeSigningPolicy.PROTOCOL_VERSION
                    && BridgeSigningPolicy.PINNED_KEY_ID.equals(metadata.keyId())
                    && metadata.counter() == 1L && metadata.recipientUuid().equals(state.playerUuid)
                    && metadata.clientNonce().equals(state.clientNonce)
                    && metadata.streamId().equals(stream.streamId)
                    && body.sender() == BridgeRequestCodec.Sender.NARCOTICS
                    && body.sessionUuid().equals(state.session.sessionUuid())
                    && body.requestKind() == 1 && Arrays.equals(body.requestId(), reopen.requestId());
        } catch (RuntimeException malformed) {
            return false;
        }
    }

    /** Re-signs an expired reopen ACK at counter one without rotating its pending stream. */
    private boolean renewExpiredReopenHelloAck(UUID playerUuid, PlayerState state,
            BridgeRequestCodec.StreamReopenRequest reopen, byte[] exactRequest, byte[] response) {
        Stream stream = state.current;
        if (!isPendingReopenHelloAck(state, stream, reopen, response)
                || state.lastRequestBytes == null || !Arrays.equals(state.lastRequestBytes, exactRequest)) {
            return false;
        }
        long now = positiveNow();
        if (now <= 0L) {
            return false;
        }
        try {
            if (BridgeEnvelopeCodec.decode(response).metadata().expiresAtMillis() > now) {
                return false;
            }
        } catch (RuntimeException malformed) {
            return false;
        }
        byte[] body = BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.HelloAck(
                BridgeRequestCodec.Sender.NARCOTICS, state.session.sessionUuid(), 1, reopen.requestId(),
                heartbeatFeatures(state)));
        byte[] refreshed = signAndGet(state, stream, 1L, now, body);
        if (refreshed != null) {
            stream.helloEnvelopeBytes = refreshed.clone();
            cacheResponse(state, exactRequest, refreshed);
            replayPendingHelloAck(playerUuid, state, stream, refreshed);
        }
        // Do not retire a retryable stream on temporary signing failure.
        return true;
    }

    private void replayPendingHelloAck(UUID playerUuid, PlayerState state, Stream stream, byte[] envelope) {
        if (envelope == null || state.current != stream || !isCurrent(playerUuid, state.session)) {
            return;
        }
        try {
            packetSink.accept(playerUuid, envelope.clone());
        } catch (RuntimeException ignored) {
            // Keep the unaccepted stream and its exact counted request available for another retry.
        }
    }

    private boolean bindSession(PlayerState state,
            ClientModSessionRegistry.VerifiedClientSession verifiedSession, String clientNonce) {
        if (!state.connectionEpoch.equals(verifiedSession.connectionEpoch())) {
            return false;
        }
        if (state.clientNonce != null && !state.clientNonce.equals(clientNonce)) {
            return false;
        }
        if (state.session == null || !state.session.sessionUuid().equals(verifiedSession.sessionUuid())) {
            retireStream(state);
            state.unacceptedHelloRecoveryAllowed = false;
            state.session = verifiedSession;
            state.nextRequestCounter = 1L;
            state.lastRequestCounter = 0L;
            state.lastRequestBytes = null;
            state.lastRequestResponse = null;
            state.requestExhausted = false;
        }
        state.clientNonce = clientNonce;
        return true;
    }

    private boolean bindingMatches(PlayerState state,
            ClientModSessionRegistry.VerifiedClientSession verifiedSession, String nonce) {
        return state != null && verifiedSession != null && nonce != null
                && state.playerUuid.equals(verifiedSession.playerUuid())
                && state.connectionEpoch.equals(verifiedSession.connectionEpoch())
                && state.session != null && state.session.sessionUuid().equals(verifiedSession.sessionUuid())
                && state.session.clientVersion().equals(verifiedSession.clientVersion())
                && Objects.equals(state.clientNonce, nonce)
                && isCurrent(state.playerUuid, verifiedSession);
    }

    private boolean isCurrent(UUID playerUuid,
            ClientModSessionRegistry.VerifiedClientSession expected) {
        if (playerUuid == null || expected == null) {
            return false;
        }
        return registry.current(playerUuid).filter(expected::equals).isPresent();
    }

    private long positiveNow() {
        long now = clock.getAsLong();
        return now > 0L ? now : -1L;
    }

    private static boolean isPlayerOnline(Player player) {
        return player != null && player.isOnline() && player.getUniqueId() != null;
    }

    private static boolean isCanonicalClientNonce(String value) {
        if (value == null || value.length() != BridgeSigningPolicy.CLIENT_NONCE_BASE64URL_LENGTH) {
            return false;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            return decoded.length == BridgeSigningPolicy.CLIENT_NONCE_BYTES
                    && Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(value);
        } catch (IllegalArgumentException malformed) {
            return false;
        }
    }

    private void retireStaleState(UUID playerUuid) {
        PlayerState state = players.get(playerUuid);
        if (state != null) {
            retireStream(state);
            state.unacceptedHelloRecoveryAllowed = false;
            state.session = null;
            state.nextRequestCounter = 1L;
            state.lastRequestCounter = 0L;
            state.lastRequestBytes = null;
            state.lastRequestResponse = null;
            state.requestExhausted = false;
        }
    }

    private void retireStream(PlayerState state) {
        if (state.current != null) {
            state.unacceptedHelloRecoveryAllowed = state.current.phase == Phase.HELLO_SENT
                    && !state.current.clientAcknowledgedHello;
            state.mostRecentlyRetiredStreamId = state.current.streamId;
            state.current = null;
        }
    }

    private void onSessionInvalidated(UUID playerUuid, ClientModSessionRegistry.InvalidationReason reason) {
        synchronized (this) {
            if (reason == ClientModSessionRegistry.InvalidationReason.QUIT
                    || reason == ClientModSessionRegistry.InvalidationReason.RECONNECT) {
                players.remove(playerUuid);
                return;
            }
            PlayerState state = players.get(playerUuid);
            if (state != null) {
                retireStaleState(playerUuid);
            }
        }
    }

    private static NarcoticsBridgeBodyCodec.Body copyWithTimestampAndFence(
            NarcoticsBridgeBodyCodec.Body body, long timestamp, long fence) {
        return new NarcoticsBridgeBodyCodec.Body(body.type(), body.commandSeq(), timestamp,
                body.sessionUuid(), body.effectId(), body.shaderpackId(), body.durationMillis(),
                body.intensity(), body.fadeInMillis(), body.fadeOutMillis(), body.mode(),
                body.clearPolicy(), body.source(), body.reason(), body.status(), body.visualInstanceId(),
                body.targetVisualInstanceId(), fence);
    }

    private enum Phase {
        HELLO_SENT,
        READY_PENDING_CONFIRM,
        READY
    }

    private static final class PlayerState {
        Player playerHandle;
        final UUID playerUuid;
        final UUID connectionEpoch;
        final Set<String> seenStreamIds = new HashSet<>();
        ClientModSessionRegistry.VerifiedClientSession session;
        String clientNonce;
        String mostRecentlyRetiredStreamId;
        boolean unacceptedHelloRecoveryAllowed;
        long nextRequestCounter = 1L;
        long lastRequestCounter;
        byte[] lastRequestBytes;
        byte[] lastRequestResponse;
        long lastReopenAtMillis;
        long lastHelloAckReplayAtMillis;
        boolean requestExhausted;
        Stream current;

        PlayerState(UUID playerUuid, UUID connectionEpoch) {
            this.playerUuid = playerUuid;
            this.connectionEpoch = connectionEpoch;
        }
    }

    private static final class Stream {
        long nextHeartbeatAtMillis;
        final String streamId;
        final byte[] helloId;
        final int helloRequestKind;
        long nextEnvelopeCounter = 1L;
        Phase phase = Phase.HELLO_SENT;
        byte[] helloEnvelopeBytes;
        byte[] readyRequestId;
        long readyRequestCounter;
        byte[] readyAckEnvelopeBytes;
        long readyAckExpiresAtMillis;
        byte[] confirmRequestBytes;
        byte[] readyConfirmAckEnvelopeBytes;
        boolean clientAcknowledgedHello;

        Stream(String streamId, byte[] helloId, int helloRequestKind) {
            this.streamId = streamId;
            this.helloId = helloId.clone();
            this.helloRequestKind = helloRequestKind;
        }
    }

    private static final class MessageDigestUtil {
        private MessageDigestUtil() {
        }

        static byte[] sha256(byte[] value) {
            try {
                return java.security.MessageDigest.getInstance("SHA-256").digest(value == null ? new byte[0] : value);
            } catch (java.security.NoSuchAlgorithmException impossible) {
                throw new IllegalStateException("SHA-256 is unavailable", impossible);
            }
        }

        static boolean constantTimeEquals(byte[] left, byte[] right) {
            return left != null && right != null && java.security.MessageDigest.isEqual(left, right);
        }
    }
}
