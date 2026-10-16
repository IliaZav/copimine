package me.copimine.endevent.domain;

import java.util.Map;

/** A saved tick clock belongs only to the exact attempt which created it. */
public final class PreBossTickSnapshotPolicy {
    private PreBossTickSnapshotPolicy() { }

    public static Map<String, String> encode(String eventId, long generation, long elapsedTicks) {
        if (eventId == null || eventId.isBlank() || generation <= 0L || elapsedTicks < 0L || elapsedTicks > 800L) {
            throw new IllegalArgumentException("invalid pre-boss tick snapshot");
        }
        return Map.of("pre-boss-clock", "1", "pre-boss-event", eventId,
                "pre-boss-generation", Long.toString(generation), "pre-boss-elapsed-ticks", Long.toString(elapsedTicks));
    }

    public static long decode(Map<String, String> values, String eventId, long generation) {
        if (values == null || values.keySet().stream().noneMatch(key -> key.startsWith("pre-boss-"))) return 0L;
        try {
            if (values.containsKey("pre-boss-handoff")) {
                throw new IllegalArgumentException("interrupted pre-boss handoff requires reconciliation");
            }
            if (!"1".equals(values.get("pre-boss-clock")) || !eventId.equals(values.get("pre-boss-event"))
                    || generation != Long.parseLong(values.get("pre-boss-generation"))) {
                throw new IllegalArgumentException("stale pre-boss tick snapshot");
            }
            long elapsed = Long.parseLong(values.get("pre-boss-elapsed-ticks"));
            encode(eventId, generation, elapsed);
            return elapsed;
        } catch (NullPointerException exception) {
            throw new IllegalArgumentException("incomplete pre-boss tick snapshot", exception);
        }
    }
}
