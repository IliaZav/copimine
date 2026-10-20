package me.copimine.endevent.runtime.encounter;

import java.util.LinkedHashMap;
import java.util.Map;
import me.copimine.endevent.domain.CollapseRingEncounterPolicy;
import me.copimine.endevent.domain.EndRiftObjective;
import me.copimine.endevent.runtime.EncounterContext;

/** Wave 6 paired-guard objective with a first-death timer per ring. */
public final class CollapseRingsEncounter extends AbstractWaveEncounter {
    private final Map<Integer, Long> deadlines = new LinkedHashMap<>();

    public CollapseRingsEncounter() {
        super(EndRiftObjective.Objective.COLLAPSE_RINGS, 6,
                EndRiftObjective.REQUIRED_COLLAPSE_RINGS);
    }

    public synchronized boolean firstGuardDown(EncounterContext context, int ring, long nowTick) {
        if (!accepts(context) || ring < 1 || ring > EndRiftObjective.RING_COUNT
                || deadlines.containsKey(ring)) return false;
        deadlines.put(ring, Math.max(0L, nowTick) + CollapseRingEncounterPolicy.PAIR_TIMER_TICKS);
        return true;
    }

    public synchronized Result completePair(EncounterContext context, int ring, long nowTick) {
        Long deadline = deadlines.get(ring);
        if (!accepts(context) || deadline == null || Math.max(0L, nowTick) > deadline) {
            return rejected("PAIR_WINDOW_EXPIRED_OR_MISSING");
        }
        deadlines.remove(ring);
        return addProgress(context, 1, "collapse ring pair defeated");
    }

    public synchronized boolean timerActive(int ring, long nowTick) {
        Long deadline = deadlines.get(ring);
        return deadline != null && Math.max(0L, nowTick) <= deadline;
    }

    @Override
    protected synchronized void onReset() { deadlines.clear(); }
}
