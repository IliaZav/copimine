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
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

final class NarcoticsBridgeV4SenderTest {
    private static final String NONCE = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Test
    void trustedHelloAndReadyReceiptGateVisualSends() throws Exception {
        Fixture fixture = new Fixture(true);
        assertFalse(fixture.sender.isReady(fixture.player));
        assertFalse(fixture.sender.sendVisualStart(fixture.player, startBody(fixture.session.sessionUuid()), true));
        assertTrue(fixture.outbound.isEmpty());

        byte[] helloId = id(1);
        fixture.request(new BridgeRequestCodec.ControlHello(NONCE, fixture.playerId,
                fixture.session.sessionUuid(), helloId));
        assertEquals(1, fixture.outbound.size());
        byte[] helloEnvelopeBytes = fixture.outbound.get(0);
        BridgeEnvelope helloEnvelope = fixture.verify(helloEnvelopeBytes, 1L);
        BridgeControlBodyCodec.HelloAck helloAck = assertInstanceOf(BridgeControlBodyCodec.HelloAck.class,
                BridgeControlBodyCodec.decode(helloEnvelope.bodyBytes()));
        assertEquals(BridgeRequestCodec.Sender.NARCOTICS, helloAck.sender());
        assertEquals(fixture.session.sessionUuid(), helloAck.sessionUuid());
        assertEquals(0, helloAck.requestKind());
        assertArrayEquals(helloId, helloAck.requestId());

        byte[] streamIdBytes = Base64.getUrlDecoder().decode(helloEnvelope.metadata().streamId());
        byte[] readyId = id(2);
        fixture.request(new BridgeRequestCodec.SenderReady(NONCE, fixture.playerId,
                fixture.session.sessionUuid(), 1L, BridgeRequestCodec.Sender.NARCOTICS,
                helloEnvelope.metadata().streamId(), readyId, sha256(helloEnvelopeBytes)));
        assertEquals(2, fixture.outbound.size());
        BridgeEnvelope readyEnvelope = fixture.verify(fixture.outbound.get(1), 2L);
        BridgeControlBodyCodec.SenderReadyAck readyAck = assertInstanceOf(BridgeControlBodyCodec.SenderReadyAck.class,
                BridgeControlBodyCodec.decode(readyEnvelope.bodyBytes()));
        assertEquals(BridgeRequestCodec.Sender.NARCOTICS, readyAck.sender());
        assertEquals(1L, readyAck.readyRequestCounter());
        assertArrayEquals(readyId, readyAck.requestId());
        assertFalse(fixture.sender.isReady(fixture.player));

        byte[] confirm = BridgeRequestCodec.encode(new BridgeRequestCodec.SenderReadyConfirm(NONCE,
                fixture.playerId, fixture.session.sessionUuid(), BridgeRequestCodec.Sender.NARCOTICS,
                helloEnvelope.metadata().streamId(), readyId, 1L,
                sha256(fixture.outbound.get(1))));
        fixture.sender.onRequest(fixture.player, confirm);
        assertTrue(fixture.sender.isReady(fixture.player));
        assertEquals(3, fixture.outbound.size());
        byte[] confirmReceipt = fixture.outbound.get(2).clone();
        BridgeEnvelope receiptEnvelope = fixture.verify(confirmReceipt, 3L);
        BridgeControlBodyCodec.SenderReadyConfirmAck receipt = assertInstanceOf(
                BridgeControlBodyCodec.SenderReadyConfirmAck.class,
                BridgeControlBodyCodec.decode(receiptEnvelope.bodyBytes()));
        assertArrayEquals(sha256(confirm), receipt.confirmRequestSha256());
        fixture.sender.onRequest(fixture.player, confirm);
        assertEquals(4, fixture.outbound.size(), "an exact confirm retry replays the same signed receipt");
        assertArrayEquals(confirmReceipt, fixture.outbound.get(3));
        assertTrue(fixture.sender.sendVisualStart(fixture.player, startBody(fixture.session.sessionUuid()), true));
        assertEquals(5, fixture.outbound.size());
        BridgeEnvelope visualEnvelope = fixture.verify(fixture.outbound.get(4), 4L);
        NarcoticsBridgeBodyCodec.Body visual = NarcoticsBridgeBodyCodec.decode(visualEnvelope.bodyBytes());
        assertEquals(NarcoticsBridgeBodyCodec.Type.VISUAL_START, visual.type());
        assertEquals(0L, visual.clearThroughCounter());
        assertEquals(streamIdBytes.length, 16);
    }

    @Test
    void unavailableSignerAndChangedRegistryBindingFailClosed() throws Exception {
        Fixture unavailable = new Fixture(false);
        unavailable.request(new BridgeRequestCodec.ControlHello(NONCE, unavailable.playerId,
                unavailable.session.sessionUuid(), id(3)));
        assertTrue(unavailable.outbound.isEmpty());
        assertFalse(unavailable.sender.isReady(unavailable.player));

        Fixture trusted = new Fixture(true);
        trusted.request(new BridgeRequestCodec.ControlHello(NONCE, trusted.playerId,
                trusted.session.sessionUuid(), id(4)));
        trusted.current = Optional.empty();
        assertFalse(trusted.sender.sendVisualStart(trusted.player, startBody(trusted.session.sessionUuid()), true));
        assertEquals(1, trusted.outbound.size());
        assertFalse(trusted.sender.isReady(trusted.player));
    }

    @Test
    void wrongSessionHelloDoesNotRetireAnEstablishedReadyStream() throws Exception {
        Fixture fixture = readyFixture();
        int outboundBefore = fixture.outbound.size();

        fixture.request(new BridgeRequestCodec.ControlHello(NONCE, fixture.playerId,
                UUID.randomUUID(), id(40)));

        assertTrue(fixture.sender.isReady(fixture.player), "an untrusted session claim must not retire live readiness");
        assertEquals(outboundBefore, fixture.outbound.size());
    }

