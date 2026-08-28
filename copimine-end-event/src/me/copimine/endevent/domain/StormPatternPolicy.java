package me.copimine.endevent.domain;

import java.util.LinkedHashSet;
import java.util.Set;

/** Pure Wave V pattern rotation and bounded candidate geometry. */
public final class StormPatternPolicy {
    private static final double MINIMUM_SAFE_RATIO = 0.35D;

    private StormPatternPolicy() {
    }

    public static Pattern nextPattern(Pattern previous, long seed) {
        Pattern[] values = Pattern.values();
        int start = Math.floorMod((int) (seed ^ (seed >>> 32)), values.length);
        for (int offset = 0; offset < values.length; offset++) {
            Pattern next = values[(start + offset) % values.length];
            if (next != previous) {
                return next;
            }
        }
        return values[0];
    }

    /**
     * Returns candidate floor cells for a visual/mechanical pattern.  The
     * adapter still intersects this set with its exact block snapshot and
     * protected cells before mutating anything.
     */
    public static Set<HazardPlanner.Point> patternCells(Bounds bounds, Pattern pattern,
                                                         int phase, long seed) {
        if (bounds == null || pattern == null) {
            return Set.of();
        }
        Set<HazardPlanner.Point> cells = new LinkedHashSet<>();
        int movingX = Math.floorMod((int) seed + phase * 3, bounds.width()) + bounds.minX();
        int movingZ = Math.floorMod((int) (seed >>> 16) + phase * 2, bounds.depth()) + bounds.minZ();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                HazardPlanner.Point point = new HazardPlanner.Point(x, z);
                // Bounds are expressed in world coordinates.  The Core is
                // therefore the centre of the selected rectangle, not
                // necessarily world coordinate 0,0.
                if (x == bounds.centerX() && z == bounds.centerZ()) {
                    continue;
                }
                int dx = x - bounds.centerX();
                int dz = z - bounds.centerZ();
                boolean hazard = switch (pattern) {
                    case RINGS -> Math.floorMod(Math.max(Math.abs(dx), Math.abs(dz)) + phase, 5) <= 1;
                    case CROSS -> (Math.abs(dx) <= 1 || Math.abs(dz) <= 1)
                            && Math.floorMod(dx * dx + dz * dz + phase, 3) != 0;
                    case SECTORS -> ((dx >= 0) == (dz >= 0))
                            ^ Math.floorMod(phase, 2) == 1;
                    case MOVING_SAFE_ISLAND -> Math.abs(x - movingX) + Math.abs(z - movingZ) > 4;
                    case FRACTURE_LANES -> Math.floorMod(x + z + phase, 5) == 0;
                };
                if (hazard) {
                    cells.add(point);
                }
            }
        }
        return Set.copyOf(cells);
    }

    public static double minimumSafeRatio() {
        return MINIMUM_SAFE_RATIO;
    }

    public enum Pattern {
        RINGS,
        CROSS,
        SECTORS,
        MOVING_SAFE_ISLAND,
        FRACTURE_LANES
    }

    public record Bounds(int minX, int maxX, int minZ, int maxZ) {
        public Bounds {
            if (minX > maxX || minZ > maxZ) {
                throw new IllegalArgumentException("storm bounds must be ordered");
            }
        }

        public int width() {
            return maxX - minX + 1;
        }

        public int depth() {
            return maxZ - minZ + 1;
        }

        public int centerX() {
            return Math.floorDiv(minX + maxX, 2);
        }

        public int centerZ() {
            return Math.floorDiv(minZ + maxZ, 2);
        }

        public boolean contains(HazardPlanner.Point point) {
            return point != null && point.x() >= minX && point.x() <= maxX
                    && point.z() >= minZ && point.z() <= maxZ;
        }
    }
}
