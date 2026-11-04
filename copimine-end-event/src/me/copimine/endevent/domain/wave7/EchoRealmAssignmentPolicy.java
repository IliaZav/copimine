package me.copimine.endevent.domain.wave7;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Assign every admitted Echo owner to the ordinary arena.
 *
 * <p>The allocation is deterministic: the roster is ordered by UUID string, so a
 * restart recomputes the identical split and a rejoin cannot move anybody into
 * the "easier" realm.</p>
 */
public final class EchoRealmAssignmentPolicy {
    /** Legacy diagnostic value retained for compatibility; it cannot enable limbo. */
    public static final int LIMBO_THRESHOLD = 15;
    public static final int MAX_PARTY = 20;

    private EchoRealmAssignmentPolicy() { }

    /** Which presentation region one admitted owner fights in. */
    public enum Realm {
        /** The authored arena, visible to everyone standing in it. */
        ORDINARY,
        /**
         * Removed from the active design. Kept only so persisted snapshots from
         * before the removal still parse; nothing may ever assign it again.
         */
        LIMBO
    }

    /** One admitted owner's frozen realm. */
    public record Assignment(UUID owner, Realm realm, int ordinal) {
        public Assignment {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(realm, "realm");
            if (ordinal < 0) throw new IllegalArgumentException("invalid Echo realm ordinal");
        }
    }

    /** The frozen split of one Echo stage. */
    public record Split(List<Assignment> assignments) {
        public Split {
            if (assignments == null) throw new IllegalArgumentException("assignments required");
            assignments = List.copyOf(assignments);
        }

        public long count(Realm realm) {
            return assignments.stream().filter(a -> a.realm() == realm).count();
        }

        /** The realm of one owner, or null when the owner was never admitted. */
        public Realm realmOf(UUID owner) {
            for (Assignment assignment : assignments) {
                if (assignment.owner().equals(owner)) return assignment.realm();
            }
            return null;
        }

        /**
         * ECHO-003: whether {@code viewer} may perceive {@code subject}'s duel.
         *
         * <p>Same realm only. An owner never sees the other group's players,
         * copies, projectiles or sounds.</p>
         */
        public boolean mayPerceive(UUID viewer, UUID subject) {
            Realm viewerRealm = realmOf(viewer);
            Realm subjectRealm = realmOf(subject);
            return viewerRealm != null && viewerRealm == subjectRealm;
        }

        public boolean limboUsed() {
            return count(Realm.LIMBO) > 0;
        }
    }

    /**
     * The one supported assignment now that Limbo is removed from the design:
     * every admitted owner is placed in the ordinary arena.
     */
    public static Split ordinaryOnly(Collection<UUID> admitted) {
        return assign(admitted);
    }

    /** Assign all admitted owners to the arena in deterministic order. */
    public static Split assign(Collection<UUID> admitted) {
        return assign(admitted, LIMBO_THRESHOLD);
    }

    /** Retain the legacy threshold argument for callers; it no longer enables limbo. */
    public static Split assign(Collection<UUID> admitted, int limboThreshold) {
        if (limboThreshold < 2 || limboThreshold > MAX_PARTY) {
            throw new IllegalArgumentException("Echo limbo threshold must be in [2, " + MAX_PARTY + "]");
        }
        if (admitted == null || admitted.isEmpty()) {
            throw new IllegalArgumentException("admitted Echo roster required");
        }
        if (admitted.size() > MAX_PARTY) {
            throw new IllegalArgumentException("Echo party exceeds " + MAX_PARTY);
        }
        if (admitted.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("null Echo owner in roster");
        }
        List<UUID> ordered = new ArrayList<>(admitted);
        if (new HashSet<>(ordered).size() != ordered.size()) {
            throw new IllegalArgumentException("duplicate Echo owner in roster");
        }
        ordered.sort(Comparator.comparing(UUID::toString));

        List<Assignment> assignments = new ArrayList<>(ordered.size());
        for (int ordinal = 0; ordinal < ordered.size(); ordinal++) {
            // Every owner fights their own copy on the arena, regardless of
            // roster size or the legacy diagnostic threshold override.
            assignments.add(new Assignment(ordered.get(ordinal), Realm.ORDINARY, ordinal));
        }
        return new Split(assignments);
    }
}
