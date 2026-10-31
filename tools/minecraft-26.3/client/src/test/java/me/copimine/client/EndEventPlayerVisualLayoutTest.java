package me.copimine.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndEventPlayerVisualLayoutTest {
    /**
     * Superseded expectation: an earlier revision drew a purple Wave 2 gaze
     * band along the screen edges. That overlay covered the combat centre and
     * was removed by "keep hunt mark glow-only"; the HUNT mark is communicated
     * through the target glow. Carrier feedback keeps its screen edges.
     */
    @Test
    void waveTwoHuntMarkIsGlowOnlyAndNeverDrawsAScreenOverlay() {
        var hunt = new EndEventPlayerVisualManager.VisualState(
                EndEventPlayerVisualManager.Kind.HUNT, 1_000L, 9_000L);
        var carrier = new EndEventPlayerVisualManager.VisualState(
                EndEventPlayerVisualManager.Kind.CARRIER, 1_000L, 9_000L);

        assertTrue(EndEventPlayerVisualLayout.edges(800, 450, hunt, 5_000L).isEmpty(),
                "the Wave 2 hunt mark must not cover the screen; only the target glow shows it");
        assertFalse(EndEventPlayerVisualLayout.edges(800, 450, carrier, 5_000L).isEmpty(),
                "carrier feedback keeps its unobstructed screen edges");
        assertTrue(EndEventPlayerVisualLayout.edges(800, 450, hunt, 9_000L).isEmpty(),
                "the hunt presentation also stays empty after the server mark expires");
    }

    @Test
    void carrierEdgesStayThinAndTranslucentAroundAnOpenCombatCentre() {
        var carrier = new EndEventPlayerVisualManager.VisualState(
                EndEventPlayerVisualManager.Kind.CARRIER, 1_000L, 9_000L);

        List<EndEventPlayerVisualLayout.Edge> edges = EndEventPlayerVisualLayout.edges(
                800, 450, carrier, 5_000L);

        assertTrue(edges.stream().allMatch(edge -> edge.alphaFrom() <= 40 && edge.alphaTo() <= 40),
                "the carrier overlay stays translucent");
        assertTrue(edges.stream().allMatch(edge -> edge.top() < 32 || edge.bottom() > 418
                        || edge.left() < 23 || edge.right() > 777),
                "the carrier overlay remains at the screen edges and leaves the combat centre clear");
    }
}