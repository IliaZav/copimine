package me.copimine.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/** Normal world render path, with depth testing and no event shader in safe chambers. */
public final class BlackFogChamberRenderer {
    private BlackFogChamberRenderer() {}
    public static void render(LevelRenderContext context) {
        var chamber = BlackFogVolumeRuntime.chamber();
        Minecraft client = Minecraft.getInstance();
        if (chamber == null || context == null || client.player == null || client.level == null
                || client.player.isDeadOrDying() || !client.gameRenderer.mainCamera().isInitialized()) return;
        var fog = ClientBridgeProtocol.endEventBlackFog();
        float strength = fog.fadeStrength(client.level.dimension().identifier().getPath(), System.currentTimeMillis());
        if (strength <= 0) return;
        var mesh = BlackFogChamberMesh.build(chamber.minX(),chamber.minZ(),chamber.maxX(),chamber.maxZ(),
                chamber.floor(),strength, (client.level.getGameTime()+context.levelState().worldPartialTicks)/20.0);
        EndRiftRenderSubmission.submit(context, client.gameRenderer.mainCamera().position(), RenderTypes.debugQuads(),
                (matrices, vertices) -> {
                    for (var quad : mesh) {
                        vertex(vertices,matrices.last(),quad.a()); vertex(vertices,matrices.last(),quad.b());
                        vertex(vertices,matrices.last(),quad.c()); vertex(vertices,matrices.last(),quad.d());
                    }
                });
    }
    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, BlackFogChamberMesh.Point p) {
        buffer.addVertex(pose,p.x(),p.y(),p.z()).setColor(p.color());
    }
}
