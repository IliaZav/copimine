package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure reward sizing and deterministic shared-rare decision for a wave. */
public final class WaveRewardPolicy {
    public static final int MAX_PERSONAL_STACKS = 8;

    private WaveRewardPolicy() {
    }

    public static RewardBundle bundle(int wave, int playerIndex, int participantCount,
                                      Map<String, Integer> configured) {
        if (wave < 1 || wave > 5 || playerIndex < 0 || participantCount < 1
                || playerIndex >= participantCount || configured == null || configured.isEmpty()) {
            throw new IllegalArgumentException("invalid wave reward request");
        }
        Map<String, Integer> merged = new LinkedHashMap<>();
        configured.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String material = entry.getKey() == null ? "" : entry.getKey().trim().toUpperCase();
                    int amount = entry.getValue() == null ? 0 : entry.getValue();
                    if (!material.isBlank() && amount > 0) {
                        merged.merge(material, amount, Math::addExact);
                    }
                });
        if (merged.isEmpty() || merged.size() > MAX_PERSONAL_STACKS) {
            throw new IllegalArgumentException("wave reward must contain one to eight merged stacks");
        }
        List<RewardStack> stacks = new ArrayList<>();
        merged.forEach((material, amount) -> stacks.add(new RewardStack(material, amount)));
        return new RewardBundle(List.copyOf(stacks), participantCount);
    }

    /** One deterministic roll key; callers must persist the result by event/wave. */
    public static boolean sharedRareRoll(String eventId, int wave) {
        return sharedRareRoll(eventId, wave, 0.25D);
    }

    /**
     * Deterministic, bounded shared roll.  The caller persists the decision
     * under the event/wave key and must not roll once per participant.
     */
    public static boolean sharedRareRoll(String eventId, int wave, double chance) {
        if (eventId == null || eventId.isBlank() || wave < 1 || wave > 5) {
            return false;
        }
        if (Double.isNaN(chance) || Double.isInfinite(chance) || chance <= 0.0D || chance > 1.0D) {
            return false;
        }
        if (chance >= 1.0D) {
            return true;
        }
        long hash = 1125899906842597L;
        hash = 31L * hash + eventId.hashCode();
        hash = 31L * hash + wave;
        long bucket = Math.floorMod(hash, 10_000L);
        return bucket < Math.round(chance * 10_000.0D);
    }

    public record RewardBundle(List<RewardStack> stacks, int participantCount) {
        public RewardBundle {
            stacks = List.copyOf(stacks == null ? List.of() : stacks);
            if (stacks.isEmpty() || stacks.size() > MAX_PERSONAL_STACKS || participantCount < 1
                    || stacks.stream().anyMatch(stack -> stack == null)) {
                throw new IllegalArgumentException("invalid reward bundle");
            }
        }
    }

    public record RewardStack(String material, int amount) {
        public RewardStack {
            material = material == null ? "" : material.trim().toUpperCase();
            if (material.isBlank() || amount < 1) {
                throw new IllegalArgumentException("invalid reward stack");
            }
        }
    }
}
