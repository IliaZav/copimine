package me.copimine.client.mixin;

import me.copimine.client.ClientBridgeProtocol;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the captured prisoner stationary while the server-owned session is active. */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputPrisonerMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void copimine$lockPrisonerMovement(boolean slowDown, float slowDownFactor,
                                               CallbackInfo ci) {
        if (!ClientBridgeProtocol.isPrisonerModeActive()) {
            return;
        }
        Input input = (Input) (Object) this;
        input.movementForward = 0.0F;
        input.movementSideways = 0.0F;
        input.jumping = false;
        input.sneaking = false;
    }
}
