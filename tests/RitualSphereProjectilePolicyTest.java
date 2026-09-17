import me.copimine.endevent.domain.RitualSphereProjectilePolicy;

public final class RitualSphereProjectilePolicyTest {
    public static void main(String[] args) {
        var direction = RitualSphereProjectilePolicy.direction(
                new RitualSphereProjectilePolicy.Vec3(0.0D, 10.0D, 0.0D),
                new RitualSphereProjectilePolicy.Vec3(0.0D, 10.0D, 8.0D));

        check(close(direction.x(), 0.0D), "sphere projectile x direction");
        check(close(direction.y(), 0.0D), "sphere projectile y direction");
        check(close(direction.z(), 1.0D), "sphere projectile must point from sphere to target");
        check(close(length(direction), 1.0D), "sphere projectile direction must be normalized");

        var coincident = RitualSphereProjectilePolicy.direction(
                new RitualSphereProjectilePolicy.Vec3(1.0D, 2.0D, 3.0D),
                new RitualSphereProjectilePolicy.Vec3(1.0D, 2.0D, 3.0D));
        check(close(length(coincident), 0.0D), "coincident origin and target must not launch");

        System.out.println("RitualSphereProjectilePolicyTest OK");
    }

    private static double length(RitualSphereProjectilePolicy.Vec3 value) {
        return Math.sqrt(value.x() * value.x() + value.y() * value.y() + value.z() * value.z());
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
