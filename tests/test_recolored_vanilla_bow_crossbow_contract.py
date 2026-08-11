from __future__ import annotations

import importlib.util
import json
from pathlib import Path
from zipfile import ZipFile

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
GENERATOR_PATH = ROOT / "resourcepacks" / "generate_new_item_textures.py"
REFERENCE = ROOT / "resourcepacks" / "reference" / "vanilla" / "item"
PACKAGED = ROOT / "resourcepacks" / "src"
ARTIFACT_TEXTURES = PACKAGED / "assets" / "copimine" / "textures" / "item" / "artifacts"
ARTIFACT_MODELS = PACKAGED / "assets" / "copimine" / "models" / "item" / "artifacts"
ZIP_PATH = ROOT / "resourcepacks" / "build" / "CopiMineResourcePack.zip"

REFERENCE_FRAMES = {
    "bow.png",
    "bow_pulling_0.png",
    "bow_pulling_1.png",
    "bow_pulling_2.png",
    "crossbow_standby.png",
    "crossbow_pulling_0.png",
    "crossbow_pulling_1.png",
    "crossbow_pulling_2.png",
    "crossbow_arrow.png",
}

# These are the three-pixel arrow fletching/nock in the 1.21.1 bow pull
# frames. The grey diagonal string is intentionally not included here.
BOW_PROJECTILE_COORDS = {
    "bow_pulling_0.png": {(1, 0), (1, 1), (2, 1)},
    "bow_pulling_1.png": {(2, 1), (2, 2), (3, 2)},
    "bow_pulling_2.png": {(3, 2), (3, 3), (4, 3)},
}

BOW_OUTPUTS = {
    "teleport_bow": "bow.png",
    "teleport_bow_pulling_0": "bow_pulling_0.png",
    "teleport_bow_pulling_1": "bow_pulling_1.png",
    "teleport_bow_pulling_2": "bow_pulling_2.png",
    "cobblestone_trail_bow": "bow.png",
    "cobblestone_trail_bow_pulling_0": "bow_pulling_0.png",
    "cobblestone_trail_bow_pulling_1": "bow_pulling_1.png",
    "cobblestone_trail_bow_pulling_2": "bow_pulling_2.png",
}

CROSSBOW_OUTPUTS = {
    "explosive_crossbow": "crossbow_standby.png",
    "explosive_crossbow_pulling_0": "crossbow_pulling_0.png",
    "explosive_crossbow_pulling_1": "crossbow_pulling_1.png",
    "explosive_crossbow_pulling_2": "crossbow_pulling_2.png",
}


def _load_generator():
    spec = importlib.util.spec_from_file_location("copimine_texture_generator", GENERATOR_PATH)
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def _logical_pixels(image: Image.Image) -> Image.Image:
    """Sample the top-left pixel of each 2x2 nearest-neighbor output cell."""
    assert image.size == (32, 32)
    return image.crop((0, 0, 32, 32)).resize((16, 16), Image.Resampling.NEAREST)


def _assert_recolored_shape(output_name: str, reference_name: str):
    with Image.open(REFERENCE / reference_name) as source_file, Image.open(ARTIFACT_TEXTURES / f"{output_name}.png") as output_file:
        source = source_file.convert("RGBA")
        output = _logical_pixels(output_file.convert("RGBA"))
        assert output_file.size == (32, 32)
        assert output_file.mode == "RGBA"

        changed_palette = False
        for y in range(16):
            for x in range(16):
                source_pixel = source.getpixel((x, y))
                output_pixel = output.getpixel((x, y))
                assert output_pixel[3] == source_pixel[3], (output_name, x, y)
                if source_pixel[3] and output_pixel[:3] != source_pixel[:3]:
                    changed_palette = True
        assert changed_palette, output_name


