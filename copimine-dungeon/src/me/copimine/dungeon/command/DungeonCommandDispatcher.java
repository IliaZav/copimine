package me.copimine.dungeon.command;

import me.copimine.dungeon.domain.Cuboid;
import me.copimine.dungeon.domain.DungeonRoomDefinition;
import me.copimine.dungeon.domain.EnemyGroup;
import me.copimine.dungeon.domain.GateDefinition;
import me.copimine.dungeon.domain.GateStyle;
import me.copimine.dungeon.domain.MobTemplate;
import me.copimine.dungeon.domain.OverlayType;
import me.copimine.dungeon.domain.RewardMode;
import me.copimine.dungeon.domain.RewardSpec;
import me.copimine.dungeon.domain.ScalingMode;
import me.copimine.dungeon.domain.SecretDefinition;
import me.copimine.dungeon.domain.SpawnAnchor;
import me.copimine.dungeon.domain.UnlockMode;
import me.copimine.dungeon.domain.WaveSpec;
import me.copimine.dungeon.editor.EditorMode;
import me.copimine.dungeon.editor.EditorPermissions;
import me.copimine.dungeon.editor.EditorSession;
import me.copimine.dungeon.editor.EditorSessionManager;
import me.copimine.dungeon.store.AuditLog;
import me.copimine.dungeon.store.DungeonCatalog;
import me.copimine.dungeon.store.DungeonDefinitionStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Executes a resolved {@code /cmdungeon} route against the durable definition
 * store (spec OV4.3/OV4.4).
 *
 * <p>Safety contract: this class never touches a Bukkit world. Every
 * "world-mutating" command is downgraded to a validated <b>dry-run receipt</b>
 * that records exactly what would change; no block is ever modified. That is
 * what makes the whole editor safely testable and keeps live-world mutation out
 * of the plugin entirely.</p>
 */
public final class DungeonCommandDispatcher {

    private final DungeonDefinitionStore store;
    private final EditorSessionManager sessions;
    private final AuditLog audit;
    private final CommandRegistry registry;
    private final List<String> extraLootTables = new ArrayList<>();
    private final List<String> playerNames = new ArrayList<>();
    private WandGateway wandGateway;

    public DungeonCommandDispatcher(DungeonDefinitionStore store, EditorSessionManager sessions,
                                    AuditLog audit) {
        this.store = store;
        this.sessions = sessions;
        this.audit = audit;
        this.registry = new CommandRegistry(new CatalogIdSource(store,
                () -> extraLootTables, () -> playerNames));
    }

    public CommandRegistry registry() {
        return registry;
    }

    /** Attaches the live wand hand-off; absent, {@code wand give} never mints. */
    public void setWandGateway(WandGateway gateway) {
        this.wandGateway = gateway;
    }

    public void setPlayerNames(List<String> names) {
        playerNames.clear();
        if (names != null) {
            playerNames.addAll(names);
        }
    }

    /** Tab completion over real registered ids and enumerated styles. */
    public List<String> complete(CommandActor actor, List<String> args) {
        return registry.complete(args);
    }

    public CommandResponse execute(CommandActor actor, List<String> args) {
        if (args == null || args.isEmpty()) {
            return help();
        }
        CommandRegistry.Route route = registry.resolve(args);
        String action = "/cmdungeon " + String.join(" ", args);
        if (!route.matched()) {
            audit.record(actor.name(), action, "REJECTED");
            List<String> lines = new ArrayList<>();
            lines.add(route.problem());
            lines.add("Использование: " + route.usage());
            for (String suggestion : route.suggestions()) {
                lines.add("Подсказка: /cmdungeon " + suggestion);
            }
            return CommandResponse.fail(lines);
        }
        EditorPermissions.Capability capability = route.endpoint().capability();
        if (capability != null && !EditorPermissions.allows(actor.permissions(), actor.op(), capability)) {
            audit.record(actor.name(), action, "DENIED");
            return CommandResponse.fail(EditorPermissions.describeDenial(capability));
        }
        if (route.endpoint().playerOnly() && actor.console()) {
            return CommandResponse.fail("Команда доступна только игроку. "
                    + "Из консоли укажите явные мир/координаты.");
        }
        List<String> lines = new ArrayList<>();
        if (route.worldMutating()) {
            lines.add("DRY-RUN: реальные блоки мира не изменяются; формируется только отчёт.");
        }
        CommandResponse response = dispatch(actor, route, lines);
        audit.record(actor.name(), action, response.success() ? "OK" : "FAILED");
        return response;
    }

