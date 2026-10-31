package me.copimine.endevent.domain;

/**
 * Reinforcement limits and packet identity for the Wave 2 hunt.
 *
 * <p>Superseded: the earlier design marked one rotating player for 45 seconds
 * and gated the wave on completed mark periods. The canonical contract marks
 * every participant for the whole wave and completes the wave on exactly three
 * subwaves, so there is no target rotation and no mark duration here.</p>
 */
public final class WaveTwoHuntPolicy {
    public static final int MAX_LATE_REINFORCEMENTS = 2;
    /** Packet identity is independent from the bounded gameplay completion counter. */
    public static final int MAX_MARK_ASSIGNMENT_ID = Integer.MAX_VALUE;

    private WaveTwoHuntPolicy() { }

    /** Whether another monotonically increasing packet identity can be issued. */
    public static boolean markAssignmentIdsExhausted(int assignmentId) {
        if (assignmentId < 0) {
            throw new IllegalArgumentException("mark assignment identity must not be negative");
        }
        return assignmentId >= MAX_MARK_ASSIGNMENT_ID;
    }

    /** Advance the packet identity without changing gameplay completion progress. */
    public static int nextMarkAssignmentId(int assignmentId) {
        if (markAssignmentIdsExhausted(assignmentId)) {
            throw new IllegalStateException("Wave 2 mark assignment identity is exhausted");
        }
        return assignmentId + 1;
    }

    /** A snapshot without packet identity starts a fresh wave-scoped identity. */
    public static int restoreMarkAssignmentId(String encoded) {
        if (encoded == null) return 0;
        try {
            int assignmentId = Integer.parseInt(encoded);
            if (assignmentId < 0) {
                throw new IllegalArgumentException("Wave 2 mark assignment identity regressed");
            }
            return assignmentId;
        } catch (NumberFormatException malformed) {
            throw new IllegalArgumentException("Wave 2 mark assignment identity is malformed", malformed);
        }
    }

    /**
     * Bound a caller-requested initial reinforcement by available pressure
     * slots and the caller's remaining total Wave 2 reserve.
     */
    public static int initialReinforcementCount(int requestedCount,
                                                int maxPressureSlots,
                                                int remainingBudget) {
        return Math.min(nonNegative(requestedCount),
                Math.min(nonNegative(maxPressureSlots), nonNegative(remainingBudget)));
    }

    /**
     * Late reinforcement is limited to two mobs and also respects both the
     * caller's current pressure slots and remaining total Wave 2 reserve.
     */
    public static int lateReinforcementCount(int maxPressureSlots, int remainingBudget) {
        return Math.min(MAX_LATE_REINFORCEMENTS,
                Math.min(nonNegative(maxPressureSlots), nonNegative(remainingBudget)));
    }

    private static int nonNegative(int value) {
        return Math.max(0, value);
    }
}