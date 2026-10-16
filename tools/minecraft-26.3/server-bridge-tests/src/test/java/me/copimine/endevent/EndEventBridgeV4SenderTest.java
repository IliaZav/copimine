package me.copimine.endevent;

import me.copimine.endevent.bridge.EndEventBridgeV4Sender;
import me.copimine.endevent.bridge.EndEventBridgeV4Access;
import me.copimine.protocol.BridgeControlBodyCodec;
import me.copimine.protocol.BridgeEnvelope;
import me.copimine.protocol.BridgeEnvelopeCodec;
import me.copimine.protocol.BridgeRequestCodec;
import me.copimine.protocol.BridgeSigner;
import me.copimine.protocol.BridgeSigningPolicy;
import me.copimine.protocol.ClientModSessionRegistry;
import me.copimine.protocol.EndEventBridgeBodyCodec;
import me.copimine.protocol.InboundBridgeRequestRateLimiter;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

final class EndEventBridgeV4SenderTest {
    private static final String NONCE = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Test
    void exactReadyConfirmRetryReplaysItsOriginalSignedReceipt() throws Exception {
        Fixture fixture = new Fixture(true, true);
        fixture.request(new BridgeRequestCodec.ControlHello(NONCE, fixture.playerUuid,
                fixture.session.sessionUuid(), id(1)));
        String stream = fixture.streamId();
        byte[] readyAckBytes = fixture.outbound.get(0).clone();
        fixture.request(new BridgeRequestCodec.SenderReady(NONCE, fixture.playerUuid,
                fixture.session.sessionUuid(), 1L, BridgeRequestCodec.Sender.END_EVENT, stream,
                id(2), sha256(readyAckBytes)));
        readyAckBytes = fixture.outbound.get(1).clone();
        BridgeRequestCodec.SenderReadyConfirm confirm = new BridgeRequestCodec.SenderReadyConfirm(NONCE,
                fixture.playerUuid, fixture.session.sessionUuid(), BridgeRequestCodec.Sender.END_EVENT,
                stream, id(2), 1L, sha256(readyAckBytes));
        fixture.request(confirm);
        byte[] receipt = fixture.outbound.get(2).clone();
        assertEquals(3L, fixture.verify(receipt, 3L).metadata().counter());

        fixture.request(confirm);

        assertTrue(fixture.sender.isReady(fixture.playerUuid));
        assertArrayEquals(receipt, fixture.outbound.getLast(),
                "the exact confirm retry must replay identical signed bytes without advancing the stream");
    }

    @Test
    void signedHandshakeAndExactReadyConfirmGateMutationSends() throws Exception {
        Fixture fixture = new Fixture(true, true);
        fixture.request(new BridgeRequestCodec.ControlHello(NONCE, fixture.playerUuid,
                fixture.session.sessionUuid(), id(1)));
        BridgeEnvelope hello = fixture.verify(fixture.outbound.get(0), 1L);
        BridgeControlBodyCodec.HelloAck helloAck = assertInstanceOf(BridgeControlBodyCodec.HelloAck.class,
                BridgeControlBodyCodec.decode(hello.bodyBytes()));
        assertEquals(BridgeRequestCodec.Sender.END_EVENT, helloAck.sender());
        assertEquals(fixture.session.sessionUuid(), helloAck.sessionUuid());

        byte[] readyId = id(2);
        fixture.request(new BridgeRequestCodec.SenderReady(NONCE, fixture.playerUuid,
                fixture.session.sessionUuid(), 1L, BridgeRequestCodec.Sender.END_EVENT,
                hello.metadata().streamId(), readyId, sha256(fixture.outbound.get(0))));
        BridgeEnvelope ready = fixture.verify(fixture.outbound.get(1), 2L);
        assertInstanceOf(BridgeControlBodyCodec.SenderReadyAck.class,
                BridgeControlBodyCodec.decode(ready.bodyBytes()));
        assertFalse(fixture.sender.isReady(fixture.playerUuid));

        fixture.request(new BridgeRequestCodec.SenderReadyConfirm(NONCE, fixture.playerUuid,
                fixture.session.sessionUuid(), BridgeRequestCodec.Sender.NARCOTICS,
                hello.metadata().streamId(), readyId, 1L, sha256(fixture.outbound.get(1))));
        assertFalse(fixture.sender.isReady(fixture.playerUuid), "another sender cannot confirm End Event readiness");

        fixture.request(new BridgeRequestCodec.SenderReadyConfirm(NONCE, fixture.playerUuid,
                fixture.session.sessionUuid(), BridgeRequestCodec.Sender.END_EVENT,
                hello.metadata().streamId(), readyId, 1L,
                sha256(fixture.outbound.get(1))));
        assertTrue(fixture.sender.isReady(fixture.playerUuid));
        assertTrue(fixture.sendValidState());
        assertEquals(5, fixture.outbound.size());
        BridgeEnvelope confirmReceipt = fixture.verify(fixture.outbound.get(2), 3L);
        BridgeControlBodyCodec.SenderReadyConfirmAck receipt = assertInstanceOf(
                BridgeControlBodyCodec.SenderReadyConfirmAck.class,
                BridgeControlBodyCodec.decode(confirmReceipt.bodyBytes()));
        assertArrayEquals(sha256(BridgeRequestCodec.encode(new BridgeRequestCodec.SenderReadyConfirm(NONCE,
                fixture.playerUuid, fixture.session.sessionUuid(), BridgeRequestCodec.Sender.END_EVENT,
                hello.metadata().streamId(), readyId, 1L, sha256(fixture.outbound.get(1))))),
                receipt.confirmRequestSha256());
        assertEquals(EndEventBridgeBodyCodec.Type.CONTEXT_BIND,
                EndEventBridgeBodyCodec.decode(fixture.verify(fixture.outbound.get(3), 4L).bodyBytes()).type());
        assertEquals(EndEventBridgeBodyCodec.Type.END_PLAYER_STATE,
                EndEventBridgeBodyCodec.decode(fixture.verify(fixture.outbound.get(4), 5L).bodyBytes()).type());
    }

    @Test
    void unavailableSignerAndWrongRegistryBindingFailClosed() throws Exception {
        Fixture unavailable = new Fixture(false, true);
        unavailable.request(new BridgeRequestCodec.ControlHello(NONCE, unavailable.playerUuid,
                unavailable.session.sessionUuid(), id(3)));
        assertTrue(unavailable.outbound.isEmpty());
        assertFalse(unavailable.sender.isReady(unavailable.playerUuid));

        Fixture trusted = new Fixture(true, true);
        trusted.request(new BridgeRequestCodec.ControlHello(NONCE, trusted.playerUuid,
                trusted.session.sessionUuid(), id(4)));
        trusted.current = Optional.empty();
        assertFalse(trusted.sender.isReady(trusted.playerUuid));
        assertFalse(trusted.sendValidState());
        assertEquals(1, trusted.outbound.size());
    }

