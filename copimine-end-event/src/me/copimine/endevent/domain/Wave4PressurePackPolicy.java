package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Stable slots and bounded retry timing for Wave 4's one pressure pack. */
public final class Wave4PressurePackPolicy {
    public static final int MAX_RETRIES = 3;
    public static final int MAX_TOTAL_ATTEMPTS = MAX_RETRIES + 1;
    public static final long RETRY_BASE_MILLIS = 250L;

    private Wave4PressurePackPolicy() { }

    public static List<Slot> plan(WaveMechanicsPolicy.WaveCounts composition) {
        if (composition == null) return List.of();
        List<Slot> slots = new ArrayList<>(composition.total());
        append(slots, "STALKER", composition.spiders());
        append(slots, "BRUTE", composition.endermen());
        append(slots, "CHANNELER", composition.skeletons());
        return List.copyOf(slots);
    }

    public static List<Slot> missing(List<Slot> planned, Set<String> spawnedSlots) {
        if (planned == null || planned.isEmpty()) return List.of();
        Set<String> present = spawnedSlots == null ? Set.of() : spawnedSlots;
        return planned.stream().filter(slot -> !present.contains(slot.key())).toList();
    }

    public static boolean shouldAttempt(int attempts, long now, long retryAtMillis) {
        return attempts >= 0 && attempts < MAX_TOTAL_ATTEMPTS && now >= retryAtMillis;
    }

    public static boolean exhausted(int attempts) {
        return attempts >= MAX_TOTAL_ATTEMPTS;
    }

    public static long retryDelayMillis(int failedAttempts) {
        int boundedFailure = Math.max(1, Math.min(MAX_RETRIES, failedAttempts));
        return RETRY_BASE_MILLIS * boundedFailure;
    }

    private static void append(List<Slot> slots, String role, int count) {
        for (int ordinal = 0; ordinal < Math.max(0, count); ordinal++) {
            slots.add(new Slot(role, ordinal, slots.size()));
        }
    }

    public record Slot(String role, int ordinal, int spawnIndex) {
        public String key() {
            return role + ":" + ordinal;
        }
    }
}
