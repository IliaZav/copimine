package me.copimine.endevent.domain;

/**
 * Target arbitration for the LAST_SEAL under-player ambush Kagune spell.
 *
 * <p>The canonical contract makes the committed target a guaranteed grab:
 * once a legal participant is committed, the cast has no random miss roll.
 * The cast may only be withheld or cancelled for a concrete safety reason
 * (invalid target, unsafe floor, or pressure on a recently controlled
 * player), which is represented by the explicit {@link Decision} values
 * instead of a probability.</p>
 */
public final class BossAmbushPolicy {
    /** Minimum gap between two committed ambushes on the same player. */
    public static final long RECENT_TARGET_MILLIS = 20_000L;
    /** Short ground warning before the apparition becomes a committed grab. */
    public static final int GROUND_WARNING_TICKS = 10;
    /** Upper bound for the whole committed cast, in ticks. */
    public static final int MAX_CAST_TICKS = 120;

    public enum Decision {
        COMMIT,
        NOT_LAST_SEAL,
        NO_TARGET,
        RECENT_TARGET,
        UNSAFE_GROUND,
        ALREADY_RESTRAINED
    }

    private BossAmbushPolicy() {
    }

    public static Decision decide(BossPhase phase, boolean targetValid, boolean targetRestrained,
                                  boolean groundSafe, long nowMillis, long lastGrabMillis) {
        if (phase != BossPhase.LAST_SEAL) {
            return Decision.NOT_LAST_SEAL;
        }
        if (!targetValid) {
            return Decision.NO_TARGET;
        }
        if (targetRestrained) {
            return Decision.ALREADY_RESTRAINED;
        }
        if (!groundSafe) {
            return Decision.UNSAFE_GROUND;
        }
        if (recentlyGrabbed(nowMillis, lastGrabMillis)) {
            return Decision.RECENT_TARGET;
        }
        return Decision.COMMIT;
    }

    /** A committed target keeps the grab only while it stays legal. */
    public static boolean keepsCommitment(boolean targetValid, boolean eventCurrent, boolean bossAlive) {
        return targetValid && eventCurrent && bossAlive;
    }

    public static boolean recentlyGrabbed(long nowMillis, long lastGrabMillis) {
        if (lastGrabMillis <= 0L) {
            return false;
        }
        long elapsed = nowMillis - lastGrabMillis;
        return elapsed >= 0L && elapsed < RECENT_TARGET_MILLIS;
    }
}
