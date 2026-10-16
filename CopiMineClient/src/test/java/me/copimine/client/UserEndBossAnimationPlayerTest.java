package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserEndBossAnimationPlayerTest {
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

    @Test
    void unknownClipSamplesTheIdentityPoseInsteadOfMutatingModelParts() {
        assertEquals(ChameleonGuardianGeometry.GuardianPose.identity(),
                UserEndBossAnimationPlayer.sample("NOT_A_SUPPLIED_CLIP", 20.0F));
    }

    @Test
    void preservesTheSuppliedOneSecondHurtClip() {
        assertEquals(1.0F, UserEndBossAnimationPlayer.clipLengthSeconds("HURT"), 0.0001F);
    }
}
