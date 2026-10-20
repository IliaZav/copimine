package me.copimine.endevent.domain;

/** Admission rules shared by player-only wave projectile area effects. */
public final class WaveProjectileTargetPolicy {
    private WaveProjectileTargetPolicy() {
    }

    public static boolean allowsPlayerAoe(boolean playerTarget,
                                          boolean ownerAllowsTarget,
                                          boolean sameWorld,
                                          boolean withinRadius,
                                          boolean clearBlockSight) {
        return playerTarget && ownerAllowsTarget && sameWorld && withinRadius && clearBlockSight;
    }
}
