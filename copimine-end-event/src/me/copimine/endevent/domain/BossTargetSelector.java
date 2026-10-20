package me.copimine.endevent.domain;

import java.util.Comparator;
import java.util.UUID;

/** Contextual, fairness-aware target scoring for group combat. */
public final class BossTargetSelector {
    private BossTargetSelector() {
    }

    public static TargetDecision choose(BossPerceptionSnapshot snapshot, UUID currentTarget) {
        if (snapshot == null || snapshot.livingPlayers().isEmpty()) {
            return new TargetDecision(null, 0.0D, "NO_ELIGIBLE_PLAYER");
        }
        return snapshot.livingPlayers().stream()
                .map(player -> scored(player, currentTarget))
                .max(Comparator.comparingDouble(TargetDecision::score)
                        .thenComparing(value -> value.target().toString()))
                .orElse(new TargetDecision(null, 0.0D, "NO_ELIGIBLE_PLAYER"));
    }

    private static TargetDecision scored(BossPerceptionSnapshot.PlayerObservation player,
                                         UUID currentTarget) {
        double score = player.recentDamageThreat() * 1.5D
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
