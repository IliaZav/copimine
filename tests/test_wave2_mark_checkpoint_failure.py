"""A cleared Wave 2 subwave must be pinned durably before the next one starts."""
from pathlib import Path
import subprocess

from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]


def test_subwave_clear_checkpoint_failure_stops_the_production_tick(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    method = extract(source, "void tickCurrentHuntObjective(long now)")
    probe = tmp_path / "WaveTwoClearCheckpointProbe.java"
    probe.write_text(r'''
import java.util.*;import java.util.logging.Logger;
import me.copimine.endevent.domain.*;
public class WaveTwoClearCheckpointProbe {
    enum EventPhase {WAVE_2,RECOVERY_REQUIRED}
    static class World {}
    static class Location {World getWorld(){return new World();}}
    int waveSpawnGroupIndex=1,currentHuntCycles,waveTwoGroupsSpawned=1,newMarks,spawns,saves;
    // This fixture starts after the opening, with combat group one already spawned.
    long waveTwoGroupClearedAtMillis, waveTwoIntroStartedMillis=1L;
    static class Config { Object phaseMusic(String phase){return phase;} }
    Config config=new Config();void playEventMusic(Object track){}
    boolean waveTwoGroupCleared,official=true,saveSucceeds;
    final Set<UUID> waveTwoOutstandingEntityUuids=new HashSet<>();
    final List<WaveMechanicsPolicy.WaveCounts> waveSpawnSchedule=List.of(
            new WaveMechanicsPolicy.WaveCounts(1,0,0,0,0),
            new WaveMechanicsPolicy.WaveCounts(1,0,0,0,0),
            new WaveMechanicsPolicy.WaveCounts(1,0,0,0,0));
    final String eventId="event";EventPhase phase=EventPhase.WAVE_2;
    boolean isOfficialCurrentAttempt(){return official;}
    boolean saveStateSync(){saves++;return saveSucceeds;}
    boolean checkpointWaveTwoGroupClear(){
        if(!official||saveStateSync())return true;
        phase=EventPhase.RECOVERY_REQUIRED;
        return false;
    }
    boolean spawnNextWaveGroup(World w,Location l){spawns++;waveSpawnGroupIndex++;return true;}
    void updateMarkedTargetObjective(long now){newMarks++;}
    // The hunt tick also drains an owed phantom activation; this probe only
    // exercises the checkpoint gate, so the retry is inert here.
    void retryPendingWaveTwoPhantomActivation(long now){}
    void forcePhase(EventPhase next,String reason){phase=next;}
    Location coreCombatAnchorLocation(){return new Location();}
    void playWaveFeedback(String key,Location where){}
    Logger getLogger(){return Logger.getLogger("clear-checkpoint");}
''' + method + r'''
    static void check(boolean b,String reason){if(!b)throw new AssertionError(reason);}
    public static void main(String[] args){
        var failed=new WaveTwoClearCheckpointProbe();
        failed.tickCurrentHuntObjective(11_000L);
        check(failed.saves==1,"an official cleared subwave must attempt one durable checkpoint");
        check(failed.phase==EventPhase.RECOVERY_REQUIRED,
                "a failed clear checkpoint must fail closed instead of publishing uncommitted progress");
        check(failed.newMarks==0&&failed.spawns==0,
                "the tick must stop before the next subwave or another mark packet");
        check(failed.currentHuntCycles==1,"the clear itself is still counted for the log line");
        var passed=new WaveTwoClearCheckpointProbe();passed.saveSucceeds=true;
        passed.tickCurrentHuntObjective(11_000L);
        check(passed.phase==EventPhase.WAVE_2&&passed.newMarks==1&&passed.currentHuntCycles==1,
                "a durable clear keeps normal progression and the collective mark");
        check(passed.spawns==0,"the next subwave waits for the three-second post-clear pause");
        passed.tickCurrentHuntObjective(14_000L);
        check(passed.spawns==1&&passed.waveSpawnGroupIndex==2,
                "the follow-up tick starts the next subwave once the pause elapses");
        var diagnostic=new WaveTwoClearCheckpointProbe();diagnostic.official=false;
        diagnostic.tickCurrentHuntObjective(11_000L);
        check(diagnostic.saves==0&&diagnostic.newMarks==1,
                "a disposable probe must not require official persistence");
        var unloaded=new WaveTwoClearCheckpointProbe();
        unloaded.waveTwoOutstandingEntityUuids.add(UUID.randomUUID());
        unloaded.tickCurrentHuntObjective(11_000L);
        check(unloaded.saves==0&&unloaded.currentHuntCycles==0&&unloaded.newMarks==1,
                "an outstanding fighter cannot be checkpointed as a cleared subwave");
    }
}
''', encoding="utf-8")
    compiled = subprocess.run(["javac", "-encoding", "UTF-8", "-sourcepath", str(ROOT / "copimine-end-event/src"), "-d", str(tmp_path), str(probe)], capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    run = subprocess.run(["java", "-cp", str(tmp_path), "WaveTwoClearCheckpointProbe"], capture_output=True, text=True)
    assert run.returncode == 0, run.stdout + run.stderr
