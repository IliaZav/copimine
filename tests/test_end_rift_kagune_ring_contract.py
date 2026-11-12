"""Master contract 20 / 21: the three-row expanding Kagune ring.

Geometry, randomness and survivability are all owned by
``KaguneRingGapPolicy``; these tests drive the real class through javac + java.
"""

from pathlib import Path
import subprocess


ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"

SOURCES = ["KaguneRingGapPolicy", "KaguneRingCastPolicy"]

# The contract's own minimum offset between two rows, written out here so a
# relaxed policy constant cannot make this file agree with itself.
REQUIRED_CROSS_ROW_FLOOR_RAD = 0.12


def run_probe(tmp_path, name, body):
    probe = tmp_path / (name + ".java")
    probe.write_text(body, encoding="utf-8")
    files = [DOMAIN / (stem + ".java") for stem in SOURCES]
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


def test_gap_count_scales_with_the_party(tmp_path):
    """20.2: more players means more gaps, inside the contract brackets."""
    run_probe(
        tmp_path,
        "GapCountProbe",
        """
import me.copimine.endevent.domain.*;

public class GapCountProbe {
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(KaguneRingGapPolicy.gapCount(2) == 2, "two to three players get two gaps");
        check(KaguneRingGapPolicy.gapCount(3) == 2, "three players get two gaps");
        check(KaguneRingGapPolicy.gapCount(4) == 3, "four to five players get three gaps");
        check(KaguneRingGapPolicy.gapCount(5) == 3, "five players get three gaps");
        check(KaguneRingGapPolicy.gapCount(6) == 4, "six to ten players get four gaps");
        check(KaguneRingGapPolicy.gapCount(10) == 4, "ten players get four gaps");
        check(KaguneRingGapPolicy.gapCount(11) >= 5, "eleven players get five or more gaps");
        check(KaguneRingGapPolicy.gapCount(20) <= 7, "the contract caps gaps at seven");
        check(KaguneRingGapPolicy.gapCount(20) > KaguneRingGapPolicy.gapCount(11),
                "a bigger party keeps getting more gaps");
        check(KaguneRingGapPolicy.gapCount(0) == 0, "no party means no ring");

        int previous = 0;
        for (int players = 1; players <= 20; players++) {
            int gaps = KaguneRingGapPolicy.gapCount(players);
            check(gaps >= previous, "gap count never shrinks as the party grows");
            previous = gaps;
        }
    }
}
""",
    )


