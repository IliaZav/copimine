package me.copimine.endevent.runtime;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** Per-lethal-event ownership only; never restores or persists an inventory. */
public final class EventDeathProtectionListener implements Listener {
    private final Predicate<PlayerDeathEvent> eligibility;
    private final Logger logger;
    private final Map<PlayerDeathEvent, Receipt> receipts = new WeakHashMap<>();

    public EventDeathProtectionListener(Predicate<PlayerDeathEvent> eligibility, Logger logger) {
        this.eligibility = eligibility;
        this.logger = logger;
    }

    /** First-party recovery integrations query before their own death mutations. */
    public boolean protects(PlayerDeathEvent event) {
        return event != null && !event.isCancelled()
                && (receipts.containsKey(event) || eligibility.test(event));
    }

    /**
     * A known delayed emitter must never capture the retained inventory.
     * Its callback sees only independent drops. Restore the owned event view
     * in finally; ordinary later listeners and HIGHEST retain their semantics.
     */
    public void withRetainedDropsHidden(PlayerDeathEvent event, Runnable forwarder) {
        if (event.isCancelled()) return;
        Receipt receipt = receipts.get(event);
        if (receipt == null) {
            forwarder.run();
            return;
        }
        List<HiddenDrop> hidden = new ArrayList<>();
        ListIterator<ItemStack> iterator = event.getDrops().listIterator();
        int ordinal = 0;
        while (iterator.hasNext()) {
            ItemStack drop = iterator.next();
            int index = ordinal++;
            OwnedDrop owned = receipt.ownedDrops.get(drop);
            if (owned == null || !owned.descriptor().isSimilar(drop)) continue;
            int count = Math.min(Math.max(0, drop.getAmount()), owned.count());
            if (count <= 0) continue;
            ItemStack retained = drop;
            if (count == drop.getAmount()) {
                iterator.remove();
            } else {
                ItemStack remainder = drop.clone();
                remainder.setAmount(drop.getAmount() - count);
                iterator.set(remainder);
                retained = drop.clone();
                retained.setAmount(count);
                receipt.ownedDrops.put(retained, new OwnedDrop(retained.clone(), count));
            }
            hidden.add(new HiddenDrop(index, retained));
        }
        try {
            forwarder.run();
        } finally {
            for (HiddenDrop drop : hidden) {
                event.getDrops().add(Math.min(drop.index(), event.getDrops().size()), drop.stack());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void capture(PlayerDeathEvent event) {
        if (event.isCancelled() || receipts.containsKey(event) || !eligibility.test(event)) return;
        PlayerInventory inventory = event.getEntity().getInventory();
        List<ItemStack> budget = new ArrayList<>();
        addCurrentSlots(budget, inventory.getStorageContents());
        addCurrentSlots(budget, inventory.getArmorContents());
        addCurrentSlots(budget, inventory.getExtraContents());
        Map<ItemStack, OwnedDrop> owned = new IdentityHashMap<>();
        for (ItemStack drop : event.getDrops()) {
            if (drop == null || drop.getType() == Material.AIR || drop.getAmount() <= 0) continue;
            int available = drop.getAmount();
            int matched = 0;
            for (ItemStack slot : budget) {
                if (slot.getAmount() <= 0 || !slot.isSimilar(drop)) continue;
                int count = Math.min(available, slot.getAmount());
                slot.setAmount(slot.getAmount() - count); // detached descriptors only
                matched += count;
                available -= count;
                if (available == 0) break;
            }
            if (matched > 0) owned.put(drop, new OwnedDrop(drop.clone(), matched));
        }
        receipts.put(event, new Receipt(owned));
        logger.info("END_RIFT_DEATH_CAPTURE player=" + event.getEntity().getUniqueId()
                + " keep_inventory_before=" + event.getKeepInventory()
                + " drops=" + event.getDrops().size() + " inventory_origins=" + owned.size()
                + " drop_list=" + event.getDrops().getClass().getName());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void retain(PlayerDeathEvent event) {
        if (event.isCancelled()) {
            receipts.remove(event);
            return;
        }
        Receipt receipt = receipts.get(event);
        if (receipt == null) return;
        logger.info("END_RIFT_DEATH_APPLY player=" + event.getEntity().getUniqueId()
                + " drops_before=" + event.getDrops().size()
                + " captured_origins=" + receipt.ownedDrops.size());
        event.setKeepInventory(true);
        if (receipt.applied) return;
        ListIterator<ItemStack> drops = event.getDrops().listIterator();
        while (drops.hasNext()) {
            ItemStack drop = drops.next();
            OwnedDrop original = receipt.ownedDrops.get(drop);
            if (original == null || !original.descriptor().isSimilar(drop)) continue;
            int removed = Math.min(Math.max(0, drop.getAmount()), original.count());
            if (removed <= 0) continue;
            receipt.removedItems += removed;
            if (removed == drop.getAmount()) {
                drops.remove();
            } else {
                // A CraftItemStack can share its handle with a live slot.
                // Replace the drop instead of changing the retained inventory.
                ItemStack remainder = drop.clone();
                remainder.setAmount(drop.getAmount() - removed);
                drops.set(remainder);
            }
        }
        // Pinned Paper/Purpur 1.21.1 skips processKeep entirely when keepInventory
        // is true. Leave getItemsToKeep and all XP fields under their existing
        // owners; do not reinsert items or erase independent reward semantics.
        receipt.applied = true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void finish(PlayerDeathEvent event) {
        Receipt receipt = receipts.remove(event);
        if (receipt != null && receipt.applied && !event.isCancelled()) {
            logger.info("END_RIFT_INVENTORY_RETAINED player=" + event.getEntity().getUniqueId()
                    + " removed_inventory_drop_items=" + receipt.removedItems
                    + " remaining_independent_drops=" + event.getDrops().size());
        }
    }

    public void clear() { receipts.clear(); }

    private static void addCurrentSlots(List<ItemStack> target, ItemStack[] slots) {
        if (slots == null) return;
        for (ItemStack slot : slots) {
            if (slot != null && slot.getType() != Material.AIR && slot.getAmount() > 0) {
                target.add(slot.clone());
            }
        }
    }

    private record OwnedDrop(ItemStack descriptor, int count) { }
    private record HiddenDrop(int index, ItemStack stack) { }

    private static final class Receipt {
        private final Map<ItemStack, OwnedDrop> ownedDrops;
        private boolean applied;
        private int removedItems;
        private Receipt(Map<ItemStack, OwnedDrop> ownedDrops) { this.ownedDrops = ownedDrops; }
    }
}
