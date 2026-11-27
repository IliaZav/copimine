package me.copimine.dungeon.runtime;

import me.copimine.dungeon.domain.DungeonRoomDefinition;
import me.copimine.dungeon.domain.RewardMode;
import me.copimine.dungeon.domain.SpawnAnchor;
import me.copimine.dungeon.store.DungeonDefinitionStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Item;
import org.bukkit.entity.Entity;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Chest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

final class DungeonRewardMailbox implements Listener {
    private final Plugin plugin;
    private final Path directory;
    private final Map<UUID, List<Ready>> available = new HashMap<>();
    private final Set<String> clearedRooms = new java.util.HashSet<>();
    private final Map<UUID, DroppedReward> drops = new HashMap<>();
    private final NamespacedKey dropTag;
    private int dropBudget;

    private record Site(UUID world, int x, int y, int z) {
        Location at() { return new Location(Bukkit.getWorld(world), x + 0.5, y + 0.25, z + 0.5); }
        boolean matches(Location block) {
            return block.getWorld() != null && world.equals(block.getWorld().getUID())
                    && x == block.getBlockX() && y == block.getBlockY() && z == block.getBlockZ();
        }
    }

    private record Ready(Path path, long generation, RewardMode mode, Site site, ItemStack icon) {}
    private record DroppedReward(Item item, Ready reward) {}

    private static final class ChestView implements InventoryHolder {
        final UUID owner;
        final Ready reward;
        final Inventory inventory;

        ChestView(Player player, Ready reward, List<?> items) {
            owner = player.getUniqueId();
            this.reward = reward;
            inventory = Bukkit.createInventory(this, 54, Component.text("Тайник · добыча"));
            for (int slot = 0; slot < Math.min(items.size(), 45); slot++) {
                if (items.get(slot) instanceof ItemStack item) inventory.setItem(slot, item.clone());
            }
            ItemStack claim = new ItemStack(Material.LIME_CONCRETE);
            claim.editMeta(meta -> meta.displayName(Component.text("Забрать всю добычу")));
            inventory.setItem(49, claim);
        }

        @Override public Inventory getInventory() { return inventory; }
    }

    DungeonRewardMailbox(Plugin plugin, Path directory) {
        this.plugin = plugin;
        this.directory = directory;
        dropTag = new NamespacedKey(plugin, "dungeon_reward_drop");
    }

    static LootTable lootTable(DungeonRoomDefinition room) {
        if (room.reward().lootTableId().isBlank()) {
            return null;
        }
        NamespacedKey key = NamespacedKey.fromString(room.reward().lootTableId());
        return key == null ? null : Bukkit.getLootTable(key);
    }

    long load() throws IOException {
        Files.createDirectories(directory);
        long latest = 0L;
        try (var files = Files.list(directory)) {
            for (Path path : files.toList()) {
                String name = path.getFileName().toString();
                boolean ready = name.endsWith(".ready");
                if (!ready && !name.endsWith(".pending")) continue;
                YamlConfiguration checkpoint = read(path);
                long generation = checkpoint.getLong("generation");
                if (generation <= 0 || !path.equals(file(generation, ready ? ".ready" : ".pending"))) {
                    throw new IOException("Invalid dungeon reward checkpoint name");
                }
                if (checkpoint.getString("room") == null) throw new IOException("Reward checkpoint has no room");
                clearedRooms.add(checkpoint.getString("room"));
                if (ready) index(readReady(path, checkpoint));
                latest = Math.max(latest, generation);
            }
        }
        return latest;
    }

    List<Long> pending(String roomId) throws IOException {
        List<Long> pending = new ArrayList<>();
        try (var files = Files.list(directory)) {
            for (Path path : files.filter(p -> p.getFileName().toString().endsWith(".pending")).toList()) {
                YamlConfiguration data = read(path);
                if (roomId.equals(data.getString("room"))) {
                    pending.add(data.getLong("generation"));
                }
            }
        }
        pending.sort(Long::compare);
        return pending;
    }

    boolean cleared(String roomId) { return clearedRooms.contains(roomId); }

