package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Spec 11: Wave 4 must persist distinct obelisk HP receipts, so a restart never
 * silently heals an obelisk back to full health.
 */
public final class Wave4ObeliskRecoverySnapshot {
    public static final String PREFIX = "wave4-obelisk.";
    private static final String VERSION = "2";
    private static final int MAX_OBELISKS = 6;
    public static final long MAX_TIMER_REMAINING_TICKS = 7L * 24L * 60L * 60L * 20L;

    private Wave4ObeliskRecoverySnapshot() {
    }

    /** One obelisk's surviving state, keyed by its placement ordinal. */
    public record ObeliskReceipt(int ordinal, int health, int maxHealth, String stage,
                                 long emergenceElapsedTicks, long nextPulseRemainingTicks,
                                 long nextFireRemainingTicks, long collapseRemainingTicks) {
        public ObeliskReceipt(int ordinal, int health, int maxHealth, String stage) {
            this(ordinal, health, maxHealth, stage, defaultEmergenceElapsedTicks(stage),
                    0L, 0L, "COLLAPSING".equals(stage) ? 8L : 0L);
        }

        public ObeliskReceipt {
            if (ordinal < 0 || ordinal >= MAX_OBELISKS
                    || maxHealth < 1 || health < 0 || health > maxHealth
                    || stage == null || stage.isBlank()
                    || emergenceElapsedTicks < 0L || emergenceElapsedTicks > ObeliskGeometryPolicy.EMERGENCE_TICKS
                    || nextPulseRemainingTicks < 0L || nextPulseRemainingTicks > MAX_TIMER_REMAINING_TICKS
                    || nextFireRemainingTicks < 0L || nextFireRemainingTicks > MAX_TIMER_REMAINING_TICKS
                    || collapseRemainingTicks < 0L || collapseRemainingTicks > 8L) {
                throw new IllegalArgumentException("Wave 4 obelisk receipt is outside its bounds");
            }
        }

        private static long defaultEmergenceElapsedTicks(String stage) {
            if (stage == null) return 0L;
            return switch (stage) {
                case "EMERGE_BASE" -> 10L;
                case "EMERGE_LOWER" -> 20L;
                case "EMERGE_CORE" -> 30L;
                case "EMERGE_UPPER" -> 40L;
                case "EMERGE_CROWN" -> 50L;
                case "AWAKEN" -> 60L;
                case "ACTIVE", "COLLAPSING" -> ObeliskGeometryPolicy.EMERGENCE_TICKS;
                default -> 0L;
            };
        }
    }

    public record Data(String eventId, long generation, List<ObeliskReceipt> obelisks,
                       boolean assaultComplete, boolean mobsStarted, int pressurePackAttempts) {
        public Data {
            if (eventId == null || eventId.isBlank() || generation <= 0L
                    || obelisks == null || obelisks.size() > MAX_OBELISKS
                    || pressurePackAttempts < 0 || pressurePackAttempts > 64) {
                throw new IllegalArgumentException("Wave 4 checkpoint is outside its bounds");
            }
            obelisks = List.copyOf(obelisks);
        }

        public Map<String, String> encode() {
            Map<String, String> encoded = new LinkedHashMap<>();
            encoded.put(PREFIX + "version", VERSION);
            encoded.put(PREFIX + "event", eventId);
            encoded.put(PREFIX + "generation", Long.toString(generation));
            encoded.put(PREFIX + "count", Integer.toString(obelisks.size()));
            encoded.put(PREFIX + "assault-complete", Boolean.toString(assaultComplete));
            encoded.put(PREFIX + "mobs-started", Boolean.toString(mobsStarted));
            encoded.put(PREFIX + "pressure-pack-attempts", Integer.toString(pressurePackAttempts));
            for (int index = 0; index < obelisks.size(); index++) {
                ObeliskReceipt receipt = obelisks.get(index);
                String key = PREFIX + index + ".";
                encoded.put(key + "ordinal", Integer.toString(receipt.ordinal()));
                encoded.put(key + "health", Integer.toString(receipt.health()));
                encoded.put(key + "max-health", Integer.toString(receipt.maxHealth()));
                encoded.put(key + "stage", receipt.stage());
                encoded.put(key + "emergence-elapsed-ticks", Long.toString(receipt.emergenceElapsedTicks()));
                encoded.put(key + "next-pulse-remaining-ticks", Long.toString(receipt.nextPulseRemainingTicks()));
                encoded.put(key + "next-fire-remaining-ticks", Long.toString(receipt.nextFireRemainingTicks()));
                encoded.put(key + "collapse-remaining-ticks", Long.toString(receipt.collapseRemainingTicks()));
            }
            return Map.copyOf(encoded);
        }
    }

