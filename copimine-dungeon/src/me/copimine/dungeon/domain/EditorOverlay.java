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

    public record OutlinePoint(double x, double y, double z, boolean guide) { }

    private record VisibleLine(double x, double y, double z, double dx, double dy, double dz,
                               double start, double end, boolean guide) {
        double length() { return (end - start) * Math.sqrt(dx * dx + dy * dy + dz * dz); }
    }

    /** Exact block-face outlines, clipped before allocating the per-viewer point budget. */
    public static List<OutlinePoint> visibleOutline(Cuboid box, double eyeX, double eyeY, double eyeZ,
                                                    double range, int budget, boolean guides) {
        if (budget < 1 || range <= 0D) return List.of();
        double[] x = {box.minX() - .035D, box.maxX() + 1.035D};
        double[] y = {box.minY() - .035D, box.maxY() + 1.035D};
        double[] z = {box.minZ() - .035D, box.maxZ() + 1.035D};
        List<VisibleLine> lines = new ArrayList<>(16);
        for (int a = 0; a < 2; a++) {
            for (int b = 0; b < 2; b++) {
                clip(lines, x[0], y[a], z[b], x[1], y[a], z[b], eyeX, eyeY, eyeZ, range, false);
                clip(lines, x[a], y[0], z[b], x[a], y[1], z[b], eyeX, eyeY, eyeZ, range, false);
                clip(lines, x[a], y[b], z[0], x[a], y[b], z[1], eyeX, eyeY, eyeZ, range, false);
            }
        }
        if (guides) {
            // The outline remains on the selected block faces. Add a second line
            // inside the volume, above the floor and past any one-block shell.
            double level = Math.clamp(eyeY - .75D, Math.min(y[1], y[0] + 1.1D), y[1]);
            double insetX = Math.min(1.1D, (x[1] - x[0]) * .25D);
            double insetZ = Math.min(1.1D, (z[1] - z[0]) * .25D);
            double left = x[0] + insetX, right = x[1] - insetX;
            double front = z[0] + insetZ, back = z[1] - insetZ;
            for (double side : new double[]{front, back}) {
                clip(lines, left, level, side, right, level, side, eyeX, eyeY, eyeZ, range, true);
            }
            for (double side : new double[]{left, right}) {
                clip(lines, side, level, front, side, level, back, eyeX, eyeY, eyeZ, range, true);
            }
            if (eyeX >= left && eyeX <= right && eyeZ >= front && eyeZ <= back) {
                clip(lines, left, level, eyeZ, right, level, eyeZ, eyeX, eyeY, eyeZ, range, true);
                clip(lines, eyeX, level, front, eyeX, level, back, eyeX, eyeY, eyeZ, range, true);
            }
        }
        if (lines.isEmpty()) return List.of();
        int limit = Math.min(HARD_BUDGET, budget);
        double length = lines.stream().mapToDouble(VisibleLine::length).sum();
        double spacing = Math.max(.35D, length / Math.max(1, limit - 2 * lines.size()));
        List<OutlinePoint> result = new ArrayList<>(limit);
        for (int lineIndex = 0; lineIndex < lines.size() && result.size() < limit; lineIndex++) {
            var line = lines.get(lineIndex);
            int available = Math.max(1, limit - result.size()
                    - Math.min(limit - result.size() - 1, 2 * (lines.size() - lineIndex - 1)));
            int count = Math.min(available, Math.max(2, 1 + (int) Math.ceil(line.length() / spacing)));
            for (int i = 0; i < count; i++) {
                double fraction = count == 1 ? .5D : (double) i / (count - 1);
                double t = line.start() + (line.end() - line.start()) * fraction;
                result.add(new OutlinePoint(line.x() + line.dx() * t, line.y() + line.dy() * t,
                        line.z() + line.dz() * t, line.guide()));
            }
        }
        return result;
    }

    private static void clip(List<VisibleLine> lines, double x, double y, double z,
                             double endX, double endY, double endZ,
                             double eyeX, double eyeY, double eyeZ, double range, boolean guide) {
        double dx = endX - x, dy = endY - y, dz = endZ - z;
        double a = dx * dx + dy * dy + dz * dz;
        double px = x - eyeX, py = y - eyeY, pz = z - eyeZ;
        double projection = px * dx + py * dy + pz * dz;
        double discriminant = projection * projection - a * (px * px + py * py + pz * pz - range * range);
        if (a <= 0 || discriminant < 0) return;
        double root = Math.sqrt(discriminant);
        double start = Math.max(0, (-projection - root) / a);
        double end = Math.min(1, (-projection + root) / a);
        if (start <= end) lines.add(new VisibleLine(x, y, z, dx, dy, dz, start, end, guide));
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
