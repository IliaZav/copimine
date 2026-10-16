package me.copimine.endevent.domain;

/** Deterministic rings for guardian-owned visuals and cast fallback roots. */
public final class TentacleGuardianLayoutPolicy {
    public static final double PERMANENT_RING_RADIUS = 9.0D;
    public static final double TEMPORARY_FALLBACK_RING_RADIUS = 6.5D;

    private TentacleGuardianLayoutPolicy() {
    }

    public static Offset permanentOffset(int slot, int count) {
        return ringOffset(slot, count, PERMANENT_RING_RADIUS);
    }

    public static Offset temporaryFallbackOffset(int slot, int count) {
        return ringOffset(slot, count, TEMPORARY_FALLBACK_RING_RADIUS);
    }

    private static Offset ringOffset(int slot, int count, double radius) {
        if (count <= 0 || !Double.isFinite(radius) || radius <= 0.0D) {
            return new Offset(0.0D, 0.0D);
        }
        int safeSlot = Math.floorMod(slot, count);
        double angle = -Math.PI / 2.0D + (Math.PI * 2.0D * safeSlot / count);
        return new Offset(Math.cos(angle) * radius, Math.sin(angle) * radius);
    }

    public record Offset(double x, double z) {
        public Offset {
            if (!Double.isFinite(x) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("tentacle layout offset must be finite");
            }
        }
    }
}
