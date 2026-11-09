package me.copimine.endevent.domain;

/**
 * Lease policy for the Wave 5 fog freeze pose.
 *
 * <p>The fog used to be a fixed ten-second stretch, so one 30 s
 * {@code WAVE_FROZEN} pose covered it and the comment beside the send said so.
 * The roster-frozen fog now runs 18 s on the first cycle and up to 26 s on the
 * last, and a server tick pause can stretch the wall-clock window further, so a
 * single 30 s lease no longer proves it outlives the fog.</p>
 *
 * <p>Re-sending the pose on every heartbeat is NOT an option: the pose packet is
 * one client-bridge envelope per viewer, the signed client accepts
 * {@code DEFAULT_PACKETS_PER_SECOND = 48} per connection, and the pause is only
 * {@code WAVE_FROZEN}. The original one-shot send exists precisely because that
 * storm overflowed the budget and the resulting counter gap retired every custom
 * model binding. The refresh therefore has to be rare.</p>
 *
 * <p>Budget, counted per viewer at the hard roster cap of 56 live actors
 * ({@code PressureBudgetController.HARD_CAP}):</p>
 * <ul>
 *   <li>every tick (50 ms): 56 / 0.05 = 1120 packets/s = 23x the 48/s budget —
 *       the original outage;</li>
 *   <li>every 5 s: 56 / 5 = 11.2 packets/s = 23% of the budget, leaving 36.8
 *       packets/s for beams, HUD and fog-state traffic.</li>
 * </ul>
 *
 * <p>The interval is therefore fixed at five seconds: comfortably inside the
 * budget, and short enough that the pose is refreshed many times over even the
 * longest fog.</p>
 */
public final class Wave5FogLeasePolicy {
    /** One 30 s pose used to cover the whole fog; kept as the protocol baseline. */
    public static final long BASE_LEASE_MILLIS = 30_000L;
    /** Pose re-sent while the fog holds, never more often than this. */
    public static final long REFRESH_INTERVAL_MILLIS = 5_000L;
    /** Sent once when the fog releases; the combat clip is a short cue. */
    public static final long RELEASE_LEASE_MILLIS = 500L;
    /** Longest fog the timing policy can produce: FOG_SECONDS + 8. */
    public static final long MAX_FOG_MILLIS = 26_000L;

    private Wave5FogLeasePolicy() {
    }

    /**
     * Duration carried by the FIRST frozen pose.
     *
     * <p>Deliberately longer than the longest fog plus a margin, so a single
     * lost refresh cannot expose a combat pose mid-fog. It is not unbounded:
     * the wave release path clears the lease explicitly, and a stale long lease
     * would outlive the wave itself.</p>
     */
    public static long initialLeaseMillis() {
        return 45_000L;
    }

    /**
     * True when the frozen pose is due to be re-sent.
     *
     * <p>A missing or non-positive timestamp means this is the first refresh,
     * which the caller treats as due so the lease is established immediately.
     * The comparison is inclusive: exactly one interval after the previous send
     * is the earliest moment the next one is allowed.</p>
     */
    public static boolean refreshDue(long lastRefreshMillis, long nowMillis) {
        if (lastRefreshMillis <= 0L) return true;
        if (nowMillis < lastRefreshMillis) return false;
        return nowMillis - lastRefreshMillis >= REFRESH_INTERVAL_MILLIS;
    }

    /**
     * The dead band that keeps a slow server from starving the refresh.
     *
     * <p>A refresh must never be so late that the pose expires before the next
     * one arrives. With a 45 s lease and a 5 s interval the pose is renewed
     * nine times over, so even a heavily delayed tick cannot drop it.</p>
     */
    public static boolean coversFog(int fogSeconds) {
        return fogSeconds >= 0
                && initialLeaseMillis() > (long) fogSeconds * 1_000L + REFRESH_INTERVAL_MILLIS;
    }

    /**
     * Packets per second a viewer receives for {@code actors} frozen mobs at
     * {@code intervalMillis} between refreshes. Used to keep the budget honest
     * in tests and diagnostics.
     */
    public static double refreshPacketsPerSecond(int actors, long intervalMillis) {
        if (actors <= 0 || intervalMillis <= 0L) return 0.0D;
        return actors * 1_000.0D / intervalMillis;
    }
}
