import me.copimine.endevent.domain.PortalCapturePolicy;
import me.copimine.endevent.domain.PortalPresentationPolicy;

import java.util.List;

public final class PortalPresentationPolicyTest {
    /** Half of the configured closing window, so the test never hardcodes a duration. */
    private static final long HALF = PortalCapturePolicy.CAPTURE_MILLIS / 2L;
    private static final long FULL = PortalCapturePolicy.CAPTURE_MILLIS;
    private static int failures;

    public static void main(String[] args) {
        run("capture gauge", PortalPresentationPolicyTest::captureGauge);
        run("subdued inactive", PortalPresentationPolicyTest::subduedInactive);
        run("one collapse deadline", PortalPresentationPolicyTest::oneCollapseDeadline);
        run("next gate activation", PortalPresentationPolicyTest::nextGateActivation);
        run("bounded reconstruction retries", PortalPresentationPolicyTest::boundedReconstruction);
        run("grace and decay projection", PortalPresentationPolicyTest::graceAndDecay);
        run("all closed", PortalPresentationPolicyTest::allClosed);
        if (failures != 0) throw new AssertionError(failures + " portal presentation scenarios failed");
        System.out.println("PortalPresentationPolicyTest OK scenarios=7");
    }

    private static void captureGauge() {
        var zero = PortalPresentationPolicy.frame(PortalCapturePolicy.initial(), true, -1, 0);
        var half = PortalPresentationPolicy.frame(partial(HALF), true, -1, HALF);
        var full = PortalPresentationPolicy.frame(complete(FULL), false, FULL, FULL);
        check(zero.gaugeSegments() == 0 && half.gaugeSegments() == 12 && full.gaugeSegments() == 24,
                "0/50/100% must render 0/12/24 captured arc segments; half=" + half.gaugeSegments());
        close(half.progress(), 0.5, "half capture must use actual server progress");
        check(half.scale() < zero.scale(), "capture must visibly contract the active frame");
        check(full.visible(), "the last full gauge must survive into its collapse");
    }

    private static void subduedInactive() {
        var active = PortalPresentationPolicy.frame(partial(HALF), true, -1, HALF);
        var waiting = PortalPresentationPolicy.frame(partial(HALF), false, -1, HALF);
        check(waiting.brightness() < active.brightness(), "waiting portal must be subdued");
        check(waiting.gaugeSegments() == 0, "an inactive gate must not look capturable");
    }

    private static void oneCollapseDeadline() {
        var done = complete(FULL);
        var start = PortalPresentationPolicy.frame(done, false, FULL, FULL);
        var middle = PortalPresentationPolicy.frame(done, false, FULL, FULL + 300);
        var end = PortalPresentationPolicy.frame(done, false, FULL, FULL + 600);
        check(start.visible() && middle.visible() && !end.visible(), "completed portal must close at deadline");
        close(middle.scale(), start.scale() / 2, "collapse scale must halve at its halfway point");
        check(!PortalPresentationPolicy.frame(done, false, FULL, FULL + 4_000).visible(),
                "late completed refresh must never restart a closed portal");
        check(!PortalPresentationPolicy.collapseFinished(FULL, FULL + 599)
                && PortalPresentationPolicy.collapseFinished(FULL, FULL + 600), "cleanup waits for exactly600ms collapse");
    }

    private static void nextGateActivation() {
        var initial = PortalCapturePolicy.initial();
        check(PortalPresentationPolicy.activeIndex(List.of(initial, initial, initial)) == 0,
                "only the first unfinished portal activates");
        check(PortalPresentationPolicy.activeIndex(List.of(complete(FULL), initial, initial)) == 1,
                "first completion must activate exactly the second portal");
        check(PortalPresentationPolicy.activeIndex(List.of(complete(FULL), complete(FULL * 2), initial)) == 2,
                "second completion must activate exactly the third portal");
    }

    private static void boundedReconstruction() {
        check(PortalPresentationPolicy.shouldRebuild(true, false, 0, 0, 0),
                "a missing visible layer may retry immediately");
        check(!PortalPresentationPolicy.shouldRebuild(true, false, 1, 499, 500),
                "retry backoff must suppress an early rebuild");
        check(PortalPresentationPolicy.shouldRebuild(true, false, 1, 500, 500),
                "a transient failure may retry after backoff");
        check(PortalPresentationPolicy.shouldRebuild(true, false, 2, 1_000, 1_000),
                "a second transient failure still has a bounded retry");
        check(!PortalPresentationPolicy.shouldRebuild(true, false, 3, 2_000, 2_000),
                "reconstruction must stop at the retry limit");
        check(!PortalPresentationPolicy.shouldRebuild(true, true, 0, 0, 0),
                "live owned layer must not duplicate");
        check(!PortalPresentationPolicy.shouldRebuild(false, false, 0, 0, 0),
                "closed layer must not resurrect");
        check(PortalPresentationPolicy.retryDelayMillis(1) == 250L
                        && PortalPresentationPolicy.retryDelayMillis(2) == 500L
                        && PortalPresentationPolicy.retryDelayMillis(3) == 750L,
                "consecutive failed rebuilds must back off");
        check(PortalPresentationPolicy.retryDelayMillis(99) == 1_000L,
                "rebuild backoff must remain bounded");
    }

    private static void graceAndDecay() {
        var state = PortalCapturePolicy.tick(PortalCapturePolicy.initial(), true, 0);
        state = PortalCapturePolicy.tick(state, true, HALF);
        // Cumulative decay is measured from the last occupied timestamp, so the
        // loss after grace is (elapsed - GRACE_MILLIS) * rate, clamped by progress.
        long outside = 1_000L;
        long decayedAt = HALF + PortalCapturePolicy.GRACE_MILLIS + outside;
        var grace = PortalCapturePolicy.tick(state, false, HALF + PortalCapturePolicy.GRACE_MILLIS);
        var decayed = PortalCapturePolicy.tick(grace, false, decayedAt);
        close(PortalPresentationPolicy.frame(grace, true, -1, HALF + PortalCapturePolicy.GRACE_MILLIS).progress(),
                0.5, "grace must preserve the half gauge");
        double expectedLost = outside * PortalCapturePolicy.DEFAULT_DECAY_RATE
                / PortalCapturePolicy.CAPTURE_MILLIS;
        close(PortalPresentationPolicy.frame(decayed, true, -1, decayedAt).progress(),
                0.5 - expectedLost,
                "gauge must follow authoritative decay, not a separate timer");
    }

    private static void allClosed() {
        check(PortalPresentationPolicy.activeIndex(List.of(complete(FULL), complete(FULL * 2), complete(FULL * 3))) == -1,
                "three completed gates leave no active capture target");
        check(!PortalPresentationPolicy.frame(complete(FULL), false, -1, 8_000).visible(),
                "missing closure ownership must fail closed");
    }

    private static PortalCapturePolicy.PortalState partial(long progress) {
        return new PortalCapturePolicy.PortalState(false, progress, progress, progress);
    }

    private static PortalCapturePolicy.PortalState complete(long time) {
        return new PortalCapturePolicy.PortalState(true, PortalCapturePolicy.CAPTURE_MILLIS, time, time);
    }

    private static void run(String name, Runnable scenario) {
        try { scenario.run(); }
        catch (AssertionError failure) { failures++; System.err.println("FAIL " + name + ": " + failure.getMessage()); }
    }

    private static void close(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 1e-8, message + "; actual=" + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
