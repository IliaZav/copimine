package me.copimine.client.mixin;

import me.copimine.client.ClientBridgeProtocol;
import net.minecraft.client.render.entity.ShulkerEntityRenderer;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Event shulkers receive a texture only after a server UUID binding. */
@Mixin(ShulkerEntityRenderer.class)
public abstract class ShulkerEntityRendererMixin {
    private static final Identifier COPIMINE_SHULKER_TEXTURE = Identifier.of(
            "copimineclient", "textures/entity/end_rift_shulker.png");

    @Inject(method = "getTexture", at = @At("HEAD"), cancellable = true)
    private void copimine$shulkerTexture(ShulkerEntity entity, CallbackInfoReturnable<Identifier> cir) {
        if (entity != null && "END_RIFT_SHULKER_V1".equals(
                ClientBridgeProtocol.endEventVisualForEntity(entity.getUuid().toString()))) {
            cir.setReturnValue(COPIMINE_SHULKER_TEXTURE);
        }
    }
}
