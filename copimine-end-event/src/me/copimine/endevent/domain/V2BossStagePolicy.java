package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;

/** Pure, monotonic official boss stage transitions and bounded spell availability. */
public final class V2BossStagePolicy {
    private V2BossStagePolicy() {
    }

    public static StageTransition transition(V2BossStage previous, double health, double maxHealth) {
        V2BossStage requested = V2BossStage.forHealth(health, maxHealth);
        V2BossStage current = previous == null || requested.rank() >= previous.rank()
                ? requested : previous;
        List<V2BossStage> entered = new ArrayList<>();
        if (previous == null) {
            entered.add(current);
        } else if (current.rank() > previous.rank()) {
            for (int rank = previous.rank() + 1; rank <= current.rank(); rank++) {
                entered.add(V2BossStage.values()[rank]);
            }
        }
        return new StageTransition(current, List.copyOf(entered));
    }

    public static boolean damageAllowed(V2BossStage stage, boolean boundedCastActive) {
        return stage != null && !boundedCastActive;
    }

    public static List<EndRiftAiPolicy.BossSpell> spellPool(V2BossStage stage) {
        if (stage == null) {
            return List.of();
        }
        return switch (stage) {
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
                    EndRiftAiPolicy.BossSpell.WILL_DISTORTION,
                    EndRiftAiPolicy.BossSpell.SUMMON_SERVANTS);
            case RAGE -> List.of(
                    EndRiftAiPolicy.BossSpell.VOID_BLAST,
                    EndRiftAiPolicy.BossSpell.RIFT_PROJECTILE,
                    EndRiftAiPolicy.BossSpell.RIFT_ARROWS,
                    EndRiftAiPolicy.BossSpell.ARENA_INFERNO);
            case LAST_SEAL -> List.of(
                    EndRiftAiPolicy.BossSpell.WILL_DISTORTION,
                    EndRiftAiPolicy.BossSpell.ARENA_INFERNO,
                    EndRiftAiPolicy.BossSpell.FINAL_STRIKE);
        };
    }

    public record StageTransition(V2BossStage current, List<V2BossStage> entered) {
        public StageTransition {
            if (current == null) {
                throw new IllegalArgumentException("current stage is required");
            }
            entered = List.copyOf(entered == null ? List.of() : entered);
        }
    }
}
