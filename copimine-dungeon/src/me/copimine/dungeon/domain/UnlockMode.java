package me.copimine.dungeon.domain;

import java.util.Locale;

/**
 * Allowed gate unlock modes (spec OV4.5): auto after clear, manual interaction
 * only after clear, or a specific verified milestone (e.g. Guardian victory).
 */
public enum UnlockMode {
    ON_ROOM_CLEAR("on_clear"),
    MANUAL_AFTER_ROOM_CLEAR("manual_after_clear"),
    ON_SPECIFIC_MILESTONE("milestone");

    private final String token;

    UnlockMode(String token) {
        this.token = token;
    }

    public String token() {
        return token;
    }

    public static UnlockMode fromToken(String token) {
        if (token == null) {
            return null;
        }
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        for (UnlockMode mode : values()) {
            if (mode.token.equals(normalized) || mode.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return mode;
            }
        }
        return null;
    }

    public static String tokens() {
        return "on_clear|manual_after_clear|milestone";
    }

    /** True when a player must physically interact after the room is cleared. */
    public boolean requiresManualInteraction() {
        return this == MANUAL_AFTER_ROOM_CLEAR;
    }
}
