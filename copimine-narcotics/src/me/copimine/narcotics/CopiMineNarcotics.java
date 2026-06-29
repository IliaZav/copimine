package me.copimine.narcotics;

import me.copimine.clientbridge.CopiMineClientBridge;
import me.copimine.narcotics.cauldron.CauldronBrewingService;
import me.copimine.narcotics.config.NarcoticsConfigService;
import me.copimine.narcotics.db.NarcoticsDatabase;
import me.copimine.narcotics.item.NarcoticItemFactory;
import me.copimine.narcotics.model.NarcoticDefinition;
import me.copimine.narcotics.recipe.NarcoticsRecipeService;
import me.copimine.narcotics.resourcepack.NarcoticsResourcePackAudit;
import me.copimine.narcotics.use.OverdoseService;
import me.copimine.visualruntime.VisualRuntimeService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CopiMineNarcotics extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static final Set<String> VALID_TEXTURE_MODES = Set.of("VANILLA", "CUSTOM");
    private static final Set<String> VALID_VISUAL_MODES = Set.of("AUTO", "CLIENT_MOD", "SERVER_OVERLAY", "SERVER_FALLBACK");

    private final ConcurrentHashMap<UUID, Long> consumeCooldownUntil = new ConcurrentHashMap<>();
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
        saveResource("config.yml", false);
        configService = new NarcoticsConfigService(this);
        configService.reload();
        database = new NarcoticsDatabase(this, configService);
        database.start();
        itemFactory = new NarcoticItemFactory(this, configService);
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
        getLogger().info("CopiMineNarcotics with optional CopiMineClient bridge enabled.");
    }

    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            overdoseService.clearActiveEffects(player, true);
            visualRuntime.clear(player);
        }
        consumeCooldownUntil.clear();
        if (clientBridge != null) {
            clientBridge.shutdown();
        }
        cauldronService.shutdown();
        database.shutdown();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() == null || event.getHand().name().contains("OFF_HAND")) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack inHand = player.getInventory().getItemInMainHand();
        NarcoticDefinition official = itemFactory.resolveOfficial(inHand);
        if (resetInProgress) {
            boolean brewingAttempt = event.getAction() == Action.RIGHT_CLICK_BLOCK
                    && event.getClickedBlock() != null
                    && cauldronService.isSupportedCauldron(event.getClickedBlock())
                    && recipeService.isRecognizedIngredient(inHand);
            if (official != null || brewingAttempt) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.YELLOW + "Наркотики временно недоступны: идёт сброс состояния.");
            }
            return;
        }

        if (official != null) {
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);
            }
            if (canConsumeOfficialOnInteract(event)) {
                event.setCancelled(true);
                long now = Instant.now().getEpochSecond();
                long cooldownUntil = consumeCooldownUntil.getOrDefault(player.getUniqueId(), 0L);
                if (cooldownUntil > now) {
                    player.sendMessage(message("consume_cooldown", String.valueOf(cooldownUntil - now)));
                    return;
                }
                consumeCooldownUntil.put(player.getUniqueId(), now + configService.consumeCooldownSeconds());
                itemFactory.consumeOne(player, inHand);
                overdoseService.consume(player, official);
            }
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Block block = event.getClickedBlock();
        if ((block.getType() == Material.CAULDRON || block.getType() == Material.WATER_CAULDRON) && !cauldronService.isSupportedCauldron(block)) {
            if (recipeService.isRecognizedIngredient(inHand)) {
                event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
                event.setCancelled(true);
                player.sendMessage(ChatColor.YELLOW + "Для варки нужен полный котёл воды.");
            }
            return;
        }
        if (!cauldronService.isSupportedCauldron(block)) {
            return;
        }
        if (!recipeService.isRecognizedIngredient(inHand)) {
            return;
        }
        if (cauldronService.tryAddIngredient(player, block, inHand)) {
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            event.setCancelled(true);
        }
    }

    private boolean canConsumeOfficialOnInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_AIR) {
            return true;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return false;
        }
        return !isUnsafeConsumeTarget(event.getClickedBlock());
    }

    private boolean isUnsafeConsumeTarget(Block block) {
        if (block == null) {
            return false;
        }
        if (cauldronService.isSupportedCauldron(block)) {
            return true;
        }
        Material type = block.getType();
        if (type == Material.WATER_CAULDRON || type == Material.CAULDRON) {
            return true;
        }
        return block.getState() instanceof InventoryHolder || type.isInteractable();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        cauldronService.handleCauldronBroken(event.getBlock(), event.getPlayer().getLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void onCauldronLevelChange(CauldronLevelChangeEvent event) {
        cauldronService.handleCauldronLevelChange(event.getBlock(), event.getNewState().getBlockData());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (shouldBlockInventoryClick(event)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                sendBlocked(player);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!itemFactory.isOfficialFinishedItem(event.getOldCursor())) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (isBlockedDestination(top) && event.getRawSlots().stream().anyMatch(slot -> slot >= 0 && slot < top.getSize())) {
            event.setCancelled(true);
            sendBlocked((Player) event.getWhoClicked());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (!itemFactory.isOfficialFinishedItem(event.getItem())) {
            return;
        }
        if (isBlockedDestination(event.getDestination()) || isBlockedDestination(event.getSource())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryPickup(InventoryPickupItemEvent event) {
        if (itemFactory.isOfficialFinishedItem(event.getItem().getItemStack()) && isBlockedDestination(event.getInventory())) {
            event.setCancelled(true);
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
        overdoseService.preloadState(event.getPlayer().getUniqueId());
        visualRuntime.clearTracking(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        overdoseService.clearActiveEffects(event.getPlayer(), true);
        visualRuntime.clear(event.getPlayer());
        visualRuntime.clearTracking(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        overdoseService.clearActiveEffects(event.getEntity(), true);
        visualRuntime.clear(event.getEntity());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        overdoseService.clearActiveEffects(event.getPlayer(), true);
        visualRuntime.clear(event.getPlayer());
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
                    } catch (Exception ignored) {
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
                case "reload" -> handleReload(sender);
                case "reset-state", "reset" -> handleResetState(sender, args);
                case "clearoverdose" -> handleClearOverdoseV2(sender, args);
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
            sender.sendMessage(ChatColor.RED + "Не удалось выполнить команду.");
            return true;
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
            return prefix(List.of("give", "reload", "reset", "clearoverdose", "texture", "visuals", "selfcheck", "info", "setweight", "setthreshold", "setwindow", "setduration"), args[0]);
        }
        if (args.length == 2 && "give".equalsIgnoreCase(args[0])) {
            return prefix(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 3 && "give".equalsIgnoreCase(args[0])) {
            List<String> values = new ArrayList<>(configService.itemIds());
            values.add("all");
            return prefix(values, args[2]);
        }
        if (args.length == 2 && ("clearoverdose".equalsIgnoreCase(args[0]) || "info".equalsIgnoreCase(args[0]))) {
            return prefix(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
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
            return prefix(List.of("AUTO", "CLIENT_MOD", "SERVER_OVERLAY", "SERVER_FALLBACK"), args[2]);
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

    private boolean handleReload(CommandSender sender) {
        if (!hasPermission(sender, "copimine.narcotics.reload")) {
            sender.sendMessage(message("no_permission"));
            return true;
        }
        reloadConfig();
        configService.reload();
        recipeService.reload(configService);
        itemFactory.reload(configService);
        clientBridge.reload(configService);
        visualRuntime.reload(configService);
        overdoseService.reload(configService);
        cauldronService.reload(configService, recipeService, itemFactory);
        sender.sendMessage(message("reload_ok"));
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

    private boolean handleClearOverdoseV2(CommandSender sender, String[] args) {
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
        overdoseService.forceClearOverdose(target);
        database.auditAsync(sender.getName(), "clearoverdose", "target=" + target.getName());
        sender.sendMessage(ChatColor.GREEN + "Состояние очищено: " + target.getName());
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
            String effectId = args[3].toUpperCase(Locale.ROOT);
            if (!configService.visualEffectIds().contains(effectId)) {
                sender.sendMessage(ChatColor.RED + "Неизвестный visual effect id.");
                return true;
            }
            Integer seconds = parseBoundedInt(sender, args.length >= 5 ? args[4] : "30", "duration", 1, 600, false);
            if (seconds == null) {
                return true;
            }
            visualRuntime.apply(target, effectId, seconds, false);
            database.auditAsync(sender.getName(), "visual_test", target.getName() + "," + effectId + "," + seconds);
            sender.sendMessage(ChatColor.GREEN + "Тест визуала запущен для: " + target.getName());
            return true;
        }
        sendHelpV2(sender);
        return true;
    }

    private boolean handleClearOverdose(CommandSender sender, String[] args) {
        return handleClearOverdoseV2(sender, args);
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
            sender.sendMessage(ChatColor.RED + "Неверный visual mode. Доступно: AUTO, CLIENT_MOD, SERVER_OVERLAY, SERVER_FALLBACK.");
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
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics visuals mode <auto|client_mod|server_overlay|server_fallback>");
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics visuals test <игрок> <effectId> [seconds]");
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics selfcheck");
        sender.sendMessage(ChatColor.GOLD + "/cmclient check <игрок>");
        sender.sendMessage(ChatColor.GOLD + "/cmclient visualtest <игрок> <effectId> [seconds]");
        sender.sendMessage(ChatColor.GOLD + "/cmclient fallbacktest <игрок> <effectId> [seconds]");
        sender.sendMessage(ChatColor.GOLD + "/cmclient require client <true|false>");
    }

    private void sendHelp(CommandSender sender) {
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
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics visuals mode <auto|client_mod|server_overlay|server_fallback>");
        sender.sendMessage(ChatColor.GOLD + "/cmnarcotics visuals test <игрок> <effectId> [seconds]");
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
        } catch (Exception ignored) {
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

