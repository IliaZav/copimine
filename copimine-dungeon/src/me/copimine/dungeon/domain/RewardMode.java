package me.copimine.dungeon.domain;

import java.util.Locale;

/** Safe reward delivery modes (spec OV4.4). */
public enum RewardMode {
    /** Place a real configured chest only at an approved safe anchor. */
    CHEST("chest"),
    /** Spawn room-owned item entities with deduplication. */
    DROPS("drops"),
    /** Per-player rewards via inventory/mailbox fallback when full. */
    PERSONAL("personal");

    private final String token;

    RewardMode(String token) {
        this.token = token;
    }

    public String token() {
        return token;
    }

    public static RewardMode fromToken(String token) {
        if (token == null) {
            return null;
        }
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        for (RewardMode mode : values()) {
            if (mode.token.equals(normalized) || mode.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return mode;
            }
        }
        return null;
    }

    public static String tokens() {
        return "chest|drops|personal";
    }
}
