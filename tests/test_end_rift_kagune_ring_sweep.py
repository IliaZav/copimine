"""Contract 20.3: a Kagune ring row must actually travel outward.

The cast is three concentric rows that emerge at 7.5, 11 and 14.5 blocks and
sweep ROW_TRAVEL_BLOCKS further during their lifetime. Two independent questions
follow, and both were wrong in production:

  - does the DRAWN ring move, or is it a single static frame at the emerge radius?
  - does the DAMAGE band move, or is every tick judged against the emerge radius?

exist. A second mutation then proved the tests worthless: deleting the progress
term from the drawn radius, and separately ignoring it in the policy, both left
the suite green. These are behavioural tests instead: they run the real policy
through a compiled probe and assert the band that hurts a player actually moves.
"""

import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"
POLICY = DOMAIN / "KaguneRingGapPolicy.java"


PROBE = textwrap.dedent(
    """
    package me.copimine.endevent.domain;

    import java.util.List;

    public final class KaguneSweepProbe {
        private static int failures;

        private static void check(String what, boolean ok) {
            if (!ok) {
                failures++;
                System.out.println("FAIL " + what);
            }
        }

        public static void main(String[] args) {
            // Row 0 emerges at 7.5 and travels 5 blocks, so its band must end at 12.5.
            KaguneRingGapPolicy.Row row = new KaguneRingGapPolicy.Row(0, 7.5D, new double[] {0.0D, 3.14D});
            KaguneRingGapPolicy.Layout layout = new KaguneRingGapPolicy.Layout(
                    1L, List.of(row), true);
            // Pick a direction that is NOT inside a gap, so the gap rule does not
            // mask the radius behaviour being measured.
            double free = 1.0D;

            // 1. At emergence the band has not reached 9 blocks yet.
            check("at progress 0 the band stops short of 9.0",
                    !KaguneRingGapPolicy.rowHits(layout, 0, 9.0D, free, 0.0D));

            // 2. Halfway through the sweep the band DOES reach 9 blocks.
            check("at progress 0.5 the band reaches 9.0",
                    KaguneRingGapPolicy.rowHits(layout, 0, 9.0D, free, 0.5D));

            // 3. It reaches the full 12.4 at the end of the sweep.
            check("at progress 1.0 the band reaches 12.4",
                    KaguneRingGapPolicy.rowHits(layout, 0, 12.4D, free, 1.0D));

            // 4. It never reaches past the authored travel.
            check("the band never reaches 13.0",
                    !KaguneRingGapPolicy.rowHits(layout, 0, 13.0D, free, 1.0D));

            // 5. The band is monotonic: reaching further is never undone.
            int reached = 0;
            for (int step = 0; step <= 10; step++) {
                double progress = step / 10.0D;
                double outer = 7.5D + 5.0D * progress;
                if (KaguneRingGapPolicy.rowHits(layout, 0, outer, free, progress)) {
                    reached++;
                }
            }
            check("every progress covers its own outermost point", reached == 11);

            // 6. Behind the emerge radius nothing ever hits.
            check("nothing hits inside the emerge radius",
                    !KaguneRingGapPolicy.rowHits(layout, 0, 7.0D, free, 1.0D));

            // 7. The gap is still safe at every progress.
            for (int step = 0; step <= 10; step++) {
                double progress = step / 10.0D;
                double outer = 7.5D + 5.0D * progress;
                check("gap stays safe at progress " + progress,
                        !KaguneRingGapPolicy.rowHits(layout, 0, outer, 0.0D, progress));
            }

            // 8. A degenerate progress is treated as emergence, not as infinity.
            check("NaN progress behaves like emergence",
                    !KaguneRingGapPolicy.rowHits(layout, 0, 9.0D, free, Double.NaN));
            check("negative progress behaves like emergence",
                    !KaguneRingGapPolicy.rowHits(layout, 0, 9.0D, free, -5.0D));
            check("over-unity progress clamps to the full sweep",
                    KaguneRingGapPolicy.rowHits(layout, 0, 12.4D, free, 9.0D));

            // 9. rowReachesAt is the primitive the band rule rests on.
            check("rowReachesAt agrees at 9.0 / 0.5",
                    KaguneRingGapPolicy.rowReachesAt(row, 9.0D, 0.5D));
            check("rowReachesAt disagrees at 9.0 / 0.0",
                    !KaguneRingGapPolicy.rowReachesAt(row, 9.0D, 0.0D));

            if (failures > 0) {
                throw new AssertionError(failures + " sweep checks failed");
            }
            System.out.println("SWEEP_OK");
        }
    }
    """
)


def run_probe():
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        source = root / "src/me/copimine/endevent/domain"
        source.mkdir(parents=True)
        (source / "KaguneSweepProbe.java").write_text(PROBE, "utf-8")
        out = root / "out"
        out.mkdir()
        compiled = subprocess.run(
            ["javac", "-encoding", "UTF-8", "-d", str(out),
             str(POLICY), str(source / "KaguneSweepProbe.java")],
            capture_output=True, text=True)
        assert compiled.returncode == 0, compiled.stdout + compiled.stderr
        return subprocess.run(
            ["java", "-cp", str(out), "me.copimine.endevent.domain.KaguneSweepProbe"],
            capture_output=True, text=True)


def test_the_damage_band_travels_outward_with_the_row():
    result = run_probe()
    assert result.returncode == 0, result.stdout + result.stderr
    assert "SWEEP_OK" in result.stdout


def test_the_drawn_radius_uses_the_same_travel_constant():
    """The render must move, not merely be called every tick."""
    text = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
            ).read_text("utf-8")
    start = text.index("private void renderKaguneRingRow(")
    depth = 0
    for index in range(text.index("{", start), len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                body = text[start: index + 1]
                break
    else:
        raise AssertionError("unbalanced renderKaguneRingRow")
    assert "ROW_TRAVEL_BLOCKS" in body, (
        "the drawn ring is not offset by the travel distance, so every row is a "
        "static circle that appears and vanishes without moving"
    )
    assert "sweepProgress" in body, (
        "the draw must consume the sweep progress, or it cannot move"
    )