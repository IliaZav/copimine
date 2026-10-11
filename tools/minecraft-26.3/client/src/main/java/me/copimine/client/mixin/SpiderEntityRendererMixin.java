package me.copimine.client.mixin;

import me.copimine.client.ClientBridgeProtocol;
import me.copimine.client.EndEventTextureCatalog;
import me.copimine.client.EndRiftRenderStateAccess;
import net.minecraft.client.renderer.entity.SpiderRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Event spiders receive the supplied skin only after a server UUID binding. */
@Mixin(SpiderRenderer.class)
public abstract class SpiderEntityRendererMixin {
    @Inject(method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)Lnet/minecraft/resources/Identifier;",
            at = @At("HEAD"), cancellable = true)
    private void copimine$spiderTexture(LivingEntityRenderState state, CallbackInfoReturnable<Identifier> cir) {
        String visual = state instanceof EndRiftRenderStateAccess bound ? bound.copimine$visual() : "";
        Identifier texture = switch (visual) {
            case "END_RIFT_SPIDER_V1",
                 "END_RIFT_ELITE_SPIDER_V1",
                 "END_RIFT_WAVE_GUARDIAN_SPIDER_V1",
                 "END_RIFT_RITUAL_GUARD_SPIDER_V1" -> EndEventTextureCatalog.textureForVisual(visual);
            default -> null;
        };
        if (texture != null) {
            EndEventTextureCatalog.logLookup("mob:" + visual, texture);
            if (EndEventTextureCatalog.isAvailable(texture)) {
                cir.setReturnValue(texture);
            }
        }
    }
}
