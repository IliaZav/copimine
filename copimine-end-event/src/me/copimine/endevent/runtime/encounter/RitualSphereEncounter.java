package me.copimine.endevent.runtime.encounter;

import java.util.List;
import java.util.UUID;
import me.copimine.endevent.domain.RitualSealCapturePolicy;
import me.copimine.endevent.domain.RitualSphereEncounterPolicy;
import me.copimine.endevent.domain.RitualSphereScalingPolicy;
import me.copimine.endevent.runtime.EncounterContext;

/** Coordinator adapter for the server-side Wave 6 Ritual Sphere objective. */
public final class RitualSphereEncounter extends AbstractWaveEncounter {
    private RitualSphereEncounterPolicy.State state;

    public RitualSphereEncounter() {
        super(me.copimine.endevent.domain.EndRiftObjective.Objective.RITUAL_SPHERE, 6, 4);
    }

    @Override
    public synchronized Result start(EncounterContext context) {
        Result result = super.start(context);
        if (result.status() == Status.STARTED) {
            state = null;
        }
        return result;
    }

    /** Capture exactly one eligible participant after physical seal entry. */
    public synchronized Result capture(EncounterContext context,
                                       List<RitualSealCapturePolicy.Candidate> candidates,
                                       double sealX, double sealZ, long nowMillis) {
        if (!accepts(context)) {
            return rejected("STALE_OR_NOT_STARTED");
        }
        if (state != null) {
            return result(Status.IN_PROGRESS, "prisoner already captured");
        }
        UUID prisoner = RitualSealCapturePolicy.select(candidates, sealX, sealZ);
        if (prisoner == null || !context.isLivingParticipant(prisoner)) {
            return result(Status.IN_PROGRESS, "waiting for prisoner");
        }
        state = RitualSphereEncounterPolicy.initial(context.generation(), prisoner,
                context.livingParticipants().size(), nowMillis);
        return result(Status.IN_PROGRESS, "prisoner captured");
    }

    public synchronized Result drain(EncounterContext context, double currentHealth, long nowMillis) {
        if (!accepts(context) || state == null) {
            return rejected("STALE_OR_NOT_STARTED");
        }
        RitualSphereEncounterPolicy.DrainTransition transition =
                RitualSphereEncounterPolicy.advanceDrain(state, currentHealth, nowMillis);
        state = transition.state();
        return result(Status.IN_PROGRESS, transition.applied()
                ? "prisoner drain applied" : "prisoner drain not due");
    }

    @Override
    public synchronized Result complete(EncounterContext context) {
        if (!accepts(context)) {
            return rejected("STALE_OR_NOT_STARTED");
        }
        if (state == null) {
            return rejected("PRISONER_CAPTURE_REQUIRED");
        }
        return super.complete(context);
    }

    public synchronized Result casterDefeated(EncounterContext context) {
        if (!accepts(context)) {
            return rejected("STALE_OR_NOT_STARTED");
        }
        return addProgress(context, 1, "ritual caster defeated");
    }

    public synchronized RitualSphereEncounterPolicy.State state() {
        return state;
    }

    @Override
    protected synchronized int requiredFor(EncounterContext context) {
        return RitualSphereScalingPolicy.forPlayers(context.livingParticipants().size()).casterCount();
    }

    @Override
    protected synchronized void onReset() {
        state = null;
    }
}
