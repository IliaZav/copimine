package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Pure pairing and input-safety rules for the Wave 6 control-swap effect. */
public final class RitualControlPairPolicy {
    public static final int MAX_PAIRS = 3;

    private RitualControlPairPolicy() {
    }

    public static boolean canActivate(boolean reverseMovement, boolean controlSwap) {
        return !(reverseMovement && controlSwap);
    }

    public static List<Pair> pair(List<String> participantIds, int requestedPairs) {
        return pair(participantIds, null, requestedPairs);
    }

    public static List<Pair> pair(List<String> participantIds, String excludedId, int requestedPairs) {
        if (participantIds == null || requestedPairs <= 0) {
            return List.of();
        }
        int limit = Math.min(MAX_PAIRS, requestedPairs);
        Set<String> unique = new LinkedHashSet<>();
        for (String id : participantIds) {
            if (id == null || id.isBlank()) {
                continue;
            }
            String normalized = id.trim();
            if (excludedId != null && excludedId.equals(normalized)) {
                continue;
            }
            unique.add(normalized);
        }
        List<String> ids = new ArrayList<>(unique);
        List<Pair> result = new ArrayList<>();
        for (int index = 0; index + 1 < ids.size() && result.size() < limit; index += 2) {
            result.add(new Pair(ids.get(index), ids.get(index + 1)));
        }
        return List.copyOf(result);
    }

    public static double boundedInput(double input) {
        if (!Double.isFinite(input)) {
            return 0.0D;
        }
        return Math.max(-1.0D, Math.min(1.0D, input));
    }

    public record Pair(String first, String second) {
        public Pair {
            if (first == null || first.isBlank() || second == null || second.isBlank()
                    || first.equals(second)) {
                throw new IllegalArgumentException("control pair must contain two distinct players");
            }
        }
    }
}
