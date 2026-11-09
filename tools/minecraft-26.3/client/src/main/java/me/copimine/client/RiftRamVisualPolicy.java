package me.copimine.client;

/** Canonical visual ids for the Wave 6 Rift Ram, one per armour stage. */
public final class RiftRamVisualPolicy {
    public static final String ARMOR0_VISUAL = "END_RIFT_RAM_ARMOR0_V1";
    public static final String ARMOR1_VISUAL = "END_RIFT_RAM_ARMOR1_V1";
    public static final String ARMOR2_VISUAL = "END_RIFT_RAM_ARMOR2_V1";
    public static final String ARMOR3_VISUAL = "END_RIFT_RAM_ARMOR3_V1";
    public static final String ARMOR4_VISUAL = "END_RIFT_RAM_ARMOR4_V1";
    public static final String ARMOR5_VISUAL = "END_RIFT_RAM_ARMOR5_V1";

    private RiftRamVisualPolicy() {
    }

    /**
     * Maps a server-reported armour stage to its visual id.
     *
     * <p>The server sends the accepted Core impact count, so this is a pure
     * lookup with a clamped index. Clamping matters: a malformed or future
     * payload must fall back to the intact stage rather than render nothing,
     * because an invisible ravager reads to the player as a broken encounter.</p>
     */
    public static String visualForArmorStage(int stage) {
        return switch (Math.max(0, Math.min(5, stage))) {
            case 0 -> ARMOR0_VISUAL;
            case 1 -> ARMOR1_VISUAL;
            case 2 -> ARMOR2_VISUAL;
            case 3 -> ARMOR3_VISUAL;
            case 4 -> ARMOR4_VISUAL;
            default -> ARMOR5_VISUAL;
        };
    }

    public static boolean isRiftRamVisual(String visualId) {
        if (visualId == null) return false;
        String normalized = visualId.trim().toUpperCase(java.util.Locale.ROOT);
        return ARMOR0_VISUAL.equals(normalized) || ARMOR1_VISUAL.equals(normalized)
                || ARMOR2_VISUAL.equals(normalized) || ARMOR3_VISUAL.equals(normalized)
                || ARMOR4_VISUAL.equals(normalized) || ARMOR5_VISUAL.equals(normalized);
    }
}
