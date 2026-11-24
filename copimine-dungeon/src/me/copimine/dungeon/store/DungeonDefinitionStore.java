package me.copimine.dungeon.store;

import me.copimine.dungeon.domain.Cuboid;
import me.copimine.dungeon.domain.DungeonRoomDefinition;
import me.copimine.dungeon.domain.EnemyGroup;
import me.copimine.dungeon.domain.GateDefinition;
import me.copimine.dungeon.domain.GateStyle;
import me.copimine.dungeon.domain.MobTemplate;
import me.copimine.dungeon.domain.ParticipationMode;
import me.copimine.dungeon.domain.ParticipationPolicy;
import me.copimine.dungeon.domain.RewardMode;
import me.copimine.dungeon.domain.RewardSpec;
import me.copimine.dungeon.domain.ScalingMode;
import me.copimine.dungeon.domain.SecretDefinition;
import me.copimine.dungeon.domain.SpawnAnchor;
import me.copimine.dungeon.domain.UnlockMode;
import me.copimine.dungeon.domain.WaveSpec;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Versioned, durable dungeon definition store (spec OV4.3). Pure Java: the
 * Bukkit plugin only supplies the file path.
 *
 * <ul>
 *   <li>Deterministic tab-separated schema with a schema-version header.</li>
 *   <li>Atomic save: write temp -&gt; fsync -&gt; ATOMIC_MOVE (fallback replace).</li>
 *   <li>Migration hook for older schema versions (fails closed on newer ones).</li>
 *   <li>Validation produces human-readable Russian problems.</li>
 *   <li>Export / import with {@code --dry-run} (never an implicit publish).</li>
 *   <li>Draft undo / redo over definition edits.</li>
 * </ul>
 */
public final class DungeonDefinitionStore {

    public static final int SCHEMA_VERSION = 1;
    public static final String HEADER = "CMDUNGEON";

    private int schemaVersion;
    private DungeonCatalog catalog;
    private final Deque<String> undoStack = new ArrayDeque<>();
    private final Deque<String> redoStack = new ArrayDeque<>();
    private List<String> loadProblems = List.of();

    private DungeonDefinitionStore(DungeonCatalog catalog) {
        this.schemaVersion = SCHEMA_VERSION;
        this.catalog = catalog;
    }

    public static DungeonDefinitionStore empty() {
        return new DungeonDefinitionStore(new DungeonCatalog());
    }

