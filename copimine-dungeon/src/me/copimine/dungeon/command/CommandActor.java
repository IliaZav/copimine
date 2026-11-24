package me.copimine.dungeon.command;

import java.util.Set;
import java.util.UUID;

/**
 * The immutable identity of whoever ran a {@code /cmdungeon} command.
 *
 * <p>Console senders have {@code console == true} and no position; commands
 * that need a live selection require {@code explicitPosition}. {@code op} is
 * carried for audit only: {@link me.copimine.dungeon.editor.EditorPermissions}
 * never treats it as sufficient.</p>
 */
public record CommandActor(UUID id, String name, boolean console, String world,
                           int x, int y, int z, boolean explicitPosition,
                           Set<String> permissions, boolean op) {

    public CommandActor {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    public static CommandActor player(UUID id, String name, String world, int x, int y, int z,
                                      Set<String> permissions, boolean op) {
        return new CommandActor(id, name, false, world, x, y, z, true, permissions, op);
    }

    public static CommandActor consoleActor() {
        return new CommandActor(null, "CONSOLE", true, null, 0, 0, 0, false, Set.of(), true);
    }

    /** Stable key used for grant bookkeeping and audit lines. */
    public String key() {
        return id == null ? "console:" + name : id.toString();
    }

    public int[] position() {
        return new int[]{x, y, z};
    }
}
