package me.copimine.endevent.domain;

/** Small server-authored suspension motion around the stable prisoner anchor. */
public final class RitualSpherePresentationPolicy {
    public static final double HEIGHT_OFFSET = 6.2D;
    /** Body centre inside the raised shell; deliberately static while the shell bobs. */
    public static final double PRISONER_HEIGHT_OFFSET = HEIGHT_OFFSET - 0.9D;
    private RitualSpherePresentationPolicy() { }
    public static double centerHeightOffset(long tick) {
        return HEIGHT_OFFSET + suspensionOffset(tick);
    }
    /**
     * The captive is held at a fixed height. Deriving the anchor from the
     * animated shell would move it every tick and teleport the prisoner in a
     * correction loop.
     */
    public static double prisonerHeightOffset() {
        return PRISONER_HEIGHT_OFFSET;
    }
    public static int channelColor(int livingGuards, int assignedGuards) {
        if (assignedGuards < 1 || assignedGuards > 3) throw new IllegalArgumentException("invalid guard count");
        if (livingGuards <= 0) return 0xEC8CFF;
        if (livingGuards >= assignedGuards) return 0xD04BFF;
        return livingGuards * 3 <= assignedGuards ? 0xA36BFF : 0xA55BFF;
    }
    /**
     * The sphere shows its own damage. Each mage death drains it visibly: the
     * hue cools from violet toward a deep angry crimson and the shell grows a
     * crack stage, so a player inside the sphere can read progress at a glance
     * without any label. Deaths beyond the wheel clamp to the final stage.
     */
    public static final int MAX_CRACK_STAGE = 5;

    /** Body colour of the sphere for {@code deaths} mages already destroyed. */
    public static int sphereBodyColor(int deaths) {
        return switch (Math.max(0, Math.min(MAX_CRACK_STAGE, deaths))) {
            case 0 -> 0xD04BFF;
            case 1 -> 0xC13BE8;
            case 2 -> 0xA82BD1;
            case 3 -> 0x8E1FB0;
            case 4 -> 0x74158C;
            default -> 0x5A0C6B;
        };
    }

    /** How many crack bands the shell publishes for {@code deaths} mages destroyed. */
    public static int crackStage(int deaths) {
        return Math.max(0, Math.min(MAX_CRACK_STAGE, deaths));
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
