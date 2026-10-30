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
        "private boolean tickNamedTrialActor(",
        "private Player closestRealitySplitTrialTarget(",
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
        "private boolean resolveNamedTrialAttack(",
        "private boolean stepNamedTrialArchmage(",
    )
    methods = "\n".join(extract_method(source, signature) for signature in signatures)
    code = r'''
import java.util.*;
import me.copimine.endevent.domain.*;
import me.copimine.endevent.runtime.*;
import me.copimine.endevent.runtime.wave7.Wave7AdmissionRuntimeAdapter.Decision;

public class OccludedArchmageProbe {
    long generation=Wave7AdmissionRuntimeChecks.GEN, eventTickCounter=1L;
    final String eventId=Wave7AdmissionRuntimeChecks.EVENT.toString();
    final RealitySplitChamberController realitySplitChamberController=Wave7AdmissionRuntimeChecks.chambers;
    final RealitySplitTrialController realitySplitTrialController=Wave7AdmissionRuntimeChecks.trials;
    final Set<UUID> realitySplitTrialEntityUuids=new HashSet<>();
    record NamespacedKey(Object owner,String name) {}
    final NamespacedKey keyChamberId=new NamespacedKey(this,"room"),keyTrialRole=new NamespacedKey(this,"role"),
        keyWave=new NamespacedKey(this,"wave"),keyGeneration=new NamespacedKey(this,"generation"),
        keyEventGeneration=new NamespacedKey(this,"event_generation"),keyEventId=new NamespacedKey(this,"event"),
        keyEventSessionId=new NamespacedKey(this,"session");
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
    }
    enum EntityType { EVOKER, SKELETON }
    enum Particle { PORTAL, HAPPY_VILLAGER }
    enum Sound { ENTITY_ENDERMAN_TELEPORT }
    static class Vector {
        double x,y,z;Vector(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
        double getX(){return x;}double getZ(){return z;}
        public Vector clone(){return new Vector(x,y,z);}Vector multiply(double m){x*=m;y*=m;z*=m;return this;}
    }
    static class Location {
        double x,y,z;Location(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
        public Location clone(){return new Location(x,y,z);}
        Location add(Vector v){return add(v.x,v.y,v.z);}
        Location add(double x,double y,double z){this.x+=x;this.y+=y;this.z+=z;return this;}
        double getY(){return y;}void setY(double y){this.y=y;}
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
        Location getEyeLocation(){return location.clone().add(0,1.6,0);}
        double getEyeHeight(){return 1.6;}World getWorld(){return world;}
    }
    static class Player extends LivingEntity {
        boolean online=true;
        Player(UUID id){super(id,EntityType.EVOKER,new Location(5,70,0));}
    }
    static class Bukkit {
        static final List<Player> players=new ArrayList<>();
        static Collection<Player> getOnlinePlayers(){return players;}
        static Player getPlayer(UUID id){return players.stream().filter(p->p.online&&p.id.equals(id)).findFirst().orElse(null);}
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
    void moveNamedTrialMarksman(LivingEntity actor,int chamber){}
    void renderNamedTrialTelegraph(LivingEntity a,Player p,Wave7NamedTrialActorPolicy.Attack attack,boolean sound){warnings++;}
    boolean hasNamedTrialLineOfSight(Location from,Location to){return Math.abs(to.z)<0.01?visible:pathClear;}
    Vector horizontalDirection(Location from,Location to){return new Vector(1,0,0);}
    boolean isRealitySplitDestinationAllowed(UUID target,Location to,ChamberIsolationPolicy.Assignment assignment){
        return destinationAllowed&&realitySplitChamberController.allowsPlayerInChamber(generation,target,trial.chamber());}
    boolean isSafeCombatEntityLocation(Location to,LivingEntity source){return safeFloor;}
    boolean teleportCombatEntity(Entity e,Location to){if(!teleportAllowed)return false;e.location=to.clone();teleports++;return true;}
    void spawnEventParticle(Location at,Particle particle,int count,double x,double y,double z,double speed){}
    boolean launchNamedTrialArrow(LivingEntity a,Player p,Wave7NamedTrialActorPolicy.Attack attack,float speed,float spread,boolean glow,boolean spear){damageActions++;return true;}
    boolean spawnNamedTrialFangs(LivingEntity a,Player p,Wave7NamedTrialActorPolicy.Attack attack){damageActions++;return true;}
    void resolveNamedTrialBeam(LivingEntity a,Player p,int chamber){damageActions++;}

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
    void expireWarning(){
        var c=checkpoint();
        require(writeNamedTrialActorCheckpoint(actor,new Wave7NamedTrialActorPolicy.Checkpoint(
            c.eventId(),c.generation(),c.chamber(),c.trial(),c.actorId(),c.attackIndex(),
            c.attack(),1L,c.cooldownUntilMillis(),c.targetId(),c.supportUsed())),"expire warning");
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
    files += [str(BASE / "domain/Wave7NamedTrialActorPolicy.java"), str(fixture), str(probe)]
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
