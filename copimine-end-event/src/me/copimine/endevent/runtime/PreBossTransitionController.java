package me.copimine.endevent.runtime;

/** Generation-scoped, single-fire timer for the Wave 7 to boss handoff. */
public final class PreBossTransitionController {
    public static final long DURATION_MILLIS = 40_000L;

    public enum Status {
        NOT_STARTED,
        WAITING,
        HANDOFF_STARTED,
        ALREADY_STARTED,
        STALE_CONTEXT
    }

    public record TickResult(Status status, long elapsedMillis, long remainingMillis) {
        public TickResult {
            status = status == null ? Status.NOT_STARTED : status;
            elapsedMillis = Math.max(0L, elapsedMillis);
            remainingMillis = Math.max(0L, remainingMillis);
        }
    }

    private String eventId = "";
    private long generation = Long.MIN_VALUE;
    private long startedAtMillis;
    private boolean started;
    private boolean handoffStarted;

    /** A repeated begin for the same attempt preserves its original deadline. */
    public synchronized void start(EncounterContext context, long nowMillis) {
        if (context == null) throw new IllegalArgumentException("encounter context is required");
        if (started && context.owns(eventId, generation)) return;
        eventId = context.eventId();
        generation = context.generation();
        startedAtMillis = Math.max(0L, nowMillis);
        started = true;
        handoffStarted = false;
    }

    public synchronized TickResult tick(EncounterContext context, long nowMillis,
                                        BossStartGateway gateway) {
        if (!started) return new TickResult(Status.NOT_STARTED, 0L, DURATION_MILLIS);
        if (context == null || !context.owns(eventId, generation)) {
            return new TickResult(Status.STALE_CONTEXT, 0L, DURATION_MILLIS);
        }
        long elapsed = nowMillis <= startedAtMillis ? 0L
                : Math.min(DURATION_MILLIS, nowMillis - startedAtMillis);
        long remaining = DURATION_MILLIS - elapsed;
        if (handoffStarted) return new TickResult(Status.ALREADY_STARTED, elapsed, remaining);
        if (remaining > 0L) return new TickResult(Status.WAITING, elapsed, remaining);
        if (gateway == null) throw new IllegalArgumentException("boss start gateway is required");

        // Mark before invoking external gameplay code so a thrown callback is
        // visible and cannot create a duplicate boss on the next server tick.
        handoffStarted = true;
        gateway.start(context);
        return new TickResult(Status.HANDOFF_STARTED, elapsed, 0L);
    }

    public synchronized boolean isStartedFor(EncounterContext context) {
        return started && context != null && context.owns(eventId, generation);
    }

    public synchronized long remainingMillis(long nowMillis) {
        if (!started || handoffStarted) return 0L;
        long elapsed = nowMillis <= startedAtMillis ? 0L : nowMillis - startedAtMillis;
        return Math.max(0L, DURATION_MILLIS - elapsed);
    }

    public synchronized void reset() {
        eventId = "";
        generation = Long.MIN_VALUE;
        startedAtMillis = 0L;
        started = false;
        handoffStarted = false;
    }
}
