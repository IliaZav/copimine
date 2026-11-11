package me.copimine.endevent.domain;

/** Finite hunt budgets and the 11-November single-owner mark rotation. */
public final class WaveTwoHuntPolicy {
    public static final int MAX_LATE_REINFORCEMENTS = 2;
    /** Packet identity is independent from the bounded gameplay completion counter. */
    public static final int MAX_MARK_ASSIGNMENT_ID = Integer.MAX_VALUE;

    private WaveTwoHuntPolicy() { }

    public static final long MARK_ROTATION_MILLIS = 20_000L;

    /** Uses the persisted intro origin: reconnect/restart never renews a full period. */
    public static long markEpoch(long introStartedMillis, long nowMillis) {
        if (introStartedMillis < 0L || nowMillis < introStartedMillis) return 0L;
        return (nowMillis - introStartedMillis) / MARK_ROTATION_MILLIS;
    }

    /** Stable frozen order, skipping unavailable owners without admitting foreign players. */
    public static java.util.UUID markedParticipant(java.util.List<java.util.UUID> frozenRoster,
            java.util.Set<java.util.UUID> eligible, long epoch, int spawnedGroup) {
        if (frozenRoster == null || frozenRoster.isEmpty() || eligible == null
                || eligible.isEmpty() || epoch < 0L) return null;
        int start = (int) ((epoch % frozenRoster.size()
                + Math.max(0, spawnedGroup - 1)) % frozenRoster.size());
        for (int offset = 0; offset < frozenRoster.size(); offset++) {
            java.util.UUID candidate = frozenRoster.get((start + offset) % frozenRoster.size());
            if (candidate != null && eligible.contains(candidate)) return candidate;
        }
        return null;
    }


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