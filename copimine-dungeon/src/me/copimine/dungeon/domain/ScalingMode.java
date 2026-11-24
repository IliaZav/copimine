package me.copimine.dungeon.domain;

import java.util.Locale;

/**
 * Per-room difficulty scaling (spec OV4.4). FIXED uses the configured count,
 * PER_PLAYER multiplies by the eligible roster, ADAPTIVE bounds against
 * {@code maxConcurrentEnemies} and the real server/entity budget.
 */
public enum ScalingMode {
    FIXED("fixed"),
    PER_PLAYER("per_player"),
    ADAPTIVE("adaptive");

    private final String token;

    ScalingMode(String token) {
        this.token = token;
    }

    public String token() {
        return token;
    }

    public static ScalingMode fromToken(String token) {
        if (token == null) {
            return null;
        }
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        for (ScalingMode mode : values()) {
            if (mode.token.equals(normalized) || mode.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return mode;
            }
        }
        return null;
    }

    public static String tokens() {
        return "fixed|per_player|adaptive";
    }
}