    public static DungeonDefinitionStore load(Path file) throws IOException {
        String raw = Files.readString(file, StandardCharsets.UTF_8);
        int version = readSchemaVersion(raw);
        String migrated = migrate(version, raw);
        Parsed parsed = parse(migrated);
        DungeonDefinitionStore store = new DungeonDefinitionStore(parsed.catalog());
        store.schemaVersion = SCHEMA_VERSION;
        store.loadProblems = parsed.problems();
        return store;
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public DungeonCatalog catalog() {
        return catalog;
    }

    public List<String> loadProblems() {
        return loadProblems;
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    public void save(Path file) throws IOException {
        atomicWrite(file, serialize());
    }

    public void export(Path file) throws IOException {
        atomicWrite(file, serialize());
    }

    public String serialize() {
        return serializeCatalog(catalog);
    }

    public ImportResult importFile(Path file, boolean dryRun) throws IOException {
        String raw = Files.readString(file, StandardCharsets.UTF_8);
        return importText(raw, dryRun);
    }

    public ImportResult importText(String raw, boolean dryRun) {
        int version;
        try {
            version = readSchemaVersion(raw);
        } catch (IllegalArgumentException error) {
            return ImportResult.rejected(List.of(error.getMessage()));
        }
        String migrated;
        try {
            migrated = migrate(version, raw);
        } catch (IllegalStateException error) {
            return ImportResult.rejected(List.of(error.getMessage()));
        }
        Parsed parsed = parse(migrated);
        List<String> problems = new ArrayList<>(parsed.problems());
        problems.addAll(validateCatalog(parsed.catalog(), null));
        int count = parsed.catalog().totalDefinitions();
        if (!dryRun && problems.isEmpty()) {
            undoStack.push(serialize());
            redoStack.clear();
            catalog = parsed.catalog();
            loadProblems = List.of();
            return ImportResult.applied(List.of(), count);
        }
        return new ImportResult(problems, false, count);
    }

    public static String migrate(int fromVersion, String raw) {
        if (fromVersion == SCHEMA_VERSION) {
            return raw;
        }
        if (fromVersion < SCHEMA_VERSION) {
            // Sequential migration hook. No transformations are required for the
            // first published schema, but every older version must pass through
            // here rather than being silently reinterpreted.
            return raw;
        }
        throw new IllegalStateException("Формат схемы " + fromVersion
                + " новее поддерживаемого (" + SCHEMA_VERSION + "). Обновление отклонено.");
    }

    public static int readSchemaVersion(String raw) {
        for (String line : raw.split("\n", -1)) {
            if (line.isBlank()) {
                continue;
            }
            if (!line.startsWith(HEADER + "\t")) {
                throw new IllegalArgumentException(
                        "Заголовок файла определений не распознан: ожидался '" + HEADER + "\\t<версия>'.");
            }
            String[] parts = line.split("\t", -1);
            if (parts.length < 2) {
                throw new IllegalArgumentException("В заголовке файла отсутствует номер версии схемы.");
            }
            try {
                return Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("Некорректный номер версии схемы: " + parts[1]);
            }
        }
        throw new IllegalArgumentException("Файл определений пуст: отсутствует заголовок " + HEADER + ".");
    }

    private static void atomicWrite(Path file, String content) throws IOException {
        Path absolute = file.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = parent == null
                ? Paths.get(file.getFileName().toString() + ".tmp")
                : parent.resolve(file.getFileName().toString() + ".tmp");
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(tmp, StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            channel.write(ByteBuffer.wrap(bytes));
            channel.force(true);
        }
        try {
            Files.move(tmp, absolute, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(tmp, absolute, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    public List<String> validate(Set<String> knownWorlds) {
        List<String> problems = new ArrayList<>(loadProblems);
        problems.addAll(validateCatalog(catalog, knownWorlds));
        return problems;
    }

    public static List<String> validateCatalog(DungeonCatalog catalog, Set<String> knownWorlds) {
        List<String> problems = new ArrayList<>();
        for (DungeonRoomDefinition room : catalog.rooms().values()) {
            validateRoom(room, catalog, knownWorlds, problems);
        }
        for (GateDefinition gate : catalog.gates().values()) {
            if (!catalog.rooms().containsKey(gate.roomId())) {
                problems.add("Шлюз '" + gate.id() + "': комната '" + gate.roomId() + "' не найдена.");
            }
            if (gate.bounds() == null) {
                problems.add("Шлюз '" + gate.id() + "': не задана физическая маска блоков.");
            }
        }
        for (SecretDefinition secret : catalog.secrets().values()) {
            if (!catalog.rooms().containsKey(secret.roomId())) {
                problems.add("Секрет '" + secret.id() + "': комната '" + secret.roomId() + "' не найдена.");
            }
            if (secret.bounds() == null) {
                problems.add("Секрет '" + secret.id() + "': не задана маска скрытого прохода.");
            }
        }
        for (SpawnAnchor spawn : catalog.spawns().values()) {
            if (!catalog.rooms().containsKey(spawn.roomId())) {
                problems.add("Спавн '" + spawn.id() + "': комната '" + spawn.roomId() + "' не найдена.");
            }
        }
        return problems;
    }

    private static void validateRoom(DungeonRoomDefinition room, DungeonCatalog catalog,
                                     Set<String> knownWorlds, List<String> problems) {
        String id = room.id();
        if (room.bounds() == null) {
            problems.add("Комната '" + id + "': не заданы границы (нужны две точки выделения).");
        } else {
            if (room.world() == null || room.world().isBlank()) {
                problems.add("Комната '" + id + "': не задан мир.");
            } else if (knownWorlds != null && !knownWorlds.contains(room.world())) {
                problems.add("Комната '" + id + "': мир '" + room.world() + "' не найден среди загруженных.");
            }
            if (room.trigger() != null && !room.bounds().contains(room.trigger())) {
                problems.add("Комната '" + id + "': триггер выходит за границы комнаты.");
            }
        }
        if (room.waves().isEmpty()) {
            problems.add("Комната '" + id + "': не задан ни один набор мобов.");
        } else {
            int index = 0;
            for (WaveSpec wave : room.waves()) {
                if (wave.isEmpty()) {
                    problems.add("Комната '" + id + "': волна #" + index + " без мобов.");
                }
                index++;
            }
        }
        if (room.scalingMode() == ScalingMode.ADAPTIVE && room.maxConcurrentEnemies() <= 0) {
            problems.add("Комната '" + id + "': адаптивное масштабирование требует лимит одновременных врагов.");
        }
        if (room.reward().mode() == RewardMode.CHEST) {
            if (room.reward().anchorId().isEmpty()) {
                problems.add("Комната '" + id + "': режим CHEST без якоря награды.");
            } else if (!catalog.spawns().containsKey(room.reward().anchorId())) {
                problems.add("Комната '" + id + "': якорь награды '" + room.reward().anchorId()
                        + "' не найден.");
            }
        }
        for (String gateId : room.gateIds()) {
            if (!catalog.gates().containsKey(gateId)) {
                problems.add("Комната '" + id + "': шлюз '" + gateId + "' не найден.");
            }
        }
        for (String secretId : room.secretIds()) {
            if (!catalog.secrets().containsKey(secretId)) {
                problems.add("Комната '" + id + "': секрет '" + secretId + "' не найден.");
            }
        }
        for (String spawnId : room.spawnAnchorIds()) {
            if (!catalog.spawns().containsKey(spawnId)) {
                problems.add("Комната '" + id + "': спавн-якорь '" + spawnId + "' не найден.");
            }
        }
    }

    // ------------------------------------------------------------------
    // Draft undo / redo
    // ------------------------------------------------------------------

    public void applyEdit(Consumer<DungeonCatalog> mutator) {
        Objects.requireNonNull(mutator, "mutator");
        undoStack.push(serialize());
        redoStack.clear();
        mutator.accept(catalog);
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public boolean undo() {
        if (undoStack.isEmpty()) {
            return false;
        }
        redoStack.push(serialize());
        catalog = parse(undoStack.pop()).catalog();
        return true;
    }

    public boolean redo() {
        if (redoStack.isEmpty()) {
            return false;
        }
        undoStack.push(serialize());
        catalog = parse(redoStack.pop()).catalog();
        return true;
    }

    // ------------------------------------------------------------------
    // Serialization
    // ------------------------------------------------------------------

    public static String serializeCatalog(DungeonCatalog catalog) {
        StringBuilder out = new StringBuilder();
        out.append(HEADER).append('\t').append(SCHEMA_VERSION).append('\n');
        for (DungeonRoomDefinition room : catalog.rooms().values()) {
            out.append("ROOM").append('\t')
                    .append(esc(room.id())).append('\t')
                    .append(esc(room.displayName())).append('\t')
                    .append(room.enabled()).append('\t')
                    .append(esc(room.world())).append('\t')
                    .append(esc(encodeCuboid(room.bounds()))).append('\t')
                    .append(esc(encodeCuboid(room.trigger()))).append('\t')
                    .append(room.scalingMode().name()).append('\t')
                    .append(room.maxConcurrentEnemies()).append('\t')
                    .append(room.participation().mode().name()).append('\t')
                    .append(room.participation().maxPlayers() == null ? "" : room.participation().maxPlayers()).append('\t')
                    .append(room.reward().mode().name()).append('\t')
                    .append(esc(room.reward().anchorId())).append('\t')
                    .append(esc(room.reward().lootTableId())).append('\t')
                    .append(room.resetCooldownSeconds())
                    .append('\n');
            for (WaveSpec wave : room.waves()) {
                out.append("WAVE").append('\t')
                        .append(esc(room.id())).append('\t')
                        .append(wave.delayTicks()).append('\t')
                        .append(esc(encodeGroups(wave)))
                        .append('\n');
            }
        }
        for (GateDefinition gate : catalog.gates().values()) {
            out.append("GATE").append('\t')
                    .append(esc(gate.id())).append('\t')
                    .append(esc(gate.roomId())).append('\t')
                    .append(gate.style().name()).append('\t')
                    .append(gate.unlock().name()).append('\t')
                    .append(esc(gate.world())).append('\t')
                    .append(esc(encodeCuboid(gate.bounds()))).append('\t')
                    .append(esc(gate.direction())).append('\t')
                    .append(gate.initialOpen())
                    .append('\n');
        }
        for (SecretDefinition secret : catalog.secrets().values()) {
            out.append("SECRET").append('\t')
                    .append(esc(secret.id())).append('\t')
                    .append(esc(secret.roomId())).append('\t')
                    .append(secret.style().name()).append('\t')
                    .append(secret.unlock().name()).append('\t')
                    .append(esc(secret.world())).append('\t')
                    .append(esc(encodeCuboid(secret.bounds()))).append('\t')
                    .append(esc(secret.entryRule()))
                    .append('\n');
        }
        for (SpawnAnchor spawn : catalog.spawns().values()) {
            out.append("SPAWN").append('\t')
                    .append(esc(spawn.id())).append('\t')
                    .append(esc(spawn.roomId())).append('\t')
                    .append(esc(spawn.world())).append('\t')
                    .append(spawn.x()).append('\t')
                    .append(spawn.y()).append('\t')
                    .append(spawn.z()).append('\t')
                    .append(spawn.radius())
                    .append('\n');
        }
        return out.toString();
    }

    public static Parsed parse(String raw) {
        DungeonCatalog catalog = new DungeonCatalog();
        List<String> problems = new ArrayList<>();
        boolean headerSeen = false;
        for (String line : raw.split("\n", -1)) {
            if (line.isBlank()) {
                continue;
            }
            String[] fields = line.split("\t", -1);
            String kind = fields[0];
            if (HEADER.equals(kind)) {
                headerSeen = true;
                continue;
            }
            try {
                switch (kind) {
                    case "ROOM" -> parseRoom(catalog, fields, problems);
                    case "WAVE" -> parseWave(catalog, fields, problems);
                    case "GATE" -> parseGate(catalog, fields, problems);
                    case "SECRET" -> parseSecret(catalog, fields, problems);
                    case "SPAWN" -> parseSpawn(catalog, fields, problems);
                    default -> problems.add("Неизвестная запись схемы: '" + kind + "'.");
                }
            } catch (RuntimeException error) {
                problems.add("Ошибка в записи '" + kind + "': " + error.getMessage());
            }
        }
        if (!headerSeen) {
            problems.add("Отсутствует заголовок файла определений " + HEADER + ".");
        }
        return new Parsed(catalog, problems);
    }

    private static void parseRoom(DungeonCatalog catalog, String[] f, List<String> problems) {
        if (f.length < 15) {
            problems.add("Запись ROOM содержит недостаточно полей.");
            return;
        }
        String id = unesc(f[1]);
        if (catalog.rooms().containsKey(id)) {
            problems.add("Дублирующийся идентификатор комнаты '" + id + "'.");
            return;
        }
        ScalingMode scaling = ScalingMode.fromToken(f[7]);
        if (scaling == null) {
            scaling = ScalingMode.FIXED;
            problems.add("Комната '" + id + "': неизвестный режим масштабирования '" + f[7] + "'.");
        }
        ParticipationMode participation = ParticipationMode.fromToken(f[9]);
        if (participation == null) {
            participation = ParticipationMode.JOIN_SAFE;
            problems.add("Комната '" + id + "': неизвестная политика участия '" + f[9] + "'.");
        }
        Integer maxPlayers = f[10].isBlank() ? null : Integer.valueOf(f[10].trim());
        RewardMode rewardMode = RewardMode.fromToken(f[11]);
        if (rewardMode == null) {
            rewardMode = RewardMode.PERSONAL;
            problems.add("Комната '" + id + "': неизвестный режим награды '" + f[11] + "'.");
        }
        RewardSpec reward;
        try {
            reward = new RewardSpec(rewardMode, unesc(f[12]), unesc(f[13]));
        } catch (IllegalArgumentException error) {
            reward = RewardSpec.personal(unesc(f[13]));
            problems.add("Комната '" + id + "': " + error.getMessage());
        }
        DungeonRoomDefinition.Builder builder = DungeonRoomDefinition.builder(id)
                .displayName(unesc(f[2]))
                .enabled(Boolean.parseBoolean(f[3]))
                .world(unesc(f[4]))
                .bounds(decodeCuboid(unesc(f[5])))
                .trigger(decodeCuboid(unesc(f[6])))
                .scalingMode(scaling)
                .maxConcurrentEnemies(parseInt(f[8]))
                .participation(new ParticipationPolicy(participation, maxPlayers))
                .reward(reward)
                .resetCooldownSeconds(parseInt(f[14]));
        catalog.putRoom(builder.build());
    }

    private static void parseWave(DungeonCatalog catalog, String[] f, List<String> problems) {
        if (f.length < 4) {
            problems.add("Запись WAVE содержит недостаточно полей.");
            return;
        }
        String roomId = unesc(f[1]);
        DungeonRoomDefinition room = catalog.room(roomId);
        if (room == null) {
            problems.add("Волна ссылается на неизвестную комнату '" + roomId + "'.");
            return;
        }
        List<EnemyGroup> groups = new ArrayList<>();
        if (!f[3].isBlank()) {
            for (String entry : unesc(f[3]).split(",")) {
                if (entry.isBlank()) {
                    continue;
                }
                String[] pair = entry.split(":", 2);
                if (pair.length != 2) {
                    problems.add("Комната '" + roomId + "': некорректная запись группы мобов '" + entry + "'.");
                    continue;
                }
                try {
                    MobTemplate template = MobTemplate.fromId(pair[0]);
                    if (template == null) {
                        problems.add("Комната '" + roomId + "': неизвестный шаблон моба '" + pair[0] + "'.");
                        continue;
                    }
                    groups.add(new EnemyGroup(template, Integer.parseInt(pair[1].trim())));
                } catch (RuntimeException error) {
                    problems.add("Комната '" + roomId + "': " + error.getMessage());
                }
            }
        }
        List<WaveSpec> waves = new ArrayList<>(room.waves());
        waves.add(new WaveSpec(groups, parseInt(f[2])));
        catalog.putRoom(room.toBuilder().waves(waves).build());
    }

    private static void parseGate(DungeonCatalog catalog, String[] f, List<String> problems) {
        if (f.length < 9) {
            problems.add("Запись GATE содержит недостаточно полей.");
            return;
        }
        String id = unesc(f[1]);
        if (catalog.gates().containsKey(id)) {
            problems.add("Дублирующийся идентификатор шлюза '" + id + "'.");
            return;
        }
        GateStyle style = GateStyle.fromToken(f[3]);
        if (style == null) {
            problems.add("Шлюз '" + id + "': неизвестный стиль '" + f[3] + "'.");
            style = GateStyle.ASSEMBLING_SEAL;
        }
        UnlockMode unlock = UnlockMode.fromToken(f[4]);
        if (unlock == null) {
            problems.add("Шлюз '" + id + "': неизвестный режим разблокировки '" + f[4] + "'.");
            unlock = UnlockMode.ON_ROOM_CLEAR;
        }
        catalog.putGate(new GateDefinition(id, unesc(f[2]), style, unlock, unesc(f[5]),
                decodeCuboid(unesc(f[6])), unesc(f[7]), Boolean.parseBoolean(f[8])));
    }

    private static void parseSecret(DungeonCatalog catalog, String[] f, List<String> problems) {
        if (f.length < 8) {
            problems.add("Запись SECRET содержит недостаточно полей.");
            return;
        }
        String id = unesc(f[1]);
        if (catalog.secrets().containsKey(id)) {
            problems.add("Дублирующийся идентификатор секрета '" + id + "'.");
            return;
        }
        GateStyle style = GateStyle.fromToken(f[3]);
        if (style == null) {
            problems.add("Секрет '" + id + "': неизвестный стиль '" + f[3] + "'.");
            style = GateStyle.SECRET_DISGUISED;
        }
        UnlockMode unlock = UnlockMode.fromToken(f[4]);
        if (unlock == null) {
            problems.add("Секрет '" + id + "': неизвестный режим разблокировки '" + f[4] + "'.");
            unlock = UnlockMode.ON_ROOM_CLEAR;
        }
        catalog.putSecret(new SecretDefinition(id, unesc(f[2]), style, unlock, unesc(f[5]),
                decodeCuboid(unesc(f[6])), unesc(f[7])));
    }

    private static void parseSpawn(DungeonCatalog catalog, String[] f, List<String> problems) {
        if (f.length < 8) {
            problems.add("Запись SPAWN содержит недостаточно полей.");
            return;
        }
        String id = unesc(f[1]);
        if (catalog.spawns().containsKey(id)) {
            problems.add("Дублирующийся идентификатор спавна '" + id + "'.");
            return;
        }
        catalog.putSpawn(new SpawnAnchor(id, unesc(f[2]), unesc(f[3]), parseInt(f[4]),
                parseInt(f[5]), parseInt(f[6]), Double.parseDouble(f[7].trim())));
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    private static int parseInt(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Integer.parseInt(value.trim());
    }

    private static String encodeGroups(WaveSpec wave) {
        StringBuilder builder = new StringBuilder();
        for (EnemyGroup member : wave.group()) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(member.template().id()).append(':').append(member.count());
        }
        return builder.toString();
    }

    private static String encodeCuboid(Cuboid c) {
        if (c == null) {
            return "";
        }
        return c.minX() + "," + c.minY() + "," + c.minZ() + ","
                + c.maxX() + "," + c.maxY() + "," + c.maxZ();
    }

    private static Cuboid decodeCuboid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String[] p = value.split(",");
        if (p.length != 6) {
            throw new IllegalArgumentException("Ожидалось 6 координат кубоида, получено " + p.length + ".");
        }
        return new Cuboid(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()),
                Integer.parseInt(p[2].trim()), Integer.parseInt(p[3].trim()),
                Integer.parseInt(p[4].trim()), Integer.parseInt(p[5].trim()));
    }

    private static String esc(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static String unesc(String value) {
        if (value == null || value.isEmpty()) {
            return value == null ? "" : value;
        }
        StringBuilder out = new StringBuilder(value.length());
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (escaped) {
                out.append(switch (ch) {
                    case 't' -> '\t';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    default -> ch;
                });
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else {
                out.append(ch);
            }
        }
        if (escaped) {
            out.append('\\');
        }
        return out.toString();
    }

    /** Result of parsing a serialized catalogue. */
    public record Parsed(DungeonCatalog catalog, List<String> problems) {
        public Parsed {
            problems = List.copyOf(problems);
        }
    }
}
