import me.copimine.endevent.domain.TentacleGuardianLayoutPolicy;

public final class TentacleGuardianLayoutPolicyTest {
    public static void main(String[] args) {
        var north = TentacleGuardianLayoutPolicy.permanentOffset(0, 4);
        var east = TentacleGuardianLayoutPolicy.permanentOffset(1, 4);
        var south = TentacleGuardianLayoutPolicy.permanentOffset(2, 4);
        var west = TentacleGuardianLayoutPolicy.permanentOffset(3, 4);

        requireClose(9.0D, Math.hypot(north.x(), north.z()), "north root clears the guardian");
        requireClose(9.0D, Math.hypot(east.x(), east.z()), "east root clears the guardian");
        requireClose(9.0D, Math.hypot(south.x(), south.z()), "south root clears the guardian");
        requireClose(9.0D, Math.hypot(west.x(), west.z()), "west root clears the guardian");
        require(Math.hypot(north.x() - east.x(), north.z() - east.z()) > 12.0D,
                "neighboring roots remain visibly separated");
        require(Math.hypot(south.x() - west.x(), south.z() - west.z()) > 12.0D,
                "opposite roots remain evenly spaced");
        var invalid = TentacleGuardianLayoutPolicy.permanentOffset(0, 0);
        requireClose(0.0D, Math.hypot(invalid.x(), invalid.z()),
                "an empty ring has a safe zero offset");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void requireClose(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 0.0001D) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }
}
