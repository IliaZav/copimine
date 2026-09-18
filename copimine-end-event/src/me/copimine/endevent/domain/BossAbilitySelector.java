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
        if (snapshot == null || snapshot.livingPlayers().isEmpty()
                || available == null || available.isEmpty()) {
            return new AbilityDecision(null, 0.0D, "NO_CANDIDATE");
        }
        BossIntent safeIntent = intent == null ? BossIntent.PRESSURE : intent;
        List<ScoredAbility> ranked = available.stream().filter(ability -> ability != null)
                .distinct()
                .map(ability -> score(snapshot, safeIntent, target, previous, ability,
                        budget, generation))
                .filter(ScoredAbility::valid)
                .sorted(Comparator.comparingDouble(ScoredAbility::score).reversed()
                        .thenComparing(value -> value.ability().ordinal()))
                .toList();
        if (ranked.isEmpty()) {
            return new AbilityDecision(null, 0.0D, "NO_VALID_ABILITY");
        }
        // Rotate through the top three valid choices. The old skip-based
        // selector clamped every cursor >= 2 to the third entry, which made
        // the boss repeat one ability forever during longer fights.
        int candidateCount = Math.min(3, ranked.size());
        ScoredAbility selected = ranked.get(Math.floorMod(cursor, candidateCount));
        return new AbilityDecision(selected.ability(), selected.score(), selected.reason());
    }

    private static ScoredAbility score(BossPerceptionSnapshot snapshot, BossIntent intent,
                                       UUID target, BossAbilityId previous,
                                       BossAbilityId ability, BossHazardBudget budget,
                                       long generation) {
        BossPerceptionSnapshot.PlayerObservation observation = snapshot.livingPlayers().stream()
                .filter(value -> target != null && target.equals(value.playerId()))
                .findFirst().orElse(null);
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
                - (observation != null && observation.recentlyHardControlled() && ability.hardControl()
                ? 8.0D : 0.0D);
        return new ScoredAbility(ability, score, true, "SCORED");
    }

    private static double phaseWeight(BossPhase phase, BossAbilityId ability) {
        if (phase == null) return 0.0D;
        return switch (phase) {
            case AWAKENING -> ability == BossAbilityId.VOID_BLAST ? 3.0D : 1.0D;
            case HUNT -> ability == BossAbilityId.RIFT_ARROWS || ability == BossAbilityId.VOID_MARK ? 4.0D : 1.0D;
            case RIFT -> ability == BossAbilityId.VOID_MARK || ability == BossAbilityId.SUMMON_SERVANTS ? 4.0D : 1.0D;
            case OVERLOAD -> ability == BossAbilityId.ARENA_INFERNO ? 4.0D : 1.0D;
            case RAGE -> ability == BossAbilityId.RIFT_ARROWS || ability == BossAbilityId.ARENA_INFERNO ? 4.0D : 1.0D;
            case LAST_SEAL -> ability == BossAbilityId.FINAL_STRIKE ? 5.0D : 1.0D;
        };
    }

    private static double intentMatch(BossIntent intent, BossAbilityId ability) {
        return switch (intent) {
            case PUNISH_STACK -> ability == BossAbilityId.VOID_BLAST ? 6.0D : 0.0D;
            case PUNISH_SPREAD -> ability == BossAbilityId.VOID_MARK || ability == BossAbilityId.RIFT_PROJECTILE ? 5.0D : 0.0D;
            case CREATE_SPACE -> ability == BossAbilityId.REPOSITION || ability == BossAbilityId.VOID_BLAST ? 6.0D : 0.0D;
            case FLANK -> ability == BossAbilityId.RIFT_PROJECTILE ? 3.0D : 0.0D;
            case RANGED_PRESSURE -> ability == BossAbilityId.RIFT_ARROWS || ability == BossAbilityId.RIFT_PROJECTILE ? 5.0D : 0.0D;
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
