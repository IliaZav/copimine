package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteVisualRuntimeRateLimiterTest {
    @Test
    void allowsTheFirstRuntimeTransitionAndLimitsBursts() {
        RemoteVisualRuntimeRateLimiter limiter = new RemoteVisualRuntimeRateLimiter(2_000_000_000L);

        assertTrue(limiter.tryAcquire(1_000L));
        assertFalse(limiter.tryAcquire(1_999_999_999L));
        assertTrue(limiter.tryAcquire(2_000_001_000L));
    }

    @Test
    void allowsTransitionsAtTheIntervalBoundary() {
        RemoteVisualRuntimeRateLimiter limiter = new RemoteVisualRuntimeRateLimiter(1_000L);

        assertTrue(limiter.tryAcquire(5_000L));
        assertTrue(limiter.tryAcquire(6_000L));
    }

    @Test
    void rejectsNonPositiveIntervals() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new RemoteVisualRuntimeRateLimiter(0L)
        );
    }
}
