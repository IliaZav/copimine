"""Paper dispatches ProjectileLaunchEvent during spawn, before a later setShooter.

Exercise the actual launch/tag methods with that order and the real room adapter.
The ordering was checked against the pinned Paper 26.3 CraftWorld/ServerLevel bytecode.
"""
from pathlib import Path
import subprocess

import pytest
from tests.test_wave7_admission_runtime_adapter import BASE, PRODUCTION, HARNESS
from tests.test_wave7_named_trial_warning_audio_runtime import extract_method

SOURCE = BASE / "CopiMineEndEvent.java"


@pytest.fixture(scope="module")
def scripted_probe(tmp_path_factory):
    out = tmp_path_factory.mktemp("wave7-scripted-launch")
    source = SOURCE.read_text(encoding="utf-8")
    methods = "\n".join(extract_method(source, signature) for signature in (
        "private boolean launchNamedTrialArrow(", "private boolean tagNamedTrialProjectile("))
    methods = methods.replace("org.bukkit.entity.Snowball", "Snowball")
    methods = methods.replace("org.bukkit.entity.AbstractArrow", "AbstractArrow")
    methods = methods.replace("org.bukkit.entity.Arrow", "Arrow")
    code = r'''
import java.util.*;
import me.copimine.endevent.domain.*;
import me.copimine.endevent.runtime.*;
import me.copimine.endevent.runtime.wave7.Wave7AdmissionRuntimeAdapter;
import me.copimine.endevent.runtime.wave7.Wave7AdmissionRuntimeAdapter.Decision;
public class ScriptedLaunchProbe {
    final long generation=Wave7AdmissionRuntimeChecks.GEN;
    final RealitySplitChamberController realitySplitChamberController=Wave7AdmissionRuntimeChecks.chambers;
    Wave7AdmissionRuntimeAdapter wave7Admission=Wave7AdmissionRuntimeChecks.adapter;
    final Set<UUID> realitySplitTrialEntityUuids=new HashSet<>();
    final Map<UUID,Entity> entities=new HashMap<>();
    final Object keyChamberId="room",keyTrialRole="trial",keyTrialIndex="index";
    static final String EVENT_KIND_REALITY_SPLIT_OBJECTIVE="objective";
    Object namedTrialProjectileMarkerKey(){return "projectile";}
    Object namedTrialProjectileSourceKey(){return "source";}
    Object namedTrialProjectileAttackKey(){return "attack";}
    Object namedTrialProjectileExpiryKey(){return "expiry";}
    static class Type<T>{}
    static class PersistentDataType {
        static final Type<String> STRING=new Type<>();static final Type<Integer> INTEGER=new Type<>();
        static final Type<Byte> BYTE=new Type<>();static final Type<Long> LONG=new Type<>();
    }
    static class Data {
        final Map<Object,Object> values=new HashMap<>();
        <T> void set(Object key,Type<T> type,T value){values.put(key,value);}
    }
    enum EntityType {SNOWBALL}
    enum Material {TRIDENT}
    record ItemStack(Material type){}
    static class Vector {
        double x,y,z;Vector(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
        Vector subtract(Vector b){x-=b.x;y-=b.y;z-=b.z;return this;}
        Vector multiply(double m){x*=m;y*=m;z*=m;return this;}
        Vector rotateAroundY(double angle){double nx=x*Math.cos(angle)+z*Math.sin(angle);z=z*Math.cos(angle)-x*Math.sin(angle);x=nx;return this;}
        double lengthSquared(){return x*x+y*y+z*z;}
        Vector normalize(){return multiply(1.0/Math.sqrt(lengthSquared()));}
    }
    static class Location {final double x;Location(double x){this.x=x;}Vector toVector(){return new Vector(x,71.6,0);}}
    static class Entity {
        final UUID id;final Data data=new Data();boolean valid=true;
        Entity(UUID id){this.id=id;}UUID getUniqueId(){return id;}
        Data getPersistentDataContainer(){return data;}boolean isValid(){return valid;}
        void remove(){valid=false;}
    }
    static class LivingEntity extends Entity {
        final World world;LivingEntity(UUID id,World world){super(id);this.world=world;}
        World getWorld(){return world;}Location getEyeLocation(){return new Location(0);}
    }
    static class Player extends LivingEntity {double eyeX=5;Player(UUID id,World w){super(id,w);}@Override Location getEyeLocation(){return new Location(eyeX);}}
    static class Projectile extends Entity {
        LivingEntity shooter;Projectile(UUID id){super(id);}
        void setShooter(LivingEntity actor){shooter=actor;}
        void setVelocity(Vector v){if(!Double.isFinite(v.lengthSquared()))throw new AssertionError("nonfinite velocity");}
        void setGlowing(boolean value){}
    }
    static class AbstractArrow extends Projectile {
        enum PickupStatus {DISALLOWED}AbstractArrow(UUID id){super(id);}
        void setPickupStatus(PickupStatus status){}void setCritical(boolean value){}
    }
    static class Arrow extends AbstractArrow {Arrow(UUID id){super(id);}}
    static class Snowball extends Projectile {Snowball(UUID id){super(id);}void setItem(ItemStack item){}}
    static class World {
        final ScriptedLaunchProbe p;boolean cancel;Projectile spawned;int earlyLaunches;
        World(ScriptedLaunchProbe p){this.p=p;}
        <T extends Projectile>T publish(T shot){
            spawned=shot;p.entities.put(shot.id,shot);earlyLaunches++;
            // This callback occurs inside spawn, before the caller gets the object.
            Decision early=p.wave7Admission.launch(shot.id,shot.shooter==null?null:shot.shooter.id);
            if(early!=Decision.IGNORE)throw new AssertionError("test must model an unset-shooter launch");
            if(cancel)shot.valid=false;
            return shot;
        }
        Arrow spawnArrow(Location from,Vector dir,float speed,float spread){if(!Double.isFinite(dir.lengthSquared()))throw new AssertionError("nonfinite arrow direction");return publish(new Arrow(UUID.randomUUID()));}
        Entity spawnEntity(Location from,EntityType type){return publish(new Snowball(UUID.randomUUID()));}
    }
    static class Scheduler {int tasks;void runTaskLater(Object owner,Runnable action,long ticks){tasks++;}}
    static class Bukkit {static final Scheduler scheduler=new Scheduler();static Scheduler getScheduler(){return scheduler;}}
    boolean isNamedRealitySplitActor(Entity actor){return actor!=null&&actor.valid;}
    boolean isCurrentRealitySplitTrialTarget(LivingEntity actor,Player target){return true;}
    int readInt(Entity e,Object key,int fallback){return (int)e.data.values.getOrDefault(key,fallback);}
    String readString(Entity e,Object key){return (String)e.data.values.get(key);}
    void tag(Entity p,String kind,int wave,boolean official){}
    void removeRealitySplitTrialEntity(UUID id,String reason){Entity e=entities.get(id);if(e!=null)e.remove();realitySplitTrialEntityUuids.remove(id);}
''' + methods + r'''
    public static void main(String[] args){
        Wave7AdmissionRuntimeChecks.boot();var p=new ScriptedLaunchProbe();var world=new World(p);
        boolean spear=args[0].endsWith("spear");boolean cancelled=args[0].startsWith("cancelled");world.cancel=cancelled;
        UUID actorId=spear?Wave7AdmissionRuntimeChecks.ARCHMAGE:Wave7AdmissionRuntimeChecks.MARKSMAN;
        UUID targetId=spear?Wave7AdmissionRuntimeChecks.D:Wave7AdmissionRuntimeChecks.C;
        var actor=new LivingEntity(actorId,world);var target=new Player(targetId,world);
        actor.data.values.put(p.keyChamberId,spear?3:2);actor.data.values.put(p.keyTrialRole,spear?"RIFT_ARCHMAGE":"MOBILE_MARKSMAN");
        var attack=spear?Wave7NamedTrialActorPolicy.Attack.ARCHMAGE_SPEAR:Wave7NamedTrialActorPolicy.Attack.MARKSMAN_QUICK_SHOT;
        boolean coincident=args[0].startsWith("coincident");if(coincident)target.eyeX=0;
        boolean launched=p.launchNamedTrialArrow(actor,target,attack,1.25F,0.0F,false,spear);
        if(coincident){
            if(!launched||world.earlyLaunches!=0||p.wave7Admission.trackedScopes()!=0)
                throw new AssertionError("coincident origin/target must finish as a harmless miss, not spawn NaN velocity");
            return;
        }
        if(world.earlyLaunches!=1)throw new AssertionError("one physical launch expected");
        if(cancelled){
            if(!launched||!p.realitySplitTrialEntityUuids.isEmpty()||p.wave7Admission.trackedScopes()!=0)
                throw new AssertionError("cancelled Paper spawn must resolve harmlessly without authorization or aborting the trial");
        }else{
            if(!launched)throw new AssertionError("valid cast should launch");
            if(p.wave7Admission.damage(world.spawned.id,actor.id,target.id)!=Decision.ALLOW)
                throw new AssertionError("actual scripted projectile lacks room launch receipt after shooter assignment");
            if(p.wave7Admission.damage(world.spawned.id,actor.id,Wave7AdmissionRuntimeChecks.A)!=Decision.DENY)
                throw new AssertionError("registration must not bypass cross-room isolation");
            Wave7AdmissionRuntimeChecks.chambers.clear();
            if(p.wave7Admission.damage(world.spawned.id,actor.id,target.id)!=Decision.DENY)
                throw new AssertionError("stale projectile must stay denied after layout clear");
        }
    }
}
'''
    path=out/"ScriptedLaunchProbe.java";path.write_text(code,encoding="utf-8")
    fixture=out/"Wave7AdmissionRuntimeChecks.java";fixture.write_text(HARNESS,encoding="utf-8")
    files=[str(BASE/rel) for rel in PRODUCTION]+[str(BASE/"domain/Wave7NamedTrialActorPolicy.java"),str(fixture),str(path)]
    result=subprocess.run(["javac","-encoding","UTF-8","-d",str(out),*files],capture_output=True,text=True,encoding="utf-8",errors="replace",timeout=30)
    assert result.returncode==0,result.stdout+result.stderr
    return out


@pytest.mark.parametrize("scenario", ["valid-arrow", "valid-spear", "cancelled-arrow", "cancelled-spear", "coincident-arrow", "coincident-spear"])
def test_scripted_projectile_is_admitted_only_after_valid_owned_spawn(scripted_probe, scenario):
    run=subprocess.run(["java","-cp",str(scripted_probe),"ScriptedLaunchProbe",scenario],capture_output=True,text=True,encoding="utf-8",errors="replace",timeout=15)
    assert run.returncode==0,run.stdout+run.stderr
