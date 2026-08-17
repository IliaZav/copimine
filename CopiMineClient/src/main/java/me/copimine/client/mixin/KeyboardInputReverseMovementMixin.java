package me.copimine.client.mixin;

import me.copimine.client.ClientBridgeProtocol;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies the optional End Rift control effect after vanilla keyboard sampling. */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputReverseMovementMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void copimine$reverseMovement(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        if (ClientBridgeProtocol.isReverseMovementActive()) {
            Input input = (Input) (Object) this;
            input.movementForward = -input.movementForward;
            input.movementSideways = -input.movementSideways;
        }
    }
}
