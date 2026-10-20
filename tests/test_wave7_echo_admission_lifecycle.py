"""Execute current context/cleanup methods instead of claiming wiring from names."""
from pathlib import Path
import subprocess

import pytest
from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "copimine-end-event/src/me/copimine/endevent"


@pytest.fixture(scope="module")
def lifecycle(tmp_path_factory):
    directory = tmp_path_factory.mktemp("echo-admission-lifecycle")
    source = (BASE / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    callbacks = "\n".join(extract(source, signature) for signature in (
        "EchoCombatAdmission.Context currentEchoCombatContext()",
        "void tickEchoCombatSources()", "boolean clearEchoPresentationProbe()", "void beginStartRitualIfReady()",
    ))
    run_signature = "boolean mayRunEchoPresentationProbe()"
    callbacks += "\n" + (extract(source, run_signature) if run_signature in source else "private boolean mayRunEchoPresentationProbe(){return true;}")
    harness = directory / "EchoAdmissionLifecycle.java"
    harness.write_text(r'''
import java.util.*;import java.util.logging.Level;
import me.copimine.endevent.domain.wave7.EchoCombatAdmission;
import me.copimine.endevent.domain.EventPhase;
public class EchoAdmissionLifecycle {
 static UUID OWNER=new UUID(0,1),ACTOR=new UUID(0,2),EVENT=new UUID(0,3);
 static class Player {}
 static class Bukkit {static Player owner=new Player();static Player getPlayer(UUID id){return id.equals(OWNER)?owner:null;}}
 static class Entity {UUID getUniqueId(){return ACTOR;}}
 static class Probe {boolean live=true,closed;int closeCalls;UUID owner(){return OWNER;}Entity carrier(){return new Entity();}
  boolean activeForCombat(Player p,UUID event,long gen,long tick,boolean capable){return live&&p==Bukkit.owner&&event.equals(EVENT)&&gen==7&&tick==110&&capable;}
  void close(long tick){closeCalls++;closed=true;}
 }
 static class Sources {boolean failClear,failTick;int clearCalls,tickCalls;
  void clear(){clearCalls++;if(failClear){failClear=false;throw new IllegalStateException("retry clear");}}
  void tick(){tickCalls++;if(failTick){failTick=false;throw new IllegalStateException("retry tick");}}
 }
 Probe echoPresentationProbe=new Probe();Sources echoCombatAdmissionListener=new Sources();boolean echoProbeCombatClosing;
 EchoCombatAdmission.Pair echoProbeCombatPair=new EchoCombatAdmission.Pair(EVENT,7,10,new UUID(0,4),OWNER,ACTOR,new UUID(0,5));
 String eventId=EVENT.toString();long generation=7,eventTickCounter=110;int unregistered;
 EventPhase phase=EventPhase.READY_FOR_PLAYERS;boolean bootstrapped=true,testCombatAiMode,testWaveFrontVisualMode;
 int activeWave,requiredPlayers=2,transitions,resets;long phaseDeadlineMillis;Object creativeTestTask;
 Map<String,UUID> padOccupants=new HashMap<>(Map.of("one",OWNER,"two",ACTOR));Map<String,String> transitionRuneRenderStates=new HashMap<>();
 class Config {int startRitualTimeoutSeconds(){return 60;}}Config config=new Config();
 class RuneController {void reset(){resets++;}}RuneController transitionRuneController=new RuneController();
 boolean isOfficialAttemptActive(){return phase==EventPhase.START_RITUAL;}
 boolean transition(EventPhase next,String reason,String receipt){transitions++;phase=next;return true;}
 Set<UUID> echoPresentationCapablePlayers=new HashSet<>(Set.of(OWNER));
 UUID parseUuidOrNull(String s){return UUID.fromString(s);}void unregisterOwnedEntity(UUID id,String reason){unregistered++;}
 java.util.logging.Logger getLogger(){return java.util.logging.Logger.getAnonymousLogger();}
''' + callbacks + r'''
 static void require(boolean value,String why){if(!value)throw new AssertionError(why);}
 public static void main(String[] args){var a=new EchoAdmissionLifecycle();var pair=a.echoProbeCombatPair;var probe=a.echoPresentationProbe;var sources=a.echoCombatAdmissionListener;
  switch(args[0]){
   case "active":{var c=a.currentEchoCombatContext();require(c.pair()==pair&&c.tick()==110&&c.active(),"context identities replaced or owner liveness ignored");break;}
   case "stale-generation":a.generation=8;require(!a.currentEchoCombatContext().active(),"stale generation active");break;
   case "capability":a.echoPresentationCapablePlayers.clear();require(!a.currentEchoCombatContext().active(),"missing native capability active");break;
   case "closing":a.echoProbeCombatClosing=true;require(!a.currentEchoCombatContext().active(),"failed cleanup still authorizes combat");break;
   case "close":require(a.clearEchoPresentationProbe()&&probe.closed&&sources.clearCalls==1&&a.unregistered==1&&a.currentEchoCombatContext()==null,"close did not clear all current pair resources");break;
   case "retry":sources.failClear=true;require(!a.clearEchoPresentationProbe()&&a.echoPresentationProbe==probe&&a.echoProbeCombatPair==pair&&!a.currentEchoCombatContext().active(),"cleanup failure lost retry identity or stayed hostile");
    require(a.clearEchoPresentationProbe()&&probe.closeCalls==1&&sources.clearCalls==2&&a.unregistered==1,"retry did not finish exactly one carrier cleanup");break;
   case "tick-failure":sources.failTick=true;a.tickEchoCombatSources();require(probe.closed&&a.currentEchoCombatContext()==null&&sources.clearCalls==1,"tick removal failure escaped without closing the scene");break;
   case "empty":a.echoPresentationProbe=null;a.echoProbeCombatPair=null;require(a.currentEchoCombatContext()==null&&a.clearEchoPresentationProbe()&&sources.clearCalls==1,"empty scene skipped remaining source cleanup");break;
   case "official-fence":a.phase=EventPhase.START_RITUAL;require(!a.currentEchoCombatContext().active(),"local pair admitted combat after official ritual began");break;
   case "other-probe-fence":a.activeWave=6;require(!a.currentEchoCombatContext().active(),"local pair admitted combat alongside another wave probe");break;
   case "ritual-start":a.beginStartRitualIfReady();require(a.phase==EventPhase.START_RITUAL&&probe.closed&&a.currentEchoCombatContext()==null&&sources.clearCalls==1&&a.transitions==1,"official ritual started with local pair sources alive");break;
   case "ritual-cleanup-failure":sources.failClear=true;a.beginStartRitualIfReady();require(a.phase==EventPhase.READY_FOR_PLAYERS&&a.transitions==0&&a.phaseDeadlineMillis==0&&a.resets==0&&!a.currentEchoCombatContext().active(),"failed local cleanup still committed the official ritual");break;
   case "ritual-retry":sources.failClear=true;a.beginStartRitualIfReady();a.beginStartRitualIfReady();require(a.phase==EventPhase.START_RITUAL&&a.transitions==1&&probe.closeCalls==1&&sources.clearCalls==2,"ritual retry leaked sources or repeated the start transaction");break;
   default:throw new AssertionError(args[0]);
  }
 }
}
''', encoding="utf-8")
    compiled = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(directory), str(BASE / "domain/wave7/EchoCombatAdmission.java"), str(BASE / "domain/EventPhase.java"), str(harness)], capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    return directory


@pytest.mark.parametrize("scenario", ["active", "stale-generation", "capability", "closing", "close", "retry", "tick-failure", "empty", "official-fence", "other-probe-fence", "ritual-start", "ritual-cleanup-failure", "ritual-retry"])
def test_current_context_and_retryable_cleanup(lifecycle, scenario):
    result = subprocess.run(["java", "-cp", str(lifecycle), "EchoAdmissionLifecycle", scenario], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
