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
        List<BossTargetSelector.TargetDecision> candidates =
                BossTargetSelector.ranked(snapshot, currentTarget, cursor);
        BossTargetSelector.TargetDecision target = candidates.isEmpty()
                ? new BossTargetSelector.TargetDecision(null, 0.0D, "NO_ELIGIBLE_PLAYER")
                : candidates.getFirst();
        List<BossAbilityId> history = List.copyOf(acceptedHistory);
        BossAbilitySelector.AbilityDecision ability = BossAbilitySelector.choose(snapshot, intent,
                target.target(), previous, available, history, budget, generation, cursor);
        // The highest-scoring player is not always actionable: a contact move
        // against a distant player is rejected before any cast is committed.
        // Try the bounded alternates so a legal attack is never withheld while
        // one legitimately exists.
        for (int index = 1; index < candidates.size() && ability.ability() == null; index++) {
            BossTargetSelector.TargetDecision alternate = candidates.get(index);
            BossAbilitySelector.AbilityDecision next = BossAbilitySelector.choose(snapshot, intent,
                    alternate.target(), previous, available, history, budget, generation, cursor);
            if (next.ability() != null) {
                target = alternate;
                ability = next;
            }
        }
        // Group pressure: one primary plus up to two genuinely different
        // secondaries, so a combo can spread across the party instead of
        // repeatedly landing on whoever the primary happens to be.
        List<BossTargetSelector.TargetDecision> supports =
                BossTargetSelector.secondaries(candidates, target.target(), MAX_SECONDARIES);
        return new Decision(target, supports, intent, ability);
    }

    /** Hard cap on distinct secondary players a single combo may include. */
    public static final int MAX_SECONDARIES = 2;

    public record Decision(BossTargetSelector.TargetDecision target,
                           List<BossTargetSelector.TargetDecision> supports,
                           BossIntent intent,
                           BossAbilitySelector.AbilityDecision ability) {
    }
}
