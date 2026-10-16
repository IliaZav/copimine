package me.copimine.endevent.domain;

/** Deadline rules for the ten-minute transition-rune inactivity watchdog. */
public final class IntermissionWatchdogPolicy {
    public static final long TIMEOUT_MILLIS = 10L * 60_000L;

    private IntermissionWatchdogPolicy() { }

    /** Keep a persisted deadline; start one full watchdog window when it is absent. */
    public static long recoverDeadline(long persistedDeadlineMillis, long nowMillis) {
        if (persistedDeadlineMillis > 0L) return persistedDeadlineMillis;
        if (nowMillis > Long.MAX_VALUE - TIMEOUT_MILLIS) return Long.MAX_VALUE;
        return nowMillis + TIMEOUT_MILLIS;
    }

    public static boolean isExpired(long nowMillis, long deadlineMillis) {
        return deadlineMillis > 0L && nowMillis >= deadlineMillis;
    }
}