def test_layout_is_never_impossible_and_never_one_lane(tmp_path):
    """20.1 / 20.2 / 20.3: frozen, staggered and survivable for every party."""
    output = run_probe(
        tmp_path,
        "LayoutProbe",
        """
import java.util.*;
import me.copimine.endevent.domain.*;

public class LayoutProbe {
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        double radius = KaguneRingGapPolicy.ROW_EMERGE_RADIUS[0];
        check(KaguneRingGapPolicy.maxFittingGaps(radius) >= 7,
                "the innermost ring must host the contract's gap counts");
        for (int players = 1; players <= 20; players++) {
            check(KaguneRingGapPolicy.patternPossible(players, radius),
                    "a party of " + players + " must always be survivable");
        }

        for (int players = 2; players <= 20; players++) {
            int gaps = KaguneRingGapPolicy.gapCount(players);
            for (long seed = 1; seed <= 120; seed++) {
                var layout = KaguneRingGapPolicy.layout(seed, players, List.of(), List.of());
                check(layout.feasible(), "layout refused for " + players + " seed " + seed);
                check(layout.rows().size() == KaguneRingGapPolicy.ROW_COUNT,
                        "the cast must have three rows");
                check(!layout.sharesEscapeLane(),
                        "rows share a straight lane for " + players + "/" + seed);
                check(KaguneRingGapPolicy.hasSurvivableGap(layout),
                        "a row exposes no gap for " + players + "/" + seed);
                for (int index = 0; index < KaguneRingGapPolicy.ROW_COUNT; index++) {
                    var row = layout.row(index);
                    check(row.gapCount() == gaps, "wrong gap count for " + players + "/" + seed);
                    check(Math.abs(row.emergeRadius()
                            - KaguneRingGapPolicy.ROW_EMERGE_RADIUS[index]) < 1.0e-9,
                            "row radius drifted");
                    double[] centres = row.gapCentres();
                    for (int a = 0; a < centres.length; a++) {
                        for (int b = a + 1; b < centres.length; b++) {
                            double distance = KaguneRingGapPolicy.angleDistance(
                                    centres[a], centres[b]) * radius;
                            check(distance >= KaguneRingGapPolicy.MIN_GAP_SEPARATION_BLOCKS,
                                    "two gaps of one row are only " + distance + " apart");
                        }
                    }
                }
                // Independent of the policy's own constant: measure the gap
                // centres themselves, so lowering the threshold cannot hide a
                // shared lane from this test.
                for (int a = 0; a < layout.rows().size(); a++) {
                    for (int b = a + 1; b < layout.rows().size(); b++) {
                        double[] left = layout.row(a).gapCentres();
                        double[] right = layout.row(b).gapCentres();
                        for (double one : left) {
                            for (double two : right) {
                                double offset = KaguneRingGapPolicy.angleDistance(one, two);
                                check(offset >= 0.12D,
                                        "rows " + a + " and " + b + " share a lane ("
                                                + offset + " rad)");
                                check(!KaguneRingGapPolicy.sameDirection(one, two),
                                        "rows " + a + " and " + b + " share a lane");
                            }
                        }
                    }
                }
            }
        }
    }
}
""",
    )
    assert "AssertionError" not in output


def test_layout_is_seeded_frozen_and_off_the_players(tmp_path):
    """20.2: random per cast, deterministic per seed, never on a player."""
    run_probe(
        tmp_path,
        "DeterminismProbe",
        """
import java.util.*;
import me.copimine.endevent.domain.*;

public class DeterminismProbe {
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    static boolean same(double[] a, double[] b) { return Arrays.equals(a, b); }

    public static void main(String[] args) {
        var first = KaguneRingGapPolicy.layout(4242L, 6, List.of(), List.of());
        var second = KaguneRingGapPolicy.layout(4242L, 6, List.of(), List.of());
        check(first.feasible() && second.feasible(), "the reference cast is feasible");
        for (int row = 0; row < first.rows().size(); row++) {
            check(same(first.row(row).gapCentres(), second.row(row).gapCentres()),
                    "the same seed must yield the same frozen layout");
        }

        boolean differed = false;
        for (long seed = 4243L; seed < 4290L; seed++) {
            var other = KaguneRingGapPolicy.layout(seed, 6, List.of(), List.of());
            if (!same(first.row(0).gapCentres(), other.row(0).gapCentres())) differed = true;
        }
        check(differed, "different seeds must give different gap positions");

        List<Double> players = new ArrayList<>();
        for (int index = 0; index < 6; index++) players.add(index * 1.0D);
        for (long seed = 1; seed <= 40; seed++) {
            var layout = KaguneRingGapPolicy.layout(seed, 6, players, List.of());
            check(layout.feasible(), "players must not make the cast impossible");
            double radius = KaguneRingGapPolicy.ROW_EMERGE_RADIUS[0];
            for (var row : layout.rows()) {
                for (double centre : row.gapCentres()) {
                    for (double player : players) {
                        double offset = KaguneRingGapPolicy.angleDistance(centre, player) * radius;
                        check(offset >= KaguneRingGapPolicy.MIN_PLAYER_OFFSET_BLOCKS - 1.0e-9,
                                "a gap was centred on a player (" + offset + ")");
                    }
                }
            }
        }

        List<Double> walls = new ArrayList<>();
        walls.add(0.5D);
        walls.add(2.5D);
        for (long seed = 1; seed <= 40; seed++) {
            var layout = KaguneRingGapPolicy.layout(seed, 6, List.of(), walls);
            check(layout.feasible(), "walls must not make the cast impossible");
            double radius = KaguneRingGapPolicy.ROW_EMERGE_RADIUS[0];
            for (var row : layout.rows()) {
                for (double centre : row.gapCentres()) {
                    for (double wall : walls) {
                        double offset = KaguneRingGapPolicy.angleDistance(centre, wall) * radius;
                        check(offset >= KaguneRingGapPolicy.GAP_WIDTH_BLOCKS - 1.0e-9,
                                "a gap sits inside a wall (" + offset + ")");
                    }
                }
            }
        }
    }
}
""",
    )