    @Test
    void wrongNonceReopenDoesNotRetireStreamOrConsumeItsCounter() throws Exception {
        Fixture fixture = readyFixture();
        String currentStream = BridgeEnvelopeCodec.decode(fixture.outbound.get(0)).metadata().streamId();
        int outboundBefore = fixture.outbound.size();
        String wrongNonce = "AQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

        fixture.request(new BridgeRequestCodec.StreamReopenRequest(wrongNonce, fixture.playerId,
                fixture.session.sessionUuid(), 2L, BridgeRequestCodec.Sender.NARCOTICS,
                Optional.of(currentStream), id(41)));

        assertTrue(fixture.sender.isReady(fixture.player), "a wrong nonce must be ignored without retiring the stream");
        assertEquals(outboundBefore, fixture.outbound.size());

        fixture.request(new BridgeRequestCodec.StreamReopenRequest(NONCE, fixture.playerId,
                fixture.session.sessionUuid(), 2L, BridgeRequestCodec.Sender.NARCOTICS,
                Optional.of(currentStream), id(42)));

        assertFalse(fixture.sender.isReady(fixture.player));
        assertEquals(outboundBefore + 1, fixture.outbound.size());
        assertNotEquals(currentStream,
                BridgeEnvelopeCodec.decode(fixture.outbound.get(outboundBefore)).metadata().streamId());
    }

    @Test
    void reopenNamingMostRecentlyRetiredStreamCannotRetireNewCurrentStream() throws Exception {
        Fixture fixture = readyFixture();
        String firstStream = fixture.sender.activeStreamId(fixture.player).orElseThrow();
        int outboundBefore = fixture.outbound.size();

        fixture.request(new BridgeRequestCodec.StreamReopenRequest(NONCE, fixture.playerId,
                fixture.session.sessionUuid(), 2L, BridgeRequestCodec.Sender.NARCOTICS,
                Optional.of(firstStream), id(57)));
        String currentStream = fixture.sender.activeStreamId(fixture.player).orElseThrow();
        assertNotEquals(firstStream, currentStream);
        assertEquals(outboundBefore + 1, fixture.outbound.size());

        fixture.now.addAndGet(1_001L);
        fixture.request(new BridgeRequestCodec.StreamReopenRequest(NONCE, fixture.playerId,
                fixture.session.sessionUuid(), 3L, BridgeRequestCodec.Sender.NARCOTICS,
                Optional.of(firstStream), id(58)));

        assertEquals(currentStream, fixture.sender.activeStreamId(fixture.player).orElseThrow(),
                "a stale retired ID must not rotate the newer active stream");
        assertEquals(outboundBefore + 1, fixture.outbound.size(),
                "a stale reopen must not emit a replacement stream acknowledgement");
    }

    @Test
    void emptyExpectedReopenCannotReplaceAReadyStreamAfterExplicitRetirement() throws Exception {
        Fixture fixture = readyFixture();
        String knownStream = BridgeEnvelopeCodec.decode(fixture.outbound.get(0)).metadata().streamId();
        int outboundBefore = fixture.outbound.size();
        fixture.sender.retire(fixture.playerId);

        fixture.request(new BridgeRequestCodec.StreamReopenRequest(NONCE, fixture.playerId,
                fixture.session.sessionUuid(), 2L, BridgeRequestCodec.Sender.NARCOTICS,
                Optional.empty(), id(43)));
        assertEquals(outboundBefore, fixture.outbound.size(), "empty expected ID must not reopen a known ready stream");

        fixture.request(new BridgeRequestCodec.StreamReopenRequest(NONCE, fixture.playerId,
                fixture.session.sessionUuid(), 3L, BridgeRequestCodec.Sender.NARCOTICS,
                Optional.of(knownStream), id(44)));
        assertEquals(outboundBefore + 1, fixture.outbound.size(), "the client-known retired ID remains a valid recovery fence");
    }

    @Test
    void failedReadyAckDoesNotPermitEmptyExpectedReopenAfterClientProvedHelloReceipt() throws Exception {
        Fixture fixture = new Fixture(true);
        fixture.request(new BridgeRequestCodec.ControlHello(NONCE, fixture.playerId,
                fixture.session.sessionUuid(), id(45)));
        String knownStream = BridgeEnvelopeCodec.decode(fixture.outbound.get(0)).metadata().streamId();
        int outboundBefore = fixture.outbound.size();
        fixture.signer.available = false;
        fixture.request(new BridgeRequestCodec.SenderReady(NONCE, fixture.playerId,
                fixture.session.sessionUuid(), 1L, BridgeRequestCodec.Sender.NARCOTICS,
                knownStream, id(46), sha256(fixture.outbound.get(0))));
        fixture.signer.available = true;

        fixture.request(new BridgeRequestCodec.StreamReopenRequest(NONCE, fixture.playerId,
                fixture.session.sessionUuid(), 2L, BridgeRequestCodec.Sender.NARCOTICS,
                Optional.empty(), id(47)));
        assertEquals(outboundBefore, fixture.outbound.size(), "a validated ready receipt proves the client knows the old stream");

        fixture.request(new BridgeRequestCodec.StreamReopenRequest(NONCE, fixture.playerId,
                fixture.session.sessionUuid(), 3L, BridgeRequestCodec.Sender.NARCOTICS,
                Optional.of(knownStream), id(48)));
        assertEquals(outboundBefore + 1, fixture.outbound.size(), "recovery remains available through its known stream ID");
    }

    @Test
    void timedOutStopIssuesFreshSignedStopForTheSameVisualInstance() throws Exception {
        Fixture fixture = readyFixture();
        fixture.now.set(System.currentTimeMillis());
        ClientCapabilityService capabilities = capabilities(fixture);
        ClientVisualEffectService visuals = new ClientVisualEffectService(null, capabilities, fixture.sender,
                fixture.now::get, ignored -> fixture.player);
        try {
            assertTrue(visuals.sendVisualStart(fixture.player, "INVERT", "", 20,
                    1.0F, 0, 0, "TEST", null, null) > 0L);
            NarcoticsBridgeBodyCodec.Body start = body(fixture.outbound.get(fixture.outbound.size() - 1));
            visuals.sendVisualStop(fixture.player, "INVERT", "test_stop");
            NarcoticsBridgeBodyCodec.Body stop = body(fixture.outbound.get(fixture.outbound.size() - 1));
            assertEquals(NarcoticsBridgeBodyCodec.Type.VISUAL_STOP, stop.type());

            fixture.now.addAndGet(32_001L);
            visuals.tick();

            assertEquals(6, fixture.outbound.size());
            BridgeEnvelope retryEnvelope = fixture.verify(fixture.outbound.get(5), 6L);
            NarcoticsBridgeBodyCodec.Body retry = NarcoticsBridgeBodyCodec.decode(retryEnvelope.bodyBytes());
            assertEquals(NarcoticsBridgeBodyCodec.Type.VISUAL_STOP, retry.type());
            assertTrue(retry.commandSeq() > stop.commandSeq());
            assertEquals(stop.targetVisualInstanceId(), retry.targetVisualInstanceId());
        } finally {
            visuals.shutdown();
            fixture.sender.close();
        }
    }

