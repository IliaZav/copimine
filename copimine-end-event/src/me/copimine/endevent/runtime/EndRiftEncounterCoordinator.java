package me.copimine.endevent.runtime;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import me.copimine.endevent.domain.EndEventStateMachine;
import me.copimine.endevent.domain.EndRiftObjective;
import me.copimine.endevent.domain.EventPhase;
import me.copimine.endevent.runtime.encounter.BlackFogEncounter;
import me.copimine.endevent.runtime.encounter.CollapseRingsEncounter;
import me.copimine.endevent.runtime.encounter.ObeliskAssaultEncounter;
import me.copimine.endevent.runtime.encounter.RealitySplitEncounter;
import me.copimine.endevent.runtime.encounter.RiftCarriersEncounter;
import me.copimine.endevent.runtime.encounter.RiftGatesEncounter;
import me.copimine.endevent.runtime.encounter.RiftHuntEncounter;
import me.copimine.endevent.runtime.encounter.WaveEncounter;

/**
 * Pure orchestration boundary for the canonical seven-wave graph. Bukkit
 * adapters own entities and effects; this coordinator owns ordering,
 * generation fences and objective progress admission.
 */
public final class EndRiftEncounterCoordinator implements AutoCloseable {
    private final EndRiftSession session;
    private final Map<EndRiftObjective.Objective, WaveEncounter> encounters = new EnumMap<>(EndRiftObjective.Objective.class);
    private final List<Transition> history = new ArrayList<>();
    private boolean closed;

    public EndRiftEncounterCoordinator(EncounterContext context, EventPhase initialPhase) {
        this(context, initialPhase, null);
    }

    public EndRiftEncounterCoordinator(EncounterContext context, EventPhase initialPhase,
                                       AutoCloseable resourceScope) {
        this.session = new EndRiftSession(context, initialPhase, resourceScope);
        register(new RiftCarriersEncounter());
        register(new RiftHuntEncounter());
        register(new RiftGatesEncounter());
        register(new ObeliskAssaultEncounter());
        register(new BlackFogEncounter());
        register(new CollapseRingsEncounter());
        register(new RealitySplitEncounter());
    }

    public synchronized EndRiftSession session() { return session; }
    public synchronized EncounterContext context() { return session.context(); }
    public synchronized EventPhase phase() { return session.phase(); }
    public synchronized List<Transition> history() { return List.copyOf(history); }
    public synchronized WaveEncounter encounter(EndRiftObjective.Objective objective) {
        return encounters.get(objective);
    }

    /** Transition only through the strict graph; no wave number jump is exposed. */
    public synchronized Result transition(EventPhase expected, EventPhase next,
                                           String reason, String idempotencyKey) {
        if (closed) return rejected("COORDINATOR_CLOSED");
        EndRiftSession.TransitionOutcome outcome = session.transition(expected, next, reason, idempotencyKey);
        if (!outcome.accepted()) return rejected(outcome.code());
        session.updateObjectiveForPhase();
        history.add(new Transition(expected, next, idempotencyKey));
        return accepted(next, "transition accepted");
    }

    /** Start the next canonical wave, including its required predecessor phase. */
    public synchronized Result startNextWave(EndRiftObjective.Objective objective,
                                             String reason, String idempotencyKey) {
        if (closed || objective == null) return rejected("INVALID_OBJECTIVE");
        EventPhase target = wavePhase(objective);
        EventPhase predecessor = predecessorPhase(objective);
        if (session.phase() != predecessor) return rejected("PREDECESSOR_PHASE_REQUIRED");
        Result moved = transition(predecessor, target, reason, idempotencyKey);
        if (!moved.accepted()) return moved;
        WaveEncounter encounter = encounters.get(objective);
        return fromEncounter(encounter.start(session.context().withObjective(objective)));
    }

    /** Start a wave after an adapter has already committed the phase transition. */
    public synchronized Result startCurrentWave(EndRiftObjective.Objective objective) {
        if (closed || objective == null || session.phase() != wavePhase(objective)) {
            return rejected("WAVE_PHASE_REQUIRED");
        }
        WaveEncounter encounter = encounters.get(objective);
        return fromEncounter(encounter.start(session.context().withObjective(objective)));
    }

    public synchronized Result tickCurrentWave() {
        EndRiftObjective.Objective objective = objectiveForWavePhase(session.phase());
        if (objective == null) return rejected("NOT_IN_WAVE");
        return fromEncounter(encounters.get(objective).tick(session.context().withObjective(objective)));
    }

