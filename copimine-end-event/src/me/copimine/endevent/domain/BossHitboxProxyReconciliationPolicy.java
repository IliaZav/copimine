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
        List<Key> expectedValues = normalize(expected, "expected");
        List<Key> liveValues = normalize(live == null ? List.of() : live, "live");
        Set<Key> expectedKeys = unique(expectedValues);
        Set<Key> liveKeys = unique(liveValues);
        LinkedHashSet<Key> missing = new LinkedHashSet<>(expectedKeys);
        missing.removeAll(liveKeys);
        LinkedHashSet<Key> stale = new LinkedHashSet<>(liveKeys);
        stale.removeAll(expectedKeys);
        return new Result(missing, stale, duplicates(liveValues), 0);
    }

    private static List<Key> normalize(Collection<Key> values, String name) {
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
        return List.copyOf(sorted);
    }

    private static Set<Key> unique(List<Key> values) {
        return new LinkedHashSet<>(values);
    }

    private static Set<Key> duplicates(List<Key> values) {
        LinkedHashSet<Key> seen = new LinkedHashSet<>();
        LinkedHashSet<Key> duplicate = new LinkedHashSet<>();
        for (Key value : values) {
            if (!seen.add(value)) {
                duplicate.add(value);
            }
        }
        return duplicate;
    }

    public record Result(Set<Key> missing, Set<Key> stale, Set<Key> duplicates,
                         int malformed) {
        public Result(Set<Key> missing, Set<Key> stale) {
            this(missing, stale, Set.of(), 0);
        }

        public Result {
            missing = immutableOrdered(missing, "missing");
            stale = immutableOrdered(stale, "stale");
            duplicates = immutableOrdered(duplicates, "duplicates");
            if (malformed < 0) {
                throw new IllegalArgumentException("malformed proxy count must not be negative");
            }
        }

        public boolean isHealthy() {
            return missing.isEmpty() && stale.isEmpty() && duplicates.isEmpty()
                    && malformed == 0;
        }

        public boolean requiresRebuild() {
            return !isHealthy();
        }

        public Result withMalformed(int count) {
            if (count < 0) {
                throw new IllegalArgumentException("malformed proxy count must not be negative");
            }
            return new Result(missing, stale, duplicates, count);
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
