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
    void acceptsUpToTenCyclesAndRefusesOutOfRange() {
        EndEventBlackFogManager manager = new EndEventBlackFogManager();
        assertFalse(manager.apply(packet(1L, 1L, "the_end|wave5|0"), 1L));
        for (int cycle = 1; cycle <= 10; cycle++) {
            assertTrue(manager.apply(packet(cycle + 1L, cycle + 1L,
                    "the_end|wave5|" + cycle), cycle + 1L));
        }
        assertFalse(manager.apply(packet(12L, 12L, "the_end|wave5|11"), 12L));
    }

    @Test
    void rejectsNonNumericCycleWithoutThrowing() {
        EndEventBlackFogManager manager = new EndEventBlackFogManager();

        assertFalse(manager.apply(packet(1L, 1L, "the_end|wave5|abc"), 1L));
        assertFalse(manager.apply(packet(2L, 2L, "the_end|wave5||"), 2L));
        assertFalse(manager.apply(packet(3L, 3L, "the_end|wave5| 1"), 3L));
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
