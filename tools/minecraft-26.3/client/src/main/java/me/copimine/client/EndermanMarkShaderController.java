package me.copimine.client;

/**
 * Puts the vanilla Enderman screen effect on the marked Wave 2 player.
 *
 * The player asked for the shader the game itself shows when you stand inside an Enderman in
 * spectator mode, not an imitation and not a shaderpack. In 26.3 that effect is the built-in post
 * shader {@code minecraft:invert}: GameRenderer.checkEntityPostEffect applies exactly that
 * identifier for an Enderman, and GameRenderer then renders it from requestedPostEffects.
 *
 * Because requestedPostEffects is plain vanilla render state, this route needs no Iris, no OptiFine
 * and no Sodium. The mod puts the effect in the frame itself, so it works on a bare client. Nothing
 * here touches the player's own shaderpack: the pack is a separate subsystem and its saved state is
 * restored by IrisShaderpackRuntime when the event releases it.
 *
 * The effect is driven strictly by the Wave 2 hunt mark. It is applied while the mark is live and
 * withdrawn the moment the mark clears, expires, or the player leaves the dimension, so a player who
 * was never marked, or who is past wave 2, never sees it.
 */
public final class EndermanMarkShaderController {
    /** Effect id understood by ClientPostProcessController, mapped to minecraft:invert. */
    public static final String EFFECT_ID = "ENDERMAN";

    private final ClientPostProcessController postProcess;

    private volatile boolean active;
    private volatile String status = "idle";
    private volatile String lastFailureReason = "";

    public EndermanMarkShaderController(ClientPostProcessController postProcess) {
        this.postProcess = postProcess;
    }

    /**
     * Requests the effect while the mark is live, and clears it otherwise.
     *
     * Called every frame with the current marked state, so it must be cheap and idempotent.
     * ClientPostProcessController already returns early on an unchanged effect, which keeps the
     * call from re-submitting the post chain on every frame.
     */
    public void update(boolean marked) {
        if (postProcess == null) {
            status = "post-process-controller-not-wired";
            lastFailureReason = "client-post-process-unavailable";
            return;
        }
        if (marked) {
            if (postProcess.apply(EFFECT_ID, 1.0F)) {
                active = true;
                status = "enderman-shader-active";
                lastFailureReason = "";
                return;
            }
            active = false;
            status = "enderman-shader-rejected";
            lastFailureReason = postProcess.lastFailureReason();
            return;
        }
        if (active) {
            postProcess.clear();
            active = false;
            status = "enderman-shader-cleared";
            lastFailureReason = "";
        }
    }

    public boolean active() {
        return active;
    }

    public String statusLine() {
        return status + ", effect=" + EFFECT_ID + ", active=" + active;
    }

    public String lastFailureReason() {
        return lastFailureReason == null || lastFailureReason.isBlank()
                ? "enderman-shader-inactive"
                : lastFailureReason;
    }
}
