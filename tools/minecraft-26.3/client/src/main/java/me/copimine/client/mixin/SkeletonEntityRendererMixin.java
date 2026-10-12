package me.copimine.client.mixin;

import me.copimine.client.ClientBridgeProtocol;
import me.copimine.client.EndEventTextureCatalog;
import me.copimine.client.EndRiftRenderStateAccess;
import net.minecraft.client.renderer.entity.SkeletonRenderer;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Event skeletons receive a texture only after a server UUID binding. */
@Mixin(SkeletonRenderer.class)
public abstract class SkeletonEntityRendererMixin {
    @Inject(method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/SkeletonRenderState;)Lnet/minecraft/resources/Identifier;",
            at = @At("HEAD"), cancellable = true)
    private void copimine$skeletonTexture(SkeletonRenderState state,
                                          CallbackInfoReturnable<Identifier> cir) {
        String visual = state instanceof EndRiftRenderStateAccess bound ? bound.copimine$visual() : "";
        Identifier texture = EndEventTextureCatalog.textureForVisual(visual);
        boolean resourcePresent = texture != null && EndEventTextureCatalog.isAvailable(texture);
        EndEventTextureCatalog.logLookup("mob:" + visual, texture);
        if (resourcePresent) {
            cir.setReturnValue(texture);
        }
    }
}
