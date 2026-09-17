package me.copimine.endevent.domain;

import java.util.OptionalDouble;

/** Exact finite-ray intersection against an animated boss oriented box. */
public final class BossOrientedHitboxPolicy {
    private static final double MODEL_UNITS_PER_BLOCK = BossHitboxProfile.MODEL_UNITS_PER_BLOCK;
    private static final double PARALLEL_EPSILON = 1.0E-10D;
    private static final double ORTHONORMAL_EPSILON = 1.0E-8D;

    private BossOrientedHitboxPolicy() {
    }

    /**
     * Converts one model-profile envelope into a world-space OBB.  The
     * profile dimensions remain local half-extents; only its pose pivot,
     * authored pose, and boss body yaw are applied to the box frame.
     */
    public static OrientedBox fromPart(BossHitboxProfile.Part part,
                                       BossHitboxTransformPolicy.Anchor anchor,
                                       BossHitboxTransformPolicy.PoseOffset pose) {
        if (part == null || anchor == null || pose == null) {
            throw new IllegalArgumentException("part, anchor, and pose are required");
        }
        return fromPartWithPose(part, anchor, BossHitboxPose.fromOffset(pose));
    }

    /** Builds the OBB from the lossless matrix pose used by animated hitboxes. */
    public static OrientedBox fromPartWithPose(BossHitboxProfile.Part part,
                                               BossHitboxTransformPolicy.Anchor anchor,
                                               BossHitboxPose pose) {
        if (part == null || anchor == null || pose == null) {
            throw new IllegalArgumentException("part, anchor, and pose are required");
        }
        Matrix3 poseRotation = pose.rotation();
        Matrix3 bodyYaw = Matrix3.rotationY(anchor.yawDegrees());
        Vec3 pivot = new Vec3(part.posePivotModel().x(), part.posePivotModel().y(),
                part.posePivotModel().z());
        Vec3 modelCenter = new Vec3(part.centerModel().x(), part.centerModel().y(),
                part.centerModel().z())
                .subtract(pivot);
        modelCenter = poseRotation.transform(modelCenter).add(pivot).add(new Vec3(
                pose.translationModelX(), pose.translationModelY(), pose.translationModelZ()));
        Vec3 worldOffset = bodyYaw.transform(modelCenter.scale(1.0D / MODEL_UNITS_PER_BLOCK));
        Vec3 worldCenter = new Vec3(anchor.x() + worldOffset.x(),
                anchor.y() + worldOffset.y(), anchor.z() + worldOffset.z());
        Vec3 halfExtents = new Vec3(part.widthModel() / (2.0D * MODEL_UNITS_PER_BLOCK),
                part.heightModel() / (2.0D * MODEL_UNITS_PER_BLOCK),
                part.depthModel() / (2.0D * MODEL_UNITS_PER_BLOCK));
        return new OrientedBox(worldCenter, halfExtents, bodyYaw.multiply(poseRotation));
    }

    /**
     * Returns the nearest non-negative distance along the finite ray, or an
     * empty result when the ray misses or the requested range is invalid.
     */
    public static OptionalDouble nearestHitDistance(Ray ray, OrientedBox box,
                                                    double maxDistance) {
        if (ray == null || box == null || !Double.isFinite(maxDistance)
                || maxDistance < 0.0D) {
            return OptionalDouble.empty();
        }
        Matrix3 inverse = box.orientation().transpose();
        Vec3 localOrigin = inverse.transform(ray.origin().subtract(box.center()));
        Vec3 localDirection = inverse.transform(ray.direction());
        double tMin = 0.0D;
        double tMax = maxDistance;
        double[] origins = {localOrigin.x(), localOrigin.y(), localOrigin.z()};
        double[] directions = {localDirection.x(), localDirection.y(), localDirection.z()};
        double[] halfExtents = {box.halfExtents().x(), box.halfExtents().y(),
                box.halfExtents().z()};
        for (int axis = 0; axis < 3; axis++) {
            double origin = origins[axis];
            double direction = directions[axis];
            double halfExtent = halfExtents[axis];
            if (Math.abs(direction) <= PARALLEL_EPSILON) {
                if (origin < -halfExtent || origin > halfExtent) {
                    return OptionalDouble.empty();
                }
                continue;
            }
            double near = (-halfExtent - origin) / direction;
            double far = (halfExtent - origin) / direction;
            if (near > far) {
                double swap = near;
                near = far;
                far = swap;
            }
            tMin = Math.max(tMin, near);
            tMax = Math.min(tMax, far);
            if (tMin > tMax) {
                return OptionalDouble.empty();
            }
        }
        return tMax >= 0.0D && tMin <= maxDistance
                ? OptionalDouble.of(tMin) : OptionalDouble.empty();
    }

    public record Vec3(double x, double y, double z) {
        public Vec3 {
            requireFinite(x, "x");
            requireFinite(y, "y");
            requireFinite(z, "z");
        }

        public Vec3 add(Vec3 other) {
            requireOther(other);
            return new Vec3(x + other.x(), y + other.y(), z + other.z());
        }

        public Vec3 subtract(Vec3 other) {
            requireOther(other);
            return new Vec3(x - other.x(), y - other.y(), z - other.z());
        }

        public Vec3 scale(double factor) {
            requireFinite(factor, "factor");
            return new Vec3(x * factor, y * factor, z * factor);
        }

        public double dot(Vec3 other) {
            requireOther(other);
            return x * other.x() + y * other.y() + z * other.z();
        }

        public double length() {
            return Math.sqrt(dot(this));
        }

