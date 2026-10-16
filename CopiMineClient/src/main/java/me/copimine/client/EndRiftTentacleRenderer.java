package me.copimine.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import me.copimine.client.mixin.ClientWorldAccessor;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Dedicated renderer for the event-owned tentacle carrier displays. The
 * carrier remains a real server entity for visibility and lifecycle, while
 * the supplied Kagune mesh is rendered with its six imported pieces, UVs,
 * hierarchy, and animation clips.
 */
public final class EndRiftTentacleRenderer {
    private static final Identifier TEXTURE = Identifier.of(
            "copimineclient", "textures/entity/end_rift_tentacle_hd.png");
    private static final EndRiftTentacleRig.RenderRig RIG = EndRiftTentacleRig.createRenderRig();
    private static final Map<UUID, FacingState> FACING_BY_ENTITY = new HashMap<>();

    private EndRiftTentacleRenderer() {
    }

    public static Identifier textureForVisual(String visualId) {
        return EndRiftTentacleModel.VISUAL_ID.equals(normalize(visualId)) ? TEXTURE : null;
    }

    public static EndRiftTentaclePose.TentaclePose poseFor(String visualId,
                                                            String animationId,
                                                            long elapsedTicks) {
        if (!EndRiftTentacleModel.VISUAL_ID.equals(normalize(visualId))) {
            return EndRiftTentaclePose.TentaclePose.identity();
        }
        float progress = EndRiftTentacleAnimator.normalizedProgress(animationId, elapsedTicks);
        return EndRiftTentacleAnimator.poseFor(animationId, progress, 0L);
    }

    public static EndRiftTentaclePose.TentaclePose poseForAt(String visualId,
                                                              String animationId,
                                                              long elapsedMillis,
                                                              long durationMillis,
                                                              long deterministicSeed) {
        if (!EndRiftTentacleModel.VISUAL_ID.equals(normalize(visualId))) {
            return EndRiftTentaclePose.TentaclePose.identity();
        }
        int durationTicks = EndRiftTentacleAnimator.durationTicks(animationId);
        // Looping clips use their authored length, not the server carrier's
        // lifetime/state timeout (READY bindings can be 60 seconds long).
        // Using that unrelated timeout stretched the supplied idle keyframes
        // until they appeared frozen and then snapped back to frame zero.
        long safeDurationMillis = EndRiftTentacleAnimator.loops(animationId)
                ? EndRiftTentacleAnimator.loopDurationMillis(animationId)
                : durationMillis > 0L ? durationMillis : durationTicks * 50L;
        float progress;
        if (EndRiftTentacleAnimator.loops(animationId)) {
            long elapsed = Math.max(0L, elapsedMillis);
            progress = (float) ((elapsed % safeDurationMillis) / (double) safeDurationMillis);
        } else {
            progress = Math.max(0.0F, Math.min(1.0F,
                    Math.max(0L, elapsedMillis) / (float) safeDurationMillis));
        }
        return EndRiftTentacleAnimator.poseFor(animationId, progress, deterministicSeed);
    }

    /**
     * Identifies the server-side ItemDisplay that carries a tentacle. The
     * bridge visual binding is preferred when present, but the item component
     * is authoritative enough to recover from a delayed or missed bind packet.
     */
    public static boolean isTentacleCarrier(DisplayEntity entity) {
        if (!(entity instanceof DisplayEntity.ItemDisplayEntity itemDisplay)) {
            return false;
        }
        if (entity.getUuid() != null
                && EndRiftTentacleModel.VISUAL_ID.equals(
                        ClientBridgeProtocol.endEventVisualForEntity(entity.getUuid().toString()))) {
            return true;
        }
        ItemStack stack = itemDisplay.getItemStack();
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CustomModelDataComponent customModelData = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
        return customModelData != null
                && EndRiftTentacleModel.isServerCustomModelData(customModelData.value());
    }

    /** Whether the vanilla carrier is safe to hide for this entity. */
    public static boolean isCustomRenderEligible(DisplayEntity entity) {
        // CustomModelData is authoritative on the carrier itself. Waiting for
        // the optional bridge bind left a vanilla square visible and made the
        // articulated tentacle appear missing during reconnects.
        return isTentacleCarrier(entity);
    }

