package me.copimine.endevent.domain;

import java.util.Collection;
import java.util.Optional;

/** Read-only acceptance rules for the cells a Wave 7 barrier would claim. */
public final class Wave7BarrierPreflightPolicy {
    private Wave7BarrierPreflightPolicy() { }

    public record Candidate(boolean allowedLocation, boolean gateLocation,
                            boolean floorSolid, boolean floorLiquid,
                            boolean barrierLiquid, boolean tileState,
                            boolean barrierPassable, boolean fullCollisionWall) { }

    public static boolean accepts(Candidate candidate) {
        return candidate != null && candidate.allowedLocation() && !candidate.gateLocation()
                && candidate.floorSolid() && !candidate.floorLiquid()
                && !candidate.barrierLiquid() && !candidate.tileState()
                && (candidate.barrierPassable() || candidate.fullCollisionWall());
    }

    public static boolean acceptsAll(Collection<Candidate> candidates) {
        if (candidates == null) return false;
        for (Candidate candidate : candidates) {
            if (!accepts(candidate)) return false;
        }
        return true;
    }

    /**
     * Resolve a retained journal entry against canonical wall geometry using
     * its absolute floor-block Y. The geometry supplies the expected level;
     * the journal Y must match that level rather than defining it.
     */
    public static Optional<RealitySplitBarrierPolicy.Cell> matchJournalCell(
            int xOffset, int zOffset, int journalFloorY, int combatFloorY,
            Collection<RealitySplitBarrierPolicy.Cell> geometryCells) {
        if (geometryCells == null) return Optional.empty();
        for (RealitySplitBarrierPolicy.Cell cell : geometryCells) {
            if (cell == null || cell.level() < 1
                    || cell.xOffset() != xOffset || cell.zOffset() != zOffset) continue;
            long expectedFloorY = (long) combatFloorY + cell.level() - 1L;
            if (expectedFloorY == journalFloorY) return Optional.of(cell);
        }
        return Optional.empty();
    }
}