def test_vanilla_reference_frames_are_present_and_not_packaged_as_overrides():
    assert REFERENCE.is_dir()
    assert {path.name for path in REFERENCE.glob("*.png")} >= REFERENCE_FRAMES
    vanilla_packaged = PACKAGED / "assets" / "minecraft" / "textures" / "item"
    assert not list(vanilla_packaged.glob("bow*.png")) if vanilla_packaged.exists() else True
    assert not list(vanilla_packaged.glob("crossbow*.png")) if vanilla_packaged.exists() else True


def test_generator_uses_explicit_vanilla_recolor_and_projectile_coordinates():
    generator = _load_generator()
    assert hasattr(generator, "load_vanilla_texture")
    assert hasattr(generator, "recolor_vanilla_texture")
    assert hasattr(generator, "BOW_PROJECTILE_COORDS")
    assert generator.BOW_PROJECTILE_COORDS == BOW_PROJECTILE_COORDS
    assert generator.CROSSBOW_PROJECTILE_REFERENCE == "crossbow_arrow.png"


def test_recolored_bows_preserve_vanilla_geometry_with_projectile():
    for output_name, reference_name in BOW_OUTPUTS.items():
        _assert_recolored_shape(output_name, reference_name)


def test_bow_pull_frames_are_distinct_and_keep_custom_projectile():
    frames = []
    for output_name in ("teleport_bow_pulling_0", "teleport_bow_pulling_1", "teleport_bow_pulling_2"):
        with Image.open(ARTIFACT_TEXTURES / f"{output_name}.png") as image:
            frames.append(image.convert("RGBA").tobytes())
    assert len(set(frames)) == 3

    for output_name, reference_name in BOW_OUTPUTS.items():
        if "pulling" not in output_name:
            continue
        with Image.open(ARTIFACT_TEXTURES / f"{output_name}.png") as image, Image.open(REFERENCE / reference_name) as source_file:
            logical = _logical_pixels(image.convert("RGBA"))
            source = source_file.convert("RGBA")
            for x, y in BOW_PROJECTILE_COORDS[reference_name]:
                assert logical.getpixel((x, y))[3] > 0
                assert logical.getpixel((x, y))[:3] != source.getpixel((x, y))[:3]


def test_recolored_crossbows_preserve_vanilla_geometry():
    for output_name, reference_name in CROSSBOW_OUTPUTS.items():
        _assert_recolored_shape(output_name, reference_name)
        model = json.loads((ARTIFACT_MODELS / f"{output_name}.json").read_text(encoding="utf-8"))
        assert "crossbow_arrow" not in json.dumps(model)


def test_explosive_crossbow_charged_animation_is_valid_and_keeps_custom_tnt():
    atlas_path = ARTIFACT_TEXTURES / "explosive_crossbow_charged.png"
    with Image.open(atlas_path) as atlas_file:
        atlas = atlas_file.convert("RGBA")
        assert atlas.size == (32, 128)
        frames = [atlas.crop((0, offset, 32, offset + 32)).tobytes() for offset in range(0, 128, 32)]
        assert len(set(frames)) >= 2
        assert any(pixel[0] > pixel[1] * 2 for pixel in atlas.getdata() if pixel[3])
    metadata = json.loads((ARTIFACT_TEXTURES / "explosive_crossbow_charged.png.mcmeta").read_text(encoding="utf-8"))
    assert metadata["animation"]["interpolate"] is False


def test_built_zip_contains_custom_states_but_no_vanilla_item_texture_overrides():
    assert ZIP_PATH.is_file()
    with ZipFile(ZIP_PATH) as archive:
        names = set(archive.namelist())
        assert archive.testzip() is None
        assert "assets/copimine/textures/item/artifacts/teleport_bow_pulling_2.png" in names
        assert "assets/copimine/textures/item/artifacts/explosive_crossbow_charged.png" in names
        assert not any(name.startswith("assets/minecraft/textures/item/bow") for name in names)
        assert not any(name.startswith("assets/minecraft/textures/item/crossbow") for name in names)
