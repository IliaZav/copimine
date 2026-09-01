package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;

/** Pure transitions for health stages, spell availability and one-shot Judgment. */
public final class BossStagePolicy {
    private BossStagePolicy() {
    }

    public static BossStage stageFor(double health, boolean judgmentTriggered) {
        return stageFor(health, BossHealthScalingPolicy.BASE_HEALTH, judgmentTriggered);
    }

    /** Resolve a stage from a percentage of the active virtual HP pool. */
    public static BossStage stageFor(double health, double maxHealth, boolean judgmentTriggered) {
        double safeMax = positive(maxHealth, BossHealthScalingPolicy.BASE_HEALTH);
        double safeHealth = finite(health) ? Math.max(0.0D, health) : 0.0D;
        double fraction = safeHealth / safeMax;
        if (fraction > 0.80D) {
            return BossStage.AWAKENING;
        }
        if (fraction > 0.60D) {
            return BossStage.HUNTER;
        }
        if (fraction > 0.40D) {
            return BossStage.DISTORTION;
        }
        if (fraction > 0.20D) {
            return BossStage.ABSORPTION;
        }
        return BossStage.CATASTROPHE;
    }

    public static StageTransition transition(BossStage previous, double health, boolean judgmentTriggered) {
        return transition(previous, health, BossHealthScalingPolicy.BASE_HEALTH, judgmentTriggered);
    }

    public static StageTransition transition(BossStage previous, double health, double maxHealth,
                                              boolean judgmentTriggered) {
        BossStage requested = stageFor(health, maxHealth, judgmentTriggered);
        boolean judgment = !judgmentTriggered && finite(health)
                && health <= judgmentThreshold(maxHealth);
        // Physical Bukkit health is a projection of the authoritative virtual
        // value and can briefly recover while a cast, reconnect, or another
        // plugin is being reconciled.  Named combat phases are one-way: a
        // transient increase must never undo mechanics players have already
        // learned or re-arm an earlier phase's spells.
        BossStage current = previous != null && requested.ordinal() < previous.ordinal()
                ? previous : requested;
        List<BossStage> entered = new ArrayList<>();
        if (previous == null) {
            entered.add(current);
        } else if (previous != current) {
            for (int ordinal = previous.ordinal() + 1; ordinal <= current.ordinal(); ordinal++) {
                entered.add(BossStage.values()[ordinal]);
            }
        }
        return new StageTransition(current, List.copyOf(entered), judgment);
    }

    public static List<EndRiftAiPolicy.BossSpell> spellPool(BossStage stage) {
        if (stage == null) {
            return List.of();
        }
        return switch (stage) {
            case AWAKENING -> List.of(
                    EndRiftAiPolicy.BossSpell.VOID_BLAST,
                    EndRiftAiPolicy.BossSpell.RIFT_PROJECTILE,
                    EndRiftAiPolicy.BossSpell.RIFT_ARROWS);
            case HUNTER -> List.of(
                    EndRiftAiPolicy.BossSpell.VOID_BLAST,
                    EndRiftAiPolicy.BossSpell.RIFT_PROJECTILE,
                    EndRiftAiPolicy.BossSpell.RIFT_ARROWS,
                    EndRiftAiPolicy.BossSpell.VOID_MARK,
                    EndRiftAiPolicy.BossSpell.SUMMON_SERVANTS);
            case DISTORTION -> List.of(
                    EndRiftAiPolicy.BossSpell.VOID_BLAST,
                    EndRiftAiPolicy.BossSpell.RIFT_PROJECTILE,
                    EndRiftAiPolicy.BossSpell.RIFT_ARROWS,
                    EndRiftAiPolicy.BossSpell.VOID_MARK,
                    EndRiftAiPolicy.BossSpell.WILL_DISTORTION,
                    EndRiftAiPolicy.BossSpell.RIFT_OBELISKS,
                    EndRiftAiPolicy.BossSpell.SUMMON_SERVANTS);
            case ABSORPTION -> List.of(
                    EndRiftAiPolicy.BossSpell.VOID_BLAST,
                    EndRiftAiPolicy.BossSpell.RIFT_PROJECTILE,
                    EndRiftAiPolicy.BossSpell.RIFT_ARROWS,
                    EndRiftAiPolicy.BossSpell.VOID_MARK,
                    EndRiftAiPolicy.BossSpell.SUMMON_SERVANTS);
            case CATASTROPHE -> List.of(
                    EndRiftAiPolicy.BossSpell.VOID_BLAST,
                    EndRiftAiPolicy.BossSpell.RIFT_PROJECTILE,
                    EndRiftAiPolicy.BossSpell.RIFT_ARROWS,
                    EndRiftAiPolicy.BossSpell.VOID_MARK,
                    EndRiftAiPolicy.BossSpell.WILL_DISTORTION,
                    EndRiftAiPolicy.BossSpell.SUMMON_SERVANTS,
                    EndRiftAiPolicy.BossSpell.ARENA_INFERNO);
        };
    }

