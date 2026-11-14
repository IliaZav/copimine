package me.copimine.endevent.domain;

import java.util.*;

/** Cast fairness is independent of the grounded boss's pursuit focus and lock. */
public final class BossSpellTargetSelector {
    private long generation = Long.MIN_VALUE;
    private int cursor;
    private final Deque<UUID> recent = new ArrayDeque<>();

    public boolean recentlyTargeted(long currentGeneration, UUID player) {
        return generation == currentGeneration && player != null && recent.contains(player);
    }

    /** Record an accepted cast only; perception and rejected proposals are read-only. */
    public void recordAccepted(long currentGeneration, UUID player, int recentLimit) {
        if (currentGeneration <= 0L || player == null) return;
        if (generation != currentGeneration) {
            generation = currentGeneration; cursor = 0; recent.clear();
        }
        recent.remove(player);
        recent.addLast(player);
        int limit = Math.max(0, Math.min(20, recentLimit));
        while (recent.size() > limit) recent.removeFirst();
    }

    public UUID choose(long currentGeneration, List<UUID> candidates, int recentLimit) {
        if (currentGeneration <= 0L) return null;
        if (generation != currentGeneration) {
            generation = currentGeneration; cursor = 0; recent.clear();
        }
        List<UUID> eligible = candidates == null ? List.of() : candidates.stream()
                .filter(Objects::nonNull).distinct().sorted(Comparator.comparing(UUID::toString)).toList();
        if (eligible.isEmpty()) return null;
        recent.removeIf(id -> !eligible.contains(id));
        UUID selected = EndRiftAiPolicy.chooseFairTarget(eligible, null,
                new ArrayList<>(recent), cursor).target();
        cursor = cursor == Integer.MAX_VALUE ? 0 : cursor + 1;
        if (selected != null) {
            recent.remove(selected); recent.addLast(selected);
            int limit = Math.max(0, Math.min(20, recentLimit));
            while (recent.size() > limit) recent.removeFirst();
        }
        return selected;
    }
}
