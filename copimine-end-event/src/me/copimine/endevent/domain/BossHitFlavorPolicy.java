package me.copimine.endevent.domain;

/**
 * Owner request: hits should read as crisp, critical or area damage.
 *
 * <p>This is a <b>presentation</b> classifier only. The server has already
 * finalized the damage before a flavour is chosen, so nothing here can add,
 * remove, multiply or reject damage. It exists so the impact burst, the sound
 * pitch and the recoil can match the kind of hit the player actually made,
 * instead of every hit rendering one identical spark.</p>
 */
public final class BossHitFlavorPolicy {

    /** The two independent presentation flags. */
    public record Flavor(boolean critical, boolean splash) {
    }

    private static final Flavor NONE = new Flavor(false, false);
    private static final Flavor CRITICAL = new Flavor(true, false);
    private static final Flavor SPLASH = new Flavor(false, true);

    private BossHitFlavorPolicy() {
    }

    public static Flavor none() {
        return NONE;
    }

    /**
     * Melee flavour from the real attacker state.
     *
     * @param playerFalling the attacker is mid-air falling (vanilla crit)
     * @param sweepAttack   the damage cause is a sweep (area) attack
     */
    public static Flavor melee(boolean playerFalling, boolean sweepAttack) {
        if (sweepAttack) {
            // A sweep is area damage by construction; it is never a crit even
            // when the attacker happens to be falling.
            return SPLASH;
        }
        return playerFalling ? CRITICAL : NONE;
    }

    /**
     * Projectile flavour from the real projectile state.
     *
     * @param arrowCritical the arrow is a vanilla critical arrow
     * @param areaOfEffect  the projectile is an area cloud / splash potion
     */
    public static Flavor projectile(boolean arrowCritical, boolean areaOfEffect) {
        if (areaOfEffect) {
            return SPLASH;
        }
        return arrowCritical ? CRITICAL : NONE;
    }

    /** A cosmetic spark count, bounded so a raid cannot flood the client. */
    public static int extraSparks(Flavor flavor) {
        if (flavor == null) {
            return 0;
        }
        if (flavor.splash()) {
            return 10;
        }
        return flavor.critical() ? 14 : 0;
    }

    /** Splash ring radius in blocks, zero for a single-target hit. */
    public static double splashRadius(Flavor flavor) {
        return flavor != null && flavor.splash() ? 1.7D : 0.0D;
    }
}
