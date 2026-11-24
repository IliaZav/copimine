"""Author the three Phase 4 (OVERLOAD) infernal summon textures.

N23-P1-09 needs custom-textured vanilla-type bodies: an infernal Magma Cube, a
Blaze and a Wither Skeleton. The meshes are the real vanilla models (so the
existing client mixins can bind a per-UUID texture), and these atlases supply
the authored cold-obsidian + rift-fire look.

Sizes follow the vanilla atlases the models sample:
  * magma_cube: 64x32
  * blaze:      64x32
  * wither:     skeleton family, 64x32

Run: python CopiMineClient/tools/generate_inferno_summon_atlases.py
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src" / "main" / "resources" / "assets" / "copimineclient" / "textures" / "entity"


def _hash(x: int, y: int, salt: int) -> int:
    value = (x * 73856093) ^ (y * 19349663) ^ (salt * 83492791)
    value &= 0xFFFFFFFF
    value ^= value >> 13
    value = (value * 1274126177) & 0xFFFFFFFF
    return value


def magma_atlas() -> Image.Image:
    image = Image.new("RGBA", (64, 32), (27, 13, 16, 255))
    draw = ImageDraw.Draw(image)
    for y in range(32):
        for x in range(64):
            noise = _hash(x, y, 1) % 26
            r = min(255, 27 + noise)
            draw.point((x, y), (r, min(255, 13 + noise // 2), min(255, 16 + noise // 3), 255))
    # Glowing magma veins: diagonal hot lines with a bright core.
    for offset in range(-32, 64, 7):
        for y in range(32):
            x = (y + offset) % 64
            draw.point((x, y), (255, 106, 0, 255))
            draw.point(((x + 1) % 64, y), (255, 178, 40, 255))
    for _ in range(36):
        cx = _hash(_, 3, 2) % 64
        cy = _hash(_, 7, 4) % 32
        draw.rectangle([cx, cy, cx + 1, cy], fill=(255, 214, 92, 255))
    return image


def blaze_atlas() -> Image.Image:
    image = Image.new("RGBA", (64, 32), (22, 11, 28, 255))
    draw = ImageDraw.Draw(image)
    for y in range(32):
        for x in range(64):
            noise = _hash(x, y, 5) % 22
            draw.point((x, y), (22 + noise, 11 + noise // 2, 28 + noise, 255))
    # Vertical magma rods of a Blaze, re-lit as violet rift fire.
    for rod in range(0, 64, 8):
        draw.rectangle([rod + 2, 4, rod + 4, 27], fill=(233, 200, 255, 255))
        draw.rectangle([rod + 3, 6, rod + 3, 25], fill=(255, 255, 255, 255))
    for y in range(32):
        for x in range(64):
            if (x + y) % 6 == 0:
                draw.point((x, y), (200, 123, 255, 255))
    return image


def wither_atlas() -> Image.Image:
    image = Image.new("RGBA", (64, 32), (58, 47, 56, 255))
    draw = ImageDraw.Draw(image)
    for y in range(32):
        for x in range(64):
            noise = _hash(x, y, 9) % 20
            draw.point((x, y), (58 + noise, 47 + noise, 56 + noise, 255))
    # Dark seam lines between "bones".
    for y in range(0, 32, 4):
        for x in range(64):
            draw.point((x, y), (28, 22, 30, 255))
    # Glowing rift-violet eye sockets on the front head island (classic 8x8 head).
    for ex, ey in ((10, 9), (13, 9)):
        draw.point((ex, ey), (255, 120, 255, 255))
        draw.point((ex + 1, ey), (199, 123, 255, 255))
    return image


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    assets = {
        "end_rift_inferno_magma.png": magma_atlas(),
        "end_rift_inferno_blaze.png": blaze_atlas(),
        "end_rift_inferno_wither.png": wither_atlas(),
    }
    for name, image in assets.items():
        path = OUT / name
        image.save(path)
        print(f"wrote {path} ({image.width}x{image.height})")


if __name__ == "__main__":
    main()
