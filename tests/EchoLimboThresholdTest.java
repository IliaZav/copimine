package me.copimine.endevent.domain.wave7;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Proves the Echo split cuts at ten by default and at any size when forced. */
public final class EchoLimboThresholdTest {
    private static int failures;

    public static void main(String[] args) {
        theSpecThresholdIsTen();
        fromTenThePartyIsCutInHalf();
        theForcedThresholdCutsASmallParty();
        aOnePlayerSecondHalfIsRefused();
        theSplitIsStableForTheSameRoster();
        nobodyIsLostAndNobodyIsDuplicated();
        System.out.println(failures == 0 ? "EchoLimboThresholdTest: PASS"
                : "EchoLimboThresholdTest: FAIL " + failures);
        if (failures > 0) System.exit(1);
    }

    private static void theSpecThresholdIsTen() {
        check("spec threshold is ten", EchoRealmAssignmentPolicy.LIMBO_THRESHOLD == 10);
        check("nine players stay together",
                !EchoRealmAssignmentPolicy.assign(roster(9)).limboUsed());
    }

    private static void fromTenThePartyIsCutInHalf() {
        for (int size : new int[]{10, 11, 20}) {
            var split = EchoRealmAssignmentPolicy.assign(roster(size));
            long ordinary = split.count(EchoRealmAssignmentPolicy.Realm.ORDINARY);
            long limbo = split.count(EchoRealmAssignmentPolicy.Realm.LIMBO);
            check("party " + size + " uses limbo", split.limboUsed());
            check("party " + size + " differs by at most one", Math.abs(ordinary - limbo) <= 1);
            check("party " + size + " keeps the arena larger", ordinary >= limbo);
        }
    }

    private static void theForcedThresholdCutsASmallParty() {
        var split = EchoRealmAssignmentPolicy.assign(roster(4), 2);
        check("forced split used", split.limboUsed());
        check("forced split divides four", split.count(EchoRealmAssignmentPolicy.Realm.LIMBO) == 2);
    }

    private static void aOnePlayerSecondHalfIsRefused() {
        boolean refused = false;
        try {
            EchoRealmAssignmentPolicy.assign(roster(2), 1);
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        check("threshold of one refused", refused);
    }

    private static void theSplitIsStableForTheSameRoster() {
        List<UUID> roster = roster(11);
        var first = EchoRealmAssignmentPolicy.assign(roster);
        var second = EchoRealmAssignmentPolicy.assign(new ArrayList<>(roster));
        check("split is deterministic", first.assignments().equals(second.assignments()));
    }

    private static void nobodyIsLostAndNobodyIsDuplicated() {
        List<UUID> roster = roster(20);
        var split = EchoRealmAssignmentPolicy.assign(roster);
        Set<UUID> owners = new HashSet<>();
        for (var assignment : split.assignments()) owners.add(assignment.owner());
        check("every owner assigned once", owners.size() == roster.size());
        check("no phantom owners", owners.equals(new HashSet<>(roster)));
    }

    private static List<UUID> roster(int size) {
        List<UUID> users = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            users.add(new UUID(0L, 1000L + i));
        }
        return users;
    }

    private static void check(String label, boolean condition) {
        if (!condition) {
            failures++;
            System.out.println("FAIL: " + label);
        }
    }
}
