package me.copimine.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real bridge dispatch, including cleanup outside the new manager. */
class ClientBridgeProtocolPlayerVisualTest {
    @BeforeEach
    @AfterEach
    void resetPlayerVisualTransport() {
        ClientBridgeProtocol.endEventPlayerVisuals().reset();
    }

    @Test
    void serverPlayerStateRoutesToTheDedicatedPresentationAndClearsImmediately() throws Exception {
        dispatch(playerPacket(100L, "ACTIVE"));
        assertNotNull(ClientBridgeProtocol.endEventPlayerVisuals().visualState("the_end", System.currentTimeMillis()));
        dispatch(playerPacket(200L, "CLEAR"));
        assertNull(ClientBridgeProtocol.endEventPlayerVisuals().visualState("the_end", System.currentTimeMillis()));
        dispatch(playerPacket(150L, "ACTIVE"));
        assertNull(ClientBridgeProtocol.endEventPlayerVisuals().visualState("the_end", System.currentTimeMillis()));
    }

    @Test
    void clearingOneWorldBeamDoesNotEraseTheHuntEdge() throws Exception {
        dispatch(playerPacket(100L, "ACTIVE"));
        dispatch(new BridgePayload("END_EVENT:END_WORLD_VFX_CLEAR", 2, 4L, 200L,
                "event", "one-world-beam", false, false, false, false, Set.of(), "", "RIFT_BEAM",
                1_000, 0F, 0, 0, "", "", "", "", ""));
        assertEquals(EndEventPlayerVisualManager.Kind.HUNT,
                ClientBridgeProtocol.endEventPlayerVisuals().visualState("the_end", System.currentTimeMillis()).kind());
    }

    @Test
    void deathAndWorldExitUseTheExistingCleanupRouteAndFenceQueuedActivePackets() throws Exception {
        dispatch(playerPacket(100L, "ACTIVE"));
        ClientBridgeProtocol.clearEndEventState();
        assertNull(ClientBridgeProtocol.endEventPlayerVisuals().visualState("the_end", System.currentTimeMillis()));
        dispatch(playerPacket(200L, "ACTIVE"));
        assertNull(ClientBridgeProtocol.endEventPlayerVisuals().visualState("the_end", System.currentTimeMillis()));
        assertTrue(ClientBridgeProtocol.endEventPlayerVisuals().resumeAfterLocalExit("event", 4L, 300L, "the_end"));
        dispatch(playerPacket(300L, "ACTIVE"));
        assertNotNull(ClientBridgeProtocol.endEventPlayerVisuals().visualState("the_end", System.currentTimeMillis()));
    }

    @Test
    void disconnectAndJoinResetThePresentationAndAllowTheCurrentServerSnapshot() throws Exception {
        dispatch(playerPacket(100L, "ACTIVE"));
        ClientBridgeProtocol.onDisconnect();
        assertNull(ClientBridgeProtocol.endEventPlayerVisuals().visualState("the_end", System.currentTimeMillis()));
        ClientBridgeProtocol.onJoin();
        dispatch(playerPacket(100L, "ACTIVE"));
        assertNotNull(ClientBridgeProtocol.endEventPlayerVisuals().visualState("the_end", System.currentTimeMillis()));
        ClientBridgeProtocol.onDisconnect();
    }

    private static BridgePayload playerPacket(long timestamp, String status) {
        return new BridgePayload("END_EVENT:END_PLAYER_STATE", 2, 4L, timestamp,
                "event", "wave2-hunt-mark", false, false, false, false, Set.of(), "", "WAVE2_HUNT_MARK_V1",
                11_000, .8F, 0, 0, "the_end|wave2|1", "", "", "", status);
    }

    private static void dispatch(BridgePayload payload) throws Exception {
        Method apply = ClientBridgeProtocol.class.getDeclaredMethod("applyEndEventPayload", BridgePayload.class);
        apply.setAccessible(true);
        apply.invoke(null, payload);
    }
}
