package me.copimine.endevent.domain.wave7;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** The retired limbo threshold must never move Echo owners out of the arena. */
public final class EchoLimboThresholdTest {
    private static int failures;

    public static void main(String[] args) {
        theSpecThresholdIsFifteen();
        largePartiesStayInTheArena();
        tenToFourteenPlayersStayInOneArena();
        theForcedThresholdCannotEnableLimbo();
        aOnePlayerSecondHalfIsRefused();
        theSplitIsStableForTheSameRoster();
        nobodyIsLostAndNobodyIsDuplicated();
        System.out.println(failures == 0 ? "EchoLimboThresholdTest: PASS"
                : "EchoLimboThresholdTest: FAIL " + failures);
        if (failures > 0) System.exit(1);
    }

    /**
     * SUPERSEDED 2026-11-03 (user request): the split started at ten players.
     * The user asked for fifteen, because ten to fourteen still reads as one
     * fight and halving it only produced two half-empty arenas.
     */
    private static void theSpecThresholdIsFifteen() {
        check("spec threshold is fifteen", EchoRealmAssignmentPolicy.LIMBO_THRESHOLD == 15);
        check("fourteen players stay together",
                !EchoRealmAssignmentPolicy.assign(roster(14)).limboUsed());
    }

    /**
     * The band the user explicitly named: ten, eleven and fourteen are the
     * sizes that used to split and must now fight as a single party.
     */
    private static void tenToFourteenPlayersStayInOneArena() {
        for (int size = 10; size <= 14; size++) {
            var split = EchoRealmAssignmentPolicy.assign(roster(size));
            check("party " + size + " stays in one arena", !split.limboUsed());
            check("party " + size + " is all ordinary",
                    split.count(EchoRealmAssignmentPolicy.Realm.LIMBO) == 0);
        }
    }

    private static void largePartiesStayInTheArena() {
        for (int size : new int[]{15, 16, 20}) {
            var split = EchoRealmAssignmentPolicy.assign(roster(size));
            long ordinary = split.count(EchoRealmAssignmentPolicy.Realm.ORDINARY);
            check("party " + size + " does not use limbo", !split.limboUsed());
            check("party " + size + " remains in the arena", ordinary == size);
        }
    }

    private static void theForcedThresholdCannotEnableLimbo() {
        var split = EchoRealmAssignmentPolicy.assign(roster(4), 2);
        check("forced split disabled", !split.limboUsed());
        check("all four remain in the arena", split.count(EchoRealmAssignmentPolicy.Realm.ORDINARY) == 4);
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
