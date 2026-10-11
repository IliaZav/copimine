package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteDiagnosticRateLimiterTest {
    @Test
    void limitsRemotePayloadLoggingWithoutRetainingPerMessageKeys() {
        RemoteDiagnosticRateLimiter limiter = new RemoteDiagnosticRateLimiter(5_000_000_000L);

        assertTrue(limiter.tryAcquire(1_000L));
        assertFalse(limiter.tryAcquire(2_000_000_000L));
        assertFalse(limiter.tryAcquire(4_999_999_999L));
        assertTrue(limiter.tryAcquire(5_000_001_000L));
    }
}
