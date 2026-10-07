"""Execute the real Paper replica item adapter using an isolated item API boundary."""
from pathlib import Path
import subprocess

import pytest

from tests.test_wave7_echo_loadout import BASE


def item_api_sources():
    return {
        "org/bukkit/Material.java": '''package org.bukkit;
public enum Material {AIR(0,64),IRON_SWORD(250,1),SHIELD(336,1),GOLDEN_APPLE(0,64),ARROW(0,64),TNT(0,64),CROSSBOW(465,1),BOW(384,1),IRON_HELMET(165,1),IRON_CHESTPLATE(240,1),IRON_LEGGINGS(225,1),IRON_BOOTS(195,1);
private final int durability,stack;Material(int d,int s){durability=d;stack=s;}public short getMaxDurability(){return (short)durability;}
public int getMaxStackSize(){return stack;}public boolean isAir(){return this==AIR;}}''',
        "org/bukkit/NamespacedKey.java": '''package org.bukkit;
public record NamespacedKey(String namespace,String key){public static NamespacedKey fromString(String value){String[] p=value.split(":",-1);return new NamespacedKey(p[0],p[1]);}
public static NamespacedKey minecraft(String key){return new NamespacedKey("minecraft",key);}
public String toString(){return namespace+":"+key;}}''',
        "org/bukkit/Registry.java": '''package org.bukkit;
public class Registry {public static final Enchants ENCHANTMENT=new Enchants();public static class Enchants {
public org.bukkit.enchantments.Enchantment get(NamespacedKey key){return new org.bukkit.enchantments.Enchantment(key);}}}''',
        "org/bukkit/enchantments/Enchantment.java": '''package org.bukkit.enchantments;
public record Enchantment(org.bukkit.NamespacedKey key){public org.bukkit.NamespacedKey getKey(){return key;}
public boolean canEnchantItem(org.bukkit.inventory.ItemStack item){return true;}}''',
        "org/bukkit/persistence/PersistentDataContainer.java": '''package org.bukkit.persistence;
public class PersistentDataContainer {public java.util.Set<org.bukkit.NamespacedKey> keys=new java.util.HashSet<>();
public java.util.Set<org.bukkit.NamespacedKey> getKeys(){return keys;}}''',
        "org/bukkit/inventory/meta/ItemMeta.java": '''package org.bukkit.inventory.meta;
public interface ItemMeta {org.bukkit.persistence.PersistentDataContainer getPersistentDataContainer();
boolean hasCustomModelData();boolean hasAttributeModifiers();boolean isUnbreakable();boolean hasMaxStackSize();boolean hasFood();boolean hasTool();
boolean hasDisplayName();String getDisplayName();void setDisplayName(String name);
java.util.Map<org.bukkit.enchantments.Enchantment,Integer> getEnchants();boolean addEnchant(org.bukkit.enchantments.Enchantment e,int level,boolean unsafe);}''',
        "org/bukkit/inventory/meta/Damageable.java": '''package org.bukkit.inventory.meta;
public interface Damageable extends ItemMeta {int getDamage();void setDamage(int damage);boolean hasMaxDamage();}''',
        "org/bukkit/inventory/meta/CrossbowMeta.java": '''package org.bukkit.inventory.meta;
public interface CrossbowMeta extends ItemMeta {boolean hasChargedProjectiles();}''',
        "org/bukkit/inventory/meta/BlockStateMeta.java": "package org.bukkit.inventory.meta;public interface BlockStateMeta extends ItemMeta {}",
        "org/bukkit/inventory/ItemStack.java": '''package org.bukkit.inventory;
public class ItemStack {public org.bukkit.Material material;public int amount;public Meta meta=new Meta();
public ItemStack(org.bukkit.Material m){this(m,1);}public ItemStack(org.bukkit.Material m,int n){material=m;amount=n;}
public org.bukkit.Material getType(){return material;}public int getAmount(){return amount;}
public org.bukkit.inventory.meta.ItemMeta getItemMeta(){return meta;}public boolean setItemMeta(org.bukkit.inventory.meta.ItemMeta m){meta=(Meta)m;return true;}
public static class Meta implements org.bukkit.inventory.meta.Damageable,org.bukkit.inventory.meta.CrossbowMeta {
public int damage;public String name="";public boolean custom,attribute,unbreakable,charged,maximum,food,tool,stack;
public org.bukkit.persistence.PersistentDataContainer pdc=new org.bukkit.persistence.PersistentDataContainer();
public java.util.Map<org.bukkit.enchantments.Enchantment,Integer> enchantments=new java.util.HashMap<>();
public org.bukkit.persistence.PersistentDataContainer getPersistentDataContainer(){return pdc;}
public boolean hasCustomModelData(){return custom;}public boolean hasAttributeModifiers(){return attribute;}
public boolean isUnbreakable(){return unbreakable;}public boolean hasMaxStackSize(){return stack;}public boolean hasFood(){return food;}public boolean hasTool(){return tool;}
public boolean hasDisplayName(){return !name.isEmpty();}public String getDisplayName(){return name;}public void setDisplayName(String n){name=n;}
public int getDamage(){return damage;}public void setDamage(int d){damage=d;}public boolean hasMaxDamage(){return maximum;}
public boolean hasChargedProjectiles(){return charged;}
public java.util.Map<org.bukkit.enchantments.Enchantment,Integer> getEnchants(){return enchantments;}
public boolean addEnchant(org.bukkit.enchantments.Enchantment e,int level,boolean unsafe){enchantments.put(e,level);return true;}}
}''',
        "org/bukkit/inventory/PlayerInventory.java": '''package org.bukkit.inventory;
public interface PlayerInventory {ItemStack[] getStorageContents();int getHeldItemSlot();ItemStack getBoots();ItemStack getLeggings();ItemStack getChestplate();ItemStack getHelmet();ItemStack getItemInOffHand();}''',
        "org/bukkit/inventory/EntityEquipment.java": '''package org.bukkit.inventory;
public interface EntityEquipment {void setBoots(ItemStack i);void setLeggings(ItemStack i);void setChestplate(ItemStack i);void setHelmet(ItemStack i);
void setItemInMainHand(ItemStack i);void setItemInOffHand(ItemStack i);
ItemStack getItemInMainHand();ItemStack getItemInOffHand();
void setHelmetDropChance(float v);void setChestplateDropChance(float v);void setLeggingsDropChance(float v);void setBootsDropChance(float v);
void setItemInMainHandDropChance(float v);void setItemInOffHandDropChance(float v);}''',
    }


