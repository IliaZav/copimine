package me.copimine.client.mixin;

import me.copimine.client.ClientBridgeProtocol;
import me.copimine.client.CopiMineClientLogger;
import me.copimine.client.RiftGuardianModelRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.EndermanEntityRenderer;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.util.Identifier;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Set;

/** Uses event textures only for UUIDs explicitly bound by the End Rift server. */
@Mixin(EndermanEntityRenderer.class)
public abstract class EndermanEntityRendererMixin extends MobEntityRenderer<EndermanEntity, EntityModel<EndermanEntity>> {
    @Unique
    private static final Set<String> COPIMINE_LOGGED_RENDER_ENTITIES = new HashSet<>();
    @Unique
    private final RiftGuardianModelRenderer copimine$guardianRenderer = new RiftGuardianModelRenderer();
    @Unique
    private EntityModel<EndermanEntity> copimine$vanillaModel;

    protected EndermanEntityRendererMixin(EntityRendererFactory.Context context, EntityModel<EndermanEntity> model, float shadowRadius) {
        super(context, model, shadowRadius);
    }

    private static Identifier texture(String name) {
        return Identifier.of("copimineclient", "textures/entity/" + name + ".png");
    }

    @Inject(method = "render(Lnet/minecraft/entity/mob/EndermanEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("HEAD"))
    private void copimine$useGuardianModelForBoundBoss(EndermanEntity entity, float yaw, float tickDelta,
                                                       MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                       int light, CallbackInfo ci) {
        if (entity == null || !ClientBridgeProtocol.isBoundEndBoss(entity.getUuid().toString())) {
            return;
        }
        copimine$vanillaModel = model;
        model = copimine$guardianRenderer.modelForPhase(
                ClientBridgeProtocol.bossPhaseForEntity(entity.getUuid().toString()),
                ClientBridgeProtocol.bossPhaseTransitionMillisForEntity(entity.getUuid().toString()));
    }

    @Inject(method = "render(Lnet/minecraft/entity/mob/EndermanEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("RETURN"))
    private void copimine$restoreVanillaModelAfterBoundBoss(EndermanEntity entity, float yaw, float tickDelta,
                                                            MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                            int light, CallbackInfo ci) {
        if (copimine$vanillaModel != null) {
            model = copimine$vanillaModel;
            copimine$vanillaModel = null;
        }
    }

    @Inject(method = "getTexture", at = @At("HEAD"), cancellable = true)
    private void copimine$guardianTexture(EndermanEntity entity, CallbackInfoReturnable<Identifier> cir) {
        if (entity == null) {
            return;
        }
        if (ClientBridgeProtocol.isBoundEndBoss(entity.getUuid().toString())) {
            cir.setReturnValue(copimine$guardianRenderer.textureForPhase(
                    ClientBridgeProtocol.bossPhaseForEntity(entity.getUuid().toString())));
            return;
        }
        String visual = ClientBridgeProtocol.endEventVisualForEntity(entity.getUuid().toString());
        Identifier texture = switch (visual) {
            case "END_RIFT_GUARDIAN_V1" -> texture("end_rift_guardian");
            case "END_RIFT_ELITE_V1" -> texture("end_rift_elite");
            case "END_RIFT_ENDERMAN_V1" -> texture("end_rift_enderman");
            default -> null;
        };
        if (!visual.isBlank() && COPIMINE_LOGGED_RENDER_ENTITIES.add(entity.getUuid().toString())) {
            boolean resourcePresent = texture != null
                    && MinecraftClient.getInstance().getResourceManager().getResource(texture).isPresent();
            CopiMineClientLogger.info("End Rift renderer visual=" + visual
                    + ", uuid=" + entity.getUuid()
                    + ", texture=" + texture
                    + ", resourcePresent=" + resourcePresent);
        }
        if (texture != null) {
            cir.setReturnValue(texture);
        }
    }
}
