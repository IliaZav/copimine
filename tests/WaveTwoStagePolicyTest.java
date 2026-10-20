package me.copimine.endevent.domain;

import java.util.List;

public final class WaveTwoStagePolicyTest {
    public static void main(String[] args) {
        splitIntoThreeBoundedGroupsWithoutChangingComposition();
        rejectUnlaunchableThreeStageCompositions();
        spawnBoundaryKeepsOnlyTheApprovedFinalElite();
        everyNonEmptyStageGetsAGroupWhenEnoughMobsExist();
        waveTwoPressureExceptionIsBoundedToThreeGroups();
        waveTwoRosterScalesAcrossTheSupportedPartySizes();
        stagesUseTwentySecondMarksAndFiveSecondGroupPause();
        restartRecoversProgressAndTheActiveSpawnBoundary();
        restartRejectsInconsistentDurableProgress();
        System.out.println("WaveTwoStagePolicyTest OK");
    }

    private static void splitIntoThreeBoundedGroupsWithoutChangingComposition() {
        var total = new WaveMechanicsPolicy.WaveCounts(8, 4, 5, 2, 1);
        List<WaveMechanicsPolicy.WaveCounts> groups = WaveTwoStagePolicy.split(total);
        check(groups.size() == 3, "Wave 2 always plans three groups");
        check(groups.stream().mapToInt(WaveMechanicsPolicy.WaveCounts::total).sum() == 20,
                "group split preserves the elite-normalized mob total");
        check(groups.get(0).eliteEndermen() == 0 && groups.get(0).eliteSkeletons() == 0
                        && groups.get(1).eliteEndermen() == 0 && groups.get(1).eliteSkeletons() == 0,
                "the first two Wave 2 groups stay free of elites");
        check(groups.get(2).eliteEndermen() == 0 && groups.get(2).eliteSkeletons() == 1,
                "the final Wave 2 group converts one skeleton into an elite");
        check(groups.stream().mapToInt(WaveMechanicsPolicy.WaveCounts::total).sum() == total.total(),
                "the group-3 elite replaces a skeleton without increasing pressure");
        check(groups.equals(WaveTwoStagePolicy.split(total)), "group plan is deterministic");
    }

    private static void everyNonEmptyStageGetsAGroupWhenEnoughMobsExist() {
        List<WaveMechanicsPolicy.WaveCounts> groups = WaveTwoStagePolicy.split(
                new WaveMechanicsPolicy.WaveCounts(7, 2, 0, 0, 0));
        check(groups.stream().allMatch(group -> group.total() > 0),
                "three or more ordinary mobs are spread across all three stages");
        int smallest = groups.stream().mapToInt(WaveMechanicsPolicy.WaveCounts::total).min().orElse(0);
        int largest = groups.stream().mapToInt(WaveMechanicsPolicy.WaveCounts::total).max().orElse(0);
        check(largest - smallest <= 1, "stage groups stay balanced");
    }

    private static void waveTwoPressureExceptionIsBoundedToThreeGroups() {
        var defaultRoster = new WaveMechanicsPolicy.WaveCounts(12, 12, 6, 0, 0);
        var defaultGroups = WaveTwoStagePolicy.split(defaultRoster);
        check(defaultGroups.stream().mapToInt(WaveMechanicsPolicy.WaveCounts::total).sum() == 30,
                "the Wave 2 exception preserves the complete default reserve");
        check(defaultGroups.stream().allMatch(group -> group.total() == 10),
                "two-player Wave 2 uses three ten-mob groups");

        var maximum = WaveTwoStagePolicy.split(new WaveMechanicsPolicy.WaveCounts(56, 0, 0, 0, 0));
        check(maximum.stream().mapToInt(WaveMechanicsPolicy.WaveCounts::total).sum()
                        == WaveTwoStagePolicy.MAX_TOTAL_MOBS,
                "Wave 2 cannot exceed the absolute global entity hard cap");
        check(maximum.stream().allMatch(group -> group.total() <= WaveTwoStagePolicy.MAX_ACTIVE_GROUP_MOBS),
                "the active group remains bounded even at the global Wave 2 cap");
        check(!WaveTwoStagePolicy.canStart(new WaveMechanicsPolicy.WaveCounts(57, 0, 1, 0, 0)),
                "oversized corrupted Wave 2 state is rejected instead of silently truncated");
        boolean rejected = false;
        try {
            WaveTwoStagePolicy.split(new WaveMechanicsPolicy.WaveCounts(57, 0, 1, 0, 0));
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "the group planner refuses an over-cap reserve");
    }

