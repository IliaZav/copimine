package me.copimine.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Renders local-only grey Enderman phantoms while the local player is marked in Wave 2. */
public final class WaveTwoPhantomRenderer {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            "copimineclient", "textures/entity/end_rift_enderman.png");
    private static final int PHANTOM_TINT = 0x99B0B0B0;
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final int MAX_RENDER_DISTANCE_SQUARED = 64 * 64;

    private static final RiftEventEndermanModel MODEL = createModel();
    private static final EndermanRenderState MODEL_STATE = stationaryModelState();
    private static ClientLevel currentWorld;

    private WaveTwoPhantomRenderer() { }

    /** Draws translucent phantoms without creating or registering any entity. */
    public static void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (context == null || client.level == null || client.player == null
                || client.player.isDeadOrDying()) {
            clear();
            return;
        }

        ClientLevel world = client.level;
        if (currentWorld != world) {
            clearActivationPresentation();
            currentWorld = world;
        }

        EndEventPlayerVisualManager manager = ClientBridgeProtocol.endEventPlayerVisuals();
        if (manager == null) {
            clearActivationPresentation();
            return;
        }
        long nowMillis = System.currentTimeMillis();
        String dimension = world.dimension().identifier().getPath();
        EndEventPlayerVisualManager.VisualState visual = manager.visualState(dimension, nowMillis);
        if (visual == null || visual.kind() != EndEventPlayerVisualManager.Kind.HUNT) {
            clearActivationPresentation();
            return;
        }

        WaveTwoPhantomLayoutManager layoutManager = ClientBridgeProtocol.waveTwoPhantomLayouts();
        int layoutCycle = layoutManager.cycle(dimension, manager.eventId(), manager.generation());
        if (layoutCycle < 1 || !layoutManager.isComplete(
                dimension, manager.eventId(), manager.generation(), layoutCycle)) {
            return;
        }
        List<WaveTwoPhantomLayoutManager.Phantom> phantoms = layoutManager.layout(
                dimension, manager.eventId(), manager.generation(), layoutCycle);

        float tickDelta = context.levelState().worldPartialTicks;
        Vec3 playerPosition = client.player.getPosition(tickDelta);

        WaveTwoPhantomActivationManager activationManager = ClientBridgeProtocol.waveTwoPhantomActivations();
        WaveTwoPhantomActivationManager.Activation activation = activationManager.active(dimension, nowMillis);
        int activationIndex = -1;
        if (activation != null && activation.eventId().equals(manager.eventId())
                && activation.generation() == manager.generation()
                && activation.cycle() == layoutCycle
                && activation.acceptedAtMillis() >= visual.startedAtMillis()) {
            for (int index = 0; index < phantoms.size(); index++) {
                WaveTwoPhantomLayoutManager.Phantom phantom = phantoms.get(index);
                if (phantom.id().equals(activation.phantomId())
                        && phantom.position().equals(activation.position())) {
                    activationIndex = index;
                    break;
                }
            }
        } else if (activation != null) {
            activationManager.clearPresentation();
        }
        if (activationIndex < 0) activation = null;
        int selectedActivationIndex = activationIndex;
        Set<String> materializedPhantomIds = activationManager.usedPhantomIds(
                dimension, manager.eventId(), manager.generation(), layoutCycle);

        var camera = client.gameRenderer.mainCamera();
        if (camera == null || !camera.isInitialized()) return;
        Vec3 cameraPosition = camera.position();
        WaveTwoPhantomLayout.Position target = new WaveTwoPhantomLayout.Position(
                playerPosition.x, playerPosition.y + client.player.getBbHeight() * 0.82D,
                playerPosition.z);
        EndRiftRenderSubmission.submit(context, cameraPosition,
                RenderTypes.entityTranslucent(TEXTURE), (matrices, buffer) -> {
                    for (int index = 0; index < phantoms.size(); index++) {
                        WaveTwoPhantomLayoutManager.Phantom phantom = phantoms.get(index);
                        if (isRetiredPhantom(index, selectedActivationIndex, phantom.id(),
                                materializedPhantomIds)) continue;
                        WaveTwoPhantomLayout.Position position = phantom.position();
                        Vec3 phantomPosition = new Vec3(position.x(), position.y(), position.z());
                        if (cameraPosition.distanceToSqr(phantomPosition) > MAX_RENDER_DISTANCE_SQUARED) continue;
                        WaveTwoPhantomLayout.LookPose lookPose = WaveTwoPhantomLayout.lookAt(position, target);
                        applyHeadLook(MODEL_STATE, lookPose);
                        matrices.pushPose();
                        // Match LivingEntityRenderer's feet-anchored model transform:
                        // keep the body at its fixed forward yaw, invert model Y, and offset by
                        // 1.501 blocks before drawing. setupAnim applies lookPose only to the head.
                        matrices.translate(position.x(), position.y() + 1.501D, position.z());
                        matrices.rotateDegrees(Axis.YP, 180.0F - lookPose.bodyYawDegrees());
                        matrices.scale(-1.0F, -1.0F, 1.0F);
                        MODEL_STATE.isCreepy = index == selectedActivationIndex;
                        MODEL.setupAnim(MODEL_STATE);
                        MODEL.renderToBuffer(matrices, buffer, FULL_BRIGHT,
                                OverlayTexture.NO_OVERLAY, PHANTOM_TINT);
                        matrices.popPose();
                    }
                    MODEL_STATE.isCreepy = false;
                });
    }

    /** A materialized phantom is replaced by its real entity, except during its own warning. */
    static boolean isRetiredPhantom(int index, int selectedActivationIndex, String phantomId,
                                    Set<String> materializedPhantomIds) {
        return index != selectedActivationIndex && materializedPhantomIds.contains(phantomId);
    }

    static void applyHeadLook(EndermanRenderState state, WaveTwoPhantomLayout.LookPose lookPose) {
        state.yRot = lookPose.headYawDegrees();
        state.xRot = 0.0F;
    }

    private static RiftEventEndermanModel createModel() {
        return new RiftEventEndermanModel(
                RiftEventEndermanModel.getTexturedModelData(
                        RiftEventEndermanModel.Variant.ORDINARY).bakeRoot(),
                RiftEventEndermanModel.Variant.ORDINARY);
    }

    private static EndermanRenderState stationaryModelState() {
        EndermanRenderState state = new EndermanRenderState();
        state.ageInTicks = 0.0F;
        state.isCreepy = false;
        state.walkAnimationPos = 0.0F;
        state.walkAnimationSpeed = 0.0F;
        return state;
    }

    private static void clear() {
        clearActivationPresentation();
        currentWorld = null;
    }

    private static void clearActivationPresentation() {
        ClientBridgeProtocol.waveTwoPhantomActivations().clearPresentation();
        ClientBridgeProtocol.waveTwoPhantomLayouts().clearPresentation();
    }
}
