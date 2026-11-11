package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Master contract 17.4: the protected Last Seal regeneration was five times too
 * strong (1% per second, 25% total cap). The controlling values are now
 * <b>0.2% of maximum health per second</b> with a <b>5% total cap for the whole
 * encounter generation</b>.
 *
 * <p>Regeneration is allowed only while the cocoon still has living defenders.
 * After the cocoon breaks it stops permanently, and forty consecutive seconds
 * without a living defender must not be able to re-open it.</p>
 */
public final class GuardianRegenerationPolicy {
    /** Contract 17.4: five times lower than the retired 25% cap. */
    public static final double CAP_FRACTION = 0.05D;
    /** One small step per second keeps the swing readable in combat. */
    public static final long STEP_INTERVAL_MILLIS = 1_000L;
    /** Contract 17.4: five times lower than the retired 1% per second. */
    public static final double STEP_FRACTION = 0.002D;
    /** 0.2% per second reaches the 5% cap in twenty-five active seconds. */
    public static final long STEPS_TO_CAP = 25L;

    private GuardianRegenerationPolicy() {
    }

    public static double cap(double bossMaxHealth) {
        return finite(bossMaxHealth) ? Math.max(0.0D, bossMaxHealth) * CAP_FRACTION : 0.0D;
    }

    public static double stepBudget(double bossMaxHealth) {
        return finite(bossMaxHealth) ? Math.max(0.0D, bossMaxHealth) * STEP_FRACTION : 0.0D;
    }

    /**
     * Health this step may actually restore: never past the generation cap,
     * never past the boss's own maximum, zero when anything is unusable.
     */
    public static double step(double bossMaxHealth, double currentHealth,
                              double grantedThisGeneration) {
        if (!finite(bossMaxHealth) || bossMaxHealth <= 0.0D
                || !finite(currentHealth) || currentHealth < 0.0D
                || !finite(grantedThisGeneration) || grantedThisGeneration < 0.0D) {
            return 0.0D;
        }
        double remainingCap = Math.max(0.0D, cap(bossMaxHealth) - grantedThisGeneration);
        double missingHealth = Math.max(0.0D, bossMaxHealth - currentHealth);
        return Math.min(stepBudget(bossMaxHealth), Math.min(remainingCap, missingHealth));
    }

    /**
     * Full gate for one regeneration step.
     *
     * @param phaseIsFinalSeal          BossPhase.LAST_SEAL only
     * @param cocoonBrokenThisGeneration a broken cocoon is permanent
     * @param livingDefenders           the Kagune that own the cocoon shield
     */
    public static boolean allowed(boolean phaseIsFinalSeal,
                                  boolean cocoonBrokenThisGeneration,
                                  int livingDefenders) {
        return phaseIsFinalSeal && !cocoonBrokenThisGeneration && livingDefenders > 0;
    }

    /** Convenience gate returning the actual heal for one step. */
    public static double stepGate(boolean phaseIsFinalSeal,
                                  boolean cocoonBrokenThisGeneration,
                                  int livingDefenders, double bossMaxHealth,
                                  double currentHealth, double grantedThisGeneration) {
        if (!allowed(phaseIsFinalSeal, cocoonBrokenThisGeneration, livingDefenders)) {
            return 0.0D;
        }
        return step(bossMaxHealth, currentHealth, grantedThisGeneration);
    }

    /** Simulate a steady one-second cadence; used by balance and regression proof. */
    public static double totalForActiveSeconds(double bossMaxHealth, double currentHealth,
                                               int activeSeconds) {
        double health = Math.max(0.0D, currentHealth);
        double granted = 0.0D;
        int steps = Math.max(0, activeSeconds);
        for (int index = 0; index < steps; index++) {
            double healed = step(bossMaxHealth, health, granted);
            granted += healed;
            health += healed;
        }
        return granted;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}