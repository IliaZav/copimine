package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndEventClientStateTest {
    @Test
    void entityBindingsStayBoundedAndUnbindReleasesCapacity() {
        EndEventClientState state = new EndEventClientState();
        for (int index = 0; index < EndEventClientState.MAX_ENTITY_BINDINGS; index++) {
            assertTrue(state.apply(bind("entity-" + index, "instance-" + index), 100L));
        }

        assertEquals(EndEventClientState.MAX_ENTITY_BINDINGS, state.eventVisualEntityIds().size());
        assertFalse(state.apply(bind("overflow", "overflow-instance"), 101L));
        assertEquals(EndEventClientState.MAX_ENTITY_BINDINGS, state.eventVisualEntityIds().size());
        assertTrue(state.tentaclePoseForEntityAt("entity-0", 101L) != null);

        assertTrue(state.apply(unbind("entity-0", "instance-0"), 102L));
        assertNull(state.tentaclePoseForEntityAt("entity-0", 102L));
        assertTrue(state.apply(bind("replacement", "replacement-instance"), 103L));
        assertEquals(EndEventClientState.MAX_ENTITY_BINDINGS, state.eventVisualEntityIds().size());
        assertTrue(state.eventVisualEntityIds().contains("replacement"));
    }

    @Test
    void missingAnimationBindingLeavesRendererReadyFallbackAvailable() {
        EndEventClientState state = new EndEventClientState();

        assertNull(state.tentaclePoseForEntityAt("unbound-entity", 1_000L));
    }

    @Test
    void unbindSuppressesMarkerFallbackUntilTheServerBindsThatEntityAgain() {
        EndEventClientState state = new EndEventClientState();

        assertTrue(state.apply(unbind("marker-only-display", "instance-1"), 100L));
        assertTrue(state.isEntityRenderSuppressed("marker-only-display"));

        assertTrue(state.apply(bind("marker-only-display", "instance-2"), 101L));
        assertFalse(state.isEntityRenderSuppressed("marker-only-display"));

        assertTrue(state.apply(unbind("marker-only-display", "instance-2"), 102L));
        assertTrue(state.isEntityRenderSuppressed("marker-only-display"));
        state.clear();
        assertFalse(state.isEntityRenderSuppressed("marker-only-display"));
    }

    @Test
    void invalidAnimationPoseRequestsKeepTheIdentityPose() {
        EndEventClientState state = new EndEventClientState();
        EndRiftTentaclePose.TentaclePose identity = EndRiftTentaclePose.TentaclePose.identity();

        assertEquals(identity, state.tentaclePoseForEntityAt(null, 1_000L));
        assertEquals(identity, state.tentaclePoseForEntityAt(" ", 1_000L));
        assertEquals(identity, state.tentaclePoseForEntityAt("entity", -1L));
    }

    private static EndEventPacket bind(String subject, String instance) {
        return new EndEventPacket("END_ENTITY_BIND", "test-event", 1L, instance,
                0L, subject, "END_RIFT_ENDERMAN_V1", "", "");
    }

    private static EndEventPacket unbind(String subject, String instance) {
        return new EndEventPacket("END_ENTITY_UNBIND", "test-event", 1L, instance,
                0L, subject, "", "", "");
    }
}
