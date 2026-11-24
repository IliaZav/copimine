"""N23-P1-06 residual: the three Kagune roles no longer borrow another body.

The V6 audit named the remaining shares exactly: ``kagune_stab -> RIFT_LANCE``
(the ordinary rift projectile), ``kagune_eruption -> GEYSER`` (the rock
eruption) and ``small_kagune -> KAGUNE_KNOT`` / ``830206`` (the ambush talon
body). The canonical boss spec keeps three genuinely different Kagune roles --
defensive guardians, the ambush grab and the small melee-only attackers -- and
the audit says "sharing the main visible object/attack identity is not
[allowed]".

This walks the whole chain for each role: spell id, enum arm, mesh selector, an
authored, bounded and distinct silhouette, the renderer table, ``NAMES``, the
motion profile and the generated JSON. It also proves the three new bodies are
different from the three bodies they used to share. This is authored geometry,
not a Minecraft screenshot; the F2/F4 visual acceptance stays owner-only.
"""
import json
import subprocess
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / "tools/minecraft-26.3/client/src/main/java/me/copimine/client"
MESH_DIR = (ROOT / "CopiMineClient/src/main/resources/assets/copimineclient"
            / "geometry/guardian_spells")
GENERATOR = ROOT / "CopiMineClient/tools/generate_guardian_spell_meshes.py"

# role -> (spell id, Shape, modelData, anchor, mesh name)
ROLES = {
    "stab": ("kagune_stab", "KAGUNE_STAB", 830220, "FEET", "kagune_stab"),
    "eruption": ("kagune_eruption", "KAGUNE_ERUPTION", 830221, "LOCKED_FLOOR", "kagune_eruption"),
    "small": ("small_kagune", "KAGUNE_SMALL", 830222, "FEET", "kagune_small"),
}

# What each role used to borrow. The new body must never resolve back to it.
BORROWED = {
    "kagune_stab": ("RIFT_LANCE", 830202),
    "kagune_eruption": ("GEYSER", 830217),
    "small_kagune": ("KAGUNE_KNOT", 830206),
}

PROBE = """
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import me.copimine.endevent.domain.BossSpellGeometryPolicy;
import me.copimine.endevent.domain.EndRiftAiPolicy;

public final class KaguneFamilyProbe {
    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    static void role(String id, BossSpellGeometryPolicy.Shape shape, int model,
                     BossSpellGeometryPolicy.Anchor anchor) {
        BossSpellGeometryPolicy.Shape resolved = BossSpellGeometryPolicy.shape(id);
        check(resolved == shape, id + " maps to " + resolved + ", not " + shape);
        check(BossSpellGeometryPolicy.modelData(shape) == model,
                id + " mesh id changed: " + BossSpellGeometryPolicy.modelData(shape));
        for (var beat : BossSpellGeometryPolicy.Beat.values()) {
            check(BossSpellGeometryPolicy.anchor(shape, beat) == anchor,
                    id + " anchor at " + beat + " is "
                            + BossSpellGeometryPolicy.anchor(shape, beat));
            for (int frame = 0; frame <= 20; frame++) {
                List<BossSpellGeometryPolicy.Point> points = BossSpellGeometryPolicy.sample(
                        shape, beat, frame / 20.0, 4.0);
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
        // The three roles are real BossSpell ids owned by the LastSeal runtime.
        check(EndRiftAiPolicy.BossSpell.KAGUNE_STAB.id().equals("kagune_stab"), "stab id");
        check(EndRiftAiPolicy.BossSpell.KAGUNE_ERUPTION.id().equals("kagune_eruption"), "eruption id");
        check(EndRiftAiPolicy.BossSpell.SMALL_KAGUNE.id().equals("small_kagune"), "small id");

        role("kagune_stab", BossSpellGeometryPolicy.Shape.KAGUNE_STAB, 830220,
                BossSpellGeometryPolicy.Anchor.FEET);
        role("kagune_eruption", BossSpellGeometryPolicy.Shape.KAGUNE_ERUPTION, 830221,
                BossSpellGeometryPolicy.Anchor.LOCKED_FLOOR);
        role("small_kagune", BossSpellGeometryPolicy.Shape.KAGUNE_SMALL, 830222,
                BossSpellGeometryPolicy.Anchor.FEET);

        // No role falls back to the body it borrowed before.
        check(BossSpellGeometryPolicy.modelData(BossSpellGeometryPolicy.Shape.KAGUNE_STAB)
                        != BossSpellGeometryPolicy.modelData(BossSpellGeometryPolicy.Shape.RIFT_LANCE),
                "the stab still shares the rift lance body");
        check(BossSpellGeometryPolicy.modelData(BossSpellGeometryPolicy.Shape.KAGUNE_ERUPTION)
                        != BossSpellGeometryPolicy.modelData(BossSpellGeometryPolicy.Shape.GEYSER),
                "the eruption still shares the rock geyser body");
        check(BossSpellGeometryPolicy.modelData(BossSpellGeometryPolicy.Shape.KAGUNE_SMALL)
                        != BossSpellGeometryPolicy.modelData(BossSpellGeometryPolicy.Shape.KAGUNE_KNOT),
                "the small Kagune still shares the ambush knot body");
        check(BossSpellGeometryPolicy.modelData(BossSpellGeometryPolicy.Shape.KAGUNE_SMALL)
                        != BossSpellGeometryPolicy.modelData(BossSpellGeometryPolicy.Shape.AMBUSH_TALONS),
                "the small Kagune still shares the ambush talon body");

        // The three roles, and the three bodies they used to borrow, are seven
        // distinct silhouettes at the same charge frame.
        Set<String> distinct = new HashSet<>();
        for (var shape : List.of(
                BossSpellGeometryPolicy.Shape.KAGUNE_STAB,
                BossSpellGeometryPolicy.Shape.KAGUNE_ERUPTION,
                BossSpellGeometryPolicy.Shape.KAGUNE_SMALL,
                BossSpellGeometryPolicy.Shape.RIFT_LANCE,
                BossSpellGeometryPolicy.Shape.GEYSER,
                BossSpellGeometryPolicy.Shape.KAGUNE_KNOT,
                BossSpellGeometryPolicy.Shape.AMBUSH_TALONS)) {
            check(distinct.add(BossSpellGeometryPolicy.sample(shape,
                    BossSpellGeometryPolicy.Beat.CHARGE, 0.63, 4.0).toString()),
                    "two bodies share a silhouette: " + shape);
        }

        System.out.println("OK");
    }
}
"""


