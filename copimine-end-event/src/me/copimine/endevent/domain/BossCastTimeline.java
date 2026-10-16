package me.copimine.endevent.domain;

/**
 * Deadline reconciliation for short boss ability animations. The timeline
 * repairs an expired callback to NONE; it does not decide whether combat
 * damage is allowed.
 */
public final class BossCastTimeline {
    private BossCastTimeline() {
    }

    public static Reconciled reconcile(BossAbilityState state, long nowMillis,
                                       long deadlineMillis) {
        BossAbilityState safe = state == null ? BossAbilityState.NONE : state;
        if (safe == BossAbilityState.NONE) {
            return new Reconciled(safe, 0L, false);
        }
        if (safe == BossAbilityState.RECOVERY) {
            if (deadlineMillis > 0L && nowMillis < deadlineMillis) {
                return new Reconciled(safe, deadlineMillis, true);
            }
            return new Reconciled(BossAbilityState.NONE, 0L, false);
        }
        if (deadlineMillis > 0L && nowMillis < deadlineMillis) {
            return new Reconciled(safe, deadlineMillis, true);
        }
        return new Reconciled(BossAbilityState.NONE, 0L, false);
    }

    public record Reconciled(BossAbilityState state, long deadlineMillis,
                             boolean animationActive) {
        public Reconciled {
            state = state == null ? BossAbilityState.NONE : state;
            deadlineMillis = Math.max(0L, deadlineMillis);
        }

    }
}
