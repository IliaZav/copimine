package me.copimine.endevent.domain;

/** Pure timing and safety rules for the boss's one-shot catastrophe attack. */
public final class BossFinalStrikePolicy {
    public static final int TELEGRAPH_TICKS = 20;
    public static final int CHARGE_TICKS = 20;
    public static final int IMPACT_TICK = TELEGRAPH_TICKS + CHARGE_TICKS;
    public static final int IMPACT_TICKS = 6;
    public static final int TOTAL_TICKS = IMPACT_TICK + IMPACT_TICKS;
    public static final double DEFAULT_DAMAGE = 8.0D;
    public static final double MAX_DAMAGE = 12.0D;

    private BossFinalStrikePolicy() {
    }

    public static boolean canStart(BossStage stage, boolean alreadyUsed,
                                   boolean bossAlive, boolean hasTarget) {
        return stage == BossStage.CATASTROPHE && !alreadyUsed && bossAlive && hasTarget;
    }

    public static Phase phaseAt(long elapsedTicks) {
        long elapsed = Math.max(0L, elapsedTicks);
        if (elapsed < TELEGRAPH_TICKS) {
            return Phase.TELEGRAPH;
        }
        if (elapsed < IMPACT_TICK) {
            return Phase.CHARGE;
        }
        if (elapsed < TOTAL_TICKS) {
            return Phase.IMPACT;
        }
        return Phase.COMPLETE;
    }

    public static boolean isImpactTick(long elapsedTicks) {
        return elapsedTicks == IMPACT_TICK;
    }

    public static double validatedDamage(double configuredDamage) {
        if (!Double.isFinite(configuredDamage) || configuredDamage <= 0.0D) {
            return 0.0D;
        }
        return Math.min(MAX_DAMAGE, configuredDamage);
    }

    public enum Phase {
        TELEGRAPH,
        CHARGE,
        IMPACT,
        COMPLETE
    }
}
