import me.copimine.artifacts.OrphanTransferReconciliationRunGate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class OrphanTransferReconciliationRunGateTest {
    public static void main(String[] args) throws Exception {
        onlyOneOverlappingReconciliationRunCanEnter();
        gateIsReleasedWhenReconciliationThrows();
        System.out.println("OrphanTransferReconciliationRunGateTest passed.");
    }

    private static void onlyOneOverlappingReconciliationRunCanEnter() throws Exception {
        OrphanTransferReconciliationRunGate gate = new OrphanTransferReconciliationRunGate();
        CountDownLatch firstRunEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstRun = new CountDownLatch(1);
        AtomicInteger activeRuns = new AtomicInteger();
        AtomicInteger maximumActiveRuns = new AtomicInteger();
        AtomicReference<Throwable> workerFailure = new AtomicReference<>();
        Thread firstRun = new Thread(() -> {
            try {
                boolean ran = gate.runIfIdle(() -> {
                    int active = activeRuns.incrementAndGet();
                    maximumActiveRuns.accumulateAndGet(active, Math::max);
                    firstRunEntered.countDown();
                    await(releaseFirstRun);
                    activeRuns.decrementAndGet();
                });
                if (!ran) {
                    workerFailure.set(new AssertionError("the first reconciliation run must enter"));
                }
            } catch (Throwable error) {
                workerFailure.set(error);
            }
        }, "orphan-reconciliation-first-run");
        firstRun.start();

        check(firstRunEntered.await(5, TimeUnit.SECONDS),
                "first reconciliation run must start before the competing run");
        AtomicInteger competingRuns = new AtomicInteger();
        boolean competingRunEntered = gate.runIfIdle(competingRuns::incrementAndGet);
        releaseFirstRun.countDown();
        firstRun.join(5_000L);

        check(!firstRun.isAlive(), "first reconciliation run must finish");
        check(workerFailure.get() == null, "first reconciliation run must not fail: " + workerFailure.get());
        check(!competingRunEntered && competingRuns.get() == 0,
                "the overlapping scheduler tick must be skipped without running its work");
        check(maximumActiveRuns.get() == 1 && activeRuns.get() == 0,
                "reconciliation work must never overlap");
        check(gate.runIfIdle(() -> { }), "a later reconciliation run must enter after the active run finishes");
    }

    private static void gateIsReleasedWhenReconciliationThrows() {
        OrphanTransferReconciliationRunGate gate = new OrphanTransferReconciliationRunGate();
        boolean failed = false;
        try {
            gate.runIfIdle(() -> {
                throw new IllegalStateException("simulated database failure");
            });
        } catch (IllegalStateException expected) {
            failed = true;
        }

        check(failed, "the simulated reconciliation failure must propagate to the scheduler");
        check(gate.runIfIdle(() -> { }), "the next reconciliation must enter after an exception");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("timed out waiting for the first reconciliation run");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for the first reconciliation run", error);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
