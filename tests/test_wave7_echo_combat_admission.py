"""Run the actual pair listener; portable boundaries are not a Minecraft playtest."""
from pathlib import Path
import subprocess

import pytest

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "copimine-end-event/src/me/copimine/endevent"


@pytest.fixture(scope="module")
def admission_probe(tmp_path_factory):
    directory = tmp_path_factory.mktemp("echo-combat-admission")
    stubs = {
        "org/bukkit/World.java": "package org.bukkit;public record World(java.util.UUID uid){public java.util.UUID getUID(){return uid;}}",
        "org/bukkit/projectiles/ProjectileSource.java": "package org.bukkit.projectiles;public interface ProjectileSource {}",
        "org/bukkit/entity/Entity.java": """package org.bukkit.entity;public class Entity {
 public final java.util.UUID id;public org.bukkit.World world;public boolean valid=true,failRemove,persistent=true;public int removals;
 public Entity(java.util.UUID id,org.bukkit.World w){this.id=id;world=w;}public java.util.UUID getUniqueId(){return id;}
 public org.bukkit.World getWorld(){return world;}public boolean isValid(){return valid;}
 public void setPersistent(boolean value){persistent=value;}
 public void remove(){if(failRemove){failRemove=false;throw new IllegalStateException("remove retry");}removals++;valid=false;}
} """,
        "org/bukkit/entity/LivingEntity.java": "package org.bukkit.entity;public class LivingEntity extends Entity implements org.bukkit.projectiles.ProjectileSource {public LivingEntity(java.util.UUID id,org.bukkit.World w){super(id,w);}}",
        "org/bukkit/entity/Projectile.java": "package org.bukkit.entity;public class Projectile extends Entity {public org.bukkit.projectiles.ProjectileSource shooter;public Projectile(java.util.UUID id,org.bukkit.World w,org.bukkit.projectiles.ProjectileSource s){super(id,w);shooter=s;}public org.bukkit.projectiles.ProjectileSource getShooter(){return shooter;}}",
        "org/bukkit/entity/AbstractArrow.java": "package org.bukkit.entity;public class AbstractArrow extends Projectile {public enum PickupStatus {ALLOWED,DISALLOWED};public PickupStatus pickup=PickupStatus.ALLOWED;public AbstractArrow(java.util.UUID id,org.bukkit.World w,org.bukkit.projectiles.ProjectileSource s){super(id,w,s);}public void setPickupStatus(PickupStatus p){pickup=p;}}",
        "org/bukkit/entity/ThrownPotion.java": "package org.bukkit.entity;public class ThrownPotion extends Projectile {public ThrownPotion(java.util.UUID id,org.bukkit.World w,org.bukkit.projectiles.ProjectileSource s){super(id,w,s);}}",
        "org/bukkit/entity/AreaEffectCloud.java": "package org.bukkit.entity;public class AreaEffectCloud extends Entity {public org.bukkit.projectiles.ProjectileSource source;public AreaEffectCloud(java.util.UUID id,org.bukkit.World w,org.bukkit.projectiles.ProjectileSource s){super(id,w);source=s;}public org.bukkit.projectiles.ProjectileSource getSource(){return source;}}",
        "org/bukkit/damage/DamageSource.java": "package org.bukkit.damage;public record DamageSource(org.bukkit.entity.Entity direct,org.bukkit.entity.Entity causing){public org.bukkit.entity.Entity getDirectEntity(){return direct;}public org.bukkit.entity.Entity getCausingEntity(){return causing;}}",
        "org/bukkit/event/Listener.java": "package org.bukkit.event;public interface Listener {}",
        "org/bukkit/event/EventPriority.java": "package org.bukkit.event;public enum EventPriority {LOWEST,LOW,NORMAL,HIGH,HIGHEST,MONITOR}",
        "org/bukkit/event/EventHandler.java": "package org.bukkit.event;@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface EventHandler {EventPriority priority();boolean ignoreCancelled();}",
        "org/bukkit/event/entity/EntityDamageEvent.java": "package org.bukkit.event.entity;public class EntityDamageEvent {public boolean cancelled;org.bukkit.entity.Entity entity;org.bukkit.damage.DamageSource source;public EntityDamageEvent(org.bukkit.entity.Entity e,org.bukkit.damage.DamageSource s){entity=e;source=s;}public org.bukkit.entity.Entity getEntity(){return entity;}public org.bukkit.damage.DamageSource getDamageSource(){return source;}public boolean isCancelled(){return cancelled;}public void setCancelled(boolean c){cancelled=c;}}",
        "org/bukkit/event/entity/EntityDamageByEntityEvent.java": "package org.bukkit.event.entity;public class EntityDamageByEntityEvent extends EntityDamageEvent {org.bukkit.entity.Entity damager;public EntityDamageByEntityEvent(org.bukkit.entity.Entity d,org.bukkit.entity.Entity e,org.bukkit.damage.DamageSource s){super(e,s);damager=d;}public org.bukkit.entity.Entity getDamager(){return damager;}}",
        "org/bukkit/event/entity/ProjectileLaunchEvent.java": "package org.bukkit.event.entity;public class ProjectileLaunchEvent {public boolean cancelled;org.bukkit.entity.Projectile entity;public ProjectileLaunchEvent(org.bukkit.entity.Projectile e){entity=e;}public org.bukkit.entity.Projectile getEntity(){return entity;}public boolean isCancelled(){return cancelled;}public void setCancelled(boolean c){cancelled=c;}}",
        "org/bukkit/event/entity/ProjectileHitEvent.java": "package org.bukkit.event.entity;public class ProjectileHitEvent extends ProjectileLaunchEvent {org.bukkit.entity.Entity target;public ProjectileHitEvent(org.bukkit.entity.Projectile e,org.bukkit.entity.Entity t){super(e);target=t;}public org.bukkit.entity.Entity getHitEntity(){return target;}}",
        "org/bukkit/event/entity/PotionSplashEvent.java": "package org.bukkit.event.entity;public class PotionSplashEvent extends ProjectileHitEvent {public java.util.Map<org.bukkit.entity.LivingEntity,Double> affected=new java.util.LinkedHashMap<>();public PotionSplashEvent(org.bukkit.entity.ThrownPotion p,org.bukkit.entity.LivingEntity...targets){super(p,null);for(var t:targets)affected.put(t,1.0);}public org.bukkit.entity.ThrownPotion getEntity(){return (org.bukkit.entity.ThrownPotion)super.getEntity();}public java.util.Collection<org.bukkit.entity.LivingEntity> getAffectedEntities(){return new java.util.ArrayList<>(affected.keySet());}public void setIntensity(org.bukkit.entity.LivingEntity t,double v){affected.put(t,v);}}",
        "org/bukkit/event/entity/LingeringPotionSplashEvent.java": "package org.bukkit.event.entity;public class LingeringPotionSplashEvent extends ProjectileHitEvent {org.bukkit.entity.AreaEffectCloud cloud;public LingeringPotionSplashEvent(org.bukkit.entity.ThrownPotion p,org.bukkit.entity.AreaEffectCloud c){super(p,null);cloud=c;}public org.bukkit.entity.ThrownPotion getEntity(){return (org.bukkit.entity.ThrownPotion)super.getEntity();}public org.bukkit.entity.AreaEffectCloud getAreaEffectCloud(){return cloud;}}",
        "org/bukkit/event/entity/AreaEffectCloudApplyEvent.java": "package org.bukkit.event.entity;public class AreaEffectCloudApplyEvent {public boolean cancelled;org.bukkit.entity.AreaEffectCloud entity;public java.util.List<org.bukkit.entity.LivingEntity> affected;public AreaEffectCloudApplyEvent(org.bukkit.entity.AreaEffectCloud c,org.bukkit.entity.LivingEntity...targets){entity=c;affected=new java.util.ArrayList<>(java.util.List.of(targets));}public org.bukkit.entity.AreaEffectCloud getEntity(){return entity;}public java.util.List<org.bukkit.entity.LivingEntity> getAffectedEntities(){return affected;}public boolean isCancelled(){return cancelled;}}",
        "org/bukkit/event/entity/EntityCombustByEntityEvent.java": "package org.bukkit.event.entity;public class EntityCombustByEntityEvent {public boolean cancelled;org.bukkit.entity.Entity entity,source;public EntityCombustByEntityEvent(org.bukkit.entity.Entity s,org.bukkit.entity.Entity t){source=s;entity=t;}public org.bukkit.entity.Entity getEntity(){return entity;}public org.bukkit.entity.Entity getCombuster(){return source;}public boolean isCancelled(){return cancelled;}public void setCancelled(boolean c){cancelled=c;}}",
        "org/bukkit/event/entity/EntityTargetLivingEntityEvent.java": "package org.bukkit.event.entity;public class EntityTargetLivingEntityEvent {public boolean cancelled;org.bukkit.entity.Entity entity;org.bukkit.entity.LivingEntity target;public EntityTargetLivingEntityEvent(org.bukkit.entity.Entity s,org.bukkit.entity.LivingEntity t){entity=s;target=t;}public org.bukkit.entity.Entity getEntity(){return entity;}public org.bukkit.entity.LivingEntity getTarget(){return target;}public boolean isCancelled(){return cancelled;}public void setCancelled(boolean c){cancelled=c;}}",
    }
    for relative, source in stubs.items():
        target = directory / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(source, encoding="utf-8")
    production = []
    policy = BASE / "domain/wave7/EchoCombatAdmission.java"
    listener = BASE / "runtime/wave7/EchoCombatAdmissionListener.java"
    # At the baseline no admission callback exists; execute its permissive
    # behavior rather than manufacturing a compiler/import failure as RED.
    if policy.exists():
        production.append(str(policy))
    else:
        fallback = directory / "EchoCombatAdmission.java"
        fallback.write_text("""package me.copimine.endevent.domain.wave7;import java.util.UUID;
public final class EchoCombatAdmission {
 public record Pair(UUID event,long generation,long epoch,UUID duel,UUID owner,UUID actor,UUID world){}
 public record Context(Pair pair,long tick,boolean active){}
} """, encoding="utf-8")
    if listener.exists():
        production.append(str(listener))
    else:
        fallback = directory / "EchoCombatAdmissionListener.java"
        fallback.write_text("""package me.copimine.endevent.runtime.wave7;
import java.util.function.Supplier;import me.copimine.endevent.domain.wave7.EchoCombatAdmission.Context;
import org.bukkit.event.entity.*;public final class EchoCombatAdmissionListener {
 public EchoCombatAdmissionListener(Supplier<Context> current){}
 public void onDamage(EntityDamageEvent e){}public void onLaunch(ProjectileLaunchEvent e){}
 public void onHit(ProjectileHitEvent e){}public void onSplash(PotionSplashEvent e){}
 public void onLingering(LingeringPotionSplashEvent e){}public void onCloud(AreaEffectCloudApplyEvent e){}
 public void onCombust(EntityCombustByEntityEvent e){}public void onTarget(EntityTargetLivingEntityEvent e){}
 public void tick(){}public void clear(){}public int trackedProjectiles(){return 0;}public int trackedClouds(){return 0;}
} """, encoding="utf-8")
    harness = directory / "EchoAdmissionChecks.java"
    harness.write_text(r'''
import java.util.*;import org.bukkit.*;import org.bukkit.entity.*;import org.bukkit.damage.*;import org.bukkit.event.entity.*;
import me.copimine.endevent.domain.wave7.EchoCombatAdmission.*;
import me.copimine.endevent.runtime.wave7.EchoCombatAdmissionListener;
public class EchoAdmissionChecks {
 static UUID id(int n){return new UUID(0,n);}static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static World world=new World(id(7));static LivingEntity owner=new LivingEntity(id(1),world),actor=new LivingEntity(id(2),world),other=new LivingEntity(id(3),world),pet=new LivingEntity(id(4),world);
 static Context[] context={new Context(new Pair(id(5),9,10,id(6),owner.id,actor.id,world.uid()),100,true)};
 static EchoCombatAdmissionListener listener=new EchoCombatAdmissionListener(()->context[0]);static int serial=1000;
 static Projectile arrow(LivingEntity shooter){return new AbstractArrow(id(serial++),world,shooter);}
 static ProjectileLaunchEvent launch(Projectile p){var e=new ProjectileLaunchEvent(p);listener.onLaunch(e);return e;}
 static EntityDamageEvent damage(Entity direct,Entity causing,Entity target){var e=new EntityDamageEvent(target,new DamageSource(direct,causing));listener.onDamage(e);return e;}
 static ThrownPotion potion(LivingEntity shooter){return new ThrownPotion(id(serial++),world,shooter);}
 static void change(long generation,long epoch,boolean active,long tick){var p=context[0].pair();context[0]=new Context(new Pair(p.event(),generation,epoch,p.duel(),p.owner(),p.actor(),p.world()),tick,active);}
 public static void main(String[] args){String s=args[0];
 switch(s){
  case "owner-melee":case "owner-thorns": require(!damage(owner,owner,actor).cancelled,"own counterpart hit cancelled");break;
  case "echo-melee": require(!damage(actor,actor,owner).cancelled,"Echo cannot hit its owner");break;
  case "foreign-melee":case "foreign-thorns":require(damage(other,other,actor).cancelled,"foreign entity influenced Echo");break;
  case "foreign-owner-hit":require(damage(other,other,owner).cancelled,"outsider influenced owner during private slice");break;
  case "cross-sweep": require(damage(owner,owner,other).cancelled,"owner sweep leaked outside its pair");break;
  case "echo-foreign": require(damage(actor,actor,other).cancelled,"Echo hit a foreign target");break;
  case "owner-pet":require(damage(pet,owner,actor).cancelled,"owner pet was falsely treated as the owner");break;
  case "forged-causing":require(damage(owner,other,actor).cancelled,"contradictory causing source admitted");break;
  case "unrelated":require(!damage(other,other,pet).cancelled,"ordinary foreign damage changed");break;
  case "environment":require(!damage(null,null,actor).cancelled,"native environmental physics changed");break;
  case "no-scene":context[0]=null;require(!damage(other,other,owner).cancelled,"protection escaped its local lifetime");break;
  case "legacy-foreign-source":case "legacy-owner-source":{
   var e=new EntityDamageByEntityEvent(s.equals("legacy-owner-source")?owner:other,actor,new DamageSource(null,null));listener.onDamage(e);
   require(e.cancelled==s.equals("legacy-foreign-source"),"entity event without DamageSource bypassed admission or lost native owner hit");break;}
  case "disagrees-with-damager":{var e=new EntityDamageByEntityEvent(other,actor,new DamageSource(owner,owner));listener.onDamage(e);
   require(e.cancelled,"claimed DamageSource overrode a foreign event damager");break;}
  case "self-potion-damage":{var p=potion(owner);launch(p);require(!damage(p,owner,owner).cancelled,"admitted native potion lost its self effect");break;}
  case "cancelled-damage":{var e=new EntityDamageEvent(actor,new DamageSource(owner,owner));e.cancelled=true;listener.onDamage(e);require(e.cancelled,"admission reopened a prior cancellation");break;}
  case "invalid-handle-cleanup":{var p=arrow(owner);launch(p);p.valid=false;listener.tick();require(listener.trackedProjectiles()==0&&p.removals==0,"removed projectile receipt retained or double removed");break;}
  case "source-world-cleanup":{var p=arrow(owner);launch(p);p.world=new World(id(55));listener.tick();require(!p.valid&&listener.trackedProjectiles()==0,"changed-world private source survived");break;}
  case "no-persistent-source":{var p=arrow(actor);launch(p);var pot=potion(owner);launch(pot);var c=new AreaEffectCloud(id(serial++),world,owner);listener.onLingering(new LingeringPotionSplashEvent(pot,c));
   require(!p.persistent&&!pot.persistent&&!c.persistent,"unloaded private sources can be saved and return without their receipts");break;}
  case "owner-arrow":case "echo-arrow":case "untracked-arrow":case "foreign-arrow":case "changed-shooter":case "stale-generation":case "stale-epoch":case "paused":case "expired":case "backward-time":case "duplicate-launch":case "arrow-cross-hit":{
   boolean fromEcho=s.equals("echo-arrow");var p=arrow(s.equals("foreign-arrow")?other:fromEcho?actor:owner);
   if(!Set.of("untracked-arrow","foreign-arrow").contains(s))launch(p);
   if(s.equals("changed-shooter"))p.shooter=other;
   if(s.equals("stale-generation"))change(10,10,true,101);
   if(s.equals("stale-epoch"))change(9,11,true,101);
   if(s.equals("paused"))change(9,10,false,101);
   if(s.equals("expired"))change(9,10,true,1300);
   if(s.equals("backward-time"))change(9,10,true,99);
   if(s.equals("duplicate-launch")){change(9,10,true,1299);launch(p);change(9,10,true,1300);}
   var target=s.equals("arrow-cross-hit")?other:fromEcho?owner:actor;
   boolean allowed=Set.of("owner-arrow","echo-arrow").contains(s);
   require(damage(p,(Entity)p.shooter,target).cancelled!=allowed,"projectile source/lifetime admission: "+s);
   if(fromEcho)require(((AbstractArrow)p).pickup==AbstractArrow.PickupStatus.DISALLOWED,"replica arrow can become a real item");
   break;}
  case "wrong-world":{var p=arrow(owner);p.world=new World(id(55));require(launch(p).cancelled,"foreign-world paired launch accepted");break;}
  case "launch-cap":{for(int i=0;i<64;i++)require(!launch(arrow(owner)).cancelled,"launch budget shrunk");require(launch(arrow(owner)).cancelled&&listener.trackedProjectiles()==64,"projectile capacity is not bounded/fail-closed");break;}
  case "cancelled-launch":{var p=arrow(owner);var e=new ProjectileLaunchEvent(p);e.cancelled=true;listener.onLaunch(e);require(listener.trackedProjectiles()==0,"cancelled launch got a valid receipt");break;}
  case "foreign-hit":case "own-hit":{var p=arrow(owner);launch(p);var e=new ProjectileHitEvent(p,s.equals("own-hit")?actor:other);listener.onHit(e);require(e.cancelled==s.equals("foreign-hit"),"body collision intercepted a private projectile");break;}
  case "owned-splash":case "foreign-splash":case "stale-splash":{
   var p=potion(s.equals("foreign-splash")?other:owner);if(!s.equals("foreign-splash"))launch(p);
   if(s.equals("stale-splash"))change(10,10,true,101);
   var e=new PotionSplashEvent(p,owner,actor,other);listener.onSplash(e);
   boolean own=s.equals("owned-splash");
   require(e.affected.get(owner)==(own?1.0:0.0)&&e.affected.get(actor)==(own?1.0:0.0),"foreign/stale potion damage or healing crossed pair");
   require(e.affected.get(other)==(s.equals("foreign-splash")?1.0:0.0),"paired potion leaked or ordinary splash changed");break;}
  case "owned-cloud":case "foreign-cloud":case "stale-cloud":case "changed-cloud-source":{
   var p=potion(owner);launch(p);var c=new AreaEffectCloud(id(serial++),world,s.equals("foreign-cloud")?other:owner);
   if(!s.equals("foreign-cloud"))listener.onLingering(new LingeringPotionSplashEvent(p,c));
   if(s.equals("stale-cloud"))change(10,10,true,101);
   if(s.equals("changed-cloud-source"))c.source=other;
   var e=new AreaEffectCloudApplyEvent(c,owner,actor,other);listener.onCloud(e);
   require(e.affected.contains(owner)==s.equals("owned-cloud")&&e.affected.contains(actor)==s.equals("owned-cloud"),"cloud ignored source/session isolation");
   require(e.affected.contains(other)==s.equals("foreign-cloud"),"cloud leaked to outsider or unrelated cloud changed");break;}
  case "cloud-cap":{for(int i=0;i<16;i++){var p=potion(owner);launch(p);listener.onLingering(new LingeringPotionSplashEvent(p,new AreaEffectCloud(id(serial++),world,owner)));}
   var p=potion(owner);launch(p);var c=new AreaEffectCloud(id(serial++),world,owner);var e=new LingeringPotionSplashEvent(p,c);listener.onLingering(e);
   require(e.cancelled&&!c.valid&&listener.trackedClouds()==16,"cloud budget is not bounded");break;}
  case "clear":case "clear-retry":case "expire-cleanup":case "replacement-cleanup":{
   var p=arrow(actor);launch(p);var pot=potion(owner);launch(pot);var c=new AreaEffectCloud(id(serial++),world,owner);listener.onLingering(new LingeringPotionSplashEvent(pot,c));
   if(s.equals("clear-retry")){p.failRemove=true;try{listener.clear();throw new AssertionError("native remove failure hidden");}catch(IllegalStateException expected){}
    require(listener.trackedProjectiles()>0,"native removal failure forgot its retry handle");}
   if(s.equals("expire-cleanup"))change(9,10,true,1300);if(s.equals("replacement-cleanup"))change(10,10,true,101);
   if(s.endsWith("cleanup"))listener.tick();else listener.clear();
   require(!p.valid&&!pot.valid&&!c.valid&&listener.trackedProjectiles()==0&&listener.trackedClouds()==0,"tracked private resources survived cleanup");
   int removed=p.removals+c.removals;listener.clear();require(p.removals+c.removals==removed,"cleanup applied twice");break;}
  case "own-fire":case "foreign-fire":{var e=new EntityCombustByEntityEvent(s.equals("own-fire")?owner:other,actor);listener.onCombust(e);require(e.cancelled==s.equals("foreign-fire"),"foreign ignition bypassed pair");break;}
  case "own-target":case "foreign-target":case "pet-target":{var e=new EntityTargetLivingEntityEvent(s.equals("pet-target")?pet:actor,s.equals("foreign-target")?other:s.equals("pet-target")?actor:owner);listener.onTarget(e);require(e.cancelled!=s.equals("own-target"),"target selection uses a different pair rule");break;}
  case "handler-priority":{for(var m:EchoCombatAdmissionListener.class.getDeclaredMethods())if(m.getName().startsWith("on")){
   var a=m.getAnnotation(org.bukkit.event.EventHandler.class);require(a!=null&&a.priority()==org.bukkit.event.EventPriority.HIGHEST&&a.ignoreCancelled(),"admission mutates a MONITOR event or reopens cancellation");}break;}
  default:throw new AssertionError("unknown "+s);
 }
 }
}
''', encoding="utf-8")
    compiled = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(directory), *production, *map(str, directory.rglob("*.java"))], capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    return directory


