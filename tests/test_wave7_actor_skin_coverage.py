"""Wave 7 miniboss skins: every declared face must actually be painted.

The three named trial actors shipped with authored sheets that no longer matched
their rigs.  A Minecraft cuboid samples **six** separate rectangles of the atlas,
so painting "roughly where the part sits" leaves most faces reading transparent
texels.  The runtime hides that: ``EndRiftAtlasUvLayout.fit`` repacks any face
that is less than 95 % painted onto unrelated atlas pixels -- except for paths
containing ``rune``/``sigil``, which it deliberately skips.  The measured result
was an invisible Archmage sigil ring (0 %), an invisible rune hand (0 %), empty
Warden shards (0 %) and empty skeleton lower legs (0 %), while everything else
silently borrowed somebody else's texels.

This module is the contract that stops that from happening again.  It shares one
footprint source of truth with the generator (``resourcepacks/tools/
wave7_uv_layout.py``, which parses the real ``texOffs``/``addBox`` calls) so the
measurement cannot drift away from what is painted:

1. every face of every declared box -- no part is filtered out by name -- is at
   least 95 % painted, and its rectangle actually lies inside the atlas;
2. the whole atlas is opaque, which is the only state in which the runtime
   repack is a genuine no-op for the excluded rune/sigil paths too;
3. the specific islands named in the defect report are painted, spelled out so a
   regression names the part that broke;
4. the generator still reproduces the shipped bytes, twice, deterministically,
   and writes only into the client resource root.
"""

import importlib
import importlib.util
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / "resourcepacks" / "tools"
RESOURCES = ROOT / "CopiMineClient" / "src" / "main" / "resources"
TEXTURE_ROOT = (
    RESOURCES / "assets" / "copimineclient" / "textures" / "entity"
)

# ``EndRiftAtlasUvLayout.fit`` repacks a face below this painted fraction, so a
# sheet that satisfies this bound is a no-op for the repack.
REPACK_THRESHOLD = 0.95
# A texel counts as painted at the same alpha the runtime samples as visible.
VISIBLE_ALPHA = 128

EXPECTED_SIZES = {
    "end_rift_wave7_warden.png": (128, 128),
    "end_rift_wave7_archmage.png": (64, 64),
    "end_rift_wave7_marksman.png": (64, 32),
}

# Islands called out by the defect report; each one used to be blank.
REQUIRED_PARTS = {
    "end_rift_wave7_warden.png": (
        "rift_shard_left",
        "rift_shard_right",
        "rift_back_spines",
        "rift_chest_plate",
        "rift_crown",
        "rift_core",
    ),
    "end_rift_wave7_archmage.png": (
        "rift_sigil_ring",
        "rift_rune_hand",
        "rift_hood_rune",
        "rift_focus_orb",
        "rift_focus_staff",
        "rift_hood",
        "rift_robe",
        "rift_seal",
    ),
    "end_rift_wave7_marksman.png": (
        "left_leg",
        "right_leg",
        "left_lower_leg",
        "right_lower_leg",
        "marksman_quiver",
        "marksman_sight",
        "chest_rift",
    ),
}


def _load(name, filename):
    """Load a tools module by path, as the other resource tests do."""
    path = TOOLS / filename
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


sys.path.insert(0, str(TOOLS))
layout = importlib.import_module("wave7_uv_layout")
generator = _load("wave7_skin_generator", "generate_wave7_actor_skins.py")


def painted_fraction(image, face):
    """Fraction of a face's texels that the runtime would draw as visible."""
    alpha = image.getchannel("A")
    painted = 0
    for y in range(face.y, face.bottom):
        for x in range(face.x, face.right):
            if alpha.getpixel((x, y)) >= VISIBLE_ALPHA:
                painted += 1
    return painted / face.area


def _shipped(name):
    return Image.open(TEXTURE_ROOT / name).convert("RGBA")


def _part_fractions(image, atlas):
    """Painted fraction of every declared part, worst face wins."""
    fractions = {}
    for face in atlas.faces():
        current = painted_fraction(image, face)
        if face.part not in fractions or current < fractions[face.part]:
            fractions[face.part] = current
    return fractions


def test_both_consumers_share_one_footprint_module():
    """The generator and this test must measure the same geometry."""
    source = (TOOLS / "generate_wave7_actor_skins.py").read_text(encoding="utf-8")
    assert "import wave7_uv_layout as layout" in source
    assert layout.ROOT == ROOT, layout.ROOT


def test_every_atlas_matches_its_rig_and_is_not_empty():
    atlases = layout.atlases()
    assert set(atlases) == set(EXPECTED_SIZES), sorted(atlases)
    for name, atlas in atlases.items():
        assert (atlas.width, atlas.height) == EXPECTED_SIZES[name], name
        assert atlas.boxes, f"{name} declares no boxes"
        # Every declared box must unwrap into six faces, all on the atlas.
        assert len(atlas.faces()) == 6 * len(atlas.boxes), name
        for face in atlas.faces():
            assert face.width >= 1 and face.height >= 1, (name, face)
            assert 0 <= face.x and face.right <= atlas.width, (
                f"{name}: {face.part} {face.side} escapes the atlas horizontally"
            )
            assert 0 <= face.y and face.bottom <= atlas.height, (
                f"{name}: {face.part} {face.side} escapes the atlas vertically"
            )


