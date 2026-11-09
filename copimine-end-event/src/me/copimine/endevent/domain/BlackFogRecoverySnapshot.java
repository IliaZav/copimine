package me.copimine.endevent.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Durable Wave 5 stage data retained when a server restart interrupts its live combat. */
public final class BlackFogRecoverySnapshot {
    public static final String PREFIX = "wave5-fog.";
    private static final String VERSION = "3";

    private BlackFogRecoverySnapshot() { }

    public enum Stage {
        TELEGRAPH,
        SAFE,
        FOG,
        COMPLETE
    }

    public record Data(String eventId, long generation, int cycle, Stage stage,
                       long phaseDeadlineMillis, int zoneCount, int zoneSide,
                       int setupAttempts, long setupRetryAtMillis, int packsSpawned, int targetCycles) {
        /** Compatibility constructor for tools; new runtime always supplies its frozen target. */
        public Data(String eventId, long generation, int cycle, Stage stage,
                    long phaseDeadlineMillis, int zoneCount, int zoneSide,
                    int setupAttempts, long setupRetryAtMillis, int packsSpawned) {
            this(eventId, generation, cycle, stage, phaseDeadlineMillis, zoneCount, zoneSide,
                    setupAttempts, setupRetryAtMillis, packsSpawned, BlackFogTimingPolicy.CYCLE_COUNT);
        }
        public Data {
            if (eventId == null || eventId.isBlank() || generation <= 0L
                    || targetCycles < BlackFogTimingPolicy.MIN_CYCLES
                    || targetCycles > BlackFogTimingPolicy.CYCLE_COUNT
                    || cycle < 0 || cycle > targetCycles
                    || stage == null || phaseDeadlineMillis < 0L
                    || zoneCount < 0 || zoneCount > 20
                    || zoneSide < 0 || zoneSide > 3
                    || setupAttempts < 0 || setupAttempts > 3 || setupRetryAtMillis < 0L
                    // Wave 5 owes exactly one pack per completed fog, so the
                    // count can never exceed the cycle count; a receipt claiming
                    // more would silently satisfy a debt that is still owed.
                    || packsSpawned < 0 || packsSpawned > cycle) {
                throw new IllegalArgumentException("Wave 5 recovery checkpoint is outside its bounds");
            }
            if ((stage == Stage.SAFE || stage == Stage.FOG)
                    && (cycle >= targetCycles || zoneCount < 1 || zoneSide < 1)) {
                throw new IllegalArgumentException("active Wave 5 safe/fog stage requires its live zone geometry");
            }
            if (stage == Stage.COMPLETE
                    && (cycle != targetCycles || phaseDeadlineMillis != 0L)) {
                throw new IllegalArgumentException("completed Wave 5 checkpoint must reach its frozen target with no deadline");
            }
            if (stage != Stage.COMPLETE && cycle >= targetCycles) {
                throw new IllegalArgumentException("active Wave 5 checkpoint cannot exceed the cycle count");
            }
        }

        public Map<String, String> encode() {
            Map<String, String> encoded = new LinkedHashMap<>();
            encoded.put(PREFIX + "version", VERSION);
            encoded.put(PREFIX + "target-cycles", Integer.toString(targetCycles));
            encoded.put(PREFIX + "event", eventId);
            encoded.put(PREFIX + "generation", Long.toString(generation));
            encoded.put(PREFIX + "cycle", Integer.toString(cycle));
            encoded.put(PREFIX + "stage", stage.name());
            encoded.put(PREFIX + "deadline-millis", Long.toString(phaseDeadlineMillis));
            encoded.put(PREFIX + "zone-count", Integer.toString(zoneCount));
            encoded.put(PREFIX + "zone-side", Integer.toString(zoneSide));
            encoded.put(PREFIX + "setup-attempts", Integer.toString(setupAttempts));
            encoded.put(PREFIX + "retry-at-millis", Long.toString(setupRetryAtMillis));
            // W5-004: the post-fog packs are mandatory pressure, and a restart
            // used to forget how many of them had already been released — the
            // wave then either waited forever for a pack it had already spawned
            // or spawned a second one.
            encoded.put(PREFIX + "packs-spawned", Integer.toString(packsSpawned));
            return Map.copyOf(encoded);
        }
    }

