package me.copimine.client;

import me.copimine.client.mixin.ClientWorldAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.phys.Vec3;
import java.util.LinkedHashSet;
import java.util.List;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Client mesh for the server-positioned Last Seal shield ItemDisplays. */
public final class EndRiftGuardianShieldRenderer {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            "copimineclient", "textures/entity/end_rift_guardian_shield_hd.png");
    private static final int FULL_BRIGHT_LIGHT = 0x00F000F0;
    private static final float MODEL_UNITS_PER_BLOCK = 16.0F;
    private static final float HALF_DEPTH_PIXELS = 1.5F;
    private static final Map<UUID, EndRiftGuardianShieldModel.Orbit> ORBITS = new HashMap<>();
    private static ClientLevel candidateWorld;
    private static long candidateGameTime = Long.MIN_VALUE;
    private static Set<UUID> cachedCandidateIds = new LinkedHashSet<>();

    private EndRiftGuardianShieldRenderer() {
    }

    public static boolean isShieldCarrier(Display entity) {
        if (!(entity instanceof Display.ItemDisplay itemDisplay)) {
            return false;
        }
        if (isBridgeBound(entity)) return true;
        return hasShieldMarker(itemDisplay);
    }

    private static boolean isBridgeBound(Display entity) {
        return entity != null && entity.getUUID() != null && EndRiftGuardianShieldModel.VISUAL_ID.equals(
                ClientBridgeProtocol.endEventVisualForEntity(entity.getUUID().toString()));
    }

    private static boolean hasShieldMarker(Display.ItemDisplay itemDisplay) {
        if (itemDisplay.getUUID() != null
                && ClientBridgeProtocol.isEndEventEntityRenderSuppressed(itemDisplay.getUUID().toString())) return false;
        ItemStack stack = itemDisplay.getItemStack();
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CustomModelData modelData = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return EndRiftGuardianShieldModel.isServerCustomModelData(
                EndRiftItemModelData.firstFloatAsExactInteger(modelData));
    }

    /** Hide the carrier as soon as its authoritative model id arrives.  The
     * bridge packet is intentionally not required: it can arrive one or two
     * client ticks after the display, and drawing the vanilla carrier during
     * that gap produces the black square seen in-game. */
    public static boolean isCustomRenderEligible(Display entity) {
        if (entity == null || entity.getUUID() == null || !isShieldCarrier(entity)) return false;
        Set<UUID> candidates = candidateEntityIds(Minecraft.getInstance().level);
        if (candidates.contains(entity.getUUID())) return true;
        return EndRiftGuardianShieldCandidatePolicy.admit(candidates, entity.getUUID(),
                isBridgeBound(entity), entity instanceof Display.ItemDisplay itemDisplay
                        && hasShieldMarker(itemDisplay));
    }

    /** Draws only at real server ItemDisplay positions; it never derives an orbit locally. */
    public static void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (context == null || client.level == null) return;
        ClientLevel world = client.level;
        var camera = client.gameRenderer.mainCamera();
        if (camera == null || !camera.isInitialized()) return;
        Vec3 cameraPos = camera.position();
        float tickDelta = context.levelState().worldPartialTicks;
        long time = world.getGameTime();
        double renderTick = time + tickDelta;
        Vec3 bossPosition = bossPosition(world, tickDelta);
        Set<UUID> candidateIds = candidateEntityIds(world);
        ORBITS.keySet().retainAll(candidateIds);
        if (candidateIds.isEmpty()) return;
        EndRiftRenderSubmission.submit(context, cameraPos, RenderTypes.entityTranslucent(TEXTURE), (matrices, buffer) -> {
            for (UUID entityId : candidateIds) {
                Entity entity = ((ClientWorldAccessor) world).copimine$getEntities().get(entityId);
                if (!(entity instanceof Display.ItemDisplay display) || entity.isRemoved()
                        || !isShieldCarrier(display)) continue;
                Vec3 position = entity.getPosition(tickDelta);
                if (!finite(position)) continue;
                EndRiftGuardianShieldModel.Pose pose = EndRiftGuardianShieldModel.pose(
                        (time + tickDelta + (entityId.getLeastSignificantBits() & 31L)) / 40.0F);
                if (!pose.isFinite()) continue;
                matrices.pushPose();
                EndRiftGuardianShieldModel.Orbit orbit = null;
                if (finite(bossPosition)) {
                    orbit = ORBITS.get(entityId);
                    if (EndRiftGuardianShieldModel.shouldReseedOrbit(
                            orbit, renderTick,
                            bossPosition.x, bossPosition.y, bossPosition.z,
                            position.x, position.y, position.z)) {
                        orbit = EndRiftGuardianShieldModel.orbitFromCarrier(
                                bossPosition.x, bossPosition.y, bossPosition.z,
                                position.x, position.y, position.z, renderTick);
                        if (orbit.isFinite()) {
                            ORBITS.put(entityId, orbit);
                        }
                    }
                }
                if (orbit != null && orbit.isFinite()) {
                    matrices.translate(orbit.xAt(renderTick, bossPosition.x),
                            orbit.yAt(renderTick, bossPosition.y),
                            orbit.zAt(renderTick, bossPosition.z));
                    matrices.rotate(Axis.YP, (float) Math.toRadians(orbit.faceRotationDegreesAt(renderTick)));
                } else {
                    matrices.translate(position.x, position.y, position.z);
                    float yaw = EndRiftGuardianShieldModel.faceRotationDegrees(
                            entity.yRotO, entity.getYRot(), tickDelta);
                    matrices.rotate(Axis.YP, (float) Math.toRadians(yaw));
                }
                float scale = EndRiftGuardianShieldModel.worldRenderScale(pose);
                matrices.scale(scale, scale, scale);
                matrices.rotate(Axis.ZP, pose.shieldRoll());
                renderShieldMesh(matrices, buffer);
                matrices.popPose();
            }
        });
    }

    private static Vec3 bossPosition(ClientLevel world, float tickDelta) {
        String bossId = ClientBridgeProtocol.endEventState().bossUuid();
        if (bossId == null || bossId.isBlank()) {
            return null;
        }
        try {
            Entity boss = ((ClientWorldAccessor) world).copimine$getEntities()
                    .get(UUID.fromString(bossId));
            return boss == null || boss.isRemoved() ? null : boss.getPosition(tickDelta);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Set<UUID> candidateEntityIds(ClientLevel world) {
        if (world == null) {
            candidateWorld = null;
            candidateGameTime = Long.MIN_VALUE;
            cachedCandidateIds = new LinkedHashSet<>();
            return cachedCandidateIds;
        }
        long gameTime = world.getGameTime();
        if (candidateWorld == world && candidateGameTime == gameTime) return cachedCandidateIds;

        LinkedHashSet<UUID> ids = new LinkedHashSet<>(EndRiftGuardianShieldCandidatePolicy.MAX_CANDIDATES);
        var entityLookup = ((ClientWorldAccessor) world).copimine$getEntities();
        int inspectedCount = 0;
        for (String raw : ClientBridgeProtocol.endEventVisualEntityIds()) {
            if (!EndRiftGuardianShieldCandidatePolicy.canInspectEntity(inspectedCount)
                    || EndRiftGuardianShieldCandidatePolicy.isFull(ids)) break;
            inspectedCount++;
            try {
                UUID entityId = UUID.fromString(raw);
                Entity entity = entityLookup.get(entityId);
                if (entity instanceof Display.ItemDisplay display && !entity.isRemoved()) {
                    EndRiftGuardianShieldCandidatePolicy.admit(ids, entityId,
                            isBridgeBound(display), hasShieldMarker(display));
                }
            } catch (IllegalArgumentException ignored) {
                // Malformed bridge state must not prevent fallback discovery.
            }
        }
        if (!EndRiftGuardianShieldCandidatePolicy.isFull(ids)) {
            for (Entity entity : world.entitiesForRendering()) {
                if (!EndRiftGuardianShieldCandidatePolicy.canInspectEntity(inspectedCount)) break;
                inspectedCount++;
                if (!(entity instanceof Display.ItemDisplay display) || entity.isRemoved()
                        || entity.getUUID() == null) continue;
                boolean bridgeBound = isBridgeBound(display);
                boolean markerPresent = hasShieldMarker(display);
                if (EndRiftGuardianShieldCandidatePolicy.admit(
                        ids, entity.getUUID(), bridgeBound, markerPresent)
                        && EndRiftGuardianShieldCandidatePolicy.isFull(ids)) {
                    break;
                }
            }
        }
        candidateWorld = world;
        candidateGameTime = gameTime;
        cachedCandidateIds = ids;
        return cachedCandidateIds;
    }

    /** Shared plate geometry for Wave 6; carrier motion and ownership remain with its caller. */
    public static void renderShieldMesh(PoseStack matrices, VertexConsumer buffer) {
        PoseStack.Pose entry = matrices.last();
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

    private static void emitQuad(VertexConsumer buffer, PoseStack.Pose entry,
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

    private static void emitVertex(VertexConsumer buffer, PoseStack.Pose entry,
                                   EndRiftGuardianShieldModel.Point point, float z,
                                   float normalX, float normalY, float normalZ, int tint) {
        buffer.addVertex(entry, point.x() / MODEL_UNITS_PER_BLOCK,
                        EndRiftGuardianShieldModel.worldY(point), z / MODEL_UNITS_PER_BLOCK)
                .setColor(tint)
                .setUv(EndRiftGuardianShieldModel.textureU(point),
                        EndRiftGuardianShieldModel.textureV(point))
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT_LIGHT)
                .setNormal(entry, normalX, normalY, normalZ);
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }
}
