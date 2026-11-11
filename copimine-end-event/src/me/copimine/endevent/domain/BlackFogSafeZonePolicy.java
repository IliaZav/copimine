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
     * count. The footprint shrinks monotonically across every cycle, stays
     * usable for the whole frozen party even in the final cycle. Invalid cycles or empty rosters return zero so
     * callers can fail closed without creating a safe zone.
     */
    public static int sideForCycleAndPlayers(int cycleIndex, int livingPlayers) {
        if (cycleIndex < 0 || cycleIndex >= BlackFogTimingPolicy.CYCLE_COUNT
                || livingPlayers < 1) {
            return 0;
        }

        double progress = BlackFogTimingPolicy.progress(cycleIndex,
                BlackFogTimingPolicy.cycleCount(livingPlayers));
        int authoredSide = progress < 1.0D / 3.0D ? 3 : progress < 2.0D / 3.0D ? 2 : 1;
        // Freeze the footprint floor against the smallest late-cycle zone count.
        // This preserves monotonic shrinking while retaining one cell per admitted player.
        int players = Math.min(20, livingPlayers);
        int finalZones = BlackFogTimingPolicy.zoneCount(players,
                BlackFogTimingPolicy.cycleCount(players) - 1);
        int capacitySide = 1;
        while (capacitySide < MAX_SIDE && finalZones * capacitySide * capacitySide < players) capacitySide++;
        return Math.max(authoredSide, capacitySide);
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

    /**
     * The perimeter ring of a safe-zone footprint, in authoring order.
     *
     * <p>The floor tiles already mark the zone, but from inside the black fog a
     * player reads the EDGE of the safe area, not its centre. This ring is what
     * the runtime draws as glowing emerald particles so the boundary is visible
     * from anywhere in the arena. A one-cell footprint is its own perimeter.</p>
     *
     * @return the outermost cells of the footprint, never the interior
     */
    public static List<Cell> perimeter(int centerX, int centerZ, int side) {
        if (side < 1 || side > MAX_SIDE) {
            return List.of();
        }
        int firstOffset = -(side / 2);
        int lastOffset = firstOffset + side - 1;
        List<Cell> ring = new ArrayList<>(side * 4);
        for (int dx = firstOffset; dx <= lastOffset; dx++) {
            for (int dz = firstOffset; dz <= lastOffset; dz++) {
                boolean onEdge = dx == firstOffset || dx == lastOffset
                        || dz == firstOffset || dz == lastOffset;
                if (!onEdge) {
                    continue;
                }
                ring.add(new Cell(centerX + dx, centerZ + dz));
            }
        }
        return List.copyOf(ring);
    }

    public record Cell(int x, int z) {
    }
}
