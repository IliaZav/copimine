"""Execute the production heartbeat/planner and projectile admission adapters."""
from pathlib import Path
import subprocess
import pytest
from tests.test_wave_navigation_adapter import extract

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


@pytest.fixture(scope="module")
def probe(tmp_path_factory):
    out = tmp_path_factory.mktemp("wave-fog-lifecycle")
    source = SOURCE.read_text("utf-8")
    methods = "\n".join(extract(source, sig) for sig in [
        "void tickWaveMobAi()", "Map<UUID, UUID> planWaveMobSquads(List<Player> candidates)",
        "boolean isWaveAiCombatPhase(int wave)", "boolean isEventArrowPhaseAllowed(Arrow arrow)",
        "boolean isEliteAbilityProjectile(Projectile projectile)",
        "void freezeWaveMobs(boolean frozen)", "void freezeFogCombatMob(Entity entity)",
        "void clearWaveProjectileGroup(UUID groupId, String reason)",
        "void spawnPendingSkeletonSideArrows(UUID arrowId, PendingSkeletonSideArrowShot pending)",
        "void clearDragonBreathCloudsForFog()",
        "void scheduleEliteDragonBreathCloudDamage(AreaEffectCloud cloud, LivingEntity shooter,",
        "void ensureEventCombatAi(Entity entity)",
        "boolean isOfficialNumberedWaveMob(Entity entity)",
        "boolean isActiveOfficialWavePhase(int wave)",
        "void quiesceInactiveOfficialWaveMob(Mob mob)",
        "void resolveEliteProjectileImpact(UUID projectileId, UUID shooterId, UUID targetId,",
        "boolean isWaveAiCombatEntity(Entity entity)",
    ])
    if "private void clearWaveProjectilesForFog()" in source:
        methods += extract(source, "void clearWaveProjectilesForFog()")
    if "private boolean isWaveProjectileShooterAllowed(Entity shooter)" in source:
        methods += extract(source, "boolean isWaveProjectileShooterAllowed(Entity shooter)")
    java = out / "WaveFogLifecycleProbe.java"
    java.write_text(r'''
import java.util.*;import java.util.logging.*;
import me.copimine.endevent.runtime.WaveCombatCoordinator;
import me.copimine.endevent.domain.EliteAbilityPolicy;
import me.copimine.endevent.domain.SpiderEffectLeasePolicy;
import me.copimine.endevent.domain.DragonBreathCloudPolicy;
import me.copimine.endevent.domain.EliteProjectileImpactPolicy;
import me.copimine.endevent.domain.WaveProjectileAdmissionController;
import me.copimine.endevent.domain.WaveProjectileTargetPolicy;
public class WaveFogLifecycleProbe {
 enum EventPhase { WAVE_1,WAVE_2,WAVE_3,WAVE_4,WAVE_5,WAVE_6,WAVE_7,INTERMISSION_4,CORE_RESTORATION,RECOVERY_REQUIRED,BOSS_ACTIVE,READY_FOR_PLAYERS }
 enum BlackFogPhase { COMBAT,FOG }
 EventPhase phase=EventPhase.WAVE_5;BlackFogPhase blackFogPhase=BlackFogPhase.COMBAT;
 long generation=3,eventTickCounter=40;int activeWave=5;boolean localTextureShowcase,testCombatAiMode;
 record Config(String environment){}Config config=new Config("local");Set<UUID> spellServants=new HashSet<>();
 Object keyKind=new Object(),keyWave=new Object(),keyEventId=new Object(),keyArrowSpell=new Object(),keyEliteProjectileAbility=new Object();String eventId="event",recoveryReason;
 static final String EVENT_KIND_BOSS="boss",EVENT_KIND_ELITE="elite",EVENT_KIND_PROJECTILE="projectile",ARROW_SPELL_RITUAL_PROJECTILE="ritual",ARROW_SPELL_RITUAL_GUARD_WEB="guard";
 WaveCombatCoordinator waveCombatCoordinator=new WaveCombatCoordinator();
 WaveProjectileAdmissionController waveProjectileAdmission=new WaveProjectileAdmissionController();
 Map<UUID,Entity> ownedEntities=new LinkedHashMap<>();Map<UUID,Object> waveLeashReturns=new HashMap<>();
 Map<UUID,Long> waveDashGenerations=new HashMap<>(),nextWavePathRequestMillis=new HashMap<>(),lastWavePathLogMillis=new HashMap<>();
 Map<UUID,Object> waveLastPathDestinations=new HashMap<>();Set<UUID> waveTwoOutstandingEntityUuids=new HashSet<>();UUID waveTwoMarkedPlayerUuid;
 Map<UUID,UUID> recentWaveTargets=new HashMap<>();Map<UUID,Location> waveLastProgressLocations=new HashMap<>();Map<UUID,Long> waveLastProgressAt=new HashMap<>(),nextWaveStuckRepositionMillis=new HashMap<>();
 Map<Long,Set<UUID>> wave3GateAssignmentMembers=new HashMap<>();Map<UUID,Integer> wave3GateMobTargets=new HashMap<>();Set<UUID> wave3GateIntentPathRefresh=new HashSet<>();
 Map<UUID,Long> activeEliteProjectileExpiresAtTick=new HashMap<>();int plannerReconciles;
 Set<UUID> eliteProjectileDamageAllowed=new HashSet<>(),eliteProjectileDamageBlocked=new HashSet<>(),eliteProjectileImpactScheduled=new HashSet<>();
 Map<UUID,EntityDamageByEntityEvent> eliteProjectileDamageEvents=new HashMap<>();int impactEffects;
 static class EntityDamageByEntityEvent extends EntityDamageEvent {}
 static class ProjectileHitEvent {boolean isCancelled(){return false;}}
 void removeEliteProjectileVisual(UUID id,String why){}
 void applyEliteVoidBurst(LivingEntity s,Location p,EliteProjectileImpactPolicy.Impact i){impactEffects++;}
 void applyEliteSpiderWeb(Player p,LivingEntity s){impactEffects++;}
 boolean spawnEliteDragonBreathCloud(LivingEntity s,Location p,int w,UUID projectileId){impactEffects++;return true;}
 boolean waveFogFreezeAudible;long nextWaveTargetMillis;int sideArrows;
 record WaveFogAiLease(long generation,boolean ai,boolean aware){}
 Map<UUID,WaveFogAiLease> waveFogAiLeases=new HashMap<>();Set<UUID> waveFogResumeHolds=new HashSet<>();
 Map<UUID,Long> waveFrozenCueRefreshTicks=new HashMap<>();Map<UUID,String> waveCombatCueKeys=new HashMap<>();
 Map<UUID,Integer> activeEventArrowAges=new HashMap<>();Map<UUID,PendingSkeletonSideArrowShot> pendingSkeletonShotSideArrows=new HashMap<>();
 record PendingSkeletonSideArrowShot(UUID shooterId,UUID worldId,Location origin,Vector direction,float speed,int wave,Object arrowKind,String arrowSpell,double damage,UUID volleyId,String eventId,long generation){}
 static class Vector {public Vector clone(){return new Vector();}Vector rotateAroundY(double d){return this;}}
 static final World WORLD=new World();
 static class Location {double distanceSquared(Location l){return 0;}double getX(){return 0;}double getY(){return 0;}double getZ(){return 0;}public Location clone(){return new Location();}World getWorld(){return WORLD;}void setWorld(World w){}}
 static class World {UUID getUID(){return new UUID(0,1);}Arrow spawnArrow(Location l,Vector v,float s,float spread){return new Arrow();}}
 static class Entity {UUID id=UUID.randomUUID();long gen=3;int wave=5;String kind="elite",session="event";boolean removed,official=true;UUID getUniqueId(){return id;}World getWorld(){return WORLD;}Location getLocation(){return new Location();}boolean isValid(){return !removed;}boolean isDead(){return removed;}void remove(){removed=true;}}
 static class LivingEntity extends Entity {}
 static class Player extends LivingEntity {int damageCalls;boolean isOnline(){return true;}void damage(double d,LivingEntity e){damageCalls++;}Location getEyeLocation(){return new Location();}}
 static class Path {int stopCount;void stopPathfinding(){stopCount++;}}
 static class Mob extends LivingEntity {boolean ai=true,aware=true;LivingEntity target;Path path=new Path();LivingEntity getTarget(){return target;}void setTarget(LivingEntity p){target=p;}Path getPathfinder(){return path;}boolean hasAI(){return ai;}boolean isAware(){return aware;}void setAI(boolean b){ai=b;}void setAware(boolean b){aware=b;}void setVelocity(Vector v){}Location getEyeLocation(){return new Location();}}
 static class Skeleton extends Mob {}static class Spider extends Mob {}static class Enderman extends Mob {}
 static class Projectile extends Entity {Object shooter;Object getShooter(){return shooter;}}
 static class Arrow extends Projectile {void setShooter(Entity e){shooter=e;}}
 static class AreaEffectCloud extends Entity {double getRadius(){return 3;}}
 static class BukkitTask {}static class Scheduler {List<Runnable> tasks=new ArrayList<>();BukkitTask runTaskLater(Object owner,Runnable r,long delay){tasks.add(r);return new BukkitTask();}void drain(){for(var r:new ArrayList<>(tasks))r.run();tasks.clear();}}
 static class Bukkit {static Scheduler scheduler=new Scheduler();static Scheduler getScheduler(){return scheduler;}static Map<UUID,Player> players=new HashMap<>();static Map<UUID,Entity> entities=new HashMap<>();static Player getPlayer(UUID id){return players.get(id);}static World getWorld(UUID id){return WORLD;}static Entity getEntity(UUID id){return entities.get(id);}}
 Set<DragonBreathCloudPolicy.Hit> eliteDragonBreathCloudHits=new HashSet<>();
 static class EntityDamageEvent {boolean isCancelled(){return false;}}
 static class PendingPlayerDamageOutcome {EntityDamageEvent outcome;PendingPlayerDamageOutcome(String e,long g,UUID s,UUID p){}}
 PendingPlayerDamageOutcome pendingPlayerDamageOutcome;
 enum PotionEffectType {BLINDNESS}
 SpiderEffectLeasePolicy.Owner currentSpiderEffectOwner(){return new SpiderEffectLeasePolicy.Owner(eventId,generation);}
 void registerEncounterTask(BukkitTask t){}void applySpiderPotionLease(Player p,PotionEffectType type,int ticks,int amp,SpiderEffectLeasePolicy.Owner owner){}
 static class WaveTwoEntityRecoveryPolicy {static List<UUID> coordinatorRoster(List<UUID> a,Set<UUID>b){return a;}}
 String readString(Entity e,Object key){return key==keyKind?e.kind:key==keyEventId?e.session:key==keyEliteProjectileAbility?"DRAGON_BREATH":"";}int readInt(Entity e,Object key,int fallback){return e.wave;}
 void clearWaveCombatCue(UUID id){}void sendWaveMobPose(Entity e,String s,long d){}void finishWaveMobDash(Mob m,long g){waveDashGenerations.remove(m.id);}
 void renderWaveCombatCue(Mob m,Location l,String a,String b){}Location coreCombatAnchorLocation(){return new Location();}void playWaveFeedback(String s,Location l){}
 boolean isSkeletonMiniBoss(Skeleton s){return true;}boolean configureEventSkeletonArrow(Arrow a,Skeleton s,int w,Object k,String spell,double damage){sideArrows++;return true;}void tagEventArrowVolley(Arrow a,UUID id){}
 boolean isEventArrow(Arrow a){return true;}
 boolean isTaggedEliteAbilityEntity(Entity e){return true;}void clearEliteProjectileRuntime(UUID id,String why,boolean keep){waveProjectileAdmission.releaseProjectile(id);activeEliteProjectileExpiresAtTick.remove(id);eliteProjectileImpactScheduled.remove(id);eliteProjectileDamageAllowed.remove(id);}
 void cleanupEventArrow(UUID id){waveProjectileAdmission.releaseProjectile(id);activeEventArrowAges.remove(id);Entity e=Bukkit.getEntity(id);if(e!=null)e.remove();}
 boolean isLiveOwnedEntity(UUID id){return ownedEntities.containsKey(id)&&!ownedEntities.get(id).removed;}
 boolean ownedBySession(Entity e,String id,long gen){return e.gen==gen&&e.session.equals(id);}
 boolean ownedByEvent(Entity e,String id){return e!=null&&e.session.equals(id);}long readEntityGeneration(Entity e){return e.gen;}
 boolean isWaveCombatKind(String kind){return true;}boolean isOfficialEntity(Entity e){return e.official;}
 boolean testBossMovementFrozen;Object bossPhase;static final String EVENT_KIND_REALITY_SPLIT_TRIAL="trial",EVENT_KIND_RITUAL_CASTER="caster";
 Map<UUID,Set<UUID>> ritualGuardsByCaster=new HashMap<>();
 static class BossFinalSealAnchorPolicy {static boolean shouldPin(Object p){return false;}}
 static class RitualCasterTacticsPolicy {static class State {}static State state(boolean a,boolean b){return new State();}static boolean nativeAiEnabled(State s){return true;}}
 boolean readRitualCasterAwakened(Entity e){return true;}boolean isOfficialBoss(Entity e){return false;}
 boolean isWaveTwoRecoveryFenced(Entity e){return false;}void suspendWaveTwoRecoveryEntity(Entity e){}
 boolean isFogFrozenCombatEntity(Entity e){return e.wave==5&&blackFogPhase==BlackFogPhase.FOG;}
 boolean isWaveFogAiHeld(Entity e){return waveFogResumeHolds.contains(e.id);}
 void enforceWaveMobContainment(){}boolean isCombatPhase(){return true;}
 void renderWaveMobRoleVisuals(long now){}List<Player> activeWaveParticipants(){return new ArrayList<>(Bukkit.players.values());}
 boolean usesWaveCombatCoordination(Entity e){return true;}boolean isEliteSpiderWebSupport(Mob m,String k){return false;}
 boolean isWaveTargetAllowed(Entity e,Player p){return true;}UUID mostRecentWaveTarget(UUID id){return null;}
 boolean hasProjectileBlockLineOfSight(Location from,Location to){return true;}
 void rememberWaveTarget(UUID a,UUID b){}Logger getLogger(){return Logger.getAnonymousLogger();}
 void maintainSkeletonCombatPosture(Skeleton s,Player p,String k,long n){}void maintainWaveMobPath(Mob m,Player p,String k,long n){}
 void watchWaveMobProgress(Mob m,Player p,String k,long n){}int realitySplitChamberId(Entity e){return 0;}
 boolean isCurrentRitualGuard(Entity e){return false;}boolean isCurrentRitualCaster(Entity e){return false;}boolean isCurrentCollapseGuard(Entity e){return false;}
 void forcePhase(EventPhase p,String why){phase=p;}
 void reconcileWave3GateSquadIntents(List<WaveCombatCoordinator.Squad> squads,long now){plannerReconciles++;}
 boolean isRitualProjectile(Arrow a){return false;}boolean ritualSphereProjectileProvenanceAllowed(Arrow a,Object p){return true;}
 boolean ritualGuardWebProvenanceAllowed(Arrow a,Object p){return true;}boolean hasLiveTestWaveEntities(){return false;}
 boolean isTestBoss(Entity e){return false;}boolean isEventSkeleton(Entity e){return e instanceof Skeleton;}
 boolean isTaggedEliteAbilityProjectile(Projectile p){return true;}
 EliteAbilityPolicy.Ability eliteAbility(Projectile p){return EliteAbilityPolicy.Ability.DRAGON_BREATH;}
 static void check(boolean v,String why){if(!v)throw new AssertionError(why);}
''' + methods + r'''
 public static void main(String[] args){
  var h=new WaveFogLifecycleProbe();h.waveCombatCoordinator.begin(h.generation);
  switch(args[0]) {
   case "fog", "intermission", "intermission-wave1", "intermission-wave3", "core", "empty-resume", "generation-stale" -> {
    var original=new Enderman();h.ownedEntities.put(original.id,original);var participant=new Player();Bukkit.players.put(participant.id,participant);
    if(args[0].equals("intermission-wave1")){original.wave=1;h.activeWave=1;h.phase=EventPhase.WAVE_1;}
    if(args[0].equals("intermission-wave3")){original.wave=3;h.activeWave=3;h.phase=EventPhase.WAVE_3;}
    // Objective/freeze clears happen after this heartbeat's AI, before the next AI tick.
    h.tickWaveMobAi();
    if(args[0].equals("fog")){h.blackFogPhase=BlackFogPhase.FOG;h.freezeWaveMobs(true);}
    else h.waveCombatCoordinator.clear();
    if(args[0].startsWith("intermission"))h.phase=EventPhase.INTERMISSION_4;
    if(args[0].equals("core"))h.phase=EventPhase.CORE_RESTORATION;
    if(args[0].startsWith("intermission")){original.target=participant;}
    if(args[0].equals("generation-stale")){h.generation++;original.target=participant;}
    if(args[0].equals("empty-resume"))h.ownedEntities.clear();
    h.tickWaveMobAi();
    if(!args[0].equals("empty-resume"))check(!original.ai&&!original.aware,"noncombat heartbeat restored mob AI");
    if(args[0].startsWith("intermission")||args[0].equals("generation-stale")){
     check(original.target==null,"inactive official wave mob retained its target");
     check(original.path.stopCount>0,"inactive official wave mob retained its navigation path");
    }
    check(!h.waveCombatCoordinator.owns(h.generation),"empty/frozen heartbeat restored planner ownership");
    h.blackFogPhase=BlackFogPhase.COMBAT;h.phase=EventPhase.WAVE_5;h.freezeWaveMobs(false);
    var m=new Enderman();m.gen=h.generation;h.ownedEntities.put(m.id,m);var p=new Player();Bukkit.players.put(p.id,p);
    h.tickWaveMobAi();check(h.waveCombatCoordinator.owns(h.generation),"valid resume failed to begin authoritative generation before planning");
    check(m.target!=null,"valid resume did not reacquire target");
   }
   case "arrow-fog", "arrow-intermission", "arrow-stale", "arrow-resume" -> {
    var m=new Skeleton();h.ownedEntities.put(m.id,m);var a=new Arrow();a.shooter=m;
    if(args[0].equals("arrow-fog"))h.blackFogPhase=BlackFogPhase.FOG;
    if(args[0].equals("arrow-intermission"))h.phase=EventPhase.INTERMISSION_4;
    if(args[0].equals("arrow-stale"))h.generation++;
    check(h.isEventArrowPhaseAllowed(a)==args[0].equals("arrow-resume"),"already launched arrow phase/generation admission leaked");
   }
   case "elite-fog", "elite-stale", "elite-resume" -> {
    var m=new Enderman();h.ownedEntities.put(m.id,m);var p=new Projectile();p.shooter=m;h.activeEliteProjectileExpiresAtTick.put(p.id,100L);
    if(args[0].equals("elite-fog"))h.blackFogPhase=BlackFogPhase.FOG;
    if(args[0].equals("elite-stale"))h.generation++;
    check(h.isEliteAbilityProjectile(p)==args[0].equals("elite-resume"),"elite projectile phase/generation admission leaked");
   }
   case "side-fog", "side-stale", "side-resume" -> {
    var m=new Skeleton();h.ownedEntities.put(m.id,m);
    var pending=new PendingSkeletonSideArrowShot(m.id,m.getWorld().getUID(),m.getLocation(),new Vector(),1,5,null,"spell",2,UUID.randomUUID(),h.eventId,h.generation);
    if(args[0].equals("side-fog"))h.blackFogPhase=BlackFogPhase.FOG;
    if(args[0].equals("side-stale"))h.generation++;
    h.spawnPendingSkeletonSideArrows(UUID.randomUUID(),pending);
    check(h.sideArrows==(args[0].equals("side-resume")?2:0),"delayed side-arrow callback escaped phase/generation fence");
   }
   case "fog-cleanup" -> {
    var a=new Arrow();var side=new Arrow();var p=new Projectile();var cloud=new AreaEffectCloud();
    for(var e:List.of(a,side,p,cloud))Bukkit.entities.put(e.id,e);
    UUID volley=UUID.randomUUID();h.waveProjectileAdmission.reserveGroup(volley,2,3);
    h.waveProjectileAdmission.trackProjectile(volley,a.id);h.waveProjectileAdmission.trackProjectile(volley,side.id);
    var pending=new PendingSkeletonSideArrowShot(UUID.randomUUID(),WORLD.getUID(),new Location(),new Vector(),1,5,null,"spell",2,volley,h.eventId,h.generation);
    h.pendingSkeletonShotSideArrows.put(a.id,pending);
    h.activeEventArrowAges.put(a.id,0);h.activeEventArrowAges.put(side.id,0);
    h.activeEliteProjectileExpiresAtTick.put(p.id,100L);h.activeEliteProjectileExpiresAtTick.put(cloud.id,100L);
    h.clearDragonBreathCloudsForFog();
    check(a.removed&&side.removed&&p.removed&&cloud.removed,"Fog retained already launched arrows/elite projectiles/clouds");
    check(h.activeEventArrowAges.isEmpty()&&h.activeEliteProjectileExpiresAtTick.isEmpty(),"Fog retained projectile ownership");
    check(!h.waveProjectileAdmission.hasGroup(volley)&&h.waveProjectileAdmission.activeProjectileCount()==0,
      "Fog retained a volley admission group or one of its physical child leases");
   }
   case "cloud-fog", "cloud-stale", "cloud-resume", "cloud-cleaned-resume" -> {
    var m=new Enderman();h.ownedEntities.put(m.id,m);Bukkit.entities.put(m.id,m);
    var c=new AreaEffectCloud();Bukkit.entities.put(c.id,c);h.activeEliteProjectileExpiresAtTick.put(c.id,100L);
    var p=new Player();Bukkit.players.put(p.id,p);h.scheduleEliteDragonBreathCloudDamage(c,m,p,5);
    if(args[0].equals("cloud-fog"))h.blackFogPhase=BlackFogPhase.FOG;
    if(args[0].equals("cloud-stale"))h.generation++;
    if(args[0].equals("cloud-cleaned-resume")){h.clearDragonBreathCloudsForFog();h.blackFogPhase=BlackFogPhase.COMBAT;}
    Bukkit.scheduler.drain();
    check(p.damageCalls==(args[0].equals("cloud-resume")?1:0),"queued cloud damage escaped cleanup/phase/generation fence");
   }
   case "impact-fog", "impact-stale", "impact-resume", "impact-cleaned-resume" -> {
    var m=new Enderman();h.ownedEntities.put(m.id,m);Bukkit.entities.put(m.id,m);var shot=new Projectile();Bukkit.entities.put(shot.id,shot);
    h.activeEliteProjectileExpiresAtTick.put(shot.id,100L);h.eliteProjectileImpactScheduled.add(shot.id);
    long expected=h.generation;
    if(args[0].equals("impact-fog"))h.blackFogPhase=BlackFogPhase.FOG;
    if(args[0].equals("impact-stale"))h.generation++;
    if(args[0].equals("impact-cleaned-resume")){h.clearDragonBreathCloudsForFog();h.blackFogPhase=BlackFogPhase.COMBAT;}
    h.resolveEliteProjectileImpact(shot.id,m.id,null,true,new Location(),EliteAbilityPolicy.Ability.DRAGON_BREATH,5,h.eventId,expected,null);
    check(h.impactEffects==(args[0].equals("impact-resume")?1:0),"queued elite impact escaped cleanup/phase/generation fence");
   }
   case "local-arrow-ready", "local-side-ready", "local-arrow-fog", "local-side-fog", "local-arrow-stale", "local-side-stale", "local-arrow-session", "local-side-session" -> {
    h.phase=EventPhase.READY_FOR_PLAYERS;h.testCombatAiMode=false;
    var m=new Skeleton();m.official=false;h.ownedEntities.put(m.id,m);
    var a=new Arrow();a.shooter=m;a.official=false;
    var pending=new PendingSkeletonSideArrowShot(m.id,m.getWorld().getUID(),m.getLocation(),new Vector(),1,5,null,"spell",2,UUID.randomUUID(),h.eventId,h.generation);
    if(args[0].endsWith("-fog"))h.blackFogPhase=BlackFogPhase.FOG;
    if(args[0].endsWith("-stale"))h.generation++;
    if(args[0].endsWith("-session"))m.session="old-event";
    boolean allowed=args[0].endsWith("-ready");
    if(args[0].startsWith("local-arrow"))check(h.isEventArrowPhaseAllowed(a)==allowed,"disposable local arrow READY/Fog/session admission mismatch");
    else {h.spawnPendingSkeletonSideArrows(a.id,pending);check(h.sideArrows==(allowed?2:0),"disposable local side-arrow READY/Fog/session admission mismatch");}
   }
  }
  System.out.println(args[0]+" OK");
 }
}
''', encoding="utf-8")
    sources = [java, ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/WaveCombatCoordinator.java",
               *[ROOT / ("copimine-end-event/src/me/copimine/endevent/domain/" + name + ".java")
                 for name in ["EliteAbilityPolicy", "DragonBreathCloudPolicy", "SpiderEffectLeasePolicy", "BlackFogEffectLeasePolicy", "EliteProjectileImpactPolicy", "WaveProjectileAdmissionController", "WaveProjectileTargetPolicy", "PressureBudgetController"]]]
    built = subprocess.run(["javac", "-proc:none", "-encoding", "UTF-8", "-d", str(out), *map(str, sources)], capture_output=True, text=True)
    assert built.returncode == 0, built.stdout + built.stderr
    return out


@pytest.mark.parametrize("scenario", ["fog", "intermission", "intermission-wave1", "intermission-wave3", "core", "empty-resume", "generation-stale", "arrow-fog", "arrow-intermission", "arrow-stale", "arrow-resume", "elite-fog", "elite-stale", "elite-resume", "side-fog", "side-stale", "side-resume", "fog-cleanup", "cloud-fog", "cloud-stale", "cloud-resume", "cloud-cleaned-resume", "impact-fog", "impact-stale", "impact-resume", "impact-cleaned-resume", "local-arrow-ready", "local-side-ready", "local-arrow-fog", "local-side-fog", "local-arrow-stale", "local-side-stale", "local-arrow-session", "local-side-session"])
def test_heartbeat_and_projectile_admission(probe, scenario):
    run = subprocess.run(["java", "-cp", str(probe), "WaveFogLifecycleProbe", scenario], capture_output=True, text=True)
    assert run.returncode == 0, run.stdout + run.stderr
