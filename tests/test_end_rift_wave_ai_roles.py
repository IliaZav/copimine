"""Behaviour probes for production wave tactics and spell selection, not source labels."""
from pathlib import Path
import subprocess
import pytest

ROOT = Path(__file__).resolve().parents[1]

@pytest.fixture(scope="module")
def ai_probe(tmp_path_factory):
    directory = tmp_path_factory.mktemp("wave-ai-roles")
    probe = directory / "WaveAiRolesProbe.java"
    probe.write_text('''
import java.util.*;
import me.copimine.endevent.domain.*;
public class WaveAiRolesProbe {
  static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
  public static void main(String[] args){
    switch(args[0]){
      case "simple-first-wave" -> {
        for(int slot=0;slot<30;slot++){
          var spell=EndRiftAiPolicy.miniBossSpell(EndRiftObjective.Objective.RIFT_CARRIERS,slot);
          check(spell==EndRiftAiPolicy.MiniBossSpell.ECHO_PULSE || spell==EndRiftAiPolicy.MiniBossSpell.RIFT_STEP,
            "Wave 1 cannot introduce ranged, arrow or narcotic enemies: "+spell);
        }
      }
      case "objective-elites" -> {
        check(CombatTacticsPolicy.tacticFor(EndRiftObjective.Objective.RIFT_GATES,"ELITE",0)
          ==CombatTacticsPolicy.MobTactic.GATE_DEFENDER,"elite must defend the active portal");
        check(CombatTacticsPolicy.tacticFor(EndRiftObjective.Objective.OBELISK_ASSAULT,"ELITE",0)
          ==CombatTacticsPolicy.MobTactic.OBELISK_GUARD,"elite must retain its obelisk role");
      }
      case "later-waves-unchanged" -> {
        for(var objective : new EndRiftObjective.Objective[]{EndRiftObjective.Objective.COLLAPSE_RINGS,
            EndRiftObjective.Objective.RITUAL_SPHERE,EndRiftObjective.Objective.REALITY_SPLIT}) {
          for(int slot=0;slot<30;slot++) {
            var expected=EndRiftAiPolicy.MiniBossSpell.values()[Math.floorMod(objective.ordinal()+slot,5)];
            check(EndRiftAiPolicy.miniBossSpell(objective,slot)==expected,"later wave spell assignment changed");
            check(CombatTacticsPolicy.tacticFor(objective,"ELITE",slot)==CombatTacticsPolicy.MobTactic.ELITE_HUNTER,
              "separate later-wave controllers must retain their existing tier assignment");
          }
        }
      }
      default -> throw new AssertionError("unknown case");
    }
  }
}
''', encoding="utf-8")
    domain = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"
    files = [domain / name for name in ["BossPhase.java", "CombatTacticsPolicy.java", "EndRiftAiPolicy.java", "EndRiftObjective.java"]]
    result = subprocess.run(["javac", "-d", str(directory), *map(str,files), str(probe)],capture_output=True,text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    return directory

@pytest.mark.parametrize("scenario", ["simple-first-wave", "objective-elites", "later-waves-unchanged"])
def test_existing_wave_policy_preserves_objective_and_counterplay(ai_probe, scenario):
    result=subprocess.run(["java","-cp",str(ai_probe),"WaveAiRolesProbe",scenario],capture_output=True,text=True)
    assert result.returncode == 0, result.stdout+result.stderr
