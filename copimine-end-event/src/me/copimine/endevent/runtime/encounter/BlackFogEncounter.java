package me.copimine.endevent.runtime.encounter;

import java.util.LinkedHashSet;
import java.util.Set;
import me.copimine.endevent.domain.BlackFogTimingPolicy;
import me.copimine.endevent.domain.EndRiftObjective;
import me.copimine.endevent.runtime.EncounterContext;

/** Wave 5 three-cycle fog objective; the warning/safe/fog timeline is adapter-owned. */
public final class BlackFogEncounter extends AbstractWaveEncounter {
    private final Set<Integer> completedCycles = new LinkedHashSet<>();

    public BlackFogEncounter() {
        super(EndRiftObjective.Objective.BLACK_FOG, 5, BlackFogTimingPolicy.CYCLE_COUNT);
    }

    public synchronized Result completeCycle(EncounterContext context, int cycle,
                                              boolean safeZoneHeld) {
        if (!accepts(context) || !safeZoneHeld || cycle < 1
                || cycle > BlackFogTimingPolicy.CYCLE_COUNT || cycle != progress() + 1
                || !completedCycles.add(cycle)) {
            return rejected("FOG_CYCLE_NOT_SAFE_OR_NEXT");
        }
        return addProgress(context, 1, "black fog cycle complete");
    }

    @Override
    protected synchronized void onReset() { completedCycles.clear(); }
}