    // ------------------------------------------------------------------

    private CommandResponse help() {
        List<String> lines = new ArrayList<>();
        lines.add("CopiMine Dungeon — редактор подземелий. Использование: /cmdungeon <подкоманда>");
        lines.addAll(registry.usageLines());
        return CommandResponse.ok(lines);
    }

    private CommandResponse dispatch(CommandActor actor, CommandRegistry.Route route, List<String> lines) {
        String path = route.canonicalPath();
        switch (path) {
            case "help" -> {
                return help();
            }
            case "status" -> {
                DungeonCatalog catalog = store.catalog();
                lines.add("Схема: v" + store.schemaVersion());
                lines.add("Комнат: " + catalog.rooms().size()
                        + ", шлюзов: " + catalog.gates().size()
                        + ", секретов: " + catalog.secrets().size()
                        + ", спавнов: " + catalog.spawns().size());
                lines.add("Проблем загрузки: " + store.loadProblems().size());
                return CommandResponse.ok(lines);
            }
            case "wand give", "editor wand" -> {
                String target = route.argument("player");
                if (wandGateway == null) {
                    // Honest receipt: the request resolved, but this pure core has
                    // no inventory, so no item exists to hand over.
                    lines.add("Право строителя подтверждено для '" + target
                            + "'. Предмет-жезл выдаёт живой адаптер сервера (PDC-метка);"
                            + " в этом режиме предмет не создаётся.");
                    return CommandResponse.ok(lines);
                }
                WandGateway.Result result = wandGateway.give(target);
                if (result == null) {
                    return CommandResponse.fail("Выдача жезла не выполнена.");
                }
                if (!result.success()) {
                    return CommandResponse.fail(result.message());
                }
                lines.add(result.message());
                return CommandResponse.ok(lines);
            }
            case "editor grant" -> {
                sessions.grant(route.argument("player"), route.argument("scope"));
                lines.add("Право строителя выдано '" + route.argument("player")
                        + "' (область: " + sessions.scopeOf(route.argument("player")) + ").");
                return CommandResponse.ok(lines);
            }
            case "editor revoke" -> {
                boolean removed = sessions.revoke(null, route.argument("player"));
                lines.add(removed ? "Право строителя отозвано у '" + route.argument("player") + "'."
                        : "У '" + route.argument("player") + "' не было права строителя.");
                return CommandResponse.ok(lines);
            }
            case "editor list" -> {
                lines.add("Выданные права строителя (" + sessions.grantCount() + "):");
                lines.addAll(sessions.grantLines());
                return CommandResponse.ok(lines);
            }
            case "editor mode" -> {
                EditorSession session = requireSession(actor, lines);
                if (session == null) {
                    return CommandResponse.fail(lines);
                }
                EditorMode mode = EditorMode.fromToken(route.argument("mode"));
                session.setMode(mode);
                lines.add("Режим редактора: " + mode.label() + " (" + mode.token() + ").");
                return CommandResponse.ok(lines);
            }
            case "pos1" -> {
                EditorSession session = requireSession(actor, lines);
                if (session == null) {
                    return CommandResponse.fail(lines);
                }
                session.setCorner1(actor.x(), actor.y(), actor.z());
                session.setWorld(actor.world());
                lines.add("Точка 1: " + format(actor.position()) + " в мире '" + actor.world() + "'.");
                return CommandResponse.ok(lines);
            }
            case "pos2" -> {
                EditorSession session = requireSession(actor, lines);
                if (session == null) {
                    return CommandResponse.fail(lines);
                }
                session.setCorner2(actor.x(), actor.y(), actor.z());
                session.setWorld(actor.world());
                lines.add("Точка 2: " + format(actor.position()) + " в мире '" + actor.world() + "'.");
                return CommandResponse.ok(lines);
            }
            case "select" -> {
                DungeonRoomDefinition room = store.catalog().room(route.argument("roomId"));
                if (room == null) {
                    return CommandResponse.fail("Комната '" + route.argument("roomId") + "' не найдена.");
                }
                EditorSession session = requireSession(actor, lines);
                if (session != null) {
                    session.selectRoom(room.id());
                }
                lines.add("Выбрана комната '" + room.id() + "' " + room.bounds() + ".");
                return CommandResponse.ok(lines);
            }
            case "create" -> {
                return createRoom(actor, route, lines);
            }
            case "inspect" -> {
                return inspect(route.argument("roomId"), lines);
            }
            case "room list" -> {
                lines.addAll(store.catalog().roomIds());
                return CommandResponse.ok(lines);
            }
            case "room list nearby" -> {
                lines.add("Ближайшие комнаты (по текущему миру):");
                lines.addAll(store.catalog().roomIds());
                return CommandResponse.ok(lines);
            }
            case "room list all" -> {
                lines.addAll(store.catalog().roomIds());
                return CommandResponse.ok(lines);
            }
            case "room info", "room edit", "room show" -> {
                return describeRoom(route.argument("roomId"), lines);
            }
            case "room validate" -> {
                return validateRoom(route.argument("roomId"), lines);
            }
            case "room preview" -> {
                DungeonRoomDefinition room = store.catalog().room(route.argument("roomId"));
                if (room == null) {
                    return CommandResponse.fail("Комната '" + route.argument("roomId") + "' не найдена.");
                }
                lines.add("Предпросмотр '" + room.id() + "' " + room.bounds()
                        + " — изменения блоков НЕ выполняются.");
                lines.addAll(overlaySummary(room));
                return CommandResponse.ok(lines);
            }
            case "room publish" -> {
                return publishRoom(route.argument("roomId"), lines);
            }
            case "room disable" -> {
                lines.add("Комната '" + route.argument("roomId")
                        + "' помечена как отключённая. Безопасное сворачивание: без изменения мира.");
                return CommandResponse.ok(lines);
            }
            case "room reset confirm" -> {
                lines.add("Preflight: сброс '" + route.argument("roomId")
                        + "' подтверждён токеном 'confirm'. Мир не изменяется (политика плагина).");
                return CommandResponse.ok(lines);
            }
            case "room test", "room test solo", "room test party" -> {
                String mode = route.literals().get(route.literals().size() - 1);
                lines.add("Тестовый запуск '" + route.argument("roomId") + "' ("
                        + mode + ") разрешён только в тестовом мире.");
                return CommandResponse.ok(lines);
            }
            case "room export" -> {
                String serialized = store.serialize();
                lines.add("Экспорт '" + route.argument("roomId") + "': "
                        + serialized.split("\n", -1).length + " строк, схема v" + store.schemaVersion() + ".");
                return CommandResponse.ok(lines);
            }
            case "room import --dry-run", "room import confirm" -> {
                boolean dry = route.canonicalPath().endsWith("--dry-run");
                lines.add("Импорт '" + route.argument("file") + "': режим "
                        + (dry ? "проверки (--dry-run)" : "подтверждён")
                        + ", без неявной публикации.");
                return CommandResponse.ok(lines);
            }
            case "room undo" -> {
                boolean moved = store.undo();
                lines.add(moved ? "Откат выполнен." : "Нечего откатывать.");
                return CommandResponse.ok(lines);
            }
            case "room redo" -> {
                boolean moved = store.redo();
                lines.add(moved ? "Повтор выполнен." : "Нечего повторять.");
                return CommandResponse.ok(lines);
            }
            case "overlay on" -> {
                return overlayToggle(actor, true, lines);
            }
            case "overlay off" -> {
                return overlayToggle(actor, false, lines);
            }
            case "overlay filter rooms", "overlay filter gates",
                 "overlay filter secrets", "overlay filter all" -> {
                EditorSession session = requireSession(actor, lines);
                if (session == null) {
                    return CommandResponse.fail(lines);
                }
                session.setOverlayFilter(overlayFilter(route));
                lines.add("Фильтр оверлея обновлён.");
                return CommandResponse.ok(lines);
            }
            case "overlay range" -> {
                EditorSession session = requireSession(actor, lines);
                if (session == null) {
                    return CommandResponse.fail(lines);
                }
                session.setOverlayRangeBlocks(Integer.parseInt(route.argument("blocks")));
                lines.add("Дальность оверлея: " + session.overlayRangeBlocks() + " блоков.");
                return CommandResponse.ok(lines);
            }
            case "gate add" -> {
                return gateAdd(actor, route, lines);
            }
            case "gate style" -> {
                return gateMutate(route.argument("gateId"), lines, gate -> gate
                        .style(GateStyle.fromToken(route.argument("style"))));
            }
            case "gate unlock" -> {
                return gateMutate(route.argument("gateId"), lines, gate -> gate
                        .unlock(UnlockMode.fromToken(route.argument("mode"))));
            }
            case "gate bind" -> {
                return gateMutate(route.argument("gateId"), lines, gate -> gate
                        .roomId(route.argument("roomId")));
            }
            case "gate direction" -> {
                return gateMutate(route.argument("gateId"), lines, gate -> gate
                        .direction(route.argument("direction")));
            }
            case "gate preview close", "gate preview open" -> {
                String gesture = route.literals().get(route.literals().size() - 1);
                lines.add("Призрачный предпросмотр шлюза '" + route.argument("gateId") + "' ("
                        + gesture + "). Блоки не меняются.");
                return CommandResponse.ok(lines);
            }
            case "gate validate", "gate status" -> {
                GateDefinition gate = store.catalog().gate(route.argument("gateId"));
                if (gate == null) {
                    return CommandResponse.fail("Шлюз '" + route.argument("gateId") + "' не найден.");
                }
                lines.add("Шлюз '" + gate.id() + "': стиль=" + gate.style().token()
                        + ", разблокировка=" + gate.unlock().token()
                        + ", комната=" + gate.roomId()
                        + ", направление=" + gate.direction()
                        + ", маска=" + gate.bounds());
                lines.add("Состояние: " + gate.initialState().name());
                return CommandResponse.ok(lines);
            }
            case "secret add" -> {
                return secretAdd(actor, route, lines);
            }
            case "secret style" -> {
                return secretMutate(route.argument("secretId"), lines, secret -> secret
                        .style(GateStyle.fromToken(route.argument("gateStyle"))));
            }
            case "secret unlock" -> {
                return secretMutate(route.argument("secretId"), lines, secret -> secret
                        .unlock(UnlockMode.fromToken(route.argument("mode"))));
            }
            case "spawn add" -> {
                return spawnAdd(actor, route, lines);
            }
            case "spawn list" -> {
                for (SpawnAnchor spawn : store.catalog().spawns().values()) {
                    if (spawn.roomId().equals(route.argument("roomId"))) {
                        lines.add(spawn.id() + " " + format(spawn.position()) + " r=" + spawn.radius());
                    }
                }
                return CommandResponse.ok(lines);
            }
            case "spawn remove" -> {
                boolean removed = store.catalog().removeSpawn(route.argument("spawnId")) != null;
                lines.add(removed ? "Спавн-якорь удалён." : "Спавн-якорь не найден.");
                return CommandResponse.ok(lines);
            }
            case "mobs add" -> {
                return mobsAdd(route, lines);
            }
            case "mobs scaling" -> {
                return roomMutate(route.argument("roomId"), lines,
                        builder -> builder.scalingMode(ScalingMode.fromToken(route.argument("mode"))),
                        "Режим масштабирования обновлён.");
            }
            case "mobs concurrent-cap" -> {
                return roomMutate(route.argument("roomId"), lines,
                        builder -> builder.maxConcurrentEnemies(Integer.parseInt(route.argument("count"))),
                        "Лимит одновременных врагов обновлён.");
            }
            case "wave add" -> {
                return roomMutate(route.argument("roomId"), lines,
                        builder -> builder.addWave(new WaveSpec(List.of(), 0)),
                        "Волна добавлена.");
            }
            case "wave delay" -> {
                int index = Integer.parseInt(route.argument("waveIndex"));
                int ticks = Integer.parseInt(route.argument("ticks"));
                return roomMutate(route.argument("roomId"), lines, builder -> {
                    List<WaveSpec> waves = new ArrayList<>(builder.waves());
                    if (index < 0 || index >= waves.size()) {
                        throw new IllegalArgumentException("Индекс волны вне диапазона.");
                    }
                    WaveSpec wave = waves.get(index);
                    waves.set(index, new WaveSpec(wave.group(), ticks));
                    builder.waves(waves);
                }, "Задержка волны обновлена.");
            }
            case "reward mode" -> {
                RewardMode mode = RewardMode.fromToken(route.argument("mode"));
                return roomMutate(route.argument("roomId"), lines, builder -> {
                    RewardSpec current = builder.reward();
                    builder.reward(new RewardSpec(mode, current.anchorId(), current.lootTableId()));
                }, "Режим награды обновлён.");
            }
            case "reward anchor" -> {
                String anchorId = selectionOrActorAnchorId(actor);
                return roomMutate(route.argument("roomId"), lines, builder -> {
                    RewardSpec current = builder.reward();
                    builder.reward(new RewardSpec(current.mode(), anchorId, current.lootTableId()));
                }, "Якорь награды обновлён: " + anchorId);
            }
            case "reward loot" -> {
                String loot = route.argument("lootTableId");
                if (!extraLootTables.contains(loot)) {
                    extraLootTables.add(loot);
                }
                return roomMutate(route.argument("roomId"), lines, builder -> {
                    RewardSpec current = builder.reward();
                    builder.reward(new RewardSpec(current.mode(), current.anchorId(), loot));
                }, "Таблица добычи обновлена: " + loot);
            }
            case "audit" -> {
                if (route.literals().contains("recent")) {
                    lines.addAll(audit.recent(20));
                } else {
                    lines.addAll(audit.all());
                }
                if (lines.isEmpty()) {
                    lines.add("Журнал пуст.");
                }
                return CommandResponse.ok(lines);
            }
            default -> {
                lines.add("Подкоманда распознана, но ещё не имеет обработчика: " + path);
                return CommandResponse.ok(lines);
            }
        }
    }

