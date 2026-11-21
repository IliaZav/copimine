package me.copimine.endevent.domain;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-viewer, event-generation-scoped memory of the last visual binding that
 * actually reached a viewer.
 *
 * <p>The boss tick used to re-broadcast every owned entity's {@code END_ENTITY_BIND}
 * to every eligible viewer once per second even when nothing had changed. With
 * dozens of defensive and small Kagune plus the rest of the roster this could
 * exceed the V4 sender's application budget, so genuine action frames (contact,
 * cast, damage, retract) waited behind cosmetic repeats.</p>
 *
 * <p>This cache turns the periodic pass into a delta: a binding is only sent when
 * the viewer has never seen that exact signature, or when the signature changed
 * (for example the Ram's armour stage). It never suppresses entity visibility;
 * the binding still happens on spawn, on late join and on generation change, and
 * {@link #clear()} plus a forced pass remain the bounded loss-recovery path.</p>
 *
 * <p>The map is access-ordered so eviction always drops the least recently
 * confirmed binding. Entries are bounded both globally and per viewer, so a
 * pathological stream of entity ids cannot grow memory without limit. This
 * class holds no Bukkit state and is intentionally unit-testable.</p>
 */
public final class ClientBindingSnapshotCache {
    /** Default global ceiling; comfortably above 20 viewers x 40 actors. */
    public static final int DEFAULT_MAX_ENTRIES = 4096;
    /** Default per-viewer ceiling; a viewer never needs thousands of binds. */
    public static final int DEFAULT_MAX_KEYS_PER_VIEWER = 512;

    private final int maxEntries;
    private final int maxKeysPerViewer;
    private final LinkedHashMap<Key, String> delivered =
            new LinkedHashMap<>(256, 0.75F, true);

    private long generation = Long.MIN_VALUE;
    private long deliveredCount;
    private long coalescedCount;
    private long failedCount;
    private long evictedCount;
    private long resetCount;

    public ClientBindingSnapshotCache() {
        this(DEFAULT_MAX_ENTRIES, DEFAULT_MAX_KEYS_PER_VIEWER);
    }

    public ClientBindingSnapshotCache(int maxEntries, int maxKeysPerViewer) {
        this.maxEntries = Math.max(1, maxEntries);
        this.maxKeysPerViewer = Math.max(1, maxKeysPerViewer);
    }

    /**
     * Drops every binding when the event generation changes.
     *
     * <p>A generation change invalidates all previous ownership, so no old
     * signature may be treated as still delivered.</p>
     */
    public synchronized void observeGeneration(long currentGeneration) {
        if (currentGeneration == generation) {
            return;
        }
        generation = currentGeneration;
        delivered.clear();
        resetCount++;
    }

    /**
     * Whether this exact signature still has to be delivered to the viewer.
     *
     * @return {@code true} for a first delivery or a changed signature;
     *         {@code false} when the viewer already holds this exact state
     */
    public synchronized boolean shouldSend(UUID viewer, UUID entity, String signature) {
        if (viewer == null || entity == null || signature == null || signature.isEmpty()) {
            return true;
        }
        String last = delivered.get(new Key(viewer, entity));
        return last == null || !last.equals(signature);
    }

    /** Records a signature that was actually delivered, not merely attempted. */
    public synchronized void recordDelivered(UUID viewer, UUID entity, String signature) {
        if (viewer == null || entity == null || signature == null || signature.isEmpty()) {
            return;
        }
        Key key = new Key(viewer, entity);
        delivered.put(key, signature);
        deliveredCount++;
        trimViewer(viewer, key);
        trimGlobal(key);
    }

    public synchronized void recordCoalesced() {
        coalescedCount++;
    }

    public synchronized void recordFailed() {
        failedCount++;
    }

    /** Forgets one entity for every viewer, e.g. on despawn or unbind. */
    public synchronized void invalidateEntity(UUID entity) {
        if (entity == null) {
            return;
        }
        delivered.keySet().removeIf(key -> key.entity().equals(entity));
    }

    /**
     * Forgets everything one viewer holds.
     *
     * <p>Called before a forced full resync (late join, resource-pack reload,
     * world change) so the snapshot is not mistaken for a delivered delta.</p>
     */
    public synchronized void invalidateViewer(UUID viewer) {
        if (viewer == null) {
            return;
        }
        delivered.keySet().removeIf(key -> key.viewer().equals(viewer));
    }

    /** Clears every binding without changing the generation. */
    public synchronized void clear() {
        delivered.clear();
    }

    public synchronized int size() {
        return delivered.size();
    }

    public synchronized long generation() {
        return generation;
    }

    public synchronized Metrics metrics() {
        return new Metrics(deliveredCount, coalescedCount, failedCount,
                evictedCount, resetCount, delivered.size());
    }

    private void trimViewer(UUID viewer, Key keep) {
        int count = 0;
        for (Key key : delivered.keySet()) {
            if (key.viewer().equals(viewer)) {
                count++;
            }
        }
        if (count <= maxKeysPerViewer) {
            return;
        }
        Iterator<Key> iterator = delivered.keySet().iterator();
        while (iterator.hasNext() && count > maxKeysPerViewer) {
            Key candidate = iterator.next();
            if (candidate.equals(keep) || !candidate.viewer().equals(viewer)) {
                continue;
            }
            iterator.remove();
            count--;
            evictedCount++;
        }
    }

    private void trimGlobal(Key keep) {
        Iterator<Key> iterator = delivered.keySet().iterator();
        while (delivered.size() > maxEntries && iterator.hasNext()) {
            Key candidate = iterator.next();
            if (candidate.equals(keep)) {
                continue;
            }
            iterator.remove();
            evictedCount++;
        }
    }

    private record Key(UUID viewer, UUID entity) { }

    /** Monotonic counters plus the current entry count, for diagnostics. */
    public record Metrics(long delivered, long coalesced, long failed,
                          long evicted, long resets, int entries) {
    }
}