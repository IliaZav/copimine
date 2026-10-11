package me.copimine.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.Vec3;

/** Small adapter for frame-bounded custom geometry in Minecraft's deferred renderer. */
public final class EndRiftRenderSubmission {
    private EndRiftRenderSubmission() { }

    @FunctionalInterface
    public interface Geometry {
        void render(PoseStack matrices, VertexConsumer vertices);
    }

    public static void submit(LevelRenderContext context, Vec3 cameraPosition,
                              RenderType renderType, Geometry geometry) {
        if (context == null || cameraPosition == null || renderType == null || geometry == null
                || !Double.isFinite(cameraPosition.x) || !Double.isFinite(cameraPosition.y)
                || !Double.isFinite(cameraPosition.z)) {
            return;
        }

        PoseStack cameraRelative = new PoseStack();
        cameraRelative.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
        context.submitNodeCollector().submitCustomGeometry(cameraRelative, renderType, (pose, vertices) -> {
            PoseStack matrices = new PoseStack();
            matrices.mulPose(pose.pose());
            geometry.render(matrices, vertices);
        });
    }
}
