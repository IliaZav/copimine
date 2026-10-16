package me.copimine.endevent.domain;

/** Pure presentation contract for an authoritative boss-hit transaction. */
public final class BossHitFeedbackPolicy {
    public enum Outcome {
        ACCEPTED_MELEE,
        ACCEPTED_PROJECTILE,
        SHIELD_BLOCKED_MELEE,
        SHIELD_BLOCKED_PROJECTILE,
        PHASE_IMMUNE,
        CINEMATIC_IMMUNE
    }

    public record Feedback(
            Outcome outcome,
            boolean redFlash,
            boolean hurtAnimation,
            double physicalRecoilHorizontal,
            double physicalRecoilVertical,
            boolean impactParticles,
            boolean shieldParticles,
            String soundCue,
            int feedbackDurationTicks,
            boolean attackerLocalCue) {
    }

    private BossHitFeedbackPolicy() {
    }

    public static Feedback forAcceptedHit(boolean projectile) {
        return forOutcome(projectile ? Outcome.ACCEPTED_PROJECTILE : Outcome.ACCEPTED_MELEE);
    }

    public static Feedback forShieldBlockedHit(boolean projectile) {
        return forOutcome(projectile
                ? Outcome.SHIELD_BLOCKED_PROJECTILE : Outcome.SHIELD_BLOCKED_MELEE);
    }

    public static Feedback forOutcome(Outcome outcome) {
        if (outcome == null) {
            return silent(Outcome.PHASE_IMMUNE);
        }
        return switch (outcome) {
            case ACCEPTED_MELEE -> new Feedback(
                    outcome, true, true, 0.12D, 0.0D,
                    true, false, "ENTITY_PLAYER_ATTACK_STRONG", 4, true);
            case ACCEPTED_PROJECTILE -> new Feedback(
                    outcome, true, true, 0.0D, 0.0D,
                    true, false, "ENTITY_ARROW_HIT", 4, true);
            case SHIELD_BLOCKED_MELEE, SHIELD_BLOCKED_PROJECTILE -> new Feedback(
                    outcome, false, false, 0.0D, 0.0D,
                    false, true, "BLOCK_ANVIL_HIT", 3, true);
            case PHASE_IMMUNE, CINEMATIC_IMMUNE -> silent(outcome);
        };
    }

    private static Feedback silent(Outcome outcome) {
        return new Feedback(outcome, false, false, 0.0D, 0.0D,
                false, false, "", 0, false);
    }
}
