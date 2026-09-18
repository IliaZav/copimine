package me.copimine.endevent.runtime.encounter;

import java.util.LinkedHashSet;
import java.util.Set;
import me.copimine.endevent.domain.EndRiftObjective;
import me.copimine.endevent.runtime.EncounterContext;

/** Wave 2 bounded mark/hunt cycles; a cycle cannot be completed twice. */
public final class RiftHuntEncounter extends AbstractWaveEncounter {
    private final Set<Integer> completedCycles = new LinkedHashSet<>();

    public RiftHuntEncounter() {
        super(EndRiftObjective.Objective.RIFT_HUNT, 2,
                EndRiftObjective.REQUIRED_HUNT_CYCLES);
    }

    public synchronized Result completeCycle(EncounterContext context, int cycle) {
        if (!accepts(context) || cycle < 1 || cycle > EndRiftObjective.REQUIRED_HUNT_CYCLES
                || cycle != progress() + 1 || !completedCycles.add(cycle)) {
            return rejected("CYCLE_NOT_NEXT_OR_STALE");
        }
        return addProgress(context, 1, "hunt cycle complete");
    }

    @Override
    protected synchronized void onReset() { completedCycles.clear(); }
}
