package me.copimine.endevent.runtime.wave7;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import me.copimine.endevent.domain.wave7.EchoLoadoutState;
import me.copimine.endevent.domain.wave7.EchoLoadoutState.Item;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

/** Reads once on the server thread; every projected item is built from safe plain fields. */
public final class EchoReplicaInventory {
    private final EchoLoadoutState state;
    private EchoReplicaInventory(EchoLoadoutState state) {
        this.state=state;
        // A persisted descriptor cannot invent a Minecraft material/durability/stack limit.
        for (int slot=0;slot<42;slot++) validateNative(state.item(slot));
    }
    public static EchoReplicaInventory capture(PlayerInventory inventory, UUID event, long attempt, UUID duel, UUID owner) {
        var storage=inventory.getStorageContents();
        if (storage.length!=36) throw new IllegalArgumentException("Expected vanilla 36-slot storage");
        var items=new ArrayList<Item>();
        for (ItemStack item:storage) items.add(describe(item));
        items.add(describe(inventory.getBoots())); items.add(describe(inventory.getLeggings()));
        items.add(describe(inventory.getChestplate())); items.add(describe(inventory.getHelmet()));
        items.add(describe(inventory.getItemInOffHand()));
        return new EchoReplicaInventory(EchoLoadoutState.create(event,attempt,duel,owner,items,inventory.getHeldItemSlot()));
    }
    public static EchoReplicaInventory restore(Map<String,String> encoded, UUID event, long attempt, UUID duel, UUID owner,
                                                long minimumRevision) {
        return new EchoReplicaInventory(EchoLoadoutState.restore(encoded,event,attempt,duel,owner,minimumRevision));
    }
    public EchoLoadoutState state() { return state; }
    /** Persist an observed native durability outcome; never repair or refill a replica. */
    public boolean recordNativeWear(int slot, ItemStack result, long expectedRevision) {
        if (slot < 0 || slot >= 42 || result == null || state.revision() != expectedRevision) return false;
        Item before = state.item(slot);
        if (!before.usable() || before.maximumDamage() == 0) return false;
        int damage;
        if (result.getType().isAir() || result.getAmount() == 0) damage = before.maximumDamage();
        else {
            if (!result.getType().name().equals(before.material()) || result.getAmount() != 1
                    || !(result.getItemMeta() instanceof Damageable nativeMeta)) return false;
            damage = nativeMeta.getDamage();
            if (damage < before.damage() || damage >= before.maximumDamage()) return false;
        }
        return damage == before.damage() || state.damage(slot, damage - before.damage(), expectedRevision);
    }
    private static Item describe(ItemStack stack) {
        if (stack==null || stack.getType().isAir() || stack.getAmount()==0) return Item.empty();
        Material material=stack.getType(); ItemMeta meta=stack.getItemMeta();
        boolean custom=meta!=null && (!meta.getPersistentDataContainer().getKeys().isEmpty()
                || meta.hasCustomModelData() || meta.hasAttributeModifiers() || meta.isUnbreakable()
                || meta.hasMaxStackSize() || meta.hasFood() || meta.hasTool()
                || meta instanceof Damageable d && d.hasMaxDamage()
                || meta instanceof CrossbowMeta c && c.hasChargedProjectiles() || meta instanceof BlockStateMeta);
        var enchantments=new LinkedHashMap<String,Integer>();
        if (meta!=null) for (var entry:meta.getEnchants().entrySet()) {
            String key=entry.getKey().getKey().toString();
            if (!EchoLoadoutState.safeEnchantment(key,entry.getValue()) || !entry.getKey().canEnchantItem(stack)) custom=true;
            else enchantments.put(key,entry.getValue());
        }
        int maximum=material.getMaxDurability();
        int amount=stack.getAmount();
        if (amount<1 || !custom && amount>material.getMaxStackSize())
            throw new IllegalArgumentException("Invalid source stack quantity");
        // Blocked custom stacks can legitimately override vanilla stack limits.
        // Record only an inert bounded descriptor; never copy their executable data.
        if (custom) amount=maximum>0 ? 1 : Math.min(amount,Math.min(64,material.getMaxStackSize()));
        int damage=!custom && meta instanceof Damageable d ? Math.max(0,Math.min(maximum,d.getDamage())) : 0;
        String name=meta!=null && meta.hasDisplayName() ? meta.getDisplayName() : "";
        if (name.length()>256) name=name.substring(0,256);
        return new Item(material.name(),amount,maximum,damage,custom?Map.of():enchantments,name,custom);
    }
    private static Material validateNative(Item item) {
        Material material;
        try { material=Material.valueOf(item.material()); }
        catch (IllegalArgumentException error) { throw new IllegalArgumentException("Unsupported native replica material",error); }
        if (item.amount()>material.getMaxStackSize() || item.maximumDamage()!=material.getMaxDurability())
            throw new IllegalArgumentException("Replica descriptor disagrees with native item limits");
        return material;
    }
    /** No source handle, PDC, nested data or arbitrary native ItemMeta is cloned. */
    public ItemStack stack(int slot) {
        Item item=state.item(slot); Material material=validateNative(item);
        if (!item.usable()) return new ItemStack(Material.AIR);
        var replica=new ItemStack(material,item.amount()); ItemMeta meta=replica.getItemMeta();
        if (meta==null) throw new IllegalArgumentException("Replica item has no supported native metadata");
        if (!item.name().isEmpty()) meta.setDisplayName(item.name());
        if (meta instanceof Damageable d) d.setDamage(item.damage());
        for (var entry:item.enchantments().entrySet()) {
            var enchantment=Registry.ENCHANTMENT.get(NamespacedKey.fromString(entry.getKey()));
            if (enchantment==null || !enchantment.canEnchantItem(replica) || !meta.addEnchant(enchantment,entry.getValue(),false))
                throw new IllegalArgumentException("Replica enchantment has no native combat adapter");
        }
        if (!replica.setItemMeta(meta)) throw new IllegalArgumentException("Replica metadata rejected");
        return replica;
    }
    public void equip(EntityEquipment equipment,int mainSlot,int offSlot) {
        equipment.setBoots(stack(36)); equipment.setLeggings(stack(37));
        equipment.setChestplate(stack(38)); equipment.setHelmet(stack(39));
        equipment.setItemInMainHand(mainSlot==-1?new ItemStack(Material.AIR):stack(mainSlot));
        equipment.setItemInOffHand(offSlot==-1?new ItemStack(Material.AIR):stack(offSlot));
    }
}
