package me.copimine.dungeon.command;

import me.copimine.dungeon.domain.GateStyle;
import me.copimine.dungeon.domain.RewardMode;
import me.copimine.dungeon.domain.ScalingMode;
import me.copimine.dungeon.domain.UnlockMode;
import me.copimine.dungeon.editor.EditorMode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses, validates and tab-completes the {@code /cmdungeon} grammar
 * (spec OV4.3). Pure Java: the Bukkit executor delegates here so the whole
 * documented interface contract is unit-testable without a server.
 */
public final class CommandRegistry {

    /** Bounded direction tokens accepted by {@code gate direction}. */
    public static final List<String> DIRECTIONS = List.of("up", "left", "right", "outward", "auto");
    /** Overlay filter tokens accepted by {@code overlay filter}. */
    public static final List<String> OVERLAY_FILTERS = List.of("rooms", "gates", "secrets", "all");

    private final CommandTree tree;
    private final IdSource ids;

    public CommandRegistry(IdSource ids) {
        this.tree = CommandTree.spec();
        this.ids = ids;
    }

    public CommandTree tree() {
        return tree;
    }

    /** Result of resolving a token list against the grammar. */
    public static final class Route {
        private final boolean matched;
        private final boolean worldMutating;
        private final boolean requiresConfirm;
        private final CommandNode endpoint;
        private final List<String> path;
        private final Map<String, String> arguments;
        private final String usage;
        private final String problem;
        private final List<String> suggestions;

        Route(boolean matched, boolean worldMutating, boolean requiresConfirm, CommandNode endpoint,
              List<String> path, Map<String, String> arguments, String usage, String problem,
              List<String> suggestions) {
            this.matched = matched;
            this.worldMutating = worldMutating;
            this.requiresConfirm = requiresConfirm;
            this.endpoint = endpoint;
            this.path = List.copyOf(path);
            this.arguments = Map.copyOf(arguments);
            this.usage = usage;
            this.problem = problem;
            this.suggestions = List.copyOf(suggestions);
        }

        public boolean matched() {
            return matched;
        }

        /** True when any node on the matched path mutates the world (dry-run only). */
        public boolean worldMutating() {
            return worldMutating;
        }

        /** True when any node on the matched path demands an explicit confirm token. */
        public boolean requiresConfirm() {
            return requiresConfirm;
        }

        public CommandNode endpoint() {
            return endpoint;
        }

        public List<String> path() {
            return path;
        }

        /** Literal tokens only, e.g. {@code [room, publish]}. */
        public List<String> literals() {
            List<String> literals = new ArrayList<>();
            for (String token : path) {
                if (!token.startsWith("<")) {
                    literals.add(token);
                }
            }
            return literals;
        }

        public Map<String, String> arguments() {
            return arguments;
        }

        public String argument(String placeholder) {
            return arguments.get(placeholder);
        }

        public String usage() {
            return usage;
        }

        public String problem() {
            return problem;
        }

        public List<String> suggestions() {
            return suggestions;
        }

        public String canonicalPath() {
            return String.join(" ", literals());
        }
    }

    // ------------------------------------------------------------------
    // Resolution
    // ------------------------------------------------------------------

    public Route resolve(List<String> tokens) {
        CommandNode node = tree.root();
        // The path begins *after* the root literal (cmdungeon): canonicalPath()
        // therefore yields the sub-command grammar without the command name.
        List<String> path = new ArrayList<>();
        Map<String, String> arguments = new LinkedHashMap<>();

        for (String raw : tokens) {
            String token = raw == null ? "" : raw.trim();
            CommandNode literal = node.childLiteral(token);
            if (literal != null) {
                node = literal;
                path.add(node.usageToken());
                continue;
            }
            CommandNode argument = node.argumentChild();
            if (argument != null) {
                String diagnostic = validate(argument, token);
                if (diagnostic != null) {
                    boolean[] flags = flags(node);
                    return new Route(false, flags[0], flags[1], node, path, arguments,
                            usageOf(node) + " " + argument.usageToken(), diagnostic, List.of());
                }
                node = argument;
                path.add(node.usageToken());
                arguments.put(argument.placeholder(), token);
                continue;
            }
            return unknown(node, path, arguments, token);
        }

        if (node.isCommand()) {
            boolean[] flags = flags(node);
            return new Route(true, flags[0], flags[1], node, path, arguments,
                    usageOf(node), null, List.of());
        }
        return missingContinuation(node, path, arguments);
    }

    /** {worldMutating, requiresConfirm} accumulated along root..node. */
    private boolean[] flags(CommandNode node) {
        boolean worldMutating = false;
        boolean requiresConfirm = false;
        for (CommandNode current : ancestry(node)) {
            worldMutating |= current.worldMutating();
            requiresConfirm |= current.requiresConfirm();
        }
        return new boolean[]{worldMutating, requiresConfirm};
    }

