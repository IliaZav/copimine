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
}
