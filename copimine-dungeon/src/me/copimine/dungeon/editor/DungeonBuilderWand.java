package me.copimine.dungeon.editor;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The branded builder wand (spec OV4.3).
 *
 * <p>Identity is a custom-model-data item whose PDC carries a server-only MAC
 * (see {@link WandToken}). A player can forge the name, lore, custom-model-data
 * and even copy the texture, but cannot produce the token, so
 * {@link #isAuthentic} fails and no building privilege is granted. This is why
 * privileges are never derived from the item's appearance.</p>
 */
public final class DungeonBuilderWand {

    private final Plugin plugin;
    private final String secret;
    private final Material material;
    private final int customModelData;
    private final NamespacedKey tokenKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey versionKey;

    public DungeonBuilderWand(Plugin plugin, String secret, Material material, int customModelData) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.secret = Objects.requireNonNull(secret, "secret");
        this.material = Objects.requireNonNull(material, "material");
        this.customModelData = customModelData;
        this.tokenKey = new NamespacedKey(plugin, "wand_token");
        this.ownerKey = new NamespacedKey(plugin, "wand_owner");
        this.versionKey = new NamespacedKey(plugin, "wand_version");
    }

    /** Mints a fresh authentic wand owned by the given player. */
    public ItemStack create(UUID ownerId, String ownerName) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                "&5Жезл строителя CopiMine"));
        meta.setLore(List.of(
                ChatColor.translateAlternateColorCodes('&', "&7Редактор подземелий"),
                ChatColor.translateAlternateColorCodes('&', "&8Метка подлинности обязательна")));
        meta.setCustomModelData(customModelData);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(tokenKey, PersistentDataType.STRING,
                WandToken.issue(secret, ownerId.toString(), WandToken.TOKEN_VERSION));
        pdc.set(ownerKey, PersistentDataType.STRING, ownerId.toString());
        pdc.set(versionKey, PersistentDataType.INTEGER, WandToken.TOKEN_VERSION);
        item.setItemMeta(meta);
        return item;
    }

    /** True only for an unmodified wand whose server MAC matches the holder. */
    public boolean isAuthentic(ItemStack item, UUID holder) {
        if (item == null || item.getType() != material || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String token = pdc.get(tokenKey, PersistentDataType.STRING);
        String owner = pdc.get(ownerKey, PersistentDataType.STRING);
        Integer version = pdc.get(versionKey, PersistentDataType.INTEGER);
        if (token == null || owner == null || version == null) {
            return false;
        }
        if (version != WandToken.TOKEN_VERSION) {
            return false;
        }
        if (holder != null && !owner.equals(holder.toString())) {
            return false;
        }
        return WandToken.verify(secret, owner, version, token);
    }

    /** Cheap structural check used only for feedback, never for authorization. */
    public boolean looksLikeWand(ItemStack item) {
        if (item == null || item.getType() != material || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta() != null
                && item.getItemMeta().getPersistentDataContainer().has(tokenKey, PersistentDataType.STRING);
    }

    public Plugin plugin() {
        return plugin;
    }
}
