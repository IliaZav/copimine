package me.copimine.client.mixin;

import me.copimine.client.ClientBridgeProtocol;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.EyesFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.EndermanEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents the vanilla Enderman eyes pass from being drawn over the supplied
 * Rift Guardian mesh.  The check is UUID-scoped so ordinary Endermen keep
 * their vanilla emissive eyes and the custom boss remains server-bound.
 */
@Mixin(EyesFeatureRenderer.class)
public abstract class EndermanEyesFeatureRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void copimine$hideVanillaGuardianEyes(MatrixStack matrices,
                                                   VertexConsumerProvider vertexConsumers,
                                                   int light,
                                                   Entity entity,
                                                   float limbAngle,
                                                   float limbDistance,
                                                   float tickDelta,
                                                   float animationProgress,
                                                   float headYaw,
                                                   float headPitch,
                                                   CallbackInfo callback) {
        if (entity instanceof EndermanEntity enderman) {
            String uuid = enderman.getUuid().toString();
            String visual = ClientBridgeProtocol.endEventVisualForEntity(uuid);
            if (ClientBridgeProtocol.isBoundEndBoss(uuid)
                    || (visual != null && visual.startsWith("END_RIFT_"))) {
                callback.cancel();
            }
        }
    }
}
