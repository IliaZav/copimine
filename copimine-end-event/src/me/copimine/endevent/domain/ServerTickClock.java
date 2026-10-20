package me.copimine.endevent.domain;

/** Logical time from scheduler ticks; wall-clock pauses cannot advance gameplay timers. */
public final class ServerTickClock {
    private ServerTickClock() { }
    public static long millis(long tick) {
        if (tick < 0L) throw new IllegalArgumentException("server tick must be non-negative");
        // Tick zero must differ from the legacy hold state's idle sentinel.
        return tick >= Long.MAX_VALUE / 50L - 1L ? Long.MAX_VALUE : (tick + 1L) * 50L;
    }
}
