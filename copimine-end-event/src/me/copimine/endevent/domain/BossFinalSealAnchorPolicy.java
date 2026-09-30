package me.copimine.endevent.domain;

/** Position contract for the boss's final seal phase. */
public final class BossFinalSealAnchorPolicy {
    public static final double DEFAULT_TOLERANCE = 0.15D;

    private BossFinalSealAnchorPolicy() {
    }

    public static boolean shouldPin(BossPhase phase) {
        return phase == BossPhase.LAST_SEAL;
    }

    public static boolean isAtCore(double x, double y, double z,
                                   double coreX, double coreY, double coreZ,
                                   double tolerance) {
        double safeTolerance = Double.isFinite(tolerance)
                ? Math.max(0.01D, tolerance) : DEFAULT_TOLERANCE;
        double dx = x - coreX;
        double dy = y - coreY;
        double dz = z - coreZ;
        return Double.isFinite(dx) && Double.isFinite(dy) && Double.isFinite(dz)
                && dx * dx + dy * dy + dz * dz <= safeTolerance * safeTolerance;
    }
}
