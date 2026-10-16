package me.copimine.endevent.domain;

/** Refresh at the next deadline, independent of the scheduler's clock phase. */
public final class VisualRefreshDeadline {
    private final long intervalMillis;
    private long nextMillis;

    public VisualRefreshDeadline(long intervalMillis) {
        if (intervalMillis <= 0L) throw new IllegalArgumentException("positive interval required");
        this.intervalMillis = intervalMillis;
    }

    public boolean shouldRefresh(long nowMillis) {
        if (nowMillis < 0L || nowMillis < nextMillis) return false;
        nextMillis = nowMillis > Long.MAX_VALUE - intervalMillis
                ? Long.MAX_VALUE : nowMillis + intervalMillis;
        return true;
    }

    public void reset() { nextMillis = 0L; }
}
