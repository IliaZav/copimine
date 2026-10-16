package me.copimine.visualruntime;

import java.util.UUID;

/** Rejects completion callbacks from client commands superseded by a refresh or replacement. */
public final class VisualSessionCompletionPolicy {
    private VisualSessionCompletionPolicy() {
    }

    public static boolean isCurrent(UUID currentCommandEpoch, UUID callbackCommandEpoch) {
        return currentCommandEpoch != null && currentCommandEpoch.equals(callbackCommandEpoch);
    }

    public static boolean canApplyFallback(UUID currentCommandEpoch, UUID callbackCommandEpoch,
            long sessionUntilMillis, long nowMillis) {
        return isCurrent(currentCommandEpoch, callbackCommandEpoch) && sessionUntilMillis > nowMillis;
    }

    public static int remainingSecondsUntil(long deadlineMillis, long nowMillis) {
        if (deadlineMillis <= nowMillis) {
            return 0;
        }
        long remainingMillis = deadlineMillis - nowMillis;
        return (int) Math.max(1L, Math.min(600L, (long) Math.ceil(remainingMillis / 1000.0D)));
    }
}
