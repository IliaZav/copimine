package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;

/** Pure transitions for health stages, spell availability and one-shot Judgment. */
public final class BossStagePolicy {
    private static final double JUDGMENT_THRESHOLD = 250.0D;

    private BossStagePolicy() {
    }

    public static BossStage stageFor(double health, boolean judgmentTriggered) {
        return BossStage.forHealth(health);
    }

    public static StageTransition transition(BossStage previous, double health, boolean judgmentTriggered) {
        BossStage current = stageFor(health, judgmentTriggered);
        List<BossStage> entered = new ArrayList<>();
        if (previous == null) {
            entered.add(current);
        } else if (previous != current) {
            int first = Math.min(previous.ordinal(), current.ordinal());
            int last = Math.max(previous.ordinal(), current.ordinal());
            for (int ordinal = first + 1; ordinal <= last; ordinal++) {
                entered.add(BossStage.values()[ordinal]);
            }
        }
        boolean judgment = !judgmentTriggered && finite(health) && health <= JUDGMENT_THRESHOLD;
        return new StageTransition(current, List.copyOf(entered), judgment);
    }

    public static List<EndRiftAiPolicy.BossSpell> spellPool(BossStage stage) {
        if (stage == null) {
            return List.of();
        }
        return switch (stage) {
            case AWAKENING -> List.of(
                    EndRiftAiPolicy.BossSpell.VOID_BLAST,
                    EndRiftAiPolicy.BossSpell.RIFT_PROJECTILE);
            case HUNTER -> List.of(
                    EndRiftAiPolicy.BossSpell.VOID_BLAST,
                    EndRiftAiPolicy.BossSpell.RIFT_PROJECTILE,
                    EndRiftAiPolicy.BossSpell.VOID_MARK);
            case DISTORTION -> List.of(
                    EndRiftAiPolicy.BossSpell.VOID_BLAST,
                    EndRiftAiPolicy.BossSpell.RIFT_PROJECTILE,
                    EndRiftAiPolicy.BossSpell.VOID_MARK,
                    EndRiftAiPolicy.BossSpell.WILL_DISTORTION,
                    EndRiftAiPolicy.BossSpell.SUMMON_SERVANTS);
            case ABSORPTION -> List.of(EndRiftAiPolicy.BossSpell.VOID_BLAST);
            case CATASTROPHE -> List.of(
                    EndRiftAiPolicy.BossSpell.VOID_BLAST,
                    EndRiftAiPolicy.BossSpell.RIFT_PROJECTILE,
                    EndRiftAiPolicy.BossSpell.VOID_MARK,
                    EndRiftAiPolicy.BossSpell.WILL_DISTORTION,
                    EndRiftAiPolicy.BossSpell.SUMMON_SERVANTS,
                    EndRiftAiPolicy.BossSpell.ARENA_INFERNO);
        };
    }

    public static double judgmentThreshold() {
        return JUDGMENT_THRESHOLD;
    }

    public record StageTransition(BossStage current, List<BossStage> entered, boolean triggerJudgment) {
        public StageTransition {
            entered = List.copyOf(entered == null ? List.of() : entered);
        }
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
