package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PadLayout {
    private PadLayout() {
    }

    public static Result compute(int count, double baseAngleDegrees, List<Double> candidateRadii) {
        if (count < 1 || candidateRadii == null || candidateRadii.isEmpty()) {
            return Result.invalid("INVALID_ARGUMENTS");
        }
        for (double radius : candidateRadii) {
            if (!Double.isFinite(radius) || radius <= 0.0D) {
                continue;
            }
            List<Point> points = new ArrayList<>(count);
            Set<String> keys = new HashSet<>();
            boolean unique = true;
            for (int index = 0; index < count; index++) {
                double angle = Math.toRadians(baseAngleDegrees + (360.0D * index / count));
                int blockX = (int) Math.round(Math.cos(angle) * radius);
                int blockZ = (int) Math.round(Math.sin(angle) * radius);
                String key = blockX + ":" + blockZ;
                if (!keys.add(key)) {
                    unique = false;
                    break;
                }
                points.add(new Point(blockX, blockZ, radius, angle));
            }
            if (unique && points.size() == count) {
                return new Result(true, List.copyOf(points), "OK");
            }
        }
        return Result.invalid("NO_UNIQUE_RADIUS");
    }

    public record Point(int blockX, int blockZ, double radius, double angleRadians) {
    }

    public record Result(boolean valid, List<Point> points, String reason) {
        private static Result invalid(String reason) {
            return new Result(false, List.of(), reason);
        }
    }
}
