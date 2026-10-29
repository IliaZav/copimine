package me.copimine.endevent.domain;

/**
 * Spec 6.4: the final-phase conditional regeneration is small and capped for
 * the whole encounter generation. It only ever runs while real guardian
 * shields are alive, so a zero-guardian vulnerability state never heals.
 */
public final class GuardianRegenerationPolicy {
    /** Spec 6.4: "capped around 24-25% of boss maximum HP". */
    public static final double CAP_FRACTION = 0.25D;
    /** One small step per second keeps the swing readable in combat. */
    public static final long STEP_INTERVAL_MILLIS = 1_000L;
    /** 1% of maximum health per second reaches the 25% cap in 25 active seconds. */
    public static final double STEP_FRACTION = 0.01D;

    private GuardianRegenerationPolicy() {
    }

    public static double cap(double bossMaxHealth) {
        return Double.isFinite(bossMaxHealth) && bossMaxHealth > 0.0D
                ? bossMaxHealth * CAP_FRACTION : 0.0D;
    }

    public static double stepBudget(double bossMaxHealth) {
        return Double.isFinite(bossMaxHealth) && bossMaxHealth > 0.0D
                ? bossMaxHealth * STEP_FRACTION : 0.0D;
    }

    /**
     * Health this step may actually restore: never past the generation cap,
     * never past the boss's own maximum, zero when anything is unusable.
     */
    public static double step(double bossMaxHealth, double currentHealth,
                              double grantedThisGeneration) {
        if (!Double.isFinite(bossMaxHealth) || bossMaxHealth <= 0.0D
                || !Double.isFinite(currentHealth) || currentHealth < 0.0D
                || !Double.isFinite(grantedThisGeneration) || grantedThisGeneration < 0.0D) {
            return 0.0D;
        }
        double remainingCap = Math.max(0.0D, cap(bossMaxHealth) - grantedThisGeneration);
        double missingHealth = Math.max(0.0D, bossMaxHealth - currentHealth);
        return Math.min(stepBudget(bossMaxHealth), Math.min(remainingCap, missingHealth));
    }
}