"""Execute the actual startup adapter; malformed trials must leave recovery usable."""
from pathlib import Path
import subprocess

from tests.test_wave_navigation_adapter import extract

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent"


def test_named_checkpoint_resumes_and_legacy_or_malformed_trial_checkpoint_requires_recovery(tmp_path):
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
    String eventId = "00000000-0000-0000-0000-000000000073", recoveryReason = "";
    EventPhase phase = EventPhase.WAVE_7;
    int activeWave = 7;
    boolean testWaveFrontVisualMode, finalSealBarrierHold = true;
    Set<UUID> sandboxWaveRoster = Set.of();
    Map<String,String> wave7RecoveryCheckpoint = Map.of();
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
        var legacy = new Wave7CheckpointProbe();
        legacy.restoreWave7Checkpoint(new EventSnapshot(Map.copyOf(state)),false);
        require(legacy.phase==EventPhase.RECOVERY_REQUIRED && !legacy.realitySplitTrialController.owns(73),
                "legacy checkpoint cannot become the new approved official trials");
        var named = new RealitySplitTrialController();
        named.beginNamed(UUID.fromString(legacy.eventId),73,assignment,List.of(
                RealitySplitTrialController.NamedTrial.WARDEN,RealitySplitTrialController.NamedTrial.MOBILE_MARKSMAN));
        named.completeNamedActor(UUID.fromString(legacy.eventId),73,0,RealitySplitTrialController.NamedTrial.WARDEN);
        state = new LinkedHashMap<>(RealitySplitChamberSnapshot.encode(73,assignment,Set.of(0),Set.of()));
        state.putAll(RealitySplitTrialSnapshot.encodeNamed(named.namedSnapshot()));
        var valid = new Wave7CheckpointProbe();
        valid.restoreWave7Checkpoint(new EventSnapshot(Map.copyOf(state)),false);
        require(valid.phase==EventPhase.WAVE_7 && valid.realitySplitTrialController.namedTrial(0).stage()==RealitySplitTrialController.Stage.COMPLETE,
                "valid named checkpoint keeps its sampled layout and completed trial");
        // Legacy schema 1-3 migration emits a current snapshot with WAVE_7
        // intact but no named-trial progress. It must not resume as a fresh
        // empty encounter merely because the absent checkpoint decodes empty.
        for (int legacySchema = 1; legacySchema <= 3; legacySchema++) {
            var missingLegacyProgress = new Wave7CheckpointProbe();
            missingLegacyProgress.restoreWave7Checkpoint(new EventSnapshot(Map.of()),false);
            require(missingLegacyProgress.phase==EventPhase.RECOVERY_REQUIRED
                            && missingLegacyProgress.encounterController.phase==EventPhase.RECOVERY_REQUIRED,
                    "legacy schema " + legacySchema + " WAVE_7 without named-trial progress must enter recovery");
            require(missingLegacyProgress.activeWave==0 && !missingLegacyProgress.finalSealBarrierHold
                            && !missingLegacyProgress.testWaveFrontVisualMode,
                    "legacy schema " + legacySchema + " checkpoint cannot restart stale Wave 7 state");
        }
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
        "domain/RealitySplitChamberSnapshot.java",
        "runtime/RealitySplitChamberController.java",
        "runtime/RealitySplitTrialController.java", "runtime/RealitySplitTrialSnapshot.java",
    )]
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), *map(str, inputs), str(probe)],
                   check=True, capture_output=True, text=True)
    result = subprocess.run(["java", "-cp", str(tmp_path), "Wave7CheckpointProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
