package me.copimine.dungeon.command;

import java.util.List;

/**
 * Result of executing a resolved command: one or more output lines plus a
 * success flag. The Bukkit executor copies the lines back to the sender; tests
 * assert on them directly.
 */
public record CommandResponse(boolean success, List<String> lines) {

    public CommandResponse {
        lines = List.copyOf(lines);
    }

    public static CommandResponse ok(String... lines) {
        return new CommandResponse(true, List.of(lines));
    }

    public static CommandResponse ok(List<String> lines) {
        return new CommandResponse(true, lines);
    }

    public static CommandResponse fail(String... lines) {
        return new CommandResponse(false, List.of(lines));
    }

    public static CommandResponse fail(List<String> lines) {
        return new CommandResponse(false, lines);
    }
}