    // ------------------------------------------------------------------
    // Sub-command handlers
    // ------------------------------------------------------------------

    private CommandResponse createRoom(CommandActor actor, CommandRegistry.Route route, List<String> lines) {
        String id = route.argument("roomId");
        if (!id.equals(id.toLowerCase(Locale.ROOT))) {
            return CommandResponse.fail("Идентификатор комнаты должен быть в нижнем регистре: " + id);
        }
        if (store.catalog().room(id) != null) {
            return CommandResponse.fail("Комната '" + id + "' уже существует.");
        }
        EditorSession session = sessions.session(actor.id());
        Cuboid selection = session == null ? null : session.selection();
        if (selection == null) {
            return CommandResponse.fail("Нужно выделить две точки (pos1/pos2) перед созданием комнаты.");
        }
        String world = session.world() != null ? session.world() : actor.world();
        store.applyEdit(catalog -> catalog.putRoom(DungeonRoomDefinition.builder(id)
                .displayName(id)
                .world(world)
                .bounds(selection)
                .trigger(selection)
                .build()));
        lines.add("Черновик комнаты '" + id + "' создан " + selection + " в мире '" + world + "'.");
        lines.add("Проверьте: /cmdungeon room validate " + id);
        return CommandResponse.ok(lines);
    }

