import java.util.OptionalDouble;
import me.copimine.endevent.domain.BossOrientedHitboxPolicy;

public final class BossOrientedHitboxPolicyTest {
    public static void main(String[] args) {
        BossOrientedHitboxPolicy.OrientedBox thinDiagonal =
                new BossOrientedHitboxPolicy.OrientedBox(
                        new BossOrientedHitboxPolicy.Vec3(0.0D, 0.0D, 0.0D),
                        new BossOrientedHitboxPolicy.Vec3(0.10D, 0.50D, 2.00D),
                        new BossOrientedHitboxPolicy.Euler(0.0D, 45.0D, 0.0D));

        BossOrientedHitboxPolicy.Ray emptyCorner = new BossOrientedHitboxPolicy.Ray(
                new BossOrientedHitboxPolicy.Vec3(1.45D, -5.0D, 1.45D),
                new BossOrientedHitboxPolicy.Vec3(0.0D, 1.0D, 0.0D));
        OptionalDouble cornerHit = BossOrientedHitboxPolicy.nearestHitDistance(
                emptyCorner, thinDiagonal, 10.0D);
        check(enclosingAabbWouldHit(emptyCorner, 1.50D, 0.50D, 1.50D, 10.0D),
                "the regression ray must cross the old enclosing AABB");
        check(cornerHit.isEmpty(),
                "a ray through the empty 45-degree AABB corner must miss the OBB");

        BossOrientedHitboxPolicy.Ray centre = new BossOrientedHitboxPolicy.Ray(
                new BossOrientedHitboxPolicy.Vec3(0.0D, 0.0D, -5.0D),
                new BossOrientedHitboxPolicy.Vec3(0.0D, 0.0D, 1.0D));
        OptionalDouble centreHit = BossOrientedHitboxPolicy.nearestHitDistance(
                centre, thinDiagonal, 10.0D);
        check(centreHit.isPresent(), "a centre ray must hit the oriented box");
        check(centreHit.getAsDouble() >= 0.0D && centreHit.getAsDouble() < 10.0D,
                "the OBB must return the nearest finite ray distance");
        check(Math.abs(centreHit.getAsDouble() - 4.8585786438D) < 1.0E-6D,
                "the centre ray must report the rotated box entry distance");

        testFiniteProjectileSegments(thinDiagonal);

        System.out.println("BossOrientedHitboxPolicyTest OK");
    }

    private static void testFiniteProjectileSegments(BossOrientedHitboxPolicy.OrientedBox box) {
        check(!BossOrientedHitboxPolicy.segmentIntersects(
                        box,
                        new BossOrientedHitboxPolicy.Vec3(0.0D, 0.0D, -4.0D),
                        new BossOrientedHitboxPolicy.Vec3(0.0D, 0.0D, -2.0D)),
                "a projectile segment ending before the box must not hit");
        check(!BossOrientedHitboxPolicy.segmentIntersects(
                        box,
                        new BossOrientedHitboxPolicy.Vec3(0.0D, 0.0D, 2.0D),
                        new BossOrientedHitboxPolicy.Vec3(0.0D, 0.0D, 4.0D)),
                "a projectile segment already past the box must not hit it again");
        check(BossOrientedHitboxPolicy.segmentIntersects(
                        box,
                        new BossOrientedHitboxPolicy.Vec3(0.0D, 0.0D, -2.0D),
                        new BossOrientedHitboxPolicy.Vec3(0.0D, 0.0D, 2.0D)),
                "a projectile segment crossing the box must hit");
        check(!BossOrientedHitboxPolicy.segmentIntersects(
                        box,
                        new BossOrientedHitboxPolicy.Vec3(0.0D, 0.0D, -4.0D),
                        new BossOrientedHitboxPolicy.Vec3(0.0D, 0.0D, -4.0D)),
                "a zero-length segment outside the box must not invent a hit");
        check(BossOrientedHitboxPolicy.segmentIntersects(
                        box,
                        new BossOrientedHitboxPolicy.Vec3(0.0D, 0.0D, 0.0D),
                        new BossOrientedHitboxPolicy.Vec3(0.0D, 0.0D, 0.0D)),
                "a zero-length segment inside the box must hit exactly once");
    }

    private static boolean enclosingAabbWouldHit(BossOrientedHitboxPolicy.Ray ray,
                                                   double halfX, double halfY,
                                                   double halfZ, double maxDistance) {
        double tMin = 0.0D;
        double tMax = maxDistance;
        double[] origin = {ray.origin().x(), ray.origin().y(), ray.origin().z()};
        double[] direction = {ray.direction().x(), ray.direction().y(), ray.direction().z()};
        double[] half = {halfX, halfY, halfZ};
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(direction[axis]) < 1.0E-9D) {
                if (Math.abs(origin[axis]) > half[axis]) return false;
                continue;
            }
            double near = (-half[axis] - origin[axis]) / direction[axis];
            double far = (half[axis] - origin[axis]) / direction[axis];
            if (near > far) {
                double swap = near;
                near = far;
                far = swap;
            }
            tMin = Math.max(tMin, near);
            tMax = Math.min(tMax, far);
            if (tMin > tMax) return false;
        }
        return tMax >= 0.0D && tMin <= maxDistance;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