    /** Complete an objective and enter its only legal following stage. */
    public synchronized Result completeWave(EndRiftObjective.Objective objective,
                                             String reason, String idempotencyKey) {
        if (closed || objective == null || session.phase() != wavePhase(objective)) {
            return rejected("WAVE_PHASE_REQUIRED");
        }
        WaveEncounter encounter = encounters.get(objective);
        WaveEncounter.Result completed = encounter.complete(session.context().withObjective(objective));
        if (!completed.complete()) return fromEncounter(completed);
        EventPhase next = switch (objective) {
            case RIFT_CARRIERS -> EventPhase.INTERMISSION_1;
            case RIFT_HUNT -> EventPhase.INTERMISSION_2;
            case RIFT_GATES -> EventPhase.INTERMISSION_3;
            case OBELISK_ASSAULT -> EventPhase.CORE_RESTORATION;
            case BLACK_FOG -> EventPhase.INTERMISSION_5;
            case COLLAPSE_RINGS -> EventPhase.INTERMISSION_6;
            case REALITY_SPLIT -> EventPhase.PRE_BOSS_COOLDOWN;
        };
        Result moved = transition(session.phase(), next, reason, idempotencyKey);
        if (!moved.accepted()) return moved;
        return fromEncounter(completed);
    }

    /** W4 has no transition-rune intermission; restoration is an explicit stage. */
    public synchronized Result completeCoreRestoration(String reason, String idempotencyKey) {
        if (closed || session.phase() != EventPhase.CORE_RESTORATION) {
            return rejected("CORE_RESTORATION_REQUIRED");
        }
        return transition(EventPhase.CORE_RESTORATION, EventPhase.WAVE_5,
                reason, idempotencyKey);
    }

    /** Advance only from an intermission that belongs to the corresponding wave. */
    public synchronized Result advanceIntermission(String reason, String idempotencyKey) {
        EndRiftObjective.Objective next = switch (session.phase()) {
            case INTERMISSION_1 -> EndRiftObjective.Objective.RIFT_HUNT;
            case INTERMISSION_2 -> EndRiftObjective.Objective.RIFT_GATES;
            case INTERMISSION_3 -> EndRiftObjective.Objective.OBELISK_ASSAULT;
            case INTERMISSION_5 -> EndRiftObjective.Objective.COLLAPSE_RINGS;
            case INTERMISSION_6 -> EndRiftObjective.Objective.REALITY_SPLIT;
            default -> null;
        };
        if (next == null) return rejected("INTERMISSION_REQUIRED");
        return startNextWave(next, reason, idempotencyKey);
    }

    public synchronized boolean accepts(String eventId, long generation) {
        return !closed && session.accepts(eventId, generation);
    }

    public synchronized void resetEncounter(EndRiftObjective.Objective objective) {
        WaveEncounter encounter = encounters.get(objective);
        if (encounter != null) encounter.reset();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        encounters.values().forEach(WaveEncounter::reset);
        session.close();
    }

    private void register(WaveEncounter encounter) {
        encounters.put(encounter.objective(), encounter);
    }

    private static Result fromEncounter(WaveEncounter.Result result) {
        return new Result(result.accepted(), null, result.status().name(), result.reason(),
                result.progress(), result.required());
    }

    private Result accepted(EventPhase phase, String reason) {
        return new Result(true, phase, "OK", reason, 0, 0);
    }

    private Result rejected(String reason) {
        return new Result(false, session.phase(), reason, "", 0, 0);
    }

    public static EventPhase wavePhase(EndRiftObjective.Objective objective) {
        return switch (objective) {
            case RIFT_CARRIERS -> EventPhase.WAVE_1;
            case RIFT_HUNT -> EventPhase.WAVE_2;
            case RIFT_GATES -> EventPhase.WAVE_3;
            case OBELISK_ASSAULT -> EventPhase.WAVE_4;
            case BLACK_FOG -> EventPhase.WAVE_5;
            case COLLAPSE_RINGS -> EventPhase.WAVE_6;
            case REALITY_SPLIT -> EventPhase.WAVE_7;
        };
    }

    private static EventPhase predecessorPhase(EndRiftObjective.Objective objective) {
        return switch (objective) {
            case RIFT_CARRIERS -> EventPhase.START_RITUAL;
            case RIFT_HUNT -> EventPhase.INTERMISSION_1;
            case RIFT_GATES -> EventPhase.INTERMISSION_2;
            case OBELISK_ASSAULT -> EventPhase.INTERMISSION_3;
            case BLACK_FOG -> EventPhase.CORE_RESTORATION;
            case COLLAPSE_RINGS -> EventPhase.INTERMISSION_5;
            case REALITY_SPLIT -> EventPhase.INTERMISSION_6;
        };
    }

    private static EndRiftObjective.Objective objectiveForWavePhase(EventPhase phase) {
        return switch (phase) {
            case WAVE_1 -> EndRiftObjective.Objective.RIFT_CARRIERS;
            case WAVE_2 -> EndRiftObjective.Objective.RIFT_HUNT;
            case WAVE_3 -> EndRiftObjective.Objective.RIFT_GATES;
            case WAVE_4 -> EndRiftObjective.Objective.OBELISK_ASSAULT;
            case WAVE_5 -> EndRiftObjective.Objective.BLACK_FOG;
            case WAVE_6 -> EndRiftObjective.Objective.COLLAPSE_RINGS;
            case WAVE_7 -> EndRiftObjective.Objective.REALITY_SPLIT;
            default -> null;
        };
    }

    public record Result(boolean accepted, EventPhase phase, String code, String reason,
                         int progress, int required) {
    }

    public record Transition(EventPhase from, EventPhase to, String idempotencyKey) {
    }
}
