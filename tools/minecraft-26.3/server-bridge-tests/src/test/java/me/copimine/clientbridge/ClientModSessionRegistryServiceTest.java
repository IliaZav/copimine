package me.copimine.clientbridge;

import me.copimine.protocol.ClientModHandshakePolicy;
import me.copimine.protocol.ClientModSessionRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientModSessionRegistryServiceTest {
    private static final long NOW = 1_800_000_000_000L;
    private static final long EXPIRY = NOW + 10_000L;
    private static final String VERSION = "0.1.2+26.3";

    @Test
    void publishesOnlyAnExactVerifiedV3ChallengeResponseForTheCurrentConnection() {
        ClientModSessionRegistryService registry = new ClientModSessionRegistryService(message -> { });
        UUID player = UUID.randomUUID();
        UUID connectionEpoch = registry.openConnection(player);
        UUID challenge = UUID.randomUUID();
        registry.beginChallenge(player, challenge, EXPIRY, VERSION);

        assertFalse(registry.acceptChallengeResponse(
                player, challenge.toString(), null, EXPIRY, NOW,
                ClientModHandshakePolicy.MOD_ID, VERSION, ClientModHandshakePolicy.PROTOCOL_VERSION));
        assertFalse(registry.acceptChallengeResponse(
                player, challenge.toString(), "not-a-uuid", EXPIRY, NOW,
                ClientModHandshakePolicy.MOD_ID, VERSION, ClientModHandshakePolicy.PROTOCOL_VERSION));
        assertTrue(registry.current(player).isEmpty());
        assertFalse(accept(registry, player, challenge, "0.1.1", NOW));
        assertTrue(registry.current(player).isEmpty());

        UUID replacementChallenge = UUID.randomUUID();
        registry.beginChallenge(player, replacementChallenge, EXPIRY, VERSION);
        assertTrue(accept(registry, player, replacementChallenge, VERSION, NOW));
        ClientModSessionRegistry.VerifiedClientSession current = registry.current(player).orElseThrow();
        assertEquals(player, current.playerUuid());
        assertEquals(replacementChallenge, current.sessionUuid());
        assertEquals(VERSION, current.clientVersion());
        assertEquals(connectionEpoch, current.connectionEpoch());

        assertFalse(accept(registry, player, challenge, VERSION, NOW));
        assertEquals(current, registry.current(player).orElseThrow());
    }

    @Test
    void reconnectAllocatesANewEpochAndMakesOldChallengeAndSessionStale() {
        ClientModSessionRegistryService registry = new ClientModSessionRegistryService(message -> { });
        UUID player = UUID.randomUUID();
        UUID firstEpoch = registry.openConnection(player);
        UUID challenge = UUID.randomUUID();
        registry.beginChallenge(player, challenge, EXPIRY, VERSION);
        assertTrue(accept(registry, player, challenge, VERSION, NOW));

        UUID secondEpoch = registry.openConnection(player);

        assertNotEquals(firstEpoch, secondEpoch);
        assertTrue(registry.current(player).isEmpty());
        assertFalse(accept(registry, player, challenge, VERSION, NOW));
        assertEquals(Optional.of(secondEpoch), registry.connectionEpoch(player));
    }

    @Test
    void freshChallengeNotifiesEverySenderAfterClearingAndBeforeDispatchEvenIfOneListenerThrows() {
        List<String> order = new ArrayList<>();
        AtomicReference<ClientModSessionRegistryService> registryRef = new AtomicReference<>();
        ClientModSessionRegistryService registry = new ClientModSessionRegistryService(message -> order.add("log"));
        registryRef.set(registry);
        UUID player = UUID.randomUUID();
        UUID connectionEpoch = registry.openConnection(player);
        UUID challenge = UUID.randomUUID();
        registry.beginChallenge(player, challenge, EXPIRY, VERSION);
        assertTrue(accept(registry, player, challenge, VERSION, NOW));

        registry.addInvalidationListener((playerUuid, reason) -> {
            assertEquals(player, playerUuid);
            assertTrue(registryRef.get().current(playerUuid).isEmpty());
            order.add("narcotics");
            throw new IllegalStateException("test listener failure");
        });
        registry.addInvalidationListener((playerUuid, reason) -> {
            assertEquals(ClientModSessionRegistry.InvalidationReason.FRESH_CHALLENGE, reason);
            assertTrue(registryRef.get().current(playerUuid).isEmpty());
            order.add("end-event");
        });
        registry.setFreshChallengeRequester(playerUuid -> {
            assertTrue(registryRef.get().current(playerUuid).isEmpty());
            order.add("challenge");
        });

        registry.requestFreshChallenge(player);

        assertEquals(List.of("narcotics", "log", "end-event", "challenge"), order);
        assertTrue(registry.current(player).isEmpty());
        assertEquals(Optional.of(connectionEpoch), registry.connectionEpoch(player));
    }

    @Test
    void quitVersionChangeRejectionAndShutdownInvalidateCurrentSessionAndNotifyListeners() {
        ClientModSessionRegistryService registry = new ClientModSessionRegistryService(message -> { });
        UUID player = UUID.randomUUID();
        List<ClientModSessionRegistry.InvalidationReason> reasons = new ArrayList<>();
        registry.addInvalidationListener((ignored, reason) -> reasons.add(reason));

        registry.openConnection(player);
        UUID challenge = UUID.randomUUID();
        registry.beginChallenge(player, challenge, EXPIRY, VERSION);
        assertTrue(accept(registry, player, challenge, VERSION, NOW));
        registry.invalidate(player, ClientModSessionRegistry.InvalidationReason.VERSION_CHANGED);
        assertTrue(registry.current(player).isEmpty());

        UUID secondChallenge = UUID.randomUUID();
        registry.beginChallenge(player, secondChallenge, EXPIRY, VERSION);
        assertTrue(accept(registry, player, secondChallenge, VERSION, NOW));
        registry.invalidate(player, ClientModSessionRegistry.InvalidationReason.REJECTED);

        UUID thirdChallenge = UUID.randomUUID();
        registry.beginChallenge(player, thirdChallenge, EXPIRY, VERSION);
        assertTrue(accept(registry, player, thirdChallenge, VERSION, NOW));
        registry.closeConnection(player);
        assertTrue(registry.connectionEpoch(player).isEmpty());

        UUID anotherPlayer = UUID.randomUUID();
        registry.openConnection(anotherPlayer);
        registry.beginChallenge(anotherPlayer, UUID.randomUUID(), EXPIRY, VERSION);
        registry.shutdown();

        assertTrue(registry.current(player).isEmpty());
        assertTrue(registry.current(anotherPlayer).isEmpty());
        assertTrue(reasons.contains(ClientModSessionRegistry.InvalidationReason.VERSION_CHANGED));
        assertTrue(reasons.contains(ClientModSessionRegistry.InvalidationReason.REJECTED));
        assertTrue(reasons.contains(ClientModSessionRegistry.InvalidationReason.QUIT));
        assertTrue(reasons.contains(ClientModSessionRegistry.InvalidationReason.SHUTDOWN));
    }

    private static boolean accept(ClientModSessionRegistryService registry, UUID player,
                                  UUID challenge, String version, long now) {
        return registry.acceptChallengeResponse(player, challenge.toString(), challenge.toString(),
                EXPIRY, now, ClientModHandshakePolicy.MOD_ID, version,
                ClientModHandshakePolicy.PROTOCOL_VERSION);
    }
}
