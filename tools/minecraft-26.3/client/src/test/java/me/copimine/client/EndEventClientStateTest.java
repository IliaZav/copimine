package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndEventClientStateTest {
    @Test
    void cocoonSnapshotsResumeTheStageInsteadOfReplayingItOnHeartbeatOrRejoin() {
        var state = new EndEventClientState();
        assertTrue(state.apply(new EndEventPacket("END_BOSS_BIND", "event", 1, "instance",
                0, "boss", "END_RIFT_GUARDIAN_V1", "", ""), 10_000));
        assertTrue(state.apply(new EndEventPacket("END_BOSS_PHASE", "event", 1, "instance",
                1000, "boss", "", "COCOON_FALL", ""), 10_000));
        assertEquals("COCOON_FALL", state.bossAnimationForEntity("boss"));
        assertEquals(1000, state.bossAnimationElapsedMillisForEntity("boss", 10_000));
        assertTrue(state.apply(new EndEventPacket("END_BOSS_PHASE", "event", 1, "instance",
                500, "boss", "", "COCOON_FALL", ""), 10_500));
        assertEquals(1500, state.bossAnimationElapsedMillisForEntity("boss", 10_500));
        assertFalse(state.apply(new EndEventPacket("END_BOSS_PHASE", "event", 1, "instance",
                2100, "boss", "", "COCOON_FALL", ""), 10_600));
        assertEquals(1600, state.bossAnimationElapsedMillisForEntity("boss", 10_600));
        assertTrue(state.apply(new EndEventPacket("END_BOSS_PHASE", "event", 1, "instance",
                250, "boss", "", "LAST_SEAL|COCOON_LAND", ""), 11_000));
        assertEquals("COCOON_LAND", state.bossAnimationForEntity("boss"));
        assertEquals(750, state.bossAnimationElapsedMillisForEntity("boss", 11_000));
        assertTrue(state.apply(new EndEventPacket("END_BOSS_PHASE", "event", 1, "instance",
                800, "boss", "", "RUN", ""), 11_250));
        assertEquals(0, state.bossAnimationElapsedMillisForEntity("boss", 11_250));
    }
    @Test
    void signedV4BareAnimationSurvivesHeartbeatAndUsesTheBossbarPhase() {
        EndEventClientState state = new EndEventClientState();
        assertTrue(state.apply(new EndEventPacket("END_BOSS_BIND", "event", 1L, "boss-instance",
                0L, "boss", "END_RIFT_GUARDIAN_V1", "", ""), 50L));
        EndEventPacket slam = new EndEventPacket("END_BOSS_PHASE", "event", 1L, "boss-instance",
                400L, "boss", "", "GROUND_SLAM", "");
        assertTrue(state.apply(slam, 100L));
        assertEquals("GROUND_SLAM", state.bossAnimationForEntity("boss"),
                "V4 carries the canonical animation alone, without a legacy phase prefix");
        assertTrue(state.apply(slam, 600L));
        assertEquals(500L, state.bossAnimationElapsedMillisForEntity("boss", 600L),
                "heartbeat must not restart a committed authored clip");
        assertTrue(state.applyBossBar(new EndEventPacket("END_BOSS_BAR", "event", 1L,
                "boss-instance", 1000L, "boss", "", "RIFT|TELEGRAPHING", ""),
                0.5F, 500, 1000, 600L));
        assertEquals("RIFT", state.bossPhaseForEntity("boss"),
                "health phase comes from the signed bar, not the animation identifier");
        assertTrue(state.apply(new EndEventPacket("END_BOSS_PHASE", "event", 1L, "boss-instance",
                400L, "boss", "", "CHEST_STRIKE", ""), 700L));
        assertEquals(0L, state.bossAnimationElapsedMillisForEntity("boss", 700L));
    }

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
    void guardianAndLegacyPacketsKeepFullScaleAndMalformedScaleIsSafe() {
        for (String cue : new String[]{"READY", "READY|role=PERMANENT|scale=0.5",
                "READY|role=SMALL|scale=NaN",
                "READY|role=SMALL|scale=Infinity", "READY|role=SMALL|scale=0",
                "READY|role=SMALL|scale=5", "READY|role=SMALL|scale=-1"}) {
            EndEventClientState state = new EndEventClientState();
            assertTrue(state.apply(tentacleBind("actor", "instance"), 100L));
            assertTrue(state.apply(tentaclePhase("actor", "instance", cue), 101L));
            assertEquals(1.0F, state.tentaclePoseForEntityAt("actor", 200L).rigScale(), 0.0001F, cue);
        }
    }

    @Test
    void ambushAndTemporaryRolesRetainTheirSmallerScaleAcrossClipBlending() {
        for (String role : new String[]{"UNDER_PLAYER", "TEMPORARY"}) {
            EndEventClientState state = new EndEventClientState();
            assertTrue(state.apply(tentacleBind("actor", "instance"), 100L));
            assertTrue(state.apply(tentaclePhase("actor", "instance", "READY|role=" + role + "|scale=0.55"), 101L));
            assertTrue(state.apply(tentaclePhase("actor", "instance", "TELEGRAPH_GRAB|role=" + role + "|scale=0.55"), 300L));
            assertEquals(0.55F, state.tentaclePoseForEntityAt("actor", 350L).rigScale(), 0.0001F);
            assertEquals(role, state.tentacleRoleForEntity("actor"));
        }
    }

    @Test
    void acceptedHealthChangeFlashesWithoutInterruptingAnAttackOrRenewingOnHeartbeat() {
        EndEventClientState state = new EndEventClientState();
        assertTrue(state.apply(tentacleBind("actor", "instance"), 100L));
        assertTrue(state.apply(tentaclePhase("actor", "instance", "READY|t=2|hp=60"), 101L));
        assertFalse(state.tentacleHurtFlashAt("actor", 110L));
        assertTrue(state.apply(tentaclePhase("actor", "instance", "READY|t=2|hp=50"), 200L));
        assertTrue(state.tentacleHurtFlashAt("actor", 250L));
        assertTrue(state.apply(tentaclePhase("actor", "instance", "READY|t=2|hp=50"), 350L));
        assertFalse(state.tentacleHurtFlashAt("actor", 401L));
        assertTrue(state.apply(tentaclePhase("actor", "instance", "READY|hp=NaN"), 410L));
        assertFalse(state.tentacleHurtFlashAt("actor", 411L));
        state.clear();
        assertFalse(state.tentacleHurtFlashAt("actor", 250L));
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

    @Test
    void aLoopingDecoyCueRenewedUnderANewCastKeepsItsPlayedPhase() {
        EndEventClientState state = new EndEventClientState();
        assertTrue(state.apply(guardianBind("decoy", "decoy-instance"), 1_000L));
        assertTrue(state.apply(guardianActor("decoy", "decoy-instance",
                "GACT1|IDLE_BREATH|0|5000|10000"), 1_000L));
        assertEquals(5_000L, state.bossAnimationElapsedMillisForEntity("decoy", 1_000L));

        // The server caps one cue at ten seconds; at the cap it renews the
        // same action under the next cast with elapsed zero.
        assertTrue(state.apply(guardianActor("decoy", "decoy-instance",
                "GACT1|IDLE_BREATH|1|0|10000"), 6_000L));
        assertEquals(10_000L, state.bossAnimationElapsedMillisForEntity("decoy", 6_000L),
                "the loop must continue where it stopped instead of popping to frame zero");
        assertEquals(10_500L, state.bossAnimationElapsedMillisForEntity("decoy", 6_500L));

        // A second rollover keeps accumulating, so a long idle never snaps.
        assertTrue(state.apply(guardianActor("decoy", "decoy-instance",
                "GACT1|IDLE_BREATH|2|0|10000"), 11_000L));
        assertEquals(15_000L, state.bossAnimationElapsedMillisForEntity("decoy", 11_000L));

        // A genuinely new action starts at zero and drops the loop phase.
        assertTrue(state.apply(guardianActor("decoy", "decoy-instance",
                "GACT1|HURT|3|0|10000"), 11_500L));
        assertEquals(0L, state.bossAnimationElapsedMillisForEntity("decoy", 11_500L));
        assertEquals(500L, state.bossAnimationElapsedMillisForEntity("decoy", 12_000L));
    }

    @Test
    void aOneShotCueIsNeverTreatedAsALoopRollover() {
        EndEventClientState state = new EndEventClientState();
        assertTrue(state.apply(guardianBind("decoy", "decoy-instance"), 1_000L));
        assertTrue(state.apply(guardianActor("decoy", "decoy-instance",
                "GACT1|SPELL_RIFT_ARROWS|0|4000|10000"), 1_000L));
        // Same action, new cast: this is a repeated cast, not a loop, so the
        // clip is allowed to restart from its first frame.
        assertTrue(state.apply(guardianActor("decoy", "decoy-instance",
                "GACT1|SPELL_RIFT_ARROWS|1|0|10000"), 2_000L));
        assertEquals(0L, state.bossAnimationElapsedMillisForEntity("decoy", 2_000L));
    }

    @Test
    void sameCastHeartbeatsDoNotDoubleCountTheLoopClock() {
        // The server heartbeats a decoy's looping cue every ten ticks under the
        // SAME cast with an advancing elapsed. Only a cast change is a
        // rollover; adding the phase on every heartbeat would run the clip
        // faster than real time.
        EndEventClientState state = new EndEventClientState();
        assertTrue(state.apply(guardianBind("decoy", "decoy-instance"), 0L));
        assertTrue(state.apply(guardianActor("decoy", "decoy-instance",
                "GACT1|RUN|10|0|10000"), 0L));
        assertEquals(0L, state.bossAnimationElapsedMillisForEntity("decoy", 0L));

        // Five heartbeats inside one cast: the clock must track the cue's own
        // elapsed, not accumulate each previous segment.
        assertTrue(state.apply(guardianActor("decoy", "decoy-instance",
                "GACT1|RUN|10|500|10000"), 500L));
        assertEquals(500L, state.bossAnimationElapsedMillisForEntity("decoy", 500L));
        assertTrue(state.apply(guardianActor("decoy", "decoy-instance",
                "GACT1|RUN|10|1000|10000"), 1_000L));
        assertEquals(1_000L, state.bossAnimationElapsedMillisForEntity("decoy", 1_000L));
        assertTrue(state.apply(guardianActor("decoy", "decoy-instance",
                "GACT1|RUN|10|1500|10000"), 1_500L));
        assertEquals(1_500L, state.bossAnimationElapsedMillisForEntity("decoy", 1_500L));
        assertEquals(2_000L, state.bossAnimationElapsedMillisForEntity("decoy", 2_000L));

        // A genuine ten-second rollover then adds exactly one segment.
        assertTrue(state.apply(guardianActor("decoy", "decoy-instance",
                "GACT1|RUN|20|0|10000"), 10_000L));
        assertEquals(10_000L, state.bossAnimationElapsedMillisForEntity("decoy", 10_000L));
        assertEquals(10_500L, state.bossAnimationElapsedMillisForEntity("decoy", 10_500L));
    }

    private static EndEventPacket guardianBind(String subject, String instance) {
        return new EndEventPacket("END_ENTITY_BIND", "test-event", 1L, instance,
                0L, subject, "END_RIFT_ECHO_DECOY_V1", "", "");
    }

    private static EndEventPacket guardianActor(String subject, String instance, String cue) {
        return new EndEventPacket("END_ENTITY_PHASE", "test-event", 1L, instance,
                1000L, subject, "", cue, "");
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
