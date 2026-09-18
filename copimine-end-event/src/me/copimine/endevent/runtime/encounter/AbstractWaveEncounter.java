package me.copimine.endevent.runtime.encounter;

import java.util.Objects;
import me.copimine.endevent.domain.EndRiftObjective;
import me.copimine.endevent.runtime.EncounterContext;

/** Shared generation/idempotency guard for the seven small encounter adapters. */
abstract class AbstractWaveEncounter implements WaveEncounter {
    private final EndRiftObjective.Objective objective;
    private final int wave;
    private final int defaultRequired;
    private boolean started;
    private boolean completed;
    private String eventId = "";
    private long generation;
    private int progress;
    protected int required;

    AbstractWaveEncounter(EndRiftObjective.Objective objective, int wave, int required) {
        this.objective = Objects.requireNonNull(objective, "objective");
        if (wave < 1 || wave > EndRiftObjective.MAX_WAVE) throw new IllegalArgumentException("invalid wave");
        if (required < 1) throw new IllegalArgumentException("required progress must be positive");
        this.wave = wave;
        this.defaultRequired = required;
        this.required = required;
    }

    @Override public final EndRiftObjective.Objective objective() { return objective; }
    @Override public final int wave() { return wave; }
    @Override public final synchronized boolean started() { return started; }
    @Override public final synchronized boolean completed() { return completed; }
    @Override public final synchronized long generation() { return generation; }
    public final synchronized int progress() { return progress; }
    public final synchronized int required() { return required; }

    @Override
    public synchronized Result start(EncounterContext context) {
        if (!contextMatches(context)) return rejected("CONTEXT_MISMATCH");
        if (started) return new Result(completed ? Status.COMPLETE : Status.ALREADY_STARTED,
                progress, required, "already started");
        required = requiredFor(context);
        if (required < 1) return rejected("NO_LIVING_PARTICIPANTS");
        started = true;
        eventId = context.eventId();
        generation = context.generation();
        return new Result(Status.STARTED, progress, required, "objective started");
    }

    @Override
    public synchronized Result tick(EncounterContext context) {
        if (!accepts(context)) return rejected("STALE_OR_NOT_STARTED");
        return completed ? result(Status.COMPLETE, "objective complete")
                : result(Status.IN_PROGRESS, "objective active");
    }

    @Override
    public synchronized Result complete(EncounterContext context) {
        if (!accepts(context)) return rejected("STALE_OR_NOT_STARTED");
        if (progress < required) return result(Status.IN_PROGRESS, "required progress missing");
        completed = true;
        return result(Status.COMPLETE, "objective complete");
    }

    @Override
    public synchronized void reset() {
        started = false;
        completed = false;
        eventId = "";
        generation = 0L;
        progress = 0;
        required = defaultRequired;
        onReset();
    }

    protected int requiredFor(EncounterContext context) { return defaultRequired; }
    protected void onReset() { }

    protected final synchronized boolean accepts(EncounterContext context) {
        return started && !completed && contextMatches(context)
                && context.owns(eventId, generation);
    }

    protected final synchronized boolean acceptsCompleted(EncounterContext context) {
        return started && contextMatches(context) && context.owns(eventId, generation);
    }

    protected final synchronized boolean participantIsValid(EncounterContext context,
                                                            java.util.UUID player) {
        return accepts(context) && context.isLivingParticipant(player);
    }

    protected final synchronized Result addProgress(EncounterContext context, int delta,
                                                    String reason) {
        if (!accepts(context) || delta < 1) return rejected(delta < 1 ? "INVALID_PROGRESS" : "STALE_OR_COMPLETE");
        progress = Math.min(required, progress + delta);
        // Progress reaches the threshold first. The coordinator is the only
        // owner allowed to commit the terminal state and advance the graph;
        // this prevents the last objective callback from racing its cleanup
        // and makes completeWave exactly once.
        return result(Status.IN_PROGRESS, reason);
    }

    protected final synchronized Result result(Status status, String reason) {
        return new Result(status, progress, required, reason == null ? "" : reason);
    }

    protected final synchronized Result rejected(String reason) {
        return new Result(Status.REJECTED, progress, required, reason == null ? "" : reason);
    }

    private boolean contextMatches(EncounterContext context) {
        return context != null && context.objective() == objective;
    }
}
