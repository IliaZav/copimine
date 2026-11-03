
"""Authored entity textures for the End Rift named Wave 7 trial actors.

Generated deterministically by resourcepacks/tools/generate_wave7_actor_skins.py and
mirrored into the active 26.3 client resource roots by the same script.

Palette follows the authored rift direction already used by the event:
blackened rift hide, corrupted violet cloth, emerald seals, and a pale bone
highlight so the silhouettes read at arena distance.
"""
from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]

# Rift palette (locked; the same values are used by every actor so the three
# named trials read as one family).
RIFT_BLACK = (13, 10, 17)
RIFT_DEEP = (26, 18, 34)
RIFT_HIDE = (38, 26, 50)
RIFT_HIDE_LIT = (58, 40, 74)
RIFT_VIOLET = (94, 58, 138)
RIFT_VIOLET_LIT = (132, 84, 180)
RIFT_GLOW = (186, 126, 236)
RIFT_EMERALD = (46, 196, 148)
RIFT_EMERALD_LIT = (128, 246, 200)
BONE = (206, 196, 168)
BONE_SHADE = (150, 140, 118)
BONE_DARK = (104, 96, 82)
CLOTH = (58, 44, 78)
CLOTH_LIT = (86, 66, 112)
METAL = (66, 70, 86)
METAL_LIT = (120, 126, 148)
EYE = (240, 110, 90)


def _canvas(width: int, height: int) -> Image.Image:
    return Image.new("RGBA", (width, height), (0, 0, 0, 0))


