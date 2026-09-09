package me.copimine.client.mixin;

import me.copimine.client.ClientBridgeProtocol;
import me.copimine.client.EndRiftTentacleModel;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.DisplayEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the client-side part of the tentacle pose to the real ItemDisplay
 * renderer.  The server still owns the display transform, target, contact and
 * release markers; this hook only adds a small interpolated pose so the
 * segmented resource-pack model follows the animation state on clients that
 * have the optional mod installed.
 */
@Mixin(DisplayEntityRenderer.class)
public abstract class DisplayEntityRendererMixin {
    @Unique
    private boolean copimine$tentaclePosePushed;

    @Inject(
            method = "render(Lnet/minecraft/entity/decoration/DisplayEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"))
    private void copimine$applyTentaclePose(DisplayEntity entity, float yaw, float tickDelta,
                                             MatrixStack matrices,
                                             VertexConsumerProvider vertexConsumers,
                                             int light, CallbackInfo callback) {
        if (!(entity instanceof DisplayEntity.ItemDisplayEntity)
                || entity.getUuid() == null || matrices == null) {
            return;
        }
        String visual = ClientBridgeProtocol.endEventVisualForEntity(
                entity.getUuid().toString());
        if (!EndRiftTentacleModel.VISUAL_ID.equals(visual)) {
            return;
        }
        EndRiftTentacleModel.Pose pose = ClientBridgeProtocol.endEventTentaclePoseForEntity(
                entity.getUuid().toString(), Math.max(0L, entity.age));
        if (pose == null || !pose.isFinite()) {
            return;
        }
        matrices.push();
        copimine$tentaclePosePushed = true;

        // The server-provided ItemDisplay transform already controls the
        // authoritative rise/retract height.  Keep the client correction
        // deliberately subtle so it cannot make the visual drift away from
        // the server socket or affect any gameplay collision.
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation(pose.tipYaw() * 0.16F));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotation(pose.bendX() * 0.12F));
        float pulse = 1.0F + Math.max(-0.025F,
                Math.min(0.025F, (pose.clawOpen() - 0.5F) * 0.025F));
        matrices.scale(pulse, pulse, pulse);
    }

    @Inject(
            method = "render(Lnet/minecraft/entity/decoration/DisplayEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("RETURN"))
    private void copimine$restoreTentaclePose(DisplayEntity entity, float yaw, float tickDelta,
                                               MatrixStack matrices,
                                               VertexConsumerProvider vertexConsumers,
                                               int light, CallbackInfo callback) {
        if (copimine$tentaclePosePushed) {
            matrices.pop();
            copimine$tentaclePosePushed = false;
        }
    }
}