    @Test
    void reconnectEpochRetiresOldSenderAndAcceptsOnlyNewSessionHello() throws Exception {
        Fixture fixture = readyFixture();
        String oldStream = fixture.streamId();
        fixture.current = Optional.of(new ClientModSessionRegistry.VerifiedClientSession(
                fixture.playerUuid, UUID.randomUUID(), "26.3", UUID.randomUUID()));

        assertFalse(fixture.sender.isReady(fixture.playerUuid));
        assertFalse(fixture.sendValidState());
        fixture.request(new BridgeRequestCodec.ControlHello(NONCE, fixture.playerUuid,
                fixture.current.orElseThrow().sessionUuid(), id(10)));

        assertEquals(5, fixture.outbound.size());
        BridgeEnvelope replacement = fixture.verify(fixture.outbound.get(4), 1L);
        assertNotEquals(oldStream, replacement.metadata().streamId());
        assertFalse(fixture.sender.isReady(fixture.playerUuid));
    }

    @Test
    void pluginRestartRetiresStreamsAndRequestsFreshVerifiedSessionChallenge() throws Exception {
        Fixture fixture = readyFixture();
        String previousStream = fixture.streamId();

        fixture.sender.requestFreshChallenges(List.of(fixture.playerUuid));

        assertEquals(List.of(fixture.playerUuid), fixture.requestedChallenges);
        assertFalse(fixture.sender.isReady(fixture.playerUuid));
        fixture.request(new BridgeRequestCodec.ControlHello(NONCE, fixture.playerUuid,
                fixture.session.sessionUuid(), id(16)));
        assertEquals(5, fixture.outbound.size());
        assertNotEquals(previousStream, fixture.verify(fixture.outbound.get(4), 1L).metadata().streamId());
    }

    @Test
    void expiredHelloAckCannotCompleteReadyHandshake() throws Exception {
        Fixture fixture = new Fixture(true, true);
        fixture.request(new BridgeRequestCodec.ControlHello(NONCE, fixture.playerUuid,
                fixture.session.sessionUuid(), id(11)));
        BridgeEnvelope hello = BridgeEnvelopeCodec.decode(fixture.outbound.get(0));
        fixture.now.set(hello.metadata().expiresAtMillis());

        fixture.request(new BridgeRequestCodec.SenderReady(NONCE, fixture.playerUuid,
                fixture.session.sessionUuid(), 1L, BridgeRequestCodec.Sender.END_EVENT,
                hello.metadata().streamId(), id(12), sha256(fixture.outbound.get(0))));

        assertEquals(1, fixture.outbound.size());
        assertFalse(fixture.sender.isReady(fixture.playerUuid));
    }

    @Test
    void confirmAtReadyAckExpiryFailsAndTargetedReopenRestartsTheStream() throws Exception {
        Fixture fixture = new Fixture(true, true);
        fixture.request(new BridgeRequestCodec.ControlHello(NONCE, fixture.playerUuid,
                fixture.session.sessionUuid(), id(13)));
        BridgeEnvelope hello = BridgeEnvelopeCodec.decode(fixture.outbound.get(0));
        byte[] readyId = id(14);
        fixture.request(new BridgeRequestCodec.SenderReady(NONCE, fixture.playerUuid,
                fixture.session.sessionUuid(), 1L, BridgeRequestCodec.Sender.END_EVENT,
                hello.metadata().streamId(), readyId, sha256(fixture.outbound.get(0))));
        BridgeEnvelope readyAck = BridgeEnvelopeCodec.decode(fixture.outbound.get(1));
        fixture.now.set(readyAck.metadata().expiresAtMillis());

        fixture.request(new BridgeRequestCodec.SenderReadyConfirm(NONCE, fixture.playerUuid,
                fixture.session.sessionUuid(), BridgeRequestCodec.Sender.END_EVENT,
                hello.metadata().streamId(), readyId, 1L, sha256(fixture.outbound.get(1))));
        assertFalse(fixture.sender.isReady(fixture.playerUuid));

        fixture.request(new BridgeRequestCodec.StreamReopenRequest(NONCE, fixture.playerUuid,
                fixture.session.sessionUuid(), 2L, BridgeRequestCodec.Sender.END_EVENT,
                Optional.of(hello.metadata().streamId()), id(15)));
        assertEquals(3, fixture.outbound.size());
        BridgeEnvelope reopened = fixture.verify(fixture.outbound.get(2), 1L);
        assertNotEquals(hello.metadata().streamId(), reopened.metadata().streamId());
    }

    @Test
    void exactReopenRetryAfterHelloAckExpiryGetsFreshSignedAckOnSameStream() throws Exception {
        Fixture fixture = readyFixture();
        String expectedStream = fixture.streamId();
        byte[] reopenId = id(62);
        BridgeRequestCodec.StreamReopenRequest reopen = new BridgeRequestCodec.StreamReopenRequest(NONCE,
                fixture.playerUuid, fixture.session.sessionUuid(), 2L, BridgeRequestCodec.Sender.END_EVENT,
                Optional.of(expectedStream), reopenId);
        byte[] exactRequest = BridgeRequestCodec.encode(reopen);

        fixture.sender.onRequest(fixture.player, exactRequest);
        byte[] firstAckBytes = fixture.outbound.getLast().clone();
        BridgeEnvelope firstAck = fixture.verify(firstAckBytes, 1L);
        assertNotEquals(expectedStream, firstAck.metadata().streamId());
        fixture.now.set(firstAck.metadata().expiresAtMillis());

        fixture.sender.onRequest(fixture.player, exactRequest);

        assertEquals(6, fixture.outbound.size(), "an expired exact duplicate must produce a fresh ACK");
        byte[] refreshedBytes = fixture.outbound.getLast();
        BridgeEnvelope refreshed = fixture.verify(refreshedBytes, 1L);
        assertEquals(firstAck.metadata().streamId(), refreshed.metadata().streamId(),
                "refreshing an unacknowledged stream must not rotate the client's stream fence");
        assertTrue(refreshed.metadata().expiresAtMillis() > fixture.now.get());
        BridgeControlBodyCodec.HelloAck body = assertInstanceOf(BridgeControlBodyCodec.HelloAck.class,
                BridgeControlBodyCodec.decode(refreshed.bodyBytes()));
        assertEquals(1, body.requestKind());
        assertArrayEquals(reopenId, body.requestId());
    }

