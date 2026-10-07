import me.copimine.endevent.domain.PortalCapturePolicy;
import me.copimine.endevent.domain.PortalPresentationPolicy;

import java.util.List;

public final class PortalPresentationPolicyTest {
    private static int failures;

    public static void main(String[] args) {
        run("capture gauge", PortalPresentationPolicyTest::captureGauge);
        run("subdued inactive", PortalPresentationPolicyTest::subduedInactive);
        run("one collapse deadline", PortalPresentationPolicyTest::oneCollapseDeadline);
        run("next gate activation", PortalPresentationPolicyTest::nextGateActivation);
        run("bounded reconstruction", PortalPresentationPolicyTest::boundedReconstruction);
        run("grace and decay projection", PortalPresentationPolicyTest::graceAndDecay);
        run("all closed", PortalPresentationPolicyTest::allClosed);
        if (failures != 0) throw new AssertionError(failures + " portal presentation scenarios failed");
        System.out.println("PortalPresentationPolicyTest OK scenarios=7");
    }

    private static void captureGauge() {
        var zero = PortalPresentationPolicy.frame(PortalCapturePolicy.initial(), true, -1, 0);
        var half = PortalPresentationPolicy.frame(partial(2_500), true, -1, 2_500);
        var full = PortalPresentationPolicy.frame(complete(5_000), false, 5_000, 5_000);
        check(zero.gaugeSegments() == 0 && half.gaugeSegments() == 12 && full.gaugeSegments() == 24,
                "0/50/100% must render 0/12/24 captured arc segments; half=" + half.gaugeSegments());
        close(half.progress(), 0.5, "half capture must use actual server progress");
        check(half.scale() < zero.scale(), "capture must visibly contract the active frame");
        check(full.visible(), "the last full gauge must survive into its collapse");
    }

    private static void subduedInactive() {
        var active = PortalPresentationPolicy.frame(partial(2_500), true, -1, 2_500);
        var waiting = PortalPresentationPolicy.frame(partial(2_500), false, -1, 2_500);
        check(waiting.brightness() < active.brightness(), "waiting portal must be subdued");
        check(waiting.gaugeSegments() == 0, "an inactive gate must not look capturable");
    }

    private static void oneCollapseDeadline() {
        var done = complete(5_000);
        var start = PortalPresentationPolicy.frame(done, false, 5_000, 5_000);
        var middle = PortalPresentationPolicy.frame(done, false, 5_000, 5_300);
        var end = PortalPresentationPolicy.frame(done, false, 5_000, 5_600);
        check(start.visible() && middle.visible() && !end.visible(), "completed portal must close at deadline");
        close(middle.scale(), start.scale() / 2, "collapse scale must halve at its halfway point");
        check(!PortalPresentationPolicy.frame(done, false, 5_000, 9_000).visible(),
                "late completed refresh must never restart a closed portal");
        check(!PortalPresentationPolicy.collapseFinished(5_000, 5_599)
                && PortalPresentationPolicy.collapseFinished(5_000, 5_600), "cleanup waits for exactly600ms collapse");
    }

    private static void nextGateActivation() {
        var initial = PortalCapturePolicy.initial();
        check(PortalPresentationPolicy.activeIndex(List.of(initial, initial, initial)) == 0,
                "only the first unfinished portal activates");
        check(PortalPresentationPolicy.activeIndex(List.of(complete(5_000), initial, initial)) == 1,
                "first completion must activate exactly the second portal");
        check(PortalPresentationPolicy.activeIndex(List.of(complete(5_000), complete(10_000), initial)) == 2,
                "second completion must activate exactly the third portal");
    }

    private static void boundedReconstruction() {
        check(PortalPresentationPolicy.shouldRebuild(true, false, 0), "lost visible layer gets one reconstruction");
        check(!PortalPresentationPolicy.shouldRebuild(true, false, 1), "repeated disappearance must not spawn forever");
        check(!PortalPresentationPolicy.shouldRebuild(true, true, 0), "live owned layer must not duplicate");
        check(!PortalPresentationPolicy.shouldRebuild(false, false, 0), "closed layer must not resurrect");
    }

    private static void graceAndDecay() {
        var state = PortalCapturePolicy.tick(PortalCapturePolicy.initial(), true, 0);
        state = PortalCapturePolicy.tick(state, true, 2_500);
        var grace = PortalCapturePolicy.tick(state, false, 2_950);
        var decayed = PortalCapturePolicy.tick(grace, false, 3_450);
        close(PortalPresentationPolicy.frame(grace, true, -1, 2_950).progress(), 0.5,
                "grace must preserve the half gauge");
        close(PortalPresentationPolicy.frame(decayed, true, -1, 3_450).progress(), 0.45,
                "gauge must follow authoritative decay, not a separate timer");
    }

    private static void allClosed() {
        check(PortalPresentationPolicy.activeIndex(List.of(complete(5_000), complete(10_000), complete(15_000))) == -1,
                "three completed gates leave no active capture target");
        check(!PortalPresentationPolicy.frame(complete(5_000), false, -1, 8_000).visible(),
                "missing closure ownership must fail closed");
    }

    private static PortalCapturePolicy.PortalState partial(long progress) {
        return new PortalCapturePolicy.PortalState(false, progress, progress, progress);
    }

    private static PortalCapturePolicy.PortalState complete(long time) {
        return new PortalCapturePolicy.PortalState(true, 5_000, time, time);
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
