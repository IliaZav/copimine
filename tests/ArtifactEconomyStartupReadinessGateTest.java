package me.copimine.artifacts;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Focused regression coverage for the EconomyCore-to-Artifacts schema startup gate. */
public final class ArtifactEconomyStartupReadinessGateTest {
    private ArtifactEconomyStartupReadinessGateTest() {}

    public static void main(String[] args) throws Exception {
        waitsForEconomySchemaReadiness();
        failsClosedWhenEconomyNeverBecomesReady();
        retriesAcrossReadinessWindowsAndInitializesOnce();
        failsClosedAfterRetryDeadline();
        stopsWhenBootstrapIsCancelled();
        preservesInterruption();
        System.out.println("ArtifactEconomyStartupReadinessGateTest OK (six transitions)");
    }

    private static void waitsForEconomySchemaReadiness() throws Exception {
        AtomicInteger probes = new AtomicInteger();
        EconomyDatabaseStartupGate.await(() -> probes.incrementAndGet() >= 3, 500L, 1L);
        if (probes.get() != 3) {
            throw new AssertionError("schema bootstrap must wait until the bridge reports ready");
        }
    }

    private static void failsClosedWhenEconomyNeverBecomesReady() throws Exception {
        try {
            EconomyDatabaseStartupGate.await(() -> false, 8L, 1L);
            throw new AssertionError("unavailable economy schema must not be treated as ready");
        } catch (IllegalStateException expected) {
            if (!expected.getMessage().contains("EconomyCore PostgreSQL")) {
                throw new AssertionError("timeout should identify the unavailable dependency", expected);
            }
        }
    }

    private static void preservesInterruption() throws Exception {
        AtomicInteger interrupted = new AtomicInteger();
        Thread waiter = new Thread(() -> {
            try {
                EconomyDatabaseStartupGate.await(() -> false, 10_000L, 10L);
                interrupted.set(-1);
            } catch (InterruptedException expected) {
                if (Thread.currentThread().isInterrupted()) {
                    interrupted.set(1);
                }
            }
        }, "artifact-readiness-test");
        waiter.start();
        Thread.sleep(20L);
        waiter.interrupt();
        waiter.join(1_000L);
        if (waiter.isAlive() || interrupted.get() != 1) {
            throw new AssertionError("readiness wait must terminate and preserve interruption");
        }
    }

    private static void retriesAcrossReadinessWindowsAndInitializesOnce() throws Exception {
        AtomicInteger retries = new AtomicInteger();
        AtomicInteger initializations = new AtomicInteger();
        boolean initialized = EconomyDatabaseStartupGate.awaitAndInitialize(
                () -> retries.get() >= 1,
                () -> false,
                20L,
                200L,
                1L,
                retries::incrementAndGet,
                initializations::incrementAndGet);
        if (!initialized || retries.get() != 1 || initializations.get() != 1) {
            throw new AssertionError("a late-ready schema must cross a retry window and initialize exactly once");
        }
    }

    private static void stopsWhenBootstrapIsCancelled() throws Exception {
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicInteger initializations = new AtomicInteger();
        boolean initialized = EconomyDatabaseStartupGate.awaitAndInitialize(
                () -> false,
                cancelled::get,
                5L,
                100L,
                1L,
                () -> cancelled.set(true),
                initializations::incrementAndGet);
        if (initialized || initializations.get() != 0) {
            throw new AssertionError("cancelled schema bootstrap must not initialize");
        }
    }

    private static void failsClosedAfterRetryDeadline() throws Exception {
        AtomicInteger retries = new AtomicInteger();
        AtomicInteger initializations = new AtomicInteger();
        try {
            EconomyDatabaseStartupGate.awaitAndInitialize(
                    () -> false,
                    () -> false,
                    3L,
                    15L,
                    1L,
                    retries::incrementAndGet,
                    initializations::incrementAndGet);
            throw new AssertionError("unavailable economy schema must stop at the total retry deadline");
        } catch (IllegalStateException expected) {
            if (!expected.getMessage().contains("bounded retries")) {
                throw new AssertionError("retry timeout should identify its bounded policy", expected);
            }
        }
        if (retries.get() < 1 || initializations.get() != 0) {
            throw new AssertionError("retry timeout must retry and never initialize an unavailable schema");
        }
    }

}
