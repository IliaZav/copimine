package me.copimine.narcotics.item;

import me.copimine.narcotics.CopiMineNarcotics;
import me.copimine.narcotics.config.NarcoticsConfigService;
import me.copimine.narcotics.db.NarcoticsDatabase;
import me.copimine.narcotics.model.NarcoticDefinition;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public final class NarcoticItemFactory {
    private final CopiMineNarcotics plugin;
    private NarcoticsConfigService configService;
    private final NamespacedKey itemTypeKey;
    private final NamespacedKey narcoticIdKey;
    private final NamespacedKey versionKey;
    private final NamespacedKey officialKey;
    private final NamespacedKey instanceIdKey;
    private final NamespacedKey signatureVersionKey;
    private final NamespacedKey narcoticSignatureKey;
    private final NarcoticsDatabase database;
    private final byte[] signingSecret;
    private static final int SIGNATURE_VERSION = 1;

    public NarcoticItemFactory(CopiMineNarcotics plugin, NarcoticsConfigService configService,
                               NarcoticsDatabase database) {
        this.plugin = plugin;
        this.configService = configService;
        this.database = database;
        this.signingSecret = loadSigningSecret(plugin);
        itemTypeKey = new NamespacedKey(plugin, "copimine_item_type");
        narcoticIdKey = new NamespacedKey(plugin, "narcotic_id");
        versionKey = new NamespacedKey(plugin, "narcotic_version");
        officialKey = new NamespacedKey(plugin, "official");
        instanceIdKey = new NamespacedKey(plugin, "narcotic_instance_id");
        signatureVersionKey = new NamespacedKey(plugin, "narcotic_signature_version");
        narcoticSignatureKey = new NamespacedKey(plugin, "narcotic_signature");
    }

    public void reload(NarcoticsConfigService configService) {
        this.configService = configService;
    }

    public ItemStack createOfficialItem(NarcoticDefinition definition, int amount) {
        return createOfficialItem(definition, amount, UUID.randomUUID().toString(), true);
    }

    /** Rebuild a signed item while retaining an already authenticated identity. */
    private ItemStack createOfficialItem(NarcoticDefinition definition, int amount, String existingInstanceId) {
        return createOfficialItem(definition, amount, existingInstanceId, false);
    }

    private ItemStack createOfficialItem(NarcoticDefinition definition, int amount, String instanceId, boolean registerIssued) {
        Material base = definition.material() == null ? Material.PAPER : definition.material();
        if (base == Material.AIR) {
            base = definition.fallbackMaterial() == null ? Material.PAPER : definition.fallbackMaterial();
        }
        // A server-issued narcotic is intentionally non-stackable. A single
        // cryptographic identity must never be copied to several physical
        // units through ItemStack.amount.
        ItemStack stack = new ItemStack(base, 1);
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
        int itemVersion = configService.narcoticVersion();
        meta.getPersistentDataContainer().set(instanceIdKey, PersistentDataType.STRING, instanceId);
        meta.getPersistentDataContainer().set(signatureVersionKey, PersistentDataType.INTEGER, SIGNATURE_VERSION);
        meta.getPersistentDataContainer().set(narcoticSignatureKey, PersistentDataType.STRING,
                sign(instanceId, definition.id(), base, itemVersion));
        stack.setItemMeta(meta);
        if (registerIssued) {
            database.registerIssuedInstance(instanceId, definition.id());
        }
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
        if (stack.getAmount() != 1 || !"RP_NARCOTIC".equals(type) || id == null || !hasOfficialFlag(meta)) {
            return null;
        }
        NarcoticDefinition definition = configService.items().get(id);
        if (definition == null) {
            return null;
        }
        String instanceId = meta.getPersistentDataContainer().get(instanceIdKey, PersistentDataType.STRING);
        Integer signatureVersion = meta.getPersistentDataContainer().get(signatureVersionKey, PersistentDataType.INTEGER);
        String signature = meta.getPersistentDataContainer().get(narcoticSignatureKey, PersistentDataType.STRING);
        if (instanceId == null || instanceId.isBlank() || signature == null || signature.isBlank()
                || signatureVersion == null || signatureVersion != SIGNATURE_VERSION) {
            return null;
        }
        try {
            UUID.fromString(instanceId);
        } catch (IllegalArgumentException invalidInstanceId) {
            return null;
        }
        if (stack.getType() != definition.material()) {
            return null;
        }
        if (version == null || version > configService.narcoticVersion()) {
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
        String expected = sign(instanceId, definition.id(), stack.getType(), version);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
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
        return resolveOfficial(stack) != null
                && instanceId.equals(candidateId)
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
        if (!leftovers.isEmpty()) {
            // Never turn a failed compensation into an untracked world item.
            // The caller must enqueue a durable refund when capacity is absent.
            plugin.getLogger().severe("Unable to restore a narcotic item: inventory is full; durable refund required.");
        }
        player.updateInventory();
    }

    public int migrateOfficialItems(Player player) {
        if (player == null) {
            return 0;
        }
        int updated = migrateInventory(player.getInventory());
        ItemStack offHand = player.getInventory().getItemInOffHand();
        NarcoticDefinition offHandDefinition = resolveOfficial(offHand);
        if (offHandDefinition != null) {
            player.getInventory().setItemInOffHand(createOfficialItem(offHandDefinition, 1, instanceId(offHand)));
            updated++;
        }
        updated += migrateInventory(player.getEnderChest());
        player.updateInventory();
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
        int limit = inventory instanceof PlayerInventory ? 36 : inventory.getSize();
        for (int index = 0; index < limit; index++) {
            ItemStack current = inventory.getItem(index);
            NarcoticDefinition definition = resolveOfficial(current);
            if (definition == null) {
                continue;
            }
            String currentInstanceId = instanceId(current);
            inventory.setItem(index, createOfficialItem(definition, 1, currentInstanceId));
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

    private String sign(String instanceId, String narcoticId, Material material, int version) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret, "HmacSHA256"));
            String payload = instanceId + "|" + narcoticId + "|" + material.name() + "|" + version
                    + "|signature-v" + SIGNATURE_VERSION;
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("Narcotic signing is unavailable; refusing to issue an item.", error);
        }
    }

    private byte[] loadSigningSecret(CopiMineNarcotics plugin) {
        String configured = System.getenv("COPIMINE_NARCOTICS_SIGNING_SECRET");
        if (configured != null && !configured.isBlank()) {
            try {
                byte[] decoded = Base64.getDecoder().decode(configured.trim());
                if (decoded.length >= 32) {
                    return decoded;
                }
            } catch (IllegalArgumentException ignored) {
                // Do not use a malformed or weak environment value.
            }
            throw new IllegalStateException("COPIMINE_NARCOTICS_SIGNING_SECRET must be base64 and at least 32 bytes.");
        }
        Path path = plugin.getDataFolder().toPath().resolve("narcotics-signing-secret.b64");
        try {
            Files.createDirectories(path.getParent());
            if (Files.isRegularFile(path)) {
                byte[] decoded = Base64.getDecoder().decode(Files.readString(path, StandardCharsets.US_ASCII).trim());
                if (decoded.length >= 32) {
                    return decoded;
                }
                throw new IllegalStateException("Narcotics signing secret is too short.");
            }
            byte[] generated = new byte[32];
            new SecureRandom().nextBytes(generated);
            String encoded = Base64.getEncoder().encodeToString(generated);
            Files.writeString(path, encoded + System.lineSeparator(), StandardCharsets.US_ASCII,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try (var channel = java.nio.channels.FileChannel.open(path, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            return generated;
        } catch (Exception error) {
            throw new IllegalStateException("Unable to load or persist the narcotics signing secret.", error);
        }
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private String plainName(String text) {
        return ChatColor.stripColor(color(text == null ? "" : text));
    }
}
