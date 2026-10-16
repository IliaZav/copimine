package me.copimine.client.mixin;

import me.copimine.client.ClientBridgeProtocol;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the client-side health display in the server's configured range for
 * the one UUID-bound End Rift guardian.  The server still owns health changes;
 * this hook changes only the value exposed to vanilla client UI code.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHealthProjectionMixin {
    @Inject(method = "getMaxHealth", at = @At("RETURN"), cancellable = true)
    private void copimine$projectBoundBossMaxHealth(CallbackInfoReturnable<Float> cir) {
        LivingEntity entity = (LivingEntity) (Object) this;
        float nativeMaxHealth = cir.getReturnValue();
        double projected = ClientBridgeProtocol.projectedBossMaxHealth(
                entity.getUuid().toString(), nativeMaxHealth);
        if (Double.compare(projected, nativeMaxHealth) != 0) {
            cir.setReturnValue((float) projected);
        }
    }
}
