package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;

/** Pure model-unit to world-space transform for the boss proxy rig. */
public final class BossHitboxTransformPolicy {
    public static final double MODEL_UNITS_PER_BLOCK = BossHitboxProfile.MODEL_UNITS_PER_BLOCK;
    public static final double MIN_BOX_DIMENSION_BLOCKS = 0.02D;
    public static final double MAX_BOX_DIMENSION_BLOCKS = 8.0D;

    private BossHitboxTransformPolicy() {
    }

    public static Box transform(BossHitboxProfile.Part part, Anchor anchor,
                                PoseOffset pose) {
        if (part == null || anchor == null || pose == null) {
            throw new IllegalArgumentException("part, anchor, and pose are required");
        }
        Matrix3 poseRotation = Matrix3.eulerXyz(
                pose.pitchDegrees(), pose.yawDegrees(), pose.rollDegrees());
        Vec3 posePivot = new Vec3(part.posePivotModel().x(),
                part.posePivotModel().y(), part.posePivotModel().z());
        List<Vec3> corners = new ArrayList<>(8);
        double halfWidth = part.widthModel() / 2.0D;
        double halfHeight = part.heightModel() / 2.0D;
        double halfDepth = part.depthModel() / 2.0D;
        for (int x = -1; x <= 1; x += 2) {
            for (int y = -1; y <= 1; y += 2) {
                for (int z = -1; z <= 1; z += 2) {
                    Vec3 corner = new Vec3(
                            part.centerModel().x() + x * halfWidth,
                            part.centerModel().y() + y * halfHeight,
                            part.centerModel().z() + z * halfDepth);
                    Vec3 posed = poseRotation.transform(corner.subtract(posePivot))
                            .add(posePivot).add(new Vec3(
                            pose.translationModelX(), pose.translationModelY(),
                            pose.translationModelZ()));
                    corners.add(rotateAroundWorldYaw(posed.scale(1.0D / MODEL_UNITS_PER_BLOCK), anchor.yawDegrees()));
                }
            }
        }
        double minX = corners.stream().mapToDouble(Vec3::x).min().orElseThrow();
        double minY = corners.stream().mapToDouble(Vec3::y).min().orElseThrow();
        double minZ = corners.stream().mapToDouble(Vec3::z).min().orElseThrow();
        double maxX = corners.stream().mapToDouble(Vec3::x).max().orElseThrow();
        double maxY = corners.stream().mapToDouble(Vec3::y).max().orElseThrow();
        double maxZ = corners.stream().mapToDouble(Vec3::z).max().orElseThrow();
        Vec3 center = new Vec3(
                anchor.x() + (minX + maxX) / 2.0D,
                anchor.y() + (minY + maxY) / 2.0D,
                anchor.z() + (minZ + maxZ) / 2.0D);
        return new Box(part.id(), part.segmentIndex(), center,
                clamp(maxX - minX), clamp(maxY - minY), clamp(maxZ - minZ));
    }

    public static List<Box> transformAll(BossHitboxProfile profile, Anchor anchor,
                                         PoseOffset pose) {
        if (profile == null) {
            throw new IllegalArgumentException("profile is required");
        }
        List<Box> result = new ArrayList<>(profile.proxyCount());
        for (BossHitboxProfile.Part part : profile.parts()) {
            result.add(transform(part, anchor, pose));
        }
        return List.copyOf(result);
    }

    private static Vec3 rotateAroundWorldYaw(Vec3 localBlocks, double yawDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        return new Vec3(
                cos * localBlocks.x() - sin * localBlocks.z(),
                localBlocks.y(),
                sin * localBlocks.x() + cos * localBlocks.z());
    }

