package me.copimine.endevent.domain;

/**
 * The V2 boss transaction is deliberately about the real Bukkit health
 * value.  The adapter uses this policy to validate and project the approved
 * hit onto the entity's normal damage event without introducing a second HP
 * authority.
 */
public final class BossRealHealthDamagePolicy {
    private BossRealHealthDamagePolicy() {
    }

    public static Result apply(double currentHealth, double finalDamage,
                               BossCastState castState, double maxHealth) {
        double current = V2BossHealthScalingPolicy.clampCurrent(currentHealth, maxHealth);
        if (!Double.isFinite(finalDamage) || finalDamage <= 0.0D || current <= 0.0D) {
            return new Result(current, 0.0D, false, false);
        }
        double multiplier = BossDamagePolicy.incomingDamageMultiplier(castState);
        double effective = finalDamage * multiplier;
        if (!Double.isFinite(effective) || effective <= 0.0D) {
            return new Result(current, 0.0D, false, false);
        }
        double remaining = Math.max(0.0D, current - effective);
        return new Result(remaining, effective, true, remaining <= 0.0D);
    }

    /**
     * Scale the event's base amount so Bukkit's ordinary damage pipeline
     * produces the already-finalized target amount.  Keeping the ratio based
     * on the pre-adjustment final amount preserves armor/protection modifiers
     * while applying the EXHAUSTED multiplier exactly once.
     */
    public static double scaleBaseDamage(double baseDamage, double finalizedDamage,
                                         double targetFinalDamage) {
        if (!Double.isFinite(baseDamage) || baseDamage <= 0.0D
                || !Double.isFinite(finalizedDamage) || finalizedDamage <= 0.0D
                || !Double.isFinite(targetFinalDamage) || targetFinalDamage <= 0.0D) {
            return 0.0D;
        }
        double scaled = baseDamage * (targetFinalDamage / finalizedDamage);
        return Double.isFinite(scaled) && scaled > 0.0D ? scaled : 0.0D;
    }

    public record Result(double remainingHealth, double appliedDamage,
                         boolean applied, boolean lethal) {
    }
}
