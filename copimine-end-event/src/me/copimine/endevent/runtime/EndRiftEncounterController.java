package me.copimine.endevent.runtime;

import me.copimine.endevent.domain.EndEventStateMachine;
import me.copimine.endevent.domain.EndRiftObjective;
import me.copimine.endevent.domain.EventPhase;

/**
 * Single lifecycle authority shared by the live plugin facade and the pure
 * encounter coordinator. It owns the encounter identity, immutable context,
 * and global phase graph; adapters may request transitions but cannot replace
 * the graph or advance a stale generation.
 */
public final class EndRiftEncounterController {
    private String eventId;
    private long generation;
    private EncounterContext context;
    private EndEventStateMachine stateMachine;

    public EndRiftEncounterController(String eventId, long generation, EventPhase initialPhase) {
        restore(eventId, generation, initialPhase, null);
    }

    public EndRiftEncounterController(EncounterContext context, EventPhase initialPhase) {
        if (context == null) throw new IllegalArgumentException("context is required");
        restore(context.eventId(), context.generation(), initialPhase, context);
    }

    public synchronized String eventId() { return eventId; }
    public synchronized long generation() { return generation; }
    public synchronized EventPhase phase() { return stateMachine.phase(); }
    public synchronized EncounterContext context() { return context; }

    public synchronized boolean accepts(String expectedEventId, long expectedGeneration) {
        return eventId != null && eventId.equals(expectedEventId)
                && generation == expectedGeneration;
    }

    public synchronized EndEventStateMachine.TransitionResult previewTransition(
            EventPhase expected, EventPhase next, String reason, String idempotencyKey) {
        return previewTransition(eventId, generation, expected, next, reason, idempotencyKey);
    }

    public synchronized EndEventStateMachine.TransitionResult previewTransition(
            String expectedEventId, long expectedGeneration, EventPhase expected,
            EventPhase next, String reason, String idempotencyKey) {
        if (!accepts(expectedEventId, expectedGeneration)) return staleGeneration();
        return stateMachine.previewTransition(expected, next, reason, idempotencyKey);
    }

    public synchronized EndEventStateMachine.TransitionResult transition(
            EventPhase expected, EventPhase next, String reason, String idempotencyKey) {
        return transition(eventId, generation, expected, next, reason, idempotencyKey);
    }

    public synchronized EndEventStateMachine.TransitionResult transition(
            String expectedEventId, long expectedGeneration, EventPhase expected,
            EventPhase next, String reason, String idempotencyKey) {
        if (!accepts(expectedEventId, expectedGeneration)) return staleGeneration();
        return stateMachine.transition(expected, next, reason, idempotencyKey);
    }

    /** A completed wave reports its number; this controller chooses the only next global phase. */
    public synchronized EndEventStateMachine.TransitionResult completeWave(
            String expectedEventId, long expectedGeneration, int wave,
            String reason, String idempotencyKey) {
        if (!accepts(expectedEventId, expectedGeneration)) return staleGeneration();
        EventPhase current = wavePhase(wave);
        EventPhase next = phaseAfterWave(wave);
        if (current == null || next == null || phase() != current) return invalidPhase("WAVE_PHASE_REQUIRED");
        return transition(expectedEventId, expectedGeneration, current, next, reason, idempotencyKey);
    }

    public synchronized EndEventStateMachine.TransitionResult completeCoreRestoration(
            String expectedEventId, long expectedGeneration, String reason, String idempotencyKey) {
        return transition(expectedEventId, expectedGeneration, EventPhase.CORE_RESTORATION,
                EventPhase.INTERMISSION_4, reason, idempotencyKey);
    }

    /** The rune adapter reports a valid completed hold; this controller chooses the next wave. */
    public synchronized EndEventStateMachine.TransitionResult advanceIntermission(
            String expectedEventId, long expectedGeneration,
            String reason, String idempotencyKey) {
        if (!accepts(expectedEventId, expectedGeneration)) return staleGeneration();
        int nextWave = nextWaveNumberAfterIntermission(phase());
        EventPhase next = wavePhase(nextWave);
        if (nextWave == 0 || next == null) return invalidPhase("INTERMISSION_REQUIRED");
        return transition(expectedEventId, expectedGeneration, phase(), next, reason, idempotencyKey);
    }

