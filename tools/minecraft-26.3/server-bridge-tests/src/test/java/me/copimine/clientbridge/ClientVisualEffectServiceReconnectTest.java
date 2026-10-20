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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

final class ClientVisualEffectServiceReconnectTest {
    private static final String CLIENT_NONCE = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Test
    void successfulResendRemainsDesiredAcrossReconnectsAndDuplicateStateCallbacks() {
        Fixture fixture = new Fixture();
        fixture.startVisual(60);

        fixture.reconnect();
        fixture.service.onBridgeStateChanged(fixture.player);
        assertEquals(2, fixture.visualCommands().size());
        assertTrue(fixture.service.playerSummary(fixture.player).contains("pending_ack:INVERT#2"));
        assertTrue(fixture.fallbackCalls.isEmpty());

        fixture.service.onBridgeStateChanged(fixture.player);
        assertEquals(2, fixture.visualCommands().size(), "a duplicate callback for one stream must not resend twice");

        fixture.reconnect();
        fixture.service.onBridgeStateChanged(fixture.player);
        assertEquals(3, fixture.visualCommands().size(), "a later reconnect must resend the active desired visual");
        assertTrue(fixture.service.playerSummary(fixture.player).contains("pending_ack:INVERT#3"));
        assertTrue(fixture.fallbackCalls.isEmpty());
        assertEquals("pending=1, running=0, correlated=1", fixture.service.sessionsSummary());
    }

    @Test
    void failedResendRetiresDesiredVisualAndRunsItsFallbackOnlyOnce() {
        Fixture fixture = new Fixture();
        fixture.startVisual(60);
        fixture.reconnect();
        fixture.signer.failNextVisual = true;

        fixture.service.onBridgeStateChanged(fixture.player);

        assertEquals(1, fixture.visualCommands().size(), "a failed signature must not be reported as a dispatched resend");
        assertEquals(1, fixture.fallbackCalls.size());
        FallbackCall fallback = fixture.fallbackCalls.getFirst();
        assertEquals(fixture.playerId, fallback.playerUuid());
        assertEquals("INVERT", fallback.effectId());
        assertEquals(60, fallback.seconds());
        assertEquals("test-reconnect", fallback.source());
        assertEquals("stream-resend-failed", fallback.reason());
        assertEquals("pending=0, running=0, correlated=0", fixture.service.sessionsSummary());
        assertTrue(fixture.service.playerSummary(fixture.player).contains("lastError=INVERT:stream-resend-failed"));

        fixture.service.onBridgeStateChanged(fixture.player);
        assertEquals(1, fixture.fallbackCalls.size(), "repeating the state callback must not invoke fallback again");

        fixture.reconnect();
        fixture.service.onBridgeStateChanged(fixture.player);
        assertEquals(1, fixture.visualCommands().size(), "a retired visual must not be resurrected on another reconnect");
        assertEquals(1, fixture.fallbackCalls.size());
    }

    @Test
    void expiredDesiredVisualIsRetiredWithoutResendOrFallback() {
        Fixture fixture = new Fixture();
        fixture.startVisual(1);
        fixture.reconnect();

        fixture.service.onBridgeStateChanged(fixture.player);

        assertEquals(1, fixture.visualCommands().size());
        assertTrue(fixture.fallbackCalls.isEmpty(), "an expired effect has no remaining visual work to fall back for");
        assertEquals("pending=0, running=0, correlated=0", fixture.service.sessionsSummary());
        assertTrue(fixture.service.playerSummary(fixture.player).startsWith("none,"));

        fixture.service.onBridgeStateChanged(fixture.player);
        fixture.reconnect();
        fixture.service.onBridgeStateChanged(fixture.player);
        assertEquals(1, fixture.visualCommands().size(), "later reconnects must not resurrect an expired visual");
        assertTrue(fixture.fallbackCalls.isEmpty());
    }

    private record FallbackCall(UUID playerUuid, String effectId, int seconds, float intensity,
                                String source, String reason) {
    }

    private static final class Fixture {
        private final UUID playerId = UUID.randomUUID();
        private final UUID sessionId = UUID.randomUUID();
        private final ClientModSessionRegistry.VerifiedClientSession session =
                new ClientModSessionRegistry.VerifiedClientSession(playerId, sessionId, "26.3", UUID.randomUUID());
        private final Player player = playerHandle(playerId);
        private final AtomicLong now = new AtomicLong(System.currentTimeMillis());
        private final List<byte[]> outbound = new ArrayList<>();
        private final List<FallbackCall> fallbackCalls = new ArrayList<>();
        private final TestSigner signer = new TestSigner();
        private final NarcoticsBridgeV4Sender sender;
        private final ClientVisualEffectService service;
        private String knownStreamId;
        private long nextClientCounter = 2L;
        private int nextRequestId = 10;

