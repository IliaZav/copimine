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
    /** Trial room separators stay close to the requested five-to-six-block height. */
    public static final int HEIGHT = 6;
    /** Later boss containment keeps its taller eight-block radial wall. */
    public static final int FINAL_SEAL_HEIGHT = 8;
    /** The Core block stays open; the level immediately above the boss is sealed. */
    public static final int FINAL_SEAL_CORE_CLEARANCE_LEVELS = 1;
    /** Every central level above the clearance is a required final-seal wall cell. */
    public static final int FINAL_SEAL_FIRST_COVERED_LEVEL =
            FINAL_SEAL_CORE_CLEARANCE_LEVELS + 1;
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
     * Full containment used only after Wave 7 has entered the final seal.
     *
     * <p>The ordinary radial walls deliberately leave the Core column open so
     * the Core remains usable during the room puzzle.  The final seal is a
     * different geometry: the boss stands on the Core, while the blocks above
     * the boss clearance must be closed so neither players nor the boss can
     * escape vertically through the old central shaft.  Level one stays open
     * at the centre for the Core block and the boss's feet; level two and above
     * are sealed.</p>
     */
    public static List<Cell> finalSealCells(int chamberCount) {
        Set<Cell> result = new LinkedHashSet<>(
                cellsExcludingBoundaries(chamberCount, Set.of(), FINAL_SEAL_HEIGHT));
        for (int level = FINAL_SEAL_FIRST_COVERED_LEVEL;
                level <= FINAL_SEAL_HEIGHT; level++) {
            result.add(new Cell(0, 0, level));
        }
        return List.copyOf(result);
    }

    /**
     * Return only the still-closed physical separators.  The open boundary
     * set is supplied by the persisted Wave 7 graph so a restart cannot
     * recreate a passage that players have already crossed.
     */
    public static List<Cell> cellsExcludingBoundaries(int chamberCount,
                                                       Set<Integer> openBoundaries) {
        return cellsExcludingBoundaries(chamberCount, openBoundaries, HEIGHT, true);
    }

    private static List<Cell> cellsExcludingBoundaries(int chamberCount,
                                                        Set<Integer> openBoundaries,
                                                        int wallHeight) {
        return cellsExcludingBoundaries(chamberCount, openBoundaries, wallHeight, false);
    }

    private static List<Cell> cellsExcludingBoundaries(int chamberCount,
                                                        Set<Integer> openBoundaries,
                                                        int wallHeight,
                                                        boolean includeCoreJoin) {
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
        Set<Cell> centralJoin = includeCoreJoin
                && excluded.size() < boundaryCount(count)
                ? centralJoin(wallHeight) : Set.of();
        for (int boundary = 0; boundary < boundaryCount(count); boundary++) {
            if (excluded.contains(boundary)) {
                continue;
            }
            result.addAll(cellsForBoundary(boundary, count, wallHeight));
        }
        result.addAll(centralJoin);
        return List.copyOf(result);
    }

    private static Set<Cell> centralJoin(int wallHeight) {
        Set<Cell> result = new LinkedHashSet<>();
        for (int level = 1; level <= wallHeight; level++) {
            result.add(new Cell(0, 0, level));
        }
        return result;
    }

    public static List<Cell> cellsForBoundary(int boundary, int chamberCount) {
        return cellsForBoundary(boundary, chamberCount, HEIGHT);
    }

    /** Cells owned by one still-closed Wave 7 boundary, including its shared Core junction. */
    public static List<Cell> cellsForClosedBoundary(int boundary, int chamberCount,
                                                    Set<Integer> openBoundaries) {
        int count = safeChamberCount(chamberCount);
        int boundaryCount = boundaryCount(count);
        if (boundary < 0 || boundary >= boundaryCount) {
            return List.of();
        }
        Set<Integer> excluded = new LinkedHashSet<>();
        if (openBoundaries != null) {
            for (Integer open : openBoundaries) {
                if (open != null && open >= 0 && open < boundaryCount) {
                    excluded.add(open);
                }
            }
        }
        if (excluded.contains(boundary)) {
            return List.of();
        }
        Set<Cell> result = new LinkedHashSet<>(cellsForBoundary(boundary, count));
        if (excluded.size() < boundaryCount) {
            result.addAll(centralJoin(HEIGHT));
        }
        return List.copyOf(result);
    }

    private static List<Cell> cellsForBoundary(int boundary, int chamberCount, int wallHeight) {
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
                        addCellColumn(result, targetX, targetZ, wallHeight);
                    } else {
                        addCardinalSegment(result, previousX, previousZ, targetX, targetZ,
                                wallHeight);
                    }
                } else {
                    double perpendicularX = -Math.sin(angle) * sign;
                    double perpendicularZ = Math.cos(angle) * sign;
                    for (int width = -WALL_HALF_WIDTH; width <= WALL_HALF_WIDTH; width++) {
                        int x = (int) Math.round(Math.cos(angle) * radius * sign
                                + perpendicularX * width);
                        int z = (int) Math.round(Math.sin(angle) * radius * sign
                                + perpendicularZ * width);
                        addCellColumn(result, x, z, wallHeight);
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
                                           int targetX, int targetZ,
                                           int wallHeight) {
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
            addCellColumn(result, x, z, wallHeight);
        }
    }

    private static void addCellColumn(Set<Cell> result, int x, int z, int wallHeight) {
        for (int level = 1; level <= wallHeight; level++) {
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
