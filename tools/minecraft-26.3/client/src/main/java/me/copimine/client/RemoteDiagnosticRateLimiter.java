package me.copimine.client;

/** Fixed-memory limiter for diagnostics caused by remote server payloads. */
final class RemoteDiagnosticRateLimiter {
    private final long intervalNanos;
    private long lastAcceptedAtNanos;
    private boolean hasAccepted;

    RemoteDiagnosticRateLimiter(long intervalNanos) {
        if (intervalNanos <= 0L) throw new IllegalArgumentException("intervalNanos must be positive");
        this.intervalNanos = intervalNanos;
    }

    synchronized boolean tryAcquire(long nowNanos) {
        if (hasAccepted && nowNanos - lastAcceptedAtNanos < intervalNanos) return false;
        lastAcceptedAtNanos = nowNanos;
        hasAccepted = true;
        return true;
    }
}
