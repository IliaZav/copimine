package me.copimine.client.mixin;

import me.copimine.client.PrisonerTargetSelector;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Colors the local hover outline without changing an entity's team or glowing state. */
@Mixin(Entity.class)
public abstract class PrisonerTargetOutlineColorMixin {
    @Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true)
    private void copimine$colorHoveredPrisonerTarget(CallbackInfoReturnable<Integer> cir) {
        int color = PrisonerTargetSelector.outlineColor(MinecraftClient.getInstance(),
                (Entity) (Object) this);
        if (color != 0) cir.setReturnValue(color);
    }
}
