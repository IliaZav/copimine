package me.copimine.worldcore;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public final class CopiMineWorldCore extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static final int MAX_SAFE_LOCATION_CHECKS = 256;
    private static final int MAX_NETHER_SAFE_Y = 120;
    private static final int MAX_EVACUATIONS_PER_TICK = 8;
    private static final long TRUSTED_TELEPORT_TTL_MILLIS = 5_000L;
    private WorldLimit overworldLimit;
    private WorldAccess netherAccess;
    private WorldAccess endAccess;
    private final Set<UUID> warnedOutside = new LinkedHashSet<>();
    private final Map<UUID, String> blockedWorldWarnings = new HashMap<>();
    private final Map<String, BorderSnapshot> savedBorders = new HashMap<>();
    /** Durable snapshot of vanilla borders replaced by this plugin. */
    private File savedBordersFile;
    private final Map<UUID, TeleportToken> trustedPluginTeleports = new ConcurrentHashMap<>();
    private final Set<UUID> redirectInFlight = ConcurrentHashMap.newKeySet();
    private final Set<UUID> evacuationQueued = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<EvacuationRequest> evacuationQueue = new ConcurrentLinkedQueue<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        reloadLocalConfig();
        savedBordersFile = new File(getDataFolder(), "border-snapshots.yml");
        loadSavedBorders();
        PluginCommand command = getCommand("cmworld");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::enforceWorldAccessAndBorders, 40L, 40L);
        applyOverworldBorder();
    }

    @Override
    public void onDisable() {
        restoreSavedBorders();
        warnedOutside.clear();
        blockedWorldWarnings.clear();
        trustedPluginTeleports.clear();
        redirectInFlight.clear();
    }

    public void openAdminWorldHub(Player player) {
        if (player == null || !player.hasPermission("copimine.world.admin")) {
            if (player != null) {
                player.sendMessage(color("&cНет прав."));
            }
            return;
        }
        MenuHolder holder = new MenuHolder("world-root");
        Inventory inventory = holder.create(27, color("&9&lМиры CopiMine"));
        button(holder, inventory, 10, Material.GRASS_BLOCK, "&aГраница мира", List.of(
                "&7Радиус: &f" + overworldLimit.radius() + " блоков",
                "&7Статус: " + (overworldLimit.enabled() ? "&aвключена" : "&cвыключена"),
                "&eЛКМ: статус",
                "&eПКМ: применить vanilla worldborder"
        ), "gui:border:status", "gui:border:apply");
        button(holder, inventory, 12, Material.NETHERRACK, "&cНижний мир", List.of(
                "&7Статус: " + (netherAccess.enabled() ? "&aоткрыт" : "&cзакрыт"),
                "&7Миры: &f" + String.join(", ", netherAccess.worldNames()),
                "&eЛКМ: открыть",
                "&eПКМ: закрыть"
        ), "gui:nether:open", "gui:nether:close");
        button(holder, inventory, 14, Material.END_STONE, "&5Энд", List.of(
                "&7Статус: " + (endAccess.enabled() ? "&aоткрыт" : "&cзакрыт"),
                "&7Миры: &f" + String.join(", ", endAccess.worldNames()),
                "&eЛКМ: открыть",
                "&eПКМ: закрыть"
        ), "gui:end:open", "gui:end:close");
        button(holder, inventory, 16, Material.COMPASS, "&bПроверка", List.of(
                "&7Перепроверить игроков, границы и закрытые миры."
        ), "gui:safecheck");
        button(holder, inventory, 22, Material.BOOK, "&eПодсказка", List.of(
                "&7Команды: &f/cmworld status",
                "&7/cmworld border set <radius> [confirm]",
                "&7/cmworld nether open|close",
                "&7/cmworld end open|close"
        ), "");
        player.openInventory(inventory);
    }

    /**
     * Explicit API for trusted server plugins.  A normal PLUGIN teleport never
     * inherits the command-teleport exemption; callers must opt in for exactly
     * one next teleport event.
     */
    public boolean teleportTrusted(Player player, Location target) {
        if (player == null || target == null || target.getWorld() == null || !player.isOnline()) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        TeleportToken token = new TeleportToken(uuid, UUID.randomUUID().toString(),
                target.getWorld().getName(), System.currentTimeMillis() + TRUSTED_TELEPORT_TTL_MILLIS);
        trustedPluginTeleports.put(uuid, token);
        boolean result = player.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN);
        if (!result) {
            trustedPluginTeleports.remove(uuid, token);
        }
        return result;
    }

    private void openWorldCloseConfirmMenu(Player player, boolean nether, int playersInside) {
        String worldName = nether ? "Нижний мир" : "Энд";
        String applyAction = nether ? "gui:confirm:close:nether" : "gui:confirm:close:end";
        MenuHolder holder = new MenuHolder("world-close-confirm");
        Inventory inventory = holder.create(27, color("&cПодтвердить закрытие"));
        button(holder, inventory, 11, Material.RED_WOOL, "&cПодтвердить закрытие", List.of(
                "&7Мир: &f" + worldName,
                "&7Игроков внутри: &f" + playersInside,
                "&7После подтверждения игроки будут выведены в безопасный мир."
        ), applyAction);
        button(holder, inventory, 15, Material.ARROW, "&aОтмена", List.of(
                "&7Вернуться в меню миров."
        ), "gui:close-confirm:cancel");
        player.openInventory(inventory);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("copimine.world.admin")) {
            sender.sendMessage(color("&cНет прав."));
            return true;
        }
        if (args.length == 0 || "status".equalsIgnoreCase(args[0])) {
            sendStatus(sender);
            return true;
        }
        try {
            return handleCommand(sender, args);
        } catch (Exception error) {
            sender.sendMessage(color("&cНе удалось выполнить команду. Подробности записаны в лог."));
            getLogger().warning("cmworld failed: " + error.getMessage());
            return true;
        }
    }

    private boolean handleCommand(CommandSender sender, String[] args) {
        if ("reload".equalsIgnoreCase(args[0])) {
            reloadConfig();
            reloadLocalConfig();
            applyOverworldBorder();
            sender.sendMessage(color("&aКонфигурация CopiMineWorldCore перезагружена."));
            return true;
        }
        if ("safecheck".equalsIgnoreCase(args[0])) {
            enforceWorldAccessAndBorders();
            sender.sendMessage(color("&aПроверка запущена; перемещения игроков поставлены в очередь и будут выполнены постепенно."));
            return true;
        }
        if ("border".equalsIgnoreCase(args[0])) {
            return handleBorderCommand(sender, args);
        }
        if ("nether".equalsIgnoreCase(args[0]) || "end".equalsIgnoreCase(args[0])) {
            return handleWorldToggleCommand(sender, args);
        }
        sendHelp(sender);
        return true;
    }

    private boolean handleBorderCommand(CommandSender sender, String[] args) {
        if (args.length == 1 || "status".equalsIgnoreCase(args[1])) {
            sender.sendMessage(color("&7Граница мира: &f" + overworldLimit.radius() + " &7блоков, enabled=&f" + overworldLimit.enabled()));
            return true;
        }
        if ("apply".equalsIgnoreCase(args[1])) {
            OperationResult result = applyOverworldBorder();
            sender.sendMessage(color((result.success() ? "&a" : "&c") + result.message()));
            return true;
        }
        if ("set".equalsIgnoreCase(args[1]) && args.length >= 3) {
            int radius = parseInt(args[2], -1);
            if (radius < 1000 || radius > 100000) {
                sender.sendMessage(color("&cРадиус должен быть в диапазоне 1000..100000."));
                return true;
            }
            if (playersOutside(radius) > 0 && (args.length < 4 || !"confirm".equalsIgnoreCase(args[3]))) {
                sender.sendMessage(color("&eЧасть игроков окажется за границей. Повтори: &f/cmworld border set " + radius + " confirm"));
                return true;
            }
            getConfig().set("world_limits.overworld.radius", radius);
            saveConfig();
            reloadLocalConfig();
            applyOverworldBorder();
            sender.sendMessage(color("&aНовый радиус границы: &f" + radius));
            return true;
        }
        sendHelp(sender);
        return true;
    }

    private boolean handleWorldToggleCommand(CommandSender sender, String[] args) {
        boolean isNether = "nether".equalsIgnoreCase(args[0]);
        WorldAccess access = isNether ? netherAccess : endAccess;
        String path = isNether ? "world_access.nether.enabled" : "world_access.end.enabled";
        String title = isNether ? "Нижний мир" : "Энд";
        if (args.length == 1 || "status".equalsIgnoreCase(args[1])) {
            sender.sendMessage(color("&7" + title + ": &f" + (access.enabled() ? "открыт" : "закрыт")));
            return true;
        }
        if ("open".equalsIgnoreCase(args[1])) {
            OperationResult result = setWorldState(isNether, true);
            sender.sendMessage(color((result.success() ? "&a" : "&c") + result.message()));
            return true;
        }
        if ("close".equalsIgnoreCase(args[1])) {
            int inside = playersInside(access);
            if (inside > 0 && (args.length < 3 || !"confirm".equalsIgnoreCase(args[2]))) {
                sender.sendMessage(color("&eВ мире есть игроки. Повтори: &f/cmworld " + args[0] + " close confirm"));
                return true;
            }
            OperationResult result = setWorldState(isNether, false);
            sender.sendMessage(color((result.success() ? "&a" : "&c") + result.message()));
            return true;
        }
        sendHelp(sender);
        return true;
    }

    private OperationResult setWorldState(boolean nether, boolean enabled) {
        String path = nether ? "world_access.nether.enabled" : "world_access.end.enabled";
        String title = nether ? "Нижний мир" : "Энд";
        WorldAccess previous = nether ? netherAccess : endAccess;
        try {
            getConfig().set(path, enabled);
            saveConfig();
            reloadLocalConfig();
            if (!enabled) {
                evacuatePlayers(previous, color("&e" + title + " сейчас закрыт."));
            }
            return new OperationResult(true, title + (enabled ? " открыт." : " закрыт."));
        } catch (Exception error) {
            getLogger().log(java.util.logging.Level.WARNING, "WorldCore failed to change world state", error);
            return new OperationResult(false, "Не удалось изменить состояние мира.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("copimine.world.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return prefix(List.of("status", "border", "nether", "end", "reload", "safecheck"), args[0]);
        }
        if (args.length == 2 && "border".equalsIgnoreCase(args[0])) {
            return prefix(List.of("status", "set", "apply"), args[1]);
        }
        if (args.length == 2 && ("nether".equalsIgnoreCase(args[0]) || "end".equalsIgnoreCase(args[0]))) {
            return prefix(List.of("status", "open", "close"), args[1]);
        }
        if (args.length == 4 && "border".equalsIgnoreCase(args[0]) && "set".equalsIgnoreCase(args[1])) {
            return prefix(List.of("confirm"), args[3]);
        }
        if (args.length == 3 && ("nether".equalsIgnoreCase(args[0]) || "end".equalsIgnoreCase(args[0])) && "close".equalsIgnoreCase(args[1])) {
            return prefix(List.of("confirm"), args[2]);
        }
        return List.of();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPortal(PlayerPortalEvent event) {
        if (event.isCancelled()) {
            return;
        }
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL && !endAccess.enabled() && !endAccess.allowPortals()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(color("&eЭнд сейчас закрыт."));
            return;
        }
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL && !netherAccess.enabled() && !netherAccess.allowPortals()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(color("&eНижний мир сейчас закрыт."));
            return;
        }
        World targetWorld = event.getTo() == null ? null : event.getTo().getWorld();
        if (targetWorld == null) {
            return;
        }
        if (isBlockedWorld(targetWorld, true)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(blockedMessage(targetWorld));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.isCancelled()) {
            if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) {
                trustedPluginTeleports.remove(event.getPlayer().getUniqueId());
            }
            return;
        }
        if (event.getTo() == null || event.getTo().getWorld() == null) {
            return;
        }
        World targetWorld = event.getTo().getWorld();
        boolean portalTeleport = switch (event.getCause()) {
            case NETHER_PORTAL, END_PORTAL, END_GATEWAY -> true;
            default -> false;
        };
        boolean commandTeleport = event.getCause() == PlayerTeleportEvent.TeleportCause.COMMAND;
        boolean pluginTeleport = event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN;
        TeleportToken trustedToken = pluginTeleport ? trustedPluginTeleports.get(event.getPlayer().getUniqueId()) : null;
        boolean trustedPluginTeleport = trustedToken != null
                && trustedToken.playerUuid().equals(event.getPlayer().getUniqueId())
                && trustedToken.targetWorld().equals(targetWorld.getName())
                && trustedToken.expiresAtMillis() >= System.currentTimeMillis();
        if (pluginTeleport && trustedToken != null) {
            trustedPluginTeleports.remove(event.getPlayer().getUniqueId(), trustedToken);
        }
        WorldAccess targetAccess = accessFor(targetWorld);
        if (commandTeleport && targetAccess != null && !targetAccess.enabled() && targetAccess.allowCommandsTeleport()) {
            return;
        }
        if (pluginTeleport && targetAccess != null && !targetAccess.enabled() && trustedPluginTeleport) {
            return;
        }
        if (isBlockedWorld(targetWorld, portalTeleport)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(blockedMessage(targetWorld));
            return;
        }
        if (overworldLimit.enabled()
                && overworldLimit.worldNames().contains(targetWorld.getName())
                && overworldLimit.blockTeleportOutside()
                && isOutsideLimit(event.getTo(), overworldLimit)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(color("&eТелепорт за границу мира запрещён."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null || to.getWorld() == null) {
            return;
        }
        WorldAccess blockedAccess = accessFor(to.getWorld());
        if (blockedAccess != null && !blockedAccess.enabled()) {
            redirectPlayer(event.getPlayer(), accessFor(to.getWorld()), blockedMessage(to.getWorld()));
            warnedOutside.remove(event.getPlayer().getUniqueId());
            return;
        }
        blockedWorldWarnings.remove(event.getPlayer().getUniqueId());
        if (!overworldLimit.enabled() || !overworldLimit.worldNames().contains(to.getWorld().getName())) {
            warnedOutside.remove(event.getPlayer().getUniqueId());
            return;
        }
        double distance = distanceFromCenter(to, overworldLimit);
        if (distance > overworldLimit.radius()) {
            if (overworldLimit.correctPlayersOutside()) {
                Location safe = clampToBorder(to, overworldLimit);
                if (safe != null) {
                    event.setTo(safe);
                } else {
                    event.setCancelled(true);
                    getLogger().warning("WorldCore could not find a safe border clamp location for " + event.getPlayer().getName());
                }
                event.getPlayer().sendMessage(color("&eНельзя выходить за границу мира."));
            }
            warnedOutside.remove(event.getPlayer().getUniqueId());
            return;
        }
        if (distance >= warningThreshold(overworldLimit)) {
            if (warnedOutside.add(event.getPlayer().getUniqueId())) {
                event.getPlayer().sendMessage(color("&eГраница мира рядом."));
            }
        } else {
            warnedOutside.remove(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        World current = event.getPlayer().getWorld();
        WorldAccess blockedAccess = accessFor(current);
        if (blockedAccess != null && !blockedAccess.enabled()) {
            redirectPlayer(event.getPlayer(), blockedAccess, blockedMessage(current));
            return;
        }
        blockedWorldWarnings.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        warnedOutside.remove(event.getPlayer().getUniqueId());
        blockedWorldWarnings.remove(event.getPlayer().getUniqueId());
        redirectInFlight.remove(event.getPlayer().getUniqueId());
        trustedPluginTeleports.remove(event.getPlayer().getUniqueId());
        evacuationQueued.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!player.hasPermission("copimine.world.admin")) {
            player.closeInventory();
            player.sendMessage(color("&cНет прав."));
            return;
        }
        String action = event.isRightClick() ? holder.rightActions.get(event.getRawSlot()) : holder.actions.get(event.getRawSlot());
        if (action == null || action.isBlank()) {
            return;
        }
        switch (action) {
            case "gui:border:status" -> player.sendMessage(color("&7Текущий радиус границы: &f" + overworldLimit.radius()));
            case "gui:border:apply" -> {
                OperationResult result = applyOverworldBorder();
                player.sendMessage(color((result.success() ? "&a" : "&c") + result.message()));
            }
            case "gui:nether:open" -> {
                OperationResult result = setWorldState(true, true);
                if (!result.success()) {
                    player.sendMessage(color("&c" + result.message()));
                    return;
                }
                player.sendMessage(color("&aНижний мир открыт."));
                openAdminWorldHub(player);
            }
            case "gui:nether:close" -> {
                if (playersInside(netherAccess) > 0) {
                    openWorldCloseConfirmMenu(player, true, playersInside(netherAccess));
                } else {
                    OperationResult result = setWorldState(true, false);
                    if (!result.success()) {
                        player.sendMessage(color("&c" + result.message()));
                        return;
                    }
                    player.sendMessage(color("&aНижний мир закрыт."));
                    openAdminWorldHub(player);
                }
            }
            case "gui:end:open" -> {
                OperationResult result = setWorldState(false, true);
                if (!result.success()) {
                    player.sendMessage(color("&c" + result.message()));
                    return;
                }
                player.sendMessage(color("&aЭнд открыт."));
                openAdminWorldHub(player);
            }
            case "gui:end:close" -> {
                if (playersInside(endAccess) > 0) {
                    openWorldCloseConfirmMenu(player, false, playersInside(endAccess));
                } else {
                    OperationResult result = setWorldState(false, false);
                    if (!result.success()) {
                        player.sendMessage(color("&c" + result.message()));
                        return;
                    }
                    player.sendMessage(color("&aЭнд закрыт."));
                    openAdminWorldHub(player);
                }
            }
            case "gui:confirm:close:nether" -> {
                OperationResult result = setWorldState(true, false);
                if (!result.success()) {
                    player.sendMessage(color("&c" + result.message()));
                    return;
                }
                player.sendMessage(color("&aНижний мир закрыт."));
                openAdminWorldHub(player);
            }
            case "gui:confirm:close:end" -> {
                OperationResult result = setWorldState(false, false);
                if (!result.success()) {
                    player.sendMessage(color("&c" + result.message()));
                    return;
                }
                player.sendMessage(color("&aЭнд закрыт."));
                openAdminWorldHub(player);
            }
            case "gui:close-confirm:cancel" -> openAdminWorldHub(player);
            case "gui:safecheck" -> {
                enforceWorldAccessAndBorders();
                player.sendMessage(color("&aПроверка завершена."));
            }
            default -> {
            }
        }
    }

    private void dispatchConsole(String command) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    private void enforceWorldAccessAndBorders() {
        processEvacuationQueue();
        for (Player player : Bukkit.getOnlinePlayers()) {
            World world = player.getWorld();
            WorldAccess blockedAccess = accessFor(world);
            if (blockedAccess != null && !blockedAccess.enabled()) {
                enqueueEvacuation(player, blockedAccess, blockedMessage(world));
                continue;
            }
            blockedWorldWarnings.remove(player.getUniqueId());
            if (overworldLimit.enabled()
                    && overworldLimit.worldNames().contains(world.getName())
                    && isOutsideLimit(player.getLocation(), overworldLimit)) {
                Location safe = clampToBorder(player.getLocation(), overworldLimit);
                if (safe != null) {
                    if (!player.teleport(safe)) {
                        getLogger().warning("WorldCore could not clamp " + player.getName() + " back inside the border.");
                        player.sendMessage(color("&cНе удалось вернуть вас в безопасную зону границы."));
                    }
                } else {
                    getLogger().warning("WorldCore could not safely clamp " + player.getName() + " back inside the border.");
                }
            }
        }
    }

    private void reloadLocalConfig() {
        FileConfiguration cfg = getConfig();
        overworldLimit = new WorldLimit(
                cfg.getBoolean("world_limits.overworld.enabled", true),
                resolveOverworldWorldNames(cfg),
                cfg.getInt("world_limits.overworld.radius", 10000),
                cfg.getBoolean("world_limits.overworld.use_vanilla_worldborder", true),
                cfg.getBoolean("world_limits.overworld.block_teleport_outside", true),
                cfg.getBoolean("world_limits.overworld.correct_players_outside", true),
                cfg.getInt("world_limits.overworld.warning_distance", 64)
        );
        netherAccess = loadAccess(cfg, "world_access.nether", Set.of("world_nether"));
        endAccess = loadAccess(cfg, "world_access.end", Set.of("world_the_end"));
    }

    private LinkedHashSet<String> resolveOverworldWorldNames(FileConfiguration cfg) {
        LinkedHashSet<String> names = new LinkedHashSet<>(cfg.getStringList("world_limits.overworld.world_names"));
        if (!names.isEmpty()) {
            return names;
        }
        names.add("world");
        if (Bukkit.getWorld("world") != null) {
            return names;
        }
        names.clear();
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL) {
                names.add(world.getName());
                return names;
            }
        }
        if (!Bukkit.getWorlds().isEmpty()) {
            names.add(Bukkit.getWorlds().getFirst().getName());
        }
        return names;
    }

    private WorldAccess loadAccess(FileConfiguration cfg, String path, Set<String> defaults) {
        List<String> names = cfg.getStringList(path + ".world_names");
        if (names.isEmpty()) {
            names = new ArrayList<>(defaults);
        }
        return new WorldAccess(
                cfg.getBoolean(path + ".enabled", false),
                new LinkedHashSet<>(names),
                cfg.getBoolean(path + ".allow_portals", false),
                cfg.getBoolean(path + ".allow_commands_teleport", false),
                cfg.getString(path + ".redirect_world", "world"),
                cfg.getBoolean(path + ".redirect_to_safe_spawn", true)
        );
    }

    private OperationResult applyOverworldBorder() {
        if (!overworldLimit.enabled() || !overworldLimit.useVanillaWorldBorder()) {
            restoreSavedBorders();
            return new OperationResult(true, "Vanilla worldborder restored to its previous settings.");
        }
        int applied = 0;
        for (String worldName : overworldLimit.worldNames()) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                continue;
            }
            WorldBorder border = world.getWorldBorder();
            savedBorders.computeIfAbsent(worldName, ignored -> BorderSnapshot.capture(border));
            Location center = world.getSpawnLocation();
            border.setCenter(center.getX(), center.getZ());
            border.setSize(overworldLimit.radius() * 2.0D);
            applied++;
        }
        persistSavedBorders();
        if (applied == 0) {
            return new OperationResult(false, "No configured overworld worlds are loaded.");
        }
        return new OperationResult(true, "Vanilla worldborder applied to " + applied + " world(s).");
    }

    private void restoreSavedBorders() {
        for (Map.Entry<String, BorderSnapshot> entry : new ArrayList<>(savedBorders.entrySet())) {
            World world = Bukkit.getWorld(entry.getKey());
            if (world == null) {
                continue;
            }
            entry.getValue().restore(world.getWorldBorder());
            savedBorders.remove(entry.getKey());
        }
        if (savedBorders.isEmpty() && savedBordersFile != null && savedBordersFile.isFile()) {
            // The durable snapshot is no longer needed once every loaded
            // world has been restored.  A crash before onDisable can leave
            // this file behind; delete it only after the actual restore.
            if (!savedBordersFile.delete() && savedBordersFile.isFile()) {
                getLogger().warning("WorldCore could not remove restored border snapshot file.");
            }
        }
    }

    private void loadSavedBorders() {
        if (savedBordersFile == null || !savedBordersFile.isFile()) {
            return;
        }
        try {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(savedBordersFile);
            ConfigurationSection worlds = cfg.getConfigurationSection("worlds");
            if (worlds == null) {
                return;
            }
            for (String worldName : worlds.getKeys(false)) {
                String path = "worlds." + worldName;
                savedBorders.put(worldName, new BorderSnapshot(
                        cfg.getDouble(path + ".center_x"),
                        cfg.getDouble(path + ".center_z"),
                        cfg.getDouble(path + ".size", 59_999_968D),
                        cfg.getDouble(path + ".damage_buffer"),
                        cfg.getDouble(path + ".damage_amount"),
                        cfg.getInt(path + ".warning_distance"),
                        cfg.getInt(path + ".warning_time")));
            }
        } catch (RuntimeException error) {
            getLogger().warning("WorldCore could not read border snapshot: " + error.getMessage());
        }
    }

    private void persistSavedBorders() {
        if (savedBordersFile == null || savedBorders.isEmpty()) {
            return;
        }
        try {
            if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
                throw new IOException("could not create plugin data directory");
            }
            YamlConfiguration cfg = new YamlConfiguration();
            for (Map.Entry<String, BorderSnapshot> entry : savedBorders.entrySet()) {
                String path = "worlds." + entry.getKey();
                BorderSnapshot snap = entry.getValue();
                cfg.set(path + ".center_x", snap.centerX());
                cfg.set(path + ".center_z", snap.centerZ());
                cfg.set(path + ".size", snap.size());
                cfg.set(path + ".damage_buffer", snap.damageBuffer());
                cfg.set(path + ".damage_amount", snap.damageAmount());
                cfg.set(path + ".warning_distance", snap.warningDistance());
                cfg.set(path + ".warning_time", snap.warningTime());
            }
            Path targetPath = savedBordersFile.toPath();
            Path tmpPath = targetPath.resolveSibling(savedBordersFile.getName() + ".tmp");
            cfg.save(tmpPath.toFile());
            try (FileChannel channel = FileChannel.open(tmpPath, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(tmpPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(tmpPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | IllegalArgumentException error) {
            getLogger().warning("WorldCore could not persist border snapshot: " + error.getMessage());
        }
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(color("&6CopiMineWorldCore"));
        sender.sendMessage(color("&7Overworld border: &f" + overworldLimit.radius() + "&7, enabled=&f" + overworldLimit.enabled() + "&7, worlds=&f" + overworldLimit.worldNames()));
        sender.sendMessage(color("&7Nether: &f" + (netherAccess.enabled() ? "открыт" : "закрыт") + "&7, worlds=&f" + netherAccess.worldNames()));
        sender.sendMessage(color("&7End: &f" + (endAccess.enabled() ? "открыт" : "закрыт") + "&7, worlds=&f" + endAccess.worldNames()));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(color("&6/cmworld status"));
        sender.sendMessage(color("&6/cmworld border status"));
        sender.sendMessage(color("&6/cmworld border set <radius> [confirm]"));
        sender.sendMessage(color("&6/cmworld border apply"));
        sender.sendMessage(color("&6/cmworld nether open|close|status"));
        sender.sendMessage(color("&6/cmworld end open|close|status"));
        sender.sendMessage(color("&7Если игроки внутри: &f/cmworld nether close confirm"));
        sender.sendMessage(color("&7Если игроки внутри: &f/cmworld end close confirm"));
        sender.sendMessage(color("&6/cmworld reload"));
        sender.sendMessage(color("&6/cmworld safecheck"));
    }

    private int playersOutside(int radius) {
        WorldLimit probe = new WorldLimit(
                overworldLimit.enabled(),
                overworldLimit.worldNames(),
                radius,
                overworldLimit.useVanillaWorldBorder(),
                overworldLimit.blockTeleportOutside(),
                overworldLimit.correctPlayersOutside(),
                overworldLimit.warningDistance()
        );
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (probe.worldNames().contains(player.getWorld().getName()) && isOutsideLimit(player.getLocation(), probe)) {
                count++;
            }
        }
        return count;
    }

    private int playersInside(WorldAccess access) {
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (matchesAccessWorld(access, player.getWorld())) {
                count++;
            }
        }
        return count;
    }

    private void evacuatePlayers(WorldAccess access, String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (matchesAccessWorld(access, player.getWorld())) {
                enqueueEvacuation(player, access, message);
            }
        }
    }

    private void enqueueEvacuation(Player player, WorldAccess access, String message) {
        if (player == null || access == null || !player.isOnline()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (evacuationQueued.add(uuid)) {
            evacuationQueue.offer(new EvacuationRequest(uuid, access, message));
        }
    }

    private void processEvacuationQueue() {
        for (int processed = 0; processed < MAX_EVACUATIONS_PER_TICK; processed++) {
            EvacuationRequest request = evacuationQueue.poll();
            if (request == null) {
                return;
            }
            evacuationQueued.remove(request.playerUuid());
            Player player = Bukkit.getPlayer(request.playerUuid());
            if (player != null && player.isOnline() && matchesAccessWorld(request.access(), player.getWorld())) {
                redirectPlayer(player, request.access(), request.message());
            }
        }
    }

    private boolean isBlockedWorld(World world, boolean portalTeleport) {
        WorldAccess access = accessFor(world);
        if (access == null || access.enabled()) {
            return false;
        }
        if (portalTeleport && access.allowPortals()) {
            return false;
        }
        return true;
    }

    private String blockedMessage(World world) {
        WorldAccess access = accessFor(world);
        if (access == endAccess || (world != null && world.getEnvironment() == World.Environment.THE_END)) {
            return color("&eЭнд сейчас закрыт.");
        }
        return color("&eНижний мир сейчас закрыт.");
    }

    private WorldAccess accessFor(World world) {
        if (world == null) {
            return null;
        }
        if (matchesAccessWorld(netherAccess, world)) {
            return netherAccess;
        }
        if (matchesAccessWorld(endAccess, world)) {
            return endAccess;
        }
        return null;
    }

    private boolean matchesAccessWorld(WorldAccess access, World world) {
        if (access == null || world == null) {
            return false;
        }
        if (access.worldNames().contains(world.getName())) {
            return true;
        }
        return access == netherAccess && world.getEnvironment() == World.Environment.NETHER
                || access == endAccess && world.getEnvironment() == World.Environment.THE_END;
    }

    private void redirectPlayer(Player player, WorldAccess access, String message) {
        // The final validated location is teleported with player.teleport(safe)
        // in finishRedirect after the target chunk has been prepared.
        if (access == null || player == null || !redirectInFlight.add(player.getUniqueId())) {
            return;
        }
        World target = resolveRedirectTarget(player.getWorld(), access);
        if (target == null) {
            redirectInFlight.remove(player.getUniqueId());
            return;
        }
        int chunkX = target.getSpawnLocation().getBlockX() >> 4;
        int chunkZ = target.getSpawnLocation().getBlockZ() >> 4;
        if (!target.isChunkLoaded(chunkX, chunkZ)) {
            target.getChunkAtAsync(chunkX, chunkZ, true).whenComplete((chunk, error) -> Bukkit.getScheduler().runTask(this, () -> {
                finishRedirect(player, access, message, target, error);
            }));
            return;
        }
        finishRedirect(player, access, message, target, null);
    }

    private void finishRedirect(Player player, WorldAccess access, String message, World target, Throwable preparationError) {
        boolean retryScheduled = false;
        try {
            if (preparationError != null || player == null || !player.isOnline()) {
                if (preparationError != null) {
                    getLogger().warning("WorldCore failed to prepare redirect chunk: " + preparationError.getMessage());
                }
                return;
            }
            Location safe = access.redirectToSafeSpawn() ? safeSpawn(target) : findSafeLocation(target, target.getSpawnLocation());
            if (safe == null) {
                getLogger().warning("WorldCore could not find a safe redirect location in world " + target.getName() + " for " + player.getName());
                player.sendMessage(color("&cНе удалось найти безопасную точку для перемещения."));
                return;
            }
            if (player.teleport(safe)) {
                blockedWorldWarnings.remove(player.getUniqueId());
                player.sendMessage(message);
            } else {
                getLogger().warning("WorldCore could not redirect " + player.getName() + " from a closed world.");
                // A protection plugin may cancel the first teleport event. A
                // cancelled redirect must not silently leave the player in a
                // closed world: retry once on the next tick at a freshly
                // validated spawn location, then report failure explicitly.
                retryScheduled = true;
                Bukkit.getScheduler().runTask(this, () -> retryRedirectTeleport(player, target, message, safe));
            }
        } finally {
            if (player != null && !retryScheduled) {
                redirectInFlight.remove(player.getUniqueId());
            }
        }
    }

    private void retryRedirectTeleport(Player player, World target, String message, Location firstTarget) {
        try {
            if (player == null || !player.isOnline()) {
                return;
            }
            Location fallback = safeSpawn(target);
            if (fallback == null) {
                fallback = firstTarget;
            }
            if (fallback != null && player.teleport(fallback)) {
                blockedWorldWarnings.remove(player.getUniqueId());
                player.sendMessage(message);
                return;
            }
            player.sendMessage(color("&cНе удалось переместить вас из закрытого мира."));
        } finally {
            if (player != null) {
                redirectInFlight.remove(player.getUniqueId());
            }
        }
    }

    private World resolveRedirectTarget(World source, WorldAccess requested) {
        Set<String> visited = new HashSet<>();
        WorldAccess current = requested;
        while (current != null) {
            String redirectName = current.redirectWorld();
            if (redirectName == null || redirectName.isBlank()
                    || !visited.add(redirectName.toLowerCase(Locale.ROOT))) {
                break;
            }
            World target = Bukkit.getWorld(redirectName);
            if (target != null && target != source) {
                WorldAccess targetAccess = accessFor(target);
                if (targetAccess == null || targetAccess.enabled()) {
                    return target;
                }
                current = targetAccess;
                continue;
            }
            break;
        }
        for (World candidate : Bukkit.getWorlds()) {
            if (candidate == source) {
                continue;
            }
            WorldAccess candidateAccess = accessFor(candidate);
            if (candidateAccess == null || candidateAccess.enabled()) {
                return candidate;
            }
        }
        return null;
    }

    private Location safeSpawn(World world) {
        return findSafeLocation(world, world.getSpawnLocation());
    }

    private Location findSafeLocation(World world, Location origin) {
        if (world == null || origin == null || origin.getWorld() != world) {
            return null;
        }
        int originX = origin.getBlockX();
        int originZ = origin.getBlockZ();
        int[] checks = {0};
        for (int radius = 0; radius <= 6; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    if (checks[0]++ >= MAX_SAFE_LOCATION_CHECKS) {
                        return null;
                    }
                    Location safe = safeLocationAt(world, originX + dx, originZ + dz);
                    if (safe != null) {
                        return safe;
                    }
                }
            }
        }
        for (int radius = 7; radius <= 32; radius += 5) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    if (checks[0]++ >= MAX_SAFE_LOCATION_CHECKS) {
                        return null;
                    }
                    Location safe = safeLocationAt(world, originX + dx, originZ + dz);
                    if (safe != null) {
                        return safe;
                    }
                }
            }
        }
        if (!world.isChunkLoaded(originX >> 4, originZ >> 4)) {
            return null;
        }
        int fallbackY = Math.max(world.getMinHeight() + 2, world.getHighestBlockYAt(originX, originZ) + 1);
        if (isSafeStandingLocation(world, originX, fallbackY, originZ)) {
            return new Location(world, originX + 0.5D, fallbackY, originZ + 0.5D);
        }
        Location spawnSafe = safeLocationAt(world, world.getSpawnLocation().getBlockX(), world.getSpawnLocation().getBlockZ());
        if (spawnSafe != null) {
            return spawnSafe;
        }
        getLogger().warning("WorldCore failed to find a safe location in world " + world.getName()
                + " near " + originX + "," + originZ + ". Teleport was cancelled.");
        return null;
    }

    private Location safeLocationAt(World world, int x, int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }
        int minY = world.getMinHeight() + 1;
        int maxY = world.getMaxHeight() - 2;
        if (world.getEnvironment() == World.Environment.NETHER) {
            maxY = Math.min(maxY, MAX_NETHER_SAFE_Y - 1);
        }
        int highest = Math.max(minY + 1, Math.min(maxY, world.getHighestBlockYAt(x, z) + 1));
        for (int y = Math.min(maxY, highest + 2); y >= Math.max(minY, highest - 6); y--) {
            if (isSafeStandingLocation(world, x, y, z)) {
                return new Location(world, x + 0.5D, y, z + 0.5D);
            }
        }
        for (int y = highest + 3; y <= Math.min(maxY, highest + 12); y++) {
            if (isSafeStandingLocation(world, x, y, z)) {
                return new Location(world, x + 0.5D, y, z + 0.5D);
            }
        }
        return null;
    }

    private boolean isSafeStandingLocation(World world, int x, int y, int z) {
        if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 1) {
            return false;
        }
        if (world.getEnvironment() == World.Environment.NETHER && y + 1 >= MAX_NETHER_SAFE_Y) {
            return false;
        }
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        Block ground = world.getBlockAt(x, y - 1, z);
        return isPassableForPlayer(feet) && isPassableForPlayer(head) && isSafeGround(ground.getType());
    }

    private boolean isPassableForPlayer(Block block) {
        Material type = block.getType();
        return type == Material.AIR;
    }

    private boolean isSafeGround(Material type) {
        return type.isSolid() && !isHazard(type);
    }

    private boolean isHazard(Material type) {
        return switch (type) {
            case LAVA, WATER, BUBBLE_COLUMN, FIRE, SOUL_FIRE, CAMPFIRE, SOUL_CAMPFIRE, CACTUS, MAGMA_BLOCK,
                    END_PORTAL, END_PORTAL_FRAME, NETHER_PORTAL, POWDER_SNOW, SWEET_BERRY_BUSH,
                    WITHER_ROSE, VOID_AIR, CAVE_AIR, KELP, KELP_PLANT, SEAGRASS, TALL_SEAGRASS -> true;
            default -> false;
        };
    }

    private boolean isOutsideLimit(Location location, WorldLimit limit) {
        return distanceFromCenter(location, limit) > limit.radius();
    }

    private double distanceFromCenter(Location location, WorldLimit limit) {
        Location center = location.getWorld() == null ? location : location.getWorld().getSpawnLocation();
        return new Vector(location.getX() - center.getX(), 0.0D, location.getZ() - center.getZ()).length();
    }

    private double warningThreshold(WorldLimit limit) {
        return Math.max(0.0D, limit.radius() - Math.max(0, limit.warningDistance()));
    }

    private Location clampToBorder(Location location, WorldLimit limit) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        Location center = world.getSpawnLocation();
        double dx = location.getX() - center.getX();
        double dz = location.getZ() - center.getZ();
        double distance = Math.max(0.0001D, Math.sqrt((dx * dx) + (dz * dz)));
        double allowed = Math.max(1.0D, limit.radius() - 1.5D);
        double scale = allowed / distance;
        Location target = new Location(world, center.getX() + (dx * scale), location.getY(), center.getZ() + (dz * scale));
        return findSafeLocation(world, target);
    }

    private List<String> prefix(List<String> values, String token) {
        String probe = token == null ? "" : token.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(probe)) {
                result.add(value);
            }
        }
        return result;
    }

    private int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException parseError) {
            return fallback;
        }
    }

    private String color(String raw) {
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    private void button(MenuHolder holder, Inventory inventory, int slot, Material material, String name, List<String> lore, String action) {
        button(holder, inventory, slot, material, name, lore, action, "");
    }

    private void button(MenuHolder holder, Inventory inventory, int slot, Material material, String name, List<String> lore, String leftAction, String rightAction) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                coloredLore.add(color(line));
            }
            meta.setLore(coloredLore);
            meta.addItemFlags(ItemFlag.values());
            stack.setItemMeta(meta);
        }
        inventory.setItem(slot, stack);
        if (leftAction != null && !leftAction.isBlank()) {
            holder.actions.put(slot, leftAction);
        }
        if (rightAction != null && !rightAction.isBlank()) {
            holder.rightActions.put(slot, rightAction);
        }
    }

    private record WorldLimit(boolean enabled, Set<String> worldNames, int radius, boolean useVanillaWorldBorder,
                              boolean blockTeleportOutside, boolean correctPlayersOutside, int warningDistance) {
    }

    private record WorldAccess(boolean enabled, Set<String> worldNames, boolean allowPortals,
                               boolean allowCommandsTeleport, String redirectWorld, boolean redirectToSafeSpawn) {
    }

    private record OperationResult(boolean success, String message) {
    }

    private record TeleportToken(UUID playerUuid, String operationId, String targetWorld, long expiresAtMillis) {
    }

    private record EvacuationRequest(UUID playerUuid, WorldAccess access, String message) {
    }

    private record BorderSnapshot(double centerX, double centerZ, double size, double damageBuffer,
                                  double damageAmount, int warningDistance, int warningTime) {
        private static BorderSnapshot capture(WorldBorder border) {
            return new BorderSnapshot(border.getCenter().getX(), border.getCenter().getZ(), border.getSize(),
                    border.getDamageBuffer(), border.getDamageAmount(), border.getWarningDistance(), border.getWarningTime());
        }

        private void restore(WorldBorder border) {
            border.setCenter(centerX, centerZ);
            border.setSize(size);
            border.setDamageBuffer(damageBuffer);
            border.setDamageAmount(damageAmount);
            border.setWarningDistance(warningDistance);
            border.setWarningTime(warningTime);
        }
    }

    private static final class MenuHolder implements InventoryHolder {
        private final String id;
        private final Map<Integer, String> actions = new HashMap<>();
        private final Map<Integer, String> rightActions = new HashMap<>();
        private Inventory inventory;

        private MenuHolder(String id) {
            this.id = id;
        }

        private Inventory create(int size, String title) {
            this.inventory = Bukkit.createInventory(this, size, title);
            return this.inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