    @Test
    void wrongBindingGapAndMalformedPacketsDoNotConsumeRequestCounter() throws Exception {
        Fixture fixture = readyFixture();
        String stream = fixture.streamId();
        fixture.request(new BridgeRequestCodec.PrisonerAbilityRequest(NONCE, UUID.randomUUID(),
                fixture.session.sessionUuid(), 2L, stream, fixture.event.eventUuid(), fixture.event.generation(),
                "HEAL", Optional.of(fixture.playerUuid)));
        fixture.request(new BridgeRequestCodec.PrisonerAbilityRequest(NONCE, fixture.playerUuid,
                fixture.session.sessionUuid(), 3L, stream, fixture.event.eventUuid(), fixture.event.generation(),
                "HEAL", Optional.of(fixture.playerUuid)));
        fixture.sender.onRequest(fixture.player, new byte[] {4, 1, 2});
        assertTrue(fixture.abilities.isEmpty());

        fixture.request(new BridgeRequestCodec.PrisonerAbilityRequest(NONCE, fixture.playerUuid,
                fixture.session.sessionUuid(), 2L, stream, fixture.event.eventUuid(), fixture.event.generation(),
                "HEAL", Optional.of(fixture.playerUuid)));
        assertEquals(1, fixture.abilities.size());
        fixture.request(new BridgeRequestCodec.PrisonerAbilityRequest(NONCE, fixture.playerUuid,
                fixture.session.sessionUuid(), 2L, stream, fixture.event.eventUuid(), fixture.event.generation(),
                "HEAL", Optional.of(fixture.playerUuid)));
        assertEquals(1, fixture.abilities.size(), "an exact replay must not repeat the mechanic");
    }

    @Test
    void rateDeniedAbilityConsumesItsCounterWithoutApplyingThenLaterCounterStillWorks() throws Exception {
        Fixture fixture = readyFixture();
        BridgeControlBodyCodec.SenderReadyAck readyAck = assertInstanceOf(BridgeControlBodyCodec.SenderReadyAck.class,
                BridgeControlBodyCodec.decode(BridgeEnvelopeCodec.decode(fixture.outbound.get(1)).bodyBytes()));
        byte[] duplicateConfirm = BridgeRequestCodec.encode(new BridgeRequestCodec.SenderReadyConfirm(
                NONCE, fixture.playerUuid, fixture.session.sessionUuid(), BridgeRequestCodec.Sender.END_EVENT,
                fixture.streamId(), readyAck.requestId(), readyAck.readyRequestCounter(), sha256(fixture.outbound.get(1))));
        fixture.sender.onPluginMessageReceived(EndEventBridgeV4Sender.CHANNEL, fixture.player, duplicateConfirm);

        byte[] deniedAbility = BridgeRequestCodec.encode(new BridgeRequestCodec.PrisonerAbilityRequest(NONCE,
                fixture.playerUuid, fixture.session.sessionUuid(), 2L, fixture.streamId(),
                fixture.event.eventUuid(), fixture.event.generation(), "HEAL", Optional.of(fixture.playerUuid)));
        fixture.sender.onPluginMessageReceived(EndEventBridgeV4Sender.CHANNEL, fixture.player, deniedAbility);
        assertTrue(fixture.abilities.isEmpty(), "the rate-denied ability must not run");

        fixture.requestNanos.set(InboundBridgeRequestRateLimiter.MIN_INTERVAL_NANOS);
        byte[] nextAbility = BridgeRequestCodec.encode(new BridgeRequestCodec.PrisonerAbilityRequest(NONCE,
                fixture.playerUuid, fixture.session.sessionUuid(), 3L, fixture.streamId(),
                fixture.event.eventUuid(), fixture.event.generation(), "HEAL", Optional.of(fixture.playerUuid)));
        fixture.sender.onPluginMessageReceived(EndEventBridgeV4Sender.CHANNEL, fixture.player, nextAbility);

        assertEquals(1, fixture.abilities.size(), "the next admitted counter must not be wedged by the denied packet");
        assertEquals("HEAL", fixture.abilities.getFirst().abilityId());
    }

    @Test
    void exactRetryOfRateDeniedAbilityAppliesAtMostOnce() throws Exception {
        Fixture fixture = readyFixture();
        BridgeControlBodyCodec.SenderReadyAck readyAck = assertInstanceOf(BridgeControlBodyCodec.SenderReadyAck.class,
                BridgeControlBodyCodec.decode(BridgeEnvelopeCodec.decode(fixture.outbound.get(1)).bodyBytes()));
        byte[] duplicateConfirm = BridgeRequestCodec.encode(new BridgeRequestCodec.SenderReadyConfirm(
                NONCE, fixture.playerUuid, fixture.session.sessionUuid(), BridgeRequestCodec.Sender.END_EVENT,
                fixture.streamId(), readyAck.requestId(), readyAck.readyRequestCounter(), sha256(fixture.outbound.get(1))));
        fixture.sender.onPluginMessageReceived(EndEventBridgeV4Sender.CHANNEL, fixture.player, duplicateConfirm);

        byte[] deniedAbility = BridgeRequestCodec.encode(new BridgeRequestCodec.PrisonerAbilityRequest(NONCE,
                fixture.playerUuid, fixture.session.sessionUuid(), 2L, fixture.streamId(),
                fixture.event.eventUuid(), fixture.event.generation(), "HEAL", Optional.of(fixture.playerUuid)));
        fixture.sender.onPluginMessageReceived(EndEventBridgeV4Sender.CHANNEL, fixture.player, deniedAbility);
        assertTrue(fixture.abilities.isEmpty(), "the first rate-denied delivery must not run");

        fixture.requestNanos.set(InboundBridgeRequestRateLimiter.MIN_INTERVAL_NANOS);
        fixture.sender.onPluginMessageReceived(EndEventBridgeV4Sender.CHANNEL, fixture.player, deniedAbility);
        assertEquals(1, fixture.abilities.size(), "an exact admitted retry may recover the denied action");

        fixture.requestNanos.set(2L * InboundBridgeRequestRateLimiter.MIN_INTERVAL_NANOS);
        fixture.sender.onPluginMessageReceived(EndEventBridgeV4Sender.CHANNEL, fixture.player, deniedAbility);
        assertEquals(1, fixture.abilities.size(), "the recovered action must remain idempotent on later replays");
    }

