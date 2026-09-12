package me.copimine.endevent.domain;

import java.util.List;

/**
 * Exactly-once transaction for the boss's real entity health.
 *
 * <p>The Bukkit adapter cancels the native event after reading
 * {@code getFinalDamage()}, then commits this result to the entity. Calling
 * this policy once for each incoming event makes same-tick hits compose
 * instead of overwriting one another.</p>
 */
public final class BossRealHealthDamagePolicy {
    private BossRealHealthDamagePolicy() {
    }

    public static Result apply(double currentHealth, double finalDamage,
                               double multiplier, double maxHealth) {
        double current = BossHealthPolicy.clampCurrent(currentHealth, maxHealth);
        if (!Double.isFinite(finalDamage) || finalDamage <= 0.0D || current <= 0.0D) {
            return new Result(current, 0.0D, false, false);
        }
        double safeMultiplier = Double.isFinite(multiplier) && multiplier > 0.0D
                ? multiplier : 1.0D;
        double effective = finalDamage * safeMultiplier;
        if (!Double.isFinite(effective) || effective <= 0.0D) {
            return new Result(current, 0.0D, false, false);
        }
        double remaining = Math.max(0.0D, current - effective);
        return new Result(remaining, effective, true, remaining <= 0.0D);
    }

    public static Result apply(double currentHealth, double finalDamage,
                               BossDamagePolicy.Decision decision, double maxHealth) {
        if (decision == null || !decision.allowed()) {
            return new Result(BossHealthPolicy.clampCurrent(currentHealth, maxHealth),
                    0.0D, false, false);
        }
        return apply(currentHealth, finalDamage, decision.multiplier(), maxHealth);
    }

    /** Apply already-finalized hits in arrival order for the no-lost-update contract. */
    public static Result applyHits(double currentHealth, List<Double> finalDamages,
                                   double maxHealth) {
        double health = BossHealthPolicy.clampCurrent(currentHealth, maxHealth);
        double applied = 0.0D;
        if (finalDamages == null) {
            return new Result(health, applied, false, health <= 0.0D);
        }
        for (Double damage : finalDamages) {
            Result result = apply(health, damage == null ? 0.0D : damage,
                    1.0D, maxHealth);
            health = result.remainingHealth();
            applied += result.appliedDamage();
        }
        return new Result(health, applied, applied > 0.0D, health <= 0.0D);
    }

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