    private static double clamp(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("transformed hitbox dimension is not finite");
        }
        return Math.max(MIN_BOX_DIMENSION_BLOCKS,
                Math.min(MAX_BOX_DIMENSION_BLOCKS, value));
    }

    public record Anchor(double x, double y, double z, double yawDegrees) {
        public Anchor {
            requireFinite(x, "x");
            requireFinite(y, "y");
            requireFinite(z, "z");
            requireFinite(yawDegrees, "yawDegrees");
        }
    }

    /** Pose displacement/rotation in the same model-unit space as geometry.json. */
    public record PoseOffset(double translationModelX, double translationModelY,
                             double translationModelZ, double pitchDegrees,
                             double yawDegrees, double rollDegrees) {
        public static final PoseOffset NONE = new PoseOffset(0.0D, 0.0D, 0.0D,
                0.0D, 0.0D, 0.0D);

        public PoseOffset {
            requireFinite(translationModelX, "translationModelX");
            requireFinite(translationModelY, "translationModelY");
            requireFinite(translationModelZ, "translationModelZ");
            requireFinite(pitchDegrees, "pitchDegrees");
            requireFinite(yawDegrees, "yawDegrees");
            requireFinite(rollDegrees, "rollDegrees");
        }
    }

    public record Box(BossHitboxProfile.PartId partId, int segmentIndex,
                      Vec3 center, double width, double height, double depth) {
        public Box {
            if (partId == null || center == null) {
                throw new IllegalArgumentException("box part and center are required");
            }
            if (segmentIndex < 0) {
                throw new IllegalArgumentException("box segment index must not be negative");
            }
            requireFinite(width, "width");
            requireFinite(height, "height");
            requireFinite(depth, "depth");
            if (width <= 0.0D || height <= 0.0D || depth <= 0.0D) {
                throw new IllegalArgumentException("box dimensions must be positive");
            }
        }

        public double minX() {
            return center.x() - width / 2.0D;
        }

        public double maxX() {
            return center.x() + width / 2.0D;
        }

        public double minY() {
            return center.y() - height / 2.0D;
        }

        public double maxY() {
            return center.y() + height / 2.0D;
        }

        public double minZ() {
            return center.z() - depth / 2.0D;
        }

        public double maxZ() {
            return center.z() + depth / 2.0D;
        }
    }

    public record Vec3(double x, double y, double z) {
        private Vec3 subtract(Vec3 other) {
            return new Vec3(x - other.x(), y - other.y(), z - other.z());
        }

        private Vec3 add(Vec3 other) {
            return new Vec3(x + other.x(), y + other.y(), z + other.z());
        }

        private Vec3 scale(double factor) {
            return new Vec3(x * factor, y * factor, z * factor);
        }
    }

    private record Matrix3(double m00, double m01, double m02,
                           double m10, double m11, double m12,
                           double m20, double m21, double m22) {
        private static Matrix3 eulerXyz(double pitchDegrees, double yawDegrees,
                                        double rollDegrees) {
            double pitch = Math.toRadians(pitchDegrees);
            double yaw = Math.toRadians(yawDegrees);
            double roll = Math.toRadians(rollDegrees);
            double cp = Math.cos(pitch);
            double sp = Math.sin(pitch);
            double cy = Math.cos(yaw);
            double sy = Math.sin(yaw);
            double cr = Math.cos(roll);
            double sr = Math.sin(roll);
            Matrix3 rx = new Matrix3(1, 0, 0, 0, cp, -sp, 0, sp, cp);
            Matrix3 ry = new Matrix3(cy, 0, sy, 0, 1, 0, -sy, 0, cy);
            Matrix3 rz = new Matrix3(cr, -sr, 0, sr, cr, 0, 0, 0, 1);
            return rx.multiply(ry).multiply(rz);
        }

        private Vec3 transform(Vec3 vector) {
            return new Vec3(
                    m00 * vector.x() + m01 * vector.y() + m02 * vector.z(),
                    m10 * vector.x() + m11 * vector.y() + m12 * vector.z(),
                    m20 * vector.x() + m21 * vector.y() + m22 * vector.z());
        }

        private Matrix3 multiply(Matrix3 other) {
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
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
