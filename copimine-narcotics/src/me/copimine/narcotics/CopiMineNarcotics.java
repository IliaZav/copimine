package me.copimine.narcotics;

import me.copimine.clientbridge.CopiMineClientBridge;
import me.copimine.narcotics.cauldron.CauldronBrewingService;
import me.copimine.narcotics.config.NarcoticsConfigService;
import me.copimine.narcotics.db.NarcoticsDatabase;
import me.copimine.narcotics.item.NarcoticItemFactory;
import me.copimine.narcotics.model.NarcoticDefinition;
import me.copimine.narcotics.recipe.NarcoticsRecipeService;
import me.copimine.narcotics.recipe.IngredientEntry;
import me.copimine.narcotics.resourcepack.NarcoticsResourcePackAudit;
import me.copimine.narcotics.use.OverdoseService;
import me.copimine.visualruntime.VisualRuntimeService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.entity.Item;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import io.papermc.paper.event.entity.EntityInsideBlockEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class CopiMineNarcotics extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    /*
     * Runtime note for premium/admin scenarios:
     * if a future narcotics flow ever needs an AR-backed gate, it must resolve
     * CopiMineUltimateAdminPlus.ArtifactsBridge through main.artifactsBridge()
     * and only then call bridge.charge(...) with an explicit idempotency key.
     */
    private static final Set<String> VALID_TEXTURE_MODES = Set.of("VANILLA", "CUSTOM");
    private static final Set<String> VALID_VISUAL_MODES = Set.of("AUTO", "CLIENT_MOD", "SERVER_FALLBACK");

    private final ConcurrentHashMap<UUID, Long> consumeCooldownUntil = new ConcurrentHashMap<>();
    private final Set<UUID> consumeInFlight = ConcurrentHashMap.newKeySet();
    /** Exact instance reserved while the DB state is being committed. */
    private final ConcurrentHashMap<UUID, String> consumeReservations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> consumeReservationNarcotics = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Integer> consumeReservationQuantities = new ConcurrentHashMap<>();
    /** Prevent duplicate recovery rows while several Paper removal events race. */
    private final Set<String> lossRecoveryInFlight = ConcurrentHashMap.newKeySet();
    private NamespacedKey pendingRefundKey;
    private volatile boolean resetInProgress = false;

    private NarcoticsConfigService configService;
    private NarcoticsDatabase database;
    private NarcoticItemFactory itemFactory;
    private NarcoticsRecipeService recipeService;
    private CopiMineClientBridge clientBridge;
    private VisualRuntimeService visualRuntime;
    private OverdoseService overdoseService;
    private CauldronBrewingService cauldronService;
    private NarcoticsResourcePackAudit resourcePackAudit;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        configService = new NarcoticsConfigService(this);
        configService.reload();
        database = new NarcoticsDatabase(this, configService);
        database.start();
        itemFactory = new NarcoticItemFactory(this, configService);
        pendingRefundKey = new NamespacedKey(this, "pending_refund_id");
        recipeService = new NarcoticsRecipeService(configService, itemFactory);
        clientBridge = new CopiMineClientBridge(this, configService);
        clientBridge.register();
        visualRuntime = new VisualRuntimeService(this, configService, clientBridge);
        overdoseService = new OverdoseService(this, configService, database, visualRuntime);
        cauldronService = new CauldronBrewingService(this, configService, database, recipeService, itemFactory);
        resourcePackAudit = new NarcoticsResourcePackAudit(this, configService);
        cauldronService.preloadCacheIfEnabled();

        for (String commandName : List.of("cmnarcotics", "cmclient")) {
            PluginCommand command = getCommand(commandName);
            if (command != null) {
                command.setExecutor(this);
                command.setTabCompleter(this);
            }
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        registerExternalItemRemovalListener();
        for (Player player : Bukkit.getOnlinePlayers()) {
            overdoseService.preloadState(player.getUniqueId());
            bindOwners(player);
        }
        scheduleIntegritySweep();
        getLogger().info("CopiMineNarcotics with optional CopiMineClient bridge enabled.");
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (overdoseService != null) {
                overdoseService.clearActiveEffects(player, true);
            }
            if (visualRuntime != null) {
                visualRuntime.clear(player);
            }
        }
        consumeCooldownUntil.clear();
        consumeInFlight.clear();
        consumeReservations.clear();
        consumeReservationNarcotics.clear();
        consumeReservationQuantities.clear();
        if (clientBridge != null) {
            clientBridge.shutdown();
        }
        if (cauldronService != null) {
            cauldronService.shutdown();
        }
        if (database != null) {
            database.shutdown();
        }
    }

    private void scheduleIntegritySweep() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!isEnabled()) {
                return;
            }
            if (cauldronService != null) {
                cauldronService.runIntegritySweep();
            }
            scheduleIntegritySweep();
        }, 100L);
    }

    /** Paper only exposes silent third-party item deletion through this event. */
    @SuppressWarnings({"deprecation", "removal"})
    private void registerExternalItemRemovalListener() {
        try {
            EventExecutor executor = (listener, event) -> {
                if (event instanceof EntityRemoveEvent removal) {
                    onNarcoticItemRemoved(removal);
                }
            };
            Bukkit.getPluginManager().registerEvent(
                    EntityRemoveEvent.class, this, EventPriority.MONITOR, executor, this, true);
        } catch (Throwable error) {
            getLogger().warning("Narcotic external removal listener unavailable: " + error.getMessage());
        }
    }

    /** Bind old stacks once the owning player is known; never overwrite a binding. */
    private void bindOwners(Player player) {
        if (player == null || itemFactory == null) {
            return;
        }
        boolean changed = false;
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            changed |= itemFactory.bindOwnerIfMissing(player.getInventory().getItem(slot), player.getUniqueId());
        }
        changed |= itemFactory.bindOwnerIfMissing(player.getInventory().getItemInOffHand(), player.getUniqueId());
        for (int slot = 0; slot < player.getEnderChest().getSize(); slot++) {
            changed |= itemFactory.bindOwnerIfMissing(player.getEnderChest().getItem(slot), player.getUniqueId());
        }
        changed |= itemFactory.bindOwnerIfMissing(player.getItemOnCursor(), player.getUniqueId());
        if (changed) {
            player.updateInventory();
        }
    }

    private me.copimine.narcotics.model.NarcoticDefinition officialNarcotic(ItemStack stack) {
        return stack == null || itemFactory == null ? null : itemFactory.resolveOfficial(stack);
    }

    /**
     * Durable-first recovery for a physical narcotic that disappeared outside
     * the normal consume path.  The refund journal is fsynced before the
     * callback removes the entity/cursor, so a database outage cannot turn a
     * lost stack into an unrecoverable loss.
     */
    private boolean queueNarcoticRecovery(ItemStack stack, UUID ownerHint, String reason, Runnable removePhysical) {
        NarcoticDefinition definition = officialNarcotic(stack);
        if (definition == null || database == null) {
            return false;
        }
        UUID owner = itemFactory.ownerUuid(stack);
        if (owner == null && ownerHint != null && itemFactory.bindOwnerIfMissing(stack, ownerHint)) {
            owner = ownerHint;
        }
        if (owner == null) {
            getLogger().warning("Narcotic loss has no owner metadata; preserving physical item for manual recovery.");
            return false;
        }
        String instanceId = itemFactory.instanceId(stack);
        if (instanceId == null || instanceId.isBlank()) {
            return false;
        }
        String guard = owner + ":" + instanceId;
        if (!lossRecoveryInFlight.add(guard)) {
            return true;
        }
        int amount = Math.max(1, Math.min(64, stack.getAmount()));
        CompletableFuture<Void> queued;
        try {
            queued = database.queuePendingRefund(owner, definition.id(), amount, instanceId);
        } catch (Throwable error) {
            lossRecoveryInFlight.remove(guard);
            getLogger().log(java.util.logging.Level.WARNING, "Unable to queue narcotic recovery", error);
            return false;
        }
        // A completed exceptional future means the journal append itself
        // failed.  Keep the physical copy in that case; a normal completion
        // means the write-ahead row is durable even if PostgreSQL is offline.
        if (queued.isCompletedExceptionally()) {
            lossRecoveryInFlight.remove(guard);
            return false;
        }
        UUID ownerUuid = owner;
        queued.whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(this, () -> {
            try {
                if (removePhysical != null) {
                    removePhysical.run();
                }
                Player ownerPlayer = Bukkit.getPlayer(ownerUuid);
                if (ownerPlayer != null && ownerPlayer.isOnline()) {
                    processPendingRefunds(ownerPlayer);
                }
            } finally {
                lossRecoveryInFlight.remove(guard);
            }
        }));
        return true;
    }

    private boolean isDestructiveEntityDamage(EntityDamageEvent.DamageCause cause) {
        return true; // official narcotics are never allowed to be destroyed in-world
    }

    private boolean isDestructiveBlock(Material type) {
        return type == Material.CACTUS || type == Material.FIRE || type == Material.SOUL_FIRE
                || type == Material.LAVA || type == Material.MAGMA_BLOCK || type == Material.POWDER_SNOW;
    }

    @SuppressWarnings({"deprecation", "removal"})
    private void onNarcoticItemRemoved(EntityRemoveEvent event) {
        if (!(event.getEntity() instanceof Item item)) {
            return;
        }
        EntityRemoveEvent.Cause cause = event.getCause();
        if (cause == EntityRemoveEvent.Cause.PICKUP || cause == EntityRemoveEvent.Cause.PLAYER_QUIT
                || cause == EntityRemoveEvent.Cause.UNLOAD) {
            return;
        }
        ItemStack stack = item.getItemStack();
        if (officialNarcotic(stack) == null) {
            return;
        }
        UUID owner = itemFactory.ownerUuid(stack);
        String instance = itemFactory.instanceId(stack);
        if (owner != null && instance != null && lossRecoveryInFlight.contains(owner + ":" + instance)) {
            return;
        }
        queueNarcoticRecovery(stack, null, "entity-" + String.valueOf(cause).toLowerCase(Locale.ROOT), null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() == null || event.getHand().name().contains("OFF_HAND")) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack inHand = player.getInventory().getItemInMainHand();
        NarcoticDefinition official = itemFactory.resolveOfficial(inHand);
        if (event.isCancelled()) {
            if (official != null || (event.getClickedBlock() != null && cauldronService.isSupportedCauldron(event.getClickedBlock())
                    && recipeService.canEnterCauldron(inHand))) {
                player.sendMessage(ChatColor.RED + "Взаимодействие с котлом запрещено защитой региона.");
            }
            return;
        }
        if (resetInProgress) {
            boolean brewingAttempt = event.getAction() == Action.RIGHT_CLICK_BLOCK
                    && event.getClickedBlock() != null
                    && cauldronService.isSupportedCauldron(event.getClickedBlock())
                    && recipeService.canEnterCauldron(inHand);
            if (official != null || brewingAttempt) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.YELLOW + "Наркотики временно недоступны: идёт сброс состояния.");
            }
            return;
        }

        if (official != null) {
            if (!overdoseService.isStateReady(player)) {
                event.setCancelled(true);
                event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
                player.sendMessage(ChatColor.YELLOW + "Состояние наркотиков загружается. Попробуйте ещё раз через секунду.");
                return;
            }
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK && isUnsafeConsumeTarget(event.getClickedBlock())) {
                event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            }
            if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
                return;
            }
            if (!database.hasAsyncCapacity()) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.YELLOW + "Наркотики временно недоступны: база данных занята. Попробуйте через несколько секунд.");
                return;
            }
            event.setCancelled(true);
            long now = Instant.now().getEpochSecond();
            int cooldownSeconds = configService.consumeCooldownSeconds();
            if (cooldownSeconds > 0) {
                long cooldownUntil = consumeCooldownUntil.getOrDefault(player.getUniqueId(), 0L);
                if (cooldownUntil > now) {
                    player.sendMessage(message("consume_cooldown", String.valueOf(cooldownUntil - now)));
                    return;
                }
                consumeCooldownUntil.put(player.getUniqueId(), now + cooldownSeconds);
            }
            if (!consumeInFlight.add(player.getUniqueId())) {
                player.sendMessage(ChatColor.YELLOW + "Предыдущая операция ещё сохраняется. Попробуйте через секунду.");
                return;
            }
            String reservedInstanceId = itemFactory.instanceId(inHand);
            if (reservedInstanceId == null || reservedInstanceId.isBlank()) {
                consumeInFlight.remove(player.getUniqueId());
                consumeCooldownUntil.remove(player.getUniqueId());
                player.sendMessage(ChatColor.RED + "Narcotic instance metadata is missing; obtain a new item.");
                return;
            }
            String reservedNarcoticId = official.id();
            int reservedQuantity = Math.max(1, itemFactory.exactQuantity(player, reservedInstanceId, reservedNarcoticId));
            consumeReservations.put(player.getUniqueId(), reservedInstanceId);
            consumeReservationNarcotics.put(player.getUniqueId(), reservedNarcoticId);
            consumeReservationQuantities.put(player.getUniqueId(), reservedQuantity);
            // Persist first; remove the exact issued stack only after the DB
            // acknowledgement so a failed write never deletes the item.
            database.reserveConsumption(player.getUniqueId(), reservedInstanceId, reservedNarcoticId, reservedQuantity)
                    .thenCompose(ignored -> overdoseService.consume(player, official, reservedInstanceId))
                    .whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(this, () -> {
                if (error == null) {
                    Player current = Bukkit.getPlayer(player.getUniqueId());
                    if (current == null || !current.isOnline()) {
                        // Keep the reservation until the player reconnects;
                        // clearing it here would leave a committed use and a
                        // physical copy that can be consumed again.
                        return;
                    }
                    completePhysicalConsumption(current, reservedInstanceId, reservedNarcoticId, reservedQuantity);
                    consumeReservations.remove(current.getUniqueId(), reservedInstanceId);
                    consumeReservationNarcotics.remove(current.getUniqueId(), reservedNarcoticId);
                    consumeReservationQuantities.remove(current.getUniqueId(), reservedQuantity);
                    consumeInFlight.remove(current.getUniqueId());
                    return;
                }
                consumeReservations.remove(player.getUniqueId(), reservedInstanceId);
                consumeReservationNarcotics.remove(player.getUniqueId(), reservedNarcoticId);
                consumeReservationQuantities.remove(player.getUniqueId(), reservedQuantity);
                consumeInFlight.remove(player.getUniqueId());
                consumeCooldownUntil.remove(player.getUniqueId());
                getLogger().log(java.util.logging.Level.WARNING, "Failed to persist narcotic consumption for " + player.getUniqueId(), error);
                // Resolve the durable marker before allowing the item to be
                // used again. A failed future can be an acknowledgement loss
                // after PostgreSQL committed: recovery removes the exact item
                // for STATE_COMMITTED rows, while RESERVED rows are released
                // without creating a refund or duplicate.
                Bukkit.getScheduler().runTaskLater(this, () -> recoverDurableConsumptions(player), 2L);
                player.sendMessage(ChatColor.RED + "Не удалось сохранить использование; предмет не списан.");
            }));
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Block block = event.getClickedBlock();
        if ((block.getType() == Material.CAULDRON || block.getType() == Material.WATER_CAULDRON) && !cauldronService.isSupportedCauldron(block)) {
            return;
        }
        if (!cauldronService.isSupportedCauldron(block)) {
            return;
        }
        if (!recipeService.canEnterCauldron(inHand)) {
            return;
        }
        if (cauldronService.tryAddIngredient(player, block, inHand)) {
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        cauldronService.handleCauldronBroken(block, event.getPlayer().getLocation());
        if (block.getType() == Material.FIRE || block.getType() == Material.SOUL_FIRE || block.getType() == Material.NETHERRACK) {
            cauldronService.handleRigSupportBroken(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCauldronLevelChange(CauldronLevelChangeEvent event) {
        cauldronService.handleCauldronLevelChange(event.getBlock(), event.getNewState().getBlockData());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (cauldronService.cachedStateCount() <= 0) {
            return;
        }
        getServer().getScheduler().runTaskLater(this, () -> cauldronService.reconcileLoadedChunk(event.getWorld().getName(), event.getChunk().getX(), event.getChunk().getZ()), 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && isReservedConsumeItem(player, event)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.YELLOW + "Предмет занят сохранением использования; повторите действие через секунду.");
            player.updateInventory();
            return;
        }
        if (shouldBlockInventoryClick(event)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                sendBlocked(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player
                && isReservedConsumeInstance(player, event.getOldCursor())) {
            event.setCancelled(true);
            player.updateInventory();
            return;
        }
        if (!itemFactory.isOfficialFinishedItem(event.getOldCursor())) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (isBlockedDestination(top) && event.getRawSlots().stream().anyMatch(slot -> slot >= 0 && slot < top.getSize())) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                sendBlocked(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCreativeOfficialCopy(InventoryCreativeEvent event) {
        if (itemFactory.isOfficialCandidate(event.getCursor()) || itemFactory.isOfficialCandidate(event.getCurrentItem())) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                player.updateInventory();
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (!itemFactory.isOfficialFinishedItem(event.getItem())) {
            return;
        }
        if (isReservedConsumeInstance(itemFactory.instanceId(event.getItem()))) {
            event.setCancelled(true);
            return;
        }
        if (isBlockedDestination(event.getDestination()) || isBlockedDestination(event.getSource())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryPickup(InventoryPickupItemEvent event) {
        ItemStack stack = event.getItem().getItemStack();
        if (isReservedConsumeInstance(itemFactory.instanceId(stack))) {
            event.setCancelled(true);
            return;
        }
        if (itemFactory.isOfficialFinishedItem(stack) && isBlockedDestination(event.getInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onNarcoticPickup(EntityPickupItemEvent event) {
        if (event == null || event.getItem() == null || !(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack stack = event.getItem().getItemStack();
        if (officialNarcotic(stack) == null) {
            return;
        }
        UUID owner = itemFactory.ownerUuid(stack);
        if (owner == null || owner.equals(player.getUniqueId())) {
            if (owner == null) {
                itemFactory.bindOwnerIfMissing(stack, player.getUniqueId());
                event.getItem().setItemStack(stack);
            }
            return;
        }
        event.setCancelled(true);
        queueNarcoticRecovery(stack, owner, "foreign-pickup", event.getItem()::remove);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onNarcoticDrop(PlayerDropItemEvent event) {
        if (event == null || event.getItemDrop() == null || officialNarcotic(event.getItemDrop().getItemStack()) == null) {
            return;
        }
        ItemStack stack = event.getItemDrop().getItemStack();
        UUID owner = itemFactory.ownerUuid(stack);
        if (owner == null) {
            itemFactory.bindOwnerIfMissing(stack, event.getPlayer().getUniqueId());
            event.getItemDrop().setItemStack(stack);
            return;
        }
        if (owner.equals(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        queueNarcoticRecovery(stack, owner, "foreign-drop", event.getItemDrop()::remove);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onNarcoticDespawn(ItemDespawnEvent event) {
        if (event == null || event.getEntity() == null || officialNarcotic(event.getEntity().getItemStack()) == null) {
            return;
        }
        event.setCancelled(true);
        queueNarcoticRecovery(event.getEntity().getItemStack(), null, "despawn", event.getEntity()::remove);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onNarcoticDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Item item) || officialNarcotic(item.getItemStack()) == null
                || !isDestructiveEntityDamage(event.getCause())) {
            return;
        }
        event.setCancelled(true);
        queueNarcoticRecovery(item.getItemStack(), null, "damage-" + String.valueOf(event.getCause()).toLowerCase(Locale.ROOT), item::remove);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onNarcoticInsideBlock(EntityInsideBlockEvent event) {
        if (!(event.getEntity() instanceof Item item) || event.getBlock() == null
                || !isDestructiveBlock(event.getBlock().getType()) || officialNarcotic(item.getItemStack()) == null) {
            return;
        }
        event.setCancelled(true);
        queueNarcoticRecovery(item.getItemStack(), null, "block-" + event.getBlock().getType().name().toLowerCase(Locale.ROOT), item::remove);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onNarcoticMerge(ItemMergeEvent event) {
        if ((event.getEntity() != null && officialNarcotic(event.getEntity().getItemStack()) != null)
                || (event.getTarget() != null && officialNarcotic(event.getTarget().getItemStack()) != null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onNarcoticCreativeDelete(InventoryCreativeEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() >= 0) {
            return;
        }
        ItemStack candidate = officialNarcotic(event.getCursor()) != null ? event.getCursor() : event.getCurrentItem();
        if (officialNarcotic(candidate) == null) {
            return;
        }
        event.setCancelled(true);
        if (queueNarcoticRecovery(candidate, player.getUniqueId(), "creative-delete", () -> {
            // The InventoryCreativeEvent object is no longer authoritative
            // when the durable callback runs. Clear the live cursor instead,
            // otherwise the server can put the deleted stack back on the next
            // inventory update.
            player.setItemOnCursor(new ItemStack(Material.AIR));
            player.updateInventory();
        })) {
            // The callback clears the cursor only after the durable append.
            player.updateInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onReservedConsumeDrop(PlayerDropItemEvent event) {
        if (event == null || event.getPlayer() == null || event.getItemDrop() == null) {
            return;
        }
        if (isReservedConsumeInstance(event.getPlayer(), event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getPlayer().updateInventory();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        for (ItemStack stack : event.getInventory().getMatrix()) {
            if (itemFactory.isOfficialFinishedItem(stack)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (itemFactory.isOfficialFinishedItem(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (itemFactory.isOfficialFinishedItem(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsumeMilk(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() == Material.MILK_BUCKET && overdoseService.shouldBlockMilk(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        overdoseService.handleMovementInversion(event);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        bindOwners(event.getPlayer());
        overdoseService.preloadState(event.getPlayer().getUniqueId());
        processPendingRefunds(event.getPlayer());
        visualRuntime.clearTracking(event.getPlayer());
        String reservedInstanceId = consumeReservations.get(event.getPlayer().getUniqueId());
        if (reservedInstanceId != null) {
            Bukkit.getScheduler().runTaskLater(this, () -> finalizeOfflineConsume(event.getPlayer(), reservedInstanceId), 1L);
        }
        Bukkit.getScheduler().runTaskLater(this, () -> recoverDurableConsumptions(event.getPlayer()), 2L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        overdoseService.releasePlayerSession(event.getPlayer());
        // Keep an in-flight reservation until its DB future completes.  A
        // quit must not turn a committed use into a reusable physical item.
        visualRuntime.clearTracking(event.getPlayer());
    }

    private boolean isReservedConsumeInstance(Player player, ItemStack stack) {
        if (player == null || stack == null) {
            return false;
        }
        String reserved = consumeReservations.get(player.getUniqueId());
        return reserved != null && !reserved.isBlank() && reserved.equals(itemFactory.instanceId(stack));
    }

    private boolean isReservedConsumeInstance(String instanceId) {
        return instanceId != null && !instanceId.isBlank() && consumeReservations.containsValue(instanceId);
    }

    private boolean isReservedConsumeItem(Player player, InventoryClickEvent event) {
        if (player == null || event == null) {
            return false;
        }
        if (isReservedConsumeInstance(player, event.getCurrentItem())
                || isReservedConsumeInstance(player, event.getCursor())) {
            return true;
        }
        if (event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0) {
            return isReservedConsumeInstance(player, player.getInventory().getItem(event.getHotbarButton()));
        }
        return event.getClick() == ClickType.SWAP_OFFHAND
                && isReservedConsumeInstance(player, player.getInventory().getItemInOffHand());
    }

    private void completePhysicalConsumption(Player player, String instanceId, String narcoticId, int quantityBefore) {
        if (player == null || instanceId == null || instanceId.isBlank()) {
            return;
        }
        int currentQuantity = itemFactory.exactQuantity(player, instanceId, narcoticId);
        boolean removed = currentQuantity >= Math.max(1, quantityBefore)
                && itemFactory.consumeOneExact(player, instanceId, narcoticId);
        database.completeConsumptionReservation(instanceId).exceptionally(error -> {
            getLogger().log(java.util.logging.Level.WARNING,
                    "Unable to finalize narcotic reservation " + instanceId, error);
            return null;
        });
        if (removed) {
            player.sendMessage(ChatColor.GREEN + "Наркотик использован.");
        } else {
            // The state is already committed.  Do not enqueue a refund here:
            // the item may have been removed just before a restart, and a
            // refund would duplicate the same physical instance.
            player.sendMessage(ChatColor.YELLOW + "Наркотик уже использован или его нет в инвентаре; повторное использование запрещено.");
        }
    }

    private void finalizeOfflineConsume(Player player, String reservedInstanceId) {
        if (player == null || !player.isOnline() || reservedInstanceId == null || reservedInstanceId.isBlank()) {
            return;
        }
        String current = consumeReservations.get(player.getUniqueId());
        if (!reservedInstanceId.equals(current)) {
            return;
        }
        // The durable DB state was committed before reconnect.  Remove the
        // exact instance if it is still present; never enqueue a second refund.
        String narcoticId = consumeReservationNarcotics.get(player.getUniqueId());
        int quantityBefore = Math.max(1, consumeReservationQuantities.getOrDefault(player.getUniqueId(), 1));
        completePhysicalConsumption(player, reservedInstanceId, narcoticId, quantityBefore);
        consumeReservations.remove(player.getUniqueId(), reservedInstanceId);
        if (narcoticId != null) {
            consumeReservationNarcotics.remove(player.getUniqueId(), narcoticId);
        }
        consumeReservationQuantities.remove(player.getUniqueId());
        consumeInFlight.remove(player.getUniqueId());
    }

    private void recoverDurableConsumptions(Player player) {
        if (player == null || !player.isOnline() || database == null) {
            return;
        }
        database.loadConsumptionReservations(player.getUniqueId(), 16).whenComplete((rows, error) ->
                Bukkit.getScheduler().runTask(this, () -> {
                    if (error != null) {
                        getLogger().log(java.util.logging.Level.WARNING,
                                "Failed to recover narcotic consumption reservations", error);
                        return;
                    }
                    if (rows == null || !player.isOnline()) {
                        return;
                    }
                    for (NarcoticsDatabase.ConsumptionReservation row : rows) {
                        if (row == null || row.instanceId() == null || row.instanceId().isBlank()) {
                            continue;
                        }
                        if (!"STATE_COMMITTED".equalsIgnoreCase(row.status())) {
                            // A RESERVED row has no committed player state:
                            // release it and leave the physical item alone.
                            database.releaseConsumptionReservation(row.instanceId());
                            continue;
                        }
                        consumeReservations.put(player.getUniqueId(), row.instanceId());
                        consumeReservationNarcotics.put(player.getUniqueId(), row.narcoticId());
                        consumeReservationQuantities.put(player.getUniqueId(), row.quantityBefore());
                        completePhysicalConsumption(player, row.instanceId(), row.narcoticId(), row.quantityBefore());
                        consumeReservations.remove(player.getUniqueId(), row.instanceId());
                        consumeReservationNarcotics.remove(player.getUniqueId(), row.narcoticId());
                        consumeReservationQuantities.remove(player.getUniqueId());
                        consumeInFlight.remove(player.getUniqueId());
                    }
                }));
    }

    private void processPendingRefunds(Player player) {
        if (player == null || !player.isOnline() || database == null) {
            return;
        }
        // Replay the write-ahead refund journal before reserving rows. This is
        // what makes a loss recoverable even when PostgreSQL was unavailable
        // at the moment the item was destroyed; no restart is required.
        database.flushPendingRefundJournal().whenComplete((ignored, flushError) ->
                Bukkit.getScheduler().runTask(this, () -> reservePendingRefunds(player, flushError)));
    }

    private void reservePendingRefunds(Player player, Throwable flushError) {
        if (player == null || !player.isOnline() || database == null) {
            return;
        }
        if (flushError != null) {
            getLogger().log(java.util.logging.Level.WARNING, "Failed to replay narcotics refund journal", flushError);
        }
        database.reservePendingRefunds(player.getUniqueId(), 16).whenComplete((rows, error) -> Bukkit.getScheduler().runTask(this, () -> {
            if (error != null || rows == null || rows.isEmpty() || !player.isOnline()) {
                if (error != null) {
                    getLogger().log(java.util.logging.Level.WARNING, "Failed to load narcotics refunds", error);
                }
                return;
            }
            for (NarcoticsDatabase.PendingRefund row : rows) {
                ItemStack refund = null;
                if (row.narcoticId().startsWith("INGREDIENT:")) {
                    IngredientEntry entry = IngredientEntry.deserialize(row.narcoticId().substring("INGREDIENT:".length()));
                    if (entry != null) {
                        refund = entry.toItemStack();
                    }
                } else {
                    NarcoticDefinition definition = configService.items().get(row.narcoticId());
                    if (definition != null) {
                        refund = itemFactory.createOfficialItem(definition, Math.max(1, row.amount()), player.getUniqueId());
                    }
                }
                if (refund == null) {
                    database.releasePendingRefund(row.id());
                    continue;
                }
                // The database row is intentionally marked DELIVERING before
                // we touch the inventory.  A crash between addItem() and the
                // completion update must therefore be recognizable on the
                // next join; otherwise the stale-row retry would duplicate
                // the refund.  The marker is scoped to this exact row and is
                // never used as an authorization signal for normal items.
                if (hasPendingRefundMarker(player, row.id())) {
                    database.completePendingRefund(row.id());
                    continue;
                }
                markPendingRefund(refund, row.id());
                refund.setAmount(Math.max(1, row.amount()));
                Map<Integer, ItemStack> leftovers = player.getInventory().addItem(refund);
                if (!leftovers.isEmpty()) {
                    // addItem may have inserted a partial stack before
                    // reporting leftovers.  Remove that partial, then release
                    // the row; otherwise the next retry would duplicate the
                    // already inserted portion.
                    removePendingRefundMarkers(player, row.id());
                    database.releasePendingRefund(row.id());
                    player.sendMessage(ChatColor.YELLOW + "Освободите место для возврата наркотика.");
                } else {
                    database.completePendingRefund(row.id());
                }
            }
            player.updateInventory();
        }));
    }

    private void markPendingRefund(ItemStack stack, String refundId) {
        if (stack == null || stack.getItemMeta() == null || pendingRefundKey == null
                || refundId == null || refundId.isBlank()) {
            return;
        }
        var meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(pendingRefundKey, PersistentDataType.STRING, refundId);
        stack.setItemMeta(meta);
    }

    private boolean hasPendingRefundMarker(Player player, String refundId) {
        if (player == null || refundId == null || refundId.isBlank() || pendingRefundKey == null) {
            return false;
        }
        for (ItemStack stack : player.getInventory().getContents()) {
            if (hasPendingRefundMarker(stack, refundId)) {
                return true;
            }
        }
        return hasPendingRefundMarker(player.getItemOnCursor(), refundId);
    }

    private boolean hasPendingRefundMarker(ItemStack stack, String refundId) {
        if (stack == null || stack.getType() == Material.AIR || stack.getItemMeta() == null
                || pendingRefundKey == null) {
            return false;
        }
        String marker = stack.getItemMeta().getPersistentDataContainer().get(pendingRefundKey, PersistentDataType.STRING);
        return refundId.equals(marker);
    }

    private void removePendingRefundMarkers(Player player, String refundId) {
        if (player == null || refundId == null || refundId.isBlank()) {
            return;
        }
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (hasPendingRefundMarker(stack, refundId)) {
                player.getInventory().setItem(slot, new ItemStack(Material.AIR));
            }
        }
        if (hasPendingRefundMarker(player.getItemOnCursor(), refundId)) {
            player.setItemOnCursor(new ItemStack(Material.AIR));
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        overdoseService.clearActiveEffects(event.getEntity(), true);
        visualRuntime.clear(event.getEntity());
        Player player = event.getEntity();
        if (event.getKeepInventory() || event.getDrops().isEmpty()) {
            return;
        }
        // Do not let a death drop be picked up or fall into the void before a
        // durable recovery row exists.  queuePendingRefund writes the local
        // journal before returning; removing the drop after that boundary is
        // therefore safe even when PostgreSQL is unavailable.
        List<ItemStack> recoverable = new ArrayList<>();
        for (ItemStack stack : new ArrayList<>(event.getDrops())) {
            if (officialNarcotic(stack) == null) {
                continue;
            }
            if (itemFactory.ownerUuid(stack) == null) {
                itemFactory.bindOwnerIfMissing(stack, player.getUniqueId());
            }
            if (queueNarcoticRecovery(stack, player.getUniqueId(), "death", null)) {
                recoverable.add(stack);
            }
        }
        if (!recoverable.isEmpty()) {
            event.getDrops().removeAll(recoverable);
            player.sendMessage(ChatColor.YELLOW + "Потерянные наркотики сохранены и будут возвращены после входа.");
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(this, () -> overdoseService.restoreActiveOverdose(event.getPlayer()));
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        overdoseService.clearActiveEffects(event.getPlayer(), true);
        visualRuntime.clear(event.getPlayer());
        Bukkit.getScheduler().runTask(this, () -> overdoseService.restoreActiveOverdose(event.getPlayer()));
    }

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        boolean ready = switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED, ACCEPTED -> true;
            default -> false;
        };
        visualRuntime.markResourcePackReady(event.getPlayer(), ready);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if ("cmclient".equalsIgnoreCase(command.getName())) {
            return clientBridge.handleCommand(sender, args, (target, payload) -> {
                String[] parts = payload.split(":", 2);
                int seconds = 30;
                if (parts.length == 2) {
                    try {
                        seconds = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException parseError) {
                        getLogger().warning("cmclient fallback test received invalid duration from "
                                + sender.getName() + " payload=" + payload + ": " + parseError.getMessage());
                        seconds = 30;
                    }
                }
                visualRuntime.applyServerFallbackTest(target, parts[0], seconds);
            });
        }
        if (args.length == 0) {
            sendHelpV2(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        try {
            return switch (sub) {
                case "give" -> handleGive(sender, args);
                case "recover", "restore" -> handleRecover(sender);
                case "reload" -> handleReload(sender);
                case "reset-state", "reset" -> handleResetState(sender, args);
                case "clear", "clearoverdose" -> handleClearPlayer(sender, args);
                case "test" -> handleDrugTest(sender, args);
                case "effects" -> handleEffectsOnlyTest(sender, args);
                case "shader" -> handleShaderOnlyTest(sender, args);
                case "overdose" -> handleOverdoseTest(sender, args);
                case "stop" -> handleStopVisuals(sender, args);
                case "texture", "texture-mode" -> handleTextureV2(sender, args);
                case "visuals" -> handleVisualsV3(sender, args);
                case "visual-mode" -> handleVisualMode(sender, args);
                case "visual-effect" -> handleVisualEffect(sender, args);
                case "selfcheck" -> handleSelfCheckV2(sender);
                case "info" -> handleInfo(sender, args);
                case "setweight" -> handleSetWeight(sender, args);
                case "setthreshold" -> handleSetThreshold(sender, args);
                case "setwindow" -> handleSetWindow(sender, args);
                case "setduration" -> handleSetDuration(sender, args);
                default -> {
                    sendHelpV2(sender);
                    yield true;
                }
            };
        } catch (Exception error) {
            getLogger().warning(command.getName() + " failed: " + error.getMessage());
            if (sender instanceof Player player) {
                forwardBugToAdminPlus(player, "narcotics-command", command.getName() + " " + String.join(" ", args), error);
            }
            sender.sendMessage(ChatColor.RED + "Не удалось выполнить команду.");
            return true;
        }
    }

    /** Keep the player-facing error short while attaching technical context
     * to the central /reporta workflow when AdminPlus is present. */
    private void forwardBugToAdminPlus(Player player, String source, String action, Throwable error) {
        Plugin admin = Bukkit.getPluginManager().getPlugin("CopiMineUltimateAdminPlus");
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
            ).invoke(admin, player, source, action, error, player.getInventory().getItemInMainHand(), player.getLocation());
        } catch (ReflectiveOperationException bridgeError) {
            getLogger().fine("reporta bridge unavailable: " + bridgeError.getMessage());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if ("cmclient".equalsIgnoreCase(command.getName())) {
            if (args.length == 1) {
                return prefix(List.of("check", "visualtest", "fallbacktest", "require"), args[0]);
            }
            if (args.length == 2 && ("check".equalsIgnoreCase(args[0]) || "visualtest".equalsIgnoreCase(args[0]) || "fallbacktest".equalsIgnoreCase(args[0]))) {
                return prefix(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
            }
            if (args.length == 2 && "require".equalsIgnoreCase(args[0])) {
                return prefix(List.of("client"), args[1]);
            }
            if (args.length == 3 && ("visualtest".equalsIgnoreCase(args[0]) || "fallbacktest".equalsIgnoreCase(args[0]))) {
                return prefix(new ArrayList<>(configService.visualEffectIds()), args[2]);
            }
            if (args.length == 3 && "require".equalsIgnoreCase(args[0])) {
                return prefix(List.of("true", "false"), args[2]);
            }
            return List.of();
        }
        if (args.length == 1) {
            return prefix(List.of("give", "recover", "restore", "reload", "reset", "clear", "clearoverdose", "test", "effects", "shader", "overdose", "stop", "texture", "visuals", "selfcheck", "info", "setweight", "setthreshold", "setwindow", "setduration"), args[0]);
        }
        if (args.length == 2 && "give".equalsIgnoreCase(args[0])) {
            return prefix(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 3 && "give".equalsIgnoreCase(args[0])) {
            List<String> values = new ArrayList<>(configService.itemIds());
            values.add("all");
            return prefix(values, args[2]);
        }
        if (args.length == 2 && ("clear".equalsIgnoreCase(args[0]) || "clearoverdose".equalsIgnoreCase(args[0]) || "info".equalsIgnoreCase(args[0]) || "overdose".equalsIgnoreCase(args[0]) || "stop".equalsIgnoreCase(args[0]))) {
            return prefix(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 2 && ("test".equalsIgnoreCase(args[0]) || "effects".equalsIgnoreCase(args[0]))) {
            return prefix(new ArrayList<>(configService.itemIds()), args[1]);
        }
        if (args.length == 2 && "shader".equalsIgnoreCase(args[0])) {
            List<String> values = new ArrayList<>(configService.visualEffectIds());
            values.addAll(configService.itemIds());
            values.add("overdose");
            return prefix(values.stream().distinct().toList(), args[1]);
        }
        if (args.length == 3 && ("test".equalsIgnoreCase(args[0]) || "effects".equalsIgnoreCase(args[0]) || "shader".equalsIgnoreCase(args[0]))) {
            return prefix(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
        }
        if (args.length == 2 && ("texture".equalsIgnoreCase(args[0]) || "texture-mode".equalsIgnoreCase(args[0]))) {
            return prefix(List.of("mode", "migrate"), args[1]);
        }
        if (args.length == 3 && ("texture".equalsIgnoreCase(args[0]) || "texture-mode".equalsIgnoreCase(args[0])) && "mode".equalsIgnoreCase(args[1])) {
            return prefix(List.of("VANILLA", "CUSTOM"), args[2]);
        }
        if (args.length == 3 && ("texture".equalsIgnoreCase(args[0]) || "texture-mode".equalsIgnoreCase(args[0])) && "migrate".equalsIgnoreCase(args[1])) {
            return prefix(List.of("online", "nearby"), args[2]);
        }
        if (args.length == 2 && "visuals".equalsIgnoreCase(args[0])) {
            return prefix(List.of("status", "enable", "disable", "mode", "test"), args[1]);
        }
        if (args.length == 3 && "visuals".equalsIgnoreCase(args[0]) && ("enable".equalsIgnoreCase(args[1]) || "disable".equalsIgnoreCase(args[1]))) {
            List<String> values = new ArrayList<>(configService.visualEffectIds());
            values.add("all");
            return prefix(values, args[2]);
        }
        if (args.length == 3 && "visuals".equalsIgnoreCase(args[0]) && "mode".equalsIgnoreCase(args[1])) {
            return prefix(List.of("AUTO", "CLIENT_MOD", "SERVER_FALLBACK"), args[2]);
        }
        if (args.length == 3 && "visuals".equalsIgnoreCase(args[0]) && "test".equalsIgnoreCase(args[1])) {
            return prefix(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
        }
        if (args.length == 4 && "visuals".equalsIgnoreCase(args[0]) && "test".equalsIgnoreCase(args[1])) {
            return prefix(new ArrayList<>(configService.visualEffectIds()), args[3]);
        }
        if (args.length == 2 && ("reset-state".equalsIgnoreCase(args[0]) || "reset".equalsIgnoreCase(args[0]))) {
            return prefix(List.of("confirm"), args[1]);
        }
        if (args.length == 2 && "setweight".equalsIgnoreCase(args[0])) {
            return prefix(new ArrayList<>(configService.itemIds()), args[1]);
        }
        return List.of();
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "copimine.narcotics.give")) {
            sender.sendMessage(message("no_permission"));
            return true;
        }
        if (args.length < 3) {
            sendHelpV2(sender);
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(message("player_not_found"));
            return true;
        }
        String rawId = args[2].toLowerCase(Locale.ROOT);
        if ("all".equals(rawId)) {
            int dropped = 0;
            for (NarcoticDefinition definition : configService.items().values()) {
                dropped += deliverOfficialItem(target, definition);
            }
            sender.sendMessage(message("all_given", target.getName()));
            if (dropped > 0) {
                sender.sendMessage(ChatColor.YELLOW + "Часть предметов была выброшена рядом с игроком: " + dropped);
            }
            database.auditAsync(sender.getName(), "give_all", "target=" + target.getName() + ",dropped=" + dropped);
            return true;
        }
        NarcoticDefinition definition = configService.items().get(rawId);
        if (definition == null) {
            sender.sendMessage(message("unknown_item"));
            return true;
        }
        int dropped = deliverOfficialItem(target, definition);
        sender.sendMessage(message("item_given", definition.plainDisplayName(), target.getName()));
        if (dropped > 0) {
            sender.sendMessage(ChatColor.YELLOW + "Предмет не влез в инвентарь и был выброшен рядом с игроком.");
        }
        database.auditAsync(sender.getName(), "give", "target=" + target.getName() + ",item=" + definition.id() + ",dropped=" + dropped);
        return true;
    }

    private boolean handleRecover(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        processPendingRefunds(player);
        player.sendMessage(ChatColor.GREEN + "Проверяю сохранённые возвраты наркотиков и повторяю выдачу, если она ожидает места в инвентаре.");
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!hasPermission(sender, "copimine.narcotics.reload")) {
            sender.sendMessage(message("no_permission"));
            return true;
        }
        try {
            NarcoticsConfigService candidate = new NarcoticsConfigService(this);
            candidate.reload();
            configService = candidate;
            recipeService.reload(configService);
            itemFactory.reload(configService);
            clientBridge.reload(configService);
            visualRuntime.reload(configService);
            overdoseService.reload(configService);
            cauldronService.reload(configService, recipeService, itemFactory);
            sender.sendMessage(message("reload_ok"));
        } catch (Exception error) {
            getLogger().warning("Narcotics reload rejected: " + error.getMessage());
            sender.sendMessage(ChatColor.RED + "Рецепты не изменены: новая конфигурация содержит ошибку.");
        }
        return true;
    }

    private boolean handleResetState(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "copimine.narcotics.reset")) {
            sender.sendMessage(message("no_permission"));
            return true;
        }
        String confirmArg = args.length >= 2 ? args[args.length - 1] : "";
        if (!"confirm".equalsIgnoreCase(confirmArg)) {
            sender.sendMessage(message("state_reset_need_confirm"));
            return true;
        }
        if (resetInProgress) {
            sender.sendMessage(ChatColor.YELLOW + "Сброс уже выполняется.");
            return true;
        }
        resetInProgress = true;
        database.resetNarcoticsState().whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(this, () -> {
            if (error != null) {
                getLogger().warning("Narcotics reset failed: " + error.getMessage());
                resetInProgress = false;
                sender.sendMessage(ChatColor.RED + "Не удалось очистить состояние наркотиков.");
                return;
            }
            cauldronService.clearCache();
            overdoseService.clearAllCachedState();
            for (Player online : Bukkit.getOnlinePlayers()) {
                overdoseService.clearActiveEffects(online, true);
                visualRuntime.clear(online);
            }
            sender.sendMessage(message("state_reset_ok"));
            resetInProgress = false;
        }));
        return true;
    }

    private boolean handleClearPlayer(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "copimine.narcotics.clearoverdose")) {
            sender.sendMessage(message("no_permission"));
            return true;
        }
        if (args.length < 2) {
            sendHelpV2(sender);
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(message("player_not_found"));
            return true;
        }
        overdoseService.clearPlayer(target);
        clientBridge.visuals().clearVisuals(target, "admin-clear");
        visualRuntime.clear(target);
        database.auditAsync(sender.getName(), "clear", "target=" + target.getName());
        sender.sendMessage(ChatColor.GREEN + "Состояние очищено: " + target.getName());
        return true;
    }

    private boolean handleDrugTest(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "copimine.narcotics.admin")) {
            sender.sendMessage(message("no_permission"));
            return true;
        }
        if (args.length < 3) {
            sendHelpV2(sender);
            return true;
        }
        NarcoticDefinition definition = configService.items().get(args[1].toLowerCase(Locale.ROOT));
        if (definition == null) {
            sender.sendMessage(message("unknown_item"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(message("player_not_found"));
            return true;
        }
        Integer seconds = parseBoundedInt(sender, args.length >= 4 ? args[3] : String.valueOf(Math.max(15, definition.maxEffectDurationSeconds(false))), "duration", 1, 600, false);
        if (seconds == null) {
            return true;
        }
        overdoseService.runDrugTest(target, definition, seconds, true);
        database.auditAsync(sender.getName(), "test", definition.id() + "," + target.getName() + "," + seconds);
        sender.sendMessage(ChatColor.GREEN + "Тест наркотика запущен: " + definition.plainDisplayName() + " -> " + target.getName());
        return true;
    }

    private boolean handleEffectsOnlyTest(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "copimine.narcotics.admin")) {
            sender.sendMessage(message("no_permission"));
            return true;
        }
        if (args.length < 3) {
            sendHelpV2(sender);
            return true;
        }
        NarcoticDefinition definition = configService.items().get(args[1].toLowerCase(Locale.ROOT));
        if (definition == null) {
            sender.sendMessage(message("unknown_item"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(message("player_not_found"));
            return true;
        }
        Integer seconds = parseBoundedInt(sender, args.length >= 4 ? args[3] : String.valueOf(Math.max(15, definition.maxEffectDurationSeconds(false))), "duration", 1, 600, false);
        if (seconds == null) {
            return true;
        }
        overdoseService.runDrugTest(target, definition, seconds, false);
        database.auditAsync(sender.getName(), "effects", definition.id() + "," + target.getName() + "," + seconds);
        sender.sendMessage(ChatColor.GREEN + "Тест эффектов запущен: " + definition.plainDisplayName() + " -> " + target.getName());
        return true;
    }

    private boolean handleShaderOnlyTest(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "copimine.narcotics.visuals")) {
            sender.sendMessage(message("no_permission"));
            return true;
        }
        if (args.length < 3) {
            sendHelpV2(sender);
            return true;
        }
        String requestedId = args[1].toLowerCase(Locale.ROOT);
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(message("player_not_found"));
            return true;
        }
        String effectId;
        boolean overdoseRoute = "overdose".equalsIgnoreCase(requestedId);
        NarcoticDefinition definition = configService.items().get(requestedId);
        if (definition != null) {
            effectId = definition.visualEffectId();
            overdoseRoute = overdoseRoute || "zhuzevo".equalsIgnoreCase(definition.id());
        } else {
            effectId = args[1].toUpperCase(Locale.ROOT);
        }
        if (!configService.visualEffectIds().contains(effectId)) {
            sender.sendMessage(ChatColor.RED + "Unknown visual effect id or narcotic id.");
            return true;
        }
        Integer seconds = parseBoundedInt(sender, args.length >= 4 ? args[3] : "30", "duration", 1, 600, false);
        if (seconds == null) {
            return true;
        }
        visualRuntime.apply(target, effectId, seconds, overdoseRoute);
        database.auditAsync(sender.getName(), "shader", requestedId + "," + target.getName() + "," + effectId + "," + seconds + "," + overdoseRoute);
        sender.sendMessage(ChatColor.GREEN + "Тест шейдера запущен: " + requestedId + " -> " + target.getName());
        return true;
    }

    private boolean handleOverdoseTest(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "copimine.narcotics.admin")) {
            sender.sendMessage(message("no_permission"));
            return true;
        }
        if (args.length < 2) {
            sendHelpV2(sender);
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(message("player_not_found"));
            return true;
        }
        Integer seconds = parseBoundedInt(sender, args.length >= 3 ? args[2] : "45", "duration", 1, 600, false);
        if (seconds == null) {
            return true;
        }
        NarcoticDefinition definition = configService.items().getOrDefault("zhuzevo", configService.items().values().stream().findFirst().orElse(null));
        if (definition == null) {
            sender.sendMessage(ChatColor.RED + "Нет доступных конфигов наркотиков для теста овердоза.");
            return true;
        }
        overdoseService.runOverdoseTest(target, definition, seconds, true);
        database.auditAsync(sender.getName(), "overdose", target.getName() + "," + definition.id() + "," + seconds);
        sender.sendMessage(ChatColor.GREEN + "Тест овердоза запущен: " + target.getName());
        return true;
    }

    private boolean handleStopVisuals(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "copimine.narcotics.visuals")) {
            sender.sendMessage(message("no_permission"));
            return true;
        }
        if (args.length < 2) {
            sendHelpV2(sender);
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(message("player_not_found"));
            return true;
        }
        clientBridge.visuals().clearVisuals(target, "admin-stop");
        visualRuntime.clear(target);
        database.auditAsync(sender.getName(), "stop", "target=" + target.getName());
        sender.sendMessage(ChatColor.GREEN + "Текущий эффект остановлен: " + target.getName());
        return true;
    }

    private boolean handleTextureV2(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "copimine.narcotics.texture")) {
            sender.sendMessage(message("no_permission"));
            return true;
        }
        if (args.length < 2) {
            sendHelpV2(sender);
            return true;
        }
        if ("mode".equalsIgnoreCase(args[1])) {
            if (args.length < 3) {
                sendHelpV2(sender);
                return true;
            }
            String mode = args[2].toUpperCase(Locale.ROOT);
            if (!VALID_TEXTURE_MODES.contains(mode)) {
                sender.sendMessage(ChatColor.RED + "Неверный режим текстур. Доступно: VANILLA или CUSTOM.");
                return true;
            }
            configService.setTextureMode(mode);
            sender.sendMessage(message("texture_mode_set", configService.textureMode().name()));
            database.auditAsync(sender.getName(), "texture_mode", configService.textureMode().name());
            return true;
        }
        if ("migrate".equalsIgnoreCase(args[1])) {
            if (args.length < 3) {
                sendHelpV2(sender);
                return true;
            }
            int updated = 0;
            if ("online".equalsIgnoreCase(args[2])) {
                for (Player target : Bukkit.getOnlinePlayers()) {
                    updated += itemFactory.migrateOfficialItems(target);
                }
            } else if ("nearby".equalsIgnoreCase(args[2])) {
                Player player = requirePlayer(sender);
                if (player == null) {
                    return true;
                }
                for (var entity : player.getNearbyEntities(32.0D, 32.0D, 32.0D)) {
                    if (entity instanceof Player target) {
                        updated += itemFactory.migrateOfficialItems(target);
                    }
                }
                updated += itemFactory.migrateOfficialItems(player);
                updated += migrateNearbyStorageInventories(player, 12);
            } else {
                sender.sendMessage(ChatColor.RED + "Неверный режим миграции. Доступно: online или nearby.");
                return true;
            }
            database.auditAsync(sender.getName(), "texture_migrate", args[2] + ",updated=" + updated);
            sender.sendMessage(ChatColor.GREEN + "Обновлено предметов: " + updated);
            return true;
        }
        sendHelpV2(sender);
        return true;
    }

    private boolean handleVisualsV3(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "copimine.narcotics.visuals")) {
            sender.sendMessage(message("no_permission"));
            return true;
        }
        if (args.length < 2) {
            sendHelpV2(sender);
            return true;
        }
        if ("status".equalsIgnoreCase(args[1])) {
            NarcoticsResourcePackAudit.Report report = resourcePackAudit.inspect();
            sender.sendMessage(ChatColor.GRAY + "Визуалы включены: " + configService.visualsEnabled());
            sender.sendMessage(ChatColor.GRAY + "Настроенный режим: " + configService.visualMode().name());
            sender.sendMessage(ChatColor.GRAY + "Client bridge включён: " + configService.clientBridgeEnabled());
            sender.sendMessage(ChatColor.GRAY + "Client visuals разрешены: " + configService.allowClientModVisuals());
            sender.sendMessage(ChatColor.GRAY + "Client ZIP shaderpack runtime поддерживается: " + visualRuntime.supportsClientZipShaderpackRuntime());
            sender.sendMessage(ChatColor.GRAY + "Причина ZIP shaderpack runtime: " + visualRuntime.clientShaderpackSupportReason());
            sender.sendMessage(ChatColor.GRAY + "Server overlay поддерживается: " + visualRuntime.supportsServerOverlayRuntime());
            sender.sendMessage(ChatColor.GRAY + "Причина overlay: " + visualRuntime.serverOverlaySupportReason());
            sender.sendMessage(ChatColor.GRAY + "Server fallback поддерживается: " + visualRuntime.supportsServerParticleFallback());
            sender.sendMessage(ChatColor.GRAY + "Клиентский visual runtime поддерживается: " + visualRuntime.supportsClientShaderLikeRuntime());
            sender.sendMessage(ChatColor.GRAY + "Причина клиентского visual runtime: " + visualRuntime.clientShaderLikeSupportReason());
            sender.sendMessage(ChatColor.GRAY + "True shader runtime поддерживается: " + visualRuntime.supportsShaderRuntime());
            sender.sendMessage(ChatColor.GRAY + "Причина true shader runtime: " + visualRuntime.shaderSupportReason());
            sender.sendMessage(ChatColor.GRAY + "Overlay-ассеты на месте: " + !report.overlayTextures().isEmpty());
            sender.sendMessage(ChatColor.GRAY + "Shader-профили на месте: " + !report.shaderProfiles().isEmpty());
            sender.sendMessage(ChatColor.GRAY + "Только серверный fallback: " + (!visualRuntime.supportsServerOverlayRuntime() && !visualRuntime.supportsClientShaderLikeRuntime()));
            sender.sendMessage(ChatColor.GRAY + "Включённые эффекты: " + configService.visualEffectIds().stream().filter(configService::isVisualEffectEnabled).sorted().toList());
            String sampleEffect = configService.visualEffectIds().stream().sorted().findFirst().orElse("CHAOS");
            sender.sendMessage(ChatColor.GRAY + "Маршрут эффекта (" + sampleEffect + "): " + visualRuntime.resolvedModeFor(sampleEffect));
            if (!report.ok()) {
                sender.sendMessage(ChatColor.RED + "Проблема resource pack: " + report.summary());
            }
            return true;
        }
        if ("enable".equalsIgnoreCase(args[1]) || "disable".equalsIgnoreCase(args[1])) {
            boolean enable = "enable".equalsIgnoreCase(args[1]);
            if (args.length == 2) {
                configService.setVisualsEnabled(enable);
                sender.sendMessage(enable ? message("visuals_enabled") : message("visuals_disabled"));
                database.auditAsync(sender.getName(), "visuals", enable ? "enable" : "disable");
                return true;
            }
            String effectId = args[2].toUpperCase(Locale.ROOT);
            if (!"ALL".equals(effectId) && !configService.visualEffectIds().contains(effectId)) {
                sender.sendMessage(ChatColor.RED + "Неизвестный visual effect id.");
                return true;
            }
            if ("ALL".equals(effectId)) {
                for (String id : configService.visualEffectIds()) {
                    configService.setVisualEffectEnabled(id, enable);
                }
            } else {
                configService.setVisualEffectEnabled(effectId, enable);
            }
            if (enable) {
                configService.setVisualsEnabled(true);
            }
            sender.sendMessage(message("visual_effect_set", effectId, enable ? "ON" : "OFF"));
            database.auditAsync(sender.getName(), "visual_effect", effectId + "=" + enable);
            return true;
        }
        if ("mode".equalsIgnoreCase(args[1])) {
            if (args.length < 3) {
                sendHelpV2(sender);
                return true;
            }
            return handleVisualMode(sender, new String[]{"visual-mode", args[2]});
        }
        if ("test".equalsIgnoreCase(args[1])) {
            if (args.length < 4) {
                sendHelpV2(sender);
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(message("player_not_found"));
                return true;
            }
            String requestedId = args[3].toLowerCase(Locale.ROOT);
            boolean overdoseTest = "overdose".equalsIgnoreCase(requestedId);
            String effectId;
            if (configService.items().containsKey(requestedId)) {
                effectId = configService.items().get(requestedId).visualEffectId();
                overdoseTest = overdoseTest || "zhuzevo".equalsIgnoreCase(requestedId);
            } else {
                effectId = args[3].toUpperCase(Locale.ROOT);
            }
            if (!configService.visualEffectIds().contains(effectId)) {
                sender.sendMessage(ChatColor.RED + "Unknown visual effect id or narcotic id.");
                return true;
            }
            Integer seconds = parseBoundedInt(sender, args.length >= 5 ? args[4] : "30", "duration", 1, 600, false);
            if (seconds == null) {
                return true;
            }
            if (overdoseTest) {
                visualRuntime.apply(target, effectId, seconds, true);
            } else {
                visualRuntime.apply(target, effectId, seconds, false);
            }
            database.auditAsync(sender.getName(), "visual_test", target.getName() + "," + requestedId + "," + effectId + "," + seconds + "," + overdoseTest);
            sender.sendMessage(ChatColor.GREEN + "Visual test started: " + target.getName()
                    + " / request=" + requestedId
                    + " / effect=" + effectId
                    + (overdoseTest ? " / overdose-route" : ""));
            return true;
        }
        sendHelpV2(sender);
        return true;
    }

    private boolean handleClearOverdose(CommandSender sender, String[] args) {
        return handleClearPlayer(sender, args);
    }

    private boolean handleTexture(CommandSender sender, String[] args) {
        return handleTextureV2(sender, args);
    }

    private boolean handleVisualsV2(CommandSender sender, String[] args) {
        return handleVisualsV3(sender, args);
    }

    private boolean handleVisuals(CommandSender sender, String[] args) {
        return handleVisualsV3(sender, args);
    }

    private boolean handleVisualMode(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "copimine.narcotics.visuals")) {
            sender.sendMessage(message("no_permission"));
            return true;
        }
        if (args.length < 2) {
            sendHelpV2(sender);
            return true;
        }
        String mode = args[1].toUpperCase(Locale.ROOT);
        if (!VALID_VISUAL_MODES.contains(mode)) {
            sender.sendMessage(ChatColor.RED + "Неверный visual mode. Доступно: AUTO, CLIENT_MOD, SERVER_FALLBACK.");
            return true;
        }
        configService.setVisualMode(mode);
        sender.sendMessage(message("visual_mode_set", configService.visualMode().name()));
        database.auditAsync(sender.getName(), "visual_mode", configService.visualMode().name());
        return true;
    }

    private boolean handleVisualEffect(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "copimine.narcotics.visuals")) {
            sender.sendMessage(message("no_permission"));
            return true;
        }
        if (args.length < 3) {
            sendHelpV2(sender);
            return true;
        }
        String effectId = args[1].toUpperCase(Locale.ROOT);
        if (!configService.visualEffectIds().contains(effectId)) {
            sender.sendMessage(ChatColor.RED + "Неизвестный visual effect id.");
            return true;
        }
        boolean enabled = "on".equalsIgnoreCase(args[2]);
        configService.setVisualEffectEnabled(effectId, enabled);
        sender.sendMessage(message("visual_effect_set", effectId, enabled ? "ON" : "OFF"));
        database.auditAsync(sender.getName(), "visual_effect", effectId + "=" + enabled);
        return true;
    }

    private boolean handleSelfCheckV2(CommandSender sender) {
        if (!hasPermission(sender, "copimine.narcotics.selfcheck")) {
            sender.sendMessage(message("no_permission"));
            return true;
        }
        NarcoticsResourcePackAudit.Report report = resourcePackAudit.inspect();
        sender.sendMessage(message("selfcheck_ok"));
        sender.sendMessage(ChatColor.GRAY + "Client bridge enabled: " + configService.clientBridgeEnabled());
        sender.sendMessage(ChatColor.GRAY + "Client visuals allowed: " + configService.allowClientModVisuals());
        sender.sendMessage(ChatColor.GRAY + "Client ZIP shaderpack runtime supported: " + visualRuntime.supportsClientZipShaderpackRuntime());
        sender.sendMessage(ChatColor.GRAY + "Client ZIP shaderpack runtime reason: " + visualRuntime.clientShaderpackSupportReason());
        sender.sendMessage(ChatColor.GRAY + "Server overlay supported: " + visualRuntime.supportsServerOverlayRuntime());
        sender.sendMessage(ChatColor.GRAY + "Server fallback supported: " + visualRuntime.supportsServerParticleFallback());
        sender.sendMessage(ChatColor.GRAY + "Client visual runtime supported: " + visualRuntime.supportsClientShaderLikeRuntime());
        sender.sendMessage(ChatColor.GRAY + "Client visual runtime reason: " + visualRuntime.clientShaderLikeSupportReason());
        sender.sendMessage(ChatColor.GRAY + "True shader runtime supported: " + visualRuntime.supportsShaderRuntime());
        sender.sendMessage(ChatColor.GRAY + "True shader runtime reason: " + visualRuntime.shaderSupportReason());
        sender.sendMessage(ChatColor.GRAY + "Предметов: " + configService.items().size());
        sender.sendMessage(ChatColor.GRAY + "Режим текстур: " + configService.textureMode().name());
        sender.sendMessage(ChatColor.GRAY + "Режим визуалов: " + configService.visualMode().name());
        sender.sendMessage(ChatColor.GRAY + "Визуалы включены: " + configService.visualsEnabled());
        sender.sendMessage(ChatColor.GRAY + "Кэш котлов: " + cauldronService.cachedStateCount());
        sender.sendMessage(ChatColor.GRAY + "Моделей предметов: " + report.itemModels().size());
        sender.sendMessage(ChatColor.GRAY + "Текстур предметов: " + report.itemTextures().size());
        sender.sendMessage(ChatColor.GRAY + "Overlay-ассетов: " + report.overlayTextures().size());
        sender.sendMessage(ChatColor.GRAY + "Shader-профилей: " + report.shaderProfiles().size());
        sender.sendMessage(ChatColor.GRAY + "Font manifest: " + report.fontManifestPresent());
        sender.sendMessage(ChatColor.GRAY + "Документ сторонних ассетов: " + report.thirdPartyDocPresent());
        sender.sendMessage(ChatColor.GRAY + "Документ лицензий: " + report.licensesDocPresent());
        sender.sendMessage(ChatColor.GRAY + "Без hotlink: " + report.noHotlinks());
        sender.sendMessage(ChatColor.GRAY + "Без runtime download: " + report.noRuntimeDownloads());
        sender.sendMessage(ChatColor.GRAY + "SHA1 pack: " + (report.zipSha1() == null ? "missing" : report.zipSha1()));
        sender.sendMessage(ChatColor.GRAY + "SHA1 синхронизирован: " + report.hashSynced());
        sender.sendMessage(report.ok() ? ChatColor.GREEN + "Проверка resource pack пройдена" : ChatColor.RED + "Проблема resource pack: " + report.summary());
        return true;
    }

    private boolean handleSelfCheck(CommandSender sender) {
        return handleSelfCheckV2(sender);
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "copimine.narcotics.admin")) {
            sender.sendMessage(message("no_permission"));
            return true;
        }
        if (args.length < 2) {
            sendHelpV2(sender);
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(message("player_not_found"));
            return true;
        }
        OverdoseService.PlayerState state = overdoseService.state(target.getUniqueId());
        sender.sendMessage(ChatColor.GRAY + "Игрок: " + target.getName());
        sender.sendMessage(ChatColor.GRAY + "Scale: " + state.currentScale());
        sender.sendMessage(ChatColor.GRAY + "Last item: " + state.lastItemId());
        sender.sendMessage(ChatColor.GRAY + "Overdose until: " + state.overdoseUntil());
        sender.sendMessage(ChatColor.GRAY + "Invert until: " + state.invertedMovementUntil());
        sender.sendMessage(ChatColor.GRAY + "Visual: " + visualRuntime.sessionSummary(target.getUniqueId()));
        sender.sendMessage(ChatColor.GRAY + "Client: " + clientBridge.statusFor(target));
        return true;
    }

    private boolean handleSetWeight(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "copimine.narcotics.admin")) {
            sender.sendMessage(message("no_permission"));
            return true;
        }
        if (args.length < 3) {
            sendHelpV2(sender);
            return true;
        }
        String itemId = args[1].toLowerCase(Locale.ROOT);
        if (!configService.itemIds().contains(itemId)) {
            sender.sendMessage(message("unknown_item"));
            return true;
        }
        Integer weight = parseBoundedInt(sender, args[2], "weight", 1, 1000, false);
        if (weight == null) {
            return true;
        }
        configService.setOverdoseWeight(itemId, weight);
        recipeService.reload(configService);
        itemFactory.reload(configService);
        overdoseService.reload(configService);
        sender.sendMessage(ChatColor.GREEN + "Вес обновлён: " + itemId);
        database.auditAsync(sender.getName(), "setweight", itemId + "=" + weight);
        return true;
    }

    private boolean handleSetThreshold(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "copimine.narcotics.admin")) {
            sender.sendMessage(message("no_permission"));
            return true;
        }
        if (args.length < 2) {
            sendHelpV2(sender);
            return true;
        }
        Integer threshold = parseBoundedInt(sender, args[1], "threshold", 1, 10000, false);
        if (threshold == null) {
            return true;
        }
        configService.setOverdoseThreshold(threshold);
        overdoseService.reload(configService);
        sender.sendMessage(ChatColor.GREEN + "Порог обновлён: " + configService.overdoseThreshold());
        database.auditAsync(sender.getName(), "setthreshold", String.valueOf(configService.overdoseThreshold()));
        return true;
    }

    private boolean handleSetWindow(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "copimine.narcotics.admin")) {
            sender.sendMessage(message("no_permission"));
            return true;
        }
        if (args.length < 2) {
            sendHelpV2(sender);
            return true;
        }
        Integer window = parseBoundedInt(sender, args[1], "window", 60, 86400, false);
        if (window == null) {
            return true;
        }
        configService.setUsageWindowSeconds(window);
        overdoseService.reload(configService);
        sender.sendMessage(ChatColor.GREEN + "Окно обновлено: " + configService.usageWindowSeconds());
        database.auditAsync(sender.getName(), "setwindow", String.valueOf(configService.usageWindowSeconds()));
        return true;
    }

    private boolean handleSetDuration(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "copimine.narcotics.admin")) {
            sender.sendMessage(message("no_permission"));
            return true;
        }
        if (args.length < 2) {
            sendHelpV2(sender);
            return true;
        }
        Integer duration = parseBoundedInt(sender, args[1], "duration", 10, 3600, true);
        if (duration == null) {
            return true;
        }
        configService.setDurationOverrideSeconds(duration);
        overdoseService.reload(configService);
        sender.sendMessage(ChatColor.GREEN + "Override-длительность: " + configService.durationOverrideSeconds());
        database.auditAsync(sender.getName(), "setduration", String.valueOf(configService.durationOverrideSeconds()));
        return true;
    }

    private boolean isBlockedDestination(Inventory inventory) {
        if (inventory == null || !configService.blockProcessingInventories()) {
            return false;
        }
        return switch (inventory.getType()) {
            case CRAFTING, WORKBENCH, CRAFTER, FURNACE, BLAST_FURNACE, SMOKER, BREWING, SMITHING,
                    ANVIL, GRINDSTONE, STONECUTTER, HOPPER, DROPPER, DISPENSER -> true;
            default -> false;
        };
    }

    private boolean shouldBlockInventoryClick(InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        Inventory clicked = event.getClickedInventory();
        InventoryView view = event.getView();
        Inventory top = view == null ? null : view.getTopInventory();
        boolean topBlocked = isBlockedDestination(top);
        boolean clickedBlocked = isBlockedDestination(clicked);
        boolean cursorOfficial = itemFactory.isOfficialFinishedItem(cursor);
        boolean currentOfficial = itemFactory.isOfficialFinishedItem(current);
        boolean hotbarOfficial = event.getHotbarButton() >= 0
                && itemFactory.isOfficialFinishedItem(event.getWhoClicked().getInventory().getItem(event.getHotbarButton()));
        boolean offhandOfficial = itemFactory.isOfficialFinishedItem(event.getWhoClicked().getInventory().getItemInOffHand());
        InventoryAction action = event.getAction();
        ClickType click = event.getClick();

        if (!cursorOfficial && !currentOfficial && !hotbarOfficial && !offhandOfficial) {
            return false;
        }

        if (clickedBlocked && clicked == top) {
            if (currentOfficial && isRecoveryExtraction(click, action, cursor)) {
                return false;
            }
            return cursorOfficial || hotbarOfficial || offhandOfficial || currentOfficial;
        }

        if (topBlocked && clicked == event.getWhoClicked().getInventory()) {
            if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY && currentOfficial) {
                return true;
            }
            if (action == InventoryAction.COLLECT_TO_CURSOR && cursorOfficial) {
                return true;
            }
            if ((click == ClickType.NUMBER_KEY || action == InventoryAction.HOTBAR_SWAP || action == InventoryAction.HOTBAR_MOVE_AND_READD) && hotbarOfficial) {
                return true;
            }
            if (click == ClickType.SWAP_OFFHAND && offhandOfficial) {
                return true;
            }
            if (click.isShiftClick() && currentOfficial) {
                return true;
            }
            if (cursorOfficial && event.getRawSlot() >= 0 && top != null && event.getRawSlot() < top.getSize()) {
                return true;
            }
        }
        return false;
    }

    private boolean isRecoveryExtraction(ClickType click, InventoryAction action, ItemStack cursor) {
        if (cursor != null && cursor.getType() != Material.AIR) {
            return false;
        }
        return switch (action) {
            case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME, MOVE_TO_OTHER_INVENTORY -> true;
            default -> click.isLeftClick() || click.isRightClick() || click.isShiftClick();
        };
    }

    private void sendBlocked(Player player) {
        player.sendMessage(message("processing_blocked"));
    }

    private void sendHelpV2(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics recover");
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics give <игрок> <item|all>");
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics reload");
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics reset confirm");
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics clearoverdose <игрок>");
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics info <игрок>");
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics setweight <id> <value>");
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics setthreshold <value>");
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics setwindow <seconds>");
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics setduration <seconds>");
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics texture mode <vanilla|custom>");
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics texture migrate <online|nearby>");
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics visuals status");
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics visuals enable <effectId|all>");
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics visuals disable <effectId|all>");
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics visuals mode <auto|client_mod|server_fallback>");
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics visuals test <игрок> <effectId|narcoticId|overdose> [seconds]");
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics selfcheck");
        sender.sendMessage(ChatColor.GOLD + "/cmclient check <игрок>");
        sender.sendMessage(ChatColor.GOLD + "/cmclient visualtest <игрок> <effectId> [seconds]");
        sender.sendMessage(ChatColor.GOLD + "/cmclient fallbacktest <игрок> <effectId> [seconds]");
        sender.sendMessage(ChatColor.GOLD + "/cmclient require client <true|false>");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics recover");
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics give <игрок> <item|all>");
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics reload");
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics reset confirm");
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics clearoverdose <игрок>");
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics info <игрок>");
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics setweight <id> <value>");
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics setthreshold <value>");
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics setwindow <seconds>");
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics setduration <seconds>");
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics texture mode <vanilla|custom>");
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics texture migrate <online|nearby>");
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics visuals status");
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics visuals enable <effectId|all>");
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics visuals disable <effectId|all>");
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics visuals mode <auto|client_mod|server_fallback>");
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics visuals test <игрок> <effectId|narcoticId|overdose> [seconds]");
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics selfcheck");
        sender.sendMessage(ChatColor.GOLD + "/cmclient check <игрок>");
        sender.sendMessage(ChatColor.GOLD + "/cmclient visualtest <игрок> <effectId> [seconds]");
        sender.sendMessage(ChatColor.GOLD + "/cmclient fallbacktest <игрок> <effectId> [seconds]");
        sender.sendMessage(ChatColor.GOLD + "/cmclient require client <true|false>");
    }

    private String message(String key, String... args) {
        return configService.message(key, args);
    }

    private boolean hasPermission(CommandSender sender, String permission) {
        return sender.hasPermission(permission) || sender.hasPermission("copimine.narcotics.admin");
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(ChatColor.RED + "Эта команда доступна только игроку.");
        return null;
    }

    private int deliverOfficialItem(Player target, NarcoticDefinition definition) {
        Map<Integer, ItemStack> leftovers = target.getInventory().addItem(itemFactory.createOfficialItem(definition, 1));
        if (leftovers.isEmpty()) {
            return 0;
        }
        for (ItemStack leftover : leftovers.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), leftover);
        }
        target.updateInventory();
        return leftovers.size();
    }

    private Integer parseBoundedInt(CommandSender sender, String raw, String label, int min, int max, boolean allowZero) {
        int parsed;
        try {
            parsed = Integer.parseInt(raw);
        } catch (NumberFormatException parseError) {
            sender.sendMessage(ChatColor.RED + "Некорректное число для " + label + ".");
            return null;
        }
        if (allowZero && parsed == 0) {
            return 0;
        }
        if (parsed < min || parsed > max) {
            sender.sendMessage(ChatColor.RED + "Значение " + label + " должно быть в диапазоне " + min + ".." + max + (allowZero ? " или 0." : "."));
            return null;
        }
        return parsed;
    }

    private boolean isUnsafeConsumeTarget(Block clickedBlock) {
        if (clickedBlock == null) {
            return false;
        }
        if (clickedBlock.getState() instanceof Container) {
            return true;
        }
        Material type = clickedBlock.getType();
        return type.isInteractable()
                || type == Material.CAULDRON
                || type == Material.WATER_CAULDRON;
    }

    private int migrateNearbyStorageInventories(Player player, int radius) {
        Set<String> visited = new HashSet<>();
        int updated = 0;
        Block origin = player.getLocation().getBlock();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockState state = origin.getRelative(dx, dy, dz).getState();
                    if (!(state instanceof Container container)) {
                        continue;
                    }
                    String key = state.getWorld().getName() + ":" + state.getX() + ":" + state.getY() + ":" + state.getZ();
                    if (!visited.add(key)) {
                        continue;
                    }
                    updated += itemFactory.migrateStorageInventory(container.getInventory());
                }
            }
        }
        return updated;
    }

    private List<String> prefix(List<String> values, String raw) {
        String needle = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(needle)) {
                out.add(value);
            }
        }
        return out;
    }
}
