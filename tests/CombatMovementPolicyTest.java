import me.copimine.endevent.domain.CombatMovementPolicy;

public final class CombatMovementPolicyTest {
    public static void main(String[] args) {
        testStepIsHorizontalAndCapped();
        testStepStopsAtTarget();
        testInvalidInputsProduceNoMovement();
        testArenaBoundsRejectUnsafePositions();
        System.out.println("CombatMovementPolicyTest OK");
    }

    private static void testStepIsHorizontalAndCapped() {
        CombatMovementPolicy.Step step = CombatMovementPolicy.stepTowards(
                10.0D, 68.0D, -39.0D, 0.0D, 99.0D, -39.0D, 2.0D);
        check(step.horizontalLength() <= CombatMovementPolicy.MAX_COMBAT_STEP_BLOCKS,
                "fallback step must be capped");
        check(Math.abs(step.y()) < 0.0001D, "fallback step must never steer vertically");
        check(step.x() < 0.0D && Math.abs(step.z()) < 0.0001D,
                "fallback step must point toward the target horizontally");
    }

    private static void testStepStopsAtTarget() {
        CombatMovementPolicy.Step step = CombatMovementPolicy.stepTowards(
                1.0D, 68.0D, 1.0D, 1.05D, 70.0D, 1.0D, 1.0D);
        check(Math.abs(step.x() - 0.05D) < 0.0001D,
                "fallback step must not overshoot a nearby target");
    }

    private static void testInvalidInputsProduceNoMovement() {
        check(CombatMovementPolicy.stepTowards(Double.NaN, 0.0D, 0.0D,
                1.0D, 0.0D, 1.0D, 1.0D).equals(CombatMovementPolicy.Step.ZERO),
                "non-finite input must fail closed");
        check(CombatMovementPolicy.stepTowards(0.0D, 0.0D, 0.0D,
                1.0D, 0.0D, 1.0D, 0.0D).equals(CombatMovementPolicy.Step.ZERO),
                "non-positive speed must fail closed");
    }

    private static void testArenaBoundsRejectUnsafePositions() {
        check(CombatMovementPolicy.withinBounds(0.5D, 68.0D, 0.5D,
                4.5D, 68.0D, 0.5D, 20.0D, 3.0D, 3.5D),
                "a safe floor position must be accepted");
        check(!CombatMovementPolicy.withinBounds(0.5D, 68.0D, 0.5D,
                0.5D, 68.0D, 0.5D, 20.0D, 3.0D, 3.5D),
                "Core position must be rejected");
        check(!CombatMovementPolicy.withinBounds(0.5D, 68.0D, 0.5D,
                4.5D, 72.0D, 0.5D, 20.0D, 3.0D, 3.5D),
                "vertical escape must be rejected");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
