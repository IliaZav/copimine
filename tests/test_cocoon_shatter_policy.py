"""9G.07 cocoon split: the authored shatter plan is executable, not string-matched."""
from pathlib import Path
import subprocess

import pytest

ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"

PROBE = r"""
import me.copimine.endevent.domain.CocoonShatterPolicy;

public final class CocoonShatterProbe {
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }

    public static void main(String[] args) {
        var plates = CocoonShatterPolicy.plates();
        check(plates.size() == CocoonShatterPolicy.PLATE_COUNT, "plate count");
        check(plates.equals(CocoonShatterPolicy.plates()), "plate layout must be deterministic");

        boolean[] seen = new boolean[CocoonShatterPolicy.PLATE_COUNT];
        for (var plate : plates) {
            check(plate.index() >= 0 && plate.index() < seen.length, "index out of range");
            check(!seen[plate.index()], "duplicate plate index");
            seen[plate.index()] = true;
            check(plate.yawDegrees() >= 0 && plate.yawDegrees() < 360, "yaw range");
            check(plate.elevationDegrees() >= -30 && plate.elevationDegrees() <= 50, "elevation range");
            check(plate.speed() > 0 && plate.speed() < 0.4, "speed range");
            check(plate.size() > 0.2 && plate.size() < 1.0, "size range");
            check(plate.releaseDelay() >= 0 && plate.releaseDelay() <= 3, "stagger range");
        }

        check(CocoonShatterPolicy.stageAt(0) == CocoonShatterPolicy.Stage.VEINS_PULSE, "t0 veins");
        check(CocoonShatterPolicy.stageAt(CocoonShatterPolicy.VEINS_TICKS - 1)
                == CocoonShatterPolicy.Stage.VEINS_PULSE, "last veins tick");
        check(CocoonShatterPolicy.stageAt(CocoonShatterPolicy.VEINS_TICKS)
                == CocoonShatterPolicy.Stage.SHELL_CRACK, "crack start");
        int burst = CocoonShatterPolicy.VEINS_TICKS + CocoonShatterPolicy.CRACK_TICKS;
        check(CocoonShatterPolicy.stageAt(burst - 1) == CocoonShatterPolicy.Stage.SHELL_CRACK,
                "last crack tick");
        check(CocoonShatterPolicy.stageAt(burst) == CocoonShatterPolicy.Stage.PLATE_BURST,
                "burst start");
        int dust = burst + CocoonShatterPolicy.BURST_TICKS;
        check(CocoonShatterPolicy.stageAt(dust - 1) == CocoonShatterPolicy.Stage.PLATE_BURST,
                "last burst tick");
        check(CocoonShatterPolicy.stageAt(dust) == CocoonShatterPolicy.Stage.SHOCK_DUST,
                "dust start");
        check(CocoonShatterPolicy.stageAt(CocoonShatterPolicy.TOTAL_TICKS)
                == CocoonShatterPolicy.Stage.REVEAL, "reveal");
        check(CocoonShatterPolicy.finishedAt(CocoonShatterPolicy.TOTAL_TICKS), "finished");
        check(!CocoonShatterPolicy.finishedAt(CocoonShatterPolicy.TOTAL_TICKS - 1), "not finished");
        check(CocoonShatterPolicy.burstStartsAt(burst), "burst cue exact");
        check(!CocoonShatterPolicy.burstStartsAt(burst + 1), "burst cue once");
        check(CocoonShatterPolicy.shockStartsAt(dust), "shock cue exact");

        // Rest pose is on the shell, not at the centre.
        for (var plate : plates) {
            var rest = CocoonShatterPolicy.offset(plate, 0);
            double radius = Math.sqrt(rest.x() * rest.x() + rest.z() * rest.z());
            check(radius > 0.0, "resting plate on axis: " + plate.index());
            check(Math.abs(rest.y()) <= CocoonShatterPolicy.SHELL_HALF_HEIGHT + 1e-6,
                    "resting height");
        }

        // Released plates travel outward and respect the hard radius budget.
        var plate = plates.get(0);
        int age = 12;
        var early = CocoonShatterPolicy.offset(plate, plate.releaseDelay() + 1);
        var late = CocoonShatterPolicy.offset(plate, plate.releaseDelay() + age);
        double earlyRadius = Math.sqrt(early.x() * early.x() + early.z() * early.z());
        double lateRadius = Math.sqrt(late.x() * late.x() + late.z() * late.z());
        check(lateRadius > earlyRadius, "plate must move outward");
        for (var p : plates) {
            var far = CocoonShatterPolicy.offset(p, 400);
            double radius = Math.sqrt(far.x() * far.x() + far.z() * far.z());
            check(radius <= CocoonShatterPolicy.MAX_RADIUS + 1e-6, "plate escaped the budget");
            check(Double.isFinite(far.x()) && Double.isFinite(far.y())
                    && Double.isFinite(far.z()), "non-finite plate");
        }

        // Ballistic arc: the piece leaves the shell, rises, then falls.
        boolean rose = false;
        for (int t = 1; t < 40; t++) {
            var a = CocoonShatterPolicy.offset(plate, plate.releaseDelay() + t);
            var b = CocoonShatterPolicy.offset(plate, plate.releaseDelay() + t + 1);
            if (b.y() > a.y()) rose = true;
        }
        check(rose, "plate never rose");
        var apex = CocoonShatterPolicy.offset(plate, plate.releaseDelay() + 6);
        var tail = CocoonShatterPolicy.offset(plate, plate.releaseDelay() + 34);
        check(tail.y() < apex.y(), "gravity never pulled the plate down");

        // Crack growth and the shock radius are monotonic inside their beats.
        check(CocoonShatterPolicy.crackProgress(CocoonShatterPolicy.VEINS_TICKS) == 0.0,
                "crack starts at zero");
        check(CocoonShatterPolicy.crackProgress(burst - 1) > 0.5, "crack grows");
        check(CocoonShatterPolicy.shockRadius(CocoonShatterPolicy.VEINS_TICKS) == 0.0,
                "no shock before dust");
        double previous = -1.0;
        int lastDustTick = dust + CocoonShatterPolicy.DUST_TICKS;
        for (int t = dust; t <= CocoonShatterPolicy.TOTAL_TICKS; t++) {
            double radius = CocoonShatterPolicy.shockRadius(t);
            if (t < lastDustTick) {
                check(radius > previous, "shock must expand");
            } else {
                check(radius >= previous, "shock must not shrink");
            }
            check(radius <= 7.0 + 1e-6, "shock escaped the budget");
            previous = radius;
        }

        // Vein pulse is a bounded triangle and negative ticks clamp safely.
        check(CocoonShatterPolicy.veinPulse(-5) >= 0.0 && CocoonShatterPolicy.veinPulse(-5) <= 1.0,
                "negative pulse");
        check(CocoonShatterPolicy.veinPulse(0) >= 0.0, "pulse lower bound");
        check(CocoonShatterPolicy.stageAt(-9) == CocoonShatterPolicy.Stage.VEINS_PULSE,
                "negative tick clamps to the first beat");
        var nullOffset = CocoonShatterPolicy.offset(null, 4);
        check(nullOffset.x() == 0.0 && nullOffset.y() == 0.0 && nullOffset.z() == 0.0,
                "null plate has no transform");

        // The last plates dissolve instead of disappearing on one frame.
        check(CocoonShatterPolicy.revealFade(0) == 1.0, "plates visible at start");
        check(CocoonShatterPolicy.revealFade(dust + CocoonShatterPolicy.DUST_TICKS) == 1.0,
                "plates visible until the reveal");
        double previousFade = 1.0;
        for (int t = dust + CocoonShatterPolicy.DUST_TICKS + 1;
                t <= CocoonShatterPolicy.TOTAL_TICKS; t++) {
            double fade = CocoonShatterPolicy.revealFade(t);
            check(fade <= previousFade, "fade must not rise");
            check(fade >= 0.0 && fade <= 1.0, "fade range");
            previousFade = fade;
        }
        check(previousFade == 0.0, "plates must be gone by the end");

        System.out.println("OK");
    }
}
"""


