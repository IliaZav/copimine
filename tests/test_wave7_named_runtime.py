"""Execute official named-trial dispatch with unavailable actor implementations."""
from pathlib import Path
import subprocess

from tests.test_wave_navigation_adapter import extract

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent"


def test_selected_personal_echo_remains_deferred_and_start_fails_closed(tmp_path):
    source = (SOURCE / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    adapters = extract(source, "boolean startRealitySplitTrials(World world, Location core)")
    for signature in (
        "boolean startNamedRealitySplitTrials(World world, Location core)",
        "boolean namedRealitySplitTrialImplemented(RealitySplitTrialController.NamedTrial trial)",
    ):
        if "    private " + signature in source:
            adapters += extract(source, signature)
    probe = tmp_path / "NamedWave7StartProbe.java"
    probe.write_text('''
import java.util.*;
import java.util.logging.*;
import me.copimine.endevent.domain.*;
import me.copimine.endevent.runtime.*;
public class NamedWave7StartProbe {
    String eventId="00000000-0000-0000-0000-000000000071",recoveryReason="";
    long generation=71;
    boolean recovery;
    boolean namedWave7InitialActorCreation;
    int spawned;
    RealitySplitChamberController realitySplitChamberController=new RealitySplitChamberController();
    RealitySplitTrialController realitySplitTrialController=new RealitySplitTrialController();
    static class World {} static class Location {}
    enum EventPhase {RECOVERY_REQUIRED}
    Logger getLogger(){return Logger.getAnonymousLogger();}
    UUID parseUuidOrNull(String raw){return UUID.fromString(raw);}
    boolean isRealitySplitTrialRuntimeActive(){return !recovery;}
    boolean isOfficialCurrentAttempt(){return true;}
    void forcePhase(EventPhase phase,String reason){recovery=true;}
    void enterNamedRealitySplitRecovery(String reason){recoveryReason=reason;recovery=true;}
    void clearRealitySplitTrialEntities(String reason){}
    void finishRealitySplitTrial(int room,String source){}
    void finishNamedRealitySplitTrial(int room,String source){}
    boolean ensureRealitySplitTrialRoom(World world,Location core,RealitySplitTrialController.TrialState state){
        throw new AssertionError("new named trial entered a historical actor adapter");
    }
    boolean ensureNamedRealitySplitTrialRoom(World world,Location core,RealitySplitTrialController.NamedTrialState state){spawned++;return true;}
''' + adapters + '''
    static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    public static void main(String[] args){
        var probe=new NamedWave7StartProbe();
        probe.realitySplitChamberController.begin(71,List.of(new UUID(0,1),new UUID(0,2)));
        probe.realitySplitTrialController.beginNamed(UUID.fromString(probe.eventId),71,
                probe.realitySplitChamberController.assignment(),List.of(
                RealitySplitTrialController.NamedTrial.WARDEN,RealitySplitTrialController.NamedTrial.PERSONAL_ECHO));
        var before=probe.realitySplitTrialController.namedSnapshot();
        require(!probe.startRealitySplitTrials(new World(),new Location()),"unimplemented actor cannot report ready");
        require(probe.recovery&&probe.recoveryReason.contains("PERSONAL_ECHO"),
                "deferred personal Echo selection must enter explicit recovery");
        require(probe.spawned==0,"validate complete selected layout before spawning any actor");
        require(probe.realitySplitTrialController.namedSnapshot().equals(before),"failure cannot erase or reroll selected layout");
        require(!probe.realitySplitTrialController.allComplete(71),"missing actors cannot complete the layout");
    }
}
''', encoding="utf-8")
    paths = [SOURCE / item for item in (
        "domain/ChamberIsolationPolicy.java", "domain/RealitySplitBarrierPolicy.java",
        "domain/Wave7AdmissionPolicy.java",
        "runtime/RealitySplitChamberController.java", "runtime/RealitySplitTrialController.java",
    )]
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), *map(str, paths), str(probe)],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "NamedWave7StartProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr


