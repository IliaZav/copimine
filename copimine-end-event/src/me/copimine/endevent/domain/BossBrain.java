package me.copimine.endevent.domain;

import java.util.List;
import java.util.ArrayDeque;
import java.util.UUID;

/** One bounded perception -> target -> intent -> ability decision. */
public final class BossBrain {
    private final ArrayDeque<BossAbilityId> acceptedHistory = new ArrayDeque<>();
    private long historyGeneration = Long.MIN_VALUE;

    public void reset() {
        acceptedHistory.clear();
        historyGeneration = Long.MIN_VALUE;
    }

    public void recordAccepted(BossAbilityId ability, long generation) {
        if (ability == null) return;
        adoptGeneration(generation);
        acceptedHistory.addFirst(ability);
        while (acceptedHistory.size() > 4) acceptedHistory.removeLast();
    }

    private void adoptGeneration(long generation) {
        if (historyGeneration != generation) {
            acceptedHistory.clear();
            historyGeneration = generation;
        }
    }
    public Decision decide(BossPerceptionSnapshot snapshot,
                           List<BossAbilityId> available,
                           BossAbilityId previous,
                           UUID currentTarget,
                           BossHazardBudget budget,
                           long generation,
                           int cursor) {
        adoptGeneration(generation);
        BossIntent intent = BossIntentSelector.choose(snapshot);
        BossTargetSelector.TargetDecision target = BossTargetSelector.choose(snapshot, currentTarget);
        BossAbilitySelector.AbilityDecision ability = BossAbilitySelector.choose(snapshot, intent,
                target.target(), previous, available, List.copyOf(acceptedHistory), budget, generation, cursor);
        return new Decision(target, intent, ability);
    }

    public record Decision(BossTargetSelector.TargetDecision target,
                           BossIntent intent,
                           BossAbilitySelector.AbilityDecision ability) {
    }
}
