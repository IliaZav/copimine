package me.copimine.endevent.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Generation-scoped active-time cadence; no actor lifetime or shared cast slot. */
public final class SmallKaguneCadence {
    private static final String PREFIX = "boss-small-kagune.";
    private String event;
    private long generation = Long.MIN_VALUE, remainingNanos = -1L, lastNanos;
    private int step;
    private boolean stopped;

    public int poll(String event, long generation, long nowNanos, boolean combatActive,
                    boolean cocoonBroken, int admittedPlayers, int living) {
        if (event == null || event.isBlank() || generation <= 0L) { pause(); return 0; }
        if (!Objects.equals(this.event, event) || this.generation != generation) {
            this.event = event; this.generation = generation;
            remainingNanos = -1L; lastNanos = 0L; step = 0; stopped = false;
        }
        if (cocoonBroken) stopped = true;
        if (!combatActive || stopped || admittedPlayers < 1) { pause(); return 0; }
        if (remainingNanos < 0L) remainingNanos = intervalNanos();
        if (lastNanos != 0L && nowNanos > lastNanos) {
            // A stalled tick cannot enqueue several catch-up batches at once.
            remainingNanos -= Math.min(1_000_000_000L, nowNanos - lastNanos);
        }
        lastNanos = nowNanos;
        if (remainingNanos > 0L) return 0;
        step = step == Integer.MAX_VALUE ? 0 : step + 1;
        remainingNanos = intervalNanos();
        return SmallKaguneSpawnPolicy.admission(admittedPlayers, living);
    }

    private long intervalNanos() {
        long seed = generation ^ ((long) event.hashCode() << 32);
        return SmallKaguneSpawnPolicy.intervalMillis(step, seed) * 1_000_000L;
    }

    public void pause() { lastNanos = 0L; }

    public Map<String, String> snapshot(String event, long generation) {
        if (!Objects.equals(this.event, event) || this.generation != generation) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        result.put(PREFIX + "schema", "1"); result.put(PREFIX + "event", event);
        result.put(PREFIX + "generation", Long.toString(generation));
        result.put(PREFIX + "step", Integer.toString(step));
        result.put(PREFIX + "remaining-nanos", Long.toString(remainingNanos));
        result.put(PREFIX + "stopped", Boolean.toString(stopped));
        return Map.copyOf(result);
    }

    public void restore(Map<String, String> saved, String expectedEvent, long expectedGeneration) {
        if (saved == null || saved.keySet().stream().noneMatch(key -> key.startsWith(PREFIX))) return;
        try {
            if (!"1".equals(saved.get(PREFIX + "schema")) || expectedGeneration <= 0L
                    || expectedEvent == null || expectedEvent.isBlank()
                    || !expectedEvent.equals(saved.get(PREFIX + "event"))
                    || expectedGeneration != Long.parseLong(saved.get(PREFIX + "generation")))
                throw new IllegalArgumentException("scope");
            int restoredStep = Integer.parseInt(saved.get(PREFIX + "step"));
            long remaining = Long.parseLong(saved.get(PREFIX + "remaining-nanos"));
            String stop = saved.get(PREFIX + "stopped");
            if (restoredStep < 0 || remaining < -1L || restoredStep > 0 && remaining < 0L
                    || remaining > SmallKaguneSpawnPolicy.SPAWN_MAX_INTERVAL_MILLIS * 1_000_000L
                    || !"true".equals(stop) && !"false".equals(stop))
                throw new IllegalArgumentException("state");
            event = expectedEvent; generation = expectedGeneration; step = restoredStep;
            remainingNanos = remaining; stopped = Boolean.parseBoolean(stop); pause();
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("INVALID_SMALL_KAGUNE_CADENCE", invalid);
        }
    }
}