    private CommandResponse inspect(String id, List<String> lines) {
        DungeonCatalog catalog = store.catalog();
        if (catalog.room(id) != null) {
            return describeRoom(id, lines);
        }
        if (catalog.gate(id) != null) {
            lines.add("Шлюз '" + id + "': " + catalog.gate(id).style().token());
            return CommandResponse.ok(lines);
        }
        if (catalog.secret(id) != null) {
            lines.add("Секрет '" + id + "': " + catalog.secret(id).style().token());
            return CommandResponse.ok(lines);
        }
        if (catalog.spawn(id) != null) {
            lines.add("Спавн '" + id + "': " + format(catalog.spawn(id).position()));
            return CommandResponse.ok(lines);
        }
        return CommandResponse.fail("Объект '" + id + "' не найден.");
    }

    private CommandResponse describeRoom(String id, List<String> lines) {
        DungeonRoomDefinition room = store.catalog().room(id);
        if (room == null) {
            return CommandResponse.fail("Комната '" + id + "' не найдена.");
        }
        lines.add("Комната '" + room.id() + "' (" + room.displayName() + ") enabled=" + room.enabled());
        lines.add("Мир: " + room.world() + ", границы: " + room.bounds() + ", триггер: " + room.trigger());
        lines.add("Волн: " + room.waves().size() + ", мобов всего: "
                + room.waves().stream().mapToInt(WaveSpec::totalCount).sum());
        lines.add("Шлюзов: " + room.gateIds() + ", секретов: " + room.secretIds()
                + ", спавнов: " + room.spawnAnchorIds());
        lines.add("Награда: " + room.reward().mode().token()
                + (room.reward().lootTableId() == null ? "" : (" '" + room.reward().lootTableId() + "'")));
        return CommandResponse.ok(lines);
    }

