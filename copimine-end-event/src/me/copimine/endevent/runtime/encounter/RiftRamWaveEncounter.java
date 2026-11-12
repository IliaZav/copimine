package me.copimine.endevent.runtime.encounter;

import me.copimine.endevent.domain.EndRiftObjective;
import me.copimine.endevent.domain.RiftRamPolicy;
import me.copimine.endevent.runtime.EncounterContext;

/**
 * Wave 6 objective: the Rift Ram shatters the pillar roster, one at a time.
 *
 * <p>The roster is the one {@link RiftRamPolicy} owns -- four ordinary pillars
 * and one final pillar that absorbs three collisions, seven accepted impacts in
 * total. The requirement is inherited from
 * {@link EndRiftObjective#REQUIRED_PILLAR_IMPACTS}, which is derived from that
 * same policy, so this adapter can never ask for fewer collisions than the live
 * encounter actually counts.</p>
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

    /**
     * The live Ram's accepted collision count is admitted through the shared
     * three-argument report in {@link AbstractWaveEncounter}.
     *
     * <p>No override is needed: this adapter's {@code required} IS
     * {@link RiftRamPolicy#REQUIRED_IMPACTS}, so the shared clamp already bounds
     * the incoming count by exactly the roster. Adding a second clamp here would
     * be dead code that no behavioural test could ever distinguish.</p>
     */
}
