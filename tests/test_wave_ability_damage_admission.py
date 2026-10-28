"""Execute the real wave ability handlers through accepted/cancelled damage events."""
from pathlib import Path
import subprocess

import pytest

from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]


@pytest.fixture(scope="module")
def ability_probe(tmp_path_factory):
    output = tmp_path_factory.mktemp("wave-ability-admission")
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    signatures = [
        "private static final class PendingPlayerDamageOutcome",
        "void onPendingPlayerDamageOutcome(EntityDamageEvent event)",
        "void miniBossVoidSnare(LivingEntity miniBoss, Player target, Location mark)",
        "void miniBossEchoPulse(LivingEntity miniBoss)",
        "void miniBossRiftStep(LivingEntity miniBoss, Player target)",
        "void resolveNamedTrialBeam(",
        "void applyRealitySplitDirectionalDamage(",
    ]
    if "boolean applyWaveAbilityDamage(" in source:
        signatures.append("boolean applyWaveAbilityDamage(")
    methods = "\n".join(extract(source, signature) for signature in signatures)
    methods = methods.replace("org.bukkit.potion.", "").replace("org.bukkit.FluidCollisionMode", "FluidCollisionMode")
    java = output / "WaveAbilityAdmissionProbe.java"
    java.write_text(r'''
import java.util.*;
import me.copimine.endevent.domain.WaveDamagePolicy;
public class WaveAbilityAdmissionProbe {
    static final int SLOWNESS_DEBUFF_TICKS=40, MINI_VOID_SNARE_DEBUFF_TICKS=40, MINI_ECHO_PULSE_DEBUFF_TICKS=40;
    static final double WAVE_MOB_DAMAGE_REDUCTION=0.75;
    enum PotionEffectType {SLOWNESS,WEAKNESS,BLINDNESS}
    enum Particle {REVERSE_PORTAL,END_ROD,PORTAL,DRAGON_BREATH}
    enum FluidCollisionMode {NEVER}
    static class World {boolean blocked;Object rayTraceBlocks(Location l,Vector v,double d,FluidCollisionMode f,boolean b){return blocked?new Object():null;}}
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
        double getX(){return x;}double getY(){return y;}double getZ(){return z;}
    }
    static class Location {
        double x,y,z; Location(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
        double distanceSquared(Location p){return (x-p.x)*(x-p.x)+(y-p.y)*(y-p.y)+(z-p.z)*(z-p.z);}
        Vector toVector(){return new Vector(x,y,z);}
        Location add(double a,double b,double c){x+=a;y+=b;z+=c;return this;}
        double getX(){return x;}double getY(){return y;}double getZ(){return z;}
        Location add(Vector v){return add(v.x,v.y,v.z);}
        double distance(Location p){return Math.sqrt(distanceSquared(p));}
        public Location clone(){return new Location(x,y,z);}
    }
    static class Entity { final UUID id=UUID.randomUUID();UUID getUniqueId(){return id;} }
    static class LivingEntity extends Entity {
        boolean valid=true,dead,visible=true;
        boolean isValid(){return valid;}boolean isDead(){return dead;}
        Location getLocation(){return new Location(0,0,0);}Location getEyeLocation(){return getLocation().add(0,1.6,0);}
        World getWorld(){return WORLD;}
        boolean hasLineOfSight(Entity e){return visible;}
        double getHeight(){return 2;}
    }
    static class Player extends LivingEntity {
        final WaveAbilityAdmissionProbe owner;int effects,impulses,damageCalls;double health=20;
        boolean cancel,observe=true,advanceGeneration,throwDamage,blocked,absorb;double absorption,y;
        Player(WaveAbilityAdmissionProbe owner){this.owner=owner;}
        void damage(double amount,Entity attacker){
            damageCalls++;
            if(throwDamage)throw new IllegalStateException("injected damage failure");
            var event=new EntityDamageByEntityEvent(this,attacker,cancel);
            event.finalDamage=blocked||absorb?0:amount;
            if(observe)owner.onPendingPlayerDamageOutcome(event);
            if(advanceGeneration)owner.generation++;
            if(!cancel&&!blocked){if(absorb)absorption-=amount;else health-=amount;}
        }
        boolean isOnline(){return true;}boolean isInvulnerable(){return false;}
        double getHealth(){return health;}
        double getAbsorptionAmount(){return absorption;}
        void addPotionEffect(PotionEffect effect){effects++;}
        void setVelocity(Vector v){impulses++;}
        Vector getVelocity(){return new Vector(0,0,0);}
        Location getLocation(){return new Location(1,y,0);}
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
    record Tuning(){double voidSnareDamage(){return 4;}double echoPulseDamage(){return 5;}double riftStepDamage(){return 3;}}
    record Config(){Tuning miniBossTuning(){return new Tuning();}double containmentRadius(){return 20;}}
    final Config config=new Config();String eventId="event";long generation=7;
    PendingPlayerDamageOutcome pendingPlayerDamageOutcome;
    final List<Player> players=new ArrayList<>();boolean blockRay=true;
    boolean usesWaveCombatCoordination(LivingEntity e){return true;}
    boolean isMiniBossTargetAllowed(Entity e,Player p){return true;}
    boolean hasProjectileBlockLineOfSight(Location a,Location b){return blockRay;}
    boolean ownedBySession(Entity e,String id,long gen){return eventId.equals(id)&&generation==gen;}
    double horizontalDistanceSquared(Location a,Location b){return (a.x-b.x)*(a.x-b.x)+(a.z-b.z)*(a.z-b.z);}
    int abilityDebuffAmplifier(String s){return 1;}int weaknessDebuffAmplifier(String s){return 0;}
    List<Player> activeLivingPlayers(){return players;}
    static class RealitySplitTrialRuntimeState {
        Vector direction=new Vector(1,0,0);Location previousLocation;
        Set<UUID> hitPlayers=new HashSet<>();Map<UUID,Long> launchIncarnations=new HashMap<>();
    }
    static class Lifecycle {boolean incarnationMatches(UUID id,long gen,long incarnation){return true;}}
    final Lifecycle attemptLifecycle=new Lifecycle();
    List<Player> realitySplitTrialTargets(Entity actor){return players;}
    boolean isCurrentRealitySplitTrialTarget(Entity actor,Player target){return true;}
    boolean isOfficialWave7ReturnContext(){return false;}
    boolean hasNamedTrialLineOfSight(Location a,Location b){return blockRay;}
    Location coreCombatAnchorLocation(){return new Location(0,0,0);}
    Location findSafeCombatLocation(Location a,Location b,double radius){return null;}
    boolean teleportCombatEntity(Entity e,Location l){return true;}
    double distanceSquaredToSegment(Vector p,Vector a,Vector b){return 0;}
    void spawnEventParticle(Location l,Particle p,int count,double x,double y,double z,double speed){}
''' + methods + r'''
    static void require(boolean pass,String reason){if(!pass)throw new AssertionError(reason);}
    public static void main(String[] args){
        var main=new WaveAbilityAdmissionProbe();var mob=new LivingEntity();var target=new Player(main);main.players.add(target);
        String scenario=args[0];boolean snare=scenario.startsWith("snare");
        target.cancel=scenario.contains("cancelled");
        target.observe=!scenario.contains("unobserved");
        target.advanceGeneration=scenario.contains("stale");
        target.blocked=scenario.contains("shield");target.absorb=scenario.contains("absorption");
        if(target.absorb)target.absorption=20;
        if(scenario.contains("cover"))mob.visible=false;
        if(scenario.contains("blocked-ray"))main.blockRay=false;
        if(scenario.contains("above"))target.y=6;
        if(scenario.contains("below"))target.y=-6;
        boolean warden=scenario.startsWith("warden"),step=scenario.startsWith("step"),beam=scenario.startsWith("beam");
        WORLD.blocked=scenario.contains("blocked-ray");
        var state=new RealitySplitTrialRuntimeState();
        if(warden)main.applyRealitySplitDirectionalDamage(mob,state,1.35,7,5.5,false);
        else if(step)main.miniBossRiftStep(mob,target);
        else if(beam)main.resolveNamedTrialBeam(mob,target,0);
        else if(snare)main.miniBossVoidSnare(mob,target,target.getLocation());else main.miniBossEchoPulse(mob);
        boolean accepted=scenario.endsWith("accepted");
        if(accepted){
            require(target.damageCalls==1&&(target.health<20||target.absorb&&target.absorption<20),"accepted attack deals one real hit");
            require(target.effects==(warden?0:snare?2:1),"accepted attack keeps approved debuffs");
            require(target.impulses==(snare||step||beam?0:1),"accepted pulse keeps its impulse");
        } else {
            require(target.effects==0,"rejected/stale/occluded hit must not apply debuffs");
            require(target.impulses==0,"rejected/stale/occluded hit must not apply knockback");
            if(scenario.contains("cover")||scenario.contains("blocked-ray"))require(target.damageCalls==0,"solid cover prevents the pulse hit");
            if(warden)require(state.hitPlayers.isEmpty(),"rejected hit must not commit an accepted Warden hit receipt");
        }
        require(main.pendingPlayerDamageOutcome==null,"nested damage context must be restored");
    }
}
''', encoding="utf-8")
    policy = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/WaveDamagePolicy.java"
    compiled = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(output), str(policy), str(java)], capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    return output


@pytest.mark.parametrize("scenario", [
    "snare-cancelled", "pulse-cancelled", "snare-unobserved", "pulse-unobserved",
    "snare-stale", "pulse-stale", "pulse-cover", "pulse-blocked-ray",
    "snare-shield", "pulse-shield", "snare-accepted", "pulse-accepted", "pulse-absorption-accepted",
    "warden-cancelled", "warden-stale", "warden-blocked-ray", "warden-above", "warden-below", "warden-accepted",
    "step-cancelled", "step-unobserved", "step-accepted", "beam-cancelled", "beam-stale", "beam-blocked-ray", "beam-accepted",
])
def test_wave_ability_effects_require_an_accepted_current_hit(ability_probe, scenario):
    result = subprocess.run(["java", "-cp", str(ability_probe), "WaveAbilityAdmissionProbe", scenario], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
