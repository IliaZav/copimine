package me.copimine.endevent.domain.wave7;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** ECHO-002/ECHO-003: the frozen ordinary/limbo split must match the spec table. */
public final class EchoRealmAssignmentPolicyTest {
    public static void main(String[] args) {
        EchoRealmAssignmentPolicyTest test = new EchoRealmAssignmentPolicyTest();
        test.specTable();
        test.stableAcrossRejoin();
        test.perceptionIsRealmScoped();
        test.rejectsInvalidRosters();
        System.out.println("EchoRealmAssignmentPolicyTest OK");
    }

    private void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private List<UUID> roster(int size) {
        List<UUID> owners = new ArrayList<>(size);
        for (int i = 0; i < size; i++) owners.add(new UUID(0L, 1000L + i));
        return owners;
    }

    private void expectRejected(Runnable action, String message) {
        try {
            action.run();
            throw new IllegalStateException("expected rejection: " + message);
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    /**
     * ECHO-002 table, superseded 2026-11-03: 2-14 all ordinary, then an even
     * split taking the larger half.
     *
     * <p>The user raised the authored threshold from ten to fifteen. The old rows
     * for ten and eleven asserted a split that the product no longer wants, so
     * they are kept visible with their new expectation rather than deleted.</p>
     */
    private void specTable() {
        int[][] table = {
                {2, 2, 0}, {9, 9, 0}, {10, 10, 0}, {11, 11, 0}, {14, 14, 0},
                {15, 8, 7}, {16, 8, 8}, {20, 10, 10}
        };
        for (int[] row : table) {
            int party = row[0];
            EchoRealmAssignmentPolicy.Split split = EchoRealmAssignmentPolicy.assign(roster(party));
            check(split.count(EchoRealmAssignmentPolicy.Realm.ORDINARY) == row[1],
                    "ordinary count wrong at " + party);
            check(split.count(EchoRealmAssignmentPolicy.Realm.LIMBO) == row[2],
                    "limbo count wrong at " + party);
            check(split.assignments().size() == party, "assignment count wrong at " + party);
        }
        // Difference of at most one for every party size.
        for (int party = 2; party <= EchoRealmAssignmentPolicy.MAX_PARTY; party++) {
            EchoRealmAssignmentPolicy.Split split = EchoRealmAssignmentPolicy.assign(roster(party));
            long ordinary = split.count(EchoRealmAssignmentPolicy.Realm.ORDINARY);
            long limbo = split.count(EchoRealmAssignmentPolicy.Realm.LIMBO);
            if (party < EchoRealmAssignmentPolicy.LIMBO_THRESHOLD) {
                // Below the threshold the split is intentionally unused, so the
                // "difference of at most one" rule does not apply at all.
                check(limbo == 0, "limbo used below the threshold at " + party);
                continue;
            }
            check(ordinary - limbo >= 0 && ordinary - limbo <= 1,
                    "unbalanced realms at " + party + ": " + ordinary + "/" + limbo);
            check(limbo > 0, "limbo unused at " + party);
        }
    }

    /** A death/rejoin must not move a participant to the other realm. */
    private void stableAcrossRejoin() {
        List<UUID> owners = roster(20);
        EchoRealmAssignmentPolicy.Split first = EchoRealmAssignmentPolicy.assign(owners);
        List<UUID> reversed = new ArrayList<>(owners);
        java.util.Collections.reverse(reversed);
        EchoRealmAssignmentPolicy.Split again = EchoRealmAssignmentPolicy.assign(reversed);
        check(first.assignments().equals(again.assignments()), "split depends on iteration order");
        for (UUID owner : owners) {
            check(first.realmOf(owner) == again.realmOf(owner), "realm changed for " + owner);
        }
        check(first.realmOf(new UUID(0L, 424242L)) == null, "stranger received a realm");
    }

    /** ECHO-003/ECHO-008: a realm never perceives the other realm's activity. */
    private void perceptionIsRealmScoped() {
        List<UUID> owners = roster(20);
        EchoRealmAssignmentPolicy.Split split = EchoRealmAssignmentPolicy.assign(owners);
        for (UUID a : owners) {
            for (UUID b : owners) {
                boolean sameRealm = split.realmOf(a) == split.realmOf(b);
                check(split.mayPerceive(a, b) == sameRealm, "perception crosses realms");
            }
        }
        UUID ordinary = owners.stream()
                .filter(o -> split.realmOf(o) == EchoRealmAssignmentPolicy.Realm.ORDINARY)
                .findFirst().orElseThrow();
        UUID limbo = owners.stream()
                .filter(o -> split.realmOf(o) == EchoRealmAssignmentPolicy.Realm.LIMBO)
                .findFirst().orElseThrow();
        check(!split.mayPerceive(ordinary, limbo), "ordinary sees limbo");
        check(!split.mayPerceive(limbo, ordinary), "limbo sees ordinary");
        check(!split.mayPerceive(ordinary, new UUID(0L, 999999L)), "stranger is perceived");
    }

    private void rejectsInvalidRosters() {
        expectRejected(() -> EchoRealmAssignmentPolicy.assign(null), "null roster");
        expectRejected(() -> EchoRealmAssignmentPolicy.assign(List.of()), "empty roster");
        List<UUID> tooMany = roster(EchoRealmAssignmentPolicy.MAX_PARTY + 1);
        expectRejected(() -> EchoRealmAssignmentPolicy.assign(tooMany), "oversized roster");
        List<UUID> duplicate = roster(4);
        duplicate.add(duplicate.get(0));
        expectRejected(() -> EchoRealmAssignmentPolicy.assign(duplicate), "duplicate owner");
        List<UUID> withNull = new ArrayList<>(roster(3));
        withNull.add(null);
        expectRejected(() -> EchoRealmAssignmentPolicy.assign(withNull), "null owner");
    }
}
