package me.copimine.endevent.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Deterministic, bounded planner for temporary hazard blocks. */
public final class HazardPlanner {
    private static final long MAX_PLANNABLE_CELLS = 1_000_000L;

    private HazardPlanner() { }

    public static Plan plan(int minX, int maxX, int minZ, int maxZ, Set<Point> protectedPoints,
                            Pattern previous, int maxMutated, double minimumSafeRatio, long seed) {
        if (minX > maxX || minZ > maxZ || maxMutated < 0 || Double.isNaN(minimumSafeRatio)
                || Double.isInfinite(minimumSafeRatio) || minimumSafeRatio < 0.0D || minimumSafeRatio > 1.0D
                || protectedPoints == null || protectedPoints.stream().anyMatch(point -> point == null)
                || previous == null) throw new IllegalArgumentException("invalid hazard bounds");
        long width = (long) maxX - minX + 1L;
        long height = (long) maxZ - minZ + 1L;
        if (width <= 0L || height <= 0L || width > MAX_PLANNABLE_CELLS / height) {
            throw new IllegalArgumentException("hazard rectangle is too large");
        }
        List<Point> all = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                all.add(new Point(x, z));
                if (z == maxZ) break;
            }
            if (x == Integer.MAX_VALUE) break;
        }
        int safeRequired = (int) Math.ceil(all.size() * minimumSafeRatio);
        int allowed = Math.min(maxMutated, all.size() - safeRequired);
        Map<Point, String> validSnapshot = new LinkedHashMap<>();
        for (Map.Entry<Point, String> entry : previous.originalBlocks().entrySet()) {
            if (inside(entry.getKey(), minX, maxX, minZ, maxZ)) validSnapshot.put(entry.getKey(), entry.getValue());
        }
        List<Point> candidates = new ArrayList<>();
        for (Point point : validSnapshot.keySet()) if (!protectedPoints.contains(point)) candidates.add(point);
        candidates.sort(Comparator.comparingInt(Point::x).thenComparingInt(Point::z));
        Collections.shuffle(candidates, new Random(seed));
        Set<Point> hazards = new LinkedHashSet<>();
        Set<Point> safe = new LinkedHashSet<>(all);
        for (Point candidate : candidates) {
            if (hazards.size() >= allowed) break;
            safe.remove(candidate);
            if (safe.size() >= safeRequired && connected(safe)) hazards.add(candidate);
            else safe.add(candidate);
        }
        Map<Point, String> plannedOriginalBlocks = new LinkedHashMap<>();
        for (Point hazard : hazards) plannedOriginalBlocks.put(hazard, validSnapshot.get(hazard));
        return new Plan(hazards, safe, plannedOriginalBlocks);
    }

    private static boolean inside(Point point, int minX, int maxX, int minZ, int maxZ) {
        return point != null && point.x() >= minX && point.x() <= maxX
                && point.z() >= minZ && point.z() <= maxZ;
    }

    private static boolean connected(Set<Point> cells) {
        if (cells.isEmpty()) return false;
        Set<Point> seen = new HashSet<>();
        ArrayDeque<Point> queue = new ArrayDeque<>();
        queue.add(cells.iterator().next());
        while (!queue.isEmpty()) {
            Point point = queue.remove();
            if (!seen.add(point)) continue;
            for (Point neighbor : point.neighbors()) if (cells.contains(neighbor)) queue.add(neighbor);
        }
        return seen.size() == cells.size();
    }

    public record Point(int x, int z) {
        private List<Point> neighbors() {
            return List.of(new Point(x + 1, z), new Point(x - 1, z), new Point(x, z + 1), new Point(x, z - 1));
        }
    }

    public record Cell(Point point, String originalBlock, boolean hazard) {
        public Cell {
            if (point == null || originalBlock == null || originalBlock.isBlank()) throw new IllegalArgumentException("invalid cell");
        }
    }

    public record Pattern(Map<Point, String> originalBlocks) {
        public Pattern {
            if (originalBlocks == null) throw new IllegalArgumentException("snapshot is required");
            Map<Point, String> copy = new LinkedHashMap<>();
            for (Map.Entry<Point, String> entry : originalBlocks.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isBlank()) {
                    throw new IllegalArgumentException("snapshot entries must be complete");
                }
                copy.put(entry.getKey(), entry.getValue());
            }
            originalBlocks = immutableMap(copy);
        }
    }

    public record Plan(Set<Point> hazardCells, Set<Point> safeCells, Map<Point, String> originalBlocks) {
        public Plan {
            Set<Point> hazardCopy = immutableSet(hazardCells == null ? Set.of() : hazardCells);
            Set<Point> safeCopy = immutableSet(safeCells == null ? Set.of() : safeCells);
            Map<Point, String> originalCopy = immutableMap(originalBlocks == null ? Map.of() : originalBlocks);
            if (!originalCopy.keySet().equals(hazardCopy) || !Collections.disjoint(hazardCopy, safeCopy)) {
                throw new IllegalArgumentException("plan snapshots must match disjoint hazards exactly");
            }
            hazardCells = hazardCopy;
            safeCells = safeCopy;
            originalBlocks = originalCopy;
        }
    }

    private static <T> Set<T> immutableSet(Set<T> values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }

    private static <K, V> Map<K, V> immutableMap(Map<K, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
