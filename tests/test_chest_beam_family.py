"""9H: the signature chest beam is its own authored family, not a reused lance.

The ultimate is the encounter's visual benchmark, so its contract is enforced
from three independent directions:

* the server geometry must produce a finite, charge-compressed release shaft
  that spans the real 26-block lane and is not the RIFT_LANCE silhouette;
* the socket body must be a real authored mesh with its own model id, present in
  the client mesh table and byte-identical to what the generator emits;
* the runtime must draw the shaft from the frozen locked lane and keep the true
  capsule radius marked with rails, never a thin misleading laser.
"""
import json
import subprocess
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"
EVENT = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
CLIENT = ROOT / "tools/minecraft-26.3/client/src/main/java/me/copimine/client"
MESH_DIR = (ROOT / "CopiMineClient/src/main/resources/assets/copimineclient"
            / "geometry/guardian_spells")
GENERATOR = ROOT / "CopiMineClient/tools/generate_guardian_spell_meshes.py"

PROBE = r"""
import java.util.List;
import me.copimine.endevent.domain.BossSpellGeometryPolicy;
import me.copimine.endevent.domain.ChestBeamPolicy;

public final class ChestBeamProbe {
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }

    public static void main(String[] args) {
        var shape = BossSpellGeometryPolicy.shape("chest_beam");
        check(shape == BossSpellGeometryPolicy.Shape.CHEST_BEAM, "chest_beam must map to its own shape");
        int id = BossSpellGeometryPolicy.modelData(shape);
        check(id == 830216, "chest beam mesh id changed: " + id);
        check(id != BossSpellGeometryPolicy.modelData(BossSpellGeometryPolicy.Shape.RIFT_LANCE),
                "the beam must not reuse the lance mesh");
        for (var beat : BossSpellGeometryPolicy.Beat.values()) {
            check(BossSpellGeometryPolicy.anchor(shape, beat) == BossSpellGeometryPolicy.Anchor.CHEST,
                    "chest beam anchored away from the chest at " + beat);
        }

        double longest = 0.0;
        for (var beat : BossSpellGeometryPolicy.Beat.values()) {
            for (int frame = 0; frame <= 20; frame++) {
                List<BossSpellGeometryPolicy.Point> points = BossSpellGeometryPolicy.sample(
                        shape, beat, frame / 20.0, ChestBeamPolicy.BEAM_RADIUS);
                check(!points.isEmpty(), "empty frame " + beat + " " + frame);
                check(points.size() <= BossSpellGeometryPolicy.MAX_POINTS, "point budget");
                for (var point : points) {
                    check(Double.isFinite(point.side()) && Double.isFinite(point.up())
                            && Double.isFinite(point.forward()), "non-finite point");
                    check(point.layer() >= 0 && point.layer() <= 2, "layer range");
                    longest = Math.max(longest, point.forward());
                }
            }
        }
        // The release must reach the real finite lane, not a one-block decal.
        check(longest >= 25.0, "release shaft too short: " + longest);
        check(longest <= ChestBeamPolicy.BEAM_RANGE + 2.5, "release escaped the lane: " + longest);

        // The charge beat is a compressed socket, not the same long shaft.
        double chargeReach = 0.0;
        for (var point : BossSpellGeometryPolicy.sample(shape,
                BossSpellGeometryPolicy.Beat.CHARGE, 1.0, ChestBeamPolicy.BEAM_RADIUS)) {
            chargeReach = Math.max(chargeReach, point.forward());
        }
        check(chargeReach < 2.0, "charge must stay compressed: " + chargeReach);

        // Core, shell/rings and bright nodes all exist, so the frame is not flat.
        boolean[] layers = new boolean[3];
        for (var point : BossSpellGeometryPolicy.sample(shape,
                BossSpellGeometryPolicy.Beat.RELEASE, 1.0, ChestBeamPolicy.BEAM_RADIUS)) {
            layers[point.layer()] = true;
        }
        check(layers[0] && layers[1] && layers[2], "release is missing a layer");

        var beam = BossSpellGeometryPolicy.sample(shape,
                BossSpellGeometryPolicy.Beat.RELEASE, 0.75, ChestBeamPolicy.BEAM_RADIUS);
        var lance = BossSpellGeometryPolicy.sample(BossSpellGeometryPolicy.Shape.RIFT_LANCE,
                BossSpellGeometryPolicy.Beat.RELEASE, 0.75, ChestBeamPolicy.BEAM_RADIUS);
        check(!beam.toString().equals(lance.toString()), "beam still reads as the lance");

        // A bad frame is refused rather than drawn at infinity.
        check(BossSpellGeometryPolicy.sample(shape, BossSpellGeometryPolicy.Beat.RELEASE,
                Double.NaN, ChestBeamPolicy.BEAM_RADIUS).isEmpty(), "NaN progress accepted");
        check(BossSpellGeometryPolicy.sample(shape, BossSpellGeometryPolicy.Beat.RELEASE,
                0.5, 0.0).isEmpty(), "zero radius accepted");

        System.out.println("OK");
    }
}
"""


