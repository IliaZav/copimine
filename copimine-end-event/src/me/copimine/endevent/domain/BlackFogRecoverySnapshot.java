package me.copimine.endevent.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Durable Wave 5 stage data retained when a server restart interrupts its live combat. */
public final class BlackFogRecoverySnapshot {
    public static final String PREFIX = "wave5-fog.";
    private static final String VERSION = "1";

    private BlackFogRecoverySnapshot() { }

    public enum Stage {
        TELEGRAPH,
        SAFE,
        FOG,
        COMPLETE
    }

    public record Data(String eventId, long generation, int cycle, Stage stage,
                       long phaseDeadlineMillis, int zoneCount, int zoneSide,
                       int setupAttempts, long setupRetryAtMillis) {
        public Data {
            if (eventId == null || eventId.isBlank() || generation <= 0L
                    || cycle < 0 || cycle > BlackFogTimingPolicy.CYCLE_COUNT
                    || stage == null || phaseDeadlineMillis < 0L
                    || zoneCount < 0 || zoneCount > 20
                    || zoneSide < 0 || zoneSide > 3
                    || setupAttempts < 0 || setupAttempts > 3 || setupRetryAtMillis < 0L) {
                throw new IllegalArgumentException("Wave 5 recovery checkpoint is outside its bounds");
            }
            if ((stage == Stage.SAFE || stage == Stage.FOG)
                    && (cycle >= BlackFogTimingPolicy.CYCLE_COUNT || zoneCount < 1 || zoneSide < 1)) {
                throw new IllegalArgumentException("active Wave 5 safe/fog stage requires its live zone geometry");
            }
            if (stage == Stage.COMPLETE
                    && (cycle != BlackFogTimingPolicy.CYCLE_COUNT || phaseDeadlineMillis != 0L)) {
                throw new IllegalArgumentException("completed Wave 5 checkpoint must have three cycles and no deadline");
            }
            if (stage != Stage.COMPLETE && cycle >= BlackFogTimingPolicy.CYCLE_COUNT) {
                throw new IllegalArgumentException("active Wave 5 checkpoint cannot exceed the cycle count");
            }
        }

        public Map<String, String> encode() {
            Map<String, String> encoded = new LinkedHashMap<>();
            encoded.put(PREFIX + "version", VERSION);
            encoded.put(PREFIX + "event", eventId);
            encoded.put(PREFIX + "generation", Long.toString(generation));
            encoded.put(PREFIX + "cycle", Integer.toString(cycle));
            encoded.put(PREFIX + "stage", stage.name());
            encoded.put(PREFIX + "deadline-millis", Long.toString(phaseDeadlineMillis));
            encoded.put(PREFIX + "zone-count", Integer.toString(zoneCount));
            encoded.put(PREFIX + "zone-side", Integer.toString(zoneSide));
            encoded.put(PREFIX + "setup-attempts", Integer.toString(setupAttempts));
            encoded.put(PREFIX + "retry-at-millis", Long.toString(setupRetryAtMillis));
            return Map.copyOf(encoded);
        }
    }

    /** Decode only a checkpoint bound to the already-loaded event generation. */
    public static Data decode(Map<String, String> progress, String expectedEventId,
                              long expectedGeneration) {
        Objects.requireNonNull(progress, "progress");
        if (!hasCheckpoint(progress)) {
            throw new IllegalArgumentException("Wave 5 recovery checkpoint is missing");
        }
        String event = required(progress, "event");
        long generation = parseLong(progress, "generation");
        if (!Objects.equals(event, expectedEventId) || generation != expectedGeneration) {
            throw new IllegalArgumentException("Wave 5 recovery checkpoint belongs to another event generation");
        }
        if (!VERSION.equals(required(progress, "version"))) {
            throw new IllegalArgumentException("Unsupported Wave 5 recovery checkpoint version");
        }
        Stage stage;
        try {
            stage = Stage.valueOf(required(progress, "stage"));
        } catch (IllegalArgumentException invalidStage) {
            throw new IllegalArgumentException("Invalid Wave 5 recovery checkpoint stage", invalidStage);
        }
        return new Data(event, generation, parseInt(progress, "cycle"), stage,
                parseLong(progress, "deadline-millis"), parseInt(progress, "zone-count"),
                parseInt(progress, "zone-side"), parseInt(progress, "setup-attempts"),
                parseLong(progress, "retry-at-millis"));
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
