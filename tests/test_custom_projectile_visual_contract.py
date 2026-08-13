from __future__ import annotations

import importlib.util
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
GENERATOR_PATH = ROOT / "resourcepacks" / "generate_new_item_textures.py"
REFERENCE = ROOT / "resourcepacks" / "reference" / "vanilla" / "item"
TEXTURES = ROOT / "resourcepacks" / "src" / "assets" / "copimine" / "textures" / "item" / "artifacts"

BOW_PROJECTILE_COORDS = {
    "bow_pulling_0.png": {(1, 0), (1, 1), (2, 1)},
    "bow_pulling_1.png": {(2, 1), (2, 2), (3, 2)},
    "bow_pulling_2.png": {(3, 2), (3, 3), (4, 3)},
}

CROSSBOW_OUTPUTS = {
    "teleport_bow": "crossbow_standby.png",
    "teleport_bow_pulling_0": "crossbow_pulling_0.png",
    "teleport_bow_pulling_1": "crossbow_pulling_1.png",
    "teleport_bow_pulling_2": "crossbow_pulling_2.png",
}


def _load_generator():
    spec = importlib.util.spec_from_file_location("copimine_texture_generator", GENERATOR_PATH)
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def _logical(image: Image.Image) -> Image.Image:
    assert image.size == (32, 32)
    return image.convert("RGBA").resize((16, 16), Image.Resampling.NEAREST)


def test_generator_declares_custom_projectile_layers():
    generator = _load_generator()
    assert hasattr(generator, "BOW_PROJECTILE_COORDS")
    assert hasattr(generator, "CROSSBOW_PROJECTILE_REFERENCE")
    assert generator.CROSSBOW_PROJECTILE_REFERENCE == "crossbow_arrow.png"


def test_bow_pull_frames_keep_visible_recolored_custom_arrow():
    for prefix in ("cobblestone_trail_bow",):
        for reference_name in BOW_PROJECTILE_COORDS:
            stage = reference_name.removeprefix("bow_").removesuffix(".png")
            output_name = f"{prefix}_{stage}"
            with Image.open(TEXTURES / f"{output_name}.png") as output_file:
                output = _logical(output_file)
            with Image.open(REFERENCE / reference_name) as reference_file:
                reference = reference_file.convert("RGBA")

            for x, y in BOW_PROJECTILE_COORDS[reference_name]:
                output_pixel = output.getpixel((x, y))
                reference_pixel = reference.getpixel((x, y))
                assert output_pixel[3] > 0, (prefix, output_name, x, y)
                assert output_pixel[:3] != reference_pixel[:3], (prefix, output_name, x, y)


def test_teleport_crossbow_pull_frames_keep_recolored_vanilla_geometry():
    for output_name, reference_name in CROSSBOW_OUTPUTS.items():
        with Image.open(TEXTURES / f"{output_name}.png") as output_file, Image.open(REFERENCE / reference_name) as reference_file:
            output = _logical(output_file)
            reference = reference_file.convert("RGBA")
            changed = 0
            for y in range(16):
                for x in range(16):
                    source = reference.getpixel((x, y))
                    actual = output.getpixel((x, y))
                    assert actual[3] == source[3], (output_name, x, y)
                    if source[3] and actual[:3] != source[:3]:
                        changed += 1
            assert changed > 0, output_name


def test_charged_crossbow_contains_recolored_vanilla_bolt_geometry():
    reference_path = REFERENCE / "crossbow_arrow.png"
    atlas_path = TEXTURES / "explosive_crossbow_charged.png"
    assert reference_path.is_file()

    with Image.open(reference_path) as reference_file, Image.open(atlas_path) as atlas_file:
        reference = reference_file.convert("RGBA")
        first_frame = atlas_file.convert("RGBA").crop((0, 0, 32, 32))
        output = _logical(first_frame)

    changed_bolt_pixels = 0
    for y in range(16):
        for x in range(16):
            if reference.getpixel((x, y))[3] == 0:
                continue
            assert output.getpixel((x, y))[3] > 0, (x, y)
            if output.getpixel((x, y))[:3] != reference.getpixel((x, y))[:3]:
                changed_bolt_pixels += 1
    assert changed_bolt_pixels > 0
