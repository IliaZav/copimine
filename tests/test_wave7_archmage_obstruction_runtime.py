"""Ordinary cover/blocked summon geometry must not deadlock or abort named trials."""
from pathlib import Path
import subprocess

import pytest

from tests.test_wave7_named_trial_warning_audio_runtime import extract_method

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def compile_probe(directory, name, body):
    path = directory / (name + ".java")
    path.write_text(body, encoding="utf-8")
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(directory), str(path)],
                            capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=30)
    assert result.returncode == 0, result.stdout + result.stderr
    return directory, name


def run_probe(probe, scenario):
    directory, name = probe
    result = subprocess.run(["java", "-cp", str(directory), name, scenario], capture_output=True,
                            text=True, encoding="utf-8", errors="replace", timeout=15)
    assert result.returncode == 0, result.stdout + result.stderr


@pytest.fixture(scope="module")
def resolve_probe(tmp_path_factory):
    directory = tmp_path_factory.mktemp("archmage-cover-runtime")
    method = extract_method(SOURCE.read_text(encoding="utf-8"), "private boolean resolveNamedTrialAttack(")
    return compile_probe(directory, "ArchmageCoverProbe", r'''
public class ArchmageCoverProbe {
    static class Wave7NamedTrialActorPolicy {
        enum Attack { NONE, MARKSMAN_QUICK_SHOT, MARKSMAN_HEAVY_SHOT, MARKSMAN_SPREAD_SHOT,
            MARKSMAN_SUPPORT, ARCHMAGE_FANGS, ARCHMAGE_SPEAR, ARCHMAGE_STEP, ARCHMAGE_BEAM }
    }
    enum Particle { HAPPY_VILLAGER }
    static class Location { Location add(double x,double y,double z){return this;} }
    static class LivingEntity {
        double hp=100;
        double getMaxHealth(){return 200;}double getHealth(){return hp;}void setHealth(double hp){this.hp=hp;}
        Location getLocation(){return new Location();}Location getEyeLocation(){return new Location();}
    }
    static class Player extends LivingEntity {}
    boolean visible=false,eligible=true;int steps,attacks;
    boolean isCurrentRealitySplitTrialTarget(LivingEntity a,Player p){return eligible;}
    boolean hasNamedTrialLineOfSight(Location a,Location b){return visible;}
    boolean launchNamedTrialArrow(LivingEntity a,Player t,Wave7NamedTrialActorPolicy.Attack x,float s,float spread,boolean g,boolean spear){attacks++;return true;}
    boolean spawnNamedTrialFangs(LivingEntity a,Player t,Wave7NamedTrialActorPolicy.Attack x){attacks++;return true;}
    boolean stepNamedTrialArchmage(LivingEntity a,Player p,int c){steps++;return true;}
    void resolveNamedTrialBeam(LivingEntity a,Player p,int c){attacks++;}
    void spawnEventParticle(Location l,Particle p,int n,double x,double y,double z,double v){}
''' + method + r'''
    public static void main(String[] args){
        var p=new ArchmageCoverProbe();var actor=new LivingEntity();var target=new Player();
        var attack=Wave7NamedTrialActorPolicy.Attack.valueOf(args[0]);
        if(!p.resolveNamedTrialAttack(actor,target,attack,0))throw new AssertionError("legal blocked action should finish safely");
        if(attack==Wave7NamedTrialActorPolicy.Attack.ARCHMAGE_STEP){
            if(p.steps!=1)throw new AssertionError("Step must reach its own safe relocation checks when target is behind cover");
        }else if(p.attacks!=0||p.steps!=0)throw new AssertionError("Direct attack must remain blocked by cover");
    }
}
''')


@pytest.mark.parametrize("attack", ["ARCHMAGE_STEP", "ARCHMAGE_FANGS", "ARCHMAGE_SPEAR", "ARCHMAGE_BEAM", "MARKSMAN_QUICK_SHOT"])
def test_cover_blocks_damage_but_not_safe_tactical_step(resolve_probe, attack):
    run_probe(resolve_probe, attack)