    /**
     * Per-stage pathing speed.  Absorption deliberately slows the boss for
     * its readable channel/recovery beat; Catastrophe then becomes a bounded,
     * unmistakable escalation instead of five visually identical stages.
     */
    public static double movementSpeed(BossStage stage) {
        return combatProfile(stage, false).movementSpeed();
    }

    /**
     * Return the complete bounded combat posture for one named stage.  Keeping
     * these values together prevents the live adapter from accidentally
     * applying a faster path speed without also applying the matching cooldown
     * and entity budget rules.
     *
     * <p>The second argument means that the five-second Absorption channel has
     * completed.  It is deliberately not inferred from the stage alone: a
     * restarted fight can be in the Absorption health band while its channel
     * is still active.</p>
     */
    public static CombatProfile combatProfile(BossStage stage, boolean absorptionCompleted) {
        BossStage safeStage = stage == null ? BossStage.AWAKENING : stage;
        return switch (safeStage) {
            case AWAKENING -> new CombatProfile(
                    0.95D, 1.00D, 1.00D, 1.00D, 0.0D, 0.0D, 4);
            case HUNTER -> new CombatProfile(
                    1.05D, 0.90D, 0.80D, 0.80D, 0.5D, 0.0D, 4);
            case DISTORTION -> new CombatProfile(
                    1.18D, 0.82D, 0.88D, 0.90D, 1.0D, 0.0D, 4);
            case ABSORPTION -> absorptionCompleted
                    ? new CombatProfile(
                    1.08D, 0.82D, 0.85D, 0.85D, 2.0D, 4.0D, 4)
                    : new CombatProfile(
                    0.92D, 1.00D, 1.00D, 1.00D, 1.0D, 0.0D, 4);
            case CATASTROPHE -> new CombatProfile(
                    1.28D, 0.70D, 0.65D, 0.75D, 3.0D, 0.0D, 3);
        };
    }

    public static double judgmentThreshold() {
        return judgmentThreshold(BossHealthScalingPolicy.BASE_HEALTH);
    }

    public static double judgmentThreshold(double maxHealth) {
        return BossHealthScalingPolicy.judgmentThreshold(maxHealth);
    }

    /** Upper boundary for a stage, expressed in the active pool's HP units. */
    public static double upperThreshold(BossStage stage, double maxHealth) {
        double safeMax = positive(maxHealth, BossHealthScalingPolicy.BASE_HEALTH);
        if (stage == null) {
            return safeMax;
        }
        return switch (stage) {
            case AWAKENING -> safeMax;
            case HUNTER -> safeMax * 0.80D;
            case DISTORTION -> safeMax * 0.60D;
            case ABSORPTION -> safeMax * 0.40D;
            case CATASTROPHE -> safeMax * 0.20D;
        };
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
            spellCooldownMultiplier = boundedMultiplier(spellCooldownMultiplier);
            teleportCooldownMultiplier = boundedMultiplier(teleportCooldownMultiplier);
            targetRotationMultiplier = boundedMultiplier(targetRotationMultiplier);
            meleeDamageBonus = finite(meleeDamageBonus) ? Math.max(0.0D, meleeDamageBonus) : 0.0D;
            nextMeleeAttackBonus = finite(nextMeleeAttackBonus)
                    ? Math.max(0.0D, nextMeleeAttackBonus) : 0.0D;
            summonCap = Math.max(0, summonCap);
        }

        private static double boundedMultiplier(double value) {
            return finite(value) ? Math.max(0.50D, Math.min(1.25D, value)) : 1.0D;
        }
    }

    public record StageTransition(BossStage current, List<BossStage> entered, boolean triggerJudgment) {
        public StageTransition {
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
