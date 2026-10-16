package me.copimine.endevent.domain;

import java.util.Set;
import java.util.Map;
import java.util.UUID;

/** Per-cloud damage admission so a lingering cloud cannot repeatedly damage one player. */
public final class DragonBreathCloudPolicy {
    private DragonBreathCloudPolicy() { }

    public record Hit(UUID cloudId, UUID playerId) { }

    /** Reserve one active Dragon Breath cloud for each caster without refreshing it. */
    public static boolean claimCloud(Map<UUID, UUID> activeCloudsByCaster,
                                     UUID casterId, UUID cloudId) {
        if (activeCloudsByCaster == null || casterId == null || cloudId == null) return false;
        return activeCloudsByCaster.putIfAbsent(casterId, cloudId) == null;
    }

    /** Release the caster slot only when this exact cloud expires or is removed. */
    public static void releaseCloud(Map<UUID, UUID> activeCloudsByCaster, UUID cloudId) {
        if (activeCloudsByCaster == null || cloudId == null) return;
        activeCloudsByCaster.entrySet().removeIf(entry -> cloudId.equals(entry.getValue()));
    }

    public static boolean admitHit(Set<Hit> hits, UUID cloudId, UUID playerId) {
        if (hits == null || cloudId == null || playerId == null) return false;
        return hits.add(new Hit(cloudId, playerId));
    }

    public static boolean releaseHit(Set<Hit> hits, UUID cloudId, UUID playerId) {
        return hits != null && cloudId != null && playerId != null
                && hits.remove(new Hit(cloudId, playerId));
    }

    /** Admit only eligible players in the cloud's world and radius. */
    public static boolean shouldSchedulePlayerHit(boolean playerTarget, boolean targetAllowed,
                                                 boolean sameWorld, boolean insideRadius) {
        return playerTarget && targetAllowed && sameWorld && insideRadius;
    }

    /** Commit the debuff only after the normal damage event is observed and accepted. */
    public static boolean shouldApplyEffects(boolean damageEventObserved,
                                             boolean damageEventCancelled) {
        return damageEventObserved && !damageEventCancelled;
    }

    public static void releaseCloud(Set<Hit> hits, UUID cloudId) {
        if (hits == null || cloudId == null) return;
        hits.removeIf(hit -> cloudId.equals(hit.cloudId()));
    }
}
