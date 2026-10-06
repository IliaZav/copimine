"""Execute the real Paper carrier adapter with a narrow native-navigation boundary."""
from pathlib import Path
import subprocess

import pytest

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
        "org/bukkit/World.java": "package org.bukkit; public interface World { NamespacedKey getKey(); }",
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
java.util.UUID getUniqueId();org.bukkit.World getWorld();org.bukkit.Location getEyeLocation();boolean isOnline();boolean isDead();}''',
        "org/bukkit/entity/Husk.java": '''package org.bukkit.entity; public interface Husk extends Mob {
java.util.UUID getUniqueId();org.bukkit.World getWorld();org.bukkit.Location getLocation();org.bukkit.Location getEyeLocation();
org.bukkit.inventory.EntityEquipment getEquipment();com.destroystokyo.paper.entity.Pathfinder getPathfinder();
org.bukkit.util.Vector getVelocity();void setVelocity(org.bukkit.util.Vector v);boolean isOnGround();boolean isValid();boolean isDead();
double getHealth();void setHealth(double v);void damage(double v);void remove();void setAdult();void setShouldBurnInDay(boolean v);
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
    static boolean aware,goals=true,removed;static int moves;static double health=20;
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
        EntityEquipment gear=proxy(EntityEquipment.class,(p,m,a)->value(m.getReturnType()));
        Pathfinder path=proxy(Pathfinder.class,(p,m,a)->{
            if(m.getName().equals("moveTo")){if(aware&&!goals)moves++;return aware&&!goals;}
            return value(m.getReturnType());});
        Husk carrier=proxy(Husk.class,(p,m,a)->switch(m.getName()){
            case "getLocation","getEyeLocation"->location.clone();case "getWorld"->world;
            case "getUniqueId"->ACTOR;case "getEquipment"->gear;case "getPathfinder"->path;
            case "getVelocity"->new org.bukkit.util.Vector();case "getHealth"->health;
            case "setAware"->{aware=(Boolean)a[0];yield null;}
            case "setHealth"->{health=(Double)a[0];yield null;}
            case "isDead"->health<=0;case "isValid"->!removed;case "isOnGround"->true;
            case "remove"->{removed=true;yield null;}default->value(m.getReturnType());});
        Player owner=proxy(Player.class,(p,m,a)->switch(m.getName()){
            case "getUniqueId"->OWNER;case "getWorld"->world;case "getEyeLocation"->location.clone().add(4,1.6,0);
            case "isOnline"->true;default->value(m.getReturnType());});
        List<String> packets=new ArrayList<>();
        var probe=new EchoPresentationProbe(carrier,owner,EVENT,7,10,100,(type,frame)->packets.add(type));
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
        }else throw new AssertionError(args[0]);
    }
}
''', encoding="utf-8")
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-cp", classpath, "-d", str(directory),
                             str(BASE / "domain/wave7/EchoPresentationProbeState.java"),
                             str(BASE / "runtime/wave7/EchoPresentationProbe.java"), *boundary_sources, str(probe)],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    return classpath


@pytest.mark.parametrize("scenario", ["walk", "death", "generation"])
def test_native_carrier_adapter(carrier_probe, scenario):
    result = subprocess.run(["java", "-cp", carrier_probe, "EchoCarrierChecks", scenario],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
