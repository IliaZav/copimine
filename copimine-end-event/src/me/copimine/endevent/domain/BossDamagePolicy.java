package me.copimine.endevent.domain;

/** Fail-closed, time-bounded damage policy for boss casts. */
public final class BossDamagePolicy {
    private BossDamagePolicy() {
    }

    public static boolean damageAllowed(BossStage stage, BossCastState castState,
                                        long nowMillis, long deadlineMillis) {
        if (castState == null || castState == BossCastState.NONE || castState == BossCastState.EXHAUSTED) {
            return true;
        }
        if (deadlineMillis <= 0L) {
            return true;
        }
        return nowMillis >= deadlineMillis;
    }

    public static double incomingDamageMultiplier(BossCastState castState) {
        return castState == BossCastState.EXHAUSTED ? 1.5D : 1.0D;
    }

    /**
     * Apply the exhausted-window bonus to the already-finalized incoming hit
     * exactly once.  The Bukkit adapter must call this before it cancels the
     * original event; reading getFinalDamage() after changing the event would
     * apply the multiplier twice.
     */
    public static double applyIncomingDamage(double incomingDamage, BossCastState castState) {
        if (!Double.isFinite(incomingDamage) || incomingDamage <= 0.0D) {
            return 0.0D;
        }
        double adjusted = incomingDamage * incomingDamageMultiplier(castState);
        return Double.isFinite(adjusted) ? adjusted : 0.0D;
    }
}
