"""Execute the real Paper carrier adapter with a narrow native-navigation boundary."""
from pathlib import Path
import subprocess

import pytest
from tests.test_wave7_echo_replica_inventory import item_api_sources

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "copimine-end-event/src/me/copimine/endevent"


@pytest.fixture(scope="module")
def carrier_probe(tmp_path_factory):
    directory = tmp_path_factory.mktemp("echo-carrier-adapter")
    # Keep this regression runnable in the static CI job before Paper is
    # installed. These boundaries follow the checked 1.21.1 API; the separate
    # plugin build compiles the same production adapter against actual Paper.
    classpath = str(directory)
    stubs = {
        "org/bukkit/NamespacedKey.java": '''package org.bukkit;
public record NamespacedKey(String namespace,String key){
public static NamespacedKey minecraft(String key){return new NamespacedKey("minecraft",key);}
public String toString(){return namespace+":"+key;}}''',
        "org/bukkit/World.java": "package org.bukkit; public interface World { NamespacedKey getKey();void playSound(Location l,Sound s,float v,float p); }",
        "org/bukkit/Sound.java": "package org.bukkit;public enum Sound {ENTITY_GENERIC_EAT,ENTITY_PLAYER_BURP}",
        "org/bukkit/potion/PotionEffectType.java": "package org.bukkit.potion;public enum PotionEffectType {REGENERATION,ABSORPTION}",
        "org/bukkit/potion/PotionEffect.java": "package org.bukkit.potion;public record PotionEffect(PotionEffectType type,int duration,int amplifier){}",
        "org/bukkit/Server.java": '''package org.bukkit; public interface Server {
com.destroystokyo.paper.entity.ai.MobGoals getMobGoals();java.util.logging.Logger getLogger();
String getName();String getVersion();String getBukkitVersion();}''',
        "org/bukkit/Bukkit.java": '''package org.bukkit; public class Bukkit {
private static Server server;public static com.destroystokyo.paper.entity.ai.MobGoals getMobGoals(){return server.getMobGoals();}}''',
        "org/bukkit/Material.java": "package org.bukkit; public enum Material { IRON_HELMET,IRON_CHESTPLATE,IRON_LEGGINGS,IRON_BOOTS,BOW,CROSSBOW,GOLDEN_APPLE,IRON_SWORD,SHIELD }",
        "org/bukkit/util/Vector.java": '''package org.bukkit.util; public class Vector {
double x,y,z;public Vector(){this(0,0,0);}public Vector(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
public Vector setY(double v){y=v;return this;}public Vector subtract(Vector v){x-=v.x;y-=v.y;z-=v.z;return this;}
public double lengthSquared(){return x*x+y*y+z*z;}}''',
        "org/bukkit/Location.java": '''package org.bukkit; public class Location implements Cloneable {
World world;double x,y,z;public Location(World w,double x,double y,double z){world=w;this.x=x;this.y=y;this.z=z;}
public Location clone(){return new Location(world,x,y,z);}public World getWorld(){return world;}
public Location add(double dx,double dy,double dz){x+=dx;y+=dy;z+=dz;return this;}
public double distanceSquared(Location l){return (x-l.x)*(x-l.x)+(y-l.y)*(y-l.y)+(z-l.z)*(z-l.z);}
public org.bukkit.util.Vector toVector(){return new org.bukkit.util.Vector(x,y,z);}
public Location setDirection(org.bukkit.util.Vector v){return this;}public float getYaw(){return 0;}public float getPitch(){return 0;}}''',
        "org/bukkit/entity/Pose.java": "package org.bukkit.entity; public enum Pose { STANDING,SNEAKING }",
        "org/bukkit/entity/Mob.java": "package org.bukkit.entity; public interface Mob {}",
        "org/bukkit/entity/Player.java": '''package org.bukkit.entity; public interface Player {
java.util.UUID getUniqueId();org.bukkit.World getWorld();org.bukkit.Location getEyeLocation();boolean isOnline();boolean isDead();org.bukkit.inventory.PlayerInventory getInventory();}''',
        "org/bukkit/entity/Pillager.java": '''package org.bukkit.entity; public interface Pillager extends Mob {
java.util.UUID getUniqueId();org.bukkit.World getWorld();org.bukkit.Location getLocation();org.bukkit.Location getEyeLocation();
org.bukkit.inventory.EntityEquipment getEquipment();com.destroystokyo.paper.entity.Pathfinder getPathfinder();
org.bukkit.util.Vector getVelocity();void setVelocity(org.bukkit.util.Vector v);boolean isOnGround();boolean isValid();boolean isDead();
double getHealth();void setHealth(double v);void damage(double v);void remove();
void setCanPickupItems(boolean v);void setRemoveWhenFarAway(boolean v);void setPersistent(boolean v);void setSilent(boolean v);
void setAI(boolean v);void setAware(boolean v);void setTarget(Player p);void setPose(Pose p,boolean fixed);void setRotation(float y,float p);void swingMainHand();}''',
        "org/bukkit/inventory/ItemStack.java": "package org.bukkit.inventory; public class ItemStack { public ItemStack(org.bukkit.Material material){} }",
        "org/bukkit/inventory/EntityEquipment.java": '''package org.bukkit.inventory; public interface EntityEquipment {
void setHelmetDropChance(float v);void setChestplateDropChance(float v);void setLeggingsDropChance(float v);void setBootsDropChance(float v);
void setItemInMainHandDropChance(float v);void setItemInOffHandDropChance(float v);void setHelmet(ItemStack v);void setChestplate(ItemStack v);
void setLeggings(ItemStack v);void setBoots(ItemStack v);void setItemInMainHand(ItemStack v);void setItemInOffHand(ItemStack v);}''',
        "com/destroystokyo/paper/entity/Pathfinder.java": '''package com.destroystokyo.paper.entity;
public interface Pathfinder {void stopPathfinding();boolean moveTo(org.bukkit.Location l,double speed);}''',
        "com/destroystokyo/paper/entity/ai/MobGoals.java": '''package com.destroystokyo.paper.entity.ai;
public interface MobGoals {void removeAllGoals(org.bukkit.entity.Mob mob);}''',
    }
    stubs.update(item_api_sources())
    stubs["org/bukkit/inventory/EquipmentSlot.java"] = "package org.bukkit.inventory;public enum EquipmentSlot {HAND,OFF_HAND}"
    stubs["org/bukkit/entity/Pillager.java"] = stubs["org/bukkit/entity/Pillager.java"].replace(
        "void swingMainHand();", "void swingMainHand();boolean addPotionEffect(org.bukkit.potion.PotionEffect effect);"
        "void startUsingItem(org.bukkit.inventory.EquipmentSlot hand);void clearActiveItem();"
        "boolean hasActiveItem();org.bukkit.inventory.EquipmentSlot getActiveItemHand();"
        "void damageItemStack(org.bukkit.inventory.EquipmentSlot hand,int amount);")
    stubs["org/bukkit/Sound.java"] = "package org.bukkit;public enum Sound {ENTITY_GENERIC_EAT,ENTITY_PLAYER_BURP,ITEM_SHIELD_BLOCK,ITEM_SHIELD_BREAK}"
    boundary_sources = []
    for name, source in stubs.items():
        path = directory / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source, encoding="utf-8")
        boundary_sources.append(str(path))
    probe = directory / "EchoCarrierChecks.java"
    probe.write_text(r'''
import java.lang.reflect.*;import java.util.*;import java.util.logging.*;
import org.bukkit.*;import org.bukkit.entity.*;import org.bukkit.inventory.*;
import com.destroystokyo.paper.entity.Pathfinder;
import com.destroystokyo.paper.entity.ai.MobGoals;
import me.copimine.endevent.domain.wave7.EchoPresentationProbeState;
import me.copimine.endevent.runtime.wave7.EchoPresentationProbe;
public class EchoCarrierChecks {
    static boolean aware,goals=true,removed,online=true,ownerAlive=true;static int moves,effects;static double health=20;
    static ItemStack mainItem,offItem;static org.bukkit.inventory.EquipmentSlot activeHand;static int wearCalls,raiseCalls;
    static UUID OWNER=new UUID(0,2),ACTOR=new UUID(0,3),EVENT=new UUID(0,1);
    static Object value(Class<?> type){
        if(!type.isPrimitive())return null;if(type==boolean.class)return false;if(type==int.class)return 0;
        if(type==double.class)return 0D;if(type==float.class)return 0F;if(type==long.class)return 0L;return null;
    }
    @SuppressWarnings("unchecked")static <T>T proxy(Class<T> type,InvocationHandler handler){
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler);
    }
    public static void main(String[] args)throws Exception{
        World world=proxy(World.class,(p,m,a)->switch(m.getName()){
            case "getKey"->NamespacedKey.minecraft("overworld");case "equals"->p==a[0];
            case "hashCode"->1;default->value(m.getReturnType());});
        Location location=new Location(world,5,68,5);
        MobGoals goalApi=proxy(MobGoals.class,(p,m,a)->{if(m.getName().equals("removeAllGoals"))goals=false;return value(m.getReturnType());});
        Server fixtureServer=proxy(Server.class,(p,m,a)->switch(m.getName()){
            case "getMobGoals"->goalApi;case "getLogger"->Logger.getLogger("echo-fixture");
            case "getName","getVersion","getBukkitVersion"->"echo-fixture";default->value(m.getReturnType());});
        // Test-only injection avoids Bukkit.setServer's ServiceLoader startup log.
        var serverField=Bukkit.class.getDeclaredField("server");serverField.setAccessible(true);
        serverField.set(null,fixtureServer);
        EntityEquipment gear=proxy(EntityEquipment.class,(p,m,a)->switch(m.getName()){
            case "setItemInMainHand"->{mainItem=(ItemStack)a[0];yield null;}
            case "setItemInOffHand"->{offItem=(ItemStack)a[0];yield null;}
            case "getItemInMainHand"->mainItem;case "getItemInOffHand"->offItem;default->value(m.getReturnType());});
        Pathfinder path=proxy(Pathfinder.class,(p,m,a)->{
            if(m.getName().equals("moveTo")){if(aware&&!goals)moves++;return aware&&!goals;}
            return value(m.getReturnType());});
        Pillager carrier=proxy(Pillager.class,(p,m,a)->switch(m.getName()){
            case "getLocation","getEyeLocation"->location.clone();case "getWorld"->world;
            case "getUniqueId"->ACTOR;case "getEquipment"->gear;case "getPathfinder"->path;
            case "getVelocity"->new org.bukkit.util.Vector();case "getHealth"->health;
            case "setAware"->{aware=(Boolean)a[0];yield null;}
            case "setHealth"->{health=(Double)a[0];yield null;}
            case "startUsingItem"->{activeHand=(org.bukkit.inventory.EquipmentSlot)a[0];raiseCalls++;yield null;}
            case "clearActiveItem"->{activeHand=null;yield null;}
            case "hasActiveItem"->activeHand!=null;case "getActiveItemHand"->activeHand;
            case "damageItemStack"->{
                wearCalls++;boolean main=a[0]==org.bukkit.inventory.EquipmentSlot.HAND;
                ItemStack current=main?mainItem:offItem;
                if(!args[0].equals("copy-shield-unbreaking"))current.meta.damage+=(Integer)a[1];
                if(current.meta.damage>=current.getType().getMaxDurability()){
                    if(main)mainItem=new ItemStack(Material.AIR);else offItem=new ItemStack(Material.AIR);
                }yield null;
            }
            case "isDead"->health<=0;case "isValid"->!removed;case "isOnGround"->true;
            case "remove"->{removed=true;yield null;}default->value(m.getReturnType());});
        // Actual effect outcome is observed, not an assertion that a mock exists.
        Pillager originalCarrier=carrier;
        carrier=proxy(Pillager.class,(p,m,a)->{if(m.getName().equals("addPotionEffect")){effects++;return true;}return m.invoke(originalCarrier,a);});
        ItemStack[] storage=new ItemStack[36];storage[0]=new ItemStack(Material.BOW);storage[1]=new ItemStack(Material.ARROW,2);
        boolean mainShield=args[0].equals("copy-main-shield")||args[0].equals("copy-native-main-shield")||args[0].equals("copy-shield-main-hit");
        if(mainShield)storage[0]=new ItemStack(Material.SHIELD);
        var inventory=proxy(PlayerInventory.class,(p,m,a)->switch(m.getName()){
            case "getStorageContents"->storage;case "getHeldItemSlot"->0;
            case "getItemInOffHand"->new ItemStack(mainShield?Material.AIR:Material.SHIELD);
            default->value(m.getReturnType());});
        Player owner=proxy(Player.class,(p,m,a)->switch(m.getName()){
            case "getUniqueId"->OWNER;case "getWorld"->world;case "getEyeLocation"->location.clone().add(4,1.6,0);
            case "isOnline"->online;case "isDead"->!ownerAlive;case "getInventory"->inventory;default->value(m.getReturnType());});
        List<String> packets=new ArrayList<>();
        List<EchoPresentationProbeState.Frame> frames=new ArrayList<>();
        var sender=(java.util.function.BiConsumer<String,EchoPresentationProbeState.Frame>)(type,frame)->{packets.add(type);frames.add(frame);};
        EchoPresentationProbe probe;
        if(args[0].startsWith("copy")){
            try{probe=EchoPresentationProbe.class.getConstructor(Pillager.class,Player.class,UUID.class,long.class,long.class,long.class,
                java.util.function.BiConsumer.class,boolean.class).newInstance(carrier,owner,EVENT,7L,10L,100L,sender,true);}
            catch(NoSuchMethodException missing){probe=new EchoPresentationProbe(carrier,owner,EVENT,7,10,100,sender);}
        }else probe=new EchoPresentationProbe(carrier,owner,EVENT,7,10,100,sender);
        if(args[0].equals("walk")){
            probe.action(EchoPresentationProbeState.Action.WALK,105);
            probe.tick(owner,EVENT,7,105,true);probe.tick(owner,EVENT,7,110,true);
            if(moves!=1)throw new AssertionError("native navigation requires aware carrier with removed goals; moves="+moves);
        }else if(args[0].equals("death")){
            probe.action(EchoPresentationProbeState.Action.DEATH,105);
            if(!probe.tick(owner,EVENT,7,110,true))throw new AssertionError("native death window skipped");
            if(probe.tick(owner,EVENT,7,125,true)||!removed||!packets.get(packets.size()-1).equals("END_ECHO_REMOVE"))
                throw new AssertionError("corpse did not close");
        }else if(args[0].equals("generation")){
            if(probe.tick(owner,EVENT,8,105,true)||!removed)throw new AssertionError("stale generation retained carrier");
        }else if(args[0].equals("copy-gear")){
            if(mainItem.getType()!=Material.BOW)throw new AssertionError("frozen owner bow replaced with free fixed sword");
            if(probe.action(EchoPresentationProbeState.Action.CROSSBOW,105))throw new AssertionError("replica invented missing crossbow");
        }else if(args[0].equals("copy-eat")){
            if(!probe.action(EchoPresentationProbeState.Action.EAT,105))throw new AssertionError("first apple use");
            probe.tick(owner,EVENT,7,136,true);if(effects!=0)throw new AssertionError("heal before completed use");
            probe.tick(owner,EVENT,7,137,true);probe.tick(owner,EVENT,7,140,true);
            if(effects!=2)throw new AssertionError("completion must apply two native golden apple effects exactly once; effects="+effects);
            probe.action(EchoPresentationProbeState.Action.EAT,145);probe.tick(owner,EVENT,7,177,true);
            if(effects!=4||probe.action(EchoPresentationProbeState.Action.EAT,180))throw new AssertionError("finite two extra apples refilled");
            if(storage[0].getType()!=Material.BOW||storage[1].getAmount()!=2)throw new AssertionError("real inventory changed");
        }else if(args[0].equals("copy-cancel")){
            probe.action(EchoPresentationProbeState.Action.EAT,105);probe.action(EchoPresentationProbeState.Action.IDLE,115);
            probe.tick(owner,EVENT,7,140,true);if(effects!=0)throw new AssertionError("interrupted use healed");
            probe.action(EchoPresentationProbeState.Action.EAT,145);probe.tick(owner,EVENT,7,177,true);
            probe.action(EchoPresentationProbeState.Action.EAT,180);probe.tick(owner,EVENT,7,212,true);
            if(effects!=4)throw new AssertionError("interruption consumed a supply");
        }else if(args[0].equals("copy-stale")){
            probe.action(EchoPresentationProbeState.Action.EAT,105);
            probe.tick(owner,EVENT,8,140,true);if(effects!=0||!removed)throw new AssertionError("stale pending use committed");
        }else if(args[0].equals("copy-quit")){
            probe.action(EchoPresentationProbeState.Action.EAT,105);online=false;
            probe.tick(owner,EVENT,7,140,true);if(effects!=0||!removed)throw new AssertionError("quit pending use committed");
        }else if(args[0].equals("copy-main-shield")){
            if(!probe.action(EchoPresentationProbeState.Action.SHIELD,105)||mainItem.getType()!=Material.SHIELD
                ||!frames.get(frames.size()-1).hand().equals("MAIN"))throw new AssertionError("main-hand shield moved/animated as offhand");
        }else if(args[0].equals("copy-native-shield")||args[0].equals("copy-native-main-shield")){
            probe.action(EchoPresentationProbeState.Action.SHIELD,105);
            var expected=args[0].equals("copy-native-main-shield")?org.bukkit.inventory.EquipmentSlot.HAND:org.bukkit.inventory.EquipmentSlot.OFF_HAND;
            if(activeHand!=expected)throw new AssertionError("shield pose never activated native blocking hand: "+activeHand);
            probe.action(EchoPresentationProbeState.Action.IDLE,110);
            if(activeHand!=null)throw new AssertionError("replaced shield use still blocks");
            probe.action(EchoPresentationProbeState.Action.SHIELD,115);probe.close(116);
            if(activeHand!=null)throw new AssertionError("cleanup retained native shield use");
        }else if(args[0].equals("copy-native-stale")){
            probe.action(EchoPresentationProbeState.Action.SHIELD,105);
            if(activeHand==null)throw new AssertionError("native use was not started before stale cleanup");
            probe.tick(owner,EVENT,8,110,true);
            if(activeHand!=null||!removed)throw new AssertionError("generation change retained native shield use");
        }else if(args[0].startsWith("copy-shield-")){
            probe.action(EchoPresentationProbeState.Action.SHIELD,105);
            Object receipt=new Object();double blocked=args[0].equals("copy-shield-break")?400:4.9;
            boolean applied=blocked(probe,receipt,owner,EVENT,7,110,true,blocked,args[0].equals("copy-shield-axe"));
            if(!applied)throw new AssertionError("accepted native shield block has no finite wear/disable adapter");
            if(args[0].equals("copy-shield-break")){
                if(offItem.getType()!=Material.AIR||activeHand!=null||probe.action(EchoPresentationProbeState.Action.SHIELD,115))
                    throw new AssertionError("broken native shield respawned");
            }else if(args[0].equals("copy-shield-axe")){
                if(activeHand!=null||probe.action(EchoPresentationProbeState.Action.SHIELD,209)
                    ||!probe.action(EchoPresentationProbeState.Action.SHIELD,210))throw new AssertionError("axe disable must last 100 ticks");
            }else if(args[0].equals("copy-shield-unbreaking")){
                probe.action(EchoPresentationProbeState.Action.IDLE,111);probe.action(EchoPresentationProbeState.Action.SHIELD,112);
                if(offItem.meta.damage!=0)throw new AssertionError("native Unbreaking outcome replaced by raw wear");
            }else if(args[0].equals("copy-shield-main-hit")){
                probe.action(EchoPresentationProbeState.Action.IDLE,111);probe.action(EchoPresentationProbeState.Action.SHIELD,112);
                if(mainItem.meta.damage!=5||activeHand!=org.bukkit.inventory.EquipmentSlot.HAND)throw new AssertionError("main-hand finite shield wear/hand lost");
            }else if(args[0].equals("copy-shield-hit")){
                if(offItem.meta.damage!=5||raiseCalls!=1||health!=20)throw new AssertionError("shield wear/timing changed or adapter applied health damage");
                if(blocked(probe,receipt,owner,EVENT,7,110,true,4.9,false)||wearCalls!=1)throw new AssertionError("same accepted block consumed twice");
                if(!blocked(probe,new Object(),owner,EVENT,7,110,true,4.9,false)||offItem.meta.damage!=10)throw new AssertionError("second distinct hit in same tick lost");
                if(blocked(probe,new Object(),owner,EVENT,8,115,true,4.9,false)||offItem.meta.damage!=10)throw new AssertionError("stale generation wore shield");
                if(blocked(probe,new Object(),owner,EVENT,7,115,false,4.9,false)||offItem.meta.damage!=10)throw new AssertionError("revoked capability wore shield");
                online=false;if(blocked(probe,new Object(),owner,EVENT,7,115,true,4.9,false))throw new AssertionError("quit owner still wore shield");online=true;
                ownerAlive=false;if(blocked(probe,new Object(),owner,EVENT,7,115,true,4.9,false))throw new AssertionError("dead owner still wore shield");ownerAlive=true;
                Player foreign=proxy(Player.class,(p,m,a)->m.getName().equals("getUniqueId")?new UUID(0,99):m.invoke(owner,a));
                if(blocked(probe,new Object(),foreign,EVENT,7,115,true,4.9,false))throw new AssertionError("foreign owner shield transaction accepted");
                if(blocked(probe,new Object(),owner,EVENT,7,115,true,0,false)
                    ||blocked(probe,new Object(),owner,EVENT,7,115,true,Double.NaN,false))throw new AssertionError("unblocked/invalid hit wore shield");
                probe.action(EchoPresentationProbeState.Action.IDLE,116);probe.action(EchoPresentationProbeState.Action.SHIELD,117);
                if(offItem.meta.damage!=10)throw new AssertionError("equipment projection repaired native shield wear");
                if(blocked(probe,new Object(),owner,EVENT,7,115,true,4.9,false)||offItem.meta.damage!=10)throw new AssertionError("stale hit from before new shield raise wore current use");
            }else throw new AssertionError(args[0]);
        }else throw new AssertionError(args[0]);
    }
    static boolean blocked(EchoPresentationProbe probe,Object receipt,Player owner,UUID event,long generation,long tick,
                           boolean capable,double amount,boolean axe)throws Exception{
        try{return (Boolean)EchoPresentationProbe.class.getMethod("acceptedShieldBlock",Object.class,Player.class,UUID.class,
            long.class,long.class,boolean.class,double.class,boolean.class).invoke(probe,receipt,owner,event,generation,tick,capable,amount,axe);}
        catch(NoSuchMethodException absent){return false;}
    }
}
''', encoding="utf-8")
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-cp", classpath, "-d", str(directory),
                             str(BASE / "domain/wave7/EchoPresentationProbeState.java"),
                             str(BASE / "domain/wave7/EchoLoadoutState.java"),
                             str(BASE / "runtime/wave7/EchoReplicaInventory.java"),
                             str(BASE / "runtime/wave7/EchoPresentationProbe.java"), *boundary_sources, str(probe)],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    return classpath


@pytest.mark.parametrize("scenario", ["walk", "death", "generation", "copy-gear", "copy-eat", "copy-cancel", "copy-stale", "copy-quit", "copy-main-shield", "copy-native-shield", "copy-native-main-shield", "copy-native-stale", "copy-shield-hit", "copy-shield-main-hit", "copy-shield-unbreaking", "copy-shield-break", "copy-shield-axe"])
def test_native_carrier_adapter(carrier_probe, scenario):
    result = subprocess.run(["java", "-cp", carrier_probe, "EchoCarrierChecks", scenario],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
