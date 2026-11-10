package me.copimine.client.mixin;

import me.copimine.client.BossCameraShakeRuntime;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class BossCameraShakeMixin {
    @Inject(method = "update(Lnet/minecraft/client/DeltaTracker;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER))
    private void copimine$bossImpactCamera(DeltaTracker deltaTracker, CallbackInfo ci) {
        BossCameraShakeRuntime.apply((Camera) (Object) this);
    }
}
