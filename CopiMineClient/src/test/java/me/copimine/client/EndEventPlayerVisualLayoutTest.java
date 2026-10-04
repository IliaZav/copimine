package me.copimine.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EndEventPlayerVisualLayoutTest {
    @Test
    void theRenderedFootprintLeavesTheCombatCenterClearAtSeveralGuiScales() {
        var state = new EndEventPlayerVisualManager.VisualState(EndEventPlayerVisualManager.Kind.CARRIER, 1_000, 16_000);
        for (int[] size : new int[][]{{1_920, 1_080}, {640, 360}, {427, 240}, {320, 180}, {160, 90}}) {
            int width = size[0], height = size[1];
            var edges = EndEventPlayerVisualLayout.edges(width, height, state, 2_000);
            assertFalse(edges.isEmpty());
            assertTrue(edges.size() <= 42, "draw calls remain bounded at all GUI scales");
            for (var edge : edges) {
                assertTrue(edge.left() >= 0 && edge.top() >= 0 && edge.right() <= width && edge.bottom() <= height);
                assertTrue(edge.left() < edge.right() && edge.top() < edge.bottom());
                assertTrue(edge.right() <= height / 20 || edge.left() >= width - height / 20
                        || edge.bottom() <= height / 20 || edge.top() >= height - height / 20,
                        "every pixel belongs to the outer five percent of screen height");
                assertFalse(edge.left() <= width / 2 && edge.right() > width / 2
                        && edge.top() <= height / 2 && edge.bottom() > height / 2);
                assertTrue(edge.alphaFrom() <= 40 && edge.alphaTo() <= 40);
            }
        }
    }

    @Test
    void theHuntRevealSettlesToALowerPersistentEdgeWithoutContinuousPulsing() {
        var hunt = new EndEventPlayerVisualManager.VisualState(EndEventPlayerVisualManager.Kind.HUNT, 1_000, 12_000);
        List<EndEventPlayerVisualLayout.Edge> early = EndEventPlayerVisualLayout.edges(640, 360, hunt, 1_200);
        List<EndEventPlayerVisualLayout.Edge> persistent = EndEventPlayerVisualLayout.edges(640, 360, hunt, 2_000);
        assertFalse(early.isEmpty());
        assertFalse(persistent.isEmpty());
        assertTrue(early.getFirst().alphaFrom() > persistent.getFirst().alphaFrom());
        assertEquals(persistent, EndEventPlayerVisualLayout.edges(640, 360, hunt, 11_999));
        assertEquals(0xB387ED, persistent.getFirst().colorFrom() & 0xFFFFFF);
    }

    @Test
    void noPixelsAreDrawnForExpiredMissingOrInvalidPresentation() {
        var state = new EndEventPlayerVisualManager.VisualState(EndEventPlayerVisualManager.Kind.CARRIER, 1_000, 2_500);
        assertTrue(EndEventPlayerVisualLayout.edges(640, 360, null, 1_200).isEmpty());
        assertTrue(EndEventPlayerVisualLayout.edges(640, 360, state, 999).isEmpty());
        assertTrue(EndEventPlayerVisualLayout.edges(640, 360, state, 2_500).isEmpty());
        assertTrue(EndEventPlayerVisualLayout.edges(0, 360, state, 1_200).isEmpty());
        assertTrue(EndEventPlayerVisualLayout.edges(640, 0, state, 1_200).isEmpty());
    }

    @Test
    void sidesSoftenInwardAndCarrierFeedbackStaysCalm() {
        var state = new EndEventPlayerVisualManager.VisualState(EndEventPlayerVisualManager.Kind.CARRIER, 1_000, 16_000);
        var edges = EndEventPlayerVisualLayout.edges(640, 360, state, 1_500);
        assertFalse(edges.isEmpty());
        assertEquals(0x74E6E8, edges.getFirst().colorFrom() & 0xFFFFFF);
        assertEquals(0, edges.getFirst().alphaTo());
        assertTrue(edges.get(2).alphaFrom() > edges.get(edges.size() - 2).alphaFrom());
        assertEquals(edges, EndEventPlayerVisualLayout.edges(640, 360, state, 15_999));
    }
}
