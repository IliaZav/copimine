"""An occluded legal Archmage target still permits bounded Step, never blind damage.

The probe executes the production selection, checkpoint, target-admission,
resolution and relocation methods with real room controllers and actor policy.
Only Bukkit world/player operations are adapted; no test-only branch is added.
"""
from pathlib import Path
import subprocess

import pytest

from tests.test_wave7_admission_runtime_adapter import BASE, PRODUCTION, HARNESS
from tests.test_wave7_named_trial_warning_audio_runtime import extract_method

SOURCE = BASE / "CopiMineEndEvent.java"


@pytest.fixture(scope="module")
def occluded_archmage_probe(tmp_path_factory):
    out = tmp_path_factory.mktemp("wave7-archmage-occluded")
    source = SOURCE.read_text(encoding="utf-8")
    signatures = (
        "private boolean tickNamedTrialActor(LivingEntity actor,",
        # Roster basis for miniboss scaling: the probe executes the real
        # production resolution path, which reads the frozen roster.
        "private int wave7FrozenRosterSize(",
        # SUPERSEDED: the closest-player picker was replaced by the
        # party-sweeping realitySplitTrialTarget(...), which the probe must now
        # execute to exercise real target selection.
        "private Player realitySplitTrialTarget(",
        "private Player closestRealitySplitTrialParticipant(",
        "private List<Player> realitySplitTrialTargets(",
        "private boolean isCurrentRealitySplitTrialTarget(",
        "private boolean isNamedRealitySplitActor(",
        "private boolean isChamberWaveEntity(",
        "private int chamberWaveNumber(",
        "private boolean ownedBySession(",
        "private boolean ownedByEvent(",
        "private NamespacedKey namedTrialActorCheckpointKey(",
        "private boolean writeNamedTrialActorCheckpoint(",
        "private Wave7NamedTrialActorPolicy.Checkpoint readNamedTrialActorCheckpoint(",
        "private boolean finishNamedTrialAttack(",
        # The three-argument overload delegates to the timed one, so the probe must
        # compile the real timed body rather than resolve a missing overload.
        "private boolean finishNamedTrialAttack(Entity actor,\n            Wave7NamedTrialActorPolicy.Checkpoint checkpoint, long now, long cooldownMillis)",
        "private boolean resolveNamedTrialAttack(",
        "private boolean stepNamedTrialArchmage(",
        # MAGE7-005: the resolved cast now records what the tool cost through
        # these three methods, so the probe must execute them for real rather
        # than stub the punish-window bookkeeping away.
        "private long archmageToolTick(",
        "private void writeArchmageToolTick(",
        "private void recordArchmageToolUse(",
        # MARK-005 durability: the tick path now reconciles the actor's real
        # visibility with its durable vanish receipt, so the probe must execute
        # that method too.
        "private void enforceNamedTrialVanish(",
    )
    methods = "\n".join(extract_method(source, signature) for signature in signatures)
    code = r'''
import java.util.*;
import me.copimine.endevent.domain.*;
import me.copimine.endevent.domain.Wave7TrialTargetPolicy;
import me.copimine.endevent.runtime.*;
import me.copimine.endevent.runtime.wave7.Wave7AdmissionRuntimeAdapter.Decision;

public class OccludedArchmageProbe {
    // Production visual/audio seams the probed methods call. They are pure
    // presentation, so the probe stubs them and asserts only server semantics.
    void renderWave7AbilityEffect(Object a,Object b,String c){}
    void renderWave7AbilityBurst(Object a,String b){}
    boolean summonWave7MarksmanDefender(Object a,int b){return false;}
    // MARK-005 / MAGE7-004 seams added with the invisibility and Vex support.
    void tickWave7ArchmageVexes(Object a,int b){}
    Wave7NamedTrialActorPolicy.Checkpoint beginMarksmanVanish(Object a,
            Wave7NamedTrialActorPolicy.Checkpoint c,long now){return null;}
    // W7 pair symbiosis: the Marksman tick now also maintains the skeleton pair
    // and can commit the pair's joint crossfire. Both belong to the sibling
    // actor, so the probe stubs them out; the pair rules themselves are covered
    // by Wave7SkeletonPairPolicyTest.
    boolean maintainNamedMarksmanPair(Object a,int b){return true;}
    boolean isPairedCrossfireLeader(Object a,int b){return false;}
    boolean pairedCrossfireReady(Object a,Object b,int c){return false;}
    boolean fireNamedMarksmanPairedCrossfire(Object a,Object b,
            Wave7NamedTrialActorPolicy.Checkpoint c,long d,int e){return true;}
    long generation=Wave7AdmissionRuntimeChecks.GEN, eventTickCounter=1L;
    final String eventId=Wave7AdmissionRuntimeChecks.EVENT.toString();
    final RealitySplitChamberController realitySplitChamberController=Wave7AdmissionRuntimeChecks.chambers;
    final RealitySplitTrialController realitySplitTrialController=Wave7AdmissionRuntimeChecks.trials;
    // Miniboss scaling reads the roster frozen at admission; the probe pins a
    // two-player attempt so the health curve is deterministic.
    final me.copimine.endevent.runtime.EventCombatProfileService combatProfiles=
        new me.copimine.endevent.runtime.EventCombatProfileService();
    final Set<UUID> realitySplitTrialEntityUuids=new HashSet<>();
    // Party-sweeping target selection reads this fairness ledger.
    final Map<UUID,Long> realitySplitLastHitOrdinal=new HashMap<>();
    record NamespacedKey(Object owner,String name) {}
    final NamespacedKey keyChamberId=new NamespacedKey(this,"room"),keyTrialRole=new NamespacedKey(this,"role"),
        keyWave=new NamespacedKey(this,"wave"),keyGeneration=new NamespacedKey(this,"generation"),
        keyEventGeneration=new NamespacedKey(this,"event_generation"),keyEventId=new NamespacedKey(this,"event"),
        keyEventSessionId=new NamespacedKey(this,"session"),
        // MAGE7-005 tool bookkeeping keys the real recorder writes through.
        keyWave7ArchmageStepTick=new NamespacedKey(this,"end_event_wave7_archmage_step_tick"),
        keyWave7ArchmagePunishUntil=new NamespacedKey(this,"end_event_wave7_archmage_punish_until");
    static class Type<T> {}
    static class PersistentDataType {
        static final Type<String> STRING=new Type<>();
        static final Type<Long> LONG=new Type<>();
    }
    static class PersistentDataContainer {
        final Map<Object,Object> values=new HashMap<>();
        <T> void set(Object key,Type<T> type,T value){values.put(key,value);}
        @SuppressWarnings("unchecked") <T> T getOrDefault(Object key,Type<T> type,T fallback){
            return (T)values.getOrDefault(key,fallback);}
        @SuppressWarnings("unchecked") <T> T get(Object key,Type<T> type){return (T)values.get(key);}
    }
    enum EntityType { EVOKER, SKELETON }
    enum Particle { PORTAL, HAPPY_VILLAGER }
    enum Sound { ENTITY_ENDERMAN_TELEPORT }
    static class Vector {
        double x,y,z;Vector(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
        double getX(){return x;}double getY(){return y;}double getZ(){return z;}
        public Vector clone(){return new Vector(x,y,z);}Vector multiply(double m){x*=m;y*=m;z*=m;return this;}
    }
    static class Location {
        double x,y,z;Location(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
        public Location clone(){return new Location(x,y,z);}
        Location add(Vector v){return add(v.x,v.y,v.z);}
        Location add(double x,double y,double z){this.x+=x;this.y+=y;this.z+=z;return this;}
        double getX(){return x;}double getZ(){return z;}double getY(){return y;}void setY(double y){this.y=y;}
        double distanceSquared(Location other){double dx=x-other.x,dy=y-other.y,dz=z-other.z;return dx*dx+dy*dy+dz*dz;}
    }
    static class World {void playSound(Location at,Sound sound,float volume,float pitch){}}
    static class Entity {
        final UUID id;final PersistentDataContainer data=new PersistentDataContainer();
        Location location;Entity(UUID id,Location location){this.id=id;this.location=location;}
        UUID getUniqueId(){return id;}Location getLocation(){return location.clone();}
        PersistentDataContainer getPersistentDataContainer(){return data;}
    }
    static class LivingEntity extends Entity {
        EntityType type;boolean valid=true,dead;double hp=200.0;
        final World world=new World();
        LivingEntity(UUID id,EntityType type,Location at){super(id,at);this.type=type;}
        boolean isValid(){return valid;}boolean isDead(){return dead;}EntityType getType(){return type;}
        double getHealth(){return hp;}double getMaxHealth(){return 200.0;}void setHealth(double hp){this.hp=hp;}
        // The real tick zeroes the horizontal velocity of a committed caster so an
        // authored strike is never carried sideways by locomotion.
        Vector velocity=new Vector(0,0,0);
        Vector getVelocity(){return velocity;}void setVelocity(Vector v){velocity=v;}
        Location getEyeLocation(){return location.clone().add(0,1.6,0);}
        double getEyeHeight(){return 1.6;}World getWorld(){return world;}
        // MARK-005 durability: the real tick path reconciles the actor's effect
        // list with its durable vanish receipt, so the probe needs an effect bag.
        final Set<Object> effects=new HashSet<>();
        boolean hasPotionEffect(Object type){return effects.contains(type);}
        boolean addPotionEffect(Object effect,boolean force){return effects.add(effectType(effect));}
        boolean removePotionEffect(Object type){return effects.remove(type);}
        static Object effectType(Object effect){return ((PotionEffect)effect).type;}
    }
    enum PotionEffectType { INVISIBILITY }
    static class PotionEffect {
        final PotionEffectType type;
        PotionEffect(PotionEffectType type,int duration,int amplifier,boolean ambient,boolean particles,boolean icon){this.type=type;}
    }
    static class Wave7MarksmanInvisibilityPolicy { static final int INVISIBLE_TICKS=100; }
    java.util.logging.Logger getLogger(){return java.util.logging.Logger.getLogger("probe");}
    static class Player extends LivingEntity {
        boolean online=true;
        Player(UUID id){super(id,EntityType.EVOKER,new Location(5,70,0));}
    }
    static class Bukkit {
        static final List<Player> players=new ArrayList<>();
        static Collection<Player> getOnlinePlayers(){return players;}
        static Player getPlayer(UUID id){return players.stream().filter(p->p.online&&p.id.equals(id)).findFirst().orElse(null);}
        // The tick path resolves pair partners and other trial actors by id.
        static Entity getEntity(UUID id){return getOnlinePlayers().stream()
            .filter(p->p.id.equals(id)).findFirst().orElse(null);}
    }
    boolean visible,pathClear=true,safeFloor=true,destinationAllowed=true,teleportAllowed=true;
    int warnings,damageActions,teleports;
    LivingEntity actor;Player target;RealitySplitTrialController.NamedTrialState trial;
    String readString(Entity e,Object key){return (String)e.data.values.getOrDefault(key,"");}
    int readInt(Entity e,Object key,int fallback){return (int)e.data.values.getOrDefault(key,fallback);}
    UUID parseUuidOrNull(String value){try{return UUID.fromString(value);}catch(RuntimeException e){return null;}}
    boolean isRealitySplitTrialRuntimeActive(){return true;}
    boolean isCombatTarget(Player p){return p!=null&&p.online&&p.valid&&!p.dead&&p.hp>0;}
    int wave7PlayerRoom(UUID player){return Wave7AdmissionRuntimeChecks.rooms.getOrDefault(player,-1);}
    boolean realitySplitTargetAllowed(Entity source,Player p){
        return realitySplitChamberController.allowsMobTarget(generation,source.id,p.id)
            &&realitySplitChamberController.allowsPlayerInChamber(generation,p.id,wave7PlayerRoom(p.id));}
    double pursueNamedTrialTarget(LivingEntity actor,Player target,double reach){return 0.0;}
    void moveNamedTrialMarksman(LivingEntity actor,int chamber){}
    // The Archmage's free-roam reposition runs beside the warned Step, and it
    // returns early unless the actor is a Mob. The probe stubs it for the same
    // reason it stubs the Marksman's: the Step under test is
    // stepNamedTrialArchmage, which is executed for real.
    void moveNamedTrialArchmage(LivingEntity actor){}
    void renderNamedTrialTelegraph(LivingEntity a,Player p,Wave7NamedTrialActorPolicy.Attack attack,boolean sound){warnings++;}
    boolean hasNamedTrialLineOfSight(Location from,Location to){return Math.abs(to.z)<0.01?visible:pathClear;}
    Vector horizontalDirection(Location from,Location to){return new Vector(1,0,0);}
    boolean isRealitySplitDestinationAllowed(UUID target,Location to,ChamberIsolationPolicy.Assignment assignment){
        return destinationAllowed&&realitySplitChamberController.allowsPlayerInChamber(generation,target,trial.chamber());}
    boolean isSafeCombatEntityLocation(Location to,LivingEntity source){return safeFloor;}
    boolean teleportCombatEntity(Entity e,Location to){if(!teleportAllowed)return false;e.location=to.clone();teleports++;return true;}
    void spawnEventParticle(Location at,Particle particle,int count,double x,double y,double z,double speed){}
    // --- named-trial aim lock -------------------------------------------------
    // The real lock ledger: stepNamedTrialArchmage() reserves the aim through
    // lockNamedTrialAim() and the tick path refuses to resolve a cast whose lock
    // is missing, stale or belongs to another event. Reproducing that ledger is
    // what makes the "stale cast fails closed" and "safe step teleports once"
    // assertions meaningful, so it is a faithful stub rather than a no-op.
    record TrialAimLock(long generation,String event,long cast,Location point,
            Map<UUID,Location> fangMarks){}
    final Map<UUID,TrialAimLock> trialAimLocks=new HashMap<>();
    void lockNamedTrialAim(LivingEntity actor,Player target,
            Wave7NamedTrialActorPolicy.Attack attack,long cast){
        // The non-fang attacks lock the single target's eye; the fang fan is not
        // exercised by this probe, so it records an empty mark set.
        trialAimLocks.put(actor.getUniqueId(),new TrialAimLock(generation,eventId,cast,
                target.getEyeLocation().clone(),new HashMap<>()));
    }
    // Presentation seams: the authored pose packets and the arrival pose queue.
    void sendTrialPose(Entity actor,String action,String stage,long cast,int duration,int elapsed){}
    void sendTrialFollowupPose(Entity actor,String stage,int duration){}
    // No arrival pose is queued in this probe, so no arrival tick is pending.
    boolean tickNamedTrialArrival(LivingEntity actor){return false;}
    // The strike resolves against the FROZEN aim recorded by lockNamedTrialAim,
    // falling back to the target's eye exactly like production.
    Location namedTrialAim(LivingEntity actor,Player target){
        TrialAimLock lock=trialAimLocks.get(actor.getUniqueId());
        return lock!=null&&lock.generation()==generation&&Objects.equals(lock.event(),eventId)
            ? lock.point().clone() : target.getEyeLocation().clone();
    }
    // Pair symbiosis belongs to the sibling Marksman; the pair rules have their
    // own policy test, so the archmage probe reports "no partner".
    UUID namedMarksmanPartnerId(LivingEntity actor,int chamber){return null;}
    boolean beginNamedMarksmanCrossfire(LivingEntity actor,Player target,
            RealitySplitTrialController.NamedTrialState trial,
            Wave7NamedTrialActorPolicy.Checkpoint mine,long now){return false;}
    boolean launchNamedTrialArrow(LivingEntity a,Player p,Wave7NamedTrialActorPolicy.Attack attack,float speed,float spread,boolean glow,boolean spear){damageActions++;return true;}
    // A spread burst fires its whole fan through the production method.
    boolean launchMarksmanFan(LivingEntity a,Player p,Wave7NamedTrialActorPolicy.Attack attack,int fan){damageActions+=8;return true;}
    boolean spawnNamedTrialFangs(LivingEntity a,Player p,Wave7NamedTrialActorPolicy.Attack attack){damageActions++;return true;}
    void resolveNamedTrialBeam(LivingEntity a,Player p,int chamber){damageActions++;}
    boolean spawnNamedTrialFangCircle(LivingEntity a,Player p,Wave7NamedTrialActorPolicy.Attack attack){damageActions++;return true;}
    boolean beginNamedTrialFangCrown(LivingEntity a,Player p){damageActions++;return true;}
    boolean resolveNamedTrialRepulse(LivingEntity a,Player p,Wave7NamedTrialActorPolicy.Attack attack){damageActions++;return true;}

    void setup(boolean marksman,long attackIndex){
        int room=marksman?2:3;
        trial=realitySplitTrialController.namedTrial(room);
        actor=new LivingEntity(marksman?Wave7AdmissionRuntimeChecks.MARKSMAN:Wave7AdmissionRuntimeChecks.ARCHMAGE,
            marksman?EntityType.SKELETON:EntityType.EVOKER,new Location(0,70,0));
        target=new Player(marksman?Wave7AdmissionRuntimeChecks.C:Wave7AdmissionRuntimeChecks.D);
        Bukkit.players.add(target);
        actor.data.values.put(keyChamberId,room);actor.data.values.put(keyTrialRole,trial.trial().name());
        actor.data.values.put(keyWave,7);actor.data.values.put(keyEventId,eventId);
        actor.data.values.put(keyEventGeneration,generation);
        actor.data.values.put(new NamespacedKey(this,"end_event_named_trial_schema"),"2");
        realitySplitTrialEntityUuids.add(actor.id);
        require(writeNamedTrialActorCheckpoint(actor,new Wave7NamedTrialActorPolicy.Checkpoint(
            UUID.fromString(eventId),generation,room,trial.trial(),actor.id,attackIndex,
            Wave7NamedTrialActorPolicy.Attack.NONE,0L,0L,null,false)),"initial checkpoint");
    }
    Wave7NamedTrialActorPolicy.Checkpoint checkpoint(){return readNamedTrialActorCheckpoint(actor,trial);}
    // Simulates the telegraph window having elapsed. Production writes the
    // checkpoint deadline and the aim lock in the same breath, so the expiry
    // rewrite keeps them in sync; leaving a lock that disagrees with its
    // checkpoint would test a half-written state production cannot produce.
    void expireWarning(){
        var c=checkpoint();
        long expired=System.currentTimeMillis()-1_000L;
        require(writeNamedTrialActorCheckpoint(actor,new Wave7NamedTrialActorPolicy.Checkpoint(
            c.eventId(),c.generation(),c.chamber(),c.trial(),c.actorId(),c.attackIndex(),
            c.attack(),expired,c.cooldownUntilMillis(),c.targetId(),c.supportUsed())),"expire warning");
        TrialAimLock armed=trialAimLocks.get(actor.getUniqueId());
        if(armed!=null)trialAimLocks.put(actor.getUniqueId(),
            new TrialAimLock(armed.generation(),armed.event(),expired,armed.point(),armed.fangMarks()));
    }
    void assertNoAction(){
        require(checkpoint().attack()==Wave7NamedTrialActorPolicy.Attack.NONE&&warnings==0
            &&damageActions==0&&teleports==0,"ineligible target or cooldown must not start any action");
    }
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
''' + methods + r'''
    public static void main(String[] args){
        Wave7AdmissionRuntimeChecks.boot();
        var p=new OccludedArchmageProbe();String mode=args[0];
        long ordinal=mode.startsWith("occluded-ordinal-")?Long.parseLong(mode.substring(17)):0L;
        p.setup(mode.equals("marksman-occluded"),ordinal);
        if(mode.equals("cross-room-only")){
            Bukkit.players.clear();Bukkit.players.add(new Player(Wave7AdmissionRuntimeChecks.A));
        }
        if(mode.equals("connected-other-room")||mode.equals("same-room-with-nearer-other-room")){
            require(p.realitySplitChamberController.markChamberComplete(p.generation,0),"complete neighboring room");
            require(p.realitySplitChamberController.openCompletedPassage(p.generation,0,3),"open legal passage");
            Player neighbor=new Player(Wave7AdmissionRuntimeChecks.A);neighbor.location.x=2.0;
            if(mode.equals("connected-other-room"))Bukkit.players.clear();
            Bukkit.players.add(neighbor);
        }
        if(mode.equals("no-target"))Bukkit.players.clear();
        if(mode.equals("cooldown")){
            var c=p.checkpoint();
            p.writeNamedTrialActorCheckpoint(p.actor,new Wave7NamedTrialActorPolicy.Checkpoint(
                c.eventId(),c.generation(),c.chamber(),c.trial(),c.actorId(),c.attackIndex(),
                c.attack(),0L,System.currentTimeMillis()+60_000L,null,false));
        }
        p.visible=mode.equals("visible-normal");
        require(p.tickNamedTrialActor(p.actor,p.trial),"valid actor tick must remain active");
        if(Set.of("cross-room-only","connected-other-room","no-target","cooldown","marksman-occluded").contains(mode)){
            p.assertNoAction();return;
        }
        if(mode.equals("visible-normal")){
            require(p.checkpoint().attack()==Wave7NamedTrialActorPolicy.Attack.ARCHMAGE_FANGS,
                "visible target preserves ordinary attack rotation");return;
        }
        require(p.checkpoint().attack()==Wave7NamedTrialActorPolicy.Attack.ARCHMAGE_STEP
            &&p.target.id.equals(p.checkpoint().targetId())&&p.warnings==1,
            "occluded eligible target must reserve warned Step instead of stalling or blind damage");
        if(mode.startsWith("occluded-ordinal-")||mode.equals("same-room-with-nearer-other-room"))return;
        p.expireWarning();
        if(mode.equals("blocked-path"))p.pathClear=false;
        if(mode.equals("blocked-destination"))p.destinationAllowed=false;
        if(mode.equals("unsafe-floor"))p.safeFloor=false;
        if(mode.equals("teleport-cancelled"))p.teleportAllowed=false;
        if(mode.equals("target-invalid"))p.target.dead=true;
        if(mode.equals("target-quit"))Bukkit.players.clear();
        if(mode.equals("stale-generation")||mode.equals("stale-checkpoint")){
            p.generation++;
            if(mode.equals("stale-checkpoint"))p.actor.data.values.put(p.keyEventGeneration,p.generation);
            require(!p.tickNamedTrialActor(p.actor,p.trial),"stale cast must fail closed");
            require(p.teleports==0&&p.damageActions==0,"stale cast cannot relocate or damage");return;
        }
        require(p.tickNamedTrialActor(p.actor,p.trial),"blocked or invalid Step resolves safely");
        var after=p.checkpoint();
        require(after.attack()==Wave7NamedTrialActorPolicy.Attack.NONE
            &&after.cooldownUntilMillis()>System.currentTimeMillis()&&p.damageActions==0,
            "Step must finish into cooldown without blind damage");
        require(p.teleports==(mode.equals("safe-step")?1:0),"only legal clear Step may teleport");
        require(p.tickNamedTrialActor(p.actor,p.trial),"cooldown tick");
        require(p.checkpoint().attackIndex()==after.attackIndex()&&p.checkpoint().attack()==after.attack(),
            "Step cooldown prevents immediate repeat");
    }
}
'''
    probe = out / "OccludedArchmageProbe.java"
    probe.write_text(code, encoding="utf-8")
    fixture = out / "Wave7AdmissionRuntimeChecks.java"
    fixture.write_text(HARNESS, encoding="utf-8")
    files = [str(BASE / rel) for rel in PRODUCTION]
    files += [str(BASE / "domain/Wave7NamedTrialActorPolicy.java"),
             # V5.1: the Archmage rotation now delegates its circle/crown/repulse
             # contracts to this pure policy, so the probe compiles the real one.
             str(BASE / "domain/ArchmageV5CombatPolicy.java"),
             # MARK-005/006: the checkpoint now derives the vanish cooldown from
             # the persisted hide deadline, so the policy must be on the probe's
             # classpath rather than stubbed away.
             str(BASE / "domain/Wave7MarksmanInvisibilityPolicy.java"),
             str(BASE / "domain/Wave7MinibossScalingPolicy.java"),
             # MAGE7-005: the tick path now reads and writes the punish window,
             # so the probe compiles the real policy instead of stubbing it.
             str(BASE / "domain/Wave7ArchmagePunishPolicy.java"),
             # Miniboss health scales from the roster frozen at admission.
             str(BASE / "runtime/EventCombatProfileService.java"),
             # The service owns a durable ordered combat history (audit ECHO 10).
             str(BASE / "runtime/CombatSequence.java"),
             str(BASE / "domain/wave7/EchoBehaviorStrategy.java"),
             # Party-sweeping target selection for Wave 7 minibosses.
             str(BASE / "domain/Wave7TrialTargetPolicy.java"),
             str(fixture), str(probe)]
    compiled = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(out), *files],
        capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=30,
    )
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    return out


@pytest.mark.parametrize("scenario", [
    "occluded-ordinal-0", "occluded-ordinal-1", "occluded-ordinal-2", "occluded-ordinal-3",
    "safe-step", "blocked-path", "blocked-destination", "unsafe-floor", "teleport-cancelled",
    "target-invalid", "target-quit", "stale-generation", "stale-checkpoint",
    "cross-room-only", "connected-other-room", "same-room-with-nearer-other-room",
    "no-target", "cooldown", "marksman-occluded", "visible-normal",
])
def test_archmage_repositions_under_cover_without_bypassing_admission(occluded_archmage_probe, scenario):
    result = subprocess.run(
        ["java", "-cp", str(occluded_archmage_probe), "OccludedArchmageProbe", scenario],
        capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=15,
    )
    assert result.returncode == 0, result.stdout + result.stderr
