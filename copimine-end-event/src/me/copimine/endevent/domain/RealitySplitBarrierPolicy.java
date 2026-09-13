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
    public static final int HEIGHT = 3;
    public static final double MIN_RADIUS = 4.5D;
    public static final double MAX_RADIUS = 17.5D;
    public static final int MAX_CELLS = 512;
    /** A three-block cross-section keeps diagonal walls physically closed. */
    public static final int WALL_HALF_WIDTH = 1;
    /**
     * The client-facing column must cover its entire BARRIER cell.  Smaller
     * display scales turn a continuous collision wall into disconnected,
     * easy-to-miss posts from a player's viewpoint.
     */
    public static final float VISUAL_CELL_SCALE = 1.0F;
    /** Centres a scaled BlockDisplay on the same block cell as its barrier. */
    public static final float VISUAL_CELL_TRANSLATION = -0.5F;

    private static final int FIRST_RADIUS = 5;
    private static final int LAST_RADIUS = 17;
    private static final int MAX_CHAMBERS = 4;

    private RealitySplitBarrierPolicy() {
    }

    public static List<Cell> cells(int chamberCount) {
        int count = safeChamberCount(chamberCount);
        Set<Cell> result = new LinkedHashSet<>();
        for (int boundary = 0; boundary < boundaryCount(count); boundary++) {
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
            for (int radius = FIRST_RADIUS; radius <= LAST_RADIUS; radius++) {
                // Fill the perpendicular cross-section as well as the center
                // line.  A one-cell diagonal chain leaves corner gaps that a
                // player can cross; three cells keep the physical BARRIER
                // wall closed without making the room geometry excessive.
                double perpendicularX = -Math.sin(angle) * sign;
                double perpendicularZ = Math.cos(angle) * sign;
                for (int width = -WALL_HALF_WIDTH; width <= WALL_HALF_WIDTH; width++) {
                    int x = (int) Math.round(Math.cos(angle) * radius * sign
                            + perpendicularX * width);
                    int z = (int) Math.round(Math.sin(angle) * radius * sign
                            + perpendicularZ * width);
                    for (int level = 1; level <= HEIGHT; level++) {
                        result.add(new Cell(x, z, level));
                    }
                }
            }
        }
        return List.copyOf(result);
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
