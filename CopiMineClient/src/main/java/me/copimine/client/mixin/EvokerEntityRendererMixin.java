package me.copimine.client.mixin;

import me.copimine.client.ClientBridgeProtocol;
import me.copimine.client.EndEventTextureCatalog;
import net.minecraft.client.render.entity.EvokerEntityRenderer;
import net.minecraft.entity.mob.EvokerEntity;
import net.minecraft.entity.mob.SpellcastingIllagerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies the supplied Archmage skin only to an entity bound by the event server. */
@Mixin(EvokerEntityRenderer.class)
public abstract class EvokerEntityRendererMixin {
    private static final String WAVE7_ARCHMAGE_VISUAL = "END_RIFT_WAVE7_ARCHMAGE_V1";

    @Inject(method = "getTexture", at = @At("HEAD"), cancellable = true)
    private void copimine$wave7ArchmageTexture(SpellcastingIllagerEntity entity,
                                               CallbackInfoReturnable<Identifier> cir) {
        if (!(entity instanceof EvokerEntity evoker) || !WAVE7_ARCHMAGE_VISUAL.equals(
                ClientBridgeProtocol.endEventVisualForEntity(evoker.getUuid().toString()))) {
            return;
        }
        Identifier texture = EndEventTextureCatalog.textureForVisual(WAVE7_ARCHMAGE_VISUAL);
        if (texture != null && EndEventTextureCatalog.isAvailable(texture)) {
            EndEventTextureCatalog.logLookup("mob:" + WAVE7_ARCHMAGE_VISUAL, texture);
            cir.setReturnValue(texture);
        }
    }
}
