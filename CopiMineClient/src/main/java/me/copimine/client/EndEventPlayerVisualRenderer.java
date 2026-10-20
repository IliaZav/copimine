package me.copimine.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Carrier/hunt feedback uses only soft outer edges; no labels or camera motion. */
public final class EndEventPlayerVisualRenderer {
    private EndEventPlayerVisualRenderer() { }

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || client.player.isDead() || client.options.hudHidden) return;
        long nowMillis = System.currentTimeMillis();
        String dimension = client.world.getRegistryKey().getValue().getPath();
        var state = ClientBridgeProtocol.endEventPlayerVisuals().visualState(dimension, nowMillis);
        for (var edge : EndEventPlayerVisualLayout.edges(context.getScaledWindowWidth(),
                context.getScaledWindowHeight(), state, nowMillis)) {
            context.fillGradient(edge.left(), edge.top(), edge.right(), edge.bottom(), edge.colorFrom(), edge.colorTo());
        }
    }
}
