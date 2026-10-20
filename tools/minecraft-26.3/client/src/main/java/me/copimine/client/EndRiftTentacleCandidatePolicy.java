package me.copimine.client;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Applies one bounded admission set to both carrier suppression and custom rendering. */
public final class EndRiftTentacleCandidatePolicy {
    public static final int MAX_CANDIDATES = 16;
    public static final int MAX_ENTITY_INSPECTIONS = 256;

    private EndRiftTentacleCandidatePolicy() { }

    public static boolean admit(Set<UUID> admitted, UUID entityId,
                                boolean bridgeBound, boolean markerPresent) {
        if (admitted == null || entityId == null || (!bridgeBound && !markerPresent)) return false;
        if (admitted.contains(entityId)) return true;
        if (admitted.size() >= MAX_CANDIDATES) return false;
        return admitted.add(entityId);
    }

    public static boolean isFull(Set<UUID> admitted) {
        return admitted != null && admitted.size() >= MAX_CANDIDATES;
    }

    public static boolean canInspectEntity(int inspectedCount) {
        return inspectedCount >= 0 && inspectedCount < MAX_ENTITY_INSPECTIONS;
    }

    public static List<UUID> snapshot(Set<UUID> admitted) {
        return admitted == null ? List.of() : List.copyOf(admitted);
    }
}
