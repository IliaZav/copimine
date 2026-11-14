package me.copimine.client;

import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RiftGuardianModelRendererTest {
    @Test void rendererKeepsCanonicalActionIdentityUntilTheActualClipLookup() {
        assertEquals("CAST_CHARGE", RiftGuardianModelRenderer.normalizeAnimationId("CAST_CHARGE"));
        assertEquals("CAST_CHARGE", RiftGuardianModelRenderer.normalizeAnimationId("TELEGRAPHING"));
        assertEquals("SPELL_VOID_BLAST", RiftGuardianModelRenderer.normalizeAnimationId("SPELL_VOID_BLAST"));
        assertEquals("SPELL_SUMMON_SERVANTS", RiftGuardianModelRenderer.normalizeAnimationId("SPELL_SUMMON_SERVANTS"));
        assertEquals("SPELL_ARENA_INFERNO", RiftGuardianModelRenderer.normalizeAnimationId("SPELL_ARENA_INFERNO"));
        assertEquals("TELEPORT_RIP", RiftGuardianModelRenderer.normalizeAnimationId("TELEPORT"));
        assertEquals("UNKNOWN", RiftGuardianModelRenderer.normalizeAnimationId("NOT_A_REAL_ACTION"));
    }

    @Test void everyPhaseActuallySamplesTheNewClipsThroughTheProductionRenderer() {
        for (var phase : RiftGuardianModelRenderer.Phase.values()) {
            var renderer=new RiftGuardianModelRenderer();
            for (String action : new String[]{"CAST_CHARGE","CAST_RELEASE","RECOVERY","FINAL_STRIKE"}) {
                var model=renderer.modelForPhase(phase.name(),1200,action,10);
                model.setupAnim(new EndermanRenderState());
                assertEquals(UserEndBossAnimationPlayer.sample(action,10),model.currentPose(),phase+":"+action);
            }
        }
    }
}
