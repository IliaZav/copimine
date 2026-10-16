package me.copimine.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ClientBridgeReceiveRateLimiterTest {
    @Test
    void boundsBurstAndRefillsGradually() {
        ClientBridgeReceiveRateLimiter limiter = new ClientBridgeReceiveRateLimiter(2, 2);

        assertTrue(limiter.tryAcquire(0L));
        assertTrue(limiter.tryAcquire(0L));
        assertFalse(limiter.tryAcquire(0L));
        assertFalse(limiter.tryAcquire(499_999_999L));
        assertTrue(limiter.tryAcquire(500_000_000L));
        assertFalse(limiter.tryAcquire(500_000_000L));
    }

    @Test
    void resetRestoresBurstAcrossConnectionBoundaries() {
        ClientBridgeReceiveRateLimiter limiter = new ClientBridgeReceiveRateLimiter(1, 0);

        assertTrue(limiter.tryAcquire(-5_000_000_000L));
        assertFalse(limiter.tryAcquire(-5_000_000_000L));

        limiter.reset();

        assertTrue(limiter.tryAcquire(-6_000_000_000L));
        assertFalse(limiter.tryAcquire(-6_000_000_000L));
    }

    @Test
    void acceptsNegativeMonotonicClockOrigins() {
        ClientBridgeReceiveRateLimiter limiter = new ClientBridgeReceiveRateLimiter(1, 2);

        assertTrue(limiter.tryAcquire(-1_000_000_000L));
        assertFalse(limiter.tryAcquire(-1_000_000_000L));
        assertFalse(limiter.tryAcquire(-500_000_001L));
        assertTrue(limiter.tryAcquire(-500_000_000L));
    }
}
