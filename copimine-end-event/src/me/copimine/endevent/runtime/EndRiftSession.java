package me.copimine.endevent.runtime;

import me.copimine.endevent.domain.EndEventStateMachine;
import me.copimine.endevent.domain.EventPhase;

/** One generation-fenced session and its single cleanup boundary. */
public final class EndRiftSession implements AutoCloseable {
    private final EndRiftEncounterController encounterController;
    private final AutoCloseable resourceScope;
    private boolean closed;
    private RuntimeException cleanupFailure;

    public EndRiftSession(EncounterContext context, EventPhase initialPhase,
                          AutoCloseable resourceScope) {
        if (context == null) throw new IllegalArgumentException("context is required");
        this.encounterController = new EndRiftEncounterController(context, initialPhase);
        this.resourceScope = resourceScope;
    }

    public synchronized EncounterContext context() { return encounterController.context(); }
    public synchronized EventPhase phase() { return encounterController.phase(); }
    public synchronized long generation() { return encounterController.generation(); }
    public synchronized String eventId() { return encounterController.eventId(); }
    public synchronized boolean closed() { return closed; }
    public synchronized RuntimeException cleanupFailure() { return cleanupFailure; }

    public synchronized boolean accepts(String eventId, long generation) {
        return !closed && encounterController.accepts(eventId, generation);
    }

    public synchronized TransitionOutcome transition(EventPhase expected, EventPhase next,
                                                      String reason, String idempotencyKey) {
        if (closed) return TransitionOutcome.rejected(phase(), "SESSION_CLOSED");
        EndEventStateMachine.TransitionResult result = encounterController.transition(
                expected, next, reason, idempotencyKey);
        if (!result.success()) return TransitionOutcome.rejected(phase(), result.code());
        return new TransitionOutcome(true, next, result.code(), result.reason(), result.idempotencyKey());
    }

    public synchronized TransitionOutcome previewTransition(EventPhase expected, EventPhase next,
                                                             String reason, String idempotencyKey) {
        if (closed) return TransitionOutcome.rejected(phase(), "SESSION_CLOSED");
        EndEventStateMachine.TransitionResult result = encounterController.previewTransition(
                expected, next, reason, idempotencyKey);
        if (!result.success()) return TransitionOutcome.rejected(phase(), result.code());
        return new TransitionOutcome(true, next, result.code(), result.reason(), result.idempotencyKey());
    }

    public synchronized EventPhase phaseAfterWave(int wave) {
        return EndRiftEncounterController.phaseAfterWave(wave);
    }

    public synchronized TransitionOutcome previewCompleteWave(int wave, String reason,
                                                                String idempotencyKey) {
        if (closed) return TransitionOutcome.rejected(phase(), "SESSION_CLOSED");
        EndEventStateMachine.TransitionResult result = encounterController.previewCompleteWave(
                eventId(), generation(), wave, reason, idempotencyKey);
        if (!result.success()) return TransitionOutcome.rejected(phase(), result.code());
        return new TransitionOutcome(true, phaseAfterWave(wave), result.code(),
                result.reason(), result.idempotencyKey());
    }

    public synchronized TransitionOutcome completeWave(int wave, String reason,
                                                        String idempotencyKey) {
        if (closed) return TransitionOutcome.rejected(phase(), "SESSION_CLOSED");
        EndEventStateMachine.TransitionResult result = encounterController.completeWave(
                eventId(), generation(), wave, reason, idempotencyKey);
        if (!result.success()) return TransitionOutcome.rejected(phase(), result.code());
        return new TransitionOutcome(true, phase(), result.code(), result.reason(),
                result.idempotencyKey());
    }

    public synchronized TransitionOutcome completeCoreRestoration(String reason,
                                                                   String idempotencyKey) {
        if (closed) return TransitionOutcome.rejected(phase(), "SESSION_CLOSED");
        EndEventStateMachine.TransitionResult result = encounterController.completeCoreRestoration(
                eventId(), generation(), reason, idempotencyKey);
        if (!result.success()) return TransitionOutcome.rejected(phase(), result.code());
        return new TransitionOutcome(true, phase(), result.code(), result.reason(),
                result.idempotencyKey());
    }

    public synchronized TransitionOutcome advanceIntermission(String reason,
                                                                String idempotencyKey) {
        if (closed) return TransitionOutcome.rejected(phase(), "SESSION_CLOSED");
        EndEventStateMachine.TransitionResult result = encounterController.advanceIntermission(
                eventId(), generation(), reason, idempotencyKey);
        if (!result.success()) return TransitionOutcome.rejected(phase(), result.code());
        return new TransitionOutcome(true, phase(), result.code(), result.reason(),
                result.idempotencyKey());
    }

    public synchronized void updateObjectiveForPhase() {
        int wave = waveForPhase(encounterController.phase());
        if (wave > 0) encounterController.replaceContext(
                context().withObjective(me.copimine.endevent.domain.EndRiftObjective.objective(wave)));
    }

    public synchronized void replaceContext(EncounterContext replacement) {
        if (closed) throw new IllegalStateException("session is closed");
        if (replacement == null || !encounterController.accepts(
                replacement.eventId(), replacement.generation())) {
            throw new IllegalArgumentException("replacement must retain event identity and generation");
        }
        encounterController.replaceContext(replacement);
    }

    public synchronized void close() {
        if (closed) {
            if (cleanupFailure != null) throw cleanupFailure;
            return;
        }
        closed = true;
        if (resourceScope != null) {
            try {
                resourceScope.close();
            } catch (Exception error) {
                cleanupFailure = error instanceof RuntimeException runtime
                        ? runtime
                        : new IllegalStateException("Encounter resource cleanup failed", error);
                throw cleanupFailure;
            }
        }
    }

    public static int waveForPhase(EventPhase phase) {
        if (phase == null) return 0;
        return switch (phase) {
            case WAVE_1 -> 1;
            case WAVE_2 -> 2;
            case WAVE_3 -> 3;
            case WAVE_4 -> 4;
            case WAVE_5 -> 5;
            case WAVE_6 -> 6;
            case WAVE_7 -> 7;
            default -> 0;
        };
    }

    public record TransitionOutcome(boolean accepted, EventPhase phase, String code,
                                    String reason, String idempotencyKey) {
        private static TransitionOutcome rejected(EventPhase phase, String code) {
            return new TransitionOutcome(false, phase, code, "", "");
        }
    }
}
