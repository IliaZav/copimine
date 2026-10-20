package me.copimine.client;

import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndEventBlackFogDensityTest {
    @Test
    void activeWaveFiveFogKeepsVisibilityWithinTheArenaAsIntensityChanges() {
        EndEventBlackFogManager manager = new EndEventBlackFogManager();

        assertTrue(manager.apply(packet(1L, 1_000L, 0.5F), 1_000L));
        assertEquals(6.0F, manager.fogEndBlocks("the_end", 1_100L), 0.001F,
                "the least intense supported fog should still be dense");

        assertTrue(manager.apply(packet(2L, 2_000L, 0.88F), 2_000L));
        assertEquals(4.48F, manager.fogEndBlocks("the_end", 2_100L), 0.001F,
                "the normal server intensity should obscure most of the arena");

        assertTrue(manager.apply(packet(3L, 3_000L, 1.0F), 3_000L));
        assertEquals(4.0F, manager.fogEndBlocks("the_end", 3_100L), 0.001F,
                "maximum intensity should retain a short, positive view distance");
    }

    private static BridgePayload packet(long sequence, long timestamp, float intensity) {
        return new BridgePayload("END_EVENT:END_FOG_STATE", ClientBridgeProtocol.PROTOCOL_VERSION,
                sequence, timestamp, "event-1", "wave5-black-fog", false, false, false, false,
                Set.of(), "", "WAVE5_FOG_V1", 8_000, intensity, 0, 0,
                "the_end|wave5|1", "", "", "", "ACTIVE");
    }
}
