package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;

/** Pure geometry/timeline contract for a physical five-block Wave 4 obelisk. */
public final class ObeliskGeometryPolicy {
    public static final int EMERGENCE_TICKS = 70;
    public static final int FOOTPRINT_RADIUS = 1;
    public static final int HEIGHT = 5;

    private ObeliskGeometryPolicy() {
    }

    public enum Stage {
        GROUND_WARNING,
        EMERGE_BASE,
        EMERGE_LOWER,
        EMERGE_CORE,
        EMERGE_UPPER,
        EMERGE_CROWN,
        AWAKEN,
        ACTIVE
    }

    /** A cell also carries a stable layer, which keeps model and block views aligned. */
    public record Cell(int x, int y, int z, int layer) {
        public String key() {
            return x + ":" + y + ":" + z;
        }
    }

    public static Stage stageAt(long elapsedTicks) {
        long elapsed = Math.max(0L, elapsedTicks);
        if (elapsed < 10L) {
            return Stage.GROUND_WARNING;
        }
        if (elapsed < 20L) {
            return Stage.EMERGE_BASE;
        }
        if (elapsed < 30L) {
            return Stage.EMERGE_LOWER;
        }
        if (elapsed < 40L) {
            return Stage.EMERGE_CORE;
        }
        if (elapsed < 50L) {
            return Stage.EMERGE_UPPER;
        }
        if (elapsed < 60L) {
            return Stage.EMERGE_CROWN;
        }
        if (elapsed < EMERGENCE_TICKS) {
            return Stage.AWAKEN;
        }
        return Stage.ACTIVE;
    }

    /**
     * Returns a 3x3 footprint with five vertical layers.  The footprint is
     * intentionally more substantial than a single display or a 1x1 block:
     * the physical cells are the gameplay and recovery authority.
     */
    public static List<Cell> cells(int baseX, int baseY, int baseZ) {
        List<Cell> cells = new ArrayList<>(29);
        addSquare(cells, baseX, baseY, baseZ, 0);
        addSquare(cells, baseX, baseY + 1, baseZ, 1);
        addCross(cells, baseX, baseY + 2, baseZ, 2);
        addCross(cells, baseX, baseY + 3, baseZ, 3);
        cells.add(new Cell(baseX, baseY + 4, baseZ, 4));
        return List.copyOf(cells);
    }

    public static List<Cell> visibleCells(List<Cell> cells, Stage stage) {
        if (cells == null || stage == null) {
            return List.of();
        }
        int highestLayer = switch (stage) {
            case GROUND_WARNING -> -1;
            case EMERGE_BASE -> 0;
            case EMERGE_LOWER -> 1;
            case EMERGE_CORE -> 2;
            case EMERGE_UPPER -> 3;
            case EMERGE_CROWN, AWAKEN, ACTIVE -> 4;
        };
        return cells.stream().filter(cell -> cell.layer() <= highestLayer).toList();
    }

    private static void addSquare(List<Cell> cells, int x, int y, int z, int layer) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                cells.add(new Cell(x + dx, y, z + dz, layer));
            }
        }
    }

    private static void addCross(List<Cell> cells, int x, int y, int z, int layer) {
        cells.add(new Cell(x, y, z, layer));
        cells.add(new Cell(x - 1, y, z, layer));
        cells.add(new Cell(x + 1, y, z, layer));
        cells.add(new Cell(x, y, z - 1, layer));
        cells.add(new Cell(x, y, z + 1, layer));
    }
}
