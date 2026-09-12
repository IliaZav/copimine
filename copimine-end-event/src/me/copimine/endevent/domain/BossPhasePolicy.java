package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * The single phase policy for the current End Rift boss.
 *
 * <p>Health is the entity's real health. A cast timeline may expose a
 * named, short-lived immunity reason, but a generic cast flag never makes the
 * boss invulnerable.</p>
 */
public final class BossPhasePolicy {
    private BossPhasePolicy() {
    }

    public static BossPhase phaseFor(double health, double maxHealth) {
        return BossPhase.forHealth(health, maxHealth);
    }

    public static StageTransition transition(BossPhase previous, double health, double maxHealth) {
        BossPhase requested = phaseFor(health, maxHealth);
        BossPhase current = previous == null || requested.rank() >= previous.rank()
                ? requested : previous;
        List<BossPhase> entered = new ArrayList<>();
        if (previous == null) {
            entered.add(current);
        } else if (current.rank() > previous.rank()) {
            for (int rank = previous.rank() + 1; rank <= current.rank(); rank++) {
                entered.add(BossPhase.values()[rank]);
            }
        }
        return new StageTransition(current, List.copyOf(entered));
    }

    public static boolean damageAllowed(BossPhase phase, DamageImmunityReason reason) {
        return phase != null && (reason == null || reason == DamageImmunityReason.NONE);
    }

    public static List<EndRiftAiPolicy.BossSpell> spellPool(BossPhase phase) {
        if (phase == null) {
            return List.of();
        }
        return switch (phase) {
            case AWAKENING -> List.of(
                    EndRiftAiPolicy.BossSpell.VOID_BLAST,
                    EndRiftAiPolicy.BossSpell.RIFT_PROJECTILE);
            case HUNT -> List.of(
                    EndRiftAiPolicy.BossSpell.VOID_BLAST,
                    EndRiftAiPolicy.BossSpell.RIFT_PROJECTILE,
                    EndRiftAiPolicy.BossSpell.RIFT_ARROWS,
                    EndRiftAiPolicy.BossSpell.VOID_MARK);
            case RIFT -> List.of(
                    EndRiftAiPolicy.BossSpell.VOID_MARK,
                    EndRiftAiPolicy.BossSpell.RIFT_PROJECTILE,
                    EndRiftAiPolicy.BossSpell.SUMMON_SERVANTS);
            case OVERLOAD -> List.of(
                    EndRiftAiPolicy.BossSpell.VOID_BLAST,
                    EndRiftAiPolicy.BossSpell.RIFT_ARROWS,
                    EndRiftAiPolicy.BossSpell.SUMMON_SERVANTS);
            case RAGE -> List.of(
                    EndRiftAiPolicy.BossSpell.VOID_BLAST,
                    EndRiftAiPolicy.BossSpell.RIFT_PROJECTILE,
                    EndRiftAiPolicy.BossSpell.RIFT_ARROWS,
                    EndRiftAiPolicy.BossSpell.ARENA_INFERNO);
            case LAST_SEAL -> List.of(
                    EndRiftAiPolicy.BossSpell.VOID_MARK,
                    EndRiftAiPolicy.BossSpell.ARENA_INFERNO,
                    EndRiftAiPolicy.BossSpell.FINAL_STRIKE);
        };
    }

    public static double movementSpeed(BossPhase phase) {
        return combatProfile(phase).movementSpeed();
    }

    public static CombatProfile combatProfile(BossPhase phase) {
        BossPhase safe = phase == null ? BossPhase.AWAKENING : phase;
        return switch (safe) {
            case AWAKENING -> new CombatProfile(0.95D, 1.00D, 1.00D, 1.00D, 0.0D, 0.0D, 4);
            case HUNT -> new CombatProfile(1.05D, 0.90D, 0.80D, 0.80D, 0.5D, 0.0D, 4);
            case RIFT -> new CombatProfile(1.18D, 0.82D, 0.88D, 0.90D, 1.0D, 0.0D, 4);
            case OVERLOAD -> new CombatProfile(1.12D, 0.78D, 0.82D, 0.84D, 1.5D, 0.0D, 4);
            case RAGE -> new CombatProfile(1.22D, 0.70D, 0.68D, 0.75D, 2.0D, 0.0D, 3);
            case LAST_SEAL -> new CombatProfile(1.16D, 0.82D, 0.76D, 0.72D, 2.5D, 0.0D, 3);
        };
    }

    /** Upper boundary in the real entity-health domain. */
    public static double upperThreshold(BossPhase phase, double maxHealth) {
        double safeMax = positive(maxHealth, BossHealthPolicy.MIN_HEALTH);
        if (phase == null) {
            return safeMax;
        }
        return switch (phase) {
            case AWAKENING -> safeMax;
            case HUNT -> safeMax * 0.80D;
            case RIFT -> safeMax * 0.60D;
            case OVERLOAD -> safeMax * 0.45D;
            case RAGE -> safeMax * 0.30D;
            case LAST_SEAL -> safeMax * 0.20D;
        };
    }

    public static double lastSealThreshold(double maxHealth) {
        return positive(maxHealth, BossHealthPolicy.MIN_HEALTH) * 0.20D;
    }

    public enum DamageImmunityReason {
        NONE,
        BOSS_CINEMATIC,
        LAST_SEAL_GUARDIANS,
        FINAL_STRIKE_COMMIT,
        ADMIN_TEST_FREEZE
    }

    public record CombatProfile(double movementSpeed,
                                double spellCooldownMultiplier,
                                double teleportCooldownMultiplier,
                                double targetRotationMultiplier,
                                double meleeDamageBonus,
                                double nextMeleeAttackBonus,
                                int summonCap) {
        public CombatProfile {
            movementSpeed = finite(movementSpeed) ? Math.max(0.1D, movementSpeed) : 1.0D;
            spellCooldownMultiplier = bounded(spellCooldownMultiplier);
            teleportCooldownMultiplier = bounded(teleportCooldownMultiplier);
            targetRotationMultiplier = bounded(targetRotationMultiplier);
            meleeDamageBonus = finite(meleeDamageBonus) ? Math.max(0.0D, meleeDamageBonus) : 0.0D;
            nextMeleeAttackBonus = finite(nextMeleeAttackBonus)
                    ? Math.max(0.0D, nextMeleeAttackBonus) : 0.0D;
            summonCap = Math.max(0, summonCap);
        }

        private static double bounded(double value) {
            return finite(value) ? Math.max(0.50D, Math.min(1.25D, value)) : 1.0D;
        }
    }

    public record StageTransition(BossPhase current, List<BossPhase> entered) {
        public StageTransition {
            if (current == null) {
                throw new IllegalArgumentException("current phase is required");
            }
            entered = List.copyOf(entered == null ? List.of() : entered);
        }

    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static double positive(double value, double fallback) {
        return finite(value) && value > 0.0D ? value : fallback;
    }
}