    @Test
    void rateDeniedSenderReadyCanRetryExactCounterAfterAdmissionWindow() throws Exception {
        Fixture fixture = new Fixture(true, true);
        byte[] helloRequest = BridgeRequestCodec.encode(new BridgeRequestCodec.ControlHello(NONCE,
                fixture.playerUuid, fixture.session.sessionUuid(), id(51)));
        fixture.sender.onPluginMessageReceived(EndEventBridgeV4Sender.CHANNEL, fixture.player, helloRequest);
        byte[] helloAck = fixture.outbound.getFirst().clone();
        String stream = BridgeEnvelopeCodec.decode(helloAck).metadata().streamId();
        byte[] readyRequest = BridgeRequestCodec.encode(new BridgeRequestCodec.SenderReady(NONCE,
                fixture.playerUuid, fixture.session.sessionUuid(), 1L, BridgeRequestCodec.Sender.END_EVENT,
                stream, id(52), sha256(helloAck)));

        fixture.sender.onPluginMessageReceived(EndEventBridgeV4Sender.CHANNEL, fixture.player, readyRequest);
        assertEquals(1, fixture.outbound.size(), "the immediate SenderReady is rate denied");

        fixture.requestNanos.set(InboundBridgeRequestRateLimiter.MIN_INTERVAL_NANOS);
        fixture.sender.onPluginMessageReceived(EndEventBridgeV4Sender.CHANNEL, fixture.player, readyRequest);

        assertEquals(2, fixture.outbound.size(), "the exact retry should complete the handshake");
        assertInstanceOf(BridgeControlBodyCodec.SenderReadyAck.class,
                BridgeControlBodyCodec.decode(BridgeEnvelopeCodec.decode(fixture.outbound.getLast()).bodyBytes()));
    }

    @Test
    void rateDeniedStreamReopenCanRetryExactCounterAfterAdmissionWindow() throws Exception {
        Fixture fixture = readyFixture();
        BridgeControlBodyCodec.SenderReadyAck readyAck = assertInstanceOf(BridgeControlBodyCodec.SenderReadyAck.class,
                BridgeControlBodyCodec.decode(BridgeEnvelopeCodec.decode(fixture.outbound.get(1)).bodyBytes()));
        byte[] duplicateConfirm = BridgeRequestCodec.encode(new BridgeRequestCodec.SenderReadyConfirm(
                NONCE, fixture.playerUuid, fixture.session.sessionUuid(), BridgeRequestCodec.Sender.END_EVENT,
                fixture.streamId(), readyAck.requestId(), readyAck.readyRequestCounter(), sha256(fixture.outbound.get(1))));
        fixture.sender.onPluginMessageReceived(EndEventBridgeV4Sender.CHANNEL, fixture.player, duplicateConfirm);
        int outboundBefore = fixture.outbound.size();
        byte[] reopen = BridgeRequestCodec.encode(new BridgeRequestCodec.StreamReopenRequest(NONCE,
                fixture.playerUuid, fixture.session.sessionUuid(), 2L, BridgeRequestCodec.Sender.END_EVENT,
                Optional.of(fixture.streamId()), id(53)));

        fixture.sender.onPluginMessageReceived(EndEventBridgeV4Sender.CHANNEL, fixture.player, reopen);
        assertEquals(outboundBefore, fixture.outbound.size(), "the first reopen is rate denied");

        fixture.requestNanos.set(InboundBridgeRequestRateLimiter.MIN_INTERVAL_NANOS);
        fixture.sender.onPluginMessageReceived(EndEventBridgeV4Sender.CHANNEL, fixture.player, reopen);

        assertEquals(outboundBefore + 1, fixture.outbound.size(), "the exact retry should produce a replacement HelloAck");
        BridgeControlBodyCodec.HelloAck reopenAck = assertInstanceOf(BridgeControlBodyCodec.HelloAck.class,
                BridgeControlBodyCodec.decode(BridgeEnvelopeCodec.decode(fixture.outbound.getLast()).bodyBytes()));
        assertEquals(1, reopenAck.requestKind());
    }

    @Test
    void pendingConfirmConsumesAbilityCounterButDoesNotApplyIt() throws Exception {
        Fixture fixture = new Fixture(true, true);
        fixture.request(new BridgeRequestCodec.ControlHello(NONCE, fixture.playerUuid,
                fixture.session.sessionUuid(), id(5)));
        String stream = fixture.streamId();
        byte[] readyId = id(6);
        fixture.request(new BridgeRequestCodec.SenderReady(NONCE, fixture.playerUuid,
                fixture.session.sessionUuid(), 1L, BridgeRequestCodec.Sender.END_EVENT, stream, readyId,
                sha256(fixture.outbound.get(0))));
        fixture.request(new BridgeRequestCodec.PrisonerAbilityRequest(NONCE, fixture.playerUuid,
                fixture.session.sessionUuid(), 2L, stream, fixture.event.eventUuid(), fixture.event.generation(),
                "HEAL", Optional.of(fixture.playerUuid)));
        assertTrue(fixture.abilities.isEmpty());
        fixture.request(new BridgeRequestCodec.SenderReadyConfirm(NONCE, fixture.playerUuid,
                fixture.session.sessionUuid(), BridgeRequestCodec.Sender.END_EVENT, stream, readyId, 1L,
                sha256(fixture.outbound.get(1))));
        fixture.request(new BridgeRequestCodec.PrisonerAbilityRequest(NONCE, fixture.playerUuid,
                fixture.session.sessionUuid(), 3L, stream, fixture.event.eventUuid(), fixture.event.generation(),
                "HEAL", Optional.of(fixture.playerUuid)));
        assertEquals(1, fixture.abilities.size());
    }

    @Test
    void staleStreamRequestConsumesCounterThenAllowsTargetedReopen() throws Exception {
        Fixture fixture = readyFixture();
        String oldStream = fixture.streamId();
        fixture.request(new BridgeRequestCodec.PrisonerAbilityRequest(NONCE, fixture.playerUuid,
                fixture.session.sessionUuid(), 2L, "AAAAAAAAAAAAAAAAAAAAAA", fixture.event.eventUuid(),
                fixture.event.generation(), "HEAL", Optional.of(fixture.playerUuid)));
        assertTrue(fixture.abilities.isEmpty());
        fixture.request(new BridgeRequestCodec.StreamReopenRequest(NONCE, fixture.playerUuid,
                fixture.session.sessionUuid(), 3L, BridgeRequestCodec.Sender.END_EVENT,
                Optional.of(oldStream), id(7)));
        assertEquals(5, fixture.outbound.size());
        BridgeEnvelope reopened = fixture.verify(fixture.outbound.get(4), 1L);
        assertNotEquals(oldStream, reopened.metadata().streamId());
        BridgeControlBodyCodec.HelloAck ack = assertInstanceOf(BridgeControlBodyCodec.HelloAck.class,
                BridgeControlBodyCodec.decode(reopened.bodyBytes()));
        assertEquals(1, ack.requestKind());
        assertArrayEquals(id(7), ack.requestId());
    }

