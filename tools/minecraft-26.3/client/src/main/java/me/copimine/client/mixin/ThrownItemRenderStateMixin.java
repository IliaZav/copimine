package me.copimine.client.mixin;

import me.copimine.client.GuardianSpellRenderer;
import me.copimine.client.GuardianSpellRenderStateAccess;
import net.minecraft.client.renderer.entity.state.ThrownItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ThrownItemRenderState.class)
public abstract class ThrownItemRenderStateMixin implements GuardianSpellRenderStateAccess {
    @Unique private GuardianSpellRenderer.Pose copimine$spellPose;
    @Override public GuardianSpellRenderer.Pose copimine$spellPose() { return copimine$spellPose; }
    @Override public void copimine$spellPose(GuardianSpellRenderer.Pose pose) { copimine$spellPose = pose; }
}
