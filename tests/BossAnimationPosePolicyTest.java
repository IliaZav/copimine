import me.copimine.endevent.domain.BossAnimationPosePolicy;
import me.copimine.endevent.domain.BossHitboxPose;
import me.copimine.endevent.domain.BossHitboxProfile;
import me.copimine.endevent.domain.BossHitboxTransformPolicy;
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
        BossHitboxPose childForearmAtBind =
                BossAnimationPosePolicy.sampleSegments("IDLE_BREATH", 0.0D).get(
                        new BossHitboxProfile.PartKey(BossHitboxProfile.PartId.LEFT_FOREARM, 0));
        require(childForearmAtBind != null && isNone(childForearmAtBind),
                "zero animation must be relative to the authored bind pose");
        require(childForearm != null && !isNone(childForearm),
                "a child forearm must inherit its animated parent transform");
        BossOrientedHitboxPolicy.Matrix3 bindParentRotation =
                BossOrientedHitboxPolicy.Matrix3.euler(new BossOrientedHitboxPolicy.Euler(
                        21.55363D, 3.40444D, 13.78967D));
        BossOrientedHitboxPolicy.Matrix3 animatedParentRotation =
                BossOrientedHitboxPolicy.Matrix3.euler(new BossOrientedHitboxPolicy.Euler(
                        21.55363D + 0.8647D,
                        3.40444D + 1.8673D,
                        13.78967D + 4.6231D));
        BossOrientedHitboxPolicy.Matrix3 bindChildRotation =
                BossOrientedHitboxPolicy.Matrix3.euler(new BossOrientedHitboxPolicy.Euler(
                        -32.5D, 0.0D, 0.0D));
        BossOrientedHitboxPolicy.Matrix3 expectedParentRotation =
                animatedParentRotation.multiply(bindChildRotation)
                        .multiply(bindParentRotation.multiply(bindChildRotation).transpose());
        require(matrixSame(expectedParentRotation, childForearm.rotation()),
                "combined child pose must preserve the exact bind-relative rotation matrix");
        BossHitboxProfile.Part forearm = BossHitboxProfile.canonical().parts(
                BossHitboxProfile.PartId.LEFT_FOREARM).get(0);
        BossOrientedHitboxPolicy.OrientedBox forearmBox =
                BossOrientedHitboxPolicy.fromPartWithPose(forearm,
                        new BossHitboxTransformPolicy.Anchor(10.0D, 20.0D, -4.0D, 90.0D),
                        childForearm);
        BossOrientedHitboxPolicy.Matrix3 expectedWorldRotation =
                BossOrientedHitboxPolicy.Matrix3.rotationY(90.0D)
                        .multiply(childForearm.rotation());
        require(matrixSame(expectedWorldRotation, forearmBox.orientation()),
                "OBB path must consume the composed matrix without Euler loss");
        BossHitboxTransformPolicy.Box forearmEnvelope =
                BossHitboxTransformPolicy.transformWithPose(forearm,
                        new BossHitboxTransformPolicy.Anchor(10.0D, 20.0D, -4.0D, 90.0D),
                        childForearm);
        require(close(forearmEnvelope.center().x(), forearmBox.center().x())
                        && close(forearmEnvelope.center().y(), forearmBox.center().y())
                        && close(forearmEnvelope.center().z(), forearmBox.center().z()),
                "AABB and OBB paths must share the same animated center");
        double projectedX = Math.abs(forearmBox.orientation().m00())
                * forearmBox.halfExtents().x()
                + Math.abs(forearmBox.orientation().m01())
                * forearmBox.halfExtents().y()
                + Math.abs(forearmBox.orientation().m02())
                * forearmBox.halfExtents().z();
        double projectedY = Math.abs(forearmBox.orientation().m10())
                * forearmBox.halfExtents().x()
                + Math.abs(forearmBox.orientation().m11())
                * forearmBox.halfExtents().y()
                + Math.abs(forearmBox.orientation().m12())
                * forearmBox.halfExtents().z();
        double projectedZ = Math.abs(forearmBox.orientation().m20())
                * forearmBox.halfExtents().x()
                + Math.abs(forearmBox.orientation().m21())
                * forearmBox.halfExtents().y()
                + Math.abs(forearmBox.orientation().m22())
                * forearmBox.halfExtents().z();
        require(close(forearmEnvelope.width(), projectedX * 2.0D)
                        && close(forearmEnvelope.height(), projectedY * 2.0D)
                        && close(forearmEnvelope.depth(), projectedZ * 2.0D),
                "AABB envelope must be the projection of the same animated OBB");
        BossHitboxTransformPolicy.PoseOffset legacy = BossAnimationPosePolicy.sample(
                "IDLE_BREATH", 40.0D).get(BossHitboxProfile.PartId.LEFT_FOREARM);
        BossOrientedHitboxPolicy.Matrix3 legacyRotation =
                BossOrientedHitboxPolicy.Matrix3.euler(new BossOrientedHitboxPolicy.Euler(
                        legacy.pitchDegrees(), legacy.yawDegrees(), legacy.rollDegrees()));
        require(matrixSame(legacyRotation, childForearm.rotation()),
                "legacy Euler view must round-trip a combined bind-relative rotation");

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

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 1.0E-9D;
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
