package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Contextual, fairness-aware target scoring for group combat. */
public final class BossTargetSelector {
    /**
     * Bounded alternates the brain may try before giving up on a decision.
     *
     * <p>Small enough that a decision stays O(players) and never re-scans the
     * world: the candidate list is already built from the bounded perception
     * snapshot, and only the top few are re-scored against the ability pool.</p>
     */
    public static final int MAX_CANDIDATES = 4;

    private BossTargetSelector() {
    }

    public static TargetDecision choose(BossPerceptionSnapshot snapshot, UUID currentTarget) {
        return choose(snapshot, currentTarget, 0);
    }

    /**
     * Best target for this decision.
     *
     * @param cursor decision cursor, used only to rotate exact score ties so a
     *               permanent UUID ordering cannot hand every ambiguous pick to
     *               the same player
     */
    public static TargetDecision choose(BossPerceptionSnapshot snapshot, UUID currentTarget, int cursor) {
        List<TargetDecision> ranked = ranked(snapshot, currentTarget, cursor);
        return ranked.isEmpty()
                ? new TargetDecision(null, 0.0D, "NO_ELIGIBLE_PLAYER") : ranked.getFirst();
    }

    /**
     * Highest-scoring targets, best first, bounded to {@link #MAX_CANDIDATES}.
     *
     * <p>Exposed so the brain can fall through to the next target when the
     * best-scoring one cannot actually be acted on by any currently available
     * ability. The old single-choice selection could pick a distant target and
     * then reject every contact ability against it, which surfaced as the boss
     * standing idle for a whole decision window -- the "empty priority branch
     * with no attacks" the boss contract forbids.</p>
     */
    public static List<TargetDecision> ranked(BossPerceptionSnapshot snapshot,
                                              UUID currentTarget, int cursor) {
        if (snapshot == null) return List.of();
        List<TargetDecision> sorted = new ArrayList<>(snapshot.livingPlayers().stream()
                .map(player -> scored(player, currentTarget))
                .toList());
        if (sorted.isEmpty()) return List.of();
        sorted.sort(Comparator.comparingDouble(TargetDecision::score).reversed()
                .thenComparing(value -> value.target().toString()));
        // Rotate equal-score runs by the decision cursor. Every exact tie used
        // to fall to the lexicographically largest UUID, so one player absorbed
        // every ambiguous decision while an equally valid teammate was ignored.
        int index = 0;
        while (index < sorted.size()) {
            int end = index + 1;
            while (end < sorted.size()
                    && Double.compare(sorted.get(end).score(), sorted.get(index).score()) == 0) {
                end++;
            }
            int runLength = end - index;
            if (runLength > 1) {
                List<TargetDecision> run = new ArrayList<>(sorted.subList(index, end));
                Collections.rotate(run, -Math.floorMod(cursor, runLength));
                for (int offset = 0; offset < runLength; offset++) {
                    sorted.set(index + offset, run.get(offset));
                }
            }
            index = end;
        }
        return List.copyOf(sorted.subList(0, Math.min(MAX_CANDIDATES, sorted.size())));
    }

    private static TargetDecision scored(BossPerceptionSnapshot.PlayerObservation player,
                                         UUID currentTarget) {
        double score = player.recentDamageThreat() * 1.5D
                + (player.lineOfSight() ? 3.0D : 0.0D)
                + (player.objectiveThreat() ? 4.0D : 0.0D)
                + (player.isolated() ? 3.0D : 0.0D)
                + Math.max(0.0D, 1.0D - player.healthFraction()) * 1.5D
                - (player.recentlyTargeted() ? 4.5D : 0.0D)
                - (player.recentlyHardControlled() ? 8.0D : 0.0D)
                - (currentTarget != null && currentTarget.equals(player.playerId()) ? 1.0D : 0.0D);
        String reason = player.recentlyHardControlled() ? "HARD_CONTROL_COOLDOWN"
                : player.isolated() ? "ISOLATED_OPPORTUNITY"
                : player.objectiveThreat() ? "OBJECTIVE_THREAT" : "FAIR_PRESSURE";
        return new TargetDecision(player.playerId(), score, reason);
    }

    public record TargetDecision(UUID target, double score, String reason) {
        public TargetDecision {
            reason = reason == null || reason.isBlank() ? "UNKNOWN" : reason;
            score = Double.isFinite(score) ? score : 0.0D;
        }
    }
}