    private Route unknown(CommandNode node, List<String> path, Map<String, String> arguments,
                          String token) {
        List<String> candidates = node.literalChildren();
        String suggestion = CommandSuggestions.nearest(token, candidates);
        String problem = "Неизвестный аргумент '" + token + "' для '" + usageOf(node) + "'."
                + (suggestion == null ? "" : " Возможно, вы имели в виду '" + suggestion + "'.");
        List<String> suggestions = suggestion == null ? List.of() : List.of(suggestion);
        boolean[] flags = flags(node);
        return new Route(false, flags[0], flags[1], node, path, arguments,
                usageOf(node), problem, suggestions);
    }

    private Route missingContinuation(CommandNode node, List<String> path,
                                      Map<String, String> arguments) {
        String tail;
        CommandNode argument = node.argumentChild();
        if (argument != null) {
            tail = argument.usageToken();
        } else {
            tail = String.join("|", node.literalChildren());
        }
        String usage = usageOf(node) + " " + tail;
        boolean[] flags = flags(node);
        return new Route(false, flags[0], flags[1], node, path, arguments, usage,
                "Не хватает аргументов. Использование: " + usage, List.of());
    }

    private String usageOf(CommandNode node) {
        List<String> tokens = new ArrayList<>();
        for (CommandNode current : ancestry(node)) {
            tokens.add(current.usageToken());
        }
        return "/" + String.join(" ", tokens);
    }

    /** Node chain from the root (inclusive) to the given node (inclusive). */
    private List<CommandNode> ancestry(CommandNode target) {
        List<CommandNode> chain = new ArrayList<>();
        if (find(tree.root(), target, chain)) {
            return chain;
        }
        return chain;
    }

    private boolean find(CommandNode node, CommandNode target, List<CommandNode> chain) {
        chain.add(node);
        if (node == target) {
            return true;
        }
        for (CommandNode child : node.children()) {
            if (find(child, target, chain)) {
                return true;
            }
        }
        chain.remove(chain.size() - 1);
        return false;
    }

    /** Every complete-command usage line in the tree, in grammar order. */
    public List<String> usageLines() {
        List<String> lines = new ArrayList<>();
        collectUsage(tree.root(), new ArrayList<>(), lines);
        return lines;
    }

