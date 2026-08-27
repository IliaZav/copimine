package me.copimine.endevent.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Deterministic, bounded planner for temporary hazard blocks. */
public final class HazardPlanner {
    private HazardPlanner() { }

    public static Plan plan(int minX, int maxX, int minZ, int maxZ, Set<Point> protectedPoints,
                            Pattern previous, int maxMutated, double minimumSafeRatio, long seed) {
        if (minX > maxX || minZ > maxZ || maxMutated < 0 || Double.isNaN(minimumSafeRatio)
                || Double.isInfinite(minimumSafeRatio) || minimumSafeRatio < 0.0D || minimumSafeRatio > 1.0D
                || protectedPoints == null || previous == null) throw new IllegalArgumentException("invalid hazard bounds");
        List<Point> all = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) all.add(new Point(x, z));
            if (x == Integer.MAX_VALUE) break;
        }
        int safeRequired = (int) Math.ceil(all.size() * minimumSafeRatio);
        int allowed = Math.min(maxMutated, all.size() - safeRequired);
        List<Point> candidates = new ArrayList<>();
        for (Point point : all) if (!protectedPoints.contains(point)) candidates.add(point);
        Collections.shuffle(candidates, new Random(seed));
        Set<Point> hazards = new LinkedHashSet<>();
        Set<Point> safe = new LinkedHashSet<>(all);
        for (Point candidate : candidates) {
            if (hazards.size() >= allowed) break;
            safe.remove(candidate);
            if (safe.size() >= safeRequired && connected(safe)) hazards.add(candidate);
            else safe.add(candidate);
        }
        return new Plan(hazards, safe, previous.originalBlocks());
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
            originalBlocks = Map.copyOf(originalBlocks == null ? Map.of() : new LinkedHashMap<>(originalBlocks));
        }
    }

    public record Plan(Set<Point> hazardCells, Set<Point> safeCells, Map<Point, String> originalBlocks) {
        public Plan {
            hazardCells = Set.copyOf(hazardCells == null ? Set.of() : hazardCells);
            safeCells = Set.copyOf(safeCells == null ? Set.of() : safeCells);
            originalBlocks = Map.copyOf(originalBlocks == null ? Map.of() : originalBlocks);
        }
    }
}
