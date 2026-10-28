"""Execute production boss attacks, including the final MONITOR damage outcome."""
from pathlib import Path
import subprocess

import pytest

from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]


@pytest.fixture(scope="module")
def boss_attack_probe(tmp_path_factory):
    out = tmp_path_factory.mktemp("boss-attack-admission")
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    signatures = [
        "private static final class PendingPlayerDamageOutcome",
        "void onPendingPlayerDamageOutcome(EntityDamageEvent event)",
        "boolean applyContactHit(", "void voidBlast(",
        "void executeBossFinalStrikeImpact(",
        "void executeContactBossAbility(", "Vector contactFacing(",
        "void onRiftProjectileHit(", "void voidMark(",
        "void startChestStrike(",
        "boolean applyBossActorAbilityDamage(",
        "void reconcileBossCastProjection(",
    ]
    if "boolean applyBossAbilityDamage(" in source:
        signatures.append("boolean applyBossAbilityDamage(")
    methods = "\n".join(extract(source, signature) for signature in signatures)
    java = out / "BossAttackAdmissionProbe.java"
    java.write_text(r'''
import java.util.*;
import me.copimine.endevent.domain.*;
public class BossAttackAdmissionProbe {
    static final int SLOWNESS_DEBUFF_TICKS=40, MINI_VOID_SNARE_DEBUFF_TICKS=40, MINI_ECHO_PULSE_DEBUFF_TICKS=40;
    static final double WAVE_MOB_DAMAGE_REDUCTION=0.75;
    enum PotionEffectType {SLOWNESS,WEAKNESS,BLINDNESS,WITHER}
    enum Particle {REVERSE_PORTAL,END_ROD,PORTAL,DRAGON_BREATH,SWEEP_ATTACK,EXPLOSION,SOUL_FIRE_FLAME}
    enum FluidCollisionMode {NEVER}
    static class World {void playSound(Object... args){} void spawnParticle(Object... args){} boolean blocked;Object rayTraceBlocks(Location l,Vector v,double d,FluidCollisionMode f,boolean b){return blocked?new Object():null;}}
    static final World WORLD=new World();
    record PotionEffect(PotionEffectType type,int ticks,int amplifier,boolean a,boolean b,boolean c) {}
    static class Vector {
        double x,y,z; Vector(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
        double lengthSquared(){return x*x+y*y+z*z;}
        double length(){return Math.sqrt(lengthSquared());}
        Vector normalize(){double d=Math.sqrt(lengthSquared());x/=d;y/=d;z/=d;return this;}
        Vector multiply(double d){x*=d;y*=d;z*=d;return this;}
        Vector setY(double d){y=d;return this;}
        Vector subtract(Vector v){x-=v.x;y-=v.y;z-=v.z;return this;}
        Vector add(Vector v){x+=v.x;y+=v.y;z+=v.z;return this;}
        public Vector clone(){return new Vector(x,y,z);}
        double dot(Vector v){return x*v.x+y*v.y+z*v.z;} double getX(){return x;}double getY(){return y;}double getZ(){return z;}
    }
    static class Location {
        double x,y,z; Location(World w,double x,double y,double z){this(x,y,z);} Location(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
        World getWorld(){return WORLD;} double distanceSquared(Location p){return (x-p.x)*(x-p.x)+(y-p.y)*(y-p.y)+(z-p.z)*(z-p.z);}
        Vector getDirection(){return new Vector(1,0,0);} Vector toVector(){return new Vector(x,y,z);}
        Location add(double a,double b,double c){x+=a;y+=b;z+=c;return this;}
        double getX(){return x;}double getY(){return y;}double getZ(){return z;}
        Location add(Vector v){return add(v.x,v.y,v.z);}
        double distance(Location p){return Math.sqrt(distanceSquared(p));}
        public Location clone(){return new Location(x,y,z);}
    }
    static class Entity { final UUID id=UUID.randomUUID();UUID getUniqueId(){return id;} }
    static class LivingEntity extends Entity {
        void setInvulnerable(boolean b){}
        boolean valid=true,dead,visible=true;
        boolean isValid(){return valid;}boolean isDead(){return dead;}
        Location getLocation(){return new Location(0,0,0);}Location getEyeLocation(){return getLocation().add(0,1.6,0);}
        World getWorld(){return WORLD;}
        boolean hasLineOfSight(Entity e){return visible;}
        double getHeight(){return 2;}
    }
    static class Player extends LivingEntity {
        final BossAttackAdmissionProbe owner;int effects,impulses,damageCalls;double health=20;
        boolean cancel,observe=true,advanceGeneration,throwDamage,blocked,absorb,leaveRoster,changeBoss;double absorption,y,x=1;
        Player(BossAttackAdmissionProbe owner){this.owner=owner;}
        void damage(double amount,Entity attacker){
            damageCalls++;
            if(throwDamage)throw new IllegalStateException("injected damage failure");
            var event=new EntityDamageByEntityEvent(this,attacker,cancel);
            event.finalDamage=blocked||absorb?0:amount;
            if(observe)owner.onPendingPlayerDamageOutcome(event);
            if(advanceGeneration)owner.generation++; if(leaveRoster)owner.admitted=false; if(changeBoss)owner.current=false;
            if(!cancel&&!blocked){if(absorb)absorption-=amount;else health-=amount;}
        }
        boolean isOnline(){return true;}boolean isInvulnerable(){return false;}
        double getHealth(){return health;}
        double getAbsorptionAmount(){return absorption;}
        void addPotionEffect(PotionEffect effect){effects++;} void addPotionEffect(PotionEffect effect,boolean force){effects++;} void playEffect(EntityEffect effect){effects++;}
        void setVelocity(Vector v){impulses++;}
        Vector getVelocity(){return new Vector(0,0,0);}
        Location getLocation(){return new Location(x,y,0);}
    }
    static class EntityDamageEvent {
        enum DamageCause { CUSTOM }
        final Entity entity;final boolean cancelled;double finalDamage;
        EntityDamageEvent(Entity entity,boolean cancelled){this.entity=entity;this.cancelled=cancelled;}
        Entity getEntity(){return entity;}boolean isCancelled(){return cancelled;}
        double getFinalDamage(){return finalDamage;}
        DamageCause getCause(){return DamageCause.CUSTOM;}
    }
    static class EntityDamageByEntityEvent extends EntityDamageEvent {
        final Entity damager;
        EntityDamageByEntityEvent(Entity target,Entity damager,boolean cancelled){super(target,cancelled);this.damager=damager;}
        Entity getDamager(){return damager;}
    }

    enum EntityEffect {HURT}
    enum Sound {ENTITY_ENDERMAN_STARE,ENTITY_GENERIC_EXPLODE,ENTITY_WITHER_HURT,ENTITY_PLAYER_ATTACK_SWEEP,ENTITY_WARDEN_SONIC_CHARGE}
    enum SoundCategory {HOSTILE}
    enum EventPhase {BOSS_ACTIVE,VICTORY_PROCESSING}
    EventPhase phase=EventPhase.BOSS_ACTIVE;
    boolean admitted=true,current=true;
    boolean testCombatAiMode=false;
    final LivingEntity boss=new LivingEntity();
    UUID bossUuid=boss.getUniqueId();
    BossAbilityState bossAbilityState=BossAbilityState.NONE;
    long bossCastDeadlineMillis,bossSpellPauseUntilMillis,bossChestStrikeSequence,bossCastSequence;
    boolean bossChestStrikeActive,bossEdgeVolleyActive;
    BukkitTask bossCastTask;
    BossPhase bossPhase=BossPhase.HUNT;
    static final double BOSS_BLAST_DAMAGE=6;
    static final int BOSS_BLAST_DEBUFF_TICKS=40;
    static final double BOSS_PROJECTILE_DAMAGE=6,VOID_MARK_DAMAGE=6,VOID_MARK_RADIUS_BLOCKS=3;
    static final int VOID_MARK_DEBUFF_TICKS=40,VOID_MARK_DURATION_SECONDS=3,MAX_ACTIVE_VOID_MARKS=3;
    static class Registry {boolean owns(long g){return g==7;}}
    final Registry taskRegistry=new Registry();
    record TimedRelease(long delay,Runnable callback){}
    static class BukkitTask {void cancel(){}boolean isCancelled(){return false;}}
    static class Scheduler {
        Runnable callback;final List<TimedRelease> releases=new ArrayList<>();
        BukkitTask runTaskTimer(Object p,Runnable r,long d,long period){callback=r;return new BukkitTask();}
        BukkitTask runTaskLater(Object p,Runnable r,long d){releases.add(new TimedRelease(d,r));return new BukkitTask();}
    }
    static class Bukkit {static final Scheduler scheduler=new Scheduler();static Scheduler getScheduler(){return scheduler;}}
    final Map<UUID,BukkitTask> activeVoidMarkTasks=new HashMap<>();
    final Map<UUID,Location> activeVoidMarkCenters=new HashMap<>();
    final Set<UUID> activeRiftProjectiles=new HashSet<>();
    static class Snowball extends Entity {}
    record ProjectileHitEvent(Snowball projectile,Entity hit){Entity getEntity(){return projectile;}Entity getHitEntity(){return hit;}}
    String eventId="event";long generation=7;
    PendingPlayerDamageOutcome pendingPlayerDamageOutcome;
    final List<Player> players=new ArrayList<>();
    boolean isActiveBossParticipant(Player p){return admitted;}
    boolean isCombatTarget(Player p){return admitted;}
    LivingEntity liveBoss(){return boss;}
    boolean ownedBySession(Entity e,String id,long gen){return true;}
    boolean isCurrentBossCarrier(LivingEntity b){return current && b==boss;}
    boolean isTestBoss(LivingEntity b){return false;}
    boolean hasProjectileBlockLineOfSight(Location a,Location b){return boss.visible;}
    List<Player> activeLivingPlayers(){return players;}
    List<Player> activeBossParticipants(){return players;}
    void spawnEventParticle(Object... args){}
    void releaseBossHazardReservation(String s){}
    void registerEncounterTask(BukkitTask t){}
    void cancelVoidMark(UUID id){activeVoidMarkCenters.remove(id);}
    void cleanupRiftProjectile(UUID id){activeRiftProjectiles.remove(id);}
    void renderBossFinalStrikeImpact(Location mark,double radius){}
    int abilityDebuffAmplifier(String s){return 0;}
    java.util.logging.Logger getLogger(){return java.util.logging.Logger.getAnonymousLogger();}
    static class EventConfig {
        record BossFinalStrikeTuning(){double radius(){return 4;} double damage(){return 6;} int witherTicks(){return 40;}}
        BossFinalStrikeTuning finalStrikeTuning(){return new BossFinalStrikeTuning();}
    }
    final EventConfig config=new EventConfig();
    ''' + methods + r'''
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args){
        var p=new BossAttackAdmissionProbe();var target=new Player(p);p.players.add(target);
        String s=args[0];
        target.cancel=s.contains("cancelled");target.observe=!s.contains("unobserved");
        target.blocked=s.contains("shield");target.advanceGeneration=s.contains("stale");
        target.leaveRoster=s.contains("left");target.changeBoss=s.contains("replaced");
        target.absorb=s.contains("absorption");target.absorption=20;
        p.admitted=!s.contains("outsider");p.boss.visible=!s.contains("cover");
        if(s.contains("range"))target.x=30;
        if(s.contains("above"))target.y=10;
        if(s.contains("behind"))target.x=-2;
        if(s.startsWith("contact"))p.applyContactHit(target,p.boss,6,new Vector(1,0,0));
        else if(s.startsWith("blast"))p.voidBlast(p.boss,target);
        else if(s.startsWith("actor")){
            boolean accepted=p.applyBossActorAbilityDamage(p.boss,new LivingEntity(),target,6);
            if(accepted)target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER,40,0,false,true,true));
        }
        else if(s.startsWith("chest")){
            p.startChestStrike(p.boss,target,p.generation);
            p.reconcileBossCastProjection(p.boss,System.currentTimeMillis()+100_000);
            check(p.bossAbilityState==BossAbilityState.EXECUTING,"slow chest flight admitted another major cast");
            check(Bukkit.scheduler.releases.getFirst().delay()==1,"chest launch was delayed past its authored release marker");
            for(var release:Bukkit.scheduler.releases)release.callback().run();
            if(s.endsWith("accepted"))check(p.bossCastDeadlineMillis-System.currentTimeMillis()>=2400,
                    "chest recovery cut off the remaining authored animation");
        }
        else if(s.startsWith("swipe")||s.startsWith("slam"))p.executeContactBossAbility(p.boss,target,
                s.startsWith("swipe")?EndRiftAiPolicy.BossSpell.MELEE_SWIPE:EndRiftAiPolicy.BossSpell.GROUND_SLAM,p.generation);
        else if(s.startsWith("projectile")){
            var projectile=new Snowball();p.activeRiftProjectiles.add(projectile.id);
            p.onRiftProjectileHit(new ProjectileHitEvent(projectile,target));
        }
        else if(s.startsWith("mark")){p.voidMark(p.boss,target);Bukkit.scheduler.callback.run();}
        else p.executeBossFinalStrikeImpact(p.boss,target.getLocation(),target.getUniqueId());
        if(s.endsWith("accepted")){
            check(target.damageCalls==1,"legal attack must deal exactly one hit");
            check(target.effects==(s.startsWith("mark")?2:1),"accepted attack must keep its reaction/debuff, including absorption");
            check(target.impulses==(s.startsWith("final")||s.startsWith("projectile")||s.startsWith("mark")||s.startsWith("actor")?0:1),"accepted attack impulse");
        } else {
            check(target.effects==0 && target.impulses==0,"cancelled, protected, stale or foreign hit applied a reaction");
            if(s.contains("outsider")||s.contains("cover"))check(target.damageCalls==0,"foreign or occluded target was damaged");
        }
        check(p.pendingPlayerDamageOutcome==null,"damage context leaked");
    }
}
''', encoding="utf-8")
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-sourcepath", str(ROOT / "copimine-end-event/src"), "-d", str(out), str(java)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    return out


@pytest.mark.parametrize("attack", ["contact", "blast", "final", "swipe", "slam", "projectile", "mark", "chest", "actor"])
@pytest.mark.parametrize("outcome", ["cancelled", "unobserved", "shield", "stale", "left", "replaced", "outsider", "cover", "accepted", "absorption-accepted"])
def test_boss_hit_reactions_follow_the_current_accepted_damage(boss_attack_probe, attack, outcome):
    if attack in {"projectile", "actor"} and outcome == "cover":
        pytest.skip("Physical projectile collision, rather than caster sight, owns cover")
    result = subprocess.run(["java", "-cp", str(boss_attack_probe), "BossAttackAdmissionProbe", f"{attack}-{outcome}"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr


@pytest.mark.parametrize("scenario", ["swipe-range", "swipe-above", "swipe-behind", "slam-range", "slam-above"])
def test_boss_contact_volume_can_really_miss(boss_attack_probe, scenario):
    result = subprocess.run(["java", "-cp", str(boss_attack_probe), "BossAttackAdmissionProbe", scenario], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
