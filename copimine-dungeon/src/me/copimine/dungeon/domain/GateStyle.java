package me.copimine.dungeon.domain;

import java.util.Locale;

/**
 * The six polished, physical gate/secret templates (spec OV4.5). Each style is
 * a reusable entrance/exit mechanism driven by a real server block mask; the
 * cinematic client model is presentation only and must never claim a doorway is
 * traversable while server collision still blocks it.
 */
public enum GateStyle {
    /** GATE-01: obsidian/End-stone/purpur pieces assemble into one solid door. */
    ASSEMBLING_SEAL("appearing"),
    /** GATE-02: genuine grille with bars that rise toward the ceiling. */
    RISING_PORTCULLIS("grille"),
    /** GATE-03: monolithic carved slab lifting out of the floor. */
    LIFTING_STONE_WALL("lift"),
    /** GATE-04: two symmetrical panels sliding into side recesses. */
    SPLIT_SLIDING_WALL("slide"),
    /** GATE-05: cracked slab splitting into bounded client-only debris. */
    COLLAPSING_BARRIER("collapse"),
    /** GATE-06: ordinary wall material concealing a passage; builder-only reveal. */
    SECRET_DISGUISED("secret");

    private final String token;

    GateStyle(String token) {
        this.token = token;
    }

    /** Stable lowercase command token used by {@code /cmdungeon gate style}. */
    public String token() {
        return token;
    }

    public static GateStyle fromToken(String token) {
        if (token == null) {
            return null;
        }
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        for (GateStyle style : values()) {
            if (style.token.equals(normalized) || style.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return style;
            }
        }
        return null;
    }

    public static String tokens() {
        StringBuilder builder = new StringBuilder();
        for (GateStyle style : values()) {
            if (builder.length() > 0) {
                builder.append('|');
            }
            builder.append(style.token);
        }
        return builder.toString();
    }

    /** A secret is a special gate category with concealment + unlock rule. */
    public boolean isSecretFamily() {
        return this == SECRET_DISGUISED;
    }
}
