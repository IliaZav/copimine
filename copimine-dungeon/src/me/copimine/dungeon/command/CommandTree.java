package me.copimine.dungeon.command;

import me.copimine.dungeon.editor.EditorPermissions;

import java.util.ArrayList;
import java.util.List;

/**
 * The complete {@code /cmdungeon} grammar from spec OV4.3 (literal block
 * {@code /cmdungeon help .. /cmdungeon audit <roomId> [recent]}).
 *
 * <p>Pure data: the tree can be walked, validated, usage-rendered and
 * tab-completed with no server. Every leaf node is marked {@code asCommand()}
 * so "unknown subcommand returns exact usage" can be implemented by printing
 * the usage of the deepest matched node.</p>
 */
public final class CommandTree {

    private final CommandNode root;

    private CommandTree(CommandNode root) {
        this.root = root;
    }

    public CommandNode root() {
        return root;
    }

    private static CommandNode cmd(String token, EditorPermissions.Capability capability) {
        return CommandNode.literal(token, capability, false, false, false).asCommand();
    }

    private static CommandNode playerCmd(String token, EditorPermissions.Capability capability) {
        return CommandNode.literal(token, capability, false, false, true).asCommand();
    }

    private static CommandNode argCmd(CommandNode.ArgType type, String placeholder,
                                      EditorPermissions.Capability capability) {
        return CommandNode.argument(type, placeholder, capability).asCommand();
    }

    /** An argument that is itself a complete command; extra child tokens are optional. */
    private static CommandNode optArg(CommandNode.ArgType type, String placeholder,
                                      EditorPermissions.Capability capability, CommandNode... children) {
        return CommandNode.argument(type, placeholder, capability).with(children).asCommand();
    }

