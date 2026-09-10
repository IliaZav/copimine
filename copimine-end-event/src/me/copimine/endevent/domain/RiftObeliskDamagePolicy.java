package me.copimine.endevent.domain;

/** Pure HP/eligibility rules for one Rift Obelisk. */
public final class RiftObeliskDamagePolicy {
    public static final int DEFAULT_HEALTH = 3;

    private RiftObeliskDamagePolicy() {
    }

    /**
     * Evaluate one projectile contact.  The five booleans deliberately keep
     * source identity, reflection, generation and player ownership separate;
     * satisfying only one of them can never damage the obelisk.
     */
    public static HitResult applyReflectedHit(int currentHealth,
                                               boolean eventOwned,
                                               boolean reflected,
                                               boolean generationMatches,
                                               boolean reflectedByPlayer) {
        return applyReflectedHit(currentHealth, DEFAULT_HEALTH, eventOwned, reflected,
                generationMatches, reflectedByPlayer);
    }

    /**
     * V3 keeps the same source/reflection contract but scales the real-block
     * obelisk health by the bounded party profile (3, 4 or 5).  Keeping the
     * maximum health explicit prevents a damaged object from being silently
     * clamped back to the legacy three-hit value.
     */
    public static HitResult applyReflectedHit(int currentHealth, int maximumHealth,
                                               boolean eventOwned,
                                               boolean reflected,
                                               boolean generationMatches,
                                               boolean reflectedByPlayer) {
        int safeMaximum = Math.max(0, maximumHealth);
        int safeHealth = Math.max(0, Math.min(safeMaximum, currentHealth));
        if (safeHealth == 0) {
            return new HitResult(0, false, false, true);
        }
        boolean accepted = eventOwned && reflected && generationMatches && reflectedByPlayer;
        if (!accepted) {
            return new HitResult(safeHealth, false, false, false);
        }
        int remaining = safeHealth - 1;
        return new HitResult(remaining, true, remaining == 0, false);
    }

    public record HitResult(int remainingHealth, boolean accepted, boolean destroyed, boolean ignored) {
    }
}
