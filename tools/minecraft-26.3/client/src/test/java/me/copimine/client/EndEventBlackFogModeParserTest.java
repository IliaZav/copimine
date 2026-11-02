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
        assertArrayEquals(new String[]{"the_nether", "wave5", "5"},
                EndEventBlackFogManager.parseMode("the_nether|wave5|5"));
    }

    @Test
    void acceptsTheOneBasedFiveCycleRangeInTheRuntimeManager() {
        EndEventBlackFogManager manager = new EndEventBlackFogManager();

        // Wave 5 runs exactly five cycles server-side, so the client must accept
        // every one of them; rejecting four and five hid the hazard entirely.
        assertFalse(manager.apply(packet(1L, 1L, "the_end|wave5|0"), 1L));
        assertTrue(manager.apply(packet(2L, 2L, "the_end|wave5|1"), 2L));
        assertTrue(manager.apply(packet(3L, 3L, "the_end|wave5|2"), 3L));
        assertTrue(manager.apply(packet(4L, 4L, "the_end|wave5|3"), 4L));
        assertTrue(manager.apply(packet(5L, 5L, "the_end|wave5|4"), 5L));
        assertTrue(manager.apply(packet(6L, 6L, "the_end|wave5|5"), 6L));
        assertFalse(manager.apply(packet(7L, 7L, "the_end|wave5|6"), 7L),
                "a sixth cycle is not part of the five-cycle wave");
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