    void prepare(DungeonRoomDefinition room, long generation, World world, Set<UUID> roster, SpawnAnchor anchor)
            throws IOException {
        Path pending = file(generation, ".pending");
        if (Files.exists(pending) || Files.exists(file(generation, ".ready"))) {
            return;
        }
        LootTable table = lootTable(room);
        if (table == null || world == null || roster.isEmpty()) {
            throw new IOException("Missing reward adapter, loot table, world or roster for " + room.id());
        }
        var area = room.trigger();
        Location origin = new Location(world, area.minX() + 0.5, area.minY(), area.minZ() + 0.5);
        YamlConfiguration data = new YamlConfiguration();
        data.set("room", room.id());
        data.set("generation", generation);
        data.set("mode", room.reward().mode().token());
        if (room.reward().mode() != RewardMode.PERSONAL) {
            if (anchor == null) throw new IOException("Missing physical reward anchor for " + room.id());
            data.set("site.world", world.getUID().toString());
            data.set("site.x", anchor.x());
            data.set("site.y", anchor.y());
            data.set("site.z", anchor.z());
            origin = new Location(world, anchor.x() + 0.5, anchor.y() + 0.25, anchor.z() + 0.5);
        }
        // Keep the original loot rolls on disk through retries and restarts.
        for (UUID id : roster) {
            LootContext context = new LootContext.Builder(origin).build();
            Random random = new Random(generation ^ id.getMostSignificantBits() ^ id.getLeastSignificantBits());
            List<ItemStack> loot = table.populateLoot(random, context).stream()
                    .filter(item -> !item.getType().isAir() && item.getAmount() > 0).toList();
            data.set("players." + id, loot);
        }
        DungeonDefinitionStore.atomicWrite(pending, data.saveToString());
        clearedRooms.add(room.id());
    }

    void release(String roomId, long generation) throws IOException {
        Path pending = file(generation, ".pending");
        Path ready = file(generation, ".ready");
        YamlConfiguration data = read(Files.exists(ready) ? ready : pending);
        if (!roomId.equals(data.getString("room")) || data.getLong("generation") != generation) {
            throw new IOException("Reward checkpoint does not match room and generation");
        }
        Map<UUID, Ready> validated = readReady(ready, data);
        if (!Files.exists(ready)) {
            DungeonDefinitionStore.atomicWrite(ready, data.saveToString());
        }
        index(validated);
        Files.deleteIfExists(pending);
    }

    void tick(Iterable<? extends Player> players) {
        dropBudget = 16;
        for (var iterator = drops.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            var tracked = entry.getValue();
            Player owner = Bukkit.getPlayer(entry.getKey());
            if (!tracked.item().isValid() || owner == null || !near(owner, tracked.reward().site(), 12)) {
                tracked.item().remove();
                iterator.remove();
            }
        }
        for (Player player : players) deliver(player);
    }

    void stop() {
        drops.values().forEach(reward -> reward.item().remove());
        drops.clear();
    }

    private void deliver(Player player) {
        if (!eligible(player)) return;
        UUID owner = player.getUniqueId();
        List<Ready> inbox = available.get(owner);
        if (inbox == null) return;
        for (Ready reward : List.copyOf(inbox)) {
            if (hasReceipt(player, reward.generation())) {
                inbox.remove(reward);
                continue;
            }
            if (reward.mode() == RewardMode.PERSONAL || reward.icon() == null) {
                claim(player, reward);
            } else if (reward.mode() == RewardMode.DROPS && dropBudget > 0 && !drops.containsKey(owner)
                    && near(player, reward.site(), 12) && valid(reward)) {
                ItemStack icon = reward.icon().clone();
                Location at = reward.site().at();
                Item item = at.getWorld().dropItem(at, icon, entity -> {
                    entity.setOwner(owner);
                    entity.setPersistent(false);
                    entity.setUnlimitedLifetime(true);
                    entity.setInvulnerable(true);
                    entity.setGravity(false);
                    entity.setVelocity(new org.bukkit.util.Vector());
                    entity.setPickupDelay(10);
                    entity.getPersistentDataContainer().set(dropTag, PersistentDataType.LONG, reward.generation());
                });
                if (item.isValid()) drops.put(owner, new DroppedReward(item, reward));
                dropBudget--;
            }
        }
        if (inbox.isEmpty()) available.remove(owner);
    }

    private boolean claim(Player player, Ready reward) {
        var inbox = available.get(player.getUniqueId());
        if (inbox == null || !inbox.contains(reward)) return false;
        try {
            if (!deposit(player, read(reward.path()))) return false;
            inbox.remove(reward);
            return true;
        } catch (IOException | RuntimeException error) {
            plugin.getLogger().warning("Unable to deliver dungeon reward " + reward.generation()
                    + ": " + error.getMessage());
            return false;
        }
    }

