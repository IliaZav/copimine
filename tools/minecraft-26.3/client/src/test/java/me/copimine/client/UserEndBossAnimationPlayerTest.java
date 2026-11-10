package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserEndBossAnimationPlayerTest {
    @Test
    void phaseShiftHasItsOwnGroundedRoarClipInsteadOfReplayingAnAttack() {
        assertEquals(2.2F, UserEndBossAnimationPlayer.clipLengthSeconds("PHASE_TRANSITION"), 0.0001F);
        var phase = UserEndBossAnimationPlayer.sample("PHASE_TRANSITION", 18F);
        assertTrue(phase.hasBoneDelta("head"));
        assertTrue(phase.hasBoneDelta("left_hand"));
        assertNotEquals(UserEndBossAnimationPlayer.sample("GROUND_SLAM", 18F), phase);
    }

    @Test
    void samplesSuppliedIdleTracksAsNamedSourceBoneDeltas() {
        ChameleonGuardianGeometry.GuardianPose pose =
                UserEndBossAnimationPlayer.sample("IDLE_BREATH", 20.0F);

        assertTrue(pose.hasBoneDelta("head"));
        assertTrue(pose.hasBoneDelta("body"));
        assertTrue(pose.hasBoneDelta("left_hand"));
        assertTrue(pose.hasBoneDelta("right_hand"));
        assertFalse(pose.hasBoneDelta("right_leg_low"));
    }

    /**
     * A name that is not in the catalogue still animates: the renderer resolves
     * every wire id through clipFor(), because the supplied asset ships seven
     * clips for twenty-five animations and returning the identity pose left the
     * boss visibly frozen during every spell and cast beat. Identity is now
     * reserved for a name that resolves to no clip at all — the test asserts the
     * pose carries authored bone deltas, never invented geometry.
     */
    @Test
    void unknownClipStillAnimatesThroughTheNearestAuthoredClip() {
        ChameleonGuardianGeometry.GuardianPose pose =
                UserEndBossAnimationPlayer.sample("NOT_A_SUPPLIED_CLIP", 20.0F);

        assertNotEquals(ChameleonGuardianGeometry.GuardianPose.identity(), pose,
                "an unmapped animation must not freeze on the identity pose");
        assertTrue(pose.hasBoneDelta("head"),
                "the pose must come from an authored clip, not from invented geometry");
    }

    @Test
    void preservesTheSuppliedOneSecondHurtClip() {
        assertEquals(1.0F, UserEndBossAnimationPlayer.clipLengthSeconds("HURT"), 0.0001F);
    }
}
