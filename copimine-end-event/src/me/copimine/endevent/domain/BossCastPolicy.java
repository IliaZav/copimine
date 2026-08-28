package me.copimine.endevent.domain;

/**
 * Single authority for the boss cast damage gate.  An active deadline is the
 * only reason to be invulnerable; an expired or missing deadline is repaired
 * to a normal damageable state.
 */
public final class BossCastPolicy {
    private BossCastPolicy() {
    }

    public static boolean damageAllowed(BossCastState state, long nowMillis, long deadlineMillis) {
        if (state == null || state == BossCastState.NONE || state == BossCastState.EXHAUSTED) {
            return true;
        }
        return deadlineMillis <= 0L || nowMillis >= deadlineMillis;
    }

    public static Reconciled reconcile(BossCastState state, long nowMillis, long deadlineMillis) {
        if (state == null || state == BossCastState.NONE || state == BossCastState.EXHAUSTED) {
            return new Reconciled(state == null ? BossCastState.NONE : state, 0L, false);
        }
        if (deadlineMillis > 0L && nowMillis < deadlineMillis) {
            return new Reconciled(state, deadlineMillis, true);
        }
        return new Reconciled(BossCastState.NONE, 0L, false);
    }

    public record Reconciled(BossCastState state, long deadlineMillis, boolean invulnerable) {
        public Reconciled {
            state = state == null ? BossCastState.NONE : state;
            deadlineMillis = Math.max(0L, deadlineMillis);
        }
    }
}
