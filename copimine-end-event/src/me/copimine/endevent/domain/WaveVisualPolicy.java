package me.copimine.endevent.domain;

/**
 * Deterministic, bounded geometry for the wave-front telegraph.
 *
 * <p>The policy is deliberately independent of Bukkit so it can be tested
 * without starting a server.  The server renders the returned frame with
 * viewer-scoped particles and one temporary floor overlay.</p>
 */
public final class WaveVisualPolicy {
    public static final int OPENING_TICKS = 40;
    public static final double MAX_RADIUS_BLOCKS = 20.0D;
    public static final int MAX_RING_POINTS = 96;
    private static final double FLOOR_Y = 0.08D;

    private WaveVisualPolicy() {
    }

    public static boolean active(int elapsedTicks) {
        return elapsedTicks >= 0 && elapsedTicks < OPENING_TICKS;
    }

    public static Frame frame(int wave, int elapsedTicks, boolean completed) {
        int safeWave = Math.max(1, Math.min(6, wave));
        int safeTicks = Math.max(0, elapsedTicks);
        double progress = completed
                ? 1.0D
                : Math.min(1.0D, safeTicks / (double) OPENING_TICKS);
        double eased = 1.0D - Math.pow(1.0D - progress, 3.0D);
        double baseRadius = 1.25D;
        double outerRadius = baseRadius
                + (MAX_RADIUS_BLOCKS - baseRadius) * eased;
        double innerRadius = Math.max(0.5D, outerRadius * 0.68D);
        int outerPoints = Math.max(32, Math.min(MAX_RING_POINTS,
                32 + (int) Math.round(outerRadius * 3.0D)));
        int innerPoints = Math.max(16, Math.min(MAX_RING_POINTS, outerPoints / 2));
        double pulseHeight = FLOOR_Y + eased * 0.20D;
        double radialEnergy = Math.max(0.0D, Math.min(1.0D, eased));
        return new Frame(safeWave, safeTicks, outerRadius, innerRadius,
                outerPoints, innerPoints, FLOOR_Y, pulseHeight, radialEnergy,
                completed || safeTicks >= OPENING_TICKS);
    }

    public record Frame(int wave, int elapsedTicks, double outerRadius,
                        double innerRadius, int outerPoints, int innerPoints,
                        double floorY, double pulseHeight,
                        double radialEnergy, boolean completed) {
        public Frame {
            wave = Math.max(1, Math.min(6, wave));
            elapsedTicks = Math.max(0, elapsedTicks);
            outerRadius = finitePositive(outerRadius, 1.25D);
            innerRadius = Math.max(0.5D, finitePositive(innerRadius, 0.5D));
            outerPoints = Math.max(1, Math.min(MAX_RING_POINTS, outerPoints));
            innerPoints = Math.max(1, Math.min(MAX_RING_POINTS, innerPoints));
            floorY = Double.isFinite(floorY) ? floorY : FLOOR_Y;
            pulseHeight = Double.isFinite(pulseHeight) ? Math.max(0.0D, pulseHeight) : FLOOR_Y;
            radialEnergy = Double.isFinite(radialEnergy)
                    ? Math.max(0.0D, Math.min(1.0D, radialEnergy)) : 0.0D;
        }

        private static double finitePositive(double value, double fallback) {
            return Double.isFinite(value) ? Math.max(0.01D, value) : fallback;
        }
    }
}