def test_one_damage_event_per_player_per_cast(tmp_path):
    """20.3: a cast hits each player at most once, however many rows pass."""
    run_probe(
        tmp_path,
        "CastProbe",
        """
import java.util.*;
import me.copimine.endevent.domain.*;

public class CastProbe {
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        var cast = new KaguneRingCastPolicy();
        UUID player = UUID.nameUUIDFromBytes("player".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        UUID other = UUID.nameUUIDFromBytes("other".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        check(cast.claim(player), "the first row hits the player");
        check(cast.alreadyHit(player), "the claim is remembered");
        check(!cast.claim(player), "the second and third rows cannot hit again");
        check(cast.claim(other), "another player has their own hit");
        check(cast.hitCount() == 2, "two players were hit once each");
        check(cast.struckPlayers().contains(player) && cast.struckPlayers().contains(other),
                "both hits are recorded");

        for (int row = 0; row < 3; row++) cast.claim(player);
        check(cast.hitCount() == 2, "repeated rows never add a hit");

        check(!cast.claim(null), "a null player is refused");
        var fresh = new KaguneRingCastPolicy();
        check(!fresh.claim(null), "a null player is refused on a fresh cast too");
        check(fresh.hitCount() == 0, "a refused claim changes nothing");

        cast.reset();
        check(cast.hitCount() == 0, "a new cast starts empty");
        check(cast.claim(player), "the player can be hit by the next cast");

        check(!KaguneRingCastPolicy.castComplete(2), "two rows are not the whole cast");
        check(KaguneRingCastPolicy.castComplete(3), "three finished rows end the cast");
    }
}
""",
    )