        private static void requireOther(Vec3 other) {
            if (other == null) {
                throw new IllegalArgumentException("vector is required");
            }
        }
    }

    /** A normalized ray; maxDistance is therefore measured in blocks. */
    public record Ray(Vec3 origin, Vec3 direction) {
        public Ray {
            if (origin == null || direction == null) {
                throw new IllegalArgumentException("ray origin and direction are required");
            }
            double length = direction.length();
            if (!Double.isFinite(length) || length <= PARALLEL_EPSILON) {
                throw new IllegalArgumentException("ray direction must be finite and non-zero");
            }
            direction = direction.scale(1.0D / length);
        }
    }

    public record Euler(double pitchDegrees, double yawDegrees, double rollDegrees) {
        public Euler {
            requireFinite(pitchDegrees, "pitchDegrees");
            requireFinite(yawDegrees, "yawDegrees");
            requireFinite(rollDegrees, "rollDegrees");
        }
    }

    public record OrientedBox(Vec3 center, Vec3 halfExtents, Matrix3 orientation) {
        public OrientedBox(Vec3 center, Vec3 halfExtents, Euler rotation) {
            this(center, halfExtents, Matrix3.euler(rotation));
        }

        public OrientedBox {
            if (center == null || halfExtents == null || orientation == null) {
                throw new IllegalArgumentException("OBB center, extents, and orientation are required");
            }
            if (halfExtents.x() <= 0.0D || halfExtents.y() <= 0.0D
                    || halfExtents.z() <= 0.0D) {
                throw new IllegalArgumentException("OBB half-extents must be positive");
            }
            if (!orientation.isOrthonormal()) {
                throw new IllegalArgumentException("OBB orientation must be orthonormal");
            }
        }
    }

    /** Immutable 3x3 rotation matrix used for world/local frame transforms. */
    public record Matrix3(double m00, double m01, double m02,
                          double m10, double m11, double m12,
                          double m20, double m21, double m22) {
        public Matrix3 {
            requireFinite(m00, "m00");
            requireFinite(m01, "m01");
            requireFinite(m02, "m02");
            requireFinite(m10, "m10");
            requireFinite(m11, "m11");
            requireFinite(m12, "m12");
            requireFinite(m20, "m20");
            requireFinite(m21, "m21");
            requireFinite(m22, "m22");
        }

        public static Matrix3 identity() {
            return new Matrix3(1.0D, 0.0D, 0.0D,
                    0.0D, 1.0D, 0.0D,
                    0.0D, 0.0D, 1.0D);
        }

        public static Matrix3 rotationY(double degrees) {
            requireFinite(degrees, "degrees");
            double radians = Math.toRadians(degrees);
            double cosine = Math.cos(radians);
            double sine = Math.sin(radians);
            // Bukkit entity yaw uses the Minecraft world convention: +90
            // degrees maps local X to world +Z and local Z to world -X.
            // Keep this separate from Matrix3.euler's authored model-space
            // Y rotation, whose sign follows the Bedrock animation data.
            return new Matrix3(cosine, 0.0D, -sine,
                    0.0D, 1.0D, 0.0D,
                    sine, 0.0D, cosine);
        }

        public static Matrix3 euler(Euler euler) {
            if (euler == null) {
                throw new IllegalArgumentException("Euler rotation is required");
            }
            double pitch = Math.toRadians(euler.pitchDegrees());
            double yaw = Math.toRadians(euler.yawDegrees());
            double roll = Math.toRadians(euler.rollDegrees());
            double cp = Math.cos(pitch);
            double sp = Math.sin(pitch);
            double cy = Math.cos(yaw);
            double sy = Math.sin(yaw);
            double cr = Math.cos(roll);
            double sr = Math.sin(roll);
            Matrix3 x = new Matrix3(1.0D, 0.0D, 0.0D,
                    0.0D, cp, -sp, 0.0D, sp, cp);
            Matrix3 y = new Matrix3(cy, 0.0D, sy,
                    0.0D, 1.0D, 0.0D, -sy, 0.0D, cy);
            Matrix3 z = new Matrix3(cr, -sr, 0.0D,
                    sr, cr, 0.0D, 0.0D, 0.0D, 1.0D);
            return x.multiply(y).multiply(z);
        }

        public Vec3 transform(Vec3 vector) {
            if (vector == null) {
                throw new IllegalArgumentException("vector is required");
            }
            return new Vec3(
                    m00 * vector.x() + m01 * vector.y() + m02 * vector.z(),
                    m10 * vector.x() + m11 * vector.y() + m12 * vector.z(),
                    m20 * vector.x() + m21 * vector.y() + m22 * vector.z());
        }

        public Matrix3 transpose() {
            return new Matrix3(m00, m10, m20,
                    m01, m11, m21,
                    m02, m12, m22);
        }

        public Matrix3 multiply(Matrix3 other) {
            if (other == null) {
                throw new IllegalArgumentException("matrix is required");
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

        public boolean isOrthonormal() {
            Vec3 first = new Vec3(m00, m10, m20);
            Vec3 second = new Vec3(m01, m11, m21);
            Vec3 third = new Vec3(m02, m12, m22);
            return close(first.dot(first), 1.0D)
                    && close(second.dot(second), 1.0D)
                    && close(third.dot(third), 1.0D)
                    && close(first.dot(second), 0.0D)
                    && close(first.dot(third), 0.0D)
                    && close(second.dot(third), 0.0D);
        }

        private static boolean close(double actual, double expected) {
            return Math.abs(actual - expected) <= ORTHONORMAL_EPSILON;
        }
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
