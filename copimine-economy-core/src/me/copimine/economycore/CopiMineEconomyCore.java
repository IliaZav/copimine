package me.copimine.economycore;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import javax.crypto.SecretKeyFactory;
import javax.crypto.Mac;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import org.bukkit.util.Transformation;

public final class CopiMineEconomyCore extends JavaPlugin implements Listener {
    private static final Pattern SAFE_SCHEMA = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final int PIN_MAX_ATTEMPTS = 5;
    private static final long PIN_LOCK_SECONDS = 900L;
    private static final long PIN_ATTEMPT_WINDOW_SECONDS = 900L;
    private static final int MODEL_ATM_TERMINAL = 12002;
    private static final int LEGACY_AR_SIGNATURE_VERSION = 2;
    private static final int OFFICIAL_AR_SIGNATURE_VERSION = 3;
    private static final int OFFICIAL_AR_FUNGIBLE_SIGNATURE_VERSION = OFFICIAL_AR_SIGNATURE_VERSION;
    private static final String AR_FUNGIBLE_SERIAL_PREFIX = "AR_FUNGIBLE:";
    // Preserve the legacy id for compatibility with existing rows and ledgers.
    private static final String TREASURY_ACCOUNT_ID = "PRESIDENT_BUDGET";
    private static final String TREASURY_ACCOUNT_LABEL = "Казна CopiMine";
    private static final String TREASURY_ACCOUNT_TYPE = "TREASURY";
    private static final Set<Long> DONATION_PACKS = Set.of(50L, 100L, 250L, 500L, 1000L);
    private static final long DONATION_SESSION_TTL_MS = 15L * 60L * 1000L;
    private static final String PENDING_AR_SETTLEMENT_STATUS_DEBITED = "DEBITED";
    private static final String PENDING_AR_SETTLEMENT_STATUS_PENDING = "PENDING";
    private static final String PENDING_AR_SETTLEMENT_STATUS_DELIVERING = "DELIVERING";
    private static final String PENDING_AR_SETTLEMENT_STATUS_DELIVERED = "DELIVERED";
    private static final String PENDING_AR_SETTLEMENT_STATUS_REVIEW = "DELIVERY_REVIEW";
    private static final String PENDING_AR_SETTLEMENT_TYPE_WITHDRAW_DELIVERY = "WITHDRAW_DELIVERY";
    private static final String PENDING_AR_SETTLEMENT_TYPE_DEPOSIT_RESTORE = "DEPOSIT_RESTORE";
    private static final String AR_DEPOSIT_STATUS_CREATED = "CREATED";
    private static final String AR_DEPOSIT_STATUS_PHYSICAL_REMOVED = "PHYSICAL_REMOVED";
    private static final String AR_DEPOSIT_STATUS_COMMITTED = "COMMITTED";
    private static final String AR_DEPOSIT_STATUS_RESTORE_PENDING = "RESTORE_PENDING";
    private static final String AR_DEPOSIT_STATUS_CANCELLED = "CANCELLED";
    private static final String AR_DEPOSIT_STATUS_REVIEW = "REVIEW";
    private static final String AR_DEPOSIT_STATUS_DEPOSITED = "DEPOSITED";

    private DbSettings db;
    private ThreadPoolExecutor dbExecutor;
    private SimpleConnectionPool connectionPool;
    /**
     * PostgreSQL/schema work is deliberately initialized off the Bukkit
     * thread.  Services are registered immediately so dependants can resolve
     * the bridge, but every JDBC workflow is queued behind this startup task.
     */
    private final AtomicBoolean databaseReady = new AtomicBoolean(false);
    private final AtomicBoolean databaseInitializing = new AtomicBoolean(false);
    private final AtomicBoolean startupFailed = new AtomicBoolean(false);
    private final AtomicBoolean schemaReady = new AtomicBoolean(false);
    private volatile String startupError = "DATABASE_STARTING";
    private volatile CompletableFuture<Void> databaseStartupFuture = CompletableFuture.completedFuture(null);
    private Path pendingArSettlementJournalPath;
    private final Object pendingArSettlementJournalLock = new Object();
    private final Map<UUID, AtmPinSession> atmPinSessions = new ConcurrentHashMap<>();
    private final Map<UUID, TaxStatusCache> taxStatusCache = new ConcurrentHashMap<>();
    /** Read-only president/treasury role cache; event handlers never query JDBC. */
    private final Map<UUID, Boolean> treasuryAccessCache = new ConcurrentHashMap<>();
    private final Set<UUID> atmPinRefreshBypass = ConcurrentHashMap.newKeySet();
    private final Set<BlockKey> activeAtmBlocks = ConcurrentHashMap.newKeySet();
    private final Map<BlockKey, String> atmIdsByBlock = new ConcurrentHashMap<>();
    private final Map<BlockKey, String> atmExpectedMaterials = new ConcurrentHashMap<>();
    private NamespacedKey visualEntityTypeKey;
    private NamespacedKey visualKindKey;
    private NamespacedKey visualLinkedIdKey;
    private NamespacedKey visualModelIdKey;
    private NamespacedKey officialArSerialKey;
    private NamespacedKey officialArSignatureKey;
    private NamespacedKey officialArDenominationKey;
    private NamespacedKey officialArSignatureVersionKey;
    private byte[] officialArSigningSecret;
    private BukkitTask atmAnchorGuardTask;

    private final BankService bankService = new BankServiceImpl();
    private final PinService pinService = new PinServiceImpl();
    private final AtmService atmService = new AtmServiceImpl();
    private final LedgerService ledgerService = new LedgerServiceImpl();
    private final OfficialArService officialArService = new OfficialArServiceImpl();
    private final DonationBalanceServiceImpl donationBalanceService = new DonationBalanceServiceImpl();
    private final DonationPaymentServiceImpl donationPaymentService = new DonationPaymentServiceImpl();
    private final DonationPurchaseServiceImpl donationPurchaseService = new DonationPurchaseServiceImpl();
    private final EconomyService economyService = new EconomyServiceImpl();
    private final ArtifactsBridge artifactsBridge = new ArtifactsBridgeImpl();

    public interface EconomyService {
        BankService bankService();
        PinService pinService();
        AtmService atmService();
        LedgerService ledgerService();
        DonationBalanceService donationBalanceService();
        DonationPaymentService donationPaymentService();
        DonationPurchaseService donationPurchaseService();
    }

    public interface BankService {
        PinStatus pinStatus(UUID playerUuid);
        long balance(UUID playerUuid, String playerName);
        CompletableFuture<Long> balanceAsync(UUID playerUuid, String playerName);
        TxnResult charge(UUID playerUuid, String playerName, long amount, String pin, String idempotencyKey, String action, String details);
        CompletableFuture<TxnResult> chargeAsync(UUID playerUuid, String playerName, long amount, String pin, String idempotencyKey, String action, String details);
        TxnResult refund(UUID playerUuid, String playerName, long amount, String idempotencyKey, String action, String details);
        TxnResult transferWithPin(UUID fromUuid, String fromName, UUID toUuid, String toName, long amount, String pin, String idempotencyKey, String action, String details);
        CompletableFuture<TxnResult> transferWithPinAsync(UUID fromUuid, String fromName, UUID toUuid, String toName, long amount, String pin, String idempotencyKey, String action, String details);
        TxnResult transferToAccount(UUID playerUuid, String playerName, String pin, String toAccountId, String toOwnerUuid, String toOwnerName, long amount, String idempotencyKey, String action, String details);
        TxnResult credit(UUID toUuid, String toName, long amount, String idempotencyKey, String action, String details);
        CompletableFuture<TxnResult> creditAsync(UUID toUuid, String toName, long amount, String idempotencyKey, String action, String details);
    }

    public interface PinService {
        PinStatus pinStatus(UUID playerUuid);
        boolean verify(UUID playerUuid, String pin) throws Exception;
    }

    public interface AtmService {
        boolean isAtmBlock(Block block);
        String atmId(Block block);
        void openAtm(Player player, String atmId) throws Exception;
        void openAdminHub(Player player) throws Exception;
        void openAtmDirectory(Player player) throws Exception;
        String createAtTarget(Player player) throws Exception;
        String archive(Player actor, String atmId) throws Exception;
    }

    public interface LedgerService {
        void pluginEvent(String source, String eventType, String actor, String target, String details);
    }

    public interface OfficialArService {
        boolean isOfficialAr(ItemStack stack);
        int countOfficialAr(Inventory inventory);
        CompletableFuture<Boolean> prepareIssuanceAsync(String issuanceKey, Material material, int amount, String source);
        ItemStack createPreparedStack(Material material, int amount, String source);
        ItemStack normalizeStack(ItemStack stack);
        void normalizePlayer(Player player);
    }

    public interface DonationBalanceService {
        long balance(UUID playerUuid, String playerName);
        CompletableFuture<Long> balanceAsync(UUID playerUuid, String playerName);
        TxnResult add(UUID playerUuid, String playerName, long amount, String reason, String actor, String source, String idempotencyKey);
        CompletableFuture<TxnResult> addAsync(UUID playerUuid, String playerName, long amount, String reason, String actor, String source, String idempotencyKey);
        TxnResult subtract(UUID playerUuid, String playerName, long amount, String reason, String actor, String source, String idempotencyKey);
        CompletableFuture<TxnResult> subtractAsync(UUID playerUuid, String playerName, long amount, String reason, String actor, String source, String idempotencyKey);
        CompletableFuture<List<Map<String, Object>>> ledgerAsync(UUID playerUuid, int limit);
    }

    public interface DonationPaymentService {
        Set<Long> allowedPacks();
        CompletableFuture<Map<String, Object>> createSessionAsync(UUID playerUuid, String playerName, long amountRub, String actor, String source, String idempotencyKey);
        CompletableFuture<Map<String, Object>> sessionAsync(UUID playerUuid, String sessionId);
        CompletableFuture<Map<String, Object>> markPaidAsync(String sessionId, String actor, String idempotencyKey);
        CompletableFuture<Map<String, Object>> cancelAsync(String sessionId, String actor);
    }

    public interface TaxService {
        TaxStatus status(UUID playerUuid);
        CompletableFuture<TaxStatus> statusAsync(UUID playerUuid);
        void openPayment(Player player);
    }

    /** Typed optional API exported through Bukkit's service registry. */
    public interface ElectionRuntimeService {
        Map<String, Object> activePresidentRevenueProfile();
        CompletableFuture<Map<String, Object>> grantTaxClockExemption(UUID playerUuid, String playerName, String artifactInstanceId);
    }

    public record TaxStatus(boolean available, boolean active, boolean paid, boolean subscription,
                            long validUntilMillis, long periodHours, long amount) {
        public static TaxStatus inactive() {
            return new TaxStatus(true, false, false, false, 0L, 0L, 0L);
        }

        public static TaxStatus unavailable() {
            return new TaxStatus(false, false, false, false, 0L, 0L, 0L);
        }
    }

    public interface DonationPurchaseService {
        CompletableFuture<Map<String, Object>> createTestPurchaseAsync(UUID playerUuid, String playerName, String itemId, long price, String actor);
        CompletableFuture<Map<String, Object>> createAdminGiftAsync(UUID playerUuid, String playerName, String itemId, String actor, String idempotencyKey);
        CompletableFuture<Map<String, Object>> purchaseIntentAsync(UUID playerUuid, String playerName, String itemId, long priceDonation, String actor, String source, String idempotencyKey);
        CompletableFuture<Map<String, Object>> createClaimAsync(UUID playerUuid, String itemId, long amount, String actor, String purchaseId);
        CompletableFuture<List<Map<String, Object>>> getUnclaimedItemsAsync(UUID playerUuid, int limit);
        CompletableFuture<Boolean> reserveClaimAsync(UUID playerUuid, String claimId);
        CompletableFuture<Boolean> markClaimDeliveringAsync(UUID playerUuid, String claimId);
        CompletableFuture<Boolean> completeClaimAsync(UUID playerUuid, String claimId);
        CompletableFuture<Boolean> completeClaimByPurchaseAsync(UUID playerUuid, String purchaseId);
        CompletableFuture<Boolean> markClaimDeliveryReviewAsync(UUID playerUuid, String claimId);
        CompletableFuture<Boolean> releaseClaimAsync(UUID playerUuid, String claimId);
    }

    public interface ArtifactsBridge {
        PinStatus pinStatus(UUID playerUuid);
        CompletableFuture<PinStatus> pinStatusAsync(UUID playerUuid);
        TxnResult charge(UUID playerUuid, String playerName, long amount, String pin, String idempotencyKey, String action, String details);
        TxnResult refund(UUID playerUuid, String playerName, long amount, String idempotencyKey, String action, String details);
        TxnResult credit(UUID playerUuid, String playerName, long amount, String idempotencyKey, String action, String details);
        TxnResult creditAccount(String accountId, String ownerUuid, String ownerName, long amount, String idempotencyKey, String action, String details);
        TxnResult transferToAccount(UUID playerUuid, String playerName, String pin, String toAccountId, String toOwnerUuid, String toOwnerName, long amount, String idempotencyKey, String action, String details);
        TxnResult transferFromAccount(String fromAccountId, String fromOwnerUuid, String fromOwnerName, UUID toUuid, String toName, long amount, String idempotencyKey, String action, String details);
        TxnResult stealFromPlayerAccount(UUID fromUuid, String fromName, UUID toUuid, String toName, long amount, String idempotencyKey, String action, String details);
        /**
         * Returns charged shop transfers that have no matching artifact row.
         * The economy plugin owns this recovery query so feature plugins never
         * read bank tables directly.
         */
        List<Map<String, Object>> findOrphanedArtifactShopTransfers(int limit);
        long balance(UUID playerUuid, String playerName);
        Health health(UUID playerUuid, String context);
    }

    public static final class PinStatus {
        public final boolean configured;
        public final boolean mustChange;
        public final long lockedSeconds;

        public PinStatus(boolean configured, boolean mustChange, long lockedSeconds) {
            this.configured = configured;
            this.mustChange = mustChange;
            this.lockedSeconds = lockedSeconds;
        }
    }

    public static final class TxnResult {
        public final boolean ok;
        public final String code;
        public final String message;
        public final long balanceAfter;
        public final String txId;

        public TxnResult(boolean ok, String code, String message, long balanceAfter, String txId) {
            this.ok = ok;
            this.code = code;
            this.message = message;
            this.balanceAfter = balanceAfter;
            this.txId = txId;
        }
    }

    public static final class Health {
        public final boolean bridgeReady;
        public final boolean postgresReady;
        public final boolean pinReady;
        public final long balance;
        public final String context;
        public final String lastError;

        public Health(boolean bridgeReady, boolean postgresReady, boolean pinReady, long balance, String context, String lastError) {
            this.bridgeReady = bridgeReady;
            this.postgresReady = postgresReady;
            this.pinReady = pinReady;
            this.balance = balance;
            this.context = context;
            this.lastError = lastError;
        }
    }

    private record BlockKey(String world, int x, int y, int z) {}
    private record PendingArSettlement(String id, UUID playerUuid, String playerName, long amount, String settlementType, String reason) {}
    private record PendingArReservation(String token, List<String> ids) {}
    private record SettlementResult(boolean accepted, String idempotencyKey, String message) {}
    private record ArFragment(int slot, ItemStack snapshot) {}
    private record SerializedArFragment(int slot, String serial, Material material, int amount) {}
    private record ArDepositIntent(String id, UUID playerUuid, String playerName, String accountId,
                                   String scope, String atmId, long amount, String idempotencyKey,
                                   List<SerializedArFragment> fragments, String status) {}

