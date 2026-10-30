package me.copimine.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndEventPlayerVisualLayoutTest {
    @Test
    void waveTwoHuntEdgeRendersAsShortHudOverlayWithoutCoveringTheCenter() {
        var hunt = new EndEventPlayerVisualManager.VisualState(
                EndEventPlayerVisualManager.Kind.HUNT, 1_000L, 9_000L);

        List<EndEventPlayerVisualLayout.Edge> edges = EndEventPlayerVisualLayout.edges(
                800, 450, hunt, 5_000L);

        assertFalse(edges.isEmpty(), "the Wave 2 Enderman gaze must be visible while its mark is active");
        assertTrue(edges.stream().allMatch(edge -> edge.alphaFrom() <= 40 && edge.alphaTo() <= 40),
                "the gaze overlay stays translucent");
        assertTrue(edges.stream().allMatch(edge -> edge.top() < 32 || edge.bottom() > 418
                        || edge.left() < 23 || edge.right() > 777),
                "the gaze overlay remains at the screen edges and leaves the combat center clear");
        assertTrue(EndEventPlayerVisualLayout.edges(800, 450, hunt, 9_000L).isEmpty(),
                "the gaze overlay clears when the server mark expires");
    }
}
