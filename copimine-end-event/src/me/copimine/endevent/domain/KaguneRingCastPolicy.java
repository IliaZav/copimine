package me.copimine.endevent.domain;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Master contract 20.3: one direct damage event per player per overall
 * three-row Kagune ring cast. Three rows must never stack onto one launched
 * player, and a cast never hits the same player twice even across rows.
 */
public final class KaguneRingCastPolicy {
    private final Set<UUID> struck = new HashSet<>();
    private int hits;

    /**
     * Claim the single hit for this player in this cast.
     *
     * @return true only for the first accepted claim
     */
    public boolean claim(UUID playerId) {
        if (playerId == null || !struck.add(playerId)) {
            return false;
        }
        hits++;
        return true;
    }

    public boolean alreadyHit(UUID playerId) {
        return playerId != null && struck.contains(playerId);
    }

    public int hitCount() {
        return hits;
    }

    /** Lethal/absent players never consume the one hit, so a rejoin cannot dodge... */
    public Set<UUID> struckPlayers() {
        return Set.copyOf(struck);
    }

    public void reset() {
        struck.clear();
        hits = 0;
    }

    /** A cast is complete when every row has retracted; the tracker is then replaced. */
    public static boolean castComplete(int rowsFinished) {
        return rowsFinished >= KaguneRingGapPolicy.ROW_COUNT;
    }

    /** Per-cast, per-player ledger used by adapters that need a stable ordering. */
    public static final class Ordered {
        private final Map<UUID, Integer> seen = new HashMap<>();

        public boolean claim(UUID playerId) {
            if (playerId == null || seen.containsKey(playerId)) {
                return false;
            }
            seen.put(playerId, seen.size());
            return true;
        }

        public int claimIndex(UUID playerId) {
            return seen.getOrDefault(playerId, -1);
        }
    }
}