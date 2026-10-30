package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;

/** Deterministic pacing and composition for bounded Wave 2 pressure periods. */
public final class WaveTwoStagePolicy {
    public static final int OPENING_GROUP_COUNT = 2;
    public static final int LATE_REINFORCEMENT_BUDGET = 8;
    public static final int MAX_GROUP_COUNT = OPENING_GROUP_COUNT + 1;
    /** Old snapshots used six stage counters; new runs stop at MAX_GROUP_COUNT. */
    public static final int STAGE_COUNT = 6;
    public static final int FIRST_LATE_GROUP = OPENING_GROUP_COUNT + 1;
    /** Wave 2 may exceed generic pressure only inside this bounded total reserve. */
    public static final int MAX_TOTAL_MOBS = PressureBudgetController.HARD_CAP;
    /** Two opening groups share the maximum initial reserve after the final reserve is held back. */
    public static final int MAX_ACTIVE_GROUP_MOBS =
            (MAX_TOTAL_MOBS - LATE_REINFORCEMENT_BUDGET + 1) / OPENING_GROUP_COUNT;
    public static final long MARK_DURATION_MILLIS = WaveTwoHuntPolicy.MARK_ROTATION_MILLIS;
    public static final long NEXT_GROUP_PAUSE_MILLIS = 0L;

    private WaveTwoStagePolicy() { }

    /**
     * Preserve the scaled total as two large opening groups followed by one
     * bounded final reserve group. The reserve is held back from the initial
     * roster, so this schedule never exceeds its total budget.
     */
    public static List<WaveMechanicsPolicy.WaveCounts> split(
            WaveMechanicsPolicy.WaveCounts requested) {
        WaveMechanicsPolicy.WaveCounts ordinary = WaveMechanicsPolicy.compositionForWave(2, requested);
        int total = ordinary.total();
        if (total > MAX_TOTAL_MOBS) {
            throw new IllegalArgumentException("Wave 2 reserve exceeds the bounded event hard cap");
        }
        if (total < OPENING_GROUP_COUNT) return List.of();

        int lateBudget = Math.min(LATE_REINFORCEMENT_BUDGET, total - OPENING_GROUP_COUNT);
        int openingBudget = total - lateBudget;
        int lateGroupCount = lateBudget > 0 ? 1 : 0;
        int groupCount = OPENING_GROUP_COUNT + lateGroupCount;
        int[][] counts = new int[groupCount][3];
        int[] groupCapacity = new int[groupCount];
        groupCapacity[0] = (openingBudget + 1) / 2;
        groupCapacity[1] = openingBudget / 2;
        if (lateGroupCount > 0) groupCapacity[OPENING_GROUP_COUNT] = lateBudget;
        int[] groupSize = new int[groupCount];
        int[] remaining = {
                Math.max(0, ordinary.endermen()),
                Math.max(0, ordinary.spiders()),
                Math.max(0, ordinary.skeletons())};

        // Balance the two opening groups while preserving each ordinary role.
        for (int type = 0; type < remaining.length; type++) {
            int openingSlots = openingBudget - groupSize[0] - groupSize[1];
            int openingCount = Math.min(remaining[type], openingSlots);
            for (int index = 0; index < openingCount; index++) {
                int selected = groupSize[0] <= groupSize[1] ? 0 : 1;
                if (groupSize[selected] >= groupCapacity[selected]) selected = 1 - selected;
                if (groupSize[selected] >= groupCapacity[selected]) {
                    throw new IllegalStateException("Wave 2 opening reserve allocation is inconsistent");
                }
                counts[selected][type]++;
                groupSize[selected]++;
            }
            remaining[type] -= openingCount;
        }

        // Deliver the reserved late roster together as the third and final group.
        int lateCursor = 0;
        for (int type = 0; type < remaining.length; type++) {
            while (remaining[type] > 0) {
                int selected = -1;
                for (int offset = 0; offset < lateGroupCount; offset++) {
                    int candidate = OPENING_GROUP_COUNT + (lateCursor + offset) % lateGroupCount;
                    if (groupSize[candidate] < groupCapacity[candidate]) {
                        selected = candidate;
                        lateCursor = (candidate - OPENING_GROUP_COUNT + 1) % lateGroupCount;
                        break;
                    }
                }
                if (selected < 0) {
                    throw new IllegalStateException("Wave 2 reinforcement reserve allocation is inconsistent");
                }
                counts[selected][type]++;
                groupSize[selected]++;
                remaining[type]--;
            }
        }

        reserveFirstLateEnderman(counts, groupCount);

        int firstLateElite = counts.length > FIRST_LATE_GROUP - 1
                && counts[FIRST_LATE_GROUP - 1][2] > 0 ? 1 : 0;
        if (firstLateElite > 0) counts[FIRST_LATE_GROUP - 1][2]--;
        List<WaveMechanicsPolicy.WaveCounts> groups = new ArrayList<>(groupCount);
        for (int group = 0; group < groupCount; group++) {
            groups.add(new WaveMechanicsPolicy.WaveCounts(
                    counts[group][0], counts[group][1], counts[group][2], 0,
                    group == FIRST_LATE_GROUP - 1 ? firstLateElite : 0));
        }
        return List.copyOf(groups);
    }

    /** Keep one existing Enderman slot in the first late group for the server-owned phantom activation. */
    private static void reserveFirstLateEnderman(int[][] counts, int groupCount) {
        int lateGroup = FIRST_LATE_GROUP - 1;
        if (groupCount <= lateGroup || counts[lateGroup][0] > 0) return;
        int replacementType = counts[lateGroup][1] > 0 ? 1 : counts[lateGroup][2] > 0 ? 2 : -1;
        if (replacementType < 0) return;

        for (int openingGroup = 0; openingGroup < OPENING_GROUP_COUNT; openingGroup++) {
            if (counts[openingGroup][0] <= 0) continue;
            counts[openingGroup][0]--;
            counts[openingGroup][replacementType]++;
            counts[lateGroup][replacementType]--;
            counts[lateGroup][0]++;
            return;
        }
    }

