package me.copimine.endevent.domain;

/**
 * Pure, bounded fallback movement for event combat entities.  Paper's
 * Pathfinder remains the primary navigator; this policy only supplies a small
 * horizontal nudge when a path request is rejected or temporarily unavailable.
 */
public final class CombatMovementPolicy {
    public static final double MAX_COMBAT_STEP_BLOCKS = 0.20D;

    private CombatMovementPolicy() {
    }

    /**
     * Return one horizontal step toward a target.  The step never contains a
     * vertical component and is capped so it cannot become a teleport during a
     * lag spike or an unusually large speed value.
     */
    public static Step stepTowards(double fromX, double fromY, double fromZ,
                                   double targetX, double targetY, double targetZ,
                                   double speed) {
        if (!finite(fromX) || !finite(fromY) || !finite(fromZ)
                || !finite(targetX) || !finite(targetY) || !finite(targetZ)
                || !finite(speed) || speed <= 0.0D) {
            return Step.ZERO;
        }
        double deltaX = targetX - fromX;
        double deltaZ = targetZ - fromZ;
        double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        if (!finite(distance) || distance <= 0.001D) {
            return Step.ZERO;
        }
        double requested = Math.min(MAX_COMBAT_STEP_BLOCKS, Math.max(0.06D, speed * 0.12D));
        double amount = Math.min(distance, requested);
        return new Step(deltaX / distance * amount, 0.0D, deltaZ / distance * amount);
    }

    /**
     * Validate the prospective feet position against the arena leash.  The
     * Bukkit adapter performs block collision checks separately because only it
     * can inspect the live world.
     */
    public static boolean withinBounds(double anchorX, double anchorY, double anchorZ,
                                       double x, double y, double z,
                                       double radius, double verticalRadius,
                                       double minimumCoreDistance) {
        if (!finite(anchorX) || !finite(anchorY) || !finite(anchorZ)
                || !finite(x) || !finite(y) || !finite(z)
                || !finite(radius) || radius <= 0.0D
                || !finite(verticalRadius) || verticalRadius < 0.0D
                || !finite(minimumCoreDistance) || minimumCoreDistance < 0.0D) {
            return false;
        }
        double deltaX = x - anchorX;
        double deltaZ = z - anchorZ;
        double distanceSquared = deltaX * deltaX + deltaZ * deltaZ;
        return distanceSquared <= radius * radius
                && distanceSquared >= minimumCoreDistance * minimumCoreDistance
                && Math.abs(y - anchorY) <= verticalRadius;
    }

    public record Step(double x, double y, double z) {
        public static final Step ZERO = new Step(0.0D, 0.0D, 0.0D);

        public double horizontalLength() {
            return Math.sqrt(x * x + z * z);
        }
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
