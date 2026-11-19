package me.copimine.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import me.copimine.client.ClientBridgeProtocol;
import me.copimine.client.EchoVanillaRenderer;
import me.copimine.client.EndEventTextureCatalog;
import me.copimine.client.EndRiftBossWorldScalePolicy;
import me.copimine.client.EndRiftRenderStateAccess;
import me.copimine.client.EndermanRendererSelection;
import me.copimine.client.EchoPlayerView;
import me.copimine.client.Wave7NamedTrialModelRenderer;
import me.copimine.client.Wave7NamedTrialVisualPolicy;
import me.copimine.client.RiftEventEndermanModelRenderer;
import me.copimine.client.RiftEventSkeletonModelRenderer;
import me.copimine.client.RiftGuardianModel;
import me.copimine.client.RiftGuardianModelRenderer;
import me.copimine.client.RiftSpiderModelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.monster.enderman.EndermanModel;
import net.minecraft.client.model.monster.skeleton.SkeletonModel;
import net.minecraft.client.model.monster.spider.SpiderModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.EvokerRenderState;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.client.renderer.entity.state.WardenRenderState;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.model.monster.warden.WardenModel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
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
    @Unique private final Wave7NamedTrialModelRenderer copimine$wave7Renderer = new Wave7NamedTrialModelRenderer();
    @Unique private me.copimine.client.RiftEndermiteModel copimine$endermiteModel;
    @Unique private EntityModel<?> copimine$activeModel;

    /**
     * Contract 15.3's subtle identification clue: a decoy/replica body is drawn
     * a shade colder than the real boss. ARGB 0xD6/0xC4/0xF0 keeps the authored
     * skin readable while a careful player can learn the difference.
     */
    @Unique private static final int COPIER_TINT = 0xFFD6C4F0;

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void copimine$bindEventRenderState(T entity, S state, float partialTick,
                                               CallbackInfo ci) {
        if (!(state instanceof EndRiftRenderStateAccess bound)) return;
        String uuid = entity.getUUID().toString();
        bound.copimine$poseProgress(ClientBridgeProtocol.endEventState().wavePoseProgress(uuid,System.currentTimeMillis()));
        bound.copimine$bindTrialFrame(ClientBridgeProtocol.endEventState().trialFrame(uuid, System.currentTimeMillis()));
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
            Minecraft client = Minecraft.getInstance();
            if (client.player != null && client.level != null) {
                Entity rendered = bound.copimine$entityId() < 0
                        ? null : client.level.getEntity(bound.copimine$entityId());
                if (rendered instanceof Player
                        && ClientBridgeProtocol.endEventPlayerVisuals().hidesPlayer(
                        rendered.getUUID(), client.player.getUUID(),
                        client.level.dimension().identifier().getPath(), System.currentTimeMillis())) {
                    ci.cancel();
                    return;
                }
            }
        }

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
        } else if (state instanceof EndRiftRenderStateAccess copy
                && copimine$isGuardianCopy(copy.copimine$visual())) {
            // Contract 15.3 asks for a SUBTLE, learnable clue that separates a
            // copy from the real boss ("a consistent difference in Rift core
            // energy"). Both bodies share one mesh and one skin, so without a
            // per-body tint a decoy was pixel-identical to the boss and the
            // rule had no implementation at all. A cool violet rim is enough
            // for a careful player to learn and far from the forbidden "FAKE"
            // label.
            cir.setReturnValue(COPIER_TINT);
        }
    }

    /**
     * Contract 15.3's learnable clue: a decoy and an attack replica keep the
     * Guardian body but read slightly colder than the real boss.
     */
    @Unique
    private static boolean copimine$isGuardianCopy(String visual) {
        if (visual == null) {
            return false;
        }
        String normalized = visual.trim().toUpperCase(java.util.Locale.ROOT);
        return "END_RIFT_ECHO_DECOY_V1".equals(normalized)
                || "END_RIFT_ECHO_REPLICA_V1".equals(normalized);
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
        // Keyed on the MODEL, not on the render-state class. An Echo decoy is a
        // Pillager, so its state is an IllagerRenderState; requiring
        // EndermanRenderState here left the Guardian mesh out of the decoy and
        // let vanilla submit the (invisible) compatibility carrier instead.
        if (model instanceof RiftGuardianModel guardian
                && state instanceof LivingEntityRenderState livingState) {
            // The Enderman boss route already carries the Guardian texture
            // through its renderer; every other entity type (Pillager decoy,
            // echo replica) renders with its own texture, so the authored boss
            // skin has to be supplied here or the body draws with wrong UVs.
            RenderType type = state instanceof EndermanRenderState
                    ? renderType
                    : net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(
                            me.copimine.client.RiftGuardianModelRenderer.bossTexture());
            // Layers are suppressed for these carriers (see
            // copimine$suppressGuardianCarrierLayers), so the pose that would
            // normally be advanced by LivingEntityRenderer.submit is advanced
            // here, immediately before the geometry is emitted.
            guardian.setupAnim(livingState);
            me.copimine.client.ChameleonGuardianRenderer.submit(collector, matrices, type,
                    light, overlay, color, guardian);
            return;
        }
        collector.submitModel(model, state, matrices, renderType, light, overlay, color,
                uvMapping, outlineColor);
    }

    /**
     * A Guardian body carried by something that is not an Enderman must not run
     * the carrier's layers.
     *
     * <p>A Pillager decoy extracts an {@code IllagerRenderState}, and the
     * illager layers cast {@code getParentModel()} to {@code IllagerModel}. The
     * shared Guardian model is not one, so running them threw
     * {@code ClassCastException} once per decoy per frame. The authored body is
     * the whole visible decoy, so its layers are skipped deliberately.</p>
     */
    @Inject(method = "shouldRenderLayers(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)Z",
            at = @At("HEAD"), cancellable = true)
    private void copimine$suppressGuardianCarrierLayers(LivingEntityRenderState state,
                                                        CallbackInfoReturnable<Boolean> cir) {
        if (state instanceof EndermanRenderState) return;
        if (state instanceof EndRiftRenderStateAccess bound
                && copimine$wearsGuardianBody(bound.copimine$visual())) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "scale(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At("HEAD"), cancellable = true)
    private void copimine$scaleImportedGuardian(LivingEntityRenderState state, PoseStack matrices,
                                                 CallbackInfo ci) {
        if (state instanceof EndRiftRenderStateAccess bound && copimine$isGuardianState(bound)) {
            float scale = EndRiftBossWorldScalePolicy.renderScale();
            matrices.scale(scale, scale, scale);
            ci.cancel();
        }
    }

    /**
     * True when this render state is the Rift Guardian — either because the
     * server set the bound-boss flag, or because it bound the Guardian visual
     * id. Both routes mean the same thing to the renderer, and treating only
     * the flag as authoritative is what let a visual-bound boss draw with the
     * vanilla Enderman model at vanilla scale.
     */
    @Unique
    private static boolean copimine$isGuardianState(EndRiftRenderStateAccess bound) {
        if (bound == null) return false;
        if (bound.copimine$endBoss()) return true;
        return copimine$wearsGuardianBody(bound.copimine$visual());
    }

    /**
     * Contract 15.2/15.3: the real Guardian, an echo copy and an echo decoy all
     * wear the authored Guardian body, so the model, texture and scale decisions
     * must agree on one list. Keeping the list here is what stops the identity
     * check and the model selection from drifting apart.
     */
    @Unique
    private static boolean copimine$wearsGuardianBody(String visual) {
        if (visual == null) {
            return false;
        }
        String normalized = visual.trim().toUpperCase(java.util.Locale.ROOT);
        return "END_RIFT_GUARDIAN_V1".equals(normalized)
                || "END_RIFT_ECHO_DECOY_V1".equals(normalized)
                || "END_RIFT_ECHO_REPLICA_V1".equals(normalized);
    }

    @Unique
    private void copimine$selectWaveModel(EntityRenderState state, EndRiftRenderStateAccess bound) {
        String uuid = bound.copimine$uuid();
        String visual = bound.copimine$visual();
        EntityModel<?> selected = null;
        Wave7NamedTrialVisualPolicy.ActorModel namedActor = Wave7NamedTrialVisualPolicy.modelFor(visual);
        if (namedActor != null && !copimine$wave7Renderer.supportsState(visual, state.getClass())) {
            return;
        }
        if (namedActor == Wave7NamedTrialVisualPolicy.ActorModel.WARDEN
                && state instanceof WardenRenderState && getModel() instanceof WardenModel) {
            selected = copimine$wave7Renderer.modelFor(namedActor);
        } else if (namedActor == Wave7NamedTrialVisualPolicy.ActorModel.ARCHMAGE
                && state instanceof EvokerRenderState && getModel() instanceof IllagerModel<?>) {
            selected = copimine$wave7Renderer.modelFor(namedActor);
        } else if (state instanceof IllagerRenderState && copimine$wearsGuardianBody(visual)) {
            // Contract 15.3: the server spawns a decoy as a PILLAGER on purpose,
            // so it never shares the boss entity type, HP or defeat effects. A
            // Pillager extracts an IllagerRenderState, which no branch matched:
            // the decoy fell through and vanilla drew a small brown illager
            // where the contract asks for a copy of the Guardian. Selecting the
            // Guardian model here makes the decoy look like the boss it imitates.
            selected = copimine$guardianRenderer.modelForPhase(bound.copimine$bossPhase(),
                    bound.copimine$bossPhaseTransitionMillis(), bound.copimine$bossAnimation(),
                    ClientBridgeProtocol.bossAnimationElapsedTicksForEntity(uuid, System.currentTimeMillis()));
        } else if (state instanceof EndermanRenderState && getModel() instanceof EndermanModel<?>) {
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
                if (decision.usesGuardianModel()) {
                    // The server can bind the Guardian by VISUAL ID without the
                    // endBoss flag (the disposable boss harness does exactly
                    // that). Handling only the flag left this branch routing a
                    // Guardian to the ordinary event Enderman model, so the boss
                    // rendering as "just an Enderman" was a client-side defect,
                    // not a server one. The guardian branch must win here too.
                    selected = copimine$guardianRenderer.modelForPhase(bound.copimine$bossPhase(),
                            bound.copimine$bossPhaseTransitionMillis(), bound.copimine$bossAnimation(),
                            ClientBridgeProtocol.bossAnimationElapsedTicksForEntity(uuid, System.currentTimeMillis()));
                } else if (decision.usesCustomModel()) {
                    selected = copimine$eventRenderer.modelFor(decision.kind());
                }
            }
        } else if (state instanceof SkeletonRenderState && getModel() instanceof SkeletonModel<?>) {
            selected = copimine$skeletonRenderer.modelFor(visual);
        } else if ("END_RIFT_ENDERMITE_V1".equals(visual)
                && getModel() instanceof net.minecraft.client.model.monster.endermite.EndermiteModel
                && EndEventTextureCatalog.isAvailable(EndEventTextureCatalog.textureForVisual(visual))) {
            if (copimine$endermiteModel == null) copimine$endermiteModel = new me.copimine.client.RiftEndermiteModel(
                    me.copimine.client.RiftEndermiteModel.createLayer().bakeRoot());
            selected = copimine$endermiteModel;
        } else if (state instanceof LivingEntityRenderState && getModel() instanceof SpiderModel) {
            selected = copimine$spiderRenderer.modelFor(visual);
        }
        if (selected != null) {
            copimine$activeModel = selected;
        }
    }
}
