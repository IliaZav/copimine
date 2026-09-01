package me.copimine.endevent.domain;

/** Deterministic first-shot timing for a bounded group of Rift Obelisks. */
public final class RiftObeliskTimingPolicy {
    public static final int MIN_STAGGER_TICKS = 5;

    private RiftObeliskTimingPolicy() {
    }

    public static int staggerTicks(int fireIntervalTicks, int totalObelisks) {
        int interval = Math.max(1, fireIntervalTicks);
        int total = Math.max(1, Math.min(RiftObeliskScalingPolicy.MAX_ACTIVE, totalObelisks));
        return Math.max(MIN_STAGGER_TICKS, interval / (total + 1));
    }

    public static long firstFireTick(long activationTick, int fireIntervalTicks,
                                     int obeliskIndex, int totalObelisks) {
        int total = Math.max(1, Math.min(RiftObeliskScalingPolicy.MAX_ACTIVE, totalObelisks));
        int index = Math.max(0, Math.min(total - 1, obeliskIndex));
        return activationTick + Math.max(1, fireIntervalTicks)
                + (long) index * staggerTicks(fireIntervalTicks, total);
    }
}
