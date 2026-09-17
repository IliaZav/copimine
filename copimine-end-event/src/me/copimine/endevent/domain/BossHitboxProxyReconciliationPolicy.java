package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Pure expected/live reconciliation for a generation-owned boss proxy rig. */
public final class BossHitboxProxyReconciliationPolicy {
    private static final Comparator<Key> KEY_ORDER = Comparator
            .comparingInt((Key key) -> key.partId().ordinal())
            .thenComparingInt(Key::segmentIndex);

    private BossHitboxProxyReconciliationPolicy() {
    }

    public static Set<Key> expectedKeys(BossHitboxProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("boss hitbox profile is required");
        }
        List<Key> keys = new ArrayList<>();
        for (BossHitboxProfile.Part part : profile.parts()) {
            keys.add(new Key(part.id(), part.segmentIndex()));
        }
        keys.sort(KEY_ORDER);
        return Collections.unmodifiableSet(new LinkedHashSet<>(keys));
    }

    public static Result reconcile(Collection<Key> expected, Collection<Key> live) {
        Set<Key> expectedKeys = normalize(expected, "expected");
        Set<Key> liveKeys = normalize(live == null ? List.of() : live, "live");
        LinkedHashSet<Key> missing = new LinkedHashSet<>(expectedKeys);
        missing.removeAll(liveKeys);
        LinkedHashSet<Key> stale = new LinkedHashSet<>(liveKeys);
        stale.removeAll(expectedKeys);
        return new Result(missing, stale);
    }

    private static Set<Key> normalize(Collection<Key> values, String name) {
        if (values == null) {
            throw new IllegalArgumentException(name + " keys are required");
        }
        List<Key> sorted = new ArrayList<>();
        for (Key key : values) {
            if (key == null) {
                throw new IllegalArgumentException(name + " keys contain null");
            }
            sorted.add(key);
        }
        sorted.sort(KEY_ORDER);
        return new LinkedHashSet<>(sorted);
    }

    public record Result(Set<Key> missing, Set<Key> stale) {
        public Result {
            missing = immutableOrdered(missing, "missing");
            stale = immutableOrdered(stale, "stale");
        }

        public boolean isHealthy() {
            return missing.isEmpty() && stale.isEmpty();
        }

        public boolean requiresRebuild() {
            return !isHealthy();
        }

        private static Set<Key> immutableOrdered(Set<Key> values, String name) {
            if (values == null) {
                throw new IllegalArgumentException(name + " keys are required");
            }
            List<Key> sorted = new ArrayList<>(values);
            sorted.sort(KEY_ORDER);
            return Collections.unmodifiableSet(new LinkedHashSet<>(sorted));
        }
    }

    public record Key(BossHitboxProfile.PartId partId, int segmentIndex) {
        public Key {
            if (partId == null) {
                throw new IllegalArgumentException("proxy part id is required");
            }
            if (segmentIndex < 0) {
                throw new IllegalArgumentException("proxy segment index must not be negative");
            }
        }
    }
}
