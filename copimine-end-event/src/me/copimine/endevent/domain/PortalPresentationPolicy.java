package me.copimine.endevent.domain;

import java.util.List;

/** Portal-specific visual projection of authoritative capture state. */
public final class PortalPresentationPolicy {
    public static final long COLLAPSE_MILLIS = 600L;
    public static final int GAUGE_SEGMENTS = 24;
    private PortalPresentationPolicy() {}

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
    public static boolean shouldRebuild(boolean visible, boolean present, int attempts) {
        return visible && !present && attempts == 0;
    }

    public record Frame(double progress, int gaugeSegments, double scale, int brightness, boolean visible) {}
}
