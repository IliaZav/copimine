"""N23-P1-06/N23-P1-08: the geyser, the floor rift and the inferno column are
no longer one shared body.

Measured on the production lineage, ``BossSpellGeometryPolicy.shape`` sent
``kagune_eruption``, ``rift_fracture`` and ``inferno_geyser`` all to a single
``Shape.GEYSER`` whose ``modelData`` was the same ``830207`` fault fragment the
ground slam used. A rock eruption, a cut in the floor and a pressurised fire
column were therefore the same visible object -- the "sharing the main visible
object/attack identity is not [allowed]" case the spec calls out.

This walks the whole chain for each hazard independently: enum arm, mesh
selector, an authored, bounded and distinct silhouette, the renderer table,
``NAMES`` and the generated JSON. It proves authored geometry, not a Minecraft
visual; the F2 screenshots stay an owner acceptance.
"""
import json
import subprocess
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"
CLIENT = ROOT / "tools/minecraft-26.3/client/src/main/java/me/copimine/client"
MESH_DIR = (ROOT / "CopiMineClient/src/main/resources/assets/copimineclient"
            / "geometry/guardian_spells")
GENERATOR = ROOT / "CopiMineClient/tools/generate_guardian_spell_meshes.py"

EXPECTED = {
    "geyser": ("kagune_eruption", 830217),
    "rift_fracture": ("rift_fracture", 830218),
    "inferno_geyser": ("inferno_geyser", 830219),
}

PROBE = """
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import me.copimine.endevent.domain.BossSpellGeometryPolicy;

public final class GeyserFamilyProbe {
    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    static void body(String id, BossSpellGeometryPolicy.Shape shape, int model) {
        BossSpellGeometryPolicy.Shape resolved = BossSpellGeometryPolicy.shape(id);
        check(resolved == shape, id + " maps to " + resolved + ", not " + shape);
        check(BossSpellGeometryPolicy.modelData(resolved) == model,
                id + " mesh id changed: " + BossSpellGeometryPolicy.modelData(resolved));
        for (var beat : BossSpellGeometryPolicy.Beat.values()) {
            check(BossSpellGeometryPolicy.anchor(resolved, beat)
                            == BossSpellGeometryPolicy.Anchor.LOCKED_FLOOR,
                    id + " is not a floor hazard at " + beat);
            for (int frame = 0; frame <= 20; frame++) {
                List<BossSpellGeometryPolicy.Point> points = BossSpellGeometryPolicy.sample(
                        resolved, beat, frame / 20.0, 4.0);
                check(!points.isEmpty(), id + " empty at " + beat + " " + frame);
                check(points.size() <= BossSpellGeometryPolicy.MAX_POINTS, id + " unbounded");
                double minSide = Double.MAX_VALUE, maxSide = -Double.MAX_VALUE;
                double minUp = Double.MAX_VALUE, maxUp = -Double.MAX_VALUE;
                double minFwd = Double.MAX_VALUE, maxFwd = -Double.MAX_VALUE;
                for (var point : points) {
                    check(Double.isFinite(point.side() + point.up() + point.forward()),
                            id + " non-finite point");
                    check(point.layer() >= 0 && point.layer() <= 2, id + " bad layer");
                    minSide = Math.min(minSide, point.side());
                    maxSide = Math.max(maxSide, point.side());
                    minUp = Math.min(minUp, point.up());
                    maxUp = Math.max(maxUp, point.up());
                    minFwd = Math.min(minFwd, point.forward());
                    maxFwd = Math.max(maxFwd, point.forward());
                }
                check(maxSide - minSide > 0.10, id + " has no side extent at " + beat);
                check(maxUp - minUp > 0.10, id + " has no vertical extent at " + beat);
                check(maxFwd - minFwd > 0.10, id + " has no forward extent at " + beat);
            }
        }
    }

    public static void main(String[] args) {
        body("kagune_eruption", BossSpellGeometryPolicy.Shape.GEYSER, 830217);
        body("rift_fracture", BossSpellGeometryPolicy.Shape.RIFT_FRACTURE, 830218);
        body("inferno_geyser", BossSpellGeometryPolicy.Shape.INFERNO_GEYSER, 830219);

        // The ground slam keeps its own fragment, so the eruption never shares
        // a body with it again.
        check(BossSpellGeometryPolicy.modelData(BossSpellGeometryPolicy.Shape.SLAM_FAULT) == 830207,
                "the ground slam lost the fault fragment");
        check(BossSpellGeometryPolicy.modelData(BossSpellGeometryPolicy.Shape.SLAM_FAULT)
                        != BossSpellGeometryPolicy.modelData(BossSpellGeometryPolicy.Shape.GEYSER),
                "the eruption still shares the ground-slam body");
        check(BossSpellGeometryPolicy.modelData(BossSpellGeometryPolicy.Shape.GEYSER)
                        != BossSpellGeometryPolicy.modelData(BossSpellGeometryPolicy.Shape.INFERNO_GEYSER),
                "the rock eruption still shares the fire column");

        // The three hazards are three genuinely different silhouettes.
        Set<String> distinct = new HashSet<>();
        for (var shape : List.of(BossSpellGeometryPolicy.Shape.GEYSER,
                BossSpellGeometryPolicy.Shape.RIFT_FRACTURE,
                BossSpellGeometryPolicy.Shape.INFERNO_GEYSER)) {
            check(distinct.add(BossSpellGeometryPolicy.sample(shape,
                    BossSpellGeometryPolicy.Beat.CHARGE, 0.63, 4.0).toString()),
                    "two hazards share a silhouette: " + shape);
        }

        System.out.println("OK");
    }
}
"""


