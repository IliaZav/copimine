package me.copimine.client.mixin;

import me.copimine.client.ClientPostProcessRuntime;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds CopiMine's selected effect to the deferred 26.3 per-frame post-effect list. */
@Mixin(GameRenderer.class)
public abstract class GameRendererPostProcessMixin {
    @Inject(method = "extract(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("TAIL"))
    private void copimine$submitPostProcess(DeltaTracker deltaTracker, boolean renderLevel,
                                            CallbackInfo ci) {
        ClientPostProcessRuntime.submit((GameRenderer) (Object) this);
    }
}
