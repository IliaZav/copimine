package me.copimine.client.mixin;

import me.copimine.client.EndEventTextureCatalog;
import me.copimine.client.EndRiftRenderStateAccess;
import net.minecraft.client.renderer.entity.EndermiteRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Restores the authored Wave 6 Rift Ram Endermite skin.
 *
 * <p>The pre-26.3 client shipped an {@code EndermiteEntityRenderer} mixin plus
 * its {@code end_rift_endermite.png} sheet. Both were deleted with the old wave
 * implementation, and nothing replaced them: every add the Ram released since
 * then rendered with the plain vanilla bug sheet, which is exactly the
 * "authored endermite models" regression W6-01 reports.</p>
 *
 * <p>As with every other event renderer, the override is gated on a server UUID
 * binding carried in the render state. A natural Endermite a player brought into
 * the arena therefore keeps its vanilla skin.</p>
 */
@Mixin(EndermiteRenderer.class)
public abstract class EndermiteEntityRendererMixin {
    @Inject(method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)Lnet/minecraft/resources/Identifier;",
            at = @At("HEAD"), cancellable = true)
    private void copimine$endermiteTexture(LivingEntityRenderState state,
                                           CallbackInfoReturnable<Identifier> cir) {
        if (!(state instanceof EndRiftRenderStateAccess bound)) return;
        String visual = bound.copimine$visual();
        if (!"END_RIFT_ENDERMITE_V1".equals(visual)) return;
        Identifier texture = EndEventTextureCatalog.textureForVisual(visual);
        if (texture == null) return;
        EndEventTextureCatalog.logLookup("mob:" + visual, texture);
        // Only swap when the sheet is actually on disk. A missing asset must
        // fall through to vanilla rather than render an invisible entity.
        if (EndEventTextureCatalog.isAvailable(texture)) {
            cir.setReturnValue(texture);
        }
    }
}
