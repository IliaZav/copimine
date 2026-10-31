package me.copimine.endevent.domain;

/**
 * Bounded decision table for placing the Wave 6 prisoner inside the ritual sphere.
 *
 * <p>Capture is only committed once the runtime accepted the teleport. A rejected
 * teleport must never leave the event half-started with a captured player who is
 * still standing outside the shell, so the attempts are counted and then rolled
 * back explicitly instead of being retried forever.
 */
public final class RitualPrisonerPlacementPolicy {
    /** Consecutive rejected placements before the capture is rolled back. */
    public static final int MAX_PLACEMENT_ATTEMPTS = 3;

    private RitualPrisonerPlacementPolicy() {
    }

    /** Outcome of one placement attempt. */
    public enum Outcome {
        /** The player is inside the shell; commit the capture. */
        PLACED,
        /** Retry on a later tick without committing anything yet. */
        RETRY,
        /** Attempts exhausted; release everything and wait for a fresh capture. */
        ABORT
    }

    /**
     * @param teleported whether the runtime accepted the placement teleport
     * @param consecutiveFailures failures already recorded (including this one)
     */
    public static Outcome afterAttempt(boolean teleported, int consecutiveFailures) {
        if (teleported) {
            return Outcome.PLACED;
        }
        if (consecutiveFailures < 0) {
            throw new IllegalArgumentException("consecutiveFailures must not be negative");
        }
        return consecutiveFailures >= MAX_PLACEMENT_ATTEMPTS ? Outcome.ABORT : Outcome.RETRY;
    }
}