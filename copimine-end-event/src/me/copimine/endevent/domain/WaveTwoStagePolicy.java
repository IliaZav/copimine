package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;

/** Deterministic pacing and composition for the three bounded Wave 2 groups. */
public final class WaveTwoStagePolicy {
    public static final int STAGE_COUNT = 3;
    /** Wave 2 may exceed generic pressure only inside this three-stage reserve. */
    public static final int MAX_TOTAL_MOBS = PressureBudgetController.HARD_CAP;
    /** Balanced groups from the global Wave 2 cap never exceed this live group size. */
    public static final int MAX_ACTIVE_GROUP_MOBS = (MAX_TOTAL_MOBS + STAGE_COUNT - 1) / STAGE_COUNT;
    public static final long MARK_DURATION_MILLIS = 20_000L;
    public static final long NEXT_GROUP_PAUSE_MILLIS = 5_000L;

    private WaveTwoStagePolicy() { }

    /**
     * Split the configured/scaled count into three balanced ordinary groups.
     * Stale elite entries are normalized using the same Wave 2 rules as live
     * spawning, so this policy can never create a new elite.
     */
    public static List<WaveMechanicsPolicy.WaveCounts> split(
            WaveMechanicsPolicy.WaveCounts requested) {
        WaveMechanicsPolicy.WaveCounts ordinary = WaveMechanicsPolicy.compositionForWave(2, requested);
        if (ordinary.total() > MAX_TOTAL_MOBS) {
            throw new IllegalArgumentException("Wave 2 reserve exceeds the bounded event hard cap");
        }
        int[][] counts = new int[STAGE_COUNT][3];
        int role = 0;
        int mobOrdinal = 0;
        int[] totals = {
                Math.max(0, ordinary.endermen()),
                Math.max(0, ordinary.spiders()),
                Math.max(0, ordinary.skeletons())};
        for (int type = 0; type < totals.length; type++) {
            for (int index = 0; index < totals[type]; index++) {
                role = type;
                counts[mobOrdinal++ % STAGE_COUNT][role]++;
            }
        }
        int finalEliteSkeletons = ordinary.skeletons() > 0 ? 1 : 0;
        if (finalEliteSkeletons > 0) {
            if (counts[STAGE_COUNT - 1][2] > 0) {
                counts[STAGE_COUNT - 1][2]--;
            } else {
                for (int stage = 0; stage < STAGE_COUNT - 1; stage++) {
                    if (counts[stage][2] > 0) {
                        counts[stage][2]--;
                        break;
                    }
                }
            }
        }
        List<WaveMechanicsPolicy.WaveCounts> groups = new ArrayList<>(STAGE_COUNT);
        for (int stage = 0; stage < STAGE_COUNT; stage++) {
            groups.add(new WaveMechanicsPolicy.WaveCounts(
                    counts[stage][0], counts[stage][1], counts[stage][2], 0,
                    stage == STAGE_COUNT - 1 ? finalEliteSkeletons : 0));
        }
        return List.copyOf(groups);
    }

    /**
     * Whether a scaled Wave 2 composition can satisfy all three kill stages.
     * Each stage must contain at least one mob, and the final group must be
     * able to promote one skeleton without adding roster pressure.
     */
    public static boolean canStart(WaveMechanicsPolicy.WaveCounts requested) {
        if (requested == null) return false;
        WaveMechanicsPolicy.WaveCounts ordinary = WaveMechanicsPolicy.compositionForWave(2, requested);
        if (ordinary.total() < STAGE_COUNT || ordinary.total() > MAX_TOTAL_MOBS
                || ordinary.skeletons() <= 0) return false;
        List<WaveMechanicsPolicy.WaveCounts> groups = split(ordinary);
        if (groups.size() != STAGE_COUNT
                || groups.stream().anyMatch(group -> group == null || group.total() <= 0
                || group.total() > MAX_ACTIVE_GROUP_MOBS)) {
            return false;
        }
        WaveMechanicsPolicy.WaveCounts finalGroup = groups.get(STAGE_COUNT - 1);
        return finalGroup.eliteEndermen() == 0 && finalGroup.eliteSkeletons() == 1;
    }

    /**
     * Sanitize a group at its final spawn boundary. The one approved elite
     * replaces an ordinary skeleton in group three; legacy elite slots are
     * still folded into ordinary roles and cannot leak into earlier groups.
     */
    public static WaveMechanicsPolicy.WaveCounts sanitizeForSpawn(
            int groupNumber, WaveMechanicsPolicy.WaveCounts requested) {
        WaveMechanicsPolicy.WaveCounts ordinary = WaveMechanicsPolicy.compositionForWave(2, requested);
        if (groupNumber != STAGE_COUNT || requested == null) return ordinary;
        int eliteSkeletons = Math.min(1, Math.max(0, requested.eliteSkeletons()));
        return new WaveMechanicsPolicy.WaveCounts(ordinary.endermen(), ordinary.spiders(),
                Math.max(0, ordinary.skeletons() - eliteSkeletons), 0, eliteSkeletons);
    }

    public static long nextGroupSpawnAt(long groupClearedAtMillis) {
        if (groupClearedAtMillis < 0L) {
            throw new IllegalArgumentException("group clear time must not be negative");
        }
        return Math.addExact(groupClearedAtMillis, NEXT_GROUP_PAUSE_MILLIS);
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