    /**
     * Whether the two opening groups can launch inside the bounded Wave 2 budget.
     */
    public static boolean canStart(WaveMechanicsPolicy.WaveCounts requested) {
        if (requested == null) return false;
        WaveMechanicsPolicy.WaveCounts ordinary = WaveMechanicsPolicy.compositionForWave(2, requested);
        if (ordinary.total() < OPENING_GROUP_COUNT || ordinary.total() > MAX_TOTAL_MOBS) return false;
        List<WaveMechanicsPolicy.WaveCounts> groups = split(ordinary);
        if (groups.size() < OPENING_GROUP_COUNT || groups.size() > MAX_GROUP_COUNT
                || groups.stream().anyMatch(group -> group == null || group.total() <= 0
                || group.total() > MAX_ACTIVE_GROUP_MOBS)) {
            return false;
        }
        return groups.stream().mapToInt(WaveMechanicsPolicy.WaveCounts::total).sum() == ordinary.total();
    }

    /**
     * Sanitize each group at its spawn boundary. One approved elite skeleton
     * may replace an ordinary skeleton in the first late pressure group.
     */
    public static WaveMechanicsPolicy.WaveCounts sanitizeForSpawn(
            int groupNumber, WaveMechanicsPolicy.WaveCounts requested) {
        WaveMechanicsPolicy.WaveCounts ordinary = WaveMechanicsPolicy.compositionForWave(2, requested);
        if (groupNumber != FIRST_LATE_GROUP || requested == null) return ordinary;
        int eliteSkeletons = Math.min(1, Math.max(0, requested.eliteSkeletons()));
        return new WaveMechanicsPolicy.WaveCounts(ordinary.endermen(), ordinary.spiders(),
                Math.max(0, ordinary.skeletons() - eliteSkeletons), 0, eliteSkeletons);
    }

    public static long nextGroupSpawnAt(long groupClearedAtMillis) {
        if (groupClearedAtMillis < 0L) {
            throw new IllegalArgumentException("group clear time must not be negative");
        }
        return groupClearedAtMillis;
    }

    /**
     * Start the first pressure batch immediately. Hold the second substantial
     * batch until the second mark begins, then gate the final reserve behind
     * two completed mark periods.
     */
    public static boolean maySpawnNextGroup(int spawnedGroups,
                                            int completedMarkPeriods,
                                            int lastLateGroupMarkPeriod) {
        if (spawnedGroups < 0 || spawnedGroups >= MAX_GROUP_COUNT
                || completedMarkPeriods < 0
                || completedMarkPeriods > WaveTwoHuntPolicy.MAX_COMPLETED_MARK_PERIODS
                || lastLateGroupMarkPeriod < 0 || lastLateGroupMarkPeriod > completedMarkPeriods) {
            return false;
        }
        int nextGroupNumber = spawnedGroups + 1;
        if (nextGroupNumber == 1) return true;
        if (nextGroupNumber == 2) return completedMarkPeriods >= 1;
        if (nextGroupNumber == FIRST_LATE_GROUP) {
            return completedMarkPeriods >= WaveTwoHuntPolicy.MIN_MARK_PERIODS
                    && lastLateGroupMarkPeriod == 0;
        }
        return false;
    }

    /** Reject durable counters that could skip a group or award a clear on resume. */
    public static boolean hasConsistentProgress(int completedStages, int spawnedGroups,
                                                boolean groupCleared) {
        if (completedStages < 0 || completedStages > STAGE_COUNT
                || spawnedGroups < 0 || spawnedGroups > STAGE_COUNT
                || spawnedGroups < completedStages
                || spawnedGroups > completedStages + 1) {
            return false;
        }
        if (groupCleared) {
            return completedStages > 0 && spawnedGroups == completedStages;
        }
        if (completedStages == STAGE_COUNT) return false;
        return completedStages == 0 && spawnedGroups == 0
                || spawnedGroups == completedStages + 1;
    }

    /**
     * Recover the next zero-based schedule index from durable counters and
     * group markers. The entity marker closes the crash window between a
     * group's spawn and the state snapshot that records it.
     */
    public static int recoveryNextGroupIndex(int completedStages, int persistedGroupsSpawned,
                                             int highestEntityGroup, boolean groupCleared) {
        int completed = Math.max(0, Math.min(STAGE_COUNT, completedStages));
        int persisted = Math.max(0, Math.min(STAGE_COUNT, persistedGroupsSpawned));
        int entity = Math.max(0, Math.min(STAGE_COUNT, highestEntityGroup));
        int spawned = Math.max(persisted, entity);
        if (groupCleared && spawned == completed) return spawned;
        int expectedActiveGroup = completed == 0 ? 0 : Math.min(STAGE_COUNT, completed + 1);
        return Math.max(spawned, expectedActiveGroup);
    }

    public static int spawnedMobOffset(List<WaveMechanicsPolicy.WaveCounts> groups, int groupIndex) {
        if (groups == null) return 0;
        int end = Math.max(0, Math.min(groups.size(), groupIndex));
        return groups.subList(0, end).stream().mapToInt(WaveMechanicsPolicy.WaveCounts::total).sum();
    }

    public static boolean markExpired(long nowMillis, long markDeadlineMillis) {
        return nowMillis >= markDeadlineMillis;
    }
}