    @Test
    void applicationTransportFailureRetiresStreamBeforeItsCounterCanBeReused() throws Exception {
        Fixture fixture = readyFixture();
        String stream = fixture.streamId();
        int outboundBefore = fixture.outbound.size();
        fixture.failNextSendAfterCapture = true;

        assertFalse(fixture.sendValidState());
        assertEquals(outboundBefore + 1, fixture.outbound.size());
        BridgeEnvelope failedSend = BridgeEnvelopeCodec.decode(fixture.outbound.getLast());
        assertEquals(stream, failedSend.metadata().streamId());
        assertEquals(5L, failedSend.metadata().counter());
        assertFalse(fixture.sender.isReady(fixture.playerUuid),
                "an ambiguous post-allocation send failure must retire the sender stream");

        fixture.now.incrementAndGet();
        assertFalse(fixture.sendValidState());
        assertEquals(outboundBefore + 1, fixture.outbound.size(),
                "a later mutation must not reuse the failed stream counter");
    }

    @Test
    void reopenTransportFailureRetainsAndReplaysTheExactSignedAcknowledgement() throws Exception {
        Fixture fixture = readyFixture();
        String oldStream = fixture.streamId();
        int outboundBefore = fixture.outbound.size();
        byte[] reopen = BridgeRequestCodec.encode(new BridgeRequestCodec.StreamReopenRequest(NONCE,
                fixture.playerUuid, fixture.session.sessionUuid(), 2L, BridgeRequestCodec.Sender.END_EVENT,
                Optional.of(oldStream), id(43)));
        fixture.failNextSendAfterCapture = true;

        fixture.sender.onRequest(fixture.player, reopen);
        assertEquals(outboundBefore + 1, fixture.outbound.size());
        byte[] capturedAck = fixture.outbound.getLast().clone();
        String replacementStream = BridgeEnvelopeCodec.decode(capturedAck).metadata().streamId();
        assertNotEquals(oldStream, replacementStream);
        assertFalse(fixture.sender.isReady(fixture.playerUuid));

        fixture.sender.onRequest(fixture.player, reopen);

        assertEquals(outboundBefore + 2, fixture.outbound.size(),
                "an exact retry must recover delivery of the already signed ACK");
        assertArrayEquals(capturedAck, fixture.outbound.getLast());
        assertEquals(replacementStream, BridgeEnvelopeCodec.decode(fixture.outbound.getLast()).metadata().streamId());
    }

    @Test
    void exactReopenRetryRecoversWhenSigningWasTemporarilyUnavailable() throws Exception {
        Fixture fixture = readyFixture();
        String oldStream = fixture.streamId();
        int outboundBefore = fixture.outbound.size();
        byte[] reopen = BridgeRequestCodec.encode(new BridgeRequestCodec.StreamReopenRequest(NONCE,
                fixture.playerUuid, fixture.session.sessionUuid(), 2L, BridgeRequestCodec.Sender.END_EVENT,
                Optional.of(oldStream), id(44)));
        fixture.signer.available = false;

        fixture.sender.onRequest(fixture.player, reopen);
        assertEquals(outboundBefore, fixture.outbound.size());
        assertFalse(fixture.sender.isReady(fixture.playerUuid));

        fixture.signer.available = true;
        fixture.now.addAndGet(1_000L);
        fixture.sender.onRequest(fixture.player, reopen);

        assertEquals(outboundBefore + 1, fixture.outbound.size());
        BridgeEnvelope reopened = fixture.verify(fixture.outbound.getLast(), 1L);
        assertNotEquals(oldStream, reopened.metadata().streamId());
    }

    @Test
    void senderReadyTransportFailureCanReplayExactAckAndCompleteHandshake() throws Exception {
        Fixture fixture = new Fixture(true, true);
        fixture.request(new BridgeRequestCodec.ControlHello(NONCE, fixture.playerUuid,
                fixture.session.sessionUuid(), id(45)));
        BridgeEnvelope hello = BridgeEnvelopeCodec.decode(fixture.outbound.getFirst());
        byte[] readyRequest = BridgeRequestCodec.encode(new BridgeRequestCodec.SenderReady(NONCE,
                fixture.playerUuid, fixture.session.sessionUuid(), 1L, BridgeRequestCodec.Sender.END_EVENT,
                hello.metadata().streamId(), id(46), sha256(fixture.outbound.getFirst())));
        fixture.failNextSendAfterCapture = true;

        fixture.sender.onRequest(fixture.player, readyRequest);
        byte[] capturedAck = fixture.outbound.getLast().clone();
        assertFalse(fixture.sender.isReady(fixture.playerUuid));

        fixture.sender.onRequest(fixture.player, readyRequest);
        assertEquals(3, fixture.outbound.size());
        assertArrayEquals(capturedAck, fixture.outbound.getLast());

        BridgeEnvelope readyAck = fixture.verify(capturedAck, 2L);
        fixture.request(new BridgeRequestCodec.SenderReadyConfirm(NONCE, fixture.playerUuid,
                fixture.session.sessionUuid(), BridgeRequestCodec.Sender.END_EVENT, hello.metadata().streamId(),
                id(46), 1L, sha256(capturedAck)));
        assertTrue(fixture.sender.isReady(fixture.playerUuid));
    }

    @Test
    void emptyExpectedStreamReplacesAnUnacceptedHelloSentStream() throws Exception {
        Fixture fixture = new Fixture(true, true);
        fixture.request(new BridgeRequestCodec.ControlHello(NONCE, fixture.playerUuid,
                fixture.session.sessionUuid(), id(17)));
        String unacceptedStream = fixture.streamId();

        assertTrue(fixture.reopen(1L, Optional.empty(), id(18)));

        assertEquals(2, fixture.outbound.size());
        BridgeEnvelope replacement = fixture.verify(fixture.outbound.get(1), 1L);
        assertNotEquals(unacceptedStream, replacement.metadata().streamId());
        BridgeControlBodyCodec.HelloAck ack = assertInstanceOf(BridgeControlBodyCodec.HelloAck.class,
                BridgeControlBodyCodec.decode(replacement.bodyBytes()));
        assertEquals(1, ack.requestKind());
        assertArrayEquals(id(18), ack.requestId());
    }

