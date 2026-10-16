package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PrisonerHudLayoutTest {
    @Test
    void ordinaryScaledWindowsKeepAllFourIconsAboveVanillaHud() {
        int[][] windows = {{1920,1080}, {960,540}, {640,360}, {480,270}, {427,240},
                {320,240}, {320,180}, {240,120}};
        for (int[] window : windows) {
            var layout = PrisonerHudLayout.compute(window[0], window[1], 20, 0);
            assertTrue(layout.visible(), window[0] + "x" + window[1]);
            assertEquals(4, layout.slots().size());
            assertLayoutSafe(layout, window[0], window[1]);
            assertTrue(layout.bounds().bottom() <= layout.vanillaHud().top() - 6);
        }
    }

    @Test
    void extraHealthAndAbsorptionRowsMovePanelAboveArmorAndHeartAnimation() {
        // Five rows of hearts, spaced by seven pixels; armor is above the fifth row.
        var normal = PrisonerHudLayout.compute(480, 270, 20, 0);
        var extraHearts = PrisonerHudLayout.compute(480, 270, 80, 20);
        assertEquals(214, normal.vanillaHud().top());
        assertEquals(186, extraHearts.vanillaHud().top());
        assertTrue(extraHearts.bounds().top() < normal.bounds().top());
        assertLayoutSafe(extraHearts, 480, 270);
    }

    @Test
    void narrowWindowUsesTwoRowsWithoutShrinkingIcons() {
        var layout = PrisonerHudLayout.compute(160, 240, 20, 0);
        assertTrue(layout.visible());
        assertEquals(2, layout.columns());
        assertLayoutSafe(layout, 160, 240);
    }

    @Test
    void shortWideWindowPlacesTwoByTwoPanelBesideTallHealthHud() {
        var layout = PrisonerHudLayout.compute(600, 120, 400, 0);
        assertTrue(layout.visible());
        assertEquals(2, layout.columns());
        assertTrue(layout.bounds().right() <= layout.vanillaHud().left());
        assertLayoutSafe(layout, 600, 120);
    }

    @Test
    void impossibleViewportFailsClosedInsteadOfCoveringControls() {
        var layout = PrisonerHudLayout.compute(80, 60, 20, 0);
        assertFalse(layout.visible());
        assertTrue(layout.slots().isEmpty());
    }

    @Test
    void healthRowsRemainInsideBoundsAcrossNormalGuiScaleAndHealthFixtures() {
        int[][] windows = {{320,240}, {427,240}, {640,360}, {960,540}};
        double[][] health = {{20,0}, {40,0}, {80,20}, {200,40}, {400,0}};
        for (int[] window : windows) {
            for (double[] fixture : health) {
                var layout = PrisonerHudLayout.compute(window[0], window[1], fixture[0], fixture[1]);
                assertTrue(layout.visible());
                assertLayoutSafe(layout, window[0], window[1]);
            }
        }
    }

    private static void assertLayoutSafe(PrisonerHudLayout.Layout layout, int width, int height) {
        assertTrue(layout.bounds().left() >= 0);
        assertTrue(layout.bounds().top() >= 0);
        assertTrue(layout.bounds().right() <= width);
        assertTrue(layout.bounds().bottom() <= height);
        assertFalse(layout.bounds().intersects(layout.vanillaHud()));
        for (var slot : layout.slots()) {
            assertEquals(40, slot.right() - slot.left());
            assertEquals(44, slot.bottom() - slot.top());
            assertTrue(slot.left() >= layout.bounds().left() + 3);
            assertTrue(slot.top() >= layout.bounds().top() + 3);
            assertTrue(slot.right() <= layout.bounds().right() - 3);
            assertTrue(slot.bottom() <= layout.bounds().bottom() - 3);
            assertFalse(slot.intersects(layout.vanillaHud()));
        }
        for (int i = 0; i < layout.slots().size(); i++) {
            for (int j = i + 1; j < layout.slots().size(); j++) {
                assertFalse(layout.slots().get(i).intersects(layout.slots().get(j)));
            }
        }
    }
}
