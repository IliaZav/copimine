import me.copimine.endevent.domain.RitualTargetPolicy;

import java.util.List;
import java.util.UUID;

public final class RitualTargetPolicyTest {
    public static void main(String[] args) {
        UUID prisoner = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID freeA = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID freeB = UUID.fromString("00000000-0000-0000-0000-000000000003");

        List<UUID> targets = RitualTargetPolicy.freeTargets(List.of(
                new RitualTargetPolicy.Candidate(prisoner, true),
                new RitualTargetPolicy.Candidate(freeA, true),
                new RitualTargetPolicy.Candidate(freeB, true)
        ), prisoner);

        check(!targets.contains(prisoner), "ritual prisoner must never be a hostile target");
        check(targets.equals(List.of(freeA, freeB)),
                "eligible free players must remain targetable in deterministic order");

        UUID ineligible = UUID.fromString("00000000-0000-0000-0000-000000000004");
        List<UUID> withoutPrisonerFilter = RitualTargetPolicy.freeTargets(List.of(
                new RitualTargetPolicy.Candidate(ineligible, false),
                new RitualTargetPolicy.Candidate(freeB, true),
                new RitualTargetPolicy.Candidate(freeA, true),
                new RitualTargetPolicy.Candidate(freeB, true)
        ), null);
        check(withoutPrisonerFilter.equals(List.of(freeB, freeA)),
                "null prisoner must leave eligible caller-order targets intact and deduplicated");

        check(RitualTargetPolicy.freeTargets(List.of(
                new RitualTargetPolicy.Candidate(prisoner, true),
                new RitualTargetPolicy.Candidate(freeA, false)
        ), prisoner).isEmpty(),
                "ineligible candidates and the prisoner must be excluded");
        check(RitualTargetPolicy.freeTargets(null, prisoner).equals(List.of()),
                "null candidate list must return an empty list");
        expectFailure(() -> new RitualTargetPolicy.Candidate(null, true),
                "candidate construction must reject a null player id");

        System.out.println("RitualTargetPolicyTest OK");
    }

    private static void expectFailure(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
