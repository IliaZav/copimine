"""Build the closed 3D spell meshes the elite abilities fly with.

Every elite and boss projectile is authored geometry, never a flat sprite: the
spell is a real display entity in flight, so it needs a closed mesh and a UV
atlas. Historically all elite spells shared one pair of textures, so every cast
looked identical no matter which ability fired. Each spell now owns an atlas and
a mesh of its own, and the geometry follows the spell's actual role:

  web_shot        a thrown cobweb bundle: solid core inside a tangle of strands
  rift_bolt       a forward-running spear: elongated core plus a tail fin
  dragon_breath   a stubby wide-mouthed cone, like a snout venting flame
  void_burst      a compact swelling orb with a cracked shell and spikes
  triple_arrow    a bone-white bolt with three swept-back fletching vanes
  arrow_rain      a thin shaft falling nose-down, dark violet tip, trailing barb
  explosive_arrow a swollen head with a molten seam and an ignition stub

Every element is emitted with UV rectangles taken from the 64x64 atlas in
GenerateSpellAtlases.py: the core element samples cell 0, shell elements sample
cells 1-5, and the rim seam samples an accent cell. Faces are all present and
double-sided-safe (shade off) so the mesh never develops holes at any angle.
This module also writes the per-item definition the 26.3 client copies, so a
regenerated mesh and its item definition can never drift apart.
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


def web_shot_elements():
    """A thrown cobweb bundle: a solid core wrapped in a tangle of strands."""
    elements = [
        # Solid core - the weight the spider spits, centred in the tangle.
        _box([5, 5, 2], [11, 11, 14], "#shell", 1),
        # Front tangle, holding the bundle's leading face open.
        _box([7, 2, 12], [9, 14, 15], "#seam", 8),
        _box([2, 7, 12], [14, 9, 15], "#seam", 9),
        # Rear tangle, so the silhouette differs on both ends.
        _box([7, 3, 1], [9, 13, 4], "#seam", 8),
        # Strands wrapped around the body, deliberately not mirrored.
        _box([2, 7, 3], [4, 9, 12], "#seam", 9),
        _box([12, 7, 4], [14, 9, 11], "#seam", 10),
        # Cross strands that bridge the core to the tangle on each flank.
        _box([3, 7, 6], [5, 9, 11], "#seam", 9),
        _box([11, 7, 5], [13, 9, 10], "#seam", 10),
        # A loose trailing strand so the bundle reads as tumbling in flight.
        _box([6, 6, 14], [9, 9, 16], "#seam", 8),
    ]
    return elements


def triple_arrow_elements():
    """A bone-white bolt with three swept-back fletching vanes."""
    elements = [
        # Bone shaft running the whole bolt.
        _box([7, 7, 2], [9, 9, 15], "#core", 0),
        # Needle head.
        _box([7, 7, 15], [9, 9, 16], "#seam", 8),
        # Three vanes around the nock: top, bottom and one flank.
        _box([7, 10, 2], [9, 11, 7], "#shell", 1),
        _box([7, 5, 2], [9, 6, 7], "#shell", 1),
        _box([10, 7, 2], [11, 9, 7], "#shell", 2),
        # Nock block that completes the closed rear body.
        _box([6, 6, 1], [10, 10, 3], "#shell", 3),
    ]
    return elements


def arrow_rain_elements():
    """A thin shaft falling nose-down: narrow body, dark tip, trailing barb."""
    elements = [
        # Long thin shaft.
        _box([7, 7, 3], [9, 9, 14], "#core", 0),
        # Dark violet tip, pointing down for a descending volley.
        _box([6, 2, 2], [10, 6, 6], "#seam", 8),
        # Collar joining tip to shaft.
        _box([6, 6, 4], [10, 8, 7], "#shell", 1),
        # Trailing barb far above the tip.
        _box([7, 11, 12], [9, 13, 15], "#shell", 2),
        _box([4, 9, 12], [7, 11, 14], "#shell", 2),
        _box([7, 9, 12], [9, 11, 14], "#shell", 3),
    ]
    return elements


def explosive_arrow_elements():
    """A swollen explosive head with a molten seam and ignition stub."""
    elements = [
        # Swollen head - the densest element of the spell.
        _box([4, 4, 8], [12, 12, 16], "#shell", 1),
        # Molten seam splitting the head and continuing into the shaft.
        _box([7, 4, 6], [9, 12, 9], "#seam", 8),
        # Remaining shaft behind the seam.
        _box([7, 7, 1], [9, 9, 6], "#core", 0),
        # Ignition stub: the fuse that reads as "about to burst". It must stay
        # inside the 16-unit editing space, so it rises off the head's top face.
        _box([7, 7, 15], [9, 9, 16], "#core", 0),
        # Head lugs, uneven so the spell never reads as a plain cube.
        _box([3, 6, 10], [5, 10, 14], "#shell", 2),
        _box([11, 5, 9], [13, 9, 13], "#shell", 3),
    ]
    return elements


MESHES = {
    "end_rift_web_shot": web_shot_elements,
    "end_rift_rift_bolt": rift_bolt_elements,
    "end_rift_dragon_breath": dragon_breath_elements,
    "end_rift_void_burst": void_burst_elements,
    "end_rift_triple_arrow": triple_arrow_elements,
    "end_rift_arrow_rain": arrow_rain_elements,
    "end_rift_explosive_arrow": explosive_arrow_elements,
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


def build_definition(name: str):
    """The 26.3 item definition that points the projectile at its authored model."""
    return {"model": {"type": "minecraft:model", "model": f"copimine:item/{name}"}}


def main() -> int:
    root = Path(__file__).resolve().parents[2]
    assets = root / "resourcepacks" / "src" / "assets" / "copimine"
    target = assets / "models" / "item"
    definitions = assets / "items"
    target.mkdir(parents=True, exist_ok=True)
    definitions.mkdir(parents=True, exist_ok=True)
    for name in MESHES:
        model = build_model(name)
        destination = target / f"{name}.json"
        # .gitattributes pins eol=lf for these paths, and the 26.3 client build
        # copies them byte-for-byte into the jar. Inside the checkout that means
        # LF, but write_text() defaults to os.linesep, so a Windows run emitted
        # CRLF and shifted the pinned client digest by 24 bytes. Pin LF here so
        # the generated bytes are identical on every platform.
        destination.write_text(
            json.dumps(model, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n"
        )
        definition = definitions / f"{name}.json"
        definition.write_text(
            json.dumps(build_definition(name), ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
            newline="\n",
        )
        print(f"wrote {destination.relative_to(root)} elements={len(model['elements'])}")
        print(f"wrote {definition.relative_to(root)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