    private static void waveTwoRosterScalesAcrossTheSupportedPartySizes() {
        var configured = new WaveMechanicsPolicy.WaveCounts(12, 12, 6, 0, 0);
        int[] playerCounts = {2, 5, 6, 10, 20};
        int[] expectedTotals = {30, 34, 36, 42, 56};
        int[] expectedGroupThreeElites = {1, 1, 1, 1, 1};
        int[][] expectedGroupSizes = {{10, 10, 10}, {12, 11, 11}, {12, 12, 12},
                {14, 14, 14}, {19, 19, 18}};
        for (int index = 0; index < playerCounts.length; index++) {
            int players = playerCounts[index];
            var scaled = WaveScalingPolicy.scale(2, configured, players,
                    WaveTwoStagePolicy.MAX_TOTAL_MOBS);
            List<WaveMechanicsPolicy.WaveCounts> groups = WaveTwoStagePolicy.split(scaled);
            int total = groups.stream().mapToInt(WaveMechanicsPolicy.WaveCounts::total).sum();

            check(total == expectedTotals[index], players + " players preserve the expected scaled Wave 2 roster");
            check(groups.size() == WaveTwoStagePolicy.STAGE_COUNT,
                    players + " players retain exactly three sequential groups");
            check(groups.stream().allMatch(group -> group.total() > 0),
                    players + " players retain a meaningful roster in every group");
            for (int group = 0; group < groups.size(); group++) {
                check(groups.get(group).total() == expectedGroupSizes[index][group],
                        players + " players receive the expected group-" + (group + 1) + " size");
            }
            int smallest = groups.stream().mapToInt(WaveMechanicsPolicy.WaveCounts::total).min().orElse(0);
            int largest = groups.stream().mapToInt(WaveMechanicsPolicy.WaveCounts::total).max().orElse(0);
            check(largest - smallest <= 1,
                    players + " players receive balanced group sizes");
            check(groups.stream().limit(2).allMatch(group ->
                            group.eliteEndermen() == 0 && group.eliteSkeletons() == 0),
                    players + " players have no Wave 2 elite before the final group");
            check(groups.get(2).eliteEndermen() + groups.get(2).eliteSkeletons()
                            == expectedGroupThreeElites[index]
                            && groups.get(2).eliteEndermen() == 0
                            && groups.get(2).eliteSkeletons() == expectedGroupThreeElites[index],
                    players + " players receive exactly the approved replacement elite count in group three");
            check(groups.stream().mapToInt(WaveMechanicsPolicy.WaveCounts::skeletons).sum()
                            + groups.stream().mapToInt(WaveMechanicsPolicy.WaveCounts::eliteSkeletons).sum()
                            == scaled.skeletons(),
                    players + " players do not grow the roster when the final skeleton is promoted");
            check(groups.stream().allMatch(group -> group.total() <= WaveTwoStagePolicy.MAX_ACTIVE_GROUP_MOBS),
                    players + " players stay below the simultaneous active-group cap");
        }
    }

    private static void rejectUnlaunchableThreeStageCompositions() {
        var configured = new WaveMechanicsPolicy.WaveCounts(12, 12, 6, 0, 0);
        for (int hardCap = 0; hardCap <= 2; hardCap++) {
            var scaled = WaveScalingPolicy.scale(2, configured, 2, hardCap);
            check(scaled.total() == hardCap,
                    "the scaling fixture must produce the requested low hard-cap total");
            check(!WaveTwoStagePolicy.canStart(scaled),
                    "a zero-, one-, or two-mob scaled roster cannot start three hunt groups");
        }
        check(!WaveTwoStagePolicy.canStart(new WaveMechanicsPolicy.WaveCounts(5, 4, 0, 0, 0)),
                "a roster without skeletons cannot provide the required group-3 elite");
        check(!WaveTwoStagePolicy.canStart(null), "a missing Wave 2 composition is rejected");
        check(WaveTwoStagePolicy.canStart(configured),
                "the current default Wave 2 composition remains launchable");
    }

