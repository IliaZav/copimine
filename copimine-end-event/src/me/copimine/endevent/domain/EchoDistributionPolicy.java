package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ECHO-001 / ECHO-002: who fights where during the Personal Echo stage.
 *
 * <p>Every admitted participant gets exactly one real copy and fights
 * immediately; there is no queue of five copies for a full party. Up to nine
 * participants share the ordinary region. From ten, the roster splits across
 * the ordinary and limbo regions with a difference of at most one, assigned
 * deterministically so a rejoin can never hand someone an easier realm.
 */
public final class EchoDistributionPolicy {
    /** Below this size the ordinary region alone is used. */
    public static final int SPLIT_FROM = 10;
    public static final int MIN_PARTICIPANTS = 2;
    public static final int MAX_PARTICIPANTS = 20;

    /** The two regions a personal duel may run in. */
    public enum Region {
        ORDINARY,
        LIMBO
    }

    private EchoDistributionPolicy() {
    }

    /**
     * A frozen assignment. The mapping is keyed by owner, and each owner owns
     * exactly one copy, so {@code ownerOf(id)} is the copy owner's own id.
     */
    public record Plan(Map<UUID, Region> regions) {
        public Plan {
            if (regions == null || regions.isEmpty()) {
                throw new IllegalArgumentException("an Echo plan needs at least one participant");
            }
            regions = Map.copyOf(regions);
        }

        public Region regionOf(UUID owner) {
            Region region = regions.get(owner);
            if (region == null) {
                throw new IllegalArgumentException("participant is not part of this Echo plan");
            }
            return region;
        }

        public boolean hasCopyFor(UUID owner) {
            return regions.containsKey(owner);
        }

        /** One copy per participant, so the copy's owner is the participant. */
        public UUID ownerOf(UUID owner) {
            if (!regions.containsKey(owner)) {
                throw new IllegalArgumentException("no copy belongs to this participant");
            }
            return owner;
        }

        public int totalCopies() {
            return regions.size();
        }

        public int playersIn(Region region) {
            int count = 0;
            for (Region assigned : regions.values()) {
                if (assigned == region) {
                    count++;
                }
            }
            return count;
        }

        /**
         * Re-admits a participant that died or disconnected. The frozen region
         * is kept, so returning never moves anyone to the easy side.
         */
        public Plan withReturning(UUID owner) {
            if (!regions.containsKey(owner)) {
                throw new IllegalArgumentException("returning participant was never admitted");
            }
            return this;
        }
    }

    /**
     * Freezes the assignment for the admitted roster.
     *
     * <p>Sorting by the UUID string makes the result depend on who is present
     * and not on the order they happened to connect in, which is what keeps a
     * restart and a reconnect consistent with the fight already in progress.
     */
    public static Plan assign(List<UUID> admitted) {
        if (admitted == null || admitted.isEmpty()) {
            throw new IllegalArgumentException("the admitted roster is required");
        }
        List<UUID> sorted = new ArrayList<>(new LinkedHashSet<>(admitted));
        sorted.sort(Comparator.comparing(UUID::toString));
        int size = sorted.size();
        if (size < MIN_PARTICIPANTS || size > MAX_PARTICIPANTS) {
            throw new IllegalArgumentException("Echo supports 2-20 participants, got " + size);
        }
        if (size < SPLIT_FROM) {
            Map<UUID, Region> ordinaryOnly = new LinkedHashMap<>();
            for (UUID id : sorted) {
                ordinaryOnly.put(id, Region.ORDINARY);
            }
            return new Plan(ordinaryOnly);
        }
        // Split as evenly as possible: an odd roster puts the extra pair in the
        // ordinary region, which is the reference region for the fight itself.
        int ordinaryCount = (size + 1) / 2;
        Map<UUID, Region> split = new LinkedHashMap<>();
        int index = 0;
        for (UUID id : sorted) {
            split.put(id, index < ordinaryCount ? Region.ORDINARY : Region.LIMBO);
            index++;
        }
        return new Plan(split);
    }

    /** Convenience for callers that hold a set. */
    public static Plan assign(Set<UUID> admitted) {
        return assign(new ArrayList<>(admitted));
    }
}