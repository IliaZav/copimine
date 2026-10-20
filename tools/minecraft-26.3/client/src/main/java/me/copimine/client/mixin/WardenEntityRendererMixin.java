package me.copimine.client.mixin;

import me.copimine.client.EndEventTextureCatalog;
import me.copimine.client.EndRiftRenderStateAccess;
import me.copimine.client.Wave7NamedTrialVisualPolicy;
import net.minecraft.client.renderer.entity.WardenRenderer;
import net.minecraft.client.renderer.entity.state.WardenRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies the named Warden atlas only to a server-bound Wave 7 actor. */
@Mixin(WardenRenderer.class)
public abstract class WardenEntityRendererMixin {
    @Inject(method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/WardenRenderState;)Lnet/minecraft/resources/Identifier;",
            at = @At("HEAD"), cancellable = true)
    private void copimine$wave7WardenTexture(WardenRenderState state,
                                              CallbackInfoReturnable<Identifier> cir) {
        if (!(state instanceof EndRiftRenderStateAccess bound)
                || !Wave7NamedTrialVisualPolicy.WARDEN_VISUAL.equals(bound.copimine$visual())) return;
        Identifier texture = EndEventTextureCatalog.textureForVisual(bound.copimine$visual());
        EndEventTextureCatalog.logLookup("wave7-warden", texture);
        if (EndEventTextureCatalog.isAvailable(texture)) cir.setReturnValue(texture);
    }
}
