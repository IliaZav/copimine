"""Wave 6 elite pillar skeleton: the only skeleton-on-a-pillar mechanic.

The master rebuild puts one elite marksman on top of the final pillar. It must
move, fire two distinguishable modelled volleys, stay killable, and drop to the
floor when its pillar collapses instead of becoming an unreachable orphan.
These assertions execute the production policy.
"""
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / 'copimine-end-event/src/me/copimine/endevent/domain'

PROBE = r'''
import me.copimine.endevent.domain.WaveSixPillarSkeletonPolicy;
import me.copimine.endevent.domain.WaveSixPillarSkeletonPolicy.Ability;
public class PillarSkeletonProbe {
  static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
  static boolean unit(double[] v) {
    return Math.abs(Math.hypot(v[0], v[1]) - 1.0D) < 1e-9;
  }
  public static void main(String[] args) {
    // --- the two abilities are distinguishable and both are really used ----
    check(WaveSixPillarSkeletonPolicy.TRIPLE_SHOT_ARROWS == 3, "the triple shot fires three");
    check(WaveSixPillarSkeletonPolicy.BONE_RAIN_ARROWS >= 1, "the second ability fires something");
    check(WaveSixPillarSkeletonPolicy.TRIPLE_SHOT_SPREAD_DEGREES
            != WaveSixPillarSkeletonPolicy.BONE_RAIN_ARC_DEGREES,
            "the two volleys must not have the same silhouette");
    check(WaveSixPillarSkeletonPolicy.TRIPLE_SHOT_COOLDOWN_TICKS
            != WaveSixPillarSkeletonPolicy.BONE_RAIN_COOLDOWN_TICKS,
            "the two abilities must not share one timer");

    // --- no target means no cast, whatever the cooldowns say --------------
    check(WaveSixPillarSkeletonPolicy.nextAbility(500, 0, 0, false) == Ability.NONE,
            "an untargeted skeleton never fires");
    check(WaveSixPillarSkeletonPolicy.nextAbility(-1, 0, 0, true) == Ability.NONE,
            "a negative tick never fires");

    // --- cooldowns gate each ability independently ------------------------
    check(WaveSixPillarSkeletonPolicy.nextAbility(0, -1, -1, true) == Ability.BONE_RAIN,
            "a fresh skeleton opens with the heavier volley");
    // At tick 1 both abilities were just used at tick 0, so neither may fire.
    // The short cooldown is the one that expires first, so pick a tick inside it.
    check(WaveSixPillarSkeletonPolicy.nextAbility(
            WaveSixPillarSkeletonPolicy.TRIPLE_SHOT_COOLDOWN_TICKS - 1, 0, 0, true) == Ability.NONE,
            "nothing fires inside both cooldowns");
    // Once the short cooldown lapses the triple shot takes over, even though
    // the heavy volley is still recharging.
    check(WaveSixPillarSkeletonPolicy.nextAbility(
            WaveSixPillarSkeletonPolicy.TRIPLE_SHOT_COOLDOWN_TICKS, 0, 0, true) == Ability.TRIPLE_SHOT,
            "the triple shot takes over when the heavy volley is spent");
    // The heavier volley must not monopolise: after both are ready it wins, so
    // the pair genuinely alternates rather than repeating one animation.
    check(WaveSixPillarSkeletonPolicy.nextAbility(10_000, 0, 0, true) == Ability.BONE_RAIN,
            "both ready prefers the heavy volley, so the fight alternates");

    // --- every fired direction is a real unit vector ----------------------
    double[][] triple = WaveSixPillarSkeletonPolicy.tripleShotDirections(0.0D, 1.0D);
    check(triple.length == WaveSixPillarSkeletonPolicy.TRIPLE_SHOT_ARROWS, "triple shot width");
    for (double[] d : triple) check(unit(d), "triple shot direction is normalised");
    // The centre arrow of an odd spread points straight at the target.
    check(Math.abs(triple[1][0]) < 1e-9 && Math.abs(triple[1][1] - 1.0D) < 1e-9,
            "the middle arrow aims exactly at the target");
    // The fan is symmetric about the aim: the two flanking arrows diverge to
    // opposite sides, and by exactly the same amount.
    check(triple[0][0] * triple[2][0] < 0, "the flanking arrows fan to both sides");
    check(Math.abs(triple[0][0] + triple[2][0]) < 1e-9, "the fan is symmetric about the aim");

    double[][] bone = WaveSixPillarSkeletonPolicy.boneRainDirections(1.0D, 0.0D);
    check(bone.length == WaveSixPillarSkeletonPolicy.BONE_RAIN_ARROWS, "bone rain width");
    for (double[] d : bone) check(unit(d), "bone rain direction is normalised");
    // A wider arc than the triple shot, i.e. visibly a different attack.
    check(Math.abs(bone[0][1]) > Math.abs(triple[0][0]),
            "the heavy volley spreads wider than the triple shot");

    // --- a degenerate aim must not fire into the skeleton itself ----------
    double[][] onSpot = WaveSixPillarSkeletonPolicy.spread(0.0D, 0.0D, 3, 7.0D);
    check(onSpot.length == 0, "a zero-length aim produces no arrows");
    check(WaveSixPillarSkeletonPolicy.spread(Double.NaN, 0.0D, 3, 7.0D).length == 0,
            "a non-finite aim produces no arrows");
    check(WaveSixPillarSkeletonPolicy.spread(0.0D, 1.0D, 0, 7.0D).length == 0,
            "asking for zero arrows yields zero");

    // --- it moves instead of standing still ------------------------------
    check(WaveSixPillarSkeletonPolicy.shouldReposition(0, -1), "a new skeleton picks a spot");
    check(!WaveSixPillarSkeletonPolicy.shouldReposition(
            WaveSixPillarSkeletonPolicy.REPOSITION_TICKS - 1, 0), "it holds briefly");
    check(WaveSixPillarSkeletonPolicy.shouldReposition(
            WaveSixPillarSkeletonPolicy.REPOSITION_TICKS, 0), "then it moves");
    check(!WaveSixPillarSkeletonPolicy.shouldReposition(-5, 0), "a negative tick never repositions");
    // Patrol stays on the platform.
    for (int step = 0; step < 64; step++) {
      double[] off = WaveSixPillarSkeletonPolicy.patrolOffset(step);
      double r = Math.hypot(off[0], off[1]);
      check(r <= WaveSixPillarSkeletonPolicy.PATROL_RADIUS + 1e-9, "patrol stays on the platform");
      check(r > 0.0D, "patrol actually displaces");
    }
    // Distinct steps give distinct spots, so it is not oscillating on one line.
    double[] a = WaveSixPillarSkeletonPolicy.patrolOffset(1);
    double[] b = WaveSixPillarSkeletonPolicy.patrolOffset(2);
    check(Math.hypot(a[0] - b[0], a[1] - b[1]) > 0.05D, "consecutive patrol spots differ");

    // --- the drop happens exactly once, and only for a survivor ----------
    check(!WaveSixPillarSkeletonPolicy.shouldFall(6, true, false),
            "the skeleton does not fall before the pillar collapses");
    check(WaveSixPillarSkeletonPolicy.shouldFall(7, true, false),
            "a survivor falls when the final pillar is destroyed");
    check(!WaveSixPillarSkeletonPolicy.shouldFall(7, false, false),
            "a dead skeleton never falls");
    check(!WaveSixPillarSkeletonPolicy.shouldFall(7, true, true),
            "the drop happens once");
    check(!WaveSixPillarSkeletonPolicy.shouldFall(6, false, true), "nothing falls on an ordinary impact");

    // --- no orphan may be left behind ------------------------------------
    check(!WaveSixPillarSkeletonPolicy.encounterMayComplete(true, true),
            "a skeleton still on a destroyed pillar blocks completion");
    check(WaveSixPillarSkeletonPolicy.encounterMayComplete(true, false),
            "a skeleton fighting on the floor does not block completion");
    check(WaveSixPillarSkeletonPolicy.encounterMayComplete(false, false),
            "no skeleton does not block completion");

    System.out.println("OK");
  }
}
'''


def test_elite_pillar_skeleton(tmp_path):
    probe = tmp_path / 'PillarSkeletonProbe.java'
    probe.write_text(PROBE, encoding='utf-8')
    built = subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(tmp_path),
                            str(DOMAIN / 'RiftRamPolicy.java'),
                            str(DOMAIN / 'WaveSixPillarSkeletonPolicy.java'), str(probe)],
                           capture_output=True, text=True)
    assert built.returncode == 0, built.stderr
    result = subprocess.run(['java', '-cp', str(tmp_path), 'PillarSkeletonProbe'],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stderr
    assert 'OK' in result.stdout
