package me.copimine.client;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RiftEndermiteModelTest {
    @Test void carapaceAndSpinesFollowTheRealSegmentAnimation() {
        var root = RiftEndermiteModel.createLayer().bakeRoot();
        var model = new RiftEndermiteModel(root);
        var state = new EntityRenderState();
        state.ageInTicks = 17;
        model.setupAnim(state);
        for (int index = 0; index < 4; index++) {
            var segment = root.getChild("segment" + index);
            assertFalse(segment.getChild("rift_carapace").isEmpty());
            assertFalse(segment.getChild("rift_spine").isEmpty());
            assertTrue(Float.isFinite(segment.yRot));
        }
        assertNotEquals(root.getChild("segment0").yRot, root.getChild("segment2").yRot);
    }
}
