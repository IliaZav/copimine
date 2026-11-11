package me.copimine.endevent.domain;

/**
 * When the Rift Guardian may swing a hand at a nearby player.
 *
 * <p>The 11-November contract (10.5) supersedes the old auxiliary melee
 * role. Melee is no longer an occasional filler between casts: it is the
 * backbone of the close fight, with a cadence in the authored 1.8-2.4 s
 * range that tightens as the phase escalates. The final phase explicitly
 * <em>permits</em> melee - the cocoon fury is permanent, so a blanket
 * {@code LAST_SEAL} ban is forbidden.</p>
 */
public final class BossCloseCombatPolicy {

    /** Slowest basic hit: the theory floor of the tuned range, 2.4 s. */
    public static final long CADENCE_SLOWEST_MILLIS = 2_400L;
    /** Fastest basic hit: the top of the tuned range, 1.8 s. */
    public static final long CADENCE_FASTEST_MILLIS = 1_800L;
    /** Back-off after a refused swing, so a genuine opportunity is re-tried. */
    public static final long RETRY_MILLIS = 600L;

    private BossCloseCombatPolicy() {
    }

    /**
     * Basic-hit cadence for a phase.
     *
     * <p>AWAKENING opens at the slow end of the authored range and each later
     * phase tightens toward the fast end, so the creature visibly sharpens.
     * The range is inverted deliberately: it never leaves 1.8-2.4 s.</p>
     */
    public static long cadenceMillis(BossPhase phase) {
        if (phase == null) {
            return CADENCE_SLOWEST_MILLIS;
        }
        return switch (phase) {
            case AWAKENING -> CADENCE_SLOWEST_MILLIS;
            case HUNT -> 2_200L;
            case RIFT -> 2_100L;
            case OVERLOAD -> 2_000L;
            case RAGE -> 1_900L;
            case LAST_SEAL -> CADENCE_FASTEST_MILLIS;
        };
    }

    /**
     * Whether melee exists at all in a phase. It exists in every real phase;
     * only an unknown (null) phase must not invent eligibility.
     */
    public static boolean meleePermitted(BossPhase phase) {
        return phase != null;
    }

    /**
     * The committed timestamp of the next legal swing.
     */
    public static long nextStrikeMillis(BossPhase phase, long nowMillis) {
        return nowMillis + cadenceMillis(phase);
    }

    /** Delay before re-trying a swing that was refused, not a full cadence. */
    public static long retryMillis() {
        return RETRY_MILLIS;
    }

    /**
     * Whether a close-range swing may start right now.
     *
     * <p>Gates, in order: the phase permits melee, the cooldown expired, no
     * other cast owns the body, the target is inside the authored reach with
     * a clear line of sight and is not already held by crowd control.</p>
     */
    public static boolean canStrike(BossPhase phase, long now, long nextStrike,
                                    long spellPauseUntil, long nextMajorCast,
                                    double distance, boolean lineOfSight,
                                    boolean controlledTarget) {
        return meleePermitted(phase)
                && now >= nextStrike && now >= spellPauseUntil && now < nextMajorCast
                && Double.isFinite(distance) && distance >= 0.0D
                && distance <= BossContactStrikePolicy.MELEE_SWIPE_RANGE
                && lineOfSight && !controlledTarget;
    }

    /**
     * Keep the historical constant name working for callers that only need a
     * legal default. SUPERSEDED: it used to be the single 8 s cooldown that
     * made the boss swing rarely; it is now the slowest legal cadence.
     */
    public static final long COOLDOWN_MILLIS = CADENCE_SLOWEST_MILLIS;
}
