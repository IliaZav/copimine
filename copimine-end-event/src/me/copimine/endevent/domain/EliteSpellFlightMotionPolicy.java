package me.copimine.endevent.domain;

/**
 * Flight animation for the elite spell projectiles.
 *
 * A spell that flies as a frozen mesh reads as a sliding prop, not as a spell:
 * the previous revision only tumbled the spider's cobweb block and left the
 * three authored spell meshes perfectly static. Each spell therefore owns a
 * distinct motion signature, chosen to match what the mesh actually is:
 *
 * <ul>
 *   <li>{@code RIFT_BOLT} - a spear. It spins fast around its own long axis so
 *       the stabiliser fins flicker, and it pulses in scale so the core looks
 *       like it is drawing power as it travels.</li>
 *   <li>{@code VOID_BURST} - a swelling orb. It barely rotates (an orb has no
 *       readable spin) but breathes noticeably: it grows and contracts, so the
 *       cast looks like it is about to burst.</li>
 *   <li>{@code DRAGON_BREATH} - a snout venting flame. It wobbles on two axes,
 *       like a jet of fire guttering, and pulses faster than the orb.</li>
 * </ul>
 *
 * The policy is a pure function of the two things the server actually has at
 * render time: the spell identity and how long the spell has been airborne.
 * That keeps it cheap (no per-tick allocation beyond the returned value) and
 * lets the behaviour be tested without a running server.
 */
public final class EliteSpellFlightMotionPolicy {

    /** Milliseconds per full 360 degree revolution, or 0 for no spin. */
    private static final double RIFT_BOLT_SPIN_PERIOD_MS = 240.0D;
    private static final double VOID_BURST_SPIN_PERIOD_MS = 1400.0D;
    private static final double DRAGON_BREATH_SPIN_PERIOD_MS = 700.0D;

    /** Milliseconds per pulse cycle, or 0 for no scale pulse. */
    private static final double RIFT_BOLT_PULSE_PERIOD_MS = 260.0D;
    private static final double VOID_BURST_PULSE_PERIOD_MS = 420.0D;
    private static final double DRAGON_BREATH_PULSE_PERIOD_MS = 190.0D;

    /** Fractional growth applied at the peak of the pulse. */
    private static final double RIFT_BOLT_PULSE_DEPTH = 0.10D;
    private static final double VOID_BURST_PULSE_DEPTH = 0.24D;
    private static final double DRAGON_BREATH_PULSE_DEPTH = 0.16D;

    /** Wobble amplitude in radians for the two wobbling spells. */
    private static final float DRAGON_BREATH_WOBBLE_RADIANS = 0.22F;
    private static final float VOID_BURST_WOBBLE_RADIANS = 0.05F;

    private static final double TWO_PI = Math.PI * 2.0D;

    private EliteSpellFlightMotionPolicy() {
    }

    /**
     * Motion state for one render tick.
     *
     * @param spinRadians   rotation about the spell's own long axis
     * @param wobblePitch   secondary rotation on the pitch axis, in radians
     * @param wobbleYaw     secondary rotation on the yaw axis, in radians
     * @param scaleMultiplier multiplier applied to the base display scale
     */
    public record Motion(double spinRadians, float wobblePitch, float wobbleYaw,
                         float scaleMultiplier) {
    }

    public static Motion motionFor(String spell, long airborneMillis) {
        long elapsed = Math.max(0L, airborneMillis);
        String identity = normalise(spell);
        return switch (identity) {
            case "RIFT_BOLT" -> new Motion(
                    spin(elapsed, RIFT_BOLT_SPIN_PERIOD_MS),
                    0.0F, 0.0F,
                    pulse(elapsed, RIFT_BOLT_PULSE_PERIOD_MS, RIFT_BOLT_PULSE_DEPTH));
            case "VOID_BURST" -> new Motion(
                    spin(elapsed, VOID_BURST_SPIN_PERIOD_MS),
                    wobble(elapsed, VOID_BURST_WOBBLE_RADIANS, 900.0D),
                    wobble(elapsed, VOID_BURST_WOBBLE_RADIANS, 900.0D),
                    pulse(elapsed, VOID_BURST_PULSE_PERIOD_MS, VOID_BURST_PULSE_DEPTH));
            case "DRAGON_BREATH" -> new Motion(
                    spin(elapsed, DRAGON_BREATH_SPIN_PERIOD_MS),
                    wobble(elapsed, DRAGON_BREATH_WOBBLE_RADIANS, 260.0D),
                    wobble(elapsed, DRAGON_BREATH_WOBBLE_RADIANS, 190.0D),
                    pulse(elapsed, DRAGON_BREATH_PULSE_PERIOD_MS, DRAGON_BREATH_PULSE_DEPTH));
            // An unknown spell must still render, it simply does not animate.
            default -> new Motion(0.0D, 0.0F, 0.0F, 1.0F);
        };
    }

    /** True when this spell identity has a motion signature of its own. */
    public static boolean animates(String spell) {
        String identity = normalise(spell);
        return "RIFT_BOLT".equals(identity)
                || "VOID_BURST".equals(identity)
                || "DRAGON_BREATH".equals(identity);
    }

    private static String normalise(String spell) {
        return spell == null ? "" : spell.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static double spin(long elapsed, double period) {
        if (period <= 0.0D) return 0.0D;
        return (elapsed % (long) period) / period * TWO_PI;
    }

    private static float wobble(long elapsed, float amplitude, double period) {
        if (period <= 0.0D) return 0.0F;
        return (float) (Math.sin(elapsed / period * TWO_PI) * amplitude);
    }

    private static float pulse(long elapsed, double period, double depth) {
        if (period <= 0.0D) return 1.0F;
        double wave = 0.5D - 0.5D * Math.cos(elapsed / period * TWO_PI);
        return (float) (1.0D + wave * depth);
    }
}
