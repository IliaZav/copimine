package me.copimine.endevent.domain;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Samples the generated server copy of the artist-authored boss animation.
 *
 * <p>The client model is a Bedrock bone hierarchy.  A child track is not an
 * independent world transform: it is composed after every animated parent.
 * This policy keeps that rule on the server by evaluating the generated
 * pivot/parent metadata into one affine transform for each hitbox segment.
 * The result is converted to the existing {@link BossHitboxTransformPolicy}
 * pose shape without loading client classes or resources.</p>
 */
public final class BossAnimationPosePolicy {
    private static final BossHitboxProfile PROFILE = BossHitboxProfile.canonical();
    private static final Map<BossHitboxProfile.PartKey, String> SOURCE_BONES = Map.ofEntries(
            Map.entry(new BossHitboxProfile.PartKey(BossHitboxProfile.PartId.HEAD, 0), "head"),
            Map.entry(new BossHitboxProfile.PartKey(BossHitboxProfile.PartId.CHEST, 0), "body"),
            Map.entry(new BossHitboxProfile.PartKey(BossHitboxProfile.PartId.PELVIS, 0), "body"),
            Map.entry(new BossHitboxProfile.PartKey(BossHitboxProfile.PartId.LEFT_UPPER_ARM, 0), "left_hand"),
            Map.entry(new BossHitboxProfile.PartKey(BossHitboxProfile.PartId.LEFT_FOREARM, 0), "left_hand_low"),
            Map.entry(new BossHitboxProfile.PartKey(BossHitboxProfile.PartId.RIGHT_UPPER_ARM, 0), "right_hand"),
            Map.entry(new BossHitboxProfile.PartKey(BossHitboxProfile.PartId.RIGHT_FOREARM, 0), "right_hand_low"),
            Map.entry(new BossHitboxProfile.PartKey(BossHitboxProfile.PartId.LEFT_LEG, 0), "group6"),
            Map.entry(new BossHitboxProfile.PartKey(BossHitboxProfile.PartId.LEFT_LEG, 1), "group2"),
            Map.entry(new BossHitboxProfile.PartKey(BossHitboxProfile.PartId.RIGHT_LEG, 0), "group5"),
            Map.entry(new BossHitboxProfile.PartKey(BossHitboxProfile.PartId.RIGHT_LEG, 1), "group"));

    private BossAnimationPosePolicy() {
    }

    public static String sourceDigest() {
        return GeneratedBossAnimationPoses.SOURCE_DIGEST;
    }

    /**
     * Returns one pose for every canonical hitbox segment.  Missing tracks,
     * missing clips, and malformed animation ids deliberately resolve to the
     * bind pose rather than to guessed combat-state offsets.
     */
    public static Map<BossHitboxProfile.PartKey, BossHitboxTransformPolicy.PoseOffset> sampleSegments(
            String animationId, double elapsedTicks) {
        if (!Double.isFinite(elapsedTicks) || elapsedTicks < 0.0D) {
            throw new IllegalArgumentException("elapsedTicks must be finite and non-negative");
        }
        LinkedHashMap<BossHitboxProfile.PartKey, BossHitboxTransformPolicy.PoseOffset> result =
                new LinkedHashMap<>();
        for (BossHitboxProfile.Part part : PROFILE.parts()) {
            result.put(new BossHitboxProfile.PartKey(part.id(), part.segmentIndex()),
                    BossHitboxTransformPolicy.PoseOffset.NONE);
        }

        BossAnimationId canonical = BossAnimationId.fromWire(animationId);
        GeneratedBossAnimationPoses.Clip clip =
                GeneratedBossAnimationPoses.clip(canonical.wireId());
        if (clip == null) {
            return Map.copyOf(result);
        }
        double time = clip.loop()
                ? elapsedTicks % clip.lengthTicks()
                : Math.min(elapsedTicks, clip.lengthTicks());
        Map<String, Affine> cache = new HashMap<>();
        for (Map.Entry<BossHitboxProfile.PartKey, String> entry : SOURCE_BONES.entrySet()) {
            BossHitboxProfile.Part part = partFor(entry.getKey());
            Affine composed = composedTransform(entry.getValue(), clip, time, cache, new HashSet<>());
            if (part != null && composed != null) {
                result.put(entry.getKey(), toPoseOffset(part, composed));
            }
        }
        return Map.copyOf(result);
    }

