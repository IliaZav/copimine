package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndRiftGuardianShieldModelTest {
    @Test
    void usesTheDedicatedServerCarrierIdAndFiniteShieldPose() {
        assertEquals(830020, EndRiftGuardianShieldModel.SERVER_CUSTOM_MODEL_DATA);
        assertTrue(EndRiftGuardianShieldModel.isServerCustomModelData(830020));
        assertFalse(EndRiftGuardianShieldModel.isServerCustomModelData(830017));
        assertTrue(EndRiftGuardianShieldModel.pose(0.5F).isFinite());
        assertEquals(1, EndRiftGuardianShieldModel.pose(0.5F).plateCount());
    }

    @Test
    void shieldLeavesTheGuardianTorsoVisibleInsideTheFourPlateOrbit() {
        assertEquals(0.68F,
                EndRiftGuardianShieldModel.worldRenderScale(EndRiftGuardianShieldModel.pose(0.5F)),
                0.0001F);
    }

    @Test
    void shieldRenderTintEnablesTranslucentPurpleTextureBlending() {
        int alpha = EndRiftGuardianShieldModel.renderTint() >>> 24;
        assertTrue(alpha > 0 && alpha < 255,
                "the shield tint must blend its purple texture instead of drawing opaque plates");
    }

    @Test
    void shieldFaceUsesATaperedNinePointSilhouetteInsteadOfARectangularCard() {
        var outline = EndRiftGuardianShieldModel.outline();

        assertEquals(9, outline.size());
        assertEquals(new EndRiftGuardianShieldModel.Point(-6.0F, -14.0F), outline.get(0));
        assertEquals(new EndRiftGuardianShieldModel.Point(0.0F, 10.0F), outline.get(5));
        assertEquals(new EndRiftGuardianShieldModel.Point(-8.0F, -11.0F), outline.get(8));
        assertEquals(7, EndRiftGuardianShieldModel.frontTriangles().size(),
                "a nine-point plate is triangulated as a solid face, not one flat quad");
    }

    @Test
    void translucentRenderLayerReceivesCompleteQuadsForBothShieldFaces() {
        var triangles = EndRiftGuardianShieldModel.frontTriangles();
        var quads = EndRiftGuardianShieldModel.frontQuads();

        assertEquals(triangles.size(), quads.size());
        for (int index = 0; index < quads.size(); index++) {
            var triangle = triangles.get(index);
            var quad = quads.get(index);
            assertEquals(triangle.first(), quad.first());
            assertEquals(triangle.second(), quad.second());
            assertEquals(triangle.third(), quad.third());
            assertEquals(quad.third(), quad.fourth(),
                    "the degenerate fourth vertex keeps adjacent triangles out of the same Minecraft quad");
        }
        assertEquals(0, (quads.size() * 4 * 2) % 4);
    }

    @Test
    void shieldUvSamplesOnlyTheDedicatedShapedAtlasRegion() {
        assertEquals(96.0F / 512.0F,
                EndRiftGuardianShieldModel.textureU(new EndRiftGuardianShieldModel.Point(-8.0F, 0.0F)),
                0.0001F);
        assertEquals(416.0F / 512.0F,
                EndRiftGuardianShieldModel.textureU(new EndRiftGuardianShieldModel.Point(8.0F, 0.0F)),
                0.0001F);
        assertEquals(20.0F / 512.0F,
                EndRiftGuardianShieldModel.textureV(new EndRiftGuardianShieldModel.Point(0.0F, -14.0F)),
                0.0001F);
        assertEquals(492.0F / 512.0F,
                EndRiftGuardianShieldModel.textureV(new EndRiftGuardianShieldModel.Point(0.0F, 10.0F)),
                0.0001F);
    }

    @Test
    void imageTopMapsAboveTheBottomTipInWorldSpace() {
        var outline = EndRiftGuardianShieldModel.outline();
        assertTrue(EndRiftGuardianShieldModel.worldY(outline.get(0))
                > EndRiftGuardianShieldModel.worldY(outline.get(5)),
                "PNG coordinates grow downward, while Minecraft Y grows upward");
        assertEquals(14.0F / 16.0F,
                EndRiftGuardianShieldModel.worldY(outline.get(0)), 0.0001F);
        assertEquals(-10.0F / 16.0F,
                EndRiftGuardianShieldModel.worldY(outline.get(5)), 0.0001F);
    }

    @Test
    void renderOnlyOrbitAdvancesSmoothlyBetweenServerCarrierUpdates() {
        var orbit = EndRiftGuardianShieldModel.orbitFromCarrier(
                10.0D, 64.0D, -5.0D, 10.78D, 67.1D, -5.0D, 100.0D);
        assertTrue(orbit.isFinite());
        assertEquals(10.78D, orbit.xAt(100.0D, 10.0D), 0.00001D);
        assertEquals(67.1D, orbit.yAt(100.0D, 64.0D), 0.00001D);
        assertEquals(-5.0D, orbit.zAt(100.0D, -5.0D), 0.00001D);
        assertEquals(0.75D, orbit.angleDegreesAt(100.5D), 0.00001D);
        assertEquals(1.5D, orbit.angleDegreesAt(101.0D), 0.00001D);
        assertEquals(-90.75D, orbit.faceRotationDegreesAt(100.5D), 0.00001D);
        assertEquals(67.1D + Math.sin(Math.toRadians(1.5D)) * 0.18D,
                orbit.yAt(100.5D, 64.0D), 0.00001D);
        assertEquals(67.1D + Math.sin(Math.toRadians(15.0D)) * 0.18D,
                orbit.yAt(105.0D, 64.0D), 0.00001D);
        assertTrue(orbit.zAt(100.5D, -5.0D) > -5.0D);
    }

    @Test
    void renderOnlyOrbitDoesNotSnapBackwardWhenCarrierYawWraps() {
        double radians = Math.toRadians(359.0D);
        var orbit = EndRiftGuardianShieldModel.orbitFromCarrier(
                0.0D, 0.0D, 0.0D, 0.78D * Math.cos(radians),
                3.1D, 0.78D * Math.sin(radians), 20.0D);
        assertTrue(orbit.angleDegreesAt(21.0D) > orbit.angleDegreesAt(20.0D));
        assertEquals(360.5D, orbit.angleDegreesAt(21.0D), 0.00001D);
    }

    @Test
    void shieldFaceNormalPointsTowardItsOrbitRadiusInsteadOfAlongTheTangent() {
        // Paper sends the carrier yaw as orbit angle + 90 degrees. The
        // renderer emits the visible front face at -Z, so rotate that normal
        // toward the carrier's outward orbit radius.
        float[] serverYaw = {90.0F, 180.0F, 270.0F, 360.0F};
        float[][] radialNormals = {
                {1.0F, 0.0F}, {0.0F, 1.0F}, {-1.0F, 0.0F}, {0.0F, -1.0F}
        };
        for (int index = 0; index < serverYaw.length; index++) {
            double radians = Math.toRadians(
                    EndRiftGuardianShieldModel.faceRotationDegrees(
                            serverYaw[index], serverYaw[index], 1.0F));
            double normalX = -Math.sin(radians);
            double normalZ = -Math.cos(radians);
            assertEquals(radialNormals[index][0], normalX, 0.00001D);
            assertEquals(radialNormals[index][1], normalZ, 0.00001D);
        }
    }

    @Test
    void shieldOrbitYawCrossesTheFullCircleWithoutSpinningBackwards() {
        float start = EndRiftGuardianShieldModel.faceRotationDegrees(448.5F, 90.0F, 0.0F);
        float midpoint = EndRiftGuardianShieldModel.faceRotationDegrees(448.5F, 90.0F, 0.5F);
        float end = EndRiftGuardianShieldModel.faceRotationDegrees(448.5F, 90.0F, 1.0F);

        assertEquals(-0.75F, midpoint - start, 0.0001F);
        assertEquals(-1.5F, end - start, 0.0001F);
    }
}
