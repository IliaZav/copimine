from __future__ import annotations

import importlib.util
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "resourcepacks"
PREFIX = "src/assets/copimine/"


def test_wave5_floor_has_a_dedicated_low_model_and_unique_paper_mapping():
    manifest = json.loads((PACK / "models_manifest.json").read_text("utf-8"))
    entries = [e for e in manifest["items"] if e["custom_model_data"] == 830027]
    assert len(entries) == 1, "replace emerald slab Display with an authored safe-floor model"
    assert entries[0]["base_material"] == "paper"
    assert entries[0]["id"] == "end_event_safe_floor"
    data = json.loads((PACK / (PREFIX + "models/item/end_event_safe_floor.json")).read_text("utf-8"))
    assert len(data["elements"]) >= 5
    assert min(e["from"][1] for e in data["elements"]) == 8
    assert max(e["to"][1] for e in data["elements"]) <= 8.4
    for element in data["elements"]:
        assert 0 <= element["from"][0] < element["to"][0] <= 16
        assert 0 <= element["from"][2] < element["to"][2] <= 16
        assert len(element["faces"]["up"]["uv"]) == 4
        assert element["faces"]["up"]["texture"] == "#seal"
    base = data["elements"][0]
    assert base["from"] == [0, 8, 0] and base["to"] == [16, 8.2, 16]
    metadata = json.loads((PACK / (PREFIX + "manifests/wave5_safe_floor_asset.json")).read_text("utf-8"))
    assert metadata["world_scale"] == [1, 1, 1], "cover every real safe-cell top completely"


def test_wave5_green_white_pattern_is_not_the_vanilla_emerald_texture():
    path = PACK / (PREFIX + "textures/item/end_event_safe_floor.png")
    assert path.is_file()
    with Image.open(path) as image:
        assert image.size == (32, 32)
        rgba = image.convert("RGBA")
        colors = [rgba.getpixel((x, y)) for y in range(32) for x in range(32)]
        assert any(g > 170 and r > 130 and b > 150 for r, g, b, _ in colors)
        assert any(g > r * 1.3 and g > b * 1.05 for r, g, b, _ in colors)
        assert all(a == 255 for _, _, _, a in colors), "emerald floor must not show through texture holes"


def test_wave5_safe_tile_generation_is_reproducible_and_required_in_pack(tmp_path):
    path = PACK / "tools/generate_wave5_safe_tile.py"
    assert path.is_file()
    spec = importlib.util.spec_from_file_location("safe_tile", path)
    module = importlib.util.module_from_spec(spec)
    assert spec and spec.loader
    spec.loader.exec_module(module)
    for relative in module.generate(tmp_path):
        assert (tmp_path / relative).read_bytes() == (PACK / relative).read_bytes()
    builder = (PACK / "build-resourcepack.py").read_text("utf-8")
    for relative in ("assets/copimine/models/item/end_event_safe_floor.json",
                     "assets/copimine/textures/item/end_event_safe_floor.png",
                     "assets/copimine/manifests/wave5_safe_floor_asset.json"):
        assert relative in builder