@pytest.fixture(scope="module")
def probe(tmp_path_factory):
    directory = tmp_path_factory.mktemp("cocoon-shatter")
    source = directory / "CocoonShatterProbe.java"
    source.write_text(PROBE, encoding="utf-8")
    result = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(directory),
         str(DOMAIN / "CocoonShatterPolicy.java"), str(source)],
        capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    return directory


def test_cocoon_shatter_plan_is_executable(probe):
    result = subprocess.run(
        ["java", "-cp", str(probe), "CocoonShatterProbe"],
        capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    assert result.stdout.strip() == "OK"


def test_shatter_never_extends_the_fall_contract():
    """The split is cosmetic; the descent clock stays owned by CocoonFallPolicy."""
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
              ).read_text("utf-8", errors="surrogateescape")
    assert "playCocoonShatter(boss, coreBlockTopLocation())" in source
    start = source.index("private void playCocoonShatter(")
    end = source.index("private void recordTentacleGuardianSpawnFailure(", start)
    body = source[start:end]
    for forbidden in ("setType(", "setBlockData(", "damage(", "cocoonFallElapsedTicks++"):
        assert forbidden not in body, forbidden
    assert "CocoonShatterPolicy.TOTAL_TICKS" in body
    assert "CocoonShatterPolicy.offset(" in body
    assert "unregisterOwnedEntity(" in body
