package me.copimine.client;

/**
 * Deterministic materialisation clock for an echo copy.
 *
 * <p>The copy must arrive with an animation and its eyes must light up
 * gradually, so the two ramps live here as pure arithmetic rather than inside
 * the renderer. The renderer only asks "how far in is this copy?" and applies
 * the answer; nothing here touches Minecraft, which is what lets the gate
 * exercise the timing without a client jar.</p>
 *
 * <p>Both clocks are measured from the first drawn frame ({@link #mark}), not
 * from when the server bound the actor: a copy that is bound while off-screen
 * should still be seen to arrive.</p>
 */
public final class EchoAppearanceClock {
    /** How long the body takes to grow into place. */
    public static final long SPAWN_MILLIS = 700L;
    /** The eyes stay dark until the body has mostly taken shape. */
    public static final long EYE_DELAY_MILLIS = 450L;
    /** They then light up over this long, so a copy arrives before it stares. */
    public static final long EYE_FADE_MILLIS = 900L;
    /** Scale of a fully formed body. */
    public static final float FULL_SCALE = 1.0F;
    /** Scale of a body at the instant it starts appearing. */
    public static final float BORN_SCALE = 0.86F;

    private long appearedAt = -1L;

    /** Record the first drawn frame; later calls keep the original instant. */
    public void mark(long nowMillis) {
        if (appearedAt < 0L) appearedAt = nowMillis;
    }

    /** True once a frame has been drawn for this view. */
    public boolean appeared() {
        return appearedAt >= 0L;
    }

    /** Forget the arrival, so a recycled actor materialises again. */
    public void reset() {
        appearedAt = -1L;
    }

    /**
     * 0 on the first drawn frame, 1 once the body is fully formed. A view that
     * has never been drawn reports 1: there is nothing to animate yet, and the
     * first drawn frame marks it.
     */
    public float spawn(long nowMillis) {
        if (appearedAt < 0L) return 1.0F;
        long elapsed = nowMillis - appearedAt;
        if (elapsed <= 0L) return 0.0F;
        if (elapsed >= SPAWN_MILLIS) return 1.0F;
        return (float) elapsed / (float) SPAWN_MILLIS;
    }

    /** 0 while the eyes are dark, rising to 1 once the glow has fully arrived. */
    public float eyeGlow(long nowMillis) {
        if (appearedAt < 0L) return 1.0F;
        long elapsed = nowMillis - appearedAt - EYE_DELAY_MILLIS;
        if (elapsed <= 0L) return 0.0F;
        if (elapsed >= EYE_FADE_MILLIS) return 1.0F;
        return (float) elapsed / (float) EYE_FADE_MILLIS;
    }

    /**
     * Eased scale for the body pass: smoothstep from {@link #BORN_SCALE} to
     * {@link #FULL_SCALE}, so the copy grows rather than pops in.
     */
    public static float materialiseScale(float spawn) {
        float clamped = Math.max(0.0F, Math.min(1.0F, spawn));
        float eased = clamped * clamped * (3.0F - 2.0F * clamped);
        return BORN_SCALE + (FULL_SCALE - BORN_SCALE) * eased;
    }
}