    public synchronized EndEventStateMachine.TransitionResult previewCompleteWave(
            String expectedEventId, long expectedGeneration, int wave,
            String reason, String idempotencyKey) {
        if (!accepts(expectedEventId, expectedGeneration)) return staleGeneration();
        EventPhase current = wavePhase(wave);
        EventPhase next = phaseAfterWave(wave);
        if (current == null || next == null || phase() != current) return invalidPhase("WAVE_PHASE_REQUIRED");
        return stateMachine.previewTransition(current, next, reason, idempotencyKey);
    }

    public static EventPhase wavePhase(int wave) {
        return switch (wave) {
            case 1 -> EventPhase.WAVE_1;
            case 2 -> EventPhase.WAVE_2;
            case 3 -> EventPhase.WAVE_3;
            case 4 -> EventPhase.WAVE_4;
            case 5 -> EventPhase.WAVE_5;
            case 6 -> EventPhase.WAVE_6;
            case 7 -> EventPhase.WAVE_7;
            default -> null;
        };
    }

    public static EventPhase phaseAfterWave(int wave) {
        return switch (wave) {
            case 1 -> EventPhase.INTERMISSION_1;
            case 2 -> EventPhase.INTERMISSION_2;
            case 3 -> EventPhase.INTERMISSION_3;
            case 4 -> EventPhase.CORE_RESTORATION;
            case 5 -> EventPhase.INTERMISSION_5;
            case 6 -> EventPhase.INTERMISSION_6;
            case 7 -> EventPhase.PRE_BOSS_COOLDOWN;
            default -> null;
        };
    }

    public static int nextWaveNumberAfterIntermission(EventPhase phase) {
        return switch (phase) {
            case INTERMISSION_1 -> 2;
            case INTERMISSION_2 -> 3;
            case INTERMISSION_3 -> 4;
            case INTERMISSION_4 -> 5;
            case INTERMISSION_5 -> 6;
            case INTERMISSION_6 -> 7;
            default -> 0;
        };
    }

    public static EventPhase predecessorPhase(int wave) {
        return switch (wave) {
            case 1 -> EventPhase.START_RITUAL;
            case 2 -> EventPhase.INTERMISSION_1;
            case 3 -> EventPhase.INTERMISSION_2;
            case 4 -> EventPhase.INTERMISSION_3;
            case 5 -> EventPhase.INTERMISSION_4;
            case 6 -> EventPhase.INTERMISSION_5;
            case 7 -> EventPhase.INTERMISSION_6;
            default -> null;
        };
    }

    public static int waveNumber(EndRiftObjective.Objective objective) {
        if (objective == null) return 0;
        return switch (objective) {
            case RIFT_CARRIERS -> 1;
            case RIFT_HUNT -> 2;
            case RIFT_GATES -> 3;
            case OBELISK_ASSAULT -> 4;
            case BLACK_FOG -> 5;
            case COLLAPSE_RINGS, RITUAL_SPHERE -> 6;
            case REALITY_SPLIT -> 7;
        };
    }

    /** Replace only changing roster/context facts while retaining this run's identity. */
    public synchronized void replaceContext(EncounterContext replacement) {
        if (replacement == null || !accepts(replacement.eventId(), replacement.generation())) {
            throw new IllegalArgumentException("context must retain event identity and generation");
        }
        context = replacement;
    }

    /**
     * Reset/load boundary. This is the only bypass of the normal phase graph;
     * callers use it for snapshot restoration and explicit abort/recovery.
     */
    public synchronized void restore(String eventId, long generation, EventPhase phase,
                                     EncounterContext context) {
        if (eventId == null) throw new IllegalArgumentException("event id is required");
        if (generation < 0L) throw new IllegalArgumentException("generation cannot be negative");
        if (context != null && !context.owns(eventId, generation)) {
            throw new IllegalArgumentException("context identity must match controller identity");
        }
        this.eventId = eventId;
        this.generation = generation;
        this.context = context;
        this.stateMachine = new EndEventStateMachine(phase);
    }

    /**
     * Abort/recovery may target any safe phase. The exact run identity is still
     * checked so an old callback cannot reset a newer encounter.
     */
    public synchronized boolean recoverTo(String expectedEventId, long expectedGeneration,
                                          EventPhase safePhase) {
        if (!accepts(expectedEventId, expectedGeneration) || safePhase == null) return false;
        stateMachine = new EndEventStateMachine(safePhase);
        return true;
    }

    private static EndEventStateMachine.TransitionResult staleGeneration() {
        return new EndEventStateMachine.TransitionResult(false, "STALE_GENERATION", "", "");
    }

    private static EndEventStateMachine.TransitionResult invalidPhase(String code) {
        return new EndEventStateMachine.TransitionResult(false, code, "", "");
    }
}
