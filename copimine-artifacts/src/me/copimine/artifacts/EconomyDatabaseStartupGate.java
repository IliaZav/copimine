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

    /**
     * Waits across bounded retry windows and invokes initialization once after readiness.
     * Returns false when the owning plugin lifecycle is cancelled before readiness.
     */
    static boolean awaitAndInitialize(
            BooleanSupplier economyPostgresReady,
            BooleanSupplier cancelled,
            long retryWindowMillis,
            long totalTimeoutMillis,
            long pollIntervalMillis,
            Runnable onRetry,
            CheckedInitialization initialize) throws Exception {
        Objects.requireNonNull(economyPostgresReady, "economyPostgresReady");
        Objects.requireNonNull(cancelled, "cancelled");
        Objects.requireNonNull(onRetry, "onRetry");
        Objects.requireNonNull(initialize, "initialize");
        if (retryWindowMillis <= 0L || totalTimeoutMillis <= 0L || pollIntervalMillis <= 0L) {
            throw new IllegalArgumentException("Retry window, total timeout, and poll interval must be positive.");
        }
        if (retryWindowMillis > totalTimeoutMillis) {
            throw new IllegalArgumentException("Retry window cannot exceed the total timeout.");
        }
        long maximumSafeTimeoutMillis = Long.MAX_VALUE / 1_000_000L / 2L;
        if (totalTimeoutMillis > maximumSafeTimeoutMillis) {
            throw new IllegalArgumentException("Total timeout is too large for a monotonic deadline.");
        }

        long retryWindowNanos = TimeUnit.MILLISECONDS.toNanos(retryWindowMillis);
        long totalTimeoutNanos = TimeUnit.MILLISECONDS.toNanos(totalTimeoutMillis);
        long pollIntervalNanos = TimeUnit.MILLISECONDS.toNanos(pollIntervalMillis);
        long startedAt = System.nanoTime();
        long nextRetryAtNanos = retryWindowNanos;
        while (true) {
            if (cancelled.getAsBoolean()) {
                return false;
            }
            if (economyPostgresReady.getAsBoolean()) {
                if (cancelled.getAsBoolean()) {
                    return false;
                }
                initialize.run();
                return true;
            }

            long elapsedNanos = System.nanoTime() - startedAt;
            long remainingNanos = totalTimeoutNanos - elapsedNanos;
            if (remainingNanos <= 0L) {
                throw new IllegalStateException(
                        "Timed out waiting for EconomyCore PostgreSQL schema readiness after bounded retries.");
            }
            if (elapsedNanos >= nextRetryAtNanos) {
                onRetry.run();
                nextRetryAtNanos = nextRetryAtNanos > totalTimeoutNanos - retryWindowNanos
                        ? totalTimeoutNanos
                        : nextRetryAtNanos + retryWindowNanos;
                continue;
            }

            long untilRetryNanos = nextRetryAtNanos - elapsedNanos;
            try {
                TimeUnit.NANOSECONDS.sleep(Math.min(remainingNanos, Math.min(untilRetryNanos, pollIntervalNanos)));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw interrupted;
            }
        }
    }

    @FunctionalInterface
    interface CheckedInitialization {
        void run() throws Exception;
    }
}