    public static CommandTree spec() {
        EditorPermissions.Capability admin = EditorPermissions.Capability.ADMIN_PUBLISH;
        EditorPermissions.Capability builder = EditorPermissions.Capability.EDIT_DRAFT;
        EditorPermissions.Capability grant = EditorPermissions.Capability.WAND_GRANT;

        CommandNode room = CommandNode.literal("room", builder, false, false, false,
                CommandNode.literal("list", builder, false, false, false,
                        cmd("nearby", builder), cmd("all", builder)).asCommand(),
                CommandNode.literal("info", builder, false, false, false,
                        argCmd(CommandNode.ArgType.ROOM_ID, "roomId", builder)),
                CommandNode.literal("edit", builder, false, false, false,
                        argCmd(CommandNode.ArgType.ROOM_ID, "roomId", builder)),
                CommandNode.literal("bounds", builder, false, false, true,
                        argCmd(CommandNode.ArgType.ROOM_ID, "roomId", builder)),
                CommandNode.literal("trigger", builder, false, false, true,
                        argCmd(CommandNode.ArgType.ROOM_ID, "roomId", builder)),
                CommandNode.literal("validate", builder, false, false, false,
                        argCmd(CommandNode.ArgType.ROOM_ID, "roomId", builder)),
                // Preview is explicitly NO block mutations: never world-mutating.
                CommandNode.literal("preview", builder, false, false, false,
                        argCmd(CommandNode.ArgType.ROOM_ID, "roomId", builder)),
                // Publication activates a durable definition; the command itself changes no blocks.
                CommandNode.literal("publish", admin, false, false, false,
                        argCmd(CommandNode.ArgType.ROOM_ID, "roomId", admin)),
                CommandNode.literal("disable", admin, false, false, false,
                        argCmd(CommandNode.ArgType.ROOM_ID, "roomId", admin)),
                CommandNode.literal("reset", admin, true, true, false,
                        CommandNode.argument(CommandNode.ArgType.ROOM_ID, "roomId", admin)
                                .with(cmd("confirm", admin))),
                CommandNode.literal("test", builder, false, false, false,
                        optArg(CommandNode.ArgType.ROOM_ID, "roomId", builder,
                                cmd("solo", builder), cmd("party", builder))),
                CommandNode.literal("export", builder, false, false, false,
                        argCmd(CommandNode.ArgType.ROOM_ID, "roomId", builder)),
                CommandNode.literal("import", builder, false, false, false,
                        CommandNode.argument(CommandNode.ArgType.FILE, "file", builder)
                                .with(cmd("--dry-run", builder),
                                        CommandNode.literal("confirm", admin, true, true, false)
                                                .asCommand())),
                CommandNode.literal("undo", builder, false, false, false,
                        argCmd(CommandNode.ArgType.ROOM_ID, "roomId", builder)),
                CommandNode.literal("redo", builder, false, false, false,
                        argCmd(CommandNode.ArgType.ROOM_ID, "roomId", builder)),
                CommandNode.literal("show", builder, false, false, false,
                        optArg(CommandNode.ArgType.ROOM_ID, "roomId", builder,
                                CommandNode.number("seconds", 1, 600, builder).asCommand())));

        CommandNode gate = CommandNode.literal("gate", builder, false, false, false,
                CommandNode.literal("add", builder, false, false, false,
                        argCmd(CommandNode.ArgType.GATE_ID_NEW, "gateId", builder)),
                CommandNode.literal("bounds", builder, false, false, true,
                        argCmd(CommandNode.ArgType.GATE_ID, "gateId", builder)),
                CommandNode.literal("style", builder, false, false, false,
                        CommandNode.argument(CommandNode.ArgType.GATE_ID, "gateId", builder)
                                .with(argCmd(CommandNode.ArgType.GATE_STYLE, "style", builder))),
                CommandNode.literal("role", builder, false, false, false,
                        CommandNode.argument(CommandNode.ArgType.GATE_ID, "gateId", builder)
                                .with(cmd("entrance", builder), cmd("exit", builder))),
                CommandNode.literal("unlock", builder, false, false, false,
                        CommandNode.argument(CommandNode.ArgType.GATE_ID, "gateId", builder)
                                .with(argCmd(CommandNode.ArgType.UNLOCK_MODE, "mode", builder))),
                CommandNode.literal("bind", builder, false, false, false,
                        CommandNode.argument(CommandNode.ArgType.GATE_ID, "gateId", builder)
                                .with(argCmd(CommandNode.ArgType.ROOM_ID, "roomId", builder))),
                CommandNode.literal("direction", builder, false, false, false,
                        CommandNode.argument(CommandNode.ArgType.GATE_ID, "gateId", builder)
                                .with(argCmd(CommandNode.ArgType.DIRECTION, "direction", builder))),
                // Ghost-only preview: never changes real collision state.
                CommandNode.literal("preview", builder, false, false, false,
                        CommandNode.argument(CommandNode.ArgType.GATE_ID, "gateId", builder)
                                .with(cmd("close", builder), cmd("open", builder))),
                CommandNode.literal("validate", builder, false, false, false,
                        argCmd(CommandNode.ArgType.GATE_ID, "gateId", builder)),
                CommandNode.literal("status", builder, false, false, false,
                        argCmd(CommandNode.ArgType.GATE_ID, "gateId", builder)));

        CommandNode secret = CommandNode.literal("secret", builder, false, false, false,
                CommandNode.literal("add", builder, false, false, false,
                        argCmd(CommandNode.ArgType.SECRET_ID_NEW, "secretId", builder)),
                CommandNode.literal("bounds", builder, false, false, true,
                        argCmd(CommandNode.ArgType.SECRET_ID, "secretId", builder)),
                CommandNode.literal("style", builder, false, false, false,
                        CommandNode.argument(CommandNode.ArgType.SECRET_ID, "secretId", builder)
                                .with(argCmd(CommandNode.ArgType.GATE_STYLE, "gateStyle", builder))),
                CommandNode.literal("unlock", builder, false, false, false,
                        CommandNode.argument(CommandNode.ArgType.SECRET_ID, "secretId", builder)
                                .with(argCmd(CommandNode.ArgType.UNLOCK_MODE, "mode", builder))));

        CommandNode spawn = CommandNode.literal("spawn", builder, false, false, false,
                CommandNode.literal("zone", builder, false, false, false,
                        argCmd(CommandNode.ArgType.SPAWN_ID_NEW, "spawnId", builder)),
                CommandNode.literal("bounds", builder, false, false, false,
                        argCmd(CommandNode.ArgType.SPAWN_ID, "spawnId", builder)),
                CommandNode.literal("add", builder, false, false, false,
                        CommandNode.argument(CommandNode.ArgType.SPAWN_ID_NEW, "spawnId", builder)
                                .with(argCmd(CommandNode.ArgType.MOB_TEMPLATE, "mobTemplate", builder))),
                CommandNode.literal("list", builder, false, false, false,
                        argCmd(CommandNode.ArgType.ROOM_ID, "roomId", builder)),
                CommandNode.literal("remove", builder, false, false, false,
                        argCmd(CommandNode.ArgType.SPAWN_ID, "spawnId", builder)));

        CommandNode mobs = CommandNode.literal("mobs", builder, false, false, false,
                CommandNode.literal("add", builder, false, false, false,
                        CommandNode.argument(CommandNode.ArgType.ROOM_ID, "roomId", builder)
                                .with(CommandNode.argument(CommandNode.ArgType.MOB_TEMPLATE, "mobTemplate", builder)
                                        .with(CommandNode.number("count", 1, 200, builder).asCommand()))),
                CommandNode.literal("scaling", builder, false, false, false,
                        CommandNode.argument(CommandNode.ArgType.ROOM_ID, "roomId", builder)
                                .with(argCmd(CommandNode.ArgType.SCALING_MODE, "mode", builder))),
                CommandNode.literal("concurrent-cap", builder, false, false, false,
                        CommandNode.argument(CommandNode.ArgType.ROOM_ID, "roomId", builder)
                                .with(CommandNode.number("count", 1, 500, builder).asCommand())));

        CommandNode wave = CommandNode.literal("wave", builder, false, false, false,
                CommandNode.literal("add", builder, false, false, false,
                        argCmd(CommandNode.ArgType.ROOM_ID, "roomId", builder)),
                CommandNode.literal("mob", builder, false, false, false,
                        CommandNode.argument(CommandNode.ArgType.ROOM_ID, "roomId", builder)
                                .with(CommandNode.number("waveIndex", 0, 63, builder)
                                        .with(CommandNode.argument(CommandNode.ArgType.MOB_TEMPLATE, "mobTemplate", builder)
                                                .with(CommandNode.number("count", 0, 200, builder).asCommand())))),
                CommandNode.literal("delay", builder, false, false, false,
                        CommandNode.argument(CommandNode.ArgType.ROOM_ID, "roomId", builder)
                                .with(CommandNode.number("waveIndex", 0, 63, builder)
                                        .with(CommandNode.number("ticks", 0, 72000, builder).asCommand()))));

        CommandNode reward = CommandNode.literal("reward", builder, false, false, false,
                CommandNode.literal("mode", builder, false, false, false,
                        CommandNode.argument(CommandNode.ArgType.ROOM_ID, "roomId", builder)
                                .with(argCmd(CommandNode.ArgType.REWARD_MODE, "mode", builder))),
                CommandNode.literal("anchor", builder, false, false, false,
                        argCmd(CommandNode.ArgType.ROOM_ID, "roomId", builder)),
                CommandNode.literal("loot", builder, false, false, false,
                        CommandNode.argument(CommandNode.ArgType.ROOM_ID, "roomId", builder)
                                .with(argCmd(CommandNode.ArgType.LOOT_TABLE, "lootTableId", builder))));

        CommandNode overlay = CommandNode.literal("overlay", builder, false, false, false,
                cmd("on", builder),
                cmd("off", builder),
                CommandNode.literal("filter", builder, false, false, false,
                        cmd("rooms", builder), cmd("gates", builder),
                        cmd("secrets", builder), cmd("all", builder)),
                CommandNode.literal("range", builder, false, false, false,
                        CommandNode.number("blocks", 16, 96, builder).asCommand()));

        CommandNode editor = CommandNode.literal("editor", builder, false, false, false,
                CommandNode.literal("grant", grant, false, false, false,
                        optArg(CommandNode.ArgType.PLAYER, "player", grant,
                                argCmd(CommandNode.ArgType.SCOPE, "scope", grant))),
                CommandNode.literal("revoke", grant, false, false, false,
                        argCmd(CommandNode.ArgType.PLAYER, "player", grant)),
                cmd("list", admin),
                CommandNode.literal("mode", builder, false, false, false,
                        argCmd(CommandNode.ArgType.EDITOR_MODE, "mode", builder)),
                CommandNode.literal("wand", grant, false, false, false,
                        argCmd(CommandNode.ArgType.PLAYER, "player", grant)));

        CommandNode wand = CommandNode.literal("wand", grant, false, false, false,
                CommandNode.literal("give", grant, false, false, false,
                        argCmd(CommandNode.ArgType.PLAYER, "player", grant)));

        List<CommandNode> children = new ArrayList<>();
        children.add(cmd("help", null));
        children.add(cmd("status", builder));
        children.add(wand);
        children.add(editor);
        children.add(playerCmd("pos1", builder));
        children.add(playerCmd("pos2", builder));
        children.add(CommandNode.literal("select", builder, false, false, false,
                argCmd(CommandNode.ArgType.ROOM_ID, "roomId", builder)));
        children.add(CommandNode.literal("create", builder, false, false, false,
                argCmd(CommandNode.ArgType.ROOM_ID_NEW, "roomId", builder)));
        children.add(room);
        children.add(overlay);
        children.add(gate);
        children.add(secret);
        children.add(spawn);
        children.add(mobs);
        children.add(wave);
        children.add(reward);
        children.add(CommandNode.literal("audit", builder, false, false, false,
                optArg(CommandNode.ArgType.ROOM_ID, "roomId", builder, cmd("recent", builder))));
        children.add(CommandNode.literal("inspect", builder, false, false, false,
                argCmd(CommandNode.ArgType.ROOM_ID, "roomId", builder)));

        CommandNode root = CommandNode.literal("cmdungeon", null, false, false, false,
                children.toArray(new CommandNode[0]));
        return new CommandTree(root);
    }

    /** Root-level literal tokens, for {@code help} and top-level completion. */
    public List<String> rootTokens() {
        return root.literalChildren();
    }
}
