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

    /**
     * One presentation decision.
     *
     * <p>{@code critical} and {@code splash} are presentation-only classifications
     * of an <em>accepted</em> hit. They never change the authoritative damage: the
     * server has already finalized it before this record is built. They only
     * choose a louder cue / a wider burst so the party can feel the difference
     * between a precise strike, a critical and an area hit.</p>
     */
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
            boolean attackerLocalCue,
            boolean critical,
            boolean splash) {
    }

    private BossHitFeedbackPolicy() {
    }

    public static Feedback forAcceptedHit(boolean projectile) {
        return forAcceptedHit(projectile, false, false);
    }

    /**
     * Accepted-hit feedback with the two owner-requested flavours.
     *
     * @param projectile a real projectile landed the hit
     * @param critical   the hit was a critical strike (extra spike, sharper cue)
     * @param splash     the hit was area damage (wider but softer impact)
     */
    public static Feedback forAcceptedHit(boolean projectile, boolean critical, boolean splash) {
        if (splash) {
            return new Feedback(
                    projectile ? Outcome.ACCEPTED_PROJECTILE : Outcome.ACCEPTED_MELEE,
                    true, true,
                    // Area hits do not shove the body around as much as a
                    // focused blow; the real damage is unchanged.
                    projectile ? 0.0D : 0.06D, 0.0D,
                    true, false, "ENTITY_ARROW_HIT", 4, true,
                    false, true);
        }
        if (critical) {
            return new Feedback(
                    projectile ? Outcome.ACCEPTED_PROJECTILE : Outcome.ACCEPTED_MELEE,
                    true, true,
                    projectile ? 0.0D : 0.18D, 0.0D,
                    true, false,
                    projectile ? "ENTITY_ARROW_HIT" : "ENTITY_PLAYER_ATTACK_CRIT",
                    5, true,
                    true, false);
        }
        return forOutcome(projectile ? Outcome.ACCEPTED_PROJECTILE : Outcome.ACCEPTED_MELEE);
    }

    public static Feedback forShieldBlockedHit(boolean projectile) {
        return forOutcome(projectile
                ? Outcome.SHIELD_BLOCKED_PROJECTILE : Outcome.SHIELD_BLOCKED_MELEE);
    }

    /**
     * Presentation for one classified hit.
     *
     * <p>Contract 10.6: the classification is the single authority, so the
     * presentation is derived from it and cannot disagree with the real HP
     * write. A blocked hit shows the shield impact and never flashes flesh
     * red; a missed hit renders nothing at all.</p>
     */
    public static Feedback forClassification(BossHitClassificationPolicy.Hit hit) {
        return forClassification(hit, false, false);
    }

    /** As {@link #forClassification(BossHitClassificationPolicy.Hit)} with flavour. */
    public static Feedback forClassification(BossHitClassificationPolicy.Hit hit,
                                             boolean critical, boolean splash) {
        if (hit == null) {
            return forOutcome(Outcome.PHASE_IMMUNE);
        }
        return switch (hit.outcome()) {
            case ACCEPTED -> forAcceptedHit(false, critical, splash);
            case BLOCKED -> forShieldBlockedHit(false);
            case MISSED, REJECTED, IMMUNE -> silent(Outcome.PHASE_IMMUNE);
        };
    }

    public static Feedback forOutcome(Outcome outcome) {
        if (outcome == null) {
            return silent(Outcome.PHASE_IMMUNE);
        }
        return switch (outcome) {
            case ACCEPTED_MELEE -> new Feedback(
                    outcome, true, true, 0.12D, 0.0D,
                    true, false, "ENTITY_PLAYER_ATTACK_STRONG", 4, true,
                    false, false);
            case ACCEPTED_PROJECTILE -> new Feedback(
                    outcome, true, true, 0.0D, 0.0D,
                    true, false, "ENTITY_ARROW_HIT", 4, true,
                    false, false);
            case SHIELD_BLOCKED_MELEE, SHIELD_BLOCKED_PROJECTILE -> new Feedback(
                    outcome, false, false, 0.0D, 0.0D,
                    false, true, "BLOCK_ANVIL_HIT", 3, true,
                    false, false);
            case PHASE_IMMUNE, CINEMATIC_IMMUNE -> silent(outcome);
        };
    }

    /**
     * A hit whose red flash the client actually renders.
     *
     * <p>The player reported "he does not turn red when I hit him". A vanilla
     * mob only flickers while its hurt timer runs, and the boss's hurt state is
     * suppressed by its own damage pipeline, so the accepted-hit flash is forced
     * explicitly: no damage-immunity window is opened and no extra damage is
     * applied, the body is merely respawned with the red overlay.</p>
     */
    public static boolean forcesRedTint(Feedback feedback) {
        return feedback != null && feedback.redFlash()
                && (feedback.outcome() == Outcome.ACCEPTED_MELEE
                || feedback.outcome() == Outcome.ACCEPTED_PROJECTILE);
    }

    /** Number of extra critical sparks to render, zero for an ordinary hit. */
    public static int criticalSparkCount(Feedback feedback) {
        return feedback != null && feedback.critical() ? 14 : 0;
    }

    /** Radius of the splash ring in blocks, zero for a single-target hit. */
    public static double splashRingRadius(Feedback feedback) {
        return feedback != null && feedback.splash() ? 1.7D : 0.0D;
    }

    private static Feedback silent(Outcome outcome) {
        return new Feedback(outcome, false, false, 0.0D, 0.0D,
                false, false, "", 0, false, false, false);
    }
}
