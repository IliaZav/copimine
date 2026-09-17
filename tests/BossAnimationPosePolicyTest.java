import me.copimine.endevent.domain.BossAnimationPosePolicy;
import me.copimine.endevent.domain.BossHitboxProfile;
import me.copimine.endevent.domain.BossHitboxTransformPolicy;

import java.util.Map;

public final class BossAnimationPosePolicyTest {
    public static void main(String[] args) {
        Map<BossHitboxProfile.PartId, BossHitboxTransformPolicy.PoseOffset> bind =
                BossAnimationPosePolicy.sample("CHEST_STRIKE", 0.0D);
        Map<BossHitboxProfile.PartId, BossHitboxTransformPolicy.PoseOffset> attack =
                BossAnimationPosePolicy.sample("CHEST_STRIKE", 40.0D);

        BossHitboxTransformPolicy.PoseOffset armAtBind = bind.get(
                BossHitboxProfile.PartId.LEFT_UPPER_ARM);
        BossHitboxTransformPolicy.PoseOffset armAtAttack = attack.get(
                BossHitboxProfile.PartId.LEFT_UPPER_ARM);
        require(armAtBind != null && armAtAttack != null, "left arm sample is required");
        require(!same(armAtBind, armAtAttack),
                "authored attack frame must move the left upper arm");

        BossHitboxTransformPolicy.PoseOffset unanimatedAtBind = bind.get(
                BossHitboxProfile.PartId.LEFT_LEG);
        BossHitboxTransformPolicy.PoseOffset unanimatedAtAttack = attack.get(
                BossHitboxProfile.PartId.LEFT_LEG);
        require(same(BossHitboxTransformPolicy.PoseOffset.NONE, unanimatedAtBind),
                "bone absent from clip must stay at bind pose");
        require(same(unanimatedAtBind, unanimatedAtAttack),
                "unanimated leg must remain at bind pose during chest strike");

        BossHitboxTransformPolicy.PoseOffset midpoint =
                BossAnimationPosePolicy.sample("CHEST_STRIKE", 20.0D).get(
                        BossHitboxProfile.PartId.LEFT_UPPER_ARM);
        require(midpoint != null && midpoint.translationModelZ() > 0.0D,
                "linear authored interpolation must be visible at midpoint");
        require(BossAnimationPosePolicy.sample("unknown", 10.0D).values().stream()
                        .allMatch(BossAnimationPosePolicyTest::isNone),
                "unknown animation must fail closed to bind pose");
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
