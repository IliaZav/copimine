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
        Path definitions = dataFolder.resolve(getConfig().getString("dungeons.definitions_file", "dungeons.tsv"));
        store = loadStore(definitions);
        dispatcher = new DungeonCommandDispatcher(store, sessions, audit);
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
        getServer().getPluginManager().registerEvents(new EditorSessionListener(sessions), this);
        runtime = new DungeonRuntime(this, getConfig().getBoolean("dungeons.runtime.enabled", false));
        runtime.start(store);
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
            Path definitions = getDataFolder().toPath()
                    .resolve(getConfig().getString("dungeons.definitions_file", "dungeons.tsv"));
            store.save(definitions);
        } catch (IOException error) {
            getLogger().log(Level.SEVERE, "Failed to persist dungeon definitions", error);
        } finally {
            if (sessions != null) {
                sessions.closeAll();
            }
        }
    }

    private DungeonDefinitionStore loadStore(Path definitions) {
        if (Files.isRegularFile(definitions)) {
            try {
                DungeonDefinitionStore loaded = DungeonDefinitionStore.load(definitions);
                if (!loaded.loadProblems().isEmpty()) {
                    getLogger().warning("Loaded definitions with " + loaded.loadProblems().size() + " problem(s):");
                    for (String problem : loaded.loadProblems()) {
                        getLogger().warning("  " + problem);
                    }
                }
                return loaded;
            } catch (IOException | RuntimeException error) {
                getLogger().log(Level.SEVERE,
                        "Definitions file is unreadable; starting from an empty store (no data overwritten).",
                        error);
            }
        }
        return DungeonDefinitionStore.empty();
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
