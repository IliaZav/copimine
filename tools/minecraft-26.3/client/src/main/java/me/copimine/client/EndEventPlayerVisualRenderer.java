package me.copimine.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Carrier/hunt feedback uses only soft outer edges; no labels or camera motion. */
public final class EndEventPlayerVisualRenderer {
    private EndEventPlayerVisualRenderer() { }

    public static void render(GuiGraphicsExtractor context) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || client.player.isDeadOrDying() || client.gui.hud.isHidden()) return;
        long nowMillis = System.currentTimeMillis();
        String dimension = client.level.dimension().identifier().getPath();
        var state = ClientBridgeProtocol.endEventPlayerVisuals().visualState(dimension, nowMillis);
        for (var edge : EndEventPlayerVisualLayout.edges(context.guiWidth(),
                context.guiHeight(), state, nowMillis)) {
            context.fillGradient(edge.left(), edge.top(), edge.right(), edge.bottom(), edge.colorFrom(), edge.colorTo());
        }
    }
}