    @Test
    void stopWhileBridgeIsNotReadyDoesNotReassertPriorVisualOnNewStream() throws Exception {
        Fixture fixture = readyFixture();
        fixture.now.set(System.currentTimeMillis());
        ClientCapabilityService capabilities = capabilities(fixture);
        ClientVisualEffectService visuals = new ClientVisualEffectService(null, capabilities, fixture.sender,
                fixture.now::get, ignored -> fixture.player);
        try {
            assertTrue(visuals.sendVisualStart(fixture.player, "INVERT", "", 20,
                    1.0F, 0, 0, "TEST", null, null) > 0L);
            String retiredStream = fixture.sender.activeStreamId(fixture.player).orElseThrow();

            fixture.sender.retire(fixture.playerId);
            visuals.sendVisualStop(fixture.player, "INVERT", "test_stop_while_unready");

            fixture.request(new BridgeRequestCodec.StreamReopenRequest(NONCE, fixture.playerId,
                    fixture.session.sessionUuid(), 2L, BridgeRequestCodec.Sender.NARCOTICS,
                    Optional.of(retiredStream), id(49)));
            BridgeEnvelope reopened = BridgeEnvelopeCodec.decode(fixture.outbound.getLast());
            String replacementStream = reopened.metadata().streamId();
            byte[] readyId = id(50);
            fixture.request(new BridgeRequestCodec.SenderReady(NONCE, fixture.playerId,
                    fixture.session.sessionUuid(), 3L, BridgeRequestCodec.Sender.NARCOTICS,
                    replacementStream, readyId, sha256(fixture.outbound.getLast())));
            byte[] readyAck = fixture.outbound.getLast().clone();
            fixture.request(new BridgeRequestCodec.SenderReadyConfirm(NONCE, fixture.playerId,
                    fixture.session.sessionUuid(), BridgeRequestCodec.Sender.NARCOTICS,
                    replacementStream, readyId, 3L, sha256(readyAck)));
            assertTrue(fixture.sender.isReady(fixture.player));

            int beforeReconcile = fixture.outbound.size();
            visuals.onBridgeStateChanged(fixture.player);

            assertEquals(beforeReconcile, fixture.outbound.size(),
                    "a stop requested while unready must not restore the old visual on the replacement stream");
        } finally {
            visuals.shutdown();
            fixture.sender.close();
        }
    }

    @Test
    void deathCleanupPreservesCommandSequenceForTheCurrentSession() throws Exception {
        Fixture fixture = readyFixture();
        fixture.now.set(System.currentTimeMillis());
        ClientCapabilityService capabilities = capabilities(fixture);
        ClientVisualEffectService visuals = new ClientVisualEffectService(null, capabilities, fixture.sender,
                fixture.now::get, ignored -> fixture.player);
        try {
            assertEquals(1L, visuals.sendVisualStart(fixture.player, "INVERT", "", 5,
                    1.0F, 0, 0, "TEST", null, null));
            visuals.handleMessage(fixture.player, ClientBridgePayloads.visualAck(1L,
                    fixture.session.sessionUuid().toString(), "INVERT", "STARTED"));
            visuals.forgetPlayerPreservingSessionSequence(fixture.player);
            visuals.clearVisuals(fixture.player, "death");
            assertTrue(visuals.sessionsSummary().contains("pending=1"),
                    "the death clear must remain tracked for retry until its acknowledgement arrives");
            assertTrue(visuals.sessionsSummary().contains("correlated=1"),
                    "the death clear must retain its exact acknowledgement correlation");
            visuals.handleMessage(fixture.player, ClientBridgePayloads.visualAck(2L,
                    fixture.session.sessionUuid().toString(), "", "CLEARED"));
            assertTrue(visuals.sessionsSummary().contains("pending=0"));
            assertTrue(visuals.sessionsSummary().contains("correlated=0"));
            assertEquals(3L, visuals.sendVisualStart(fixture.player, "INVERT", "", 5,
                    1.0F, 0, 0, "TEST", null, null),
                    "a death clears presentation state but the client command sequence remains session-monotonic");
        } finally {
            visuals.shutdown();
            fixture.sender.close();
        }
    }

    @Test
    void acknowledgedRunningVisualSurvivesTemporaryBridgeRetirementAndIsReconciledOnNewStream() throws Exception {
        Fixture fixture = readyFixture();
        fixture.now.set(System.currentTimeMillis());
        ClientCapabilityService capabilities = capabilities(fixture);
        ClientVisualEffectService visuals = new ClientVisualEffectService(null, capabilities, fixture.sender,
                fixture.now::get, ignored -> fixture.player);
        try {
            long initialSequence = visuals.sendVisualStart(fixture.player, "INVERT", "", 20,
                    1.0F, 0, 0, "TEST", null, null);
            NarcoticsBridgeBodyCodec.Body initial = body(fixture.outbound.getLast());
            visuals.handleMessage(fixture.player, ClientBridgePayloads.visualAck(initialSequence,
                    fixture.session.sessionUuid().toString(), "INVERT", "STARTED"));

            String oldStreamId = fixture.sender.activeStreamId(fixture.player).orElseThrow();
            fixture.sender.retire(fixture.playerId);
            fixture.now.addAndGet(1_000L);
            visuals.tick();
            assertTrue(visuals.sessionsSummary().contains("running=1"),
                    "temporary bridge retirement must retain an unexpired desired visual");

            fixture.request(new BridgeRequestCodec.StreamReopenRequest(NONCE, fixture.playerId,
                    fixture.session.sessionUuid(), 2L, BridgeRequestCodec.Sender.NARCOTICS,
                    Optional.of(oldStreamId), id(44)));
            byte[] helloAckBytes = fixture.outbound.getLast().clone();
            BridgeEnvelope helloAck = fixture.verify(helloAckBytes, 1L);
            byte[] readyRequestId = id(45);
            fixture.request(new BridgeRequestCodec.SenderReady(NONCE, fixture.playerId,
                    fixture.session.sessionUuid(), 3L, BridgeRequestCodec.Sender.NARCOTICS,
                    helloAck.metadata().streamId(), readyRequestId, sha256(helloAckBytes)));
            byte[] readyAckBytes = fixture.outbound.getLast().clone();
            fixture.request(new BridgeRequestCodec.SenderReadyConfirm(NONCE, fixture.playerId,
                    fixture.session.sessionUuid(), BridgeRequestCodec.Sender.NARCOTICS,
                    helloAck.metadata().streamId(), readyRequestId, 3L, sha256(readyAckBytes)));
            assertTrue(fixture.sender.isReady(fixture.player));

            visuals.onBridgeStateChanged(fixture.player);

            NarcoticsBridgeBodyCodec.Body reconciled = body(fixture.outbound.getLast());
            assertEquals(NarcoticsBridgeBodyCodec.Type.VISUAL_START, reconciled.type());
            assertEquals(initial.visualInstanceId(), reconciled.visualInstanceId());
            assertTrue(reconciled.commandSeq() > initialSequence);
            assertEquals(19_000L, reconciled.durationMillis());
        } finally {
            visuals.shutdown();
            fixture.sender.close();
        }
    }

