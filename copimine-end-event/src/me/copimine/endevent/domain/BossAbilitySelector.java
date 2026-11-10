package me.copimine.endevent.domain;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Ability scoring after intent selection. Invalid line-of-sight and budget
 * combinations are rejected before a cast timeline is committed.
 */
public final class BossAbilitySelector {
    private BossAbilitySelector() {
    }

    public static AbilityDecision choose(BossPerceptionSnapshot snapshot,
                                          BossIntent intent,
                                          UUID target,
                                          BossAbilityId previous,
                                          List<BossAbilityId> available,
                                          BossHazardBudget budget,
                                          long generation,
                                          int cursor) {
        return choose(snapshot, intent, target, previous, available, List.of(), budget, generation, cursor);
    }

    public static AbilityDecision choose(BossPerceptionSnapshot snapshot,
                                          BossIntent intent, UUID target, BossAbilityId previous,
                                          List<BossAbilityId> available, List<BossAbilityId> recentAccepted,
                                          BossHazardBudget budget, long generation, int cursor) {
        if (snapshot == null || snapshot.livingPlayers().isEmpty()
                || available == null || available.isEmpty()) {
            return new AbilityDecision(null, 0.0D, "NO_CANDIDATE");
        }
        BossIntent safeIntent = intent == null ? BossIntent.PRESSURE : intent;
        List<ScoredAbility> ranked = available.stream().filter(ability -> ability != null)
                .distinct()
                .map(ability -> score(snapshot, safeIntent, target, previous, ability,
                        recentAccepted, budget, generation))
                .filter(ScoredAbility::valid)
                .sorted(Comparator.comparingDouble(ScoredAbility::score).reversed()
                        .thenComparingInt(value -> Math.floorMod(value.ability().ordinal() - cursor,
                                BossAbilityId.values().length)))
                .toList();
        if (ranked.isEmpty()) {
            return new AbilityDecision(null, 0.0D, "NO_VALID_ABILITY");
        }
        // Context and accepted cast history decide the action. Rotating an
        // index through a re-sorted top three can permanently skip a role.
        ScoredAbility selected = ranked.getFirst();
        return new AbilityDecision(selected.ability(), selected.score(), selected.reason());
    }

    private static ScoredAbility score(BossPerceptionSnapshot snapshot, BossIntent intent,
                                       UUID target, BossAbilityId previous,
                                       BossAbilityId ability, List<BossAbilityId> recentAccepted, BossHazardBudget budget,
                                       long generation) {
        BossPerceptionSnapshot.PlayerObservation observation = snapshot.livingPlayers().stream()
                .filter(value -> target != null && target.equals(value.playerId()))
                .findFirst().orElse(null);
        if (!contactOpportunity(snapshot, observation, ability)) {
            return new ScoredAbility(ability, Double.NEGATIVE_INFINITY, false, "NO_CONTACT_OPPORTUNITY");
        }
        if (ability.requiresLineOfSight() && (observation == null || !observation.lineOfSight())) {
            return new ScoredAbility(ability, Double.NEGATIVE_INFINITY, false, "NO_LINE_OF_SIGHT");
        }
        if (budget != null && !budget.canReserve(target, ability.mechanic(), generation)) {
            return new ScoredAbility(ability, Double.NEGATIVE_INFINITY, false, "HAZARD_BUDGET_FULL");
        }
        double score = phaseWeight(snapshot.phase(), ability)
                + intentMatch(intent, ability)
                + geometryMatch(snapshot, ability)
                + (observation == null ? 0.0D : observation.recentDamageThreat())
                - (previous == ability ? 4.0D : 0.0D)
                - historyPenalty(recentAccepted, ability)
                - (observation != null && observation.recentlyHardControlled() && ability.hardControl()
                ? 8.0D : 0.0D);
        return new ScoredAbility(ability, score, true, "SCORED");
    }

    private static double historyPenalty(List<BossAbilityId> history, BossAbilityId ability) {
        if (history == null) return 0;
        double penalty = 0;
        for (int index = 0; index < Math.min(4, history.size()); index++) {
            if (history.get(index) == ability) penalty += (4 - index) * 2.0;
        }
        return penalty;
    }