def test_row_hit_rule_offers_a_real_escape(tmp_path):
    """20.1 / 20.3: the striking row misses players standing in its gap."""
    run_probe(
        tmp_path,
        "HitRuleProbe",
        """
import java.util.*;
import me.copimine.endevent.domain.*;

public class HitRuleProbe {
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        var layout = KaguneRingGapPolicy.layout(777L, 6, List.of(), List.of());
        check(layout.feasible(), "the reference cast is feasible");

        for (int row = 0; row < layout.rows().size(); row++) {
            var current = layout.row(row);
            double inner = current.emergeRadius();
            double outer = inner + KaguneRingGapPolicy.ROW_TRAVEL_BLOCKS;
            double[] centres = current.gapCentres();
            double halfWidth = KaguneRingGapPolicy.halfWidthRad(inner);
            // The gap really is TWO BLOCKS wide: a player half a block off the
            // centre is inside it, a player 1.5 blocks off is not.
            double block = 1.0D / inner;
            check(KaguneRingGapPolicy.insideGap(current, centres[0] + block),
                    "a player less than one block off the centre must be inside the gap");
            check(!KaguneRingGapPolicy.insideGap(current, centres[0] + block * 1.5D),
                    "a player more than one block off the centre must be outside the gap");
            for (double centre : centres) {
                check(!KaguneRingGapPolicy.rowHits(layout, row, inner + 1.0D,
                                centre + block * 0.9D),
                        "the gap is narrower than two blocks");
                check(KaguneRingGapPolicy.rowHits(layout, row, inner + 1.0D,
                                centre + block * 1.4D),
                        "the gap is wider than two blocks");
            }

            for (double centre : centres) {
                check(!KaguneRingGapPolicy.rowHits(layout, row, inner + 1.0D, centre),
                        "a player inside the gap was still hit");
                check(KaguneRingGapPolicy.insideGap(current, centre),
                        "the gap centre is not inside its own gap");
                check(KaguneRingGapPolicy.rowHits(layout, row, inner + 1.0D,
                                centre + halfWidth * 3.0D),
                        "the ring must hit beside its gaps");
            }

            check(!KaguneRingGapPolicy.rowHits(layout, row, inner - 2.0D, centres[0]),
                    "a player inside the row's start radius is not hit");
            check(!KaguneRingGapPolicy.rowHits(layout, row, outer + 2.0D, centres[0]),
                    "a player beyond the row's reach is not hit");

            check(KaguneRingGapPolicy.rowReaches(current, inner), "the row reaches its start");
            check(KaguneRingGapPolicy.rowReaches(current, outer), "the row reaches its end");
            check(!KaguneRingGapPolicy.rowReaches(current, inner - 0.5D),
                    "the row does not reach inward");
            check(!KaguneRingGapPolicy.rowReaches(current, outer + 0.5D),
                    "the row does not reach outward");
            // The sweep really is about five blocks, not merely finite.
            check(Math.abs(KaguneRingGapPolicy.ROW_TRAVEL_BLOCKS - 5.0D) < 1.0e-9,
                    "a row travels about five blocks");
            check(Math.abs((outer - inner) - 5.0D) < 1.0e-9,
                    "the row band must be five blocks deep");
            check(!KaguneRingGapPolicy.rowReaches(current, inner + 5.4D),
                    "the row must not sweep further than five blocks");
            check(KaguneRingGapPolicy.rowReaches(current, inner + 4.6D),
                    "the row must sweep at least five blocks");
            check(!KaguneRingGapPolicy.rowReaches(current, Double.NaN),
                    "a broken radius is refused");
        }
        check(!KaguneRingGapPolicy.rowHits(layout, 3, 1.0D, 0.0D), "row 3 does not exist");
    }
}
""",
    )

def test_ring_policy_survives_degenerate_inputs(tmp_path):
    """20.2: a broken arena or a broken payload refuses the cast, never crashes."""
    run_probe(
        tmp_path,
        "EdgeProbe",
        """
import java.util.*;
import me.copimine.endevent.domain.*;

public class EdgeProbe {
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(!KaguneRingGapPolicy.patternPossible(20, 1.0), "a tiny ring must be refused");
        check(KaguneRingGapPolicy.maxFittingGaps(1.0) < 7, "a tiny ring fits fewer than seven");
        for (int players = 1; players <= 20; players++) {
            check(KaguneRingGapPolicy.patternPossible(players, 7.5),
                    "the real ring must host a party of " + players);
        }
        check(KaguneRingGapPolicy.maxFittingGaps(0.0) == 0, "zero radius fits nothing");
        check(KaguneRingGapPolicy.maxFittingGaps(-5.0) == 0, "negative radius fits nothing");
        check(KaguneRingGapPolicy.halfWidthRad(0.0) == Math.PI, "zero radius is degenerate");
        check(KaguneRingGapPolicy.gapCount(-3) == 0, "a negative party is refused");
        check(KaguneRingGapPolicy.gapCount(200) <= 7, "a huge party is still capped at seven");

        var layout = KaguneRingGapPolicy.layout(1L, 6, null, null);
        check(layout.feasible(), "null player and wall lists must not break the cast");
        check(!KaguneRingGapPolicy.rowHits(null, 0, 8.0, 0.0), "a null layout is refused");
        check(!KaguneRingGapPolicy.rowHits(layout, 99, 8.0, 0.0), "a missing row is refused");
        check(!KaguneRingGapPolicy.hasSurvivableGap(null), "a null layout has no gap");
        check(!KaguneRingGapPolicy.insideGap(null, 0.0), "a null row has no gap");
        check(!KaguneRingGapPolicy.rowReaches(null, 8.0), "a null row reaches nothing");
        check(Math.abs(KaguneRingGapPolicy.wrap(Math.PI * 3)) <= Math.PI,
                "wrap stays inside the range");
        check(KaguneRingGapPolicy.wrap(-Math.PI * 3) > -Math.PI,
                "wrap stays above minus pi");
    }
}
""",
    )


