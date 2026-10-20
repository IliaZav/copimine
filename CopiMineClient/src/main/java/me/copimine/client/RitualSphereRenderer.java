package me.copimine.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/** Render the round Wave 6 shell and individual server-positioned guard shields. */
public final class RitualSphereRenderer {
    private static final Identifier MEMBRANE = Identifier.of("copimineclient", "textures/entity/end_event_ritual_membrane.png");
    private static final Identifier GUARDIAN_SHIELD_TEXTURE = Identifier.of("copimineclient", "textures/entity/end_rift_guardian_shield_hd.png");
    private static final int FULL_BRIGHT = 0x00F000F0;
    private RitualSphereRenderer() { }

    private static int modelData(DisplayEntity entity) {
        if (!(entity instanceof DisplayEntity.ItemDisplayEntity item)) return -1;
        ItemStack stack = item.getItemStack();
        CustomModelDataComponent data = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
        return data == null ? -1 : data.value();
    }
    public static boolean isCustomRenderEligible(DisplayEntity entity) {
        int data = modelData(entity);
        return data == RitualSphereMesh.SPHERE_MODEL_DATA || data == RitualSphereMesh.SHIELD_MODEL_DATA;
    }

    public static void render(WorldRenderContext context) {
        if (context == null || context.world() == null || context.camera() == null
                || context.matrixStack() == null || context.consumers() == null) return;
        Vec3d camera = context.camera().getPos();
        float delta = context.tickCounter() == null ? 1.0F : context.tickCounter().getTickDelta(true);
        MatrixStack matrices = context.matrixStack();
        for (Entity entity : context.world().getEntities()) {
            if (!(entity instanceof DisplayEntity display) || entity.isRemoved()) continue;
            int data = modelData(display);
            if (data != RitualSphereMesh.SPHERE_MODEL_DATA && data != RitualSphereMesh.SHIELD_MODEL_DATA) continue;
            Vec3d position = entity.getLerpedPos(delta);
            Vec3d relativeCamera = camera.subtract(position);
            if (relativeCamera.lengthSquared() > 64*64) continue;
            if (data == RitualSphereMesh.SPHERE_MODEL_DATA
                    && !RitualSphereMesh.visibleFrom(relativeCamera.x, relativeCamera.y, relativeCamera.z)) continue;
            matrices.push();
            matrices.translate(position.x-camera.x, position.y-camera.y, position.z-camera.z);
            if (data == RitualSphereMesh.SPHERE_MODEL_DATA) {
                float yaw = net.minecraft.util.math.MathHelper.lerpAngleDegrees(delta, entity.prevYaw, entity.getYaw());
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
                Vec3d localView = relativeCamera.rotateY((float)Math.toRadians(yaw)).normalize();
                boolean outside = RitualSphereMesh.shellVisibleFrom(relativeCamera.x, relativeCamera.y, relativeCamera.z);
                RitualSphereMesh.InteriorView interiorView = null;
                if (!outside) {
                    Vec3d localCamera = relativeCamera.rotateY((float)Math.toRadians(yaw));
                    Vec3d forward = new Vec3d(context.camera().getHorizontalPlane()).rotateY((float)Math.toRadians(yaw));
                    Vec3d up = new Vec3d(context.camera().getVerticalPlane()).rotateY((float)Math.toRadians(yaw));
                    interiorView = new RitualSphereMesh.InteriorView(localCamera.x, localCamera.y, localCamera.z,
                            forward.x, forward.y, forward.z, up.x, up.y, up.z);
                }
                VertexConsumer buffer = context.consumers().getBuffer(RenderLayer.getEntityTranslucentEmissive(MEMBRANE));
                for (var quad : outside ? RitualSphereMesh.quads() : RitualSphereMesh.interiorQuads(interiorView)) {
                    float nx = (quad.first().x()+quad.second().x()+quad.third().x()+quad.fourth().x())/4;
                    float ny = (quad.first().y()+quad.second().y()+quad.third().y()+quad.fourth().y())/4;
                    float nz = (quad.first().z()+quad.second().z()+quad.third().z()+quad.fourth().z())/4;
                    int alpha;
                    if (outside) {
                        // Draw the outside facing this camera. Even a two-sided
                        // translucent layer cannot fill the view with the far shell.
                        double length = Math.sqrt(nx*nx+ny*ny+nz*nz);
                        double facing = (nx*localView.x + ny*localView.y + nz*localView.z)/length;
                        if (facing <= 0) continue;
                        alpha = RitualSphereMesh.surfaceAlpha(facing);
                    } else {
                        alpha = RitualSphereMesh.interiorAlpha(quad, interiorView);
                        if (alpha <= 0) continue;
                        nx = -nx; ny = -ny; nz = -nz;
                    }
                    int tint = (alpha << 24) | 0xFFFFFF;
                    vertex(buffer, matrices.peek(), quad.first(), nx, ny, nz, tint);
                    vertex(buffer, matrices.peek(), quad.second(), nx, ny, nz, tint);
                    vertex(buffer, matrices.peek(), quad.third(), nx, ny, nz, tint);
                    vertex(buffer, matrices.peek(), quad.fourth(), nx, ny, nz, tint);
                }
            } else {
                // A separate camera-facing pose for each viewer keeps the plate
                // readable even when its orbit is tangent to that viewer.
                matrices.multiply(context.camera().getRotation());
                EndRiftGuardianShieldModel.Pose pose = EndRiftGuardianShieldModel.pose(
                        (context.world().getTime() + delta + (entity.getUuid().getLeastSignificantBits() & 31L)) / 40.0F);
                float scale = EndRiftGuardianShieldModel.worldRenderScale(pose);
                matrices.scale(scale, scale, scale);
                matrices.multiply(RotationAxis.POSITIVE_Z.rotation(pose.shieldRoll()));
                VertexConsumer buffer = context.consumers().getBuffer(RenderLayer.getEntityTranslucent(GUARDIAN_SHIELD_TEXTURE));
                // The same atlas, mesh, UVs and visual pulse as the boss shield.
                // Only the Wave 6 server carrier controls position and lifetime.
                EndRiftGuardianShieldRenderer.renderShieldMesh(matrices, buffer);
            }
            matrices.pop();
        }
    }
    private static void vertex(VertexConsumer buffer, MatrixStack.Entry entry, RitualSphereMesh.Point point,
                               float nx, float ny, float nz, int tint) {
        float length = (float)Math.sqrt(nx*nx+ny*ny+nz*nz);
        buffer.vertex(entry,point.x(),point.y(),point.z()).color(tint).texture(point.u(),point.v())
                .overlay(OverlayTexture.DEFAULT_UV).light(FULL_BRIGHT).normal(entry,nx/length,ny/length,nz/length);
    }
}
