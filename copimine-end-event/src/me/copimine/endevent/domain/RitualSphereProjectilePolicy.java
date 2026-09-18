package me.copimine.endevent.domain;

/** Pure direction and speed bounds for the Wave 6 Ritual Sphere barrage. */
public final class RitualSphereProjectilePolicy {
    public static final double MAX_INITIAL_SPEED = 0.85D;

    private RitualSphereProjectilePolicy() {
    }

    /**
     * Return the finite unit vector from the visible sphere origin to a target.
     * Invalid or coincident points deliberately produce no launch direction.
     */
    public static Vec3 direction(Vec3 origin, Vec3 target) {
        if (!finite(origin) || !finite(target)) {
            return zero();
        }
        double x = target.x() - origin.x();
        double y = target.y() - origin.y();
        double z = target.z() - origin.z();
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return zero();
        }
        double scale = Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z)));
        if (!Double.isFinite(scale) || scale == 0.0D) {
            return zero();
        }
        double scaledX = x / scale;
        double scaledY = y / scale;
        double scaledZ = z / scale;
        double scaledLength = Math.sqrt(scaledX * scaledX
                + scaledY * scaledY + scaledZ * scaledZ);
        if (!Double.isFinite(scaledLength) || scaledLength == 0.0D) {
            return zero();
        }
        x = scaledX / scaledLength;
        y = scaledY / scaledLength;
        z = scaledZ / scaledLength;
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                ? new Vec3(x, y, z) : zero();
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x())
                && Double.isFinite(value.y()) && Double.isFinite(value.z());
    }

    private static Vec3 zero() {
        return new Vec3(0.0D, 0.0D, 0.0D);
    }

    public record Vec3(double x, double y, double z) {
    }
}