def test_no_straight_escape_lane_is_detected(tmp_path):
    """20.2: two gap centres that line up are one lane, and must be seen as such.

    This is the assertion the earlier suite was missing. Mutating
    ``sameDirection`` to always return false, or zeroing
    ``MIN_GAP_SEPARATION_BLOCKS``, previously left every test green.
    """
    run_probe(
        tmp_path,
        "LaneProbe",
        """
import me.copimine.endevent.domain.*;

public final class LaneProbe {
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        double floor = KaguneRingGapPolicy.CROSS_ROW_FLOOR_RAD;
        check(Math.abs(floor - __FLOOR__) < 1e-9,
                "the contract floor is 0.12 rad, found " + floor);

        check(KaguneRingGapPolicy.sameDirection(0.5, 0.5),
                "two gaps at the same angle are the same lane");
        check(KaguneRingGapPolicy.sameDirection(0.5, 0.5 + floor * 0.5),
                "half the floor apart is still the same lane");
        check(!KaguneRingGapPolicy.sameDirection(0.5, 0.5 + floor * 2.0),
                "twice the floor apart is a distinct lane");
        check(!KaguneRingGapPolicy.sameDirection(0.5, 0.5 + Math.PI),
                "opposite sides of the ring are distinct lanes");

        double wrapped = (Math.PI * 2.0) - 0.001;
        check(KaguneRingGapPolicy.sameDirection(0.001, wrapped),
                "the seam must be measured the short way round");

        double near = KaguneRingGapPolicy.minCentreSeparationRad(12.0);
        double far = KaguneRingGapPolicy.minCentreSeparationRad(24.0);
        check(near > far, "a smaller ring needs a wider angular separation");
        check(KaguneRingGapPolicy.minCentreSeparationRad(0.0) == 2.0 * Math.PI,
                "a zero radius is refused rather than dividing by zero");

        // The separation is not decoration: on a small ring it is the only
        // thing that keeps the gap count down. With a 2-block gap and 3-block
        // separation a radius-2 ring fits exactly 2 gaps; drop the separation
        // and it fits 6. That difference is what the 5.0 divisor buys.
        check(KaguneRingGapPolicy.maxFittingGaps(2.0) == 2,
                "a radius-2 ring fits exactly 2 separated gaps, got "
                        + KaguneRingGapPolicy.maxFittingGaps(2.0));
        check(KaguneRingGapPolicy.maxFittingGaps(3.0) == 3,
                "a radius-3 ring fits exactly 3 separated gaps, got "
                        + KaguneRingGapPolicy.maxFittingGaps(3.0));
        check(KaguneRingGapPolicy.maxFittingGaps(4.0) == 5,
                "a radius-4 ring fits exactly 5 separated gaps, got "
                        + KaguneRingGapPolicy.maxFittingGaps(4.0));
        check(KaguneRingGapPolicy.MIN_GAP_SEPARATION_BLOCKS == 3.0,
                "the contract keeps a 3-block separation, found "
                        + KaguneRingGapPolicy.MIN_GAP_SEPARATION_BLOCKS);
        check(KaguneRingGapPolicy.GAP_WIDTH_BLOCKS == 2.0,
                "the contract keeps two-block gaps, found "
                        + KaguneRingGapPolicy.GAP_WIDTH_BLOCKS);
        int wideFit = KaguneRingGapPolicy.maxFittingGaps(60.0);
        int narrowFit = KaguneRingGapPolicy.maxFittingGaps(8.0);
        check(wideFit >= narrowFit,
                "a wider ring never fits fewer gaps than a narrow one");
        check(KaguneRingGapPolicy.maxFittingGaps(0.0) == 0,
                "a zero radius fits no gaps");
    }
}
""".replace("__FLOOR__", repr(REQUIRED_CROSS_ROW_FLOOR_RAD)),
    )
