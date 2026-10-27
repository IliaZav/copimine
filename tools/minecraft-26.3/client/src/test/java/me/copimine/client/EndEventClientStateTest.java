package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndEventClientStateTest {
    @Test
    void smallKaguneServerScaleReachesTheRenderedPoseAcrossAnimationChanges() {
        EndEventClientState state = new EndEventClientState();
        assertTrue(state.apply(tentacleBind("small", "small-instance"), 100L));
        assertTrue(state.apply(tentaclePhase("small", "small-instance",
                "EMERGING|t=5|role=SMALL|scale=0.5"), 101L));
        assertEquals(0.5F, state.tentaclePoseForEntityAt("small", 200L).rigScale(), 0.0001F);
        assertEquals(0.31F, EndRiftTentacleWorldScalePolicy.rendererLengthScaleForRig(
                state.tentaclePoseForEntityAt("small", 200L).rigScale()), 0.0001F);
        assertTrue(state.apply(tentaclePhase("small", "small-instance",
                "TELEGRAPH_GRAB|t=25|role=SMALL|scale=0.5"), 300L));
        for (long time : new long[]{300L, 350L, 420L, 500L}) {
            assertEquals(0.5F, state.tentaclePoseForEntityAt("small", time).rigScale(), 0.0001F,
                    "clip blending must not expand a small actor into a full guardian");
        }
    }

    @Test
    void guardianAmbushAndLegacyPacketsKeepFullScaleAndMalformedScaleIsSafe() {
        for (String cue : new String[]{"READY", "READY|role=PERMANENT|scale=0.5",
                "READY|role=UNDER_PLAYER|scale=0.5", "READY|role=SMALL|scale=NaN",
                "READY|role=SMALL|scale=Infinity", "READY|role=SMALL|scale=0",
                "READY|role=SMALL|scale=5", "READY|role=SMALL|scale=-1"}) {
            EndEventClientState state = new EndEventClientState();
            assertTrue(state.apply(tentacleBind("actor", "instance"), 100L));
            assertTrue(state.apply(tentaclePhase("actor", "instance", cue), 101L));
            assertEquals(1.0F, state.tentaclePoseForEntityAt("actor", 200L).rigScale(), 0.0001F, cue);
        }
    }

    private static EndEventPacket tentacleBind(String subject, String instance) {
        return new EndEventPacket("END_ENTITY_BIND", "test-event", 1L, instance,
                0L, subject, EndRiftTentacleModel.VISUAL_ID, "", "");
    }

    private static EndEventPacket tentaclePhase(String subject, String instance, String cue) {
        return new EndEventPacket("END_ENTITY_PHASE", "test-event", 1L, instance,
                1000L, subject, "", cue, "");
    }

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

    @Test
    void reverseMarkExpiresAtItsServerDeadline() {
        EndEventClientState state = new EndEventClientState();
        assertTrue(state.apply(reverseControl("event-1", 1L, "control-1", 100L), 1_000L));

        assertTrue(state.isReverseActive(1_099L));
        assertFalse(state.isReverseActive(1_100L));
        assertEquals("NONE", state.controlMode());
    }

    @Test
    void eventSwitchAndConnectionResetClearThePreviousReverseMark() {
        EndEventClientState state = new EndEventClientState();
        assertTrue(state.apply(reverseControl("event-1", 1L, "control-1", 10_000L), 1_000L));
        assertTrue(state.isReverseActive(1_001L));

        assertFalse(state.apply(new EndEventPacket("END_WORLD_VFX_CLEAR", "event-2", 2L,
                "", 0L, "", "", "", ""), 1_002L));
        assertFalse(state.isReverseActive(1_003L));

        assertTrue(state.apply(reverseControl("event-2", 2L, "control-2", 10_000L), 1_004L));
        state.clear();
        assertFalse(state.isReverseActive(1_005L));
        assertEquals("NONE", state.controlMode());
    }

    private static EndEventPacket reverseControl(String eventId, long generation,
                                                 String instanceId, long durationMillis) {
        return new EndEventPacket("END_CONTROL_START", eventId, generation, instanceId,
                durationMillis, "", "", "", "");
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