@pytest.fixture(scope="module")
def fangs_probe(tmp_path_factory):
    directory = tmp_path_factory.mktemp("archmage-fangs-runtime")
    source = SOURCE.read_text(encoding="utf-8")
    method = "\n".join(extract_method(source, signature) for signature in (
        "private boolean spawnNamedTrialFangs(", "private boolean tagNamedTrialProjectile("))
    # Replace only the API type's qualification, not any production branch.
    method = method.replace("org.bukkit.entity.EvokerFangs", "EvokerFangs")
    method = method.replace("me.copimine.endevent.runtime.wave7.Wave7AdmissionRuntimeAdapter.Decision", "Decision")
    return compile_probe(directory, "ArchmageFangsProbe", r'''
import java.util.*;
public class ArchmageFangsProbe {
    static class Wave7NamedTrialActorPolicy { enum Attack { ARCHMAGE_FANGS } }
    enum EntityType { EVOKER_FANGS }
    static class Vector {
        double x,y,z;Vector(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
        double getX(){return x;}double getZ(){return z;}
        public Vector clone(){return new Vector(x,y,z);}Vector multiply(double m){x*=m;y*=m;z*=m;return this;}
    }
    static class Location {
        double x,y,z;Location(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
        double getY(){return y;}void setY(double y){this.y=y;}
        public Location clone(){return new Location(x,y,z);}Location add(Vector v){x+=v.x;y+=v.y;z+=v.z;return this;}
    }
    static class Type<T>{}
    static class PersistentDataType {
        static final Type<String> STRING=new Type<>();static final Type<Integer> INTEGER=new Type<>();
        static final Type<Byte> BYTE=new Type<>();static final Type<Long> LONG=new Type<>();
    }
    static class Data {final Map<Object,Object> values=new HashMap<>();<T>void set(Object k,Type<T>t,T v){values.put(k,v);}}
    static class Entity {
        final UUID id=UUID.randomUUID();final Data data=new Data();boolean valid=true,removed;
        UUID getUniqueId(){return id;}boolean isValid(){return valid&&!removed;}
        Data getPersistentDataContainer(){return data;}void remove(){removed=true;}
    }
    static class Projectile extends Entity {}
    static class LivingEntity extends Entity {
        final World world;LivingEntity(World w){world=w;}World getWorld(){return world;}
        Location getLocation(){return new Location(0,70,0);}Location getEyeLocation(){return new Location(0,71.6,0);}
    }
    static class Player extends LivingEntity {Player(World w){super(w);}}
    static class EvokerFangs extends Entity {void setOwner(LivingEntity actor){}}
    static class World {
        final Map<UUID,Entity> spawned=new LinkedHashMap<>();
        int spawns,cancelledAt;
        Entity spawnEntity(Location at,EntityType type){var f=new EvokerFangs();if(++spawns==cancelledAt)f.valid=false;spawned.put(f.id,f);return f;}
    }
    static class Scheduler {int tasks;void runTaskLater(Object plugin,Runnable callback,long ticks){tasks++;}}
    static class Bukkit {static final Scheduler scheduler=new Scheduler();static Scheduler getScheduler(){return scheduler;}}
    enum Decision { ALLOW }
    static class Admission {Decision launch(UUID projectile,UUID actor){return Decision.ALLOW;}}
    class Chambers {
        boolean owns(long generation){return true;}
        boolean assignEntity(long generation,UUID entity,int chamber){return !(tagFailure&&++assignments==2);}
    }
    final World world=new World();int checks,assignments;int blockedAt;boolean tagFailure;
    final long generation=7L;final Chambers realitySplitChamberController=new Chambers();
    final Admission wave7Admission=new Admission();final Set<UUID> realitySplitTrialEntityUuids=new HashSet<>();
    final Object keyTrialRole="role",keyTrialIndex="index",keyChamberId="chamber";
    static final String EVENT_KIND_REALITY_SPLIT_OBJECTIVE="objective";
    Object namedTrialProjectileMarkerKey(){return "marker";}Object namedTrialProjectileSourceKey(){return "source";}
    Object namedTrialProjectileAttackKey(){return "attack";}Object namedTrialProjectileExpiryKey(){return "expiry";}
    int readInt(Entity e,Object key,int fallback){return 0;}String readString(Entity e,Object key){return "RIFT_ARCHMAGE";}
    boolean isNamedRealitySplitActor(Entity e){return e.isValid();}void tag(Entity e,String kind,int wave,boolean official){}
    boolean isCurrentRealitySplitTrialTarget(LivingEntity a,Player p){return true;}
    boolean hasNamedTrialLineOfSight(Location a,Location b){return true;}
    Vector horizontalDirection(Location a,Location b){return new Vector(1,0,0);}
    boolean isSafeCombatEntityLocation(Location l,LivingEntity a){return ++checks!=blockedAt;}
    void removeRealitySplitTrialEntity(UUID id,String reason){Entity e=world.spawned.get(id);if(e!=null)e.remove();realitySplitTrialEntityUuids.remove(id);}
''' + method + r'''
    public static void main(String[] args){
        var p=new ArchmageFangsProbe();String mode=args[0];
        p.blockedAt=mode.equals("blocked-first")?1:mode.equals("blocked-second")?2:0;
        p.world.cancelledAt=mode.equals("cancelled-first")?1:mode.equals("cancelled-second")?2:0;
        p.tagFailure=mode.equals("broken-ownership");
        boolean handled=p.spawnNamedTrialFangs(new LivingEntity(p.world),new Player(p.world),Wave7NamedTrialActorPolicy.Attack.ARCHMAGE_FANGS);
        long live=p.world.spawned.values().stream().filter(Entity::isValid).count();
        if(mode.startsWith("blocked")||mode.startsWith("cancelled")){
            if(!handled)throw new AssertionError("Blocked/cancelled fang spawn is an ordinary miss, not WAVE7_NAMED_ACTOR_RUNTIME_FAILED");
            if(live!=0||!p.realitySplitTrialEntityUuids.isEmpty())throw new AssertionError("Partial fang cast must roll back all actors and ownership");
        }else if(mode.equals("broken-ownership")){
            if(handled||live!=0||!p.realitySplitTrialEntityUuids.isEmpty())throw new AssertionError("Failed actor ownership must still fail closed and roll back");
        }else if(!handled||live!=3||p.realitySplitTrialEntityUuids.size()!=3)throw new AssertionError("Unobstructed cast must create exactly three owned fangs");
    }
}
''')


@pytest.mark.parametrize("scenario", ["blocked-first", "blocked-second", "cancelled-first", "cancelled-second", "normal", "broken-ownership"])
def test_blocked_fangs_resolve_as_safe_miss_without_aborting_wave(fangs_probe, scenario):
    run_probe(fangs_probe, scenario)
