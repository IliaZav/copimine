import java.util.List;
import me.copimine.artifacts.AngelSealDeathPolicy;

public final class AngelSealDeathPolicyTest {
    public static void main(String[] args) {
        testFirstAuthenticCandidateIsSelectedDeterministically();
        testDeathPreventionNeverConsumes();
        testNoSealDoesNotProtect();
        System.out.println("AngelSealDeathPolicyTest OK");
    }

    private static void testFirstAuthenticCandidateIsSelectedDeterministically() {
        var decision = AngelSealDeathPolicy.decide(
            false,
            false,
            false,
            List.of(
                new AngelSealDeathPolicy.SealCandidate(
                    AngelSealDeathPolicy.Surface.STORAGE, 0, "tampered", false),
                new AngelSealDeathPolicy.SealCandidate(
                    AngelSealDeathPolicy.Surface.STORAGE, 4, "seal-main", true),
                new AngelSealDeathPolicy.SealCandidate(
                    AngelSealDeathPolicy.Surface.OFFHAND, 0, "seal-offhand", true)));
        check(decision.preserveInventory(), "an authentic seal must preserve inventory");
        check(decision.selectedSeal() != null, "the selected seal must be reported");
        check("seal-main".equals(decision.selectedSeal().uniqueItemId()),
            "the first authentic inventory candidate must be selected");
    }

    private static void testDeathPreventionNeverConsumes() {
        var candidates = List.of(new AngelSealDeathPolicy.SealCandidate(
            AngelSealDeathPolicy.Surface.ARMOR, 0, "seal-armor", true));
        check(!AngelSealDeathPolicy.decide(true, false, false, candidates).preserveInventory(),
            "cancelled death must not consume a seal");
        check(!AngelSealDeathPolicy.decide(false, true, false, candidates).preserveInventory(),
            "pre-existing keepInventory must not consume a seal");
        check(!AngelSealDeathPolicy.decide(false, false, true, candidates).preserveInventory(),
            "resurrected death must not consume a seal");
    }

    private static void testNoSealDoesNotProtect() {
        check(!AngelSealDeathPolicy.decide(false, false, false, List.of()).preserveInventory(),
            "a player without a seal must follow vanilla death");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
