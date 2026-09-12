import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.copimine.endevent.domain.TransitionRunePolicy;

public final class TransitionRunePolicyTest {
    public static void main(String[] args) {
        UUID alpha = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID bravo = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Set<UUID> roster = Set.of(alpha, bravo);

        TransitionRunePolicy.Check ready = TransitionRunePolicy.evaluate(roster, List.of(
                occupied(alpha, "rune-a"), occupied(bravo, "rune-b")));
        check(ready.complete(), "two eligible players on two unique runes must satisfy the check");
        check(ready.missingPlayers().isEmpty(), "complete check must have no missing players");

        TransitionRunePolicy.HoldState started = TransitionRunePolicy.advance(
                TransitionRunePolicy.HoldState.idle(), ready, 10_000L, 5_000L);
        check(started.startedAtMillis() == 10_000L && !started.complete(), "a full roster starts the hold");
        check(!TransitionRunePolicy.advance(started, ready, 14_999L, 5_000L).complete(),
                "four seconds and 999 milliseconds is not a five-second hold");
        check(TransitionRunePolicy.advance(started, ready, 15_000L, 5_000L).complete(),
                "the hold completes exactly at five seconds");

        TransitionRunePolicy.Check missing = TransitionRunePolicy.evaluate(roster, List.of(occupied(alpha, "rune-a")));
        TransitionRunePolicy.HoldState reset = TransitionRunePolicy.advance(started, missing, 12_000L, 5_000L);
        check(reset.startedAtMillis() == 0L && !reset.complete(), "a player leaving resets the hold immediately");
        check(reset.reason() == TransitionRunePolicy.Reason.MISSING_ROSTER_MEMBER,
                "leave reason must be traceable");

        TransitionRunePolicy.Check duplicateRune = TransitionRunePolicy.evaluate(roster, List.of(
                occupied(alpha, "rune-a"), occupied(bravo, "rune-a")));
        check(!duplicateRune.complete(), "a single rune cannot satisfy two participants");
        check(duplicateRune.reason() == TransitionRunePolicy.Reason.DUPLICATE_RUNE,
                "duplicate rune reason must be explicit");

        TransitionRunePolicy.Check duplicatePlayer = TransitionRunePolicy.evaluate(roster, List.of(
                occupied(alpha, "rune-a"), occupied(alpha, "rune-b"), occupied(bravo, "rune-c")));
        check(!duplicatePlayer.complete(), "one player cannot occupy two runes");
        check(duplicatePlayer.reason() == TransitionRunePolicy.Reason.DUPLICATE_PLAYER,
                "duplicate player reason must be explicit");

        TransitionRunePolicy.Check dead = TransitionRunePolicy.evaluate(roster, List.of(
                occupied(alpha, "rune-a"), new TransitionRunePolicy.RuneOccupancy(bravo, "rune-b", true, false, true)));
        check(!dead.complete() && dead.missingPlayers().equals(Set.of(bravo)),
                "dead roster members may not satisfy a rune");
        check(dead.reason() == TransitionRunePolicy.Reason.INELIGIBLE_ROSTER_MEMBER,
                "dead member reason must be explicit");

        TransitionRunePolicy.Check external = TransitionRunePolicy.evaluate(roster, List.of(
                occupied(alpha, "rune-a"), occupied(bravo, "rune-b"),
                occupied(UUID.fromString("00000000-0000-0000-0000-000000000003"), "rune-c")));
        check(external.complete(), "spectators and helpers outside the committed roster do not block the hold");

        System.out.println("TransitionRunePolicyTest OK");
    }

    private static TransitionRunePolicy.RuneOccupancy occupied(UUID player, String rune) {
        return new TransitionRunePolicy.RuneOccupancy(player, rune, true, true, true);
    }

    private static void check(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
