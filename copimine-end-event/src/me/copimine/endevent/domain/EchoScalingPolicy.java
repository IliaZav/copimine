package me.copimine.endevent.domain;

/**
 * ECHO-011 / ECHO-012: how a personal copy scales against its owner.
 *
 * <p>The copy is deliberately not a miniboss. It fights with the owner's own
 * weapon concept, so it is built from the owner's stable maximum health at
 * admission and hits 1.25x harder. The damage bonus is applied once, after the
 * legitimate hit has been established, because applying it twice is how copies
 * quietly become unkillable.
 */
public final class EchoScalingPolicy {
    /** Contract band for copy health. */
    public static final double MIN_HEALTH_MULTIPLIER = 2.0D;
    public static final double MAX_HEALTH_MULTIPLIER = 3.0D;
    /** Accepted default inside the band. */
    public static final double HEALTH_MULTIPLIER = 2.5D;
    /** Starting damage bonus. */
    public static final double DAMAGE_MULTIPLIER = 1.25D;

    private EchoScalingPolicy() {
    }

    /**
     * Copy maximum health from the owner's stable maximum at admission.
     *
     * <p>Temporary current health, absorptions and totems are intentionally not
     * part of this: the copy is built from a stable figure so a player who
     * happened to eat just before admission does not create a stronger copy.
     */
    public static double copyMaxHealth(double ownerMaxHealth) {
        if (!Double.isFinite(ownerMaxHealth) || ownerMaxHealth <= 0.0D) {
            return 0.0D;
        }
        return ownerMaxHealth * HEALTH_MULTIPLIER;
    }

    /** Owner damage after the 1.25x bonus, applied once. */
    public static double copyDamage(double legitimateDamage) {
        if (!Double.isFinite(legitimateDamage) || legitimateDamage <= 0.0D) {
            return 0.0D;
        }
        return legitimateDamage * DAMAGE_MULTIPLIER;
    }

    /**
     * Whether a hit may receive the bonus at all.
     *
     * @param fromOwner          the hit came from the copy's own owner
     * @param alreadyScaled      this projectile or effect was scaled already
     * @param cancelledByCombat  combat admission cancelled the hit
     */
    public static boolean isLegalSource(boolean fromOwner, boolean alreadyScaled,
                                        boolean cancelledByCombat) {
        return fromOwner && !alreadyScaled && !cancelledByCombat;
    }
}