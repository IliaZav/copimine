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
        int[] ordinary = {
                Math.max(0, composition.endermen()),
                Math.max(0, composition.spiders()),
                Math.max(0, composition.skeletons())};
        int ordinaryTotal = ordinary[0] + ordinary[1] + ordinary[2];
        int zombies = ordinaryTotal == 0 ? 0 : 1;
        int giants = ordinaryTotal >= 8 ? 1 : 0;

        // Keep the roster's existing pressure size. New W4 roles replace the
        // largest current ordinary role bucket, with stable tie ordering.
        for (int replacement = 0; replacement < zombies + giants; replacement++) {
            int selected = largestOrdinaryBucket(ordinary);
            if (selected < 0) break;
            ordinary[selected]--;
        }
        append(slots, "STALKER", ordinary[1]);
        append(slots, "BRUTE", ordinary[0]);
        append(slots, "CHANNELER", ordinary[2]);
        append(slots, "ZOMBIE", zombies);
        append(slots, "RIFT_GIANT", giants);
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

    private static int largestOrdinaryBucket(int[] counts) {
        int selected = -1;
        for (int index = 0; index < counts.length; index++) {
            if (counts[index] > 0 && (selected < 0 || counts[index] > counts[selected])) {
                selected = index;
            }
        }
        return selected;
    }

    public record Slot(String role, int ordinal, int spawnIndex) {
        public String key() {
            return role + ":" + ordinal;
        }
    }
}
