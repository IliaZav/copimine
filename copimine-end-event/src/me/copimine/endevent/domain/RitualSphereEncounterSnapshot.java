package me.copimine.endevent.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Strict codec for the durable Wave 6 roster state. */
public final class RitualSphereEncounterSnapshot {
    private static final String PREFIX = "ritual-sphere.";
    private static final String GENERATION = PREFIX + "generation";
    private static final String PRISONER = PREFIX + "prisoner";
    private static final String PARTICIPANTS = PREFIX + "participants";
    private static final String CASTER_DEATHS = PREFIX + "caster-deaths";
    private static final String SPELL_CURSOR = PREFIX + "spell-cursor";
    private static final Set<String> CURRENT_KEYS = Set.of(
            GENERATION, PRISONER, PARTICIPANTS, CASTER_DEATHS, SPELL_CURSOR);
    /** Previous releases wrote these values. They are accepted and discarded during migration. */
    private static final Set<String> LEGACY_KEYS = Set.of(
            PREFIX + "successful-drains", PREFIX + "intensity", PREFIX + "last-drain-millis");

    private RitualSphereEncounterSnapshot() {
    }

    public static Map<String, String> encode(RitualSphereEncounterPolicy.State state) {
        return encode(state, 0);
    }

    public static Map<String, String> encode(RitualSphereEncounterPolicy.State state, int casterDeaths) {
        return encode(state, casterDeaths, 0);
    }

    public static Map<String, String> encode(RitualSphereEncounterPolicy.State state,
                                             int casterDeaths, int spellCursor) {
        if (state == null) {
            return Map.of();
        }
        if (casterDeaths < 0 || casterDeaths > state.profile().casterCount()) {
            throw new IllegalArgumentException("Ritual Sphere caster deaths are outside the live roster");
        }
        int persistedSpellCursor = normalizeSpellCursor(casterDeaths, spellCursor);
        Map<String, String> encoded = new LinkedHashMap<>();
        encoded.put(GENERATION, Long.toString(state.generation()));
        encoded.put(PRISONER, state.prisoner() == null ? "" : state.prisoner().toString());
        encoded.put(PARTICIPANTS, Integer.toString(state.profile().participants()));
        encoded.put(CASTER_DEATHS, Integer.toString(casterDeaths));
        encoded.put(SPELL_CURSOR, Integer.toString(persistedSpellCursor));
        return Map.copyOf(encoded);
    }

    public static Data decode(Map<String, String> encoded, long expectedGeneration) {
        if (encoded == null || encoded.keySet().stream().noneMatch(key -> key.startsWith(PREFIX))) {
            return new Data(null, false);
        }
        if (expectedGeneration <= 0L) {
            throw new IllegalArgumentException("Ritual Sphere snapshot requires a positive generation");
        }
        for (String key : encoded.keySet()) {
            if (key != null && key.startsWith(PREFIX)
                    && !CURRENT_KEYS.contains(key) && !LEGACY_KEYS.contains(key)) {
                throw new IllegalArgumentException("Ritual Sphere snapshot has unknown key: " + key);
            }
        }
        boolean migratedLegacyState = encoded.keySet().stream().anyMatch(LEGACY_KEYS::contains);
        boolean hasCurrentState = encoded.keySet().stream().anyMatch(CURRENT_KEYS::contains);
        if (!hasCurrentState) {
            return new Data(null, migratedLegacyState);
        }

        long generation = parseLong(required(encoded, GENERATION), GENERATION);
        if (generation != expectedGeneration) {
            throw new IllegalArgumentException("Ritual Sphere snapshot generation does not match event generation");
        }
        UUID prisoner = parseNullableUuid(required(encoded, PRISONER), PRISONER);
        int participants = parseInt(required(encoded, PARTICIPANTS), PARTICIPANTS);
        if (participants < RitualSphereScalingPolicy.MIN_PLAYERS
                || participants > RitualSphereScalingPolicy.MAX_PLAYERS) {
            throw new IllegalArgumentException("Ritual Sphere snapshot participant count is outside 2-20");
        }
        RitualSphereScalingPolicy.Profile profile = RitualSphereScalingPolicy.forPlayers(participants);
        // Snapshots written before caster deaths were durable are accepted as
        // zero progress only. The runtime roster gate then refuses to resume if
        // fewer than the full living roster is actually present.
        int casterDeaths = encoded.containsKey(CASTER_DEATHS)
                ? parseInt(encoded.get(CASTER_DEATHS), CASTER_DEATHS) : 0;
        if (casterDeaths < 0 || casterDeaths > profile.casterCount()) {
            throw new IllegalArgumentException("Ritual Sphere caster deaths exceed the authored roster");
        }
        int spellCursor = encoded.containsKey(SPELL_CURSOR)
                ? parseInt(encoded.get(SPELL_CURSOR), SPELL_CURSOR) : 0;
        spellCursor = normalizeSpellCursor(casterDeaths, spellCursor);
        RitualSphereEncounterPolicy.State state = new RitualSphereEncounterPolicy.State(
                generation, prisoner, profile);
        return new Data(state, migratedLegacyState, casterDeaths, spellCursor);
    }

    private static int normalizeSpellCursor(int casterDeaths, int spellCursor) {
        if (casterDeaths < 0 || casterDeaths > RitualCasterProgressionPolicy.TOTAL_CASTERS
                || spellCursor < 0) {
            throw new IllegalArgumentException("Ritual Sphere spell cursor is outside the unlocked rotation");
        }
        int rotationSize = RitualCasterProgressionPolicy.availableSpells(casterDeaths).size();
        if (rotationSize == 0) {
            return 0;
        }
        if (spellCursor >= rotationSize) {
            throw new IllegalArgumentException("Ritual Sphere spell cursor is outside the unlocked rotation");
        }
        return spellCursor;
    }

    private static String required(Map<String, String> encoded, String key) {
        String value = encoded.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Ritual Sphere snapshot is missing " + key);
        }
        return value;
    }

    private static UUID parseNullableUuid(String value, String key) {
        if (value == null || value.isBlank()) {
            return null;
        }
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

    public record Data(RitualSphereEncounterPolicy.State state, boolean migratedLegacyState,
                       int casterDeaths, int spellCursor) {
        public Data(RitualSphereEncounterPolicy.State state, boolean migratedLegacyState) {
            this(state, migratedLegacyState, 0, 0);
        }

        public Data(RitualSphereEncounterPolicy.State state, boolean migratedLegacyState,
                    int casterDeaths) {
            this(state, migratedLegacyState, casterDeaths, 0);
        }

        public Data(RitualSphereEncounterPolicy.State state) {
            this(state, false, 0, 0);
        }
    }
}
