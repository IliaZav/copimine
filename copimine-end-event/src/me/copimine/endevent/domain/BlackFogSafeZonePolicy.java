package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;

/** Exact square footprints for the shrinking safe zones in Wave 5. */
public final class BlackFogSafeZonePolicy {
    private static final int MAX_SIDE = 3;

    private BlackFogSafeZonePolicy() {
    }

    public static List<Cell> cells(int centerX, int centerZ, int side) {
        if (side < 1 || side > MAX_SIDE) {
            return List.of();
        }
        int firstOffset = -(side / 2);
        int endOffset = firstOffset + side;
        List<Cell> cells = new ArrayList<>(side * side);
        for (int dx = firstOffset; dx < endOffset; dx++) {
            for (int dz = firstOffset; dz < endOffset; dz++) {
                cells.add(new Cell(centerX + dx, centerZ + dz));
            }
        }
        return List.copyOf(cells);
    }

    public record Cell(int x, int z) {
    }
}