def test_every_declared_face_is_painted():
    """No part is exempt: an unpainted face is what made armour vanish."""
    for name, atlas in layout.atlases().items():
        image = _shipped(name)
        for face in atlas.faces():
            fraction = painted_fraction(image, face)
            assert fraction >= REPACK_THRESHOLD, (
                f"{name}: {face.part} {face.side} face is only "
                f"{fraction:.1%} painted at ({face.x}, {face.y}, "
                f"{face.width}x{face.height}); the runtime would repack it"
            )


def test_whole_atlas_is_opaque():
    """The runtime repack skips ``rune``/``sigil`` paths, so those islands can
    only be visible if their texels are authored.  Full coverage makes the
    repack a no-op for every face, declared or inherited."""
    for name, atlas in layout.atlases().items():
        alpha = _shipped(name).getchannel("A")
        low, high = alpha.getextrema()
        assert low == 255 and high == 255, (
            f"{name}: alpha ranges over ({low}, {high}); part of the atlas is "
            "still transparent and the rig repack would move it"
        )
        assert alpha.histogram()[255] == atlas.width * atlas.height, name


def test_the_islands_named_in_the_defect_report_are_painted():
    for name, parts in REQUIRED_PARTS.items():
        atlas = layout.atlases()[name]
        declared = {box.part for box in atlas.boxes}
        image = _shipped(name)
        fractions = _part_fractions(image, atlas)
        for part in parts:
            assert part in declared, f"{name}: {part} is no longer declared by the rig"
            assert fractions[part] >= REPACK_THRESHOLD, (
                f"{name}: {part} is only {fractions[part]:.1%} painted"
            )


def test_invisible_variant_parts_are_painted_too():
    """``chestRift`` and the ``elite_*``/``guard_*`` boxes are ``visible = false``
    for the Marksman variant but still traverse ``ModelPart.visit``; they must
    sample authored texels rather than transparency."""
    atlas = layout.atlases()["end_rift_wave7_marksman.png"]
    hidden = {
        "chest_rift", "elite_horn_left", "elite_horn_right",
        "elite_shoulder_left", "elite_shoulder_right", "elite_mantle",
        "elite_horn_crown", "guard_crest", "guard_chest_seal",
        "guardian_spine", "hat",
    }
    declared = {box.part for box in atlas.boxes}
    assert hidden <= declared, sorted(hidden - declared)
    fractions = _part_fractions(_shipped(atlas.name), atlas)
    for part in sorted(hidden):
        assert fractions[part] >= REPACK_THRESHOLD, (
            f"{atlas.name}: {part} is only {fractions[part]:.1%} painted"
        )


def test_the_generator_reproduces_the_shipped_art(tmp_path):
    """A hand-edited PNG nobody can rebuild is a one-way change."""
    written = generator.generate(tmp_path)
    assert len(written) == 3, written
    for path in written:
        shipped = TEXTURE_ROOT / path.name
        assert shipped.exists(), shipped
        assert path.read_bytes() == shipped.read_bytes(), (
            f"{path.name} does not match its generator; re-run "
            "resourcepacks/tools/generate_wave7_actor_skins.py"
        )


def test_the_generator_is_deterministic(tmp_path):
    first = {p.name: p.read_bytes() for p in generator.generate(tmp_path / "a")}
    second = {p.name: p.read_bytes() for p in generator.generate(tmp_path / "b")}
    assert first == second
    for name, data in first.items():
        assert data == (TEXTURE_ROOT / name).read_bytes(), name


def test_the_generator_writes_only_into_the_client_resource_root():
    paths = generator.output_paths()
    assert len(paths) == 3
    assert generator.TEXTURE_ROOT == TEXTURE_ROOT, generator.TEXTURE_ROOT
    for path in paths:
        assert path.is_absolute(), path
        assert RESOURCES in path.parents, f"{path} is outside the client resources"
        assert path.name in EXPECTED_SIZES, path.name
        with Image.open(path) as image:
            assert image.size == EXPECTED_SIZES[path.name], (path.name, image.size)
            assert image.mode == "RGBA", (path.name, image.mode)
    # The phantom tree the old ``parents[1]`` root produced must never come back.
    assert not (ROOT / "resourcepacks" / "assets").exists(), (
        "the generator is writing into resourcepacks/assets again"
    )


def test_painted_fraction_table(capsys):
    """Print the evidence: every part, every atlas, worst face wins."""
    rows = []
    for name, atlas in layout.atlases().items():
        fractions = _part_fractions(_shipped(name), atlas)
        for part, fraction in sorted(fractions.items(), key=lambda item: item[1]):
            rows.append((name, part, fraction))
    print(f"{'atlas':<31}{'part':<24}{'painted':>8}")
    for name, part, fraction in rows:
        print(f"{name:<31}{part:<24}{fraction:>7.1%}")
    assert rows
    assert all(fraction >= REPACK_THRESHOLD for _, _, fraction in rows)