"""Wave 4 substitutes rejected obelisk sites instead of aborting the wave.

The observed failure was "music plays, nothing spawns". The startup planned
exactly profile.obeliskCount() anchors, and a single site refused by the safety
policy left planned.size() != obeliskCount(), which sets currentWave4StartupFailed
and returns. The arena then looked active with no objective in it.

ObeliskPlacementPolicy.spareCandidates() supplies deterministic substitutes
from the same attempt seed, so a refused site can be replaced and recovery
replays the identical substitute layout.
"""
import textwrap
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def run_probe(tmp_path: Path) -> dict:
    out = tmp_path / "classes"
    out.mkdir()
    probe = tmp_path / "SpareProbe.java"
    probe.write_text(textwrap.dedent("""
        import me.copimine.endevent.domain.ObeliskPlacementPolicy;
        import java.util.List;

        public final class SpareProbe {
            private static void check(String label, boolean value) {
                if (!value) throw new AssertionError(label);
            }

            public static void main(String[] args) {
                long seed = 424242L;
                List<ObeliskPlacementPolicy.Point> primary =
                        ObeliskPlacementPolicy.scatter(4, seed, 18.0D);
                check("the primary scatter returns exactly the roster count", primary.size() == 4);

                List<ObeliskPlacementPolicy.Point> spares =
                        ObeliskPlacementPolicy.spareCandidates(6, seed, 18.0D, primary);
                check("spares are produced", spares.size() == 6);

                // A substitute must never sit on a planned anchor or on another spare,
                // otherwise the fallback collapses two obelisks onto one site.
                for (ObeliskPlacementPolicy.Point spare : spares) {
                    check("a spare never reuses a planned anchor", !primary.contains(spare));
                }
                for (int i = 0; i < spares.size(); i++) {
                    for (int j = i + 1; j < spares.size(); j++) {
                        check("spares are distinct", !spares.get(i).equals(spares.get(j)));
                    }
                }

                // Same seed, same answer: recovery must replay the substitutes.
                check("spares are deterministic",
                        spares.equals(ObeliskPlacementPolicy.spareCandidates(6, seed, 18.0D, primary)));
                check("a different seed draws different spares",
                        !spares.equals(ObeliskPlacementPolicy.spareCandidates(6, seed + 1, 18.0D, primary)));

                // A degenerate band must still yield anchors rather than an empty list.
                List<ObeliskPlacementPolicy.Point> rescued =
                        ObeliskPlacementPolicy.spareCandidates(3, seed, Double.NaN, List.of());
                check("an unmeasurable arena still yields spares", rescued.size() == 3);

                check("zero requested yields nothing",
                        ObeliskPlacementPolicy.spareCandidates(0, seed, 18.0D, primary).isEmpty());
                check("negative requested yields nothing",
                        ObeliskPlacementPolicy.spareCandidates(-4, seed, 18.0D, primary).isEmpty());
                check("a null taken list is tolerated",
                        ObeliskPlacementPolicy.spareCandidates(2, seed, 18.0D, null).size() == 2);
            }
        }
    """), encoding="utf-8")
    # ObeliskScalingPolicy reuses ObeliskFireDirectorPolicy constants, so those
    # three files are the minimal compile unit. Pulling in the whole domain
    # package drags unrelated classes with their own dependencies and fails.
    sources = [DOMAIN / name for name in (
        "ObeliskPlacementPolicy.java",
        "ObeliskScalingPolicy.java",
        "ObeliskFireDirectorPolicy.java")]
    compiled = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(out), *map(str, sources), str(probe)],
        capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    ran = subprocess.run(["java", "-cp", str(out), "SpareProbe"],
                         capture_output=True, text=True)
    assert ran.returncode == 0, ran.stdout + ran.stderr
    return {}


def test_spares_are_deterministic_distinct_and_never_reuse_a_planned_site(tmp_path):
    run_probe(tmp_path)


def test_the_spare_rule_keeps_min_distance_from_planned_sites():
    text = (DOMAIN / "ObeliskPlacementPolicy.java").read_text(encoding="utf-8")
    start = text.index("public static List<Point> spareCandidates(")
    body = text[start:text.index("    /** Fixed-seed scatter", start)]
    assert "occupied.stream().anyMatch(other ->" in body, (
        "a spare must be kept MIN_DISTANCE away from the primary anchors, not "
        "merely differ from them: two obelisks four blocks apart after "
        "substitution would break the approach-path contract")
    assert "obligatory" not in body
    assert "MIN_DISTANCE" in body


def test_the_startup_iterates_substitutes_instead_of_failing():
    text = SOURCE.read_text(encoding="utf-8")
    start = text.index("private void startWave4ObeliskAssault(")
    body = text[start:text.index("if (planned.size() != profile.obeliskCount())", start)]
    assert "ObeliskPlacementPolicy.spareCandidates(" in body, (
        "without substitutes a single refused site aborts the wave")
    assert "candidates.addAll(spares);" in body
    assert "for (ObeliskPlacementPolicy.Point point : candidates)" in body, (
        "the loop must walk the primary anchors AND the spares")


def test_the_failure_path_is_still_reachable_for_a_truly_impossible_arena():
    text = SOURCE.read_text(encoding="utf-8")
    start = text.index("private void startWave4ObeliskAssault(")
    body = text[start:start + 9000]
    assert "planned.size() != profile.obeliskCount()" in body, (
        "a controlled refusal must stay possible; the fix adds options, it does "
        "not pretend placement can always succeed")
    assert "currentWave4StartupFailed = true" in body
