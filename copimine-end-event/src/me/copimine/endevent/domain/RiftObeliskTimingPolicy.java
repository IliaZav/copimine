package me.copimine.endevent.domain;

/** Deterministic first-shot timing for a bounded group of Rift Obelisks. */
public final class RiftObeliskTimingPolicy {
    public static final int MIN_STAGGER_TICKS = 5;
    public static final int MAX_OBELISKS = 6;

    private RiftObeliskTimingPolicy() {
    }

    public static int staggerTicks(int fireIntervalTicks, int totalObelisks) {
        int interval = Math.max(1, fireIntervalTicks);
        int total = validateTotal(totalObelisks);
        return Math.max(MIN_STAGGER_TICKS, interval / (total + 1));
    }

    public static long firstFireTick(long activationTick, int fireIntervalTicks,
                                     int obeliskIndex, int totalObelisks) {
        int total = validateTotal(totalObelisks);
        if (obeliskIndex < 0 || obeliskIndex >= total) {
            throw new IllegalArgumentException("obelisk index is outside the current W4 profile");
        }
        int index = obeliskIndex;
        return activationTick + Math.max(1, fireIntervalTicks)
                + (long) index * staggerTicks(fireIntervalTicks, total);
    }

    private static int validateTotal(int totalObelisks) {
        if (totalObelisks < 1 || totalObelisks > MAX_OBELISKS) {
            throw new IllegalArgumentException("obelisk count is outside the current W4 profile");
        }
        return totalObelisks;
    }
}
