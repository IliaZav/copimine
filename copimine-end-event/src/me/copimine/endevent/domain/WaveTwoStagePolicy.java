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
    /** The next subwave starts this long after the previous finite roster is cleared. */
    public static final long NEXT_GROUP_PAUSE_MILLIS = 3_000L;

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

        // Audit wave 2 / W2-011: the ordinary budget is distributed by the
        // authored 25% / 33% / 42% shares instead of two equal halves plus a
        // flat reserve, so later subwaves really awaken more actors (W2-003).
        // Largest-remainder rounding keeps the parts summing to the party and
        // makes the split deterministic for every roster size.
        int lateBudget = Math.min(LATE_REINFORCEMENT_BUDGET,
                Math.max(1, total / 5));
        int lateGroupCount = lateBudget > 0 ? 1 : 0;
        int groupCount = OPENING_GROUP_COUNT + lateGroupCount;
        int[] shares = distributeByAuthoredShares(total, groupCount);
        int[][] counts = new int[groupCount][3];
        int[] groupCapacity = new int[groupCount];
        for (int group = 0; group < groupCount; group++) groupCapacity[group] = shares[group];
        int[] groupSize = new int[groupCount];
        int[] remaining = {
                Math.max(0, ordinary.endermen()),
                Math.max(0, ordinary.spiders()),
                Math.max(0, ordinary.skeletons())};

        // Fill the opening subwaves in share order while preserving each ordinary
        // role, then let the final subwave take whatever is left.
        for (int type = 0; type < remaining.length; type++) {
            for (int group = 0; group < OPENING_GROUP_COUNT; group++) {
                int available = groupCapacity[group] - groupSize[group];
                int take = Math.min(remaining[type], Math.max(0, available));
                counts[group][type] += take;
                groupSize[group] += take;
                remaining[type] -= take;
            }
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

    /**
     * Authored share of the ordinary budget per subwave, in per-mille (W2-011).
     *
     * <p>Kept here so the pacing policy has no dependency on the roster policy;
     * the sum is the budget and the parts always sum to the party.</p>
     */
    public static final int[] SUBWAVE_SHARES_PERMILLE = {250, 330, 420};

    /**
     * Split {@code total} into {@code subwaves} parts by the authored shares.
     *
     * <p>Largest-remainder rounding keeps the parts summing to the party, and a
     * final pass makes later subwaves at least as strong as earlier ones, which
     * is the W2-003 escalation rule.</p>
     */
    public static int[] distributeByAuthoredShares(int total, int subwaves) {
        int safeSubwaves = Math.max(1, subwaves);
        int safeTotal = Math.max(0, total);
        int[] parts = new int[safeSubwaves];
        if (safeSubwaves != SUBWAVE_SHARES_PERMILLE.length) {
            int each = safeTotal / safeSubwaves;
            java.util.Arrays.fill(parts, each);
            for (int index = 0; index < safeTotal - each * safeSubwaves; index++) parts[index]++;
            return parts;
        }
        int shareSum = 0;
        for (int share : SUBWAVE_SHARES_PERMILLE) shareSum += share;
        long[] remainders = new long[safeSubwaves];
        int assigned = 0;
        for (int index = 0; index < safeSubwaves; index++) {
            long scaled = (long) safeTotal * SUBWAVE_SHARES_PERMILLE[index];
            parts[index] = (int) (scaled / shareSum);
            remainders[index] = scaled % shareSum;
            assigned += parts[index];
        }
        // Largest remainder first; ties go to the earlier subwave, so the split is
        // fully deterministic. The final reserve honours its own bounded budget
        // straight away, so a small party never overfills the last subwave and
        // strands the remainder on an opening group.
        int finalCapAllocation = LATE_REINFORCEMENT_BUDGET;
        while (assigned < safeTotal) {
            int best = -1;
            for (int index = 0; index < safeSubwaves; index++) {
                if (remainders[index] < 0L) continue;
                if (index == safeSubwaves - 1 && parts[index] >= finalCapAllocation) continue;
                if (best < 0 || remainders[index] > remainders[best]) best = index;
            }
            if (best < 0) {
                // The bounded reserve is full; the surplus belongs to the opening
                // subwaves, never to the capped one.
                for (int index = 0; index < safeSubwaves - 1 && assigned < safeTotal; index++) {
                    parts[index]++;
                    assigned++;
                }
                if (assigned < safeTotal) return parts;
                break;
            }
            parts[best]++;
            remainders[best] = -1L;
            assigned++;
        }
        // W2-003: a later subwave is never weaker than an earlier one when the
        // total permits it — but the final reserve also stays inside its own
        // bounded budget, so escalation must not move actors into the last group
        // past that ceiling.
        int finalCap = LATE_REINFORCEMENT_BUDGET;
        for (int index = 1; index < safeSubwaves; index++) {
            int ceiling = index == safeSubwaves - 1 ? finalCap : Integer.MAX_VALUE;
            if (parts[index] < parts[index - 1] && parts[index - 1] > 1 && parts[index] < ceiling) {
                parts[index - 1]--;
                parts[index]++;
            }
        }
        return parts;
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
        if (requested == null) return ordinary;
        // The third subwave is the late hunt: it must mix skeleton archers with
        // ordinary Endermen pressure. It used to come out as skeletons only,
        // which is exactly what the player reported. At least a third of the
        // bodies stay ordinary Endermen so the marked player still has to move.
        int endermen = ordinary.endermen();
        int skeletons = ordinary.skeletons();
        int total = endermen + skeletons + ordinary.spiders();
        int minimumEndermen = Math.max(1, (total + 2) / 3);
        if (endermen < minimumEndermen) {
            int moved = Math.min(minimumEndermen - endermen, skeletons);
            endermen += moved;
            skeletons -= moved;
        }
        if (groupNumber != FIRST_LATE_GROUP) {
            return new WaveMechanicsPolicy.WaveCounts(endermen, ordinary.spiders(), skeletons,
                    ordinary.eliteEndermen(), 0);
        }
        int eliteSkeletons = Math.min(1, Math.max(0, requested.eliteSkeletons()));
        return new WaveMechanicsPolicy.WaveCounts(endermen, ordinary.spiders(),
                Math.max(0, skeletons - eliteSkeletons), ordinary.eliteEndermen(), eliteSkeletons);
    }

    public static long nextGroupSpawnAt(long groupClearedAtMillis) {
        if (groupClearedAtMillis < 0L) {
            throw new IllegalArgumentException("group clear time must not be negative");
        }
        return Math.addExact(groupClearedAtMillis, NEXT_GROUP_PAUSE_MILLIS);
    }

    /**
     * Every subwave starts once the previous finite roster is cleared. The wave
     * holds exactly {@link #MAX_GROUP_COUNT} subwaves; there is no mark gate.
     */
    public static boolean maySpawnNextGroup(int spawnedGroups) {
        return spawnedGroups >= 0 && spawnedGroups < MAX_GROUP_COUNT;
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
}
