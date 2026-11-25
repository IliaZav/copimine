package me.copimine.client;

/**
 * Authored cast envelope for the V5.1 world-space spell meshes (26.3 client).
 *
 * <p>Section 9A.03(C) requires every effect to carry 4-8 *semantic* animation
 * keys (dormant, breath/anticipation, energized, release, peak deformation,
 * fragment/dissipation, completed) refined with authored easing, instead of one
 * endless sine loop. The mesh builders own the shapes; this class owns the
 * clock and the heat that drive them:</p>
 *
 * <ul>
 *   <li>{@link #tempoMillis} re-times the authored cast clock. A heavy
 *       Awakening crush winds up slowly and then snaps through the release; a
 *       rock impact runs a short impulse and then damps; jaws open fast and
 *       settle slowly. The map is strictly monotone and piecewise smoothstep,
 *       so a later instant can never animate backwards, and past the authored
 *       window it continues 1:1 (a long telegraph never freezes).</li>
 *   <li>{@link #heat} is the authored ignition curve for the tone map: dim but
 *       readable on the first frame, hottest at the release, cooling through
 *       the dissipation. It only scales material brightness, never the stroke
 *       points or half-widths, so the announced hazard silhouette and the
 *       render footprint stay exactly the authored ones.</li>
 *   <li>{@link #beat} exposes the current semantic key so the contract can be
 *       asserted in plain arithmetic without a running client.</li>
 * </ul>
 *
 * <p>Everything here is pure arithmetic on the cast clock: deterministic,
 * frame-rate independent and identical on every quality preset.</p>
 */
public final class SpellVisualEnvelope {

    /** The authored cast window the seven keys span. */
    public static final long CAST_WINDOW_MILLIS = 2_000L;

    /** Seven semantic keys: the 4-8 range required by Section 9A.03(C). */
    public static final int KEY_COUNT = 7;

    /** Semantic keys in cast order. */
    public enum Beat {
        DORMANT,
        ANTICIPATION,
        ENERGIZED,
        RELEASE,
        PEAK,
        DISSIPATE,
        COMPLETED
    }

    /** Key times in the authored window, shared so every family reads the same beats. */
    private static final long[] REAL_MILLIS = {0L, 240L, 540L, 860L, 1_180L, 1_580L, 2_000L};

    /** Identity re-time, used by families that already own an internal phase arc. */
    private static final long[] IDENTITY_TEMPO =
            {0L, 333L, 667L, 1_000L, 1_333L, 1_667L, 2_000L};

    // Per-family authored tempo (cast clock re-time) and heat (material ignition).

    private static final long[] CROWN_TEMPO = {0L, 150L, 520L, 900L, 1_230L, 1_680L, 2_000L};
    private static final double[] CROWN_HEAT = {0.45D, 0.62D, 0.85D, 1.00D, 0.92D, 0.68D, 0.50D};

    private static final long[] CIRCLE_TEMPO = {0L, 340L, 780L, 1_050L, 1_300L, 1_700L, 2_000L};
    private static final double[] CIRCLE_HEAT = {0.50D, 0.66D, 0.95D, 1.00D, 0.90D, 0.66D, 0.48D};

    private static final long[] FANG_TEMPO = {0L, 480L, 900L, 1_180L, 1_400L, 1_720L, 2_000L};
    private static final double[] FANG_HEAT = {0.45D, 0.70D, 0.95D, 1.00D, 0.85D, 0.62D, 0.45D};

    private static final long[] SWEEP_TEMPO = {0L, 520L, 880L, 1_080L, 1_250L, 1_660L, 2_000L};
    private static final double[] SWEEP_HEAT = {0.45D, 0.60D, 0.90D, 1.00D, 0.88D, 0.62D, 0.46D};

    private static final long[] SPHERE_TEMPO = {0L, 120L, 460L, 900L, 1_320L, 1_750L, 2_000L};
    private static final double[] SPHERE_HEAT = {0.40D, 0.55D, 0.72D, 0.88D, 1.00D, 0.70D, 0.48D};

    private static final long[] SCULK_TEMPO = {0L, 220L, 520L, 880L, 1_210L, 1_620L, 2_000L};
    private static final double[] SCULK_HEAT = {0.42D, 0.58D, 0.78D, 0.92D, 1.00D, 0.72D, 0.52D};

    private static final double[] GRAVITY_HEAT = {0.75D, 0.85D, 0.95D, 1.00D, 0.96D, 0.88D, 0.78D};

    private static final long[] FAN_TEMPO = {0L, 460L, 850L, 1_100L, 1_320L, 1_700L, 2_000L};
    private static final double[] FAN_HEAT = {0.48D, 0.72D, 0.95D, 1.00D, 0.86D, 0.62D, 0.46D};

    private static final long[] HEAVY_TEMPO = {0L, 500L, 880L, 1_100L, 1_280L, 1_680L, 2_000L};
    private static final double[] HEAVY_HEAT = {0.45D, 0.62D, 0.88D, 1.00D, 0.90D, 0.64D, 0.46D};

    private static final long[] SEPARATION_TEMPO = {0L, 140L, 480L, 920L, 1_340L, 1_760L, 2_000L};
    private static final double[] SEPARATION_HEAT = {0.40D, 0.52D, 0.68D, 0.85D, 1.00D, 0.68D, 0.46D};

    private static final double[] BEAM_HEAT = {0.35D, 0.50D, 0.72D, 1.00D, 0.95D, 0.70D, 0.42D};

