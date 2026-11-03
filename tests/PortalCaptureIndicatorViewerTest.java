import me.copimine.endevent.domain.PortalCaptureIndicatorPolicy;

/**
 * Audit wave 3: every viewer of a portal must be told the SAME direction.
 *
 * <p>The runtime used to read and overwrite the previous progress sample inside
 * the per-viewer render call, so the first observer saw INWARD and everyone
 * after them compared the new value with itself and got IDLE. This test drives
 * the shared-sample contract the runtime now follows: advance once, re-read for
 * every observer.</p>
 */
public final class PortalCaptureIndicatorViewerTest {
    public static void main(String[] args) {
        double previous = -1.0D;
        // First update: no prior sample, so nothing is invented.
        check(PortalCaptureIndicatorPolicy.flow(0.20D, previous)
                        == PortalCaptureIndicatorPolicy.Flow.IDLE,
                "an unknown first sample must not invent a direction");
        previous = 0.20D;

        // Progress really moved. Every observer of THIS update must agree.
        double moved = 0.45D;
        PortalCaptureIndicatorPolicy.Flow firstViewer =
                PortalCaptureIndicatorPolicy.flow(moved, previous);
        check(firstViewer == PortalCaptureIndicatorPolicy.Flow.INWARD,
                "closing progress must stream inward");
        // The old defect: the sample was advanced on the first viewer, so the
        // second compared moved with itself.
        PortalCaptureIndicatorPolicy.Flow naivelyAdvanced =
                PortalCaptureIndicatorPolicy.flow(moved, moved);
        check(naivelyAdvanced == PortalCaptureIndicatorPolicy.Flow.IDLE,
                "the per-viewer advance is exactly the defect being fixed");
        // The fixed contract: re-reading the SAME sample gives every viewer the
        // same answer.
        for (int viewer = 1; viewer <= 8; viewer++) {
            check(PortalCaptureIndicatorPolicy.flow(moved, previous) == firstViewer,
                    "viewer " + viewer + " disagreed about the direction");
        }
        previous = moved;

        // Recovery is outward, and it is equally shared.
        PortalCaptureIndicatorPolicy.Flow outward =
                PortalCaptureIndicatorPolicy.flow(0.30D, previous);
        check(outward == PortalCaptureIndicatorPolicy.Flow.OUTWARD,
                "recovery must stream outward");
        for (int viewer = 1; viewer <= 8; viewer++) {
            check(PortalCaptureIndicatorPolicy.flow(0.30D, previous) == outward,
                    "viewer " + viewer + " disagreed about the recovery direction");
        }

        System.out.println("PortalCaptureIndicatorViewerTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
