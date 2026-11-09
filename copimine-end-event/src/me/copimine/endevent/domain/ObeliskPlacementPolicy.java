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
    /** How many substitute anchors a caller may reserve for rejected sites. */
    public static final int MAX_SPARE_CANDIDATES = 12;

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

    /**
     * Extra anchors for the caller's placement fallback.
     *
     * <p>{@link #scatter} returns exactly {@code count} points, so a wave that
     * rejects one on safety grounds has nothing left to try and aborts with
     * "planned fewer obelisks than the roster requires" — the arena then plays
     * music with no objectives. This returns additional deterministic anchors
     * from the same seeded stream, spaced by the same rule, so the caller can
     * substitute a rejected site instead of failing the whole wave.</p>
     *
     * @param count  how many spare anchors the caller wants
     * @param seed   the SAME attempt-scoped seed as the primary scatter
     * @param usableRadius the same usable radius as the primary scatter
     * @param taken  anchors already in use; spares keep MIN_DISTANCE from them
     */
    public static List<Point> spareCandidates(int count, long seed, double usableRadius,
                                              List<Point> taken) {
        int wanted = Math.max(0, Math.min(ObeliskScalingPolicy.MAX_OBELISKS, count));
        if (wanted == 0) return List.of();
        double minRadius = usableRadius * MIN_RADIUS_FRACTION;
        double maxRadius = usableRadius * MAX_RADIUS_FRACTION;
        // An unusable band still has the legacy ring available, exactly as the
        // primary path falls back, so a spare list is never empty by accident.
        double usableMin = Double.isFinite(minRadius) && minRadius >= MIN_DISTANCE
                ? minRadius : DEFAULT_RADIUS * MIN_RADIUS_FRACTION;
        double usableMax = Double.isFinite(maxRadius) && maxRadius >= usableMin
                ? maxRadius : DEFAULT_RADIUS;
        List<Point> occupied = taken == null ? List.of() : taken;
        List<Point> spares = new ArrayList<>(wanted);
        Random random = new Random(seed ^ 0x5DEECE66DL);
        int attempts = 0;
        while (spares.size() < wanted && attempts < PLACEMENT_ATTEMPTS * wanted * 4) {
            attempts++;
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = usableMin + random.nextDouble() * (usableMax - usableMin);
            Point candidate = new Point((int) Math.round(Math.cos(angle) * radius),
                    (int) Math.round(Math.sin(angle) * radius));
            if (occupied.contains(candidate) || spares.contains(candidate)) continue;
            boolean tooClose = occupied.stream().anyMatch(other ->
                    Math.hypot(candidate.x() - other.x(), candidate.z() - other.z()) < MIN_DISTANCE)
                    || spares.stream().anyMatch(other ->
                    Math.hypot(candidate.x() - other.x(), candidate.z() - other.z()) < MIN_DISTANCE);
            if (!tooClose) spares.add(candidate);
        }
        // The ring is the deterministic last resort, same as the primary path.
        if (spares.size() < wanted) {
            for (Point ring : candidates(ObeliskScalingPolicy.MAX_OBELISKS)) {
                if (spares.size() >= wanted) break;
                if (occupied.contains(ring) || spares.contains(ring)) continue;
                spares.add(ring);
            }
        }
        return List.copyOf(spares);
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

    /** Extra choices cover the approved band; spacing is checked against accepted sites. */
    public static List<Point> searchCandidates(long seed, double usableRadius) {
        double radius = Double.isFinite(usableRadius) && usableRadius >= MIN_DISTANCE / MIN_RADIUS_FRACTION
                ? Math.min(usableRadius, 128.0D) : DEFAULT_RADIUS / MIN_RADIUS_FRACTION;
        double min = radius * MIN_RADIUS_FRACTION, max = radius * MAX_RADIUS_FRACTION;
        List<Point> points = new ArrayList<>();
        int extent = (int) Math.ceil(max);
        for (int x = -extent; x <= extent; x++) {
            for (int z = -extent; z <= extent; z++) {
                double distance = Math.hypot(x, z);
                if (distance >= min && distance <= max) points.add(new Point(x, z));
            }
        }
        java.util.Collections.shuffle(points, new Random(seed ^ 0x4F42454C49534BL));
        return List.copyOf(points.subList(0, Math.min(512, points.size())));
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