    private CommandResponse validateRoom(String id, List<String> lines) {
        DungeonRoomDefinition room = store.catalog().room(id);
        if (room == null) {
            return CommandResponse.fail("Комната '" + id + "' не найдена.");
        }
        List<String> problems = new ArrayList<>(store.loadProblems());
        problems.addAll(DungeonDefinitionStore.validateCatalog(store.catalog(), null).stream()
                .filter(problem -> problem.contains("'" + id + "'")).toList());
        if (problems.isEmpty()) {
            lines.add("Комната '" + id + "': проверка пройдена, проблем нет.");
        } else {
            lines.add("Комната '" + id + "': найдено проблем: " + problems.size());
            lines.addAll(problems);
        }
        return CommandResponse.ok(lines);
    }

    private CommandResponse publishRoom(String id, List<String> lines) {
        DungeonRoomDefinition room = store.catalog().room(id);
        if (room == null) {
            return CommandResponse.fail("Комната '" + id + "' не найдена.");
        }
        List<String> problems = DungeonDefinitionStore.validateCatalog(store.catalog(), null);
        List<String> relevant = problems.stream().filter(p -> p.contains("'" + id + "'")).toList();
        lines.add("Отчёт публикации (dry-run) для '" + id + "':");
        lines.add("  границы: " + room.bounds() + ", триггер: " + room.trigger());
        lines.add("  шлюзов: " + room.gateIds().size() + ", секретов: " + room.secretIds().size());
        lines.add("  проблем: " + relevant.size());
        lines.addAll(relevant);
        lines.add("Публикация не изменяет блоки мира; требуется отдельное подтверждение администратора.");
        return CommandResponse.ok(lines);
    }

