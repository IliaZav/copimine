package me.copimine.client;

import me.copimine.client.mixin.ClientWorldAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Client mesh for the server-positioned Last Seal shield ItemDisplays. */
public final class EndRiftGuardianShieldRenderer {
    private static final Identifier TEXTURE = Identifier.of(
            "copimineclient", "textures/entity/end_rift_guardian_shield_hd.png");
    private static final int FULL_BRIGHT_LIGHT = 0x00F000F0;
    private static final float MODEL_UNITS_PER_BLOCK = 16.0F;
    private static final float HALF_DEPTH_PIXELS = 1.5F;
    private static final Map<UUID, EndRiftGuardianShieldModel.Orbit> ORBITS = new HashMap<>();

    private EndRiftGuardianShieldRenderer() {
    }

    public static boolean isShieldCarrier(DisplayEntity entity) {
        if (!(entity instanceof DisplayEntity.ItemDisplayEntity itemDisplay)) {
            return false;
        }
        if (entity.getUuid() != null && EndRiftGuardianShieldModel.VISUAL_ID.equals(
                ClientBridgeProtocol.endEventVisualForEntity(entity.getUuid().toString()))) {
            return true;
        }
        ItemStack stack = itemDisplay.getItemStack();
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CustomModelDataComponent modelData = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
        return modelData != null && EndRiftGuardianShieldModel.isServerCustomModelData(modelData.value());
    }

    /** Hide the carrier as soon as its authoritative model id arrives.  The
     * bridge packet is intentionally not required: it can arrive one or two
     * client ticks after the display, and drawing the vanilla carrier during
     * that gap produces the black square seen in-game. */
    public static boolean isCustomRenderEligible(DisplayEntity entity) {
        return isShieldCarrier(entity);
    }

