package me.copimine.dungeon.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import me.copimine.dungeon.editor.EditorPermissions;

/**
 * Bukkit adapter for {@code /cmdungeon} (spec OV4.3). All parsing, validation,
 * permission evaluation and dispatch happen in the pure
 * {@link DungeonCommandDispatcher}; this class only converts the sender and
 * prints the returned lines.
 */
public final class DungeonCommand implements CommandExecutor, TabCompleter {

    private final DungeonCommandDispatcher dispatcher;

    public DungeonCommand(DungeonCommandDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        CommandActor actor = actorOf(sender);
        CommandResponse response = dispatcher.execute(actor, List.of(args));
        for (String line : response.lines()) {
            if (line != null && !line.isBlank()) {
                sender.sendMessage(line);
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        CommandActor actor = actorOf(sender);
        List<String> candidates = dispatcher.complete(actor, List.of(args));
        if (args.length == 0) {
            return candidates;
        }
        String partial = args[args.length - 1] == null ? ""
                : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> filtered = new ArrayList<>();
        Set<String> unique = new LinkedHashSet<>();
        for (String candidate : candidates) {
            if (candidate != null && candidate.toLowerCase(Locale.ROOT).startsWith(partial)
                    && unique.add(candidate)) {
                filtered.add(candidate);
            }
        }
        return filtered.isEmpty() ? candidates : filtered;
    }

    private CommandActor actorOf(CommandSender sender) {
        if (sender instanceof Player player) {
            Set<String> permissions = new LinkedHashSet<>();
            if (player.hasPermission(EditorPermissions.ADMIN)) {
                permissions.add(EditorPermissions.ADMIN);
            }
            if (player.hasPermission(EditorPermissions.BUILDER)) {
                permissions.add(EditorPermissions.BUILDER);
            }
            return CommandActor.player(player.getUniqueId(), player.getName(),
                    player.getWorld().getName(),
                    player.getLocation().getBlockX(), player.getLocation().getBlockY(),
                    player.getLocation().getBlockZ(), permissions, player.isOp());
        }
        return new CommandActor(null, "CONSOLE", true, null, 0, 0, 0, false,
                Set.of(EditorPermissions.ADMIN, EditorPermissions.BUILDER), true);
    }
}
