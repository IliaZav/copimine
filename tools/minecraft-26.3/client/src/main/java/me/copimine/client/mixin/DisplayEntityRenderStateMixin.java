package me.copimine.client.mixin;

import me.copimine.client.DisplayRenderStateAccess;
import me.copimine.client.GuardianSpellRenderStateAccess;
import me.copimine.client.GuardianSpellRenderer;
import net.minecraft.client.renderer.entity.state.DisplayEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(DisplayEntityRenderState.class)
public abstract class DisplayEntityRenderStateMixin implements DisplayRenderStateAccess, GuardianSpellRenderStateAccess {
    @Unique private GuardianSpellRenderer.Pose copimine$spellPose;
    @Override public GuardianSpellRenderer.Pose copimine$spellPose() { return copimine$spellPose; }
    @Override public void copimine$spellPose(GuardianSpellRenderer.Pose pose) { copimine$spellPose=pose; }
    @Unique private boolean copimine$hideVanillaCarrier;

    @Override public boolean copimine$hideVanillaCarrier() { return copimine$hideVanillaCarrier; }
    @Override public void copimine$setHideVanillaCarrier(boolean hide) { copimine$hideVanillaCarrier = hide; }
}
