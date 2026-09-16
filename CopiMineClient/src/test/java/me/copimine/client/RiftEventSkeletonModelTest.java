package me.copimine.client;

import net.minecraft.client.model.ModelPart;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class RiftEventSkeletonModelTest {
    @Test
    void eliteAndSpecialSkeletonsHaveReadableSilhouetteParts() {
        RiftEventSkeletonModel ordinary = new RiftEventSkeletonModel(
                RiftEventSkeletonModel.getTexturedModelData(false).createModel(), false);
        RiftEventSkeletonModel elite = new RiftEventSkeletonModel(
                RiftEventSkeletonModel.getTexturedModelData(true).createModel(), true);
        ModelPart ordinaryRoot = ordinary.getPart();
        ModelPart eliteRoot = elite.getPart();

        assertNotNull(ordinaryRoot.getChild("guard_crest"));
        assertNotNull(ordinaryRoot.getChild("guard_chest_seal"));
        assertNotNull(ordinaryRoot.getChild("guardian_spine"));
        assertNotNull(eliteRoot.getChild("elite_mantle"));
        assertNotNull(eliteRoot.getChild("elite_horn_crown"));
    }

    @Test
    void rendererRoutesSpecialSkeletonVisualIdsToDedicatedModels() {
        RiftEventSkeletonModelRenderer renderer = new RiftEventSkeletonModelRenderer();

        var waveGuardian = renderer.modelFor("END_RIFT_WAVE_GUARDIAN_SKELETON_V1");
        var ritualGuard = renderer.modelFor("END_RIFT_RITUAL_GUARD_SKELETON_V1");
        assertNotNull(waveGuardian);
        assertNotNull(ritualGuard);
        assertNotSame(waveGuardian, ritualGuard);
    }
}
