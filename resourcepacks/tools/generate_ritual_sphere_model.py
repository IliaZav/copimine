from __future__ import annotations

import argparse
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MODEL = ROOT / "src" / "assets" / "copimine" / "models" / "item" / "end_event_ritual_sphere.json"
GRID_CENTERS = tuple(range(-7, 8, 2))
INNER_RADIUS = 5.2
OUTER_RADIUS = 7.8
FACES = ("down", "up", "north", "south", "west", "east")


def build_model() -> dict:
    elements = []
    for x in GRID_CENTERS:
        for y in GRID_CENTERS:
            for z in GRID_CENTERS:
                radius_squared = x * x + y * y + z * z
                if not INNER_RADIUS * INNER_RADIUS <= radius_squared <= OUTER_RADIUS * OUTER_RADIUS:
                    continue
                start = [axis + 7 for axis in (x, y, z)]
                end = [axis + 2 for axis in start]
                elements.append({
                    "from": start,
                    "to": end,
                    "shade": False,
                    "faces": {face: {"texture": "#shell"} for face in FACES},
                })

    return {
        "parent": "minecraft:item/generated",
        "render_type": "minecraft:translucent",
        "ambientocclusion": False,
        "textures": {
            "shell": "copimine:item/end_event_ritual_shell",
            "particle": "copimine:item/end_event_ritual_shell",
        },
        "elements": elements,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    expected = json.dumps(build_model(), indent=2) + "\n"

    if args.check:
        if not MODEL.is_file() or MODEL.read_text(encoding="utf-8") != expected:
            print(f"Ritual sphere model is stale: {MODEL}")
            return 1
        print("Ritual sphere model matches its deterministic generator.")
        return 0

    MODEL.write_text(expected, encoding="utf-8", newline="\n")
    print(f"Generated {MODEL}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
