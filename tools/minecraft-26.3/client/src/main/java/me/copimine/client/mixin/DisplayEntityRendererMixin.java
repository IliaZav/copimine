package me.copimine.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import me.copimine.client.DisplayRenderStateAccess;
import me.copimine.client.EndRiftGuardianShieldRenderer;
import me.copimine.client.EndRiftTentacleRenderer;
import me.copimine.client.RitualSphereRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.state.DisplayEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hide custom ItemDisplay carriers during deferred submission after deciding from their item data. */
@Mixin(DisplayRenderer.class)
public abstract class DisplayEntityRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Display;Lnet/minecraft/client/renderer/entity/state/DisplayEntityRenderState;F)V",
            at = @At("TAIL"))
    private void copimine$tagArticulatedCarrier(Display entity, DisplayEntityRenderState state,
                                               float partialTick, CallbackInfo ci) {
        if (!(state instanceof DisplayRenderStateAccess access)) return;
        access.copimine$setHideVanillaCarrier(
                EndRiftTentacleRenderer.isCustomRenderEligible(entity)
                        || EndRiftGuardianShieldRenderer.isCustomRenderEligible(entity)
                        || RitualSphereRenderer.isCustomRenderEligible(entity));
    }

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void copimine$hideArticulatedTentacleCarrier(DisplayEntityRenderState state,
                                                          PoseStack matrices,
                                                          SubmitNodeCollector collector,
                                                          CameraRenderState camera,
                                                          CallbackInfo ci) {
        if (state instanceof DisplayRenderStateAccess access && access.copimine$hideVanillaCarrier()) {
            ci.cancel();
        }
    }
}
