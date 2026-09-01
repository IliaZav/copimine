package me.copimine.endevent.domain;

/** Pure finite-state rules for the player-triggered boss defeat sequence. */
public final class BossDefeatCinematicPolicy {
    public static final int TELEGRAPH_TICKS = 16;
    public static final int COLLAPSE_TICK = 24;
    public static final int COMMIT_TICK = 40;
    public static final int TOTAL_TICKS = 45;

    private BossDefeatCinematicPolicy() {
    }

    public static boolean canStart(boolean officialKill, boolean alreadyStarted,
                                   boolean bossAlive, boolean playerOwnedHit) {
        return officialKill && !alreadyStarted && bossAlive && playerOwnedHit;
    }

    public static Phase phaseAt(long elapsedTicks) {
        long elapsed = Math.max(0L, elapsedTicks);
        if (elapsed < TELEGRAPH_TICKS) {
            return Phase.TELEGRAPH;
        }
        if (elapsed < COLLAPSE_TICK) {
            return Phase.CHARGE;
        }
        if (elapsed < COMMIT_TICK) {
            return Phase.COLLAPSE;
        }
        if (elapsed < TOTAL_TICKS) {
            return Phase.FINAL_FLASH;
        }
        return Phase.COMPLETE;
    }

    public static boolean shouldCommit(long elapsedTicks) {
        return elapsedTicks == COMMIT_TICK;
    }

    /**
     * A process restart cannot resume the scheduler that was showing the
     * player finisher.  The durable BOSS_FINISH + zero virtual HP boundary is
     * therefore finalized immediately, while a positive pool remains a normal
     * damageable boss and an already committed victory stays idempotent.
     */
    public static boolean shouldFinalizeAfterRestart(boolean bossFinishPhase,
                                                     boolean officialDeathCommitted,
                                                     boolean bossAlive,
                                                     double virtualHealth) {
        return bossFinishPhase && !officialDeathCommitted && bossAlive
                && Double.isFinite(virtualHealth) && virtualHealth <= 0.0D;
    }

    public enum Phase {
        TELEGRAPH,
        CHARGE,
        COLLAPSE,
        FINAL_FLASH,
        COMPLETE
    }
}
