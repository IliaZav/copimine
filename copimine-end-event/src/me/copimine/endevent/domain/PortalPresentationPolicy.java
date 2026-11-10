package me.copimine.endevent.domain;

import java.util.List;

/** Portal-specific visual projection of authoritative capture state. */
public final class PortalPresentationPolicy {
    public static final long COLLAPSE_MILLIS = 600L;
    public static final int MAX_REBUILD_ATTEMPTS = 3;
    public static final long REBUILD_RETRY_BASE_MILLIS = 250L;
    public static final int GAUGE_SEGMENTS = 24;
    /** How many shards the closing gate throws toward its own centre. */
    public static final int COLLAPSE_SHARD_COUNT = 14;
    /** Full turns the shard ring travels while the gate folds shut. */
    public static final double COLLAPSE_SPIN_TURNS = 1.35D;
    /** Continuous idle orbit of the shard layer, in turns per second. */
    public static final double IDLE_SPIN_TURNS_PER_SECOND = 0.11D;
    private PortalPresentationPolicy() {}

    /**
     * Fraction of the collapse window already spent, {@code 0..1}.
     *
     * <p>W3-01: the gate used to shrink on a single linear factor and then
     * vanish. The closing sequence now needs a real timeline — the fold, the
     * inward shard rush and the final burst all read this one fraction, so they
     * can never disagree about when the gate is closed. A missing or future
     * deadline reports {@code 0}, never a negative or NaN value.</p>
     */
    public static double collapseProgress(long closingAt, long now) {
        if (closingAt < 0L || now <= closingAt) return 0.0D;
        return Math.min(1.0D, (now - closingAt) / (double) COLLAPSE_MILLIS);
    }

    /**
     * How far the membrane has folded inward, {@code 1..0}.
     *
     * <p>Ease-out: the gate rushes in at once and then hangs for a beat before
     * it is gone, which reads as a fold rather than a linear scale-down.</p>
     */
    public static double collapseFoldScale(double progress) {
        double clamped = clamp01(progress);
        double eased = 1.0D - Math.pow(1.0D - clamped, 2.0D);
        return Math.max(0.0D, 1.0D - 0.85D * eased);
    }

    /** Radians the shard ring has rotated around the gate axis. */
    public static double collapseSpinRadians(double progress) {
        return Math.PI * 2.0D * COLLAPSE_SPIN_TURNS * clamp01(progress);
    }

    /**
     * Implosion intensity, {@code 0..1}, peaking mid-collapse.
     *
     * <p>Zero on both ends keeps the burst a discrete event: the shaft of
     * inward streams is only visible while the gate is actually folding.</p>
     */
    public static double collapseImplosionIntensity(double progress) {
        return Math.sin(clamp01(progress) * Math.PI);
    }

    /** Slow orbit that keeps the standing gate reading as a solid 3D object. */
    public static double idleSpinRadians(long nowMillis) {
        return Math.PI * 2.0D * IDLE_SPIN_TURNS_PER_SECOND
                * (Math.max(0L, nowMillis) / 1000.0D);
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) return 0.0D;
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    public static Frame frame(PortalCapturePolicy.PortalState state, boolean active, long closingAt, long now) {
        if (state == null || now < 0) return new Frame(0, 0, 0, 0, false);
        double progress = Math.min(1, state.progressMillis() / (double) PortalCapturePolicy.CAPTURE_MILLIS);
        if (state.completed()) {
            if (closingAt < 0 || collapseFinished(closingAt, now)) return new Frame(1, 0, 0, 0, false);
            double remaining = 1 - Math.min(1, Math.max(0, now - closingAt) / (double) COLLAPSE_MILLIS);
            return new Frame(1, GAUGE_SEGMENTS, 0.65 * remaining, 15, true);
        }
        if (!active) return new Frame(progress, 0, 0.8, 4, true);
        return new Frame(progress, (int) Math.round(GAUGE_SEGMENTS * progress), 1 - 0.35 * progress, 15, true);
    }

    public static int activeIndex(List<PortalCapturePolicy.PortalState> states) {
        for (int index = 0; index < states.size(); index++) if (!states.get(index).completed()) return index;
        return -1;
    }

    public static boolean collapseFinished(long closingAt, long now) {
        return closingAt >= 0 && now >= closingAt && now - closingAt >= COLLAPSE_MILLIS;
    }
    public static boolean shouldRebuild(boolean visible, boolean present, int attempts,
                                        long now, long retryAtMillis) {
        return visible && !present && attempts >= 0 && attempts < MAX_REBUILD_ATTEMPTS
                && now >= retryAtMillis;
    }

    public static long retryDelayMillis(int retryNumber) {
        int boundedRetry = Math.max(1, Math.min(4, retryNumber));
        return REBUILD_RETRY_BASE_MILLIS * boundedRetry;
    }

    public record Frame(double progress, int gaugeSegments, double scale, int brightness, boolean visible) {}

    public enum Layer { FRAME, INNER, SHARD }
    public record Placement(float scale, float translateY) { }

    /** Authored bounds are X[-8,24], Y[-8,32]; NONE subtracts (8,8,8). */
    public static Placement placement(Frame frame, boolean completed, Layer layer) {
        double factor = completed ? frame.scale() : switch (layer) {
            case FRAME -> 1.0D;
            case INNER -> frame.scale();
            case SHARD -> 1.0D - frame.progress() * .25D;
        };
        float scale = (float) (2.24D * Math.max(0.0D, factor));
        // Contract energy about the membrane's centre; the solid frame stays grounded.
        return new Placement(scale, 2.8F - scale * .25F);
    }
}
