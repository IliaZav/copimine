package me.copimine.client;

/** Fixed-memory limiter for expensive server-triggered shader runtime changes. */
final class RemoteVisualRuntimeRateLimiter {
    private final long intervalNanos;
    private long lastAcceptedAtNanos;
    private boolean hasAccepted;

    RemoteVisualRuntimeRateLimiter(long intervalNanos) {
        if (intervalNanos <= 0L) {
            throw new IllegalArgumentException("intervalNanos must be positive");
        }
        this.intervalNanos = intervalNanos;
    }

    synchronized boolean tryAcquire(long nowNanos) {
        if (hasAccepted && nowNanos - lastAcceptedAtNanos < intervalNanos) {
            return false;
        }
        lastAcceptedAtNanos = nowNanos;
        hasAccepted = true;
        return true;
    }
}
