package me.copimine.endevent.domain;

/** Ownership checks for the short Regeneration/Resistance effects supplied by a Rift Giant. */
public final class RiftGiantAuraEffectPolicy {
    public static final int AURA_DURATION_TICKS = 40;

    private RiftGiantAuraEffectPolicy() { }

    public static boolean hasManagedAuraSignature(int duration, int amplifier,
                                                   boolean ambient, boolean particles,
                                                   boolean icon) {
        return duration > 0 && duration <= AURA_DURATION_TICKS && amplifier == 0
                && ambient && !particles && icon;
    }

    public static boolean mayApply(boolean effectMissing, boolean sourceLeaseMatches,
                                   int duration, int amplifier, boolean ambient,
                                   boolean particles, boolean icon) {
        return effectMissing || sourceLeaseMatches
                && hasManagedAuraSignature(duration, amplifier, ambient, particles, icon);
    }

    public static boolean mayRemove(boolean sourceLeaseMatches, int duration, int amplifier,
                                    boolean ambient, boolean particles, boolean icon) {
        return sourceLeaseMatches
                && hasManagedAuraSignature(duration, amplifier, ambient, particles, icon);
    }
}
