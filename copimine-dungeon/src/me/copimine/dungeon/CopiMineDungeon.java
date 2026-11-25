package me.copimine.dungeon;

import me.copimine.dungeon.command.CommandActor;
import me.copimine.dungeon.command.DungeonCommand;
import me.copimine.dungeon.command.DungeonCommandDispatcher;
import me.copimine.dungeon.command.WandGateway;
import me.copimine.dungeon.editor.DungeonBuilderWand;
import me.copimine.dungeon.editor.EditorPermissions;
import me.copimine.dungeon.editor.EditorSessionListener;
import me.copimine.dungeon.editor.EditorSessionManager;
import me.copimine.dungeon.store.AuditLog;
import me.copimine.dungeon.store.DungeonDefinitionStore;
import me.copimine.dungeon.runtime.DungeonRuntime;

import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.logging.Level;

/**
 * CopiMine Dungeon — first-party dungeon builder/editor plugin (spec OV4.3-OV4.5).
 *
 * <p>The editor is deliberately inert with respect to live worlds: every
 * published/validated/previewed editor operation works on the durable definition
 * store and produces a dry-run receipt. The optional room/gate runtime (spec
 * OV4.10) is disabled by default and only runs when
 * {@code dungeons.runtime.enabled} is explicitly true; enabling it starts only
 * configured, enabled rooms in loaded worlds.</p>
 */
public final class CopiMineDungeon extends JavaPlugin {

    private DungeonDefinitionStore store;
    private EditorSessionManager sessions;
    private AuditLog audit;
    private DungeonCommandDispatcher dispatcher;
    private DungeonBuilderWand wand;
    private DungeonRuntime runtime;
    private DungeonDefinitionStore published;
    private Path publicationFile;
    private Path definitionsFile;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Path dataFolder = getDataFolder().toPath();
        try {
            Files.createDirectories(dataFolder);
        } catch (IOException error) {
            getLogger().log(Level.SEVERE, "Unable to create plugin data folder", error);
        }

        String secret = ensureWandSecret();
        Material wandMaterial = Material.matchMaterial(getConfig().getString("editor.wand.material", "BLAZE_ROD"));
        if (wandMaterial == null || !wandMaterial.isItem()) {
            wandMaterial = Material.BLAZE_ROD;
        }
        int customModelData = getConfig().getInt("editor.wand.custom_model_data", 830100);

        sessions = new EditorSessionManager();
        audit = new AuditLog();
        publicationFile = dataFolder.resolve("published-dungeons.tsv").toAbsolutePath().normalize();
        try {
            definitionsFile = dataFolder.resolve(getConfig().getString("dungeons.definitions_file", "dungeons.tsv"))
                    .toAbsolutePath().normalize();
            assertDistinctFiles(definitionsFile, publicationFile);
            assertDistinctFiles(definitionsFile, publicationFile.resolveSibling("published-dungeons.previous.tsv"));
            store = loadStore(definitionsFile);
            if (store == null) throw new IOException("Dungeon definitions are unreadable");
            if (Files.notExists(publicationFile)) {
                published = DungeonDefinitionStore.snapshot(store.catalog());
                published.save(publicationFile);
            } else {
                published = DungeonDefinitionStore.load(publicationFile);
                if (!published.loadProblems().isEmpty()) {
                    throw new IOException("Published dungeon catalogue contains unreadable records");
                }
            }
        } catch (IOException | RuntimeException error) {
            getLogger().log(Level.SEVERE, "Unable to load published dungeon definitions", error);
            store = null;
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        dispatcher = new DungeonCommandDispatcher(store, sessions, audit);
        dispatcher.setPublicationGateway(this::publishRoom);
        wand = new DungeonBuilderWand(this, secret, wandMaterial, customModelData);
        dispatcher.setWandGateway(this::giveBuilderWand);

        PluginCommand command = getCommand("cmdungeon");
        if (command != null) {
            DungeonCommand executor = new DungeonCommand(dispatcher);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().warning("Command /cmdungeon is not declared in plugin.yml; editor disabled.");
        }
        EditorSessionListener editor = new EditorSessionListener(sessions, wand, store);
        getServer().getPluginManager().registerEvents(editor, this);
        getServer().getScheduler().runTaskTimer(this, editor::drawOverlays, 5L, 10L);
        runtime = new DungeonRuntime(this, getConfig().getBoolean("dungeons.runtime.enabled", false));
        runtime.start(published);
        getLogger().info("CopiMineDungeon enabled: schema v" + store.schemaVersion()
                + ", " + store.catalog().totalDefinitions() + " definitions loaded.");
    }

