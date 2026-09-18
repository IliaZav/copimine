package me.copimine.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import me.copimine.client.mixin.ClientWorldAccessor;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Dedicated renderer for the event-owned tentacle carrier displays. The
 * carrier remains a real server entity for visibility and lifecycle, while
 * the model is rendered as a cached, articulated five-segment rig with four
 * independently animated claws.
 */
public final class EndRiftTentacleRenderer {
    private static final Identifier TEXTURE = Identifier.of(
            "copimineclient", "textures/entity/end_rift_tentacle_hd.png");
    private static final EndRiftTentacleRig.RenderRig RIG = EndRiftTentacleRig.createRenderRig();
    private static final int FULL_BRIGHT_LIGHT = 0x00F000F0;

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
        long safeDurationMillis = durationMillis > 0L ? durationMillis : durationTicks * 50L;
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
            String healthState = ClientBridgeProtocol.endEventTentacleHealthForEntity(uuid);
            VertexConsumer buffer = consumers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE));
            matrices.push();
            matrices.translate(position.x, position.y, position.z);
            Entity target = targetEntity(context, uuid);
            Vec3d targetPosition = target == null ? null : target.getLerpedPos(tickDelta);
            if (targetPosition != null && finite(targetPosition)) {
                // The rig's authored forward axis is +Z. Rotate the whole
                // articulated chain toward the server-selected target; all
                // bone animation remains local and the server still owns the
                // actual hit/lock decision.
                matrices.multiply(RotationAxis.POSITIVE_Y.rotation(
                        targetYaw(position, targetPosition)));
            }
            matrices.scale(pose.rigScale() / 16.0F,
                    pose.rigScale() / 16.0F, pose.rigScale() / 16.0F);
            RIG.render(matrices, buffer, pose, FULL_BRIGHT_LIGHT, 0,
                    tintForHealthState(healthState));
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

    private static int tintForHealthState(String healthState) {
        return switch (normalize(healthState)) {
            case "DAMAGED" -> 0xFFE1B8FF;
            case "CRITICAL" -> 0xFFFF78D8;
            case "DEAD" -> 0x986A5A8F;
            default -> 0xFFFFFFFF;
        };
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
