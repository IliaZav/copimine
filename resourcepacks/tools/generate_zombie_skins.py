"""Generate the two event-zombie skins from the vanilla 64x64 zombie layout.

Keeping the vanilla silhouette guarantees correct UV faces; only the palette
and a few accent pixels change, so vanilla zombies stay untouched.
Run: python resourcepacks/tools/generate_zombie_skins.py <path-to-vanilla-zombie.png>
"""
from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "CopiMineClient/src/main/resources/assets/copimineclient/textures/entity"

# front face of the head is the u=8..15, v=8..15 slab of the 64x64 layout
HEAD_FRONT = (8, 8, 16, 16)
CHEST_FRONT = (20, 20, 28, 32)


def brightness(px: tuple[int, int, int]) -> float:
    return 0.299 * px[0] + 0.587 * px[1] + 0.114 * px[2]


def category(px: tuple[int, int, int]) -> str:
    r, g, b = px
    if g > r + 8 and g > b + 8:
        return "skin"
    if r < 24 and g > 70 and b > 70 and abs(g - b) < 40:
        return "cloth"
    if b > g + 20 and b > 55:
        return "trouser"
    return "dark"


VARIANTS = {
    # ordinary event zombie: cold rift-green flesh, violet rags, cyan glare
    "end_rift_zombie.png": {
        "skin": (86, 138, 112),
        "cloth": (58, 44, 86),
        "trouser": (38, 33, 62),
        "dark": (22, 28, 30),
        "eye": (126, 246, 234),
        "rune": (146, 214, 176),
    },
    # elite rift giant: near-black hide, blood-violet plate, magenta glare
    "end_rift_elite_zombie.png": {
        "skin": (96, 62, 118),
        "cloth": (74, 26, 42),
        "trouser": (30, 24, 38),
        "dark": (16, 14, 20),
        "eye": (255, 96, 186),
        "rune": (214, 62, 150),
    },
}

REFERENCE = {"skin": 0.44, "cloth": 0.45, "trouser": 0.20, "dark": 0.12}


def tint(px: tuple[int, int, int], target: tuple[int, int, int], reference: float) -> tuple[int, int, int, int]:
    factor = brightness(px) / max(1.0, reference * 255.0)
    factor = min(1.35, max(0.45, factor))
    return (
        min(255, round(target[0] * factor)),
        min(255, round(target[1] * factor)),
        min(255, round(target[2] * factor)),
        255,
    )


def inside(x: int, y: int, box: tuple[int, int, int, int]) -> bool:
    return box[0] <= x < box[2] and box[1] <= y < box[3]


def build(source: Image.Image, variant: dict) -> Image.Image:
    src = source.convert("RGBA")
    out = Image.new("RGBA", src.size, (0, 0, 0, 0))
    src_px, out_px = src.load(), out.load()
    for y in range(src.height):
        for x in range(src.width):
            r, g, b, a = src_px[x, y]
            if a == 0:
                continue
            key = category((r, g, b))
            px = tint((r, g, b), variant[key], REFERENCE[key])
            if key == "dark" and brightness((r, g, b)) < 40 and inside(x, y, HEAD_FRONT):
                px = (*variant["eye"], 255)
            out_px[x, y] = px
    # a short rune column on the chest so both roles read as event mobs
    accent = (*variant["rune"], 255)
    for y in (22, 24, 26, 28):
        x = 23 if y % 4 == 2 else 24
        if inside(x, y, CHEST_FRONT):
            out_px[x, y] = accent
    return out


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    source = Image.open(sys.argv[1])
    OUT.mkdir(parents=True, exist_ok=True)
    for name, variant in VARIANTS.items():
        build(source, variant).save(OUT / name, optimize=True)
        print("wrote", (OUT / name).relative_to(ROOT))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())