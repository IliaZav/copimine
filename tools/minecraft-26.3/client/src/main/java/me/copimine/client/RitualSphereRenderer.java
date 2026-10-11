package me.copimine.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Render the round Wave 6 shell and individual server-positioned guard shields. */
public final class RitualSphereRenderer {
    private static final Identifier MEMBRANE = Identifier.fromNamespaceAndPath("copimineclient", "textures/entity/end_event_ritual_membrane.png");
    private static final Identifier GUARDIAN_SHIELD_TEXTURE = Identifier.fromNamespaceAndPath("copimineclient", "textures/entity/end_rift_guardian_shield_hd.png");
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final int MAX_CUSTOM_DISPLAY_CARRIERS = 16;
    private RitualSphereRenderer() { }

    private static int modelData(Display entity) {
        if (!(entity instanceof Display.ItemDisplay item)) return -1;
        if (entity.getUUID() != null
                && ClientBridgeProtocol.isEndEventEntityRenderSuppressed(entity.getUUID().toString())) return -1;
        ItemStack stack = item.getItemStack();
        CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return EndRiftItemModelData.firstFloatAsExactInteger(data);
    }
    public static boolean isCustomRenderEligible(Display entity) {
        int data = modelData(entity);
        return data == RitualSphereMesh.SPHERE_MODEL_DATA || data == RitualSphereMesh.SHIELD_MODEL_DATA;
    }

    public static void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (context == null || client.level == null) return;
        Camera camera = client.gameRenderer.mainCamera();
        if (camera == null || !camera.isInitialized()) return;
        Vec3 cameraPosition = camera.position();
        float delta = context.levelState().worldPartialTicks;
        List<Display> sphereCarriers = new ArrayList<>();
        List<Display> shieldCarriers = new ArrayList<>();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof Display display) || entity.isRemoved()) continue;
            int data = modelData(display);
            if (data == RitualSphereMesh.SPHERE_MODEL_DATA) sphereCarriers.add(display);
            else if (data == RitualSphereMesh.SHIELD_MODEL_DATA) shieldCarriers.add(display);
            if (sphereCarriers.size() + shieldCarriers.size() >= MAX_CUSTOM_DISPLAY_CARRIERS) break;
        }
        if (!sphereCarriers.isEmpty()) {
            EndRiftRenderSubmission.submit(context, cameraPosition,
                    RenderTypes.entityTranslucentEmissive(MEMBRANE), (matrices, buffer) -> {
                for (Display entity : sphereCarriers) {
                    if (entity.isRemoved()) continue;
                    Vec3 position = entity.getPosition(delta);
                    Vec3 relativeCamera = cameraPosition.subtract(position);
                    if (relativeCamera.lengthSqr() > 64 * 64
                            || !RitualSphereMesh.visibleFrom(relativeCamera.x, relativeCamera.y, relativeCamera.z)) continue;
                    matrices.pushPose();
                    matrices.translate(position.x, position.y, position.z);
                    float yaw = net.minecraft.util.Mth.rotLerp(delta, entity.yRotO, entity.getYRot());
                    matrices.rotateDegrees(Axis.YP, -yaw);
                    Vec3 localView = relativeCamera.yRot((float) Math.toRadians(yaw)).normalize();
                    boolean outside = RitualSphereMesh.shellVisibleFrom(relativeCamera.x, relativeCamera.y, relativeCamera.z);
                    RitualSphereMesh.InteriorView interiorView = null;
                    if (!outside) {
                        Vec3 localCamera = relativeCamera.yRot((float) Math.toRadians(yaw));
                        Vec3 forward = new Vec3(camera.forwardVector()).yRot((float) Math.toRadians(yaw));
                        Vec3 up = new Vec3(camera.upVector()).yRot((float) Math.toRadians(yaw));
                        interiorView = new RitualSphereMesh.InteriorView(localCamera.x, localCamera.y, localCamera.z,
                                forward.x, forward.y, forward.z, up.x, up.y, up.z);
                    }
                    for (var quad : outside ? RitualSphereMesh.quads() : RitualSphereMesh.interiorQuads(interiorView)) {
                        float nx = (quad.first().x() + quad.second().x() + quad.third().x() + quad.fourth().x()) / 4;
                        float ny = (quad.first().y() + quad.second().y() + quad.third().y() + quad.fourth().y()) / 4;
                        float nz = (quad.first().z() + quad.second().z() + quad.third().z() + quad.fourth().z()) / 4;
                        int alpha;
                        if (outside) {
                            double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
                            double facing = (nx * localView.x + ny * localView.y + nz * localView.z) / length;
                            if (facing <= 0) continue;
                            alpha = RitualSphereMesh.surfaceAlpha(facing);
                        } else {
                            alpha = RitualSphereMesh.interiorAlpha(quad, interiorView);
                            if (alpha <= 0) continue;
                            nx = -nx; ny = -ny; nz = -nz;
                        }
                        int tint = (alpha << 24) | 0xFFFFFF;
                        vertex(buffer, matrices.last(), quad.first(), nx, ny, nz, tint);
                        vertex(buffer, matrices.last(), quad.second(), nx, ny, nz, tint);
                        vertex(buffer, matrices.last(), quad.third(), nx, ny, nz, tint);
                        vertex(buffer, matrices.last(), quad.fourth(), nx, ny, nz, tint);
                    }
                    matrices.popPose();
                }
            });
        }
        if (!shieldCarriers.isEmpty()) {
            EndRiftRenderSubmission.submit(context, cameraPosition,
                    RenderTypes.entityTranslucent(GUARDIAN_SHIELD_TEXTURE), (matrices, buffer) -> {
                for (Display entity : shieldCarriers) {
                    if (entity.isRemoved() || entity.getUUID() == null) continue;
                    Vec3 position = entity.getPosition(delta);
                    if (cameraPosition.distanceToSqr(position) > 64 * 64) continue;
                    matrices.pushPose();
                    matrices.translate(position.x, position.y, position.z);
                    matrices.rotate(camera.rotation());
                    EndRiftGuardianShieldModel.Pose pose = EndRiftGuardianShieldModel.pose(
                            (client.level.getGameTime() + delta
                                    + (entity.getUUID().getLeastSignificantBits() & 31L)) / 40.0F);
                    float scale = EndRiftGuardianShieldModel.worldRenderScale(pose);
                    matrices.scale(scale, scale, scale);
                    matrices.rotate(Axis.ZP, pose.shieldRoll());
                    EndRiftGuardianShieldRenderer.renderShieldMesh(matrices, buffer);
                    matrices.popPose();
                }
            });
        }
    }
    private static void vertex(VertexConsumer buffer, PoseStack.Pose entry, RitualSphereMesh.Point point,
                               float nx, float ny, float nz, int tint) {
        float length = (float)Math.sqrt(nx*nx+ny*ny+nz*nz);
        buffer.addVertex(entry,point.x(),point.y(),point.z()).setColor(tint).setUv(point.u(),point.v())
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(entry,nx/length,ny/length,nz/length);
    }
}