    /** Decode only a checkpoint bound to the already-loaded event generation. */
    public static Data decode(Map<String, String> progress, String expectedEventId,
                              long expectedGeneration) {
        Objects.requireNonNull(progress, "progress");
        if (!hasCheckpoint(progress)) {
            throw new IllegalArgumentException("Wave 4 obelisk checkpoint is missing");
        }
        String event = required(progress, "event");
        long generation = parseLong(progress, "generation");
        if (!Objects.equals(event, expectedEventId) || generation != expectedGeneration) {
            throw new IllegalArgumentException("Wave 4 obelisk checkpoint belongs to another event generation");
        }
        if (!VERSION.equals(required(progress, "version"))) {
            throw new IllegalArgumentException("Unsupported Wave 4 obelisk checkpoint version");
        }
        int count = parseInt(progress, "count");
        if (count < 0 || count > MAX_OBELISKS) {
            throw new IllegalArgumentException("Invalid Wave 4 obelisk count");
        }
        List<ObeliskReceipt> obelisks = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            String key = index + ".";
            obelisks.add(new ObeliskReceipt(parseInt(progress, key + "ordinal"),
                    parseInt(progress, key + "health"),
                    parseInt(progress, key + "max-health"),
                    required(progress, key + "stage"),
                    parseLong(progress, key + "emergence-elapsed-ticks"),
                    parseLong(progress, key + "next-pulse-remaining-ticks"),
                    parseLong(progress, key + "next-fire-remaining-ticks"),
                    parseLong(progress, key + "collapse-remaining-ticks")));
        }
        boolean assaultComplete = parseBoolean(progress, "assault-complete");
        boolean mobsStarted = parseBoolean(progress, "mobs-started");
        int pressurePackAttempts = parseInt(progress, "pressure-pack-attempts");
        return new Data(event, generation, obelisks, assaultComplete, mobsStarted, pressurePackAttempts);
    }

    public static boolean hasCheckpoint(Map<String, String> progress) {
        return progress != null && progress.keySet().stream()
                .anyMatch(key -> key != null && key.startsWith(PREFIX));
    }

    /** Keep malformed checkpoint bytes available to an administrator for recovery diagnostics. */
    public static Map<String, String> extract(Map<String, String> progress) {
        Map<String, String> extracted = new LinkedHashMap<>();
        if (progress != null) {
            progress.forEach((key, value) -> {
                if (key != null && key.startsWith(PREFIX) && value != null) {
                    extracted.put(key, value);
                }
            });
        }
        return Map.copyOf(extracted);
    }

    private static String required(Map<String, String> progress, String key) {
        String value = progress.get(PREFIX + key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Wave 4 obelisk checkpoint field is missing: " + key);
        }
        return value;
    }

    private static int parseInt(Map<String, String> progress, String key) {
        try {
            return Integer.parseInt(required(progress, key));
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Invalid Wave 4 obelisk checkpoint " + key, invalid);
        }
    }

    private static long parseLong(Map<String, String> progress, String key) {
        try {
            return Long.parseLong(required(progress, key));
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Invalid Wave 4 obelisk checkpoint " + key, invalid);
        }
    }

    private static boolean parseBoolean(Map<String, String> progress, String key) {
        String value = required(progress, key);
        if (!"true".equals(value) && !"false".equals(value)) {
            throw new IllegalArgumentException("Invalid Wave 4 obelisk checkpoint " + key);
        }
        return Boolean.parseBoolean(value);
    }
}
