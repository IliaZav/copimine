package me.copimine.client.mixin;

import me.copimine.client.EndEventTextureCatalog;
import me.copimine.client.EndRiftRenderStateAccess;
import net.minecraft.client.renderer.entity.WitherSkeletonRenderer;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * N23-P1-09: the Phase 4 infernal Wither Skeleton wears its authored skin only
 * after the server has bound its UUID. V6 9E.06 approves this one mobile ground
 * skeleton; the vanilla proportions and the 14.6 tower/turret ban are untouched.
 */
@Mixin(WitherSkeletonRenderer.class)
public abstract class WitherSkeletonEntityRendererMixin {
    @Inject(method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/SkeletonRenderState;)Lnet/minecraft/resources/Identifier;",
            at = @At("HEAD"), cancellable = true)
    private void copimine$infernoWitherTexture(SkeletonRenderState state,
                                               CallbackInfoReturnable<Identifier> cir) {
        String visual = state instanceof EndRiftRenderStateAccess bound ? bound.copimine$visual() : "";
        if (!"END_RIFT_INFERNO_WITHER_V1".equals(visual)) {
            return;
        }
        Identifier texture = EndEventTextureCatalog.textureForVisual(visual);
        if (texture != null && EndEventTextureCatalog.isAvailable(texture)) {
            EndEventTextureCatalog.logLookup("mob:" + visual, texture);
            cir.setReturnValue(texture);
        }
    }
}
