import me.copimine.endevent.domain.WaveObjectivePolicy;

public final class WaveObjectivePolicyTest {
    public static void main(String[] args) {
        WaveObjectivePolicy.Objective[] expected = {
                WaveObjectivePolicy.Objective.AWAKENING, WaveObjectivePolicy.Objective.HUNT,
                WaveObjectivePolicy.Objective.PORTALS, WaveObjectivePolicy.Objective.TOWER_DEFENSE,
                WaveObjectivePolicy.Objective.RIFT_STORM};
        int[] expectedCaps = {16, 20, 24, 28, 32};
        for (int wave = 1; wave <= 5; wave++) {
            WaveObjectivePolicy.Objective objective = WaveObjectivePolicy.objective(wave);
            check(objective == expected[wave - 1], "wave " + wave + " objective mismatch");
            check(!WaveObjectivePolicy.title(wave).isBlank(), "objective title must be exposed");
            check(WaveObjectivePolicy.mobCap(wave) == expectedCaps[wave - 1], "objective mob cap mismatch");
        }
        check(WaveObjectivePolicy.isComplete(1, new WaveObjectivePolicy.Progress(10, 10, 0, 0, false, false)), "awakening predicate");
        check(WaveObjectivePolicy.isComplete(2, new WaveObjectivePolicy.Progress(10, 10, 0, 0, false, false)), "hunt predicate");
        check(WaveObjectivePolicy.isComplete(3, new WaveObjectivePolicy.Progress(0, 0, 3, 3, false, false)), "portal predicate");
        check(WaveObjectivePolicy.isComplete(4, new WaveObjectivePolicy.Progress(0, 0, 0, 0, true, false)), "tower predicate");
        check(WaveObjectivePolicy.isComplete(5, new WaveObjectivePolicy.Progress(0, 0, 0, 0, false, true)), "storm predicate");
        check(!WaveObjectivePolicy.isComplete(3, new WaveObjectivePolicy.Progress(0, 0, 2, 3, true, true)), "incomplete predicate");
        boolean rejected = false;
        try { WaveObjectivePolicy.objective(6); } catch (IllegalArgumentException expectedException) { rejected = true; }
        check(rejected, "unknown waves must be rejected");
        System.out.println("WaveObjectivePolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