    @Test
    void nonemptyReopenRejectsRetiredIdsWhenANewerStreamIsCurrent() throws Exception {
        Fixture fixture = new Fixture(true, true);
        fixture.request(new BridgeRequestCodec.ControlHello(NONCE, fixture.playerUuid,
                fixture.session.sessionUuid(), id(19)));
        String first = fixture.streamId();

        assertTrue(fixture.reopen(1L, Optional.of(first), id(20)));
        String second = BridgeEnvelopeCodec.decode(fixture.outbound.get(1)).metadata().streamId();
        fixture.now.addAndGet(1_001L);
        assertFalse(fixture.reopen(2L, Optional.of(first), id(21)),
                "the most recently retired ID must not retire a different current stream");
        assertEquals(2, fixture.outbound.size());

        fixture.now.addAndGet(1_001L);
        assertTrue(fixture.reopen(3L, Optional.of(second), id(22)),
                "the current stream ID remains a valid replacement fence");
        String third = BridgeEnvelopeCodec.decode(fixture.outbound.get(2)).metadata().streamId();
        assertNotEquals(second, third);

        fixture.now.addAndGet(1_001L);
        assertFalse(fixture.reopen(4L, Optional.of(first), id(23)),
                "an older retired stream must not reopen over the current stream");
        assertEquals(3, fixture.outbound.size());
    }

    @Test
    void reopenIsThrottledPerSenderButExactLastRetransmitIsIdempotent() throws Exception {
        Fixture fixture = new Fixture(true, true);
        fixture.request(new BridgeRequestCodec.ControlHello(NONCE, fixture.playerUuid,
                fixture.session.sessionUuid(), id(23)));
        String first = fixture.streamId();
        BridgeRequestCodec.StreamReopenRequest accepted = new BridgeRequestCodec.StreamReopenRequest(
                NONCE, fixture.playerUuid, fixture.session.sessionUuid(), 1L,
                BridgeRequestCodec.Sender.END_EVENT, Optional.of(first), id(24));
        fixture.request(accepted);
        byte[] firstResponse = fixture.outbound.get(1).clone();

        fixture.now.addAndGet(500L);
        assertTrue(fixture.sender.onRequest(fixture.player, BridgeRequestCodec.encode(accepted)),
                "an exact retransmit replays the cached response");
        assertEquals(3, fixture.outbound.size());
        assertArrayEquals(firstResponse, fixture.outbound.get(2));

        String second = BridgeEnvelopeCodec.decode(firstResponse).metadata().streamId();
        fixture.request(new BridgeRequestCodec.StreamReopenRequest(NONCE, fixture.playerUuid,
                fixture.session.sessionUuid(), 2L, BridgeRequestCodec.Sender.END_EVENT,
                Optional.of(second), id(25)));
        assertEquals(3, fixture.outbound.size(), "a distinct reopen inside one second is throttled");

        fixture.now.addAndGet(500L);
        assertTrue(fixture.reopen(3L, Optional.of(second), id(26)),
                "the retransmit did not move the one-second reopen window");
        assertEquals(4, fixture.outbound.size());
    }

    @Test
    void repeatedControlHelloResendsCachedAckAtMostOncePerSecondWithoutRotatingStream() throws Exception {
        Fixture fixture = new Fixture(true, true);
        BridgeRequestCodec.ControlHello hello = new BridgeRequestCodec.ControlHello(NONCE, fixture.playerUuid,
                fixture.session.sessionUuid(), id(27));
        fixture.request(hello);
        byte[] cachedHelloAck = fixture.outbound.get(0).clone();
        String streamId = fixture.streamId();

        fixture.now.addAndGet(1_000L);
        assertTrue(fixture.sender.onRequest(fixture.player, BridgeRequestCodec.encode(hello)));
        assertEquals(2, fixture.outbound.size());
        assertArrayEquals(cachedHelloAck, fixture.outbound.get(1));

        fixture.now.addAndGet(500L);
        assertTrue(fixture.sender.onRequest(fixture.player, BridgeRequestCodec.encode(hello)));
        assertEquals(2, fixture.outbound.size(), "a cached hello ACK resend inside one second is suppressed");

        fixture.now.addAndGet(500L);
        assertTrue(fixture.sender.onRequest(fixture.player, BridgeRequestCodec.encode(hello)));
        assertEquals(3, fixture.outbound.size());
        assertArrayEquals(cachedHelloAck, fixture.outbound.get(2));
        assertEquals(streamId, BridgeEnvelopeCodec.decode(fixture.outbound.get(2)).metadata().streamId());

        fixture.request(new BridgeRequestCodec.SenderReady(NONCE, fixture.playerUuid,
                fixture.session.sessionUuid(), 1L, BridgeRequestCodec.Sender.END_EVENT,
                streamId, id(28), sha256(cachedHelloAck)));
        assertEquals(4, fixture.outbound.size(), "hello ACK retransmits do not consume stream counters");
        assertEquals(2L, BridgeEnvelopeCodec.decode(fixture.outbound.get(3)).metadata().counter());
    }

    @Test
    void heartbeatStartsAtFiveSecondsOnlyAfterConfirmedReadyAndUsesContiguousCounters() throws Exception {
        Fixture fixture = heartbeatFixture("0.1.3+26.3", false);
        assertEquals(37, BridgeEnvelopeCodec.decode(fixture.outbound.get(0)).bodyBytes().length);
        BridgeControlBodyCodec.HelloAck hello = (BridgeControlBodyCodec.HelloAck)
                BridgeControlBodyCodec.decode(BridgeEnvelopeCodec.decode(fixture.outbound.get(0)).bodyBytes());
        assertEquals(1, hello.featureFlags());
        fixture.now.addAndGet(5_000L);
        tick(fixture);
        assertEquals(2, fixture.outbound.size(), "pending confirm cannot emit heartbeat");
        confirmHeartbeatFixture(fixture);
        assertEquals(3L, fixture.verify(fixture.outbound.getLast(), 3L).metadata().counter());
        fixture.now.addAndGet(4_999L);
        tick(fixture);
        assertEquals(3, fixture.outbound.size());
        fixture.now.incrementAndGet();
        tick(fixture);
        assertHeartbeat(fixture, 4L);
        tick(fixture);
        assertEquals(4, fixture.outbound.size(), "same deadline emits only once");
        fixture.now.addAndGet(5_000L);
        tick(fixture);
        assertHeartbeat(fixture, 5L);
    }

