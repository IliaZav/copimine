"""Original W3/W4 structures and compact W1 charge, without external assets.

Only these named structure outputs are written. Supplied HD obelisk portraits,
all W6/W7 geometry, the Guardian and Kagune assets remain untouched.
Run from any directory: python resourcepacks/tools/generate_wave3_wave4_structures.py
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path

from PIL import Image, ImageDraw
try:
    from resourcepacks.tools.deterministic_png import png_bytes
except ModuleNotFoundError:
    from deterministic_png import png_bytes

ROOT = Path(__file__).resolve().parents[1]
FACES = ("down", "up", "north", "south", "west", "east")
STONE = "copimine:item/end_event_rift_structure_stone"
ENERGY = "copimine:item/end_event_rift_structure_energy"
PORTAL_SCALE = 2.24
OBELISK_SCALE = (3.25, 5.0, 3.25)


def cuboid(name, low, high, texture="frame", rotation=None,
           world_scale=(PORTAL_SCALE,) * 3, uv=None):
    """Eight texels per world block; explicit UVs avoid default tall-face stretch."""
    lengths = [(high[i] - low[i]) * world_scale[i] / 4 for i in range(3)]
    result = {"name": name, "from": low, "to": high, "faces": {}}
    for face in FACES:
        a, b = {"down": (0, 2), "up": (0, 2), "north": (0, 1),
                "south": (0, 1), "west": (2, 1), "east": (2, 1)}[face]
        coordinates = uv or [0, 0, round(lengths[a], 4), round(lengths[b], 4)]
        if max(coordinates) > 16:
            raise ValueError(f"UV tile overflow for {name}/{face}: split the authored face")
        result["faces"][face] = {"uv": coordinates, "texture": "#" + texture}
    if rotation:
        result["rotation"] = rotation
    return result


def rotate(origin, angle, axis="z"):
    return {"origin": origin, "axis": axis, "angle": angle, "rescale": False}


def structure(elements, **textures):
    return {"parent": "minecraft:block/block", "ambientocclusion": False,
            "gui_light": "front", "textures": {"particle": STONE, **textures},
            "elements": elements}


def portal_frame():
    pieces = [
        ("left_foundation", [-8, -8, 3], [-1, -5, 13], None),
        ("right_foundation", [16, -8, 3], [24, -5, 13], None),
        ("left_lower", [-7, -5, 4], [-2, 7, 12], None),
        ("left_middle", [-6, 8, 4], [-1, 18, 12], rotate([-3.5, 13, 8], -22.5)),
        ("left_shoulder", [-3, 19, 3], [3, 26, 13], rotate([0, 22.5, 8], -22.5)),
        ("left_arch", [0, 27, 4], [8, 30, 12], rotate([4, 28.5, 8], 22.5)),
        ("crown_split", [7, 30, 5], [10, 32, 11], None),
        ("right_lower", [18, -5, 3], [23, 5, 13], None),
        ("right_middle", [17, 6, 4], [22, 18, 12], rotate([19.5, 12, 8], 22.5)),
        ("right_shoulder", [15, 19, 3], [21, 26, 13], rotate([18, 22.5, 8], 22.5)),
        ("right_arch", [11, 26, 5], [19, 29, 11], rotate([15, 27.5, 8], -22.5)),
    ]
    elements = []
    for name, low, high, rotation in pieces:
        elements.append(cuboid(name, low, high, rotation=rotation))
        # Front/back energy inlays are independent solid strips with a small
        # separation from the structural face, so neither side z-fights.
        cx = (low[0] + high[0]) / 2
        seam_bottom, seam_top = low[1] + 0.3, high[1] - 0.3
        for side, z1, z2 in (("front", low[2] - 0.08, low[2] - 0.025),
                             ("back", high[2] + 0.025, high[2] + 0.08)):
            elements.append(cuboid(name + "_seam_" + side,
                                   [cx - 0.17, seam_bottom, z1],
                                   [cx + 0.17, seam_top, z2], "energy", rotation))
    return structure(elements, frame=STONE, energy=ENERGY)


def portal_membrane():
    # Seven narrow volumes form a concave aperture; the open dark centre and
    # transparent animated cuts are visible from front, back and oblique views.
    columns = [(0.5, 2.6, -1.8, 19), (2.6, 4.8, -4.3, 24),
               (4.8, 7.0, -5.3, 26.8), (7.0, 9.2, -5.8, 28),
               (9.2, 11.4, -5.0, 26.0), (11.4, 13.6, -3.5, 23.3),
               (13.6, 15.5, -0.8, 18.5)]
    elements = []
    for index, (x1, x2, y1, y2) in enumerate(columns):
        e = cuboid(f"void_membrane_{index}", [x1, y1, 7.5], [x2, y2, 8.5],
                   "rift", uv=[0, 0, 16, 16])
        # Every strip samples its own portion of one coherent square animation
        # instead of stretching the same frame separately onto every strip.
        for side in ("north", "south"):
            # A 32x128 animation frame holds 32x72 authored aperture pixels
            # plus transparent mipmap-friendly padding. X/Y have identical
            # texels per model unit; the tall membrane never stretches a
            # square texture into a doorway-shaped rectangle.
            e["faces"][side]["uv"] = [round((x1 - .5) / 15 * 16, 6),
                                      round((28 - y2) / 60 * 16, 6),
                                      round((x2 - .5) / 15 * 16, 6),
                                      round((28 - y1) / 60 * 16, 6)]
        elements.append(e)
    return structure(elements, rift="copimine:item/end_event_rift_membrane")


def portal_shards():
    pieces = [
        ([-6, 18.5, 1.5], [-3, 22, 4.5], -22.5),
        ([-2.5, 25.5, 11.5], [1, 29, 14.5], 22.5),
        ([5.0, 28, 1], [7, 31, 3], -22.5),
        ([11, 28.5, 12], [13, 31.5, 14], 22.5),
        ([19.5, 19, 11.5], [22.5, 23, 14.5], -22.5),
        ([22.5, 10, 1.5], [24.5, 13, 4.5], 22.5),
        ([-8.5, 5, 11], [-6.5, 8, 14], -22.5),
        ([17, -3.5, 1], [20, -.5, 4], 22.5),
    ]
    elements = []
    for index, (low, high, angle) in enumerate(pieces):
        origin = [(low[i] + high[i]) / 2 for i in range(3)]
        rotation = rotate(origin, angle)
        elements.append(cuboid(f"detached_fragment_{index}", low, high, rotation=rotation))
        elements.append(cuboid(f"fragment_cut_{index}",
                               [low[0] + .25, low[1] + .2, low[2] - .08],
                               [high[0] - .25, low[1] + .5, low[2] - .025],
                               "energy", rotation))
    return structure(elements, frame=STONE, energy=ENERGY)


def obelisk(state):
    # The visible body is independent of invisible server collision cells. The
    # three meshes deliberately lose crown/body/buttress pieces with damage.
    parts = [
        ("foundation", [0, 0, 0], [16, 1.0, 16], "frame"),
        ("lower_step", [1, 1.0, 1], [15, 2.0, 15], "frame"),
        ("inset_plinth", [2, 2.0, 2], [14, 3.0, 14], "frame"),
        ("lower_core", [4, 3, 4], [12, 5.5, 12], "frame"),
        ("seal_body", [4, 5.5, 4], [12, 10, 12], "frame"),
        ("upper_core", [4, 10, 4], [12, 12.4, 12], "frame"),
        ("crown_step", [3, 12.4, 3], [13, 13.7, 13], "frame"),
        ("crown", [4, 13.7, 4], [12, 15, 12], "frame"),
        ("crown_socket", [5, 15, 5], [11, 16, 11], "energy"),
    ]
    supports = [
        ("northwest_buttress", [2, 3, 2], [4, 12.4, 4], "frame"),
        ("northeast_buttress", [12, 3, 2], [14, 12.4, 4], "frame"),
        ("southwest_buttress", [2, 3, 12], [4, 12.4, 14], "frame"),
        ("southeast_buttress", [12, 3, 12], [14, 12.4, 14], "frame"),
    ]
    if state == "full":
        parts += supports
    elif state == "damaged":
        parts = [p for p in parts if p[0] not in ("upper_core", "crown")]
        parts += supports[:3]
        parts += [("broken_upper_left", [4, 10, 4], [7.7, 12.4, 12], "frame"),
                  ("broken_crown_left", [4, 13.7, 4], [7.7, 15, 12], "frame")]
    else:
        parts = [p for p in parts if p[0] not in ("seal_body", "upper_core", "crown_step", "crown")]
        parts += supports[:1]
        parts += [("exposed_core_fragment", [4, 5.5, 4], [7.3, 10, 12], "frame"),
                  ("critical_crown_fragment", [5, 12.4, 5], [8.1, 13.7, 11], "frame")]
    elements = [cuboid(name, low, high, texture, world_scale=OBELISK_SCALE)
                for name, low, high, texture in parts]
    # Front/back square seal faces: matching world width/height keeps the
    # supplied HD artwork intact and prevents the old fivefold vertical stretch.
    extent = 6.4 if state != "critical" else 2.8
    height = extent * OBELISK_SCALE[0] / OBELISK_SCALE[1]
    for side in ("north", "south"):
        if state == "critical" and side in ("south", "east"):
            continue
        x1 = 8 - extent / 2 if state != "critical" else 4.2
        x2 = x1 + extent
        if side == "north": low, high = [x1, 5.7, 3.90], [x2, 5.7 + height, 3.96]
        elif side == "south": low, high = [x1, 5.7, 12.04], [x2, 5.7 + height, 12.10]
        elif side == "west": low, high = [3.90, 5.7, x1], [3.96, 5.7 + height, x2]
        else: low, high = [12.04, 5.7, x1], [12.10, 5.7 + height, x2]
        panel = cuboid("health_seal_" + side, low, high, "frame",
                       world_scale=OBELISK_SCALE, uv=[0, 0, 16, 16])
        panel['faces'][side]['texture'] = '#rift'
        elements.append(panel)
    if state == "full":
        for x in (3.90, 12.04):
            elements.append(cuboid("stable_energy_spine_" + str(x), [x, 3.1, 7.8],
                                   [x + .06, 12.2, 8.2], "energy", world_scale=OBELISK_SCALE))
    elif state == "damaged":
        elements.append(cuboid("fracture_energy", [7.72, 10.1, 4.2], [7.78, 12.1, 11.8],
                               "energy", world_scale=OBELISK_SCALE))
    return structure(elements, frame=STONE, energy=ENERGY,
                     rift=f"copimine:item/end_event_rift_obelisk_{state}_hd")


def carrier_charge():
    elements = [
        cuboid("lower_charge_cap", [6, 3, 6], [10, 5.5, 10],
               rotation=rotate([8, 4.25, 8], 45, "y"), world_scale=(1, 1, 1)),
        cuboid("upper_charge_cap", [6, 10.5, 6], [10, 13, 10],
               rotation=rotate([8, 11.75, 8], 45, "y"), world_scale=(1, 1, 1)),
        cuboid("cyan_charge_core", [5.5, 5.5, 5.5], [10.5, 10.5, 10.5], "energy",
               rotation=rotate([8, 8, 8], 45, "y"), world_scale=(1, 1, 1)),
        cuboid("white_energy_inset", [7.5, 5.1, 7.5], [8.5, 10.9, 8.5], "energy",
               world_scale=(1, 1, 1)),
    ]
    return structure(elements, frame=STONE, energy="copimine:item/end_event_carrier_charge")


def charge_texture():
    image = Image.new("RGBA", (32, 32), (27, 203, 239, 255))
    for y in range(32):
        for x in range(32):
            if (x + y * 2) % 13 < 2:
                image.putpixel((x, y), (179, 249, 255, 255))
            elif (x * 3 + y) % 11 < 2:
                image.putpixel((x, y), (30, 145, 183, 255))
    return image


def stone_texture():
    texture = Image.new("RGBA", (32, 32))
    for y in range(32):
        for x in range(32):
            variation = ((x * 17 + y * 11 + x * y * 3) % 13) - 6
            seam = 4 if (x + 2 * y) % 19 < 2 else 0
            texture.putpixel((x, y), (23 + variation + seam, 17 + variation,
                                     35 + variation + seam, 255))
    draw = ImageDraw.Draw(texture)
    draw.line([(3, 0), (8, 7), (6, 11), (15, 18), (13, 24), (18, 31)],
              fill=(43, 28, 57, 255), width=1)
    draw.line([(24, 0), (21, 9), (25, 14), (22, 22), (28, 31)],
              fill=(11, 8, 18, 255), width=1)
    return texture


def energy_texture():
    texture = Image.new("RGBA", (32, 32))
    for y in range(32):
        for x in range(32):
            bright = (x * 7 + y * 11) % 9
            texture.putpixel((x, y), (198 + bright * 5, 39 + bright * 4,
                                     224 + bright * 3, 255))
    return texture


def membrane_texture():
    texture = Image.new("RGBA", (32, 1024), (0, 0, 0, 0))
    for frame in range(8):
        for y in range(72):
            for x in range(32):
                nx, ny = (x - 15.5) / 16, (y - 35.5) / 36
                radius = math.sqrt(nx * nx + ny * ny)
                if radius > .98:
                    continue
                pulse = math.sin(radius * 20 - frame * math.pi / 4 + math.atan2(ny, nx) * 2)
                crack = (x * 3 + y * 5 + frame * 4) % 31
                if crack == 0:
                    rgba = (0, 0, 0, 0)
                elif radius < .25:
                    rgba = (8, 3, 17, 190)
                elif pulse > .83:
                    rgba = (203, 37, 224, 255)
                elif pulse > .45:
                    rgba = (84, 22, 140, 235)
                else:
                    rgba = (24, 9, 47, 215)
                texture.putpixel((x, frame * 128 + y), rgba)
    return texture


def generate(output_root=ROOT):
    output_root = Path(output_root)
    outputs = []

    def write(relative, data):
        target = output_root / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        if isinstance(data, Image.Image):
            target.write_bytes(png_bytes(data))
        else:
            target.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
        outputs.append(relative)

    prefix = "src/assets/copimine/"
    write(prefix + "models/item/end_event_rift_gate.json", portal_frame())
    write(prefix + "models/item/end_event_carrier_charge.json", carrier_charge())
    write(prefix + "textures/item/end_event_carrier_charge.png", charge_texture())
    # The layout entrance and retired 830007 keep their own baseline geometry;
    # canonical Wave 3 uses 830018/830008/830009 only.
    write(prefix + "models/block/end_event_portal_inner.json", portal_membrane())
    write(prefix + "models/block/end_event_portal_shard.json", portal_shards())
    for state in ("full", "damaged", "critical"):
        write(prefix + f"models/item/end_event_rift_obelisk_{state}.json", obelisk(state))
    write(prefix + "textures/item/end_event_rift_structure_stone.png", stone_texture())
    write(prefix + "textures/item/end_event_rift_structure_energy.png", energy_texture())
    write(prefix + "textures/item/end_event_rift_membrane.png", membrane_texture())
    write(prefix + "textures/item/end_event_rift_membrane.png.mcmeta",
          {"animation": {"width": 32, "height": 128, "frametime": 3,
                         "interpolate": False, "frames": list(range(8))}})
    hashes = {relative: hashlib.sha256((output_root / relative).read_bytes()).hexdigest()
              for relative in outputs}
    write(prefix + "manifests/waves_3_4_structure_assets.json", {
        "source": "resourcepacks/tools/generate_wave3_wave4_structures.py",
        "license": "Original CopiMine procedural artwork and geometry; no third-party source",
        "native_visual_acceptance": "NOT VERIFIED: requires current installed Minecraft captures",
        "carrier_charge": {"custom_model_data": 830029, "base_material": "paper",
                           "model": "copimine:item/end_event_carrier_charge",
                           "usage": "W1 owned drop/presentation; never reuse portal shard layer"},
        "portal": {"model_floor_y": -8, "item_none_center": [8, 8, 8],
                   "world_scale": 2.24, "floor_translation": [0, 2.24, 0],
                   "target_size_blocks": [4.48, 5.6, 1.42],
                   "layers": {"frame": 830018, "membrane": 830008, "shards": 830009},
                   "progress": "Server keeps frame footprint fixed; independently compresses membrane/shards at 0/25/50/75/100"},
        "obelisk": {"item_none_bounds": [-.5, .5], "model_floor_y": 0,
                    "world_scale": [3.25, 5, 3.25], "floor_translation": [0, 2.5, 0],
                    "collision_presentation": "Use invisible journal-owned collision cells; opaque blocks occlude the authored model",
                    "health_models": {"full": 830010, "damaged": 830011, "critical": 830012},
                    "supplied_hd_textures": "Preserved without writing"},
        "sha256": hashes,
    })
    return outputs


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-root", type=Path, default=ROOT)
    args = parser.parse_args()
    for relative in generate(args.output_root):
        print(relative)
