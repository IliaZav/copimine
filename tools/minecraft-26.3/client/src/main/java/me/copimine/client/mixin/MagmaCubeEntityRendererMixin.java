package me.copimine.client.mixin;

import me.copimine.client.EndEventTextureCatalog;
import me.copimine.client.EndRiftRenderStateAccess;
import net.minecraft.client.renderer.entity.MagmaCubeRenderer;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * N23-P1-09: the Phase 4 infernal Magma Cube wears its authored skin only after
 * the server has bound its UUID. A natural Magma Cube has no binding and keeps
 * the vanilla sheet, so this never leaks into ordinary gameplay. The vanilla
 * silhouette and its small/large variants are untouched.
 */
@Mixin(MagmaCubeRenderer.class)
public abstract class MagmaCubeEntityRendererMixin {
    @Inject(method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/SlimeRenderState;)Lnet/minecraft/resources/Identifier;",
            at = @At("HEAD"), cancellable = true)
    private void copimine$infernoMagmaTexture(SlimeRenderState state, CallbackInfoReturnable<Identifier> cir) {
        String visual = state instanceof EndRiftRenderStateAccess bound ? bound.copimine$visual() : "";
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
