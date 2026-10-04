"""Authored W3/W4 geometry, UV and reproducible source-pipeline checks.

These tests cannot prove native Minecraft rendering. They reject the previous
small gate, identical obelisk silhouettes and implicit stretched UVs.
"""
from __future__ import annotations

import importlib.util
import json
import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "resourcepacks"
ASSETS = PACK / "src/assets/copimine"


def model(name: str, kind: str = "item") -> dict:
    return json.loads((ASSETS / f"models/{kind}/{name}.json").read_text("utf-8"))


def mesh_bounds(data: dict) -> tuple[list[float], list[float]]:
    vertices = []
    for element in data["elements"]:
        rotation = element.get("rotation")
        for x in (element["from"][0], element["to"][0]):
            for y in (element["from"][1], element["to"][1]):
                for z in (element["from"][2], element["to"][2]):
                    point = [x, y, z]
                    if rotation:
                        axis = "xyz".index(rotation["axis"])
                        a, b = ((1, 2), (2, 0), (0, 1))[axis]
                        origin = rotation["origin"]
                        angle = math.radians(rotation["angle"])
                        da, db = point[a] - origin[a], point[b] - origin[b]
                        point[a] = origin[a] + da * math.cos(angle) - db * math.sin(angle)
                        point[b] = origin[b] + da * math.sin(angle) + db * math.cos(angle)
                    vertices.append(point)
    return ([min(p[i] for p in vertices) for i in range(3)],
            [max(p[i] for p in vertices) for i in range(3)])


def test_portal_is_large_deep_fractured_geometry_with_a_clear_walkthrough():
    data = model("end_event_rift_gate")
    assert len(data["elements"]) >= 24, "replace the seven-cuboid vanilla doorway"
    low, high = mesh_bounds(data)
    world_size = [(high[i] - low[i]) / 16 * 2.24 for i in range(3)]
    assert 4.0 <= world_size[0] <= 5.0
    assert 5.0 <= world_size[1] <= 6.0
    assert world_size[2] >= 1.0, "a framed sheet is not a three-dimensional structure"
    assert abs(low[1] + 8) < 1e-6, "floor contract is model y=-8"
    assert any(e.get("rotation") for e in data["elements"])
    assert all(not (e["from"][0] < 8 < e["to"][0]
                    and e["from"][1] < 5 < e["to"][1])
               for e in data["elements"]), "keep the central walk-through void open"
    assert data["textures"]["frame"].startswith("copimine:"), "author the structural material"


def test_portal_layers_share_the_origin_and_are_readable_from_both_sides():
    for name, kind in (("end_event_rift_gate", "item"),
                       ("end_event_portal_inner", "block"),
                       ("end_event_portal_shard", "block")):
        data = model(name, kind)
        assert data.get("gui_light") == "front"
        for element in data["elements"]:
            assert {"north", "south"} <= element["faces"].keys()
            for face in element["faces"].values():
                assert len(face.get("uv", [])) == 4, "implicit default UVs stretch scaled geometry"
                assert all(0 <= v <= 16 for v in face["uv"])
            for coordinate in element["from"] + element["to"]:
                assert -16 <= coordinate <= 32, "Minecraft Java model coordinate limits"
    shards = model("end_event_portal_shard", "block")
    assert len(shards["elements"]) >= 8
    assert max(e["to"][2] for e in shards["elements"]) > 10
    assert min(e["from"][2] for e in shards["elements"]) < 6


def test_portal_membrane_has_distinct_animated_pixel_frames():
    path = ASSETS / "textures/item/end_event_rift_membrane.png"
    metadata = path.with_suffix(".png.mcmeta")
    assert metadata.is_file(), "the old static membrane must be replaced"
    animation = json.loads(metadata.read_text("utf-8"))["animation"]
    with Image.open(path) as texture:
        assert texture.size == (32, 1024)
        frames = [texture.crop((0, index * 128, 32, (index + 1) * 128)).tobytes()
                  for index in range(8)]
        assert len(set(frames)) == 8
        assert texture.getextrema()[3][0] == 0
        assert texture.getextrema()[3][1] == 255
    assert animation["frametime"] == 3
    assert animation["interpolate"] is False
    assert animation["width"] == 32 and animation["height"] == 128
    for element in model("end_event_portal_inner", "block")["elements"]:
        uv = element["faces"]["north"]["uv"]
        width_density = (uv[2] - uv[0]) * 32 / (element["to"][0] - element["from"][0])
        height_density = (uv[3] - uv[1]) * 128 / (element["to"][1] - element["from"][1])
        assert abs(width_density - height_density) < .001, "membrane UV must preserve physical aspect"