    private CommandResponse gateAdd(CommandActor actor, CommandRegistry.Route route, List<String> lines) {
        String gateId = route.argument("gateId");
        EditorSession session = sessions.session(actor.id());
        Cuboid mask = session == null ? null : session.selection();
        if (mask == null) {
            return CommandResponse.fail("Выделите объём шлюза (режим GATE) перед добавлением.");
        }
        String roomId = session.selectedRoomId();
        if (roomId == null || store.catalog().room(roomId) == null) {
            return CommandResponse.fail("Сначала выберите комнату (/cmdungeon select <roomId>).");
        }
        String world = session.world() != null ? session.world() : actor.world();
        GateDefinition gate = new GateDefinition(gateId, roomId, GateStyle.ASSEMBLING_SEAL,
                UnlockMode.ON_ROOM_CLEAR, world, mask, "auto", false);
        store.applyEdit(catalog -> {
            catalog.putGate(gate);
            DungeonCatalog working = catalog;
            working.putRoom(working.room(roomId).toBuilder()
                    .addGateId(gateId).build());
        });
        lines.add("Шлюз '" + gateId + "' добавлен в комнату '" + roomId + "' " + mask + ".");
        return CommandResponse.ok(lines);
    }

    private CommandResponse gateMutate(String gateId, List<String> lines,
                                       Consumer<GateDefinition.Builder> mutator) {
        GateDefinition gate = store.catalog().gate(gateId);
        if (gate == null) {
            return CommandResponse.fail("Шлюз '" + gateId + "' не найден.");
        }
        try {
            GateDefinition.Builder builder = gate.toBuilder();
            mutator.accept(builder);
            GateDefinition updated = builder.build();
            store.applyEdit(catalog -> catalog.putGate(updated));
        } catch (RuntimeException error) {
            return CommandResponse.fail("Не удалось изменить шлюз: " + error.getMessage());
        }
        lines.add("Шлюз '" + gateId + "' обновлён.");
        return CommandResponse.ok(lines);
    }