    /**
     * Compatibility view for callers that only need the first segment of each
     * role.  Runtime hitbox code uses {@link #sampleSegments(String, double)} so
     * repeated leg roles cannot overwrite one another.
     */
    public static Map<BossHitboxProfile.PartId, BossHitboxTransformPolicy.PoseOffset> sample(
            String animationId, double elapsedTicks) {
        Map<BossHitboxProfile.PartKey, BossHitboxTransformPolicy.PoseOffset> segments =
                sampleSegments(animationId, elapsedTicks);
        EnumMap<BossHitboxProfile.PartId, BossHitboxTransformPolicy.PoseOffset> result =
                new EnumMap<>(BossHitboxProfile.PartId.class);
        for (Map.Entry<BossHitboxProfile.PartKey, BossHitboxTransformPolicy.PoseOffset> entry
                : segments.entrySet()) {
            result.putIfAbsent(entry.getKey().partId(), entry.getValue());
        }
        for (BossHitboxProfile.PartId partId : BossHitboxProfile.PartId.values()) {
            result.putIfAbsent(partId, BossHitboxTransformPolicy.PoseOffset.NONE);
        }
        return Map.copyOf(result);
    }

    private static BossHitboxProfile.Part partFor(BossHitboxProfile.PartKey key) {
        for (BossHitboxProfile.Part part : PROFILE.parts()) {
            if (new BossHitboxProfile.PartKey(part.id(), part.segmentIndex()).equals(key)) {
                return part;
            }
        }
        return null;
    }

    private static Affine composedTransform(
            String boneName,
            GeneratedBossAnimationPoses.Clip clip,
            double time,
            Map<String, Affine> cache,
            Set<String> visiting) {
        Affine cached = cache.get(boneName);
        if (cached != null) {
            return cached;
        }
        GeneratedBossAnimationPoses.BoneDefinition definition =
                GeneratedBossAnimationPoses.bone(boneName);
        if (definition == null || !visiting.add(boneName)) {
            return null;
        }
        GeneratedBossAnimationPoses.BoneTrack track = clip.bones().get(boneName);
        GeneratedBossAnimationPoses.Vec3 rotation = sample(
                track == null ? null : track.rotation(), time);
        GeneratedBossAnimationPoses.Vec3 position = sample(
                track == null ? null : track.position(), time);
        BossOrientedHitboxPolicy.Matrix3 localRotation = BossOrientedHitboxPolicy.Matrix3.euler(
                new BossOrientedHitboxPolicy.Euler(
                        component(rotation, 0), component(rotation, 1), component(rotation, 2)));
        BossOrientedHitboxPolicy.Vec3 pivot = toOriented(definition.pivot());
        BossOrientedHitboxPolicy.Vec3 localTranslation = pivot
                .subtract(localRotation.transform(pivot))
                .add(toOriented(position));
        Affine local = new Affine(localRotation, localTranslation);
        Affine result;
        if (definition.parent() == null) {
            result = local;
        } else {
            Affine parent = composedTransform(definition.parent(), clip, time, cache, visiting);
            result = parent == null ? local : parent.compose(local);
        }
        visiting.remove(boneName);
        cache.put(boneName, result);
        return result;
    }

    private static BossHitboxTransformPolicy.PoseOffset toPoseOffset(
            BossHitboxProfile.Part part, Affine transform) {
        BossOrientedHitboxPolicy.Euler euler = toEuler(transform.linear());
        BossOrientedHitboxPolicy.Vec3 pivot = new BossOrientedHitboxPolicy.Vec3(
                part.posePivotModel().x(), part.posePivotModel().y(), part.posePivotModel().z());
        BossOrientedHitboxPolicy.Vec3 movedPivot = transform.apply(pivot);
        BossOrientedHitboxPolicy.Vec3 translation = movedPivot.subtract(pivot);
        return new BossHitboxTransformPolicy.PoseOffset(
                translation.x(), translation.y(), translation.z(),
                euler.pitchDegrees(), euler.yawDegrees(), euler.rollDegrees());
    }

