import me.copimine.endevent.domain.GuardianRegenerationPolicy;

/**
 * Master 17.4 supersedes Canon 6.4: 0.2% per second and a 5% total cap.
 * Living shields are required; cocoon destruction disables it permanently.
 */
public final class GuardianRegenerationPolicyTest {
    public static void main(String[] args) {
        double max = 20_000.0D;
        require(Math.abs(GuardianRegenerationPolicy.cap(max) - 1_000.0D) < 0.001D,
                "the generation cap must be 5% of boss maximum health");
        require(GuardianRegenerationPolicy.CAP_FRACTION == 0.05D,
                "the master contract requires the exact five-times-reduced cap");
        require(Math.abs(GuardianRegenerationPolicy.stepBudget(max) - 40.0D) < 0.001D,
                "one step restores 0.2% of maximum health");
        require(GuardianRegenerationPolicy.STEP_INTERVAL_MILLIS >= 500L
                        && GuardianRegenerationPolicy.STEP_INTERVAL_MILLIS <= 2_000L,
                "a step is slow enough to read in combat");

        require(GuardianRegenerationPolicy.cap(Double.NaN) == 0.0D,
                "a missing maximum must offer no healing at all");
        require(GuardianRegenerationPolicy.stepBudget(0.0D) == 0.0D,
                "a zero maximum must offer no healing at all");

        // Missing health limits the step, not the budget.
        require(Math.abs(GuardianRegenerationPolicy.step(max, max - 20.0D, 0.0D) - 20.0D) < 0.001D,
                "a nearly full boss can only regain its missing health");
        require(GuardianRegenerationPolicy.step(max, max, 0.0D) == 0.0D,
                "a full-health boss never regenerates");

        // The remaining cap limits the step even when a lot of health is missing.
        double nearCap = GuardianRegenerationPolicy.cap(max) - 20.0D;
        require(Math.abs(GuardianRegenerationPolicy.step(max, 0.0D, nearCap) - 20.0D) < 0.001D,
                "the final step may not overshoot the generation cap");
        require(GuardianRegenerationPolicy.step(max, 0.0D, GuardianRegenerationPolicy.cap(max)) == 0.0D,
                "the cap is a hard stop, not a soft target");

        // Accumulation reaches the cap and then stops.
        double granted = 0.0D;
        double health = max * 0.25D;
        int steps = 0;
        while (steps < 100) {
            double healed = GuardianRegenerationPolicy.step(max, health, granted);
            if (healed <= 0.0D) {
                break;
            }
            granted += healed;
            health += healed;
            steps++;
        }
        require(Math.abs(granted - GuardianRegenerationPolicy.cap(max)) < 0.001D,
                "repeated steps must land exactly on the generation cap");
        require(steps == 25, "0.2% steps reach the 5% cap in 25 seconds of protection");
        require(health <= max + 0.001D, "regeneration must never exceed the boss maximum");

        // Rejected inputs heal nothing instead of throwing inside a combat tick.
        require(GuardianRegenerationPolicy.step(Double.NaN, 10.0D, 0.0D) == 0.0D,
                "non-finite maximum heals nothing");
        require(GuardianRegenerationPolicy.step(max, Double.NaN, 0.0D) == 0.0D,
                "non-finite health heals nothing");
        require(GuardianRegenerationPolicy.step(max, 10.0D, Double.NaN) == 0.0D,
                "non-finite granted amount heals nothing");
        require(GuardianRegenerationPolicy.step(max, -5.0D, 0.0D) == 0.0D,
                "negative health heals nothing");
        require(GuardianRegenerationPolicy.step(max, 10.0D, -1.0D) == 0.0D,
                "a negative accumulator heals nothing");

        System.out.println("GuardianRegenerationPolicyTest OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private GuardianRegenerationPolicyTest() {
    }
}