package me.copimine.artifacts;

import java.util.ArrayList;
import java.util.List;

/** Pure geometry used by projectile artifact handlers and unit-tested without Bukkit. */
public final class CombatArtifactMath {
    private static final double EPSILON = 1.0e-9;

    private CombatArtifactMath() {
    }

    public static List<Point> interpolate(Point start, Point end, double maxStep) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("start and end are required");
        }
        if (!(maxStep > 0.0) || !Double.isFinite(maxStep)) {
            throw new IllegalArgumentException("maxStep must be finite and positive");
        }
        double distance = start.distanceTo(end);
        int segments = Math.max(1, (int) Math.ceil(distance / maxStep));
        List<Point> points = new ArrayList<>(segments + 1);
        for (int index = 0; index <= segments; index++) {
            double ratio = (double) index / (double) segments;
            points.add(new Point(
                start.x + (end.x - start.x) * ratio,
                start.y + (end.y - start.y) * ratio,
                start.z + (end.z - start.z) * ratio
            ));
        }
        return List.copyOf(points);
    }

    public static Velocity awayArcVelocity(Point attacker, Point target, double horizontalSpeed, double verticalSpeed) {
        if (attacker == null || target == null) {
            throw new IllegalArgumentException("attacker and target are required");
        }
        if (!Double.isFinite(horizontalSpeed) || !Double.isFinite(verticalSpeed)) {
            throw new IllegalArgumentException("velocity components must be finite");
        }
        double x = target.x - attacker.x;
        double z = target.z - attacker.z;
        double horizontalLength = Math.hypot(x, z);
        if (horizontalLength < EPSILON) {
            x = 0.0;
            z = 1.0;
            horizontalLength = 1.0;
        }
        return new Velocity(
            x / horizontalLength * horizontalSpeed,
            verticalSpeed,
            z / horizontalLength * horizontalSpeed
        );
    }

    public record Point(double x, double y, double z) {
        public double distanceTo(Point other) {
            if (other == null) {
                return Double.POSITIVE_INFINITY;
            }
            return Math.sqrt(
                Math.pow(x - other.x, 2.0)
                    + Math.pow(y - other.y, 2.0)
                    + Math.pow(z - other.z, 2.0)
            );
        }
    }

    public record Velocity(double x, double y, double z) {
    }
}