        private Fixture() {
            ClientModSessionRegistry registry = new ClientModSessionRegistry() {
                @Override
                public Optional<VerifiedClientSession> current(UUID uuid) {
                    return playerId.equals(uuid) ? Optional.of(session) : Optional.empty();
                }

                @Override
                public void requestFreshChallenge(UUID uuid) {
                }

                @Override
                public ListenerRegistration addInvalidationListener(InvalidationListener listener) {
                    return () -> { };
                }

                @Override
                public void removeInvalidationListener(InvalidationListener listener) {
                }
            };
            sender = new NarcoticsBridgeV4Sender(registry, signer, now::get,
                    (uuid, payload) -> outbound.add(payload.clone()));
            assertTrue(sender.startStream(player, session, CLIENT_NONCE, requestId()));
            finishReadyHandshake(1L);

            ClientCapabilityService capabilities = new ClientCapabilityService();
            long capabilityNow = System.currentTimeMillis();
            capabilities.update(player, new ClientCapabilityState(4, "test", true, true, true, false,
                    Set.of("INVERT"), sessionId.toString(), capabilityNow, capabilityNow));
            service = new ClientVisualEffectService(null, capabilities, sender, now::get, uuid -> player);
        }

        private void startVisual(int seconds) {
            long seq = service.sendVisualStart(player, "INVERT", "", seconds, 0.8F,
                    0, 0, "test-reconnect", (uuid, effect, duration, intensity, source, reason) ->
                            fallbackCalls.add(new FallbackCall(uuid, effect, duration, intensity, source, reason)), null);
            assertTrue(seq > 0L, "the fixture must begin with an accepted client visual command");
            knownStreamId = sender.activeStreamId(player).orElseThrow();
        }

        private void reconnect() {
            now.addAndGet(1_001L);
            String expectedStream = sender.activeStreamId(player).orElse(knownStreamId);
            assertNotNull(expectedStream, "the reconnect must name the current or most recently known stream");
            BridgeRequestCodec.StreamReopenRequest reopen = new BridgeRequestCodec.StreamReopenRequest(
                    CLIENT_NONCE, playerId, sessionId, nextClientCounter++, BridgeRequestCodec.Sender.NARCOTICS,
                    Optional.of(expectedStream), requestId());
            sender.onRequest(player, BridgeRequestCodec.encode(reopen));
            assertNotEquals(expectedStream, sender.activeStreamId(player).orElseThrow());
            finishReadyHandshake(nextClientCounter++);
            assertTrue(sender.isReady(player));
            knownStreamId = sender.activeStreamId(player).orElseThrow();
        }

        private void finishReadyHandshake(long readyCounter) {
            String streamId = sender.activeStreamId(player).orElseThrow();
            byte[] helloEnvelope = outbound.getLast();
            byte[] readyId = requestId();
            BridgeRequestCodec.SenderReady ready = new BridgeRequestCodec.SenderReady(
                    CLIENT_NONCE, playerId, sessionId, readyCounter, BridgeRequestCodec.Sender.NARCOTICS,
                    streamId, readyId, sha256(helloEnvelope));
            sender.onRequest(player, BridgeRequestCodec.encode(ready));
            byte[] readyEnvelope = outbound.getLast();
            BridgeRequestCodec.SenderReadyConfirm confirm = new BridgeRequestCodec.SenderReadyConfirm(
                    CLIENT_NONCE, playerId, sessionId, BridgeRequestCodec.Sender.NARCOTICS,
                    streamId, readyId, readyCounter, sha256(readyEnvelope));
            sender.onRequest(player, BridgeRequestCodec.encode(confirm));
        }

        private byte[] requestId() {
            byte[] id = new byte[BridgeControlBodyCodec.REQUEST_ID_BYTES];
            id[id.length - 1] = (byte) nextRequestId++;
            return id;
        }

        private List<NarcoticsBridgeBodyCodec.Body> visualCommands() {
            List<NarcoticsBridgeBodyCodec.Body> visuals = new ArrayList<>();
            for (byte[] envelopeBytes : outbound) {
                try {
                    NarcoticsBridgeBodyCodec.Body body = NarcoticsBridgeBodyCodec.decode(
                            BridgeEnvelopeCodec.decode(envelopeBytes).bodyBytes());
                    if (body.type() == NarcoticsBridgeBodyCodec.Type.VISUAL_START) {
                        visuals.add(body);
                    }
                } catch (RuntimeException controlEnvelope) {
                    // The outbound channel also carries stream setup packets; only visual bodies count here.
                }
            }
            return visuals;
        }
    }

    private static final class TestSigner implements BridgeSigner {
        private boolean failNextVisual;

        @Override
        public Optional<byte[]> sign(String nonce, UUID recipient, String streamId, long counter,
                                     long issuedAtMillis, long expiresAtMillis, byte[] exactBody) {
            try {
                NarcoticsBridgeBodyCodec.Body visual = NarcoticsBridgeBodyCodec.decode(exactBody);
                if (visual.type() == NarcoticsBridgeBodyCodec.Type.VISUAL_START && failNextVisual) {
                    failNextVisual = false;
                    return Optional.empty();
                }
            } catch (RuntimeException controlBody) {
                // The signer receives both setup controls and visual commands.
            }
            BridgeEnvelope.Metadata metadata = new BridgeEnvelope.Metadata(
                    BridgeSigningPolicy.PROTOCOL_VERSION, BridgeSigningPolicy.PINNED_KEY_ID,
                    nonce, recipient, streamId, counter, issuedAtMillis, expiresAtMillis);
            return Optional.of(BridgeEnvelopeCodec.encode(
                    new BridgeEnvelope(metadata, exactBody, new byte[64])));
        }
    }

    private static Player playerHandle(UUID playerUuid) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> playerUuid;
                    case "isOnline" -> true;
                    case "getName" -> "visual-reconnect-test";
                    default -> null;
                });
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
