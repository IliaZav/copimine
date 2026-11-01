package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * W3-003 / W3-008: the three coordinated capture indicators that live directly
 * on the portal.
 *
 * <p>The existing authored portal model and its animation are preserved; this
 * policy only decides <em>what additional evidence</em> is drawn on top of it:</p>
 *
 * <ul>
 *   <li>a <b>segmented seal ring</b> whose lit segments report the amount of
 *       authoritative closing progress;</li>
 *   <li><b>directional particle streams</b> that flow inward while the seal is
 *       being closed and outward while it is recovering, so the direction of
 *       change is readable even when two samples have the same percentage;</li>
 *   <li>a few <b>bright surface seal veins</b> that light up on the portal
 *       membrane, proving that the real portal is being sealed rather than an
 *       unrelated ring being drawn on the floor.</li>
 * </ul>
 *
 * <p>Nothing here may fabricate progress: the caller feeds the same immutable
 * {@link PortalCapturePolicy.PortalState} that the server uses for gameplay, so
 * the ring cannot reach 100 % while the objective is still incomplete.</p>
 */
public final class PortalCaptureIndicatorPolicy {
    /** Segments of the seal ring; 24 keeps a full ring readable at 18 s of closing. */
    public static final int RING_SEGMENTS = 24;
    /** Number of surface seal veins; "a few" per the contract. */
    public static final int VEIN_COUNT = 5;
    /** Streams emitted per draw; bounded so the indicator stays cheap. */
    public static final int STREAM_POINTS = 6;
    /** Radius of the seal ring in blocks, just outside the capture circle. */
    public static final double RING_RADIUS_BLOCKS = PortalCapturePolicy.CAPTURE_RADIUS_BLOCKS + 0.05D;
    /** Height of the surface veins above the portal base. */
    public static final double VEIN_HEIGHT_BLOCKS = 1.9D;
    /** Veins appear once this share of progress exists; before that the seal is untouched. */
    public static final double VEIN_START_PROGRESS = 0.05D;

    private PortalCaptureIndicatorPolicy() {
    }

    /** Direction of the visual flow, derived from the authoritative change between two samples. */
    public enum Flow {
        /** Nothing is being sealed right now; no streams are drawn. */
        IDLE,
        /** Progress grew: streams flow inward, the seal is closing. */
        INWARD,
        /** Progress shrank: streams flow outward, the portal recovers. */
        OUTWARD
    }

    /**
     * @param progress       authoritative progress in {@code [0,1]}
     * @param previousProgress progress observed on the previous sample, or {@code -1} when unknown
     * @param completed      authoritative completion flag
     */
    public record Indicators(double progress,
                             int litSegments,
                             Flow flow,
                             double streamStrength,
                             int veinCount,
                             double veinBrightness,
                             boolean visible) {
        public Indicators {
            if (!Double.isFinite(progress)) throw new IllegalArgumentException("progress must be finite");
            if (progress < 0.0D || progress > 1.0D) {
                throw new IllegalArgumentException("progress must be within [0,1]");
            }
            if (litSegments < 0 || litSegments > RING_SEGMENTS) {
                throw new IllegalArgumentException("litSegments must be within the ring budget");
            }
            if (flow == null) throw new IllegalArgumentException("flow is required");
            if (!Double.isFinite(streamStrength) || streamStrength < 0.0D || streamStrength > 1.0D) {
                throw new IllegalArgumentException("streamStrength must be within [0,1]");
            }
            if (veinCount < 0 || veinCount > VEIN_COUNT) {
                throw new IllegalArgumentException("veinCount must be within the vein budget");
            }
            if (!Double.isFinite(veinBrightness) || veinBrightness < 0.0D || veinBrightness > 1.0D) {
                throw new IllegalArgumentException("veinBrightness must be within [0,1]");
            }
        }
    }

    public static final Indicators HIDDEN =
            new Indicators(0.0D, 0, Flow.IDLE, 0.0D, 0, 0.0D, false);

