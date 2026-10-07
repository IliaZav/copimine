package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Deterministic participant pairing for the independent Wave 6 lanes. */
public final class CollapseRingPlayerPairPolicy {
    public static final long ROTATION_PERIOD_TICKS = CollapseRingEncounterPolicy.PAIR_TIMER_TICKS;

    private CollapseRingPlayerPairPolicy() {
    }

    /**
     * Pair a frozen roster in UUID order. An odd final roster member remains an
     * explicit one-player lane instead of being silently dropped or duplicated.
     * When there are more pairs than rings, pair lanes share a ring but retain
     * independent participant sets.
     */
    public static List<PlayerPair> assign(List<UUID> roster, int ringCount) {
        if (ringCount < 1 || ringCount > CollapseRingGeometryPolicy.RING_COUNT) {
            throw new IllegalArgumentException("ring count is outside Wave 6");
        }
        List<UUID> players = new ArrayList<>(new LinkedHashSet<>(
                roster == null ? List.of() : roster));
        players.removeIf(value -> value == null);
        players.sort(Comparator.comparing(UUID::toString));
        List<PlayerPair> result = new ArrayList<>();
        for (int index = 0; index < players.size(); index += 2) {
            Set<UUID> members = new LinkedHashSet<>();
            members.add(players.get(index));
            if (index + 1 < players.size()) {
                members.add(players.get(index + 1));
            }
            result.add(new PlayerPair(index / 2, (index / 2) % ringCount, members));
        }
        return List.copyOf(result);
    }

    /** Union all independent pairs that occupy one spatial ring. */
    public static Set<UUID> playersForRing(int ring, List<UUID> roster, int ringCount) {
        if (ring < 0 || ring >= ringCount) {
            return Set.of();
        }
        Set<UUID> result = new LinkedHashSet<>();
        for (PlayerPair pair : assign(roster, ringCount)) {
            if (pair.ringId() == ring) {
                result.addAll(pair.players());
            }
        }
        return Set.copyOf(result);
    }

    /** Local, pair-relative visual rotation; server uptime never enters it. */
    public static double relativeAngle(long rotationStartedTick, long nowTick) {
        long elapsed = Math.max(0L, Math.max(0L, nowTick) - Math.max(0L, rotationStartedTick));
        return Math.floorMod(elapsed, ROTATION_PERIOD_TICKS)
                * (Math.PI * 2.0D / ROTATION_PERIOD_TICKS);
    }

    public record PlayerPair(int id, int ringId, Set<UUID> players) {
        public PlayerPair {
            if (id < 0 || ringId < 0 || ringId >= CollapseRingGeometryPolicy.RING_COUNT
                    || players == null || players.isEmpty() || players.size() > 2) {
                throw new IllegalArgumentException("Wave 6 player pair is invalid");
            }
            players = Set.copyOf(players);
        }
    }
}