@pytest.fixture(scope="module")
def probe(tmp_path_factory):
    directory = tmp_path_factory.mktemp("geyser-family")
    source = directory / "GeyserFamilyProbe.java"
    source.write_text(PROBE, encoding="utf-8")
    result = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-sourcepath", str(ROOT / "copimine-end-event/src"),
         "-d", str(directory), str(source)],
        capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    return directory


def test_geyser_family_geometry_is_authored_bounded_and_distinct(probe):
    result = subprocess.run(
        ["java", "-cp", str(probe), "GeyserFamilyProbe"],
        capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    assert result.stdout.strip() == "OK"


def test_geyser_family_meshes_are_generated_and_wired():
    renderer = (CLIENT / "GuardianSpellRenderer.java").read_text(encoding="utf-8")
    names = (CLIENT / "GuardianSpellMesh.java").read_text(encoding="utf-8")
    for name, (_, model) in EXPECTED.items():
        assert f'case {model} -> "{name}";' in renderer, (name, model)
        assert f'"{name}"' in names, name
        assert f'case "{name}" -> new Motion(' in renderer, f"{name} needs a motion profile"
        document = json.loads((MESH_DIR / f"{name}.json").read_text(encoding="utf-8"))
        elements = document.get("elements")
        assert isinstance(elements, list)
        assert 1 <= len(elements) <= 32, (name, len(elements))
        for element in elements:
            low, high = element["from"], element["to"]
            for axis in range(3):
                assert -16 <= low[axis] < high[axis] <= 32, (name, element)
            assert set(element["faces"]) == {"north", "south", "east", "west", "up", "down"}
            for face in element["faces"].values():
                u = face["uv"]
                assert 0 <= min(u) and max(u) <= 16, face


def test_geyser_family_meshes_match_the_generator_byte_for_byte():
    result = subprocess.run(
        ["python", str(GENERATOR), "--check", "--output",
         str(ROOT / "CopiMineClient/src/main/resources")],
        capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr


def _method(source, signature):
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[start : index + 1]
    raise AssertionError(f"unterminated method: {signature}")


def test_the_phase_three_hazards_spawn_their_authored_bodies():
    """The authored bodies must be reachable, not dead JSON.

    ``kagune_eruption`` already renders through the generic boss-spell body, but
    the Phase 3 crack and column have their own runtime. This pins the two
    spawns so the meshes cannot silently become unreferenced assets.
    """
    event = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(
        encoding="utf-8", errors="surrogateescape")
    start = _method(event, "private void startCurrentRiftFractures(")
    assert ("playCasterSpellBody(boss, BossSpellGeometryPolicy.Shape.RIFT_FRACTURE,"
            in start), "the floor rift must spawn its authored crack body"
    tick = _method(event, "private void tickCurrentRiftFractures(")
    assert ("playCasterSpellBody(boss, BossSpellGeometryPolicy.Shape.INFERNO_GEYSER,"
            in tick), "the eruption must spawn its authored pressure-fire body"
    # Both live behind the eruption one-shot, so one hazard cannot spam bodies.
    eruption = tick[tick.index("if (!state.erupted())"):]
    assert "INFERNO_GEYSER" in eruption[: eruption.index("renderCurrentRiftFracture")]

