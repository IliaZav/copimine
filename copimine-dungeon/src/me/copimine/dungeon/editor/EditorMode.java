package me.copimine.dungeon.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Editor gesture/selection mode (spec OV4.3). All names are stable lowercase
 * IDs accepted by {@code /cmdungeon editor mode <mode>} and offered by tab
 * completion.
 */
public enum EditorMode {
    ROOM("room", "Комната"),
    TRIGGER("trigger", "Триггер"),
    GATE("gate", "Шлюз"),
    SECRET("secret", "Секрет"),
    SPAWN("spawn", "Спавн"),
    REWARD("reward", "Награда"),
    REST("rest", "Отдых"),
    ARENA("arena", "Арена (только просмотр)"),
    PORTAL("portal", "Портал");

    private final String token;
    private final String label;

    EditorMode(String token, String label) {
        this.token = token;
        this.label = label;
    }

    public String token() {
        return token;
    }

    public String label() {
        return label;
    }

    /** Arena mode is a read-only survey preview; it can never create a definition. */
    public boolean readOnly() {
        return this == ARENA;
    }

    public static EditorMode fromToken(String token) {
        if (token == null) {
            return null;
        }
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        for (EditorMode mode : values()) {
            if (mode.token.equals(normalized) || mode.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return mode;
            }
        }
        return null;
    }

    /** The eight editable modes exposed by the spec's {@code editor mode} list. */
    public static List<String> editableTokens() {
        List<String> tokens = new ArrayList<>();
        for (EditorMode mode : values()) {
            if (!mode.readOnly()) {
                tokens.add(mode.token);
            }
        }
        return tokens;
    }

    public static List<String> allTokens() {
        List<String> tokens = new ArrayList<>();
        for (EditorMode mode : values()) {
            tokens.add(mode.token);
        }
        return tokens;
    }
}
