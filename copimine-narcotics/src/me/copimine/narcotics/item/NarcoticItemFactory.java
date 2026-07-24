package me.copimine.narcotics.item;

import me.copimine.narcotics.CopiMineNarcotics;
import me.copimine.narcotics.config.NarcoticsConfigService;
import me.copimine.narcotics.model.NarcoticDefinition;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;

public final class NarcoticItemFactory {
    private final CopiMineNarcotics plugin;
    private NarcoticsConfigService configService;
    private final NamespacedKey itemTypeKey;
    private final NamespacedKey narcoticIdKey;
    private final NamespacedKey versionKey;
    private final NamespacedKey officialKey;

    public NarcoticItemFactory(CopiMineNarcotics plugin, NarcoticsConfigService configService) {
        this.plugin = plugin;
        this.configService = configService;
        itemTypeKey = new NamespacedKey(plugin, "copimine_item_type");
        narcoticIdKey = new NamespacedKey(plugin, "narcotic_id");
        versionKey = new NamespacedKey(plugin, "narcotic_version");
        officialKey = new NamespacedKey(plugin, "official");
    }

    public void reload(NarcoticsConfigService configService) {
        this.configService = configService;
    }

    public ItemStack createOfficialItem(NarcoticDefinition definition, int amount) {
        Material base = definition.material() == null ? Material.PAPER : definition.material();
        if (base == Material.AIR) {
            base = definition.fallbackMaterial() == null ? Material.PAPER : definition.fallbackMaterial();
        }
        ItemStack stack = new ItemStack(base, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setDisplayName(color("&e" + plainName(definition.displayName())));
        meta.setLore(List.of());
        if (configService.textureMode() == NarcoticsConfigService.TextureMode.CUSTOM && definition.customModelData() > 0) {
            meta.setCustomModelData(definition.customModelData());
        }
        meta.addItemFlags(ItemFlag.values());
        meta.getPersistentDataContainer().set(itemTypeKey, PersistentDataType.STRING, "RP_NARCOTIC");
        meta.getPersistentDataContainer().set(narcoticIdKey, PersistentDataType.STRING, definition.id());
        meta.getPersistentDataContainer().set(versionKey, PersistentDataType.INTEGER, configService.narcoticVersion());
        meta.getPersistentDataContainer().set(officialKey, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    public NarcoticDefinition resolveOfficial(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || !stack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        String type = meta.getPersistentDataContainer().get(itemTypeKey, PersistentDataType.STRING);
        String id = meta.getPersistentDataContainer().get(narcoticIdKey, PersistentDataType.STRING);
        Integer version = meta.getPersistentDataContainer().get(versionKey, PersistentDataType.INTEGER);
        if (!"RP_NARCOTIC".equals(type) || id == null || !hasOfficialFlag(meta)) {
            return null;
        }
        NarcoticDefinition definition = configService.items().get(id);
        if (definition == null) {
            return null;
        }
        if (stack.getType() != definition.material()) {
            return null;
        }
        if (version != null && version > configService.narcoticVersion()) {
            return null;
        }
        if (configService.textureMode() == NarcoticsConfigService.TextureMode.CUSTOM
                && definition.customModelData() > 0
                && (!meta.hasCustomModelData() || meta.getCustomModelData() != definition.customModelData())) {
            return null;
        }
        if (meta.hasCustomModelData() && meta.getCustomModelData() != definition.customModelData()) {
            return null;
        }
        return definition;
    }

    public boolean isOfficialFinishedItem(ItemStack stack) {
        return resolveOfficial(stack) != null;
    }

    public void consumeOne(Player player, ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return;
        }
        if (stack.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            stack.setAmount(stack.getAmount() - 1);
            player.getInventory().setItemInMainHand(stack);
        }
        player.updateInventory();
    }

    /** Restore exactly one ingredient after a failed asynchronous state write. */
    public void restoreOne(Player player, ItemStack template) {
        if (player == null || template == null || template.getType() == Material.AIR) {
            return;
        }
        ItemStack restored = template.clone();
        restored.setAmount(1);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(restored);
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        player.updateInventory();
    }

    public int migrateOfficialItems(Player player) {
        int updated = migrateInventory(player.getInventory());
        updated += migrateInventory(player.getEnderChest());
        ItemStack offHand = player.getInventory().getItemInOffHand();
        NarcoticDefinition offHandDefinition = resolveOfficialLoose(offHand);
        if (offHandDefinition != null) {
            player.getInventory().setItemInOffHand(createOfficialItem(offHandDefinition, Math.max(1, offHand.getAmount())));
            updated++;
        }
        if (updated > 0) {
            player.updateInventory();
        }
        return updated;
    }

    public int migrateStorageInventory(Inventory inventory) {
        return migrateInventory(inventory);
    }

    private int migrateInventory(Inventory inventory) {
        if (inventory == null) {
            return 0;
        }
        int updated = 0;
        for (int index = 0; index < inventory.getSize(); index++) {
            ItemStack stack = inventory.getItem(index);
            NarcoticDefinition definition = resolveOfficialLoose(stack);
            if (definition == null) {
                continue;
            }
            inventory.setItem(index, createOfficialItem(definition, Math.max(1, stack.getAmount())));
            updated++;
        }
        return updated;
    }

    private NarcoticDefinition resolveOfficialLoose(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || !stack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        String type = meta.getPersistentDataContainer().get(itemTypeKey, PersistentDataType.STRING);
        String id = meta.getPersistentDataContainer().get(narcoticIdKey, PersistentDataType.STRING);
        if (!"RP_NARCOTIC".equals(type) || id == null || !hasOfficialFlag(meta)) {
            return null;
        }
        return configService.items().get(id);
    }

    private boolean hasOfficialFlag(ItemMeta meta) {
        Byte byteValue = meta.getPersistentDataContainer().get(officialKey, PersistentDataType.BYTE);
        if (byteValue != null) {
            return byteValue != 0;
        }
        Boolean booleanValue = meta.getPersistentDataContainer().get(officialKey, PersistentDataType.BOOLEAN);
        if (booleanValue != null) {
            return booleanValue;
        }
        String textValue = meta.getPersistentDataContainer().get(officialKey, PersistentDataType.STRING);
        if (textValue != null) {
            return "true".equalsIgnoreCase(textValue) || "1".equals(textValue);
        }
        return false;
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private String plainName(String text) {
        return ChatColor.stripColor(color(text == null ? "" : text));
    }
}
