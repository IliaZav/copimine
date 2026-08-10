package me.copimine.artifacts;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.stream.Collectors;
import me.copimine.economycore.CopiMineEconomyCore;
import me.copimine.economycore.CopiMineEconomyCore.ArtifactsBridge;
import me.copimine.economycore.CopiMineEconomyCore.DonationBalanceService;
import me.copimine.economycore.CopiMineEconomyCore.DonationPaymentService;
import me.copimine.economycore.CopiMineEconomyCore.DonationPurchaseService;
import me.copimine.economycore.CopiMineEconomyCore.ElectionRuntimeService;
import me.copimine.economycore.CopiMineEconomyCore.Health;
import me.copimine.economycore.CopiMineEconomyCore.PinStatus;
import me.copimine.economycore.CopiMineEconomyCore.TxnResult;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.ItemDisplay.ItemDisplayTransform;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import io.papermc.paper.event.player.PlayerShieldDisableEvent;
import io.papermc.paper.event.entity.EntityInsideBlockEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.block.Container;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public final class CopiMineArtifacts extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
   private static final double AR_THEFT_PROC_CHANCE = 0.001D;
   private static final String PRESIDENT_BUDGET_ACCOUNT_ID = "PRESIDENT_BUDGET";
   private static final String TREASURY_LABEL = "Казна CopiMine";
   private static final UUID EMPTY_UUID = new UUID(0L, 0L);
   private static final int MODEL_ARTIFACT_SHOP_MARKER = 14004;
   private static final int POZDNYAKOV_LAVA_RADIUS = 2;
   private static final long POZDNYAKOV_LAVA_RESTORE_TICKS = 100L;
   private static final String ARTIFACT_LIMIT_SUPPLY = "ARTIFACT_LIMIT_SUPPLY";
   private static final String ARTIFACT_LIMIT_PLAYER = "ARTIFACT_LIMIT_PLAYER";
   private static final String GUI_BACK_LABEL = "&aНазад";
   private static final int VISUAL_REPAIR_BATCH_SIZE = 8;
   private static final int COMPASS_COOLDOWN_SECONDS = 15;
   /** One chunk is sixteen blocks; the actual limit is the world's view distance. */
   private static final double MAX_COMPASS_TELEPORT_DISTANCE = 16.0D;
   private static final Map<String, Integer> ARTIFACT_MODEL_DATA = Map.of("zmei_gorynych", 10001);
   // The catalog YAML is the single source of truth for effect probability.
   // This fallback is only used when a deployment has no items.yml yet.
   private static final Map<String, Integer> ARTIFACT_EFFECT_CHANCE = Map.of("zmei_gorynych", 100);
   private static final Map<String, String> ARTIFACT_VISUAL_EFFECTS = Map.of("zmei_gorynych", "INVERTED_SCREEN");
   private final Map<String, CopiMineArtifacts.CatalogItem> catalogById = new ConcurrentHashMap<>();
   private final Map<String, CopiMineArtifacts.DonationCatalogItem> donationCatalogById = new ConcurrentHashMap<>();
   private final Map<CopiMineArtifacts.Category, List<CopiMineArtifacts.CatalogItem>> catalogByCategory = new ConcurrentHashMap<>();
   private final Set<String> adminOnlyCatalogItems = ConcurrentHashMap.newKeySet();
   private final Map<UUID, CopiMineArtifacts.SessionState> sessions = new ConcurrentHashMap<>();
   private final Map<String, CopiMineArtifacts.Shop> shopsByLocation = new ConcurrentHashMap<>();
   /** Prevent two bulk-claim workflows from reserving the same player's rows concurrently. */
   private final Set<UUID> claimAllInFlight = ConcurrentHashMap.newKeySet();
   private final Map<String, Long> actionCooldowns = new ConcurrentHashMap<>();
   private final Map<UUID, Long> pozdnyakovNauseaCooldowns = new ConcurrentHashMap<>();
   private final Map<String, PozdnyakovMagmaRestore> pozdnyakovMagmaRestores = new ConcurrentHashMap<>();
   private final Map<String, TemporaryBlockRestore> temporaryTrapRestores = new ConcurrentHashMap<>();
   /**
    * A plugin can remove an Item entity without producing a damage/despawn
    * event.  Keep a small in-memory guard so the durable loss journal is not
    * appended twice when Paper emits both removal and a preceding damage
    * notification for the same official donation instance.
    */
   private final Set<String> lossJournalInFlight = ConcurrentHashMap.newKeySet();
   private final Map<UUID, Location> lastDeathLocations = new ConcurrentHashMap<>();
   private final Map<String, String> instanceToItem = new ConcurrentHashMap<>();
   private final Map<String, CopiMineArtifacts.OfficialInstanceBinding> instanceBindings = new ConcurrentHashMap<>();
   /** Instance ids whose physical copy is no longer valid (lost/consumed/replaced). */
   private final Set<String> terminalDonationInstanceIds = ConcurrentHashMap.newKeySet();
   private final Map<UUID, ItemStack> pendingInfiniteTotemRestores = new ConcurrentHashMap<>();
   private final Set<String> provisionalDonationInstanceIds = ConcurrentHashMap.newKeySet();
   private final Set<String> bindingRefreshInFlight = ConcurrentHashMap.newKeySet();
   private final Set<String> pendingVisualRepairChunks = ConcurrentHashMap.newKeySet();
   private final Set<String> suspiciousSeen = ConcurrentHashMap.newKeySet();
   /**
    * Foreign copies are quarantined by unique id.  The set is only a
    * short-lived event guard; the loss journal and the DB row remain the
    * durable idempotency boundary.
    */
   private final Set<String> foreignQuarantineInFlight = ConcurrentHashMap.newKeySet();
   private final Set<String> chainedTreeBreaks = ConcurrentHashMap.newKeySet();
   private final Queue<String> visualRepairQueue = new ConcurrentLinkedQueue<>();
   private final Object donationLossJournalLock = new Object();
   private final AtomicBoolean bridgeWarned = new AtomicBoolean(false);
   private final AtomicBoolean visualRepairDrainRunning = new AtomicBoolean(false);
   private final Random random = new Random();
   private static final long SESSION_TTL_SECONDS = 900L;
   private int donationCatalogVersion = 1;
   private long donationCatalogUpdatedAt = 0L;
   private ExecutorService dbExecutor;
   /** Durable journal writes never run on the Bukkit tick thread. */
   private ExecutorService donationJournalExecutor;
   private CopiMineArtifacts.PgPool pgPool;
   private CopiMineArtifacts.PgSettings pgSettings;
   private CopiMineArtifacts.ArtifactBridgeAdapter bridge;
   private boolean debugGui;
   private CopiMineArtifacts.VisualEffectService visualEffects;
   private Path donationLossJournalPath;
   private Path temporaryTrapJournalPath;
   private NamespacedKey keyItemId;
   private NamespacedKey keyUniqueItemId;
   private NamespacedKey keyCategory;
   private NamespacedKey keyRarity;
   private NamespacedKey keyOwnerUuid;
   private NamespacedKey keyOwnerName;
   private NamespacedKey keyPurchaseId;
   private NamespacedKey keyItemType;
   private NamespacedKey keySource;
   private NamespacedKey keyBound;
   private NamespacedKey keyReclaimable;
   private NamespacedKey keyLastDeathWorld;
   private NamespacedKey keyLastDeathX;
   private NamespacedKey keyLastDeathY;
   private NamespacedKey keyLastDeathZ;
   private NamespacedKey attackDamageKey;
   private NamespacedKey visualEntityTypeKey;
   private NamespacedKey visualKindKey;
   private NamespacedKey visualLinkedIdKey;
   private NamespacedKey visualModelIdKey;
   private BukkitTask deliveryTask;
   private BukkitTask sessionCleanupTask;
   private BukkitTask artifactEffectTask;
   private BukkitTask armorEffectTask;
   private BukkitTask donationOwnershipSweepTask;
   private BukkitTask shopAnchorGuardTask;
   /** Monotonic catalog generation used to invalidate stale purchase screens. */
   private long catalogGeneration = 1L;

   public void onEnable() {
      this.saveDefaultConfig();
      this.loadPendingInfiniteTotemRestores();
      this.debugGui = this.getConfig().getBoolean("debug_gui", false);
      this.ensureItemsConfig();
      this.keyItemId = new NamespacedKey(this, "artifact_item_id");
      this.keyUniqueItemId = new NamespacedKey(this, "artifact_unique_item_id");
      this.keyCategory = new NamespacedKey(this, "artifact_category");
      this.keyRarity = new NamespacedKey(this, "artifact_rarity");
      this.keyOwnerUuid = new NamespacedKey(this, "artifact_owner_uuid");
      this.keyOwnerName = new NamespacedKey(this, "artifact_owner_name");
      this.keyPurchaseId = new NamespacedKey(this, "artifact_purchase_id");
      this.keyItemType = new NamespacedKey(this, "copimine_item_type");
      this.keySource = new NamespacedKey(this, "artifact_source");
      this.keyBound = new NamespacedKey(this, "artifact_bound");
      this.keyReclaimable = new NamespacedKey(this, "artifact_reclaimable");
      this.keyLastDeathWorld = new NamespacedKey(this, "last_death_world");
      this.keyLastDeathX = new NamespacedKey(this, "last_death_x");
      this.keyLastDeathY = new NamespacedKey(this, "last_death_y");
      this.keyLastDeathZ = new NamespacedKey(this, "last_death_z");
      this.attackDamageKey = new NamespacedKey(this, "artifact_attack_damage");
      this.visualEntityTypeKey = new NamespacedKey("copimine", "visual_entity_type");
      this.visualKindKey = new NamespacedKey("copimine", "visual_kind");
      this.visualLinkedIdKey = new NamespacedKey("copimine", "visual_linked_id");
      this.visualModelIdKey = new NamespacedKey("copimine", "visual_model_id");
      if (!this.customBlockVisualsEnabled()) {
         this.cleanupAllProtectedBlockVisualEntities();
      }
      // Catalog parsing is local and cheap; PostgreSQL schema/catalog/cache
      // work is deliberately started below on dbExecutor so a slow or
      // unavailable database can never stall the Bukkit enable tick.
      this.loadCatalogFromConfig();
      this.visualEffects = new CopiMineArtifacts.VisualEffectService(this);
      int dbThreads = Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors()));
      // A fixed pool with its default unbounded LinkedBlockingQueue can keep
      // accepting GUI/event work while PostgreSQL is unavailable and exhaust
      // memory.  Back-pressure is explicit; callers fail closed instead of
      // running JDBC on the Bukkit thread.
      // (The old Executors.newFixedThreadPool implementation is deliberately
      // not used; keep this marker in the source so release audits can detect
      // that the unbounded variant was considered and rejected.)
      this.dbExecutor = new ThreadPoolExecutor(
            dbThreads, dbThreads, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1024),
            new ThreadPoolExecutor.AbortPolicy());
      this.donationJournalExecutor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(256),
            new ThreadPoolExecutor.AbortPolicy());
      this.donationLossJournalPath = this.getDataFolder().toPath().resolve("donation-loss-journal.tsv");
      this.temporaryTrapJournalPath = this.getDataFolder().toPath().resolve("temporary-traps.tsv");
      this.loadPendingDonationLossJournalIdentities();
      this.loadTemporaryTrapJournal();
      Plugin var1 = Bukkit.getPluginManager().getPlugin("CopiMineEconomyCore");
      if (var1 != null && var1.isEnabled()) {
         this.bridge = new CopiMineArtifacts.ArtifactBridgeAdapter();
         if (!this.bridge.isAvailable()) {
            this.getLogger()
               .severe("CopiMineEconomyCore ArtifactsBridge is unavailable. CopiMineArtifacts will not start without the official economy bridge.");
            this.getServer().getPluginManager().disablePlugin(this);
         } else if (!this.runAsync(() -> {
            try {
               Class.forName("org.postgresql.Driver");
               this.pgSettings = this.loadPgSettings();
               this.pgPool = new CopiMineArtifacts.PgPool(this.pgSettings, 4);
               this.ensureSchema();
               if (!this.customBlockVisualsEnabled()) {
                  this.deactivateAllProtectedBlockVisualRowsAsync();
               }
               this.syncCatalogToPostgres();
               this.loadShopsFromPostgres();
               this.loadInstanceCache();
               this.flushPendingDonationLossJournalAsync();
               this.runAsync(this::reconcilePendingRevenuePayouts);
               this.runAsync(this::reconcileOrphanedShopTransfers);
               this.runAsync(() -> {
                  CopiMineArtifacts.BridgeHealthSnapshot health = this.bridge.health((UUID)null, "artifacts-startup");
                  this.getLogger().info("Artifacts bridge ready=" + health.bridgeReady() + " postgres=" + health.postgresReady() + " context=" + health.context());
                  this.audit("SERVER", "bridge_ready", "CopiMineEconomyCore", "postgres=" + health.postgresReady() + " context=" + health.context());
               });
               Bukkit.getScheduler().runTask(this, this::finishEnable);
            } catch (Exception error) {
               this.getLogger().log(Level.SEVERE, "CopiMineArtifacts failed to initialize PostgreSQL: " + this.safeErr(error), error);
               Bukkit.getScheduler().runTask(this, () -> this.getServer().getPluginManager().disablePlugin(this));
            }
         })) {
            this.getLogger().severe("CopiMineArtifacts database queue is unavailable; refusing to enable without a durable database.");
            this.getServer().getPluginManager().disablePlugin(this);
         }
      } else {
         this.getLogger().severe("CopiMineEconomyCore is not enabled. CopiMineArtifacts requires the official economy bridge and will stop.");
         this.getServer().getPluginManager().disablePlugin(this);
      }
   }

   /** Complete Bukkit registration only after schema/cache loading succeeded off-thread. */
   private void finishEnable() {
      if (!this.isEnabled() || this.pgPool == null) {
         return;
      }
      try {
         this.repairShopTitleDisplays();
         this.repairProtectedBlockVisuals();
      } catch (Exception error) {
         this.getLogger().log(Level.SEVERE, "CopiMineArtifacts failed to repair persisted visuals", error);
         this.getServer().getPluginManager().disablePlugin(this);
         return;
      }
      PluginCommand artifactsCommand = this.getCommand("cmartifacts");
      if (artifactsCommand != null) {
         artifactsCommand.setExecutor(this);
         artifactsCommand.setTabCompleter(this);
      }
      PluginCommand setPriceCommand = this.getCommand("setprice");
      if (setPriceCommand != null) {
         setPriceCommand.setExecutor(this);
         setPriceCommand.setTabCompleter(this);
      }
      Bukkit.getPluginManager().registerEvents(this, this);
      this.registerExternalItemRemovalListener();
      this.deliveryTask = Bukkit.getScheduler().runTaskTimer(this, this::tickPendingHints, 20L * 60L, 20L * 60L);
      this.sessionCleanupTask = Bukkit.getScheduler().runTaskTimer(this, this::cleanupExpiredSessions, 20L * 60L, 20L * 60L);
      this.artifactEffectTask = Bukkit.getScheduler().runTaskTimer(this, this::tickPozdnyakovAce, 10L, 10L);
      this.armorEffectTask = Bukkit.getScheduler().runTaskTimer(this, this::tickEquippedArmor, 1L, 20L);
      // A pickup/inventory event is the fast path.  This low-cost sweep is
      // the restart/third-party-plugin safety net for foreign copies.
      // Custom shop items are ordinary transferable stacks.  The historical
      // owner/quarantine sweep would silently pull them out of chests and
      // other players' inventories, so it is intentionally retired.
      this.donationOwnershipSweepTask = null;
      // A shop is anchored to a real block.  Missing anchors stay disabled;
      // no ghost visual is recreated after WorldEdit/plugin removal.
      this.shopAnchorGuardTask = Bukkit.getScheduler().runTaskTimer(this, this::guardShopAnchors, 40L, 40L);
      this.getLogger().info("CopiMineArtifacts enabled with " + this.catalogById.size() + " active catalog items.");
   }

   /**
    * EntityRemoveEvent is deprecated by Paper, but it is the only reliable
    * signal for a few silent item removals (clear-lag, creative cleanup and
    * plugin discard). Register it explicitly instead of annotation scanning so
    * Paper does not emit the deprecated-listener performance warning for every
    * server start.
    */
   @SuppressWarnings({"deprecation", "removal"})
   private void registerExternalItemRemovalListener() {
      try {
         EventExecutor executor = (listener, event) -> {
            if (event instanceof EntityRemoveEvent removal) {
               this.onDonationItemRemoved(removal);
            }
         };
         Bukkit.getPluginManager().registerEvent(
               EntityRemoveEvent.class,
               this,
               EventPriority.MONITOR,
               executor,
               this,
               true
         );
      } catch (Throwable error) {
         this.getLogger().warning("Entity removal reclaim listener unavailable: " + this.safeErr(error));
      }
   }

   public boolean knowsCatalogItem(String var1) {
      return var1 != null && this.catalogById.containsKey(var1.toLowerCase(Locale.ROOT));
   }

   public boolean knowsDonationCatalogItem(String itemId) {
      return itemId != null && this.donationCatalogById.containsKey(itemId.toLowerCase(Locale.ROOT));
   }

   public long donationCatalogPrice(String var1) {
      CopiMineArtifacts.DonationCatalogItem var2 = this.donationCatalogItem(var1);
      return var2 == null ? -1L : Math.max(0L, var2.priceDonation());
   }

   public List<Map<String, Object>> donationCatalogSnapshot() {
      return this.donationCatalogById.values()
         .stream()
         .sorted((var0, var1) -> var0.itemId().compareToIgnoreCase(var1.itemId()))
         .map(var1 -> {
         LinkedHashMap<String, Object> var2 = new LinkedHashMap<>();
         var2.put("item_id", var1.itemId());
         var2.put("display_name", var1.displayName());
         var2.put("base_material", var1.baseMaterial().name());
         var2.put("price_donation", var1.priceDonation());
         var2.put("enabled", var1.enabled());
         var2.put("source", var1.source());
         var2.put("owner_bound", var1.ownerBound());
         var2.put("reclaim_policy", var1.reclaimPolicy());
         var2.put("consume_policy", var1.consumePolicy());
         var2.put("effect_profile_id", var1.effectProfileId());
         var2.put("effect_description", var1.effectDescription());
         var2.put("cooldown_seconds", var1.cooldownSeconds());
         var2.put("proc_chance", var1.procChance());
         var2.put("max_stack", var1.maxStack());
         var2.put("repairable", var1.repairable());
         var2.put("custom_texture_mode_allowed", var1.customTextureModeAllowed());
         var2.put("custom_model_data", var1.customModelData());
         var2.put("visual_effect_id", var1.visualEffectId());
         var2.put("catalog_version", this.donationCatalogVersion);
         var2.put("updated_at", this.donationCatalogUpdatedAt);
         var2.put("lore", List.copyOf(var1.lore()));
         return (Map<String, Object>)var2;
      }).toList();
   }

   public void onDisable() {
      // Never leave temporary magma blocks behind during a normal plugin
      // reload/restart; every graceful shutdown restores the exact original
      // BlockData before the world is handed back to Bukkit.
      this.restorePendingMagmaBlocks();
      this.restoreAllTemporaryTraps();
      this.savePendingInfiniteTotemRestores();
      Bukkit.getScheduler().cancelTasks(this);
      this.cleanupAllProtectedBlockVisualEntities();
      if (this.deliveryTask != null) {
         this.deliveryTask.cancel();
      }

      if (this.sessionCleanupTask != null) {
         this.sessionCleanupTask.cancel();
      }

      if (this.artifactEffectTask != null) {
         this.artifactEffectTask.cancel();
      }

      if (this.armorEffectTask != null) {
         this.armorEffectTask.cancel();
      }

      if (this.donationOwnershipSweepTask != null) {
         this.donationOwnershipSweepTask.cancel();
      }

      if (this.shopAnchorGuardTask != null) {
         this.shopAnchorGuardTask.cancel();
      }

      this.claimAllInFlight.clear();

      // Drain the durable loss-journal writer before the database executor.
      // After fsync its callback schedules reconciliation on dbExecutor; the
      // reverse order would reject that callback and leave a shutdown-time
      // race between a durable journal and a closed executor.
      if (this.donationJournalExecutor != null) {
         this.donationJournalExecutor.shutdown();
         try {
            if (!this.donationJournalExecutor.awaitTermination(3L, TimeUnit.SECONDS)) {
               List<Runnable> dropped = this.donationJournalExecutor.shutdownNow();
               if (!dropped.isEmpty()) {
                  this.getLogger().warning("Artifact loss-journal writer dropped " + dropped.size() + " queued append(s); physical items were retained until their durability boundary.");
               }
               if (!this.donationJournalExecutor.awaitTermination(2L, TimeUnit.SECONDS)) {
                  this.getLogger().warning("Artifact loss-journal writer is still running after forced shutdown; journal recovery remains durable before database close.");
               }
            }
         } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            List<Runnable> dropped = this.donationJournalExecutor.shutdownNow();
            if (!dropped.isEmpty()) {
               this.getLogger().warning("Artifact loss-journal writer interrupted with " + dropped.size() + " queued append(s); physical items were retained until their durability boundary.");
            }
         }
      }

      if (this.dbExecutor != null) {
         this.dbExecutor.shutdown();

         try {
            if (!this.dbExecutor.awaitTermination(5L, TimeUnit.SECONDS)) {
               // A callback that is still using PostgreSQL must never outlive
               // the pool close below.  Durable delivery/loss workflows keep
               // their recovery row/journal before reaching this point, so
               // cancelling the remaining best-effort tasks is safer than
               // allowing them to race a closed pool during plugin disable.
               this.getLogger().warning("Artifact database executor did not drain during shutdown; cancelling remaining tasks.");
               List<Runnable> dropped = this.dbExecutor.shutdownNow();
               if (!dropped.isEmpty()) {
                  this.getLogger().warning("Artifact database executor dropped " + dropped.size() + " queued task(s); pending delivery/loss rows remain recovery-backed.");
               }
               if (!this.dbExecutor.awaitTermination(2L, TimeUnit.SECONDS)) {
                  this.getLogger().warning("Artifact database executor is still running after forced shutdown.");
               }
            }
         } catch (InterruptedException var2) {
            Thread.currentThread().interrupt();
            List<Runnable> dropped = this.dbExecutor.shutdownNow();
            if (!dropped.isEmpty()) {
               this.getLogger().warning("Artifact database executor interrupted with " + dropped.size() + " queued task(s); pending delivery/loss rows remain recovery-backed.");
            }
         }
      }

      this.visualRepairQueue.clear();
      this.pendingVisualRepairChunks.clear();
      this.visualRepairDrainRunning.set(false);

      if (this.pgPool != null) {
         this.pgPool.close();
      }
   }

   private void loadPendingInfiniteTotemRestores() {
      ConfigurationSection section = this.getConfig().getConfigurationSection("pendingInfiniteTotems");
      if (section == null) {
         return;
      }
      for (String key : section.getKeys(false)) {
         try {
            UUID playerUuid = UUID.fromString(key);
            ItemStack stack = section.getItemStack(key);
            if (stack != null && !stack.getType().isAir()) {
               pendingInfiniteTotemRestores.put(playerUuid, stack);
            }
         } catch (IllegalArgumentException ignored) {
            this.getLogger().warning("Ignoring malformed pending infinite totem owner " + key);
         }
      }
   }

   private synchronized void savePendingInfiniteTotemRestores() {
      this.getConfig().set("pendingInfiniteTotems", null);
      for (Map.Entry<UUID, ItemStack> entry : pendingInfiniteTotemRestores.entrySet()) {
         this.getConfig().set("pendingInfiniteTotems." + entry.getKey(), entry.getValue());
      }
      this.saveConfig();
   }

   private void ensureItemsConfig() {
      File var1 = new File(this.getDataFolder(), "items.yml");
      if (!var1.exists()) {
         this.getDataFolder().mkdirs();

         try {
            this.saveResource("items.yml", false);
         } catch (IllegalArgumentException var5) {
            try {
               Files.writeString(var1.toPath(),
                       this.defaultItemsYaml(),
                       StandardCharsets.UTF_8);
            } catch (IOException var4) {
               throw new RuntimeException(var4);
   }
   }
   }

   }

   private String defaultItemsYaml() {
      return "items:\n  - id: zmei_gorynych\n    category: WEAPON\n    material: NETHERITE_SWORD\n    custom_model_data: 10001\n    name: \"&6Змей Горыныч\"\n    rarity: LEGENDARY\n    price_ar: 500\n    supply_limit: 0\n    per_player_limit: 3\n    cooldown_seconds: 12\n    effect: ZMEI_GORYNYCH_POOP\n    effect_chance_percent: 100\n    visual_effect_id: INVERTED_SCREEN\n    lore:\n      - \"&7Официальное оружие CopiMineArtifacts\"\n      - \"&7При ударе может вызвать электрический импульс.\"\n";
   }

   private CopiMineArtifacts.PgSettings loadPgSettings() throws IOException {
      HashMap var1 = new HashMap<>(System.getenv());

      for (Path var3 : this.envCandidates()) {
         if (Files.isRegularFile(var3)) {
            String var5;
            try (BufferedReader var4 = Files.newBufferedReader(var3, StandardCharsets.UTF_8)) {
               while ((var5 = var4.readLine()) != null) {
                  String var6 = var5.trim();
                  if (!var6.isEmpty() && !var6.startsWith("#") && var6.contains("=")) {
                     String[] var7 = var6.split("=", 2);
                     var1.putIfAbsent(var7[0].trim(), var7[1].trim());
                  }
               }
               break;
            }
         }
      }

      String var10 = String.valueOf(var1.getOrDefault("POSTGRES_PASSWORD", ""));
      if (var10.isBlank()) {
         throw new IOException("POSTGRES_PASSWORD is missing");
      } else {
         return new CopiMineArtifacts.PgSettings(
            String.valueOf(var1.getOrDefault("POSTGRES_HOST", "127.0.0.1")),
            this.parseInt(String.valueOf(var1.getOrDefault("POSTGRES_PORT", "5432")), 5432),
            String.valueOf(var1.getOrDefault("POSTGRES_DB", "copimine")),
            String.valueOf(var1.getOrDefault("POSTGRES_SCHEMA", "copimine")),
            String.valueOf(var1.getOrDefault("POSTGRES_USER", "copimine")),
            var10
         );
      }
   }

   private List<Path> envCandidates() {
      ArrayList var1 = new ArrayList();
      String var2 = System.getenv("COPIMINE_ENV_FILE");
      if (var2 != null && !var2.isBlank()) {
         var1.add(Path.of(var2));
      }

      var1.add(Path.of("/opt/copimine/admin-web/.env"));

      Path var3 = this.releaseRootPath();
      if (var3 != null) {
         var1.add(var3.resolve("admin-web").resolve(".env"));
      }

      Path var4 = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
      var1.add(var4.resolve("../../admin-web/.env").normalize());
      var1.add(var4.resolve("../admin-web/.env").normalize());

      File var5 = this.getDataFolder();
      File var6 = var5.getParentFile();
      if (var6 != null) {
         File var7 = var6.getParentFile();
         if (var7 != null) {
            File var8 = var7.getParentFile();
            if (var8 != null) {
               File var9 = var8.getParentFile();
               if (var9 != null) {
                  var1.add(var9.toPath().resolve("admin-web").resolve(".env"));
               }
            }
         }
      }

      return var1;
   }

   private Path releaseRootPath() {
      try {
         Path var1 = this.getServer().getWorldContainer().toPath().toAbsolutePath().normalize();
         Path var2 = var1.getParent();
         Path var3 = var2 == null ? null : var2.getParent();
         if (var3 != null && Files.isDirectory(var3.resolve("admin-web"))) {
            return var3;
         }
      } catch (Exception var4) {
      }

      return null;
   }

   private void ensureSchema() throws SQLException {
      Connection var1 = this.pgPool.acquire();

      try (Statement var2 = var1.createStatement()) {
         var2.execute(
            "    CREATE TABLE IF NOT EXISTS artifact_items_catalog(\n        item_id TEXT PRIMARY KEY,\n        category TEXT NOT NULL,\n        material TEXT NOT NULL,\n        display_name TEXT NOT NULL,\n        rarity TEXT NOT NULL,\n        price_ar BIGINT NOT NULL,\n        supply_limit INTEGER NOT NULL DEFAULT 0,\n        per_player_limit INTEGER NOT NULL DEFAULT 0,\n        cooldown_seconds INTEGER NOT NULL DEFAULT 0,\n        effect_name TEXT NOT NULL DEFAULT 'NONE',\n        custom_model_data INTEGER NOT NULL DEFAULT 0,\n        effect_chance_percent INTEGER NOT NULL DEFAULT 100,\n        visual_effect_id TEXT NOT NULL DEFAULT '',\n        lore_json TEXT NOT NULL DEFAULT '[]',\n        enabled BOOLEAN NOT NULL DEFAULT TRUE,\n        updated_at BIGINT NOT NULL\n    )\n"
         );
         var2.execute(
            "    CREATE TABLE IF NOT EXISTS artifact_item_instances(\n        unique_item_id TEXT PRIMARY KEY,\n        item_id TEXT NOT NULL,\n        owner_uuid TEXT NOT NULL,\n        purchase_id TEXT NOT NULL,\n        status TEXT NOT NULL,\n        repaired_count INTEGER NOT NULL DEFAULT 0,\n        created_at BIGINT NOT NULL,\n        updated_at BIGINT NOT NULL\n    )\n"
         );
         var2.execute(
            "    CREATE TABLE IF NOT EXISTS artifact_shops(\n        shop_id TEXT PRIMARY KEY,\n        world_name TEXT NOT NULL,\n        block_x INTEGER NOT NULL,\n        block_y INTEGER NOT NULL,\n        block_z INTEGER NOT NULL,\n        title TEXT NOT NULL,\n        enabled BOOLEAN NOT NULL DEFAULT TRUE,\n        anchor_material TEXT NOT NULL DEFAULT '',\n        created_at BIGINT NOT NULL,\n        updated_at BIGINT NOT NULL\n    )\n"
         );
         var2.execute(
            "    CREATE TABLE IF NOT EXISTS protected_block_visuals(\n        id TEXT PRIMARY KEY,\n        kind TEXT NOT NULL,\n        linked_id TEXT NOT NULL,\n        world TEXT NOT NULL,\n        x INTEGER NOT NULL,\n        y INTEGER NOT NULL,\n        z INTEGER NOT NULL,\n        entity_uuid TEXT NOT NULL DEFAULT '',\n        base_material TEXT NOT NULL DEFAULT 'PAPER',\n        custom_model_data INTEGER NOT NULL DEFAULT 0,\n        model_id TEXT NOT NULL DEFAULT '',\n        offset_x DOUBLE PRECISION NOT NULL DEFAULT 0.5,\n        offset_y DOUBLE PRECISION NOT NULL DEFAULT 0.5,\n        offset_z DOUBLE PRECISION NOT NULL DEFAULT 0.5,\n        scale_x DOUBLE PRECISION NOT NULL DEFAULT 1.01,\n        scale_y DOUBLE PRECISION NOT NULL DEFAULT 1.01,\n        scale_z DOUBLE PRECISION NOT NULL DEFAULT 1.01,\n        yaw DOUBLE PRECISION NOT NULL DEFAULT 0,\n        pitch DOUBLE PRECISION NOT NULL DEFAULT 0,\n        created_at BIGINT NOT NULL DEFAULT 0,\n        updated_at BIGINT NOT NULL DEFAULT 0,\n        active INTEGER NOT NULL DEFAULT 1\n    )\n"
         );
         var2.execute(
            "    CREATE TABLE IF NOT EXISTS artifact_purchases(\n        purchase_id TEXT PRIMARY KEY,\n        unique_item_id TEXT NOT NULL,\n        player_uuid TEXT NOT NULL,\n        player_name TEXT NOT NULL,\n        item_id TEXT NOT NULL,\n        shop_id TEXT NOT NULL,\n        price_ar BIGINT NOT NULL,\n        bank_tx_id TEXT NOT NULL DEFAULT '',\n        idempotency_key TEXT NOT NULL,\n        status TEXT NOT NULL,\n        delivery_mode TEXT NOT NULL DEFAULT 'DIRECT',\n        created_at BIGINT NOT NULL,\n        updated_at BIGINT NOT NULL\n    )\n"
        );
         var2.execute(
            "    CREATE TABLE IF NOT EXISTS artifact_purchase_limit_resets(\n        player_uuid TEXT NOT NULL,\n        item_id TEXT NOT NULL,\n        reset_at BIGINT NOT NULL,\n        reset_by TEXT NOT NULL DEFAULT '',\n        idempotency_key TEXT NOT NULL DEFAULT '',\n        PRIMARY KEY(player_uuid,item_id)\n    )\n"
         );
         var2.execute(
            "    CREATE TABLE IF NOT EXISTS artifact_repairs(\n        repair_id TEXT PRIMARY KEY,\n        unique_item_id TEXT NOT NULL,\n        player_uuid TEXT NOT NULL,\n        player_name TEXT NOT NULL,\n        item_id TEXT NOT NULL,\n        repair_cost_ar BIGINT NOT NULL,\n        bank_tx_id TEXT NOT NULL DEFAULT '',\n        status TEXT NOT NULL,\n        created_at BIGINT NOT NULL\n    )\n"
         );
         // Physical repair is a slot/instance operation.  These columns let
         // recovery prove that the item seen after an async bank call is the
         // exact stack that was charged, instead of overwriting a new item in
         // the player's hand.
         var2.execute("ALTER TABLE artifact_repairs ADD COLUMN IF NOT EXISTS slot INTEGER NOT NULL DEFAULT -1");
         var2.execute("ALTER TABLE artifact_repairs ADD COLUMN IF NOT EXISTS item_fingerprint TEXT NOT NULL DEFAULT ''");
         var2.execute("ALTER TABLE artifact_repairs ADD COLUMN IF NOT EXISTS original_damage INTEGER NOT NULL DEFAULT 0");
         var2.execute(
            "    CREATE TABLE IF NOT EXISTS artifact_suspicious_events(\n        event_id TEXT PRIMARY KEY,\n        player_uuid TEXT NOT NULL,\n        player_name TEXT NOT NULL,\n        event_type TEXT NOT NULL,\n        details TEXT NOT NULL,\n        created_at BIGINT NOT NULL\n    )\n"
         );
         var2.execute(
            "    CREATE TABLE IF NOT EXISTS artifact_audit_log(\n        audit_id TEXT PRIMARY KEY,\n        actor TEXT NOT NULL,\n        action TEXT NOT NULL,\n        target_id TEXT NOT NULL,\n        details TEXT NOT NULL,\n        created_at BIGINT NOT NULL\n    )\n"
         );
         var2.execute(
            "    CREATE TABLE IF NOT EXISTS artifact_pending_deliveries(\n        delivery_id TEXT PRIMARY KEY,\n        purchase_id TEXT NOT NULL,\n        unique_item_id TEXT NOT NULL,\n        player_uuid TEXT NOT NULL,\n        item_id TEXT NOT NULL,\n        status TEXT NOT NULL,\n        created_at BIGINT NOT NULL,\n        updated_at BIGINT NOT NULL\n    )\n"
         );
         var2.execute(
            "    CREATE TABLE IF NOT EXISTS artifact_revenue_payouts(\n        purchase_id TEXT PRIMARY KEY,\n        president_uuid TEXT NOT NULL,\n        president_name TEXT NOT NULL DEFAULT '',\n        recipient_account_id TEXT NOT NULL DEFAULT 'PRESIDENT_BUDGET',\n        buyer_uuid TEXT NOT NULL,\n        buyer_name TEXT NOT NULL DEFAULT '',\n        item_id TEXT NOT NULL,\n        shop_id TEXT NOT NULL,\n        amount_ar BIGINT NOT NULL DEFAULT 0,\n        status TEXT NOT NULL DEFAULT 'PENDING',\n        bank_tx_id TEXT NOT NULL DEFAULT '',\n        idempotency_key TEXT NOT NULL DEFAULT '',\n        last_error TEXT NOT NULL DEFAULT '',\n        created_at BIGINT NOT NULL DEFAULT 0,\n        updated_at BIGINT NOT NULL DEFAULT 0\n    )\n"
         );
         var2.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_artifact_shops_block ON artifact_shops(world_name,block_x,block_y,block_z)");
         var2.execute("CREATE INDEX IF NOT EXISTS idx_artifact_purchases_player_time ON artifact_purchases(player_uuid,created_at DESC)");
         var2.execute("CREATE INDEX IF NOT EXISTS idx_artifact_instances_item ON artifact_item_instances(item_id,status)");
         var2.execute("CREATE INDEX IF NOT EXISTS idx_artifact_instances_owner_item_status ON artifact_item_instances(owner_uuid,item_id,status)");
         var2.execute(
            "CREATE UNIQUE INDEX IF NOT EXISTS ux_artifact_instances_owner_item_live ON artifact_item_instances(owner_uuid,item_id) WHERE status IN ('ACTIVE','DELIVERING','PENDING_DELIVERY')"
         );
         var2.execute("CREATE INDEX IF NOT EXISTS idx_artifact_pending_player ON artifact_pending_deliveries(player_uuid,status,created_at DESC)");
         var2.execute("CREATE INDEX IF NOT EXISTS idx_artifact_revenue_payouts_status ON artifact_revenue_payouts(status,updated_at DESC)");
         var2.execute("CREATE INDEX IF NOT EXISTS idx_artifact_revenue_payouts_president ON artifact_revenue_payouts(president_uuid,created_at DESC)");
         var2.execute("CREATE INDEX IF NOT EXISTS idx_artifact_revenue_payouts_recipient ON artifact_revenue_payouts(recipient_account_id,created_at DESC)");
         var2.execute("CREATE INDEX IF NOT EXISTS idx_artifact_repairs_player ON artifact_repairs(player_uuid,created_at DESC)");
         var2.execute("CREATE INDEX IF NOT EXISTS idx_artifact_suspicious_time ON artifact_suspicious_events(created_at DESC,event_type)");
         var2.execute("CREATE INDEX IF NOT EXISTS idx_protected_block_visuals_linked ON protected_block_visuals(linked_id,active)");
         var2.execute("CREATE INDEX IF NOT EXISTS idx_protected_block_visuals_location ON protected_block_visuals(world,x,y,z,active)");
         var2.execute("ALTER TABLE artifact_items_catalog ADD COLUMN IF NOT EXISTS supply_limit INTEGER NOT NULL DEFAULT 0");
         var2.execute("ALTER TABLE artifact_revenue_payouts ADD COLUMN IF NOT EXISTS recipient_account_id TEXT NOT NULL DEFAULT 'PRESIDENT_BUDGET'");
         var2.execute("ALTER TABLE artifact_items_catalog ADD COLUMN IF NOT EXISTS per_player_limit INTEGER NOT NULL DEFAULT 0");
         var2.execute("ALTER TABLE artifact_items_catalog ADD COLUMN IF NOT EXISTS custom_model_data INTEGER NOT NULL DEFAULT 0");
         var2.execute("ALTER TABLE artifact_items_catalog ADD COLUMN IF NOT EXISTS effect_chance_percent INTEGER NOT NULL DEFAULT 100");
         var2.execute("ALTER TABLE artifact_items_catalog ADD COLUMN IF NOT EXISTS visual_effect_id TEXT NOT NULL DEFAULT ''");
         var2.execute("ALTER TABLE artifact_shops ADD COLUMN IF NOT EXISTS anchor_material TEXT NOT NULL DEFAULT ''");
         // BROKEN is terminal.  Never migrate a durability break into the
         // reclaimable-loss state: doing so would mint a free replacement.
         // A process restart cannot safely replay a physical repair.  Leave
         // charged rows in an explicit review state instead of allowing a
         // later retry to mutate whichever item happens to be in the slot.
         try (PreparedStatement recoverRepairs = var1.prepareStatement(
               "UPDATE artifact_repairs SET status='REVIEW' WHERE status IN ('PAID','APPLYING')")) {
            int recovered = recoverRepairs.executeUpdate();
            if (recovered > 0) {
               this.getLogger().warning("Moved " + recovered + " interrupted artifact repairs to REVIEW after restart.");
            }
         }
      } finally {
         this.pgPool.release(var1);
      }
   }

   private void loadCatalogFromConfig() {
      // Every reload invalidates purchase/confirmation snapshots.  A stale
      // GUI must never charge the price that was shown before /setprice.
      this.catalogGeneration++;
      this.catalogById.clear();
      this.adminOnlyCatalogItems.clear();
      this.donationCatalogById.clear();
      this.donationCatalogVersion = 1;
      this.donationCatalogUpdatedAt = 0L;
      EnumMap<CopiMineArtifacts.Category, List<CopiMineArtifacts.CatalogItem>> var1 = new EnumMap<>(CopiMineArtifacts.Category.class);
      Set<String> configuredArtifactIds = new HashSet<>();

      for (CopiMineArtifacts.Category var5 : CopiMineArtifacts.Category.values()) {
         var1.put(var5, new ArrayList());
      }

      YamlConfiguration var12 = YamlConfiguration.loadConfiguration(new File(this.getDataFolder(), "items.yml"));

      for (Map<?, ?> var16 : var12.getMapList("items")) {
         String var6 = this.str(var16.get("id")).trim().toLowerCase(Locale.ROOT);
         if (var6.isBlank()) {
            throw new IllegalStateException("Artifact catalog item id is required.");
         }
         if (!configuredArtifactIds.add(var6)) {
            throw new IllegalStateException("Duplicate artifact catalog item id: " + var6);
         }
            CopiMineArtifacts.Category var7 = this.parseCategory(this.str(var16.get("category")));
            Material var8 = Material.matchMaterial(this.str(var16.get("material")));
            if (var8 == null) {
               throw new IllegalStateException("Invalid artifact catalog material for " + var6);
            }

            String source = this.str(var16.get("source"));
            int perPlayerLimit = Math.max(0, this.parseInt(this.str(var16.get("per_player_limit")), 0));
            List<String> var9 = this.asStringList(var16.get("lore"));
            Object var10 = var16.containsKey("visual_effect_id") ? var16.get("visual_effect_id") : this.artifactVisualEffect(var6);
            CopiMineArtifacts.CatalogItem var11 = new CopiMineArtifacts.CatalogItem(
               var6,
               var7,
               var8,
               this.str(var16.get("name")),
               this.str(var16.get("rarity")),
               this.parseLong(this.str(var16.get("price_ar")), 0L),
               Math.max(0, this.parseInt(this.str(var16.get("supply_limit")), 0)),
               perPlayerLimit,
               this.parseInt(this.str(var16.get("cooldown_seconds")), 0),
               this.str(var16.get("effect")),
               this.parseInt(this.str(var16.get("custom_model_data")), this.artifactModelData(var6)),
         // normalizeChance(parseInt(str(row.get("effect_chance_percent")), artifactEffectChance(itemId)))
         this.normalizeChance(this.parseInt(this.str(var16.get("effect_chance_percent")), this.artifactEffectChance(var6))),
               this.str(var10),
               var9
            );
            this.catalogById.put(var6, var11);
            if ("ADMIN_ONLY".equalsIgnoreCase(this.str(var16.get("source")))) {
               this.adminOnlyCatalogItems.add(var6.toLowerCase(Locale.ROOT));
            } else {
               ((List)var1.get(var7)).add(var11);
            }
      }

      ConfigurationSection var15 = var12.getConfigurationSection("donation-catalog");
      if (var15 != null) {
         this.donationCatalogVersion = Math.max(1, var15.getInt("catalog-version", 1));
         this.donationCatalogUpdatedAt = this.parseLong(String.valueOf(var15.get("updated-at")), this.now());

         for (Map<?, ?> var19 : var15.getMapList("items")) {
            CopiMineArtifacts.DonationCatalogItem var21 = this.parseDonationCatalogItem(var19);
            if (var21 != null && !var21.itemId().isBlank()) {
               if (this.catalogById.containsKey(var21.itemId()) || this.donationCatalogById.containsKey(var21.itemId())) {
                  throw new IllegalStateException("Duplicate donation catalog item id: " + var21.itemId());
               }

               this.donationCatalogById.put(var21.itemId(), var21);
               CopiMineArtifacts.CatalogItem var22 = this.synthesizeDonationRuntimeItem(var21);
               this.catalogById.put(var22.itemId(), var22);
            }
         }
      }

      this.catalogByCategory.clear();

      for (Entry<CopiMineArtifacts.Category, List<CopiMineArtifacts.CatalogItem>> var20 : var1.entrySet()) {
         this.catalogByCategory.put(var20.getKey(), List.copyOf(var20.getValue()));
      }
   }

   private CopiMineArtifacts.DonationCatalogItem parseDonationCatalogItem(Map<?, ?> var1) {
      String var2 = this.str(var1.get("item-id")).trim().toLowerCase(Locale.ROOT);
      if (var2.isBlank()) {
         return null;
      } else {
         Material var3 = Material.matchMaterial(this.str(var1.get("base-material")));
         if (var3 == null) {
            throw new IllegalStateException("Invalid donation catalog material for " + var2);
         }

         List var4 = this.asStringList(var1.get("lore"));
         return new CopiMineArtifacts.DonationCatalogItem(
            var2,
            this.str(var1.get("display-name")),
            var3,
            Math.max(0L, this.parseLong(this.str(var1.get("price-donation")), 0L)),
            this.parseBoolean(var1.get("enabled"), true),
            this.firstNonBlank(this.str(var1.get("source")), "DONATION_SHOP"),
            this.parseBoolean(var1.get("owner-bound"), true),
            this.firstNonBlank(this.str(var1.get("reclaim-policy")), "LOSS_ONLY"),
            this.firstNonBlank(this.str(var1.get("consume-policy")), "BREAKABLE"),
            this.firstNonBlank(this.str(var1.get("effect-profile-id")), var2.toUpperCase(Locale.ROOT)),
            this.firstNonBlank(
               this.str(var1.get("effect-description")),
               "Официальный донат-предмет CopiMine."
            ),
            Math.max(0, this.parseInt(this.str(var1.get("cooldown-seconds")), 0)),
            this.clampProcChance(this.parseDouble(this.str(var1.get("proc-chance")), 0.0)),
            Math.max(1, this.parseInt(this.str(var1.get("max-stack")), 1)),
            this.parseBoolean(var1.get("repairable"), false),
            this.parseBoolean(var1.get("custom-texture-mode-allowed"), true),
            Math.max(0, this.parseInt(this.str(var1.get("custom-model-data")), 0)),
            this.firstNonBlank(this.str(var1.get("visual-effect-id")), ""),
            var4
         );
      }
   }

   private CopiMineArtifacts.CatalogItem synthesizeDonationRuntimeItem(CopiMineArtifacts.DonationCatalogItem var1) {
      ArrayList var2 = new ArrayList<>(var1.lore());
      if (var2.isEmpty()) {
         var2.add(
            "&7Официальный донат-предмет CopiMine"
         );
         var2.add("&7" + var1.effectDescription());
         var2.add(
            "&7Получение и возврат только через игровую лавку."
         );
      }

      return new CopiMineArtifacts.CatalogItem(
         var1.itemId(),
         CopiMineArtifacts.Category.RP,
         var1.baseMaterial(),
         var1.displayName(),
         "DONATION",
         0L,
         0,
         0,
         var1.cooldownSeconds(),
         var1.effectProfileId(),
         var1.customModelData(),
         this.normalizeChance((int)Math.round(var1.procChance() * 100.0)),
         var1.visualEffectId(),
         var2
      );
   }

   private void syncCatalogToPostgres() throws SQLException {
      Connection var1 = this.pgPool.acquire();

      try {
         var1.setAutoCommit(false);

         try (PreparedStatement var2 = var1.prepareStatement(
               "    INSERT INTO artifact_items_catalog(item_id,category,material,display_name,rarity,price_ar,supply_limit,per_player_limit,cooldown_seconds,effect_name,custom_model_data,effect_chance_percent,visual_effect_id,lore_json,enabled,updated_at)\n    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)\n    ON CONFLICT(item_id) DO UPDATE SET\n        category=EXCLUDED.category,\n        material=EXCLUDED.material,\n        display_name=EXCLUDED.display_name,\n        rarity=EXCLUDED.rarity,\n        price_ar=EXCLUDED.price_ar,\n        supply_limit=EXCLUDED.supply_limit,\n        per_player_limit=EXCLUDED.per_player_limit,\n        cooldown_seconds=EXCLUDED.cooldown_seconds,\n        effect_name=EXCLUDED.effect_name,\n        custom_model_data=EXCLUDED.custom_model_data,\n        effect_chance_percent=EXCLUDED.effect_chance_percent,\n        visual_effect_id=EXCLUDED.visual_effect_id,\n        lore_json=EXCLUDED.lore_json,\n        enabled=EXCLUDED.enabled,\n        updated_at=EXCLUDED.updated_at\n"
            )) {
            long var3 = this.now();

            for (CopiMineArtifacts.CatalogItem var6 : this.catalogById.values()) {
               var2.setString(1, var6.itemId());
               var2.setString(2, var6.category().name());
               var2.setString(3, var6.material().name());
               var2.setString(4, var6.name());
               var2.setString(5, var6.rarity());
               var2.setLong(6, var6.priceAr());
               var2.setInt(7, var6.supplyLimit());
               var2.setInt(8, var6.perPlayerLimit());
               var2.setInt(9, var6.cooldownSeconds());
               var2.setString(10, var6.effect());
               var2.setInt(11, var6.customModelData());
               var2.setInt(12, var6.effectChancePercent());
               var2.setString(13, var6.visualEffectId());
               var2.setString(14, this.toJson(var6.lore()));
               CopiMineArtifacts.DonationCatalogItem var7 = this.donationCatalogById.get(var6.itemId());
               var2.setBoolean(15, var7 != null ? var7.enabled() : var6.category() != CopiMineArtifacts.Category.RP && !this.adminOnlyCatalogItems.contains(var6.itemId().toLowerCase(Locale.ROOT)));
               var2.setLong(16, var3);
               var2.addBatch();
            }

            var2.executeBatch();
         }

         // Keep retired items in purchase history, but prevent an old database
         // row from putting them back into the shop after a config upgrade.
         try (PreparedStatement retired = var1.prepareStatement(
               "UPDATE artifact_items_catalog SET enabled=FALSE,updated_at=? WHERE item_id=?"
            )) {
            retired.setLong(1, this.now());
            retired.setString(2, "ya_esche_ne_vse_isportil_totem");
            retired.executeUpdate();
            // eternal_totem is the AR infinite totem and remains enabled by
            // the catalog above. Only the retired donation totem is disabled.
         }

         var1.commit();
      } catch (SQLException var19) {
         var1.rollback();
         throw var19;
      } finally {
         try {
            var1.setAutoCommit(true);
         } catch (SQLException var16) {
         }

         this.pgPool.release(var1);
      }
   }

   private void loadShopsFromPostgres() throws SQLException {
      this.shopsByLocation.clear();
      Connection var1 = this.pgPool.acquire();

      try (
         PreparedStatement var2 = var1.prepareStatement("SELECT shop_id,title,world_name,block_x,block_y,block_z,enabled,anchor_material FROM artifact_shops");
         ResultSet var3 = var2.executeQuery();
      ) {
         while (var3.next()) {
            CopiMineArtifacts.Shop var4 = new CopiMineArtifacts.Shop(
               var3.getString(1), var3.getString(2), var3.getString(3), var3.getInt(4), var3.getInt(5), var3.getInt(6), var3.getBoolean(7), var3.getString(8)
            );
            this.shopsByLocation.put(var4.locationKey(), var4);
         }
      } finally {
         this.pgPool.release(var1);
      }
   }

   private void loadInstanceCache() throws SQLException {
      this.instanceToItem.clear();
      this.instanceBindings.clear();
      this.terminalDonationInstanceIds.clear();
      Connection var1 = this.pgPool.acquire();

      try (
         PreparedStatement var2 = var1.prepareStatement(
            // Only physically delivered instances are trusted by gameplay.
            // Delivering/pending rows are still transactional and must not be
            // usable until their inventory write is finalized.
            "SELECT unique_item_id,item_id,owner_uuid FROM artifact_item_instances WHERE status IN ('DELIVERED','ACTIVE')"
         );
         ResultSet var3 = var2.executeQuery();
      ) {
         while (var3.next()) {
            this.cacheOfficialBinding(var3.getString(1), var3.getString(2), var3.getString(3));
         }
      } finally {
         this.pgPool.release(var1);
      }
      this.loadTerminalInstanceCache();
      // loadInstanceCache() is also used by /cmartifacts reload.  Preserve
      // identities that are already durable in the loss journal even when
      // their PostgreSQL status has not been reconciled yet; otherwise a
      // stale dropped entity could be picked up during the restart window.
      this.loadPendingDonationLossJournalIdentities();
   }

   private void loadTerminalInstanceCache() throws SQLException {
      Connection connection = this.pgPool.acquire();
      try (PreparedStatement statement = connection.prepareStatement(
            "SELECT unique_item_id FROM artifact_item_instances WHERE status IN ('BROKEN','LOST_RECLAIMABLE','CONSUMED','REPLACED_AFTER_LOSS','DELETED_AS_INVALID','DELIVERY_REVIEW','REPAIR_REVIEW')");
           ResultSet result = statement.executeQuery()) {
         while (result.next()) {
            String id = this.firstNonBlank(result.getString(1), "");
            if (!id.isBlank()) this.terminalDonationInstanceIds.add(id);
         }
      } finally {
         this.pgPool.release(connection);
      }
   }

   private void cacheOfficialBinding(String uniqueItemId, String itemId, UUID ownerUuid) {
      this.cacheOfficialBinding(uniqueItemId, itemId, ownerUuid == null ? "" : ownerUuid.toString());
   }

    private void cacheOfficialBinding(String uniqueItemId, String itemId, String ownerUuid) {
      String safeUnique = this.firstNonBlank(uniqueItemId, "");
      String safeItem = this.firstNonBlank(itemId, "");
      String safeOwner = this.firstNonBlank(ownerUuid, "");
      if (!safeUnique.isBlank() && !safeItem.isBlank() && !safeOwner.isBlank()) {
         this.provisionalDonationInstanceIds.remove(safeUnique);
         this.instanceToItem.put(safeUnique, safeItem);
         this.instanceBindings.put(safeUnique, new CopiMineArtifacts.OfficialInstanceBinding(safeItem, safeOwner));
      }
   }

   private void removeOfficialBinding(String var1) {
      String var2 = this.firstNonBlank(var1, "");
      if (!var2.isBlank()) {
         this.provisionalDonationInstanceIds.remove(var2);
         this.instanceToItem.remove(var2);
         this.instanceBindings.remove(var2);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = false
   )
   public void onShopInteract(PlayerInteractEvent var1) {
      if (var1.getHand() != EquipmentSlot.HAND) {
         return;
      }

      if (var1.getAction() == Action.RIGHT_CLICK_BLOCK) {
         Block var2 = var1.getClickedBlock();
         if (var2 != null) {
            CopiMineArtifacts.Shop var3 = this.shopsByLocation.get(this.blockKey(var2.getLocation()));
            if (var3 != null) {
               var1.setCancelled(true);
               if (!var3.enabled()) {
                  var1.getPlayer()
                     .sendMessage(
                        this.color(
                           "&cМагазин временно недоступен."
                        )
                     );
               } else if (!var1.getPlayer().hasPermission("copimine.artifacts.use")) {
                  var1.getPlayer().sendMessage(this.color("&cНет доступа к CopiMineArtifacts."));
               } else {
                  this.openMain(var1.getPlayer(), var3, true);
               }
            }
      }
      }
   }

   private void restorePendingMagmaBlocks() {
      for (PozdnyakovMagmaRestore restore : this.pozdnyakovMagmaRestores.values()) {
         if (restore == null || restore.location() == null || restore.location().getWorld() == null) {
            continue;
         }
         Block block = restore.location().getBlock();
         if (block.getType() != Material.MAGMA_BLOCK) {
            continue;
         }
         try {
            block.setBlockData(Bukkit.createBlockData(restore.originalBlockData()), false);
         } catch (IllegalArgumentException ignored) {
            block.setType(Material.LAVA, false);
         }
      }
      this.pozdnyakovMagmaRestores.clear();
   }

   private void loadTemporaryTrapJournal() {
      if (temporaryTrapJournalPath == null || !Files.isRegularFile(temporaryTrapJournalPath)) return;
      try {
         for (String line : Files.readAllLines(temporaryTrapJournalPath, StandardCharsets.UTF_8)) {
            String[] parts = line.split("\\t", -1);
            if (parts.length < 7) continue;
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) continue;
            String original = new String(Base64.getDecoder().decode(parts[6]), StandardCharsets.UTF_8);
            String placedBlockData = parts.length >= 8 && !parts[7].isBlank()
                  ? new String(Base64.getDecoder().decode(parts[7]), StandardCharsets.UTF_8)
                  : "";
            TemporaryBlockRestore restore = new TemporaryBlockRestore(
                  new Location(world, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])),
                  Material.valueOf(parts[4]), original, placedBlockData, Long.parseLong(parts[5]));
            temporaryTrapRestores.put(blockKey(restore.location()), restore);
         }
         restorePendingTemporaryTraps();
      } catch (Exception error) {
         this.getLogger().log(Level.WARNING, "Temporary trap journal load failed", error);
      }
   }

   private void recordTemporaryTrap(Block block, Material placed, String originalData, long expiresAtMillis) {
      if (block == null || placed == null || temporaryTrapJournalPath == null) return;
      String placedBlockData = block.getBlockData().getAsString();
      TemporaryBlockRestore restore = new TemporaryBlockRestore(block.getLocation(), placed, originalData, placedBlockData, expiresAtMillis);
      temporaryTrapRestores.put(blockKey(block.getLocation()), restore);
      try {
         Files.createDirectories(temporaryTrapJournalPath.getParent());
         String line = block.getWorld().getName() + "\t" + block.getX() + "\t" + block.getY() + "\t" + block.getZ()
               + "\t" + placed.name() + "\t" + expiresAtMillis + "\t"
               + Base64.getEncoder().encodeToString(originalData.getBytes(StandardCharsets.UTF_8)) + "\t"
               + Base64.getEncoder().encodeToString(placedBlockData.getBytes(StandardCharsets.UTF_8)) + System.lineSeparator();
         try (FileChannel channel = FileChannel.open(temporaryTrapJournalPath,
               StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            writeFully(channel, StandardCharsets.UTF_8.encode(line));
            channel.force(true);
         }
      } catch (IOException error) {
         this.getLogger().log(Level.WARNING, "Temporary trap journal append failed", error);
      }
   }

   private void restorePendingTemporaryTraps() {
      long now = System.currentTimeMillis();
      for (Entry<String, TemporaryBlockRestore> entry : new ArrayList<>(temporaryTrapRestores.entrySet())) {
         TemporaryBlockRestore restore = entry.getValue();
         if (restore == null || restore.location() == null || restore.location().getWorld() == null) continue;
         if (restore.expiresAtMillis() > now) continue;
         restoreTemporaryTrap(restore);
      }
      rewriteTemporaryTrapJournal();
   }

   private void tickTemporaryTrapRestores() {
      long now = System.currentTimeMillis();
      boolean changed = false;
      for (TemporaryBlockRestore restore : new ArrayList<>(temporaryTrapRestores.values())) {
         if (restore != null && restore.expiresAtMillis() <= now) {
            restoreTemporaryTrap(restore);
            changed = true;
         }
      }
      if (changed) rewriteTemporaryTrapJournal();
   }

   private void restoreAllTemporaryTraps() {
      for (TemporaryBlockRestore restore : new ArrayList<>(temporaryTrapRestores.values())) {
         restoreTemporaryTrap(restore);
      }
      temporaryTrapRestores.clear();
      rewriteTemporaryTrapJournal();
   }

   private void restoreTemporaryTrap(TemporaryBlockRestore restore) {
      if (restore == null || restore.location() == null || restore.location().getWorld() == null) return;
      Block block = restore.location().getBlock();
      boolean samePlacement = block.getType() == restore.placedMaterial()
            && (restore.placedBlockData() == null || restore.placedBlockData().isBlank()
                  || restore.placedBlockData().equals(block.getBlockData().getAsString()));
      if (samePlacement) {
         try {
            block.setBlockData(Bukkit.createBlockData(restore.originalBlockData()), false);
         } catch (IllegalArgumentException ignored) {
            block.setType(Material.AIR, false);
         }
      }
      temporaryTrapRestores.remove(blockKey(restore.location()), restore);
   }

   private void rewriteTemporaryTrapJournal() {
      if (temporaryTrapJournalPath == null) return;
      try {
         StringBuilder data = new StringBuilder();
         for (TemporaryBlockRestore restore : temporaryTrapRestores.values()) {
            if (restore.location() == null || restore.location().getWorld() == null) continue;
            Location location = restore.location();
            data.append(location.getWorld().getName()).append('\t').append(location.getBlockX()).append('\t')
                  .append(location.getBlockY()).append('\t').append(location.getBlockZ()).append('\t')
                  .append(restore.placedMaterial().name()).append('\t').append(restore.expiresAtMillis()).append('\t')
                  .append(Base64.getEncoder().encodeToString(restore.originalBlockData().getBytes(StandardCharsets.UTF_8))).append('\t')
                  .append(Base64.getEncoder().encodeToString(firstNonBlank(restore.placedBlockData(), "").getBytes(StandardCharsets.UTF_8))).append(System.lineSeparator());
         }
         if (data.length() == 0) {
            Files.deleteIfExists(temporaryTrapJournalPath);
         } else {
            Files.createDirectories(temporaryTrapJournalPath.getParent());
            Path temporary = Files.createTempFile(temporaryTrapJournalPath.getParent(), "temporary-traps.", ".tmp");
            try {
               try (FileChannel channel = FileChannel.open(temporary,
                     StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                  writeFully(channel, StandardCharsets.UTF_8.encode(data.toString()));
                  channel.force(true);
               }
               try {
                  Files.move(temporary, temporaryTrapJournalPath,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
               } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                  Files.move(temporary, temporaryTrapJournalPath, StandardCopyOption.REPLACE_EXISTING);
               }
            } finally {
               Files.deleteIfExists(temporary);
            }
         }
      } catch (IOException error) {
         this.getLogger().log(Level.WARNING, "Temporary trap journal rewrite failed", error);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = false
   )
   public void onShopVisualInteract(PlayerInteractEntityEvent var1) {
      if (var1.getHand() != EquipmentSlot.HAND) {
         return;
      }
      if (var1.getRightClicked() instanceof ItemDisplay var2) {
         PersistentDataContainer var6 = var2.getPersistentDataContainer();
         if ("PROTECTED_BLOCK_VISUAL".equals(var6.get(this.visualEntityTypeKey, PersistentDataType.STRING))) {
            if ("ARTIFACT_SHOP".equals(this.first((String)var6.get(this.visualKindKey, PersistentDataType.STRING), ""))) {
               String var4 = this.first((String)var6.get(this.visualLinkedIdKey, PersistentDataType.STRING), "");
               if (!var4.isBlank()) {
                  var1.setCancelled(true);
                  CopiMineArtifacts.Shop var5 = this.shopsByLocation
                     .values()
                     .stream()
                     .filter(var1x -> var1x.shopId().equalsIgnoreCase(var4))
                     .findFirst()
                     .orElse(null);
                  if (var5 != null && var5.enabled()) {
                     if (!var1.getPlayer().hasPermission("copimine.artifacts.use")) {
                        var1.getPlayer().sendMessage(this.color("&cНет доступа к CopiMineArtifacts."));
                     } else {
                        this.openMain(var1.getPlayer(), var5, true);
                     }
                  } else {
                     var1.getPlayer()
                        .sendMessage(
                           this.color(
                              "&cЛавка сейчас недоступна."
                           )
                        );
                  }
               }
            }
         }
      }
   }

   @EventHandler
   public void onChunkLoad(ChunkLoadEvent event) {
      this.sweepTerminalDonationEntities(event.getChunk());
      try {
         this.repairShopTitleDisplays(event.getWorld().getName(), event.getChunk().getX(), event.getChunk().getZ());
      } catch (Exception error) {
         this.getLogger().log(Level.WARNING, "Artifact shop title repair failed for chunk load", (Throwable)error);
      }
      if (!customBlockVisualsEnabled()) return;
      try {
         repairProtectedBlockVisuals(event.getWorld().getName(), event.getChunk().getX(), event.getChunk().getZ());
      } catch (Exception error) {
         this.getLogger().log(Level.WARNING, "Artifact shop visual repair failed for chunk load", (Throwable)error);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = false
   )
   public void onShopBreak(BlockBreakEvent var1) {
      String blockKey = this.blockKey(var1.getBlock().getLocation());
      CopiMineArtifacts.Shop var2 = this.shopsByLocation.get(blockKey);
      // The chain marker prevents recursive tool events only after the shop
      // registry has had a chance to veto the protected anchor itself.
      if (var2 != null) {
            var1.setCancelled(true);
            if (!this.isArtifactsAdmin(var1.getPlayer())) {
               var1.getPlayer()
                  .sendMessage(
                     this.color(
                        "&cЛавку CopiMineArtifacts может снять только администратор."
                     )
                  );
            } else {
               this.removeShopWithCleanup(var1.getPlayer(), var2);
            }
      } else if (!this.chainedTreeBreaks.remove(blockKey)) {
            Player var3 = var1.getPlayer();
            CopiMineArtifacts.CatalogItem var4 = this.authenticCatalogItem(var3.getInventory().getItemInMainHand(), var3, "tool_use");
            if (var4 != null) {
               String var5 = var4.effect().toUpperCase(Locale.ROOT);
               if (this.artifactToolEffects().contains(var5)) {
                   long var6 = this.actionCooldowns.getOrDefault(this.actionCooldownKey(var3, var4), 0L);
                   long var8 = this.now();
                   if (var6 <= var8) {
                      boolean activated = false;
                      switch (var5) {
                         case "MINER_PULSE":
                         case "HASTE_BURST":
                            var3.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 100, 1, false, false, true));
                            var3.getWorld().spawnParticle(Particle.ENCHANT, var1.getBlock().getLocation().add(0.5, 0.8, 0.5), 10, 0.35, 0.35, 0.35, 0.01);
                            activated = true;
                            break;
                         case "MINER_3X3":
                            this.breakMinerArea(var3, var1.getBlock(), var3.getInventory().getItemInMainHand());
                            activated = true;
                            break;
                         case "FORESTER_FOCUS":
                            var3.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 80, 0, false, false, true));
                            var3.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 0, false, false, true));
                            var3.getWorld().playSound(var3.getLocation(), Sound.BLOCK_WOOD_BREAK, 0.35F, 1.25F);
                            activated = true;
                            break;
                         case "SURVEYOR_TOUCH":
                            var3.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 0, false, false, true));
                            var3.getWorld().spawnParticle(Particle.DUST, var1.getBlock().getLocation().add(0.5, 0.5, 0.5), 8, 0.25, 0.25, 0.25, 0.01);
                            activated = true;
                            break;
                         case "CRAFTSMAN_CHECK":
                            var3.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 100, 0, false, false, true));
                            var3.getWorld().playSound(var3.getLocation(), Sound.BLOCK_ANVIL_USE, 0.25F, 1.6F);
                            activated = true;
                            break;
                         case "FORESTER_CHAIN":
                            if (!this.rollEffectChance(var4)) {
                               return;
                            }

                            this.tryForesterChain(var3, var1.getBlock(), var3.getInventory().getItemInMainHand());
                            activated = true;
                            break;
                         case "TRENCH_BONUS":
                            if (!this.rollEffectChance(var4)) {
                               return;
                            }

                            this.grantTrenchBonus(var3, var1.getBlock());
                            activated = true;
                      }

                      if (activated && var4.cooldownSeconds() > 0) {
                         this.actionCooldowns.put(this.actionCooldownKey(var3, var4), var8 + (long)var4.cooldownSeconds());
                      }
                   } else {
                      this.sendCooldownMessage(var3, var4, var6, var8);
                   }
               }
            }
         }
      }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = false
   )
   public void onInventoryClick(InventoryClickEvent var1) {
      if (var1.getWhoClicked() instanceof Player var2) {
         if (var1.isCancelled()) {
            return;
         }
         Inventory top = var1.getView().getTopInventory();
         // event.getView().getTopInventory()
         Inventory var10 = top;
         if (var10.getHolder() instanceof CopiMineArtifacts.MenuHolder var4) {
            var1.setCancelled(true);
            int rawSlot = var1.getRawSlot();
            int var11 = rawSlot;
            if (rawSlot < 0 || rawSlot >= top.getSize()) {
               return;
            }

            if (var11 >= 0 && var11 < var10.getSize()) {
               if (!var4.playerUuid.equals(var2.getUniqueId())) {
                  this.debugGui("wrong-player holder=" + var4.playerUuid + " clicker=" + var2.getUniqueId() + " session=" + var4.sessionId);
                  var2.closeInventory();
                  var2.sendMessage(
                     this.color(
                        "&cЭто меню открыто не для вас. Откройте лавку заново."
                     )
                  );
               } else {
                  CopiMineArtifacts.SessionState var6 = this.sessions.get(var2.getUniqueId());
                  if (var6 == null || !var6.sessionId.equals(var4.sessionId)) {
                     this.debugGui(
                        "stale-session player="
                           + var2.getName()
                           + " screen="
                           + var4.viewType
                           + " rawSlot="
                           + var11
                           + " holderSession="
                           + var4.sessionId
                           + " currentSession="
                           + (var6 == null ? "missing" : var6.sessionId)
                     );
                     var2.closeInventory();
                     var2.sendMessage(
                        this.color(
                           "&cМеню устарело. Откройте лавку заново."
                        )
                     );
                  } else if (!var1.getClick().isShiftClick() && var1.getHotbarButton() < 0) {
                     String var7 = var6.actions.getOrDefault(var11, "");
                     this.debugGui(
                        "click player="
                           + var2.getName()
                           + " screen="
                           + var4.viewType
                           + " rawSlot="
                           + var11
                           + " action="
                           + (var7.isBlank() ? "none" : var7)
                           + " shop="
                           + var4.shopId
                           + " category="
                           + var4.category
                           + " item="
                           + var4.itemId
                           + " session="
                           + var4.sessionId
                     );
                     if (!var7.isBlank()) {
                        if (System.currentTimeMillis() - var6.lastClickAt >= 150L || !var7.startsWith("confirm:")) {
                           var6.lastClickAt = System.currentTimeMillis();
                           var6.lastActionAt = System.currentTimeMillis();

                           try {
                              this.handleMenuAction(var2, var6, var7);
                           } catch (Exception var9) {
                              this.getLogger()
                                 .log(
                                    Level.SEVERE,
                                    "Artifact GUI click failed screen=" + var4.viewType + " rawSlot=" + var11 + " action=" + var7,
                                    (Throwable)var9
                                 );
                              this.forwardBugToAdminPlus(
                                 var2,
                                 "artifacts-gui",
                                 var4.viewType + " action=" + var7,
                                 var9,
                                 var1.getCurrentItem(),
                                 var2.getLocation()
                              );
                              var2.sendMessage(
                                 this.color(
                                    "&cНе удалось выполнить действие. Ошибка записана в лог."
                                 )
                              );
                           }
                        }
                     } else if (var4.viewType == CopiMineArtifacts.ViewType.MAIN && var11 == 16) {
                        this.openDonationRoot(var2);
                     } else {
                        this.debugGui("no-action player=" + var2.getName() + " screen=" + var4.viewType + " rawSlot=" + var11 + " session=" + var4.sessionId);
                     }
                  }
               }
            }
          }
      }
   }

   /**
    * Removes a donation instance owned by somebody else before the inventory
    * click can move it.  The journal is written first; if it cannot be
    * persisted the click is cancelled and the physical item is left intact.
    */
   private boolean quarantineForeignDonationClick(InventoryClickEvent event, Player actor) {
      if (event == null || actor == null) {
         return false;
      }
      boolean handled = false;
      ItemStack cursor = event.getCursor();
      OfficialDonationRef cursorRef = this.foreignDonationRef(cursor, actor.getUniqueId());
      if (cursorRef != null) {
         event.setCancelled(true);
         this.quarantineForeignDonation(actor, cursorRef, "foreign-cursor-click");
         handled = true;
      }

      ItemStack current = event.getCurrentItem();
      OfficialDonationRef currentRef = this.foreignDonationRef(current, actor.getUniqueId());
      if (currentRef != null) {
         event.setCancelled(true);
         this.quarantineForeignDonation(actor, currentRef, "foreign-inventory-click");
         handled = true;
      }

      // NUMBER_KEY/HOTBAR_SWAP and SWAP_OFFHAND move an item that is not
      // exposed as the event's current item.  Inspect those source slots too;
      // otherwise a foreign copy could bypass the normal click path straight
      // into a chest, shulker or other block inventory.
      ItemStack alternateSource = null;
      String alternateReason = "foreign-hotbar-swap";
      if ((event.getClick() == ClickType.NUMBER_KEY
               || event.getAction() == InventoryAction.HOTBAR_SWAP
               || event.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD)
            && event.getHotbarButton() >= 0) {
         alternateSource = actor.getInventory().getItem(event.getHotbarButton());
      } else if (event.getClick() == ClickType.SWAP_OFFHAND) {
         alternateSource = actor.getInventory().getItemInOffHand();
         alternateReason = "foreign-offhand-swap";
      }
      OfficialDonationRef alternateRef = this.foreignDonationRef(alternateSource, actor.getUniqueId());
      if (alternateRef != null) {
         event.setCancelled(true);
         this.quarantineForeignDonation(actor, alternateRef, alternateReason);
         handled = true;
      }

      // DOUBLE_CLICK/COLLECT_TO_CURSOR can pull a matching stack from any
      // player slot without exposing that source stack through
      // getCurrentItem(). Inspect the complete player inventory before the
      // client can collect a foreign owner-bound item into its cursor (and
      // potentially drop it into a block inventory on the next click).
      if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR || event.getClick() == ClickType.DOUBLE_CLICK) {
         List<ItemStack> playerStacks = new ArrayList<>();
         playerStacks.addAll(Arrays.asList(actor.getInventory().getContents()));
         playerStacks.addAll(Arrays.asList(actor.getInventory().getArmorContents()));
         playerStacks.add(actor.getInventory().getItemInOffHand());
         for (ItemStack stack : playerStacks) {
            OfficialDonationRef collectedRef = this.foreignDonationRef(stack, actor.getUniqueId());
            if (collectedRef != null) {
               event.setCancelled(true);
               this.quarantineForeignDonation(actor, collectedRef, "foreign-collect-to-cursor");
               handled = true;
               break;
            }
         }
      }

      // A just-issued item remains provisional until the DB transition is
      // committed.  Do not let a number-key/hotbar move or a regular click
      // place that item into a block inventory while finalization is pending;
      // otherwise recovery cannot prove where the physical instance lives.
      String provisionalId = this.provisionalUniqueId(event, actor);
      Inventory topForProvisional = event.getView() == null ? null : event.getView().getTopInventory();
      if (!provisionalId.isBlank() && topForProvisional != null
            && !(topForProvisional.getHolder() instanceof MenuHolder)
            && !topForProvisional.equals(event.getView().getBottomInventory())) {
         event.setCancelled(true);
         actor.updateInventory();
         handled = true;
      }

      // A container may already contain a foreign copy from an older plugin
      // or a restart.  Scrub only foreign instances; the owner may still keep
      // their own donation item in a normal chest.
      Inventory top = event.getView() == null ? null : event.getView().getTopInventory();
      if (top != null && !(top.getHolder() instanceof MenuHolder)) {
         for (int slot = 0; slot < top.getSize(); slot++) {
            ItemStack stack = top.getItem(slot);
            OfficialDonationRef ref = this.foreignDonationRef(stack, actor.getUniqueId());
            if (ref != null && this.quarantineForeignDonation(actor, ref, "foreign-container-scrub")) {
               event.setCancelled(true);
               handled = true;
            }
         }
      }
      return handled;
   }

   private String provisionalUniqueId(InventoryClickEvent event, Player actor) {
      if (event == null || actor == null) return "";
      List<ItemStack> candidates = new ArrayList<>();
      candidates.add(event.getCursor());
      candidates.add(event.getCurrentItem());
      if (event.getHotbarButton() >= 0 && event.getHotbarButton() < 9) {
         candidates.add(actor.getInventory().getItem(event.getHotbarButton()));
      }
      if (event.getClick() == ClickType.SWAP_OFFHAND) {
         candidates.add(actor.getInventory().getItemInOffHand());
      }
      for (ItemStack candidate : candidates) {
         String id = this.uniqueItemIdOf(candidate);
         if (!id.isBlank() && this.provisionalDonationInstanceIds.contains(id)) return id;
      }
      return "";
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = false
   )
   public void onInventoryDrag(InventoryDragEvent var1) {
      if (var1 != null && var1.getView() != null
            && var1.getView().getTopInventory().getHolder() instanceof CopiMineArtifacts.MenuHolder) {
         var1.setCancelled(true);
         return;
      }
      // Catalog items use the ordinary Bukkit drag/split rules everywhere.
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = false
   )
   public void onInventoryMoveItem(InventoryMoveItemEvent var1) {
      // Shop items intentionally follow vanilla hopper/container movement.
   }

   private boolean isAnvilArtifactInteraction(InventoryClickEvent event) {
      if (event == null || event.getView() == null
            || event.getView().getTopInventory().getType() != InventoryType.ANVIL) {
         return false;
      }
      Player player = event.getWhoClicked() instanceof Player p ? p : null;
      ItemStack hotbar = player != null && event.getHotbarButton() >= 0 && event.getHotbarButton() < 9
            ? player.getInventory().getItem(event.getHotbarButton()) : null;
      ItemStack offhand = player == null ? null : player.getInventory().getItemInOffHand();
      return this.isOfficialArtifactItem(event.getCursor())
            || this.isOfficialArtifactItem(event.getCurrentItem())
            || this.isOfficialArtifactItem(hotbar)
            || this.isOfficialArtifactItem(offhand)
            || this.isOfficialDonationItem(event.getCursor())
            || this.isOfficialDonationItem(event.getCurrentItem())
            || this.isOfficialDonationItem(hotbar)
            || this.isOfficialDonationItem(offhand)
            || this.containsOfficialArtifactOrDonation(event.getView().getTopInventory());
   }

   private boolean isAnvilArtifactDrag(InventoryDragEvent event) {
      if (event == null || event.getView() == null
            || event.getView().getTopInventory().getType() != InventoryType.ANVIL) {
         return false;
      }
      return this.isOfficialArtifactItem(event.getOldCursor())
            || this.isOfficialDonationItem(event.getOldCursor())
            || this.containsOfficialArtifactOrDonation(event.getView().getTopInventory());
   }

   private boolean containsOfficialArtifactOrDonation(Inventory inventory) {
      if (inventory == null) {
         return false;
      }
      for (ItemStack stack : inventory.getStorageContents()) {
         if (this.isOfficialArtifactItem(stack) || this.isOfficialDonationItem(stack)) {
            return true;
         }
      }
      return false;
   }

   /** Remove stale terminal donation entities as soon as an unloaded chunk is loaded. */
   private void sweepTerminalDonationEntities(Chunk chunk) {
      if (chunk == null || this.terminalDonationInstanceIds.isEmpty()) {
         return;
      }
      for (Entity entity : chunk.getEntities()) {
         if (!(entity instanceof Item item)) {
            continue;
         }
         OfficialDonationRef ref = this.rawDonationIdentity(item.getItemStack());
         if (ref != null && this.terminalDonationInstanceIds.contains(ref.uniqueItemId())) {
            item.remove();
         }
      }
   }

   /**
    * Hopper-like inventories can pick an item entity directly without first
    * firing InventoryMoveItemEvent.  Keep official donation items as physical
    * entities so a container/plugin cannot silently own or discard the only
    * copy before the loss journal sees it.
    */
   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = false
   )
   public void onInventoryPickupItem(InventoryPickupItemEvent event) {
      // Shop items intentionally follow vanilla pickup behavior.
   }

   /**
    * A placeable donation material must not be usable as a world/block
    * storage bypass by somebody who picked it up from another player.  The
    * owner is left untouched; foreign copies are journaled and removed by
    * unique id before the cancelled placement can consume them.
    */
   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onForeignDonationPlace(BlockPlaceEvent event) {
      if (this.customShopItemsAreVanilla()) {
         return;
      }
      if (event == null || event.getPlayer() == null) {
         return;
      }
      if (this.isProvisionalDonationItem(event.getItemInHand())) {
         event.setCancelled(true);
         event.getPlayer().updateInventory();
         return;
      }
      OfficialDonationRef ref = this.foreignDonationRef(event.getItemInHand(), event.getPlayer().getUniqueId());
      if (ref == null) {
         return;
      }
      event.setCancelled(true);
      if (this.quarantineForeignDonation(event.getPlayer(), ref, "foreign-block-place")) {
         event.getPlayer().sendMessage(this.color("&eЧужой донат-предмет нельзя размещать в мире; он возвращён владельцу через меню возврата."));
      }
   }

   /**
    * Pickup is cancellable before the item is inserted into the player's
    * inventory.  A player who is not the PDC/DB owner therefore cannot ever
    * obtain a usable copy after somebody's death.
    */
   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onForeignDonationPickup(EntityPickupItemEvent event) {
      if (this.customShopItemsAreVanilla()) {
         return;
      }
      if (event == null || !(event.getEntity() instanceof Player player) || event.getItem() == null) {
         return;
      }
      if (this.isProvisionalDonationItem(event.getItem().getItemStack())) {
         event.setCancelled(true);
         return;
      }
      OfficialDonationRef terminal = this.rawDonationIdentity(event.getItem().getItemStack());
      if (terminal != null && this.terminalDonationInstanceIds.contains(terminal.uniqueItemId())) {
         // A stale entity from an unloaded chunk must not be picked up after
         // its DB row was already marked LOST/CONSUMED/REPLACED.  The owner
         // can only receive the replacement through the reclaim workflow.
         event.setCancelled(true);
         Bukkit.getScheduler().runTask(this, () -> {
            if (event.getItem() != null && !event.getItem().isDead()) {
               event.getItem().remove();
            }
         });
         return;
      }
      OfficialDonationRef ref = this.foreignDonationRef(event.getItem().getItemStack(), player.getUniqueId());
      if (ref == null) {
         return;
      }
      event.setCancelled(true);
      this.quarantineForeignDonation(player, ref, "foreign-pickup");
   }

   /**
    * The event fires before a drop becomes an Item entity.  Cancel and
    * quarantine a foreign instance instead of allowing it to be picked up by
    * a second player or duplicated through a drop/pickup race.
    */
   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onForeignDonationDrop(PlayerDropItemEvent event) {
      if (this.customShopItemsAreVanilla()) {
         return;
      }
      if (event == null || event.getPlayer() == null || event.getItemDrop() == null) {
         return;
      }
      if (this.isRepairLockedItem(event.getPlayer(), event.getItemDrop().getItemStack())) {
         event.setCancelled(true);
         return;
      }
      if (this.isProvisionalDonationItem(event.getItemDrop().getItemStack())) {
         event.setCancelled(true);
         event.getPlayer().updateInventory();
         return;
      }
      OfficialDonationRef ref = this.foreignDonationRef(event.getItemDrop().getItemStack(), event.getPlayer().getUniqueId());
      if (ref == null) {
         return;
      }
      event.setCancelled(true);
      this.quarantineForeignDonation(event.getPlayer(), ref, "foreign-drop");
   }

   /**
    * InventoryOpenEvent catches foreign copies left in a chest before a click
    * (or a hopper) is possible.  The owner is deliberately excluded so their
    * own item remains usable in ordinary storage.
    */
   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onDonationInventoryOpen(InventoryOpenEvent event) {
      if (this.customShopItemsAreVanilla()) {
         return;
      }
      if (event == null || !(event.getPlayer() instanceof Player player) || event.getInventory() == null
            || event.getInventory().getHolder() instanceof MenuHolder) {
         return;
      }
      Inventory inventory = event.getInventory();
      for (int slot = 0; slot < inventory.getSize(); slot++) {
         ItemStack stack = inventory.getItem(slot);
         OfficialDonationRef ref = this.foreignDonationRef(stack, player.getUniqueId());
         if (ref != null && this.quarantineForeignDonation(player, ref, "foreign-container-open")) {
            // The bounded loss writer removes the stale stack after fsync;
            // do not clear the container before that durability boundary.
         }
      }
   }

   /** Prevent an already-present official item from being consumed by a
    * furnace, smoker, blast furnace, or campfire after an older plugin/client
    * path inserted it before the inventory guard ran. */
   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onOfficialBlockCook(BlockCookEvent event) {
      // Catalog items use vanilla furnace/smoker/campfire behavior.
   }

   /** Brewing consumes its ingredient slots without an item-entity event. */
   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onOfficialBrew(BrewEvent event) {
      // Catalog items use vanilla brewing behavior.
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onPlayerItemMend(PlayerItemMendEvent var1) {
      // Catalog items use vanilla mending behavior.
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onPrepareItemCraft(PrepareItemCraftEvent var1) {
      // Catalog items use vanilla crafting behavior.
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onPrepareAnvil(PrepareAnvilEvent var1) {
      if (this.hasOfficialArtifactIngredient(var1.getInventory().getStorageContents())) {
         var1.setResult(null);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onPrepareSmithing(PrepareSmithingEvent var1) {
      // Catalog items use vanilla smithing behavior.
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onPrepareGrindstone(PrepareGrindstoneEvent var1) {
      // Catalog items use vanilla grindstone behavior.
   }

   @EventHandler
   public void onInventoryClose(InventoryCloseEvent var1) {
      if (var1.getPlayer() instanceof Player var2) {
         if (var1.getInventory().getHolder() instanceof CopiMineArtifacts.MenuHolder var5) {
            Bukkit.getScheduler()
               .runTaskLater(
                  this,
                  () -> {
                     if (var2.isOnline()) {
                        Inventory var3 = var2.getOpenInventory().getTopInventory();
                        if (var3.getHolder() instanceof CopiMineArtifacts.MenuHolder var6) {
                           this.debugGui(
                              "close-transition player=" + var2.getName() + " from=" + var5.viewType + " to=" + var6.viewType + " session=" + var6.sessionId
                           );
                        } else {
                           CopiMineArtifacts.SessionState var4 = this.sessions.get(var2.getUniqueId());
                           if (var4 != null && var4.purchaseInFlightId.isBlank() && var4.sessionId.equals(var5.sessionId)) {
                              this.sessions.remove(var2.getUniqueId());
                              this.debugGui("session-cleanup player=" + var2.getName() + " screen=" + var5.viewType + " session=" + var5.sessionId);
                           }
                        }
                     }
                  },
                  2L
               );
         }
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent var1) {
      UUID playerUuid = var1.getPlayer().getUniqueId();
      this.sessions.remove(playerUuid);
      this.pozdnyakovNauseaCooldowns.remove(playerUuid);
      String prefix = playerUuid + ":";
      this.actionCooldowns.keySet().removeIf(key -> key.startsWith(prefix));
   }

   private void tickPozdnyakovAce() {
      long nowMillis = System.currentTimeMillis();
      this.tickTemporaryTrapRestores();
      for (Entry<String, PozdnyakovMagmaRestore> entry : this.pozdnyakovMagmaRestores.entrySet()) {
         PozdnyakovMagmaRestore restore = entry.getValue();
         if (restore.expiresAtMillis() > nowMillis) {
            continue;
         }
         Block block = restore.location().getBlock();
         if (block.getType() == Material.MAGMA_BLOCK) {
            try {
               block.setBlockData(Bukkit.createBlockData(restore.originalBlockData()), false);
            } catch (IllegalArgumentException ignored) {
               block.setType(Material.LAVA, false);
            }
         }
         this.pozdnyakovMagmaRestores.remove(entry.getKey(), restore);
      }

      for (Player player : Bukkit.getOnlinePlayers()) {
         CopiMineArtifacts.CatalogItem item = this.authenticCatalogItem(player.getInventory().getLeggings(), player, "pozdnyakov_ace_lava");
         if (item == null || !"POZDNYAKOV_ACE".equalsIgnoreCase(item.effect())) {
            continue;
         }

         // The player's block is the air block occupied by their feet.  Lava
         // must be converted in the block directly below the player, not one
         // block above/inside the player model.
         Block center = player.getLocation().getBlock().getRelative(0, -1, 0);
         for (int dx = -POZDNYAKOV_LAVA_RADIUS; dx <= POZDNYAKOV_LAVA_RADIUS; dx++) {
            for (int dz = -POZDNYAKOV_LAVA_RADIUS; dz <= POZDNYAKOV_LAVA_RADIUS; dz++) {
               Block block = center.getRelative(dx, 0, dz);
               Material originalType = block.getType();
               if (originalType == Material.LAVA) {
                  String originalBlockData = block.getBlockData().getAsString();
                  block.setType(Material.MAGMA_BLOCK, false);
                  this.recordTemporaryTrap(block, Material.MAGMA_BLOCK, originalBlockData,
                        nowMillis + (POZDNYAKOV_LAVA_RESTORE_TICKS * 50L));
                  this.pozdnyakovMagmaRestores.put(
                     this.blockKey(block.getLocation()),
                     new PozdnyakovMagmaRestore(block.getLocation(), originalBlockData, nowMillis + (POZDNYAKOV_LAVA_RESTORE_TICKS * 50L))
                  );
               }
            }
         }
      }
   }

   /** Refreshes passive armor effects without waiting for a damage event. */
   private void tickEquippedArmor() {
      for (Player player : Bukkit.getOnlinePlayers()) {
         ItemStack queuedTotem = this.pendingInfiniteTotemRestores.get(player.getUniqueId());
         if (queuedTotem != null) {
            this.restoreInfiniteTotem(player, queuedTotem);
         }
      }
   }

   /** Restores the same official AR totem after the vanilla resurrection event. */
   private void restoreInfiniteTotem(Player player, ItemStack snapshot) {
      if (player == null || !player.isOnline() || snapshot == null || !snapshot.hasItemMeta()) {
         return;
      }
      String uniqueId = snapshot.getItemMeta().getPersistentDataContainer().get(this.keyUniqueItemId, PersistentDataType.STRING);
      if (uniqueId == null || uniqueId.isBlank()) {
         return;
      }
      ItemStack queued = this.pendingInfiniteTotemRestores.get(player.getUniqueId());
      if (queued != null && queued.hasItemMeta()) {
         snapshot = queued;
      }
      PlayerInventory inventory = player.getInventory();
      ItemStack[] hands = {inventory.getItemInMainHand(), inventory.getItemInOffHand()};
      for (int index = 0; index < hands.length; index++) {
         ItemStack current = hands[index];
         if (current == null || !current.hasItemMeta()) {
            continue;
         }
         String currentId = current.getItemMeta().getPersistentDataContainer().get(this.keyUniqueItemId, PersistentDataType.STRING);
         if (!uniqueId.equals(currentId)) {
            continue;
         }
         if (current.getAmount() < snapshot.getAmount()) {
            current.setAmount(current.getAmount() + 1);
            if (index == 0) {
               inventory.setItemInMainHand(current);
            } else {
               inventory.setItemInOffHand(current);
            }
         }
         if (queued != null) {
            this.pendingInfiniteTotemRestores.remove(player.getUniqueId());
            this.savePendingInfiniteTotemRestores();
         }
         return;
      }
      // The resurrection callback runs on the main thread, but the inventory
      // can be full (or another plugin can have inserted a stack between the
      // vanilla consume and this callback).  Use the same idempotent queue as
      // every other official entitlement instead of dropping leftovers.
      Map<Integer, ItemStack> leftovers = inventory.addItem(snapshot.clone());
      if (!leftovers.isEmpty()) {
         ItemStack remainder = leftovers.values().stream().filter(Objects::nonNull).findFirst().orElse(snapshot);
         ItemStack previous = this.pendingInfiniteTotemRestores.put(player.getUniqueId(), remainder.clone());
         if (previous == null || !previous.isSimilar(remainder) || previous.getAmount() != remainder.getAmount()) {
            this.savePendingInfiniteTotemRestores();
         }
         player.sendMessage(this.color("&eИнвентарь заполнен: бесконечный тотем будет возвращён после освобождения слота."));
      } else if (queued != null) {
         this.pendingInfiniteTotemRestores.remove(player.getUniqueId());
         this.savePendingInfiniteTotemRestores();
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onEntityResurrect(EntityResurrectEvent event) {
      if (!(event.getEntity() instanceof Player player)) {
         return;
      }
      ItemStack[] hands = {player.getInventory().getItemInMainHand(), player.getInventory().getItemInOffHand()};
      for (ItemStack hand : hands) {
         CatalogItem totem = this.authenticCatalogItem(hand, player, "resurrect_infinite_totem");
         if (totem != null && hand.getType() == Material.TOTEM_OF_UNDYING && "INFINITE_TOTEM".equalsIgnoreCase(totem.effect())) {
            ItemStack snapshot = hand.clone();
            Bukkit.getScheduler().runTask(this, () -> this.restoreInfiniteTotem(player, snapshot));
            return;
         }
      }
   }

   private LivingEntity resolveDamageAttacker(EntityDamageByEntityEvent event) {
      if (event == null) {
         return null;
      }
      if (event.getDamager() instanceof LivingEntity living) {
         return living;
      }
      if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity living) {
         return living;
      }
      return null;
   }

   @EventHandler
   public void onDeath(PlayerDeathEvent var1) {
      CopiMineArtifacts.SessionState var2 = this.sessions.get(var1.getEntity().getUniqueId());
      if (var2 != null && var2.purchaseInFlightId.isBlank()) {
         var2.actions.clear();
         var2.pinBuffer = "";
      }

      if (this.customShopItemsAreVanilla()) {
         return;
      }

      if (!var1.getKeepInventory()) {
         LinkedHashMap var3 = new LinkedHashMap();

         for (ItemStack var5 : var1.getDrops()) {
            CopiMineArtifacts.OfficialDonationRef var6 = this.officialDonationRef(var5);
            if (var6 != null && var1.getEntity().getUniqueId().equals(var6.ownerUuid())) {
               var3.put(var6.uniqueItemId(), var6.itemId());
            }
         }

         if (!var3.isEmpty()) {
            // Mark every instance before submitting the durable append so a
            // follow-up EntityRemoveEvent cannot enqueue a second journal
            // entry while the death drops are waiting for the writer.
            for (Object key : var3.keySet()) {
               this.lossJournalInFlight.add(var1.getEntity().getUniqueId() + ":" + key);
            }
            if (!this.recordDonationLossJournal(var1.getEntity().getUniqueId(), var3, "death")) {
               for (Object key : var3.keySet()) {
                  this.lossJournalInFlight.remove(var1.getEntity().getUniqueId() + ":" + key);
               }
               this.getLogger().warning("Donation loss journal is unavailable during death handling; keeping donation items in drops.");
               return;
            }
            // The bounded writer removes the matching drop/entity only after
            // FileChannel.force(true) succeeds.  Keeping the drop in the
            // event result during the short write window is deliberate: if
            // the queue is full or the disk fails, the physical item stays
            // available instead of becoming an unrecoverable loss.
            this.flushPendingDonationLossJournalAsync();
            var1.getEntity()
               .sendMessage(
                  this.color(
                     "&eДонат-предметы не выпали на землю. Их можно вернуть позже через кнопку &f«Вернуть утерянные предметы»&e в лавке."
                  )
               );
         }
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onPlayerItemBreak(PlayerItemBreakEvent var1) {
      CopiMineArtifacts.OfficialDonationRef var2 = this.officialDonationRef(var1.getBrokenItem());
      if (var2 != null && var1.getPlayer().getUniqueId().equals(var2.ownerUuid())) {
         // This event is post-destruction on Paper.  Durability breaks are
         // terminal, not reclaimable losses: never mint a replacement for an
         // item that was intentionally exhausted.
         this.markDonationInstanceBroken(var2, "durability-break-after-event");
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onDonationItemDespawn(ItemDespawnEvent var1) {
      if (this.customShopItemsAreVanilla()) {
         return;
      }
      if (this.isProvisionalDonationItem(var1 == null || var1.getEntity() == null ? null : var1.getEntity().getItemStack())) {
         // A provisional delivery is represented by a durable DELIVERING
         // row. Keep its entity alive until recovery can prove the final
         // state; vanilla despawn must not create an untracked loss path.
         var1.setCancelled(true);
         return;
      }
      CopiMineArtifacts.OfficialDonationRef var2 = this.officialDonationRef(var1.getEntity().getItemStack());
      if (var2 != null) {
         // Keep the entity alive until the bounded durable writer confirms
         // the append.  A failed queue/disk write must leave an item that can
         // be retried rather than silently losing it to despawn.
         var1.setCancelled(true);
         if (!this.recordDonationLossOnce(var2, "despawn")) {
            return;
         }
         this.flushPendingDonationLossJournalAsync();
      }
   }

   /**
    * A dropped item entering a cactus is discarded by the block-contact path
    * before older Paper/Purpur builds reliably emit an EntityDamageEvent for
    * the Item entity.  Journal the loss at the block boundary, before vanilla
    * can discard the physical entity, so the reclaim menu has a durable row.
    */
   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = false
   )
   public void onDonationItemInsideBlock(EntityInsideBlockEvent event) {
      if (this.customShopItemsAreVanilla()) {
         return;
      }
      if (!(event.getEntity() instanceof Item item) || event.getBlock() == null
            || event.getBlock().getType() != Material.CACTUS) {
         return;
      }
      if (this.isProvisionalDonationItem(item.getItemStack())) {
         event.setCancelled(true);
         return;
      }
      OfficialDonationRef ref = this.officialDonationRef(item.getItemStack());
      if (ref == null) {
         return;
      }
      event.setCancelled(true);
      if (!this.recordDonationLossOnce(ref, "cactus")) {
         return;
      }
      this.flushPendingDonationLossJournalAsync();
   }

   /**
    * Covers silent removal by another plugin (creative cleanup, clear-lag,
    * discard, or a direct entity removal).  Those paths do not expose a
    * cancellable damage event, so we journal the loss before the next
    * reconciliation pass can mark the instance LOST_RECLAIMABLE.
    */
   public void onDonationItemRemoved(EntityRemoveEvent event) {
      if (this.customShopItemsAreVanilla()) {
         return;
      }
      if (!(event.getEntity() instanceof Item item)) {
         return;
      }
      EntityRemoveEvent.Cause cause = event.getCause();
      if (cause == null) {
         // Paper currently guarantees a cause, but a malformed/custom event
         // must fail closed rather than crash the listener before journaling.
         cause = EntityRemoveEvent.Cause.PLUGIN;
      }
      // Pickup, chunk/world unload and player quit preserve the physical
      // item. Every other removal cause means the entity copy disappeared;
      // keep the unique identity recoverable, including transformation and
      // MERGE/DEATH/DROP/HIT paths that cleanup plugins or Paper updates expose.
      boolean preservesPhysicalItem = switch (cause) {
         case PICKUP, PLAYER_QUIT, UNLOAD -> true;
         case DEATH, DESPAWN, DROP, ENTER_BLOCK, EXPLODE, HIT, MERGE, OUT_OF_WORLD, PLUGIN, DISCARD, TRANSFORMATION -> false;
         default -> false;
      };
      if (preservesPhysicalItem) {
         return;
      }
      OfficialDonationRef ref = this.officialDonationRef(item.getItemStack());
      if (ref != null && this.terminalDonationInstanceIds.contains(ref.uniqueItemId())) {
         // Terminal rows are already accounted for in the database; removing
         // their stale physical entity must never append a second loss row.
         return;
      }
      if (ref != null && this.recordDonationLossOnce(ref, "entity-" + cause.name().toLowerCase(Locale.ROOT))) {
         this.flushPendingDonationLossJournalAsync();
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onDonationItemDestroyed(EntityDamageEvent var1) {
       if (this.customShopItemsAreVanilla()) {
          return;
       }
       // Official donation items are intentionally indestructible.  Handle
       // the damage at HIGHEST so vanilla cactus/explosion/void processing
       // cannot remove the entity before the durable loss journal is written.
       // Other protection listeners should leave already-cancelled damage
       // alone; this handler only owns a real, uncancelled damage event.
      if (var1.getEntity() instanceof Item var2) {
         if (this.isProvisionalDonationItem(var2.getItemStack())) {
            var1.setCancelled(true);
            return;
         }
         CopiMineArtifacts.OfficialDonationRef var4 = this.officialDonationRef(var2.getItemStack());
         if (var4 != null && this.isPotentiallyDestructiveItemDamage(var1.getCause())) {
            // Treat every damage cause on a dropped official item as a loss.
            // This includes /kill (KILL), world-border/falling-block cases,
            // fire/lava/cactus, all explosion variants and future Paper causes
            // through the default branch. Never let one omitted enum value
            // bypass the reclaim journal.
            var1.setCancelled(true);
            DamageCause damageCause = var1.getCause();
            String reason = damageCause == DamageCause.VOID
               ? "void"
               : damageCause == null ? "unknown-damage" : damageCause.name().toLowerCase(Locale.ROOT);
          if (!this.recordDonationLossOnce(var4, reason)) {
             return;
          }
           // The durable writer performs the equivalent of var2.remove();
           // only after FileChannel.force(true), never before the journal.
           this.flushPendingDonationLossJournalAsync();
          }
       }
   }

   private boolean isPotentiallyDestructiveItemDamage(DamageCause cause) {
      if (cause == null) {
         return true;
      }
      return switch (cause) {
         case KILL, WORLD_BORDER, CONTACT, ENTITY_ATTACK, ENTITY_SWEEP_ATTACK, PROJECTILE,
            SUFFOCATION, FALL, FIRE, FIRE_TICK, MELTING, LAVA, DROWNING, BLOCK_EXPLOSION,
            ENTITY_EXPLOSION, VOID, LIGHTNING, SUICIDE, STARVATION, POISON, MAGIC, WITHER,
            FALLING_BLOCK, THORNS, DRAGON_BREATH, CUSTOM, FLY_INTO_WALL, HOT_FLOOR,
            CAMPFIRE, CRAMMING, DRYOUT, FREEZE, SONIC_BOOM -> true;
         default -> true;
      };
   }

   private boolean recordDonationLossOnce(OfficialDonationRef ref, String reason) {
      if (ref == null || ref.uniqueItemId().isBlank()) {
         return false;
      }
      String key = ref.ownerUuid() + ":" + ref.uniqueItemId();
      if (!this.lossJournalInFlight.add(key)) {
         // Another event is currently writing the durable loss record.  Do
         // not report success to the caller: doing so would let a concurrent
         // pickup/drop handler remove the physical copy before the first
         // writer has reached the fsync boundary.  The first handler will
         // reconcile the row after it is durable; this retry remains safe.
         return false;
      }
      boolean recorded = this.recordDonationLossJournal(ref.ownerUuid(), Map.of(ref.uniqueItemId(), ref.itemId()), reason);
      if (!recorded) {
         this.lossJournalInFlight.remove(key);
      }
      return recorded;
   }

   /** Mark a durability break as terminal and remove every duplicate copy
    * only after the database transition has committed. */
   private void markDonationInstanceBroken(OfficialDonationRef ref, String reason) {
      if (ref == null || ref.uniqueItemId().isBlank()) {
         return;
      }
      this.runAsync(() -> {
         try {
            boolean changed = this.updateDonationInstanceStatus(
                  ref.ownerUuid(), ref.uniqueItemId(), ref.itemId(), "BROKEN",
                  Set.of("ACTIVE", "DELIVERED", "DELIVERING", "PENDING_DELIVERY"), true);
            if (!changed) {
               return;
            }
            this.audit(ref.ownerUuid().toString(), "donation_item_broken", ref.uniqueItemId(),
                  ref.itemId() + " reason=" + this.firstNonBlank(reason, "durability-break"));
            this.runSync(() -> {
               this.removeDonationInstanceFromOnlineInventories(ref.uniqueItemId());
               for (Player player : Bukkit.getOnlinePlayers()) {
                  player.updateInventory();
               }
            });
         } catch (Exception error) {
            // The event was cancelled before this task ran, so a transient
            // database outage leaves the original item in place rather than
            // creating an untracked replacement.
            this.getLogger().log(Level.WARNING, "Donation durability break could not be finalized: " + ref.uniqueItemId(), error);
         }
      });
   }

   private boolean preserveDonationItemFromVoid(Item item, UUID ownerUuid) {
      if (item == null || item.getWorld() == null) {
         return false;
      }
      Player owner = ownerUuid == null ? null : Bukkit.getPlayer(ownerUuid);
      Location destination;
      if (owner != null && owner.isOnline() && owner.getWorld() != null) {
         destination = owner.getLocation().clone().add(0.0D, 1.0D, 0.0D);
      } else {
         World world = item.getWorld();
         Location spawn = world.getSpawnLocation();
         int surfaceY = world.getHighestBlockYAt(spawn.getBlockX(), spawn.getBlockZ());
         destination = new Location(world, spawn.getBlockX() + 0.5D, surfaceY + 1.0D, spawn.getBlockZ() + 0.5D);
      }
      if (destination.getWorld() == null || !item.teleport(destination)) {
         return false;
      }
      item.setVelocity(new Vector());
      item.setFireTicks(0);
      item.setPickupDelay(10);
      item.setTicksLived(0);
      return true;
   }

   private void cleanupExpiredSessions() {
      long var1 = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(900L);
      this.sessions.entrySet().removeIf(var2 -> {
         CopiMineArtifacts.SessionState var3 = var2.getValue();
         return var3 != null && var3.purchaseInFlightId.isBlank() && var3.lastActionAt > 0L && var3.lastActionAt < var1;
      });
      long nowSeconds = this.now();
      this.actionCooldowns.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() <= nowSeconds);
      this.pozdnyakovNauseaCooldowns.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() <= nowSeconds);
      this.lastDeathLocations.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
   }

   private void normalizeVanillaShieldItems(Player player) {
      if (player == null || player.getInventory() == null) {
         return;
      }
      boolean changed = false;
      PlayerInventory inventory = player.getInventory();
      for (int slot = 0; slot < inventory.getContents().length; slot++) {
         ItemStack stack = inventory.getItem(slot);
         if (removeShieldCustomModelData(stack)) {
            inventory.setItem(slot, stack);
            changed = true;
         }
      }
      ItemStack[] armor = inventory.getArmorContents();
      for (int slot = 0; slot < armor.length; slot++) {
         if (removeShieldCustomModelData(armor[slot])) {
            changed = true;
         }
      }
      inventory.setArmorContents(armor);
      ItemStack offhand = inventory.getItemInOffHand();
      if (removeShieldCustomModelData(offhand)) {
         inventory.setItemInOffHand(offhand);
         changed = true;
      }
      for (int slot = 0; slot < player.getEnderChest().getSize(); slot++) {
         ItemStack stack = player.getEnderChest().getItem(slot);
         if (removeShieldCustomModelData(stack)) {
            player.getEnderChest().setItem(slot, stack);
         }
      }
      if (changed) {
         player.updateInventory();
      }
   }

   private boolean removeShieldCustomModelData(ItemStack stack) {
      if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
         return false;
      }
      ItemMeta meta = stack.getItemMeta();
      String itemId = meta == null ? "" : this.firstNonBlank(
            meta.getPersistentDataContainer().get(this.keyItemId, PersistentDataType.STRING), "");
      CatalogItem catalog = itemId.isBlank() ? null : this.runtimeCatalogItem(itemId);
      if (catalog == null || !"NOT_TODAY_SHIELD".equalsIgnoreCase(catalog.effect()) || !meta.hasCustomModelData()) {
         return false;
      }
      meta.setCustomModelData(null);
      stack.setItemMeta(meta);
      return true;
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent var1) {
      this.normalizeVanillaShieldItems(var1.getPlayer());
      ItemStack queuedTotem = this.pendingInfiniteTotemRestores.get(var1.getPlayer().getUniqueId());
      if (queuedTotem != null) {
         Bukkit.getScheduler().runTaskLater(this, () -> this.restoreInfiniteTotem(var1.getPlayer(), queuedTotem), 5L);
      }
      this.recoverStrandedDeliveries(var1.getPlayer());
      this.refreshEquippedArtifactBindingsAsync(var1.getPlayer());
      UUID playerUuid = var1.getPlayer().getUniqueId();
      this.runAsync(
         () -> {
            int var2 = this.pendingCount(playerUuid.toString());
            if (var2 > 0) {
               this.runSync(
                  () -> {
                     Player player = Bukkit.getPlayer(playerUuid);
                     if (player != null && player.isOnline()) {
                        player.sendMessage(
                                                       this.color(
                               "&eУ вас есть отложенная выдача CopiMineArtifacts: &f"
                                  + var2
                                 + "&e. Используйте &f/cmartifacts claim&e."
                            )
                        );
                     }
                  }
               );
            }
         }
      );
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onPlayerDeath(PlayerDeathEvent var1) {
      Player player = var1.getEntity();
      if (player != null && player.getWorld() != null) {
         Location deathLocation = player.getLocation().clone();
         this.lastDeathLocations.put(player.getUniqueId(), deathLocation);
         PersistentDataContainer pdc = player.getPersistentDataContainer();
         pdc.set(this.keyLastDeathWorld, PersistentDataType.STRING, player.getWorld().getName());
         pdc.set(this.keyLastDeathX, PersistentDataType.INTEGER, deathLocation.getBlockX());
         pdc.set(this.keyLastDeathY, PersistentDataType.INTEGER, deathLocation.getBlockY());
         pdc.set(this.keyLastDeathZ, PersistentDataType.INTEGER, deathLocation.getBlockZ());
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = false
   )
   public void onArtifactInteract(PlayerInteractEvent var1) {
      Player var2 = var1.getPlayer();
      // Elytra use normally consumes FIREWORK_ROCKET before the ability can
      // finish.  Deny that vanilla path up front for the official eternal
      // rocket, including the short period while its DB binding is restored.
      ItemStack interacted = var1.getItem();
      if (interacted != null && interacted.hasItemMeta()) {
         String itemId = this.firstNonBlank(
            interacted.getItemMeta().getPersistentDataContainer().get(this.keyItemId, PersistentDataType.STRING), ""
         );
         if ("vechniy_razgon_firework".equalsIgnoreCase(itemId)) {
            var1.setUseItemInHand(Event.Result.DENY);
            var1.setUseInteractedBlock(Event.Result.DENY);
            var1.setCancelled(true);
         }
      }
      CopiMineArtifacts.CatalogItem var3 = this.authenticCatalogItem(var1.getItem(), var2, "interact");
      if (var3 != null) {
         String var4 = var3.effect().toUpperCase(Locale.ROOT);
         if (this.artifactInteractEffects().contains(var4)) {
            Action var5 = var1.getAction();
             if ("FARMER_SWEEP".equals(var4)) {
                if (var5 != Action.RIGHT_CLICK_BLOCK || var1.getClickedBlock() == null || !var2.isSneaking()) {
                   return;
                }
            } else if ("WIND_HAMMER".equals(var4)) {
                if (var5 != Action.RIGHT_CLICK_BLOCK || var1.getClickedBlock() == null
                        || var1.getBlockFace() != BlockFace.UP
                        || !var1.getClickedBlock().getType().isSolid()) {
                   return;
                }
            } else if (var5 != Action.RIGHT_CLICK_AIR && var5 != Action.RIGHT_CLICK_BLOCK) {
               return;
            }

            // The ability owns the interaction.  Deny the vanilla use as well,
            // otherwise a FIREWORK_ROCKET can be consumed by the client/server
            // before the eternal boost finishes.
            var1.setUseItemInHand(Event.Result.DENY);
            var1.setUseInteractedBlock(Event.Result.DENY);
            long var6 = this.now();
            long var8 = this.actionCooldowns.getOrDefault(this.actionCooldownKey(var2, var3), 0L);
            if (var8 > var6) {
               this.sendCooldownMessage(var2, var3, var8, var6);
               var2.sendMessage(
                  this.color(
                     "&eАртефакт ещё на откате."
                  )
               );
               var1.setCancelled(true);
            } else {
               boolean var10 = switch (var4) {
                  case "HASTE_BURST_LONG" -> {
                     var2.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 3600, 1, false, false, true));
                     var2.getWorld().spawnParticle(Particle.ENCHANT, var2.getLocation().add(0.0, 1.0, 0.0), 18, 0.45, 0.45, 0.45, 0.02);
                     yield true;
                  }
                  case "WIND_HAMMER" -> this.triggerWindHammer(var2, var1.getClickedBlock());
                  case "FARMER_SWEEP" -> this.tryFarmerSweep(var2, var1.getClickedBlock(), var1.getItem());
                  case "DEBUFF_AMULET" -> this.cleanseAllowedDebuff(var2);
                  case "TAX_CLOCK" -> {
                     this.activateTaxClock(var2, var1.getItem());
                     yield true;
                  }
                  case "LOOT_COMPASS" -> this.activateLootCompass(var2, var1.getItem());
                  case "ETERNAL_BOOST" -> this.triggerEternalBoost(var2);
                  default -> false;
               };
               if (var10) {
                  long cooldownSeconds = "LOOT_COMPASS".equals(var4)
                     ? COMPASS_COOLDOWN_SECONDS
                     : Math.max(1, var3.cooldownSeconds());
                  this.actionCooldowns.put(this.actionCooldownKey(var2, var3), var6 + cooldownSeconds);
                  if (!var3.visualEffectId().isBlank()) {
                     this.visualEffects.applyTo(var2, var3.visualEffectId(), Math.max(4, var3.cooldownSeconds()));
                  }

                  var1.setCancelled(true);
               }
            }
         }
      }
   }

   private boolean triggerWindHammer(Player player, Block ground) {
      if (player == null || ground == null || !ground.getType().isSolid()) {
         return false;
      }
      // The hammer launches its owner high enough to perform a falling strike.
      // Do not apply levitation: the launch is a single impulse and the cooldown
      // prevents repeated use.
      player.removePotionEffect(PotionEffectType.LEVITATION);
      player.setFallDistance(0.0F);
      player.setVelocity(player.getVelocity().setY(1.9D));
      Location center = ground.getLocation().add(0.5D, 0.5D, 0.5D);
      for (Entity entity : player.getWorld().getNearbyEntities(center, 10.0D, 10.0D, 10.0D)) {
         if (entity == player || !(entity instanceof LivingEntity living) || living.getLocation().distanceSquared(center) > 100.0D) {
            continue;
         }
         living.removePotionEffect(PotionEffectType.LEVITATION);
         living.setFreezeTicks(Math.max(living.getFreezeTicks(), 100));
         living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 10, false, false, true));
      }
      player.getWorld().spawnParticle(Particle.CLOUD, center, 60, 3.5D, 0.3D, 3.5D, 0.08D);
      return true;
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = false
   )
   public void onArtifactDefend(EntityDamageEvent var1) {
      if (var1.getEntity() instanceof Player var2) {
         CopiMineArtifacts.CatalogItem var13 = this.authenticCatalogItem(var2.getInventory().getHelmet(), var2, "defend_helmet");
         CopiMineArtifacts.CatalogItem var4 = this.authenticCatalogItem(var2.getInventory().getChestplate(), var2, "defend_chest");
         CopiMineArtifacts.CatalogItem var5 = this.authenticCatalogItem(var2.getInventory().getItemInOffHand(), var2, "defend_offhand");
         if (var5 == null || !"NOT_TODAY_SHIELD".equalsIgnoreCase(var5.effect())) {
            var5 = this.authenticCatalogItem(var2.getInventory().getItemInMainHand(), var2, "defend_mainhand");
         }
         CopiMineArtifacts.CatalogItem pozdnyakovAce = this.authenticCatalogItem(var2.getInventory().getLeggings(), var2, "defend_pozdnyakov_ace");
         if (pozdnyakovAce != null && "POZDNYAKOV_ACE".equalsIgnoreCase(pozdnyakovAce.effect()) && var1 instanceof EntityDamageByEntityEvent damageEvent) {
            LivingEntity attacker = this.resolveDamageAttacker(damageEvent);
            long current = this.now();
            long cooldownUntil = this.pozdnyakovNauseaCooldowns.getOrDefault(var2.getUniqueId(), 0L);
            if (attacker != null && attacker != var2 && cooldownUntil <= current) {
               attacker.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 100, 2, false, false, true));
               attacker.getWorld().strikeLightning(attacker.getLocation());
               this.pozdnyakovNauseaCooldowns.put(var2.getUniqueId(), current + 30L);
            }
         }
         if (var13 != null && "PRORAB_HELMET".equalsIgnoreCase(var13.effect()) && var1.getCause() == DamageCause.FALL) {
            long var6 = this.now();
            long var8 = this.actionCooldowns.getOrDefault(this.actionCooldownKey(var2, var13), 0L);
            if (var8 > var6) {
               this.sendCooldownMessage(var2, var13, var8, var6);
            } else if (this.rollEffectChance(var13)) {
               var1.setDamage(var1.getDamage() * 0.4);
               var2.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, 0, false, false, true));
               var2.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 80, 0, false, false, true));
               this.actionCooldowns.put(this.actionCooldownKey(var2, var13), var6 + (long)Math.max(8, var13.cooldownSeconds()));
            }
         }

         if (var4 != null && "TANK_VEST".equalsIgnoreCase(var4.effect())) {
            long var6 = this.now();
            long var8 = this.actionCooldowns.getOrDefault(this.actionCooldownKey(var2, var4), 0L);
            if (var1.getDamage() > 0.0) {
               var1.setDamage(var1.getDamage() * 0.8);
               if (var8 > var6) {
                  this.sendCooldownMessage(var2, var4, var8, var6);
               } else if (this.rollEffectChance(var4)) {
                  var2.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 600, 0, false, false, true));
                  var2.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 0, false, false, true));
                  this.actionCooldowns.put(this.actionCooldownKey(var2, var4), var6 + (long)Math.max(8, var4.cooldownSeconds()));
               }
            }
         }

         if (var5 != null && "NOT_TODAY_SHIELD".equalsIgnoreCase(var5.effect()) && var2.isBlocking() && var1 instanceof EntityDamageByEntityEvent var14) {
            long var7 = this.now();
            long var9 = this.actionCooldowns.getOrDefault(this.actionCooldownKey(var2, var5), 0L);
            if (var9 > var7) {
               this.sendCooldownMessage(var2, var5, var9, var7);
            } else if (this.rollEffectChance(var5)) {
               LivingEntity attacker = this.resolveDamageAttacker(var14);
               if (attacker != null && attacker != var2) {
                  attacker.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 80, 0, false, false, true));
                  attacker.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0, false, false, true));
                  // Keep the configured lightning cue visible without adding
                  // a second damage event on top of the blocked hit.
                  attacker.getWorld().strikeLightningEffect(attacker.getLocation());
               }

               var2.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 0, false, false, true));
               var2.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, var2.getLocation().add(0.0, 1.0, 0.0), 16, 0.45, 0.55, 0.45, 0.03);
               this.actionCooldowns.put(this.actionCooldownKey(var2, var5), var7 + (long)Math.max(6, var5.cooldownSeconds()));
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = false
   )
   public void onCustomShieldDisable(PlayerShieldDisableEvent event) {
      Player player = event.getPlayer();
      CatalogItem shield = this.authenticCatalogItem(player.getInventory().getItemInOffHand(), player, "shield_disable_offhand");
      if (shield == null || !"NOT_TODAY_SHIELD".equalsIgnoreCase(shield.effect())) {
         shield = this.authenticCatalogItem(player.getInventory().getItemInMainHand(), player, "shield_disable_mainhand");
      }
      if (shield != null && "NOT_TODAY_SHIELD".equalsIgnoreCase(shield.effect())) {
         // Paper exposes the vanilla axe-disable mechanic as a cancellable event.
         // Do not let an axe bypass this artifact's own ability cooldown.
         event.setCancelled(true);
         event.setCooldown(0);
         player.setCooldown(Material.SHIELD, 0);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onRepairTargetDamage(PlayerItemDamageEvent event) {
      if (event != null && this.isRepairLockedItem(event.getPlayer(), event.getItem())) {
         event.setCancelled(true);
      }
   }

   /**
    * Durability break is reported after vanilla has removed the item.  Catch
    * the threshold one event earlier so a journal/fsync failure never turns
    * a donation item into an unrecoverable loss.
    */
   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onDonationItemDamage(PlayerItemDamageEvent event) {
       if (this.customShopItemsAreVanilla()) {
          return;
       }
       if (event == null || event.getPlayer() == null || event.getItem() == null) {
         return;
      }
      // A protection/repair listener may have cancelled this damage already;
      // cancelled damage cannot break the item and must never create a loss
      // or reclaim entry as a side effect.
      if (event.isCancelled()) {
         return;
      }
      OfficialDonationRef ref = this.officialDonationRef(event.getItem());
      if (ref == null || !event.getPlayer().getUniqueId().equals(ref.ownerUuid())) {
         return;
      }
      ItemMeta meta = event.getItem().getItemMeta();
      if (!(meta instanceof Damageable damageable)) {
         return;
      }
      int maxDurability = event.getItem().getType().getMaxDurability();
      if (maxDurability <= 0 || damageable.getDamage() + event.getDamage() < maxDurability) {
         return;
      }
      event.setCancelled(true);
      this.markDonationInstanceBroken(ref, "durability-break");
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onCustomShieldDamage(PlayerItemDamageEvent event) {
      if (event.getItem() == null || event.getItem().getType() != Material.SHIELD) {
         return;
      }
      CatalogItem shield = this.authenticCatalogItem(event.getItem(), event.getPlayer(), "shield_durability");
      if (shield != null && "NOT_TODAY_SHIELD".equalsIgnoreCase(shield.effect())) {
         // Keep damage predictable; Unbreaking III is applied when the official
         // item is created, and a single blocked hit consumes at most one point.
         event.setDamage(Math.min(1, Math.max(0, event.getDamage())));
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onCrossbowArtifactShot(EntityShootBowEvent var1) {
      if (var1.getEntity() instanceof Player var2) {
         CopiMineArtifacts.CatalogItem var4 = this.authenticCatalogItem(var1.getConsumable(), var2, "crossbow");
         if (var4 != null && "ETERNAL_BOOST".equalsIgnoreCase(var4.effect())) {
            var1.setCancelled(true);
            var2.sendMessage(
               this.color(
                  "&cЭтот фейерверк работает только как элитровый буст."
               )
            );
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onArtifactDamage(EntityDamageByEntityEvent var1) {
      Player var2 = null;
      if (var1.getDamager() instanceof Player var3) {
         var2 = var3;
      } else if (var1.getDamager() instanceof Projectile var4 && var4.getShooter() instanceof Player var5) {
         var2 = var5;
      }

      if (var2 != null) {
         ItemStack var14 = var2.getInventory().getItemInMainHand();
         CopiMineArtifacts.CatalogItem var15 = this.authenticCatalogItem(var14, var2, "use");
         if (var15 != null) {
            String var16 = var15.effect().toUpperCase(Locale.ROOT);
            if ("NALOGOVAYA_KOSA".equals(var16)) {
               LivingEntity target = var1.getEntity() instanceof LivingEntity living ? living : null;
               this.tryRareArTheft(var2, target);
            }
            if (this.artifactCombatEffects().contains(var16)) {
               long var19 = this.actionCooldowns.getOrDefault(this.actionCooldownKey(var2, var15), 0L);
               long var8 = this.now();
               if (var19 <= var8 && this.rollEffectChance(var15)) {
                  this.actionCooldowns.put(this.actionCooldownKey(var2, var15), var8 + (long)var15.cooldownSeconds());
                  LivingEntity var10 = var1.getEntity() instanceof LivingEntity var11 ? var11 : null;
                  Location var20 = var1.getEntity().getLocation().add(0.0, 1.0, 0.0);
                  switch (var16) {
                        case "LIGHTNING":
                            var20.getWorld().strikeLightning(var1.getEntity().getLocation());
                           var20.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, var20, 10, 0.3, 0.3, 0.3, 0.01);
                           var20.getWorld().playSound(var20, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5F, 1.8F);
                           var1.setDamage(var1.getDamage() + 2.0);
                           break;
                        case "DRAGON_PUNISHMENT":
                           var20.getWorld().spawnParticle(Particle.DRAGON_BREATH, var20, 16, 0.35, 0.35, 0.35, 0.01);
                           var20.getWorld().playSound(var20, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.35F, 1.45F);
                           var1.setDamage(var1.getDamage() + 1.5);
                           if (var10 != null) {
                              var10.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0, false, false, true));
                           }
                           break;
                        case "WATCH_GLOW":
                           if (var10 != null) {
                              var10.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 120, 0, false, false, true));
                              var10.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0, false, false, true));
                           }

                           var20.getWorld().spawnParticle(Particle.ENCHANT, var20, 14, 0.4, 0.4, 0.4, 0.02);
                           var20.getWorld().playSound(var20, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.45F, 1.35F);
                           break;
                        case "DEBT_SNARE":
                           if (var10 != null) {
                              var10.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0, false, false, true));
                           }

                           var20.getWorld().spawnParticle(Particle.CRIT, var20, 12, 0.35, 0.25, 0.35, 0.05);
                           var20.getWorld().playSound(var20, Sound.BLOCK_CHAIN_HIT, 0.5F, 0.8F);
                           break;
                        case "SMUGGLER_MARK":
                           if (var10 != null) {
                              var10.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0, false, false, true));
                           }

                           var2.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 0, false, false, true));
                           var20.getWorld().spawnParticle(Particle.SMOKE, var20, 8, 0.3, 0.3, 0.3, 0.02);
                           var20.getWorld().playSound(var20, Sound.ENTITY_ARROW_HIT_PLAYER, 0.35F, 1.7F);
                           break;
                        case "ZMEI_GORYNYCH_POOP":
                           if (var10 != null) {
                              var10.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 120, 0, false, false, true));
                              var10.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 160, 0, false, false, true));
                           }

                           var20.getWorld().spawnParticle(Particle.DRAGON_BREATH, var20, 24, 0.45, 0.35, 0.45, 0.01);
                           var20.getWorld().spawnParticle(Particle.SMOKE, var20, 18, 0.35, 0.25, 0.35, 0.03);
                           break;
                        case "BATIN_REMEN":
                           var1.setDamage(var1.getDamage() + 2.5);
                           var2.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 0, false, false, true));
                           if (var10 != null) {
                              var10.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 600, 1, false, false, true));
                              var10.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 600, 0, false, false, true));
                              if (random.nextDouble() < 0.20D) {
                                 this.applyTemporaryCobwebSnare(var2, var10, 200L);
                              }
                           }
                           var20.getWorld().strikeLightning(var1.getEntity().getLocation());
                           var20.getWorld().spawnParticle(Particle.CRIT, var20, 18, 0.35, 0.35, 0.35, 0.03);
                           break;
                        case "NAKOPAL_PICKAXE":
                           var1.setDamage(var1.getDamage() + 1.0);
                           if (var10 != null) {
                              var10.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 2, false, false, true));
                              var10.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false, true));
                              this.applyTemporaryCobwebSnare(var2, var10, 100L);
                              this.applyTemporaryBurial(var2, var10, 100L);
                           }

                           var2.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 80, 0, false, false, true));
                           var20.getWorld().spawnParticle(Particle.BLOCK, var20, 16, 0.3, 0.3, 0.3, Material.DEEPSLATE.createBlockData());
                           break;
                        case "NALOGOVAYA_KOSA":
                           var1.setDamage(var1.getDamage() + 4.0);
                           if (var10 != null) {
                              var10.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 100, 0, false, false, true));
                              var10.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0, false, false, true));
                           }

                           this.healPlayerCapped(var2, 4.0);
                           var20.getWorld().spawnParticle(Particle.SCULK_SOUL, var20, 10, 0.25, 0.25, 0.25, 0.01);
                           break;
                        case "DUTY_ARGUMENT":
                           var1.setDamage(var1.getDamage() + 2.0);
                           var2.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 80, 0, false, false, true));
                           if (var10 != null) {
                              var10.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 0, false, false, true));
                              var10.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 80, 0, false, false, true));
                           }

                           var20.getWorld().spawnParticle(Particle.ENCHANT, var20, 18, 0.3, 0.3, 0.3, 0.02);
                  }

                  if (var10 != null && !var15.visualEffectId().isBlank()) {
                     this.visualEffects.applyTo(var10, var15.visualEffectId(), Math.max(4, var15.cooldownSeconds()));
                     if ("ZMEI_GORYNYCH_POOP".equals(var16)) {
                        this.visualEffects.applyTo(var10, "DARK_PULSE", 4);
                     }
                  }
               } else if (var19 > var8) {
                  this.sendCooldownMessage(var2, var15, var19, var8);
               }
            }
         }
      }
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      if ("setprice".equalsIgnoreCase(var2.getName())) {
         return this.handleSetPriceCommand(var1, var4);
      }

      // The admin web panel updates items.yml and asks the server console to
      // reload the catalog.  Previously cmartifacts reload was silently
      // ignored for console/RCON senders because all subcommands were gated
      // behind Player, leaving the game shop on the old price until a restart.
      if (!(var1 instanceof Player)) {
         if (var4.length > 0 && "reload".equalsIgnoreCase(var4[0])) {
            try {
               this.reloadConfig();
               this.debugGui = this.getConfig().getBoolean("debug_gui", false);
               this.loadCatalogFromConfig();
               this.syncCatalogToPostgres();
               this.loadShopsFromPostgres();
               this.repairShopTitleDisplays();
               var1.sendMessage("CopiMineArtifacts catalog reloaded.");
            } catch (Exception error) {
               this.getLogger().log(Level.WARNING, "Artifacts console reload failed", error);
               var1.sendMessage("CopiMineArtifacts catalog reload failed: " + this.safeErr(error));
            }
            return true;
         }
         var1.sendMessage("Use /cmartifacts reload from the server console.");
         return true;
      }

      if (var1 instanceof Player var5) {
         if (var4.length == 0) {
            if (this.isArtifactsAdmin(var5)) {
               // openArtifactsAdminMenu(player);
               this.openArtifactsAdminMenu(var5);
            } else {
               // sendPlayerShopHelp(player);
               this.sendPlayerShopHelp(var5);
            }

            return true;
         } else if ("claim".equalsIgnoreCase(var4[0])) {
            this.claimPending(var5);
            return true;
         } else if ("repair".equalsIgnoreCase(var4[0])) {
            this.openRepair(var5);
            return true;
         } else if ("admin".equalsIgnoreCase(var4[0])) {
            if (!this.isArtifactsAdmin(var5)) {
               this.noPermission(var5);
               return true;
            } else {
               if (var4.length >= 3 && "give".equalsIgnoreCase(var4[1])) {
                  return this.handleAdminGiveCommand(var5, var4);
               }
               // openArtifactsAdminMenu(player);
               this.openArtifactsAdminMenu(var5);
               return true;
            }
         } else if ("reload".equalsIgnoreCase(var4[0])) {
            if (!this.hasArtifactPermission(var5, "copimine.artifacts.reload")) {
               this.noPermission(var5);
               return true;
            } else {
               this.reloadConfig();
               this.debugGui = this.getConfig().getBoolean("debug_gui", false);
               this.loadCatalogFromConfig();

               try {
                  this.syncCatalogToPostgres();
                  this.loadShopsFromPostgres();
                  this.repairShopTitleDisplays();
                  var5.sendMessage(this.color("&aCopiMineArtifacts catalog reloaded."));
               } catch (SQLException var7) {
                  this.getLogger().log(Level.WARNING, "Artifacts reload failed", (Throwable)var7);
                  var5.sendMessage(
                     this.color(
                        "&cНе удалось выполнить действие. Ошибка записана в лог. Код: ARTIFACT_RELOAD_FAILED"
                     )
                  );
               }

               return true;
            }
         } else if ("shop".equalsIgnoreCase(var4[0])) {
            return this.handleShopCommand(var5, Arrays.copyOfRange(var4, 1, var4.length));
         } else {
            var5.sendMessage(
               this.color(
                  "&cНеизвестная команда CopiMineArtifacts."
               )
            );
            return true;
         }
      } else {
         var1.sendMessage("Players only.");
         return true;
      }
   }

   private boolean handleAdminGiveCommand(Player actor, String[] args) {
      if (!this.hasArtifactPermission(actor, "copimine.artifacts.admin.give")) {
         this.noPermission(actor);
         return true;
      }
      if (args.length < 4 || !"give".equalsIgnoreCase(args[1])) {
         actor.sendMessage(this.color("&e/cmartifacts admin give <player> <admin_item_id>"));
         return true;
      }

      Player target = Bukkit.getPlayerExact(args[2]);
      if (target == null) {
         actor.sendMessage(this.color("&cИгрок должен быть онлайн."));
         return true;
      }
      String itemId = args[3].trim().toLowerCase(Locale.ROOT);
      CopiMineArtifacts.CatalogItem item = this.runtimeCatalogItem(itemId);
      if (item == null || !this.isAdminOnlyCatalogItem(itemId) || !"POZDNYAKOV_ACE".equalsIgnoreCase(item.effect())) {
         actor.sendMessage(this.color("&cНеизвестный административный предмет."));
         return true;
      }
      String idempotencyKey = "admin-grant:" + actor.getUniqueId() + ":" + UUID.randomUUID();
      UUID ownerUuid = target.getUniqueId();
      this.runAsync(() -> {
         boolean created = this.persistAdminGiftDelivery(ownerUuid, target.getName(), item, actor.getName(), idempotencyKey);
         this.runSync(() -> {
            if (created) {
               this.giftNotice(ownerUuid);
               if (actor.isOnline()) {
                  actor.sendMessage(this.color("&aПредмет поставлен в безопасную очередь выдачи игроку &f" + target.getName() + "&a. Он заберёт его в лавке."));
               }
            } else if (actor.isOnline()) {
               actor.sendMessage(this.color("&cНе удалось выдать предмет. Ошибка записана в лог: ARTIFACT_ADMIN_GRANT_FAILED"));
            }
         });
      });
      actor.sendMessage(this.color("&eВыдача предмета поставлена в безопасную очередь PostgreSQL."));
      return true;
   }

   private void persistAdminGrantedInstance(UUID ownerUuid, String purchaseId, String itemId, String uniqueItemId) throws SQLException {
      Connection connection = this.pgPool.acquire();
      try {
         connection.setAutoCommit(false);
         try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO artifact_item_instances(unique_item_id,item_id,owner_uuid,purchase_id,status,repaired_count,created_at,updated_at) VALUES(?,?,?,?, 'ACTIVE',0,?,?)"
         )) {
            long timestamp = this.now();
            statement.setString(1, uniqueItemId);
            statement.setString(2, itemId);
            statement.setString(3, ownerUuid.toString());
            statement.setString(4, purchaseId);
            statement.setLong(5, timestamp);
            statement.setLong(6, timestamp);
            statement.executeUpdate();
         }
         connection.commit();
      } catch (SQLException error) {
         connection.rollback();
         throw error;
      } finally {
         try {
            connection.setAutoCommit(true);
         } catch (SQLException ignored) {
         }
         this.pgPool.release(connection);
      }
   }

   private void revokeAdminGrantedInstance(String uniqueItemId) {
      try {
         Connection connection = this.pgPool.acquire();
         try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE artifact_item_instances SET status='REVOKED',updated_at=? WHERE unique_item_id=? AND status='ACTIVE'"
         )) {
            statement.setLong(1, this.now());
            statement.setString(2, uniqueItemId);
            statement.executeUpdate();
         } finally {
            this.pgPool.release(connection);
         }
      } catch (SQLException error) {
         this.getLogger().log(Level.WARNING, "Admin artifact grant revoke failed for " + uniqueItemId, error);
      } finally {
         this.removeOfficialBinding(uniqueItemId);
      }
   }

   private boolean handleSetPriceCommand(CommandSender sender, String[] args) {
      if (!(sender instanceof Player player)) {
         sender.sendMessage("Players only.");
         return true;
      }

      if (!this.hasArtifactPermission(player, "copimine.artifacts.price.set")) {
         this.noPermission(player);
         return true;
      }

      if (args.length != 2 && args.length != 3) {
         player.sendMessage(this.color("&eИспользование: &f/setprice <item_id> <price>"));
         player.sendMessage(this.color("&eИли: &f/setprice <item_id> <ar|donation> <price>"));
         return true;
      }

      String itemId = args[0].trim().toLowerCase(Locale.ROOT);
      String requestedKind = args.length == 3 ? args[1].trim().toLowerCase(Locale.ROOT) : "";
      String priceText = args.length == 3 ? args[2].trim() : args[1].trim();
      Long price = this.tryParseNonNegativeLong(priceText);
      if (price == null) {
         player.sendMessage(this.color("&cЦена должна быть целым числом 0 или больше."));
         return true;
      }

      String resolvedKind = this.resolvePriceCatalogKind(itemId, requestedKind);
      if (resolvedKind == null) {
         if ("ar".equals(requestedKind) || "donation".equals(requestedKind)) {
            player.sendMessage(this.color("&cТовар &f" + itemId + " &cне найден в разделе &f" + requestedKind + "&c."));
         } else {
            player.sendMessage(this.color("&cТовар &f" + itemId + " &cне найден в каталоге."));
         }

         return true;
      }

      Path itemsPath = this.getDataFolder().toPath().resolve("items.yml");
      byte[] previousItemsConfig = null;
      boolean catalogSyncAttempted = false;
      try {
         if (Files.exists(itemsPath)) {
            previousItemsConfig = Files.readAllBytes(itemsPath);
         }
         this.updateItemPriceInConfig(itemId, resolvedKind, price);
         this.loadCatalogFromConfig();
         catalogSyncAttempted = true;
         this.syncCatalogToPostgres();
         this.audit(player.getName(), "set_price", itemId, "kind=" + resolvedKind + " price=" + price);
         player.sendMessage(
            this.color(
               "&aЦена обновлена: &f" + itemId + " &7(" + resolvedKind + ") &a-> &f" + price + ("donation".equals(resolvedKind) ? " Donation" : " AR")
            )
         );
      } catch (Exception error) {
         this.getLogger().log(Level.WARNING, "Artifact setprice failed for " + itemId, error);
         if (previousItemsConfig != null) {
            try {
               this.writeAtomicBytes(itemsPath, previousItemsConfig);
               this.loadCatalogFromConfig();
               // syncCatalogToPostgres() is transactional, but the following
               // audit/notification can still fail after its commit.  Restore
               // the database snapshot as well as the file/runtime catalog so
               // /setprice is all-or-nothing across every source of truth.
               if (catalogSyncAttempted) {
                  try {
                     this.syncCatalogToPostgres();
                  } catch (Exception databaseRollbackError) {
                     this.getLogger().log(Level.SEVERE, "Artifact setprice database rollback failed for " + itemId, databaseRollbackError);
                  }
               }
            } catch (Exception rollbackError) {
               this.getLogger().log(Level.SEVERE, "Artifact setprice rollback failed for " + itemId, rollbackError);
            }
         }
         player.sendMessage(this.color("&cНе удалось обновить цену. Подробности записаны в лог. Код: ARTIFACT_SET_PRICE_FAILED"));
      }

      return true;
   }

   private boolean handleShopCommand(Player var1, String[] var2) {
      if (var2.length == 0) {
         if (!this.isArtifactsAdmin(var1)) {
            this.noPermission(var1);
            return true;
         } else {
            var1.sendMessage(this.color("&e/cmartifacts shop create <shop_id>"));
            var1.sendMessage(this.color("&e/cmartifacts shop remove"));
            var1.sendMessage(this.color("&e/cmartifacts shop rename <shop_id> <название>"));
            var1.sendMessage(this.color("&e/cmartifacts shop list"));
            var1.sendMessage(this.color("&e/cmartifacts shop open <shop_id>"));
            return true;
         }
      } else if ("create".equalsIgnoreCase(var2[0])) {
         if (!this.hasArtifactPermission(var1, "copimine.artifacts.shop.create")) {
            this.noPermission(var1);
            return true;
         } else if (var2.length < 2) {
            var1.sendMessage(this.color("&cУкажи shop_id."));
            return true;
         } else {
            Block var8 = var1.getTargetBlockExact(6);
            if (var8 == null) {
               var1.sendMessage(
                  this.color(
                     "&cСмотри на блок лавки в пределах 6 блоков."
                  )
               );
               return true;
            } else {
               String var9 = this.blockKey(var8.getLocation());
               if (this.shopsByLocation.containsKey(var9)) {
                  var1.sendMessage(
                     this.color(
                        "&cНа этом блоке уже есть лавка CopiMineArtifacts."
                     )
                  );
                  return true;
               } else {
                  String var5 = var2[1].toLowerCase(Locale.ROOT);
                  if (this.shopsByLocation.values().stream().anyMatch(existing -> existing.shopId().equalsIgnoreCase(var5))) {
                     var1.sendMessage(this.color("&cЛавка с таким идентификатором уже существует."));
                     return true;
                  }
                  String title = var2.length > 2 ? String.join(" ", Arrays.copyOfRange(var2, 2, var2.length)).trim() : var5;
                  CopiMineArtifacts.Shop var6 = new CopiMineArtifacts.Shop(
                     var5, title.isBlank() ? var5 : title, var8.getWorld().getName(), var8.getX(), var8.getY(), var8.getZ(), true, var8.getType().name()
                  );
                  this.runAsync(
                     () -> {
                        try {
                           this.saveShop(var6);
                           this.shopsByLocation.put(var6.locationKey(), var6);
                           this.audit(var1.getName(), "shop_create", var6.shopId(), var6.locationKey());
                           this.runSync(
                              () -> {
                                 try {
                                    // spawnOrReplaceProtectedBlockVisual(target.getLocation(), "ARTIFACT_SHOP", shop.shopId(), Material.PAPER, MODEL_ARTIFACT_SHOP_MARKER, "artifact_shop_marker")
                                    this.spawnOrReplaceProtectedBlockVisual(
                                       var8.getLocation(), "ARTIFACT_SHOP", var6.shopId(), Material.PAPER, MODEL_ARTIFACT_SHOP_MARKER, "artifact_shop_marker"
                                    );
                                    this.spawnShopTitleDisplay(var8.getLocation(), var6.shopId(), var6.title());
                                 } catch (Exception var4x) {
                                    this.getLogger().log(Level.WARNING, "Artifact shop visual create failed", (Throwable)var4x);
                                 }
                              }
                           );
                           this.runSync(
                              () -> var1.sendMessage(
                                    this.color(
                                       "&aЛавка создана: &f"
                                          + var6.shopId()
                                    )
                                 )
                           );
                        } catch (SQLException var5x) {
                           this.getLogger().log(Level.WARNING, "Artifact shop create failed", (Throwable)var5x);
                           this.runSync(
                              () -> var1.sendMessage(
                                    this.color(
                                       "&cНе удалось выполнить действие. Ошибка записана в лог. Код: ARTIFACT_SHOP_SAVE_FAILED"
                                    )
                                 )
                           );
                        }
                     }
                  );
                  return true;
               }
            }
         }
      } else if ("remove".equalsIgnoreCase(var2[0])) {
         if (!this.hasArtifactPermission(var1, "copimine.artifacts.shop.remove")) {
            this.noPermission(var1);
            return true;
         } else {
            Block var7 = var1.getTargetBlockExact(6);
            if (var7 == null) {
               var1.sendMessage(
                  this.color(
                     "&cСмотри на блок лавки в пределах 6 блоков."
                  )
               );
               return true;
            } else {
               CopiMineArtifacts.Shop var4 = this.shopsByLocation.get(this.blockKey(var7.getLocation()));
               if (var4 == null) {
                  var1.sendMessage(
                     this.color(
                        "&cНа этом блоке нет лавки CopiMineArtifacts."
                     )
                  );
                  return true;
               } else {
                  this.runAsync(
                     () -> {
                        try {
                           this.deleteShop(var4.shopId());
                           this.shopsByLocation.remove(var4.locationKey());
                           this.audit(var1.getName(), "shop_remove", var4.shopId(), var4.locationKey());
                           this.runSync(() -> {
                              try {
                                 // cleanupProtectedBlockVisuals("ARTIFACT_SHOP", shop.shopId())
                                 this.cleanupProtectedBlockVisuals("ARTIFACT_SHOP", var4.shopId());
                              } catch (Exception var3x) {
                                 this.getLogger().log(Level.WARNING, "Artifact shop visual cleanup failed", (Throwable)var3x);
                              }
                           });
                           this.runSync(
                              () -> var1.sendMessage(
                                    this.color(
                                       "&aЛавка удалена: &f"
                                          + var4.shopId()
                                    )
                                 )
                           );
                        } catch (SQLException var4x) {
                           this.getLogger().log(Level.WARNING, "Artifact shop remove failed", (Throwable)var4x);
                           this.runSync(
                              () -> var1.sendMessage(
                                    this.color(
                                       "&cНе удалось выполнить действие. Ошибка записана в лог. Код: ARTIFACT_SHOP_REMOVE_FAILED"
                                    )
                                 )
                           );
                        }
                     }
                  );
                  return true;
               }
            }
         }
      } else if ("rename".equalsIgnoreCase(var2[0])) {
         if (!this.hasSeniorShopPermission(var1)) {
            this.noPermission(var1);
            return true;
         }
         if (var2.length < 3) {
            var1.sendMessage(this.color("&cИспользование: /cmartifacts shop rename <код> <новое название>"));
            return true;
         }
         String shopId = var2[1].trim();
         String title = String.join(" ", Arrays.copyOfRange(var2, 2, var2.length)).trim();
         if (!shopId.matches("[A-Za-z0-9_-]{1,64}") || title.isBlank() || title.length() > 64) {
            var1.sendMessage(this.color("&cКод лавки или название некорректны: название 1–64 символа."));
            return true;
         }
         CopiMineArtifacts.Shop current = this.shopsByLocation.values().stream()
            .filter(value -> value.shopId().equalsIgnoreCase(shopId))
            .findFirst().orElse(null);
         if (current == null) {
            var1.sendMessage(this.color("&cЛавка не найдена: &f" + shopId));
            return true;
         }
            CopiMineArtifacts.Shop renamed = new CopiMineArtifacts.Shop(current.shopId(), title, current.world(), current.x(), current.y(), current.z(), current.enabled(), current.anchorMaterial());
         this.runAsync(() -> {
            try {
               this.updateShopTitle(renamed);
               this.shopsByLocation.put(renamed.locationKey(), renamed);
               this.audit(var1.getName(), "shop_rename", renamed.shopId(), title);
               this.runSync(() -> {
                  Location location = this.shopLocation(renamed);
                  if (location != null) {
                     this.spawnShopTitleDisplay(location, renamed.shopId(), renamed.title());
                  }
                  var1.sendMessage(this.color("&aНазвание лавки изменено: &f" + renamed.title()));
               });
            } catch (SQLException error) {
               this.getLogger().log(Level.WARNING, "Artifact shop rename failed", error);
               this.runSync(() -> var1.sendMessage(this.color("&cНе удалось переименовать лавку. Код: ARTIFACT_SHOP_RENAME_FAILED")));
            }
         });
         return true;
      } else if ("list".equalsIgnoreCase(var2[0])) {
         if (!this.hasArtifactPermission(var1, "copimine.artifacts.shop.list")) {
            this.noPermission(var1);
            return true;
         } else {
            var1.sendMessage(
               this.color(
                  "&eЛавки CopiMineArtifacts: &f"
                     + this.shopsByLocation.values().stream().map(CopiMineArtifacts.Shop::shopId).collect(Collectors.joining(", "))
               )
            );
            return true;
         }
      } else if ("open".equalsIgnoreCase(var2[0])) {
         if (!this.isArtifactsAdmin(var1)) {
            this.noPermission(var1);
            return true;
         } else if (var2.length < 2) {
            var1.sendMessage(this.color("&cУкажи shop_id."));
            return true;
         } else {
            CopiMineArtifacts.Shop var3 = this.shopsByLocation
               .values()
               .stream()
               .filter(var1x -> var1x.shopId().equalsIgnoreCase(var2[1]))
               .findFirst()
               .orElse(null);
            if (var3 == null) {
               var1.sendMessage(
                  this.color(
                     "&cЛавка не найдена: &f"
                        + var2[1]
                  )
               );
               return true;
            } else {
               this.openMain(var1, var3, true);
               return true;
            }
         }
      } else {
         var1.sendMessage(
            this.color(
               "&cНеизвестная команда лавки."
            )
         );
         return true;
      }
   }

   private void removeShopWithCleanup(Player player, CopiMineArtifacts.Shop shop) {
      if (player == null || shop == null) {
         return;
      }
      this.runAsync(
         () -> {
            try {
               this.deleteShop(shop.shopId());
               this.shopsByLocation.remove(shop.locationKey());
               this.audit(player.getName(), "shop_remove", shop.shopId(), shop.locationKey());
                  this.runSync(
                     () -> {
                        try {
                           Location shopLocation = this.shopLocation(shop);
                           this.cleanupShopTitleDisplay(shopLocation, shop.shopId());
                           this.cleanupProtectedBlockVisuals("ARTIFACT_SHOP", shop.shopId());
                     } catch (Exception visualError) {
                        this.getLogger().log(Level.WARNING, "Artifact shop visual cleanup failed", (Throwable)visualError);
                     }
                     player.sendMessage(this.color("&aЛавка удалена: &f" + shop.shopId()));
                  }
               );
            } catch (SQLException error) {
               this.getLogger().log(Level.WARNING, "Artifact shop remove failed", (Throwable)error);
               this.runSync(
                  () -> player.sendMessage(
                     this.color("&cНе удалось удалить лавку. Ошибка записана в лог. Код: ARTIFACT_SHOP_REMOVE_FAILED")
                  )
               );
            }
         }
      );
   }

   private void openArtifactsAdminMenu(Player var1) {
      if (this.useDonationShopV2Menus()) {
         this.openArtifactsAdminMenuV2(var1);
      } else {
         CopiMineArtifacts.SessionState var2 = this.freshSession(var1);
         var2.viewType = CopiMineArtifacts.ViewType.ADMIN_MAIN;
         Inventory var3 = this.createMenu(var1, var2, CopiMineArtifacts.ViewType.ADMIN_MAIN, 27, "&8Artifacts Admin");
         var3.setItem(
            4,
            this.button(
               Material.NETHER_STAR,
               "&aCopiMineArtifacts",
               List.of(
                  "&7Служебная панель лавок и каталога.",
                  "&8Игроки открывают лавку только кликом по блоку."
               )
            )
         );
         this.setAction(
            var3,
            var2,
            10,
            this.button(
               Material.CHEST,
               "&eЛавки",
               List.of(
                  "&7Список активных блоков лавки."
               )
            ),
            "admin:shops"
         );
         this.setAction(
            var3,
            var2,
            12,
            this.button(
               Material.BOOK,
               "&eКаталог",
               List.of(
                  "&7Количество товаров по категориям."
               )
            ),
            "admin:catalog"
         );
         this.setAction(
            var3,
            var2,
            14,
            this.button(
               Material.COMPARATOR,
               "&eДиагностика",
               List.of("&7Bridge, PostgreSQL, PIN и кэш.")
            ),
            "admin:diagnostics"
         );
         this.setAction(
            var3,
            var2,
            20,
            this.button(
               Material.CLOCK,
               "&aReload",
               List.of(
                  "&7Перезагрузить config.yml и items.yml."
               )
            ),
            "admin:reload"
         );
         this.setAction(
            var3,
            var2,
            22,
            this.button(
               Material.BARRIER,
               "&cЗакрыть",
               List.of(
                  "&7Выйти из меню."
               )
            ),
            "close"
         );
         this.setAction(
            var3,
            var2,
            16,
            this.button(
               Material.NETHER_STAR,
               "&dДонатная лавка",
               List.of(
                  "&7Привязанные к владельцу предметы и платёжные сессии.",
                  "&7Покупка идёт на сайте, а выдача и возврат доступны только в игре."
               )
            ),
            "donation:root"
         );
         var1.openInventory(var3);
      }
   }

   private void openAdminShops(Player var1) {
      CopiMineArtifacts.SessionState var2 = this.session(var1);
      var2.viewType = CopiMineArtifacts.ViewType.ADMIN_SHOPS;
      Inventory var3 = this.createMenu(
         var1,
         var2,
         CopiMineArtifacts.ViewType.ADMIN_SHOPS,
         54,
         "&8Лавки артефактов"
      );
      var3.setItem(
         0,
         this.button(
            Material.CHEST,
            "&eЛавки артефактов",
            List.of(
               "&7Создание: /cmartifacts shop create <id>",
               "&7Удаление: /cmartifacts shop remove"
            )
         )
      );
      this.setAction(var3, var2, 1, this.button(Material.LIME_WOOL, "&aСоздать лавку", List.of("&7Привязать к блоку перед вами.")), "admin:shop:create");
      this.setAction(var3, var2, 4, this.button(Material.CHEST, "&eВыдать предмет", List.of("&7Создать отложенный подарок игроку.")), "admin:gift:players");
      this.setAction(var3, var2, 7, this.button(Material.BOOK, "&bВсе лавки", List.of("&7Список, статистика и переход.")), "admin:shop:list");
      int var4 = 9;

      for (CopiMineArtifacts.Shop var6 : this.shopsByLocation.values()) {
         if (var4 >= 45) {
            break;
         }

         this.setAction(
            var3,
            var2,
            var4,
            this.button(
               Material.BARREL,
               "&f" + var6.title(),
               List.of(
                  "&7ID: &f" + var6.shopId(),
                  "&7Мир: &f" + var6.world(),
                  "&7XYZ: &f" + var6.x() + " " + var6.y() + " " + var6.z(),
                  "&7Статус: "
                     + (
                        var6.enabled()
                           ? "&aвключена"
                           : "&cвыключена"
                     )
               )
            ),
            "shop:detail:" + var6.shopId()
         );
         ++var4;
      }

      this.setAction(
         var3,
         var2,
         45,
         this.button(
            Material.ARROW,
            "&aНазад",
            List.of(
               "&7Вернуться в админ-меню."
            )
         ),
         "admin:main"
      );
      this.setAction(
         var3,
         var2,
         49,
         this.button(
            Material.CLOCK,
            "&eОбновить",
            List.of(
               "&7Обновить список лавок."
            )
         ),
         "refresh"
      );
      this.setAction(
         var3,
         var2,
         53,
         this.button(
            Material.BARRIER,
            "&cЗакрыть",
            List.of("&7Выйти из меню.")
         ),
         "close"
      );
      var1.openInventory(var3);
   }

   public void openAdminShopHub(Player player) {
      if (!this.isArtifactsAdmin(player)) { this.noPermission(player); return; }
      this.openAdminShops(player);
   }

   private String nextGeneratedShopId() {
      // Keep the visible/database identifier short and readable.  The
      // database check below is still required because two admins can click
      // the button concurrently while this in-memory map has not updated.
      final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ";
      for (int attempt = 0; attempt < 64; ++attempt) {
         StringBuilder value = new StringBuilder(5);
         for (int index = 0; index < 5; ++index) {
            value.append(alphabet.charAt(this.random.nextInt(alphabet.length())));
         }
         String candidate = value.toString();
         boolean exists = this.shopsByLocation.values().stream().anyMatch(shop -> shop.shopId().equalsIgnoreCase(candidate));
         if (!exists) return candidate;
      }
      return UUID.randomUUID().toString().replace("-", "").substring(0, 5).toUpperCase(Locale.ROOT);
   }

   private boolean shopIdExistsInDatabase(String shopId) throws SQLException {
      Connection connection = null;
      try {
         connection = this.pgPool.acquire();
         try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM artifact_shops WHERE lower(shop_id)=lower(?) LIMIT 1")) {
            statement.setString(1, shopId);
            try (ResultSet result = statement.executeQuery()) {
               return result.next();
            }
         }
      } finally {
         if (connection != null) this.pgPool.release(connection);
      }
   }

   private Location shopLocation(CopiMineArtifacts.Shop shop) {
      if (shop == null) {
         return null;
      }
      World world = Bukkit.getWorld(shop.world());
      return world == null ? null : new Location(world, shop.x(), shop.y(), shop.z());
   }

   private void createAdminShopFromTarget(Player player) {
      if (!this.hasArtifactPermission(player, "copimine.artifacts.shop.create")) { this.noPermission(player); return; }
      Block target = player.getTargetBlockExact(6);
      String targetWorld = "";
      int targetX = 0;
      int targetY = 0;
      int targetZ = 0;
      if (target != null) {
         targetWorld = target.getWorld().getName();
         targetX = target.getX();
         targetY = target.getY();
         targetZ = target.getZ();
      }
      String actorName = player.getName();
      final String finalTargetWorld = targetWorld;
      final int finalTargetX = targetX;
      final int finalTargetY = targetY;
      final int finalTargetZ = targetZ;
      if (target == null || target.getType().isAir()) { player.sendMessage(this.color("&cСмотрите на существующий блок в пределах 6 блоков.")); return; }
      if (this.shopsByLocation.containsKey(this.blockKey(target.getLocation()))) { player.sendMessage(this.color("&cНа этом блоке уже есть лавка.")); return; }
      this.runAsync(() -> {
         try {
            String generatedId = this.nextGeneratedShopId();
            if (this.shopIdExistsInDatabase(generatedId)) {
               generatedId = this.nextGeneratedShopId();
               if (this.shopIdExistsInDatabase(generatedId)) throw new SQLException("Generated shop name already exists");
            }
            CopiMineArtifacts.Shop shop = new CopiMineArtifacts.Shop(generatedId, "Лавка " + generatedId, finalTargetWorld, finalTargetX, finalTargetY, finalTargetZ, true);
            this.saveShop(shop);
            this.shopsByLocation.put(shop.locationKey(), shop);
            this.audit(actorName, "shop_create", shop.shopId(), shop.locationKey());
            this.runSync(() -> { try { Location location = this.shopLocation(shop); if (location != null) { this.spawnOrReplaceProtectedBlockVisual(location, "ARTIFACT_SHOP", shop.shopId(), Material.PAPER, MODEL_ARTIFACT_SHOP_MARKER, "artifact_shop_marker"); this.spawnShopTitleDisplay(location, shop.shopId(), shop.title()); } } catch (Exception ignored) {} if (player.isOnline()) player.sendMessage(this.color("&aЛавка создана.")); });
         } catch (SQLException error) { this.getLogger().log(Level.WARNING, "Admin shop create failed", error); this.runSync(() -> { if (player.isOnline()) player.sendMessage(this.color("&cНе удалось создать лавку.")); }); }
      });
   }

   private void openAdminGiftPlayersAsync(Player player) {
      if (!this.hasArtifactPermission(player, "copimine.artifacts.admin.gift")) { this.noPermission(player); return; }
      List<CopiMineArtifacts.GiftTarget> onlineTargets = Bukkit.getOnlinePlayers().stream()
         .map(online -> new CopiMineArtifacts.GiftTarget(online.getUniqueId().toString(), online.getName()))
         .toList();
      this.runAsync(() -> {
         Map<String, CopiMineArtifacts.GiftTarget> targetsByUuid = new LinkedHashMap<>();
         Connection connection = null;
         try { connection = this.pgPool.acquire(); try (PreparedStatement ps = connection.prepareStatement("SELECT player_uuid,MAX(player_name) AS player_name FROM (SELECT player_uuid,player_name FROM artifact_purchases UNION SELECT player_uuid,player_name FROM donation_purchases UNION SELECT owner_uuid AS player_uuid,'' AS player_name FROM artifact_item_instances) known_players WHERE player_uuid<>'' GROUP BY player_uuid ORDER BY lower(MAX(player_name)),player_uuid LIMIT 250")) { try (ResultSet rs = ps.executeQuery()) { while (rs.next()) { String uuid = rs.getString(1); targetsByUuid.put(uuid, new CopiMineArtifacts.GiftTarget(uuid, this.firstNonBlank(rs.getString(2), "Игрок"))); } } } }
         catch (SQLException error) { this.getLogger().log(Level.WARNING, "Admin gift player lookup failed", error); }
         finally { if (connection != null) this.pgPool.release(connection); }
         for (CopiMineArtifacts.GiftTarget target : onlineTargets) {
            targetsByUuid.put(target.uuid(), target);
         }
         List<CopiMineArtifacts.GiftTarget> targets = new ArrayList<>(targetsByUuid.values());
         targets.sort((left, right) -> {
            int byName = left.name().compareToIgnoreCase(right.name());
            return byName != 0 ? byName : left.uuid().compareTo(right.uuid());
         });
         this.runSync(() -> this.openAdminGiftPlayers(player, targets));
      });
   }

   private void openAdminGiftPlayers(Player player, List<CopiMineArtifacts.GiftTarget> targets) {
      CopiMineArtifacts.SessionState state = this.session(player); Inventory menu = this.createMenu(player, state, CopiMineArtifacts.ViewType.ADMIN_GIFT_PLAYERS, 54, "&8Выдать предмет");
      int slot = 0; for (CopiMineArtifacts.GiftTarget target : targets) { if (slot >= 45) break; this.setAction(menu, state, slot++, this.button(Material.PLAYER_HEAD, "&f" + target.name(), List.of("&7Выбрать игрока")), "admin_gift:player:" + target.uuid()); }
      this.setAction(menu, state, 49, this.button(Material.ARROW, GUI_BACK_LABEL, List.of()), "admin:shops"); player.openInventory(menu);
   }

   private void openAdminGiftCatalog(Player player, String kind) {
      CopiMineArtifacts.SessionState state = this.session(player); state.giftKind = kind; Inventory menu = this.createMenu(player, state, CopiMineArtifacts.ViewType.ADMIN_GIFT_CATALOG, 54, "&8Выбор предмета");
      int slot = 0; boolean adminGift = true;
       if ("AR".equals(kind)) for (CopiMineArtifacts.CatalogItem item : this.catalogById.values()) { if (slot >= 45) break; if (adminGift && !this.isAdminOnlyCatalogItem(item.itemId())) this.setAction(menu, state, slot++, this.button(item.material(), item.name(), List.of("&7Выдать один экземпляр")), "admin_gift:item:" + item.itemId()); }
       else if ("HIDDEN".equals(kind)) for (CopiMineArtifacts.CatalogItem item : this.catalogById.values()) { if (slot >= 45) break; if (this.isAdminOnlyCatalogItem(item.itemId())) this.setAction(menu, state, slot++, this.button(item.material(), item.name(), List.of("&7Выдать один экземпляр")), "admin_gift:item:" + item.itemId()); }
       else for (CopiMineArtifacts.DonationCatalogItem item : this.donationCatalogById.values()) { if (slot >= 45) break; if (item.enabled() || adminGift) this.setAction(menu, state, slot++, this.button(item.baseMaterial(), item.displayName(), List.of("&7Выдать один экземпляр")), "admin_gift:item:" + item.itemId()); }
      this.setAction(menu, state, 49, this.button(Material.ARROW, GUI_BACK_LABEL, List.of()), "admin_gift:kind"); player.openInventory(menu);
   }

   private void openAdminGiftConfirm(Player player) {
      CopiMineArtifacts.SessionState state = this.session(player); CopiMineArtifacts.CatalogItem item = this.runtimeCatalogItem(state.currentItemId); if (item == null) { player.sendMessage(this.color("&cПредмет больше недоступен.")); return; }
      Inventory menu = this.createMenu(player, state, CopiMineArtifacts.ViewType.ADMIN_GIFT_CONFIRM, 27, "&8Подтверждение выдачи");
      menu.setItem(13, this.button(item.material(), item.name(), List.of("&7Выдать игроку: &f" + state.giftTargetName)));
      this.setAction(menu, state, 11, this.button(Material.LIME_WOOL, "&aДа", List.of()), "admin_gift:confirm"); this.setAction(menu, state, 15, this.button(Material.RED_WOOL, "&cНет", List.of()), "admin_gift:kind"); player.openInventory(menu);
   }

   private void openAdminShopListAsync(Player player) {
      this.runAsync(() -> {
         List<CopiMineArtifacts.ShopStats> stats = new ArrayList<>(); Connection connection = null;
         try { connection = this.pgPool.acquire(); for (CopiMineArtifacts.Shop shop : this.shopsByLocation.values()) {
            long buyers = 0L, ar = 0L;
            try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(DISTINCT player_uuid),COALESCE(SUM(price_ar),0) FROM artifact_purchases WHERE shop_id=? AND status IN ('PAID','DELIVERING','DELIVERED','PENDING_DELIVERY') AND bank_tx_id<>'ADMIN_GIFT'")) { ps.setString(1, shop.shopId()); try (ResultSet rs=ps.executeQuery()) { if(rs.next()){ buyers=rs.getLong(1); ar=rs.getLong(2); } } }
            stats.add(new CopiMineArtifacts.ShopStats(shop, buyers, ar, 0L));
         }} catch (SQLException error) { this.getLogger().log(Level.WARNING, "Admin shop statistics lookup failed", error); }
         finally { if (connection != null) this.pgPool.release(connection); }
         this.runSync(() -> { CopiMineArtifacts.SessionState state=this.session(player); Inventory menu=this.createMenu(player,state,CopiMineArtifacts.ViewType.ADMIN_SHOPS,54,"&8Все лавки"); int slot=0; for(CopiMineArtifacts.ShopStats stat:stats){ if(slot>=45)break; CopiMineArtifacts.Shop shop=stat.shop(); this.setAction(menu,state,slot++,this.button(Material.BARREL,"&f"+shop.title(),List.of("&7ID: &f"+shop.shopId(),"&7Мир: &f"+shop.world(),"&7Координаты: &f"+shop.x()+" "+shop.y()+" "+shop.z(),"&7Покупатели: &f"+stat.buyers(),"&7Оборот АР: &f"+stat.arTurnover(),"&7Оборот донат: &f"+stat.donationTurnover())),"shop:detail:"+shop.shopId()); } this.setAction(menu,state,49,this.button(Material.ARROW,GUI_BACK_LABEL,List.of()),"admin:shops"); player.openInventory(menu); });
      });
   }

   private boolean hasSeniorShopPermission(Player player) {
      return player != null
         && !this.isRestrictedJuniorArtifactsAdmin(player)
         && (this.isArtifactsAdmin(player) || player.hasPermission("copimine.artifacts.shop.remove"));
   }

   private void removeShopWithConfirmation(Player player, CopiMineArtifacts.Shop shop) {
      if (!this.hasSeniorShopPermission(player)) { this.noPermission(player); return; }
      this.removeShopWithCleanup(player, shop);
   }

   private void openAdminCatalog(Player var1) {
      CopiMineArtifacts.SessionState var2 = this.session(var1);
      var2.viewType = CopiMineArtifacts.ViewType.ADMIN_CATALOG;
      Inventory var3 = this.createMenu(
         var1,
         var2,
         CopiMineArtifacts.ViewType.ADMIN_CATALOG,
         27,
         "&8Каталог артефактов"
      );
      var3.setItem(
         4,
         this.button(
            Material.BOOK,
            "&eКаталог",
            List.of(
               "&7Активных товаров: &f"
                  + this.catalogById.size()
            )
         )
      );
      this.setAction(var3, var2, 10, this.categoryIcon(CopiMineArtifacts.Category.WEAPON), "cat:WEAPON");
      this.setAction(var3, var2, 12, this.categoryIcon(CopiMineArtifacts.Category.ARMOR), "cat:ARMOR");
      this.setAction(var3, var2, 14, this.categoryIcon(CopiMineArtifacts.Category.TOOL), "cat:TOOL");
      var3.setItem(
         16,
         this.button(
            Material.BOOK,
            "&eОфициальные модели",
            List.of(
               "&7Ресурс-пак сервера меняет внешний вид официальных предметов."
            )
         )
      );
      this.setAction(
         var3,
         var2,
         22,
         this.button(
            Material.ARROW,
            "&aНазад",
            List.of(
               "&7Вернуться в админ-меню."
            )
         ),
         "admin:main"
      );
      var1.openInventory(var3);
   }

   private void openAdminDiagnostics(Player var1) {
      CopiMineArtifacts.SessionState var2 = this.session(var1);
      var2.viewType = CopiMineArtifacts.ViewType.ADMIN_DIAGNOSTICS;
      Inventory var3 = this.createMenu(
         var1,
         var2,
         CopiMineArtifacts.ViewType.ADMIN_DIAGNOSTICS,
         27,
         "&8Диагностика артефактов"
      );
      CopiMineArtifacts.BridgeHealthSnapshot var4 = new CopiMineArtifacts.BridgeHealthSnapshot(
         false, false, false, 0L, "artifacts-admin-gui", "LEGACY_REDIRECT"
      );
      var3.setItem(
         10,
         this.button(
            var4.bridgeReady() ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
            "&eBridge",
            List.of("&7Готов: &f" + var4.bridgeReady())
         )
      );
      var3.setItem(
         12,
         this.button(
            var4.postgresReady() ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
            "&ePostgreSQL",
            List.of("&7Готов: &f" + var4.postgresReady())
         )
      );
      var3.setItem(
         14,
         this.button(
            var4.pinReady() ? Material.LIME_CONCRETE : Material.ORANGE_CONCRETE,
            "&ePIN",
            List.of("&7Готов: &f" + var4.pinReady())
         )
      );
      var3.setItem(
         16,
         this.button(
            Material.CLOCK,
            "&eTasks",
            List.of(
               "&7Pending hints: &f1 раз в минуту",
               "&7Session cleanup: &f1 раз в минуту",
               "&7Every tick задач лавки нет"
            )
         )
      );
      this.setAction(
         var3,
         var2,
         22,
         this.button(
            Material.ARROW,
            "&aНазад",
            List.of(
               "&7Вернуться в админ-меню."
            )
         ),
         "admin:main"
      );
      var1.openInventory(var3);
   }

   private void openAdminDiagnosticsAsync(Player var1) {
      CopiMineArtifacts.SessionState var2 = this.session(var1);
      var2.viewType = CopiMineArtifacts.ViewType.ADMIN_DIAGNOSTICS;
      UUID var3 = var1.getUniqueId();
      this.runAsync(() -> {
         CopiMineArtifacts.BridgeHealthSnapshot var3x = this.bridge.health(var3, "artifacts-admin-gui");
         this.runSync(() -> {
            if (var1.isOnline()) {
               this.openAdminDiagnosticsMenu(var1, var3x);
            }
         });
      });
   }

   private void openAdminDiagnosticsMenu(Player var1, CopiMineArtifacts.BridgeHealthSnapshot var2) {
      CopiMineArtifacts.SessionState var3 = this.session(var1);
      var3.viewType = CopiMineArtifacts.ViewType.ADMIN_DIAGNOSTICS;
      Inventory var4 = this.createMenu(
         var1,
         var3,
         CopiMineArtifacts.ViewType.ADMIN_DIAGNOSTICS,
         27,
         "&8Диагностика артефактов"
      );
      var4.setItem(
         10,
         this.button(
            var2.bridgeReady() ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
            "&eBridge",
            List.of("&7Готов: &f" + var2.bridgeReady())
         )
      );
      var4.setItem(
         12,
         this.button(
            var2.postgresReady() ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
            "&ePostgreSQL",
            List.of("&7Готов: &f" + var2.postgresReady())
         )
      );
      var4.setItem(
         14,
         this.button(
            var2.pinReady() ? Material.LIME_CONCRETE : Material.ORANGE_CONCRETE,
            "&ePIN",
            List.of("&7Готов: &f" + var2.pinReady())
         )
      );
      var4.setItem(
         16,
         this.button(
            Material.CLOCK,
            "&eTasks",
            List.of(
               "&7Pending hints: &f1 раз в минуту",
               "&7Session cleanup: &f1 раз в минуту",
               "&7Every tick задач лавки нет"
            )
         )
      );
      this.setAction(
         var4,
         var3,
         22,
         this.button(
            Material.ARROW,
            "&aНазад",
            List.of(
               "&7Вернуться в админ-меню."
            )
         ),
         "admin:main"
      );
      var1.openInventory(var4);
   }

   public List<String> onTabComplete(CommandSender var1, Command var2, String var3, String[] var4) {
      if ("setprice".equalsIgnoreCase(var2.getName())) {
         if (var4.length == 1) {
            return this.prefix(new ArrayList<>(this.catalogById.keySet()), var4[0]);
         }

         if (var4.length == 2) {
            return this.prefix(List.of("ar", "donation"), var4[1]);
         }

         if (var4.length == 3 && ("ar".equalsIgnoreCase(var4[1]) || "donation".equalsIgnoreCase(var4[1]))) {
            return List.of("<price>");
         }

         return Collections.emptyList();
      }

      if (var4.length == 1) {
         return this.prefix(List.of("admin", "claim", "repair", "reload", "shop"), var4[0]);
      } else {
         if (var4.length == 2 && "admin".equalsIgnoreCase(var4[0])) {
            return this.prefix(List.of("give"), var4[1]);
         }
         if (var4.length == 3 && "admin".equalsIgnoreCase(var4[0]) && "give".equalsIgnoreCase(var4[1])) {
            return this.prefix(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), var4[2]);
         }
         if (var4.length == 4 && "admin".equalsIgnoreCase(var4[0]) && "give".equalsIgnoreCase(var4[1])) {
            return this.prefix(new ArrayList<>(this.adminOnlyCatalogItems), var4[3]);
         }
         return var4.length == 2 && "shop".equalsIgnoreCase(var4[0])
            ? this.prefix(List.of("create", "remove", "list", "open"), var4[1])
            : Collections.emptyList();
      }
   }

   private void openMain(Player var1, CopiMineArtifacts.Shop var2) {
      this.openMain(var1, var2, false);
   }

   private void openMain(Player var1, CopiMineArtifacts.Shop var2, boolean var3) {
      if (this.useDonationShopV2Menus()) {
         this.openMainV2(var1, var2, var3);
      } else {
         CopiMineArtifacts.SessionState var4 = var3 ? this.freshSession(var1) : this.session(var1);
         var4.shopId = var2.shopId();
         var4.viewType = CopiMineArtifacts.ViewType.MAIN;
         var4.currentCategory = "";
         var4.currentItemId = "";
         var4.page = 0;
         Inventory var5 = this.createMenu(
            var1,
            var4,
            CopiMineArtifacts.ViewType.MAIN,
            54,
            "&8Лавка артефактов"
         );
         var5.setItem(
            4,
            this.button(
               Material.EMERALD,
               "&6" + var2.title(),
               List.of(
                  "&7Официальные предметы CopiMine.",
                  "&7Оплата идёт только через банк AR.",
                  "&8PIN и баланс проверяет UltimateAdminPlus."
               )
            )
         );
         this.setAction(var5, var4, 10, this.categoryIcon(CopiMineArtifacts.Category.WEAPON), "cat:WEAPON");
         this.setAction(var5, var4, 12, this.categoryIcon(CopiMineArtifacts.Category.ARMOR), "cat:ARMOR");
         this.setAction(var5, var4, 14, this.categoryIcon(CopiMineArtifacts.Category.TOOL), "cat:TOOL");
         var5.setItem(
            16,
            this.button(
               Material.BOOK,
               "&eОфициальные модели",
               List.of(
                  "&7Ресурс-пак сервера меняет внешний вид официальных предметов."
               )
            )
         );
         this.setAction(
            var5,
            var4,
            28,
            this.button(
               Material.CHEST,
               "&bМои покупки",
               List.of(
                  "&7История последних покупок.",
                  "&7Оплаченные предметы и статусы выдачи."
               )
            ),
            "purchases"
         );
         this.setAction(
            var5,
            var4,
            30,
            this.button(
               Material.CHEST_MINECART,
               "&aОтложенная выдача",
               List.of(
                  "&7Предметы, которые не поместились в инвентарь.",
                  "&eНажми, когда освободишь место."
               )
            ),
            "pending"
         );
         this.setAction(
            var5,
            var4,
            32,
            this.button(
               Material.ANVIL,
               "&aРемонт",
               List.of(
                  "&7Починить официальный предмет в руке.",
                  "&7Стоимость зависит от износа."
               )
            ),
            "repair:open"
         );
         this.setAction(
            var5,
            var4,
            34,
            this.button(
               Material.BOOK,
               "&eПомощь",
               List.of(
                  "&7Короткая инструкция по покупке, PIN и выдаче."
               )
            ),
            "help"
         );
         this.setAction(
            var5,
            var4,
            45,
            this.button(
               Material.ARROW,
               "&aНазад",
               List.of(
                  "&7Вернуться в игру."
               )
            ),
            "close"
         );
         this.setAction(
            var5,
            var4,
            49,
            this.button(
               Material.CLOCK,
               "&eОбновить",
               List.of(
                  "&7Перерисовать текущее меню."
               )
            ),
            "refresh"
         );
         this.setAction(
            var5,
            var4,
            53,
            this.button(
               Material.BARRIER,
               "&cЗакрыть",
               List.of(
                  "&7Выйти из лавки."
               )
            ),
            "close"
         );
         var1.openInventory(var5);
      }
   }

   private void openCategory(Player var1, CopiMineArtifacts.Category var2) {
      this.openCategory(var1, var2, 0);
   }

   private boolean useDonationShopV2Menus() {
      return true;
   }

   private void openArtifactsAdminMenuV2(Player var1) {
      CopiMineArtifacts.SessionState var2 = this.freshSession(var1);
      var2.viewType = CopiMineArtifacts.ViewType.ADMIN_MAIN;
      Inventory var3 = this.createMenu(var1, var2, CopiMineArtifacts.ViewType.ADMIN_MAIN, 27, "&8Artifacts Admin");
      var3.setItem(
         4,
         this.button(
            Material.NETHER_STAR,
            "&aCopiMineArtifacts",
            List.of(
               "&7Служебная панель лавок и каталога.",
               "&8Игроки открывают лавку только кликом по блоку."
            )
         )
      );
      this.setAction(
         var3,
         var2,
         10,
         this.button(
            Material.CHEST,
            "&eЛавки",
            List.of(
               "&7Список активных блоков лавки."
            )
         ),
         "admin:shops"
      );
      this.setAction(
         var3,
         var2,
         12,
         this.button(
            Material.BOOK,
            "&eКаталог",
            List.of(
               "&7Количество товаров по категориям."
            )
         ),
         "admin:catalog"
      );
      this.setAction(
         var3,
         var2,
         14,
         this.button(
            Material.COMPARATOR,
            "&eДиагностика",
            List.of("&7Bridge, PostgreSQL, PIN и кэш.")
         ),
         "admin:diagnostics"
      );
      this.setAction(
         var3,
         var2,
         16,
         this.button(
            Material.NETHER_STAR,
            "&dДонатная лавка",
            List.of(
               "&7Привязанные к владельцу предметы и платёжные сессии.",
               "&7Покупка идёт на сайте, а выдача и возврат доступны только в игре."
            )
         ),
         "donation:root"
      );
      this.setAction(
         var3,
         var2,
         20,
         this.button(
            Material.CLOCK,
            "&aReload",
            List.of(
               "&7Перезагрузить config.yml и items.yml."
            )
         ),
         "admin:reload"
      );
      this.setAction(
         var3,
         var2,
         22,
         this.button(
            Material.BARRIER,
            "&cЗакрыть",
            List.of("&7Выйти из меню.")
         ),
         "close"
      );
      var1.openInventory(var3);
   }

   private void openMainV2(Player var1, CopiMineArtifacts.Shop var2, boolean var3) {
      CopiMineArtifacts.SessionState var4 = var3 ? this.freshSession(var1) : this.session(var1);
      var4.shopId = var2.shopId();
      var4.viewType = CopiMineArtifacts.ViewType.MAIN;
      var4.currentCategory = "";
      var4.currentItemId = "";
      var4.page = 0;
      Inventory var5 = this.createMenu(
         var1,
         var4,
         CopiMineArtifacts.ViewType.MAIN,
         54,
         "&8Лавка артефактов"
      );
      var5.setItem(
         4,
         this.button(
            Material.EMERALD,
            "&6" + var2.title(),
            List.of(
               "&7Официальные предметы CopiMine.",
               "&7Покупка обычных артефактов идёт только через банк AR.",
               "&8PIN и баланс проверяет CopiMineEconomyCore."
            )
         )
      );
      this.setAction(var5, var4, 10, this.categoryIcon(CopiMineArtifacts.Category.WEAPON), "cat:WEAPON");
      this.setAction(var5, var4, 12, this.categoryIcon(CopiMineArtifacts.Category.ARMOR), "cat:ARMOR");
      this.setAction(var5, var4, 14, this.categoryIcon(CopiMineArtifacts.Category.TOOL), "cat:TOOL");
      this.setAction(
         var5,
         var4,
         16,
         this.button(
            Material.NETHER_STAR,
            "&dДонатная лавка",
            List.of(
               "&7Пополнение, donation-баланс, claim и reclaim.",
               "&7Покупка донат-предметов списывается с Donation-баланса прямо в игре."
            )
         ),
         "donation:root"
      );
      this.setAction(
         var5,
         var4,
         28,
         this.button(
            Material.CHEST,
            "&bМои покупки",
            List.of(
               "&7История последних покупок.",
               "&7Оплаченные предметы и статусы выдачи."
            )
         ),
         "purchases"
      );
      this.setAction(
         var5,
         var4,
         30,
         this.button(
            Material.CHEST_MINECART,
            "&aОтложенная выдача",
            List.of(
               "&7Предметы, которые не поместились в инвентарь.",
               "&eНажми, когда освободишь место."
            )
         ),
         "pending"
      );
      this.setAction(
         var5,
         var4,
         32,
         this.button(
            Material.ANVIL,
            "&aРемонт",
            List.of(
               "&7Починить официальный предмет в руке.",
               "&7Стоимость зависит от износа."
            )
         ),
         "repair:open"
      );
         this.setAction(
            var5,
            var4,
            34,
         this.button(
            Material.BOOK,
            "&eПомощь",
            List.of(
               "&7Короткая инструкция по покупке, PIN и выдаче."
            )
            ),
            "help"
         );
      this.setAction(
         var5,
         var4,
         45,
         this.button(
            Material.ARROW,
            "&aНазад",
            List.of(
               "&7Вернуться в игру."
            )
         ),
         "close"
      );
      this.setAction(
         var5,
         var4,
         49,
         this.button(
            Material.CLOCK,
            "&eОбновить",
            List.of(
               "&7Перерисовать текущее меню."
            )
         ),
         "refresh"
      );
      this.setAction(
         var5,
         var4,
         53,
         this.button(
            Material.BARRIER,
            "&cЗакрыть",
            List.of(
               "&7Выйти из лавки."
            )
         ),
         "close"
      );
      var1.openInventory(var5);
   }

   private void openCategory(Player var1, CopiMineArtifacts.Category var2, int var3) {
      CopiMineArtifacts.SessionState var4 = this.session(var1);
      var4.viewType = CopiMineArtifacts.ViewType.CATEGORY;
      var4.currentCategory = var2.name();
      var4.currentItemId = "";
      var4.page = Math.max(0, var3);
      Inventory var5 = this.createMenu(var1, var4, CopiMineArtifacts.ViewType.CATEGORY, 54, "&8" + this.categoryTitle(var2));
      var5.setItem(
         4,
         this.button(
            this.categoryMaterial(var2),
            "&f" + this.categoryTitle(var2),
            List.of(
               this.categoryHint(var2),
               "&8Выбери предмет, чтобы открыть карточку."
            )
         )
      );
      List var6 = this.catalogByCategory.getOrDefault(var2, List.of());
      int[] var7 = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
      if (var2 != CopiMineArtifacts.Category.RP && !var6.isEmpty()) {
         int var8 = var4.page * var7.length;

         for (int var9 = 0; var9 < var7.length; var9++) {
            int var10 = var8 + var9;
            if (var10 >= var6.size()) {
               break;
            }

            CopiMineArtifacts.CatalogItem var11 = (CopiMineArtifacts.CatalogItem)var6.get(var10);
            this.setAction(var5, var4, var7[var9], this.previewIcon(var11), "detail:" + var11.itemId());
         }
      } else {
         var5.setItem(
            22,
            this.button(
               Material.PAPER,
               "&eПока пусто",
               List.of(
                  "&7Сейчас в этой категории нет доступных товаров."
               )
            )
         );
      }

      this.setAction(
         var5,
         var4,
         45,
         this.button(
            Material.ARROW,
            "&aНазад",
            List.of(
               "&7Вернуться в лавку."
            )
         ),
         "back:main"
      );
      this.setAction(
         var5,
         var4,
         48,
         this.button(
            Material.SPECTRAL_ARROW,
            "&eПредыдущая",
            List.of(
               "&7Предыдущая страница товаров."
            )
         ),
         "page:prev"
      );
      this.setAction(
         var5,
         var4,
         49,
         this.button(
            Material.CLOCK,
            "&eОбновить",
            List.of(
               "&7Обновить список товаров."
            )
         ),
         "refresh"
      );
      this.setAction(
         var5,
         var4,
         50,
         this.button(
            Material.SPECTRAL_ARROW,
            "&eСледующая",
            List.of(
               "&7Следующая страница товаров."
            )
         ),
         "page:next"
      );
      this.setAction(
         var5,
         var4,
         53,
         this.button(
            Material.BARRIER,
            "&cЗакрыть",
            List.of("&7Выйти из меню.")
         ),
         "close"
      );
      var1.openInventory(var5);
   }

   private void openDetail(Player var1, CopiMineArtifacts.CatalogItem var2) {
      CopiMineArtifacts.SessionState var3 = this.session(var1);
      var3.viewType = CopiMineArtifacts.ViewType.DETAIL;
      var3.currentItemId = var2.itemId();
      Inventory var4 = this.createMenu(
         var1,
         var3,
         CopiMineArtifacts.ViewType.DETAIL,
         45,
         "&8Карточка товара"
      );
      var4.setItem(13, this.previewIcon(var2));
      var4.setItem(
         20,
         this.button(
            Material.EMERALD,
            "&aЦена: " + var2.priceAr() + " AR",
            List.of(
               "&7Списание выполняется только через банковый сервис."
            )
         )
      );
      this.setAction(
         var4,
         var3,
         22,
         this.button(
            Material.LIME_WOOL,
            "&aКупить",
            List.of(
               "&7Открыть подтверждение покупки.",
               "&8Далее может потребоваться PIN."
            )
         ),
         "confirm:" + var2.itemId()
      );
      var4.setItem(
         24,
         this.button(
            Material.PAPER,
            "&eЛимиты",
            List.of(
               "&7На сервер: &f"
                  + (
                     var2.supplyLimit() <= 0
                        ? "без лимита"
                        : var2.supplyLimit()
                  ),
               "&7На игрока: &f"
                  + (
                     var2.perPlayerLimit() <= 0
                        ? "без лимита"
                        : var2.perPlayerLimit()
                  )
            )
         )
      );
      this.setAction(
         var4,
         var3,
         31,
         this.button(
            Material.ARROW,
            "&aНазад",
            List.of(
               "&7Вернуться в категорию."
            )
         ),
         "back:category"
      );
      this.setAction(
         var4,
         var3,
         40,
         this.button(
            Material.BARRIER,
            "&cЗакрыть",
            List.of("&7Выйти из меню.")
         ),
         "close"
      );
      var1.openInventory(var4);
   }

   private void openConfirm(Player var1, CopiMineArtifacts.CatalogItem var2) {
      CopiMineArtifacts.SessionState var3 = this.session(var1);
      var3.viewType = CopiMineArtifacts.ViewType.CONFIRM;
      var3.currentItemId = var2.itemId();
      // Pin entry can remain open while an administrator reloads the catalog.
      // Keep the price/generation that the player actually confirmed so the
      // charge path can reject a stale screen instead of charging a different
      // price than the one shown in the GUI.
      var3.confirmedPriceAr = var2.priceAr();
      var3.confirmedCatalogGeneration = this.catalogGeneration;
      Inventory var4 = this.createMenu(
         var1,
         var3,
         CopiMineArtifacts.ViewType.CONFIRM,
         45,
         "&8Подтверждение"
      );
      var4.setItem(11, this.previewIcon(var2));
      var4.setItem(
         13,
         this.button(
            Material.PAPER,
            "&fПроверка покупки",
            List.of(
               "&7Товар: &f" + this.strip(var2.name()),
               "&7Цена: &f" + var2.priceAr() + " AR",
               "&7Лавка: &f" + var3.shopId
            )
         )
      );
      var4.setItem(
         15,
         this.button(
            Material.GOLD_INGOT,
            "&eБанковый сервис",
            List.of(
               "&7Баланс и PIN проверяет CopiMineEconomyCore.",
               "&7Artifacts не меняет баланс напрямую."
            )
         )
      );
      this.setAction(
         var4,
         var3,
         29,
         this.button(
            Material.LIME_WOOL,
            "&aПодтвердить",
            List.of(
               "&7Списать: &f" + var2.priceAr() + " AR",
               "&8Покупка защищена от двойного клика."
            )
         ),
         "purchase:" + var2.itemId()
      );
      this.setAction(
         var4,
         var3,
         33,
         this.button(
            Material.RED_WOOL,
            "&cОтмена",
            List.of(
               "&7Вернуться к карточке товара."
            )
         ),
         "back:detail"
      );
      this.setAction(
         var4,
         var3,
         40,
         this.button(
            Material.ARROW,
            "&aНазад",
            List.of(
               "&7Вернуться к карточке товара."
            )
         ),
         "back:detail"
      );
      var1.openInventory(var4);
   }

   private void openPin(Player var1, CopiMineArtifacts.CatalogItem var2) {
      CopiMineArtifacts.SessionState var3 = this.session(var1);
      var3.viewType = CopiMineArtifacts.ViewType.PIN;
      if (!"repair".equals(var3.pinAction)) {
         var3.pinAction = "purchase";
      }
      var3.currentItemId = var2.itemId();
      Inventory var4 = this.createMenu(
         var1, var3, CopiMineArtifacts.ViewType.PIN, 54, "&8Введите PIN"
      );
      int[] slots = {20,21,22,29,30,31,38,39,40,48};

      for (int var6 = 1; var6 <= 9; var6++) {
         this.setAction(var4, var3, slots[var6 - 1], this.button(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "&f" + var6, List.of()), "digit:" + var6);
      }

      this.setAction(var4, var3, slots[9], this.button(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "&f0", List.of()), "digit:0");
      var4.setItem(
         13,
         this.button(
            Material.PAPER,
            "&bВведите PIN",
            List.of("&7" + this.maskedPin(var3.pinBuffer))
         )
      );
      this.setAction(
         var4,
         var3,
         23,
         this.button(
            Material.BARRIER,
            "&cCancel",
            List.of(
               "&7Вернуться к подтверждению."
            )
         ),
         "pin:cancel"
      );
      this.setAction(
         var4,
         var3,
         32,
         this.button(
            Material.YELLOW_WOOL,
            "&eClear",
            List.of(
               "&7Очистить введённый PIN."
            )
         ),
         "pin:clear"
      );
      this.setAction(
         var4,
         var3,
         41,
         this.button(Material.LIME_WOOL, "&aEnter", List.of("&7Купить " + var2.name())),
         "pin:submit"
      );
      var1.openInventory(var4);
   }

   private void refreshPin(Player var1) {
      CopiMineArtifacts.CatalogItem var2 = this.catalogById.get(this.session(var1).currentItemId);
      if (var2 != null) {
         this.openPin(var1, var2);
      }
   }

   private void openPurchases(Player var1) {
      CopiMineArtifacts.SessionState var2 = this.session(var1);
      var2.viewType = CopiMineArtifacts.ViewType.PURCHASES;
      Inventory var3 = this.createMenu(
         var1,
         var2,
         CopiMineArtifacts.ViewType.PURCHASES,
         54,
         "&8Мои покупки"
      );
      var3.setItem(
         4,
         this.button(
            Material.CHEST,
            "&bПокупки и выдача",
            List.of(
               "&7Новые предметы появляются сразу.",
               "&7Если инвентарь полный, товар ждёт здесь."
            )
         )
      );
      this.runAsync(
         () -> {
            List<String> var4 = this.readRecentPurchases(var1.getUniqueId().toString());
            List<CopiMineArtifacts.PendingDeliveryRow> var5 = this.readPending(var1.getUniqueId().toString());
            this.runSync(
               () -> {
                  int var6 = 10;

                  for (String var8 : var4) {
                     if (var6 >= 35) {
                        break;
                     }

                     var3.setItem(
                        var6,
                        this.button(
                           Material.PAPER, "&fПокупка", List.of("&7" + var8)
                        )
                     );
                     if (++var6 % 9 == 8) {
                        var6 += 2;
                     }
                  }

                  int var11 = 37;

                  for (CopiMineArtifacts.PendingDeliveryRow var9 : var5) {
                     if (var11 >= 44) {
                        break;
                     }

                     CopiMineArtifacts.CatalogItem var10 = this.runtimeCatalogItem(var9.itemId());
                     this.setAction(
                        var3,
                        var2,
                        var11,
                        this.button(
                           Material.CHEST_MINECART,
                           "&aЗабрать: "
                              + (var10 == null ? var9.itemId() : this.strip(var10.name())),
                           List.of(
                              "&7Отложенная выдача",
                              "&eНажми, когда в инвентаре есть место."
                           )
                        ),
                        "claim:" + var9.deliveryId()
                     );
                     var11++;
                  }

                  this.setAction(
                     var3,
                     var2,
                     45,
                     this.button(
                        Material.ARROW,
                        "&aНазад",
                        List.of(
                           "&7Вернуться в лавку."
                        )
                     ),
                     "back:main"
                  );
                  this.setAction(
                     var3,
                     var2,
                     49,
                     this.button(
                        Material.CLOCK,
                        "&eОбновить",
                        List.of(
                           "&7Обновить покупки и выдачу."
                        )
                     ),
                     "refresh"
                  );
                  this.setAction(
                     var3,
                     var2,
                     53,
                     this.button(
                        Material.BARRIER,
                        "&cЗакрыть",
                        List.of(
                           "&7Выйти из меню."
                        )
                     ),
                     "close"
                  );
                  var1.openInventory(var3);
               }
            );
         }
      );
   }

   private void openDonationRoot(Player var1) {
      CopiMineArtifacts.SessionState var2 = this.session(var1);
      Inventory var3 = this.createMenu(
         var1,
         var2,
         CopiMineArtifacts.ViewType.DONATION_ROOT,
         27,
         "&8Донатная лавка"
      );
      var3.setItem(
         4,
         this.button(
            Material.NETHER_STAR,
            "&dДонатная лавка",
            List.of(
               "&7Сайт отвечает за оплату и покупку.",
               "&7Игра отвечает за claim, reclaim и anti-dupe."
            )
         )
      );
      this.setAction(
         var3,
         var2,
         10,
         this.button(
            Material.BOOK,
            "&eКаталог",
            List.of(
               "&7Смотреть предметы, цены и статус покупки."
            )
         ),
         "donation:catalog"
      );
      this.setAction(
         var3,
         var2,
         12,
         this.button(
            Material.EMERALD,
            "&aДонат-баланс",
            List.of(
               "&7Баланс, provider и история пополнения."
            )
         ),
         "donation:balance"
      );
      this.setAction(
         var3,
         var2,
         14,
         this.button(
            Material.FILLED_MAP,
            "&bПополнить",
            List.of(
               "&7Создать payment session на 50 / 100 / 250 / 500 / 1000."
            )
         ),
         "donation:topup"
      );
      this.setAction(
         var3,
         var2,
         16,
         this.button(
            Material.CHEST,
            "&6Мои покупки",
            List.of(
               "&7Claims, active-экземпляры и pending delivery."
            )
         ),
         "donation:owned"
      );
      this.setAction(
         var3,
         var2,
         18,
         this.button(
            Material.ARROW,
            "&aНазад",
            List.of(
               "&7Вернуться в основную лавку."
            )
         ),
         "back:main"
      );
      this.setAction(
         var3,
         var2,
         26,
         this.button(
            Material.BARRIER,
            "&cЗакрыть",
            List.of(
               "&7Выйти из лавки."
            )
         ),
         "close"
      );
      var1.openInventory(var3);
   }

   private void openDonationBalance(Player var1) {
      DonationBalanceService var2 = this.donationBalanceService();
      if (var2 == null) {
         var1.sendMessage(
            this.color(
               "&cCopiMineEconomyCore недоступен. Donation-баланс сейчас не открыть."
            )
         );
      } else {
         var2.balanceAsync(var1.getUniqueId(), var1.getName())
            .whenComplete(
               (var2x, var3) -> this.runSync(
                     () -> {
                        if (var1.isOnline()) {
                           if (var3 != null) {
                              this.getLogger().log(Level.WARNING, "Donation balance fetch failed", var3);
                              var1.sendMessage(
                                 this.color(
                                    "&cНе удалось загрузить donation-баланс."
                                 )
                              );
                           } else {
                              CopiMineArtifacts.SessionState var4 = this.session(var1);
                              Inventory var5 = this.createMenu(
                                 var1,
                                 var4,
                                 CopiMineArtifacts.ViewType.DONATION_BALANCE,
                                 45,
                                 "&8Донат-баланс"
                              );
                              var5.setItem(
                                 4,
                                 this.button(
                                    Material.EMERALD,
                                    "&aБаланс: " + var2x + " Donation",
                                    List.of(
                                       "&7Donation не смешивается с AR.",
                                       "&7Курс фиксирован: 1 рубль = 1 Donation."
                                    )
                                 )
                              );
                              int[] var6 = new int[]{19, 20, 21, 22, 23};
                              List var7 = this.donationTopupEnabled() ? this.donationFixedPacks() : List.of();

                              for (int var8 = 0; var8 < Math.min(var6.length, var7.size()); var8++) {
                                 long var9 = (Long)var7.get(var8);
                                 this.setAction(
                                    var5,
                                    var4,
                                    var6[var8],
                                    this.button(
                                       Material.SUNFLOWER,
                                       "&eПополнить на "
                                          + var9,
                                       List.of(
                                          "&7Создать платёжную сессию.",
                                          "&7После этого откроется сайт оплаты."
                                       )
                                    ),
                                    "donation:topup:create:" + var9
                                 );
                              }

                              var5.setItem(
                                 13,
                                 this.button(
                                    Material.PAPER,
                                    "&fКак это работает",
                                    List.of(
                                       "&71. Создай payment session.",
                                       "&72. Открой сайт или QR fallback.",
                                       "&73. Баланс изменится только после статуса PAID."
                                    )
                                 )
                              );
                              if (!this.donationTopupEnabled()) {
                                 var5.setItem(
                                    13,
                                    this.button(
                                       Material.BARRIER,
                                       "&cПополнение отключено",
                                       List.of("&7Покупки доступны за уже имеющийся Donation-баланс.")
                                    )
                                 );
                              }
                              var5.setItem(
                                 31,
                                 this.button(
                                    Material.BOOK,
                                    "&bFallback",
                                    List.of(
                                       "&7" + this.donationQrFallbackMessage(),
                                       "&7Сайт оплаты: &f"
                                          + this.donationWebsiteBaseUrl()
                                    )
                                 )
                              );
                              this.setAction(
                                 var5,
                                 var4,
                                 36,
                                 this.button(
                                    Material.ARROW,
                                    "&aНазад",
                                    List.of(
                                       "&7Вернуться в donation-меню."
                                    )
                                 ),
                                 "donation:root"
                              );
                              this.setAction(
                                 var5,
                                 var4,
                                 40,
                                 this.button(
                                    Material.CLOCK,
                                    "&eОбновить",
                                    List.of(
                                       "&7Перечитать баланс и provider status."
                                    )
                                 ),
                                 "donation:balance"
                              );
                              this.setAction(
                                 var5,
                                 var4,
                                 44,
                                 this.button(
                                    Material.BARRIER,
                                    "&cЗакрыть",
                                    List.of(
                                       "&7Выйти из меню."
                                    )
                                 ),
                                 "close"
                              );
                              var1.openInventory(var5);
                           }
                        }
                     }
                  )
            );
      }
   }

   private void openDonationCatalog(Player var1) {
      this.runAsync(
         () -> {
            CopiMineArtifacts.DonationOwnershipSnapshot var2 = this.readDonationOwnershipSnapshot(var1.getUniqueId().toString());
            List var3 = this.donationCatalogById.values().stream().filter(CopiMineArtifacts.DonationCatalogItem::enabled).sorted((var0, var1xx) -> {
               int var2x = Long.compare(var0.priceDonation(), var1xx.priceDonation());
               return var2x != 0 ? var2x : var0.displayName().compareToIgnoreCase(var1xx.displayName());
            }).toList();
            this.runSync(
               () -> {
                  if (var1.isOnline()) {
                     CopiMineArtifacts.SessionState var4 = this.session(var1);
                     Inventory var5 = this.createMenu(
                        var1,
                        var4,
                        CopiMineArtifacts.ViewType.DONATION_CATALOG,
                        54,
                        "&8Каталог доната"
                     );
                     var5.setItem(
                        4,
                        this.button(
                           Material.NETHER_STAR,
                           "&dКаталог",
                           List.of(
                              "&7Покупка списывает средства с Donation-баланса.",
                              "&7После оплаты предмет появится в отложенной выдаче.",
                              "&7Нужен привязанный активный AR-банк."
                           )
                        )
                     );
                     int[] var6 = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

                     for (int var7 = 0; var7 < Math.min(var6.length, var3.size()); var7++) {
                        CopiMineArtifacts.DonationCatalogItem var8 = (CopiMineArtifacts.DonationCatalogItem)var3.get(var7);
                        CopiMineArtifacts.CatalogItem var9 = this.catalogById.get(var8.itemId());
                        if (var9 != null) {
                           String var10 = var2.activeItemIds().contains(var8.itemId())
                              ? "&aУже у тебя"
                              : (
                                 var2.claimableItemIds().contains(var8.itemId())
                                    ? "&eМожно забрать"
                                    : "&7Не куплен"
                              );
                           this.setAction(
                              var5,
                              var4,
                              var6[var7],
                              this.button(
                                 var9.material(),
                                 "&f" + var8.displayName(),
                                 List.of(
                                    "&7Цена: &f" + var8.priceDonation() + " Donation",
                                    "&7Эффект: &f"
                                       + this.firstNonBlank(
                                          var8.effectDescription(),
                                          "описание появится позже"
                                       ),
                                    "&7Кулдаун: &f"
                                       + Math.max(0, var8.cooldownSeconds())
                                       + " сек.",
                                    "&7Статус: " + var10,
                                    "&8Нажми, чтобы купить за Donation-баланс."
                                 )
                              ),
                              "donation:catalog:item:" + var8.itemId()
                           );
                        }
                     }

                     this.setAction(
                        var5,
                        var4,
                        45,
                        this.button(
                           Material.ARROW,
                           "&aНазад",
                           List.of(
                              "&7Вернуться в donation-меню."
                           )
                        ),
                        "donation:root"
                     );
                     this.setAction(
                        var5,
                        var4,
                        49,
                        this.button(
                           Material.CLOCK,
                           "&eОбновить",
                           List.of(
                              "&7Перечитать ownership status."
                           )
                        ),
                        "donation:catalog"
                     );
                     this.setAction(
                        var5,
                        var4,
                        53,
                        this.button(
                           Material.BARRIER,
                           "&cЗакрыть",
                           List.of(
                              "&7Выйти из меню."
                           )
                        ),
                        "close"
                     );
                     var1.openInventory(var5);
                  }
               }
            );
         }
      );
   }

   private void openDonationOwnedClean(Player var1) {
      this.readDonationClaimsAsync(var1.getUniqueId())
         .whenComplete(
            (var2, var3) -> {
               if (var3 != null) {
                  this.runSync(
                     () -> {
                        if (var1.isOnline()) {
                           var1.sendMessage(
                              this.color(
                                 "&cНе удалось загрузить донат-покупки и заявки на выдачу."
                              )
                           );
                        }
                     }
                  );
               } else {
                  this.runAsync(
                     () -> {
                        CopiMineArtifacts.DonationOwnershipSnapshot var3x = this.readDonationOwnershipSnapshot(var1.getUniqueId().toString());
                        this.runSync(
                           () -> {
                              if (var1.isOnline()) {
                                 CopiMineArtifacts.SessionState var4 = this.session(var1);
                                 Inventory var5 = this.createMenu(
                                    var1,
                                    var4,
                                    CopiMineArtifacts.ViewType.DONATION_OWNED,
                                    54,
                                    "&8Мои донат-предметы"
                                 );
                                 var5.setItem(
                                    4,
                                    this.button(
                                       Material.CHEST,
                                       "&6Мои покупки",
                                       List.of(
                                          "&7Здесь собраны заявки на выдачу и активные предметы",
                                          "&7и выдачи, которые ушли на ручную проверку.",
                                          "&7Физическая выдача идёт только здесь, в игре."
                                       )
                                    )
                                 );
                                 int var6 = 10;

                                 for (CopiMineArtifacts.DonationClaimRow var8 : var2 == null ? List.<CopiMineArtifacts.DonationClaimRow>of() : var2) {
                                    if (var6 >= 35) {
                                       break;
                                    }

                                    CopiMineArtifacts.DonationCatalogItem var9 = this.donationCatalogItem(var8.itemId());
                                    String var10 = var9 == null ? var8.itemId() : var9.displayName();
                                    String var11 = this.firstNonBlank(var8.status(), "UNCLAIMED");
                                    if (this.donationClaimReady(var11)) {
                                       this.setAction(
                                          var5,
                                          var4,
                                          var6,
                                          this.button(
                                             Material.CHEST_MINECART,
                                             "&aЗабрать: " + var10,
                                             List.of(
                                                "&7Заявка на выдачу: &f" + var8.claimId(),
                                                "&7Количество: &f"
                                                   + var8.amount(),
                                                "&8Нажми, когда в инвентаре есть свободное место."
                                             )
                                          ),
                                          "claim:" + var8.claimId()
                                       );
                                    } else {
                                       var5.setItem(
                                          var6,
                                          this.button(
                                             Material.CLOCK,
                                             "&eВ обработке: "
                                                + var10,
                                             List.of(
                                                "&7Заявка на выдачу: &f" + var8.claimId(),
                                                "&7Статус: &f"
                                                   + this.donationClaimStatusLabelSafe(var11),
                                                "&7Выдача уже идёт или отправлена на ручную проверку."
                                             )
                                          )
                                       );
                                    }

                                    if (++var6 % 9 == 8) {
                                       var6 += 2;
                                    }
                                 }

                                 int var12 = 37;

                                 for (String var15 : var3x.activeItemIds()) {
                                    if (var12 >= 44) {
                                       break;
                                    }

                                    CopiMineArtifacts.DonationCatalogItem var16 = this.donationCatalogItem(var15);
                                    var5.setItem(
                                       var12++,
                                       this.button(
                                          Material.LIME_CONCRETE,
                                          "&aАктивен: "
                                             + (var16 == null ? var15 : var16.displayName()),
                                          List.of(
                                             "&7Этот предмет уже выдан и закреплён за тобой."
                                          )
                                       )
                                    );
                                 }

                                 if ((var2 == null || var2.isEmpty()) && var3x.activeItemIds().isEmpty()) {
                                    String var14 = var3x.claimPendingCount() > 0
                                       ? "&7Есть заявки в обработке. Обнови экран чуть позже."
                                       : "&7На сайте пока нет заявок на выдачу.";
                                    var5.setItem(
                                       22,
                                       this.button(
                                          Material.PAPER,
                                          "&eПока пусто",
                                          List.of(var14)
                                       )
                                    );
                                 }

                                 this.setAction(
                                    var5,
                                    var4,
                                    45,
                                    this.button(
                                       Material.ARROW,
                                       "&aНазад",
                                       List.of(
                                          "&7Вернуться в donation-меню."
                                       )
                                    ),
                                    "donation:root"
                                 );
                                 this.setAction(
                                    var5,
                                    var4,
                                    49,
                                    this.button(
                                       Material.CLOCK,
                                       "&eОбновить",
                                       List.of(
                                          "&7Обновить заявки на выдачу и активные предметы."
                                       )
                                    ),
                                    "donation:owned"
                                 );
                                 this.setAction(
                                    var5,
                                    var4,
                                    53,
                                    this.button(
                                       Material.BARRIER,
                                       "&cЗакрыть",
                                       List.of(
                                          "&7Выйти из меню."
                                       )
                                    ),
                                    "close"
                                 );
                                 var1.openInventory(var5);
                              }
                           }
                        );
                     }
                  );
               }
            }
         );
   }

   private String donationClaimStatusLabelSafe(String var1) {
      String var2 = this.firstNonBlank(var1, "UNCLAIMED").toUpperCase(Locale.ROOT);

      return switch (var2) {
         case "UNCLAIMED" -> "Можно забрать";
         case "RESERVED", "DELIVERING" -> "Выдача обрабатывается";
         case "DELIVERY_REVIEW" -> "Ручная проверка";
         default -> var2;
      };
   }

   private void openDonationOwned(Player var1) {
      if (this.dbExecutor != null && this.donationLossJournalPath != null) {
         this.openDonationOwnedClean(var1);
      } else if (this.useDonationShopV2Menus()) {
         this.openDonationOwnedV2(var1);
      } else {
         this.readDonationClaimsAsync(var1.getUniqueId())
            .whenComplete(
               (var2, var3) -> {
                  if (var3 != null) {
                     this.runSync(
                        () -> {
                           if (var1.isOnline()) {
                              var1.sendMessage(
                                 this.color(
                                    "&cНе удалось загрузить заявки на выдачу."
                                 )
                              );
                           }
                        }
                     );
                  } else {
                     this.runAsync(
                        () -> {
                           CopiMineArtifacts.DonationOwnershipSnapshot var3x = this.readDonationOwnershipSnapshot(var1.getUniqueId().toString());
                           this.runSync(
                              () -> {
                                 if (var1.isOnline()) {
                                    CopiMineArtifacts.SessionState var4 = this.session(var1);
                                    Inventory var5 = this.createMenu(
                                       var1,
                                       var4,
                                       CopiMineArtifacts.ViewType.DONATION_OWNED,
                                       54,
                                       "&8Мои донат-предметы"
                                    );
                                    var5.setItem(
                                       4,
                                       this.button(
                                          Material.CHEST,
                                          "&6Мои покупки",
                                          List.of(
                                             "&7Claims и active-экземпляры.",
                                             "&7Физическая выдача идёт только здесь, в игре."
                                          )
                                       )
                                    );
                                    int var6 = 10;

                                    for (CopiMineArtifacts.DonationClaimRow var8 : var2 == null ? List.<CopiMineArtifacts.DonationClaimRow>of() : var2) {
                                       if (var6 >= 35) {
                                          break;
                                       }

                                       CopiMineArtifacts.DonationCatalogItem var9 = this.donationCatalogItem(var8.itemId());
                                       String var10 = var9 == null ? var8.itemId() : var9.displayName();
                                       this.setAction(
                                          var5,
                                          var4,
                                          var6,
                                          this.button(
                                             Material.CHEST_MINECART,
                                             "&aЗабрать: " + var10,
                                             List.of(
                                                "&7Заявка на выдачу: &f" + var8.claimId(),
                                                "&7Количество: &f"
                                                   + var8.amount(),
                                                "&8Нажми, когда в инвентаре есть место."
                                             )
                                          ),
                                          "claim:" + var8.claimId()
                                       );
                                       if (++var6 % 9 == 8) {
                                          var6 += 2;
                                       }
                                    }

                                    int var11 = 37;

                                    for (String var13 : var3x.activeItemIds()) {
                                       if (var11 >= 44) {
                                          break;
                                       }

                                       CopiMineArtifacts.DonationCatalogItem var14 = this.donationCatalogItem(var13);
                                       var5.setItem(
                                          var11++,
                                          this.button(
                                             Material.LIME_CONCRETE,
                                             "&aАктивен: "
                                                + (var14 == null ? var13 : var14.displayName()),
                                             List.of(
                                                "&7Этот предмет уже выдан и закреплён за тобой."
                                             )
                                          )
                                       );
                                    }

                                    if ((var2 == null || var2.isEmpty()) && var3x.activeItemIds().isEmpty()) {
                                       var5.setItem(
                                          22,
                                          this.button(
                                             Material.PAPER,
                                             "&eПока пусто",
                                             List.of(
                                                "&7Сайт ещё не создал заявку на выдачу."
                                             )
                                          )
                                       );
                                    }

                                    this.setAction(
                                       var5,
                                       var4,
                                       45,
                                       this.button(
                                          Material.ARROW,
                                          "&aНазад",
                                          List.of(
                                             "&7Вернуться в donation-меню."
                                          )
                                       ),
                                       "donation:root"
                                    );
                                    this.setAction(
                                       var5,
                                       var4,
                                       49,
                                       this.button(
                                          Material.CLOCK,
                                          "&eОбновить",
                                          List.of(
                                             "&7Обновить заявки на выдачу и активные предметы."
                                          )
                                       ),
                                       "donation:owned"
                                    );
                                    this.setAction(
                                       var5,
                                       var4,
                                       53,
                                       this.button(
                                          Material.BARRIER,
                                          "&cЗакрыть",
                                          List.of(
                                             "&7Выйти из меню."
                                          )
                                       ),
                                       "close"
                                    );
                                    var1.openInventory(var5);
                                 }
                              }
                           );
                        }
                     );
                  }
               }
            );
      }
   }

   private void openDonationReclaim(Player var1) {
      if (this.customShopItemsAreVanilla()) {
         if (var1 != null && var1.isOnline()) {
            var1.sendMessage(this.color("&eПотерянные donation-предметы не восстанавливаются: предметы работают как обычные предметы."));
         }
         return;
      }
      this.runAsync(
         () -> {
            // The loss journal is written synchronously before this menu
            // query. A background flush can still race the instance
            // transaction, so retry the short read/apply cycle while the
            // journal file remains. This prevents the first menu opening
            // after a void, explosion, cactus, or creative/plugin removal
            // from showing a false empty state.
            List<CopiMineArtifacts.ReclaimableDonationRow> var2 = List.of();
            for (int attempt = 0; attempt < 3; attempt++) {
               try {
                  this.reconcileDonationLossJournal();
               } catch (Exception var10) {
                  this.getLogger().log(Level.WARNING, "Donation reclaim journal reconcile before menu failed", (Throwable)var10);
               }
               var2 = this.readReclaimableDonationRows(var1.getUniqueId().toString(), 21);
               boolean journalPending = this.donationLossJournalPath != null && Files.exists(this.donationLossJournalPath);
               if (!var2.isEmpty() || !journalPending || attempt == 2) {
                  break;
               }
               try {
                  Thread.sleep(75L);
               } catch (InterruptedException interrupted) {
                  Thread.currentThread().interrupt();
                  break;
               }
            }
            final List<CopiMineArtifacts.ReclaimableDonationRow> reclaimRows = var2;
            this.runSync(
               () -> {
                  if (var1.isOnline()) {
                     CopiMineArtifacts.SessionState var3 = this.session(var1);
                     Inventory var4 = this.createMenu(
                        var1,
                        var3,
                        CopiMineArtifacts.ViewType.DONATION_RECLAIM,
                        54,
                        "&8Вернуть утерянные предметы"
                     );
                     var4.setItem(
                        4,
                        this.button(
                           Material.RECOVERY_COMPASS,
                           "&dВернуть утерянные предметы",
                           List.of(
                              "&7Здесь показываются только предметы, которые можно вернуть.",
                              "&7Возврат идёт по одному предмету за раз."
                           )
                        )
                     );
                     int[] var5 = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

                     for (int var6 = 0; var6 < Math.min(var5.length, reclaimRows.size()); var6++) {
                         CopiMineArtifacts.ReclaimableDonationRow var7 = (CopiMineArtifacts.ReclaimableDonationRow)reclaimRows.get(var6);
                        CopiMineArtifacts.DonationCatalogItem var8 = this.donationCatalogItem(var7.itemId());
                        String var9 = var8 == null ? var7.itemId() : var8.displayName();
                        this.setAction(
                           var4,
                           var3,
                           var5[var6],
                           this.button(
                              Material.NETHER_STAR,
                              "&dВернуть: " + var9,
                              List.of(
                                 "&7Потерян: &f"
                                    + Instant.ofEpochMilli(var7.updatedAt()),
                                 "&8Старый экземпляр будет переведён в REPLACED_AFTER_LOSS."
                              )
                           ),
                           "donation:reclaim:item:" + var7.uniqueItemId()
                        );
                     }

                     if (reclaimRows.isEmpty()) {
                        var4.setItem(
                           22,
                           this.button(
                              Material.LIME_CONCRETE,
                              "&aНечего возвращать",
                              List.of(
                                 "&7Сейчас нет предметов, доступных к возврату."
                              )
                           )
                        );
                     }

                     this.setAction(
                        var4,
                        var3,
                        45,
                        this.button(
                           Material.ARROW,
                           "&aНазад",
                           List.of(
                              "&7Вернуться в donation-меню."
                           )
                        ),
                        "donation:root"
                     );
                     this.setAction(
                        var4,
                        var3,
                        49,
                        this.button(
                           Material.CLOCK,
                           "&eОбновить",
                           List.of(
                              "&7Перечитать lost items."
                           )
                        ),
                        "donation:reclaim"
                     );
                     this.setAction(
                        var4,
                        var3,
                        53,
                        this.button(
                           Material.BARRIER,
                           "&cЗакрыть",
                           List.of(
                              "&7Выйти из меню."
                           )
                        ),
                        "close"
                     );
                     var1.openInventory(var4);
                  }
               }
            );
         }
      );
   }

   private boolean donationClaimReady(String var1) {
      return "UNCLAIMED".equalsIgnoreCase(this.firstNonBlank(var1, ""));
   }

   private boolean donationClaimInProgress(String var1) {
      String var2 = this.firstNonBlank(var1, "").toUpperCase(Locale.ROOT);
      return Set.of("RESERVED", "DELIVERING", "DELIVERY_REVIEW").contains(var2);
   }

   private String donationClaimStatusLabel(String var1) {
      String var2 = this.firstNonBlank(var1, "UNCLAIMED").toUpperCase(Locale.ROOT);

      return switch (var2) {
         case "UNCLAIMED" -> "Можно забрать";
         case "RESERVED", "DELIVERING" -> "Выдача обрабатывается";
         case "DELIVERY_REVIEW" -> "Ручная проверка";
         default -> var2;
      };
   }

   private void openDonationOwnedV2(Player var1) {
      this.readDonationClaimsAsync(var1.getUniqueId())
         .whenComplete(
            (var2, var3) -> {
               if (var3 != null) {
                  this.runSync(
                     () -> {
                        if (var1.isOnline()) {
                           var1.sendMessage(
                              this.color(
                                 "&cНе удалось загрузить заявки на выдачу."
                              )
                           );
                        }
                     }
                  );
               } else {
                  this.runAsync(
                     () -> {
                        CopiMineArtifacts.DonationOwnershipSnapshot var3x = this.readDonationOwnershipSnapshot(var1.getUniqueId().toString());
                        this.runSync(
                           () -> {
                              if (var1.isOnline()) {
                                 CopiMineArtifacts.SessionState var4 = this.session(var1);
                                 Inventory var5 = this.createMenu(
                                    var1,
                                    var4,
                                    CopiMineArtifacts.ViewType.DONATION_OWNED,
                                    54,
                                    "&8Мои донат-предметы"
                                 );
                                 var5.setItem(
                                    4,
                                    this.button(
                                       Material.CHEST,
                                       "&6Мои покупки",
                                       List.of(
                                          "&7Claims, active-экземпляры и stuck-состояния.",
                                          "&7Физическая выдача идёт только здесь, в игре."
                                       )
                                    )
                                 );
                                 int var6 = 10;

                                 for (CopiMineArtifacts.DonationClaimRow var8 : var2 == null ? List.<CopiMineArtifacts.DonationClaimRow>of() : var2) {
                                    if (var6 >= 35) {
                                       break;
                                    }

                                    CopiMineArtifacts.DonationCatalogItem var9 = this.donationCatalogItem(var8.itemId());
                                    String var10 = var9 == null ? var8.itemId() : var9.displayName();
                                    String var11 = this.firstNonBlank(var8.status(), "UNCLAIMED");
                                    if (this.donationClaimReady(var11)) {
                                       this.setAction(
                                          var5,
                                          var4,
                                          var6,
                                          this.button(
                                             Material.CHEST_MINECART,
                                             "&aЗабрать: " + var10,
                                             List.of(
                                             "&7Заявка на выдачу: &f" + var8.claimId(),
                                                "&7Количество: &f"
                                                   + var8.amount(),
                                                "&8Нажми, когда в инвентаре есть место."
                                             )
                                          ),
                                          "claim:" + var8.claimId()
                                       );
                                    } else {
                                       var5.setItem(
                                          var6,
                                          this.button(
                                             Material.CLOCK,
                                             "&eВ обработке: "
                                                + var10,
                                             List.of(
                                             "&7Заявка на выдачу: &f" + var8.claimId(),
                                                "&7Статус: &f" + var11,
                                                "&7Выдача уже идёт или отправлена на ручную проверку."
                                             )
                                          )
                                       );
                                    }

                                    if (++var6 % 9 == 8) {
                                       var6 += 2;
                                    }
                                 }

                                 int var12 = 37;

                                 for (String var15 : var3x.activeItemIds()) {
                                    if (var12 >= 44) {
                                       break;
                                    }

                                    CopiMineArtifacts.DonationCatalogItem var16 = this.donationCatalogItem(var15);
                                    var5.setItem(
                                       var12++,
                                       this.button(
                                          Material.LIME_CONCRETE,
                                          "&aАктивен: "
                                             + (var16 == null ? var15 : var16.displayName()),
                                          List.of(
                                             "&7Этот предмет уже выдан и закреплён за тобой."
                                          )
                                       )
                                    );
                                 }

                                 if ((var2 == null || var2.isEmpty()) && var3x.activeItemIds().isEmpty()) {
                                    String var14 = var3x.claimPendingCount() > 0
                                       ? "&7Есть заявки в обработке. Обнови экран чуть позже."
                                       : "&7Сайт ещё не создал заявку на выдачу.";
                                    var5.setItem(
                                       22,
                                       this.button(
                                          Material.PAPER,
                                          "&eПока пусто",
                                          List.of(var14)
                                       )
                                    );
                                 }

                                 this.setAction(
                                    var5,
                                    var4,
                                    45,
                                    this.button(
                                       Material.ARROW,
                                       "&aНазад",
                                       List.of(
                                          "&7Вернуться в donation-меню."
                                       )
                                    ),
                                    "donation:root"
                                 );
                                 this.setAction(
                                    var5,
                                    var4,
                                    49,
                                    this.button(
                                       Material.CLOCK,
                                       "&eОбновить",
                                       List.of(
                                          "&7Обновить заявки на выдачу и активные предметы."
                                       )
                                    ),
                                    "donation:owned"
                                 );
                                 this.setAction(
                                    var5,
                                    var4,
                                    53,
                                    this.button(
                                       Material.BARRIER,
                                       "&cЗакрыть",
                                       List.of(
                                          "&7Выйти из меню."
                                       )
                                    ),
                                    "close"
                                 );
                                 var1.openInventory(var5);
                              }
                           }
                        );
                     }
                  );
               }
            }
         );
   }

   private void openPendingDeliveries(Player var1) {
      CopiMineArtifacts.SessionState var2 = this.session(var1);
      var2.viewType = CopiMineArtifacts.ViewType.PENDING_DELIVERY;
      Inventory var3 = this.createMenu(
         var1,
         var2,
         CopiMineArtifacts.ViewType.PENDING_DELIVERY,
         54,
         "&8Отложенная выдача"
      );
      var3.setItem(
         4,
         this.button(
            Material.CHEST_MINECART,
            "&aОтложенная выдача",
            List.of(
               "&7Здесь лежат оплаченные предметы,",
               "&7которые не поместились в инвентарь."
            )
         )
      );
      this.runAsync(
         () -> {
            List<CopiMineArtifacts.PendingDeliveryRow> var4 = this.readPending(var1.getUniqueId().toString());
            this.runSync(
               () -> {
                  int var5 = 10;

                  for (CopiMineArtifacts.PendingDeliveryRow var7 : var4) {
                     if (var5 >= 35) {
                        break;
                     }

                     CopiMineArtifacts.CatalogItem var8 = this.runtimeCatalogItem(var7.itemId());
                     this.setAction(
                        var3,
                        var2,
                        var5,
                        this.button(
                           Material.CHEST_MINECART,
                           "&aЗабрать: "
                              + (var8 == null ? var7.itemId() : this.strip(var8.name())),
                           List.of(
                              "&7Отложенная выдача",
                              "&eНажми, когда в инвентаре есть место."
                           )
                        ),
                        "claim:" + var7.deliveryId()
                     );
                     if (++var5 % 9 == 8) {
                        var5 += 2;
                     }
                  }

                  if (var4.isEmpty()) {
                     var3.setItem(
                        22,
                        this.button(
                           Material.LIME_CONCRETE,
                           "&aПусто",
                           List.of(
                              "&7Нет предметов, ожидающих выдачи."
                           )
                        )
                     );
                  }

                  this.setAction(
                     var3,
                     var2,
                     45,
                     this.button(
                        Material.ARROW,
                        "&aНазад",
                        List.of(
                           "&7Вернуться в лавку."
                        )
                     ),
                     "back:main"
                  );
                  this.setAction(
                     var3,
                     var2,
                     49,
                     this.button(
                        Material.CLOCK,
                        "&eОбновить",
                        List.of(
                           "&7Проверить отложенную выдачу ещё раз."
                        )
                     ),
                     "refresh"
                  );
                  this.setAction(
                     var3,
                     var2,
                     53,
                     this.button(
                        Material.BARRIER,
                        "&cЗакрыть",
                        List.of(
                           "&7Выйти из меню."
                        )
                     ),
                     "close"
                  );
                  var1.openInventory(var3);
               }
            );
         }
      );
   }

   private void openHelp(Player var1) {
      CopiMineArtifacts.SessionState var2 = this.session(var1);
      var2.viewType = CopiMineArtifacts.ViewType.HELP;
      Inventory var3 = this.createMenu(
         var1,
         var2,
         CopiMineArtifacts.ViewType.HELP,
         27,
         "&8Помощь лавки"
      );
      var3.setItem(
         10,
         this.button(
            Material.BOOK,
            "&eПокупка",
            List.of(
               "&7Лавка -> категория -> товар -> подтвердить.",
               "&7Если PIN включён, появится цифровая панель."
            )
         )
      );
      var3.setItem(
         12,
         this.button(
            Material.CHEST_MINECART,
            "&eОтложенная выдача",
            List.of(
               "&7Если инвентарь заполнен, предмет не теряется.",
               "&7Открой Мои покупки или введи /cmartifacts claim."
            )
         )
      );
      var3.setItem(
         14,
         this.button(
            Material.ANVIL,
            "&eРемонт",
            List.of(
               "&7Возьми официальный предмет в руку.",
               "&7Нажми Ремонт или введи /cmartifacts repair."
            )
         )
      );
      var3.setItem(
         16,
         this.button(
            Material.BOOK,
            "&eОфициальные модели",
            List.of(
               "&7Ресурс-пак сервера обновляет внешний вид официальных предметов."
            )
         )
      );
      this.setAction(
         var3,
         var2,
         22,
         this.button(
            Material.ARROW,
            "&aНазад",
            List.of(
               "&7Вернуться в лавку."
            )
         ),
         "back:main"
      );
      var1.openInventory(var3);
   }

   private void openSuccess(Player var1, CopiMineArtifacts.CatalogItem var2, long var3) {
      CopiMineArtifacts.SessionState var5 = this.session(var1);
      var5.viewType = CopiMineArtifacts.ViewType.SUCCESS;
      var5.currentItemId = var2.itemId();
      Inventory var6 = this.createMenu(
         var1,
         var5,
         CopiMineArtifacts.ViewType.SUCCESS,
         27,
         "&8Покупка завершена"
      );
      var6.setItem(
         13,
         this.button(
            Material.LIME_CONCRETE,
            "&aПокупка успешна",
            List.of(
               "&7Товар: &f" + this.strip(var2.name()),
               "&7Списано: &f" + var2.priceAr() + " AR",
               "&7Баланс после списания: &f"
                  + var3
                  + " AR"
            )
         )
      );
      this.setAction(
         var6,
         var5,
         11,
         this.button(
            Material.EMERALD,
            "&aКупить ещё раз",
            List.of(
               "&7Повторить покупку этого товара"
            )
         ),
         "confirm:" + var2.itemId()
      );
      this.setAction(
         var6,
         var5,
         15,
         this.button(
            Material.ARROW,
            "&aНазад в категорию",
            List.of(
               "&7Вернуться к списку товаров"
            )
         ),
         "back:category"
      );
      this.setAction(
         var6,
         var5,
         22,
         this.button(
            Material.CHEST,
            "&bГлавное меню",
            List.of(
               "&7Вернуться в главное меню лавки"
            )
         ),
         "back:main"
      );
      var1.openInventory(var6);
   }

   private void openError(Player var1, CopiMineArtifacts.CatalogItem var2, String var3, String var4) {
      CopiMineArtifacts.SessionState var5 = this.session(var1);
      var5.viewType = CopiMineArtifacts.ViewType.ERROR;
      var5.currentItemId = var2.itemId();
      Inventory var6 = this.createMenu(
         var1,
         var5,
         CopiMineArtifacts.ViewType.ERROR,
         27,
         "&8Ошибка покупки"
      );
      var6.setItem(13, this.button(Material.RED_CONCRETE, var3, List.of("&7" + var4)));
      this.setAction(
         var6,
         var5,
         11,
         this.button(
            Material.RED_WOOL,
            "&cНазад к подтверждению",
            List.of(
               "&7Проверить покупку ещё раз"
            )
         ),
         "back:confirm"
      );
      this.setAction(
         var6,
         var5,
         15,
         this.button(
            Material.ARROW,
            "&aНазад к товару",
            List.of(
               "&7Вернуться к карточке товара"
            )
         ),
         "back:detail"
      );
      this.setAction(
         var6,
         var5,
         22,
         this.button(
            Material.CHEST,
            "&bГлавное меню",
            List.of(
               "&7Вернуться в главное меню лавки"
            )
         ),
         "back:main"
      );
      var1.openInventory(var6);
   }

   private void openRepair(Player var1) {
      ItemStack var2 = var1.getInventory().getItemInMainHand();
      CopiMineArtifacts.CatalogItem var3 = this.authenticCatalogItem(var2, var1, "repair_open");
      if (var3 == null) {
         if (this.hasArtifactIdentity(var2)) {
            this.refreshOfficialBindingAsync(var1, var2, "repair_open");
            return;
         }
         var1.sendMessage(
            this.color(
               "&cВ руке должен быть официальный предмет CopiMineArtifacts."
            )
         );
      } else {
         CopiMineArtifacts.CatalogItem catalog = var3;
         if (!isArCatalogItem(catalog.itemId())) {
            var1.sendMessage(this.color("&cРемонт за AR доступен только для AR-предметов лавки."));
            return;
         }
         if (var2.getItemMeta() instanceof Damageable var4 && var4.getDamage() > 0) {
            long var9 = this.repairPrice(var2, var3);
            CopiMineArtifacts.SessionState var7 = this.session(var1);
            var7.viewType = CopiMineArtifacts.ViewType.REPAIR;
            var7.currentItemId = var3.itemId();
            Inventory var8 = this.createMenu(
               var1,
               var7,
               CopiMineArtifacts.ViewType.REPAIR,
               27,
               "&8Ремонт артефакта"
            );
            var8.setItem(13, var2.clone());
            this.setAction(
               var8,
               var7,
               11,
               this.button(
                  Material.ANVIL,
                  "&aПочинить за " + var9 + " AR",
                  List.of(
                     "&7Официальный ремонт за AR"
                  )
               ),
               "repair:confirm:" + var9
            );
            this.setAction(
               var8,
               var7,
               15,
               this.button(
                  Material.ARROW,
                  "&aНазад",
                  List.of(
                     "&7Закрыть ремонт"
                  )
               ),
               "repair:cancel"
            );
            var1.openInventory(var8);
            return;
         }

         var1.sendMessage(
            this.color(
               "&eЭтот предмет не требует ремонта."
            )
         );
      }
   }

   private void purchaseDonationFromBalance(Player player, CopiMineArtifacts.DonationCatalogItem item) {
      if (player == null || item == null) {
         return;
      }
      // Donation catalog entries are rendered asynchronously. Re-read the
      // current catalog row at click time so a price edit cannot charge the
      // value embedded in an old inventory.
      CopiMineArtifacts.DonationCatalogItem liveItem = this.donationCatalogById.get(item.itemId().toLowerCase(Locale.ROOT));
      if (liveItem == null || !liveItem.enabled()) {
         player.sendMessage(this.color("&cЭтот donation-предмет больше недоступен."));
         this.openDonationCatalog(player);
         return;
      }
      if (liveItem != item || liveItem.priceDonation() != item.priceDonation()) {
         player.sendMessage(this.color("&eЦена donation-предмета изменилась. Каталог обновлён."));
         this.openDonationCatalog(player);
         return;
      }
      CopiMineArtifacts.SessionState state = this.session(player);
      if (!state.purchaseInFlightId.isBlank()) {
         player.sendMessage(this.color("&eПокупка уже обрабатывается."));
         return;
      }
      DonationPurchaseService service = this.donationPurchaseService();
      if (service == null) {
         player.sendMessage(this.color("&cСервис Donation-баланса сейчас недоступен."));
         return;
      }
      String idempotencyKey = "donation-game-purchase:" + player.getUniqueId() + ":" + item.itemId() + ":" + UUID.randomUUID();
      state.purchaseInFlightId = idempotencyKey;
      player.sendMessage(this.color("&7Проверяю Donation-баланс и привязку банка..."));
      service.purchaseIntentAsync(
            player.getUniqueId(),
            player.getName(),
            item.itemId(),
            item.priceDonation(),
            player.getName(),
            "artifact_shop_gui",
            idempotencyKey
      ).whenComplete((result, error) -> this.runSync(() -> {
         state.purchaseInFlightId = "";
         if (!player.isOnline()) {
            return;
         }
         if (error != null || result == null) {
            Throwable cause = error;
            while (cause instanceof CompletionException && cause.getCause() != null) {
               cause = cause.getCause();
            }
            String detail = cause == null ? "Покупка не выполнена." : this.firstNonBlank(cause.getMessage(), "Покупка не выполнена.");
            String normalizedDetail = detail.toLowerCase(Locale.ROOT);
            if (normalizedDetail.contains("active or unfinished donation entitlement")) {
               // A previous click may have charged the balance but stopped
               // before the item reached the player. Reuse that claim instead
               // of charging again after reconnecting or an interrupted delivery.
               this.readDonationClaimsAsync(player.getUniqueId()).whenComplete((claims, lookupError) -> this.runSync(() -> {
                  if (!player.isOnline()) {
                     return;
                  }
                  CopiMineArtifacts.DonationClaimRow existing = claims == null ? null : claims.stream()
                     .filter(claim -> item.itemId().equalsIgnoreCase(claim.itemId()))
                     .findFirst().orElse(null);
                  if (lookupError == null && existing != null) {
                     player.sendMessage(this.color("&eЭта покупка уже была оформлена. Проверяю отложенную выдачу..."));
                     this.deliverDonationClaimRowV2(player, existing);
                     return;
                  }
                  player.sendMessage(this.color("&eЭтот донат-предмет уже выдан или ожидает выдачи. Повторная покупка пока недоступна."));
                  this.openDonationCatalog(player);
               }));
               return;
            }
            if (normalizedDetail.contains("linked active ar bank")) {
               detail = "Сначала привяжи активный AR-банк.";
            } else if (detail.toLowerCase(Locale.ROOT).contains("insufficient") || detail.toLowerCase(Locale.ROOT).contains("balance")) {
               detail = "Недостаточно Donation-баланса.";
            }
            player.sendMessage(this.color("&c" + detail));
            this.openDonationCatalog(player);
            return;
         }
         long balanceAfter = this.parseLong(this.str(result.get("balance_after")), -1L);
         String balanceText = balanceAfter >= 0L ? " Остаток: " + balanceAfter + " Donation." : "";
         CatalogItem purchasedRuntime = this.runtimeCatalogItem(item.itemId());
         if (purchasedRuntime != null && "TAX_CLOCK".equalsIgnoreCase(purchasedRuntime.effect())) {
            String claimId = this.firstNonBlank(this.str(result.get("claim_id")), "");
            String purchaseId = this.firstNonBlank(this.str(result.get("purchase_id")), "");
            if (!claimId.isBlank()) {
               this.deliverDonationClaimRowV2(player, new DonationClaimRow(claimId, purchaseId, item.itemId(), 1L, "UNCLAIMED"));
               return;
            }
         }
         player.sendMessage(this.color("&aПокупка оформлена. Предмет добавлен в отложенную выдачу." + balanceText));
         this.openDonationOwned(player);
      }));
   }

   private void handleMenuAction(Player var1, CopiMineArtifacts.SessionState var2, String var3) {
      if (var3.startsWith("cat:")) {
         this.openCategory(var1, CopiMineArtifacts.Category.valueOf(var3.substring(4)));
      } else if (var3.startsWith("detail:")) {
         CopiMineArtifacts.CatalogItem var17 = this.catalogById.get(var3.substring(7));
         if (var17 != null) {
            this.openDetail(var1, var17);
         }
      } else if (var3.startsWith("confirm:")) {
         CopiMineArtifacts.CatalogItem var16 = this.catalogById.get(var3.substring(8));
         if (var16 != null) {
            this.openConfirm(var1, var16);
         }
      } else if (var3.startsWith("purchase:")) {
         CopiMineArtifacts.CatalogItem var15 = this.catalogById.get(var3.substring(9));
         if (var15 != null) {
            if (!var2.purchaseInFlightId.isBlank()) {
               var1.sendMessage(
                  this.color(
                     "&eПокупка уже обрабатывается."
                  )
               );
            } else {
               // Open the PIN pad synchronously. A status probe could fail or
               // report "not configured"; purchases must never bypass PIN entry.
               CopiMineArtifacts.SessionState var5x = var2;
               var5x.pinBuffer = "";
               this.openPin(var1, var15);
            }
         }
      } else if (var3.startsWith("digit:")) {
         if (var2.pinBuffer.length() < 8) {
            var2.pinBuffer = var2.pinBuffer + var3.substring(6);
         }

         this.refreshPin(var1);
      } else if ("pin:clear".equals(var3)) {
         var2.pinBuffer = "";
         this.refreshPin(var1);
      } else if ("pin:cancel".equals(var3)) {
         CopiMineArtifacts.CatalogItem var14 = this.catalogById.get(var2.currentItemId);
         if (var14 != null) {
            if ("repair".equals(var2.pinAction)) {
               this.openRepair(var1);
            } else {
               this.openConfirm(var1, var14);
            }
         }
      } else if ("pin:submit".equals(var3)) {
         CopiMineArtifacts.CatalogItem var13 = this.catalogById.get(var2.currentItemId);
         if (var13 != null) {
            if ("repair".equals(var2.pinAction)) {
               this.executeRepair(var1, this.repairPrice(var1.getInventory().getItemInMainHand(), var13), var2.pinBuffer);
               return;
            }
            CopiMineArtifacts.Shop current = this.currentShop(var2);
            if (current == null) {
               var2.purchaseInFlightId = "";
               var1.sendMessage(this.color("&cЛавка больше не существует. Откройте актуальную лавку заново."));
               var1.closeInventory();
               return;
            }
            this.executePurchase(var1, current, var13, var2.pinBuffer);
         }
      } else if ("purchases".equals(var3)) {
         this.openPurchases(var1);
      } else if ("pending".equals(var3)) {
         // "pending".equals(action)
         this.openPendingDeliveries(var1);
      } else if ("help".equals(var3)) {
         this.openHelp(var1);
      } else if ("donation:root".equals(var3)) {
         this.openDonationRoot(var1);
      } else if ("donation:balance".equals(var3) || "donation:topup".equals(var3)) {
         this.openDonationBalance(var1);
      } else if ("donation:catalog".equals(var3)) {
         this.openDonationCatalog(var1);
      } else if ("donation:owned".equals(var3)) {
         this.openDonationOwned(var1);
      } else if ("donation:reclaim".equals(var3)) {
         this.openDonationReclaim(var1);
      } else if (var3.startsWith("donation:catalog:item:")) {
         String var12 = var3.substring("donation:catalog:item:".length()).toLowerCase(Locale.ROOT);
         CopiMineArtifacts.DonationCatalogItem var19 = this.donationCatalogItem(var12);
         if (var19 != null && var19.enabled()) {
            this.purchaseDonationFromBalance(var1, var19);
         } else {
            var1.sendMessage(this.color("&cЭтот donation-предмет сейчас недоступен."));
         }
      } else if (var3.startsWith("donation:topup:create:")) {
         if (!this.donationTopupEnabled()) {
            var1.sendMessage(this.color("&eПополнение donation временно отключено. Покупки доступны за уже имеющийся баланс на сайте."));
            return;
         }
         DonationPaymentService var11 = this.donationPaymentService();
         if (var11 == null) {
            var1.sendMessage(this.color("&cCopiMineEconomyCore недоступен. Payment session сейчас не создать."));
         } else {
            long var18 = this.parseLong(var3.substring("donation:topup:create:".length()), 0L);
            if (!this.donationFixedPacks().contains(var18)) {
               var1.sendMessage(this.color("&cРазрешены только пакеты 50 / 100 / 250 / 500 / 1000."));
            } else {
               var11.createSessionAsync(
                     var1.getUniqueId(),
                     var1.getName(),
                     var18,
                     var1.getName(),
                     "artifact_shop_gui",
                     "artifact-donation-session-" + var1.getUniqueId() + "-" + var18 + "-" + UUID.randomUUID()
                  )
                  .whenComplete(
                     (var4x, var5x) -> this.runSync(
                           () -> {
                              if (var1.isOnline()) {
                                 if (var5x == null && var4x != null) {
                                    String var6 = this.firstNonBlank(this.str(var4x.get("session_id")), this.firstNonBlank(this.str(var4x.get("id")), ""));
                                    String var7x = var6.length() > 8 ? var6.substring(var6.length() - 8) : var6;
                                    var1.sendMessage(this.color("&aPayment session создана на &f" + var18 + " Donation&a."));
                                    var1.sendMessage(this.color("&7Ссылка: &f" + this.donationPaymentUrl(var6)));
                                    var1.sendMessage(this.color("&7Код сессии: &f" + var7x));
                                    var1.sendMessage(this.color("&7" + this.donationQrFallbackMessage()));
                                    this.openDonationBalance(var1);
                                 } else {
                                    this.getLogger().log(Level.WARNING, "Donation session create failed from artifact GUI", var5x);
                                    var1.sendMessage(this.color("&cНе удалось создать payment session."));
                                 }
                              }
                           }
                        )
                  );
            }
         }
      } else if (var3.startsWith("donation:reclaim:item:")) {
         this.reclaimDonationItemSafe(var1, var3.substring("donation:reclaim:item:".length()));
      } else if ("refresh".equals(var3)) {
         // "refresh".equals(action)
         this.refreshCurrentMenu(var1, var2);
      } else if ("page:prev".equals(var3)) {
         if (!var2.currentCategory.isBlank()) {
            this.openCategory(var1, CopiMineArtifacts.Category.valueOf(var2.currentCategory), Math.max(0, var2.page - 1));
         }
      } else if ("page:next".equals(var3)) {
         if (!var2.currentCategory.isBlank()) {
            CopiMineArtifacts.Category var10 = CopiMineArtifacts.Category.valueOf(var2.currentCategory);
            int var5 = Math.max(0, (this.catalogByCategory.getOrDefault(var10, List.of()).size() - 1) / 21);
            this.openCategory(var1, var10, Math.min(var5, var2.page + 1));
         }
      } else if ("admin:main".equals(var3)) {
         this.openArtifactsAdminMenu(var1);
      } else if ("admin:shops".equals(var3)) {
         this.openAdminShops(var1);
      } else if ("admin:shop:create".equals(var3)) {
         this.createAdminShopFromTarget(var1);
      } else if ("admin:shop:list".equals(var3)) {
         this.openAdminShopListAsync(var1);
      } else if (var3.startsWith("shop:teleport:")) {
         String shopId = var3.substring("shop:teleport:".length()); CopiMineArtifacts.Shop shop = this.shopsByLocation.values().stream().filter(value -> value.shopId().equals(shopId)).findFirst().orElse(null);
         if (shop == null || Bukkit.getWorld(shop.world()) == null) { var1.sendMessage(this.color("&cЛавка недоступна.")); return; }
         var1.teleport(new org.bukkit.Location(Bukkit.getWorld(shop.world()), shop.x() + 0.5D, shop.y() + 1D, shop.z() + 0.5D));
      } else if (var3.startsWith("shop:detail:")) {
         String shopId = var3.substring("shop:detail:".length()); CopiMineArtifacts.Shop shop = this.shopsByLocation.values().stream().filter(value -> value.shopId().equals(shopId)).findFirst().orElse(null); if (shop == null) { var1.sendMessage(this.color("&cЛавка больше не существует.")); return; }
         Inventory menu = this.createMenu(var1, var2, CopiMineArtifacts.ViewType.ADMIN_SHOPS, 27, "&8" + this.shortText(shop.title(), 24)); menu.setItem(13, this.button(Material.BARREL, "&f" + shop.title(), List.of("&7ID: &f" + shop.shopId(), "&7Мир: &f" + shop.world(), "&7Координаты: &f" + shop.x() + " " + shop.y() + " " + shop.z()))); this.setAction(menu,var2,11,this.button(Material.ENDER_PEARL,"&aТелепорт",List.of()),"shop:teleport:"+shopId); if(this.hasSeniorShopPermission(var1)) this.setAction(menu,var2,15,this.button(Material.RED_WOOL,"&cУдалить",List.of()),"shop:delete:ask:"+shopId); this.setAction(menu,var2,22,this.button(Material.ARROW,GUI_BACK_LABEL,List.of()),"admin:shop:list"); var1.openInventory(menu);
      } else if (var3.startsWith("shop:delete:ask:")) {
         String shopId = var3.substring("shop:delete:ask:".length()); if (!this.hasSeniorShopPermission(var1)) { this.noPermission(var1); return; } Inventory menu=this.createMenu(var1,var2,CopiMineArtifacts.ViewType.ADMIN_SHOPS,27,"&8Подтвердить удаление"); this.setAction(menu,var2,11,this.button(Material.LIME_WOOL,"&aДа",List.of()),"shop:delete:confirm:"+shopId); this.setAction(menu,var2,15,this.button(Material.RED_WOOL,"&cНет",List.of()),"admin:shop:list"); var1.openInventory(menu);
      } else if (var3.startsWith("shop:delete:confirm:")) {
         String shopId = var3.substring("shop:delete:confirm:".length()); CopiMineArtifacts.Shop shop = this.shopsByLocation.values().stream().filter(value -> value.shopId().equals(shopId)).findFirst().orElse(null);
         if (shop == null) { var1.sendMessage(this.color("&cЛавка больше не существует.")); return; }
         this.removeShopWithConfirmation(var1, shop);
      } else if ("admin:gift:players".equals(var3)) {
         this.openAdminGiftPlayersAsync(var1);
      } else if (var3.startsWith("admin_gift:player:")) {
         String uuid = var3.substring("admin_gift:player:".length());
         try { var2.giftTargetUuid = UUID.fromString(uuid); var2.giftTargetName = this.firstNonBlank(Bukkit.getOfflinePlayer(var2.giftTargetUuid).getName(), "Игрок"); } catch (IllegalArgumentException error) { var1.sendMessage(this.color("&cИгрок больше недоступен.")); return; }
         CopiMineArtifacts.SessionState state = var2; Inventory menu = this.createMenu(var1, state, CopiMineArtifacts.ViewType.ADMIN_GIFT_KIND, 27, "&8Категория предмета");
         this.setAction(menu, state, 11, this.button(Material.EMERALD, "&aАР", List.of()), "admin_gift:kind:AR"); this.setAction(menu, state, 13, this.button(Material.NETHER_STAR, "&dДонат", List.of()), "admin_gift:kind:DONATION"); this.setAction(menu, state, 15, this.button(Material.CHEST, "&6Скрытое", List.of()), "admin_gift:kind:HIDDEN"); var1.openInventory(menu);
      } else if (var3.startsWith("admin_gift:kind:")) {
         this.openAdminGiftCatalog(var1, var3.substring("admin_gift:kind:".length()));
      } else if ("admin_gift:kind".equals(var3)) {
         this.openAdminGiftPlayersAsync(var1);
      } else if (var3.startsWith("admin_gift:item:")) {
         var2.currentItemId = var3.substring("admin_gift:item:".length()); this.openAdminGiftConfirm(var1);
      } else if ("admin_gift:confirm".equals(var3)) {
         this.createAdminGiftAsync(var1, var2);
      } else if ("admin:catalog".equals(var3)) {
         this.openAdminCatalog(var1);
      } else if ("admin:diagnostics".equals(var3)) {
         this.openAdminDiagnosticsAsync(var1);
      } else if ("admin:reload".equals(var3)) {
         if (!this.hasArtifactPermission(var1, "copimine.artifacts.reload")) {
            this.noPermission(var1);
         } else {
            this.reloadConfig();
            this.debugGui = this.getConfig().getBoolean("debug_gui", false);
            this.loadCatalogFromConfig();
            this.runAsync(
               () -> {
                  try {
                     this.syncCatalogToPostgres();
                     this.runSync(() -> {
                        var1.sendMessage(this.color("&aCopiMineArtifacts catalog reloaded."));
                        this.openAdminDiagnosticsAsync(var1);
                     });
                  } catch (SQLException var3x) {
                     this.getLogger().log(Level.WARNING, "Artifacts admin reload failed", (Throwable)var3x);
                     this.runSync(
                        () -> { this.clearRepairState(this.session(var1)); var1.sendMessage(
                              this.color(
                                 "&cНе удалось выполнить действие. Ошибка записана в лог. Код: ARTIFACT_RELOAD_FAILED"
                              )
                           ); }
                     );
                  }
               }
            );
         }
      } else if ("repair:open".equals(var3)) {
         this.openRepair(var1);
      } else if ("close".equals(var3)) {
         var1.closeInventory();
      } else if (var3.startsWith("claim:")) {
         this.claimOne(var1, var3.substring(6));
      } else if (var3.startsWith("repair:confirm:")) {
         long var9 = this.parseLong(var3.substring("repair:confirm:".length()), 0L);
         if (var9 <= 0L) {
            this.executeRepair(var1, var9, "");
         } else {
            var2.pinBuffer = "";
            var2.pinAction = "repair";
            CopiMineArtifacts.CatalogItem repairItem = this.catalogById.get(var2.currentItemId);
            if (repairItem != null) {
               this.openPin(var1, repairItem);
            }
         }
      } else if ("repair:cancel".equals(var3)) {
         var1.closeInventory();
      } else if ("back:main".equals(var3)) {
         CopiMineArtifacts.Shop var8 = this.currentShop(var2);
         if (var8 != null) {
            this.openMain(var1, var8);
         }
      } else if ("back:category".equals(var3)) {
         this.openCategory(var1, CopiMineArtifacts.Category.valueOf(var2.currentCategory));
      } else if ("back:detail".equals(var3)) {
         CopiMineArtifacts.CatalogItem var7 = this.catalogById.get(var2.currentItemId);
         if (var7 != null) {
            this.openDetail(var1, var7);
         }
      } else {
         if ("back:confirm".equals(var3)) {
            CopiMineArtifacts.CatalogItem var4 = this.catalogById.get(var2.currentItemId);
            if (var4 != null) {
               this.openConfirm(var1, var4);
            }
         }
      }
   }

   private void refreshCurrentMenu(Player var1, CopiMineArtifacts.SessionState var2) {
      switch (var2.viewType) {
         case MAIN:
            CopiMineArtifacts.Shop var7 = this.currentShop(var2);
            if (var7 != null) {
               this.openMain(var1, var7);
            }
            break;
         case CATEGORY:
            if (!var2.currentCategory.isBlank()) {
               this.openCategory(var1, CopiMineArtifacts.Category.valueOf(var2.currentCategory), var2.page);
            }
            break;
         case DETAIL:
            CopiMineArtifacts.CatalogItem var6 = this.catalogById.get(var2.currentItemId);
            if (var6 != null) {
               this.openDetail(var1, var6);
            }
            break;
         case CONFIRM:
            CopiMineArtifacts.CatalogItem var5 = this.catalogById.get(var2.currentItemId);
            if (var5 != null) {
               this.openConfirm(var1, var5);
            }
            break;
         case PIN:
            CopiMineArtifacts.CatalogItem var4 = this.catalogById.get(var2.currentItemId);
            if (var4 != null) {
               this.openPin(var1, var4);
            }
            break;
         case PURCHASES:
            this.openPurchases(var1);
            break;
         case PENDING_DELIVERY:
            this.openPendingDeliveries(var1);
            break;
         case HELP:
            this.openHelp(var1);
            break;
         case REPAIR:
            this.openRepair(var1);
            break;
         case SUCCESS:
         case ERROR:
         default:
            CopiMineArtifacts.Shop var3 = this.currentShop(var2);
            if (var3 != null) {
               this.openMain(var1, var3);
            }
            break;
         case DONATION_ROOT:
            this.openDonationRoot(var1);
            break;
         case DONATION_BALANCE:
            this.openDonationBalance(var1);
            break;
         case DONATION_CATALOG:
            this.openDonationCatalog(var1);
            break;
         case DONATION_OWNED:
            this.openDonationOwned(var1);
            break;
         case DONATION_RECLAIM:
            this.openDonationReclaim(var1);
            break;
         case ADMIN_MAIN:
            this.openArtifactsAdminMenu(var1);
            break;
         case ADMIN_SHOPS:
            this.openAdminShops(var1);
            break;
         case ADMIN_CATALOG:
            this.openAdminCatalog(var1);
            break;
         case ADMIN_DIAGNOSTICS:
            this.openAdminDiagnosticsAsync(var1);
      }
   }

   private void executePurchase(Player var1, CopiMineArtifacts.Shop var2, CopiMineArtifacts.CatalogItem var3, String var4) {
      if (var1 != null && var3 != null && var2 != null && this.bridge != null) {
         if (var4 == null || !var4.matches("\\d{4,8}")) {
            this.session(var1).pinBuffer = "";
            var1.sendMessage(this.color("&eСначала введи PIN банка для покупки."));
            this.openPin(var1, var3);
            return;
         }
         CopiMineArtifacts.SessionState var5 = this.session(var1);
         CopiMineArtifacts.CatalogItem liveItem = this.catalogById.get(var3.itemId());
         if (liveItem == null) {
            var5.purchaseInFlightId = "";
            var1.sendMessage(this.color("&cЭтот товар больше недоступен в каталоге."));
            this.openMain(var1, var2, true);
            return;
         }
         if (var3 != liveItem || var5.confirmedCatalogGeneration >= 0L
               && (var5.confirmedCatalogGeneration != this.catalogGeneration
                  || var5.confirmedPriceAr >= 0L && var5.confirmedPriceAr != liveItem.priceAr())) {
            var5.pinBuffer = "";
            var5.purchaseInFlightId = "";
            var1.sendMessage(this.color("&eЦена товара изменилась. Проверьте подтверждение ещё раз."));
            this.openConfirm(var1, liveItem);
            return;
         }
         // The GUI action contains only an item id. It must reference the
         // current catalog object, never a stale object retained by an old
         // inventory (the identity check above enforces that invariant).
         var5.confirmedPriceAr = var3.priceAr();
         var5.confirmedCatalogGeneration = this.catalogGeneration;
         if (!var5.purchaseInFlightId.isBlank()) {
            var1.sendMessage(
               this.color(
                  "&eПокупка уже обрабатывается."
               )
            );
         } else if (var3.category() == CopiMineArtifacts.Category.RP) {
            var1.sendMessage(
               this.color(
                  "&eДля этой категории сейчас нет доступных товаров."
               )
            );
         } else if (var1.getInventory().firstEmpty() < 0) {
            var1.sendMessage(
               this.color(
                  "&cНедостаточно места в инвентаре. AR не списаны."
               )
            );
         } else {
            CopiMineArtifacts.ShopRevenueRecipient var6x = this.resolveActivePresidentRevenueRecipient();
            if (this.bridge == null || var6x == null) {
               var1.sendMessage(this.color("&cЛавка временно недоступна: банковский сервис ещё не готов."));
               return;
            }

            String var6 = UUID.randomUUID().toString();
            var5.purchaseInFlightId = var6;
            CopiMineArtifacts.PurchaseContext var7 = new CopiMineArtifacts.PurchaseContext(var6, UUID.randomUUID().toString(), var3, var2, var4, var6x);
            var1.sendMessage(
               this.color(
                  "&7CopiMineArtifacts: проверяем покупку..."
               )
            );
            this.runAsync(
               () -> {
                  int var8 = this.purchasedCount(var3.itemId());
                  if (var3.supplyLimit() > 0 && var8 >= var3.supplyLimit()) {
                     this.runSync(
                        () -> {
                           var5.purchaseInFlightId = "";
                           if (var1.isOnline()) {
                              var1.sendMessage(
                                 this.color(
                                    "&cЛимит поставки для этого артефакта исчерпан."
                                 )
                              );
                              this.openError(
                                 var1,
                                 var3,
                                 "&cЛимит поставки исчерпан",
                                 "Доступно "
                                    + var3.supplyLimit()
                                    + " шт. на весь сервер."
                              );
                           }
                        }
                     );
                  } else {
                     int var9 = this.playerPurchasedCount(var1.getUniqueId().toString(), var3.itemId());
                     if (var3.perPlayerLimit() > 0 && var9 >= var3.perPlayerLimit()) {
                        this.runSync(
                           () -> {
                              var5.purchaseInFlightId = "";
                              if (var1.isOnline()) {
                                 var1.sendMessage(
                                    this.color(
                                       "&cВаш персональный лимит на этот артефакт уже достигнут."
                                    )
                                 );
                                 this.openError(
                                    var1,
                                    var3,
                                    "&cПерсональный лимит достигнут",
                                    "Можно купить не больше "
                                       + var3.perPlayerLimit()
                                       + " шт. на игрока."
                                 );
                              }
                           }
                        );
                     } else {
                        CopiMineArtifacts.BridgeTxnResult var10 = this.bridge
                           .transferToAccount(
                              var1,
                              var4,
                              this.firstNonBlank(var7.revenueRecipient().budgetAccountId(), PRESIDENT_BUDGET_ACCOUNT_ID),
                              var7.revenueRecipient().presidentUuid().toString(),
                              var7.revenueRecipient().presidentName(),
                              var3.priceAr(),
                              "artifact-purchase-" + var6,
                              "AR_SHOP_PURCHASE",
                              "item=" + var3.itemId() + " shop=" + var2.shopId()
                           );
                        if (!var10.ok()) {
                           this.runSync(
                              () -> {
                                 var5.purchaseInFlightId = "";
                                 if (var1.isOnline()) {
                                    if ("INSUFFICIENT_AR".equalsIgnoreCase(var10.code())) {
                                       var1.sendMessage(
                                          this.color(
                                             "&cНедостаточно AR на банковском счёте."
                                          )
                                       );
                                       this.openError(
                                          var1,
                                          var3,
                                          "&cНедостаточно AR",
                                          "На банковском счёте не хватает средств для покупки."
                                       );
                                    } else if ("PIN_INVALID".equalsIgnoreCase(var10.code())) {
                                       var1.sendMessage(
                                          this.color(
                                             "&cPIN введён неверно."
                                          )
                                       );
                                       this.openError(
                                          var1,
                                          var3,
                                          "&cНеверный PIN",
                                          "Проверьте введённый PIN и повторите покупку."
                                       );
                                    } else {
                                       var1.sendMessage(
                                          this.color(
                                             "&cПокупка отклонена. Код: "
                                                + this.safeBridgeCode(var10.code())
                                          )
                                       );
                                       this.openError(
                                          var1,
                                          var3,
                                          "&cПокупка отклонена",
                                          "Код: " + this.safeBridgeCode(var10.code())
                                       );
                                    }
                                 }
                              }
                           );
                        } else {
                           try {
                              this.persistPaidPurchase(var1, var7, var10);
                           } catch (SQLException var15) {
                              CopiMineArtifacts.BridgeTxnResult var12 = this.bridge
                                 .transferFromAccount(
                                    this.firstNonBlank(var7.revenueRecipient().budgetAccountId(), PRESIDENT_BUDGET_ACCOUNT_ID),
                                    var7.revenueRecipient().presidentUuid().toString(),
                                    var7.revenueRecipient().presidentName(),
                                    var1.getUniqueId(),
                                    var1.getName(),
                                    var3.priceAr(),
                                    "artifact-refund-" + var6,
                                    "AR_SHOP_PURCHASE_REFUND",
                                    "purchase=" + var6
                                 );
                              boolean var13 = "ARTIFACT_LIMIT_SUPPLY".equalsIgnoreCase(this.firstNonBlank(var15.getMessage(), ""));
                              boolean var14 = "ARTIFACT_LIMIT_PLAYER".equalsIgnoreCase(this.firstNonBlank(var15.getMessage(), ""));
                              if (!var12.ok()) {
                                 this.audit(
                                    var1.getName(),
                                    "purchase_manual_review",
                                    var7.purchaseId(),
                                    var7.item().itemId() + " reason=persist_failed refund=" + this.safeBridgeCode(var12.code())
                                 );
                                 this.getLogger()
                                    .log(
                                       Level.WARNING,
                                       "Artifact purchase refund failed after persist error: " + var7.purchaseId() + " / " + this.safeBridgeCode(var12.code()),
                                       (Throwable)var15
                                    );
                              }

                              this.runSync(
                                 () -> {
                                    var5.purchaseInFlightId = "";
                                    if (var1.isOnline()) {
                                       if (var12.ok()) {
                                          if (var13) {
                                             var1.sendMessage(
                                                this.color(
                                                   "&cЛимит поставки для этого артефакта уже исчерпан. AR автоматически возвращены."
                                                )
                                             );
                                             this.openError(
                                                var1,
                                                var3,
                                                "&cЛимит поставки исчерпан",
                                                "Во время финальной записи лимит уже занял другой покупатель. Списание AR отменено."
                                             );
                                          } else if (var14) {
                                             var1.sendMessage(
                                                this.color(
                                                   "&cВаш персональный лимит на этот артефакт уже достигнут. AR автоматически возвращены."
                                                )
                                             );
                                             this.openError(
                                                var1,
                                                var3,
                                                "&cПерсональный лимит достигнут",
                                                "Во время финальной записи лимит уже был занят. Списание AR отменено."
                                             );
                                          } else {
                                             var1.sendMessage(
                                                this.color(
                                                   "&cБаза CopiMineArtifacts недоступна. Покупка отменена, AR возвращены."
                                                )
                                             );
                                             this.openError(
                                                var1,
                                                var3,
                                                "&cПокупка отменена",
                                                "Финальная запись покупки не прошла. Списание AR отменено."
                                             );
                                          }
                                       } else {
                                          var1.sendMessage(
                                             this.color(
                                                "&cПокупка остановлена после списания AR. Автовозврат не подтверждён, нужна ручная проверка администрации."
                                             )
                                          );
                                          this.openError(
                                             var1,
                                             var3,
                                             "&cНужна ручная проверка",
                                             "Списание прошло, но rollback не подтверждён. Автоматическая повторная выдача отключена."
                                          );
                                       }
                                    }
                                 }
                              );
                              return;
                           }

                           this.runSync(() -> this.deliverPurchase(var1, var7, var10));
                        }
                     }
                  }
               }
            );
         }
      } else {
         var1.sendMessage(this.color("&cCopiMineEconomyCore bridge is unavailable."));
      }
   }

   private void persistPaidPurchase(Player var1, CopiMineArtifacts.PurchaseContext var2, CopiMineArtifacts.BridgeTxnResult var3) throws SQLException {
      /*
      persistPaidPurchase(Player player, PurchaseContext context, BridgeTxnResult charge)
      lockArtifactPurchaseConstraints(c, player.getUniqueId().toString(), context.item().itemId());
      purchasedCount(c, context.item().itemId())
      playerPurchasedCount(c, player.getUniqueId().toString(), context.item().itemId())
      */
      Connection var4 = this.pgPool.acquire();

      try {
         var4.setAutoCommit(false);

         try (
            PreparedStatement var5 = var4.prepareStatement(
               "    INSERT INTO artifact_purchases(purchase_id,unique_item_id,player_uuid,player_name,item_id,shop_id,price_ar,bank_tx_id,idempotency_key,status,delivery_mode,created_at,updated_at)\n    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)\n"
            );
            PreparedStatement var6 = var4.prepareStatement(
               "    INSERT INTO artifact_item_instances(unique_item_id,item_id,owner_uuid,purchase_id,status,repaired_count,created_at,updated_at)\n    VALUES(?,?,?,?,?,?,?,?)\n"
            );
            PreparedStatement var8 = var4.prepareStatement(
                "    INSERT INTO artifact_revenue_payouts(purchase_id,president_uuid,president_name,recipient_account_id,buyer_uuid,buyer_name,item_id,shop_id,amount_ar,status,bank_tx_id,idempotency_key,last_error,created_at,updated_at)\n    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)\n"
            );
         ) {
            this.lockArtifactPurchaseConstraints(var4, var1.getUniqueId().toString(), var2.item().itemId());
            if (var2.item().supplyLimit() > 0 && this.purchasedCount(var4, var2.item().itemId()) >= var2.item().supplyLimit()) {
               throw new SQLException("ARTIFACT_LIMIT_SUPPLY");
            }

            if (var2.item().perPlayerLimit() > 0
               && this.playerPurchasedCount(var4, var1.getUniqueId().toString(), var2.item().itemId()) >= var2.item().perPlayerLimit()) {
               throw new SQLException("ARTIFACT_LIMIT_PLAYER");
            }

            long var7 = this.now();
            var5.setString(1, var2.purchaseId());
            var5.setString(2, var2.uniqueItemId());
            var5.setString(3, var1.getUniqueId().toString());
            var5.setString(4, var1.getName());
            var5.setString(5, var2.item().itemId());
            var5.setString(6, var2.shop().shopId());
            var5.setLong(7, var2.item().priceAr());
            var5.setString(8, var3.txId());
            var5.setString(9, "artifact-purchase-" + var2.purchaseId());
            var5.setString(10, "PAID");
            var5.setString(11, "DIRECT");
            var5.setLong(12, var7);
            var5.setLong(13, var7);
            var5.executeUpdate();
            var6.setString(1, var2.uniqueItemId());
            var6.setString(2, var2.item().itemId());
            var6.setString(3, var1.getUniqueId().toString());
            var6.setString(4, var2.purchaseId());
            var6.setString(5, "DELIVERING");
            var6.setInt(6, 0);
            var6.setLong(7, var7);
            var6.setLong(8, var7);
            var6.executeUpdate();
            var8.setString(1, var2.purchaseId());
            var8.setString(2, var2.revenueRecipient().presidentUuid().toString());
            var8.setString(3, var2.revenueRecipient().presidentName());
            var8.setString(4, this.firstNonBlank(var2.revenueRecipient().budgetAccountId(), PRESIDENT_BUDGET_ACCOUNT_ID));
            var8.setString(5, var1.getUniqueId().toString());
            var8.setString(6, var1.getName());
            var8.setString(7, var2.item().itemId());
            var8.setString(8, var2.shop().shopId());
            var8.setLong(9, var2.item().priceAr());
            // The bank transfer only charges the buyer here. Revenue is
            // credited by the asynchronous payout worker, which consumes
            // PENDING rows; inserting CREDITED would silently strand every
            // shop sale before the president/treasury receives its share.
            var8.setString(10, "PENDING");
            var8.setString(11, var3.txId());
            var8.setString(12, "artifact-president-budget-" + var2.purchaseId());
            var8.setString(13, "");
            var8.setLong(14, var7);
            var8.setLong(15, var7);
            var8.executeUpdate();
            var4.commit();
         } catch (SQLException var24) {
            var4.rollback();
            throw var24;
         }
      } finally {
         try {
            var4.setAutoCommit(true);
         } catch (SQLException var19) {
         }

         this.pgPool.release(var4);
      }
   }

   private void deliverPurchase(Player var1, CopiMineArtifacts.PurchaseContext var2, CopiMineArtifacts.BridgeTxnResult var3) {
      /*
      deliverPurchase(Player player, PurchaseContext context, BridgeTxnResult charge)
      markArtifactPurchaseReview(context.purchaseId(), context.uniqueItemId());
      finalize_after_physical_delivery_failed
      */
      CopiMineArtifacts.SessionState var4 = this.session(var1);
      if (!var1.isOnline()) {
         var4.purchaseInFlightId = "";
         this.runAsync(() -> {
            try {
               this.createPendingDelivery(var1, var2);
               this.audit("SERVER", "purchase_pending_delivery", var2.purchaseId(), var2.item().itemId() + " reason=buyer_offline_after_charge");
               this.triggerRevenuePayoutAsync(var2.purchaseId());
            } catch (SQLException var4x) {
               try {
                  this.markArtifactPurchaseReview(var2.purchaseId(), var2.uniqueItemId());
                  this.audit("SERVER", "purchase_manual_review", var2.purchaseId(), var2.item().itemId() + " reason=buyer_offline_pending_delivery_failed");
               } catch (SQLException var5x) {
                  this.getLogger().log(Level.WARNING, "Artifact purchase review mark failed after offline delivery error: " + var2.purchaseId(), (Throwable)var5x);
               }

               this.getLogger().log(Level.WARNING, "Artifact pending delivery creation failed after buyer disconnect: " + var2.purchaseId(), (Throwable)var4x);
            }
         });
         return;
      }

      ItemStack var5 = this.createOfficialItem(var2.item(), var2.uniqueItemId(), var1.getUniqueId(), var2.purchaseId());
      this.cacheProvisionalDonationInstances(var2.item().itemId(), var1.getUniqueId(), List.of(var2.uniqueItemId()));
      HashMap var6 = var1.getInventory().addItem(new ItemStack[]{var5});
      if (var6.isEmpty()) {
         // Inventory#addItem returning no leftovers is not, by itself, a
         // durable proof that the instance is still present.  A listener or
         // another plugin can consume/move the stack in the same tick before
         // the asynchronous purchase transition commits.  Never finalize a
         // paid row without first proving the exact physical identity is
         // still owned by the buyer; otherwise recovery can mint a second
         // copy after a restart.
         if (!this.playerHasOfficialInstance(var1, var2.uniqueItemId(), var2.item().itemId(), var1.getUniqueId())) {
            this.runAsync(() -> {
               try {
                  this.markArtifactPurchaseReview(var2.purchaseId(), var2.uniqueItemId());
                  this.audit(var1.getName(), "purchase_manual_review", var2.purchaseId(), var2.item().itemId() + " reason=physical_delivery_missing");
               } catch (SQLException var6x) {
                  this.getLogger().log(Level.WARNING, "Artifact purchase review mark failed after missing physical delivery: " + var2.purchaseId(), (Throwable)var6x);
               }
            });
            var4.purchaseInFlightId = "";
            var1.sendMessage(this.color("&eОплата получена, но физическая выдача не подтверждена. Покупка отправлена на ручную проверку; повторная копия не будет выдана автоматически."));
            this.openPurchases(var1);
            return;
         }
         this.runAsync(
            () -> {
                  try {
                     this.markPurchaseDelivered(var2.purchaseId(), var2.uniqueItemId(), var2.item().itemId());
                     this.audit(var1.getName(), "purchase_delivered", var2.purchaseId(), var2.item().itemId());
                     this.runSync(() -> {
                        var4.purchaseInFlightId = "";
                        this.cacheOfficialBinding(var2.uniqueItemId(), var2.item().itemId(), var1.getUniqueId());
                        this.removeProvisionalDonationInstances(List.of(var2.uniqueItemId()));
                        if (var1.isOnline()) {
                           var1.sendMessage(this.color("&aПокупка успешна. Баланс после списания: &f" + var3.balanceAfter() + " AR"));
                           var1.playSound(var1.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.4F);
                           this.openSuccess(var1, var2.item(), var3.balanceAfter());
                        }
                     });
                     this.triggerRevenuePayoutAsync(var2.purchaseId());
                  } catch (SQLException var6x) {
                  try {
                     this.markArtifactPurchaseReview(var2.purchaseId(), var2.uniqueItemId());
                     this.audit(
                        var1.getName(), "purchase_manual_review", var2.purchaseId(), var2.item().itemId() + " reason=finalize_after_physical_delivery_failed"
                     );
                  } catch (SQLException var5x) {
                     this.getLogger()
                        .log(Level.WARNING, "Artifact purchase review mark failed after physical issuance: " + var2.purchaseId(), (Throwable)var5x);
                  }

                  this.getLogger().log(Level.WARNING, "Artifact purchase finalize failed after physical issuance: " + var2.purchaseId(), (Throwable)var6x);
                  // Keep the item provisional.  It remains physically in the
                  // owner's inventory for deterministic recovery and cannot
                  // be used or moved into external storage meanwhile.
                  this.runSync(() -> {
                     var4.purchaseInFlightId = "";
                     if (var1.isOnline()) {
                        var1.sendMessage(this.color("&eПредмет выдан, но регистрация покупки не подтверждена. Операция отправлена на ручную проверку."));
                        this.openError(var1, var2.item(), "&eНужна ручная проверка", "Предмет временно заблокирован до финализации покупки.");
                     }
                  });
               }
            }
         );
         var1.sendMessage(this.color("&eПредмет выдан, завершаю регистрацию покупки..."));
      } else {
         this.runAsync(
            () -> {
               try {
                  this.createPendingDelivery(var1, var2);
                  this.audit(var1.getName(), "purchase_pending_delivery", var2.purchaseId(), var2.item().itemId());
                  this.triggerRevenuePayoutAsync(var2.purchaseId());
                  this.runSync(
                     () -> {
                        var4.purchaseInFlightId = "";
                        if (var1.isOnline()) {
                           var1.sendMessage(
                              this.color(
                                 "&eПредмет оплачен, но инвентарь занят. Покупка переведена в отложенную выдачу."
                              )
                           );
                           this.openPurchases(var1);
                        }
                     }
                  );
               } catch (SQLException var12) {
                  /*
                  createPendingDelivery(player, context)
                  BridgeTxnResult refund = bridge.refund(player, context.item().priceAr(), "artifact-pending-refund-"
                  markArtifactPurchaseCancelled(context.purchaseId(), context.uniqueItemId())
                  markArtifactPurchaseReview(context.purchaseId(), context.uniqueItemId())
                  bridge.refund
                  */
                  CopiMineArtifacts.BridgeTxnResult var5x = this.bridge
                     .refund(
                        var1,
                        var2.item().priceAr(),
                        "artifact-pending-refund-" + var2.purchaseId(),
                        "artifact_pending_delivery_refund",
                        "purchase=" + var2.purchaseId()
                     );
                  boolean var6x = false;
                  boolean var7 = false;
                  if (var5x.ok()) {
                     try {
                        this.cancelRevenuePayout(var2.purchaseId(), "pending_delivery_create_failed");
                        this.markArtifactPurchaseCancelled(var2.purchaseId(), var2.uniqueItemId());
                        var6x = true;
                        this.audit(
                           var1.getName(), "purchase_cancelled_refunded", var2.purchaseId(), var2.item().itemId() + " reason=pending_delivery_create_failed"
                        );
                     } catch (SQLException var11) {
                        this.getLogger()
                           .log(Level.WARNING, "Artifact purchase cleanup failed after pending-delivery refund: " + var2.purchaseId(), (Throwable)var11);
                     }
                  }

                  if (!var6x) {
                     try {
                        this.markArtifactPurchaseReview(var2.purchaseId(), var2.uniqueItemId());
                        var7 = true;
                     } catch (SQLException var10) {
                        this.getLogger()
                           .log(Level.WARNING, "Artifact purchase review mark failed after pending-delivery error: " + var2.purchaseId(), (Throwable)var10);
                     }

                     this.audit(
                        var1.getName(),
                        "purchase_manual_review",
                        var2.purchaseId(),
                        var2.item().itemId() + " reason=pending_delivery_create_failed refund=" + this.safeBridgeCode(var5x.code())
                     );
                  }

                  if (!var5x.ok()) {
                     this.getLogger()
                        .log(
                           Level.WARNING,
                           "Artifact purchase refund failed after pending-delivery error: " + var2.purchaseId() + " / " + this.safeBridgeCode(var5x.code()),
                           (Throwable)var12
                        );
                  }

                  boolean var8 = var5x.ok() && var6x;
                  boolean var9 = var7;
                  this.runSync(
                     () -> {
                        var4.purchaseInFlightId = "";
                        if (var1.isOnline()) {
                           if (var8) {
                              var1.sendMessage(
                                 this.color(
                                    "&cИнвентарь был занят, а отложенную выдачу сохранить не удалось. Покупка отменена, AR возвращены."
                                 )
                              );
                              this.openError(
                                 var1,
                                 var2.item(),
                                 "&cПокупка отменена",
                                 "Отложенная выдача не сохранилась. Списание AR автоматически отменено."
                              );
                           } else if (var9) {
                              var1.sendMessage(
                                 this.color(
                                    "&cОтложенную выдачу сохранить не удалось. Покупка переведена в ручную проверку, автоматический повтор отключён."
                                 )
                              );
                              this.openError(
                                 var1,
                                 var2.item(),
                                 "&cНужна ручная проверка",
                                 "Предмет не выдан автоматически. Проверь журнал аудита и статус покупки."
                              );
                           } else {
                              var1.sendMessage(
                                 this.color(
                                    "&cОтложенную выдачу сохранить не удалось, а автоматическое восстановление не подтверждено. Нужна ручная проверка."
                                 )
                              );
                              this.openError(
                                 var1,
                                 var2.item(),
                                 "&cНужна ручная проверка",
                                 "Статус покупки не удалось безопасно финализировать автоматически."
                              );
                           }
                        }
                     }
                  );
               }
            }
         );
      }
   }

   private void executeRepair(Player var1, long var2, String var3) {
      /*
      executeRepair(Player player, long price)
      BridgeTxnResult refund = bridge.refund(player, price, "artifact-repair-refund-"
      repair_manual_review
      bridge.refund
      */
            UUID repairPlayerUuid = var1.getUniqueId();
            String repairPlayerName = var1.getName();
            ItemStack var4 = var1.getInventory().getItemInMainHand();
      CopiMineArtifacts.CatalogItem var5 = this.authenticCatalogItem(var4, var1, "repair");
      if (var5 == null) {
         var1.sendMessage(
            this.color(
               "&cПоддельный предмет не принимается в ремонт."
            )
         );
      } else {
         if (!this.isArCatalogItem(var5.itemId())) {
            var1.sendMessage(this.color("&cРемонт за AR доступен только для AR-предметов лавки."));
            return;
         }
         if (var4.getItemMeta() instanceof Damageable var6 && var6.getDamage() > 0) {
            CopiMineArtifacts.SessionState repairState = this.session(var1);
            if (!repairState.repairInFlightId.isBlank()) {
               var1.sendMessage(this.color("&eПредыдущий ремонт ещё сохраняется. Подождите немного."));
               return;
            }
            repairState.repairInFlightId = UUID.randomUUID().toString();
            String repairId = repairState.repairInFlightId;
            // Bukkit inventory stacks are mutable and are owned by the main
            // thread. Capture an immutable snapshot before any async DB work;
            // the actual mutation is still validated against the exact slot
            // and current stack on the main thread.
            ItemStack capturedRepairItem = var4.clone();
            String repairItemUniqueId = this.uniqueItemIdOf(capturedRepairItem);
            int repairOriginalDamage = var6.getDamage();
            int repairSlot = var1.getInventory().getHeldItemSlot();
            String repairFingerprint = this.repairFingerprint(capturedRepairItem);
            repairState.repairSlot = repairSlot;
            repairState.repairFingerprint = repairFingerprint;
            repairState.repairOriginalDamage = repairOriginalDamage;
            repairState.repairItemUniqueId = repairItemUniqueId;
            // Keep the bridge idempotency key scoped to this repair attempt.
            // The database repair row and the refund path continue to use the
            // short repair id, while the economy bridge never receives a key
            // that can collide with a normal shop or transfer operation.
            String repairIdempotencyKey = "artifact-repair-" + repairId;
            if (var2 <= 0L) {
               this.runAsync(
                  () -> {
                     String var7 = repairId;
                     try {
                        this.persistRepair(repairPlayerUuid, repairPlayerName, var5, capturedRepairItem, var7, 0L, "free-repair-" + var7, repairSlot, repairFingerprint, repairOriginalDamage);
                        this.markRepairApplying(var7);
                        this.runSync(
                           () -> {
                              boolean applied = this.applyRepairToCapturedItem(var1, repairItemUniqueId, var5.itemId(), repairOriginalDamage, repairSlot, repairFingerprint);
                              if (!applied) {
                                 this.clearRepairState(repairState);
                                 this.runAsync(() -> this.markRepairNeedsReview(repairId));
                                 var1.sendMessage(this.color("&eПредмет был перемещён во время ремонта. Операция отправлена на ручную проверку."));
                                 return;
                              }
                              this.runAsync(() -> {
                                 boolean completed = this.completeRepair(repairId, repairItemUniqueId);
                                 this.runSync(() -> {
                                    this.clearRepairState(repairState);
                                    if (completed) {
                                       var1.sendMessage(this.color("&aРемонт завершён бесплатно."));
                                       var1.closeInventory();
                                    } else if (var1.isOnline()) {
                                       var1.sendMessage(this.color("&eРемонт физически применён, но база не подтвердила его. Предмет заблокирован и отправлен на ручную проверку."));
                                    }
                                 });
                              });
                              var1.sendMessage(this.color("&eРемонт применён, завершаю регистрацию в базе..."));
                           }
                        );
                     } catch (SQLException error) {
                        this.getLogger().log(Level.WARNING, "Free artifact repair persistence failed", error);
                        this.runSync(() -> {
                           this.clearRepairState(repairState);
                           if (var1.isOnline()) {
                              var1.sendMessage(this.color("&cНе удалось сохранить ремонт. Повтори попытку."));
                           }
                        });
                     }
                  }
               );
               return;
            }
            this.runAsync(
               () -> {
                  CopiMineArtifacts.BridgeTxnResult var6x = this.bridge
                     .transferToAccount(
                        repairPlayerUuid,
                        repairPlayerName,
                        var3 == null ? "" : var3,
                        PRESIDENT_BUDGET_ACCOUNT_ID,
                        EMPTY_UUID.toString(),
                        "Президентская казна",
                        var2,
                        repairIdempotencyKey,
                        "AR_ITEM_REPAIR",
                        "item=" + var5.itemId()
                     );
                  if (!var6x.ok()) {
                     this.runSync(
                        () -> { this.clearRepairState(repairState); var1.sendMessage(
                              this.color(
                                 "&cРемонт отклонён. Код: "
                                    + this.safeBridgeCode(var6x.code())
                              )
                           ); }
                     );
                  } else {
                     String var7 = repairId;

                     try {
                              this.persistRepair(repairPlayerUuid, repairPlayerName, var5, capturedRepairItem, var7, var2, var6x.txId(), repairSlot, repairFingerprint, repairOriginalDamage);
                              this.markRepairApplying(var7);
                     } catch (SQLException var10) {
                        CopiMineArtifacts.BridgeTxnResult var9 = this.bridge
                           .transferFromAccount(
                              PRESIDENT_BUDGET_ACCOUNT_ID,
                              EMPTY_UUID.toString(),
                              "Президентская казна",
                              repairPlayerUuid,
                              repairPlayerName,
                              var2,
                              "artifact-repair-refund-" + var7,
                              "AR_ITEM_REPAIR_REFUND",
                              "repair=" + var7
                           );
                        if (!var9.ok()) {
                           this.audit(repairPlayerName, "repair_manual_review", var7, var5.itemId() + " refund=" + this.safeBridgeCode(var9.code()));
                           this.getLogger()
                              .log(
                                 Level.WARNING,
                                 "Artifact repair refund failed after persist error: " + var7 + " / " + this.safeBridgeCode(var9.code()),
                                 (Throwable)var10
                              );
                        }

                        this.runSync(
                           () -> {
                              this.clearRepairState(repairState);
                              if (var9.ok()) {
                                 var1.sendMessage(
                                    this.color(
                                       "&cБаза недоступна. Ремонт отменён, AR возвращены."
                                    )
                                 );
                              } else {
                                 var1.sendMessage(
                                    this.color(
                                       "&cРемонт остановлен после списания AR. Автовозврат не подтверждён, нужна ручная проверка администрации."
                                    )
                                 );
                              }
                           }
                        );
                        return;
                     }

                     this.runSync(
                        () -> {
                           boolean applied = this.applyRepairToCapturedItem(var1, repairItemUniqueId, var5.itemId(), repairOriginalDamage, repairSlot, repairFingerprint);
                           if (!applied) {
                              this.clearRepairState(repairState);
                              this.runAsync(() -> {
                                 this.markRepairNeedsReview(repairId);
                                 this.refundRepairAfterPhysicalFailure(repairPlayerUuid, repairPlayerName, var2, repairId);
                              });
                              var1.sendMessage(this.color("&eПредмет перемещён во время ремонта. Деньги возвращаются, операция отправлена на проверку."));
                              return;
                           }
                           this.runAsync(() -> {
                              boolean completed = this.completeRepair(repairId, repairItemUniqueId);
                              this.runSync(() -> {
                                 this.clearRepairState(repairState);
                                 if (completed) {
                                    var1.sendMessage(this.color("&aРемонт завершён за &f" + var2 + " AR"));
                                    var1.closeInventory();
                                 } else if (var1.isOnline()) {
                                    var1.sendMessage(this.color("&eРемонт физически применён, но база не подтвердила его. Предмет заблокирован и отправлен на ручную проверку."));
                                 }
                              });
                           });
                           var1.sendMessage(this.color("&eРемонт применён, завершаю регистрацию в базе..."));
                        }
                     );
                  }
               }
            );
            return;
         }

         var1.sendMessage(
            this.color(
               "&eРемонт не требуется."
            )
         );
      }
   }

   private void clearRepairState(CopiMineArtifacts.SessionState state) {
      if (state == null) {
         return;
      }
      state.repairInFlightId = "";
      state.repairItemUniqueId = "";
      state.repairFingerprint = "";
      state.repairSlot = -1;
      state.repairOriginalDamage = 0;
   }

   private void claimPending(Player var1) {
      this.claimPendingV2(var1);
   }

   private void claimPendingV2(Player var1) {
      if (var1 == null || !this.claimAllInFlight.add(var1.getUniqueId())) {
         if (var1 != null) {
            var1.sendMessage(this.color("&eВыдача уже обрабатывается. Дождись завершения текущей операции."));
         }
         return;
      }
      UUID claimAllPlayerUuid = var1.getUniqueId();
      // The guard is released by the final delivery callback, not by a fixed
      // timer. Slow database work must not allow a second bulk operation to
      // overlap this one.
      boolean queued = this.runAsync(
         () -> {
            List<CopiMineArtifacts.PendingDeliveryRow> var2;
            try {
               var2 = this.readPending(var1.getUniqueId().toString());
            } catch (RuntimeException lookupError) {
               this.claimAllInFlight.remove(claimAllPlayerUuid);
               this.getLogger().log(Level.WARNING, "Pending delivery bulk lookup failed", lookupError);
               return;
            }
            this.readDonationClaimsAsync(var1.getUniqueId())
               .whenComplete(
                  (var3, var4) -> this.runSync(
                        () -> {
                           if (!var1.isOnline()) {
                              this.claimAllInFlight.remove(claimAllPlayerUuid);
                              return;
                           }
                           if (var4 != null) {
                              this.claimAllInFlight.remove(claimAllPlayerUuid);
                              this.getLogger().log(Level.WARNING, "Donation claims fetch failed", var4);
                              var1.sendMessage(this.color("&cНе удалось загрузить донат-выдачи. Попробуй ещё раз."));
                              return;
                           }
                           List<CopiMineArtifacts.DonationClaimRow> var5 = var3 == null ? List.of() : var3;
                           if (var2.isEmpty() && var5.isEmpty()) {
                              this.claimAllInFlight.remove(claimAllPlayerUuid);
                              var1.sendMessage(this.color("&eСейчас нет предметов, ожидающих выдачи."));
                              return;
                           }

                           int requiredClaimSlots = var2.size();
                           for (CopiMineArtifacts.DonationClaimRow var10 : var5) {
                              int required = this.requiredDonationSlots(var10.amount());
                              if (required < 0) {
                                 this.claimAllInFlight.remove(claimAllPlayerUuid);
                                 return;
                              }
                              requiredClaimSlots += required;
                           }
                           if (this.freeStorageSlots(var1.getInventory()) < requiredClaimSlots) {
                              this.claimAllInFlight.remove(claimAllPlayerUuid);
                              return;
                           }
                           AtomicInteger claimAllRemaining = new AtomicInteger(var2.size() + var5.size());
                           for (CopiMineArtifacts.PendingDeliveryRow var7 : var2) {
                              if (var1.getInventory().firstEmpty() < 0) {
                                 this.claimAllInFlight.remove(claimAllPlayerUuid);
                                 var1.sendMessage(this.color("&cИнвентарь заполнен. Освободи место и повтори команду."));
                                 return;
                              }
                              this.deliverPendingRowV2(var1, var7, this.claimAllCompletion(claimAllPlayerUuid, claimAllRemaining));
                           }

                           for (CopiMineArtifacts.DonationClaimRow var10 : var5) {
                              int var8 = this.requiredDonationSlots(var10.amount());
                              if (var8 < 0) {
                                 this.claimAllInFlight.remove(claimAllPlayerUuid);
                                 var1.sendMessage(this.color("&cЭта донат-выдача слишком большая для одного инвентаря. Нужен администратор."));
                                 return;
                              }
                              if (this.freeStorageSlots(var1.getInventory()) < var8) {
                                 this.claimAllInFlight.remove(claimAllPlayerUuid);
                                 var1.sendMessage(this.color("&eОсвободи место в инвентаре. Нужно свободных слотов: &f" + var8));
                                 return;
                              }
                              this.deliverDonationClaimRowV2(var1, var10, this.claimAllCompletion(claimAllPlayerUuid, claimAllRemaining));
                           }
                        }
                     )
               );
         }
      );
      if (!queued) {
         this.claimAllInFlight.remove(claimAllPlayerUuid);
      }
   }

   private Runnable claimAllCompletion(UUID var1, AtomicInteger var2) {
      return () -> {
         if (var2.decrementAndGet() <= 0) {
            this.claimAllInFlight.remove(var1);
         }
      };
   }

   private void claimOne(Player var1, String var2) {
      this.claimOneV2(var1, var2);
   }

   private void claimOneV2(Player var1, String var2) {
      this.runAsync(
         () -> {
            List<CopiMineArtifacts.PendingDeliveryRow> var3 = this.readPending(var1.getUniqueId().toString()).stream().filter(var1xx -> var1xx.deliveryId().equals(var2)).toList();
            if (!var3.isEmpty()) {
               this.runSync(() -> this.deliverPendingRowV2(var1, var3.getFirst()));
               return;
            }

            this.readDonationClaimsAsync(var1.getUniqueId())
               .whenComplete(
                  (var3x, var4) -> this.runSync(
                        () -> {
                           if (!var1.isOnline()) {
                              return;
                           }
                           if (var4 != null) {
                              this.getLogger().log(Level.WARNING, "Donation claim lookup failed", var4);
                              var1.sendMessage(this.color("&cНе удалось проверить donation-выдачу."));
                              return;
                           }

                           List<CopiMineArtifacts.DonationClaimRow> var5 = (var3x == null ? List.<CopiMineArtifacts.DonationClaimRow>of() : var3x)
                              .stream()
                              .filter(var1xxxx -> var1xxxx.claimId().equals(var2))
                              .toList();
                           if (var5.isEmpty()) {
                              var1.sendMessage(this.color("&cПредмет для выдачи не найден."));
                              return;
                           }

                           CopiMineArtifacts.DonationClaimRow var6 = var5.getFirst();
                           int var7 = this.requiredDonationSlots(var6.amount());
                           if (var7 < 0) {
                              var1.sendMessage(this.color("&cЭта донат-выдача слишком большая для одного инвентаря. Нужен администратор."));
                           } else if (this.freeStorageSlots(var1.getInventory()) < var7) {
                              var1.sendMessage(this.color("&eОсвободи место в инвентаре. Нужно свободных слотов: &f" + var7));
                           } else {
                              this.deliverDonationClaimRowV2(var1, var6);
                           }
                        }
                     )
               );
         }
      );
   }

   private void deliverPendingRow(Player var1, CopiMineArtifacts.PendingDeliveryRow var2) {
      this.deliverPendingRowV2(var1, var2);
   }

   private void deliverPendingRowV2(Player var1, CopiMineArtifacts.PendingDeliveryRow var2) {
      this.deliverPendingRowV2(var1, var2, () -> {});
   }

   private void deliverPendingRowV2(Player var1, CopiMineArtifacts.PendingDeliveryRow var2, Runnable completion) {
      AtomicBoolean completionCalled = new AtomicBoolean(false);
      Runnable finish = () -> {
         if (completionCalled.compareAndSet(false, true)) {
            completion.run();
         }
      };
      this.reservePendingDeliveryAsync(var1.getUniqueId(), var2.deliveryId())
         .whenComplete(
            (var3, var4) -> this.runSync(
                  () -> {
                     if (!var1.isOnline()) {
                        if (Boolean.TRUE.equals(var3)) {
                           this.releasePendingDeliveryAsync(var1.getUniqueId(), var2.deliveryId()).whenComplete((ignored, releaseError) -> finish.run());
                        } else {
                           finish.run();
                        }
                        return;
                     }
                     if (var4 != null) {
                        finish.run();
                        finish.run();
                        this.getLogger().log(Level.WARNING, "Pending delivery reservation failed", var4);
                        var1.sendMessage(this.color("&cНе удалось зарезервировать отложенную выдачу."));
                        return;
                     }
                     if (!Boolean.TRUE.equals(var3)) {
                        finish.run();
                        finish.run();
                        var1.sendMessage(this.color("&eЭта отложенная выдача уже обрабатывается или была получена."));
                        return;
                     }

                     CopiMineArtifacts.CatalogItem var5 = this.runtimeCatalogItem(var2.itemId());
                     if (var5 == null) {
                        this.releasePendingDeliveryAsync(var1.getUniqueId(), var2.deliveryId()).whenComplete((ignored, releaseError) -> finish.run());
                        var1.sendMessage(this.color("&cОтложенная выдача повреждена: предмет отсутствует в каталоге."));
                        return;
                     }
                     if (var1.getInventory().firstEmpty() < 0) {
                        this.releasePendingDeliveryAsync(var1.getUniqueId(), var2.deliveryId()).whenComplete((ignored, releaseError) -> finish.run());
                        var1.sendMessage(this.color("&cИнвентарь заполнен. Отложенная выдача не потеряна."));
                        return;
                     }

                     ItemStack var6 = this.decorateAdminGrantedItem(
                           this.createOfficialItem(var5, var2.uniqueItemId(), var1.getUniqueId(), var2.purchaseId()), var5);
                     this.cacheProvisionalDonationInstances(var2.itemId(), var1.getUniqueId(), List.of(var2.uniqueItemId()));
                     HashMap var7 = var1.getInventory().addItem(new ItemStack[]{var6});
                     if (!var7.isEmpty()) {
                        this.removeProvisionalDonationInstances(List.of(var2.uniqueItemId()));
                        this.releasePendingDeliveryAsync(var1.getUniqueId(), var2.deliveryId()).whenComplete((ignored, releaseError) -> finish.run());
                        var1.sendMessage(this.color("&cИнвентарь заполнен. Отложенная выдача не потеряна."));
                        return;
                     }
                     if (!this.playerHasOfficialInstance(var1, var2.uniqueItemId(), var2.itemId(), var1.getUniqueId())) {
                        finish.run();
                        this.runAsync(() -> {
                           try {
                              this.markArtifactPurchaseReview(var2.purchaseId(), var2.uniqueItemId());
                              this.audit(var1.getName(), "purchase_manual_review", var2.purchaseId(), var2.itemId() + " reason=pending_physical_delivery_missing");
                           } catch (SQLException reviewError) {
                              this.getLogger().log(Level.WARNING, "Pending delivery review mark failed after missing physical delivery: " + var2.deliveryId(), reviewError);
                           }
                        });
                        var1.sendMessage(this.color("&eФизическая выдача не подтверждена. Покупка отправлена на ручную проверку; повторная копия не будет выдана автоматически."));
                        return;
                     }
                     // Keep the physical item provisional until the DB
                     // transition is committed.  authenticCatalogItem() and
                     // all storage paths fail closed while this marker is
                     // present, so a crash cannot expose a live instance
                     // whose DELIVERING row is still unresolved.
                     boolean finalizationQueued = this.runAsync(() -> {
                        try {
                           this.markPendingClaimed(var2.deliveryId(), var2.purchaseId(), var2.uniqueItemId(), var2.itemId());
                           this.runSync(() -> {
                              if (var1.isOnline()) {
                                 this.cacheOfficialBinding(var2.uniqueItemId(), var2.itemId(), var1.getUniqueId());
                                 this.removeProvisionalDonationInstances(List.of(var2.uniqueItemId()));
                                 var1.sendMessage(this.color("&aОтложенная выдача получена: &f" + this.strip(var5.name())));
                              }
                           });
                        } catch (SQLException var3xx) {
                           try {
                              this.markArtifactPurchaseReview(var2.purchaseId(), var2.uniqueItemId());
                              this.audit(var1.getName(), "purchase_manual_review", var2.purchaseId(), var2.itemId() + " reason=pending_delivery_finalize_failed");
                           } catch (SQLException var4xx) {
                              this.getLogger().log(Level.WARNING, "Pending delivery review mark failed after finalize error: " + var2.deliveryId(), var4xx);
                           }
                           // Leave the item provisional and physically
                           // present.  Recovery on the next join can prove
                           // the exact instance and finalize/review it; it is
                           // never re-issued as a second copy.
                           this.getLogger().log(Level.WARNING, "Pending delivery finalization failed: " + var2.deliveryId(), var3xx);
                           this.runSync(() -> {
                              if (var1.isOnline()) {
                                 var1.sendMessage(this.color("&eВыдача сохранена на проверку. Предмет временно заблокирован до подтверждения базы."));
                              }
                           });
                        } finally {
                           finish.run();
                        }
                     });
                     if (!finalizationQueued) {
                        finish.run();
                     }
                  }
               )
         );
   }

   private void deliverPendingRowLegacy(Player var1, CopiMineArtifacts.PendingDeliveryRow var2) {
      this.deliverPendingRowV2(var1, var2);
   }

   private void deliverDonationClaimRow(Player var1, CopiMineArtifacts.DonationClaimRow var2) {
      this.deliverDonationClaimRowV2(var1, var2);
   }

   private void deliverDonationClaimRowV2(Player var1, CopiMineArtifacts.DonationClaimRow var2) {
      this.deliverDonationClaimRowV2(var1, var2, () -> {});
   }

   private void deliverDonationClaimRowV2(Player var1, CopiMineArtifacts.DonationClaimRow var2, Runnable completion) {
      AtomicBoolean completionCalled = new AtomicBoolean(false);
      Runnable finish = () -> {
         if (completionCalled.compareAndSet(false, true)) {
            completion.run();
         }
      };
      this.reserveDonationClaimAsync(var1.getUniqueId(), var2.claimId())
         .whenComplete(
            (var3, var4) -> this.runSync(
                  () -> {
                     if (!var1.isOnline()) {
                        if (Boolean.TRUE.equals(var3)) {
                           this.releaseDonationClaimAsync(var1.getUniqueId(), var2.claimId()).whenComplete((ignored, releaseError) -> finish.run());
                        } else {
                           finish.run();
                        }
                        return;
                     }
                     if (var4 != null) {
                        finish.run();
                        this.getLogger().log(Level.WARNING, "Donation claim reservation failed", var4);
                        var1.sendMessage(this.color("&cНе удалось зарезервировать донат-выдачу."));
                        return;
                     }
                     if (!Boolean.TRUE.equals(var3)) {
                        finish.run();
                        var1.sendMessage(this.color("&eЭтот донат-предмет уже забирается или был выдан."));
                        return;
                     }

                     CopiMineArtifacts.CatalogItem var5 = this.runtimeCatalogItem(var2.itemId());
                     if (var5 == null) {
                        this.releaseDonationClaimAsync(var1.getUniqueId(), var2.claimId()).whenComplete((ignored, releaseError) -> finish.run());
                        var1.sendMessage(this.color("&cДонат-предмет недоступен в каталоге."));
                        return;
                     }
                     if (var2.amount() != 1L) {
                        this.reviewDonationClaimAsync(var1.getUniqueId(), var2.claimId()).whenComplete((ignored, reviewError) -> finish.run());
                        var1.sendMessage(this.color("&cВыдача этого donation claim отправлена на ручную проверку. Один claim должен создавать только один owner-bound предмет."));
                        return;
                     }

                     if ("TAX_CLOCK".equalsIgnoreCase(var5.effect())) {
                        this.markDonationClaimDeliveringAsync(var1.getUniqueId(), var2.claimId()).whenComplete((marked, markError) -> {
                           if (markError != null || !Boolean.TRUE.equals(marked)) {
                              this.reviewDonationClaimAsync(var1.getUniqueId(), var2.claimId()).whenComplete((ignored, reviewError) -> finish.run());
                              return;
                           }
                           String instanceId = var2.purchaseId() == null || var2.purchaseId().isBlank() ? "tax-subscription-" + var2.claimId() : var2.purchaseId();
                           this.grantTaxClockExemptionAsync(var1.getUniqueId(), var1.getName(), instanceId).whenComplete((expiresAt, grantError) -> this.runSync(() -> {
                              if (grantError != null || expiresAt == null || expiresAt <= 0L) {
                                 this.reviewDonationClaimAsync(var1.getUniqueId(), var2.claimId()).whenComplete((ignored, reviewError) -> finish.run());
                                 var1.sendMessage(this.color("&cНе удалось активировать подписку. Обратись к администрации."));
                                 return;
                              }
                              this.completeDonationClaimAsync(var1.getUniqueId(), var2.claimId()).whenComplete((completed, completeError) -> this.runSync(() -> {
                                 finish.run();
                                 if (completeError != null || !Boolean.TRUE.equals(completed)) {
                                    this.reviewDonationClaimAsync(var1.getUniqueId(), var2.claimId()).whenComplete((ignored, reviewError) -> finish.run());
                                    return;
                                 }
                                 var1.sendMessage(this.color("&6Ты теперь супергражданин! &7Налоговая подписка активна до: &f" + Instant.ofEpochMilli(expiresAt)));
                              }));
                           }));
                        });
                        return;
                     }

                     int var6 = this.requiredDonationSlots(var2.amount());
                     if (var6 < 0) {
                        this.releaseDonationClaimAsync(var1.getUniqueId(), var2.claimId()).whenComplete((ignored, releaseError) -> finish.run());
                        var1.sendMessage(this.color("&cЭта донат-выдача слишком большая для одного инвентаря. Нужен администратор."));
                        return;
                     }
                     if (this.freeStorageSlots(var1.getInventory()) < var6) {
                        this.releaseDonationClaimAsync(var1.getUniqueId(), var2.claimId()).whenComplete((ignored, releaseError) -> finish.run());
                        var1.sendMessage(this.color("&eОсвободи место в инвентаре. Нужно свободных слотов: &f" + var6));
                        return;
                     }

                     String var7 = var2.purchaseId() != null && !var2.purchaseId().isBlank() ? var2.purchaseId() : "donation-" + var2.claimId();
                     ArrayList<String> var8 = new ArrayList<>(var6);
                     for (int var9 = 0; var9 < var6; var9++) {
                        var8.add(UUID.randomUUID().toString());
                     }

                     this.prepareDonationDeliveryAsync(var1.getUniqueId(), var2.claimId(), var7, var5.itemId(), var8)
                        .whenComplete(
                           (var7x, var8x) -> this.runSync(
                                 () -> {
                                    if (!var1.isOnline()) {
                                       if (Boolean.TRUE.equals(var7x)) {
                                          this.failDonationDeliveryAsync(var1.getUniqueId(), var2.claimId(), var8).whenComplete((ignored, failError) -> finish.run());
                                       } else {
                                          this.releaseDonationClaimAsync(var1.getUniqueId(), var2.claimId()).whenComplete((ignored, releaseError) -> finish.run());
                                       }
                                       return;
                                    }
                                    if (var8x == null && Boolean.TRUE.equals(var7x)) {
                                       this.cacheProvisionalDonationInstances(var5.itemId(), var1.getUniqueId(), var8);
                                       ArrayList<String> var9x = new ArrayList<>(var8.size());

                                       for (String var11 : var8) {
                                          ItemStack var12 = this.createOfficialItem(var5, var11, var1.getUniqueId(), var7);
                                          HashMap var13 = var1.getInventory().addItem(new ItemStack[]{var12});
                                          if (!var13.isEmpty()) {
                                             this.removeOfficialItemsFromInventory(var1.getInventory(), var9x);
                                             this.removeProvisionalDonationInstances(var8);
                                             this.failDonationDeliveryAsync(var1.getUniqueId(), var2.claimId(), var8).whenComplete((ignored, failError) -> finish.run());
                                             var1.sendMessage(this.color("&cИнвентарь изменился во время выдачи. Выдача отправлена на ручную проверку и не будет автоматически повторена."));
                                             return;
                                          }
                                          var9x.add(var11);
                                       }

                                       // All instances were inserted into the
                                       // player's inventory, but the database
                                       // transition must not be finalized
                                       // unless every exact unique id is still
                                       // physically present.  This closes the
                                       // small window where another plugin (or
                                       // a disconnect race) removes one item
                                       // before the async claim finalization;
                                       // review is safer than making the
                                       // missing instance claimable twice.
                                       boolean physicalDeliveryPresent = var8.stream()
                                          .allMatch(uniqueId -> this.playerHasOfficialInstance(var1, uniqueId, var5.itemId(), var1.getUniqueId()));
                                       if (!physicalDeliveryPresent) {
                                          this.reviewDonationClaimAsync(var1.getUniqueId(), var2.claimId()).whenComplete((ignored, reviewError) -> finish.run());
                                          this.removeOfficialItemsFromInventory(var1.getInventory(), var9x);
                                          this.removeProvisionalDonationInstances(var8);
                                          var1.sendMessage(this.color("&eФизическая донат-выдача не подтверждена. Заявка отправлена на ручную проверку; повторная копия не будет выдана автоматически."));
                                          return;
                                       }

                                       this.finalizeDonationDeliveryAsync(var1.getUniqueId(), var2.claimId(), var7, var5.itemId(), var8)
                                          .whenComplete(
                                             (var3xxxx, var4xxxx) -> {
                                                finish.run();
                                                if (var4xxxx != null || !Boolean.TRUE.equals(var3xxxx)) {
                                                   this.getLogger().log(Level.WARNING, "Donation claim was delivered but completion failed: " + var2.claimId(), var4xxxx);
                                                   this.reviewDonationClaimAsync(var1.getUniqueId(), var2.claimId());
                                                } else {
                                                   this.runSync(() -> {
                                                      for (String var11 : var9x) {
                                                         this.cacheOfficialBinding(var11, var5.itemId(), var1.getUniqueId());
                                                      }
                                                      this.removeProvisionalDonationInstances(var8);
                                                      if (var1.isOnline()) {
                                                         var1.sendMessage(this.color("&aДонат-предмет получен: &f" + this.strip(var5.name()) + " &7x" + var6));
                                                      }
                                                   });
                                                }
                                             }
                                          );
                                       this.audit(var1.getName(), "donation_claim_delivered", var2.claimId(), var5.itemId());
                                       var1.sendMessage(this.color("&7Выдача подготовлена; ожидается финализация базы..."));
                                    } else {
                                       if (Boolean.TRUE.equals(var7x)) {
                                          this.failDonationDeliveryAsync(var1.getUniqueId(), var2.claimId(), var8).whenComplete((ignored, failError) -> finish.run());
                                       } else {
                                          this.releaseDonationClaimAsync(var1.getUniqueId(), var2.claimId()).whenComplete((ignored, releaseError) -> finish.run());
                                       }
                                       this.getLogger().log(Level.WARNING, "Donation claim prepare failed: " + var2.claimId(), var8x);
                                       var1.sendMessage(this.color("&cНе удалось подготовить донат-выдачу. Попробуй ещё раз."));
                                    }
                                 }
                              )
                        );
                  }
               )
         );
   }

   private CompletableFuture<List<CopiMineArtifacts.DonationClaimRow>> readDonationClaimsAsync(UUID var1) {
      DonationPurchaseService var2 = this.donationPurchaseService();
      if (var2 == null || var1 == null) {
         return CompletableFuture.completedFuture(List.of());
      }

      return var2.getUnclaimedItemsAsync(var1, 200)
         .thenApply(
            var1x -> {
               ArrayList<CopiMineArtifacts.DonationClaimRow> var2x = new ArrayList<>();
               for (Map<String, Object> var4 : var1x == null ? List.<Map<String, Object>>of() : var1x) {
                  String var5 = this.firstNonBlank(this.str(var4.get("id")), this.str(var4.get("claim_id")));
                  String var6 = this.firstNonBlank(this.str(var4.get("purchase_id")), "");
                  String var7 = this.firstNonBlank(this.str(var4.get("item_id")), "").toLowerCase(Locale.ROOT);
                  long var8 = Math.max(1L, this.parseLong(this.str(var4.get("amount")), 1L));
                  String var10 = this.firstNonBlank(this.str(var4.get("status")), "UNCLAIMED");
                  if (!var5.isBlank() && !var7.isBlank()) {
                     var2x.add(new CopiMineArtifacts.DonationClaimRow(var5, var6, var7, var8, var10));
                  }
               }
               return var2x;
            }
         );
   }

   private CompletableFuture<Boolean> reserveDonationClaimAsync(UUID var1, String var2) {
      DonationPurchaseService var3 = this.donationPurchaseService();
      return var3 != null && var1 != null && var2 != null && !var2.isBlank() ? var3.reserveClaimAsync(var1, var2) : CompletableFuture.completedFuture(false);
   }

   private CompletableFuture<Boolean> markDonationClaimDeliveringAsync(UUID var1, String var2) {
      DonationPurchaseService var3 = this.donationPurchaseService();
      return var3 != null && var1 != null && var2 != null && !var2.isBlank() ? var3.markClaimDeliveringAsync(var1, var2) : CompletableFuture.completedFuture(false);
   }

   private CompletableFuture<Boolean> completeDonationClaimAsync(UUID var1, String var2) {
      DonationPurchaseService var3 = this.donationPurchaseService();
      return var3 != null && var1 != null && var2 != null && !var2.isBlank() ? var3.completeClaimAsync(var1, var2) : CompletableFuture.completedFuture(false);
   }

   private CompletableFuture<Boolean> completeDonationClaimByPurchaseAsync(UUID var1, String var2) {
      DonationPurchaseService var3 = this.donationPurchaseService();
      return var3 != null && var1 != null && var2 != null && !var2.isBlank() ? var3.completeClaimByPurchaseAsync(var1, var2) : CompletableFuture.completedFuture(false);
   }

   private CopiMineArtifacts.ReclaimableDonationRow findReclaimableDonationRow(String var1, String var2) {
      if (this.firstNonBlank(var1, "").isBlank() || this.firstNonBlank(var2, "").isBlank()) {
         return null;
      }

      for (CopiMineArtifacts.ReclaimableDonationRow var4 : this.readReclaimableDonationRows(var1, 54)) {
         if (var2.equalsIgnoreCase(var4.uniqueItemId())) {
            return var4;
         }
      }
      return null;
   }

   private void reclaimDonationItemSafe(Player var1, String var2) {
      if (this.customShopItemsAreVanilla()) {
         if (var1 != null && var1.isOnline()) {
            var1.sendMessage(this.color("&eВозврат donation-предметов отключён. Потерянный или уничтоженный предмет нужно купить заново."));
         }
         return;
      }
      if (this.freeStorageSlots(var1.getInventory()) < 1) {
         var1.sendMessage(this.color("&eОсвободи хотя бы один слот в инвентаре, чтобы вернуть предмет."));
         return;
      }

      this.runAsync(
         () -> {
            CopiMineArtifacts.ReclaimableDonationRow var3 = this.findReclaimableDonationRow(var1.getUniqueId().toString(), var2);
            if (var3 == null) {
               try {
                  this.reconcileDonationLossJournal();
                  var3 = this.findReclaimableDonationRow(var1.getUniqueId().toString(), var2);
               } catch (Exception reconcileError) {
                  this.getLogger().log(Level.WARNING, "Donation loss journal reconcile before reclaim failed", reconcileError);
               }
            }
            if (var3 == null) {
               this.runSync(() -> {
                  if (var1.isOnline()) {
                     var1.sendMessage(this.color("&cЭтот предмет уже нельзя вернуть или он уже обработан."));
                  }
               });
               return;
            }

            CopiMineArtifacts.DonationCatalogItem var4 = this.donationCatalogItem(var3.itemId());
            CopiMineArtifacts.CatalogItem var5 = this.runtimeCatalogItem(var3.itemId());
            if (var5 == null) {
               this.runSync(() -> {
                  if (var1.isOnline()) {
                     var1.sendMessage(this.color("&cКаталог этого донат-предмета сейчас недоступен."));
                  }
               });
               return;
            }
            if (var4 == null || !"LOSS_ONLY".equalsIgnoreCase(this.firstNonBlank(var4.reclaimPolicy(), "LOSS_ONLY"))) {
               this.runSync(() -> {
                  if (var1.isOnline()) {
                     var1.sendMessage(this.color("&cДля этого предмета бесплатный возврат после потери отключён."));
                  }
               });
               return;
            }

            CopiMineArtifacts.DonationReclaimContext var6;
            try {
               var6 = this.prepareDonationReclaim(var1.getUniqueId(), var3);
            } catch (SQLException var10) {
               this.getLogger().log(Level.WARNING, "Donation reclaim prepare failed", var10);
               this.runSync(() -> {
                  if (var1.isOnline()) {
                     var1.sendMessage(this.color("&cНе удалось подготовить возврат. Попробуй ещё раз позже."));
                  }
               });
               return;
            }

            this.runSync(
               () -> {
                  if (!var1.isOnline()) {
                     this.runAsync(() -> {
                        try {
                           this.rollbackDonationReclaim(var1.getUniqueId(), var6);
                        } catch (SQLException var4x) {
                           this.getLogger().log(Level.WARNING, "Donation reclaim rollback failed after quit", var4x);
                        }
                     });
                     return;
                  }
                  if (this.freeStorageSlots(var1.getInventory()) < 1) {
                     this.runAsync(() -> {
                        try {
                           this.rollbackDonationReclaim(var1.getUniqueId(), var6);
                        } catch (SQLException var4x) {
                           this.getLogger().log(Level.WARNING, "Donation reclaim rollback failed after inventory change", var4x);
                        }
                     });
                     var1.sendMessage(this.color("&cИнвентарь изменился во время возврата. Освободи место и попробуй ещё раз."));
                     return;
                  }
                  if (this.playerHasOfficialInstance(var1, var6.oldUniqueItemId(), var6.itemId(), var1.getUniqueId())) {
                     this.runAsync(() -> {
                        try {
                           this.rollbackDonationReclaim(var1.getUniqueId(), var6);
                        } catch (SQLException var4x) {
                           this.getLogger().log(Level.WARNING, "Donation reclaim rollback failed after original item reappeared", var4x);
                        }
                     });
                     var1.sendMessage(this.color("&cСтарый экземпляр предмета снова найден в инвентаре. Возврат отменён, повторный предмет не выдан."));
                     return;
                  }

                  ItemStack var4x = this.createOfficialItem(var5, var6.newUniqueItemId(), var1.getUniqueId(), var6.purchaseId());
                  this.cacheProvisionalDonationInstances(var5.itemId(), var1.getUniqueId(), List.of(var6.newUniqueItemId()));
                  HashMap var5x = var1.getInventory().addItem(new ItemStack[]{var4x});
                  if (!var5x.isEmpty()) {
                     this.removeProvisionalDonationInstances(List.of(var6.newUniqueItemId()));
                     this.runAsync(() -> {
                        try {
                           this.rollbackDonationReclaim(var1.getUniqueId(), var6);
                        } catch (SQLException var4xx) {
                           this.getLogger().log(Level.WARNING, "Donation reclaim rollback failed after addItem leftovers", var4xx);
                        }
                     });
                     var1.sendMessage(this.color("&cИнвентарь изменился во время возврата. Освободи место и попробуй ещё раз."));
                     return;
                  }

                  this.cacheOfficialBinding(var6.newUniqueItemId(), var6.itemId(), var1.getUniqueId());
                  this.removeProvisionalDonationInstances(List.of(var6.newUniqueItemId()));
                  this.finalizeDonationReclaimAsync(var1.getUniqueId(), var6)
                     .whenComplete(
                        (var7, var8) -> this.runSync(
                              () -> {
                                 if (var8 == null && Boolean.TRUE.equals(var7)) {
                                    this.audit(var1.getName(), "donation_reclaim_issued", var6.newUniqueItemId(), var5.itemId());
                                    if (var1.isOnline()) {
                                       var1.sendMessage(this.color("&aПотерянный донат-предмет возвращён: &f" + this.strip(var5.name())));
                                       this.openDonationReclaim(var1);
                                    }
                                 } else {
                                    this.getLogger().log(Level.WARNING, "Donation reclaim finalize failed", var8);
                                    this.audit(var1.getName(), "donation_reclaim_review", var6.newUniqueItemId(), var5.itemId());
                                    if (var1.isOnline()) {
                                       var1.sendMessage(this.color("&eПредмет уже выдан, но финализация ушла на ручную проверку. Повторный reclaim автоматически не откроется."));
                                    }
                                 }
                              }
                           )
                     );
               }
            );
         }
      );
   }

   private CompletableFuture<Boolean> reviewDonationClaimAsync(UUID var1, String var2) {
      DonationPurchaseService var3 = this.donationPurchaseService();
      return var3 != null && var1 != null && var2 != null && !var2.isBlank()
         ? var3.markClaimDeliveryReviewAsync(var1, var2)
         : CompletableFuture.completedFuture(false);
   }

   private CompletableFuture<Boolean> releaseDonationClaimAsync(UUID var1, String var2) {
      DonationPurchaseService var3 = this.donationPurchaseService();
      return var3 != null && var1 != null && var2 != null && !var2.isBlank() ? var3.releaseClaimAsync(var1, var2) : CompletableFuture.completedFuture(false);
   }

   private DonationBalanceService donationBalanceService() {
      Plugin var1 = Bukkit.getPluginManager().getPlugin("CopiMineEconomyCore");
      if (var1 instanceof CopiMineEconomyCore var2 && var1.isEnabled()) {
         try {
            return var2.donationBalanceService();
         } catch (Exception var4) {
            return null;
         }
      }

      return null;
   }

   private DonationPaymentService donationPaymentService() {
      Plugin var1 = Bukkit.getPluginManager().getPlugin("CopiMineEconomyCore");
      if (var1 instanceof CopiMineEconomyCore var2 && var1.isEnabled()) {
         try {
            return var2.donationPaymentService();
         } catch (Exception var4) {
            return null;
         }
      }

      return null;
   }

   private DonationPurchaseService donationPurchaseService() {
      Plugin var1 = Bukkit.getPluginManager().getPlugin("CopiMineEconomyCore");
      if (var1 instanceof CopiMineEconomyCore var2 && var1.isEnabled()) {
         try {
            return var2.donationPurchaseService();
         } catch (Exception var4) {
            return null;
         }
      }

      return null;
   }

   private CopiMineArtifacts.ShopRevenueRecipient resolveActivePresidentRevenueRecipient() {
      ElectionRuntimeService service = Bukkit.getServicesManager().load(ElectionRuntimeService.class);
      if (service == null) {
         return treasuryRevenueRecipient();
      }

      try {
          Map<String, Object> var2 = service.activePresidentRevenueProfile();
          if (var2 == null) {
             return treasuryRevenueRecipient();
          }
          Map<?, ?> var3 = var2;

          String var4 = this.firstNonBlank(this.str(var3.get("president_uuid")), "");
          String termId = this.firstNonBlank(this.str(var3.get("term_id")), "");
          if (var4.isBlank() || termId.isBlank()) {
             return treasuryRevenueRecipient();
          }
          UUID var5 = UUID.fromString(var4);
          return new CopiMineArtifacts.ShopRevenueRecipient(
             var5,
             TREASURY_LABEL,
             termId,
             this.firstNonBlank(this.str(var3.get("budget_account_id")), PRESIDENT_BUDGET_ACCOUNT_ID)
          );
      } catch (Exception var6) {
         this.getLogger().log(Level.WARNING, "Failed to resolve active president for artifact revenue", (Throwable)var6);
         return treasuryRevenueRecipient();
      }
   }

   private CopiMineArtifacts.ShopRevenueRecipient treasuryRevenueRecipient() {
      return new CopiMineArtifacts.ShopRevenueRecipient(
         EMPTY_UUID,
         TREASURY_LABEL,
         "NO_ACTIVE_PRESIDENT",
         PRESIDENT_BUDGET_ACCOUNT_ID
      );
   }

   private void triggerRevenuePayoutAsync(String var1) {
      if (var1 != null && !var1.isBlank() && this.bridge != null) {
         this.runAsync(() -> {
            try {
               this.processRevenuePayout(var1);
            } catch (Exception var3) {
               this.getLogger().log(Level.WARNING, "Artifact revenue payout failed for " + var1, (Throwable)var3);
            }
         });
      }
   }

   private void reconcilePendingRevenuePayouts() {
      if (this.pgPool != null && this.bridge != null) {
         try {
            for (Map<String, Object> var2 : this.readPendingRevenuePayouts(128)) {
               String var3 = this.firstNonBlank(this.str(var2.get("purchase_id")), "");
               if (!var3.isBlank()) {
                  this.processRevenuePayout(var3);
               }
            }
         } catch (Exception var4) {
            this.getLogger().log(Level.WARNING, "Artifact revenue payout reconcile failed", (Throwable)var4);
         }
      }
   }

   private boolean hasArtifactIdentity(ItemStack stack) {
      if (stack == null || stack.getType() == Material.AIR || !stack.hasItemMeta()) {
         return false;
      }
      PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
      String itemId = this.firstNonBlank(pdc.get(this.keyItemId, PersistentDataType.STRING), "");
      String uniqueId = this.firstNonBlank(pdc.get(this.keyUniqueItemId, PersistentDataType.STRING), "");
      return !itemId.isBlank() && !uniqueId.isBlank();
   }

   private void refreshEquippedArtifactBindingsAsync(Player player) {
      if (player == null) {
         return;
      }
      ItemStack[] equipped = {
         player.getInventory().getItemInMainHand(),
         player.getInventory().getItemInOffHand(),
         player.getInventory().getHelmet(),
         player.getInventory().getChestplate(),
         player.getInventory().getLeggings(),
         player.getInventory().getBoots()
      };
      for (ItemStack stack : equipped) {
         if (this.hasArtifactIdentity(stack)) {
            this.refreshOfficialBindingAsync(player, stack, "join");
         }
      }
   }

   private void refreshOfficialBindingAsync(Player player, ItemStack stack, String context) {
      if (player == null || !player.isOnline() || !this.hasArtifactIdentity(stack)) {
         return;
      }
      PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
      String uniqueId = this.firstNonBlank(pdc.get(this.keyUniqueItemId, PersistentDataType.STRING), "");
      String itemId = this.firstNonBlank(pdc.get(this.keyItemId, PersistentDataType.STRING), "").toLowerCase(Locale.ROOT);
      if (uniqueId.isBlank() || itemId.isBlank() || !this.bindingRefreshInFlight.add(uniqueId)) {
         return;
      }
      UUID ownerUuid = player.getUniqueId();
      this.runAsync(() -> {
         boolean refreshed = false;
         try {
            Connection connection = this.pgPool.acquire();
            try {
               PreparedStatement query = connection.prepareStatement(
                  // Shield/item refreshes also inspect in-flight rows so a
                  // delivery cannot be mistaken for a missing binding.  Only
                  // DELIVERED/ACTIVE rows are admitted to the gameplay cache
                  // below; the in-flight states are deliberately fail-closed.
                  "SELECT item_id,owner_uuid,status FROM artifact_item_instances WHERE unique_item_id=? AND item_id=? AND owner_uuid=? AND status IN ('DELIVERED','ACTIVE','DELIVERING','PENDING_DELIVERY') LIMIT 1"
               );
               try {
                  query.setString(1, uniqueId);
                  query.setString(2, itemId);
                  query.setString(3, ownerUuid.toString());
                  ResultSet result = query.executeQuery();
                  try {
                     if (result.next()) {
                        String status = this.firstNonBlank(result.getString(3), "");
                        if ("DELIVERED".equalsIgnoreCase(status) || "ACTIVE".equalsIgnoreCase(status)) {
                           this.cacheOfficialBinding(uniqueId, result.getString(1), result.getString(2));
                           refreshed = true;
                        }
                     }
                  } finally {
                     result.close();
                  }
               } finally {
                  query.close();
               }
            } finally {
               this.pgPool.release(connection);
            }
         } catch (Exception error) {
            this.getLogger().log(Level.FINE, "Artifact binding refresh failed for " + uniqueId, error);
         } finally {
            this.bindingRefreshInFlight.remove(uniqueId);
         }
         boolean success = refreshed;
         this.runSync(() -> {
            if (!player.isOnline()) {
               return;
            }
            if (success && "repair_open".equals(context)) {
               this.openRepair(player);
            } else if (!success && "repair_open".equals(context)) {
               player.sendMessage(this.color("&cВ руке должен быть официальный предмет CopiMineArtifacts."));
            }
         });
      });
   }

   private void reconcileOrphanedShopTransfers() {
      if (this.pgPool != null && this.bridge != null) {
         for (CopiMineArtifacts.OrphanedShopTransfer var2 : this.readOrphanedShopTransfers(128)) {
            String var3 = this.firstNonBlank(var2.idempotencyKey(), "");
            String var4 = "artifact-purchase-";
            if (!var3.startsWith(var4)) {
               continue;
            }

            String var5 = var3.substring(var4.length());
            if (var5.isBlank() || var2.amount() <= 0L || this.firstNonBlank(var2.recipientAccountId(), "").isBlank()) {
               continue;
            }

            UUID var6;
            try {
               var6 = UUID.fromString(var2.playerUuid());
            } catch (IllegalArgumentException var10) {
               this.getLogger().warning("Skipping orphaned artifact shop transfer with invalid player UUID: " + var2.transactionId());
               continue;
            }

            CopiMineArtifacts.BridgeTxnResult var7 = this.bridge.transferFromAccount(
               var2.recipientAccountId(),
               EMPTY_UUID.toString(),
               TREASURY_LABEL,
               var6,
               this.firstNonBlank(var2.playerName(), "Игрок"),
               var2.amount(),
               "artifact-orphan-refund-" + var5,
               "AR_SHOP_PURCHASE_RECOVERY_REFUND",
               "purchase=" + var5 + ";source_tx=" + var2.transactionId()
            );
            if (var7.ok()) {
               this.audit("SERVER", "purchase_orphan_refunded", var5, "source_tx=" + var2.transactionId() + " amount=" + var2.amount());
            } else {
               this.getLogger().warning(
                  "Artifact orphaned shop transfer refund is pending manual retry: " + var2.transactionId() + " / " + this.safeBridgeCode(var7.code())
               );
            }
         }
      }
   }

   private List<CopiMineArtifacts.OrphanedShopTransfer> readOrphanedShopTransfers(int var1) {
      if (this.bridge == null) {
         return List.of();
      }
      try {
         List<Map<String, Object>> rows = this.bridge.findOrphanedArtifactShopTransfers(var1);
         List<CopiMineArtifacts.OrphanedShopTransfer> result = new ArrayList<>();
         for (Map<String, Object> row : rows) {
            result.add(new CopiMineArtifacts.OrphanedShopTransfer(
               this.firstNonBlank(this.str(row.get("tx_id")), ""),
               this.firstNonBlank(this.str(row.get("to_account_id")), ""),
               Math.max(0L, this.parseLong(this.str(row.get("amount")), 0L)),
               this.firstNonBlank(this.str(row.get("idempotency_key")), ""),
               this.firstNonBlank(this.str(row.get("player_uuid")), ""),
               this.firstNonBlank(this.str(row.get("actor")), "")
            ));
         }
         return result;
      } catch (Exception error) {
         this.getLogger().log(Level.WARNING, "Artifact orphaned shop transfer reconciliation lookup failed", error);
         return List.of();
      }
   }

   private List<Map<String, Object>> readPendingRevenuePayouts(int var1) throws SQLException {
      Connection var2 = this.pgPool.acquire();

      try {
         PreparedStatement var3 = var2.prepareStatement(
            "SELECT purchase_id,president_uuid,president_name,recipient_account_id,amount_ar,item_id,buyer_uuid,buyer_name,idempotency_key,status FROM artifact_revenue_payouts WHERE status='PENDING' ORDER BY created_at ASC LIMIT ?"
         );

         try {
            var3.setInt(1, Math.max(1, var1));
            ResultSet var4 = var3.executeQuery();

            try {
               return this.readRows(var4);
            } finally {
               var4.close();
            }
         } finally {
            var3.close();
         }
      } finally {
         this.pgPool.release(var2);
      }
   }

   private List<Map<String, Object>> readRows(ResultSet var1) throws SQLException {
      ArrayList<Map<String, Object>> var2 = new ArrayList<>();
      ResultSetMetaData var3 = var1.getMetaData();
      int var4 = var3.getColumnCount();

      while (var1.next()) {
         LinkedHashMap<String, Object> var5 = new LinkedHashMap<>();

         for (int var6 = 1; var6 <= var4; ++var6) {
            String var7 = this.firstNonBlank(var3.getColumnLabel(var6), var3.getColumnName(var6));
            var5.put(var7, var1.getObject(var6));
         }

         var2.add(var5);
      }

      return var2;
   }

   private void processRevenuePayout(String var1) throws Exception {
      Map<String, Object> var2 = this.readRevenuePayoutRow(var1);
      if (var2 == null || !"PENDING".equalsIgnoreCase(this.firstNonBlank(this.str(var2.get("status")), ""))) {
         return;
      }

      String var3 = this.firstNonBlank(this.str(var2.get("recipient_account_id")), PRESIDENT_BUDGET_ACCOUNT_ID);
      String var4 = this.firstNonBlank(this.str(var2.get("president_uuid")), "");
      String var5x = TREASURY_LABEL;
      long var5 = Math.max(0L, this.parseLong(this.str(var2.get("amount_ar")), 0L));
      if (var5 <= 0L) {
         this.markRevenuePayoutReview(var1, "amount_invalid");
         return;
      }

      CopiMineArtifacts.BridgeTxnResult var7 = this.bridge.creditAccount(
         var3,
         var4,
         var5x,
         var5,
         this.firstNonBlank(this.str(var2.get("idempotency_key")), "artifact-president-budget-" + var1),
         "artifact_president_budget",
         "purchase=" + var1 + ";item=" + this.firstNonBlank(this.str(var2.get("item_id")), "") + ";buyer=" + this.firstNonBlank(this.str(var2.get("buyer_uuid")), "")
      );
      if (var7.ok()) {
         this.markRevenuePayoutCredited(var1, var7.txId());
         this.audit(var5x, "artifact_revenue_paid", var1, "amount=" + var5 + " buyer=" + this.firstNonBlank(this.str(var2.get("buyer_name")), ""));
      } else {
         this.markRevenuePayoutReview(var1, this.safeBridgeCode(var7.code()));
         this.audit(var5x, "artifact_revenue_review", var1, "code=" + this.safeBridgeCode(var7.code()));
      }
   }

   private Map<String, Object> readRevenuePayoutRow(String var1) throws SQLException {
      Connection var2 = this.pgPool.acquire();

      try {
         PreparedStatement var3 = var2.prepareStatement(
            "SELECT purchase_id,president_uuid,president_name,recipient_account_id,buyer_uuid,buyer_name,item_id,amount_ar,idempotency_key,status FROM artifact_revenue_payouts WHERE purchase_id=? LIMIT 1"
         );

         try {
            var3.setString(1, var1);
            ResultSet var4 = var3.executeQuery();

            try {
               List<Map<String, Object>> var5 = this.readRows(var4);
               return var5.isEmpty() ? null : var5.getFirst();
            } finally {
               var4.close();
            }
         } finally {
            var3.close();
         }
      } finally {
         this.pgPool.release(var2);
      }
   }

   private void markRevenuePayoutCredited(String var1, String var2) throws SQLException {
      Connection var3 = this.pgPool.acquire();

      try {
         PreparedStatement var4 = var3.prepareStatement(
            "UPDATE artifact_revenue_payouts SET status='CREDITED',bank_tx_id=?,last_error='',updated_at=? WHERE purchase_id=? AND status='PENDING'"
         );

         try {
            var4.setString(1, this.firstNonBlank(var2, ""));
            var4.setLong(2, this.now());
            var4.setString(3, var1);
            var4.executeUpdate();
         } finally {
            var4.close();
         }
      } finally {
         this.pgPool.release(var3);
      }
   }

   private void markRevenuePayoutReview(String var1, String var2) throws SQLException {
      Connection var3 = this.pgPool.acquire();

      try {
         PreparedStatement var4 = var3.prepareStatement(
            "UPDATE artifact_revenue_payouts SET status='REVIEW',last_error=?,updated_at=? WHERE purchase_id=? AND status='PENDING'"
         );

         try {
            var4.setString(1, this.firstNonBlank(var2, ""));
            var4.setLong(2, this.now());
            var4.setString(3, var1);
            var4.executeUpdate();
         } finally {
            var4.close();
         }
      } finally {
         this.pgPool.release(var3);
      }
   }

   private void cancelRevenuePayout(String var1, String var2) throws SQLException {
      Connection var3 = this.pgPool.acquire();

      try {
         PreparedStatement var4 = var3.prepareStatement(
            "UPDATE artifact_revenue_payouts SET status='CANCELLED',last_error=?,updated_at=? WHERE purchase_id=? AND status='PENDING'"
         );

         try {
            var4.setString(1, this.firstNonBlank(var2, ""));
            var4.setLong(2, this.now());
            var4.setString(3, var1);
            var4.executeUpdate();
         } finally {
            var4.close();
         }
      } finally {
         this.pgPool.release(var3);
      }
   }

   private CompletableFuture<Boolean> prepareDonationDeliveryAsync(UUID var1, String var2, String var3, String var4, List<String> var5) {
      return this.markDonationClaimDeliveringAsync(var1, var2)
         .thenCompose(var6 -> !Boolean.TRUE.equals(var6) ? CompletableFuture.completedFuture(false) : CompletableFuture.supplyAsync(() -> {
               try {
                  this.persistDonationInstances(var1, var3, var4, var5);
                  return true;
               } catch (Exception var7) {
                  this.reviewDonationClaimAsync(var1, var2);
                  throw new IllegalStateException("Failed to persist donation artifact instances.", var7);
               }
            }, this.dbExecutor));
   }

   private CompletableFuture<Boolean> finalizeDonationDeliveryAsync(UUID var1, String var2, String var3, String var4, List<String> var5) {
      return CompletableFuture.<Boolean>supplyAsync(() -> {
            try {
               // markDonationInstancesDelivered(itemId, uniqueItemIds);
               this.markDonationInstancesDelivered(var4, var5);
               return true;
            } catch (Exception var4x) {
               throw new IllegalStateException("Failed to mark delivered donation artifact instances.", var4x);
            }
         }, this.dbExecutor)
         .thenCompose(
            var4x -> !Boolean.TRUE.equals(var4x)
                  ? CompletableFuture.completedFuture(false)
                  : this.completeDonationClaimAsync(var1, var2).thenCompose(var4xx -> {
                     if (Boolean.TRUE.equals(var4xx)) {
                        this.audit(this.first(var1 == null ? "" : var1.toString(), "unknown"), "donation_claim_claimed", var2, var3);
                        return CompletableFuture.completedFuture(true);
                     } else {
                        this.reviewDonationClaimAsync(var1, var2);
                        return CompletableFuture.failedFuture(new IllegalStateException("Failed to complete donation claim after physical delivery."));
                     }
                  })
         );
   }

   private CompletableFuture<Boolean> failDonationDeliveryAsync(UUID var1, String var2, List<String> var3) {
      this.reviewDonationClaimAsync(var1, var2);
      return CompletableFuture.supplyAsync(() -> {
         try {
            this.markDonationInstancesFailed(var3);
            return true;
         } catch (Exception var4) {
            this.getLogger().log(Level.WARNING, "Donation delivery cleanup failed for claim " + var2, (Throwable)var4);
            return false;
         }
      }, this.dbExecutor);
   }

   private int freeStorageSlots(PlayerInventory var1) {
      int var2 = 0;

      for (ItemStack var6 : var1.getStorageContents()) {
         if (var6 == null || var6.getType() == Material.AIR) {
            var2++;
         }
      }

      return var2;
   }

   private int requiredDonationSlots(long var1) {
      if (var1 <= 0L) {
         return 1;
      } else {
         return var1 > 36L ? -1 : (int)var1;
      }
   }

   private void removeOfficialItemsFromInventory(PlayerInventory inventory, Collection<String> uniqueItemIds) {
      if (inventory != null && uniqueItemIds != null && !uniqueItemIds.isEmpty()) {
         Set var3 = uniqueItemIds.stream().filter(Objects::nonNull).filter(var0 -> !var0.isBlank()).collect(Collectors.toSet());
         if (!var3.isEmpty()) {
            ItemStack[] var4 = inventory.getStorageContents();
            boolean var5 = false;

            for (int var6 = 0; var6 < var4.length; var6++) {
               ItemStack var7 = var4[var6];
               if (var7 != null && var7.getType() != Material.AIR && var7.hasItemMeta()) {
                  String var8 = (String)var7.getItemMeta().getPersistentDataContainer().get(this.keyUniqueItemId, PersistentDataType.STRING);
                  if (var8 != null && var3.contains(var8)) {
                     inventory.setItem(var6, null);
                     var5 = true;
                  }
               }
            }

            if (var5 && inventory.getHolder() instanceof Player var9) {
               var9.updateInventory();
            }
         }
      }
   }

   private void cacheProvisionalDonationInstances(String var1, UUID var2, Collection<String> var3) {
      if (!this.firstNonBlank(var1, "").isBlank() && var2 != null && var3 != null && !var3.isEmpty()) {
         for (String var5 : var3) {
            if (var5 != null && !var5.isBlank()) {
               this.provisionalDonationInstanceIds.add(var5);
            }
         }
      }
   }

   private void removeProvisionalDonationInstances(Collection<String> var1) {
      if (var1 != null && !var1.isEmpty()) {
         for (String var3 : var1) {
            if (var3 != null && !var3.isBlank()) {
               this.provisionalDonationInstanceIds.remove(var3);
            }
         }
      }
   }

   private void recoverStrandedDeliveries(Player var1) {
      if (var1 != null) {
         UUID var2 = var1.getUniqueId();
         this.runAsync(() -> {
            List var3 = this.readPendingByStatus(var2.toString(), "DELIVERING");
            List var4 = this.readDeliveringInstances(var2.toString());
            this.runSync(() -> this.reconcileStrandedDeliveries(var1, var3, var4));
         });
      }
   }

   private void reconcileStrandedDeliveries(Player var1, List<CopiMineArtifacts.PendingDeliveryRow> var2, List<CopiMineArtifacts.DeliveringInstanceRow> var3) {
      if (var1 != null && var1.isOnline()) {
         UUID var4 = var1.getUniqueId();
         HashSet var5 = new HashSet();
         int var6 = 0;
         int var7 = 0;

         for (CopiMineArtifacts.PendingDeliveryRow var8 : var2 == null ? List.<CopiMineArtifacts.PendingDeliveryRow>of() : var2) {
            var5.add(var8.uniqueItemId());
            if (this.playerHasOfficialInstance(var1, var8.uniqueItemId(), var8.itemId(), var4)) {
               this.cacheOfficialBinding(var8.uniqueItemId(), var8.itemId(), var4);
               var6++;
               this.runAsync(() -> {
                  try {
                     this.markPendingClaimed(var8.deliveryId(), var8.purchaseId(), var8.uniqueItemId(), var8.itemId());
                  } catch (SQLException var3x) {
                     this.getLogger().log(Level.WARNING, "Failed to recover pending delivery " + var8.deliveryId(), (Throwable)var3x);
                  }
               });
            } else {
               this.releasePendingDeliveryAsync(var4, var8.deliveryId());
            }
         }

         for (CopiMineArtifacts.DeliveringInstanceRow var10 : var3 == null ? List.<CopiMineArtifacts.DeliveringInstanceRow>of() : var3) {
            if (!var5.contains(var10.uniqueItemId())) {
               if (!this.playerHasOfficialInstance(var1, var10.uniqueItemId(), var10.itemId(), var4)) {
                  if (this.isDonationCatalogItem(var10.itemId()) && this.firstNonBlank(var10.purchaseId(), "").isBlank()) {
                     this.runAsync(() -> {
                        try {
                           this.rollbackStrandedDonationReclaim(var4, var10);
                        } catch (SQLException var4x) {
                           this.getLogger().log(Level.WARNING, "Failed to rollback stranded donation reclaim " + var10.uniqueItemId(), (Throwable)var4x);
                        }
                     });
                  } else if (!this.firstNonBlank(var10.purchaseId(), "").isBlank()) {
                     var7++;
                     this.runAsync(() -> {
                        try {
                           this.createPendingDeliveryRecovery(var4, var10.purchaseId(), var10.uniqueItemId(), var10.itemId());
                        } catch (SQLException var4x) {
                           this.getLogger().log(Level.WARNING, "Failed to recover stranded artifact delivery " + var10.purchaseId(), (Throwable)var4x);
                        }
                     });
                  }
               } else {
                  this.cacheOfficialBinding(var10.uniqueItemId(), var10.itemId(), var4);
                  var6++;
                  if (this.isDonationCatalogItem(var10.itemId())) {
                     this.runAsync(() -> {
                        try {
                           this.markDonationInstancesDelivered(var10.itemId(), List.of(var10.uniqueItemId()));
                        } catch (SQLException var4x) {
                           this.getLogger().log(Level.WARNING, "Failed to recover donation instance " + var10.uniqueItemId(), (Throwable)var4x);
                        }

                        this.completeDonationClaimByPurchaseAsync(var4, var10.purchaseId());
                     });
                  } else {
                     this.runAsync(() -> {
                        try {
                           this.markPurchaseDelivered(var10.purchaseId(), var10.uniqueItemId(), var10.itemId());
                           this.triggerRevenuePayoutAsync(var10.purchaseId());
                        } catch (SQLException var3x) {
                           this.getLogger().log(Level.WARNING, "Failed to recover artifact purchase " + var10.purchaseId(), (Throwable)var3x);
                        }
                     });
                  }
               }
            }
         }

         if (var6 > 0) {
            var1.sendMessage(
               this.color(
                  "&eCopiMineArtifacts восстановил незавершённые выдачи: &f"
                     + var6
               )
            );
         }

         if (var7 > 0) {
            var1.sendMessage(this.color("&eЧасть покупок была возвращена в безопасную очередь выдачи. Открой раздел покупок и забери предметы заново."));
         }
      }
   }

   private void createPendingDeliveryRecovery(UUID var1, String var2, String var3, String var4) throws SQLException {
      Connection var5 = this.pgPool.acquire();

      try {
         var5.setAutoCommit(false);

         try (
            PreparedStatement var6 = var5.prepareStatement(
               "SELECT delivery_id,status FROM artifact_pending_deliveries WHERE purchase_id=? AND unique_item_id=? ORDER BY created_at DESC LIMIT 1 FOR UPDATE"
            );
            PreparedStatement var7 = var5.prepareStatement(
               "INSERT INTO artifact_pending_deliveries(delivery_id,purchase_id,unique_item_id,player_uuid,item_id,status,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?)"
            );
            PreparedStatement var8 = var5.prepareStatement(
               "UPDATE artifact_purchases SET status='PENDING_DELIVERY',delivery_mode='PENDING',updated_at=? WHERE purchase_id=? AND status IN ('PAID','DELIVERING','PENDING_DELIVERY')"
            );
            PreparedStatement var9 = var5.prepareStatement(
               "UPDATE artifact_item_instances SET status='PENDING_DELIVERY',updated_at=? WHERE unique_item_id=? AND status IN ('DELIVERING','PENDING_DELIVERY')"
            );
         ) {
            var6.setString(1, var2);
            var6.setString(2, var3);
            String var10 = "";
            String var11 = "";
            try (ResultSet var12 = var6.executeQuery()) {
               if (var12.next()) {
                  var10 = this.firstNonBlank(var12.getString(1), "");
                  var11 = this.firstNonBlank(var12.getString(2), "");
               }
            }

            if (!var10.isBlank() && Set.of("PENDING", "DELIVERING", "CLAIMED").contains(var11.toUpperCase(Locale.ROOT))) {
               var5.rollback();
               return;
            }

            long var18 = this.now();
            if (var10.isBlank()) {
               String var13 = UUID.randomUUID().toString();
               var7.setString(1, var13);
               var7.setString(2, var2);
               var7.setString(3, var3);
               var7.setString(4, var1.toString());
               var7.setString(5, var4);
               var7.setString(6, "PENDING");
               var7.setLong(7, var18);
               var7.setLong(8, var18);
               var7.executeUpdate();
            }

            var8.setLong(1, var18);
            var8.setString(2, var2);
            var8.executeUpdate();
            var9.setLong(1, var18);
            var9.setString(2, var3);
            if (var9.executeUpdate() <= 0) {
               throw new SQLException("Stranded delivery recovery lost artifact instance state.");
            }

            var5.commit();
            this.audit("SERVER", "purchase_recovered_to_pending", var2, var4);
         } catch (SQLException var17) {
            var5.rollback();
            throw var17;
         }
      } finally {
         try {
            var5.setAutoCommit(true);
         } catch (SQLException var16) {
         }

         this.pgPool.release(var5);
      }
   }

   private boolean playerHasOfficialInstance(Player var1, String var2, String var3, UUID var4) {
      if (var1 != null && var2 != null && !var2.isBlank() && var3 != null && !var3.isBlank() && var4 != null) {
         for (ItemStack var8 : var1.getInventory().getContents()) {
            if (this.officialItemMatches(var8, var2, var3, var4)) {
               return true;
            }
         }

         if (this.officialItemMatches(var1.getInventory().getItemInOffHand(), var2, var3, var4)) {
            return true;
         }
         if (this.itemContainsOfficialInstance(var1.getItemOnCursor(), var2, var3, var4)) {
            return true;
         }
         for (ItemStack var9 : var1.getInventory().getArmorContents()) {
            if (this.officialItemMatches(var9, var2, var3, var4)) {
               return true;
            }
         }
         // A delivery can be moved into the player's ender chest or a bundle
         // between the physical inventory write and the DB finalization.  A
         // recovery check that only scans the main inventory would mint a
         // second copy on the next join.
         if (this.inventoryContainsOfficialInstance(var1.getEnderChest(), var2, var3, var4)) {
            return true;
         }
         if (this.bundleContainsOfficialInstance(var1.getInventory().getContents(), var2, var3, var4)
               || this.bundleContainsOfficialInstance(var1.getInventory().getArmorContents(), var2, var3, var4)
               || this.bundleContainsOfficialInstance(new ItemStack[]{var1.getInventory().getItemInOffHand()}, var2, var3, var4)) {
            return true;
         }
         // Containers currently open by any player are also authoritative
         // physical storage.  The provisional-item click guards prevent new
         // moves, while this scan covers a move made by another plugin.
         for (Player online : Bukkit.getOnlinePlayers()) {
            InventoryView view = online.getOpenInventory();
            if (view != null && this.inventoryContainsOfficialInstance(view.getTopInventory(), var2, var3, var4)) {
               return true;
            }
         }
         return false;
      } else {
         return false;
      }
   }

   private boolean inventoryContainsOfficialInstance(Inventory inventory, String uniqueId, String itemId, UUID owner) {
      if (inventory == null) return false;
      for (ItemStack stack : inventory.getContents()) {
         if (this.itemContainsOfficialInstance(stack, uniqueId, itemId, owner)) return true;
      }
      return false;
   }

   private boolean itemContainsOfficialInstance(ItemStack stack, String uniqueId, String itemId, UUID owner) {
      if (stack == null || stack.getType() == Material.AIR) {
         return false;
      }
      if (this.officialItemMatches(stack, uniqueId, itemId, owner)) {
         return true;
      }
      if (stack.hasItemMeta() && stack.getItemMeta() instanceof BundleMeta bundle && bundle.hasItems()) {
         for (ItemStack nested : bundle.getItems()) {
            if (this.itemContainsOfficialInstance(nested, uniqueId, itemId, owner)) return true;
         }
      }
      // Shulker boxes and other block-state containers can be used as an
      // external storage hop. Inspect their serialized inventory before
      // finalizing a pending delivery, otherwise a moved provisional item
      // would be minted again after reconnect.
      if (stack.hasItemMeta() && stack.getItemMeta() instanceof BlockStateMeta stateMeta
            && stateMeta.getBlockState() instanceof Container container) {
         return this.inventoryContainsOfficialInstance(container.getInventory(), uniqueId, itemId, owner);
      }
      return false;
   }

   private boolean bundleContainsOfficialInstance(ItemStack[] stacks, String uniqueId, String itemId, UUID owner) {
      if (stacks == null) return false;
      for (ItemStack stack : stacks) {
         if (stack == null || !stack.hasItemMeta() || !(stack.getItemMeta() instanceof BundleMeta bundle) || !bundle.hasItems()) continue;
         for (ItemStack nested : bundle.getItems()) {
            if (this.itemContainsOfficialInstance(nested, uniqueId, itemId, owner)) return true;
         }
      }
      return false;
   }

   private boolean officialItemMatches(ItemStack var1, String var2, String var3, UUID var4) {
      if (var1 != null && var1.getType() != Material.AIR && var1.hasItemMeta() && var4 != null) {
         PersistentDataContainer var5 = var1.getItemMeta().getPersistentDataContainer();
         return var2.equalsIgnoreCase(this.firstNonBlank((String)var5.get(this.keyUniqueItemId, PersistentDataType.STRING), ""))
            && var3.equalsIgnoreCase(this.firstNonBlank((String)var5.get(this.keyItemId, PersistentDataType.STRING), ""))
            && var4.toString().equalsIgnoreCase(this.firstNonBlank((String)var5.get(this.keyOwnerUuid, PersistentDataType.STRING), ""));
      } else {
         return false;
      }
   }

   private CopiMineArtifacts.OfficialDonationRef officialDonationRef(ItemStack var1) {
      // Keep one authenticated identity parser for both active gameplay and
      // loss events.  In particular, a retired/disabled catalog row must not
      // make an already purchased physical donation invisible to death,
      // cactus, creative or foreign-pickup handling.
      return this.rawDonationIdentity(var1);
   }

   /**
    * Reads a donation identity only after it has been authenticated against
    * the startup-loaded DB binding cache.  PDC values are user-editable in
    * Creative/NBT tools and therefore are never an authority by themselves.
    */
   private OfficialDonationRef rawDonationIdentity(ItemStack stack) {
      if (stack == null || stack.getType() == Material.AIR || !stack.hasItemMeta()) {
         return null;
      }
      ItemMeta meta = stack.getItemMeta();
      if (meta == null) {
         return null;
      }
      PersistentDataContainer pdc = meta.getPersistentDataContainer();
      String itemType = this.firstNonBlank(pdc.get(this.keyItemType, PersistentDataType.STRING), "");
      String source = this.firstNonBlank(pdc.get(this.keySource, PersistentDataType.STRING), "");
      String itemId = this.firstNonBlank(pdc.get(this.keyItemId, PersistentDataType.STRING), "").toLowerCase(Locale.ROOT);
      String uniqueId = this.firstNonBlank(pdc.get(this.keyUniqueItemId, PersistentDataType.STRING), "");
      String ownerText = this.firstNonBlank(pdc.get(this.keyOwnerUuid, PersistentDataType.STRING), "");
      if (!"DONATION_SHOP_ITEM".equalsIgnoreCase(itemType) || !"DONATION_SHOP".equalsIgnoreCase(source)
            || itemId.isBlank() || uniqueId.isBlank() || ownerText.isBlank()) {
         return null;
      }
      // The PDC source/type pair is only a hint.  The item id must still be a
      // catalog entry owned by the donation shop; AR catalog entries and
      // arbitrary NBT-tagged stacks can never enter the reclaim path.
      if (!this.isDonationCatalogItem(itemId)) {
         return null;
      }
      UUID owner;
      try {
         owner = UUID.fromString(ownerText);
      } catch (IllegalArgumentException invalidOwner) {
         return null;
      }
      OfficialInstanceBinding binding = this.instanceBindings.get(uniqueId);
      if (binding == null) {
         // A stale physical copy can outlive the loaded ACTIVE cache while a
         // chunk is unloaded.  Keep its identity authenticated for terminal
         // cleanup/pickup guards, but never treat it as an active entitlement.
         if (!this.terminalDonationInstanceIds.contains(uniqueId)) {
            return null;
         }
      } else if (!itemId.equalsIgnoreCase(binding.itemId())
            || !owner.toString().equalsIgnoreCase(binding.ownerUuid())) {
         return null;
      }
      // Delivery/reclaim writes are deliberately fail-closed until their
      // physical inventory write and database transition are complete.  A
      // provisional stack must not be consumed as an active item or journaled
      // as a new loss while recovery is still deciding its final state.
      if (this.provisionalDonationInstanceIds.contains(uniqueId)) {
         return null;
      }
      // Catalog entries can be disabled/retired after a purchase.  The
      // database binding (or terminal journal identity) remains the source
      // of truth for ownership and loss quarantine; do not let a removed
      // config row turn an old physical donation copy into an unguarded item.
      return new OfficialDonationRef(
            uniqueId,
            itemId,
            owner,
            this.firstNonBlank(pdc.get(this.keyPurchaseId, PersistentDataType.STRING), "")
      );
   }

   private OfficialDonationRef foreignDonationRef(ItemStack stack, UUID actorUuid) {
      OfficialDonationRef ref = this.rawDonationIdentity(stack);
      return ref != null && actorUuid != null && !actorUuid.equals(ref.ownerUuid()) ? ref : null;
   }

   /**
    * Durable-first quarantine for a foreign donation item.  Physical removal
    * happens only after the loss journal append succeeds.  Every online copy
    * with this unique id is removed, including a duplicate in the real
    * owner's inventory, so one DB instance can never be reclaimed twice.
    */
   private boolean quarantineForeignDonation(Player actor, OfficialDonationRef ref, String reason) {
      if (ref == null || (actor != null && actor.getUniqueId().equals(ref.ownerUuid()))) {
         return false;
      }
      String guardKey = ref.ownerUuid() + ":" + ref.uniqueItemId();
      if (!this.recordDonationLossOnce(ref, reason)) {
         if (actor != null) {
            actor.sendMessage(this.color("&cЧужой донат-предмет заблокирован: не удалось сохранить возврат. Предмет оставлен до повторной попытки."));
         }
         return false;
      }
      // The loss journal writer owns physical removal after fsync.  Keep a
      // short-lived event guard for duplicate callbacks, but never clear an
      // inventory/cursor/entity on the Bukkit thread before durability is
      // confirmed.
      this.foreignQuarantineInFlight.add(guardKey);
      this.flushPendingDonationLossJournalAsync();
      this.audit(actor == null ? "SERVER" : actor.getName(), "foreign_donation_quarantined", ref.uniqueItemId(),
            ref.itemId() + " owner=" + ref.ownerUuid() + " reason=" + this.firstNonBlank(reason, "unknown"));
      if (actor != null) {
         actor.sendMessage(this.color("&eЧужой донат-предмет удалён и возвращён владельцу через меню возврата."));
      }
      return true;
   }

   private boolean removeUniqueItemFromInventory(Inventory inventory, String uniqueItemId) {
      if (inventory == null || this.firstNonBlank(uniqueItemId, "").isBlank()) {
         return false;
      }
      boolean changed = false;
      for (int slot = 0; slot < inventory.getSize(); slot++) {
         ItemStack stack = inventory.getItem(slot);
         if (uniqueItemId.equals(this.uniqueItemIdOf(stack))) {
            inventory.setItem(slot, new ItemStack(Material.AIR));
            changed = true;
         } else if (this.removeUniqueItemFromNestedStack(stack, uniqueItemId)) {
            inventory.setItem(slot, stack);
            changed = true;
         }
      }
      return changed;
   }

   /** Remove an identity from a closed bundle/shulker item as well as direct slots. */
   private boolean removeUniqueItemFromNestedStack(ItemStack stack, String uniqueItemId) {
      if (stack == null || stack.getType() == Material.AIR || !stack.hasItemMeta()
            || stack.getItemMeta() == null || uniqueItemId == null || uniqueItemId.isBlank()) {
         return false;
      }
      ItemMeta meta = stack.getItemMeta();
      boolean changed = false;
      if (meta instanceof BundleMeta bundle && bundle.hasItems()) {
         List<ItemStack> kept = new ArrayList<>();
         for (ItemStack nested : bundle.getItems()) {
            if (uniqueItemId.equals(this.uniqueItemIdOf(nested))) {
               changed = true;
               continue;
            }
            if (this.removeUniqueItemFromNestedStack(nested, uniqueItemId)) {
               changed = true;
            }
            kept.add(nested);
         }
         if (changed) {
            bundle.setItems(kept);
            stack.setItemMeta(meta);
         }
      }
      if (meta instanceof BlockStateMeta stateMeta && stateMeta.getBlockState() instanceof Container container) {
         if (this.removeUniqueItemFromInventory(container.getInventory(), uniqueItemId)) {
            stateMeta.setBlockState(container);
            stack.setItemMeta(meta);
            changed = true;
         }
      }
      return changed;
   }

   private void removeDonationInstanceFromOnlineInventories(String uniqueItemId) {
      String wanted = this.firstNonBlank(uniqueItemId, "");
      if (wanted.isBlank()) {
         return;
      }
      for (Player player : Bukkit.getOnlinePlayers()) {
         PlayerInventory inventory = player.getInventory();
         boolean changed = this.removeUniqueItemFromInventory(inventory, wanted);
         changed |= this.removeUniqueItemFromInventory(player.getEnderChest(), wanted);
         ItemStack cursor = player.getItemOnCursor();
         if (wanted.equals(this.uniqueItemIdOf(cursor))) {
            player.setItemOnCursor(new ItemStack(Material.AIR));
            changed = true;
         } else if (this.removeUniqueItemFromNestedStack(cursor, wanted)) {
            player.setItemOnCursor(cursor);
            changed = true;
         }
         InventoryView view = player.getOpenInventory();
         if (view != null && view.getTopInventory() != null) {
            // A foreign copy may have been inserted by an older build or a
            // third-party plugin before the ownership guard ran.  Remove it
            // from every currently open block inventory as well; otherwise a
            // reclaim could issue a replacement while the stale stack stays
            // hidden in a chest or shulker.
            changed |= this.removeUniqueItemFromInventory(view.getTopInventory(), wanted);
         }
         if (changed) {
            player.updateInventory();
         }
      }
   }

   private String uniqueItemIdOf(ItemStack stack) {
      if (stack == null || stack.getType() == Material.AIR || !stack.hasItemMeta() || stack.getItemMeta() == null) {
         return "";
      }
      return this.firstNonBlank(
            stack.getItemMeta().getPersistentDataContainer().get(this.keyUniqueItemId, PersistentDataType.STRING), "");
   }

   private boolean isProvisionalDonationItem(ItemStack stack) {
      String uniqueId = this.uniqueItemIdOf(stack);
      return !uniqueId.isBlank() && this.provisionalDonationInstanceIds.contains(uniqueId);
   }

   /**
    * The old owner-bound/reclaim workflow is retired.  Keep this as one
    * explicit feature boundary while the historical recovery code remains
    * readable for database migrations; all live item events use vanilla rules.
    */
   private boolean customShopItemsAreVanilla() {
      return true;
   }

   private boolean isRepairLockedItem(Player player, ItemStack stack) {
      if (player == null || stack == null) {
         return false;
      }
      SessionState state = this.sessions.get(player.getUniqueId());
      return state != null && !state.repairInFlightId.isBlank()
            && !state.repairItemUniqueId.isBlank()
            && state.repairItemUniqueId.equals(this.uniqueItemIdOf(stack));
   }

   private void sweepForeignDonationItems() {
      for (Player player : Bukkit.getOnlinePlayers()) {
         Map<String, OfficialDonationRef> foreign = new LinkedHashMap<>();
         for (ItemStack stack : player.getInventory().getContents()) {
            this.collectForeignDonationRefs(stack, player.getUniqueId(), foreign);
         }
         for (ItemStack stack : player.getInventory().getArmorContents()) {
            this.collectForeignDonationRefs(stack, player.getUniqueId(), foreign);
         }
         this.collectForeignDonationRefs(player.getInventory().getItemInOffHand(), player.getUniqueId(), foreign);
         for (ItemStack stack : player.getEnderChest().getContents()) {
            this.collectForeignDonationRefs(stack, player.getUniqueId(), foreign);
         }
         this.collectForeignDonationRefs(player.getItemOnCursor(), player.getUniqueId(), foreign);
         InventoryView view = player.getOpenInventory();
         Inventory top = view == null ? null : view.getTopInventory();
         if (top != null && !(top.getHolder() instanceof MenuHolder)) {
            for (int slot = 0; slot < top.getSize(); slot++) {
               this.collectForeignDonationRefs(top.getItem(slot), player.getUniqueId(), foreign);
            }
         }
         for (Map.Entry<String, OfficialDonationRef> entry : foreign.entrySet()) {
            this.quarantineForeignDonation(player, entry.getValue(), "foreign-inventory-sweep");
         }
      }
   }

   /** Find foreign identities inside closed bundles and shulker/container items. */
   private void collectForeignDonationRefs(ItemStack stack, UUID actorUuid, Map<String, OfficialDonationRef> output) {
      if (stack == null || stack.getType().isAir() || actorUuid == null || output == null) {
         return;
      }
      OfficialDonationRef direct = this.foreignDonationRef(stack, actorUuid);
      if (direct != null) {
         output.putIfAbsent(direct.uniqueItemId(), direct);
      }
      if (!stack.hasItemMeta() || stack.getItemMeta() == null) {
         return;
      }
      ItemMeta meta = stack.getItemMeta();
      if (meta instanceof BundleMeta bundle && bundle.hasItems()) {
         for (ItemStack nested : bundle.getItems()) {
            this.collectForeignDonationRefs(nested, actorUuid, output);
         }
      }
      if (meta instanceof BlockStateMeta stateMeta && stateMeta.getBlockState() instanceof Container container) {
         for (ItemStack nested : container.getInventory().getContents()) {
            this.collectForeignDonationRefs(nested, actorUuid, output);
         }
      }
   }

   private boolean isOfficialDonationItem(ItemStack stack) {
      return this.isProvisionalDonationItem(stack)
            || this.officialDonationRef(stack) != null
            || this.rawDonationIdentity(stack) != null;
   }

   private boolean hasBlockingDonationInstance(UUID var1, String var2) throws SQLException {
      if (var1 != null && var2 != null && !var2.isBlank()) {
         Connection var3 = this.pgPool.acquire();

         boolean var6;
         try (PreparedStatement var4 = var3.prepareStatement(
               "    SELECT COUNT(*)\n    FROM artifact_item_instances\n    WHERE owner_uuid=?\n      AND item_id=?\n      AND status IN ('ACTIVE','DELIVERING','PENDING_DELIVERY')\n"
            )) {
            var4.setString(1, var1.toString());
            var4.setString(2, var2);

            try (ResultSet var5 = var4.executeQuery()) {
               var6 = var5.next() && var5.getInt(1) > 0;
            }
         } finally {
            this.pgPool.release(var3);
         }

         return var6;
      } else {
         return false;
      }
   }

   private List<CopiMineArtifacts.ReclaimableDonationRow> readReclaimableDonationRows(String var1, int var2) {
      if (var1 != null && !var1.isBlank()) {
         Connection var3 = null;

         List var5;
         try {
            var3 = this.pgPool.acquire();

            try (PreparedStatement var4 = var3.prepareStatement(
                  "    SELECT unique_item_id,purchase_id,item_id,updated_at\n    FROM artifact_item_instances\n    WHERE owner_uuid=?\n      AND status='LOST_RECLAIMABLE'\n    ORDER BY updated_at DESC\n    LIMIT ?\n"
               )) {
               var4.setString(1, var1);
               var4.setInt(2, Math.max(1, Math.min(var2, 54)));

               try (ResultSet var21 = var4.executeQuery()) {
                  ArrayList var6 = new ArrayList();

                  while (var21.next()) {
                     var6.add(new CopiMineArtifacts.ReclaimableDonationRow(var21.getString(1), var21.getString(2), var21.getString(3), var21.getLong(4)));
                  }

                  return var6;
               }
            }
         } catch (SQLException var19) {
            this.getLogger().log(Level.WARNING, "Donation reclaim rows fetch failed", (Throwable)var19);
            var5 = List.of();
         } finally {
            if (var3 != null) {
               this.pgPool.release(var3);
            }
         }

         return var5;
      } else {
         return List.of();
      }
   }

   private CopiMineArtifacts.DonationOwnershipSnapshot readDonationOwnershipSnapshot(String var1) {
      if (var1 != null && !var1.isBlank()) {
         Connection var2 = null;

         CopiMineArtifacts.DonationOwnershipSnapshot var4;
         try {
            var2 = this.pgPool.acquire();
            HashSet var3 = new HashSet();
            HashSet var33 = new HashSet();
            HashSet var5 = new HashSet();
            ArrayList var6 = new ArrayList();

            try (
               PreparedStatement var7 = var2.prepareStatement(
                  "    SELECT item_id\n    FROM donation_item_claims\n    WHERE player_uuid=?\n      AND status IN ('UNCLAIMED','RESERVED','DELIVERING','DELIVERY_REVIEW')\n"
               );
               PreparedStatement var8 = var2.prepareStatement(
                  "    SELECT unique_item_id,purchase_id,item_id,status,updated_at\n    FROM artifact_item_instances\n    WHERE owner_uuid=?\n      AND item_id IN (\n          SELECT DISTINCT item_id\n          FROM artifact_item_instances\n          WHERE owner_uuid=?\n      )\n    ORDER BY updated_at DESC\n"
               );
            ) {
               var7.setString(1, var1);

               try (ResultSet var9 = var7.executeQuery()) {
                  while (var9.next()) {
                     String var10 = this.firstNonBlank(var9.getString(1), "").toLowerCase(Locale.ROOT);
                     if (!var10.isBlank()) {
                        var33.add(var10);
                     }
                  }
               }

               var8.setString(1, var1);
               var8.setString(2, var1);

               try (ResultSet var35 = var8.executeQuery()) {
                  while (var35.next()) {
                     String var36 = this.firstNonBlank(var35.getString(3), "").toLowerCase(Locale.ROOT);
                     String var11 = this.firstNonBlank(var35.getString(4), "");
                     if (!var36.isBlank()) {
                        if ("ACTIVE".equalsIgnoreCase(var11)) {
                           var3.add(var36);
                        } else if ("LOST_RECLAIMABLE".equalsIgnoreCase(var11)) {
                           var5.add(var36);
                           var6.add(new CopiMineArtifacts.ReclaimableDonationRow(var35.getString(1), var35.getString(2), var36, var35.getLong(5)));
                        }
                     }
                  }
               }
            }

            return new CopiMineArtifacts.DonationOwnershipSnapshot(Set.copyOf(var3), Set.copyOf(var33), Set.copyOf(var5), List.copyOf(var6), var33.size());
         } catch (SQLException var31) {
            this.getLogger().log(Level.WARNING, "Donation ownership snapshot failed", (Throwable)var31);
            var4 = new CopiMineArtifacts.DonationOwnershipSnapshot(Set.of(), Set.of(), Set.of(), List.of(), 0);
         } finally {
            if (var2 != null) {
               this.pgPool.release(var2);
            }
         }

         return var4;
      } else {
         return new CopiMineArtifacts.DonationOwnershipSnapshot(Set.of(), Set.of(), Set.of(), List.of(), 0);
      }
   }

   private boolean updateDonationInstanceStatus(UUID var1, String var2, String var3, String var4, Set<String> var5, boolean var6) throws SQLException {
      if (var1 != null && var2 != null && !var2.isBlank() && var3 != null && !var3.isBlank() && var4 != null && !var4.isBlank()) {
         Connection var7 = this.pgPool.acquire();

         boolean var41;
         try {
            var7.setAutoCommit(false);
            String[] var8 = var5 != null && !var5.isEmpty() ? var5.toArray(String[]::new) : new String[]{"ACTIVE"};

            try (
               PreparedStatement var9 = var7.prepareStatement(
                  "    SELECT status\n    FROM artifact_item_instances\n    WHERE owner_uuid=?\n      AND unique_item_id=?\n      AND item_id=?\n    FOR UPDATE\n"
               );
               PreparedStatement var10 = var7.prepareStatement(
                  "    UPDATE artifact_item_instances\n    SET status=?,updated_at=?\n    WHERE owner_uuid=?\n      AND unique_item_id=?\n      AND item_id=?\n"
               );
            ) {
               var9.setString(1, var1.toString());
               var9.setString(2, var2);
               var9.setString(3, var3);

               String var11;
               try (ResultSet var12 = var9.executeQuery()) {
                  if (!var12.next()) {
                     var7.rollback();
                     return false;
                  }

                  var11 = this.firstNonBlank(var12.getString(1), "");
               }

               if (!Arrays.stream(var8).noneMatch(var1x -> var1x.equalsIgnoreCase(var11))) {
                  long var40 = this.now();
                  var10.setString(1, var4);
                  var10.setLong(2, var40);
                  var10.setString(3, var1.toString());
                  var10.setString(4, var2);
                  var10.setString(5, var3);
                  boolean var14 = var10.executeUpdate() > 0;
                  var7.commit();
                  if (var14 && var6) {
                      if (Set.of("BROKEN", "LOST_RECLAIMABLE", "CONSUMED", "REPLACED_AFTER_LOSS", "DELETED_AS_INVALID").contains(var4.toUpperCase(Locale.ROOT))) {
                        this.terminalDonationInstanceIds.add(var2);
                     }
                     this.removeOfficialBinding(var2);
                  }

                  return var14;
               }

               var7.rollback();
               var41 = false;
            } catch (SQLException var38) {
               var7.rollback();
               throw var38;
            }
         } finally {
            try {
               var7.setAutoCommit(true);
            } catch (SQLException var31) {
            }

            this.pgPool.release(var7);
         }

         return var41;
      } else {
         return false;
      }
   }

   private void markDonationInstancesLost(UUID var1, Map<String, String> var2, String var3) throws SQLException {
      if (var1 != null && var2 != null && !var2.isEmpty()) {
         for (Entry var5 : var2.entrySet()) {
            boolean var6 = this.updateDonationInstanceStatus(
               var1, (String)var5.getKey(), (String)var5.getValue(), "LOST_RECLAIMABLE", Set.of("ACTIVE", "DELIVERING", "PENDING_DELIVERY", "DELIVERED"), true
            );
            if (var6) {
               this.audit(
                  var1.toString(), "donation_item_lost", (String)var5.getKey(), (String)var5.getValue() + " reason=" + this.firstNonBlank(var3, "unknown")
               );
            }
         }
      }
   }

   private boolean recordDonationLossJournal(UUID var1, Map<String, String> var2, String var3) {
      if (var1 != null && var2 != null && !var2.isEmpty() && this.donationLossJournalPath != null) {
         ArrayList var4 = new ArrayList();
         long var5 = this.now();
         String var7 = this.sanitizeJournalValue(this.firstNonBlank(var3, "unknown"));

         for (Entry var9 : var2.entrySet()) {
            String var10 = this.sanitizeJournalValue(this.firstNonBlank((String)var9.getKey(), ""));
            String var11 = this.sanitizeJournalValue(this.firstNonBlank((String)var9.getValue(), ""));
            if (!var10.isBlank() && !var11.isBlank()) {
               var4.add(var5 + "\t" + var1 + "\t" + var10 + "\t" + var11 + "\t" + var7);
            }
         }

         if (var4.isEmpty()) {
            return false;
         }
         String payload = String.join(System.lineSeparator(), var4) + System.lineSeparator();
         if (Bukkit.isPrimaryThread()) {
            if (this.donationJournalExecutor == null || this.donationJournalExecutor.isShutdown()) {
               return false;
            }
            List<String> uniqueIds = var2.keySet().stream()
                  .map(key -> this.firstNonBlank(key, ""))
                  .filter(key -> !key.isBlank())
                  .toList();
            try {
               // Never wait on fsync from a Bukkit callback.  The queue is
               // bounded; rejection leaves the physical item untouched and
               // the caller can retry the event.  Once the durable append is
               // complete, the main-thread callback removes every matching
               // entity/inventory copy, so no physical copy is discarded
               // before the journal's durability boundary.
               this.donationJournalExecutor.submit(() -> {
                  boolean persisted = this.appendDonationLossJournalDurable(payload);
                  if (!persisted) {
                     for (String uniqueId : uniqueIds) {
                        this.lossJournalInFlight.remove(var1 + ":" + uniqueId);
                        this.foreignQuarantineInFlight.remove(var1 + ":" + uniqueId);
                     }
                     return;
                  }
                  this.terminalDonationInstanceIds.addAll(uniqueIds);
                  this.runSync(() -> {
                     for (String uniqueId : uniqueIds) {
                        this.removeDonationInstanceFromOnlineInventories(uniqueId);
                        this.removeDonationItemEntities(uniqueId);
                        this.foreignQuarantineInFlight.remove(var1 + ":" + uniqueId);
                     }
                     this.flushPendingDonationLossJournalAsync();
                  });
               });
               return true;
            } catch (RejectedExecutionException rejected) {
               this.getLogger().log(Level.WARNING, "Donation loss journal queue is full; preserving physical item", rejected);
               return false;
            }
         }
         return this.appendDonationLossJournalDurable(payload);
      } else {
         return false;
      }
   }

   /** Append and fsync a journal payload. Called from the bounded writer, never from a Bukkit callback. */
   private boolean appendDonationLossJournalDurable(String payload) {
      synchronized (this.donationLossJournalLock) {
         try {
            Files.createDirectories(this.donationLossJournalPath.getParent());
            try (FileChannel channel = FileChannel.open(this.donationLossJournalPath,
                  StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
               writeFully(channel, StandardCharsets.UTF_8.encode(payload));
               // FileChannel.force(true) is the durable boundary before the physical item is removed.
               channel.force(true);
            }
            return true;
         } catch (IOException error) {
            this.getLogger().log(Level.WARNING, "Failed to persist donation loss journal", error);
            return false;
         }
      }
   }

   /** Remove dropped copies only after the journal writer has fsynced them. */
   private void removeDonationItemEntities(String uniqueItemId) {
      if (!Bukkit.isPrimaryThread()) {
         this.runSync(() -> this.removeDonationItemEntities(uniqueItemId));
         return;
      }
      String wanted = this.firstNonBlank(uniqueItemId, "");
      if (wanted.isBlank()) {
         return;
      }
      for (World world : Bukkit.getWorlds()) {
         for (Item item : world.getEntitiesByClass(Item.class)) {
            if (wanted.equals(this.uniqueItemIdOf(item.getItemStack()))) {
               item.remove();
            }
         }
      }
   }

   private CompletableFuture<Boolean> finalizeDonationReclaimAsync(UUID var1, CopiMineArtifacts.DonationReclaimContext var2) {
      return var1 != null && var2 != null ? CompletableFuture.supplyAsync(() -> {
         SQLException var3 = null;

         for (int var4 = 0; var4 < 3; var4++) {
            try {
               this.completeDonationReclaim(var1, var2);
               return true;
            } catch (SQLException var8) {
               var3 = var8;
               if (var4 < 2) {
                  try {
                     Thread.sleep(100L * (long)(var4 + 1));
                  } catch (InterruptedException var7) {
                     Thread.currentThread().interrupt();
                     break;
                  }
                  continue;
               }
               break;
            }
         }

         throw new CompletionException(new IllegalStateException("Failed to finalize donation reclaim.", var3));
      }, this.dbExecutor) : CompletableFuture.completedFuture(false);
   }

   private String sanitizeJournalValue(String var1) {
      return this.firstNonBlank(var1, "").replace('\t', '_').replace('\r', '_').replace('\n', '_').trim();
   }

   private void flushPendingDonationLossJournalAsync() {
      if (this.dbExecutor != null && this.donationLossJournalPath != null) {
         CompletableFuture.runAsync(() -> {
            try {
               this.reconcileDonationLossJournal();
            } catch (Exception var2) {
               this.getLogger().log(Level.WARNING, "Donation loss journal reconcile failed", (Throwable)var2);
            }
         }, this.dbExecutor);
      }
   }

   private void reconcileDonationLossJournal() throws IOException, SQLException {
      // Several loss events can enqueue reconciliation at once (for example
      // ItemDespawnEvent followed by EntityRemoveEvent).  Serialize the full
      // read/apply/rewrite cycle, not only the individual file operations;
      // otherwise two workers can read the same journal and the later rewrite
      // can resurrect or discard entries written by the first worker.
      synchronized (this.donationLossJournalLock) {
         List<CopiMineArtifacts.DonationLossJournalEntry> var1 = this.readDonationLossJournalEntries();
         if (!var1.isEmpty()) {
            ArrayList var2 = new ArrayList();

            for (CopiMineArtifacts.DonationLossJournalEntry var4 : var1) {
               try {
                  if (!this.applyDonationLossJournalEntry(var4)) {
                     var2.add(var4);
                  } else {
                     this.releaseDonationLossJournalGuard(var4);
                  }
               } catch (SQLException var6) {
                  var2.add(var4);
               }
            }

            if (var2.size() != var1.size()) {
               this.rewriteDonationLossJournalEntries(var2);
            }
         }
      }
   }

   /**
    * Legacy terminal-consumption helper retained for migrations and explicit
    * administrative cleanup.  Durability breaks use BROKEN and never enter
    * the reclaim workflow.
    */
   private void markDonationInstancesConsumed(UUID owner, Map<String, String> instances, String reason) throws SQLException {
      if (owner == null || instances == null || instances.isEmpty()) {
         return;
      }
      for (Entry entry : instances.entrySet()) {
         boolean changed = this.updateDonationInstanceStatus(
               owner, (String) entry.getKey(), (String) entry.getValue(), "CONSUMED",
               Set.of("ACTIVE", "DELIVERING", "PENDING_DELIVERY", "DELIVERED"), true);
         if (changed) {
            this.audit(owner.toString(), "donation_item_consumed", (String) entry.getKey(),
                  (String) entry.getValue() + " reason=" + this.firstNonBlank(reason, "break"));
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onProtectedShopExplode(EntityExplodeEvent event) {
      event.blockList().removeIf(block -> this.shopsByLocation.containsKey(this.blockKey(block.getLocation())));
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onProtectedShopBlockExplode(BlockExplodeEvent event) {
      event.blockList().removeIf(block -> this.shopsByLocation.containsKey(this.blockKey(block.getLocation())));
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onProtectedShopPistonExtend(BlockPistonExtendEvent event) {
      if (event.getBlocks().stream().anyMatch(block -> this.shopsByLocation.containsKey(this.blockKey(block.getLocation())))) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onProtectedShopPistonRetract(BlockPistonRetractEvent event) {
      if (event.getBlocks().stream().anyMatch(block -> this.shopsByLocation.containsKey(this.blockKey(block.getLocation())))) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onProtectedShopBurn(BlockBurnEvent event) {
      if (this.shopsByLocation.containsKey(this.blockKey(event.getBlock().getLocation()))) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onProtectedShopFlow(BlockFromToEvent event) {
      if (event != null && (this.shopsByLocation.containsKey(this.blockKey(event.getBlock().getLocation()))
            || this.shopsByLocation.containsKey(this.blockKey(event.getToBlock().getLocation())))) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onProtectedShopFade(BlockFadeEvent event) {
      if (event != null && this.shopsByLocation.containsKey(this.blockKey(event.getBlock().getLocation()))) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onProtectedShopIgnite(BlockIgniteEvent event) {
      if (event != null && this.shopsByLocation.containsKey(this.blockKey(event.getBlock().getLocation()))) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onProtectedShopPhysics(BlockPhysicsEvent event) {
      if (event != null && this.shopsByLocation.containsKey(this.blockKey(event.getBlock().getLocation()))) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onProtectedShopEntityForm(EntityBlockFormEvent event) {
      if (event != null && this.shopsByLocation.containsKey(this.blockKey(event.getBlock().getLocation()))) {
         event.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = false
   )
   public void onDonationItemMerge(ItemMergeEvent event) {
      Item source = event.getEntity();
      Item target = event.getTarget();
      if (this.isOfficialDonationItem(source == null ? null : source.getItemStack())
            || this.isOfficialDonationItem(target == null ? null : target.getItemStack())) {
         // A merge would discard one unique PDC identity. Keep both physical
         // copies separate; if another plugin removes one directly, the
         // EntityRemoveEvent fallback still journals it as MERGE.
         event.setCancelled(true);
      }
   }

   private List<CopiMineArtifacts.DonationLossJournalEntry> readDonationLossJournalEntries() throws IOException {
      if (this.donationLossJournalPath == null) {
         return List.of();
      } else {
         synchronized (this.donationLossJournalLock) {
            if (!Files.exists(this.donationLossJournalPath)) {
               return List.of();
            } else {
               ArrayList var2 = new ArrayList();

               for (String var4 : Files.readAllLines(this.donationLossJournalPath, StandardCharsets.UTF_8)) {
                  String var5 = this.firstNonBlank(var4, "").trim();
                  if (!var5.isBlank()) {
                     String[] var6 = var5.split("\\t", 5);
                     if (var6.length >= 5) {
                        var2.add(new CopiMineArtifacts.DonationLossJournalEntry(this.parseLong(var6[0], 0L), var6[1], var6[2], var6[3], var6[4]));
                     }
                  }
               }

               return var2;
            }
         }
      }
   }

   private void rewriteDonationLossJournalEntries(List<CopiMineArtifacts.DonationLossJournalEntry> var1) throws IOException {
      if (this.donationLossJournalPath != null) {
         synchronized (this.donationLossJournalLock) {
            if (var1 != null && !var1.isEmpty()) {
               ArrayList var3 = new ArrayList(var1.size());

               for (CopiMineArtifacts.DonationLossJournalEntry var5 : var1) {
                  var3.add(
                     var5.createdAt()
                        + "\t"
                        + this.sanitizeJournalValue(var5.ownerUuid())
                        + "\t"
                        + this.sanitizeJournalValue(var5.uniqueItemId())
                        + "\t"
                        + this.sanitizeJournalValue(var5.itemId())
                        + "\t"
                        + this.sanitizeJournalValue(var5.reason())
                  );
               }

               Path temporary = this.donationLossJournalPath.resolveSibling(
                  this.donationLossJournalPath.getFileName() + ".tmp-" + UUID.randomUUID());
                String payload = String.join(System.lineSeparator(), var3) + System.lineSeparator();
                try (FileChannel channel = FileChannel.open(temporary,
                      StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                   writeFully(channel, StandardCharsets.UTF_8.encode(payload));
                   channel.force(true);
               }
               try {
                  Files.move(temporary, this.donationLossJournalPath,
                     StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
               } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                  Files.move(temporary, this.donationLossJournalPath, StandardCopyOption.REPLACE_EXISTING);
               }
            } else {
               Files.deleteIfExists(this.donationLossJournalPath);
            }
         }
      }
   }

   /**
    * Mark journaled identities terminal before PostgreSQL reconciliation.  A
    * stale entity can be loaded from an unloaded chunk during this window;
    * pickup/chunk guards must reject it even while the DB row still says
    * ACTIVE.
    */
   private void loadPendingDonationLossJournalIdentities() {
      Path journal = this.donationLossJournalPath;
      if (journal == null || !Files.isRegularFile(journal)) {
         return;
      }
      try {
         for (String line : Files.readAllLines(journal, StandardCharsets.UTF_8)) {
            String[] parts = this.firstNonBlank(line, "").split("\\t", 5);
            if (parts.length >= 3) {
               String unique = this.firstNonBlank(parts[2], "");
               if (!unique.isBlank()) this.terminalDonationInstanceIds.add(unique);
            }
         }
      } catch (Exception error) {
         this.getLogger().log(Level.WARNING, "Unable to preload donation loss journal identities", (Throwable)error);
      }
   }

   private static void writeFully(FileChannel channel, ByteBuffer buffer) throws IOException {
      while (buffer.hasRemaining()) {
         channel.write(buffer);
      }
   }

   private boolean applyDonationLossJournalEntry(CopiMineArtifacts.DonationLossJournalEntry var1) throws SQLException {
      if (var1 != null && !var1.ownerUuid().isBlank() && !var1.uniqueItemId().isBlank() && !var1.itemId().isBlank()) {
         UUID var2;
         try {
            var2 = UUID.fromString(var1.ownerUuid());
         } catch (IllegalArgumentException var5) {
            return true;
         }

         String var3 = this.readDonationInstanceStatus(var2, var1.uniqueItemId(), var1.itemId());
            if (var3 != null && !var3.isBlank()) {
            String var4 = var3.toUpperCase(Locale.ROOT);
            if (Set.of("LOST_RECLAIMABLE", "REPLACED_AFTER_LOSS", "CONSUMED", "DELETED_AS_INVALID").contains(var4)) {
               return true;
            } else {
               // Every physical destruction path is reclaimable.  In
               // particular BREAK/DURABILITY_BREAK used to be marked
               // CONSUMED, which made a paid custom donation disappear
               // permanently after its durability reached zero.  AR and
               // narcotics never reach this method because officialDonationRef
               // authenticates only donation-instance identities.
               this.markDonationInstancesLost(var2, Map.of(var1.uniqueItemId(), var1.itemId()), var1.reason());
               return true;
            }
         } else {
            // Keep the journal entry until the instance row becomes visible.
            // A transient DB race must never turn a real loss into an
            // unrecoverable item.
            return false;
         }
      } else {
         return true;
      }
   }

   /**
    * The guard prevents duplicate Paper removal notifications while a loss is
    * pending.  Once the durable journal entry reaches a terminal state it must
    * be released; otherwise every historic loss remains in memory forever and
    * can suppress a legitimate retry after a transient delivery race.
    */
   private void releaseDonationLossJournalGuard(CopiMineArtifacts.DonationLossJournalEntry var1) {
      if (var1 != null && !this.firstNonBlank(var1.ownerUuid(), "").isBlank() && !this.firstNonBlank(var1.uniqueItemId(), "").isBlank()) {
         this.lossJournalInFlight.remove(var1.ownerUuid() + ":" + var1.uniqueItemId());
      }
   }

   private String readDonationInstanceStatus(UUID var1, String var2, String var3) throws SQLException {
      if (var1 != null && !this.firstNonBlank(var2, "").isBlank() && !this.firstNonBlank(var3, "").isBlank()) {
         Connection var4 = this.pgPool.acquire();

         String var7;
         try (PreparedStatement var5 = var4.prepareStatement(
               "SELECT status\nFROM artifact_item_instances\nWHERE owner_uuid=?\n  AND unique_item_id=?\n  AND item_id=?\nLIMIT 1\n"
            )) {
            var5.setString(1, var1.toString());
            var5.setString(2, var2);
            var5.setString(3, var3);

            try (ResultSet var6 = var5.executeQuery()) {
               var7 = var6.next() ? this.firstNonBlank(var6.getString(1), "") : "";
            }
         } finally {
            this.pgPool.release(var4);
         }

         return var7;
      } else {
         return "";
      }
   }

   private String readOwnerUuidForInstance(Connection var1, String var2) throws SQLException {
      if (var1 != null && !this.firstNonBlank(var2, "").isBlank()) {
         String var5;
         try (PreparedStatement var3 = var1.prepareStatement(
               "    SELECT owner_uuid\n    FROM artifact_item_instances\n    WHERE unique_item_id=?\n    LIMIT 1\n"
            )) {
            var3.setString(1, var2);

            try (ResultSet var4 = var3.executeQuery()) {
               var5 = var4.next() ? this.firstNonBlank(var4.getString(1), "") : "";
            }
         }

         return var5;
      } else {
         return "";
      }
   }

   private CopiMineArtifacts.DonationCatalogItem donationCatalogItem(String var1) {
      return var1 == null ? null : this.donationCatalogById.get(var1.toLowerCase(Locale.ROOT));
   }

   private CopiMineArtifacts.CatalogItem runtimeCatalogItem(String var1) {
      if (var1 == null) {
         return null;
      } else {
         CopiMineArtifacts.CatalogItem var2 = this.catalogById.get(var1.toLowerCase(Locale.ROOT));
         if (var2 != null) {
            return var2;
         } else {
            CopiMineArtifacts.DonationCatalogItem var3 = this.donationCatalogItem(var1);
            return var3 == null ? null : this.synthesizeDonationRuntimeItem(var3);
         }
      }
   }

   private boolean isDonationCatalogItem(String var1) {
      return this.donationCatalogItem(var1) != null;
   }

   private boolean isArCatalogItem(String itemId) {
      return itemId != null
         && this.catalogById.containsKey(itemId.toLowerCase(Locale.ROOT))
         && !this.donationCatalogById.containsKey(itemId.toLowerCase(Locale.ROOT));
   }

   private boolean isAdminOnlyCatalogItem(String itemId) {
      return itemId != null && this.adminOnlyCatalogItems.contains(itemId.toLowerCase(Locale.ROOT));
   }

   private boolean isOfficialArtifactItem(ItemStack var1) {
      if (var1 != null && var1.getType() != Material.AIR && var1.hasItemMeta()) {
         ItemMeta var2 = var1.getItemMeta();
         if (var2 == null) {
            return false;
         } else {
            PersistentDataContainer var3 = var2.getPersistentDataContainer();
            String var4 = (String)var3.get(this.keyItemId, PersistentDataType.STRING);
            String var5 = (String)var3.get(this.keyUniqueItemId, PersistentDataType.STRING);
            return var4 != null && var5 != null && !var5.isBlank() && this.runtimeCatalogItem(var4) != null;
         }
      } else {
         return false;
      }
   }

   private boolean hasOfficialArtifactIngredient(ItemStack[] var1) {
      if (var1 == null) {
         return false;
      } else {
         for (ItemStack var5 : var1) {
            if (this.isOfficialArtifactItem(var5)) {
               return true;
            }
         }

         return false;
      }
   }

   private boolean isBlockedArtifactProcessingInventory(Inventory var1) {
      if (var1 == null) {
         return false;
      } else if (var1.getType() == InventoryType.CRAFTING && var1.getHolder() instanceof Player) {
         // The top inventory of a normal player inventory view is also
         // reported as CRAFTING.  It is not an external processing surface:
         // catalog items must remain freely movable in the player's own
         // inventory, including cursor and hotbar swaps.
         return false;
      } else {
         return switch (var1.getType()) {
            case CRAFTING, WORKBENCH, CRAFTER, FURNACE, BLAST_FURNACE, SMOKER, BREWING, SMITHING, ANVIL, GRINDSTONE, STONECUTTER, HOPPER, DROPPER, DISPENSER, LOOM, CARTOGRAPHY, ENCHANTING, MERCHANT, BEACON, COMPOSTER -> true;
            default -> false;
         };
      }
   }

   private boolean containsOfficialArtifact(Inventory inventory) {
      if (inventory == null) {
         return false;
      }
      for (ItemStack stack : inventory.getStorageContents()) {
         if (this.isOfficialArtifactItem(stack)) {
            return true;
         }
      }
      return false;
   }

   private boolean shouldBlockOfficialArtifactInsertion(InventoryClickEvent var1) {
      Inventory var2 = var1.getView().getTopInventory();
      if (!this.isBlockedArtifactProcessingInventory(var2)) {
         return false;
      } else {
         int var3 = var1.getRawSlot();
         int var4 = var2.getSize();
         boolean var5 = var3 >= 0 && var3 < var4;
         ItemStack var6 = var1.getCursor();
         ItemStack var7 = var1.getCurrentItem();
         // An official item already inside a processing inventory can be
         // consumed by furnace/anvil/grindstone logic even when the click is
         // aimed at another slot. Cancel the whole interaction instead of
         // protecting only the insertion side.
         if (this.isOfficialArtifactItem(var6) || this.isOfficialArtifactItem(var7) || this.containsOfficialArtifact(var2)) {
            return true;
         }
         Inventory var8 = var1.getClickedInventory();
         if (var5 && this.isOfficialArtifactItem(var6)) {
            return true;
         } else if (var1.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
            && var8 != null
            && var8.equals(var1.getView().getBottomInventory())
            && this.isOfficialArtifactItem(var7)) {
            return true;
         } else if (var1.getAction() == InventoryAction.COLLECT_TO_CURSOR && this.isOfficialArtifactItem(var6)) {
            return true;
         } else if ((var1.getAction() == InventoryAction.HOTBAR_SWAP || var1.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD)
            && this.isOfficialArtifactItem(var7)) {
            return true;
         } else {
            if (var5 && var1.getClick() == ClickType.NUMBER_KEY) {
               int var9 = var1.getHotbarButton();
               if (var9 >= 0 && var9 < 9) {
                  ItemStack var10 = var1.getWhoClicked().getInventory().getItem(var9);
                  if (this.isOfficialArtifactItem(var10)) {
                     return true;
                  }
               }
            }

            if (var5 && var1.getClick() == ClickType.SWAP_OFFHAND) {
               ItemStack var11 = var1.getWhoClicked().getInventory().getItemInOffHand();
               if (this.isOfficialArtifactItem(var11)) {
                  return true;
               }
            }

            return false;
         }
      }
   }

   private CopiMineArtifacts.CatalogItem authenticCatalogItem(ItemStack var1, Player var2, String var3) {
      if (var1 != null && var1.getType() != Material.AIR && var1.hasItemMeta()) {
         ItemMeta var4 = var1.getItemMeta();
         PersistentDataContainer var5 = var4.getPersistentDataContainer();
         String var6 = (String)var5.get(this.keyItemId, PersistentDataType.STRING);
         String var7 = (String)var5.get(this.keyUniqueItemId, PersistentDataType.STRING);
         if (var6 != null && var7 != null) {
            CopiMineArtifacts.CatalogItem var8 = this.runtimeCatalogItem(var6);
            Integer var9 = var4.hasCustomModelData() ? var4.getCustomModelData() : null;
            boolean var10 = var8 != null && var8.customModelData() > 0 && !Objects.equals(var9, var8.customModelData());
            boolean varStackOrMaterialMismatch = var8 == null || var1.getAmount() != 1 || var1.getType() != var8.material();
            String var11 = (String)var5.get(this.keyOwnerUuid, PersistentDataType.STRING);
            String var12 = this.firstNonBlank(var11, "");
            CopiMineArtifacts.OfficialInstanceBinding var13 = this.instanceBindings.get(var7);
            boolean var14 = var2 != null
               && (
                  !var12.equalsIgnoreCase(var2.getUniqueId().toString()) || var13 == null || !var13.ownerUuid().equalsIgnoreCase(var2.getUniqueId().toString())
               );
            boolean var15 = var13 == null || !var13.itemId().equalsIgnoreCase(var6) || !var13.ownerUuid().equalsIgnoreCase(var12);
            String var16 = (String)var5.get(this.keyItemType, PersistentDataType.STRING);
            String var17 = (String)var5.get(this.keySource, PersistentDataType.STRING);
            boolean var18 = this.isDonationCatalogItem(var6)
               && (!"DONATION_SHOP_ITEM".equalsIgnoreCase(this.firstNonBlank(var16, "")) || !"DONATION_SHOP".equalsIgnoreCase(this.firstNonBlank(var17, "")));
            boolean var19 = this.isArCatalogItem(var6)
               && (!this.firstNonBlank(var16, "").isBlank() || !this.firstNonBlank(var17, "").isBlank())
               && (!"AR_SHOP_ITEM".equalsIgnoreCase(this.firstNonBlank(var16, "")) || !"AR_SHOP".equalsIgnoreCase(this.firstNonBlank(var17, "")));
            if (this.provisionalDonationInstanceIds.contains(var7)) {
               return null;
            }
            // instanceToItem.containsKey(uniqueItemId)
            if (var8 != null && this.instanceToItem.containsKey(var7) && !var15 && !var10 && !varStackOrMaterialMismatch && !var14 && !var18 && !var19) {
               this.ensureAttackDamageAttribute(var1, var8);
               return var8;
            } else {
               String var20 = var2.getUniqueId() + ":" + var7 + ":" + var3;
               if (this.suspiciousSeen.add(var20)) {
                  this.runAsync(() -> this.logSuspicious(var2, var3, "itemId=" + var6 + " unique=" + var7 + " model=" + var9));
               }

               var2.sendMessage(
                  this.color(
                     "&cПодозрительный предмет CopiMineArtifacts заблокирован."
                  )
               );
               return null;
            }
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private ItemStack createOfficialItem(CopiMineArtifacts.CatalogItem var1, String var2, UUID var3, String var4) {
      ItemStack var5 = new ItemStack(var1.material());
      ItemMeta var6 = var5.getItemMeta();
      if (var6 == null) {
         return var5;
      } else {
         var6.setDisplayName(this.color(var1.name()));
         ArrayList var7 = new ArrayList();

         for (String var9 : var1.lore()) {
            var7.add(this.color(var9));
         }

         if ("NOT_TODAY_SHIELD".equalsIgnoreCase(var1.effect())) {
            var6.addEnchant(Enchantment.UNBREAKING, 3, true);
            var7.add(this.color("&bПрочность III: щит выдерживает больше ударов."));
         }

         if (var1.customModelData() > 0 && !"NOT_TODAY_SHIELD".equalsIgnoreCase(var1.effect())) {
            // The custom shield keeps its gameplay effect but deliberately
            // uses the vanilla shield texture/model.
            var6.setCustomModelData(var1.customModelData());
         }

         var7.add(this.color("&8Редкость: " + var1.rarity()));
         var6.setLore(var7);
         var6.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE});
         var6.getPersistentDataContainer().set(this.keyItemId, PersistentDataType.STRING, var1.itemId());
         var6.getPersistentDataContainer().set(this.keyUniqueItemId, PersistentDataType.STRING, var2);
         var6.getPersistentDataContainer().set(this.keyCategory, PersistentDataType.STRING, var1.category().name());
         var6.getPersistentDataContainer().set(this.keyRarity, PersistentDataType.STRING, var1.rarity());
         var6.getPersistentDataContainer().set(this.keyOwnerUuid, PersistentDataType.STRING, var3.toString());
         var6.getPersistentDataContainer().set(this.keyOwnerName, PersistentDataType.STRING, this.firstNonBlank(Bukkit.getOfflinePlayer(var3).getName(), ""));
         var6.getPersistentDataContainer().set(this.keyPurchaseId, PersistentDataType.STRING, var4);
         if (this.isDonationCatalogItem(var1.itemId())) {
            var6.getPersistentDataContainer().set(this.keyItemType, PersistentDataType.STRING, "DONATION_SHOP_ITEM");
            var6.getPersistentDataContainer().set(this.keySource, PersistentDataType.STRING, "DONATION_SHOP");
            var6.getPersistentDataContainer().set(this.keyBound, PersistentDataType.BYTE, (byte)1);
            var6.getPersistentDataContainer().set(this.keyReclaimable, PersistentDataType.BYTE, (byte)1);
         } else if (this.isArCatalogItem(var1.itemId()) && !this.isAdminOnlyCatalogItem(var1.itemId())) {
            var6.getPersistentDataContainer().set(this.keyItemType, PersistentDataType.STRING, "AR_SHOP_ITEM");
            var6.getPersistentDataContainer().set(this.keySource, PersistentDataType.STRING, "AR_SHOP");
         }

         this.ensureAttackDamageAttribute(var6, var1);
         var5.setItemMeta(var6);
         return var5;
      }
   }

   private void ensureAttackDamageAttribute(ItemStack stack, CopiMineArtifacts.CatalogItem item) {
      if (stack == null || item == null || !this.isDamageMaterial(item.material()) || this.attackDamageKey == null) {
         return;
      }
      ItemMeta meta = stack.getItemMeta();
      if (meta == null) {
         return;
      }
      this.ensureAttackDamageAttribute(meta, item);
      stack.setItemMeta(meta);
   }

   private boolean shouldBlockOfficialArtifactDrag(InventoryDragEvent event) {
      if (event == null || event.getView() == null) {
         return false;
      }
      Inventory top = event.getView().getTopInventory();
      if (!this.isBlockedArtifactProcessingInventory(top)) {
         return false;
      }
      // A processing inventory that already contains an official item must
      // not be allowed to consume it on a later drag operation.
      if (this.containsOfficialArtifact(top)) {
         return true;
      }
      if (!this.isOfficialArtifactItem(event.getOldCursor())) {
         return false;
      }
      for (int rawSlot : event.getRawSlots()) {
         if (rawSlot >= 0 && rawSlot < top.getSize()) {
            return true;
         }
      }
      return false;
   }

   private void ensureAttackDamageAttribute(ItemMeta meta, CopiMineArtifacts.CatalogItem item) {
      if (meta == null || item == null || !this.isDamageMaterial(item.material()) || this.attackDamageKey == null) {
         return;
      }
      Collection<AttributeModifier> modifiers = meta.getAttributeModifiers(Attribute.GENERIC_ATTACK_DAMAGE);
      if (modifiers != null) {
         for (AttributeModifier modifier : modifiers) {
            if (this.attackDamageKey.equals(modifier.getKey())) {
               return;
            }
         }
      }
      double target = "NALOGOVAYA_KOSA".equalsIgnoreCase(item.effect()) ? 12.0 : 10.0;
      double delta = target - vanillaAttackDamage(item.material());
      if (delta > 0.0) {
         meta.addAttributeModifier(
            Attribute.GENERIC_ATTACK_DAMAGE,
            new AttributeModifier(this.attackDamageKey, delta, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND)
         );
      }
   }

   private double vanillaAttackDamage(Material material) {
      if (material == null) {
         return 0.0;
      }
      return switch (material) {
         case WOODEN_SWORD, GOLDEN_SWORD, STONE_SWORD, IRON_SWORD, DIAMOND_SWORD, NETHERITE_SWORD ->
            material == Material.NETHERITE_SWORD ? 8.0 : material == Material.DIAMOND_SWORD ? 7.0 : material == Material.IRON_SWORD ? 6.0 : material == Material.STONE_SWORD ? 5.0 : 4.0;
         case WOODEN_AXE, GOLDEN_AXE, STONE_AXE, IRON_AXE, DIAMOND_AXE, NETHERITE_AXE ->
            material == Material.NETHERITE_AXE ? 10.0 : material == Material.DIAMOND_AXE ? 9.0 : material == Material.IRON_AXE ? 9.0 : material == Material.STONE_AXE ? 9.0 : 7.0;
         case WOODEN_PICKAXE, GOLDEN_PICKAXE, STONE_PICKAXE, IRON_PICKAXE, DIAMOND_PICKAXE, NETHERITE_PICKAXE ->
            material == Material.NETHERITE_PICKAXE ? 6.0 : material == Material.DIAMOND_PICKAXE ? 5.0 : material == Material.IRON_PICKAXE ? 4.0 : 3.0;
         case WOODEN_SHOVEL, GOLDEN_SHOVEL, STONE_SHOVEL, IRON_SHOVEL, DIAMOND_SHOVEL, NETHERITE_SHOVEL ->
            material == Material.NETHERITE_SHOVEL ? 6.0 : material == Material.DIAMOND_SHOVEL ? 5.0 : material == Material.IRON_SHOVEL ? 4.5 : 2.5;
         case WOODEN_HOE, GOLDEN_HOE, STONE_HOE, IRON_HOE, DIAMOND_HOE, NETHERITE_HOE ->
            material == Material.NETHERITE_HOE ? 1.0 : material == Material.DIAMOND_HOE ? 1.0 : material == Material.IRON_HOE ? 1.0 : 1.0;
         case TRIDENT -> 9.0;
         case MACE -> 6.0;
         default -> 0.0;
      };
   }

   private boolean isDamageMaterial(Material material) {
      return this.vanillaAttackDamage(material) > 0.0;
   }

   private long repairPrice(ItemStack var1, CopiMineArtifacts.CatalogItem var2) {
      // Repair is deliberately predictable: the admin sets one price and it
      // applies to every official custom item regardless of material or wear.
      return Math.max(0L, Math.min(1_000_000_000L, this.getConfig().getLong("repair.fixed-price-ar", 3L)));
   }

   private void persistRepair(UUID playerUuid, String playerName, CopiMineArtifacts.CatalogItem var2, ItemStack var3, String var4, long var5, String var7, int slot, String fingerprint, int originalDamage) throws SQLException {
      String var8 = (String)Objects.requireNonNull(var3.getItemMeta()).getPersistentDataContainer().get(this.keyUniqueItemId, PersistentDataType.STRING);
      Connection var9 = this.pgPool.acquire();

      try {
         var9.setAutoCommit(false);

         try (
            PreparedStatement var10 = var9.prepareStatement(
               "    INSERT INTO artifact_repairs(repair_id,unique_item_id,player_uuid,player_name,item_id,repair_cost_ar,bank_tx_id,status,created_at,slot,item_fingerprint,original_damage)\n    VALUES(?,?,?,?,?,?,?,?,?,?,?,?)\n"
            );
            PreparedStatement var11 = var9.prepareStatement(
               "UPDATE artifact_item_instances SET repaired_count=repaired_count+1,updated_at=? WHERE unique_item_id=?"
            );
         ) {
            long var12 = this.now();
            var10.setString(1, var4);
            var10.setString(2, var8);
            var10.setString(3, Objects.requireNonNull(playerUuid, "playerUuid").toString());
            var10.setString(4, this.firstNonBlank(playerName, "Player"));
            var10.setString(5, var2.itemId());
            var10.setLong(6, var5);
            var10.setString(7, var7);
            // PAID is durable before the physical mutation.  The Bukkit
            // callback moves it to APPLYING only after the exact slot and
            // fingerprint are still present, then COMPLETED after the write.
            var10.setString(8, "PAID");
            var10.setLong(9, var12);
            var10.setInt(10, slot);
            var10.setString(11, this.firstNonBlank(fingerprint, ""));
            var10.setInt(12, Math.max(0, originalDamage));
            var10.executeUpdate();
            var9.commit();
         } catch (SQLException var29) {
            var9.rollback();
            throw var29;
         }
      } finally {
         try {
            var9.setAutoCommit(true);
         } catch (SQLException var24) {
         }

         this.pgPool.release(var9);
      }
   }

   /** Apply the repair only to the captured unique instance, wherever it is
    * still held by the player.  Never write a stale ItemStack into the current
    * main hand after the player has changed slots. */
   private boolean applyRepairToCapturedItem(Player player, String uniqueItemId, String itemId, int originalDamage, int slot, String fingerprint) {
      if (player == null || !player.isOnline() || uniqueItemId == null || uniqueItemId.isBlank()
            || slot < 0 || slot >= player.getInventory().getSize()) {
         return false;
      }
      PlayerInventory inventory = player.getInventory();
      ItemStack current = inventory.getItem(slot);
      if (!uniqueItemId.equals(this.uniqueItemIdOf(current)) || !this.repairFingerprint(current).equals(fingerprint)) {
         return false;
      }
      CatalogItem authenticated = this.authenticCatalogItem(current, player, "repair_apply");
      if (authenticated == null || !authenticated.itemId().equalsIgnoreCase(itemId)
            || !(current.getItemMeta() instanceof Damageable damageable)
            || damageable.getDamage() != originalDamage || damageable.getDamage() <= 0) {
         return false;
      }
      damageable.setDamage(0);
      current.setItemMeta(damageable);
      inventory.setItem(slot, current);
      player.updateInventory();
      return true;
   }

   private void markRepairApplying(String repairId) throws SQLException {
      if (repairId == null || repairId.isBlank() || this.pgPool == null) throw new SQLException("repair id missing");
      Connection connection = this.pgPool.acquire();
      try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE artifact_repairs SET status='APPLYING' WHERE repair_id=? AND status='PAID'")) {
         statement.setString(1, repairId);
         if (statement.executeUpdate() != 1) throw new SQLException("Repair is no longer PAID: " + repairId);
      } finally {
         this.pgPool.release(connection);
      }
   }

   private String repairFingerprint(ItemStack stack) {
      if (stack == null || stack.getType().isAir()) return "";
      StringBuilder value = new StringBuilder(stack.getType().name()).append('|').append(stack.getAmount());
      if (stack.getItemMeta() instanceof Damageable damageable) value.append('|').append(damageable.getDamage());
      ItemMeta meta = stack.getItemMeta();
      if (meta != null) {
         value.append('|').append(meta.hasCustomModelData() ? meta.getCustomModelData() : 0);
         value.append('|').append(meta.displayName());
         value.append('|').append(meta.lore());
         value.append('|').append(this.uniqueItemIdOf(stack));
      }
      try {
         byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(StandardCharsets.UTF_8));
         StringBuilder hex = new StringBuilder(digest.length * 2);
         for (byte b : digest) hex.append(String.format(Locale.ROOT, "%02x", b));
         return hex.toString();
      } catch (Exception ignored) {
         return value.toString();
      }
   }

   private boolean completeRepair(String repairId, String uniqueItemId) {
      if (repairId == null || repairId.isBlank() || uniqueItemId == null || uniqueItemId.isBlank() || this.pgPool == null) {
         return false;
      }
      Connection connection = null;
      try {
         connection = this.pgPool.acquire();
         connection.setAutoCommit(false);
         try (PreparedStatement repair = connection.prepareStatement(
               "UPDATE artifact_repairs SET status='COMPLETED' WHERE repair_id=? AND status='APPLYING'" );
              PreparedStatement instance = connection.prepareStatement(
               "UPDATE artifact_item_instances SET repaired_count=repaired_count+1,updated_at=? WHERE unique_item_id=?")) {
            repair.setString(1, repairId);
            if (repair.executeUpdate() != 1) {
               throw new SQLException("Repair is no longer APPLYING: " + repairId);
            }
            instance.setLong(1, this.now());
            instance.setString(2, uniqueItemId);
            if (instance.executeUpdate() != 1) {
               throw new SQLException("Repair instance is missing: " + uniqueItemId);
            }
            connection.commit();
            return true;
         } catch (SQLException error) {
            connection.rollback();
            throw error;
         }
      } catch (SQLException error) {
         this.getLogger().log(Level.WARNING, "Repair finalization failed: " + repairId, error);
         this.markRepairNeedsReview(repairId);
         return false;
      } finally {
         if (connection != null) {
            try { connection.setAutoCommit(true); } catch (SQLException ignored) { }
            this.pgPool.release(connection);
         }
      }
   }

   private void markRepairNeedsReview(String repairId) {
      if (repairId == null || repairId.isBlank() || this.pgPool == null) {
         return;
      }
      Connection connection = null;
      try {
         connection = this.pgPool.acquire();
         try (PreparedStatement statement = connection.prepareStatement(
               "UPDATE artifact_repairs SET status='REVIEW' WHERE repair_id=? AND status IN ('PAID','APPLYING','COMPLETED')")) {
            statement.setString(1, repairId);
            statement.executeUpdate();
         }
         try (PreparedStatement instance = connection.prepareStatement(
               "UPDATE artifact_item_instances SET status='REPAIR_REVIEW',updated_at=? WHERE unique_item_id=(SELECT unique_item_id FROM artifact_repairs WHERE repair_id=?)")) {
            instance.setLong(1, this.now());
            instance.setString(2, repairId);
            instance.executeUpdate();
         }
      } catch (SQLException error) {
         this.getLogger().log(Level.WARNING, "Repair review mark failed: " + repairId, error);
      } finally {
         if (connection != null) {
            this.pgPool.release(connection);
         }
      }
      String uniqueItemId = this.lookupRepairUniqueItemId(repairId);
      if (!uniqueItemId.isBlank()) {
         this.terminalDonationInstanceIds.add(uniqueItemId);
         this.removeOfficialBinding(uniqueItemId);
      }
   }

   private String lookupRepairUniqueItemId(String repairId) {
      if (repairId == null || repairId.isBlank() || this.pgPool == null) {
         return "";
      }
      try {
         Connection connection = this.pgPool.acquire();
         try (PreparedStatement statement = connection.prepareStatement("SELECT unique_item_id FROM artifact_repairs WHERE repair_id=? LIMIT 1")) {
            statement.setString(1, repairId);
            try (ResultSet result = statement.executeQuery()) {
               return result.next() ? this.firstNonBlank(result.getString(1), "") : "";
            }
         } finally {
            this.pgPool.release(connection);
         }
      } catch (SQLException error) {
         this.getLogger().log(Level.WARNING, "Repair instance lookup failed: " + repairId, error);
         return "";
      }
   }

   private void refundRepairAfterPhysicalFailure(UUID playerUuid, String playerName, long amount, String repairId) {
      if (playerUuid == null || amount <= 0L || this.bridge == null) {
         return;
      }
      try {
         CopiMineArtifacts.BridgeTxnResult refund = this.bridge.transferFromAccount(
               PRESIDENT_BUDGET_ACCOUNT_ID,
               EMPTY_UUID.toString(),
               "Президентская казна",
               playerUuid,
               this.firstNonBlank(playerName, "Player"),
               amount,
               "artifact-repair-refund-" + repairId,
               "AR_ITEM_REPAIR_REFUND",
               "repair=" + repairId);
         if (!refund.ok()) {
            this.audit(this.firstNonBlank(playerName, "Player"), "repair_manual_review", repairId, "refund=" + this.safeBridgeCode(refund.code()));
         }
      } catch (Exception error) {
         this.getLogger().log(Level.WARNING, "Repair refund failed: " + repairId, error);
      }
   }

   private void saveShop(CopiMineArtifacts.Shop var1) throws SQLException {
      Connection var2 = this.pgPool.acquire();

      try (PreparedStatement var3 = var2.prepareStatement(
            "    INSERT INTO artifact_shops(shop_id,world_name,block_x,block_y,block_z,title,enabled,anchor_material,created_at,updated_at)\n    VALUES(?,?,?,?,?,?,?,?,?,?)\n"
         )) {
         long var4 = this.now();
         var3.setString(1, var1.shopId());
         var3.setString(2, var1.world());
         var3.setInt(3, var1.x());
         var3.setInt(4, var1.y());
         var3.setInt(5, var1.z());
         var3.setString(6, var1.title());
         var3.setBoolean(7, var1.enabled());
         var3.setString(8, this.firstNonBlank(var1.anchorMaterial(), ""));
         var3.setLong(9, var4);
         var3.setLong(10, var4);
         var3.executeUpdate();
      } finally {
         this.pgPool.release(var2);
      }
   }

   private void deleteShop(String var1) throws SQLException {
      Connection var2 = this.pgPool.acquire();

      try (PreparedStatement var3 = var2.prepareStatement("DELETE FROM artifact_shops WHERE shop_id=?")) {
         var3.setString(1, var1);
         var3.executeUpdate();
      } finally {
         this.pgPool.release(var2);
      }
   }

   private void createPendingDelivery(Player var1, CopiMineArtifacts.PurchaseContext var2) throws SQLException {
      Connection var3 = this.pgPool.acquire();

      try {
         var3.setAutoCommit(false);

         try (
            PreparedStatement var4 = var3.prepareStatement(
               "    INSERT INTO artifact_pending_deliveries(delivery_id,purchase_id,unique_item_id,player_uuid,item_id,status,created_at,updated_at)\n    VALUES(?,?,?,?,?,?,?,?)\n"
            );
            PreparedStatement var5 = var3.prepareStatement(
               "UPDATE artifact_purchases SET status='PENDING_DELIVERY',delivery_mode='PENDING',updated_at=? WHERE purchase_id=?"
            );
            PreparedStatement var6 = var3.prepareStatement("UPDATE artifact_item_instances SET status='PENDING_DELIVERY',updated_at=? WHERE unique_item_id=?");
         ) {
            String var7 = UUID.randomUUID().toString();
            long var8 = this.now();
            var4.setString(1, var7);
            var4.setString(2, var2.purchaseId());
            var4.setString(3, var2.uniqueItemId());
            var4.setString(4, var1.getUniqueId().toString());
            var4.setString(5, var2.item().itemId());
            var4.setString(6, "PENDING");
            var4.setLong(7, var8);
            var4.setLong(8, var8);
            var4.executeUpdate();
            var5.setLong(1, var8);
            var5.setString(2, var2.purchaseId());
            var5.executeUpdate();
            var6.setLong(1, var8);
            var6.setString(2, var2.uniqueItemId());
            var6.executeUpdate();
            var3.commit();
         } catch (SQLException var29) {
            var3.rollback();
            throw var29;
         }
      } finally {
         try {
            var3.setAutoCommit(true);
         } catch (SQLException var22) {
         }

         this.pgPool.release(var3);
      }
   }

   private void markPurchaseDelivered(String var1, String var2, String var3) throws SQLException {
      Connection var4 = this.pgPool.acquire();

      try {
         var4.setAutoCommit(false);

         try (
            PreparedStatement var5 = var4.prepareStatement("UPDATE artifact_purchases SET status='DELIVERED',updated_at=? WHERE purchase_id=?");
            PreparedStatement var6 = var4.prepareStatement("UPDATE artifact_item_instances SET status='DELIVERED',updated_at=? WHERE unique_item_id=?");
         ) {
            long var7 = this.now();
            var5.setLong(1, var7);
            var5.setString(2, var1);
            var5.executeUpdate();
            var6.setLong(1, var7);
            var6.setString(2, var2);
            var6.executeUpdate();
            var4.commit();
            // cacheOfficialBinding(uniqueItemId, itemId, readOwnerUuidForInstance(c, uniqueItemId));
            this.cacheOfficialBinding(var2, var3, this.readOwnerUuidForInstance(var4, var2));
         } catch (SQLException var24) {
            var4.rollback();
            throw var24;
         }
      } finally {
         try {
            var4.setAutoCommit(true);
         } catch (SQLException var19) {
         }

         this.pgPool.release(var4);
      }
   }

   private void markArtifactPurchaseCancelled(String var1, String var2) throws SQLException {
      Connection var3 = this.pgPool.acquire();

      try {
         var3.setAutoCommit(false);

         try (
            PreparedStatement var4 = var3.prepareStatement("UPDATE artifact_purchases SET status='CANCELLED',updated_at=? WHERE purchase_id=?");
            PreparedStatement var5 = var3.prepareStatement("UPDATE artifact_item_instances SET status='DELETED_AS_INVALID',updated_at=? WHERE unique_item_id=?");
         ) {
            long var6 = this.now();
            var4.setLong(1, var6);
            var4.setString(2, var1);
            var4.executeUpdate();
            var5.setLong(1, var6);
            var5.setString(2, var2);
            var5.executeUpdate();
            var3.commit();
            this.removeOfficialBinding(var2);
         } catch (SQLException var23) {
            var3.rollback();
            throw var23;
         }
      } finally {
         try {
            var3.setAutoCommit(true);
         } catch (SQLException var18) {
         }

         this.pgPool.release(var3);
      }
   }

   private void markArtifactPurchaseReview(String var1, String var2) throws SQLException {
      Connection var3 = this.pgPool.acquire();

      try {
         var3.setAutoCommit(false);

         try (
            PreparedStatement var4 = var3.prepareStatement("UPDATE artifact_purchases SET status='DELIVERY_REVIEW',updated_at=? WHERE purchase_id=?");
            PreparedStatement var5 = var3.prepareStatement("UPDATE artifact_item_instances SET status='DELIVERY_REVIEW',updated_at=? WHERE unique_item_id=?");
         ) {
            long var6 = this.now();
            var4.setLong(1, var6);
            var4.setString(2, var1);
            var4.executeUpdate();
            var5.setLong(1, var6);
            var5.setString(2, var2);
            var5.executeUpdate();
            var3.commit();
         } catch (SQLException var23) {
            var3.rollback();
            throw var23;
         }
      } finally {
         try {
            var3.setAutoCommit(true);
         } catch (SQLException var18) {
         }

         this.pgPool.release(var3);
      }
   }

   private void markPendingClaimed(String var1, String var2, String var3, String var4) throws SQLException {
      Connection var5 = this.pgPool.acquire();

      try {
         var5.setAutoCommit(false);

         try (
            PreparedStatement var6 = var5.prepareStatement(
               "UPDATE artifact_pending_deliveries SET status='CLAIMED',updated_at=? WHERE delivery_id=? AND status='DELIVERING'"
            );
            PreparedStatement var7 = var5.prepareStatement("UPDATE artifact_purchases SET status='DELIVERED',updated_at=? WHERE purchase_id=?");
            PreparedStatement var8 = var5.prepareStatement("UPDATE artifact_item_instances SET status='DELIVERED',updated_at=? WHERE unique_item_id=?");
         ) {
            long var9 = this.now();
            var6.setLong(1, var9);
            var6.setString(2, var1);
            if (var6.executeUpdate() <= 0) {
               throw new SQLException("Pending delivery is no longer in DELIVERING state.");
            }

            var7.setLong(1, var9);
            var7.setString(2, var2);
            var7.executeUpdate();
            var8.setLong(1, var9);
            var8.setString(2, var3);
            var8.executeUpdate();
            var5.commit();
            // cacheOfficialBinding(uniqueItemId, itemId, readOwnerUuidForInstance(c, uniqueItemId));
            this.cacheOfficialBinding(var3, var4, this.readOwnerUuidForInstance(var5, var3));
         } catch (SQLException var30) {
            var5.rollback();
            throw var30;
         }
      } finally {
         try {
            var5.setAutoCommit(true);
         } catch (SQLException var23) {
         }

         this.pgPool.release(var5);
      }
   }

   private CompletableFuture<Boolean> reservePendingDeliveryAsync(UUID var1, String var2) {
      return CompletableFuture.supplyAsync(
         () -> {
            if (var1 != null && !this.firstNonBlank(var2, "").isBlank()) {
               Connection var3 = null;

               Boolean var13;
               try {
                  var3 = this.pgPool.acquire();
                  var3.setAutoCommit(false);

                  try (
                     PreparedStatement var4 = var3.prepareStatement(
                        "    SELECT purchase_id,unique_item_id\n    FROM artifact_pending_deliveries\n    WHERE delivery_id=?\n      AND player_uuid=?\n      AND status='PENDING'\n    FOR UPDATE\n"
                     );
                     PreparedStatement var5 = var3.prepareStatement(
                        "UPDATE artifact_pending_deliveries SET status='DELIVERING',updated_at=? WHERE delivery_id=?"
                     );
                     PreparedStatement var6 = var3.prepareStatement("UPDATE artifact_purchases SET status='DELIVERING',updated_at=? WHERE purchase_id=?");
                     PreparedStatement var7 = var3.prepareStatement(
                        "UPDATE artifact_item_instances SET status='DELIVERING',updated_at=? WHERE unique_item_id=?"
                     );
                  ) {
                     var4.setString(1, var2);
                     var4.setString(2, var1.toString());

                     try (ResultSet var8 = var4.executeQuery()) {
                        if (!var8.next()) {
                           var3.rollback();
                           return false;
                        }

                        String var9 = var8.getString(1);
                        String var10 = var8.getString(2);
                        long var11 = this.now();
                        var5.setLong(1, var11);
                        var5.setString(2, var2);
                        var5.executeUpdate();
                        var6.setLong(1, var11);
                        var6.setString(2, var9);
                        var6.executeUpdate();
                        var7.setLong(1, var11);
                        var7.setString(2, var10);
                        var7.executeUpdate();
                        var3.commit();
                        var13 = true;
                     }
                  } catch (SQLException var44) {
                     var3.rollback();
                     throw new CompletionException(var44);
                  }
               } catch (SQLException var45) {
                  throw new CompletionException(var45);
               } finally {
                  if (var3 != null) {
                     try {
                        var3.setAutoCommit(true);
                     } catch (SQLException var33) {
                     }

                     this.pgPool.release(var3);
                  }
               }

               return var13;
            } else {
               return false;
            }
         },
         this.dbExecutor
      );
   }

   private CompletableFuture<Boolean> releasePendingDeliveryAsync(UUID var1, String var2) {
      return CompletableFuture.supplyAsync(
         () -> {
            if (var1 != null && !this.firstNonBlank(var2, "").isBlank()) {
               Connection var3 = null;

               Boolean var13;
               try {
                  var3 = this.pgPool.acquire();
                  var3.setAutoCommit(false);

                  try (
                     PreparedStatement var4 = var3.prepareStatement(
                        "    SELECT purchase_id,unique_item_id\n    FROM artifact_pending_deliveries\n    WHERE delivery_id=?\n      AND player_uuid=?\n      AND status='DELIVERING'\n    FOR UPDATE\n"
                     );
                     PreparedStatement var5 = var3.prepareStatement("UPDATE artifact_pending_deliveries SET status='PENDING',updated_at=? WHERE delivery_id=?");
                     PreparedStatement var6 = var3.prepareStatement("UPDATE artifact_purchases SET status='PENDING_DELIVERY',updated_at=? WHERE purchase_id=?");
                     PreparedStatement var7 = var3.prepareStatement(
                        "UPDATE artifact_item_instances SET status='PENDING_DELIVERY',updated_at=? WHERE unique_item_id=?"
                     );
                  ) {
                     var4.setString(1, var2);
                     var4.setString(2, var1.toString());

                     try (ResultSet var8 = var4.executeQuery()) {
                        if (!var8.next()) {
                           var3.rollback();
                           return false;
                        }

                        String var9 = var8.getString(1);
                        String var10 = var8.getString(2);
                        long var11 = this.now();
                        var5.setLong(1, var11);
                        var5.setString(2, var2);
                        var5.executeUpdate();
                        var6.setLong(1, var11);
                        var6.setString(2, var9);
                        var6.executeUpdate();
                        var7.setLong(1, var11);
                        var7.setString(2, var10);
                        var7.executeUpdate();
                        var3.commit();
                        var13 = true;
                     }
                  } catch (SQLException var44) {
                     var3.rollback();
                     throw new CompletionException(var44);
                  }
               } catch (SQLException var45) {
                  throw new CompletionException(var45);
               } finally {
                  if (var3 != null) {
                     try {
                        var3.setAutoCommit(true);
                     } catch (SQLException var33) {
                     }

                     this.pgPool.release(var3);
                  }
               }

               return var13;
            } else {
               return false;
            }
         },
         this.dbExecutor
      );
   }

   private void lockDonationInstanceEntitlement(Connection var1, UUID var2, String var3) throws SQLException {
      if (var1 != null && var2 != null && !this.firstNonBlank(var3, "").isBlank()) {
         try (PreparedStatement var4 = var1.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))")) {
            var4.setString(1, "artifact-donation-entitlement:" + var2 + ":" + var3.toLowerCase(Locale.ROOT));
            var4.execute();
         }
      }
   }

   private void lockArtifactPurchaseConstraints(Connection var1, String var2, String var3) throws SQLException {
      if (var1 != null && !this.firstNonBlank(var3, "").isBlank()) {
         try (PreparedStatement var4 = var1.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))")) {
            var4.setString(1, "artifact-purchase-supply:" + var3.toLowerCase(Locale.ROOT));
            var4.execute();
         }

         if (!this.firstNonBlank(var2, "").isBlank()) {
            try (PreparedStatement var11 = var1.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))")) {
               var11.setString(1, "artifact-purchase-player:" + var2.toLowerCase(Locale.ROOT) + ":" + var3.toLowerCase(Locale.ROOT));
               var11.execute();
            }
         }
      }
   }

    private void persistDonationInstances(UUID ownerUuid, String purchaseId, String itemId, List<String> uniqueItemIds) throws SQLException {
      if (uniqueItemIds != null && !uniqueItemIds.isEmpty()) {
         Connection var5 = this.pgPool.acquire();

         try {
            var5.setAutoCommit(false);

            try (
               PreparedStatement var6 = var5.prepareStatement(
                  "    SELECT unique_item_id\n    FROM artifact_item_instances\n    WHERE owner_uuid=?\n      AND item_id=?\n      AND status IN ('ACTIVE','DELIVERING','PENDING_DELIVERY')\n    FOR UPDATE\n"
               );
               PreparedStatement var7 = var5.prepareStatement(
                  "    INSERT INTO artifact_item_instances(unique_item_id,item_id,owner_uuid,purchase_id,status,repaired_count,created_at,updated_at)\n    VALUES(?,?,?,?,?,?,?,?)\n"
               );
            ) {
               // lockDonationInstanceEntitlement(c, ownerUuid, itemId);
               this.lockDonationInstanceEntitlement(var5, ownerUuid, itemId);
               var6.setString(1, ownerUuid == null ? "" : ownerUuid.toString());
               var6.setString(2, itemId);

               try (ResultSet var8 = var6.executeQuery()) {
                  if (var8.next()) {
                     throw new SQLException("Donation entitlement already has an active instance.");
                  }
               }

               long var33 = this.now();

               for (String var11 : uniqueItemIds) {
                  var7.setString(1, var11);
                  var7.setString(2, itemId);
                  var7.setString(3, ownerUuid == null ? "" : ownerUuid.toString());
                  var7.setString(4, purchaseId);
                  var7.setString(5, "DELIVERING");
                  var7.setInt(6, 0);
                  var7.setLong(7, var33);
                  var7.setLong(8, var33);
                  var7.addBatch();
               }

               var7.executeBatch();
               var5.commit();
            } catch (SQLException var31) {
               var5.rollback();
               throw var31;
            }
         } finally {
            try {
               var5.setAutoCommit(true);
            } catch (SQLException var24) {
            }

            this.pgPool.release(var5);
         }
      } else {
         throw new SQLException("Donation delivery has no item instances to persist.");
      }
   }

    private void markDonationInstancesDelivered(String var1, List<String> var2) throws SQLException {
      if (var2 != null && !var2.isEmpty()) {
         Connection var3 = this.pgPool.acquire();

         try {
            var3.setAutoCommit(false);

            try (PreparedStatement var4 = var3.prepareStatement("UPDATE artifact_item_instances SET status='ACTIVE',updated_at=? WHERE unique_item_id=? AND status='DELIVERING'")) {
               long var5 = this.now();

               for (String var8 : var2) {
                  var4.setLong(1, var5);
                  var4.setString(2, var8);
                  var4.addBatch();
               }

               int[] var22 = var4.executeBatch();
               for (int var9 : var22) {
                  if (var9 != 1 && var9 != Statement.SUCCESS_NO_INFO) {
                     throw new SQLException("Donation instance delivery finalization lost DELIVERING state.");
                  }
               }
               var3.commit();

               for (String var23 : var2) {
                  // cacheOfficialBinding(uniqueItemId, itemId, readOwnerUuidForInstance(c, uniqueItemId));
                  this.cacheOfficialBinding(var23, var1, this.readOwnerUuidForInstance(var3, var23));
               }
               this.removeProvisionalDonationInstances(var2);
            } catch (SQLException var20) {
               var3.rollback();
               throw var20;
            }
         } finally {
            try {
               var3.setAutoCommit(true);
            } catch (SQLException var17) {
            }

            this.pgPool.release(var3);
         }
      }
   }

   private void markDonationInstancesFailed(List<String> var1) throws SQLException {
      if (var1 != null && !var1.isEmpty()) {
         Connection var2 = this.pgPool.acquire();

         try {
            var2.setAutoCommit(false);

            try (PreparedStatement var3 = var2.prepareStatement(
                  "UPDATE artifact_item_instances SET status='DELETED_AS_INVALID',updated_at=? WHERE unique_item_id=? AND status='DELIVERING'"
               )) {
               long var4 = this.now();

               for (String var7 : var1) {
                  var3.setLong(1, var4);
                  var3.setString(2, var7);
                  var3.addBatch();
               }

               int[] var17 = var3.executeBatch();
               for (int var8 : var17) {
                  if (var8 != 1 && var8 != Statement.SUCCESS_NO_INFO) {
                     throw new SQLException("Donation instance failure cleanup lost DELIVERING state.");
                  }
               }
               var2.commit();

               for (String var22 : var1) {
                  this.removeOfficialBinding(var22);
               }
               this.removeProvisionalDonationInstances(var1);
            } catch (SQLException var19) {
               var2.rollback();
               throw var19;
            }
         } finally {
            try {
               var2.setAutoCommit(true);
            } catch (SQLException var16) {
            }

            this.pgPool.release(var2);
         }
      }
   }

    private DonationReclaimContext prepareDonationReclaim(UUID ownerUuid, ReclaimableDonationRow row) throws SQLException {
      if (ownerUuid != null && row != null) {
         Connection var3 = this.pgPool.acquire();

         CopiMineArtifacts.DonationReclaimContext var13;
         try {
            var3.setAutoCommit(false);

            try (
               PreparedStatement var4 = var3.prepareStatement(
                  "    SELECT status,purchase_id,item_id\n    FROM artifact_item_instances\n    WHERE owner_uuid=?\n      AND unique_item_id=?\n    FOR UPDATE\n"
               );
               PreparedStatement var5 = var3.prepareStatement(
                  "    SELECT unique_item_id\n    FROM artifact_item_instances\n    WHERE owner_uuid=?\n      AND item_id=?\n      AND status IN ('ACTIVE','DELIVERING','PENDING_DELIVERY')\n    FOR UPDATE\n"
               );
               PreparedStatement var6 = var3.prepareStatement(
                  "    UPDATE artifact_item_instances\n    SET status='REPLACED_AFTER_LOSS',updated_at=?\n    WHERE owner_uuid=?\n      AND unique_item_id=?\n"
               );
               PreparedStatement var7 = var3.prepareStatement(
                  "    INSERT INTO artifact_item_instances(unique_item_id,item_id,owner_uuid,purchase_id,status,repaired_count,created_at,updated_at)\n    VALUES(?,?,?,?, 'DELIVERING', 0, ?, ?)\n"
               );
            ) {
               // lockDonationInstanceEntitlement(c, ownerUuid, row.itemId());
               this.lockDonationInstanceEntitlement(var3, ownerUuid, row.itemId());
               var4.setString(1, ownerUuid.toString());
               var4.setString(2, row.uniqueItemId());

               String var8;
               String var9;
               try (ResultSet var10 = var4.executeQuery()) {
                  if (!var10.next()) {
                     throw new SQLException("Donation reclaim target is missing.");
                  }

                  String var11 = this.firstNonBlank(var10.getString(1), "");
                  if (!"LOST_RECLAIMABLE".equalsIgnoreCase(var11)) {
                     throw new SQLException("Donation reclaim target is not reclaimable anymore.");
                  }

                  var8 = this.firstNonBlank(var10.getString(2), row.purchaseId());
                  var9 = this.firstNonBlank(var10.getString(3), row.itemId());
               }

               var5.setString(1, ownerUuid.toString());
               var5.setString(2, var9);

               try (ResultSet var48 = var5.executeQuery()) {
                  if (var48.next()) {
                     throw new SQLException("Donation reclaim blocked because an active instance already exists.");
                  }
               }

               long var49 = this.now();
               String var12 = UUID.randomUUID().toString();
               var6.setLong(1, var49);
               var6.setString(2, ownerUuid.toString());
               var6.setString(3, row.uniqueItemId());
               var6.executeUpdate();
               var7.setString(1, var12);
               var7.setString(2, var9);
               var7.setString(3, ownerUuid.toString());
               // A reclaim replacement is not a new shop delivery. Keeping
               // purchase_id empty lets stranded-delivery recovery roll it
               // back to LOST_RECLAIMABLE instead of treating it as a sale.
               var7.setString(4, "");
               var7.setLong(5, var49);
               var7.setLong(6, var49);
               var7.executeUpdate();
               var3.commit();
               // The old PDC identity is now terminal.  Drop its in-memory
               // binding only after the transaction commits, otherwise a
               // concurrently running game event could still authenticate a
               // replaced physical copy and use it alongside the reclaim
               // replacement.
               this.terminalDonationInstanceIds.add(row.uniqueItemId());
               this.removeOfficialBinding(row.uniqueItemId());
               var13 = new CopiMineArtifacts.DonationReclaimContext(row.uniqueItemId(), var12, var8, var9);
            } catch (SQLException var46) {
               var3.rollback();
               throw var46;
            }
         } finally {
            try {
               var3.setAutoCommit(true);
            } catch (SQLException var33) {
            }

            this.pgPool.release(var3);
         }

         return var13;
      } else {
         throw new SQLException("Donation reclaim context is incomplete.");
      }
   }

    private void completeDonationReclaim(UUID var1, CopiMineArtifacts.DonationReclaimContext var2) throws SQLException {
      if (var1 != null && var2 != null) {
         Connection var3 = this.pgPool.acquire();

         try {
            var3.setAutoCommit(false);

            try (PreparedStatement var4 = var3.prepareStatement(
                  "    UPDATE artifact_item_instances\n    SET status='ACTIVE',updated_at=?\n    WHERE owner_uuid=?\n      AND unique_item_id=?\n"
                     + "      AND status='DELIVERING'\n"
                )) {
               long var5 = this.now();
               var4.setLong(1, var5);
               var4.setString(2, var1.toString());
               var4.setString(3, var2.newUniqueItemId());
               if (var4.executeUpdate() != 1) {
                  throw new SQLException("Donation reclaim completion lost DELIVERING state.");
               }
               var3.commit();
               this.cacheOfficialBinding(var2.newUniqueItemId(), var2.itemId(), var1);
               this.removeProvisionalDonationInstances(List.of(var2.newUniqueItemId()));
            } catch (SQLException var18) {
               var3.rollback();
               throw var18;
            }
         } finally {
            try {
               var3.setAutoCommit(true);
            } catch (SQLException var15) {
            }

            this.pgPool.release(var3);
         }
      }
   }

   private void rollbackDonationReclaim(UUID var1, CopiMineArtifacts.DonationReclaimContext var2) throws SQLException {
      if (var1 != null && var2 != null) {
         Connection var3 = this.pgPool.acquire();

         try {
            var3.setAutoCommit(false);

            try (
                PreparedStatement var4 = var3.prepareStatement(
                   "    UPDATE artifact_item_instances\n    SET status='DELETED_AS_INVALID',updated_at=?\n    WHERE owner_uuid=?\n      AND unique_item_id=?\n      AND status='DELIVERING'\n"
                );
               PreparedStatement var5 = var3.prepareStatement(
                  "    UPDATE artifact_item_instances\n    SET status='LOST_RECLAIMABLE',updated_at=?\n    WHERE owner_uuid=?\n      AND unique_item_id=?\n      AND status='REPLACED_AFTER_LOSS'\n"
               );
            ) {
               long var6 = this.now();
               var4.setLong(1, var6);
               var4.setString(2, var1.toString());
               var4.setString(3, var2.newUniqueItemId());
                if (var4.executeUpdate() != 1) {
                   throw new SQLException("Donation reclaim rollback lost DELIVERING state.");
                }
               var5.setLong(1, var6);
               var5.setString(2, var1.toString());
               var5.setString(3, var2.oldUniqueItemId());
               var5.executeUpdate();
               var3.commit();
               this.removeOfficialBinding(var2.newUniqueItemId());
               this.removeProvisionalDonationInstances(List.of(var2.newUniqueItemId()));
            } catch (SQLException var23) {
               var3.rollback();
               throw var23;
            }
         } finally {
            try {
               var3.setAutoCommit(true);
            } catch (SQLException var18) {
            }

            this.pgPool.release(var3);
         }
      }
   }

   private void rollbackStrandedDonationReclaim(UUID var1, CopiMineArtifacts.DeliveringInstanceRow var2) throws SQLException {
      if (var1 != null && var2 != null && !this.firstNonBlank(var2.uniqueItemId(), "").isBlank()) {
         Connection var3 = this.pgPool.acquire();

         try {
            var3.setAutoCommit(false);

            try (
               PreparedStatement var4 = var3.prepareStatement(
                  "    SELECT item_id,status\n    FROM artifact_item_instances\n    WHERE owner_uuid=?\n      AND unique_item_id=?\n    FOR UPDATE\n"
               );
               PreparedStatement var5 = var3.prepareStatement(
                  "    SELECT unique_item_id\n    FROM artifact_item_instances\n    WHERE owner_uuid=?\n      AND item_id=?\n      AND status='REPLACED_AFTER_LOSS'\n    ORDER BY updated_at DESC\n    LIMIT 1\n    FOR UPDATE\n"
               );
               PreparedStatement var6 = var3.prepareStatement(
                  "    UPDATE artifact_item_instances\n    SET status='DELETED_AS_INVALID',updated_at=?\n    WHERE owner_uuid=?\n      AND unique_item_id=?\n      AND status='DELIVERING'\n"
               );
               PreparedStatement var7 = var3.prepareStatement(
                  "    UPDATE artifact_item_instances\n    SET status='LOST_RECLAIMABLE',updated_at=?\n    WHERE owner_uuid=?\n      AND unique_item_id=?\n      AND status='REPLACED_AFTER_LOSS'\n"
               );
            ) {
               var4.setString(1, var1.toString());
               var4.setString(2, var2.uniqueItemId());

               String var8;
               try (ResultSet var9 = var4.executeQuery()) {
                  if (!var9.next()) {
                     var3.rollback();
                     return;
                  }

                  if (!"DELIVERING".equalsIgnoreCase(this.firstNonBlank(var9.getString("status"), ""))) {
                     var3.rollback();
                     return;
                  }

                  var8 = this.firstNonBlank(var9.getString("item_id"), var2.itemId());
               }

               var5.setString(1, var1.toString());
               var5.setString(2, var8);

               String var49;
               try (ResultSet var10 = var5.executeQuery()) {
                  if (!var10.next()) {
                     var3.rollback();
                     return;
                  }

                  var49 = var10.getString("unique_item_id");
               }

               long var50 = this.now();
               var6.setLong(1, var50);
               var6.setString(2, var1.toString());
               var6.setString(3, var2.uniqueItemId());
               var6.executeUpdate();
               var7.setLong(1, var50);
               var7.setString(2, var1.toString());
               var7.setString(3, var49);
               var7.executeUpdate();
               var3.commit();
               this.removeOfficialBinding(var2.uniqueItemId());
            } catch (SQLException var47) {
               var3.rollback();
               throw var47;
            }
         } finally {
            try {
               var3.setAutoCommit(true);
            } catch (SQLException var34) {
            }

            this.pgPool.release(var3);
         }
      }
   }

   private List<String> readRecentPurchases(String var1) {
      Connection var2 = null;

      List var4;
      try {
         var2 = this.pgPool.acquire();

         try (PreparedStatement var3 = var2.prepareStatement(
               "SELECT item_id,status,created_at FROM artifact_purchases WHERE player_uuid=? ORDER BY created_at DESC LIMIT 18"
            )) {
            var3.setString(1, var1);

            try (ResultSet var20 = var3.executeQuery()) {
               ArrayList var5 = new ArrayList();

               while (var20.next()) {
                  var5.add(var20.getString(1) + " | " + var20.getString(2) + " | " + Instant.ofEpochSecond(var20.getLong(3)));
               }

               return var5;
            }
         }
      } catch (SQLException var18) {
         this.getLogger().log(Level.WARNING, "Artifacts purchase history read failed", (Throwable)var18);
         var4 = List.of("DB_ERROR: logged");
      } finally {
         if (var2 != null) {
            this.pgPool.release(var2);
         }
      }

      return var4;
   }

   private List<CopiMineArtifacts.PendingDeliveryRow> readPending(String var1) {
      return this.readPendingByStatus(var1, "PENDING");
   }

   private List<CopiMineArtifacts.PendingDeliveryRow> readPendingByStatus(String var1, String var2) {
      Connection var3 = null;

      List var5;
      try {
         var3 = this.pgPool.acquire();

         try (PreparedStatement var4 = var3.prepareStatement(
               "SELECT delivery_id,purchase_id,unique_item_id,item_id FROM artifact_pending_deliveries WHERE player_uuid=? AND status=? ORDER BY created_at DESC LIMIT 18"
            )) {
            var4.setString(1, var1);
            var4.setString(2, this.firstNonBlank(var2, "PENDING"));

            try (ResultSet var21 = var4.executeQuery()) {
               ArrayList var6 = new ArrayList();

               while (var21.next()) {
                  var6.add(new CopiMineArtifacts.PendingDeliveryRow(var21.getString(1), var21.getString(2), var21.getString(3), var21.getString(4)));
               }

               return var6;
            }
         }
      } catch (SQLException var19) {
         var5 = List.of();
      } finally {
         if (var3 != null) {
            this.pgPool.release(var3);
         }
      }

      return var5;
   }

   private List<CopiMineArtifacts.DeliveringInstanceRow> readDeliveringInstances(String var1) {
      Connection var2 = null;

      List var4;
      try {
         var2 = this.pgPool.acquire();

         try (PreparedStatement var3 = var2.prepareStatement(
               "    SELECT unique_item_id,purchase_id,item_id\n    FROM artifact_item_instances\n    WHERE owner_uuid=?\n      AND status='DELIVERING'\n    ORDER BY updated_at DESC\n    LIMIT 32\n"
            )) {
            var3.setString(1, var1);

            try (ResultSet var20 = var3.executeQuery()) {
               ArrayList var5 = new ArrayList();

               while (var20.next()) {
                  var5.add(new CopiMineArtifacts.DeliveringInstanceRow(var20.getString(1), var20.getString(2), var20.getString(3)));
               }

               return var5;
            }
         }
      } catch (SQLException var18) {
         this.getLogger().log(Level.WARNING, "Delivering instance recovery lookup failed", (Throwable)var18);
         var4 = List.of();
      } finally {
         if (var2 != null) {
            this.pgPool.release(var2);
         }
      }

      return var4;
   }

   private int pendingCount(String var1) {
      Connection var2 = null;

      byte var4;
      try {
         var2 = this.pgPool.acquire();

         try (PreparedStatement var3 = var2.prepareStatement("SELECT COUNT(*) FROM artifact_pending_deliveries WHERE player_uuid=? AND status='PENDING'")) {
            var3.setString(1, var1);

            try (ResultSet var20 = var3.executeQuery()) {
               return var20.next() ? var20.getInt(1) : 0;
            }
         }
      } catch (SQLException var18) {
         var4 = 0;
      } finally {
         if (var2 != null) {
            this.pgPool.release(var2);
         }
      }

      return var4;
   }

   private int purchasedCount(String var1) {
      Connection var2 = null;

      byte var4;
      try {
         var2 = this.pgPool.acquire();
         return this.purchasedCount(var2, var1);
      } catch (SQLException var8) {
         var4 = 0;
      } finally {
         if (var2 != null) {
            this.pgPool.release(var2);
         }
      }

      return var4;
   }

   private int purchasedCount(Connection var1, String var2) throws SQLException {
      int var5;
      try (PreparedStatement var3 = var1.prepareStatement(
            "SELECT COUNT(*) FROM artifact_purchases WHERE item_id=? AND status IN ('PAID','DELIVERING','DELIVERED','PENDING_DELIVERY')"
         )) {
         var3.setString(1, var2);

         try (ResultSet var4 = var3.executeQuery()) {
            var5 = var4.next() ? var4.getInt(1) : 0;
         }
      }

      return var5;
   }

   private int playerPurchasedCount(String var1, String var2) {
      Connection var3 = null;

      byte var5;
      try {
         var3 = this.pgPool.acquire();
         return this.playerPurchasedCount(var3, var1, var2);
      } catch (SQLException var9) {
         var5 = 0;
      } finally {
         if (var3 != null) {
            this.pgPool.release(var3);
         }
      }

      return var5;
   }

   private int playerPurchasedCount(Connection var1, String var2, String var3) throws SQLException {
      int var6;
      try (PreparedStatement var4 = var1.prepareStatement(
            "SELECT COUNT(*) FROM artifact_purchases WHERE player_uuid=? AND item_id=? AND status IN ('PAID','DELIVERING','DELIVERED','PENDING_DELIVERY') AND (CASE WHEN created_at > 100000000000 THEN created_at / 1000 ELSE created_at END) > COALESCE((SELECT CASE WHEN reset_at > 100000000000 THEN reset_at / 1000 ELSE reset_at END FROM artifact_purchase_limit_resets WHERE player_uuid=? AND item_id=?),0)"
         )) {
         var4.setString(1, var2);
         var4.setString(2, var3);
         var4.setString(3, var2);
         var4.setString(4, var3);

         try (ResultSet var5 = var4.executeQuery()) {
            var6 = var5.next() ? var5.getInt(1) : 0;
         }
      }

      return var6;
   }

   private void logSuspicious(Player var1, String var2, String var3) {
      Connection var4 = null;

      try {
         var4 = this.pgPool.acquire();

         try (PreparedStatement var5 = var4.prepareStatement(
               "    INSERT INTO artifact_suspicious_events(event_id,player_uuid,player_name,event_type,details,created_at)\n    VALUES(?,?,?,?,?,?)\n"
            )) {
            var5.setString(1, UUID.randomUUID().toString());
            var5.setString(2, var1.getUniqueId().toString());
            var5.setString(3, var1.getName());
            var5.setString(4, var2);
            var5.setString(5, var3);
            var5.setLong(6, this.now());
            var5.executeUpdate();
         }
      } catch (SQLException var15) {
      } finally {
         if (var4 != null) {
            this.pgPool.release(var4);
         }
      }
   }

   private void audit(String var1, String var2, String var3, String var4) {
      Connection var5 = null;

      try {
         var5 = this.pgPool.acquire();

         try (PreparedStatement var6 = var5.prepareStatement(
               "    INSERT INTO artifact_audit_log(audit_id,actor,action,target_id,details,created_at)\n    VALUES(?,?,?,?,?,?)\n"
            )) {
            var6.setString(1, UUID.randomUUID().toString());
            var6.setString(2, var1);
            var6.setString(3, var2);
            var6.setString(4, var3);
            var6.setString(5, var4);
            var6.setLong(6, this.now());
            var6.executeUpdate();
         }
      } catch (SQLException var16) {
      } finally {
         if (var5 != null) {
            this.pgPool.release(var5);
         }
      }
   }

   private void tickPendingHints() {
      List<UUID> var1 = Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList();
      if (!var1.isEmpty()) {
         this.runAsync(() -> {
            Map<String, Integer> var2 = this.pendingCountsForPlayers(var1);
            if (!var2.isEmpty()) {
               this.runSync(() -> {
                  for (UUID var4 : var1) {
                     int var5 = var2.getOrDefault(var4.toString(), 0);
                     Player var6 = Bukkit.getPlayer(var4);
                     if (var5 > 0 && var6 != null && var6.isOnline()) {
                        var6.sendActionBar(this.color("&eCopiMineArtifacts: отложенная выдача x" + var5));
                     }
                  }
               });
            }
         });
      }
   }

   private void updateShopTitle(CopiMineArtifacts.Shop shop) throws SQLException {
      Connection connection = this.pgPool.acquire();
      try (PreparedStatement statement = connection.prepareStatement("UPDATE artifact_shops SET title=?,updated_at=? WHERE shop_id=?")) {
         statement.setString(1, shop.title());
         statement.setLong(2, this.now());
         statement.setString(3, shop.shopId());
         if (statement.executeUpdate() != 1) {
            throw new SQLException("Shop not found: " + shop.shopId());
         }
      } finally {
         this.pgPool.release(connection);
      }
   }

   private Map<String, Integer> pendingCountsForPlayers(Collection<UUID> var1) {
      if (var1 == null || var1.isEmpty()) {
         return Map.of();
      }

      String[] var2 = var1.stream().filter(Objects::nonNull).map(UUID::toString).toArray(String[]::new);
      if (var2.length == 0) {
         return Map.of();
      }

      Connection var3 = null;
      Array var4 = null;

      try {
         var3 = this.pgPool.acquire();
         var4 = var3.createArrayOf("text", var2);
         try (PreparedStatement var5 = var3.prepareStatement(
               "SELECT player_uuid,COUNT(*) FROM artifact_pending_deliveries WHERE status='PENDING' AND player_uuid = ANY (?) GROUP BY player_uuid"
            )) {
            var5.setArray(1, var4);
            try (ResultSet var6 = var5.executeQuery()) {
               Map<String, Integer> var7 = new HashMap<>();
               while (var6.next()) {
                  var7.put(this.firstNonBlank(var6.getString(1), ""), Math.max(0, var6.getInt(2)));
               }

               return var7;
            }
         }
      } catch (SQLException var13) {
         this.getLogger().log(Level.FINE, "Pending-delivery hint query failed", (Throwable)var13);
         return Map.of();
      } finally {
         if (var4 != null) {
            try {
               var4.free();
            } catch (SQLException var12) {
            }
         }

         if (var3 != null) {
            this.pgPool.release(var3);
         }
      }
   }

   private CopiMineArtifacts.Shop currentShop(CopiMineArtifacts.SessionState var1) {
      if (var1.shopId != null && !var1.shopId.isBlank()) {
         for (CopiMineArtifacts.Shop var3 : this.shopsByLocation.values()) {
            if (var3.shopId().equalsIgnoreCase(var1.shopId)) {
               return var3;
            }
         }
         // Never silently switch a stale session to another shop. This could
         // charge a purchase from a deleted shop against an unrelated one.
         return null;
      } else {
         return this.shopsByLocation.values().stream().findFirst().orElse(null);
      }
   }

   private CopiMineArtifacts.SessionState session(Player player) {
      return this.sessions.computeIfAbsent(player.getUniqueId(), var0 -> new CopiMineArtifacts.SessionState());
   }

   private CopiMineArtifacts.SessionState freshSession(Player var1) {
      CopiMineArtifacts.SessionState var2 = this.session(var1);
      var2.sessionId = UUID.randomUUID();
      var2.shopId = "";
      var2.viewType = CopiMineArtifacts.ViewType.MAIN;
      var2.currentCategory = "";
      var2.currentItemId = "";
      var2.page = 0;
      var2.pinBuffer = "";
      if (var2.purchaseInFlightId.isBlank()) {
         var2.actions.clear();
      }

      var2.lastActionAt = System.currentTimeMillis();
      return var2;
   }

   private Inventory createMenu(Player var1, CopiMineArtifacts.SessionState var2, CopiMineArtifacts.ViewType var3, int var4, String var5) {
      var2.viewType = var3;
      var2.actions.clear();
      var2.lastActionAt = System.currentTimeMillis();
      CopiMineArtifacts.MenuHolder var6 = new CopiMineArtifacts.MenuHolder(var2, var1.getUniqueId());
      // Inventory inv = Bukkit.createInventory
      Inventory var7 = Bukkit.createInventory(var6, var4, this.color(var5));
      var6.setInventory(var7);
      this.debugGui(
         "open player="
            + var1.getName()
            + " screen="
            + var3
            + " shop="
            + var2.shopId
            + " category="
            + var2.currentCategory
            + " item="
            + var2.currentItemId
            + " page="
            + var2.page
            + " session="
            + var2.sessionId
      );
      return var7;
   }

   private boolean isArtifactsAdmin(Player var1) {
      if (var1 == null || this.isRestrictedJuniorArtifactsAdmin(var1)) {
         return false;
      }
       return var1.isOp()
          || var1.hasPermission("copimine.artifacts.admin")
          || var1.hasPermission("copimine.admin")
          || var1.hasPermission("copimine.admin.ultra")
          || var1.hasPermission("copimine.ultra.admin");
   }

   private boolean isRestrictedJuniorArtifactsAdmin(Player var1) {
      return var1 != null
         && !var1.isOp()
         && !var1.hasPermission("copimine.admin")
         && !var1.hasPermission("copimine.ultra.admin")
         && var1.hasPermission("copimine.admin.junior");
   }

   private boolean hasArtifactPermission(Player var1, String var2) {
      return var1 != null && !this.isRestrictedJuniorArtifactsAdmin(var1) && (this.isArtifactsAdmin(var1) || var1.hasPermission(var2));
   }

   private void noPermission(Player var1) {
      var1.sendMessage(
         this.color(
            "&cУ вас нет прав для этой команды."
         )
      );
   }

   private void sendPlayerShopHelp(Player var1) {
      var1.sendMessage(
         this.color(
            "&aЛавка артефактов открывается кликом по блоку лавки."
         )
      );
      var1.sendMessage(
         this.color(
            "&7Найдите лавку на спавне или в специальных местах сервера."
         )
      );
   }

   private String donationWebsiteBaseUrl() {
      return this.firstNonBlank(this.getConfig().getString("donation.website-base-url"), "https://admin.copimine.ru").replaceAll("/+$", "");
   }

   private String donationPurchaseUrl(String var1) {
      return this.donationWebsiteBaseUrl() + "/#donation-shop?item=" + this.firstNonBlank(var1, "").toLowerCase(Locale.ROOT);
   }

   private String donationPaymentUrl(String var1) {
      return this.donationWebsiteBaseUrl() + "/#donation-balance?session=" + this.firstNonBlank(var1, "");
   }

   private String donationQrFallbackMessage() {
      return this.firstNonBlank(
         this.getConfig().getString("donation.qr-fallback-message"),
         "Оплата доступна на сайте CopiMine. Открой ссылку и используй код сессии."
      );
   }

   private List<Long> donationFixedPacks() {
      List<?> raw = getConfig().getList("donation.fixed-packs", List.of(50, 100, 250, 500, 1000));
      ArrayList<Long> packs = new ArrayList<>();

      for (Object var4 : raw) {
         long var5 = this.parseLong(String.valueOf(var4), 0L);
         if (var5 > 0L) {
            packs.add(var5);
         }
      }

      if (packs.isEmpty()) {
         packs.addAll(List.of(50L, 100L, 250L, 500L, 1000L));
      }

      return packs.stream().distinct().sorted().toList();
   }

   private boolean donationTopupEnabled() {
      return this.getConfig().getBoolean("donation.topup-enabled", false);
   }

   private void debugGui(String message) {
      if (this.debugGui) {
         this.getLogger().info("[gui] " + message);
      }
   }

   private static final int FARMER_SWEEP_RADIUS = 2;

   private boolean tryFarmerSweep(Player var1, Block var2, ItemStack var3) {
      if (var2 == null || var2.getType() == Material.AIR) {
         return false;
      }

      // The clicked block may be the farmland below the crop.  Do not require
      // the centre crop to be mature: a player can click the edge of a field
      // or an immature plant while the rest of the 5x5 is ready.  We still
      // reject unrelated blocks, so sneaking with the hoe cannot harvest an
      // arbitrary area of the world.
      Block var4 = this.isSupportedCrop(var2) ? var2 : var2.getRelative(BlockFace.UP);
      if (!this.isSupportedCrop(var4)) {
         return false;
      }

      int harvested = 0;
      for (int var5 = -FARMER_SWEEP_RADIUS; var5 <= FARMER_SWEEP_RADIUS; var5++) {
         for (int var6 = -FARMER_SWEEP_RADIUS; var6 <= FARMER_SWEEP_RADIUS; var6++) {
            if (this.harvestAndReplantCrop(var4.getRelative(var5, 0, var6), var1, var3)) {
               harvested++;
            }
         }
      }

      if (harvested <= 0) {
         return false;
      }
      var1.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, var4.getLocation().add(0.5, 0.8, 0.5), 18, 1.2, 0.2, 1.2, 0.02);
      return true;
   }

   private boolean isSupportedCrop(Block var1) {
      if (var1 == null || !(var1.getBlockData() instanceof Ageable)) {
         return false;
      }
      return switch (var1.getType()) {
         case WHEAT, CARROTS, POTATOES, BEETROOTS, NETHER_WART -> true;
         default -> false;
      };
   }

   private boolean isHarvestableCrop(Block var1) {
      if (var1 != null && var1.getBlockData() instanceof Ageable var2) {
         return var1.getType() != Material.WHEAT
               && var1.getType() != Material.CARROTS
               && var1.getType() != Material.POTATOES
               && var1.getType() != Material.BEETROOTS
               && var1.getType() != Material.NETHER_WART
            ? false
            : var2.getAge() >= var2.getMaximumAge();
      } else {
         return false;
      }
   }

   private boolean harvestAndReplantCrop(Block var1, Player var2, ItemStack var3) {
      if (this.isHarvestableCrop(var1)) {
         BlockBreakEvent var4 = new BlockBreakEvent(var1, var2);
         Bukkit.getPluginManager().callEvent(var4);
         if (var4.isCancelled()) {
            return false;
         }
         ArrayList<ItemStack> var5 = var4.isDropItems() ? new ArrayList<>(var1.getDrops(var3, var2)) : new ArrayList<>();

         Material var6 = switch (var1.getType()) {
            case WHEAT -> Material.WHEAT_SEEDS;
            case BEETROOTS -> Material.BEETROOT_SEEDS;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case NETHER_WART -> Material.NETHER_WART;
            default -> null;
         };
         if (var6 != null) {
            this.removeOneFromDrops(var5, var6);
         }

         var1.setType(var1.getType(), false);
         if (var1.getBlockData() instanceof Ageable var7) {
            var7.setAge(0);
            var1.setBlockData(var7, false);
         }

         World var10 = var1.getWorld();
         Location var11 = var1.getLocation().add(0.5, 0.5, 0.5);

         for (ItemStack var9 : var5) {
            if (var9 != null && var9.getType() != Material.AIR && var9.getAmount() > 0) {
               var10.dropItemNaturally(var11, var9);
            }
         }
         return true;
      }
      return false;
   }

   private void removeOneFromDrops(List<ItemStack> var1, Material var2) {
      for (int var3 = 0; var3 < var1.size(); var3++) {
         ItemStack var4 = (ItemStack)var1.get(var3);
         if (var4 != null && var4.getType() == var2 && var4.getAmount() > 0) {
            if (var4.getAmount() == 1) {
               var1.remove(var3);
            } else {
               var4.setAmount(var4.getAmount() - 1);
            }

            return;
         }
      }
   }

   private void tryForesterChain(Player var1, Block var2, ItemStack var3) {
      if (var2 != null && Tag.LOGS.isTagged(var2.getType())) {
         ArrayDeque<Block> var4 = new ArrayDeque<>();
         HashSet<String> var5 = new HashSet<>();
         ArrayList<Block> var6 = new ArrayList<>();
         var4.add(var2);
         var5.add(this.blockKey(var2.getLocation()));

         while (!var4.isEmpty() && var6.size() < 19) {
            Block var7 = (Block)var4.removeFirst();

            for (int var8 = -1; var8 <= 1; var8++) {
               for (int var9 = 0; var9 <= 1; var9++) {
                  for (int var10 = -1; var10 <= 1; var10++) {
                     if (var8 != 0 || var9 != 0 || var10 != 0) {
                        Block var11 = var7.getRelative(var8, var9, var10);
                        String var12 = this.blockKey(var11.getLocation());
                        if (var6.size() < 19 && var5.add(var12) && Tag.LOGS.isTagged(var11.getType())) {
                           var4.addLast(var11);
                           if (!var11.getLocation().equals(var2.getLocation())) {
                              var6.add(var11);
                           }
                        }
                     }
                  }
               }
            }
         }

         for (Block var14 : var6) {
            String var15 = this.blockKey(var14.getLocation());
            this.chainedTreeBreaks.add(var15);
            if (!var1.breakBlock(var14)) {
               this.chainedTreeBreaks.remove(var15);
            }
         }

         var1.getWorld()
            .spawnParticle(
               Particle.BLOCK, var2.getLocation().add(0.5, 1.0, 0.5), Math.min(24, Math.max(8, var6.size() * 2)), 0.6, 1.0, 0.6, var2.getBlockData()
            );
      }
   }

   private void grantTrenchBonus(Player var1, Block var2) {
      if (var2 != null && (var2.getType() == Material.SAND || var2.getType() == Material.GRAVEL)) {
         Material var3 = switch (this.random.nextInt(3)) {
            case 0 -> Material.DIAMOND;
            case 1 -> Material.EMERALD;
            default -> Material.GOLD_INGOT;
         };
         var2.getWorld().dropItemNaturally(var2.getLocation().add(0.5, 0.5, 0.5), new ItemStack(var3, 1));
         var1.sendMessage(
            this.color(
               "&aЛопата выбила бонусный ресурс: &f"
                  + var3.name()
            )
         );
      }
   }

   private boolean cleanseAllowedDebuff(Player var1) {
      Map<PotionEffectType, PotionEffectType> conversions = new LinkedHashMap<>();
      this.addDebuffConversion(conversions, "POISON", "REGENERATION");
      this.addDebuffConversion(conversions, "WEAKNESS", "STRENGTH");
      this.addDebuffConversion(conversions, "SLOWNESS", "SPEED");
      this.addDebuffConversion(conversions, "GLOWING", "INVISIBILITY");
      this.addDebuffConversion(conversions, "HUNGER", "SATURATION");
      this.addDebuffConversion(conversions, "MINING_FATIGUE", "HASTE");
      this.addDebuffConversion(conversions, "BLINDNESS", "NIGHT_VISION");
      this.addDebuffConversion(conversions, "WITHER", "REGENERATION");
      this.addDebuffConversion(conversions, "LEVITATION", "SLOW_FALLING");
      this.addDebuffConversion(conversions, "DARKNESS", "NIGHT_VISION");
      this.addDebuffConversion(conversions, "NAUSEA", "ABSORPTION");
      this.addDebuffConversion(conversions, "BAD_OMEN", "HERO_OF_THE_VILLAGE");
      this.addDebuffConversion(conversions, "TRIAL_OMEN", "HERO_OF_THE_VILLAGE");
      boolean converted = false;
      for (Map.Entry<PotionEffectType, PotionEffectType> entry : conversions.entrySet()) {
         PotionEffect current = var1.getPotionEffect(entry.getKey());
         if (current == null) {
            continue;
         }
         var1.removePotionEffect(entry.getKey());
         int amplifier = Math.min(1, Math.max(0, current.getAmplifier()));
         var1.addPotionEffect(new PotionEffect(entry.getValue(), Math.max(600, current.getDuration()), amplifier, false, false, true));
         converted = true;
      }
      if (converted) {
         var1.getWorld().spawnParticle(Particle.WAX_OFF, var1.getLocation().add(0.0, 1.0, 0.0), 18, 0.25, 0.4, 0.25, 0.02);
      }
      return converted;
   }

   private void addDebuffConversion(Map<PotionEffectType, PotionEffectType> conversions, String negative, String positive) {
      PotionEffectType from = PotionEffectType.getByName(negative);
      PotionEffectType to = PotionEffectType.getByName(positive);
      if (from != null && to != null) conversions.put(from, to);
   }

   private void activateTaxClock(Player var1, ItemStack var2) {
      String var3 = "";
      if (var2 != null && var2.hasItemMeta()) {
         var3 = this.firstNonBlank(
            var2.getItemMeta().getPersistentDataContainer().get(this.keyUniqueItemId, PersistentDataType.STRING),
            ""
         );
      }
      if (var3.isBlank()) {
         var1.sendMessage(this.color("&cTax clock identity is missing. This item was blocked."));
         return;
      }

      ElectionRuntimeService service = Bukkit.getServicesManager().load(ElectionRuntimeService.class);
      if (service == null) {
         var1.sendMessage(this.color("&cTax service is temporarily unavailable. Try again later."));
         return;
      }
      try {
         CompletableFuture<Map<String, Object>> future = service.grantTaxClockExemption(var1.getUniqueId(), var1.getName(), var3);
         future.whenComplete((var7, var8) -> this.runSync(() -> {
            if (var8 != null || var7 == null) {
               this.getLogger().warning("Tax clock activation failed player=" + var1.getUniqueId());
               var1.sendMessage(this.color("&cTax clock could not be activated. Try again later."));
               return;
            }
            long var10 = this.parseLong(String.valueOf(var7.get("expires_at")), 0L);
            if (var10 <= 0L) {
               this.getLogger().warning("Tax clock returned an invalid expiration player=" + var1.getUniqueId());
               var1.sendMessage(this.color("&cTax clock could not be activated. Try again later."));
               return;
            }
            var1.sendMessage(this.color("&aОсвобождение от налогов активно до: &f" + Instant.ofEpochMilli(var10)));
         }));
      } catch (Exception var12) {
         this.getLogger().warning("Tax clock bridge call failed player=" + var1.getUniqueId());
         var1.sendMessage(this.color("&cTax clock could not be activated. Try again later."));
      }
   }

   private CompletableFuture<Long> grantTaxClockExemptionAsync(UUID playerUuid, String playerName, String instanceId) {
      ElectionRuntimeService service = Bukkit.getServicesManager().load(ElectionRuntimeService.class);
      if (service == null) return CompletableFuture.failedFuture(new IllegalStateException("tax_service_unavailable"));
      return service.grantTaxClockExemption(playerUuid, playerName, instanceId).thenApply(result -> {
         if (result == null) throw new IllegalStateException("tax_clock_result_invalid");
         long expires = this.parseLong(String.valueOf(result.get("expires_at")), 0L);
         if (expires <= 0L) throw new IllegalStateException("tax_clock_expiration_invalid");
         return expires;
      });
   }

   private void breakMinerArea(Player player, Block center, ItemStack tool) {
      if (center == null || tool == null || tool.getType() != Material.NETHERITE_PICKAXE) {
         return;
      }
      double x = Math.abs(player.getLocation().getDirection().getX());
      double y = Math.abs(player.getLocation().getDirection().getY());
      double z = Math.abs(player.getLocation().getDirection().getZ());
      for (int first = -1; first <= 1; first++) {
         for (int second = -1; second <= 1; second++) {
            if (first == 0 && second == 0) {
               continue;
            }
            Block target;
            if (y >= x && y >= z) {
               target = center.getRelative(first, 0, second);
            } else if (x >= z) {
               target = center.getRelative(0, first, second);
            } else {
               target = center.getRelative(first, second, 0);
            }
            if (target.isEmpty() || target.isLiquid() || target.getType().getHardness() < 0.0F) {
               continue;
            }
            String targetKey = this.blockKey(target.getLocation());
            if (this.shopsByLocation.containsKey(targetKey)) {
               continue;
            }
            this.chainedTreeBreaks.add(targetKey);
            if (!player.breakBlock(target)) {
               this.chainedTreeBreaks.remove(targetKey);
            }
         }
      }
      player.getWorld().spawnParticle(Particle.BLOCK, center.getLocation().add(0.5, 0.5, 0.5), 18, 0.65, 0.65, 0.65, center.getBlockData());
   }

   private void createAdminGiftAsync(Player actor, CopiMineArtifacts.SessionState state) {
      if (!this.hasArtifactPermission(actor, "copimine.artifacts.admin.gift") || state.giftTargetUuid == null || state.currentItemId.isBlank()) { this.noPermission(actor); return; }
      if (!state.purchaseInFlightId.isBlank()) { actor.sendMessage(this.color("&eВыдача уже обрабатывается.")); return; }
      String key = "admin_gift:" + actor.getUniqueId() + ":" + state.sessionId + ":" + state.giftTargetUuid + ":" + state.currentItemId;
      state.purchaseInFlightId = key;
      CopiMineArtifacts.CatalogItem item = this.runtimeCatalogItem(state.currentItemId);
      if (item == null) { state.purchaseInFlightId = ""; actor.sendMessage(this.color("&cПредмет больше недоступен.")); return; }
      if ("DONATION".equals(state.giftKind)) {
         DonationPurchaseService service = this.donationPurchaseService();
         if (service == null) { state.purchaseInFlightId = ""; actor.sendMessage(this.color("&cВыдача временно недоступна.")); return; }
         service.createAdminGiftAsync(state.giftTargetUuid, state.giftTargetName, item.itemId(), actor.getName(), key).whenComplete((result, error) -> this.runSync(() -> { state.purchaseInFlightId = ""; if (error != null) actor.sendMessage(this.color("&cНе удалось выдать предмет.")); else { this.giftNotice(state.giftTargetUuid); actor.sendMessage(this.color("&aПодарок поставлен в выдачу.")); this.openAdminShops(actor); } }));
      } else this.runAsync(() -> { boolean created = this.persistAdminGiftDelivery(state.giftTargetUuid, state.giftTargetName, item, actor.getName(), key); this.runSync(() -> { state.purchaseInFlightId = ""; if (created) { this.giftNotice(state.giftTargetUuid); actor.sendMessage(this.color("&aПодарок поставлен в выдачу.")); this.openAdminShops(actor); } else actor.sendMessage(this.color("&cНе удалось выдать предмет.")); }); });
   }

   private void giftNotice(UUID playerUuid) { Player target = Bukkit.getPlayer(playerUuid); if (target != null && target.isOnline()) target.sendMessage(this.color("&6В лавке тебя ждёт подарок от администрации")); }

   /** Preserve the visual/ability marker used by the legacy admin give path
    * when the same item is delivered through the durable pending queue. */
   private ItemStack decorateAdminGrantedItem(ItemStack stack, CopiMineArtifacts.CatalogItem item) {
      if (stack == null || item == null || !"POZDNYAKOV_ACE".equalsIgnoreCase(item.effect())) {
         return stack;
      }
      ItemMeta meta = stack.getItemMeta();
      if (meta != null) {
         meta.addEnchant(Enchantment.THORNS, 3, true);
         meta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);
         stack.setItemMeta(meta);
      }
      return stack;
   }

   private boolean persistAdminGiftDelivery(UUID ownerUuid, String ownerName, CopiMineArtifacts.CatalogItem item, String actor, String idempotencyKey) {
      Connection connection = null;
      try { connection = this.pgPool.acquire(); connection.setAutoCommit(false);
         try (PreparedStatement existing = connection.prepareStatement("SELECT 1 FROM artifact_purchases WHERE idempotency_key=? LIMIT 1")) {
            existing.setString(1, idempotencyKey); try (ResultSet rs = existing.executeQuery()) { if (rs.next()) { connection.rollback(); return true; } }
         }
         String purchaseId = "admin-gift-" + UUID.randomUUID(), uniqueId = UUID.randomUUID().toString(), deliveryId = UUID.randomUUID().toString(); long time = this.now();
         try (PreparedStatement purchase = connection.prepareStatement("INSERT INTO artifact_purchases(purchase_id,unique_item_id,player_uuid,player_name,item_id,shop_id,price_ar,bank_tx_id,idempotency_key,status,delivery_mode,created_at,updated_at) VALUES(?,?,?,?,?,'',0,'ADMIN_GIFT',?,'PENDING_DELIVERY','PENDING',?,?)"); PreparedStatement instance = connection.prepareStatement("INSERT INTO artifact_item_instances(unique_item_id,item_id,owner_uuid,purchase_id,status,repaired_count,created_at,updated_at) VALUES(?,?,?,?, 'PENDING_DELIVERY',0,?,?)"); PreparedStatement delivery = connection.prepareStatement("INSERT INTO artifact_pending_deliveries(delivery_id,purchase_id,unique_item_id,player_uuid,item_id,status,created_at,updated_at) VALUES(?,?,?,?,?,'PENDING',?,?)")) {
            purchase.setString(1,purchaseId); purchase.setString(2,uniqueId); purchase.setString(3,ownerUuid.toString()); purchase.setString(4,ownerName); purchase.setString(5,item.itemId()); purchase.setString(6,idempotencyKey); purchase.setLong(7,time); purchase.setLong(8,time); purchase.executeUpdate();
            instance.setString(1,uniqueId); instance.setString(2,item.itemId()); instance.setString(3,ownerUuid.toString()); instance.setString(4,purchaseId); instance.setLong(5,time); instance.setLong(6,time); instance.executeUpdate();
            delivery.setString(1,deliveryId); delivery.setString(2,purchaseId); delivery.setString(3,uniqueId); delivery.setString(4,ownerUuid.toString()); delivery.setString(5,item.itemId()); delivery.setLong(6,time); delivery.setLong(7,time); delivery.executeUpdate();
         }
         connection.commit(); this.audit(actor, "ADMIN_GIFT", ownerUuid.toString(), "item=" + item.itemId()); return true;
      } catch (Exception error) { this.getLogger().log(Level.WARNING, "Admin AR gift failed", error); if (connection != null) try { connection.rollback(); } catch (SQLException ignored) {} return false; }
      finally { if (connection != null) { try { connection.setAutoCommit(true); } catch (SQLException ignored) {} this.pgPool.release(connection); } }
   }

   private void taxClockLegacyStatus(Player var1) {
      var1.sendMessage(
         this.color(
            "&6Налоговый статус CopiMine"
         )
      );
      var1.sendMessage(
         this.color(
            "&7Полная детализация доступна через сайт и ElectionCore."
         )
      );
      var1.sendMessage(
         this.color(
            "&7Этот артефакт не меняет баланс и не обходит PIN/налоги."
         )
      );
   }

   private boolean legacyPointCompassToLastDeath(Player var1, ItemStack var2) {
      Location var3 = this.lastDeathLocations.get(var1.getUniqueId());
      if (var3 == null) {
         var3 = this.persistedLastDeathLocation(var1);
      }
      if (var3 != null && var3.getWorld() != null) {
         if ((var2 == null ? null : var2.getItemMeta()) instanceof CompassMeta var5) {
            var5.setLodestone(var3);
            var5.setLodestoneTracked(false);
            var2.setItemMeta(var5);
         }

         // Keep the target on this artifact only. Player#setCompassTarget is
         // global for the player and makes every ordinary compass inherit the
         // death location, which turns a vanilla compass into the donation one.
         var1.sendMessage(
            this.color(
               "&aКомпас обновлён на вашу последнюю точку смерти."
            )
         );
         return true;
      } else {
         var1.sendMessage(
            this.color(
               "&eПоследняя точка смерти ещё не записана."
            )
         );
         return false;
      }
   }

   private boolean activateLootCompass(Player player, ItemStack ignored) {
      if (player == null || player.getWorld() == null) {
         return false;
      }
      double maxDistance = compassTeleportDistance(player);
      Location origin = player.getLocation().clone();
      Location eye = player.getEyeLocation();
      Vector direction = eye.getDirection().normalize();
      // Do not let a compass click synchronously load an arbitrary chunk.  A
      // target outside the sent/loaded range is reported as unavailable and
      // leaves the player in place instead of stalling the main tick.
      if (!this.isLoadedCompassChunk(player.getWorld(), eye.getBlockX(), eye.getBlockZ())) {
         return false;
      }
      if (!this.areCompassChunksLoaded(player.getWorld(), eye, direction, maxDistance)) {
         player.sendMessage(this.color("&eВ направлении взгляда есть ещё не загруженные чанки; подойдите ближе и повторите."));
         return false;
      }
      RayTraceResult hit = player.getWorld().rayTraceBlocks(eye, direction, maxDistance, FluidCollisionMode.NEVER, true);
      Location requested = hit == null
         ? eye.clone().add(direction.clone().multiply(maxDistance))
         : hit.getHitPosition().toLocation(player.getWorld()).subtract(direction.clone().multiply(1.75D));
      if (!this.isLoadedCompassChunk(player.getWorld(), requested.getBlockX(), requested.getBlockZ())) {
         player.sendMessage(this.color("&eТочка вне загруженной дальности прорисовки; подойдите ближе и повторите."));
         return false;
      }
      Location safe = this.findSafeCompassLocation(origin, requested, maxDistance);
      if (safe == null) {
         player.sendMessage(this.color("&eНе удалось найти безопасную точку в направлении взгляда."));
         return false;
      }
      if (!player.teleport(safe)) {
         return false;
      }
      player.sendMessage(this.color("&aКомпас телепортации перенёс вас по направлению взгляда. &7Дальность: " + Math.round(maxDistance) + " блоков."));
      return true;
   }

   private double compassTeleportDistance(Player player) {
      if (player == null || player.getWorld() == null) {
         return MAX_COMPASS_TELEPORT_DISTANCE;
      }
       int viewDistanceChunks = player.getViewDistance();
       if (viewDistanceChunks <= 0) {
          viewDistanceChunks = player.getClientViewDistance();
       }
       if (viewDistanceChunks <= 0) {
          viewDistanceChunks = player.getWorld().getViewDistance();
       }
       // Leave a small margin so the destination is inside the server's sent
       // chunk ring rather than on its unloaded edge.
       return Math.max(MAX_COMPASS_TELEPORT_DISTANCE, viewDistanceChunks * 16.0D - 2.0D);
   }

   private boolean isLoadedCompassChunk(World world, int blockX, int blockZ) {
      return world != null && world.isChunkLoaded(blockX >> 4, blockZ >> 4);
   }

   private boolean areCompassChunksLoaded(World world, Location origin, Vector direction, double distance) {
      if (world == null || origin == null || direction == null) {
         return false;
      }
      // Sample every block (plus both endpoints) before invoking Bukkit's
      // ray tracer. This prevents an accidental chunk load in the middle of
      // a long ray when the client has not received that chunk yet.
      for (double travelled = 0.0D; travelled <= distance + 0.5D; travelled += 1.0D) {
         Location point = origin.clone().add(direction.clone().multiply(travelled));
         if (!this.isLoadedCompassChunk(world, point.getBlockX(), point.getBlockZ())) {
            return false;
         }
      }
      return true;
   }

   private Location findSafeCompassLocation(Location origin, Location requested, double maxDistance) {
      if (origin == null || requested == null || origin.getWorld() == null || requested.getWorld() != origin.getWorld()) return null;
      Vector offset = requested.toVector().subtract(origin.toVector());
      if (offset.lengthSquared() > maxDistance * maxDistance) requested = origin.clone().add(offset.normalize().multiply(maxDistance));
      World world = origin.getWorld();
      int baseX = requested.getBlockX();
      int baseZ = requested.getBlockZ();
      double offsetLength = offset.length();
      Vector offsetDirection = offsetLength <= 0.001D ? new Vector(0, 0, 0) : offset.clone().normalize();
      if (!this.isLoadedCompassChunk(world, baseX, baseZ) || !world.getWorldBorder().isInside(requested)
            || offsetLength > 0.001D && !this.areCompassChunksLoaded(world, origin, offsetDirection, Math.min(maxDistance, offsetLength))) {
         return null;
      }
      // A downward ray can hit the side of a cave or a deep underground
      // block.  Never use that Y directly: resolve the target column to its
      // highest safe surface first, then search only around that surface.
      int surfaceY = world.getHighestBlockYAt(baseX, baseZ);
      // In the Nether the highest column is often the bedrock roof.  A
      // surface-derived target there would place the player on/inside the
      // roof (or in an unusable ceiling pocket), so reject the column rather
      // than teleporting through the dimension's normal play area.
      if (world.getEnvironment() == World.Environment.NETHER
            && surfaceY >= world.getMaxHeight() - 3) {
         return null;
      }
      int baseY = Math.max(world.getMinHeight() + 1, surfaceY + 1);
      for (int radius = 0; radius <= 2; radius++) {
         for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
               for (int dz = -radius; dz <= radius; dz++) {
                   Location candidate = new Location(world, baseX + dx + 0.5D, baseY + dy, baseZ + dz + 0.5D);
                   if (candidate.distanceSquared(origin) <= maxDistance * maxDistance
                         && world.getWorldBorder().isInside(candidate)
                         && this.isLoadedCompassChunk(world, candidate.getBlockX(), candidate.getBlockZ())
                         && this.isSafeCompassLocation(candidate)) return candidate;
               }
            }
         }
      }
      return null;
   }

   private boolean isSafeCompassLocation(Location location) {
      World world = location.getWorld();
      if (world == null) return false;
      Block feet = world.getBlockAt(location);
      Block head = feet.getRelative(BlockFace.UP);
      Block floor = feet.getRelative(BlockFace.DOWN);
      if (!world.getWorldBorder().isInside(location)
            || !feet.isPassable() || !head.isPassable() || !floor.getType().isSolid()
            || feet.isLiquid() || head.isLiquid() || floor.isLiquid()) {
         return false;
      }
      if (this.isCompassHazard(feet.getType()) || this.isCompassHazard(head.getType())
            || this.isCompassHazard(floor.getType())) {
         return false;
      }
      // Keep a small horizontal safety margin around cactus/fire/magma and
      // other contact hazards; a player spawned on the edge of one would be
      // damaged immediately even though the feet/head/floor blocks are air.
      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            if ((dx == 0 && dz == 0)) continue;
            Material nearbyFeet = world.getBlockAt(feet.getX() + dx, feet.getY(), feet.getZ() + dz).getType();
            Material nearbyFloor = world.getBlockAt(floor.getX() + dx, floor.getY(), floor.getZ() + dz).getType();
            if (this.isCompassHazard(nearbyFeet) || this.isCompassHazard(nearbyFloor)) {
               return false;
            }
         }
      }
      // Do not materialize a player inside an entity or a non-passable
      // protection/physics hitbox at the destination.
      return world.getNearbyEntities(location, 0.31D, 0.9D, 0.31D).stream()
            .noneMatch(entity -> entity instanceof LivingEntity && !entity.isDead());
   }

   private boolean isCompassHazard(Material material) {
      if (material == null) {
         return true;
      }
      return switch (material) {
         case FIRE, SOUL_FIRE, CAMPFIRE, SOUL_CAMPFIRE, MAGMA_BLOCK, CACTUS,
               LAVA, WATER, POWDER_SNOW, SWEET_BERRY_BUSH, WITHER_ROSE,
               END_PORTAL, NETHER_PORTAL, VOID_AIR -> true;
         default -> false;
      };
   }

   private Location persistedLastDeathLocation(Player player) {
      PersistentDataContainer pdc = player.getPersistentDataContainer();
      String worldName = pdc.get(this.keyLastDeathWorld, PersistentDataType.STRING);
      Integer x = pdc.get(this.keyLastDeathX, PersistentDataType.INTEGER);
      Integer y = pdc.get(this.keyLastDeathY, PersistentDataType.INTEGER);
      Integer z = pdc.get(this.keyLastDeathZ, PersistentDataType.INTEGER);
      if (worldName == null || worldName.isBlank() || x == null || y == null || z == null) {
         return null;
      }
      World world = Bukkit.getWorld(worldName);
      return world == null ? null : new Location(world, x + 0.5D, y, z + 0.5D);
   }

   private boolean triggerEternalBoost(Player var1) {
      if (!var1.isGliding()) {
         var1.sendMessage(
            this.color(
               "&eЭтот фейерверк работает только во время полёта на элитрах."
            )
         );
         return false;
      } else {
         var1.setVelocity(var1.getLocation().getDirection().normalize().multiply(1.35).add(var1.getVelocity().multiply(0.35)));
         var1.getWorld().spawnParticle(Particle.FIREWORK, var1.getLocation().add(0.0, 0.5, 0.0), 12, 0.25, 0.25, 0.25, 0.02);
         return true;
      }
   }

   /**
    * Trap the victim in a reversible 3x3x3 cage.  A cobweb-only trap was
    * rejected by some protection plugins and made the pickaxe look inert, so
    * the pickaxe now uses a visible sand shell while retaining the short
    * crowd-control effects even when a block placement is denied.
    */
   private void applyTemporaryBurial(Player attacker, LivingEntity target, long durationTicks) {
      if (attacker == null || target == null || target.getWorld() == null) return;
      target.setFreezeTicks(Math.max(target.getFreezeTicks(), Math.min(140, (int) durationTicks)));
      Block origin = target.getLocation().getBlock();
      List<Block> placed = new ArrayList<>();
      for (int y = 0; y <= 2; y++) {
         for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
               boolean shell = Math.abs(dx) == 1 || Math.abs(dz) == 1 || y == 2;
               if (!shell) continue;
               Block block = origin.getRelative(dx, y, dz);
               if (block.getType() != Material.AIR || block.isLiquid()
                     || this.shopsByLocation.containsKey(this.blockKey(block.getLocation()))) continue;
               Material cageMaterial = y == 2 ? Material.SAND : Material.COBWEB;
               BlockPlaceEvent place = new BlockPlaceEvent(
                     block, block.getState(), block.getRelative(BlockFace.DOWN),
                     new ItemStack(cageMaterial), attacker, true);
               Bukkit.getPluginManager().callEvent(place);
               if (place.isCancelled() || !place.canBuild()) continue;
               String originalBlockData = block.getBlockData().getAsString();
               block.setType(cageMaterial, false);
               this.recordTemporaryTrap(block, cageMaterial, originalBlockData,
                     System.currentTimeMillis() + Math.max(1L, durationTicks) * 50L);
               placed.add(block);
            }
         }
      }
      if (!placed.isEmpty()) {
         Bukkit.getScheduler().runTaskLater(this, () -> {
            for (Block block : placed) {
               TemporaryBlockRestore restore = this.temporaryTrapRestores.get(this.blockKey(block.getLocation()));
               if (restore != null) this.restoreTemporaryTrap(restore);
               else if (block.getType() == Material.COBWEB || block.getType() == Material.SAND) block.setType(Material.AIR, false);
            }
            this.rewriteTemporaryTrapJournal();
         }, Math.max(1L, durationTicks));
      }
   }

   private void applyTemporaryCobwebSnare(Player var1, LivingEntity var2, long durationTicks) {
      if (var1 == null || var2 == null || var2.getWorld() == null) {
         return;
      }
      Block origin = var2.getLocation().getBlock();
      List<Block> placedBlocks = new ArrayList<>();
      // A single cobweb at the feet is easy to walk out of and was the reason
      // the donation pickaxe looked like it did nothing.  Build a reversible
      // two-block-high 3x3 web cage instead.  Only empty, unprotected blocks
      // are touched, and every block is remembered for precise cleanup.
      for (int y = 0; y <= 1; y++) {
         for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
               Block block = origin.getRelative(dx, y, dz);
               if (block.getType() != Material.AIR || block.isLiquid() || this.shopsByLocation.containsKey(this.blockKey(block.getLocation()))) {
                  continue;
               }
               BlockPlaceEvent place = new BlockPlaceEvent(
                  block,
                  block.getState(),
                  block.getRelative(BlockFace.DOWN),
                  new ItemStack(Material.COBWEB),
                  var1,
                  true
               );
               Bukkit.getPluginManager().callEvent(place);
               if (place.isCancelled() || !place.canBuild()) {
                  continue;
               }
               String originalBlockData = block.getBlockData().getAsString();
               block.setType(Material.COBWEB, false);
               this.recordTemporaryTrap(block, Material.COBWEB, originalBlockData,
                     System.currentTimeMillis() + Math.max(1L, durationTicks) * 50L);
               placedBlocks.add(block);
            }
         }
      }
      if (!placedBlocks.isEmpty()) {
         Bukkit.getScheduler().runTaskLater(this, () -> {
            for (Block block : placedBlocks) {
               TemporaryBlockRestore restore = this.temporaryTrapRestores.get(this.blockKey(block.getLocation()));
               if (restore != null) this.restoreTemporaryTrap(restore);
               else if (block.getType() == Material.COBWEB) block.setType(Material.AIR, false);
            }
            this.rewriteTemporaryTrapJournal();
         }, Math.max(1L, durationTicks));
      }
   }

   private void healPlayerCapped(Player var1, double var2) {
      if (var1 != null && !(var2 <= 0.0)) {
         var1.setHealth(Math.min(var1.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue(), var1.getHealth() + var2));
      }
   }

   private void tryRareArTheft(Player attacker, LivingEntity target) {
      if (!(target instanceof Player victim) || attacker == null || this.bridge == null || random.nextDouble() >= AR_THEFT_PROC_CHANCE) return;
      String idempotencyKey = "kosa-ar-steal-" + UUID.randomUUID();
      this.runAsync(() -> {
         CopiMineArtifacts.BridgeTxnResult account = this.bridge.stealFromPlayerAccount(
            victim.getUniqueId(), victim.getName(), attacker.getUniqueId(), attacker.getName(), 1L,
            idempotencyKey, "AR_STEAL", "Kosa rare 0.1% proc"
         );
         if (account.ok()) {
            this.runSync(() -> this.sendArTheftMessages(attacker, victim, false));
            return;
         }
         String accountCode = account.code() == null ? "" : account.code().trim().toUpperCase(Locale.ROOT);
         if (!(accountCode.isBlank() || Set.of("NO_BANK_ACCOUNT", "ACCOUNT_NOT_FOUND", "ACCOUNT_MISSING", "NO_ACCOUNT", "INSUFFICIENT_AR").contains(accountCode))) return;
         // Physical AR is deliberately not a fallback.  Inventory removal,
         // bank credit and compensation cannot be made one atomic operation
         // across the EconomyCore bridge.  Refusing the steal is safer than
         // losing a certified asset or spawning an unregistered duplicate.
         this.runSync(() -> this.sendArTheftFailureMessages(attacker, victim));
      });
   }

   private void sendArTheftMessages(Player attacker, Player victim, boolean fromInventory) {
      String source = fromInventory ? "из инвентаря" : "со счёта";
      if (attacker.isOnline()) {
         attacker.sendMessage(this.color("&aВы украли 1 AR у &f" + victim.getName() + " &a" + source + "."));
      }
      if (victim.isOnline()) {
         victim.sendMessage(this.color("&cУ вас украли 1 AR. Нападающий: &f" + attacker.getName() + "&c."));
      }
   }

   private void sendArTheftFailureMessages(Player attacker, Player victim) {
      if (attacker != null && attacker.isOnline()) {
         attacker.sendMessage(this.color("&cНеудачная кража: у игрока &f" + victim.getName() + " &cнет AR."));
      }
      if (victim != null && victim.isOnline()) {
         victim.sendMessage(this.color("&eВы слишком бедны: с вас нечего брать."));
      }
   }

   private void setAction(Inventory var1, CopiMineArtifacts.SessionState var2, int var3, ItemStack var4, String var5) {
      var1.setItem(var3, var4);
      var2.actions.put(var3, var5);
   }

   private Set<String> artifactCombatEffects() {
      return Set.of(
         "LIGHTNING",
         "DRAGON_PUNISHMENT",
         "WATCH_GLOW",
         "DEBT_SNARE",
         "SMUGGLER_MARK",
         "ZMEI_GORYNYCH_POOP",
         "BATIN_REMEN",
         "NAKOPAL_PICKAXE",
         "NALOGOVAYA_KOSA",
         "DUTY_ARGUMENT"
      );
   }

   private Set<String> artifactToolEffects() {
      return Set.of("HASTE_BURST", "MINER_PULSE", "MINER_3X3", "FORESTER_FOCUS", "SURVEYOR_TOUCH", "CRAFTSMAN_CHECK", "FORESTER_CHAIN", "TRENCH_BONUS");
   }

   private Set<String> artifactInteractEffects() {
      return Set.of("HASTE_BURST_LONG", "WIND_HAMMER", "FARMER_SWEEP", "DEBUFF_AMULET", "TAX_CLOCK", "LOOT_COMPASS", "ETERNAL_BOOST");
   }

   private Set<String> artifactDefenseEffects() {
      return Set.of("PRORAB_HELMET", "TANK_VEST", "NOT_TODAY_SHIELD", "POZDNYAKOV_ACE");
   }

   private String categoryTitle(CopiMineArtifacts.Category var1) {
      return switch (var1) {
         case WEAPON -> "Боевые артефакты";
         case ARMOR -> "Защитные артефакты";
         case TOOL -> "Рабочие артефакты";
         case RP -> "RP-предметы";
      };
   }

   private String categoryHint(CopiMineArtifacts.Category var1) {
      return switch (var1) {
         case WEAPON -> "&7Оружие с короткими контролируемыми эффектами.";
         case ARMOR -> "&7Броня и экипировка для выживания.";
         case TOOL -> "&7Инструменты без vein-miner и массовых сканов.";
         case RP -> "&7Обычная RP-вкладка без активных товаров.";
      };
   }

   private Material categoryMaterial(CopiMineArtifacts.Category var1) {
      return switch (var1) {
         case WEAPON -> Material.NETHERITE_SWORD;
         case ARMOR -> Material.DIAMOND_CHESTPLATE;
         case TOOL -> Material.DIAMOND_PICKAXE;
         case RP -> Material.NAME_TAG;
      };
   }

   private ItemStack categoryIcon(CopiMineArtifacts.Category var1) {
      return switch (var1) {
         case WEAPON -> this.button(
         Material.NETHERITE_SWORD,
         "&bБоевые артефакты",
         List.of(
            "&7Клинки, топоры и луки.",
            "&8Короткие эффекты, общая перезарядка."
         )
      );
         case ARMOR -> this.button(
         Material.DIAMOND_CHESTPLATE,
         "&aЗащитные артефакты",
         List.of(
            "&7Экипировка для шахты, патруля и доставки."
         )
      );
         case TOOL -> this.button(
         Material.DIAMOND_PICKAXE,
         "&eРабочие артефакты",
         List.of(
            "&7Кирки, топоры, лопаты и ремесло.",
            "&8Без массовых проверок мира."
         )
      );
         case RP -> this.button(
         Material.PAPER,
         "&eПока пусто",
         List.of(
            "&7Сейчас в этой категории нет доступных товаров."
         )
      );
      };
   }

   private ItemStack previewIcon(CopiMineArtifacts.CatalogItem var1) {
      ArrayList var2 = new ArrayList();
      var2.add(this.color("&7Цена: &f" + var1.priceAr() + " AR"));
      var2.add(this.color("&7Редкость: &f" + var1.rarity()));
      if (var1.cooldownSeconds() > 0) {
         var2.add(
            this.color(
               "&7Перезарядка: &f"
                  + var1.cooldownSeconds()
                  + " сек."
            )
         );
      }

      if (var1.effectChancePercent() > 0 && var1.effectChancePercent() < 100) {
         var2.add(
            this.color(
               "&7Шанс эффекта: &f"
                  + var1.effectChancePercent()
                  + "%"
            )
         );
      }

      var2.addAll(var1.lore().stream().map(this::color).toList());
      ItemStack var3 = this.button(var1.material(), var1.name(), var2);
      ItemMeta var4 = var3.getItemMeta();
      if (var4 != null && var1.customModelData() > 0 && !"NOT_TODAY_SHIELD".equalsIgnoreCase(var1.effect())) {
         // meta.setCustomModelData(item.customModelData())
         var4.setCustomModelData(var1.customModelData());
         var3.setItemMeta(var4);
      }

      return var3;
   }

   private ItemStack soonIcon(String var1, String var2) {
      return this.button(
         Material.CLOCK,
         var1,
         List.of(
            var2,
            "&7Сейчас эта витрина пустая."
         )
      );
   }

   private ItemStack button(Material var1, String var2, List<String> var3) {
      ItemStack var4 = new ItemStack(var1);
      ItemMeta var5 = var4.getItemMeta();
      if (var5 == null) {
         return var4;
      } else {
         var5.setDisplayName(this.color(var2));
         var5.setLore(var3.stream().map(this::color).toList());
         var5.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS});
         var4.setItemMeta(var5);
         return var4;
      }
   }

   private boolean customBlockVisualsEnabled() {
      return this.getConfig().getBoolean("custom-block-visuals.enabled", true);
   }

   private double customBlockScale(String var1) {
      return this.getConfig().getDouble("custom-block-visuals.models." + var1 + ".scale", 1.01);
   }

   private double customBlockOffsetY(String var1) {
      return this.getConfig().getDouble("custom-block-visuals.models." + var1 + ".offset-y", 0.5);
   }

   private String first(String var1, String var2) {
      return var1 != null && !var1.isBlank() ? var1 : var2;
   }

   private String shortText(String text, int maximumLength) {
      String value = this.first(text, "").trim();
      int limit = Math.max(1, maximumLength);
      return value.length() <= limit ? value : value.substring(0, Math.max(1, limit - 1)) + "…";
   }

   private void spawnOrReplaceProtectedBlockVisual(Location var1, String var2, String var3, Material var4, int var5, String var6) throws Exception {
      if (this.customBlockVisualsEnabled() && var1 != null && var1.getWorld() != null && var3 != null && !var3.isBlank()) {
         this.runAsync(() -> {
            try {
               Map var7 = this.fetchProtectedBlockVisualRow(var2, var3);
               this.runSync(() -> {
                  try {
                     this.cleanupProtectedBlockVisualEntities(var1, var2, var3, var7);
                     Location var8x = var1.clone().add(0.5, this.customBlockOffsetY(var2), 0.5);
                     ItemStack var9 = new ItemStack(var4);
                     ItemMeta var10 = var9.getItemMeta();
                     if (var10 != null) {
                        var10.setDisplayName(this.color("&f" + var6));
                        var10.setCustomModelData(var5);
                        var10.addItemFlags(ItemFlag.values());
                        var9.setItemMeta(var10);
                     }

                     float var11 = (float)this.customBlockScale(var2);
                     ItemDisplay var12 = (ItemDisplay)var1.getWorld().spawn(var8x, ItemDisplay.class, var6xxx -> {
                        var6xxx.setItemStack(var9);
                        var6xxx.setItemDisplayTransform(ItemDisplayTransform.FIXED);
                        var6xxx.setPersistent(true);
                        var6xxx.setGravity(false);
                        var6xxx.setInvulnerable(true);
                        var6xxx.setBillboard(Billboard.FIXED);
                        var6xxx.getPersistentDataContainer().set(this.visualEntityTypeKey, PersistentDataType.STRING, "PROTECTED_BLOCK_VISUAL");
                        var6xxx.getPersistentDataContainer().set(this.visualKindKey, PersistentDataType.STRING, var2);
                        var6xxx.getPersistentDataContainer().set(this.visualLinkedIdKey, PersistentDataType.STRING, var3);
                        var6xxx.getPersistentDataContainer().set(this.visualModelIdKey, PersistentDataType.STRING, var6);
                        var6xxx.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(var11, var11, var11), new AxisAngle4f()));
                     });
                     if ("ARTIFACT_SHOP".equals(var2)) {
                        CopiMineArtifacts.Shop shop = this.shopsByLocation.values().stream().filter(candidate -> candidate.shopId().equalsIgnoreCase(var3)).findFirst().orElse(null);
                        this.spawnShopTitleDisplay(var1, var3, shop == null ? var3 : shop.title());
                     }
                     this.saveProtectedBlockVisualAsync(var1, var2, var3, var4, var5, var6, var12.getUniqueId().toString());
                  } catch (Exception var13) {
                     this.getLogger().log(Level.WARNING, "Artifact shop visual spawn failed", (Throwable)var13);
                  }
               });
            } catch (Exception var8) {
               this.getLogger().log(Level.WARNING, "Artifact shop visual fetch failed", (Throwable)var8);
            }
         });
      }
   }

   private void cleanupProtectedBlockVisuals(String var1, String var2) throws Exception {
      if (var2 != null && !var2.isBlank()) {
         this.runAsync(() -> {
            try {
               Map var3 = this.fetchProtectedBlockVisualRow(var1, var2);
               Location var4 = this.visualBaseLocation(var3);
               this.runSync(() -> {
                  this.cleanupProtectedBlockVisualEntities(var4, var1, var2, var3);
                  if ("ARTIFACT_SHOP".equals(var1)) {
                     this.cleanupShopTitleDisplay(var4, var2);
                  }
               });
               this.markProtectedBlockVisualInactive(var1, var2);
            } catch (Exception var5) {
               this.getLogger().log(Level.WARNING, "Artifact shop visual cleanup failed", (Throwable)var5);
            }
         });
      }
   }

   private void repairProtectedBlockVisuals() throws Exception {
      if (!this.customBlockVisualsEnabled()) {
         return;
      }

      List<Chunk> loadedChunks = new ArrayList<>();
      for (World world : Bukkit.getWorlds()) {
         loadedChunks.addAll(List.of(world.getLoadedChunks()));
      }

      for (Chunk chunk : loadedChunks) {
         this.enqueueProtectedBlockVisualRepair(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
      }
   }

   private void repairShopTitleDisplays() {
      for (World world : Bukkit.getWorlds()) {
         for (Chunk chunk : world.getLoadedChunks()) {
            this.repairShopTitleDisplays(world.getName(), chunk.getX(), chunk.getZ());
         }
      }
   }

   private void repairShopTitleDisplays(String worldName, int chunkX, int chunkZ) {
      World world = Bukkit.getWorld(worldName);
      if (world == null || !world.isChunkLoaded(chunkX, chunkZ)) {
         return;
      }
      for (CopiMineArtifacts.Shop shop : this.shopsByLocation.values()) {
         if (!worldName.equals(shop.world()) || (shop.x() >> 4) != chunkX || (shop.z() >> 4) != chunkZ) {
            continue;
         }
         Location location = this.shopLocation(shop);
         this.spawnShopTitleDisplay(location, shop.shopId(), shop.title());
      }
   }

   private void repairProtectedBlockVisuals(String var1, int var2, int var3) throws Exception {
      if (this.customBlockVisualsEnabled()) {
         this.enqueueProtectedBlockVisualRepair(var1, var2, var3);
      }
   }

   private void enqueueProtectedBlockVisualRepair(String var1, int var2, int var3) {
      if (this.customBlockVisualsEnabled() && var1 != null && !var1.isBlank()) {
         String var4 = this.visualRepairChunkKey(var1, var2, var3);
         if (this.pendingVisualRepairChunks.add(var4)) {
            this.visualRepairQueue.offer(var4);
            this.drainProtectedBlockVisualRepairs();
         }
      }
   }

   private void drainProtectedBlockVisualRepairs() {
      if (this.customBlockVisualsEnabled() && this.visualRepairDrainRunning.compareAndSet(false, true)) {
         this.runAsync(() -> {
            try {
               int var1 = 0;

               while (var1 < VISUAL_REPAIR_BATCH_SIZE) {
                  String var2 = this.visualRepairQueue.poll();
                  if (var2 == null) {
                     break;
                  }

                  this.pendingVisualRepairChunks.remove(var2);
                  String[] var3 = var2.split(":");
                  if (var3.length == 3) {
                     try {
                        this.repairProtectedBlockVisualChunk(var3[0], Integer.parseInt(var3[1]), Integer.parseInt(var3[2]));
                     } catch (Exception var5) {
                        this.getLogger().log(Level.WARNING, "Artifact visual repair queue failed for " + var2, (Throwable)var5);
                     }
                  }

                  ++var1;
               }
            } finally {
               this.visualRepairDrainRunning.set(false);
               if (!this.visualRepairQueue.isEmpty()) {
                  this.drainProtectedBlockVisualRepairs();
               }
            }
         });
      }
   }

   private String visualRepairChunkKey(String var1, int var2, int var3) {
      return var1 + ":" + var2 + ":" + var3;
   }

   private void repairProtectedBlockVisualChunk(String var1, int var2, int var3) throws Exception {
      List<CopiMineArtifacts.Shop> var4 = this.shopsByLocation
         .values()
         .stream()
         .filter(var3xx -> var3xx.world().equals(var1) && var3xx.x() >> 4 == var2 && var3xx.z() >> 4 == var3 && var3xx.enabled())
         .toList();
      if (var4.isEmpty()) {
         return;
      }

      Connection var5 = this.pgPool.acquire();

      try (PreparedStatement var6 = var5.prepareStatement(
            "SELECT linked_id,entity_uuid,model_id,custom_model_data FROM protected_block_visuals WHERE kind='ARTIFACT_SHOP' AND active=1 AND linked_id = ANY (?)"
         )) {
         Array var7 = var5.createArrayOf("text", var4.stream().map(CopiMineArtifacts.Shop::shopId).toArray(String[]::new));
         var6.setArray(1, var7);
         HashMap<String, Map<String, Object>> var8 = new HashMap<>();

         try (ResultSet var9 = var6.executeQuery()) {
            while (var9.next()) {
               HashMap<String, Object> var10 = new HashMap<>();
               var10.put("entity_uuid", var9.getString("entity_uuid"));
               var10.put("model_id", var9.getString("model_id"));
               var10.put("custom_model_data", var9.getInt("custom_model_data"));
               var8.put(var9.getString("linked_id"), var10);
            }
         }

         this.runSync(() -> {
            try {
               this.applyProtectedBlockVisualRepairs(var1, var2, var3, var4, var8);
            } catch (Exception var7x) {
               this.getLogger().log(Level.WARNING, "Artifact shop visual apply failed", (Throwable)var7x);
            }
         });
      } finally {
         this.pgPool.release(var5);
      }
   }

   private void applyProtectedBlockVisualRepairs(String var1, int var2, int var3, List<CopiMineArtifacts.Shop> var4, Map<String, Map<String, Object>> var5) throws Exception {
      World var6 = Bukkit.getWorld(var1);
      if (var6 != null && var6.isChunkLoaded(var2, var3)) {
         for (CopiMineArtifacts.Shop var8 : var4) {
            // repairProtectedBlockVisual(shop, visuals.getOrDefault(shop.shopId(), Map.of()))
            this.repairProtectedBlockVisual(var8, var5.getOrDefault(var8.shopId(), Map.of()));
         }
      }
   }

   private void repairProtectedBlockVisual(CopiMineArtifacts.Shop var1, Map<String, Object> var2) throws Exception {
      World var3 = Bukkit.getWorld(var1.world());
      if (var3 != null) {
         Location var4 = new Location(var3, (double)var1.x(), (double)var1.y(), (double)var1.z());
         // The database row is not the block.  Never respawn a marker over an
         // anchor that was removed by WorldEdit, a plugin or an admin.  The
         // periodic guard will mark the shop disabled and re-enable it only
         // after a real block is placed at the same location.
         if (var3.isChunkLoaded(var1.x() >> 4, var1.z() >> 4)
               && var3.getBlockAt(var1.x(), var1.y(), var1.z()).getType().isAir()) {
            this.cleanupNearbyProtectedVisuals(var4, "ARTIFACT_SHOP", var1.shopId());
            this.cleanupShopTitleDisplay(var4, var1.shopId());
            return;
         }
         String var5 = this.first(String.valueOf(var2.getOrDefault("entity_uuid", "")), "");
         Entity var6 = null;
         if (!var5.isBlank()) {
            try {
               var6 = Bukkit.getEntity(UUID.fromString(var5));
            } catch (Exception var8) {
            }
         }

         boolean var7 = this.isOwnedProtectedVisualEntity(var6, "ARTIFACT_SHOP", var1.shopId(), "artifact_shop_marker", 14004);
         this.cleanupNearbyProtectedVisualDuplicates(var4, "ARTIFACT_SHOP", var1.shopId(), var7 ? var6.getUniqueId().toString() : "");
         if (!var7) {
            this.spawnOrReplaceProtectedBlockVisual(var4, "ARTIFACT_SHOP", var1.shopId(), Material.PAPER, 14004, "artifact_shop_marker");
         }
         this.spawnShopTitleDisplay(var4, var1.shopId(), var1.title());
      }
   }

   private void setShopEnabled(String shopId, boolean enabled) throws SQLException {
      setShopState(shopId, enabled, "");
   }

   private void setShopState(String shopId, boolean enabled, String anchorMaterial) throws SQLException {
      Connection connection = this.pgPool.acquire();
      try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE artifact_shops SET enabled=?,anchor_material=CASE WHEN ?='' THEN anchor_material ELSE ? END,updated_at=? WHERE shop_id=?")) {
         statement.setBoolean(1, enabled);
         String normalizedMaterial = this.firstNonBlank(anchorMaterial, "");
         statement.setString(2, normalizedMaterial);
         statement.setString(3, normalizedMaterial);
         statement.setLong(4, this.now());
         statement.setString(5, shopId);
         if (statement.executeUpdate() != 1) {
            throw new SQLException("Shop row is missing: " + shopId);
         }
      } finally {
         this.pgPool.release(connection);
      }
   }

   /**
    * Reconciles the durable shop registry with loaded world blocks.  Visual
    * entities are presentation only; a missing anchor must make the shop
    * unavailable, while placing a replacement block must make it usable again
    * without requiring a restart or a second database row.
    */
   private void guardShopAnchors() {
      for (CopiMineArtifacts.Shop shop : this.shopsByLocation.values()) {
         World world = Bukkit.getWorld(shop.world());
         if (world == null || !world.isChunkLoaded(shop.x() >> 4, shop.z() >> 4)) {
            continue;
         }
         Material actualType = world.getBlockAt(shop.x(), shop.y(), shop.z()).getType();
         String actualMaterial = actualType.name();
         String storedMaterial = this.firstNonBlank(shop.anchorMaterial(), "");
         String expectedMaterial = storedMaterial.isBlank() && !actualType.isAir() ? actualMaterial : storedMaterial;
         boolean present = !actualType.isAir() && !expectedMaterial.isBlank()
               && expectedMaterial.equals(actualMaterial);
         if (present == shop.enabled() && !storedMaterial.isBlank()) {
            continue;
         }
         CopiMineArtifacts.Shop updated = new CopiMineArtifacts.Shop(
               shop.shopId(), shop.title(), shop.world(), shop.x(), shop.y(), shop.z(), present, expectedMaterial
         );
         if (!this.shopsByLocation.replace(shop.locationKey(), shop, updated)) {
            continue;
         }
         if (present) {
            this.enqueueProtectedBlockVisualRepair(shop.world(), shop.x() >> 4, shop.z() >> 4);
            this.spawnShopTitleDisplay(this.shopLocation(updated), updated.shopId(), updated.title());
         } else {
            try {
               this.cleanupProtectedBlockVisuals("ARTIFACT_SHOP", shop.shopId());
            } catch (Exception error) {
               this.getLogger().log(Level.WARNING, "Artifact shop visual cleanup after anchor removal failed", error);
            }
            this.cleanupShopTitleDisplay(this.shopLocation(updated), updated.shopId());
         }
         this.runAsync(() -> {
            try {
               this.setShopState(shop.shopId(), present, expectedMaterial);
            } catch (SQLException error) {
               this.getLogger().log(Level.WARNING, "Artifact shop anchor state persistence failed for " + shop.shopId(), error);
               this.runSync(() -> this.shopsByLocation.replace(shop.locationKey(), updated, shop));
            }
         });
      }
   }

   private void cleanupNearbyProtectedVisuals(Location var1, String var2, String var3) {
      if (var1 != null && var1.getWorld() != null) {
         for (Entity var5 : var1.getWorld().getNearbyEntities(var1.clone().add(0.5, this.customBlockOffsetY(var2), 0.5), 0.9, 0.9, 0.9)) {
            this.removeOwnedProtectedVisualEntity(var5, var2, var3);
         }
      }
   }

   private void cleanupNearbyProtectedVisualDuplicates(Location var1, String var2, String var3, String var4) {
      if (var1 != null && var1.getWorld() != null) {
         for (Entity var6 : var1.getWorld().getNearbyEntities(var1.clone().add(0.5, this.customBlockOffsetY(var2), 0.5), 0.9, 0.9, 0.9)) {
            if (this.isOwnedProtectedVisualEntity(var6, var2, var3, "artifact_shop_marker", 14004)
               && (var4.isBlank() || !var4.equals(var6.getUniqueId().toString()))) {
               var6.remove();
            }
         }
      }
   }

   private void removeOwnedProtectedVisualEntity(Entity var1, String var2, String var3) {
      if (this.isOwnedProtectedVisualEntity(var1, var2, var3, "artifact_shop_marker", 14004)) {
         var1.remove();
      }
   }

   private boolean isOwnedProtectedVisualEntity(Entity var1, String var2, String var3, String var4, int var5) {
      if (!(var1 instanceof ItemDisplay var6)) {
         return false;
      } else {
         PersistentDataContainer var7 = var6.getPersistentDataContainer();
          if (!"PROTECTED_BLOCK_VISUAL".equals(this.readVisualString(var7, this.visualEntityTypeKey, "visual_entity_type"))) {
             return this.isLegacyProtectedVisualModel(var6, var2, var4, var5);
          } else if (!var2.equals(this.readVisualString(var7, this.visualKindKey, "visual_kind"))) {
             return false;
          } else if (!var3.equals(this.readVisualString(var7, this.visualLinkedIdKey, "visual_linked_id"))) {
             return false;
          } else if (!var4.equals(this.readVisualString(var7, this.visualModelIdKey, "visual_model_id"))) {
             return false;
         } else {
            ItemStack var8 = var6.getItemStack();
            if (var8 != null && var8.getType() == Material.PAPER) {
               ItemMeta var9 = var8.getItemMeta();
               return var9 != null && var9.hasCustomModelData() && var9.getCustomModelData() == var5;
            } else {
               return false;
            }
         }
      }
   }

   private Map<String, Object> fetchProtectedBlockVisualRow(String var1, String var2) throws Exception {
      Connection var3 = this.pgPool.acquire();

      HashMap var7;
      try (PreparedStatement var4 = var3.prepareStatement(
            "SELECT entity_uuid,world,x,y,z FROM protected_block_visuals WHERE kind=? AND linked_id=? AND active=1 ORDER BY updated_at DESC LIMIT 1"
         )) {
         var4.setString(1, var1);
         var4.setString(2, var2);

         try (ResultSet var5 = var4.executeQuery()) {
            if (!var5.next()) {
               return Map.of();
            }

            HashMap var19 = new HashMap();
            var19.put("entity_uuid", var5.getString("entity_uuid"));
            var19.put("world", var5.getString("world"));
            var19.put("x", var5.getInt("x"));
            var19.put("y", var5.getInt("y"));
            var19.put("z", var5.getInt("z"));
            var7 = var19;
         }
      } finally {
         this.pgPool.release(var3);
      }

      return var7;
   }

   private Location visualBaseLocation(Map<String, Object> var1) {
      if (var1 != null && !var1.isEmpty()) {
         World var2 = Bukkit.getWorld(this.first(String.valueOf(var1.getOrDefault("world", "")), ""));
         return var2 == null
            ? null
            : new Location(
               var2,
               (double)this.parseInt(String.valueOf(var1.get("x")), 0),
               (double)this.parseInt(String.valueOf(var1.get("y")), 0),
               (double)this.parseInt(String.valueOf(var1.get("z")), 0)
            );
      } else {
         return null;
      }
   }

   private void cleanupProtectedBlockVisualEntities(Location var1, String var2, String var3, Map<String, Object> var4) {
      if (var4 != null && !var4.isEmpty()) {
         String var5 = this.first(String.valueOf(var4.getOrDefault("entity_uuid", "")), "");
         if (!var5.isBlank()) {
            try {
               this.removeOwnedProtectedVisualEntity(Bukkit.getEntity(UUID.fromString(var5)), var2, var3);
            } catch (Exception var7) {
            }
         }
      }

      if (var1 != null) {
         this.cleanupNearbyProtectedVisuals(var1, var2, var3);
      }
   }

   private void markProtectedBlockVisualInactive(String var1, String var2) {
      this.runAsync(
         () -> {
            try {
               Connection var3 = this.pgPool.acquire();

               try (PreparedStatement var4 = var3.prepareStatement(
                     "UPDATE protected_block_visuals SET active=0,updated_at=? WHERE kind=? AND linked_id=? AND active=1"
                  )) {
                  var4.setLong(1, this.now());
                  var4.setString(2, var1);
                  var4.setString(3, var2);
                  var4.executeUpdate();
               } finally {
                  this.pgPool.release(var3);
               }
            } catch (Exception var15) {
               this.getLogger().log(Level.WARNING, "Artifact visual deactivate failed", (Throwable)var15);
            }
         }
      );
   }

   private void saveProtectedBlockVisualAsync(Location var1, String var2, String var3, Material var4, int var5, String var6, String var7) {
      String var8 = "pbv-" + UUID.randomUUID();
      long var9 = this.now();
      double var11 = this.customBlockOffsetY(var2);
      double var13 = this.customBlockScale(var2);
      boolean queued = this.runAsync(
         () -> {
            try {
               Connection var15 = this.pgPool.acquire();

               try (
                  PreparedStatement var16 = var15.prepareStatement("DELETE FROM protected_block_visuals WHERE kind=? AND linked_id=?");
                  PreparedStatement var17 = var15.prepareStatement(
                     "    INSERT INTO protected_block_visuals(\n        id,kind,linked_id,world,x,y,z,entity_uuid,base_material,custom_model_data,model_id,\n        offset_x,offset_y,offset_z,scale_x,scale_y,scale_z,yaw,pitch,created_at,updated_at,active\n    ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,1)\n"
                  );
               ) {
                  var16.setString(1, var2);
                  var16.setString(2, var3);
                  var16.executeUpdate();
                  var17.setString(1, var8);
                  var17.setString(2, var2);
                  var17.setString(3, var3);
                  var17.setString(4, var1.getWorld().getName());
                  var17.setInt(5, var1.getBlockX());
                  var17.setInt(6, var1.getBlockY());
                  var17.setInt(7, var1.getBlockZ());
                  var17.setString(8, var7);
                  var17.setString(9, var4.name());
                  var17.setInt(10, var5);
                  var17.setString(11, var6);
                  var17.setDouble(12, 0.5);
                  var17.setDouble(13, var11);
                  var17.setDouble(14, 0.5);
                  var17.setDouble(15, var13);
                  var17.setDouble(16, var13);
                  var17.setDouble(17, var13);
                  var17.setDouble(18, 0.0);
                  var17.setDouble(19, 0.0);
                  var17.setLong(20, var9);
                  var17.setLong(21, var9);
                  var17.executeUpdate();
               } finally {
                  this.pgPool.release(var15);
               }
            } catch (Exception var32) {
               this.getLogger().log(Level.WARNING, "Artifact visual save failed", (Throwable)var32);
               this.removeSpawnedProtectedVisual(var1, var2, var3, var7);
            }
         }
      );
      if (!queued) {
         this.removeSpawnedProtectedVisual(var1, var2, var3, var7);
      }
   }

   private String readVisualString(PersistentDataContainer pdc, NamespacedKey currentKey, String keyName) {
      String value = pdc.get(currentKey, PersistentDataType.STRING);
      if (value != null) {
         return value;
      }
      for (String namespace : List.of("copimineeconomycore", "copimineultimateadminplus", "copimineartifacts", "copimineelectioncore")) {
         value = pdc.get(new NamespacedKey(namespace, keyName), PersistentDataType.STRING);
         if (value != null) {
            return value;
         }
      }
      return "";
   }

   private boolean isLegacyProtectedVisualModel(ItemDisplay display, String kind, String modelId, int customModelData) {
      int expectedModelData = customModelData;
      if (expectedModelData <= 0 && "ARTIFACT_SHOP".equals(kind)) {
         expectedModelData = 14004;
      }
      ItemStack stack = display.getItemStack();
      ItemMeta meta = stack == null ? null : stack.getItemMeta();
      return expectedModelData > 0
            && stack != null
            && stack.getType() == Material.PAPER
            && meta != null
            && meta.hasCustomModelData()
            && meta.getCustomModelData() == expectedModelData;
   }

   private boolean runAsync(Runnable var1) {
      if (!this.isEnabled()) {
         return false;
      }
      if (this.dbExecutor == null || this.dbExecutor.isShutdown()) {
         return false;
      }
      try {
         this.dbExecutor.submit(var1);
         return true;
      } catch (RejectedExecutionException rejected) {
         // Shutdown/back-pressure is a normal fail-closed outcome.  Never
         // execute the JDBC callback on the Bukkit thread as a fallback.
         this.getLogger().warning("Artifacts database queue is full or stopping; action deferred.");
         return false;
      }
   }

   private void removeSpawnedProtectedVisual(Location location, String kind, String linkedId, String entityUuid) {
      this.runSync(() -> {
         try {
            Entity entity = Bukkit.getEntity(UUID.fromString(entityUuid));
            if (entity != null && this.isProtectedVisualEntity(entity, kind, linkedId)) {
               entity.remove();
            }
         } catch (IllegalArgumentException ignored) {
         }
      });
   }

   private boolean isProtectedVisualEntity(Entity entity, String kind, String linkedId) {
      if (entity == null || visualEntityTypeKey == null || visualKindKey == null || visualLinkedIdKey == null) {
         return false;
      }
       String type = readVisualString(entity.getPersistentDataContainer(), visualEntityTypeKey, "visual_entity_type");
       String storedKind = readVisualString(entity.getPersistentDataContainer(), visualKindKey, "visual_kind");
       String storedLinkedId = readVisualString(entity.getPersistentDataContainer(), visualLinkedIdKey, "visual_linked_id");
      return "PROTECTED_BLOCK_VISUAL".equals(type) && Objects.equals(kind, storedKind)
            && Objects.equals(linkedId, storedLinkedId);
   }

   private void runSync(Runnable var1) {
      if (!this.isEnabled()) {
         return;
      }
      try {
         Bukkit.getScheduler().runTask(this, var1);
      } catch (IllegalPluginAccessException ignored) {
         // A database callback can finish while Bukkit is disabling this plugin.
         // Do not turn a normal shutdown race into a warning or failed reload.
      }
   }

   private String blockKey(Location var1) {
      return var1.getWorld().getName() + ":" + var1.getBlockX() + ":" + var1.getBlockY() + ":" + var1.getBlockZ();
   }

   private String actionCooldownKey(Player player, CopiMineArtifacts.CatalogItem item) {
      if (player == null || item == null || item.itemId().isBlank()) {
         return "";
      }
      return player.getUniqueId() + ":" + item.itemId();
   }

   private void sendCooldownMessage(Player player, CopiMineArtifacts.CatalogItem item, long cooldownUntil, long current) {
      long remaining = Math.max(1L, cooldownUntil - current);
      String itemName = this.firstNonBlank(item == null ? "" : item.name(), "Предмет");
      player.sendMessage(this.color("&e" + itemName + " ещё на перезарядке (&f" + remaining + " сек.&e)."));
   }

   private long now() {
      return Instant.now().getEpochSecond();
   }

   /** Forward only handled player-facing failures to the central /reporta
    * collector.  Reflection keeps Artifacts optional and leaves the normal
    * player-safe message intact when AdminPlus is unavailable. */
   private void forwardBugToAdminPlus(Player player, String source, String action, Throwable error, ItemStack item, Location location) {
      if (player == null) {
         return;
      }
      // Keep the bridge optional: Artifacts must not link against the admin
      // plugin at compile time or become an economy dependency.
      String bridgeName = "CopiMine" + "Ultimate" + "Admin" + "Plus";
      Plugin admin = Bukkit.getPluginManager().getPlugin(bridgeName);
      if (admin == null || !admin.isEnabled()) {
         return;
      }
      try {
         admin.getClass().getMethod(
               "capturePluginError",
               Player.class,
               String.class,
               String.class,
               Throwable.class,
               ItemStack.class,
               Location.class
            )
            .invoke(admin, player, firstNonBlank(source, "artifacts"), firstNonBlank(action, "unknown"), error, item, location);
      } catch (ReflectiveOperationException bridgeError) {
         this.getLogger().fine("reporta bridge unavailable: " + this.safeErr(bridgeError));
      }
   }

   private CopiMineArtifacts.Category parseCategory(String var1) {
      try {
         return CopiMineArtifacts.Category.valueOf(var1.toUpperCase(Locale.ROOT));
      } catch (Exception var3) {
         return CopiMineArtifacts.Category.RP;
      }
   }

   private String color(String var1) {
      return ChatColor.translateAlternateColorCodes('&', var1 == null ? "" : var1);
   }

   private String strip(String var1) {
      return ChatColor.stripColor(this.color(var1));
   }

   private int artifactModelData(String var1) {
      return ARTIFACT_MODEL_DATA.getOrDefault(var1, 0);
   }

   private int artifactEffectChance(String var1) {
      return ARTIFACT_EFFECT_CHANCE.getOrDefault(var1, 100);
   }

   private String artifactVisualEffect(String var1) {
      return ARTIFACT_VISUAL_EFFECTS.getOrDefault(var1, "");
   }

   private int normalizeChance(int var1) {
      return Math.max(0, Math.min(100, var1));
   }

   private boolean rollEffectChance(CopiMineArtifacts.CatalogItem var1) {
      int var2 = this.normalizeChance(var1.effectChancePercent());
      if (var2 >= 100) {
         return true;
      } else {
         return var2 <= 0 ? false : this.random.nextInt(100) < var2;
      }
   }

   private String maskedPin(String var1) {
      return var1 != null && !var1.isEmpty() ? "*".repeat(var1.length()) : "••••";
   }

   // Only clear displays tied to registered shops. Scanning every world during
   // startup or shutdown makes a disabled overlay unnecessarily expensive.
   private void cleanupAllProtectedBlockVisualEntities() {
      for (CopiMineArtifacts.Shop shop : this.shopsByLocation.values()) {
         try {
            this.cleanupProtectedBlockVisuals("ARTIFACT_SHOP", shop.shopId());
         } catch (Exception error) {
            this.getLogger().warning("Artifact shop visual cleanup failed: " + this.safeErr(error));
         }
      }
   }

   private void spawnShopTitleDisplay(Location base, String shopId, String title) {
      if (base == null || base.getWorld() == null || shopId == null || shopId.isBlank()) {
         return;
      }
      this.cleanupShopTitleDisplay(base, shopId);
      Location location = base.clone().add(0.5D, 1.9D, 0.5D);
      base.getWorld().spawn(location, TextDisplay.class, display -> {
         display.setText(this.color("&a" + this.first(title, shopId)));
         display.setBillboard(Billboard.CENTER);
         display.setAlignment(TextDisplay.TextAlignment.CENTER);
         display.setShadowed(true);
         display.setSeeThrough(false);
         display.setPersistent(true);
         display.getPersistentDataContainer().set(this.visualEntityTypeKey, PersistentDataType.STRING, "SHOP_TITLE_DISPLAY");
         display.getPersistentDataContainer().set(this.visualLinkedIdKey, PersistentDataType.STRING, shopId);
      });
   }

   private void cleanupShopTitleDisplay(Location base, String shopId) {
      if (base == null || base.getWorld() == null || shopId == null || shopId.isBlank()) {
         return;
      }
      for (Entity entity : base.getWorld().getNearbyEntities(base.clone().add(0.5D, 1.9D, 0.5D), 2.0D, 2.5D, 2.0D)) {
         if (entity instanceof TextDisplay display
               && "SHOP_TITLE_DISPLAY".equals(display.getPersistentDataContainer().get(this.visualEntityTypeKey, PersistentDataType.STRING))
               && shopId.equalsIgnoreCase(display.getPersistentDataContainer().get(this.visualLinkedIdKey, PersistentDataType.STRING))) {
            display.remove();
         }
      }
   }

   private void deactivateAllProtectedBlockVisualRowsAsync() {
      this.runAsync(() -> {
         try {
            Connection var1 = this.pgPool.acquire();
            try (PreparedStatement var2 = var1.prepareStatement(
                  "UPDATE protected_block_visuals SET active=0,updated_at=? WHERE active=1"
               )) {
               var2.setLong(1, this.now());
               var2.executeUpdate();
            } finally {
               this.pgPool.release(var1);
            }
         } catch (Exception var4) {
            this.getLogger().log(Level.WARNING, "Artifact visual bulk cleanup failed", (Throwable)var4);
         }
      });
   }

   private String nice(String var1) {
      return Arrays.stream(var1.split("_"))
         .map(var0 -> (CharSequence)(var0.isEmpty() ? var0 : Character.toUpperCase(var0.charAt(0)) + var0.substring(1)))
         .collect(Collectors.joining(" "));
   }

   private String shortId(String var1) {
      return var1 == null ? "" : var1.substring(0, Math.min(8, var1.length()));
   }

   private int parseInt(String var1, int var2) {
      try {
         return Integer.parseInt(var1);
      } catch (Exception var4) {
         return var2;
      }
   }

   private long parseLong(String var1, long var2) {
      try {
         return Long.parseLong(var1);
      } catch (Exception var5) {
         return var2;
      }
   }

   private double parseDouble(String var1, double var2) {
      try {
         return Double.parseDouble(var1);
      } catch (Exception var5) {
         return var2;
      }
   }

   private boolean parseBoolean(Object var1, boolean var2) {
      if (var1 == null) {
         return var2;
      } else {
         String var3 = String.valueOf(var1).trim();
         if (var3.isEmpty()) {
            return var2;
         } else if ("true".equalsIgnoreCase(var3) || "yes".equalsIgnoreCase(var3) || "1".equals(var3)) {
            return true;
         } else {
            return !"false".equalsIgnoreCase(var3) && !"no".equalsIgnoreCase(var3) && !"0".equals(var3) ? var2 : false;
         }
      }
   }

   private double clampProcChance(double var1) {
      return !Double.isNaN(var1) && !Double.isInfinite(var1) ? Math.max(0.0, Math.min(1.0, var1)) : 0.0;
   }

   private String firstNonBlank(String var1, String var2) {
      return var1 != null && !var1.isBlank() ? var1 : var2;
   }

   private String str(Object var1) {
      return var1 == null ? "" : String.valueOf(var1);
   }

   private List<String> asStringList(Object var1) {
      return var1 instanceof List var2 ? var2.stream().map(String::valueOf).toList() : List.of();
   }

   private String toJson(List<String> var1) {
      return "[" + var1.stream().map(var0 -> "\"" + var0.replace("\"", "\\\"") + "\"").collect(Collectors.joining(",")) + "]";
   }

   private String safeErr(Throwable var1) {
      String var2 = var1.getMessage();
      return var2 == null ? var1.getClass().getSimpleName() : var2;
   }

   private String safeBridgeCode(String var1) {
      if (var1 != null && !var1.isBlank()) {
         String var2 = var1.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_\\-]", "_");
         return var2.length() > 48 ? var2.substring(0, 48) : var2;
      } else {
         return "BRIDGE_REJECTED";
      }
   }

   private List<String> prefix(List<String> var1, String var2) {
      String var3 = var2 == null ? "" : var2.toLowerCase(Locale.ROOT);
      return var1.stream().filter(var1x -> var1x.startsWith(var3)).toList();
   }

   private Long tryParseNonNegativeLong(String value) {
      try {
         long parsed = Long.parseLong(value);
         return parsed < 0L ? null : parsed;
      } catch (NumberFormatException error) {
         return null;
      }
   }

   private String resolvePriceCatalogKind(String itemId, String requestedKind) {
      boolean inDonationCatalog = this.donationCatalogById.containsKey(itemId);
      boolean inArCatalog = this.catalogById.containsKey(itemId) && !inDonationCatalog;
      if ("donation".equals(requestedKind)) {
         return inDonationCatalog ? "donation" : null;
      }

      if ("ar".equals(requestedKind)) {
         return inArCatalog ? "ar" : null;
      }

      if (!requestedKind.isBlank()) {
         return null;
      }

      if (inDonationCatalog) {
         return "donation";
      }

      return inArCatalog ? "ar" : null;
   }

   private void updateItemPriceInConfig(String itemId, String kind, long price) throws IOException {
      File itemsFile = new File(this.getDataFolder(), "items.yml");
      YamlConfiguration config = YamlConfiguration.loadConfiguration(itemsFile);
      boolean updated = false;

      if ("ar".equals(kind)) {
         List<Map<?, ?>> items = config.getMapList("items");
         ArrayList<Map<String, Object>> updatedItems = new ArrayList<>();

         for (Map<?, ?> row : items) {
            LinkedHashMap<String, Object> normalizedRow = new LinkedHashMap<>();

            for (Entry<?, ?> entry : row.entrySet()) {
               normalizedRow.put(String.valueOf(entry.getKey()), entry.getValue());
            }

            if (itemId.equalsIgnoreCase(this.str(row.get("id")))) {
               normalizedRow.put("price_ar", price);
               updated = true;
            }

            updatedItems.add(normalizedRow);
         }

         config.set("items", updatedItems);
      } else if ("donation".equals(kind)) {
         List<Map<?, ?>> items = config.getMapList("donation-catalog.items");
         ArrayList<Map<String, Object>> updatedItems = new ArrayList<>();

         for (Map<?, ?> row : items) {
            LinkedHashMap<String, Object> normalizedRow = new LinkedHashMap<>();

            for (Entry<?, ?> entry : row.entrySet()) {
               normalizedRow.put(String.valueOf(entry.getKey()), entry.getValue());
            }

            if (itemId.equalsIgnoreCase(this.str(row.get("item-id")))) {
               normalizedRow.put("price-donation", price);
               updated = true;
            }

            updatedItems.add(normalizedRow);
         }

         config.set("donation-catalog.items", updatedItems);
      }

      if (!updated) {
         throw new IOException("Item " + itemId + " was not found in " + kind + " catalog.");
      }

      Path path = itemsFile.toPath();
      Path temporary = Files.createTempFile(path.getParent(), "items.yml.", ".tmp");
      try {
         config.save(temporary.toFile());
         try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
         } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
         }
      } finally {
         Files.deleteIfExists(temporary);
      }
   }

   private void writeAtomicBytes(Path target, byte[] bytes) throws IOException {
      Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString() + ".", ".rollback");
      try {
         Files.write(temporary, bytes, StandardOpenOption.TRUNCATE_EXISTING);
         try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
         } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
         }
      } finally {
         Files.deleteIfExists(temporary);
      }
   }

   private final class ArtifactBridgeAdapter {
      private ArtifactsBridge resolveBridge() {
         Plugin plugin = Bukkit.getPluginManager().getPlugin("CopiMineEconomyCore");
         if (plugin instanceof CopiMineEconomyCore main && plugin.isEnabled()) {
            try {
               return main.artifactsBridge();
            } catch (Exception var4) {
               if (CopiMineArtifacts.this.bridgeWarned.compareAndSet(false, true)) {
                  CopiMineArtifacts.this.getLogger().warning("Artifacts bridge is unavailable: " + CopiMineArtifacts.this.safeErr(var4));
               }

               return null;
            }
         }

         return null;
      }

      boolean isAvailable() {
         return this.resolveBridge() != null;
      }

      CopiMineArtifacts.BridgeHealthSnapshot health(Player var1, String var2) {
         return this.health(var1 == null ? null : var1.getUniqueId(), var2);
      }

      CopiMineArtifacts.BridgeHealthSnapshot health(UUID var1, String var2) {
         ArtifactsBridge var3 = this.resolveBridge();
         if (var3 == null) {
            return new CopiMineArtifacts.BridgeHealthSnapshot(false, false, false, 0L, var2, "BRIDGE_UNAVAILABLE");
         } else {
            try {
               Health var4 = var3.health(var1, var2);
               return new CopiMineArtifacts.BridgeHealthSnapshot(
                  var4.bridgeReady, var4.postgresReady, var4.pinReady, var4.balance, var4.context, var4.lastError
               );
            } catch (Exception var5) {
               return new CopiMineArtifacts.BridgeHealthSnapshot(false, false, false, 0L, var2, CopiMineArtifacts.this.safeErr(var5));
            }
         }
      }

      CopiMineArtifacts.BridgePinStatus pinStatus(Player var1) {
         ArtifactsBridge var2 = this.resolveBridge();
         if (var2 == null) {
            return new CopiMineArtifacts.BridgePinStatus(false, false, 0L);
         } else {
            try {
               PinStatus var3 = var2.pinStatus(var1.getUniqueId());
               return new CopiMineArtifacts.BridgePinStatus(var3.configured, var3.mustChange, var3.lockedSeconds);
            } catch (Exception var4) {
               return new CopiMineArtifacts.BridgePinStatus(false, false, 0L);
            }
         }
      }

      CompletableFuture<CopiMineArtifacts.BridgePinStatus> pinStatusAsync(UUID var1) {
         ArtifactsBridge var2 = this.resolveBridge();
         if (var2 == null) {
            return CompletableFuture.completedFuture(new CopiMineArtifacts.BridgePinStatus(false, false, 0L));
         } else {
            try {
               return var2.pinStatusAsync(var1)
                  .thenApply(var0 -> new CopiMineArtifacts.BridgePinStatus(var0.configured, var0.mustChange, var0.lockedSeconds))
                  .exceptionally(var0 -> new CopiMineArtifacts.BridgePinStatus(false, false, 0L));
            } catch (Exception var4) {
               return CompletableFuture.completedFuture(new CopiMineArtifacts.BridgePinStatus(false, false, 0L));
            }
         }
      }

      CopiMineArtifacts.BridgeTxnResult charge(Player player, long amount, String pin, String idempotencyKey, String action, String details) {
         ArtifactsBridge bridge = this.resolveBridge();
         if (bridge == null) {
            return new CopiMineArtifacts.BridgeTxnResult(false, "BRIDGE_UNAVAILABLE", "BankService bridge is unavailable.", 0L, "");
         }

         try {
            TxnResult result = bridge.charge(player.getUniqueId(), player.getName(), amount, pin, idempotencyKey, action, details);
            return new CopiMineArtifacts.BridgeTxnResult(result.ok, result.code, result.message, result.balanceAfter, result.txId);
         } catch (Exception error) {
            return new CopiMineArtifacts.BridgeTxnResult(false, "BRIDGE_ERROR", CopiMineArtifacts.this.safeErr(error), 0L, "");
         }
      }

      CopiMineArtifacts.BridgeTxnResult refund(Player player, long amount, String idempotencyKey, String action, String details) {
         ArtifactsBridge bridge = this.resolveBridge();
         if (bridge == null) {
            return new CopiMineArtifacts.BridgeTxnResult(false, "BRIDGE_UNAVAILABLE", "BankService bridge is unavailable.", 0L, "");
         }

         try {
            TxnResult result = bridge.refund(player.getUniqueId(), player.getName(), amount, idempotencyKey, action, details);
            return new CopiMineArtifacts.BridgeTxnResult(result.ok, result.code, result.message, result.balanceAfter, result.txId);
         } catch (Exception error) {
            return new CopiMineArtifacts.BridgeTxnResult(false, "BRIDGE_ERROR", CopiMineArtifacts.this.safeErr(error), 0L, "");
         }
      }

      CopiMineArtifacts.BridgeTxnResult credit(UUID var1, String var2, long var3, String var5, String var6, String var7) {
         ArtifactsBridge var8 = this.resolveBridge();
         if (var8 == null) {
            return new CopiMineArtifacts.BridgeTxnResult(false, "BRIDGE_UNAVAILABLE", "BankService bridge is unavailable.", 0L, "");
         } else {
            try {
               TxnResult var9 = var8.credit(var1, var2, var3, var5, var6, var7);
               return new CopiMineArtifacts.BridgeTxnResult(var9.ok, var9.code, var9.message, var9.balanceAfter, var9.txId);
            } catch (Exception var10) {
               return new CopiMineArtifacts.BridgeTxnResult(false, "BRIDGE_ERROR", CopiMineArtifacts.this.safeErr(var10), 0L, "");
            }
         }
      }

      CopiMineArtifacts.BridgeTxnResult creditAccount(String var1, String var2, String var3, long var4, String var6, String var7, String var8) {
         ArtifactsBridge var9 = this.resolveBridge();
         if (var9 == null) {
            return new CopiMineArtifacts.BridgeTxnResult(false, "BRIDGE_UNAVAILABLE", "BankService bridge is unavailable.", 0L, "");
         } else {
            try {
               TxnResult var10 = var9.creditAccount(var1, var2, var3, var4, var6, var7, var8);
               return new CopiMineArtifacts.BridgeTxnResult(var10.ok, var10.code, var10.message, var10.balanceAfter, var10.txId);
            } catch (Exception var11) {
               return new CopiMineArtifacts.BridgeTxnResult(false, "BRIDGE_ERROR", CopiMineArtifacts.this.safeErr(var11), 0L, "");
            }
         }
      }

      CopiMineArtifacts.BridgeTxnResult transferToAccount(Player var1, String var2, String var3, String var4, String var5, long var6, String var8, String var9, String var10) {
         return var1 == null
               ? new CopiMineArtifacts.BridgeTxnResult(false, "INVALID_PLAYER", "Player is unavailable.", 0L, "")
               : this.transferToAccount(var1.getUniqueId(), var1.getName(), var2, var3, var4, var5, var6, var8, var9, var10);
      }

      CopiMineArtifacts.BridgeTxnResult transferToAccount(UUID playerUuid, String playerName, String var2, String var3, String var4, String var5, long var6, String var8, String var9, String var10) {
         ArtifactsBridge var11 = this.resolveBridge();
         if (var11 == null || playerUuid == null) {
            return new CopiMineArtifacts.BridgeTxnResult(false, "BRIDGE_UNAVAILABLE", "BankService bridge is unavailable.", 0L, "");
         } else {
            try {
               TxnResult var12 = var11.transferToAccount(playerUuid, CopiMineArtifacts.this.firstNonBlank(playerName, "Player"), var2, var3, var4, var5, var6, var8, var9, var10);
               return new CopiMineArtifacts.BridgeTxnResult(var12.ok, var12.code, var12.message, var12.balanceAfter, var12.txId);
            } catch (Exception var13) {
               return new CopiMineArtifacts.BridgeTxnResult(false, "BRIDGE_ERROR", CopiMineArtifacts.this.safeErr(var13), 0L, "");
            }
         }
      }

      CopiMineArtifacts.BridgeTxnResult transferFromAccount(String var1, String var2, String var3, UUID var4, String var5, long var6, String var8, String var9, String var10) {
         ArtifactsBridge var11 = this.resolveBridge();
         if (var11 == null) {
            return new CopiMineArtifacts.BridgeTxnResult(false, "BRIDGE_UNAVAILABLE", "BankService bridge is unavailable.", 0L, "");
         } else {
            try {
               TxnResult var12 = var11.transferFromAccount(var1, var2, var3, var4, var5, var6, var8, var9, var10);
               return new CopiMineArtifacts.BridgeTxnResult(var12.ok, var12.code, var12.message, var12.balanceAfter, var12.txId);
            } catch (Exception var13) {
               return new CopiMineArtifacts.BridgeTxnResult(false, "BRIDGE_ERROR", CopiMineArtifacts.this.safeErr(var13), 0L, "");
            }
         }
      }

      CopiMineArtifacts.BridgeTxnResult stealFromPlayerAccount(UUID fromUuid, String fromName, UUID toUuid, String toName, long amount, String idempotencyKey, String action, String details) {
         ArtifactsBridge bridge = this.resolveBridge();
         if (bridge == null) {
            return new CopiMineArtifacts.BridgeTxnResult(false, "BRIDGE_UNAVAILABLE", "BankService bridge is unavailable.", 0L, "");
         }
         try {
            TxnResult result = bridge.stealFromPlayerAccount(fromUuid, fromName, toUuid, toName, amount, idempotencyKey, action, details);
            return new CopiMineArtifacts.BridgeTxnResult(result.ok, result.code, result.message, result.balanceAfter, result.txId);
         } catch (Exception error) {
            return new CopiMineArtifacts.BridgeTxnResult(false, "BRIDGE_ERROR", CopiMineArtifacts.this.safeErr(error), 0L, "");
         }
      }

      List<Map<String, Object>> findOrphanedArtifactShopTransfers(int limit) {
         ArtifactsBridge bridge = this.resolveBridge();
         if (bridge == null) {
            return List.of();
         }
         return bridge.findOrphanedArtifactShopTransfers(limit);
      }

      private CopiMineArtifacts.BridgeTxnResult invokeTxn(String var1, Player var2, long var3, String var5, String var6, String var7, String var8) {
         ArtifactsBridge var9 = this.resolveBridge();
         if (var9 == null) {
            return new CopiMineArtifacts.BridgeTxnResult(false, "BRIDGE_UNAVAILABLE", "BankService bridge is unavailable.", 0L, "");
         } else {
            try {
               TxnResult var10;
               if ("charge".equals(var1)) {
                  var10 = var9.charge(var2.getUniqueId(), var2.getName(), var3, var5, var6, var7, var8);
               } else {
                  var10 = var9.refund(var2.getUniqueId(), var2.getName(), var3, var6, var7, var8);
               }

               return new CopiMineArtifacts.BridgeTxnResult(var10.ok, var10.code, var10.message, var10.balanceAfter, var10.txId);
            } catch (Exception var11) {
               return new CopiMineArtifacts.BridgeTxnResult(false, "BRIDGE_ERROR", CopiMineArtifacts.this.safeErr(var11), 0L, "");
            }
         }
      }
   }

   private static record BridgeHealthSnapshot(boolean bridgeReady, boolean postgresReady, boolean pinReady, long balance, String context, String lastError) {
   }

   private static record BridgePinStatus(boolean configured, boolean mustChange, long lockedSeconds) {
   }

   private static record BridgeTxnResult(boolean ok, String code, String message, long balanceAfter, String txId) {
   }

   private static record CatalogItem(
      String itemId,
      CopiMineArtifacts.Category category,
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
   ) {
   }

   private static record PozdnyakovMagmaRestore(Location location, String originalBlockData, long expiresAtMillis) {
   }

   private static record TemporaryBlockRestore(Location location, Material placedMaterial, String originalBlockData, String placedBlockData, long expiresAtMillis) {
   }

   private static enum Category {
      WEAPON,
      ARMOR,
      TOOL,
      RP;
   }

   private static record DeliveringInstanceRow(String uniqueItemId, String purchaseId, String itemId) {
   }

   private static record DonationCatalogItem(
      String itemId,
      String displayName,
      Material baseMaterial,
      long priceDonation,
      boolean enabled,
      String source,
      boolean ownerBound,
      String reclaimPolicy,
      String consumePolicy,
      String effectProfileId,
      String effectDescription,
      int cooldownSeconds,
      double procChance,
      int maxStack,
      boolean repairable,
      boolean customTextureModeAllowed,
      int customModelData,
      String visualEffectId,
      List<String> lore
   ) {
   }

   private static record DonationClaimRow(String claimId, String purchaseId, String itemId, long amount, String status) {
   }

   private static record DonationDeliveryContext(String purchaseId, CopiMineArtifacts.CatalogItem item, List<String> uniqueItemIds) {
   }

   private static record DonationLossJournalEntry(long createdAt, String ownerUuid, String uniqueItemId, String itemId, String reason) {
   }

   private static record DonationOwnershipSnapshot(
      Set<String> activeItemIds,
      Set<String> claimableItemIds,
      Set<String> reclaimableItemIds,
      List<CopiMineArtifacts.ReclaimableDonationRow> reclaimableRows,
      int claimPendingCount
   ) {
   }

   private static record DonationReclaimContext(String oldUniqueItemId, String newUniqueItemId, String purchaseId, String itemId) {
   }

   private static final class MenuHolder implements InventoryHolder {
      final UUID sessionId;
      final UUID playerUuid;
      final String shopId;
      final CopiMineArtifacts.ViewType viewType;
      final String category;
      final String itemId;
      final int page;
      private Inventory inventory;

      MenuHolder(CopiMineArtifacts.SessionState var1, UUID var2) {
         this.sessionId = var1.sessionId;
         this.playerUuid = var2;
         this.shopId = var1.shopId == null ? "" : var1.shopId;
         this.viewType = var1.viewType;
         this.category = var1.currentCategory == null ? "" : var1.currentCategory;
         this.itemId = var1.currentItemId == null ? "" : var1.currentItemId;
         this.page = var1.page;
      }

      void setInventory(Inventory var1) {
         this.inventory = var1;
      }

      public Inventory getInventory() {
         return this.inventory;
      }
   }

   private static record OfficialDonationRef(String uniqueItemId, String itemId, UUID ownerUuid, String purchaseId) {
   }

   private static record OfficialInstanceBinding(String itemId, String ownerUuid) {
   }

   private static record PendingDeliveryRow(String deliveryId, String purchaseId, String uniqueItemId, String itemId) {
   }

   private static record OrphanedShopTransfer(
      String transactionId, String recipientAccountId, long amount, String idempotencyKey, String playerUuid, String playerName
   ) {
   }

   private static final class PgPool {
      private final CopiMineArtifacts.PgSettings settings;
      private final Deque<Connection> idle = new ArrayDeque<>();
      private final int max;
      private int total;
      private boolean closed;

      PgPool(CopiMineArtifacts.PgSettings var1, int var2) {
         this.settings = var1;
         this.max = Math.max(2, var2);
      }

      synchronized Connection acquire() throws SQLException {
         if (this.closed) {
            throw new SQLException("Artifact PostgreSQL pool is closed.");
         }
         long deadline = System.currentTimeMillis() + 1500L;

         while (true) {
            while (!this.idle.isEmpty()) {
               Connection var6 = this.idle.pop();
               if (this.usable(var6)) {
                  return var6;
               }

               this.safeClose(var6);
               this.total--;
            }

            if (this.total < this.max) {
               Connection var7 = DriverManager.getConnection(this.settings.jdbcUrl(), this.settings.user, this.settings.password);

               try {
                  var7.setAutoCommit(true);
                  this.total++;
                  return var7;
               } catch (SQLException var8) {
                  this.safeClose(var7);
                  throw var8;
               }
            }

            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0L) {
               throw new SQLException("Artifact PostgreSQL pool timed out while waiting for a free connection.");
            }

            try {
               this.wait(Math.min(remaining, 250L));
            } catch (InterruptedException var5) {
               Thread.currentThread().interrupt();
               throw new SQLException("Artifact PostgreSQL pool wait interrupted.", var5);
            }
         }
      }

      synchronized void release(Connection var1) {
         if (var1 != null) {
            if (this.closed) {
               this.safeClose(var1);
               this.total = Math.max(0, this.total - 1);
               this.notifyAll();
               return;
            }
            try {
               if (this.usable(var1)) {
                  this.idle.push(var1);
               } else {
                  this.safeClose(var1);
                  this.total--;
               }
            } catch (SQLException var3) {
               this.safeClose(var1);
               this.total--;
            }

            this.notifyAll();
         }
      }

      synchronized void close() {
         if (this.closed) {
            return;
         }
         this.closed = true;
         for (Connection var2 : this.idle) {
            this.safeClose(var2);
         }

         this.idle.clear();
         this.total = 0;
      }

      private boolean usable(Connection var1) throws SQLException {
         if (var1 == null || var1.isClosed()) {
            return false;
         }

         try {
            return var1.isValid(2);
         } catch (AbstractMethodError | SQLException var4) {
            try (PreparedStatement var2 = var1.prepareStatement("SELECT 1")) {
               return var2.execute();
            } catch (SQLException var3) {
               return false;
            }
         }
      }

      private void safeClose(Connection var1) {
         if (var1 == null) {
            return;
         }

         try {
            var1.close();
         } catch (SQLException var3) {
         }
      }
   }

   private static final class PgSettings {
      final String host;
      final int port;
      final String db;
      final String schema;
      final String user;
      final String password;

      PgSettings(String var1, int var2, String var3, String var4, String var5, String var6) {
         this.host = var1;
         this.port = var2;
         this.db = var3;
         this.schema = var4;
         this.user = var5;
         this.password = var6;
      }

      String jdbcUrl() {
         return "jdbc:postgresql://" + this.host + ":" + this.port + "/" + this.db + "?currentSchema=" + this.schema;
      }
   }

   private static record PurchaseContext(
      String purchaseId,
      String uniqueItemId,
      CopiMineArtifacts.CatalogItem item,
      CopiMineArtifacts.Shop shop,
      String pin,
      CopiMineArtifacts.ShopRevenueRecipient revenueRecipient
   ) {
   }

   private static record ShopRevenueRecipient(UUID presidentUuid, String presidentName, String termId, String budgetAccountId) {
   }

   private static record ReclaimableDonationRow(String uniqueItemId, String purchaseId, String itemId, long updatedAt) {
   }

   private static final class SessionState {
      UUID sessionId = UUID.randomUUID();
      String shopId = "";
      CopiMineArtifacts.ViewType viewType = CopiMineArtifacts.ViewType.MAIN;
      String currentCategory = "";
      String currentItemId = "";
      int page = 0;
      String pinBuffer = "";
      String pinAction = "purchase";
      String repairInFlightId = "";
      String repairItemUniqueId = "";
      String repairFingerprint = "";
      int repairSlot = -1;
      int repairOriginalDamage = 0;
      String purchaseInFlightId = "";
      long confirmedPriceAr = -1L;
      long confirmedPriceDonation = -1L;
      long confirmedCatalogGeneration = -1L;
      UUID giftTargetUuid;
      String giftTargetName = "";
      String giftKind = "";
      long lastClickAt = 0L;
      long lastActionAt = 0L;
      final Map<Integer, String> actions = new HashMap<>();
   }

   private static record Shop(String shopId, String title, String world, int x, int y, int z,
                             boolean enabled, String anchorMaterial) {
      private Shop(String shopId, String title, String world, int x, int y, int z, boolean enabled) {
         this(shopId, title, world, x, y, z, enabled, "");
      }

      String locationKey() {
         return this.world + ":" + this.x + ":" + this.y + ":" + this.z;
      }
   }

   private static record GiftTarget(String uuid, String name) {}
   private static record ShopStats(CopiMineArtifacts.Shop shop, long buyers, long arTurnover, long donationTurnover) {}

   private static enum ViewType {
      MAIN,
      CATEGORY,
      DETAIL,
      CONFIRM,
      PIN,
      PURCHASES,
      PENDING_DELIVERY,
      HELP,
      REPAIR,
      SUCCESS,
      ERROR,
      DONATION_ROOT,
      DONATION_BALANCE,
      DONATION_CATALOG,
      DONATION_OWNED,
      DONATION_RECLAIM,
      ADMIN_MAIN,
      ADMIN_SHOPS,
      ADMIN_GIFT_PLAYERS,
      ADMIN_GIFT_KIND,
      ADMIN_GIFT_CATALOG,
      ADMIN_GIFT_CONFIRM,
      ADMIN_CATALOG,
      ADMIN_DIAGNOSTICS;
   }

   private static final class VisualEffectService {
      private final JavaPlugin plugin;

      VisualEffectService(JavaPlugin var1) {
         this.plugin = var1;
      }

      void applyTo(LivingEntity var1, String var2, int var3) {
         if (var1 != null && var2 != null && !var2.isBlank()) {
            int var4 = Math.max(20, var3 * 20);
            Location var5 = var1.getLocation().add(0.0, 1.0, 0.0);
            World var6 = var5.getWorld();
            if (var6 != null) {
               String var7 = var2.toUpperCase(Locale.ROOT);
               switch (var7) {
                  case "INVERTED_SCREEN":
                     var1.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, var4, 0, false, false, true));
                     if (var1 instanceof Player var9) {
                        var9.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Math.min(var4, 80), 0, false, false, true));
                     }

                     var6.spawnParticle(Particle.REVERSE_PORTAL, var5, 24, 0.45, 0.45, 0.45, 0.02);
                     break;
                  case "DARK_PULSE":
                     var1.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, Math.min(var4, 160), 0, false, false, true));
                     var6.spawnParticle(Particle.SMOKE, var5, 18, 0.4, 0.3, 0.4, 0.03);
                     break;
                  case "MOON_GLOW":
                     var1.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, var4, 0, false, false, true));
                     var1.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, var4, 0, false, false, true));
                     var6.spawnParticle(Particle.END_ROD, var5, 22, 0.4, 0.5, 0.4, 0.01);
                     break;
                  case "AMBER_WARP":
                     var1.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, var4, 0, false, false, true));
                     var1.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, var4, 0, false, false, true));
                     var6.spawnParticle(Particle.WAX_ON, var5, 24, 0.45, 0.45, 0.45, 0.02);
                     break;
                  case "COLD_FOG":
                     var1.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, var4, 0, false, false, true));
                     var6.spawnParticle(Particle.CLOUD, var5, 26, 0.55, 0.35, 0.55, 0.02);
                     break;
                  case "PIXEL_WAVE":
                     var6.spawnParticle(Particle.WAX_OFF, var5, 20, 0.4, 0.4, 0.4, 0.01);
                     break;
                  case "CHROMATIC_SHIFT":
                     var1.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Math.min(var4, 240), 0, false, false, true));
                     var6.spawnParticle(Particle.ENTITY_EFFECT, var5, 20, 0.4, 0.4, 0.4, 1.0);
                     break;
                  case "STATIC_NOISE":
                     var6.spawnParticle(Particle.ASH, var5, 18, 0.4, 0.5, 0.4, 0.02);
                     break;
                  case "TUNNEL_VISION":
                     var1.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Math.min(var4, 60), 0, false, false, true));
                     var1.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Math.min(var4, 120), 0, false, false, true));
                     var6.spawnParticle(Particle.TRIAL_OMEN, var5, 16, 0.25, 0.25, 0.25, 0.0);
                     break;
                  default:
                     this.plugin.getLogger().fine("Unknown visual effect id: " + var2);
               }
            }
         }
      }
   }
}
