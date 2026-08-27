import me.copimine.endevent.domain.HazardPlanner;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class HazardPlannerTest {
    public static void main(String[] args) {
        Map<HazardPlanner.Point, String> snapshot = new HashMap<>();
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) snapshot.put(new HazardPlanner.Point(x, z), "STONE_" + x + "_" + z);
        HazardPlanner.Point protectedPoint = new HazardPlanner.Point(2, 2);
        HazardPlanner.Pattern previous = new HazardPlanner.Pattern(snapshot);
        HazardPlanner.Plan plan = HazardPlanner.plan(0, 4, 0, 4, Set.of(protectedPoint), previous, 8, 0.80D, 77L);
        check(plan.hazardCells().size() <= 8, "hazards must respect the cap");
        check(!plan.hazardCells().contains(protectedPoint), "protected cells must never be mutated");
        check(plan.safeCells().size() >= 20, "safe ratio must be met");
        check(plan.originalBlocks().keySet().equals(snapshot.keySet()), "snapshot keys must be preserved exactly");
        check(plan.hazardCells().stream().allMatch(p -> p.x() >= 0 && p.x() <= 4 && p.z() >= 0 && p.z() <= 4),
                "hazards must stay inside inclusive bounds");
        check(isConnected(plan.safeCells()), "safe cells must be connected");
        check(plan.equals(HazardPlanner.plan(0, 4, 0, 4, Set.of(protectedPoint), previous, 8, 0.80D, 77L)),
                "same seed and inputs must produce the same plan");
        check(HazardPlanner.plan(0, 1, 0, 1, Set.of(), previous, 0, 1.0D, 2L).hazardCells().isEmpty(),
                "zero mutation cap must produce no hazards");
        boolean rejected = false;
        try { HazardPlanner.plan(2, 1, 0, 1, Set.of(), previous, 1, 0.5D, 2L); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "invalid bounds must fail closed");
        System.out.println("HazardPlannerTest OK");
    }

    private static boolean isConnected(Set<HazardPlanner.Point> cells) {
        if (cells.isEmpty()) return false;
        Set<HazardPlanner.Point> seen = new HashSet<>();
        java.util.ArrayDeque<HazardPlanner.Point> todo = new java.util.ArrayDeque<>();
        todo.add(cells.iterator().next());
        while (!todo.isEmpty()) {
            HazardPlanner.Point p = todo.remove();
            if (!seen.add(p)) continue;
            for (HazardPlanner.Point n : new HazardPlanner.Point[] {
                    new HazardPlanner.Point(p.x() + 1, p.z()), new HazardPlanner.Point(p.x() - 1, p.z()),
                    new HazardPlanner.Point(p.x(), p.z() + 1), new HazardPlanner.Point(p.x(), p.z() - 1)})
                if (cells.contains(n)) todo.add(n);
        }
        return seen.size() == cells.size();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
