package me.copimine.client;

import java.util.Set;
import java.util.UUID;

/** Bounds remote shield candidates and the world scan used to discover them. */
final class EndRiftGuardianShieldCandidatePolicy {
    static final int MAX_CANDIDATES = 4;
    static final int MAX_ENTITY_INSPECTIONS = 256;

    private EndRiftGuardianShieldCandidatePolicy() {
    }

    static boolean admit(Set<UUID> admitted, UUID entityId,
                         boolean bridgeBound, boolean markerPresent) {
        if (admitted == null || entityId == null || (!bridgeBound && !markerPresent)) return false;
        if (admitted.contains(entityId)) return true;
        if (admitted.size() >= MAX_CANDIDATES) return false;
        return admitted.add(entityId);
    }

    static boolean isFull(Set<UUID> admitted) {
        return admitted != null && admitted.size() >= MAX_CANDIDATES;
    }

    static boolean canInspectEntity(int inspectedCount) {
        return inspectedCount >= 0 && inspectedCount < MAX_ENTITY_INSPECTIONS;
    }
}
