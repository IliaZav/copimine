package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;

/** Deterministic radial anchor positions for real Wave 4 obelisks. */
public final class V3ObeliskPlacementPolicy {
    public static final int MIN_DISTANCE = 4;
    public static final int DEFAULT_RADIUS = 9;

    private V3ObeliskPlacementPolicy() {
    }

    public static List<Point> candidates(int count) {
        int safeCount = Math.max(0, Math.min(V3ObeliskScalingPolicy.MAX_OBELISKS, count));
        if (safeCount == 0) {
            return List.of();
        }
        List<Point> points = new ArrayList<>(safeCount);
        // The phase offset keeps a point away from the usual north-facing
        // player spawn while remaining perfectly reproducible after recovery.
        for (int index = 0; index < safeCount; index++) {
            double angle = -Math.PI / 2.0D + (Math.PI * 2.0D * index / safeCount);
            points.add(new Point((int) Math.round(Math.cos(angle) * DEFAULT_RADIUS),
                    (int) Math.round(Math.sin(angle) * DEFAULT_RADIUS)));
        }
        return List.copyOf(points);
    }

    public static boolean hasReasonableSpacing(List<Point> points) {
        if (points == null) {
            return false;
        }
        for (int first = 0; first < points.size(); first++) {
            Point a = points.get(first);
            if (a == null) {
                return false;
            }
            for (int second = first + 1; second < points.size(); second++) {
                Point b = points.get(second);
                if (b == null || Math.hypot(a.x() - b.x(), a.z() - b.z()) < MIN_DISTANCE) {
                    return false;
                }
            }
        }
        return true;
    }

    public record Point(int x, int z) {
    }
}
