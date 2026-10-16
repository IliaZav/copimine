package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChameleonGuardianGeometryTest {
    @Test
    void compilesEverySuppliedBoneCubeAndFace() {
        ChameleonGuardianGeometry geometry = ChameleonGuardianGeometry.load();

        assertEquals(16, geometry.boneCount());
        assertEquals(119, geometry.cubeCount());
        assertEquals(714, geometry.faceCount());
        assertEquals(714, geometry.restFaces().size());
    }

    @Test
    void nestedLowerArmUsesTheParentStackAroundItsAbsoluteSourcePivot() {
        ChameleonGuardianGeometry geometry = ChameleonGuardianGeometry.load();

        ChameleonGuardianGeometry.Vertex joint = geometry.transformedBonePivot("right_hand_low");

        assertEquals(-12.24544F, joint.x(), 0.0002F);
        assertEquals(-43.31405F, joint.y(), 0.0002F);
        assertEquals(8.57257F, joint.z(), 0.0002F);
    }

    @Test
    void rotatedHornComposesItsOwnRotationUnderTheRotatedHead() {
        ChameleonGuardianGeometry geometry = ChameleonGuardianGeometry.load();

        ChameleonGuardianGeometry.Vertex pivot = geometry.transformedCubePivot("head", 22);

        assertEquals(6.5F, pivot.x(), 0.0002F);
        assertEquals(-76.19105F, pivot.y(), 0.0002F);
        assertEquals(-4.52798F, pivot.z(), 0.0002F);
    }
}
