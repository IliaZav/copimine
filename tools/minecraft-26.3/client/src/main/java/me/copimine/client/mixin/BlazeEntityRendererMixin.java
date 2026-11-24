package me.copimine.client.mixin;

import me.copimine.client.EndEventTextureCatalog;
import me.copimine.client.EndRiftRenderStateAccess;
import net.minecraft.client.renderer.entity.BlazeRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * N23-P1-09: the Phase 4 infernal Blaze wears its authored skin only after the
 * server has bound its UUID, so a natural Blaze keeps the vanilla sheet.
 */
@Mixin(BlazeRenderer.class)
public abstract class BlazeEntityRendererMixin {
    @Inject(method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)Lnet/minecraft/resources/Identifier;",
            at = @At("HEAD"), cancellable = true)
    private void copimine$infernoBlazeTexture(LivingEntityRenderState state,
                                              CallbackInfoReturnable<Identifier> cir) {
        String visual = state instanceof EndRiftRenderStateAccess bound ? bound.copimine$visual() : "";
        if (!"END_RIFT_INFERNO_BLAZE_V1".equals(visual)) {
            return;
        }
        Identifier texture = EndEventTextureCatalog.textureForVisual(visual);
        if (texture != null && EndEventTextureCatalog.isAvailable(texture)) {
            EndEventTextureCatalog.logLookup("mob:" + visual, texture);
            cir.setReturnValue(texture);
        }
    }
}
