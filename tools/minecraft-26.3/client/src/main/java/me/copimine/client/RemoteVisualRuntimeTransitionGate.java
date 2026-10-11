package me.copimine.client;

/** Shares one cooldown across server-triggered Iris apply and restore operations. */
final class RemoteVisualRuntimeTransitionGate {
    private final RemoteVisualRuntimeRateLimiter rateLimiter;

    RemoteVisualRuntimeTransitionGate(long intervalNanos) {
        this.rateLimiter = new RemoteVisualRuntimeRateLimiter(intervalNanos);
    }

    boolean allow(boolean serverTriggered, boolean requiresIrisReload, long nowNanos) {
        return !serverTriggered || !requiresIrisReload || rateLimiter.tryAcquire(nowNanos);
    }
}
