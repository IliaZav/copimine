import me.copimine.endevent.domain.PortalCapturePolicy;

public final class PortalCapturePolicyTest {
    public static void main(String[] args) {
        PortalCapturePolicy.PortalState state = PortalCapturePolicy.initial();
        state = PortalCapturePolicy.tick(state, true, 0L);
        state = PortalCapturePolicy.tick(state, true, 4_999L);
        check(state.progressMillis() == 4_999L && !state.completed(), "capture must need five continuous seconds");
        state = PortalCapturePolicy.tick(state, true, 5_000L);
        check(state.completed(), "portal must complete at five seconds");
        check(PortalCapturePolicy.tick(state, true, 5_000L).equals(state), "same timestamp must be deterministic");

        PortalCapturePolicy.PortalState grace = PortalCapturePolicy.tick(PortalCapturePolicy.initial(), true, 1_000L);
        grace = PortalCapturePolicy.tick(grace, true, 1_999L);
        grace = PortalCapturePolicy.tick(grace, false, 2_449L);
        grace = PortalCapturePolicy.tick(grace, true, 2_449L);
        check(grace.progressMillis() == 999L, "a gap within grace must preserve progress");
        check(grace.lastOccupiedMillis() == 2_449L, "same-timestamp re-entry must record the occupied time");
        PortalCapturePolicy.PortalState boundary = PortalCapturePolicy.tick(grace, false, 2_899L);
        check(boundary.progressMillis() == grace.progressMillis(), "re-entry at the same timestamp must preserve the grace boundary");
        PortalCapturePolicy.PortalState decayed = PortalCapturePolicy.tick(grace, false, 10_000L);
        check(decayed.progressMillis() < grace.progressMillis(), "a gap beyond grace must decay progress");
        check(decayed.progressMillis() == 0L, "long gaps must fail closed to zero progress");

        PortalCapturePolicy.PortalState gradual = PortalCapturePolicy.initial();
        gradual = PortalCapturePolicy.tick(gradual, true, 0L);
        gradual = PortalCapturePolicy.tick(gradual, true, 5_000L);
        check(gradual.completed(), "the occupied portal must complete before decay is tested");
        PortalCapturePolicy.PortalState partial = PortalCapturePolicy.initial();
        partial = PortalCapturePolicy.tick(partial, true, 0L);
        partial = PortalCapturePolicy.tick(partial, true, 2_000L);
        PortalCapturePolicy.PortalState afterExit = PortalCapturePolicy.tick(partial, false, 2_450L);
        check(afterExit.progressMillis() == partial.progressMillis(),
                "leaving during the grace window must not reset progress");
        PortalCapturePolicy.PortalState firstDecay = PortalCapturePolicy.tick(afterExit, false, 2_950L);
        check(firstDecay.progressMillis() > 0L && firstDecay.progressMillis() < afterExit.progressMillis(),
                "progress must remain visible and decrease gradually after exit");
        PortalCapturePolicy.PortalState secondDecay = PortalCapturePolicy.tick(firstDecay, false, 3_450L);
        check(secondDecay.progressMillis() < firstDecay.progressMillis(),
                "each subsequent empty update must reduce progress monotonically");
        check(PortalCapturePolicy.tick(secondDecay, false, 12_000L).progressMillis() == 0L,
                "progress must eventually decay to zero");

        PortalCapturePolicy.PortalState configured = PortalCapturePolicy.initial();
        configured = PortalCapturePolicy.tick(configured, true, 0L, 0.25D);
        configured = PortalCapturePolicy.tick(configured, true, 2_000L, 0.25D);
        configured = PortalCapturePolicy.tick(configured, false, 2_950L, 0.25D);
        check(configured.progressMillis() == 1_875L,
                "configured decay rate must be applied deterministically");
        boolean invalidRate = false;
        try { PortalCapturePolicy.tick(PortalCapturePolicy.initial(), true, 0L, 0.0D); }
        catch (IllegalArgumentException expected) { invalidRate = true; }
        check(invalidRate, "zero decay rate must be rejected to guarantee eventual cleanup");

        PortalCapturePolicy.PortalState first = PortalCapturePolicy.tick(PortalCapturePolicy.initial(), true, 0L);
        PortalCapturePolicy.PortalState second = PortalCapturePolicy.tick(PortalCapturePolicy.initial(), true, 0L);
        check(first.equals(second), "independent portals must not share mutable state");
        boolean invalidTime = false;
        try { PortalCapturePolicy.tick(PortalCapturePolicy.initial(), true, -1L); }
        catch (IllegalArgumentException expected) { invalidTime = true; }
        check(invalidTime, "negative portal timestamps must fail closed");
        System.out.println("PortalCapturePolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
