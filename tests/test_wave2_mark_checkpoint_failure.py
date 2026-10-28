"""An uncommitted 45-second mark must not authorize further Wave 2 actions."""
from pathlib import Path
import subprocess

from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]


def test_mark_period_save_failure_stops_the_production_tick(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    method = extract(source, "void tickCurrentHuntObjective(long now)")
    probe = tmp_path / "WaveTwoMarkCheckpointProbe.java"
    probe.write_text(r'''
import java.util.*;import java.util.logging.Logger;
import me.copimine.endevent.domain.*;
public class WaveTwoMarkCheckpointProbe {
    enum EventPhase {WAVE_2,RECOVERY_REQUIRED}
    static class Player {}
    static class World {}
    static class Location {World getWorld(){return new World();}}
    static class Bukkit {static Player getPlayer(UUID id){return new Player();}}
    int waveSpawnGroupIndex,currentHuntCycles,waveTwoCompletedMarkPeriods=1,waveTwoLastLateGroupMarkPeriod;
    long waveTwoGroupClearedAtMillis,waveTwoNextMarkMillis,waveTwoMarkDeadlineMillis=45_000;
    UUID waveTwoMarkedPlayerUuid=UUID.randomUUID();boolean waveTwoGroupCleared,official=true,saveSucceeds;
    final Set<UUID> waveTwoOutstandingEntityUuids=new HashSet<>();
    final List<Integer> waveSpawnSchedule=List.of(1);
    final String eventId="event";int newMarks,saves,clears;EventPhase phase=EventPhase.WAVE_2;
    boolean isActiveArenaParticipant(Player p){return p!=null;}
    boolean isOfficialCurrentAttempt(){return official;}
    boolean saveStateSync(){saves++;return saveSucceeds;}
    void clearCurrentHuntMark(long now){clears++;waveTwoMarkedPlayerUuid=null;}
    void updateMarkedTargetObjective(long now){newMarks++;}
    void forcePhase(EventPhase next,String reason){phase=next;}
    boolean checkpointWaveTwoGroupClear(){return true;}
    void spawnNextWaveGroup(World w,Location l){}
    Location coreCombatAnchorLocation(){return new Location();}
    void playWaveFeedback(String key,Location where){}
    Logger getLogger(){return Logger.getLogger("mark-checkpoint");}
''' + method + r'''
    static void check(boolean b,String reason){if(!b)throw new AssertionError(reason);}
    public static void main(String[] args){
        var failed=new WaveTwoMarkCheckpointProbe();failed.tickCurrentHuntObjective(45_000);
        check(failed.saves==1,"official completed period must attempt a durable checkpoint");
        check(failed.phase==EventPhase.RECOVERY_REQUIRED,"save failure must fail closed instead of publishing uncommitted progress");
        check(failed.newMarks==0,"save failure must stop the tick before another mark is assigned");
        var passed=new WaveTwoMarkCheckpointProbe();passed.saveSucceeds=true;passed.tickCurrentHuntObjective(45_000);
        check(passed.phase==EventPhase.WAVE_2&&passed.newMarks==1&&passed.waveTwoCompletedMarkPeriods==2,"durable completed period keeps normal progression");
        var diagnostic=new WaveTwoMarkCheckpointProbe();diagnostic.official=false;diagnostic.tickCurrentHuntObjective(45_000);
        check(diagnostic.saves==0&&diagnostic.newMarks==1,"disposable probe must not require official persistence");
        var early=new WaveTwoMarkCheckpointProbe();early.tickCurrentHuntObjective(44_999);
        check(early.saves==0&&early.waveTwoCompletedMarkPeriods==1,"44.999 seconds is not a complete mark period");
    }
}
''', encoding="utf-8")
    compiled = subprocess.run(["javac", "-encoding", "UTF-8", "-sourcepath", str(ROOT / "copimine-end-event/src"), "-d", str(tmp_path), str(probe)], capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    run = subprocess.run(["java", "-cp", str(tmp_path), "WaveTwoMarkCheckpointProbe"], capture_output=True, text=True)
    assert run.returncode == 0, run.stdout + run.stderr
