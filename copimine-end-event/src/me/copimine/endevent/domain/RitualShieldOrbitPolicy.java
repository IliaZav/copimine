package me.copimine.endevent.domain;

/** Stable guard slots retain their own orbit when another guard dies. */
public final class RitualShieldOrbitPolicy {
    private RitualShieldOrbitPolicy() { }
    public static Pose pose(int guardSlot, int originalGuardCount, long tick) {
        if (originalGuardCount < 1 || originalGuardCount > 3
                || guardSlot < 0 || guardSlot >= originalGuardCount || tick < 0L)
            throw new IllegalArgumentException("invalid ritual shield orbit");
        double angle = guardSlot * Math.PI * 2.0 / originalGuardCount + tick * 0.025;
        return new Pose(Math.cos(angle), 1.5 + Math.sin(angle * 1.5) * 0.15,
                Math.sin(angle), 90.0 - Math.toDegrees(angle));
    }
    public record Pose(double x, double y, double z, double yawDegrees) { }
}
