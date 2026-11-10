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

    @Test
    void playerDetailsStayCenteredAsOneGroup() {
        assertEquals(212, TabPanelLayout.playerRowLeft(45, 544, 106, 80));
    }

    @Test
    void panelHangsFromTheTopInsteadOfTheVerticalCentre() {
        // A centred plate drifts down as the roster grows; the reference layout does not move.
        assertEquals(6, TabPanelLayout.panelTop());
        assertTrue(TabPanelLayout.panelTop() < 20);
    }

    @Test
    void panelColoursAreNeutralDarkNotGreen() {
        int background = TabPanelLayout.PANEL_BACKGROUND_COLOR;
        int border = TabPanelLayout.PANEL_BORDER_COLOR;

        // Alpha must keep the plate readable while staying translucent, and green must not
        // dominate either channel.
        assertEquals(0x8C, (background >>> 24) & 0xFF);
        assertTrue(green(background) <= red(background) + 4);
        assertTrue(green(background) <= blue(background) + 4);
        assertTrue(green(border) <= red(border) + 8);
        assertTrue(green(border) <= blue(border) + 8);
        assertTrue(luminance(background) < 0x40);
    }

    @Test
    void longNamesAreClampedInsideTheBorder() {
        // Screen-capped plate: the row must stay inside it instead of pushing the ping off-panel.
        int panelWidth = 320;
        int pingWidth = 60;

        int budget = TabPanelLayout.playerNameWidth(panelWidth, pingWidth);

        assertTrue(budget > 0);
        assertTrue(budget + TabPanelLayout.NAME_GAP + pingWidth + TabPanelLayout.BARS_GAP
                + TabPanelLayout.BAR_AREA_WIDTH < panelWidth);
        assertEquals(1, TabPanelLayout.playerNameWidth(40, 60));
    }

    @Test
    void truncationMarkerComesOutOfTheNameBudget() {
        assertEquals(40, TabPanelLayout.clippedNameBudget(46, 6));
        assertEquals(1, TabPanelLayout.clippedNameBudget(4, 6));
        assertEquals(46, TabPanelLayout.clippedNameBudget(46, 0));
        assertEquals(46, TabPanelLayout.clippedNameBudget(46, -3));
    }

    @Test
    void rosterIsClippedToTheRowsThatFitOnScreen() {
        // 6px margin, a 30px logo line and a 9px footer line fit 20 rows at 1080p GUI scale 3
        // but must clip at a small window instead of drawing past the bottom edge.
        assertEquals(20, TabPanelLayout.visiblePlayerRows(365, 39, 13, 20));
        assertEquals(5, TabPanelLayout.visiblePlayerRows(365, 39, 13, 5));
        assertTrue(TabPanelLayout.visiblePlayerRows(90, 39, 13, 20) < 20);
        assertEquals(0, TabPanelLayout.visiblePlayerRows(90, 39, 13, 0));
    }

    private static int red(int color) {
        return (color >>> 16) & 0xFF;
    }

    private static int green(int color) {
        return (color >>> 8) & 0xFF;
    }

    private static int blue(int color) {
        return color & 0xFF;
    }

    private static int luminance(int color) {
        return (red(color) + green(color) + blue(color)) / 3;
    }
}
