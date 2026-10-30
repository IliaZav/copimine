package me.copimine.endevent.domain;

import java.util.List;

public final class WaveTwoStagePolicyTest {
    public static void main(String[] args) {
        splitIntoTwoOpeningBatchesAndBoundedLateGroupsWithoutChangingComposition();
        rejectUnlaunchableTwoOpeningBatches();
        spawnBoundaryKeepsOnlyTheApprovedFirstLateElite();
        everyNonEmptyGroupGetsMobsWhenEnoughExist();
        waveTwoPressureExceptionIsBoundedToThreeGroups();
        expandedConfiguredRosterUsesTheHardCapWithoutAddingGroups();
        waveTwoRosterScalesAcrossTheSupportedPartySizes();
        defaultTwoPlayerPlanReservesAReachablePhantomActivationSlot();
        huntUsesFortyFiveSecondMarksAndImmediateClearAdvancement();
        restartRecoversProgressAndTheActiveSpawnBoundary();
        restartRejectsInconsistentDurableProgress();
        lateGroupsWaitForCompletedMarkPeriods();
        System.out.println("WaveTwoStagePolicyTest OK");
    }

    private static void splitIntoTwoOpeningBatchesAndBoundedLateGroupsWithoutChangingComposition() {
        var total = new WaveMechanicsPolicy.WaveCounts(8, 4, 5, 2, 1);
        List<WaveMechanicsPolicy.WaveCounts> groups = WaveTwoStagePolicy.split(total);
        check(groups.size() == 3, "Wave 2 plans exactly two opening groups and one final reserve group");
        check(groups.stream().mapToInt(WaveMechanicsPolicy.WaveCounts::total).sum() == 20,
                "group split preserves the elite-normalized mob total");
        check(groups.get(0).eliteEndermen() == 0 && groups.get(0).eliteSkeletons() == 0
                        && groups.get(1).eliteEndermen() == 0 && groups.get(1).eliteSkeletons() == 0,
                "the first two Wave 2 groups stay free of elites");
        check(groups.get(2).eliteEndermen() == 0 && groups.get(2).eliteSkeletons() == 1,
                "the first late Wave 2 group converts one skeleton into an elite");
        check(groups.get(2).total() == WaveTwoStagePolicy.LATE_REINFORCEMENT_BUDGET,
                "the final reserve is delivered as one bounded subwave");
        check(groups.stream().mapToInt(WaveMechanicsPolicy.WaveCounts::total).sum() == total.total(),
                "the first late elite replaces a skeleton without increasing pressure");
        check(groups.equals(WaveTwoStagePolicy.split(total)), "group plan is deterministic");
    }

    private static void everyNonEmptyGroupGetsMobsWhenEnoughExist() {
        List<WaveMechanicsPolicy.WaveCounts> groups = WaveTwoStagePolicy.split(
                new WaveMechanicsPolicy.WaveCounts(7, 2, 0, 0, 0));
        check(groups.stream().allMatch(group -> group.total() > 0),
                "a sufficiently large ordinary roster fills every planned group");
        check(groups.size() == 3 && groups.get(0).total() == 1 && groups.get(1).total() == 1
                        && groups.get(2).total() == 7,
                "two balanced opening groups lead into one consolidated final reserve");
    }

