package me.copimine.client;

/**
 * The single transform contract for the imported Kagune rig.
 *
 * <p>Source rotations come from a Blockbench {@code geckolib_model} file and are
 * composed exactly the way {@link BedrockCoordinateTransform#sourceRotation}
 * composes them: {@code R = Rx(pitch) * Ry(yaw) * Rz(roll)}. The renderer draws
 * the imported vertices in the same Y-up model space as the source file, so no
 * {@code diag(1,-1,1)} basis conjugation is applied here; that conversion only
 * belongs to the {@code ModelPart} path that maps geometry into Minecraft's
 * Y-down entity space.</p>
 *
 * <p>Matrices are column-major {@code float[16]} (JOML layout) so the renderer
 * can hand them to {@code new Matrix4f(float[])} without conversion. Keeping the
 * math dependency-free lets it be unit tested outside the Fabric client.</p>
 */
public final class TentacleRigTransformPolicy {
    private static final float[] IDENTITY = {
            1.0F, 0.0F, 0.0F, 0.0F,
            0.0F, 1.0F, 0.0F, 0.0F,
            0.0F, 0.0F, 1.0F, 0.0F,
            0.0F, 0.0F, 0.0F, 1.0F};

    private TentacleRigTransformPolicy() {
    }

    public static float[] identity() {
        return IDENTITY.clone();
    }

    /** Translation * Rx * Ry * Rz * uniform-scale, all angles in radians. */
    public static float[] local(double translateX, double translateY, double translateZ,
                                double pitch, double yaw, double roll, double scale) {
        double safeScale = Double.isFinite(scale) && scale > 0.0D ? scale : 1.0D;
        double[] rotation = rotation(pitch, yaw, roll);
        return new float[]{
                (float) (rotation[0] * safeScale), (float) (rotation[3] * safeScale),
                (float) (rotation[6] * safeScale), 0.0F,
                (float) (rotation[1] * safeScale), (float) (rotation[4] * safeScale),
                (float) (rotation[7] * safeScale), 0.0F,
                (float) (rotation[2] * safeScale), (float) (rotation[5] * safeScale),
                (float) (rotation[8] * safeScale), 0.0F,
                (float) translateX, (float) translateY, (float) translateZ, 1.0F};
    }

    /** Row-major 3x3 rotation {@code Rx * Ry * Rz}. */
    static double[] rotation(double pitch, double yaw, double roll) {
        double safePitch = Double.isFinite(pitch) ? pitch : 0.0D;
        double safeYaw = Double.isFinite(yaw) ? yaw : 0.0D;
        double safeRoll = Double.isFinite(roll) ? roll : 0.0D;
        double cp = Math.cos(safePitch);
        double sp = Math.sin(safePitch);
        double cy = Math.cos(safeYaw);
        double sy = Math.sin(safeYaw);
        double cr = Math.cos(safeRoll);
        double sr = Math.sin(safeRoll);
        return new double[]{
                cy * cr, -cy * sr, sy,
                cp * sr + sp * sy * cr, cp * cr - sp * sy * sr, -sp * cy,
                sp * sr - cp * sy * cr, sp * cr + cp * sy * sr, cp * cy};
    }

    /** Column-major {@code a * b}; {@code b} is applied first to a column vector. */
    public static float[] multiply(float[] a, float[] b) {
        if (a == null || b == null || a.length != 16 || b.length != 16) {
            throw new IllegalArgumentException("two 4x4 matrices are required");
        }
        float[] result = new float[16];
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                double sum = 0.0D;
                for (int k = 0; k < 4; k++) {
                    sum += a[k * 4 + row] * (double) b[column * 4 + k];
                }
                result[column * 4 + row] = (float) sum;
            }
        }
        return result;
    }

    /** Applies a column-major transform to a point. */
    public static double[] transformPoint(float[] matrix, double x, double y, double z) {
        if (matrix == null || matrix.length != 16) {
            throw new IllegalArgumentException("a 4x4 matrix is required");
        }
        return new double[]{
                matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12],
                matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13],
                matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14]};
    }

    /** Maximum distance between two 4x4 matrices, used by pose-continuity checks. */
    public static double distance(float[] left, float[] right) {
        double max = 0.0D;
        for (int index = 0; index < 16; index++) {
            max = Math.max(max, Math.abs((double) left[index] - right[index]));
        }
        return max;
    }
}
