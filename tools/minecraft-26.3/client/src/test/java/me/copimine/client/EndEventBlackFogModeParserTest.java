package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EndEventBlackFogModeParserTest {
    @Test
    void acceptsTheLongestSupportedFogStateMode() {
        assertArrayEquals(new String[]{"the_nether", "wave5", "2"},
                EndEventBlackFogManager.parseMode("the_nether|wave5|2"));
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
}
