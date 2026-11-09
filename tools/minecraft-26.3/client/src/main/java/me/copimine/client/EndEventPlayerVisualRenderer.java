package me.copimine.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Carrier/hunt feedback uses only soft outer edges; no labels or camera motion. */
public final class EndEventPlayerVisualRenderer {
    private EndEventPlayerVisualRenderer() { }

    public static void render(GuiGraphicsExtractor context) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || client.player.isDeadOrDying() || client.gui.hud.isHidden()) {
            // Leaving the world or dying must withdraw the mark shader, or a fresh session would
            // start with a stale screen effect it can never clear.
            ClientBridgeProtocol.endermanMarkShader().update(false);
            return;
        }
        long nowMillis = System.currentTimeMillis();
        String dimension = client.level.dimension().identifier().getPath();
        var state = ClientBridgeProtocol.endEventPlayerVisuals().visualState(dimension, nowMillis);
        // The Enderman shader belongs to the hunt mark and to nothing else. It is applied while the
        // mark is live and withdrawn as soon as the mark expires, clears or the player leaves the
        // wave 2 dimension. The player asked for the in-game Enderman effect, so this is the
        // vanilla post shader rather than a shaderpack.
        if (state != null && state.kind() == EndEventPlayerVisualManager.Kind.HUNT) {
            ClientBridgeProtocol.endermanMarkShader().updateIntro(nowMillis - state.startedAtMillis());
        } else {
            ClientBridgeProtocol.endermanMarkShader().update(false);
        }
        for (var edge : EndEventPlayerVisualLayout.edges(context.guiWidth(),
                context.guiHeight(), state, nowMillis)) {
            context.fillGradient(edge.left(), edge.top(), edge.right(), edge.bottom(), edge.colorFrom(), edge.colorTo());
        }
    }
}
