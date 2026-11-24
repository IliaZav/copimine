package me.copimine.client;

/**
 * Emissive tone map for the authored V5.1 spell ribbons (26.3 client).
 *
 * <p>Section 9A.02 asks for high-value Rift energy: white-hot cores, saturated
 * mid-tones and deep near-black exteriors. Multiplying one hue by a brightness
 * factor cannot do that - every stroke just becomes a darker copy of the same
 * colour, which is exactly the flat look the spec forbids. This map instead
 * spans a real ramp: dim strokes sink toward a deep, hue-preserving shade while
 * the brightest authored vertices burn toward pure white, so a single ribbon
 * carries a readable hot core and a cool feathered edge.</p>
 *
 * <p>The map is pure and deterministic: identical arguments always return the
 * identical 24-bit colour, which is what lets the client rebuild the exact same
 * frame from the signed cast clock without any per-frame state.</p>
 */
public final class SpellVisualToneMap {

    /** Brightness at which a stroke stops being a mid-tone and starts to burn. */
    public static final float HOT_START = 0.66F;

    /** Fraction of the way to pure white reached at maximum authored brightness. */
    public static final float HOT_MIX = 0.62F;

    /** Fraction of its own hue a fully dim stroke keeps (the deep exterior). */
    public static final float COLD_SCALE = 0.42F;

    private SpellVisualToneMap() {
    }

    /**
     * Map an authored brightness in {@code [0, 1]} onto the hot-to-deep ramp.
     *
     * @param rgb        server-authored spell colour (alpha bits are ignored)
     * @param brightness authored per-stroke brightness; non-finite or out of range values are clamped
     * @return the 24-bit emissive colour for this vertex
     */
    public static int tone(int rgb, float brightness) {
        int base = rgb & 0xFFFFFF;
        float value = Float.isFinite(brightness)
                ? Math.max(0.0F, Math.min(1.0F, brightness))
                : 0.0F;
        if (value >= HOT_START) {
            double hot = (value - HOT_START) / (double) (1.0F - HOT_START);
            return blend(base, 0xFFFFFF, HOT_MIX * hot);
        }
        double glow = smooth(value / (double) HOT_START);
        return blend(scale(base, COLD_SCALE), base, glow);
    }

    /** True when this brightness reaches the white-hot band. */
    public static boolean isHot(float brightness) {
        return Float.isFinite(brightness) && brightness >= HOT_START;
    }

    /** Linear channel blend from {@code from} to {@code to}; {@code t} is clamped. */
    static int blend(int from, int to, double t) {
        double u = Math.max(0.0D, Math.min(1.0D, t));
        int red = channel(((from >> 16) & 0xFF) + (int) Math.round((((to >> 16) & 0xFF)
                - ((from >> 16) & 0xFF)) * u));
        int green = channel(((from >> 8) & 0xFF) + (int) Math.round((((to >> 8) & 0xFF)
                - ((from >> 8) & 0xFF)) * u));
        int blue = channel((from & 0xFF) + (int) Math.round(((to & 0xFF)
                - (from & 0xFF)) * u));
        return (red << 16) | (green << 8) | blue;
    }

    /** Scale a colour toward black without changing its hue ordering. */
    static int scale(int rgb, double factor) {
        double f = Math.max(0.0D, Math.min(1.0D, factor));
        int red = channel((int) Math.round(((rgb >> 16) & 0xFF) * f));
        int green = channel((int) Math.round(((rgb >> 8) & 0xFF) * f));
        int blue = channel((int) Math.round((rgb & 0xFF) * f));
        return (red << 16) | (green << 8) | blue;
    }

    private static int channel(int value) {
        return Math.max(0, Math.min(255, value));
    }

    /** Smoothstep ease, so the mid-tone ramp has no visible seam. */
    private static double smooth(double value) {
        double u = Math.max(0.0D, Math.min(1.0D, value));
        return u * u * (3.0D - 2.0D * u);
    }
}
