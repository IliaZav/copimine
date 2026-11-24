package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The emissive tone map is the difference between a flat recoloured ribbon and
 * the authored hot-core / deep-edge energy the V5.1 art direction requires. It
 * must stay pure, bounded and monotonic for every server-authored spell colour.
 */
class SpellVisualToneMapTest {

    private static final int[] PALETTE = {
            0x8A5CFF, // fang line, violet
            0xB070FF, // circle seal, light violet
            0x7FE8FF, // separation, cyan
            0x62FFE0, // echo sphere, teal
            0x4FD9A8, // sculk trace, green
            0xFFB44A, // heavy slam, amber
            0xFF8A2B, // geyser, orange
            0xFFFFFF, // pure white must stay white
            0x000000, // pure black must stay black
    };

    @Test
    void toneStaysInsideTheTwentyFourBitSpaceAndIsDeterministic() {
        for (int rgb : PALETTE) {
            for (int step = 0; step <= 100; step++) {
                float brightness = step / 100.0F;
                int first = SpellVisualToneMap.tone(rgb, brightness);
                int second = SpellVisualToneMap.tone(rgb, brightness);
                assertEquals(first, second, "the same cast instant must rebuild the same colour");
                assertEquals(0, first & 0xFF000000, "the tone map never invents an alpha channel");
                assertTrue(first >= 0 && first <= 0xFFFFFF);
            }
        }
    }

    @Test
    void risingBrightnessNeverDarkensAChannel() {
        for (int rgb : PALETTE) {
            int previous = SpellVisualToneMap.tone(rgb, 0.0F);
            for (int step = 1; step <= 100; step++) {
                int current = SpellVisualToneMap.tone(rgb, step / 100.0F);
                assertChannelAtLeast(current, previous, "brightness " + step + "% for " + hex(rgb));
                previous = current;
            }
        }
    }

    @Test
    void theHotBandBurnsTowardWhite() {
        for (int rgb : PALETTE) {
            int mid = SpellVisualToneMap.tone(rgb, SpellVisualToneMap.HOT_START);
            int peak = SpellVisualToneMap.tone(rgb, 1.0F);
            assertTrue(luminance(peak) >= luminance(mid),
                    "the peak must not be dimmer than the start of the hot band for " + hex(rgb));
            assertTrue(distanceToWhite(peak) <= distanceToWhite(mid),
                    "the peak must sit closer to white-hot for " + hex(rgb));
        }
        assertTrue(SpellVisualToneMap.isHot(1.0F));
        assertTrue(SpellVisualToneMap.isHot(SpellVisualToneMap.HOT_START));
        assertFalse(SpellVisualToneMap.isHot(SpellVisualToneMap.HOT_START - 0.01F));
    }

    @Test
    void theColdEndKeepsItsHueFamily() {
        for (int rgb : PALETTE) {
            int deep = SpellVisualToneMap.tone(rgb, 0.0F);
            int mid = SpellVisualToneMap.tone(rgb, 0.5F);
            assertChannelAtMost(deep, mid, "a dim stroke must never outshine its own mid-tone for " + hex(rgb));
            assertEquals(dominantChannel(rgb), dominantChannel(deep),
                    "the deep exterior must keep the spell's hue, not wash it out for " + hex(rgb));
        }
    }

    @Test
    void nonFiniteBrightnessFailsClosedToTheDeepEnd() {
        int deep = SpellVisualToneMap.tone(0xFF8A2B, 0.0F);
        assertEquals(deep, SpellVisualToneMap.tone(0xFF8A2B, Float.NaN));
        assertEquals(deep, SpellVisualToneMap.tone(0xFF8A2B, -4.0F));
        assertEquals(SpellVisualToneMap.tone(0xFF8A2B, 1.0F),
                SpellVisualToneMap.tone(0xFF8A2B, 40.0F));
    }

    @Test
    void alphaBitsInTheServerColourAreIgnored() {
        int clean = SpellVisualToneMap.tone(0x00FF8A2B, 0.8F);
        int withAlpha = SpellVisualToneMap.tone(0xFFFF8A2B, 0.8F);
        assertEquals(clean, withAlpha);
    }

    private static void assertChannelAtLeast(int actual, int floor, String message) {
        assertTrue(red(actual) >= red(floor) && green(actual) >= green(floor) && blue(actual) >= blue(floor),
                message + ": " + hex(actual) + " < " + hex(floor));
    }

    private static void assertChannelAtMost(int actual, int ceiling, String message) {
        assertTrue(red(actual) <= red(ceiling) && green(actual) <= green(ceiling)
                        && blue(actual) <= blue(ceiling),
                message + ": " + hex(actual) + " > " + hex(ceiling));
    }

    private static int dominantChannel(int rgb) {
        int red = red(rgb);
        int green = green(rgb);
        int blue = blue(rgb);
        if (red >= green && red >= blue) {
            return 0;
        }
        return green >= blue ? 1 : 2;
    }

    private static int red(int rgb) {
        return (rgb >> 16) & 0xFF;
    }

    private static int green(int rgb) {
        return (rgb >> 8) & 0xFF;
    }

    private static int blue(int rgb) {
        return rgb & 0xFF;
    }

    private static int luminance(int rgb) {
        return red(rgb) * 299 + green(rgb) * 587 + blue(rgb) * 114;
    }

    private static int distanceToWhite(int rgb) {
        return (255 - red(rgb)) + (255 - green(rgb)) + (255 - blue(rgb));
    }

    private static String hex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }
}
