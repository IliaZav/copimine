"""The Wave 2 materialization elite roll is 30%, once, and capped at two.

The spec asks each genuine phantom materialization to become an elite 30% of
the time, capped at two per wave, with exactly ONE roll per materialization.
Rolling per tick instead of per materialization would turn "30%" into "until it
happens", so the invariant is tested at the policy level.
"""
import textwrap
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"
POLICY = DOMAIN / "WaveTwoPhantomMaterializationPolicy.java"


def build_and_run(tmp_path):
    out = tmp_path / "classes"
    out.mkdir()
    probe = tmp_path / "RollProbe.java"
    probe.write_text(textwrap.dedent("""
        import me.copimine.endevent.domain.WaveTwoPhantomMaterializationPolicy;
        public final class RollProbe {
            public static void main(String[] args) {
                int elitesSeen = 0;
                for (int roll = 0; roll < 100; roll++) {
                    if (WaveTwoPhantomMaterializationPolicy.becomesElite(roll, 0)) elitesSeen++;
                }
                System.out.println("eliteWindow=" + elitesSeen);
                System.out.println("at29=" + WaveTwoPhantomMaterializationPolicy.becomesElite(29, 0));
                System.out.println("at30=" + WaveTwoPhantomMaterializationPolicy.becomesElite(30, 0));
                System.out.println("at99=" + WaveTwoPhantomMaterializationPolicy.becomesElite(99, 0));
                System.out.println("neg=" + WaveTwoPhantomMaterializationPolicy.becomesElite(-1, 0));
                System.out.println("over=" + WaveTwoPhantomMaterializationPolicy.becomesElite(100, 0));
                System.out.println("cap1=" + WaveTwoPhantomMaterializationPolicy.becomesElite(0, 1));
                System.out.println("cap2=" + WaveTwoPhantomMaterializationPolicy.becomesElite(0, 2));
                System.out.println("cap3=" + WaveTwoPhantomMaterializationPolicy.becomesElite(0, 3));
                System.out.println("pct=" + WaveTwoPhantomMaterializationPolicy.ELITE_PERCENT);
                System.out.println("max=" + WaveTwoPhantomMaterializationPolicy.MAX_ELITE_MATERIALIZATIONS);
            }
        }
    """), encoding="utf-8")
    # The policy reuses WaveTwoHuntPolicy's constants, so it has to be on the
    # same compile line; compiling the policy alone fails with "cannot find
    # symbol WaveTwoHuntPolicy".
    hunt = DOMAIN / "WaveTwoHuntPolicy.java"
    javac = subprocess.run(
        ["javac", "-d", str(out), str(POLICY), str(hunt), str(probe)],
        capture_output=True, text=True)
    assert javac.returncode == 0, javac.stderr
    run = subprocess.run(
        ["java", "-cp", str(out), "RollProbe"],
        capture_output=True, text=True)
    assert run.returncode == 0, run.stderr
    return dict(
        line.split("=", 1) for line in run.stdout.strip().splitlines() if "=" in line)


def test_the_elite_window_is_exactly_thirty_percent(tmp_path):
    result = build_and_run(tmp_path)
    assert result["eliteWindow"] == "30", (
        "the elite chance must cover exactly 30 of 100 rolls")
    assert result["pct"] == "30"


def test_the_roll_boundary_is_the_usual_half_open_interval(tmp_path):
    result = build_and_run(tmp_path)
    assert result["at29"] == "true", "roll 29 is inside the 30% window"
    assert result["at30"] == "false", "roll 30 is the first outside value"
    assert result["at99"] == "false"


def test_a_broken_roll_can_never_produce_an_elite(tmp_path):
    result = build_and_run(tmp_path)
    assert result["neg"] == "false", "an out-of-range roll must fail closed"
    assert result["over"] == "false"


def test_elites_are_capped_at_two_per_wave(tmp_path):
    result = build_and_run(tmp_path)
    assert result["cap1"] == "true", "a second elite is still allowed"
    assert result["cap2"] == "false", "the cap is reached after two"
    assert result["cap3"] == "false", "the cap cannot be exceeded"
    assert result["max"] == "2"


def test_the_roll_is_taken_once_per_phantom_not_per_tick():
    text = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(
        encoding="utf-8")
    start = text.index("private void promoteMaterializedWaveTwoEndermanToElite")
    end = text.index("private WaveTwoPhantomMaterializationPolicy.Budget currentWaveTwoPhantomBudget")
    method = text[start:end]
    assert "computeIfAbsent" in method, (
        "the roll must be stored per phantom id, so a repeated callback re-reads "
        "it instead of rerolling")
    assert "waveTwoPhantomEliteRolls" in method


def test_the_elite_count_is_reset_with_the_wave():
    text = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(
        encoding="utf-8")
    marker = text.index("waveTwoPhantomEliteRolls.clear();")
    window = text[marker:marker + 200]
    assert "waveTwoEliteMaterializations = 0;" in window, (
        "a finished Wave 2 must hand a clean elite budget to the next one")
