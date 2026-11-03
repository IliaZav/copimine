package me.copimine.endevent.domain.wave7;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * ECHO-002: freeze a balanced ordinary/limbo split when the Echo stage is admitted.
 *
 * <p>Every admitted participant still fights a real personal copy at the same
 * time; the split only separates what two groups of players SEE and hear, it
 * never reduces the number of copies the server runs. Up to nine participants
 * the whole party shares the ordinary region, because splitting four duels into
 * two half-empty rooms buys nothing. From ten participants the roster is cut in
 * half with a difference of at most one, ordinary taking the larger half.</p>
 *
 * <p>The allocation is deterministic: the roster is ordered by UUID string, so a
 * restart recomputes the identical split and a rejoin cannot move anybody into
 * the "easier" realm.</p>
 */
public final class EchoRealmAssignmentPolicy {
    /** Below this party size both realms are pointless; everyone fights ordinary. */
    public static final int LIMBO_THRESHOLD = 10;
    public static final int MAX_PARTY = 20;

    private EchoRealmAssignmentPolicy() { }

    /** Which presentation region one admitted owner fights in. */
    public enum Realm {
        /** The authored arena, visible to everyone standing in it. */
        ORDINARY,
        /** The mirrored play area where the second half fights, hidden from the first. */
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
     * Cut the admitted roster in half at admission time.
     *
     * <p>The result is stable for a given roster: ordinary keeps the larger half
     * so the authored arena is never the emptier room (ECHO-004 asks for equal
     * affordances, not for equal head counts, and the arena is the room with the
     * authored cover).</p>
     */
    public static Split assign(Collection<UUID> admitted) {
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

        int limboCount = ordered.size() < LIMBO_THRESHOLD ? 0 : ordered.size() / 2;
        List<Assignment> assignments = new ArrayList<>(ordered.size());
        for (int ordinal = 0; ordinal < ordered.size(); ordinal++) {
            // The tail of the ordered roster goes to limbo, so the ordinary half
            // is exactly the first (size - limboCount) owners and no seat moves
            // when one more participant is admitted mid-stage.
            Realm realm = ordinal < ordered.size() - limboCount ? Realm.ORDINARY : Realm.LIMBO;
            assignments.add(new Assignment(ordered.get(ordinal), realm, ordinal));
        }
        return new Split(assignments);
    }
}