    @Test
    void expiredRunningVisualIsNotReconciledAfterTheBridgeReturns() throws Exception {
        Fixture fixture = readyFixture();
        fixture.now.set(System.currentTimeMillis());
        ClientCapabilityService capabilities = capabilities(fixture);
        ClientVisualEffectService visuals = new ClientVisualEffectService(null, capabilities, fixture.sender,
                fixture.now::get, ignored -> fixture.player);
        try {
            long sequence = visuals.sendVisualStart(fixture.player, "INVERT", "", 2,
                    1.0F, 0, 0, "TEST", null, null);
            visuals.handleMessage(fixture.player, ClientBridgePayloads.visualAck(sequence,
                    fixture.session.sessionUuid().toString(), "INVERT", "STARTED"));
            String oldStream = fixture.sender.activeStreamId(fixture.player).orElseThrow();
            fixture.sender.retire(fixture.playerId);
            fixture.now.addAndGet(2_001L);
            visuals.tick();
            assertTrue(visuals.sessionsSummary().contains("running=1"),
                    "the finished report remains correlated until the timeout grace expires");

            fixture.request(new BridgeRequestCodec.StreamReopenRequest(NONCE, fixture.playerId,
                    fixture.session.sessionUuid(), 2L, BridgeRequestCodec.Sender.NARCOTICS,
                    Optional.of(oldStream), id(46)));
            byte[] helloAck = fixture.outbound.getLast().clone();
            String stream = BridgeEnvelopeCodec.decode(helloAck).metadata().streamId();
            byte[] readyId = id(47);
            fixture.request(new BridgeRequestCodec.SenderReady(NONCE, fixture.playerId,
                    fixture.session.sessionUuid(), 3L, BridgeRequestCodec.Sender.NARCOTICS,
                    stream, readyId, sha256(helloAck)));
            byte[] readyAck = fixture.outbound.getLast().clone();
            fixture.request(new BridgeRequestCodec.SenderReadyConfirm(NONCE, fixture.playerId,
                    fixture.session.sessionUuid(), BridgeRequestCodec.Sender.NARCOTICS,
                    stream, readyId, 3L, sha256(readyAck)));
            int beforeReconcile = fixture.outbound.size();

            visuals.onBridgeStateChanged(fixture.player);

            assertEquals(beforeReconcile, fixture.outbound.size(),
                    "an expired desired visual must not be resurrected after stream recovery");
        } finally {
            visuals.shutdown();
            fixture.sender.close();
        }
    }

    @Test
    void normalVisualFinishedReportRemainsCorrelatedDuringTheTimeoutGrace() throws Exception {
        Fixture fixture = readyFixture();
        fixture.now.set(System.currentTimeMillis());
        ClientCapabilityService capabilities = capabilities(fixture);
        ClientVisualEffectService visuals = new ClientVisualEffectService(null, capabilities, fixture.sender,
                fixture.now::get, ignored -> fixture.player);
        try {
            long sequence = visuals.sendVisualStart(fixture.player, "INVERT", "", 2,
                    1.0F, 0, 0, "TEST", null, null);
            visuals.handleMessage(fixture.player, ClientBridgePayloads.visualAck(sequence,
                    fixture.session.sessionUuid().toString(), "INVERT", "STARTED"));

            fixture.now.addAndGet(2_500L);
            visuals.tick();
            assertTrue(visuals.sessionsSummary().contains("running=1"),
                    "a normal finish report must remain correlated until the timeout grace elapses");

            visuals.handleMessage(fixture.player, ClientBridgePayloads.visualFinished(sequence,
                    fixture.session.sessionUuid().toString(), "INVERT", "duration"));

            assertTrue(visuals.sessionsSummary().contains("running=0"));
            assertTrue(visuals.playerSummary(fixture.player).contains("lastFinished=INVERT:duration"));
        } finally {
            visuals.shutdown();
            fixture.sender.close();
        }
    }

    @Test
    void readinessQueriesAfterStreamRetirementPreserveSessionForTargetedReopen() throws Exception {
        Fixture fixture = readyFixture();
        String oldStream = fixture.sender.activeStreamId(fixture.player).orElseThrow();
        int outboundBefore = fixture.outbound.size();

        fixture.sender.retire(fixture.playerId);
        assertFalse(fixture.sender.isReady(fixture.player));
        assertTrue(fixture.sender.activeStreamId(fixture.player).isEmpty());
        assertEquals(-1L, fixture.sender.nextEnvelopeCounter(fixture.player));
        assertFalse(fixture.sender.sendVisualStart(fixture.player,
                startBody(fixture.session.sessionUuid()), true));

        fixture.request(new BridgeRequestCodec.StreamReopenRequest(NONCE, fixture.playerId,
                fixture.session.sessionUuid(), 2L, BridgeRequestCodec.Sender.NARCOTICS,
                Optional.of(oldStream), id(48)));

        assertEquals(outboundBefore + 1, fixture.outbound.size(),
                "a read-only readiness query must not erase the binding needed to reopen the retired stream");
        assertNotEquals(oldStream,
                BridgeEnvelopeCodec.decode(fixture.outbound.getLast()).metadata().streamId());
    }

