package me.copimine.artifacts;

import me.copimine.economycore.CopiMineEconomyCore;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public final class CopiMineArtifacts extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private enum Category { WEAPON, ARMOR, TOOL, RP }
    private static final int MODEL_ARTIFACT_SHOP_MARKER = 14004;
    private enum ViewType { MAIN, CATEGORY, DETAIL, CONFIRM, PIN, PURCHASES, PENDING_DELIVERY, HELP, REPAIR, SUCCESS, ERROR, ADMIN_MAIN, ADMIN_SHOPS, ADMIN_CATALOG, ADMIN_DIAGNOSTICS }

    private record CatalogItem(
            String itemId,
            Category category,
            Material material,
            String name,
            String rarity,
            long priceAr,
            int supplyLimit,
            int perPlayerLimit,
            int cooldownSeconds,
            String effect,
            int customModelData,
            int effectChancePercent,
            String visualEffectId,
            List<String> lore
    ) {}

    private record Shop(
            String shopId,
            String title,
            String world,
            int x,
            int y,
            int z,
            boolean enabled
    ) {
        String locationKey() { return world + ":" + x + ":" + y + ":" + z; }
    }

    private record PurchaseContext(String purchaseId, String uniqueItemId, CatalogItem item, Shop shop, String pin) {}
    private record BridgePinStatus(boolean configured, boolean mustChange, long lockedSeconds) {}
    private record BridgeTxnResult(boolean ok, String code, String message, long balanceAfter, String txId) {}
    private record BridgeHealthSnapshot(boolean bridgeReady, boolean postgresReady, boolean pinReady, long balance, String context, String lastError) {}
    private record PendingDeliveryRow(String deliveryId, String purchaseId, String uniqueItemId, String itemId) {}
    private record DonationClaimRow(String claimId, String purchaseId, String itemId, long amount) {}
    private record DonationDeliveryContext(String purchaseId, CatalogItem item, List<String> uniqueItemIds) {}

    private static final class MenuHolder implements InventoryHolder {
        final UUID sessionId;
        final UUID playerUuid;
        final String shopId;
        final ViewType viewType;
        final String category;
        final String itemId;
        final int page;
        private Inventory inventory;

        MenuHolder(SessionState state, UUID playerUuid) {
            this.sessionId = state.sessionId;
            this.playerUuid = playerUuid;
            this.shopId = state.shopId == null ? "" : state.shopId;
            this.viewType = state.viewType;
            this.category = state.currentCategory == null ? "" : state.currentCategory;
            this.itemId = state.currentItemId == null ? "" : state.currentItemId;
            this.page = state.page;
        }

        void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override public Inventory getInventory() { return inventory; }
    }

    private static final class SessionState {
        UUID sessionId = UUID.randomUUID();
        String shopId = "";
        ViewType viewType = ViewType.MAIN;
        String currentCategory = "";
        String currentItemId = "";
        int page = 0;
        String pinBuffer = "";
        String purchaseInFlightId = "";
        long lastClickAt = 0L;
        long lastActionAt = 0L;
        final Map<Integer, String> actions = new HashMap<>();
    }

    private static final class PgSettings {
        final String host;
        final int port;
        final String db;
        final String schema;
        final String user;
        final String password;
        PgSettings(String host, int port, String db, String schema, String user, String password) {
            this.host = host;
            this.port = port;
            this.db = db;
            this.schema = schema;
            this.user = user;
            this.password = password;
        }
        String jdbcUrl() {
            return "jdbc:postgresql://" + host + ":" + port + "/" + db + "?currentSchema=" + schema;
        }
    }

    private static final class PgPool {
        private final PgSettings settings;
        private final Deque<Connection> idle = new ArrayDeque<>();
        private final int max;
        private int total;
        PgPool(PgSettings settings, int max) {
            this.settings = settings;
            this.max = Math.max(2, max);
        }
        synchronized Connection acquire() throws SQLException {
            long deadline = System.currentTimeMillis() + 1500L;
            while (true) {
                while (!idle.isEmpty()) {
                    Connection c = idle.pop();
                    if (c != null && !c.isClosed()) {
                        return c;
                    }
                    total--;
                }
                if (total < max) {
                    total++;
                    Connection c = DriverManager.getConnection(settings.jdbcUrl(), settings.user, settings.password);
                    c.setAutoCommit(true);
                    return c;
                }
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0L) {
                    throw new SQLException("Artifact PostgreSQL pool timed out while waiting for a free connection.");
                }
                try {
                    wait(Math.min(remaining, 250L));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new SQLException("Artifact PostgreSQL pool wait interrupted.", e);
                }
            }
        }
        synchronized void release(Connection c) {
            if (c == null) return;
            try {
                if (c.isClosed()) {
                    total--;
                } else {
                    idle.push(c);
                }
            } catch (SQLException e) {
                total--;
            }
            notifyAll();
        }
        synchronized void close() {
            for (Connection c : idle) {
                try { c.close(); } catch (SQLException ignored) {}
            }
            idle.clear();
            total = 0;
        }
    }

    private static final class VisualEffectService {
        private final JavaPlugin plugin;

        VisualEffectService(JavaPlugin plugin) {
            this.plugin = plugin;
        }

        void applyTo(LivingEntity target, String effectId, int durationSeconds) {
            if (target == null || effectId == null || effectId.isBlank()) {
                return;
            }
            int ticks = Math.max(20, durationSeconds * 20);
            Location loc = target.getLocation().add(0.0D, 1.0D, 0.0D);
            World world = loc.getWorld();
            if (world == null) {
                return;
            }
            switch (effectId.toUpperCase(Locale.ROOT)) {
                case "INVERTED_SCREEN" -> {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, ticks, 0, false, false, true));
                    if (target instanceof Player player) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Math.min(ticks, 20 * 4), 0, false, false, true));
                    }
                    world.spawnParticle(Particle.REVERSE_PORTAL, loc, 24, 0.45, 0.45, 0.45, 0.02);
                }
                case "DARK_PULSE" -> {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, Math.min(ticks, 20 * 8), 0, false, false, true));
                    world.spawnParticle(Particle.SMOKE, loc, 18, 0.4, 0.3, 0.4, 0.03);
                }
                case "MOON_GLOW" -> {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, ticks, 0, false, false, true));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, 0, false, false, true));
                    world.spawnParticle(Particle.END_ROD, loc, 22, 0.4, 0.5, 0.4, 0.01);
                }
                case "AMBER_WARP" -> {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, ticks, 0, false, false, true));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, ticks, 0, false, false, true));
                    world.spawnParticle(Particle.WAX_ON, loc, 24, 0.45, 0.45, 0.45, 0.02);
                }
                case "COLD_FOG" -> {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 0, false, false, true));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, ticks, 0, false, false, true));
                    world.spawnParticle(Particle.CLOUD, loc, 26, 0.55, 0.35, 0.55, 0.02);
                }
                case "PIXEL_WAVE" -> world.spawnParticle(Particle.WAX_OFF, loc, 20, 0.4, 0.4, 0.4, 0.01);
                case "CHROMATIC_SHIFT" -> {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Math.min(ticks, 20 * 12), 0, false, false, true));
                    world.spawnParticle(Particle.ENTITY_EFFECT, loc, 20, 0.4, 0.4, 0.4, 1.0);
                }
                case "STATIC_NOISE" -> world.spawnParticle(Particle.ASH, loc, 18, 0.4, 0.5, 0.4, 0.02);
                case "TUNNEL_VISION" -> {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Math.min(ticks, 20 * 3), 0, false, false, true));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Math.min(ticks, 20 * 6), 0, false, false, true));
                    world.spawnParticle(Particle.TRIAL_OMEN, loc, 16, 0.25, 0.25, 0.25, 0.0);
                }
                default -> plugin.getLogger().fine("Unknown visual effect id: " + effectId);
            }
        }
    }

    private static final Map<String, Integer> ARTIFACT_MODEL_DATA = Map.of(
            "zmei_gorynych", 10001
    );
    private static final Map<String, Integer> ARTIFACT_EFFECT_CHANCE = Map.of(
            "zmei_gorynych", 10
    );
    private static final Map<String, String> ARTIFACT_VISUAL_EFFECTS = Map.of(
            "zmei_gorynych", "INVERTED_SCREEN"
    );

    private final Map<String, CatalogItem> catalogById = new ConcurrentHashMap<>();
    private final Map<Category, List<CatalogItem>> catalogByCategory = new ConcurrentHashMap<>();
    private final Map<UUID, SessionState> sessions = new ConcurrentHashMap<>();
    private final Map<String, Shop> shopsByLocation = new ConcurrentHashMap<>();
    private final Map<UUID, Long> actionCooldowns = new ConcurrentHashMap<>();
    private final Map<String, String> instanceToItem = new ConcurrentHashMap<>();
    private final Set<String> suspiciousSeen = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean bridgeWarned = new AtomicBoolean(false);
    private final Random random = new Random();
    private static final long SESSION_TTL_SECONDS = 15L * 60L;
    private ExecutorService dbExecutor;
    private PgPool pgPool;
    private PgSettings pgSettings;
    private ArtifactBridgeAdapter bridge;
    private boolean debugGui;
    private VisualEffectService visualEffects;

    private NamespacedKey keyItemId;
    private NamespacedKey keyUniqueItemId;
    private NamespacedKey keyCategory;
    private NamespacedKey keyRarity;
    private NamespacedKey keyOwnerUuid;
    private NamespacedKey keyPurchaseId;
    private NamespacedKey visualEntityTypeKey;
    private NamespacedKey visualKindKey;
    private NamespacedKey visualLinkedIdKey;
    private NamespacedKey visualModelIdKey;
    private BukkitTask deliveryTask;
    private BukkitTask sessionCleanupTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        debugGui = getConfig().getBoolean("debug_gui", false);
        ensureItemsConfig();
        keyItemId = new NamespacedKey(this, "artifact_item_id");
        keyUniqueItemId = new NamespacedKey(this, "artifact_unique_item_id");
        keyCategory = new NamespacedKey(this, "artifact_category");
        keyRarity = new NamespacedKey(this, "artifact_rarity");
        keyOwnerUuid = new NamespacedKey(this, "artifact_owner_uuid");
        keyPurchaseId = new NamespacedKey(this, "artifact_purchase_id");
        visualEntityTypeKey = new NamespacedKey(this, "visual_entity_type");
        visualKindKey = new NamespacedKey(this, "visual_kind");
        visualLinkedIdKey = new NamespacedKey(this, "visual_linked_id");
        visualModelIdKey = new NamespacedKey(this, "visual_model_id");
        visualEffects = new VisualEffectService(this);
        dbExecutor = Executors.newFixedThreadPool(Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors())));
        Plugin mainPlugin = Bukkit.getPluginManager().getPlugin("CopiMineEconomyCore");
        if (mainPlugin == null || !mainPlugin.isEnabled()) {
            getLogger().severe("CopiMineEconomyCore is not enabled. CopiMineArtifacts requires the official economy bridge and will stop.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        bridge = new ArtifactBridgeAdapter();
        if (!bridge.isAvailable()) {
            getLogger().severe("CopiMineEconomyCore ArtifactsBridge is unavailable. CopiMineArtifacts will not start without the official economy bridge.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        try {
            Class.forName("org.postgresql.Driver");
            pgSettings = loadPgSettings();
            pgPool = new PgPool(pgSettings, 4);
            ensureSchema();
            loadCatalogFromConfig();
            syncCatalogToPostgres();
            loadShopsFromPostgres();
            loadInstanceCache();
            repairProtectedBlockVisuals();
            BridgeHealthSnapshot health = bridge.health(null, "artifacts-startup");
            getLogger().info("Artifacts bridge ready=" + health.bridgeReady() + " postgres=" + health.postgresReady() + " context=" + health.context());
            audit("SERVER", "bridge_ready", "CopiMineEconomyCore", "postgres=" + health.postgresReady() + " context=" + health.context());
        } catch (Exception e) {
            throw new RuntimeException("CopiMineArtifacts failed to initialize PostgreSQL: " + safeErr(e), e);
        }
        PluginCommand command = getCommand("cmartifacts");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        deliveryTask = Bukkit.getScheduler().runTaskTimer(this, this::tickPendingHints, 20L * 60L, 20L * 60L);
        sessionCleanupTask = Bukkit.getScheduler().runTaskTimer(this, this::cleanupExpiredSessions, 20L * 60L, 20L * 60L);
        getLogger().info("CopiMineArtifacts enabled with " + catalogById.size() + " active catalog items.");
    }

    public boolean knowsCatalogItem(String itemId) {
        return itemId != null && catalogById.containsKey(itemId.toLowerCase(Locale.ROOT));
    }

    @Override
    public void onDisable() {
        if (deliveryTask != null) deliveryTask.cancel();
        if (sessionCleanupTask != null) sessionCleanupTask.cancel();
        if (dbExecutor != null) {
            dbExecutor.shutdown();
            try { dbExecutor.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }
        if (pgPool != null) pgPool.close();
    }

    private void ensureItemsConfig() {
        File items = new File(getDataFolder(), "items.yml");
        if (!items.exists()) {
            getDataFolder().mkdirs();
            try {
                saveResource("items.yml", false);
            } catch (IllegalArgumentException ignored) {
                try {
                    Files.writeString(items.toPath(), defaultItemsYaml(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private String defaultItemsYaml() {
        return "items:\n" +
                "  - id: zmei_gorynych\n" +
                "    category: WEAPON\n" +
                "    material: NETHERITE_SWORD\n" +
                "    custom_model_data: 10001\n" +
                "    name: \"&6Змей Горыныч\"\n" +
                "    rarity: LEGENDARY\n" +
                "    price_ar: 500\n" +
                "    supply_limit: 0\n" +
                "    per_player_limit: 1\n" +
                "    cooldown_seconds: 12\n" +
                "    effect: ZMEI_GORYNYCH_POOP\n" +
                "    effect_chance_percent: 10\n" +
                "    visual_effect_id: INVERTED_SCREEN\n" +
                "    lore:\n" +
                "      - \"&7Официальное оружие CopiMineArtifacts\"\n" +
                "      - \"&7При ударе может вызвать электрический импульс.\"\n";
    }

    private PgSettings loadPgSettings() throws IOException {
        Map<String, String> env = new HashMap<>(System.getenv());
        for (Path candidate : envCandidates()) {
            if (!Files.isRegularFile(candidate)) continue;
            try (BufferedReader reader = Files.newBufferedReader(candidate, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String raw = line.trim();
                    if (raw.isEmpty() || raw.startsWith("#") || !raw.contains("=")) continue;
                    String[] parts = raw.split("=", 2);
                    env.putIfAbsent(parts[0].trim(), parts[1].trim());
                }
            }
            break;
        }
        String password = env.getOrDefault("POSTGRES_PASSWORD", "");
        if (password.isBlank()) throw new IOException("POSTGRES_PASSWORD is missing");
        return new PgSettings(
                env.getOrDefault("POSTGRES_HOST", "127.0.0.1"),
                parseInt(env.getOrDefault("POSTGRES_PORT", "5432"), 5432),
                env.getOrDefault("POSTGRES_DB", "copimine"),
                env.getOrDefault("POSTGRES_SCHEMA", "copimine"),
                env.getOrDefault("POSTGRES_USER", "copimine"),
                password
        );
    }

    private List<Path> envCandidates() {
        List<Path> paths = new ArrayList<>();
        String explicit = System.getenv("COPIMINE_ENV_FILE");
        if (explicit != null && !explicit.isBlank()) paths.add(Path.of(explicit));
        File data = getDataFolder();
        File plugins = data.getParentFile();
        if (plugins != null) {
            File server = plugins.getParentFile();
            if (server != null) {
                File minecraft = server.getParentFile();
                if (minecraft != null) {
                    File root = minecraft.getParentFile();
                    if (root != null) {
                        paths.add(root.toPath().resolve("admin-web").resolve(".env"));
                    }
                }
            }
        }
        return paths;
    }

    private void ensureSchema() throws SQLException {
        Connection c = pgPool.acquire();
        try (Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS artifact_items_catalog(
                    item_id TEXT PRIMARY KEY,
                    category TEXT NOT NULL,
                    material TEXT NOT NULL,
                    display_name TEXT NOT NULL,
                    rarity TEXT NOT NULL,
                    price_ar BIGINT NOT NULL,
                    supply_limit INTEGER NOT NULL DEFAULT 0,
                    per_player_limit INTEGER NOT NULL DEFAULT 0,
                    cooldown_seconds INTEGER NOT NULL DEFAULT 0,
                    effect_name TEXT NOT NULL DEFAULT 'NONE',
                    custom_model_data INTEGER NOT NULL DEFAULT 0,
                    effect_chance_percent INTEGER NOT NULL DEFAULT 100,
                    visual_effect_id TEXT NOT NULL DEFAULT '',
                    lore_json TEXT NOT NULL DEFAULT '[]',
                    enabled BOOLEAN NOT NULL DEFAULT TRUE,
                    updated_at BIGINT NOT NULL
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS artifact_item_instances(
                    unique_item_id TEXT PRIMARY KEY,
                    item_id TEXT NOT NULL,
                    owner_uuid TEXT NOT NULL,
                    purchase_id TEXT NOT NULL,
                    status TEXT NOT NULL,
                    repaired_count INTEGER NOT NULL DEFAULT 0,
                    created_at BIGINT NOT NULL,
                    updated_at BIGINT NOT NULL
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS artifact_shops(
                    shop_id TEXT PRIMARY KEY,
                    world_name TEXT NOT NULL,
                    block_x INTEGER NOT NULL,
                    block_y INTEGER NOT NULL,
                    block_z INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    enabled BOOLEAN NOT NULL DEFAULT TRUE,
                    created_at BIGINT NOT NULL,
                    updated_at BIGINT NOT NULL
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS protected_block_visuals(
                    id TEXT PRIMARY KEY,
                    kind TEXT NOT NULL,
                    linked_id TEXT NOT NULL,
                    world TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    entity_uuid TEXT NOT NULL DEFAULT '',
                    base_material TEXT NOT NULL DEFAULT 'PAPER',
                    custom_model_data INTEGER NOT NULL DEFAULT 0,
                    model_id TEXT NOT NULL DEFAULT '',
                    offset_x DOUBLE PRECISION NOT NULL DEFAULT 0.5,
                    offset_y DOUBLE PRECISION NOT NULL DEFAULT 0.5,
                    offset_z DOUBLE PRECISION NOT NULL DEFAULT 0.5,
                    scale_x DOUBLE PRECISION NOT NULL DEFAULT 1.01,
                    scale_y DOUBLE PRECISION NOT NULL DEFAULT 1.01,
                    scale_z DOUBLE PRECISION NOT NULL DEFAULT 1.01,
                    yaw DOUBLE PRECISION NOT NULL DEFAULT 0,
                    pitch DOUBLE PRECISION NOT NULL DEFAULT 0,
                    created_at BIGINT NOT NULL DEFAULT 0,
                    updated_at BIGINT NOT NULL DEFAULT 0,
                    active INTEGER NOT NULL DEFAULT 1
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS artifact_purchases(
                    purchase_id TEXT PRIMARY KEY,
                    unique_item_id TEXT NOT NULL,
                    player_uuid TEXT NOT NULL,
                    player_name TEXT NOT NULL,
                    item_id TEXT NOT NULL,
                    shop_id TEXT NOT NULL,
                    price_ar BIGINT NOT NULL,
                    bank_tx_id TEXT NOT NULL DEFAULT '',
                    idempotency_key TEXT NOT NULL,
                    status TEXT NOT NULL,
                    delivery_mode TEXT NOT NULL DEFAULT 'DIRECT',
                    created_at BIGINT NOT NULL,
                    updated_at BIGINT NOT NULL
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS artifact_repairs(
                    repair_id TEXT PRIMARY KEY,
                    unique_item_id TEXT NOT NULL,
                    player_uuid TEXT NOT NULL,
                    player_name TEXT NOT NULL,
                    item_id TEXT NOT NULL,
                    repair_cost_ar BIGINT NOT NULL,
                    bank_tx_id TEXT NOT NULL DEFAULT '',
                    status TEXT NOT NULL,
                    created_at BIGINT NOT NULL
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS artifact_suspicious_events(
                    event_id TEXT PRIMARY KEY,
                    player_uuid TEXT NOT NULL,
                    player_name TEXT NOT NULL,
                    event_type TEXT NOT NULL,
                    details TEXT NOT NULL,
                    created_at BIGINT NOT NULL
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS artifact_audit_log(
                    audit_id TEXT PRIMARY KEY,
                    actor TEXT NOT NULL,
                    action TEXT NOT NULL,
                    target_id TEXT NOT NULL,
                    details TEXT NOT NULL,
                    created_at BIGINT NOT NULL
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS artifact_pending_deliveries(
                    delivery_id TEXT PRIMARY KEY,
                    purchase_id TEXT NOT NULL,
                    unique_item_id TEXT NOT NULL,
                    player_uuid TEXT NOT NULL,
                    item_id TEXT NOT NULL,
                    status TEXT NOT NULL,
                    created_at BIGINT NOT NULL,
                    updated_at BIGINT NOT NULL
                )
            """);
            st.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_artifact_shops_block ON artifact_shops(world_name,block_x,block_y,block_z)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_artifact_purchases_player_time ON artifact_purchases(player_uuid,created_at DESC)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_artifact_instances_item ON artifact_item_instances(item_id,status)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_artifact_pending_player ON artifact_pending_deliveries(player_uuid,status,created_at DESC)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_artifact_repairs_player ON artifact_repairs(player_uuid,created_at DESC)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_artifact_suspicious_time ON artifact_suspicious_events(created_at DESC,event_type)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_protected_block_visuals_linked ON protected_block_visuals(linked_id,active)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_protected_block_visuals_location ON protected_block_visuals(world,x,y,z,active)");
            st.execute("ALTER TABLE artifact_items_catalog ADD COLUMN IF NOT EXISTS supply_limit INTEGER NOT NULL DEFAULT 0");
            st.execute("ALTER TABLE artifact_items_catalog ADD COLUMN IF NOT EXISTS per_player_limit INTEGER NOT NULL DEFAULT 0");
            st.execute("ALTER TABLE artifact_items_catalog ADD COLUMN IF NOT EXISTS custom_model_data INTEGER NOT NULL DEFAULT 0");
            st.execute("ALTER TABLE artifact_items_catalog ADD COLUMN IF NOT EXISTS effect_chance_percent INTEGER NOT NULL DEFAULT 100");
            st.execute("ALTER TABLE artifact_items_catalog ADD COLUMN IF NOT EXISTS visual_effect_id TEXT NOT NULL DEFAULT ''");
        } finally {
            pgPool.release(c);
        }
    }

    private void loadCatalogFromConfig() {
        catalogById.clear();
        EnumMap<Category, List<CatalogItem>> byCategory = new EnumMap<>(Category.class);
        for (Category category : Category.values()) byCategory.put(category, new ArrayList<>());
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "items.yml"));
        List<Map<?, ?>> rows = yaml.getMapList("items");
        for (Map<?, ?> row : rows) {
            String itemId = str(row.get("id"));
            if (itemId.isBlank()) continue;
            Category category = parseCategory(str(row.get("category")));
            Material material = Material.matchMaterial(str(row.get("material")));
            if (material == null) material = Material.STONE;
            List<String> lore = asStringList(row.get("lore"));
            Object visualEffectValue = row.containsKey("visual_effect_id") ? row.get("visual_effect_id") : artifactVisualEffect(itemId);
            CatalogItem item = new CatalogItem(
                    itemId,
                    category,
                    material,
                    str(row.get("name")),
                    str(row.get("rarity")),
                    parseLong(str(row.get("price_ar")), 0L),
                    Math.max(0, parseInt(str(row.get("supply_limit")), 0)),
                    Math.max(0, parseInt(str(row.get("per_player_limit")), 0)),
                    parseInt(str(row.get("cooldown_seconds")), 0),
                    str(row.get("effect")),
                    parseInt(str(row.get("custom_model_data")), artifactModelData(itemId)),
                    normalizeChance(parseInt(str(row.get("effect_chance_percent")), artifactEffectChance(itemId))),
                    str(visualEffectValue),
                    lore
            );
            catalogById.put(itemId, item);
            byCategory.get(category).add(item);
        }
        catalogByCategory.clear();
        for (Map.Entry<Category, List<CatalogItem>> e : byCategory.entrySet()) {
            catalogByCategory.put(e.getKey(), List.copyOf(e.getValue()));
        }
    }

    private void syncCatalogToPostgres() throws SQLException {
        Connection c = pgPool.acquire();
        try {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO artifact_items_catalog(item_id,category,material,display_name,rarity,price_ar,supply_limit,per_player_limit,cooldown_seconds,effect_name,custom_model_data,effect_chance_percent,visual_effect_id,lore_json,enabled,updated_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(item_id) DO UPDATE SET
                    category=EXCLUDED.category,
                    material=EXCLUDED.material,
                    display_name=EXCLUDED.display_name,
                    rarity=EXCLUDED.rarity,
                    price_ar=EXCLUDED.price_ar,
                    supply_limit=EXCLUDED.supply_limit,
                    per_player_limit=EXCLUDED.per_player_limit,
                    cooldown_seconds=EXCLUDED.cooldown_seconds,
                    effect_name=EXCLUDED.effect_name,
                    custom_model_data=EXCLUDED.custom_model_data,
                    effect_chance_percent=EXCLUDED.effect_chance_percent,
                    visual_effect_id=EXCLUDED.visual_effect_id,
                    lore_json=EXCLUDED.lore_json,
                    enabled=EXCLUDED.enabled,
                    updated_at=EXCLUDED.updated_at
            """)) {
                long now = now();
                for (CatalogItem item : catalogById.values()) {
                    ps.setString(1, item.itemId());
                    ps.setString(2, item.category().name());
                    ps.setString(3, item.material().name());
                    ps.setString(4, item.name());
                    ps.setString(5, item.rarity());
                    ps.setLong(6, item.priceAr());
                    ps.setInt(7, item.supplyLimit());
                    ps.setInt(8, item.perPlayerLimit());
                    ps.setInt(9, item.cooldownSeconds());
                    ps.setString(10, item.effect());
                    ps.setInt(11, item.customModelData());
                    ps.setInt(12, item.effectChancePercent());
                    ps.setString(13, item.visualEffectId());
                    ps.setString(14, toJson(item.lore()));
                    ps.setBoolean(15, item.category() != Category.RP);
                    ps.setLong(16, now);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            c.commit();
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            try { c.setAutoCommit(true); } catch (SQLException ignored) {}
            pgPool.release(c);
        }
    }

    private void loadShopsFromPostgres() throws SQLException {
        shopsByLocation.clear();
        Connection c = pgPool.acquire();
        try (PreparedStatement ps = c.prepareStatement("SELECT shop_id,title,world_name,block_x,block_y,block_z,enabled FROM artifact_shops")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Shop shop = new Shop(
                            rs.getString(1),
                            rs.getString(2),
                            rs.getString(3),
                            rs.getInt(4),
                            rs.getInt(5),
                            rs.getInt(6),
                            rs.getBoolean(7)
                    );
                    shopsByLocation.put(shop.locationKey(), shop);
                }
            }
        } finally {
            pgPool.release(c);
        }
    }

    private void loadInstanceCache() throws SQLException {
        instanceToItem.clear();
        Connection c = pgPool.acquire();
        try (PreparedStatement ps = c.prepareStatement("SELECT unique_item_id,item_id FROM artifact_item_instances WHERE status IN ('DELIVERED','PENDING_DELIVERY','DONATION_DELIVERING')")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    instanceToItem.put(rs.getString(1), rs.getString(2));
                }
            }
        } finally {
            pgPool.release(c);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShopInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        Shop shop = shopsByLocation.get(blockKey(block.getLocation()));
        if (shop == null) return;
        event.setCancelled(true);
        if (!shop.enabled()) {
            event.getPlayer().sendMessage(color("&cМагазин временно недоступен."));
            return;
        }
        if (!event.getPlayer().hasPermission("copimine.artifacts.use")) {
            event.getPlayer().sendMessage(color("&cНет доступа к CopiMineArtifacts."));
            return;
        }
        openMain(event.getPlayer(), shop, true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShopVisualInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemDisplay display)) return;
        PersistentDataContainer pdc = display.getPersistentDataContainer();
        if (!"PROTECTED_BLOCK_VISUAL".equals(pdc.get(visualEntityTypeKey, PersistentDataType.STRING))) return;
        if (!"ARTIFACT_SHOP".equals(first(pdc.get(visualKindKey, PersistentDataType.STRING), ""))) return;
        String shopId = first(pdc.get(visualLinkedIdKey, PersistentDataType.STRING), "");
        if (shopId.isBlank()) return;
        event.setCancelled(true);
        Shop shop = shopsByLocation.values().stream().filter(candidate -> candidate.shopId().equalsIgnoreCase(shopId)).findFirst().orElse(null);
        if (shop == null || !shop.enabled()) {
            event.getPlayer().sendMessage(color("&cЛавка сейчас недоступна."));
            return;
        }
        if (!event.getPlayer().hasPermission("copimine.artifacts.use")) {
            event.getPlayer().sendMessage(color("&cНет доступа к CopiMineArtifacts."));
            return;
        }
        openMain(event.getPlayer(), shop, true);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!customBlockVisualsEnabled()) return;
        try {
            repairProtectedBlockVisuals(event.getWorld().getName(), event.getChunk().getX(), event.getChunk().getZ());
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Artifact shop visual repair failed for chunk load", e);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShopBreak(BlockBreakEvent event) {
        Shop shop = shopsByLocation.get(blockKey(event.getBlock().getLocation()));
        if (shop != null) {
            if (!isArtifactsAdmin(event.getPlayer())) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(color("&cЛавка CopiMineArtifacts снимается только через /cmartifacts shop remove."));
            }
            return;
        }
        Player player = event.getPlayer();
        CatalogItem catalog = authenticCatalogItem(player.getInventory().getItemInMainHand(), player, "tool_use");
        if (catalog == null) return;
        String effect = catalog.effect().toUpperCase(Locale.ROOT);
        if (!artifactToolEffects().contains(effect)) return;
        long cooldownUntil = actionCooldowns.getOrDefault(player.getUniqueId(), 0L);
        long now = now();
        if (cooldownUntil > now) return;
        actionCooldowns.put(player.getUniqueId(), now + Math.max(2, catalog.cooldownSeconds()));
        switch (effect) {
            case "MINER_PULSE", "HASTE_BURST" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 20 * 5, 1, false, false, true));
                player.getWorld().spawnParticle(Particle.ENCHANT, event.getBlock().getLocation().add(0.5, 0.8, 0.5), 10, 0.35, 0.35, 0.35, 0.01);
            }
            case "FORESTER_FOCUS" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 20 * 4, 0, false, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 3, 0, false, false, true));
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_WOOD_BREAK, 0.35f, 1.25f);
            }
            case "SURVEYOR_TOUCH" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 3, 0, false, false, true));
                player.getWorld().spawnParticle(Particle.DUST, event.getBlock().getLocation().add(0.5, 0.5, 0.5), 8, 0.25, 0.25, 0.25, 0.01);
            }
            case "CRAFTSMAN_CHECK" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 20 * 5, 0, false, false, true));
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.25f, 1.6f);
            }
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= top.getSize()) return;
        if (!holder.playerUuid.equals(player.getUniqueId())) {
            debugGui("wrong-player holder=" + holder.playerUuid + " clicker=" + player.getUniqueId() + " session=" + holder.sessionId);
            player.closeInventory();
            player.sendMessage(color("&cЭто меню открыто не для вас. Откройте лавку заново."));
            return;
        }
        SessionState state = sessions.get(player.getUniqueId());
        if (state == null || !state.sessionId.equals(holder.sessionId)) {
            debugGui("stale-session player=" + player.getName() + " screen=" + holder.viewType + " rawSlot=" + rawSlot + " holderSession=" + holder.sessionId + " currentSession=" + (state == null ? "missing" : state.sessionId));
            player.closeInventory();
            player.sendMessage(color("&cМеню устарело. Откройте лавку заново."));
            return;
        }
        if (event.getClick().isShiftClick() || event.getHotbarButton() >= 0) return;
        String action = state.actions.getOrDefault(rawSlot, "");
        debugGui("click player=" + player.getName() + " screen=" + holder.viewType + " rawSlot=" + rawSlot + " action=" + (action.isBlank() ? "none" : action) + " shop=" + holder.shopId + " category=" + holder.category + " item=" + holder.itemId + " session=" + holder.sessionId);
        if (action.isBlank()) {
            debugGui("no-action player=" + player.getName() + " screen=" + holder.viewType + " rawSlot=" + rawSlot + " session=" + holder.sessionId);
            return;
        }
        if (System.currentTimeMillis() - state.lastClickAt < 150L && action.startsWith("confirm:")) return;
        state.lastClickAt = System.currentTimeMillis();
        state.lastActionAt = System.currentTimeMillis();
        try {
            handleMenuAction(player, state, action);
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Artifact GUI click failed screen=" + holder.viewType + " rawSlot=" + rawSlot + " action=" + action, ex);
            player.sendMessage(color("&cНе удалось выполнить действие. Ошибка записана в лог."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getInventory().getHolder() instanceof MenuHolder holder) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!player.isOnline()) return;
                Inventory top = player.getOpenInventory().getTopInventory();
                if (top.getHolder() instanceof MenuHolder nextHolder) {
                    debugGui("close-transition player=" + player.getName() + " from=" + holder.viewType + " to=" + nextHolder.viewType + " session=" + nextHolder.sessionId);
                    return;
                }
                SessionState state = sessions.get(player.getUniqueId());
                if (state != null && state.purchaseInFlightId.isBlank() && state.sessionId.equals(holder.sessionId)) {
                    sessions.remove(player.getUniqueId());
                    debugGui("session-cleanup player=" + player.getName() + " screen=" + holder.viewType + " session=" + holder.sessionId);
                }
            }, 2L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        SessionState state = sessions.get(event.getEntity().getUniqueId());
        if (state == null || !state.purchaseInFlightId.isBlank()) return;
        state.actions.clear();
        state.pinBuffer = "";
    }

    private void cleanupExpiredSessions() {
        long cutoff = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(SESSION_TTL_SECONDS);
        sessions.entrySet().removeIf(entry -> {
            SessionState state = entry.getValue();
            return state != null
                    && state.purchaseInFlightId.isBlank()
                    && state.lastActionAt > 0L
                    && state.lastActionAt < cutoff;
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        runAsync(() -> {
            int count = pendingCount(event.getPlayer().getUniqueId().toString());
            if (count > 0) {
                runSync(() -> event.getPlayer().sendMessage(color("&eУ вас есть отложенная выдача CopiMineArtifacts: &f" + count + "&e. Используйте &f/cmartifacts claim&e.")));
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArtifactDamage(EntityDamageByEntityEvent event) {
        Player player = null;
        if (event.getDamager() instanceof Player directPlayer) {
            player = directPlayer;
        } else if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            player = shooter;
        }
        if (player == null) return;
        ItemStack item = player.getInventory().getItemInMainHand();
        CatalogItem catalog = authenticCatalogItem(item, player, "use");
        if (catalog == null) return;
        String effect = catalog.effect().toUpperCase(Locale.ROOT);
        if (!artifactCombatEffects().contains(effect)) return;
        if (!rollEffectChance(catalog)) return;
        long cooldownUntil = actionCooldowns.getOrDefault(player.getUniqueId(), 0L);
        long now = now();
        if (cooldownUntil > now) return;
        actionCooldowns.put(player.getUniqueId(), now + catalog.cooldownSeconds());
        LivingEntity target = event.getEntity() instanceof LivingEntity living ? living : null;
        Location loc = event.getEntity().getLocation().add(0, 1, 0);
        switch (effect) {
            case "LIGHTNING" -> {
                loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 10, 0.3, 0.3, 0.3, 0.01);
                loc.getWorld().playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5f, 1.8f);
                event.setDamage(event.getDamage() + 2.0D);
            }
            case "DRAGON_PUNISHMENT" -> {
                loc.getWorld().spawnParticle(Particle.DRAGON_BREATH, loc, 16, 0.35, 0.35, 0.35, 0.01);
                loc.getWorld().playSound(loc, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.35f, 1.45f);
                event.setDamage(event.getDamage() + 1.5D);
                if (target != null) target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 20 * 4, 0, false, false, true));
            }
            case "WATCH_GLOW" -> {
                if (target != null) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 6, 0, false, false, true));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 20 * 3, 0, false, false, true));
                }
                loc.getWorld().spawnParticle(Particle.ENCHANT, loc, 14, 0.4, 0.4, 0.4, 0.02);
                loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.45f, 1.35f);
            }
            case "DEBT_SNARE" -> {
                if (target != null) target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 3, 0, false, false, true));
                loc.getWorld().spawnParticle(Particle.CRIT, loc, 12, 0.35, 0.25, 0.35, 0.05);
                loc.getWorld().playSound(loc, Sound.BLOCK_CHAIN_HIT, 0.5f, 0.8f);
            }
            case "SMUGGLER_MARK" -> {
                if (target != null) target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 5, 0, false, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 3, 0, false, false, true));
                loc.getWorld().spawnParticle(Particle.SMOKE, loc, 8, 0.3, 0.3, 0.3, 0.02);
                loc.getWorld().playSound(loc, Sound.ENTITY_ARROW_HIT_PLAYER, 0.35f, 1.7f);
            }
            case "ZMEI_GORYNYCH_POOP" -> {
                if (target != null) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 6, 0, false, false, true));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 20 * 8, 0, false, false, true));
                }
                loc.getWorld().spawnParticle(Particle.DRAGON_BREATH, loc, 24, 0.45, 0.35, 0.45, 0.01);
                loc.getWorld().spawnParticle(Particle.SMOKE, loc, 18, 0.35, 0.25, 0.35, 0.03);
            }
            default -> { }
        }
        if (target != null && !catalog.visualEffectId().isBlank()) {
            visualEffects.applyTo(target, catalog.visualEffectId(), Math.max(4, catalog.cooldownSeconds()));
            if ("ZMEI_GORYNYCH_POOP".equals(effect)) {
                visualEffects.applyTo(target, "DARK_PULSE", 4);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length == 0) {
            if (isArtifactsAdmin(player)) {
                openArtifactsAdminMenu(player);
            } else {
                sendPlayerShopHelp(player);
            }
            return true;
        }
        if ("claim".equalsIgnoreCase(args[0])) {
            claimPending(player);
            return true;
        }
        if ("repair".equalsIgnoreCase(args[0])) {
            openRepair(player);
            return true;
        }
        if ("admin".equalsIgnoreCase(args[0])) {
            if (!isArtifactsAdmin(player)) {
                noPermission(player);
                return true;
            }
            openArtifactsAdminMenu(player);
            return true;
        }
        if ("reload".equalsIgnoreCase(args[0])) {
            if (!hasArtifactPermission(player, "copimine.artifacts.reload")) {
                noPermission(player);
                return true;
            }
            reloadConfig();
            debugGui = getConfig().getBoolean("debug_gui", false);
            loadCatalogFromConfig();
            try {
                syncCatalogToPostgres();
                player.sendMessage(color("&aCopiMineArtifacts catalog reloaded."));
            } catch (SQLException e) {
                getLogger().log(Level.WARNING, "Artifacts reload failed", e);
                player.sendMessage(color("&cНе удалось выполнить действие. Ошибка записана в лог. Код: ARTIFACT_RELOAD_FAILED"));
            }
            return true;
        }
        if ("shop".equalsIgnoreCase(args[0])) {
            return handleShopCommand(player, Arrays.copyOfRange(args, 1, args.length));
        }
        player.sendMessage(color("&cНеизвестная команда CopiMineArtifacts."));
        return true;
    }

    private boolean handleShopCommand(Player player, String[] args) {
        if (args.length == 0) {
            if (!isArtifactsAdmin(player)) {
                noPermission(player);
                return true;
            }
            player.sendMessage(color("&e/cmartifacts shop create <shop_id>"));
            player.sendMessage(color("&e/cmartifacts shop remove"));
            player.sendMessage(color("&e/cmartifacts shop list"));
            player.sendMessage(color("&e/cmartifacts shop open <shop_id>"));
            return true;
        }
        if ("create".equalsIgnoreCase(args[0])) {
            if (!hasArtifactPermission(player, "copimine.artifacts.shop.create")) {
                noPermission(player);
                return true;
            }
            if (args.length < 2) {
                player.sendMessage(color("&cУкажи shop_id."));
                return true;
            }
            Block target = player.getTargetBlockExact(6);
            if (target == null) {
                player.sendMessage(color("&cСмотри на блок лавки в пределах 6 блоков."));
                return true;
            }
            String key = blockKey(target.getLocation());
            if (shopsByLocation.containsKey(key)) {
                player.sendMessage(color("&cНа этом блоке уже есть лавка CopiMineArtifacts."));
                return true;
            }
            String shopId = args[1].toLowerCase(Locale.ROOT);
            Shop shop = new Shop(shopId, "CopiMine Artifacts", target.getWorld().getName(), target.getX(), target.getY(), target.getZ(), true);
            runAsync(() -> {
                try {
                    saveShop(shop);
                    shopsByLocation.put(shop.locationKey(), shop);
                    audit(player.getName(), "shop_create", shop.shopId(), shop.locationKey());
                    runSync(() -> {
                        try {
                            spawnOrReplaceProtectedBlockVisual(target.getLocation(), "ARTIFACT_SHOP", shop.shopId(), Material.PAPER, MODEL_ARTIFACT_SHOP_MARKER, "artifact_shop_marker");
                        } catch (Exception e) {
                            getLogger().log(Level.WARNING, "Artifact shop visual create failed", e);
                        }
                    });
                    runSync(() -> player.sendMessage(color("&aЛавка создана: &f" + shop.shopId())));
                } catch (SQLException e) {
                    getLogger().log(Level.WARNING, "Artifact shop create failed", e);
                    runSync(() -> player.sendMessage(color("&cНе удалось выполнить действие. Ошибка записана в лог. Код: ARTIFACT_SHOP_SAVE_FAILED")));
                }
            });
            return true;
        }
        if ("remove".equalsIgnoreCase(args[0])) {
            if (!hasArtifactPermission(player, "copimine.artifacts.shop.remove")) {
                noPermission(player);
                return true;
            }
            Block target = player.getTargetBlockExact(6);
            if (target == null) {
                player.sendMessage(color("&cСмотри на блок лавки в пределах 6 блоков."));
                return true;
            }
            Shop shop = shopsByLocation.get(blockKey(target.getLocation()));
            if (shop == null) {
                player.sendMessage(color("&cНа этом блоке нет лавки CopiMineArtifacts."));
                return true;
            }
            runAsync(() -> {
                try {
                    deleteShop(shop.shopId());
                    shopsByLocation.remove(shop.locationKey());
                    audit(player.getName(), "shop_remove", shop.shopId(), shop.locationKey());
                    runSync(() -> {
                        try {
                            cleanupProtectedBlockVisuals("ARTIFACT_SHOP", shop.shopId());
                        } catch (Exception e) {
                            getLogger().log(Level.WARNING, "Artifact shop visual cleanup failed", e);
                        }
                    });
                    runSync(() -> player.sendMessage(color("&aЛавка удалена: &f" + shop.shopId())));
                } catch (SQLException e) {
                    getLogger().log(Level.WARNING, "Artifact shop remove failed", e);
                    runSync(() -> player.sendMessage(color("&cНе удалось выполнить действие. Ошибка записана в лог. Код: ARTIFACT_SHOP_REMOVE_FAILED")));
                }
            });
            return true;
        }
        if ("list".equalsIgnoreCase(args[0])) {
            if (!hasArtifactPermission(player, "copimine.artifacts.shop.list")) {
                noPermission(player);
                return true;
            }
            player.sendMessage(color("&eЛавки CopiMineArtifacts: &f" + shopsByLocation.values().stream().map(Shop::shopId).collect(Collectors.joining(", "))));
            return true;
        }
        if ("open".equalsIgnoreCase(args[0])) {
            if (!isArtifactsAdmin(player)) {
                noPermission(player);
                return true;
            }
            if (args.length < 2) {
                player.sendMessage(color("&cУкажи shop_id."));
                return true;
            }
            Shop shop = shopsByLocation.values().stream().filter(s -> s.shopId().equalsIgnoreCase(args[1])).findFirst().orElse(null);
            if (shop == null) {
                player.sendMessage(color("&cЛавка не найдена: &f" + args[1]));
                return true;
            }
            openMain(player, shop, true);
            return true;
        }
        player.sendMessage(color("&cНеизвестная команда лавки."));
        return true;
    }

    private void openArtifactsAdminMenu(Player player) {
        SessionState state = freshSession(player);
        state.viewType = ViewType.ADMIN_MAIN;
        Inventory inv = createMenu(player, state, ViewType.ADMIN_MAIN, 27, "&8Artifacts Admin");
        inv.setItem(4, button(Material.NETHER_STAR, "&aCopiMineArtifacts", List.of(
                "&7Служебная панель лавок и каталога.",
                "&8Игроки открывают лавку только кликом по блоку."
        )));
        setAction(inv, state, 10, button(Material.CHEST, "&eЛавки", List.of("&7Список активных блоков лавки.")), "admin:shops");
        setAction(inv, state, 12, button(Material.BOOK, "&eКаталог", List.of("&7Количество товаров по категориям.")), "admin:catalog");
        setAction(inv, state, 14, button(Material.COMPARATOR, "&eДиагностика", List.of("&7Bridge, PostgreSQL, PIN и кэш.")), "admin:diagnostics");
        setAction(inv, state, 16, button(Material.CLOCK, "&aReload", List.of("&7Перезагрузить config.yml и items.yml.")), "admin:reload");
        setAction(inv, state, 22, button(Material.BARRIER, "&cЗакрыть", List.of("&7Выйти из меню.")), "close");
        player.openInventory(inv);
    }

    private void openAdminShops(Player player) {
        SessionState state = session(player);
        state.viewType = ViewType.ADMIN_SHOPS;
        Inventory inv = createMenu(player, state, ViewType.ADMIN_SHOPS, 54, "&8Лавки артефактов");
        inv.setItem(4, button(Material.CHEST, "&eЛавки артефактов", List.of(
                "&7Создание: /cmartifacts shop create <id>",
                "&7Удаление: /cmartifacts shop remove"
        )));
        int slot = 10;
        for (Shop shop : shopsByLocation.values()) {
            if (slot >= 35) break;
            inv.setItem(slot, button(Material.BARREL, "&f" + shop.shopId(), List.of(
                    "&7Мир: &f" + shop.world(),
                    "&7XYZ: &f" + shop.x() + " " + shop.y() + " " + shop.z(),
                    "&7Статус: " + (shop.enabled() ? "&aвключена" : "&cвыключена")
            )));
            slot++;
            if (slot % 9 == 8) slot += 2;
        }
        setAction(inv, state, 45, button(Material.ARROW, "&aНазад", List.of("&7Вернуться в админ-меню.")), "admin:main");
        setAction(inv, state, 49, button(Material.CLOCK, "&eОбновить", List.of("&7Обновить список лавок.")), "refresh");
        setAction(inv, state, 53, button(Material.BARRIER, "&cЗакрыть", List.of("&7Выйти из меню.")), "close");
        player.openInventory(inv);
    }

    private void openAdminCatalog(Player player) {
        SessionState state = session(player);
        state.viewType = ViewType.ADMIN_CATALOG;
        Inventory inv = createMenu(player, state, ViewType.ADMIN_CATALOG, 27, "&8Каталог артефактов");
        inv.setItem(4, button(Material.BOOK, "&eКаталог", List.of("&7Активных товаров: &f" + catalogById.size())));
        setAction(inv, state, 10, categoryIcon(Category.WEAPON), "cat:WEAPON");
        setAction(inv, state, 12, categoryIcon(Category.ARMOR), "cat:ARMOR");
        setAction(inv, state, 14, categoryIcon(Category.TOOL), "cat:TOOL");
        inv.setItem(16, button(Material.BOOK, "&eОфициальные модели", List.of("&7Ресурс-пак сервера меняет внешний вид официальных предметов.")));
        setAction(inv, state, 22, button(Material.ARROW, "&aНазад", List.of("&7Вернуться в админ-меню.")), "admin:main");
        player.openInventory(inv);
    }

    private void openAdminDiagnostics(Player player) {
        SessionState state = session(player);
        state.viewType = ViewType.ADMIN_DIAGNOSTICS;
        Inventory inv = createMenu(player, state, ViewType.ADMIN_DIAGNOSTICS, 27, "&8Диагностика артефактов");
        BridgeHealthSnapshot health = bridge.health(player, "artifacts-admin-gui");
        inv.setItem(10, button(health.bridgeReady() ? Material.LIME_CONCRETE : Material.RED_CONCRETE, "&eBridge", List.of("&7Готов: &f" + health.bridgeReady())));
        inv.setItem(12, button(health.postgresReady() ? Material.LIME_CONCRETE : Material.RED_CONCRETE, "&ePostgreSQL", List.of("&7Готов: &f" + health.postgresReady())));
        inv.setItem(14, button(health.pinReady() ? Material.LIME_CONCRETE : Material.ORANGE_CONCRETE, "&ePIN", List.of("&7Готов: &f" + health.pinReady())));
        inv.setItem(16, button(Material.CLOCK, "&eTasks", List.of(
                "&7Pending hints: &f1 раз в минуту",
                "&7Session cleanup: &f1 раз в минуту",
                "&7Every tick задач лавки нет"
        )));
        setAction(inv, state, 22, button(Material.ARROW, "&aНазад", List.of("&7Вернуться в админ-меню.")), "admin:main");
        player.openInventory(inv);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return prefix(List.of("admin", "claim", "repair", "reload", "shop"), args[0]);
        if (args.length == 2 && "shop".equalsIgnoreCase(args[0])) return prefix(List.of("create", "remove", "list", "open"), args[1]);
        return Collections.emptyList();
    }

    private void openMain(Player player, Shop shop) {
        openMain(player, shop, false);
    }

    private void openMain(Player player, Shop shop, boolean fresh) {
        SessionState state = fresh ? freshSession(player) : session(player);
        state.shopId = shop.shopId();
        state.viewType = ViewType.MAIN;
        state.currentCategory = "";
        state.currentItemId = "";
        state.page = 0;
        Inventory inv = createMenu(player, state, ViewType.MAIN, 54, "&8Лавка артефактов");
        inv.setItem(4, button(Material.EMERALD, "&6" + shop.title(), List.of(
                "&7Официальные предметы CopiMine.",
                "&7Оплата идёт только через банк AR.",
                "&8PIN и баланс проверяет UltimateAdminPlus."
        )));
        setAction(inv, state, 10, categoryIcon(Category.WEAPON), "cat:WEAPON");
        setAction(inv, state, 12, categoryIcon(Category.ARMOR), "cat:ARMOR");
        setAction(inv, state, 14, categoryIcon(Category.TOOL), "cat:TOOL");
        inv.setItem(16, button(Material.BOOK, "&eОфициальные модели", List.of("&7Ресурс-пак сервера меняет внешний вид официальных предметов.")));
        setAction(inv, state, 28, button(Material.CHEST, "&bМои покупки", List.of(
                "&7История последних покупок.",
                "&7Оплаченные предметы и статусы выдачи."
        )), "purchases");
        setAction(inv, state, 30, button(Material.CHEST_MINECART, "&aОтложенная выдача", List.of(
                "&7Предметы, которые не поместились в инвентарь.",
                "&eНажми, когда освободишь место."
        )), "pending");
        setAction(inv, state, 32, button(Material.ANVIL, "&aРемонт", List.of(
                "&7Починить официальный предмет в руке.",
                "&7Стоимость зависит от износа."
        )), "repair:open");
        setAction(inv, state, 34, button(Material.BOOK, "&eПомощь", List.of(
                "&7Короткая инструкция по покупке, PIN и выдаче."
        )), "help");
        setAction(inv, state, 45, button(Material.ARROW, "&aНазад", List.of("&7Вернуться в игру.")), "close");
        setAction(inv, state, 49, button(Material.CLOCK, "&eОбновить", List.of("&7Перерисовать текущее меню.")), "refresh");
        setAction(inv, state, 53, button(Material.BARRIER, "&cЗакрыть", List.of("&7Выйти из лавки.")), "close");
        player.openInventory(inv);
    }

    private void openCategory(Player player, Category category) {
        openCategory(player, category, 0);
    }

    private void openCategory(Player player, Category category, int page) {
        SessionState state = session(player);
        state.viewType = ViewType.CATEGORY;
        state.currentCategory = category.name();
        state.currentItemId = "";
        state.page = Math.max(0, page);
        Inventory inv = createMenu(player, state, ViewType.CATEGORY, 54, "&8" + categoryTitle(category));
        inv.setItem(4, button(categoryMaterial(category), "&f" + categoryTitle(category), List.of(categoryHint(category), "&8Выбери предмет, чтобы открыть карточку.")));
        List<CatalogItem> items = catalogByCategory.getOrDefault(category, List.of());
        int[] itemSlots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
        if (category == Category.RP || items.isEmpty()) {
            inv.setItem(22, button(Material.PAPER, "&eПока пусто", List.of("&7Сейчас в этой категории нет доступных товаров.")));
        } else {
            int from = state.page * itemSlots.length;
            for (int i = 0; i < itemSlots.length; i++) {
                int idx = from + i;
                if (idx >= items.size()) break;
                CatalogItem item = items.get(idx);
                setAction(inv, state, itemSlots[i], previewIcon(item), "detail:" + item.itemId());
            }
        }
        setAction(inv, state, 45, button(Material.ARROW, "&aНазад", List.of("&7Вернуться в лавку.")), "back:main");
        setAction(inv, state, 48, button(Material.SPECTRAL_ARROW, "&eПредыдущая", List.of("&7Предыдущая страница товаров.")), "page:prev");
        setAction(inv, state, 49, button(Material.CLOCK, "&eОбновить", List.of("&7Обновить список товаров.")), "refresh");
        setAction(inv, state, 50, button(Material.SPECTRAL_ARROW, "&eСледующая", List.of("&7Следующая страница товаров.")), "page:next");
        setAction(inv, state, 53, button(Material.BARRIER, "&cЗакрыть", List.of("&7Выйти из меню.")), "close");
        player.openInventory(inv);
    }

    private void openDetail(Player player, CatalogItem item) {
        SessionState state = session(player);
        state.viewType = ViewType.DETAIL;
        state.currentItemId = item.itemId();
        Inventory inv = createMenu(player, state, ViewType.DETAIL, 45, "&8Карточка товара");
        inv.setItem(13, previewIcon(item));
        inv.setItem(20, button(Material.EMERALD, "&aЦена: " + item.priceAr() + " AR", List.of("&7Списание выполняется только через bank bridge.")));
        setAction(inv, state, 22, button(Material.LIME_WOOL, "&aКупить", List.of("&7Открыть подтверждение покупки.", "&8Далее может потребоваться PIN.")), "confirm:" + item.itemId());
        inv.setItem(24, button(Material.PAPER, "&eЛимиты", List.of(
                "&7На сервер: &f" + (item.supplyLimit() <= 0 ? "без лимита" : item.supplyLimit()),
                "&7На игрока: &f" + (item.perPlayerLimit() <= 0 ? "без лимита" : item.perPlayerLimit())
        )));
        setAction(inv, state, 31, button(Material.ARROW, "&aНазад", List.of("&7Вернуться в категорию.")), "back:category");
        setAction(inv, state, 40, button(Material.BARRIER, "&cЗакрыть", List.of("&7Выйти из меню.")), "close");
        player.openInventory(inv);
    }

    private void openConfirm(Player player, CatalogItem item) {
        SessionState state = session(player);
        state.viewType = ViewType.CONFIRM;
        state.currentItemId = item.itemId();
        Inventory inv = createMenu(player, state, ViewType.CONFIRM, 45, "&8Подтверждение");
        inv.setItem(11, previewIcon(item));
        inv.setItem(13, button(Material.PAPER, "&fПроверка покупки", List.of(
                "&7Товар: &f" + strip(item.name()),
                "&7Цена: &f" + item.priceAr() + " AR",
                "&7Лавка: &f" + state.shopId
        )));
        inv.setItem(15, button(Material.GOLD_INGOT, "&eBank bridge", List.of(
                "&7Баланс и PIN проверяет CopiMineEconomyCore.",
                "&7Artifacts не меняет баланс напрямую."
        )));
        setAction(inv, state, 29, button(Material.LIME_WOOL, "&aПодтвердить", List.of("&7Списать: &f" + item.priceAr() + " AR", "&8Покупка защищена от двойного клика.")), "purchase:" + item.itemId());
        setAction(inv, state, 33, button(Material.RED_WOOL, "&cОтмена", List.of("&7Вернуться к карточке товара.")), "back:detail");
        setAction(inv, state, 40, button(Material.ARROW, "&aНазад", List.of("&7Вернуться к карточке товара.")), "back:detail");
        player.openInventory(inv);
    }

    private void openPin(Player player, CatalogItem item) {
        SessionState state = session(player);
        state.viewType = ViewType.PIN;
        state.currentItemId = item.itemId();
        Inventory inv = createMenu(player, state, ViewType.PIN, 54, "&8Введите PIN");
        int[] slots = {20,21,22,29,30,31,38,39,40,48};
        for (int i = 1; i <= 9; i++) {
            setAction(inv, state, slots[i - 1], button(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "&f" + i, List.of()), "digit:" + i);
        }
        setAction(inv, state, slots[9], button(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "&f0", List.of()), "digit:0");
        inv.setItem(13, button(Material.PAPER, "&bВведите PIN", List.of("&7" + maskedPin(state.pinBuffer))));
        setAction(inv, state, 23, button(Material.BARRIER, "&cCancel", List.of("&7Вернуться к подтверждению.")), "pin:cancel");
        setAction(inv, state, 32, button(Material.YELLOW_WOOL, "&eClear", List.of("&7Очистить введённый PIN.")), "pin:clear");
        setAction(inv, state, 41, button(Material.LIME_WOOL, "&aEnter", List.of("&7Купить " + item.name())), "pin:submit");
        player.openInventory(inv);
    }

    private void refreshPin(Player player) {
        CatalogItem item = catalogById.get(session(player).currentItemId);
        if (item != null) openPin(player, item);
    }

    private void openPurchases(Player player) {
        SessionState state = session(player);
        state.viewType = ViewType.PURCHASES;
        Inventory inv = createMenu(player, state, ViewType.PURCHASES, 54, "&8Мои покупки");
        inv.setItem(4, button(Material.CHEST, "&bПокупки и выдача", List.of("&7Новые предметы появляются сразу.", "&7Если инвентарь полный, товар ждёт здесь.")));
        runAsync(() -> {
            List<String> rows = readRecentPurchases(player.getUniqueId().toString());
            List<PendingDeliveryRow> pending = readPending(player.getUniqueId().toString());
            runSync(() -> {
                int slot = 10;
                for (String row : rows) {
                    if (slot >= 35) break;
                    inv.setItem(slot, button(Material.PAPER, "&fПокупка", List.of("&7" + row)));
                    slot++;
                    if (slot % 9 == 8) slot += 2;
                }
                int pendingSlot = 37;
                for (PendingDeliveryRow row : pending) {
                    if (pendingSlot >= 44) break;
                    CatalogItem item = catalogById.get(row.itemId());
                    setAction(inv, state, pendingSlot, button(Material.CHEST_MINECART, "&aЗабрать: " + (item == null ? row.itemId() : strip(item.name())), List.of("&7Отложенная выдача", "&eНажми, когда в инвентаре есть место.")), "claim:" + row.deliveryId());
                    pendingSlot++;
                }
                setAction(inv, state, 45, button(Material.ARROW, "&aНазад", List.of("&7Вернуться в лавку.")), "back:main");
                setAction(inv, state, 49, button(Material.CLOCK, "&eОбновить", List.of("&7Обновить покупки и выдачу.")), "refresh");
                setAction(inv, state, 53, button(Material.BARRIER, "&cЗакрыть", List.of("&7Выйти из меню.")), "close");
                player.openInventory(inv);
            });
        });
    }

    private void openPendingDeliveries(Player player) {
        SessionState state = session(player);
        state.viewType = ViewType.PENDING_DELIVERY;
        Inventory inv = createMenu(player, state, ViewType.PENDING_DELIVERY, 54, "&8Отложенная выдача");
        inv.setItem(4, button(Material.CHEST_MINECART, "&aОтложенная выдача", List.of(
                "&7Здесь лежат оплаченные предметы,",
                "&7которые не поместились в инвентарь."
        )));
        runAsync(() -> {
            List<PendingDeliveryRow> pending = readPending(player.getUniqueId().toString());
            runSync(() -> {
                int slot = 10;
                for (PendingDeliveryRow row : pending) {
                    if (slot >= 35) break;
                    CatalogItem item = catalogById.get(row.itemId());
                    setAction(inv, state, slot, button(Material.CHEST_MINECART, "&aЗабрать: " + (item == null ? row.itemId() : strip(item.name())), List.of(
                            "&7Отложенная выдача",
                            "&eНажми, когда в инвентаре есть место."
                    )), "claim:" + row.deliveryId());
                    slot++;
                    if (slot % 9 == 8) slot += 2;
                }
                if (pending.isEmpty()) {
                    inv.setItem(22, button(Material.LIME_CONCRETE, "&aПусто", List.of("&7Нет предметов, ожидающих выдачи.")));
                }
                setAction(inv, state, 45, button(Material.ARROW, "&aНазад", List.of("&7Вернуться в лавку.")), "back:main");
                setAction(inv, state, 49, button(Material.CLOCK, "&eОбновить", List.of("&7Проверить отложенную выдачу ещё раз.")), "refresh");
                setAction(inv, state, 53, button(Material.BARRIER, "&cЗакрыть", List.of("&7Выйти из меню.")), "close");
                player.openInventory(inv);
            });
        });
    }

    private void openHelp(Player player) {
        SessionState state = session(player);
        state.viewType = ViewType.HELP;
        Inventory inv = createMenu(player, state, ViewType.HELP, 27, "&8Помощь лавки");
        inv.setItem(10, button(Material.BOOK, "&eПокупка", List.of("&7Лавка -> категория -> товар -> подтвердить.", "&7Если PIN включён, появится цифровая панель.")));
        inv.setItem(12, button(Material.CHEST_MINECART, "&eОтложенная выдача", List.of("&7Если инвентарь заполнен, предмет не теряется.", "&7Открой Мои покупки или введи /cmartifacts claim.")));
        inv.setItem(14, button(Material.ANVIL, "&eРемонт", List.of("&7Возьми официальный предмет в руку.", "&7Нажми Ремонт или введи /cmartifacts repair.")));
        inv.setItem(16, button(Material.BOOK, "&eОфициальные модели", List.of("&7Ресурс-пак сервера обновляет внешний вид официальных предметов.")));
        setAction(inv, state, 22, button(Material.ARROW, "&aНазад", List.of("&7Вернуться в лавку.")), "back:main");
        player.openInventory(inv);
    }

    private void openSuccess(Player player, CatalogItem item, long balanceAfter) {
        SessionState state = session(player);
        state.viewType = ViewType.SUCCESS;
        state.currentItemId = item.itemId();
        Inventory inv = createMenu(player, state, ViewType.SUCCESS, 27, "&8Покупка завершена");
        inv.setItem(13, button(Material.LIME_CONCRETE, "&aПокупка успешна", List.of(
                "&7Товар: &f" + strip(item.name()),
                "&7Списано: &f" + item.priceAr() + " AR",
                "&7Баланс после списания: &f" + balanceAfter + " AR"
        )));
        setAction(inv, state, 11, button(Material.EMERALD, "&aКупить ещё раз", List.of("&7Повторить покупку этого товара")), "confirm:" + item.itemId());
        setAction(inv, state, 15, button(Material.ARROW, "&aНазад в категорию", List.of("&7Вернуться к списку товаров")), "back:category");
        setAction(inv, state, 22, button(Material.CHEST, "&bГлавное меню", List.of("&7Вернуться в главное меню лавки")), "back:main");
        player.openInventory(inv);
    }

    private void openError(Player player, CatalogItem item, String title, String message) {
        SessionState state = session(player);
        state.viewType = ViewType.ERROR;
        state.currentItemId = item.itemId();
        Inventory inv = createMenu(player, state, ViewType.ERROR, 27, "&8Ошибка покупки");
        inv.setItem(13, button(Material.RED_CONCRETE, title, List.of("&7" + message)));
        setAction(inv, state, 11, button(Material.RED_WOOL, "&cНазад к подтверждению", List.of("&7Проверить покупку ещё раз")), "back:confirm");
        setAction(inv, state, 15, button(Material.ARROW, "&aНазад к товару", List.of("&7Вернуться к карточке товара")), "back:detail");
        setAction(inv, state, 22, button(Material.CHEST, "&bГлавное меню", List.of("&7Вернуться в главное меню лавки")), "back:main");
        player.openInventory(inv);
    }

    private void openRepair(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        CatalogItem catalog = authenticCatalogItem(item, player, "repair_open");
        if (catalog == null) {
            player.sendMessage(color("&cВ руке должен быть официальный предмет CopiMineArtifacts."));
            return;
        }
        if (!(item.getItemMeta() instanceof Damageable damageable) || damageable.getDamage() <= 0) {
            player.sendMessage(color("&eЭтот предмет не требует ремонта."));
            return;
        }
        long price = repairPrice(item, catalog);
        SessionState state = session(player);
        state.viewType = ViewType.REPAIR;
        state.currentItemId = catalog.itemId();
        Inventory inv = createMenu(player, state, ViewType.REPAIR, 27, "&8Ремонт артефакта");
        inv.setItem(13, item.clone());
        setAction(inv, state, 11, button(Material.ANVIL, "&aПочинить за " + price + " AR", List.of("&7Официальный ремонт за AR")), "repair:confirm:" + price);
        setAction(inv, state, 15, button(Material.ARROW, "&aНазад", List.of("&7Закрыть ремонт")), "repair:cancel");
        player.openInventory(inv);
    }

    private void handleMenuAction(Player player, SessionState state, String action) {
        if (action.startsWith("cat:")) {
            openCategory(player, Category.valueOf(action.substring(4)));
            return;
        }
        if (action.startsWith("detail:")) {
            CatalogItem item = catalogById.get(action.substring(7));
            if (item != null) openDetail(player, item);
            return;
        }
        if (action.startsWith("confirm:")) {
            CatalogItem item = catalogById.get(action.substring(8));
            if (item != null) openConfirm(player, item);
            return;
        }
        if (action.startsWith("purchase:")) {
            CatalogItem item = catalogById.get(action.substring(9));
            if (item == null) return;
            BridgePinStatus pin = bridge.pinStatus(player);
            if (pin.configured()) {
                openPin(player, item);
            } else {
                executePurchase(player, currentShop(state), item, "");
            }
            return;
        }
        if (action.startsWith("digit:")) {
            if (state.pinBuffer.length() < 8) state.pinBuffer += action.substring(6);
            refreshPin(player);
            return;
        }
        if ("pin:clear".equals(action)) {
            state.pinBuffer = "";
            refreshPin(player);
            return;
        }
        if ("pin:cancel".equals(action)) {
            CatalogItem item = catalogById.get(state.currentItemId);
            if (item != null) openConfirm(player, item);
            return;
        }
        if ("pin:submit".equals(action)) {
            CatalogItem item = catalogById.get(state.currentItemId);
            if (item != null) executePurchase(player, currentShop(state), item, state.pinBuffer);
            return;
        }
        if ("purchases".equals(action)) {
            openPurchases(player);
            return;
        }
        if ("pending".equals(action)) {
            openPendingDeliveries(player);
            return;
        }
        if ("help".equals(action)) {
            openHelp(player);
            return;
        }
        if ("refresh".equals(action)) {
            refreshCurrentMenu(player, state);
            return;
        }
        if ("page:prev".equals(action)) {
            if (!state.currentCategory.isBlank()) {
                openCategory(player, Category.valueOf(state.currentCategory), Math.max(0, state.page - 1));
            }
            return;
        }
        if ("page:next".equals(action)) {
            if (!state.currentCategory.isBlank()) {
                Category category = Category.valueOf(state.currentCategory);
                int maxPage = Math.max(0, (catalogByCategory.getOrDefault(category, List.of()).size() - 1) / 21);
                openCategory(player, category, Math.min(maxPage, state.page + 1));
            }
            return;
        }
        if ("admin:main".equals(action)) {
            openArtifactsAdminMenu(player);
            return;
        }
        if ("admin:shops".equals(action)) {
            openAdminShops(player);
            return;
        }
        if ("admin:catalog".equals(action)) {
            openAdminCatalog(player);
            return;
        }
        if ("admin:diagnostics".equals(action)) {
            openAdminDiagnostics(player);
            return;
        }
        if ("admin:reload".equals(action)) {
            if (!hasArtifactPermission(player, "copimine.artifacts.reload")) {
                noPermission(player);
                return;
            }
            reloadConfig();
            debugGui = getConfig().getBoolean("debug_gui", false);
            loadCatalogFromConfig();
            runAsync(() -> {
                try {
                    syncCatalogToPostgres();
                    runSync(() -> {
                        player.sendMessage(color("&aCopiMineArtifacts catalog reloaded."));
                        openAdminDiagnostics(player);
                    });
                } catch (SQLException e) {
                    getLogger().log(Level.WARNING, "Artifacts admin reload failed", e);
                    runSync(() -> player.sendMessage(color("&cНе удалось выполнить действие. Ошибка записана в лог. Код: ARTIFACT_RELOAD_FAILED")));
                }
            });
            return;
        }
        if ("repair:open".equals(action)) {
            openRepair(player);
            return;
        }
        if ("close".equals(action)) {
            player.closeInventory();
            return;
        }
        if (action.startsWith("claim:")) {
            claimOne(player, action.substring(6));
            return;
        }
        if (action.startsWith("repair:confirm:")) {
            long price = parseLong(action.substring("repair:confirm:".length()), 0L);
            executeRepair(player, price);
            return;
        }
        if ("repair:cancel".equals(action)) {
            player.closeInventory();
            return;
        }
        if ("back:main".equals(action)) {
            Shop shop = currentShop(state);
            if (shop != null) openMain(player, shop);
            return;
        }
        if ("back:category".equals(action)) {
            openCategory(player, Category.valueOf(state.currentCategory));
            return;
        }
        if ("back:detail".equals(action)) {
            CatalogItem item = catalogById.get(state.currentItemId);
            if (item != null) openDetail(player, item);
            return;
        }
        if ("back:confirm".equals(action)) {
            CatalogItem item = catalogById.get(state.currentItemId);
            if (item != null) openConfirm(player, item);
        }
    }

    private void refreshCurrentMenu(Player player, SessionState state) {
        switch (state.viewType) {
            case MAIN -> {
                Shop shop = currentShop(state);
                if (shop != null) openMain(player, shop);
            }
            case CATEGORY -> {
                if (!state.currentCategory.isBlank()) openCategory(player, Category.valueOf(state.currentCategory), state.page);
            }
            case DETAIL -> {
                CatalogItem item = catalogById.get(state.currentItemId);
                if (item != null) openDetail(player, item);
            }
            case CONFIRM -> {
                CatalogItem item = catalogById.get(state.currentItemId);
                if (item != null) openConfirm(player, item);
            }
            case PIN -> {
                CatalogItem item = catalogById.get(state.currentItemId);
                if (item != null) openPin(player, item);
            }
            case PURCHASES -> openPurchases(player);
            case PENDING_DELIVERY -> openPendingDeliveries(player);
            case HELP -> openHelp(player);
            case REPAIR -> openRepair(player);
            case ADMIN_MAIN -> openArtifactsAdminMenu(player);
            case ADMIN_SHOPS -> openAdminShops(player);
            case ADMIN_CATALOG -> openAdminCatalog(player);
            case ADMIN_DIAGNOSTICS -> openAdminDiagnostics(player);
            default -> {
                Shop shop = currentShop(state);
                if (shop != null) openMain(player, shop);
            }
        }
    }

    private void executePurchase(Player player, Shop shop, CatalogItem item, String pin) {
        if (shop == null) {
            player.sendMessage(color("&cМагазин временно недоступен."));
            return;
        }
        SessionState state = session(player);
        if (!state.purchaseInFlightId.isBlank()) {
            player.sendMessage(color("&eПокупка уже обрабатывается."));
            return;
        }
        if (item.category() == Category.RP) {
            player.sendMessage(color("&eДля этой категории сейчас нет доступных товаров."));
            return;
        }
        if (player.getInventory().firstEmpty() < 0) {
            player.sendMessage(color("&cНедостаточно места в инвентаре. AR не списаны."));
            return;
        }
        String purchaseId = UUID.randomUUID().toString();
        state.purchaseInFlightId = purchaseId;
        PurchaseContext context = new PurchaseContext(purchaseId, UUID.randomUUID().toString(), item, shop, pin);
        player.sendMessage(color("&7CopiMineArtifacts: проверяем покупку..."));
        runAsync(() -> {
            int totalPurchased = purchasedCount(item.itemId());
            if (item.supplyLimit() > 0 && totalPurchased >= item.supplyLimit()) {
                runSync(() -> {
                    state.purchaseInFlightId = "";
                    player.sendMessage(color("&cЛимит поставки для этого артефакта исчерпан."));
                    openError(player, item, "&cЛимит поставки исчерпан", "Доступно " + item.supplyLimit() + " шт. на весь сервер.");
                });
                return;
            }
            int playerPurchased = playerPurchasedCount(player.getUniqueId().toString(), item.itemId());
            if (item.perPlayerLimit() > 0 && playerPurchased >= item.perPlayerLimit()) {
                runSync(() -> {
                    state.purchaseInFlightId = "";
                    player.sendMessage(color("&cВаш персональный лимит на этот артефакт уже достигнут."));
                    openError(player, item, "&cПерсональный лимит достигнут", "Можно купить не больше " + item.perPlayerLimit() + " шт. на игрока.");
                });
                return;
            }
            BridgeTxnResult charge = bridge.charge(player, item.priceAr(), pin, "artifact-purchase-" + purchaseId, "artifact_purchase", "item=" + item.itemId() + " shop=" + shop.shopId());
            if (!charge.ok()) {
                runSync(() -> {
                    state.purchaseInFlightId = "";
                    if ("INSUFFICIENT_AR".equalsIgnoreCase(charge.code())) {
                        player.sendMessage(color("&cНедостаточно AR на банковском счёте."));
                    } else if ("PIN_INVALID".equalsIgnoreCase(charge.code())) {
                        player.sendMessage(color("&cPIN введён неверно."));
                    } else {
                        player.sendMessage(color("&cПокупка отклонена. Код: " + safeBridgeCode(charge.code())));
                    }
                    if ("INSUFFICIENT_AR".equalsIgnoreCase(charge.code())) {
                        openError(player, item, "&cНедостаточно AR", "На банковском счёте не хватает средств для покупки.");
                    } else if ("PIN_INVALID".equalsIgnoreCase(charge.code())) {
                        openError(player, item, "&cНеверный PIN", "Проверьте введённый PIN и повторите покупку.");
                    } else {
                        openError(player, item, "&cПокупка отклонена", "Код: " + safeBridgeCode(charge.code()));
                    }
                });
                return;
            }
            try {
                persistPaidPurchase(player, context, charge);
            } catch (SQLException e) {
                bridge.refund(player, item.priceAr(), "artifact-refund-" + purchaseId, "artifact_refund", "purchase=" + purchaseId);
                runSync(() -> {
                    state.purchaseInFlightId = "";
                    player.sendMessage(color("&cБаза CopiMineArtifacts недоступна. Покупка отменена, AR возвращены."));
                });
                return;
            }
            runSync(() -> deliverPurchase(player, context, charge));
        });
    }

    private void persistPaidPurchase(Player player, PurchaseContext context, BridgeTxnResult charge) throws SQLException {
        Connection c = pgPool.acquire();
        try {
            c.setAutoCommit(false);
            try (PreparedStatement purchase = c.prepareStatement("""
                INSERT INTO artifact_purchases(purchase_id,unique_item_id,player_uuid,player_name,item_id,shop_id,price_ar,bank_tx_id,idempotency_key,status,delivery_mode,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
            """);
                 PreparedStatement instance = c.prepareStatement("""
                INSERT INTO artifact_item_instances(unique_item_id,item_id,owner_uuid,purchase_id,status,repaired_count,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?,?)
            """)) {
                long now = now();
                purchase.setString(1, context.purchaseId());
                purchase.setString(2, context.uniqueItemId());
                purchase.setString(3, player.getUniqueId().toString());
                purchase.setString(4, player.getName());
                purchase.setString(5, context.item().itemId());
                purchase.setString(6, context.shop().shopId());
                purchase.setLong(7, context.item().priceAr());
                purchase.setString(8, charge.txId());
                purchase.setString(9, "artifact-purchase-" + context.purchaseId());
                purchase.setString(10, "PAID");
                purchase.setString(11, "DIRECT");
                purchase.setLong(12, now);
                purchase.setLong(13, now);
                purchase.executeUpdate();

                instance.setString(1, context.uniqueItemId());
                instance.setString(2, context.item().itemId());
                instance.setString(3, player.getUniqueId().toString());
                instance.setString(4, context.purchaseId());
                instance.setString(5, "DELIVERING");
                instance.setInt(6, 0);
                instance.setLong(7, now);
                instance.setLong(8, now);
                instance.executeUpdate();
                c.commit();
                instanceToItem.put(context.uniqueItemId(), context.item().itemId());
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } finally {
            try { c.setAutoCommit(true); } catch (SQLException ignored) {}
            pgPool.release(c);
        }
    }

    private void deliverPurchase(Player player, PurchaseContext context, BridgeTxnResult charge) {
        SessionState state = session(player);
        ItemStack stack = createOfficialItem(context.item(), context.uniqueItemId(), player.getUniqueId(), context.purchaseId());
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
        if (leftovers.isEmpty()) {
            runAsync(() -> {
                try {
                    markPurchaseDelivered(context.purchaseId(), context.uniqueItemId());
                    audit(player.getName(), "purchase_delivered", context.purchaseId(), context.item().itemId());
                } catch (SQLException ignored) {}
            });
            state.purchaseInFlightId = "";
            player.sendMessage(color("&aПокупка успешна. Баланс после списания: &f" + charge.balanceAfter() + " AR"));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
            openSuccess(player, context.item(), charge.balanceAfter());
            return;
        }
        runAsync(() -> {
            try {
                createPendingDelivery(player, context);
                audit(player.getName(), "purchase_pending_delivery", context.purchaseId(), context.item().itemId());
            } catch (SQLException ignored) {}
        });
        state.purchaseInFlightId = "";
        player.sendMessage(color("&eПредмет оплачен, но инвентарь занялся. Покупка переведена в отложенная выдача."));
        openPurchases(player);
    }

    private void executeRepair(Player player, long price) {
        ItemStack item = player.getInventory().getItemInMainHand();
        CatalogItem catalog = authenticCatalogItem(item, player, "repair");
        if (catalog == null) {
            player.sendMessage(color("&cПоддельный предмет не принимается в ремонт."));
            return;
        }
        if (!(item.getItemMeta() instanceof Damageable damageable) || damageable.getDamage() <= 0) {
            player.sendMessage(color("&eРемонт не требуется."));
            return;
        }
        runAsync(() -> {
            BridgeTxnResult charge = bridge.charge(player, price, "", "artifact-repair-" + UUID.randomUUID(), "artifact_repair", "item=" + catalog.itemId());
            if (!charge.ok()) {
                runSync(() -> player.sendMessage(color("&cРемонт отклонён. Код: " + safeBridgeCode(charge.code()))));
                return;
            }
            String repairId = UUID.randomUUID().toString();
            try {
                persistRepair(player, catalog, item, repairId, price, charge.txId());
            } catch (SQLException e) {
                bridge.refund(player, price, "artifact-repair-refund-" + repairId, "artifact_repair_refund", "repair=" + repairId);
                runSync(() -> player.sendMessage(color("&cБаза недоступна. Ремонт отменён, AR возвращены.")));
                return;
            }
            runSync(() -> {
                Damageable d = (Damageable) item.getItemMeta();
                d.setDamage(0);
                item.setItemMeta((ItemMeta) d);
                player.getInventory().setItemInMainHand(item);
                player.sendMessage(color("&aРемонт завершён за &f" + price + " AR"));
                player.closeInventory();
            });
        });
    }
    private void claimPending(Player player) {
        runAsync(() -> {
            List<PendingDeliveryRow> rows = readPending(player.getUniqueId().toString());
            readDonationClaimsAsync(player.getUniqueId()).whenComplete((donationRows, error) -> runSync(() -> {
                if (!player.isOnline()) {
                    return;
                }
                if (error != null) {
                    getLogger().log(Level.WARNING, "Donation claims fetch failed", error);
                    player.sendMessage(color("&cНе удалось загрузить донат-выдачу. Попробуй ещё раз."));
                    return;
                }
                List<DonationClaimRow> safeDonationRows = donationRows == null ? List.of() : donationRows;
                if (rows.isEmpty() && safeDonationRows.isEmpty()) {
                    player.sendMessage(color("&eУ тебя нет предметов, ожидающих выдачи."));
                    return;
                }
                for (PendingDeliveryRow row : rows) {
                    if (player.getInventory().firstEmpty() < 0) {
                        player.sendMessage(color("&cИнвентарь заполнен. Освободи место и повтори команду."));
                        return;
                    }
                    deliverPendingRow(player, row);
                }
                for (DonationClaimRow row : safeDonationRows) {
                    int requiredSlots = requiredDonationSlots(row.amount());
                    if (requiredSlots < 0) {
                        player.sendMessage(color("&cЭта донат-выдача слишком большая для одного инвентаря. Нужен администратор."));
                        return;
                    }
                    if (freeStorageSlots(player.getInventory()) < requiredSlots) {
                        player.sendMessage(color("&eОсвободи место в инвентаре. Нужно свободных слотов: &f" + requiredSlots));
                        return;
                    }
                    deliverDonationClaimRow(player, row);
                }
            }));
        });
    }
    private void claimOne(Player player, String deliveryId) {
        runAsync(() -> {
            List<PendingDeliveryRow> rows = readPending(player.getUniqueId().toString()).stream().filter(x -> x.deliveryId().equals(deliveryId)).toList();
            if (!rows.isEmpty()) {
                runSync(() -> deliverPendingRow(player, rows.get(0)));
                return;
            }
            readDonationClaimsAsync(player.getUniqueId()).whenComplete((donationRows, error) -> runSync(() -> {
                if (!player.isOnline()) {
                    return;
                }
                if (error != null) {
                    getLogger().log(Level.WARNING, "Donation claim lookup failed", error);
                    player.sendMessage(color("&cНе удалось проверить донат-выдачу."));
                    return;
                }
                List<DonationClaimRow> matches = (donationRows == null ? List.<DonationClaimRow>of() : donationRows).stream()
                        .filter(x -> x.claimId().equals(deliveryId))
                        .toList();
                if (matches.isEmpty()) {
                    player.sendMessage(color("&cПредмет для выдачи не найден."));
                    return;
                }
                int requiredSlots = requiredDonationSlots(matches.getFirst().amount());
                if (requiredSlots < 0) {
                    player.sendMessage(color("&cЭта донат-выдача слишком большая для одного инвентаря. Нужен администратор."));
                    return;
                }
                if (freeStorageSlots(player.getInventory()) < requiredSlots) {
                    player.sendMessage(color("&eОсвободи место в инвентаре. Нужно свободных слотов: &f" + requiredSlots));
                    return;
                }
                deliverDonationClaimRow(player, matches.getFirst());
            }));
        });
    }

    private void deliverPendingRow(Player player, PendingDeliveryRow row) {
        CatalogItem item = catalogById.get(row.itemId());
        if (item == null) {
            player.sendMessage(color("&cОтложенная выдача повреждён: item missing."));
            return;
        }
        if (player.getInventory().firstEmpty() < 0) {
            player.sendMessage(color("&cИнвентарь заполнен. Отложенная выдача не потерян."));
            return;
        }
        ItemStack stack = createOfficialItem(item, row.uniqueItemId(), player.getUniqueId(), row.purchaseId());
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
        if (!leftovers.isEmpty()) {
            player.sendMessage(color("&cИнвентарь заполнен. Отложенная выдача не потерян."));
            return;
        }
        runAsync(() -> {
            try {
                markPendingClaimed(row.deliveryId(), row.purchaseId(), row.uniqueItemId());
            } catch (SQLException ignored) {}
        });
        player.sendMessage(color("&aОтложенная выдача получен: &f" + strip(item.name())));
    }

    private void deliverDonationClaimRow(Player player, DonationClaimRow row) {
        reserveDonationClaimAsync(player.getUniqueId(), row.claimId()).whenComplete((reserved, error) -> runSync(() -> {
            if (!player.isOnline()) {
                if (Boolean.TRUE.equals(reserved)) {
                    releaseDonationClaimAsync(player.getUniqueId(), row.claimId());
                }
                return;
            }
            if (error != null) {
                getLogger().log(Level.WARNING, "Donation claim reservation failed", error);
                player.sendMessage(color("&cНе удалось зарезервировать донат-выдачу."));
                return;
            }
            if (!Boolean.TRUE.equals(reserved)) {
                player.sendMessage(color("&eЭтот донат-предмет уже забирается или был выдан."));
                return;
            }
            CatalogItem item = catalogById.get(row.itemId());
            if (item == null) {
                releaseDonationClaimAsync(player.getUniqueId(), row.claimId());
                player.sendMessage(color("&cДонат-предмет недоступен в каталоге."));
                return;
            }
            int amount = requiredDonationSlots(row.amount());
            if (amount < 0) {
                releaseDonationClaimAsync(player.getUniqueId(), row.claimId());
                player.sendMessage(color("&cЭта донат-выдача слишком большая для одного инвентаря. Нужен администратор."));
                return;
            }
            if (freeStorageSlots(player.getInventory()) < amount) {
                releaseDonationClaimAsync(player.getUniqueId(), row.claimId());
                player.sendMessage(color("&eОсвободи место в инвентаре. Нужно свободных слотов: &f" + amount));
                return;
            }
            String purchaseId = row.purchaseId() == null || row.purchaseId().isBlank() ? "donation-" + row.claimId() : row.purchaseId();
            List<String> uniqueItemIds = new ArrayList<>(amount);
            for (int i = 0; i < amount; i++) {
                uniqueItemIds.add(UUID.randomUUID().toString());
            }
            prepareDonationDeliveryAsync(player.getUniqueId(), row.claimId(), purchaseId, item.itemId(), uniqueItemIds).whenComplete((prepared, prepareError) -> runSync(() -> {
                if (!player.isOnline()) {
                    if (Boolean.TRUE.equals(prepared)) {
                        failDonationDeliveryAsync(player.getUniqueId(), row.claimId(), uniqueItemIds);
                    } else {
                        releaseDonationClaimAsync(player.getUniqueId(), row.claimId());
                    }
                    return;
                }
                if (prepareError != null || !Boolean.TRUE.equals(prepared)) {
                    if (Boolean.TRUE.equals(prepared)) {
                        failDonationDeliveryAsync(player.getUniqueId(), row.claimId(), uniqueItemIds);
                    } else {
                        releaseDonationClaimAsync(player.getUniqueId(), row.claimId());
                    }
                    getLogger().log(Level.WARNING, "Donation claim prepare failed: " + row.claimId(), prepareError);
                    player.sendMessage(color("&cНе удалось подготовить донат-выдачу. Попробуй ещё раз."));
                    return;
                }
                for (String uniqueItemId : uniqueItemIds) {
                    ItemStack stack = createOfficialItem(item, uniqueItemId, player.getUniqueId(), purchaseId);
                    Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
                    if (!leftovers.isEmpty()) {
                        failDonationDeliveryAsync(player.getUniqueId(), row.claimId(), uniqueItemIds);
                        player.sendMessage(color("&cИнвентарь изменился во время выдачи. Выдача отправлена на ручную проверку и не будет автоматически повторена."));
                        return;
                    }
                }
                finalizeDonationDeliveryAsync(player.getUniqueId(), row.claimId(), purchaseId, uniqueItemIds).whenComplete((completed, completeError) -> {
                    if (completeError != null || !Boolean.TRUE.equals(completed)) {
                        getLogger().log(Level.WARNING, "Donation claim was delivered but completion failed: " + row.claimId(), completeError);
                        reviewDonationClaimAsync(player.getUniqueId(), row.claimId());
                    }
                });
                audit(player.getName(), "donation_claim_delivered", row.claimId(), item.itemId());
                player.sendMessage(color("&aДонат-предмет получен: &f" + strip(item.name()) + " &7x" + amount));
            }));
        }));
    }

    private CompletableFuture<List<DonationClaimRow>> readDonationClaimsAsync(UUID playerUuid) {
        if (playerUuid == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        CopiMineEconomyCore.DonationPurchaseService service = donationPurchaseService();
        if (service == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        return service.getUnclaimedItemsAsync(playerUuid, 18).thenApply(rows -> {
            List<DonationClaimRow> out = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                out.add(new DonationClaimRow(
                        str(row.get("id")),
                        str(row.get("purchase_id")),
                        str(row.get("item_id")),
                        parseLong(str(row.get("amount")), 1L)
                ));
            }
            return out;
        });
    }

    private CompletableFuture<Boolean> reserveDonationClaimAsync(UUID playerUuid, String claimId) {
        CopiMineEconomyCore.DonationPurchaseService service = donationPurchaseService();
        if (service == null || playerUuid == null || claimId == null || claimId.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }
        return service.reserveClaimAsync(playerUuid, claimId);
    }

    private CompletableFuture<Boolean> completeDonationClaimAsync(UUID playerUuid, String claimId) {
        CopiMineEconomyCore.DonationPurchaseService service = donationPurchaseService();
        if (service == null || playerUuid == null || claimId == null || claimId.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }
        return service.completeClaimAsync(playerUuid, claimId);
    }

    private CompletableFuture<Boolean> markDonationClaimDeliveringAsync(UUID playerUuid, String claimId) {
        CopiMineEconomyCore.DonationPurchaseService service = donationPurchaseService();
        if (service == null || playerUuid == null || claimId == null || claimId.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }
        return service.markClaimDeliveringAsync(playerUuid, claimId);
    }

    private CompletableFuture<Boolean> reviewDonationClaimAsync(UUID playerUuid, String claimId) {
        CopiMineEconomyCore.DonationPurchaseService service = donationPurchaseService();
        if (service == null || playerUuid == null || claimId == null || claimId.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }
        return service.markClaimDeliveryReviewAsync(playerUuid, claimId);
    }

    private CompletableFuture<Boolean> releaseDonationClaimAsync(UUID playerUuid, String claimId) {
        CopiMineEconomyCore.DonationPurchaseService service = donationPurchaseService();
        if (service == null || playerUuid == null || claimId == null || claimId.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }
        return service.releaseClaimAsync(playerUuid, claimId);
    }

    private CopiMineEconomyCore.DonationPurchaseService donationPurchaseService() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("CopiMineEconomyCore");
        if (!(plugin instanceof CopiMineEconomyCore main) || !plugin.isEnabled()) {
            return null;
        }
        try {
            return main.donationPurchaseService();
        } catch (Exception e) {
            return null;
        }
    }

    private CompletableFuture<Boolean> prepareDonationDeliveryAsync(UUID playerUuid, String claimId, String purchaseId, String itemId, List<String> uniqueItemIds) {
        return markDonationClaimDeliveringAsync(playerUuid, claimId).thenCompose(marked -> {
            if (!Boolean.TRUE.equals(marked)) {
                return CompletableFuture.completedFuture(false);
            }
            return CompletableFuture.supplyAsync(() -> {
                try {
                    persistDonationInstances(playerUuid, purchaseId, itemId, uniqueItemIds);
                    return true;
                } catch (Exception error) {
                    reviewDonationClaimAsync(playerUuid, claimId);
                    throw new IllegalStateException("Failed to persist donation artifact instances.", error);
                }
            }, dbExecutor);
        });
    }

    private CompletableFuture<Boolean> finalizeDonationDeliveryAsync(UUID playerUuid, String claimId, String purchaseId, List<String> uniqueItemIds) {
        return completeDonationClaimAsync(playerUuid, claimId).thenCompose(completed -> {
            if (!Boolean.TRUE.equals(completed)) {
                return CompletableFuture.completedFuture(false);
            }
            return CompletableFuture.supplyAsync(() -> {
                try {
                    markDonationInstancesDelivered(uniqueItemIds);
                    audit(first(playerUuid == null ? "" : playerUuid.toString(), "unknown"), "donation_claim_claimed", claimId, purchaseId);
                    return true;
                } catch (Exception error) {
                    throw new IllegalStateException("Failed to finalize donation artifact instances.", error);
                }
            }, dbExecutor);
        });
    }

    private CompletableFuture<Boolean> failDonationDeliveryAsync(UUID playerUuid, String claimId, List<String> uniqueItemIds) {
        reviewDonationClaimAsync(playerUuid, claimId);
        return CompletableFuture.supplyAsync(() -> {
            try {
                markDonationInstancesFailed(uniqueItemIds);
                return true;
            } catch (Exception error) {
                getLogger().log(Level.WARNING, "Donation delivery cleanup failed for claim " + claimId, error);
                return false;
            }
        }, dbExecutor);
    }

    private int freeStorageSlots(PlayerInventory inventory) {
        int free = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack == null || stack.getType() == Material.AIR) {
                free++;
            }
        }
        return free;
    }

    private int requiredDonationSlots(long amount) {
        if (amount <= 0L) {
            return 1;
        }
        return amount > 36L ? -1 : (int) amount;
    }

    private CatalogItem authenticCatalogItem(ItemStack item, Player player, String eventType) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        String itemId = meta.getPersistentDataContainer().get(keyItemId, PersistentDataType.STRING);
        String uniqueItemId = meta.getPersistentDataContainer().get(keyUniqueItemId, PersistentDataType.STRING);
        if (itemId == null || uniqueItemId == null) return null;
        CatalogItem catalog = catalogById.get(itemId);
        Integer actualModelData = meta.hasCustomModelData() ? meta.getCustomModelData() : null;
        boolean modelMismatch = catalog != null && catalog.customModelData() > 0 && !Objects.equals(actualModelData, catalog.customModelData());
        if (catalog == null || !instanceToItem.containsKey(uniqueItemId) || modelMismatch) {
            String marker = player.getUniqueId() + ":" + uniqueItemId + ":" + eventType;
            if (suspiciousSeen.add(marker)) {
                runAsync(() -> logSuspicious(player, eventType, "itemId=" + itemId + " unique=" + uniqueItemId + " model=" + actualModelData));
            }
            player.sendMessage(color("&cПодозрительный предмет CopiMineArtifacts заблокирован."));
            return null;
        }
        return catalog;
    }

    private ItemStack createOfficialItem(CatalogItem item, String uniqueItemId, UUID owner, String purchaseId) {
        ItemStack stack = new ItemStack(item.material());
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        meta.setDisplayName(color(item.name()));
        List<String> lore = new ArrayList<>();
        for (String line : item.lore()) lore.add(color(line));
        if (item.customModelData() > 0) {
            meta.setCustomModelData(item.customModelData());
        }
        lore.add(color("&8Редкость: " + item.rarity()));
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
        meta.getPersistentDataContainer().set(keyItemId, PersistentDataType.STRING, item.itemId());
        meta.getPersistentDataContainer().set(keyUniqueItemId, PersistentDataType.STRING, uniqueItemId);
        meta.getPersistentDataContainer().set(keyCategory, PersistentDataType.STRING, item.category().name());
        meta.getPersistentDataContainer().set(keyRarity, PersistentDataType.STRING, item.rarity());
        meta.getPersistentDataContainer().set(keyOwnerUuid, PersistentDataType.STRING, owner.toString());
        meta.getPersistentDataContainer().set(keyPurchaseId, PersistentDataType.STRING, purchaseId);
        stack.setItemMeta(meta);
        return stack;
    }

    private long repairPrice(ItemStack item, CatalogItem catalog) {
        if (!(item.getItemMeta() instanceof Damageable damageable)) return Math.max(10L, catalog.priceAr() / 10L);
        int max = item.getType().getMaxDurability();
        if (max <= 0) return Math.max(10L, catalog.priceAr() / 10L);
        double ratio = Math.max(0.1D, (double) damageable.getDamage() / (double) max);
        return Math.max(10L, Math.round(catalog.priceAr() * 0.25D * ratio));
    }

    private void persistRepair(Player player, CatalogItem catalog, ItemStack item, String repairId, long price, String txId) throws SQLException {
        String uniqueItemId = Objects.requireNonNull(item.getItemMeta()).getPersistentDataContainer().get(keyUniqueItemId, PersistentDataType.STRING);
        Connection c = pgPool.acquire();
        try {
            c.setAutoCommit(false);
            try (PreparedStatement repair = c.prepareStatement("""
                INSERT INTO artifact_repairs(repair_id,unique_item_id,player_uuid,player_name,item_id,repair_cost_ar,bank_tx_id,status,created_at)
                VALUES(?,?,?,?,?,?,?,?,?)
            """);
                 PreparedStatement update = c.prepareStatement("UPDATE artifact_item_instances SET repaired_count=repaired_count+1,updated_at=? WHERE unique_item_id=?")) {
                long now = now();
                repair.setString(1, repairId);
                repair.setString(2, uniqueItemId);
                repair.setString(3, player.getUniqueId().toString());
                repair.setString(4, player.getName());
                repair.setString(5, catalog.itemId());
                repair.setLong(6, price);
                repair.setString(7, txId);
                repair.setString(8, "COMPLETED");
                repair.setLong(9, now);
                repair.executeUpdate();
                update.setLong(1, now);
                update.setString(2, uniqueItemId);
                update.executeUpdate();
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } finally {
            try { c.setAutoCommit(true); } catch (SQLException ignored) {}
            pgPool.release(c);
        }
    }

    private void saveShop(Shop shop) throws SQLException {
        Connection c = pgPool.acquire();
        try (PreparedStatement ps = c.prepareStatement("""
            INSERT INTO artifact_shops(shop_id,world_name,block_x,block_y,block_z,title,enabled,created_at,updated_at)
            VALUES(?,?,?,?,?,?,?,?,?)
        """)) {
            long now = now();
            ps.setString(1, shop.shopId());
            ps.setString(2, shop.world());
            ps.setInt(3, shop.x());
            ps.setInt(4, shop.y());
            ps.setInt(5, shop.z());
            ps.setString(6, shop.title());
            ps.setBoolean(7, shop.enabled());
            ps.setLong(8, now);
            ps.setLong(9, now);
            ps.executeUpdate();
        } finally {
            pgPool.release(c);
        }
    }

    private void deleteShop(String shopId) throws SQLException {
        Connection c = pgPool.acquire();
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM artifact_shops WHERE shop_id=?")) {
            ps.setString(1, shopId);
            ps.executeUpdate();
        } finally {
            pgPool.release(c);
        }
    }

    private void createPendingDelivery(Player player, PurchaseContext context) throws SQLException {
        Connection c = pgPool.acquire();
        try {
            c.setAutoCommit(false);
            try (PreparedStatement pending = c.prepareStatement("""
                INSERT INTO artifact_pending_deliveries(delivery_id,purchase_id,unique_item_id,player_uuid,item_id,status,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?,?)
            """);
                 PreparedStatement purchase = c.prepareStatement("UPDATE artifact_purchases SET status='PENDING_DELIVERY',delivery_mode='PENDING',updated_at=? WHERE purchase_id=?");
                 PreparedStatement instance = c.prepareStatement("UPDATE artifact_item_instances SET status='PENDING_DELIVERY',updated_at=? WHERE unique_item_id=?")) {
                String deliveryId = UUID.randomUUID().toString();
                long now = now();
                pending.setString(1, deliveryId);
                pending.setString(2, context.purchaseId());
                pending.setString(3, context.uniqueItemId());
                pending.setString(4, player.getUniqueId().toString());
                pending.setString(5, context.item().itemId());
                pending.setString(6, "PENDING");
                pending.setLong(7, now);
                pending.setLong(8, now);
                pending.executeUpdate();
                purchase.setLong(1, now);
                purchase.setString(2, context.purchaseId());
                purchase.executeUpdate();
                instance.setLong(1, now);
                instance.setString(2, context.uniqueItemId());
                instance.executeUpdate();
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } finally {
            try { c.setAutoCommit(true); } catch (SQLException ignored) {}
            pgPool.release(c);
        }
    }

    private void markPurchaseDelivered(String purchaseId, String uniqueItemId) throws SQLException {
        Connection c = pgPool.acquire();
        try {
            c.setAutoCommit(false);
            try (PreparedStatement purchase = c.prepareStatement("UPDATE artifact_purchases SET status='DELIVERED',updated_at=? WHERE purchase_id=?");
                 PreparedStatement instance = c.prepareStatement("UPDATE artifact_item_instances SET status='DELIVERED',updated_at=? WHERE unique_item_id=?")) {
                long now = now();
                purchase.setLong(1, now);
                purchase.setString(2, purchaseId);
                purchase.executeUpdate();
                instance.setLong(1, now);
                instance.setString(2, uniqueItemId);
                instance.executeUpdate();
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } finally {
            try { c.setAutoCommit(true); } catch (SQLException ignored) {}
            pgPool.release(c);
        }
    }

    private void markPendingClaimed(String deliveryId, String purchaseId, String uniqueItemId) throws SQLException {
        Connection c = pgPool.acquire();
        try {
            c.setAutoCommit(false);
            try (PreparedStatement pending = c.prepareStatement("UPDATE artifact_pending_deliveries SET status='CLAIMED',updated_at=? WHERE delivery_id=?");
                 PreparedStatement purchase = c.prepareStatement("UPDATE artifact_purchases SET status='DELIVERED',updated_at=? WHERE purchase_id=?");
                 PreparedStatement instance = c.prepareStatement("UPDATE artifact_item_instances SET status='DELIVERED',updated_at=? WHERE unique_item_id=?")) {
                long now = now();
                pending.setLong(1, now);
                pending.setString(2, deliveryId);
                pending.executeUpdate();
                purchase.setLong(1, now);
                purchase.setString(2, purchaseId);
                purchase.executeUpdate();
                instance.setLong(1, now);
                instance.setString(2, uniqueItemId);
                instance.executeUpdate();
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } finally {
            try { c.setAutoCommit(true); } catch (SQLException ignored) {}
            pgPool.release(c);
        }
    }

    private void persistDonationInstances(UUID ownerUuid, String purchaseId, String itemId, List<String> uniqueItemIds) throws SQLException {
        if (uniqueItemIds == null || uniqueItemIds.isEmpty()) {
            throw new SQLException("Donation delivery has no item instances to persist.");
        }
        Connection c = pgPool.acquire();
        try {
            c.setAutoCommit(false);
            try (PreparedStatement instance = c.prepareStatement("""
                INSERT INTO artifact_item_instances(unique_item_id,item_id,owner_uuid,purchase_id,status,repaired_count,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?,?)
            """)) {
                long current = now();
                for (String uniqueItemId : uniqueItemIds) {
                    instance.setString(1, uniqueItemId);
                    instance.setString(2, itemId);
                    instance.setString(3, ownerUuid == null ? "" : ownerUuid.toString());
                    instance.setString(4, purchaseId);
                    instance.setString(5, "DONATION_DELIVERING");
                    instance.setInt(6, 0);
                    instance.setLong(7, current);
                    instance.setLong(8, current);
                    instance.addBatch();
                }
                instance.executeBatch();
                c.commit();
                for (String uniqueItemId : uniqueItemIds) {
                    instanceToItem.put(uniqueItemId, itemId);
                }
            } catch (SQLException error) {
                c.rollback();
                throw error;
            }
        } finally {
            try { c.setAutoCommit(true); } catch (SQLException ignored) {}
            pgPool.release(c);
        }
    }

    private void markDonationInstancesDelivered(List<String> uniqueItemIds) throws SQLException {
        if (uniqueItemIds == null || uniqueItemIds.isEmpty()) {
            return;
        }
        Connection c = pgPool.acquire();
        try {
            c.setAutoCommit(false);
            try (PreparedStatement instance = c.prepareStatement("UPDATE artifact_item_instances SET status='DELIVERED',updated_at=? WHERE unique_item_id=?")) {
                long current = now();
                for (String uniqueItemId : uniqueItemIds) {
                    instance.setLong(1, current);
                    instance.setString(2, uniqueItemId);
                    instance.addBatch();
                }
                instance.executeBatch();
                c.commit();
            } catch (SQLException error) {
                c.rollback();
                throw error;
            }
        } finally {
            try { c.setAutoCommit(true); } catch (SQLException ignored) {}
            pgPool.release(c);
        }
    }

    private void markDonationInstancesFailed(List<String> uniqueItemIds) throws SQLException {
        if (uniqueItemIds == null || uniqueItemIds.isEmpty()) {
            return;
        }
        Connection c = pgPool.acquire();
        try {
            c.setAutoCommit(false);
            try (PreparedStatement instance = c.prepareStatement("UPDATE artifact_item_instances SET status='FAILED_DELIVERY',updated_at=? WHERE unique_item_id=?")) {
                long current = now();
                for (String uniqueItemId : uniqueItemIds) {
                    instance.setLong(1, current);
                    instance.setString(2, uniqueItemId);
                    instance.addBatch();
                }
                instance.executeBatch();
                c.commit();
                for (String uniqueItemId : uniqueItemIds) {
                    instanceToItem.remove(uniqueItemId);
                }
            } catch (SQLException error) {
                c.rollback();
                throw error;
            }
        } finally {
            try { c.setAutoCommit(true); } catch (SQLException ignored) {}
            pgPool.release(c);
        }
    }

    private List<String> readRecentPurchases(String uuid) {
        Connection c = null;
        try {
            c = pgPool.acquire();
            try (PreparedStatement ps = c.prepareStatement("SELECT item_id,status,created_at FROM artifact_purchases WHERE player_uuid=? ORDER BY created_at DESC LIMIT 18")) {
                ps.setString(1, uuid);
                try (ResultSet rs = ps.executeQuery()) {
                    List<String> rows = new ArrayList<>();
                    while (rs.next()) {
                        rows.add(rs.getString(1) + " | " + rs.getString(2) + " | " + Instant.ofEpochSecond(rs.getLong(3)));
                    }
                    return rows;
                }
            }
        } catch (SQLException e) {
            getLogger().log(Level.WARNING, "Artifacts purchase history read failed", e);
            return List.of("DB_ERROR: logged");
        } finally {
            if (c != null) pgPool.release(c);
        }
    }

    private List<PendingDeliveryRow> readPending(String uuid) {
        Connection c = null;
        try {
            c = pgPool.acquire();
            try (PreparedStatement ps = c.prepareStatement("SELECT delivery_id,purchase_id,unique_item_id,item_id FROM artifact_pending_deliveries WHERE player_uuid=? AND status='PENDING' ORDER BY created_at DESC LIMIT 18")) {
                ps.setString(1, uuid);
                try (ResultSet rs = ps.executeQuery()) {
                    List<PendingDeliveryRow> rows = new ArrayList<>();
                    while (rs.next()) {
                        rows.add(new PendingDeliveryRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)));
                    }
                    return rows;
                }
            }
        } catch (SQLException e) {
            return List.of();
        } finally {
            if (c != null) pgPool.release(c);
        }
    }

    private int pendingCount(String uuid) {
        Connection c = null;
        try {
            c = pgPool.acquire();
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM artifact_pending_deliveries WHERE player_uuid=? AND status='PENDING'")) {
                ps.setString(1, uuid);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        } catch (SQLException e) {
            return 0;
        } finally {
            if (c != null) pgPool.release(c);
        }
    }

    private int purchasedCount(String itemId) {
        Connection c = null;
        try {
            c = pgPool.acquire();
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM artifact_purchases WHERE item_id=? AND status IN ('PAID','DELIVERING','DELIVERED','PENDING_DELIVERY')")) {
                ps.setString(1, itemId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        } catch (SQLException e) {
            return 0;
        } finally {
            if (c != null) pgPool.release(c);
        }
    }

    private int playerPurchasedCount(String playerUuid, String itemId) {
        Connection c = null;
        try {
            c = pgPool.acquire();
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM artifact_purchases WHERE player_uuid=? AND item_id=? AND status IN ('PAID','DELIVERING','DELIVERED','PENDING_DELIVERY')")) {
                ps.setString(1, playerUuid);
                ps.setString(2, itemId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        } catch (SQLException e) {
            return 0;
        } finally {
            if (c != null) pgPool.release(c);
        }
    }

    private void logSuspicious(Player player, String eventType, String details) {
        Connection c = null;
        try {
            c = pgPool.acquire();
            try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO artifact_suspicious_events(event_id,player_uuid,player_name,event_type,details,created_at)
                VALUES(?,?,?,?,?,?)
            """)) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, player.getUniqueId().toString());
                ps.setString(3, player.getName());
                ps.setString(4, eventType);
                ps.setString(5, details);
                ps.setLong(6, now());
                ps.executeUpdate();
            }
        } catch (SQLException ignored) {
        } finally {
            if (c != null) pgPool.release(c);
        }
    }

    private void audit(String actor, String action, String targetId, String details) {
        Connection c = null;
        try {
            c = pgPool.acquire();
            try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO artifact_audit_log(audit_id,actor,action,target_id,details,created_at)
                VALUES(?,?,?,?,?,?)
            """)) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, actor);
                ps.setString(3, action);
                ps.setString(4, targetId);
                ps.setString(5, details);
                ps.setLong(6, now());
                ps.executeUpdate();
            }
        } catch (SQLException ignored) {
        } finally {
            if (c != null) pgPool.release(c);
        }
    }

    private void tickPendingHints() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            runAsync(() -> {
                int count = pendingCount(player.getUniqueId().toString());
                if (count > 0) {
                    runSync(() -> player.sendActionBar(color("&eCopiMineArtifacts: отложенная выдача x" + count)));
                }
            });
        }
    }

    private Shop currentShop(SessionState state) {
        if (state.shopId == null || state.shopId.isBlank()) return shopsByLocation.values().stream().findFirst().orElse(null);
        for (Shop shop : shopsByLocation.values()) if (shop.shopId().equals(state.shopId)) return shop;
        return shopsByLocation.values().stream().findFirst().orElse(null);
    }

    private SessionState session(Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(), k -> new SessionState());
    }

    private SessionState freshSession(Player player) {
        SessionState state = session(player);
        state.sessionId = UUID.randomUUID();
        state.shopId = "";
        state.viewType = ViewType.MAIN;
        state.currentCategory = "";
        state.currentItemId = "";
        state.page = 0;
        state.pinBuffer = "";
        if (state.purchaseInFlightId.isBlank()) {
            state.actions.clear();
        }
        state.lastActionAt = System.currentTimeMillis();
        return state;
    }

    private Inventory createMenu(Player player, SessionState state, ViewType viewType, int size, String title) {
        state.viewType = viewType;
        state.actions.clear();
        state.lastActionAt = System.currentTimeMillis();
        MenuHolder holder = new MenuHolder(state, player.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, size, color(title));
        holder.setInventory(inv);
        debugGui("open player=" + player.getName() + " screen=" + viewType + " shop=" + state.shopId + " category=" + state.currentCategory + " item=" + state.currentItemId + " page=" + state.page + " session=" + state.sessionId);
        return inv;
    }

    private boolean isArtifactsAdmin(Player player) {
        return player.isOp() || player.hasPermission("copimine.artifacts.admin");
    }

    private boolean hasArtifactPermission(Player player, String permission) {
        return isArtifactsAdmin(player) || player.hasPermission(permission);
    }

    private void noPermission(Player player) {
        player.sendMessage(color("&cУ вас нет прав для этой команды."));
    }

    private void sendPlayerShopHelp(Player player) {
        player.sendMessage(color("&aЛавка артефактов открывается кликом по блоку лавки."));
        player.sendMessage(color("&7Найдите лавку на спавне или в специальных местах сервера."));
    }

    private void debugGui(String message) {
        if (debugGui) {
            getLogger().info("[gui] " + message);
        }
    }

    private void setAction(Inventory inv, SessionState state, int slot, ItemStack item, String action) {
        inv.setItem(slot, item);
        state.actions.put(slot, action);
    }

    private Set<String> artifactCombatEffects() {
        return Set.of("LIGHTNING", "DRAGON_PUNISHMENT", "WATCH_GLOW", "DEBT_SNARE", "SMUGGLER_MARK", "ZMEI_GORYNYCH_POOP");
    }

    private Set<String> artifactToolEffects() {
        return Set.of("HASTE_BURST", "MINER_PULSE", "FORESTER_FOCUS", "SURVEYOR_TOUCH", "CRAFTSMAN_CHECK");
    }

    private String categoryTitle(Category category) {
        return switch (category) {
            case WEAPON -> "Боевые артефакты";
            case ARMOR -> "Защитные артефакты";
            case TOOL -> "Рабочие артефакты";
            case RP -> "RP-предметы";
        };
    }

    private String categoryHint(Category category) {
        return switch (category) {
            case WEAPON -> "&7Оружие с короткими контролируемыми эффектами.";
            case ARMOR -> "&7Броня и экипировка для выживания.";
            case TOOL -> "&7Инструменты без vein-miner и массовых сканов.";
            case RP -> "&7Обычная RP-вкладка без активных товаров.";
        };
    }

    private Material categoryMaterial(Category category) {
        return switch (category) {
            case WEAPON -> Material.NETHERITE_SWORD;
            case ARMOR -> Material.DIAMOND_CHESTPLATE;
            case TOOL -> Material.DIAMOND_PICKAXE;
            case RP -> Material.NAME_TAG;
        };
    }

    private ItemStack categoryIcon(Category category) {
        return switch (category) {
            case WEAPON -> button(Material.NETHERITE_SWORD, "&bБоевые артефакты", List.of("&7Клинки, топоры и луки.", "&8Короткие эффекты, общая перезарядка."));
            case ARMOR -> button(Material.DIAMOND_CHESTPLATE, "&aЗащитные артефакты", List.of("&7Экипировка для шахты, патруля и доставки."));
            case TOOL -> button(Material.DIAMOND_PICKAXE, "&eРабочие артефакты", List.of("&7Кирки, топоры, лопаты и ремесло.", "&8Без массовых проверок мира."));
            case RP -> button(Material.PAPER, "&eПока пусто", List.of("&7Сейчас в этой категории нет доступных товаров."));
        };
    }

    private ItemStack previewIcon(CatalogItem item) {
        List<String> lore = new ArrayList<>();
        lore.add(color("&7Цена: &f" + item.priceAr() + " AR"));
        lore.add(color("&7Редкость: &f" + item.rarity()));
        if (item.cooldownSeconds() > 0) lore.add(color("&7Перезарядка: &f" + item.cooldownSeconds() + " сек."));
        if (item.effectChancePercent() > 0 && item.effectChancePercent() < 100) lore.add(color("&7Шанс эффекта: &f" + item.effectChancePercent() + "%"));
        lore.addAll(item.lore().stream().map(this::color).toList());
        ItemStack stack = button(item.material(), item.name(), lore);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && item.customModelData() > 0) {
            meta.setCustomModelData(item.customModelData());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack soonIcon(String name, String line) {
        return button(Material.CLOCK, name, List.of(line, "&7Сейчас эта витрина пустая."));
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        meta.setDisplayName(color(name));
        meta.setLore(lore.stream().map(this::color).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        stack.setItemMeta(meta);
        return stack;
    }

    private boolean customBlockVisualsEnabled() {
        return getConfig().getBoolean("custom-block-visuals.enabled", true);
    }

    private double customBlockScale(String kind) {
        return getConfig().getDouble("custom-block-visuals.models." + kind + ".scale", 1.01D);
    }

    private double customBlockOffsetY(String kind) {
        return getConfig().getDouble("custom-block-visuals.models." + kind + ".offset-y", 0.5D);
    }

    private String first(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void spawnOrReplaceProtectedBlockVisual(Location blockLocation, String kind, String linkedId, Material baseMaterial, int customModelData, String modelId) throws Exception {
        if (!customBlockVisualsEnabled() || blockLocation == null || blockLocation.getWorld() == null || linkedId == null || linkedId.isBlank()) return;
        runAsync(() -> {
            try {
                Map<String, Object> currentRow = fetchProtectedBlockVisualRow(kind, linkedId);
                runSync(() -> {
                    try {
                        cleanupProtectedBlockVisualEntities(blockLocation, kind, linkedId, currentRow);
                        Location displayLocation = blockLocation.clone().add(0.5D, customBlockOffsetY(kind), 0.5D);
                        ItemStack visualItem = new ItemStack(baseMaterial);
                        ItemMeta meta = visualItem.getItemMeta();
                        if (meta != null) {
                            meta.setDisplayName(color("&f" + modelId));
                            meta.setCustomModelData(customModelData);
                            meta.addItemFlags(ItemFlag.values());
                            visualItem.setItemMeta(meta);
                        }
                        float scale = (float) customBlockScale(kind);
                        ItemDisplay display = blockLocation.getWorld().spawn(displayLocation, ItemDisplay.class, entity -> {
                            entity.setItemStack(visualItem);
                            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                            entity.setPersistent(true);
                            entity.setGravity(false);
                            entity.setInvulnerable(true);
                            entity.setBillboard(Display.Billboard.FIXED);
                            entity.getPersistentDataContainer().set(visualEntityTypeKey, PersistentDataType.STRING, "PROTECTED_BLOCK_VISUAL");
                            entity.getPersistentDataContainer().set(visualKindKey, PersistentDataType.STRING, kind);
                            entity.getPersistentDataContainer().set(visualLinkedIdKey, PersistentDataType.STRING, linkedId);
                            entity.getPersistentDataContainer().set(visualModelIdKey, PersistentDataType.STRING, modelId);
                            entity.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
                        });
                        saveProtectedBlockVisualAsync(blockLocation, kind, linkedId, baseMaterial, customModelData, modelId, display.getUniqueId().toString());
                    } catch (Exception error) {
                        getLogger().log(Level.WARNING, "Artifact shop visual spawn failed", error);
                    }
                });
            } catch (Exception error) {
                getLogger().log(Level.WARNING, "Artifact shop visual fetch failed", error);
            }
        });
    }

    private void cleanupProtectedBlockVisuals(String kind, String linkedId) throws Exception {
        if (linkedId == null || linkedId.isBlank()) return;
        runAsync(() -> {
            try {
                Map<String, Object> row = fetchProtectedBlockVisualRow(kind, linkedId);
                Location baseLocation = visualBaseLocation(row);
                runSync(() -> cleanupProtectedBlockVisualEntities(baseLocation, kind, linkedId, row));
                markProtectedBlockVisualInactive(kind, linkedId);
            } catch (Exception error) {
                getLogger().log(Level.WARNING, "Artifact shop visual cleanup failed", error);
            }
        });
    }

    private void repairProtectedBlockVisuals() throws Exception {
        if (!customBlockVisualsEnabled()) return;
        List<String> loadedChunks = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                loadedChunks.add(world.getName() + ":" + chunk.getX() + ":" + chunk.getZ());
            }
        }
        runAsync(() -> {
            for (String entry : loadedChunks) {
                String[] parts = entry.split(":");
                if (parts.length != 3) continue;
                try {
                    repairProtectedBlockVisuals(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                } catch (Exception error) {
                    getLogger().log(Level.WARNING, "Artifact startup visual repair failed", error);
                }
            }
        });
    }

    private void repairProtectedBlockVisuals(String worldName, int chunkX, int chunkZ) throws Exception {
        if (!customBlockVisualsEnabled()) return;
        runAsync(() -> {
            try {
                List<Shop> chunkShops = shopsByLocation.values().stream()
                        .filter(shop -> shop.world().equals(worldName) && (shop.x() >> 4) == chunkX && (shop.z() >> 4) == chunkZ && shop.enabled())
                        .toList();
                Connection c = pgPool.acquire();
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT linked_id,entity_uuid,model_id,custom_model_data FROM protected_block_visuals WHERE kind='ARTIFACT_SHOP' AND active=1 AND linked_id = ANY (?)"
                )) {
                    Array ids = c.createArrayOf("text", chunkShops.stream().map(Shop::shopId).toArray(String[]::new));
                    ps.setArray(1, ids);
                    Map<String, Map<String, Object>> visuals = new HashMap<>();
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> row = new HashMap<>();
                            row.put("entity_uuid", rs.getString("entity_uuid"));
                            row.put("model_id", rs.getString("model_id"));
                            row.put("custom_model_data", rs.getInt("custom_model_data"));
                            visuals.put(rs.getString("linked_id"), row);
                        }
                    }
                    runSync(() -> {
                        try {
                            applyProtectedBlockVisualRepairs(worldName, chunkX, chunkZ, chunkShops, visuals);
                        } catch (Exception e) {
                            getLogger().log(Level.WARNING, "Artifact shop visual apply failed", e);
                        }
                    });
                } finally {
                    pgPool.release(c);
                }
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Artifact shop visual fetch failed", e);
            }
        });
    }

    private void applyProtectedBlockVisualRepairs(String worldName, int chunkX, int chunkZ, List<Shop> chunkShops, Map<String, Map<String, Object>> visuals) throws Exception {
        World world = Bukkit.getWorld(worldName);
        if (world == null || !world.isChunkLoaded(chunkX, chunkZ)) return;
        for (Shop shop : chunkShops) {
            repairProtectedBlockVisual(shop, visuals.getOrDefault(shop.shopId(), Map.of()));
        }
    }

    private void repairProtectedBlockVisual(Shop shop, Map<String, Object> row) throws Exception {
        World world = Bukkit.getWorld(shop.world());
        if (world == null) return;
        Location location = new Location(world, shop.x(), shop.y(), shop.z());
        String expectedEntityUuid = first(String.valueOf(row.getOrDefault("entity_uuid", "")), "");
        Entity entity = null;
        if (!expectedEntityUuid.isBlank()) {
            try {
                entity = Bukkit.getEntity(UUID.fromString(expectedEntityUuid));
            } catch (Exception ignored) {
            }
        }
        boolean validEntity = isOwnedProtectedVisualEntity(entity, "ARTIFACT_SHOP", shop.shopId(), "artifact_shop_marker", MODEL_ARTIFACT_SHOP_MARKER);
        cleanupNearbyProtectedVisualDuplicates(location, "ARTIFACT_SHOP", shop.shopId(), validEntity ? entity.getUniqueId().toString() : "");
        if (!validEntity) {
            spawnOrReplaceProtectedBlockVisual(location, "ARTIFACT_SHOP", shop.shopId(), Material.PAPER, MODEL_ARTIFACT_SHOP_MARKER, "artifact_shop_marker");
        }
    }

    private void cleanupNearbyProtectedVisuals(Location blockLocation, String kind, String linkedId) {
        if (blockLocation == null || blockLocation.getWorld() == null) return;
        for (Entity entity : blockLocation.getWorld().getNearbyEntities(blockLocation.clone().add(0.5D, customBlockOffsetY(kind), 0.5D), 0.9D, 0.9D, 0.9D)) {
            removeOwnedProtectedVisualEntity(entity, kind, linkedId);
        }
    }

    private void cleanupNearbyProtectedVisualDuplicates(Location blockLocation, String kind, String linkedId, String keepEntityUuid) {
        if (blockLocation == null || blockLocation.getWorld() == null) return;
        for (Entity entity : blockLocation.getWorld().getNearbyEntities(blockLocation.clone().add(0.5D, customBlockOffsetY(kind), 0.5D), 0.9D, 0.9D, 0.9D)) {
            if (!isOwnedProtectedVisualEntity(entity, kind, linkedId, "artifact_shop_marker", MODEL_ARTIFACT_SHOP_MARKER)) continue;
            if (!keepEntityUuid.isBlank() && keepEntityUuid.equals(entity.getUniqueId().toString())) continue;
            entity.remove();
        }
    }

    private void removeOwnedProtectedVisualEntity(Entity entity, String kind, String linkedId) {
        if (!isOwnedProtectedVisualEntity(entity, kind, linkedId, "artifact_shop_marker", MODEL_ARTIFACT_SHOP_MARKER)) return;
        entity.remove();
    }

    private boolean isOwnedProtectedVisualEntity(Entity entity, String kind, String linkedId, String modelId, int customModelData) {
        if (!(entity instanceof ItemDisplay display)) return false;
        PersistentDataContainer pdc = display.getPersistentDataContainer();
        if (!"PROTECTED_BLOCK_VISUAL".equals(pdc.get(visualEntityTypeKey, PersistentDataType.STRING))) return false;
        if (!kind.equals(first(pdc.get(visualKindKey, PersistentDataType.STRING), ""))) return false;
        if (!linkedId.equals(first(pdc.get(visualLinkedIdKey, PersistentDataType.STRING), ""))) return false;
        if (!modelId.equals(first(pdc.get(visualModelIdKey, PersistentDataType.STRING), ""))) return false;
        ItemStack stack = display.getItemStack();
        if (stack == null || stack.getType() != Material.PAPER) return false;
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.hasCustomModelData() && meta.getCustomModelData() == customModelData;
    }

    private Map<String, Object> fetchProtectedBlockVisualRow(String kind, String linkedId) throws Exception {
        Connection c = pgPool.acquire();
        try (PreparedStatement select = c.prepareStatement("SELECT entity_uuid,world,x,y,z FROM protected_block_visuals WHERE kind=? AND linked_id=? AND active=1 ORDER BY updated_at DESC LIMIT 1")) {
            select.setString(1, kind);
            select.setString(2, linkedId);
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    return Map.of();
                }
                Map<String, Object> row = new HashMap<>();
                row.put("entity_uuid", rs.getString("entity_uuid"));
                row.put("world", rs.getString("world"));
                row.put("x", rs.getInt("x"));
                row.put("y", rs.getInt("y"));
                row.put("z", rs.getInt("z"));
                return row;
            }
        } finally {
            pgPool.release(c);
        }
    }

    private Location visualBaseLocation(Map<String, Object> row) {
        if (row == null || row.isEmpty()) return null;
        World world = Bukkit.getWorld(first(String.valueOf(row.getOrDefault("world", "")), ""));
        if (world == null) return null;
        return new Location(world, parseInt(String.valueOf(row.get("x")), 0), parseInt(String.valueOf(row.get("y")), 0), parseInt(String.valueOf(row.get("z")), 0));
    }

    private void cleanupProtectedBlockVisualEntities(Location baseLocation, String kind, String linkedId, Map<String, Object> row) {
        if (row != null && !row.isEmpty()) {
            String entityUuid = first(String.valueOf(row.getOrDefault("entity_uuid", "")), "");
            if (!entityUuid.isBlank()) {
                try {
                    removeOwnedProtectedVisualEntity(Bukkit.getEntity(UUID.fromString(entityUuid)), kind, linkedId);
                } catch (Exception ignored) {
                }
            }
        }
        if (baseLocation != null) {
            cleanupNearbyProtectedVisuals(baseLocation, kind, linkedId);
        }
    }

    private void markProtectedBlockVisualInactive(String kind, String linkedId) {
        runAsync(() -> {
            try {
                Connection c = pgPool.acquire();
                try (PreparedStatement ps = c.prepareStatement("UPDATE protected_block_visuals SET active=0,updated_at=? WHERE kind=? AND linked_id=? AND active=1")) {
                    ps.setLong(1, now());
                    ps.setString(2, kind);
                    ps.setString(3, linkedId);
                    ps.executeUpdate();
                } finally {
                    pgPool.release(c);
                }
            } catch (Exception error) {
                getLogger().log(Level.WARNING, "Artifact visual deactivate failed", error);
            }
        });
    }

    private void saveProtectedBlockVisualAsync(Location blockLocation, String kind, String linkedId, Material baseMaterial, int customModelData, String modelId, String entityUuid) {
        String visualId = "pbv-" + UUID.randomUUID();
        long createdAt = now();
        double offsetY = customBlockOffsetY(kind);
        double scale = customBlockScale(kind);
        runAsync(() -> {
            try {
                Connection c = pgPool.acquire();
                try (PreparedStatement delete = c.prepareStatement("DELETE FROM protected_block_visuals WHERE kind=? AND linked_id=?");
                     PreparedStatement insert = c.prepareStatement("""
                         INSERT INTO protected_block_visuals(
                             id,kind,linked_id,world,x,y,z,entity_uuid,base_material,custom_model_data,model_id,
                             offset_x,offset_y,offset_z,scale_x,scale_y,scale_z,yaw,pitch,created_at,updated_at,active
                         ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,1)
                     """)) {
                    delete.setString(1, kind);
                    delete.setString(2, linkedId);
                    delete.executeUpdate();
                    insert.setString(1, visualId);
                    insert.setString(2, kind);
                    insert.setString(3, linkedId);
                    insert.setString(4, blockLocation.getWorld().getName());
                    insert.setInt(5, blockLocation.getBlockX());
                    insert.setInt(6, blockLocation.getBlockY());
                    insert.setInt(7, blockLocation.getBlockZ());
                    insert.setString(8, entityUuid);
                    insert.setString(9, baseMaterial.name());
                    insert.setInt(10, customModelData);
                    insert.setString(11, modelId);
                    insert.setDouble(12, 0.5D);
                    insert.setDouble(13, offsetY);
                    insert.setDouble(14, 0.5D);
                    insert.setDouble(15, scale);
                    insert.setDouble(16, scale);
                    insert.setDouble(17, scale);
                    insert.setDouble(18, 0D);
                    insert.setDouble(19, 0D);
                    insert.setLong(20, createdAt);
                    insert.setLong(21, createdAt);
                    insert.executeUpdate();
                } finally {
                    pgPool.release(c);
                }
            } catch (Exception error) {
                getLogger().log(Level.WARNING, "Artifact visual save failed", error);
            }
        });
    }

    private void runAsync(Runnable runnable) {
        dbExecutor.submit(runnable);
    }

    private void runSync(Runnable runnable) {
        Bukkit.getScheduler().runTask(this, runnable);
    }

    private String blockKey(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private long now() {
        return Instant.now().getEpochSecond();
    }

    private Category parseCategory(String raw) {
        try { return Category.valueOf(raw.toUpperCase(Locale.ROOT)); }
        catch (Exception e) { return Category.RP; }
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private String strip(String text) {
        return ChatColor.stripColor(color(text));
    }

    private int artifactModelData(String itemId) {
        return ARTIFACT_MODEL_DATA.getOrDefault(itemId, 0);
    }

    private int artifactEffectChance(String itemId) {
        return ARTIFACT_EFFECT_CHANCE.getOrDefault(itemId, 100);
    }

    private String artifactVisualEffect(String itemId) {
        return ARTIFACT_VISUAL_EFFECTS.getOrDefault(itemId, "");
    }

    private int normalizeChance(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private boolean rollEffectChance(CatalogItem item) {
        int chance = normalizeChance(item.effectChancePercent());
        if (chance >= 100) return true;
        if (chance <= 0) return false;
        return random.nextInt(100) < chance;
    }

    private String maskedPin(String pin) {
        if (pin == null || pin.isEmpty()) return "• • • •";
        return "*".repeat(pin.length());
    }

    private String nice(String key) {
        return Arrays.stream(key.split("_")).map(s -> s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1)).collect(Collectors.joining(" "));
    }

    private String shortId(String id) {
        return id == null ? "" : id.substring(0, Math.min(8, id.length()));
    }

    private int parseInt(String raw, int fallback) {
        try { return Integer.parseInt(raw); } catch (Exception e) { return fallback; }
    }

    private long parseLong(String raw, long fallback) {
        try { return Long.parseLong(raw); } catch (Exception e) { return fallback; }
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private List<String> asStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private String toJson(List<String> values) {
        return "[" + values.stream().map(v -> "\"" + v.replace("\"", "\\\"") + "\"").collect(Collectors.joining(",")) + "]";
    }

    private String safeErr(Throwable t) {
        String m = t.getMessage();
        return m == null ? t.getClass().getSimpleName() : m;
    }

    private String safeBridgeCode(String code) {
        if (code == null || code.isBlank()) return "BRIDGE_REJECTED";
        String normalized = code.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_\\-]", "_");
        return normalized.length() > 48 ? normalized.substring(0, 48) : normalized;
    }

    private List<String> prefix(List<String> options, String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(x -> x.startsWith(p)).toList();
    }

    private final class ArtifactBridgeAdapter {
        private CopiMineEconomyCore.ArtifactsBridge resolveBridge() {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("CopiMineEconomyCore");
            if (!(plugin instanceof CopiMineEconomyCore main) || !plugin.isEnabled()) return null;
            try {
                return main.artifactsBridge();
            } catch (Exception e) {
                if (bridgeWarned.compareAndSet(false, true)) {
                    getLogger().warning("Artifacts bridge is unavailable: " + safeErr(e));
                }
                return null;
            }
        }

        boolean isAvailable() {
            return resolveBridge() != null;
        }

        BridgeHealthSnapshot health(Player player, String context) {
            CopiMineEconomyCore.ArtifactsBridge bridge = resolveBridge();
            if (bridge == null) return new BridgeHealthSnapshot(false, false, false, 0L, context, "BRIDGE_UNAVAILABLE");
            try {
                CopiMineEconomyCore.Health value = bridge.health(player == null ? null : player.getUniqueId(), context);
                return new BridgeHealthSnapshot(value.bridgeReady, value.postgresReady, value.pinReady, value.balance, value.context, value.lastError);
            } catch (Exception e) {
                return new BridgeHealthSnapshot(false, false, false, 0L, context, safeErr(e));
            }
        }

        BridgePinStatus pinStatus(Player player) {
            CopiMineEconomyCore.ArtifactsBridge bridge = resolveBridge();
            if (bridge == null) return new BridgePinStatus(false, false, 0L);
            try {
                CopiMineEconomyCore.PinStatus value = bridge.pinStatus(player.getUniqueId());
                return new BridgePinStatus(value.configured, value.mustChange, value.lockedSeconds);
            } catch (Exception e) {
                return new BridgePinStatus(false, false, 0L);
            }
        }

        BridgeTxnResult charge(Player player, long amount, String pin, String idempotencyKey, String action, String details) {
            return invokeTxn("charge", player, amount, pin, idempotencyKey, action, details);
        }

        BridgeTxnResult refund(Player player, long amount, String idempotencyKey, String action, String details) {
            return invokeTxn("refund", player, amount, "", idempotencyKey, action, details);
        }

        private BridgeTxnResult invokeTxn(String method, Player player, long amount, String pin, String idempotencyKey, String action, String details) {
            CopiMineEconomyCore.ArtifactsBridge bridge = resolveBridge();
            if (bridge == null) return new BridgeTxnResult(false, "BRIDGE_UNAVAILABLE", "BankService bridge is unavailable.", 0L, "");
            try {
                CopiMineEconomyCore.TxnResult value;
                if ("charge".equals(method)) {
                    value = bridge.charge(player.getUniqueId(), player.getName(), amount, pin, idempotencyKey, action, details);
                } else {
                    value = bridge.refund(player.getUniqueId(), player.getName(), amount, idempotencyKey, action, details);
                }
                return new BridgeTxnResult(value.ok, value.code, value.message, value.balanceAfter, value.txId);
            } catch (Exception e) {
                return new BridgeTxnResult(false, "BRIDGE_ERROR", safeErr(e), 0L, "");
            }
        }
    }
}
