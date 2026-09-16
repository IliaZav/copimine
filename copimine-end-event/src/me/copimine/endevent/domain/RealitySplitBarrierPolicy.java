package me.copimine.endevent.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic radial wall geometry for Wave 7.  These are relative cells;
 * the Bukkit adapter supplies the Core position and journals the original
 * blocks before placing the temporary collision barrier.
 */
public final class RealitySplitBarrierPolicy {
    /** Five solid levels stop a player from jumping over a room separator. */
    public static final int HEIGHT = 5;
    /** Start beside the Core so no walkable central gap connects rooms. */
    public static final double MIN_RADIUS = 0.5D;
    /**
     * A radial line reaches a square arena edge after travelling farther than
     * its nominal twenty-block radius.  The adapter clips the resulting cells
     * to the configured arena bounds, so this closes the diagonal corner
     * bypass without mutating blocks outside the event.
     */
    public static final double MAX_RADIUS = 32.0D;
    public static final int MAX_CELLS = 1536;
    /** The gameplay collision wall is exactly one block wide. */
    public static final int WALL_HALF_WIDTH = 0;
    /**
     * The client-facing column must cover its entire BARRIER cell.  Smaller
     * display scales turn a continuous collision wall into disconnected,
     * easy-to-miss posts from a player's viewpoint.
     */
    public static final float VISUAL_CELL_SCALE = 1.0F;
    /** Centres a scaled BlockDisplay on the same block cell as its barrier. */
    public static final float VISUAL_CELL_TRANSLATION = -0.5F;

    private static final int FIRST_RADIUS = 1;
    private static final int LAST_RADIUS = 31;
    private static final int MAX_CHAMBERS = 4;

    private RealitySplitBarrierPolicy() {
    }

    public static List<Cell> cells(int chamberCount) {
        return cellsExcludingBoundaries(chamberCount, Set.of());
    }

    /**
     * Return only the still-closed physical separators.  The open boundary
     * set is supplied by the persisted Wave 7 graph so a restart cannot
     * recreate a passage that players have already crossed.
     */
    public static List<Cell> cellsExcludingBoundaries(int chamberCount,
                                                       Set<Integer> openBoundaries) {
        int count = safeChamberCount(chamberCount);
        Set<Cell> result = new LinkedHashSet<>();
        Set<Integer> excluded = new LinkedHashSet<>();
        if (openBoundaries != null) {
            for (Integer boundary : openBoundaries) {
                if (boundary != null && boundary >= 0 && boundary < boundaryCount(count)) {
                    excluded.add(boundary);
                }
            }
        }
        for (int boundary = 0; boundary < boundaryCount(count); boundary++) {
            if (excluded.contains(boundary)) {
                continue;
            }
            result.addAll(cellsForBoundary(boundary, count));
        }
        return List.copyOf(result);
    }

    public static List<Cell> cellsForBoundary(int boundary, int chamberCount) {
        int count = safeChamberCount(chamberCount);
        if (boundary < 0 || boundary >= boundaryCount(count)) {
            return List.of();
        }
        Set<Cell> result = new LinkedHashSet<>();
        double angle = boundaryAngle(boundary, count);
        int directions = count == 2 ? 2 : 1;
        for (int direction = 0; direction < directions; direction++) {
            double sign = direction == 0 ? 1.0D : -1.0D;
            int previousX = 0;
            int previousZ = 0;
            boolean hasPrevious = false;
            for (int radius = FIRST_RADIUS; radius <= LAST_RADIUS; radius++) {
                int targetX = (int) Math.round(Math.cos(angle) * radius * sign);
                int targetZ = (int) Math.round(Math.sin(angle) * radius * sign);
                if (WALL_HALF_WIDTH == 0) {
                    // A rounded diagonal can jump from (x,z) to (x+1,z+1),
                    // leaving a corner gap that a player can squeeze through.
                    // Connect successive samples with an edge-connected,
                    // cardinal staircase.  It remains one block wide while
                    // making the union of collision cells continuous.
                    if (!hasPrevious) {
                        addCellColumn(result, targetX, targetZ);
                    } else {
                        addCardinalSegment(result, previousX, previousZ, targetX, targetZ);
                    }
                } else {
                    double perpendicularX = -Math.sin(angle) * sign;
                    double perpendicularZ = Math.cos(angle) * sign;
                    for (int width = -WALL_HALF_WIDTH; width <= WALL_HALF_WIDTH; width++) {
                        int x = (int) Math.round(Math.cos(angle) * radius * sign
                                + perpendicularX * width);
                        int z = (int) Math.round(Math.sin(angle) * radius * sign
                                + perpendicularZ * width);
                        addCellColumn(result, x, z);
                    }
                }
                previousX = targetX;
                previousZ = targetZ;
                hasPrevious = true;
            }
        }
        return List.copyOf(result);
    }

    private static void addCardinalSegment(Set<Cell> result,
                                           int startX, int startZ,
                                           int targetX, int targetZ) {
        int x = startX;
        int z = startZ;
        while (x != targetX || z != targetZ) {
            int dx = targetX - x;
            int dz = targetZ - z;
            if (dx != 0 && (dz == 0 || Math.abs(dx) >= Math.abs(dz))) {
                x += Integer.signum(dx);
            } else {
                z += Integer.signum(dz);
            }
            addCellColumn(result, x, z);
        }
    }

    private static void addCellColumn(Set<Cell> result, int x, int z) {
        for (int level = 1; level <= HEIGHT; level++) {
            result.add(new Cell(x, z, level));
        }
    }

    /**
     * Map only adjacent room pairs to a physical radial gate.  A diagonal
     * graph edge may still be recorded by the logical controller, but it must
     * not remove an unrelated wall.
     */
    public static int boundaryForPair(int first, int second, int chamberCount) {
        int count = safeChamberCount(chamberCount);
        if (first < 0 || second < 0 || first >= count || second >= count || first == second) {
            return -1;
        }
        if (count == 2) {
            return 0;
        }
        if ((first + 1) % count == second) {
            return first;
        }
        if ((second + 1) % count == first) {
            return second;
        }
        return -1;
    }

    public record Cell(int xOffset, int zOffset, int level) {
    }

    private static int safeChamberCount(int chamberCount) {
        return Math.max(2, Math.min(MAX_CHAMBERS, chamberCount));
    }

    private static int boundaryCount(int chamberCount) {
        return chamberCount == 2 ? 1 : chamberCount;
    }

    private static double boundaryAngle(int boundary, int chamberCount) {
        return -Math.PI / 2.0D
                + Math.PI * 2.0D * (boundary + 0.5D) / chamberCount;
    }
}
