package me.copimine.client;

/** Bounds unauthenticated per-connection work before a bridge envelope is copied or decoded. */
final class ClientBridgeReceiveRateLimiter {
    private static final int DEFAULT_BURST = 96;
    private static final int DEFAULT_PACKETS_PER_SECOND = 48;

    private final int capacity;
    private final int refillPerSecond;
    private double availableTokens;
    private long lastRefillNanos;
    private boolean hasLastRefill;

    ClientBridgeReceiveRateLimiter() {
        this(DEFAULT_BURST, DEFAULT_PACKETS_PER_SECOND);
    }

    ClientBridgeReceiveRateLimiter(int capacity, int refillPerSecond) {
        if (capacity < 1 || refillPerSecond < 0) {
            throw new IllegalArgumentException("Invalid bridge receive rate limit");
        }
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
        this.availableTokens = capacity;
    }

    synchronized boolean tryAcquire(long nowNanos) {
        if (!hasLastRefill) {
            lastRefillNanos = nowNanos;
            hasLastRefill = true;
        } else {
            // System.nanoTime() may be negative; only the elapsed-time difference matters.
            long elapsedNanos = nowNanos - lastRefillNanos;
            if (elapsedNanos > 0L) {
                availableTokens = Math.min(capacity,
                        availableTokens + elapsedNanos * (refillPerSecond / 1_000_000_000.0));
                lastRefillNanos = nowNanos;
            }
        }
        if (availableTokens < 1.0) {
            return false;
        }
        availableTokens -= 1.0;
        return true;
    }

    synchronized void reset() {
        availableTokens = capacity;
        hasLastRefill = false;
    }
}
