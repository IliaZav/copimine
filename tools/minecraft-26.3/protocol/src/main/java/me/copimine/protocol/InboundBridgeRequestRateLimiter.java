package me.copimine.protocol;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Monotonic per-player admission policy for bounded inbound bridge requests. */
public final class InboundBridgeRequestRateLimiter {
    public static final long MIN_INTERVAL_NANOS = 50_000_000L;

    private final LongSupplier nanoClock;
    private final Map<UUID, Long> lastAdmittedAtNanos = new HashMap<>();

    public InboundBridgeRequestRateLimiter() {
        this(System::nanoTime);
    }

    public InboundBridgeRequestRateLimiter(LongSupplier nanoClock) {
        if (nanoClock == null) {
            throw new NullPointerException("nanoClock");
        }
        this.nanoClock = nanoClock;
    }

    /**
     * Admits one bounded packet for parsing. Call this before decoding, so malformed bounded packets
     * consume the same per-player interval as valid packets.
     */
    public synchronized boolean allow(UUID playerUuid) {
        if (playerUuid == null) {
            return false;
        }
        long nowNanos = nanoClock.getAsLong();
        Long previous = lastAdmittedAtNanos.get(playerUuid);
        if (previous != null && nowNanos - previous < MIN_INTERVAL_NANOS) {
            return false;
        }
        lastAdmittedAtNanos.put(playerUuid, nowNanos);
        return true;
    }

    public synchronized void remove(UUID playerUuid) {
        if (playerUuid != null) {
            lastAdmittedAtNanos.remove(playerUuid);
        }
    }

    public synchronized void clear() {
        lastAdmittedAtNanos.clear();
    }
}
