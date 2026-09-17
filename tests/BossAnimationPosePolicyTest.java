import me.copimine.endevent.domain.BossAnimationPosePolicy;
import me.copimine.endevent.domain.BossHitboxPose;
import me.copimine.endevent.domain.BossHitboxProfile;
import me.copimine.endevent.domain.BossOrientedHitboxPolicy;

import java.util.Map;

public final class BossAnimationPosePolicyTest {
    public static void main(String[] args) {
        Map<BossHitboxProfile.PartKey, BossHitboxPose> bind =
                BossAnimationPosePolicy.sampleSegments("CHEST_STRIKE", 0.0D);
        Map<BossHitboxProfile.PartKey, BossHitboxPose> attack =
                BossAnimationPosePolicy.sampleSegments("CHEST_STRIKE", 40.0D);

        BossHitboxPose armAtBind = bind.get(
                new BossHitboxProfile.PartKey(BossHitboxProfile.PartId.LEFT_UPPER_ARM, 0));
        BossHitboxPose armAtAttack = attack.get(
                new BossHitboxProfile.PartKey(BossHitboxProfile.PartId.LEFT_UPPER_ARM, 0));
        require(armAtBind != null && armAtAttack != null, "left arm sample is required");
        require(!same(armAtBind, armAtAttack),
                "authored attack frame must move the left upper arm");

        BossHitboxPose unanimatedAtBind = bind.get(
                new BossHitboxProfile.PartKey(BossHitboxProfile.PartId.LEFT_LEG, 0));
        BossHitboxPose unanimatedAtAttack = attack.get(
                new BossHitboxProfile.PartKey(BossHitboxProfile.PartId.LEFT_LEG, 0));
        require(same(BossHitboxPose.NONE, unanimatedAtBind),
                "bone absent from clip must stay at bind pose");
        require(same(unanimatedAtBind, unanimatedAtAttack),
                "unanimated leg must remain at bind pose during chest strike");

        BossHitboxPose midpoint =
                BossAnimationPosePolicy.sampleSegments("CHEST_STRIKE", 20.0D).get(
                        new BossHitboxProfile.PartKey(BossHitboxProfile.PartId.LEFT_UPPER_ARM, 0));
        require(midpoint != null && midpoint.translationModelZ() > 0.0D,
                "linear authored interpolation must be visible at midpoint");
        require(BossAnimationPosePolicy.sampleSegments("unknown", 10.0D).values().stream()
                        .allMatch(BossAnimationPosePolicyTest::isNone),
                "unknown animation must fail closed to bind pose");

        BossHitboxPose childForearm =
                BossAnimationPosePolicy.sampleSegments("IDLE_BREATH", 40.0D).get(
                        new BossHitboxProfile.PartKey(BossHitboxProfile.PartId.LEFT_FOREARM, 0));
        require(childForearm != null && !isNone(childForearm),
                "a child forearm must inherit its animated parent transform");
        BossOrientedHitboxPolicy.Matrix3 expectedParentRotation =
                BossOrientedHitboxPolicy.Matrix3.euler(new BossOrientedHitboxPolicy.Euler(
                        0.8647D, 1.8673D, 4.6231D));
        require(matrixSame(expectedParentRotation, childForearm.rotation()),
                "combined child pose must preserve the exact parent rotation matrix");
        BossHitboxProfile.Part forearm = BossHitboxProfile.canonical().parts(
                BossHitboxProfile.PartId.LEFT_FOREARM).get(0);
        BossOrientedHitboxPolicy.OrientedBox forearmBox =
                BossOrientedHitboxPolicy.fromPartWithPose(forearm,
                        new me.copimine.endevent.domain.BossHitboxTransformPolicy.Anchor(
                                0.0D, 0.0D, 0.0D, 0.0D), childForearm);
        require(matrixSame(childForearm.rotation(), forearmBox.orientation()),
                "OBB path must consume the composed matrix without Euler loss");

        Map<BossHitboxProfile.PartKey, BossHitboxPose> slam =
                BossAnimationPosePolicy.sampleSegments("GROUND_SLAM", 50.0D);
        BossHitboxPose upperLeg = slam.get(
                new BossHitboxProfile.PartKey(BossHitboxProfile.PartId.LEFT_LEG, 0));
        BossHitboxPose lowerLeg = slam.get(
                new BossHitboxProfile.PartKey(BossHitboxProfile.PartId.LEFT_LEG, 1));
        require(upperLeg != null && lowerLeg != null, "both authored leg segments are required");
        require(!same(upperLeg, lowerLeg),
                "the lower leg segment must compose its own authored child track");
        System.out.println("BossAnimationPosePolicyTest OK");
    }

    private static boolean same(BossHitboxPose first, BossHitboxPose second) {
        return Math.abs(first.translationModelX() - second.translationModelX()) < 1.0E-9D
                && Math.abs(first.translationModelY() - second.translationModelY()) < 1.0E-9D
                && Math.abs(first.translationModelZ() - second.translationModelZ()) < 1.0E-9D
                && matrixSame(first.rotation(), second.rotation());
    }

    private static boolean isNone(BossHitboxPose pose) {
        return same(BossHitboxPose.NONE, pose);
    }

    private static boolean matrixSame(me.copimine.endevent.domain.BossOrientedHitboxPolicy.Matrix3 first,
                                      me.copimine.endevent.domain.BossOrientedHitboxPolicy.Matrix3 second) {
        return Math.abs(first.m00() - second.m00()) < 1.0E-9D
                && Math.abs(first.m01() - second.m01()) < 1.0E-9D
                && Math.abs(first.m02() - second.m02()) < 1.0E-9D
                && Math.abs(first.m10() - second.m10()) < 1.0E-9D
                && Math.abs(first.m11() - second.m11()) < 1.0E-9D
                && Math.abs(first.m12() - second.m12()) < 1.0E-9D
                && Math.abs(first.m20() - second.m20()) < 1.0E-9D
                && Math.abs(first.m21() - second.m21()) < 1.0E-9D
                && Math.abs(first.m22() - second.m22()) < 1.0E-9D;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
