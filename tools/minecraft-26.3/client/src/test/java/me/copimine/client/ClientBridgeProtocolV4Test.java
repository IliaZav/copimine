package me.copimine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import me.copimine.protocol.BridgeControlBodyCodec;
import me.copimine.protocol.BridgeEnvelope;
import me.copimine.protocol.BridgeEnvelopeCodec;
import me.copimine.protocol.BridgeRequestCodec;
import me.copimine.protocol.BridgeSigningPolicy;
import me.copimine.protocol.EndEventBridgeBodyCodec;
import me.copimine.protocol.NarcoticsBridgeBodyCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class ClientBridgeProtocolV4Test {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID SESSION = UUID.fromString("00000000-0000-0000-0000-000000000022");
    private static final UUID EVENT = UUID.fromString("00000000-0000-0000-0000-000000000033");
    private static final long NOW = 1_800_000_000_000L;
    private static final long EXPIRES = NOW + 10_000L;

    private KeyPair trustedKey;
    private KeyPair otherKey;
    private ClientBridgeConnectionState state;
    private FakeTransport transport;
    private ClientBridgeProtocolV4 protocol;
    private final List<Object> applied = new ArrayList<>();
    private final List<BridgeRequestCodec.Sender> appliedSenders = new ArrayList<>();
    private final List<String> endEventLifecycle = new ArrayList<>();
    private final List<BridgeRequestCodec.Sender> clearedSenders = new ArrayList<>();
    private final List<BridgePayload> visualReports = new ArrayList<>();
    private int narcoticsApplications;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
        trustedKey = generator.generateKeyPair();
        otherKey = generator.generateKeyPair();
        state = new ClientBridgeConnectionState();
        transport = new FakeTransport();
        protocol = new ClientBridgeProtocolV4(state, trustedKey.getPublic(), transport,
                new ClientBridgeProtocolV4.MutationHandler() {
                    @Override
                    public void apply(BridgeRequestCodec.Sender sender, Object mutation) {
                        appliedSenders.add(sender);
                        applied.add(mutation);
                        if (sender == BridgeRequestCodec.Sender.END_EVENT
                                && mutation instanceof EndEventBridgeBodyCodec.Body body
                                && body.type() == EndEventBridgeBodyCodec.Type.CONTEXT_BIND) {
                            endEventLifecycle.add("bind:" + body.generation());
                        }
                    }

                    @Override
                    public void clear(BridgeRequestCodec.Sender sender) {
                        clearedSenders.add(sender);
                        if (sender == BridgeRequestCodec.Sender.END_EVENT) {
                            endEventLifecycle.add("clear");
                        }
                    }

                    @Override
                    public BridgePayload applyNarcotics(NarcoticsBridgeBodyCodec.Body body,
                            BridgeEnvelope.Metadata metadata) {
                        narcoticsApplications++;
                        appliedSenders.add(BridgeRequestCodec.Sender.NARCOTICS);
                        applied.add(body);
                        return BridgePayload.visualAck(body.sessionUuid().toString(), body.commandSeq(),
                                body.effectId(), ClientVisualReportPolicy.ackStatus(body.type()));
                    }

                    @Override
                    public void reportNarcotics(BridgePayload report) {
                        visualReports.add(report);
                    }
                }, new ClientBridgeReceiveRateLimiter(512, 0), BridgeEnvelopeCodec::decode);
        protocol.openConnection(PLAYER);
        protocol.acceptVerifiedSession(SESSION);
        transport.drainClientQueue();
    }

    @Test
    void helloAndReadyAcksMustMatchTheExactCorrelatedExchange() throws Exception {
        BridgeRequestCodec.ControlHello hello = lastRequest(BridgeRequestCodec.ControlHello.class);
        String streamId = streamId(1);
        byte[] helloEnvelope = signedEnvelope(trustedKey, streamId, 1, NOW,
                BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.HelloAck(
                        BridgeRequestCodec.Sender.END_EVENT, SESSION, 0, hello.helloId())));

        assertTrue(protocol.onEnvelope(helloEnvelope, PLAYER, NOW));
        transport.drainClientQueue();

        BridgeRequestCodec.SenderReady ready = lastRequest(BridgeRequestCodec.SenderReady.class);
        assertEquals(1, ready.requestCounter());
        assertEquals(streamId, ready.streamId());
        assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(helloEnvelope), ready.ackEnvelopeSha256());
        assertFalse(state.isReady(ClientBridgeConnectionState.Sender.END_EVENT));

        byte[] readyAckEnvelope = signedEnvelope(trustedKey, streamId, 2, NOW + 1,
                BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.SenderReadyAck(
                        BridgeRequestCodec.Sender.END_EVENT, SESSION, ready.requestId(), ready.requestCounter())));
        assertTrue(protocol.onEnvelope(readyAckEnvelope, PLAYER, NOW + 1));
        transport.drainClientQueue();

        BridgeRequestCodec.SenderReadyConfirm confirm = lastRequest(BridgeRequestCodec.SenderReadyConfirm.class);
        assertEquals(streamId, confirm.streamId());
        assertArrayEquals(ready.requestId(), confirm.readyRequestId());
        assertEquals(ready.requestCounter(), confirm.readyRequestCounter());
        assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(readyAckEnvelope), confirm.ackEnvelopeSha256());
        assertFalse(state.isReady(ClientBridgeConnectionState.Sender.END_EVENT),
                "queueing READY_CONFIRM does not prove that the server accepted it");
        assertFalse(state.isReady(ClientBridgeConnectionState.Sender.NARCOTICS));

        byte[] confirmAck = signedEnvelope(trustedKey, streamId, 3, NOW + 2,
                BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.SenderReadyConfirmAck(
                        BridgeRequestCodec.Sender.END_EVENT, SESSION, ready.requestId(), ready.requestCounter(),
                        MessageDigest.getInstance("SHA-256").digest(BridgeRequestCodec.encode(confirm)))));
        assertTrue(protocol.onEnvelope(confirmAck, PLAYER, NOW + 2));
        transport.drainClientQueue();
        assertTrue(protocol.isReady(BridgeRequestCodec.Sender.END_EVENT));
    }

    @Test
    void signedType254ReceiptRejectsTruncatedBody() throws Exception {
        PendingReadyConfirm pending = beginPendingReadyConfirm();
        byte[] validBody = BridgeControlBodyCodec.encode(confirmReceipt(pending));
        byte[] truncatedBody = java.util.Arrays.copyOf(validBody, validBody.length - 1);

        assertConfirmReceiptRejected(pending, truncatedBody, 3L);
    }

    @Test
    void signedType254ReceiptRejectsTrailingBodyBytes() throws Exception {
        PendingReadyConfirm pending = beginPendingReadyConfirm();
        byte[] validBody = BridgeControlBodyCodec.encode(confirmReceipt(pending));
        byte[] trailingBody = java.util.Arrays.copyOf(validBody, validBody.length + 1);

        assertConfirmReceiptRejected(pending, trailingBody, 3L);
    }

    @Test
    void signedType254ReceiptRejectsWrongSender() throws Exception {
        PendingReadyConfirm pending = beginPendingReadyConfirm();
        BridgeControlBodyCodec.SenderReadyConfirmAck valid = confirmReceipt(pending);
        byte[] body = BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.SenderReadyConfirmAck(
                BridgeRequestCodec.Sender.NARCOTICS, valid.sessionUuid(), valid.requestId(),
                valid.readyRequestCounter(), valid.confirmRequestSha256()));

        assertConfirmReceiptRejected(pending, body, 3L);
    }

    @Test
    void signedType254ReceiptRejectsWrongSession() throws Exception {
        PendingReadyConfirm pending = beginPendingReadyConfirm();
        BridgeControlBodyCodec.SenderReadyConfirmAck valid = confirmReceipt(pending);
        byte[] body = BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.SenderReadyConfirmAck(
                valid.sender(), UUID.fromString("00000000-0000-0000-0000-000000000099"),
                valid.requestId(), valid.readyRequestCounter(), valid.confirmRequestSha256()));

        assertConfirmReceiptRejected(pending, body, 3L);
    }

    @Test
    void signedType254ReceiptRejectsWrongReadyRequestId() throws Exception {
        PendingReadyConfirm pending = beginPendingReadyConfirm();
        BridgeControlBodyCodec.SenderReadyConfirmAck valid = confirmReceipt(pending);
        byte[] wrongRequestId = valid.requestId();
        wrongRequestId[0] ^= 1;
        byte[] body = BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.SenderReadyConfirmAck(
                valid.sender(), valid.sessionUuid(), wrongRequestId,
                valid.readyRequestCounter(), valid.confirmRequestSha256()));

        assertConfirmReceiptRejected(pending, body, 3L);
    }

    @Test
    void signedType254ReceiptRejectsWrongReadyRequestCounter() throws Exception {
        PendingReadyConfirm pending = beginPendingReadyConfirm();
        BridgeControlBodyCodec.SenderReadyConfirmAck valid = confirmReceipt(pending);
        byte[] body = BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.SenderReadyConfirmAck(
                valid.sender(), valid.sessionUuid(), valid.requestId(),
                valid.readyRequestCounter() + 1, valid.confirmRequestSha256()));

        assertConfirmReceiptRejected(pending, body, 3L);
    }

    @Test
    void signedType254ReceiptRejectsWrongStreamCounter() throws Exception {
        PendingReadyConfirm pending = beginPendingReadyConfirm();
        byte[] body = BridgeControlBodyCodec.encode(confirmReceipt(pending));

        assertConfirmReceiptRejected(pending, body, 4L);
    }

    @Test
    void signedType254ReceiptRejectsConfirmBodyDigestMismatch() throws Exception {
        PendingReadyConfirm pending = beginPendingReadyConfirm();
        BridgeControlBodyCodec.SenderReadyConfirmAck valid = confirmReceipt(pending);
        byte[] wrongDigest = valid.confirmRequestSha256();
        wrongDigest[0] ^= 1;
        byte[] body = BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.SenderReadyConfirmAck(
                valid.sender(), valid.sessionUuid(), valid.requestId(),
                valid.readyRequestCounter(), wrongDigest));

        assertConfirmReceiptRejected(pending, body, 3L);
    }

    @Test
    void changedInitialHelloAckCannotRotateAnActiveSenderStream() throws Exception {
        String oldStream = establishReady(BridgeRequestCodec.Sender.NARCOTICS, 5);
        byte[] activeVisual = narcoticsBody(SESSION, NOW + 3);
        assertTrue(protocol.onEnvelope(signedEnvelope(trustedKey, oldStream, 4, NOW + 3, activeVisual),
                PLAYER, NOW + 3));
        transport.drainClientQueue();
        assertEquals(1, narcoticsApplications);
        assertTrue(clearedSenders.isEmpty());

        BridgeRequestCodec.ControlHello hello = lastRequest(BridgeRequestCodec.ControlHello.class);
        String replacementStream = streamId(55);
        byte[] replacementHelloAck = signedEnvelope(trustedKey, replacementStream, 1, NOW + 4,
                BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.HelloAck(
                        BridgeRequestCodec.Sender.NARCOTICS, SESSION, 0, hello.helloId())));

        assertTrue(protocol.onEnvelope(replacementHelloAck, PLAYER, NOW + 4));
        transport.drainClientQueue();

        assertTrue(clearedSenders.isEmpty(), "an invalid initial ACK must not mutate the active sender visuals");
        assertEquals(oldStream,
                state.activeStream(ClientBridgeConnectionState.Sender.NARCOTICS).orElseThrow());
        assertTrue(state.isReady(ClientBridgeConnectionState.Sender.NARCOTICS),
                "a duplicate initial hello cannot rotate or retire an active stream");
    }

    @Test
    void missingInitialHelloAckRetriesTheSameControlHelloInsteadOfReopeningAnUnknownStream() throws Exception {
        byte[] originalHello = transport.sentRequests.getFirst().clone();
        assertTrue(BridgeRequestCodec.decode(originalHello) instanceof BridgeRequestCodec.ControlHello);

        protocol.tick(NOW + 3_000L);

        assertArrayEquals(originalHello, transport.sentRequests.getLast(),
                "a lost hello must be retried byte-for-byte with the same hello ID");
        assertEquals(2, transport.sentRequests.stream()
                .map(BridgeRequestCodec::decode)
                .filter(BridgeRequestCodec.ControlHello.class::isInstance)
                .count());
        assertFalse(transport.sentRequests.stream()
                .map(BridgeRequestCodec::decode)
                .anyMatch(BridgeRequestCodec.StreamReopenRequest.class::isInstance),
                "the server cannot reopen a stream it has not created");
    }

    @Test
    void expiredLostInitialHelloAckRecoversWithEmptyExpectedStreamReopen() throws Exception {
        int initialRequestCount = transport.sentRequests.size();
        protocol.tick(NOW + 31_001L);

        BridgeRequestCodec.StreamReopenRequest reopen = lastRequestFor(BridgeRequestCodec.Sender.END_EVENT,
                BridgeRequestCodec.StreamReopenRequest.class);
        assertTrue(reopen.expectedStreamId().isEmpty(),
                "after the hello ACK cache expires, recover only the missing sender with an empty expected stream");
        assertTrue(transport.sentRequests.subList(initialRequestCount, transport.sentRequests.size()).stream()
                .anyMatch(bytes -> MessageDigest.isEqual(bytes, transport.sentRequests.getFirst())),
                "the exact hello must remain in flight so a server that missed it can bind this client session");

        String recoveredStream = streamId(31);
        transport.nowMillis = NOW + 31_002L;
        byte[] recoveredHelloAck = signedEnvelope(trustedKey, recoveredStream, 1, NOW + 31_002L,
                BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.HelloAck(
                        BridgeRequestCodec.Sender.END_EVENT, SESSION, 1, reopen.requestId())));
        assertTrue(protocol.onEnvelope(recoveredHelloAck, PLAYER, NOW + 31_002L));
        transport.drainClientQueue();

        assertEquals(recoveredStream,
                state.activeStream(ClientBridgeConnectionState.Sender.END_EVENT).orElseThrow());
        assertEquals(recoveredStream,
                lastRequestFor(BridgeRequestCodec.Sender.END_EVENT, BridgeRequestCodec.SenderReady.class).streamId());
    }

    @Test
    void initialHelloKeepsRetryingAlongsideEmptyRecoveryUntilServerStateExists() throws Exception {
        byte[] initialHello = transport.sentRequests.getFirst().clone();
        int initialRequestCount = transport.sentRequests.size();

        protocol.tick(NOW + 31_001L);

        assertTrue(transport.sentRequests.subList(initialRequestCount, transport.sentRequests.size()).stream()
                .anyMatch(bytes -> MessageDigest.isEqual(bytes, initialHello)),
                "the client must keep retransmitting ControlHello so a server that missed the first one can bind state");
        BridgeRequestCodec.StreamReopenRequest reopen = lastRequestFor(BridgeRequestCodec.Sender.END_EVENT,
                BridgeRequestCodec.StreamReopenRequest.class);
        assertTrue(reopen.expectedStreamId().isEmpty());

        transport.nowMillis = NOW + 31_002L;
        String helloStream = streamId(33);
        byte[] helloAck = signedEnvelope(trustedKey, helloStream, 1, NOW + 31_002L,
                BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.HelloAck(
                        BridgeRequestCodec.Sender.END_EVENT, SESSION, 0,
                        firstRequest(BridgeRequestCodec.ControlHello.class).helloId())));
        assertTrue(protocol.onEnvelope(helloAck, PLAYER, NOW + 31_002L));
        transport.drainClientQueue();
        assertTrue(state.activeStream(ClientBridgeConnectionState.Sender.END_EVENT).isEmpty(),
                "an initial HelloAck cannot supersede the already-pending empty-stream recovery request");

        String recoveredStream = streamId(34);
        byte[] reopenAck = signedEnvelope(trustedKey, recoveredStream, 1, NOW + 31_003L,
                BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.HelloAck(
                        BridgeRequestCodec.Sender.END_EVENT, SESSION, 1, reopen.requestId())));
        transport.nowMillis = NOW + 31_003L;
        assertTrue(protocol.onEnvelope(reopenAck, PLAYER, NOW + 31_003L));
        transport.drainClientQueue();
        assertEquals(recoveredStream,
                state.activeStream(ClientBridgeConnectionState.Sender.END_EVENT).orElseThrow());
    }

    @Test
    void lateInitialHelloAckCannotReplaceAcceptedReopenStream() throws Exception {
        BridgeRequestCodec.ControlHello initialHello = firstRequest(BridgeRequestCodec.ControlHello.class);
        protocol.tick(NOW + 31_001L);
        BridgeRequestCodec.StreamReopenRequest reopen = lastRequestFor(BridgeRequestCodec.Sender.END_EVENT,
                BridgeRequestCodec.StreamReopenRequest.class);
        String recoveredStream = streamId(35);
        byte[] reopenAck = signedEnvelope(trustedKey, recoveredStream, 1, NOW + 31_002L,
                BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.HelloAck(
                        BridgeRequestCodec.Sender.END_EVENT, SESSION, 1, reopen.requestId())));
        transport.nowMillis = NOW + 31_002L;
        assertTrue(protocol.onEnvelope(reopenAck, PLAYER, NOW + 31_002L));
        transport.drainClientQueue();

        long readyRequestsBeforeLateHello = transport.sentRequests.stream()
                .map(BridgeRequestCodec::decode)
                .filter(request -> request instanceof BridgeRequestCodec.SenderReady ready
                        && ready.sender() == BridgeRequestCodec.Sender.END_EVENT)
                .count();
        String lateHelloStream = streamId(36);
        byte[] lateHelloAck = signedEnvelope(trustedKey, lateHelloStream, 1, NOW + 31_003L,
                BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.HelloAck(
                        BridgeRequestCodec.Sender.END_EVENT, SESSION, 0, initialHello.helloId())));
        transport.nowMillis = NOW + 31_003L;
        assertTrue(protocol.onEnvelope(lateHelloAck, PLAYER, NOW + 31_003L));
        transport.drainClientQueue();

        assertEquals(recoveredStream,
                state.activeStream(ClientBridgeConnectionState.Sender.END_EVENT).orElseThrow(),
                "the delayed initial ACK must not replace a stream opened through recovery");
        assertEquals(readyRequestsBeforeLateHello, transport.sentRequests.stream()
                .map(BridgeRequestCodec::decode)
                .filter(request -> request instanceof BridgeRequestCodec.SenderReady ready
                        && ready.sender() == BridgeRequestCodec.Sender.END_EVENT)
                .count(), "the stale initial ACK must not start a second readiness handshake");
    }

    @Test
    void malformedAndForgedPacketsConsumeAdmissionBeforeDecodeAndReconnectResetsIt() throws Exception {
        AtomicInteger decodeAttempts = new AtomicInteger();
        ClientBridgeReceiveRateLimiter limiter = new ClientBridgeReceiveRateLimiter(2, 0);
        ClientBridgeProtocolV4 limitedProtocol = new ClientBridgeProtocolV4(state, trustedKey.getPublic(), transport,
                (sender, mutation) -> { }, limiter, raw -> {
                    decodeAttempts.incrementAndGet();
                    return BridgeEnvelopeCodec.decode(raw);
                });
        limitedProtocol.openConnection(PLAYER);
        limitedProtocol.acceptVerifiedSession(SESSION);

        assertFalse(limitedProtocol.onEnvelope(new BridgeV4Payload(new byte[] {0}), PLAYER, NOW));
        assertEquals(1, decodeAttempts.get(), "the malformed packet must reach the bounded decoder once");

        String stream = streamId(20);
        BridgeRequestCodec.ControlHello hello = lastRequest(BridgeRequestCodec.ControlHello.class);
        byte[] helloAckBody = BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.HelloAck(
                BridgeRequestCodec.Sender.END_EVENT, SESSION, 0, hello.helloId()));
        byte[] forged = signedEnvelope(otherKey, stream, 1, NOW, helloAckBody);
        assertFalse(limitedProtocol.onEnvelope(new BridgeV4Payload(forged), PLAYER, NOW));
        assertEquals(2, decodeAttempts.get(), "the forged signature must consume the second packet slot");

        byte[] trusted = signedEnvelope(trustedKey, stream, 1, NOW, helloAckBody);
        assertFalse(limitedProtocol.onEnvelope(new BridgeV4Payload(trusted), PLAYER, NOW));
        assertEquals(2, decodeAttempts.get(), "an over-limit trusted packet must be rejected before decode");

        limitedProtocol.close();
        limitedProtocol.openConnection(PLAYER);
        limitedProtocol.acceptVerifiedSession(SESSION);
        hello = lastRequest(BridgeRequestCodec.ControlHello.class);
        helloAckBody = BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.HelloAck(
                BridgeRequestCodec.Sender.END_EVENT, SESSION, 0, hello.helloId()));
        byte[] afterReconnect = signedEnvelope(trustedKey, streamId(21), 1, NOW, helloAckBody);

        assertTrue(limitedProtocol.onEnvelope(new BridgeV4Payload(afterReconnect), PLAYER, NOW));
        assertEquals(3, decodeAttempts.get(), "a new play connection must start with a fresh admission budget");
    }

    @Test
    void wrongKeyWrongNonceAndModifiedRawBodyDoNotRetireAnEstablishedStream() throws Exception {
        String stream = establishReady(BridgeRequestCodec.Sender.NARCOTICS, 7);
        byte[] body = narcoticsBody(SESSION, NOW + 2);

        assertFalse(protocol.onEnvelope(signedEnvelope(otherKey, stream, 4, NOW + 2, body), PLAYER, NOW + 2));

        byte[] wrongNonce = signedEnvelope(trustedKey, stream, 4, NOW + 2, body, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        assertFalse(protocol.onEnvelope(wrongNonce, PLAYER, NOW + 2));

        BridgeEnvelope valid = BridgeEnvelopeCodec.decode(signedEnvelope(trustedKey, stream, 4, NOW + 2, body));
        byte[] modifiedBody = valid.bodyBytes();
        modifiedBody[6] ^= 1;
        BridgeEnvelope modified = new BridgeEnvelope(valid.metadata(), modifiedBody, valid.signature());
        assertFalse(protocol.onEnvelope(BridgeEnvelopeCodec.encode(modified), PLAYER, NOW + 2));

        assertEquals(stream, state.activeStream(ClientBridgeConnectionState.Sender.NARCOTICS).orElseThrow());
        assertTrue(state.isReady(ClientBridgeConnectionState.Sender.NARCOTICS));
        assertTrue(applied.isEmpty());
    }

    @Test
    void validAuthenticatedMutationIsAppliedOnlyAfterClientThreadRevalidation() throws Exception {
        String stream = establishReady(BridgeRequestCodec.Sender.NARCOTICS, 8);
        byte[] body = narcoticsBody(SESSION, NOW + 2);
        byte[] envelope = signedEnvelope(trustedKey, stream, 4, NOW + 2, body);

        assertTrue(protocol.onEnvelope(envelope, PLAYER, NOW + 2));
        assertTrue(applied.isEmpty());

        transport.drainClientQueue();

        assertEquals(1, applied.size());
        assertEquals(BridgeRequestCodec.Sender.NARCOTICS, appliedSenders.getFirst());
        assertTrue(applied.getFirst() instanceof NarcoticsBridgeBodyCodec.Body);
    }

    @Test
    void replayedAuthenticatedMutationIsDroppedWithoutRetiringItsStream() throws Exception {
        String stream = establishReady(BridgeRequestCodec.Sender.NARCOTICS, 10);
        String endEventStream = establishReady(BridgeRequestCodec.Sender.END_EVENT, 11);
        byte[] body = narcoticsBody(SESSION, NOW + 2);
        byte[] envelope = signedEnvelope(trustedKey, stream, 4, NOW + 2, body);

        assertTrue(protocol.onEnvelope(envelope, PLAYER, NOW + 2));
        transport.drainClientQueue();
        assertEquals(1, applied.size());

        assertTrue(protocol.onEnvelope(envelope, PLAYER, NOW + 2));
        transport.drainClientQueue();

        assertEquals(1, applied.size());
        assertEquals(stream, state.activeStream(ClientBridgeConnectionState.Sender.NARCOTICS).orElseThrow());
        assertTrue(state.isReady(ClientBridgeConnectionState.Sender.NARCOTICS));
        assertEquals(endEventStream, state.activeStream(ClientBridgeConnectionState.Sender.END_EVENT).orElseThrow());
        assertTrue(state.isReady(ClientBridgeConnectionState.Sender.END_EVENT));
    }

    @Test
    void expiredReplayCannotRetireAnActiveStream() throws Exception {
        String stream = establishReady(BridgeRequestCodec.Sender.NARCOTICS, 26);
        byte[] envelope = signedEnvelope(trustedKey, stream, 4, NOW + 2,
                narcoticsBody(SESSION, NOW + 2));

        assertTrue(protocol.onEnvelope(envelope, PLAYER, NOW + 2));
        transport.drainClientQueue();
        assertEquals(1, applied.size());
        assertTrue(state.isReady(ClientBridgeConnectionState.Sender.NARCOTICS));

        assertTrue(protocol.onEnvelope(envelope, PLAYER, NOW + 2));
        transport.nowMillis = NOW + 5_002;
        transport.drainClientQueue();

        assertEquals(1, applied.size());
        assertEquals(stream, state.activeStream(ClientBridgeConnectionState.Sender.NARCOTICS).orElseThrow());
        assertTrue(state.isReady(ClientBridgeConnectionState.Sender.NARCOTICS));
    }

    @Test
    void packetFromPreviousServerConnectionIsRejectedAfterServerSwitch() throws Exception {
        String oldStream = establishReady(BridgeRequestCodec.Sender.NARCOTICS, 12);
        String oldNonce = state.currentConnection().orElseThrow().nonce();
        byte[] stalePacket = signedEnvelope(trustedKey, oldStream, 4, NOW + 2,
                narcoticsBody(SESSION, NOW + 2));

        protocol.close();
        protocol.openConnection(PLAYER);
        protocol.acceptVerifiedSession(SESSION);
        transport.drainClientQueue();

        assertFalse(oldNonce.equals(state.currentConnection().orElseThrow().nonce()));
        assertFalse(protocol.onEnvelope(stalePacket, PLAYER, NOW + 2));
        transport.drainClientQueue();

        assertTrue(applied.isEmpty());
        assertTrue(state.activeStream(ClientBridgeConnectionState.Sender.NARCOTICS).isEmpty());
        assertFalse(state.isReady(ClientBridgeConnectionState.Sender.NARCOTICS));
    }

    @Test
    void queuedMutationRechecksLivePlayerNonceStreamAndExpiryBeforeApplying() throws Exception {
        String stream = establishReady(BridgeRequestCodec.Sender.NARCOTICS, 9);
        byte[] body = narcoticsBody(SESSION, NOW + 2);
        byte[] envelope = signedEnvelope(trustedKey, stream, 4, NOW + 2, body);

        assertTrue(protocol.onEnvelope(envelope, PLAYER, NOW + 2));
        transport.currentPlayer = UUID.fromString("00000000-0000-0000-0000-000000000099");
        transport.drainClientQueue();
        assertTrue(applied.isEmpty());
        assertTrue(state.isReady(ClientBridgeConnectionState.Sender.NARCOTICS));

        transport.currentPlayer = PLAYER;
        byte[] nextBody = narcoticsBody(SESSION, NOW + 3);
        byte[] nextEnvelope = signedEnvelope(trustedKey, stream, 4, NOW + 3, nextBody);
        assertTrue(protocol.onEnvelope(nextEnvelope, PLAYER, NOW + 3));
        transport.nowMillis = EXPIRES;
        transport.drainClientQueue();
        assertTrue(applied.isEmpty());
        assertTrue(state.activeStream(ClientBridgeConnectionState.Sender.NARCOTICS).isEmpty());
        assertTrue(state.activeStream(ClientBridgeConnectionState.Sender.END_EVENT).isEmpty());
    }

    @Test
    void replacementContextBindClearsEndEventManagersBeforeApplyingTheNewGeneration() throws Exception {
        String stream = establishReady(BridgeRequestCodec.Sender.END_EVENT, 16);

        byte[] firstBind = endEventContextBind(1, NOW + 2);
        assertTrue(protocol.onEnvelope(signedEnvelope(trustedKey, stream, 4, NOW + 2, firstBind), PLAYER, NOW + 2));
        transport.drainClientQueue();
        assertEquals(List.of("clear", "bind:1"), endEventLifecycle);

        byte[] replacementBind = endEventContextBind(2, NOW + 3);
        assertTrue(protocol.onEnvelope(signedEnvelope(trustedKey, stream, 5, NOW + 3, replacementBind), PLAYER, NOW + 3));
        transport.drainClientQueue();
        assertEquals(List.of("clear", "bind:1", "clear", "bind:2"), endEventLifecycle);
    }

    @Test
    void explicitContextUnbindAllowsTheSameEventGenerationToRebind() throws Exception {
        String stream = establishReady(BridgeRequestCodec.Sender.END_EVENT, 17);
        byte[] firstBind = endEventContextBind(1, NOW + 2);
        assertTrue(protocol.onEnvelope(signedEnvelope(trustedKey, stream, 4, NOW + 2, firstBind), PLAYER, NOW + 2));
        transport.drainClientQueue();

        byte[] unbind = endEventContextUnbind(1, NOW + 3);
        assertTrue(protocol.onEnvelope(signedEnvelope(trustedKey, stream, 5, NOW + 3, unbind), PLAYER, NOW + 3));
        transport.drainClientQueue();

        byte[] rebound = endEventContextBind(1, NOW + 4);
        assertTrue(protocol.onEnvelope(signedEnvelope(trustedKey, stream, 6, NOW + 4, rebound), PLAYER, NOW + 4),
                "a viewer returning to the same event may rebind its exact current generation after CONTEXT_UNBIND");
        transport.drainClientQueue();
        assertEquals(List.of("clear", "bind:1", "clear", "bind:1"), endEventLifecycle);
    }

    @Test
    void contextUnbindDoesNotAuthorizeSameGenerationForAnotherEvent() throws Exception {
        String stream = establishReady(BridgeRequestCodec.Sender.END_EVENT, 18);
        UUID otherEvent = new UUID(0L, 0x44L);
        byte[] firstBind = endEventContextBind(1, NOW + 2);
        assertTrue(protocol.onEnvelope(signedEnvelope(trustedKey, stream, 4, NOW + 2, firstBind), PLAYER, NOW + 2));
        transport.drainClientQueue();

        byte[] unbind = endEventContextUnbind(1, NOW + 3);
        assertTrue(protocol.onEnvelope(signedEnvelope(trustedKey, stream, 5, NOW + 3, unbind), PLAYER, NOW + 3));
        transport.drainClientQueue();

        byte[] unrelatedBind = endEventContextBind(otherEvent, 1, NOW + 4,
                "the_end|0,0,0|10,10,10");
        assertTrue(protocol.onEnvelope(
                signedEnvelope(trustedKey, stream, 6, NOW + 4, unrelatedBind), PLAYER, NOW + 4));
        transport.drainClientQueue();
        assertEquals(1L, endEventLifecycle.stream().filter("bind:1"::equals).count());
        assertTrue(state.activeStream(ClientBridgeConnectionState.Sender.END_EVENT).isEmpty());
    }

    @Test
    void contextUnbindDoesNotAuthorizeSameGenerationWithChangedBounds() throws Exception {
        String stream = establishReady(BridgeRequestCodec.Sender.END_EVENT, 19);
        byte[] firstBind = endEventContextBind(1, NOW + 2);
        assertTrue(protocol.onEnvelope(signedEnvelope(trustedKey, stream, 4, NOW + 2, firstBind), PLAYER, NOW + 2));
        transport.drainClientQueue();

        byte[] unbind = endEventContextUnbind(1, NOW + 3);
        assertTrue(protocol.onEnvelope(signedEnvelope(trustedKey, stream, 5, NOW + 3, unbind), PLAYER, NOW + 3));
        transport.drainClientQueue();

        byte[] changedBounds = endEventContextBind(EVENT, 1, NOW + 4,
                "the_end|0,0,0|11,10,10");
        assertTrue(protocol.onEnvelope(
                signedEnvelope(trustedKey, stream, 6, NOW + 4, changedBounds), PLAYER, NOW + 4));
        transport.drainClientQueue();
        assertEquals(1L, endEventLifecycle.stream().filter("bind:1"::equals).count());
        assertTrue(state.activeStream(ClientBridgeConnectionState.Sender.END_EVENT).isEmpty());
    }

    @Test
    void streamScopedGenerationFenceSupportsMoreThanSixtyFourEvents() throws Exception {
        String stream = establishReady(BridgeRequestCodec.Sender.END_EVENT, 20);
        long counter = 4L;
        for (long generation = 1; generation <= 70; generation++) {
            UUID event = new UUID(0L, 0x1000L + generation);
            long timestamp = NOW + counter;
            byte[] bind = endEventContextBind(event, generation, timestamp,
                    "the_end|0,0,0|10,10,10");
            assertTrue(protocol.onEnvelope(
                    signedEnvelope(trustedKey, stream, counter++, timestamp, bind), PLAYER, timestamp),
                    "global generation " + generation + " should bind without UUID history exhaustion");
            transport.drainClientQueue();

            timestamp = NOW + counter;
            byte[] unbind = endEventContextUnbind(event, generation, timestamp);
            assertTrue(protocol.onEnvelope(
                    signedEnvelope(trustedKey, stream, counter++, timestamp, unbind), PLAYER, timestamp));
            transport.drainClientQueue();
        }
        assertEquals(java.util.OptionalLong.of(70L), state.generationHighWater());
    }

    @Test
    void freshStreamReassertsTheCurrentContextAndStartsItsOwnGenerationFence() throws Exception {
        String stream = establishReady(BridgeRequestCodec.Sender.END_EVENT, 21);
        byte[] firstBind = endEventContextBind(100, NOW + 3);
        assertTrue(protocol.onEnvelope(signedEnvelope(trustedKey, stream, 4, NOW + 3, firstBind), PLAYER, NOW + 3));
        transport.drainClientQueue();
        assertEquals(java.util.OptionalLong.of(100L), state.generationHighWater());

        assertTrue(protocol.requestReopen(BridgeRequestCodec.Sender.END_EVENT));
        BridgeRequestCodec.StreamReopenRequest reopen = lastRequest(BridgeRequestCodec.StreamReopenRequest.class);
        String replacementStream = streamId(22);
        byte[] helloAck = signedEnvelope(trustedKey, replacementStream, 1, NOW + 4,
                BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.HelloAck(
                        BridgeRequestCodec.Sender.END_EVENT, SESSION, 1, reopen.requestId())));
        assertTrue(protocol.onEnvelope(helloAck, PLAYER, NOW + 4));
        transport.drainClientQueue();

        BridgeRequestCodec.SenderReady ready = lastRequestFor(
                BridgeRequestCodec.Sender.END_EVENT, BridgeRequestCodec.SenderReady.class);
        byte[] readyAck = signedEnvelope(trustedKey, replacementStream, 2, NOW + 5,
                BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.SenderReadyAck(
                        BridgeRequestCodec.Sender.END_EVENT, SESSION,
                        ready.requestId(), ready.requestCounter())));
        assertTrue(protocol.onEnvelope(readyAck, PLAYER, NOW + 5));
        transport.drainClientQueue();

        BridgeRequestCodec.SenderReadyConfirm confirm = lastRequestFor(
                BridgeRequestCodec.Sender.END_EVENT, BridgeRequestCodec.SenderReadyConfirm.class);
        byte[] confirmAck = signedEnvelope(trustedKey, replacementStream, 3, NOW + 6,
                BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.SenderReadyConfirmAck(
                        BridgeRequestCodec.Sender.END_EVENT, SESSION, ready.requestId(), ready.requestCounter(),
                        MessageDigest.getInstance("SHA-256").digest(BridgeRequestCodec.encode(confirm)))));
        assertTrue(protocol.onEnvelope(confirmAck, PLAYER, NOW + 6));
        transport.drainClientQueue();

        byte[] reassertedBind = endEventContextBind(100, NOW + 7);
        assertTrue(protocol.onEnvelope(
                signedEnvelope(trustedKey, replacementStream, 4, NOW + 7, reassertedBind), PLAYER, NOW + 7));
        transport.drainClientQueue();
        assertEquals(java.util.OptionalLong.of(100L), state.generationHighWater());
        assertEquals(2L, endEventLifecycle.stream().filter("bind:100"::equals).count());
    }

    @Test
    void exactRetainedNarcoticsDuplicateReplaysItsOriginalReportWithoutReapplying() throws Exception {
        String stream = establishReady(BridgeRequestCodec.Sender.NARCOTICS, 18);
        byte[] start = narcoticsBody(SESSION, NOW + 2);
        byte[] firstEnvelope = signedEnvelope(trustedKey, stream, 4, NOW + 2, start);

        assertTrue(protocol.onEnvelope(firstEnvelope, PLAYER, NOW + 2));
        transport.drainClientQueue();
        assertEquals(1, narcoticsApplications);
        assertEquals(1, visualReports.size());
        BridgePayload originalReport = visualReports.getFirst();

        byte[] duplicate = narcoticsBody(SESSION, NOW + 3);
        assertTrue(protocol.onEnvelope(signedEnvelope(trustedKey, stream, 5, NOW + 3, duplicate), PLAYER, NOW + 3));
        transport.drainClientQueue();

        assertEquals(1, narcoticsApplications);
        assertEquals(2, visualReports.size());
        assertEquals(originalReport, visualReports.get(1));
        assertEquals(stream, state.activeStream(ClientBridgeConnectionState.Sender.NARCOTICS).orElseThrow());
    }

    @Test
    void retainedOlderNarcoticsCommandReplaysItsOriginalReportAfterNewerCommands() throws Exception {
        String stream = establishReady(BridgeRequestCodec.Sender.NARCOTICS, 30);
        byte[] firstCommand = narcoticsBody(SESSION, NOW + 2, 1L);
        assertTrue(protocol.onEnvelope(signedEnvelope(trustedKey, stream, 4, NOW + 2, firstCommand), PLAYER, NOW + 2));
        transport.drainClientQueue();
        BridgePayload firstReport = visualReports.getFirst();

        byte[] secondCommand = narcoticsBody(SESSION, NOW + 3, 2L);
        assertTrue(protocol.onEnvelope(signedEnvelope(trustedKey, stream, 5, NOW + 3, secondCommand), PLAYER, NOW + 3));
        transport.drainClientQueue();
        assertEquals(2, narcoticsApplications);
        assertEquals(2, visualReports.size());

        byte[] retryOfFirst = narcoticsBody(SESSION, NOW + 4, 1L);
        assertTrue(protocol.onEnvelope(signedEnvelope(trustedKey, stream, 6, NOW + 4, retryOfFirst), PLAYER, NOW + 4));
        transport.drainClientQueue();

        assertEquals(2, narcoticsApplications, "an older retained command receipt must not reapply the effect");
        assertEquals(3, visualReports.size(), "the original report for command 1 must be replayed");
        assertEquals(firstReport, visualReports.get(2));
        assertEquals(stream, state.activeStream(ClientBridgeConnectionState.Sender.NARCOTICS).orElseThrow());
    }

    @Test
    void evictedNarcoticsCommandReceiptRemainsStale() throws Exception {
        String stream = establishReady(BridgeRequestCodec.Sender.NARCOTICS, 31);
        for (long commandSeq = 1; commandSeq <= 257; commandSeq++) {
            long timestamp = NOW + commandSeq * 100L;
            byte[] body = narcoticsBody(SESSION, timestamp, commandSeq);
            assertTrue(protocol.onEnvelope(signedEnvelope(trustedKey, stream, commandSeq + 3,
                    timestamp, body), PLAYER, timestamp));
            transport.drainClientQueue();
        }

        int applicationsBeforeRetry = narcoticsApplications;
        int reportsBeforeRetry = visualReports.size();
        long timestamp = NOW + 300;
        byte[] evictedRetry = narcoticsBody(SESSION, timestamp, 1L);
        assertTrue(protocol.onEnvelope(signedEnvelope(trustedKey, stream, 261, timestamp,
                evictedRetry), PLAYER, timestamp));
        transport.drainClientQueue();

        assertEquals(applicationsBeforeRetry, narcoticsApplications);
        assertEquals(reportsBeforeRetry, visualReports.size());
        assertEquals(stream, state.activeStream(ClientBridgeConnectionState.Sender.NARCOTICS).orElseThrow());
    }

    @Test
    void unavailableV4TransportDoesNotConsumeACountedRequestCounter() throws Exception {
        establishReady(BridgeRequestCodec.Sender.END_EVENT, 19);
        long counterBefore = state.lastRequestCounter(ClientBridgeConnectionState.Sender.END_EVENT);
        int requestCountBefore = transport.sentRequests.size();
        transport.channelAvailable = false;

        assertFalse(protocol.sendPrisonerAbility(EVENT, 1, "HEAL", Optional.empty()));
        assertEquals(counterBefore, state.lastRequestCounter(ClientBridgeConnectionState.Sender.END_EVENT));
        assertEquals(requestCountBefore, transport.sentRequests.size());

        transport.channelAvailable = true;
        assertTrue(protocol.sendPrisonerAbility(EVENT, 1, "HEAL", Optional.empty()));
        BridgeRequestCodec.PrisonerAbilityRequest sent = lastRequest(BridgeRequestCodec.PrisonerAbilityRequest.class);
        assertEquals(counterBefore + 1, sent.requestCounter());
    }

    @Test
    void transportSendFailureRollsBackTheUnsentCountedRequestCounter() throws Exception {
        establishReady(BridgeRequestCodec.Sender.END_EVENT, 20);
        long counterBefore = state.lastRequestCounter(ClientBridgeConnectionState.Sender.END_EVENT);
        transport.failNextSend = true;

        assertFalse(protocol.sendPrisonerAbility(EVENT, 1, "HEAL", Optional.empty()));
        assertEquals(counterBefore, state.lastRequestCounter(ClientBridgeConnectionState.Sender.END_EVENT));

        assertTrue(protocol.sendPrisonerAbility(EVENT, 1, "HEAL", Optional.empty()));
        BridgeRequestCodec.PrisonerAbilityRequest sent = lastRequest(BridgeRequestCodec.PrisonerAbilityRequest.class);
        assertEquals(counterBefore + 1, sent.requestCounter());
    }

    @Test
    void invalidCountedRequestDoesNotConsumeItsSenderCounter() throws Exception {
        establishReady(BridgeRequestCodec.Sender.END_EVENT, 24);
        long counterBefore = state.lastRequestCounter(ClientBridgeConnectionState.Sender.END_EVENT);

        assertFalse(protocol.sendPrisonerAbility(EVENT, 1, "UNKNOWN", Optional.empty()));
        assertEquals(counterBefore, state.lastRequestCounter(ClientBridgeConnectionState.Sender.END_EVENT));

        assertTrue(protocol.sendPrisonerAbility(EVENT, 1, "HEAL", Optional.empty()));
        assertEquals(counterBefore + 1,
                lastRequest(BridgeRequestCodec.PrisonerAbilityRequest.class).requestCounter());
    }

    @Test
    void unsentSenderReadyCanRetryTheExactHelloAckWithoutConsumingItsCounter() throws Exception {
        BridgeRequestCodec.ControlHello hello = lastRequest(BridgeRequestCodec.ControlHello.class);
        String stream = streamId(21);
        byte[] helloAck = signedEnvelope(trustedKey, stream, 1, NOW,
                BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.HelloAck(
                        BridgeRequestCodec.Sender.END_EVENT, SESSION, 0, hello.helloId())));
        transport.failNextSend = true;

        assertTrue(protocol.onEnvelope(helloAck, PLAYER, NOW));
        transport.drainClientQueue();
        assertEquals(0, state.lastRequestCounter(ClientBridgeConnectionState.Sender.END_EVENT));
        assertFalse(transport.sentRequests.stream()
                .map(BridgeRequestCodec::decode)
                .anyMatch(BridgeRequestCodec.SenderReady.class::isInstance));

        assertTrue(protocol.onEnvelope(helloAck, PLAYER, NOW));
        transport.drainClientQueue();
        assertEquals(1, state.lastRequestCounter(ClientBridgeConnectionState.Sender.END_EVENT));
        assertEquals(1, lastRequestFor(BridgeRequestCodec.Sender.END_EVENT,
                BridgeRequestCodec.SenderReady.class).requestCounter());
    }

    @Test
    void sentSenderReadyIsRetriedByteForByteUntilItsAckArrives() throws Exception {
        BridgeRequestCodec.ControlHello hello = lastRequest(BridgeRequestCodec.ControlHello.class);
        String stream = streamId(25);
        byte[] helloAck = signedEnvelope(trustedKey, stream, 1, NOW,
                BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.HelloAck(
                        BridgeRequestCodec.Sender.END_EVENT, SESSION, 0, hello.helloId())));

        assertTrue(protocol.onEnvelope(helloAck, PLAYER, NOW));
        transport.drainClientQueue();
        byte[] originalReady = transport.sentRequests.get(transport.sentRequests.size() - 1).clone();
        int requestCountBeforeRetry = transport.sentRequests.size();
        assertTrue(BridgeRequestCodec.decode(originalReady) instanceof BridgeRequestCodec.SenderReady);

        protocol.tick(NOW + 1_000L);

        assertEquals(requestCountBeforeRetry + 1, transport.sentRequests.size(),
                "an unacknowledged SenderReady must be sent again before the signed hello acknowledgement expires");
        assertArrayEquals(originalReady, transport.sentRequests.get(requestCountBeforeRetry),
                "a server rate-limit drop must be retried with the same request counter and exact bytes");
        assertEquals(1L, lastRequestFor(BridgeRequestCodec.Sender.END_EVENT,
                BridgeRequestCodec.SenderReady.class).requestCounter());
    }

    @Test
    void unavailableV4TransportDoesNotConsumeAStreamReopenCounter() throws Exception {
        String stream = establishReady(BridgeRequestCodec.Sender.END_EVENT, 22);
        long counterBefore = state.lastRequestCounter(ClientBridgeConnectionState.Sender.END_EVENT);
        transport.channelAvailable = false;

        assertFalse(protocol.requestReopen(BridgeRequestCodec.Sender.END_EVENT));
        assertEquals(counterBefore, state.lastRequestCounter(ClientBridgeConnectionState.Sender.END_EVENT));
        assertEquals(stream, state.activeStream(ClientBridgeConnectionState.Sender.END_EVENT).orElseThrow());

        transport.channelAvailable = true;
        assertTrue(protocol.requestReopen(BridgeRequestCodec.Sender.END_EVENT));
        assertEquals(counterBefore + 1,
                lastRequestFor(BridgeRequestCodec.Sender.END_EVENT,
                        BridgeRequestCodec.StreamReopenRequest.class).requestCounter());
    }

    @Test
    void failedTargetedReopenSendRetriesTheSameRequestAfterRetiringTheOldStream() throws Exception {
        String retiredStream = establishReady(BridgeRequestCodec.Sender.END_EVENT, 32);
        long previousCounter = state.lastRequestCounter(ClientBridgeConnectionState.Sender.END_EVENT);
        transport.failNextSend = true;

        assertFalse(protocol.requestReopen(BridgeRequestCodec.Sender.END_EVENT));
        assertTrue(state.activeStream(ClientBridgeConnectionState.Sender.END_EVENT).isEmpty());
        assertEquals(previousCounter + 1, state.lastRequestCounter(ClientBridgeConnectionState.Sender.END_EVENT),
                "the serialized request counter stays reserved while its exact request is pending retry");
        byte[] firstAttempt = transport.attemptedRequests.getLast().clone();
        BridgeRequestCodec.StreamReopenRequest reopen =
                (BridgeRequestCodec.StreamReopenRequest) BridgeRequestCodec.decode(firstAttempt);
        assertEquals(Optional.of(retiredStream), reopen.expectedStreamId());

        protocol.tick(NOW + 3_000L);

        assertArrayEquals(firstAttempt, transport.sentRequests.getLast(),
                "a failed send must preserve the exact request ID, counter, and retired-stream fence");
        assertEquals(previousCounter + 1,
                lastRequestFor(BridgeRequestCodec.Sender.END_EVENT,
                        BridgeRequestCodec.StreamReopenRequest.class).requestCounter());
    }

    @Test
    void unansweredReopenKeepsItsExactIdentityAfterItsSignedResponseWouldExpire() throws Exception {
        String retiredStream = establishReady(BridgeRequestCodec.Sender.END_EVENT, 35);
        assertTrue(protocol.requestReopen(BridgeRequestCodec.Sender.END_EVENT));
        byte[] firstRequestBytes = transport.sentRequests.getLast().clone();
        BridgeRequestCodec.StreamReopenRequest first = lastRequestFor(BridgeRequestCodec.Sender.END_EVENT,
                BridgeRequestCodec.StreamReopenRequest.class);

        transport.nowMillis = NOW + 31_001L;
        protocol.tick(NOW + 31_001L);

        BridgeRequestCodec.StreamReopenRequest retry = lastRequestFor(BridgeRequestCodec.Sender.END_EVENT,
                BridgeRequestCodec.StreamReopenRequest.class);
        assertTrue(MessageDigest.isEqual(first.requestId(), retry.requestId()),
                "a retry must retain the request ID until the server can refresh its signed response");
        assertEquals(first.requestCounter(), retry.requestCounter(),
                "a retry must not skip the server's next required counted request");
        assertEquals(Optional.of(retiredStream), retry.expectedStreamId(),
                "a retry must preserve the original retired-stream fence");
        List<byte[]> endEventReopenAttempts = transport.attemptedRequests.stream()
                .filter(bytes -> BridgeRequestCodec.decode(bytes) instanceof BridgeRequestCodec.StreamReopenRequest request
                        && request.sender() == BridgeRequestCodec.Sender.END_EVENT)
                .toList();
        assertArrayEquals(firstRequestBytes, endEventReopenAttempts.getLast(),
                "the client must retransmit byte-for-byte rather than create a counter gap");

        String refreshedResponseStream = streamId(37);
        byte[] refreshedResponse = signedEnvelope(trustedKey, refreshedResponseStream, 1, NOW + 31_002L,
                BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.HelloAck(
                        BridgeRequestCodec.Sender.END_EVENT, SESSION, 1, first.requestId())));
        transport.nowMillis = NOW + 31_002L;
        assertTrue(protocol.onEnvelope(refreshedResponse, PLAYER, NOW + 31_002L));
        transport.drainClientQueue();
        assertEquals(refreshedResponseStream,
                state.activeStream(ClientBridgeConnectionState.Sender.END_EVENT).orElseThrow());
    }

    @Test
    void senderIsNotMarkedReadyUntilItsAuthenticatedConfirmCanBeSent() throws Exception {
        BridgeRequestCodec.ControlHello hello = lastRequest(BridgeRequestCodec.ControlHello.class);
        String stream = streamId(23);
        byte[] helloAck = signedEnvelope(trustedKey, stream, 1, NOW,
                BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.HelloAck(
                        BridgeRequestCodec.Sender.END_EVENT, SESSION, 0, hello.helloId())));
        assertTrue(protocol.onEnvelope(helloAck, PLAYER, NOW));
        transport.drainClientQueue();
        BridgeRequestCodec.SenderReady ready = lastRequestFor(BridgeRequestCodec.Sender.END_EVENT,
                BridgeRequestCodec.SenderReady.class);
        byte[] readyAck = signedEnvelope(trustedKey, stream, 2, NOW + 1,
                BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.SenderReadyAck(
                        BridgeRequestCodec.Sender.END_EVENT, SESSION, ready.requestId(), ready.requestCounter())));
        transport.channelAvailable = false;

        assertTrue(protocol.onEnvelope(readyAck, PLAYER, NOW + 1));
        transport.drainClientQueue();
        assertFalse(state.isReady(ClientBridgeConnectionState.Sender.END_EVENT));

        transport.channelAvailable = true;
        assertTrue(protocol.onEnvelope(readyAck, PLAYER, NOW + 1));
        transport.drainClientQueue();
        BridgeRequestCodec.SenderReadyConfirm confirm = lastRequestFor(BridgeRequestCodec.Sender.END_EVENT,
                BridgeRequestCodec.SenderReadyConfirm.class);
        byte[] receipt = signedEnvelope(trustedKey, stream, 3, NOW + 2,
                BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.SenderReadyConfirmAck(
                        BridgeRequestCodec.Sender.END_EVENT, SESSION, ready.requestId(), ready.requestCounter(),
                        MessageDigest.getInstance("SHA-256").digest(BridgeRequestCodec.encode(confirm)))));
        assertTrue(protocol.onEnvelope(receipt, PLAYER, NOW + 2));
        transport.drainClientQueue();
        assertTrue(state.isReady(ClientBridgeConnectionState.Sender.END_EVENT));
        assertTrue(protocol.isReady(BridgeRequestCodec.Sender.END_EVENT));
    }

    @Test
    void expiredQueuedPacketFromPriorConnectionCannotRetireReusedStreamId() throws Exception {
        String oldStream = establishReady(BridgeRequestCodec.Sender.NARCOTICS, 17);
        byte[] oldBody = narcoticsBody(SESSION, NOW + 2);
        assertTrue(protocol.onEnvelope(signedEnvelope(trustedKey, oldStream, 4, NOW + 2, oldBody), PLAYER, NOW));
        Runnable staleTask = transport.removeQueuedClientTask(0);

        protocol.close();
        protocol.openConnection(PLAYER);
        protocol.acceptVerifiedSession(SESSION);
        transport.nowMillis = NOW;
        String currentStream = establishReady(BridgeRequestCodec.Sender.NARCOTICS, 17);
        assertEquals(oldStream, currentStream);

        transport.nowMillis = NOW + 10_000;
        staleTask.run();

        assertEquals(currentStream, state.activeStream(ClientBridgeConnectionState.Sender.NARCOTICS).orElseThrow());
        assertTrue(protocol.isReady(BridgeRequestCodec.Sender.NARCOTICS));
        assertFalse(transport.sentRequests.stream()
                .map(BridgeRequestCodec::decode)
                .anyMatch(request -> request instanceof BridgeRequestCodec.StreamReopenRequest reopen
                        && reopen.sender() == BridgeRequestCodec.Sender.NARCOTICS));
    }

    @Test
    void authenticatedCounterGapRetiresOnlyItsOwnSender() throws Exception {
        String endStream = establishReady(BridgeRequestCodec.Sender.END_EVENT, 13);
        String narcoticsStream = establishReady(BridgeRequestCodec.Sender.NARCOTICS, 14);
        byte[] body = endEventContextBind(1, NOW + 2);
        byte[] skipped = signedEnvelope(trustedKey, endStream, 5, NOW + 2, body);

        assertTrue(protocol.onEnvelope(skipped, PLAYER, NOW + 2));
        transport.drainClientQueue();

        assertTrue(state.activeStream(ClientBridgeConnectionState.Sender.END_EVENT).isEmpty());
        assertEquals(narcoticsStream,
                state.activeStream(ClientBridgeConnectionState.Sender.NARCOTICS).orElseThrow());
        assertTrue(state.isReady(ClientBridgeConnectionState.Sender.NARCOTICS));
    }

    @Test
    void negotiatedHeartbeatWatchdogStartsAtReceiptAndReopensOnlySilentSenderAt20Seconds() throws Exception {
        PendingReadyConfirm pending = beginPendingReadyConfirm(92,
                BridgeControlBodyCodec.SENDER_HEARTBEAT_FEATURE);
        long receiptNanos = 500_000_000_000L;
        transport.nowNanos = receiptNanos;
        byte[] receipt = signedEnvelope(trustedKey, pending.stream(), 3, NOW + 2,
                BridgeControlBodyCodec.encode(confirmReceipt(pending)));
        assertTrue(protocol.onEnvelope(receipt, PLAYER, NOW + 2));
        transport.drainClientQueue();
        assertTrue(protocol.isReady(BridgeRequestCodec.Sender.END_EVENT));

        transport.nowNanos = receiptNanos + 2_000_000_000L;
        String narcoticsStream = establishReady(BridgeRequestCodec.Sender.NARCOTICS, 93,
                BridgeControlBodyCodec.SENDER_HEARTBEAT_FEATURE);

        transport.nowNanos = receiptNanos + 19_999_999_999L;
        protocol.tick(NOW + 19_999L);
        assertTrue(protocol.isReady(BridgeRequestCodec.Sender.END_EVENT), "19,999 ms is before the deadline");
        assertTrue(protocol.isReady(BridgeRequestCodec.Sender.NARCOTICS));

        transport.nowNanos = receiptNanos + 20_000_000_000L;
        protocol.tick(NOW + 20_000L);

        assertFalse(protocol.isReady(BridgeRequestCodec.Sender.END_EVENT));
        assertTrue(state.activeStream(ClientBridgeConnectionState.Sender.END_EVENT).isEmpty());
        BridgeRequestCodec.StreamReopenRequest reopen = lastRequestFor(BridgeRequestCodec.Sender.END_EVENT,
                BridgeRequestCodec.StreamReopenRequest.class);
        assertEquals(Optional.of(pending.stream()), reopen.expectedStreamId());
        assertEquals(narcoticsStream,
                state.activeStream(ClientBridgeConnectionState.Sender.NARCOTICS).orElseThrow());
        assertTrue(protocol.isReady(BridgeRequestCodec.Sender.NARCOTICS),
                "a healthy sibling stream remains ready when End Event times out");
        assertEquals(List.of(BridgeRequestCodec.Sender.END_EVENT), clearedSenders);
    }

    @Test
    void heartbeatTimeoutRetainsRetiredStreamWhenTransportIsTemporarilyUnavailable() throws Exception {
        String stream = establishReady(BridgeRequestCodec.Sender.END_EVENT, 106,
                BridgeControlBodyCodec.SENDER_HEARTBEAT_FEATURE);
        long counterBeforeTimeout = state.lastRequestCounter(ClientBridgeConnectionState.Sender.END_EVENT);
        transport.nowNanos = 20_000_000_000L;
        transport.channelAvailable = false;

        protocol.tick(NOW + 20_000L);

        assertFalse(protocol.isReady(BridgeRequestCodec.Sender.END_EVENT));
        assertTrue(state.activeStream(ClientBridgeConnectionState.Sender.END_EVENT).isEmpty());
        assertEquals(counterBeforeTimeout,
                state.lastRequestCounter(ClientBridgeConnectionState.Sender.END_EVENT),
                "unavailable transport must not consume a reopen counter");
        assertTrue(transport.sentRequests.stream().map(BridgeRequestCodec::decode)
                .noneMatch(request -> request instanceof BridgeRequestCodec.StreamReopenRequest reopen
                        && reopen.sender() == BridgeRequestCodec.Sender.END_EVENT));

        protocol.tick(NOW + 31_001L);
        assertEquals(counterBeforeTimeout,
                state.lastRequestCounter(ClientBridgeConnectionState.Sender.END_EVENT),
                "HelloAck recovery must not replace the deferred target while the transport is unavailable");
        assertTrue(transport.sentRequests.stream().map(BridgeRequestCodec::decode)
                .noneMatch(request -> request instanceof BridgeRequestCodec.StreamReopenRequest reopen
                        && reopen.sender() == BridgeRequestCodec.Sender.END_EVENT));

        transport.channelAvailable = true;
        protocol.tick(NOW + 31_002L);

        BridgeRequestCodec.StreamReopenRequest reopen = lastRequestFor(BridgeRequestCodec.Sender.END_EVENT,
                BridgeRequestCodec.StreamReopenRequest.class);
        assertEquals(Optional.of(stream), reopen.expectedStreamId(),
                "recovery must retain the retired stream fence across a transient transport outage");
        assertEquals(counterBeforeTimeout + 1, reopen.requestCounter());
    }

    @Test
    void validHeartbeatRefreshesOnlyItsSenderAndDuplicateDoesNotExtendTheDeadline() throws Exception {
        String endStream = establishReady(BridgeRequestCodec.Sender.END_EVENT, 94,
                BridgeControlBodyCodec.SENDER_HEARTBEAT_FEATURE);
        String narcoticsStream = establishReady(BridgeRequestCodec.Sender.NARCOTICS, 95,
                BridgeControlBodyCodec.SENDER_HEARTBEAT_FEATURE);

        transport.nowNanos = 19_000_000_000L;
        byte[] heartbeat = signedEnvelope(trustedKey, endStream, 4, NOW + 3,
                BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.SenderHeartbeat(
                        BridgeRequestCodec.Sender.END_EVENT, SESSION)));
        assertTrue(protocol.onEnvelope(heartbeat, PLAYER, NOW + 3));
        transport.drainClientQueue();

        transport.nowNanos = 20_000_000_000L;
        assertTrue(protocol.onEnvelope(heartbeat, PLAYER, NOW + 3), "validly signed duplicate is admitted for replay checks");
        transport.drainClientQueue();
        protocol.tick(NOW + 20_000L);
        assertTrue(protocol.isReady(BridgeRequestCodec.Sender.END_EVENT), "the accepted heartbeat refreshed End Event");
        assertFalse(protocol.isReady(BridgeRequestCodec.Sender.NARCOTICS), "Narcotics has its own earlier deadline");
        assertEquals(narcoticsStream,
                lastRequestFor(BridgeRequestCodec.Sender.NARCOTICS,
                        BridgeRequestCodec.StreamReopenRequest.class).expectedStreamId().orElseThrow());

        transport.nowNanos = 38_999_999_999L;
        protocol.tick(NOW + 38_999L);
        assertTrue(protocol.isReady(BridgeRequestCodec.Sender.END_EVENT), "duplicate counter did not reset liveness");
        transport.nowNanos = 39_000_000_000L;
        protocol.tick(NOW + 39_000L);
        assertFalse(protocol.isReady(BridgeRequestCodec.Sender.END_EVENT));
        assertEquals(endStream,
                lastRequestFor(BridgeRequestCodec.Sender.END_EVENT,
                        BridgeRequestCodec.StreamReopenRequest.class).expectedStreamId().orElseThrow());
    }

    @Test
    void validApplicationEnvelopeRefreshesNegotiatedHeartbeatLiveness() throws Exception {
        String stream = establishReady(BridgeRequestCodec.Sender.END_EVENT, 96,
                BridgeControlBodyCodec.SENDER_HEARTBEAT_FEATURE);
        transport.nowNanos = 15_000_000_000L;
        byte[] application = endEventContextBind(1, NOW + 3);
        assertTrue(protocol.onEnvelope(signedEnvelope(trustedKey, stream, 4, NOW + 3, application),
                PLAYER, NOW + 3));
        transport.drainClientQueue();

        transport.nowNanos = 34_999_999_999L;
        protocol.tick(NOW + 34_999L);
        assertTrue(protocol.isReady(BridgeRequestCodec.Sender.END_EVENT));
        transport.nowNanos = 35_000_000_000L;
        protocol.tick(NOW + 35_000L);
        assertFalse(protocol.isReady(BridgeRequestCodec.Sender.END_EVENT));
        assertEquals(stream, lastRequestFor(BridgeRequestCodec.Sender.END_EVENT,
                BridgeRequestCodec.StreamReopenRequest.class).expectedStreamId().orElseThrow());
    }

    @Test
    void legacyStreamHasNoHeartbeatTimeoutAndUnauthenticatedPacketsDoNotRefreshIt() throws Exception {
        String legacyStream = establishReady(BridgeRequestCodec.Sender.END_EVENT, 97);
        transport.nowNanos = 120_000_000_000L;
        protocol.tick(NOW + 120_000L);
        assertTrue(protocol.isReady(BridgeRequestCodec.Sender.END_EVENT),
                "a legacy HelloAck is exempt from the heartbeat watchdog");
        assertEquals(legacyStream,
                state.activeStream(ClientBridgeConnectionState.Sender.END_EVENT).orElseThrow());
        assertTrue(transport.sentRequests.stream().map(BridgeRequestCodec::decode)
                .noneMatch(request -> request instanceof BridgeRequestCodec.StreamReopenRequest reopen
                        && reopen.sender() == BridgeRequestCodec.Sender.END_EVENT));
    }

    @Test
    void invalidHeartbeatEnvelopesDoNotExtendAuthenticatedActivityDeadline() throws Exception {
        String stream = establishReady(BridgeRequestCodec.Sender.END_EVENT, 102,
                BridgeControlBodyCodec.SENDER_HEARTBEAT_FEATURE);
        byte[] heartbeatBody = BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.SenderHeartbeat(
                BridgeRequestCodec.Sender.END_EVENT, SESSION));
        transport.nowNanos = 10_000_000_000L;
        byte[] accepted = signedEnvelope(trustedKey, stream, 4, NOW + 3, heartbeatBody);
        assertTrue(protocol.onEnvelope(accepted, PLAYER, NOW + 3));
        transport.drainClientQueue();

        transport.nowNanos = 29_000_000_000L;
        assertFalse(protocol.onEnvelope(signedEnvelope(otherKey, stream, 5, NOW + 4, heartbeatBody),
                PLAYER, NOW + 4), "an untrusted signature cannot refresh liveness");

        byte[] validCounterFive = signedEnvelope(trustedKey, stream, 5, NOW + 4, heartbeatBody);
        BridgeEnvelope decoded = BridgeEnvelopeCodec.decode(validCounterFive);
        byte[] modifiedBody = decoded.bodyBytes();
        modifiedBody[2] = (byte) BridgeRequestCodec.Sender.NARCOTICS.id();
        byte[] modified = BridgeEnvelopeCodec.encode(new BridgeEnvelope(decoded.metadata(), modifiedBody,
                decoded.signature()));
        assertFalse(protocol.onEnvelope(modified, PLAYER, NOW + 4), "a modified exact body cannot refresh liveness");

        byte[] expired = signedEnvelope(trustedKey, stream, 5, NOW, heartbeatBody);
        assertFalse(protocol.onEnvelope(expired, PLAYER, NOW + 6_000L), "an expired envelope cannot refresh liveness");
        byte[] wrongNonceBytes = Base64.getUrlDecoder().decode(state.currentConnection().orElseThrow().nonce());
        wrongNonceBytes[0] ^= 1;
        String wrongCanonicalNonce = Base64.getUrlEncoder().withoutPadding().encodeToString(wrongNonceBytes);
        byte[] wrongNonce = signedEnvelope(trustedKey, stream, 5, NOW + 4, heartbeatBody, wrongCanonicalNonce);
        assertFalse(protocol.onEnvelope(wrongNonce, PLAYER, NOW + 4), "a wrong connection nonce cannot refresh liveness");
        byte[] wrongStream = signedEnvelope(trustedKey, streamId(103), 5, NOW + 4, heartbeatBody);
        assertTrue(protocol.onEnvelope(wrongStream, PLAYER, NOW + 4), "a packet on an unrelated stream is admitted for routing");
        transport.drainClientQueue();

        transport.nowNanos = 29_999_999_999L;
        protocol.tick(NOW + 29_999L);
        assertTrue(protocol.isReady(BridgeRequestCodec.Sender.END_EVENT));
        transport.nowNanos = 30_000_000_000L;
        protocol.tick(NOW + 30_000L);
        assertFalse(protocol.isReady(BridgeRequestCodec.Sender.END_EVENT));
        assertEquals(stream, lastRequestFor(BridgeRequestCodec.Sender.END_EVENT,
                BridgeRequestCodec.StreamReopenRequest.class).expectedStreamId().orElseThrow());
    }

    @Test
    void authenticatedHeartbeatWrongSessionRetiresOnlyItsBoundStream() throws Exception {
        String endStream = establishReady(BridgeRequestCodec.Sender.END_EVENT, 98,
                BridgeControlBodyCodec.SENDER_HEARTBEAT_FEATURE);
        String narcoticsStream = establishReady(BridgeRequestCodec.Sender.NARCOTICS, 99,
                BridgeControlBodyCodec.SENDER_HEARTBEAT_FEATURE);
        byte[] wrongSession = signedEnvelope(trustedKey, endStream, 4, NOW + 3,
                BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.SenderHeartbeat(
                        BridgeRequestCodec.Sender.END_EVENT,
                        UUID.fromString("00000000-0000-0000-0000-000000000099"))));

        assertTrue(protocol.onEnvelope(wrongSession, PLAYER, NOW + 3));
        transport.drainClientQueue();

        assertFalse(protocol.isReady(BridgeRequestCodec.Sender.END_EVENT));
        assertEquals(endStream, lastRequestFor(BridgeRequestCodec.Sender.END_EVENT,
                BridgeRequestCodec.StreamReopenRequest.class).expectedStreamId().orElseThrow());
        assertEquals(narcoticsStream,
                state.activeStream(ClientBridgeConnectionState.Sender.NARCOTICS).orElseThrow());
        assertTrue(protocol.isReady(BridgeRequestCodec.Sender.NARCOTICS));
    }

    @Test
    void authenticatedHeartbeatWrongSenderRetiresEnvelopeBoundStream() throws Exception {
        String endStream = establishReady(BridgeRequestCodec.Sender.END_EVENT, 104,
                BridgeControlBodyCodec.SENDER_HEARTBEAT_FEATURE);
        String narcoticsStream = establishReady(BridgeRequestCodec.Sender.NARCOTICS, 105,
                BridgeControlBodyCodec.SENDER_HEARTBEAT_FEATURE);
        byte[] wrongSender = signedEnvelope(trustedKey, endStream, 4, NOW + 3,
                BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.SenderHeartbeat(
                        BridgeRequestCodec.Sender.NARCOTICS, SESSION)));

        assertTrue(protocol.onEnvelope(wrongSender, PLAYER, NOW + 3));
        transport.drainClientQueue();

        assertEquals(endStream, lastRequestFor(BridgeRequestCodec.Sender.END_EVENT,
                BridgeRequestCodec.StreamReopenRequest.class).expectedStreamId().orElseThrow());
        assertEquals(narcoticsStream,
                state.activeStream(ClientBridgeConnectionState.Sender.NARCOTICS).orElseThrow());
        assertTrue(protocol.isReady(BridgeRequestCodec.Sender.NARCOTICS));
    }

    @Test
    void authenticatedHeartbeatCounterGapRetiresOnlyItsSender() throws Exception {
        String endStream = establishReady(BridgeRequestCodec.Sender.END_EVENT, 100,
                BridgeControlBodyCodec.SENDER_HEARTBEAT_FEATURE);
        String narcoticsStream = establishReady(BridgeRequestCodec.Sender.NARCOTICS, 101,
                BridgeControlBodyCodec.SENDER_HEARTBEAT_FEATURE);
        byte[] gap = signedEnvelope(trustedKey, endStream, 5, NOW + 3,
                BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.SenderHeartbeat(
                        BridgeRequestCodec.Sender.END_EVENT, SESSION)));

        assertTrue(protocol.onEnvelope(gap, PLAYER, NOW + 3));
        transport.drainClientQueue();

        assertEquals(endStream, lastRequestFor(BridgeRequestCodec.Sender.END_EVENT,
                BridgeRequestCodec.StreamReopenRequest.class).expectedStreamId().orElseThrow());
        assertEquals(narcoticsStream,
                state.activeStream(ClientBridgeConnectionState.Sender.NARCOTICS).orElseThrow());
        assertTrue(protocol.isReady(BridgeRequestCodec.Sender.NARCOTICS));
    }

    private PendingReadyConfirm beginPendingReadyConfirm() throws Exception {
        return beginPendingReadyConfirm(91, 0);
    }

    private PendingReadyConfirm beginPendingReadyConfirm(int seed, int featureFlags) throws Exception {
        BridgeRequestCodec.ControlHello hello = lastRequest(BridgeRequestCodec.ControlHello.class);
        String stream = streamId(seed);
        byte[] helloAck = signedEnvelope(trustedKey, stream, 1, NOW,
                BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.HelloAck(
                        BridgeRequestCodec.Sender.END_EVENT, SESSION, 0, hello.helloId(), featureFlags)));
        assertTrue(protocol.onEnvelope(helloAck, PLAYER, NOW));
        transport.drainClientQueue();

        BridgeRequestCodec.SenderReady ready = lastRequestFor(BridgeRequestCodec.Sender.END_EVENT,
                BridgeRequestCodec.SenderReady.class);
        byte[] readyAck = signedEnvelope(trustedKey, stream, 2, NOW + 1,
                BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.SenderReadyAck(
                        BridgeRequestCodec.Sender.END_EVENT, SESSION, ready.requestId(), ready.requestCounter())));
        assertTrue(protocol.onEnvelope(readyAck, PLAYER, NOW + 1));
        transport.drainClientQueue();

        BridgeRequestCodec.SenderReadyConfirm confirm = lastRequestFor(BridgeRequestCodec.Sender.END_EVENT,
                BridgeRequestCodec.SenderReadyConfirm.class);
        assertFalse(protocol.isReady(BridgeRequestCodec.Sender.END_EVENT));
        return new PendingReadyConfirm(stream, ready, confirm);
    }

    private static BridgeControlBodyCodec.SenderReadyConfirmAck confirmReceipt(PendingReadyConfirm pending)
            throws Exception {
        return new BridgeControlBodyCodec.SenderReadyConfirmAck(
                BridgeRequestCodec.Sender.END_EVENT, SESSION, pending.ready().requestId(),
                pending.ready().requestCounter(),
                MessageDigest.getInstance("SHA-256").digest(BridgeRequestCodec.encode(pending.confirm())));
    }

    private void assertConfirmReceiptRejected(PendingReadyConfirm pending, byte[] body, long counter)
            throws Exception {
        byte[] receipt = signedEnvelope(trustedKey, pending.stream(), counter, NOW + 2, body);
        assertTrue(protocol.onEnvelope(receipt, PLAYER, NOW + 2),
                "the outer signature and client binding are valid; the receipt body must still be rejected");
        transport.drainClientQueue();
        assertFalse(protocol.isReady(BridgeRequestCodec.Sender.END_EVENT));
        assertFalse(state.isReady(ClientBridgeConnectionState.Sender.END_EVENT));
    }

    private String establishReady(BridgeRequestCodec.Sender sender, int streamSeed) throws Exception {
        return establishReady(sender, streamSeed, 0);
    }

    private String establishReady(BridgeRequestCodec.Sender sender, int streamSeed, int featureFlags) throws Exception {
        BridgeRequestCodec.ControlHello hello = lastRequest(BridgeRequestCodec.ControlHello.class);
        String stream = streamId(streamSeed);
        byte[] helloEnvelope = signedEnvelope(trustedKey, stream, 1, NOW,
                BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.HelloAck(
                        sender, SESSION, 0, hello.helloId(), featureFlags)));
        assertTrue(protocol.onEnvelope(helloEnvelope, PLAYER, NOW));
        transport.drainClientQueue();

        BridgeRequestCodec.SenderReady ready = lastRequestFor(sender, BridgeRequestCodec.SenderReady.class);
        byte[] readyAckEnvelope = signedEnvelope(trustedKey, stream, 2, NOW + 1,
                BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.SenderReadyAck(
                        sender, SESSION, ready.requestId(), ready.requestCounter())));
        assertTrue(protocol.onEnvelope(readyAckEnvelope, PLAYER, NOW + 1));
        transport.drainClientQueue();
        BridgeRequestCodec.SenderReadyConfirm confirm = lastRequestFor(sender,
                BridgeRequestCodec.SenderReadyConfirm.class);
        byte[] confirmAckEnvelope = signedEnvelope(trustedKey, stream, 3, NOW + 2,
                BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.SenderReadyConfirmAck(
                        sender, SESSION, ready.requestId(), ready.requestCounter(),
                        MessageDigest.getInstance("SHA-256").digest(BridgeRequestCodec.encode(confirm)))));
        assertTrue(protocol.onEnvelope(confirmAckEnvelope, PLAYER, NOW + 2));
        transport.drainClientQueue();
        assertTrue(state.isReady(toStateSender(sender)), "active="
                + state.activeStream(toStateSender(sender)).orElse("none")
                + ", readyRequest=" + lastRequestFor(sender, BridgeRequestCodec.SenderReady.class)
                + ", sent=" + transport.sentRequests.size());
        return stream;
    }

    private <T extends BridgeRequestCodec.Request> T lastRequest(Class<T> type) {
        for (int index = transport.sentRequests.size() - 1; index >= 0; index--) {
            BridgeRequestCodec.Request request = BridgeRequestCodec.decode(transport.sentRequests.get(index));
            if (type.isInstance(request)) return type.cast(request);
        }
        throw new AssertionError("No request of type " + type.getSimpleName());
    }

    private <T extends BridgeRequestCodec.Request> T firstRequest(Class<T> type) {
        for (byte[] bytes : transport.sentRequests) {
            BridgeRequestCodec.Request request = BridgeRequestCodec.decode(bytes);
            if (type.isInstance(request)) return type.cast(request);
        }
        throw new AssertionError("No request of type " + type.getSimpleName());
    }

    private <T extends BridgeRequestCodec.Request> T lastRequestFor(BridgeRequestCodec.Sender sender, Class<T> type) {
        for (int index = transport.sentRequests.size() - 1; index >= 0; index--) {
            BridgeRequestCodec.Request request = BridgeRequestCodec.decode(transport.sentRequests.get(index));
            if (type.isInstance(request) && requestSender(request) == sender) return type.cast(request);
        }
        throw new AssertionError("No request of type " + type.getSimpleName() + " for " + sender);
    }

    private static BridgeRequestCodec.Sender requestSender(BridgeRequestCodec.Request request) {
        if (request instanceof BridgeRequestCodec.SenderReady value) return value.sender();
        if (request instanceof BridgeRequestCodec.SenderReadyConfirm value) return value.sender();
        if (request instanceof BridgeRequestCodec.StreamReopenRequest value) return value.sender();
        if (request instanceof BridgeRequestCodec.PrisonerAbilityRequest) return BridgeRequestCodec.Sender.END_EVENT;
        return null;
    }

    private byte[] signedEnvelope(KeyPair signer, String stream, long counter, long issuedAt, byte[] body)
            throws Exception {
        return signedEnvelope(signer, stream, counter, issuedAt, body,
                state.currentConnection().orElseThrow().nonce());
    }

    private static byte[] signedEnvelope(KeyPair signer, String stream, long counter, long issuedAt,
            byte[] body, String nonce) throws Exception {
        BridgeEnvelope.Metadata metadata = new BridgeEnvelope.Metadata(
                BridgeSigningPolicy.PROTOCOL_VERSION, BridgeSigningPolicy.PINNED_KEY_ID, nonce,
                PLAYER, stream, counter, issuedAt, issuedAt + 5_000);
        return BridgeEnvelopeCodec.encode(new BridgeEnvelope(metadata, body,
                BridgeSigningPolicy.signV4(signer.getPrivate(), metadata, body)));
    }

    private static byte[] narcoticsBody(UUID session, long timestamp) {
        return narcoticsBody(session, timestamp, 1L);
    }

    private static byte[] narcoticsBody(UUID session, long timestamp, long commandSeq) {
        return NarcoticsBridgeBodyCodec.encode(new NarcoticsBridgeBodyCodec.Body(
                NarcoticsBridgeBodyCodec.Type.VISUAL_START, commandSeq, timestamp, session,
                "INVERT", "", 10_000, 0.5F, 0, 0, "CLIENT_MOD", "PRESERVE", "SYSTEM",
                "", "", EVENT, new UUID(0, 0), 0));
    }

    private static byte[] endEventContextBind(long generation, long timestamp) {
        return endEventContextBind(EVENT, generation, timestamp, "the_end|0,0,0|10,10,10");
    }

    private static byte[] endEventContextBind(UUID eventUuid, long generation, long timestamp, String bounds) {
        return EndEventBridgeBodyCodec.encode(new EndEventBridgeBodyCodec.Body(
                EndEventBridgeBodyCodec.Type.CONTEXT_BIND, generation, timestamp, eventUuid,
                "", "", "", 0, 0.0F, 0, 0,
                bounds, "", "", "", ""));
    }

    private static byte[] endEventContextUnbind(long generation, long timestamp) {
        return endEventContextUnbind(EVENT, generation, timestamp);
    }

    private static byte[] endEventContextUnbind(UUID eventUuid, long generation, long timestamp) {
        return EndEventBridgeBodyCodec.encode(new EndEventBridgeBodyCodec.Body(
                EndEventBridgeBodyCodec.Type.CONTEXT_UNBIND, generation, timestamp, eventUuid,
                "", "", "", 0, 0.0F, 0, 0, "", "", "", "", ""));
    }

    private static String streamId(long seed) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(16);
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeLong(seed);
        output.writeLong(seed * 17);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
    }

    private static ClientBridgeConnectionState.Sender toStateSender(BridgeRequestCodec.Sender sender) {
        return sender == BridgeRequestCodec.Sender.END_EVENT
                ? ClientBridgeConnectionState.Sender.END_EVENT : ClientBridgeConnectionState.Sender.NARCOTICS;
    }

    private static final class FakeTransport implements ClientBridgeProtocolV4.Transport {
        private final List<byte[]> sentRequests = new ArrayList<>();
        private final List<byte[]> attemptedRequests = new ArrayList<>();
        private final List<Runnable> clientQueue = new ArrayList<>();
        private UUID currentPlayer = PLAYER;
        private long nowMillis = NOW;
        private long nowNanos;
        private boolean channelAvailable = true;
        private boolean failNextSend;

        @Override
        public boolean canSendRequest() {
            return channelAvailable;
        }

        @Override
        public boolean sendRequest(byte[] requestBytes) {
            if (!canSendRequest()) {
                return false;
            }
            attemptedRequests.add(requestBytes.clone());
            if (failNextSend) {
                failNextSend = false;
                return false;
            }
            sentRequests.add(requestBytes.clone());
            return true;
        }

        @Override
        public void executeOnClient(Runnable task) {
            clientQueue.add(task);
        }

        @Override
        public UUID currentPlayerUuid() {
            return currentPlayer;
        }

        @Override
        public long currentTimeMillis() {
            return nowMillis;
        }

        @Override
        public long nanoTime() {
            return nowNanos;
        }

        private void drainClientQueue() {
            while (!clientQueue.isEmpty()) {
                clientQueue.remove(0).run();
            }
        }

        private Runnable removeQueuedClientTask(int index) {
            return clientQueue.remove(index);
        }
    }

    private record PendingReadyConfirm(String stream, BridgeRequestCodec.SenderReady ready,
            BridgeRequestCodec.SenderReadyConfirm confirm) {
    }
}