@pytest.fixture(scope="module")
def probe(tmp_path_factory):
    directory = tmp_path_factory.mktemp("kagune-family")
    source = directory / "KaguneFamilyProbe.java"
    source.write_text(PROBE, encoding="utf-8")
    result = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-sourcepath", str(ROOT / "copimine-end-event/src"),
         "-d", str(directory), str(source)],
        capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    return directory


def test_kagune_roles_resolve_to_their_own_authored_bodies(probe):
    result = subprocess.run(
        ["java", "-cp", str(probe), "KaguneFamilyProbe"],
        capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    assert result.stdout.strip() == "OK"


def test_kagune_meshes_are_generated_bounded_and_wired():
    renderer = (CLIENT / "GuardianSpellRenderer.java").read_text(encoding="utf-8")
    names = (CLIENT / "GuardianSpellMesh.java").read_text(encoding="utf-8")
    # Alpha/translucency is the authored material path, not an opaque block.
    assert "entityTranslucentEmissive(TEXTURE)" in renderer
    for _, (_, _, model, _, name) in ROLES.items():
        assert f'case {model} -> "{name}";' in renderer, (name, model)
        assert f'"{name}"' in names, name
        body = renderer[renderer.index(f'case "{name}" -> new Motion('):]
        body = body[: body.index(");")]
        assert "charge" in body or "heartbeat" in body, f"{name} has no animated phase"
        document = json.loads((MESH_DIR / f"{name}.json").read_text(encoding="utf-8"))
        elements = document.get("elements")
        assert isinstance(elements, list)
        assert 1 <= len(elements) <= 32, (name, len(elements))
        for element in elements:
            low, high = element["from"], element["to"]
            for axis in range(3):
                assert -16 <= low[axis] < high[axis] <= 32, (name, element)
            rotation = element.get("rotation")
            if rotation is not None:
                for origin in rotation["origin"]:
                    assert -16 <= origin <= 32, (name, rotation)
                assert abs(rotation["angle"]) <= 45, (name, rotation)
            assert set(element["faces"]) == {"north", "south", "east", "west", "up", "down"}
            for face in element["faces"].values():
                u = face["uv"]
                assert 0 <= min(u) and max(u) <= 16, face


def test_the_three_kagune_bodies_are_visibly_different_from_each_other_and_the_old_ones():
    """Sharing the mesh is sharing the main visible object, even if the enum
    differs. Compare the authored cuboid signature, not a file name."""
    def signature(name):
        document = json.loads((MESH_DIR / f"{name}.json").read_text(encoding="utf-8"))
        return frozenset(
            (tuple(element["from"]), tuple(element["to"]),
             None if "rotation" not in element else
             (element["rotation"]["axis"], element["rotation"]["angle"]))
            for element in document["elements"])

    new = {name: signature(name) for name in (role[4] for role in ROLES.values())}
    assert len(set(new.values())) == 3, "two Kagune roles share a cuboid set"
    for name in ("rift_lance", "geyser", "kagune_splinter"):
        assert signature(name) not in new.values(), f"{name} is reused by a Kagune body"


def test_kagune_meshes_match_the_generator_byte_for_byte():
    result = subprocess.run(
        ["python", str(GENERATOR), "--check", "--output",
         str(ROOT / "CopiMineClient/src/main/resources")],
        capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
