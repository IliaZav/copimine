package me.copimine.endevent.domain;

/**
 * N23-P1-04: chest-beam per-player admission and attempted/accepted accounting.
 *
 * <p>Two defects lived in the signature ultimate's damage loop. First, a player
 * was added to the "already hit" set <em>before</em> {@code applyContactHit}
 * confirmed the damage, so a protection-cancelled or otherwise refused hit was
 * later labelled a successful one. Second, the locked lane's damage was resolved
 * against the player's torso capsule while the solid-cover test sampled the
 * <em>eye</em>, so a wall that shields the body but leaves the head peeking over
 * a slab could still deliver a hit straight through cover.</p>
 *
 * <p>This policy owns both rules as pure decisions: cover is sampled at the same
 * body point the damage is resolved against, and a beam attempt is recorded only
 * when the beam is genuinely admitted, while the attempt itself is remembered so
 * a refused damage event cannot make the beam re-roll every tick (a retry
 * storm). It never removes cover, never widens a lane and never grants a hit.</p>
 */
public final class ChestBeamAdmissionPolicy {

    /** A body sample the cover ray can be drawn against. */
    public enum BodySample { TORSO, EYE }

    /**
     * The sample the locked lane's damage capsule is actually resolved against.
     * Announcement (rails) and damage share this body point, so the solid-cover
     * rule must be sampled here too rather than at the eye.
     */
    public static final BodySample DAMAGEABLE_SAMPLE = BodySample.TORSO;

    /** What the beam should do with this candidate on this tick. */
    public enum Decision { ATTEMPT, SKIP_OUT_OF_LANE, SKIP_COVERED, SKIP_ATTEMPTED }

    private ChestBeamAdmissionPolicy() {
    }

    /**
     * Solid-cover verdict for a body sample. A block between the core and the
     * damageable sample means the shot is covered; sampling the eye instead
     * would let a body behind cover be hit because its head was briefly visible.
     */
    public static boolean covered(BodySample damageable, boolean torsoBlocked, boolean eyeBlocked) {
        return damageable == BodySample.EYE ? eyeBlocked : torsoBlocked;
    }

    /**
     * The per-tick decision. Order matters: an out-of-lane player is never an
     * attempt, an already-attempted player is never retried (a refused damage
     * event must not become a per-tick re-roll), and only a clear, in-lane,
     * uncovered body is admitted.
     */
    public static Decision decide(boolean inLane, boolean covered, boolean alreadyAttempted) {
        if (!inLane) {
            return Decision.SKIP_OUT_OF_LANE;
        }
        if (alreadyAttempted) {
            return Decision.SKIP_ATTEMPTED;
        }
        if (covered) {
            return Decision.SKIP_COVERED;
        }
        return Decision.ATTEMPT;
    }

    /** True exactly when the beam may resolve a hit against this candidate. */
    public static boolean admitted(Decision decision) {
        return decision == Decision.ATTEMPT;
    }
}
