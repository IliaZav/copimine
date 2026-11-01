package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Irregular, reproducible placement for the real Wave 4 obelisks.
 *
 * <p>W4-001 [U] is explicit: obelisks must move farther from the Core and stand
 * irregularly, because a ring makes players "stand in one central cluster or on
 * a perfectly predictable circle". This policy therefore scatters anchors inside
 * a radial band instead of stepping around a single radius.</p>
 *
 * <p>W4-003 [H] still requires a persisted layout that survives recovery, so the
 * scatter is driven by a seeded {@link Random}: the seed is part of the attempt
 * and the resulting points are what the caller stores. Recomputing with the same
 * seed yields byte-identical anchors, so a restart cannot reroll the arena
 * (SAVE-003), while a new attempt may legitimately draw a new layout.</p>
 */
public final class ObeliskPlacementPolicy {
    public static final int MIN_DISTANCE = 4;
    /** Legacy fixed radius, kept as the default when no usable band is known. */
    public static final int DEFAULT_RADIUS = 9;
    /** W4-002 [A]: outer band as a share of the usable centre-to-boundary distance. */
    public static final double MIN_RADIUS_FRACTION = 0.65D;
    public static final double MAX_RADIUS_FRACTION = 0.85D;
    /** Radial jitter inside the band, so no two obelisks share a ring. */
    private static final double JITTER_DEGREES = 26.0D;
    private static final int PLACEMENT_ATTEMPTS = 48;

    private ObeliskPlacementPolicy() {
    }

    /**
     * Scatters {@code count} anchors inside the validated radial band.
     *
     * @param count   requested obelisk count, clamped to the established maximum
     * @param seed    attempt-scoped seed; the same seed reproduces the layout
     * @param usableRadius real polygon/floor-resolved centre-to-boundary distance
     * @return anchor points in the order the obelisks were planned
     */
    public static List<Point> scatter(int count, long seed, double usableRadius) {
        int safeCount = Math.max(0, Math.min(ObeliskScalingPolicy.MAX_OBELISKS, count));
        if (safeCount == 0) return List.of();
        double minRadius = usableRadius * MIN_RADIUS_FRACTION;
        double maxRadius = usableRadius * MAX_RADIUS_FRACTION;
        if (!Double.isFinite(minRadius) || !Double.isFinite(maxRadius) || minRadius < MIN_DISTANCE) {
            // Fall back to the legacy ring when the arena shape is unknown; a
            // caller that cannot measure the arena must not get a broken layout.
            return candidates(safeCount);
        }
        Random random = new Random(seed);
        List<Point> points = new ArrayList<>(safeCount);
        int attempts = 0;
        // Rejection sampling keeps the spacing contract: no two anchors closer
        // than MIN_DISTANCE, so every obelisk keeps its own approach path.
        while (points.size() < safeCount && attempts < PLACEMENT_ATTEMPTS * safeCount) {
            attempts++;
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = minRadius + random.nextDouble() * (maxRadius - minRadius);
            int x = (int) Math.round(Math.cos(angle) * radius);
            int z = (int) Math.round(Math.sin(angle) * radius);
            Point candidate = new Point(x, z);
            if (points.contains(candidate)) continue;
            boolean tooClose = false;
            for (Point other : points) {
                if (Math.hypot(candidate.x() - other.x(), candidate.z() - other.z()) < MIN_DISTANCE) {
                    tooClose = true;
                    break;
                }
            }
            if (!tooClose) points.add(candidate);
        }
        // If rejection sampling could not seat every obelisk (tiny arena), the
        // deterministic ring fills the remainder so the wave never silently
        // spawns fewer obelisks than the roster requires.
        if (points.size() < safeCount) {
            for (Point fallback : candidates(safeCount)) {
                if (points.size() >= safeCount) break;
                boolean tooClose = points.stream().anyMatch(other ->
                        Math.hypot(fallback.x() - other.x(), fallback.z() - other.z()) < MIN_DISTANCE);
                if (!tooClose) points.add(fallback);
            }
        }
        return List.copyOf(points);
    }

    /** Fixed-seed scatter used when the caller cannot measure the arena. */
    public static List<Point> scatter(int count, long seed) {
        return scatter(count, seed, DEFAULT_RADIUS / MIN_RADIUS_FRACTION);
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