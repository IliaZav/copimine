package me.copimine.endevent.domain;

/** Pure bounded movement and damage rules for the Wave 6 Gravity Well. */
public final class RitualZoneEffectPolicy {
    public static final double RADIUS_BLOCKS = 4.0D;
    public static final double PULL_PER_UPDATE = 0.12D;

    private RitualZoneEffectPolicy() {
    }

    public static Result effect(boolean insideActiveZone, boolean damagePulseDue) {
        if (!insideActiveZone) {
            return new Result(false, false, false);
        }
        return new Result(true, true, damagePulseDue);
    }

    public static boolean contains(double centerX, double centerZ,
                                   double playerX, double playerZ,
                                   double radius) {
        if (!Double.isFinite(centerX) || !Double.isFinite(centerZ)
                || !Double.isFinite(playerX) || !Double.isFinite(playerZ)
                || !Double.isFinite(radius) || radius < 0.0D) {
            return false;
        }
        double dx = playerX - centerX;
        double dz = playerZ - centerZ;
        return dx * dx + dz * dz <= radius * radius;
    }

    /** Return a bounded horizontal velocity delta toward the well center. */
    public static Pull pull(double playerX, double playerZ,
                            double centerX, double centerZ) {
        if (!Double.isFinite(playerX) || !Double.isFinite(playerZ)
                || !Double.isFinite(centerX) || !Double.isFinite(centerZ)) {
            return new Pull(0.0D, 0.0D);
        }
        double dx = centerX - playerX;
        double dz = centerZ - playerZ;
        double length = Math.hypot(dx, dz);
        if (length < 1.0E-9D) {
            return new Pull(0.0D, 0.0D);
        }
        return new Pull(dx / length * PULL_PER_UPDATE,
                dz / length * PULL_PER_UPDATE);
    }

    public record Result(boolean slowness, boolean pull, boolean periodicDamage) {
    }

    public record Pull(double x, double z) {
    }
}
