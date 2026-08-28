import java.util.HashSet;
import java.util.Set;
import me.copimine.endevent.domain.HazardPlanner;
import me.copimine.endevent.domain.StormPatternPolicy;

public final class StormPatternPolicyTest {
    public static void main(String[] args) {
        StormPatternPolicy.Pattern previous = null;
        Set<StormPatternPolicy.Pattern> seen = new HashSet<>();
        for (int index = 0; index < 12; index++) {
            StormPatternPolicy.Pattern next = StormPatternPolicy.nextPattern(previous, index + 1L);
            check(next != previous, "storm pattern must not repeat consecutively");
            seen.add(next);
            previous = next;
        }
        check(seen.size() == StormPatternPolicy.Pattern.values().length,
                "pattern rotation must exercise every storm shape");

        StormPatternPolicy.Bounds bounds = new StormPatternPolicy.Bounds(-20, 20, -20, 20);
        for (StormPatternPolicy.Pattern pattern : StormPatternPolicy.Pattern.values()) {
            Set<HazardPlanner.Point> cells = StormPatternPolicy.patternCells(bounds, pattern, 3, 60L);
            check(!cells.isEmpty(), pattern + " must produce bounded hazard candidates");
            check(cells.stream().allMatch(point -> bounds.contains(point)),
                    pattern + " candidates must stay within the arena bounds");
            check(!cells.contains(new HazardPlanner.Point(0, 0)),
                    pattern + " must leave the core cell protected");
        }
        check(StormPatternPolicy.minimumSafeRatio() >= 0.35D,
                "storm must retain the configured safe floor ratio");
        System.out.println("StormPatternPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