    @Test
    void timedOutClearIssuesFreshSignedFenceWithNewCommandSequence() throws Exception {
        Fixture fixture = readyFixture();
        fixture.now.set(System.currentTimeMillis());
        ClientCapabilityService capabilities = capabilities(fixture);
        ClientVisualEffectService visuals = new ClientVisualEffectService(null, capabilities, fixture.sender,
                fixture.now::get, ignored -> fixture.player);
        try {
            visuals.clearVisuals(fixture.player, "test_clear");
            NarcoticsBridgeBodyCodec.Body clear = body(fixture.outbound.get(fixture.outbound.size() - 1));
            assertEquals(NarcoticsBridgeBodyCodec.Type.VISUAL_CLEAR_ALL, clear.type());

            fixture.now.addAndGet(32_001L);
            visuals.tick();

            assertEquals(5, fixture.outbound.size());
            BridgeEnvelope retryEnvelope = fixture.verify(fixture.outbound.get(4), 5L);
            NarcoticsBridgeBodyCodec.Body retry = NarcoticsBridgeBodyCodec.decode(retryEnvelope.bodyBytes());
            assertEquals(NarcoticsBridgeBodyCodec.Type.VISUAL_CLEAR_ALL, retry.type());
            assertTrue(retry.commandSeq() > clear.commandSeq());
            assertEquals(retryEnvelope.metadata().counter() - 1L, retry.clearThroughCounter());
        } finally {
            visuals.shutdown();
            fixture.sender.close();
        }
    }