    @Override
    public void onDisable() {
        if (runtime != null) {
            runtime.stop();
            runtime = null;
        }
        if (store == null) {
            return;
        }
        try {
            assertDistinctFiles(definitionsFile, publicationFile);
            assertDistinctFiles(definitionsFile, publicationFile.resolveSibling("published-dungeons.previous.tsv"));
            store.save(definitionsFile);
        } catch (IOException error) {
            getLogger().log(Level.SEVERE, "Failed to persist dungeon definitions", error);
        } finally {
            if (sessions != null) {
                sessions.closeAll();
            }
        }
    }

    private void publishRoom(String roomId, boolean enable) throws IOException {
        var source = enable ? store.catalog() : published.catalog();
        var definition = source.room(roomId);
        if (definition == null) {
            throw new IOException("Комната " + roomId + " не найдена.");
        }
        DungeonDefinitionStore next = DungeonDefinitionStore.snapshot(published.catalog());
        var catalog = next.catalog();
        catalog.gates().values().stream().filter(gate -> gate.roomId().equals(roomId))
                .map(me.copimine.dungeon.domain.GateDefinition::id).toList().forEach(catalog::removeGate);
        catalog.secrets().values().stream().filter(secret -> secret.roomId().equals(roomId))
                .map(me.copimine.dungeon.domain.SecretDefinition::id).toList().forEach(catalog::removeSecret);
        catalog.spawns().values().stream().filter(spawn -> spawn.roomId().equals(roomId))
                .map(me.copimine.dungeon.domain.SpawnAnchor::id).toList().forEach(catalog::removeSpawn);
        catalog.putRoom(definition.toBuilder().enabled(enable).build());
        for (String id : definition.gateIds()) {
            var gate = source.gate(id);
            if (gate == null || !gate.roomId().equals(roomId) || catalog.gate(id) != null
                    || !definition.world().equals(gate.world())) {
                throw new IOException("Неверный владелец или мир ворот " + id);
            }
            catalog.putGate(gate);
        }
        for (String id : definition.secretIds()) {
            var secret = source.secret(id);
            if (secret == null || !secret.roomId().equals(roomId) || catalog.secret(id) != null
                    || !definition.world().equals(secret.world())) {
                throw new IOException("Неверный владелец или мир секрета " + id);
            }
            catalog.putSecret(secret);
        }
        for (String id : definition.spawnAnchorIds()) {
            var spawn = source.spawn(id);
            if (spawn == null || !spawn.roomId().equals(roomId) || catalog.spawn(id) != null
                    || !definition.world().equals(spawn.world())) {
                throw new IOException("Неверный владелец или мир спавна " + id);
            }
            catalog.putSpawn(spawn);
        }
        var masks = new java.util.ArrayList<>(catalog.gates().values());
        catalog.secrets().values().stream().map(me.copimine.dungeon.domain.SecretDefinition::physicalGate)
                .forEach(masks::add);
        var overlaps = me.copimine.dungeon.domain.PhysicalGatePolicy.overlapConflicts(masks);
        if (!overlaps.isEmpty()) {
            throw new IOException("Маски ворот пересекаются: " + String.join(", ", overlaps.getFirst()));
        }
        if (enable) validatePublishedRoom(next, roomId);
        Runnable install = runtime.prepareRevision(next, roomId);
        published.save(publicationFile.resolveSibling("published-dungeons.previous.tsv"));
        next.save(publicationFile);
        published = next;
        install.run();
    }

    private void validatePublishedRoom(DungeonDefinitionStore next, String roomId) throws IOException {
        var catalog = next.catalog();
        var room = catalog.room(roomId);
        var world = getServer().getWorld(room.world());
        if (world == null) throw new IOException("Мир комнаты должен быть загружен.");
        var bounds = room.bounds();
        if (bounds == null || room.trigger() == null || !bounds.contains(room.trigger())
                || bounds.minY() < world.getMinHeight() || bounds.maxY() >= world.getMaxHeight()) {
            throw new IOException("Границы комнаты или триггер выходят за допустимый объём.");
        }
        var gateWorld = new me.copimine.dungeon.runtime.BukkitGateWorld(world);
        var masks = new java.util.ArrayList<me.copimine.dungeon.domain.GateDefinition>();
        room.gateIds().stream().map(catalog::gate).forEach(masks::add);
        room.secretIds().stream().map(catalog::secret)
                .map(me.copimine.dungeon.domain.SecretDefinition::physicalGate).forEach(masks::add);
        for (var gate : masks) {
            var id = gate.id();
            for (int[] cell : me.copimine.dungeon.domain.PhysicalGatePolicy.doorMask(gate)) {
                if (!gateWorld.chunkLoaded(cell[0], cell[1], cell[2])
                        || !gateWorld.mutable(cell[0], cell[1], cell[2])) {
                    throw new IOException("Маска ворот " + id + " захватывает защищённый или незагруженный блок.");
                }
            }
        }
        for (String id : room.spawnAnchorIds()) {
            var spawn = catalog.spawn(id);
            var zone = spawn.bounds();
            if (!bounds.contains(zone) || !world.isChunkLoaded(spawn.x() >> 4, spawn.z() >> 4)) {
                throw new IOException("Зона спавна " + id + " должна быть в границах комнаты и в загруженном чанке.");
            }
            boolean safeCenter = false;
            for (int y = Math.max(world.getMinHeight() + 1, zone.minY());
                    y <= Math.min(world.getMaxHeight() - 3, zone.maxY()); y++) {
                if (world.getBlockAt(spawn.x(), y - 1, spawn.z()).getType().isSolid()
                        && world.getBlockAt(spawn.x(), y, spawn.z()).getType().isAir()
                        && world.getBlockAt(spawn.x(), y + 1, spawn.z()).getType().isAir()) {
                    safeCenter = true;
                    break;
                }
            }
            if (!safeCenter) {
                throw new IOException("Спавн " + id + " должен иметь пол и два свободных блока в центре зоны.");
            }
        }
        var reward = room.reward();
        if (reward.mode() != me.copimine.dungeon.domain.RewardMode.PERSONAL) {
            throw new IOException("Выберите личную выдачу. Физическая выдача в этой версии недоступна.");
        }
        var key = org.bukkit.NamespacedKey.fromString(reward.lootTableId());
        if (key == null || org.bukkit.Bukkit.getLootTable(key) == null) {
            throw new IOException("Таблица добычи " + reward.lootTableId() + " не найдена.");
        }
    }

