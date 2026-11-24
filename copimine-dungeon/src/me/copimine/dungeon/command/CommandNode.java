package me.copimine.dungeon.command;

import me.copimine.dungeon.editor.EditorPermissions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One node of the {@code /cmdungeon} grammar (spec OV4.3).
 *
 * <p>Nodes are either literal tokens or typed arguments. A node may be marked
 * {@code terminal} when it is a complete command; children are optional so a
 * command can also accept a bounded continuation. The tree is pure data so the
 * documented interface contract can be parsed, validated and tab-completed
 * without a server.</p>
 */
public final class CommandNode {

    /** Argument kinds drive validation and tab-completion candidates. */
    public enum ArgType {
        ROOM_ID,
        GATE_ID,
        SECRET_ID,
        SPAWN_ID,
        /** A brand-new id that must not already be registered. */
        ROOM_ID_NEW,
        GATE_ID_NEW,
        SECRET_ID_NEW,
        SPAWN_ID_NEW,
        MOB_TEMPLATE,
        LOOT_TABLE,
        PLAYER,
        SCOPE,
        FILE,
        GATE_STYLE,
        UNLOCK_MODE,
        SCALING_MODE,
        REWARD_MODE,
        DIRECTION,
        EDITOR_MODE,
        OVERLAY_FILTER,
        WORLD,
        INTEGER
    }

    private final String literal;
    private final ArgType argType;
    private final String placeholder;
    private final EditorPermissions.Capability capability;
    private final boolean worldMutating;
    private final boolean requiresConfirm;
    private final boolean playerOnly;
    private final int minimum;
    private final int maximum;
    private final List<CommandNode> children = new ArrayList<>();
    private boolean terminal;

    private CommandNode(String literal, ArgType argType, String placeholder,
                        EditorPermissions.Capability capability, boolean worldMutating,
                        boolean requiresConfirm, boolean playerOnly, int minimum, int maximum) {
        this.literal = literal;
        this.argType = argType;
        this.placeholder = placeholder;
        this.capability = capability;
        this.worldMutating = worldMutating;
        this.requiresConfirm = requiresConfirm;
        this.playerOnly = playerOnly;
        this.minimum = minimum;
        this.maximum = maximum;
    }

    public static CommandNode literal(String token, EditorPermissions.Capability capability,
                                      boolean worldMutating, boolean requiresConfirm,
                                      boolean playerOnly, CommandNode... children) {
        CommandNode node = new CommandNode(token, null, token, capability, worldMutating,
                requiresConfirm, playerOnly, Integer.MIN_VALUE, Integer.MAX_VALUE);
        node.children.addAll(List.of(children));
        return node;
    }

    public static CommandNode argument(ArgType type, String placeholder,
                                       EditorPermissions.Capability capability,
                                       CommandNode... children) {
        CommandNode node = new CommandNode(null, type, placeholder, capability, false, false,
                false, Integer.MIN_VALUE, Integer.MAX_VALUE);
        node.children.addAll(List.of(children));
        return node;
    }

    /** Numeric argument bounded to an inclusive range (waves, ticks, seconds, blocks). */
    public static CommandNode number(String placeholder, int minimum, int maximum,
                                     EditorPermissions.Capability capability) {
        return new CommandNode(null, ArgType.INTEGER, placeholder, capability, false, false,
                false, minimum, maximum);
    }

    /** Marks this node as a complete command that may be executed here. */
    public CommandNode asCommand() {
        this.terminal = true;
        return this;
    }

    /** Appends children fluently (used while building the static grammar). */
    public CommandNode with(CommandNode... nodes) {
        children.addAll(List.of(nodes));
        return this;
    }

    public boolean isLiteral() {
        return literal != null;
    }

    public String literal() {
        return literal;
    }

    public ArgType argType() {
        return argType;
    }

    /** Rendered usage token: the literal itself or {@code <placeholder>}. */
    public String usageToken() {
        return isLiteral() ? literal : "<" + placeholder + ">";
    }

    public String placeholder() {
        return placeholder;
    }

    public EditorPermissions.Capability capability() {
        return capability;
    }

    public boolean worldMutating() {
        return worldMutating;
    }

    public boolean requiresConfirm() {
        return requiresConfirm;
    }

    public boolean playerOnly() {
        return playerOnly;
    }

    public int minimum() {
        return minimum;
    }

    public int maximum() {
        return maximum;
    }

    public boolean isCommand() {
        return terminal;
    }

    public List<CommandNode> children() {
        return List.copyOf(children);
    }

    public boolean isLeaf() {
        return children.isEmpty();
    }

    /** First literal child matching the token, or {@code null}. */
    public CommandNode childLiteral(String token) {
        if (token == null) {
            return null;
        }
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        for (CommandNode child : children) {
            if (child.isLiteral() && child.literal.equals(normalized)) {
                return child;
            }
        }
        return null;
    }

    /** Child literal tokens, used for suggestions and tab completion. */
    public List<String> literalChildren() {
        List<String> tokens = new ArrayList<>(children.size());
        for (CommandNode child : children) {
            if (child.isLiteral()) {
                tokens.add(child.literal);
            }
        }
        return tokens;
    }

    /** First argument child (at most one per node), or {@code null}. */
    public CommandNode argumentChild() {
        for (CommandNode child : children) {
            if (!child.isLiteral()) {
                return child;
            }
        }
        return null;
    }
}
