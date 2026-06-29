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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CopiMineWorldCore extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private WorldLimit overworldLimit;
    private WorldAccess netherAccess;
    private WorldAccess endAccess;
    private final Set<UUID> warnedOutside = new LinkedHashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        reloadLocalConfig();
        PluginCommand command = getCommand("cmworld");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::enforceWorldAccessAndBorders, 40L, 40L);
        applyOverworldBorder();
    }

    public void openAdminWorldHub(Player player) {
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
            sender.sendMessage(color("&aПроверка завершена."));
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
            applyOverworldBorder();
            sender.sendMessage(color("&aVanilla worldborder применён."));
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
            getConfig().set(path, true);
            saveConfig();
            reloadLocalConfig();
            sender.sendMessage(color("&a" + title + " открыт."));
            return true;
        }
        if ("close".equalsIgnoreCase(args[1])) {
            int inside = playersInside(access);
            if (inside > 0 && (args.length < 3 || !"confirm".equalsIgnoreCase(args[2]))) {
                sender.sendMessage(color("&eВ мире есть игроки. Повтори: &f/cmworld " + args[0] + " close confirm"));
                return true;
            }
            getConfig().set(path, false);
            saveConfig();
            reloadLocalConfig();
            evacuatePlayers(access, color("&e" + title + " сейчас закрыт."));
            sender.sendMessage(color("&a" + title + " закрыт."));
            return true;
        }
        sendHelp(sender);
        return true;
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

    @EventHandler
    public void onPortal(PlayerPortalEvent event) {
        World targetWorld = event.getTo() == null ? null : event.getTo().getWorld();
        if (targetWorld == null) {
            return;
        }
        if (isBlockedWorld(targetWorld, true)) {
            event.setCancelled(true);
            redirectPlayer(event.getPlayer(), accessFor(targetWorld), blockedMessage(targetWorld));
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null || event.getTo().getWorld() == null) {
            return;
        }
        World targetWorld = event.getTo().getWorld();
        boolean portalTeleport = switch (event.getCause()) {
            case NETHER_PORTAL, END_PORTAL, END_GATEWAY -> true;
            default -> false;
        };
        if (isBlockedWorld(targetWorld, portalTeleport)) {
            event.setCancelled(true);
            redirectPlayer(event.getPlayer(), accessFor(targetWorld), blockedMessage(targetWorld));
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

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null || to.getWorld() == null) {
            return;
        }
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

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        World current = event.getPlayer().getWorld();
        if (isBlockedWorld(current, false)) {
            redirectPlayer(event.getPlayer(), accessFor(current), blockedMessage(current));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        warnedOutside.remove(event.getPlayer().getUniqueId());
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
        String action = event.isRightClick() ? holder.rightActions.get(event.getRawSlot()) : holder.actions.get(event.getRawSlot());
        if (action == null || action.isBlank()) {
            return;
        }
        switch (action) {
            case "gui:border:status" -> player.sendMessage(color("&7Текущий радиус границы: &f" + overworldLimit.radius()));
            case "gui:border:apply" -> {
                applyOverworldBorder();
                player.sendMessage(color("&aVanilla worldborder применён."));
            }
            case "gui:nether:open" -> {
                dispatchConsole("cmworld nether open");
                player.sendMessage(color("&aНижний мир открыт."));
                openAdminWorldHub(player);
            }
            case "gui:nether:close" -> {
                if (playersInside(netherAccess) > 0) {
                    openWorldCloseConfirmMenu(player, true, playersInside(netherAccess));
                } else {
                    dispatchConsole("cmworld nether close");
                    player.sendMessage(color("&aНижний мир закрыт."));
                    openAdminWorldHub(player);
                }
            }
            case "gui:end:open" -> {
                dispatchConsole("cmworld end open");
                player.sendMessage(color("&aЭнд открыт."));
                openAdminWorldHub(player);
            }
            case "gui:end:close" -> {
                if (playersInside(endAccess) > 0) {
                    openWorldCloseConfirmMenu(player, false, playersInside(endAccess));
                } else {
                    dispatchConsole("cmworld end close");
                    player.sendMessage(color("&aЭнд закрыт."));
                    openAdminWorldHub(player);
                }
            }
            case "gui:confirm:close:nether" -> {
                dispatchConsole("cmworld nether close confirm");
                player.sendMessage(color("&aНижний мир закрыт."));
                openAdminWorldHub(player);
            }
            case "gui:confirm:close:end" -> {
                dispatchConsole("cmworld end close confirm");
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
        for (Player player : Bukkit.getOnlinePlayers()) {
            World world = player.getWorld();
            if (isBlockedWorld(world, false)) {
                redirectPlayer(player, accessFor(world), blockedMessage(world));
                continue;
            }
            if (overworldLimit.enabled()
                    && overworldLimit.worldNames().contains(world.getName())
                    && isOutsideLimit(player.getLocation(), overworldLimit)) {
                Location safe = clampToBorder(player.getLocation(), overworldLimit);
                if (safe != null) {
                    player.teleport(safe);
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

    private void applyOverworldBorder() {
        if (!overworldLimit.enabled() || !overworldLimit.useVanillaWorldBorder()) {
            return;
        }
        for (String worldName : overworldLimit.worldNames()) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                continue;
            }
            WorldBorder border = world.getWorldBorder();
            Location center = world.getSpawnLocation();
            border.setCenter(center.getX(), center.getZ());
            border.setSize(overworldLimit.radius() * 2.0D);
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
                redirectPlayer(player, access, message);
            }
        }
    }

    private boolean isBlockedWorld(World world, boolean portal) {
        WorldAccess access = accessFor(world);
        if (access == null || access.enabled()) {
            return false;
        }
        return portal || !access.allowCommandsTeleport();
    }

    private String blockedMessage(World world) {
        if (world != null && endAccess.worldNames().contains(world.getName())) {
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
        if (access == null) {
            return;
        }
        World target = Bukkit.getWorld(access.redirectWorld());
        if (target == null && !Bukkit.getWorlds().isEmpty()) {
            target = Bukkit.getWorlds().getFirst();
        }
        if (target == null) {
            return;
        }
        Location safe = access.redirectToSafeSpawn() ? safeSpawn(target) : findSafeLocation(target, target.getSpawnLocation());
        if (safe == null) {
            getLogger().warning("WorldCore could not find a safe redirect location in world " + target.getName() + " for " + player.getName());
            player.sendMessage(color("&cНе удалось найти безопасную точку для перемещения."));
            return;
        }
        player.teleport(safe);
        player.sendMessage(message);
    }

    private Location safeSpawn(World world) {
        return findSafeLocation(world, world.getSpawnLocation());
    }

    private Location findSafeLocation(World world, Location origin) {
        int originX = origin.getBlockX();
        int originZ = origin.getBlockZ();
        for (int radius = 0; radius <= 6; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
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
                    Location safe = safeLocationAt(world, originX + dx, originZ + dz);
                    if (safe != null) {
                        return safe;
                    }
                }
            }
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
        int minY = world.getMinHeight() + 1;
        int maxY = world.getMaxHeight() - 2;
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
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        Block ground = world.getBlockAt(x, y - 1, z);
        return isPassableForPlayer(feet) && isPassableForPlayer(head) && isSafeGround(ground.getType());
    }

    private boolean isPassableForPlayer(Block block) {
        Material type = block.getType();
        return !type.isSolid() && !isHazard(type);
    }

    private boolean isSafeGround(Material type) {
        return type.isSolid() && !isHazard(type);
    }

    private boolean isHazard(Material type) {
        return switch (type) {
            case LAVA, FIRE, SOUL_FIRE, CAMPFIRE, SOUL_CAMPFIRE, CACTUS, MAGMA_BLOCK,
                    END_PORTAL, END_PORTAL_FRAME, NETHER_PORTAL, POWDER_SNOW, SWEET_BERRY_BUSH,
                    WITHER_ROSE, VOID_AIR, CAVE_AIR -> true;
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
        } catch (Exception ignored) {
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