    /**
     * Compute the indicator set for one portal sample.
     *
     * @param state            authoritative portal state, may be {@code null}
     * @param active           whether this portal is the currently active one
     * @param previousProgress progress seen on the previous frame, or {@code -1}
     */
    public static Indicators evaluate(PortalCapturePolicy.PortalState state,
                                      boolean active,
                                      double previousProgress) {
        if (state == null) return HIDDEN;
        if (state.completed()) {
            // The seal is finished; the caller owns the collapse animation.
            return new Indicators(1.0D, RING_SEGMENTS, Flow.INWARD, 0.0D, 0, 0.0D, false);
        }
        double progress = Math.min(1.0D,
                state.progressMillis() / (double) PortalCapturePolicy.CAPTURE_MILLIS);
        // W3-008: an incomplete objective may never render a full ring. Rounding
        // 0.999 to "all segments" would fake the last percent the server has not
        // awarded yet, so the last segment is reserved for real completion.
        int lit = litSegments(progress);
        if (!active) {
            // Dormant portals keep a dim ring so players can see where the seal
            // was, but they never pretend to be closing.
            return new Indicators(progress, lit, Flow.IDLE, 0.0D, 0, 0.0D, true);
        }
        Flow flow = flow(progress, previousProgress);
        double strength = switch (flow) {
            case IDLE -> 0.0D;
            case INWARD, OUTWARD -> 0.35D + 0.65D * progress;
        };
        int veins = progress >= VEIN_START_PROGRESS
                ? Math.max(1, Math.min(VEIN_COUNT,
                (int) Math.ceil(VEIN_COUNT * progress)))
                : 0;
        double veinBrightness = veins == 0 ? 0.0D : 0.25D + 0.75D * progress;
        return new Indicators(progress, lit, flow, strength, veins, veinBrightness, true);
    }

    /**
     * Lit ring segments for a progress share. Never returns the full ring: the
     * final segment exists only for a genuinely completed objective.
     */
    static int litSegments(double progress) {
        if (!Double.isFinite(progress) || progress <= 0.0D) return 0;
        if (progress >= 1.0D) return RING_SEGMENTS;
        int rounded = (int) Math.round(RING_SEGMENTS * progress);
        return Math.max(0, Math.min(RING_SEGMENTS - 1, rounded));
    }

    /**
     * Flow between two authoritative samples. A repeated timestamp (identical
     * progress) yields {@link Flow#IDLE} rather than a fabricated direction, and
     * an unknown previous sample cannot invent a recovery.
     */
    public static Flow flow(double progress, double previousProgress) {
        if (!Double.isFinite(previousProgress) || previousProgress < 0.0D) return Flow.IDLE;
        if (progress > previousProgress + 1.0E-6D) return Flow.INWARD;
        if (progress < previousProgress - 1.0E-6D) return Flow.OUTWARD;
        return Flow.IDLE;
    }

    /**
     * Angles (radians) of every lit ring segment, so the ring never renders a
     * uniform full circle before the server says the portal is closed.
     */
    public static List<Double> litSegmentAngles(Indicators indicators) {
        Indicators safe = indicators == null ? HIDDEN : indicators;
        if (safe.litSegments() == 0) return List.of();
        List<Double> angles = new ArrayList<>(safe.litSegments());
        double step = Math.PI * 2.0D / RING_SEGMENTS;
        for (int segment = 0; segment < safe.litSegments(); segment++) {
            angles.add(-Math.PI / 2.0D + step * segment);
        }
        return List.copyOf(angles);
    }

    /**
     * Radians the vein/stream pattern rotates this frame. Positive while closing
     * (inward), negative while recovering (outward), zero when idle — the ring
     * and the veins therefore reverse together with the real progress.
     */
    public static double swirlRadians(Indicators indicators, long tick) {
        Indicators safe = indicators == null ? HIDDEN : indicators;
        long safeTick = Math.max(0L, tick);
        return switch (safe.flow()) {
            case IDLE -> 0.0D;
            case INWARD -> safeTick * 0.16D;
            case OUTWARD -> -safeTick * 0.12D;
        };
    }
}