    private static double phaseWeight(BossPhase phase, BossAbilityId ability) {
        if (phase == null) return 0.0D;
        return switch (phase) {
            case AWAKENING -> ability == BossAbilityId.VOID_BLAST ? 3.0D : 1.0D;
            case HUNT -> ability == BossAbilityId.RIFT_ARROWS || ability == BossAbilityId.VOID_MARK ? 4.0D : 1.0D;
            case RIFT -> ability == BossAbilityId.VOID_MARK
                    || ability == BossAbilityId.SUMMON_SERVANTS
                    || ability == BossAbilityId.BOSS_FIREBALL ? 4.0D : 1.0D;
            case OVERLOAD -> ability == BossAbilityId.ARENA_INFERNO
                    || ability == BossAbilityId.BOSS_FIREBALL ? 4.0D : 1.0D;
            case RAGE -> ability == BossAbilityId.RIFT_ARROWS
                    || ability == BossAbilityId.ARENA_INFERNO
                    || ability == BossAbilityId.BOSS_FIREBALL ? 4.0D : 1.0D;
            case LAST_SEAL -> switch (ability) {
                case AMBUSH_GRAB -> 7.0D;
                case SMALL_KAGUNE -> 6.0D;
                case FINAL_STRIKE, BOSS_FIREBALL -> 5.0D;
                default -> 1.0D;
            };
        };
    }

    private static boolean contactOpportunity(BossPerceptionSnapshot snapshot,
            BossPerceptionSnapshot.PlayerObservation target, BossAbilityId ability) {
        double range = switch (ability) {
            case MELEE_SWIPE -> BossContactStrikePolicy.MELEE_SWIPE_RANGE;
            case GROUND_SLAM -> BossContactStrikePolicy.GROUND_SLAM_RANGE;
            case CHEST_STRIKE -> BossContactStrikePolicy.CHEST_STRIKE_RANGE;
            default -> Double.POSITIVE_INFINITY;
        };
        if (!Double.isFinite(range)) return true;
        if (target == null || !target.lineOfSight() || target.distanceToBoss() > range) return false;
        double height = Math.abs(target.y() - snapshot.bossPosition().y());
        return height <= (ability == BossAbilityId.GROUND_SLAM
                ? BossContactStrikePolicy.GROUND_SLAM_VERTICAL
                : BossContactStrikePolicy.CHEST_STRIKE_VERTICAL);
    }

    private static double intentMatch(BossIntent intent, BossAbilityId ability) {
        return switch (intent) {
            case PUNISH_STACK -> ability == BossAbilityId.VOID_BLAST ? 6.0D : 0.0D;
            case PUNISH_SPREAD -> ability == BossAbilityId.VOID_MARK || ability == BossAbilityId.RIFT_PROJECTILE ? 5.0D : 0.0D;
            case CREATE_SPACE -> ability == BossAbilityId.REPOSITION || ability == BossAbilityId.VOID_BLAST ? 6.0D : 0.0D;
            case FLANK -> ability == BossAbilityId.RIFT_PROJECTILE ? 3.0D : 0.0D;
            case RANGED_PRESSURE -> ability == BossAbilityId.RIFT_ARROWS
                    || ability == BossAbilityId.RIFT_PROJECTILE
                    || ability == BossAbilityId.BOSS_FIREBALL ? 5.0D : 0.0D;
            case CONTROL, CHANNEL -> ability == BossAbilityId.VOID_MARK || ability == BossAbilityId.FINAL_STRIKE ? 5.0D : 0.0D;
            case SUMMON -> ability == BossAbilityId.SUMMON_SERVANTS ? 6.0D : 0.0D;
            case RECOVER -> ability == BossAbilityId.RECOVER || ability == BossAbilityId.REPOSITION ? 6.0D : 0.0D;
            default -> ability == BossAbilityId.VOID_BLAST ? 2.0D : 0.0D;
        };
    }

    private static double geometryMatch(BossPerceptionSnapshot snapshot, BossAbilityId ability) {
        return snapshot.partyStacked() && ability == BossAbilityId.VOID_BLAST ? 3.0D
                : snapshot.partySpread() && ability.requiresLineOfSight() ? 2.0D : 0.0D;
    }

    private record ScoredAbility(BossAbilityId ability, double score,
                                 boolean valid, String reason) {
    }

    public record AbilityDecision(BossAbilityId ability, double score, String reason) {
        public AbilityDecision {
            reason = reason == null || reason.isBlank() ? "UNKNOWN" : reason;
            score = Double.isFinite(score) ? score : 0.0D;
        }
    }
}
