"""Execute the real W6 guard adapters at detached Paper movement/visibility boundaries."""

from pathlib import Path
import subprocess

import pytest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def declaration(source, signature):
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


@pytest.fixture(scope="module")
def guard_probe(tmp_path_factory):
    source = SOURCE.read_text(encoding="utf-8")
    methods = "\n".join(declaration(source, signature) for signature in (
        "private void tickRitualGuardPost(",
        "private boolean ritualGuardTargetAllowed(",
        "private boolean isRitualGuardAbilityCastValid(",
    ))
    directory = tmp_path_factory.mktemp("guard-tactics")
    fixture = directory / "GuardTacticsProbe.java"
    fixture.write_text(r'''
import java.util.*;
import me.copimine.endevent.domain.RitualGuardAggroPolicy;
public class GuardTacticsProbe {
    long generation=1, ritualGuardAbilityGeneration=1, eventTickCounter;
    UUID ritualGuardAbilityGuard, ritualGuardAbilityTarget;
    Object ritualGuardAbilityProfile=new Object();
    Location ritualGuardAbilityOrigin=new Location(0,0), ritualGuardAbilityImpact=new Location(2,0);
    static final long WAVE_PATH_REQUEST_INTERVAL_MILLIS=1000;
    static final String EVENT_KIND_RITUAL_GUARD="RITUAL_GUARD";
    Map<UUID,Long> nextWavePathRequestMillis=new HashMap<>(), ritualCasterAlertUntil=new HashMap<>(),
        wavePlayerAggroUntil=new HashMap<>();
    Map<UUID,UUID> ritualGuardCasters=new HashMap<>();
    Map<UUID,Entity> ownedEntities=new HashMap<>();
    static class World { }
    static final World WORLD=new World();
    static class Vector {
        double x,z; Vector(){ } Vector(double x,double z){this.x=x;this.z=z;}
        Vector subtract(Vector v){x-=v.x;z-=v.z;return this;} Vector setY(double y){return this;}
    }
    static class Location {
        double x,z; Location(double x,double z){this.x=x;this.z=z;}
        public Location clone(){return new Location(x,z);} Vector toVector(){return new Vector(x,z);}
        Location add(Vector v){x+=v.x;z+=v.z;return this;}
    }
    static class Entity {
        UUID id=UUID.randomUUID(); Location pos;
        Entity(double x,double z){pos=new Location(x,z);}
        UUID getUniqueId(){return id;} Location getLocation(){return pos;} World getWorld(){return WORLD;}
    }
    static class Player extends Entity {
        boolean online=true, dead; Player(double x,double z){super(x,z);}
        Location getEyeLocation(){return pos;} boolean isOnline(){return online;}
        boolean isDead(){return dead;} double getHealth(){return dead?0:20;}
    }
    static class Path {
        int moves, stops; boolean active;
        void stopPathfinding(){stops++;active=false;}
        boolean hasPath(){return active;}
        boolean moveTo(Location p,double speed){moves++;active=true;return true;}
        boolean moveTo(Player p,double speed){return moveTo(p.getLocation(),speed);}
    }
    static class Mob extends Entity {
        Player target; Path path=new Path(); boolean ai, aware, los=true;
        Mob(double x,double z){super(x,z);} Player getTarget(){return target;}
        void setTarget(Player p){target=p;} Path getPathfinder(){return path;}
        void setAI(boolean value){ai=value;} void setAware(boolean value){aware=value;}
        void setVelocity(Vector v){ } boolean hasLineOfSight(Player p){return los;}
    }
    static class Skeleton extends Mob { Skeleton(double x,double z){super(x,z);} }
    static class Bukkit {
        static Map<UUID,Player> players=new HashMap<>();
        static Player getPlayer(UUID id){return players.get(id);}
    }
    Location ritualGuardPostLocation(Mob guard,Mob caster,Location core){return new Location(2.4,0);}
    static double horizontalDistanceSquared(Location a,Location b){return (a.x-b.x)*(a.x-b.x)+(a.z-b.z)*(a.z-b.z);}
    void faceRitualCaster(Mob mob,Location target){ }
    Player ritualNearestTarget(Location origin,double radius){return null;}
    void maintainSkeletonCombatPosture(Skeleton skeleton,Player target,String kind,long now){ }
    boolean isCurrentRitualGuard(Entity guard){return ritualGuardCasters.containsKey(guard.id);}
    boolean ritualTargetAllowed(Entity guard,Player player){return player.online&&!player.dead;}
    boolean isLiveOwnedEntity(UUID id){return ownedEntities.containsKey(id);}
    METHODS
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        var f=new GuardTacticsProbe(); var guard=new Mob(2.4,0); var caster=new Mob(0,0);
        var target=new Player(2,0); f.ritualGuardCasters.put(guard.id,caster.id);
        f.ownedEntities.put(guard.id,guard);f.ownedEntities.put(caster.id,caster);
        switch(args[0]){
            case "leash" -> {
                f.ritualCasterAlertUntil.put(caster.id,Long.MAX_VALUE);
                target.pos=new Location(15,0);
                check(!f.ritualGuardTargetAllowed(guard,target),"alarm must not send a posted guard after a player beyond its caster leash");
                target.pos=new Location(14,0);
                check(f.ritualGuardTargetAllowed(guard,target),"an attacking player at the leash boundary remains eligible");
            }
            case "hysteresis" -> {
                guard.target=target; target.pos=new Location(3.8,0);
                check(f.ritualGuardTargetAllowed(guard,target),"engaged guard must not disengage as soon as the player crosses the three-block wake radius");
                target.pos=new Location(6.01,0);
                check(!f.ritualGuardTargetAllowed(guard,target),"unprovoked interception ends outside the six-block holding radius");
                guard.target=null; target.pos=new Location(3.8,0);
                check(!f.ritualGuardTargetAllowed(guard,target),"an idle guard must keep its three-block wake radius");
            }
            case "return" -> {
                guard.pos=new Location(7,0);
                for(long now=0;now<=1000;now+=250)f.tickRitualGuardPost(guard,caster,caster.pos,null,now);
                check(guard.path.stops==0,"returning guard must not stop its home path on every encounter tick");
                check(guard.path.moves==2,"home path requests must respect the one-second request interval");
                guard.pos=new Location(2.4,0);f.tickRitualGuardPost(guard,caster,caster.pos,null,1250);
                check(!guard.ai&&!guard.aware&&!guard.path.active,"arrived guard holds its post without native wandering");
            }
            case "combat-return" -> {
                guard.pos=new Location(7,0);guard.target=target;guard.path.active=true;
                f.nextWavePathRequestMillis.put(guard.id,1000L);
                f.tickRitualGuardPost(guard,caster,caster.pos,null,250);
                check(guard.target==null&&guard.path.stops==1&&guard.path.moves==1,
                    "lost combat target must cancel the chase and start the home path immediately");
                f.tickRitualGuardPost(guard,caster,caster.pos,null,500);
                check(guard.path.stops==1&&guard.path.moves==1,"home path must survive the following tick");
            }
            case "visibility" -> {
                f.ritualGuardAbilityGuard=guard.id;f.ritualGuardAbilityTarget=target.id;
                Bukkit.players.put(target.id,target);
                check(f.isRitualGuardAbilityCastValid(),"visible nearby target retains its committed cast");
                guard.los=false;
                check(!f.isRitualGuardAbilityCastValid(),"guard must cancel its committed cast when the target takes cover");
                guard.los=true;f.generation=2;
                check(!f.isRitualGuardAbilityCastValid(),"stale generation must cancel its committed cast");
            }
        }
        System.out.println("GuardTacticsProbe OK "+args[0]);
    }
}
'''.replace("METHODS", methods), encoding="utf-8")
    policy = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/RitualGuardAggroPolicy.java"
    compiled = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(directory), str(policy), str(fixture)],
                              capture_output=True, text=True, timeout=45)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    return directory


@pytest.mark.parametrize("scenario", ["leash", "hysteresis", "return", "combat-return", "visibility"])
def test_actual_guard_tactics(guard_probe, scenario):
    result = subprocess.run(["java", "-cp", str(guard_probe), "GuardTacticsProbe", scenario],
                            capture_output=True, text=True, timeout=30)
    assert result.returncode == 0, result.stdout + result.stderr