def test_named_death_requires_new_actor_identity_and_selected_role(tmp_path):
    source = (SOURCE / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    adapters = extract(source, "void handleRealitySplitTrialBossDeath(Entity entity)")
    adapters += extract(source, "void handleNamedRealitySplitTrialBossDeath(Entity entity, int chamber, String role)")
    probe = tmp_path / "NamedWave7DeathProbe.java"
    probe.write_text('''
import java.util.*;
import java.util.logging.*;
import me.copimine.endevent.domain.*;
import me.copimine.endevent.runtime.*;
public class NamedWave7DeathProbe {
    String eventId="00000000-0000-0000-0000-000000000071";
    long generation=71;
    int finished,saves;
    Object keyChamberId=new Object(),keyTrialRole=new Object();
    RealitySplitTrialController realitySplitTrialController=new RealitySplitTrialController();
    Set<UUID> realitySplitTrialEntityUuids=new HashSet<>();
    Map<UUID,Object> realitySplitTrialRuntimeStates=new HashMap<>();
    static class NamespacedKey {NamespacedKey(Object plugin,String name){}}
    static class Entity {
        UUID id=UUID.randomUUID(),owner;int room;String role;boolean named;
        Entity(int room,String role,UUID owner,boolean named){this.room=room;this.role=role;this.owner=owner;this.named=named;}
        UUID getUniqueId(){return id;}
    }
    UUID parseUuidOrNull(String raw){try{return UUID.fromString(raw);}catch(RuntimeException ignored){return null;}}
    Logger getLogger(){return Logger.getAnonymousLogger();}
    boolean ownedBySession(Entity actor,String event,long gen){return true;}
    int readInt(Entity actor,Object key,int fallback){return actor.room;}
    String readString(Entity actor,Object key){return key==keyTrialRole?actor.role:actor.owner==null?"":actor.owner.toString();}
    boolean isNamedRealitySplitActor(Entity actor){return actor.named&&realitySplitTrialEntityUuids.contains(actor.id);}
    void finishRealitySplitTrial(int room,String reason){throw new AssertionError("named completion reached historical adapter");}
    void finishNamedRealitySplitTrial(int room,String reason){finished++;}
    boolean saveStateSync(){saves++;return true;}
    void enterNamedRealitySplitRecovery(String reason){throw new AssertionError(reason);}
    Entity actor(int room,String role,UUID owner,boolean named){var actor=new Entity(room,role,owner,named);realitySplitTrialEntityUuids.add(actor.id);return actor;}
''' + adapters + '''
    static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    public static void main(String[] args){
        var probe=new NamedWave7DeathProbe();
        var owners=java.util.stream.IntStream.range(1,7).mapToObj(i->new UUID(0,i)).toList();
        var assignment=ChamberIsolationPolicy.assign(owners);
        probe.realitySplitTrialController.beginNamed(UUID.fromString(probe.eventId),71,assignment,List.of(
                RealitySplitTrialController.NamedTrial.WARDEN,RealitySplitTrialController.NamedTrial.PERSONAL_ECHO,
                RealitySplitTrialController.NamedTrial.MOBILE_MARKSMAN,RealitySplitTrialController.NamedTrial.RIFT_ARCHMAGE));
        probe.handleRealitySplitTrialBossDeath(probe.actor(0,"WARDEN",null,false));
        require(probe.finished==0,"historical Warden death cannot resolve a new named trial");
        probe.handleRealitySplitTrialBossDeath(probe.actor(2,"JUGGERNAUT",null,true));
        require(probe.finished==0,"historical Juggernaut death cannot become a Marksman receipt");
        var warden=probe.actor(0,"WARDEN",null,true);
        probe.handleRealitySplitTrialBossDeath(warden);probe.handleRealitySplitTrialBossDeath(warden);
        require(probe.finished==1,"new registered Warden completes exactly once");
        require(probe.realitySplitTrialController.namedTrial(1).stage()
                        == RealitySplitTrialController.Stage.ACTIVE
                        && probe.realitySplitTrialController.namedTrial(1).echoReceipts().isEmpty(),
                "an unspawned deferred Echo remains active and has no completion receipt");
        require(!probe.realitySplitTrialController.allComplete(71),
                "unavailable named trials cannot authorize the Wave 7 handoff");
    }
}
''', encoding="utf-8")
    paths = [SOURCE / item for item in (
        "domain/ChamberIsolationPolicy.java", "runtime/RealitySplitTrialController.java",
    )]
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), *map(str, paths), str(probe)],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "NamedWave7DeathProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
