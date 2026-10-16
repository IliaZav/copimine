package me.copimine.endevent.domain;

import java.util.Map;
import java.util.UUID;

/** Rate limit and expiry rules for the horizontal root from elite Spider Web Shot. */
public final class SpiderRootControlPolicy {
    public static final int REAPPLY_IMMUNITY_TICKS = 60;

    private SpiderRootControlPolicy() { }

    public static boolean canApply(long nowTick, long nextAllowedTick) {
        if (nowTick < 0L || nextAllowedTick < 0L) {
            throw new IllegalArgumentException("ticks must not be negative");
        }
        return nowTick >= nextAllowedTick;
    }

    public static long nextAllowedTick(long applicationTick) {
        if (applicationTick < 0L) {
            throw new IllegalArgumentException("applicationTick must not be negative");
        }
        return Math.addExact(applicationTick, REAPPLY_IMMUNITY_TICKS);
    }

    /** Remove all root and cooldown state for a player at a lifecycle boundary. */
    public static void clearPlayerState(UUID playerId,
                                        Map<UUID, Long> rootedUntil,
                                        Map<UUID, Long> nextApplyTick,
                                        Map<UUID, SpiderEffectLeasePolicy.Owner> owners) {
        if (playerId == null) return;
        if (rootedUntil != null) rootedUntil.remove(playerId);
        if (nextApplyTick != null) nextApplyTick.remove(playerId);
        if (owners != null) owners.remove(playerId);
    }
}
