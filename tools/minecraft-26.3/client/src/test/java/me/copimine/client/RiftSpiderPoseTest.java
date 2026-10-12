package me.copimine.client;

import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RiftSpiderPoseTest {
    @Test
    void everyLegStartsWithTheVanillaSpiderSplay() {
        ModelPart root = RiftSpiderModel.getTexturedModelData(
                RiftSpiderModel.Variant.ORDINARY).bakeRoot();

        assertLegPose(root, "right_hind_leg", 0.7853982F, -0.7853982F);
        assertLegPose(root, "left_hind_leg", -0.7853982F, 0.7853982F);
        assertLegPose(root, "right_middle_hind_leg", 0.3926991F, -0.58119464F);
        assertLegPose(root, "left_middle_hind_leg", -0.3926991F, 0.58119464F);
        assertLegPose(root, "right_middle_front_leg", -0.3926991F, -0.58119464F);
        assertLegPose(root, "left_middle_front_leg", 0.3926991F, 0.58119464F);
        assertLegPose(root, "right_front_leg", -0.7853982F, -0.7853982F);
        assertLegPose(root, "left_front_leg", 0.7853982F, 0.7853982F);
    }

    private static void assertLegPose(ModelPart root, String legName,
                                      float expectedYaw, float expectedRoll) {
        ModelPart leg = root.getChild(legName);
        assertEquals(0.0F, leg.xRot, 1.0E-6F, legName + " pitch");
        assertEquals(expectedYaw, leg.yRot, 1.0E-6F, legName + " yaw");
        assertEquals(expectedRoll, leg.zRot, 1.0E-6F, legName + " roll");
    }
}
