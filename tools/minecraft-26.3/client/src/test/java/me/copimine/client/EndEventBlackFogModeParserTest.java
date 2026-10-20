package me.copimine.client;

import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndEventBlackFogModeParserTest {
    @Test
    void acceptsTheLongestSupportedFogStateMode() {
        assertArrayEquals(new String[]{"the_nether", "wave5", "3"},
                EndEventBlackFogManager.parseMode("the_nether|wave5|3"));
    }

    @Test
    void acceptsOnlyTheOneBasedThreeCycleRangeInTheRuntimeManager() {
        EndEventBlackFogManager manager = new EndEventBlackFogManager();

        assertFalse(manager.apply(packet(1L, 1L, "the_end|wave5|0"), 1L));
        assertTrue(manager.apply(packet(2L, 2L, "the_end|wave5|1"), 2L));
        assertTrue(manager.apply(packet(3L, 3L, "the_end|wave5|3"), 3L));
        assertFalse(manager.apply(packet(4L, 4L, "the_end|wave5|4"), 4L));
    }

    @Test
    void rejectsOversizedModeBeforeSplitting() {
        String oversized = ("x|".repeat(32_768)) + "x";

        assertNull(EndEventBlackFogManager.parseMode(oversized));
    }

    @Test
    void rejectsNullMode() {
        assertNull(EndEventBlackFogManager.parseMode(null));
    }

    private static BridgePayload packet(long sequence, long timestamp, String mode) {
        return new BridgePayload("END_EVENT:END_FOG_STATE", ClientBridgeProtocol.PROTOCOL_VERSION,
                sequence, timestamp, "event-1", "wave5-black-fog", false, false, false, false,
                Set.of(), "", "WAVE5_FOG_V1", 8_000, 0.8F, 0, 0, mode, "", "", "", "ACTIVE");
    }
}
