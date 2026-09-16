package me.copimine.client;

import net.minecraft.client.model.ModelPart;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class RiftEventSpiderRoleModelTest {
    @Test
    void spiderRoleVariantsHaveDistinctReadableSilhouetteParts() {
        RiftSpiderModel model = new RiftSpiderModel(
                RiftSpiderModel.getTexturedModelData().createModel());
        ModelPart root = model.getPart();

        assertNotNull(root.getChild("elite_carapace"));
        assertNotNull(root.getChild("guardian_spine"));
        assertNotNull(root.getChild("guard_seal"));
        assertNotNull(root.getChild("ritual_focus"));
    }

    @Test
    void rendererRoutesSpecialSpiderVisualIdsToDedicatedModels() {
        RiftSpiderModelRenderer renderer = new RiftSpiderModelRenderer();

        var elite = renderer.modelFor("END_RIFT_ELITE_SPIDER_V1");
        var waveGuardian = renderer.modelFor("END_RIFT_WAVE_GUARDIAN_SPIDER_V1");
        var ritualGuard = renderer.modelFor("END_RIFT_RITUAL_GUARD_SPIDER_V1");
        assertNotNull(elite);
        assertNotNull(waveGuardian);
        assertNotNull(ritualGuard);
        assertNotSame(elite, waveGuardian);
        assertNotSame(waveGuardian, ritualGuard);
    }
}
