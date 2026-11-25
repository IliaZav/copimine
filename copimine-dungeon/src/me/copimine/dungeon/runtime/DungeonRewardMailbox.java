package me.copimine.dungeon.runtime;

import me.copimine.dungeon.domain.DungeonRoomDefinition;
import me.copimine.dungeon.domain.RewardMode;
import me.copimine.dungeon.store.DungeonDefinitionStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
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

final class DungeonRewardMailbox {
    private final Plugin plugin;
    private final Path directory;
    private final Map<UUID, List<Path>> available = new HashMap<>();

    DungeonRewardMailbox(Plugin plugin, Path directory) {
        this.plugin = plugin;
        this.directory = directory;
    }

    static LootTable lootTable(DungeonRoomDefinition room) {
        if (room.reward().mode() != RewardMode.PERSONAL || room.reward().lootTableId().isBlank()) {
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
                if (ready) index(path, checkpoint);
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

    void prepare(DungeonRoomDefinition room, long generation, World world, Set<UUID> roster)
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
        // Keep the original loot rolls on disk through retries and restarts.
        for (UUID id : roster) {
            LootContext context = new LootContext.Builder(origin).build();
            Random random = new Random(generation ^ id.getMostSignificantBits() ^ id.getLeastSignificantBits());
            List<ItemStack> loot = table.populateLoot(random, context).stream()
                    .filter(item -> !item.getType().isAir() && item.getAmount() > 0).toList();
            data.set("players." + id, loot);
        }
        DungeonDefinitionStore.atomicWrite(pending, data.saveToString());
    }

    void release(String roomId, long generation) throws IOException {
        Path pending = file(generation, ".pending");
        Path ready = file(generation, ".ready");
        YamlConfiguration data = read(Files.exists(ready) ? ready : pending);
        if (!roomId.equals(data.getString("room")) || data.getLong("generation") != generation) {
            throw new IOException("Reward checkpoint does not match room and generation");
        }
        if (!Files.exists(ready)) {
            DungeonDefinitionStore.atomicWrite(ready, data.saveToString());
        }
        index(ready, data);
        Files.deleteIfExists(pending);
    }

    void deliver(Player player) {
        if (!player.isOnline() || player.isDead()) {
            return;
        }
        List<Path> inbox = available.get(player.getUniqueId());
        if (inbox == null) {
            return;
        }
        for (Path path : List.copyOf(inbox)) {
            try {
                YamlConfiguration data = read(path);
                if (deposit(player, data)) {
                    inbox.remove(path);
                }
            } catch (IOException | RuntimeException error) {
                plugin.getLogger().warning("Unable to deliver dungeon mailbox entry " + path.getFileName()
                        + ": " + error.getMessage());
            }
        }
        if (inbox.isEmpty()) {
            available.remove(player.getUniqueId());
        }
    }

    private boolean deposit(Player player, YamlConfiguration data) throws IOException {
        NamespacedKey receipt = new NamespacedKey(plugin, "reward_" + data.getLong("generation"));
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

    private void index(Path file, YamlConfiguration data) throws IOException {
        var players = data.getConfigurationSection("players");
        if (players == null || data.getLong("generation") <= 0 || data.getString("room") == null) {
            throw new IOException("Invalid dungeon reward checkpoint " + file);
        }
        for (String key : players.getKeys(false)) {
            UUID player;
            try {
                player = UUID.fromString(key);
            } catch (IllegalArgumentException error) {
                throw new IOException("Invalid player in dungeon reward checkpoint " + file, error);
            }
            List<Path> inbox = available.computeIfAbsent(player, ignored -> new ArrayList<>());
            if (!inbox.contains(file)) {
                inbox.add(file);
            }
        }
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
