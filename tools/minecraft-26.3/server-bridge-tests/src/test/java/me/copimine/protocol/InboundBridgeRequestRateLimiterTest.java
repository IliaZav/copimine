package me.copimine.protocol;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

final class InboundBridgeRequestRateLimiterTest {
    @Test
    void limitsEachPlayerByMonotonicFiftyMillisecondIntervals() {
        AtomicLong now = new AtomicLong(10_000L);
        InboundBridgeRequestRateLimiter limiter = new InboundBridgeRequestRateLimiter(now::get);
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();

        assertTrue(limiter.allow(firstPlayer));
        now.addAndGet(49_999_999L);
        assertFalse(limiter.allow(firstPlayer));
        assertTrue(limiter.allow(secondPlayer), "another player has an independent interval");

        now.incrementAndGet();
        assertTrue(limiter.allow(firstPlayer), "the exact 50 ms boundary is accepted");
        assertFalse(limiter.allow(firstPlayer));
    }

    @Test
    void malformedBoundedRequestConsumesTheSamePerPlayerInterval() {
        AtomicLong now = new AtomicLong(0L);
        InboundBridgeRequestRateLimiter limiter = new InboundBridgeRequestRateLimiter(now::get);
        UUID player = UUID.randomUUID();

        // The listener calls allow before decode, so a malformed bounded packet consumes this slot.
        boolean malformedPacketAcceptedForParsing = limiter.allow(player);
        assertTrue(malformedPacketAcceptedForParsing);
        assertFalse(limiter.allow(player), "a following packet inside 50 ms is dropped before parsing");

        now.addAndGet(InboundBridgeRequestRateLimiter.MIN_INTERVAL_NANOS);
        assertTrue(limiter.allow(player));
    }

    @Test
    void removeAndClearRetirePlayerIntervals() {
        AtomicLong now = new AtomicLong(0L);
        InboundBridgeRequestRateLimiter limiter = new InboundBridgeRequestRateLimiter(now::get);
        UUID player = UUID.randomUUID();

        assertTrue(limiter.allow(player));
        limiter.remove(player);
        assertTrue(limiter.allow(player));

        limiter.clear();
        assertTrue(limiter.allow(player));
    }
}
