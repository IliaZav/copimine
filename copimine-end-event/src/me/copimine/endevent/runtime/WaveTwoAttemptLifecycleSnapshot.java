package me.copimine.endevent.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Strict durable participant lifecycle for resuming one official Wave 2 attempt. */
public final class WaveTwoAttemptLifecycleSnapshot {
    private static final String PREFIX = "wave2-attempt-lifecycle.";
    private static final int MAX_ROSTER_SIZE = 100;

    private WaveTwoAttemptLifecycleSnapshot() { }

    public static Map<String, String> encode(String eventId, long generation, Set<UUID> roster,
                                              AttemptLifecycleController lifecycle,
                                              Map<UUID, Long> offlineDeadlines,
                                              long savedMillis, long graceMillis) {
        requireContext(eventId, generation, roster, lifecycle, offlineDeadlines, savedMillis, graceMillis);
        if (!lifecycle.owns(generation) || !lifecycle.registeredRoster().equals(roster)) {
            throw new IllegalArgumentException("Wave 2 lifecycle does not own the reward roster");
        }
        Map<String, String> encoded = new LinkedHashMap<>();
        encoded.put(PREFIX + "version", "1");
        encoded.put(PREFIX + "event", eventId);
        encoded.put(PREFIX + "generation", Long.toString(generation));
        encoded.put(PREFIX + "saved-millis", Long.toString(savedMillis));
        encoded.put(PREFIX + "roster", canonicalRoster(roster));
        long latestAllowedDeadline = Math.addExact(savedMillis, graceMillis);
        for (UUID owner : sorted(roster)) {
            AttemptLifecycleController.ParticipantStatus status = lifecycle.status(owner);
            Long deadlineValue = offlineDeadlines.get(owner);
            long deadline = deadlineValue == null ? 0L : deadlineValue;
            if (status == null || !status.registered() || deadline < 0L
                    || deadline > 0L && (!status.active() || status.online()
                    || deadline > latestAllowedDeadline)) {
                throw new IllegalArgumentException("Invalid Wave 2 participant lifecycle state");
            }
            String ownerPrefix = PREFIX + owner + ".";
            encoded.put(ownerPrefix + "active", Boolean.toString(status.active()));
            encoded.put(ownerPrefix + "alive", Boolean.toString(status.alive()));
            encoded.put(ownerPrefix + "offline-deadline-millis", Long.toString(deadline));
        }
        if (!roster.containsAll(offlineDeadlines.keySet())) {
            throw new IllegalArgumentException("Wave 2 offline deadline belongs to an unknown owner");
        }
        return Map.copyOf(encoded);
    }

    /** Returns null only when no lifecycle checkpoint exists; malformed checkpoints are rejected. */
    public static Snapshot decode(Map<String, String> encoded, String eventId, long generation,
                                  Set<UUID> roster, long nowMillis, long graceMillis) {
        if (encoded == null) throw new IllegalArgumentException("Wave 2 progress is required");
        boolean found = encoded.keySet().stream().anyMatch(key -> key.startsWith(PREFIX));
        if (!found) return null;
        if (eventId == null || eventId.isBlank() || generation <= 0L || roster == null || roster.isEmpty()
                || roster.size() > MAX_ROSTER_SIZE || roster.contains(null) || nowMillis <= 0L
                || graceMillis < 1000L || graceMillis > 120_000L) {
            throw new IllegalArgumentException("Invalid Wave 2 lifecycle restore context");
        }
        String version = encoded.get(PREFIX + "version");
        String savedEvent = encoded.get(PREFIX + "event");
        long savedGeneration = parseLong(encoded.get(PREFIX + "generation"));
        long savedMillis = parseLong(encoded.get(PREFIX + "saved-millis"));
        if (!"1".equals(version) || !eventId.equals(savedEvent) || savedGeneration != generation
                || savedMillis <= 0L || nowMillis < savedMillis
                || !canonicalRoster(roster).equals(encoded.get(PREFIX + "roster"))) {
            throw new IllegalArgumentException("Wave 2 lifecycle checkpoint identity mismatch");
        }
        long latestAllowedDeadline;
        try {
            latestAllowedDeadline = Math.addExact(savedMillis, graceMillis);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Wave 2 lifecycle checkpoint deadline window overflows", overflow);
        }
        Map<UUID, ParticipantState> states = new LinkedHashMap<>();
        Set<String> expectedKeys = new LinkedHashSet<>(Set.of(PREFIX + "version", PREFIX + "event",
                PREFIX + "generation", PREFIX + "saved-millis", PREFIX + "roster"));
        for (UUID owner : sorted(roster)) {
            String ownerPrefix = PREFIX + owner + ".";
            boolean active = parseBoolean(encoded.get(ownerPrefix + "active"));
            boolean alive = parseBoolean(encoded.get(ownerPrefix + "alive"));
            long deadline = parseLong(encoded.get(ownerPrefix + "offline-deadline-millis"));
            if (deadline < 0L || deadline > 0L && (!active || deadline > latestAllowedDeadline)) {
                throw new IllegalArgumentException("Invalid Wave 2 participant deadline");
            }
            states.put(owner, new ParticipantState(active, alive, deadline));
            expectedKeys.add(ownerPrefix + "active");
            expectedKeys.add(ownerPrefix + "alive");
            expectedKeys.add(ownerPrefix + "offline-deadline-millis");
        }
        Set<String> actualKeys = new LinkedHashSet<>();
        encoded.keySet().stream().filter(key -> key.startsWith(PREFIX)).forEach(actualKeys::add);
        if (!actualKeys.equals(expectedKeys)) throw new IllegalArgumentException("Unexpected Wave 2 lifecycle fields");
        return new Snapshot(savedMillis, Map.copyOf(states));
    }

    /** The expiry boundary is inclusive, so a late join cannot beat the periodic watchdog. */
    public static boolean reconnectDeadlineExpired(long deadlineMillis, long nowMillis) {
        return deadlineMillis > 0L && nowMillis >= deadlineMillis;
    }

    private static void requireContext(String eventId, long generation, Set<UUID> roster,
                                       AttemptLifecycleController lifecycle, Map<UUID, Long> deadlines,
                                       long savedMillis, long graceMillis) {
        if (eventId == null || eventId.isBlank() || generation <= 0L || roster == null || roster.isEmpty()
                || roster.size() > MAX_ROSTER_SIZE || roster.contains(null) || lifecycle == null || deadlines == null
                || savedMillis <= 0L || graceMillis < 1000L || graceMillis > 120_000L) {
            throw new IllegalArgumentException("Invalid Wave 2 lifecycle snapshot context");
        }
    }

    private static String canonicalRoster(Set<UUID> roster) {
        return String.join(",", sorted(roster).stream().map(UUID::toString).toList());
    }

    private static ArrayList<UUID> sorted(Set<UUID> roster) {
        ArrayList<UUID> sorted = new ArrayList<>(roster);
        sorted.sort(Comparator.comparing(UUID::toString));
        return sorted;
    }

    private static long parseLong(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing Wave 2 lifecycle number");
        return Long.parseLong(value);
    }

    private static boolean parseBoolean(String value) {
        if ("true".equals(value)) return true;
        if ("false".equals(value)) return false;
        throw new IllegalArgumentException("Invalid Wave 2 lifecycle boolean");
    }

    public record ParticipantState(boolean active, boolean alive, long offlineDeadlineMillis) { }
    public record Snapshot(long savedMillis, Map<UUID, ParticipantState> participants) { }
}
