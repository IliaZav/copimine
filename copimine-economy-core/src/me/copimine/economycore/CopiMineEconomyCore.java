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
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
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
import java.util.ArrayList;
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

    private DbSettings db;
    private ExecutorService dbExecutor;
    private final Map<UUID, AtmPinSession> atmPinSessions = new ConcurrentHashMap<>();
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
    private final DonationBalanceService donationBalanceService = new DonationBalanceServiceImpl();
    private final DonationPurchaseService donationPurchaseService = new DonationPurchaseServiceImpl();
    private final EconomyService economyService = new EconomyServiceImpl();
    private final ArtifactsBridge artifactsBridge = new ArtifactsBridgeImpl();

    public interface EconomyService {
        BankService bankService();
        PinService pinService();
        AtmService atmService();
        LedgerService ledgerService();
        DonationBalanceService donationBalanceService();
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

    public interface DonationBalanceService {
        long balance(UUID playerUuid, String playerName);
        CompletableFuture<Long> balanceAsync(UUID playerUuid, String playerName);
        TxnResult add(UUID playerUuid, String playerName, long amount, String reason, String actor, String source, String idempotencyKey);
        CompletableFuture<TxnResult> addAsync(UUID playerUuid, String playerName, long amount, String reason, String actor, String source, String idempotencyKey);
        TxnResult subtract(UUID playerUuid, String playerName, long amount, String reason, String actor, String source, String idempotencyKey);
        CompletableFuture<TxnResult> subtractAsync(UUID playerUuid, String playerName, long amount, String reason, String actor, String source, String idempotencyKey);
        CompletableFuture<List<Map<String, Object>>> ledgerAsync(UUID playerUuid, int limit);
    }

    public interface DonationPurchaseService {
        CompletableFuture<Map<String, Object>> createTestPurchaseAsync(UUID playerUuid, String playerName, String itemId, long price, String actor);
        CompletableFuture<Map<String, Object>> createClaimAsync(UUID playerUuid, String itemId, long amount, String actor, String purchaseId);
        CompletableFuture<List<Map<String, Object>>> getUnclaimedItemsAsync(UUID playerUuid, int limit);
        CompletableFuture<Boolean> reserveClaimAsync(UUID playerUuid, String claimId);
        CompletableFuture<Boolean> markClaimDeliveringAsync(UUID playerUuid, String claimId);
        CompletableFuture<Boolean> completeClaimAsync(UUID playerUuid, String claimId);
        CompletableFuture<Boolean> markClaimDeliveryReviewAsync(UUID playerUuid, String claimId);
        CompletableFuture<Boolean> releaseClaimAsync(UUID playerUuid, String claimId);
    }

    public interface ArtifactsBridge {
        PinStatus pinStatus(UUID playerUuid);
        TxnResult charge(UUID playerUuid, String playerName, long amount, String pin, String idempotencyKey, String action, String details);
        TxnResult refund(UUID playerUuid, String playerName, long amount, String idempotencyKey, String action, String details);
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
        getServer().getServicesManager().register(DonationBalanceService.class, donationBalanceService, this, ServicePriority.Normal);
        getServer().getServicesManager().register(DonationPurchaseService.class, donationPurchaseService, this, ServicePriority.Normal);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                repairProtectedBlockVisuals();
            } catch (Exception error) {
                getLogger().warning("ATM visual repair: " + safeError(error));
            }
        }, 20L);
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

    public ArtifactsBridge artifactsBridge() {
        return artifactsBridge;
    }

    public DonationBalanceService donationBalanceService() {
        return donationBalanceService;
    }

    public DonationPurchaseService donationPurchaseService() {
        return donationPurchaseService;
    }

    public void openAdminEconomyHub(Player player) throws Exception {
        if (!hasEconomyAdmin(player)) {
            player.sendMessage(color("&cДоступно только администрации экономики."));
            return;
        }
        MenuHolder holder = new MenuHolder("economy:hub", "");
        Inventory inventory = holder.create(27, color("&b&lЭкономика CopiMine"));
        button(holder, inventory, 11, Material.GOLD_BLOCK, "&bБанкоматы", List.of("&7Реестр банкоматов и создание по блоку."), "economy:atms");
        button(holder, inventory, 13, Material.ENDER_CHEST, "&fСводка банка", List.of("&7Банк, PIN, переводы и AR."), "economy:summary");
        button(holder, inventory, 15, Material.ARROW, "&aОбновить", List.of("&7Открыть список банкоматов."), "economy:atms");
        player.openInventory(inventory);
    }

    public void openAtmDirectory(Player player) throws Exception {
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
        return createBankAtmFromTargetAsync(player);
    }

    public String archiveAtm(Player actor, String atmId) throws Exception {
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
        event.getPlayer().sendMessage(color("&cЭтот банкомат сначала нужно снять через меню экономики."));
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
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof MenuHolder menu && "atm:pin".equals(menu.id())) {
            atmPinSessions.remove(event.getPlayer().getUniqueId());
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
            player.sendMessage(color("&cНе удалось выполнить действие."));
            getLogger().warning("economy action " + action + ": " + safeError(error));
        }
    }

    private void handleMenuAction(Player player, MenuHolder menu, String action) throws Exception {
        if (action.equals("economy:atms")) {
            openBankAtms(player);
            return;
        }
        if (action.equals("economy:summary")) {
            openEconomySummary(player);
            return;
        }
        if (action.equals("atm:create-target")) {
            player.sendMessage(color(createBankAtmFromTargetAsync(player)));
            return;
        }
        if (action.startsWith("atm:open:")) {
            openBankAtm(player, action.substring("atm:open:".length()));
            return;
        }
        if (action.startsWith("atm:delete:")) {
            player.sendMessage(color(archiveBankAtmAsync(player, action.substring("atm:delete:".length()))));
            return;
        }
        if (action.startsWith("atm:deposit-hand:")) {
            player.sendMessage(color(depositArFromHandAsync(player, action.substring("atm:deposit-hand:".length()))));
            return;
        }
        if (action.startsWith("atm:deposit-all:")) {
            player.sendMessage(color(depositAllArAsync(player, action.substring("atm:deposit-all:".length()))));
            return;
        }
        if (action.startsWith("atm:withdraw:")) {
            String[] parts = action.split(":");
            openAtmPinPad(player, parts[2], "WITHDRAW", parseInt(parts[3], 0), "", "", "");
            return;
        }
        if (action.startsWith("atm:targets:")) {
            openBankTransferTargets(player, action.substring("atm:targets:".length()));
            return;
        }
        if (action.startsWith("atm:target:")) {
            String[] parts = action.split(":");
            openBankTransferAmounts(player, parts[2], parts[3]);
            return;
        }
        if (action.startsWith("atm:transfer:")) {
            String[] parts = action.split(":");
            openAtmPinPad(player, parts[2], "TRANSFER", parseInt(parts[4], 0), "", parts[3], bankTargetName(parts[3]));
            return;
        }
        if (action.startsWith("pin:")) {
            handleAtmPinAction(player, action);
            return;
        }
        if (action.startsWith("nav:")) {
            String target = action.substring("nav:".length());
            if (target.equals("hub")) {
                openAdminEconomyHub(player);
            } else if (target.equals("atms")) {
                openBankAtms(player);
            }
        }
    }

    private void openEconomySummary(Player player) throws Exception {
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
        dbFuture("open atm directory", () -> queryList("SELECT id,name,world,x,y,z,created_at FROM ar_atms WHERE active=1 ORDER BY created_at DESC LIMIT 28"))
                .whenComplete((rows, error) -> Bukkit.getScheduler().runTask(this, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (error != null) {
                        player.sendMessage(color("&cНе удалось открыть список банкоматов."));
                        getLogger().warning("open atm directory: " + safeError(error));
                        return;
                    }
                    MenuHolder holder = new MenuHolder("economy:atms", "");
                    Inventory inventory = holder.create(54, color("&b&lБанкоматы CopiMine"));
                    button(holder, inventory, 10, Material.GOLD_BLOCK, "&aСоздать по блоку", List.of("&7Создаёт банкомат на блоке, куда ты смотришь."), "atm:create-target");
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
        Block block = player.getTargetBlockExact(6);
        if (block == null) {
            return "&cСначала посмотри на блок банкомата.";
        }
        if (isAtmBlock(block)) {
            return "&eНа этом блоке уже есть банкомат.";
        }
        String id = "atm_" + UUID.randomUUID().toString().replace("-", "");
        long t = now();
        update("INSERT INTO ar_atms(id,world,x,y,z,name,active,created_by,created_at,archived_by,archived_at) VALUES(?,?,?,?,?,'Банкомат',1,?,?, '',0)",
                id, block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), player.getName(), t);
        cacheAtm(id, block);
        spawnOrReplaceProtectedBlockVisual(block.getLocation(), "ATM", id, Material.PAPER, MODEL_ATM_TERMINAL, "atm_terminal");
        pluginEvent("economy_core", "atm_created", player.getName(), id, "world=" + block.getWorld().getName());
        return "&aБанкомат создан.";
    }

    private String createBankAtmFromTargetAsync(Player player) throws Exception {
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
            update("INSERT INTO ar_atms(id,world,x,y,z,name,active,created_by,created_at,archived_by,archived_at) VALUES(?,?,?,?,?,'Банкомат',1,?,?, '',0)",
                    id, worldName, x, y, z, player.getName(), t);
            pluginEvent("economy_core", "atm_created", player.getName(), id, "world=" + worldName);
            return id;
        }).whenComplete((createdId, error) -> Bukkit.getScheduler().runTask(this, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (error != null) {
                player.sendMessage(color("&cНе удалось создать банкомат."));
                getLogger().warning("create atm: " + safeError(error));
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
                    List.of("&7Баланс: &f" + payload.get("balance") + " AR"), "");
            button(holder, inventory, 10, Material.DIAMOND_ORE, "&aВнести предмет из руки", List.of("&7Берёт официальный AR из основной руки.", "&7PIN не требуется."), "atm:deposit-hand:" + atmId);
            button(holder, inventory, 12, Material.CHEST, "&aВнести весь AR", List.of("&7Берёт весь официальный AR из инвентаря.", "&7PIN не требуется."), "atm:deposit-all:" + atmId);
            button(holder, inventory, 14, Material.PLAYER_HEAD, "&bПеревести игроку", List.of("&7Банковский перевод с проверкой PIN."), "atm:targets:" + atmId);
            button(holder, inventory, 28, Material.EMERALD, "&eСнять 1 AR", List.of("&7Потребуется PIN банка."), "atm:withdraw:" + atmId + ":1");
            button(holder, inventory, 30, Material.EMERALD_BLOCK, "&eСнять 16 AR", List.of("&7Потребуется PIN банка."), "atm:withdraw:" + atmId + ":16");
            button(holder, inventory, 32, Material.DIAMOND_ORE, "&eСнять 64 AR", List.of("&7Потребуется PIN банка."), "atm:withdraw:" + atmId + ":64");
            button(holder, inventory, 49, Material.ARROW, "&aК банкоматам", List.of(), "nav:atms");
            player.openInventory(inventory);
        }));
    }

    private void openBankTransferTargets(Player player, String atmId) throws Exception {
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

    private void openBankTransferAmounts(Player player, String atmId, String targetUuid) throws Exception {
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

    private void openAtmPinPad(Player player, String atmId, String action, int amount, String pin, String targetUuid, String targetName) {
        String masked = pin.isBlank() ? "• • • •" : pin.chars().mapToObj(i -> "•").reduce((a, b) -> a + " " + b).orElse("•");
        MenuHolder holder = new MenuHolder("atm:pin", atmId);
        Inventory inventory = holder.create(45, color("&e&lВведите PIN"));
        holder.data.put("atm_id", atmId);
        holder.data.put("action", action);
        holder.data.put("amount", String.valueOf(amount));
        holder.data.put("target_uuid", first(targetUuid, ""));
        holder.data.put("target_name", first(targetName, ""));
        atmPinSessions.put(player.getUniqueId(), new AtmPinSession(atmId, action, amount, pin, targetUuid, targetName));
        button(holder, inventory, 13, Material.PAPER, "&fВведите PIN",
                targetUuid.isBlank()
                        ? List.of("&7Код: &f" + masked)
                        : List.of("&7Кому: &f" + first(targetName, targetUuid), "&7Код: &f" + masked),
                "");
        int slot = 19;
        for (String digit : List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")) {
            button(holder, inventory, slot++, Material.LIGHT_BLUE_STAINED_GLASS_PANE, "&f" + digit, List.of(), "pin:digit:" + digit);
            if (slot == 22) slot = 28;
        }
        button(holder, inventory, 23, Material.BARRIER, "&cCancel", List.of(), "pin:cancel");
        button(holder, inventory, 32, Material.ORANGE_WOOL, "&eClear", List.of(), "pin:clear");
        button(holder, inventory, 41, Material.LIME_WOOL, "&aEnter", List.of("&7Подтвердить банковскую операцию."), "pin:confirm");
        player.openInventory(inventory);
    }

    private void handleAtmPinAction(Player player, String action) throws Exception {
        AtmPinSession session = atmPinSessions.get(player.getUniqueId());
        if (session == null) {
            player.closeInventory();
            return;
        }
        String pin = first(session.pin(), "");
        if (action.startsWith("pin:digit:")) {
            if (pin.length() < 8) {
                pin += action.substring("pin:digit:".length());
            }
            openAtmPinPad(player, session.atmId(), session.action(), session.amount(), pin, session.targetUuid(), session.targetName());
            return;
        }
        if (action.equals("pin:clear")) {
            openAtmPinPad(player, session.atmId(), session.action(), session.amount(), "", session.targetUuid(), session.targetName());
            return;
        }
        if (action.equals("pin:cancel")) {
            atmPinSessions.remove(player.getUniqueId());
            openBankAtm(player, session.atmId());
            return;
        }
        if (!action.equals("pin:confirm")) {
            return;
        }
        if (!pin.matches("\\d{4,8}")) {
            player.sendMessage(color("&cВведите PIN полностью: от 4 до 8 цифр."));
            openAtmPinPad(player, session.atmId(), session.action(), session.amount(), pin, session.targetUuid(), session.targetName());
            return;
        }
        TxnResult result;
        if ("TRANSFER".equals(session.action())) {
            result = bankService.transferWithPin(player.getUniqueId(), player.getName(), UUID.fromString(session.targetUuid()), first(session.targetName(), bankTargetName(session.targetUuid())), session.amount(), pin,
                    "atm-transfer-" + UUID.randomUUID(), "ATM_TRANSFER", "atm=" + session.atmId());
        } else {
            result = applyWithdrawWithPin(player, session.atmId(), session.amount(), pin);
        }
        if (!result.ok) {
            player.sendMessage(color("&c" + first(result.message, "Операция отклонена.")));
            openAtmPinPad(player, session.atmId(), session.action(), session.amount(), pin, session.targetUuid(), session.targetName());
            return;
        }
        atmPinSessions.remove(player.getUniqueId());
        player.sendMessage(color("&aОперация выполнена."));
        openBankAtm(player, session.atmId());
    }

    private String depositArFromHand(Player player, String atmId) throws Exception {
        ItemStack stack = player.getInventory().getItemInMainHand();
        if (!isOfficialAr(stack)) {
            return "&cВ основной руке нет официального AR.";
        }
        int amount = stack.getAmount();
        player.getInventory().setItemInMainHand(null);
        TxnResult result = bankService.credit(player.getUniqueId(), player.getName(), amount, "atm-hand-" + UUID.randomUUID(), "ATM_DEPOSIT", "atm=" + atmId);
        if (!result.ok) {
            player.getInventory().setItemInMainHand(stack);
            return "&cНе удалось внести AR в банк.";
        }
        openBankAtm(player, atmId);
        return "&aВ банк внесено: &f" + amount + " AR";
    }

    private String depositArFromHandAsync(Player player, String atmId) throws Exception {
        ItemStack stack = player.getInventory().getItemInMainHand();
        if (!isOfficialAr(stack)) {
            return "&cВ основной руке нет официального AR.";
        }
        int amount = stack.getAmount();
        ItemStack snapshot = stack.clone();
        player.getInventory().setItemInMainHand(null);
        player.updateInventory();
        String txKey = "atm-hand-" + UUID.randomUUID();
        bankService.creditAsync(player.getUniqueId(), player.getName(), amount, txKey, "ATM_DEPOSIT", "atm=" + atmId)
                .whenComplete((result, error) -> Bukkit.getScheduler().runTask(this, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (error != null || result == null || !result.ok) {
                        player.getInventory().setItemInMainHand(snapshot);
                        player.updateInventory();
                        player.sendMessage(color("&cНе удалось внести AR в банк."));
                        if (error != null) {
                            getLogger().warning("atm deposit hand: " + safeError(error));
                        }
                        openBankAtm(player, atmId);
                        return;
                    }
                    player.sendMessage(color("&aВ банк внесено: &f" + amount + " AR"));
                    openBankAtm(player, atmId);
                }));
        return "&7Вносим AR в банк...";
    }

    private String depositAllAr(Player player, String atmId) throws Exception {
        int available = countOfficialAr(player.getInventory());
        if (available <= 0) {
            return "&cВ инвентаре нет официального AR.";
        }
        TxnResult result = bankService.credit(player.getUniqueId(), player.getName(), available, "atm-all-" + UUID.randomUUID(), "ATM_DEPOSIT_ALL", "atm=" + atmId);
        if (!result.ok) {
            return "&cНе удалось внести AR в банк.";
        }
        removeOfficialAr(player.getInventory(), available);
        openBankAtm(player, atmId);
        return "&aВ банк внесено: &f" + available + " AR";
    }

    private String depositAllArAsync(Player player, String atmId) throws Exception {
        int available = countOfficialAr(player.getInventory());
        if (available <= 0) {
            return "&cВ инвентаре нет официального AR.";
        }
        ItemStack[] snapshot = cloneInventoryContents(player.getInventory());
        removeOfficialAr(player.getInventory(), available);
        player.updateInventory();
        String txKey = "atm-all-" + UUID.randomUUID();
        bankService.creditAsync(player.getUniqueId(), player.getName(), available, txKey, "ATM_DEPOSIT_ALL", "atm=" + atmId)
                .whenComplete((result, error) -> Bukkit.getScheduler().runTask(this, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (error != null || result == null || !result.ok) {
                        restoreInventorySnapshot(player, snapshot);
                        player.sendMessage(color("&cНе удалось внести AR в банк."));
                        if (error != null) {
                            getLogger().warning("atm deposit all: " + safeError(error));
                        }
                        openBankAtm(player, atmId);
                        return;
                    }
                    player.sendMessage(color("&aВ банк внесено: &f" + available + " AR"));
                    openBankAtm(player, atmId);
                }));
        return "&7Вносим весь AR в банк...";
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

    private TxnResult applyWithdrawWithPin(Player player, String atmId, long amount, String pin) throws Exception {
        TxnResult result = bankService.charge(player.getUniqueId(), player.getName(), amount, pin, "atm-withdraw-" + UUID.randomUUID(), "ATM_WITHDRAW", "atm=" + atmId);
        if (!result.ok) {
            return result;
        }
        ItemStack out = createOfficialArStack((int) amount, player.getUniqueId().toString(), player.getName(), "bank-withdraw");
        Map<Integer, ItemStack> left = player.getInventory().addItem(out);
        if (!left.isEmpty()) {
            left.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
        }
        return result;
    }

    private ItemStack createOfficialArStack(int amount, String ownerUuid, String ownerName, String source) {
        ItemStack stack = new ItemStack(Material.DIAMOND_ORE, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setDisplayName(color("&b&lAR &8| &fОфициальная руда"));
        meta.setLore(List.of(color("&7Владелец: &f" + first(ownerName, "неизвестно")), color("&7Источник: &f" + first(source, "bank"))));
        meta.addItemFlags(ItemFlag.values());
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(new NamespacedKey("copiminear", "type"), PersistentDataType.STRING, "certified");
        pdc.set(new NamespacedKey("copiminear", "owner_uuid"), PersistentDataType.STRING, first(ownerUuid, ""));
        pdc.set(new NamespacedKey("copiminear", "owner_name"), PersistentDataType.STRING, first(ownerName, ""));
        pdc.set(new NamespacedKey("copiminear", "source"), PersistentDataType.STRING, first(source, ""));
        stack.setItemMeta(meta);
        return stack;
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
        for (ItemStack stack : inventory.getContents()) {
            if (stack == null || !isOfficialAr(stack)) {
                continue;
            }
            int take = Math.min(left, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            if (stack.getAmount() <= 0) {
                inventory.remove(stack);
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
            for (String entry : chunks) {
                String[] parts = entry.split(":");
                if (parts.length != 3) {
                    continue;
                }
                List<Map<String, Object>> rows = loadAtmVisualRepairRows(parts[0], parseInt(parts[1], 0), parseInt(parts[2], 0));
                if (rows.isEmpty()) {
                    continue;
                }
                int chunkX = parseInt(parts[1], 0);
                int chunkZ = parseInt(parts[2], 0);
                Bukkit.getScheduler().runTask(this, () -> applyAtmVisualRepairs(parts[0], chunkX, chunkZ, rows));
            }
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
        } catch (Exception ignored) {
        }
        return null;
    }

    private Map<String, Object> fetchProtectedBlockVisualRow(String kind, String linkedId) throws Exception {
        return queryOne("SELECT entity_uuid,world,x,y,z FROM protected_block_visuals WHERE kind=? AND linked_id=? AND active=1 ORDER BY updated_at DESC LIMIT 1", kind, linkedId);
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

    private boolean bankPinSet(String uuid) throws Exception {
        return scalarLong("SELECT COUNT(*) FROM bank_pin_hashes WHERE minecraft_uuid=? AND COALESCE(pin_hash,'')<>''", uuid) > 0;
    }

    private boolean bankPinMustChange(String uuid) throws Exception {
        return scalarLong("SELECT COALESCE(must_change,0) FROM bank_pin_hashes WHERE minecraft_uuid=? LIMIT 1", uuid) > 0;
    }

    private long bankPinLockedSeconds(String uuid) throws Exception {
        long until = scalarLong("SELECT COALESCE(locked_until,0) FROM account_lockouts WHERE account_id=?", bankPinLockoutKey(uuid));
        return Math.max(0L, until - nowSec());
    }

    private boolean verifyBankPin(String uuid, String pin) throws Exception {
        if (pin == null || !pin.matches("\\d{4,8}")) {
            return false;
        }
        List<Map<String, Object>> rows = queryList("SELECT pin_hash FROM bank_pin_hashes WHERE minecraft_uuid=? LIMIT 1", uuid);
        if (rows.isEmpty()) {
            return false;
        }
        boolean ok = verifyPinHash(string(rows.getFirst().get("pin_hash")), pin);
        if (ok) {
            update("DELETE FROM account_lockouts WHERE account_id=?", bankPinLockoutKey(uuid));
        }
        return ok;
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
        byte[] salt = parts[2].getBytes(StandardCharsets.UTF_8);
        String expected = parts[3].toLowerCase(Locale.ROOT);
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, iterations, Math.max(128, expected.length() * 4));
        byte[] got = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        return MessageDigest.isEqual(hex(got).getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
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

    private String bankPinLockoutKey(String uuid) {
        return "bank-pin:" + uuid;
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

    private void ensureBankAccount(String uuid, String name) throws Exception {
        tx(connection -> {
            ensureBankAccount(connection, uuid, name);
            return null;
        });
    }

    private Map<String, Object> ensureBankAccount(Connection connection, String uuid, String name) throws Exception {
        long t = now();
        update(connection, "INSERT INTO cmv4_bank_accounts(account_id,owner_uuid,owner_name,account_type,currency,balance,status,version,created_at,updated_at) VALUES(?,?,?,'PLAYER','AR',0,'ACTIVE',0,?,?) ON CONFLICT(account_id) DO UPDATE SET owner_name=excluded.owner_name,updated_at=excluded.updated_at",
                bankAccountId(uuid), uuid, first(name, ""), t, t);
        return queryOne(connection, "SELECT * FROM cmv4_bank_accounts WHERE account_id=? LIMIT 1", bankAccountId(uuid));
    }

    private Map<String, Object> ensureDonationAccount(Connection connection, String uuid, String name) throws Exception {
        long t = now();
        update(connection, "INSERT INTO donation_accounts(player_uuid,player_name,balance,created_at,updated_at) VALUES(?,?,0,?,?) ON CONFLICT(player_uuid) DO UPDATE SET player_name=excluded.player_name,updated_at=excluded.updated_at",
                uuid, first(name, ""), t, t);
        return queryOne(connection, "SELECT * FROM donation_accounts WHERE player_uuid=? LIMIT 1", uuid);
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

    private void ensureSchema() throws Exception {
        tx(connection -> {
            update(connection, "CREATE TABLE IF NOT EXISTS cmv4_bank_accounts(account_id TEXT PRIMARY KEY,owner_uuid TEXT NOT NULL,owner_name TEXT NOT NULL DEFAULT '',account_type TEXT NOT NULL DEFAULT 'PLAYER',currency TEXT NOT NULL DEFAULT 'AR',balance BIGINT NOT NULL DEFAULT 0 CHECK(balance>=0),status TEXT NOT NULL DEFAULT 'ACTIVE',version BIGINT NOT NULL DEFAULT 0,created_at BIGINT NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL DEFAULT 0)");
            update(connection, "CREATE TABLE IF NOT EXISTS cmv4_bank_ledger(tx_id TEXT PRIMARY KEY,account_id TEXT NOT NULL,counterparty_account_id TEXT NOT NULL DEFAULT '',player_uuid TEXT NOT NULL DEFAULT '',tx_type TEXT NOT NULL,amount BIGINT NOT NULL CHECK(amount>=0),balance_after BIGINT NOT NULL DEFAULT 0,idempotency_key TEXT NOT NULL DEFAULT '',status TEXT NOT NULL DEFAULT 'COMMITTED',created_at BIGINT NOT NULL,actor TEXT NOT NULL DEFAULT '',details TEXT NOT NULL DEFAULT '')");
            update(connection, "CREATE TABLE IF NOT EXISTS cmv4_bank_transfers(tx_id TEXT PRIMARY KEY,from_account_id TEXT NOT NULL,to_account_id TEXT NOT NULL,amount BIGINT NOT NULL CHECK(amount>0),currency TEXT NOT NULL DEFAULT 'AR',status TEXT NOT NULL DEFAULT 'COMMITTED',idempotency_key TEXT NOT NULL DEFAULT '',created_at BIGINT NOT NULL,actor TEXT NOT NULL DEFAULT '',details TEXT NOT NULL DEFAULT '')");
            update(connection, "CREATE TABLE IF NOT EXISTS bank_pin_hashes(minecraft_uuid TEXT PRIMARY KEY,site_account_id TEXT NOT NULL DEFAULT '',pin_hash TEXT NOT NULL,must_change INTEGER NOT NULL DEFAULT 0,created_at BIGINT NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL DEFAULT 0)");
            update(connection, "CREATE TABLE IF NOT EXISTS ar_atms(id TEXT PRIMARY KEY,world TEXT NOT NULL,x INTEGER NOT NULL,y INTEGER NOT NULL,z INTEGER NOT NULL,name TEXT NOT NULL DEFAULT 'Банкомат',active INTEGER NOT NULL DEFAULT 1,created_by TEXT NOT NULL DEFAULT '',created_at BIGINT NOT NULL DEFAULT 0,archived_by TEXT NOT NULL DEFAULT '',archived_at BIGINT NOT NULL DEFAULT 0)");
            update(connection, "CREATE TABLE IF NOT EXISTS account_lockouts(account_id TEXT PRIMARY KEY,locked_until BIGINT NOT NULL DEFAULT 0,reason TEXT NOT NULL DEFAULT '',updated_at BIGINT NOT NULL DEFAULT 0)");
            update(connection, "CREATE TABLE IF NOT EXISTS failed_pin_attempts(id BIGSERIAL PRIMARY KEY,minecraft_uuid TEXT NOT NULL,site_account_id TEXT NOT NULL DEFAULT '',attempted_at BIGINT NOT NULL DEFAULT 0,source TEXT NOT NULL DEFAULT '')");
            update(connection, "CREATE TABLE IF NOT EXISTS atm_events(id BIGSERIAL PRIMARY KEY,atm_id TEXT NOT NULL,player_uuid TEXT NOT NULL DEFAULT '',player_name TEXT NOT NULL DEFAULT '',event_type TEXT NOT NULL,amount BIGINT NOT NULL DEFAULT 0,balance_after BIGINT NOT NULL DEFAULT 0,created_at BIGINT NOT NULL DEFAULT 0,details TEXT NOT NULL DEFAULT '')");
            update(connection, "CREATE TABLE IF NOT EXISTS plugin_events(id BIGSERIAL PRIMARY KEY,source TEXT NOT NULL,event_type TEXT NOT NULL,actor TEXT NOT NULL DEFAULT '',target TEXT NOT NULL DEFAULT '',created_at BIGINT NOT NULL DEFAULT 0,details TEXT NOT NULL DEFAULT '')");
            update(connection, "CREATE TABLE IF NOT EXISTS cmv4_audit_events(id BIGSERIAL PRIMARY KEY,time BIGINT NOT NULL DEFAULT 0,actor TEXT NOT NULL DEFAULT '',action TEXT NOT NULL DEFAULT '',details TEXT NOT NULL DEFAULT '',admin_only INTEGER NOT NULL DEFAULT 0,source TEXT NOT NULL DEFAULT '')");
            update(connection, "CREATE TABLE IF NOT EXISTS donation_accounts(player_uuid TEXT PRIMARY KEY,player_name TEXT NOT NULL DEFAULT '',balance BIGINT NOT NULL DEFAULT 0 CHECK(balance>=0),created_at BIGINT NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL DEFAULT 0)");
            update(connection, "CREATE TABLE IF NOT EXISTS donation_balance_ledger(id TEXT PRIMARY KEY,player_uuid TEXT NOT NULL,delta BIGINT NOT NULL,balance_after BIGINT NOT NULL DEFAULT 0,reason TEXT NOT NULL DEFAULT '',actor TEXT NOT NULL DEFAULT '',source TEXT NOT NULL DEFAULT '',idempotency_key TEXT NOT NULL DEFAULT '',created_at BIGINT NOT NULL DEFAULT 0)");
            update(connection, "CREATE TABLE IF NOT EXISTS donation_payment_sessions(id TEXT PRIMARY KEY,player_uuid TEXT NOT NULL DEFAULT '',provider TEXT NOT NULL DEFAULT 'SBP_MOCK',amount BIGINT NOT NULL DEFAULT 0,status TEXT NOT NULL DEFAULT 'CREATED',qr_payload TEXT NOT NULL DEFAULT '',created_at BIGINT NOT NULL DEFAULT 0,expires_at BIGINT NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL DEFAULT 0)");
            update(connection, "CREATE TABLE IF NOT EXISTS donation_purchases(id TEXT PRIMARY KEY,player_uuid TEXT NOT NULL,item_id TEXT NOT NULL,price BIGINT NOT NULL DEFAULT 0,status TEXT NOT NULL DEFAULT 'CREATED',created_at BIGINT NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL DEFAULT 0)");
            update(connection, "CREATE TABLE IF NOT EXISTS donation_item_claims(id TEXT PRIMARY KEY,player_uuid TEXT NOT NULL,item_id TEXT NOT NULL,amount BIGINT NOT NULL DEFAULT 1,status TEXT NOT NULL DEFAULT 'UNCLAIMED',claimed_at BIGINT NOT NULL DEFAULT 0,created_at BIGINT NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL DEFAULT 0,purchase_id TEXT NOT NULL DEFAULT '',actor TEXT NOT NULL DEFAULT '')");
            update(connection, "CREATE TABLE IF NOT EXISTS protected_block_visuals(id TEXT PRIMARY KEY,kind TEXT NOT NULL,linked_id TEXT NOT NULL,world TEXT NOT NULL,x INTEGER NOT NULL,y INTEGER NOT NULL,z INTEGER NOT NULL,entity_uuid TEXT NOT NULL DEFAULT '',base_material TEXT NOT NULL DEFAULT 'PAPER',custom_model_data INTEGER NOT NULL DEFAULT 0,model_id TEXT NOT NULL DEFAULT '',offset_x DOUBLE PRECISION NOT NULL DEFAULT 0.5,offset_y DOUBLE PRECISION NOT NULL DEFAULT 0.5,offset_z DOUBLE PRECISION NOT NULL DEFAULT 0.5,scale_x DOUBLE PRECISION NOT NULL DEFAULT 1.01,scale_y DOUBLE PRECISION NOT NULL DEFAULT 1.01,scale_z DOUBLE PRECISION NOT NULL DEFAULT 1.01,yaw DOUBLE PRECISION NOT NULL DEFAULT 0,pitch DOUBLE PRECISION NOT NULL DEFAULT 0,created_at BIGINT NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL DEFAULT 0,active INTEGER NOT NULL DEFAULT 1)");
            update(connection, "CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv4_bank_owner_type_active ON cmv4_bank_accounts(owner_uuid,account_type,currency) WHERE status='ACTIVE'");
            update(connection, "CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv4_bank_ledger_idempotency ON cmv4_bank_ledger(idempotency_key) WHERE idempotency_key<>''");
            update(connection, "CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv4_bank_transfers_idempotency ON cmv4_bank_transfers(idempotency_key) WHERE idempotency_key<>''");
            update(connection, "CREATE UNIQUE INDEX IF NOT EXISTS ux_donation_balance_ledger_idempotency ON donation_balance_ledger(idempotency_key) WHERE idempotency_key<>''");
            update(connection, "CREATE INDEX IF NOT EXISTS idx_donation_balance_ledger_player_time ON donation_balance_ledger(player_uuid,created_at DESC)");
            update(connection, "CREATE INDEX IF NOT EXISTS idx_donation_sessions_player_status ON donation_payment_sessions(player_uuid,status,created_at DESC)");
            update(connection, "CREATE INDEX IF NOT EXISTS idx_donation_purchases_player_status ON donation_purchases(player_uuid,status,created_at DESC)");
            update(connection, "CREATE INDEX IF NOT EXISTS idx_donation_claims_player_status ON donation_item_claims(player_uuid,status,created_at DESC)");
            update(connection, "CREATE INDEX IF NOT EXISTS idx_ar_atms_location ON ar_atms(world,x,y,z,active)");
            update(connection, "CREATE INDEX IF NOT EXISTS idx_protected_block_visuals_linked ON protected_block_visuals(linked_id,active)");
            update(connection, "CREATE INDEX IF NOT EXISTS idx_protected_block_visuals_location ON protected_block_visuals(world,x,y,z,active)");
            return null;
        });
    }

    private DbSettings loadDbSettings() throws Exception {
        Map<String, String> values = new HashMap<>(System.getenv());
        Path envFile = Optional.ofNullable(System.getenv("COPIMINE_ENV_FILE"))
                .filter(path -> !path.isBlank())
                .map(Paths::get)
                .orElse(Paths.get(System.getProperty("user.dir")).resolve("../admin-web/.env").normalize());
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
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            statement.executeUpdate();
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

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws Exception;
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private String safeError(Throwable error) {
        String message = error == null ? "unknown" : String.valueOf(error.getMessage());
        return message.replaceAll("(?i)(password=)[^\\s&]+", "$1***").replaceAll("(?i)(POSTGRES_PASSWORD=)[^\\s&]+", "$1***");
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

    private record AtmPinSession(String atmId, String action, int amount, String pin, String targetUuid, String targetName) {}

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
        public DonationPurchaseService donationPurchaseService() {
            return donationPurchaseService;
        }
    }

    private final class DonationBalanceServiceImpl implements DonationBalanceService {
        @Override
        public long balance(UUID playerUuid, String playerName) {
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
            } catch (Exception error) {
                return new TxnResult(false, "DONATION_ERROR", safeError(error), 0L, "");
            }
        }
    }

    private void requireKnownDonationItem(String itemId) {
        String normalized = first(itemId, "").trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Donation claim item id is required.");
        }
        Plugin plugin = Bukkit.getPluginManager().getPlugin("CopiMineArtifacts");
        if (plugin == null || !plugin.isEnabled()) {
            throw new IllegalStateException("CopiMineArtifacts is required to validate donation item ids.");
        }
        try {
            Object result = plugin.getClass().getMethod("knowsCatalogItem", String.class).invoke(plugin, normalized);
            if (!(result instanceof Boolean known) || !known) {
                throw new IllegalArgumentException("Unknown artifact catalog item id: " + normalized);
            }
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Unable to validate donation item id via CopiMineArtifacts.", error);
        }
    }

    private final class DonationPurchaseServiceImpl implements DonationPurchaseService {
        @Override
        public CompletableFuture<Map<String, Object>> createTestPurchaseAsync(UUID playerUuid, String playerName, String itemId, long price, String actor) {
            return dbFuture("create donation test purchase", () -> tx(connection -> {
                requireKnownDonationItem(itemId);
                String uuid = playerUuid == null ? "" : playerUuid.toString();
                long t = now();
                String purchaseId = "don-purchase-" + UUID.randomUUID();
                update(connection, "INSERT INTO donation_purchases(id,player_uuid,item_id,price,status,created_at,updated_at) VALUES(?,?,?,?, 'PAID', ?, ?)",
                        purchaseId, uuid, first(itemId, ""), Math.max(0L, price), t, t);
                String claimId = "don-claim-" + UUID.randomUUID();
                update(connection, "INSERT INTO donation_item_claims(id,player_uuid,item_id,amount,status,claimed_at,created_at,updated_at,purchase_id,actor) VALUES(?,?,?,?, 'UNCLAIMED', 0, ?, ?, ?, ?)",
                        claimId, uuid, first(itemId, ""), 1L, t, t, purchaseId, first(actor, ""));
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("purchase_id", purchaseId);
                row.put("claim_id", claimId);
                row.put("player_uuid", uuid);
                row.put("player_name", first(playerName, ""));
                row.put("item_id", first(itemId, ""));
                row.put("price", Math.max(0L, price));
                row.put("status", "PAID");
                return row;
            }));
        }

        @Override
        public CompletableFuture<Map<String, Object>> createClaimAsync(UUID playerUuid, String itemId, long amount, String actor, String purchaseId) {
            return dbFuture("create donation claim", () -> tx(connection -> {
                requireKnownDonationItem(itemId);
                String claimId = "don-claim-" + UUID.randomUUID();
                long t = now();
                update(connection, "INSERT INTO donation_item_claims(id,player_uuid,item_id,amount,status,claimed_at,created_at,updated_at,purchase_id,actor) VALUES(?,?,?,?, 'UNCLAIMED', 0, ?, ?, ?, ?)",
                        claimId, playerUuid == null ? "" : playerUuid.toString(), first(itemId, ""), Math.max(1L, amount), t, t, first(purchaseId, ""), first(actor, ""));
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("claim_id", claimId);
                row.put("player_uuid", playerUuid == null ? "" : playerUuid.toString());
                row.put("item_id", first(itemId, ""));
                row.put("amount", Math.max(1L, amount));
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
                return queryList("SELECT id,item_id,amount,status,purchase_id,created_at FROM donation_item_claims WHERE player_uuid=? AND status='UNCLAIMED' ORDER BY created_at DESC LIMIT ?",
                        playerUuid.toString(), Math.max(1, Math.min(limit, 200)));
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
                    List<Map<String, Object>> rows = queryList(connection, "SELECT status FROM donation_item_claims WHERE id=? AND player_uuid=? FOR UPDATE", claimId, playerUuid.toString());
                    if (rows.isEmpty()) {
                        return false;
                    }
                    if (!"RESERVED".equalsIgnoreCase(string(rows.getFirst().get("status")))) {
                        return false;
                    }
                    update(connection, "UPDATE donation_item_claims SET status='UNCLAIMED',updated_at=? WHERE id=?", now(), claimId);
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
            openBankAtms(player);
        }

        @Override
        public String createAtTarget(Player player) throws Exception {
            return createBankAtmFromTargetAsync(player);
        }

        @Override
        public String archive(Player actor, String atmId) throws Exception {
            return archiveBankAtmAsync(actor, atmId);
        }
    }

    private final class ArtifactsBridgeImpl implements ArtifactsBridge {
        @Override
        public PinStatus pinStatus(UUID playerUuid) {
            return BankServiceImpl.pinStatusFor(playerUuid);
        }

        @Override
        public TxnResult charge(UUID playerUuid, String playerName, long amount, String pin, String idempotencyKey, String action, String details) {
            return bankService.charge(playerUuid, playerName, amount, pin, idempotencyKey, action, details);
        }

        @Override
        public TxnResult refund(UUID playerUuid, String playerName, long amount, String idempotencyKey, String action, String details) {
            return bankService.refund(playerUuid, playerName, amount, idempotencyKey, action, details);
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
            if (fromUuid == null || toUuid == null || amount <= 0) {
                return new TxnResult(false, "INVALID_REQUEST", "Некорректные данные перевода.", 0L, "");
            }
            try {
                long locked = bankPinLockedSeconds(fromUuid.toString());
                if (locked > 0) {
                    return new TxnResult(false, "PIN_LOCKED", "PIN временно заблокирован.", 0L, "");
                }
                if (!bankPinSet(fromUuid.toString())) {
                    return new TxnResult(false, "PIN_REQUIRED", "Сначала задай банковский PIN на сайте.", 0L, "");
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
                    long before = scalarLong(connection, "SELECT COALESCE(balance,0) FROM cmv4_bank_accounts WHERE account_id=? FOR UPDATE", fromId);
                    long targetBefore = scalarLong(connection, "SELECT COALESCE(balance,0) FROM cmv4_bank_accounts WHERE account_id=? FOR UPDATE", toId);
                    if (before < amount) {
                        return new TxnResult(false, "INSUFFICIENT_AR", "Недостаточно AR на счёте.", before, "");
                    }
                    long t = now();
                    long after = before - amount;
                    long targetAfter = targetBefore + amount;
                    String txId = first(idempotencyKey, "tx-" + UUID.randomUUID());
                    update(connection, "UPDATE cmv4_bank_accounts SET balance=?,version=version+1,updated_at=? WHERE account_id=?", after, t, fromId);
                    update(connection, "UPDATE cmv4_bank_accounts SET balance=?,version=version+1,updated_at=? WHERE account_id=?", targetAfter, t, toId);
                    update(connection, "INSERT INTO cmv4_bank_transfers(tx_id,from_account_id,to_account_id,amount,currency,status,idempotency_key,created_at,actor,details) VALUES(?,?,?,?,'AR','COMMITTED',?,?,?,?)",
                            txId, fromId, toId, amount, txId, t, first(fromName, ""), first(details, ""));
                    update(connection, "INSERT INTO cmv4_bank_ledger(tx_id,account_id,counterparty_account_id,player_uuid,tx_type,amount,balance_after,idempotency_key,status,created_at,actor,details) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                            txId + ":out", fromId, toId, fromUuid.toString(), first(action, "TRANSFER_OUT"), amount, after, txId + ":out", "COMMITTED", t, first(fromName, ""), first(details, ""));
                    update(connection, "INSERT INTO cmv4_bank_ledger(tx_id,account_id,counterparty_account_id,player_uuid,tx_type,amount,balance_after,idempotency_key,status,created_at,actor,details) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                            txId + ":in", toId, fromId, toUuid.toString(), "TRANSFER_IN", amount, targetAfter, txId + ":in", "COMMITTED", t, first(fromName, ""), first(details, ""));
                    return new TxnResult(true, "OK", "Коммит выполнен.", after, txId);
                });
            } catch (Exception error) {
                return new TxnResult(false, "BANK_ERROR", safeError(error), 0L, "");
            }
        }

        @Override
        public CompletableFuture<TxnResult> transferWithPinAsync(UUID fromUuid, String fromName, UUID toUuid, String toName, long amount, String pin, String idempotencyKey, String action, String details) {
            return dbFuture("bank transfer", () -> transferWithPin(fromUuid, fromName, toUuid, toName, amount, pin, idempotencyKey, action, details));
        }

        @Override
        public TxnResult credit(UUID toUuid, String toName, long amount, String idempotencyKey, String action, String details) {
            if (toUuid == null || amount <= 0) {
                return new TxnResult(false, "INVALID_REQUEST", "Некорректные данные пополнения.", 0L, "");
            }
            try {
                return tx(connection -> {
                    Map<String, Object> account = ensureBankAccount(connection, toUuid.toString(), first(toName, ""));
                    String accountId = string(account.get("account_id"));
                    long before = scalarLong(connection, "SELECT COALESCE(balance,0) FROM cmv4_bank_accounts WHERE account_id=? FOR UPDATE", accountId);
                    long after = before + amount;
                    long t = now();
                    String txId = first(idempotencyKey, "credit-" + UUID.randomUUID());
                    update(connection, "UPDATE cmv4_bank_accounts SET balance=?,version=version+1,updated_at=? WHERE account_id=?", after, t, accountId);
                    update(connection, "INSERT INTO cmv4_bank_ledger(tx_id,account_id,counterparty_account_id,player_uuid,tx_type,amount,balance_after,idempotency_key,status,created_at,actor,details) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                            txId, accountId, first(action, "CREDIT"), toUuid.toString(), first(action, "CREDIT"), amount, after, txId, "COMMITTED", t, first(toName, ""), first(details, ""));
                    return new TxnResult(true, "OK", "Коммит выполнен.", after, txId);
                });
            } catch (Exception error) {
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
                    List<Map<String, Object>> existing = queryList(connection, "SELECT tx_id,balance_after FROM cmv4_bank_ledger WHERE idempotency_key=? LIMIT 1", txKey);
                    if (!existing.isEmpty()) {
                        return new TxnResult(true, "OK", "Idempotent replay.", intValue(existing.getFirst().get("balance_after")), string(existing.getFirst().get("tx_id")));
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
    }
}
