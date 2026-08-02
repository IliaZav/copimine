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
import java.util.UUID;

public final class NarcoticItemFactory {
    private final CopiMineNarcotics plugin;
    private NarcoticsConfigService configService;
    private final NamespacedKey itemTypeKey;
    private final NamespacedKey narcoticIdKey;
    private final NamespacedKey versionKey;
    private final NamespacedKey officialKey;
    private final NamespacedKey instanceIdKey;
    private final NamespacedKey ownerUuidKey;

    public NarcoticItemFactory(CopiMineNarcotics plugin, NarcoticsConfigService configService) {
        this.plugin = plugin;
        this.configService = configService;
        itemTypeKey = new NamespacedKey(plugin, "copimine_item_type");
        narcoticIdKey = new NamespacedKey(plugin, "narcotic_id");
        versionKey = new NamespacedKey(plugin, "narcotic_version");
        officialKey = new NamespacedKey(plugin, "official");
        instanceIdKey = new NamespacedKey(plugin, "narcotic_instance_id");
        ownerUuidKey = new NamespacedKey(plugin, "narcotic_owner_uuid");
    }

    public void reload(NarcoticsConfigService configService) {
        this.configService = configService;
    }

    public ItemStack createOfficialItem(NarcoticDefinition definition, int amount) {
        return createOfficialItem(definition, amount, null);
    }

    /** Create a server-issued narcotic bound to its entitlement owner. */
    public ItemStack createOfficialItem(NarcoticDefinition definition, int amount, UUID ownerUuid) {
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
        // Every issued stack carries a server-generated instance identifier.  It
        // is not a security signature (Creative events are still cancelled),
        // but it lets audits and recovery distinguish physical instances.
        meta.getPersistentDataContainer().set(instanceIdKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        if (ownerUuid != null) {
            meta.getPersistentDataContainer().set(ownerUuidKey, PersistentDataType.STRING, ownerUuid.toString());
        }
        stack.setItemMeta(meta);
        return stack;
    }

    /** Return the owner embedded by the issuing path, or null for legacy items. */
    public UUID ownerUuid(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta() || stack.getItemMeta() == null) {
            return null;
        }
        String raw = stack.getItemMeta().getPersistentDataContainer().get(ownerUuidKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** Bind legacy official stacks exactly once when a known player owns them. */
    public boolean bindOwnerIfMissing(ItemStack stack, UUID ownerUuid) {
        if (stack == null || stack.getType() == Material.AIR || ownerUuid == null || !isOfficialCandidate(stack)
                || ownerUuid(stack) != null || stack.getItemMeta() == null) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(ownerUuidKey, PersistentDataType.STRING, ownerUuid.toString());
        stack.setItemMeta(meta);
        return true;
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
        String instanceId = meta.getPersistentDataContainer().get(instanceIdKey, PersistentDataType.STRING);
        if (instanceId == null || instanceId.isBlank()) {
            meta.getPersistentDataContainer().set(instanceIdKey, PersistentDataType.STRING, UUID.randomUUID().toString());
            stack.setItemMeta(meta);
        } else {
            try {
                UUID.fromString(instanceId);
            } catch (IllegalArgumentException invalidInstanceId) {
                meta.getPersistentDataContainer().set(instanceIdKey, PersistentDataType.STRING, UUID.randomUUID().toString());
                stack.setItemMeta(meta);
            }
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

    /** Returns true for a catalog item even when its instance metadata is stale.
     * This is used only by quarantine/Creative guards, never to authorize use. */
    public boolean isOfficialCandidate(ItemStack stack) {
        return resolveOfficialLoose(stack) != null;
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

    /** Remove one item from the exact server-issued stack after persistence succeeds. */
    public boolean consumeOneExact(Player player, String instanceId, String narcoticId) {
        if (player == null || instanceId == null || instanceId.isBlank()) {
            return false;
        }
        ItemStack cursor = player.getItemOnCursor();
        if (matchesExact(cursor, instanceId, narcoticId)) {
            if (cursor.getAmount() <= 1) {
                player.setItemOnCursor(new ItemStack(Material.AIR));
            } else {
                cursor.setAmount(cursor.getAmount() - 1);
                player.setItemOnCursor(cursor);
            }
            player.updateInventory();
            return true;
        }
        PlayerInventoryView view = new PlayerInventoryView(player);
        for (int slot = 0; slot < view.size(); slot++) {
            ItemStack candidate = view.get(slot);
            if (!matchesExact(candidate, instanceId, narcoticId)) {
                continue;
            }
            if (candidate.getAmount() <= 1) {
                view.set(slot, new ItemStack(Material.AIR));
            } else {
                candidate.setAmount(candidate.getAmount() - 1);
                view.set(slot, candidate);
            }
            player.updateInventory();
            return true;
        }
        return false;
    }

    /** Count the exact issued instance currently held by the player. */
    public int exactQuantity(Player player, String instanceId, String narcoticId) {
        if (player == null || instanceId == null || instanceId.isBlank()) {
            return 0;
        }
        int quantity = 0;
        ItemStack cursor = player.getItemOnCursor();
        if (matchesExact(cursor, instanceId, narcoticId)) {
            quantity += Math.max(0, cursor.getAmount());
        }
        PlayerInventoryView view = new PlayerInventoryView(player);
        for (int slot = 0; slot < view.size(); slot++) {
            ItemStack candidate = view.get(slot);
            if (matchesExact(candidate, instanceId, narcoticId)) {
                long next = (long) quantity + Math.max(0, candidate.getAmount());
                quantity = (int) Math.min(Integer.MAX_VALUE, next);
            }
        }
        return quantity;
    }

    private boolean matchesExact(ItemStack stack, String instanceId, String narcoticId) {
        if (stack == null || stack.getType() == Material.AIR || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        String candidateId = meta.getPersistentDataContainer().get(instanceIdKey, PersistentDataType.STRING);
        String candidateNarcotic = meta.getPersistentDataContainer().get(narcoticIdKey, PersistentDataType.STRING);
        return instanceId.equals(candidateId)
                && (narcoticId == null || narcoticId.equalsIgnoreCase(candidateNarcotic));
    }

    public String instanceId(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta() || stack.getItemMeta() == null) {
            return "";
        }
        return stack.getItemMeta().getPersistentDataContainer().getOrDefault(instanceIdKey, PersistentDataType.STRING, "");
    }

    private static final class PlayerInventoryView {
        private final Player player;

        private PlayerInventoryView(Player player) {
            this.player = player;
        }

        private int size() {
            return player.getInventory().getSize() + 1;
        }

        private ItemStack get(int slot) {
            return slot == player.getInventory().getSize()
                    ? player.getInventory().getItemInOffHand()
                    : player.getInventory().getItem(slot);
        }

        private void set(int slot, ItemStack item) {
            if (slot == player.getInventory().getSize()) {
                player.getInventory().setItemInOffHand(item);
            } else {
                player.getInventory().setItem(slot, item);
            }
        }
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
