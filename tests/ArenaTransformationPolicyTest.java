import me.copimine.endevent.domain.ArenaTransformationPolicy;
import me.copimine.endevent.domain.ArenaTransformationPolicy.Advance;
import me.copimine.endevent.domain.ArenaTransformationPolicy.Mask;
import me.copimine.endevent.domain.ArenaTransformationPolicy.Shape;
import me.copimine.endevent.domain.ArenaTransformationPolicy.Step;

public final class ArenaTransformationPolicyTest {
    public static void main(String[] args) {
        testMaskGeometryIsExact();
        testMasksFailClosed();
        testPlanRequiresTheHallInsideTheSnapshot();
        testStepLadderIsMonotoneAndNeverSkips();
        testRevealDeltaAndReversibility();
        testVoidRescue();
        System.out.println("ArenaTransformationPolicyTest OK");
    }

    private static void testMaskGeometryIsExact() {
        Mask square = new Mask(Shape.SQUARE, 0, 0, 2, 10, 12);
        check(square.cellCount() == 25, "a half-extent 2 square is 5x5 cells");
        check(square.blockCount() == 75, "three vertical layers multiply the cells");
        check(square.contains(2, 2) && square.contains(-2, -2), "the square is inclusive");
        check(!square.contains(3, 0), "a cell one past the edge is outside");

        Mask circle = new Mask(Shape.CIRCLE, 0, 0, 2, 10, 12);
        check(circle.cellCount() == 13, "a radius 2 circle is 13 cells in the closed form");
        check(circle.contains(0, 2) && circle.contains(2, 0), "the axis cells lie on the rim");
        check(!circle.contains(2, 2), "the diagonal corner is outside the circle");
    }

    private static void testMasksFailClosed() {
        checkThrows(() -> new Mask(Shape.SQUARE, 0, 0,
                        ArenaTransformationPolicy.MAX_HALF_EXTENT + 1, 0, 1),
                "an over-limit half-extent must fail closed");
        checkThrows(() -> new Mask(Shape.SQUARE, 0, 0, -1, 0, 1),
                "a negative half-extent must fail closed");
        checkThrows(() -> new Mask(Shape.SQUARE, 0, 0, 2, 10, 9),
                "maxY below minY must fail closed");
    }

    private static void testPlanRequiresTheHallInsideTheSnapshot() {
        Mask square = new Mask(Shape.SQUARE, 0, 0, 4, 10, 12);
        Mask inside = new Mask(Shape.CIRCLE, 0, 0, 3, 10, 12);
        ArenaTransformationPolicy plan = ArenaTransformationPolicy.plan(square, inside);
        check(plan.step() == Step.SNAPSHOT, "a fresh plan starts at the snapshot step");

        Mask outside = new Mask(Shape.CIRCLE, 3, 0, 3, 10, 12);
        checkThrows(() -> ArenaTransformationPolicy.plan(square, outside),
                "a hall reaching outside the snapshot must be refused");
        checkThrows(() -> ArenaTransformationPolicy.plan(
                        new Mask(Shape.CIRCLE, 0, 0, 3, 10, 12), inside),
                "the snapshot footprint must be a square");
    }

    private static void testStepLadderIsMonotoneAndNeverSkips() {
        ArenaTransformationPolicy plan = ArenaTransformationPolicy.plan(
                new Mask(Shape.SQUARE, 0, 0, 4, 10, 12),
                new Mask(Shape.CIRCLE, 0, 0, 2, 10, 12));
        check(plan.advance(Step.PHASE_SIX_OPENING) == Advance.OUT_OF_ORDER,
                "skipping the reveal must be refused");
        check(plan.advance(Step.CIRCULAR_REVEAL) == Advance.ADVANCED, "the reveal advances");
        check(plan.advance(Step.CIRCULAR_REVEAL) == Advance.ALREADY_SETTLED,
                "replaying a step is idempotent");
        check(plan.advance(Step.SNAPSHOT) == Advance.OUT_OF_ORDER,
                "the ladder never moves backwards, even for the rebuild");
        check(plan.advance(Step.PHASE_SIX_OPENING) == Advance.ADVANCED, "the ladder keeps moving up");
        check(plan.advance(Step.PORTALS) == Advance.ADVANCED, "portals follow the opening");
        check(plan.advance(Step.REBUILD) == Advance.ADVANCED, "the reverse rebuild is the last step");
    }

    private static void testRevealDeltaAndReversibility() {
        ArenaTransformationPolicy plan = ArenaTransformationPolicy.plan(
                new Mask(Shape.SQUARE, 0, 0, 4, 10, 12),
                new Mask(Shape.CIRCLE, 0, 0, 2, 10, 12));
        check(plan.revealCellDelta() == 81 - 13,
                "the reveal changes exactly the square cells outside the circle");
        check(plan.revealIsReversible(),
                "every cell the reveal touches lies inside the snapshot");
    }

    private static void testVoidRescue() {
        check(ArenaTransformationPolicy.voidRescueY(-5.0, 8, 12) == 12.0,
                "a player below the void floor is returned to the safe landing");
        check(ArenaTransformationPolicy.voidRescueY(20.0, 8, 12) == 20.0,
                "a player above the floor is not moved");
        check(ArenaTransformationPolicy.voidRescueY(Double.NaN, 8, 12) == 12.0,
                "a non-finite Y is rescued, never left dangling");
        checkThrows(() -> ArenaTransformationPolicy.voidRescueY(0.0, 8, 8),
                "a landing at or below the void floor must fail closed");
    }

    private interface ThrowingRunnable {
        void run();
    }

    private static void checkThrows(ThrowingRunnable action, String message) {
        try {
            action.run();
        } catch (RuntimeException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