    private static boolean eligible(Player player) {
        return player.isOnline() && !player.isDead()
                && (player.getGameMode() == org.bukkit.GameMode.SURVIVAL
                    || player.getGameMode() == org.bukkit.GameMode.ADVENTURE);
    }

    private static boolean near(Player player, Site site, double distance) {
        return eligible(player) && site != null && player.getWorld().getUID().equals(site.world())
                && player.getLocation().distanceSquared(site.at()) <= distance * distance;
    }

    private static boolean valid(Ready reward) {
        Site site = reward.site();
        return site != null && DungeonRewardSite.valid(reward.mode(), Bukkit.getWorld(site.world()),
                site.x(), site.y(), site.z());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChest(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || event.getClickedBlock() == null || !eligible(event.getPlayer())) return;
        Player player = event.getPlayer();
        List<Ready> inbox = available.get(player.getUniqueId());
        if (inbox == null) return;
        Location block = event.getClickedBlock().getLocation();
        for (Ready reward : inbox) {
            if (reward.mode() != RewardMode.CHEST || !reward.site().matches(block)
                    || hasReceipt(player, reward.generation()) || !valid(reward)) continue;
            event.setCancelled(true);
            try {
                List<?> items = read(reward.path()).getList("players." + player.getUniqueId());
                if (items == null) throw new IOException("Missing chest reward");
                Chest chest = (Chest) event.getClickedBlock().getState();
                chest.open();
                renderChestReveal(player, reward);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (block.getWorld().isChunkLoaded(block.getBlockX() >> 4, block.getBlockZ() >> 4)
                            && block.getBlock().getState() instanceof Chest current) current.close();
                }, 22L);
                player.openInventory(new ChestView(player, reward, items).inventory);
            } catch (IOException | RuntimeException error) {
                plugin.getLogger().warning("Unable to open dungeon chest reward " + reward.generation()
                        + ": " + error.getMessage());
            }
            return;
        }
    }

    private void renderChestReveal(Player player, Ready reward) {
        Location center = reward.site().at().add(0, .48, 0);
        player.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, .65f, .79f);
        for (int beat = 0; beat < 3; beat++) {
            int frame = beat;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!near(player, reward.site(), 6) || !valid(reward)) return;
                for (int point = 0; point < 8; point++) {
                    double angle = point * Math.PI / 4 + frame * .55;
                    double radius = .47 - frame * .08;
                    Location at = center.clone().add(Math.cos(angle) * radius, frame * .23, Math.sin(angle) * radius);
                    player.spawnParticle(point % 2 == 0 ? Particle.WAX_ON : Particle.ENCHANT, at, 1, 0, 0, 0, .02);
                }
            }, 2L + beat * 5);
        }
    }

    @EventHandler
    public void onChestClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ChestView view)) return;
        event.setCancelled(true);
        if (event.getRawSlot() != 49 || !(event.getWhoClicked() instanceof Player player)
                || !view.owner.equals(player.getUniqueId())
                || !near(player, view.reward.site(), 6) || !valid(view.reward)) return;
        boolean claimed = claim(player, view.reward);
        if (claimed) {
            player.playSound(view.reward.site().at(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 0.8f);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.getOpenInventory().getTopInventory().getHolder() == view) player.closeInventory();
            });
        } else {
            player.sendMessage("Не удалось забрать добычу. Освободите место и попробуйте снова.");
        }
    }

    @EventHandler
    public void onChestDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ChestView) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        Item item = event.getItem();
        if (!tagged(item)) return;
        event.setCancelled(true);
        if (!(event.getEntity() instanceof Player player)) return;
        var tracked = drops.get(player.getUniqueId());
        if (tracked != null && tracked.item().getUniqueId().equals(item.getUniqueId())
                && near(player, tracked.reward().site(), 4) && valid(tracked.reward())
                && claim(player, tracked.reward())) {
            drops.remove(player.getUniqueId());
            item.remove();
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 0.85f);
        } else {
            item.setPickupDelay(20);
        }
    }

    @EventHandler public void onHopper(InventoryPickupItemEvent event) {
        if (tagged(event.getItem())) event.setCancelled(true);
    }

    @EventHandler public void onMerge(ItemMergeEvent event) {
        if (tagged(event.getEntity()) || tagged(event.getTarget())) event.setCancelled(true);
    }

    boolean tagged(Entity entity) {
        return entity instanceof Item && entity.getPersistentDataContainer().has(dropTag, PersistentDataType.LONG);
    }

    private NamespacedKey receipt(long rewardGeneration) {
        return new NamespacedKey(plugin, "reward_" + rewardGeneration);
    }

    private boolean hasReceipt(Player player, long rewardGeneration) {
        return player.getPersistentDataContainer().has(receipt(rewardGeneration), PersistentDataType.BYTE);
    }

    private boolean deposit(Player player, YamlConfiguration data) throws IOException {
        NamespacedKey receipt = receipt(data.getLong("generation"));
        var tags = player.getPersistentDataContainer();
        if (tags.has(receipt, PersistentDataType.BYTE)) {
            return true;
        }
        List<?> loot = data.getList("players." + player.getUniqueId());
        if (loot == null || loot.stream().anyMatch(item -> !(item instanceof ItemStack))) {
            throw new IOException("Invalid serialized dungeon loot");
        }
        var inventory = player.getInventory();
        ItemStack[] original = inventory.getStorageContents();
        for (int i = 0; i < original.length; i++) {
            if (original[i] != null) {
                original[i] = original[i].clone();
            }
        }
        ItemStack[] items = loot.stream().map(item -> ((ItemStack) item).clone()).toArray(ItemStack[]::new);
        try {
            if (!inventory.addItem(items).isEmpty()) {
                inventory.setStorageContents(original);
                return false;
            }
            // Inventory and receipt share the player-data save boundary.
            tags.set(receipt, PersistentDataType.BYTE, (byte) 1);
            player.saveData();
        } catch (RuntimeException error) {
            inventory.setStorageContents(original);
            tags.remove(receipt);
            throw error;
        }
        if (items.length > 0) {
            player.sendMessage("Награда подземелья получена.");
        }
        return true;
    }

    private Map<UUID, Ready> readReady(Path file, YamlConfiguration data) throws IOException {
        Map<UUID, Ready> validated = new java.util.LinkedHashMap<>();
        var players = data.getConfigurationSection("players");
        if (players == null || players.getKeys(false).isEmpty()
                || data.getLong("generation") <= 0 || data.getString("room") == null) {
            throw new IOException("Invalid dungeon reward checkpoint " + file);
        }
        RewardMode mode = RewardMode.fromToken(data.getString("mode", "personal"));
        if (mode == null) throw new IOException("Invalid reward delivery mode " + file);
        Site site = null;
        if (mode != RewardMode.PERSONAL) {
            try {
                if (!data.isInt("site.x") || !data.isInt("site.y") || !data.isInt("site.z"))
                    throw new IllegalArgumentException("Missing anchor coordinates");
                site = new Site(UUID.fromString(data.getString("site.world", "")),
                        data.getInt("site.x"), data.getInt("site.y"), data.getInt("site.z"));
            } catch (IllegalArgumentException invalid) {
                throw new IOException("Invalid physical reward site " + file, invalid);
            }
        }
        for (String key : players.getKeys(false)) {
            UUID player;
            try {
                player = UUID.fromString(key);
            } catch (IllegalArgumentException error) {
                throw new IOException("Invalid player in dungeon reward checkpoint " + file, error);
            }
            List<?> loot = players.getList(key);
            if (loot == null || loot.stream().anyMatch(item -> !(item instanceof ItemStack))) {
                throw new IOException("Invalid loot in reward checkpoint " + file);
            }
            ItemStack icon = loot.isEmpty() ? null : ((ItemStack) loot.getFirst()).clone();
            if (icon != null) icon.setAmount(1);
            Ready reward = new Ready(file, data.getLong("generation"), mode, site, icon);
            if (validated.putIfAbsent(player, reward) != null) {
                throw new IOException("Duplicate player in reward checkpoint " + file);
            }
        }
        return validated;
    }

    private void index(Map<UUID, Ready> validated) {
        validated.forEach((player, entry) -> {
            List<Ready> inbox = available.computeIfAbsent(player, ignored -> new ArrayList<>());
            if (inbox.stream().noneMatch(reward -> reward.path().equals(entry.path()))) {
                inbox.add(entry);
                inbox.sort(java.util.Comparator.comparingLong(Ready::generation));
            }
        });
    }

    private Path file(long generation, String suffix) {
        return directory.resolve(Long.toString(generation) + suffix);
    }

    private static YamlConfiguration read(Path file) throws IOException {
        YamlConfiguration data = new YamlConfiguration();
        try {
            data.load(file.toFile());
        } catch (InvalidConfigurationException error) {
            throw new IOException("Invalid dungeon reward checkpoint " + file, error);
        }
        return data;
    }
}
