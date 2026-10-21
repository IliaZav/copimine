import me.copimine.endevent.domain.Wave4PressurePackPolicy;
import me.copimine.endevent.domain.WaveMechanicsPolicy;

import java.util.LinkedHashSet;
import java.util.List;

public final class Wave4PressurePackPolicyTest {
    public static void main(String[] args) {
        var composition = new WaveMechanicsPolicy.WaveCounts(9, 8, 4, 0, 0);
        List<Wave4PressurePackPolicy.Slot> planned = Wave4PressurePackPolicy.plan(composition);
        check(planned.size() == 21, "slot plan must preserve every requested mob");
        check(planned.stream().map(Wave4PressurePackPolicy.Slot::key).distinct().count() == 21,
                "planned slots must be stable and unique");

        var smallRoster = new WaveMechanicsPolicy.WaveCounts(1, 2, 0, 0, 0);
        List<Wave4PressurePackPolicy.Slot> smallPlan = Wave4PressurePackPolicy.plan(smallRoster);
        check(smallPlan.size() == 3
                        && countRole(smallPlan, "ZOMBIE") == 1
                        && countRole(smallPlan, "RIFT_GIANT") == 0,
                "normal zombie pressure must replace an existing slot at small rosters");

        var maxRoster = new WaveMechanicsPolicy.WaveCounts(2, 5, 1, 0, 0);
        List<Wave4PressurePackPolicy.Slot> maxPlan = Wave4PressurePackPolicy.plan(maxRoster);
        check(maxPlan.size() == 8,
                "maximum roster replacements must not add pressure slots");
        check(countRole(maxPlan, "ZOMBIE") == 1,
                "maximum roster retains one ordinary zombie pressure slot");
        check(countRole(maxPlan, "RIFT_GIANT") == 1,
                "maximum roster uses one bounded Giant replacement slot");

        var completed = new LinkedHashSet<String>();
        planned.stream().map(Wave4PressurePackPolicy.Slot::key)
                .filter(key -> !key.equals("CHANNELER:3")).forEach(completed::add);
        List<Wave4PressurePackPolicy.Slot> missing = Wave4PressurePackPolicy.missing(planned, completed);
        check(missing.size() == 1 && missing.get(0).key().equals("CHANNELER:3"),
                "retry plan must contain only the missing role slot");
        check(Wave4PressurePackPolicy.missing(planned,
                        new LinkedHashSet<>(planned.stream().map(Wave4PressurePackPolicy.Slot::key).toList())).isEmpty(),
                "a complete pack must not spawn duplicate slots");

        check(Wave4PressurePackPolicy.shouldAttempt(0, 100, 0), "initial pack attempt is immediately eligible");
        check(!Wave4PressurePackPolicy.shouldAttempt(1, 349, 350), "retry must honor backoff");
        check(Wave4PressurePackPolicy.shouldAttempt(1, 350, 350), "retry becomes eligible at its deadline");
        check(Wave4PressurePackPolicy.retryDelayMillis(1) == 250L
                        && Wave4PressurePackPolicy.retryDelayMillis(2) == 500L
                        && Wave4PressurePackPolicy.retryDelayMillis(3) == 750L,
                "retry delay must increase across failures");
        check(Wave4PressurePackPolicy.exhausted(Wave4PressurePackPolicy.MAX_TOTAL_ATTEMPTS),
                "total attempts must have a hard bound");
        check(!Wave4PressurePackPolicy.shouldAttempt(Wave4PressurePackPolicy.MAX_TOTAL_ATTEMPTS, 10_000, 0),
                "exhausted packs must stop retrying");
        retryOnlyEachFailedSlot(planned);
        retryAfterWholePackFailure(planned);
        System.out.println("Wave4PressurePackPolicyTest OK");
    }

    private static void retryOnlyEachFailedSlot(List<Wave4PressurePackPolicy.Slot> planned) {
        for (int failureIndex = 0; failureIndex < planned.size(); failureIndex++) {
            var completed = new LinkedHashSet<String>();
            for (int index = 0; index < planned.size(); index++) {
                if (index != failureIndex) completed.add(planned.get(index).key());
            }
            List<Wave4PressurePackPolicy.Slot> missing = Wave4PressurePackPolicy.missing(planned, completed);
            check(missing.size() == 1 && missing.get(0).key().equals(planned.get(failureIndex).key()),
                    "a failed first, middle, or last slot is the only retry candidate");
            completed.add(missing.get(0).key());
            check(Wave4PressurePackPolicy.missing(planned, completed).isEmpty(),
                    "a successful retry completes without repeating prior successful slots");
        }
    }

    private static void retryAfterWholePackFailure(List<Wave4PressurePackPolicy.Slot> planned) {
        var completed = new LinkedHashSet<String>();
        List<Wave4PressurePackPolicy.Slot> firstRetry = Wave4PressurePackPolicy.missing(planned, completed);
        check(firstRetry.size() == planned.size(), "all spawn failures leave every slot pending");
        for (Wave4PressurePackPolicy.Slot slot : firstRetry) completed.add(slot.key());
        check(Wave4PressurePackPolicy.missing(planned, completed).isEmpty(),
                "a later full recovery admits every slot exactly once");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static long countRole(List<Wave4PressurePackPolicy.Slot> slots, String role) {
        return slots.stream().filter(slot -> slot.role().equals(role)).count();
    }
}
