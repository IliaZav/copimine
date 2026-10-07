package me.copimine.client.mixin;

import me.copimine.client.PrisonerTargetSelector;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Enables Minecraft's model outline only for the prisoner's current eligible hover. */
@Mixin(MinecraftClient.class)
public abstract class PrisonerTargetOutlineMixin {
    @Inject(method = "hasOutline", at = @At("HEAD"), cancellable = true)
    private void copimine$outlineHoveredPrisonerTarget(Entity entity,
                                                       CallbackInfoReturnable<Boolean> cir) {
        if (PrisonerTargetSelector.outlineColor((MinecraftClient) (Object) this, entity) != 0) {
            cir.setReturnValue(true);
        }
    }
}
