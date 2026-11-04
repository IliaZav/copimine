package me.copimine.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndEventPlayerVisualLayoutTest {
    /**
     * The player reported "no Enderman shader on me" during Wave 2. The mark used
     * to be glow-only and drew nothing at all, so the hunt was completely
     * invisible. It now paints a violet Enderman pulse at the screen edges; the
     * combat centre must still stay clear and the mark must still end with its
     * server lifetime.
     */
    @Test
    void waveTwoHuntMarkShowsAnEndermanTintAtTheScreenEdgesOnly() {
        var hunt = new EndEventPlayerVisualManager.VisualState(
                EndEventPlayerVisualManager.Kind.HUNT, 1_000L, 9_000L);
        var carrier = new EndEventPlayerVisualManager.VisualState(
                EndEventPlayerVisualManager.Kind.CARRIER, 1_000L, 9_000L);

        List<EndEventPlayerVisualLayout.Edge> huntEdges = EndEventPlayerVisualLayout.edges(800, 450, hunt, 5_000L);
        assertFalse(huntEdges.isEmpty(),
                "the Wave 2 hunt mark must be visible; glow alone left the player with no shader");
        assertTrue(huntEdges.stream().allMatch(edge -> edge.top() < 32 || edge.bottom() > 418
                        || edge.left() < 23 || edge.right() > 777),
                "the hunt tint stays at the screen edges and leaves the combat centre clear");
        assertTrue(EndEventPlayerVisualLayout.edges(800, 450, carrier, 5_000L).size() > 0,
                "carrier feedback keeps its unobstructed screen edges");
        assertTrue(EndEventPlayerVisualLayout.edges(800, 450, hunt, 9_000L).isEmpty(),
                "the hunt presentation ends with the server mark");
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