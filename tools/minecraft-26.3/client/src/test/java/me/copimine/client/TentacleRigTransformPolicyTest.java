package me.copimine.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the Kagune rig transform contract: Bedrock source rotations compose as
 * {@code Rx*Ry*Rz} in the same Y-up model space the renderer draws, and a child
 * bone always inherits its parent's transform.
 */
class TentacleRigTransformPolicyTest {
    private static final double EPSILON = 1.0E-5D;

    @Test
    void identityTransformKeepsEveryPoint() {
        double[] point = TentacleRigTransformPolicy.transformPoint(
                TentacleRigTransformPolicy.identity(), 1.5D, -2.25D, 3.75D);
        assertEquals(1.5D, point[0], EPSILON);
        assertEquals(-2.25D, point[1], EPSILON);
        assertEquals(3.75D, point[2], EPSILON);
    }

    @Test
    void rotationOrderIsXThenYThenZ() {
        double pitch = Math.toRadians(35.0D);
        double yaw = Math.toRadians(-47.5D);
        double roll = Math.toRadians(26.5D);
        float[] matrix = TentacleRigTransformPolicy.local(0, 0, 0, pitch, yaw, roll, 1.0D);
        double[] actual = TentacleRigTransformPolicy.transformPoint(matrix, 1.0D, 2.0D, 3.0D);

        // Independent reference: rotate the point by Z, then Y, then X.
        double[] afterZ = rotateZ(1.0D, 2.0D, 3.0D, roll);
        double[] afterY = rotateY(afterZ[0], afterZ[1], afterZ[2], yaw);
        double[] expected = rotateX(afterY[0], afterY[1], afterY[2], pitch);

        assertEquals(expected[0], actual[0], EPSILON);
        assertEquals(expected[1], actual[1], EPSILON);
        assertEquals(expected[2], actual[2], EPSILON);
    }

    @Test
    void childOffsetIsRotatedByTheParent() {
        float[] parent = TentacleRigTransformPolicy.local(0, 0, 0, 0, Math.PI / 2.0D, 0, 1.0D);
        float[] child = TentacleRigTransformPolicy.local(1.0D, 0, 0, 0, 0, 0, 1.0D);
        double[] origin = TentacleRigTransformPolicy.transformPoint(
                TentacleRigTransformPolicy.multiply(parent, child), 0, 0, 0);
        // A 90-degree yaw around Y maps +X onto -Z.
        assertEquals(0.0D, origin[0], EPSILON);
        assertEquals(0.0D, origin[1], EPSILON);
        assertEquals(-1.0D, origin[2], EPSILON);
    }

    @Test
    void chainCompositionMatchesComposedMatrix() {
        List<float[]> chain = List.of(
                TentacleRigTransformPolicy.local(0.35D, 0.0D, 0.0D, 0, 0, 0, 1.0D),
                TentacleRigTransformPolicy.local(0.15D, 1.5D, 0.0D,
                        Math.toRadians(-42.5D), Math.toRadians(-47.5D), 0, 1.0D),
                TentacleRigTransformPolicy.local(0.25D, 1.25D, 0.0D,
                        Math.toRadians(-77.5382D), Math.toRadians(-12.8192D),
                        Math.toRadians(8.3899D), 1.0D));
        float[] incremental = TentacleRigTransformPolicy.identity();
        for (float[] link : chain) {
            incremental = TentacleRigTransformPolicy.multiply(incremental, link);
        }
        float[] composed = TentacleRigTransformPolicy.multiply(
                TentacleRigTransformPolicy.multiply(chain.get(0), chain.get(1)), chain.get(2));
        assertTrue(TentacleRigTransformPolicy.distance(incremental, composed) < 1.0E-6D,
                "incremental and composed hierarchy math must agree");
    }

    @Test
    void grabAndHoldKeyframesStayFiniteAndContinuous() {
        // Authored hold/trow angles from the supplied Blockbench clips.
        float[] holdTip = TentacleRigTransformPolicy.multiply(
                TentacleRigTransformPolicy.local(0.35D, 0, 0,
                        0, Math.toRadians(-45.0D), 0, 1.0D),
                TentacleRigTransformPolicy.local(0.15D, 1.5D, 0,
                        Math.toRadians(-20.0888D), Math.toRadians(10.3502D),
                        Math.toRadians(26.1618D), 1.0D));
        float[] throwTip = TentacleRigTransformPolicy.multiply(
                TentacleRigTransformPolicy.local(0.35D, 0, 0,
                        0, Math.toRadians(-45.0D), 0, 1.0D),
                TentacleRigTransformPolicy.local(0.15D, 1.5D, 0,
                        Math.toRadians(-20.0888D), Math.toRadians(10.3502D),
                        Math.toRadians(26.1618D), 1.0D));
        for (float value : holdTip) {
            assertTrue(Float.isFinite(value), "hold pose must stay finite");
        }
        assertTrue(TentacleRigTransformPolicy.distance(holdTip, throwTip) < 1.0E-6D,
                "the shared 1layer2 pose must be identical in hold and throw");
        double[] socket = TentacleRigTransformPolicy.transformPoint(holdTip, 0.0D, 7.6181D, 0.0D);
        assertTrue(Math.abs(socket[0]) < 20.0D && Math.abs(socket[1]) < 20.0D
                        && Math.abs(socket[2]) < 20.0D,
                "grab socket must stay inside the model envelope: "
                        + socket[0] + "," + socket[1] + "," + socket[2]);
    }

    private static double[] rotateX(double x, double y, double z, double angle) {
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        return new double[]{x, c * y - s * z, s * y + c * z};
    }

    private static double[] rotateY(double x, double y, double z, double angle) {
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        return new double[]{c * x + s * z, y, -s * x + c * z};
    }

    private static double[] rotateZ(double x, double y, double z, double angle) {
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        return new double[]{c * x - s * y, s * x + c * y, z};
    }
}
