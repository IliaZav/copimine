"""Execute the production completed-room adapter with a failed physical opening."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def method(source, signature):
    start = source.index("    private " + signature)
    opening = source.index("{", start)
    depth, end = 1, opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


def test_logical_passage_waits_for_physical_restore_and_can_retry(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    adapter = method(source, "void openCompletedRealitySplitRoom(int chamber, int chamberCount)")
    if "private int tryOpenCompletedRealitySplitPassages" in source:
        adapter += method(source, "int tryOpenCompletedRealitySplitPassages(int chamber, int chamberCount)")
    java = tmp_path / "PassageCommitProbe.java"
    java.write_text('''
import java.util.*;
import java.util.logging.Logger;
import me.copimine.endevent.domain.RealitySplitBarrierPolicy;
import me.copimine.endevent.runtime.RealitySplitChamberController;
import me.copimine.endevent.runtime.RealitySplitTrialController;
public class PassageCommitProbe {
  long generation = 5; String eventId = "synthetic"; boolean restored;
  RealitySplitChamberController realitySplitChamberController = new RealitySplitChamberController();
  RealitySplitTrialController realitySplitTrialController = new RealitySplitTrialController();
  Map<Integer,Long> realitySplitOrphanedSinceMillis = new HashMap<>();
  static class Sound { static Object BLOCK_AMETHYST_BLOCK_CHIME = new Object(); }
  Logger getLogger() { return Logger.getLogger("PassageCommitProbe"); }
  Object coreCombatAnchorLocation() { return null; }
  Object realitySplitChamberCombatPoint(Object core, int room, int count) { return null; }
  void renderWaveMilestone(int wave, Object point, String text, Object sound) { }
  int countCompletedRealitySplitChambers() { return 1; }
  boolean openRealitySplitBoundary(int boundary, int count) { return restored; }
  public static void main(String[] args) {
    var probe = new PassageCommitProbe();
    probe.realitySplitChamberController.begin(5, List.of(new UUID(0,1), new UUID(0,2)));
    probe.realitySplitTrialController.begin(5, probe.realitySplitChamberController.assignment());
    probe.realitySplitChamberController.markChamberComplete(5, 0);
    probe.openCompletedRealitySplitRoom(0, 2);
    if (!probe.realitySplitChamberController.openPassages().isEmpty())
      throw new AssertionError("failed block restoration granted imaginary helper access");
    probe.restored = true;
    probe.openCompletedRealitySplitRoom(0, 2);
    if (!probe.realitySplitChamberController.boundaryOpen(0, 1))
      throw new AssertionError("successful retry did not commit the passage");
    probe.openCompletedRealitySplitRoom(0, 2);
    if (probe.realitySplitChamberController.openPassages().size() != 1)
      throw new AssertionError("duplicate callback duplicated passage receipts");
  }
''' + adapter + "\n}\n", encoding="utf-8")
    paths = [ROOT / "copimine-end-event/src/me/copimine/endevent/domain" / name
             for name in ["ChamberIsolationPolicy.java", "RealitySplitBarrierPolicy.java", "Wave7AdmissionPolicy.java"]]
    paths += [ROOT / "copimine-end-event/src/me/copimine/endevent/runtime" / name
              for name in ["RealitySplitChamberController.java", "RealitySplitTrialController.java"]]
    compile_result = subprocess.run(["javac", "-J-Xmx128m", "-d", str(tmp_path), *map(str, paths), str(java)],
                                    capture_output=True, text=True)
    assert compile_result.returncode == 0, compile_result.stdout + compile_result.stderr
    result = subprocess.run(["java", "-Xmx128m", "-cp", str(tmp_path), "PassageCommitProbe"],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
