package me.copimine.endevent.domain;

import java.util.Arrays;

/**
 * N23-P1-07: the bounded, per-viewer cost ladder for the server-emitted spell
 * presentation particles.
 *
 * <p>The interim emitter in {@code BossPresentationEffects} still ships the
 * cosmetic points from the server, and the reported cost is real: a 96-point
 * geometry drawn once per admitted viewer per frame is
 * {@code O(samples * viewers * frames)}. V6 requires a large, measured
 * reduction, and it requires the cost to fall on the many before it is allowed
 * to touch the few - a distant or peripheral viewer must never cost the same as
 * the one standing in the spell.</p>
 *
 * <p>This class owns only the arithmetic. It emits no Bukkit call and holds no
 * state, so the exact same ladder is the contract the future signed cast packet
 * (N23-P1-07) will hand to the client, and the interim emitter cannot silently
 * invent a different budget. Everything here is deterministic and bounded:
 *
 * <ul>
 *   <li>a viewer resolves to full, reduced or silhouette-only detail from its
 *       distance and the living party size, never from a random or wall-clock
 *       source;</li>
 *   <li>the near/mid distance bands tighten as the party grows, so a raid does
 *       not pay for twenty full-detail copies of the same effect;</li>
 *   <li>a frame ships at most {@link #framePointBudget} points across all
 *       viewers, spent nearest first, so the closest watchers keep the authored
 *       silhouette and only the surplus far viewers shed detail;</li>
 *   <li>the per-viewer count never drops below one point, so a spell is never
 *       rendered as nothing for a viewer who is still in range.</li>
 * </ul>
 */
public final class SpellVfxLodPolicy {
    /** Full authored detail. */
    public static final int LOD_FULL = 0;
    /** Half the authored detail; the default for mid-range and larger parties. */
    public static final int LOD_REDUCED = 1;
    /** A quarter of the authored detail; the far-viewer floor. */
    public static final int LOD_SILHOUETTE = 2;

    /** Default near band, in blocks, for a small party. */
    public static final double NEAR_BLOCKS = 14.0D;
    /** Default mid band, in blocks, for a small party. */
    public static final double MID_BLOCKS = 28.0D;

    /** Points one effect frame may ship across every viewer of a solo party. */
    public static final int FRAME_BUDGET_MIN = 128;
    /** Points added to the frame budget per extra living viewer. */
    public static final int FRAME_BUDGET_PER_PLAYER = 24;
    /** Hard ceiling on shipped points per effect frame, at any party size. */
    public static final int FRAME_BUDGET_CAP = 480;

    private SpellVfxLodPolicy() {
    }

    /** The coarsest LOD the authored detail allows; out-of-range input is clamped, never thrown. */
    public static int clampLod(int lod) {
        return Math.max(LOD_FULL, Math.min(LOD_SILHOUETTE, lod));
    }

    /**
     * How much detail one viewer may receive, from distance and party size.
     *
     * <p>The bands tighten with the party: a raid resolves its mid band by
     * 20 blocks and its far band beyond, so the geometric growth of
     * {@code viewers} is partly paid for by coarsening each viewer's copy.</p>
     */
    public static int lodForViewer(double distance, int livingPlayers) {
        int players = Math.max(1, livingPlayers);
        double d = Double.isFinite(distance) ? Math.max(0.0D, distance) : MID_BLOCKS;
        double near = players >= 16 ? 8.0D : players >= 10 ? 11.0D : NEAR_BLOCKS;
        double mid = players >= 16 ? 20.0D : players >= 10 ? 24.0D : MID_BLOCKS;
        if (d > mid) {
            return LOD_SILHOUETTE;
        }
        if (d > near) {
            return LOD_REDUCED;
        }
        return LOD_FULL;
    }

    /** The stride that walks an ordered point list at this LOD; always at least 1. */
    public static int sampleStride(int lod) {
        return switch (clampLod(lod)) {
            case LOD_FULL -> 1;
            case LOD_REDUCED -> 2;
            default -> 4;
        };
    }

    /**
     * The bounded number of points one viewer may receive.
     *
     * <p>Never zero for a positive base count: a viewer in range always keeps at
     * least the one point that anchors the effect, so LOD can coarsen a spell
     * but can never erase it.</p>
     */
    public static int pointsForViewer(int baseSamples, double distance, int livingPlayers) {
        if (baseSamples <= 0) {
            return 0;
        }
        int stride = sampleStride(lodForViewer(distance, livingPlayers));
        return Math.max(1, (baseSamples + stride - 1) / stride);
    }

    /** The per-frame point budget for a party, bounded and monotonic in the party size. */
    public static int framePointBudget(int livingPlayers) {
        int players = Math.max(1, livingPlayers);
        long raw = FRAME_BUDGET_MIN + (long) FRAME_BUDGET_PER_PLAYER * (players - 1);
        return (int) Math.min(FRAME_BUDGET_CAP, raw);
    }

    /**
     * The points a whole frame actually ships, spent nearest first.
     *
     * <p>This mirrors the emitter exactly: viewers are processed from closest to
     * furthest, each contributes its {@link #pointsForViewer} count while the
     * {@link #framePointBudget} lasts, and once it is spent the remaining
     * viewers contribute nothing. It is the number a diagnostics line and the
     * automated tests both use, so the emitter and the budget cannot drift.</p>
     *
     * @param distances viewer distances in blocks; order is irrelevant
     */
    public static int totalPoints(int baseSamples, double[] distances, int livingPlayers) {
        if (baseSamples <= 0 || distances == null || distances.length == 0) {
            return 0;
        }
        double[] sorted = distances.clone();
        Arrays.sort(sorted);
        int budget = framePointBudget(livingPlayers <= 0 ? sorted.length : livingPlayers);
        int spent = 0;
        for (double distance : sorted) {
            if (spent >= budget) {
                break;
            }
            int points = pointsForViewer(baseSamples, distance, livingPlayers);
            spent += Math.min(points, budget - spent);
        }
        return spent;
    }

    /**
     * The unbounded cost the same distances would have paid at full detail for
     * every viewer. Used only to state the reduction, never to ship points.
     */
    public static int unboundedPoints(int baseSamples, double[] distances) {
        if (baseSamples <= 0 || distances == null || distances.length == 0) {
            return 0;
        }
        return baseSamples * distances.length;
    }

    /** Stable diagnostics label; logs must not invent another. */
    public static String describe(int livingPlayers, int baseSamples, double[] distances) {
        int unbounded = unboundedPoints(baseSamples, distances);
        int bounded = totalPoints(baseSamples, distances, livingPlayers);
        return "vfx_lod base=" + Math.max(0, baseSamples)
                + " viewers=" + (distances == null ? 0 : distances.length)
                + " budget=" + framePointBudget(livingPlayers)
                + " shipped=" + bounded
                + " unbounded=" + unbounded
                + " saved=" + Math.max(0, unbounded - bounded);
    }
}
