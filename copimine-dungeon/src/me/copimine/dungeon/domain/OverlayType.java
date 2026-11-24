package me.copimine.dungeon.domain;

import java.util.Locale;

/**
 * Editor overlay categories with the exact colour + line style demanded by
 * spec OV4.3:
 *
 * <pre>
 * ROOM       cyan solid
 * TRIGGER    green dashed
 * GATE       amber double-line
 * SECRET     magenta dotted (builder-only reveal)
 * SPAWN      violet reticle
 * REWARD     gold diamond
 * REST       muted teal
 * PROTECTED  red cross-hatching (immutable blocks)
 * DRAFT      white/blue dashed (unsaved draft)
 * </pre>
 */
public enum OverlayType {
    ROOM(0x00E5FF, LineStyle.SOLID, "КОМНАТА"),
    TRIGGER(0x39FF14, LineStyle.DASHED, "ТРИГГЕР"),
    GATE(0xFFB300, LineStyle.DOUBLE, "ШЛЮЗ"),
    SECRET(0xFF00FF, LineStyle.DOTTED, "СЕКРЕТ"),
    SPAWN(0x8A2BE2, LineStyle.RETICLE, "СПАВН"),
    REWARD(0xFFD700, LineStyle.DIAMOND, "НАГРАДА"),
    REST(0x3AA6A0, LineStyle.SOLID, "ОТДЫХ"),
    PROTECTED(0xFF2D2D, LineStyle.CROSS_HATCH, "ЗАЩИЩЕНО"),
    DRAFT(0xFFFFFF, LineStyle.DRAFT_DASHED, "ЧЕРНОВИК");

    private final int color;
    private final LineStyle lineStyle;
    private final String label;

    OverlayType(int color, LineStyle lineStyle, String label) {
        this.color = color;
        this.lineStyle = lineStyle;
        this.label = label;
    }

    public int color() {
        return color;
    }

    public LineStyle lineStyle() {
        return lineStyle;
    }

    /** Short Russian label shown in the editor inspector. */
    public String label() {
        return label;
    }

    /** True when this overlay may only be shown to authorized builders. */
    public boolean builderOnly() {
        return this == SECRET || this == PROTECTED;
    }

    public static OverlayType fromToken(String token) {
        if (token == null) {
            return null;
        }
        String normalized = token.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals("DOORS")) {
            return GATE;
        }
        if (normalized.equals("SECRETS")) {
            return SECRET;
        }
        for (OverlayType type : values()) {
            if (type.name().equals(normalized)) {
                return type;
            }
        }
        return null;
    }
}