    /** Draw all visible event tentacle carriers in one bounded world pass. */
    public static void render(WorldRenderContext context) {
        if (context == null || context.world() == null || context.camera() == null
                || context.matrixStack() == null || context.consumers() == null) {
            return;
        }
        Camera camera = context.camera();
        Vec3d cameraPos = camera.getPos();
        if (cameraPos == null) {
            return;
        }
        float tickDelta = context.tickCounter() == null
                ? 1.0F : context.tickCounter().getTickDelta(true);
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();
        long nowMillis = System.currentTimeMillis();

        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Set<UUID> candidateIds = candidateEntityIds((ClientWorld) context.world());
        FACING_BY_ENTITY.keySet().retainAll(candidateIds);
        for (UUID entityUuid : candidateIds) {
            Entity entity = ((ClientWorldAccessor) context.world())
                    .copimine$getEntityLookup().get(entityUuid);
            if (!(entity instanceof DisplayEntity.ItemDisplayEntity display)
                    || entity.isRemoved() || entity.getUuid() == null
                    || !isTentacleCarrier(display)) {
                continue;
            }
            String uuid = entityUuid.toString();
            EndRiftTentaclePose.TentaclePose pose =
                    ClientBridgeProtocol.endEventTentaclePoseAt(uuid, nowMillis);
            if (pose == null) {
                String fallbackAnimation = ClientBridgeProtocol
                        .endEventAnimationForEntity(uuid);
                pose = poseForAt(EndRiftTentacleModel.VISUAL_ID,
                        fallbackAnimation == null || fallbackAnimation.isBlank()
                                ? "READY" : fallbackAnimation,
                        nowMillis, 0L,
                        entityUuid.getMostSignificantBits() ^ entityUuid.getLeastSignificantBits());
            }
            if (pose == null || !pose.isFinite()) {
                continue;
            }
            Vec3d position = entity.getLerpedPos(tickDelta);
            if (position == null || !finite(position)) {
                continue;
            }
            String animation = ClientBridgeProtocol.endEventAnimationForEntity(uuid);
            boolean hurtFlash = showsHurtFlash(animation);
            VertexConsumer buffer = consumers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE));
            matrices.push();
            matrices.translate(position.x, position.y, position.z);
            Entity target = targetEntity(context, uuid);
            Vec3d targetPosition = target == null ? null : target.getLerpedPos(tickDelta);
            float facingYaw = facingYaw(entityUuid, animation, position, targetPosition, nowMillis);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotation(facingYaw));
            matrices.scale(EndRiftTentacleWorldScalePolicy.rendererScaleForRig(pose.rigScale()),
                    EndRiftTentacleWorldScalePolicy.rendererLengthScaleForRig(pose.rigScale()),
                    EndRiftTentacleWorldScalePolicy.rendererScaleForRig(pose.rigScale()));
            int packedLight = WorldRenderer.getLightmapCoordinates(
                    (ClientWorld) context.world(), entity.getBlockPos());
            // The hit-recovery phase lasts only for the hurt animation. Use
            // Minecraft's hurt overlay for that window; health thresholds
            // persist between hits and must not tint the rig indefinitely.
            RIG.render(matrices, buffer, pose, packedLight,
                    OverlayTexture.getUv(0.0F, hurtFlash), 0xFFFFFFFF);
            matrices.pop();
        }
        matrices.pop();
    }

    private static Set<UUID> candidateEntityIds(ClientWorld world) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (String uuidValue : ClientBridgeProtocol.endEventVisualEntityIds()) {
            try {
                ids.add(UUID.fromString(uuidValue));
            } catch (IllegalArgumentException ignored) {
                // A malformed bridge entry must not prevent the item scan.
            }
        }
        for (Entity entity : world.getEntities()) {
            if (entity instanceof DisplayEntity.ItemDisplayEntity display
                    && !entity.isRemoved() && entity.getUuid() != null
                    && isTentacleCarrier(display)) {
                ids.add(entity.getUuid());
            }
        }
        return ids;
    }

    private static boolean finite(Vec3d value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    private static Entity targetEntity(WorldRenderContext context, String tentacleUuid) {
        String targetUuid = ClientBridgeProtocol.endEventTentacleTargetForEntity(tentacleUuid);
        if (targetUuid == null || targetUuid.isBlank() || context == null || context.world() == null) {
            return null;
        }
        try {
            Entity target = ((ClientWorldAccessor) context.world())
                    .copimine$getEntityLookup().get(UUID.fromString(targetUuid));
            return target instanceof Entity candidate && !candidate.isRemoved() ? candidate : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static float targetYaw(Vec3d origin, Vec3d target) {
        if (origin == null || target == null || !finite(origin) || !finite(target)) {
            return 0.0F;
        }
        return (float) Math.atan2(target.x - origin.x, target.z - origin.z);
    }

    static boolean showsHurtFlash(String animation) {
        return "HIT_RECOVERY".equalsIgnoreCase(normalize(animation));
    }

    private static float facingYaw(UUID entityUuid, String animation, Vec3d origin,
                                   Vec3d target, long nowMillis) {
        FacingState previous = FACING_BY_ENTITY.get(entityUuid);
        float currentYaw = previous == null ? 0.0F : previous.yaw();
        long elapsedMillis = previous == null ? 0L : Math.max(0L, nowMillis - previous.updatedAtMillis());
        if (EndRiftTentacleFacingPolicy.tracksTarget(animation)
                && target != null && finite(origin) && finite(target)) {
            float targetYaw = targetYaw(origin, target);
            currentYaw = previous == null ? targetYaw
                    : EndRiftTentacleFacingPolicy.advanceYaw(currentYaw, targetYaw, elapsedMillis);
        }
        FACING_BY_ENTITY.put(entityUuid, new FacingState(currentYaw, nowMillis));
        return currentYaw;
    }

    private record FacingState(float yaw, long updatedAtMillis) {
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
