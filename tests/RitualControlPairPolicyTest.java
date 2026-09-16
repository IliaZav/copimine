import me.copimine.endevent.domain.RitualControlPairPolicy;

import java.util.List;

public final class RitualControlPairPolicyTest {
    public static void main(String[] args) {
        check(RitualControlPairPolicy.canActivate(false, false),
                "neither control effect should be active by default");
        check(RitualControlPairPolicy.canActivate(true, false),
                "reverse movement may activate alone");
        check(RitualControlPairPolicy.canActivate(false, true),
                "control swap may activate alone");
        check(!RitualControlPairPolicy.canActivate(true, true),
                "reverse and control swap must be mutually exclusive");

        List<RitualControlPairPolicy.Pair> pairs = RitualControlPairPolicy.pair(
                List.of("a", "b", "c", "d", "e"), 2);
        check(pairs.size() == 2, "pair assignment must be bounded by requested pairs");
        check(pairs.get(0).first().equals("a") && pairs.get(0).second().equals("b"),
                "pair assignment must be deterministic");
        check(pairs.get(1).first().equals("c") && pairs.get(1).second().equals("d"),
                "pair assignment must not reuse players");
        check(RitualControlPairPolicy.pair(List.of("a"), 3).isEmpty(),
                "a single player cannot form a swap pair");
        check(RitualControlPairPolicy.boundedInput(99.0D) == 1.0D,
                "client input must be bounded");
        check(RitualControlPairPolicy.boundedInput(-99.0D) == -1.0D,
                "negative client input must be bounded");
        System.out.println("RitualControlPairPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
