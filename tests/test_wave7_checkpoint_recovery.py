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
    adapter = "\n".join((
        adapter,
        extract(source, "boolean shouldRestoreWave7RecoveryCheckpoint(Map<String, String> objectiveProgress)"),
        extract(source, "boolean isWave7RecoveryCheckpointActive()"),
    ))
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
    boolean namedWave7RuntimeRestorePending;
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
        state = new LinkedHashMap<>(RealitySplitChamberSnapshot.encode(73,assignment,Set.of(0),Set.of(),
                Set.of(new RealitySplitChamberController.Passage(0,1))));
        state.putAll(RealitySplitTrialSnapshot.encodeNamed(named.namedSnapshot()));
        var valid = new Wave7CheckpointProbe();
        valid.restoreWave7Checkpoint(new EventSnapshot(Map.copyOf(state)),false);
        require(valid.phase==EventPhase.WAVE_7 && valid.realitySplitTrialController.namedTrial(0).stage()==RealitySplitTrialController.Stage.COMPLETE,
                "valid named checkpoint keeps its sampled layout and completed trial");
        require(valid.realitySplitChamberController.openingPassages().equals(
                        Set.of(new RealitySplitChamberController.Passage(0,1)))
                        && !valid.realitySplitChamberController.boundaryOpen(0,1),
                "a valid OPENING checkpoint restores intent without granting room access");
        var recovery = new Wave7CheckpointProbe();
        recovery.phase=EventPhase.RECOVERY_REQUIRED; recovery.activeWave=0; recovery.finalSealBarrierHold=false;
        recovery.wave7RecoveryCheckpoint=Map.copyOf(state);
        require(recovery.shouldRestoreWave7RecoveryCheckpoint(state),
                "a recovery snapshot with Wave 7 checkpoint data must be selected for strict restoration");
        recovery.restoreWave7Checkpoint(new EventSnapshot(Map.copyOf(state)),false);
        require(recovery.phase==EventPhase.RECOVERY_REQUIRED
                        && recovery.realitySplitChamberController.owns(73)
                        && recovery.isWave7RecoveryCheckpointActive()
                        && recovery.realitySplitChamberController.openingPassages().equals(
                            Set.of(new RealitySplitChamberController.Passage(0,1))),
                "restart recovery retains the valid OPENING checkpoint for a closed wall rebuild");
        require(!recovery.shouldRestoreWave7RecoveryCheckpoint(Map.of("wave2.cycles","1")),
                "unrelated recovery checkpoints must not be interpreted as Wave 7");
        var incompleteNamed = new RealitySplitTrialController();
        incompleteNamed.beginNamed(UUID.fromString("00000000-0000-0000-0000-000000000073"),73,assignment,List.of(
                RealitySplitTrialController.NamedTrial.WARDEN,RealitySplitTrialController.NamedTrial.MOBILE_MARKSMAN));
        var unauthorizedOpeningState = new LinkedHashMap<>(RealitySplitChamberSnapshot.encode(73,assignment,Set.of(),Set.of(),
                Set.of(new RealitySplitChamberController.Passage(0,1))));
        unauthorizedOpeningState.putAll(RealitySplitTrialSnapshot.encodeNamed(incompleteNamed.namedSnapshot()));
        var unauthorizedOpening = new Wave7CheckpointProbe();
        unauthorizedOpening.restoreWave7Checkpoint(new EventSnapshot(Map.copyOf(unauthorizedOpeningState)),false);
        require(unauthorizedOpening.phase==EventPhase.RECOVERY_REQUIRED
                        && !unauthorizedOpening.realitySplitChamberController.owns(73),
                "official OPENING intent without an adjacent completed room must fail closed");
        var unauthorizedOpenState = new LinkedHashMap<>(RealitySplitChamberSnapshot.encode(73,assignment,Set.of(),
                Set.of(new RealitySplitChamberController.Passage(0,1))));
        unauthorizedOpenState.putAll(RealitySplitTrialSnapshot.encodeNamed(incompleteNamed.namedSnapshot()));
        var unauthorizedOpen = new Wave7CheckpointProbe();
        unauthorizedOpen.restoreWave7Checkpoint(new EventSnapshot(Map.copyOf(unauthorizedOpenState)),false);
        require(unauthorizedOpen.phase==EventPhase.RECOVERY_REQUIRED
                        && !unauthorizedOpen.realitySplitChamberController.owns(73),
                "official OPEN passage without an adjacent completed room must fail closed");
        var finalSealState = new LinkedHashMap<>(RealitySplitChamberSnapshot.encode(73,assignment,Set.of(0,1),Set.of()));
        finalSealState.put("final-seal-wave","7");
        var finalSeal = new Wave7CheckpointProbe();
        finalSeal.restoreWave7Checkpoint(new EventSnapshot(Map.copyOf(finalSealState)),false);
        require(finalSeal.phase==EventPhase.WAVE_7 && finalSeal.realitySplitChamberController.owns(73)
                        &&finalSeal.realitySplitChamberController.completedChambers().equals(Set.of(0,1))
                        &&!finalSeal.realitySplitTrialController.owns(73),
                "completed final-seal checkpoint restores its rooms without inventing active trial state");
        var openingFinalSealState = new LinkedHashMap<>(finalSealState);
        openingFinalSealState.put("reality-split.opening", "0:1");
        var openingFinalSeal = new Wave7CheckpointProbe();
        openingFinalSeal.restoreWave7Checkpoint(new EventSnapshot(Map.copyOf(openingFinalSealState)),false);
        require(openingFinalSeal.phase==EventPhase.RECOVERY_REQUIRED,
                "final-seal checkpoint cannot replay a Wave 7 opening intent into its contained room");
        var incompleteFinalSealState = new LinkedHashMap<>(RealitySplitChamberSnapshot.encode(73,assignment,Set.of(0),Set.of()));
        incompleteFinalSealState.put("final-seal-wave","7");
        var incompleteFinalSeal = new Wave7CheckpointProbe();
        incompleteFinalSeal.restoreWave7Checkpoint(new EventSnapshot(Map.copyOf(incompleteFinalSealState)),false);
        require(incompleteFinalSeal.phase==EventPhase.RECOVERY_REQUIRED,
                "final-seal marker cannot bypass incomplete room completion receipts");
        var fourPlayerAssignment = ChamberIsolationPolicy.assign(List.of(
                new UUID(0,1),new UUID(0,2),new UUID(0,3),new UUID(0,4)));
        var oversizedChamberCountState = new LinkedHashMap<>(RealitySplitChamberSnapshot.encode(
                73,fourPlayerAssignment,Set.of(0,1,2,3),Set.of()));
        oversizedChamberCountState.put("reality-split.chamber-count","5");
        oversizedChamberCountState.put("final-seal-wave","7");
        var oversizedChamberCount = new Wave7CheckpointProbe();
        oversizedChamberCount.restoreWave7Checkpoint(
                new EventSnapshot(Map.copyOf(oversizedChamberCountState)),false);
        require(oversizedChamberCount.phase==EventPhase.RECOVERY_REQUIRED
                        && !oversizedChamberCount.realitySplitChamberController.owns(73)
                        && oversizedChamberCount.activeWave==0 && !oversizedChamberCount.finalSealBarrierHold,
                "out-of-range raw chamber count cannot be clamped into a completed final seal");
        var mixedFinalSealState = new LinkedHashMap<>(finalSealState);
        mixedFinalSealState.put("reality-split-trial.schema","2");
        var mixedFinalSeal = new Wave7CheckpointProbe();
        mixedFinalSeal.restoreWave7Checkpoint(new EventSnapshot(Map.copyOf(mixedFinalSealState)),false);
        require(mixedFinalSeal.phase==EventPhase.RECOVERY_REQUIRED,
                "final-seal checkpoint must not ignore partial or mixed trial progress");
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
        "domain/Wave7EchoDuelCellPolicy.java", "domain/wave7/EchoBehaviorStrategy.java",
        "domain/Wave7RoomLayoutPolicy.java", "domain/wave7/EchoLoadoutState.java",
        "runtime/RealitySplitChamberController.java",
        "runtime/RealitySplitEchoDuelState.java", "runtime/RealitySplitTrialController.java",
        "runtime/RealitySplitTrialSnapshot.java",
    )]
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), *map(str, inputs), str(probe)],
                   check=True, capture_output=True, text=True)
    result = subprocess.run(["java", "-cp", str(tmp_path), "Wave7CheckpointProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr

    bootstrap = source[source.index("private void restoreRealitySplitBarriersAfterBootstrap()"):
                       source.index("private boolean spawnRealitySplitBarriers(")]
    assert "isWave7RecoveryCheckpointActive()" in bootstrap
    assert bootstrap.index("if (isWave7RecoveryCheckpointActive())") < bootstrap.index(
        "reconcileOpeningRealitySplitPassages()"), (
            "recovery rebuild must keep a pending OPENING wall closed instead of replaying it")


def test_recovery_bootstrap_cleanup_preserves_rebuilt_wave7_barriers(tmp_path):
    source = (SOURCE / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    restore = extract(source, "void restorePersistedCombatRuntime()")
    cleanup = extract(source, "void cleanupUnexpectedCombatEntitiesAfterRestart()")
    boss_cleanup = extract(source, "void clearBossOnly()")
    clear = extract(source, "boolean clearWaveEntities(boolean includeRewards, boolean resetWaveObjectiveState)")
    assert restore.index("restoreRealitySplitBarriersAfterBootstrap()") < restore.index(
        "cleanupUnexpectedCombatEntitiesAfterRestart()"), (
            "bootstrap must rebuild the owned walls before stale combat cleanup")
    assert "boolean preserveWave7RecoveryCheckpoint = isWave7RecoveryCheckpointActive();" in cleanup
    assert "clearWaveEntities(true, !preserveWave7RecoveryCheckpoint)" in cleanup
    assert "if (resetWaveObjectiveState)" in clear
    assert "if (!clearWaveObjectiveState()) return false;" in clear
    normalized_boss_cleanup = " ".join(boss_cleanup.split())
    guarded_barrier_release = (
        'if (!finalSealBarrierContext() && !isWave7RecoveryCheckpointActive()) '
        '{ releaseFinalSealBarriers("boss-cleanup"); }'
    )
    assert guarded_barrier_release in normalized_boss_cleanup, (
        "boss cleanup must release barriers only outside an active Wave 7 recovery checkpoint")

    probe = tmp_path / "Wave7RecoveryCleanupProbe.java"
    probe.write_text('''
import java.util.logging.Logger;
public class Wave7RecoveryCleanupProbe {
    enum EventPhase { RECOVERY_REQUIRED, BOSS_CINEMATIC }
    EventPhase phase = EventPhase.RECOVERY_REQUIRED;
    String eventId = "synthetic";
    boolean testWaveFrontVisualMode, recoveryCheckpointActive = true;
    boolean barrierPresent = true, objectiveStateReset, staleCombatRemoved;
    int activeWave, liveMobs = 2;
    Logger getLogger() { return Logger.getAnonymousLogger(); }
    int waveForPhase(EventPhase value) { return 0; }
    boolean isWave7RecoveryCheckpointActive() { return recoveryCheckpointActive; }
    boolean isPersistedBossPhase(EventPhase value) { return false; }
    boolean finalSealBarrierContext() { return false; }
    int countLiveOwnedMobs() { return liveMobs; }
    boolean clearWaveEntities(boolean includeRewards, boolean resetWaveObjectiveState) {
        objectiveStateReset = resetWaveObjectiveState;
        staleCombatRemoved = true;
        if (resetWaveObjectiveState) barrierPresent = false;
        return true;
    }
    void clearBossOnly() { }
    void clearCombatAiState() { }
''' + cleanup + '''
    public static void main(String[] args) {
        var probe = new Wave7RecoveryCleanupProbe();
        probe.cleanupUnexpectedCombatEntitiesAfterRestart();
        if (!probe.staleCombatRemoved || probe.objectiveStateReset || !probe.barrierPresent)
            throw new AssertionError("recovery cleanup must remove stale actors while retaining rebuilt barriers");
    }
}
''', encoding="utf-8")
    compiled = subprocess.run(["javac", "-d", str(tmp_path), str(probe)],
                             capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "Wave7RecoveryCleanupProbe"],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
