package me.copimine.client.mixin;

import me.copimine.client.EchoVanillaRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native unload also fences a packet-owned actor which has never been rendered. */
@Mixin(ClientLevel.class)
public abstract class EchoCarrierUnloadMixin {
    @Inject(method = "removeEntity", at = @At("HEAD"))
    private void copimine$retireEchoCarrier(int id, Entity.RemovalReason reason, CallbackInfo ci) {
        Entity entity = ((ClientLevel) (Object) this).getEntity(id);
        if (entity != null) EchoVanillaRenderer.retire(entity.getUUID());
    }
}
