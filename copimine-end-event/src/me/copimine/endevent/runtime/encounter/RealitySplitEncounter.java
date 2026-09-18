package me.copimine.endevent.runtime.encounter;

import java.util.LinkedHashSet;
import java.util.Set;
import me.copimine.endevent.domain.ChamberIsolationPolicy;
import me.copimine.endevent.domain.EndRiftObjective;
import me.copimine.endevent.runtime.EncounterContext;

/** Wave 7 isolated-chamber objective; a chamber completes once, then merges explicitly. */
public final class RealitySplitEncounter extends AbstractWaveEncounter {
    private ChamberIsolationPolicy.Assignment assignment =
            new ChamberIsolationPolicy.Assignment(0, java.util.Map.of());
    private final Set<Integer> completedChambers = new LinkedHashSet<>();

    public RealitySplitEncounter() {
        super(EndRiftObjective.Objective.REALITY_SPLIT, 7, 2);
    }

    @Override
    protected synchronized int requiredFor(EncounterContext context) {
        assignment = ChamberIsolationPolicy.assign(
                new java.util.ArrayList<>(context.livingParticipants()));
        return Math.max(1, assignment.chamberCount());
    }

    public synchronized Result completeChamber(EncounterContext context, int chamber) {
        if (!accepts(context) || chamber < 0 || chamber >= assignment.chamberCount()
                || !completedChambers.add(chamber)) {
            return rejected("CHAMBER_INVALID_OR_ALREADY_COMPLETE");
        }
        return addProgress(context, 1, "reality chamber complete");
    }

    public synchronized ChamberIsolationPolicy.Assignment assignment() { return assignment; }
    public synchronized Set<Integer> completedChambers() { return Set.copyOf(completedChambers); }

    @Override
    protected synchronized void onReset() {
        assignment = new ChamberIsolationPolicy.Assignment(0, java.util.Map.of());
        completedChambers.clear();
    }
}