    private static final double[] GEYSER_HEAT = {0.38D, 0.50D, 0.66D, 0.86D, 1.00D, 0.98D, 0.96D};

    private SpellVisualEnvelope() {
    }

    /** Authored cast-clock re-time for one family, in milliseconds. */
    static long[] tempoOf(SpellVisualGeometry.Spell spell) {
        if (spell == null) {
            return IDENTITY_TEMPO;
        }
        return switch (spell) {
            case CROWN -> CROWN_TEMPO;
            case CIRCLE -> CIRCLE_TEMPO;
            case FANG_LINE -> FANG_TEMPO;
            case WARDEN_SWEEP -> SWEEP_TEMPO;
            case ECHO_SPHERE -> SPHERE_TEMPO;
            case SCULK_TRACE -> SCULK_TEMPO;
            case GRAVITY -> IDENTITY_TEMPO;
            case GUARDIAN_FAN -> FAN_TEMPO;
            case GUARDIAN_HEAVY -> HEAVY_TEMPO;
            case SEPARATION -> SEPARATION_TEMPO;
            case BEAM -> IDENTITY_TEMPO;
            case GEYSER -> IDENTITY_TEMPO;
        };
    }

    /** Authored material ignition curve for one family, one value per key. */
    static double[] heatOf(SpellVisualGeometry.Spell spell) {
        if (spell == null) {
            return GRAVITY_HEAT;
        }
        return switch (spell) {
            case CROWN -> CROWN_HEAT;
            case CIRCLE -> CIRCLE_HEAT;
            case FANG_LINE -> FANG_HEAT;
            case WARDEN_SWEEP -> SWEEP_HEAT;
            case ECHO_SPHERE -> SPHERE_HEAT;
            case SCULK_TRACE -> SCULK_HEAT;
            case GRAVITY -> GRAVITY_HEAT;
            case GUARDIAN_FAN -> FAN_HEAT;
            case GUARDIAN_HEAVY -> HEAVY_HEAT;
            case SEPARATION -> SEPARATION_HEAT;
            case BEAM -> BEAM_HEAT;
            case GEYSER -> GEYSER_HEAT;
        };
    }

    /** Key time in the authored window for one semantic beat. */
    public static long keyMillis(int index) {
        return REAL_MILLIS[Math.max(0, Math.min(KEY_COUNT - 1, index))];
    }

    /**
     * Re-time the authored cast clock.
     *
     * <p>Piecewise smoothstep between the authored keys, strictly monotone, and
     * 1:1 after the authored window. Never returns a value below the previous
     * instant, so rotation, rise and collapse can never jitter backwards.</p>
     */
    public static long tempoMillis(SpellVisualGeometry.Spell spell, long elapsedMillis) {
        long elapsed = Math.max(0L, Math.min(120_000L, elapsedMillis));
        if (elapsed >= CAST_WINDOW_MILLIS) {
            return elapsed;
        }
        long[] tempo = tempoOf(spell);
        if (tempo == IDENTITY_TEMPO) {
            return elapsed;
        }
        int index = segment(elapsed);
        double span = REAL_MILLIS[index + 1] - REAL_MILLIS[index];
        double u = span <= 0.0D ? 1.0D : (elapsed - REAL_MILLIS[index]) / span;
        double eased = smoothstep(u);
        long low = tempo[index];
        long high = tempo[index + 1];
        long value = low + Math.round((high - low) * eased);
        return Math.max(low, Math.min(high, value));
    }

    /**
     * Authored ignition heat in {@code [0,1]} for the tone map.
     *
     * <p>Only brightness uses this value. Stroke points and half-widths never
     * depend on it, so the danger silhouette is identical at every key.</p>
     */
    public static double heat(SpellVisualGeometry.Spell spell, long elapsedMillis) {
        long elapsed = Math.max(0L, Math.min(120_000L, elapsedMillis));
        double[] heat = heatOf(spell);
        if (elapsed >= CAST_WINDOW_MILLIS) {
            return heat[KEY_COUNT - 1];
        }
        int index = segment(elapsed);
        double span = REAL_MILLIS[index + 1] - REAL_MILLIS[index];
        double u = span <= 0.0D ? 1.0D : (elapsed - REAL_MILLIS[index]) / span;
        double eased = smoothstep(u);
        double value = heat[index] + (heat[index + 1] - heat[index]) * eased;
        if (!Double.isFinite(value)) {
            return 1.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    /** Which semantic key the cast clock is currently inside. */
    public static Beat beat(SpellVisualGeometry.Spell spell, long elapsedMillis) {
        long elapsed = Math.max(0L, elapsedMillis);
        for (int index = KEY_COUNT - 1; index > 0; index--) {
            if (elapsed >= REAL_MILLIS[index]) {
                return Beat.values()[index];
            }
        }
        return Beat.DORMANT;
    }

    /** Authored key index for a normalized progress in {@code [0,1]}. */
    private static int segment(long elapsedMillis) {
        for (int index = 0; index < KEY_COUNT - 1; index++) {
            if (elapsedMillis < REAL_MILLIS[index + 1]) {
                return index;
            }
        }
        return KEY_COUNT - 2;
    }

    /** Cubic smoothstep: monotone on {@code [0,1]} with zero end slopes. */
    private static double smoothstep(double u) {
        double clamped = Math.max(0.0D, Math.min(1.0D, u));
        return clamped * clamped * (3.0D - 2.0D * clamped);
    }
}
