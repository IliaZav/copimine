package me.copimine.endevent.domain;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Bounded ground-path admission and distinct refuge-cell allocation before fog mutation. */
public final class BlackFogRefugeAccessPolicy {
    public static final int MAX_GRID_CELLS = 16_384;
    public static final long MAX_LEAD_MILLIS = 20_000L;
    private static final double SLOWED_BLOCKS_PER_SECOND = 2.8;
    private static final long REACTION_MILLIS = 1_200L;
    private BlackFogRefugeAccessPolicy() { }

    public record Cell(int x, int z) { }
    public record Admission(boolean admitted, int longestPathBlocks, long requiredLeadMillis) {
        public static Admission refused() { return new Admission(false, -1, 0L); }
    }

    public static Admission plan(int minX, int minZ, int maxX, int maxZ,
            List<Cell> players, Set<Cell> refuges, Predicate<Cell> walkable) {
        long widthLong = (long) maxX - minX + 1, depthLong = (long) maxZ - minZ + 1;
        if (widthLong < 1 || depthLong < 1 || widthLong > MAX_GRID_CELLS
                || depthLong > MAX_GRID_CELLS || widthLong * depthLong > MAX_GRID_CELLS
                || players == null || players.isEmpty() || players.size() > 20
                || refuges == null || refuges.size() < players.size() || refuges.size() > 180
                || refuges.stream().anyMatch(cell -> cell == null) || walkable == null) return Admission.refused();
        int width = (int) widthLong, depth = (int) depthLong;
        boolean[] terrain = new boolean[width * depth];
        for (int z = 0; z < depth; z++) for (int x = 0; x < width; x++) {
            terrain[z * width + x] = walkable.test(new Cell(minX + x, minZ + z));
        }
        List<Cell> targets = refuges.stream().sorted(Comparator.comparingInt(Cell::x)
                .thenComparingInt(Cell::z)).toList();
        int[] targetIndices = new int[targets.size()];
        for (int i = 0; i < targets.size(); i++) {
            int index = index(targets.get(i), minX, minZ, width, depth);
            if (index < 0 || !terrain[index]) return Admission.refused();
            targetIndices[i] = index;
        }
        int[][] costs = new int[players.size()][targets.size()];
        Map<Cell, int[]> routes = new HashMap<>();
        for (int p = 0; p < players.size(); p++) {
            Cell start = players.get(p);
            int startIndex = index(start, minX, minZ, width, depth);
            if (startIndex < 0 || !terrain[startIndex]) return Admission.refused();
            int[] distances = routes.computeIfAbsent(start,
                    ignored -> distances(startIndex, width, terrain));
            for (int t = 0; t < targets.size(); t++) costs[p][t] = distances[targetIndices[t]];
        }
        // Minimax matching prevents all owners being promised the same nearest cell.
        // Search only the survivable warning horizon; a maze cannot create an unbounded pause.
        int maxPath = (int) ((MAX_LEAD_MILLIS - REACTION_MILLIS)
                * SLOWED_BLOCKS_PER_SECOND / 1000.0);
        if (!canAllocate(costs, maxPath)) return Admission.refused();
        int low = 0, high = maxPath;
        while (low < high) {
            int middle = (low + high) / 2;
            if (canAllocate(costs, middle)) high = middle;
            else low = middle + 1;
        }
        long lead = REACTION_MILLIS + (long) Math.ceil(low * 1000.0 / SLOWED_BLOCKS_PER_SECOND);
        return new Admission(true, low, lead);
    }

    private static int index(Cell cell, int minX, int minZ, int width, int depth) {
        if (cell == null) return -1;
        long x = (long) cell.x - minX, z = (long) cell.z - minZ;
        return x < 0 || x >= width || z < 0 || z >= depth ? -1 : (int) z * width + (int) x;
    }

    private static int[] distances(int start, int width, boolean[] terrain) {
        int[] distance = new int[terrain.length];
        Arrays.fill(distance, -1);
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        distance[start] = 0;
        pending.add(start);
        while (!pending.isEmpty()) {
            int cell = pending.removeFirst();
            int x = cell % width;
            if (x > 0) visit(cell - 1, distance[cell] + 1, terrain, distance, pending);
            if (x + 1 < width) visit(cell + 1, distance[cell] + 1, terrain, distance, pending);
            if (cell >= width) visit(cell - width, distance[cell] + 1, terrain, distance, pending);
            if (cell + width < terrain.length) visit(cell + width, distance[cell] + 1, terrain, distance, pending);
        }
        return distance;
    }

    private static void visit(int cell, int value, boolean[] terrain, int[] distances,
            ArrayDeque<Integer> pending) {
        if (terrain[cell] && distances[cell] < 0) {
            distances[cell] = value;
            pending.addLast(cell);
        }
    }

    private static boolean canAllocate(int[][] costs, int limit) {
        int[] owners = new int[costs[0].length];
        Arrays.fill(owners, -1);
        for (int player = 0; player < costs.length; player++) {
            if (!assign(player, limit, costs, owners, new boolean[owners.length])) return false;
        }
        return true;
    }

    private static boolean assign(int player, int limit, int[][] costs, int[] owners, boolean[] seen) {
        for (int cell = 0; cell < owners.length; cell++) {
            if (seen[cell] || costs[player][cell] < 0 || costs[player][cell] > limit) continue;
            seen[cell] = true;
            if (owners[cell] < 0 || assign(owners[cell], limit, costs, owners, seen)) {
                owners[cell] = player;
                return true;
            }
        }
        return false;
    }
}
