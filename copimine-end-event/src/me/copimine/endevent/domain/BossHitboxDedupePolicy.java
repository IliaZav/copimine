package me.copimine.endevent.domain;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded, generation-scoped accepted-hit dedupe for the composite rig. */
public final class BossHitboxDedupePolicy {
    public static final long ENTRY_TTL_MILLIS = 500L;
    public static final int DEFAULT_MAX_ENTRIES = 128;

    private final int maxEntries;
    private final Map<String, Long> acceptedUntil = new LinkedHashMap<>();
    private long generation = Long.MIN_VALUE;

    public BossHitboxDedupePolicy() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public BossHitboxDedupePolicy(int maxEntries) {
        this.maxEntries = Math.max(1, Math.min(DEFAULT_MAX_ENTRIES, maxEntries));
    }

    /**
     * Accept an attack identity at most once for the active encounter
     * generation.  Switching generation atomically discards all old keys.
     */
    public boolean accept(String attackIdentity, long encounterGeneration, long nowMillis) {
        if (attackIdentity == null || attackIdentity.isBlank()) {
            return false;
        }
        if (generation != encounterGeneration) {
            acceptedUntil.clear();
            generation = encounterGeneration;
        }
        purge(nowMillis);
        if (acceptedUntil.containsKey(attackIdentity)) {
            return false;
        }
        while (acceptedUntil.size() >= maxEntries) {
            Iterator<String> iterator = acceptedUntil.keySet().iterator();
            if (!iterator.hasNext()) {
                break;
            }
            iterator.next();
            iterator.remove();
        }
        long expiry = nowMillis > Long.MAX_VALUE - ENTRY_TTL_MILLIS
                ? Long.MAX_VALUE : nowMillis + ENTRY_TTL_MILLIS;
        acceptedUntil.put(attackIdentity, expiry);
        return true;
    }

    public void purge(long nowMillis) {
        Iterator<Map.Entry<String, Long>> iterator = acceptedUntil.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (entry.getValue() <= nowMillis) {
                iterator.remove();
            }
        }
    }

    public void clear() {
        acceptedUntil.clear();
        generation = Long.MIN_VALUE;
    }

    public int size() {
        return acceptedUntil.size();
    }

    public int maxEntries() {
        return maxEntries;
    }

    public long generation() {
        return generation;
    }
}
