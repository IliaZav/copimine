package me.copimine.artifacts;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Bounded wait for the EconomyCore schema before Artifacts runs its migrations. */
final class EconomyDatabaseStartupGate {
    private static final long DEFAULT_TIMEOUT_MILLIS = 30_000L;
    private static final long DEFAULT_POLL_INTERVAL_MILLIS = 100L;

    private EconomyDatabaseStartupGate() {}

    static void await(BooleanSupplier economyPostgresReady) throws InterruptedException {
        await(economyPostgresReady, DEFAULT_TIMEOUT_MILLIS, DEFAULT_POLL_INTERVAL_MILLIS);
    }

    static void await(BooleanSupplier economyPostgresReady, long timeoutMillis, long pollIntervalMillis)
            throws InterruptedException {
        Objects.requireNonNull(economyPostgresReady, "economyPostgresReady");
        if (timeoutMillis <= 0L || pollIntervalMillis <= 0L) {
            throw new IllegalArgumentException("Readiness timeout and poll interval must be positive.");
        }

        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (true) {
            if (economyPostgresReady.getAsBoolean()) {
                return;
            }
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0L) {
                throw new IllegalStateException(
                        "Timed out waiting for EconomyCore PostgreSQL schema readiness.");
            }
            long intervalNanos = TimeUnit.MILLISECONDS.toNanos(pollIntervalMillis);
            try {
                TimeUnit.NANOSECONDS.sleep(Math.min(remainingNanos, intervalNanos));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw interrupted;
            }
        }
    }
}
