"""Execute the production room search and staging commit at unloaded chunk edges."""
from pathlib import Path
import subprocess

import pytest

from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "copimine-end-event/src/me/copimine/endevent"


@pytest.fixture(scope="module")
def return_chunk_probe(tmp_path_factory):
    directory = tmp_path_factory.mktemp("return-chunk-probe")
    source = (BASE / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    signatures = (
        "Location findSafeCombatLocation(Location anchor, Location preferred, double radius,\n"
        "                                             double minCoreDistance, int chamberId,\n"
        "                                             Entity occupant)",
        "void tickWave7Returns()",
        "boolean isRealitySplitDestinationAllowed(",
    )
    adapters = "\n".join(extract(source, signature) for signature in signatures)
    optional = "boolean isLoadedWave7ReturnCandidate(Location candidate, Entity occupant)"
    if optional in source:
        adapters += "\n" + extract(source, optional)
    destination_overload = ("boolean isRealitySplitDestinationAllowed(\n"
                            "            UUID playerId, Location target,\n"
                            "            ChamberIsolationPolicy.Assignment assignment, Location anchor)")
    if destination_overload in source:
        adapters += "\n" + extract(source, destination_overload)
    stubs = {
        "org/bukkit/World.java": "package org.bukkit; public interface World {}",
        "org/bukkit/Location.java": "package org.bukkit; public interface Location {}",
        "me/copimine/endevent/runtime/ArenaEntranceResolver.java": r'''package me.copimine.endevent.runtime;
import org.bukkit.*;import java.util.function.Predicate;
public class ArenaEntranceResolver {public static boolean safe(Location location,Predicate<Object> danger){return true;}}
''',
    }
    for name, text in stubs.items():
        path = directory / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
    probe = directory / "ReturnChunkProbe.java"
    probe.write_text(r'''
import java.util.*;
import me.copimine.endevent.domain.*;
import me.copimine.endevent.runtime.AttemptLifecycleController;
public class ReturnChunkProbe {
    static final UUID OWNER=new UUID(0,1);
    static final double MIN_WAVE_CORE_DISTANCE_BLOCKS=3.5D;
    enum BlockFace {UP,DOWN}
    enum Material {AIR,STONE; boolean isSolid(){return this==STONE;}}
    enum GameMode {SURVIVAL,ADVENTURE}
    enum NamedTextColor {AQUA}
    enum Sound {BLOCK_RESPAWN_ANCHOR_SET_SPAWN}
    enum EventPhase {RECOVERY_REQUIRED}
    static class Vector {}
    static class Component {static Component text(String text,NamedTextColor color){return new Component();}}
    static class World implements org.bukkit.World {
        int reads,chunkChecks;boolean allLoaded=true;Set<String> loaded=Set.of();double borderMax=1e6;
        int getMinHeight(){return -64;}int getMaxHeight(){return 320;}
        boolean isChunkLoaded(int x,int z){chunkChecks++;return allLoaded||loaded.contains(x+":"+z);}
        WorldBorder getWorldBorder(){return new WorldBorder(this);}
        Block getBlockAt(int x,int y,int z){
            if(!isChunkLoaded(x>>4,z>>4))throw new AssertionError("room candidate read an unloaded chunk before admission");
            reads++;return new Block(this,x,y,z);
        }
    }
    record WorldBorder(World world) {boolean isInside(Location location){return Math.abs(location.getX())<=world.borderMax&&Math.abs(location.getZ())<=world.borderMax;}}
    static class Location implements org.bukkit.Location,Cloneable {
        World world;double x,y,z;
        Location(World world,double x,double y,double z){this.world=world;this.x=x;this.y=y;this.z=z;}
        World getWorld(){return world;}double getX(){return x;}double getY(){return y;}double getZ(){return z;}
        int getBlockX(){return (int)Math.floor(x);}int getBlockY(){return (int)Math.floor(y);}int getBlockZ(){return (int)Math.floor(z);}
        Block getBlock(){return world.getBlockAt(getBlockX(),getBlockY(),getBlockZ());}
        public Location clone(){return new Location(world,x,y,z);}
        Location add(double dx,double dy,double dz){x+=dx;y+=dy;z+=dz;return this;}
        double distanceSquared(Location other){return Math.pow(x-other.x,2)+Math.pow(y-other.y,2)+Math.pow(z-other.z,2);}
    }
    record Block(World world,int x,int y,int z) {
        int getX(){return x;}int getY(){return y;}int getZ(){return z;}
        Block getRelative(BlockFace face){return world.getBlockAt(x,y+(face==BlockFace.UP?1:-1),z);}
        Material getType(){return y==67?Material.STONE:Material.AIR;}
        boolean isPassable(){return getType()==Material.AIR;}boolean isLiquid(){return false;}
    }
    static class Entity {
        Location location;double width=.6,height=1.8;
        World getWorld(){return location.getWorld();}double getWidth(){return width;}double getHeight(){return height;}
    }
    static class Player extends Entity {
        UUID getUniqueId(){return OWNER;}boolean isOnline(){return true;}boolean isDead(){return false;}
        double getHealth(){return 20;}GameMode getGameMode(){return GameMode.SURVIVAL;}
        Location getLocation(){return location;}void sendActionBar(Component component){}
        void setVelocity(Vector vector){}void setFallDistance(float value){}
        void playSound(Location location,Sound sound,float volume,float pitch){}
    }
    static class Bukkit {static Player player;static Player getPlayer(UUID owner){return OWNER.equals(owner)?player:null;}}
    static class Chambers {
        ChamberIsolationPolicy.Assignment assignment(){return new ChamberIsolationPolicy.Assignment(1,Map.of(OWNER,0));}
        boolean allChambersComplete(long generation){return false;}
        boolean allowsPlayerInChamber(long generation,UUID player,int room){return generation==43&&OWNER.equals(player)&&room==0;}
    }
    static class Config {double arenaRadius(){return 20;}}
    Chambers realitySplitChamberController=new Chambers();Config config=new Config();
    AttemptLifecycleController attemptLifecycle=new AttemptLifecycleController();
    Map<UUID,Location> wave7ReturnStagingLocations=new HashMap<>();
    Map<UUID,Long> wave7ReturnActionNextTicks=new HashMap<>(),wave7ReturnEnterNextTicks=new HashMap<>();
    Map<UUID,Object> wave7ProjectileIncarnations=new HashMap<>();
    long generation=43,eventTickCounter=140,lastWave7ParticipationSaveTick=100;
    String eventId="synthetic-attempt",recoveryReason;int coreX,coreZ,moves,saves;boolean official=true;
    boolean isOfficialWave7ReturnContext(){return official;}
    boolean isFireBlock(Object block){return false;}boolean isWebBlock(Block block){return false;}
    boolean isTemporaryMovementHazard(Object block){return false;}boolean isCoreBlockPosition(Location location){return false;}
    double configuredCombatVerticalRadius(){return 3;}double boundedCombatRadius(double radius){return radius;}
    long wave7ReturnGraceMillis(){return 120000;}
    boolean isSafeCombatOccupantLocation(Location location,Entity entity){
        double half=entity.getWidth()/2+.35;
        for(int x=(int)Math.floor(location.x-half+1e-6);x<=(int)Math.ceil(location.x+half-1e-6)-1;x++)
            for(int z=(int)Math.floor(location.z-half+1e-6);z<=(int)Math.ceil(location.z+half-1e-6)-1;z++)
                location.world.getBlockAt(x,location.getBlockY()-1,z);
        return true;
    }
    boolean isSafeCombatParticipantLocation(Location location,Entity entity){return isSafeCombatOccupantLocation(location,entity);}
    boolean isArenaLocation(Location location){return true;}
    Location realitySplitChamberCenter(Location anchor,int claim,int count){return anchor.clone().add(8,0,4);}
    Location coreCombatAnchorLocation(){throw new AssertionError("staging commit invoked inherited Core floor scan");}
    boolean teleportWave7Return(Player player,Location destination){moves++;player.location=destination.clone();return true;}
    void saveStateAsync(){}boolean saveStateSync(){saves++;return true;}
    void message(Player player,String message){}void refreshClientBindingsForPlayer(Player player){}
    void cancelSessionTasks(){}void cleanupOwnedEntities(String event,long generation){}void clearClientEffects(){}
    void forcePhase(EventPhase phase,String reason){}
    java.util.logging.Logger getLogger(){return java.util.logging.Logger.getAnonymousLogger();}
''' + adapters + r'''
    static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    static Player player(World world){var player=new Player();player.location=new Location(world,8.5,68,4.5);return player;}
    void beginStaging(Player player){
        Bukkit.player=player;attemptLifecycle.begin(43,Set.of(OWNER));attemptLifecycle.enableWave7Returns(43);
        attemptLifecycle.markDead(OWNER,43);attemptLifecycle.markAlive(OWNER,43);
        require(attemptLifecycle.beginReturn(OWNER,43,100,40)!=null,"fixture has an actual unfinished staged claim");
        wave7ReturnStagingLocations.put(OWNER,player.location.clone());
    }
    public static void main(String[] args){
        var main=new ReturnChunkProbe();var world=new World();var player=player(world);
        if(args[0].equals("search")){
            world.allLoaded=false;
            var result=main.findSafeCombatLocation(new Location(world,.5,68,.5),new Location(world,8.5,68,4.5),19,3.5,0,player);
            require(result==null&&world.reads==0,"unloaded room search must fail without any block reads");
            world.loaded=Set.of("0:0");world.reads=0;
            result=main.findSafeCombatLocation(new Location(world,32.5,68,.5),new Location(world,15.5,68,4.5),19,3.5,-1,player);
            require(result==null&&world.reads==0,"loaded centre cannot hide the unloaded adjacent footprint chunk");
            world.allLoaded=true;world.reads=0;
            result=main.findSafeCombatLocation(new Location(world,.5,68,.5),new Location(world,8.5,68,4.5),19,3.5,0,player);
            require(result!=null&&result.x==8.5&&result.y==68&&result.z==4.5,"loaded safe room must keep its actual deterministic destination");
            require(world.reads<=48*12,"bounded candidate and footprint work was exceeded");
            world.borderMax=2;world.reads=0;
            require(main.findSafeCombatLocation(new Location(world,32.5,68,.5),new Location(world,15.5,68,4.5),19,3.5,-1,player)==null&&world.reads==0,"room candidates outside world border must be refused before block reads");
            world.borderMax=1e6;world.reads=0;
            require(main.findSafeCombatLocation(new Location(world,.5,320,.5),new Location(world,8.5,320,4.5),19,3.5,0,player)==null&&world.reads==0,"standing footprint beyond world height must not read blocks");
            world.borderMax=2;main.official=false;
            require(main.findSafeCombatLocation(new Location(world,.5,68,.5),new Location(world,8.5,68,4.5),19,3.5,0,player)!=null,"Wave 7 return restriction must not rewrite other combat callers");
        }else if(args[0].equals("staging")){
            main.beginStaging(player);main.tickWave7Returns();
            require(main.moves==1&&main.saves==1&&!main.attemptLifecycle.isReturnPending(OWNER,43),"valid return must admit the original owner using the validated entrance height");
            main=new ReturnChunkProbe();main.coreX=32;player=player(world);player.location=new Location(world,15.5,68,4.5);
            main.beginStaging(player);world.allLoaded=false;world.loaded=Set.of("0:0");world.reads=0;
            main.tickWave7Returns();
            require(main.moves==0&&main.attemptLifecycle.isReturnPending(OWNER,43)&&world.reads==0,"unloaded room must cancel staging without teleport, admission or block loading");
            world.allLoaded=true;main=new ReturnChunkProbe();player=player(world);main.beginStaging(player);main.generation=44;
            main.tickWave7Returns();require(main.moves==0,"stale generation must never admit an old staging owner");
        }else throw new AssertionError("unknown fixture");
    }
}
''', encoding="utf-8")
    production = [BASE / name for name in (
        "domain/BossMovementPolicy.java", "domain/ChamberIsolationPolicy.java", "domain/RealitySplitPlayerTeleportPolicy.java",
        "runtime/AttemptLifecycleController.java")]
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(directory),
                             *map(str, production), *map(str, directory.rglob("*.java"))],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    return directory


@pytest.mark.parametrize("scenario", ["search", "staging"])
def test_official_return_cannot_load_unchecked_room_chunks(return_chunk_probe, scenario):
    result = subprocess.run(["java", "-cp", str(return_chunk_probe), "ReturnChunkProbe", scenario],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
