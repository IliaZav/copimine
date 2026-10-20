package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;

/** Exact square footprints for the shrinking safe zones in Wave 5. */
public final class BlackFogSafeZonePolicy {
    private static final int MAX_SIDE = 3;

    private BlackFogSafeZonePolicy() {
    }

    /**
     * Returns the safe-zone side for a zero-based fog cycle and living player
     * count. Later cycles always shrink the footprint, while larger groups
     * receive a smaller maximum area. Invalid cycles or empty rosters return
     * zero so callers can fail closed without creating a safe zone.
     */
    public static int sideForCycleAndPlayers(int cycleIndex, int livingPlayers) {
        if (cycleIndex < 0 || cycleIndex >= BlackFogTimingPolicy.CYCLE_COUNT
                || livingPlayers < 1) {
            return 0;
        }

        int playerCap = livingPlayers <= 3 ? 3 : livingPlayers <= 5 ? 2 : 1;
        return Math.max(1, playerCap - cycleIndex);
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