    @Test
    void identicalHelloRetransmissionIsThrottledPerPlayerAndSender() throws Exception {
        Fixture fixture = new Fixture(true);
        byte[] hello = BridgeRequestCodec.encode(new BridgeRequestCodec.ControlHello(NONCE, fixture.playerId,
                fixture.session.sessionUuid(), id(5)));
        fixture.sender.onRequest(fixture.player, hello);
        byte[] first = fixture.outbound.get(0).clone();

        // One cached ACK replay is allowed, but repeated v4 hello packets cannot amplify sends.
        fixture.sender.onRequest(fixture.player, hello);
        assertEquals(2, fixture.outbound.size());
        assertArrayEquals(first, fixture.outbound.get(1));
        fixture.sender.onRequest(fixture.player, hello);
        assertEquals(2, fixture.outbound.size());
        fixture.now.addAndGet(999L);
        fixture.sender.onRequest(fixture.player, hello);
        assertEquals(2, fixture.outbound.size());
        fixture.now.addAndGet(1L);
        fixture.sender.onRequest(fixture.player, hello);
        assertEquals(3, fixture.outbound.size());
        assertArrayEquals(first, fixture.outbound.get(2));

        byte[] changedHello = BridgeRequestCodec.encode(new BridgeRequestCodec.ControlHello(NONCE,
                fixture.playerId, fixture.session.sessionUuid(), id(6)));
        fixture.sender.onRequest(fixture.player, changedHello);
        assertEquals(3, fixture.outbound.size(), "a new hello ID under the same session must not replace the stream");

        String differentNonce = "AQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        byte[] changed = BridgeRequestCodec.encode(new BridgeRequestCodec.ControlHello(differentNonce,
                fixture.playerId, fixture.session.sessionUuid(), id(7)));
        fixture.sender.onRequest(fixture.player, changed);
        assertEquals(3, fixture.outbound.size());
        assertArrayEquals(first, fixture.outbound.get(2));
    }

    @Test
    void helloAckReplayThrottleIsIndependentPerPlayer() throws Exception {
        Fixture fixture = new Fixture(true);
        byte[] firstHello = BridgeRequestCodec.encode(new BridgeRequestCodec.ControlHello(NONCE,
                fixture.playerId, fixture.session.sessionUuid(), id(12)));
        fixture.sender.onRequest(fixture.player, firstHello);
        fixture.sender.onRequest(fixture.player, firstHello);
        assertEquals(2, fixture.outbound.size());

        byte[] secondHello = BridgeRequestCodec.encode(new BridgeRequestCodec.ControlHello(NONCE,
                fixture.secondPlayerId, fixture.secondSession.sessionUuid(), id(13)));
        fixture.sender.onRequest(fixture.secondPlayer, secondHello);
        assertEquals(3, fixture.outbound.size());
        byte[] secondAck = fixture.outbound.get(2).clone();
        fixture.sender.onRequest(fixture.secondPlayer, secondHello);
        assertEquals(4, fixture.outbound.size(), "another player's first duplicate replay has its own throttle");
        assertArrayEquals(secondAck, fixture.outbound.get(3));
        assertEquals(fixture.secondPlayerId, BridgeEnvelopeCodec.decode(secondAck).metadata().recipientUuid());
    }

    @Test
    void expiredReadyAckCannotBeConfirmed() throws Exception {
        Fixture fixture = new Fixture(true);
        fixture.request(new BridgeRequestCodec.ControlHello(NONCE, fixture.playerId,
                fixture.session.sessionUuid(), id(7)));
        BridgeEnvelope hello = BridgeEnvelopeCodec.decode(fixture.outbound.get(0));
        byte[] readyId = id(8);
        fixture.request(new BridgeRequestCodec.SenderReady(NONCE, fixture.playerId,
                fixture.session.sessionUuid(), 1L, BridgeRequestCodec.Sender.NARCOTICS,
                hello.metadata().streamId(), readyId, sha256(fixture.outbound.get(0))));
        assertEquals(2, fixture.outbound.size());
        fixture.now.addAndGet(61_000L);
        fixture.request(new BridgeRequestCodec.SenderReady(NONCE, fixture.playerId,
                fixture.session.sessionUuid(), 1L, BridgeRequestCodec.Sender.NARCOTICS,
                hello.metadata().streamId(), readyId, sha256(fixture.outbound.get(0))));
        assertEquals(2, fixture.outbound.size(), "an expired cached ready ACK must not be replayed");
        fixture.request(new BridgeRequestCodec.SenderReadyConfirm(NONCE, fixture.playerId,
                fixture.session.sessionUuid(), BridgeRequestCodec.Sender.NARCOTICS,
                hello.metadata().streamId(), readyId, 1L,
                sha256(fixture.outbound.get(1))));
        assertFalse(fixture.sender.isReady(fixture.player));
        assertFalse(fixture.sender.sendVisualStart(fixture.player, startBody(fixture.session.sessionUuid()), true));
    }

    @Test
    void expiredUnacceptedHelloAckIsNotReplayedAndCanRecoverWithEmptyExpectedStream() throws Exception {
        Fixture fixture = new Fixture(true);
        byte[] helloId = id(10);
        fixture.request(new BridgeRequestCodec.ControlHello(NONCE, fixture.playerId,
                fixture.session.sessionUuid(), helloId));
        String expiredStream = BridgeEnvelopeCodec.decode(fixture.outbound.get(0)).metadata().streamId();

        fixture.now.addAndGet(61_000L);
        fixture.request(new BridgeRequestCodec.ControlHello(NONCE, fixture.playerId,
                fixture.session.sessionUuid(), helloId));
        assertEquals(1, fixture.outbound.size(), "an expired hello ACK must not be replayed");

        fixture.request(new BridgeRequestCodec.StreamReopenRequest(NONCE, fixture.playerId,
                fixture.session.sessionUuid(), 1L, BridgeRequestCodec.Sender.NARCOTICS,
                Optional.empty(), id(11)));
        assertEquals(2, fixture.outbound.size());
        BridgeEnvelope reopened = fixture.verify(fixture.outbound.get(1), 1L);
        assertNotEquals(expiredStream, reopened.metadata().streamId());
        BridgeControlBodyCodec.HelloAck ack = assertInstanceOf(BridgeControlBodyCodec.HelloAck.class,
                BridgeControlBodyCodec.decode(reopened.bodyBytes()));
        assertEquals(1, ack.requestKind());
        assertArrayEquals(id(11), ack.requestId());
    }

    @Test
    void exactReopenRetryAfterHelloAckExpiryGetsFreshSignedAckOnSameStream() throws Exception {
        Fixture fixture = readyFixture();
        String expectedStream = fixture.sender.activeStreamId(fixture.player).orElseThrow();
        byte[] reopenId = id(49);
        BridgeRequestCodec.StreamReopenRequest reopen = new BridgeRequestCodec.StreamReopenRequest(NONCE,
                fixture.playerId, fixture.session.sessionUuid(), 2L, BridgeRequestCodec.Sender.NARCOTICS,
                Optional.of(expectedStream), reopenId);
        byte[] exactRequest = BridgeRequestCodec.encode(reopen);

        fixture.sender.onRequest(fixture.player, exactRequest);
        byte[] firstAckBytes = fixture.outbound.getLast().clone();
        BridgeEnvelope firstAck = fixture.verify(firstAckBytes, 1L);
        assertNotEquals(expectedStream, firstAck.metadata().streamId());
        fixture.now.set(firstAck.metadata().expiresAtMillis());

        fixture.sender.onRequest(fixture.player, exactRequest);

        assertEquals(5, fixture.outbound.size(), "an expired exact duplicate must produce a fresh ACK");
        byte[] refreshedBytes = fixture.outbound.getLast();
        BridgeEnvelope refreshed = fixture.verify(refreshedBytes, 1L);
        assertEquals(firstAck.metadata().streamId(), refreshed.metadata().streamId(),
                "refreshing an unacknowledged stream must not rotate the client's stream fence");
        assertEquals(refreshed.metadata().streamId(), fixture.sender.activeStreamId(fixture.player).orElseThrow());
        assertTrue(refreshed.metadata().expiresAtMillis() > fixture.now.get());
        BridgeControlBodyCodec.HelloAck body = assertInstanceOf(BridgeControlBodyCodec.HelloAck.class,
                BridgeControlBodyCodec.decode(refreshed.bodyBytes()));
        assertEquals(1, body.requestKind());
        assertArrayEquals(reopenId, body.requestId());
    }

    @Test
    void failedReopenCanRetryAfterSigningRecoversEvenWithoutAnActiveStream() throws Exception {
        Fixture fixture = new Fixture(true);
        byte[] helloId = id(20);
        fixture.request(new BridgeRequestCodec.ControlHello(NONCE, fixture.playerId,
                fixture.session.sessionUuid(), helloId));
        String originalStream = BridgeEnvelopeCodec.decode(fixture.outbound.get(0)).metadata().streamId();
        BridgeRequestCodec.StreamReopenRequest reopen = new BridgeRequestCodec.StreamReopenRequest(
                NONCE, fixture.playerId, fixture.session.sessionUuid(), 1L,
                BridgeRequestCodec.Sender.NARCOTICS, Optional.of(originalStream), id(21));

        fixture.signer.available = false;
        fixture.request(reopen);
        assertEquals(1, fixture.outbound.size(), "failed signing must not emit an envelope");

        fixture.now.addAndGet(1_001L);
        fixture.signer.available = true;
        fixture.request(reopen);

        assertEquals(2, fixture.outbound.size(), "the exact reopen retry should recover the stream");
        BridgeEnvelope recovered = fixture.verify(fixture.outbound.get(1), 1L);
        BridgeControlBodyCodec.HelloAck ack = assertInstanceOf(BridgeControlBodyCodec.HelloAck.class,
                BridgeControlBodyCodec.decode(recovered.bodyBytes()));
        assertEquals(1, ack.requestKind());
        assertArrayEquals(id(21), ack.requestId());
    }

    @Test
    void failedInitialHelloCanRecoverFromReopenWithNoClientKnownStream() throws Exception {
        Fixture fixture = new Fixture(false);
        fixture.request(new BridgeRequestCodec.ControlHello(NONCE, fixture.playerId,
                fixture.session.sessionUuid(), id(27)));
        assertTrue(fixture.outbound.isEmpty(), "an unavailable signer must fail the initial hello closed");

        fixture.signer.available = true;
        fixture.request(new BridgeRequestCodec.StreamReopenRequest(NONCE, fixture.playerId,
                fixture.session.sessionUuid(), 1L, BridgeRequestCodec.Sender.NARCOTICS,
                Optional.empty(), id(28)));

        assertEquals(1, fixture.outbound.size(), "a client with no received stream must recover its initial hello");
        BridgeEnvelope recovered = fixture.verify(fixture.outbound.get(0), 1L);
        BridgeControlBodyCodec.HelloAck ack = assertInstanceOf(BridgeControlBodyCodec.HelloAck.class,
                BridgeControlBodyCodec.decode(recovered.bodyBytes()));
        assertEquals(1, ack.requestKind());
        assertArrayEquals(id(28), ack.requestId());
    }

    @Test
    void failedEmptyExpectedReopenCanRetryAfterInitialHelloSigningRecovers() throws Exception {
        Fixture fixture = new Fixture(false);
        fixture.request(new BridgeRequestCodec.ControlHello(NONCE, fixture.playerId,
                fixture.session.sessionUuid(), id(29)));
        BridgeRequestCodec.StreamReopenRequest reopen = new BridgeRequestCodec.StreamReopenRequest(
                NONCE, fixture.playerId, fixture.session.sessionUuid(), 1L,
                BridgeRequestCodec.Sender.NARCOTICS, Optional.empty(), id(30));

        fixture.request(reopen);
        assertTrue(fixture.outbound.isEmpty(), "both initial hello and reopen signing must fail closed");

        fixture.now.addAndGet(1_001L);
        fixture.signer.available = true;
        fixture.request(reopen);

        assertEquals(1, fixture.outbound.size(), "the exact empty-expected retry must recover after signer availability returns");
        BridgeEnvelope recovered = fixture.verify(fixture.outbound.get(0), 1L);
        BridgeControlBodyCodec.HelloAck ack = assertInstanceOf(BridgeControlBodyCodec.HelloAck.class,
                BridgeControlBodyCodec.decode(recovered.bodyBytes()));
        assertEquals(1, ack.requestKind());
        assertArrayEquals(id(30), ack.requestId());
    }

    @Test
    void rateLimitedExactDuplicateDoesNotReplayCachedResponse() throws Exception {
        Fixture fixture = new Fixture(true);
        BridgeRequestCodec.ControlHello hello = new BridgeRequestCodec.ControlHello(NONCE, fixture.playerId,
                fixture.session.sessionUuid(), id(21));
        fixture.request(hello);
        String streamId = BridgeEnvelopeCodec.decode(fixture.outbound.get(0)).metadata().streamId();
        BridgeRequestCodec.SenderReady ready = new BridgeRequestCodec.SenderReady(NONCE, fixture.playerId,
                fixture.session.sessionUuid(), 1L, BridgeRequestCodec.Sender.NARCOTICS,
                streamId, id(27), sha256(fixture.outbound.get(0)));
        fixture.request(ready);
        assertEquals(2, fixture.outbound.size());

        fixture.request(ready, false);

        assertEquals(2, fixture.outbound.size(),
                "a cached response must not bypass rate admission for an exact duplicate");
    }

    @Test
    void rateLimitedCountedRequestConsumesCounterWithoutExecutingAndNextCounterWorks() throws Exception {
        Fixture fixture = new Fixture(true);
        fixture.request(new BridgeRequestCodec.ControlHello(NONCE, fixture.playerId,
                fixture.session.sessionUuid(), id(22)));
        String streamId = BridgeEnvelopeCodec.decode(fixture.outbound.get(0)).metadata().streamId();
        int outboundBefore = fixture.outbound.size();

        BridgeRequestCodec.SenderReady denied = new BridgeRequestCodec.SenderReady(NONCE, fixture.playerId,
                fixture.session.sessionUuid(), 1L, BridgeRequestCodec.Sender.NARCOTICS,
                streamId, id(23), sha256(fixture.outbound.get(0)));
        fixture.request(denied, false);
        assertEquals(outboundBefore, fixture.outbound.size(), "rate-limited requests must not execute");

        BridgeRequestCodec.SenderReady next = new BridgeRequestCodec.SenderReady(NONCE, fixture.playerId,
                fixture.session.sessionUuid(), 2L, BridgeRequestCodec.Sender.NARCOTICS,
                streamId, id(24), sha256(fixture.outbound.get(0)));
        fixture.request(next);

        assertEquals(outboundBefore + 1, fixture.outbound.size(),
                "the next valid client counter must remain synchronized after an admission rejection");
        BridgeControlBodyCodec.SenderReadyAck ack = assertInstanceOf(BridgeControlBodyCodec.SenderReadyAck.class,
                BridgeControlBodyCodec.decode(BridgeEnvelopeCodec.decode(
                        fixture.outbound.get(fixture.outbound.size() - 1)).bodyBytes()));
        assertEquals(2L, ack.readyRequestCounter());
        assertArrayEquals(id(24), ack.requestId());
    }

    @Test
    void exactRateLimitedCountedRequestCanRetryAfterAdmissionRecovers() throws Exception {
        Fixture fixture = new Fixture(true);
        fixture.request(new BridgeRequestCodec.ControlHello(NONCE, fixture.playerId,
                fixture.session.sessionUuid(), id(25)));
        String streamId = BridgeEnvelopeCodec.decode(fixture.outbound.get(0)).metadata().streamId();
        int outboundBefore = fixture.outbound.size();
        BridgeRequestCodec.SenderReady retry = new BridgeRequestCodec.SenderReady(NONCE, fixture.playerId,
                fixture.session.sessionUuid(), 1L, BridgeRequestCodec.Sender.NARCOTICS,
                streamId, id(26), sha256(fixture.outbound.get(0)));

        fixture.request(retry, false);
        fixture.request(retry, true);

        assertEquals(outboundBefore + 1, fixture.outbound.size(),
                "an exact counted request without a cached response should be retryable after admission");
        BridgeControlBodyCodec.SenderReadyAck ack = assertInstanceOf(BridgeControlBodyCodec.SenderReadyAck.class,
                BridgeControlBodyCodec.decode(BridgeEnvelopeCodec.decode(
                        fixture.outbound.get(fixture.outbound.size() - 1)).bodyBytes()));
        assertEquals(1L, ack.readyRequestCounter());
        assertArrayEquals(id(26), ack.requestId());
    }

    private static NarcoticsBridgeBodyCodec.Body startBody(UUID session) {
        return new NarcoticsBridgeBodyCodec.Body(NarcoticsBridgeBodyCodec.Type.VISUAL_START,
                1L, 1_700_000_000_000L, session, "INVERT", "", 5_000L, 1.0F,
                0L, 0L, "CLIENT_MOD", "PRESERVE", "TEST", "", "", UUID.randomUUID(),
                new UUID(0L, 0L), 0L);
    }

    private static ClientCapabilityService capabilities(Fixture fixture) {
        ClientCapabilityService capabilities = new ClientCapabilityService();
        String sessionId = fixture.session.sessionUuid().toString();
        long now = fixture.now.get();
        capabilities.update(fixture.player, new ClientCapabilityState(4, "test", true,
                true, true, false, Set.of("INVERT"), sessionId, now, now));
        return capabilities;
    }

    private static NarcoticsBridgeBodyCodec.Body body(byte[] envelopeBytes) {
        return NarcoticsBridgeBodyCodec.decode(BridgeEnvelopeCodec.decode(envelopeBytes).bodyBytes());
    }

    private static Fixture readyFixture() throws Exception {
        Fixture fixture = new Fixture(true);
        byte[] helloId = id(37);
        fixture.request(new BridgeRequestCodec.ControlHello(NONCE, fixture.playerId,
                fixture.session.sessionUuid(), helloId));
        BridgeEnvelope hello = BridgeEnvelopeCodec.decode(fixture.outbound.get(0));
        byte[] readyId = id(38);
        fixture.request(new BridgeRequestCodec.SenderReady(NONCE, fixture.playerId,
                fixture.session.sessionUuid(), 1L, BridgeRequestCodec.Sender.NARCOTICS,
                hello.metadata().streamId(), readyId, sha256(fixture.outbound.get(0))));
        fixture.request(new BridgeRequestCodec.SenderReadyConfirm(NONCE, fixture.playerId,
                fixture.session.sessionUuid(), BridgeRequestCodec.Sender.NARCOTICS,
                hello.metadata().streamId(), readyId, 1L, sha256(fixture.outbound.get(1))));
        assertTrue(fixture.sender.isReady(fixture.player));
        return fixture;
    }

    private static byte[] id(int value) {
        byte[] result = new byte[16];
        result[15] = (byte) value;
        return result;
    }

    private static byte[] sha256(byte[] bytes) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(bytes);
    }

    private static final class Fixture {
        final UUID playerId = UUID.randomUUID();
        final ClientModSessionRegistry.VerifiedClientSession session =
                new ClientModSessionRegistry.VerifiedClientSession(playerId, UUID.randomUUID(), "26.3", UUID.randomUUID());
        final UUID secondPlayerId = UUID.randomUUID();
        final ClientModSessionRegistry.VerifiedClientSession secondSession =
                new ClientModSessionRegistry.VerifiedClientSession(secondPlayerId,
                        UUID.randomUUID(), "26.3", UUID.randomUUID());
        final List<byte[]> outbound = new ArrayList<>();
        final AtomicLong now = new AtomicLong(1_700_000_000_000L);
        final Player player = playerHandle(playerId, "bridge-test");
        final Player secondPlayer = playerHandle(secondPlayerId, "bridge-test-second");
        Optional<ClientModSessionRegistry.VerifiedClientSession> current = Optional.of(session);
        final TestSigner signer;
        final NarcoticsBridgeV4Sender sender;

        Fixture(boolean available) throws Exception {
            signer = new TestSigner(available);
            ClientModSessionRegistry registry = new ClientModSessionRegistry() {
                @Override public Optional<VerifiedClientSession> current(UUID uuid) {
                    if (uuid.equals(playerId)) return Fixture.this.current;
                    return uuid.equals(secondPlayerId) ? Optional.of(secondSession) : Optional.empty();
                }
                @Override public void requestFreshChallenge(UUID uuid) { }
                @Override public ListenerRegistration addInvalidationListener(InvalidationListener listener) {
                    return () -> { };
                }
                @Override public void removeInvalidationListener(InvalidationListener listener) { }
            };
            sender = new NarcoticsBridgeV4Sender(registry, signer, now::get,
                    (uuid, payload) -> outbound.add(payload.clone()));
        }

        void request(BridgeRequestCodec.Request request) {
            request(player, request);
        }

        void request(BridgeRequestCodec.Request request, boolean admitted) {
            byte[] payload = BridgeRequestCodec.encode(request);
            sender.onRequest(player, payload, admitted);
        }

        void request(Player target, BridgeRequestCodec.Request request) {
            sender.onRequest(target, BridgeRequestCodec.encode(request));
        }

        BridgeEnvelope verify(byte[] bytes, long counter) {
            BridgeEnvelope envelope = BridgeEnvelopeCodec.decode(bytes);
            assertEquals(counter, envelope.metadata().counter());
            assertEquals(playerId, envelope.metadata().recipientUuid());
            assertEquals(NONCE, envelope.metadata().clientNonce());
            assertEquals(BridgeSigningPolicy.VerificationResult.VALID,
                    BridgeSigningPolicy.verifyV4(signer.publicKey, envelope, now.get()));
            return envelope;
        }

        private static Player playerHandle(UUID playerUuid, String playerName) {
            return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getUniqueId" -> playerUuid;
                        case "isOnline" -> true;
                        case "getName" -> playerName;
                        default -> null;
                    });
        }
    }

    private static final class TestSigner implements BridgeSigner {
        final KeyPair pair;
        final java.security.PublicKey publicKey;
        boolean available;

        TestSigner(boolean available) throws Exception {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
            pair = generator.generateKeyPair();
            publicKey = pair.getPublic();
            this.available = available;
        }

        @Override public Optional<byte[]> sign(String nonce, UUID recipient, String streamId,
                long counter, long issuedAtMillis, long expiresAtMillis, byte[] body) {
            if (!available) return Optional.empty();
            BridgeEnvelope.Metadata metadata = new BridgeEnvelope.Metadata(4,
                    BridgeSigningPolicy.PINNED_KEY_ID, nonce, recipient, streamId,
                    counter, issuedAtMillis, expiresAtMillis);
            byte[] signature;
            try {
                Signature ed = Signature.getInstance("Ed25519");
                ed.initSign(pair.getPrivate());
                signature = BridgeSigningPolicy.signV4(pair.getPrivate(), metadata, body);
            } catch (Exception error) {
                throw new AssertionError(error);
            }
            return Optional.of(BridgeEnvelopeCodec.encode(new BridgeEnvelope(metadata, body, signature)));
        }
    }
}
