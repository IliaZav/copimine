package me.copimine.endevent.domain;

/** Pure policy for preventing a lethal hit from skipping the final boss phase. */
public final class BossThresholdPolicy {
    private BossThresholdPolicy() {
    }

    public static Decision evaluate(
            double currentHealth,
            double incomingDamage,
            double maxHealth,
            double halfThreshold,
            double finalThreshold,
            double finalHealth,
            boolean halfAlreadyTriggered,
            boolean finalAlreadyTriggered) {
        double safeMax = positive(maxHealth, 1.0D);
        double current = clamp(currentHealth, 0.0D, safeMax);
        if (finalAlreadyTriggered) {
            return new Decision(false, false, current);
        }
        double damage = Math.max(0.0D, finite(incomingDamage) ? incomingDamage : 0.0D);
        double projected = Math.max(0.0D, current - damage);
        double finalLimit = clamp(finalThreshold, 0.0D, safeMax);
        double halfLimit = clamp(halfThreshold, finalLimit, safeMax);
        if (!halfAlreadyTriggered && projected <= halfLimit) {
            return new Decision(true, false, halfLimit);
        }
        if (projected <= finalLimit) {
            return new Decision(false, true, clamp(finalHealth, 1.0D, safeMax));
        }
        return new Decision(false, false, projected);
    }

    private static double positive(double value, double fallback) {
        return finite(value) && value > 0.0D ? value : fallback;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, finite(value) ? value : minimum));
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    public record Decision(boolean triggerHalf, boolean triggerFinal, double appliedHealth) {
    }
}