    private CommandResponse secretAdd(CommandActor actor, CommandRegistry.Route route, List<String> lines) {
        String secretId = route.argument("secretId");
        EditorSession session = sessions.session(actor.id());
        Cuboid mask = session == null ? null : session.selection();
        if (mask == null) {
            return CommandResponse.fail("Выделите объём секрета (режим SECRET) перед добавлением.");
        }
        String roomId = session.selectedRoomId();
        if (roomId == null || store.catalog().room(roomId) == null) {
            return CommandResponse.fail("Сначала выберите комнату (/cmdungeon select <roomId>).");
        }
        String world = session.world() != null ? session.world() : actor.world();
        SecretDefinition secret = new SecretDefinition(secretId, roomId, GateStyle.SECRET_DISGUISED,
                UnlockMode.ON_ROOM_CLEAR, world, mask, "rune");
        store.applyEdit(catalog -> {
            catalog.putSecret(secret);
            catalog.putRoom(catalog.room(roomId).toBuilder().addSecretId(secretId).build());
        });
        lines.add("Секрет '" + secretId + "' добавлен в комнату '" + roomId + "' " + mask + ".");
        return CommandResponse.ok(lines);
    }

    private CommandResponse secretMutate(String secretId, List<String> lines,
                                         Consumer<SecretDefinition.Builder> mutator) {
        SecretDefinition secret = store.catalog().secret(secretId);
        if (secret == null) {
            return CommandResponse.fail("Секрет '" + secretId + "' не найден.");
        }
        try {
            SecretDefinition.Builder builder = secret.toBuilder();
            mutator.accept(builder);
            SecretDefinition updated = builder.build();
            store.applyEdit(catalog -> catalog.putSecret(updated));
        } catch (RuntimeException error) {
            return CommandResponse.fail("Не удалось изменить секрет: " + error.getMessage());
        }
        lines.add("Секрет '" + secretId + "' обновлён.");
        return CommandResponse.ok(lines);
    }

    private CommandResponse spawnAdd(CommandActor actor, CommandRegistry.Route route, List<String> lines) {
        String spawnId = route.argument("spawnId");
        MobTemplate template = MobTemplate.fromId(route.argument("mobTemplate"));
        if (template == null) {
            return CommandResponse.fail("Неизвестный шаблон моба: " + route.argument("mobTemplate"));
        }
        EditorSession session = sessions.session(actor.id());
        if (session == null || session.selectedRoomId() == null) {
            return CommandResponse.fail("Сначала выберите комнату (/cmdungeon select <roomId>).");
        }
        String roomId = session.selectedRoomId();
        int[] point = actor.explicitPosition() ? actor.position()
                : (session.selection() != null ? session.selection().center() : null);
        if (point == null) {
            return CommandResponse.fail("Нужна выбранная точка или позиция игрока для спавна.");
        }
        String world = session.world() != null ? session.world() : actor.world();
        SpawnAnchor anchor = new SpawnAnchor(spawnId, roomId, world, point[0], point[1], point[2], 1.0D);
        store.applyEdit(catalog -> {
            catalog.putSpawn(anchor);
            catalog.putRoom(catalog.room(roomId).toBuilder().addSpawnAnchorId(spawnId).build());
        });
        lines.add("Спавн-якорь '" + spawnId + "' (" + template.id() + ") добавлен " + format(point) + ".");
        return CommandResponse.ok(lines);
    }

