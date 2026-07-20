package me.copimine.economycore;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
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
import java.util.regex.Pattern;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.bukkit.util.Transformation;

public final class CopiMineEconomyCore extends JavaPlugin implements Listener {
    private static final Pattern SAFE_SCHEMA = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final int PIN_MAX_ATTEMPTS = 5;
    private static final long PIN_LOCK_SECONDS = 900L;
    private static final long PIN_ATTEMPT_WINDOW_SECONDS = 900L;
    private static final int MODEL_ATM_TERMINAL = 12002;
    // Preserve the legacy id for compatibility with existing rows and ledgers.
    private static final String TREASURY_ACCOUNT_ID = "PRESIDENT_BUDGET";
    private static final String TREASURY_ACCOUNT_LABEL = "Казна CopiMine";
    private static final String TREASURY_ACCOUNT_TYPE = "TREASURY";
    private static final Set<Long> DONATION_PACKS = Set.of(50L, 100L, 250L, 500L, 1000L);
    private static final long DONATION_SESSION_TTL_MS = 15L * 60L * 1000L;
    private static final String PENDING_AR_SETTLEMENT_STATUS_PENDING = "PENDING";
    private static final String PENDING_AR_SETTLEMENT_STATUS_DELIVERING = "DELIVERING";
    private static final String PENDING_AR_SETTLEMENT_STATUS_DELIVERED = "DELIVERED";
    private static final String PENDING_AR_SETTLEMENT_TYPE_WITHDRAW_DELIVERY = "WITHDRAW_DELIVERY";
    private static final String PENDING_AR_SETTLEMENT_TYPE_DEPOSIT_RESTORE = "DEPOSIT_RESTORE";

    private DbSettings db;
    private ExecutorService dbExecutor;
    private final Map<UUID, AtmPinSession> atmPinSessions = new ConcurrentHashMap<>();
    private final Set<UUID> atmPinRefreshBypass = ConcurrentHashMap.newKeySet();
    private final Set<BlockKey> activeAtmBlocks = ConcurrentHashMap.newKeySet();
    private final Map<BlockKey, String> atmIdsByBlock = new ConcurrentHashMap<>();
    private NamespacedKey visualEntityTypeKey;
    private NamespacedKey visualKindKey;
    private NamespacedKey visualLinkedIdKey;
    private NamespacedKey visualModelIdKey;

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
        ItemStack createStack(Material material, int amount);
        ItemStack normalizeStack(ItemStack stack);
        boolean removeAmount(Inventory inventory, int amount);
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

