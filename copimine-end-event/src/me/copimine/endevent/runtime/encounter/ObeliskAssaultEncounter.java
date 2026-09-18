package me.copimine.endevent.runtime.encounter;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import me.copimine.endevent.domain.EndRiftObjective;
import me.copimine.endevent.domain.ObeliskScalingPolicy;
import me.copimine.endevent.runtime.EncounterContext;

/** Wave 4 reflected-projectile objective; damage is accepted once per projectile UUID. */
public final class ObeliskAssaultEncounter extends AbstractWaveEncounter {
    private final Set<UUID> consumedReflections = new LinkedHashSet<>();
    private int obeliskCount;

    public ObeliskAssaultEncounter() {
        super(EndRiftObjective.Objective.OBELISK_ASSAULT, 4, 3);
    }

    @Override
    protected synchronized int requiredFor(EncounterContext context) {
        ObeliskScalingPolicy.Profile profile = ObeliskScalingPolicy.profileForPlayers(
                context.livingPlayerCount());
        obeliskCount = profile.obeliskCount();
        return Math.max(1, profile.requiredHits());
    }

    public synchronized Result reflectedHit(EncounterContext context, UUID projectile,
                                             UUID reflector, int obeliskIndex) {
        if (!participantIsValid(context, reflector) || projectile == null
                || obeliskIndex < 0 || obeliskIndex >= obeliskCount
                || !consumedReflections.add(projectile)) {
            return rejected("REFLECTION_REJECTED");
        }
        return addProgress(context, 1, "reflected Rift Fireball hit obelisk");
    }

    public synchronized int obeliskCount() { return obeliskCount; }
    public synchronized int remainingHits() { return Math.max(0, required() - progress()); }

    @Override
    protected synchronized void onReset() {
        consumedReflections.clear();
        obeliskCount = 0;
    }
}
