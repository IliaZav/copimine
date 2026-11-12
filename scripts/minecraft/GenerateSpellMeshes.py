"""Build the closed 3D spell meshes the elite abilities fly with.

The three elite spells used to share one pair of textures, so every cast looked
identical no matter which ability fired. Each spell now owns an atlas and a mesh
of its own, and the mesh geometry follows the spell's actual role:

  rift_bolt      a forward-running spear: elongated core plus a tail fin
  void_burst     a compact swelling orb with a cracked shell and spikes
  dragon_breath  a stubby wide-mouthed cone, like a snout venting flame

Every element is emitted with UV rectangles taken from the 64x64 atlas in
GenerateSpellAtlases.py: the core element samples cell 0, shell elements sample
cells 1-5, and the rim seam samples an accent cell. Faces are all present and
double-sided-safe (shade off) so the mesh never develops holes at any angle.
"""

from __future__ import annotations

import json
from pathlib import Path

ATLAS = 64
CELL = 16


def _uv(cell: int):
    x = (cell % 4) * CELL
    y = (cell // 4) * CELL
    return [x, y, x + CELL, y + CELL]


def _box(start, end, texture: str, cell: int, shade: bool = False):
    uv = _uv(cell)
    return {
        "from": list(start),
        "to": list(end),
        "shade": shade,
        "faces": {
            face: {"uv": list(uv), "texture": texture}
            for face in ("down", "up", "north", "south", "west", "east")
        },
    }


def rift_bolt_elements():
    """A spear running along +Z: bright core, dark shell, four stabiliser fins."""
    elements = [
        # Forward tip - narrow, hottest part of the spell.
        _box([7, 7, 13], [9, 9, 16], "#core", 0),
        # Bright spine through the whole body.
        _box([7, 7, 2], [9, 9, 13], "#core", 0),
        # Dark shell around the spine, leaving the spine's silhouette visible.
        _box([5, 5, 3], [11, 11, 12], "#shell", 1),
        # Rear fin cluster, deliberately asymmetric so spin reads in flight.
        _box([4, 7, 0], [12, 9, 3], "#shell", 2),
        _box([7, 4, 0], [9, 12, 3], "#shell", 3),
        _box([6, 6, 1], [10, 10, 4], "#seam", 8),
    ]
    return elements


def void_burst_elements():
    """A swelling orb: shell, inner void, cracks and four outward spikes."""
    elements = [
        _box([4, 4, 4], [12, 12, 12], "#shell", 1),
        _box([6, 6, 6], [10, 10, 10], "#core", 0),
        # Cracked seam ring around the equator.
        _box([3, 7, 3], [13, 9, 5], "#seam", 8),
        _box([3, 7, 11], [13, 9, 13], "#seam", 9),
        # Spikes: up, down, left, right - the swelling pressure of a void burst.
        _box([7, 12, 7], [9, 15, 9], "#seam", 10),
        _box([7, 1, 7], [9, 4, 9], "#seam", 10),
        _box([1, 7, 7], [4, 9, 9], "#seam", 10),
        _box([12, 7, 7], [15, 9, 9], "#seam", 10),
    ]
    return elements


def dragon_breath_elements():
    """A snout venting flame: wide mouth at +Z, narrow throat, hot inner cone."""
    elements = [
        # Wide mouth.
        _box([3, 3, 12], [13, 13, 15], "#shell", 1),
        # Throat narrowing toward the back.
        _box([5, 5, 6], [11, 11, 12], "#shell", 2),
        # Inner flame cone, bright, sits inside the mouth.
        _box([6, 6, 8], [10, 10, 14], "#core", 0),
        # Ember fringe on the mouth rim.
        _box([2, 6, 13], [4, 10, 15], "#seam", 8),
        _box([12, 6, 13], [14, 10, 15], "#seam", 9),
        _box([6, 2, 13], [10, 4, 15], "#seam", 9),
    ]
    return elements


MESHES = {
    "end_rift_rift_bolt": rift_bolt_elements,
    "end_rift_void_burst": void_burst_elements,
    "end_rift_dragon_breath": dragon_breath_elements,
}


def build_model(name: str):
    textures = {
        "particle": f"copimine:item/{name}",
        "core": f"copimine:item/{name}",
        "shell": f"copimine:item/{name}",
        "seam": f"copimine:item/{name}",
    }
    return {
        "parent": "minecraft:block/block",
        "ambientocclusion": False,
        "textures": textures,
        "elements": MESHES[name](),
        "display": {
            # The display entity renders the model fixed in world space, so the
            # blocky 16-unit editing space maps straight onto the 0.42 scale the
            # server applies to the ItemDisplay transformation.
            "fixed": {"rotation": [0, 0, 0], "scale": [1, 1, 1]},
            "gui": {"rotation": [0, 0, 0], "scale": [1, 1, 1]},
        },
    }


def main() -> int:
    root = Path(__file__).resolve().parents[2]
    target = root / "resourcepacks" / "src" / "assets" / "copimine" / "models" / "item"
    target.mkdir(parents=True, exist_ok=True)
    for name in MESHES:
        model = build_model(name)
        destination = target / f"{name}.json"
        destination.write_text(
            json.dumps(model, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        print(f"wrote {destination.relative_to(root)} elements={len(model['elements'])}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
