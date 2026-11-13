"""Build the UV atlases the elite spell meshes sample.

Each spell projectile is a closed 3D mesh, so it needs a texture that can be
sampled on six faces rather than a flat 2D silhouette. These atlases are
generated, not hand-painted, so every face is guaranteed to be non-empty and
the seven spells stay deliberately different from one another:

  web_shot         pale cobweb silk   - bone-white strands over a grey core
  rift_bolt        violet arcane      - cold rift core, bright seam, dark shell
  dragon_breath    burning magenta    - hot core bleeding into soot
  void_burst       near-black void    - almost no light, lavender cracks
  triple_arrow     bleached bone      - dry ivory shaft, grey-blue vanes
  arrow_rain       night-violet rain  - thin cold shaft, dark violet tip
  explosive_arrow  molten orange      - ember head, white-hot molten seam

The atlas is 64x64 and is laid out as a 4x4 grid of 16x16 cells. Cell 0 is the
"core" face (the glowing centre of the spell), cells 1-5 are the shell faces,
and the remaining cells carry the accent seam used along the silhouette edge.
"""

from __future__ import annotations

import math
from pathlib import Path

from PIL import Image

ATLAS = 64
CELL = 16

# Every entry is (base, core, seam). Colours are deliberately far apart so a
# viewer can tell the three spells apart at a glance in flight.
PALETTES = {
    "end_rift_web_shot": ((0xE4, 0xE2, 0xDC), (0xFF, 0xFF, 0xFF), (0x9A, 0x97, 0x90)),
    "end_rift_rift_bolt": ((0x2D, 0x10, 0x4C), (0xC8, 0x7B, 0xFF), (0x7A, 0x2E, 0xC9)),
    "end_rift_dragon_breath": ((0x53, 0x21, 0x6B), (0xFF, 0xB4, 0x5C), (0x8A, 0x44, 0xA4)),
    "end_rift_void_burst": ((0x13, 0x04, 0x27), (0xE9, 0xD4, 0xFB), (0x4B, 0x1C, 0x7A)),
    "end_rift_triple_arrow": ((0xD8, 0xD2, 0xBF), (0xFF, 0xFB, 0xEE), (0x6F, 0x7C, 0x86)),
    "end_rift_arrow_rain": ((0x1C, 0x12, 0x30), (0xB6, 0x9C, 0xE8), (0x3B, 0x27, 0x63)),
    "end_rift_explosive_arrow": ((0x7A, 0x22, 0x06), (0xFF, 0xF1, 0xC4), (0xF2, 0x7D, 0x0F)),
}


def _clamp(value: float) -> int:
    return max(0, min(255, int(round(value))))


def _mix(a, b, t: float):
    return (
        _clamp(a[0] + (b[0] - a[0]) * t),
        _clamp(a[1] + (b[1] - a[1]) * t),
        _clamp(a[2] + (b[2] - a[2]) * t),
    )


def _cell_origin(cell: int):
    return (cell % 4) * CELL, (cell // 4) * CELL


def build_atlas(base, core, seam) -> Image.Image:
    image = Image.new("RGBA", (ATLAS, ATLAS), (0, 0, 0, 0))
    pixels = image.load()
    # Only cells 0-11 carry mesh data (three atlas rows). The last row stays
    # fully transparent so the atlas keeps a genuine alpha range: an entirely
    # opaque sheet means every UV lands on paint, which is how the old shared
    # texture made all three spells look identical.
    for cell in range(12):
        ox, oy = _cell_origin(cell)
        if cell == 0:
            # The core face: a hot radial centre so the spell reads as a light
            # source when it is closest to the camera.
            for y in range(CELL):
                for x in range(CELL):
                    dx = (x + 0.5) / CELL - 0.5
                    dy = (y + 0.5) / CELL - 0.5
                    radial = min(1.0, math.hypot(dx, dy) * 2.2)
                    pixels[ox + x, oy + y] = (*_mix(core, base, radial ** 0.75), 255)
        elif cell < 6:
            # Shell faces: vertical energy streak, dark at the edges so the
            # silhouette stays readable against bright arena floors.
            phase = cell * 1.7
            for y in range(CELL):
                for x in range(CELL):
                    band = 0.5 + 0.5 * math.sin((x / CELL) * math.pi * 2.0 + phase)
                    vertical = 1.0 - abs((y + 0.5) / CELL - 0.5) * 1.1
                    t = max(0.0, min(1.0, band * 0.55 + vertical * 0.45))
                    colour = _mix(base, seam, t ** 1.6)
                    pixels[ox + x, oy + y] = (*colour, 255)
        else:
            # Accent cells: the bright seam that runs along the rim.
            for y in range(CELL):
                for x in range(CELL):
                    edge = max(abs((x + 0.5) / CELL - 0.5), abs((y + 0.5) / CELL - 0.5)) * 2.0
                    t = max(0.0, min(1.0, 1.0 - edge))
                    colour = _mix(base, core, t ** 2.2)
                    pixels[ox + x, oy + y] = (*colour, 255)
    return image


def main() -> int:
    root = Path(__file__).resolve().parents[2]
    target = root / "resourcepacks" / "src" / "assets" / "copimine" / "textures" / "item"
    target.mkdir(parents=True, exist_ok=True)
    for name, (base, core, seam) in PALETTES.items():
        image = build_atlas(base, core, seam)
        destination = target / f"{name}.png"
        image.save(destination, format="PNG", optimize=True)
        print(f"wrote {destination.relative_to(root)} {image.size}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
