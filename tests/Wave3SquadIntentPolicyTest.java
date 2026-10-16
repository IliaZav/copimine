import me.copimine.endevent.domain.Wave3SquadIntentPolicy;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Wave3SquadIntentPolicyTest {
    public static void main(String[] args) {
        testSingleActiveGateIsAssignedToOnlyOneSquad();
        testGateAssignmentsAreDistinctAndLimitedToAThirdOfSquads();
        testConfiguredCapAndMissingObjectivesKeepOtherSquadsOnPlayerPressure();
        testClosedAndCapturedGatesAreNeverAssigned();
        testStillValidAssignmentsRemainStableAcrossTicks();
        testInvalidAndDuplicateInputsAreHandledDeterministically();
        System.out.println("Wave3SquadIntentPolicyTest OK");
    }

    private static void testSingleActiveGateIsAssignedToOnlyOneSquad() {
        Map<Long, Integer> gateBySquad = Wave3SquadIntentPolicy.gateAssignments(
                List.of(40L, 10L, 30L, 20L), List.of(activeGate(2)), 4);

        check(gateBySquad.equals(Map.of(10L, 2)),
                "one active gate must be assigned to one squad only");
    }

    private static void testGateAssignmentsAreDistinctAndLimitedToAThirdOfSquads() {
        List<Long> squads = List.of(8L, 2L, 10L, 1L, 4L, 6L, 3L, 9L, 5L, 7L);
        Map<Long, Integer> gateBySquad = Wave3SquadIntentPolicy.gateAssignments(
                squads, List.of(activeGate(2), activeGate(0), activeGate(1), activeGate(1)), 4);

        check(gateBySquad.size() == 3,
                "ten squads may commit at most one third to gate objectives");
        check(Set.copyOf(gateBySquad.values()).equals(Set.of(0, 1, 2)),
                "different assigned squads must receive distinct active gates");
        check(gateBySquad.keySet().stream().allMatch(squads::contains),
                "only live squads may receive a gate objective");
    }

    private static void testConfiguredCapAndMissingObjectivesKeepOtherSquadsOnPlayerPressure() {
        List<Long> squads = List.of(1L, 2L, 3L, 4L, 5L, 6L);
        Map<Long, Integer> capped = Wave3SquadIntentPolicy.gateAssignments(
                squads, List.of(activeGate(0), activeGate(1), activeGate(2)), 1);
        check(capped.size() == 1, "caller cap must further restrict gate assignments");

        Map<Long, Integer> noGate = Wave3SquadIntentPolicy.gateAssignments(
                squads, List.of(), 3);
        check(noGate.isEmpty(), "without an active gate, every squad stays on player pressure");

        Map<Long, Integer> oneSquad = Wave3SquadIntentPolicy.gateAssignments(
                List.of(7L), List.of(activeGate(0), activeGate(1), activeGate(2)), 3);
        check(oneSquad.equals(Map.of(7L, 0)),
                "a solo squad may pursue one gate and must not fan out to every gate");
    }

    private static void testClosedAndCapturedGatesAreNeverAssigned() {
        Map<Long, Integer> gateBySquad = Wave3SquadIntentPolicy.gateAssignments(
                List.of(1L, 2L, 3L, 4L, 5L, 6L),
                List.of(activeGate(0), closedGate(1), capturedGate(2), activeGate(3)), 2);

        check(gateBySquad.size() == 2, "only active gates may receive a squad");
        check(Set.copyOf(gateBySquad.values()).equals(Set.of(0, 3)),
                "closed and captured gates must not become objectives");

        Map<Long, Integer> conflictingSnapshot = Wave3SquadIntentPolicy.gateAssignments(
                List.of(1L, 2L, 3L, 4L, 5L, 6L),
                List.of(activeGate(1), closedGate(1), activeGate(3)), 2);
        check(conflictingSnapshot.values().stream().noneMatch(index -> index == 1),
                "conflicting gate snapshots must fail closed");
    }

    private static void testStillValidAssignmentsRemainStableAcrossTicks() {
        List<Long> squads = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);
        List<Wave3SquadIntentPolicy.GateTarget> gates = List.of(
                activeGate(0), activeGate(1), activeGate(2));
        Map<Long, Integer> prior = Map.of(1L, 2, 2L, 0, 3L, 1);

        Map<Long, Integer> retained = Wave3SquadIntentPolicy.gateAssignments(
                squads, gates, 3, prior);
        check(retained.equals(prior),
                "still-active gate intents must not be reassigned on each tick");

        Map<Long, Integer> afterGateCapture = Wave3SquadIntentPolicy.gateAssignments(
                squads, List.of(activeGate(0), capturedGate(1), activeGate(2)), 3, prior);
        check(afterGateCapture.equals(Map.of(1L, 2, 2L, 0)),
                "capturing one gate drops only its assignment and retains the others");
    }

    private static void testInvalidAndDuplicateInputsAreHandledDeterministically() {
        Map<Long, Integer> normalized = Wave3SquadIntentPolicy.gateAssignments(
                java.util.Arrays.asList(3L, null, 1L, 3L, -1L),
                java.util.Arrays.asList(activeGate(2), null, activeGate(-1), activeGate(2), activeGate(0)), 2);
        check(normalized.equals(Map.of(1L, 0)),
                "duplicate or invalid IDs must not create extra objective assignments");
        try {
            Wave3SquadIntentPolicy.gateAssignments(List.of(1L), List.of(activeGate(0)), 0);
            throw new AssertionError("non-positive caller cap must be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected: callers must state a positive maximum.
        }
    }

    private static Wave3SquadIntentPolicy.GateTarget activeGate(int index) {
        return new Wave3SquadIntentPolicy.GateTarget(index,
                Wave3SquadIntentPolicy.GateState.ACTIVE);
    }

    private static Wave3SquadIntentPolicy.GateTarget closedGate(int index) {
        return new Wave3SquadIntentPolicy.GateTarget(index,
                Wave3SquadIntentPolicy.GateState.CLOSED);
    }

    private static Wave3SquadIntentPolicy.GateTarget capturedGate(int index) {
        return new Wave3SquadIntentPolicy.GateTarget(index,
                Wave3SquadIntentPolicy.GateState.CAPTURED);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
