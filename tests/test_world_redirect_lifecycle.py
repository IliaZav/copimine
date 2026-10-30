"""Execute the actual WorldCore redirect callbacks across deferred policy changes."""
from pathlib import Path
import subprocess

import pytest
from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]


@pytest.fixture(scope="module")
def redirect_probe(tmp_path_factory):
    out = tmp_path_factory.mktemp("world-redirect-lifecycle")
    source = (ROOT / "copimine-world-core/src/me/copimine/worldcore/CopiMineWorldCore.java").read_text(encoding="utf-8")
    signatures = ["void finishRedirect(", "void retryRedirectTeleport(",
                  "boolean stillBlocked(", "WorldAccess accessFor(",
                  "boolean matchesAccessWorld(", "World resolveRedirectTarget("]
    if "boolean redirectStillValid(" in source:
        signatures.append("boolean redirectStillValid(")
    methods = "\n".join(extract(source, signature) for signature in signatures)
    java = r'''
import java.util.*;
import java.util.logging.Logger;
public class WorldRedirectProbe {
    boolean enabled=true,spawnSafe=true;
    WorldAccess netherAccess;
    WorldAccess endAccess=new WorldAccess(false,Set.of("end"),false,false,"safe",true);
    final Set<UUID> redirectInFlight=new HashSet<>();
    final Set<UUID> blockedWorldWarnings=new HashSet<>();
    final World end=new World("end",World.Environment.THE_END);
    final World safe=new World("safe",World.Environment.NORMAL);
    final World other=new World("other",World.Environment.NORMAL);
    final Player player=new Player(end);
    final WorldAccess requested=endAccess;
    WorldRedirectProbe(){
        Bukkit.worlds.clear();Bukkit.scheduler.tasks.clear();
        for(World w:List.of(end,safe,other))Bukkit.worlds.put(w.name,w);
        redirectInFlight.add(player.id);
    }
    boolean isEnabled(){return enabled;}
    Logger getLogger(){return Logger.getLogger("world-redirect-test");}
    String color(String value){return value;}
    Location safeSpawn(World world){return spawnSafe?new Location(world):null;}
    Location findSafeLocation(World world,Location location){return safeSpawn(world);}
    record WorldAccess(boolean enabled,Set<String> worldNames,boolean allowPortals,
                       boolean allowCommandsTeleport,String redirectWorld,boolean redirectToSafeSpawn){}
    static class World {
        enum Environment {NORMAL,NETHER,THE_END}
        final String name;final Environment environment;final UUID id=UUID.randomUUID();
        World(String name,Environment environment){this.name=name;this.environment=environment;}
        String getName(){return name;}Environment getEnvironment(){return environment;}
        UUID getUID(){return id;}Location getSpawnLocation(){return new Location(this);}
    }
    record Location(World world){World getWorld(){return world;}}
    static class Player {
        final UUID id=UUID.randomUUID();World world;boolean online=true,cancelFirst=true;
        int teleports,messages;
        Player(World world){this.world=world;}
        UUID getUniqueId(){return id;}String getName(){return "Synthetic";}
        World getWorld(){return world;}boolean isOnline(){return online;}
        void sendMessage(String s){messages++;}
        boolean teleport(Location where){teleports++;if(cancelFirst&&teleports==1)return false;world=where.world();return true;}
    }
    static class Scheduler {
        final ArrayDeque<Runnable> tasks=new ArrayDeque<>();
        void runTask(Object plugin,Runnable action){tasks.add(action);}
    }
    static class Bukkit {
        static final Map<String,World> worlds=new LinkedHashMap<>();
        static final Scheduler scheduler=new Scheduler();
        static World getWorld(String name){return worlds.get(name);}
        static World getWorld(UUID id){return worlds.values().stream().filter(w->w.id.equals(id)).findFirst().orElse(null);}
        static List<World> getWorlds(){return new ArrayList<>(worlds.values());}
        static Scheduler getScheduler(){return scheduler;}
    }
    void firstCancelled(){
        finishRedirect(player,requested,"redirect",safe,null);
        require(player.teleports==1,"initial legal redirect attempted");
        require(Bukkit.scheduler.tasks.size()==1,"exactly one retry queued");
    }
    void runRetry(){Bukkit.scheduler.tasks.remove().run();}
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        var p=new WorldRedirectProbe();String mode=args[0];
        if(mode.equals("first-target-changed")){
            p.player.cancelFirst=false;
            p.endAccess=new WorldAccess(false,Set.of("end"),false,false,"other",true);
            p.finishRedirect(p.player,p.requested,"redirect",p.safe,null);
            require(p.player.teleports==0,"stale prepared destination must not be used");
        }else{
            p.firstCancelled();
            switch(mode){
                case "source-opened" -> p.endAccess=new WorldAccess(true,Set.of("end"),false,false,"safe",true);
                case "player-moved" -> p.player.world=p.other;
                case "target-blocked" -> p.netherAccess=new WorldAccess(false,Set.of("safe"),false,false,"other",true);
                case "route-changed" -> p.endAccess=new WorldAccess(false,Set.of("end"),false,false,"other",true);
                case "target-unloaded" -> Bukkit.worlds.remove("safe");
                case "spawn-unsafe" -> p.spawnSafe=false;
                case "plugin-disabled" -> p.enabled=false;
                case "offline" -> p.player.online=false;
                case "normal" -> {}
                default -> throw new AssertionError(mode);
            }
            p.runRetry();
            if(mode.equals("normal")){
                require(p.player.teleports==2&&p.player.world==p.safe,"valid retry completes");
                require(p.player.messages==1,"success reported once");
            }else require(p.player.teleports==1,"stale/unsafe retry must never teleport: "+mode);
        }
        require(!p.redirectInFlight.contains(p.player.id),"operation lock always released");
        require(Bukkit.scheduler.tasks.isEmpty(),"no unbounded retries");
        System.out.println("PASS "+mode);
    }
''' + methods + "\n}\n"
    path = out / "WorldRedirectProbe.java"
    path.write_text(java, encoding="utf-8")
    compiled = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(out), str(path)], capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=30)
    assert compiled.returncode == 0, compiled.stderr
    return out


@pytest.mark.parametrize("scenario", ["source-opened", "player-moved", "target-blocked", "route-changed", "target-unloaded", "spawn-unsafe", "plugin-disabled", "offline", "normal", "first-target-changed"])
def test_deferred_redirect_revalidates_actual_world_state(redirect_probe, scenario):
    result = subprocess.run(["java", "-cp", str(redirect_probe), "WorldRedirectProbe", scenario], capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=15)
    assert result.returncode == 0, result.stdout + result.stderr
