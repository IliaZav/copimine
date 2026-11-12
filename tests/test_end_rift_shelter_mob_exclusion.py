"""Contract 6.6: hostile mobs cannot occupy a black-fog refuge.

``BlackFogShelterMobPolicy`` is the whole of the "no mob inside the safe zone"
rule: it decides whether a body overlaps a refuge cell, and which neighbouring
cell a stuck body should be pushed to. It had no test at all, so the rule could
be deleted outright with every gate still green.
"""

from pathlib import Path
import subprocess


ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"


def run_probe(tmp_path, name, body):
    probe = tmp_path / (name + ".java")
    probe.write_text(body, encoding="utf-8")
    files = [DOMAIN / "BlackFogShelterMobPolicy.java"]
    compiled = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path), *map(str, files), str(probe)],
        capture_output=True,
        text=True,
    )
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    executed = subprocess.run(
        ["java", "-cp", str(tmp_path), name], capture_output=True, text=True
    )
    assert executed.returncode == 0, executed.stdout + executed.stderr
    return executed.stdout


def test_overlap_uses_the_full_body_width(tmp_path):
    """A refuge cell is blocked for the whole body, not just its centre."""
    run_probe(
        tmp_path,
        "OverlapProbe",
        """
import me.copimine.endevent.domain.*;
import java.util.*;

public final class OverlapProbe {
    static void check(boolean c, String m) { if (!c) throw new AssertionError(m); }

    public static void main(String[] args) {
        List<BlackFogShelterMobPolicy.Cell> cells = List.of(
                new BlackFogShelterMobPolicy.Cell(5, 5));

        // Dead centre of the refuge: blocked.
        check(BlackFogShelterMobPolicy.overlaps(5.5, 5.5, 1.0, cells),
                "a body at the refuge centre overlaps it");
        // Body wider than a block still reaches the refuge from outside it.
        check(BlackFogShelterMobPolicy.overlaps(4.2, 5.5, 2.0, cells),
                "a wide body beside the refuge still overlaps it");
        // The same position with a thin body does NOT overlap: this is the
        // assertion that fails if the width term is dropped.
        check(!BlackFogShelterMobPolicy.overlaps(4.2, 5.5, 0.2, cells),
                "a thin body a full block away does not overlap");
        check(!BlackFogShelterMobPolicy.overlaps(0.5, 0.5, 1.0, cells),
                "a body far away does not overlap");
        check(!BlackFogShelterMobPolicy.overlaps(5.5, 5.5, 1.0, List.of()),
                "no refuges means nothing overlaps");

        // Degenerate input fails CLOSED: refuse the body rather than let it in.
        check(BlackFogShelterMobPolicy.overlaps(Double.NaN, 5.5, 1.0, cells),
                "a NaN position is refused");
        check(BlackFogShelterMobPolicy.overlaps(5.5, Double.POSITIVE_INFINITY, 1.0, cells),
                "an infinite position is refused");
        check(BlackFogShelterMobPolicy.overlaps(5.5, 5.5, 0.0, cells),
                "a zero width is refused");
        check(BlackFogShelterMobPolicy.overlaps(5.5, 5.5, -1.0, cells),
                "a negative width is refused");
        check(BlackFogShelterMobPolicy.overlaps(5.5, 5.5, Double.NaN, cells),
                "a NaN width is refused");
    }
}
""",
    )


def test_exit_finds_a_walkable_cell_outside_every_refuge(tmp_path):
    """A mob standing in a refuge is relocated, not merely flagged."""
    run_probe(
        tmp_path,
        "ExitProbe",
        """
import me.copimine.endevent.domain.*;
import java.util.*;

public final class ExitProbe {
    static void check(boolean c, String m) { if (!c) throw new AssertionError(m); }

    public static void main(String[] args) {
        List<BlackFogShelterMobPolicy.Cell> cells = List.of(
                new BlackFogShelterMobPolicy.Cell(5, 5));
        java.util.function.BiPredicate<Integer,Integer> allWalkable = (x, y) -> true;

        // Outside a refuge there is nothing to exit.
        check(BlackFogShelterMobPolicy.exit(0.5, 0.5, 1.0, cells, allWalkable) == null,
                "a body outside every refuge needs no exit");

        // Inside one, the chosen cell must itself be outside every refuge and
        // walkable, and it must be adjacent-ish rather than across the arena.
        BlackFogShelterMobPolicy.Cell out =
                BlackFogShelterMobPolicy.exit(5.5, 5.5, 1.0, cells, allWalkable);
        check(out != null, "a body inside a refuge must be given an exit");
        check(!BlackFogShelterMobPolicy.overlaps(out.x() + .5, out.z() + .5, 1.0, cells),
                "the exit cell is outside every refuge");
        double hop = Math.hypot(out.x() + .5 - 5.5, out.z() + .5 - 5.5);
        check(hop <= 3.0, "the exit is nearby, not across the arena: " + hop);

        // When every neighbour is unwalkable, walking is refused rather than
        // teleporting the mob into a wall.
        java.util.function.BiPredicate<Integer,Integer> noneWalkable = (x, y) -> false;
        check(BlackFogShelterMobPolicy.exit(5.5, 5.5, 1.0, cells, noneWalkable) == null,
                "no walkable cell means no exit");

        // Degenerate input fails closed.
        check(BlackFogShelterMobPolicy.exit(Double.NaN, 5.5, 1.0, cells, allWalkable) == null,
                "a NaN position has no exit");
        check(BlackFogShelterMobPolicy.exit(5.5, 5.5, 0.0, cells, allWalkable) == null,
                "a zero width has no exit");

        // Two adjacent refuges must both be excluded, not just the nearest.
        List<BlackFogShelterMobPolicy.Cell> pair = List.of(
                new BlackFogShelterMobPolicy.Cell(5, 5),
                new BlackFogShelterMobPolicy.Cell(6, 5));
        BlackFogShelterMobPolicy.Cell out2 =
                BlackFogShelterMobPolicy.exit(5.5, 5.5, 1.0, pair, allWalkable);
        check(out2 != null, "a body in a pair of refuges must be given an exit");
        check(!BlackFogShelterMobPolicy.overlaps(out2.x() + .5, out2.z() + .5, 1.0, pair),
                "the exit clears BOTH refuges, not only the one it stood in");
    }
}
""",
    )
