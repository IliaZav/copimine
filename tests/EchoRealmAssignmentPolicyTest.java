package me.copimine.endevent.domain.wave7;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Echo owners remain in the arena, with deterministic assignments. */
public final class EchoRealmAssignmentPolicyTest {
    public static void main(String[] args) {
        EchoRealmAssignmentPolicyTest test = new EchoRealmAssignmentPolicyTest();
        test.everyOwnerFightsInArena();
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

    private void everyOwnerFightsInArena() {
        for (int size = 1; size <= EchoRealmAssignmentPolicy.MAX_PARTY; size++) {
            for (int threshold : new int[] {2, 15, 20}) {
                var split = EchoRealmAssignmentPolicy.assign(roster(size), threshold);
                check(split.count(EchoRealmAssignmentPolicy.Realm.ORDINARY) == size,
                        "every owner must remain on the arena at size " + size);
                check(!split.limboUsed(), "limbo must not be used");
            }
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
