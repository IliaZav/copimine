package me.copimine.endevent.runtime.encounter;

import java.util.LinkedHashSet;
import java.util.Set;
import me.copimine.endevent.domain.EndRiftObjective;
import me.copimine.endevent.runtime.EncounterContext;

/** Wave 3 exactly-three sequential portal captures. */
public final class RiftGatesEncounter extends AbstractWaveEncounter {
    private final Set<Integer> capturedGates = new LinkedHashSet<>();

    public RiftGatesEncounter() {
        super(EndRiftObjective.Objective.RIFT_GATES, 3, EndRiftObjective.REQUIRED_GATES);
    }

    public synchronized Result captureGate(EncounterContext context, int gateIndex) {
        if (!accepts(context) || gateIndex < 0 || gateIndex >= EndRiftObjective.GATE_COUNT
                || gateIndex != progress() || !capturedGates.add(gateIndex)) {
            return rejected("GATE_NOT_NEXT_OR_STALE");
        }
        return addProgress(context, 1, "portal captured sequentially");
    }

    public synchronized Set<Integer> capturedGates() { return Set.copyOf(capturedGates); }

    @Override
    protected synchronized void onReset() { capturedGates.clear(); }
}
