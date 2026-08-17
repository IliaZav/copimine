package me.copimine.endevent.domain;

import java.util.Set;
import java.util.UUID;

public final class RewardRoster {
    private final Set<UUID> players;

    private RewardRoster(Set<UUID> players) {
        this.players = Set.copyOf(players);
    }

    public static RewardRoster commitExactly(Set<UUID> occupants, int requiredPlayers) {
        if (occupants == null || requiredPlayers < 1 || occupants.size() != requiredPlayers
                || occupants.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("official roster must contain exactly required distinct players");
        }
        return new RewardRoster(occupants);
    }

    public Set<UUID> players() {
        return players;
    }
}
