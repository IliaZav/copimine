package me.copimine.client;

import net.minecraft.client.model.ModelPart;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserEndBossAnimationPlayerTest {
    @Test
    void appliesSuppliedBedrockRotationThroughTheSharedCoordinateContract() {
        ModelPart root = RiftGuardianModel.getTexturedModelData().createModel();
        ModelPart head = root.getChild("head");
        float bindPitch = head.pitch;
        root.traverse().forEach(ModelPart::resetTransform);

        assertTrue(UserEndBossAnimationPlayer.apply(root, "IDLE_BREATH", 20.0F));

        // The supplied idle clip is -2.5 degrees of Bedrock pitch at 1s.
        // Target ModelPart pitch uses the reflected +2.5 degree value.
        assertEquals(bindPitch + Math.toRadians(2.5D), head.pitch, 0.00001D);
    }

    @Test
    void appliesSuppliedBedrockPositionAsAUnitDeltaWithoutPointTranslation() {
        ModelPart root = RiftGuardianModel.getTexturedModelData().createModel();
        ModelPart head = root.getChild("head");
        float bindPivotY = head.pivotY;
        float bindPivotZ = head.pivotZ;
        root.traverse().forEach(ModelPart::resetTransform);

        assertTrue(UserEndBossAnimationPlayer.apply(root, "CHEST_STRIKE", 40.0F));

        // At 2 seconds the chest clip positions the head by source +Z=5;
        // the target delta keeps X/Z and does not add the model-origin Y.
        assertEquals(bindPivotZ + 5.0F, head.pivotZ, 0.0001F);
        assertEquals(bindPivotY, head.pivotY, 0.0001F);
    }
}
