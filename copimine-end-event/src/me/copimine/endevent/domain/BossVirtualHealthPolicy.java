package me.copimine.endevent.domain;

import java.util.List;

/**
 * Authoritative virtual-health arithmetic for the End Rift boss.
 *
 * <p>Paper delivers entity damage events on the server thread, but keeping
 * the subtraction in one pure policy makes the no-lost-hit rule explicit and
 * gives the live adapter a single operation to call for every final hit.</p>
 */
public final class BossVirtualHealthPolicy {
    private BossVirtualHealthPolicy() {
    }

    /** Apply one raw hit, including the cast-state multiplier exactly once. */
    public static HitResult applyHit(double currentHealth, double incomingDamage,
                                     BossCastState castState, double maxHealth) {
        double finalDamage = BossDamagePolicy.applyIncomingDamage(incomingDamage, castState);
        return applyFinalDamage(currentHealth, finalDamage, maxHealth);
    }

    /**
     * Apply already-final damages in arrival order.  Each list entry is an
     * independent hit; no entry overwrites the result of a previous one.
     */
    public static double applyHits(double initialHealth, List<Double> finalDamages,
                                   double maxHealth) {
        double health = clampHealth(initialHealth, maxHealth);
        if (finalDamages == null) {
            return health;
        }
        for (Double damage : finalDamages) {
            health = applyFinalDamage(health, damage == null ? 0.0D : damage, maxHealth)
                    .remainingHealth();
        }
        return health;
    }

    /** Apply one final (already policy-adjusted) damage value. */
    public static HitResult applyFinalDamage(double currentHealth, double finalDamage,
                                             double maxHealth) {
        double safeMax = positive(maxHealth, 1.0D);
        double before = clampHealth(currentHealth, safeMax);
        double applied = finite(finalDamage) ? Math.max(0.0D, finalDamage) : 0.0D;
        double after = Math.max(0.0D, before - applied);
        return new HitResult(before, applied, after);
    }

    private static double clampHealth(double health, double maxHealth) {
        double safeMax = positive(maxHealth, 1.0D);
        return finite(health) ? Math.max(0.0D, Math.min(safeMax, health)) : 0.0D;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static double positive(double value, double fallback) {
        return finite(value) && value > 0.0D ? value : fallback;
    }

    public record HitResult(double previousHealth, double appliedDamage, double remainingHealth) {
    }
}
