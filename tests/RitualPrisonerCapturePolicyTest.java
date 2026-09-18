import me.copimine.endevent.domain.RitualSealCapturePolicy;

import java.util.List;
import java.util.UUID;

/** Strong regression coverage for physical Wave 6 prisoner capture. */
public final class RitualPrisonerCapturePolicyTest {
    public static void main(String[] args) {
        UUID outsideLower = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID inside = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID insideLater = UUID.fromString("00000000-0000-0000-0000-000000000003");

        // Regression: UUID/list order must not capture a player who is outside
        // the seal before checking the physically present eligible candidate.
        UUID selected = RitualSealCapturePolicy.select(List.of(
                new RitualSealCapturePolicy.Candidate(outsideLower, true, 2.0D, 0.0D),
                new RitualSealCapturePolicy.Candidate(inside, true, 0.75D, 0.0D),
                new RitualSealCapturePolicy.Candidate(insideLater, true, 0.25D, 0.0D)
        ), 0.0D, 0.0D);
        check(inside.equals(selected),
                "the first eligible candidate physically inside the seal must be captured");

        UUID eligibleAfterInvalid = RitualSealCapturePolicy.select(List.of(
                new RitualSealCapturePolicy.Candidate(outsideLower, false, 0.0D, 0.0D),
                new RitualSealCapturePolicy.Candidate(inside, true, 0.0D, 1.25D)
        ), 0.0D, 0.0D);
        check(inside.equals(eligibleAfterInvalid),
                "ineligible occupants must not block an eligible boundary occupant");

        UUID boundary = UUID.fromString("00000000-0000-0000-0000-000000000004");
        UUID justOutside = UUID.fromString("00000000-0000-0000-0000-000000000005");
        check(boundary.equals(RitualSealCapturePolicy.select(List.of(
                        new RitualSealCapturePolicy.Candidate(boundary, true, 1.25D, 0.0D)
                ), 0.0D, 0.0D)),
                "the configured capture radius must be inclusive");
        check(RitualSealCapturePolicy.select(List.of(
                        new RitualSealCapturePolicy.Candidate(justOutside, true, 1.250001D, 0.0D)
                ), 0.0D, 0.0D) == null,
                "a candidate just beyond the capture radius must not be captured");

        check(RitualSealCapturePolicy.select(null, 0.0D, 0.0D) == null,
                "a missing candidate list must fail closed");
        check(RitualSealCapturePolicy.select(List.of(
                        new RitualSealCapturePolicy.Candidate(inside, true, 0.0D, 0.0D)
                ), Double.NaN, 0.0D) == null,
                "a non-finite seal coordinate must fail closed");
        expectFailure(() -> new RitualSealCapturePolicy.Candidate(null, true, 0.0D, 0.0D),
                "a capture candidate must require a player UUID");

        System.out.println("RitualPrisonerCapturePolicyTest OK");
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