@pytest.fixture(scope="module")
def replica_checks(tmp_path_factory):
    directory = tmp_path_factory.mktemp("echo-replica-items")
    adapter = BASE / "runtime/wave7/EchoReplicaInventory.java"
    assert adapter.exists(), "Echo still equips fresh fixed gear instead of a safe frozen copy"
    stubs = item_api_sources()
    paths = []
    for name, text in stubs.items():
        path = directory / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
        paths.append(str(path))
    harness = directory / "EchoReplicaChecks.java"
    harness.write_text(r'''
import java.util.*;import org.bukkit.*;import org.bukkit.inventory.*;
import me.copimine.endevent.runtime.wave7.EchoReplicaInventory;
import me.copimine.endevent.domain.wave7.EchoLoadoutState;
public class EchoReplicaChecks {
    static void check(boolean v,String why){if(!v)throw new AssertionError(why);}
    static class Inventory implements PlayerInventory {
        ItemStack[] slots=new ItemStack[36];ItemStack off=new ItemStack(Material.SHIELD);int reads;
        public ItemStack[] getStorageContents(){reads++;return slots;}public int getHeldItemSlot(){return 0;}
        public ItemStack getBoots(){return null;}public ItemStack getLeggings(){return null;}public ItemStack getChestplate(){return null;}
        public ItemStack getHelmet(){return null;}public ItemStack getItemInOffHand(){return off;}
    }
    static EchoReplicaInventory capture(Inventory source){return EchoReplicaInventory.capture(source,new UUID(0,1),7,new UUID(0,3),new UUID(0,2));}
    public static void main(String[] args)throws Exception{
        var source=new Inventory();var sword=new ItemStack(Material.IRON_SWORD);sword.meta.damage=123;sword.meta.name="Synthetic fixture";
        sword.meta.enchantments.put(new org.bukkit.enchantments.Enchantment(NamespacedKey.fromString("minecraft:sharpness")),3);
        source.slots[0]=sword;source.slots[1]=new ItemStack(Material.GOLDEN_APPLE,5);source.slots[2]=new ItemStack(Material.ARROW,2);
        if(args[0].equals("copy")){
            var replica=capture(source);var item=replica.stack(0);
            check(item!=sword&&item.meta!=sword.meta&&item.meta.damage==0,"fresh repaired replica, never live slot handle");
            check(sword.meta.damage==123&&sword.getAmount()==1&&source.off.getAmount()==1,"original inventory mutated");
            item.meta.damage=100;source.slots[0]=new ItemStack(Material.TNT);
            check(replica.stack(0).getType()==Material.IRON_SWORD&&replica.stack(0).meta.damage==0,"projection/owner switch changed frozen item");
            check(replica.state().remaining(1)==5&&replica.state().remaining(41)==2&&source.reads==1,"capture only once with two extras");
        }else if(args[0].equals("custom")){
            for(int flag=0;flag<7;flag++){
                var s=new ItemStack(Material.IRON_SWORD);source.slots[0]=s;
                switch(flag){case 0:s.meta.pdc.keys.add(NamespacedKey.fromString("copimineartifacts:artifact_item_id"));break;
                case 1:s.meta.attribute=true;break;case 2:s.meta.unbreakable=true;break;case 3:s.meta.custom=true;break;
                case 4:s.meta.maximum=true;break;case 5:s.meta.food=true;break;case 6:s.meta.tool=true;break;}
                var r=capture(source);check(!r.state().item(0).usable()&&r.stack(0).getType()==Material.AIR,"privileged metadata created usable replica: "+flag);
            }
            var crossbow=new ItemStack(Material.CROSSBOW);crossbow.meta.charged=true;source.slots[0]=crossbow;
            check(capture(source).stack(0).getType()==Material.AIR,"charged nested projectiles cloned");
        }else if(args[0].equals("custom-stack")){
            var customSword=new ItemStack(Material.IRON_SWORD,2);customSword.meta.stack=true;customSword.meta.damage=123;
            source.slots[0]=customSword;
            var blocked=capture(source);
            check(!blocked.state().item(0).usable()&&blocked.stack(0).getType()==Material.AIR,"custom stack became executable replica");
            check(blocked.state().item(0).amount()==1&&blocked.state().item(0).damage()==0,"blocked damageable descriptor must be bounded/inert");
            check(customSword.getAmount()==2&&customSword.meta.damage==123,"custom source stack changed");
            var customFood=new ItemStack(Material.GOLDEN_APPLE,99);customFood.meta.stack=true;source.slots[0]=customFood;
            var foodBlocked=capture(source);
            check(!foodBlocked.state().item(0).usable()&&foodBlocked.state().item(0).amount()==64,"oversized custom food not safely recorded");
            check(foodBlocked.state().remaining(41)==2&&customFood.getAmount()==99,"custom mapping altered real stack or bonus");
            source.slots[0]=new ItemStack(Material.IRON_SWORD,2);
            try{capture(source);throw new AssertionError("invalid ordinary vanilla stack accepted");}catch(IllegalArgumentException expected){}
        }else if(args[0].equals("restore")){
            var replica=capture(source);replica.state().consume(41,1,0);replica.state().damage(0,17,1);
            var restored=EchoReplicaInventory.restore(replica.state().encode(),new UUID(0,1),7,new UUID(0,3),new UUID(0,2),2);
            check(restored.stack(0).meta.damage==17&&restored.stack(41).getAmount()==1,"restored native gear repaired/refilled");
            var forged=new HashMap<>(replica.state().encode());forged.put("slot.0.maximum","9999");
            try{EchoReplicaInventory.restore(forged,new UUID(0,1),7,new UUID(0,3),new UUID(0,2),2);throw new AssertionError("forged native durability");}catch(IllegalArgumentException expected){}
        }else if(args[0].equals("native-wear")){
            var replica=capture(source);var result=replica.stack(40);result.meta.damage=5;
            boolean applied=false;
            try{applied=(Boolean)EchoReplicaInventory.class.getMethod("recordNativeWear",int.class,ItemStack.class,long.class).invoke(replica,40,result,0L);}
            catch(NoSuchMethodException absent){}
            check(applied&&replica.stack(40).meta.damage==5,"native shield wear never reaches finite loadout");
            var restore=EchoReplicaInventory.restore(replica.state().encode(),new UUID(0,1),7,new UUID(0,3),new UUID(0,2),1);
            check(restore.stack(40).meta.damage==5&&source.off.meta.damage==0,"native wear restored/repaired or changed original");
            check(!(Boolean)EchoReplicaInventory.class.getMethod("recordNativeWear",int.class,ItemStack.class,long.class).invoke(replica,40,result,0L),"stale native wear receipt accepted");
            check((Boolean)EchoReplicaInventory.class.getMethod("recordNativeWear",int.class,ItemStack.class,long.class).invoke(replica,40,new ItemStack(Material.AIR),1L),"native break rejected");
            check(replica.stack(40).getType()==Material.AIR&&replica.state().find(EchoLoadoutState.Kind.SHIELD)<0,"broken shield respawned");
        }else if(args[0].equals("native-wear-invalid")){
            var replica=capture(source);var method=EchoReplicaInventory.class.getMethod("recordNativeWear",int.class,ItemStack.class,long.class);
            check((Boolean)method.invoke(replica,40,replica.stack(40),0L)&&replica.state().revision()==0,"native Unbreaking zero outcome must remain zero wear");
            var wrong=replica.stack(0);wrong.meta.damage=8;
            check(!(Boolean)method.invoke(replica,40,wrong,0L),"wrong native material rewrote replica shield");
            var regressed=replica.stack(40);regressed.meta.damage=5;check((Boolean)method.invoke(replica,40,regressed,0L),"initial native wear");
            regressed.meta.damage=2;check(!(Boolean)method.invoke(replica,40,regressed,1L)&&replica.stack(40).meta.damage==5,"native outcome repaired consumed wear");
            var oversized=replica.stack(40);oversized.meta.damage=9999;
            check(!(Boolean)method.invoke(replica,40,oversized,1L)&&replica.state().revision()==1,"invalid native wear advanced state");
        }else throw new AssertionError(args[0]);
    }
}
''', encoding="utf-8")
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(directory), *paths,
                             str(BASE / "domain/wave7/EchoLoadoutState.java"), str(adapter), str(harness)],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    return directory


@pytest.mark.parametrize("scenario", ["copy", "custom", "custom-stack", "restore", "native-wear", "native-wear-invalid"])
def test_native_replica_inventory(replica_checks, scenario):
    result = subprocess.run(["java", "-cp", str(replica_checks), "EchoReplicaChecks", scenario],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