    @Test
    void legacyOfflineInvalidatedAndClosedStreamsNeverEmitHeartbeat() throws Exception {
        for (String version : List.of("0.1.2+26.3", "26.3", "0.1.3+26.3-extra")) {
            Fixture legacy = heartbeatFixture(version, true);
            assertEquals(36, BridgeEnvelopeCodec.decode(legacy.outbound.get(0)).bodyBytes().length);
            legacy.now.addAndGet(10_000L);
            tick(legacy);
            assertEquals(3, legacy.outbound.size());
        }
        Fixture offline = heartbeatFixture("0.1.3+26.3", true);
        offline.online = false;
        offline.now.addAndGet(5_000L);
        tick(offline);
        assertEquals(3, offline.outbound.size());
        Fixture invalid = heartbeatFixture("0.1.3+26.3", true);
        invalid.current = Optional.empty();
        invalid.now.addAndGet(5_000L);
        tick(invalid);
        assertEquals(3, invalid.outbound.size());
        Fixture closed = heartbeatFixture("0.1.3+26.3", true);
        closed.sender.close();
        closed.now.addAndGet(5_000L);
        tick(closed);
        assertEquals(3, closed.outbound.size());
    }

    @Test
    void heartbeatFailuresRetireStreamAndPreserveTargetedReopenFence() throws Exception {
        for (int failure = 0; failure < 3; failure++) {
            Fixture fixture = heartbeatFixture("0.1.3+26.3", true);
            String retired = BridgeEnvelopeCodec.decode(fixture.outbound.get(0)).metadata().streamId();
            if (failure == 0) fixture.signer.available = false;
            if (failure == 1) fixture.failNextSendAfterCapture = true;
            if (failure == 2) exhaustHeartbeatCounter(fixture);
            fixture.now.addAndGet(5_000L);
            tick(fixture);
            assertFalse(fixture.sender.isReady(fixture.playerUuid));
            int afterFailure = fixture.outbound.size();
            tick(fixture);
            assertEquals(afterFailure, fixture.outbound.size(), "retired stream cannot reuse a counter");
            fixture.signer.available = true;
            fixture.request(new BridgeRequestCodec.StreamReopenRequest(NONCE, fixture.playerUuid,
                    fixture.session.sessionUuid(), 2L, BridgeRequestCodec.Sender.END_EVENT,
                    Optional.of(retired), id(91)));
            BridgeEnvelope reopened = fixture.verify(fixture.outbound.getLast(), 1L);
            assertNotEquals(retired, reopened.metadata().streamId());
            assertEquals(1, ((BridgeControlBodyCodec.HelloAck)
                    BridgeControlBodyCodec.decode(reopened.bodyBytes())).featureFlags());
        }
    }

    @Test
    void streamsKeepIndependentCadenceAndApplicationCounters() throws Exception {
        Fixture first = heartbeatFixture("0.1.3+26.3", true);
        Fixture second = heartbeatFixture("0.1.3+26.3", false);
        first.now.addAndGet(5_000L);
        second.now.addAndGet(2_000L);
        confirmHeartbeatFixture(second);
        tick(first);
        tick(second);
        assertHeartbeat(first, 4L);
        assertEquals(3, second.outbound.size());
        assertTrue(first.sender.bindContext(first.playerUuid, first.event));
        first.verify(first.outbound.getLast(), 5L);
        first.now.addAndGet(5_000L);
        second.now.addAndGet(4_999L);
        tick(first);
        tick(second);
        assertHeartbeat(first, 6L);
        assertEquals(3, second.outbound.size());
        second.now.incrementAndGet();
        tick(second);
        assertHeartbeat(second, 4L);
    }

    private static Fixture heartbeatFixture(String version, boolean confirm) throws Exception {
        Fixture fixture = new Fixture(true, false, version);
        tick(fixture);
        assertTrue(fixture.outbound.isEmpty());
        fixture.request(new BridgeRequestCodec.ControlHello(NONCE, fixture.playerUuid,
                fixture.session.sessionUuid(), id(89)));
        tick(fixture);
        assertEquals(1, fixture.outbound.size());
        fixture.request(new BridgeRequestCodec.SenderReady(NONCE, fixture.playerUuid,
                fixture.session.sessionUuid(), 1L, BridgeRequestCodec.Sender.END_EVENT,
                BridgeEnvelopeCodec.decode(fixture.outbound.get(0)).metadata().streamId(),
                id(90), sha256(fixture.outbound.get(0))));
        if (confirm) confirmHeartbeatFixture(fixture);
        return fixture;
    }

    private static void confirmHeartbeatFixture(Fixture fixture) throws Exception {
        fixture.request(new BridgeRequestCodec.SenderReadyConfirm(NONCE, fixture.playerUuid,
                fixture.session.sessionUuid(), BridgeRequestCodec.Sender.END_EVENT,
                BridgeEnvelopeCodec.decode(fixture.outbound.get(0)).metadata().streamId(),
                id(90), 1L, sha256(fixture.outbound.get(1))));
        assertTrue(fixture.sender.isReady(fixture.playerUuid));
    }

    private static void tick(Fixture fixture) throws Exception {
        fixture.sender.tick();
    }

    private static void assertHeartbeat(Fixture fixture, long counter) {
        BridgeEnvelope envelope = fixture.verify(fixture.outbound.getLast(), counter);
        assertEquals(30_000L, envelope.metadata().expiresAtMillis() - envelope.metadata().issuedAtMillis());
        assertEquals(BridgeEnvelopeCodec.decode(fixture.outbound.get(0)).metadata().streamId(),
                envelope.metadata().streamId());
        BridgeControlBodyCodec.SenderHeartbeat heartbeat = assertInstanceOf(
                BridgeControlBodyCodec.SenderHeartbeat.class, BridgeControlBodyCodec.decode(envelope.bodyBytes()));
        assertEquals(BridgeRequestCodec.Sender.END_EVENT, heartbeat.sender());
        assertEquals(fixture.session.sessionUuid(), heartbeat.sessionUuid());
    }

    private static void exhaustHeartbeatCounter(Fixture fixture) throws Exception {
        var playersField = fixture.sender.getClass().getDeclaredField("players");
        playersField.setAccessible(true);
        Object state = ((java.util.Map<?, ?>) playersField.get(fixture.sender)).get(fixture.playerUuid);
        var streamField = state.getClass().getDeclaredField("stream");
        streamField.setAccessible(true);
        Object stream = streamField.get(state);
        var counterField = stream.getClass().getDeclaredField("serverCounter");
        counterField.setAccessible(true);
        counterField.setLong(stream, Long.MAX_VALUE);
    }

