
"""Authored texture for the Archmage's Vex servants ("вредины").

The vanilla Vex sheet is a washed-out grey spirit that reads as background noise
at arena distance. These are not background: they are the Archmage's summons,
they carry iron swords, and they are the reason a party fighting the Archmage
has to look up. The sheet keeps the vanilla 64x64 Vex UV layout exactly, so no
model change is needed -- only the surface.

Palette is the rift family already used by the three named trial actors: black
wing membrane, deep violet body, emerald seal where the summon is bound, and a
hot magenta eye so a swarm of them is countable in the dark.

Generated deterministically by resourcepacks/tools/generate_vex_servant_skin.py;
running it twice produces byte-identical output.
"""
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUTPUTS = [
    ROOT / "assets" / "copimineclient" / "textures" / "entity",
]

SIZE = 64

RIFT_BLACK = (11, 8, 14)
RIFT_DEEP = (24, 16, 31)
RIFT_HIDE = (44, 30, 58)
HIDE_LIT = (68, 46, 88)
VIOLET = (108, 66, 156)
VIOLET_LIT = (150, 100, 202)
GLOW = (198, 140, 246)
EMERALD = (46, 196, 148)
EMERALD_LIT = (140, 250, 206)
BONE = (208, 198, 172)
EYE = (255, 96, 220)
EYE_CORE = (255, 226, 255)


def _noise(seed: int, x: int, y: int) -> float:
    v = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    v = (v ^ (v >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((v ^ (v >> 16)) & 0xFFFF) / 65535.0


def fill(draw: ImageDraw.ImageDraw, box: tuple[int, int, int, int],
         base: tuple[int, int, int], seed: int, spread: int = 12,
         edge: tuple[int, int, int] | None = None) -> None:
    """Fill a UV island with rift hide that darkens toward its own border."""
    x0, y0, x1, y1 = box
    w = x1 - x0 + 1
    h = y1 - y0 + 1
    for yy in range(h):
        for xx in range(w):
            n = _noise(seed, x0 + xx, y0 + yy)
            gx = min(xx, w - 1 - xx) / max(1.0, (w - 1) / 2.0)
            gy = min(yy, h - 1 - yy) / max(1.0, (h - 1) / 2.0)
            shade = 0.55 + 0.45 * min(1.0, min(gx, gy) * 1.5)
            jitter = int((n - 0.5) * spread)
            px = tuple(max(0, min(255, int(c * shade) + jitter)) for c in base)
            draw.point((x0 + xx, y0 + yy), fill=px + (255,))
    if edge is not None:
        draw.rectangle([x0, y0, x1, y0], fill=edge + (255,))
        draw.rectangle([x0, y1, x1, y1], fill=edge + (255,))
        draw.rectangle([x0, y0, x0, y1], fill=edge + (255,))
        draw.rectangle([x1, y0, x1, y1], fill=edge + (255,))


def rune(draw: ImageDraw.ImageDraw, cx: int, cy: int, color: tuple[int, int, int]) -> None:
    """Four-point seal, readable at one texel per pixel."""
    for dx, dy in ((0, 0), (-1, 0), (1, 0), (0, -1), (0, 1)):
        draw.point((cx + dx, cy + dy), fill=color + (255,))


def build_vex_servant() -> Image.Image:
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)

    # --- Head (vanilla Vex head UV: 0,0 .. 31,15) -------------------------
    fill(draw, (0, 0, 31, 15), RIFT_HIDE, 11, 14, edge=RIFT_DEEP)
    # Face plate: the front of the head is the 8x8 island at x=8..15, y=8..15.
    fill(draw, (8, 8, 15, 15), RIFT_BLACK, 12, 8)
    for eye_x in (9, 13):
        draw.rectangle([eye_x, 10, eye_x + 1, 12], fill=EYE + (255,))
        draw.point((eye_x, 11), fill=EYE_CORE + (255,))
    # Cheek seals, so the face reads as BOUND rather than as a blank mask.
    draw.point((8, 14), fill=EMERALD + (255,))
    draw.point((15, 14), fill=EMERALD + (255,))
    # Crown horns on the top island.
    for hx in (10, 12, 14):
        draw.line([(hx, 0), (hx, 3)], fill=VIOLET_LIT + (255,))
        draw.point((hx, 0), fill=GLOW + (255,))

    # --- Body (vanilla Vex body UV: 32,0 .. 63,15) -----------------------
    fill(draw, (32, 0, 63, 15), RIFT_DEEP, 21, 12, edge=RIFT_BLACK)
    # Front torso island 20x12 at x=40..59, y=8..19 -> ribbed cloth.
    for yy in range(9, 19, 2):
        draw.line([(41, yy), (58, yy)], fill=VIOLET + (255,))
    rune(draw, 50, 13, EMERALD_LIT)
    draw.rectangle([49, 14, 51, 16], fill=RIFT_BLACK + (255,))
    rune(draw, 50, 15, EMERALD)

    # --- Arms (vanilla Vex arm UV: 0,16 .. 31,31) ------------------------
    fill(draw, (0, 16, 31, 31), HIDE_LIT, 31, 12, edge=RIFT_DEEP)
    for xx in (4, 12, 20, 28):
        draw.line([(xx, 17), (xx, 30)], fill=VIOLET + (255,))

    # --- Wings (vanilla Vex wing UV: 32,16 .. 63,31) ---------------------
    # Membrane: near-black with violet veins, so the silhouette reads against
    # the bright arena floor instead of dissolving into it.
    fill(draw, (32, 16, 63, 31), RIFT_BLACK, 41, 6, edge=RIFT_DEEP)
    for x0, y0, x1, y1 in ((33, 30, 44, 17), (45, 30, 54, 18), (55, 30, 62, 20)):
        draw.line([(x0, y0), (x1, y1)], fill=VIOLET + (255,))
        draw.line([(x0 + 1, y0), (x1 + 1, y1)], fill=RIFT_HIDE + (255,))
    # Emerald summon seal on the wing root.
    rune(draw, 34, 29, EMERALD)

    # --- Sword hand highlight (vanilla Vex right/left arm tip) -----------
    for x0 in (0, 16):
        fill(draw, (x0 + 1, 24, x0 + 6, 29), BONE, 51, 8)
        draw.point((x0 + 3, 26), fill=RIFT_BLACK + (255,))

    return image


def main() -> None:
    image = build_vex_servant()
    assert image.mode == "RGBA"
    assert image.size == (SIZE, SIZE)
    assert any(px[3] for px in image.getdata()), "vex servant sheet is fully transparent"
    for directory in OUTPUTS:
        target = directory / "end_rift_wave7_vex_servant.png"
        target.parent.mkdir(parents=True, exist_ok=True)
        image.save(target, optimize=True)
        print("wrote", target, image.size)


if __name__ == "__main__":
    main()
