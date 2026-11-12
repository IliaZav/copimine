package me.copimine.client;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import me.copimine.protocol.KaguneRingFrame;
import java.util.ArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.phys.Vec3;
import me.copimine.client.mixin.ClientWorldAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;

/**
 * Dedicated renderer for the event-owned tentacle carrier displays. The
 * carrier remains a real server entity for visibility and lifecycle, while
 * the supplied Kagune mesh is rendered with its six imported pieces, UVs,
 * hierarchy, and animation clips.
 */
public final class EndRiftTentacleRenderer {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            "copimineclient", "textures/entity/end_rift_tentacle_hd.png");
    private static final EndRiftTentacleRig.RenderRig RIG = EndRiftTentacleRig.createRenderRig();
    private static final Map<UUID, FacingState> FACING_BY_ENTITY = new HashMap<>();
    private static ClientLevel candidateWorld;
    private static long candidateGameTime = Long.MIN_VALUE;
    private static List<UUID> cachedCandidateIds = List.of();

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
    public static boolean isTentacleCarrier(Display entity) {
        if (!(entity instanceof Display.ItemDisplay)) return false;
        if (entity.getUUID() != null
                && ClientBridgeProtocol.isEndEventEntityRenderSuppressed(entity.getUUID().toString())) return false;
        return isBridgeBound(entity) || hasTentacleMarker(entity);
    }

    private static boolean isBridgeBound(Display entity) {
        return entity != null && entity.getUUID() != null
                && EndRiftTentacleModel.VISUAL_ID.equals(
                        ClientBridgeProtocol.endEventVisualForEntity(entity.getUUID().toString()));
    }

    private static boolean hasTentacleMarker(Display entity) {
        if (!(entity instanceof Display.ItemDisplay itemDisplay)) return false;
        if (entity.getUUID() != null
                && ClientBridgeProtocol.isEndEventEntityRenderSuppressed(entity.getUUID().toString())) return false;
        ItemStack stack = itemDisplay.getItemStack();
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CustomModelData customModelData = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return customModelData != null
                && EndRiftTentacleModel.isServerCustomModelData(
                        EndRiftItemModelData.firstFloatAsExactInteger(customModelData));
    }

    /** Whether the vanilla carrier is safe to hide for this entity. */
    public static boolean isCustomRenderEligible(Display entity) {
        if (entity == null || entity.getUUID() == null || !isTentacleCarrier(entity)) return false;
        return candidateEntityIds(Minecraft.getInstance().level).contains(entity.getUUID());
    }

    /** Draw all visible event tentacle carriers in one bounded world pass. */
    public static void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (context == null || client.level == null) return;
        ClientLevel world = client.level;
        Camera camera = client.gameRenderer.mainCamera();
        if (camera == null || !camera.isInitialized()) return;
        Vec3 cameraPos = camera.position();
        float tickDelta = context.levelState().worldPartialTicks;
        long nowMillis = System.currentTimeMillis();
        List<UUID> candidateIds = candidateEntityIds(world);
        FACING_BY_ENTITY.keySet().retainAll(candidateIds);
        if (candidateIds.isEmpty()) return;
        EndRiftRenderSubmission.submit(context, cameraPos, RenderTypes.entityTranslucent(TEXTURE), (matrices, buffer) -> {
            for (UUID entityUuid : candidateIds) {
                Entity entity = ((ClientWorldAccessor) world).copimine$getEntities().get(entityUuid);
                if (!(entity instanceof Display.ItemDisplay display)
                        || entity.isRemoved() || entity.getUUID() == null || !isTentacleCarrier(display)) continue;
                if (isRingCarrier(display)) {
                    KaguneRingFrame frame = ringFrame(display);
                    if (frame != null) renderRing(matrices, buffer, display, frame, tickDelta, world);
                    continue;
                }
                String uuid = entityUuid.toString();
                EndRiftTentaclePose.TentaclePose pose = ClientBridgeProtocol.endEventTentaclePoseAt(uuid, nowMillis);
                if (pose == null) {
                    String fallbackAnimation = ClientBridgeProtocol.endEventAnimationForEntity(uuid);
                    pose = poseForAt(EndRiftTentacleModel.VISUAL_ID,
                            fallbackAnimation == null || fallbackAnimation.isBlank() ? "READY" : fallbackAnimation,
                            nowMillis, 0L,
                            entityUuid.getMostSignificantBits() ^ entityUuid.getLeastSignificantBits());
                }
                if (pose == null || !pose.isFinite()) continue;
                Vec3 position = entity.getPosition(tickDelta);
                if (position == null || !finite(position)) continue;
                String animation = ClientBridgeProtocol.endEventAnimationForEntity(uuid);
                boolean hurtFlash = showsHurtFlash(animation)
                        || ClientBridgeProtocol.endEventState().tentacleHurtFlashAt(uuid, nowMillis);
                matrices.pushPose();
                matrices.translate(position.x, position.y, position.z);
                Entity target = targetEntity(world, uuid);
                Vec3 targetPosition = target == null ? null : target.getPosition(tickDelta);
                matrices.rotate(Axis.YP, facingYaw(entityUuid, animation, position, targetPosition, nowMillis));
                matrices.scale(EndRiftTentacleWorldScalePolicy.rendererScaleForRig(pose.rigScale()),
                        EndRiftTentacleWorldScalePolicy.rendererLengthScaleForRig(pose.rigScale()),
                        EndRiftTentacleWorldScalePolicy.rendererScaleForRig(pose.rigScale()));
                int packedLight = net.minecraft.util.LightCoordsUtil.getLightCoords(world, entity.blockPosition());
                RIG.render(matrices, buffer, pose, packedLight,
                        OverlayTexture.pack(0.0F, hurtFlash), tintForRole(
                                ClientBridgeProtocol.endEventState().tentacleRoleForEntity(uuid), hurtFlash));
                matrices.popPose();
            }
        });
    }

    private static boolean isRingCarrier(Display.ItemDisplay display) {
        CustomModelData data = display.getItemStack().get(DataComponents.CUSTOM_MODEL_DATA);
        Float marker = data == null ? null : data.getFloat(1);
        return marker != null && marker == KaguneRingFrame.MARKER;
    }

    private static KaguneRingFrame ringFrame(Display.ItemDisplay display) {
        CustomModelData data = display.getItemStack().get(DataComponents.CUSTOM_MODEL_DATA);
        Float count = data == null ? null : data.getFloat(8);
        if (count == null || !Float.isFinite(count) || count < 2 || count > 7 || count != Math.rint(count)) return null;
        List<Float> values = new ArrayList<>(9 + count.intValue());
        for (int i = 0; i < 9 + count.intValue(); i++) {
            Float value = data.getFloat(i);
            if (value == null) return null;
            values.add(value);
        }
        return KaguneRingFrame.decode(values);
    }

    /** All repeated rigs share one geometry submission, skin and sampled pose. */
    private static void renderRing(PoseStack matrices, VertexConsumer vertices, Display.ItemDisplay display,
                                   KaguneRingFrame frame, float partialTick, ClientLevel world) {
        if (frame.rise() <= 0D) return;
        Vec3 origin = display.getPosition(partialTick);
        if (!finite(origin)) return;
        float progress = (float) frame.strike();
        var upright = EndRiftTentacleAnimator.poseFor("EMERGE", 1F, 0L);
        var strike = EndRiftTentacleAnimator.poseFor("THROW", progress, 0L);
        EndRiftTentaclePose.TentaclePose pose;
        if (frame.stage() == KaguneRingFrame.EMERGE) {
            pose = EndRiftTentacleAnimator.poseFor("EMERGE", (float) frame.rise(), 0L);
        } else if (frame.stage() == KaguneRingFrame.TRAVEL) {
            pose = EndRiftTentaclePose.TentaclePose.lerp(upright, strike, Math.min(1F, progress * 5F));
        } else {
            float retract = (float) (1D - frame.rise());
            pose = EndRiftTentaclePose.TentaclePose.lerp(
                    EndRiftTentacleAnimator.poseFor("THROW", 1F, 0L),
                    EndRiftTentacleAnimator.poseFor("RETRACT", retract, 0L), Math.min(1F, retract * 5F));
        }
        float width = EndRiftTentacleWorldScalePolicy.rendererScaleForRig((float) KaguneRingFrame.RIG_SCALE);
        float height = EndRiftTentacleWorldScalePolicy.rendererLengthScaleForRig((float) KaguneRingFrame.RIG_SCALE);
        double radius = frame.previousRadius() + (frame.radius() - frame.previousRadius()) * Math.max(0F, Math.min(1F, partialTick));
        int light = net.minecraft.util.LightCoordsUtil.getLightCoords(world, display.blockPosition());
        for (int i = 0; i < frame.instances(); i++) {
            if (!frame.drawsInstance(i)) continue;
            double angle = frame.direction(i);
            matrices.pushPose();
            matrices.translate(origin.x + Math.cos(angle) * radius, origin.y, origin.z + Math.sin(angle) * radius);
            matrices.rotate(Axis.YP, (float) Math.toDegrees(-angle + Math.PI / 2D));
            matrices.scale(width, height, width);
            RIG.render(matrices, vertices, pose, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
            matrices.popPose();
        }
    }

    private static List<UUID> candidateEntityIds(ClientLevel world) {
        if (world == null) {
            candidateWorld = null;
            candidateGameTime = Long.MIN_VALUE;
            cachedCandidateIds = List.of();
            return cachedCandidateIds;
        }
        long gameTime = world.getGameTime();
        if (candidateWorld == world && candidateGameTime == gameTime) return cachedCandidateIds;

        LinkedHashSet<UUID> ids = new LinkedHashSet<>(EndRiftTentacleCandidatePolicy.MAX_CANDIDATES);
        var entityLookup = ((ClientWorldAccessor) world).copimine$getEntities();
        for (String uuidValue : ClientBridgeProtocol.endEventVisualEntityIds()) {
            if (EndRiftTentacleCandidatePolicy.isFull(ids)) break;
            try {
                UUID uuid = UUID.fromString(uuidValue);
                Entity entity = entityLookup.get(uuid);
                if (entity instanceof Display.ItemDisplay display && !entity.isRemoved()) {
                    EndRiftTentacleCandidatePolicy.admit(ids, uuid,
                            isBridgeBound(display), hasTentacleMarker(display));
                }
            } catch (IllegalArgumentException ignored) {
                // A malformed bridge entry must not prevent the item scan.
            }
        }
        if (!EndRiftTentacleCandidatePolicy.isFull(ids)) {
            int inspectedCount = 0;
            for (Entity entity : world.entitiesForRendering()) {
                if (!EndRiftTentacleCandidatePolicy.canInspectEntity(inspectedCount)) break;
                inspectedCount++;
                if (!(entity instanceof Display.ItemDisplay display)
                        || entity.isRemoved() || entity.getUUID() == null) continue;
                boolean bridgeBound = isBridgeBound(display);
                boolean markerPresent = hasTentacleMarker(display);
                if (EndRiftTentacleCandidatePolicy.admit(ids, entity.getUUID(), bridgeBound, markerPresent)
                        && EndRiftTentacleCandidatePolicy.isFull(ids)) {
                    break;
                }
            }
        }
        candidateWorld = world;
        candidateGameTime = gameTime;
        cachedCandidateIds = EndRiftTentacleCandidatePolicy.snapshot(ids);
        return cachedCandidateIds;
    }

    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    private static Entity targetEntity(ClientLevel world, String tentacleUuid) {
        String targetUuid = ClientBridgeProtocol.endEventTentacleTargetForEntity(tentacleUuid);
        if (targetUuid == null || targetUuid.isBlank() || world == null) {
            return null;
        }
        try {
            Entity target = ((ClientWorldAccessor) world)
                    .copimine$getEntities().get(UUID.fromString(targetUuid));
            return target instanceof Entity candidate && !candidate.isRemoved() ? candidate : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static float targetYaw(Vec3 origin, Vec3 target) {
        if (origin == null || target == null || !finite(origin) || !finite(target)) {
            return 0.0F;
        }
        return (float) Math.atan2(target.x - origin.x, target.z - origin.z);
    }

    static int tintForRole(String role, boolean hurt) {
        if (hurt) return 0xFFFF6868;
        return switch (role == null ? "" : role) {
            case "UNDER_PLAYER" -> 0xFFFFA080;
            case "TEMPORARY" -> 0xFFD0B0FF;
            case "SMALL" -> 0xFFA0D8FF;
            default -> 0xFFFFFFFF;
        };
    }

    static boolean showsHurtFlash(String animation) {
        return "HIT_RECOVERY".equalsIgnoreCase(normalize(animation));
    }

    private static float facingYaw(UUID entityUuid, String animation, Vec3 origin,
                                   Vec3 target, long nowMillis) {
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