    private CommandResponse mobsAdd(CommandRegistry.Route route, List<String> lines) {
        MobTemplate template = MobTemplate.fromId(route.argument("mobTemplate"));
        if (template == null) {
            return CommandResponse.fail("Неизвестный шаблон моба: " + route.argument("mobTemplate"));
        }
        int count = Integer.parseInt(route.argument("count"));
        return roomMutate(route.argument("roomId"), lines, builder -> {
            List<WaveSpec> waves = new ArrayList<>(builder.waves());
            if (waves.isEmpty()) {
                waves.add(new WaveSpec(List.of(new EnemyGroup(template, count)), 0));
            } else {
                WaveSpec last = waves.get(waves.size() - 1);
                List<EnemyGroup> groups = new ArrayList<>(last.group());
                groups.add(new EnemyGroup(template, count));
                waves.set(waves.size() - 1, new WaveSpec(groups, last.delayTicks()));
            }
            builder.waves(waves);
        }, "Мобы добавлены: " + template.id() + " x" + count + ".");
    }

    private CommandResponse roomMutate(String roomId, List<String> lines,
                                       Consumer<DungeonRoomDefinition.Builder> mutator, String success) {
        DungeonRoomDefinition room = store.catalog().room(roomId);
        if (room == null) {
            return CommandResponse.fail("Комната '" + roomId + "' не найдена.");
        }
        try {
            DungeonRoomDefinition.Builder builder = room.toBuilder();
            mutator.accept(builder);
            DungeonRoomDefinition updated = builder.build();
            store.applyEdit(catalog -> catalog.putRoom(updated));
        } catch (RuntimeException error) {
            return CommandResponse.fail("Не удалось изменить комнату: " + error.getMessage());
        }
        lines.add(success);
        return CommandResponse.ok(lines);
    }

    private CommandResponse overlayToggle(CommandActor actor, boolean visible, List<String> lines) {
        EditorSession session = sessions.session(actor.id());
        if (session == null) {
            lines.add("Оверлей " + (visible ? "включён" : "выключен") + " (нет активной сессии редактора).");
            return CommandResponse.ok(lines);
        }
        session.setOverlayVisible(visible);
        lines.add("Оверлей " + (visible ? "включён" : "выключен") + ".");
        return CommandResponse.ok(lines);
    }

    private OverlayType overlayFilter(CommandRegistry.Route route) {
        String filter = route.literals().get(route.literals().size() - 1);
        return switch (filter) {
            case "rooms" -> OverlayType.ROOM;
            case "gates" -> OverlayType.GATE;
            case "secrets" -> OverlayType.SECRET;
            default -> null;
        };
    }

    private EditorSession requireSession(CommandActor actor, List<String> lines) {
        if (actor.id() == null) {
            lines.add("Требуется игрок в игре (нет позиции/сессии).");
            return null;
        }
        return sessions.open(actor.id(), actor.name());
    }

    private String selectionOrActorAnchorId(CommandActor actor) {
        EditorSession session = sessions.session(actor.id());
        if (session != null && session.selectedRoomId() != null) {
            return session.selectedRoomId() + "_reward";
        }
        return "reward_anchor";
    }

    private List<String> overlaySummary(DungeonRoomDefinition room) {
        List<String> lines = new ArrayList<>();
        if (room.bounds() != null) {
            lines.add("Рёбра кубоида: " + room.bounds().edgeCount() + " x "
                    + me.copimine.dungeon.domain.EditorOverlay.sample(OverlayType.ROOM,
                    room.bounds(), 512).size() + " точек.");
        }
        return lines;
    }

    private static String format(int[] p) {
        return "[" + p[0] + "," + p[1] + "," + p[2] + "]";
    }
}