    /** Draws only at real server ItemDisplay positions; it never derives an orbit locally. */
    public static void render(WorldRenderContext context) {
        if (context == null || context.world() == null || context.camera() == null
                || context.matrixStack() == null || context.consumers() == null) {
            return;
        }
        ClientWorld world = (ClientWorld) context.world();
        Camera camera = context.camera();
        Vec3d cameraPos = camera.getPos();
        if (cameraPos == null) {
            return;
        }
        float tickDelta = context.tickCounter() == null
                ? 1.0F : context.tickCounter().getTickDelta(true);
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();
        long time = world.getTime();
        double renderTick = time + tickDelta;
        Vec3d bossPosition = bossPosition(world, tickDelta);
        Set<UUID> candidateIds = candidateEntityIds(world);
        ORBITS.keySet().retainAll(candidateIds);

        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        for (UUID entityId : candidateIds) {
            Entity entity = ((ClientWorldAccessor) world).copimine$getEntityLookup().get(entityId);
            if (!(entity instanceof DisplayEntity.ItemDisplayEntity display) || entity.isRemoved()
                    || !isShieldCarrier(display)) {
                continue;
            }
            Vec3d position = entity.getLerpedPos(tickDelta);
            if (!finite(position)) {
                continue;
            }
            EndRiftGuardianShieldModel.Pose pose = EndRiftGuardianShieldModel.pose(
                    (time + tickDelta + (entityId.getLeastSignificantBits() & 31L)) / 40.0F);
            if (!pose.isFinite()) {
                continue;
            }
            matrices.push();
            EndRiftGuardianShieldModel.Orbit orbit = null;
            if (finite(bossPosition)) {
                orbit = ORBITS.computeIfAbsent(entityId, ignored ->
                        EndRiftGuardianShieldModel.orbitFromCarrier(
                                bossPosition.x, bossPosition.y, bossPosition.z,
                                position.x, position.y, position.z, renderTick));
            }
            if (orbit != null && orbit.isFinite()) {
                matrices.translate(orbit.xAt(renderTick, bossPosition.x),
                        orbit.yAt(renderTick, bossPosition.y),
                        orbit.zAt(renderTick, bossPosition.z));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(
                        (float) orbit.faceRotationDegreesAt(renderTick)));
            } else {
                matrices.translate(position.x, position.y, position.z);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(
                        EndRiftGuardianShieldModel.faceRotationDegrees(
                                entity.prevYaw, entity.getYaw(), tickDelta)));
            }
            float scale = EndRiftGuardianShieldModel.worldRenderScale(pose);
            matrices.scale(scale, scale, scale);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotation(pose.shieldRoll()));
            VertexConsumer buffer = consumers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE));
            renderShieldMesh(matrices, buffer);
            matrices.pop();
        }
        matrices.pop();
    }

    private static Vec3d bossPosition(ClientWorld world, float tickDelta) {
        String bossId = ClientBridgeProtocol.endEventState().bossUuid();
        if (bossId == null || bossId.isBlank()) {
            return null;
        }
        try {
            Entity boss = ((ClientWorldAccessor) world).copimine$getEntityLookup()
                    .get(UUID.fromString(bossId));
            return boss == null || boss.isRemoved() ? null : boss.getLerpedPos(tickDelta);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Set<UUID> candidateEntityIds(ClientWorld world) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (String raw : ClientBridgeProtocol.endEventVisualEntityIds()) {
            try {
                ids.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
                // Malformed bridge state must not break the authoritative item scan.
            }
        }
        for (Entity entity : world.getEntities()) {
            if (entity instanceof DisplayEntity.ItemDisplayEntity display && !entity.isRemoved()
                    && entity.getUuid() != null && isShieldCarrier(display)) {
                ids.add(entity.getUuid());
            }
        }
        return ids;
    }

    /** Shared plate geometry for Wave 6; carrier motion and ownership remain with its caller. */
    public static void renderShieldMesh(MatrixStack matrices, VertexConsumer buffer) {
        MatrixStack.Entry entry = matrices.peek();
        List<EndRiftGuardianShieldModel.Quad> front = EndRiftGuardianShieldModel.frontQuads();
        for (EndRiftGuardianShieldModel.Quad quad : front) {
            emitQuad(buffer, entry, quad.first(), quad.second(), quad.third(), quad.fourth(),
                    -HALF_DEPTH_PIXELS, 0.0F, 0.0F, -1.0F,
                    EndRiftGuardianShieldModel.renderTint());
            emitQuad(buffer, entry, quad.third(), quad.second(), quad.first(), quad.first(),
                    HALF_DEPTH_PIXELS, 0.0F, 0.0F, 1.0F,
                    EndRiftGuardianShieldModel.renderTint());
        }

        List<EndRiftGuardianShieldModel.Point> outline = EndRiftGuardianShieldModel.outline();
        for (int index = 0; index < outline.size(); index++) {
            EndRiftGuardianShieldModel.Point first = outline.get(index);
            EndRiftGuardianShieldModel.Point second = outline.get((index + 1) % outline.size());
            float dx = second.x() - first.x();
            float dy = second.y() - first.y();
            float length = (float) Math.hypot(dx, dy);
            if (length <= 0.0001F) {
                continue;
            }
            float normalX = dy / length;
            float normalY = dx / length;
            emitVertex(buffer, entry, first, -HALF_DEPTH_PIXELS, normalX, normalY, 0.0F,
                    EndRiftGuardianShieldModel.sideTint());
            emitVertex(buffer, entry, second, -HALF_DEPTH_PIXELS, normalX, normalY, 0.0F,
                    EndRiftGuardianShieldModel.sideTint());
            emitVertex(buffer, entry, second, HALF_DEPTH_PIXELS, normalX, normalY, 0.0F,
                    EndRiftGuardianShieldModel.sideTint());
            emitVertex(buffer, entry, first, HALF_DEPTH_PIXELS, normalX, normalY, 0.0F,
                    EndRiftGuardianShieldModel.sideTint());
        }
    }

    private static void emitQuad(VertexConsumer buffer, MatrixStack.Entry entry,
                                 EndRiftGuardianShieldModel.Point first,
                                 EndRiftGuardianShieldModel.Point second,
                                 EndRiftGuardianShieldModel.Point third,
                                 EndRiftGuardianShieldModel.Point fourth, float z,
                                 float normalX, float normalY, float normalZ, int tint) {
        emitVertex(buffer, entry, first, z, normalX, normalY, normalZ, tint);
        emitVertex(buffer, entry, second, z, normalX, normalY, normalZ, tint);
        emitVertex(buffer, entry, third, z, normalX, normalY, normalZ, tint);
        emitVertex(buffer, entry, fourth, z, normalX, normalY, normalZ, tint);
    }

    private static void emitVertex(VertexConsumer buffer, MatrixStack.Entry entry,
                                   EndRiftGuardianShieldModel.Point point, float z,
                                   float normalX, float normalY, float normalZ, int tint) {
        buffer.vertex(entry, point.x() / MODEL_UNITS_PER_BLOCK,
                        EndRiftGuardianShieldModel.worldY(point), z / MODEL_UNITS_PER_BLOCK)
                .color(tint)
                .texture(EndRiftGuardianShieldModel.textureU(point),
                        EndRiftGuardianShieldModel.textureV(point))
                .overlay(0)
                .light(FULL_BRIGHT_LIGHT)
                .normal(entry, normalX, normalY, normalZ);
    }

    private static boolean finite(Vec3d value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }
}
