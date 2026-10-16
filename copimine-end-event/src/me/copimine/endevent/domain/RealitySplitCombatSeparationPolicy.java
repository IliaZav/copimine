package me.copimine.endevent.domain;

/**
 * Keeps a closed Wave 7 mob's preferred combat point inside the player's
 * readable melee band without placing its hitbox directly on the player.
 * World collision remains the Bukkit adapter's responsibility.
 */
public final class RealitySplitCombatSeparationPolicy {
    public static final int WAVE = 7;
    /** A spider-sized hitbox needs a visible block of room beside its target. */
    public static final double DEFAULT_MIN_SEPARATION = 1.75D;

    private RealitySplitCombatSeparationPolicy() {
    }

    /**
     * Pull an overlapping tactical point toward the Core.  This is intentionally
     * a no-op outside a closed Wave 7 room, so opened passages and earlier waves
     * retain their existing movement/knockback rules.
     *
     * @return a corrected point, the original preferred point when it is already
     * separated, or {@code null} for an inactive/invalid request
     */
    public static Point preferSafeTargetPoint(int wave, boolean closedRoom,
                                              double anchorX, double anchorZ,
                                              double targetX, double targetY, double targetZ,
                                              double preferredX, double preferredY,
                                              double preferredZ, double minSeparation) {
        if (wave != WAVE || !closedRoom
                || !finite(anchorX) || !finite(anchorZ)
                || !finite(targetX) || !finite(targetY) || !finite(targetZ)
                || !finite(preferredX) || !finite(preferredY) || !finite(preferredZ)
                || !finite(minSeparation) || minSeparation <= 0.0D) {
            return null;
        }
        double preferredDeltaX = preferredX - targetX;
        double preferredDeltaZ = preferredZ - targetZ;
        if (preferredDeltaX * preferredDeltaX + preferredDeltaZ * preferredDeltaZ
                >= minSeparation * minSeparation) {
            return new Point(preferredX, preferredY, preferredZ);
        }

        double towardAnchorX = anchorX - targetX;
        double towardAnchorZ = anchorZ - targetZ;
        double length = Math.hypot(towardAnchorX, towardAnchorZ);
        if (length < 0.0001D) {
            towardAnchorX = preferredDeltaX;
            towardAnchorZ = preferredDeltaZ;
            length = Math.hypot(towardAnchorX, towardAnchorZ);
        }
        if (length < 0.0001D) {
            towardAnchorX = 1.0D;
            towardAnchorZ = 0.0D;
            length = 1.0D;
        }
        return new Point(targetX + towardAnchorX / length * minSeparation,
                preferredY,
                targetZ + towardAnchorZ / length * minSeparation);
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    public record Point(double x, double y, double z) {
    }
}
