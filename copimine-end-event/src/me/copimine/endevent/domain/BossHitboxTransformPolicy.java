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
        return transformWithPose(part, anchor, BossHitboxPose.fromOffset(pose));
    }

    /** Applies a lossless matrix pose for the authoritative animated rig. */
    public static Box transformWithPose(BossHitboxProfile.Part part, Anchor anchor,
                                        BossHitboxPose pose) {
        if (part == null || anchor == null || pose == null) {
            throw new IllegalArgumentException("part, anchor, and pose are required");
        }
        BossOrientedHitboxPolicy.Matrix3 poseRotation = pose.rotation();
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
                    BossOrientedHitboxPolicy.Vec3 posed = poseRotation.transform(
                                    toOriented(corner.subtract(posePivot)))
                            .add(toOriented(posePivot))
                            .add(new BossOrientedHitboxPolicy.Vec3(
                                    pose.translationModelX(), pose.translationModelY(),
                                    pose.translationModelZ()));
                    BossOrientedHitboxPolicy.Vec3 world =
                            BossOrientedHitboxPolicy.Matrix3.rotationY(anchor.yawDegrees())
                                    .transform(posed.scale(1.0D / MODEL_UNITS_PER_BLOCK));
                    corners.add(new Vec3(world.x(), world.y(), world.z()));
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

    private static BossOrientedHitboxPolicy.Vec3 toOriented(Vec3 vector) {
        return new BossOrientedHitboxPolicy.Vec3(vector.x(), vector.y(), vector.z());
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
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
