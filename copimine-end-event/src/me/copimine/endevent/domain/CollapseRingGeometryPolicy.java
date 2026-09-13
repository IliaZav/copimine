package me.copimine.endevent.domain;

/**
 * Pure geometry for the three bounded Wave 6 combat lanes.  The Bukkit
 * adapter uses the same values for the ring displays, target pressure and
 * containment, so the visual ring cannot silently disagree with the AI.
 */
public final class CollapseRingGeometryPolicy {
    public static final int RING_COUNT = 3;
    public static final int MAX_VISUAL_POINTS = 256;
    public static final double RING_BAND_HALF_WIDTH = 2.25D;

    private static final double[] RING_RADII = {6.0D, 11.0D, 16.0D};
    private static final int[] VISUAL_POINTS = {64, 80, 96};

    private CollapseRingGeometryPolicy() {
    }

    public static double ringRadius(int ring) {
        return validRing(ring) ? RING_RADII[ring] : 0.0D;
    }

    public static int visualPointCount(int ring) {
        return validRing(ring) ? VISUAL_POINTS[ring] : 0;
    }

    public static boolean inRingBand(int ring, double xOffset, double zOffset) {
        double radius = ringRadius(ring);
        if (radius <= 0.0D || !Double.isFinite(xOffset) || !Double.isFinite(zOffset)) {
            return false;
        }
        return Math.abs(Math.hypot(xOffset, zOffset) - radius) <= RING_BAND_HALF_WIDTH;
    }

    /** Return the lane radius nearest to a finite requested radius. */
    public static double clampToRingRadius(int ring, double requestedRadius) {
        double radius = ringRadius(ring);
        if (radius <= 0.0D) {
            return 0.0D;
        }
        if (!Double.isFinite(requestedRadius)) {
            return radius;
        }
        return radius;
    }

    private static boolean validRing(int ring) {
        return ring >= 0 && ring < RING_COUNT;
    }
}
