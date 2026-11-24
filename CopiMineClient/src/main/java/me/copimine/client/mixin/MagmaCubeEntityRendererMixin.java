package me.copimine.client.mixin;

import me.copimine.client.ClientBridgeProtocol;
import me.copimine.client.EndEventTextureCatalog;
import net.minecraft.client.render.entity.MagmaCubeEntityRenderer;
import net.minecraft.entity.mob.MagmaCubeEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * N23-P1-09: the Phase 4 infernal Magma Cube wears an authored skin only after
 * the server has bound its UUID. A natural Magma Cube has no binding and keeps
 * the vanilla texture, so this never leaks into ordinary gameplay.
 */
@Mixin(MagmaCubeEntityRenderer.class)
public abstract class MagmaCubeEntityRendererMixin {
    @Inject(method = "getTexture", at = @At("HEAD"), cancellable = true)
    private void copimine$infernoMagmaTexture(MagmaCubeEntity entity, CallbackInfoReturnable<Identifier> cir) {
        if (entity == null) {
            return;
        }
        String visual = ClientBridgeProtocol.endEventVisualForEntity(entity.getUuid().toString());
        if (!"END_RIFT_INFERNO_MAGMA_V1".equals(visual)) {
            return;
        }
        Identifier texture = EndEventTextureCatalog.textureForVisual(visual);
        if (texture != null && EndEventTextureCatalog.isAvailable(texture)) {
            EndEventTextureCatalog.logLookup("mob:" + visual, texture);
            cir.setReturnValue(texture);
        }
    }
}
