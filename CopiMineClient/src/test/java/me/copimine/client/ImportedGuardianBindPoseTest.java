package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ImportedGuardianBindPoseTest {
    @Test
    void sourceBindPoseIsHeldByTheDirectAbsolutePivotEvaluator() {
        ChameleonGuardianGeometry geometry = ChameleonGuardianGeometry.load();

        assertEquals(714, geometry.restFaces().size());
        assertEquals(-12.24544F, geometry.transformedBonePivot("right_hand_low").x(), 0.0002F);
    }

    @Test
    void directGuardianModelDoesNotRenderTheLegacyCuboidCarrier() {
        RiftGuardianModel model = new RiftGuardianModel(
                RiftGuardianModel.getTexturedModelData().createModel());

        assertFalse(model.usesVanillaCuboidGuardianMesh());
    }

    @Test
    void importedGuardianFeetLandAtTheLivingRendererGroundPlane() {
        float footVertexY = ChameleonGuardianGeometry.load().restFaces().stream()
                .flatMap(face -> face.vertices().stream())
                .map(ChameleonGuardianGeometry.Vertex::y)
                .max(Float::compare)
                .orElseThrow();

        // The Bedrock-to-Minecraft basis flips model Y, so the greatest source
        // Y is the physical foot. The exact value flowing into Minecraft's
        // renderer must cancel its 1.501-block root baseline.
        assertEquals(1.501F, ChameleonGuardianRenderer.renderModelY(footVertexY), 0.02F,
                "the supplied guardian's lowest face must meet the entity's floor origin");
    }
}