    private static Fixture readyFixture() throws Exception {
        Fixture fixture = new Fixture(true, true);
        fixture.request(new BridgeRequestCodec.ControlHello(NONCE, fixture.playerUuid,
                fixture.session.sessionUuid(), id(8)));
        byte[] readyId = id(9);
        fixture.request(new BridgeRequestCodec.SenderReady(NONCE, fixture.playerUuid,
                fixture.session.sessionUuid(), 1L, BridgeRequestCodec.Sender.END_EVENT,
                fixture.streamId(), readyId, sha256(fixture.outbound.get(0))));
        BridgeRequestCodec.SenderReadyConfirm confirm = new BridgeRequestCodec.SenderReadyConfirm(NONCE, fixture.playerUuid,
                fixture.session.sessionUuid(), BridgeRequestCodec.Sender.END_EVENT,
                fixture.streamId(), readyId, 1L, sha256(fixture.outbound.get(1)));
        fixture.request(confirm);
        assertTrue(fixture.sender.isReady(fixture.playerUuid));
        assertEquals(4, fixture.outbound.size());
        byte[] receipt = fixture.outbound.get(2).clone();
        assertEquals(3L, BridgeEnvelopeCodec.decode(receipt).metadata().counter());
        return fixture;
    }

    private static byte[] id(int value) {
        byte[] bytes = new byte[16];
        bytes[15] = (byte) value;
        return bytes;
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    private static final class Fixture {
        final UUID playerUuid = UUID.randomUUID();
        final UUID eventUuid = UUID.randomUUID();
        final ClientModSessionRegistry.VerifiedClientSession session;
        final EndEventBridgeV4Access.EventContext event = new EndEventBridgeV4Access.EventContext(
                eventUuid, 1L, "the_end", -8, 0, -8, 8, 128, 8);
        final AtomicLong now = new AtomicLong(1_700_000_000_000L);
        final AtomicLong requestNanos = new AtomicLong();
        final List<byte[]> outbound = new ArrayList<>();
        final List<UUID> requestedChallenges = new ArrayList<>();
        final List<EndEventBridgeV4Access.PrisonerAbilityRequest> abilities = new ArrayList<>();
        boolean online = true;
        boolean failNextSendAfterCapture;
        final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> playerUuid;
                    case "isOnline" -> online;
                    case "getName" -> "end-event-test";
                    default -> null;
                });
        Optional<ClientModSessionRegistry.VerifiedClientSession> current;
        final TestSigner signer;
        final EndEventBridgeV4Sender sender;

        Fixture(boolean signerAvailable, boolean contextAvailable) throws Exception {
            this(signerAvailable, contextAvailable, "26.3");
        }

        Fixture(boolean signerAvailable, boolean contextAvailable, String version) throws Exception {
            session = new ClientModSessionRegistry.VerifiedClientSession(playerUuid, UUID.randomUUID(), version, UUID.randomUUID());
            current = Optional.of(session);
            signer = new TestSigner(signerAvailable);
            ClientModSessionRegistry registry = new ClientModSessionRegistry() {
                @Override public Optional<VerifiedClientSession> current(UUID uuid) {
                    return uuid.equals(playerUuid) ? Fixture.this.current : Optional.empty();
                }
                @Override public void requestFreshChallenge(UUID uuid) { requestedChallenges.add(uuid); }
                @Override public ListenerRegistration addInvalidationListener(InvalidationListener listener) {
                    return () -> { };
                }
                @Override public void removeInvalidationListener(InvalidationListener listener) { }
            };
            sender = new EndEventBridgeV4Sender(registry, signer, now::get,
                    (recipient, payload) -> {
                        outbound.add(payload.clone());
                        if (failNextSendAfterCapture) {
                            failNextSendAfterCapture = false;
                            throw new IllegalStateException("simulated uncertain transport delivery");
                        }
                    },
                    uuid -> contextAvailable ? event : null,
                    (recipient, request) -> abilities.add(request),
                    new InboundBridgeRequestRateLimiter(requestNanos::get));
            sender.registerPlayer(player);
        }

        void request(BridgeRequestCodec.Request request) {
            sender.onRequest(player, BridgeRequestCodec.encode(request));
        }

        boolean reopen(long counter, Optional<String> expectedStream, byte[] requestId) {
            return sender.onRequest(player, BridgeRequestCodec.encode(new BridgeRequestCodec.StreamReopenRequest(
                    NONCE, playerUuid, session.sessionUuid(), counter, BridgeRequestCodec.Sender.END_EVENT,
                    expectedStream, requestId)));
        }

        boolean sendValidState() {
            return sender.send(playerUuid, "END_PLAYER_STATE", "wave1-carrier-charge", 1_000L,
                    "the_end|wave1|1", "WAVE1_CARRIER_V1", 0, 0, 0.2F, "", "ACTIVE");
        }

        String streamId() {
            return BridgeEnvelopeCodec.decode(outbound.get(0)).metadata().streamId();
        }

        BridgeEnvelope verify(byte[] bytes, long counter) {
            BridgeEnvelope envelope = BridgeEnvelopeCodec.decode(bytes);
            assertEquals(counter, envelope.metadata().counter());
            assertEquals(playerUuid, envelope.metadata().recipientUuid());
            assertEquals(NONCE, envelope.metadata().clientNonce());
            assertEquals(BridgeSigningPolicy.VerificationResult.VALID,
                    BridgeSigningPolicy.verifyV4(signer.publicKey, envelope, now.get()));
            return envelope;
        }
    }

    private static final class TestSigner implements BridgeSigner {
        private final KeyPair pair;
        final java.security.PublicKey publicKey;
        boolean available;

        TestSigner(boolean available) throws Exception {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
            pair = generator.generateKeyPair();
            publicKey = pair.getPublic();
            this.available = available;
        }

        @Override public Optional<byte[]> sign(String nonce, UUID recipient, String streamId, long counter,
                long issuedAtMillis, long expiresAtMillis, byte[] exactBody) {
            if (!available) return Optional.empty();
            BridgeEnvelope.Metadata metadata = new BridgeEnvelope.Metadata(4, BridgeSigningPolicy.PINNED_KEY_ID,
                    nonce, recipient, streamId, counter, issuedAtMillis, expiresAtMillis);
            try {
                Signature signature = Signature.getInstance("Ed25519");
                signature.initSign(pair.getPrivate());
                signature.update(BridgeSigningPolicy.canonicalV4SigningInput(metadata, exactBody));
                return Optional.of(BridgeEnvelopeCodec.encode(new BridgeEnvelope(metadata, exactBody,
                        signature.sign())));
            } catch (Exception error) {
                throw new AssertionError(error);
            }
        }
    }
}
