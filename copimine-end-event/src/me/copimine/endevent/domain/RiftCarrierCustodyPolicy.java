package me.copimine.endevent.domain;

import java.util.List;
import java.util.UUID;

/** Resolves the one legal player custodian for a current Wave 1 charge token. */
public final class RiftCarrierCustodyPolicy {
    private RiftCarrierCustodyPolicy() { }

    public enum Slot {
        STORAGE,
        OFF_HAND,
        CURSOR,
        OBSERVED_INVENTORY,
        WORLD_ITEM
    }

    /** One observed copy, including copies that have no eligible player owner. */
    public record TokenLocation(UUID playerId, Slot slot, int amount,
                                boolean currentToken, boolean activeParticipant) { }

    /** A valid result identifies the player and inventory surface holding the single token. */
    public record Decision(boolean valid, UUID holder, Slot slot, int copies) { }

    /** Rejects duplicate/stale/external custody; only storage and off-hand slots retain a charge. */
    public static Decision resolve(List<TokenLocation> locations) {
        if (locations == null || locations.isEmpty()) return new Decision(false, null, null, 0);
        int copies = 0;
        TokenLocation candidate = null;
        for (TokenLocation location : locations) {
            if (location == null || !location.currentToken()) continue;
            if (location.slot() == null) return new Decision(false, null, null, 2);
            copies = Math.min(2, copies + Math.max(1, location.amount()));
            if (location.slot() == Slot.STORAGE || location.slot() == Slot.OFF_HAND) {
                candidate = location;
            }
        }
        boolean valid = copies == 1 && candidate != null && candidate.playerId() != null
                && candidate.activeParticipant();
        return valid
                ? new Decision(true, candidate.playerId(), candidate.slot(), copies)
                : new Decision(false, null, null, copies);
    }
}
