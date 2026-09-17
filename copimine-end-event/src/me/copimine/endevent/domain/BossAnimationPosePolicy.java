package me.copimine.endevent.domain;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Samples the generated server copy of the artist-authored boss animation.
 *
 * <p>The client samples Bedrock keyframes in seconds, with 20 animation
 * ticks per second. The generated table stores those same keyframes in ticks
 * so the authoritative hitbox update can use the event clock without loading
 * client classes or resources.</p>
 */
public final class BossAnimationPosePolicy {
    private static final Map<BossHitboxProfile.PartId, String> SOURCE_BONES =
            Map.of(
                    BossHitboxProfile.PartId.HEAD, "head",
                    BossHitboxProfile.PartId.CHEST, "body",
                    BossHitboxProfile.PartId.PELVIS, "body",
                    BossHitboxProfile.PartId.LEFT_UPPER_ARM, "left_hand",
                    BossHitboxProfile.PartId.LEFT_FOREARM, "left_hand_low",
                    BossHitboxProfile.PartId.RIGHT_UPPER_ARM, "right_hand",
                    BossHitboxProfile.PartId.RIGHT_FOREARM, "right_hand_low",
                    BossHitboxProfile.PartId.LEFT_LEG, "left_leg",
                    BossHitboxProfile.PartId.RIGHT_LEG, "right_leg");

    private BossAnimationPosePolicy() {
    }

    public static String sourceDigest() {
        return GeneratedBossAnimationPoses.SOURCE_DIGEST;
    }

    /**
     * Returns a complete immutable part map. Missing tracks and animations
     * deliberately resolve to bind pose rather than to guessed combat-state
     * offsets.
     */
    public static Map<BossHitboxProfile.PartId, BossHitboxTransformPolicy.PoseOffset> sample(
            String animationId, double elapsedTicks) {
        if (!Double.isFinite(elapsedTicks) || elapsedTicks < 0.0D) {
            throw new IllegalArgumentException("elapsedTicks must be finite and non-negative");
        }
        EnumMap<BossHitboxProfile.PartId, BossHitboxTransformPolicy.PoseOffset> result =
                new EnumMap<>(BossHitboxProfile.PartId.class);
        for (BossHitboxProfile.PartId partId : BossHitboxProfile.PartId.values()) {
            result.put(partId, BossHitboxTransformPolicy.PoseOffset.NONE);
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
        for (Map.Entry<BossHitboxProfile.PartId, String> entry : SOURCE_BONES.entrySet()) {
            GeneratedBossAnimationPoses.BoneTrack track = clip.bones().get(entry.getValue());
            if (track == null) {
                continue;
            }
            GeneratedBossAnimationPoses.Vec3 rotation = sample(track.rotation(), time);
            GeneratedBossAnimationPoses.Vec3 position = sample(track.position(), time);
            result.put(entry.getKey(), new BossHitboxTransformPolicy.PoseOffset(
                    component(position, 0), component(position, 1), component(position, 2),
                    component(rotation, 0), component(rotation, 1), component(rotation, 2)));
        }
        return Map.copyOf(result);
    }

    private static double component(GeneratedBossAnimationPoses.Vec3 vector, int axis) {
        if (vector == null) {
            return 0.0D;
        }
        return switch (axis) {
            case 0 -> vector.x();
            case 1 -> vector.y();
            case 2 -> vector.z();
            default -> throw new IllegalArgumentException("unknown vector axis: " + axis);
        };
    }

    private static GeneratedBossAnimationPoses.Vec3 sample(
            GeneratedBossAnimationPoses.Channel channel, double time) {
        if (channel == null || channel.frames().isEmpty()) {
            return null;
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
}
