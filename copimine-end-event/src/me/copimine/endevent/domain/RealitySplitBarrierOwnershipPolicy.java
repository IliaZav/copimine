package me.copimine.endevent.domain;

import java.util.Map;
import java.util.Set;

/** Determines whether a shared Wave 7 barrier cell still belongs to another closed boundary. */
public final class RealitySplitBarrierOwnershipPolicy {
    private RealitySplitBarrierOwnershipPolicy() {
    }

    public static boolean isNeededByOtherBoundary(
            Map<Integer, ? extends Set<RealitySplitBarrierPolicy.Cell>> cellsByBoundary,
            int boundaryBeingOpened,
            RealitySplitBarrierPolicy.Cell cell) {
        if (cellsByBoundary == null || cell == null) {
            return false;
        }
        for (Map.Entry<Integer, ? extends Set<RealitySplitBarrierPolicy.Cell>> entry
                : cellsByBoundary.entrySet()) {
            Integer boundary = entry.getKey();
            if (boundary == null || boundary == boundaryBeingOpened) {
                continue;
            }
            Set<RealitySplitBarrierPolicy.Cell> cells = entry.getValue();
            if (cells != null && cells.contains(cell)) {
                return true;
            }
        }
        return false;
    }
}
