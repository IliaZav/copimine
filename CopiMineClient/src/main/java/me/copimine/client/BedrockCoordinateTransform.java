package me.copimine.client;

/**
 * The single coordinate contract used by End Rift geometry and animation
 * import.
 *
 * <p>Bedrock models use a right-handed model space with X to the model's
 * right, Y up, Z toward the model front, model-space pivots, and cube origins
 * at the lower/minimum corner.  Fabric's {@code ModelPart} uses X right, Y
 * down, Z toward the model front, and child pivots expressed relative to the
 * parent.  The conversion therefore uses the fixed basis
 * {@code B = diag(1,-1,1)} and point translation {@code t=(0,24,0)}.  The
 * translation is only for model-space points; animation vectors never receive
 * it.  Since det(B) is -1, face winding/mirroring must be handled explicitly
 * by the mesh importer.</p>
 */
public final class BedrockCoordinateTransform {
    private static final double MODEL_ORIGIN_Y = 24.0D;
    private static final Matrix3 BASIS = new Matrix3(
            1.0D, 0.0D, 0.0D,
            0.0D, -1.0D, 0.0D,
            0.0D, 0.0D, 1.0D);

    private BedrockCoordinateTransform() {
    }

    /** Converts an absolute/model-space Bedrock point using B*p+t. */
    public static Vec3 sourcePoint(double x, double y, double z) {
        return new Vec3(x, MODEL_ORIGIN_Y - y, z);
    }

    public static Vec3 sourcePoint(Vec3 source) {
        if (source == null) {
            throw new IllegalArgumentException("source point must not be null");
        }
        return sourcePoint(source.x(), source.y(), source.z());
    }

    /** Converts a delta/vector using B*v; no point translation is allowed. */
    public static Vec3 sourceDelta(double x, double y, double z) {
        return new Vec3(x, -y, z);
    }

    public static Vec3 sourceDelta(Vec3 source) {
        if (source == null) {
            throw new IllegalArgumentException("source delta must not be null");
        }
        return sourceDelta(source.x(), source.y(), source.z());
    }

    public static double pointTranslationY() {
        return MODEL_ORIGIN_Y;
    }

    /** Returns R_target = B * R_source * inverse(B). */
    public static Matrix3 sourceRotation(double pitchDegrees, double yawDegrees,
                                         double rollDegrees) {
        Matrix3 source = Matrix3.eulerXyz(pitchDegrees, yawDegrees, rollDegrees);
        return BASIS.multiply(source).multiply(BASIS);
    }

    /**
     * Builds the target-space pose for a Bedrock pivot and its local rotation.
     * Bedrock pivots are absolute model-space coordinates, so this pose is
     * deliberately not a delta and includes the model-origin translation.
     */
    public static Pose targetPose(Vec3 sourcePivot, double pitchDegrees,
                                  double yawDegrees, double rollDegrees) {
        if (sourcePivot == null) {
            throw new IllegalArgumentException("source pivot must not be null");
        }
        return new Pose(sourcePoint(sourcePivot),
                sourceRotation(pitchDegrees, yawDegrees, rollDegrees));
    }

    public static double basisDeterminant() {
        return BASIS.determinant();
    }

    /** Explicitly exposes the reflection so mesh code cannot treat it as a normal rotation. */
    public static boolean changesHandedness() {
        return basisDeterminant() < 0.0D;
    }

    public record Vec3(double x, double y, double z) {
        public Vec3 add(Vec3 other) {
            if (other == null) {
                throw new IllegalArgumentException("other vector must not be null");
            }
            return new Vec3(x + other.x(), y + other.y(), z + other.z());
        }

        public Vec3 subtract(Vec3 other) {
            if (other == null) {
                throw new IllegalArgumentException("other vector must not be null");
            }
            return new Vec3(x - other.x(), y - other.y(), z - other.z());
        }
    }

    /** Immutable affine pose with a target-space translation and rotation. */
    public record Pose(Vec3 translation, Matrix3 rotation) {
        public Pose {
            if (translation == null || rotation == null) {
                throw new IllegalArgumentException("pose translation and rotation are required");
            }
        }

        public static Pose identity() {
            return new Pose(new Vec3(0.0D, 0.0D, 0.0D), Matrix3.identity());
        }

        /** Returns parent * child, preserving the child's local pivot. */
        public Pose compose(Pose child) {
            if (child == null) {
                throw new IllegalArgumentException("child pose must not be null");
            }
            return new Pose(
                    translation.add(rotation.transform(child.translation())),
                    rotation.multiply(child.rotation()));
        }

