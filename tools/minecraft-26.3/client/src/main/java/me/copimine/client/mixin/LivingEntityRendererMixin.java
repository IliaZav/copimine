package me.copimine.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import me.copimine.client.ClientBridgeProtocol;
import me.copimine.client.EchoVanillaRenderer;
import me.copimine.client.EndEventTextureCatalog;
import me.copimine.client.EndRiftBossWorldScalePolicy;
import me.copimine.client.EndRiftRenderStateAccess;
import me.copimine.client.EndermanRendererSelection;
import me.copimine.client.EchoPlayerView;
import me.copimine.client.RiftEventEndermanModelRenderer;
import me.copimine.client.RiftEventSkeletonModelRenderer;
import me.copimine.client.RiftGuardianModel;
import me.copimine.client.RiftGuardianModelRenderer;
import me.copimine.client.RiftSpiderModelRenderer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.monster.enderman.EndermanModel;
import net.minecraft.client.model.monster.skeleton.SkeletonModel;
import net.minecraft.client.model.monster.spider.SpiderModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies server-bound event models at the shared 26.3 entity submission boundary. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity,
        S extends LivingEntityRenderState, M extends EntityModel<? super S>> {
    @Shadow @Final protected M model;
    @Shadow public abstract M getModel();

    @Unique private final RiftSpiderModelRenderer copimine$spiderRenderer = new RiftSpiderModelRenderer();
    @Unique private final RiftGuardianModelRenderer copimine$guardianRenderer = new RiftGuardianModelRenderer();
    @Unique private final RiftEventEndermanModelRenderer copimine$eventRenderer = new RiftEventEndermanModelRenderer();
    @Unique private final RiftEventSkeletonModelRenderer copimine$skeletonRenderer = new RiftEventSkeletonModelRenderer();
    @Unique private EntityModel<?> copimine$activeModel;

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void copimine$bindEventRenderState(T entity, S state, float partialTick,
                                               CallbackInfo ci) {
        if (!(state instanceof EndRiftRenderStateAccess bound)) return;
        String uuid = entity.getUUID().toString();
        bound.copimine$bindRenderState(uuid, entity.getId(), entity instanceof EchoPlayerView,
                ClientBridgeProtocol.endEventVisualForEntity(uuid),
                ClientBridgeProtocol.endEventWavePoseForEntity(uuid),
                ClientBridgeProtocol.endEventAnimationForEntity(uuid),
                ClientBridgeProtocol.isBoundEndBoss(uuid),
                ClientBridgeProtocol.bossPhaseForEntity(uuid),
                ClientBridgeProtocol.bossPhaseTransitionMillisForEntity(uuid),
                ClientBridgeProtocol.bossAnimationForEntity(uuid));
    }

    @Inject(method = "getModel", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings("unchecked")
    private void copimine$scopedLayerModel(CallbackInfoReturnable<M> cir) {
        if (copimine$activeModel != null) cir.setReturnValue((M) copimine$activeModel);
    }

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void copimine$selectBoundRenderModel(S state, PoseStack matrices,
                                                  SubmitNodeCollector collector,
                                                  CameraRenderState camera,
                                                  CallbackInfo ci) {
        copimine$activeModel = null;
        if (state == null) return;

        if (state instanceof EndRiftRenderStateAccess bound) {
            String visual = bound.copimine$visual();
            if ("END_RIFT_TENTACLE_HITBOX_V1".equals(visual)) {
                // The invisible living hitbox exists for targeting; the authored
                // ItemDisplay is its only visible body.
                ci.cancel();
                return;
            }
            if (state instanceof LivingEntityRenderState living
                    && EchoVanillaRenderer.render(living, matrices, collector, camera)) {
                ci.cancel();
                return;
            }
            copimine$selectWaveModel(state, bound);
        }
    }

    @Inject(method = "submit", at = @At("RETURN"))
    private void copimine$clearScopedRenderModel(S state, PoseStack matrices,
                                                  SubmitNodeCollector collector,
                                                  CameraRenderState camera, CallbackInfo ci) {
        copimine$activeModel = null;
    }

    @Inject(method = "getModelTint", at = @At("HEAD"), cancellable = true)
    private void copimine$tintDetachedEcho(LivingEntityRenderState state,
                                            CallbackInfoReturnable<Integer> cir) {
        if (state instanceof EndRiftRenderStateAccess bound && bound.copimine$isEcho()) {
            cir.setReturnValue(0xff946bb8);
        }
    }

    @Redirect(method = "submit", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;model:Lnet/minecraft/client/model/EntityModel;",
            opcode = Opcodes.GETFIELD))
    private EntityModel<?> copimine$readSelectedModel(LivingEntityRenderer<?, ?, ?> renderer) {
        return copimine$activeModel == null ? renderer.getModel() : copimine$activeModel;
    }

    @Redirect(method = "submit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V"))
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void copimine$submitImportedGuardian(SubmitNodeCollector collector, Model model,
                                                  Object state, PoseStack matrices, RenderType renderType,
                                                  int light, int overlay, int color, UvMapping uvMapping,
                                                  int outlineColor) {
        if (model instanceof RiftGuardianModel guardian && state instanceof EndermanRenderState) {
            me.copimine.client.ChameleonGuardianRenderer.submit(collector, matrices, renderType,
                    light, overlay, color, guardian);
            return;
        }
        collector.submitModel(model, state, matrices, renderType, light, overlay, color,
                uvMapping, outlineColor);
    }

    @Inject(method = "scale(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At("HEAD"), cancellable = true)
    private void copimine$scaleImportedGuardian(LivingEntityRenderState state, PoseStack matrices,
                                                 CallbackInfo ci) {
        if (state instanceof EndRiftRenderStateAccess bound && bound.copimine$endBoss()) {
            float scale = EndRiftBossWorldScalePolicy.renderScale();
            matrices.scale(scale, scale, scale);
            ci.cancel();
        }
    }

    @Unique
    private void copimine$selectWaveModel(EntityRenderState state, EndRiftRenderStateAccess bound) {
        String uuid = bound.copimine$uuid();
        String visual = bound.copimine$visual();
        EntityModel<?> selected = null;
        if (state instanceof EndermanRenderState && getModel() instanceof EndermanModel<?>) {
            EndermanRendererSelection.Decision decision;
            if (bound.copimine$endBoss()) {
                Identifier texture = copimine$guardianRenderer.textureForState(
                        bound.copimine$bossPhase(), bound.copimine$bossAnimation());
                decision = EndermanRendererSelection.select(uuid, uuid, texture,
                        EndEventTextureCatalog.isAvailable(texture));
                if (decision.usesGuardianModel()) {
                    selected = copimine$guardianRenderer.modelForPhase(bound.copimine$bossPhase(),
                            bound.copimine$bossPhaseTransitionMillis(), bound.copimine$bossAnimation(),
                            ClientBridgeProtocol.bossAnimationElapsedTicksForEntity(uuid, System.currentTimeMillis()));
                }
            } else {
                Identifier texture = EndEventTextureCatalog.textureForVisual(visual);
                decision = EndermanRendererSelection.selectVisual(uuid, visual, null, texture,
                        EndEventTextureCatalog.isAvailable(texture));
                if (decision.usesCustomModel()) selected = copimine$eventRenderer.modelFor(decision.kind());
            }
        } else if (state instanceof SkeletonRenderState && getModel() instanceof SkeletonModel<?>) {
            selected = copimine$skeletonRenderer.modelFor(visual);
        } else if (state instanceof LivingEntityRenderState && getModel() instanceof SpiderModel) {
            selected = copimine$spiderRenderer.modelFor(visual);
        }
        if (selected != null) {
            copimine$activeModel = selected;
        }
    }
}
