package me.copimine.endevent.domain;

import java.util.List;
import java.util.UUID;

public final class WaveTwoOwnershipPolicyTest {
    public static void main(String[] args) {
        var event = new UUID(9L, 1L);
        var entity = new UUID(9L, 2L);
        var first = new UUID(1L, 1L);
        var second = new UUID(2L, 2L);
        var third = new UUID(3L, 3L);
        List<UUID> frozenRoster = List.of(first, second, third);

        UUID owner = WaveTwoOwnershipPolicy.initialOwner(event, 17L, entity, frozenRoster);
        check(frozenRoster.contains(owner), "an initial assignment belongs to the frozen squad roster");
        check(WaveTwoOwnershipPolicy.initialOwner(event, 17L, entity,
                        List.of(third, first, second)).equals(owner),
                "input roster order cannot change an assignment");
        check(WaveTwoOwnershipPolicy.initialOwner(event, 17L, entity, frozenRoster).equals(owner),
                "a restart can reconstruct an assignment from the persisted roster");

        check(WaveTwoOwnershipPolicy.allowsTarget(owner, owner, 17L, 17L),
                "the assigned player may be targeted");
        UUID nonOwner = frozenRoster.stream().filter(id -> !id.equals(owner)).findFirst().orElseThrow();
        check(!WaveTwoOwnershipPolicy.allowsTarget(owner, nonOwner, 17L, 17L),
                "a private mob cannot target another player");
        check(!WaveTwoOwnershipPolicy.allowsTarget(owner, owner, 16L, 17L),
                "a stale generation cannot reuse an assignment");

        List<UUID> survivors = frozenRoster.stream().filter(id -> !id.equals(owner)).toList();
        UUID reassigned = WaveTwoOwnershipPolicy.replacementOwner(
                event, 17L, entity, owner, survivors);
        check(!reassigned.equals(owner) && survivors.contains(reassigned),
                "a dead or disconnected owner's squad can be reassigned to a survivor");
        check(WaveTwoOwnershipPolicy.replacementOwner(event, 17L, entity, owner,
                        List.of(third, first, second).stream().filter(id -> !id.equals(owner)).toList())
                        .equals(reassigned),
                "reassignment remains deterministic after a restart");
        check(WaveTwoOwnershipPolicy.replacementOwner(event, 17L, entity, owner, List.of()).equals(owner),
                "without an eligible replacement the persisted owner is retained safely");

        String encodedRoster = WaveTwoOwnershipPolicy.encodeRoster(frozenRoster);
        check(WaveTwoOwnershipPolicy.decodeRoster(encodedRoster).equals(
                        frozenRoster.stream().sorted().toList()),
                "the frozen roster round-trips in canonical checkpoint order");
        expectInvalidRoster("", "missing owner roster is rejected");
        expectInvalidRoster(first + "," + first, "duplicate owners are rejected");
        expectInvalidRoster("not-a-uuid", "malformed owner IDs are rejected");
        System.out.println("WaveTwoOwnershipPolicyTest OK");
    }

    private static void expectInvalidRoster(String value, String message) {
        try {
            WaveTwoOwnershipPolicy.decodeRoster(value);
            throw new AssertionError(message);
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
