package me.copimine.dungeon.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure editor-overlay sampler (spec OV4.3). Given a cuboid it produces the
 * client-local particle/line sample points for the twelve cuboid edges, the
 * four vertical corner pillars and optional plane guides.
 *
 * <p>The sampler is bounded and LOD-aware: the point budget is honoured for
 * large rooms so the editor never emits an unbounded stream. It is deliberately
 * server-free so the exact geometry can be unit-tested.</p>
 */
public final class EditorOverlay {

    /** Default per-object point budget. */
    public static final int DEFAULT_BUDGET = 512;
    /** Hard ceiling for a single object regardless of requested budget. */
    public static final int HARD_BUDGET = 4096;

    private EditorOverlay() {
    }

    /** An overlay object to render. */
    public record Entry(OverlayType type, Cuboid bounds, boolean selected) {
        public Entry {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(bounds, "bounds");
        }
    }

    public static List<OverlaySample> sample(OverlayType type, Cuboid bounds, int budget) {
        return sample(type, bounds, budget, true, true);
    }

    public static List<OverlaySample> sample(OverlayType type, Cuboid bounds, int budget,
                                             boolean includePillars, boolean includePlaneGuides) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(bounds, "bounds");
        int limit = clampBudget(budget);
        int steps = lodSteps(bounds, limit, includePillars, includePlaneGuides);

        List<OverlaySample> points = new ArrayList<>();

        // 1) all twelve cuboid edges.
        for (int[][] edge : bounds.edgeSegments()) {
            appendSegment(points, type, edge[0], edge[1], steps, false);
        }

        // 2) four vertical corner pillars.
        if (includePillars) {
            for (int[] p : bounds.verticalCornerPillars(steps)) {
                points.add(OverlaySample.of(type, p[0], p[1], p[2], true));
            }
        }

        // 3) optional mid-plane guides.
        if (includePlaneGuides) {
            for (int[][] segment : planeGuideSegments(bounds)) {
                appendSegment(points, type, segment[0], segment[1], steps, false);
            }
        }

        return cap(points, limit);
    }

    /** Samples a whole visible set of objects within one shared budget. */
    public static List<OverlaySample> sampleAll(List<Entry> entries, int budget) {
        Objects.requireNonNull(entries, "entries");
        if (entries.isEmpty()) {
            return List.of();
        }
        int total = clampBudget(budget);
        int perEntry = Math.max(8, total / entries.size());
        List<OverlaySample> points = new ArrayList<>();
        for (Entry entry : entries) {
            List<OverlaySample> object = sample(entry.type(), entry.bounds(),
                    perEntry, entry.selected(), entry.selected());
            points.addAll(object);
        }
        return cap(points, total);
    }

    /** The twelve mid-plane guide segments (three rectangles). */
    public static List<int[][]> planeGuideSegments(Cuboid c) {
        List<int[][]> guides = new ArrayList<>(12);
        int cx = (c.minX() + c.maxX()) / 2;
        int cy = (c.minY() + c.maxY()) / 2;
        int cz = (c.minZ() + c.maxZ()) / 2;
        // YZ rectangle at centre X.
        addRectangle(guides,
                new int[]{cx, c.minY(), c.minZ()}, new int[]{cx, c.maxY(), c.minZ()},
                new int[]{cx, c.maxY(), c.maxZ()}, new int[]{cx, c.minY(), c.maxZ()});
        // XZ rectangle at centre Y.
        addRectangle(guides,
                new int[]{c.minX(), cy, c.minZ()}, new int[]{c.maxX(), cy, c.minZ()},
                new int[]{c.maxX(), cy, c.maxZ()}, new int[]{c.minX(), cy, c.maxZ()});
        // XY rectangle at centre Z.
        addRectangle(guides,
                new int[]{c.minX(), c.minY(), cz}, new int[]{c.maxX(), c.minY(), cz},
                new int[]{c.maxX(), c.maxY(), cz}, new int[]{c.minX(), c.maxY(), cz});
        return guides;
    }

    private static void addRectangle(List<int[][]> out, int[] a, int[] b, int[] d, int[] e) {
        out.add(new int[][]{a, b});
        out.add(new int[][]{b, d});
        out.add(new int[][]{d, e});
        out.add(new int[][]{e, a});
    }

    /** Convenience for tests: deterministic number of edge samples for a cuboid. */
    public static int edgePointCount(Cuboid c, int steps) {
        return c.edgeCount() * (Math.max(0, steps) + 1);
    }

    static int lodSteps(Cuboid c, int budget, boolean includePillars, boolean includePlaneGuides) {
        int segmentsPerStep = 12; // primary edges
        if (includePillars) {
            segmentsPerStep += 4;
        }
        if (includePlaneGuides) {
            segmentsPerStep += 12;
        }
        int maxDim = Math.max(c.sizeX(), Math.max(c.sizeY(), c.sizeZ()));
        int byBudget = (budget / segmentsPerStep) - 1;
        int steps = Math.max(1, Math.min(maxDim, byBudget));
        return steps;
    }

    private static void appendSegment(List<OverlaySample> out, OverlayType type,
                                      int[] a, int[] b, int steps, boolean pillar) {
        for (int i = 0; i <= steps; i++) {
            int x = a[0] + (int) Math.round((double) (b[0] - a[0]) * i / steps);
            int y = a[1] + (int) Math.round((double) (b[1] - a[1]) * i / steps);
            int z = a[2] + (int) Math.round((double) (b[2] - a[2]) * i / steps);
            out.add(OverlaySample.of(type, x, y, z, pillar));
        }
    }

    private static List<OverlaySample> cap(List<OverlaySample> points, int limit) {
        if (points.size() <= limit) {
            return points;
        }
        return new ArrayList<>(points.subList(0, limit));
    }

    private static int clampBudget(int budget) {
        if (budget <= 0) {
            return DEFAULT_BUDGET;
        }
        return Math.min(budget, HARD_BUDGET);
    }
}
