package me.copimine.client.mixin;

import me.copimine.client.DisplayRenderStateAccess;
import net.minecraft.client.renderer.entity.state.DisplayEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(DisplayEntityRenderState.class)
public abstract class DisplayEntityRenderStateMixin implements DisplayRenderStateAccess {
    @Unique private boolean copimine$hideVanillaCarrier;

    @Override public boolean copimine$hideVanillaCarrier() { return copimine$hideVanillaCarrier; }
    @Override public void copimine$setHideVanillaCarrier(boolean hide) { copimine$hideVanillaCarrier = hide; }
}
