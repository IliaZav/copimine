package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BedrockCoordinateTransformTest {
    @Test
    void pointUsesOneFormalBasisAndTranslation() {
        BedrockCoordinateTransform.Vec3 target = BedrockCoordinateTransform.sourcePoint(2.0, 7.0, -3.0);

        assertEquals(2.0, target.x(), 0.000001);
        assertEquals(17.0, target.y(), 0.000001);
        assertEquals(-3.0, target.z(), 0.000001);
    }

    @Test
    void animationDeltaDoesNotReceivePointTranslation() {
        BedrockCoordinateTransform.Vec3 target = BedrockCoordinateTransform.sourceDelta(2.0, 7.0, -3.0);

        assertEquals(2.0, target.x(), 0.000001);
        assertEquals(-7.0, target.y(), 0.000001);
        assertEquals(-3.0, target.z(), 0.000001);
    }

    @Test
    void basisReportsTheHandednessReflectionExplicitly() {
        assertEquals(-1.0, BedrockCoordinateTransform.basisDeterminant(), 0.000001);
    }

    @Test
    void relativePoseUsesTheInverseRotatedParentForAbsoluteBedrockPivots() {
        BedrockCoordinateTransform.Pose parent = BedrockCoordinateTransform.targetPose(
                new BedrockCoordinateTransform.Vec3(7.5, 62.5, 0.75),
                21.55363, -3.40444, -13.78967);
        BedrockCoordinateTransform.Pose child = BedrockCoordinateTransform.targetPose(
                new BedrockCoordinateTransform.Vec3(8.0, 41.25, 0.75),
                -32.5, 0.0, 0.0);

        BedrockCoordinateTransform.Pose local = child.relativeTo(parent);
        assertEquals(5.645891, local.translation().x(), 0.00001);
        assertEquals(18.964951, local.translation().y(), 0.00001);
        assertEquals(7.763185, local.translation().z(), 0.00001);
        assertPoseEquals(child, parent.compose(local));
    }

    @Test
    void conjugatedRotationRoundTripsThroughTheFormalMatrix() {
        BedrockCoordinateTransform.Matrix3 target = BedrockCoordinateTransform.sourceRotation(
                21.55363, -3.40444, -13.78967);
        BedrockCoordinateTransform.Vec3 euler = target.toEulerXyzDegrees();

        assertEquals(-21.55363, euler.x(), 0.00001);
        assertEquals(-3.40444, euler.y(), 0.00001);
        assertEquals(13.78967, euler.z(), 0.00001);
        assertEquals(1.0, target.multiply(target.inverse()).m00(), 0.000001);
        assertEquals(1.0, target.multiply(target.inverse()).m11(), 0.000001);
        assertEquals(1.0, target.multiply(target.inverse()).m22(), 0.000001);
    }

    private static void assertPoseEquals(BedrockCoordinateTransform.Pose expected,
                                         BedrockCoordinateTransform.Pose actual) {
        assertEquals(expected.translation().x(), actual.translation().x(), 0.00001);
        assertEquals(expected.translation().y(), actual.translation().y(), 0.00001);
        assertEquals(expected.translation().z(), actual.translation().z(), 0.00001);
        assertEquals(expected.rotation().m00(), actual.rotation().m00(), 0.00001);
        assertEquals(expected.rotation().m01(), actual.rotation().m01(), 0.00001);
        assertEquals(expected.rotation().m02(), actual.rotation().m02(), 0.00001);
        assertEquals(expected.rotation().m10(), actual.rotation().m10(), 0.00001);
        assertEquals(expected.rotation().m11(), actual.rotation().m11(), 0.00001);
        assertEquals(expected.rotation().m12(), actual.rotation().m12(), 0.00001);
        assertEquals(expected.rotation().m20(), actual.rotation().m20(), 0.00001);
        assertEquals(expected.rotation().m21(), actual.rotation().m21(), 0.00001);
        assertEquals(expected.rotation().m22(), actual.rotation().m22(), 0.00001);
    }
}
