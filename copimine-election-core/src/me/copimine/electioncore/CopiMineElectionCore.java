package me.copimine.electioncore;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import me.copimine.economycore.CopiMineEconomyCore;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class CopiMineElectionCore extends JavaPlugin implements Listener, CommandExecutor {
    private static final String SIDEBAR_OBJECTIVE = "cm_election_live";
    private static final Pattern SAFE_SCHEMA = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final long PRESIDENT_BROADCAST_COOLDOWN_MS = 60L * 60L * 1000L;
    private static final long PRESIDENT_LAW_REPLACE_COOLDOWN_MS = 3L * 24L * 60L * 60L * 1000L;
    private static final int LAW_TEXT_LIMIT = 80;
    private static final int BROADCAST_TEXT_LIMIT = 80;
    private static final List<Integer> CANDIDATE_LIMITS = List.of(2, 3, 4, 5, 6, 7, 8, 10, 15, 20, -1);
    private static final List<Integer> TERM_DAYS = List.of(7, 10, 14, 21, 28);
    private static final int MODEL_ATM_TERMINAL = 12002;
    private static final int MODEL_POLLING_STATION_MARKER = 14002;
    private static final int MODEL_TAX_OFFICE_MARKER = 14003;
    private static final int MODEL_ARTIFACT_SHOP_MARKER = 14004;

    private final Set<UUID> liveHidden = ConcurrentHashMap.newKeySet();
    private final Map<UUID, PromptContext> prompts = new ConcurrentHashMap<>();
    private final Map<UUID, List<ItemStack>> officialRestore = new ConcurrentHashMap<>();
    private final AtomicReference<LiveSnapshot> snapshot = new AtomicReference<>(LiveSnapshot.empty());
    private final Set<BlockKey> protectedBlocks = ConcurrentHashMap.newKeySet();
    private final ElectionStateMachine electionStateMachine = new ElectionStateMachine();

    private NamespacedKey itemTypeKey;
    private NamespacedKey electionIdKey;
    private NamespacedKey stationIdKey;
    private NamespacedKey applicationIdKey;
    private NamespacedKey ballotIdKey;
    private NamespacedKey roundKey;
    private NamespacedKey sealIdKey;
    private NamespacedKey playerUuidKey;
    private NamespacedKey textTypeKey;
    private NamespacedKey textLinkedIdKey;
    private NamespacedKey visualEntityTypeKey;
    private NamespacedKey visualKindKey;
    private NamespacedKey visualLinkedIdKey;
    private NamespacedKey visualModelIdKey;
    private DbSettings db;
    private Path webDataFile;

    @Override
    public void onEnable() {
        itemTypeKey = new NamespacedKey(this, "item_type");
        electionIdKey = new NamespacedKey(this, "election_id");
        stationIdKey = new NamespacedKey(this, "station_id");
        applicationIdKey = new NamespacedKey(this, "application_id");
        ballotIdKey = new NamespacedKey(this, "ballot_id");
        roundKey = new NamespacedKey(this, "round_no");
        sealIdKey = new NamespacedKey(this, "seal_id");
        playerUuidKey = new NamespacedKey(this, "player_uuid");
        textTypeKey = new NamespacedKey(this, "text_type");
        textLinkedIdKey = new NamespacedKey(this, "text_linked_id");
        visualEntityTypeKey = new NamespacedKey(this, "visual_entity_type");
        visualKindKey = new NamespacedKey(this, "visual_kind");
        visualLinkedIdKey = new NamespacedKey(this, "visual_linked_id");
        visualModelIdKey = new NamespacedKey(this, "visual_model_id");
        webDataFile = getDataFolder().toPath().resolve("web-data.json");
        saveDefaultConfig();
        loadHiddenPlayers();
        try {
            db = loadDbSettings();
            ensureSchema();
            reloadProtectedBlocks();
        } catch (Exception error) {
            getLogger().severe("Не удалось включить CopiMineElectionCore: " + safeError(error));
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        Objects.requireNonNull(getCommand("hidelive"), "hidelive command").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        try {
            repairProtectedBlockVisuals();
        } catch (Exception error) {
            getLogger().warning("repair visuals startup: " + safeError(error));
        }
        Bukkit.getScheduler().runTaskAsynchronously(this, this::reconcilePendingTaxPaymentsSafe);
        Bukkit.getScheduler().runTaskAsynchronously(this, this::refreshSnapshotAndPush);
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::refreshSnapshotAndPush, 40L, 60L);
        for (Player player : Bukkit.getOnlinePlayers()) {
            restoreOfficialItems(player);
        }
    }

    @Override
    public void onDisable() {
        saveHiddenPlayers();
        for (Player player : Bukkit.getOnlinePlayers()) {
            clearSidebar(player);
        }
    }

    public void openAdminElectionHub(Player player) {
        openElectionRoot(player, 0);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("hidelive")) {
            return false;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color("&cКоманда доступна только в игре."));
            return true;
        }
        if (liveHidden.contains(player.getUniqueId())) {
            liveHidden.remove(player.getUniqueId());
            saveHiddenPlayers();
            renderSidebar(player, snapshot.get());
            player.sendMessage(color("&aLive-панель снова показана только тебе."));
        } else {
            liveHidden.add(player.getUniqueId());
            saveHiddenPlayers();
            clearSidebar(player);
            player.sendMessage(color("&eLive-панель скрыта только у тебя."));
        }
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        restoreOfficialItems(event.getPlayer());
        if (!liveHidden.contains(event.getPlayer().getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(this, () -> renderSidebar(event.getPlayer(), snapshot.get()), 20L);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> restoreOfficialItems(event.getPlayer()), 5L);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        try {
            repairProtectedBlockVisuals(event.getChunk().getWorld().getName(), event.getChunk().getX(), event.getChunk().getZ());
        } catch (Exception error) {
            getLogger().warning("repair visuals chunk: " + safeError(error));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommandAlias(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage().trim();
        if (!message.startsWith("/")) {
            return;
        }
        String root = message.substring(1).split("\\s+")[0].toLowerCase(Locale.ROOT);
        if (Set.of("cadm", "adminhub", "copimine", "cadmin", "cpanel", "cpadmin", "cpa", "админка", "кмадмин").contains(root) && hasElectionAdmin(event.getPlayer())) {
            return;
        }
        if (Set.of("cmeflow", "cmpres", "cmstations", "cmseal", "cmballotadmin", "cmvote", "voteadmin").contains(root)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(color("&cСтарые команды выборов больше не используются."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack item = event.getItem();
            if (isBallot(item)) {
                event.setCancelled(true);
                openBallotVoteMenu(player, item);
                return;
            }
            if (isPresidentMandate(item)) {
                event.setCancelled(true);
                if (!isPresident(player) && !hasElectionAdmin(player)) {
                    player.sendMessage(color("&cМандат президента доступен действующему президенту."));
                    return;
                }
                openPresidentMandateMenu(player);
                return;
            }
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null || !event.getAction().isRightClick()) {
            return;
        }
        ProtectedBlockInfo protectedInfo = protectedBlockInfo(clicked);
        if (protectedInfo == null) {
            return;
        }
        event.setCancelled(true);
        if ("TAX_OFFICE".equals(protectedInfo.kind())) {
            openTaxOfficeMenu(player, protectedInfo.linkedId(), "", null);
            return;
        }
        if (!"POLLING_STATION".equals(protectedInfo.kind())) {
            return;
        }
        try {
            if (isApplicationBook(event.getItem())) {
                submitApplicationBook(player, event.getItem(), protectedInfo.linkedId());
                return;
            }
            if (isBallot(event.getItem()) && isConfirmedBallot(event.getItem())) {
                depositBallot(player, event.getItem(), protectedInfo.linkedId());
                return;
            }
            Map<String, Object> station = stationById(protectedInfo.linkedId());
            if (station == null) {
                player.sendMessage(color("&cУчасток больше не найден."));
                return;
            }
            if (isChairForStation(player, station)) {
                openChairStationMenu(player, protectedInfo.linkedId(), 0);
                return;
            }
            openStationCard(player, protectedInfo.linkedId());
        } catch (Exception error) {
            player.sendMessage(color("&cНе удалось обработать участок. Подробности в логе."));
            getLogger().warning("station interact: " + safeError(error));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSealInteract(PlayerInteractEntityEvent event) {
        if (handleProtectedVisualInteract(event.getPlayer(), event.getRightClicked(), event)) {
            return;
        }
        if (!(event.getRightClicked() instanceof Player target)) {
            return;
        }
        Player player = event.getPlayer();
        if (!isCikSeal(player.getInventory().getItemInMainHand())) {
            return;
        }
        event.setCancelled(true);
        if (!hasElectionAdmin(player) && !isChair(player)) {
            player.sendMessage(color("&cПечать ЦИК доступна только председателю или администратору."));
            return;
        }
        try {
            SealContext seal = validateSealUsage(player.getInventory().getItemInMainHand(), player);
            if (seal == null) {
                // Bulk cleanup path still exists for admin revoke flows: removeOfficialItemsFromPlayer(player, "CIK_SEAL")
                player.sendMessage(color("&cЭта печать ЦИК больше недействительна и была удалена."));
                removeItemFromMainHandIfType(player, "CIK_SEAL");
                return;
            }
            openSealTargetMenu(player, target, seal);
        } catch (Exception error) {
            player.sendMessage(color("&cНе удалось проверить печать ЦИК."));
            getLogger().warning("seal interact: " + safeError(error));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder holder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            String action = null;
            if (event.getClick().isRightClick()) {
                action = first(holder.rightActions().get(event.getRawSlot()), holder.actions().get(event.getRawSlot()));
            }
            if (action == null || action.isBlank()) {
                action = holder.actions().get(event.getRawSlot());
            }
            if (action == null || action.isBlank()) {
                return;
            }
            try {
                handleMenuAction(player, action, event.getClick(), holder);
            } catch (Exception error) {
                sendUserError(player, error, "&cНе удалось выполнить действие. Подробности в логе.");
                getLogger().warning("menu action " + action + ": " + safeError(error));
            }
            return;
        }
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        if (isProtectedOfficialItem(current) || isProtectedOfficialItem(cursor)) {
            InventoryView view = event.getView();
            if (view.getTopInventory() != null && view.getTopInventory().getHolder() != null && !(view.getTopInventory().getHolder() instanceof Player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        ItemStack cursor = event.getOldCursor();
        if (!isProtectedOfficialItem(cursor)) {
            return;
        }
        InventoryView view = event.getView();
        if (view.getTopInventory() != null && view.getTopInventory().getHolder() != null && !(view.getTopInventory().getHolder() instanceof Player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMenuClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder) {
            prompts.computeIfPresent(event.getPlayer().getUniqueId(), (uuid, prompt) -> prompt.keepOnClose() ? prompt : null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack dropped = event.getItemDrop().getItemStack();
        if (isCikSeal(dropped)) {
            event.setCancelled(true);
            event.getItemDrop().remove();
            destroyOneCikSeal(event.getPlayer(), readString(dropped, sealIdKey));
            event.getPlayer().sendMessage(color("&eПечать ЦИК уничтожена."));
            return;
        }
        if (isProtectedOfficialItem(dropped)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMoveOfficial(InventoryMoveItemEvent event) {
        if (isProtectedOfficialItem(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOfficialDeath(PlayerDeathEvent event) {
        List<ItemStack> keep = new ArrayList<>();
        event.getDrops().removeIf(stack -> {
            if (isProtectedOfficialItem(stack)) {
                keep.add(stack.clone());
                return true;
            }
            return false;
        });
        if (!keep.isEmpty()) {
            officialRestore.put(event.getEntity().getUniqueId(), keep);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOfficialDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Item item && isProtectedOfficialItem(item.getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOfficialDespawn(ItemDespawnEvent event) {
        if (isProtectedOfficialItem(event.getEntity().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isProtectedOfficialItem(event.getItemInHand())) {
            event.setCancelled(true);
        }
        if (protectedBlocks.contains(BlockKey.from(event.getBlockPlaced().getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedBreak(BlockBreakEvent event) {
        BlockKey key = BlockKey.from(event.getBlock().getLocation());
        if (!protectedBlocks.contains(key)) {
            return;
        }
        event.setCancelled(true);
        if (!hasElectionAdmin(event.getPlayer())) {
            event.getPlayer().sendMessage(color("&cЭтот выборный блок защищён."));
            return;
        }
        ProtectedBlockInfo info = protectedBlockInfo(event.getBlock());
        if (info == null) {
            event.getPlayer().sendMessage(color("&cНе удалось определить тип защищённого блока."));
            return;
        }
        if ("POLLING_STATION".equalsIgnoreCase(info.kind())) {
            openConfirmationMenu(event.getPlayer(), "&cУдалить участок", List.of(
                    "&7Блок участка будет снят с защиты и удалён из базы.",
                    "&7Потом участок можно будет создать заново, если он снова понадобится."
            ), "apply:station:remove-protection:" + info.linkedId(), "station:view:" + info.linkedId());
            return;
        }
        event.getPlayer().sendMessage(color("&eДля этого защищённого блока используйте профильное меню."));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedBurn(BlockBurnEvent event) {
        if (protectedBlocks.contains(BlockKey.from(event.getBlock().getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> protectedBlocks.contains(BlockKey.from(block.getLocation())));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> protectedBlocks.contains(BlockKey.from(block.getLocation())));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedForm(EntityBlockFormEvent event) {
        if (protectedBlocks.contains(BlockKey.from(event.getBlock().getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> protectedBlocks.contains(BlockKey.from(block.getLocation())))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> protectedBlocks.contains(BlockKey.from(block.getLocation())))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedFlow(BlockFromToEvent event) {
        if (protectedBlocks.contains(BlockKey.from(event.getToBlock().getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedFade(BlockFadeEvent event) {
        if (protectedBlocks.contains(BlockKey.from(event.getBlock().getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedIgnite(BlockIgniteEvent event) {
        if (protectedBlocks.contains(BlockKey.from(event.getBlock().getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChatPrompt(AsyncPlayerChatEvent event) {
        PromptContext prompt = prompts.remove(event.getPlayer().getUniqueId());
        if (prompt == null) {
            return;
        }
        event.setCancelled(true);
        String message = ChatColor.stripColor(event.getMessage() == null ? "" : event.getMessage()).trim();
        Bukkit.getScheduler().runTask(this, () -> handlePrompt(event.getPlayer(), prompt, message));
    }

    private void handleMenuAction(Player player, String action, ClickType click, MenuHolder holder) throws Exception {
        if (action.equals("close")) {
            player.closeInventory();
            return;
        }
        if (action.equals("back:hub")) {
            player.closeInventory();
            Plugin adminPlugin = Bukkit.getPluginManager().getPlugin("CopiMineUltimateAdminPlus");
            if (adminPlugin != null) {
                adminPlugin.getClass().getMethod("openHub", Player.class).invoke(adminPlugin, player);
            }
            return;
        }
        if (action.equals("confirm:apply")) {
            String applyAction = first(holder.data().get("apply_action"), "");
            if (!applyAction.isBlank()) {
                handleMenuAction(player, applyAction, click, holder);
            }
            return;
        }
        if (action.equals("confirm:back")) {
            String backAction = first(holder.data().get("back_action"), "");
            if (backAction.isBlank()) {
                player.closeInventory();
            } else {
                handleMenuAction(player, backAction, click, holder);
            }
            return;
        }
        if (action.equals("open:root")) {
            openElectionRoot(player, 0);
            return;
        }
        if (action.equals("open:manage")) {
            openManagementMenu(player);
            return;
        }
        if (action.startsWith("open:stations")) {
            openStationsMenu(player, parsePage(action));
            return;
        }
        if (action.equals("open:cik")) {
            openCikMenu(player, 0);
            return;
        }
        if (action.startsWith("open:cik:")) {
            openCikMenu(player, parsePage(action));
            return;
        }
        if (action.equals("open:applications")) {
            openApplicationsMenu(player, "PENDING", 0);
            return;
        }
        if (action.startsWith("open:applications:")) {
            String[] parts = action.split(":");
            String status = parts.length > 2 ? parts[2] : "PENDING";
            int page = parts.length > 3 ? parseInt(parts[3], 0) : 0;
            openApplicationsMenu(player, status, page);
            return;
        }
        if (action.equals("open:results")) {
            openResultsMenu(player);
            return;
        }
        if (action.equals("open:president")) {
            openPresidentAdminMenu(player);
            return;
        }
        if (action.equals("open:live")) {
            openLiveMenu(player);
            return;
        }
        if (action.startsWith("station:assign-chair:") && action.contains(":page:")) {
            String payload = action.substring("station:assign-chair:".length());
            int marker = payload.lastIndexOf(":page:");
            if (marker > 0) {
                openChairPicker(player, payload.substring(0, marker), parseInt(payload.substring(marker + 6), 0));
                return;
            }
        }
        if (action.startsWith("chair:ballots:")) {
            String payload = action.substring("chair:ballots:".length());
            int marker = payload.lastIndexOf(':');
            if (marker > 0) {
                String pageText = payload.substring(marker + 1);
                if (pageText.matches("-?\\d+")) {
                    openChairBallotsMenu(player, payload.substring(0, marker), parseInt(pageText, 0));
                    return;
                }
            }
        }
        if (action.startsWith("president:payments:")) {
            openPresidentPaymentsMenu(player, parseInt(action.substring("president:payments:".length()), 0));
            return;
        }
        if (action.equals("apply:manage:stop")) {
            stopElection(player.getName());
            refreshSnapshotAndPush();
            openManagementMenu(player);
            return;
        }
        if (action.startsWith("apply:stage:")) {
            String raw = action.substring("apply:stage:".length());
            setStage(requireActiveElectionId(), ElectionStage.valueOf(raw), player.getName(), "Перевод через GUI");
            refreshSnapshotAndPush();
            openManagementMenu(player);
            return;
        }
        if (action.startsWith("apply:station:remove-protection:")) {
            removePollingStation(player, action.substring("apply:station:remove-protection:".length()));
            refreshSnapshotAndPush();
            openStationsMenu(player, 0);
            return;
        }
        if (action.startsWith("apply:station:self-chair:")) {
            String stationId = action.substring("apply:station:self-chair:".length());
            claimChairForStation(player, stationId);
            refreshSnapshotAndPush();
            openStationCard(player, stationId);
            return;
        }
        if (action.startsWith("apply:station:remove-chair:")) {
            String stationId = action.substring("apply:station:remove-chair:".length());
            removeChairFromStation(stationId, player.getName());
            refreshSnapshotAndPush();
            openStationCard(player, stationId);
            return;
        }
        if (action.startsWith("apply:station:cleanup-labels:")) {
            String stationId = action.substring("apply:station:cleanup-labels:".length());
            cleanupNearbyTextDisplays(stationId);
            openStationCard(player, stationId);
            return;
        }
        if (action.equals("apply:cik:revoke-all")) {
            revokeAllSeals(player.getName());
            refreshSnapshotAndPush();
            openCikMenu(player, 0);
            return;
        }
        if (action.startsWith("apply:application:approve:")) {
            approveApplication(action.substring("apply:application:approve:".length()), player.getName());
            refreshSnapshotAndPush();
            openApplicationsMenu(player, "PENDING", 0);
            return;
        }
        if (action.startsWith("apply:application:reject:")) {
            rejectApplication(action.substring("apply:application:reject:".length()), player.getName());
            refreshSnapshotAndPush();
            openApplicationsMenu(player, "PENDING", 0);
            return;
        }
        if (action.equals("apply:results:count")) {
            countCurrentRound(player.getName());
            refreshSnapshotAndPush();
            openResultsMenu(player);
            return;
        }
        if (action.equals("apply:results:second-round")) {
            startSecondRound(player.getName());
            refreshSnapshotAndPush();
            openResultsMenu(player);
            return;
        }
        if (action.startsWith("apply:results:winner:")) {
            chooseWinner(action.substring("apply:results:winner:".length()), player.getName());
            refreshSnapshotAndPush();
            openResultsMenu(player);
            return;
        }
        if (action.equals("apply:president:remove")) {
            removePresident(player.getName());
            refreshSnapshotAndPush();
            openPresidentAdminMenu(player);
            return;
        }
        if (action.startsWith("apply:law:approve:")) {
            reviewLaw(action.substring("apply:law:approve:".length()), "APPROVED", player.getName(), "");
            refreshSnapshotAndPush();
            openPresidentAdminMenu(player);
            return;
        }
        if (action.startsWith("apply:law:reject:")) {
            reviewLaw(action.substring("apply:law:reject:".length()), "REJECTED", player.getName(), "");
            refreshSnapshotAndPush();
            openPresidentAdminMenu(player);
            return;
        }
        if (action.startsWith("apply:tax:set:")) {
            player.sendMessage(color("&eПрезидентский налог отключён. Доход президента теперь идёт из лавки AR."));
            openPresidentAdminMenu(player);
            return;
        }
        if (action.equals("apply:tax:create-office")) {
            player.sendMessage(color("&eПрезидентский налог отключён. Отдельный налоговый офис больше не используется."));
            openPresidentAdminMenu(player);
            return;
        }
        if (action.startsWith("apply:mandate:tax:")) {
            player.sendMessage(color("&eПрезидентский налог отключён. Доход президента теперь идёт из лавки AR."));
            openPresidentMandateMenu(player);
            return;
        }
        if (action.startsWith("apply:chair:annul-ballot:")) {
            String payload = action.substring("apply:chair:annul-ballot:".length());
            int marker = payload.indexOf(':');
            if (marker > 0) {
                annulBallot(payload.substring(marker + 1), payload.substring(0, marker), player, "chair-gui");
                refreshSnapshotAndPush();
                openChairBallotsMenu(player, holder.contextId(), 0);
            }
            return;
        }
        if (action.startsWith("apply:vote:confirm:")) {
            String[] parts = action.split(":");
            if (parts.length >= 5) {
                confirmBallotChoice(player, parts[3], parts[4]);
                player.closeInventory();
            }
            return;
        }
        if (action.startsWith("manage:start")) {
            ensureElectionExists(player.getName());
            refreshSnapshotAndPush();
            openManagementMenu(player);
            return;
        }
        if (action.equals("manage:stop")) {
            openConfirmationMenu(player, "&cОстановить выборы", List.of(
                    "&7Будет остановлен только текущий выборный цикл.",
                    "&7Банк, AR, игроки и другие модули не затрагиваются."
            ), "apply:manage:stop", "open:manage");
            return;
        }
        if (action.startsWith("stage:")) {
            String raw = action.substring("stage:".length());
            openConfirmationMenu(player, "&6Подтвердить этап", List.of(
                    "&7Новый этап: &f" + ElectionStage.safeValue(raw).title(),
                    "&7Изменение сразу применится ко всему выборному циклу."
            ), "apply:stage:" + raw, "open:manage");
            return;
        }
        if (action.startsWith("manage:limit:")) {
            int limit = parseInt(action.substring("manage:limit:".length()), 2);
            setCandidateLimit(player, limit);
            refreshSnapshotAndPush();
            openManagementMenu(player);
            return;
        }
        if (action.startsWith("manage:term:")) {
            int days = parseInt(action.substring("manage:term:".length()), 7);
            setPresidentTermDays(player, days);
            refreshSnapshotAndPush();
            openManagementMenu(player);
            return;
        }
        if (action.equals("manage:reset:confirm")) {
            openResetConfirmMenu(player);
            return;
        }
        if (action.equals("manage:reset:only-elections")) {
            openResetApplyMenu(player);
            return;
        }
        if (action.equals("manage:reset:apply")) {
            resetElections(player.getName());
            refreshSnapshotAndPush();
            openManagementMenu(player);
            return;
        }
        if (action.equals("stations:create")) {
            createPollingStationFromTarget(player);
            refreshSnapshotAndPush();
            openStationsMenu(player, 0);
            return;
        }
        if (action.startsWith("station:view:")) {
            openStationCard(player, action.substring("station:view:".length()));
            return;
        }
        if (action.startsWith("station:teleport:")) {
            teleportToStation(player, action.substring("station:teleport:".length()));
            return;
        }
        if (action.startsWith("station:assign-chair:")) {
            openChairPicker(player, action.substring("station:assign-chair:".length()), 0);
            return;
        }
        if (action.startsWith("station:self-chair:")) {
            String stationId = action.substring("station:self-chair:".length());
            openConfirmationMenu(player, "&aСтать председателем ЦИК", List.of(
                    "&7Ты станешь председателем этого участка.",
                    "&7После подтверждения будет выдана печать ЦИК."
            ), "apply:station:self-chair:" + stationId, "station:view:" + stationId);
            return;
        }
        if (action.startsWith("station:remove-chair:")) {
            String stationId = action.substring("station:remove-chair:".length());
            openConfirmationMenu(player, "&cРазжаловать председателя ЦИК", List.of(
                    "&7Активный председатель участка будет снят.",
                    "&7Печать ЦИК будет автоматически отозвана."
            ), "apply:station:remove-chair:" + stationId, "station:view:" + stationId);
            return;
        }
        if (action.startsWith("station:remove-protection:")) {
            String stationId = action.substring("station:remove-protection:".length());
            openConfirmationMenu(player, "&cСнять защиту участка", List.of(
                    "&7Защита блока и связанная надпись будут удалены.",
                    "&7Данные по голосам и заявкам останутся в журнале."
            ), "apply:station:remove-protection:" + stationId, "station:view:" + stationId);
            return;
        }
        if (action.startsWith("station:cleanup-labels:")) {
            String stationId = action.substring("station:cleanup-labels:".length());
            openConfirmationMenu(player, "&6Очистить лишние надписи", List.of(
                    "&7Будут удалены только связанные TextDisplay рядом с участком.",
                    "&7Чужие сущности трогаться не будут."
            ), "apply:station:cleanup-labels:" + stationId, "station:view:" + stationId);
            return;
        }
        if (action.startsWith("station:issue-target:") && action.contains(":page:")) {
            String payload = action.substring("station:issue-target:".length());
            int marker = payload.lastIndexOf(":page:");
            if (marker > 0) {
                openIssueTargetPicker(player, payload.substring(0, marker), parseInt(payload.substring(marker + 6), 0));
                return;
            }
        }
        if (action.startsWith("station:issue-target:")) {
            openIssueTargetPicker(player, action.substring("station:issue-target:".length()), 0);
            return;
        }
        if (action.startsWith("issuepicker:")) {
            String[] parts = action.split(":");
            if (parts.length >= 3) {
                openIssueOptionsMenu(player, parts[1], parts[2]);
            }
            return;
        }
        if (action.startsWith("admin:issue-application:")) {
            String[] parts = action.split(":");
            if (parts.length >= 4) {
                Player target = Bukkit.getPlayer(UUID.fromString(parts[3]));
                if (target == null) {
                    throw new IllegalStateException("Игрок должен быть онлайн для выдачи заявки.");
                }
                issueApplicationBookByAdmin(target, player, parts[2]);
                refreshSnapshotAndPush();
                openIssueOptionsMenu(player, parts[2], parts[3]);
            }
            return;
        }
        if (action.startsWith("admin:issue-ballot:")) {
            String[] parts = action.split(":");
            if (parts.length >= 4) {
                Player target = Bukkit.getPlayer(UUID.fromString(parts[3]));
                if (target == null) {
                    throw new IllegalStateException("Игрок должен быть онлайн для выдачи бюллетеня.");
                }
                issueBallotByAdmin(target, player, parts[2]);
                refreshSnapshotAndPush();
                openIssueOptionsMenu(player, parts[2], parts[3]);
            }
            return;
        }
        if (action.startsWith("chairpicker:")) {
            String[] parts = action.split(":");
            if (parts.length >= 4) {
                assignChairToStation(parts[1], parts[2], parts[3], player.getName());
                refreshSnapshotAndPush();
                openStationCard(player, parts[1]);
            }
            return;
        }
        if (action.equals("cik:revoke-all")) {
            openConfirmationMenu(player, "&cУничтожить все печати ЦИК", List.of(
                    "&7Все активные печати будут отозваны.",
                    "&7Выданные печати у игроков сразу станут недействительными."
            ), "apply:cik:revoke-all", "open:cik:0");
            return;
        }
        if (action.startsWith("cik:issue-seal:")) {
            String[] parts = action.split(":");
            if (parts.length >= 4) {
                String stationId = parts[2];
                Map<String, Object> chair = queryOne(
                        "SELECT player_uuid,player_name FROM cik_chairs WHERE station_id=? AND active=1 ORDER BY assigned_at DESC LIMIT 1",
                        stationId
                );
                if (chair == null) {
                    player.sendMessage(color("&cДля этого участка не назначен председатель."));
                } else {
                    issueOrQueueSeal(stationId, string(chair.get("player_uuid")), string(chair.get("player_name")), player.getName());
                }
            }
            openCikMenu(player, 0);
            return;
        }
        if (action.startsWith("application:view:")) {
            openApplicationDetail(player, action.substring("application:view:".length()));
            return;
        }
        if (action.startsWith("application:approve:")) {
            String applicationId = action.substring("application:approve:".length());
            openConfirmationMenu(player, "&aОдобрить заявку", List.of(
                    "&7Игрок станет кандидатом на текущих выборах.",
                    "&7После этого председатель сможет выдать игроку бюллетень."
            ), "apply:application:approve:" + applicationId, "application:view:" + applicationId);
            return;
        }
        if (action.startsWith("application:reject:")) {
            String applicationId = action.substring("application:reject:".length());
            openConfirmationMenu(player, "&cОтклонить заявку", List.of(
                    "&7Игрок не станет кандидатом на этих выборах.",
                    "&7Для новой попытки понадобится новая заявка."
            ), "apply:application:reject:" + applicationId, "application:view:" + applicationId);
            return;
        }
        if (action.startsWith("chair:application:view:")) {
            String payload = action.substring("chair:application:view:".length());
            int marker = payload.indexOf(':');
            if (marker > 0) {
                openChairApplicationDetail(player, payload.substring(0, marker), payload.substring(marker + 1));
            }
            return;
        }
        if (action.startsWith("chair:application:recommend:")) {
            String payload = action.substring("chair:application:recommend:".length());
            int marker = payload.indexOf(':');
            if (marker > 0) {
                String stationId = payload.substring(0, marker);
                String applicationId = payload.substring(marker + 1);
                setChairRecommendation(stationId, applicationId, "RECOMMEND", player);
                openChairApplicationDetail(player, stationId, applicationId);
            }
            return;
        }
        if (action.startsWith("chair:application:no-recommend:")) {
            String payload = action.substring("chair:application:no-recommend:".length());
            int marker = payload.indexOf(':');
            if (marker > 0) {
                String stationId = payload.substring(0, marker);
                String applicationId = payload.substring(marker + 1);
                setChairRecommendation(stationId, applicationId, "NOT_RECOMMEND", player);
                openChairApplicationDetail(player, stationId, applicationId);
            }
            return;
        }
        if (action.equals("results:count")) {
            openConfirmationMenu(player, "&6Подсчитать тур", List.of(
                    "&7Будут учтены только сданные подтверждённые бюллетени.",
                    "&7При ничьей откроется возможность второго тура."
            ), "apply:results:count", "open:results");
            return;
        }
        if (action.equals("results:second-round")) {
            openConfirmationMenu(player, "&6Открыть второй тур", List.of(
                    "&7В новый тур перейдут только лидеры с равным максимумом голосов.",
                    "&7Перед запуском убедись, что обычный подсчёт уже завершён."
            ), "apply:results:second-round", "open:results");
            return;
        }
        if (action.startsWith("results:winner:")) {
            String candidateUuid = action.substring("results:winner:".length());
            openConfirmationMenu(player, "&aВыбрать победителя", List.of(
                    "&7Победитель будет назначен вручную.",
                    "&7После подтверждения ему сразу будет выдан мандат."
            ), "apply:results:winner:" + candidateUuid, "open:results");
            return;
        }
        if (action.equals("president:open-mandate")) {
            openPresidentMandateMenu(player);
            return;
        }
        if (action.equals("president:remove")) {
            openConfirmationMenu(player, "&cСнять президента с должности", List.of(
                    "&7Действующий президент сразу потеряет мандат.",
                    "&7Мандат и права президента будут сразу отозваны."
            ), "apply:president:remove", "open:president");
            return;
        }
        if (action.startsWith("law:approve:")) {
            String lawId = action.substring("law:approve:".length());
            openConfirmationMenu(player, "&aОдобрить закон", List.of(
                    "&7Закон появится в live-панели и в Discord.",
                    "&7Если это замена, старый закон будет снят только после публикации."
            ), "apply:law:approve:" + lawId, "open:president");
            return;
        }
        if (action.startsWith("law:reject:")) {
            String lawId = action.substring("law:reject:".length());
            openConfirmationMenu(player, "&cОтклонить закон", List.of(
                    "&7Закон не будет опубликован.",
                    "&7Президент сможет отправить другой текст позже."
            ), "apply:law:reject:" + lawId, "open:president");
            return;
        }
        if (action.startsWith("tax:set:")) {
            player.sendMessage(color("&e\u041f\u0440\u0435\u0437\u0438\u0434\u0435\u043d\u0442\u0441\u043a\u0438\u0439 \u043d\u0430\u043b\u043e\u0433 \u043e\u0442\u043a\u043b\u044e\u0447\u0451\u043d. \u0414\u043e\u0445\u043e\u0434 \u043f\u0440\u0435\u0437\u0438\u0434\u0435\u043d\u0442\u0430 \u0442\u0435\u043f\u0435\u0440\u044c \u0438\u0434\u0451\u0442 \u0438\u0437 \u043b\u0430\u0432\u043a\u0438 AR."));
            openPresidentAdminMenu(player);
            return;
        }
        if (action.equals("tax:create-office")) {
            player.sendMessage(color("&e\u041d\u0430\u043b\u043e\u0433\u043e\u0432\u0430\u044f \u043e\u0442\u043a\u043b\u044e\u0447\u0435\u043d\u0430. \u0421\u043e\u0437\u0434\u0430\u043d\u0438\u0435 \u043d\u043e\u0432\u044b\u0445 \u043d\u0430\u043b\u043e\u0433\u043e\u0432\u044b\u0445 \u0431\u043e\u043b\u044c\u0448\u0435 \u043d\u0435\u0434\u043e\u0441\u0442\u0443\u043f\u043d\u043e."));
            openPresidentAdminMenu(player);
            return;
        }
        if (action.equals("live:refresh")) {
            refreshSnapshotAndPush();
            openLiveMenu(player);
            return;
        }
        if (action.startsWith("seal:issue-application:")) {
            Player target = Bukkit.getPlayerExact(action.substring("seal:issue-application:".length()));
            SealContext sealContext = revalidateSealContext(player, holder);
            issueApplicationBook(target, player, sealContext);
            if (target != null) {
                openSealTargetMenu(player, target, sealContext);
            }
            return;
        }
        if (action.startsWith("seal:issue-ballot:")) {
            Player target = Bukkit.getPlayerExact(action.substring("seal:issue-ballot:".length()));
            SealContext sealContext = revalidateSealContext(player, holder);
            issueBallot(target, player, sealContext);
            if (target != null) {
                openSealTargetMenu(player, target, sealContext);
            }
            return;
        }
        if (action.startsWith("chair:applications:")) {
            String payload = action.substring("chair:applications:".length());
            int marker = payload.lastIndexOf(':');
            if (marker > 0 && payload.substring(marker + 1).matches("-?\\d+")) {
                openChairApplicationsMenu(player, payload.substring(0, marker), parseInt(payload.substring(marker + 1), 0));
            } else {
                openChairApplicationsMenu(player, payload, 0);
            }
            return;
        }
        if (action.startsWith("chair:ballots:")) {
            String payload = action.substring("chair:ballots:".length());
            int marker = payload.lastIndexOf(':');
            if (marker > 0 && payload.substring(marker + 1).matches("-?\\d+")) {
                openChairBallotsMenu(player, payload.substring(0, marker), parseInt(payload.substring(marker + 1), 0));
            } else {
                openChairBallotsMenu(player, payload, 0);
            }
            return;
        }
        if (action.startsWith("chair:station:")) {
            openChairStationMenu(player, action.substring("chair:station:".length()), 0);
            return;
        }
        if (action.startsWith("chair:issue-seal:")) {
            String stationId = action.substring("chair:issue-seal:".length());
            requireChairAccess(player, stationId);
            issueOrRefreshSeal(player, stationId, player.getName(), true);
            player.sendMessage(color("&aПечать ЦИК обновлена и снова привязана к тебе."));
            openChairStationMenu(player, stationId, 0);
            return;
        }
        if (action.startsWith("chair:annul-ballot:")) {
            String ballotId = action.substring("chair:annul-ballot:".length());
            openConfirmationMenu(player, "&cАннулировать бюллетень", List.of(
                    "&7Текущий бюллетень игрока будет списан и помечен как недействительный.",
                    "&7После этого председатель сможет выдать новый бюллетень."
            ), "apply:chair:annul-ballot:" + holder.contextId() + ":" + ballotId, "chair:ballots:" + holder.contextId() + ":0");
            return;
        }
        if (action.startsWith("vote:confirm:")) {
            String[] parts = action.split(":");
            if (parts.length >= 4) {
                openConfirmationMenu(player, "&aПодтвердить выбор", List.of(
                        "&7После подтверждения изменить голос больше нельзя.",
                        "&7Голос будет засчитан только после сдачи бюллетеня в участок."
                ), "apply:vote:confirm:" + parts[2] + ":" + parts[3], "close");
            }
            return;
        }
        if (action.startsWith("vote:view-program:")) {
            String appId = action.substring("vote:view-program:".length());
            openApplicationBook(player, appId);
            return;
        }
        if (action.equals("mandate:law")) {
            startPrompt(player, new PromptContext(PromptKind.NEW_LAW, "", "", false), "&eНапиши текст закона одним сообщением. Лимит: &f" + LAW_TEXT_LIMIT + "&e символов.");
            player.closeInventory();
            return;
        }
        if (action.startsWith("mandate:replace-law:")) {
            startPrompt(player, new PromptContext(PromptKind.REPLACE_LAW, action.substring("mandate:replace-law:".length()), "", false), "&eНапиши новый текст закона одним сообщением. Лимит: &f" + LAW_TEXT_LIMIT + "&e символов.");
            player.closeInventory();
            return;
        }
        if (action.equals("mandate:broadcast:chat")) {
            startPrompt(player, new PromptContext(PromptKind.BROADCAST, "CHAT", "", false), "&eНапиши обращение президентом. Лимит: &f" + BROADCAST_TEXT_LIMIT + "&e символов.");
            player.closeInventory();
            return;
        }
        if (action.equals("mandate:broadcast:title")) {
            startPrompt(player, new PromptContext(PromptKind.BROADCAST, "TITLE", "", false), "&eНапиши обращение президентом. Лимит: &f" + BROADCAST_TEXT_LIMIT + "&e символов.");
            player.closeInventory();
            return;
        }
        if (action.equals("mandate:broadcast:actionbar")) {
            startPrompt(player, new PromptContext(PromptKind.BROADCAST, "ACTIONBAR", "", false), "&eНапиши обращение президентом. Лимит: &f" + BROADCAST_TEXT_LIMIT + "&e символов.");
            player.closeInventory();
            return;
        }
        if (action.startsWith("mandate:tax:")) {
            int amount = parseInt(action.substring("mandate:tax:".length()), 0);
            openConfirmationMenu(player, "&6Установить налог", List.of(
                    "&7Новый размер налога: &f" + Math.max(0, Math.min(50, amount)) + " AR",
                    "&7Оплаты будут идти на личный счёт президента."
            ), "apply:mandate:tax:" + amount, "president:open-mandate");
            return;
        }
        if (action.equals("mandate:payments")) {
            openPresidentPaymentsMenu(player, 0);
            return;
        }
        if (action.startsWith("taxpay:bank:")) {
            openTaxOfficeMenu(player, action.substring("taxpay:bank:".length()), "BANK_PIN", null);
            return;
        }
        if (action.startsWith("taxpay:cash:")) {
            openTaxOfficeMenu(player, action.substring("taxpay:cash:".length()), "", null);
            return;
        }
        if (action.startsWith("taxpin:")) {
            openTaxOfficeMenu(player, holder.contextId(), "", null);
            return;
        }
    }

    private void handlePrompt(Player player, PromptContext prompt, String message) {
        if (message.isBlank()) {
            player.sendMessage(color("&cТекст пустой. Действие отменено."));
            return;
        }
        try {
            switch (prompt.kind()) {
                case NEW_LAW -> {
                    if (message.length() > LAW_TEXT_LIMIT) {
                        player.sendMessage(color("&cЗакон слишком длинный."));
                        return;
                    }
                    submitLawForReview(player, message, "");
                    player.sendMessage(color("&aЗакон отправлен на проверку администрации."));
                    openPresidentMandateMenu(player);
                }
                case REPLACE_LAW -> {
                    if (message.length() > LAW_TEXT_LIMIT) {
                        player.sendMessage(color("&cЗакон слишком длинный."));
                        return;
                    }
                    submitLawForReview(player, message, prompt.value1());
                    player.sendMessage(color("&aЗамена закона отправлена на проверку."));
                    openPresidentMandateMenu(player);
                }
                case BROADCAST -> {
                    if (message.length() > BROADCAST_TEXT_LIMIT) {
                        player.sendMessage(color("&cПрезидентское обращение слишком длинное."));
                        return;
                    }
                    sendPresidentBroadcast(player, prompt.value1(), message);
                    openPresidentMandateMenu(player);
                }
            }
        } catch (Exception error) {
            player.sendMessage(color("&cНе удалось завершить действие. Подробности в логе."));
            getLogger().warning("prompt " + prompt.kind() + ": " + safeError(error));
        }
    }

    private void openElectionRoot(Player player, int page) {
        if (!hasElectionAdmin(player)) {
            player.sendMessage(color("&cНет прав на раздел выборов."));
            return;
        }
        LiveSnapshot snap = snapshot.get();
        MenuHolder holder = new MenuHolder("root", "");
        Inventory inv = holder.create(54, color("&6Выборы CopiMine"));
        setButton(holder, 10, Material.GOLDEN_HELMET, "&6Управление", List.of(
                "&7Текущий этап: &f" + snap.stageTitle(),
                "&7Текущий тур: &f" + snap.round(),
                "&7Кандидатов: &f" + snap.candidates().size(),
                "&7Участков: &f" + snap.stationCount()
        ), "open:manage");
        setButton(holder, 12, Material.LECTERN, "&eУчастки", List.of("&7Защищённые блоки участков и председатели."), "open:stations");
        setButton(holder, 14, Material.NAME_TAG, "&bЦИК", List.of("&7Печати, список председателей и выдача новых печатей."), "open:cik");
        setButton(holder, 16, Material.WRITABLE_BOOK, "&6Заявки", List.of("&7Нерассмотренные, рекомендованные и отклонённые."), "open:applications");
        setButton(holder, 28, Material.PAPER, "&aРезультаты", List.of("&7Подсчёт тура, ручной выбор победителя и запуск второго тура."), "open:results");
        setButton(holder, 30, Material.NETHER_STAR, "&dПрезидент", List.of("&7Мандат, законы, доход из AR-лавки и президентский срок."), "open:president");
        setButton(holder, 32, Material.MAP, "&bLive-панель", List.of(
                "&7Этап: &f" + snap.stageTitle(),
                "&7Президент: &f" + first(snap.presidentName(), "нет"),
                "&7Команда игрока: &f/hidelive"
        ), "open:live");
        setButton(holder, 49, Material.ARROW, "&aНазад", List.of("&7Вернуться в общий админ-хаб."), "back:hub");
        player.openInventory(inv);
    }

    private void openManagementMenu(Player player) {
        LiveSnapshot snap = snapshot.get();
        MenuHolder holder = new MenuHolder("manage", "");
        Inventory inv = holder.create(54, color("&6Управление выборами"));
        setStatic(inv, 4, infoItem(Material.PAPER, "&fСводка", List.of(
                "&7Этап: &f" + snap.stageTitle(),
                "&7Тур: &f" + snap.round(),
                "&7Кандидатов: &f" + snap.candidates().size() + " / " + formatLimit(snap.candidateLimit()),
                "&7Участков: &f" + snap.stationCount(),
                "&7Президент: &f" + first(snap.presidentName(), "нет")
        )));
        setButton(holder, 10, Material.LIME_WOOL, "&aНачать выборы", List.of("&7Создать новый цикл и перевести его в подготовку."), "manage:start");
        setButton(holder, 11, Material.RED_WOOL, "&cОстановить выборы", List.of("&7Остановить только выборный цикл без касания других систем."), "manage:stop");
        setButton(holder, 13, Material.CHEST, "&eПодготовка", List.of("&7Этап подготовки участков и лимитов."), "stage:PREPARATION");
        setButton(holder, 14, Material.WRITABLE_BOOK, "&eПриём заявок", List.of("&7Открыть выдачу и сдачу заявок."), "stage:APPLICATIONS");
        setButton(holder, 15, Material.BOOKSHELF, "&eПроверка заявок", List.of("&7Проверка ЦИК и администрацией."), "stage:REVIEW");
        setButton(holder, 16, Material.JUKEBOX, "&eДебаты", List.of("&7Публичный этап перед бюллетенями."), "stage:DEBATES");
        setButton(holder, 19, Material.FEATHER, "&eГолосование", List.of("&7Активировать бюллетени для игроков."), "stage:VOTING");
        setButton(holder, 20, Material.CALCITE, "&eПодсчёт", List.of("&7Подсчитать подтверждённые и сданные бюллетени."), "stage:COUNTING");
        setButton(holder, 21, Material.COMPARATOR, "&eВторой тур", List.of("&7Перейти ко второму туру при ничьей."), "stage:SECOND_ROUND");
        setButton(holder, 22, Material.BEACON, "&eЗавершить", List.of("&7Завершить выборы и открыть президентский срок."), "stage:FINISHED");
        setButton(holder, 24, Material.HOPPER, "&bЛимит кандидатов", buildLimitLore(snap.candidateLimit()), "none");
        int slot = 25;
        for (int value : CANDIDATE_LIMITS) {
            setButton(holder, slot++, Material.LIGHT_BLUE_STAINED_GLASS_PANE, "&f" + formatLimitLabel(value), List.of("&7Установить лимит."), "manage:limit:" + value);
        }
        setButton(holder, 37, Material.CLOCK, "&dСрок президента", buildTermLore(snap.termDays()), "none");
        int daySlot = 38;
        for (int days : TERM_DAYS) {
            setButton(holder, daySlot++, Material.CLOCK, "&f" + days + " дн.", List.of("&7Установить длительность срока."), "manage:term:" + days);
        }
        setButton(holder, 47, Material.BARRIER, "&cСбросить выборы", List.of("&7Очистить только выборные данные.", "&7Банк, AR, сайт и другие модули не трогаются."), "manage:reset:confirm");
        setButton(holder, 49, Material.ARROW, "&aНазад", List.of("&7Вернуться в раздел выборов."), "open:root");
        player.openInventory(inv);
    }

    private void openResetConfirmMenu(Player player) {
        MenuHolder holder = new MenuHolder("reset-confirm", "");
        Inventory inv = holder.create(27, color("&cСброс выборов"));
        setStatic(inv, 13, infoItem(Material.BARRIER, "&cПодтверждение сброса", List.of(
                "&7Будут очищены только выборные таблицы и защищённые выборные блоки.",
                "&7Банк, AR, сайт, игроки и другие модули не трогаются."
        )));
        setButton(holder, 11, Material.ORANGE_WOOL, "&6Очистить только выборы", List.of("&7Перейти к финальному подтверждению."), "manage:reset:only-elections");
        setButton(holder, 15, Material.ARROW, "&aНазад", List.of("&7Вернуться в управление."), "open:manage");
        player.openInventory(inv);
    }

    private void openResetApplyMenu(Player player) {
        MenuHolder holder = new MenuHolder("reset-apply", "");
        Inventory inv = holder.create(27, color("&4Подтвердить сброс"));
        setStatic(inv, 13, infoItem(Material.RED_WOOL, "&4Подтвердить сброс", List.of(
                "&7История выборов не архивируется.",
                "&7После подтверждения модуль начнёт с чистого листа."
        )));
        setButton(holder, 11, Material.RED_WOOL, "&cПодтвердить сброс", List.of("&7Очистить только выборный контур."), "manage:reset:apply");
        setButton(holder, 15, Material.ARROW, "&aНазад", List.of("&7Вернуться к предыдущему шагу."), "manage:reset:confirm");
        player.openInventory(inv);
    }

    private void openConfirmationMenu(Player player, String title, List<String> lines, String applyAction, String backAction) {
        MenuHolder holder = new MenuHolder("confirm", "");
        holder.data().put("apply_action", applyAction);
        holder.data().put("back_action", backAction);
        Inventory inv = holder.create(27, color(title));
        setStatic(inv, 13, infoItem(Material.PAPER, "&fПодтверждение", lines));
        setButton(holder, 11, Material.LIME_WOOL, "&aПодтвердить", List.of("&7Выполнить действие."), "confirm:apply");
        setButton(holder, 15, Material.ARROW, "&aНазад", List.of("&7Вернуться без изменений."), "confirm:back");
        player.openInventory(inv);
    }

    private void openStationsMenu(Player player, int page) throws Exception {
        List<Map<String, Object>> stations = queryList(
                "SELECT id,world,x,y,z,chair_name,chair_uuid,active,text_display_uuid FROM polling_stations WHERE active=1 ORDER BY created_at DESC"
        );
        MenuHolder holder = new MenuHolder("stations", "");
        Inventory inv = holder.create(54, color("&eУчастки ЦИК"));
        setButton(holder, 10, Material.EMERALD_BLOCK, "&aСоздать участок по блоку", List.of("&7Смотри на блок и создай на нём новый участок."), "stations:create");
        int start = Math.max(0, page) * 21;
        int[] slots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        for (int i = 0; i < slots.length && start + i < stations.size(); i++) {
            Map<String, Object> station = stations.get(start + i);
            String id = string(station.get("id"));
            setButton(holder, slots[i], Material.LECTERN, "&fУчасток ЦИК", stationLore(station, id), "station:view:" + id);
        }
        pageButtons(holder, inv, page, stations.size(), 21, "open:stations:" + (page - 1), "open:stations:" + (page + 1), "open:root");
        player.openInventory(inv);
    }

    private void openStationCard(Player player, String stationId) throws Exception {
        Map<String, Object> station = stationById(stationId);
        if (station == null) {
            player.sendMessage(color("&cУчасток не найден."));
            openStationsMenu(player, 0);
            return;
        }
        MenuHolder holder = new MenuHolder("station-card", stationId);
        Inventory inv = holder.create(45, color("&eКарточка участка"));
        boolean adminAccess = hasElectionAdmin(player);
        String chairUuid = string(station.get("chair_uuid"));
        String chairName = first(string(station.get("chair_name")), "");
        boolean hasChair = !chairUuid.isBlank() && !chairName.isBlank();
        setStatic(inv, 4, infoItem(Material.LECTERN, "&fУчасток ЦИК", stationLore(station, stationId)));
        setButton(holder, 20, Material.ENDER_PEARL, "&bТелепортироваться", List.of("&7Быстрый переход к участку."), "station:teleport:" + stationId);
        if (adminAccess) {
            setButton(holder, 21, Material.NAME_TAG, "&eНазначить председателя", List.of("&7Выбрать игрока для этого участка."), "station:assign-chair:" + stationId);
            if (hasChair) {
                setButton(holder, 22, Material.REDSTONE_BLOCK, "&cРазжаловать председателя", List.of(
                        "&7Снять председателя с участка.",
                        "&7Печать ЦИК будет отозвана автоматически."
                ), "station:remove-chair:" + stationId);
            } else {
                setStatic(inv, 22, infoItem(Material.LIME_STAINED_GLASS_PANE, "&aПредседатель не назначен", List.of("&7На этом участке пока нет председателя ЦИК.")));
            }
            setButton(holder, 23, Material.BRUSH, "&dОчистить лишние надписи рядом", List.of("&7Удалить только связанные TextDisplay рядом с участком."), "station:cleanup-labels:" + stationId);
            setStatic(inv, 24, infoItem(Material.PAPER, "&fСводка участка", stationStatsLore(stationId)));
            setButton(holder, 31, Material.WRITABLE_BOOK, "&eВыдать игроку", List.of("&7Выдать заявку или бюллетень выбранному игроку."), "station:issue-target:" + stationId);
            setButton(holder, 32, Material.BARRIER, "&cСнять защиту и удалить участок", List.of("&7Блок участка будет отвязан и удалён из защиты.", "&7Голоса и заявки в журнале сохранятся."), "station:remove-protection:" + stationId);
        } else if (!hasChair) {
            setButton(holder, 21, Material.NAME_TAG, "&aСтать председателем ЦИК", List.of(
                    "&7Подтвердить назначение себя председателем этого участка.",
                    "&7После подтверждения ты получишь активную печать ЦИК."
            ), "station:self-chair:" + stationId);
            setStatic(inv, 22, infoItem(Material.LIME_STAINED_GLASS_PANE, "&aПредседатель не назначен", List.of("&7Этот участок пока свободен.")));
        } else {
            setStatic(inv, 21, infoItem(Material.NAME_TAG, "&fПредседатель: &e" + chairName, List.of(
                    "&7Игрок уже назначен председателем этого участка.",
                    "&7Сменить его может администратор."
            )));
        }
        setStatic(inv, 24, infoItem(Material.PAPER, "&fСводка участка", stationStatsLore(stationId)));
        setButton(holder, 40, Material.ARROW, "&aНазад", List.of("&7К списку участков."), "open:stations");
        player.openInventory(inv);
    }

    private void openIssueTargetPicker(Player player, String stationId, int page) throws Exception {
        if (!hasElectionAdmin(player)) {
            throw new IllegalStateException("Недостаточно прав для выдачи выборных предметов.");
        }
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        onlinePlayers.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        MenuHolder holder = new MenuHolder("issue-target-picker", stationId);
        Inventory inv = holder.create(54, color("&eВыдать игроку"));
        int start = Math.max(0, page) * 21;
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        for (int i = 0; i < slots.length && start + i < onlinePlayers.size(); i++) {
            Player target = onlinePlayers.get(start + i);
            setButton(holder, slots[i], Material.PLAYER_HEAD, "&f" + target.getName(), List.of(
                    "&7UUID: &f" + shortId(target.getUniqueId().toString()),
                    "&7Нажми, чтобы выбрать игрока."
            ), "issuepicker:" + stationId + ":" + target.getUniqueId());
        }
        pageButtons(holder, inv, page, onlinePlayers.size(), 21,
                "station:issue-target:" + stationId + ":page:" + (page - 1),
                "station:issue-target:" + stationId + ":page:" + (page + 1),
                "station:view:" + stationId);
        player.openInventory(inv);
    }

    private void openIssueOptionsMenu(Player player, String stationId, String targetUuid) {
        Player target = Bukkit.getPlayer(UUID.fromString(targetUuid));
        if (target == null) {
            player.sendMessage(color("&cИгрок должен быть онлайн."));
            return;
        }
        MenuHolder holder = new MenuHolder("issue-options", stationId);
        Inventory inv = holder.create(27, color("&eВыдать игроку"));
        setStatic(inv, 13, infoItem(Material.NAME_TAG, "&f" + target.getName(), List.of(
                "&7Участок: &f" + shortId(stationId),
                "&7Игрок: &f" + target.getName()
        )));
        setButton(holder, 11, Material.WRITABLE_BOOK, "&eВыдать заявку", List.of("&7Личная книга-заявка для выбранного игрока."), "admin:issue-application:" + stationId + ":" + targetUuid);
        setButton(holder, 15, Material.PAPER, "&eВыдать бюллетень", List.of("&7Личный бюллетень текущего тура."), "admin:issue-ballot:" + stationId + ":" + targetUuid);
        setButton(holder, 22, Material.ARROW, "&aНазад", List.of("&7Вернуться к выбору игрока."), "station:issue-target:" + stationId);
        player.openInventory(inv);
    }

    private void openChairPicker(Player player, String stationId, int page) {
        List<PlayerChoice> choices = knownPlayers();
        MenuHolder holder = new MenuHolder("chair-picker", stationId);
        Inventory inv = holder.create(54, color("&bВыбор председателя ЦИК"));
        int start = Math.max(0, page) * 21;
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        for (int i = 0; i < slots.length && start + i < choices.size(); i++) {
            PlayerChoice choice = choices.get(start + i);
            setButton(holder, slots[i], Material.PLAYER_HEAD, "&f" + choice.name(), List.of(
                    "&7UUID: &f" + shortId(choice.uuid()),
                    "&7Статус: " + (choice.online() ? "&aонлайн" : "&7офлайн"),
                    "&7Нажми, чтобы назначить."
            ), "chairpicker:" + stationId + ":" + choice.uuid() + ":" + choice.name());
        }
        pageButtons(holder, inv, page, choices.size(), 21, "station:assign-chair:" + stationId + ":page:" + (page - 1), "station:assign-chair:" + stationId + ":page:" + (page + 1), "station:view:" + stationId);
        player.openInventory(inv);
    }

    private void openCikMenu(Player player, int page) throws Exception {
        List<Map<String, Object>> chairs = queryList("SELECT station_id,player_uuid,player_name,assigned_at,active FROM cik_chairs WHERE active=1 ORDER BY assigned_at DESC");
        MenuHolder holder = new MenuHolder("cik", "");
        Inventory inv = holder.create(54, color("&bЦИК"));
        setButton(holder, 10, Material.NAME_TAG, "&eПредседатели участков", List.of("&7Назначение председателей делается из карточки участка."), "none");
        setButton(holder, 12, Material.WAXED_COPPER_GRATE, "&cУничтожить все печати ЦИК", List.of(
                "&7Отозвать все активные печати участков.",
                "&7Онлайн-инвентари тоже будут очищены."
        ), "cik:revoke-all");
        int start = Math.max(0, page) * 21;
        int[] slots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        for (int i = 0; i < slots.length && start + i < chairs.size(); i++) {
            Map<String, Object> row = chairs.get(start + i);
            String stationId = string(row.get("station_id"));
            String playerName = string(row.get("player_name"));
            List<String> lore = new ArrayList<>();
            lore.add("&7Участок: &f" + shortId(stationId));
            lore.add("&7Выдана: &f" + formatTs(longValue(row.get("assigned_at"))));
            lore.add("&7Нажми, чтобы открыть участок.");
            setButton(holder, slots[i], Material.PLAYER_HEAD, "&f" + first(playerName, "без имени"), lore, "station:view:" + stationId);
        }
        pageButtons(holder, inv, page, chairs.size(), 21, "open:cik:" + (page - 1), "open:cik:" + (page + 1), "open:root");
        player.openInventory(inv);
    }

    private void openApplicationsMenu(Player player, String filter, int page) throws Exception {
        List<Map<String, Object>> rows;
        if ("RECOMMENDED".equalsIgnoreCase(filter)) {
            rows = queryList(
                    "SELECT id,player_name,station_id,chair_recommendation,admin_status,submitted_at FROM candidate_applications WHERE admin_status='PENDING' AND chair_recommendation='RECOMMEND' ORDER BY submitted_at DESC"
            );
        } else if ("NOT_RECOMMENDED".equalsIgnoreCase(filter)) {
            rows = queryList(
                    "SELECT id,player_name,station_id,chair_recommendation,admin_status,submitted_at FROM candidate_applications WHERE admin_status='PENDING' AND chair_recommendation='NOT_RECOMMEND' ORDER BY submitted_at DESC"
            );
        } else {
            rows = queryList(
                    "SELECT id,player_name,station_id,chair_recommendation,admin_status,submitted_at FROM candidate_applications WHERE admin_status=? ORDER BY submitted_at DESC",
                    filter
            );
        }
        MenuHolder holder = new MenuHolder("applications", filter);
        Inventory inv = holder.create(54, color("&6Заявки кандидатов"));
        setButton(holder, 10, Material.WRITABLE_BOOK, "&fНерассмотренные", List.of("&7Показать заявки со статусом PENDING."), "open:applications:PENDING:0");
        setButton(holder, 11, Material.LIME_DYE, "&fРекомендованы ЦИК", List.of("&7Показать заявки с положительной рекомендацией."), "open:applications:RECOMMENDED:0");
        setButton(holder, 12, Material.GRAY_DYE, "&fНе рекомендованы", List.of("&7Показать заявки с отрицательной пометкой ЦИК."), "open:applications:NOT_RECOMMENDED:0");
        setButton(holder, 13, Material.EMERALD, "&fОдобрены", List.of("&7Показать одобренные заявки."), "open:applications:APPROVED:0");
        setButton(holder, 14, Material.REDSTONE, "&fОтклонены", List.of("&7Показать отклонённые заявки."), "open:applications:REJECTED:0");
        int start = Math.max(0, page) * 21;
        int[] slots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        for (int i = 0; i < slots.length && start + i < rows.size(); i++) {
            Map<String, Object> row = rows.get(start + i);
            String id = string(row.get("id"));
            setButton(holder, slots[i], Material.BOOK, "&f" + first(string(row.get("player_name")), "Игрок"), List.of(
                    "&7Участок: &f" + shortId(string(row.get("station_id"))),
                    "&7ЦИК: &f" + humanRecommendation(string(row.get("chair_recommendation"))),
                    "&7Админ: &f" + humanApplicationStatus(string(row.get("admin_status"))),
                    "&7Нажми, чтобы открыть."
            ), "application:view:" + id);
        }
        pageButtons(holder, inv, page, rows.size(), 21, "open:applications:" + filter + ":" + (page - 1), "open:applications:" + filter + ":" + (page + 1), "open:root");
        player.openInventory(inv);
    }

    private void openApplicationDetail(Player player, String applicationId) throws Exception {
        Map<String, Object> app = applicationById(applicationId);
        if (app == null) {
            player.sendMessage(color("&cЗаявка не найдена."));
            openApplicationsMenu(player, "PENDING", 0);
            return;
        }
        MenuHolder holder = new MenuHolder("application-detail", applicationId);
        Inventory inv = holder.create(45, color("&6Карточка заявки"));
        setStatic(inv, 4, infoItem(Material.BOOK, "&f" + first(string(app.get("player_name")), "Игрок"), List.of(
                "&7Участок: &f" + shortId(string(app.get("station_id"))),
                "&7ЦИК: &f" + humanRecommendation(string(app.get("chair_recommendation"))),
                "&7Админ: &f" + humanApplicationStatus(string(app.get("admin_status"))),
                "&7Сдана: &f" + formatTs(longValue(app.get("submitted_at")))
        )));
        setButton(holder, 20, Material.WRITTEN_BOOK, "&eОткрыть книгу", List.of("&7Посмотреть ответы кандидата."), "vote:view-program:" + applicationId);
        setButton(holder, 22, Material.LIME_WOOL, "&aОдобрить", List.of("&7Игрок станет кандидатом."), "application:approve:" + applicationId);
        setButton(holder, 24, Material.RED_WOOL, "&cОтклонить", List.of("&7Игрок не станет кандидатом."), "application:reject:" + applicationId);
        setButton(holder, 40, Material.ARROW, "&aНазад", List.of("&7К списку заявок."), "open:applications");
        player.openInventory(inv);
    }

    private void openResultsMenu(Player player) {
        LiveSnapshot snap = snapshot.get();
        MenuHolder holder = new MenuHolder("results", "");
        Inventory inv = holder.create(54, color("&aРезультаты выборов"));
        setStatic(inv, 4, infoItem(Material.PAPER, "&fСводка тура", List.of(
                "&7Этап: &f" + snap.stageTitle(),
                "&7Тур: &f" + snap.round(),
                "&7Сдано бюллетеней: &f" + snap.depositedBallots(),
                "&7Кандидатов: &f" + snap.candidates().size()
        )));
        setButton(holder, 10, Material.CALCITE, "&eПодсчитать текущий тур", List.of("&7Учесть только сданные подтверждённые бюллетени."), "results:count");
        if (snap.secondRoundNeeded()) {
            setButton(holder, 12, Material.COMPARATOR, "&6Открыть второй тур", List.of("&7Во второй тур перейдут только лидеры с равным максимумом голосов."), "results:second-round");
        }
        int slot = 19;
        for (CandidateResult result : snap.candidates()) {
            setButton(holder, slot++, Material.PLAYER_HEAD, "&f" + result.name(), List.of(
                    "&7Голоса: &f" + result.votes(),
                    "&7Полоса: &f" + result.bar(),
                    "&7Нажми, чтобы выбрать победителя вручную."
            ), "results:winner:" + result.uuid());
            if (slot == 26) {
                slot = 28;
            }
        }
        setButton(holder, 49, Material.ARROW, "&aНазад", List.of("&7К разделу выборов."), "open:root");
        player.openInventory(inv);
    }

    private void openPresidentAdminMenu(Player player) {
        LiveSnapshot snap = snapshot.get();
        MenuHolder holder = new MenuHolder("president-admin", "");
        Inventory inv = holder.create(54, color("&d\u041f\u0440\u0435\u0437\u0438\u0434\u0435\u043d\u0442"));
        setStatic(inv, 4, infoItem(Material.NETHER_STAR, "&f\u041f\u0440\u0435\u0437\u0438\u0434\u0435\u043d\u0442", List.of(
                "&7\u0422\u0435\u043a\u0443\u0449\u0438\u0439: &f" + first(snap.presidentName(), "\u043d\u0435\u0442"),
                "&7\u0421\u0440\u043e\u043a: &f" + snap.termDays() + " \u0434\u043d.",
                "&7\u0410\u043a\u0442\u0438\u0432\u043d\u044b\u0445 \u0437\u0430\u043a\u043e\u043d\u043e\u0432: &f" + snap.laws().size(),
                "&7\u0418\u0441\u0442\u043e\u0447\u043d\u0438\u043a \u0434\u043e\u0445\u043e\u0434\u0430: &fAR-\u043b\u0430\u0432\u043a\u0430 CopiMine"
        )));
        setButton(holder, 10, Material.BOOK, "&e\u041e\u0442\u043a\u0440\u044b\u0442\u044c \u043c\u0430\u043d\u0434\u0430\u0442 \u043f\u0440\u0435\u0437\u0438\u0434\u0435\u043d\u0442\u0430", List.of("&7\u041e\u0442\u043a\u0440\u044b\u0442\u044c \u0438\u043d\u0442\u0435\u0440\u0444\u0435\u0439\u0441 \u043f\u0440\u0435\u0437\u0438\u0434\u0435\u043d\u0442\u0430."), "president:open-mandate");
        setButton(holder, 12, Material.BARRIER, "&c\u0421\u043d\u044f\u0442\u044c \u043f\u0440\u0435\u0437\u0438\u0434\u0435\u043d\u0442\u0430", List.of("&7\u041f\u0440\u0435\u0440\u0432\u0430\u0442\u044c \u0442\u0435\u043a\u0443\u0449\u0438\u0439 \u043f\u0440\u0435\u0437\u0438\u0434\u0435\u043d\u0442\u0441\u043a\u0438\u0439 \u0441\u0440\u043e\u043a."), "president:remove");
        setStatic(inv, 14, infoItem(Material.GOLD_INGOT, "&6\u041d\u0430\u043b\u043e\u0433\u0438 \u043e\u0442\u043a\u043b\u044e\u0447\u0435\u043d\u044b", List.of("&7\u0414\u043e\u0445\u043e\u0434 \u043f\u0440\u0435\u0437\u0438\u0434\u0435\u043d\u0442\u0430 \u0442\u0435\u043f\u0435\u0440\u044c \u0438\u0434\u0451\u0442", "&7\u043d\u0430 \u043b\u0438\u0447\u043d\u044b\u0439 \u0441\u0447\u0451\u0442 \u0447\u0435\u0440\u0435\u0437 AR-\u043b\u0430\u0432\u043a\u0443.")));
        int lawSlot = 19;
        for (Map<String, Object> law : pendingLaws()) {
            String lawId = string(law.get("id"));
            int slot = lawSlot++;
            setButton(holder, slot, Material.PAPER, "&f" + shortText(string(law.get("text")), 40), List.of(
                    "&7\u041f\u0440\u0435\u0437\u0438\u0434\u0435\u043d\u0442: &f" + first(string(law.get("president_uuid")), "\u2014"),
                    "&7\u0421\u0442\u0430\u0442\u0443\u0441: &f" + string(law.get("status")),
                    "&7\u041b\u041a\u041c: \u043e\u0434\u043e\u0431\u0440\u0438\u0442\u044c",
                    "&7\u041f\u041a\u041c: \u043e\u0442\u043a\u043b\u043e\u043d\u0438\u0442\u044c"
            ), "law:approve:" + lawId);
            holder.rightActions().put(slot, "law:reject:" + lawId);
        }
        setButton(holder, 40, Material.BOOKSHELF, "&a\u041f\u043e\u0441\u0442\u0443\u043f\u043b\u0435\u043d\u0438\u044f \u0438\u0437 \u043b\u0430\u0432\u043a\u0438", List.of("&7\u041f\u043e\u0441\u043b\u0435\u0434\u043d\u0438\u0435 \u0437\u0430\u0447\u0438\u0441\u043b\u0435\u043d\u0438\u044f \u043f\u0440\u0435\u0437\u0438\u0434\u0435\u043d\u0442\u0443", "&7\u043e\u0442 AR-\u043b\u0430\u0432\u043a\u0438 CopiMine."), "mandate:payments");
        setButton(holder, 49, Material.ARROW, "&a\u041d\u0430\u0437\u0430\u0434", List.of("&7\u041a \u0440\u0430\u0437\u0434\u0435\u043b\u0443 \u0432\u044b\u0431\u043e\u0440\u043e\u0432."), "open:root");
        player.openInventory(inv);
    }

    private void openPresidentMandateMenu(Player player) {
        LiveSnapshot snap = snapshot.get();
        MenuHolder holder = new MenuHolder("president-mandate", "");
        Inventory inv = holder.create(45, color("&d\u041c\u0430\u043d\u0434\u0430\u0442 \u043f\u0440\u0435\u0437\u0438\u0434\u0435\u043d\u0442\u0430"));
        setStatic(inv, 4, infoItem(Material.NETHER_STAR, "&f\u041f\u0440\u0435\u0437\u0438\u0434\u0435\u043d\u0442", List.of(
                "&7\u0414\u0435\u0439\u0441\u0442\u0432\u0443\u044e\u0449\u0438\u0439 \u043f\u0440\u0435\u0437\u0438\u0434\u0435\u043d\u0442: &f" + first(snap.presidentName(), "\u043d\u0435\u0442"),
                "&7\u0417\u0430\u043a\u043e\u043d\u043e\u0432: &f" + snap.laws().size(),
                "&7\u0414\u043e\u0445\u043e\u0434: &fAR-\u043b\u0430\u0432\u043a\u0430 CopiMine"
        )));
        setButton(holder, 10, Material.BOOK, "&e\u041f\u0440\u0435\u0434\u043b\u043e\u0436\u0438\u0442\u044c \u0437\u0430\u043a\u043e\u043d", List.of("&7\u0422\u0435\u043a\u0441\u0442 \u0431\u0443\u0434\u0435\u0442 \u043e\u0442\u043f\u0440\u0430\u0432\u043b\u0435\u043d \u043d\u0430 \u043f\u0440\u043e\u0432\u0435\u0440\u043a\u0443 \u0430\u0434\u043c\u0438\u043d\u0438\u0441\u0442\u0440\u0430\u0446\u0438\u0438."), "mandate:law");
        int replaceSlot = 11;
        for (Map<String, Object> law : publishedLaws()) {
            setButton(holder, replaceSlot++, Material.PAPER, "&f\u0417\u0430\u043c\u0435\u043d\u0438\u0442\u044c: " + shortText(string(law.get("text")), 22), List.of("&7\u0417\u0430\u043c\u0435\u043d\u0430 \u0434\u043e\u0441\u0442\u0443\u043f\u043d\u0430 \u043d\u0435 \u0447\u0430\u0449\u0435 \u0440\u0430\u0437\u0430 \u0432 3 \u0434\u043d\u044f."), "mandate:replace-law:" + string(law.get("id")));
        }
        setButton(holder, 19, Material.PAPER, "&e\u041e\u0431\u0440\u0430\u0449\u0435\u043d\u0438\u0435 \u0432 \u0447\u0430\u0442", List.of("&7\u041e\u0442\u043f\u0440\u0430\u0432\u0438\u0442\u044c \u0438\u0433\u0440\u043e\u043a\u0430\u043c \u0447\u0435\u0440\u0435\u0437 \u0447\u0430\u0442."), "mandate:broadcast:chat");
        setButton(holder, 20, Material.BELL, "&e\u041e\u0431\u0440\u0430\u0449\u0435\u043d\u0438\u0435 \u043d\u0430 \u044d\u043a\u0440\u0430\u043d", List.of("&7\u041f\u043e\u043a\u0430\u0437\u0430\u0442\u044c \u0438\u0433\u0440\u043e\u043a\u0430\u043c \u043d\u0430 \u044d\u043a\u0440\u0430\u043d\u0435."), "mandate:broadcast:title");
        setButton(holder, 21, Material.CLOCK, "&e\u041e\u0431\u0440\u0430\u0449\u0435\u043d\u0438\u0435 \u0432 ActionBar", List.of("&7\u041f\u043e\u043a\u0430\u0437\u0430\u0442\u044c \u0441\u043e\u043e\u0431\u0449\u0435\u043d\u0438\u0435 \u0432 action bar."), "mandate:broadcast:actionbar");
        setStatic(inv, 31, infoItem(Material.GOLD_INGOT, "&6\u041d\u0430\u043b\u043e\u0433\u0438 \u043e\u0442\u043a\u043b\u044e\u0447\u0435\u043d\u044b", List.of("&7\u041f\u0440\u0435\u0437\u0438\u0434\u0435\u043d\u0442 \u043f\u043e\u043b\u0443\u0447\u0430\u0435\u0442 \u0432\u044b\u0440\u0443\u0447\u043a\u0443", "&7\u043d\u0430 \u043b\u0438\u0447\u043d\u044b\u0439 \u0441\u0447\u0451\u0442 \u0438\u0437 AR-\u043b\u0430\u0432\u043a\u0438.")));
        setButton(holder, 40, Material.BOOKSHELF, "&a\u041f\u043e\u0441\u0442\u0443\u043f\u043b\u0435\u043d\u0438\u044f \u0438\u0437 \u043b\u0430\u0432\u043a\u0438", List.of("&7\u041f\u043e\u0441\u043b\u0435\u0434\u043d\u0438\u0435 \u0437\u0430\u0447\u0438\u0441\u043b\u0435\u043d\u0438\u044f \u043f\u0440\u0435\u0437\u0438\u0434\u0435\u043d\u0442\u0443."), "mandate:payments");
        setButton(holder, 44, Material.BARRIER, "&c\u0417\u0430\u043a\u0440\u044b\u0442\u044c", List.of("&7\u0417\u0430\u043a\u0440\u044b\u0442\u044c \u043c\u0435\u043d\u044e."), "close");
        player.openInventory(inv);
    }

    private void openPresidentPaymentsMenu(Player player, int page) {
        List<Map<String, Object>> rows = currentTaxPayments();
        MenuHolder holder = new MenuHolder("president-payments", "");
        Inventory inv = holder.create(54, color("&a\u041f\u043e\u0441\u0442\u0443\u043f\u043b\u0435\u043d\u0438\u044f \u0438\u0437 \u043b\u0430\u0432\u043a\u0438"));
        int start = Math.max(0, page) * 21;
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        for (int i = 0; i < slots.length && start + i < rows.size(); i++) {
            Map<String, Object> row = rows.get(start + i);
            setStatic(inv, slots[i], infoItem(Material.GOLD_INGOT, "&f" + first(string(row.get("player_name")), "\u0418\u0433\u0440\u043e\u043a"), List.of(
                    "&7\u0421\u0443\u043c\u043c\u0430: &f" + longValue(row.get("amount")) + " AR",
                    "&7\u0418\u0441\u0442\u043e\u0447\u043d\u0438\u043a: &f" + string(row.get("source")),
                    "&7\u0412\u0440\u0435\u043c\u044f: &f" + formatTs(longValue(row.get("created_at")))
            )));
        }
        pageButtons(holder, inv, page, rows.size(), 21, "president:payments:" + (page - 1), "president:payments:" + (page + 1), "president:open-mandate");
        player.openInventory(inv);
    }

    private void openLiveMenu(Player player) {
        LiveSnapshot snap = snapshot.get();
        MenuHolder holder = new MenuHolder("live", "");
        Inventory inv = holder.create(27, color("&bLive-панель"));
        setStatic(inv, 11, infoItem(Material.MAP, "&fПанель", List.of(
                "&7Этап: &f" + snap.stageTitle(),
                "&7Президент: &f" + first(snap.presidentName(), "нет"),
                "&7Законов: &f" + snap.laws().size(),
                "&7Скрыть live-панель можно командой &f/hidelive"
        )));
        setButton(holder, 15, Material.CLOCK, "&aОбновить", List.of("&7Пересобрать snapshot и перерисовать панель."), "live:refresh");
        setButton(holder, 22, Material.ARROW, "&aНазад", List.of("&7К разделу выборов."), "open:root");
        player.openInventory(inv);
    }

    private void openSealTargetMenu(Player player, Player target, SealContext sealContext) {
        if (target == null || sealContext == null || sealContext.stationId() == null || sealContext.stationId().isBlank()) {
            player.sendMessage(color("&cНе удалось определить участок печати ЦИК."));
            return;
        }
        MenuHolder holder = new MenuHolder("seal-target", sealContext.stationId());
        holder.data().put("seal_id", sealContext.sealId());
        holder.data().put("station_id", sealContext.stationId());
        holder.data().put("election_id", sealContext.electionId());
        holder.data().put("chair_uuid", sealContext.playerUuid());
        Inventory inv = holder.create(27, color("&bПечать ЦИК"));
        setStatic(inv, 13, infoItem(Material.NAME_TAG, "&f" + target.getName(), List.of(
                "&7Цель: &f" + target.getName(),
                "&7Участок: &f" + shortId(sealContext.stationId())
        )));
        setButton(holder, 11, Material.WRITABLE_BOOK, "&eВыдать заявку", List.of("&7Личная книга-заявка для этого игрока."), "seal:issue-application:" + target.getName());
        setButton(holder, 15, Material.PAPER, "&eВыдать бюллетень", List.of("&7Личный бюллетень текущего тура."), "seal:issue-ballot:" + target.getName());
        player.openInventory(inv);
    }

    private void openChairStationMenu(Player player, String stationId, int page) throws Exception {
        requireChairAccess(player, stationId);
        MenuHolder holder = new MenuHolder("chair-station", stationId);
        Inventory inv = holder.create(45, color("&bУчасток председателя ЦИК"));
        setButton(holder, 11, Material.BOOK, "&eЗаявки участка", List.of("&7Открыть заявки этого участка."), "chair:applications:" + stationId);
        setButton(holder, 15, Material.PAPER, "&eБюллетени участка", List.of("&7Технический статус бюллетеней без раскрытия выбора."), "chair:ballots:" + stationId);
        boolean hasSeal = hasUsableActiveSeal(player, stationId);
        setButton(holder, 31, hasSeal ? Material.HONEYCOMB_BLOCK : Material.HONEYCOMB, hasSeal ? "&aОбновить печать ЦИК" : "&eПолучить печать ЦИК", List.of(
                hasSeal ? "&7Перевыпустить личную печать этого участка." : "&7Выдать личную печать этого участка себе.",
                "&7Печать выдаётся только явным действием из меню."
        ), "chair:issue-seal:" + stationId);
        setButton(holder, 22, Material.BARRIER, "&cЗакрыть", List.of("&7Закрыть меню."), "close");
        player.openInventory(inv);
    }

    private void openChairApplicationsMenu(Player player, String stationId, int page) throws Exception {
        requireChairAccess(player, stationId);
        List<Map<String, Object>> rows = queryList(
                "SELECT id,player_name,submitted_at,chair_recommendation,admin_status FROM candidate_applications WHERE station_id=? ORDER BY submitted_at DESC,issued_at DESC",
                stationId
        );
        MenuHolder holder = new MenuHolder("chair-applications", stationId);
        Inventory inv = holder.create(54, color("&bЗаявки участка"));
        int start = Math.max(0, page) * 21;
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        for (int i = 0; i < slots.length && start + i < rows.size(); i++) {
            Map<String, Object> row = rows.get(start + i);
            String applicationId = string(row.get("id"));
            setButton(holder, slots[i], Material.WRITTEN_BOOK, "&f" + first(string(row.get("player_name")), "Игрок"), List.of(
                    "&7Сдана: &f" + formatTs(longValue(row.get("submitted_at"))),
                    "&7ЦИК: &f" + humanRecommendation(string(row.get("chair_recommendation"))),
                    "&7Админ: &f" + humanApplicationStatus(string(row.get("admin_status"))),
                    "&7Нажми, чтобы открыть карточку."
            ), "chair:application:view:" + stationId + ":" + applicationId);
        }
        pageButtons(holder, inv, page, rows.size(), 21, "chair:applications:" + stationId + ":" + (page - 1), "chair:applications:" + stationId + ":" + (page + 1), "chair:station:" + stationId);
        player.openInventory(inv);
    }

    private void openChairApplicationDetail(Player player, String stationId, String applicationId) throws Exception {
        requireChairAccess(player, stationId);
        Map<String, Object> app = applicationById(applicationId);
        if (app == null || !Objects.equals(stationId, string(app.get("station_id")))) {
            player.sendMessage(color("&cЗаявка участка не найдена."));
            openChairApplicationsMenu(player, stationId, 0);
            return;
        }
        MenuHolder holder = new MenuHolder("chair-application-detail", stationId);
        Inventory inv = holder.create(45, color("&bКарточка заявки"));
        setStatic(inv, 4, infoItem(Material.BOOK, "&f" + first(string(app.get("player_name")), "Игрок"), List.of(
                "&7Участок: &f" + shortId(stationId),
                "&7ЦИК: &f" + humanRecommendation(string(app.get("chair_recommendation"))),
                "&7Админ: &f" + humanApplicationStatus(string(app.get("admin_status"))),
                "&7Сдана: &f" + formatTs(longValue(app.get("submitted_at")))
        )));
        setButton(holder, 20, Material.WRITTEN_BOOK, "&eОткрыть книгу", List.of("&7Посмотреть ответы кандидата."), "vote:view-program:" + applicationId);
        setButton(holder, 22, Material.LIME_WOOL, "&aРекомендовать", List.of("&7Передать положительную пометку ЦИК."), "chair:application:recommend:" + stationId + ":" + applicationId);
        setButton(holder, 24, Material.RED_WOOL, "&cНе рекомендовать", List.of("&7Передать отрицательную пометку ЦИК."), "chair:application:no-recommend:" + stationId + ":" + applicationId);
        setButton(holder, 40, Material.ARROW, "&aНазад", List.of("&7К списку заявок участка."), "chair:applications:" + stationId + ":0");
        player.openInventory(inv);
    }

    private void openChairBallotsMenu(Player player, String stationId, int page) throws Exception {
        requireChairAccess(player, stationId);
        List<Map<String, Object>> rows = queryList(
                "SELECT id,player_name,status,issued_at,submitted_at FROM ballots WHERE station_id=? ORDER BY issued_at DESC",
                stationId
        );
        MenuHolder holder = new MenuHolder("chair-ballots", stationId);
        Inventory inv = holder.create(54, color("&bБюллетени участка"));
        int start = Math.max(0, page) * 21;
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        for (int i = 0; i < slots.length && start + i < rows.size(); i++) {
            Map<String, Object> row = rows.get(start + i);
            String id = string(row.get("id"));
            String status = string(row.get("status")).toUpperCase(Locale.ROOT);
            List<String> lore = new ArrayList<>();
            lore.add("&7Статус: &f" + status);
            lore.add("&7Выдан: &f" + formatTs(longValue(row.get("issued_at"))));
            lore.add("&7Сдан: &f" + formatTs(longValue(row.get("submitted_at"))));
            if ("ISSUED".equals(status) || "CONFIRMED".equals(status)) {
                lore.add("&7Нажми, чтобы аннулировать.");
                setButton(holder, slots[i], Material.PAPER, "&f" + first(string(row.get("player_name")), "Игрок"), lore, "chair:annul-ballot:" + id);
            } else if ("DEPOSITED".equals(status)) {
                lore.add("&aГолос уже принят.");
                setStatic(inv, slots[i], infoItem(Material.PAPER, "&f" + first(string(row.get("player_name")), "Игрок"), lore));
            } else {
                lore.add("&7Аннулирование недоступно.");
                setStatic(inv, slots[i], infoItem(Material.PAPER, "&f" + first(string(row.get("player_name")), "Игрок"), lore));
            }
        }
        pageButtons(holder, inv, page, rows.size(), 21, "chair:ballots:" + stationId + ":" + (page - 1), "chair:ballots:" + stationId + ":" + (page + 1), "chair:station:" + stationId);
        player.openInventory(inv);
    }

    private void openBallotVoteMenu(Player player, ItemStack ballot) {
        try {
        String ballotId = readString(ballot, ballotIdKey);
        Map<String, Object> ballotRow = ballotById(ballotId);
        if (ballotRow == null || !"ISSUED".equalsIgnoreCase(string(ballotRow.get("status")))) {
            player.sendMessage(color("&cЭтот бюллетень больше недействителен."));
            return;
        }
        try (Connection connection = openConnection()) {
            ElectionContext context = requireActiveElectionContext(connection);
            if (!string(ballotRow.get("election_id")).equals(context.electionId())
                    || context.stage() != ElectionStage.VOTING
                    || intValue(ballotRow.get("round_no")) != context.round()) {
                player.sendMessage(color("&eБюллетень не активен на текущем этапе."));
                return;
            }
        }
        List<Map<String, Object>> candidates = activeCandidates(string(ballotRow.get("election_id")), intValue(ballotRow.get("round_no")));
        MenuHolder holder = new MenuHolder("vote", ballotId);
        Inventory inv = holder.create(54, color("&aБюллетень"));
        int slot = 10;
        for (Map<String, Object> candidate : candidates) {
            String candidateUuid = string(candidate.get("player_uuid"));
            String candidateName = first(string(candidate.get("player_name")), "Кандидат");
            if (candidateUuid.equals(player.getUniqueId().toString())) {
                continue;
            }
            ItemStack head = playerHead(candidateName);
            ItemMeta meta = head.getItemMeta();
            meta.setDisplayName(color("&f" + candidateName));
            meta.setLore(List.of(
                    color("&7ЛКМ: выбрать кандидата"),
                    color("&7ПКМ: открыть программу кандидата")
            ));
            head.setItemMeta(meta);
            holder.actions().put(slot, "vote:confirm:" + ballotId + ":" + candidateUuid);
            inv.setItem(slot, head);
            String appId = candidateApplicationId(candidateUuid);
            if (appId != null) {
                holder.rightActions().put(slot, "vote:view-program:" + appId);
            }
            slot++;
            if (slot == 17) {
                slot = 19;
            }
        }
        setButton(holder, 49, Material.BARRIER, "&cЗакрыть", List.of("&7Закрыть бюллетень."), "close");
        player.openInventory(inv);
        } catch (Exception error) {
            player.sendMessage(color("&cНе удалось открыть бюллетень."));
            getLogger().warning("vote menu: " + safeError(error));
        }
    }

    private void openTaxOfficeMenu(Player player, String taxId, String mode, String pinBuffer) {
        MenuHolder holder = new MenuHolder("tax-office", first(taxId, ""));
        Inventory inv = holder.create(27, color("&6\u041d\u0430\u043b\u043e\u0433\u043e\u0432\u0430\u044f \u043e\u0442\u043a\u043b\u044e\u0447\u0435\u043d\u0430"));
        setStatic(inv, 11, infoItem(Material.GOLD_INGOT, "&f\u041f\u0440\u0435\u0437\u0438\u0434\u0435\u043d\u0442\u0441\u043a\u0438\u0439 \u043d\u0430\u043b\u043e\u0433 \u0443\u0431\u0440\u0430\u043d", List.of(
                "&7\u042d\u0442\u0430 \u043c\u0435\u0445\u0430\u043d\u0438\u043a\u0430 \u0431\u043e\u043b\u044c\u0448\u0435 \u043d\u0435 \u0438\u0441\u043f\u043e\u043b\u044c\u0437\u0443\u0435\u0442\u0441\u044f.",
                "&7\u0414\u043e\u0445\u043e\u0434 \u043f\u0440\u0435\u0437\u0438\u0434\u0435\u043d\u0442\u0430 \u0442\u0435\u043f\u0435\u0440\u044c \u043f\u043e\u0441\u0442\u0443\u043f\u0430\u0435\u0442",
                "&7\u043d\u0430 \u043b\u0438\u0447\u043d\u044b\u0439 \u0441\u0447\u0451\u0442 \u0438\u0437 AR-\u043b\u0430\u0432\u043a\u0438 CopiMine."
        )));
        setButton(holder, 15, Material.BOOKSHELF, "&a\u041f\u043e\u0441\u0442\u0443\u043f\u043b\u0435\u043d\u0438\u044f \u0438\u0437 \u043b\u0430\u0432\u043a\u0438", List.of("&7\u041e\u0442\u043a\u0440\u044b\u0442\u044c \u043f\u043e\u0441\u043b\u0435\u0434\u043d\u0438\u0435 \u0437\u0430\u0447\u0438\u0441\u043b\u0435\u043d\u0438\u044f \u043f\u0440\u0435\u0437\u0438\u0434\u0435\u043d\u0442\u0443."), "mandate:payments");
        setButton(holder, 22, Material.BARRIER, "&c\u0417\u0430\u043a\u0440\u044b\u0442\u044c", List.of("&7\u0417\u0430\u043a\u0440\u044b\u0442\u044c \u044d\u0442\u043e \u043c\u0435\u043d\u044e."), "close");
        player.openInventory(inv);
    }

    private void renderPinPad(MenuHolder holder, Inventory inv, String pin) {
        Map<Integer, Integer> digits = Map.of(
                20, 1,
                21, 2,
                22, 3,
                29, 4,
                30, 5,
                31, 6,
                38, 7,
                39, 8,
                40, 9,
                48, 0
        );
        for (Map.Entry<Integer, Integer> entry : digits.entrySet()) {
            int slot = entry.getKey();
            int digit = entry.getValue();
            inv.setItem(slot, infoItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "&f" + digit, List.of()));
            holder.actions().put(slot, "taxpin:digit:" + digit);
        }
        inv.setItem(13, infoItem(Material.PAPER, "&fВведите PIN", List.of("&7" + maskPin(pin))));
        inv.setItem(23, infoItem(Material.BARRIER, "&cCancel", List.of()));
        holder.actions().put(23, "taxpin:cancel");
        inv.setItem(32, infoItem(Material.ORANGE_WOOL, "&eClear", List.of()));
        holder.actions().put(32, "taxpin:clear");
        inv.setItem(41, infoItem(Material.LIME_WOOL, "&aEnter", List.of()));
        holder.actions().put(41, "taxpin:enter");
    }

    private void handleTaxPinAction(Player player, String action, MenuHolder holder) throws Exception {
        String taxId = holder.contextId();
        String current = first(holder.data().get("pin"), "");
        if (action.equals("taxpin:cancel")) {
            openTaxOfficeMenu(player, taxId, "", null);
            return;
        }
        if (action.equals("taxpin:clear")) {
            openTaxOfficeMenu(player, taxId, "BANK_PIN", "");
            return;
        }
        if (action.startsWith("taxpin:digit:")) {
            if (current.length() >= 8) {
                return;
            }
            openTaxOfficeMenu(player, taxId, "BANK_PIN", current + action.substring("taxpin:digit:".length()));
            return;
        }
        if (action.equals("taxpin:enter")) {
            payTaxFromBank(player, taxId, current);
        }
    }

    private void pageButtons(MenuHolder holder, Inventory inv, int page, int totalRows, int pageSize, String prevAction, String nextAction, String backAction) {
        if (page > 0) {
            setButton(holder, 45, Material.SPECTRAL_ARROW, "&eНазад", List.of("&7Предыдущая страница."), prevAction);
        }
        if ((page + 1) * pageSize < totalRows) {
            setButton(holder, 53, Material.SPECTRAL_ARROW, "&eДальше", List.of("&7Следующая страница."), nextAction);
        }
        setButton(holder, 48, Material.ARROW, "&aК разделу", List.of("&7Вернуться назад."), backAction);
    }

    private void setButton(MenuHolder holder, int slot, Material material, String name, List<String> lore, String action) {
        holder.actions().put(slot, action);
        holder.inventory().setItem(slot, infoItem(material, name, lore));
    }

    private void setStatic(Inventory inventory, int slot, ItemStack item) {
        inventory.setItem(slot, item);
    }

    private ItemStack infoItem(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(color(name));
        List<String> colored = new ArrayList<>();
        for (String line : lore) {
            colored.add(color(line));
        }
        meta.setLore(colored);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack playerHead(String name) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta raw = head.getItemMeta();
        if (raw instanceof SkullMeta meta) {
            OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(name);
            if (offline != null) {
                meta.setOwningPlayer(offline);
            }
            head.setItemMeta(meta);
        }
        return head;
    }

    private void createPollingStationFromTarget(Player player) throws Exception {
        Block target = player.getTargetBlockExact(8);
        if (target == null) {
            player.sendMessage(color("&cСначала посмотри на блок участка."));
            return;
        }
        if (protectedBlockInfo(target) != null) {
            player.sendMessage(color("&eЭтот блок уже защищён."));
            return;
        }
        String electionId = requireActiveElectionId();
        String stationId = "station_" + UUID.randomUUID().toString().replace("-", "");
        long t = now();
        tx(connection -> {
            update(connection, "INSERT INTO polling_stations(id,election_id,world,x,y,z,chair_uuid,chair_name,active,created_at,updated_at,text_display_uuid) VALUES(?,?,?,?,?,?,?,?,1,?,?,?)",
                    stationId, electionId, target.getWorld().getName(), target.getX(), target.getY(), target.getZ(), "", "", t, t, "");
            upsertProtectedBlock(connection, "POLLING_STATION", target.getLocation(), stationId, t);
            logPluginEvent(connection, "election_core", "station_created", player.getName(), stationId, "world=" + target.getWorld().getName());
            return null;
        });
        spawnOrReplaceTextDisplay(target.getLocation(), "Участок ЦИК", "STATION_LABEL", stationId);
        spawnOrReplaceProtectedBlockVisual(target.getLocation(), "POLLING_STATION", stationId, Material.PAPER, MODEL_POLLING_STATION_MARKER, "polling_station_marker");
        reloadProtectedBlocks();
        player.sendMessage(color("&aУчасток создан."));
    }

    private void createTaxOfficeFromTarget(Player player) throws Exception {
        player.sendMessage(color("&e\u041d\u0430\u043b\u043e\u0433\u043e\u0432\u0430\u044f \u043e\u0442\u043a\u043b\u044e\u0447\u0435\u043d\u0430. \u041d\u043e\u0432\u044b\u0435 \u043d\u0430\u043b\u043e\u0433\u043e\u0432\u044b\u0435 \u0431\u043e\u043b\u044c\u0448\u0435 \u043d\u0435 \u0441\u043e\u0437\u0434\u0430\u044e\u0442\u0441\u044f."));
    }

    private void spawnOrReplaceTextDisplay(Location blockLocation, String text, String kind, String linkedId) throws Exception {
        cleanupNearbyTextDisplays(linkedId);
        World world = blockLocation.getWorld();
        if (world == null) {
            return;
        }
        Location displayLocation = blockLocation.clone().add(0.5, 1.4, 0.5);
        TextDisplay display = world.spawn(displayLocation, TextDisplay.class, entity -> {
            entity.text(Component.text(ChatColor.stripColor(color("&f" + text))));
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setSeeThrough(false);
            entity.setShadowed(false);
            entity.setPersistent(true);
            entity.getPersistentDataContainer().set(textTypeKey, PersistentDataType.STRING, kind);
            entity.getPersistentDataContainer().set(textLinkedIdKey, PersistentDataType.STRING, linkedId);
            entity.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1.0f, 1.0f, 1.0f), new AxisAngle4f()));
        });
        if ("STATION_LABEL".equals(kind)) {
            update("UPDATE polling_stations SET text_display_uuid=?,updated_at=? WHERE id=?", display.getUniqueId().toString(), now(), linkedId);
        }
        update("INSERT INTO text_display_links(id,kind,linked_id,world,entity_uuid,text,created_at,active) VALUES(?,?,?,?,?,?,?,1) ON CONFLICT(id) DO UPDATE SET entity_uuid=excluded.entity_uuid,text=excluded.text,active=1",
                kind.toLowerCase(Locale.ROOT) + ":" + linkedId, kind, linkedId, world.getName(), display.getUniqueId().toString(), text, now());
    }

    private void cleanupNearbyTextDisplays(String linkedId) throws Exception {
        Map<String, Object> station = stationById(linkedId);
        Map<String, Object> linkRow = queryOne("SELECT kind,world,entity_uuid FROM text_display_links WHERE linked_id=? ORDER BY created_at DESC LIMIT 1", linkedId);
        String expectedKind = linkRow == null ? (station != null ? "STATION_LABEL" : "TAX_LABEL") : string(linkRow.get("kind"));
        String worldName;
        int x;
        int y;
        int z;
        if (station != null) {
            worldName = string(station.get("world"));
            x = intValue(station.get("x"));
            y = intValue(station.get("y"));
            z = intValue(station.get("z"));
        } else {
            Map<String, Object> link = queryOne("SELECT world,x,y,z FROM protected_blocks WHERE linked_id=? AND active=1 LIMIT 1", linkedId);
            if (link == null) {
                return;
            }
            worldName = string(link.get("world"));
            x = intValue(link.get("x"));
            y = intValue(link.get("y"));
            z = intValue(link.get("z"));
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }
        Location base = new Location(world, x + 0.5, y + 1.4, z + 0.5);
        Set<String> linkedEntityUuids = queryList("SELECT entity_uuid FROM text_display_links WHERE linked_id=?", linkedId).stream()
                .map(row -> string(row.get("entity_uuid")))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());
        for (Entity entity : world.getNearbyEntities(base, 1.6, 2.2, 1.6)) {
            if (entity instanceof TextDisplay display) {
                if (linkedEntityUuids.contains(display.getUniqueId().toString()) || isManagedTextDisplay(display, expectedKind, linkedId, base)) {
                    entity.remove();
                }
            }
        }
        update("UPDATE text_display_links SET active=0 WHERE linked_id=?", linkedId);
        if (station != null) {
            update("UPDATE polling_stations SET text_display_uuid='',updated_at=? WHERE id=?", now(), linkedId);
        }
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

    private void spawnOrReplaceProtectedBlockVisual(
            Location blockLocation,
            String kind,
            String linkedId,
            Material baseMaterial,
            int customModelData,
            String modelId
    ) throws Exception {
        if (!customBlockVisualsEnabled()) {
            cleanupProtectedBlockVisuals(kind, linkedId);
            return;
        }
        cleanupProtectedBlockVisuals(kind, linkedId);
        World world = blockLocation.getWorld();
        if (world == null) {
            return;
        }
        Location displayLocation = blockLocation.clone().add(0.5D, customBlockOffsetY(kind), 0.5D);
        ItemStack visualItem = new ItemStack(baseMaterial);
        ItemMeta meta = visualItem.getItemMeta();
        meta.setDisplayName(color("&f" + modelId));
        meta.setCustomModelData(customModelData);
        meta.addItemFlags(ItemFlag.values());
        visualItem.setItemMeta(meta);
        double scale = customBlockScale(kind);
        ItemDisplay display = world.spawn(displayLocation, ItemDisplay.class, entity -> {
            entity.setItemStack(visualItem);
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setPersistent(true);
            entity.getPersistentDataContainer().set(visualEntityTypeKey, PersistentDataType.STRING, "PROTECTED_BLOCK_VISUAL");
            entity.getPersistentDataContainer().set(visualKindKey, PersistentDataType.STRING, kind);
            entity.getPersistentDataContainer().set(visualLinkedIdKey, PersistentDataType.STRING, linkedId);
            entity.getPersistentDataContainer().set(visualModelIdKey, PersistentDataType.STRING, modelId);
            entity.setTransformation(new Transformation(
                    new Vector3f(),
                    new AxisAngle4f(),
                    new Vector3f((float) scale, (float) scale, (float) scale),
                    new AxisAngle4f()
            ));
        });
        long t = now();
        update(
                "INSERT INTO protected_block_visuals(id,kind,linked_id,world,x,y,z,entity_uuid,base_material,custom_model_data,model_id,offset_x,offset_y,offset_z,scale_x,scale_y,scale_z,yaw,pitch,created_at,updated_at,active) " +
                        "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,1) " +
                        "ON CONFLICT(id) DO UPDATE SET entity_uuid=excluded.entity_uuid,base_material=excluded.base_material,custom_model_data=excluded.custom_model_data,model_id=excluded.model_id,offset_x=excluded.offset_x,offset_y=excluded.offset_y,offset_z=excluded.offset_z,scale_x=excluded.scale_x,scale_y=excluded.scale_y,scale_z=excluded.scale_z,yaw=excluded.yaw,pitch=excluded.pitch,updated_at=excluded.updated_at,active=1",
                kind.toLowerCase(Locale.ROOT) + ":" + linkedId,
                kind,
                linkedId,
                world.getName(),
                blockLocation.getBlockX(),
                blockLocation.getBlockY(),
                blockLocation.getBlockZ(),
                display.getUniqueId().toString(),
                baseMaterial.name(),
                customModelData,
                modelId,
                0.5D,
                customBlockOffsetY(kind),
                0.5D,
                scale,
                scale,
                scale,
                0D,
                0D,
                t,
                t
        );
    }

    private void cleanupProtectedBlockVisuals(String kind, String linkedId) throws Exception {
        Map<String, Object> row = queryOne(
                "SELECT entity_uuid,world,x,y,z FROM protected_block_visuals WHERE kind=? AND linked_id=? AND active=1 ORDER BY updated_at DESC LIMIT 1",
                kind,
                linkedId
        );
        if (row != null) {
            World world = Bukkit.getWorld(string(row.get("world")));
            if (world != null) {
                String entityUuid = string(row.get("entity_uuid"));
                if (!entityUuid.isBlank()) {
                    try {
                        Entity entity = Bukkit.getEntity(UUID.fromString(entityUuid));
                        removeOwnedProtectedVisualEntity(entity, kind, linkedId);
                    } catch (Exception ignored) {
                    }
                }
                Location base = new Location(world, intValue(row.get("x")), intValue(row.get("y")), intValue(row.get("z")));
                cleanupNearbyProtectedVisuals(base, kind, linkedId);
            }
        }
        update("UPDATE protected_block_visuals SET active=0,updated_at=? WHERE kind=? AND linked_id=?", now(), kind, linkedId);
    }

    private void repairProtectedBlockVisuals() throws Exception {
        List<String> loadedChunks = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                loadedChunks.add(world.getName() + ":" + chunk.getX() + ":" + chunk.getZ());
            }
        }
        Runnable work = () -> {
            for (String entry : loadedChunks) {
                String[] parts = entry.split(":");
                if (parts.length != 3) {
                    continue;
                }
                try {
                    repairProtectedBlockVisuals(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                } catch (Exception error) {
                    getLogger().warning("repair startup visuals chunk: " + safeError(error));
                }
            }
        };
        if (Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTaskAsynchronously(this, work);
        } else {
            work.run();
        }
    }

    private void repairProtectedBlockVisuals(String worldName, int chunkX, int chunkZ) throws Exception {
        Runnable work = () -> {
            try {
                List<Map<String, Object>> rows = queryList(
                        "SELECT pb.kind,pb.linked_id,pb.world,pb.x,pb.y,pb.z,pbv.entity_uuid,pbv.model_id,pbv.custom_model_data " +
                                "FROM protected_blocks pb " +
                                "LEFT JOIN protected_block_visuals pbv ON pbv.kind=pb.kind AND pbv.linked_id=pb.linked_id AND pbv.active=1 " +
                                "WHERE pb.active=1 AND pb.world=? AND pb.kind IN ('POLLING_STATION','TAX_OFFICE') AND (pb.x >> 4)=? AND (pb.z >> 4)=?",
                        worldName,
                        chunkX,
                        chunkZ
                );
                Bukkit.getScheduler().runTask(this, () -> {
                    try {
                        applyProtectedBlockVisualRepairs(worldName, chunkX, chunkZ, rows);
                    } catch (Exception error) {
                        getLogger().warning("repair chunk visuals apply: " + safeError(error));
                    }
                });
            } catch (Exception error) {
                getLogger().warning("repair chunk visuals fetch: " + safeError(error));
            }
        };
        if (Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTaskAsynchronously(this, work);
        } else {
            work.run();
        }
    }

    private void applyProtectedBlockVisualRepairs(String worldName, int chunkX, int chunkZ, List<Map<String, Object>> rows) throws Exception {
        World world = Bukkit.getWorld(worldName);
        if (world == null || !world.isChunkLoaded(chunkX, chunkZ)) {
            return;
        }
        for (Map<String, Object> row : rows) {
            repairProtectedBlockVisual(
                    string(row.get("kind")),
                    string(row.get("linked_id")),
                    new Location(world, intValue(row.get("x")), intValue(row.get("y")), intValue(row.get("z"))),
                    row
            );
        }
    }

    private void repairProtectedBlockVisual(String kind, String linkedId, Location location, Map<String, Object> row) throws Exception {
        if (!customBlockVisualsEnabled()) {
            cleanupProtectedBlockVisuals(kind, linkedId);
            return;
        }
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        String modelId = "POLLING_STATION".equals(kind) ? "polling_station_marker" : "tax_office_marker";
        int customModelData = "POLLING_STATION".equals(kind) ? MODEL_POLLING_STATION_MARKER : MODEL_TAX_OFFICE_MARKER;
        String expectedEntityUuid = row == null ? "" : string(row.get("entity_uuid"));
        Entity entity = null;
        if (!expectedEntityUuid.isBlank()) {
            try {
                entity = Bukkit.getEntity(UUID.fromString(expectedEntityUuid));
            } catch (Exception ignored) {
            }
        }
        boolean validEntity = isOwnedProtectedVisualEntity(entity, kind, linkedId, modelId, customModelData);
        cleanupNearbyProtectedVisualDuplicates(location, kind, linkedId, validEntity ? string(entity.getUniqueId()) : "");
        if (!validEntity) {
            spawnOrReplaceProtectedBlockVisual(location, kind, linkedId, Material.PAPER, customModelData, modelId);
        }
    }

    private void cleanupNearbyProtectedVisuals(Location blockLocation, String kind, String linkedId) {
        if (blockLocation == null || blockLocation.getWorld() == null) {
            return;
        }
        Location center = blockLocation.clone().add(0.5D, customBlockOffsetY(kind), 0.5D);
        for (Entity entity : blockLocation.getWorld().getNearbyEntities(center, 1.2D, 1.2D, 1.2D)) {
            removeOwnedProtectedVisualEntity(entity, kind, linkedId);
        }
    }

    private void cleanupNearbyProtectedVisualDuplicates(Location blockLocation, String kind, String linkedId, String keepEntityUuid) {
        if (blockLocation == null || blockLocation.getWorld() == null) {
            return;
        }
        Location center = blockLocation.clone().add(0.5D, customBlockOffsetY(kind), 0.5D);
        for (Entity entity : blockLocation.getWorld().getNearbyEntities(center, 1.2D, 1.2D, 1.2D)) {
            if (!(entity instanceof ItemDisplay)) {
                continue;
            }
            if (!isOwnedProtectedVisualEntity(entity, kind, linkedId, "", 0)) {
                continue;
            }
            if (!keepEntityUuid.isBlank() && keepEntityUuid.equals(entity.getUniqueId().toString())) {
                continue;
            }
            entity.remove();
        }
    }

    private void removeOwnedProtectedVisualEntity(Entity entity, String kind, String linkedId) {
        if (isOwnedProtectedVisualEntity(entity, kind, linkedId, "", 0)) {
            entity.remove();
        }
    }

    private boolean isOwnedProtectedVisualEntity(Entity entity, String kind, String linkedId, String modelId, int customModelData) {
        if (!(entity instanceof ItemDisplay display)) {
            return false;
        }
        PersistentDataContainer pdc = display.getPersistentDataContainer();
        if (!"PROTECTED_BLOCK_VISUAL".equals(readString(pdc, visualEntityTypeKey))) {
            return false;
        }
        if (!kind.equals(readString(pdc, visualKindKey)) || !linkedId.equals(readString(pdc, visualLinkedIdKey))) {
            return false;
        }
        if (!modelId.isBlank() && !modelId.equals(readString(pdc, visualModelIdKey))) {
            return false;
        }
        ItemStack item = display.getItemStack();
        ItemMeta meta = item == null ? null : item.getItemMeta();
        if (customModelData > 0 && (meta == null || !meta.hasCustomModelData() || meta.getCustomModelData() != customModelData)) {
            return false;
        }
        return true;
    }

    private void assignChairToStation(String stationId, String playerUuid, String playerName, String actor) throws Exception {
        long t = now();
        tx(connection -> {
            update(connection, "UPDATE polling_stations SET chair_uuid=?,chair_name=?,updated_at=? WHERE id=?", playerUuid, playerName, t, stationId);
            update(connection, "UPDATE cik_chairs SET active=0 WHERE station_id=?", stationId);
            update(connection, "INSERT INTO cik_chairs(station_id,player_uuid,player_name,assigned_at,assigned_by,active) VALUES(?,?,?,?,?,1)", stationId, playerUuid, playerName, t, actor);
            logPluginEvent(connection, "election_core", "chair_assigned", actor, stationId, "player=" + playerName);
            return null;
        });
        Player online = Bukkit.getPlayer(UUID.fromString(playerUuid));
        if (online != null) {
            issueOrRefreshSeal(online, stationId, playerName, false);
        }
    }

    private void claimChairForStation(Player player, String stationId) throws Exception {
        if (player == null) {
            return;
        }
        Map<String, Object> station = stationById(stationId);
        if (station == null || intValue(station.get("active")) <= 0) {
            throw new IllegalStateException("Участок больше не активен.");
        }
        if (!string(station.get("chair_uuid")).isBlank()) {
            throw new IllegalStateException("У участка уже есть председатель ЦИК.");
        }
        assignChairToStation(stationId, player.getUniqueId().toString(), player.getName(), player.getName());
    }

    private void removeChairFromStation(String stationId, String actor) throws Exception {
        Map<String, Object> station = stationById(stationId);
        if (station == null) {
            return;
        }
        String chairUuid = string(station.get("chair_uuid"));
        String chairName = string(station.get("chair_name"));
        if (chairUuid.isBlank()) {
            return;
        }
        long t = now();
        tx(connection -> {
            update(connection, "UPDATE polling_stations SET chair_uuid='',chair_name='',updated_at=? WHERE id=?", t, stationId);
            update(connection, "UPDATE cik_chairs SET active=0 WHERE station_id=?", stationId);
            update(connection, "UPDATE cik_seals SET status='REVOKED',revoked_at=?,revoked_by=? WHERE station_id=? AND status='ACTIVE'", t, actor, stationId);
            logPluginEvent(connection, "election_core", "chair_removed", actor, stationId, "player=" + first(chairName, chairUuid));
            return null;
        });
        try {
            UUID chairPlayerUuid = UUID.fromString(chairUuid);
            officialRestore.remove(chairPlayerUuid);
            Player online = Bukkit.getPlayer(chairPlayerUuid);
            if (online != null) {
                removeOfficialItemsFromPlayer(online, "CIK_SEAL");
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void removePollingStation(Player player, String stationId) throws Exception {
        Map<String, Object> station = stationById(stationId);
        if (station == null) {
            return;
        }
        cleanupNearbyTextDisplays(stationId);
        cleanupProtectedBlockVisuals("POLLING_STATION", stationId);
        long t = now();
        tx(connection -> {
            update(connection, "UPDATE polling_stations SET active=0,updated_at=? WHERE id=?", t, stationId);
            update(connection, "UPDATE protected_blocks SET active=0,updated_at=? WHERE linked_id=?", t, stationId);
            update(connection, "UPDATE cik_chairs SET active=0 WHERE station_id=?", stationId);
            logPluginEvent(connection, "election_core", "station_removed", player.getName(), stationId, "");
            return null;
        });
        reloadProtectedBlocks();
    }

    private void issueOrRefreshSeal(Player target, String stationId, String actorName, boolean forceNew) throws Exception {
        if (target == null) {
            return;
        }
        if (!forceNew && hasActiveSeal(target, stationId)) {
            return;
        }
        String electionId = currentElectionId();
        if (electionId == null) {
            return;
        }
        String sealId = "seal_" + UUID.randomUUID().toString().replace("-", "");
        long t = now();
        tx(connection -> {
            update(connection, "UPDATE cik_seals SET status='REVOKED',revoked_at=?,revoked_by=? WHERE station_id=? AND player_uuid=? AND status='ACTIVE'", t, actorName, stationId, target.getUniqueId().toString());
            update(connection, "INSERT INTO cik_seals(id,station_id,player_uuid,player_name,election_id,status,issued_at,issued_by,revoked_at,revoked_by) VALUES(?,?,?,?,?,'ACTIVE',?,?,0,'')",
                    sealId, stationId, target.getUniqueId().toString(), target.getName(), electionId, t, actorName);
            logPluginEvent(connection, "election_core", "seal_issued", actorName, stationId, "player=" + target.getName());
            return null;
        });
        removeOfficialItemsFromPlayer(target, "CIK_SEAL");
        target.getInventory().addItem(createSealItem(sealId, electionId, stationId, target.getUniqueId().toString(), target.getName()));
    }

    private void issueOrQueueSeal(String stationId, String playerUuid, String playerName, String actorName) throws Exception {
        OfflinePlayer offline = playerUuid == null || playerUuid.isBlank() ? null : Bukkit.getOfflinePlayer(UUID.fromString(playerUuid));
        Player target = offline != null && offline.isOnline() ? offline.getPlayer() : null;
        if (target != null) {
            issueOrRefreshSeal(target, stationId, actorName, true);
            return;
        }
        long t = now();
        String electionId = requireActiveElectionId();
        String sealId = "seal_" + UUID.randomUUID().toString().replace("-", "");
        tx(connection -> {
            update(connection, "UPDATE cik_seals SET status='REVOKED',revoked_at=?,revoked_by=? WHERE station_id=? AND player_uuid=? AND status='ACTIVE'", t, actorName, stationId, playerUuid);
            update(connection, "INSERT INTO cik_seals(id,station_id,player_uuid,player_name,election_id,status,issued_at,issued_by,revoked_at,revoked_by) VALUES(?,?,?,?,?,'ACTIVE',?,?,0,'')",
                    sealId, stationId, playerUuid, playerName, electionId, t, actorName);
            logPluginEvent(connection, "election_core", "seal_issued_offline", actorName, stationId, "player=" + playerName);
            return null;
        });
        if (target == null) {
            officialRestore.computeIfAbsent(UUID.fromString(playerUuid), key -> new ArrayList<>())
                    .add(createSealItem(sealId, electionId, stationId, playerUuid, playerName));
            return;
        }
    }

    private void revokeAllSeals(String actor) throws Exception {
        long t = now();
        tx(connection -> {
            update(connection, "UPDATE cik_seals SET status='REVOKED',revoked_at=?,revoked_by=? WHERE status='ACTIVE'", t, actor);
            logPluginEvent(connection, "election_core", "seals_revoked", actor, "", "");
            return null;
        });
        for (Player online : Bukkit.getOnlinePlayers()) {
            removeOfficialItemsFromPlayer(online, "CIK_SEAL");
        }
    }

    private void issueApplicationBook(Player target, Player issuer, SealContext sealContext) throws Exception {
        if (target == null || issuer == null || sealContext == null) {
            return;
        }
        ensureCanReceiveIssuedElectionItem(target, "книгу заявки");
        String applicationId = "application_" + UUID.randomUUID().toString().replace("-", "");
        long t = now();
        tx(connection -> {
            ElectionContext context = requireActiveElectionContext(connection);
            if (!context.electionId().equals(sealContext.electionId())) {
                throw new IllegalStateException("Эта печать ЦИК больше недействительна.");
            }
            if (context.stage() != ElectionStage.PREPARATION && context.stage() != ElectionStage.APPLICATIONS) {
                throw new IllegalStateException("Заявки сейчас не выдаются.");
            }
            Map<String, Object> sealRow = queryOne(connection,
                    "SELECT id FROM cik_seals WHERE id=? AND election_id=? AND station_id=? AND player_uuid=? AND status='ACTIVE' LIMIT 1",
                    sealContext.sealId(), sealContext.electionId(), sealContext.stationId(), sealContext.playerUuid());
            if (sealRow == null) {
                throw new IllegalStateException("Не удалось проверить печать ЦИК.");
            }
            if (scalarLong(connection, "SELECT COUNT(*) FROM candidate_applications WHERE election_id=? AND player_uuid=?",
                    context.electionId(), target.getUniqueId().toString()) > 0) {
                throw new IllegalStateException("Игрок уже связан с заявкой на этих выборах.");
            }
            update(connection,
                    "INSERT INTO candidate_applications(id,election_id,player_uuid,player_name,station_id,answers,status,chair_recommendation,chair_note,admin_status,admin_note,book_signed_at,submitted_at,reviewed_at,reviewed_by,issued_at,issued_by,book_token) VALUES(?,?,?,?,?,'','ISSUED','','','PENDING','',0,0,0,'',?,?,?)",
                    applicationId, context.electionId(), target.getUniqueId().toString(), target.getName(), sealContext.stationId(), t, issuer.getName(), applicationId);
            update(connection, "UPDATE polling_stations SET applications_issued=applications_issued+1,updated_at=? WHERE id=?", t, sealContext.stationId());
            logPluginEvent(connection, "election_core", "application_issued", issuer.getName(), target.getName(), "station=" + sealContext.stationId());
            return null;
        });
        ItemStack applicationBook = createApplicationBook(applicationId, sealContext.electionId(), sealContext.stationId(), target.getUniqueId().toString());
        giveIssuedElectionItemOrRollback(target, applicationBook,
                () -> rollbackIssuedApplicationBook(applicationId, issuer.getName()),
                "Не удалось выдать книгу заявки. Выдача отменена.");
        issuer.sendMessage(color("&aЗаявка выдана игроку &f" + target.getName()));
    }

    private void issueApplicationBook(Player target, Player issuer) throws Exception {
        requireActiveElectionId();
        throw new IllegalStateException("Выдача заявки без проверенной печати ЦИК отключена.");
    }

    private void issueApplicationBookByAdmin(Player target, Player issuer, String stationId) throws Exception {
        if (!hasElectionAdmin(issuer)) {
            throw new IllegalStateException("Недостаточно прав для выдачи заявки.");
        }
        ensureCanReceiveIssuedElectionItem(target, "книгу заявки");
        String electionId = requireActiveElectionId();
        String applicationId = "application_" + UUID.randomUUID().toString().replace("-", "");
        long t = now();
        tx(connection -> {
            ElectionContext context = requireActiveElectionContext(connection);
            if (context.stage() != ElectionStage.PREPARATION && context.stage() != ElectionStage.APPLICATIONS) {
                throw new IllegalStateException("Заявки сейчас не выдаются.");
            }
            if (scalarLong(connection, "SELECT COUNT(*) FROM candidate_applications WHERE election_id=? AND player_uuid=?",
                    context.electionId(), target.getUniqueId().toString()) > 0) {
                throw new IllegalStateException("Игрок уже связан с заявкой на этих выборах.");
            }
            update(connection,
                    "INSERT INTO candidate_applications(id,election_id,player_uuid,player_name,station_id,answers,status,chair_recommendation,chair_note,admin_status,admin_note,book_signed_at,submitted_at,reviewed_at,reviewed_by,issued_at,issued_by,book_token) VALUES(?,?,?,?,?,'','ISSUED','','','PENDING','',0,0,0,'',?,?,?)",
                    applicationId, context.electionId(), target.getUniqueId().toString(), target.getName(), stationId, t, issuer.getName(), applicationId);
            update(connection, "UPDATE polling_stations SET applications_issued=applications_issued+1,updated_at=? WHERE id=?", t, stationId);
            logPluginEvent(connection, "election_core", "application_issued_admin", issuer.getName(), target.getName(), "station=" + stationId);
            return null;
        });
        ItemStack applicationBook = createApplicationBook(applicationId, electionId, stationId, target.getUniqueId().toString());
        giveIssuedElectionItemOrRollback(target, applicationBook,
                () -> rollbackIssuedApplicationBook(applicationId, issuer.getName()),
                "Не удалось выдать книгу заявки. Выдача отменена.");
        issuer.sendMessage(color("&aЗаявка выдана игроку &f" + target.getName()));
    }

    private void submitApplicationBook(Player player, ItemStack item, String clickedStationId) throws Exception {
        String applicationId = readString(item, applicationIdKey);
        String electionId = readString(item, electionIdKey);
        String stationId = readString(item, stationIdKey);
        if (applicationId.isBlank() || electionId.isBlank() || stationId.isBlank()) {
            player.sendMessage(color("&cКнига заявки повреждена."));
            return;
        }
        if (!Objects.equals(player.getUniqueId().toString(), readString(item, playerUuidKey))) {
            player.sendMessage(color("&cЧужую книгу заявки сдать нельзя."));
            return;
        }
        Map<String, Object> application = applicationById(applicationId);
        if (application == null) {
            player.sendMessage(color("&cЗаявка больше не найдена."));
            return;
        }
        if (!Objects.equals(electionId, string(application.get("election_id")))
                || !Objects.equals(stationId, string(application.get("station_id")))
                || !Objects.equals(player.getUniqueId().toString(), string(application.get("player_uuid")))) {
            player.sendMessage(color("&cКнига заявки больше недействительна."));
            return;
        }
        if (!"ISSUED".equalsIgnoreCase(string(application.get("status"))) || longValue(application.get("submitted_at")) > 0) {
            player.sendMessage(color("&eЭта заявка уже была сдана или закрыта."));
            return;
        }
        try (Connection connection = openConnection()) {
            ElectionContext context = requireActiveElectionContext(connection);
            if (!electionId.equals(context.electionId())
                    || context.stage() != ElectionStage.APPLICATIONS) {
                player.sendMessage(color("&eПриём заявок сейчас закрыт."));
                return;
            }
        }
        if (!stationMatchesOrFallback(stationId, clickedStationId)) {
            player.sendMessage(color("&cЗаявку нужно сдать на свой участок."));
            return;
        }
        if (item.getType() != Material.WRITTEN_BOOK) {
            player.sendMessage(color("&cНужна правильно подписанная книга заявки."));
            return;
        }
        if (!(item.getItemMeta() instanceof BookMeta meta) || !(item.getType() == Material.WRITTEN_BOOK || meta.hasTitle())) {
            player.sendMessage(color("&cНужна правильно подписанная книга заявки."));
            return;
        }
        String answers = readBook(meta);
        if (answers.isBlank()) {
            player.sendMessage(color("&cПустую заявку сдать нельзя."));
            return;
        }
        long t = now();
        tx(connection -> {
            update(connection, "UPDATE candidate_applications SET answers=?,status='SUBMITTED',submitted_at=?,book_signed_at=?,admin_status='PENDING' WHERE id=? AND status='ISSUED' AND submitted_at=0",
                    answers, t, t, applicationId);
            logPluginEvent(connection, "election_core", "application_submitted", player.getName(), applicationId, "station=" + clickedStationId);
            return null;
        });
        consumeSingleItem(player, item);
        player.sendMessage(color("&aЗаявка принята."));
        refreshSnapshotAndPush();
    }

    private void issueBallot(Player target, Player issuer, SealContext sealContext) throws Exception {
        if (target == null || issuer == null || sealContext == null) {
            return;
        }
        ensureCanReceiveIssuedElectionItem(target, "бюллетень");
        String ballotId = "ballot_" + UUID.randomUUID().toString().replace("-", "");
        long t = now();
        AtomicInteger roundRef = new AtomicInteger(1);
        tx(connection -> {
            ElectionContext context = requireActiveElectionContext(connection);
            if (!context.electionId().equals(sealContext.electionId())) {
                throw new IllegalStateException("Эта печать ЦИК больше недействительна.");
            }
            if (context.stage() != ElectionStage.DEBATES && context.stage() != ElectionStage.VOTING && context.stage() != ElectionStage.SECOND_ROUND) {
                throw new IllegalStateException("Бюллетени сейчас не выдаются.");
            }
            Map<String, Object> sealRow = queryOne(connection,
                    "SELECT id FROM cik_seals WHERE id=? AND election_id=? AND station_id=? AND player_uuid=? AND status='ACTIVE' LIMIT 1",
                    sealContext.sealId(), sealContext.electionId(), sealContext.stationId(), sealContext.playerUuid());
            if (sealRow == null) {
                throw new IllegalStateException("Не удалось проверить печать ЦИК.");
            }
            int round = currentRoundFromDb(connection, context.electionId());
            roundRef.set(round);
            long roundCandidates = scalarLong(connection,
                    "SELECT COUNT(*) FROM round_candidates WHERE election_id=? AND round_no=? AND active=1",
                    context.electionId(), round);
            if (roundCandidates < 2) {
                throw new IllegalStateException("Текущий тур ещё не подготовлен для выдачи бюллетеней.");
            }
            if (context.stage() == ElectionStage.SECOND_ROUND && round < 2) {
                throw new IllegalStateException("Второй тур ещё не подготовлен.");
            }
            if (scalarLong(connection, "SELECT COUNT(*) FROM candidate_applications WHERE election_id=? AND player_uuid=?",
                    context.electionId(), target.getUniqueId().toString()) > 0) {
                // hasApplicationInElection guard stays mandatory for the current election.
                throw new IllegalStateException("Игрок уже получал заявку на этих выборах. Бюллетень выдавать нельзя.");
            }
            if (scalarLong(connection, "SELECT COUNT(*) FROM candidates WHERE election_id=? AND player_uuid=? AND active=1",
                    context.electionId(), target.getUniqueId().toString()) > 0) {
                throw new IllegalStateException("Кандидату нельзя выдавать бюллетень.");
            }
            if (scalarLong(connection,
                    "SELECT COUNT(*) FROM ballots WHERE election_id=? AND round_no=? AND player_uuid=? AND status IN ('ISSUED','CONFIRMED','DEPOSITED')",
                    context.electionId(), round, target.getUniqueId().toString()) > 0) {
                // hasActiveBallot guard stays mandatory for the current round.
                throw new IllegalStateException("У игрока уже есть активный бюллетень.");
            }
            update(connection,
                    "INSERT INTO ballots(id,election_id,round_no,player_uuid,player_name,station_id,status,issued_at,issued_by,confirmed_candidate_uuid,confirmed_candidate_name,confirmed_at,submitted_at,submitted_station_id,annulled_at,annulled_by,annul_reason) VALUES(?,?,?,?,?,?, 'ISSUED', ?, ?, '', '', 0, 0, '', 0, '', '')",
                    ballotId, context.electionId(), round, target.getUniqueId().toString(), target.getName(), sealContext.stationId(), t, issuer.getName());
            update(connection, "UPDATE polling_stations SET ballots_issued=ballots_issued+1,updated_at=? WHERE id=?", t, sealContext.stationId());
            logPluginEvent(connection, "election_core", "ballot_issued", issuer.getName(), target.getName(), "station=" + sealContext.stationId() + " round=" + round);
            return null;
        });
        ItemStack ballot = createBallotItem(ballotId, sealContext.electionId(), sealContext.stationId(), roundRef.get(), target.getUniqueId().toString(), target.getName(), false, "", "");
        giveIssuedElectionItemOrRollback(target, ballot,
                () -> rollbackIssuedBallot(ballotId, issuer.getName()),
                "Не удалось выдать бюллетень. Выдача отменена.");
        issuer.sendMessage(color("&aБюллетень выдан игроку &f" + target.getName()));
    }

    private void issueBallot(Player target, Player issuer) throws Exception {
        requireActiveElectionId();
        throw new IllegalStateException("Выдача бюллетеня без проверенной печати ЦИК отключена.");
    }

    private void issueBallotByAdmin(Player target, Player issuer, String stationId) throws Exception {
        if (!hasElectionAdmin(issuer)) {
            throw new IllegalStateException("Недостаточно прав для выдачи бюллетеня.");
        }
        ensureCanReceiveIssuedElectionItem(target, "бюллетень");
        String electionId = requireActiveElectionId();
        String ballotId = "ballot_" + UUID.randomUUID().toString().replace("-", "");
        long t = now();
        AtomicInteger roundRef = new AtomicInteger(1);
        tx(connection -> {
            ElectionContext context = requireActiveElectionContext(connection);
            if (context.stage() != ElectionStage.DEBATES && context.stage() != ElectionStage.VOTING && context.stage() != ElectionStage.SECOND_ROUND) {
                throw new IllegalStateException("Бюллетени сейчас не выдаются.");
            }
            int round = currentRoundFromDb(connection, context.electionId());
            roundRef.set(round);
            long roundCandidates = scalarLong(connection,
                    "SELECT COUNT(*) FROM round_candidates WHERE election_id=? AND round_no=? AND active=1",
                    context.electionId(), round);
            if (roundCandidates < 2) {
                throw new IllegalStateException("Текущий тур ещё не подготовлен для выдачи бюллетеней.");
            }
            if (context.stage() == ElectionStage.SECOND_ROUND && round < 2) {
                throw new IllegalStateException("Второй тур ещё не подготовлен.");
            }
            if (scalarLong(connection, "SELECT COUNT(*) FROM candidate_applications WHERE election_id=? AND player_uuid=?",
                    context.electionId(), target.getUniqueId().toString()) > 0) {
                throw new IllegalStateException("Игрок уже получал заявку на этих выборах. Бюллетень выдавать нельзя.");
            }
            if (scalarLong(connection, "SELECT COUNT(*) FROM candidates WHERE election_id=? AND player_uuid=? AND active=1",
                    context.electionId(), target.getUniqueId().toString()) > 0) {
                throw new IllegalStateException("Кандидату нельзя выдавать бюллетень.");
            }
            if (scalarLong(connection,
                    "SELECT COUNT(*) FROM ballots WHERE election_id=? AND round_no=? AND player_uuid=? AND status IN ('ISSUED','CONFIRMED','DEPOSITED')",
                    context.electionId(), round, target.getUniqueId().toString()) > 0) {
                throw new IllegalStateException("У игрока уже есть активный бюллетень.");
            }
            update(connection,
                    "INSERT INTO ballots(id,election_id,round_no,player_uuid,player_name,station_id,status,issued_at,issued_by,confirmed_candidate_uuid,confirmed_candidate_name,confirmed_at,submitted_at,submitted_station_id,annulled_at,annulled_by,annul_reason) VALUES(?,?,?,?,?,?, 'ISSUED', ?, ?, '', '', 0, 0, '', 0, '', '')",
                    ballotId, context.electionId(), round, target.getUniqueId().toString(), target.getName(), stationId, t, issuer.getName());
            update(connection, "UPDATE polling_stations SET ballots_issued=ballots_issued+1,updated_at=? WHERE id=?", t, stationId);
            logPluginEvent(connection, "election_core", "ballot_issued_admin", issuer.getName(), target.getName(), "station=" + stationId + " round=" + round);
            return null;
        });
        ItemStack ballot = createBallotItem(ballotId, electionId, stationId, roundRef.get(), target.getUniqueId().toString(), target.getName(), false, "", "");
        giveIssuedElectionItemOrRollback(target, ballot,
                () -> rollbackIssuedBallot(ballotId, issuer.getName()),
                "Не удалось выдать бюллетень. Выдача отменена.");
        issuer.sendMessage(color("&aБюллетень выдан игроку &f" + target.getName()));
    }

    private void ensureCanReceiveIssuedElectionItem(Player target, String itemName) {
        if (target == null) {
            return;
        }
        if (target.getInventory().firstEmpty() < 0) {
            throw new IllegalStateException("Освободи хотя бы один слот, чтобы получить " + itemName + ".");
        }
    }

    private void giveIssuedElectionItemOrRollback(Player target, ItemStack stack, CheckedSqlAction rollback, String failureMessage) throws Exception {
        if (target == null || stack == null || stack.getType().isAir()) {
            return;
        }
        Map<Integer, ItemStack> leftovers = target.getInventory().addItem(stack);
        if (leftovers.isEmpty()) {
            return;
        }
        target.getInventory().removeItem(stack);
        rollback.run();
        throw new IllegalStateException(failureMessage);
    }

    private void rollbackIssuedApplicationBook(String applicationId, String actor) throws Exception {
        if (applicationId == null || applicationId.isBlank()) {
            return;
        }
        long t = now();
        tx(connection -> {
            Map<String, Object> row = queryOne(connection,
                    "SELECT station_id,status,submitted_at FROM candidate_applications WHERE id=? FOR UPDATE",
                    applicationId);
            if (row == null || !"ISSUED".equalsIgnoreCase(string(row.get("status"))) || longValue(row.get("submitted_at")) > 0) {
                return null;
            }
            update(connection, "DELETE FROM candidate_applications WHERE id=?", applicationId);
            String stationId = string(row.get("station_id"));
            if (!stationId.isBlank()) {
                update(connection,
                        "UPDATE polling_stations SET applications_issued=GREATEST(applications_issued-1,0),updated_at=? WHERE id=?",
                        t, stationId);
            }
            logPluginEvent(connection, "election_core", "application_issue_rollback", actor, applicationId, "delivery_failed");
            return null;
        });
    }

    private void rollbackIssuedBallot(String ballotId, String actor) throws Exception {
        if (ballotId == null || ballotId.isBlank()) {
            return;
        }
        long t = now();
        tx(connection -> {
            Map<String, Object> row = queryOne(connection,
                    "SELECT station_id,status,submitted_at FROM ballots WHERE id=? FOR UPDATE",
                    ballotId);
            if (row == null || !"ISSUED".equalsIgnoreCase(string(row.get("status"))) || longValue(row.get("submitted_at")) > 0) {
                return null;
            }
            update(connection, "DELETE FROM ballots WHERE id=?", ballotId);
            String stationId = string(row.get("station_id"));
            if (!stationId.isBlank()) {
                update(connection,
                        "UPDATE polling_stations SET ballots_issued=GREATEST(ballots_issued-1,0),updated_at=? WHERE id=?",
                        t, stationId);
            }
            logPluginEvent(connection, "election_core", "ballot_issue_rollback", actor, ballotId, "delivery_failed");
            return null;
        });
    }

    private void confirmBallotChoice(Player player, String ballotId, String candidateUuid) throws Exception {
        if (candidateUuid.equals(player.getUniqueId().toString())) {
            player.sendMessage(color("&cКандидат не может голосовать сам за себя."));
            return;
        }
        long t = now();
        AtomicReference<String> electionIdRef = new AtomicReference<>("");
        AtomicReference<String> stationIdRef = new AtomicReference<>("");
        AtomicReference<String> candidateNameRef = new AtomicReference<>("");
        AtomicInteger roundRef = new AtomicInteger(1);
        tx(connection -> {
            ElectionContext context = requireActiveElectionContext(connection);
            Map<String, Object> ballot = queryOne(connection,
                    "SELECT id,election_id,round_no,player_uuid,station_id,status FROM ballots WHERE id=? FOR UPDATE",
                    ballotId);
            if (ballot == null) {
                throw new IllegalStateException("Бюллетень не найден.");
            }
            if (!Objects.equals(string(ballot.get("player_uuid")), player.getUniqueId().toString())) {
                throw new IllegalStateException("Чужой бюллетень использовать нельзя.");
            }
            if (!context.electionId().equals(string(ballot.get("election_id"))) || context.stage() != ElectionStage.VOTING) {
                throw new IllegalStateException("Голосование уже закрыто. Выбор нельзя подтвердить.");
            }
            int liveRound = currentRoundFromDb(connection, context.electionId());
            if (liveRound != intValue(ballot.get("round_no"))) {
                throw new IllegalStateException("Голосование уже закрыто. Выбор нельзя подтвердить.");
            }
            if (!"ISSUED".equalsIgnoreCase(string(ballot.get("status")))) {
                throw new IllegalStateException("Этот бюллетень уже нельзя изменить.");
            }
            if (candidateUuid.equals(string(ballot.get("player_uuid")))) {
                throw new IllegalStateException("Кандидат не может голосовать сам за себя.");
            }
            Map<String, Object> candidate = queryOne(connection,
                    "SELECT rc.candidate_name,COALESCE(c.active,0) AS candidate_active,COALESCE(a.admin_status,'') AS admin_status " +
                            "FROM round_candidates rc " +
                            "LEFT JOIN candidates c ON c.election_id=rc.election_id AND c.player_uuid=rc.candidate_uuid " +
                            "LEFT JOIN candidate_applications a ON a.id=c.application_id " +
                            "WHERE rc.election_id=? AND rc.round_no=? AND rc.candidate_uuid=? AND rc.active=1 LIMIT 1",
                    context.electionId(), liveRound, candidateUuid);
            if (candidate == null || intValue(candidate.get("candidate_active")) <= 0 || !"APPROVED".equalsIgnoreCase(string(candidate.get("admin_status")))) {
                throw new IllegalStateException("Кандидат больше недоступен в текущем туре.");
            }
            update(connection, "UPDATE ballots SET status='CONFIRMED',confirmed_candidate_uuid=?,confirmed_candidate_name=?,confirmed_at=? WHERE id=?",
                    candidateUuid, string(candidate.get("candidate_name")), t, ballotId);
            electionIdRef.set(context.electionId());
            stationIdRef.set(string(ballot.get("station_id")));
            candidateNameRef.set(string(candidate.get("candidate_name")));
            roundRef.set(liveRound);
            return null;
        });
        replacePlayerBallot(player, ballotId, electionIdRef.get(), stationIdRef.get(), roundRef.get(), candidateUuid, candidateNameRef.get());
        player.sendMessage(color("&aГолос подтверждён. Теперь сдай бюллетень через свой участок."));
    }

    private void depositBallot(Player player, ItemStack item, String clickedStationId) throws Exception {
        String ballotId = readString(item, ballotIdKey);
        String electionId = readString(item, electionIdKey);
        String stationId = readString(item, stationIdKey);
        int round = intValue(readString(item, roundKey));
        if (ballotId.isBlank() || electionId.isBlank() || stationId.isBlank()) {
            player.sendMessage(color("&cБюллетень повреждён."));
            return;
        }
        if (!Objects.equals(player.getUniqueId().toString(), readString(item, playerUuidKey))) {
            player.sendMessage(color("&cЧужой бюллетень сдавать нельзя."));
            return;
        }
        long t = now();
        tx(connection -> {
            ElectionContext context = requireActiveElectionContext(connection);
            Map<String, Object> lockedBallot = queryOne(connection,
                    "SELECT status,election_id,round_no,player_uuid,station_id,confirmed_candidate_uuid,confirmed_candidate_name FROM ballots WHERE id=? FOR UPDATE",
                    ballotId);
            if (lockedBallot == null) {
                throw new IllegalStateException("Бюллетень не найден.");
            }
            if (!Objects.equals(string(lockedBallot.get("player_uuid")), player.getUniqueId().toString())) {
                throw new IllegalStateException("Чужой бюллетень сдавать нельзя.");
            }
            if (!context.electionId().equals(string(lockedBallot.get("election_id"))) || context.stage() != ElectionStage.VOTING) {
                throw new IllegalStateException("Голосование уже закрыто. Этот бюллетень больше нельзя сдать.");
            }
            int liveRound = currentRoundFromDb(connection, context.electionId());
            if (liveRound != intValue(lockedBallot.get("round_no"))) {
                throw new IllegalStateException("Голосование уже закрыто. Этот бюллетень больше нельзя сдать.");
            }
            if (!stationMatchesOrFallback(connection, string(lockedBallot.get("station_id")), clickedStationId)) {
                throw new IllegalStateException("Бюллетень нужно сдать на свой участок.");
            }
            if ("DEPOSITED".equalsIgnoreCase(string(lockedBallot.get("status")))) {
                throw new IllegalStateException("Этот бюллетень уже сдан.");
            }
            if ("ANNULLED".equalsIgnoreCase(string(lockedBallot.get("status")))) {
                throw new IllegalStateException("Этот бюллетень уже недействителен.");
            }
            if (!"CONFIRMED".equalsIgnoreCase(string(lockedBallot.get("status")))) {
                throw new IllegalStateException("Бюллетень нужно сначала подтвердить в меню голосования.");
            }
            if (scalarLong(connection, "SELECT COUNT(*) FROM votes WHERE ballot_id=?", ballotId) > 0) {
                throw new IllegalStateException("Этот бюллетень уже сдан.");
            }
            if (scalarLong(connection, "SELECT COUNT(*) FROM votes WHERE election_id=? AND round_no=? AND voter_uuid=?", context.electionId(), liveRound, player.getUniqueId().toString()) > 0) {
                throw new IllegalStateException("Голос этого игрока уже принят.");
            }
            Map<String, Object> candidate = queryOne(connection,
                    "SELECT candidate_name FROM round_candidates WHERE election_id=? AND round_no=? AND candidate_uuid=? AND active=1 LIMIT 1",
                    context.electionId(), liveRound, string(lockedBallot.get("confirmed_candidate_uuid")));
            if (candidate == null) {
                throw new IllegalStateException("Голосование уже закрыто. Этот бюллетень больше нельзя сдать.");
            }
            update(connection, "INSERT INTO votes(id,election_id,round_no,ballot_id,voter_uuid,voter_name,candidate_uuid,candidate_name,station_id,created_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
                    "vote_" + UUID.randomUUID().toString().replace("-", ""), context.electionId(), liveRound, ballotId, player.getUniqueId().toString(), player.getName(),
                    string(lockedBallot.get("confirmed_candidate_uuid")), string(candidate.get("candidate_name")), clickedStationId, t);
            update(connection, "UPDATE ballots SET status='DEPOSITED',submitted_at=?,submitted_station_id=? WHERE id=?", t, clickedStationId, ballotId);
            update(connection, "UPDATE polling_stations SET ballots_submitted=ballots_submitted+1,updated_at=? WHERE id=?", t, clickedStationId);
            logPluginEvent(connection, "election_core", "ballot_deposited", player.getName(), ballotId, "station=" + clickedStationId);
            return null;
        });
        consumeSingleItem(player, item);
        player.sendTitle("", color("&aГолос принят"), 5, 40, 10);
    }

    private void annulBallot(String ballotId, String stationId, Player actor, String reason) throws Exception {
        long t = now();
        tx(connection -> {
            Map<String, Object> ballot = queryOne(connection, "SELECT player_uuid,station_id,status FROM ballots WHERE id=? FOR UPDATE", ballotId);
            if (ballot == null) {
                return null;
            }
            if (!stationId.equals(string(ballot.get("station_id")))) {
                throw new IllegalStateException("Бюллетень не относится к этому участку.");
            }
            Map<String, Object> chair = queryOne(connection,
                    "SELECT station_id FROM cik_chairs WHERE station_id=? AND player_uuid=? AND active=1 LIMIT 1",
                    stationId, actor.getUniqueId().toString());
            if (chair == null) {
                throw new IllegalStateException("У тебя нет доступа к аннулированию бюллетеней этого участка.");
            }
            String status = string(ballot.get("status")).toUpperCase(Locale.ROOT);
            if ("DEPOSITED".equals(status)) {
                throw new IllegalStateException("Сданный бюллетень уже нельзя аннулировать.");
            }
            if (!"ISSUED".equals(status) && !"CONFIRMED".equals(status)) {
                throw new IllegalStateException("Этот бюллетень уже закрыт.");
            }
            update(connection, "UPDATE ballots SET status='ANNULLED',annulled_at=?,annulled_by=?,annul_reason=? WHERE id=?", t, actor.getName(), reason, ballotId);
            update(connection, "UPDATE polling_stations SET ballots_annulled=ballots_annulled+1,updated_at=? WHERE id=?", t, string(ballot.get("station_id")));
            logPluginEvent(connection, "election_core", "ballot_annulled", actor.getName(), ballotId, reason);
            return null;
        });
        Player online = playerByBallot(ballotId);
        if (online != null) {
            removeBallotFromInventory(online, ballotId);
        }
    }

    private void setChairRecommendation(String stationId, String applicationId, String recommendation, Player player) throws Exception {
        if (player == null) {
            return;
        }
        tx(connection -> {
            Map<String, Object> chair = queryOne(connection,
                    "SELECT station_id FROM cik_chairs WHERE station_id=? AND player_uuid=? AND active=1 LIMIT 1",
                    stationId, player.getUniqueId().toString());
            if (chair == null) {
                throw new IllegalStateException("У тебя нет доступа к заявкам этого участка.");
            }
            Map<String, Object> application = queryOne(connection,
                    "SELECT station_id,admin_status FROM candidate_applications WHERE id=? LIMIT 1",
                    applicationId);
            if (application == null || !stationId.equals(string(application.get("station_id")))) {
                throw new IllegalStateException("Заявка этого участка не найдена.");
            }
            if (!"PENDING".equalsIgnoreCase(string(application.get("admin_status")))) {
                throw new IllegalStateException("Эта заявка уже рассмотрена администрацией.");
            }
            update(connection, "UPDATE candidate_applications SET chair_recommendation=? WHERE id=?", recommendation, applicationId);
            return null;
        });
    }

    private void setCandidateLimit(Player player, int limit) throws Exception {
        String electionId = currentElectionId();
        if (electionId == null || electionId.isBlank()) {
            player.sendMessage(color("&eСейчас нет активных выборов."));
            return;
        }
        update("UPDATE elections SET candidate_limit=?,updated_at=? WHERE id=?", limit, now(), electionId);
    }

    private void setPresidentTermDays(Player player, int days) throws Exception {
        String electionId = currentElectionId();
        if (electionId == null || electionId.isBlank()) {
            player.sendMessage(color("&eСейчас нет активных выборов."));
            return;
        }
        update("UPDATE elections SET president_term_days=?,updated_at=? WHERE id=?", days, now(), electionId);
    }

    private void approveApplication(String applicationId, String actor) throws Exception {
        Map<String, Object> app = applicationById(applicationId);
        if (app == null) {
            return;
        }
        String electionId = string(app.get("election_id"));
        long activeCount = scalarLong("SELECT COUNT(*) FROM candidates WHERE election_id=? AND active=1", electionId);
        int limit = intValue(queryOne("SELECT candidate_limit FROM elections WHERE id=?", electionId).get("candidate_limit"));
        if (limit > 0 && activeCount >= limit) {
            throw new IllegalStateException("Лимит кандидатов уже заполнен.");
        }
        long t = now();
        tx(connection -> {
            Map<String, Object> lockedApp = queryOne(connection,
                    "SELECT election_id,player_uuid,player_name,admin_status,submitted_at FROM candidate_applications WHERE id=? FOR UPDATE",
                    applicationId);
            if (lockedApp == null) {
                return null;
            }
            if (!"PENDING".equalsIgnoreCase(string(lockedApp.get("admin_status"))) || longValue(lockedApp.get("submitted_at")) <= 0) {
                throw new IllegalStateException("Эта заявка уже рассмотрена или ещё не сдана.");
            }
            Map<String, Object> lockedElection = queryOne(connection,
                    "SELECT candidate_limit,current_round,current_stage FROM elections WHERE id=? LIMIT 1 FOR UPDATE",
                    electionId);
            if (lockedElection == null) {
                throw new IllegalStateException("Выборы для этой заявки уже не активны.");
            }
            if (ElectionStage.safeValue(string(lockedElection.get("current_stage"))) != ElectionStage.REVIEW) {
                throw new IllegalStateException("Финальное решение по кандидату доступно только на этапе проверки.");
            }
            long lockedActiveCount = scalarLong(connection, "SELECT COUNT(*) FROM candidates WHERE election_id=? AND active=1", electionId);
            int lockedLimit = intValue(lockedElection.get("candidate_limit"));
            if (lockedLimit > 0 && lockedActiveCount >= lockedLimit) {
                throw new IllegalStateException("Лимит кандидатов уже заполнен.");
            }
            update(connection, "UPDATE candidate_applications SET admin_status='APPROVED',status='APPROVED',reviewed_at=?,reviewed_by=? WHERE id=?", t, actor, applicationId);
            int round = Math.max(1, intValue(lockedElection.get("current_round")));
            update(connection, "INSERT INTO candidates(id,election_id,player_uuid,player_name,application_id,created_at,active,last_result) VALUES(?,?,?,?,?,?,1,0) ON CONFLICT(id) DO NOTHING",
                    "candidate_" + electionId + "_" + string(lockedApp.get("player_uuid")), electionId, string(lockedApp.get("player_uuid")), string(lockedApp.get("player_name")), applicationId, t);
            update(connection, "INSERT INTO round_candidates(election_id,round_no,candidate_uuid,candidate_name,active,created_at,created_by) VALUES(?,?,?,?,1,?,?) ON CONFLICT(election_id,round_no,candidate_uuid) DO UPDATE SET candidate_name=excluded.candidate_name,active=1",
                    electionId, round, string(lockedApp.get("player_uuid")), string(lockedApp.get("player_name")), t, actor);
            logPluginEvent(connection, "election_core", "application_approved", actor, applicationId, "");
            return null;
        });
    }

    private void rejectApplication(String applicationId, String actor) throws Exception {
        long t = now();
        tx(connection -> {
            Map<String, Object> lockedApp = queryOne(connection,
                    "SELECT election_id,admin_status,submitted_at FROM candidate_applications WHERE id=? FOR UPDATE",
                    applicationId);
            if (lockedApp == null) {
                return null;
            }
            if (!"PENDING".equalsIgnoreCase(string(lockedApp.get("admin_status"))) || longValue(lockedApp.get("submitted_at")) <= 0) {
                throw new IllegalStateException("Эта заявка уже рассмотрена или ещё не сдана.");
            }
            Map<String, Object> lockedElection = queryOne(connection,
                    "SELECT current_stage FROM elections WHERE id=? LIMIT 1 FOR UPDATE",
                    string(lockedApp.get("election_id")));
            if (lockedElection == null) {
                throw new IllegalStateException("Выборы для этой заявки уже не активны.");
            }
            if (ElectionStage.safeValue(string(lockedElection.get("current_stage"))) != ElectionStage.REVIEW) {
                throw new IllegalStateException("Финальное решение по кандидату доступно только на этапе проверки.");
            }
            update(connection, "UPDATE candidate_applications SET admin_status='REJECTED',status='REJECTED',reviewed_at=?,reviewed_by=? WHERE id=?", t, actor, applicationId);
            logPluginEvent(connection, "election_core", "application_rejected", actor, applicationId, "");
            return null;
        });
    }

    private void countCurrentRound(String actor) throws Exception {
        countCurrentRoundStrict(actor);
    }

    private void startSecondRound(String actor) throws Exception {
        startSecondRoundStrict(actor);
    }

    private void chooseWinner(String candidateUuid, String actor) throws Exception {
        chooseWinnerStrict(candidateUuid, actor);
    }

    private void assignPresident(Connection connection, String electionId, String presidentUuid, String presidentName, String actor, long t) throws Exception {
        update(connection, "UPDATE president_terms SET status='REMOVED',removed_at=?,removed_by=? WHERE status='ACTIVE'", t, actor);
        int termDays = intValue(queryOne(connection, "SELECT president_term_days FROM elections WHERE id=?", electionId).get("president_term_days"));
        int round = currentRoundFromDb(connection, electionId);
        String termId = "term_" + UUID.randomUUID().toString().replace("-", "");
        update(connection, "INSERT INTO president_terms(id,election_id,president_uuid,president_name,status,started_at,ends_at,removed_at,removed_by,last_broadcast_at,last_law_replace_at) VALUES(?,?,?,?, 'ACTIVE', ?, ?, 0, '', 0, 0)",
                termId, electionId, presidentUuid, presidentName, t, t + Math.max(7, termDays) * 86_400_000L);
        update(connection, "UPDATE elections SET president_uuid=?,president_name=?,manual_winner_uuid=?,manual_winner_name=?,current_stage=?,second_round_needed=0,updated_at=? WHERE id=?",
                presidentUuid, presidentName, presidentUuid, presidentName, ElectionStage.PRESIDENT_TERM.name(), t, electionId);
        update(connection, "INSERT INTO election_stages(election_id,stage,round_no,actor,created_at,notes) VALUES(?,?,?,?,?,?)",
                electionId, ElectionStage.PRESIDENT_TERM.name(), round, actor, t, presidentName);
        Player online = Bukkit.getPlayer(UUID.fromString(presidentUuid));
        if (online != null) {
            removeOfficialItemsFromPlayer(online, "PRESIDENT_MANDATE");
            online.getInventory().addItem(createPresidentMandate(electionId, presidentUuid, presidentName));
        }
    }

    private void removePresident(String actor) throws Exception {
        long t = now();
        tx(connection -> {
            Map<String, Object> active = queryOne(connection, "SELECT president_uuid FROM president_terms WHERE status='ACTIVE' ORDER BY started_at DESC LIMIT 1");
            if (active != null) {
                Player online = Bukkit.getPlayer(UUID.fromString(string(active.get("president_uuid"))));
                if (online != null) {
                    removeOfficialItemsFromPlayer(online, "PRESIDENT_MANDATE");
                }
            }
            update(connection, "UPDATE president_terms SET status='REMOVED',removed_at=?,removed_by=? WHERE status='ACTIVE'", t, actor);
            update(connection, "UPDATE elections SET president_uuid='',president_name='',updated_at=? WHERE id=?", t, currentElectionId());
            logPluginEvent(connection, "election_core", "president_removed", actor, "", "");
            return null;
        });
    }

    private void submitLawForReview(Player player, String text, String replacesLawId) throws Exception {
        if (!isPresident(player) && !hasElectionAdmin(player)) {
            throw new IllegalStateException("Нет прав президента.");
        }
        Map<String, Object> term = activeTerm();
        if (term == null) {
            throw new IllegalStateException("Нет активного президентского срока.");
        }
        if (replacesLawId.isBlank() && publishedLaws().size() >= 5) {
            throw new IllegalStateException("У президента уже 5 законов. Используй замену.");
        }
        long t = now();
        if (!replacesLawId.isBlank() && t - longValue(term.get("last_law_replace_at")) < PRESIDENT_LAW_REPLACE_COOLDOWN_MS) {
            throw new IllegalStateException("Закон можно заменять не чаще одного раза в 3 дня.");
        }
        tx(connection -> {
            if (!replacesLawId.isBlank()) {
                Map<String, Object> replaced = queryOne(connection,
                        "SELECT id,slot_no,status,term_id FROM president_laws WHERE id=? LIMIT 1",
                        replacesLawId);
                if (replaced == null
                        || !string(term.get("id")).equals(string(replaced.get("term_id")))
                        || !"PUBLISHED".equalsIgnoreCase(string(replaced.get("status")))) {
                    throw new IllegalStateException("Этот закон уже нельзя заменить.");
                }
            }
            update(connection, "INSERT INTO president_laws(id,term_id,president_uuid,text,status,created_at,published_at,replaced_law_id,slot_no) VALUES(?,?,?,?, 'PENDING', ?, 0, ?, 0)",
                    "law_" + UUID.randomUUID().toString().replace("-", ""), string(term.get("id")), player.getUniqueId().toString(), text, t, replacesLawId);
            logPluginEvent(connection, "election_core", "law_pending", player.getName(), string(term.get("id")), text);
            return null;
        });
    }

    private void countCurrentRoundStrict(String actor) throws Exception {
        String electionId = currentElectionId();
        if (electionId == null) {
            return;
        }
        long t = now();
        tx(connection -> {
            ElectionContext context = requireActiveElectionContext(connection);
            if (!electionId.equals(context.electionId()) || context.stage() != ElectionStage.VOTING) {
                throw new IllegalStateException("Подсчёт доступен только после этапа голосования.");
            }
            int round = currentRoundFromDb(connection, electionId);
            List<Map<String, Object>> resultRows = queryList(connection,
                    "SELECT rc.candidate_uuid AS player_uuid,COALESCE(NULLIF(rc.candidate_name,''),c.player_name) AS player_name,COUNT(v.id) AS votes " +
                            "FROM round_candidates rc " +
                            "LEFT JOIN candidates c ON c.election_id=rc.election_id AND c.player_uuid=rc.candidate_uuid " +
                            "LEFT JOIN votes v ON v.election_id=rc.election_id AND v.round_no=rc.round_no AND v.candidate_uuid=rc.candidate_uuid " +
                            "WHERE rc.election_id=? AND rc.round_no=? AND rc.active=1 " +
                            "GROUP BY rc.candidate_uuid,rc.candidate_name,c.player_name " +
                            "ORDER BY votes DESC,player_name ASC",
                    electionId, round);
            List<CandidateResult> results = new ArrayList<>();
            int maxVotes = 0;
            for (Map<String, Object> row : resultRows) {
                int votes = intValue(row.get("votes"));
                maxVotes = Math.max(maxVotes, votes);
                results.add(new CandidateResult(
                        string(row.get("player_uuid")),
                        first(string(row.get("player_name")), "Кандидат"),
                        votes,
                        ""
                ));
            }
            int leadingVotes = maxVotes;
            List<CandidateResult> leaders = results.stream().filter(row -> row.votes() == leadingVotes).toList();
            update(connection, "UPDATE elections SET current_stage=?,updated_at=? WHERE id=?", ElectionStage.COUNTING.name(), t, electionId);
            update(connection, "INSERT INTO election_stages(election_id,stage,round_no,actor,created_at,notes) VALUES(?,?,?,?,?,?)",
                    electionId, ElectionStage.COUNTING.name(), round, actor, t, "Подсчёт бюллетеней");
            for (CandidateResult result : results) {
                update(connection, "UPDATE candidates SET last_result=? WHERE election_id=? AND player_uuid=?", result.votes(), electionId, result.uuid());
            }
            if (results.isEmpty()) {
                update(connection, "UPDATE rounds SET status='COUNTED',ended_at=?,winner_uuid='',winner_name='' WHERE election_id=? AND round_no=?", t, electionId, round);
                update(connection, "UPDATE elections SET second_round_needed=0,manual_winner_uuid='',manual_winner_name='',updated_at=? WHERE id=?", t, electionId);
                logPluginEvent(connection, "election_core", "round_counted", actor, electionId, "round=" + round + ";candidates=0");
                return null;
            }
            if (leaders.size() > 1) {
                update(connection, "UPDATE rounds SET status='COUNTED',ended_at=?,winner_uuid='',winner_name='' WHERE election_id=? AND round_no=?", t, electionId, round);
                update(connection, "UPDATE elections SET second_round_needed=1,manual_winner_uuid='',manual_winner_name='',updated_at=? WHERE id=?", t, electionId);
                logPluginEvent(connection, "election_core", "round_tied", actor, electionId, "round=" + round);
            } else {
                update(connection, "UPDATE rounds SET status='COUNTED',ended_at=?,winner_uuid=?,winner_name=? WHERE election_id=? AND round_no=?",
                        t, leaders.getFirst().uuid(), leaders.getFirst().name(), electionId, round);
                update(connection, "UPDATE elections SET second_round_needed=0,manual_winner_uuid=?,manual_winner_name=?,updated_at=? WHERE id=?",
                        leaders.getFirst().uuid(), leaders.getFirst().name(), t, electionId);
                logPluginEvent(connection, "election_core", "round_winner", actor, leaders.getFirst().uuid(), "round=" + round);
            }
            return null;
        });
    }

    private void startSecondRoundStrict(String actor) throws Exception {
        String electionId = currentElectionId();
        if (electionId == null) {
            return;
        }
        long t = now();
        tx(connection -> {
            ElectionStage from = currentStageFromDb(connection, electionId);
            StageTransitionResult transition = validateStageTransition(connection, electionId, from, ElectionStage.SECOND_ROUND);
            if (!transition.allowed()) {
                throw new IllegalStateException(transition.message());
            }
            int round = currentRoundFromDb(connection, electionId);
            List<Map<String, Object>> resultRows = queryList(connection,
                    "SELECT rc.candidate_uuid AS player_uuid,COALESCE(NULLIF(rc.candidate_name,''),c.player_name) AS player_name,COUNT(v.id) AS votes " +
                            "FROM round_candidates rc " +
                            "LEFT JOIN candidates c ON c.election_id=rc.election_id AND c.player_uuid=rc.candidate_uuid " +
                            "LEFT JOIN votes v ON v.election_id=rc.election_id AND v.round_no=rc.round_no AND v.candidate_uuid=rc.candidate_uuid " +
                            "WHERE rc.election_id=? AND rc.round_no=? AND rc.active=1 " +
                            "GROUP BY rc.candidate_uuid,rc.candidate_name,c.player_name " +
                            "ORDER BY votes DESC,player_name ASC",
                    electionId, round);
            int maxVotes = resultRows.stream().mapToInt(row -> intValue(row.get("votes"))).max().orElse(0);
            List<CandidateResult> leaders = resultRows.stream()
                    .filter(row -> intValue(row.get("votes")) == maxVotes)
                    .map(row -> new CandidateResult(
                            string(row.get("player_uuid")),
                            first(string(row.get("player_name")), "Кандидат"),
                            intValue(row.get("votes")),
                            ""
                    ))
                    .toList();
            if (leaders.size() < 2) {
                throw new IllegalStateException("Для второго тура нужна ничья лидеров.");
            }
            int nextRound = round + 1;
            update(connection, "UPDATE ballots SET status='VOID' WHERE election_id=? AND round_no<? AND status IN ('ISSUED','CONFIRMED')", electionId, nextRound);
            update(connection, "UPDATE rounds SET status='FINISHED',ended_at=CASE WHEN ended_at=0 THEN ? ELSE ended_at END WHERE election_id=? AND round_no=?", t, electionId, round);
            update(connection, "INSERT INTO rounds(id,election_id,round_no,status,started_at,ended_at,winner_uuid,winner_name) VALUES(?,?,?,?,?,0,'','')",
                    "round_" + electionId + "_" + nextRound, electionId, nextRound, "ACTIVE", t);
            update(connection, "DELETE FROM round_candidates WHERE election_id=? AND round_no=?", electionId, nextRound);
            for (CandidateResult leader : leaders) {
                update(connection, "INSERT INTO round_candidates(election_id,round_no,candidate_uuid,candidate_name,active,created_at,created_by) VALUES(?,?,?,?,1,?,?)",
                        electionId, nextRound, leader.uuid(), leader.name(), t, actor);
            }
            update(connection, "UPDATE elections SET current_round=?,current_stage=?,second_round_needed=0,updated_at=? WHERE id=?",
                    nextRound, ElectionStage.SECOND_ROUND.name(), t, electionId);
            update(connection, "INSERT INTO election_stages(election_id,stage,round_no,actor,created_at,notes) VALUES(?,?,?,?,?,?)",
                    electionId, ElectionStage.SECOND_ROUND.name(), nextRound, actor, t, "Запуск второго тура");
            logPluginEvent(connection, "election_core", "second_round_started", actor, electionId, "round=" + nextRound);
            return null;
        });
    }

    private void chooseWinnerStrict(String candidateUuid, String actor) throws Exception {
        String electionId = currentElectionId();
        if (electionId == null) {
            return;
        }
        long t = now();
        tx(connection -> {
            ElectionContext context = requireActiveElectionContext(connection);
            if (!electionId.equals(context.electionId()) || context.stage() != ElectionStage.COUNTING) {
                throw new IllegalStateException("Победителя можно утвердить только на этапе подсчёта.");
            }
            int round = currentRoundFromDb(connection, electionId);
            Map<String, Object> candidate = queryOne(connection,
                    "SELECT candidate_name FROM round_candidates WHERE election_id=? AND round_no=? AND candidate_uuid=? AND active=1 LIMIT 1",
                    electionId, round, candidateUuid);
            if (candidate == null) {
                throw new IllegalStateException("Кандидат этого тура больше недоступен.");
            }
            update(connection, "UPDATE rounds SET status='COUNTED',ended_at=CASE WHEN ended_at=0 THEN ? ELSE ended_at END,winner_uuid=?,winner_name=? WHERE election_id=? AND round_no=?",
                    t, candidateUuid, string(candidate.get("candidate_name")), electionId, round);
            assignPresident(connection, electionId, candidateUuid, string(candidate.get("candidate_name")), actor, t);
            logPluginEvent(connection, "election_core", "winner_manual", actor, candidateUuid, "");
            return null;
        });
    }

    private void reviewLaw(String lawId, String decision, String actor, String note) throws Exception {
        long t = now();
        tx(connection -> {
            Map<String, Object> term = queryOne(connection,
                    "SELECT id,last_law_replace_at FROM president_terms WHERE status='ACTIVE' ORDER BY started_at DESC LIMIT 1 FOR UPDATE");
            if (term == null) {
                throw new IllegalStateException("Нет активного президентского срока.");
            }
            Map<String, Object> law = queryOne(connection, "SELECT * FROM president_laws WHERE id=? FOR UPDATE", lawId);
            if (law == null) {
                return null;
            }
            if (!string(term.get("id")).equals(string(law.get("term_id"))) || !"PENDING".equalsIgnoreCase(string(law.get("status")))) {
                throw new IllegalStateException("Этот закон уже нельзя пересмотреть.");
            }
            update(connection, "INSERT INTO president_law_reviews(law_id,reviewer,decision,note,created_at) VALUES(?,?,?,?,?)", lawId, actor, decision, note, t);
            if ("APPROVED".equalsIgnoreCase(decision)) {
                String replaced = string(law.get("replaced_law_id"));
                int slot = nextLawSlot(connection, string(law.get("term_id")));
                if (replaced.isBlank()
                        && scalarLong(connection, "SELECT COUNT(*) FROM president_laws WHERE term_id=? AND status='PUBLISHED'", string(law.get("term_id"))) >= 5) {
                    throw new IllegalStateException("У президента уже 5 законов. Используй замену.");
                }
                if (!replaced.isBlank()) {
                    if (t - longValue(term.get("last_law_replace_at")) < PRESIDENT_LAW_REPLACE_COOLDOWN_MS) {
                        throw new IllegalStateException("Закон можно заменять не чаще одного раза в 3 дня.");
                    }
                    Map<String, Object> replacedLaw = queryOne(connection,
                            "SELECT id,slot_no,status,term_id FROM president_laws WHERE id=? FOR UPDATE",
                            replaced);
                    if (replacedLaw == null
                            || !string(law.get("term_id")).equals(string(replacedLaw.get("term_id")))
                            || !"PUBLISHED".equalsIgnoreCase(string(replacedLaw.get("status")))) {
                        throw new IllegalStateException("Этот закон уже нельзя заменить.");
                    }
                    slot = Math.max(1, intValue(replacedLaw.get("slot_no")));
                    update(connection, "UPDATE president_laws SET status='REPLACED' WHERE id=?", replaced);
                    update(connection, "UPDATE president_terms SET last_law_replace_at=? WHERE id=?", t, string(law.get("term_id")));
                }
                update(connection, "UPDATE president_laws SET status='PUBLISHED',published_at=?,slot_no=? WHERE id=?", t, slot, lawId);
                logPluginEvent(connection, "election_core", "law_published", actor, lawId, string(law.get("text")));
            } else {
                update(connection, "UPDATE president_laws SET status='REJECTED' WHERE id=?", lawId);
                logPluginEvent(connection, "election_core", "law_rejected", actor, lawId, string(law.get("text")));
            }
            return null;
        });
    }

    private void sendPresidentBroadcast(Player player, String format, String text) throws Exception {
        Map<String, Object> term = activeTerm();
        if (term == null) {
            throw new IllegalStateException("Нет активного срока президента.");
        }
        long t = now();
        if (t - longValue(term.get("last_broadcast_at")) < PRESIDENT_BROADCAST_COOLDOWN_MS) {
            throw new IllegalStateException("Обращение можно отправлять раз в 1 час.");
        }
        tx(connection -> {
            update(connection, "INSERT INTO president_broadcasts(id,term_id,president_uuid,format,text,created_at) VALUES(?,?,?,?,?,?)",
                    "broadcast_" + UUID.randomUUID().toString().replace("-", ""), string(term.get("id")), player.getUniqueId().toString(), format, text, t);
            update(connection, "UPDATE president_terms SET last_broadcast_at=? WHERE id=?", t, string(term.get("id")));
            logPluginEvent(connection, "election_core", "broadcast_sent", player.getName(), format, text);
            return null;
        });
        for (Player online : Bukkit.getOnlinePlayers()) {
            switch (format.toUpperCase(Locale.ROOT)) {
                case "TITLE" -> online.sendTitle(color("&6Президент"), color("&f" + text), 10, 50, 10);
                case "ACTIONBAR" -> online.sendActionBar(color("&6Президент: &f" + text));
                default -> online.sendMessage(color("&6Президент &f" + player.getName() + "&7: &f" + text));
            }
        }
    }

    private void setPresidentTax(String actor, int amount) throws Exception {
        throw new IllegalStateException("\u041f\u0440\u0435\u0437\u0438\u0434\u0435\u043d\u0442\u0441\u043a\u0438\u0439 \u043d\u0430\u043b\u043e\u0433 \u043e\u0442\u043a\u043b\u044e\u0447\u0451\u043d.");
    }


    private void payTaxWithInventory(Player player, String taxId, long desired) throws Exception {
        throw new IllegalStateException("\u041f\u0440\u0435\u0437\u0438\u0434\u0435\u043d\u0442\u0441\u043a\u0438\u0439 \u043d\u0430\u043b\u043e\u0433 \u043e\u0442\u043a\u043b\u044e\u0447\u0451\u043d.");
    }


    private void payTaxFromBank(Player player, String taxId, String pin) throws Exception {
        throw new IllegalStateException("\u041f\u0440\u0435\u0437\u0438\u0434\u0435\u043d\u0442\u0441\u043a\u0438\u0439 \u043d\u0430\u043b\u043e\u0433 \u043e\u0442\u043a\u043b\u044e\u0447\u0451\u043d.");
    }


    private CopiMineEconomyCore.BankService requireEconomyBankService() {
        if (!(Bukkit.getPluginManager().getPlugin("CopiMineEconomyCore") instanceof CopiMineEconomyCore economy) || !economy.isEnabled()) {
            throw new IllegalStateException("CopiMineEconomyCore недоступен.");
        }
        return economy.bankService();
    }

    private Map<String, Object> requireActiveTaxRecord(String taxId) throws Exception {
        throw new IllegalStateException("\u041f\u0440\u0435\u0437\u0438\u0434\u0435\u043d\u0442\u0441\u043a\u0438\u0439 \u043d\u0430\u043b\u043e\u0433 \u043e\u0442\u043a\u043b\u044e\u0447\u0451\u043d.");
    }


    private ItemStack[] captureInventorySnapshot(Player player) {
        if (player == null) {
            return null;
        }
        ItemStack[] contents = player.getInventory().getContents();
        return Arrays.stream(contents)
                .map(stack -> stack == null ? null : stack.clone())
                .toArray(ItemStack[]::new);
    }

    private void restoreInventorySnapshot(Player player, ItemStack[] snapshot) {
        if (player == null || !player.isOnline() || snapshot == null) {
            return;
        }
        ItemStack[] restored = Arrays.stream(snapshot)
                .map(stack -> stack == null ? null : stack.clone())
                .toArray(ItemStack[]::new);
        player.getInventory().setContents(restored);
        player.updateInventory();
    }

    private void createTaxPaymentOperation(String operationId, String taxId, String playerUuid, String playerName, long amount, String source, String idempotencyKey, String details) throws Exception {
        tx(connection -> {
            update(connection, "INSERT INTO president_tax_payment_ops(id,tax_id,player_uuid,player_name,amount,source,status,bank_tx_id,idempotency_key,details,last_error,created_at,updated_at) VALUES(?,?,?,?,?,?,?,'',?,?,?, ?, ?) ON CONFLICT(id) DO NOTHING",
                    operationId, taxId, playerUuid, playerName, amount, source, "PENDING", idempotencyKey, details, "", now(), now());
            return null;
        });
    }

    private void markTaxPaymentOperation(String operationId, String status, String bankTxId, String lastError) throws Exception {
        update("UPDATE president_tax_payment_ops SET status=?,bank_tx_id=?,last_error=?,updated_at=? WHERE id=?", status, first(bankTxId, ""), first(lastError, ""), now(), operationId);
    }

    private void completeTaxPaymentOperation(String operationId, String bankTxId) throws Exception {
        tx(connection -> {
            Map<String, Object> op = queryOne(connection, "SELECT * FROM president_tax_payment_ops WHERE id=? FOR UPDATE", operationId);
            if (op == null || op.isEmpty()) {
                throw new IllegalStateException("tax operation missing");
            }
            if ("COMPLETED".equalsIgnoreCase(string(op.get("status")))) {
                return null;
            }
            TaxEconomyProof proof = resolveTaxPaymentEconomyProof(connection, string(op.get("source")), string(op.get("idempotency_key")), first(bankTxId, string(op.get("bank_tx_id"))));
            if (!proof.confirmed()) {
                throw new IllegalStateException("economy_proof_missing");
            }
            update(connection, "INSERT INTO president_tax_payments(id,tax_id,player_uuid,player_name,amount,source,created_at,details) VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(id) DO NOTHING",
                    "tax_payment_" + operationId, string(op.get("tax_id")), string(op.get("player_uuid")), string(op.get("player_name")), longValue(op.get("amount")), string(op.get("source")), now(), string(op.get("details")));
            update(connection, "UPDATE president_tax_payment_ops SET status='COMPLETED',bank_tx_id=?,last_error='',updated_at=? WHERE id=?", proof.bankTxId(), now(), operationId);
            logPluginEvent(connection, "election_core", "tax_paid", string(op.get("player_name")), string(op.get("tax_id")), "amount=" + longValue(op.get("amount")) + ";source=" + string(op.get("source")));
            return null;
        });
    }

    private boolean reconcileTaxPaymentOperation(String operationId) throws Exception {
        return tx(connection -> {
            Map<String, Object> op = queryOne(connection, "SELECT * FROM president_tax_payment_ops WHERE id=? FOR UPDATE", operationId);
            if (op == null || op.isEmpty()) {
                return false;
            }
            if ("COMPLETED".equalsIgnoreCase(string(op.get("status")))) {
                return true;
            }
            TaxEconomyProof proof = resolveTaxPaymentEconomyProof(connection, string(op.get("source")), string(op.get("idempotency_key")), string(op.get("bank_tx_id")));
            if (!proof.confirmed()) {
                update(connection, "UPDATE president_tax_payment_ops SET status='RECONCILE_REQUIRED',last_error=?,updated_at=? WHERE id=?", "economy_proof_missing", now(), operationId);
                return false;
            }
            update(connection, "UPDATE president_tax_payment_ops SET status='ECONOMY_CONFIRMED',bank_tx_id=?,last_error='',updated_at=? WHERE id=?", proof.bankTxId(), now(), operationId);
            return true;
        }) && finalizeReconciledTaxPayment(operationId);
    }

    private boolean isTaxPaymentOperationCompleted(String operationId) throws Exception {
        return "COMPLETED".equalsIgnoreCase(string(queryOne("SELECT status FROM president_tax_payment_ops WHERE id=? LIMIT 1", operationId).get("status")));
    }

    private void reconcilePendingTaxPaymentsSafe() {
        try {
            for (Map<String, Object> row : queryList("SELECT id FROM president_tax_payment_ops WHERE status='RECONCILE_REQUIRED' ORDER BY updated_at ASC LIMIT 200")) {
                try {
                    reconcileTaxPaymentOperation(string(row.get("id")));
                } catch (Exception error) {
                    getLogger().warning("tax reconcile: " + safeError(error));
                }
            }
        } catch (Exception error) {
            getLogger().warning("tax reconcile startup: " + safeError(error));
        }
    }

    private boolean finalizeReconciledTaxPayment(String operationId) throws Exception {
        try {
            completeTaxPaymentOperation(operationId, "");
            return true;
        } catch (IllegalStateException proofMissing) {
            return false;
        }
    }

    private TaxEconomyProof resolveTaxPaymentEconomyProof(Connection connection, String source, String idempotencyKey, String bankTxId) throws Exception {
        String proofTxId = first(bankTxId, "");
        if ("BANK".equalsIgnoreCase(source)) {
            Map<String, Object> row = queryOne(connection,
                    "SELECT tx_id FROM cmv4_bank_transfers WHERE status='COMMITTED' AND (tx_id=? OR idempotency_key=?) ORDER BY created_at DESC LIMIT 1",
                    proofTxId, first(idempotencyKey, ""));
            if (row != null && !row.isEmpty()) {
                return new TaxEconomyProof(true, first(string(row.get("tx_id")), first(proofTxId, first(idempotencyKey, ""))));
            }
        }
        if ("INVENTORY_AR".equalsIgnoreCase(source)) {
            Map<String, Object> row = queryOne(connection,
                    "SELECT tx_id FROM cmv4_bank_ledger WHERE status='COMMITTED' AND (tx_id=? OR idempotency_key=?) ORDER BY created_at DESC LIMIT 1",
                    proofTxId, first(idempotencyKey, ""));
            if (row != null && !row.isEmpty()) {
                return new TaxEconomyProof(true, first(string(row.get("tx_id")), first(proofTxId, first(idempotencyKey, ""))));
            }
        }
        return new TaxEconomyProof(false, "");
    }

    private void refreshSnapshotAndPush() {
        Runnable work = () -> {
            try {
                LiveSnapshot next = loadSnapshot();
                snapshot.set(next);
                writeWebData(next);
                Bukkit.getScheduler().runTask(this, () -> {
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        restoreOfficialItems(online);
                        if (liveHidden.contains(online.getUniqueId())) {
                            clearSidebar(online);
                        } else {
                            renderSidebar(online, next);
                        }
                    }
                });
            } catch (Exception error) {
                getLogger().warning("snapshot refresh: " + safeError(error));
            }
        };
        if (Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTaskAsynchronously(this, work);
        } else {
            work.run();
        }
    }

    private LiveSnapshot loadSnapshot() throws Exception {
        String electionId = currentElectionId();
        if (electionId == null) {
            return LiveSnapshot.empty();
        }
        Map<String, Object> election = queryOne("SELECT current_stage,current_round,candidate_limit,president_term_days,president_name,second_round_needed FROM elections WHERE id=?", electionId);
        int currentRound = Math.max(1, intValue(election.get("current_round")));
        List<CandidateResult> candidates = loadCandidateResults(electionId, currentRound);
        List<String> laws = publishedLaws().stream().map(row -> string(row.get("text"))).limit(5).toList();
        long deposited = scalarLong("SELECT COUNT(*) FROM votes WHERE election_id=? AND round_no=?", electionId, currentRound);
        long stations = scalarLong("SELECT COUNT(*) FROM polling_stations WHERE active=1");
        return new LiveSnapshot(
                electionId,
                ElectionStage.safeValue(string(election.get("current_stage"))),
                currentRound,
                string(election.get("president_name")),
                candidates,
                laws,
                intValue(election.get("candidate_limit")),
                intValue(election.get("president_term_days")),
                0,
                deposited,
                stations,
                intValue(election.get("second_round_needed")) > 0
        );
    }

    private void writeWebData(LiveSnapshot snap) throws Exception {
        writeWebDataStrict(snap);
    }

    private void writeWebDataStrict(LiveSnapshot snap) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("plugin", "CopiMineElectionCore");
        payload.put("generatedAt", now());
        payload.put("electionId", snap.electionId());
        payload.put("stage", snap.stage().name());
        payload.put("stageTitle", snap.stageTitle());
        payload.put("round", snap.round());
        payload.put("candidateLimit", snap.candidateLimit());
        payload.put("president", snap.presidentName());
        payload.put("laws", publishedLaws().stream()
                .map(row -> string(row.get("text")))
                .filter(text -> !text.isBlank())
                .limit(5)
                .toList());
        payload.put("taxAmount", snap.taxAmount());
        List<Map<String, Object>> candidateRows = new ArrayList<>();
        for (CandidateResult row : snap.candidates()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("candidate_uuid", row.uuid());
            item.put("candidate_name", row.name());
            item.put("votes", row.votes());
            item.put("bar", row.bar());
            candidateRows.add(item);
        }
        payload.put("candidates", candidateRows);
        payload.put("results", candidateRows);
        payload.put("turnout", Map.of(
                "issued_ballots", scalarLong("SELECT COUNT(*) FROM ballots WHERE election_id=? AND round_no=?", snap.electionId(), snap.round()),
                "confirmed_ballots", scalarLong("SELECT COUNT(*) FROM ballots WHERE election_id=? AND round_no=? AND status='CONFIRMED'", snap.electionId(), snap.round()),
                "deposited_ballots", scalarLong("SELECT COUNT(*) FROM ballots WHERE election_id=? AND round_no=? AND status='DEPOSITED'", snap.electionId(), snap.round()),
                "annulled_ballots", scalarLong("SELECT COUNT(*) FROM ballots WHERE election_id=? AND round_no=? AND status='ANNULLED'", snap.electionId(), snap.round())
        ));
        payload.put("stations", queryList("SELECT id,world,x,y,z,applications_issued,ballots_issued,ballots_submitted,ballots_annulled FROM polling_stations WHERE election_id=? AND active=1 ORDER BY created_at DESC LIMIT 100", snap.electionId()));
        Files.createDirectories(webDataFile.getParent());
        Files.writeString(webDataFile, toJson(payload), StandardCharsets.UTF_8);
    }

    private void renderSidebar(Player player, LiveSnapshot snap) {
        if (!player.isOnline()) {
            return;
        }
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }
        Scoreboard board = manager.getNewScoreboard();
        Objective objective = board.registerNewObjective(SIDEBAR_OBJECTIVE, "dummy", color("&6CopiMine"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank());
        List<String> lines = new ArrayList<>();
        lines.add("&fВыборы");
        lines.add("&7Этап: &f" + snap.stageTitle());
        lines.add("&7Тур: &f" + snap.round());
        lines.add("&fКандидаты");
        if (snap.candidates().isEmpty()) {
            lines.add("&7Пока нет кандидатов");
        } else {
            for (CandidateResult row : snap.candidates().stream().limit(5).toList()) {
                lines.add("&f" + shortText(row.name(), 10) + " &7" + row.bar() + " &f" + row.votes());
            }
        }
        lines.add("&fПрезидент");
        lines.add("&7" + first(snap.presidentName(), "не выбран"));
        if (!snap.laws().isEmpty()) {
            lines.add("&fЗаконы");
            int index = 1;
            for (String law : snap.laws()) {
                lines.add("&7" + index++ + ". " + shortText(law, 20));
            }
        }
        lines.add("&f/hidelive");
        int score = lines.size();
        int salt = 0;
        for (String line : lines) {
            String entry = color(line);
            while (board.getEntries().contains(entry)) {
                entry = color(line) + ChatColor.values()[salt++ % ChatColor.values().length];
            }
            objective.getScore(entry).setScore(score--);
        }
        player.setScoreboard(board);
    }

    private void clearSidebar(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }
        player.setScoreboard(manager.getMainScoreboard());
    }

    private void restoreOfficialItems(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (isPresident(player) && !hasMandate(player)) {
            try {
                Map<String, Object> term = activeTerm();
                if (term != null && Objects.equals(string(term.get("president_uuid")), player.getUniqueId().toString())) {
                    player.getInventory().addItem(createPresidentMandate(currentElectionId(), player.getUniqueId().toString(), player.getName()));
                }
            } catch (Exception error) {
                getLogger().warning("restore mandate: " + safeError(error));
            }
        }
        List<ItemStack> queued = officialRestore.remove(player.getUniqueId());
        if (queued != null) {
            for (ItemStack stack : queued) {
                if (stack != null && "CIK_SEAL".equals(readString(stack, itemTypeKey))
                        && hasOfficialItem(player, "CIK_SEAL", readString(stack, sealIdKey))) {
                    continue;
                }
                player.getInventory().addItem(stack);
            }
        }
        try {
            List<Map<String, Object>> seals = queryList(
                    "SELECT id,election_id,station_id,player_name FROM cik_seals WHERE player_uuid=? AND status='ACTIVE' ORDER BY issued_at DESC",
                    player.getUniqueId().toString()
            );
            for (Map<String, Object> seal : seals) {
                String sealId = string(seal.get("id"));
                if (!hasOfficialItem(player, "CIK_SEAL", sealId)) {
                    player.getInventory().addItem(createSealItem(
                            sealId,
                            string(seal.get("election_id")),
                            string(seal.get("station_id")),
                            player.getUniqueId().toString(),
                            first(string(seal.get("player_name")), player.getName())
                    ));
                }
            }
        } catch (Exception error) {
            getLogger().warning("restore seals: " + safeError(error));
        }
    }

    private void removeOfficialItemsFromPlayer(Player player, String type) {
        if (player == null) {
            return;
        }
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null) {
                continue;
            }
            if (type.equals(readString(stack, itemTypeKey))) {
                player.getInventory().remove(stack);
            }
        }
    }

    private void removeItemFromMainHandIfType(Player player, String type) {
        if (player == null) {
            return;
        }
        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (inHand != null && type.equals(readString(inHand, itemTypeKey))) {
            player.getInventory().setItemInMainHand(null);
        }
    }

    private void removeBallotFromInventory(Player player, String ballotId) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null) {
                continue;
            }
            if (ballotId.equals(readString(stack, ballotIdKey))) {
                player.getInventory().remove(stack);
            }
        }
    }

    private void replacePlayerBallot(Player player, String ballotId, String electionId, String stationId, int round, String candidateUuid, String candidateName) {
        removeBallotFromInventory(player, ballotId);
        player.getInventory().addItem(createBallotItem(ballotId, electionId, stationId, round, player.getUniqueId().toString(), player.getName(), true, candidateUuid, candidateName));
    }

    private boolean hasOfficialItem(Player player, String itemType, String itemId) {
        if (player == null) {
            return false;
        }
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null) {
                continue;
            }
            if (itemType.equals(readString(stack, itemTypeKey)) && itemId.equals(readString(stack, sealIdKey))) {
                return true;
            }
        }
        return false;
    }

    private SealContext revalidateSealContext(Player player, MenuHolder holder) throws Exception {
        SealContext sealContext = validateSealUsage(player.getInventory().getItemInMainHand(), player);
        if (sealContext == null) {
            throw new IllegalStateException("Не удалось проверить печать ЦИК.");
        }
        String expectedSealId = first(holder.data().get("seal_id"), "");
        String expectedStationId = first(holder.data().get("station_id"), "");
        String expectedElectionId = first(holder.data().get("election_id"), "");
        String expectedChairUuid = first(holder.data().get("chair_uuid"), "");
        if (!expectedSealId.equals(sealContext.sealId())
                || !expectedStationId.equals(sealContext.stationId())
                || !expectedElectionId.equals(sealContext.electionId())
                || !expectedChairUuid.equals(sealContext.playerUuid())) {
            throw new IllegalStateException("Эта печать ЦИК больше недействительна.");
        }
        return sealContext;
    }

    private void sendUserError(Player player, Exception error, String fallback) {
        if (player == null) {
            return;
        }
        String message = safeError(error);
        if (message == null || message.isBlank()) {
            player.sendMessage(color(fallback));
            return;
        }
        player.sendMessage(color("&c" + message));
    }

    private boolean hasActiveSeal(Player player, String stationId) throws Exception {
        if (player == null) {
            return false;
        }
        return scalarLong("SELECT COUNT(*) FROM cik_seals WHERE station_id=? AND player_uuid=? AND status='ACTIVE'", stationId, player.getUniqueId().toString()) > 0;
    }

    private boolean hasUsableActiveSeal(Player player, String stationId) throws Exception {
        if (player == null || stationId == null || stationId.isBlank()) {
            return false;
        }
        List<ItemStack> stacks = new ArrayList<>();
        stacks.add(player.getInventory().getItemInMainHand());
        stacks.add(player.getInventory().getItemInOffHand());
        Collections.addAll(stacks, player.getInventory().getContents());
        for (ItemStack stack : stacks) {
            SealContext context = validateSealUsage(stack, player);
            if (context != null && stationId.equals(context.stationId())) {
                return true;
            }
        }
        return false;
    }

    private SealContext validateSealUsage(ItemStack stack, Player player) throws Exception {
        if (player == null || !isCikSeal(stack)) {
            return null;
        }
        String sealId = readString(stack, sealIdKey);
        String electionId = readString(stack, electionIdKey);
        String stationId = readString(stack, stationIdKey);
        String playerUuid = readString(stack, playerUuidKey);
        if (sealId.isBlank() || electionId.isBlank() || stationId.isBlank() || playerUuid.isBlank()) {
            return null;
        }
        if (!player.getUniqueId().toString().equals(playerUuid)) {
            return null;
        }
        Map<String, Object> row = queryOne(
                "SELECT s.id,s.station_id,s.election_id,s.player_uuid,s.status,COALESCE(ps.active,0) station_active,COALESCE(e.active,0) election_active,COALESCE(c.active,0) chair_active " +
                        "FROM cik_seals s " +
                        "LEFT JOIN polling_stations ps ON ps.id=s.station_id " +
                        "LEFT JOIN elections e ON e.id=s.election_id " +
                        "LEFT JOIN cik_chairs c ON c.station_id=s.station_id AND c.player_uuid=s.player_uuid AND c.active=1 " +
                        "WHERE s.id=?",
                sealId
        );
        if (row == null) {
            return null;
        }
        if (!"ACTIVE".equalsIgnoreCase(string(row.get("status")))) {
            return null;
        }
        if (!sealId.equals(string(row.get("id")))
                || !stationId.equals(string(row.get("station_id")))
                || !electionId.equals(string(row.get("election_id")))
                || !playerUuid.equals(string(row.get("player_uuid")))) {
            return null;
        }
        if (intValue(row.get("station_active")) <= 0 || intValue(row.get("election_active")) <= 0 || intValue(row.get("chair_active")) <= 0) {
            return null;
        }
        String activeElectionId = currentElectionId();
        if (activeElectionId == null || !activeElectionId.equals(electionId)) {
            return null;
        }
        return new SealContext(sealId, electionId, stationId, playerUuid);
    }

    private boolean isChair(Player player) {
        try {
            return scalarLong("SELECT COUNT(*) FROM cik_chairs WHERE player_uuid=? AND active=1", player.getUniqueId().toString()) > 0
                    || player.hasPermission("copimine.election.cik");
        } catch (Exception error) {
            return player.hasPermission("copimine.election.cik");
        }
    }

    private boolean isPresident(Player player) {
        try {
            return scalarLong("SELECT COUNT(*) FROM president_terms WHERE president_uuid=? AND status='ACTIVE'", player.getUniqueId().toString()) > 0
                    || player.hasPermission("copimine.election.president");
        } catch (Exception error) {
            return player.hasPermission("copimine.election.president");
        }
    }

    private boolean hasElectionAdmin(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        return player.isOp() || player.hasPermission("copimine.admin") || player.hasPermission("copimine.election.admin");
    }

    private boolean isChairForStation(Player player, Map<String, Object> station) {
        return player.isOp()
                || player.hasPermission("copimine.election.admin")
                || player.getUniqueId().toString().equals(string(station.get("chair_uuid")));
    }

    private void requireChairAccess(Player player, String stationId) throws Exception {
        if (player == null || stationId == null || stationId.isBlank()) {
            throw new IllegalStateException("Участок председателя не найден.");
        }
        Map<String, Object> station = queryOne(
                "SELECT chair_uuid,active FROM polling_stations WHERE id=? LIMIT 1",
                stationId
        );
        if (station == null || intValue(station.get("active")) <= 0) {
            throw new IllegalStateException("Участок председателя больше не активен.");
        }
        if (!isChairForStation(player, station)) {
            throw new IllegalStateException("У тебя нет доступа к этому участку.");
        }
        if (player.hasPermission("copimine.election.admin") || player.isOp()) {
            return;
        }
        if (scalarLong(
                "SELECT COUNT(*) FROM cik_chairs WHERE station_id=? AND player_uuid=? AND active=1",
                stationId,
                player.getUniqueId().toString()
        ) <= 0) {
            throw new IllegalStateException("У тебя нет доступа к этому участку.");
        }
    }

    private String stationIdForChair(Player player) throws Exception {
        Map<String, Object> row = queryOne("SELECT station_id FROM cik_chairs WHERE player_uuid=? AND active=1 ORDER BY assigned_at DESC LIMIT 1", player.getUniqueId().toString());
        return row == null ? "" : string(row.get("station_id"));
    }

    private boolean hasApplicationInElection(String playerUuid, String electionId) throws Exception {
        return scalarLong("SELECT COUNT(*) FROM candidate_applications WHERE election_id=? AND player_uuid=?", electionId, playerUuid) > 0;
    }

    private boolean hasActiveBallot(String playerUuid, String electionId, int round) throws Exception {
        return scalarLong("SELECT COUNT(*) FROM ballots WHERE election_id=? AND round_no=? AND player_uuid=? AND status IN ('ISSUED','CONFIRMED','DEPOSITED')",
                electionId, round, playerUuid) > 0;
    }

    private boolean stationMatchesOrFallback(String expectedStationId, String clickedStationId) throws Exception {
        if (expectedStationId.equals(clickedStationId)) {
            return true;
        }
        return scalarLong("SELECT COUNT(*) FROM polling_stations WHERE id=? AND active=1", expectedStationId) == 0L;
    }

    private boolean stationMatchesOrFallback(Connection connection, String expectedStationId, String clickedStationId) throws Exception {
        if (expectedStationId.equals(clickedStationId)) {
            return true;
        }
        return scalarLong(connection, "SELECT COUNT(*) FROM polling_stations WHERE id=? AND active=1", expectedStationId) == 0L;
    }

    private String currentElectionId() throws Exception {
        Map<String, Object> row = queryOne("SELECT id FROM elections WHERE active=1 ORDER BY updated_at DESC LIMIT 1");
        return row == null ? null : string(row.get("id"));
    }

    private String requireActiveElectionId() throws Exception {
        String electionId = currentElectionId();
        if (electionId == null || electionId.isBlank()) {
            throw new IllegalStateException("Нет активных выборов.");
        }
        return electionId;
    }

    private String ensureElectionExists(String actor) throws Exception {
        String current = currentElectionId();
        if (current != null) {
            return current;
        }
        String electionId = "election_" + UUID.randomUUID().toString().replace("-", "");
        long t = now();
        tx(connection -> {
            update(connection, "UPDATE elections SET active=0 WHERE active=1");
            update(connection,
                    "INSERT INTO elections(id,status,started_at,scheduled_end_at,started_by,ended_at,ended_by,winner_uuid,winner_name,notes,current_stage,current_round,candidate_limit,president_term_days,manual_winner_uuid,manual_winner_name,president_uuid,president_name,active,second_round_needed,created_at,updated_at) VALUES(?,?,?,0,?,0,'','','','',?,?,?,?,'','','','',1,0,?,?)",
                    electionId, "ACTIVE", t, actor, ElectionStage.PREPARATION.name(), 1, 4, 7, t, t);
            update(connection, "INSERT INTO rounds(id,election_id,round_no,status,started_at,ended_at,winner_uuid,winner_name) VALUES(?,?,?,?,?,0,'','')",
                    "round_" + electionId + "_1", electionId, 1, "ACTIVE", t);
            update(connection, "INSERT INTO election_stages(election_id,stage,round_no,actor,created_at,notes) VALUES(?,?,?,?,?,?)",
                    electionId, ElectionStage.PREPARATION.name(), 1, actor, t, "Старт новых выборов");
            logPluginEvent(connection, "election_core", "election_started", actor, electionId, "");
            return null;
        });
        return electionId;
    }

    private void stopElection(String actor) throws Exception {
        String electionId = currentElectionId();
        if (electionId == null) {
            return;
        }
        long t = now();
        tx(connection -> {
            int round = currentRoundFromDb(connection, electionId);
            update(connection, "UPDATE elections SET active=0,status='STOPPED',updated_at=?,ended_at=?,ended_by=? WHERE id=?", t, t, actor, electionId);
            update(connection, "INSERT INTO election_stages(election_id,stage,round_no,actor,created_at,notes) VALUES(?,?,?,?,?,?)",
                    electionId, ElectionStage.NONE.name(), round, actor, t, "Остановка выборов");
            logPluginEvent(connection, "election_core", "election_stopped", actor, electionId, "");
            return null;
        });
    }

    private void setStage(String electionId, ElectionStage stage, String actor, String notes) throws Exception {
        if (electionId == null) {
            return;
        }
        long t = now();
        tx(connection -> {
            ElectionStage from = currentStageFromDb(connection, electionId);
            StageTransitionResult transition = validateStageTransition(connection, electionId, from, stage);
            if (!transition.allowed()) {
                throw new IllegalStateException(transition.message());
            }
            int round = currentRoundFromDb(connection, electionId);
            update(connection, "UPDATE elections SET current_stage=?,updated_at=? WHERE id=?", stage.name(), t, electionId);
            update(connection, "INSERT INTO election_stages(election_id,stage,round_no,actor,created_at,notes) VALUES(?,?,?,?,?,?)",
                    electionId, stage.name(), round, actor, t, notes);
            logPluginEvent(connection, "election_core", "stage_changed", actor, electionId, stage.name());
            return null;
        });
    }

    private void resetElections(String actor) throws Exception {
        tx(connection -> {
            for (String table : List.of(
                    "votes",
                    "ballots",
                    "round_candidates",
                    "candidates",
                    "candidate_applications",
                    "cik_seals",
                    "cik_chairs",
                    "polling_stations",
                    "rounds",
                    "election_stages",
                    "president_law_reviews",
                    "president_laws",
                    "president_broadcasts",
                    "president_tax_payments",
                    "president_taxes",
                    "president_terms",
                    "elections"
            )) {
                update(connection, "DELETE FROM " + table);
            }
            update(connection, "DELETE FROM protected_blocks WHERE kind IN ('POLLING_STATION','TAX_OFFICE')");
            update(connection, "DELETE FROM text_display_links WHERE kind IN ('STATION_LABEL','TAX_LABEL')");
            update(connection, "DELETE FROM protected_block_visuals WHERE kind IN ('POLLING_STATION','TAX_OFFICE')");
            logPluginEvent(connection, "election_core", "election_reset", actor, "", "");
            return null;
        });
        protectedBlocks.clear();
        officialRestore.clear();
        for (Player online : Bukkit.getOnlinePlayers()) {
            removeOfficialItemsFromPlayer(online, "CIK_SEAL");
            removeOfficialItemsFromPlayer(online, "APPLICATION_BOOK");
            removeOfficialItemsFromPlayer(online, "BALLOT");
            removeOfficialItemsFromPlayer(online, "PRESIDENT_MANDATE");
        }
    }

    private int currentRoundNumber() throws Exception {
        String electionId = currentElectionId();
        if (electionId == null) {
            return 1;
        }
        try (Connection connection = openConnection()) {
            return currentRoundFromDb(connection, electionId);
        }
    }

    private ElectionStage currentStageFromDb(Connection connection, String electionId) throws Exception {
        Map<String, Object> row = queryOne(connection, "SELECT current_stage FROM elections WHERE id=? LIMIT 1", electionId);
        return row == null ? ElectionStage.NONE : ElectionStage.safeValue(string(row.get("current_stage")));
    }

    private int currentRoundFromDb(Connection connection, String electionId) throws Exception {
        Map<String, Object> row = queryOne(connection, "SELECT current_round FROM elections WHERE id=? LIMIT 1", electionId);
        return Math.max(1, row == null ? 1 : intValue(row.get("current_round")));
    }

    private ElectionContext requireActiveElectionContext(Connection connection) throws Exception {
        Map<String, Object> row = queryOne(connection,
                "SELECT id,current_stage,current_round,candidate_limit,president_term_days,manual_winner_uuid,manual_winner_name,president_uuid,president_name,second_round_needed " +
                        "FROM elections WHERE active=1 ORDER BY updated_at DESC LIMIT 1 FOR UPDATE");
        if (row == null) {
            throw new IllegalStateException("Нет активных выборов.");
        }
        return new ElectionContext(
                string(row.get("id")),
                ElectionStage.safeValue(string(row.get("current_stage"))),
                Math.max(1, intValue(row.get("current_round"))),
                intValue(row.get("candidate_limit")),
                intValue(row.get("president_term_days")),
                string(row.get("manual_winner_uuid")),
                string(row.get("manual_winner_name")),
                string(row.get("president_uuid")),
                string(row.get("president_name")),
                intValue(row.get("second_round_needed")) > 0
        );
    }

    private int countActiveRoundCandidates(Connection connection, String electionId, int round) throws Exception {
        return (int) scalarLong(connection,
                "SELECT COUNT(*) FROM round_candidates WHERE election_id=? AND round_no=? AND active=1",
                electionId, round);
    }

    private int countActiveStations(Connection connection, String electionId) throws Exception {
        return (int) scalarLong(connection,
                "SELECT COUNT(*) FROM polling_stations WHERE election_id=? AND active=1",
                electionId);
    }

    private int countTiedLeaders(Connection connection, String electionId, int round) throws Exception {
        List<Map<String, Object>> rows = queryList(connection,
                "SELECT rc.candidate_uuid,COUNT(v.id) AS votes " +
                        "FROM round_candidates rc " +
                        "LEFT JOIN votes v ON v.election_id=rc.election_id AND v.round_no=rc.round_no AND v.candidate_uuid=rc.candidate_uuid " +
                        "WHERE rc.election_id=? AND rc.round_no=? AND rc.active=1 " +
                        "GROUP BY rc.candidate_uuid ORDER BY votes DESC",
                electionId, round);
        if (rows.isEmpty()) {
            return 0;
        }
        int maxVotes = intValue(rows.getFirst().get("votes"));
        int ties = 0;
        for (Map<String, Object> row : rows) {
            if (intValue(row.get("votes")) == maxVotes) {
                ties++;
            }
        }
        return ties;
    }

    private StageTransitionResult validateStageTransition(Connection connection, String electionId, ElectionStage from, ElectionStage to) throws Exception {
        return electionStateMachine.validateStageTransition(connection, electionId, from, to);
    }

    private Map<String, Object> activeTerm() throws Exception {
        return queryOne("SELECT * FROM president_terms WHERE status='ACTIVE' ORDER BY started_at DESC LIMIT 1");
    }

    public Map<String, Object> activePresidentRevenueProfile() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("term_id", "");
        payload.put("president_uuid", "");
        payload.put("president_name", "Казна CopiMine");
        payload.put("budget_account_id", "PRESIDENT_BUDGET");
        try {
            Map<String, Object> term = activeTerm();
            if (term != null) {
                payload.put("term_id", string(term.get("id")));
                payload.put("president_uuid", string(term.get("president_uuid")));
            }
        } catch (Exception ignored) {
        }
        return payload;
    }

    private Map<String, Object> activeTax() throws Exception {
        return null;
    }


    private TaxRecipient taxRecipient(Connection connection, String taxId) throws Exception {
        throw new IllegalStateException("\u041f\u0440\u0435\u0437\u0438\u0434\u0435\u043d\u0442\u0441\u043a\u0438\u0439 \u043d\u0430\u043b\u043e\u0433 \u043e\u0442\u043a\u043b\u044e\u0447\u0451\u043d.");
    }


    private List<Map<String, Object>> pendingLaws() {
        try {
            Map<String, Object> term = activeTerm();
            if (term == null) {
                return List.of();
            }
            return queryList("SELECT * FROM president_laws WHERE term_id=? AND status='PENDING' ORDER BY created_at DESC", string(term.get("id")));
        } catch (Exception error) {
            return List.of();
        }
    }

    private List<Map<String, Object>> publishedLaws() {
        try {
            Map<String, Object> term = activeTerm();
            if (term == null) {
                return List.of();
            }
            return queryList("SELECT * FROM president_laws WHERE term_id=? AND status='PUBLISHED' ORDER BY slot_no ASC, published_at DESC", string(term.get("id")));
        } catch (Exception error) {
            return List.of();
        }
    }

    private List<Map<String, Object>> currentTaxPayments() {
        try {
            return queryList(
                    "SELECT buyer_name AS player_name, amount_ar AS amount, 'ARTIFACT_SHOP' AS source, created_at FROM artifact_revenue_payouts WHERE recipient_account_id='PRESIDENT_BUDGET' AND status='CREDITED' ORDER BY created_at DESC"
            );
        } catch (Exception error) {
            return List.of();
        }
    }

    private long paidTaxAmount(String playerUuid, String taxId) throws Exception {
        return 0L;
    }


    private long dueTaxAmount(String playerUuid) throws Exception {
        return 0L;
    }


    private long dueTaxAmount(String playerUuid, String taxId, Map<String, Object> tax) throws Exception {
        return 0L;
    }


    private int nextLawSlot() throws Exception {
        Map<String, Object> term = activeTerm();
        if (term == null) {
            return 1;
        }
        return nextLawSlot(string(term.get("id")));
    }

    private int nextLawSlot(String termId) throws Exception {
        return (int) scalarLong("SELECT COALESCE(MAX(slot_no),0)+1 FROM president_laws WHERE term_id=? AND status='PUBLISHED'", termId);
    }

    private int nextLawSlot(Connection connection, String termId) throws Exception {
        return (int) scalarLong(connection, "SELECT COALESCE(MAX(slot_no),0)+1 FROM president_laws WHERE term_id=? AND status='PUBLISHED'", termId);
    }

    private List<CandidateResult> loadCandidateResults(String electionId, int round) throws Exception {
        if (electionId == null || electionId.isBlank()) {
            return List.of();
        }
        List<Map<String, Object>> rows = queryList(
                "SELECT rc.candidate_uuid AS player_uuid,COALESCE(NULLIF(rc.candidate_name,''),c.player_name) AS player_name,COUNT(v.id) AS votes " +
                        "FROM round_candidates rc " +
                        "LEFT JOIN candidates c ON c.election_id=rc.election_id AND c.player_uuid=rc.candidate_uuid " +
                        "LEFT JOIN votes v ON v.election_id=rc.election_id AND v.round_no=rc.round_no AND v.candidate_uuid=rc.candidate_uuid " +
                        "WHERE rc.election_id=? AND rc.round_no=? AND rc.active=1 " +
                        "GROUP BY rc.candidate_uuid,rc.candidate_name,c.player_name " +
                        "ORDER BY votes DESC,player_name ASC",
                electionId, round
        );
        int maxVotes = rows.stream().mapToInt(row -> intValue(row.get("votes"))).max().orElse(0);
        List<CandidateResult> results = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            int votes = intValue(row.get("votes"));
            int barCount = maxVotes <= 0 ? 1 : Math.max(1, (int) Math.round((votes * 10.0) / maxVotes));
            results.add(new CandidateResult(
                    string(row.get("player_uuid")),
                    first(string(row.get("player_name")), "Кандидат"),
                    votes,
                    "в–€".repeat(Math.max(1, Math.min(10, barCount)))
            ));
        }
        return results;
    }

    private List<Map<String, Object>> activeCandidates() throws Exception {
        String electionId = currentElectionId();
        if (electionId == null) {
            return List.of();
        }
        return activeCandidates(electionId, currentRoundNumber());
    }

    private List<Map<String, Object>> activeCandidates(String electionId, int round) throws Exception {
        if (electionId == null || electionId.isBlank()) {
            return List.of();
        }
        return queryList(
                "SELECT rc.candidate_uuid AS player_uuid,COALESCE(NULLIF(rc.candidate_name,''),c.player_name) AS player_name,c.application_id " +
                        "FROM round_candidates rc " +
                        "LEFT JOIN candidates c ON c.election_id=rc.election_id AND c.player_uuid=rc.candidate_uuid " +
                        "WHERE rc.election_id=? AND rc.round_no=? AND rc.active=1 " +
                        "ORDER BY player_name ASC",
                electionId, round
        );
    }

    private String candidateApplicationId(String candidateUuid) {
        try {
            Map<String, Object> row = queryOne("SELECT application_id FROM candidates WHERE election_id=? AND player_uuid=? AND active=1", currentElectionId(), candidateUuid);
            return row == null ? null : string(row.get("application_id"));
        } catch (Exception error) {
            return null;
        }
    }

    private void openApplicationBook(Player player, String applicationId) throws Exception {
        Map<String, Object> row = applicationById(applicationId);
        if (row == null) {
            player.sendMessage(color("&cЗаявка не найдена."));
            return;
        }
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle("Заявка кандидата");
        meta.setAuthor(first(string(row.get("player_name")), "CopiMine"));
        meta.addPage(first(string(row.get("answers")), "Текст заявки пока пуст."));
        book.setItemMeta(meta);
        player.openBook(book);
    }

    private Map<String, Object> stationById(String stationId) {
        try {
            return queryOne("SELECT * FROM polling_stations WHERE id=?", stationId);
        } catch (Exception error) {
            return null;
        }
    }

    private Map<String, Object> applicationById(String applicationId) {
        try {
            return queryOne("SELECT * FROM candidate_applications WHERE id=?", applicationId);
        } catch (Exception error) {
            return null;
        }
    }

    private Map<String, Object> ballotById(String ballotId) {
        try {
            return queryOne("SELECT * FROM ballots WHERE id=?", ballotId);
        } catch (Exception error) {
            return null;
        }
    }

    private Player playerByBallot(String ballotId) {
        try {
            Map<String, Object> row = queryOne("SELECT player_uuid FROM ballots WHERE id=?", ballotId);
            return row == null ? null : Bukkit.getPlayer(UUID.fromString(string(row.get("player_uuid"))));
        } catch (Exception error) {
            return null;
        }
    }

    private ProtectedBlockInfo protectedBlockInfo(Block block) {
        BlockKey key = BlockKey.from(block.getLocation());
        if (!protectedBlocks.contains(key)) {
            return null;
        }
        try {
            Map<String, Object> row = queryOne("SELECT kind,linked_id FROM protected_blocks WHERE world=? AND x=? AND y=? AND z=? AND active=1",
                    block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
            return row == null ? null : new ProtectedBlockInfo(string(row.get("kind")), string(row.get("linked_id")));
        } catch (Exception error) {
            return null;
        }
    }

    private boolean handleProtectedVisualInteract(Player player, Entity entity, PlayerInteractEntityEvent event) {
        if (!(entity instanceof ItemDisplay display)) {
            return false;
        }
        if (!"PROTECTED_BLOCK_VISUAL".equals(readString(display.getPersistentDataContainer(), visualEntityTypeKey))) {
            return false;
        }
        event.setCancelled(true);
        String linkedId = readString(display.getPersistentDataContainer(), visualLinkedIdKey);
        String kind = readString(display.getPersistentDataContainer(), visualKindKey);
        if (linkedId.isBlank() || kind.isBlank()) {
            player.sendMessage(color("&cЭта визуальная точка больше недействительна."));
            return true;
        }
        try {
            if ("TAX_OFFICE".equals(kind)) {
                openTaxOfficeMenu(player, linkedId, "", null);
                return true;
            }
            if ("POLLING_STATION".equals(kind)) {
                Map<String, Object> station = stationById(linkedId);
                if (station == null) {
                    player.sendMessage(color("&cУчасток больше не найден."));
                    return true;
                }
                if (isChairForStation(player, station)) {
                    openChairStationMenu(player, linkedId, 0);
                } else {
                    player.sendMessage(color("&eУчасток ЦИК. Этап: &f" + snapshot.get().stageTitle()));
                }
                return true;
            }
        } catch (Exception error) {
            getLogger().warning("visual interact: " + safeError(error));
            player.sendMessage(color("&cНе удалось открыть связанную точку."));
            return true;
        }
        return false;
    }

    private void teleportToStation(Player player, String stationId) {
        Map<String, Object> station = stationById(stationId);
        if (station == null) {
            player.sendMessage(color("&cУчасток не найден."));
            return;
        }
        World world = Bukkit.getWorld(string(station.get("world")));
        if (world == null) {
            player.sendMessage(color("&cМир участка не найден."));
            return;
        }
        Location location = safeStationTeleportLocation(world, intValue(station.get("x")), intValue(station.get("y")), intValue(station.get("z")));
        if (location == null) {
            player.sendMessage(color("&cНе удалось найти безопасную точку рядом с участком."));
            return;
        }
        player.teleport(location, PlayerTeleportEvent.TeleportCause.PLUGIN);
    }

    private Location safeStationTeleportLocation(World world, int baseX, int baseY, int baseZ) {
        for (int radius = 0; radius <= 2; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    Location safe = safeStationTeleportLocationAt(world, baseX + dx, baseY + 1, baseZ + dz);
                    if (safe != null) {
                        return safe;
                    }
                }
            }
        }
        int highestY = Math.max(world.getMinHeight() + 1, world.getHighestBlockYAt(baseX, baseZ) + 1);
        return safeStationTeleportLocationAt(world, baseX, highestY, baseZ);
    }

    private Location safeStationTeleportLocationAt(World world, int x, int y, int z) {
        int minY = world.getMinHeight() + 1;
        int maxY = world.getMaxHeight() - 2;
        int from = Math.max(minY, Math.min(maxY, y + 2));
        int to = Math.max(minY, Math.min(maxY, y - 4));
        for (int currentY = from; currentY >= to; currentY--) {
            if (isSafeStationStandingLocation(world, x, currentY, z)) {
                return new Location(world, x + 0.5D, currentY, z + 0.5D);
            }
        }
        return null;
    }

    private boolean isSafeStationStandingLocation(World world, int x, int y, int z) {
        if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 1) {
            return false;
        }
        Material feet = world.getBlockAt(x, y, z).getType();
        Material head = world.getBlockAt(x, y + 1, z).getType();
        Material ground = world.getBlockAt(x, y - 1, z).getType();
        return !feet.isSolid() && !head.isSolid() && ground.isSolid()
                && !isUnsafeTeleportMaterial(feet)
                && !isUnsafeTeleportMaterial(head)
                && !isUnsafeTeleportMaterial(ground);
    }

    private boolean isUnsafeTeleportMaterial(Material material) {
        return switch (material) {
            case LAVA, FIRE, SOUL_FIRE, CAMPFIRE, SOUL_CAMPFIRE, CACTUS, MAGMA_BLOCK,
                    END_PORTAL, END_PORTAL_FRAME, NETHER_PORTAL, POWDER_SNOW, SWEET_BERRY_BUSH,
                    WITHER_ROSE, VOID_AIR -> true;
            default -> false;
        };
    }

    private List<String> stationLore(Map<String, Object> station, String stationId) {
        return List.of(
                "&7Координаты: &f" + intValue(station.get("x")) + ", " + intValue(station.get("y")) + ", " + intValue(station.get("z")),
                "&7Мир: &f" + string(station.get("world")),
                "&7Председатель: &f" + first(string(station.get("chair_name")), "не назначен"),
                "&7Статус председателя: " + (isPlayerOnline(string(station.get("chair_uuid"))) ? "&aонлайн" : "&7офлайн"),
                "&7Выдано заявок: &f" + longValue(station.get("applications_issued")),
                "&7Выдано бюллетеней: &f" + longValue(station.get("ballots_issued")),
                "&7Сдано бюллетеней: &f" + longValue(station.get("ballots_submitted")),
                "&7Аннулировано бюллетеней: &f" + longValue(station.get("ballots_annulled"))
        );
    }

    private List<String> stationStatsLore(String stationId) {
        try {
            long applications = scalarLong("SELECT COUNT(*) FROM candidate_applications WHERE station_id=?", stationId);
            long ballots = scalarLong("SELECT COUNT(*) FROM ballots WHERE station_id=?", stationId);
            long deposited = scalarLong("SELECT COUNT(*) FROM votes WHERE station_id=?", stationId);
            return List.of(
                    "&7Заявок: &f" + applications,
                    "&7Бюллетеней: &f" + ballots,
                    "&7Голосов принято: &f" + deposited
            );
        } catch (Exception error) {
            return List.of("&7Не удалось загрузить статистику участка.");
        }
    }

    private List<String> buildLimitLore(int current) {
        return List.of("&7Текущий лимит: &f" + formatLimit(current), "&7Выбери одно из значений ниже.");
    }

    private List<String> buildTermLore(int current) {
        return List.of("&7Текущий срок: &f" + current + " дн.", "&7Выбери длительность ниже.");
    }

    private String humanRecommendation(String value) {
        return switch (value == null ? "" : value.toUpperCase(Locale.ROOT)) {
            case "RECOMMEND" -> "рекомендовать";
            case "NOT_RECOMMEND" -> "не рекомендовать";
            default -> "нет";
        };
    }

    private String humanApplicationStatus(String value) {
        return switch (value == null ? "" : value.toUpperCase(Locale.ROOT)) {
            case "APPROVED" -> "одобрена";
            case "REJECTED" -> "отклонена";
            case "RECOMMENDED" -> "рекомендована";
            case "NOT_RECOMMENDED" -> "не рекомендована";
            default -> "не рассмотрена";
        };
    }

    private String formatLimit(int value) {
        return value < 0 ? "без ограничения" : String.valueOf(value);
    }

    private String formatLimitLabel(int value) {
        return value < 0 ? "Без лимита" : String.valueOf(value);
    }

    private boolean isPlayerOnline(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return false;
        }
        try {
            return Bukkit.getPlayer(UUID.fromString(uuid)) != null;
        } catch (Exception error) {
            return false;
        }
    }

    private void openTaxOfficeMenu(Player player, String taxId, String mode, Object ignored) {
        openTaxOfficeMenu(player, taxId, mode, (String) null);
    }

    private ItemStack createApplicationBook(String applicationId, String electionId, String stationId, String playerUuid) {
        ItemStack stack = new ItemStack(Material.WRITABLE_BOOK);
        BookMeta meta = (BookMeta) stack.getItemMeta();
        meta.setTitle("Заявка кандидата");
        meta.setAuthor("CopiMine");
        meta.setPages(List.of(
                "1. Почему ты хочешь стать президентом?\\n\\n2. Что ты изменишь на сервере?",
                "3. Как ты будешь развивать экономику?\\n\\n4. Как ты будешь решать конфликты игроков?",
                "5. Какие законы хочешь предложить?\\n\\nПодпиши книгу и сдай её через свой участок."
        ));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(itemTypeKey, PersistentDataType.STRING, "APPLICATION_BOOK");
        pdc.set(applicationIdKey, PersistentDataType.STRING, applicationId);
        pdc.set(electionIdKey, PersistentDataType.STRING, electionId);
        pdc.set(stationIdKey, PersistentDataType.STRING, stationId);
        pdc.set(playerUuidKey, PersistentDataType.STRING, playerUuid);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack createBallotItem(String ballotId, String electionId, String stationId, int round, String playerUuid, String playerName, boolean confirmed, String candidateUuid, String candidateName) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(color(confirmed ? "&aБюллетень подтверждён" : "&fБюллетень"));
        List<String> lore = new ArrayList<>();
        lore.add(color("&7Тур: &f" + round));
        lore.add(color("&7Участок: &f" + shortId(stationId)));
        lore.add(color("&7Игрок: &f" + playerName));
        lore.add(color(confirmed ? "&7Выбор: &f" + first(candidateName, "подтверждён") : "&7ПКМ в голосование откроет список кандидатов."));
        if (confirmed && !lore.isEmpty()) {
            lore.set(lore.size() - 1, color("&7Статус: &aГолос подтверждён"));
        }
        meta.setLore(lore);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(itemTypeKey, PersistentDataType.STRING, "BALLOT");
        pdc.set(ballotIdKey, PersistentDataType.STRING, ballotId);
        pdc.set(electionIdKey, PersistentDataType.STRING, electionId);
        pdc.set(stationIdKey, PersistentDataType.STRING, stationId);
        pdc.set(playerUuidKey, PersistentDataType.STRING, playerUuid);
        pdc.set(roundKey, PersistentDataType.STRING, Integer.toString(round));
        if (confirmed) {
            pdc.set(new NamespacedKey(this, "confirmed_candidate_uuid"), PersistentDataType.STRING, candidateUuid);
            pdc.set(new NamespacedKey(this, "confirmed_candidate_name"), PersistentDataType.STRING, candidateName);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack createSealItem(String sealId, String electionId, String stationId, String playerUuid, String playerName) {
        ItemStack stack = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(color("&bПечать ЦИК"));
        meta.setLore(List.of(
                color("&7Участок: &f" + shortId(stationId)),
                color("&7Председатель: &f" + playerName),
                color("&7ПКМ по игроку откроет выдачу.")
        ));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(itemTypeKey, PersistentDataType.STRING, "CIK_SEAL");
        pdc.set(sealIdKey, PersistentDataType.STRING, sealId);
        pdc.set(electionIdKey, PersistentDataType.STRING, electionId);
        pdc.set(stationIdKey, PersistentDataType.STRING, stationId);
        pdc.set(playerUuidKey, PersistentDataType.STRING, playerUuid);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack createPresidentMandate(String electionId, String playerUuid, String playerName) {
        ItemStack stack = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(color("&6Мандат президента"));
        meta.setLore(List.of(
                color("&7Президент: &f" + playerName),
                color("&7ПКМ откроет меню президента."),
                color("&8Предмет нельзя выбросить и спрятать в контейнер.")
        ));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(itemTypeKey, PersistentDataType.STRING, "PRESIDENT_MANDATE");
        pdc.set(electionIdKey, PersistentDataType.STRING, electionId);
        pdc.set(playerUuidKey, PersistentDataType.STRING, playerUuid);
        stack.setItemMeta(meta);
        return stack;
    }

    private boolean hasMandate(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isPresidentMandate(stack)) {
                return true;
            }
        }
        return false;
    }

    private boolean isApplicationBook(ItemStack stack) {
        return "APPLICATION_BOOK".equals(readString(stack, itemTypeKey));
    }

    private boolean isBallot(ItemStack stack) {
        return "BALLOT".equals(readString(stack, itemTypeKey));
    }

    private boolean isCikSeal(ItemStack stack) {
        return "CIK_SEAL".equals(readString(stack, itemTypeKey));
    }

    private boolean isPresidentMandate(ItemStack stack) {
        return "PRESIDENT_MANDATE".equals(readString(stack, itemTypeKey));
    }

    private boolean isProtectedOfficialItem(ItemStack stack) {
        return isBallot(stack) || isCikSeal(stack) || isPresidentMandate(stack);
    }

    private void destroyOneCikSeal(Player player, String sealId) {
        if (player == null || sealId == null || sealId.isBlank()) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!isCikSeal(stack) || !sealId.equals(readString(stack, sealIdKey))) {
                continue;
            }
            if (stack.getAmount() <= 1) {
                inventory.setItem(slot, null);
            } else {
                stack.setAmount(stack.getAmount() - 1);
                inventory.setItem(slot, stack);
            }
            player.updateInventory();
            return;
        }
    }

    private boolean isConfirmedBallot(ItemStack stack) {
        return isBallot(stack) && !readString(stack, new NamespacedKey(this, "confirmed_candidate_uuid")).isBlank();
    }

    private String readString(ItemStack stack, NamespacedKey key) {
        if (stack == null || !stack.hasItemMeta()) {
            return "";
        }
        return readString(stack.getItemMeta().getPersistentDataContainer(), key);
    }

    private String readString(PersistentDataContainer container, NamespacedKey key) {
        if (container == null) {
            return "";
        }
        String value = container.get(key, PersistentDataType.STRING);
        return value == null ? "" : value;
    }

    private boolean isManagedTextDisplay(TextDisplay display, String expectedKind, String linkedId, Location base) {
        PersistentDataContainer container = display.getPersistentDataContainer();
        String kind = readString(container, textTypeKey);
        if (!expectedKind.equalsIgnoreCase(kind)) {
            return false;
        }
        String entityLinkedId = readString(container, textLinkedIdKey);
        if (!entityLinkedId.isBlank()) {
            return linkedId.equals(entityLinkedId);
        }
        return display.getLocation().distanceSquared(base) <= 1.25;
    }

    private long now() {
        return Instant.now().toEpochMilli();
    }

    private void startPrompt(Player player, PromptContext prompt, String message) {
        prompts.put(player.getUniqueId(), prompt);
        player.sendMessage(color(message));
    }

    private String readBook(BookMeta meta) {
        StringBuilder builder = new StringBuilder();
        for (String page : meta.getPages()) {
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append(ChatColor.stripColor(page));
        }
        return builder.toString().trim();
    }

    private void consumeSingleItem(Player player, ItemStack stack) {
        if (stack == null) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack main = inventory.getItemInMainHand();
        if (main == stack || main.isSimilar(stack)) {
            if (main.getAmount() <= 1) {
                inventory.setItemInMainHand(null);
            } else {
                main.setAmount(main.getAmount() - 1);
            }
            return;
        }
        ItemStack off = inventory.getItemInOffHand();
        if (off == stack || off.isSimilar(stack)) {
            if (off.getAmount() <= 1) {
                inventory.setItemInOffHand(null);
            } else {
                off.setAmount(off.getAmount() - 1);
            }
        }
    }

    private String maskPin(String pin) {
        if (pin == null || pin.isBlank()) {
            return "";
        }
        return "*".repeat(pin.length());
    }

    private void normalizePin(String pin) {
        if (pin == null || !pin.matches("\\d{4,8}")) {
            throw new IllegalStateException("PIN должен содержать 4-8 цифр.");
        }
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

    private ItemStack createOfficialAr(int amount) {
        ItemStack stack = new ItemStack(Material.DIAMOND_ORE, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setDisplayName(color("&bОфициальный AR"));
        meta.setLore(List.of(color("&7Официальная руда CopiMine")));
        meta.addItemFlags(ItemFlag.values());
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(new NamespacedKey("copiminear", "type"), PersistentDataType.STRING, "certified");
        pdc.remove(new NamespacedKey("copiminear", "owner_uuid"));
        pdc.remove(new NamespacedKey("copiminear", "owner_name"));
        pdc.remove(new NamespacedKey("copiminear", "source"));
        stack.setItemMeta(meta);
        return stack;
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

    private void ensureSchema() throws Exception {
        tx(connection -> {
            update(connection, "CREATE TABLE IF NOT EXISTS cmv4_schema_migrations(version TEXT PRIMARY KEY,applied_at BIGINT NOT NULL,component TEXT NOT NULL DEFAULT 'plugin')");
            update(connection, "CREATE TABLE IF NOT EXISTS elections(id TEXT PRIMARY KEY,status TEXT NOT NULL DEFAULT 'DRAFT',started_at BIGINT NOT NULL DEFAULT 0,ended_at BIGINT NOT NULL DEFAULT 0,scheduled_end_at BIGINT NOT NULL DEFAULT 0,started_by TEXT NOT NULL DEFAULT '',ended_by TEXT NOT NULL DEFAULT '',winner_uuid TEXT NOT NULL DEFAULT '',winner_name TEXT NOT NULL DEFAULT '',notes TEXT NOT NULL DEFAULT '',created_at BIGINT NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL DEFAULT 0)");
            update(connection, "ALTER TABLE elections ADD COLUMN IF NOT EXISTS created_at BIGINT NOT NULL DEFAULT 0");
            update(connection, "ALTER TABLE elections ADD COLUMN IF NOT EXISTS updated_at BIGINT NOT NULL DEFAULT 0");
            update(connection, "ALTER TABLE elections ADD COLUMN IF NOT EXISTS current_stage TEXT NOT NULL DEFAULT 'NONE'");
            update(connection, "ALTER TABLE elections ADD COLUMN IF NOT EXISTS current_round INTEGER NOT NULL DEFAULT 1");
            update(connection, "ALTER TABLE elections ADD COLUMN IF NOT EXISTS candidate_limit INTEGER NOT NULL DEFAULT 4");
            update(connection, "ALTER TABLE elections ADD COLUMN IF NOT EXISTS president_term_days INTEGER NOT NULL DEFAULT 7");
            update(connection, "ALTER TABLE elections ADD COLUMN IF NOT EXISTS manual_winner_uuid TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE elections ADD COLUMN IF NOT EXISTS manual_winner_name TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE elections ADD COLUMN IF NOT EXISTS president_uuid TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE elections ADD COLUMN IF NOT EXISTS president_name TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE elections ADD COLUMN IF NOT EXISTS active INTEGER NOT NULL DEFAULT 0");
            update(connection, "ALTER TABLE elections ADD COLUMN IF NOT EXISTS second_round_needed INTEGER NOT NULL DEFAULT 0");
            update(connection, "CREATE TABLE IF NOT EXISTS election_stages(id BIGSERIAL PRIMARY KEY,election_id TEXT NOT NULL,stage TEXT NOT NULL,round_no INTEGER NOT NULL DEFAULT 1,actor TEXT NOT NULL DEFAULT '',created_at BIGINT NOT NULL DEFAULT 0,notes TEXT NOT NULL DEFAULT '')");
            update(connection, "CREATE TABLE IF NOT EXISTS polling_stations(id TEXT PRIMARY KEY,election_id TEXT NOT NULL DEFAULT '',world TEXT NOT NULL DEFAULT '',x INTEGER NOT NULL DEFAULT 0,y INTEGER NOT NULL DEFAULT 0,z INTEGER NOT NULL DEFAULT 0,chair_uuid TEXT NOT NULL DEFAULT '',chair_name TEXT NOT NULL DEFAULT '',active INTEGER NOT NULL DEFAULT 1,created_at BIGINT NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL DEFAULT 0,text_display_uuid TEXT NOT NULL DEFAULT '',applications_issued INTEGER NOT NULL DEFAULT 0,ballots_issued INTEGER NOT NULL DEFAULT 0,ballots_submitted INTEGER NOT NULL DEFAULT 0,ballots_annulled INTEGER NOT NULL DEFAULT 0)");
            update(connection, "CREATE TABLE IF NOT EXISTS cik_chairs(id BIGSERIAL PRIMARY KEY,station_id TEXT NOT NULL,player_uuid TEXT NOT NULL,player_name TEXT NOT NULL DEFAULT '',assigned_at BIGINT NOT NULL DEFAULT 0,assigned_by TEXT NOT NULL DEFAULT '',active INTEGER NOT NULL DEFAULT 1)");
            update(connection, "CREATE TABLE IF NOT EXISTS cik_seals(id TEXT PRIMARY KEY,station_id TEXT NOT NULL,player_uuid TEXT NOT NULL,player_name TEXT NOT NULL DEFAULT '',election_id TEXT NOT NULL DEFAULT '',status TEXT NOT NULL DEFAULT 'ACTIVE',issued_at BIGINT NOT NULL DEFAULT 0,issued_by TEXT NOT NULL DEFAULT '',revoked_at BIGINT NOT NULL DEFAULT 0,revoked_by TEXT NOT NULL DEFAULT '')");
            update(connection, "CREATE TABLE IF NOT EXISTS candidate_applications(id TEXT PRIMARY KEY,election_id TEXT NOT NULL,player_uuid TEXT NOT NULL,player_name TEXT NOT NULL DEFAULT '',station_id TEXT NOT NULL DEFAULT '',answers TEXT NOT NULL DEFAULT '',status TEXT NOT NULL DEFAULT 'ISSUED',chair_recommendation TEXT NOT NULL DEFAULT '',chair_note TEXT NOT NULL DEFAULT '',admin_status TEXT NOT NULL DEFAULT 'PENDING',admin_note TEXT NOT NULL DEFAULT '',book_signed_at BIGINT NOT NULL DEFAULT 0,submitted_at BIGINT NOT NULL DEFAULT 0,reviewed_at BIGINT NOT NULL DEFAULT 0,reviewed_by TEXT NOT NULL DEFAULT '',issued_at BIGINT NOT NULL DEFAULT 0,issued_by TEXT NOT NULL DEFAULT '',book_token TEXT NOT NULL DEFAULT '')");
            update(connection, "CREATE TABLE IF NOT EXISTS candidates(id TEXT PRIMARY KEY,election_id TEXT NOT NULL,player_uuid TEXT NOT NULL,player_name TEXT NOT NULL DEFAULT '',application_id TEXT NOT NULL DEFAULT '',created_at BIGINT NOT NULL DEFAULT 0,active INTEGER NOT NULL DEFAULT 1,last_result INTEGER NOT NULL DEFAULT 0)");
            update(connection, "CREATE TABLE IF NOT EXISTS round_candidates(election_id TEXT NOT NULL,round_no INTEGER NOT NULL DEFAULT 1,candidate_uuid TEXT NOT NULL,candidate_name TEXT NOT NULL DEFAULT '',active INTEGER NOT NULL DEFAULT 1,created_at BIGINT NOT NULL DEFAULT 0,created_by TEXT NOT NULL DEFAULT '',PRIMARY KEY(election_id,round_no,candidate_uuid))");
            update(connection, "CREATE TABLE IF NOT EXISTS ballots(id TEXT PRIMARY KEY,election_id TEXT NOT NULL,round_no INTEGER NOT NULL DEFAULT 1,player_uuid TEXT NOT NULL,player_name TEXT NOT NULL DEFAULT '',station_id TEXT NOT NULL DEFAULT '',status TEXT NOT NULL DEFAULT 'ISSUED',issued_at BIGINT NOT NULL DEFAULT 0,issued_by TEXT NOT NULL DEFAULT '',confirmed_candidate_uuid TEXT NOT NULL DEFAULT '',confirmed_candidate_name TEXT NOT NULL DEFAULT '',confirmed_at BIGINT NOT NULL DEFAULT 0,submitted_at BIGINT NOT NULL DEFAULT 0,submitted_station_id TEXT NOT NULL DEFAULT '',annulled_at BIGINT NOT NULL DEFAULT 0,annulled_by TEXT NOT NULL DEFAULT '',annul_reason TEXT NOT NULL DEFAULT '')");
            update(connection, "CREATE TABLE IF NOT EXISTS votes(id TEXT PRIMARY KEY,election_id TEXT NOT NULL,round_no INTEGER NOT NULL DEFAULT 1,ballot_id TEXT NOT NULL,voter_uuid TEXT NOT NULL,voter_name TEXT NOT NULL DEFAULT '',candidate_uuid TEXT NOT NULL,candidate_name TEXT NOT NULL DEFAULT '',station_id TEXT NOT NULL DEFAULT '',created_at BIGINT NOT NULL DEFAULT 0)");
            update(connection, "CREATE TABLE IF NOT EXISTS rounds(id TEXT PRIMARY KEY,election_id TEXT NOT NULL,round_no INTEGER NOT NULL DEFAULT 1,status TEXT NOT NULL DEFAULT 'ACTIVE',started_at BIGINT NOT NULL DEFAULT 0,ended_at BIGINT NOT NULL DEFAULT 0,winner_uuid TEXT NOT NULL DEFAULT '',winner_name TEXT NOT NULL DEFAULT '')");
            update(connection, "CREATE TABLE IF NOT EXISTS president_terms(id TEXT PRIMARY KEY,election_id TEXT NOT NULL,president_uuid TEXT NOT NULL,president_name TEXT NOT NULL DEFAULT '',status TEXT NOT NULL DEFAULT 'ACTIVE',started_at BIGINT NOT NULL DEFAULT 0,ends_at BIGINT NOT NULL DEFAULT 0,removed_at BIGINT NOT NULL DEFAULT 0,removed_by TEXT NOT NULL DEFAULT '',last_broadcast_at BIGINT NOT NULL DEFAULT 0,last_law_replace_at BIGINT NOT NULL DEFAULT 0)");
            update(connection, "CREATE TABLE IF NOT EXISTS president_laws(id TEXT PRIMARY KEY,term_id TEXT NOT NULL,president_uuid TEXT NOT NULL,text TEXT NOT NULL DEFAULT '',status TEXT NOT NULL DEFAULT 'PENDING',created_at BIGINT NOT NULL DEFAULT 0,published_at BIGINT NOT NULL DEFAULT 0,replaced_law_id TEXT NOT NULL DEFAULT '',slot_no INTEGER NOT NULL DEFAULT 0)");
            update(connection, "CREATE TABLE IF NOT EXISTS president_law_reviews(id BIGSERIAL PRIMARY KEY,law_id TEXT NOT NULL,reviewer TEXT NOT NULL DEFAULT '',decision TEXT NOT NULL DEFAULT '',note TEXT NOT NULL DEFAULT '',created_at BIGINT NOT NULL DEFAULT 0)");
            update(connection, "CREATE TABLE IF NOT EXISTS president_broadcasts(id TEXT PRIMARY KEY,term_id TEXT NOT NULL,president_uuid TEXT NOT NULL,format TEXT NOT NULL DEFAULT 'CHAT',text TEXT NOT NULL DEFAULT '',created_at BIGINT NOT NULL DEFAULT 0)");
            update(connection, "CREATE TABLE IF NOT EXISTS president_taxes(id TEXT PRIMARY KEY,term_id TEXT NOT NULL,amount INTEGER NOT NULL DEFAULT 0,status TEXT NOT NULL DEFAULT 'ACTIVE',created_at BIGINT NOT NULL DEFAULT 0,created_by TEXT NOT NULL DEFAULT '')");
            update(connection, "CREATE TABLE IF NOT EXISTS president_tax_payments(id TEXT PRIMARY KEY,tax_id TEXT NOT NULL,player_uuid TEXT NOT NULL,player_name TEXT NOT NULL DEFAULT '',amount BIGINT NOT NULL DEFAULT 0,source TEXT NOT NULL DEFAULT '',created_at BIGINT NOT NULL DEFAULT 0,details TEXT NOT NULL DEFAULT '')");
            update(connection, "CREATE TABLE IF NOT EXISTS president_tax_payment_ops(id TEXT PRIMARY KEY,tax_id TEXT NOT NULL,player_uuid TEXT NOT NULL,player_name TEXT NOT NULL DEFAULT '',amount BIGINT NOT NULL DEFAULT 0,source TEXT NOT NULL DEFAULT '',status TEXT NOT NULL DEFAULT 'PENDING',bank_tx_id TEXT NOT NULL DEFAULT '',idempotency_key TEXT NOT NULL DEFAULT '',details TEXT NOT NULL DEFAULT '',last_error TEXT NOT NULL DEFAULT '',created_at BIGINT NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL DEFAULT 0)");
            update(connection, "CREATE TABLE IF NOT EXISTS protected_blocks(id TEXT PRIMARY KEY,kind TEXT NOT NULL,world TEXT NOT NULL,x INTEGER NOT NULL,y INTEGER NOT NULL,z INTEGER NOT NULL,linked_id TEXT NOT NULL DEFAULT '',active INTEGER NOT NULL DEFAULT 1,created_at BIGINT NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL DEFAULT 0)");
            update(connection, "CREATE TABLE IF NOT EXISTS protected_block_visuals(id TEXT PRIMARY KEY,kind TEXT NOT NULL,linked_id TEXT NOT NULL,world TEXT NOT NULL,x INTEGER NOT NULL,y INTEGER NOT NULL,z INTEGER NOT NULL,entity_uuid TEXT NOT NULL DEFAULT '',base_material TEXT NOT NULL DEFAULT 'PAPER',custom_model_data INTEGER NOT NULL DEFAULT 0,model_id TEXT NOT NULL DEFAULT '',offset_x DOUBLE PRECISION NOT NULL DEFAULT 0.5,offset_y DOUBLE PRECISION NOT NULL DEFAULT 0.5,offset_z DOUBLE PRECISION NOT NULL DEFAULT 0.5,scale_x DOUBLE PRECISION NOT NULL DEFAULT 1.01,scale_y DOUBLE PRECISION NOT NULL DEFAULT 1.01,scale_z DOUBLE PRECISION NOT NULL DEFAULT 1.01,yaw DOUBLE PRECISION NOT NULL DEFAULT 0,pitch DOUBLE PRECISION NOT NULL DEFAULT 0,created_at BIGINT NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL DEFAULT 0,active INTEGER NOT NULL DEFAULT 1)");
            update(connection, "CREATE TABLE IF NOT EXISTS text_display_links(id TEXT PRIMARY KEY,kind TEXT NOT NULL,linked_id TEXT NOT NULL,world TEXT NOT NULL DEFAULT '',entity_uuid TEXT NOT NULL DEFAULT '',text TEXT NOT NULL DEFAULT '',created_at BIGINT NOT NULL DEFAULT 0,active INTEGER NOT NULL DEFAULT 1)");
            update(connection, "ALTER TABLE cik_chairs ADD COLUMN IF NOT EXISTS player_uuid TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE cik_chairs ADD COLUMN IF NOT EXISTS player_name TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE cik_chairs ADD COLUMN IF NOT EXISTS assigned_at BIGINT NOT NULL DEFAULT 0");
            update(connection, "ALTER TABLE cik_chairs ADD COLUMN IF NOT EXISTS assigned_by TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE cik_chairs ADD COLUMN IF NOT EXISTS active INTEGER NOT NULL DEFAULT 1");
            update(connection, "ALTER TABLE cik_seals ADD COLUMN IF NOT EXISTS player_uuid TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE cik_seals ADD COLUMN IF NOT EXISTS player_name TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE cik_seals ADD COLUMN IF NOT EXISTS election_id TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE cik_seals ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'ACTIVE'");
            update(connection, "ALTER TABLE cik_seals ADD COLUMN IF NOT EXISTS issued_at BIGINT NOT NULL DEFAULT 0");
            update(connection, "ALTER TABLE cik_seals ADD COLUMN IF NOT EXISTS issued_by TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE cik_seals ADD COLUMN IF NOT EXISTS revoked_at BIGINT NOT NULL DEFAULT 0");
            update(connection, "ALTER TABLE cik_seals ADD COLUMN IF NOT EXISTS revoked_by TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE candidate_applications ADD COLUMN IF NOT EXISTS player_uuid TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE candidate_applications ADD COLUMN IF NOT EXISTS player_name TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE candidate_applications ADD COLUMN IF NOT EXISTS station_id TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE candidate_applications ADD COLUMN IF NOT EXISTS answers TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE candidate_applications ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'ISSUED'");
            update(connection, "ALTER TABLE candidate_applications ADD COLUMN IF NOT EXISTS chair_recommendation TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE candidate_applications ADD COLUMN IF NOT EXISTS chair_note TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE candidate_applications ADD COLUMN IF NOT EXISTS admin_status TEXT NOT NULL DEFAULT 'PENDING'");
            update(connection, "ALTER TABLE candidate_applications ADD COLUMN IF NOT EXISTS admin_note TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE candidate_applications ADD COLUMN IF NOT EXISTS book_signed_at BIGINT NOT NULL DEFAULT 0");
            update(connection, "ALTER TABLE candidate_applications ADD COLUMN IF NOT EXISTS submitted_at BIGINT NOT NULL DEFAULT 0");
            update(connection, "ALTER TABLE candidate_applications ADD COLUMN IF NOT EXISTS reviewed_at BIGINT NOT NULL DEFAULT 0");
            update(connection, "ALTER TABLE candidate_applications ADD COLUMN IF NOT EXISTS reviewed_by TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE candidate_applications ADD COLUMN IF NOT EXISTS issued_at BIGINT NOT NULL DEFAULT 0");
            update(connection, "ALTER TABLE candidate_applications ADD COLUMN IF NOT EXISTS issued_by TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE candidate_applications ADD COLUMN IF NOT EXISTS book_token TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE candidates ADD COLUMN IF NOT EXISTS player_uuid TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE candidates ADD COLUMN IF NOT EXISTS player_name TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE candidates ADD COLUMN IF NOT EXISTS application_id TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE candidates ADD COLUMN IF NOT EXISTS created_at BIGINT NOT NULL DEFAULT 0");
            update(connection, "ALTER TABLE candidates ADD COLUMN IF NOT EXISTS active INTEGER NOT NULL DEFAULT 1");
            update(connection, "ALTER TABLE candidates ADD COLUMN IF NOT EXISTS last_result INTEGER NOT NULL DEFAULT 0");
            update(connection, "ALTER TABLE ballots ADD COLUMN IF NOT EXISTS player_uuid TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE ballots ADD COLUMN IF NOT EXISTS player_name TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE ballots ADD COLUMN IF NOT EXISTS station_id TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE ballots ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'ISSUED'");
            update(connection, "ALTER TABLE ballots ADD COLUMN IF NOT EXISTS issued_at BIGINT NOT NULL DEFAULT 0");
            update(connection, "ALTER TABLE ballots ADD COLUMN IF NOT EXISTS issued_by TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE ballots ADD COLUMN IF NOT EXISTS confirmed_candidate_uuid TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE ballots ADD COLUMN IF NOT EXISTS confirmed_candidate_name TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE ballots ADD COLUMN IF NOT EXISTS confirmed_at BIGINT NOT NULL DEFAULT 0");
            update(connection, "ALTER TABLE ballots ADD COLUMN IF NOT EXISTS submitted_at BIGINT NOT NULL DEFAULT 0");
            update(connection, "ALTER TABLE ballots ADD COLUMN IF NOT EXISTS submitted_station_id TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE ballots ADD COLUMN IF NOT EXISTS annulled_at BIGINT NOT NULL DEFAULT 0");
            update(connection, "ALTER TABLE ballots ADD COLUMN IF NOT EXISTS annulled_by TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE ballots ADD COLUMN IF NOT EXISTS annul_reason TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE president_tax_payments ADD COLUMN IF NOT EXISTS player_uuid TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE president_tax_payments ADD COLUMN IF NOT EXISTS player_name TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE president_tax_payments ADD COLUMN IF NOT EXISTS amount BIGINT NOT NULL DEFAULT 0");
            update(connection, "ALTER TABLE president_tax_payments ADD COLUMN IF NOT EXISTS source TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE president_tax_payments ADD COLUMN IF NOT EXISTS created_at BIGINT NOT NULL DEFAULT 0");
            update(connection, "ALTER TABLE president_tax_payments ADD COLUMN IF NOT EXISTS details TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE president_tax_payment_ops ADD COLUMN IF NOT EXISTS player_uuid TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE president_tax_payment_ops ADD COLUMN IF NOT EXISTS player_name TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE president_tax_payment_ops ADD COLUMN IF NOT EXISTS amount BIGINT NOT NULL DEFAULT 0");
            update(connection, "ALTER TABLE president_tax_payment_ops ADD COLUMN IF NOT EXISTS source TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE president_tax_payment_ops ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'PENDING'");
            update(connection, "ALTER TABLE president_tax_payment_ops ADD COLUMN IF NOT EXISTS bank_tx_id TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE president_tax_payment_ops ADD COLUMN IF NOT EXISTS idempotency_key TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE president_tax_payment_ops ADD COLUMN IF NOT EXISTS details TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE president_tax_payment_ops ADD COLUMN IF NOT EXISTS last_error TEXT NOT NULL DEFAULT ''");
            update(connection, "ALTER TABLE president_tax_payment_ops ADD COLUMN IF NOT EXISTS created_at BIGINT NOT NULL DEFAULT 0");
            update(connection, "ALTER TABLE president_tax_payment_ops ADD COLUMN IF NOT EXISTS updated_at BIGINT NOT NULL DEFAULT 0");
            update(connection, "CREATE INDEX IF NOT EXISTS idx_polling_stations_active ON polling_stations(active,created_at DESC)");
            update(connection, "CREATE INDEX IF NOT EXISTS idx_candidate_applications_status ON candidate_applications(election_id,admin_status,submitted_at DESC)");
            update(connection, "CREATE INDEX IF NOT EXISTS idx_ballots_player ON ballots(election_id,round_no,player_uuid,status)");
            update(connection, "CREATE INDEX IF NOT EXISTS idx_votes_round_candidate ON votes(election_id,round_no,candidate_uuid)");
            update(connection, "CREATE INDEX IF NOT EXISTS idx_president_laws_status ON president_laws(status,published_at DESC)");
            update(connection, "CREATE INDEX IF NOT EXISTS idx_tax_payments_tax_player ON president_tax_payments(tax_id,player_uuid,created_at DESC)");
            update(connection, "CREATE INDEX IF NOT EXISTS idx_tax_payment_ops_status ON president_tax_payment_ops(status,updated_at DESC)");
            update(connection, "CREATE INDEX IF NOT EXISTS idx_protected_blocks_coords ON protected_blocks(world,x,y,z,active)");
            update(connection, "CREATE INDEX IF NOT EXISTS idx_protected_block_visuals_linked ON protected_block_visuals(linked_id,active)");
            update(connection, "CREATE INDEX IF NOT EXISTS idx_protected_block_visuals_location ON protected_block_visuals(world,x,y,z,active)");
            update(connection, "CREATE UNIQUE INDEX IF NOT EXISTS uq_candidate_applications_election_player ON candidate_applications(election_id,player_uuid)");
            update(connection, "CREATE UNIQUE INDEX IF NOT EXISTS uq_candidates_election_player ON candidates(election_id,player_uuid)");
            update(connection, "CREATE UNIQUE INDEX IF NOT EXISTS uq_votes_ballot_id ON votes(ballot_id)");
            update(connection, "CREATE UNIQUE INDEX IF NOT EXISTS uq_votes_voter_round ON votes(election_id,round_no,voter_uuid)");
            update(connection, "DROP INDEX IF EXISTS uq_ballots_active_player_round");
            update(connection, "CREATE UNIQUE INDEX IF NOT EXISTS uq_ballots_active_player_round ON ballots(election_id,round_no,player_uuid) WHERE status IN ('ISSUED','CONFIRMED','DEPOSITED')");
            update(connection, "CREATE UNIQUE INDEX IF NOT EXISTS uq_round_candidates_round_player ON round_candidates(election_id,round_no,candidate_uuid)");
            update(connection, "INSERT INTO cmv4_schema_migrations(version,applied_at,component) VALUES('20260621_007_copimine_election_core_rebuild',?,'election-core') ON CONFLICT(version) DO NOTHING", now());
            update(connection, "INSERT INTO cmv4_schema_migrations(version,applied_at,component) VALUES('20260623_008_copimine_election_core_phase1_stability',?,'election-core') ON CONFLICT(version) DO NOTHING", now());
            return null;
        });
    }

    private void reloadProtectedBlocks() throws Exception {
        protectedBlocks.clear();
        for (Map<String, Object> row : queryList("SELECT world,x,y,z FROM protected_blocks WHERE active=1")) {
            protectedBlocks.add(new BlockKey(string(row.get("world")), intValue(row.get("x")), intValue(row.get("y")), intValue(row.get("z"))));
        }
    }

    private void upsertProtectedBlock(Connection connection, String kind, Location location, String linkedId, long t) throws Exception {
        String id = kind.toLowerCase(Locale.ROOT) + ":" + location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
        update(connection, "INSERT INTO protected_blocks(id,kind,world,x,y,z,linked_id,active,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET linked_id=excluded.linked_id,active=1,updated_at=excluded.updated_at",
                id, kind, location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ(), linkedId, 1, t, t);
    }

    private Map<String, Object> ensureBankAccount(Connection connection, String playerUuid, String playerName) throws Exception {
        String accountId = "ar:" + playerUuid;
        long t = now();
        update(connection, "INSERT INTO cmv4_bank_accounts(account_id,owner_uuid,owner_name,account_type,currency,balance,status,version,created_at,updated_at) VALUES(?,?,?,'PLAYER','AR',0,'ACTIVE',0,?,?) ON CONFLICT(account_id) DO UPDATE SET owner_name=excluded.owner_name,updated_at=excluded.updated_at",
                accountId, playerUuid, playerName, t, t);
        return queryOne(connection, "SELECT * FROM cmv4_bank_accounts WHERE account_id=?", accountId);
    }

    private void ensureTreasuryAccount(Connection connection, String accountId, String name) throws Exception {
        long t = now();
        update(connection, "INSERT INTO cmv4_bank_accounts(account_id,owner_uuid,owner_name,account_type,currency,balance,status,version,created_at,updated_at) VALUES(?,?,?,'SYSTEM','AR',0,'ACTIVE',0,?,?) ON CONFLICT(account_id) DO UPDATE SET owner_name=excluded.owner_name,updated_at=excluded.updated_at",
                accountId, accountId, name, t, t);
    }

    private void verifyBankPin(Connection connection, String minecraftUuid, String pin) throws Exception {
        Map<String, Object> row = queryOne(connection, "SELECT pin_hash,must_change FROM bank_pin_hashes WHERE minecraft_uuid=?", minecraftUuid);
        if (row == null || !verifyPasswordHash(string(row.get("pin_hash")), pin)) {
            throw new IllegalStateException("Неверный PIN.");
        }
        if (intValue(row.get("must_change")) > 0) {
            throw new IllegalStateException("Сначала нужно изменить временный PIN.");
        }
    }

    private boolean verifyPasswordHash(String stored, String raw) {
        if (stored == null || stored.isBlank()) {
            return false;
        }
        if (stored.startsWith("sha256:")) {
            return java.security.MessageDigest.isEqual(
                    stored.substring("sha256:".length()).getBytes(StandardCharsets.UTF_8),
                    sha256Hex(raw).getBytes(StandardCharsets.UTF_8)
            );
        }
        if (stored.startsWith("pbkdf2_sha256$")) {
            try {
                String[] parts = stored.split("\\$", 4);
                int iterations = Integer.parseInt(parts[1]);
                String salt = parts[2];
                String digest = parts[3];
                byte[] check = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                        .generateSecret(new javax.crypto.spec.PBEKeySpec(raw.toCharArray(), salt.getBytes(StandardCharsets.UTF_8), iterations, 256))
                        .getEncoded();
                return java.security.MessageDigest.isEqual(bytesToHex(check).getBytes(StandardCharsets.UTF_8), digest.getBytes(StandardCharsets.UTF_8));
            } catch (Exception error) {
                return false;
            }
        }
        return false;
    }

    private String sha256Hex(String raw) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return bytesToHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            return "";
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private List<PlayerChoice> knownPlayers() {
        Map<String, PlayerChoice> byUuid = new LinkedHashMap<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            byUuid.put(online.getUniqueId().toString(), new PlayerChoice(online.getUniqueId().toString(), online.getName(), true));
        }
        try {
            for (Map<String, Object> row : queryList("SELECT minecraft_uuid,minecraft_name FROM site_accounts WHERE enabled=1 AND coalesce(minecraft_uuid,'')<>'' ORDER BY minecraft_name ASC LIMIT 100")) {
                String uuid = string(row.get("minecraft_uuid"));
                if (uuid.isBlank()) {
                    continue;
                }
                byUuid.putIfAbsent(uuid, new PlayerChoice(uuid, first(string(row.get("minecraft_name")), uuid), false));
            }
        } catch (Exception ignored) {
        }
        List<PlayerChoice> list = new ArrayList<>(byUuid.values());
        list.sort(Comparator.comparing(PlayerChoice::online).reversed().thenComparing(PlayerChoice::name, String.CASE_INSENSITIVE_ORDER));
        return list;
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
                String value = trimmed.substring(idx + 1).trim();
                values.putIfAbsent(key, value.replace("\"", ""));
            }
        }
        String host = first(values.get("POSTGRES_HOST"), values.get("PGHOST"), "127.0.0.1");
        int port = parseInt(first(values.get("POSTGRES_PORT"), values.get("PGPORT"), "5432"), 5432);
        String database = first(values.get("POSTGRES_DB"), values.get("PGDATABASE"), "copimine");
        String user = first(values.get("POSTGRES_USER"), values.get("PGUSER"), "copimine");
        String password = first(values.get("POSTGRES_PASSWORD"), values.get("PGPASSWORD"), "");
        String schema = first(values.get("POSTGRES_SCHEMA"), values.get("PGSCHEMA"), "copimine");
        if (password.isBlank()) {
            throw new IllegalStateException("POSTGRES_PASSWORD is required for CopiMineElectionCore.");
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
        } catch (Throwable ignored) {
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
        Class.forName("org.postgresql.Driver");
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
                if (!resultSet.next()) {
                    return 0L;
                }
                return resultSet.getLong(1);
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

    private void logPluginEvent(Connection connection, String source, String type, String actor, String target, String details) throws Exception {
        update(connection, "INSERT INTO plugin_events(source,event_type,actor,target,created_at,details) VALUES(?,?,?,?,?,?)",
                source, type, actor, target, now(), details);
    }

    private void saveHiddenPlayers() {
        List<String> values = liveHidden.stream().map(UUID::toString).sorted().toList();
        getConfig().set("liveHidden", values);
        saveConfig();
    }

    private void loadHiddenPlayers() {
        liveHidden.clear();
        for (String value : getConfig().getStringList("liveHidden")) {
            try {
                liveHidden.add(UUID.fromString(value));
            } catch (Exception ignored) {
            }
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String text) {
            return "\"" + text
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t") + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Map<?, ?> map) {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                parts.add(toJson(String.valueOf(entry.getKey())) + ":" + toJson(entry.getValue()));
            }
            return "{" + String.join(",", parts) + "}";
        }
        if (value instanceof Collection<?> collection) {
            List<String> parts = new ArrayList<>();
            for (Object item : collection) {
                parts.add(toJson(item));
            }
            return "[" + String.join(",", parts) + "]";
        }
        return toJson(String.valueOf(value));
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

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int intValue(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return parseInt(String.valueOf(value), 0);
    }

    private long longValue(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception error) {
            return 0L;
        }
    }

    private int parsePage(String action) {
        String[] parts = action.split(":");
        return parts.length == 0 ? 0 : parseInt(parts[parts.length - 1], 0);
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception error) {
            return fallback;
        }
    }

    private String shortId(String id) {
        if (id == null || id.isBlank()) {
            return "—";
        }
        return id.length() <= 10 ? id : id.substring(0, 10);
    }

    private String shortText(String text, int max) {
        String clean = ChatColor.stripColor(first(text, ""));
        if (clean.length() <= max) {
            return clean;
        }
        return clean.substring(0, Math.max(0, max - 1)) + "…";
    }

    private String formatTs(long ts) {
        return ts <= 0 ? "—" : Instant.ofEpochMilli(ts).toString();
    }

    private record DbSettings(String host, int port, String database, String user, String password, String schema, Path envFile) {
        String jdbcUrl() {
            return "jdbc:postgresql://" + host + ":" + port + "/" + database;
        }

        String schemaIdent() {
            return "\"" + schema.replace("\"", "\"\"") + "\"";
        }
    }

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
            this.inventory = Bukkit.createInventory(this, size, title);
            return this.inventory;
        }

        String id() {
            return id;
        }

        String contextId() {
            return contextId;
        }

        Map<Integer, String> actions() {
            return actions;
        }

        Map<Integer, String> rightActions() {
            return rightActions;
        }

        Map<String, String> data() {
            return data;
        }

        Inventory inventory() {
            return inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private record PlayerChoice(String uuid, String name, boolean online) {
    }

    private record BlockKey(String world, int x, int y, int z) {
        static BlockKey from(Location location) {
            return new BlockKey(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
    }

    private record ProtectedBlockInfo(String kind, String linkedId) {
    }

    private record SealContext(String sealId, String electionId, String stationId, String playerUuid) {
    }

    private record TaxRecipient(String presidentUuid, String presidentName) {
    }

    private record TaxEconomyProof(boolean confirmed, String bankTxId) {
    }

    private enum ElectionStage {
        NONE("Нет выборов"),
        PREPARATION("Подготовка"),
        APPLICATIONS("Приём заявок"),
        REVIEW("Проверка заявок"),
        DEBATES("Дебаты"),
        VOTING("Голосование"),
        COUNTING("Подсчёт"),
        SECOND_ROUND("Второй тур"),
        FINISHED("Завершено"),
        PRESIDENT_TERM("Президентский срок");

        private final String title;

        ElectionStage(String title) {
            this.title = title;
        }

        String title() {
            return title;
        }

        static ElectionStage safeValue(String value) {
            try {
                return value == null || value.isBlank() ? NONE : ElectionStage.valueOf(value.toUpperCase(Locale.ROOT));
            } catch (Exception error) {
                return NONE;
            }
        }
    }

    private record ElectionContext(
            String electionId,
            ElectionStage stage,
            int round,
            int candidateLimit,
            int presidentTermDays,
            String manualWinnerUuid,
            String manualWinnerName,
            String presidentUuid,
            String presidentName,
            boolean secondRoundNeeded
    ) {
    }

    private record StageTransitionResult(boolean allowed, String message) {
        static StageTransitionResult allow() {
            return new StageTransitionResult(true, "");
        }

        static StageTransitionResult deny(String message) {
            return new StageTransitionResult(false, message);
        }
    }

    private final class ElectionStateMachine {
        private StageTransitionResult validateStageTransition(Connection connection, String electionId, ElectionStage from, ElectionStage to) throws Exception {
            int round = currentRoundFromDb(connection, electionId);
            long pendingApplications = scalarLong(connection, "SELECT COUNT(*) FROM candidate_applications WHERE election_id=? AND admin_status='PENDING'", electionId);
            long activeCandidates = countActiveRoundCandidates(connection, electionId, round);
            long stations = countActiveStations(connection, electionId);
            long tiedLeaders = countTiedLeaders(connection, electionId, round);
            Map<String, Object> winnerRow = queryOne(connection, "SELECT manual_winner_uuid,president_uuid FROM elections WHERE id=? LIMIT 1", electionId);
            boolean hasWinner = winnerRow != null && (!string(winnerRow.get("manual_winner_uuid")).isBlank() || !string(winnerRow.get("president_uuid")).isBlank());

            if (from == to) {
                return StageTransitionResult.allow();
            }

            return switch (from) {
                case NONE -> to == ElectionStage.PREPARATION
                        ? StageTransitionResult.allow()
                        : StageTransitionResult.deny("Выборы сначала нужно перевести в подготовку.");
                case PREPARATION -> to == ElectionStage.APPLICATIONS
                        ? StageTransitionResult.allow()
                        : StageTransitionResult.deny("После подготовки можно перейти только к приёму заявок.");
                case APPLICATIONS -> to == ElectionStage.REVIEW
                        ? StageTransitionResult.allow()
                        : StageTransitionResult.deny("После приёма заявок можно перейти только к проверке.");
                case REVIEW -> {
                    if (pendingApplications > 0) {
                        yield StageTransitionResult.deny("Сначала нужно разобрать все заявки кандидатов.");
                    }
                    if (to == ElectionStage.DEBATES) {
                        yield StageTransitionResult.allow();
                    }
                    yield StageTransitionResult.deny("После проверки заявок можно перейти только к дебатам.");
                }
                case DEBATES -> {
                    if (to != ElectionStage.VOTING) {
                        yield StageTransitionResult.deny("После дебатов можно перейти только к голосованию.");
                    }
                    if (pendingApplications > 0) {
                        yield StageTransitionResult.deny("Сначала нужно разобрать все заявки кандидатов.");
                    }
                    if (activeCandidates < 2) {
                        yield StageTransitionResult.deny("Для голосования нужно минимум 2 кандидата.");
                    }
                    if (stations < 1) {
                        yield StageTransitionResult.deny("Сначала нужно создать хотя бы один участок.");
                    }
                    yield StageTransitionResult.allow();
                }
                case VOTING -> to == ElectionStage.COUNTING
                        ? StageTransitionResult.allow()
                        : StageTransitionResult.deny("После голосования можно перейти только к подсчёту.");
                case COUNTING -> {
                    if (to == ElectionStage.SECOND_ROUND) {
                        yield tiedLeaders >= 2
                                ? StageTransitionResult.allow()
                                : StageTransitionResult.deny("Второй тур доступен только при равенстве лидеров.");
                    }
                    if (to == ElectionStage.FINISHED || to == ElectionStage.PRESIDENT_TERM) {
                        yield hasWinner
                                ? StageTransitionResult.allow()
                                : StageTransitionResult.deny("Сначала нужно определить победителя.");
                    }
                    yield StageTransitionResult.deny("После подсчёта можно перейти только ко второму туру или к президентскому сроку.");
                }
                case SECOND_ROUND -> {
                    if (tiedLeaders < 2) {
                        yield StageTransitionResult.deny("Второй тур доступен только для кандидатов с равным максимумом голосов.");
                    }
                    if (to == ElectionStage.DEBATES || to == ElectionStage.VOTING || to == ElectionStage.COUNTING) {
                        yield StageTransitionResult.allow();
                    }
                    yield StageTransitionResult.deny("Из второго тура можно перейти только к дебатам, голосованию или подсчёту.");
                }
                case FINISHED -> to == ElectionStage.PRESIDENT_TERM
                        ? (hasWinner ? StageTransitionResult.allow() : StageTransitionResult.deny("Сначала нужно определить победителя."))
                        : StageTransitionResult.deny("После завершения можно перейти только к президентскому сроку.");
                case PRESIDENT_TERM -> to == ElectionStage.FINISHED
                        ? StageTransitionResult.allow()
                        : StageTransitionResult.deny("Президентский срок можно только завершить.");
            };
        }
    }

    private record CandidateResult(String uuid, String name, int votes, String bar) {
    }

    private record LiveSnapshot(
            String electionId,
            ElectionStage stage,
            int round,
            String presidentName,
            List<CandidateResult> candidates,
            List<String> laws,
            int candidateLimit,
            int termDays,
            int taxAmount,
            long depositedBallots,
            long stationCount,
            boolean secondRoundNeeded
    ) {
        static LiveSnapshot empty() {
            return new LiveSnapshot("", ElectionStage.NONE, 1, "", List.of(), List.of(), 4, 7, 0, 0, 0, false);
        }

        String stageTitle() {
            return stage.title();
        }
    }

    private enum PromptKind {
        NEW_LAW,
        REPLACE_LAW,
        BROADCAST
    }

    private record PromptContext(PromptKind kind, String value1, String value2, boolean keepOnClose) {
    }

    @FunctionalInterface
    private interface CheckedSqlAction {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(Connection connection) throws Exception;
    }
}
