package me.copimine.client.mixin;

import me.copimine.client.ClientBridgeProtocol;
import net.minecraft.client.render.entity.EndermanEntityRenderer;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Uses the event guardian texture only for the server-bound official boss UUID. */
@Mixin(EndermanEntityRenderer.class)
public abstract class EndermanEntityRendererMixin {
    private static final Identifier COPIMINE_GUARDIAN_TEXTURE = Identifier.of(
            "copimineclient", "textures/entity/end_rift_guardian.png");

    @Inject(method = "getTexture", at = @At("HEAD"), cancellable = true)
    private void copimine$guardianTexture(EndermanEntity entity, CallbackInfoReturnable<Identifier> cir) {
        if (entity != null && ClientBridgeProtocol.isBoundEndBoss(entity.getUuid().toString())) {
            cir.setReturnValue(COPIMINE_GUARDIAN_TEXTURE);
        }
    }
}
