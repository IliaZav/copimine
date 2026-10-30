"""Run the actual converted-skeleton bow, collision, damage and cleanup adapters."""
from pathlib import Path
import re
import subprocess

import pytest
from tests.test_wave7_named_trial_warning_audio_runtime import extract_method

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


@pytest.fixture(scope="module")
def converted_arrow_probe(tmp_path_factory):
    out = tmp_path_factory.mktemp("converted-arrow")
    source = SOURCE.read_text(encoding="utf-8")
    signatures = [
        "public void onEventSkeletonShootBow(", "public void onEventSkeletonShootBowOutcome(",
        "private void spawnPendingSkeletonSideArrows(", "private void prepareEventSkeletonArrow(",
        "private boolean isWaveProjectileShooterAllowed(", "private boolean isEventArrowPhaseAllowed(",
        "public void onEventProjectileCollide(", "public void onEventSkeletonArrowDamage(",
        "public void onCustomEventArrowDamage(", "public void onStaleEventArrowDamage(",
        "private boolean isRitualConvertedMob(", "private boolean isRitualConvertedTargetAllowed(",
        "private boolean isWaveAiCombatEntity(", "private boolean isWaveAiCombatPhase(",
        "private boolean isEventArrow(", "private boolean trackEventArrow(",
        "private void tickEventArrowProjectiles(", "private void cleanupEventArrow(",
    ]
    for signature in ("private boolean isRitualConvertedArrow(",
                      "private boolean ritualConvertedArrowTargetAllowed("):
        if signature in source:
            signatures.append(signature)
    methods = "\n".join(extract_method(source, signature) for signature in signatures)
    constants = "\n".join(re.findall(
        r'private static final String (?:ARROW_SPELL_[A-Z_]+|EVENT_KIND_PROJECTILE|EVENT_KIND_BOSS|EVENT_KIND_RITUAL_GUARD|EVENT_KIND_RITUAL_CASTER|EVENT_KIND_REALITY_SPLIT_TRIAL) = "[^"]+";', source))
    code = r'''
import java.util.*;
import java.util.logging.*;
import me.copimine.endevent.domain.*;
public class ConvertedArrowProbe {
''' + constants + r'''
    enum EventPhase { WAVE_1, WAVE_2, WAVE_3, WAVE_4, WAVE_5, WAVE_6, WAVE_7, BOSS_ACTIVE }
    enum BlackFogPhase { CLEAR, FOG }
    enum Particle { CRIT }
    static class Color {static Color fromRGB(int a,int b,int c){return new Color();}}
    static class Vector {
        double x=1,y,z; double lengthSquared(){return x*x+y*y+z*z;}double length(){return Math.sqrt(lengthSquared());}
        public Vector clone(){return new Vector();}Vector normalize(){return this;}Vector rotateAroundY(double v){return this;}
    }
    static class World {
        final UUID id=UUID.randomUUID();UUID getUID(){return id;}
        Arrow spawnArrow(Location p,Vector v,float speed,float spread){throw new AssertionError("converted arrow gained a side volley");}
    }
    static class Location {
        final World world;Location(World w){world=w;}World getWorld(){return world;}
        public Location clone(){return new Location(world);}
    }
    static class Type<T>{}
    static class PersistentDataType {
        static final Type<String> STRING=new Type<>();static final Type<Byte> BYTE=new Type<>();
    }
    static class Data {
        final Map<Object,Object> values=new HashMap<>();
        <T>void set(Object k,Type<T>t,T v){values.put(k,v);}
        @SuppressWarnings("unchecked") <T>T getOrDefault(Object k,Type<T>t,T def){return (T)values.getOrDefault(k,def);}
    }
    static class Entity {
        final UUID id=UUID.randomUUID();World world;boolean valid=true,dead,official=true;
        long generation=7;String event="event",kind="wave_mob";int wave=6;final Data data=new Data();
        Entity(World w){world=w;}UUID getUniqueId(){return id;}World getWorld(){return world;}
        boolean isValid(){return valid;}boolean isDead(){return dead;}String getType(){return getClass().getSimpleName();}
        Location getLocation(){return new Location(world);}void remove(){valid=false;Bukkit.entities.remove(id);}
        Data getPersistentDataContainer(){return data;}
    }
    static class LivingEntity extends Entity {
        double hp=20;LivingEntity(World w){super(w);}double getHealth(){return hp;}
    }
    static class Mob extends LivingEntity {LivingEntity target;boolean visible=true;Mob(World w){super(w);}LivingEntity getTarget(){return target;}boolean hasLineOfSight(Entity e){return visible;}}
    static class Skeleton extends Mob {Skeleton(World w){super(w);}}
    static class Player extends LivingEntity {
        Player(World w){super(w);}void addPotionEffect(PotionEffect effect){throw new AssertionError("converted arrow added status");}
    }
    static class Projectile extends Entity {
        Object shooter;Projectile(World w){super(w);}Object getShooter(){return shooter;}void setShooter(Object s){shooter=s;}
    }
    static class AbstractArrow extends Projectile {
        enum PickupStatus{DISALLOWED}
        AbstractArrow(World w){super(w);}
    }
    static class Arrow extends AbstractArrow {
        double damage;UUID group;boolean ritual;
        Arrow(World w){super(w);}void setDamage(double v){damage=v;}void setCritical(boolean v){}
        void setPickupStatus(PickupStatus s){}void setColor(Color c){}Vector getVelocity(){return new Vector();}
    }
    static class LargeFireball extends Projectile {LargeFireball(World w){super(w);}}
    record RiftFireballRuntimeState(long generation){boolean isWave4Obelisk(){return false;}}
    static class PotionEffect {PotionEffect(Object type,int ticks,int amp,boolean ambient,boolean particles,boolean icon){}}
    static class PotionEffectType {static final Object POISON=new Object(),NAUSEA=new Object();}
    static class EntityShootBowEvent {
        final Skeleton source;Entity projectile;boolean cancelled;
        EntityShootBowEvent(Skeleton s,Arrow a){source=s;projectile=a;}
        LivingEntity getEntity(){return source;}Entity getProjectile(){return projectile;}
        boolean isCancelled(){return cancelled;}void setCancelled(boolean b){cancelled=b;}
    }
    static class ProjectileCollideEvent {
        final Projectile projectile;final Entity target;boolean cancelled;
        ProjectileCollideEvent(Projectile p,Entity t){projectile=p;target=t;}
        Projectile getEntity(){return projectile;}Entity getCollidedWith(){return target;}
        boolean isCancelled(){return cancelled;}void setCancelled(boolean b){cancelled=b;}
    }
    static class EntityDamageByEntityEvent {
        final Arrow arrow;final Entity target;boolean cancelled;double amount=6;
        EntityDamageByEntityEvent(Arrow a,Entity t){arrow=a;target=t;}
        Entity getDamager(){return arrow;}Entity getEntity(){return target;}
        boolean isCancelled(){return cancelled;}void setCancelled(boolean b){cancelled=b;}
        void setDamage(double d){amount=d;}double getFinalDamage(){return amount;}
    }
    static class BukkitTask {}
    static class Scheduler {
        final List<Runnable> tasks=new ArrayList<>();
        BukkitTask runTaskLater(Object plugin,Runnable action,long delay){tasks.add(action);return new BukkitTask();}
        void flush(){var copy=new ArrayList<>(tasks);tasks.clear();copy.forEach(Runnable::run);}
    }
    static class Bukkit {
        static final Map<UUID,Entity> entities=new HashMap<>();static final Scheduler scheduler=new Scheduler();
        static Entity getEntity(UUID id){return entities.get(id);}static Scheduler getScheduler(){return scheduler;}
    }
    record PendingSkeletonSideArrowShot(UUID shooterId,int wave,SkeletonArrowPolicy.ArrowKind arrowKind,
        String arrowSpell,double damage,UUID volleyId,String eventId,long generation,int arrowCount){}
    static class Admission {
        final Set<UUID> groups=new HashSet<>(),children=new HashSet<>();
        boolean hasGroup(UUID id){return groups.contains(id);}void releaseUnspawnedReservations(UUID id){groups.remove(id);}
        void releaseProjectile(UUID id){children.remove(id);}
    }
    final World world=new World();final Map<UUID,Entity> ownedEntities=new HashMap<>();
    final Map<UUID,Integer> activeEventArrowAges=new HashMap<>();
    final Map<UUID,Long> nextSkeletonArrowMillis=new HashMap<>(),ritualConvertedMobUntilMillis=new HashMap<>();
    final Map<UUID,PendingSkeletonSideArrowShot> pendingSkeletonShotSideArrows=new HashMap<>();
    final Map<EntityShootBowEvent,UUID> pendingSkeletonShotBowEventArrowIds=new HashMap<>();
    final Map<EntityShootBowEvent,Arrow> pendingSkeletonShotBowEventArrows=new HashMap<>();
    final Map<UUID,Runnable> pendingEventArrowDamageEffects=new HashMap<>();
    final Set<UUID> statusArrowEffectsApplied=new HashSet<>(),detonatedEventArrows=new HashSet<>(),
        scheduledEventArrowDamageEffects=new HashSet<>(),scheduledEventArrowHitCleanup=new HashSet<>(),
        ritualProjectileTerminalEvents=new HashSet<>(),spellServants=new HashSet<>();
    final Map<UUID,RiftFireballRuntimeState> activeRiftFireballs=new HashMap<>();
    final Admission waveProjectileAdmission=new Admission();
    final Object keyKind="kind",keyWave="wave",keyArrowSpell="spell",keyRitualConverted="converted",keyRitualProjectileOwner="owner";
    final Random random=new Random(1);long generation=7;String eventId="event";int activeWave=6;
    static final int MAX_ACTIVE_EVENT_ARROWS=64,EVENT_ARROW_MAX_TICKS=100,EVENT_ARROW_TRAIL_INTERVAL_TICKS=4;
    EventPhase phase=EventPhase.WAVE_6;BlackFogPhase blackFogPhase=BlackFogPhase.CLEAR;
    boolean testCombatAiMode,budget=true,waveActive=true;
    record Config(String environment){}final Config config=new Config("production");
    Logger getLogger(){return Logger.getAnonymousLogger();}
    boolean isLiveOwnedEntity(UUID id){Entity e=ownedEntities.get(id);return e!=null&&e.valid&&!e.dead;}
    boolean ownedBySession(Entity e,String id,long gen){return e!=null&&e.event.equals(id)&&e.generation==gen;}
    boolean isOfficialEntity(Entity e){return e.official;}
    boolean isOfficialNumberedWaveMob(Entity e){return e.official&&e.wave>0&&e.wave<=7;}
    boolean isWaveCombatKind(String s){return s.equals("wave_mob")||s.equals(EVENT_KIND_RITUAL_GUARD);}
    boolean isEventSkeleton(Entity e){return e instanceof Skeleton&&isWaveCombatKind(e.kind);}
    boolean isSkeletonMiniBoss(Entity e){return false;}
    boolean isCurrentRitualGuard(Entity e){return e!=null&&e.kind.equals(EVENT_KIND_RITUAL_GUARD);}
    boolean isCurrentRitualCaster(Entity e){return false;}
    boolean isFogFrozenCombatEntity(Entity e){return false;}boolean isWaveFogAiHeld(Entity e){return false;}
    boolean ritualWaveActive(){return waveActive&&phase==EventPhase.WAVE_6;}
    boolean isRitualConversionCombatTarget(Entity e){return e instanceof Mob&&e.wave==6&&e.valid&&!e.dead&&ownedEntities.containsKey(e.id);}
    double horizontalDistanceSquared(Location a,Location b){return 4;}
    String readString(Entity e,Object key){if(key==keyKind)return e.kind;return (String)e.data.values.getOrDefault(key,"");}
    int readInt(Entity e,Object key,int fallback){return e.wave;}
    void tag(Entity e,String kind,int wave,boolean official){e.kind=kind;e.wave=wave;e.official=official;e.event=eventId;e.generation=generation;ownedEntities.put(e.id,e);}
    void tagArrowSpell(Arrow a,String spell){a.data.set(keyArrowSpell,PersistentDataType.STRING,spell);}
    void tagRitualProjectile(Arrow a){a.ritual=true;}
    void tagEventArrowVolley(Arrow a,UUID id){a.group=id;}UUID eventArrowVolleyId(Arrow a){return a.group;}
    boolean reserveWaveProjectileGroup(UUID id,int n){if(!budget)return false;waveProjectileAdmission.groups.add(id);return true;}
    boolean trackWaveProjectile(Entity e,UUID group){waveProjectileAdmission.children.add(e.id);return true;}
    void clearWaveProjectileGroup(UUID id,String reason){waveProjectileAdmission.groups.remove(id);}
    boolean configureEventSkeletonArrow(Arrow a,Skeleton s,int wave,SkeletonArrowPolicy.ArrowKind kind,String spell,double damage){throw new AssertionError("side arrow");}
    void registerEncounterTask(BukkitTask task){}
    Entity unregisterOwnedEntity(UUID id,String reason){return ownedEntities.remove(id);}
    void clearRitualProjectileMarker(Entity e){}void emitDiagnostic(Object... args){}
    void spawnEventArrowTrail(Arrow a,String spell,int age){}
    void markRitualProjectileTerminal(Arrow a,String action,Player target,String reason){}
    boolean isRitualProjectile(Arrow a){return a.ritual;}
    boolean ritualSphereProjectileProvenanceAllowed(Arrow a,UUID t){return false;}
    boolean ritualGuardWebProvenanceAllowed(Arrow a,UUID t){return false;}
    boolean ritualGuardProjectileProvenanceAllowed(Arrow a,UUID t,RitualGuardAbilityPolicy.Role r){return false;}
    boolean isCombatPhase(){return true;}boolean hasLiveTestWaveEntities(){return false;}boolean isTestBoss(Entity e){return false;}
    boolean isCombatTarget(Player p){return p.valid&&!p.dead;}
    boolean isWaveTargetAllowed(Entity e,Player p){return isCombatTarget(p);}
    boolean realitySplitTargetAllowed(Entity e,Player p){return true;}
    boolean ritualProjectileTargetAllowed(Arrow a,Player p){return true;}
    boolean isRitualProjectileSpell(String s){return false;}
    void deferEventArrowDamageEffect(EntityDamageByEntityEvent e,Arrow a,Runnable r){throw new AssertionError("converted status effect");}
    void detonateExplosiveArrow(Arrow a,Location l){throw new AssertionError("converted explosive");}
    void onCustomEventArrowDamage(EntityDamageByEntityEvent e,Arrow a,String s){throw new AssertionError("converted arrow reached hostile player-only spell");}
    boolean handleWave6CrystalBarrageCollision(ProjectileCollideEvent e){return false;}
    boolean isRiftFireball(LargeFireball e){return false;}boolean isActiveArenaParticipant(Player p){return false;}
    boolean isTaggedEliteAbilityProjectile(Projectile e){return false;}boolean isEliteAbilityProjectile(Projectile e){return false;}
''' + methods + r'''
    Skeleton source;Mob enemy;Arrow arrow;EntityShootBowEvent bow;
    void own(Entity e){ownedEntities.put(e.id,e);Bukkit.entities.put(e.id,e);}
    void setup(){
        source=new Skeleton(world);enemy=new Mob(world);arrow=new Arrow(world);source.target=enemy;arrow.shooter=source;
        own(source);own(enemy);Bukkit.entities.put(arrow.id,arrow);convert(source);
        bow=new EntityShootBowEvent(source,arrow);
    }
    void convert(Skeleton s){s.wave=0;s.data.set(keyRitualConverted,PersistentDataType.BYTE,(byte)1);ritualConvertedMobUntilMillis.put(s.id,Long.MAX_VALUE);}
    void launch(){onEventSkeletonShootBow(bow);onEventSkeletonShootBowOutcome(bow);Bukkit.scheduler.flush();}
    void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    void rejectLaunch(){launch();require(bow.cancelled&&!arrow.valid&&activeEventArrowAges.isEmpty(),"invalid converted launch was admitted");}
    public static void main(String[] args){
        var p=new ConvertedArrowProbe();p.setup();String mode=args[0];
        if(mode.startsWith("launch-")){
            switch(mode){
                case "launch-cover" -> p.source.visible=false;
                case "launch-expired" -> p.ritualConvertedMobUntilMillis.put(p.source.id,0L);
                case "launch-dead" -> p.source.dead=true;
                case "launch-missing" -> p.ownedEntities.remove(p.source.id);
                case "launch-stale" -> p.source.generation--;
                case "launch-wrong-world" -> p.arrow.world=new World();
                case "launch-owner-mismatch" -> p.arrow.shooter=p.enemy;
                case "launch-no-target" -> p.source.target=null;
                case "launch-player" -> p.source.target=new Player(p.world);
                case "launch-friendly" -> {var ally=new Skeleton(p.world);p.own(ally);p.convert(ally);p.source.target=ally;}
                case "launch-foreign-target" -> p.ownedEntities.remove(p.enemy.id);
                case "launch-dead-target" -> p.enemy.dead=true;
                case "launch-target-world" -> p.enemy.world=new World();
                case "launch-budget" -> p.budget=false;
                case "launch-cooldown" -> p.nextSkeletonArrowMillis.put(p.source.id,Long.MAX_VALUE);
                default -> throw new AssertionError(mode);
            }
            p.rejectLaunch();return;
        }
        if(mode.equals("ordinary-before-conversion")||mode.startsWith("ritual-before-conversion")||mode.equals("ordinary-normal")||mode.equals("ritual-normal")){
            p.source.data.values.remove(p.keyRitualConverted);p.ritualConvertedMobUntilMillis.clear();p.source.wave=6;
            p.source.target=new Player(p.world);
            if(mode.equals("ritual-normal"))p.source.kind=EVENT_KIND_RITUAL_GUARD;
        }
        p.launch();
        p.require(!p.bow.cancelled&&p.arrow.valid&&p.activeEventArrowAges.size()==1
            &&p.waveProjectileAdmission.children.contains(p.arrow.id),"valid native bow did not reach bounded tracker");
        if(mode.equals("valid-launch")){
            p.require("ritual_converted".equals(p.readString(p.arrow,p.keyArrowSpell))
                &&p.source.id.toString().equals(p.readString(p.arrow,p.keyRitualProjectileOwner)),"converted origin was not frozen");
            var repeat=new Arrow(p.world);repeat.shooter=p.source;p.bow=new EntityShootBowEvent(p.source,repeat);
            p.onEventSkeletonShootBow(p.bow);p.require(p.bow.cancelled&&!repeat.valid,"normal bow cooldown was bypassed");return;
        }
        Entity target=p.enemy;boolean allowed=mode.equals("valid-hit")||mode.equals("ordinary-normal")||mode.equals("ritual-normal");
        switch(mode){
            case "valid-hit" -> {}
            case "ordinary-normal","ritual-normal" -> target=p.source.target;
            case "ordinary-before-conversion","ritual-before-conversion","ritual-before-conversion-expired",
                    "ritual-before-conversion-forgotten","ritual-before-conversion-removed","ritual-before-conversion-null-owner" -> {
                p.convert(p.source);target=new Player(p.world);p.arrow.ritual=mode.startsWith("ritual-");
                if(mode.endsWith("-expired"))p.ritualConvertedMobUntilMillis.put(p.source.id,0L);
                if(mode.endsWith("-forgotten")||mode.endsWith("-removed")||mode.endsWith("-null-owner"))p.ritualConvertedMobUntilMillis.clear();
                if(mode.endsWith("-removed")||mode.endsWith("-null-owner")){p.ownedEntities.remove(p.source.id);p.source.remove();}
                if(mode.endsWith("-null-owner"))p.arrow.shooter=null;
            }
            case "player-hit" -> target=new Player(p.world);
            case "friendly-hit" -> {var ally=new Skeleton(p.world);p.own(ally);p.convert(ally);target=ally;}
            case "foreign-hit" -> p.ownedEntities.remove(p.enemy.id);
            case "dead-target" -> p.enemy.dead=true;
            case "target-world" -> p.enemy.world=new World();
            case "expired-source","cleanup-expired" -> p.ritualConvertedMobUntilMillis.put(p.source.id,0L);
            case "dead-source","cleanup-dead" -> p.source.dead=true;
            case "missing-source","cleanup-missing" -> p.ownedEntities.remove(p.source.id);
            case "source-world" -> p.source.world=new World();
            case "arrow-world" -> p.arrow.world=new World();
            case "stale-source" -> p.source.generation--;
            case "stale-arrow" -> p.arrow.generation--;
            case "changed-owner" -> {var other=new Skeleton(p.world);p.own(other);p.convert(other);p.arrow.shooter=other;}
            case "changed-wave","cleanup-wave" -> {p.activeWave=7;p.phase=EventPhase.WAVE_7;}
            case "cleanup-age" -> p.activeEventArrowAges.put(p.arrow.id,EVENT_ARROW_MAX_TICKS-1);
            case "cancelled-hit" -> {}
            default -> throw new AssertionError(mode);
        }
        if(mode.startsWith("cleanup-")){
            p.tickEventArrowProjectiles();
            p.require(!p.arrow.valid&&p.activeEventArrowAges.isEmpty()&&!p.waveProjectileAdmission.children.contains(p.arrow.id),
                "invalid/expired converted arrow retained entity or projectile budget");return;
        }
        var collision=new ProjectileCollideEvent(p.arrow,target);p.onEventProjectileCollide(collision);
        if(!mode.equals("cancelled-hit"))p.require(collision.cancelled!=allowed,"wrong faction/collision admission: "+mode);
        var damage=new EntityDamageByEntityEvent(p.arrow,target);
        if(mode.equals("cancelled-hit"))damage.cancelled=true;
        p.onStaleEventArrowDamage(damage);p.onEventSkeletonArrowDamage(damage);p.onCustomEventArrowDamage(damage);
        p.require(damage.cancelled!=allowed,"wrong final damage admission: "+mode);
        if(!allowed)p.require(!p.arrow.valid&&p.activeEventArrowAges.isEmpty(),"rejected hit retained projectile ownership");
    }
}
'''
    path=out/"ConvertedArrowProbe.java"
    path.write_text(code,encoding="utf-8")
    result=subprocess.run(["javac","-encoding","UTF-8","-sourcepath",str(ROOT/"copimine-end-event/src"),
                           "-d",str(out),str(path)],capture_output=True,text=True,encoding="utf-8",errors="replace",timeout=40)
    assert result.returncode==0,result.stdout+result.stderr
    return out


