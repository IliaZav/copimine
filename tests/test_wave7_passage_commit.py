"""Execute the production Wave 7 passage transaction and restart adapter."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent"


def method(source, name):
    start = source.index("private " + name) - 4
    opening = source.index("{", start)
    depth, end = 1, opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


def test_passage_intent_precedes_air_and_restart_reconciles_opening(tmp_path):
    source = (SOURCE / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    adapter = "\n".join(method(source, signature) for signature in (
        "void openCompletedRealitySplitRoom(int chamber, int chamberCount)",
        "int tryOpenCompletedRealitySplitPassages(int chamber, int chamberCount)",
        "boolean mayOpenRealitySplitPassage()",
        "boolean openRealitySplitPassageDurably(",
        "void rememberRealitySplitBoundaryRollback(int boundary,",
        "boolean recloseUncommittedRealitySplitBoundary(int boundary)",
        "void clearRealitySplitBoundaryRollback(int boundary)",
        "void scheduleRealitySplitBoundaryRollbackRetry()",
        "void tickUncommittedRealitySplitBoundaryRollbacks()",
        "boolean reconcileOpeningRealitySplitPassages()",
    ))
    probe = tmp_path / "PassageCommitProbe.java"
    probe.write_text('''
import java.util.*;
import java.util.logging.Logger;
import me.copimine.endevent.domain.ChamberIsolationPolicy;
import me.copimine.endevent.domain.RealitySplitBarrierPolicy;
import me.copimine.endevent.domain.RealitySplitChamberSnapshot;
import me.copimine.endevent.runtime.RealitySplitChamberController;
public class PassageCommitProbe {
  long generation = 5; String eventId = "synthetic";
  enum EventPhase { WAVE_7, RECOVERY_REQUIRED }
  EventPhase phase = EventPhase.WAVE_7;
  boolean testWaveFrontVisualMode; int activeWave = 7;
  RealitySplitChamberController realitySplitChamberController = new RealitySplitChamberController();
  final Set<RealitySplitBarrierPolicy.Cell> realitySplitBarrierCells = new LinkedHashSet<>();
  final Map<Integer,Set<RealitySplitBarrierPolicy.Cell>> realitySplitBarrierByBoundary = new LinkedHashMap<>();
  final Map<Integer,Set<RealitySplitBarrierPolicy.Cell>> realitySplitBoundaryCellsPendingCommit = new LinkedHashMap<>();
  final Map<Integer,List<RealitySplitBarrierPolicy.Cell>> realitySplitOpenedCellsPendingCommit = new LinkedHashMap<>();
  long realitySplitBoundaryRollbackRetryAtTick;
  long eventTickCounter;
  TrialStub realitySplitTrialController = new TrialStub();
  Map<Integer,Long> realitySplitOrphanedSinceMillis = new HashMap<>();
  Map<String,String> wave7RecoveryCheckpoint = Map.of();
  boolean physicalResult; boolean physicalCalled; boolean physicalOpen; boolean recovery;
  int physicalRollbackAttempts; int physicalCalls; boolean physicalRollbackResult = true; boolean visualRestoreResult = true;
  String worldName = "world";
  int saveAttempts; int failOnSave = -1;
  Map<String,String> durable = Map.of();
  List<String> order = new ArrayList<>();
  static class Sound { static Object BLOCK_AMETHYST_BLOCK_CHIME = new Object(); }
  static class World { }
  static class Bukkit { static World getWorld(String name) { return new World(); } }
  static class TrialState { String trial() { return "synthetic"; } }
  static class NamedTrialState { String trial() { return "synthetic"; } }
  static class TrialStub { NamedTrialState namedTrial(int chamber) { return null; } TrialState trial(int chamber) { return new TrialState(); } }
  Logger getLogger() { return Logger.getLogger("PassageCommitProbe"); }
  Object coreCombatAnchorLocation() { return null; }
  Object realitySplitChamberCombatPoint(Object core, int room, int count) { return null; }
  void renderWaveMilestone(int wave, Object point, String text, Object sound) { }
  int countCompletedRealitySplitChambers() { return realitySplitChamberController.completedChambers().size(); }
  Map<String,String> waveObjectiveProgressSnapshot() {
    Map<String,String> live = RealitySplitChamberSnapshot.encode(generation,
      realitySplitChamberController.assignment(), realitySplitChamberController.completedChambers(),
      realitySplitChamberController.openPassages(), realitySplitChamberController.openingPassages());
    return phase == EventPhase.RECOVERY_REQUIRED && !wave7RecoveryCheckpoint.isEmpty()
      ? wave7RecoveryCheckpoint : live;
  }
  boolean saveStateSync() {
    saveAttempts++;
    Map<String,String> candidate = Map.copyOf(waveObjectiveProgressSnapshot());
    order.add("save:" + candidate.get("reality-split.opening") + ":" + candidate.get("reality-split.open"));
    if (saveAttempts == failOnSave) return false;
    durable = candidate;
    return true;
  }
  boolean openRealitySplitBoundary(int boundary, int count) {
    physicalCalls++;
    physicalCalled = true;
    order.add("physical:" + boundary);
    if (durable.get("reality-split.opening") == null
          || durable.get("reality-split.opening").isEmpty())
      throw new AssertionError("physical AIR mutation ran without a durable OPENING intent");
    if (durable.get("reality-split.open").contains("0:1"))
      throw new AssertionError("physical opening must precede durable OPEN");
    if (physicalResult) {
      Set<RealitySplitBarrierPolicy.Cell> owned = realitySplitBarrierByBoundary.get(boundary);
      rememberRealitySplitBoundaryRollback(boundary, owned, List.copyOf(owned));
      physicalOpen = true;
      realitySplitBarrierCells.removeAll(owned);
      realitySplitBarrierByBoundary.remove(boundary);
    }
    return physicalResult;
  }
  boolean restoreRealitySplitBoundaryCollisionClosed(World world, List<RealitySplitBarrierPolicy.Cell> cells) {
    physicalRollbackAttempts++;
    if (!physicalRollbackResult) return false;
    physicalOpen = false;
    return true;
  }
  boolean restoreRealitySplitBoundaryVisuals(World world, List<RealitySplitBarrierPolicy.Cell> cells) {
    return visualRestoreResult;
  }
  boolean restoreRealitySplitBoundaryClosed(World world, List<RealitySplitBarrierPolicy.Cell> cells) {
    return restoreRealitySplitBoundaryCollisionClosed(world, cells)
      && restoreRealitySplitBoundaryVisuals(world, cells);
  }
  void enterNamedRealitySplitRecovery(String reason) {
    wave7RecoveryCheckpoint = Map.copyOf(waveObjectiveProgressSnapshot());
    recovery = true; phase = EventPhase.RECOVERY_REQUIRED;
    saveStateSync();
  }
  void begin() {
    var players = List.of(new UUID(0,1), new UUID(0,2));
    realitySplitChamberController.begin(generation, players);
    realitySplitChamberController.markChamberComplete(generation, 0);
    var wall = new RealitySplitBarrierPolicy.Cell(3, 0, 1);
    realitySplitBarrierCells.add(wall);
    realitySplitBarrierByBoundary.put(0, new LinkedHashSet<>(Set.of(wall)));
  }
  void beginFourRooms() {
    var players = List.of(new UUID(0,1), new UUID(0,2), new UUID(0,3), new UUID(0,4));
    realitySplitChamberController.begin(generation, players);
    realitySplitChamberController.markChamberComplete(generation, 0);
    for (int boundary : List.of(0, 3)) {
      var wall = new RealitySplitBarrierPolicy.Cell(boundary, 0, 1);
      realitySplitBarrierCells.add(wall);
      realitySplitBarrierByBoundary.put(boundary, new LinkedHashSet<>(Set.of(wall)));
    }
  }
  void restore(RealitySplitChamberSnapshot.Data data) {
    realitySplitChamberController.restore(data.generation(), data.assignment(),
      data.completedChambers(), data.openPassages(), data.openingPassages());
    var wall = new RealitySplitBarrierPolicy.Cell(3, 0, 1);
    realitySplitBarrierCells.add(wall);
    realitySplitBarrierByBoundary.put(0, new LinkedHashSet<>(Set.of(wall)));
  }
  static void require(boolean value, String text) { if(!value) throw new AssertionError(text); }
  public static void main(String[] args) {
    var refusedIntent = new PassageCommitProbe(); refusedIntent.begin();
    refusedIntent.physicalResult = true; refusedIntent.failOnSave = 1;
    refusedIntent.openCompletedRealitySplitRoom(0, 2);
    require(!refusedIntent.physicalCalled && !refusedIntent.physicalOpen,
      "a refused OPENING checkpoint must never mutate the physical wall");

    var recoveryGate = new PassageCommitProbe(); recoveryGate.beginFourRooms();
    recoveryGate.phase = EventPhase.RECOVERY_REQUIRED;
    recoveryGate.wave7RecoveryCheckpoint = Map.copyOf(recoveryGate.waveObjectiveProgressSnapshot());
    require(!recoveryGate.openRealitySplitPassageDurably(0, 1, 4)
            && recoveryGate.saveAttempts == 0 && !recoveryGate.physicalCalled,
      "recovery mode must reject a new passage transaction before persistence or block mutation");

    var stopAfterRecovery = new PassageCommitProbe(); stopAfterRecovery.beginFourRooms();
    stopAfterRecovery.failOnSave = 1;
    stopAfterRecovery.tryOpenCompletedRealitySplitPassages(0, 4);
    require(stopAfterRecovery.recovery && stopAfterRecovery.saveAttempts == 2
            && stopAfterRecovery.physicalCalls == 0
            && stopAfterRecovery.durable.get("reality-split.opening").isEmpty(),
      "a passage loop must stop after its first transaction enters recovery");

    var retry = new PassageCommitProbe(); retry.begin();
    retry.openCompletedRealitySplitRoom(0, 2);
    require(retry.physicalCalled && retry.order.get(0).startsWith("save:0:1:"),
      "the intent checkpoint must be first");
    require(retry.realitySplitChamberController.openPassages().isEmpty()
            && retry.realitySplitChamberController.openingPassages().size()==1,
      "a failed physical opening must retain OPENING without granting access");
    retry.physicalResult = true;
    retry.openCompletedRealitySplitRoom(0, 2);
    require(retry.realitySplitChamberController.boundaryOpen(0,1)
            && retry.realitySplitChamberController.openingPassages().isEmpty(),
      "a successful retry must transition OPENING to OPEN");
    require("0:1".equals(retry.durable.get("reality-split.open")),
      "OPEN must be durable after the physical opening");
    require(!retry.realitySplitBoundaryCellsPendingCommit.containsKey(0)
            && retry.realitySplitBarrierByBoundary.isEmpty(),
      "a durable OPEN must discard its rollback ticket and keep the boundary removed");

    var interrupted = new PassageCommitProbe(); interrupted.begin();
    interrupted.physicalResult = true; interrupted.failOnSave = 2;
    interrupted.openCompletedRealitySplitRoom(0, 2);
    require(interrupted.recovery && interrupted.durable.get("reality-split.open").isEmpty()
            && "0:1".equals(interrupted.durable.get("reality-split.opening")),
      "failure to persist OPEN must leave the last durable OPENING checkpoint");
    require(!interrupted.physicalOpen,
      "failure to persist OPEN must restore the physical wall before recovery");
    require(interrupted.physicalRollbackAttempts == 1
            && interrupted.realitySplitBarrierCells.size() == 1
            && interrupted.realitySplitBarrierByBoundary.containsKey(0)
            && interrupted.realitySplitBoundaryCellsPendingCommit.isEmpty(),
      "physical rollback must restore Wave 7 barrier ownership before recovery");

    var missingVisual = new PassageCommitProbe(); missingVisual.begin();
    missingVisual.physicalResult = true; missingVisual.failOnSave = 2;
    missingVisual.visualRestoreResult = false;
    missingVisual.openCompletedRealitySplitRoom(0, 2);
    require(!missingVisual.physicalOpen,
      "visual recreation failure must not undo the collision wall rollback");
    require(missingVisual.realitySplitBarrierCells.size() == 1
            && missingVisual.realitySplitBarrierByBoundary.containsKey(0)
            && missingVisual.realitySplitBoundaryCellsPendingCommit.containsKey(0)
            && missingVisual.recovery,
      "visual recovery failure must keep collision ownership closed and retain a retry ticket");
    missingVisual.eventTickCounter = missingVisual.realitySplitBoundaryRollbackRetryAtTick - 1;
    missingVisual.visualRestoreResult = true;
    missingVisual.tickUncommittedRealitySplitBoundaryRollbacks();
    require(missingVisual.realitySplitBoundaryCellsPendingCommit.containsKey(0),
      "visual rollback retry must wait until its bounded retry deadline");
    missingVisual.eventTickCounter++;
    missingVisual.tickUncommittedRealitySplitBoundaryRollbacks();
    require(!missingVisual.realitySplitBoundaryCellsPendingCommit.containsKey(0)
            && missingVisual.realitySplitBarrierCells.size() == 1
            && missingVisual.realitySplitBarrierByBoundary.containsKey(0),
      "a later visual restore must complete the retained rollback ticket");

    var restarted = new PassageCommitProbe();
    restarted.restore(RealitySplitChamberSnapshot.decode(interrupted.durable, 5));
    restarted.physicalResult = true;
    require(restarted.reconcileOpeningRealitySplitPassages(),
      "startup reconciliation must finish the durable OPENING intent");
    require(restarted.realitySplitChamberController.openingPassages().isEmpty()
            && restarted.realitySplitChamberController.boundaryOpen(0,1)
            && "0:1".equals(restarted.durable.get("reality-split.open")),
      "restart reconciliation must persist OPEN after the physical retry");
  }
''' + adapter + "\n}\n", encoding="utf-8")
    inputs = [SOURCE / item for item in (
        "domain/ChamberIsolationPolicy.java", "domain/RealitySplitBarrierPolicy.java",
        "domain/RealitySplitChamberSnapshot.java", "runtime/RealitySplitChamberController.java",
    )]
    compiled = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path),
                               *map(str, inputs), str(probe)], capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "PassageCommitProbe"],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr


def test_recovery_phase_stops_all_wave7_passage_callers():
    source = (SOURCE / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    objective = method(source, "void tickCurrentChamberObjective(long now)")
    orphan_recovery = method(source, "void recoverRealitySplitOrphanRooms(long now)")
    assert "if (phase == EventPhase.RECOVERY_REQUIRED) return;" in objective
    assert "&& phase != EventPhase.RECOVERY_REQUIRED; chamber++" in objective
    assert "&& phase != EventPhase.RECOVERY_REQUIRED; other++" in objective
    assert "if (phase == EventPhase.RECOVERY_REQUIRED) return;" in orphan_recovery
    assert "&& phase != EventPhase.RECOVERY_REQUIRED; other++" in orphan_recovery
