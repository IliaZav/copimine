package me.copimine.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import me.copimine.client.EndRiftRenderStateAccess;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents vanilla eyes from drawing over event-specific emissive eye artwork. */
@Mixin(EyesLayer.class)
public abstract class EndermanEyesFeatureRendererMixin {
    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void copimine$hideVanillaEventEyes(PoseStack matrices, SubmitNodeCollector collector,
                                               int light, EntityRenderState state,
                                               float bodyYaw, float headPitch, CallbackInfo callback) {
        if (!(state instanceof EndRiftRenderStateAccess bound)) return;
        String visual = bound.copimine$visual();
        if (bound.copimine$endBoss() || (visual != null && visual.startsWith("END_RIFT_"))) {
            callback.cancel();
        }
    }
}