        /** Converts this absolute pose into coordinates local to {@code parent}. */
        public Pose relativeTo(Pose parent) {
            if (parent == null) {
                throw new IllegalArgumentException("parent pose must not be null");
            }
            Matrix3 inverseParent = parent.rotation().inverse();
            return new Pose(
                    inverseParent.transform(translation.subtract(parent.translation())),
                    inverseParent.multiply(rotation));
        }
    }

    /** Small immutable 3x3 matrix used for the documented rotation conjugation. */
    public record Matrix3(double m00, double m01, double m02,
                          double m10, double m11, double m12,
                          double m20, double m21, double m22) {
        private static Matrix3 identity() {
            return new Matrix3(
                    1.0D, 0.0D, 0.0D,
                    0.0D, 1.0D, 0.0D,
                    0.0D, 0.0D, 1.0D);
        }

        private static Matrix3 eulerXyz(double pitchDegrees, double yawDegrees,
                                        double rollDegrees) {
            double p = Math.toRadians(pitchDegrees);
            double y = Math.toRadians(yawDegrees);
            double r = Math.toRadians(rollDegrees);
            double cp = Math.cos(p);
            double sp = Math.sin(p);
            double cy = Math.cos(y);
            double sy = Math.sin(y);
            double cr = Math.cos(r);
            double sr = Math.sin(r);
            Matrix3 rx = new Matrix3(1, 0, 0, 0, cp, -sp, 0, sp, cp);
            Matrix3 ry = new Matrix3(cy, 0, sy, 0, 1, 0, -sy, 0, cy);
            Matrix3 rz = new Matrix3(cr, -sr, 0, sr, cr, 0, 0, 0, 1);
            return rx.multiply(ry).multiply(rz);
        }

        public Vec3 transform(Vec3 vector) {
            if (vector == null) {
                throw new IllegalArgumentException("vector must not be null");
            }
            return new Vec3(
                    m00 * vector.x() + m01 * vector.y() + m02 * vector.z(),
                    m10 * vector.x() + m11 * vector.y() + m12 * vector.z(),
                    m20 * vector.x() + m21 * vector.y() + m22 * vector.z());
        }

        public Matrix3 multiply(Matrix3 other) {
            if (other == null) {
                throw new IllegalArgumentException("other matrix must not be null");
            }
            return new Matrix3(
                    m00 * other.m00 + m01 * other.m10 + m02 * other.m20,
                    m00 * other.m01 + m01 * other.m11 + m02 * other.m21,
                    m00 * other.m02 + m01 * other.m12 + m02 * other.m22,
                    m10 * other.m00 + m11 * other.m10 + m12 * other.m20,
                    m10 * other.m01 + m11 * other.m11 + m12 * other.m21,
                    m10 * other.m02 + m11 * other.m12 + m12 * other.m22,
                    m20 * other.m00 + m21 * other.m10 + m22 * other.m20,
                    m20 * other.m01 + m21 * other.m11 + m22 * other.m21,
                    m20 * other.m02 + m21 * other.m12 + m22 * other.m22);
        }

        public Matrix3 inverse() {
            double determinant = determinant();
            if (Math.abs(determinant) < 1.0E-12D) {
                throw new IllegalStateException("rotation matrix is not invertible");
            }
            return new Matrix3(
                    (m11 * m22 - m12 * m21) / determinant,
                    (m02 * m21 - m01 * m22) / determinant,
                    (m01 * m12 - m02 * m11) / determinant,
                    (m12 * m20 - m10 * m22) / determinant,
                    (m00 * m22 - m02 * m20) / determinant,
                    (m02 * m10 - m00 * m12) / determinant,
                    (m10 * m21 - m11 * m20) / determinant,
                    (m01 * m20 - m00 * m21) / determinant,
                    (m00 * m11 - m01 * m10) / determinant);
        }

        public Vec3 toEulerXyzDegrees() {
            double clamped = Math.max(-1.0D, Math.min(1.0D, m02));
            double yaw = Math.asin(clamped);
            double cosYaw = Math.cos(yaw);
            double pitch;
            double roll;
            if (Math.abs(cosYaw) > 1.0E-8D) {
                pitch = Math.atan2(-m12, m22);
                roll = Math.atan2(-m01, m00);
            } else {
                // The source assets are far from gimbal lock.  Keep a
                // deterministic fallback for malformed/future assets rather
                // than returning NaN to ModelTransform.
                pitch = 0.0D;
                roll = Math.atan2(m10, m11);
            }
            return new Vec3(Math.toDegrees(pitch), Math.toDegrees(yaw), Math.toDegrees(roll));
        }

        public double determinant() {
            return m00 * (m11 * m22 - m12 * m21)
                    - m01 * (m10 * m22 - m12 * m20)
                    + m02 * (m10 * m21 - m11 * m20);
        }
    }
}
