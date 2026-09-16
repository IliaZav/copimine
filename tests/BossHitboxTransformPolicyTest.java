import me.copimine.endevent.domain.BossHitboxProfile;
import me.copimine.endevent.domain.BossHitboxTransformPolicy;

public final class BossHitboxTransformPolicyTest {
    public static void main(String[] args) {
        BossHitboxProfile.Part head = BossHitboxProfile.canonical()
                .part(BossHitboxProfile.PartId.HEAD);
        BossHitboxTransformPolicy.Anchor origin = new BossHitboxTransformPolicy.Anchor(
                -12.5D, 70.0D, 8.25D, 0.0D);
        BossHitboxTransformPolicy.Box atZero = BossHitboxTransformPolicy.transform(
                head, origin, BossHitboxTransformPolicy.PoseOffset.NONE);
        check(close(atZero.center().x(), origin.x() + head.centerModel().x() / 16.0D),
                "yaw zero must preserve local X translation");
        check(close(atZero.center().z(), origin.z() + head.centerModel().z() / 16.0D),
                "yaw zero must preserve local Z translation");
        check(close(atZero.center().y(), origin.y() + head.centerModel().y() / 16.0D),
                "model units must convert to blocks");

        BossHitboxTransformPolicy.Box atNinety = BossHitboxTransformPolicy.transform(
                head, new BossHitboxTransformPolicy.Anchor(
                        origin.x(), origin.y(), origin.z(), 90.0D),
                BossHitboxTransformPolicy.PoseOffset.NONE);
        check(close(atNinety.center().x(), origin.x() - head.centerModel().z() / 16.0D),
                "yaw ninety must rotate local Z into world negative X");
        check(close(atNinety.center().z(), origin.z() + head.centerModel().x() / 16.0D),
                "yaw ninety must rotate local X into world positive Z");

        BossHitboxTransformPolicy.PoseOffset pose = new BossHitboxTransformPolicy.PoseOffset(
                4.0D, -2.0D, 8.0D, 0.0D, 0.0D, 0.0D);
        BossHitboxTransformPolicy.Box translated = BossHitboxTransformPolicy.transform(
                head, origin, pose);
        check(close(translated.center().x(), atZero.center().x() + 4.0D / 16.0D),
                "pose translation must move the proxy with the animated bone");
        check(close(translated.center().y(), atZero.center().y() - 2.0D / 16.0D),
                "pose vertical offset must be applied in model units");
        check(close(translated.center().z(), atZero.center().z() + 8.0D / 16.0D),
                "pose depth offset must be applied in model units");

        BossHitboxTransformPolicy.Box rotated = BossHitboxTransformPolicy.transform(
                head, origin, new BossHitboxTransformPolicy.PoseOffset(
                        0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 35.0D));
        check(rotated.width() > 0.0D && rotated.depth() > 0.0D && rotated.height() > 0.0D,
                "rotated boxes must remain finite and positive");
        check(Double.isFinite(rotated.center().x()) && Double.isFinite(rotated.center().y())
                        && Double.isFinite(rotated.center().z()),
                "rotated box center must remain finite");
        System.out.println("BossHitboxTransformPolicyTest OK");
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 1.0E-9D;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
