package me.copimine.endevent.domain;

/** Small server-authored suspension motion around the stable prisoner anchor. */
public final class RitualSpherePresentationPolicy {
    public static final double HEIGHT_OFFSET = 6.2D;
    private RitualSpherePresentationPolicy() { }
    public static double centerHeightOffset(long tick) {
        return HEIGHT_OFFSET + suspensionOffset(tick);
    }
    public static int channelColor(int livingGuards, int assignedGuards) {
        if (assignedGuards < 1 || assignedGuards > 3) throw new IllegalArgumentException("invalid guard count");
        if (livingGuards <= 0) return 0xEC8CFF;
        if (livingGuards >= assignedGuards) return 0xD04BFF;
        return livingGuards * 3 <= assignedGuards ? 0xA36BFF : 0xA55BFF;
    }
    public static boolean insidePrisonerAnchor(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && x*x + y*y + z*z <= 0.25D;
    }
    public static boolean atSphereAnchor(double x, double y, double z,
                                          double anchorX, double anchorY, double anchorZ) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Double.isFinite(anchorX) || !Double.isFinite(anchorY) || !Double.isFinite(anchorZ)) return false;
        double dx = x - anchorX, dy = y - anchorY, dz = z - anchorZ;
        return dx*dx + dy*dy + dz*dz <= 0.025D * 0.025D;
    }
    public static double suspensionOffset(long tick) {
        if (tick < 0L) throw new IllegalArgumentException("negative presentation tick");
        return Math.sin((tick % 240L) * Math.PI * 2.0D / 240.0D) * 0.12D;
    }
}
