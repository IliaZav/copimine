package me.copimine.endevent.domain;

/**
 * The single authority for what an incoming boss hit *is*.
 *
 * <p>The user reports that hitting the boss often produced no red reaction
 * and sometimes failed to reduce its health. Both symptoms come from the same
 * design flaw: damage, the red flash and the shield impact were decided by
 * separate branches, so they could disagree. Contract 10.6 requires one
 * classification per event, from which the real HP write <em>and</em> the
 * whole presentation are derived:</p>
 *
 * <ul>
 *   <li>ACCEPTED - real HP is lost, the body flashes red, the hurt reaction
 *       plays.</li>
 *   <li>BLOCKED - a shield impact is shown, no flesh flash, no HP loss.</li>
 *   <li>MISSED - nothing at all: no damage, no flash, no impact.</li>
 *   <li>IMMUNE - an authored immunity reason rejects the hit explicitly.</li>
 *   <li>REJECTED - the source is not a participant, so it is not a hit.</li>
 * </ul>
 *
 * <p>Pure by construction: the adapter supplies already-read booleans and the
 * finalized damage amount.</p>
 */
public final class BossHitClassificationPolicy {

    public enum Outcome {
        ACCEPTED,
        BLOCKED,
        MISSED,
        IMMUNE,
        REJECTED
    }

    /**
     * One classified hit.
     *
     * @param outcome        the single authority for the whole transaction
     * @param healthDamage   amount that must be subtracted from real HP
     * @param redFlash       red flesh flash on the visible model
     * @param hurtAnimation  flesh hurt reaction
     * @param shieldImpact   shield/guardian impact effect
     */
    public record Hit(Outcome outcome, double healthDamage, boolean redFlash,
                      boolean hurtAnimation, boolean shieldImpact) {
        public Hit {
            outcome = outcome == null ? Outcome.REJECTED : outcome;
            healthDamage = Double.isFinite(healthDamage)
                    ? Math.max(0.0D, healthDamage) : 0.0D;
        }

        public boolean accepted() {
            return outcome == Outcome.ACCEPTED;
        }
    }

    private static final Hit REJECTED =
            new Hit(Outcome.REJECTED, 0.0D, false, false, false);
    private static final Hit MISSED =
            new Hit(Outcome.MISSED, 0.0D, false, false, false);
    private static final Hit IMMUNE =
            new Hit(Outcome.IMMUNE, 0.0D, false, false, false);
    private static final Hit BLOCKED =
            new Hit(Outcome.BLOCKED, 0.0D, false, false, true);

    private BossHitClassificationPolicy() {
    }

    /**
     * Classify one incoming boss hit.
     *
     * @param shielded      a guardian/shield filter would absorb the hit
     * @param missed        the hit never reached the visible body
     * @param immune        an authored immunity reason rejects it
     * @param participant   the source is an eligible participant
     * @param finalDamage   server-finalized damage of the event
     * @param immunityScale additional acceptance multiplier (1.0 = normal)
     */
    public static Hit classify(boolean shielded, boolean missed, boolean immune,
                               boolean participant, double finalDamage,
                               double immunityScale) {
        if (!participant) {
            return REJECTED;
        }
        if (missed) {
            return MISSED;
        }
        if (immune) {
            return IMMUNE;
        }
        if (shielded) {
            return BLOCKED;
        }
        double scale = Double.isFinite(immunityScale) && immunityScale > 0.0D
                ? immunityScale : 1.0D;
        if (!Double.isFinite(finalDamage) || finalDamage <= 0.0D) {
            // A hit with no finalized damage is not an accepted hit: the boss
            // must not flash red for damage it never took.
            return MISSED;
        }
        double applied = finalDamage * scale;
        if (!Double.isFinite(applied) || applied <= 0.0D) {
            return MISSED;
        }
        return new Hit(Outcome.ACCEPTED, applied, true, true, false);
    }
}
