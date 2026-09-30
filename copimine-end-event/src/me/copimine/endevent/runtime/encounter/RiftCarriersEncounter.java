package me.copimine.endevent.runtime.encounter;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import me.copimine.endevent.domain.EndRiftObjective;
import me.copimine.endevent.runtime.EncounterContext;

/** Wave 1 delivery objective with holder release and exactly-once deliveries. */
public final class RiftCarriersEncounter extends AbstractWaveEncounter {
    private final Set<UUID> consumedDeliveries = new LinkedHashSet<>();
    private UUID holder;

    public RiftCarriersEncounter() {
        super(EndRiftObjective.Objective.RIFT_CARRIERS, 1,
                EndRiftObjective.REQUIRED_CARRIER_DELIVERIES);
    }

    public synchronized boolean selectCarrier(EncounterContext context, UUID player) {
        return participantIsValid(context, player) && holder == null && (holder = player) != null;
    }

    public synchronized boolean releaseHolder(EncounterContext context, UUID player) {
        if (!accepts(context) || holder == null || !holder.equals(player)) return false;
        holder = null;
        return true;
    }

    public synchronized Result deliver(EncounterContext context, UUID player, UUID deliveryId,
                                       boolean atCore) {
        if (!participantIsValid(context, player) || deliveryId == null || !atCore
                || (holder != null && !holder.equals(player)) || consumedDeliveries.contains(deliveryId)) {
            return rejected("DELIVERY_REJECTED");
        }
        consumedDeliveries.add(deliveryId);
        holder = null;
        return addProgress(context, 1, "carrier delivered");
    }

    public synchronized UUID holder() { return holder; }

    @Override
    protected synchronized void onReset() {
        consumedDeliveries.clear();
        holder = null;
    }
}