@pytest.fixture(scope="module")
def probe(tmp_path_factory):
    directory = tmp_path_factory.mktemp("chest-beam")
    source = directory / "ChestBeamProbe.java"
    source.write_text(PROBE, encoding="utf-8")
    result = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-sourcepath", str(ROOT / "copimine-end-event/src"),
         "-d", str(directory), str(source)],
        capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    return directory


def test_chest_beam_geometry_is_authored_and_bounded(probe):
    result = subprocess.run(
        ["java", "-cp", str(probe), "ChestBeamProbe"],
        capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    assert result.stdout.strip() == "OK"


def test_chest_beam_mesh_is_generated_and_wired():
    document = json.loads((MESH_DIR / "chest_beam.json").read_text(encoding="utf-8"))
    elements = document.get("elements")
    assert isinstance(elements, list)
    assert 1 <= len(elements) <= 32, len(elements)
    for element in elements:
        low, high = element["from"], element["to"]
        for axis in range(3):
            assert -16 <= low[axis] < high[axis] <= 32, element
        assert set(element["faces"]) == {"north", "south", "east", "west", "up", "down"}
        for face in element["faces"].values():
            u = face["uv"]
            assert 0 <= min(u) and max(u) <= 16, face

    renderer = (CLIENT / "GuardianSpellRenderer.java").read_text(encoding="utf-8")
    assert 'case 830216 -> "chest_beam";' in renderer
    assert '"chest_beam"' in renderer, "socket needs a motion profile"
    names = (CLIENT / "GuardianSpellMesh.java").read_text(encoding="utf-8")
    assert '"chest_beam"' in names
    mesh_test = (ROOT / "tools/minecraft-26.3/client/src/test/java/me/copimine/client"
                 / "GuardianSpellMeshTest.java").read_text(encoding="utf-8")
    assert "AUTHORED_MESHES = 37" in mesh_test


def test_generated_meshes_match_the_generator_byte_for_byte(tmp_path):
    """Editing the mesh by hand would silently drift from the authored source."""
    result = subprocess.run(
        ["python", str(GENERATOR), "--check", "--output",
         str(ROOT / "CopiMineClient/src/main/resources")],
        capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    assert "23 bounded standalone spell meshes" in result.stdout


def test_release_draws_the_frozen_lane_with_its_true_radius():
    source = EVENT.read_text(encoding="utf-8", errors="surrogateescape")
    start = source.index("private void renderChestBeamStage(")
    end = source.index("private static Location railPoint(", start)
    body = source[start:end]
    # The shaft is sampled from the authored shape, not a flat ring.
    assert "BossSpellGeometryPolicy.Shape.CHEST_BEAM" in body
    # The true collision radius is still marked on both edges.
    assert "renderChestBeamLaneRails(" in body
    # A bounded cross-section gives the capped wire beam real volume.
    assert "ChestBeamPolicy.rail(aim" in body
    for suffix in ('"-shell-u"', '"-shell-d"', '"-shell-l"', '"-shell-r"'):
        assert suffix in body, suffix
    # Never re-aims: every drawn point comes from the frozen lane list.
    assert "selectLaneIndices" not in body
    assert "aimAt(" not in body
