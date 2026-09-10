package me.copimine.client.mixin;

import me.copimine.client.ClientBridgeProtocol;
import me.copimine.client.EndRiftTentacleModel;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The server ItemDisplay is a lifecycle/visibility carrier.  Clients with
 * CopiMineClient render its real articulated rig in the world pass, so the
 * vanilla flat item model must not be drawn a second time.
 */
@Mixin(net.minecraft.client.render.entity.DisplayEntityRenderer.class)
public abstract class DisplayEntityRendererMixin {
    @Inject(
            method = "render(Lnet/minecraft/entity/decoration/DisplayEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"), cancellable = true)
    private void copimine$hideArticulatedTentacleCarrier(DisplayEntity entity, float yaw,
                                                          float tickDelta, MatrixStack matrices,
                                                          VertexConsumerProvider vertexConsumers,
                                                          int light, CallbackInfo ci) {
        if (!(entity instanceof DisplayEntity.ItemDisplayEntity)
                || entity.getUuid() == null) {
            return;
        }
        String visual = ClientBridgeProtocol.endEventVisualForEntity(entity.getUuid().toString());
        if (EndRiftTentacleModel.VISUAL_ID.equals(visual)) {
            ci.cancel();
        }
    }
}
