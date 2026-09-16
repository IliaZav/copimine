import me.copimine.endevent.domain.RitualSealCapturePolicy;

import java.util.List;
import java.util.UUID;

public final class RitualSealCapturePolicyTest {
    public static void main(String[] args) {
        UUID outsideLowest = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID inside = UUID.fromString("ffffffff-ffff-ffff-ffff-fffffffffff0");
        UUID ineligibleInside = UUID.fromString("ffffffff-ffff-ffff-ffff-fffffffffff1");

        UUID selected = RitualSealCapturePolicy.select(List.of(
                new RitualSealCapturePolicy.Candidate(outsideLowest, true, 5.0D, 0.0D),
                new RitualSealCapturePolicy.Candidate(ineligibleInside, false, 0.25D, 0.25D),
                new RitualSealCapturePolicy.Candidate(inside, true, 0.50D, 0.25D)
        ), 0.0D, 0.0D);

        check(inside.equals(selected),
                "the eligible player physically inside the ritual seal must be captured");

        UUID none = RitualSealCapturePolicy.select(List.of(
                new RitualSealCapturePolicy.Candidate(outsideLowest, true, 4.0D, 4.0D)
        ), 0.0D, 0.0D);
        check(none == null, "Wave 6 must not auto-capture a player outside the seal");

        UUID atBoundary = UUID.randomUUID();
        check(atBoundary.equals(RitualSealCapturePolicy.select(List.of(
                new RitualSealCapturePolicy.Candidate(atBoundary, true,
                        RitualSealCapturePolicy.CAPTURE_RADIUS_BLOCKS, 0.0D)
        ), 0.0D, 0.0D)), "the capture radius boundary must be inclusive");

        UUID outsideBoundary = UUID.randomUUID();
        check(RitualSealCapturePolicy.select(List.of(
                new RitualSealCapturePolicy.Candidate(outsideBoundary, true,
                        Math.nextUp(RitualSealCapturePolicy.CAPTURE_RADIUS_BLOCKS), 0.0D)
        ), 0.0D, 0.0D) == null, "a candidate just outside the radius must not be captured");

        UUID tieLow = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID tieHigh = UUID.fromString("ffffffff-ffff-ffff-ffff-fffffffffff3");
        UUID tieSelected = RitualSealCapturePolicy.select(List.of(
                new RitualSealCapturePolicy.Candidate(tieHigh, true, 0.5D, 0.0D),
                new RitualSealCapturePolicy.Candidate(tieLow, true, -0.5D, 0.0D)
        ), 0.0D, 0.0D);
        check(tieLow.equals(tieSelected),
                "equal-distance eligible candidates must select the lexicographically lower UUID");

        check(RitualSealCapturePolicy.select(null, 0.0D, 0.0D) == null,
                "null candidates must not capture anyone");
        check(RitualSealCapturePolicy.select(List.of(
                new RitualSealCapturePolicy.Candidate(inside, true, 0.0D, 0.0D)
        ), Double.NaN, 0.0D) == null, "non-finite seal coordinates must not capture anyone");
        expectFailure(() -> new RitualSealCapturePolicy.Candidate(null, true, 0.0D, 0.0D));
        expectFailure(() -> new RitualSealCapturePolicy.Candidate(inside, true, Double.NaN, 0.0D));
        expectFailure(() -> new RitualSealCapturePolicy.Candidate(inside, true, 0.0D,
                Double.POSITIVE_INFINITY));

        System.out.println("RitualSealCapturePolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("expected invalid ritual seal candidate to be rejected");
    }
}