    private static void assertDistinctFiles(Path left, Path right) throws IOException {
        Path leftParent = left.getParent();
        Path rightParent = right.getParent();
        Path normalizedLeft = Files.isDirectory(leftParent)
                ? leftParent.toRealPath().resolve(left.getFileName()) : left;
        Path normalizedRight = Files.isDirectory(rightParent)
                ? rightParent.toRealPath().resolve(right.getFileName()) : right;
        if (normalizedLeft.equals(normalizedRight)
                || Files.exists(left) && Files.exists(right) && Files.isSameFile(left, right)) {
            throw new IOException("Draft and published dungeon definitions must use distinct files");
        }
    }

    private DungeonDefinitionStore loadStore(Path definitions) {
        if (Files.notExists(definitions, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return DungeonDefinitionStore.empty();
        }
        try {
            DungeonDefinitionStore loaded = DungeonDefinitionStore.load(definitions);
            if (loaded.loadProblems().isEmpty()) {
                return loaded;
            }
            for (String problem : loaded.loadProblems()) {
                getLogger().severe(problem);
            }
        } catch (IOException | RuntimeException error) {
            getLogger().log(Level.SEVERE, "Unable to read dungeon definitions " + definitions, error);
        }
        getLogger().severe("Dungeon disabled to preserve the original definitions file.");
        return null;
    }

    /**
     * Live {@code /cmdungeon wand give <player>} hand-off (spec OV4.3): mint the
     * branded wand whose PDC carries the server-side authenticity token, then put
     * it in the target's inventory. A full inventory drops the wand at the
     * player's feet rather than silently discarding it, and an offline target is
     * a refusal, never a fake "given" receipt.
     */
    private WandGateway.Result giveBuilderWand(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return WandGateway.Result.fail(
                    "Укажите игрока: /cmdungeon wand give <player>.");
        }
        Player target = getServer().getPlayerExact(playerName);
        if (target == null) {
            return WandGateway.Result.fail("Игрок '" + playerName
                    + "' не в сети: жезл не выдан (предмет нельзя создать оффлайн-игроку).");
        }
        ItemStack item = wand.create(target.getUniqueId(), target.getName());
        if (!target.getInventory().addItem(item).isEmpty()) {
            target.getWorld().dropItemNaturally(target.getLocation(), item);
        }
        return WandGateway.Result.ok("Жезл строителя выдан игроку '" + target.getName()
                + "'. Метка подлинности хранится в PDC; лор/текстура прав не дают.");
    }

    private String ensureWandSecret() {
        String secret = getConfig().getString("security.wand_secret", "");
        if (secret == null || secret.isBlank()) {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            secret = HexFormat.of().formatHex(random);
            getConfig().set("security.wand_secret", secret);
            saveConfig();
            getLogger().info("Generated a new server-only wand secret.");
        }
        return secret;
    }

    public DungeonCommandDispatcher dispatcher() {
        return dispatcher;
    }

    public EditorSessionManager sessions() {
        return sessions;
    }

    public DungeonBuilderWand wand() {
        return wand;
    }

    public DungeonDefinitionStore store() {
        return store;
    }

    /** Test/administration helper: dispatch as a fully-trusted console actor. */
    public List<String> dispatchAsConsole(String... args) {
        return dispatcher.execute(CommandActor.consoleActor(), List.of(args)).lines();
    }

    /** Exposed for documentation: the required permission nodes. */
    public static List<String> permissionNodes() {
        return List.of(EditorPermissions.ADMIN, EditorPermissions.BUILDER);
    }
}
