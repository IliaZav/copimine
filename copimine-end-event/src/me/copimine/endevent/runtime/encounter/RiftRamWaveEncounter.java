package me.copimine.endevent.runtime.encounter;

import me.copimine.endevent.domain.EndRiftObjective;
import me.copimine.endevent.domain.RiftRamPolicy;
import me.copimine.endevent.runtime.EncounterContext;

/**
 * Wave 6 objective: the Rift Ram shatters five pillars, one at a time.
 *
 * <p>The Bukkit {@code RiftRamEncounter} owns the entity, the pillars and the
 * presentation; this adapter owns only the canonical objective contract the
 * coordinator advances on. Progress is driven exclusively by pillar impacts the
 * live runtime reports, so the ram cannot be marked complete by killing the
 * Ravager early -- the armour stages gate the real damage.</p>
 */
public final class RiftRamWaveEncounter extends AbstractWaveEncounter {
    public RiftRamWaveEncounter() {
        super(EndRiftObjective.Objective.RIFT_RAM, 6, EndRiftObjective.REQUIRED_PILLAR_IMPACTS);
    }

    /** Record one destroyed pillar. */
    public synchronized Result recordPillarImpact(EncounterContext context, int impacts) {
        if (impacts < 0 || impacts > RiftRamPolicy.REQUIRED_IMPACTS) {
            return rejected("INVALID_PILLAR_COUNT");
        }
        if (!accepts(context)) return rejected("STALE_OR_NOT_STARTED");
        // Set, never increment: the live runtime is authoritative for how many
        // pillars are down, so a duplicate callback cannot inflate progress.
        int target = Math.min(required, impacts);
        if (target <= progress()) return result(Status.IN_PROGRESS, "pillar impact already recorded");
        return addProgress(context, target - progress(), "pillar destroyed");
    }
}
