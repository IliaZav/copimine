package me.copimine.artifacts;

/** Pure timing and restoration rules for the reusable Angel Wings artifact. */
public final class AngelWingsFlightPolicy {
    public static final int FLIGHT_SECONDS = 15;
    public static final int COOLDOWN_SECONDS = 300;
    public static final int COUNTDOWN_SECONDS = 3;

    private AngelWingsFlightPolicy() {
    }

    public static boolean canActivate(long nowSeconds, long cooldownUntilSeconds) {
        return cooldownUntilSeconds <= nowSeconds;
    }

    public static long nextCooldownUntil(long nowSeconds) {
        return nowSeconds + COOLDOWN_SECONDS;
    }

    /**
     * Returns the visible countdown value for a flight second, or -1 outside
     * the final three seconds.  Zero is intentionally returned at the exact
     * end so the player sees the final state before flight is restored.
     */
    public static int countdownSecondsAtElapsed(long elapsedSeconds) {
        long countdownStart = FLIGHT_SECONDS - COUNTDOWN_SECONDS;
        if (elapsedSeconds < countdownStart || elapsedSeconds > FLIGHT_SECONDS) {
            return -1;
        }
        return FLIGHT_SECONDS - (int) elapsedSeconds;
    }

    public static boolean shouldRevokeFlight(boolean hadAllowFlight, boolean creative, boolean spectator) {
        return !hadAllowFlight && !creative && !spectator;
    }
}