@pytest.mark.parametrize("scenario", [
    "valid-launch","valid-hit","ordinary-normal","ritual-normal","ordinary-before-conversion","ritual-before-conversion",
    "ritual-before-conversion-expired","ritual-before-conversion-forgotten","ritual-before-conversion-removed","ritual-before-conversion-null-owner",
    "launch-cover","launch-expired","launch-dead","launch-missing","launch-stale","launch-wrong-world",
    "launch-owner-mismatch","launch-no-target","launch-player","launch-friendly",
    "launch-foreign-target","launch-dead-target","launch-target-world","launch-budget","launch-cooldown",
    "player-hit","friendly-hit","foreign-hit","dead-target","target-world","expired-source",
    "dead-source","missing-source","source-world","arrow-world","stale-source","stale-arrow",
    "changed-owner","changed-wave","cancelled-hit",
    "cleanup-expired","cleanup-dead","cleanup-missing","cleanup-wave","cleanup-age",
])
def test_converted_arrow_keeps_native_lifecycle_and_hostile_only_admission(converted_arrow_probe,scenario):
    result=subprocess.run(["java","-cp",str(converted_arrow_probe),"ConvertedArrowProbe",scenario],
                          capture_output=True,text=True,encoding="utf-8",errors="replace",timeout=15)
    assert result.returncode==0,result.stdout+result.stderr
