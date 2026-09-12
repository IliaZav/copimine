package me.copimine.endevent.domain;

/** A conservative explanation for a completed combat trace. */
public enum CombatTraceDiagnosis {
    APPLIED,
    CANCELLED,
    HURT_RESISTANCE,
    MAIN_THREAD_STALL,
    RACE_OR_REWRITE,
    OTHER_PLUGIN_OR_POLICY,
    NO_EVENT_OBSERVED,
    UNKNOWN;

    public static CombatTraceDiagnosis classify(CombatTraceRecord trace) {
        if (trace == null) {
            return NO_EVENT_OBSERVED;
        }
        double observedDelta = Math.max(0.0D, trace.healthBefore() - trace.nextTickHealth());
        if (trace.authoritativeApplied()) {
            if (trace.actualHealthDelta() > 0.0001D
                    && Math.abs(trace.actualHealthDelta() - trace.finalDamage())
                    <= Math.max(0.25D, trace.finalDamage() * 0.05D)) {
                return APPLIED;
            }
            return RACE_OR_REWRITE;
        }
        if (trace.cancelledAfter()) {
            return trace.shielded() ? OTHER_PLUGIN_OR_POLICY : CANCELLED;
        }
        if (observedDelta > 0.0001D) {
            if (trace.finalDamage() > 0.0D
                    && Math.abs(observedDelta - trace.finalDamage()) > Math.max(0.25D, trace.finalDamage() * 0.75D)) {
                return RACE_OR_REWRITE;
            }
            return APPLIED;
        }
        if (trace.shielded()) {
            return OTHER_PLUGIN_OR_POLICY;
        }
        if (trace.mspt() >= 50.0D) {
            return MAIN_THREAD_STALL;
        }
        if (trace.noDamageTicks() > 0 && trace.maximumNoDamageTicks() > 0
                && trace.noDamageTicks() >= trace.maximumNoDamageTicks()) {
            return HURT_RESISTANCE;
        }
        if (trace.finalDamage() <= 0.0D) {
            return OTHER_PLUGIN_OR_POLICY;
        }
        return UNKNOWN;
    }
}