    private static BossOrientedHitboxPolicy.Euler toEuler(BossOrientedHitboxPolicy.Matrix3 matrix) {
        double pitch = Math.atan2(-matrix.m12(),
                Math.hypot(matrix.m02(), matrix.m22()));
        double cosinePitch = Math.cos(pitch);
        double yaw;
        double roll;
        if (Math.abs(cosinePitch) > 1.0E-8D) {
            yaw = Math.atan2(matrix.m02(), matrix.m22() / cosinePitch);
            roll = Math.atan2(-matrix.m01(), matrix.m00());
        } else {
            yaw = 0.0D;
            roll = Math.atan2(matrix.m10(), matrix.m11());
        }
        return new BossOrientedHitboxPolicy.Euler(
                Math.toDegrees(pitch), Math.toDegrees(yaw), Math.toDegrees(roll));
    }

    private static double component(GeneratedBossAnimationPoses.Vec3 vector, int axis) {
        return switch (axis) {
            case 0 -> vector.x();
            case 1 -> vector.y();
            case 2 -> vector.z();
            default -> throw new IllegalArgumentException("unknown vector axis: " + axis);
        };
    }

    private static BossOrientedHitboxPolicy.Vec3 toOriented(
            GeneratedBossAnimationPoses.Vec3 vector) {
        return new BossOrientedHitboxPolicy.Vec3(vector.x(), vector.y(), vector.z());
    }

    private static GeneratedBossAnimationPoses.Vec3 sample(
            GeneratedBossAnimationPoses.Channel channel, double time) {
        if (channel == null || channel.frames().isEmpty()) {
            return new GeneratedBossAnimationPoses.Vec3(0.0D, 0.0D, 0.0D);
        }
        List<GeneratedBossAnimationPoses.Keyframe> frames = channel.frames();
        if (time <= frames.get(0).timeTicks()) {
            return frames.get(0).vector();
        }
        GeneratedBossAnimationPoses.Keyframe last = frames.get(frames.size() - 1);
        if (time >= last.timeTicks()) {
            return last.vector();
        }
        for (int index = 1; index < frames.size(); index++) {
            GeneratedBossAnimationPoses.Keyframe next = frames.get(index);
            if (time <= next.timeTicks()) {
                GeneratedBossAnimationPoses.Keyframe previous = frames.get(index - 1);
                double span = next.timeTicks() - previous.timeTicks();
                double amount = span <= 0.0D ? 1.0D
                        : (time - previous.timeTicks()) / span;
                return lerp(previous.vector(), next.vector(), amount);
            }
        }
        return last.vector();
    }

    private static GeneratedBossAnimationPoses.Vec3 lerp(
            GeneratedBossAnimationPoses.Vec3 first,
            GeneratedBossAnimationPoses.Vec3 second,
            double amount) {
        return new GeneratedBossAnimationPoses.Vec3(
                first.x() + (second.x() - first.x()) * amount,
                first.y() + (second.y() - first.y()) * amount,
                first.z() + (second.z() - first.z()) * amount);
    }

    private record Affine(BossOrientedHitboxPolicy.Matrix3 linear,
                          BossOrientedHitboxPolicy.Vec3 translation) {
        private Affine {
            if (linear == null || translation == null) {
                throw new IllegalArgumentException("affine transform is required");
            }
        }

        private BossOrientedHitboxPolicy.Vec3 apply(BossOrientedHitboxPolicy.Vec3 point) {
            return linear.transform(point).add(translation);
        }

        private Affine compose(Affine child) {
            return new Affine(linear.multiply(child.linear),
                    linear.transform(child.translation).add(translation));
        }
    }
}