    private static void waveTwoPressureExceptionIsBoundedToThreeGroups() {
        var defaultRoster = new WaveMechanicsPolicy.WaveCounts(18, 18, 12, 0, 0);
        var defaultGroups = WaveTwoStagePolicy.split(defaultRoster);
        check(defaultGroups.size() == 3
                        && defaultGroups.stream().mapToInt(WaveMechanicsPolicy.WaveCounts::total).sum() == 48,
                "the Wave 2 exception preserves the complete default reserve");
        check(defaultGroups.get(0).total() == 20 && defaultGroups.get(1).total() == 20
                        && defaultGroups.get(2).total() == 8,
                "the default reserve uses two larger openings and one eight-mob final subwave");

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

    private static void expandedConfiguredRosterUsesTheHardCapWithoutAddingGroups() {
        var doubledConfiguration = new WaveMechanicsPolicy.WaveCounts(36, 36, 24, 0, 0);
        var scaled = WaveScalingPolicy.scale(2, doubledConfiguration, 2,
                WaveTwoStagePolicy.MAX_TOTAL_MOBS);
        List<WaveMechanicsPolicy.WaveCounts> groups = WaveTwoStagePolicy.split(scaled);
        check(scaled.total() == WaveTwoStagePolicy.MAX_TOTAL_MOBS,
                "the doubled Wave 2 configuration fills but never exceeds the global 56-mob reserve");
        check(groups.size() == 3
                        && groups.get(0).total() == 24
                        && groups.get(1).total() == 24
                        && groups.get(2).total() == 8,
                "the larger Wave 2 reserve stays in exactly three bounded subwaves");
    }

    private static void waveTwoRosterScalesAcrossTheSupportedPartySizes() {
        var configured = new WaveMechanicsPolicy.WaveCounts(18, 18, 12, 0, 0);
        int[] playerCounts = {2, 5, 6, 10, 20};
        int[] expectedTotals = {48, 49, 50, 52, 56};
        int[] expectedFirstLateElites = {1, 1, 1, 1, 1};
        int[][] expectedGroupSizes = {{20, 20, 8}, {21, 20, 8},
                {21, 21, 8}, {22, 22, 8}, {24, 24, 8}};
        for (int index = 0; index < playerCounts.length; index++) {
            int players = playerCounts[index];
            var scaled = WaveScalingPolicy.scale(2, configured, players,
                    WaveTwoStagePolicy.MAX_TOTAL_MOBS);
            List<WaveMechanicsPolicy.WaveCounts> groups = WaveTwoStagePolicy.split(scaled);
            int total = groups.stream().mapToInt(WaveMechanicsPolicy.WaveCounts::total).sum();

            check(total == expectedTotals[index], players + " players preserve the expected scaled Wave 2 roster");
            check(groups.size() == WaveTwoStagePolicy.MAX_GROUP_COUNT,
                    players + " players retain exactly three groups");
            check(groups.stream().allMatch(group -> group.total() > 0),
                    players + " players retain a meaningful roster in every planned group");
            for (int group = 0; group < groups.size(); group++) {
                check(groups.get(group).total() == expectedGroupSizes[index][group],
                        players + " players receive the expected group-" + (group + 1) + " size");
            }
            int smallest = groups.stream().mapToInt(WaveMechanicsPolicy.WaveCounts::total).min().orElse(0);
            int largest = groups.stream().mapToInt(WaveMechanicsPolicy.WaveCounts::total).max().orElse(0);
            check(largest - smallest <= WaveTwoStagePolicy.MAX_ACTIVE_GROUP_MOBS - 2,
                    players + " players receive two bounded large opening groups");
            check(groups.stream().limit(2).allMatch(group ->
                            group.eliteEndermen() == 0 && group.eliteSkeletons() == 0),
                    players + " players have no Wave 2 elite before the final group");
            check(groups.get(2).eliteEndermen() + groups.get(2).eliteSkeletons()
                            == expectedFirstLateElites[index]
                            && groups.get(2).eliteEndermen() == 0
                            && groups.get(2).eliteSkeletons() == expectedFirstLateElites[index],
                    players + " players receive the approved replacement elite in the first late group");
            check(groups.stream().mapToInt(WaveMechanicsPolicy.WaveCounts::skeletons).sum()
                            + groups.stream().mapToInt(WaveMechanicsPolicy.WaveCounts::eliteSkeletons).sum()
                            == scaled.skeletons(),
                    players + " players do not grow the roster when the final skeleton is promoted");
            check(groups.stream().allMatch(group -> group.total() <= WaveTwoStagePolicy.MAX_ACTIVE_GROUP_MOBS),
                    players + " players stay below the simultaneous active-group cap");
        }
    }

    private static void rejectUnlaunchableTwoOpeningBatches() {
        var configured = new WaveMechanicsPolicy.WaveCounts(12, 12, 6, 0, 0);
        for (int hardCap = 0; hardCap <= 1; hardCap++) {
            var scaled = WaveScalingPolicy.scale(2, configured, 2, hardCap);
            check(scaled.total() == hardCap,
                    "the scaling fixture must produce the requested low hard-cap total");
            check(!WaveTwoStagePolicy.canStart(scaled),
                    "a zero- or one-mob scaled roster cannot start two opening batches");
        }
        check(WaveTwoStagePolicy.canStart(new WaveMechanicsPolicy.WaveCounts(5, 4, 0, 0, 0)),
                "a roster without skeletons can start because the late elite is optional");
        check(!WaveTwoStagePolicy.canStart(null), "a missing Wave 2 composition is rejected");
        check(WaveTwoStagePolicy.canStart(configured),
                "the current default Wave 2 composition remains launchable");
    }

    private static void defaultTwoPlayerPlanReservesAReachablePhantomActivationSlot() {
        var configured = new WaveMechanicsPolicy.WaveCounts(18, 18, 12, 0, 0);
        var scaled = WaveScalingPolicy.scale(2, configured, 2, WaveTwoStagePolicy.MAX_TOTAL_MOBS);
        List<WaveMechanicsPolicy.WaveCounts> groups = WaveTwoStagePolicy.split(scaled);
        var firstLate = groups.get(WaveTwoStagePolicy.FIRST_LATE_GROUP - 1);

        check(WaveTwoStagePolicy.canStart(scaled), "the normal two-player plan remains launchable");
        check(groups.get(0).total() == 20 && groups.get(1).total() == 20,
                "the larger two-player roster preserves two substantial opening batches");
        check(firstLate.endermen() > 0,
                "the first late group reserves a real Enderman slot for deterministic phantom activation");
        check(groups.stream().mapToInt(WaveMechanicsPolicy.WaveCounts::total).sum() == scaled.total(),
                "the reserved Enderman replaces a late roster slot without increasing the total");

        var fence = new WaveTwoPhantomMaterializationPolicy.Fence("event-1", 1L,
                WaveTwoStagePolicy.FIRST_LATE_GROUP);
        var budget = new WaveTwoPhantomMaterializationPolicy.Budget(0, 2, 1, firstLate.endermen());
        var warning = WaveTwoPhantomMaterializationPolicy.begin(null, fence,
                new WaveTwoPhantomMaterializationPolicy.Candidate(fence, "phantom-00", true), budget);
        check(warning.action() == WaveTwoPhantomMaterializationPolicy.Action.WARNING_STARTED,
                "the normal plan can start activation when its reserved late Enderman slot is reached");
        var materialized = WaveTwoPhantomMaterializationPolicy.finishWarning(
                warning.state(), fence, true, true, budget);
        check(materialized.action() == WaveTwoPhantomMaterializationPolicy.Action.MATERIALIZE_ENTITY
                        && materialized.entityBudgetCost() == 1,
                "the scheduled warning can materialize exactly one owned entity within the reserved slot");
    }

    private static void spawnBoundaryKeepsOnlyTheApprovedFirstLateElite() {
        var stale = new WaveMechanicsPolicy.WaveCounts(2, 1, 3, 4, 2);
        var earlier = WaveTwoStagePolicy.sanitizeForSpawn(1, stale);
        var firstLateGroup = WaveTwoStagePolicy.sanitizeForSpawn(WaveTwoStagePolicy.FIRST_LATE_GROUP,
                new WaveMechanicsPolicy.WaveCounts(2, 1, 3, 0, 1));
        check(earlier.eliteEndermen() == 0 && earlier.eliteSkeletons() == 0,
                "stale elite slots are normalized in groups one and two");
        check(firstLateGroup.eliteEndermen() == 0 && firstLateGroup.eliteSkeletons() == 1
                        && firstLateGroup.skeletons() == 3 && firstLateGroup.total() == 7,
                "group three keeps one explicitly requested skeleton elite as a conversion");
    }

    private static void huntUsesFortyFiveSecondMarksAndImmediateClearAdvancement() {
        check(WaveTwoStagePolicy.MARK_DURATION_MILLIS == 45_000L,
                "the hunt target changes at forty-five-second boundaries");
        check(WaveTwoStagePolicy.NEXT_GROUP_PAUSE_MILLIS == 0L,
                "the next opening group has no empty post-clear delay");
        check(WaveTwoStagePolicy.nextGroupSpawnAt(42_000L) == 42_000L,
                "next opening group becomes eligible as soon as its predecessor is cleared");
        check(WaveTwoStagePolicy.markExpired(45_000L, 45_000L),
                "the current mark expires at its exact forty-five-second boundary");
        check(!WaveTwoStagePolicy.markExpired(44_999L, 45_000L),
                "the current mark remains stable before the boundary");
    }

    private static void restartRecoversProgressAndTheActiveSpawnBoundary() {
        check(WaveTwoStagePolicy.recoveryNextGroupIndex(0, 0, 0, false) == 0,
                "a crash before the first group resumes by spawning group one");
        check(WaveTwoStagePolicy.recoveryNextGroupIndex(0, 0, 1, false) == 1,
                "entity group markers recover a group whose state save was interrupted");
        check(WaveTwoStagePolicy.recoveryNextGroupIndex(1, 1, 0, true) == 1,
                "a cleared group resumes by spawning the next opening batch without counting twice");
        check(WaveTwoStagePolicy.recoveryNextGroupIndex(1, 1, 2, true) == 2,
                "a marker recovers the next group across a spawn-save crash window");
        check(WaveTwoStagePolicy.recoveryNextGroupIndex(2, 2, 0, true) == 2,
                "two cleared stages resume with group three pending");
        check(WaveTwoStagePolicy.recoveryNextGroupIndex(3, 3, 3, true) == 3,
                "completed Wave 2 stays bounded at three groups");
        check(WaveTwoStagePolicy.recoveryNextGroupIndex(6, 6, 6, true) == 6,
                "legacy six-stage checkpoints remain recoverable without spawning more groups");
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
                "the final three-group checkpoint is internally consistent");
        check(WaveTwoStagePolicy.hasConsistentProgress(6, 6, true),
                "legacy six-stage checkpoints remain internally consistent");
        check(!WaveTwoStagePolicy.hasConsistentProgress(1, 0, false),
                "completed stages cannot exceed the durable spawn count");
        check(!WaveTwoStagePolicy.hasConsistentProgress(0, 2, false),
                "the durable spawn cursor cannot skip a stage");
        check(!WaveTwoStagePolicy.hasConsistentProgress(1, 2, true),
                "a cleared checkpoint cannot claim the next group as spawned");
        check(!WaveTwoStagePolicy.hasConsistentProgress(3, 3, false),
                "all three completed groups must have a cleared final group");
    }

    private static void lateGroupsWaitForCompletedMarkPeriods() {
        check(WaveTwoStagePolicy.maySpawnNextGroup(0, 0, 0),
                "the first opening batch starts immediately");
        check(!WaveTwoStagePolicy.maySpawnNextGroup(1, 0, 0),
                "the second substantial batch cannot be spent during the first mark");
        check(WaveTwoStagePolicy.maySpawnNextGroup(1, 1, 0),
                "the second substantial batch starts when the second mark begins");
        check(!WaveTwoStagePolicy.maySpawnNextGroup(2, 1, 0),
                "the first late reinforcement waits for two completed marks");
        check(WaveTwoStagePolicy.maySpawnNextGroup(2, 2, 0),
                "the first late reinforcement starts after the second completed mark");
        check(!WaveTwoStagePolicy.maySpawnNextGroup(3, 3, 2),
                "Wave 2 cannot spawn a fourth group after its final reserve");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
