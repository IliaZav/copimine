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
        BossCastState castState,
        boolean shielded,
        double mspt) {

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
        castState = castState == null ? BossCastState.NONE : castState;
        mspt = finiteNonNegative(mspt);
    }

    public static CombatTraceRecord open(long tick, long observedAtMillis,
                                         UUID attackerId, UUID victimId,
                                         String attackerKind, String cause,
                                         double rawDamage, double finalDamage,
                                         boolean cancelledBefore,
                                         int noDamageTicks, int maximumNoDamageTicks,
                                         double lastDamage, double healthBefore,
                                         EventPhase phase, BossCastState castState,
                                         boolean shielded, double mspt) {
        return new CombatTraceRecord(tick, observedAtMillis, attackerId, victimId,
                attackerKind, cause, rawDamage, finalDamage, cancelledBefore, cancelledBefore,
                noDamageTicks, maximumNoDamageTicks, lastDamage, healthBefore, healthBefore,
                phase, castState, shielded, mspt);
    }

    public CombatTraceRecord close(boolean cancelledAfter, double nextTickHealth) {
        return new CombatTraceRecord(tick, observedAtMillis, attackerId, victimId,
                attackerKind, cause, rawDamage, finalDamage, cancelledBefore, cancelledAfter,
                noDamageTicks, maximumNoDamageTicks, lastDamage, healthBefore, nextTickHealth,
                phase, castState, shielded, mspt);
    }

    public String toLogLine() {
        return String.format(Locale.ROOT,
                "COMBAT_TRACE tick=%d at=%d attacker=%s victim=%s attacker_kind=%s cause=%s raw=%.3f final=%.3f"
                        + " cancelled_before=%s cancelled_after=%s no_damage_ticks=%d max_no_damage_ticks=%d"
                        + " last_damage=%.3f health_before=%.3f health_next_tick=%.3f phase=%s cast=%s shielded=%s mspt=%.2f",
                tick, observedAtMillis, id(attackerId), id(victimId), attackerKind, cause,
                rawDamage, finalDamage, cancelledBefore, cancelledAfter, noDamageTicks,
                maximumNoDamageTicks, lastDamage, healthBefore, nextTickHealth, phase,
                castState, shielded, mspt);
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
