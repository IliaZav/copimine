import me.copimine.endevent.domain.BossAnimationPosePolicy;
import me.copimine.endevent.domain.BossHitboxProfile;
import me.copimine.endevent.domain.BossHitboxTransformPolicy;

import java.util.Map;

public final class BossAnimationPosePolicyTest {
    public static void main(String[] args) {
        Map<BossHitboxProfile.PartKey, BossHitboxTransformPolicy.PoseOffset> bind =
                BossAnimationPosePolicy.sampleSegments("CHEST_STRIKE", 0.0D);
        Map<BossHitboxProfile.PartKey, BossHitboxTransformPolicy.PoseOffset> attack =
                BossAnimationPosePolicy.sampleSegments("CHEST_STRIKE", 40.0D);

        BossHitboxTransformPolicy.PoseOffset armAtBind = bind.get(
                new BossHitboxProfile.PartKey(BossHitboxProfile.PartId.LEFT_UPPER_ARM, 0));
        BossHitboxTransformPolicy.PoseOffset armAtAttack = attack.get(
                new BossHitboxProfile.PartKey(BossHitboxProfile.PartId.LEFT_UPPER_ARM, 0));
        require(armAtBind != null && armAtAttack != null, "left arm sample is required");
        require(!same(armAtBind, armAtAttack),
                "authored attack frame must move the left upper arm");

        BossHitboxTransformPolicy.PoseOffset unanimatedAtBind = bind.get(
                new BossHitboxProfile.PartKey(BossHitboxProfile.PartId.LEFT_LEG, 0));
        BossHitboxTransformPolicy.PoseOffset unanimatedAtAttack = attack.get(
                new BossHitboxProfile.PartKey(BossHitboxProfile.PartId.LEFT_LEG, 0));
        require(same(BossHitboxTransformPolicy.PoseOffset.NONE, unanimatedAtBind),
                "bone absent from clip must stay at bind pose");
        require(same(unanimatedAtBind, unanimatedAtAttack),
                "unanimated leg must remain at bind pose during chest strike");

        BossHitboxTransformPolicy.PoseOffset midpoint =
                BossAnimationPosePolicy.sampleSegments("CHEST_STRIKE", 20.0D).get(
                        new BossHitboxProfile.PartKey(BossHitboxProfile.PartId.LEFT_UPPER_ARM, 0));
        require(midpoint != null && midpoint.translationModelZ() > 0.0D,
                "linear authored interpolation must be visible at midpoint");
        require(BossAnimationPosePolicy.sampleSegments("unknown", 10.0D).values().stream()
                        .allMatch(BossAnimationPosePolicyTest::isNone),
                "unknown animation must fail closed to bind pose");

        BossHitboxTransformPolicy.PoseOffset childForearm =
                BossAnimationPosePolicy.sampleSegments("IDLE_BREATH", 40.0D).get(
                        new BossHitboxProfile.PartKey(BossHitboxProfile.PartId.LEFT_FOREARM, 0));
        require(childForearm != null && !isNone(childForearm),
                "a child forearm must inherit its animated parent transform");

        Map<BossHitboxProfile.PartKey, BossHitboxTransformPolicy.PoseOffset> slam =
                BossAnimationPosePolicy.sampleSegments("GROUND_SLAM", 50.0D);
        BossHitboxTransformPolicy.PoseOffset upperLeg = slam.get(
                new BossHitboxProfile.PartKey(BossHitboxProfile.PartId.LEFT_LEG, 0));
        BossHitboxTransformPolicy.PoseOffset lowerLeg = slam.get(
                new BossHitboxProfile.PartKey(BossHitboxProfile.PartId.LEFT_LEG, 1));
        require(upperLeg != null && lowerLeg != null, "both authored leg segments are required");
        require(!same(upperLeg, lowerLeg),
                "the lower leg segment must compose its own authored child track");
        System.out.println("BossAnimationPosePolicyTest OK");
    }

    private static boolean same(BossHitboxTransformPolicy.PoseOffset first,
                                BossHitboxTransformPolicy.PoseOffset second) {
        return Math.abs(first.translationModelX() - second.translationModelX()) < 1.0E-9D
                && Math.abs(first.translationModelY() - second.translationModelY()) < 1.0E-9D
                && Math.abs(first.translationModelZ() - second.translationModelZ()) < 1.0E-9D
                && Math.abs(first.pitchDegrees() - second.pitchDegrees()) < 1.0E-9D
                && Math.abs(first.yawDegrees() - second.yawDegrees()) < 1.0E-9D
                && Math.abs(first.rollDegrees() - second.rollDegrees()) < 1.0E-9D;
    }

    private static boolean isNone(BossHitboxTransformPolicy.PoseOffset pose) {
        return same(BossHitboxTransformPolicy.PoseOffset.NONE, pose);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
