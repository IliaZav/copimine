package me.copimine.endevent.domain;

import java.util.Collection;

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
}
