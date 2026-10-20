package me.copimine.client.mixin;

import me.copimine.client.ClientBridgeProtocol;
import me.copimine.client.EndEventTextureCatalog;
import net.minecraft.client.render.entity.WardenEntityRenderer;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies the supplied Warden trial skin only to an entity bound by the event server. */
@Mixin(WardenEntityRenderer.class)
public abstract class WardenEntityRendererMixin {
    private static final String WAVE7_WARDEN_VISUAL = "END_RIFT_WAVE7_WARDEN_V1";

    @Inject(method = "getTexture", at = @At("HEAD"), cancellable = true)
    private void copimine$wave7WardenTexture(WardenEntity entity,
                                             CallbackInfoReturnable<Identifier> cir) {
        if (entity == null || !WAVE7_WARDEN_VISUAL.equals(
                ClientBridgeProtocol.endEventVisualForEntity(entity.getUuid().toString()))) {
            return;
        }
        Identifier texture = EndEventTextureCatalog.textureForVisual(WAVE7_WARDEN_VISUAL);
        if (texture != null && EndEventTextureCatalog.isAvailable(texture)) {
            EndEventTextureCatalog.logLookup("mob:" + WAVE7_WARDEN_VISUAL, texture);
            cir.setReturnValue(texture);
        }
    }
}
