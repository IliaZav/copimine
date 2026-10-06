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
    stubs["org/bukkit/entity/Pillager.java"] = stubs["org/bukkit/entity/Pillager.java"].replace(
        "void swingMainHand();", "void swingMainHand();boolean addPotionEffect(org.bukkit.potion.PotionEffect effect);")
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
    static boolean aware,goals=true,removed,online=true;static int moves,effects;static double health=20;
    static ItemStack mainItem;
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
        EntityEquipment gear=proxy(EntityEquipment.class,(p,m,a)->{if(m.getName().equals("setItemInMainHand"))mainItem=(ItemStack)a[0];return value(m.getReturnType());});
        Pathfinder path=proxy(Pathfinder.class,(p,m,a)->{
            if(m.getName().equals("moveTo")){if(aware&&!goals)moves++;return aware&&!goals;}
            return value(m.getReturnType());});
        Pillager carrier=proxy(Pillager.class,(p,m,a)->switch(m.getName()){
            case "getLocation","getEyeLocation"->location.clone();case "getWorld"->world;
            case "getUniqueId"->ACTOR;case "getEquipment"->gear;case "getPathfinder"->path;
            case "getVelocity"->new org.bukkit.util.Vector();case "getHealth"->health;
            case "setAware"->{aware=(Boolean)a[0];yield null;}
            case "setHealth"->{health=(Double)a[0];yield null;}
            case "isDead"->health<=0;case "isValid"->!removed;case "isOnGround"->true;
            case "remove"->{removed=true;yield null;}default->value(m.getReturnType());});
        // Actual effect outcome is observed, not an assertion that a mock exists.
        Pillager originalCarrier=carrier;
        carrier=proxy(Pillager.class,(p,m,a)->{if(m.getName().equals("addPotionEffect")){effects++;return true;}return m.invoke(originalCarrier,a);});
        ItemStack[] storage=new ItemStack[36];storage[0]=new ItemStack(Material.BOW);storage[1]=new ItemStack(Material.ARROW,2);
        if(args[0].equals("copy-main-shield"))storage[0]=new ItemStack(Material.SHIELD);
        var inventory=proxy(PlayerInventory.class,(p,m,a)->switch(m.getName()){
            case "getStorageContents"->storage;case "getHeldItemSlot"->0;
            case "getItemInOffHand"->new ItemStack(args[0].equals("copy-main-shield")?Material.AIR:Material.SHIELD);
            default->value(m.getReturnType());});
        Player owner=proxy(Player.class,(p,m,a)->switch(m.getName()){
            case "getUniqueId"->OWNER;case "getWorld"->world;case "getEyeLocation"->location.clone().add(4,1.6,0);
            case "isOnline"->online;case "getInventory"->inventory;default->value(m.getReturnType());});
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
        }else throw new AssertionError(args[0]);
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


@pytest.mark.parametrize("scenario", ["walk", "death", "generation", "copy-gear", "copy-eat", "copy-cancel", "copy-stale", "copy-quit", "copy-main-shield"])
def test_native_carrier_adapter(carrier_probe, scenario):
    result = subprocess.run(["java", "-cp", carrier_probe, "EchoCarrierChecks", scenario],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
