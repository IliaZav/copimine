package me.copimine.endevent.domain;

import java.util.List;
import java.util.UUID;

public final class RitualTargetPolicy {
    private RitualTargetPolicy() {
    }

    public static List<UUID> freeTargets(List<Candidate> candidates, UUID prisoner) {
        if (candidates == null) {
            return List.of();
        }
        return candidates.stream()
                .filter(Candidate::eligible)
                .map(Candidate::playerId)
                .filter(id -> prisoner == null || !prisoner.equals(id))
                .distinct()
                .toList();
    }

    public record Candidate(UUID playerId, boolean eligible) {
        public Candidate {
            if (playerId == null) {
                throw new IllegalArgumentException("playerId is required");
            }
        }
    }
}
