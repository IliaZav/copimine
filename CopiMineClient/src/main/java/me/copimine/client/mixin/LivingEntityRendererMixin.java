package me.copimine.client.mixin;

import me.copimine.client.ClientBridgeProtocol;
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

    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("HEAD"))
    private void copimine$selectEventModel(LivingEntity entity, float yaw, float tickDelta,
                                                    MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                    int light, CallbackInfo ci) {
        // A previous render may have failed before its RETURN callback.  The
        // vanilla field was not changed, so clearing this scoped selector is
        // sufficient to make the next invocation fail closed.
        copimine$activeModel = null;
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
        if (!isEventSpiderVisual(visual)
                || !EndEventTextureCatalog.isAvailable(texture)) {
            return;
        }
        copimine$activeModel = copimine$spiderRenderer.modelFor(visual);
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
        }
    }

    @Unique
    private void copimine$selectSkeletonModel(AbstractSkeletonEntity entity) {
        String visual = ClientBridgeProtocol.endEventVisualForEntity(entity.getUuid().toString());
        Identifier texture = EndEventTextureCatalog.textureForVisual(visual);
        if (!isEventSkeletonVisual(visual) || !EndEventTextureCatalog.isAvailable(texture)) {
            return;
        }
        copimine$activeModel = copimine$skeletonRenderer.modelFor(visual);
    }

    @Unique
    private static boolean isEventSpiderVisual(String visual) {
        return "END_RIFT_SPIDER_V1".equals(visual)
                || "END_RIFT_ELITE_SPIDER_V1".equals(visual)
                || "END_RIFT_WAVE_GUARDIAN_SPIDER_V1".equals(visual)
                || "END_RIFT_RITUAL_GUARD_SPIDER_V1".equals(visual);
    }

    @Unique
    private static boolean isEventSkeletonVisual(String visual) {
        return "END_RIFT_SKELETON_V1".equals(visual)
                || "END_RIFT_ELITE_SKELETON_V1".equals(visual)
                || "END_RIFT_WAVE_GUARDIAN_SKELETON_V1".equals(visual)
                || "END_RIFT_RITUAL_GUARD_SKELETON_V1".equals(visual);
    }
}