    private static void spawnBoundaryKeepsOnlyTheApprovedFinalElite() {
        var stale = new WaveMechanicsPolicy.WaveCounts(2, 1, 3, 4, 2);
        var earlier = WaveTwoStagePolicy.sanitizeForSpawn(1, stale);
        var finalGroup = WaveTwoStagePolicy.sanitizeForSpawn(3,
                new WaveMechanicsPolicy.WaveCounts(2, 1, 3, 0, 1));
        check(earlier.eliteEndermen() == 0 && earlier.eliteSkeletons() == 0,
                "stale elite slots are normalized in groups one and two");
        check(finalGroup.eliteEndermen() == 0 && finalGroup.eliteSkeletons() == 1
                        && finalGroup.skeletons() == 3 && finalGroup.total() == 7,
                "group three keeps one explicitly requested skeleton elite as a conversion");
    }

    private static void stagesUseTwentySecondMarksAndFiveSecondGroupPause() {
        check(WaveTwoStagePolicy.MARK_DURATION_MILLIS == 20_000L,
                "the hunt target changes at twenty-second boundaries");
        check(WaveTwoStagePolicy.NEXT_GROUP_PAUSE_MILLIS == 5_000L,
                "the next bounded group waits five seconds after its predecessor dies");
        check(WaveTwoStagePolicy.nextGroupSpawnAt(42_000L) == 47_000L,
                "next group deadline is derived from the confirmed group-clear time");
        check(WaveTwoStagePolicy.markExpired(20_000L, 20_000L),
                "the current mark expires at its exact twenty-second boundary");
        check(!WaveTwoStagePolicy.markExpired(19_999L, 20_000L),
                "the current mark remains stable before the boundary");
    }

    private static void restartRecoversProgressAndTheActiveSpawnBoundary() {
        check(WaveTwoStagePolicy.recoveryNextGroupIndex(0, 0, 0, false) == 0,
                "a crash before the first group resumes by spawning group one");
        check(WaveTwoStagePolicy.recoveryNextGroupIndex(0, 0, 1, false) == 1,
                "entity group markers recover a group whose state save was interrupted");
        check(WaveTwoStagePolicy.recoveryNextGroupIndex(1, 1, 0, true) == 1,
                "a cleared group resumes the five-second pause without counting twice");
        check(WaveTwoStagePolicy.recoveryNextGroupIndex(1, 1, 2, true) == 2,
                "a marker recovers the next group across a spawn-save crash window");
        check(WaveTwoStagePolicy.recoveryNextGroupIndex(2, 2, 0, true) == 2,
                "two cleared stages resume with group three pending");
        check(WaveTwoStagePolicy.recoveryNextGroupIndex(3, 3, 3, true) == 3,
                "completed Wave 2 stays bounded at exactly three groups");
        var groups = WaveTwoStagePolicy.split(new WaveMechanicsPolicy.WaveCounts(6, 3, 3, 0, 0));
        check(WaveTwoStagePolicy.spawnedMobOffset(groups, 2)
                        == groups.get(0).total() + groups.get(1).total(),
                "recovered spawn positions continue after the groups already spawned");
    }

    private static void restartRejectsInconsistentDurableProgress() {
        check(WaveTwoStagePolicy.hasConsistentProgress(0, 0, false),
                "fresh Wave 2 state has no completed or spawned group");
        check(WaveTwoStagePolicy.hasConsistentProgress(0, 1, false),
                "the first group can be active before any stage is completed");
        check(WaveTwoStagePolicy.hasConsistentProgress(1, 1, true),
                "a cleared first group has one completed and one spawned stage");
        check(WaveTwoStagePolicy.hasConsistentProgress(1, 2, false),
                "the next group can be active after one completed stage");
        check(WaveTwoStagePolicy.hasConsistentProgress(3, 3, true),
                "the final group checkpoint is internally consistent");
        check(!WaveTwoStagePolicy.hasConsistentProgress(1, 0, false),
                "completed stages cannot exceed the durable spawn count");
        check(!WaveTwoStagePolicy.hasConsistentProgress(0, 2, false),
                "the durable spawn cursor cannot skip a stage");
        check(!WaveTwoStagePolicy.hasConsistentProgress(1, 2, true),
                "a cleared checkpoint cannot claim the next group as spawned");
        check(!WaveTwoStagePolicy.hasConsistentProgress(3, 3, false),
                "all three completed stages must have a cleared final group");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
