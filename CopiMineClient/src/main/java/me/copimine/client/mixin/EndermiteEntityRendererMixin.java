package me.copimine.client.mixin;

import me.copimine.client.ClientBridgeProtocol;
import net.minecraft.client.render.entity.EndermiteEntityRenderer;
import net.minecraft.entity.mob.EndermiteEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Event endermites receive a texture only after a server UUID binding. */
@Mixin(EndermiteEntityRenderer.class)
public abstract class EndermiteEntityRendererMixin {
    private static final Identifier COPIMINE_ENDERMITE_TEXTURE = Identifier.of(
            "copimineclient", "textures/entity/end_rift_endermite.png");

    @Inject(method = "getTexture", at = @At("HEAD"), cancellable = true)
    private void copimine$endermiteTexture(EndermiteEntity entity, CallbackInfoReturnable<Identifier> cir) {
        if (entity != null && "END_RIFT_ENDERMITE_V1".equals(
                ClientBridgeProtocol.endEventVisualForEntity(entity.getUuid().toString()))) {
            cir.setReturnValue(COPIMINE_ENDERMITE_TEXTURE);
        }
    }
}
