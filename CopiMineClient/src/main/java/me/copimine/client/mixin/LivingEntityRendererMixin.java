package me.copimine.client.mixin;

import me.copimine.client.ClientBridgeProtocol;
import me.copimine.client.EchoVanillaRenderer;
import me.copimine.client.CopiMineClientLogger;
import me.copimine.client.EndRiftBossWorldScalePolicy;
import me.copimine.client.EndEventTextureCatalog;
import me.copimine.client.EndermanRendererSelection;
import me.copimine.client.RiftEventEndermanModelRenderer;
import me.copimine.client.RiftEventSkeletonModelRenderer;
import me.copimine.client.RiftGuardianModelRenderer;
import me.copimine.client.RiftSpiderModelRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.SpiderEntityModel;
import net.minecraft.client.render.entity.model.SkeletonEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.util.Identifier;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LivingEntityRenderer owns the shared render method used by the event
 * skeletons and spiders. Their concrete renderers mostly provide texture and
 * pose details, so model selection is applied at this shared boundary.
 *
 * <p>The vanilla renderer model is never replaced.  The field reads emitted
 * by {@code render} are redirected to a scoped model while the current call
 * is running.  This matters because an exception in a feature renderer can
 * skip a RETURN injection; leaving the shared renderer field mutated would
 * then leak the event model into later vanilla renders.</p>
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, M extends EntityModel<T>> {
    @Shadow
    public abstract M getModel();

    /**
     * Feature renderers (notably the skeleton held-item renderer) obtain the
     * model through FeatureRendererContext.getModel(), outside the bytecode
     * covered by the render-field redirect below. Return the same scoped
     * model there so the bow is attached to the custom articulated hand.
     */
    @Inject(method = "getModel", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings("unchecked")
    private void copimine$returnScopedEventModel(CallbackInfoReturnable<M> cir) {
        EntityModel<?> active = copimine$activeModel;
        if (active != null) {
            cir.setReturnValue((M) active);
        }
    }

    @Unique
    private final RiftSpiderModelRenderer copimine$spiderRenderer = new RiftSpiderModelRenderer();
    @Unique
    private final RiftGuardianModelRenderer copimine$guardianRenderer = new RiftGuardianModelRenderer();
    @Unique
    private final RiftEventEndermanModelRenderer copimine$eventRenderer = new RiftEventEndermanModelRenderer();
    @Unique
    private final RiftEventSkeletonModelRenderer copimine$skeletonRenderer = new RiftEventSkeletonModelRenderer();
    @Unique
    private EntityModel<?> copimine$activeModel;
    @Unique
    private static final Set<String> copimine$loggedGeometryVisuals = ConcurrentHashMap.newKeySet();

    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("HEAD"), cancellable = true)
    private void copimine$selectEventModel(LivingEntity entity, float yaw, float tickDelta,
                                                    MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                    int light, CallbackInfo ci) {
        // A previous render may have failed before its RETURN callback.  The
        // vanilla field was not changed, so clearing this scoped selector is
        // sufficient to make the next invocation fail closed.
        copimine$activeModel = null;
        if (entity != null && EchoVanillaRenderer.render(entity, yaw, tickDelta, matrices, vertexConsumers, light)) {
            ci.cancel();
            return;
        }
        if (entity != null && entity.getUuid() != null
                && "END_RIFT_TENTACLE_HITBOX_V1".equals(
                ClientBridgeProtocol.endEventVisualForEntity(entity.getUuid().toString()))) {
            // This living entity exists so Jade, melee and projectile raycasts
            // have a real health-bearing target. The ItemDisplay renders the
            // tentacle mesh, so never draw the Giant hitbox model itself.
            ci.cancel();
            return;
        }
        if (entity instanceof AbstractSkeletonEntity skeleton
                && getModel() instanceof SkeletonEntityModel<?>) {
            copimine$selectSkeletonModel(skeleton);
            return;
        }
        if (entity instanceof EndermanEntity enderman
                && getModel() instanceof net.minecraft.client.render.entity.model.EndermanEntityModel<?>) {
            copimine$selectEndermanModel(enderman);
            return;
        }
        if (!(entity instanceof SpiderEntity spider)
                || !(getModel() instanceof SpiderEntityModel<?>)) {
            return;
        }
        String uuid = spider.getUuid().toString();
        String visual = ClientBridgeProtocol.endEventVisualForEntity(uuid);
        var texture = EndEventTextureCatalog.textureForVisual(visual);
        // This is a spider visual, not a Guardian selection.  Do not route it
        // through the Enderman/boss decision object: doing so reports the
        // wrong model and animation metadata even though the final geometry is
        // a spider.
        EntityModel<?> model = copimine$spiderRenderer.modelFor(visual);
        if (model == null) {
            copimine$logGeometrySelection("spider", visual, null);
            return;
        }
        EndEventTextureCatalog.logLookup("spider", texture);
        copimine$activeModel = model;
        copimine$logGeometrySelection("spider", visual, model);
    }

    @Redirect(
            method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;model:Lnet/minecraft/client/render/entity/model/EntityModel;",
                    opcode = Opcodes.GETFIELD))
    private EntityModel<?> copimine$readScopedEventModel(LivingEntityRenderer<?, ?> renderer) {
        EntityModel<?> active = copimine$activeModel;
        return active == null ? renderer.getModel() : active;
    }

    @Inject(method = "scale(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/util/math/MatrixStack;F)V",
            at = @At("HEAD"), cancellable = true)
    private void copimine$scaleImportedGuardian(LivingEntity entity, MatrixStack matrices,
                                                 float amount, CallbackInfo ci) {
        if (entity instanceof EndermanEntity
                && ClientBridgeProtocol.isBoundEndBoss(entity.getUuid().toString())) {
            float scale = EndRiftBossWorldScalePolicy.renderScale();
            // This cancellable HEAD hook replaces the vanilla scale method;
            // applying an extra origin correction here lifts the imported
            // feet off the server entity's floor position.
            matrices.scale(scale, scale, scale);
            ci.cancel();
        }
    }

    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("RETURN"))
    private void copimine$clearScopedEventModel(LivingEntity entity, float yaw, float tickDelta,
                                                  MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                  int light, CallbackInfo ci) {
        copimine$activeModel = null;
    }

    @Unique
    private void copimine$selectEndermanModel(EndermanEntity entity) {
        String entityUuid = entity.getUuid().toString();
        EndermanRendererSelection.Decision selection;
        if (ClientBridgeProtocol.isBoundEndBoss(entityUuid)) {
            Identifier guardianTexture = copimine$guardianRenderer.textureForState(
                    ClientBridgeProtocol.bossPhaseForEntity(entityUuid),
                    ClientBridgeProtocol.bossAnimationForEntity(entityUuid));
            EndEventTextureCatalog.logLookup("boss", guardianTexture);
            selection = EndermanRendererSelection.select(
                    entityUuid,
                    entityUuid,
                    guardianTexture,
                    EndEventTextureCatalog.isAvailable(guardianTexture));
        } else {
            String visual = ClientBridgeProtocol.endEventVisualForEntity(entityUuid);
            Identifier eventTexture = EndEventTextureCatalog.textureForVisual(visual);
            selection = EndermanRendererSelection.selectVisual(entityUuid, visual, null,
                    eventTexture, EndEventTextureCatalog.isAvailable(eventTexture));
        }
        if (!selection.usesCustomModel()) {
            return;
        }

        EntityModel<?> customModel;
        if (selection.kind() == EndermanRendererSelection.Kind.GUARDIAN) {
            String phaseId = ClientBridgeProtocol.bossPhaseForEntity(entityUuid);
            long transitionMillis = ClientBridgeProtocol.bossPhaseTransitionMillisForEntity(entityUuid);
            String animationId = ClientBridgeProtocol.bossAnimationForEntity(entityUuid);
            float animationElapsedTicks = ClientBridgeProtocol.bossAnimationElapsedTicksForEntity(
                    entityUuid, System.currentTimeMillis());
            customModel = copimine$guardianRenderer.modelForPhase(
                    phaseId, transitionMillis, animationId, animationElapsedTicks);
        } else {
            customModel = copimine$eventRenderer.modelFor(selection.kind());
        }
        if (customModel != null) {
            copimine$activeModel = customModel;
            copimine$logGeometrySelection("enderman", selection.modelId(), customModel);
        }
    }

    @Unique
    private void copimine$selectSkeletonModel(AbstractSkeletonEntity entity) {
        String visual = ClientBridgeProtocol.endEventVisualForEntity(entity.getUuid().toString());
        Identifier texture = EndEventTextureCatalog.textureForVisual(visual);
        EntityModel<?> model = copimine$skeletonRenderer.modelFor(visual);
        if (model == null) {
            copimine$logGeometrySelection("skeleton", visual, null);
            return;
        }
        EndEventTextureCatalog.logLookup("skeleton", texture);
        copimine$activeModel = model;
        copimine$logGeometrySelection("skeleton", visual, model);
    }

    @Unique
    private static void copimine$logGeometrySelection(String entityType, String visual,
                                                       EntityModel<?> model) {
        String normalized = visual == null ? "" : visual.trim().toUpperCase(Locale.ROOT);
        if (!normalized.startsWith("END_RIFT_")) {
            return;
        }
        String key = entityType + "|" + normalized;
        if (copimine$loggedGeometryVisuals.add(key)) {
            CopiMineClientLogger.info("End Rift geometry " + (model == null ? "missing" : "selected")
                    + " entityType=" + entityType + " visual=" + normalized
                    + " model=" + (model == null ? "none" : model.getClass().getSimpleName()));
        }
    }
}
