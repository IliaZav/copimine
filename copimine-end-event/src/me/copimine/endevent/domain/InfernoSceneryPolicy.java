package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The authored Phase 4 (OVERLOAD) overhead scenery, as pure geometry.
 *
 * <p>V6 9E.01-9E.03 is categorical that Phase 4 must be an infernal <b>world</b>
 * with vertical drama, not a magma floor swap: a sky vortex above the Core,
 * ember storms at two heights drifting in opposite directions, fire arches on
 * the arena rim, an overhead energy cloud, and one legible moving fire front.
 * The same text is equally categorical that this scenery stays
 * <b>client-local</b> and that no more than a few dominant silhouettes may
 * compete at once.</p>
 *
 * <p>This class therefore owns only the geometry and the budget. It emits no
 * Bukkit calls, so the exact same layer definition can drive the client mesh
 * once the signed cast packet lands (N23-P1-07), and the interim server emitter
 * cannot silently invent a different, denser scene.</p>
 *
 * <p>Every rule here is bounded and deterministic: a fixed per-layer point
 * count, a total budget that grows with the party but never past
 * {@link #BUDGET_CAP}, an orbit seeded only by the frame index, and an LOD
 * ladder that drops secondary layers before it drops the silhouette.</p>
 */
public final class InfernoSceneryPolicy {
    /**
     * The authored layers, in draw-priority order.
     *
     * <p>The order is also the LOD order: the first entry is the silhouette that
     * survives every setting, the last is the first thing dropped.</p>
     */
    public enum Layer {
        /** The giant upside-down fire vortex 15-25 blocks above the Core. */
        SKY_VORTEX(19.0D, 14.0D, 0.30D, 16, 2),
        /** The one legible moving fire front, kept as a rim silhouette. */
        FIRE_FRONT(0.0D, 2.0D, 0.16D, 12, 2),
        /** Upper ember storm: slow, high, drifting one way. */
        EMBER_STORM_HIGH(24.0D, 9.0D, 0.14D, 12, 1),
        /** Lower ember storm: faster, lower, drifting the other way (parallax). */
        EMBER_STORM_LOW(20.0D, 4.0D, 0.14D, 12, 1),
        /** Overhead energy cloud hugging the vortex skirt. */
        ENERGY_CLOUD(16.0D, 11.0D, 0.12D, 10, 1),
        /** Twisted obsidian arch/fragment silhouettes around the arena edge. */
        FIRE_ARCH(26.0D, 7.0D, 0.14D, 10, 0);

        private final double radius;
        private final double height;
        private final double share;
        private final int count;
        private final int maxLod;

        Layer(double radius, double height, double share, int count, int maxLod) {
            this.radius = radius;
            this.height = height;
            this.share = share;
            this.count = count;
            this.maxLod = maxLod;
        }

        /** Horizontal radius of the layer in blocks. */
        public double radius() {
            return radius;
        }

        /** Height above the Core's combat floor. */
        public double height() {
            return height;
        }

        /** Fraction of the per-tick budget this layer may spend. */
        public double budgetShare() {
            return share;
        }

        /** The fixed number of sample points in one frame of this layer. */
        public int pointCount() {
            return count;
        }

        /** The coarsest LOD (0 = full, 2 = silhouette only) at which this layer still draws. */
        public int maxLod() {
            return maxLod;
        }
    }

    /** Hard ceiling on scenery points per frame, at any party size. */
    public static final int BUDGET_CAP = 96;
    /** The solo-party budget; even one player sees the vortex and the front. */
    public static final int BUDGET_MIN = 36;
    /** Points added per living player before the cap. */
    public static final int BUDGET_PER_PLAYER = 3;

    /** A full frame is 20 ticks; the orbit phase advances once per frame. */
    public static final int FRAME_TICKS = 20;
    /** Full, reduced and silhouette-only. */
    public static final int LOD_FULL = 0;
    public static final int LOD_REDUCED = 1;
    public static final int LOD_SILHOUETTE = 2;

    private InfernoSceneryPolicy() {
    }

    /** The per-frame point budget for a party, bounded and monotonic. */
    public static int budgetFor(int livingPlayers) {
        int players = Math.max(1, livingPlayers);
        long raw = BUDGET_MIN + (long) BUDGET_PER_PLAYER * (players - 1);
        return (int) Math.min(BUDGET_CAP, raw);
    }

    /** Whether the layer is drawn at the given LOD. */
    public static boolean drawsAt(Layer layer, int lod) {
        return layer != null && Math.max(LOD_FULL, Math.min(LOD_SILHOUETTE, lod)) <= layer.maxLod();
    }

    /**
     * How many points a layer may spend this frame.
     *
     * <p>The share is applied to the party budget, then clamped to the layer's
     * own authored point count, so a large party cannot turn one layer into a
     * dense cloud and the total can never exceed {@link #budgetFor}.</p>
     */
    public static int pointsFor(Layer layer, int livingPlayers, int lod) {
        if (!drawsAt(layer, lod)) {
            return 0;
        }
        int share = (int) Math.floor(budgetFor(livingPlayers) * layer.budgetShare());
        int points = Math.min(layer.pointCount(), Math.max(0, share));
        if (lod == LOD_SILHOUETTE) {
            // The silhouette keeps its shape but sheds detail.
            points = Math.min(points, Math.max(4, layer.pointCount() / 2));
        }
        return points;
    }

    /** The total points a full frame actually spends; never above the budget. */
    public static int framePoints(int livingPlayers, int lod) {
        int total = 0;
        for (Layer layer : Layer.values()) {
            total += pointsFor(layer, livingPlayers, lod);
        }
        // The authored shares sum to exactly 1.0 and each layer is additionally
        // clamped to its own point count, so the sum can only fall below the
        // budget. Clamping here as well keeps that invariant explicit.
        return Math.min(total, budgetFor(livingPlayers));
    }

    /**
     * One deterministic point of a circular layer.
     *
     * <p>The orbit angle is derived from the sample index and the frame number,
     * so the scene is reproducible from the tick alone and never needs random
     * state. {@code driftSign} reverses the direction for the opposing ember
     * storms.</p>
     *
     * @return {@code {x, y, z}} in world space
     */
    public static double[] orbitPoint(Layer layer, int index, int points, int frame, double driftSign,
                                      double centerX, double centerY, double centerZ) {
        if (layer == null || points <= 0 || !Double.isFinite(centerX)
                || !Double.isFinite(centerY) || !Double.isFinite(centerZ)) {
            return null;
        }
        double step = (2.0D * Math.PI) / points;
        double angle = step * (index % points) + driftSign * 0.05D * frame;
        // A gentle vertical wave keeps the ring from reading as a flat disc.
        double wave = Math.sin(angle * 2.0D + frame * 0.13D) * 1.1D;
        return new double[]{
                centerX + Math.cos(angle) * layer.radius(),
                centerY + layer.height() + wave,
                centerZ + Math.sin(angle) * layer.radius()
        };
    }

    /**
     * Every point of one layer for one frame.
     *
     * <p>Bounded by {@link #pointsFor}, so a caller can never be handed an
     * unbounded list. Returns an empty list for a layer that is not drawn at
     * this LOD rather than null, so the emitter has one shape to handle.</p>
     */
    public static List<double[]> layerPoints(Layer layer, int livingPlayers, int lod, int frame,
                                             double centerX, double centerY, double centerZ) {
        int points = pointsFor(layer, livingPlayers, lod);
        if (points <= 0) {
            return Collections.emptyList();
        }
        double driftSign = layer == Layer.EMBER_STORM_LOW || layer == Layer.FIRE_ARCH ? -1.0D : 1.0D;
        List<double[]> out = new ArrayList<>(points);
        for (int i = 0; i < points; i++) {
            double[] point = orbitPoint(layer, i, points, frame, driftSign,
                    centerX, centerY, centerZ);
            if (point != null) {
                out.add(point);
            }
        }
        return out;
    }

    /** Stable diagnostics label; logs must not invent another. */
    public static String describe(int livingPlayers, int lod) {
        return "budget=" + budgetFor(livingPlayers) + " lod=" + lod
                + " frame_points=" + framePoints(livingPlayers, lod);
    }
}
