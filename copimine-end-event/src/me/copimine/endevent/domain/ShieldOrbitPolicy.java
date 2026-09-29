package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;

/** Deterministic server-owned positions for the Last Seal guardian shields. */
public final class ShieldOrbitPolicy {
    /** Keep the four physical shield plates close enough to protect the boss body. */
    /** Four one-block plates need a little over 0.71 blocks of radius to clear. */
    public static final double ORBIT_RADIUS = 0.78D;
    public static final double TORSO_HEIGHT = 3.1D;
    public static final double BOB_HEIGHT = 0.18D;
    public static final long ORBIT_PERIOD_TICKS = 240L;

    private ShieldOrbitPolicy() {
    }

    public static List<Segment> positions(double bossX, double bossY, double bossZ,
                                          long tick, int livingGuardians) {
        if (!Double.isFinite(bossX) || !Double.isFinite(bossY) || !Double.isFinite(bossZ)
                || livingGuardians <= 0) {
            return List.of();
        }
        int count = Math.min(8, livingGuardians);
        long wrappedTick = Math.floorMod(tick, ORBIT_PERIOD_TICKS);
        double base = wrappedTick * (Math.PI * 2.0D / ORBIT_PERIOD_TICKS);
        List<Segment> segments = new ArrayList<>(count);
        for (int slot = 0; slot < count; slot++) {
            double angle = base + Math.PI * 2.0D * slot / count;
            segments.add(new Segment(slot,
                    bossX + Math.cos(angle) * ORBIT_RADIUS,
                    bossY + TORSO_HEIGHT + Math.sin(angle * 2.0D) * BOB_HEIGHT,
                    bossZ + Math.sin(angle) * ORBIT_RADIUS,
                    (float) angle));
        }
        return List.copyOf(segments);
    }

    public record Segment(int slot, double x, double y, double z, float yaw) {
        public boolean isFinite() {
            return slot >= 0 && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                    && Float.isFinite(yaw);
        }
    }
}
