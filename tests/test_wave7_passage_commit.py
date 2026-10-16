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
        "boolean openRealitySplitPassageDurably(",
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
  RealitySplitChamberController realitySplitChamberController = new RealitySplitChamberController();
  TrialStub realitySplitTrialController = new TrialStub();
  Map<Integer,Long> realitySplitOrphanedSinceMillis = new HashMap<>();
  Map<String,String> wave7RecoveryCheckpoint = Map.of();
  boolean physicalResult; boolean physicalCalled; boolean recovery;
  int saveAttempts; int failOnSave = -1;
  Map<String,String> durable = Map.of();
  List<String> order = new ArrayList<>();
  static class Sound { static Object BLOCK_AMETHYST_BLOCK_CHIME = new Object(); }
  static class TrialState { String trial() { return "synthetic"; } }
  static class NamedTrialState { String trial() { return "synthetic"; } }
  static class TrialStub { NamedTrialState namedTrial(int chamber) { return null; } TrialState trial(int chamber) { return new TrialState(); } }
  Logger getLogger() { return Logger.getLogger("PassageCommitProbe"); }
  Object coreCombatAnchorLocation() { return null; }
  Object realitySplitChamberCombatPoint(Object core, int room, int count) { return null; }
  void renderWaveMilestone(int wave, Object point, String text, Object sound) { }
  int countCompletedRealitySplitChambers() { return realitySplitChamberController.completedChambers().size(); }
  Map<String,String> waveObjectiveProgressSnapshot() {
    return RealitySplitChamberSnapshot.encode(generation,
      realitySplitChamberController.assignment(), realitySplitChamberController.completedChambers(),
      realitySplitChamberController.openPassages(), realitySplitChamberController.openingPassages());
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
    physicalCalled = true;
    order.add("physical:" + boundary);
    if (!("0:1".equals(durable.get("reality-split.opening"))
          || "1:0".equals(durable.get("reality-split.opening"))))
      throw new AssertionError("physical AIR mutation ran without a durable OPENING intent");
    if (durable.get("reality-split.open").contains("0:1"))
      throw new AssertionError("physical opening must precede durable OPEN");
    return physicalResult;
  }
  void enterNamedRealitySplitRecovery(String reason) {
    recovery = true; phase = EventPhase.RECOVERY_REQUIRED;
    wave7RecoveryCheckpoint = Map.copyOf(waveObjectiveProgressSnapshot());
    saveStateSync();
  }
  void begin() {
    var players = List.of(new UUID(0,1), new UUID(0,2));
    realitySplitChamberController.begin(generation, players);
    realitySplitChamberController.markChamberComplete(generation, 0);
  }
  void restore(RealitySplitChamberSnapshot.Data data) {
    realitySplitChamberController.restore(data.generation(), data.assignment(),
      data.completedChambers(), data.openPassages(), data.openingPassages());
  }
  static void require(boolean value, String text) { if(!value) throw new AssertionError(text); }
  public static void main(String[] args) {
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

    var interrupted = new PassageCommitProbe(); interrupted.begin();
    interrupted.physicalResult = true; interrupted.failOnSave = 2;
    interrupted.openCompletedRealitySplitRoom(0, 2);
    require(interrupted.recovery && interrupted.durable.get("reality-split.open").isEmpty()
            && "0:1".equals(interrupted.durable.get("reality-split.opening")),
      "failure to persist OPEN must leave the last durable OPENING checkpoint");

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