def test_obelisk_health_changes_geometry_instead_of_only_texture_color():
    variants = [model(f"end_event_rift_obelisk_{state}")
                for state in ("full", "damaged", "critical")]
    silhouettes = [json.dumps([(e["from"], e["to"], e.get("rotation"))
                               for e in data["elements"]]) for data in variants]
    assert len(set(silhouettes)) == 3, "full/damaged/critical previously used identical geometry"
    assert len(variants[0]["elements"]) > len(variants[1]["elements"]) > len(variants[2]["elements"])
    for data in variants:
        low, high = mesh_bounds(data)
        assert low[1] == 0 and high[1] == 16
        assert low[0] == low[2] == 0 and high[0] == high[2] == 16
        assert data["textures"]["frame"].startswith("copimine:")
        for element in data["elements"]:
            assert set(element["faces"]) == {"up", "down", "north", "south", "west", "east"}
            assert all(len(face.get("uv", [])) == 4 for face in element["faces"].values())


def test_structure_generator_reproduces_registered_assets_without_writing(tmp_path):
    path = PACK / "tools/generate_wave3_wave4_structures.py"
    assert path.is_file(), "authored structures need a reproducible source generator"
    spec = importlib.util.spec_from_file_location("wave_structures", path)
    module = importlib.util.module_from_spec(spec)
    assert spec and spec.loader
    spec.loader.exec_module(module)
    outputs = module.generate(tmp_path)
    assert len(outputs) >= 10
    for relative in outputs:
        assert (tmp_path / relative).read_bytes() == (PACK / relative).read_bytes(), relative


def test_structure_mapping_and_required_pack_registration():
    manifest = json.loads((PACK / "models_manifest.json").read_text("utf-8"))
    entries = {entry["id"]: entry for entry in manifest["items"]}
    for name, cmd in (("end_event_rift_gate", 830018), ("end_event_portal_inner", 830008),
                      ("end_event_portal_shard", 830009), ("end_event_rift_obelisk_full", 830010),
                      ("end_event_rift_obelisk_damaged", 830011), ("end_event_rift_obelisk_critical", 830012)):
        assert entries[name]["base_material"] == "paper"
        assert entries[name]["custom_model_data"] == cmd
        model_path = PACK / "src/assets" / (entries[name]["model"].replace(":", "/models/") + ".json")
        assert model_path.is_file()
    builder = (PACK / "build-resourcepack.py").read_text("utf-8")
    for relative in ("assets/copimine/textures/item/end_event_rift_structure_stone.png",
                     "assets/copimine/textures/item/end_event_rift_structure_energy.png",
                     "assets/copimine/textures/item/end_event_rift_membrane.png",
                     "assets/copimine/textures/item/end_event_rift_membrane.png.mcmeta",
                     "assets/copimine/manifests/waves_3_4_structure_assets.json"):
        assert relative in builder, relative


def test_layout_gate_and_retired_portal_preserve_the_entrance_geometry():
    manifest = json.loads((PACK / "models_manifest.json").read_text("utf-8"))
    entries = {entry["id"]: entry for entry in manifest["items"]}
    assert entries.get("end_event_layout_gate", {}).get("custom_model_data") == 830028
    assert entries["end_event_layout_gate"]["base_material"] == "paper"
    layout = model("end_event_layout_gate")
    assert len(layout["elements"]) == 7
    assert layout["elements"][0]["from"] == [0, 0, 2]
    assert layout["elements"][0]["to"] == [16, 2, 14]
    assert layout["textures"]["rift"] == "copimine:item/end_event_portal"
    retired = model("end_event_portal", "block")
    assert "elements" in retired, "the retired 830007 model must not be aliased to the new Wave 3 gate"
    assert "assets/copimine/models/item/end_event_layout_gate.json" in (PACK / "build-resourcepack.py").read_text("utf-8")


def test_wave1_charge_has_a_compact_cyan_model_separate_from_portal_shards():
    manifest = json.loads((PACK / "models_manifest.json").read_text("utf-8"))
    entries = {entry["id"]: entry for entry in manifest["items"]}
    assert entries.get("end_event_carrier_charge", {}).get("custom_model_data") == 830029
    assert entries["end_event_carrier_charge"]["base_material"] == "paper"
    charge = model("end_event_carrier_charge")
    low, high = mesh_bounds(charge)
    assert max(high[i] - low[i] for i in range(3)) <= 12
    assert len(charge["elements"]) >= 3
    assert charge["textures"]["energy"] == "copimine:item/end_event_carrier_charge"
    with Image.open(ASSETS / "textures/item/end_event_carrier_charge.png") as image:
        colors = [image.getpixel((x, y)) for x in range(32) for y in range(32)]
        assert any(b > 220 and g > 190 and r < 100 for r, g, b, _ in colors)
    builder = (PACK / "build-resourcepack.py").read_text("utf-8")
    assert "assets/copimine/models/item/end_event_carrier_charge.json" in builder
    assert "assets/copimine/textures/item/end_event_carrier_charge.png" in builder
