package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabPanelLayoutTest {
    @Test
    void pingBarsFollowVanillaThresholds() {
        assertEquals(5, TabPanelLayout.pingBars(0));
        assertEquals(5, TabPanelLayout.pingBars(149));
        assertEquals(4, TabPanelLayout.pingBars(150));
        assertEquals(4, TabPanelLayout.pingBars(299));
        assertEquals(3, TabPanelLayout.pingBars(300));
        assertEquals(3, TabPanelLayout.pingBars(599));
        assertEquals(2, TabPanelLayout.pingBars(600));
        assertEquals(2, TabPanelLayout.pingBars(999));
        assertEquals(1, TabPanelLayout.pingBars(1000));
        assertEquals(1, TabPanelLayout.pingBars(2000));
    }

    @Test
    void pingBarsOfUnknownLatencyIsZero() {
        // Vanilla draws PING_UNKNOWN_SPRITE for latency < 0; our bars must not lit up.
        assertEquals(0, TabPanelLayout.pingBars(-1));
    }

    @Test
    void lineHeightFollowsLogoGlyph() {
        assertEquals(TabPanelLayout.LINE_HEIGHT, TabPanelLayout.lineHeight("Президент: не выбран"));
        assertEquals(TabPanelLayout.LOGO_LINE_HEIGHT, TabPanelLayout.lineHeight("\uE000"));
        assertEquals(TabPanelLayout.LARGE_LOGO_LINE_HEIGHT, TabPanelLayout.lineHeight("a\uE300b"));
    }

    @Test
    void highAscentLogoIsShiftedIntoItsReservedLineBox() {
        assertEquals(0, TabPanelLayout.textYOffset("Президент: не выбран"));
        assertEquals(16, TabPanelLayout.textYOffset("\uE000"));
        assertEquals(16, TabPanelLayout.textYOffset("\uE300"));
    }

    @Test
    void blankLinesArePreservedAsLayoutSpacing() {
        assertTrue(TabPanelLayout.isDrawnLine(""));
        assertTrue(TabPanelLayout.isDrawnLine("   "));
        assertTrue(TabPanelLayout.isDrawnLine("\u2501\u2501\u2501"));
    }
}
