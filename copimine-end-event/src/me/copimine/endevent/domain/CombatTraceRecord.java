package me.copimine.endevent.domain;

import java.util.Locale;
import java.util.UUID;

/**
 * Immutable, server-side observation of one damage transaction.  This is
 * diagnostic data only: recording a trace can never accept, reject or modify
 * a Bukkit damage event.
 */
public record CombatTraceRecord(
        long tick,
        long observedAtMillis,
        UUID attackerId,
        UUID victimId,
        String attackerKind,
        String cause,
        double rawDamage,
        double finalDamage,
        boolean cancelledBefore,
        boolean cancelledAfter,
        int noDamageTicks,
        int maximumNoDamageTicks,
        double lastDamage,
        double healthBefore,
        double nextTickHealth,
        EventPhase phase,
        BossAbilityState abilityState,
        boolean shielded,
        double mspt,
        boolean authoritativeApplied) {

    /** Backward-compatible constructor for non-authoritative/native traces. */
    public CombatTraceRecord(long tick, long observedAtMillis,
                             UUID attackerId, UUID victimId,
                             String attackerKind, String cause,
                             double rawDamage, double finalDamage,
                             boolean cancelledBefore, boolean cancelledAfter,
                             int noDamageTicks, int maximumNoDamageTicks,
                             double lastDamage, double healthBefore,
                             double nextTickHealth, EventPhase phase,
                             BossAbilityState abilityState, boolean shielded,
                             double mspt) {
        this(tick, observedAtMillis, attackerId, victimId, attackerKind, cause,
                rawDamage, finalDamage, cancelledBefore, cancelledAfter,
                noDamageTicks, maximumNoDamageTicks, lastDamage, healthBefore,
                nextTickHealth, phase, abilityState, shielded, mspt, false);
    }

    public CombatTraceRecord {
        tick = Math.max(0L, tick);
        observedAtMillis = Math.max(0L, observedAtMillis);
        attackerKind = normalized(attackerKind, "UNKNOWN");
        cause = normalized(cause, "UNKNOWN");
        rawDamage = finiteNonNegative(rawDamage);
        finalDamage = finiteNonNegative(finalDamage);
        noDamageTicks = Math.max(0, noDamageTicks);
        maximumNoDamageTicks = Math.max(0, maximumNoDamageTicks);
        lastDamage = finiteNonNegative(lastDamage);
        healthBefore = finiteNonNegative(healthBefore);
        nextTickHealth = finiteNonNegative(nextTickHealth);
        phase = phase == null ? EventPhase.RECOVERY_REQUIRED : phase;
        abilityState = abilityState == null ? BossAbilityState.NONE : abilityState;
        mspt = finiteNonNegative(mspt);
    }

    public static CombatTraceRecord open(long tick, long observedAtMillis,
                                         UUID attackerId, UUID victimId,
                                         String attackerKind, String cause,
                                         double rawDamage, double finalDamage,
                                         boolean cancelledBefore,
                                         int noDamageTicks, int maximumNoDamageTicks,
                                         double lastDamage, double healthBefore,
                                         EventPhase phase, BossAbilityState abilityState,
                                         boolean shielded, double mspt) {
        return new CombatTraceRecord(tick, observedAtMillis, attackerId, victimId,
                attackerKind, cause, rawDamage, finalDamage, cancelledBefore, cancelledBefore,
                noDamageTicks, maximumNoDamageTicks, lastDamage, healthBefore, healthBefore,
                phase, abilityState, shielded, mspt, false);
    }

    public CombatTraceRecord close(boolean cancelledAfter, double nextTickHealth) {
        return close(cancelledAfter, nextTickHealth, false);
    }

    public CombatTraceRecord close(boolean cancelledAfter, double nextTickHealth,
                                   boolean authoritativeApplied) {
        return new CombatTraceRecord(tick, observedAtMillis, attackerId, victimId,
                attackerKind, cause, rawDamage, finalDamage, cancelledBefore, cancelledAfter,
                noDamageTicks, maximumNoDamageTicks, lastDamage, healthBefore, nextTickHealth,
                phase, abilityState, shielded, mspt, authoritativeApplied);
    }

    /** The exact amount observed on the entity between the two checkpoints. */
    public double actualHealthDelta() {
        return Math.max(0.0D, healthBefore - nextTickHealth);
    }

    /** Health Paper would be expected to leave after one accepted final hit. */
    public double expectedHealth() {
        return Math.max(0.0D, healthBefore - finalDamage);
    }

    /** True only when this trace has a real accepted health mutation. */
    public boolean accepted() {
        return authoritativeApplied || !cancelledAfter && actualHealthDelta() > 0.0001D;
    }

    /** Human-readable authority for the final health mutation. */
    public String authority() {
        if (authoritativeApplied) return "REAL_ENTITY_HEALTH";
        if (accepted()) return "NATIVE_PAPER";
        if (shielded) return "SHIELD_OR_POLICY";
        if (cancelledAfter) return "CANCELLED";
        return "NONE";
    }

    public CombatTraceDiagnosis diagnosis() {
        return CombatTraceDiagnosis.classify(this);
    }

    public String toLogLine() {
        return String.format(Locale.ROOT,
                "COMBAT_TRACE tick=%d at=%d attacker=%s victim=%s attacker_kind=%s cause=%s raw=%.3f final=%.3f"
                        + " cancelled_before=%s cancelled_after=%s no_damage_ticks=%d max_no_damage_ticks=%d"
                        + " last_damage=%.3f health_before=%.3f expected_health=%.3f health_after=%.3f"
                        + " health_next_tick=%.3f accepted=%s authority=%s phase=%s ability=%s shielded=%s mspt=%.2f diagnosis=%s",
                tick, observedAtMillis, id(attackerId), id(victimId), attackerKind, cause,
                rawDamage, finalDamage, cancelledBefore, cancelledAfter, noDamageTicks,
                maximumNoDamageTicks, lastDamage, healthBefore, expectedHealth(), nextTickHealth,
                nextTickHealth, accepted(), authority(), phase, abilityState, shielded, mspt,
                diagnosis());
    }

    private static String id(UUID value) {
        return value == null ? "none" : value.toString();
    }

    private static String normalized(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
    }

    private static double finiteNonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0.0D, value) : 0.0D;
    }
}