def _noise(seed: int, x: int, y: int) -> float:
    """Deterministic 0..1 hash noise; keeps regeneration byte-identical."""
    v = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    v = (v ^ (v >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((v ^ (v >> 16)) & 0xFFFF) / 65535.0


def fill_box(draw: ImageDraw.ImageDraw, x: int, y: int, w: int, h: int,
             base: tuple[int, int, int], seed: int, spread: int = 14,
             edge: tuple[int, int, int] | None = None) -> None:
    """Fill a UV island with rift hide that darkens toward its borders."""
    for yy in range(h):
        for xx in range(w):
            n = _noise(seed, x + xx, y + yy)
            gx = min(xx, w - 1 - xx) / max(1, (w - 1) / 2.0)
            gy = min(yy, h - 1 - yy) / max(1, (h - 1) / 2.0)
            shade = 0.55 + 0.45 * min(1.0, min(gx, gy) * 1.6)
            jitter = int((n - 0.5) * spread)
            px = tuple(max(0, min(255, int(c * shade) + jitter)) for c in base)
            draw.point((x + xx, y + yy), fill=px + (255,))
    if edge is not None:
        draw.rectangle([x, y, x + w - 1, y], fill=edge + (255,))
        draw.rectangle([x, y + h - 1, x + w - 1, y + h - 1], fill=edge + (255,))


def plank_lines(draw: ImageDraw.ImageDraw, x: int, y: int, w: int, h: int,
                step: int, color: tuple[int, int, int]) -> None:
    for yy in range(y + step, y + h - 1, step):
        draw.line([(x + 1, yy), (x + w - 2, yy)], fill=color + (255,))


def rune(draw: ImageDraw.ImageDraw, cx: float, cy: float, size: float,
         color: tuple[int, int, int]) -> None:
    """Small four-point rift rune; readable at 1px per texel."""
    s = max(1.0, size)
    draw.point((round(cx), round(cy)), fill=color + (255,))
    draw.point((round(cx - s), round(cy)), fill=color + (255,))
    draw.point((round(cx + s), round(cy)), fill=color + (255,))
    draw.point((round(cx), round(cy - s)), fill=color + (255,))
    draw.point((round(cx), round(cy + s)), fill=color + (255,))


def glowing_crack(draw: ImageDraw.ImageDraw, x0: int, y0: int, x1: int, y1: int,
                  color: tuple[int, int, int], seed: int) -> None:
    """A jagged vertical seam with a pair of brighter core texels."""
    steps = max(1, y1 - y0)
    for i in range(steps + 1):
        t = i / steps
        xx = x0 + (x1 - x0) * t + (0.6 if _noise(seed, i, 3) > 0.5 else -0.6)
        yy = y0 + i
        draw.point((round(xx), yy), fill=color + (255,))
        if i % 3 == 0:
            draw.point((round(xx) + 1, yy), fill=tuple(min(255, c + 40) for c in color) + (255,))


# --------------------------------------------------------------------------
# Warden (Ghost Warden / Ripula of the Rift) - vanilla Warden atlas 128x128
# --------------------------------------------------------------------------

def build_warden() -> Image.Image:
    img = _canvas(128, 128)
    d = ImageDraw.Draw(img)

    # Head block: u 0..16, v 0..16 front, sides continue to u 32.
    fill_box(d, 0, 0, 16, 16, RIFT_HIDE, 11, 16, RIFT_DEEP)      # front
    fill_box(d, 16, 0, 16, 16, RIFT_DEEP, 12, 12, RIFT_BLACK)    # right
    fill_box(d, 32, 0, 16, 16, RIFT_HIDE, 13, 14, RIFT_BLACK)    # back
    fill_box(d, 48, 0, 16, 16, RIFT_DEEP, 14, 12, RIFT_BLACK)    # left
    fill_box(d, 0, 16, 16, 8, RIFT_BLACK, 15, 8)                 # top
    fill_box(d, 16, 16, 16, 8, RIFT_BLACK, 16, 8)                # bottom

    # Chest cavity: a rune of emerald light under the head on the front face.
    for (px, py) in [(6, 5), (7, 5), (8, 5), (9, 5),
                     (5, 6), (10, 6), (7, 7), (8, 7), (6, 8), (9, 8),
                     (7, 9), (8, 9), (7, 10), (8, 10)]:
        d.point((px, py), fill=RIFT_EMERALD_LIT + (255,))
    for (px, py) in [(5, 5), (10, 5), (4, 6), (11, 6), (6, 7), (9, 7)]:
        d.point((px, py), fill=RIFT_EMERALD + (255,))
    # Two cold eyes on the front and side faces.
    for (px, py) in [(4, 3), (5, 3), (10, 3), (11, 3)]:
        d.point((px, py), fill=RIFT_GLOW + (255,))
    for (px, py) in [(19, 4), (27, 4)]:
        d.point((px, py), fill=RIFT_GLOW + (255,))

    # Torso: vanilla warden uses the left half of the texture below the head.
    fill_box(d, 0, 24, 28, 24, RIFT_DEEP, 21, 14, RIFT_BLACK)   # back
    fill_box(d, 0, 48, 24, 16, RIFT_HIDE, 22, 16, RIFT_BLACK)   # right side
    fill_box(d, 0, 64, 16, 16, RIFT_HIDE, 23, 16, RIFT_BLACK)   # front
    fill_box(d, 16, 48, 24, 16, RIFT_HIDE_LIT, 24, 14, RIFT_BLACK)
    # Emerald rib lanterns down the front of the torso.
    for col in (4, 8, 12):
        glowing_crack(d, col, 64, col, 79, RIFT_EMERALD, 100 + col)
    for row in range(66, 79, 4):
        d.point((8, row), fill=RIFT_EMERALD_LIT + (255,))
    # Violet pulse along the belly (Warden's heart).
    for (px, py) in [(7, 70), (8, 70), (9, 70), (7, 71), (9, 71), (8, 72),
                     (7, 73), (8, 73), (9, 73)]:
        d.point((px, py), fill=RIFT_VIOLET_LIT + (255,))

    # Arms and legs.
    fill_box(d, 40, 0, 16, 16, RIFT_HIDE, 31, 12, RIFT_BLACK)   # right arm upper
    fill_box(d, 40, 16, 16, 24, RIFT_DEEP, 32, 12, RIFT_BLACK)  # right arm lower
    fill_box(d, 72, 0, 16, 16, RIFT_HIDE, 33, 12, RIFT_BLACK)   # left arm upper
    fill_box(d, 72, 16, 16, 24, RIFT_DEEP, 34, 12, RIFT_BLACK)  # left arm lower
    fill_box(d, 0, 84, 12, 16, RIFT_HIDE, 35, 12, RIFT_BLACK)   # leg upper
    fill_box(d, 0, 100, 12, 16, RIFT_DEEP, 36, 12, RIFT_BLACK)  # leg lower
    fill_box(d, 16, 84, 12, 16, RIFT_HIDE, 37, 12, RIFT_BLACK)
    fill_box(d, 16, 100, 12, 16, RIFT_DEEP, 38, 12, RIFT_BLACK)

    # Claw light on the forearms and shin guards.
    for y in range(18, 40, 3):
        d.point((46, y), fill=RIFT_EMERALD + (255,))
        d.point((78, y), fill=RIFT_EMERALD + (255,))
    for y in range(102, 116, 3):
        d.point((5, y), fill=RIFT_EMERALD + (255,))
        d.point((21, y), fill=RIFT_EMERALD + (255,))

    # Authored add-on islands used by Wave7WardenModel (crown, chest plate, spines).
    fill_box(d, 72, 0, 8, 4, RIFT_VIOLET, 41, 10, RIFT_BLACK)   # rift_crown
    for px in range(73, 79):
        d.point((px, 2), fill=RIFT_GLOW + (255,))
    fill_box(d, 72, 12, 10, 7, RIFT_VIOLET, 42, 10, RIFT_BLACK)  # rift_chest_plate
    for px in range(74, 80):
        d.point((px, 15), fill=RIFT_EMERALD_LIT + (255,))
    fill_box(d, 96, 0, 8, 10, RIFT_DEEP, 43, 10, RIFT_BLACK)    # rift_back_spines
    for py in range(0, 10, 2):
        d.point((99, py), fill=RIFT_VIOLET_LIT + (255,))
    return img


# --------------------------------------------------------------------------
# Archmage - custom rig UV layout (64x64), see Wave7ArchmageModel
# --------------------------------------------------------------------------

def build_archmage() -> Image.Image:
    img = _canvas(64, 64)
    d = ImageDraw.Draw(img)

    fill_box(d, 0, 0, 32, 16, RIFT_HIDE, 51, 12, RIFT_BLACK)     # head cube
    fill_box(d, 32, 0, 16, 24, CLOTH, 52, 12, RIFT_BLACK)        # hood shell
    fill_box(d, 56, 0, 2, 3, RIFT_GLOW, 53, 4)                   # hood rune
    fill_box(d, 24, 0, 8, 6, RIFT_DEEP, 54, 6, RIFT_BLACK)       # nose

    # Face: deep black void with two violet eyes and a rune-slit.
    for (px, py) in [(6, 6), (7, 6), (8, 6), (9, 6), (6, 7), (9, 7)]:
        d.point((px, py), fill=RIFT_GLOW + (255,))
    for (px, py) in [(22, 6), (23, 6), (24, 6), (25, 6), (22, 7), (25, 7)]:
        d.point((px, py), fill=RIFT_GLOW + (255,))
    for py in range(9, 14):
        d.point((16, py), fill=RIFT_EMERALD + (255,))

    fill_box(d, 16, 20, 32, 18, RIFT_DEEP, 55, 12, RIFT_BLACK)   # body + sides
    fill_box(d, 16, 20, 8, 12, CLOTH, 56, 10, RIFT_BLACK)
    fill_box(d, 24, 20, 8, 12, CLOTH_LIT, 57, 10, RIFT_BLACK)
    fill_box(d, 16, 38, 32, 12, RIFT_VIOLET, 58, 10, RIFT_BLACK)  # robe hem
    for px in range(17, 47, 3):
        d.point((px, 44), fill=RIFT_EMERALD + (255,))
    fill_box(d, 48, 20, 8, 8, RIFT_EMERALD, 59, 8)                # chest seal
    for (px, py) in [(50, 22), (51, 22), (52, 22), (53, 22), (51, 21), (52, 23)]:
        d.point((px, py), fill=RIFT_EMERALD_LIT + (255,))

    fill_box(d, 40, 22, 8, 12, CLOTH, 60, 8, RIFT_BLACK)          # right arm
    fill_box(d, 40, 38, 8, 12, CLOTH, 60, 8, RIFT_BLACK)          # left arm
    fill_box(d, 56, 38, 1, 18, BONE_DARK, 61, 4)                  # staff shaft
    for py in range(38, 56, 4):
        d.point((56, py), fill=RIFT_GLOW + (255,))
    fill_box(d, 48, 42, 4, 4, RIFT_VIOLET_LIT, 62, 6)             # focus orb
    d.point((49, 43), fill=RIFT_GLOW + (255,))
    d.point((50, 44), fill=RIFT_GLOW + (255,))

    fill_box(d, 0, 22, 16, 12, CLOTH, 63, 8, RIFT_BLACK)          # legs
    fill_box(d, 0, 38, 16, 12, RIFT_DEEP, 64, 8, RIFT_BLACK)
    for py in range(40, 50, 3):
        d.point((3, py), fill=RIFT_EMERALD + (255,))
        d.point((11, py), fill=RIFT_EMERALD + (255,))
    return img


# --------------------------------------------------------------------------
# Marksman - skeleton atlas 64x32, same islands as RiftEventSkeletonModel
# --------------------------------------------------------------------------

def build_marksman() -> Image.Image:
    img = _canvas(64, 32)
    d = ImageDraw.Draw(img)

    fill_box(d, 0, 0, 32, 16, BONE_SHADE, 71, 10, BONE_DARK)      # skull
    # Deep hood over the skull with a thin bone face showing through.
    for yy in range(0, 5):
        for xx in range(0, 32):
            d.point((xx, yy), fill=CLOTH + (255,))
    for py in range(5, 15):
        for px in range(4, 12):
            if px in (4, 11) or py in (5, 14):
                d.point((px, py), fill=BONE_DARK + (255,))
    for (px, py) in [(6, 8), (7, 8), (8, 8), (9, 8), (6, 9), (9, 9),
                     (22, 8), (23, 8), (24, 8), (25, 8), (22, 9), (25, 9)]:
        d.point((px, py), fill=RIFT_EMERALD_LIT + (255,))
    fill_box(d, 48, 16, 4, 2, RIFT_VIOLET, 72, 6)                 # marksman_sight
    for px in range(48, 52):
        d.point((px, 16), fill=RIFT_GLOW + (255,))

    fill_box(d, 16, 16, 24, 12, BONE, 73, 12, BONE_DARK)          # torso + arms + legs islands
    fill_box(d, 24, 17, 8, 4, RIFT_DEEP, 74, 8)                   # chest rift
    for py in range(17, 21):
        d.point((28, py), fill=RIFT_VIOLET_LIT + (255,))
    fill_box(d, 40, 20, 10, 8, RIFT_DEEP, 75, 8, RIFT_BLACK)      # quiver island
    for py in range(20, 28, 2):
        d.point((42, py), fill=BONE + (255,))
        d.point((46, py), fill=RIFT_EMERALD + (255,))
    # Arms: bone with violet wraps.
    for (ux, vx) in ((40, 16), (48, 16)):
        for py in range(16, 22, 2):
            d.point((ux, py), fill=CLOTH_LIT + (255,))
    # Legs.
    for (ux, vx) in ((0, 16), (8, 16)):
        for py in range(21, 22):
            d.point((ux, py), fill=CLOTH_LIT + (255,))
    return img


def main() -> None:
    targets = [
        (ROOT / "assets" / "copimineclient" / "textures" / "entity",
         "end_rift_wave7_warden.png", build_warden),
        (ROOT / "assets" / "copimineclient" / "textures" / "entity",
         "end_rift_wave7_archmage.png", build_archmage),
        (ROOT / "assets" / "copimineclient" / "textures" / "entity",
         "end_rift_wave7_marksman.png", build_marksman),
    ]
    for directory, name, builder in targets:
        target = directory / name
        target.parent.mkdir(parents=True, exist_ok=True)
        image = builder()
        assert image.mode == "RGBA"
        assert any(px[3] for px in image.getdata()), name
        image.save(target, optimize=True)
        print("wrote", target, image.size)


if __name__ == "__main__":
    main()
