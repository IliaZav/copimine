package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Deterministic radial anchor positions for real Wave 4 obelisks. */
public final class ObeliskPlacementPolicy {
    public static final int MIN_DISTANCE = 4;
    public static final int DEFAULT_RADIUS = 9;

    private ObeliskPlacementPolicy() {
    }

    public static List<Point> candidates(int count) {
        int safeCount = Math.max(0, Math.min(ObeliskScalingPolicy.MAX_OBELISKS, count));
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

    /**
     * Deterministic bounded fallback candidates. The first ring is kept
     * identical to the original layout so existing maps remain stable; the
     * additional rings let the Bukkit adapter skip a blocked site without
     * silently creating fewer obelisks.
     */
    public static List<Point> searchCandidates(int count) {
        int safeCount = Math.max(0, Math.min(ObeliskScalingPolicy.MAX_OBELISKS, count));
        if (safeCount == 0) {
            return List.of();
        }
        Set<Point> points = new LinkedHashSet<>(candidates(safeCount));
        int[] radii = {8, 10, 11, 12};
        for (int radius : radii) {
            for (int slot = 0; slot < 16; slot++) {
                double angle = -Math.PI / 2.0D + slot * Math.PI * 2.0D / 16.0D;
                points.add(new Point((int) Math.round(Math.cos(angle) * radius),
                        (int) Math.round(Math.sin(angle) * radius)));
            }
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