    @Override
    public void onEnable() {
        visualEntityTypeKey = new NamespacedKey(this, "visual_entity_type");
        visualKindKey = new NamespacedKey(this, "visual_kind");
        visualLinkedIdKey = new NamespacedKey(this, "visual_linked_id");
        visualModelIdKey = new NamespacedKey(this, "visual_model_id");
        officialArSerialKey = new NamespacedKey(this, "ar_serial");
        officialArSignatureKey = new NamespacedKey(this, "ar_signature");
        officialArDenominationKey = new NamespacedKey(this, "ar_denomination");
        officialArSignatureVersionKey = new NamespacedKey(this, "ar_signature_version");
        officialArSigningSecret = loadOrCreateArSigningSecret();
        pendingArSettlementJournalPath = getDataFolder().toPath().resolve("pending-ar-settlements.tsv");
        try {
            Files.createDirectories(pendingArSettlementJournalPath.getParent());
        } catch (Exception error) {
            getLogger().warning("Unable to prepare pending AR settlement journal: " + safeError(error));
        }
        int dbThreads = Math.max(2, Math.min(6, Runtime.getRuntime().availableProcessors()));
        dbExecutor = new ThreadPoolExecutor(dbThreads, dbThreads, 30L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(512), task -> {
                    Thread thread = new Thread(task, "copimine-economy-db");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        databaseReady.set(false);
        startupFailed.set(false);
        databaseInitializing.set(true);
        schemaReady.set(false);
        startupError = "DATABASE_STARTING";
        databaseStartupFuture = new CompletableFuture<>();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getServicesManager().register(EconomyService.class, economyService, this, ServicePriority.Normal);
        getServer().getServicesManager().register(BankService.class, bankService, this, ServicePriority.Normal);
        getServer().getServicesManager().register(PinService.class, pinService, this, ServicePriority.Normal);
        getServer().getServicesManager().register(AtmService.class, atmService, this, ServicePriority.Normal);
        getServer().getServicesManager().register(LedgerService.class, ledgerService, this, ServicePriority.Normal);
        getServer().getServicesManager().register(OfficialArService.class, officialArService, this, ServicePriority.Normal);
        getServer().getServicesManager().register(DonationBalanceService.class, donationBalanceService, this, ServicePriority.Normal);
        getServer().getServicesManager().register(DonationPaymentService.class, donationPaymentService, this, ServicePriority.Normal);
        getServer().getServicesManager().register(DonationPurchaseService.class, donationPurchaseService, this, ServicePriority.Normal);
        startDatabaseInitializationAsync(dbThreads);
    }

    /**
     * Load the environment, create the bounded connection pool and run schema
     * migrations on the economy executor.  This keeps plugin startup
     * responsive even when PostgreSQL is slow or temporarily unavailable.
     */
    private void startDatabaseInitializationAsync(int dbThreads) {
        try {
            dbExecutor.execute(() -> {
                try {
                    Class.forName("org.postgresql.Driver");
                    DbSettings loaded = loadDbSettings();
                    db = loaded;
                    connectionPool = new SimpleConnectionPool(loaded, Math.max(2, Math.min(8, dbThreads + 2)));
                    ensureSchema();
                    int interruptedSettlements = quarantineInterruptedPendingArSettlements();
                    if (interruptedSettlements > 0) {
                        getLogger().warning("Quarantined " + interruptedSettlements + " interrupted pending AR delivery record(s) for manual review.");
                    }
                    loadAtmCache();
                    flushPendingArSettlementJournal();
                    startupError = "";
                    databaseReady.set(true);
                    databaseInitializing.set(false);
                    databaseStartupFuture.complete(null);
                    Bukkit.getScheduler().runTask(this, this::startReadyTasks);
                    getLogger().info("CopiMineEconomyCore PostgreSQL is ready.");
                } catch (Exception error) {
                    startupError = safeError(error);
                    startupFailed.set(true);
                    databaseInitializing.set(false);
                    databaseStartupFuture.completeExceptionally(error);
                    getLogger().severe("CopiMineEconomyCore PostgreSQL init failed: " + startupError);
                    Bukkit.getScheduler().runTask(this, () -> {
                        if (isEnabled()) {
                            getServer().getPluginManager().disablePlugin(this);
                        }
                    });
                }
            });
        } catch (RejectedExecutionException rejected) {
            startupError = "DATABASE_EXECUTOR_REJECTED";
            startupFailed.set(true);
            databaseInitializing.set(false);
            databaseStartupFuture.completeExceptionally(rejected);
            getLogger().severe("CopiMineEconomyCore PostgreSQL init could not be scheduled.");
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    /** Starts Bukkit-only tasks after the asynchronous database bootstrap. */
    private void startReadyTasks() {
        if (!isEnabled() || !databaseReady.get() || startupFailed.get()) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                repairProtectedBlockVisuals();
            } catch (Exception error) {
                getLogger().warning("ATM visual repair: " + safeError(error));
            }
        }, 20L);
        Bukkit.getScheduler().runTaskLater(this,
                () -> Bukkit.getOnlinePlayers().forEach(player -> {
                    processPendingArSettlements(player, false);
                    processArDepositIntents(player, false);
                }), 40L);
        Bukkit.getScheduler().runTaskTimer(this, this::refreshOpenTaxButtons, 20L, 20L);
        atmAnchorGuardTask = Bukkit.getScheduler().runTaskTimer(this, this::guardAtmAnchors, 40L, 40L);
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> refreshTreasuryAccessAsync(), 20L, 100L);
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshTreasuryAccessAsync(player.getUniqueId());
        }
    }

    @Override
    public void onDisable() {
        databaseReady.set(false);
        databaseInitializing.set(false);
        databaseStartupFuture.completeExceptionally(new IllegalStateException("CopiMineEconomyCore is shutting down."));
        Bukkit.getServicesManager().unregisterAll(this);
        if (atmAnchorGuardTask != null) {
            atmAnchorGuardTask.cancel();
            atmAnchorGuardTask = null;
        }
        if (dbExecutor != null) {
            dbExecutor.shutdown();
            try {
                if (!dbExecutor.awaitTermination(5L, TimeUnit.SECONDS)) {
                    List<Runnable> dropped = dbExecutor.shutdownNow();
                    if (!dropped.isEmpty()) {
                        // queuePendingArSettlement() writes a durable journal
                        // before it submits its DB insert, so a dropped task
                        // remains recoverable on the next startup.  Keep the
                        // count visible instead of silently discarding queued
                        // work while the connection pool is being closed.
                        getLogger().warning("Economy DB executor forced down with " + dropped.size() + " queued task(s); durable settlement journal/recovery rows will be replayed on startup.");
                    }
                    if (!dbExecutor.awaitTermination(2L, TimeUnit.SECONDS)) {
                        getLogger().warning("Economy DB executor is still running after forced shutdown.");
                    }
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                List<Runnable> dropped = dbExecutor.shutdownNow();
                if (!dropped.isEmpty()) {
                    getLogger().warning("Economy DB executor interrupted with " + dropped.size() + " queued task(s); settlement recovery remains journal-backed.");
                }
            }
        }
        if (connectionPool != null) {
            connectionPool.close();
            connectionPool = null;
        }
        taxStatusCache.clear();
        treasuryAccessCache.clear();
    }

    public EconomyService economyService() {
        return economyService;
    }

    public BankService bankService() {
        return bankService;
    }

    public PinService pinService() {
        return pinService;
    }

    public AtmService atmService() {
        return atmService;
    }

    public OfficialArService officialArService() {
        return officialArService;
    }

    public ArtifactsBridge artifactsBridge() {
        return artifactsBridge;
    }

    public DonationBalanceService donationBalanceService() {
        return donationBalanceService;
    }

    public DonationPaymentService donationPaymentService() {
        return donationPaymentService;
    }

    public DonationPurchaseService donationPurchaseService() {
        return donationPurchaseService;
    }

    public void openAdminEconomyHub(Player player) throws Exception {
        if (!requireEconomyAdmin(player)) {
            return;
        }
        MenuHolder holder = new MenuHolder("economy:hub", "");
        Inventory inventory = holder.create(27, color("&b&lЭкономика CopiMine"));
        button(holder, inventory, 11, Material.GOLD_BLOCK, "&bБанкоматы", List.of(
                "&7Список активных ATM и создание нового блока."
        ), "economy:atms");
        button(holder, inventory, 13, Material.ENDER_CHEST, "&fСводка банка", List.of(
                "&7Баланс, PIN, банкоматы и официальный AR."
        ), "economy:summary");
        button(holder, inventory, 15, Material.ARROW, "&aОбновить", List.of(
                "&7Пересобрать список банкоматов."
        ), "economy:atms");
        player.openInventory(inventory);
    }

    public void openAtmDirectory(Player player) throws Exception {
        if (!requireEconomyAdmin(player)) {
            return;
        }
        openBankAtms(player);
    }

    private BlockKey blockKey(Block block) {
        return new BlockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    private BlockKey blockKey(String world, int x, int y, int z) {
        return new BlockKey(world, x, y, z);
    }

    private void loadAtmCache() throws Exception {
        activeAtmBlocks.clear();
        atmIdsByBlock.clear();
        atmExpectedMaterials.clear();
        for (Map<String, Object> row : queryList("SELECT id,world,x,y,z,COALESCE(expected_material,'') AS expected_material FROM ar_atms WHERE active=1")) {
            BlockKey key = blockKey(string(row.get("world")), intValue(row.get("x")), intValue(row.get("y")), intValue(row.get("z")));
            activeAtmBlocks.add(key);
            atmIdsByBlock.put(key, string(row.get("id")));
            String expected = string(row.get("expected_material"));
            if (!expected.isBlank()) {
                atmExpectedMaterials.put(key, expected);
            }
        }
    }

    private void cacheAtm(String atmId, Block block) {
        BlockKey key = blockKey(block);
        activeAtmBlocks.add(key);
        atmIdsByBlock.put(key, atmId);
        if (block != null && block.getType() != Material.AIR) {
            atmExpectedMaterials.put(key, block.getType().name());
        }
    }

    private void evictAtmCache(String atmId) {
        atmIdsByBlock.entrySet().removeIf(entry -> {
            boolean remove = atmId.equals(entry.getValue());
            if (remove) {
                activeAtmBlocks.remove(entry.getKey());
                atmExpectedMaterials.remove(entry.getKey());
            }
            return remove;
        });
    }

    /** Keep ATM interaction/cache state tied to an actual loaded world block. */
    private void guardAtmAnchors() {
        for (Map.Entry<BlockKey, String> entry : new ArrayList<>(atmIdsByBlock.entrySet())) {
            BlockKey key = entry.getKey();
            World world = Bukkit.getWorld(key.world());
            if (world == null || !world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) {
                continue;
            }
            Material actualMaterial = world.getBlockAt(key.x(), key.y(), key.z()).getType();
            String expectedMaterial = first(atmExpectedMaterials.get(key), "");
            if (expectedMaterial.isBlank() && !actualMaterial.isAir()) {
                expectedMaterial = actualMaterial.name();
                atmExpectedMaterials.put(key, expectedMaterial);
                String finalExpectedMaterial = expectedMaterial;
                dbAsync("record ATM anchor material", () -> update(
                        "UPDATE ar_atms SET expected_material=? WHERE id=? AND active=1 AND COALESCE(expected_material,'')=''",
                        finalExpectedMaterial, entry.getValue()));
            }
            if (!actualMaterial.isAir() && (expectedMaterial.isBlank()
                    || actualMaterial.name().equalsIgnoreCase(expectedMaterial))) {
                continue;
            }
            String atmId = entry.getValue();
            evictAtmCache(atmId);
            try {
                cleanupProtectedBlockVisuals("ATM", atmId);
            } catch (Exception error) {
                getLogger().warning("ATM anchor visual cleanup: " + safeError(error));
            }
            dbAsync("archive missing ATM anchor", () -> update(
                    "UPDATE ar_atms SET active=0,archived_by='SYSTEM_ANCHOR_MISSING',archived_at=? WHERE id=? AND active=1",
                    now(), atmId));
            getLogger().warning("Archived ATM " + atmId + " because its anchor block is missing at "
                    + key.world() + ":" + key.x() + ":" + key.y() + ":" + key.z());
        }
    }

    public boolean isAtmBlock(Block block) {
        return block != null && activeAtmBlocks.contains(blockKey(block));
    }

    public String atmId(Block block) {
        return block == null ? "" : first(atmIdsByBlock.get(blockKey(block)), "");
    }

    public String createAtmFromTarget(Player player) throws Exception {
        if (!requireEconomyAdmin(player)) {
            return "";
        }
        return createBankAtmFromTargetAsync(player);
    }

    public String archiveAtm(Player actor, String atmId) throws Exception {
        if (!requireEconomyAdmin(actor)) {
            return "";
        }
        return archiveBankAtmAsync(actor, atmId);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        String cachedAtmId = atmId(event.getClickedBlock());
        if (cachedAtmId.isBlank()) {
            return;
        }
        event.setCancelled(true);
        openBankAtm(event.getPlayer(), cachedAtmId);
    }

    @EventHandler
    public void onInteractDisplay(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Entity entity = event.getRightClicked();
        if (!(entity instanceof ItemDisplay display)) {
            return;
        }
        PersistentDataContainer pdc = display.getPersistentDataContainer();
        if (!"PROTECTED_BLOCK_VISUAL".equals(pdc.get(visualEntityTypeKey, PersistentDataType.STRING))) {
            return;
        }
        if (!"ATM".equals(first(pdc.get(visualKindKey, PersistentDataType.STRING), ""))) {
            return;
        }
        String linkedId = first(pdc.get(visualLinkedIdKey, PersistentDataType.STRING), "");
        if (linkedId.isBlank()) {
            return;
        }
        event.setCancelled(true);
        openBankAtm(event.getPlayer(), linkedId);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isAtmBlock(event.getBlock())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!hasEconomyAdmin(player)) {
            player.sendMessage(color("&cЭтот банкомат защищён. Его может снять только администратор."));
            return;
        }
        String linkedAtmId = atmId(event.getBlock());
        if (linkedAtmId == null || linkedAtmId.isBlank()) {
            player.sendMessage(color("&cНе удалось определить банкомат для этого блока."));
            return;
        }
        openAtmDeleteConfirm(player, linkedAtmId);
    }

    /** Registered ATM blocks are stateful database records, never ordinary world blocks. */
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    public void onAtmBlockBurn(BlockBurnEvent event) {
        if (isAtmBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    public void onAtmBlockFade(BlockFadeEvent event) {
        if (isAtmBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    public void onAtmBlockIgnite(BlockIgniteEvent event) {
        if (isAtmBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    public void onAtmBlockPhysics(BlockPhysicsEvent event) {
        if (isAtmBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    public void onAtmBlockForm(EntityBlockFormEvent event) {
        if (isAtmBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    public void onAtmBlockFlow(BlockFromToEvent event) {
        if (isAtmBlock(event.getBlock()) || isAtmBlock(event.getToBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    public void onAtmBlockExplode(BlockExplodeEvent event) {
        if (isAtmBlock(event.getBlock())) {
            event.setCancelled(true);
            return;
        }
        event.blockList().removeIf(this::isAtmBlock);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    public void onAtmEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isAtmBlock);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    public void onAtmBlockPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isAtmBlock)
                || isAtmBlock(event.getBlock().getRelative(event.getDirection(), event.getBlocks().size() + 1))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    public void onAtmBlockPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isAtmBlock)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    public void onOfficialArPlace(BlockPlaceEvent event) {
        if (!isOfficialAr(event.getItemInHand())) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(color("&cОфициальный AR нельзя размещать в мире. Используйте банкомат для внесения."));
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    public void onOfficialArCreative(InventoryCreativeEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack clicked = event.getClickedInventory() == null || event.getSlot() < 0 || event.getSlot() >= event.getClickedInventory().getSize()
                ? null : event.getClickedInventory().getItem(event.getSlot());
        ItemStack hotbar = event.getHotbarButton() >= 0 && event.getHotbarButton() < 9
                ? player.getInventory().getItem(event.getHotbarButton()) : null;
        if (containsOfficialAr(event.getCursor()) || containsOfficialAr(event.getCurrentItem())
                || containsOfficialAr(clicked) || containsOfficialAr(hotbar)
                || containsOfficialAr(player.getInventory().getItemInOffHand())) {
            event.setCancelled(true);
            player.updateInventory();
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    public void onOfficialArInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (officialArTouchesContainer(event)) {
            event.setCancelled(true);
            player.updateInventory();
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    public void onOfficialArInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !containsOfficialAr(event.getOldCursor())) {
            return;
        }
        if (event.getRawSlots().stream().anyMatch(slot -> slot < event.getView().getTopInventory().getSize())) {
            event.setCancelled(true);
            player.updateInventory();
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    public void onOfficialArInventoryMove(InventoryMoveItemEvent event) {
        if (containsOfficialAr(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    public void onOfficialArInventoryPickup(InventoryPickupItemEvent event) {
        if (containsOfficialAr(event.getItem().getItemStack())) {
            event.setCancelled(true);
        }
    }

    /**
     * EconomyCore is the sole owner of the physical lifecycle of certified AR.
     * AdminPlus must not retag, delete, merge or otherwise mutate these stacks.
     * Player pickup/drop remains allowed, while every non-player transport and
     * destructive entity path is fail-closed so an item cannot disappear
     * without a ledger operation.
     */
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    public void onOfficialArPickup(EntityPickupItemEvent event) {
        if (!containsOfficialAr(event.getItem().getItemStack())) {
            return;
        }
        if (!(event.getEntity() instanceof Player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    public void onOfficialArDrop(PlayerDropItemEvent event) {
        // Certified AR is a bank instrument, not a world entity.  Refuse the
        // drop instead of creating an unlimited-lifetime entity that can be
        // copied, cleared by another plugin, or left on the ground forever.
        if (containsOfficialAr(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(color("&eОфициальный AR нельзя выбрасывать. Внесите его через банкомат."));
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    public void onOfficialArDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Item item && containsOfficialAr(item.getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    public void onOfficialArDespawn(ItemDespawnEvent event) {
        if (containsOfficialAr(event.getEntity().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    public void onOfficialArMerge(ItemMergeEvent event) {
        if (containsOfficialAr(event.getEntity().getItemStack())
                || containsOfficialAr(event.getTarget().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    public void onOfficialArSpawn(ItemSpawnEvent event) {
        if (containsOfficialAr(event.getEntity().getItemStack())) {
            event.setCancelled(true);
            getLogger().warning("Blocked an official AR world spawn; certified AR must remain in inventories or mailbox delivery.");
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    public void onOfficialArSmelt(FurnaceSmeltEvent event) {
        if (isOfficialAr(event.getSource())) {
            event.setCancelled(true);
            event.setResult(new ItemStack(Material.AIR));
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    public void onOfficialArEntityInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack hand = event.getPlayer().getInventory().getItemInMainHand();
        if (containsOfficialAr(hand)
                && (event.getRightClicked() instanceof ItemFrame
                || event.getRightClicked() instanceof ArmorStand)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    public void onOfficialArArmorStand(PlayerArmorStandManipulateEvent event) {
        if (containsOfficialAr(event.getPlayerItem()) || containsOfficialAr(event.getArmorStandItem())) {
            event.setCancelled(true);
        }
    }

    private boolean officialArTouchesContainer(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top == null || top.getType() == org.bukkit.event.inventory.InventoryType.PLAYER) {
            return false;
        }
        // Check the clicked stack regardless of whether it came from the top
        // inventory or the player's bottom inventory.  In particular,
        // SHIFT-clicking an AR stack from the player inventory would otherwise
        // bypass the top-slot check and move it into a chest/hopper.
        if (containsOfficialAr(event.getCurrentItem())) {
            return true;
        }
        if (containsOfficialAr(event.getCursor())) {
            return true;
        }
        if (event.getClick() == ClickType.NUMBER_KEY) {
            int hotbarSlot = event.getHotbarButton();
            ItemStack hotbar = hotbarSlot >= 0 && hotbarSlot < 9
                    ? event.getWhoClicked().getInventory().getItem(hotbarSlot) : null;
            return containsOfficialAr(hotbar);
        }
        if (event.getClick() == ClickType.SWAP_OFFHAND) {
            return containsOfficialAr(event.getWhoClicked().getInventory().getItemInOffHand());
        }
        if (event.getClick() == ClickType.DOUBLE_CLICK) {
            // Double-click collection can pull matching stacks from both
            // inventories even when the clicked slot itself is empty.
            return java.util.Arrays.stream(top.getContents()).anyMatch(this::containsOfficialAr)
                    || java.util.Arrays.stream(event.getWhoClicked().getInventory().getContents()).anyMatch(this::containsOfficialAr)
                    || containsOfficialAr(event.getWhoClicked().getInventory().getItemInOffHand());
        }
        return false;
    }

    private boolean containsOfficialAr(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        if (isOfficialAr(stack)) {
            return true;
        }
        if (!stack.hasItemMeta() || stack.getItemMeta() == null) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof BundleMeta bundle && bundle.hasItems()) {
            return bundle.getItems().stream().anyMatch(this::containsOfficialAr);
        }
        if (meta instanceof BlockStateMeta stateMeta && stateMeta.getBlockState() instanceof Container container) {
            return java.util.Arrays.stream(container.getInventory().getContents()).anyMatch(this::containsOfficialAr);
        }
        return false;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        try {
            repairProtectedBlockVisuals(event.getChunk().getWorld().getName(), event.getChunk().getX(), event.getChunk().getZ());
        } catch (Exception error) {
            getLogger().warning("ATM visual chunk repair: " + safeError(error));
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        normalizeOfficialArItems(event.getPlayer());
        Bukkit.getScheduler().runTaskLater(this, () -> {
            processPendingArSettlements(event.getPlayer(), true);
            processArDepositIntents(event.getPlayer(), true);
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        atmPinSessions.remove(playerId);
        atmPinRefreshBypass.remove(playerId);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof MenuHolder menu && "atm:pin".equals(menu.id())) {
            UUID playerId = event.getPlayer().getUniqueId();
            Bukkit.getScheduler().runTaskLater(this, () -> {
                InventoryView nextView = event.getPlayer().getOpenInventory();
                boolean stillInsidePinPad = nextView.getTopInventory().getHolder() instanceof MenuHolder nextMenu
                        && "atm:pin".equals(nextMenu.id());
                atmPinRefreshBypass.remove(playerId);
                if (!stillInsidePinPad) {
                    atmPinSessions.remove(playerId);
                }
            }, 1L);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof MenuHolder menu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        String action = event.getClick() == ClickType.RIGHT ? menu.rightActions.get(event.getRawSlot()) : menu.actions.get(event.getRawSlot());
        if (action == null || action.isBlank()) {
            return;
        }
        try {
            handleMenuAction(player, menu, action);
        } catch (Exception error) {
            notifyEconomyBug(player, "menu", action, error, event.getCurrentItem(), player.getLocation());
            getLogger().warning("economy menu action=" + action + " player=" + player.getName() + " error=" + safeError(error));
        }
    }

    private void handleMenuAction(Player player, MenuHolder menu, String action) throws Exception {
        if (action.equals("economy:atms")) {
            if (!requireEconomyAdmin(player)) {
                return;
            }
            openBankAtms(player);
            return;
        }
        if (action.equals("economy:summary")) {
            if (!requireEconomyAdmin(player)) {
                return;
            }
            openEconomySummary(player);
            return;
        }
        if (action.equals("atm:create-target")) {
            if (!requireEconomyAdmin(player)) {
                return;
            }
            player.sendMessage(color(createBankAtmFromTargetAsync(player)));
            return;
        }
        if (action.startsWith("atm:open:")) {
            openBankAtm(player, action.substring("atm:open:".length()));
            return;
        }
        if (action.startsWith("atm:delete-confirm:")) {
            if (!requireEconomyAdmin(player)) {
                return;
            }
            openAtmDeleteConfirm(player, action.substring("atm:delete-confirm:".length()));
            return;
        }
        if (action.startsWith("atm:delete:")) {
            if (!requireEconomyAdmin(player)) {
                return;
            }
            player.sendMessage(color(archiveBankAtmAsync(player, action.substring("atm:delete:".length()))));
            return;
        }
        if (action.equals("atm:close")) {
            player.closeInventory();
            return;
        }
        if (action.startsWith("atm:deposit-hand:")) {
            String[] parts = action.split(":");
            player.sendMessage(color(depositArFromHandAsync(player, parts[2], parts.length > 3 ? parts[3] : "PERSONAL")));
            return;
        }
        if (action.startsWith("atm:deposit-all:")) {
            String[] parts = action.split(":");
            player.sendMessage(color(depositAllArAsync(player, parts[2], parts.length > 3 ? parts[3] : "PERSONAL")));
            return;
        }
        if (action.startsWith("atm:account:")) {
            String[] parts = action.split(":");
            openBankAtmAccount(player, parts[2], parts.length > 3 ? parts[3] : "PERSONAL");
            return;
        }
        if (action.startsWith("atm:balance:")) {
            String[] parts = action.split(":");
            openAtmBalancePreview(player, parts[2], parts.length > 3 ? parts[3] : "PERSONAL");
            return;
        }
        if (action.startsWith("atm:tax:")) {
            TaxService taxService = Bukkit.getServicesManager().load(TaxService.class);
            if (taxService == null) {
                player.sendMessage(color("&cНалоговый сервис сейчас недоступен."));
                return;
            }
            taxService.openPayment(player);
            return;
        }
        if (action.startsWith("atm:withdraw:")) {
            String[] parts = action.split(":");
            String scope = parts.length > 4 ? parts[3] : "PERSONAL";
            int amount = parseInt(parts.length > 4 ? parts[4] : parts[3], 0);
            openAtmPinPad(player, parts[2], scope, "WITHDRAW", amount, "", "", "");
            return;
        }
        if (action.startsWith("atm:targets:")) {
            String[] parts = action.split(":");
            openBankTransferTargets(player, parts[2], parts.length > 3 ? parts[3] : "PERSONAL");
            return;
        }
        if (action.startsWith("atm:target:")) {
            String[] parts = action.split(":");
            String scope = parts.length > 4 ? parts[3] : "PERSONAL";
            String targetUuid = parts.length > 4 ? parts[4] : parts[3];
            openBankTransferAmounts(player, parts[2], scope, targetUuid);
            return;
        }
        if (action.startsWith("atm:transfer:")) {
            String[] parts = action.split(":");
            String scope = parts.length > 5 ? parts[3] : "PERSONAL";
            String targetUuid = parts.length > 5 ? parts[4] : parts[3];
            int amount = parseInt(parts.length > 5 ? parts[5] : parts[4], 0);
            openAtmPinPad(player, parts[2], scope, "TRANSFER", amount, "", targetUuid, bankTargetName(targetUuid));
            return;
        }
        if (action.startsWith("pin:")) {
            handleAtmPinAction(player, action);
            return;
        }
        if (action.startsWith("nav:")) {
            String target = action.substring("nav:".length());
            if (target.equals("hub")) {
                if (!requireEconomyAdmin(player)) {
                    return;
                }
                openAdminEconomyHub(player);
            } else if (target.equals("atms")) {
                if (!requireEconomyAdmin(player)) {
                    return;
                }
                openBankAtms(player);
            }
        }
    }

    private void openAtmDeleteConfirm(Player player, String atmId) {
        if (!requireEconomyAdmin(player)) {
            return;
        }
        MenuHolder holder = new MenuHolder("economy:atm-delete", atmId);
        Inventory inventory = holder.create(27, color("&cУдалить банкомат"));
        button(holder, inventory, 11, Material.RED_WOOL, "&cДа, удалить", List.of(
                "&7Блок будет снят с учёта и удалён из экономики.",
                "&7Связанный visual тоже будет очищен."
        ), "atm:delete:" + atmId);
        button(holder, inventory, 13, Material.PAPER, "&fПодтверждение", List.of(
                "&7ATM ID: &f" + shortId(atmId),
                "&7Удаление затронет только этот банкомат."
        ), "");
        button(holder, inventory, 15, Material.ARROW, "&aОтмена", List.of(
                "&7Вернуться без изменений."
        ), "economy:atms");
        player.openInventory(inventory);
    }

    private void openEconomySummary(Player player) throws Exception {
        if (!requireEconomyAdmin(player)) {
            return;
        }
        UUID playerUuid = player.getUniqueId();
        String playerName = player.getName();
        dbFuture("open economy summary", () -> {
            long balance = bankService.balance(playerUuid, playerName);
            long atmCount = scalarLong("SELECT COUNT(*) FROM ar_atms WHERE active=1");
            Map<String, Object> data = new HashMap<>();
            data.put("balance", balance);
            data.put("atmCount", atmCount);
            return data;
        }).whenComplete((data, error) -> Bukkit.getScheduler().runTask(this, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (error != null) {
                player.sendMessage(color("&cНе удалось открыть сводку экономики."));
                getLogger().warning("open economy summary: " + safeError(error));
                return;
            }
            MenuHolder holder = new MenuHolder("economy:summary", "");
            Inventory inventory = holder.create(27, color("&b&lСводка экономики"));
            button(holder, inventory, 11, Material.EMERALD_BLOCK, "&aБаланс", List.of("&7На счёте: &f" + data.get("balance") + " AR"), "");
            button(holder, inventory, 13, Material.GOLD_BLOCK, "&fБанкоматы", List.of("&7Активных банкоматов: &f" + data.get("atmCount")), "economy:atms");
            button(holder, inventory, 15, Material.ARROW, "&aНазад", List.of(), "nav:hub");
             player.openInventory(inventory);
                     }));
     }

    private void openBankAtms(Player player) throws Exception {
        if (!requireEconomyAdmin(player)) {
            return;
        }
        dbFuture("open atm directory", () -> queryList("SELECT id,name,world,x,y,z,created_at FROM ar_atms WHERE active=1 ORDER BY created_at DESC LIMIT 28"))
                .whenComplete((rows, error) -> Bukkit.getScheduler().runTask(this, () -> {
                    if (!player.isOnline() || !hasEconomyAdmin(player)) {
                        return;
                    }
                    if (error != null) {
                        player.sendMessage(color("&cНе удалось открыть список банкоматов."));
                        getLogger().warning("open atm directory: " + safeError(error));
                        return;
                    }
                    MenuHolder holder = new MenuHolder("economy:atms", "");
                    Inventory inventory = holder.create(54, color("&b&lБанкоматы CopiMine"));
                    button(holder, inventory, 10, Material.GOLD_BLOCK, "&aСоздать по блоку", List.of(
                            "&7Смотри на блок и создай на нём банкомат."
                    ), "atm:create-target");
                    int slot = 19;
                    for (Map<String, Object> row : rows) {
                        if (slot >= 46) {
                            break;
                        }
                        String atmId = string(row.get("id"));
                        button(holder, inventory, slot++, Material.EMERALD, "&b" + first(string(row.get("name")), "Банкомат"),
                                List.of("&7ID: &f" + shortId(atmId), "&7Мир: &f" + string(row.get("world")), "&7Координаты: &f" + row.get("x") + ", " + row.get("y") + ", " + row.get("z"),
                                        "&7ЛКМ: открыть", "&7ПКМ: архивировать"), "atm:open:" + atmId, "atm:delete:" + atmId);
                    }
                    button(holder, inventory, 49, Material.ARROW, "&aНазад", List.of(), "nav:hub");
                    player.openInventory(inventory);
                }));
    }

    private String createBankAtmFromTarget(Player player) throws Exception {
        if (!requireEconomyAdmin(player)) {
            return "";
        }
        Block block = player.getTargetBlockExact(6);
        if (block == null) {
            return "&cСначала посмотри на блок банкомата.";
        }
        if (isAtmBlock(block)) {
            return "&eНа этом блоке уже есть банкомат.";
        }
        String id = "atm_" + UUID.randomUUID().toString().replace("-", "");
        long t = now();
        int created = updateCount("INSERT INTO ar_atms(id,world,x,y,z,expected_material,expected_model_id,expected_custom_model_data,name,active,created_by,created_at,archived_by,archived_at) VALUES(?,?,?,?,?,?,?,?,'Банкомат',1,?,?, '',0) ON CONFLICT (world,x,y,z) WHERE active=1 DO NOTHING",
                id, block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), block.getType().name(), "atm_terminal", MODEL_ATM_TERMINAL, player.getName(), t);
        if (created != 1) {
            return "&eНа этом блоке уже есть банкомат.";
        }
        cacheAtm(id, block);
        spawnOrReplaceProtectedBlockVisual(block.getLocation(), "ATM", id, Material.PAPER, MODEL_ATM_TERMINAL, "atm_terminal");
        pluginEvent("economy_core", "atm_created", player.getName(), id, "world=" + block.getWorld().getName());
        return "&aБанкомат создан.";
    }

    private String createBankAtmFromTargetAsync(Player player) throws Exception {
        if (!requireEconomyAdmin(player)) {
            return "";
        }
        Block block = player.getTargetBlockExact(6);
        if (block == null) {
            return "&cСначала посмотри на блок банкомата.";
        }
        if (isAtmBlock(block)) {
            return "&eНа этом блоке уже есть банкомат.";
        }
        String id = "atm_" + UUID.randomUUID().toString().replace("-", "");
        long t = now();
        String worldName = block.getWorld().getName();
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        Location location = block.getLocation();
        dbFuture("create atm", () -> {
            int created = updateCount("INSERT INTO ar_atms(id,world,x,y,z,expected_material,expected_model_id,expected_custom_model_data,name,active,created_by,created_at,archived_by,archived_at) VALUES(?,?,?,?,?,?,?,?,'Банкомат',1,?,?, '',0) ON CONFLICT (world,x,y,z) WHERE active=1 DO NOTHING",
                    id, worldName, x, y, z, location.getBlock().getType().name(), "atm_terminal", MODEL_ATM_TERMINAL, player.getName(), t);
            if (created != 1) {
                throw new IllegalStateException("ATM_EXISTS:");
            }
            pluginEvent("economy_core", "atm_created", player.getName(), id, "world=" + worldName);
            return id;
        }).whenComplete((createdId, error) -> Bukkit.getScheduler().runTask(this, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (error != null) {
                Throwable root = unwrap(error);
                String message = safeError(root);
                if (message.startsWith("ATM_EXISTS")) {
                    player.sendMessage(color("&eНа этом блоке уже есть активный банкомат."));
                    return;
                }
                player.sendMessage(color("&cНе удалось создать банкомат."));
                getLogger().warning("create atm: " + message);
                return;
            }
            cacheAtm(createdId, location.getBlock());
            try {
                spawnOrReplaceProtectedBlockVisual(location, "ATM", createdId, Material.PAPER, MODEL_ATM_TERMINAL, "atm_terminal");
            } catch (Exception visualError) {
                getLogger().warning("create atm visual: " + safeError(visualError));
            }
            player.sendMessage(color("&aБанкомат создан."));
            try {
                openBankAtms(player);
            } catch (Exception openError) {
                getLogger().warning("open atm directory after create: " + safeError(openError));
            }
        }));
        return "&7Создаём банкомат...";
    }

    private String archiveBankAtm(Player actor, String atmId) throws Exception {
        if (!requireEconomyAdmin(actor)) {
            return "";
        }
        int changed = tx(connection -> {
            update(connection, "UPDATE ar_atms SET active=0,archived_by=?,archived_at=? WHERE id=? AND active=1", actor.getName(), now(), atmId);
            cleanupProtectedBlockVisuals("ATM", atmId);
            return 1;
        });
        evictAtmCache(atmId);
        pluginEvent("economy_core", "atm_archived", actor.getName(), atmId, "");
        return changed > 0 ? "&aБанкомат снят." : "&eБанкомат уже был снят.";
    }

    private String archiveBankAtmAsync(Player actor, String atmId) throws Exception {
        if (!requireEconomyAdmin(actor)) {
            return "";
        }
        dbFuture("archive atm", () -> {
            int changed = tx(connection -> {
                Map<String, Object> row = queryOne(connection, "SELECT id FROM ar_atms WHERE id=? AND active=1 FOR UPDATE", atmId);
                if (row == null || row.isEmpty()) {
                    return 0;
                }
                update(connection, "UPDATE ar_atms SET active=0,archived_by=?,archived_at=? WHERE id=? AND active=1", actor.getName(), now(), atmId);
                return 1;
            });
            if (changed > 0) {
                pluginEvent("economy_core", "atm_archived", actor.getName(), atmId, "");
            }
            return changed;
        }).whenComplete((changed, error) -> Bukkit.getScheduler().runTask(this, () -> {
            if (!actor.isOnline()) {
                return;
            }
            if (error != null) {
                actor.sendMessage(color("&cНе удалось снять банкомат."));
                getLogger().warning("archive atm: " + safeError(error));
                return;
            }
            if (changed != null && changed > 0) {
                evictAtmCache(atmId);
                try {
                    cleanupProtectedBlockVisuals("ATM", atmId);
                } catch (Exception cleanupError) {
                    getLogger().warning("archive atm visual cleanup: " + safeError(cleanupError));
                }
                actor.sendMessage(color("&aБанкомат снят."));
            } else {
                actor.sendMessage(color("&eБанкомат уже был снят."));
            }
            try {
                openBankAtms(actor);
            } catch (Exception openError) {
                getLogger().warning("open atm directory after archive: " + safeError(openError));
            }
        }));
        return "&7Снимаем банкомат...";
    }

    private void openBankAtm(Player player, String atmId) {
        processPendingArSettlements(player, false);
        if (hasTreasuryAccess(player)) {
            MenuHolder holder = new MenuHolder("economy:atm-select", atmId);
            Inventory inventory = holder.create(27, color("&b&lБанк CopiMine"));
            button(holder, inventory, 11, Material.PLAYER_HEAD, "&bЛичный счёт",
                    List.of("&7Обычный банковский счёт и личный PIN."), "atm:account:" + atmId + ":PERSONAL");
            button(holder, inventory, 15, Material.GOLD_BLOCK, "&6Казна",
                    List.of("&7Отдельный счёт казны.", "&7Доступ только у президента и админов."), "atm:account:" + atmId + ":TREASURY");
            button(holder, inventory, 22, Material.BARRIER, "&7Закрыть", List.of(), "atm:close");
            holder.actions.put(4, "atm:balance:" + atmId + ":PERSONAL");
            player.openInventory(inventory);
            return;
        }
        openBankAtmAccount(player, atmId, "PERSONAL");
    }

    private void openBankAtmAccount(Player player, String atmId, String accountScope) {
        String scope = requestedAccountScope(player, accountScope);
        if (scope.isBlank()) {
            return;
        }
        if ("PERSONAL".equals(scope)) {
            openBankAtmLegacy(player, atmId);
            return;
        }
        dbFuture("open treasury atm", () -> {
            List<Map<String, Object>> rows = queryList("SELECT id,name FROM ar_atms WHERE id=? AND active=1 LIMIT 1", atmId);
            if (rows.isEmpty()) {
                return null;
            }
            Map<String, Object> treasury = tx(this::ensureTreasuryAccount);
            Map<String, Object> payload = new HashMap<>();
            payload.put("scope", "TREASURY");
            payload.put("name", first(string(rows.getFirst().get("name")), "Банкомат"));
            payload.put("balance", longValue(treasury.get("balance")));
            return payload;
        }).whenComplete((payload, error) -> Bukkit.getScheduler().runTask(this, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (error != null) {
                player.sendMessage(color("&cНе удалось открыть банкомат."));
                getLogger().warning("treasury ATM open: " + safeError(error));
                return;
            }
            if (payload == null) {
                player.sendMessage(color("&cБанкомат больше не активен."));
                return;
            }
            MenuHolder holder = new MenuHolder("economy:atm", atmId);
            holder.data.put("scope", "TREASURY");
            Inventory inventory = holder.create(54, color("&b&lБанк CopiMine"));
            button(holder, inventory, 4, Material.GOLD_BLOCK, "&b" + first(string(payload.get("name")), "Банкомат"),
                    List.of("&7Счёт: &fКазна CopiMine", "&7Баланс: &f" + payload.get("balance") + " AR", "&7ЛКМ: показать баланс в чат"), "atm:balance:" + atmId + ":TREASURY");
            button(holder, inventory, 10, Material.DIAMOND_ORE, "&aВнести предмет из руки", List.of("&7Берёт официальный AR из основной руки.", "&7PIN не требуется."), "atm:deposit-hand:" + atmId + ":TREASURY");
            button(holder, inventory, 12, Material.CHEST, "&aВнести весь AR", List.of("&7Берёт весь официальный AR из инвентаря.", "&7PIN не требуется."), "atm:deposit-all:" + atmId + ":TREASURY");
            button(holder, inventory, 14, Material.PLAYER_HEAD, "&bПеревести игроку", List.of("&7Банковский перевод с проверкой PIN казны."), "atm:targets:" + atmId + ":TREASURY");
            button(holder, inventory, 28, Material.EMERALD, "&eСнять 1 AR", List.of("&7Потребуется PIN казны."), "atm:withdraw:" + atmId + ":TREASURY:1");
            button(holder, inventory, 30, Material.EMERALD_BLOCK, "&eСнять 16 AR", List.of("&7Потребуется PIN казны."), "atm:withdraw:" + atmId + ":TREASURY:16");
            button(holder, inventory, 32, Material.DIAMOND_ORE, "&eСнять 64 AR", List.of("&7Потребуется PIN казны."), "atm:withdraw:" + atmId + ":TREASURY:64");
            button(holder, inventory, 49, Material.ARROW, "&aНазад", List.of(), "atm:open:" + atmId);
            player.openInventory(inventory);
        }));
    }

    private void openBankAtmLegacy(Player player, String atmId) {
        UUID playerUuid = player.getUniqueId();
        String playerName = player.getName();
        dbFuture("open atm", () -> {
            List<Map<String, Object>> rows = queryList("SELECT id,name FROM ar_atms WHERE id=? AND active=1 LIMIT 1", atmId);
            if (rows.isEmpty()) {
                return null;
            }
            ensureBankAccount(playerUuid.toString(), playerName);
            long balance = bankService.balance(playerUuid, playerName);
            Map<String, Object> payload = new HashMap<>();
            payload.put("name", first(string(rows.getFirst().get("name")), "Банкомат"));
            payload.put("balance", balance);
            return payload;
        }).whenComplete((payload, error) -> Bukkit.getScheduler().runTask(this, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (error != null) {
                player.sendMessage(color("&cНе удалось открыть банкомат."));
                getLogger().warning("ATM open: " + safeError(error));
                return;
            }
            if (payload == null) {
                player.sendMessage(color("&cБанкомат больше не активен."));
                return;
            }
            MenuHolder holder = new MenuHolder("economy:atm", atmId);
            Inventory inventory = holder.create(54, color("&b&lБанк CopiMine"));
            button(holder, inventory, 4, Material.GOLD_BLOCK, "&b" + first(string(payload.get("name")), "Банкомат"),
                    List.of("&7Баланс: &f" + payload.get("balance") + " AR", "&7ЛКМ: показать баланс в чат"), "atm:balance:" + atmId + ":PERSONAL");
            button(holder, inventory, 10, Material.DIAMOND_ORE, "&aВнести предмет из руки", List.of("&7Берёт официальный AR из основной руки.", "&7PIN не требуется."), "atm:deposit-hand:" + atmId);
            button(holder, inventory, 12, Material.CHEST, "&aВнести весь AR", List.of("&7Берёт весь официальный AR из инвентаря.", "&7PIN не требуется."), "atm:deposit-all:" + atmId);
            button(holder, inventory, 14, Material.PLAYER_HEAD, "&bПеревести игроку", List.of("&7Банковский перевод с проверкой PIN."), "atm:targets:" + atmId);
            button(holder, inventory, 28, Material.EMERALD, "&eСнять 1 AR", List.of("&7Потребуется PIN банка."), "atm:withdraw:" + atmId + ":1");
            button(holder, inventory, 30, Material.EMERALD_BLOCK, "&eСнять 16 AR", List.of("&7Потребуется PIN банка."), "atm:withdraw:" + atmId + ":16");
            button(holder, inventory, 32, Material.DIAMOND_ORE, "&eСнять 64 AR", List.of("&7Потребуется PIN банка."), "atm:withdraw:" + atmId + ":64");
            button(holder, inventory, 20, Material.GOLD_INGOT, "&6Налог: проверка...", List.of("&7Проверяю статус налога."), "atm:tax:" + atmId);
            button(holder, inventory, 49, Material.BARRIER, "&7Закрыть", List.of(), "atm:close");
            player.openInventory(inventory);
            refreshTaxButton(player, holder, atmId);
        }));
    }

    private void openAtmBalancePreview(Player player, String atmId, String accountScope) {
        String scope = requestedAccountScope(player, accountScope);
        if (scope.isBlank()) {
            return;
        }
        if ("TREASURY".equals(scope)) {
            dbFuture("atm treasury balance preview", () -> {
                Map<String, Object> treasury = tx(this::ensureTreasuryAccount);
                return longValue(treasury.get("balance"));
            }).whenComplete((balance, error) -> Bukkit.getScheduler().runTask(this, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (error != null) {
                    player.sendMessage(color("&cНе удалось получить баланс казны."));
                    getLogger().warning("ATM treasury balance preview: " + safeError(error));
                    return;
                }
                player.sendMessage(color("&6Казна CopiMine: &f" + balance + " AR"));
                openBankAtmAccount(player, atmId, "TREASURY");
            }));
            return;
        }
        UUID playerUuid = player.getUniqueId();
        String playerName = player.getName();
        dbFuture("atm personal balance preview", () -> {
            ensureBankAccount(playerUuid.toString(), playerName);
            return bankService.balance(playerUuid, playerName);
        }).whenComplete((balance, error) -> Bukkit.getScheduler().runTask(this, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (error != null) {
                player.sendMessage(color("&cНе удалось получить баланс счёта."));
                getLogger().warning("ATM personal balance preview: " + safeError(error));
                return;
            }
            player.sendMessage(color("&6Ваш баланс: &f" + balance + " AR"));
            openBankAtmAccount(player, atmId, "PERSONAL");
        }));
    }

    private void openBankTransferTargets(Player player, String atmId, String accountScope) throws Exception {
        String scope = requestedAccountScope(player, accountScope);
        if (scope.isBlank()) {
            return;
        }
        if ("PERSONAL".equals(scope)) {
            openBankTransferTargetsLegacy(player, atmId);
            return;
        }
        MenuHolder holder = new MenuHolder("economy:targets", atmId);
        holder.data.put("scope", scope);
        Inventory inventory = holder.create(54, color("&b&lКому перевести AR"));
        int slot = 10;
        for (Player target : Bukkit.getOnlinePlayers().stream().sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER)).toList()) {
            if (target.getUniqueId().equals(player.getUniqueId()) || slot >= 44) {
                continue;
            }
            button(holder, inventory, slot++, Material.PLAYER_HEAD, "&b" + target.getName(),
                    List.of("&7Онлайн: &fда", "&7UUID: &f" + shortId(target.getUniqueId().toString())), "atm:target:" + atmId + ":" + scope + ":" + target.getUniqueId());
        }
        button(holder, inventory, 49, Material.ARROW, "&aНазад", List.of(), "atm:account:" + atmId + ":" + scope);
        player.openInventory(inventory);
    }

    private void openBankTransferTargetsLegacy(Player player, String atmId) throws Exception {
        MenuHolder holder = new MenuHolder("economy:targets", atmId);
        Inventory inventory = holder.create(54, color("&b&lКому перевести AR"));
        int slot = 10;
        for (Player target : Bukkit.getOnlinePlayers().stream().sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER)).toList()) {
            if (target.getUniqueId().equals(player.getUniqueId()) || slot >= 44) {
                continue;
            }
            button(holder, inventory, slot++, Material.PLAYER_HEAD, "&b" + target.getName(),
                    List.of("&7Онлайн: &fда", "&7UUID: &f" + shortId(target.getUniqueId().toString())), "atm:target:" + atmId + ":" + target.getUniqueId());
        }
        button(holder, inventory, 49, Material.ARROW, "&aНазад", List.of(), "atm:open:" + atmId);
        player.openInventory(inventory);
    }

    private void openBankTransferAmounts(Player player, String atmId, String accountScope, String targetUuid) throws Exception {
        String scope = requestedAccountScope(player, accountScope);
        if (scope.isBlank()) {
            return;
        }
        if ("PERSONAL".equals(scope)) {
            openBankTransferAmountsLegacy(player, atmId, targetUuid);
            return;
        }
        MenuHolder holder = new MenuHolder("economy:amount", atmId);
        holder.data.put("scope", scope);
        Inventory inventory = holder.create(27, color("&b&lСумма перевода"));
        String targetName = bankTargetName(targetUuid);
        button(holder, inventory, 4, Material.PLAYER_HEAD, "&b" + first(targetName, targetUuid), List.of("&7Потребуется PIN казны."), "");
        button(holder, inventory, 11, Material.EMERALD, "&eПеревести 1 AR", List.of(), "atm:transfer:" + atmId + ":" + scope + ":" + targetUuid + ":1");
        button(holder, inventory, 13, Material.EMERALD_BLOCK, "&eПеревести 16 AR", List.of(), "atm:transfer:" + atmId + ":" + scope + ":" + targetUuid + ":16");
        button(holder, inventory, 15, Material.DIAMOND_ORE, "&eПеревести 64 AR", List.of(), "atm:transfer:" + atmId + ":" + scope + ":" + targetUuid + ":64");
        button(holder, inventory, 22, Material.ARROW, "&aНазад", List.of(), "atm:targets:" + atmId + ":" + scope);
        player.openInventory(inventory);
    }

    private void openBankTransferAmountsLegacy(Player player, String atmId, String targetUuid) throws Exception {
        MenuHolder holder = new MenuHolder("economy:amount", atmId);
        Inventory inventory = holder.create(27, color("&b&lСумма перевода"));
        String targetName = bankTargetName(targetUuid);
        button(holder, inventory, 4, Material.PLAYER_HEAD, "&b" + first(targetName, targetUuid), List.of("&7Потребуется PIN банка."), "");
        button(holder, inventory, 11, Material.EMERALD, "&eПеревести 1 AR", List.of(), "atm:transfer:" + atmId + ":" + targetUuid + ":1");
        button(holder, inventory, 13, Material.EMERALD_BLOCK, "&eПеревести 16 AR", List.of(), "atm:transfer:" + atmId + ":" + targetUuid + ":16");
        button(holder, inventory, 15, Material.DIAMOND_ORE, "&eПеревести 64 AR", List.of(), "atm:transfer:" + atmId + ":" + targetUuid + ":64");
        button(holder, inventory, 22, Material.ARROW, "&aНазад", List.of(), "atm:targets:" + atmId);
        player.openInventory(inventory);
    }

    private void openAtmPinPad(Player player, String atmId, String accountScope, String action, int amount, String pin, String targetUuid, String targetName) {
        String scope = requestedAccountScope(player, accountScope);
        if (scope.isBlank()) {
            return;
        }
        String masked = pin.isBlank() ? "* * * *" : pin.chars().mapToObj(i -> "*").reduce((a, b) -> a + " " + b).orElse("*");
        MenuHolder holder = new MenuHolder("atm:pin", atmId);
        Inventory inventory = holder.create(45, color("&e&lВведите PIN"));
        holder.data.put("atm_id", atmId);
        holder.data.put("account_scope", scope);
        holder.data.put("action", action);
        holder.data.put("amount", String.valueOf(amount));
        holder.data.put("target_uuid", first(targetUuid, ""));
        holder.data.put("target_name", first(targetName, ""));
        atmPinSessions.put(player.getUniqueId(), new AtmPinSession(atmId, scope, action, amount, pin, targetUuid, targetName));
        button(holder, inventory, 13, Material.PAPER, "&fВведите PIN",
                targetUuid.isBlank()
                        ? List.of("&7Код: &f" + masked)
                        : List.of("&7Кому: &f" + first(targetName, targetUuid), "&7Код: &f" + masked),
                "");
        Map<Integer, String> keypad = Map.of(
                10, "1",
                11, "2",
                12, "3",
                19, "4",
                20, "5",
                21, "6",
                28, "7",
                29, "8",
                30, "9",
                38, "0"
        );
        keypad.forEach((slot, digit) -> button(holder, inventory, slot, Material.LIGHT_BLUE_STAINED_GLASS_PANE, "&f" + digit, List.of(), "pin:digit:" + digit));
        button(holder, inventory, 23, Material.BARRIER, "&cОтмена", List.of("&7Вернуться без операции."), "pin:cancel");
        button(holder, inventory, 24, Material.YELLOW_WOOL, "&eСтереть цифру", List.of("&7Удалить последнюю цифру PIN."), "pin:back");
        button(holder, inventory, 32, Material.ORANGE_WOOL, "&eОчистить", List.of("&7Сбросить весь введённый PIN."), "pin:clear");
        button(holder, inventory, 41, Material.LIME_WOOL, "&aПодтвердить", List.of("&7Подтвердить банковскую операцию."), "pin:confirm");
        atmPinRefreshBypass.add(player.getUniqueId());
        player.openInventory(inventory);
    }

    private void handleAtmPinAction(Player player, String action) throws Exception {
        AtmPinSession session = atmPinSessions.get(player.getUniqueId());
        if (session == null) {
            player.closeInventory();
            return;
        }
        String scope = requestedAccountScope(player, session.accountScope());
        if (scope.isBlank()) {
            atmPinSessions.remove(player.getUniqueId());
            player.closeInventory();
            return;
        }
        String pin = first(session.pin(), "");
        if (action.startsWith("pin:digit:")) {
            if (pin.length() < 8) {
                pin += action.substring("pin:digit:".length());
            }
            openAtmPinPad(player, session.atmId(), scope, session.action(), session.amount(), pin, session.targetUuid(), session.targetName());
            return;
        }
        if (action.equals("pin:clear")) {
            openAtmPinPad(player, session.atmId(), scope, session.action(), session.amount(), "", session.targetUuid(), session.targetName());
            return;
        }
        if (action.equals("pin:back")) {
            String nextPin = pin.isEmpty() ? "" : pin.substring(0, pin.length() - 1);
            openAtmPinPad(player, session.atmId(), scope, session.action(), session.amount(), nextPin, session.targetUuid(), session.targetName());
            return;
        }
        if (action.equals("pin:cancel")) {
            atmPinSessions.remove(player.getUniqueId());
            openBankAtmAccount(player, session.atmId(), scope);
            return;
        }
        if (!action.equals("pin:confirm")) {
            return;
        }
        if (!pin.matches("\\d{4,8}")) {
            player.sendMessage(color("&cВведите PIN полностью: от 4 до 8 цифр."));
            openAtmPinPad(player, session.atmId(), scope, session.action(), session.amount(), pin, session.targetUuid(), session.targetName());
            return;
        }
        String confirmedPin = pin;
        atmPinSessions.remove(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(color("&7Обрабатываем банковскую операцию..."));
        CompletableFuture<TxnResult> future;
        if ("TRANSFER".equals(session.action())) {
            if ("TREASURY".equals(scope)) {
                future = transferFromTreasuryWithPinAsync(
                        player,
                        UUID.fromString(session.targetUuid()),
                        first(session.targetName(), bankTargetName(session.targetUuid())),
                        session.amount(),
                        confirmedPin,
                        "atm-treasury-transfer-" + UUID.randomUUID(),
                        "TREASURY_TRANSFER_OUT",
                        "atm=" + session.atmId());
            } else {
                future = bankService.transferWithPinAsync(
                        player.getUniqueId(),
                        player.getName(),
                        UUID.fromString(session.targetUuid()),
                        first(session.targetName(), bankTargetName(session.targetUuid())),
                        session.amount(),
                        confirmedPin,
                        "atm-transfer-" + UUID.randomUUID(),
                        "ATM_TRANSFER",
                        "atm=" + session.atmId());
            }
        } else {
            if ("TREASURY".equals(scope)) {
                future = chargeTreasuryWithPinAsync(
                        player,
                        session.amount(),
                        confirmedPin,
                        "atm-treasury-withdraw-" + UUID.randomUUID(),
                        "TREASURY_WITHDRAW",
                        "atm=" + session.atmId());
            } else {
                future = bankService.chargeAsync(
                        player.getUniqueId(),
                        player.getName(),
                        session.amount(),
                        confirmedPin,
                        "atm-withdraw-" + UUID.randomUUID(),
                        "ATM_WITHDRAW",
                        "atm=" + session.atmId());
            }
        }
        future.whenComplete((result, error) -> Bukkit.getScheduler().runTask(this, () -> {
            if (!player.isOnline()) {
                // A successful debit creates its DEBITED delivery row in the
                // same database transaction.  Do not append a second
                // best-effort queue row here; the durable row is recovered on
                // the next join.
                return;
            }
            if (error != null || result == null || !result.ok) {
                atmPinSessions.put(player.getUniqueId(), new AtmPinSession(session.atmId(), scope, session.action(), session.amount(), confirmedPin, session.targetUuid(), session.targetName()));
                if (error != null) {
                    getLogger().warning("ATM PIN operation: " + safeError(error));
                }
                player.sendMessage(color("&c" + first(result == null ? "" : result.message, "Операция отклонена.")));
                openAtmPinPad(player, session.atmId(), scope, session.action(), session.amount(), confirmedPin, session.targetUuid(), session.targetName());
                return;
            }
            if (!"TRANSFER".equals(session.action())) {
                completeWithdrawOnMainThread(player, session.amount(), first(result.txId, ""), session.atmId(), scope);
                return;
            }
            player.sendMessage(color("&aОперация выполнена."));
            openBankAtmAccount(player, session.atmId(), scope);
        }));
    }

    private String depositArFromHandAsync(Player player, String atmId, String accountScope) throws Exception {
        ItemStack stack = player.getInventory().getItemInMainHand();
        if (!isOfficialAr(stack)) {
            return "&cВ основной руке нет официального AR.";
        }
        String scope = requestedAccountScope(player, accountScope);
        if (scope.isBlank()) {
            return "&cДоступ к казне отозван. Пополнение отменено.";
        }
        int amount = stack.getAmount();
        SerializedArFragment fragment = serializeArFragment(
                new ArFragment(player.getInventory().getHeldItemSlot(), stack.clone()));
        int handSlot = player.getInventory().getHeldItemSlot();
        String txKey = "atm-hand-" + UUID.randomUUID();
        ArDepositIntent intent = newArDepositIntent(player, atmId, scope, amount, txKey, List.of(fragment));
        createArDepositIntent(intent).whenComplete((stored, error) -> Bukkit.getScheduler().runTask(this, () -> {
            if (error != null || stored == null) {
                getLogger().warning("atm deposit hand intent creation failed: " + safeError(error));
                player.sendMessage(color("&cОперация AR не создана. Предмет не изменён."));
                return;
            }
            if (!player.isOnline() || !removeExactArFragments(player, stored.fragments())) {
                if (player.isOnline()) {
                    player.sendMessage(color("&eПредмет изменился до подтверждения операции. AR оставлен в инвентаре."));
                }
                dbAsync("cancel unchanged AR hand deposit", () -> cancelArDepositIntent(stored.id(), "physical_snapshot_changed"));
                return;
            }
            player.updateInventory();
            markArDepositPhysicalRemoved(stored.id()).whenComplete((marked, markError) -> Bukkit.getScheduler().runTask(this, () -> {
                if (markError != null || !Boolean.TRUE.equals(marked)) {
                    getLogger().warning("atm deposit hand physical removal journal failed: " + safeError(markError));
                    player.sendMessage(color("&eAR снят после фиксации намерения, но подтверждение задержано. Операция будет повторена автоматически."));
                    return;
                }
                advanceArDepositIntent(stored.id(), player);
            }));
        }));
        return "&7Вносим AR в банк...";
    }

    private String depositAllArAsync(Player player, String atmId, String accountScope) throws Exception {
        List<ArFragment> snapshots = snapshotOfficialArFragments(player.getInventory());
        if (snapshots.isEmpty()) {
            return "&cВ инвентаре нет официального AR.";
        }
        String scope = requestedAccountScope(player, accountScope);
        if (scope.isBlank()) {
            return "&cДоступ к казне отозван. Пополнение отменено.";
        }
        List<SerializedArFragment> fragments = serializeArFragments(snapshots);
        int available = fragments.stream().mapToInt(SerializedArFragment::amount).sum();
        String txKey = "atm-all-" + UUID.randomUUID();
        ArDepositIntent intent = newArDepositIntent(player, atmId, scope, available, txKey, fragments);
        createArDepositIntent(intent).whenComplete((stored, error) -> Bukkit.getScheduler().runTask(this, () -> {
            if (error != null || stored == null) {
                getLogger().warning("atm deposit all intent creation failed: " + safeError(error));
                player.sendMessage(color("&cОперация AR не создана. Предметы не изменены."));
                return;
            }
            if (!player.isOnline() || !removeExactArFragments(player, stored.fragments())) {
                if (player.isOnline()) {
                    player.sendMessage(color("&eИнвентарь изменился до подтверждения операции. AR оставлен в инвентаре."));
                }
                dbAsync("cancel changed AR bulk deposit", () -> cancelArDepositIntent(stored.id(), "physical_snapshot_changed"));
                return;
            }
            player.updateInventory();
            markArDepositPhysicalRemoved(stored.id()).whenComplete((marked, markError) -> Bukkit.getScheduler().runTask(this, () -> {
                if (markError != null || !Boolean.TRUE.equals(marked)) {
                    getLogger().warning("atm deposit all physical removal journal failed: " + safeError(markError));
                    player.sendMessage(color("&eAR снят после фиксации намерения, но подтверждение задержано. Операция будет повторена автоматически."));
                    return;
                }
                advanceArDepositIntent(stored.id(), player);
            }));
        }));
        return "&7Вносим весь AR в банк...";
    }

    private ArDepositIntent newArDepositIntent(Player player, String atmId, String scope, long amount,
                                               String idempotencyKey, List<SerializedArFragment> fragments) {
        return new ArDepositIntent(UUID.randomUUID().toString(), player.getUniqueId(), player.getName(),
                "TREASURY".equals(scope) ? treasuryAccountId() : bankAccountId(player.getUniqueId().toString()),
                scope, first(atmId, ""), amount, idempotencyKey, List.copyOf(fragments), AR_DEPOSIT_STATUS_CREATED);
    }

    private SerializedArFragment serializeArFragment(ArFragment fragment) throws Exception {
        if (fragment == null || fragment.snapshot() == null || !isOfficialAr(fragment.snapshot())) {
            throw new IllegalStateException("AR deposit snapshot is not an official item");
        }
        String serial = officialArSerial(fragment.snapshot());
        if (serial.isBlank()) {
            throw new IllegalStateException("AR deposit snapshot has no serial");
        }
        if (!isFungibleArSerial(serial)) {
            try {
                UUID.fromString(serial);
            } catch (IllegalArgumentException invalidSerial) {
                throw new IllegalStateException("AR deposit snapshot serial is invalid", invalidSerial);
            }
        }
        return new SerializedArFragment(fragment.slot(), serial, fragment.snapshot().getType(), fragment.snapshot().getAmount());
    }

    private List<SerializedArFragment> serializeArFragments(List<ArFragment> fragments) throws Exception {
        List<SerializedArFragment> result = new ArrayList<>();
        Set<String> serials = new HashSet<>();
        Map<String, SerializedArFragment> fungible = new LinkedHashMap<>();
        if (fragments == null) {
            return result;
        }
        for (ArFragment fragment : fragments) {
            SerializedArFragment serialized = serializeArFragment(fragment);
            if (isFungibleArSerial(serialized.serial())) {
                SerializedArFragment existing = fungible.get(serialized.serial());
                if (existing == null) {
                    fungible.put(serialized.serial(), serialized);
                } else {
                    long combined = (long) existing.amount() + serialized.amount();
                    if (combined > Integer.MAX_VALUE) {
                        throw new IllegalStateException("AR fungible snapshot is too large");
                    }
                    fungible.put(serialized.serial(), new SerializedArFragment(
                            existing.slot(), existing.serial(), existing.material(), (int) combined));
                }
                continue;
            }
            if (!serials.add(serialized.serial())) {
                throw new IllegalStateException("Duplicate AR serial in one deposit snapshot");
            }
            result.add(serialized);
        }
        result.addAll(fungible.values());
        return result;
    }

    private String encodeArDepositSnapshot(List<SerializedArFragment> fragments) {
        StringBuilder encoded = new StringBuilder();
        for (SerializedArFragment fragment : fragments) {
            if (encoded.length() > 0) {
                encoded.append(';');
            }
            encoded.append(fragment.slot()).append(',')
                    .append(fragment.serial()).append(',')
                    .append(fragment.material().name()).append(',')
                    .append(fragment.amount());
        }
        return encoded.toString();
    }

    private List<SerializedArFragment> decodeArDepositSnapshot(String encoded) throws Exception {
        List<SerializedArFragment> fragments = new ArrayList<>();
        Set<String> serials = new HashSet<>();
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException("AR deposit snapshot is empty");
        }
        for (String raw : encoded.split(";", -1)) {
            String[] parts = raw.split(",", -1);
            if (parts.length != 4) {
                throw new IllegalStateException("AR deposit snapshot is malformed");
            }
            int slot = Integer.parseInt(parts[0]);
            String serial = parts[1];
            Material material = Material.valueOf(parts[2]);
            int amount = Integer.parseInt(parts[3]);
            if (slot < 0 || amount <= 0 || (material != Material.DIAMOND_ORE && material != Material.DEEPSLATE_DIAMOND_ORE)
                    || (!isFungibleArSerial(serial) && !serials.add(serial))) {
                throw new IllegalStateException("AR deposit snapshot contains invalid data");
            }
            if (!isFungibleArSerial(serial)) {
                UUID.fromString(serial);
            }
            fragments.add(new SerializedArFragment(slot, serial, material, amount));
        }
        return fragments;
    }

    private CompletableFuture<ArDepositIntent> createArDepositIntent(ArDepositIntent intent) {
        return dbFuture("create AR deposit intent", () -> tx(connection -> {
            long createdAt = now();
            update(connection,
                    "INSERT INTO cmv8_ar_deposit_intents(id,player_uuid,player_name,account_id,account_scope,atm_id,amount,idempotency_key,snapshot,status,created_at,updated_at,committed_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,0) ON CONFLICT(idempotency_key) DO NOTHING",
                    intent.id(), intent.playerUuid().toString(), intent.playerName(), intent.accountId(), intent.scope(), intent.atmId(),
                    intent.amount(), intent.idempotencyKey(), encodeArDepositSnapshot(intent.fragments()), intent.status(), createdAt, createdAt);
            Map<String, Object> row = queryOne(connection,
                    "SELECT id,player_uuid,player_name,account_id,account_scope,atm_id,amount,idempotency_key,snapshot,status FROM cmv8_ar_deposit_intents WHERE idempotency_key=? LIMIT 1",
                    intent.idempotencyKey());
            return mapArDepositIntent(row);
        }));
    }

    private ArDepositIntent mapArDepositIntent(Map<String, Object> row) throws Exception {
        if (row == null || row.isEmpty()) {
            throw new IllegalStateException("AR deposit intent was not persisted");
        }
        return new ArDepositIntent(
                string(row.get("id")), UUID.fromString(string(row.get("player_uuid"))), string(row.get("player_name")),
                string(row.get("account_id")), string(row.get("account_scope")), string(row.get("atm_id")), longValue(row.get("amount")),
                string(row.get("idempotency_key")), decodeArDepositSnapshot(string(row.get("snapshot"))), string(row.get("status")));
    }

    private CompletableFuture<Boolean> markArDepositPhysicalRemoved(String intentId) {
        return dbFuture("mark AR deposit physical removal", () -> tx(connection -> {
            int changed = updateCount(connection,
                    "UPDATE cmv8_ar_deposit_intents SET status=?,updated_at=? WHERE id=? AND status=?",
                    AR_DEPOSIT_STATUS_PHYSICAL_REMOVED, now(), intentId, AR_DEPOSIT_STATUS_CREATED);
            if (changed == 1) {
                return true;
            }
            Map<String, Object> row = queryOne(connection, "SELECT status FROM cmv8_ar_deposit_intents WHERE id=? FOR UPDATE", intentId);
            return row != null && AR_DEPOSIT_STATUS_PHYSICAL_REMOVED.equals(string(row.get("status")));
        }));
    }

    private boolean cancelArDepositIntent(String intentId, String reason) throws Exception {
        return tx(connection -> updateCount(connection,
                "UPDATE cmv8_ar_deposit_intents SET status=?,updated_at=? WHERE id=? AND status=?",
                AR_DEPOSIT_STATUS_CANCELLED, now(), intentId, AR_DEPOSIT_STATUS_CREATED) == 1);
    }

    private boolean commitArDepositAtomically(String intentId) throws Exception {
        return tx(connection -> {
            Map<String, Object> intentRow = queryOne(connection,
                    "SELECT snapshot,status,amount,account_id,account_scope,player_uuid,player_name,idempotency_key "
                            + "FROM cmv8_ar_deposit_intents WHERE id=? FOR UPDATE", intentId);
            if (intentRow == null || intentRow.isEmpty()) {
                return false;
            }
            String status = string(intentRow.get("status"));
            if (AR_DEPOSIT_STATUS_COMMITTED.equals(status)) {
                return true;
            }
            if (!AR_DEPOSIT_STATUS_PHYSICAL_REMOVED.equals(status)) {
                return false;
            }
            List<SerializedArFragment> fragments = decodeArDepositSnapshot(string(intentRow.get("snapshot")));
            long total = fragments.stream().mapToLong(SerializedArFragment::amount).sum();
            if (total != longValue(intentRow.get("amount"))) {
                throw new IllegalStateException("AR deposit amount does not match its durable snapshot");
            }
            long timestamp = now();
            String accountId = string(intentRow.get("account_id"));
            Map<String, Object> account = "TREASURY".equalsIgnoreCase(string(intentRow.get("account_scope")))
                    ? ensureTreasuryAccount(connection)
                    : queryOne(connection, "SELECT * FROM cmv4_bank_accounts WHERE account_id=? FOR UPDATE", accountId);
            if (account == null || account.isEmpty()) {
                throw new IllegalStateException("AR deposit target account is missing");
            }
            String targetAccountId = string(account.get("account_id"));
            String txKey = first(string(intentRow.get("idempotency_key")), "ar-deposit-" + intentId);
            BankServiceImpl bank = new BankServiceImpl();
            TxnResult replay = bank.replayCreditIfCommitted(connection, txKey, targetAccountId);
            if (replay == null) {
                long before = scalarLong(connection,
                        "SELECT COALESCE(balance,0) FROM cmv4_bank_accounts WHERE account_id=? FOR UPDATE",
                        targetAccountId);
                long after = before + longValue(intentRow.get("amount"));
                String txId = txKey;
                update(connection,
                        "UPDATE cmv4_bank_accounts SET balance=?,version=version+1,updated_at=? WHERE account_id=?",
                        after, timestamp, targetAccountId);
                update(connection,
                        "INSERT INTO cmv4_bank_ledger(tx_id,account_id,counterparty_account_id,player_uuid,tx_type,amount,balance_after,idempotency_key,status,created_at,actor,details) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                        txId, targetAccountId, "AR_DEPOSIT", string(intentRow.get("player_uuid")),
                        "AR_DEPOSIT", longValue(intentRow.get("amount")), after, txKey, "COMMITTED",
                        timestamp, string(intentRow.get("player_name")), "atm-deposit=" + intentId);
            } else {
                Map<String, Object> ledger = queryOne(connection,
                        "SELECT account_id,amount FROM cmv4_bank_ledger WHERE idempotency_key=? LIMIT 1", txKey);
                if (ledger == null || !targetAccountId.equals(string(ledger.get("account_id")))
                        || longValue(ledger.get("amount")) != longValue(intentRow.get("amount"))) {
                    throw new IllegalStateException("AR deposit idempotency key is bound to a conflicting bank credit");
                }
            }
            for (SerializedArFragment fragment : fragments) {
                boolean fungible = isFungibleArSerial(fragment.serial());
                Map<String, Object> asset = queryOne(connection,
                        "SELECT denomination,material,signature_version,status,issued_units,deposited_units,deposited_intent_id,deposited_account_id "
                                + "FROM cmv8_ar_assets WHERE serial=? FOR UPDATE",
                        fragment.serial());
                if (asset == null || asset.isEmpty()) {
                    throw new IllegalStateException("AR asset registry entry is missing");
                }
                int expectedDenomination = fungible ? 1 : fragment.amount();
                int expectedVersion = fungible ? OFFICIAL_AR_SIGNATURE_VERSION : LEGACY_AR_SIGNATURE_VERSION;
                if (intValue(asset.get("denomination")) != expectedDenomination
                        || !fragment.material().name().equals(string(asset.get("material")))
                        || intValue(asset.get("signature_version")) != expectedVersion) {
                    throw new IllegalStateException("AR serial registry metadata conflict");
                }
                String assetStatus = string(asset.get("status"));
                if (fungible) {
                    long issued = longValue(asset.get("issued_units"));
                    long deposited = longValue(asset.get("deposited_units"));
                    if (!"ACTIVE".equals(assetStatus) || issued <= 0L || deposited + fragment.amount() > issued) {
                        throw new IllegalStateException("Fungible AR supply is not available in the authoritative registry");
                    }
                    int movement = updateCount(connection,
                            "INSERT INTO cmv8_ar_asset_movements(intent_id,serial,account_id,amount,created_at) VALUES(?,?,?,?,?) ON CONFLICT(intent_id,serial) DO NOTHING",
                            intentId, fragment.serial(), targetAccountId, fragment.amount(), timestamp);
                    if (movement == 0) {
                        Map<String, Object> existingMovement = queryOne(connection,
                                "SELECT account_id,amount FROM cmv8_ar_asset_movements WHERE intent_id=? AND serial=?",
                                intentId, fragment.serial());
                        if (existingMovement == null || !targetAccountId.equals(string(existingMovement.get("account_id")))
                                || longValue(existingMovement.get("amount")) != fragment.amount()) {
                            throw new IllegalStateException("AR deposit movement idempotency conflict");
                        }
                    }
                    update(connection,
                            "UPDATE cmv8_ar_assets SET deposited_units=deposited_units+?,updated_at=? WHERE serial=? AND status=?",
                            movement == 1 ? fragment.amount() : 0L, timestamp, fragment.serial(), "ACTIVE");
                    continue;
                }
                if (AR_DEPOSIT_STATUS_DEPOSITED.equals(assetStatus)) {
                    if (!intentId.equals(string(asset.get("deposited_intent_id")))) {
                        throw new IllegalStateException("AR serial has already been deposited by another intent");
                    }
                    continue;
                }
                if (!"ACTIVE".equals(assetStatus)) {
                    throw new IllegalStateException("AR serial registry is not active");
                }
                update(connection,
                        "UPDATE cmv8_ar_assets SET status=?,deposited_at=?,deposited_intent_id=?,deposited_account_id=?,updated_at=? WHERE serial=? AND status=?",
                        AR_DEPOSIT_STATUS_DEPOSITED, timestamp, intentId, targetAccountId, timestamp, fragment.serial(), "ACTIVE");
            }
            update(connection,
                    "UPDATE cmv8_ar_deposit_intents SET status=?,committed_at=?,updated_at=? WHERE id=? AND status=?",
                    AR_DEPOSIT_STATUS_COMMITTED, timestamp, timestamp, intentId, AR_DEPOSIT_STATUS_PHYSICAL_REMOVED);
            return true;
        });
    }

    private List<ArDepositIntent> loadArDepositIntents(UUID playerUuid) throws Exception {
        return tx(connection -> {
            List<Map<String, Object>> rows = queryList(connection,
                    "SELECT id,player_uuid,player_name,account_id,account_scope,atm_id,amount,idempotency_key,snapshot,status FROM cmv8_ar_deposit_intents WHERE player_uuid=? AND status IN (?,?) ORDER BY created_at ASC",
                    playerUuid.toString(), AR_DEPOSIT_STATUS_CREATED, AR_DEPOSIT_STATUS_PHYSICAL_REMOVED);
            List<ArDepositIntent> result = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                result.add(mapArDepositIntent(row));
            }
            return result;
        });
    }

    private boolean hasExactArFragments(Player player, List<SerializedArFragment> fragments) {
        if (player == null || fragments == null || fragments.isEmpty()) {
            return false;
        }
        for (SerializedArFragment fragment : fragments) {
            if (countOfficialArSerial(player.getInventory(), fragment.serial()) != fragment.amount()) {
                return false;
            }
        }
        return true;
    }

    private boolean removeExactArFragments(Player player, List<SerializedArFragment> fragments) {
        if (!hasExactArFragments(player, fragments)) {
            return false;
        }
        for (SerializedArFragment fragment : fragments) {
            removeOfficialArSerial(player.getInventory(), fragment.serial(), fragment.amount());
        }
        return true;
    }

    private ItemStack restoreSerializedArStack(SerializedArFragment fragment) {
        ItemStack restored = createOfficialArStack(fragment.material(), fragment.amount(), "", "", "deposit-restore");
        if (isFungibleArSerial(fragment.serial())) {
            return restored;
        }
        ItemMeta meta = restored.getItemMeta();
        if (meta == null) {
            return restored;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(officialArSerialKey, PersistentDataType.STRING, fragment.serial());
        pdc.set(officialArDenominationKey, PersistentDataType.INTEGER, fragment.amount());
        pdc.set(officialArSignatureVersionKey, PersistentDataType.INTEGER, LEGACY_AR_SIGNATURE_VERSION);
        pdc.set(officialArSignatureKey, PersistentDataType.STRING,
                officialArSignature(fragment.serial(), fragment.material(), fragment.amount()));
        restored.setItemMeta(meta);
        return restored;
    }

    private int restoreSerializedArFragments(Player player, List<SerializedArFragment> fragments) {
        if (player == null || !player.isOnline() || fragments == null) {
            return fragments == null ? 0 : fragments.stream().mapToInt(SerializedArFragment::amount).sum();
        }
        int notRestored = 0;
        for (SerializedArFragment fragment : fragments) {
            ItemStack snapshot = restoreSerializedArStack(fragment);
            notRestored += restoreOfficialArSafely(player, snapshot, fragment.serial());
        }
        player.updateInventory();
        return notRestored;
    }

    private void markArDepositRestoreState(String intentId, String status, String reason) throws Exception {
        tx(connection -> {
            update(connection,
                    "UPDATE cmv8_ar_deposit_intents SET status=?,updated_at=? WHERE id=? AND status IN (?,?,?)",
                    status, now(), intentId, AR_DEPOSIT_STATUS_CREATED, AR_DEPOSIT_STATUS_PHYSICAL_REMOVED, AR_DEPOSIT_STATUS_RESTORE_PENDING);
            return null;
        });
    }

    private void rejectArDeposit(ArDepositIntent intent, Player player, String reason) {
        int notRestored = restoreSerializedArFragments(player, intent.fragments());
        String restoreReason = "atm=" + intent.atmId() + ",scope=" + intent.scope() + ",tx=" + intent.idempotencyKey()
                + ",reason=" + first(reason, "bank_rejected");
        if (notRestored > 0) {
            long available = notRestored;
            dbAsync("mark AR deposit restore pending", () -> markArDepositRestoreState(intent.id(), AR_DEPOSIT_STATUS_RESTORE_PENDING, restoreReason));
            if (player != null && player.isOnline()) {
                long amount = available;
                queuePendingArSettlement(player.getUniqueId(), player.getName(), amount, PENDING_AR_SETTLEMENT_TYPE_DEPOSIT_RESTORE, restoreReason);
            } else if (player != null) {
                queuePendingArSettlement(player.getUniqueId(), player.getName(), available, PENDING_AR_SETTLEMENT_TYPE_DEPOSIT_RESTORE, restoreReason);
            } else {
                queuePendingArSettlement(intent.playerUuid(), intent.playerName(), available, PENDING_AR_SETTLEMENT_TYPE_DEPOSIT_RESTORE, restoreReason);
            }
        } else {
            dbAsync("cancel rejected AR deposit", () -> markArDepositRestoreState(intent.id(), AR_DEPOSIT_STATUS_CANCELLED, restoreReason));
        }
        if (player != null && player.isOnline()) {
            player.sendMessage(color("&cБанк отклонил пополнение AR. Предмет возвращён или поставлен в безопасную очередь."));
            openBankAtmAccount(player, intent.atmId(), intent.scope());
        }
    }

    private void advanceArDepositIntent(String intentId, Player player) {
        dbFuture("commit AR deposit atomically", () -> commitArDepositAtomically(intentId))
                .whenComplete((committed, error) -> Bukkit.getScheduler().runTask(this, () -> {
                    if (error != null || !Boolean.TRUE.equals(committed)) {
                        getLogger().warning("AR deposit atomic commit is pending retry for " + intentId + ": " + safeError(error));
                        if (player != null && player.isOnline()) {
                            player.sendMessage(color("&eAR снят, но единая банковская операция ещё не подтверждена. Повтор будет выполнен автоматически."));
                        }
                        return;
                    }
                    if (player != null && player.isOnline()) {
                        player.sendMessage(color("&aВ банк внесено AR: операция подтверждена реестром."));
                        dbFuture("load committed AR deposit", () -> tx(connection -> mapArDepositIntent(
                                queryOne(connection, "SELECT id,player_uuid,player_name,account_id,account_scope,atm_id,amount,idempotency_key,snapshot,status FROM cmv8_ar_deposit_intents WHERE id=?", intentId))))
                                .thenAccept(intent -> Bukkit.getScheduler().runTask(this, () -> {
                                    if (intent != null && player.isOnline()) {
                                        openBankAtmAccount(player, intent.atmId(), intent.scope());
                                    }
                                }));
                    }
                }));
    }

    private CompletableFuture<TxnResult> creditDepositForPlayerAsync(Player player, ArDepositIntent intent) {
        String action = "ATM_DEPOSIT" + (intent.fragments().size() > 1 ? "_ALL" : "");
        String details = "atm=" + intent.atmId();
        if (player != null && player.isOnline()) {
            return bankService.creditAsync(player.getUniqueId(), player.getName(), intent.amount(), intent.idempotencyKey(),
                    action, details);
        }
        return bankService.creditAsync(intent.playerUuid(), intent.playerName(), intent.amount(), intent.idempotencyKey(),
                action, details);
    }

    private void processArDepositIntents(Player player, boolean notify) {
        if (player == null || !player.isOnline()) {
            return;
        }
        dbFuture("load AR deposit intents", () -> loadArDepositIntents(player.getUniqueId()))
                .whenComplete((intents, error) -> Bukkit.getScheduler().runTask(this, () -> {
                    if (error != null || intents == null || !player.isOnline()) {
                        if (error != null) {
                            getLogger().warning("AR deposit intent recovery failed: " + safeError(error));
                        }
                        return;
                    }
                    for (ArDepositIntent intent : intents) {
                        if (AR_DEPOSIT_STATUS_CREATED.equals(intent.status())) {
                            if (hasExactArFragments(player, intent.fragments())) {
                                dbAsync("cancel unconsumed AR deposit intent", () -> cancelArDepositIntent(intent.id(), "physical_removal_never_started"));
                            } else {
                                dbAsync("review ambiguous AR deposit intent", () -> markArDepositRestoreState(intent.id(), AR_DEPOSIT_STATUS_REVIEW, "physical_snapshot_ambiguous"));
                                if (notify) {
                                    player.sendMessage(color("&eНезавершённое пополнение AR отправлено на проверку: предмет изменился до списания."));
                                }
                            }
                        } else if (AR_DEPOSIT_STATUS_PHYSICAL_REMOVED.equals(intent.status())) {
                            advanceArDepositIntent(intent.id(), player);
                        }
                    }
                }));
    }

    private void processPendingArSettlements(Player player, boolean notifyNoSpace) {
        if (player == null || !player.isOnline()) {
            return;
        }
        dbFuture("pending ar settlements load", () -> loadPendingArSettlements(player.getUniqueId()))
                .whenComplete((rows, error) -> Bukkit.getScheduler().runTask(this, () -> {
                    if (error != null) {
                        getLogger().warning("pending AR settlements load: " + safeError(error));
                        return;
                    }
                    if (rows == null || rows.isEmpty() || !player.isOnline()) {
                        return;
                    }
                    long calculatedTotalAmount = 0L;
                    for (PendingArSettlement row : rows) {
                        if (row.amount() <= 0L) {
                            dbAsync("invalid pending ar settlement review", () -> markPendingArSettlementsReview(
                                    List.of(row.id()), "", "invalid_amount"));
                            getLogger().warning("Pending AR settlement has invalid amount: " + row.id());
                            return;
                        }
                        try {
                            calculatedTotalAmount = Math.addExact(calculatedTotalAmount, row.amount());
                        } catch (ArithmeticException overflow) {
                            dbAsync("oversized pending ar settlement review", () -> markPendingArSettlementsReview(
                                    rows.stream().map(PendingArSettlement::id).toList(), "", "amount_overflow"));
                            getLogger().warning("Pending AR settlements exceed the supported issuance amount.");
                            return;
                        }
                    }
                    if (calculatedTotalAmount > Integer.MAX_VALUE) {
                        dbAsync("oversized pending ar issuance review", () -> markPendingArSettlementsReview(
                                rows.stream().map(PendingArSettlement::id).toList(), "", "amount_overflow"));
                        if (notifyNoSpace) {
                            player.sendMessage(color("&eОжидающая выдача AR слишком велика для одной операции и отправлена на ручную проверку."));
                        }
                        return;
                    }
                    final long totalAmount = calculatedTotalAmount;
                    long capacity = arCapacity(player.getInventory());
                    if (capacity < totalAmount) {
                        if (notifyNoSpace) {
                            player.sendMessage(color("&eЕсть невыданный официальный AR. Освободи место в инвентаре и открой банкомат снова."));
                        }
                        return;
                    }
                    List<String> ids = rows.stream().map(PendingArSettlement::id).toList();
                    dbFuture("pending ar settlements reserve", () -> reservePendingArSettlements(ids))
                            .whenComplete((reservation, reserveError) -> Bukkit.getScheduler().runTask(this, () -> {
                                if (reserveError != null) {
                                    getLogger().warning("pending AR settlements reserve: " + safeError(reserveError));
                                    return;
                                }
                                PendingArReservation safeReservation = reservation == null
                                        ? new PendingArReservation("", List.of()) : reservation;
                                List<String> reservedIds = safeReservation.ids();
                                if (!player.isOnline()) {
                                    dbAsync("pending ar settlements release", () -> releasePendingArSettlements(reservedIds, safeReservation.token()));
                                    return;
                                }
                                if (reservedIds.size() != ids.size()) {
                                    // Another join/retry may have reserved the
                                    // same rows. Do not release the entire id
                                    // list here: that would make the first
                                    // worker's DELIVERING rows PENDING again
                                    // and allow a duplicate physical issue.
                                    dbAsync("pending ar settlements release partial", () -> releasePendingArSettlements(reservedIds, safeReservation.token()));
                                    return;
                                }
                                 prepareOfficialArIssuance("pending:" + String.join(",", reservedIds), Material.DIAMOND_ORE,
                                                 Math.toIntExact(totalAmount), "pending-ar-settlement")
                                         .whenComplete((prepared, preparationError) -> Bukkit.getScheduler().runTask(this, () -> {
                                  if (preparationError != null || !Boolean.TRUE.equals(prepared)) {
                                      dbAsync("pending ar issuance preparation review", () -> markPendingArSettlementsReview(reservedIds, safeReservation.token(), "issuance_prepare_failed"));
                                      return;
                                  }
                                  if (!player.isOnline()) {
                                     dbAsync("pending ar settlements release after issuance prepare", () -> releasePendingArSettlements(reservedIds, safeReservation.token()));
                                     return;
                                  }
                                 if (arCapacity(player.getInventory()) < totalAmount || !issueOfficialArAmount(player, totalAmount, "pending-ar-settlement", false)) {
                                    dbAsync("pending ar settlements release", () -> releasePendingArSettlements(reservedIds, safeReservation.token()));
                                    if (notifyNoSpace) {
                                        player.sendMessage(color("&eОсвободи место в инвентаре и открой банкомат снова, чтобы забрать ожидающий AR."));
                                    }
                                    return;
                                }
                                dbFuture("pending ar settlements delivered", () -> {
                                    markPendingArSettlementsDelivered(reservedIds, safeReservation.token());
                                    return true;
                                }).whenComplete((delivered, deliveryError) -> Bukkit.getScheduler().runTask(this, () -> {
                                    if (deliveryError != null || !Boolean.TRUE.equals(delivered)) {
                                        dbAsync("pending ar settlements review", () -> markPendingArSettlementsReview(reservedIds, safeReservation.token(), "delivery_finalize_failed"));
                                        player.sendMessage(color("&eAR выдан физически, но регистрация не подтверждена. Операция отправлена на проверку."));
                                        return;
                                    }
                                    player.sendMessage(color("&aВыдан ожидающий официальный AR: &f" + totalAmount + " AR"));
                                }));
                             }));
                 }));
                 }));
     }

    private List<PendingArSettlement> loadPendingArSettlements(UUID playerUuid) throws Exception {
        return tx(connection -> {
            List<Map<String, Object>> rows = queryList(connection,
                    "SELECT id,player_uuid,player_name,amount,settlement_type,reason FROM cmv4_pending_ar_settlements WHERE player_uuid=? AND status IN (?,?) ORDER BY created_at ASC",
                    playerUuid.toString(), PENDING_AR_SETTLEMENT_STATUS_DEBITED, PENDING_AR_SETTLEMENT_STATUS_PENDING);
            List<PendingArSettlement> result = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                result.add(new PendingArSettlement(
                        string(row.get("id")),
                        UUID.fromString(string(row.get("player_uuid"))),
                        first(string(row.get("player_name")), ""),
                        longValue(row.get("amount")),
                        first(string(row.get("settlement_type")), ""),
                        first(string(row.get("reason")), "")
                ));
            }
            return result;
        });
    }

    private PendingArReservation reservePendingArSettlements(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) {
            return new PendingArReservation("", List.of());
        }
        String token = UUID.randomUUID().toString();
        return tx(connection -> {
            List<String> reserved = new ArrayList<>();
            long updatedAt = now();
            for (String id : ids) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE cmv4_pending_ar_settlements SET status=?,delivery_token=?,updated_at=? WHERE id=? AND status IN (?,?)")) {
                    bind(statement, PENDING_AR_SETTLEMENT_STATUS_DELIVERING, token, updatedAt, id, PENDING_AR_SETTLEMENT_STATUS_DEBITED, PENDING_AR_SETTLEMENT_STATUS_PENDING);
                    if (statement.executeUpdate() == 1) {
                        reserved.add(id);
                    }
                }
            }
            return new PendingArReservation(token, List.copyOf(reserved));
        });
    }

    private void markPendingArSettlementsDelivered(List<String> ids, String token) throws Exception {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        tx(connection -> {
            long updatedAt = now();
            for (String id : ids) {
                update(connection,
                        "UPDATE cmv4_pending_ar_settlements SET status=?,delivery_token='',delivered_at=?,updated_at=? WHERE id=? AND status=? AND delivery_token=?",
                        PENDING_AR_SETTLEMENT_STATUS_DELIVERED, updatedAt, updatedAt, id, PENDING_AR_SETTLEMENT_STATUS_DELIVERING, first(token, ""));
            }
            return null;
        });
    }

    private void markPendingArSettlementsReview(List<String> ids, String token, String reason) throws Exception {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        tx(connection -> {
            long updatedAt = now();
            for (String id : ids) {
                update(connection,
                        "UPDATE cmv4_pending_ar_settlements SET status=?,delivery_token='',reason=CASE WHEN reason='' THEN ? ELSE reason || ';' || ? END,updated_at=? WHERE id=? AND ((status=? AND delivery_token=?) OR (status IN (?,?) AND ?=''))",
                        PENDING_AR_SETTLEMENT_STATUS_REVIEW, first(reason, "delivery_review"), first(reason, "delivery_review"), updatedAt,
                        id, PENDING_AR_SETTLEMENT_STATUS_DELIVERING, first(token, ""),
                        PENDING_AR_SETTLEMENT_STATUS_DEBITED, PENDING_AR_SETTLEMENT_STATUS_PENDING, first(token, ""));
            }
            return null;
        });
    }

    private CompletableFuture<PendingArReservation> reserveWithdrawalDelivery(String transactionId) {
        return dbFuture("reserve withdrawal delivery", () -> tx(connection -> {
            if (transactionId == null || transactionId.isBlank()) {
                return new PendingArReservation("", List.of());
            }
            Map<String, Object> row = queryOne(connection,
                    "SELECT id FROM cmv4_pending_ar_settlements WHERE idempotency_key=? AND settlement_type=? AND status IN (?,?) LIMIT 1",
                    transactionId, PENDING_AR_SETTLEMENT_TYPE_WITHDRAW_DELIVERY,
                    PENDING_AR_SETTLEMENT_STATUS_DEBITED, PENDING_AR_SETTLEMENT_STATUS_PENDING);
            if (row == null || row.isEmpty()) {
                return new PendingArReservation("", List.of());
            }
            String id = first(string(row.get("id")), "");
            String token = UUID.randomUUID().toString();
            int changed = updateCount(connection,
                    "UPDATE cmv4_pending_ar_settlements SET status=?,delivery_token=?,updated_at=? WHERE id=? AND status IN (?,?)",
                    PENDING_AR_SETTLEMENT_STATUS_DELIVERING, token, now(), id,
                    PENDING_AR_SETTLEMENT_STATUS_DEBITED, PENDING_AR_SETTLEMENT_STATUS_PENDING);
            return changed == 1 ? new PendingArReservation(token, List.of(id)) : new PendingArReservation("", List.of());
        }));
    }

    private void releasePendingArSettlements(List<String> ids, String token) throws Exception {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        tx(connection -> {
            long updatedAt = now();
            for (String id : ids) {
                update(connection,
                        "UPDATE cmv4_pending_ar_settlements SET status=?,delivery_token='',updated_at=? WHERE id=? AND status=? AND delivery_token=?",
                        PENDING_AR_SETTLEMENT_STATUS_PENDING, updatedAt, id, PENDING_AR_SETTLEMENT_STATUS_DELIVERING, first(token, ""));
            }
            return null;
        });
    }

    private CompletableFuture<SettlementResult> queuePendingArSettlement(UUID playerUuid, String playerName, long amount, String settlementType, String reason) {
        if (playerUuid == null || amount <= 0) {
            return CompletableFuture.completedFuture(new SettlementResult(false, "", "invalid_settlement"));
        }
        String normalizedType = first(settlementType, PENDING_AR_SETTLEMENT_TYPE_WITHDRAW_DELIVERY);
        String normalizedReason = first(reason, "");
        String idempotencyKey = "pending-ar-" + sha256Hex(playerUuid + "|" + normalizedType + "|" + amount + "|" + normalizedReason);
        // Persist a local intent before handing the insert to the bounded DB
        // executor.  If the executor is full, PostgreSQL is down, or the
        // process is killed between the debit and the insert, the journal is
        // replayed on the next startup and the idempotency key prevents a
        // duplicate settlement.
        if (!appendPendingArSettlementJournal(idempotencyKey, playerUuid, playerName, amount, normalizedType, normalizedReason)) {
            getLogger().severe("Unable to durably journal pending AR settlement " + idempotencyKey + ". Manual review is required.");
            return CompletableFuture.completedFuture(new SettlementResult(false, idempotencyKey, "manual_review"));
        }
        return dbFuture("queue pending ar settlement", () -> {
            update(
                    "INSERT INTO cmv4_pending_ar_settlements(id,player_uuid,player_name,amount,settlement_type,status,reason,idempotency_key,created_at,updated_at,delivered_at) VALUES(?,?,?,?,?,?,?,?,?,?,0) ON CONFLICT(idempotency_key) DO NOTHING",
                    UUID.randomUUID().toString(),
                    playerUuid.toString(),
                    first(playerName, ""),
                    amount,
                    normalizedType,
                    PENDING_AR_SETTLEMENT_STATUS_PENDING,
                    normalizedReason,
                    idempotencyKey,
                    now(),
                    now()
            );
            return true;
        }).handle((ignored, error) -> {
            if (error != null) {
                getLogger().warning("Pending AR settlement remains in the durable journal " + idempotencyKey + ": " + safeError(error));
                return new SettlementResult(false, idempotencyKey, "manual_review");
            }
            try {
                removePendingArSettlementJournalEntry(idempotencyKey);
            } catch (Exception cleanupError) {
                // The PostgreSQL row is already durable.  Keep the local
                // journal on cleanup failure so startup replay remains a
                // safe idempotent no-op, and make the degraded state visible.
                getLogger().warning("Pending AR settlement was stored, but its local journal cleanup failed for "
                        + idempotencyKey + ": " + safeError(cleanupError));
            }
            return new SettlementResult(true, idempotencyKey, "queued");
        });
    }

    private boolean appendPendingArSettlementJournal(String idempotencyKey, UUID playerUuid, String playerName,
                                                     long amount, String settlementType, String reason) {
        Path target = pendingArSettlementJournalPath;
        if (target == null) {
            return false;
        }
        String line = String.join("\t",
                first(idempotencyKey, ""),
                playerUuid.toString(),
                encodeJournalValue(playerName),
                Long.toString(amount),
                encodeJournalValue(settlementType),
                encodeJournalValue(reason)) + "\n";
        synchronized (pendingArSettlementJournalLock) {
            try {
                Files.createDirectories(target.getParent());
                try (FileChannel channel = FileChannel.open(target,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                    ByteBuffer buffer = StandardCharsets.UTF_8.encode(line);
                    while (buffer.hasRemaining()) {
                        channel.write(buffer);
                    }
                    channel.force(true);
                }
                return true;
            } catch (Exception error) {
                getLogger().log(java.util.logging.Level.SEVERE, "Pending AR settlement journal append failed", error);
                return false;
            }
        }
    }

    private void flushPendingArSettlementJournal() throws Exception {
        synchronized (pendingArSettlementJournalLock) {
            Path target = pendingArSettlementJournalPath;
            if (target == null || !Files.isRegularFile(target)) {
                return;
            }
            List<String> lines = Files.readAllLines(target, StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                return;
            }
            Map<String, PendingArJournalRow> rows = new LinkedHashMap<>();
            for (String line : lines) {
            String[] parts = line.split("\\t", -1);
            if (parts.length != 6) {
                getLogger().warning("Ignoring malformed pending AR settlement journal row.");
                continue;
            }
            try {
                UUID uuid = UUID.fromString(parts[1]);
                long amount = Long.parseLong(parts[3]);
                if (amount <= 0 || amount > Integer.MAX_VALUE) {
                    continue;
                }
                rows.put(parts[0], new PendingArJournalRow(parts[0], uuid, decodeJournalValue(parts[2]), amount,
                        decodeJournalValue(parts[4]), decodeJournalValue(parts[5])));
            } catch (Exception ignored) {
                getLogger().warning("Ignoring invalid pending AR settlement journal row.");
            }
            }
            if (rows.isEmpty()) {
                rewritePendingArSettlementJournal(List.of());
                return;
            }
            tx(connection -> {
                for (PendingArJournalRow row : rows.values()) {
                    update(connection,
                            "INSERT INTO cmv4_pending_ar_settlements(id,player_uuid,player_name,amount,settlement_type,status,reason,idempotency_key,created_at,updated_at,delivered_at) VALUES(?,?,?,?,?,?,?,?,?,?,0) ON CONFLICT(idempotency_key) DO NOTHING",
                            UUID.randomUUID().toString(), row.playerUuid().toString(), first(row.playerName(), ""), row.amount(),
                            first(row.settlementType(), PENDING_AR_SETTLEMENT_TYPE_WITHDRAW_DELIVERY),
                            PENDING_AR_SETTLEMENT_STATUS_PENDING, first(row.reason(), ""), row.idempotencyKey(), now(), now());
                }
                return null;
            });
            // Keep the lock through replay and compaction so a concurrent
            // failed delivery cannot append a line that startup erases.
            rewritePendingArSettlementJournal(List.of());
            getLogger().info("Replayed " + rows.size() + " pending AR settlement journal row(s).");
        }
    }

    private void removePendingArSettlementJournalEntry(String idempotencyKey) throws Exception {
        if (idempotencyKey == null || idempotencyKey.isBlank() || pendingArSettlementJournalPath == null) {
            return;
        }
        synchronized (pendingArSettlementJournalLock) {
            Path target = pendingArSettlementJournalPath;
            if (!Files.isRegularFile(target)) {
                return;
            }
            List<String> keep = new ArrayList<>();
            for (String line : Files.readAllLines(target, StandardCharsets.UTF_8)) {
                String[] parts = line.split("\\t", -1);
                if (parts.length == 0 || !idempotencyKey.equals(parts[0])) {
                    keep.add(line);
                }
            }
            rewritePendingArSettlementJournal(keep);
        }
    }

    private void rewritePendingArSettlementJournal(List<String> lines) throws Exception {
        Path target = pendingArSettlementJournalPath;
        if (target == null) {
            return;
        }
        synchronized (pendingArSettlementJournalLock) {
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString() + ".", ".tmp");
            try {
                String text = lines == null || lines.isEmpty() ? "" : String.join("\n", lines) + "\n";
                byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
                try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    ByteBuffer buffer = ByteBuffer.wrap(bytes);
                    while (buffer.hasRemaining()) {
                        channel.write(buffer);
                    }
                    channel.force(true);
                }
                try {
                    Files.move(temporary, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                    Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private String encodeJournalValue(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(first(value, "").getBytes(StandardCharsets.UTF_8));
    }

    private String decodeJournalValue(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    private record PendingArJournalRow(String idempotencyKey, UUID playerUuid, String playerName, long amount,
                                       String settlementType, String reason) {}

    private String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(first(value, "").getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte current : digest) hex.append(String.format(Locale.ROOT, "%02x", current));
            return hex.toString();
        } catch (Exception error) {
            return UUID.nameUUIDFromBytes(first(value, "").getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
        }
    }

    private long arCapacity(Inventory inventory) {
        long capacity = 0L;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack == null || stack.getType().isAir()) {
                capacity += 64L;
                continue;
            }
            if (isOfficialAr(stack)) {
                capacity += Math.max(0, stack.getMaxStackSize() - stack.getAmount());
            }
        }
        return capacity;
    }

    private boolean issueOfficialArAmount(Player player, long amount, String source, boolean dropOverflow) {
        if (player == null || amount <= 0 || amount > Integer.MAX_VALUE || arCapacity(player.getInventory()) < amount) {
            return false;
        }
        long remaining = amount;
        List<String> issuedSerials = new ArrayList<>();
        while (remaining > 0) {
            int stackAmount = (int) Math.min(64L, remaining);
            ItemStack out = new OfficialArServiceImpl().createPreparedStack(Material.DIAMOND_ORE, stackAmount, source);
            String serial = officialArSerial(out);
            Map<Integer, ItemStack> left = player.getInventory().addItem(out);
            if (!left.isEmpty()) {
                // addItem() can partially succeed when another plugin changes
                // inventory limits between the capacity check and the write.
                // Roll back every stack issued by this attempt, not just the
                // last serial; otherwise the bank debit is queued for the
                // full amount while a prefix of that amount remains live.
                removeOfficialArSerial(player.getInventory(), serial, stackAmount);
                for (String issuedSerial : issuedSerials) {
                    removeOfficialArSerial(player.getInventory(), issuedSerial, 64);
                }
                player.updateInventory();
                return false;
            }
            issuedSerials.add(serial);
            remaining -= stackAmount;
        }
        player.updateInventory();
        return true;
    }

    private void completeWithdrawOnMainThread(Player player, long amount, String transactionId, String atmId, String accountScope) {
        if (player == null || !player.isOnline() || transactionId == null || transactionId.isBlank()
                || amount <= 0L || amount > Integer.MAX_VALUE) {
            if (player != null && player.isOnline()) {
                player.sendMessage(color("&eВыдача AR не подтверждена. Операция отправлена на ручную проверку."));
            }
            return;
        }
        reserveWithdrawalDelivery(transactionId).whenComplete((reservation, reserveError) -> Bukkit.getScheduler().runTask(this, () -> {
            if (reserveError != null || reservation == null || reservation.ids().isEmpty()) {
                getLogger().warning("Withdrawal delivery reservation failed for " + transactionId + ": " + safeError(reserveError));
                player.sendMessage(color("&eAR списан, но выдача не подтверждена. Операция отправлена на ручную проверку."));
                return;
            }
            if (!player.isOnline()) {
                dbAsync("withdrawal delivery release offline", () -> releasePendingArSettlements(reservation.ids(), reservation.token()));
                return;
            }
            prepareOfficialArIssuance("withdrawal:" + transactionId, Material.DIAMOND_ORE, Math.toIntExact(amount), "bank-withdraw")
                    .whenComplete((prepared, preparationError) -> Bukkit.getScheduler().runTask(this, () -> {
            if (preparationError != null || !Boolean.TRUE.equals(prepared)) {
                dbAsync("withdrawal issuance preparation review", () -> markPendingArSettlementsReview(reservation.ids(), reservation.token(), "issuance_prepare_failed"));
                player.sendMessage(color("&eВыдача AR отложена: выпуск не подтверждён реестром."));
                return;
            }
            if (!player.isOnline()) {
                dbAsync("withdrawal delivery release after issuance prepare", () -> releasePendingArSettlements(reservation.ids(), reservation.token()));
                return;
            }
            if (!issueOfficialArAmount(player, amount, "bank-withdraw", false)) {
                dbAsync("withdrawal delivery release full inventory", () -> releasePendingArSettlements(reservation.ids(), reservation.token()));
                player.sendMessage(color("&eБанк списал AR, но инвентарь заполнен. Сумма помещена в безопасную очередь выдачи."));
                return;
            }
            dbFuture("withdrawal delivery finalize", () -> {
                markPendingArSettlementsDelivered(reservation.ids(), reservation.token());
                return true;
            }).whenComplete((delivered, deliveryError) -> Bukkit.getScheduler().runTask(this, () -> {
                if (deliveryError != null || !Boolean.TRUE.equals(delivered)) {
                    dbAsync("withdrawal delivery review", () -> markPendingArSettlementsReview(reservation.ids(), reservation.token(), "withdrawal_finalize_failed"));
                    player.sendMessage(color("&eAR выдан физически, но регистрация не подтверждена. Операция отправлена на проверку."));
                    return;
                }
                player.updateInventory();
                player.sendMessage(color("&aОперация выполнена."));
                 openBankAtmAccount(player, first(atmId, ""), first(accountScope, "PERSONAL"));
             }));
                    }));
         }));
    }

    private void createWithdrawalDeliveryRow(Connection connection, UUID playerUuid, String playerName, long amount,
                                              String transactionId, String actionName, String details) throws Exception {
        if (playerUuid == null || amount <= 0L || transactionId == null || transactionId.isBlank()
                || !("TREASURY_WITHDRAW".equalsIgnoreCase(first(actionName, "")))) {
            return;
        }
        long createdAt = now();
        update(connection,
                "INSERT INTO cmv4_pending_ar_settlements(id,player_uuid,player_name,amount,settlement_type,status,reason,idempotency_key,created_at,updated_at,delivered_at) VALUES(?,?,?,?,?,?,?,?,?,?,0) ON CONFLICT(idempotency_key) DO NOTHING",
                "withdrawal-" + transactionId, playerUuid.toString(), first(playerName, ""), amount,
                PENDING_AR_SETTLEMENT_TYPE_WITHDRAW_DELIVERY, PENDING_AR_SETTLEMENT_STATUS_DEBITED,
                first(details, "") + ";tx=" + transactionId, transactionId, createdAt, createdAt);
    }

    private ItemStack createOfficialArStack(Material material, int amount, String ownerUuid, String ownerName,
                                            String source) {
        Material arMaterial = material == Material.DEEPSLATE_DIAMOND_ORE ? Material.DEEPSLATE_DIAMOND_ORE : Material.DIAMOND_ORE;
        int stackAmount = Math.max(1, Math.min(arMaterial.getMaxStackSize(), amount));
        ItemStack stack = new ItemStack(arMaterial, stackAmount);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setDisplayName(color("&bОфициальный AR"));
        meta.setLore(List.of());
        meta.addItemFlags(ItemFlag.values());
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(new NamespacedKey("copiminear", "type"), PersistentDataType.STRING, "certified");
        pdc.remove(new NamespacedKey("copiminear", "owner_uuid"));
        pdc.remove(new NamespacedKey("copiminear", "owner_name"));
        pdc.remove(new NamespacedKey("copiminear", "source"));
        String serial = arFungibleSerial(arMaterial);
        int denomination = 1;
        pdc.set(officialArSerialKey, PersistentDataType.STRING, serial);
        pdc.set(officialArDenominationKey, PersistentDataType.INTEGER, denomination);
        pdc.set(officialArSignatureVersionKey, PersistentDataType.INTEGER, OFFICIAL_AR_SIGNATURE_VERSION);
        pdc.set(officialArSignatureKey, PersistentDataType.STRING, officialArFungibleSignature(arMaterial));
        stack.setItemMeta(meta);
        return stack;
    }

    private String officialArSerial(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return "";
        }
        return first(stack.getItemMeta().getPersistentDataContainer().get(officialArSerialKey, PersistentDataType.STRING), "");
    }

    private String officialArSignature(String serial, Material material, int denomination) {
        if (serial == null || serial.isBlank() || material == null || denomination <= 0 || officialArSigningSecret == null) {
            return "";
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(officialArSigningSecret, "HmacSHA256"));
            byte[] digest = mac.doFinal((serial + "|" + material.name() + "|" + denomination + "|certified|v2").getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format(Locale.ROOT, "%02x", value));
            }
            return hex.toString();
        } catch (Exception error) {
            getLogger().warning("AR signature generation failed: " + safeError(error));
            return "";
        }
    }

    private boolean hasValidArSignature(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        String serial = first(pdc.get(officialArSerialKey, PersistentDataType.STRING), "");
        String signature = first(pdc.get(officialArSignatureKey, PersistentDataType.STRING), "");
        Integer denomination = pdc.get(officialArDenominationKey, PersistentDataType.INTEGER);
        Integer signatureVersion = pdc.get(officialArSignatureVersionKey, PersistentDataType.INTEGER);
        if (serial.isBlank() || signature.isBlank() || denomination == null || signatureVersion == null) {
            return false;
        }
        if (isFungibleArSerial(serial)) {
            if (!arFungibleSerial(stack.getType()).equals(serial)
                    || denomination != 1 || signatureVersion != OFFICIAL_AR_SIGNATURE_VERSION
                    || stack.getAmount() < 1 || stack.getAmount() > stack.getMaxStackSize()) {
                return false;
            }
            String expected = officialArFungibleSignature(stack.getType());
            return !expected.isBlank() && MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.US_ASCII), signature.getBytes(StandardCharsets.US_ASCII));
        }
        if (denomination != stack.getAmount() || signatureVersion != LEGACY_AR_SIGNATURE_VERSION) {
            return false;
        }
        try {
            UUID.fromString(serial);
        } catch (IllegalArgumentException invalidSerial) {
            return false;
        }
        String expected = officialArSignature(serial, stack.getType(), denomination);
        return !expected.isBlank() && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII), signature.getBytes(StandardCharsets.US_ASCII));
    }

    private String arFungibleSerial(Material material) {
        Material normalized = material == Material.DEEPSLATE_DIAMOND_ORE
                ? Material.DEEPSLATE_DIAMOND_ORE : Material.DIAMOND_ORE;
        return AR_FUNGIBLE_SERIAL_PREFIX + normalized.name() + ":v" + OFFICIAL_AR_SIGNATURE_VERSION;
    }

    private boolean isFungibleArSerial(String serial) {
        return serial != null
                && (serial.equals(arFungibleSerial(Material.DIAMOND_ORE))
                || serial.equals(arFungibleSerial(Material.DEEPSLATE_DIAMOND_ORE)));
    }

    private String officialArFungibleSignature(Material material) {
        if (material == null || officialArSigningSecret == null) {
            return "";
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(officialArSigningSecret, "HmacSHA256"));
            byte[] digest = mac.doFinal((AR_FUNGIBLE_SERIAL_PREFIX + material.name()
                    + "|certified|v" + OFFICIAL_AR_SIGNATURE_VERSION).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format(Locale.ROOT, "%02x", value));
            }
            return hex.toString();
        } catch (Exception error) {
            getLogger().warning("Fungible AR signature generation failed: " + safeError(error));
            return "";
        }
    }

    private CompletableFuture<Boolean> prepareOfficialArIssuance(String issuanceKey, Material material, int amount, String source) {
        return new OfficialArServiceImpl().prepareIssuanceAsync(issuanceKey, material, amount, source);
    }

    private List<ItemStack> snapshotOfficialArStacks(Inventory inventory) {
        List<ItemStack> snapshots = new ArrayList<>();
        if (inventory == null) {
            return snapshots;
        }
        for (ItemStack stack : inventory.getContents()) {
            if (isOfficialAr(stack)) {
                snapshots.add(stack.clone());
            }
        }
        return snapshots;
    }

    private List<ArFragment> snapshotOfficialArFragments(Inventory inventory) {
        List<ArFragment> snapshots = new ArrayList<>();
        if (inventory == null) {
            return snapshots;
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isOfficialAr(stack)) {
                snapshots.add(new ArFragment(slot, stack.clone()));
            }
        }
        return snapshots;
    }

    /** Restore only the failed AR deposit, never overwrite intervening player changes. */
    private int restoreOfficialArSafely(Player player, ItemStack snapshot, String serial) {
        if (player == null || snapshot == null || !isOfficialAr(snapshot)) {
            return 0;
        }
        int wanted = Math.max(0, snapshot.getAmount());
        int alreadyPresent = serial.isBlank() ? 0 : countOfficialArSerial(player.getInventory(), serial);
        int missing = Math.max(0, wanted - alreadyPresent);
        if (missing == 0) {
            return 0;
        }
        ItemStack restore = snapshot.clone();
        restore.setAmount(missing);
        Map<Integer, ItemStack> left = player.getInventory().addItem(restore);
        int notRestored = left.values().stream().mapToInt(ItemStack::getAmount).sum();
        player.updateInventory();
        return notRestored;
    }

    /** Restore to the original hand slot first; never overwrite a new item. */
    private int restoreOfficialArAtSlot(Player player, ItemStack snapshot, String serial, int slot) {
        if (player == null || snapshot == null || !isOfficialAr(snapshot)) {
            return 0;
        }
        PlayerInventory inventory = player.getInventory();
        int wanted = Math.max(0, snapshot.getAmount());
        int alreadyPresent = serial.isBlank() ? 0 : countOfficialArSerial(inventory, serial);
        int missing = Math.max(0, wanted - alreadyPresent);
        if (missing == 0) {
            return 0;
        }
        if (slot >= 0 && slot < inventory.getSize()) {
            ItemStack current = inventory.getItem(slot);
            String currentSerial = officialArSerial(current);
            if (isOfficialAr(current) && serial.equals(currentSerial)) {
                int room = Math.max(0, current.getMaxStackSize() - current.getAmount());
                int add = Math.min(room, missing);
                if (add > 0) {
                    current.setAmount(current.getAmount() + add);
                    inventory.setItem(slot, current);
                    missing -= add;
                }
            } else if (current == null || current.getType().isAir()) {
                ItemStack restored = snapshot.clone();
                int add = Math.min(restored.getMaxStackSize(), missing);
                restored.setAmount(add);
                inventory.setItem(slot, restored);
                missing -= add;
            }
        }
        if (missing > 0) {
            ItemStack restore = snapshot.clone();
            restore.setAmount(missing);
            Map<Integer, ItemStack> left = inventory.addItem(restore);
            missing = left.values().stream().mapToInt(ItemStack::getAmount).sum();
        }
        player.updateInventory();
        return missing;
    }

    private int restoreOfficialArFragments(Player player, List<ArFragment> fragments) {
        int notRestored = 0;
        if (fragments == null) {
            return 0;
        }
        for (ArFragment fragment : fragments) {
            if (fragment == null) continue;
            notRestored += restoreOfficialArAtSlot(player, fragment.snapshot(), officialArSerial(fragment.snapshot()), fragment.slot());
        }
        return notRestored;
    }

    private int restoreOfficialArSafely(Player player, List<ItemStack> snapshots) {
        int notRestored = 0;
        if (snapshots == null) {
            return 0;
        }
        for (ItemStack snapshot : snapshots) {
            notRestored += restoreOfficialArSafely(player, snapshot, officialArSerial(snapshot));
        }
        return notRestored;
    }

    private int countOfficialArSerial(Inventory inventory, String serial) {
        if (inventory == null || serial == null || serial.isBlank()) {
            return 0;
        }
        int total = 0;
        for (ItemStack stack : inventory.getContents()) {
            if (isOfficialAr(stack) && serial.equals(officialArSerial(stack))) {
                total += stack.getAmount();
            }
        }
        ItemStack offhand = inventory instanceof org.bukkit.inventory.PlayerInventory playerInventory
                ? playerInventory.getItemInOffHand() : null;
        if (isOfficialAr(offhand) && serial.equals(officialArSerial(offhand))) {
            total += offhand.getAmount();
        }
        return total;
    }

    private void removeOfficialArSerial(Inventory inventory, String serial, int amount) {
        if (inventory == null || serial == null || serial.isBlank() || amount <= 0) {
            return;
        }
        int left = amount;
        for (int slot = 0; slot < inventory.getSize() && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!isOfficialAr(stack) || !serial.equals(officialArSerial(stack))) {
                continue;
            }
            int take = Math.min(left, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            inventory.setItem(slot, stack.getAmount() <= 0 ? null : stack);
            left -= take;
        }
        if (inventory instanceof org.bukkit.inventory.PlayerInventory playerInventory && left > 0) {
            ItemStack offhand = playerInventory.getItemInOffHand();
            if (isOfficialAr(offhand) && serial.equals(officialArSerial(offhand))) {
                int take = Math.min(left, offhand.getAmount());
                offhand.setAmount(offhand.getAmount() - take);
                playerInventory.setItemInOffHand(offhand.getAmount() <= 0 ? null : offhand);
            }
        }
    }

    private void normalizeOfficialArItems(Player player) {
        if (player == null) {
            return;
        }
        boolean updated = normalizeOfficialArInventory(player.getInventory());
        updated = normalizeOfficialArInventory(player.getEnderChest()) || updated;
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (isOfficialAr(offHand) && needsOfficialArNormalization(offHand)) {
            player.getInventory().setItemInOffHand(normalizeOfficialArStack(offHand, "join-normalize"));
            updated = true;
        }
        if (updated) {
            player.updateInventory();
        }
    }

    private boolean normalizeOfficialArInventory(Inventory inventory) {
        if (inventory == null) {
            return false;
        }
        boolean updated = false;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!isOfficialAr(stack) || !needsOfficialArNormalization(stack)) {
                continue;
            }
            inventory.setItem(slot, normalizeOfficialArStack(stack, "normalize"));
            updated = true;
        }
        return updated;
    }

    private ItemStack normalizeOfficialArStack(ItemStack source, String reason) {
        ItemStack normalized = createOfficialArStack(source.getType(), Math.max(1, source.getAmount()), "", "", reason);
        String serial = officialArSerial(source);
        if (!serial.isBlank() && normalized.hasItemMeta()) {
            ItemMeta meta = normalized.getItemMeta();
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            Integer denomination = source.getItemMeta().getPersistentDataContainer()
                    .get(officialArDenominationKey, PersistentDataType.INTEGER);
            Integer signatureVersion = source.getItemMeta().getPersistentDataContainer()
                    .get(officialArSignatureVersionKey, PersistentDataType.INTEGER);
            pdc.set(officialArSerialKey, PersistentDataType.STRING, serial);
            if (denomination != null) {
                pdc.set(officialArDenominationKey, PersistentDataType.INTEGER, denomination);
            }
            if (signatureVersion != null) {
                pdc.set(officialArSignatureVersionKey, PersistentDataType.INTEGER, signatureVersion);
            }
            pdc.set(officialArSignatureKey, PersistentDataType.STRING,
                    first(source.getItemMeta().getPersistentDataContainer()
                            .get(officialArSignatureKey, PersistentDataType.STRING), ""));
            normalized.setItemMeta(meta);
        }
        return normalized;
    }

    private boolean needsOfficialArNormalization(ItemStack stack) {
        if (!isOfficialAr(stack)) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (!color("&bОфициальный AR").equals(meta.getDisplayName())) {
            return true;
        }
        if (meta.lore() != null && !meta.lore().isEmpty()) {
            return true;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(new NamespacedKey("copiminear", "owner_uuid"), PersistentDataType.STRING)
                || pdc.has(new NamespacedKey("copiminear", "owner_name"), PersistentDataType.STRING)
                || pdc.has(new NamespacedKey("copiminear", "source"), PersistentDataType.STRING)
                || !pdc.has(officialArSerialKey, PersistentDataType.STRING)
                || !hasValidArSignature(stack);
    }

    private int countOfficialAr(Inventory inventory) {
        int total = 0;
        for (ItemStack stack : inventory.getContents()) {
            if (isOfficialAr(stack)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private void removeOfficialAr(Inventory inventory, int amount) {
        int left = amount;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || !isOfficialAr(stack)) {
                continue;
            }
            int take = Math.min(left, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            if (stack.getAmount() <= 0) {
                inventory.setItem(slot, null);
            } else {
                inventory.setItem(slot, stack);
            }
            left -= take;
            if (left <= 0) {
                return;
            }
        }
    }

    private boolean isOfficialAr(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        if (stack.getType() != Material.DIAMOND_ORE && stack.getType() != Material.DEEPSLATE_DIAMOND_ORE) {
            return false;
        }
        if (!stack.hasItemMeta()) {
            return false;
        }
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        String kind = pdc.get(new NamespacedKey("copiminear", "type"), PersistentDataType.STRING);
        if (!"certified".equalsIgnoreCase(kind)) {
            return false;
        }
        return hasValidArSignature(stack);
    }

    private void spawnOrReplaceProtectedBlockVisual(Location blockLocation, String kind, String linkedId, Material baseMaterial, int customModelData, String modelId) throws Exception {
        Map<String, Object> current = fetchProtectedBlockVisualRow(kind, linkedId);
        Bukkit.getScheduler().runTask(this, () -> {
            try {
                cleanupProtectedBlockVisualEntities(current, kind, linkedId);
                Location displayLocation = blockLocation.clone().add(0.5, 0.5, 0.5);
                ItemStack visualItem = new ItemStack(baseMaterial);
                ItemMeta meta = visualItem.getItemMeta();
                if (meta == null) {
                    return;
                }
                meta.setCustomModelData(customModelData);
                meta.setDisplayName(color("&f" + modelId));
                meta.addItemFlags(ItemFlag.values());
                visualItem.setItemMeta(meta);
                ItemDisplay display = blockLocation.getWorld().spawn(displayLocation, ItemDisplay.class, entity -> {
                    entity.setItemStack(visualItem);
                    entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                    entity.setPersistent(true);
                    entity.setBillboard(Display.Billboard.FIXED);
                    entity.getPersistentDataContainer().set(visualEntityTypeKey, PersistentDataType.STRING, "PROTECTED_BLOCK_VISUAL");
                    entity.getPersistentDataContainer().set(visualKindKey, PersistentDataType.STRING, kind);
                    entity.getPersistentDataContainer().set(visualLinkedIdKey, PersistentDataType.STRING, linkedId);
                    entity.getPersistentDataContainer().set(visualModelIdKey, PersistentDataType.STRING, modelId);
                    entity.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1.01f, 1.01f, 1.01f), new AxisAngle4f()));
                });
                saveProtectedBlockVisualAsync(kind, linkedId, blockLocation, display.getUniqueId(), baseMaterial, customModelData, modelId);
            } catch (Exception error) {
                getLogger().warning("ATM visual spawn: " + safeError(error));
            }
        });
    }

    private void cleanupProtectedBlockVisuals(String kind, String linkedId) throws Exception {
        dbFuture("load atm visual cleanup", () -> fetchProtectedBlockVisualRow(kind, linkedId))
                .whenComplete((row, error) -> {
                    if (error != null) {
                        getLogger().warning("ATM visual cleanup: " + safeError(error));
                        return;
                    }
                    Bukkit.getScheduler().runTask(this, () -> {
                        try {
                            cleanupProtectedBlockVisualEntities(row, kind, linkedId);
                        } catch (Exception entityError) {
                            getLogger().warning("ATM visual cleanup entities: " + safeError(entityError));
                        }
                    });
                    dbAsync("mark atm visual inactive", () -> markProtectedBlockVisualInactive(kind, linkedId));
                });
    }

    private void repairProtectedBlockVisuals() throws Exception {
        List<String> chunks = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                chunks.add(world.getName() + ":" + chunk.getX() + ":" + chunk.getZ());
            }
        }
        if (chunks.isEmpty()) {
            return;
        }
        dbAsync("repair atm visuals startup", () -> {
            List<AtmVisualRepairBatch> batches = new ArrayList<>();
            for (String entry : chunks) {
                String[] parts = entry.split(":");
                if (parts.length != 3) {
                    continue;
                }
                int chunkX = parseInt(parts[1], 0);
                int chunkZ = parseInt(parts[2], 0);
                List<Map<String, Object>> rows = loadAtmVisualRepairRows(parts[0], parseInt(parts[1], 0), parseInt(parts[2], 0));
                if (rows.isEmpty()) {
                    continue;
                }
                batches.add(new AtmVisualRepairBatch(parts[0], chunkX, chunkZ, List.copyOf(rows)));
            }
            if (batches.isEmpty()) {
                return;
            }
            Bukkit.getScheduler().runTask(this, () -> drainAtmVisualRepairQueue(batches));
        });
    }

    private void repairProtectedBlockVisuals(String worldName, int chunkX, int chunkZ) throws Exception {
        dbAsync("repair atm visuals chunk", () -> {
            List<Map<String, Object>> rows = loadAtmVisualRepairRows(worldName, chunkX, chunkZ);
            if (rows.isEmpty()) {
                return;
            }
            Bukkit.getScheduler().runTask(this, () -> applyAtmVisualRepairs(worldName, chunkX, chunkZ, rows));
        });
    }

    private List<Map<String, Object>> loadAtmVisualRepairRows(String worldName, int chunkX, int chunkZ) throws Exception {
        return queryList(
                "SELECT a.id,a.world,a.x,a.y,a.z,COALESCE(pbv.entity_uuid,'') entity_uuid,COALESCE(pbv.model_id,'') model_id,COALESCE(pbv.custom_model_data,0) custom_model_data " +
                        "FROM ar_atms a LEFT JOIN protected_block_visuals pbv ON pbv.kind='ATM' AND pbv.linked_id=a.id AND pbv.active=1 " +
                        "WHERE a.active=1 AND a.world=? AND FLOOR(a.x / 16.0)=? AND FLOOR(a.z / 16.0)=?",
                worldName, chunkX, chunkZ);
    }

    private void applyAtmVisualRepairs(String worldName, int chunkX, int chunkZ, List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            try {
                World world = Bukkit.getWorld(worldName);
                if (world == null || !world.isChunkLoaded(chunkX, chunkZ)) {
                    continue;
                }
                Location location = new Location(world, intValue(row.get("x")), intValue(row.get("y")), intValue(row.get("z")));
                String linkedId = string(row.get("id"));
                if (world.getBlockAt(location).getType().isAir()) {
                    evictAtmCache(linkedId);
                    dbAsync("archive ATM visual orphan", () -> update(
                            "UPDATE ar_atms SET active=0,archived_by='SYSTEM_ANCHOR_MISSING',archived_at=? WHERE id=? AND active=1",
                            now(), linkedId));
                    continue;
                }
                Entity entity = findEntityByUuid(string(row.get("entity_uuid")));
                boolean valid = isOwnedProtectedVisualEntity(entity, "ATM", linkedId, "atm_terminal", MODEL_ATM_TERMINAL);
                if (!valid) {
                    spawnOrReplaceProtectedBlockVisual(location, "ATM", linkedId, Material.PAPER, MODEL_ATM_TERMINAL, "atm_terminal");
                }
            } catch (Exception error) {
                getLogger().warning("ATM visual repair row: " + safeError(error));
            }
        }
    }

    private void drainAtmVisualRepairQueue(List<AtmVisualRepairBatch> batches) {
        if (batches == null || batches.isEmpty()) {
            return;
        }
        ArrayDeque<AtmVisualRepairBatch> queue = new ArrayDeque<>(batches);
        Bukkit.getScheduler().runTaskTimer(this, task -> {
            int remaining = 8;
            while (remaining-- > 0 && !queue.isEmpty()) {
                AtmVisualRepairBatch batch = queue.removeFirst();
                applyAtmVisualRepairs(batch.worldName(), batch.chunkX(), batch.chunkZ(), batch.rows());
            }
            if (queue.isEmpty()) {
                task.cancel();
            }
        }, 1L, 1L);
    }

    private boolean isOwnedProtectedVisualEntity(Entity entity, String kind, String linkedId, String modelId, int customModelData) {
        if (!(entity instanceof ItemDisplay display)) {
            return false;
        }
        PersistentDataContainer pdc = display.getPersistentDataContainer();
        if (!"PROTECTED_BLOCK_VISUAL".equals(pdc.get(visualEntityTypeKey, PersistentDataType.STRING))) {
            return false;
        }
        if (!kind.equals(first(pdc.get(visualKindKey, PersistentDataType.STRING), ""))) {
            return false;
        }
        if (!linkedId.equals(first(pdc.get(visualLinkedIdKey, PersistentDataType.STRING), ""))) {
            return false;
        }
        if (!modelId.equals(first(pdc.get(visualModelIdKey, PersistentDataType.STRING), ""))) {
            return false;
        }
        ItemStack stack = display.getItemStack();
        return stack != null && stack.hasItemMeta() && stack.getItemMeta() != null && stack.getItemMeta().hasCustomModelData() && stack.getItemMeta().getCustomModelData() == customModelData;
    }

    private Entity findEntityByUuid(String uuidText) {
        if (uuidText == null || uuidText.isBlank()) {
            return null;
        }
        try {
            UUID uuid = UUID.fromString(uuidText);
            for (World world : Bukkit.getWorlds()) {
                Entity entity = world.getEntity(uuid);
                if (entity != null) {
                    return entity;
                }
            }
        } catch (Exception error) {
            getLogger().warning("find entity by uuid=" + uuidText + " failed: " + safeError(error));
        }
        return null;
    }

    private Map<String, Object> fetchProtectedBlockVisualRow(String kind, String linkedId) throws Exception {
        return queryOne("SELECT entity_uuid,world,x,y,z FROM protected_block_visuals WHERE kind=? AND linked_id=? ORDER BY active DESC,updated_at DESC LIMIT 1", kind, linkedId);
    }

    private void cleanupProtectedBlockVisualEntities(Map<String, Object> row, String kind, String linkedId) {
        if (row != null) {
            Entity entity = findEntityByUuid(string(row.get("entity_uuid")));
            if (isOwnedProtectedVisualEntity(entity, kind, linkedId, "atm_terminal", MODEL_ATM_TERMINAL)) {
                entity.remove();
            }
            World world = Bukkit.getWorld(string(row.get("world")));
            if (world != null) {
                Location base = new Location(world, intValue(row.get("x")) + 0.5, intValue(row.get("y")) + 0.5, intValue(row.get("z")) + 0.5);
                for (ItemDisplay display : world.getNearbyEntitiesByType(ItemDisplay.class, base, 1.5, 1.5, 1.5)) {
                    if (isOwnedProtectedVisualEntity(display, kind, linkedId, "atm_terminal", MODEL_ATM_TERMINAL)) {
                        display.remove();
                    }
                }
            }
        }
    }

    private void saveProtectedBlockVisualAsync(String kind, String linkedId, Location location, UUID entityUuid, Material baseMaterial, int customModelData, String modelId) {
        dbAsync("save atm visual", () -> tx(connection -> {
            update(connection, "DELETE FROM protected_block_visuals WHERE kind=? AND linked_id=?", kind, linkedId);
            update(connection, "INSERT INTO protected_block_visuals(id,kind,linked_id,world,x,y,z,entity_uuid,base_material,custom_model_data,model_id,offset_x,offset_y,offset_z,scale_x,scale_y,scale_z,yaw,pitch,created_at,updated_at,active) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,1)",
                    "pbv_" + UUID.randomUUID().toString().replace("-", ""), kind, linkedId, location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ(),
                    entityUuid.toString(), baseMaterial.name(), customModelData, modelId, 0.5D, 0.5D, 0.5D, 1.01D, 1.01D, 1.01D, 0D, 0D, now(), now());
            return null;
        }));
    }

    private void markProtectedBlockVisualInactive(String kind, String linkedId) throws Exception {
        update("UPDATE protected_block_visuals SET active=0,updated_at=? WHERE kind=? AND linked_id=? AND active=1", now(), kind, linkedId);
    }

    private List<Map<String, Object>> personalPinRows(String uuid) throws Exception {
        String playerKey = first(uuid, "").trim();
        if (playerKey.isBlank()) {
            return List.of();
        }
        return queryList(
                "SELECT pin_hash,must_change FROM bank_pin_hashes WHERE minecraft_uuid=? "
                        + "UNION ALL "
                        + "SELECT pin_hash,must_change FROM bank_account_pins WHERE account_id=?",
                playerKey,
                bankAccountId(playerKey));
    }

    private boolean bankPinSet(String uuid) throws Exception {
        return personalPinRows(uuid).stream().anyMatch(row -> !first(string(row.get("pin_hash")), "").isBlank());
    }

    private boolean bankPinMustChange(String uuid) throws Exception {
        List<Map<String, Object>> rows = personalPinRows(uuid);
        // Keep the personal table authoritative when it exists; the account
        // table is a compatibility fallback for accounts created by newer
        // website flows or older plugin versions.
        for (Map<String, Object> row : rows) {
            if (!first(string(row.get("pin_hash")), "").isBlank()) {
                return longValue(row.get("must_change")) > 0L;
            }
        }
        return false;
    }

    private long bankPinLockedSeconds(String uuid) throws Exception {
        long until = scalarLong("SELECT COALESCE(locked_until,0) FROM account_lockouts WHERE account_id=?", bankPinLockoutKey(uuid));
        return Math.max(0L, until - nowSec());
    }

    private boolean verifyBankPin(String uuid, String pin) throws Exception {
        if (pin == null || !pin.matches("\\d{4,8}")) {
            return false;
        }
        String candidate = pin.trim();
        List<Map<String, Object>> rows = personalPinRows(uuid);
        for (int index = 0; index < rows.size(); index++) {
            Map<String, Object> row = rows.get(index);
            String stored = first(string(row.get("pin_hash")), "").trim();
            if (stored.isBlank() || !verifyPinHash(stored, candidate)) {
                continue;
            }
            // If the account-level row was the one that matched, migrate it
            // to the personal table used by legacy ATM and shop clients.  The
            // reverse migration also keeps the two stores in sync after an
            // old plugin wrote only bank_pin_hashes.
            try {
                synchronizePersonalPinHash(uuid, stored);
            } catch (Exception migrationError) {
                getLogger().fine("Personal PIN compatibility migration skipped: " + safeError(migrationError));
            }
            update("DELETE FROM account_lockouts WHERE account_id=?", bankPinLockoutKey(uuid));
            return true;
        }
        return false;
    }

    private boolean verifyPinHash(String stored, String pin) throws Exception {
        if (stored == null || !stored.startsWith("pbkdf2_sha256$")) {
            return false;
        }
        String[] parts = stored.split("\\$", 4);
        if (parts.length != 4) {
            return false;
        }
        int iterations = Integer.parseInt(parts[1]);
        if (iterations < 1 || iterations > 10_000_000) {
            return false;
        }
        byte[] salt = decodePinSalt(parts[2]);
        String expected = parts[3].toLowerCase(Locale.ROOT);
        if (expected.isBlank() || (expected.length() & 1) != 0 || !expected.matches("[0-9a-f]+")) {
            return false;
        }
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, iterations, Math.max(128, expected.length() * 4));
        byte[] got = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        return MessageDigest.isEqual(hex(got).getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] decodePinSalt(String encoded) {
        String value = first(encoded, "");
        if (value.startsWith("base64:")) {
            try {
                return Base64.getDecoder().decode(value.substring("base64:".length()));
            } catch (IllegalArgumentException ignored) {
                return new byte[0];
            }
        }
        if (value.startsWith("hex:")) {
            return decodeHex(value.substring("hex:".length()));
        }
        // The website's canonical format intentionally stores the random
        // salt as a 32-character hex string and hashes those UTF-8 characters
        // (rather than decoding them).  Keep that behavior as the default.
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] decodeHex(String value) {
        if (value.isBlank() || (value.length() & 1) != 0 || !value.matches("[0-9a-fA-F]+")) {
            return new byte[0];
        }
        byte[] result = new byte[value.length() / 2];
        for (int index = 0; index < result.length; index++) {
            int high = Character.digit(value.charAt(index * 2), 16);
            int low = Character.digit(value.charAt(index * 2 + 1), 16);
            result[index] = (byte) ((high << 4) | low);
        }
        return result;
    }

    private void synchronizePersonalPinHash(String uuid, String pinHash) throws Exception {
        String playerKey = first(uuid, "").trim();
        if (playerKey.isBlank() || pinHash == null || pinHash.isBlank()) {
            return;
        }
        long stamp = now();
        update(
                "INSERT INTO bank_pin_hashes(minecraft_uuid,site_account_id,pin_hash,must_change,created_at,updated_at) "
                        + "VALUES(?,?,?,0,?,?) "
                        + "ON CONFLICT(minecraft_uuid) DO UPDATE SET pin_hash=excluded.pin_hash,must_change=0,updated_at=excluded.updated_at",
                playerKey, "", pinHash, stamp, stamp);
        update(
                "INSERT INTO bank_account_pins(account_id,pin_hash,must_change,created_at,updated_at,updated_by) "
                        + "VALUES(?,?,0,?,?,?) "
                        + "ON CONFLICT(account_id) DO UPDATE SET pin_hash=excluded.pin_hash,must_change=0,updated_at=excluded.updated_at,updated_by=excluded.updated_by",
                bankAccountId(playerKey), pinHash, stamp, stamp, "economy-core-pin-sync");
    }

    private void synchronizePersonalPinHash(Connection connection, String uuid, String pinHash) throws Exception {
        String playerKey = first(uuid, "").trim();
        if (playerKey.isBlank() || pinHash == null || pinHash.isBlank()) {
            return;
        }
        long stamp = now();
        update(connection,
                "INSERT INTO bank_pin_hashes(minecraft_uuid,site_account_id,pin_hash,must_change,created_at,updated_at) "
                        + "VALUES(?,?,?,0,?,?) "
                        + "ON CONFLICT(minecraft_uuid) DO UPDATE SET pin_hash=excluded.pin_hash,must_change=0,updated_at=excluded.updated_at",
                playerKey, "", pinHash, stamp, stamp);
        update(connection,
                "INSERT INTO bank_account_pins(account_id,pin_hash,must_change,created_at,updated_at,updated_by) "
                        + "VALUES(?,?,0,?,?,?) "
                        + "ON CONFLICT(account_id) DO UPDATE SET pin_hash=excluded.pin_hash,must_change=0,updated_at=excluded.updated_at,updated_by=excluded.updated_by",
                bankAccountId(playerKey), pinHash, stamp, stamp, "economy-core-pin-sync");
    }

    private void recordFailedPinAttempt(Player player, String source) {
        String uuid = player.getUniqueId().toString();
        long t = nowSec();
        dbAsync("record failed bank pin", () -> {
            update("INSERT INTO failed_pin_attempts(minecraft_uuid,site_account_id,attempted_at,source) VALUES(?,?,?,?)", uuid, "", t, first(source, "economy-core"));
            long attempts = scalarLong("SELECT COUNT(*) FROM failed_pin_attempts WHERE minecraft_uuid=? AND attempted_at>=?", uuid, t - PIN_ATTEMPT_WINDOW_SECONDS);
            if (attempts >= PIN_MAX_ATTEMPTS) {
                long until = t + PIN_LOCK_SECONDS;
                update("INSERT INTO account_lockouts(account_id,locked_until,reason,updated_at) VALUES(?,?,?,?) ON CONFLICT(account_id) DO UPDATE SET locked_until=GREATEST(account_lockouts.locked_until,excluded.locked_until),reason=excluded.reason,updated_at=excluded.updated_at",
                        bankPinLockoutKey(uuid), until, "bank-pin", t);
            }
            return;
        });
    }

    private void recordFailedAccountPinAttempt(String accountId, String source) {
        long t = nowSec();
        dbAsync("record failed account pin", () -> {
            update("INSERT INTO failed_pin_attempts(minecraft_uuid,site_account_id,attempted_at,source) VALUES(?,?,?,?)", "", first(accountId, ""), t, first(source, "economy-core-account"));
            long attempts = scalarLong("SELECT COUNT(*) FROM failed_pin_attempts WHERE site_account_id=? AND attempted_at>=?", first(accountId, ""), t - PIN_ATTEMPT_WINDOW_SECONDS);
            if (attempts >= PIN_MAX_ATTEMPTS) {
                long until = t + PIN_LOCK_SECONDS;
                update("INSERT INTO account_lockouts(account_id,locked_until,reason,updated_at) VALUES(?,?,?,?) ON CONFLICT(account_id) DO UPDATE SET locked_until=GREATEST(account_lockouts.locked_until,excluded.locked_until),reason=excluded.reason,updated_at=excluded.updated_at",
                        accountPinLockoutKey(accountId), until, "bank-account-pin", t);
            }
            return;
        });
    }

    private String bankPinLockoutKey(String uuid) {
        return "bank-pin:" + uuid;
    }

    private String accountPinLockoutKey(String accountId) {
        return "bank-pin:account:" + first(accountId, "");
    }

    private boolean accountPinSet(String accountId) throws Exception {
        return scalarLong("SELECT COUNT(*) FROM bank_account_pins WHERE account_id=? AND COALESCE(pin_hash,'')<>''", accountId) > 0;
    }

    private boolean accountPinMustChange(String accountId) throws Exception {
        return scalarLong("SELECT COALESCE(must_change,0) FROM bank_account_pins WHERE account_id=? LIMIT 1", accountId) > 0;
    }

    private long accountPinLockedSeconds(String accountId) throws Exception {
        long until = scalarLong("SELECT COALESCE(locked_until,0) FROM account_lockouts WHERE account_id=?", accountPinLockoutKey(accountId));
        return Math.max(0L, until - nowSec());
    }

    private boolean verifyAccountPinHash(String accountId, String pin) throws Exception {
        if (pin == null || !pin.matches("\\d{4,8}")) {
            return false;
        }
        List<Map<String, Object>> rows = queryList("SELECT pin_hash FROM bank_account_pins WHERE account_id=? LIMIT 1", accountId);
        if (rows.isEmpty()) {
            return false;
        }
        boolean ok = verifyPinHash(string(rows.getFirst().get("pin_hash")), pin);
        if (ok) {
            update("DELETE FROM account_lockouts WHERE account_id=?", accountPinLockoutKey(accountId));
        }
        return ok;
    }

    private record PinVerification(boolean valid, String code, String message) {
        static PinVerification ok() {
            return new PinVerification(true, "OK", "");
        }

        static PinVerification failed(String code, String message) {
            return new PinVerification(false, code, message);
        }
    }

    /**
     * Verify an account PIN, update lockout state, and keep the row lock until
     * the caller's balance mutation commits.  The old implementation checked
     * the PIN in a separate connection and recorded failures asynchronously,
     * which allowed concurrent attempts to race the lockout boundary.
     */
    private PinVerification verifyAccountPinForMutation(Connection connection, String accountId,
                                                        String pin, String source) throws Exception {
        String normalizedAccountId = first(accountId, "");
        if (!pinMatchesPolicy(pin)) {
            return PinVerification.failed("PIN_INVALID", "Неверный PIN казны.");
        }
        long nowSeconds = nowSec();
        Map<String, Object> lock = queryOne(connection,
                "SELECT locked_until FROM account_lockouts WHERE account_id=? FOR UPDATE",
                accountPinLockoutKey(normalizedAccountId));
        if (lock != null && longValue(lock.get("locked_until")) > nowSeconds) {
            return PinVerification.failed("PIN_LOCKED", "PIN казны временно заблокирован.");
        }
        Map<String, Object> row = queryOne(connection,
                "SELECT pin_hash,must_change FROM bank_account_pins WHERE account_id=? FOR UPDATE",
                normalizedAccountId);
        if (row == null || first(string(row.get("pin_hash")), "").isBlank()) {
            return PinVerification.failed("PIN_REQUIRED", "Для казны PIN ещё не задан.");
        }
        if (longValue(row.get("must_change")) > 0L) {
            return PinVerification.failed("PIN_CHANGE_REQUIRED", "PIN казны нужно обновить на сайте.");
        }
        boolean valid;
        try {
            valid = verifyPinHash(string(row.get("pin_hash")), pin.trim());
        } catch (Exception malformedHash) {
            getLogger().warning("Treasury PIN hash rejected: " + safeError(malformedHash));
            valid = false;
        }
        if (valid) {
            update(connection, "DELETE FROM account_lockouts WHERE account_id=?",
                    accountPinLockoutKey(normalizedAccountId));
            return PinVerification.ok();
        }
        update(connection,
                "INSERT INTO failed_pin_attempts(minecraft_uuid,site_account_id,attempted_at,source) VALUES(?,?,?,?)",
                "", normalizedAccountId, nowSeconds, first(source, "economy-account-pin"));
        long attempts = scalarLong(connection,
                "SELECT COUNT(*) FROM failed_pin_attempts WHERE site_account_id=? AND attempted_at>=?",
                normalizedAccountId, nowSeconds - PIN_ATTEMPT_WINDOW_SECONDS);
        if (attempts >= PIN_MAX_ATTEMPTS) {
            long lockedUntil = nowSeconds + PIN_LOCK_SECONDS;
            update(connection,
                    "INSERT INTO account_lockouts(account_id,locked_until,reason,updated_at) VALUES(?,?,?,?) "
                            + "ON CONFLICT(account_id) DO UPDATE SET locked_until=GREATEST(account_lockouts.locked_until,excluded.locked_until),reason=excluded.reason,updated_at=excluded.updated_at",
                    accountPinLockoutKey(normalizedAccountId), lockedUntil, "bank-account-pin", nowSeconds);
        }
        return PinVerification.failed("PIN_INVALID", "Неверный PIN казны.");
    }

    /**
     * Personal PIN verification for a balance mutation.  The lockout row,
     * both compatibility PIN rows, failed attempts, and the eventual ledger
     * mutation are held by the caller's transaction.
     */
    private PinVerification verifyPersonalPinForMutation(Connection connection, String uuid,
                                                         String pin, String source) throws Exception {
        String playerKey = first(uuid, "").trim();
        if (playerKey.isBlank() || !pinMatchesPolicy(pin)) {
            return PinVerification.failed("PIN_INVALID", "Personal bank PIN is invalid.");
        }
        long nowSeconds = nowSec();
        String lockoutKey = bankPinLockoutKey(playerKey);
        Map<String, Object> lock = queryOne(connection,
                "SELECT locked_until FROM account_lockouts WHERE account_id=? FOR UPDATE", lockoutKey);
        if (lock != null && longValue(lock.get("locked_until")) > nowSeconds) {
            return PinVerification.failed("PIN_LOCKED", "Personal bank PIN is temporarily locked.");
        }
        Map<String, Object> personal = queryOne(connection,
                "SELECT pin_hash,must_change FROM bank_pin_hashes WHERE minecraft_uuid=? FOR UPDATE", playerKey);
        Map<String, Object> account = queryOne(connection,
                "SELECT pin_hash,must_change FROM bank_account_pins WHERE account_id=? FOR UPDATE",
                bankAccountId(playerKey));
        Map<String, Object> selected = personal != null && !first(string(personal.get("pin_hash")), "").isBlank()
                ? personal : account;
        if (selected == null || first(string(selected.get("pin_hash")), "").isBlank()) {
            return PinVerification.failed("PIN_REQUIRED", "Personal bank PIN is not configured.");
        }
        if (longValue(selected.get("must_change")) > 0L) {
            return PinVerification.failed("PIN_CHANGE_REQUIRED", "The temporary personal bank PIN must be changed.");
        }
        boolean valid;
        try {
            valid = verifyPinHash(string(selected.get("pin_hash")), pin.trim());
        } catch (Exception malformedHash) {
            getLogger().warning("Personal PIN hash rejected: " + safeError(malformedHash));
            valid = false;
        }
        if (valid) {
            synchronizePersonalPinHash(connection, playerKey, string(selected.get("pin_hash")));
            update(connection, "DELETE FROM account_lockouts WHERE account_id=?", lockoutKey);
            return PinVerification.ok();
        }
        update(connection,
                "INSERT INTO failed_pin_attempts(minecraft_uuid,site_account_id,attempted_at,source) VALUES(?,?,?,?)",
                playerKey, "", nowSeconds, first(source, "economy-personal-pin"));
        long attempts = scalarLong(connection,
                "SELECT COUNT(*) FROM failed_pin_attempts WHERE minecraft_uuid=? AND attempted_at>=?",
                playerKey, nowSeconds - PIN_ATTEMPT_WINDOW_SECONDS);
        if (attempts >= PIN_MAX_ATTEMPTS) {
            long lockedUntil = nowSeconds + PIN_LOCK_SECONDS;
            update(connection,
                    "INSERT INTO account_lockouts(account_id,locked_until,reason,updated_at) VALUES(?,?,?,?) "
                            + "ON CONFLICT(account_id) DO UPDATE SET locked_until=GREATEST(account_lockouts.locked_until,excluded.locked_until),reason=excluded.reason,updated_at=excluded.updated_at",
                    lockoutKey, lockedUntil, "bank-pin", nowSeconds);
        }
        return PinVerification.failed("PIN_INVALID", "Personal bank PIN is invalid.");
    }

    private long now() {
        return System.currentTimeMillis();
    }

    private long nowSec() {
        return Math.max(1L, now() / 1000L);
    }

    private boolean pinMatchesPolicy(String pin) {
        return pin != null && pin.matches("\\d{4,8}");
    }

    private String bankAccountId(String uuid) {
        return "ar:" + first(uuid, "");
    }

    private String treasuryAccountId() {
        return TREASURY_ACCOUNT_ID;
    }

    private Map<String, Object> activePresidentTerm(Connection connection) throws Exception {
        return queryOne(connection, "SELECT president_uuid,president_name FROM president_terms WHERE status='ACTIVE' ORDER BY started_at DESC LIMIT 1");
    }

    private Map<String, Object> ensureTreasuryAccount(Connection connection) throws Exception {
        long t = now();
        String ownerUuid = "";
        String ownerName = TREASURY_ACCOUNT_LABEL;
        update(connection, "INSERT INTO cmv4_bank_accounts(account_id,owner_uuid,owner_name,account_type,currency,balance,status,version,created_at,updated_at) VALUES(?,?,?,?,'AR',0,'ACTIVE',0,?,?) ON CONFLICT(account_id) DO UPDATE SET owner_uuid=excluded.owner_uuid,owner_name=excluded.owner_name,updated_at=excluded.updated_at",
                treasuryAccountId(), ownerUuid, ownerName, TREASURY_ACCOUNT_TYPE, t, t);
        return queryOne(connection, "SELECT * FROM cmv4_bank_accounts WHERE account_id=? LIMIT 1", treasuryAccountId());
    }

    private boolean hasTreasuryAccess(Player player) {
        if (player == null) {
            return false;
        }
        if (hasEconomyAdmin(player)) {
            return true;
        }
        // The menu/event path is always Bukkit's main thread.  Do not perform
        // a blocking president_terms lookup here; the bounded async refresh
        // below makes the role decision available without freezing clicks.
        return treasuryAccessCache.getOrDefault(player.getUniqueId(), false);
    }

    /** Preserve an explicitly requested treasury scope; never silently fall
     * back to a personal account after the president/admin role is revoked. */
    private String requestedAccountScope(Player player, String requestedScope) {
        if (!"TREASURY".equalsIgnoreCase(first(requestedScope, "PERSONAL"))) {
            return "PERSONAL";
        }
        if (!hasTreasuryAccess(player)) {
            if (player != null && player.isOnline()) {
                player.sendMessage(color("&cДоступ к казне отозван. Операция отменена."));
            }
            return "";
        }
        return "TREASURY";
    }

    private boolean treasuryMutationAllowed(Connection connection, Player actor) throws Exception {
        if (actor == null) {
            return false;
        }
        if (hasEconomyAdmin(actor)) {
            return true;
        }
        return scalarLong(connection,
                "SELECT COUNT(*) FROM president_terms WHERE president_uuid=? AND status='ACTIVE'",
                actor.getUniqueId().toString()) > 0L;
    }

    private void refreshTreasuryAccessAsync() {
        if (!databaseReady.get() || dbExecutor == null || dbExecutor.isShutdown()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshTreasuryAccessAsync(player.getUniqueId());
        }
    }

    private void refreshTreasuryAccessAsync(UUID playerUuid) {
        if (playerUuid == null || !databaseReady.get()) {
            return;
        }
        dbFuture("treasury role refresh", () -> scalarLong(
                "SELECT COUNT(*) FROM president_terms WHERE president_uuid=? AND status='ACTIVE'",
                playerUuid.toString()) > 0L).whenComplete((allowed, error) -> {
            if (error != null) {
                return;
            }
            treasuryAccessCache.put(playerUuid, Boolean.TRUE.equals(allowed));
        });
    }

    private TxnResult creditAccount(String accountId, String ownerUuid, String ownerName, long amount, String idempotencyKey, String action, String details) {
        if (accountId == null || accountId.isBlank() || amount <= 0L) {
            return new TxnResult(false, "INVALID_REQUEST", "Некорректные данные пополнения.", 0L, "");
        }
        try {
            return tx(connection -> {
                Map<String, Object> account = treasuryAccountId().equals(accountId)
                        ? ensureTreasuryAccount(connection)
                        : queryOne(connection, "SELECT * FROM cmv4_bank_accounts WHERE account_id=? LIMIT 1", accountId);
                if (account == null || account.isEmpty()) {
                    throw new IllegalStateException("Target account is missing.");
                }
                String targetId = string(account.get("account_id"));
                String txKey = first(idempotencyKey, "credit-" + UUID.randomUUID());
                TxnResult replay = new BankServiceImpl().replayCreditIfCommitted(connection, txKey, targetId);
                if (replay != null) {
                    return replay;
                }
                long before = scalarLong(connection, "SELECT COALESCE(balance,0) FROM cmv4_bank_accounts WHERE account_id=? FOR UPDATE", targetId);
                long after = before + amount;
                long t = now();
                String txId = txKey;
                update(connection, "UPDATE cmv4_bank_accounts SET balance=?,version=version+1,updated_at=? WHERE account_id=?", after, t, targetId);
                update(connection, "INSERT INTO cmv4_bank_ledger(tx_id,account_id,counterparty_account_id,player_uuid,tx_type,amount,balance_after,idempotency_key,status,created_at,actor,details) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                        txId, targetId, first(action, "CREDIT"), first(ownerUuid, ""), first(action, "CREDIT"), amount, after, txId, "COMMITTED", t, first(ownerName, ""), first(details, ""));
                return new TxnResult(true, "OK", "Пополнение выполнено.", after, txId);
            });
        } catch (Exception error) {
            return new TxnResult(false, "BANK_ERROR", safeError(error), 0L, "");
        }
    }

    private CompletableFuture<TxnResult> creditAccountAsync(String accountId, String ownerUuid, String ownerName, long amount, String idempotencyKey, String action, String details) {
        return dbFuture("bank credit account", () -> creditAccount(accountId, ownerUuid, ownerName, amount, idempotencyKey, action, details));
    }

    private Map<String, Object> resolveManagedAccount(Connection connection, String accountId, String ownerUuid, String ownerName) throws Exception {
        String normalizedId = first(accountId, "").trim();
        if (normalizedId.isBlank()) {
            throw new IllegalArgumentException("Account id is required.");
        }
        if (treasuryAccountId().equalsIgnoreCase(normalizedId)) {
            return ensureTreasuryAccount(connection);
        }
        Map<String, Object> existing = queryOne(connection, "SELECT * FROM cmv4_bank_accounts WHERE account_id=? LIMIT 1", normalizedId);
        if (existing != null && !existing.isEmpty()) {
            return existing;
        }
        if (!first(ownerUuid, "").isBlank()) {
            ensureBankAccount(connection, ownerUuid, first(ownerName, ""));
            existing = queryOne(connection, "SELECT * FROM cmv4_bank_accounts WHERE account_id=? LIMIT 1", normalizedId);
            if (existing != null && !existing.isEmpty()) {
                return existing;
            }
        }
        throw new IllegalStateException("Managed account was not found: " + normalizedId);
    }

    private TxnResult replayTransferIfCommitted(Connection connection, String txKey, String fromAccountId, String toAccountId, long amount) throws Exception {
        if (txKey == null || txKey.isBlank()) {
            return null;
        }
        Map<String, Object> existing = queryOne(connection,
                "SELECT tx_id,from_account_id,to_account_id,amount FROM cmv4_bank_transfers WHERE idempotency_key=? LIMIT 1",
                txKey);
        if (existing == null || existing.isEmpty()) {
            return null;
        }
        if (!fromAccountId.equalsIgnoreCase(string(existing.get("from_account_id")))
                || !toAccountId.equalsIgnoreCase(string(existing.get("to_account_id")))
                || longValue(existing.get("amount")) != amount) {
            return new TxnResult(false, "IDEMPOTENCY_CONFLICT", "Transfer idempotency key is already bound to another operation.", 0L, "");
        }
        long replayBalance = scalarLong(connection,
                "SELECT COALESCE(balance_after,0) FROM cmv4_bank_ledger WHERE tx_id=? LIMIT 1",
                string(existing.get("tx_id")) + ":out");
        return new TxnResult(true, "OK", "Idempotent replay.", replayBalance, string(existing.get("tx_id")));
    }

    private TxnResult transferPlayerToAccount(UUID playerUuid, String playerName, String pin, String toAccountId, String toOwnerUuid, String toOwnerName, long amount, String idempotencyKey, String action, String details) {
        requireAsyncBankContext("ArtifactsBridge.transferToAccount");
        if (playerUuid == null || amount <= 0 || first(toAccountId, "").isBlank()) {
            return new TxnResult(false, "INVALID_REQUEST", "Transfer request is invalid.", 0L, "");
        }
        String playerKey = playerUuid.toString();
        String providedPin = first(pin, "");
        try {
            if (providedPin.isBlank()) {
                return new TxnResult(false, "PIN_REQUIRED", "Bank PIN is required for this transfer.", 0L, "");
            }
            return tx(connection -> {
                Map<String, Object> fromAccount = ensureBankAccount(connection, playerKey, first(playerName, ""));
                Map<String, Object> toAccount = resolveManagedAccount(connection, toAccountId, toOwnerUuid, toOwnerName);
                String fromId = string(fromAccount.get("account_id"));
                String resolvedToId = string(toAccount.get("account_id"));
                PinVerification pinVerification = verifyPersonalPinForMutation(
                        connection, playerKey, providedPin, "economy-artifact-transfer");
                if (!pinVerification.valid()) {
                    return new TxnResult(false, pinVerification.code(), pinVerification.message(), 0L, "");
                }
                String txKey = first(idempotencyKey, "managed-transfer-" + UUID.randomUUID());
                TxnResult replay = replayTransferIfCommitted(connection, txKey, fromId, resolvedToId, amount);
                if (replay != null) {
                    return replay;
                }
                long fromBefore;
                long toBefore;
                if (fromId.compareTo(resolvedToId) <= 0) {
                    fromBefore = scalarLong(connection, "SELECT COALESCE(balance,0) FROM cmv4_bank_accounts WHERE account_id=? FOR UPDATE", fromId);
                    toBefore = scalarLong(connection, "SELECT COALESCE(balance,0) FROM cmv4_bank_accounts WHERE account_id=? FOR UPDATE", resolvedToId);
                } else {
                    toBefore = scalarLong(connection, "SELECT COALESCE(balance,0) FROM cmv4_bank_accounts WHERE account_id=? FOR UPDATE", resolvedToId);
                    fromBefore = scalarLong(connection, "SELECT COALESCE(balance,0) FROM cmv4_bank_accounts WHERE account_id=? FOR UPDATE", fromId);
                }
                if (fromBefore < amount) {
                    return new TxnResult(false, "INSUFFICIENT_AR", "Not enough AR in bank.", fromBefore, "");
                }
                long when = now();
                long fromAfter = fromBefore - amount;
                long toAfter = toBefore + amount;
                String txId = txKey;
                update(connection, "UPDATE cmv4_bank_accounts SET balance=?,version=version+1,updated_at=? WHERE account_id=?", fromAfter, when, fromId);
                update(connection, "UPDATE cmv4_bank_accounts SET balance=?,version=version+1,updated_at=? WHERE account_id=?", toAfter, when, resolvedToId);
                update(connection, "INSERT INTO cmv4_bank_transfers(tx_id,from_account_id,to_account_id,amount,currency,status,idempotency_key,created_at,actor,details) VALUES(?,?,?,?,'AR','COMMITTED',?,?,?,?)",
                        txId, fromId, resolvedToId, amount, txKey, when, first(playerName, ""), first(details, ""));
                update(connection, "INSERT INTO cmv4_bank_ledger(tx_id,account_id,counterparty_account_id,player_uuid,tx_type,amount,balance_after,idempotency_key,status,created_at,actor,details) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                        txId + ":out", fromId, resolvedToId, playerKey, first(action, "TRANSFER_OUT"), amount, fromAfter, txId + ":out", "COMMITTED", when, first(playerName, ""), first(details, ""));
                update(connection, "INSERT INTO cmv4_bank_ledger(tx_id,account_id,counterparty_account_id,player_uuid,tx_type,amount,balance_after,idempotency_key,status,created_at,actor,details) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                        txId + ":in", resolvedToId, fromId, first(string(toAccount.get("owner_uuid")), first(toOwnerUuid, "")), first(action, "TRANSFER_IN"), amount, toAfter, txId + ":in", "COMMITTED", when, first(playerName, ""), first(details, ""));
                return new TxnResult(true, "OK", "Transfer completed.", fromAfter, txId);
            });
        } catch (Exception error) {
            try {
                String txKey = first(idempotencyKey, "");
                if (!txKey.isBlank()) {
                    TxnResult replay = tx(connection -> replayTransferIfCommitted(connection, txKey, bankAccountId(playerKey), first(toAccountId, "").trim(), amount));
                    if (replay != null && replay.ok) {
                        return replay;
                    }
                }
            } catch (Exception replayError) {
                getLogger().warning("artifacts transfer replay account=" + playerKey + " to=" + first(toAccountId, "").trim() + " key=" + first(idempotencyKey, "") + " failed: " + safeError(replayError));
            }
            return new TxnResult(false, "BANK_ERROR", safeError(error), 0L, "");
        }
    }

    private TxnResult transferFromAccount(String fromAccountId, String fromOwnerUuid, String fromOwnerName, UUID toUuid, String toName, long amount, String idempotencyKey, String action, String details) {
        requireAsyncBankContext("ArtifactsBridge.transferFromAccount");
        if (toUuid == null || amount <= 0 || first(fromAccountId, "").isBlank()) {
            return new TxnResult(false, "INVALID_REQUEST", "Transfer request is invalid.", 0L, "");
        }
        try {
            return tx(connection -> {
                Map<String, Object> fromAccount = resolveManagedAccount(connection, fromAccountId, fromOwnerUuid, fromOwnerName);
                Map<String, Object> toAccount = ensureBankAccount(connection, toUuid.toString(), first(toName, ""));
                String resolvedFromId = string(fromAccount.get("account_id"));
                String toId = string(toAccount.get("account_id"));
                String txKey = first(idempotencyKey, "managed-transfer-" + UUID.randomUUID());
                TxnResult replay = replayTransferIfCommitted(connection, txKey, resolvedFromId, toId, amount);
                if (replay != null) {
                    return replay;
                }
                long fromBefore;
                long toBefore;
                if (resolvedFromId.compareTo(toId) <= 0) {
                    fromBefore = scalarLong(connection, "SELECT COALESCE(balance,0) FROM cmv4_bank_accounts WHERE account_id=? FOR UPDATE", resolvedFromId);
                    toBefore = scalarLong(connection, "SELECT COALESCE(balance,0) FROM cmv4_bank_accounts WHERE account_id=? FOR UPDATE", toId);
                } else {
                    toBefore = scalarLong(connection, "SELECT COALESCE(balance,0) FROM cmv4_bank_accounts WHERE account_id=? FOR UPDATE", toId);
                    fromBefore = scalarLong(connection, "SELECT COALESCE(balance,0) FROM cmv4_bank_accounts WHERE account_id=? FOR UPDATE", resolvedFromId);
                }
                if (fromBefore < amount) {
                    return new TxnResult(false, "INSUFFICIENT_AR", "Not enough AR in source account.", fromBefore, "");
                }
                long when = now();
                long fromAfter = fromBefore - amount;
                long toAfter = toBefore + amount;
                String txId = txKey;
                update(connection, "UPDATE cmv4_bank_accounts SET balance=?,version=version+1,updated_at=? WHERE account_id=?", fromAfter, when, resolvedFromId);
                update(connection, "UPDATE cmv4_bank_accounts SET balance=?,version=version+1,updated_at=? WHERE account_id=?", toAfter, when, toId);
                update(connection, "INSERT INTO cmv4_bank_transfers(tx_id,from_account_id,to_account_id,amount,currency,status,idempotency_key,created_at,actor,details) VALUES(?,?,?,?,'AR','COMMITTED',?,?,?,?)",
                        txId, resolvedFromId, toId, amount, txKey, when, first(fromOwnerName, ""), first(details, ""));
                update(connection, "INSERT INTO cmv4_bank_ledger(tx_id,account_id,counterparty_account_id,player_uuid,tx_type,amount,balance_after,idempotency_key,status,created_at,actor,details) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                        txId + ":out", resolvedFromId, toId, first(string(fromAccount.get("owner_uuid")), first(fromOwnerUuid, "")), first(action, "TRANSFER_OUT"), amount, fromAfter, txId + ":out", "COMMITTED", when, first(fromOwnerName, ""), first(details, ""));
                update(connection, "INSERT INTO cmv4_bank_ledger(tx_id,account_id,counterparty_account_id,player_uuid,tx_type,amount,balance_after,idempotency_key,status,created_at,actor,details) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                        txId + ":in", toId, resolvedFromId, toUuid.toString(), first(action, "TRANSFER_IN"), amount, toAfter, txId + ":in", "COMMITTED", when, first(fromOwnerName, ""), first(details, ""));
                return new TxnResult(true, "OK", "Transfer completed.", toAfter, txId);
            });
        } catch (Exception error) {
            try {
                String txKey = first(idempotencyKey, "");
                if (!txKey.isBlank()) {
                    TxnResult replay = tx(connection -> replayTransferIfCommitted(connection, txKey, first(fromAccountId, "").trim(), bankAccountId(toUuid.toString()), amount));
                    if (replay != null && replay.ok) {
                        return replay;
                    }
                }
            } catch (Exception replayError) {
                getLogger().warning("managed transfer replay from=" + first(fromAccountId, "").trim() + " to=" + (toUuid == null ? "" : toUuid) + " key=" + first(idempotencyKey, "") + " failed: " + safeError(replayError));
            }
            return new TxnResult(false, "BANK_ERROR", safeError(error), 0L, "");
        }
    }

    private TxnResult stealFromPlayerAccount(UUID fromUuid, String fromName, UUID toUuid, String toName, long amount, String idempotencyKey, String action, String details) {
        requireAsyncBankContext("ArtifactsBridge.stealFromPlayerAccount");
        if (fromUuid == null || toUuid == null || fromUuid.equals(toUuid) || amount <= 0L) {
            return new TxnResult(false, "INVALID_REQUEST", "Invalid AR theft request.", 0L, "");
        }
        String fromId = bankAccountId(fromUuid.toString());
        String toId = bankAccountId(toUuid.toString());
        try {
            return tx(connection -> {
                Map<String, Object> fromAccount = queryOne(connection,
                    "SELECT * FROM cmv4_bank_accounts WHERE account_id=? AND status='ACTIVE' LIMIT 1", fromId);
                if (fromAccount == null || fromAccount.isEmpty()) {
                    return new TxnResult(false, "NO_BANK_ACCOUNT", "Victim has no bank account.", 0L, "");
                }
                Map<String, Object> toAccount = ensureBankAccount(connection, toUuid.toString(), first(toName, ""));
                String txKey = first(idempotencyKey, "kosa-ar-steal-" + UUID.randomUUID());
                TxnResult replay = replayTransferIfCommitted(connection, txKey, fromId, toId, amount);
                if (replay != null) {
                    return replay;
                }
                long fromBefore;
                long toBefore;
                if (fromId.compareTo(toId) <= 0) {
                    fromBefore = scalarLong(connection, "SELECT COALESCE(balance,0) FROM cmv4_bank_accounts WHERE account_id=? FOR UPDATE", fromId);
                    toBefore = scalarLong(connection, "SELECT COALESCE(balance,0) FROM cmv4_bank_accounts WHERE account_id=? FOR UPDATE", toId);
                } else {
                    toBefore = scalarLong(connection, "SELECT COALESCE(balance,0) FROM cmv4_bank_accounts WHERE account_id=? FOR UPDATE", toId);
                    fromBefore = scalarLong(connection, "SELECT COALESCE(balance,0) FROM cmv4_bank_accounts WHERE account_id=? FOR UPDATE", fromId);
                }
                if (fromBefore < amount) {
                    return new TxnResult(false, "INSUFFICIENT_AR", "Victim has no AR to take.", fromBefore, "");
                }
                long when = now();
                long fromAfter = fromBefore - amount;
                long toAfter = toBefore + amount;
                update(connection, "UPDATE cmv4_bank_accounts SET balance=?,version=version+1,updated_at=? WHERE account_id=?", fromAfter, when, fromId);
                update(connection, "UPDATE cmv4_bank_accounts SET balance=?,version=version+1,updated_at=? WHERE account_id=?", toAfter, when, toId);
                update(connection, "INSERT INTO cmv4_bank_transfers(tx_id,from_account_id,to_account_id,amount,currency,status,idempotency_key,created_at,actor,details) VALUES(?,?,?,?,'AR','COMMITTED',?,?,?,?)",
                    txKey, fromId, toId, amount, txKey, when, "CopiMine Kosa", first(details, ""));
                update(connection, "INSERT INTO cmv4_bank_ledger(tx_id,account_id,counterparty_account_id,player_uuid,tx_type,amount,balance_after,idempotency_key,status,created_at,actor,details) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                    txKey + ":out", fromId, toId, fromUuid.toString(), first(action, "AR_STEAL_OUT"), amount, fromAfter, txKey + ":out", "COMMITTED", when, first(fromName, ""), first(details, ""));
                update(connection, "INSERT INTO cmv4_bank_ledger(tx_id,account_id,counterparty_account_id,player_uuid,tx_type,amount,balance_after,idempotency_key,status,created_at,actor,details) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                    txKey + ":in", toId, fromId, toUuid.toString(), first(action, "AR_STEAL_IN"), amount, toAfter, txKey + ":in", "COMMITTED", when, "CopiMine Kosa", first(details, ""));
                return new TxnResult(true, "OK", "AR stolen.", toAfter, txKey);
            });
        } catch (Exception error) {
            return new TxnResult(false, "BANK_ERROR", safeError(error), 0L, "");
        }
    }

    private CompletableFuture<TxnResult> chargeTreasuryWithPinAsync(Player actor, long amount, String pin, String idempotencyKey, String action, String details) {
        return dbFuture("treasury charge", () -> {
            if (actor == null || amount <= 0L) {
                return new TxnResult(false, "INVALID_REQUEST", "Некорректный запрос.", 0L, "");
            }
            if (pin == null || pin.isBlank()) {
                // The bridge is public to other plugins; never rely on the
                // ATM GUI to enforce the PIN format. Fail closed before any
                // treasury read or debit is attempted.
                return new TxnResult(false, "PIN_REQUIRED", "PIN is required.", 0L, "");
            }
            String accountId = treasuryAccountId();
            return tx(connection -> {
                if (!treasuryMutationAllowed(connection, actor)) {
                    return new TxnResult(false, "ACCESS_REVOKED", "Доступ к казне отозван.", 0L, "");
                }
                PinVerification pinVerification = verifyAccountPinForMutation(
                        connection, accountId, pin, "economy-treasury-withdraw");
                if (!pinVerification.valid()) {
                    return new TxnResult(false, pinVerification.code(), pinVerification.message(), 0L, "");
                }
                ensureTreasuryAccount(connection);
                String txKey = first(idempotencyKey, "treasury-charge-" + UUID.randomUUID());
                // The ledger row records the president/admin actor UUID.  Use
                // the same identity for replay checks; passing an empty UUID
                // turns a post-commit connection error into a false
                // idempotency conflict and can queue a duplicate withdrawal.
                TxnResult replay = replayArtifactStyleTxn(connection, txKey, accountId, actor.getUniqueId().toString(), -amount, first(details, ""));
                if (replay != null) {
                    return replay;
                }
                long before = scalarLong(connection, "SELECT COALESCE(balance,0) FROM cmv4_bank_accounts WHERE account_id=? FOR UPDATE", accountId);
                long after = before - amount;
                if (after < 0L) {
                    return new TxnResult(false, "INSUFFICIENT_AR", "Недостаточно AR в казне.", before, "");
                }
                long t = now();
                String txId = "bank-" + first(action, "TREASURY_DEBIT") + "-" + UUID.randomUUID();
                update(connection, "UPDATE cmv4_bank_accounts SET balance=?,version=version+1,updated_at=? WHERE account_id=?", after, t, accountId);
                update(connection, "INSERT INTO cmv4_bank_ledger(tx_id,account_id,counterparty_account_id,player_uuid,tx_type,amount,balance_after,idempotency_key,status,created_at,actor,details) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                        txId, accountId, first(action, "TREASURY_DEBIT"), actor.getUniqueId().toString(), "DEBIT", amount, after, txKey, "COMMITTED", t, actor.getName(), first(details, ""));
                createWithdrawalDeliveryRow(connection, actor.getUniqueId(), actor.getName(), amount, txId, action, details);
                return new TxnResult(true, "OK", "Операция по казне подтверждена.", after, txId);
            });
        });
    }

    private CompletableFuture<TxnResult> transferFromTreasuryWithPinAsync(Player actor, UUID targetUuid, String targetName, long amount, String pin, String idempotencyKey, String action, String details) {
        return dbFuture("treasury transfer", () -> {
            if (actor == null || targetUuid == null || amount <= 0L) {
                return new TxnResult(false, "INVALID_REQUEST", "Некорректный перевод.", 0L, "");
            }
            if (pin == null || pin.isBlank()) {
                // Keep treasury transfers fail-closed as well; this method is
                // reachable through the service bridge without the PIN pad.
                return new TxnResult(false, "PIN_REQUIRED", "PIN is required.", 0L, "");
            }
            String accountId = treasuryAccountId();
            return tx(connection -> {
                if (!treasuryMutationAllowed(connection, actor)) {
                    return new TxnResult(false, "ACCESS_REVOKED", "Доступ к казне отозван.", 0L, "");
                }
                PinVerification pinVerification = verifyAccountPinForMutation(
                        connection, accountId, pin, "economy-treasury-transfer");
                if (!pinVerification.valid()) {
                    return new TxnResult(false, pinVerification.code(), pinVerification.message(), 0L, "");
                }
                Map<String, Object> fromAccount = ensureTreasuryAccount(connection);
                Map<String, Object> toAccount = ensureBankAccount(connection, targetUuid.toString(), first(targetName, ""));
                String fromId = string(fromAccount.get("account_id"));
                String toId = string(toAccount.get("account_id"));
                String txKey = first(idempotencyKey, "treasury-transfer-" + UUID.randomUUID());
                TxnResult replay = new BankServiceImpl().replayTransferIfCommitted(connection, txKey, fromId, toId, amount);
                if (replay != null) {
                    return replay;
                }
                long before = scalarLong(connection, "SELECT COALESCE(balance,0) FROM cmv4_bank_accounts WHERE account_id=? FOR UPDATE", fromId);
                long targetBefore = scalarLong(connection, "SELECT COALESCE(balance,0) FROM cmv4_bank_accounts WHERE account_id=? FOR UPDATE", toId);
                if (before < amount) {
                    return new TxnResult(false, "INSUFFICIENT_AR", "Недостаточно AR в казне.", before, "");
                }
                long t = now();
                long after = before - amount;
                long targetAfter = targetBefore + amount;
                String txId = txKey;
                update(connection, "UPDATE cmv4_bank_accounts SET balance=?,version=version+1,updated_at=? WHERE account_id=?", after, t, fromId);
                update(connection, "UPDATE cmv4_bank_accounts SET balance=?,version=version+1,updated_at=? WHERE account_id=?", targetAfter, t, toId);
                update(connection, "INSERT INTO cmv4_bank_transfers(tx_id,from_account_id,to_account_id,amount,currency,status,idempotency_key,created_at,actor,details) VALUES(?,?,?,?,'AR','COMMITTED',?,?,?,?)",
                        txId, fromId, toId, amount, txId, t, actor.getName(), first(details, ""));
                update(connection, "INSERT INTO cmv4_bank_ledger(tx_id,account_id,counterparty_account_id,player_uuid,tx_type,amount,balance_after,idempotency_key,status,created_at,actor,details) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                        txId + ":out", fromId, toId, actor.getUniqueId().toString(), first(action, "TREASURY_TRANSFER_OUT"), amount, after, txId + ":out", "COMMITTED", t, actor.getName(), first(details, ""));
                update(connection, "INSERT INTO cmv4_bank_ledger(tx_id,account_id,counterparty_account_id,player_uuid,tx_type,amount,balance_after,idempotency_key,status,created_at,actor,details) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                        txId + ":in", toId, fromId, targetUuid.toString(), "TRANSFER_IN", amount, targetAfter, txId + ":in", "COMMITTED", t, actor.getName(), first(details, ""));
                return new TxnResult(true, "OK", "Перевод из казны выполнен.", after, txId);
            });
        });
    }

    private void ensureBankAccount(String uuid, String name) throws Exception {
        tx(connection -> {
            ensureBankAccount(connection, uuid, name);
            return null;
        });
    }

    private Map<String, Object> ensureBankAccount(Connection connection, String uuid, String name) throws Exception {
        long t = now();
        String accountId = bankAccountId(uuid);
        update(connection, "INSERT INTO cmv4_bank_accounts(account_id,owner_uuid,owner_name,account_type,currency,balance,status,version,created_at,updated_at) VALUES(?,?,?,'PLAYER','AR',0,'ACTIVE',0,?,?) ON CONFLICT(account_id) DO UPDATE SET owner_name=excluded.owner_name,updated_at=excluded.updated_at",
                accountId, uuid, first(name, ""), t, t);
        Map<String, Object> row = queryOne(connection, "SELECT * FROM cmv4_bank_accounts WHERE account_id=? LIMIT 1", accountId);
        long currentBalance = longValue(row.get("balance"));
        if (currentBalance <= 0L) {
            long ledgerCount = scalarLong(connection, "SELECT COUNT(*) FROM cmv4_bank_ledger WHERE account_id=?", accountId);
            if (ledgerCount == 0L) {
                try {
                    Map<String, Object> legacy = queryOne(connection,
                            "SELECT COALESCE(balance,0) AS balance, COALESCE(updated_at,0) AS updated_at, COALESCE(name,'') AS name FROM cmv7_ar_balances WHERE uuid=? LIMIT 1",
                            uuid);
                    long legacyBalance = longValue(legacy.get("balance"));
                    if (legacyBalance > 0L) {
                        long updatedAt = Math.max(now(), longValue(legacy.get("updated_at")));
                        update(connection,
                                "UPDATE cmv4_bank_accounts SET balance=?,version=version+1,updated_at=?,owner_name=? WHERE account_id=?",
                                legacyBalance, updatedAt, first(name, string(legacy.get("name"))), accountId);
                        row = queryOne(connection, "SELECT * FROM cmv4_bank_accounts WHERE account_id=? LIMIT 1", accountId);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return row;
    }

    private Map<String, Object> ensureDonationAccount(Connection connection, String uuid, String name) throws Exception {
        long t = now();
        update(connection, "INSERT INTO donation_accounts(player_uuid,player_name,balance,created_at,updated_at) VALUES(?,?,0,?,?) ON CONFLICT(player_uuid) DO UPDATE SET player_name=excluded.player_name,updated_at=excluded.updated_at",
                uuid, first(name, ""), t, t);
        return queryOne(connection, "SELECT * FROM donation_accounts WHERE player_uuid=? LIMIT 1", uuid);
    }

    private TxnResult mutateDonationBalanceInConnection(Connection connection, UUID playerUuid, String playerName, long delta, String reason, String actor, String source, String idempotencyKey) throws Exception {
        if (playerUuid == null || delta == 0L) {
            return new TxnResult(false, "INVALID_REQUEST", "Некорректный donation-запрос.", 0L, "");
        }
        String uuid = playerUuid.toString();
        ensureDonationAccount(connection, uuid, first(playerName, ""));
        String key = first(idempotencyKey, "").trim();
        if (!key.isBlank()) {
            TxnResult replay = replayDonationBalanceMutation(connection, key, uuid, delta, reason, actor, source);
            if (replay != null) {
                return replay;
            }
        }
        long before = scalarLong(connection, "SELECT COALESCE(balance,0) FROM donation_accounts WHERE player_uuid=? FOR UPDATE", uuid);
        long after = before + delta;
        if (after < 0L) {
            return new TxnResult(false, "INSUFFICIENT_DONATION_BALANCE", "Недостаточно донат-баланса.", before, "");
        }
        long t = now();
        String txId = "don-" + UUID.randomUUID();
        update(connection, "UPDATE donation_accounts SET balance=?,updated_at=?,player_name=? WHERE player_uuid=?", after, t, first(playerName, ""), uuid);
        update(connection, "INSERT INTO donation_balance_ledger(id,player_uuid,delta,balance_after,reason,actor,source,idempotency_key,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                txId, uuid, delta, after, first(reason, ""), first(actor, ""), first(source, ""), key, t);
        return new TxnResult(true, "OK", delta >= 0 ? "Баланс пополнен." : "Баланс списан.", after, txId);
    }

    private TxnResult replayDonationBalanceMutation(Connection connection, String idempotencyKey, String playerUuid, long delta, String reason, String actor, String source) throws Exception {
        if (first(idempotencyKey, "").isBlank()) {
            return null;
        }
        Map<String, Object> existing = queryOne(connection,
                "SELECT id,player_uuid,delta,balance_after,reason,actor,source FROM donation_balance_ledger WHERE idempotency_key=? LIMIT 1",
                idempotencyKey);
        if (existing == null || existing.isEmpty()) {
            return null;
        }
        if (!string(existing.get("player_uuid")).equalsIgnoreCase(first(playerUuid, ""))
                || longValue(existing.get("delta")) != delta
                || !string(existing.get("reason")).equals(first(reason, ""))
                || !string(existing.get("actor")).equals(first(actor, ""))
                || !string(existing.get("source")).equals(first(source, ""))) {
            throw new IllegalStateException("Donation idempotency key conflicts with another operation.");
        }
        return new TxnResult(true, "OK", "Idempotent replay.", longValue(existing.get("balance_after")), string(existing.get("id")));
    }

    private TxnResult replayArtifactStyleTxn(Connection connection, String idempotencyKey, String accountId, String playerUuid, long signedAmount, String details) throws Exception {
        if (first(idempotencyKey, "").isBlank()) {
            return null;
        }
        Map<String, Object> existing = queryOne(connection,
                "SELECT tx_id,account_id,player_uuid,tx_type,amount,balance_after,details FROM cmv4_bank_ledger WHERE idempotency_key=? LIMIT 1",
                idempotencyKey);
        if (existing == null || existing.isEmpty()) {
            return null;
        }
        String expectedType = signedAmount < 0 ? "DEBIT" : "CREDIT";
        if (!string(existing.get("account_id")).equals(accountId)
                || !string(existing.get("player_uuid")).equalsIgnoreCase(first(playerUuid, ""))
                || !string(existing.get("tx_type")).equalsIgnoreCase(expectedType)
                || longValue(existing.get("amount")) != Math.abs(signedAmount)
                || !string(existing.get("details")).equals(first(details, ""))) {
            throw new IllegalStateException("Bank idempotency key conflicts with another operation.");
        }
        return new TxnResult(true, "OK", "Idempotent replay.", longValue(existing.get("balance_after")), string(existing.get("tx_id")));
    }

    private String bankTargetName(String targetUuid) {
        String fallback = first(targetUuid, "Игрок");
        if (targetUuid == null || targetUuid.isBlank()) {
            return fallback;
        }
        try {
            Player player = Bukkit.getPlayer(UUID.fromString(targetUuid));
            return player != null ? player.getName() : fallback;
        } catch (IllegalArgumentException ignored) {
            // A stale or malformed database row must not crash the inventory
            // click handler. Keep the UUID/text as a safe display fallback.
            return fallback;
        }
    }

    private boolean hasEconomyAdmin(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        return player.isOp() || player.hasPermission("copimine.admin") || player.hasPermission("copimine.economy.admin") || player.hasPermission("copimine.bank.admin");
    }

    private boolean requireEconomyAdmin(Player player) {
        if (player != null && hasEconomyAdmin(player)) {
            return true;
        }
        if (player != null) {
            player.sendMessage(color("&cДоступно только администрации экономики."));
        }
        return false;
    }

    private void dropLegacyPinSecretColumn(Connection connection, String table) throws Exception {
        DatabaseMetaData metadata = connection.getMetaData();
        boolean present = false;
        try (ResultSet columns = metadata.getColumns(null, null, table, "pin_sealed")) {
            present = columns.next();
        }
        if (present) {
            update(connection, "ALTER TABLE " + table + " DROP COLUMN pin_sealed");
        }
    }

    private void ensureSchema() throws Exception {
        int archivedDuplicateAtms = tx(connection -> {
            update(connection, "CREATE TABLE IF NOT EXISTS cmv4_bank_accounts(account_id TEXT PRIMARY KEY,owner_uuid TEXT NOT NULL,owner_name TEXT NOT NULL DEFAULT '',account_type TEXT NOT NULL DEFAULT 'PLAYER',currency TEXT NOT NULL DEFAULT 'AR',balance BIGINT NOT NULL DEFAULT 0 CHECK(balance>=0),status TEXT NOT NULL DEFAULT 'ACTIVE',version BIGINT NOT NULL DEFAULT 0,created_at BIGINT NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL DEFAULT 0)");
            update(connection, "CREATE TABLE IF NOT EXISTS cmv4_bank_ledger(tx_id TEXT PRIMARY KEY,account_id TEXT NOT NULL,counterparty_account_id TEXT NOT NULL DEFAULT '',player_uuid TEXT NOT NULL DEFAULT '',tx_type TEXT NOT NULL,amount BIGINT NOT NULL CHECK(amount>=0),balance_after BIGINT NOT NULL DEFAULT 0,idempotency_key TEXT NOT NULL DEFAULT '',status TEXT NOT NULL DEFAULT 'COMMITTED',created_at BIGINT NOT NULL,actor TEXT NOT NULL DEFAULT '',details TEXT NOT NULL DEFAULT '')");
            update(connection, "CREATE TABLE IF NOT EXISTS cmv4_bank_transfers(tx_id TEXT PRIMARY KEY,from_account_id TEXT NOT NULL,to_account_id TEXT NOT NULL,amount BIGINT NOT NULL CHECK(amount>0),currency TEXT NOT NULL DEFAULT 'AR',status TEXT NOT NULL DEFAULT 'COMMITTED',idempotency_key TEXT NOT NULL DEFAULT '',created_at BIGINT NOT NULL,actor TEXT NOT NULL DEFAULT '',details TEXT NOT NULL DEFAULT '')");
            update(connection, "CREATE TABLE IF NOT EXISTS bank_pin_hashes(minecraft_uuid TEXT PRIMARY KEY,site_account_id TEXT NOT NULL DEFAULT '',pin_hash TEXT NOT NULL,must_change INTEGER NOT NULL DEFAULT 0,created_at BIGINT NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL DEFAULT 0)");
            update(connection, "CREATE TABLE IF NOT EXISTS bank_account_pins(account_id TEXT PRIMARY KEY,pin_hash TEXT NOT NULL,must_change INTEGER NOT NULL DEFAULT 0,created_at BIGINT NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL DEFAULT 0,updated_by TEXT NOT NULL DEFAULT '')");
            dropLegacyPinSecretColumn(connection, "bank_pin_hashes");
            dropLegacyPinSecretColumn(connection, "bank_account_pins");
            update(connection, "CREATE TABLE IF NOT EXISTS ar_atms(id TEXT PRIMARY KEY,world TEXT NOT NULL,x INTEGER NOT NULL,y INTEGER NOT NULL,z INTEGER NOT NULL,expected_material TEXT NOT NULL DEFAULT '',expected_model_id TEXT NOT NULL DEFAULT 'atm_terminal',expected_custom_model_data INTEGER NOT NULL DEFAULT 12002,name TEXT NOT NULL DEFAULT 'Банкомат',active INTEGER NOT NULL DEFAULT 1,created_by TEXT NOT NULL DEFAULT '',created_at BIGINT NOT NULL DEFAULT 0,archived_by TEXT NOT NULL DEFAULT '',archived_at BIGINT NOT NULL DEFAULT 0)");
            update(connection, "ALTER TABLE ar_atms ADD COLUMN IF NOT EXISTS expected_material TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE ar_atms ADD COLUMN IF NOT EXISTS expected_model_id TEXT NOT NULL DEFAULT 'atm_terminal'");
            update(connection, "ALTER TABLE ar_atms ADD COLUMN IF NOT EXISTS expected_custom_model_data INTEGER NOT NULL DEFAULT 12002");
            update(connection, "CREATE TABLE IF NOT EXISTS account_lockouts(account_id TEXT PRIMARY KEY,locked_until BIGINT NOT NULL DEFAULT 0,reason TEXT NOT NULL DEFAULT '',updated_at BIGINT NOT NULL DEFAULT 0)");
            update(connection, "CREATE TABLE IF NOT EXISTS failed_pin_attempts(id BIGSERIAL PRIMARY KEY,minecraft_uuid TEXT NOT NULL,site_account_id TEXT NOT NULL DEFAULT '',attempted_at BIGINT NOT NULL DEFAULT 0,source TEXT NOT NULL DEFAULT '')");
            update(connection, "CREATE TABLE IF NOT EXISTS atm_events(id BIGSERIAL PRIMARY KEY,atm_id TEXT NOT NULL,player_uuid TEXT NOT NULL DEFAULT '',player_name TEXT NOT NULL DEFAULT '',event_type TEXT NOT NULL,amount BIGINT NOT NULL DEFAULT 0,balance_after BIGINT NOT NULL DEFAULT 0,created_at BIGINT NOT NULL DEFAULT 0,details TEXT NOT NULL DEFAULT '')");
            update(connection, "CREATE TABLE IF NOT EXISTS plugin_events(id BIGSERIAL PRIMARY KEY,source TEXT NOT NULL,event_type TEXT NOT NULL,actor TEXT NOT NULL DEFAULT '',target TEXT NOT NULL DEFAULT '',created_at BIGINT NOT NULL DEFAULT 0,details TEXT NOT NULL DEFAULT '')");
            update(connection, "CREATE TABLE IF NOT EXISTS cmv4_audit_events(id BIGSERIAL PRIMARY KEY,time BIGINT NOT NULL DEFAULT 0,actor TEXT NOT NULL DEFAULT '',action TEXT NOT NULL DEFAULT '',details TEXT NOT NULL DEFAULT '',admin_only INTEGER NOT NULL DEFAULT 0,source TEXT NOT NULL DEFAULT '')");
            update(connection, "CREATE TABLE IF NOT EXISTS donation_accounts(player_uuid TEXT PRIMARY KEY,player_name TEXT NOT NULL DEFAULT '',balance BIGINT NOT NULL DEFAULT 0 CHECK(balance>=0),created_at BIGINT NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL DEFAULT 0)");
            update(connection, "CREATE TABLE IF NOT EXISTS donation_balance_ledger(id TEXT PRIMARY KEY,player_uuid TEXT NOT NULL,delta BIGINT NOT NULL,balance_after BIGINT NOT NULL DEFAULT 0,reason TEXT NOT NULL DEFAULT '',actor TEXT NOT NULL DEFAULT '',source TEXT NOT NULL DEFAULT '',idempotency_key TEXT NOT NULL DEFAULT '',created_at BIGINT NOT NULL DEFAULT 0)");
            update(connection, "CREATE TABLE IF NOT EXISTS donation_payment_sessions(id TEXT PRIMARY KEY,player_uuid TEXT NOT NULL DEFAULT '',player_name TEXT NOT NULL DEFAULT '',provider TEXT NOT NULL DEFAULT 'MOCK_SBP',amount BIGINT NOT NULL DEFAULT 0,amount_rub BIGINT NOT NULL DEFAULT 0,donation_units BIGINT NOT NULL DEFAULT 0,currency TEXT NOT NULL DEFAULT 'RUB',status TEXT NOT NULL DEFAULT 'CREATED',qr_payload TEXT NOT NULL DEFAULT '',qr_image_path TEXT NOT NULL DEFAULT '',callback_payload_json TEXT NOT NULL DEFAULT '',idempotency_key TEXT NOT NULL DEFAULT '',created_at BIGINT NOT NULL DEFAULT 0,expires_at BIGINT NOT NULL DEFAULT 0,paid_at BIGINT NOT NULL DEFAULT 0,cancelled_at BIGINT NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL DEFAULT 0)");
            update(connection, "CREATE TABLE IF NOT EXISTS donation_purchases(id TEXT PRIMARY KEY,player_uuid TEXT NOT NULL,player_name TEXT NOT NULL DEFAULT '',item_id TEXT NOT NULL,price BIGINT NOT NULL DEFAULT 0,price_donation BIGINT NOT NULL DEFAULT 0,status TEXT NOT NULL DEFAULT 'CREATED',source TEXT NOT NULL DEFAULT '',idempotency_key TEXT NOT NULL DEFAULT '',created_at BIGINT NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL DEFAULT 0)");
            update(connection, "CREATE TABLE IF NOT EXISTS donation_item_claims(id TEXT PRIMARY KEY,player_uuid TEXT NOT NULL,item_id TEXT NOT NULL,amount BIGINT NOT NULL DEFAULT 1,status TEXT NOT NULL DEFAULT 'UNCLAIMED',claimed_at BIGINT NOT NULL DEFAULT 0,created_at BIGINT NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL DEFAULT 0,purchase_id TEXT NOT NULL DEFAULT '',actor TEXT NOT NULL DEFAULT '')");
            update(connection, "CREATE TABLE IF NOT EXISTS cmv4_pending_ar_settlements(id TEXT PRIMARY KEY,player_uuid TEXT NOT NULL,player_name TEXT NOT NULL DEFAULT '',amount BIGINT NOT NULL DEFAULT 0 CHECK(amount>0),settlement_type TEXT NOT NULL DEFAULT '',status TEXT NOT NULL DEFAULT 'PENDING',reason TEXT NOT NULL DEFAULT '',idempotency_key TEXT NOT NULL DEFAULT '',delivery_token TEXT NOT NULL DEFAULT '',created_at BIGINT NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL DEFAULT 0,delivered_at BIGINT NOT NULL DEFAULT 0)");
            update(connection, "ALTER TABLE cmv4_pending_ar_settlements ADD COLUMN IF NOT EXISTS idempotency_key TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE cmv4_pending_ar_settlements ADD COLUMN IF NOT EXISTS delivery_token TEXT NOT NULL DEFAULT ''");
            update(connection, "CREATE TABLE IF NOT EXISTS cmv8_ar_assets(serial TEXT PRIMARY KEY,denomination INTEGER NOT NULL CHECK(denomination>0),material TEXT NOT NULL,signature_version INTEGER NOT NULL,status TEXT NOT NULL DEFAULT 'ACTIVE',issued_units BIGINT NOT NULL DEFAULT 0,deposited_units BIGINT NOT NULL DEFAULT 0,deposited_intent_id TEXT NOT NULL DEFAULT '',deposited_account_id TEXT NOT NULL DEFAULT '',issued_at BIGINT NOT NULL DEFAULT 0,deposited_at BIGINT NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL DEFAULT 0)");
            update(connection, "ALTER TABLE cmv8_ar_assets ADD COLUMN IF NOT EXISTS issued_units BIGINT NOT NULL DEFAULT 0");
            update(connection, "ALTER TABLE cmv8_ar_assets ADD COLUMN IF NOT EXISTS deposited_units BIGINT NOT NULL DEFAULT 0");
            update(connection, "ALTER TABLE cmv8_ar_assets ADD COLUMN IF NOT EXISTS deposited_intent_id TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE cmv8_ar_assets ADD COLUMN IF NOT EXISTS deposited_account_id TEXT NOT NULL DEFAULT ''");
            update(connection, "CREATE TABLE IF NOT EXISTS cmv8_ar_asset_movements(intent_id TEXT NOT NULL,serial TEXT NOT NULL,account_id TEXT NOT NULL,amount BIGINT NOT NULL CHECK(amount>0),created_at BIGINT NOT NULL DEFAULT 0,PRIMARY KEY(intent_id,serial))");
            update(connection, "CREATE TABLE IF NOT EXISTS cmv8_ar_issuance_intents(idempotency_key TEXT PRIMARY KEY,serial TEXT NOT NULL,material TEXT NOT NULL,amount BIGINT NOT NULL CHECK(amount>0),status TEXT NOT NULL DEFAULT 'PREPARED',source TEXT NOT NULL DEFAULT '',created_at BIGINT NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL DEFAULT 0)");
            update(connection, "CREATE INDEX IF NOT EXISTS idx_cmv8_ar_assets_status ON cmv8_ar_assets(status,updated_at)");
            long arRegistryNow = now();
            for (Material material : List.of(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE)) {
                update(connection,
                        "INSERT INTO cmv8_ar_assets(serial,denomination,material,signature_version,status,issued_at,deposited_at,updated_at) VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(serial) DO NOTHING",
                        arFungibleSerial(material), 1, material.name(), OFFICIAL_AR_SIGNATURE_VERSION,
                        "ACTIVE", arRegistryNow, 0L, arRegistryNow);
            }
            update(connection, "CREATE TABLE IF NOT EXISTS cmv8_ar_deposit_intents(id TEXT PRIMARY KEY,player_uuid TEXT NOT NULL,player_name TEXT NOT NULL DEFAULT '',account_id TEXT NOT NULL,account_scope TEXT NOT NULL DEFAULT 'PERSONAL',atm_id TEXT NOT NULL DEFAULT '',amount BIGINT NOT NULL CHECK(amount>0),idempotency_key TEXT NOT NULL,snapshot TEXT NOT NULL,status TEXT NOT NULL DEFAULT 'CREATED',created_at BIGINT NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL DEFAULT 0,committed_at BIGINT NOT NULL DEFAULT 0)");
            update(connection, "CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv8_ar_deposit_intents_idempotency ON cmv8_ar_deposit_intents(idempotency_key)");
            update(connection, "CREATE INDEX IF NOT EXISTS idx_cmv8_ar_deposit_intents_player_status ON cmv8_ar_deposit_intents(player_uuid,status,created_at ASC)");
            update(connection, "CREATE TABLE IF NOT EXISTS protected_block_visuals(id TEXT PRIMARY KEY,kind TEXT NOT NULL,linked_id TEXT NOT NULL,world TEXT NOT NULL,x INTEGER NOT NULL,y INTEGER NOT NULL,z INTEGER NOT NULL,entity_uuid TEXT NOT NULL DEFAULT '',base_material TEXT NOT NULL DEFAULT 'PAPER',custom_model_data INTEGER NOT NULL DEFAULT 0,model_id TEXT NOT NULL DEFAULT '',offset_x DOUBLE PRECISION NOT NULL DEFAULT 0.5,offset_y DOUBLE PRECISION NOT NULL DEFAULT 0.5,offset_z DOUBLE PRECISION NOT NULL DEFAULT 0.5,scale_x DOUBLE PRECISION NOT NULL DEFAULT 1.01,scale_y DOUBLE PRECISION NOT NULL DEFAULT 1.01,scale_z DOUBLE PRECISION NOT NULL DEFAULT 1.01,yaw DOUBLE PRECISION NOT NULL DEFAULT 0,pitch DOUBLE PRECISION NOT NULL DEFAULT 0,created_at BIGINT NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL DEFAULT 0,active INTEGER NOT NULL DEFAULT 1)");
            update(connection, "ALTER TABLE donation_payment_sessions ADD COLUMN IF NOT EXISTS player_name TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE donation_payment_sessions ADD COLUMN IF NOT EXISTS amount_rub BIGINT NOT NULL DEFAULT 0");
            update(connection, "ALTER TABLE donation_payment_sessions ADD COLUMN IF NOT EXISTS donation_units BIGINT NOT NULL DEFAULT 0");
            update(connection, "ALTER TABLE donation_payment_sessions ADD COLUMN IF NOT EXISTS currency TEXT NOT NULL DEFAULT 'RUB'");
            update(connection, "ALTER TABLE donation_payment_sessions ADD COLUMN IF NOT EXISTS qr_image_path TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE donation_payment_sessions ADD COLUMN IF NOT EXISTS callback_payload_json TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE donation_payment_sessions ADD COLUMN IF NOT EXISTS idempotency_key TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE donation_payment_sessions ADD COLUMN IF NOT EXISTS paid_at BIGINT NOT NULL DEFAULT 0");
            update(connection, "ALTER TABLE donation_payment_sessions ADD COLUMN IF NOT EXISTS cancelled_at BIGINT NOT NULL DEFAULT 0");
            update(connection, "ALTER TABLE donation_purchases ADD COLUMN IF NOT EXISTS player_name TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE donation_purchases ADD COLUMN IF NOT EXISTS price_donation BIGINT NOT NULL DEFAULT 0");
            update(connection, "ALTER TABLE donation_purchases ADD COLUMN IF NOT EXISTS source TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE donation_purchases ADD COLUMN IF NOT EXISTS idempotency_key TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE ar_atms ADD COLUMN IF NOT EXISTS name TEXT NOT NULL DEFAULT 'Банкомат'");
            update(connection, "ALTER TABLE ar_atms ADD COLUMN IF NOT EXISTS active INTEGER NOT NULL DEFAULT 1");
            update(connection, "ALTER TABLE ar_atms ADD COLUMN IF NOT EXISTS created_by TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE ar_atms ADD COLUMN IF NOT EXISTS created_at BIGINT NOT NULL DEFAULT 0");
            update(connection, "ALTER TABLE ar_atms ADD COLUMN IF NOT EXISTS archived_by TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE ar_atms ADD COLUMN IF NOT EXISTS archived_at BIGINT NOT NULL DEFAULT 0");
            int archived = archiveDuplicateActiveAtms(connection);
            update(connection, "CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv4_bank_owner_type_active ON cmv4_bank_accounts(owner_uuid,account_type,currency) WHERE status='ACTIVE'");
            update(connection, "CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv4_bank_ledger_idempotency ON cmv4_bank_ledger(idempotency_key) WHERE idempotency_key<>''");
            update(connection, "CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv4_bank_transfers_idempotency ON cmv4_bank_transfers(idempotency_key) WHERE idempotency_key<>''");
            update(connection, "CREATE UNIQUE INDEX IF NOT EXISTS ux_donation_balance_ledger_idempotency ON donation_balance_ledger(idempotency_key) WHERE idempotency_key<>''");
            update(connection, "CREATE UNIQUE INDEX IF NOT EXISTS ux_donation_sessions_idempotency ON donation_payment_sessions(idempotency_key) WHERE idempotency_key<>''");
            update(connection, "CREATE UNIQUE INDEX IF NOT EXISTS ux_donation_purchases_idempotency ON donation_purchases(idempotency_key) WHERE idempotency_key<>''");
            update(connection, "CREATE INDEX IF NOT EXISTS idx_donation_balance_ledger_player_time ON donation_balance_ledger(player_uuid,created_at DESC)");
            update(connection, "CREATE INDEX IF NOT EXISTS idx_donation_sessions_player_status ON donation_payment_sessions(player_uuid,status,created_at DESC)");
            update(connection, "CREATE INDEX IF NOT EXISTS idx_donation_purchases_player_status ON donation_purchases(player_uuid,status,created_at DESC)");
            update(connection, "CREATE INDEX IF NOT EXISTS idx_donation_claims_player_status ON donation_item_claims(player_uuid,status,created_at DESC)");
            update(connection, "CREATE INDEX IF NOT EXISTS idx_cmv4_pending_ar_player_status ON cmv4_pending_ar_settlements(player_uuid,status,created_at ASC)");
            update(connection, "CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv4_pending_ar_idempotency ON cmv4_pending_ar_settlements(idempotency_key) WHERE idempotency_key<>''");
            update(connection, "CREATE UNIQUE INDEX IF NOT EXISTS ux_ar_atms_location_active ON ar_atms(world,x,y,z) WHERE active=1");
            update(connection, "CREATE INDEX IF NOT EXISTS idx_ar_atms_location ON ar_atms(world,x,y,z,active)");
            update(connection, "CREATE INDEX IF NOT EXISTS idx_protected_block_visuals_linked ON protected_block_visuals(linked_id,active)");
            update(connection, "CREATE INDEX IF NOT EXISTS idx_protected_block_visuals_location ON protected_block_visuals(world,x,y,z,active)");
            return archived;
        });
        if (archivedDuplicateAtms > 0) {
            getLogger().warning("Archived " + archivedDuplicateAtms + " duplicate active ATM record(s) before enforcing the location uniqueness constraint.");
        }
    }

    private int archiveDuplicateActiveAtms(Connection connection) throws Exception {
        return updateCount(connection,
                "WITH ranked AS (" +
                        "SELECT id,ROW_NUMBER() OVER (PARTITION BY world,x,y,z ORDER BY created_at ASC,id ASC) AS duplicate_rank " +
                        "FROM ar_atms WHERE active=1" +
                        ") UPDATE ar_atms SET active=0,archived_by='SYSTEM_DUPLICATE_CLEANUP',archived_at=? " +
                        "WHERE id IN (SELECT id FROM ranked WHERE duplicate_rank>1)",
                now());
    }

    private int quarantineInterruptedPendingArSettlements() throws Exception {
        return tx(connection -> updateCount(connection,
                "UPDATE cmv4_pending_ar_settlements SET status='DELIVERY_REVIEW',delivery_token='',reason=CASE WHEN reason='' THEN 'interrupted_after_reservation' ELSE reason || ';interrupted_after_reservation' END,updated_at=? WHERE status='DELIVERING'",
                now()));
    }

    private DbSettings loadDbSettings() throws Exception {
        Map<String, String> values = new HashMap<>(System.getenv());
        Path envFile = resolveEnvFile();
        if (Files.isRegularFile(envFile)) {
            for (String line : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isBlank() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                    continue;
                }
                int idx = trimmed.indexOf('=');
                String key = trimmed.substring(0, idx).trim();
                String value = trimmed.substring(idx + 1).trim().replace("\"", "");
                values.putIfAbsent(key, value);
            }
        }
        String host = first(values.get("POSTGRES_HOST"), values.get("PGHOST"), "127.0.0.1");
        int port = parseInt(first(values.get("POSTGRES_PORT"), values.get("PGPORT"), "5432"), 5432);
        String database = first(values.get("POSTGRES_DB"), values.get("PGDATABASE"), "copimine");
        String user = first(values.get("POSTGRES_USER"), values.get("PGUSER"), "copimine");
        String password = first(values.get("POSTGRES_PASSWORD"), values.get("PGPASSWORD"), "");
        String schema = first(values.get("POSTGRES_SCHEMA"), values.get("PGSCHEMA"), "copimine");
        if (password.isBlank()) {
            throw new IllegalStateException("POSTGRES_PASSWORD is required for CopiMineEconomyCore.");
        }
        if (!SAFE_SCHEMA.matcher(schema).matches()) {
            throw new IllegalStateException("Unsafe POSTGRES_SCHEMA.");
        }
        return new DbSettings(host, port, database, user, password, schema, envFile);
    }

    private Path resolveEnvFile() {
        String explicit = System.getenv("COPIMINE_ENV_FILE");
        if (explicit != null && !explicit.isBlank()) {
            return Paths.get(explicit).normalize();
        }
        List<Path> candidates = List.of(
                releaseRoot().resolve("admin-web").resolve(".env"),
                Paths.get(System.getProperty("user.dir")).resolve("../../admin-web/.env").normalize(),
                Paths.get(System.getProperty("user.dir")).resolve("../admin-web/.env").normalize()
        );
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return candidates.getFirst();
    }

    private byte[] loadOrCreateArSigningSecret() {
        String configured = System.getenv("COPIMINE_AR_SIGNING_SECRET");
        if (configured != null && !configured.isBlank()) {
            try {
                byte[] decoded = Base64.getDecoder().decode(configured.trim());
                if (decoded.length >= 32) {
                    return decoded;
                }
            } catch (IllegalArgumentException ignored) {
                // Fall through to the durable local secret. Never use a
                // short/invalid operator value as an item-signing key.
            }
        }
        Path secretFile = getDataFolder().toPath().resolve("ar-signing-secret.b64");
        try {
            Files.createDirectories(secretFile.getParent());
            if (Files.isRegularFile(secretFile)) {
                byte[] decoded = Base64.getDecoder().decode(Files.readString(secretFile, StandardCharsets.US_ASCII).trim());
                if (decoded.length >= 32) {
                    return decoded;
                }
            }
            byte[] generated = new byte[32];
            new SecureRandom().nextBytes(generated);
            Files.writeString(secretFile, Base64.getEncoder().encodeToString(generated), StandardCharsets.US_ASCII);
            return generated;
        } catch (Exception error) {
            getLogger().log(java.util.logging.Level.SEVERE, "Unable to persist AR signing secret", error);
            // A process-local fallback still prevents accidental vanilla AR
            // acceptance during this boot; the next restart will retry.
            byte[] generated = new byte[32];
            new SecureRandom().nextBytes(generated);
            return generated;
        }
    }

    private Path releaseRoot() {
        try {
            Path server = getServer().getWorldContainer().toPath().toAbsolutePath().normalize();
            Path minecraft = server.getParent();
            Path root = minecraft == null ? null : minecraft.getParent();
            if (root != null) {
                return root;
            }
        } catch (Throwable error) {
            getLogger().warning("releaseRoot fallback: " + safeError(error));
        }
        return Paths.get("/opt/copimine");
    }

    private <T> T tx(SqlWork<T> work) throws Exception {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (Exception error) {
                connection.rollback();
                throw error;
            }
        }
    }

    private Connection openConnection() throws Exception {
        if (!databaseReady.get() && !databaseInitializing.get()) {
            throw new IllegalStateException("CopiMineEconomyCore database is unavailable: " + first(startupError, "DATABASE_UNAVAILABLE"));
        }
        DbSettings settings = db;
        if (settings == null) {
            throw new IllegalStateException("CopiMineEconomyCore database is still starting.");
        }
        SimpleConnectionPool pool = connectionPool;
        Connection connection = pool == null
                ? DriverManager.getConnection(settings.jdbcUrl(), settings.user(), settings.password())
                : pool.acquire();
        try (Statement statement = connection.createStatement()) {
            // Schema creation is a startup migration, not a per-query
            // operation.  Repeating CREATE SCHEMA on every pooled acquire
            // adds DDL locks and latency to every ATM/bank click.  The one
            // bootstrap connection still creates it before ensureSchema();
            // all later connections only install their session settings.
            if (!schemaReady.get()) {
                synchronized (schemaReady) {
                    if (!schemaReady.get()) {
                        statement.execute("CREATE SCHEMA IF NOT EXISTS " + settings.schemaIdent());
                        schemaReady.set(true);
                    }
                }
            }
            statement.execute("SET search_path TO " + settings.schemaIdent());
            statement.execute("SET statement_timeout TO 15000");
            statement.execute("SET idle_in_transaction_session_timeout TO 30000");
        } catch (Exception error) {
            try {
                connection.close();
            } catch (Exception ignored) {
            }
            throw error;
        }
        return connection;
    }

    private void update(String sql, Object... args) throws Exception {
        tx(connection -> {
            update(connection, sql, args);
            return null;
        });
    }

    private void update(Connection connection, String sql, Object... args) throws Exception {
        updateCount(connection, sql, args);
    }

    private int updateCount(String sql, Object... args) throws Exception {
        return tx(connection -> updateCount(connection, sql, args));
    }

    private int updateCount(Connection connection, String sql, Object... args) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            return statement.executeUpdate();
        }
    }

    private Map<String, Object> queryOne(String sql, Object... args) throws Exception {
        try (Connection connection = openConnection()) {
            return queryOne(connection, sql, args);
        }
    }

    private Map<String, Object> queryOne(Connection connection, String sql, Object... args) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return row(resultSet);
            }
        }
    }

    private List<Map<String, Object>> queryList(String sql, Object... args) throws Exception {
        try (Connection connection = openConnection()) {
            return queryList(connection, sql, args);
        }
    }

    private List<Map<String, Object>> queryList(Connection connection, String sql, Object... args) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                while (resultSet.next()) {
                    rows.add(row(resultSet));
                }
                return rows;
            }
        }
    }

    private long scalarLong(String sql, Object... args) throws Exception {
        try (Connection connection = openConnection()) {
            return scalarLong(connection, sql, args);
        }
    }

    private long scalarLong(Connection connection, String sql, Object... args) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : 0L;
            }
        }
    }

    private void bind(PreparedStatement statement, Object... args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            statement.setObject(i + 1, args[i]);
        }
    }

    private Map<String, Object> row(ResultSet resultSet) throws SQLException {
        ResultSetMetaData metaData = resultSet.getMetaData();
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            row.put(metaData.getColumnLabel(i).toLowerCase(Locale.ROOT), resultSet.getObject(i));
        }
        return row;
    }

    private void pluginEvent(String source, String eventType, String actor, String target, String details) {
        dbAsync("economy plugin event", () -> update("INSERT INTO plugin_events(source,event_type,actor,target,created_at,details) VALUES(?,?,?,?,?,?)",
                source, eventType, actor, target, now(), details));
    }

    private void dbAsync(String label, SqlVoid body) {
        if (dbExecutor == null || dbExecutor.isShutdown()) {
            // Never fall back to JDBC on the Bukkit thread.  During startup
            // and shutdown the operation is retried by the owning workflow
            // (or recovered from its idempotency/review row).
            getLogger().warning(label + ": database executor is not ready; operation was not scheduled.");
            return;
        }
        CompletableFuture<Void> startup = databaseStartupFuture;
        if (!databaseReady.get()) {
            if (startupFailed.get()) {
                getLogger().warning(label + ": database startup failed; operation was not scheduled.");
                return;
            }
            startup.whenComplete((ignored, error) -> {
                if (error != null) {
                    getLogger().warning(label + ": database startup failed; operation was not scheduled.");
                    return;
                }
                dbAsync(label, body);
            });
            return;
        }
        try {
            dbExecutor.execute(() -> {
                try {
                    body.run();
                } catch (Exception error) {
                    getLogger().warning(label + ": " + safeError(error));
                }
            });
        } catch (RejectedExecutionException rejected) {
            getLogger().warning(label + ": database queue is full; operation was not scheduled.");
        }
    }

    private <T> CompletableFuture<T> dbFuture(String label, SqlSupplier<T> body) {
        if (dbExecutor == null || dbExecutor.isShutdown()) {
            CompletableFuture<T> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException(label + ": database executor is not ready."));
            return failed;
        }
        CompletableFuture<Void> startup = databaseStartupFuture;
        CompletableFuture<T> scheduled = startup.thenCompose(ignored -> {
            if (!databaseReady.get()) {
                CompletableFuture<T> failed = new CompletableFuture<>();
                failed.completeExceptionally(new IllegalStateException(label + ": database is unavailable."));
                return failed;
            }
            try {
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        return body.get();
                    } catch (Exception error) {
                        throw new CompletionException(new IllegalStateException(label + ": " + safeError(error), error));
                    }
                }, dbExecutor);
            } catch (RejectedExecutionException rejected) {
                CompletableFuture<T> failed = new CompletableFuture<>();
                failed.completeExceptionally(new IllegalStateException(label + ": database queue is full."));
                return failed;
            }
        });
        return scheduled;
    }

    private void requireAsyncBankContext(String operation) {
        if (Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(operation + " must not run on the Bukkit main thread.");
        }
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws Exception;
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private String safeError(Throwable error) {
        String message = error == null ? "unknown" : String.valueOf(error.getMessage());
        return message.replaceAll("(?i)(password=)[^\\s&]+", "$1***").replaceAll("(?i)(POSTGRES_PASSWORD=)[^\\s&]+", "$1***");
    }

    private void notifyEconomyBug(Player player, String source, String action, Throwable error, ItemStack item, Location location) {
        if (player == null) {
            return;
        }
        String token = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String world = location != null && location.getWorld() != null ? location.getWorld().getName() : "";
        int x = location == null ? 0 : location.getBlockX();
        int y = location == null ? 0 : location.getBlockY();
        int z = location == null ? 0 : location.getBlockZ();
        String itemType = item == null || item.getType() == Material.AIR ? "AIR" : item.getType().name();
        String summary = first(safeError(error), error == null ? "unknown" : error.getClass().getSimpleName());
        getLogger().warning("economy-player-bug token=" + token
                + " player=" + player.getName()
                + " source=" + first(source, "unknown")
                + " action=" + first(action, "unknown")
                + " world=" + world
                + " x=" + x
                + " y=" + y
                + " z=" + z
                + " item=" + itemType
                + " error=" + summary);
        player.sendTitle(color("&6Поздравляем, вы нашли баг"), color("&fОбратитесь к админу за вознаграждением"), 10, 80, 15);
        player.sendMessage(color("&cОшибка обработана. Код: &f" + token + "&c."));
        player.sendMessage(color("&7Если проблема повторится, отправьте: &f/report BUG " + token + " economy " + first(action, "unknown")));
    }

    private String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception error) {
            return fallback;
        }
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return parseInt(String.valueOf(value), 0);
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception error) {
            return 0L;
        }
    }

    /*
        private TxnResult mutateInConnection(Connection connection, UUID playerUuid, String playerName, long delta, String reason, String actor, String source, String idempotencyKey) throws Exception {
            if (playerUuid == null || delta == 0L) {
                return new TxnResult(false, "INVALID_REQUEST", "Некорректный donation-запрос.", 0L, "");
            }
            String uuid = playerUuid.toString();
            ensureDonationAccount(connection, uuid, first(playerName, ""));
            String key = first(idempotencyKey, "");
            if (!key.isBlank()) {
                List<Map<String, Object>> existing = queryList(connection, "SELECT id,balance_after FROM donation_balance_ledger WHERE idempotency_key=? LIMIT 1", key);
                if (!existing.isEmpty()) {
                    return new TxnResult(true, "OK", "Idempotent replay.", longValue(existing.getFirst().get("balance_after")), string(existing.getFirst().get("id")));
                }
            }
            long before = scalarLong(connection, "SELECT COALESCE(balance,0) FROM donation_accounts WHERE player_uuid=? FOR UPDATE", uuid);
            long after = before + delta;
            if (after < 0L) {
                return new TxnResult(false, "INSUFFICIENT_DONATION_BALANCE", "Недостаточно донат-баланса.", before, "");
            }
            long t = now();
            String txId = "don-" + UUID.randomUUID();
            update(connection, "UPDATE donation_accounts SET balance=?,updated_at=?,player_name=? WHERE player_uuid=?", after, t, first(playerName, ""), uuid);
            update(connection, "INSERT INTO donation_balance_ledger(id,player_uuid,delta,balance_after,reason,actor,source,idempotency_key,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                    txId, uuid, delta, after, first(reason, ""), first(actor, ""), first(source, ""), key, t);
            return new TxnResult(true, "OK", delta >= 0 ? "Баланс пополнен." : "Баланс списан.", after, txId);
        }
    }

    */

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String shortId(String id) {
        return id == null || id.isBlank() ? "—" : (id.length() <= 10 ? id : id.substring(0, 10));
    }

    private String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value & 0xff));
        }
        return builder.toString();
    }

    private void refreshOpenTaxButtons() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (!(top.getHolder() instanceof MenuHolder holder) || !"economy:atm".equals(holder.id())) {
                continue;
            }
            String scope = holder.data.getOrDefault("scope", "PERSONAL");
            if (!"PERSONAL".equalsIgnoreCase(scope)) {
                continue;
            }
            TaxStatusCache cache = taxStatusCache.get(player.getUniqueId());
            if (cache == null || now - cache.fetchedAtMillis() >= 30_000L) {
                refreshTaxButton(player, holder, holder.contextId());
            } else {
                renderTaxButton(holder, top, cache.status());
            }
        }
    }

    private void refreshTaxButton(Player player, MenuHolder holder, String atmId) {
        taxStatusCache.put(player.getUniqueId(), new TaxStatusCache(TaxStatus.unavailable(), System.currentTimeMillis()));
        TaxService service = Bukkit.getServicesManager().load(TaxService.class);
        if (service == null) {
            renderTaxButton(holder, holder.getInventory(), TaxStatus.unavailable());
            return;
        }
        CompletableFuture<TaxStatus> future;
        try {
            future = service.statusAsync(player.getUniqueId());
        } catch (Exception error) {
            getLogger().warning("ATM tax status: " + safeError(error));
            renderTaxButton(holder, holder.getInventory(), TaxStatus.unavailable());
            return;
        }
        if (future == null) {
            renderTaxButton(holder, holder.getInventory(), TaxStatus.unavailable());
            return;
        }
        future.exceptionally(error -> TaxStatus.unavailable()).thenAccept(status -> Bukkit.getScheduler().runTask(this, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != holder.getInventory()) {
                return;
            }
            taxStatusCache.put(player.getUniqueId(), new TaxStatusCache(status == null ? TaxStatus.unavailable() : status, System.currentTimeMillis()));
            renderTaxButton(holder, holder.getInventory(), status == null ? TaxStatus.unavailable() : status);
        }));
    }

    private void renderTaxButton(MenuHolder holder, Inventory inventory, TaxStatus status) {
        if (holder == null || inventory == null) {
            return;
        }
        holder.actions.remove(20);
        holder.rightActions.remove(20);
        TaxStatus value = status == null ? TaxStatus.inactive() : status;
        if (!value.available()) {
            button(holder, inventory, 20, Material.ORANGE_WOOL, "&6Налог временно недоступен", List.of("&7Не удалось получить состояние налога."), "");
        } else if (!value.active()) {
            button(holder, inventory, 20, Material.GRAY_WOOL, "&7Налог не назначен", List.of("&7Президент пока не установил налог."), "");
        } else if (value.subscription()) {
            button(holder, inventory, 20, Material.YELLOW_WOOL, "&eНалог оплачен", List.of(
                    "&7Подписка освобождает от налога на 3 месяца.",
                    "&7Осталось: &f" + formatTaxRemaining(value.validUntilMillis())
            ), "");
        } else if (value.paid()) {
            button(holder, inventory, 20, Material.LIME_WOOL, "&aНалог оплачен", List.of(
                    "&7Текущий период уже закрыт.",
                    "&7Оплачено на: &f" + value.periodHours() + " ч.",
                    "&7Осталось: &f" + formatTaxRemaining(value.validUntilMillis())
            ), "");
        } else {
            button(holder, inventory, 20, Material.EMERALD, "&aОплатить налог", List.of(
                    "&7Сумма: &f" + value.amount() + " AR",
                    "&7Платёж добровольный, потребуется PIN банка."
            ), "atm:tax:" + holder.contextId());
        }
    }

    private String formatTaxRemaining(long validUntilMillis) {
        long seconds = Math.max(0L, (validUntilMillis - System.currentTimeMillis()) / 1000L);
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        if (days > 0L) {
            return days + " д. " + hours + " ч.";
        }
        if (hours > 0L) {
            return hours + " ч. " + minutes + " мин.";
        }
        return Math.max(1L, seconds / 60L) + " мин.";
    }

    private void button(MenuHolder holder, Inventory inventory, int slot, Material material, String title, List<String> lore, String action) {
        button(holder, inventory, slot, material, title, lore, action, null);
    }

    private void button(MenuHolder holder, Inventory inventory, int slot, Material material, String title, List<String> lore, String action, String rightAction) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(title));
            if (lore != null && !lore.isEmpty()) {
                List<String> lines = new ArrayList<>();
                for (String line : lore) {
                    lines.add(color(line));
                }
                meta.setLore(lines);
            }
            meta.addItemFlags(ItemFlag.values());
            stack.setItemMeta(meta);
        }
        inventory.setItem(slot, stack);
        if (action != null && !action.isBlank()) {
            holder.actions.put(slot, action);
        }
        if (rightAction != null && !rightAction.isBlank()) {
            holder.rightActions.put(slot, rightAction);
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(Connection connection) throws Exception;
    }

    @FunctionalInterface
    private interface SqlVoid {
        void run() throws Exception;
    }

    /** Small bounded JDBC pool used when Hikari is not present in the server. */
    private static final class SimpleConnectionPool {
        private final DbSettings settings;
        private final int maxSize;
        private final BlockingQueue<Connection> idle = new ArrayBlockingQueue<>(8);
        private int created;
        private boolean closed;

        private SimpleConnectionPool(DbSettings settings, int maxSize) {
            this.settings = settings;
            this.maxSize = Math.max(2, Math.min(8, maxSize));
        }

        private Connection acquire() throws SQLException {
            Connection delegate;
            while (true) {
                synchronized (this) {
                    if (closed) {
                        throw new SQLException("Economy connection pool is closed");
                    }
                    delegate = idle.poll();
                    if (delegate == null && created < maxSize) {
                        delegate = createPhysical();
                        created++;
                    }
                }
                if (delegate != null) {
                    break;
                }
                try {
                    delegate = idle.poll(1L, TimeUnit.SECONDS);
                    if (delegate == null) {
                        synchronized (this) {
                            if (closed) {
                                throw new SQLException("Economy connection pool is closed");
                            }
                        }
                        continue;
                    }
                    break;
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new SQLException("Interrupted while waiting for economy connection", interrupted);
                }
            }
            Connection physical = delegate;
            AtomicBoolean logicalClosed = new AtomicBoolean(false);
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        if ("close".equals(method.getName())) {
                            if (logicalClosed.compareAndSet(false, true)) {
                                release(physical);
                            }
                            return null;
                        }
                        if ("isClosed".equals(method.getName())) {
                            return logicalClosed.get() || physical.isClosed();
                        }
                        if (logicalClosed.get()) {
                            throw new SQLException("Connection is closed");
                        }
                        try {
                            return method.invoke(physical, args);
                        } catch (InvocationTargetException target) {
                            throw target.getCause();
                        }
                    });
        }

        private Connection createPhysical() throws SQLException {
            try {
                Connection connection = DriverManager.getConnection(settings.jdbcUrl(), settings.user(), settings.password());
                connection.setAutoCommit(true);
                return connection;
            } catch (SQLException error) {
                throw error;
            }
        }

        private synchronized void release(Connection connection) {
            if (connection == null) return;
            if (closed) {
                closePhysical(connection);
                created = Math.max(0, created - 1);
                return;
            }
            try {
                if (!connection.getAutoCommit()) {
                    connection.rollback();
                    connection.setAutoCommit(true);
                }
                connection.clearWarnings();
                if (!idle.offer(connection)) {
                    closePhysical(connection);
                    created = Math.max(0, created - 1);
                }
            } catch (SQLException error) {
                closePhysical(connection);
                created = Math.max(0, created - 1);
            }
        }

        private synchronized void close() {
            closed = true;
            Connection connection;
            while ((connection = idle.poll()) != null) {
                closePhysical(connection);
                created = Math.max(0, created - 1);
            }
        }

        private void closePhysical(Connection connection) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
        }
    }

    private record DbSettings(String host, int port, String database, String user, String password, String schema, Path envFile) {
        String jdbcUrl() {
            return "jdbc:postgresql://" + host + ":" + port + "/" + database
                    + "?connectTimeout=5&socketTimeout=20&tcpKeepAlive=true&ApplicationName=CopiMineEconomyCore";
        }

        String schemaIdent() {
            return "\"" + schema.replace("\"", "\"\"") + "\"";
        }
    }

    private record AtmVisualRepairBatch(String worldName, int chunkX, int chunkZ, List<Map<String, Object>> rows) {}

    private record AtmPinSession(String atmId, String accountScope, String action, int amount, String pin, String targetUuid, String targetName) {}

    private record TaxStatusCache(TaxStatus status, long fetchedAtMillis) {}

    private static final class MenuHolder implements InventoryHolder {
        private final String id;
        private final String contextId;
        private final Map<Integer, String> actions = new HashMap<>();
        private final Map<Integer, String> rightActions = new HashMap<>();
        private final Map<String, String> data = new HashMap<>();
        private Inventory inventory;

        private MenuHolder(String id, String contextId) {
            this.id = id;
            this.contextId = contextId;
        }

        Inventory create(int size, String title) {
            inventory = Bukkit.createInventory(this, size, title);
            return inventory;
        }

        String id() {
            return id;
        }

        String contextId() {
            return contextId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final class EconomyServiceImpl implements EconomyService {
        @Override
        public BankService bankService() {
            return bankService;
        }

        @Override
        public PinService pinService() {
            return pinService;
        }

        @Override
        public AtmService atmService() {
            return atmService;
        }

        @Override
        public LedgerService ledgerService() {
            return ledgerService;
        }

        @Override
        public DonationBalanceService donationBalanceService() {
            return donationBalanceService;
        }

        @Override
        public DonationPaymentService donationPaymentService() {
            return donationPaymentService;
        }

        @Override
        public DonationPurchaseService donationPurchaseService() {
            return donationPurchaseService;
        }
    }

    private final class DonationBalanceServiceImpl implements DonationBalanceService {
        @Override
        public long balance(UUID playerUuid, String playerName) {
            requireAsyncBankContext("DonationBalanceService.balance");
            if (playerUuid == null) {
                return 0L;
            }
            try (Connection connection = openConnection()) {
                ensureDonationAccount(connection, playerUuid.toString(), first(playerName, ""));
                return scalarLong(connection, "SELECT COALESCE(balance,0) FROM donation_accounts WHERE player_uuid=?", playerUuid.toString());
            } catch (Exception error) {
                return 0L;
            }
        }

        @Override
        public CompletableFuture<Long> balanceAsync(UUID playerUuid, String playerName) {
            return dbFuture("donation balance", () -> balance(playerUuid, playerName));
        }

        @Override
        public TxnResult add(UUID playerUuid, String playerName, long amount, String reason, String actor, String source, String idempotencyKey) {
            return mutate(playerUuid, playerName, Math.max(0L, amount), reason, actor, source, idempotencyKey);
        }

        @Override
        public CompletableFuture<TxnResult> addAsync(UUID playerUuid, String playerName, long amount, String reason, String actor, String source, String idempotencyKey) {
            return dbFuture("donation add", () -> add(playerUuid, playerName, amount, reason, actor, source, idempotencyKey));
        }

        @Override
        public TxnResult subtract(UUID playerUuid, String playerName, long amount, String reason, String actor, String source, String idempotencyKey) {
            return mutate(playerUuid, playerName, -Math.max(0L, amount), reason, actor, source, idempotencyKey);
        }

        @Override
        public CompletableFuture<TxnResult> subtractAsync(UUID playerUuid, String playerName, long amount, String reason, String actor, String source, String idempotencyKey) {
            return dbFuture("donation subtract", () -> subtract(playerUuid, playerName, amount, reason, actor, source, idempotencyKey));
        }

        @Override
        public CompletableFuture<List<Map<String, Object>>> ledgerAsync(UUID playerUuid, int limit) {
            return dbFuture("donation ledger", () -> {
                if (playerUuid == null) {
                    return List.of();
                }
                return queryList("SELECT id,delta,balance_after,reason,actor,source,created_at FROM donation_balance_ledger WHERE player_uuid=? ORDER BY created_at DESC LIMIT ?",
                        playerUuid.toString(), Math.max(1, Math.min(limit, 200)));
            });
        }

        private TxnResult mutate(UUID playerUuid, String playerName, long delta, String reason, String actor, String source, String idempotencyKey) {
            if (playerUuid == null || delta == 0L) {
                return new TxnResult(false, "INVALID_REQUEST", "Некорректный donation-запрос.", 0L, "");
            }
            try {
                return tx(connection -> mutateDonationBalanceInConnection(connection, playerUuid, playerName, delta, reason, actor, source, idempotencyKey));
                /*
                return tx(connection -> {
                    String uuid = playerUuid.toString();
                    ensureDonationAccount(connection, uuid, first(playerName, ""));
                    String key = first(idempotencyKey, "");
                    if (!key.isBlank()) {
                        List<Map<String, Object>> existing = queryList(connection, "SELECT id,balance_after FROM donation_balance_ledger WHERE idempotency_key=? LIMIT 1", key);
                        if (!existing.isEmpty()) {
                            return new TxnResult(true, "OK", "Idempotent replay.", longValue(existing.getFirst().get("balance_after")), string(existing.getFirst().get("id")));
                        }
                    }
                    long before = scalarLong(connection, "SELECT COALESCE(balance,0) FROM donation_accounts WHERE player_uuid=? FOR UPDATE", uuid);
                    long after = before + delta;
                    if (after < 0L) {
                        return new TxnResult(false, "INSUFFICIENT_DONATION_BALANCE", "Недостаточно донат-баланса.", before, "");
                    }
                    long t = now();
                    String txId = "don-" + UUID.randomUUID();
                    update(connection, "UPDATE donation_accounts SET balance=?,updated_at=?,player_name=? WHERE player_uuid=?", after, t, first(playerName, ""), uuid);
                    update(connection, "INSERT INTO donation_balance_ledger(id,player_uuid,delta,balance_after,reason,actor,source,idempotency_key,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                            txId, uuid, delta, after, first(reason, ""), first(actor, ""), first(source, ""), key, t);
                    return new TxnResult(true, "OK", delta >= 0 ? "Баланс пополнен." : "Баланс списан.", after, txId);
                });
                */
            } catch (Exception error) {
                return new TxnResult(false, "DONATION_ERROR", safeError(error), 0L, "");
            }
        }
    }

    private final class DonationPaymentServiceImpl implements DonationPaymentService {
        @Override
        public Set<Long> allowedPacks() {
            return DONATION_PACKS;
        }

        @Override
        public CompletableFuture<Map<String, Object>> createSessionAsync(UUID playerUuid, String playerName, long amountRub, String actor, String source, String idempotencyKey) {
            return dbFuture("donation session create", () -> tx(connection -> {
                requireFixedDonationPack(amountRub);
                if (playerUuid == null) {
                    throw new IllegalArgumentException("Donation session requires a linked player UUID.");
                }
                String uuid = playerUuid == null ? "" : playerUuid.toString();
                String key = first(idempotencyKey, "").trim();
                if (!key.isBlank()) {
                    Map<String, Object> existing = queryOne(connection, "SELECT * FROM donation_payment_sessions WHERE idempotency_key=? LIMIT 1", key);
                    if (existing != null) {
                        if (!uuid.equalsIgnoreCase(string(existing.get("player_uuid")))) {
                            throw new IllegalStateException("Donation session idempotency key already belongs to another player.");
                        }
                        long existingAmount = Math.max(0L, longValue(existing.get("amount_rub")));
                        if (existingAmount != amountRub) {
                            throw new IllegalStateException("Donation session idempotency key already belongs to a different amount.");
                        }
                        return sanitizeSession(existing);
                    }
                }
                long current = now();
                String sessionId = "donation-session-" + UUID.randomUUID();
                long donationUnits = amountRub;
                String qrPayload = buildMockSbpQrPayload(playerUuid, amountRub, sessionId);
                update(connection, """
                        INSERT INTO donation_payment_sessions(
                            id,player_uuid,player_name,provider,amount,amount_rub,donation_units,currency,status,qr_payload,qr_image_path,callback_payload_json,idempotency_key,created_at,expires_at,paid_at,cancelled_at,updated_at
                        ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                        """,
                        sessionId, uuid, first(playerName, ""), "MOCK_SBP", amountRub, amountRub, donationUnits, "RUB", "PENDING",
                        qrPayload, "", "", key, current, current + DONATION_SESSION_TTL_MS, 0L, 0L, current);
                pluginEvent("donation", "session_created", first(actor, ""), uuid, "session=" + sessionId + " amount=" + amountRub + " source=" + first(source, ""));
                Map<String, Object> created = new LinkedHashMap<>();
                created.put("session_id", sessionId);
                created.put("player_uuid", uuid);
                created.put("player_name", first(playerName, ""));
                created.put("provider", "MOCK_SBP");
                created.put("amount_rub", amountRub);
                created.put("donation_units", donationUnits);
                created.put("currency", "RUB");
                created.put("status", "PENDING");
                created.put("qr_payload", qrPayload);
                created.put("created_at", current);
                created.put("expires_at", current + DONATION_SESSION_TTL_MS);
                created.put("paid_at", 0L);
                created.put("cancelled_at", 0L);
                return created;
            }));
        }

        @Override
        public CompletableFuture<Map<String, Object>> sessionAsync(UUID playerUuid, String sessionId) {
            return dbFuture("donation session read", () -> tx(connection -> {
                if (first(sessionId, "").isBlank()) {
                    return Map.of();
                }
                Map<String, Object> row = queryOne(connection, "SELECT * FROM donation_payment_sessions WHERE id=? FOR UPDATE", sessionId);
                if (row == null) {
                    return Map.of();
                }
                if (playerUuid != null) {
                    String owner = string(row.get("player_uuid"));
                    if (!owner.isBlank() && !owner.equalsIgnoreCase(playerUuid.toString())) {
                        return Map.of();
                    }
                }
                long current = now();
                if (donationSessionExpired(row, current)) {
                    update(connection, "UPDATE donation_payment_sessions SET status='EXPIRED',updated_at=? WHERE id=? AND status IN ('CREATED','PENDING')", current, sessionId);
                    row.put("status", "EXPIRED");
                    row.put("updated_at", current);
                }
                return sanitizeSession(row);
            }));
        }

        @Override
        public CompletableFuture<Map<String, Object>> markPaidAsync(String sessionId, String actor, String idempotencyKey) {
            return dbFuture("donation session mark paid", () -> tx(connection -> {
                if (first(sessionId, "").isBlank()) {
                    throw new IllegalArgumentException("Donation session id is required.");
                }
                String ledgerKey = "donation-session-paid-" + sessionId;
                Map<String, Object> row = queryOne(connection, "SELECT * FROM donation_payment_sessions WHERE id=? FOR UPDATE", sessionId);
                if (row == null) {
                    throw new IllegalArgumentException("Unknown donation session: " + sessionId);
                }
                long current = now();
                if (donationSessionExpired(row, current)) {
                    update(connection, "UPDATE donation_payment_sessions SET status='EXPIRED',updated_at=? WHERE id=? AND status IN ('CREATED','PENDING')", current, sessionId);
                    throw new IllegalStateException("Donation session is expired.");
                }
                String status = string(row.get("status"));
                String uuid = string(row.get("player_uuid"));
                if ("PAID".equalsIgnoreCase(status)) {
                    Map<String, Object> ledger = queryOne(connection,
                            "SELECT id,balance_after FROM donation_balance_ledger WHERE idempotency_key=? LIMIT 1",
                            ledgerKey);
                    if (ledger == null || ledger.isEmpty()) {
                        pluginEvent("donation", "session_manual_review", first(actor, ""), uuid, "session=" + sessionId + " reason=paid_without_ledger");
                        throw new IllegalStateException("Donation session is already marked paid, but its ledger entry is missing. Manual review is required.");
                    }
                    return sanitizeSession(row);
                }
                if ("CANCELLED".equalsIgnoreCase(status) || "EXPIRED".equalsIgnoreCase(status)) {
                    throw new IllegalStateException("Donation session is not payable anymore.");
                }
                if (uuid.isBlank()) {
                    throw new IllegalStateException("Donation session is missing a linked player UUID.");
                }
                String playerName = string(row.get("player_name"));
                long units = Math.max(0L, longValue(row.get("donation_units")));
                long amountRub = Math.max(0L, longValue(row.get("amount_rub")));
                if (units <= 0L) {
                    units = amountRub;
                }
                TxnResult credit = mutateDonationBalanceInConnection(connection, UUID.fromString(uuid), playerName, units, "DONATION_TOPUP", first(actor, ""), "mock_sbp", ledgerKey);
                if (!credit.ok) {
                    throw new IllegalStateException(first(credit.message, "Failed to credit donation balance."));
                }
                update(connection, "UPDATE donation_payment_sessions SET status='PAID',paid_at=?,updated_at=? WHERE id=?", current, current, sessionId);
                row.put("status", "PAID");
                row.put("paid_at", current);
                row.put("updated_at", current);
                pluginEvent("donation", "session_paid", first(actor, ""), uuid, "session=" + sessionId + " amount=" + amountRub + " units=" + units);
                return sanitizeSession(row);
            }));
        }

        @Override
        public CompletableFuture<Map<String, Object>> cancelAsync(String sessionId, String actor) {
            return dbFuture("donation session cancel", () -> tx(connection -> {
                if (first(sessionId, "").isBlank()) {
                    throw new IllegalArgumentException("Donation session id is required.");
                }
                Map<String, Object> row = queryOne(connection, "SELECT * FROM donation_payment_sessions WHERE id=? FOR UPDATE", sessionId);
                if (row == null) {
                    throw new IllegalArgumentException("Unknown donation session: " + sessionId);
                }
                long current = now();
                if (donationSessionExpired(row, current)) {
                    update(connection, "UPDATE donation_payment_sessions SET status='EXPIRED',updated_at=? WHERE id=? AND status IN ('CREATED','PENDING')", current, sessionId);
                    throw new IllegalStateException("Donation session is expired.");
                }
                String status = string(row.get("status"));
                if ("PAID".equalsIgnoreCase(status) || "CANCELLED".equalsIgnoreCase(status)) {
                    return sanitizeSession(row);
                }
                update(connection, "UPDATE donation_payment_sessions SET status='CANCELLED',cancelled_at=?,updated_at=? WHERE id=?", current, current, sessionId);
                row.put("status", "CANCELLED");
                row.put("cancelled_at", current);
                row.put("updated_at", current);
                pluginEvent("donation", "session_cancelled", first(actor, ""), string(row.get("player_uuid")), "session=" + sessionId);
                return sanitizeSession(row);
            }));
        }
    }

    private void requireFixedDonationPack(long amountRub) {
        if (!DONATION_PACKS.contains(amountRub)) {
            throw new IllegalArgumentException("Unsupported donation pack: " + amountRub);
        }
    }

    private String buildMockSbpQrPayload(UUID playerUuid, long amountRub, String sessionId) {
        String uuid = playerUuid == null ? "" : playerUuid.toString();
        return "SBP|MOCK_SBP|session=" + sessionId + "|player=" + uuid + "|amount=" + amountRub + "|currency=RUB";
    }

    private Map<String, Object> sanitizeSession(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        String sessionId = first(string(row.get("id")), string(row.get("session_id")));
        safe.put("id", sessionId);
        safe.put("session_id", sessionId);
        safe.put("player_uuid", string(row.get("player_uuid")));
        safe.put("player_name", string(row.get("player_name")));
        safe.put("provider", first(string(row.get("provider")), "MOCK_SBP"));
        safe.put("amount_rub", longValue(firstNonNull(row.get("amount_rub"), row.get("amount"))));
        safe.put("donation_units", longValue(firstNonNull(row.get("donation_units"), row.get("amount"))));
        safe.put("currency", first(string(row.get("currency")), "RUB"));
        safe.put("status", first(string(row.get("status")), "CREATED"));
        safe.put("qr_payload", string(row.get("qr_payload")));
        safe.put("qr_image_path", string(row.get("qr_image_path")));
        safe.put("created_at", longValue(row.get("created_at")));
        safe.put("expires_at", longValue(row.get("expires_at")));
        safe.put("paid_at", longValue(row.get("paid_at")));
        safe.put("cancelled_at", longValue(row.get("cancelled_at")));
        safe.put("updated_at", longValue(row.get("updated_at")));
        safe.put("session_code", sessionId.length() > 8 ? sessionId.substring(sessionId.length() - 8) : sessionId);
        return safe;
    }

    private boolean donationSessionExpired(Map<String, Object> row, long current) {
        if (row == null || row.isEmpty()) {
            return false;
        }
        String status = string(row.get("status"));
        long expiresAt = longValue(row.get("expires_at"));
        return ("CREATED".equalsIgnoreCase(status) || "PENDING".equalsIgnoreCase(status))
                && expiresAt > 0L
                && expiresAt <= current;
    }

    private Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
    }

    private String normalizeDonationItemId(String itemId) {
        String normalized = first(itemId, "").trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Donation claim item id is required.");
        }
        Plugin plugin = Bukkit.getPluginManager().getPlugin("CopiMineArtifacts");
        if (plugin == null || !plugin.isEnabled()) {
            throw new IllegalStateException("CopiMineArtifacts is required to validate donation item ids.");
        }
        try {
            Object result = plugin.getClass().getMethod("knowsDonationCatalogItem", String.class).invoke(plugin, normalized);
            if (!(result instanceof Boolean known) || !known) {
                throw new IllegalArgumentException("Unknown donation catalog item id: " + normalized);
            }
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Unable to validate donation item id via CopiMineArtifacts.", error);
        }
        return normalized;
    }

    private long resolveDonationCatalogPrice(String itemId) {
        String normalized = normalizeDonationItemId(itemId);
        Plugin plugin = Bukkit.getPluginManager().getPlugin("CopiMineArtifacts");
        if (plugin == null || !plugin.isEnabled()) {
            throw new IllegalStateException("CopiMineArtifacts is required to resolve donation item prices.");
        }
        try {
            Object result = plugin.getClass().getMethod("donationCatalogPrice", String.class).invoke(plugin, normalized);
            long price = result instanceof Number number ? number.longValue() : longValue(result);
            if (price <= 0L) {
                throw new IllegalStateException("Donation catalog price must be positive for item " + normalized);
            }
            return price;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Unable to resolve donation price via CopiMineArtifacts.", error);
        }
    }

    private void requireKnownDonationItem(String itemId) {
        normalizeDonationItemId(itemId);
    }

    private boolean hasOpenDonationEntitlement(Connection connection, String playerUuid, String itemId) throws SQLException {
        if (first(playerUuid, "").isBlank() || first(itemId, "").isBlank()) {
            return false;
        }
        try (PreparedStatement claims = connection.prepareStatement("""
                SELECT 1
                FROM donation_item_claims
                WHERE player_uuid=?
                  AND item_id=?
                  AND status IN ('UNCLAIMED','RESERVED','DELIVERING','DELIVERY_REVIEW')
                  AND (CASE WHEN created_at > 100000000000 THEN created_at ELSE created_at * 1000 END) >
                      COALESCE((SELECT CASE WHEN reset_at > 100000000000 THEN reset_at ELSE reset_at * 1000 END
                                FROM artifact_purchase_limit_resets
                                WHERE player_uuid=? AND item_id=?),0)
                LIMIT 1
                """);
             PreparedStatement instances = connection.prepareStatement("""
                SELECT 1
                FROM artifact_item_instances
                WHERE owner_uuid=?
                  AND item_id=?
                  AND status IN ('ACTIVE','DELIVERING','PENDING_DELIVERY')
                  AND (CASE WHEN created_at > 100000000000 THEN created_at ELSE created_at * 1000 END) >
                      COALESCE((SELECT CASE WHEN reset_at > 100000000000 THEN reset_at ELSE reset_at * 1000 END
                                FROM artifact_purchase_limit_resets
                                WHERE player_uuid=? AND item_id=?),0)
                LIMIT 1
                """)) {
            claims.setString(1, playerUuid);
            claims.setString(2, itemId);
            claims.setString(3, playerUuid);
            claims.setString(4, itemId);
            try (ResultSet rs = claims.executeQuery()) {
                if (rs.next()) {
                    return true;
                }
            }
            instances.setString(1, playerUuid);
            instances.setString(2, itemId);
            instances.setString(3, playerUuid);
            instances.setString(4, itemId);
            try (ResultSet rs = instances.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void lockDonationEntitlement(Connection connection, String playerUuid, String itemId) throws SQLException {
        if (connection == null || first(playerUuid, "").isBlank() || first(itemId, "").isBlank()) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))")) {
            ps.setString(1, "donation-entitlement:" + playerUuid + ":" + itemId.toLowerCase(Locale.ROOT));
            ps.execute();
        }
    }

    private final class DonationPurchaseServiceImpl implements DonationPurchaseService {
        @Override
        public CompletableFuture<Map<String, Object>> createAdminGiftAsync(UUID playerUuid, String playerName, String itemId, String actor, String idempotencyKey) {
            return dbFuture("create administrative donation gift", () -> tx(connection -> {
                if (playerUuid == null) throw new IllegalArgumentException("Administrative gift requires player UUID.");
                String normalized = normalizeDonationItemId(itemId);
                String giftKey = first(idempotencyKey, "").trim();
                if (giftKey.isBlank()) throw new IllegalArgumentException("Administrative gift requires an idempotency key.");
                Map<String, Object> existing = queryOne(connection, "SELECT id,item_id,status FROM donation_purchases WHERE idempotency_key=? LIMIT 1", giftKey);
                if (existing != null) return Map.of("purchase_id", string(existing.get("id")), "item_id", string(existing.get("item_id")), "status", string(existing.get("status")), "idempotent", true);
                lockDonationEntitlement(connection, playerUuid.toString(), normalized);
                if (hasOpenDonationEntitlement(connection, playerUuid.toString(), normalized)) throw new IllegalStateException("Player already has an active or unfinished donation entitlement for this item.");
                long current = now();
                String purchaseId = "admin-gift-" + UUID.randomUUID();
                String claimId = "don-claim-" + UUID.randomUUID();
                update(connection, "INSERT INTO donation_purchases(id,player_uuid,player_name,item_id,price,price_donation,status,source,idempotency_key,created_at,updated_at) VALUES(?,?,?,?,0,0,'CLAIM_PENDING','ADMIN_GIFT',?,?,?)", purchaseId, playerUuid.toString(), first(playerName, ""), normalized, giftKey, current, current);
                update(connection, "INSERT INTO donation_item_claims(id,player_uuid,item_id,amount,status,claimed_at,created_at,updated_at,purchase_id,actor) VALUES(?,?,?,?, 'UNCLAIMED',0,?,?,?,?)", claimId, playerUuid.toString(), normalized, 1L, current, current, purchaseId, first(actor, ""));
                pluginEvent("donation", "admin_gift", first(actor, ""), playerUuid.toString(), "purchase=" + purchaseId + " item=" + normalized);
                return Map.of("purchase_id", purchaseId, "claim_id", claimId, "item_id", normalized, "status", "CLAIM_PENDING", "idempotent", false);
            }));
        }

        @Override
        public CompletableFuture<Map<String, Object>> createTestPurchaseAsync(UUID playerUuid, String playerName, String itemId, long price, String actor) {
            return dbFuture("create donation test purchase", () -> tx(connection -> {
                String normalized = normalizeDonationItemId(itemId);
                long catalogPrice = resolveDonationCatalogPrice(normalized);
                String uuid = playerUuid == null ? "" : playerUuid.toString();
                lockDonationEntitlement(connection, uuid, normalized);
                if (hasOpenDonationEntitlement(connection, uuid, normalized)) {
                    throw new IllegalStateException("Player already has an active or unfinished donation entitlement for this item.");
                }
                long t = now();
                String purchaseId = "don-purchase-" + UUID.randomUUID();
                update(connection, "INSERT INTO donation_purchases(id,player_uuid,player_name,item_id,price,price_donation,status,source,idempotency_key,created_at,updated_at) VALUES(?,?,?,?,?,?, 'CLAIM_PENDING', ?, ?, ?, ?)",
                        purchaseId, uuid, first(playerName, ""), normalized, catalogPrice, catalogPrice, "admin_test_purchase", "admin-test-purchase-" + purchaseId, t, t);
                String claimId = "don-claim-" + UUID.randomUUID();
                update(connection, "INSERT INTO donation_item_claims(id,player_uuid,item_id,amount,status,claimed_at,created_at,updated_at,purchase_id,actor) VALUES(?,?,?,?, 'UNCLAIMED', 0, ?, ?, ?, ?)",
                        claimId, uuid, normalized, 1L, t, t, purchaseId, first(actor, ""));
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("purchase_id", purchaseId);
                row.put("claim_id", claimId);
                row.put("player_uuid", uuid);
                row.put("player_name", first(playerName, ""));
                row.put("item_id", normalized);
                row.put("price", catalogPrice);
                row.put("price_donation", catalogPrice);
                row.put("status", "CLAIM_PENDING");
                return row;
            }));
        }

        @Override
        public CompletableFuture<Map<String, Object>> purchaseIntentAsync(UUID playerUuid, String playerName, String itemId, long priceDonation, String actor, String source, String idempotencyKey) {
            return dbFuture("donation purchase intent", () -> tx(connection -> {
                if (playerUuid == null) {
                    throw new IllegalArgumentException("Donation purchase requires player UUID.");
                }
                String normalized = normalizeDonationItemId(itemId);
                String purchaseKey = first(idempotencyKey, "").trim();
                if (!purchaseKey.isBlank()) {
                    Map<String, Object> existing = queryOne(connection, "SELECT id,player_uuid,item_id,status,price_donation,created_at FROM donation_purchases WHERE idempotency_key=? LIMIT 1", purchaseKey);
                    if (existing != null) {
                        if (!playerUuid.toString().equalsIgnoreCase(string(existing.get("player_uuid")))) {
                            throw new IllegalStateException("Donation purchase idempotency key already belongs to another player.");
                        }
                        if (!normalized.equalsIgnoreCase(string(existing.get("item_id")))) {
                            throw new IllegalStateException("Donation purchase idempotency key already belongs to another item.");
                        }
                        long storedPrice = longValue(firstNonNull(existing.get("price_donation"), existing.get("price")));
                        if (priceDonation > 0L && storedPrice > 0L && priceDonation != storedPrice) {
                            throw new IllegalStateException("Donation purchase idempotency key already belongs to another price.");
                        }
                        return Map.of(
                                "purchase_id", string(existing.get("id")),
                                "item_id", string(existing.get("item_id")),
                                "status", string(existing.get("status")),
                                "price_donation", storedPrice,
                                "created_at", longValue(existing.get("created_at")),
                                "idempotent", true
                        );
                    }
                }
                Map<String, Object> linkedBank = queryOne(connection,
                    "SELECT account_id FROM cmv4_bank_accounts WHERE account_id=? AND owner_uuid=? AND status='ACTIVE' LIMIT 1",
                    bankAccountId(playerUuid.toString()), playerUuid.toString());
                if (linkedBank == null || linkedBank.isEmpty()) {
                    throw new IllegalStateException("Donation purchase requires a linked active AR bank account.");
                }
                lockDonationEntitlement(connection, playerUuid.toString(), normalized);
                if (hasOpenDonationEntitlement(connection, playerUuid.toString(), normalized)) {
                    throw new IllegalStateException("Player already has an active or unfinished donation entitlement for this item.");
                }
                long price = resolveDonationCatalogPrice(normalized);
                TxnResult charge = mutateDonationBalanceInConnection(connection, playerUuid, playerName, -price, "DONATION_PURCHASE", first(actor, ""), first(source, "donation_shop"), first(purchaseKey, "purchase-intent-" + playerUuid + "-" + normalized + "-" + now()));
                if (!charge.ok) {
                    throw new IllegalStateException(first(charge.message, "Donation purchase failed."));
                }
                long current = now();
                String purchaseId = "don-purchase-" + UUID.randomUUID();
                String claimId = "don-claim-" + UUID.randomUUID();
                update(connection, "INSERT INTO donation_purchases(id,player_uuid,player_name,item_id,price,price_donation,status,source,idempotency_key,created_at,updated_at) VALUES(?,?,?,?,?,?, 'CLAIM_PENDING', ?, ?, ?, ?)",
                        purchaseId, playerUuid.toString(), first(playerName, ""), normalized, price, price, first(source, "donation_shop"), purchaseKey, current, current);
                update(connection, "INSERT INTO donation_item_claims(id,player_uuid,item_id,amount,status,claimed_at,created_at,updated_at,purchase_id,actor) VALUES(?,?,?,?, 'UNCLAIMED', 0, ?, ?, ?, ?)",
                        claimId, playerUuid.toString(), normalized, 1L, current, current, purchaseId, first(actor, ""));
                pluginEvent("donation", "purchase_created", first(actor, ""), playerUuid.toString(), "purchase=" + purchaseId + " item=" + normalized + " price=" + price);
                return Map.of(
                        "purchase_id", purchaseId,
                        "claim_id", claimId,
                        "item_id", normalized,
                        "status", "CLAIM_PENDING",
                        "price_donation", price,
                        "balance_after", charge.balanceAfter
                );
            }));
        }

        @Override
        public CompletableFuture<Map<String, Object>> createClaimAsync(UUID playerUuid, String itemId, long amount, String actor, String purchaseId) {
            return dbFuture("create donation claim", () -> tx(connection -> {
                if (playerUuid == null) {
                    throw new IllegalArgumentException("Donation claim requires player UUID.");
                }
                String normalized = normalizeDonationItemId(itemId);
                long normalizedAmount = Math.max(1L, amount);
                if (normalizedAmount != 1L) {
                    throw new IllegalArgumentException("Donation claim amount must be exactly 1 for owner-bound donation items.");
                }
                String playerUuidText = playerUuid.toString();
                lockDonationEntitlement(connection, playerUuidText, normalized);
                if (hasOpenDonationEntitlement(connection, playerUuidText, normalized)) {
                    throw new IllegalStateException("Player already has an active or unfinished donation entitlement for this item.");
                }
                String claimId = "don-claim-" + UUID.randomUUID();
                long t = now();
                update(connection, "INSERT INTO donation_item_claims(id,player_uuid,item_id,amount,status,claimed_at,created_at,updated_at,purchase_id,actor) VALUES(?,?,?,?, 'UNCLAIMED', 0, ?, ?, ?, ?)",
                        claimId, playerUuidText, normalized, normalizedAmount, t, t, first(purchaseId, ""), first(actor, ""));
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("claim_id", claimId);
                row.put("player_uuid", playerUuidText);
                row.put("item_id", normalized);
                row.put("amount", normalizedAmount);
                row.put("status", "UNCLAIMED");
                return row;
            }));
        }

        @Override
        public CompletableFuture<List<Map<String, Object>>> getUnclaimedItemsAsync(UUID playerUuid, int limit) {
            return dbFuture("donation claims", () -> {
                if (playerUuid == null) {
                    return List.of();
                }
                return queryList("""
                        SELECT id,item_id,amount,status,purchase_id,created_at,updated_at
                        FROM donation_item_claims
                        WHERE player_uuid=?
                          AND status IN ('UNCLAIMED','RESERVED','DELIVERING','DELIVERY_REVIEW')
                        ORDER BY created_at DESC
                        LIMIT ?
                        """, playerUuid.toString(), Math.max(1, Math.min(limit, 200)));
            });
        }

        @Override
        public CompletableFuture<Boolean> reserveClaimAsync(UUID playerUuid, String claimId) {
            return dbFuture("reserve donation claim", () -> {
                if (playerUuid == null || first(claimId, "").isBlank()) {
                    return false;
                }
                return tx(connection -> {
                    List<Map<String, Object>> rows = queryList(connection, "SELECT status FROM donation_item_claims WHERE id=? AND player_uuid=? FOR UPDATE", claimId, playerUuid.toString());
                    if (rows.isEmpty()) {
                        return false;
                    }
                    if (!"UNCLAIMED".equalsIgnoreCase(string(rows.getFirst().get("status")))) {
                        return false;
                    }
                    update(connection, "UPDATE donation_item_claims SET status='RESERVED',updated_at=? WHERE id=?", now(), claimId);
                    return true;
                });
            });
        }

        @Override
        public CompletableFuture<Boolean> markClaimDeliveringAsync(UUID playerUuid, String claimId) {
            return dbFuture("mark donation claim delivering", () -> {
                if (playerUuid == null || first(claimId, "").isBlank()) {
                    return false;
                }
                return tx(connection -> {
                    List<Map<String, Object>> rows = queryList(connection, "SELECT status,purchase_id FROM donation_item_claims WHERE id=? AND player_uuid=? FOR UPDATE", claimId, playerUuid.toString());
                    if (rows.isEmpty()) {
                        return false;
                    }
                    if (!"RESERVED".equalsIgnoreCase(string(rows.getFirst().get("status")))) {
                        return false;
                    }
                    long current = now();
                    update(connection, "UPDATE donation_item_claims SET status='DELIVERING',updated_at=? WHERE id=?", current, claimId);
                    update(connection, "UPDATE donation_purchases SET status='DELIVERING',updated_at=? WHERE id=?", current, first(string(rows.getFirst().get("purchase_id")), ""));
                    return true;
                });
            });
        }

        @Override
        public CompletableFuture<Boolean> completeClaimAsync(UUID playerUuid, String claimId) {
            return dbFuture("complete donation claim", () -> {
                if (playerUuid == null || first(claimId, "").isBlank()) {
                    return false;
                }
                return tx(connection -> {
                    List<Map<String, Object>> rows = queryList(connection, "SELECT status,purchase_id FROM donation_item_claims WHERE id=? AND player_uuid=? FOR UPDATE", claimId, playerUuid.toString());
                    if (rows.isEmpty()) {
                        return false;
                    }
                    if (!"DELIVERING".equalsIgnoreCase(string(rows.getFirst().get("status")))) {
                        return false;
                    }
                    long current = now();
                    update(connection, "UPDATE donation_item_claims SET status='CLAIMED',claimed_at=?,updated_at=? WHERE id=?", current, current, claimId);
                    update(connection, "UPDATE donation_purchases SET status='CLAIMED',updated_at=? WHERE id=?", current, first(string(rows.getFirst().get("purchase_id")), ""));
                    return true;
                });
            });
        }

        @Override
        public CompletableFuture<Boolean> completeClaimByPurchaseAsync(UUID playerUuid, String purchaseId) {
            return dbFuture("complete donation claim by purchase", () -> {
                if (playerUuid == null || first(purchaseId, "").isBlank()) {
                    return false;
                }
                return tx(connection -> {
                    List<Map<String, Object>> rows = queryList(connection, "SELECT id,status FROM donation_item_claims WHERE purchase_id=? AND player_uuid=? FOR UPDATE", purchaseId, playerUuid.toString());
                    if (rows.isEmpty()) {
                        return false;
                    }
                    Map<String, Object> row = rows.getFirst();
                    String status = string(row.get("status"));
                    if ("CLAIMED".equalsIgnoreCase(status)) {
                        return true;
                    }
                    if (!"DELIVERING".equalsIgnoreCase(status)) {
                        return false;
                    }
                    long current = now();
                    update(connection, "UPDATE donation_item_claims SET status='CLAIMED',claimed_at=?,updated_at=? WHERE id=?", current, current, string(row.get("id")));
                    update(connection, "UPDATE donation_purchases SET status='CLAIMED',updated_at=? WHERE id=?", current, purchaseId);
                    return true;
                });
            });
        }

        @Override
        public CompletableFuture<Boolean> markClaimDeliveryReviewAsync(UUID playerUuid, String claimId) {
            return dbFuture("mark donation claim delivery review", () -> {
                if (playerUuid == null || first(claimId, "").isBlank()) {
                    return false;
                }
                return tx(connection -> {
                    List<Map<String, Object>> rows = queryList(connection, "SELECT status,purchase_id FROM donation_item_claims WHERE id=? AND player_uuid=? FOR UPDATE", claimId, playerUuid.toString());
                    if (rows.isEmpty()) {
                        return false;
                    }
                    String status = string(rows.getFirst().get("status"));
                    if (!"DELIVERING".equalsIgnoreCase(status) && !"RESERVED".equalsIgnoreCase(status)) {
                        return false;
                    }
                    long current = now();
                    update(connection, "UPDATE donation_item_claims SET status='DELIVERY_REVIEW',updated_at=? WHERE id=?", current, claimId);
                    update(connection, "UPDATE donation_purchases SET status='DELIVERY_REVIEW',updated_at=? WHERE id=?", current, first(string(rows.getFirst().get("purchase_id")), ""));
                    return true;
                });
            });
        }

        @Override
        public CompletableFuture<Boolean> releaseClaimAsync(UUID playerUuid, String claimId) {
            return dbFuture("release donation claim", () -> {
                if (playerUuid == null || first(claimId, "").isBlank()) {
                    return false;
                }
                return tx(connection -> {
                    List<Map<String, Object>> rows = queryList(connection, "SELECT status,purchase_id FROM donation_item_claims WHERE id=? AND player_uuid=? FOR UPDATE", claimId, playerUuid.toString());
                    if (rows.isEmpty()) {
                        return false;
                    }
                    if (!"RESERVED".equalsIgnoreCase(string(rows.getFirst().get("status")))) {
                        return false;
                    }
                    long current = now();
                    update(connection, "UPDATE donation_item_claims SET status='UNCLAIMED',updated_at=? WHERE id=?", current, claimId);
                    update(connection, "UPDATE donation_purchases SET status='CLAIM_PENDING',updated_at=? WHERE id=?", current, first(string(rows.getFirst().get("purchase_id")), ""));
                    return true;
                });
            });
        }
    }

    private final class PinServiceImpl implements PinService {
        @Override
        public PinStatus pinStatus(UUID playerUuid) {
            return BankServiceImpl.pinStatusFor(playerUuid);
        }

        @Override
        public boolean verify(UUID playerUuid, String pin) throws Exception {
            return playerUuid != null && verifyBankPin(playerUuid.toString(), pin);
        }
    }

    private final class LedgerServiceImpl implements LedgerService {
        @Override
        public void pluginEvent(String source, String eventType, String actor, String target, String details) {
            CopiMineEconomyCore.this.pluginEvent(source, eventType, actor, target, details);
        }
    }

    private final class AtmServiceImpl implements AtmService {
        @Override
        public boolean isAtmBlock(Block block) {
            return CopiMineEconomyCore.this.isAtmBlock(block);
        }

        @Override
        public String atmId(Block block) {
            return CopiMineEconomyCore.this.atmId(block);
        }

        @Override
        public void openAtm(Player player, String atmId) throws Exception {
            openBankAtm(player, atmId);
        }

        @Override
        public void openAdminHub(Player player) throws Exception {
            openAdminEconomyHub(player);
        }

        @Override
        public void openAtmDirectory(Player player) throws Exception {
            if (!requireEconomyAdmin(player)) {
                return;
            }
            openBankAtms(player);
        }

        @Override
        public String createAtTarget(Player player) throws Exception {
            if (!requireEconomyAdmin(player)) {
                return "";
            }
            return createBankAtmFromTargetAsync(player);
        }

        @Override
        public String archive(Player actor, String atmId) throws Exception {
            if (!requireEconomyAdmin(actor)) {
                return "";
            }
            return archiveBankAtmAsync(actor, atmId);
        }
    }

    private final class ArtifactsBridgeImpl implements ArtifactsBridge {
        @Override
        public PinStatus pinStatus(UUID playerUuid) {
            return BankServiceImpl.pinStatusFor(playerUuid);
        }

        @Override
        public CompletableFuture<PinStatus> pinStatusAsync(UUID playerUuid) {
            return dbFuture("artifacts pin status", () -> BankServiceImpl.pinStatusFor(playerUuid));
        }

        @Override
        public TxnResult charge(UUID playerUuid, String playerName, long amount, String pin, String idempotencyKey, String action, String details) {
            return bankService.charge(playerUuid, playerName, amount, pin, idempotencyKey, action, details);
        }

        @Override
        public TxnResult creditAccount(String accountId, String ownerUuid, String ownerName, long amount, String idempotencyKey, String action, String details) {
            return CopiMineEconomyCore.this.creditAccount(accountId, ownerUuid, ownerName, amount, idempotencyKey, action, details);
        }

        @Override
        public TxnResult transferToAccount(UUID playerUuid, String playerName, String pin, String toAccountId, String toOwnerUuid, String toOwnerName, long amount, String idempotencyKey, String action, String details) {
            return CopiMineEconomyCore.this.transferPlayerToAccount(playerUuid, playerName, pin, toAccountId, toOwnerUuid, toOwnerName, amount, idempotencyKey, action, details);
        }

        @Override
        public TxnResult transferFromAccount(String fromAccountId, String fromOwnerUuid, String fromOwnerName, UUID toUuid, String toName, long amount, String idempotencyKey, String action, String details) {
            return CopiMineEconomyCore.this.transferFromAccount(fromAccountId, fromOwnerUuid, fromOwnerName, toUuid, toName, amount, idempotencyKey, action, details);
        }

        @Override
        public TxnResult stealFromPlayerAccount(UUID fromUuid, String fromName, UUID toUuid, String toName, long amount, String idempotencyKey, String action, String details) {
            return CopiMineEconomyCore.this.stealFromPlayerAccount(fromUuid, fromName, toUuid, toName, amount, idempotencyKey, action, details);
        }

        @Override
        public List<Map<String, Object>> findOrphanedArtifactShopTransfers(int limit) {
            requireAsyncBankContext("ArtifactsBridge.findOrphanedArtifactShopTransfers");
            int boundedLimit = Math.max(1, Math.min(limit, 256));
            try {
                return queryList(
                    "SELECT t.tx_id,t.to_account_id,t.amount,t.idempotency_key,l.player_uuid,l.actor "
                        + "FROM cmv4_bank_transfers t "
                        + "JOIN cmv4_bank_ledger l ON l.tx_id=t.tx_id || ':out' AND l.tx_type='AR_SHOP_PURCHASE' "
                        + "WHERE t.idempotency_key LIKE 'artifact-purchase-%' "
                        + "  AND NOT EXISTS (SELECT 1 FROM artifact_purchases p WHERE p.idempotency_key=t.idempotency_key) "
                        + "  AND NOT EXISTS (SELECT 1 FROM cmv4_bank_transfers r WHERE r.idempotency_key IN ( "
                        + "      REPLACE(t.idempotency_key,'artifact-purchase-','artifact-refund-'), "
                        + "      REPLACE(t.idempotency_key,'artifact-purchase-','artifact-orphan-refund-'))) "
                        + "ORDER BY t.created_at ASC LIMIT ?",
                    boundedLimit
                );
            } catch (Exception error) {
                getLogger().log(java.util.logging.Level.WARNING, "Artifact orphan transfer lookup failed", error);
                return List.of();
            }
        }

        @Override
        public TxnResult refund(UUID playerUuid, String playerName, long amount, String idempotencyKey, String action, String details) {
            return bankService.refund(playerUuid, playerName, amount, idempotencyKey, action, details);
        }

        @Override
        public TxnResult credit(UUID playerUuid, String playerName, long amount, String idempotencyKey, String action, String details) {
            return bankService.credit(playerUuid, playerName, amount, idempotencyKey, action, details);
        }

        @Override
        public long balance(UUID playerUuid, String playerName) {
            return bankService.balance(playerUuid, playerName);
        }

        @Override
        public Health health(UUID playerUuid, String context) {
            if (!databaseReady.get()) {
                return new Health(false, false, false, 0L, first(context, "health"),
                        first(startupError, startupFailed.get() ? "DATABASE_UNAVAILABLE" : "DATABASE_STARTING"));
            }
            try {
                // A cached/empty PIN status is not proof that PostgreSQL is
                // healthy. Perform a bounded pool ping so callers never get
                // a false green readiness result after a DB outage.
                try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
                    statement.execute("SELECT 1");
                }
                PinStatus pin = pinStatus(playerUuid);
                // BankService.balance() intentionally returns a safe zero to
                // gameplay callers when a read fails.  Health must not use
                // that fail-soft value as a green readiness signal: perform
                // the same account/read operation here and let any SQL error
                // mark PostgreSQL unhealthy.
                long balance = playerUuid == null ? 0L : readBankBalanceForHealth(playerUuid);
                return new Health(true, true, pin.configured && !pin.mustChange && pin.lockedSeconds <= 0, balance, first(context, "health"), "");
            } catch (Exception error) {
                return new Health(false, false, false, 0L, first(context, "health"), safeError(error));
            }
        }
    }

    private long readBankBalanceForHealth(UUID playerUuid) throws Exception {
        if (playerUuid == null) {
            return 0L;
        }
        try (Connection connection = openConnection()) {
            ensureBankAccount(connection, playerUuid.toString(), "");
            return scalarLong(connection, "SELECT COALESCE(balance,0) FROM cmv4_bank_accounts WHERE account_id=?",
                    bankAccountId(playerUuid.toString()));
        }
    }

    private final class BankServiceImpl implements BankService {
        @Override
        public PinStatus pinStatus(UUID playerUuid) {
            return pinStatusFor(playerUuid);
        }

        private static PinStatus pinStatusFor(UUID playerUuid) {
            CopiMineEconomyCore plugin = JavaPlugin.getPlugin(CopiMineEconomyCore.class);
            if (playerUuid == null) {
                return new PinStatus(false, false, 0L);
            }
            try {
                String uuid = playerUuid.toString();
                return new PinStatus(plugin.bankPinSet(uuid), plugin.bankPinMustChange(uuid), plugin.bankPinLockedSeconds(uuid));
            } catch (Exception error) {
                return new PinStatus(false, false, 0L);
            }
        }

        @Override
        public long balance(UUID playerUuid, String playerName) {
            if (playerUuid == null) {
                return 0L;
            }
            try (Connection connection = openConnection()) {
                ensureBankAccount(connection, playerUuid.toString(), first(playerName, ""));
                return scalarLong(connection, "SELECT COALESCE(balance,0) FROM cmv4_bank_accounts WHERE account_id=?", bankAccountId(playerUuid.toString()));
            } catch (Exception error) {
                return 0L;
            }
        }

        @Override
        public CompletableFuture<Long> balanceAsync(UUID playerUuid, String playerName) {
            return dbFuture("bank balance", () -> balance(playerUuid, playerName));
        }

        @Override
        public TxnResult charge(UUID playerUuid, String playerName, long amount, String pin, String idempotencyKey, String action, String details) {
            requireAsyncBankContext("BankService.charge");
            return artifactStyleTxn(playerUuid, playerName, -Math.max(0L, amount), pin, idempotencyKey, action, details);
        }

        @Override
        public CompletableFuture<TxnResult> chargeAsync(UUID playerUuid, String playerName, long amount, String pin, String idempotencyKey, String action, String details) {
            return dbFuture("bank charge", () -> charge(playerUuid, playerName, amount, pin, idempotencyKey, action, details));
        }

        @Override
        public TxnResult refund(UUID playerUuid, String playerName, long amount, String idempotencyKey, String action, String details) {
            return artifactStyleTxn(playerUuid, playerName, Math.max(0L, amount), "", idempotencyKey, action, details);
        }

        @Override
        public TxnResult transferWithPin(UUID fromUuid, String fromName, UUID toUuid, String toName, long amount, String pin, String idempotencyKey, String action, String details) {
            requireAsyncBankContext("BankService.transferWithPin");
            if (fromUuid == null || toUuid == null || amount <= 0) {
                return new TxnResult(false, "INVALID_REQUEST", "Некорректные данные перевода.", 0L, "");
            }
            if (fromUuid.equals(toUuid)) {
                return new TxnResult(false, "INVALID_REQUEST", "Нельзя перевести AR на тот же счёт.", 0L, "");
            }
            try {
                return tx(connection -> {
                    Map<String, Object> fromAccount = ensureBankAccount(connection, fromUuid.toString(), first(fromName, ""));
                    Map<String, Object> toAccount = ensureBankAccount(connection, toUuid.toString(), first(toName, ""));
                    String fromId = string(fromAccount.get("account_id"));
                    String toId = string(toAccount.get("account_id"));
                    PinVerification pinVerification = verifyPersonalPinForMutation(
                            connection, fromUuid.toString(), pin, "economy-transfer");
                    if (!pinVerification.valid()) {
                        return new TxnResult(false, pinVerification.code(), pinVerification.message(), 0L, "");
                    }
                    String txKey = first(idempotencyKey, "tx-" + UUID.randomUUID());
                    TxnResult replay = replayTransferIfCommitted(connection, txKey, fromId, toId, amount);
                    if (replay != null) {
                        return replay;
                    }
                    long before;
                    long targetBefore;
                    if (fromId.compareTo(toId) <= 0) {
                        before = scalarLong(connection, "SELECT COALESCE(balance,0) FROM cmv4_bank_accounts WHERE account_id=? FOR UPDATE", fromId);
                        targetBefore = scalarLong(connection, "SELECT COALESCE(balance,0) FROM cmv4_bank_accounts WHERE account_id=? FOR UPDATE", toId);
                    } else {
                        targetBefore = scalarLong(connection, "SELECT COALESCE(balance,0) FROM cmv4_bank_accounts WHERE account_id=? FOR UPDATE", toId);
                        before = scalarLong(connection, "SELECT COALESCE(balance,0) FROM cmv4_bank_accounts WHERE account_id=? FOR UPDATE", fromId);
                    }
                    if (before < amount) {
                        return new TxnResult(false, "INSUFFICIENT_AR", "Недостаточно AR на счёте.", before, "");
                    }
                    long t = now();
                    long after = before - amount;
                    long targetAfter = targetBefore + amount;
                    String txId = txKey;
                    update(connection, "UPDATE cmv4_bank_accounts SET balance=?,version=version+1,updated_at=? WHERE account_id=?", after, t, fromId);
                    update(connection, "UPDATE cmv4_bank_accounts SET balance=?,version=version+1,updated_at=? WHERE account_id=?", targetAfter, t, toId);
                    update(connection, "INSERT INTO cmv4_bank_transfers(tx_id,from_account_id,to_account_id,amount,currency,status,idempotency_key,created_at,actor,details) VALUES(?,?,?,?,'AR','COMMITTED',?,?,?,?)",
                            txId, fromId, toId, amount, txId, t, first(fromName, ""), first(details, ""));
                    update(connection, "INSERT INTO cmv4_bank_ledger(tx_id,account_id,counterparty_account_id,player_uuid,tx_type,amount,balance_after,idempotency_key,status,created_at,actor,details) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                            txId + ":out", fromId, toId, fromUuid.toString(), first(action, "TRANSFER_OUT"), amount, after, txId + ":out", "COMMITTED", t, first(fromName, ""), first(details, ""));
                    update(connection, "INSERT INTO cmv4_bank_ledger(tx_id,account_id,counterparty_account_id,player_uuid,tx_type,amount,balance_after,idempotency_key,status,created_at,actor,details) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                            txId + ":in", toId, fromId, toUuid.toString(), "TRANSFER_IN", amount, targetAfter, txId + ":in", "COMMITTED", t, first(fromName, ""), first(details, ""));
                    return new TxnResult(true, "OK", "Перевод выполнен.", after, txId);
                });
            } catch (Exception error) {
                try {
                    String txKey = first(idempotencyKey, "");
                    if (!txKey.isBlank()) {
                        TxnResult replay = tx(connection -> replayTransferIfCommitted(connection, txKey, bankAccountId(fromUuid.toString()), bankAccountId(toUuid.toString()), amount));
                        if (replay != null && replay.ok) {
                            return replay;
                        }
                    }
                } catch (Exception replayError) {
                    getLogger().warning("bank transfer replay from=" + fromUuid + " to=" + toUuid + " key=" + first(idempotencyKey, "") + " failed: " + safeError(replayError));
                }
                return new TxnResult(false, "BANK_ERROR", safeError(error), 0L, "");
            }
        }

        @Override
        public CompletableFuture<TxnResult> transferWithPinAsync(UUID fromUuid, String fromName, UUID toUuid, String toName, long amount, String pin, String idempotencyKey, String action, String details) {
            return dbFuture("bank transfer", () -> transferWithPin(fromUuid, fromName, toUuid, toName, amount, pin, idempotencyKey, action, details));
        }

        @Override
        public TxnResult transferToAccount(UUID playerUuid, String playerName, String pin, String toAccountId, String toOwnerUuid, String toOwnerName, long amount, String idempotencyKey, String action, String details) {
            return CopiMineEconomyCore.this.transferPlayerToAccount(playerUuid, playerName, pin, toAccountId, toOwnerUuid, toOwnerName, amount, idempotencyKey, action, details);
        }

        @Override
        public TxnResult credit(UUID toUuid, String toName, long amount, String idempotencyKey, String action, String details) {
            requireAsyncBankContext("BankService.credit");
            if (toUuid == null || amount <= 0) {
                return new TxnResult(false, "INVALID_REQUEST", "Некорректные данные пополнения.", 0L, "");
            }
            try {
                return tx(connection -> {
                    Map<String, Object> account = ensureBankAccount(connection, toUuid.toString(), first(toName, ""));
                    String accountId = string(account.get("account_id"));
                    String txKey = first(idempotencyKey, "credit-" + UUID.randomUUID());
                    TxnResult replay = replayCreditIfCommitted(connection, txKey, accountId);
                    if (replay != null) {
                        return replay;
                    }
                    long before = scalarLong(connection, "SELECT COALESCE(balance,0) FROM cmv4_bank_accounts WHERE account_id=? FOR UPDATE", accountId);
                    long after = before + amount;
                    long t = now();
                    String txId = txKey;
                    update(connection, "UPDATE cmv4_bank_accounts SET balance=?,version=version+1,updated_at=? WHERE account_id=?", after, t, accountId);
                    update(connection, "INSERT INTO cmv4_bank_ledger(tx_id,account_id,counterparty_account_id,player_uuid,tx_type,amount,balance_after,idempotency_key,status,created_at,actor,details) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                            txId, accountId, first(action, "CREDIT"), toUuid.toString(), first(action, "CREDIT"), amount, after, txId, "COMMITTED", t, first(toName, ""), first(details, ""));
                    return new TxnResult(true, "OK", "Пополнение выполнено.", after, txId);
                });
            } catch (Exception error) {
                try {
                    String txKey = first(idempotencyKey, "");
                    if (!txKey.isBlank()) {
                        TxnResult replay = tx(connection -> replayCreditIfCommitted(connection, txKey, bankAccountId(toUuid.toString())));
                        if (replay != null && replay.ok) {
                            return replay;
                        }
                    }
                } catch (Exception replayError) {
                    getLogger().warning("bank credit replay player=" + toUuid + " key=" + first(idempotencyKey, "") + " failed: " + safeError(replayError));
                }
                return new TxnResult(false, "BANK_ERROR", safeError(error), 0L, "");
            }
        }

        @Override
        public CompletableFuture<TxnResult> creditAsync(UUID toUuid, String toName, long amount, String idempotencyKey, String action, String details) {
            return dbFuture("bank credit", () -> credit(toUuid, toName, amount, idempotencyKey, action, details));
        }

        private TxnResult artifactStyleTxn(UUID playerUuid, String playerName, long signedAmount, String pin, String idempotencyKey, String action, String details) {
            if (playerUuid == null) {
                return new TxnResult(false, "PLAYER_MISSING", "Player UUID is missing.", 0L, "");
            }
            String uuid = playerUuid.toString();
            String accountId = bankAccountId(uuid);
            long amount = Math.abs(signedAmount);
            if (amount <= 0) {
                return new TxnResult(false, "AMOUNT_INVALID", "Amount must be positive.", 0L, "");
            }
            String txKey = first(idempotencyKey, "bank-" + UUID.randomUUID());
            String actionName = first(action, "txn");
            String detailText = first(details, "");
            try {
                return tx(connection -> {
                    ensureBankAccount(connection, uuid, first(playerName, ""));
                    if (signedAmount < 0) {
                        PinVerification pinVerification = verifyPersonalPinForMutation(
                                connection, uuid, pin, "economy-bridge");
                        if (!pinVerification.valid()) {
                            return new TxnResult(false, pinVerification.code(), pinVerification.message(), 0L, "");
                        }
                    }
                    TxnResult replay = replayArtifactStyleTxn(connection, txKey, accountId, uuid, signedAmount, detailText);
                    if (replay != null) {
                        return replay;
                    }
                    long before = scalarLong(connection, "SELECT COALESCE(balance,0) FROM cmv4_bank_accounts WHERE account_id=? FOR UPDATE", accountId);
                    long after = signedAmount < 0 ? before - amount : before + amount;
                    if (after < 0) {
                        return new TxnResult(false, "INSUFFICIENT_AR", "Not enough AR in bank.", before, "");
                    }
                    long t = now();
                    String txId = "bank-" + actionName + "-" + UUID.randomUUID();
                    update(connection, "UPDATE cmv4_bank_accounts SET balance=?,version=version+1,updated_at=? WHERE account_id=?", after, t, accountId);
                    update(connection, "INSERT INTO cmv4_bank_ledger(tx_id,account_id,counterparty_account_id,player_uuid,tx_type,amount,balance_after,idempotency_key,status,created_at,actor,details) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                            txId, accountId, first(actionName, ""), uuid, signedAmount < 0 ? "DEBIT" : "CREDIT", amount, after, txKey, "COMMITTED", t, first(playerName, ""), detailText);
                    createWithdrawalDelivery(connection, playerUuid, playerName, amount, txId, actionName, detailText);
                    return new TxnResult(true, "OK", "Committed.", after, txId);
                });
            } catch (Exception error) {
                // The connection may have failed after PostgreSQL committed
                // the ledger row.  Replay by the idempotency key before
                // reporting an error; otherwise Artifacts could compensate a
                // debit that actually succeeded and create a duplicate.
                try {
                    TxnResult replay = tx(connection -> replayArtifactStyleTxn(
                            connection, txKey, accountId, uuid, signedAmount, detailText));
                    if (replay != null) {
                        return replay;
                    }
                } catch (Exception replayError) {
                    getLogger().warning("artifact bridge replay failed key=" + txKey + ": " + safeError(replayError));
                }
                return new TxnResult(false, "BANK_ERROR", safeError(error), 0L, "");
            }
        }

        private void createWithdrawalDelivery(Connection connection, UUID playerUuid, String playerName, long amount,
                                               String transactionId, String actionName, String details) throws Exception {
            if (playerUuid == null || amount <= 0L || transactionId == null || transactionId.isBlank()
                    || !("ATM_WITHDRAW".equalsIgnoreCase(first(actionName, "")))) {
                return;
            }
            long createdAt = now();
            update(connection,
                    "INSERT INTO cmv4_pending_ar_settlements(id,player_uuid,player_name,amount,settlement_type,status,reason,idempotency_key,created_at,updated_at,delivered_at) VALUES(?,?,?,?,?,?,?,?,?,?,0) ON CONFLICT(idempotency_key) DO NOTHING",
                    "withdrawal-" + transactionId, playerUuid.toString(), first(playerName, ""), amount,
                    PENDING_AR_SETTLEMENT_TYPE_WITHDRAW_DELIVERY, PENDING_AR_SETTLEMENT_STATUS_DEBITED,
                    first(details, "") + ";tx=" + transactionId, transactionId, createdAt, createdAt);
        }

        private TxnResult replayTransferIfCommitted(Connection connection, String txKey, String fromAccountId, String toAccountId, long amount) throws Exception {
            if (txKey == null || txKey.isBlank()) {
                return null;
            }
            Map<String, Object> existing = queryOne(connection,
                    "SELECT tx_id,from_account_id,to_account_id,amount FROM cmv4_bank_transfers WHERE idempotency_key=? LIMIT 1",
                    txKey);
            if (existing == null || existing.isEmpty()) {
                return null;
            }
            if (!fromAccountId.equalsIgnoreCase(string(existing.get("from_account_id")))
                    || !toAccountId.equalsIgnoreCase(string(existing.get("to_account_id")))
                    || longValue(existing.get("amount")) != amount) {
                return new TxnResult(false, "IDEMPOTENCY_CONFLICT", "Ключ перевода уже привязан к другой операции.", 0L, "");
            }
            long replayBalance = scalarLong(connection,
                    "SELECT COALESCE(balance_after,0) FROM cmv4_bank_ledger WHERE tx_id=? LIMIT 1",
                    string(existing.get("tx_id")) + ":out");
            return new TxnResult(true, "OK", "Идемпотентный повтор.", replayBalance, string(existing.get("tx_id")));
        }

        private TxnResult replayCreditIfCommitted(Connection connection, String txKey, String accountId) throws Exception {
            if (txKey == null || txKey.isBlank()) {
                return null;
            }
            Map<String, Object> existing = queryOne(connection,
                    "SELECT tx_id,account_id,balance_after FROM cmv4_bank_ledger WHERE idempotency_key=? LIMIT 1",
                    txKey);
            if (existing == null || existing.isEmpty()) {
                return null;
            }
            if (!accountId.equalsIgnoreCase(string(existing.get("account_id")))) {
                return new TxnResult(false, "IDEMPOTENCY_CONFLICT", "Ключ пополнения уже привязан к другому счёту.", 0L, "");
            }
            return new TxnResult(true, "OK", "Идемпотентный повтор.", longValue(existing.get("balance_after")), string(existing.get("tx_id")));
        }
    }

    private final class OfficialArServiceImpl implements OfficialArService {
        @Override
        public boolean isOfficialAr(ItemStack stack) {
            return CopiMineEconomyCore.this.isOfficialAr(stack);
        }

        @Override
        public int countOfficialAr(Inventory inventory) {
            return CopiMineEconomyCore.this.countOfficialAr(inventory);
        }

        @Override
        public CompletableFuture<Boolean> prepareIssuanceAsync(String issuanceKey, Material material, int amount, String source) {
            if (issuanceKey == null || issuanceKey.isBlank() || material == null || amount <= 0) {
                return CompletableFuture.failedFuture(new IllegalArgumentException("AR issuance amount/material is invalid"));
            }
            Material normalized = material == Material.DEEPSLATE_DIAMOND_ORE
                    ? Material.DEEPSLATE_DIAMOND_ORE : Material.DIAMOND_ORE;
            String serial = arFungibleSerial(normalized);
            return dbFuture("prepare official AR issuance", () -> tx(connection -> {
                Map<String, Object> existing = queryOne(connection,
                        "SELECT serial,material,amount,status FROM cmv8_ar_issuance_intents WHERE idempotency_key=? FOR UPDATE",
                        issuanceKey);
                if (existing != null && !existing.isEmpty()) {
                    if (!serial.equals(string(existing.get("serial")))
                            || !normalized.name().equals(string(existing.get("material")))
                            || longValue(existing.get("amount")) != amount
                            || !"PREPARED".equals(string(existing.get("status")))) {
                        throw new IllegalStateException("AR issuance idempotency conflict");
                    }
                    return true;
                }
                long timestamp = now();
                update(connection,
                        "INSERT INTO cmv8_ar_issuance_intents(idempotency_key,serial,material,amount,status,source,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?)",
                        issuanceKey, serial, normalized.name(), amount, "PREPARED", first(source, "unknown"), timestamp, timestamp);
                update(connection,
                        "INSERT INTO cmv8_ar_assets(serial,denomination,material,signature_version,status,issued_units,issued_at,updated_at) VALUES(?,?,?,?,?,?,?,?) "
                                + "ON CONFLICT(serial) DO UPDATE SET issued_units=cmv8_ar_assets.issued_units+excluded.issued_units,status='ACTIVE',updated_at=excluded.updated_at",
                        serial, 1, normalized.name(), OFFICIAL_AR_SIGNATURE_VERSION, "ACTIVE", amount, timestamp, timestamp);
                return true;
            }));
        }

        @Override
        public ItemStack createPreparedStack(Material material, int amount, String source) {
            return createOfficialArStack(material, amount, "", "", first(source, "prepared-issuance"));
        }

        @Override
        public ItemStack normalizeStack(ItemStack stack) {
            if (stack == null || !isOfficialAr(stack)) {
                return stack;
            }
            return normalizeOfficialArStack(stack, "service-normalize");
        }

        @Override
        public void normalizePlayer(Player player) {
            normalizeOfficialArItems(player);
        }
    }
}
