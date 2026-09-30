package me.copimine.endevent.domain;

/** Keeps Wave 7 Eye reflections inside the chamber assigned to the reflector. */
public final class RiftFireballReflectionPolicy {
    private RiftFireballReflectionPolicy() {
    }

    public static boolean canReflect(boolean wave7TrialProjectile,
                                     boolean activeEncounterParticipant,
                                     boolean assignedTrialChamberParticipant,
                                     boolean localProbeParticipant) {
        if (wave7TrialProjectile) return assignedTrialChamberParticipant;
        return activeEncounterParticipant || localProbeParticipant;
    }
}
