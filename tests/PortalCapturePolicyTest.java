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
        PortalCapturePolicy.PortalState decayed = PortalCapturePolicy.tick(grace, false, 10_000L);
        check(decayed.progressMillis() < grace.progressMillis(), "a gap beyond grace must decay progress");
        check(decayed.progressMillis() == 0L, "long gaps must fail closed to zero progress");

        PortalCapturePolicy.PortalState first = PortalCapturePolicy.tick(PortalCapturePolicy.initial(), true, 0L);
        PortalCapturePolicy.PortalState second = PortalCapturePolicy.tick(PortalCapturePolicy.initial(), true, 0L);
        check(first.equals(second), "independent portals must not share mutable state");
        System.out.println("PortalCapturePolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
