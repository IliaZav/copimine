package me.copimine.endevent.domain;

/**
 * Lossless model-space pose used by the animated boss hitbox rig.
 *
 * <p>A pose is deliberately represented by its rotation matrix instead of
 * round-tripping a composed parent/child rotation through Euler angles.  The
 * renderer-facing {@link BossHitboxTransformPolicy.PoseOffset} remains
 * available for legacy callers, but the authoritative OBB path consumes this
 * type directly.</p>
 */
public record BossHitboxPose(double translationModelX, double translationModelY,
                             double translationModelZ,
                             BossOrientedHitboxPolicy.Matrix3 rotation) {
    public static final BossHitboxPose NONE = new BossHitboxPose(
            0.0D, 0.0D, 0.0D, BossOrientedHitboxPolicy.Matrix3.identity());

    public BossHitboxPose {
        if (!Double.isFinite(translationModelX)
                || !Double.isFinite(translationModelY)
                || !Double.isFinite(translationModelZ)
                || rotation == null) {
            throw new IllegalArgumentException("boss hitbox pose must be finite and complete");
        }
        if (!rotation.isOrthonormal()) {
            throw new IllegalArgumentException("boss hitbox pose rotation must be orthonormal");
        }
    }

    public static BossHitboxPose fromOffset(BossHitboxTransformPolicy.PoseOffset offset) {
        if (offset == null) {
            throw new IllegalArgumentException("pose offset is required");
        }
        return new BossHitboxPose(offset.translationModelX(), offset.translationModelY(),
                offset.translationModelZ(), BossOrientedHitboxPolicy.Matrix3.euler(
                        new BossOrientedHitboxPolicy.Euler(offset.pitchDegrees(),
                                offset.yawDegrees(), offset.rollDegrees())));
    }
}
