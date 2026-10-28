package me.copimine.client.mixin;

import me.copimine.client.ClientBridgeProtocol;
import me.copimine.client.EndEventTextureCatalog;
import net.minecraft.client.render.entity.ZombieEntityRenderer;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Event zombies receive the supplied skin only after a server UUID binding. */
@Mixin(ZombieEntityRenderer.class)
public abstract class ZombieEntityRendererMixin {
    @Inject(method = "getTexture", at = @At("HEAD"), cancellable = true)
    private void copimine$zombieTexture(ZombieEntity entity, CallbackInfoReturnable<Identifier> cir) {
        if (entity == null) {
            return;
        }
        String visual = ClientBridgeProtocol.endEventVisualForEntity(entity.getUuid().toString());
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