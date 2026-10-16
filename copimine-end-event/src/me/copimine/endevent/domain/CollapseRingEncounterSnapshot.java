package me.copimine.endevent.domain;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Comparator;
import java.util.Set;
import java.util.Map;
import java.util.UUID;

/**
 * Strict, schema-neutral encoding for the current Wave 6 encounter.
 *
 * <p>The event snapshot already provides a durable string map for objective
 * state. Keeping the codec beside the policy prevents the Bukkit adapter from
 * inventing a second representation and makes restart/recovery testable
 * without a running server.</p>
 */
public final class CollapseRingEncounterSnapshot {
    public static final int MAX_COMPLETED_RINGS = 3;

    private static final String COMPLETED = "collapse-rings.completed";
    private static final String STATE_PREFIX = "collapse-rings.state.";
    private static final String GENERATION = STATE_PREFIX + "generation";
    private static final String ROOM = STATE_PREFIX + "room";
    private static final String GUARD_A = STATE_PREFIX + "guard-a";
    private static final String GUARD_B = STATE_PREFIX + "guard-b";
    private static final String FIRST_DOWN = STATE_PREFIX + "first-down";
    private static final String FIRST_DOWN_TICK = STATE_PREFIX + "first-down-tick";
    private static final String DEADLINE = STATE_PREFIX + "deadline";
    private static final String REVIVE_FRACTION = STATE_PREFIX + "revive-fraction";
    private static final String PHASE = STATE_PREFIX + "phase";
    private static final String ROTATION_START = STATE_PREFIX + "rotation-start";
    private static final String PLAYERS = STATE_PREFIX + "players";

    private CollapseRingEncounterSnapshot() {
    }

    public static Map<String, String> encode(int completedRings,
                                              CollapseRingEncounterPolicy.State state) {
        requireCompleted(completedRings);
        Map<String, String> encoded = new LinkedHashMap<>();
        encoded.put(COMPLETED, Integer.toString(completedRings));
        if (state == null) {
            return Map.copyOf(encoded);
        }
        encoded.put(GENERATION, Long.toString(state.generation()));
        encoded.put(ROOM, Integer.toString(state.roomId()));
        encoded.put(GUARD_A, state.guardA().toString());
        encoded.put(GUARD_B, state.guardB().toString());
        encoded.put(FIRST_DOWN, state.firstDownGuardId() == null
                ? "" : state.firstDownGuardId().toString());
        encoded.put(FIRST_DOWN_TICK, Long.toString(state.firstDownTick()));
        encoded.put(DEADLINE, Long.toString(state.killWindowDeadlineTick()));
        encoded.put(REVIVE_FRACTION, Double.toString(state.reviveHealthFraction()));
        encoded.put(PHASE, state.phase().name());
        encoded.put(ROTATION_START, Long.toString(state.rotationStartedTick()));
        encoded.put(PLAYERS, state.assignedPlayers().stream()
                .sorted(Comparator.comparing(UUID::toString))
                .map(UUID::toString)
                .collect(java.util.stream.Collectors.joining(",")));
        return Map.copyOf(encoded);
    }

    public static Data decode(Map<String, String> encoded, long expectedGeneration) {
        if (encoded == null || encoded.isEmpty()) {
            return new Data(0, null);
        }
        int completed = parseInt(encoded.getOrDefault(COMPLETED, "0"), COMPLETED);
        requireCompleted(completed);
        boolean hasState = encoded.keySet().stream().anyMatch(key -> key.startsWith(STATE_PREFIX));
        if (!hasState) {
            return new Data(completed, null);
        }
        if (expectedGeneration <= 0L) {
            throw new IllegalArgumentException("Wave 6 snapshot requires a positive generation");
        }
        long generation = parseLong(required(encoded, GENERATION), GENERATION);
        if (generation != expectedGeneration) {
            throw new IllegalArgumentException("Wave 6 snapshot generation does not match event generation");
        }
        UUID guardA = parseUuid(required(encoded, GUARD_A), GUARD_A);
        UUID guardB = parseUuid(required(encoded, GUARD_B), GUARD_B);
        UUID firstDown = parseNullableUuid(required(encoded, FIRST_DOWN), FIRST_DOWN);
        CollapseRingEncounterPolicy.Phase phase = parsePhase(required(encoded, PHASE));
        Set<UUID> assignedPlayers = parsePlayers(required(encoded, PLAYERS));
        CollapseRingEncounterPolicy.State state = new CollapseRingEncounterPolicy.State(
                generation,
                parseInt(required(encoded, ROOM), ROOM),
                guardA,
                guardB,
                firstDown,
                parseLong(required(encoded, FIRST_DOWN_TICK), FIRST_DOWN_TICK),
                parseLong(required(encoded, DEADLINE), DEADLINE),
                parseDouble(required(encoded, REVIVE_FRACTION), REVIVE_FRACTION),
                phase,
                parseLong(required(encoded, ROTATION_START), ROTATION_START), assignedPlayers);
        return new Data(completed, state);
    }

    private static Set<UUID> parsePlayers(String raw) {
        Set<UUID> result = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        for (String item : raw.split(",", -1)) {
            if (item.isBlank()) {
                throw new IllegalArgumentException("Wave 6 snapshot has an empty player id");
            }
            UUID player = parseUuid(item, PLAYERS);
            if (!result.add(player)) {
                throw new IllegalArgumentException("Wave 6 snapshot has a duplicate player id");
            }
        }
        return Set.copyOf(result);
    }

    private static String required(Map<String, String> encoded, String key) {
        String value = encoded.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Wave 6 snapshot is missing " + key);
        }
        return value;
    }

    private static CollapseRingEncounterPolicy.Phase parsePhase(String value) {
        try {
            return CollapseRingEncounterPolicy.Phase.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Wave 6 snapshot has invalid phase", error);
        }
    }

    private static UUID parseUuid(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Wave 6 snapshot is missing " + key);
        }
        return parseUuidValue(value, key);
    }

    private static UUID parseNullableUuid(String value, String key) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseUuidValue(value, key);
    }

    private static UUID parseUuidValue(String value, String key) {
        try {
            return UUID.fromString(value.trim());
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Wave 6 snapshot has invalid UUID in " + key, error);
        }
    }

    private static int parseInt(String value, String key) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Wave 6 snapshot has invalid integer in " + key, error);
        }
    }

    private static long parseLong(String value, String key) {
        try {
            return Long.parseLong(value.trim());
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Wave 6 snapshot has invalid long in " + key, error);
        }
    }

    private static double parseDouble(String value, String key) {
        try {
            return Double.parseDouble(value.trim());
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Wave 6 snapshot has invalid number in " + key, error);
        }
    }

    private static void requireCompleted(int completedRings) {
        if (completedRings < 0 || completedRings > MAX_COMPLETED_RINGS) {
            throw new IllegalArgumentException("Wave 6 completed ring count is outside 0..3");
        }
    }

    public record Data(int completedRings, CollapseRingEncounterPolicy.State encounter) {
        public Data {
            requireCompleted(completedRings);
        }
    }
}
