import java.util.UUID;
import me.copimine.endevent.runtime.wave7.EchoCombatDiagnosticThrottle;

/** E-06: bounded per-actor throttling for the named Echo decision failure channel. */
public final class EchoCombatDiagnosticThrottleTest {
    private static int assertions;

    public static void main(String[] args) {
        reportsFirstFailureThenSuppressesWithinTheWindow();
        reportsAgainAtTheWindowBoundary();
        clockResetAlwaysReports();
        forgetAndClearBoundTheMap();
        nullActorIsNeverReported();
        nonPositiveIntervalIsRejected();
        System.out.println("EchoCombatDiagnosticThrottleTest OK assertions=" + assertions);
    }

    private static void reportsFirstFailureThenSuppressesWithinTheWindow() {
        EchoCombatDiagnosticThrottle throttle = new EchoCombatDiagnosticThrottle(200L);
        UUID actor = id(1);
        check(throttle.shouldEmit(actor, 100L), "the first failure in a window is reported");
        throttle.record(actor, 100L);
        check(!throttle.shouldEmit(actor, 101L), "a repeat one tick later is suppressed");
        check(!throttle.shouldEmit(actor, 299L), "the last tick inside the window is suppressed");
        check(throttle.intervalTicks() == 200L, "the window length is preserved");
    }

    private static void reportsAgainAtTheWindowBoundary() {
        EchoCombatDiagnosticThrottle throttle = new EchoCombatDiagnosticThrottle(200L);
        UUID actor = id(2);
        throttle.record(actor, 100L);
        check(throttle.shouldEmit(actor, 300L), "exactly one window later reports again");
        throttle.record(actor, 300L);
        check(!throttle.shouldEmit(actor, 499L), "the new window suppresses its own repeats");
    }

    private static void clockResetAlwaysReports() {
        EchoCombatDiagnosticThrottle throttle = new EchoCombatDiagnosticThrottle(200L);
        UUID actor = id(3);
        throttle.record(actor, 5_000L);
        check(throttle.shouldEmit(actor, 3L),
                "a restarted event tick (moved backwards) is a fresh window, not a stale stamp");
    }

    private static void forgetAndClearBoundTheMap() {
        EchoCombatDiagnosticThrottle throttle = new EchoCombatDiagnosticThrottle(200L);
        UUID first = id(4);
        UUID second = id(5);
        throttle.record(first, 10L);
        throttle.record(second, 10L);
        check(throttle.trackedActors() == 2, "both actors are tracked");
        throttle.forget(first);
        check(throttle.trackedActors() == 1, "forget drops exactly the removed actor");
        check(throttle.shouldEmit(first, 11L), "a forgotten actor reports immediately again");
        throttle.clear();
        check(throttle.trackedActors() == 0, "clear drops every window");
    }

    private static void nullActorIsNeverReported() {
        EchoCombatDiagnosticThrottle throttle = new EchoCombatDiagnosticThrottle(200L);
        check(!throttle.shouldEmit(null, 100L), "a null actor never emits");
        throttle.record(null, 100L);
        throttle.forget(null);
        check(throttle.trackedActors() == 0, "null actor bookkeeping is a no-op");
    }

    private static void nonPositiveIntervalIsRejected() {
        expectRejected(() -> new EchoCombatDiagnosticThrottle(0L),
                "a zero window would flood the log");
        expectRejected(() -> new EchoCombatDiagnosticThrottle(-1L),
                "a negative window is not a valid throttle");
    }

    private static UUID id(long value) {
        return new UUID(0L, value);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void expectRejected(Runnable action, String message) {
        assertions++;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message);
    }
}
