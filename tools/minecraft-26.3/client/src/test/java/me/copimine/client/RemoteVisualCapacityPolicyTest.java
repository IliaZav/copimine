package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteVisualCapacityPolicyTest {
    @Test
    void keepsStackedRemoteOverlaysWithinTheRenderBudget() {
        assertEquals(4, RemoteVisualCapacityPolicy.MAX_ACTIVE_VISUALS);
    }

    @Test
    void allowsNewSequencesOnlyBelowTheBound() {
        assertTrue(RemoteVisualCapacityPolicy.accepts(0, false));
        assertTrue(RemoteVisualCapacityPolicy.accepts(RemoteVisualCapacityPolicy.MAX_ACTIVE_VISUALS - 1, false));
        assertFalse(RemoteVisualCapacityPolicy.accepts(RemoteVisualCapacityPolicy.MAX_ACTIVE_VISUALS, false));
        assertFalse(RemoteVisualCapacityPolicy.accepts(RemoteVisualCapacityPolicy.MAX_ACTIVE_VISUALS + 1, false));
    }

    @Test
    void permitsUpdatingAnExistingSequenceAtCapacity() {
        assertTrue(RemoteVisualCapacityPolicy.accepts(RemoteVisualCapacityPolicy.MAX_ACTIVE_VISUALS, true));
    }
}
