import me.copimine.endevent.domain.WaveTwoHuntPolicy;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class WaveTwoHuntPolicyTest {
    private static final UUID PLAYER_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID PLAYER_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID PLAYER_C = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    public static void main(String[] args) {
        markRotatesAtFortyFiveSecondDeadline();
        initialAssignmentsRequireTwoPeriodsAndDistinctPlayersWhenPossible();
        rotationAvoidsThePreviousTargetWhenPossible();
        clearedBatchDoesNotWaitForAnotherMarkPeriod();
        initialAndLateReinforcementsRespectBothBudgets();
        System.out.println("WaveTwoHuntPolicyTest OK");
    }

    private static void markRotatesAtFortyFiveSecondDeadline() {
        check(WaveTwoHuntPolicy.MARK_ROTATION_MILLIS == 45_000L,
                "Wave 2 mark rotation lasts forty-five seconds");
        long deadline = WaveTwoHuntPolicy.markDeadline(10_000L);
        check(deadline == 55_000L, "mark deadline is derived from its start time");
        check(!WaveTwoHuntPolicy.markExpired(54_999L, deadline),
                "mark remains active one millisecond before its deadline");
        check(WaveTwoHuntPolicy.markExpired(55_000L, deadline),
                "mark expires at its exact deadline");
        check(!WaveTwoHuntPolicy.markExpired(-1L, deadline)
                        && !WaveTwoHuntPolicy.markExpired(55_000L, 0L),
                "invalid clocks and unset deadlines do not expire an assignment");
    }

    private static void initialAssignmentsRequireTwoPeriodsAndDistinctPlayersWhenPossible() {
        check(WaveTwoHuntPolicy.initialMarkAssignments(List.of(), 0L).isEmpty(),
                "no eligible participants means no mark assignments");
        check(WaveTwoHuntPolicy.initialMarkAssignments(List.of(PLAYER_A), 0L)
                        .equals(List.of(PLAYER_A, PLAYER_A)),
                "one eligible participant still receives the minimum two mark periods");
        List<UUID> pair = WaveTwoHuntPolicy.initialMarkAssignments(
                Arrays.asList(PLAYER_A, PLAYER_A, null, PLAYER_B, PLAYER_C), 0L);
        check(pair.equals(List.of(PLAYER_A, PLAYER_B)),
                "the first two eligible mark assignments use distinct roster members");
        check(WaveTwoHuntPolicy.initialMarkAssignments(List.of(PLAYER_A, PLAYER_B, PLAYER_C), 1L)
                        .equals(List.of(PLAYER_B, PLAYER_C)),
                "the assignment cursor rotates the starting pair deterministically");
    }

    private static void rotationAvoidsThePreviousTargetWhenPossible() {
        check(WaveTwoHuntPolicy.nextMarkTarget(
                        List.of(PLAYER_A, PLAYER_B, PLAYER_C), PLAYER_A, 0L).equals(PLAYER_B),
                "rotation skips the previous target when another participant is eligible");
        check(WaveTwoHuntPolicy.nextMarkTarget(
                        List.of(PLAYER_A, PLAYER_B), PLAYER_A, 1L).equals(PLAYER_B),
                "cursor-based selection remains fair across a two-player roster");
        check(WaveTwoHuntPolicy.nextMarkTarget(List.of(PLAYER_A), PLAYER_A, 99L).equals(PLAYER_A),
                "rotation can retain the only eligible participant");
        check(WaveTwoHuntPolicy.nextMarkTarget(List.of(), PLAYER_A, 0L) == null,
                "rotation waits when no eligible participant exists");
    }

    private static void clearedBatchDoesNotWaitForAnotherMarkPeriod() {
        check(!WaveTwoHuntPolicy.mayCompleteBatch(true, 0),
                "a cleared batch still requires the minimum two mark periods");
        check(!WaveTwoHuntPolicy.mayCompleteBatch(true, 1),
                "one completed mark period is not enough");
        check(!WaveTwoHuntPolicy.mayCompleteBatch(false, 2),
                "the two mark periods do not clear a live combat batch");
        check(WaveTwoHuntPolicy.mayCompleteBatch(true, 2),
                "a cleared batch advances as soon as the second period is complete");
        check(WaveTwoHuntPolicy.mayCompleteBatch(true, 3),
                "no third forty-five-second period is required");
    }

    private static void initialAndLateReinforcementsRespectBothBudgets() {
        check(WaveTwoHuntPolicy.initialReinforcementCount(8, 6, 5) == 5,
                "initial reinforcement respects the remaining total reserve");
        check(WaveTwoHuntPolicy.initialReinforcementCount(8, 3, 20) == 3,
                "initial reinforcement respects active pressure slots");
        check(WaveTwoHuntPolicy.initialReinforcementCount(-1, 3, 20) == 0
                        && WaveTwoHuntPolicy.initialReinforcementCount(5, -1, 20) == 0
                        && WaveTwoHuntPolicy.initialReinforcementCount(5, 3, -1) == 0,
                "negative initial inputs fail closed to zero");

        check(WaveTwoHuntPolicy.lateReinforcementCount(20, 20) == 2,
                "late reinforcement never exceeds its two-mob ceiling");
        check(WaveTwoHuntPolicy.lateReinforcementCount(1, 20) == 1,
                "late reinforcement respects a single free pressure slot");
        check(WaveTwoHuntPolicy.lateReinforcementCount(20, 1) == 1,
                "late reinforcement respects a one-mob remaining reserve");
        check(WaveTwoHuntPolicy.lateReinforcementCount(0, 10) == 0
                        && WaveTwoHuntPolicy.lateReinforcementCount(10, 0) == 0
                        && WaveTwoHuntPolicy.lateReinforcementCount(-1, 10) == 0,
                "late reinforcement fails closed when either budget is exhausted");

        int initial = WaveTwoHuntPolicy.initialReinforcementCount(4, 8, 5);
        int remaining = 5 - initial;
        int late = WaveTwoHuntPolicy.lateReinforcementCount(8, remaining);
        check(initial == 4 && late == 1 && initial + late <= 5,
                "initial and late additions together never exceed the supplied total budget");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
