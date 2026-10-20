package me.copimine.endevent.domain;

import java.util.Map;
import java.util.UUID;

/** Caps a multi-arrow elite volley to one explosive hit per volley and player. */
public final class ExplosiveVolleyPolicy {
    /** Slightly longer than the five-second owned-arrow lifetime. */
    public static final long TARGET_HIT_WINDOW_MILLIS = 6_000L;

    private ExplosiveVolleyPolicy() { }

    public record TargetKey(UUID volleyId, UUID playerId) { }

    /**
     * Admit the first explosion in a volley for a player. The stable volley
     * identity is carried by each arrow, so a missing shooter does not split a
     * triple shot into independent damage transactions.
     */
    public static boolean admitTarget(Map<TargetKey, Long> lastHitAtMillis,
                                      UUID volleyId, UUID playerId, long nowMillis) {
        if (lastHitAtMillis == null || volleyId == null || playerId == null || nowMillis < 0L) {
            return false;
        }
        lastHitAtMillis.entrySet().removeIf(entry -> nowMillis >= entry.getValue()
                && nowMillis - entry.getValue() >= TARGET_HIT_WINDOW_MILLIS);
        TargetKey key = new TargetKey(volleyId, playerId);
        Long previous = lastHitAtMillis.get(key);
        if (previous != null && (nowMillis < previous
                || nowMillis - previous < TARGET_HIT_WINDOW_MILLIS)) {
            return false;
        }
        lastHitAtMillis.put(key, nowMillis);
        return true;
    }
}
