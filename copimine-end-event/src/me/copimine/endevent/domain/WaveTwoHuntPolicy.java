package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/** Pure timing, target-rotation, and reinforcement limits for the Wave 2 hunt. */
public final class WaveTwoHuntPolicy {
    public static final long MARK_ROTATION_MILLIS = 45_000L;
    public static final int MIN_MARK_PERIODS = 2;
    public static final int MAX_LATE_REINFORCEMENTS = 2;
    public static final int MAX_COMPLETED_MARK_PERIODS = 1_000_000;

    private WaveTwoHuntPolicy() { }

    /**
     * Plan the first two mark assignments in stable roster order. Duplicate or
     * null roster entries are ignored. With one eligible player, both periods
     * use that player; with two or more, the initial assignees are distinct.
     */
    public static List<UUID> initialMarkAssignments(List<UUID> eligiblePlayers, long cursor) {
        List<UUID> eligible = distinctEligible(eligiblePlayers);
        if (eligible.isEmpty()) return List.of();
        int first = (int) Math.floorMod(cursor, (long) eligible.size());
        UUID firstTarget = eligible.get(first);
        if (eligible.size() == 1) return List.of(firstTarget, firstTarget);
        UUID secondTarget = eligible.get((first + 1) % eligible.size());
        return List.of(firstTarget, secondTarget);
    }

    /**
     * Choose a target from the current eligible roster, avoiding the previous
     * target whenever another distinct participant is available.
     */
    public static UUID nextMarkTarget(List<UUID> eligiblePlayers, UUID previousTarget, long cursor) {
        List<UUID> eligible = distinctEligible(eligiblePlayers);
        if (eligible.isEmpty()) return null;
        int first = (int) Math.floorMod(cursor, (long) eligible.size());
        for (int offset = 0; offset < eligible.size(); offset++) {
            UUID candidate = eligible.get((first + offset) % eligible.size());
            if (!candidate.equals(previousTarget)) return candidate;
        }
        return eligible.get(first);
    }

    /** Start of an assignment plus its full nominal rotation duration. */
    public static long markDeadline(long startedAtMillis) {
        if (startedAtMillis < 0L) {
            throw new IllegalArgumentException("mark start time must not be negative");
        }
        return Math.addExact(startedAtMillis, MARK_ROTATION_MILLIS);
    }

    /** The exact deadline is the first instant at which the assignment expires. */
    public static boolean markExpired(long nowMillis, long deadlineMillis) {
        return nowMillis >= 0L && deadlineMillis > 0L && nowMillis >= deadlineMillis;
    }

    /** A batch may clear after two mark periods, without requiring a third period. */
    public static boolean mayCompleteBatch(boolean combatBatchCleared, int completedMarkPeriods) {
        return combatBatchCleared && completedMarkPeriods >= MIN_MARK_PERIODS;
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

    private static List<UUID> distinctEligible(List<UUID> eligiblePlayers) {
        if (eligiblePlayers == null || eligiblePlayers.isEmpty()) return List.of();
        LinkedHashSet<UUID> distinct = new LinkedHashSet<>();
        for (UUID player : eligiblePlayers) {
            if (player != null) distinct.add(player);
        }
        return distinct.isEmpty() ? List.of() : List.copyOf(new ArrayList<>(distinct));
    }

    private static int nonNegative(int value) {
        return Math.max(0, value);
    }
}
