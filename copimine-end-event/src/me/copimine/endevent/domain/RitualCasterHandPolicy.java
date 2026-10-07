package me.copimine.endevent.domain;

import java.util.List;

/** Raised Enderman palm offsets after the actual limb and renderer transforms. */
public final class RitualCasterHandPolicy {
    private RitualCasterHandPolicy() { }

    public static List<Offset> hands(double bodyYawDegrees, double pulse) {
        if (!Double.isFinite(bodyYawDegrees) || !Double.isFinite(pulse))
            throw new IllegalArgumentException("hand pose must be finite");
        double sway = Math.max(-1.0D, Math.min(1.0D, pulse));
        return List.of(hand(-5.0D, 0.18D, -2.62D - sway * 0.035D, bodyYawDegrees),
                hand(5.0D, -0.18D, -2.62D + sway * 0.035D, bodyYawDegrees));
    }

    private static Offset hand(double pivotX, double roll, double pitch, double yawDegrees) {
        // ModelPart rotates Z then X. The palm is half a texel inside the
        // 30-pixel arm end, at y=27.5 relative to its (-12) shoulder pivot.
        double lateral = (pivotX - Math.sin(roll) * 27.5D) / 16.0D;
        double rotatedY = Math.cos(roll) * 27.5D;
        double height = 1.501D - (-12.0D + Math.cos(pitch) * rotatedY) / 16.0D;
        double forward = -Math.sin(pitch) * rotatedY / 16.0D;
        double yaw = Math.toRadians(yawDegrees);
        return new Offset(lateral * Math.cos(yaw) - forward * Math.sin(yaw), height,
                lateral * Math.sin(yaw) + forward * Math.cos(yaw));
    }

    public record Offset(double x, double y, double z) { }
}