    /** Decode only a checkpoint bound to the already-loaded event generation. */
    public static Data decode(Map<String, String> progress, String expectedEventId,
                              long expectedGeneration) {
        return decode(progress, expectedEventId, expectedGeneration, 0);
    }

    /**
     * Decode a checkpoint bound to one event generation AND one roster-frozen
     * cycle target.
     *
     * <p>Point 12 of the November 9 brief: an interrupted decision must not be
     * replayed as a different one on resume. A Wave 5 checkpoint carries the
     * cycle target it was frozen at, but a party that reconnects with a
     * different roster legitimately asks for a different target. Accepting such
     * a checkpoint would silently reinterpret a five-cycle fight that was
     * already half played as a ten-cycle fight (or the reverse), exactly the
     * "old checkpoint silently becomes a new mode" failure the brief names.</p>
     *
     * <p>{@code expectedTargetCycles <= 0} means "the caller has no frozen
     * opinion yet" and keeps the stored target; any positive value must match.</p>
     */
    public static Data decode(Map<String, String> progress, String expectedEventId,
                              long expectedGeneration, int expectedTargetCycles) {
        Objects.requireNonNull(progress, "progress");
        if (!hasCheckpoint(progress)) {
            throw new IllegalArgumentException("Wave 5 recovery checkpoint is missing");
        }
        String event = required(progress, "event");
        long generation = parseLong(progress, "generation");
        if (!Objects.equals(event, expectedEventId) || generation != expectedGeneration) {
            throw new IllegalArgumentException("Wave 5 recovery checkpoint belongs to another event generation");
        }
        String version = required(progress, "version");
        // Old three/five-cycle layouts must not silently become ten-cycle fights.
        // The caller retains raw checkpoint bytes and enters explicit recovery.
        if (!VERSION.equals(version)) {
            throw new IllegalArgumentException("Unsupported Wave 5 recovery checkpoint version");
        }
        Stage stage;
        try {
            stage = Stage.valueOf(required(progress, "stage"));
        } catch (IllegalArgumentException invalidStage) {
            throw new IllegalArgumentException("Invalid Wave 5 recovery checkpoint stage", invalidStage);
        }
        int packsSpawned = parseInt(progress, "packs-spawned");
        int targetCycles = parseInt(progress, "target-cycles");
        if (expectedTargetCycles > 0 && expectedTargetCycles != targetCycles) {
            // Fence BEFORE the Data constructor runs, so the caller cannot catch a
            // half-built snapshot: the record's own bounds already accept a
            // five-cycle layout, and only this comparison knows the live target.
            throw new IllegalArgumentException(
                    "Wave 5 recovery checkpoint was frozen for a different cycle target");
        }
        return new Data(event, generation, parseInt(progress, "cycle"), stage,
                parseLong(progress, "deadline-millis"), parseInt(progress, "zone-count"),
                parseInt(progress, "zone-side"), parseInt(progress, "setup-attempts"),
                parseLong(progress, "retry-at-millis"), packsSpawned, targetCycles);
    }

    public static boolean hasCheckpoint(Map<String, String> progress) {
        return progress != null && progress.keySet().stream().anyMatch(key -> key.startsWith(PREFIX));
    }

    /** Keep malformed checkpoint bytes available to an administrator for recovery diagnostics. */
    public static Map<String, String> extract(Map<String, String> progress) {
        Map<String, String> extracted = new LinkedHashMap<>();
        if (progress != null) {
            progress.forEach((key, value) -> {
                if (key != null && key.startsWith(PREFIX) && value != null) extracted.put(key, value);
            });
        }
        return Map.copyOf(extracted);
    }

    /** Wave 5 combat mobs are transient, so no saved stage may restart as a live fight. */
    public static boolean requiresAdminRecovery(Data checkpoint) {
        return checkpoint != null;
    }

    private static String required(Map<String, String> progress, String key) {
        String value = progress.get(PREFIX + key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Wave 5 recovery checkpoint is missing " + key);
        }
        return value;
    }

    private static int parseInt(Map<String, String> progress, String key) {
        try {
            return Integer.parseInt(required(progress, key));
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Invalid Wave 5 recovery checkpoint " + key, invalid);
        }
    }

    private static long parseLong(Map<String, String> progress, String key) {
        try {
            return Long.parseLong(required(progress, key));
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Invalid Wave 5 recovery checkpoint " + key, invalid);
        }
    }
}
