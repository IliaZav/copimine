package me.copimine.dungeon.domain;

import java.util.Locale;

/**
 * Participant admission policy (spec OV4.4). A room must never invent a
 * count-based rejection: capacity is physical, so "unlimited" is the default
 * and load is throttled through AI actor budgets instead.
 */
public enum ParticipationMode {
    /** Anyone eligible entering the trigger joins; joiners mid-fight are safe. */
    JOIN_SAFE("join_safe"),
    /** Starting is synchronized with the party; only entered players participate. */
    PARTY_SYNCHRONIZED("party_synchronized");

    private final String token;

    ParticipationMode(String token) {
        this.token = token;
    }

    public String token() {
        return token;
    }

    public static ParticipationMode fromToken(String token) {
        if (token == null) {
            return null;
        }
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        for (ParticipationMode mode : values()) {
            if (mode.token.equals(normalized) || mode.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return mode;
            }
        }
        return null;
    }
}
