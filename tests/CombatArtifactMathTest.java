import me.copimine.artifacts.CombatArtifactMath;

public final class CombatArtifactMathTest {
    public static void main(String[] args) {
        testInterpolationHasNoGaps();
        testInterpolationIncludesEndpoints();
        testArcVelocityPointsAwayAndUp();
        testZeroHorizontalDistanceStillLaunchesUpward();
        System.out.println("CombatArtifactMathTest OK");
    }

    private static void testInterpolationHasNoGaps() {
        var points = CombatArtifactMath.interpolate(
            new CombatArtifactMath.Point(0.0, 64.0, 0.0),
            new CombatArtifactMath.Point(7.25, 64.0, 3.5),
            1.0
        );
        check(points.size() > 2, "long path must have intermediate points");
        for (int index = 1; index < points.size(); index++) {
            check(points.get(index - 1).distanceTo(points.get(index)) <= 1.000001,
                "interpolation contains a gap larger than one block");
        }
    }

    private static void testInterpolationIncludesEndpoints() {
        var start = new CombatArtifactMath.Point(1.0, 2.0, 3.0);
        var end = new CombatArtifactMath.Point(1.2, 2.1, 3.3);
        var points = CombatArtifactMath.interpolate(start, end, 1.0);
        check(points.get(0).equals(start), "interpolation must preserve start");
        check(points.get(points.size() - 1).equals(end), "interpolation must preserve end");
    }

    private static void testArcVelocityPointsAwayAndUp() {
        var velocity = CombatArtifactMath.awayArcVelocity(
            new CombatArtifactMath.Point(0.0, 64.0, 0.0),
            new CombatArtifactMath.Point(0.0, 64.0, 2.0),
            1.65,
            1.25
        );
        check(velocity.z() > 0.0, "target must be launched away from attacker");
        check(velocity.y() > 0.0, "target must be launched upward");
        check(Math.abs(velocity.x()) < 0.000001, "sideways velocity must not be invented");
    }

    private static void testZeroHorizontalDistanceStillLaunchesUpward() {
        var velocity = CombatArtifactMath.awayArcVelocity(
            new CombatArtifactMath.Point(5.0, 64.0, 5.0),
            new CombatArtifactMath.Point(5.0, 64.0, 5.0),
            1.65,
            1.25
        );
        check(velocity.y() > 0.0, "coincident attacker/target must still launch upward");
        check(Double.isFinite(velocity.x()) && Double.isFinite(velocity.z()),
            "zero horizontal distance must not create NaN velocity");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
