package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Deterministic least-loaded target assignment for bounded event pressure. */
public final class TargetPressurePolicy {
    private TargetPressurePolicy() {
    }

    public record Assignment(List<UUID> targets, int nextCursor) {
        public Assignment {
            targets = List.copyOf(targets == null ? List.of() : targets);
            nextCursor = Math.max(0, nextCursor);
        }
    }

    public static Assignment distribute(List<UUID> candidates, int attackerCount,
                                        int maxPerTarget, int cursor) {
        List<UUID> unique = new ArrayList<>(new LinkedHashSet<>(candidates == null ? List.of() : candidates));
        unique.remove(null);
        int count = Math.max(0, attackerCount);
        int cap = Math.max(1, maxPerTarget);
        if (unique.isEmpty() || count == 0) return new Assignment(List.of(), 0);

        Map<UUID, Integer> loads = new LinkedHashMap<>();
        for (UUID target : unique) loads.put(target, 0);
        List<UUID> result = new ArrayList<>();
        int start = Math.floorMod(cursor, unique.size());
        for (int index = 0; index < count; index++) {
            UUID selected = null;
            int selectedIndex = -1;
            int bestLoad = Integer.MAX_VALUE;
            for (int offset = 0; offset < unique.size(); offset++) {
                int candidateIndex = (start + offset) % unique.size();
                UUID candidate = unique.get(candidateIndex);
                int load = loads.get(candidate);
                if (load < cap && load < bestLoad) {
                    selected = candidate;
                    selectedIndex = candidateIndex;
                    bestLoad = load;
                }
            }
            if (selected == null) break;
            loads.put(selected, loads.get(selected) + 1);
            result.add(selected);
            start = (selectedIndex + 1) % unique.size();
        }
        return new Assignment(result, start);
    }
}
