package me.copimine.dungeon.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Axis-aligned integer block cuboid with normalized min/max bounds.
 *
 * <p>The editor's two-point selection is exactly this shape (spec OV4.3): two
 * corners are normalized into inclusive integer block bounds, so a 1x1x1
 * selection encloses exactly one block. No Bukkit types are used here so the
 * whole editor geometry stays unit-testable without a server.</p>
 */
public final class Cuboid {

    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    public Cuboid(int x1, int y1, int z1, int x2, int y2, int z2) {
        this.minX = Math.min(x1, x2);
        this.maxX = Math.max(x1, x2);
        this.minY = Math.min(y1, y2);
        this.maxY = Math.max(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxZ = Math.max(z1, z2);
    }

    public static Cuboid of(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Cuboid(x1, y1, z1, x2, y2, z2);
    }

    /** Builds a cuboid from two int[3] corners (as produced by the wand/CLI). */
    public static Cuboid fromCorners(int[] a, int[] b) {
        Objects.requireNonNull(a, "corner a");
        Objects.requireNonNull(b, "corner b");
        if (a.length < 3 || b.length < 3) {
            throw new IllegalArgumentException("Каждый угол должен иметь 3 координаты.");
        }
        return new Cuboid(a[0], a[1], a[2], b[0], b[1], b[2]);
    }

    public int minX() {
        return minX;
    }

    public int minY() {
        return minY;
    }

    public int minZ() {
        return minZ;
    }

    public int maxX() {
        return maxX;
    }

    public int maxY() {
        return maxY;
    }

    public int maxZ() {
        return maxZ;
    }

    /** Inclusive block count along X. */
    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeY() {
        return maxY - minY + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    public int volume() {
        return sizeX() * sizeY() * sizeZ();
    }

    /** True for an exactly one-block selection (1x1x1). */
    public boolean enclosesExactlyOneBlock() {
        return sizeX() == 1 && sizeY() == 1 && sizeZ() == 1;
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    public boolean contains(Cuboid other) {
        Objects.requireNonNull(other, "other");
        return other.minX >= minX && other.maxX <= maxX
                && other.minY >= minY && other.maxY <= maxY
                && other.minZ >= minZ && other.maxZ <= maxZ;
    }

    public boolean intersects(Cuboid other) {
        Objects.requireNonNull(other, "other");
        return minX <= other.maxX && maxX >= other.minX
                && minY <= other.maxY && maxY >= other.minY
                && minZ <= other.maxZ && maxZ >= other.minZ;
    }

    /** Integer floor center used for labels and anchors. */
    public int[] center() {
        return new int[]{(minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2};
    }

    /**
     * The eight corners in a stable order: bottom ring (y=minY) then top ring
     * (y=maxY), each counter-clockwise starting at (minX,minZ).
     */
    public List<int[]> corners() {
        List<int[]> result = new ArrayList<>(8);
        int[] xs = {minX, maxX};
        int[] zs = {minZ, maxZ};
        for (int y : new int[]{minY, maxY}) {
            result.add(new int[]{minX, y, minZ});
            result.add(new int[]{maxX, y, minZ});
            result.add(new int[]{maxX, y, maxZ});
            result.add(new int[]{minX, y, maxZ});
        }
        return result;
    }

    /**
     * The twelve cuboid edges as {start, end} corner pairs. Bottom ring,
     * top ring and the four vertical struts.
     */
    public List<int[][]> edgeSegments() {
        List<int[]> c = corners();
        List<int[][]> edges = new ArrayList<>(12);
        // bottom ring 0..3
        edges.add(new int[][]{c.get(0), c.get(1)});
        edges.add(new int[][]{c.get(1), c.get(2)});
        edges.add(new int[][]{c.get(2), c.get(3)});
        edges.add(new int[][]{c.get(3), c.get(0)});
        // top ring 4..7
        edges.add(new int[][]{c.get(4), c.get(5)});
        edges.add(new int[][]{c.get(5), c.get(6)});
        edges.add(new int[][]{c.get(6), c.get(7)});
        edges.add(new int[][]{c.get(7), c.get(4)});
        // vertical struts
        edges.add(new int[][]{c.get(0), c.get(4)});
        edges.add(new int[][]{c.get(1), c.get(5)});
        edges.add(new int[][]{c.get(2), c.get(6)});
        edges.add(new int[][]{c.get(3), c.get(7)});
        return edges;
    }

    /** Number of edges traced by the overlay: always twelve. */
    public int edgeCount() {
        return 12;
    }

    /**
     * Samples every edge with {@code stepsPerEdge + 1} inclusive points. The
     * total is deterministic: 12 * (stepsPerEdge + 1).
     */
    public List<int[]> edgeSamplePoints(int stepsPerEdge) {
        int steps = Math.max(0, stepsPerEdge);
        List<int[]> points = new ArrayList<>(12 * (steps + 1));
        for (int[][] edge : edgeSegments()) {
            int[] a = edge[0];
            int[] b = edge[1];
            for (int i = 0; i <= steps; i++) {
                points.add(lerp(a, b, i, steps));
            }
        }
        return points;
    }

    /**
     * Samples the four vertical corner pillars with {@code steps + 1} points
     * each (bottom to top).
     */
    public List<int[]> verticalCornerPillars(int steps) {
        int s = Math.max(0, steps);
        int[][] xz = {{minX, minZ}, {maxX, minZ}, {maxX, maxZ}, {minX, maxZ}};
        List<int[]> points = new ArrayList<>(4 * (s + 1));
        for (int[] column : xz) {
            for (int i = 0; i <= s; i++) {
                int y = (s == 0) ? minY : minY + (int) Math.round((double) (maxY - minY) * i / s);
                points.add(new int[]{column[0], y, column[1]});
            }
        }
        return points;
    }

    private static int[] lerp(int[] a, int[] b, int i, int steps) {
        if (steps == 0) {
            return new int[]{a[0], a[1], a[2]};
        }
        return new int[]{
                a[0] + (int) Math.round((double) (b[0] - a[0]) * i / steps),
                a[1] + (int) Math.round((double) (b[1] - a[1]) * i / steps),
                a[2] + (int) Math.round((double) (b[2] - a[2]) * i / steps),
        };
    }

    public String describe() {
        return "[" + minX + "," + minY + "," + minZ + " -> " + maxX + "," + maxY + "," + maxZ + "]"
                + " (" + sizeX() + "x" + sizeY() + "x" + sizeZ() + ")";
    }

    @Override
    public String toString() {
        return describe();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Cuboid c)) {
            return false;
        }
        return minX == c.minX && minY == c.minY && minZ == c.minZ
                && maxX == c.maxX && maxY == c.maxY && maxZ == c.maxZ;
    }

    @Override
    public int hashCode() {
        return Objects.hash(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
