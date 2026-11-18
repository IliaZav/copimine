package me.copimine.client;

/**
 * Which presentation passes an echo frame needs.
 *
 * <p>A copy is drained from its first frame: the darkened body pass is
 * unconditional. Only the emissive violet eye pass waits for the glow ramp,
 * because a copy should arrive looking like a player before it stares. Tying
 * the body tint to the eye ramp was the bug this policy rules out.</p>
 *
 * <p>The class imports no Minecraft types, so the decision is exercised as
 * arithmetic by the test suite. The renderer still owns the actual submission
 * order (eyes, then body); this policy only says which passes exist.</p>
 */
public final class EchoEyePassPlan {
    /** Emissive violet eye pass. */
    public static final int EYES = 1;
    /** Darkened body pass, present on every frame. */
    public static final int BODY = 1 << 1;
    /** Glow above which the eyes are worth drawing. */
    public static final float GLOW_THRESHOLD = 0.02F;

    private EchoEyePassPlan() {
    }

    /** The passes for this eye glow; {@link #BODY} is never absent. */
    public static int passes(float glow) {
        return glow > GLOW_THRESHOLD ? (EYES | BODY) : BODY;
    }

    /** Whether a plan returned by {@link #passes(float)} contains a pass. */
    public static boolean includes(int passes, int pass) {
        return (passes & pass) != 0;
    }
}