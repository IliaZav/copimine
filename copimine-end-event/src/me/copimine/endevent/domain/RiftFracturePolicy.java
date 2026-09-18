package me.copimine.endevent.domain;

/**
 * Bounded, non-persistent hazards used by the official boss RIFT phase.
 *
 * The Bukkit adapter owns locations, particles and damage events.  This class
 * owns only the party scaling and the timing contract so the phase cannot
 * accidentally turn into an unbounded projectile or block-mutation system.
 */
public final class RiftFracturePolicy {
    public static final int MAX_ACTIVE = 6;
    public static final int TELEGRAPH_TICKS = 30;
    public static final int ACTIVE_TICKS = 60;
    public static final int BATCH_COOLDOWN_TICKS = 80;
    public static final double RADIUS_BLOCKS = 2.5D;
    public static final double DAMAGE = 6.0D;

    private RiftFracturePolicy() {
    }

    /** Keep the RIFT phase readable while adding bounded target pressure. */
    public static int countForPlayers(int players) {
        int safe = Math.max(0, Math.min(20, players));
        if (safe <= 0) {
            return 0;
        }
        if (safe <= 4) {
            return 3;
        }
        if (safe <= 7) {
            return 4;
        }
        if (safe <= 10) {
            return 5;
        }
        return MAX_ACTIVE;
    }

    public static boolean canDamage(long nowTick, long activeAtTick,
                                    long expiresAtTick, boolean alreadyDamaged) {
        return !alreadyDamaged && nowTick >= activeAtTick && nowTick < expiresAtTick;
    }

    public record Profile(int count, int telegraphTicks, int activeTicks,
                          int cooldownTicks, double radius, double damage) {
        public Profile {
            count = Math.max(0, Math.min(MAX_ACTIVE, count));
            telegraphTicks = Math.max(1, telegraphTicks);
            activeTicks = Math.max(1, activeTicks);
            cooldownTicks = Math.max(1, cooldownTicks);
            radius = Math.max(0.5D, Math.min(5.0D, radius));
            damage = Math.max(0.0D, Math.min(20.0D, damage));
        }
    }

    public static Profile profileForPlayers(int players) {
        return new Profile(countForPlayers(players), TELEGRAPH_TICKS, ACTIVE_TICKS,
                BATCH_COOLDOWN_TICKS, RADIUS_BLOCKS, DAMAGE);
    }
}
