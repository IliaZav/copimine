package me.copimine.client;

/**
 * Accessibility policy for the authored world-space spell meshes.
 *
 * <p>Section 9A.04 and 9A.05 require reduced-motion to calm aggressive
 * animation without ever changing the hazard silhouette: the warning geometry,
 * attack timing and dodge cues must stay identical on every preset, while
 * pulsing, travelling waves and brightness throbs may be stilled for
 * motion-sensitive players. This class owns that decision so it can be tested
 * as pure arithmetic instead of inside a renderer.</p>
 */
public final class SpellVisualMotionPolicy {

    /** Steady full-alpha multiplier used when motion is reduced. */
    static final float STEADY_ALPHA_SCALE = 1.0F;

    private SpellVisualMotionPolicy() {
    }

    /**
     * Per-vertex alpha multiplier for the travelling brightness wave.
     *
     * <p>The animated form breathes between 0.80 and 1.00 so the mesh reads as
     * flowing energy. With reduced motion the multiplier is a constant, so the
     * ribbon silhouette and its footprint are unchanged but nothing throbs.</p>
     */
    public static float alphaScale(int vertexIndex, double phase, boolean reducedMotion) {
        if (reducedMotion) {
            return STEADY_ALPHA_SCALE;
        }
        double value = 0.80D + 0.20D * Math.sin(vertexIndex * 0.85D - phase);
        if (!Double.isFinite(value)) {
            return STEADY_ALPHA_SCALE;
        }
        return (float) Math.max(0.0D, Math.min(1.0D, value));
    }

    /**
     * Global animation phase for the flowing wave.
     *
     * <p>Reduced motion freezes the phase so every stroke keeps one fixed
     * brightness offset; the still frame is a legitimate authored pose, never a
     * blank or half-built mesh.</p>
     */
    public static double flowPhase(long elapsedMillis, boolean reducedMotion) {
        if (reducedMotion) {
            return 0.0D;
        }
        long safe = Math.max(0L, elapsedMillis);
        return Math.toRadians(safe * 0.42D);
    }

    /** The flow wave only travels when motion is not reduced. */
    public static boolean travels(boolean reducedMotion) {
        return !reducedMotion;
    }
}
