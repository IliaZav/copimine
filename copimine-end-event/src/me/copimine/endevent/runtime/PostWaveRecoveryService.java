package me.copimine.endevent.runtime;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import me.copimine.endevent.domain.PostWaveRecoveryPolicy;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

/** Applies the bounded recovery that belongs between Wave 7 and the boss. */
public final class PostWaveRecoveryService {
    public Result recover(Collection<? extends Player> participants) {
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
            AttributeInstance maxHealthAttribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (maxHealthAttribute != null) {
                double maxHealth = maxHealthAttribute.getValue();
                if (Double.isFinite(maxHealth) && maxHealth > 0.0D
                        && player.getHealth() < maxHealth) {
                    player.setHealth(maxHealth);
                    healedPlayers++;
                }
            }
            org.bukkit.inventory.PlayerInventory inventory = player.getInventory();
            if (inventory == null) continue;
            Set<ItemStack> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            repairedItems += repairItems(inventory.getStorageContents(), visited);
            repairedItems += repairItems(inventory.getArmorContents(), visited);
            repairedItems += repairItems(inventory.getExtraContents(), visited);
        }
        return new Result(healedPlayers, repairedItems);
    }

    private int repairItems(ItemStack[] items, Set<ItemStack> visited) {
        if (items == null || items.length == 0) return 0;
        int repaired = 0;
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir() || !visited.add(item)) continue;
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

    public record Result(int healedPlayers, int repairedItems) {
        public Result {
            healedPlayers = Math.max(0, healedPlayers);
            repairedItems = Math.max(0, repairedItems);
        }
    }
}