    @Override
    public void onEnable() {
        visualEntityTypeKey = new NamespacedKey(this, "visual_entity_type");
        visualKindKey = new NamespacedKey(this, "visual_kind");
        visualLinkedIdKey = new NamespacedKey(this, "visual_linked_id");
        visualModelIdKey = new NamespacedKey(this, "visual_model_id");
        dbExecutor = Executors.newFixedThreadPool(Math.max(2, Math.min(6, Runtime.getRuntime().availableProcessors())), task -> {
            Thread thread = new Thread(task, "copimine-economy-db");
            thread.setDaemon(true);
            return thread;
        });
        try {
            Class.forName("org.postgresql.Driver");
            db = loadDbSettings();
            ensureSchema();
            int interruptedSettlements = quarantineInterruptedPendingArSettlements();
            if (interruptedSettlements > 0) {
                getLogger().warning("Quarantined " + interruptedSettlements + " interrupted pending AR delivery record(s) for manual review.");
            }
            loadAtmCache();
        } catch (Exception error) {
            getLogger().severe("CopiMineEconomyCore PostgreSQL init failed: " + safeError(error));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
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
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                repairProtectedBlockVisuals();
            } catch (Exception error) {
                getLogger().warning("ATM visual repair: " + safeError(error));
            }
        }, 20L);
        Bukkit.getScheduler().runTaskLater(this, () -> Bukkit.getOnlinePlayers().forEach(player -> processPendingArSettlements(player, false)), 40L);
    }

    @Override
    public void onDisable() {
        Bukkit.getServicesManager().unregisterAll(this);
        if (dbExecutor != null) {
            dbExecutor.shutdownNow();
        }
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
        for (Map<String, Object> row : queryList("SELECT id,world,x,y,z FROM ar_atms WHERE active=1")) {
            BlockKey key = blockKey(string(row.get("world")), intValue(row.get("x")), intValue(row.get("y")), intValue(row.get("z")));
            activeAtmBlocks.add(key);
            atmIdsByBlock.put(key, string(row.get("id")));
        }
    }

    private void cacheAtm(String atmId, Block block) {
        BlockKey key = blockKey(block);
        activeAtmBlocks.add(key);
        atmIdsByBlock.put(key, atmId);
    }

    private void evictAtmCache(String atmId) {
        atmIdsByBlock.entrySet().removeIf(entry -> {
            boolean remove = atmId.equals(entry.getValue());
            if (remove) {
                activeAtmBlocks.remove(entry.getKey());
            }
            return remove;
        });
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
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
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
        Bukkit.getScheduler().runTaskLater(this, () -> processPendingArSettlements(event.getPlayer(), true), 20L);
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
        int created = updateCount("INSERT INTO ar_atms(id,world,x,y,z,name,active,created_by,created_at,archived_by,archived_at) VALUES(?,?,?,?,?,'Банкомат',1,?,?, '',0) ON CONFLICT (world,x,y,z) WHERE active=1 DO NOTHING",
                id, block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), player.getName(), t);
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
            int created = updateCount("INSERT INTO ar_atms(id,world,x,y,z,name,active,created_by,created_at,archived_by,archived_at) VALUES(?,?,?,?,?,'Банкомат',1,?,?, '',0) ON CONFLICT (world,x,y,z) WHERE active=1 DO NOTHING",
                    id, worldName, x, y, z, player.getName(), t);
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
        String scope = "TREASURY".equalsIgnoreCase(accountScope) && hasTreasuryAccess(player) ? "TREASURY" : "PERSONAL";
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
            button(holder, inventory, 49, Material.BARRIER, "&7Закрыть", List.of(), "atm:close");
            player.openInventory(inventory);
        }));
    }

    private void openAtmBalancePreview(Player player, String atmId, String accountScope) {
        String scope = "TREASURY".equalsIgnoreCase(accountScope) && hasTreasuryAccess(player) ? "TREASURY" : "PERSONAL";
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
        String scope = "TREASURY".equalsIgnoreCase(accountScope) && hasTreasuryAccess(player) ? "TREASURY" : "PERSONAL";
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
        String scope = "TREASURY".equalsIgnoreCase(accountScope) && hasTreasuryAccess(player) ? "TREASURY" : "PERSONAL";
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
        String scope = "TREASURY".equalsIgnoreCase(accountScope) && hasTreasuryAccess(player) ? "TREASURY" : "PERSONAL";
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
        String scope = "TREASURY".equalsIgnoreCase(session.accountScope()) && hasTreasuryAccess(player) ? "TREASURY" : "PERSONAL";
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
                if (error == null && result != null && result.ok && !"TRANSFER".equals(session.action())) {
                    queuePendingArSettlement(player.getUniqueId(), player.getName(), session.amount(), PENDING_AR_SETTLEMENT_TYPE_WITHDRAW_DELIVERY,
                            "atm=" + session.atmId() + ",scope=" + scope + ",tx=" + first(result.txId, ""));
                }
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
                completeWithdrawOnMainThread(player, session.amount());
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
        String scope = "TREASURY".equalsIgnoreCase(accountScope) && hasTreasuryAccess(player) ? "TREASURY" : "PERSONAL";
        int amount = stack.getAmount();
        ItemStack snapshot = stack.clone();
        player.getInventory().setItemInMainHand(null);
        player.updateInventory();
        String txKey = "atm-hand-" + UUID.randomUUID();
        ("TREASURY".equals(scope)
                ? creditAccountAsync(treasuryAccountId(), player.getUniqueId().toString(), player.getName(), amount, txKey, "TREASURY_DEPOSIT", "atm=" + atmId)
                : bankService.creditAsync(player.getUniqueId(), player.getName(), amount, txKey, "ATM_DEPOSIT", "atm=" + atmId))
                .whenComplete((result, error) -> Bukkit.getScheduler().runTask(this, () -> {
                    if (error != null || result == null || !result.ok) {
                        if (!player.isOnline()) {
                            queuePendingArSettlement(player.getUniqueId(), player.getName(), amount, PENDING_AR_SETTLEMENT_TYPE_DEPOSIT_RESTORE,
                                    "atm=" + atmId + ",scope=" + scope + ",hand=true");
                            return;
                        }
                        player.getInventory().setItemInMainHand(snapshot);
                        player.updateInventory();
                        player.sendMessage(color("&cНе удалось внести AR в банк."));
                        if (error != null) {
                            getLogger().warning("atm deposit hand: " + safeError(error));
                        }
                        openBankAtmAccount(player, atmId, scope);
                        return;
                    }
                    if (!player.isOnline()) {
                        return;
                    }
                    player.sendMessage(color("&aВ банк внесено: &f" + amount + " AR"));
                    openBankAtmAccount(player, atmId, scope);
                }));
        return "&7Вносим AR в банк...";
    }

    private String depositAllArAsync(Player player, String atmId, String accountScope) throws Exception {
        int available = countOfficialAr(player.getInventory());
        if (available <= 0) {
            return "&cВ инвентаре нет официального AR.";
        }
        String scope = "TREASURY".equalsIgnoreCase(accountScope) && hasTreasuryAccess(player) ? "TREASURY" : "PERSONAL";
        ItemStack[] snapshot = cloneInventoryContents(player.getInventory());
        removeOfficialAr(player.getInventory(), available);
        player.updateInventory();
        String txKey = "atm-all-" + UUID.randomUUID();
        ("TREASURY".equals(scope)
                ? creditAccountAsync(treasuryAccountId(), player.getUniqueId().toString(), player.getName(), available, txKey, "TREASURY_DEPOSIT_ALL", "atm=" + atmId)
                : bankService.creditAsync(player.getUniqueId(), player.getName(), available, txKey, "ATM_DEPOSIT_ALL", "atm=" + atmId))
                .whenComplete((result, error) -> Bukkit.getScheduler().runTask(this, () -> {
                    if (error != null || result == null || !result.ok) {
                        if (!player.isOnline()) {
                            queuePendingArSettlement(player.getUniqueId(), player.getName(), available, PENDING_AR_SETTLEMENT_TYPE_DEPOSIT_RESTORE,
                                    "atm=" + atmId + ",scope=" + scope + ",all=true");
                            return;
                        }
                        restoreInventorySnapshot(player, snapshot);
                        player.sendMessage(color("&cНе удалось внести AR в банк."));
                        if (error != null) {
                            getLogger().warning("atm deposit all: " + safeError(error));
                        }
                        openBankAtmAccount(player, atmId, scope);
                        return;
                    }
                    if (!player.isOnline()) {
                        return;
                    }
                    player.sendMessage(color("&aВ банк внесено: &f" + available + " AR"));
                    openBankAtmAccount(player, atmId, scope);
                }));
        return "&7Вносим весь AR в банк...";
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
                    long totalAmount = rows.stream().mapToLong(PendingArSettlement::amount).sum();
                    long capacity = arCapacity(player.getInventory());
                    if (capacity < totalAmount) {
                        if (notifyNoSpace) {
                            player.sendMessage(color("&eЕсть невыданный официальный AR. Освободи место в инвентаре и открой банкомат снова."));
                        }
                        return;
                    }
                    List<String> ids = rows.stream().map(PendingArSettlement::id).toList();
                    dbFuture("pending ar settlements reserve", () -> reservePendingArSettlements(ids))
                            .whenComplete((reserved, reserveError) -> Bukkit.getScheduler().runTask(this, () -> {
                                if (reserveError != null) {
                                    getLogger().warning("pending AR settlements reserve: " + safeError(reserveError));
                                    return;
                                }
                                if (!player.isOnline()) {
                                    dbAsync("pending ar settlements release", () -> releasePendingArSettlements(ids));
                                    return;
                                }
                                if (reserved == null || reserved != ids.size()) {
                                    dbAsync("pending ar settlements release", () -> releasePendingArSettlements(ids));
                                    return;
                                }
                                if (arCapacity(player.getInventory()) < totalAmount || !issueOfficialArAmount(player, totalAmount, "pending-ar-settlement", false)) {
                                    dbAsync("pending ar settlements release", () -> releasePendingArSettlements(ids));
                                    if (notifyNoSpace) {
                                        player.sendMessage(color("&eОсвободи место в инвентаре и открой банкомат снова, чтобы забрать ожидающий AR."));
                                    }
                                    return;
                                }
                                dbAsync("pending ar settlements delivered", () -> markPendingArSettlementsDelivered(ids));
                                player.sendMessage(color("&aВыдан ожидающий официальный AR: &f" + totalAmount + " AR"));
                            }));
                }));
    }

    private List<PendingArSettlement> loadPendingArSettlements(UUID playerUuid) throws Exception {
        return tx(connection -> {
            List<Map<String, Object>> rows = queryList(connection,
                    "SELECT id,player_uuid,player_name,amount,settlement_type,reason FROM cmv4_pending_ar_settlements WHERE player_uuid=? AND status=? ORDER BY created_at ASC",
                    playerUuid.toString(), PENDING_AR_SETTLEMENT_STATUS_PENDING);
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

    private int reservePendingArSettlements(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return tx(connection -> {
            int reserved = 0;
            long updatedAt = now();
            for (String id : ids) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE cmv4_pending_ar_settlements SET status=?,updated_at=? WHERE id=? AND status=?")) {
                    bind(statement, PENDING_AR_SETTLEMENT_STATUS_DELIVERING, updatedAt, id, PENDING_AR_SETTLEMENT_STATUS_PENDING);
                    reserved += statement.executeUpdate();
                }
            }
            return reserved;
        });
    }

    private void markPendingArSettlementsDelivered(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        tx(connection -> {
            long updatedAt = now();
            for (String id : ids) {
                update(connection,
                        "UPDATE cmv4_pending_ar_settlements SET status=?,delivered_at=?,updated_at=? WHERE id=? AND status=?",
                        PENDING_AR_SETTLEMENT_STATUS_DELIVERED, updatedAt, updatedAt, id, PENDING_AR_SETTLEMENT_STATUS_DELIVERING);
            }
            return null;
        });
    }

    private void releasePendingArSettlements(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        tx(connection -> {
            long updatedAt = now();
            for (String id : ids) {
                update(connection,
                        "UPDATE cmv4_pending_ar_settlements SET status=?,updated_at=? WHERE id=? AND status=?",
                        PENDING_AR_SETTLEMENT_STATUS_PENDING, updatedAt, id, PENDING_AR_SETTLEMENT_STATUS_DELIVERING);
            }
            return null;
        });
    }

    private void queuePendingArSettlement(UUID playerUuid, String playerName, long amount, String settlementType, String reason) {
        if (amount <= 0) {
            return;
        }
        dbAsync("queue pending ar settlement", () -> update(
                "INSERT INTO cmv4_pending_ar_settlements(id,player_uuid,player_name,amount,settlement_type,status,reason,created_at,updated_at,delivered_at) VALUES(?,?,?,?,?,?,?,?,?,0)",
                UUID.randomUUID().toString(),
                playerUuid.toString(),
                first(playerName, ""),
                amount,
                first(settlementType, PENDING_AR_SETTLEMENT_TYPE_WITHDRAW_DELIVERY),
                PENDING_AR_SETTLEMENT_STATUS_PENDING,
                first(reason, ""),
                now(),
                now()
        ));
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
        long remaining = amount;
        while (remaining > 0) {
            int stackAmount = (int) Math.min(64L, remaining);
            ItemStack out = createOfficialArStack(stackAmount, player.getUniqueId().toString(), player.getName(), source);
            Map<Integer, ItemStack> left = player.getInventory().addItem(out);
            if (!left.isEmpty()) {
                if (!dropOverflow) {
                    return false;
                }
                left.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
            }
            remaining -= stackAmount;
        }
        player.updateInventory();
        return true;
    }

    private ItemStack[] cloneInventoryContents(Inventory inventory) {
        ItemStack[] contents = inventory.getContents();
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null ? null : contents[i].clone();
        }
        return copy;
    }

    private void restoreInventorySnapshot(Player player, ItemStack[] snapshot) {
        ItemStack[] copy = new ItemStack[snapshot.length];
        for (int i = 0; i < snapshot.length; i++) {
            copy[i] = snapshot[i] == null ? null : snapshot[i].clone();
        }
        player.getInventory().setContents(copy);
        player.updateInventory();
    }

    private void completeWithdrawOnMainThread(Player player, long amount) {
        issueOfficialArAmount(player, amount, "bank-withdraw", true);
    }

    private ItemStack createOfficialArStack(int amount, String ownerUuid, String ownerName, String source) {
        return createOfficialArStack(Material.DIAMOND_ORE, amount, ownerUuid, ownerName, source);
    }

    private ItemStack createOfficialArStack(Material material, int amount, String ownerUuid, String ownerName, String source) {
        Material arMaterial = material == Material.DEEPSLATE_DIAMOND_ORE ? Material.DEEPSLATE_DIAMOND_ORE : Material.DIAMOND_ORE;
        ItemStack stack = new ItemStack(arMaterial, Math.max(1, amount));
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
        stack.setItemMeta(meta);
        return stack;
    }

    private void normalizeOfficialArItems(Player player) {
        if (player == null) {
            return;
        }
        boolean updated = normalizeOfficialArInventory(player.getInventory());
        updated = normalizeOfficialArInventory(player.getEnderChest()) || updated;
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (isOfficialAr(offHand) && needsOfficialArNormalization(offHand)) {
            player.getInventory().setItemInOffHand(createOfficialArStack(offHand.getType(), Math.max(1, offHand.getAmount()), player.getUniqueId().toString(), player.getName(), "join-normalize"));
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
            inventory.setItem(slot, createOfficialArStack(stack.getType(), Math.max(1, stack.getAmount()), "", "", "normalize"));
            updated = true;
        }
        return updated;
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
                || pdc.has(new NamespacedKey("copiminear", "source"), PersistentDataType.STRING);
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
        String kind = stack.getItemMeta().getPersistentDataContainer().get(new NamespacedKey("copiminear", "type"), PersistentDataType.STRING);
        return "certified".equalsIgnoreCase(kind);
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
                "INSERT INTO bank_pin_hashes(minecraft_uuid,site_account_id,pin_hash,pin_sealed,must_change,created_at,updated_at) "
                        + "VALUES(?,?,?,?,0,?,?) "
                        + "ON CONFLICT(minecraft_uuid) DO UPDATE SET pin_hash=excluded.pin_hash,must_change=0,updated_at=excluded.updated_at",
                playerKey, "", pinHash, "", stamp, stamp);
        update(
                "INSERT INTO bank_account_pins(account_id,pin_hash,pin_sealed,must_change,created_at,updated_at,updated_by) "
                        + "VALUES(?,?,?,0,?,?,?) "
                        + "ON CONFLICT(account_id) DO UPDATE SET pin_hash=excluded.pin_hash,must_change=0,updated_at=excluded.updated_at,updated_by=excluded.updated_by",
                bankAccountId(playerKey), pinHash, "", stamp, stamp, "economy-core-pin-sync");
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

    private long now() {
        return System.currentTimeMillis();
    }

    private long nowSec() {
        return Math.max(1L, now() / 1000L);
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
        try {
            return scalarLong("SELECT COUNT(*) FROM president_terms WHERE president_uuid=? AND status='ACTIVE'", player.getUniqueId().toString()) > 0;
        } catch (Exception error) {
            return false;
        }
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
            if (!providedPin.isBlank()) {
                long locked = bankPinLockedSeconds(playerKey);
                if (locked > 0) {
                    return new TxnResult(false, "PIN_LOCKED", "PIN temporarily locked.", 0L, "");
                }
                if (!bankPinSet(playerKey)) {
                    return new TxnResult(false, "PIN_REQUIRED", "Bank PIN is not configured.", 0L, "");
                }
                if (bankPinMustChange(playerKey)) {
                    return new TxnResult(false, "PIN_CHANGE_REQUIRED", "Temporary PIN must be changed first.", 0L, "");
                }
                if (!verifyBankPin(playerKey, providedPin)) {
                    Player online = Bukkit.getPlayer(playerUuid);
                    if (online != null) {
                        recordFailedPinAttempt(online, "economy-artifact-transfer");
                    }
                    return new TxnResult(false, "PIN_INVALID", "PIN is invalid.", 0L, "");
                }
            }
            return tx(connection -> {
                Map<String, Object> fromAccount = ensureBankAccount(connection, playerKey, first(playerName, ""));
                Map<String, Object> toAccount = resolveManagedAccount(connection, toAccountId, toOwnerUuid, toOwnerName);
                String fromId = string(fromAccount.get("account_id"));
                String resolvedToId = string(toAccount.get("account_id"));
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
            String accountId = treasuryAccountId();
            if (accountPinLockedSeconds(accountId) > 0L) {
                return new TxnResult(false, "PIN_LOCKED", "PIN казны временно заблокирован.", 0L, "");
            }
            if (!accountPinSet(accountId)) {
                return new TxnResult(false, "PIN_REQUIRED", "Для казны PIN ещё не задан.", 0L, "");
            }
            if (accountPinMustChange(accountId)) {
                return new TxnResult(false, "PIN_CHANGE_REQUIRED", "PIN казны нужно обновить на сайте.", 0L, "");
            }
            if (!verifyAccountPinHash(accountId, pin)) {
                recordFailedAccountPinAttempt(accountId, "economy-treasury-withdraw");
                return new TxnResult(false, "PIN_INVALID", "Неверный PIN казны.", 0L, "");
            }
            return tx(connection -> {
                ensureTreasuryAccount(connection);
                String txKey = first(idempotencyKey, "treasury-charge-" + UUID.randomUUID());
                TxnResult replay = replayArtifactStyleTxn(connection, txKey, accountId, "", -amount, first(details, ""));
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
                return new TxnResult(true, "OK", "Операция по казне подтверждена.", after, txId);
            });
        });
    }

    private CompletableFuture<TxnResult> transferFromTreasuryWithPinAsync(Player actor, UUID targetUuid, String targetName, long amount, String pin, String idempotencyKey, String action, String details) {
        return dbFuture("treasury transfer", () -> {
            if (actor == null || targetUuid == null || amount <= 0L) {
                return new TxnResult(false, "INVALID_REQUEST", "Некорректный перевод.", 0L, "");
            }
            String accountId = treasuryAccountId();
            if (accountPinLockedSeconds(accountId) > 0L) {
                return new TxnResult(false, "PIN_LOCKED", "PIN казны временно заблокирован.", 0L, "");
            }
            if (!accountPinSet(accountId)) {
                return new TxnResult(false, "PIN_REQUIRED", "Для казны PIN ещё не задан.", 0L, "");
            }
            if (accountPinMustChange(accountId)) {
                return new TxnResult(false, "PIN_CHANGE_REQUIRED", "PIN казны нужно обновить на сайте.", 0L, "");
            }
            if (!verifyAccountPinHash(accountId, pin)) {
                recordFailedAccountPinAttempt(accountId, "economy-treasury-transfer");
                return new TxnResult(false, "PIN_INVALID", "Неверный PIN казны.", 0L, "");
            }
            return tx(connection -> {
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
        Player player = Bukkit.getPlayer(UUID.fromString(targetUuid));
        return player != null ? player.getName() : targetUuid;
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

    private void ensureSchema() throws Exception {
        int archivedDuplicateAtms = tx(connection -> {
            update(connection, "CREATE TABLE IF NOT EXISTS cmv4_bank_accounts(account_id TEXT PRIMARY KEY,owner_uuid TEXT NOT NULL,owner_name TEXT NOT NULL DEFAULT '',account_type TEXT NOT NULL DEFAULT 'PLAYER',currency TEXT NOT NULL DEFAULT 'AR',balance BIGINT NOT NULL DEFAULT 0 CHECK(balance>=0),status TEXT NOT NULL DEFAULT 'ACTIVE',version BIGINT NOT NULL DEFAULT 0,created_at BIGINT NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL DEFAULT 0)");
            update(connection, "CREATE TABLE IF NOT EXISTS cmv4_bank_ledger(tx_id TEXT PRIMARY KEY,account_id TEXT NOT NULL,counterparty_account_id TEXT NOT NULL DEFAULT '',player_uuid TEXT NOT NULL DEFAULT '',tx_type TEXT NOT NULL,amount BIGINT NOT NULL CHECK(amount>=0),balance_after BIGINT NOT NULL DEFAULT 0,idempotency_key TEXT NOT NULL DEFAULT '',status TEXT NOT NULL DEFAULT 'COMMITTED',created_at BIGINT NOT NULL,actor TEXT NOT NULL DEFAULT '',details TEXT NOT NULL DEFAULT '')");
            update(connection, "CREATE TABLE IF NOT EXISTS cmv4_bank_transfers(tx_id TEXT PRIMARY KEY,from_account_id TEXT NOT NULL,to_account_id TEXT NOT NULL,amount BIGINT NOT NULL CHECK(amount>0),currency TEXT NOT NULL DEFAULT 'AR',status TEXT NOT NULL DEFAULT 'COMMITTED',idempotency_key TEXT NOT NULL DEFAULT '',created_at BIGINT NOT NULL,actor TEXT NOT NULL DEFAULT '',details TEXT NOT NULL DEFAULT '')");
            update(connection, "CREATE TABLE IF NOT EXISTS bank_pin_hashes(minecraft_uuid TEXT PRIMARY KEY,site_account_id TEXT NOT NULL DEFAULT '',pin_hash TEXT NOT NULL,must_change INTEGER NOT NULL DEFAULT 0,created_at BIGINT NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL DEFAULT 0)");
            update(connection, "CREATE TABLE IF NOT EXISTS bank_account_pins(account_id TEXT PRIMARY KEY,pin_hash TEXT NOT NULL,pin_sealed TEXT NOT NULL DEFAULT '',must_change INTEGER NOT NULL DEFAULT 0,created_at BIGINT NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL DEFAULT 0,updated_by TEXT NOT NULL DEFAULT '')");
            update(connection, "CREATE TABLE IF NOT EXISTS ar_atms(id TEXT PRIMARY KEY,world TEXT NOT NULL,x INTEGER NOT NULL,y INTEGER NOT NULL,z INTEGER NOT NULL,name TEXT NOT NULL DEFAULT 'Банкомат',active INTEGER NOT NULL DEFAULT 1,created_by TEXT NOT NULL DEFAULT '',created_at BIGINT NOT NULL DEFAULT 0,archived_by TEXT NOT NULL DEFAULT '',archived_at BIGINT NOT NULL DEFAULT 0)");
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
            update(connection, "CREATE TABLE IF NOT EXISTS cmv4_pending_ar_settlements(id TEXT PRIMARY KEY,player_uuid TEXT NOT NULL,player_name TEXT NOT NULL DEFAULT '',amount BIGINT NOT NULL DEFAULT 0 CHECK(amount>0),settlement_type TEXT NOT NULL DEFAULT '',status TEXT NOT NULL DEFAULT 'PENDING',reason TEXT NOT NULL DEFAULT '',created_at BIGINT NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL DEFAULT 0,delivered_at BIGINT NOT NULL DEFAULT 0)");
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
                "UPDATE cmv4_pending_ar_settlements SET status='DELIVERY_REVIEW',reason=CASE WHEN reason='' THEN 'interrupted_after_reservation' ELSE reason || ';interrupted_after_reservation' END,updated_at=? WHERE status='DELIVERING'",
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
        Connection connection = DriverManager.getConnection(db.jdbcUrl(), db.user(), db.password());
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS " + db.schemaIdent());
            statement.execute("SET search_path TO " + db.schemaIdent());
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
            try {
                body.run();
            } catch (Exception error) {
                getLogger().warning(label + ": " + safeError(error));
            }
            return;
        }
        dbExecutor.execute(() -> {
            try {
                body.run();
            } catch (Exception error) {
                getLogger().warning(label + ": " + safeError(error));
            }
        });
    }

    private <T> CompletableFuture<T> dbFuture(String label, SqlSupplier<T> body) {
        if (dbExecutor == null || dbExecutor.isShutdown()) {
            try {
                return CompletableFuture.completedFuture(body.get());
            } catch (Exception error) {
                CompletableFuture<T> failed = new CompletableFuture<>();
                failed.completeExceptionally(error);
                return failed;
            }
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return body.get();
            } catch (Exception error) {
                throw new CompletionException(new IllegalStateException(label + ": " + safeError(error), error));
            }
        }, dbExecutor);
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

    private record DbSettings(String host, int port, String database, String user, String password, String schema, Path envFile) {
        String jdbcUrl() {
            return "jdbc:postgresql://" + host + ":" + port + "/" + database;
        }

        String schemaIdent() {
            return "\"" + schema.replace("\"", "\"\"") + "\"";
        }
    }

    private record AtmVisualRepairBatch(String worldName, int chunkX, int chunkZ, List<Map<String, Object>> rows) {}

    private record AtmPinSession(String atmId, String accountScope, String action, int amount, String pin, String targetUuid, String targetName) {}

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
                LIMIT 1
                """);
             PreparedStatement instances = connection.prepareStatement("""
                SELECT 1
                FROM artifact_item_instances
                WHERE owner_uuid=?
                  AND item_id=?
                  AND status IN ('ACTIVE','DELIVERING','PENDING_DELIVERY')
                LIMIT 1
                """)) {
            claims.setString(1, playerUuid);
            claims.setString(2, itemId);
            try (ResultSet rs = claims.executeQuery()) {
                if (rs.next()) {
                    return true;
                }
            }
            instances.setString(1, playerUuid);
            instances.setString(2, itemId);
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
            try {
                PinStatus pin = pinStatus(playerUuid);
                long balance = playerUuid == null ? 0L : bankService.balance(playerUuid, "");
                return new Health(true, true, pin.configured && !pin.mustChange && pin.lockedSeconds <= 0, balance, first(context, "health"), "");
            } catch (Exception error) {
                return new Health(false, true, false, 0L, first(context, "health"), safeError(error));
            }
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
                long locked = bankPinLockedSeconds(fromUuid.toString());
                if (locked > 0) {
                    return new TxnResult(false, "PIN_LOCKED", "PIN временно заблокирован.", 0L, "");
                }
                if (!bankPinSet(fromUuid.toString())) {
                    return new TxnResult(false, "PIN_REQUIRED", "Банковский PIN ещё не задан.", 0L, "");
                }
                if (bankPinMustChange(fromUuid.toString())) {
                    return new TxnResult(false, "PIN_CHANGE_REQUIRED", "Временный PIN нужно заменить на сайте.", 0L, "");
                }
                if (!verifyBankPin(fromUuid.toString(), pin)) {
                    Player online = Bukkit.getPlayer(fromUuid);
                    if (online != null) {
                        recordFailedPinAttempt(online, "economy-transfer");
                    }
                    return new TxnResult(false, "PIN_INVALID", "Неверный банковский PIN.", 0L, "");
                }
                return tx(connection -> {
                    Map<String, Object> fromAccount = ensureBankAccount(connection, fromUuid.toString(), first(fromName, ""));
                    Map<String, Object> toAccount = ensureBankAccount(connection, toUuid.toString(), first(toName, ""));
                    String fromId = string(fromAccount.get("account_id"));
                    String toId = string(toAccount.get("account_id"));
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
                if (signedAmount < 0) {
                    long locked = bankPinLockedSeconds(uuid);
                    if (locked > 0) {
                        return new TxnResult(false, "PIN_LOCKED", "PIN temporarily locked.", 0L, "");
                    }
                    if (!bankPinSet(uuid)) {
                        return new TxnResult(false, "PIN_REQUIRED", "Bank PIN is not configured.", 0L, "");
                    }
                    if (bankPinMustChange(uuid)) {
                        return new TxnResult(false, "PIN_CHANGE_REQUIRED", "Temporary PIN must be changed first.", 0L, "");
                    }
                    if (!verifyBankPin(uuid, pin)) {
                        Player online = Bukkit.getPlayer(playerUuid);
                        if (online != null) {
                            recordFailedPinAttempt(online, "economy-bridge");
                        }
                        return new TxnResult(false, "PIN_INVALID", "PIN is invalid.", 0L, "");
                    }
                }
                return tx(connection -> {
                    ensureBankAccount(connection, uuid, first(playerName, ""));
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
                    return new TxnResult(true, "OK", "Committed.", after, txId);
                });
            } catch (Exception error) {
                return new TxnResult(false, "BANK_ERROR", safeError(error), 0L, "");
            }
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
        public ItemStack createStack(Material material, int amount) {
            return createOfficialArStack(material, amount, "", "", "service");
        }

        @Override
        public ItemStack normalizeStack(ItemStack stack) {
            if (stack == null || !isOfficialAr(stack)) {
                return stack;
            }
            return createOfficialArStack(stack.getType(), Math.max(1, stack.getAmount()), "", "", "normalize");
        }

        @Override
        public boolean removeAmount(Inventory inventory, int amount) {
            if (inventory == null || amount <= 0) {
                return false;
            }
            if (countOfficialAr(inventory) < amount) {
                return false;
            }
            removeOfficialAr(inventory, amount);
            return true;
        }

        @Override
        public void normalizePlayer(Player player) {
            normalizeOfficialArItems(player);
        }
    }
}
