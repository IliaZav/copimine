"""Wave 6 pillar roster: four minor pillars, then one final pillar worth three impacts.

SUPERSEDED CONTRACT (2026-11-11 master rebuild): the earlier encounter was a
single pillar hit three times (RiftRamPolicy.REQUIRED_IMPACTS = 3). The user
asked for "four ordinary pillars followed by one very large final pillar", the
final one requiring "three distinct successful Ram collisions". That is seven
accepted impacts across five pillars, and this file holds that shape.

These assertions execute the production policy directly. Presence of a constant
is not evidence that the roster behaves; every check below is a real call.
"""
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / 'copimine-end-event/src/me/copimine/endevent/domain'

PROBE = r'''
import me.copimine.endevent.domain.RiftRamPolicy;
public class RamPillarRosterProbe {
  static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
  public static void main(String[] args) {
    // --- roster shape -------------------------------------------------
    check(RiftRamPolicy.MINOR_PILLARS == 4, "four ordinary pillars");
    check(RiftRamPolicy.FINAL_PILLAR_IMPACTS == 3, "the final pillar takes three collisions");
    check(RiftRamPolicy.PILLAR_COUNT == 5, "four ordinary plus one final");
    check(RiftRamPolicy.REQUIRED_IMPACTS == 7, "seven accepted impacts in total");
    check(RiftRamPolicy.MINOR_PILLARS * 1 + RiftRamPolicy.FINAL_PILLAR_IMPACTS
            == RiftRamPolicy.REQUIRED_IMPACTS, "the roster adds up to the total");

    // --- which pillar is standing, and how much of it is left ----------
    // impacts 0..3 are the four ordinary pillars, one impact each.
    for (int impacts = 0; impacts < RiftRamPolicy.MINOR_PILLARS; impacts++) {
      int ordinal = RiftRamPolicy.pillarOrdinal(impacts);
      check(ordinal == impacts + 1, "ordinary pillar ordinal follows the impact count");
      check(!RiftRamPolicy.isFinalPillar(ordinal), "the first four pillars are ordinary");
      check(RiftRamPolicy.impactsRequiredForPillar(ordinal) == 1, "an ordinary pillar takes one collision");
      check(RiftRamPolicy.impactsRemainingOnPillar(impacts) == 1,
              "an ordinary pillar is destroyed by its single impact");
    }
    // impacts 4,5,6 are the final pillar.
    int finalOrdinal = RiftRamPolicy.pillarOrdinal(RiftRamPolicy.MINOR_PILLARS);
    check(finalOrdinal == 5, "the fifth pillar is final");
    check(RiftRamPolicy.isFinalPillar(5), "the fifth pillar is the final one");
    check(RiftRamPolicy.impactsRequiredForPillar(5) == 3, "the final pillar takes three");
    check(RiftRamPolicy.impactsRemainingOnPillar(4) == 3, "untouched final pillar needs three");
    check(RiftRamPolicy.impactsRemainingOnPillar(5) == 2, "one crack still needs two");
    check(RiftRamPolicy.impactsRemainingOnPillar(6) == 1, "core exposed still needs one");
    check(RiftRamPolicy.impactsRemainingOnPillar(7) == 0, "collapsed final pillar needs none");

    // The final pillar's three stages must be named and distinct.
    check(RiftRamPolicy.finalPillarStage(4) == 0, "stage 0 before the first collision");
    check(RiftRamPolicy.finalPillarStage(5) == 1, "shell cracks on the first");
    check(RiftRamPolicy.finalPillarStage(6) == 2, "a section breaks on the second");
    check(RiftRamPolicy.finalPillarStage(7) == 3, "collapse on the third");

    // --- no pillar may be raised past the roster ----------------------
    check(RiftRamPolicy.mayRaisePillar(0), "first pillar may rise");
    check(RiftRamPolicy.mayRaisePillar(6), "the final pillar may still be standing at 6 impacts");
    check(!RiftRamPolicy.mayRaisePillar(7), "nothing rises after the seventh impact");
    check(RiftRamPolicy.activePillarNumber(0) == 1, "pillar 1 is active from the start");
    check(RiftRamPolicy.activePillarNumber(4) == 5, "pillar 5 is active for the final phase");
    check(RiftRamPolicy.activePillarNumber(7) == 0, "no pillar remains after the seventh");

    // --- a glancing charge must not advance the same pillar twice ------
    check(RiftRamPolicy.mayAcceptImpact(RiftRamPolicy.State.CHARGING, 0, false), "first impact lands");
    check(!RiftRamPolicy.mayAcceptImpact(RiftRamPolicy.State.CHARGING, 0, true),
            "a charge already banked cannot bank again");
    check(!RiftRamPolicy.mayAcceptImpact(RiftRamPolicy.State.STUNNED, 0, false),
            "a stunned Ram is not charging");
    check(!RiftRamPolicy.mayAcceptImpact(RiftRamPolicy.State.CHARGING, 7, false),
            "no impact past the roster");

    // --- armour ladder over seven impacts -----------------------------
    check(RiftRamPolicy.ARMOUR_PLATES == 7, "seven armour stages, one per collision");
    for (int i = 0; i <= 7; i++) {
      check(RiftRamPolicy.remainingPlates(i) == 7 - i, "one plate leaves per accepted impact");
      double expected = (i + 1.0D) / 8.0D;
      check(Math.abs(RiftRamPolicy.damageMultiplier(i) - expected) < 1e-9, "damage ladder is a clean ramp");
      check(!RiftRamPolicy.armourBroken(i) == (i < 7), "armour holds until the seventh");
    }
    check(RiftRamPolicy.damageMultiplier(7) == 1.0D, "full damage once every plate is gone");

    // --- completion ---------------------------------------------------
    for (int i = 0; i < 7; i++) check(!RiftRamPolicy.isComplete(i, false), "only seven impacts complete on death");
    check(RiftRamPolicy.isComplete(7, false), "seven impacts and a dead Ram complete the encounter");
    check(!RiftRamPolicy.isComplete(7, true), "a living Ram is not complete");
    check(!RiftRamPolicy.isComplete(6, false), "six impacts are not the roster");

    // --- the elite skeleton belongs to the final pillar only ----------
    check(RiftRamPolicy.ELITE_SKELETON_PILLAR == 5, "the marksman skeleton stands on the final pillar");
    check(RiftRamPolicy.eliteSkeletonAliveOn(0, true) == false, "no skeleton on an ordinary pillar");
    check(RiftRamPolicy.eliteSkeletonAliveOn(4, true) == true, "the skeleton appears with the final pillar");
    check(RiftRamPolicy.eliteSkeletonAliveOn(4, false) == false, "a dead skeleton stays dead");
    check(RiftRamPolicy.eliteSkeletonFallsOn(7), "a surviving skeleton falls when the pillar collapses");
    check(!RiftRamPolicy.eliteSkeletonFallsOn(6), "the skeleton does not fall before the collapse");

    System.out.println("OK");
  }
}
'''


def test_pillar_roster_is_four_minor_and_one_final(tmp_path):
    probe = tmp_path / 'RamPillarRosterProbe.java'
    probe.write_text(PROBE, encoding='utf-8')
    built = subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(tmp_path),
                            str(DOMAIN / 'RiftRamPolicy.java'), str(probe)],
                           capture_output=True, text=True)
    assert built.returncode == 0, built.stderr
    result = subprocess.run(['java', '-cp', str(tmp_path), 'RamPillarRosterProbe'],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stderr
    assert 'OK' in result.stdout
