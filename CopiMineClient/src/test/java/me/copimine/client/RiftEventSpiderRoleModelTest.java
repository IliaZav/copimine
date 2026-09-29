package me.copimine.client;

import net.minecraft.client.model.ModelPart;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class RiftEventSpiderRoleModelTest {
    @Test
    void spiderRoleVariantsHaveDistinctReadableSilhouetteParts() {
        RiftSpiderModel model = new RiftSpiderModel(
                RiftSpiderModel.getTexturedModelData().createModel());
        ModelPart root = model.getPart();

        assertNotNull(root.getChild("body1").getChild("elite_carapace"));
        assertNotNull(root.getChild("body1").getChild("guardian_spine"));
        assertNotNull(root.getChild("head").getChild("guard_seal"));
        assertNotNull(root.getChild("head").getChild("ritual_focus"));
    }

    @Test
    void rendererRoutesSpecialSpiderVisualIdsToDedicatedModels() {
        RiftSpiderModelRenderer renderer = new RiftSpiderModelRenderer();

        var ordinary = renderer.modelFor("END_RIFT_SPIDER_V1");
        var elite = renderer.modelFor("END_RIFT_ELITE_SPIDER_V1");
        var waveGuardian = renderer.modelFor("END_RIFT_WAVE_GUARDIAN_SPIDER_V1");
        var ritualGuard = renderer.modelFor("END_RIFT_RITUAL_GUARD_SPIDER_V1");
        assertNotNull(ordinary);
        assertNotNull(elite);
        assertNotNull(waveGuardian);
        assertNotNull(ritualGuard);
        assertNotSame(ordinary, elite);
        assertNotSame(elite, waveGuardian);
        assertNotSame(waveGuardian, ritualGuard);
        assertNotSame(ritualGuard, ordinary);
        assertSame(ordinary, renderer.modelFor("end_rift_spider_v1"),
                "normalized lookup returns the ordinary custom event geometry");
        assertNull(renderer.modelFor("END_RIFT_UNKNOWN_SPIDER_V1"));
    }
}
