package me.copimine.endevent.domain;

/** One authoritative opening clock per wave, independent of the three combat groups. */
public final class WaveTwoCinematicPolicy {
    public static final long INTRO_MILLIS = 10_000L;
    private WaveTwoCinematicPolicy() { }

    public static long age(long startedAtMillis, long nowMillis) {
        if (startedAtMillis <= 0L || nowMillis < startedAtMillis) return 0L;
        return Math.min(INTRO_MILLIS, nowMillis - startedAtMillis);
    }

    public static boolean combatReady(long startedAtMillis, long nowMillis) {
        return startedAtMillis > 0L && age(startedAtMillis, nowMillis) >= INTRO_MILLIS;
    }

    public static long restoreStart(String persisted, long nowMillis, int groupsAlreadySpawned) {
        if (persisted == null || persisted.isBlank())
            return groupsAlreadySpawned > 0 ? Math.max(1L, nowMillis - INTRO_MILLIS) : nowMillis;
        long started = Long.parseLong(persisted);
        if (started <= 0L || started > nowMillis)
            throw new IllegalArgumentException("Wave 2 intro clock is outside the current timeline");
        return started;
    }
}