    private void collectUsage(CommandNode node, List<String> path, List<String> lines) {
        List<String> current = new ArrayList<>(path);
        current.add(node.usageToken());
        if (node.isCommand()) {
            lines.add("/" + String.join(" ", current));
        }
        for (CommandNode child : node.children()) {
            collectUsage(child, current, lines);
        }
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    private String validate(CommandNode argument, String token) {
        if (token.isEmpty()) {
            return "Пустой аргумент <" + argument.placeholder() + ">.";
        }
        if (argument.argType() == null) {
            return null;
        }
        return switch (argument.argType()) {
            case ROOM_ID -> membership(token, ids.roomIds(), "комната");
            case GATE_ID -> membership(token, ids.gateIds(), "шлюз");
            case SECRET_ID -> membership(token, ids.secretIds(), "секрет");
            case SPAWN_ID -> membership(token, ids.spawnIds(), "спавн-якорь");
            case ROOM_ID_NEW -> validateNewId(argument, token, "комната");
            case GATE_ID_NEW -> validateNewId(argument, token, "шлюз");
            case SECRET_ID_NEW -> validateNewId(argument, token, "секрет");
            case SPAWN_ID_NEW -> validateNewId(argument, token, "спавн-якорь");
            case MOB_TEMPLATE -> membership(token, ids.mobTemplateIds(), "шаблон моба");
            case LOOT_TABLE -> validateNewId(argument, token, "таблица добычи");
            case GATE_STYLE -> GateStyle.fromToken(token) == null
                    ? "Неизвестный стиль шлюза '" + token + "'. Доступно: " + GateStyle.tokens() + "."
                    : null;
            case UNLOCK_MODE -> UnlockMode.fromToken(token) == null
                    ? "Неизвестный режим разблокировки '" + token + "'. Доступно: " + UnlockMode.tokens() + "."
                    : null;
            case SCALING_MODE -> ScalingMode.fromToken(token) == null
                    ? "Неизвестный режим масштабирования '" + token + "'. Доступно: " + ScalingMode.tokens() + "."
                    : null;
            case REWARD_MODE -> RewardMode.fromToken(token) == null
                    ? "Неизвестный режим награды '" + token + "'. Доступно: " + RewardMode.tokens() + "."
                    : null;
            case DIRECTION -> DIRECTIONS.contains(token.toLowerCase(Locale.ROOT))
                    ? null : "Неизвестное направление '" + token + "'. Доступно: " + String.join("|", DIRECTIONS) + ".";
            case EDITOR_MODE -> EditorMode.fromToken(token) == null
                    ? "Неизвестный режим редактора '" + token + "'. Доступно: " + String.join("|", EditorMode.allTokens()) + "."
                    : null;
            case OVERLAY_FILTER -> OVERLAY_FILTERS.contains(token.toLowerCase(Locale.ROOT))
                    ? null : "Неизвестный фильтр оверлея '" + token + "'.";
            case INTEGER -> validateInteger(argument, token);
            case PLAYER, WORLD, FILE, SCOPE -> null;
        };
    }

    private String validateInteger(CommandNode argument, String token) {
        int value;
        try {
            value = Integer.parseInt(token);
        } catch (NumberFormatException error) {
            return "Аргумент <" + argument.placeholder() + "> должен быть целым числом.";
        }
        if (value < argument.minimum() || value > argument.maximum()) {
            return "Аргумент <" + argument.placeholder() + "> должен быть в диапазоне "
                    + argument.minimum() + ".." + argument.maximum() + ".";
        }
        return null;
    }

    private String membership(String token, List<String> known, String label) {
        if (known != null && known.contains(token)) {
            return null;
        }
        String suggestion = CommandSuggestions.nearest(token, known == null ? List.of() : known);
        return "Неизвестный " + label + " '" + token + "'."
                + (suggestion == null ? "" : " Возможно, вы имели в виду '" + suggestion + "'.");
    }

    /**
     * Validates an id that is being created (so it cannot be required to already
     * exist). Stable lowercase ids only, matching the store's id contract.
     */
    private String validateNewId(CommandNode argument, String token, String label) {
        String trimmed = token == null ? "" : token.trim();
        if (trimmed.isEmpty()) {
            return "Пустой идентификатор: " + label + ".";
        }
        if (!trimmed.equals(trimmed.toLowerCase(Locale.ROOT))) {
            return "Идентификатор " + label + " должен быть в нижнем регистре: " + token;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            boolean allowed = (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')
                    || ch == '_' || ch == '-' || ch == '.';
            if (!allowed) {
                return "Недопустимый символ в идентификаторе " + label + ": '" + ch + "'.";
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Tab completion
    // ------------------------------------------------------------------

    /** Completions for the final (possibly empty) token. */
    public List<String> complete(List<String> tokens) {
        CommandNode node = tree.root();
        if (tokens.isEmpty()) {
            return tree.rootTokens();
        }
        for (int index = 0; index < tokens.size() - 1; index++) {
            String token = tokens.get(index) == null ? "" : tokens.get(index).trim();
            CommandNode literal = node.childLiteral(token);
            if (literal != null) {
                node = literal;
                continue;
            }
            CommandNode argument = node.argumentChild();
            if (argument != null) {
                node = argument;
            }
        }
        String partial = tokens.get(tokens.size() - 1) == null ? "" : tokens.get(tokens.size() - 1);
        List<String> candidates = new ArrayList<>(node.literalChildren());
        CommandNode argument = node.argumentChild();
        if (argument != null) {
            candidates.addAll(argumentCandidates(argument));
        }
        List<String> matches = CommandSuggestions.prefixMatches(partial, candidates);
        if (matches.isEmpty()) {
            matches = CommandSuggestions.complete(partial, candidates);
        }
        return matches;
    }

    private List<String> argumentCandidates(CommandNode argument) {
        if (argument.argType() == null) {
            return List.of();
        }
        return switch (argument.argType()) {
            case ROOM_ID -> safe(ids.roomIds());
            case GATE_ID -> safe(ids.gateIds());
            case SECRET_ID -> safe(ids.secretIds());
            case SPAWN_ID -> safe(ids.spawnIds());
            case ROOM_ID_NEW, GATE_ID_NEW, SECRET_ID_NEW, SPAWN_ID_NEW -> List.of();
            case MOB_TEMPLATE -> safe(ids.mobTemplateIds());
            case LOOT_TABLE -> safe(ids.lootTableIds());
            case PLAYER -> safe(ids.playerNames());
            case GATE_STYLE -> List.of(GateStyle.tokens().split("\\|"));
            case UNLOCK_MODE -> List.of(UnlockMode.tokens().split("\\|"));
            case SCALING_MODE -> List.of(ScalingMode.tokens().split("\\|"));
            case REWARD_MODE -> List.of(RewardMode.tokens().split("\\|"));
            case DIRECTION -> DIRECTIONS;
            case EDITOR_MODE -> EditorMode.allTokens();
            case OVERLAY_FILTER -> OVERLAY_FILTERS;
            case WORLD, FILE, SCOPE, INTEGER -> List.of();
        };
    }

    private static List<String> safe(List<String> values) {
        return values == null ? List.of() : values;
    }
}
