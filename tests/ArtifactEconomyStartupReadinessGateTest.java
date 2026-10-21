package me.copimine.artifacts;

import java.util.concurrent.atomic.AtomicInteger;

/** Focused regression coverage for the EconomyCore-to-Artifacts schema startup gate. */
public final class ArtifactEconomyStartupReadinessGateTest {
    private ArtifactEconomyStartupReadinessGateTest() {}

    public static void main(String[] args) throws Exception {
        waitsForEconomySchemaReadiness();
        failsClosedWhenEconomyNeverBecomesReady();
        preservesInterruption();
        System.out.println("ArtifactEconomyStartupReadinessGateTest OK (three transitions)");
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
}
