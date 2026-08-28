import me.copimine.endevent.domain.WaveObjectivePolicy;

public final class WaveObjectivePolicyTest {
    public static void main(String[] args) {
        WaveObjectivePolicy.Objective[] expected = {
                WaveObjectivePolicy.Objective.AWAKENING, WaveObjectivePolicy.Objective.HUNT,
                WaveObjectivePolicy.Objective.PORTALS, WaveObjectivePolicy.Objective.TOWER_DEFENSE,
                WaveObjectivePolicy.Objective.RIFT_STORM};
        int[] configuredBaseTotals = {5 + 8, 7 + 6 + 3, 10 + 3 + 4, 8 + 6 + 4 + 2, 12 + 8 + 4 + 6};
        for (int wave = 1; wave <= 5; wave++) {
            WaveObjectivePolicy.Objective objective = WaveObjectivePolicy.objective(wave);
            check(objective == expected[wave - 1], "wave " + wave + " objective mismatch");
            check(!WaveObjectivePolicy.title(wave).isBlank(), "objective title must be exposed");
            int expectedCap = Math.min(48, configuredBaseTotals[wave - 1] * 2);
            check(WaveObjectivePolicy.mobCap(wave) == expectedCap, "objective mob cap must match config composition at max scale and hard cap");
            check(WaveObjectivePolicy.mobCap(wave) <= 48, "objective mob cap must not exceed the config hard cap");
        }
        check(WaveObjectivePolicy.title(1).equals("Пробуждение"), "wave 1 title must be Russian");
        check(WaveObjectivePolicy.title(2).equals("Охота"), "wave 2 title must be Russian");
        check(WaveObjectivePolicy.title(3).equals("Порталы"), "wave 3 title must be Russian");
        check(WaveObjectivePolicy.title(4).equals("Оборона ядра"), "wave 4 title must be Russian");
        check(WaveObjectivePolicy.title(5).equals("Шторм Разлома"), "wave 5 title must be Russian");
        check(WaveObjectivePolicy.isComplete(1, new WaveObjectivePolicy.Progress(10, 10, 0, 0, false, false)), "awakening predicate");
        check(WaveObjectivePolicy.isComplete(2, new WaveObjectivePolicy.Progress(10, 10, 0, 0, false, false)), "hunt predicate");
        check(WaveObjectivePolicy.isComplete(3, new WaveObjectivePolicy.Progress(0, 0, 3, 3, false, false)), "portal predicate");
        check(WaveObjectivePolicy.isComplete(4, new WaveObjectivePolicy.Progress(0, 0, 0, 0, true, false)), "tower predicate");
        check(WaveObjectivePolicy.isComplete(5, new WaveObjectivePolicy.Progress(0, 0, 0, 0, false, true)), "storm predicate");
        check(!WaveObjectivePolicy.isComplete(3, new WaveObjectivePolicy.Progress(0, 0, 2, 3, true, true)), "incomplete predicate");
        check(!WaveObjectivePolicy.isComplete(1, new WaveObjectivePolicy.Progress(9, 10, 0, 0, false, false)), "awakening must remain incomplete below target");
        check(!WaveObjectivePolicy.isComplete(2, new WaveObjectivePolicy.Progress(9, 10, 0, 0, false, false)), "hunt must remain incomplete below target");
        check(!WaveObjectivePolicy.isComplete(1, new WaveObjectivePolicy.Progress(0, 0, 0, 0, false, false)), "zero required mobs must fail closed");
        boolean rejected = false;
        try { WaveObjectivePolicy.objective(6); } catch (IllegalArgumentException expectedException) { rejected = true; }
        check(rejected, "unknown waves must be rejected");
        System.out.println("WaveObjectivePolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