@pytest.mark.parametrize("scenario", [
    "owner-melee", "echo-melee", "foreign-melee", "foreign-owner-hit", "cross-sweep", "echo-foreign",
    "owner-thorns", "foreign-thorns", "owner-pet", "forged-causing", "unrelated", "environment", "no-scene",
    "owner-arrow", "echo-arrow", "untracked-arrow", "foreign-arrow", "changed-shooter", "stale-generation",
    "stale-epoch", "paused", "expired", "backward-time", "duplicate-launch", "arrow-cross-hit", "wrong-world",
    "launch-cap", "cancelled-launch", "foreign-hit", "own-hit", "owned-splash", "foreign-splash", "stale-splash",
    "owned-cloud", "foreign-cloud", "stale-cloud", "changed-cloud-source", "cloud-cap", "clear", "clear-retry",
    "expire-cleanup", "replacement-cleanup", "own-fire", "foreign-fire", "own-target", "foreign-target",
    "pet-target", "handler-priority",
    "legacy-foreign-source", "legacy-owner-source", "disagrees-with-damager", "self-potion-damage",
    "cancelled-damage", "invalid-handle-cleanup",
    "source-world-cleanup", "no-persistent-source",
])
def test_exact_pair_and_source_lifetime(admission_probe, scenario):
    result = subprocess.run(["java", "-cp", str(admission_probe), "EchoAdmissionChecks", scenario], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr


def test_listener_is_connected_to_the_real_probe_lifecycle():
    source = (BASE / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    assert "new EchoCombatAdmissionListener(this::currentEchoCombatContext)" in source
    assert "registerEvents(echoCombatAdmissionListener, this)" in source
    assert "echoCombatAdmissionListener.tick()" in source
    assert "echoCombatAdmissionListener.clear()" in source
    assert "echoPresentationProbe.duel()" in source
    assert "echoPresentationProbe.activeForCombat(" in source
