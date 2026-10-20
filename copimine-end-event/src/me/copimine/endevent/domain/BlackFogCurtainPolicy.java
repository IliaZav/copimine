package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;

/** Bounded particle geometry for the visible Wave 5 arena fog curtain. */
public final class BlackFogCurtainPolicy {
    public static final int POINTS_PER_RING = 96;
    public static final int VERTICAL_LAYERS = 8;
    public static final int MAX_PARTICLE_POINTS = POINTS_PER_RING * VERTICAL_LAYERS;
    private static final double MAX_RADIUS_BLOCKS = 64.0D;
    private static final double MAX_HEIGHT_BLOCKS = 16.0D;

    private BlackFogCurtainPolicy() { }

    /**
     * Return evenly spaced points around a vertical arena perimeter. Coordinates
     * are relative to the center. Invalid or out-of-bounds dimensions return no
     * points, so a bad caller cannot create an unbounded particle wall.
     */
    public static List<Point> perimeter(double radiusBlocks, double heightBlocks) {
        if (!Double.isFinite(radiusBlocks) || !Double.isFinite(heightBlocks)
                || radiusBlocks <= 0.0D || radiusBlocks > MAX_RADIUS_BLOCKS
                || heightBlocks <= 0.0D || heightBlocks > MAX_HEIGHT_BLOCKS) {
            return List.of();
        }

        List<Point> points = new ArrayList<>(MAX_PARTICLE_POINTS);
        for (int layer = 0; layer < VERTICAL_LAYERS; layer++) {
            double y = heightBlocks * (layer + 0.5D) / VERTICAL_LAYERS;
            for (int sample = 0; sample < POINTS_PER_RING; sample++) {
                double angle = Math.PI * 2.0D * sample / POINTS_PER_RING;
                points.add(new Point(Math.cos(angle) * radiusBlocks, y,
                        Math.sin(angle) * radiusBlocks));
            }
        }
        return List.copyOf(points);
    }

    public record Point(double x, double y, double z) { }
}
