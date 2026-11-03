package me.copimine.client;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The boss used to snap between clips: a swing could jump to the first frame
 * of the next animation in a single frame. These tests pin the blend that
 * replaces the snap.
 */
class UserEndBossAnimationBlendTest {
    private static ChameleonGuardianGeometry.GuardianPose pose(String bone, float rotationX) {
        return ChameleonGuardianGeometry.GuardianPose.of(Map.of(bone,
                new ChameleonGuardianGeometry.BoneDelta(
                        new ChameleonGuardianGeometry.Point(0.0F, 0.0F, 0.0F),
                        new ChameleonGuardianGeometry.Point(rotationX, 0.0F, 0.0F),
                        new ChameleonGuardianGeometry.Point(1.0F, 1.0F, 1.0F))));
    }

    @Test
    void switchingToTheSameClipDoesNotBlend() {
        assertEquals(0.0F, UserEndBossAnimationPlayer.blendTicks("IDLE_BREATH", "IDLE_BREATH"), 0.0001F);
        // Different spellings of the same clip are the same clip: the wire id
        // arrives already canonical, and blending it against itself would add
        // a blend window to every single redraw of a running state.
        assertEquals(0.0F, UserEndBossAnimationPlayer.blendTicks("RUN", " run "), 0.0001F);
    }

    @Test
    void contactMovesAndDefeatGetTheLongestBlend() {
        float swipe = UserEndBossAnimationPlayer.blendTicks("IDLE_BREATH", "MELEE_SWIPE");
        float dying = UserEndBossAnimationPlayer.blendTicks("MELEE_SWIPE", "DYING");
        float idle = UserEndBossAnimationPlayer.blendTicks("MELEE_SWIPE", "IDLE_BREATH");
        assertTrue(swipe > 0.0F, "a contact move must ease in");
        assertTrue(dying >= swipe, "the defeat transition is the most visible one");
        assertEquals(6.0F, idle, 0.0001F);
        assertTrue(idle > UserEndBossAnimationPlayer.blendTicks("IDLE_BREATH", "HURT"),
                "recovering into idle is slower than the hurt flinch");
    }

    @Test
    void theBlendInterpolatesRatherThanSnapping() {
        ChameleonGuardianGeometry.GuardianPose from = pose("head", 0.0F);
        ChameleonGuardianGeometry.GuardianPose to = pose("head", 20.0F);
        ChameleonGuardianGeometry.GuardianPose halfway =
                UserEndBossAnimationPlayer.blend(from, to, 0.5F);
        assertEquals(10.0F, halfway.deltas().get("head").rotation().x(), 0.0001F);
        assertEquals(0.0F, UserEndBossAnimationPlayer.blend(from, to, 0.0F)
                .deltas().get("head").rotation().x(), 0.0001F);
        assertEquals(20.0F, UserEndBossAnimationPlayer.blend(from, to, 1.0F)
                .deltas().get("head").rotation().x(), 0.0001F);
    }

    @Test
    void outOfRangeWeightsAreClampedInsteadOfExtrapolating() {
        ChameleonGuardianGeometry.GuardianPose from = pose("head", 0.0F);
        ChameleonGuardianGeometry.GuardianPose to = pose("head", 20.0F);
        assertEquals(20.0F, UserEndBossAnimationPlayer.blend(from, to, 7.0F)
                .deltas().get("head").rotation().x(), 0.0001F);
        assertEquals(0.0F, UserEndBossAnimationPlayer.blend(from, to, -3.0F)
                .deltas().get("head").rotation().x(), 0.0001F);
    }

    @Test
    void aBoneAbsentFromOneClipKeepsTheOtherClipsValue() {
        ChameleonGuardianGeometry.GuardianPose from = pose("head", 12.0F);
        ChameleonGuardianGeometry.GuardianPose to = pose("right_hand", 8.0F);
        ChameleonGuardianGeometry.GuardianPose blended =
                UserEndBossAnimationPlayer.blend(from, to, 0.5F);
        // Snapping an unanimated bone to rest is the invisible pop this avoids.
        assertEquals(12.0F, blended.deltas().get("head").rotation().x(), 0.0001F);
        assertEquals(8.0F, blended.deltas().get("right_hand").rotation().x(), 0.0001F);
    }
}
