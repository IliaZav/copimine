import me.copimine.endevent.domain.BossOrientedHitboxPolicy;

/** Strong regression coverage for finite projectile sweeps through the boss OBB. */
public final class BossProjectileSweepPolicyTest {
    public static void main(String[] args) {
        BossOrientedHitboxPolicy.OrientedBox pelvis =
                new BossOrientedHitboxPolicy.OrientedBox(
                        new BossOrientedHitboxPolicy.Vec3(0.0D, 1.0D, 0.0D),
                        new BossOrientedHitboxPolicy.Vec3(0.35D, 0.6D, 0.35D),
                        new BossOrientedHitboxPolicy.Euler(0.0D, 35.0D, 0.0D));

        // Regression: a fast projectile may cross the model between server
        // samples; a point-only hit check would miss this entire segment.
        check(BossOrientedHitboxPolicy.segmentIntersects(
                        pelvis,
                        new BossOrientedHitboxPolicy.Vec3(-3.0D, 1.0D, -3.0D),
                        new BossOrientedHitboxPolicy.Vec3(3.0D, 1.0D, 3.0D)),
                "a fast diagonal segment crossing the rotated OBB must hit");

        check(!BossOrientedHitboxPolicy.segmentIntersects(
                        pelvis,
                        new BossOrientedHitboxPolicy.Vec3(2.0D, 1.0D, 2.0D),
                        new BossOrientedHitboxPolicy.Vec3(4.0D, 1.0D, 4.0D)),
                "a segment already past the OBB must not hit it again");
        check(!BossOrientedHitboxPolicy.segmentIntersects(
                        pelvis,
                        new BossOrientedHitboxPolicy.Vec3(-4.0D, 1.0D, -4.0D),
                        new BossOrientedHitboxPolicy.Vec3(-2.0D, 1.0D, -2.0D)),
                "a segment ending before the OBB must not invent a hit");
        check(!BossOrientedHitboxPolicy.segmentIntersects(
                        pelvis,
                        new BossOrientedHitboxPolicy.Vec3(0.0D, 1.0D, 2.0D),
                        new BossOrientedHitboxPolicy.Vec3(0.0D, 1.0D, 2.0D)),
                "an outside zero-length sample must not hit");
        check(BossOrientedHitboxPolicy.segmentIntersects(
                        pelvis,
                        new BossOrientedHitboxPolicy.Vec3(0.0D, 1.0D, 0.0D),
                        new BossOrientedHitboxPolicy.Vec3(0.0D, 1.0D, 0.0D)),
                "an inside zero-length sample must hit exactly once");

        System.out.println("BossProjectileSweepPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
