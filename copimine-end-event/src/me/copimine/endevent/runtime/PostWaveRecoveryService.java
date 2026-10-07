package me.copimine.endevent.runtime;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import me.copimine.endevent.domain.PostWaveRecoveryPolicy;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import me.copimine.endevent.runtime.compat.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** Applies the bounded recovery that belongs between Wave 7 and the boss. */
public final class PostWaveRecoveryService {
    private static final NamespacedKey RECEIPT = new NamespacedKey("copimine", "end_wave7_recovery");

    public Result recover(String eventId, long generation, Collection<? extends Player> participants) {
        if (eventId == null || eventId.isBlank() || generation <= 0L) {
            throw new IllegalArgumentException("recovery must belong to an encounter attempt");
        }
        String receipt = "1:" + eventId + ":" + generation;
        int healedPlayers = 0;
        int repairedItems = 0;
        if (participants == null || participants.isEmpty()) {
            return new Result(0, 0);
        }
        for (Player player : participants) {
            if (player == null || !player.isOnline() || player.isDead()
                    || player.getHealth() <= 0.0D
                    || player.getGameMode() == GameMode.SPECTATOR
                    || player.getGameMode() == GameMode.CREATIVE) {
                continue;
            }
            PersistentDataContainer metadata = player.getPersistentDataContainer();
            String previousReceipt = metadata.get(RECEIPT, PersistentDataType.STRING);
            if (receipt.equals(previousReceipt)) continue;
            org.bukkit.inventory.PlayerInventory inventory = player.getInventory();
            if (inventory == null) continue;
            double previousHealth = player.getHealth();
            ItemStack[] storage = inventory.getStorageContents();
            ItemStack[] armor = inventory.getArmorContents();
            ItemStack[] extra = inventory.getExtraContents();
            ItemStack[] originalStorage = copyItems(storage);
            ItemStack[] originalArmor = copyItems(armor);
            ItemStack[] originalExtra = copyItems(extra);
            int healed = 0;
            int repaired;
            try {
                AttributeInstance maxHealthAttribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                if (maxHealthAttribute != null) {
                    double maxHealth = maxHealthAttribute.getValue();
                    if (Double.isFinite(maxHealth) && maxHealth > 0.0D
                            && player.getHealth() < maxHealth) {
                        player.setHealth(maxHealth);
                        healed = 1;
                    }
                }
                Set<ItemStack> visited = Collections.newSetFromMap(new IdentityHashMap<>());
                repaired = repairItems(storage, visited) + repairItems(armor, visited) + repairItems(extra, visited);
                inventory.setStorageContents(storage);
                inventory.setArmorContents(armor);
                inventory.setExtraContents(extra);
                metadata.set(RECEIPT, PersistentDataType.STRING, receipt);
                // Player state and receipt are saved together. A service/plugin restart
                // can therefore neither repeat a committed repair nor heal a second time.
                player.saveData();
            } catch (RuntimeException error) {
                rollback(() -> player.setHealth(previousHealth), error);
                rollback(() -> inventory.setStorageContents(originalStorage), error);
                rollback(() -> inventory.setArmorContents(originalArmor), error);
                rollback(() -> inventory.setExtraContents(originalExtra), error);
                rollback(() -> {
                    if (previousReceipt == null) metadata.remove(RECEIPT);
                    else metadata.set(RECEIPT, PersistentDataType.STRING, previousReceipt);
                }, error);
                throw error;
            }
            healedPlayers += healed;
            repairedItems += repaired;
        }
        return new Result(healedPlayers, repairedItems);
    }

    private static void rollback(Runnable action, RuntimeException originalError) {
        try {
            action.run();
        } catch (RuntimeException rollbackError) {
            if (rollbackError != originalError) originalError.addSuppressed(rollbackError);
        }
    }

    private int repairItems(ItemStack[] items, Set<ItemStack> visited) {
        if (items == null || items.length == 0) return 0;
        int repaired = 0;
        for (ItemStack item : items) {
            if (item == null || !visited.add(item)) continue;
            int maxDurability = item.getType().getMaxDurability();
            if (maxDurability <= 0) continue;
            ItemMeta itemMeta = item.getItemMeta();
            if (!(itemMeta instanceof Damageable damageable)) continue;
            int currentDamage = damageable.getDamage();
            int repairedDamage = PostWaveRecoveryPolicy.repairedDamage(currentDamage, maxDurability);
            if (repairedDamage == currentDamage) continue;
            damageable.setDamage(repairedDamage);
            item.setItemMeta(itemMeta);
            repaired++;
        }
        return repaired;
    }

    private ItemStack[] copyItems(ItemStack[] items) {
        if (items == null) return null;
        ItemStack[] copy = items.clone();
        for (int index = 0; index < copy.length; index++) {
            if (copy[index] != null) copy[index] = copy[index].clone();
        }
        return copy;
    }

    public record Result(int healedPlayers, int repairedItems) {
        public Result {
            healedPlayers = Math.max(0, healedPlayers);
            repairedItems = Math.max(0, repairedItems);
        }
    }
}
