package me.copimine.client.mixin;

import me.copimine.client.EndEventTextureCatalog;
import me.copimine.client.EndRiftRenderStateAccess;
import net.minecraft.client.renderer.entity.AbstractZombieRenderer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Event zombies receive a skin only after a server UUID binding, so plain
 * vanilla zombies keep the vanilla texture.
 */
@Mixin(AbstractZombieRenderer.class)
public abstract class ZombieEntityRendererMixin {
    @Inject(method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/ZombieRenderState;)Lnet/minecraft/resources/Identifier;",
            at = @At("HEAD"), cancellable = true)
    private void copimine$zombieTexture(ZombieRenderState state, CallbackInfoReturnable<Identifier> cir) {
        String visual = state instanceof EndRiftRenderStateAccess bound ? bound.copimine$visual() : "";
        Identifier texture = switch (visual) {
            case "END_RIFT_ZOMBIE_V1", "END_RIFT_ELITE_ZOMBIE_V1" ->
                    EndEventTextureCatalog.textureForVisual(visual);
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