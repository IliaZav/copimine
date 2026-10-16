package me.copimine.endevent.domain;

import java.util.List;
import java.util.UUID;

/** One bounded perception -> target -> intent -> ability decision. */
public final class BossBrain {
    public Decision decide(BossPerceptionSnapshot snapshot,
                           List<BossAbilityId> available,
                           BossAbilityId previous,
                           UUID currentTarget,
                           BossHazardBudget budget,
                           long generation,
                           int cursor) {
        BossIntent intent = BossIntentSelector.choose(snapshot);
        BossTargetSelector.TargetDecision target = BossTargetSelector.choose(snapshot, currentTarget);
        BossAbilitySelector.AbilityDecision ability = BossAbilitySelector.choose(snapshot, intent,
                target.target(), previous, available, budget, generation, cursor);
        return new Decision(target, intent, ability);
    }

    public record Decision(BossTargetSelector.TargetDecision target,
                           BossIntent intent,
                           BossAbilitySelector.AbilityDecision ability) {
    }
}
