"""Execute the real public return adapter's height and staging-retry boundaries."""
from pathlib import Path
import subprocess

from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def test_return_does_not_scan_unloaded_floor_or_repeat_durable_staging(tmp_path):
    stubs = {
        "net/kyori/adventure/text/event/ClickEvent.java": "package net.kyori.adventure.text.event; public class ClickEvent {public static Object runCommand(String x){return x;}}",
        "org/bukkit/Location.java": r'''package org.bukkit;
public class Location implements Cloneable {
    World world; double x,y,z;
    public Location(World w,double x,double y,double z){world=w;this.x=x;this.y=y;this.z=z;}
    public World getWorld(){return world;} public double getX(){return x;}public double getY(){return y;}public double getZ(){return z;}
    public double distanceSquared(Location b){return (x-b.x)*(x-b.x)+(y-b.y)*(y-b.y)+(z-b.z)*(z-b.z);}
    public Location clone(){return new Location(world,x,y,z);}
}''',
        "org/bukkit/World.java": "package org.bukkit; public class World {}",
        "org/bukkit/Bukkit.java": "package org.bukkit; public class Bukkit {public static World WORLD=new World();public static World getWorld(String x){return WORLD;}}",
        "me/copimine/endevent/runtime/ArenaEntranceResolver.java": r'''package me.copimine.endevent.runtime;
import org.bukkit.*;import java.util.*;import java.util.function.Predicate;
import me.copimine.endevent.domain.ArenaEntranceLocator.Bounds;
public class ArenaEntranceResolver {
    public static boolean loaded=true;public static int calls,lastY;
    public static Optional<Location> find(World w,Bounds b,int y,Predicate<Object> danger){calls++;lastY=y;return loaded?Optional.of(new Location(w,b.minX()-2,y,b.minZ()-2)):Optional.empty();}
    public static boolean safe(Location p,Predicate<Object> danger){return loaded;}
}''',
    }
    for relative, source in stubs.items():
        path = tmp_path / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source, encoding="utf-8")
    source = SOURCE.read_text(encoding="utf-8")
    adapters = "\n".join(extract(source, signature) for signature in (
        "Location resolveWave7ReturnEntrance()", "void handleWave7Return(CommandSender sender, String[] args)"))
    probe = tmp_path / "ReturnCommandProbe.java"
    probe.write_text(r'''
import org.bukkit.*;import java.util.*;import me.copimine.endevent.runtime.*;
public class ReturnCommandProbe {
    static final UUID OWNER=new UUID(0,1), ALLY=new UUID(0,2);
    interface CommandSender {}
    enum GameMode { SURVIVAL,ADVENTURE }
    enum Sound { BLOCK_RESPAWN_ANCHOR_CHARGE }
    enum NamedTextColor { AQUA,GREEN }
    static class Component {static Component text(String x,NamedTextColor c){return new Component();}Component clickEvent(Object x){return this;}}
    static class Vector {}
    static class Player implements CommandSender {
        Location location=new Location(Bukkit.WORLD,-22,68,-42);
        UUID getUniqueId(){return OWNER;}boolean isOnline(){return true;}boolean isDead(){return false;}
        double getHealth(){return 20;}boolean isInsideVehicle(){return false;}GameMode getGameMode(){return GameMode.SURVIVAL;}
        World getWorld(){return location.getWorld();}Location getLocation(){return location;}
        void setVelocity(Vector v){}void setFallDistance(float f){}void sendMessage(Component c){}
        void playSound(Location l,Sound s,float volume,float pitch){}
    }
    static class Assignment {Map<UUID,Integer> chamberByPlayer(){return Map.of(OWNER,0,ALLY,1);}}
    static class Chambers {boolean owns(long g){return g==43;}Assignment assignment(){return new Assignment();}}
    Chambers realitySplitChamberController=new Chambers();
    AttemptLifecycleController attemptLifecycle=new AttemptLifecycleController();
    Set<UUID> officialRewardRoster=Set.of(OWNER,ALLY);List<Object> pads=new ArrayList<>();
    Map<UUID,Long> wave7ReturnActionNextTicks=new HashMap<>(),wave7ReturnEnterNextTicks=new HashMap<>();
    Map<UUID,Location> wave7ReturnStagingLocations=new HashMap<>();
    String worldName="configured-arena",eventId="synthetic-attempt";long generation=43,eventTickCounter=100;
    int arenaMinX=-20,arenaMinY=64,arenaMinZ=-40,arenaMaxX=20,arenaMaxY=72,arenaMaxZ=0,coreY=68;
    int saves,floorScans;boolean loadedFloorScanForbidden=true;
    boolean isOfficialWave7ReturnContext(){return true;}long wave7ReturnGraceMillis(){return 120000;}
    boolean isTemporaryMovementHazard(Object block){return false;}
    boolean teleportWave7Return(Player p,Location l){p.location=l.clone();return true;}
    void message(CommandSender s,String text){}void wipeOfficialAttemptIfAllDead(String reason){throw new AssertionError(reason);}
    boolean saveStateSync(){saves++;return true;}
    java.util.logging.Logger getLogger(){return java.util.logging.Logger.getAnonymousLogger();}
    int combatLevelY(){floorScans++;if(pads.isEmpty()&&loadedFloorScanForbidden)throw new AssertionError("return invoked inherited floor block reads before the loaded resolver");return 69;}
    static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    ReturnCommandProbe(){attemptLifecycle.begin(43,Set.of(OWNER,ALLY));attemptLifecycle.enableWave7Returns(43);
        attemptLifecycle.markDead(OWNER,43);attemptLifecycle.markAlive(OWNER,43);}
''' + adapters + r'''
    public static void main(String[] args){
        var main=new ReturnCommandProbe();var player=new Player();
        ArenaEntranceResolver.loaded=false;
        require(main.resolveWave7ReturnEntrance()==null,"unloaded candidates should remain refused");
        require(main.floorScans==0,"pads-empty return performed inherited block scans");
        ArenaEntranceResolver.loaded=true;
        var resolved=main.resolveWave7ReturnEntrance();
        require(resolved.getY()==68&&main.floorScans==0,"return should use saved Core reference and bounded candidate validation");
        main.pads.add(new Object());
        require(main.resolveWave7ReturnEntrance().getY()==69,"existing pad height must remain the preferred floor");
        main.pads.clear();main.handleWave7Return(player,new String[]{"return"});
        main.handleWave7Return(player,new String[]{"return","enter"});
        require(main.saves==1&&main.attemptLifecycle.isReturnProtected(OWNER,43,100),"travel must allow immediate entrance-local staging");
        var token=main.attemptLifecycle.stagingReturns(43).get(OWNER);
        require(main.attemptLifecycle.cancelReturn(token),"outgoing offense cancels staging");
        main.handleWave7Return(player,new String[]{"return","enter"});
        require(main.saves==1&&!main.attemptLifecycle.isReturnProtected(OWNER,43,100),"same-tick offense cancellation repeated synchronous durable staging");
        main.eventTickCounter=119;main.handleWave7Return(player,new String[]{"return","enter"});
        require(main.saves==1,"retry before cooldown performed another synchronous save");
        main.eventTickCounter=120;main.handleWave7Return(player,new String[]{"return","enter"});
        require(main.saves==2&&main.attemptLifecycle.isReturnProtected(OWNER,43,120),"legitimate later retry remains possible");
    }
}
''', encoding="utf-8")
    files = [ROOT / "copimine-end-event/src/me/copimine/endevent" / p for p in (
        "runtime/AttemptLifecycleController.java", "domain/ArenaEntranceLocator.java")]
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), *map(str, files),
                             *map(str, tmp_path.rglob("*.java"))], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "ReturnCommandProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
