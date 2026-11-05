"""Execute Wave 1's real reinforcement resolver in official and test modes."""
from pathlib import Path
import subprocess
from tests.test_wave_navigation_adapter import extract

ROOT = Path(__file__).resolve().parents[1]


def test_reinforcement_resolver_runs_only_for_current_wave_and_preserves_debt(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    resolver = extract(source, "void resolveWaveOneReinforcements(long now)")
    objective = extract(source, "void tickCurrentCarrierObjective(long now)")
    assert "resolveWaveOneReinforcements(now)" in objective
    java = tmp_path / "WaveOneReinforcementProbe.java"
    java.write_text(r'''
import java.util.*; import java.util.logging.*;
public class WaveOneReinforcementProbe {
 enum EventPhase { WAVE_1, WAVE_2, READY_FOR_PLAYERS }
 EventPhase phase=EventPhase.WAVE_1; int activeWave=1, wave1ReinforcementOwed=1,wave1ReinforcementSpawned,live;
 boolean testWaveFrontVisualMode; long wave1NextReinforcementAtMillis,generation=3; int spawnLimit=3;
 Object keyWave=new Object(); Map<UUID,Entity> ownedEntities=new LinkedHashMap<>();
 static class Entity {UUID id=UUID.randomUUID();}
 long WAVE6_REINFORCEMENT_RETRY_MILLIS=100; String eventId="event";
 static class PressureBudgetController { static int HARD_CAP=20; }
 static class WaveMechanicsPolicy { record WaveCounts(int total){} }
 static class World{} static class Location { World getWorld(){return new World();} }
 Location coreCombatAnchorLocation(){return new Location();} int waveScalePlayers(){return 2;}
 WaveMechanicsPolicy.WaveCounts ritualPressureCounts(int p){return new WaveMechanicsPolicy.WaveCounts(3);}
 WaveMechanicsPolicy.WaveCounts waveOneReinforcementCounts(int p){return new WaveMechanicsPolicy.WaveCounts(3);}
 int countLiveWaveEntitiesForWave(int w){return live;}
 int readInt(Entity e,Object key,int fallback){return 1;}
 void removeRitualEntity(UUID id){if(ownedEntities.remove(id)!=null)live--;}
 void spawnWaveGroup(World w,Location c,int wave,boolean guardian,boolean test,WaveMechanicsPolicy.WaveCounts group,int offset){
   for(int i=0;i<Math.min(spawnLimit,group.total());i++){var e=new Entity();ownedEntities.put(e.id,e);live++;}}
 void emitDiagnostic(Object... args){} Logger getLogger(){return Logger.getAnonymousLogger();} void saveStateAsync(){}
''' + resolver + r'''
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args){
   var p=new WaveOneReinforcementProbe();p.resolveWaveOneReinforcements(1);
   check(p.live==3&&p.wave1ReinforcementSpawned==1,"official wave must spawn the owed pack");
   p=new WaveOneReinforcementProbe();p.phase=EventPhase.READY_FOR_PLAYERS;p.testWaveFrontVisualMode=true;
   p.resolveWaveOneReinforcements(1);check(p.live==3&&p.wave1ReinforcementSpawned==1,"sandbox wave must spawn the owed pack");
   p=new WaveOneReinforcementProbe();p.phase=EventPhase.WAVE_2;p.activeWave=2;
   p.resolveWaveOneReinforcements(1);check(p.live==0&&p.wave1ReinforcementSpawned==0,"other wave must not consume debt");
   p=new WaveOneReinforcementProbe();p.live=18;p.resolveWaveOneReinforcements(1);
   check(p.live==18&&p.wave1ReinforcementSpawned==0,"insufficient cap must preserve debt for retry");
   p.live=0;p.resolveWaveOneReinforcements(102);check(p.live==3&&p.wave1ReinforcementSpawned==1,"debt retries after cap frees");
   p=new WaveOneReinforcementProbe();p.spawnLimit=1;p.resolveWaveOneReinforcements(1);
   check(p.live==0&&p.ownedEntities.isEmpty()&&p.wave1ReinforcementSpawned==0,"partial spawn must roll back and preserve debt");
   p.spawnLimit=3;p.resolveWaveOneReinforcements(102);
   check(p.live==3&&p.wave1ReinforcementSpawned==1,"complete retry must commit exactly once");
 }
}
''', encoding="utf-8")
    compiled = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(java)], capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    run = subprocess.run(["java", "-cp", str(tmp_path), "WaveOneReinforcementProbe"], capture_output=True, text=True)
    assert run.returncode == 0, run.stdout + run.stderr
