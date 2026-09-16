package me.copimine.endevent.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Strict codec for the durable part of the Wave 6 Ritual Sphere state.
 *
 * <p>Entity UUIDs are deliberately not stored here: Bukkit persists those
 * entities and their PDC independently, while this map stores only the
 * encounter timer/intensity that must survive a plugin restart.</p>
 */
public final class RitualSphereEncounterSnapshot {
    private static final String PREFIX = "ritual-sphere.";
    private static final String GENERATION = PREFIX + "generation";
    private static final String PRISONER = PREFIX + "prisoner";
    private static final String PARTICIPANTS = PREFIX + "participants";
    private static final String DRAINS = PREFIX + "successful-drains";
    private static final String INTENSITY = PREFIX + "intensity";
    private static final String LAST_DRAIN = PREFIX + "last-drain-millis";
    private static final Set<String> KNOWN_KEYS = Set.of(
            GENERATION, PRISONER, PARTICIPANTS, DRAINS, INTENSITY, LAST_DRAIN);

    private RitualSphereEncounterSnapshot() {
    }

    public static Map<String, String> encode(RitualSphereEncounterPolicy.State state) {
        if (state == null) {
            return Map.of();
        }
        Map<String, String> encoded = new LinkedHashMap<>();
        encoded.put(GENERATION, Long.toString(state.generation()));
        encoded.put(PRISONER, state.prisoner().toString());
        encoded.put(PARTICIPANTS, Integer.toString(state.profile().participants()));
        encoded.put(DRAINS, Integer.toString(state.successfulDrains()));
        encoded.put(INTENSITY, Integer.toString(state.intensity()));
        encoded.put(LAST_DRAIN, Long.toString(state.lastDrainMillis()));
        return Map.copyOf(encoded);
    }

    public static Data decode(Map<String, String> encoded, long expectedGeneration) {
        if (encoded == null || encoded.keySet().stream().noneMatch(key -> key.startsWith(PREFIX))) {
            return new Data(null);
        }
        if (expectedGeneration <= 0L) {
            throw new IllegalArgumentException("Ritual Sphere snapshot requires a positive generation");
        }
        for (String key : encoded.keySet()) {
            if (key != null && key.startsWith(PREFIX) && !KNOWN_KEYS.contains(key)) {
                throw new IllegalArgumentException("Ritual Sphere snapshot has unknown key: " + key);
            }
        }
        long generation = parseLong(required(encoded, GENERATION), GENERATION);
        if (generation != expectedGeneration) {
            throw new IllegalArgumentException("Ritual Sphere snapshot generation does not match event generation");
        }
        UUID prisoner = parseUuid(required(encoded, PRISONER), PRISONER);
        int participants = parseInt(required(encoded, PARTICIPANTS), PARTICIPANTS);
        int drains = parseInt(required(encoded, DRAINS), DRAINS);
        int intensity = parseInt(required(encoded, INTENSITY), INTENSITY);
        long lastDrain = parseLong(required(encoded, LAST_DRAIN), LAST_DRAIN);
        if (participants < RitualSphereScalingPolicy.MIN_PLAYERS
                || participants > RitualSphereScalingPolicy.MAX_PLAYERS) {
            throw new IllegalArgumentException("Ritual Sphere snapshot participant count is outside 2-20");
        }
        if (drains < 0 || drains > RitualSphereScalingPolicy.MAX_INTENSITY) {
            throw new IllegalArgumentException("Ritual Sphere snapshot drain count is outside its bound");
        }
        if (intensity != RitualSphereScalingPolicy.intensityForSuccessfulDrains(drains)) {
            throw new IllegalArgumentException("Ritual Sphere snapshot intensity does not match drains");
        }
        RitualSphereEncounterPolicy.State state = new RitualSphereEncounterPolicy.State(
                generation, prisoner, RitualSphereScalingPolicy.forPlayers(participants),
                drains, intensity, lastDrain);
        return new Data(state);
    }

    private static String required(Map<String, String> encoded, String key) {
        String value = encoded.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Ritual Sphere snapshot is missing " + key);
        }
        return value;
    }

    private static UUID parseUuid(String value, String key) {
        try {
            return UUID.fromString(value.trim());
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Ritual Sphere snapshot has invalid UUID in " + key, error);
        }
    }

    private static int parseInt(String value, String key) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Ritual Sphere snapshot has invalid integer in " + key, error);
        }
    }

    private static long parseLong(String value, String key) {
        try {
            return Long.parseLong(value.trim());
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Ritual Sphere snapshot has invalid long in " + key, error);
        }
    }

    public record Data(RitualSphereEncounterPolicy.State state) {
    }
}
