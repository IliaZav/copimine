"""Execute the actual startup adapter; malformed trials must leave recovery usable."""
from pathlib import Path
import subprocess

from tests.test_wave_navigation_adapter import extract

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent"


def test_invalid_trial_checkpoint_enters_recovery_and_valid_legacy_still_resumes(tmp_path):
    source = (SOURCE / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    signature = "void restoreWave7Checkpoint(EventSnapshot snapshot, boolean persistedDisposableWave7)"
    if "    private " + signature in source:
        adapter = extract(source, signature)
    else:
        # The old production branch was inline in applySnapshot. Preserve its
        # exact body so RED exercises its exception/partial-publication failure.
        start = source.index("            RealitySplitChamberSnapshot.Data wave7 = persistedDisposableWave7")
        end = source.index("\n        worldName = snapshot.worldName();", start)
        body = source[start:end].rsplit("\n        }", 1)[0]
        adapter = "private " + signature + " {\n" + body + "\n}"
    probe = tmp_path / "Wave7CheckpointProbe.java"
    probe.write_text('''
import java.util.*;
import java.util.logging.*;
import me.copimine.endevent.domain.*;
import me.copimine.endevent.runtime.*;
public class Wave7CheckpointProbe {
    long generation = 73;
    String eventId = "synthetic-event", recoveryReason = "";
    EventPhase phase = EventPhase.WAVE_7;
    int activeWave = 7;
    boolean testWaveFrontVisualMode, finalSealBarrierHold = true;
    Set<UUID> sandboxWaveRoster = Set.of();
    RealitySplitChamberController realitySplitChamberController = new RealitySplitChamberController();
    RealitySplitTrialController realitySplitTrialController = new RealitySplitTrialController();
    Controller encounterController = new Controller();
    static class Controller { EventPhase phase; void restore(String id,long gen,EventPhase phase,Object unused){this.phase=phase;} }
    enum EventPhase { WAVE_7, READY_FOR_PLAYERS, RECOVERY_REQUIRED }
    record EventSnapshot(Map<String,String> objectiveProgress) { }
    Logger getLogger(){return Logger.getAnonymousLogger();}
''' + adapter + '''
    static void require(boolean value, String text) { if(!value) throw new AssertionError(text); }
    public static void main(String[] args) {
        var players = List.of(new UUID(0,1),new UUID(0,2));
        var assignment = ChamberIsolationPolicy.assign(players);
        var state = new LinkedHashMap<>(RealitySplitChamberSnapshot.encode(73,assignment,Set.of(0),Set.of()));
        var trials = new RealitySplitTrialController();
        trials.begin(73,assignment);
        trials.defeatWarden(73,0);
        state.putAll(RealitySplitTrialSnapshot.encode(73,trials.snapshot()));
        var valid = new Wave7CheckpointProbe();
        valid.restoreWave7Checkpoint(new EventSnapshot(Map.copyOf(state)),false);
        require(valid.phase==EventPhase.WAVE_7 && valid.realitySplitTrialController.trial(0).stage()==RealitySplitTrialController.Stage.COMPLETE,
                "valid legacy checkpoint keeps its completed trial and phase");
        state.put("reality-split-trial.room.1","RIFT_REFLECTION:ACTIVE:99:3");
        var malformed = new Wave7CheckpointProbe();
        try {
            malformed.restoreWave7Checkpoint(new EventSnapshot(Map.copyOf(state)),false);
        } catch (RuntimeException error) {
            throw new AssertionError("invalid trial checkpoint must enter explicit recovery, not disable startup",error);
        }
        require(malformed.phase==EventPhase.RECOVERY_REQUIRED && malformed.encounterController.phase==EventPhase.RECOVERY_REQUIRED,
                "invalid progress must enter authoritative recovery");
        require(malformed.activeWave==0 && !malformed.finalSealBarrierHold && !malformed.testWaveFrontVisualMode,
                "incompatible checkpoint must not restart attacks or hold retired partitions");
        require(!malformed.realitySplitChamberController.owns(73) && !malformed.realitySplitTrialController.owns(73),
                "recovery cannot publish partially restored rooms or trial progress");
        require(!malformed.recoveryReason.isBlank(),"recovery exposes its reason");
        var disposable = new Wave7CheckpointProbe();
        disposable.testWaveFrontVisualMode=true;
        disposable.restoreWave7Checkpoint(new EventSnapshot(Map.copyOf(state)),true);
        require(disposable.phase==EventPhase.RECOVERY_REQUIRED && !disposable.testWaveFrontVisualMode,
                "disposable checkpoints use the same failure policy");
        state.put("reality-split-trial.room.1","RIFT_REFLECTION:ACTIVE:0:3");
        state.put("reality-split-trial.room.0","WARDEN:ACTIVE:0:1");
        var inconsistent = new Wave7CheckpointProbe();
        inconsistent.restoreWave7Checkpoint(new EventSnapshot(Map.copyOf(state)),false);
        require(inconsistent.phase==EventPhase.RECOVERY_REQUIRED,
                "completed passage graph and active trial conflict must enter recovery");
        require(!inconsistent.realitySplitChamberController.owns(73),
                "conflicting completion receipts cannot publish a cleared room");
    }
}
''', encoding="utf-8")
    inputs = [SOURCE / item for item in (
        "domain/ChamberIsolationPolicy.java", "domain/RealitySplitBarrierPolicy.java",
        "domain/RealitySplitChamberSnapshot.java", "runtime/RealitySplitChamberController.java",
        "runtime/RealitySplitTrialController.java", "runtime/RealitySplitTrialSnapshot.java",
    )]
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), *map(str, inputs), str(probe)],
                   check=True, capture_output=True, text=True)
    result = subprocess.run(["java", "-cp", str(tmp_path), "Wave7CheckpointProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
