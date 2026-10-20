"""Original stable green-white Wave 5 floor seal; write only named assets."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

from PIL import Image, ImageDraw
try:
    from resourcepacks.tools.deterministic_png import png_bytes
except ModuleNotFoundError:
    from deterministic_png import png_bytes

ROOT = Path(__file__).resolve().parents[1]
PREFIX = "src/assets/copimine/"


def texture():
    image = Image.new("RGBA", (32, 32), (19, 32, 34, 255))
    draw = ImageDraw.Draw(image)
    for y in range(32):
        for x in range(32):
            variation = (x * 11 + y * 7) % 5
            image.putpixel((x, y), (19 + variation, 32 + variation, 34 + variation, 255))
    draw.rectangle((1, 1, 30, 30), outline=(58, 110, 92, 255), width=1)
    draw.rectangle((3, 3, 28, 28), outline=(106, 194, 150, 255), width=1)
    draw.line([(15, 7), (24, 16), (15, 25), (6, 16), (15, 7)],
              fill=(173, 239, 206, 255), width=1)
    draw.line([(15, 11), (20, 16), (15, 21), (10, 16), (15, 11)],
              fill=(61, 128, 101, 255), width=1)
    draw.rectangle((14, 14, 17, 17), fill=(207, 255, 231, 255))
    for x, y in ((4, 4), (27, 4), (4, 27), (27, 27)):
        draw.rectangle((x - 1, y - 1, x + 1, y + 1), fill=(151, 224, 185, 255))
    return image


def model():
    elements = []
    for name, low, high in (
        ("stable_floor_plate", [0, 8, 0], [16, 8.2, 16]),
        ("north_energy_rim", [0, 8.2, 0], [16, 8.35, .35]),
        ("south_energy_rim", [0, 8.2, 15.65], [16, 8.35, 16]),
        ("west_energy_rim", [0, 8.2, .35], [.35, 8.35, 15.65]),
        ("east_energy_rim", [15.65, 8.2, .35], [16, 8.35, 15.65]),
    ):
        faces = {}
        for side in ("down", "up", "north", "south", "west", "east"):
            if side in ("up", "down"):
                uv = [low[0], low[2], high[0], high[2]]
            elif side in ("north", "south"):
                uv = [low[0], 0, high[0], high[1] - low[1]]
            else:
                uv = [low[2], 0, high[2], high[1] - low[1]]
            faces[side] = {"uv": uv, "texture": "#seal"}
        elements.append({"name": name, "from": low, "to": high, "faces": faces})
    return {"parent": "minecraft:block/block", "ambientocclusion": False, "gui_light": "front",
            "textures": {"particle": "copimine:item/end_event_safe_floor",
                         "seal": "copimine:item/end_event_safe_floor"}, "elements": elements}


def generate(output_root=ROOT):
    output_root = Path(output_root)
    outputs = []

    def write(relative, data):
        path = output_root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        if isinstance(data, Image.Image):
            path.write_bytes(png_bytes(data))
        else:
            path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
        outputs.append(relative)

    write(PREFIX + "models/item/end_event_safe_floor.json", model())
    write(PREFIX + "textures/item/end_event_safe_floor.png", texture())
    write(PREFIX + "manifests/wave5_safe_floor_asset.json", {
        "source": "resourcepacks/tools/generate_wave5_safe_tile.py",
        "license": "Original CopiMine procedural artwork and geometry; no third-party source",
        "custom_model_data": 830027, "base_material": "paper",
        "item_none_origin": [8, 8, 8], "world_scale": [1, 1, 1],
        "translation_blocks": [0, .015, 0], "anchor": "combatFloorY()+1, cell center",
        "height_blocks": .021875,
        "native_visual_acceptance": "NOT VERIFIED: current installed Minecraft captures required",
        "sha256": {relative: hashlib.sha256((output_root / relative).read_bytes()).hexdigest()
                   for relative in outputs},
    })
    return outputs


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-root", type=Path, default=ROOT)
    args = parser.parse_args()
    for relative in generate(args.output_root):
        print(relative)
