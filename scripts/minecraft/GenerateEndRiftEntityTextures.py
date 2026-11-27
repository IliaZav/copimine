#!/usr/bin/env python3
"""Deterministically re-author the End Rift entity skins.

The owner's 2026-11-21 brief replaced every End Rift actor sheet except the
Archmage's vex servant and Echo (Echo wears the player skin, so it owns no
sheet here).  The shipped sheets were procedural noise: they read as coloured
static at gameplay distance and gave no actor a silhouette of its own.

This generator paints each sheet from an explicit material contract:

* the *source luminance* of the shipped sheet is kept, so every cube still
  shades the way the model's author intended;
* that shading is re-quantised into a five-stop hand-picked ramp per actor, so
  the sheet gains a real material instead of random hue noise;
* a deterministic authored motif (plates, scales, chitin rings, woven cloth)
  and a per-actor rune decal give each actor a recognisable identity;
* a silhouette rim light keeps the dark actors readable against the void.

The Guardian also gets six progressive "last stand" stages.  They are derived
from one master sheet so the recolor is gradual, and only the final stage is
torn open and drained, exactly as the owner asked.

Every run writes byte-identical PNGs; ``--check`` compares the committed
assets against a fresh paint and fails on drift, which is what the Python
contract test drives.
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import math
import random
from dataclasses import dataclass, field
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
CANONICAL = ROOT / "CopiMineClient/src/main/resources/assets/copimineclient/textures/entity"
SOURCE_ASSETS = ROOT / "scripts/minecraft/source-assets"


def rgb(value: str) -> tuple[int, int, int]:
    value = value.lstrip("#")
    return (int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16))


def mix(a: tuple[int, int, int], b: tuple[int, int, int], t: float) -> tuple[int, int, int]:
    t = max(0.0, min(1.0, t))
    return (round(a[0] + (b[0] - a[0]) * t),
            round(a[1] + (b[1] - a[1]) * t),
            round(a[2] + (b[2] - a[2]) * t))


def clamp(value: float, low: float = 0.0, high: float = 1.0) -> float:
    return max(low, min(high, value))


def luminance(pixel: tuple[int, int, int, int]) -> float:
    return (0.299 * pixel[0] + 0.587 * pixel[1] + 0.114 * pixel[2]) / 255.0


@dataclass(frozen=True)
class Spec:
    name: str
    stops: tuple[tuple[int, int, int], ...]
    seed: int
    motif: str
    accent: tuple[int, int, int]
    accent2: tuple[int, int, int] | None = None
    contrast: float = 1.05
    pattern: float = 0.10
    runes: int = 6
    rim: float = 0.22
    patchwork: float = 0.10
    # Per-actor exposure: the shipped Warden and Ram sheets are almost black,
    # so a literal luminance copy stays black. Lift re-exposes the material.
    lift: float = 0.0


BOSS = Spec(
    name="end_rift_user_boss",
    stops=(rgb("#08060F"), rgb("#151026"), rgb("#2A2042"), rgb("#5A4A78"), rgb("#B9A279")),
    seed=0xB055,
    motif="plate",
    accent=rgb("#7FE9FF"),
    accent2=rgb("#D9B45C"),
    contrast=1.10,
    pattern=0.12,
    runes=10,
    rim=0.30,
    patchwork=0.12,
)

SPECS: tuple[Spec, ...] = (
    Spec(
        name="end_rift_endermite",
        stops=(rgb("#0D0518"), rgb("#241039"), rgb("#451A6B"), rgb("#7C33B8"), rgb("#D07BFF")),
        seed=0x31117E,
        motif="chitin",
        accent=rgb("#8FF6FF"),
        accent2=rgb("#F06BFF"),
        contrast=1.15,
        pattern=0.16,
        runes=3,
        rim=0.26,
        lift=0.04,
    ),
    Spec(
        name="end_rift_spire_harbinger",
        stops=(rgb("#0D0816"), rgb("#2D1A36"), rgb("#66556A"), rgb("#BC9D89"), rgb("#F3D8AE")),
        seed=0x5A1BE,
        motif="bone",
        accent=rgb("#FFD78F"),
        accent2=rgb("#D581FA"),
        contrast=1.26,
        pattern=0.19,
        runes=3,
        rim=0.31,
    ),
    Spec(
        name="end_rift_wave7_warden",
        stops=(rgb("#04060A"), rgb("#0B1420"), rgb("#16303F"), rgb("#2F6A72"), rgb("#7FE0CE")),
        seed=0x7A2DE,
        motif="hide",
        accent=rgb("#63F2D0"),
        accent2=rgb("#8E6BFF"),
        contrast=1.18,
        pattern=0.13,
        runes=9,
        rim=0.30,
        patchwork=0.16,
        lift=0.02,
    ),
    Spec(
        name="end_rift_wave7_archmage",
        stops=(rgb("#0A0614"), rgb("#1E1030"), rgb("#3A1D5C"), rgb("#6B3AA8"), rgb("#B98CFF")),
        seed=0xA4C4E,
        motif="weave",
        accent=rgb("#D8B45E"),
        accent2=rgb("#74F0E0"),
        contrast=1.12,
        pattern=0.12,
        runes=7,
        rim=0.24,
        lift=0.0,
    ),
    Spec(
        name="end_rift_wave7_marksman",
        stops=(rgb("#140F0A"), rgb("#3A2A1C"), rgb("#6B4B2E"), rgb("#A9814F"), rgb("#D6BC8A")),
        seed=0x4A2C5,
        motif="leather",
        accent=rgb("#6EE7C8"),
        accent2=rgb("#B98CFF"),
        contrast=1.10,
        pattern=0.14,
        runes=5,
        rim=0.22,
        lift=0.0,
    ),
)

RAM_STOPS = (rgb("#0B0710"), rgb("#231320"), rgb("#43243A"), rgb("#6E3A52"), rgb("#C7B49C"))

RAM = Spec(
    name="end_rift_ram",
    stops=RAM_STOPS,
    seed=0x2A000,
    motif="armor",
    accent=rgb("#B36BFF"),
    accent2=rgb("#D9CFB4"),
    contrast=1.14,
    pattern=0.15,
    runes=7,
    rim=0.28,
    patchwork=0.14,
)

SPECS = (RAM,) + SPECS

SOURCE_ASSETS = ROOT / "scripts/minecraft/source-assets"
SOURCES = {
    "end_rift_ram": SOURCE_ASSETS / "ravager_vanilla.png",
    "end_rift_user_boss": SOURCE_ASSETS / "end_rift_user_boss.png",
    "end_rift_endermite": SOURCE_ASSETS / "end_rift_endermite.png",
    "end_rift_spire_harbinger": SOURCE_ASSETS / "end_rift_spire_harbinger.png",
    "end_rift_wave7_warden": SOURCE_ASSETS / "end_rift_wave7_warden.png",
    "end_rift_wave7_archmage": SOURCE_ASSETS / "end_rift_wave7_archmage.png",
    "end_rift_wave7_marksman": SOURCE_ASSETS / "end_rift_wave7_marksman.png",
}


# --------------------------------------------------------------------------
# Motifs.  Each returns a shade offset in roughly [-1, 1] for one pixel.
# --------------------------------------------------------------------------

def motif_plate(x: int, y: int, rng: random.Random) -> float:
    plate = 8
    seam = 1 if (y % plate == 0 or (x + (y // plate) * 3) % plate == 0) else 0
    rivet = 1 if ((x % plate == 2) and (y % plate == 2)) else 0
    bevel = (x % plate) / plate
    return (-0.55 * seam) + (0.35 * rivet) + (0.10 * (bevel - 0.5))


def motif_scale(x: int, y: int, rng: random.Random) -> float:
    row = y // 4
    shifted = x + (row % 2) * 3
    edge = 1 if (shifted % 7 == 0 or y % 4 == 0) else 0
    return (-0.45 * edge) + 0.12 * math.sin((x + y) * 0.7)


def motif_chitin(x: int, y: int, rng: random.Random) -> float:
    ring = 1 if (x % 8 == 1) else 0
    spine = 1 if (y % 7 == 2 and x % 8 in (3, 4)) else 0
    return (-0.50 * ring) + (0.40 * spine) + 0.10 * math.cos(y * 0.9)


def motif_bone(x: int, y: int, rng: random.Random) -> float:
    crack = 1 if ((x * 3 + y * 5) % 23 == 0) else 0
    joint = 1 if (y % 9 == 0) else 0
    return (-0.40 * crack) - (0.20 * joint) + 0.10 * math.sin(x * 0.5 + y * 0.3)


def motif_hide(x: int, y: int, rng: random.Random) -> float:
    vein = 1 if ((x + y * 2) % 17 == 0 or (x * 2 - y) % 29 == 0) else 0
    pore = 1 if rng.random() < 0.06 else 0
    return (0.35 * vein) - (0.25 * pore)


def motif_weave(x: int, y: int, rng: random.Random) -> float:
    warp = 1 if x % 4 == 0 else 0
    weft = 1 if y % 4 == 0 else 0
    return 0.14 * (warp + weft) - 0.10 * (warp * weft) - 0.05


def motif_leather(x: int, y: int, rng: random.Random) -> float:
    grain = 1 if ((x * 7 + y * 3) % 31 == 0) else 0
    stitch = 1 if (x % 9 == 4 and y % 3 == 0) else 0
    return (-0.20 * grain) + (0.30 * stitch)


def motif_armor(x: int, y: int, rng: random.Random) -> float:
    band = 1 if y % 11 == 0 else 0
    rib = 1 if (x % 13 == 0 and (y // 11) % 2 == 0) else 0
    return (-0.50 * band) + (0.25 * rib) + 0.08 * math.sin(x * 0.4)


MOTIFS = {
    "plate": motif_plate,
    "scale": motif_scale,
    "chitin": motif_chitin,
    "bone": motif_bone,
    "hide": motif_hide,
    "weave": motif_weave,
    "leather": motif_leather,
    "armor": motif_armor,
}


GLYPHS = (
    ("010", "111", "010"),
    ("110", "010", "011"),
    ("101", "111", "101"),
    ("011", "110", "010"),
    ("100", "111", "001"),
    ("010", "101", "010"),
)


def ramp_color(stops: tuple[tuple[int, int, int], ...], value: float) -> tuple[int, int, int]:
    value = clamp(value)
    scaled = value * (len(stops) - 1)
    index = min(len(stops) - 2, int(scaled))
    return mix(stops[index], stops[index + 1], scaled - index)


def smooth_noise(px: int, py: int, seed: int, period: float) -> float:
    """Deterministic smooth value noise in [-1, 1] without external deps."""
    def hashed(ix: int, iy: int) -> float:
        n = (ix * 374761393 + iy * 668265263 + seed * 2654435761) & 0xFFFFFFFF
        n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
        return ((n ^ (n >> 16)) & 0xFFFF) / 32767.5 - 1.0

    fx, fy = px / period, py / period
    ix, iy = math.floor(fx), math.floor(fy)
    tx, ty = fx - ix, fy - iy
    tx = tx * tx * (3 - 2 * tx)
    ty = ty * ty * (3 - 2 * ty)
    top = hashed(ix, iy) + (hashed(ix + 1, iy) - hashed(ix, iy)) * tx
    bottom = hashed(ix, iy + 1) + (hashed(ix + 1, iy + 1) - hashed(ix, iy + 1)) * tx
    return top + (bottom - top) * ty


def paint(source: Image.Image, spec: Spec) -> Image.Image:
    source = source.convert("RGBA")
    width, height = source.size
    result = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    src = source.load()
    out = result.load()
    rng = random.Random(spec.seed)
    motif = MOTIFS[spec.motif]

    # Pre-compute silhouette mask so the rim light can look one pixel outward.
    opaque = [[src[x, y][3] > 8 for x in range(width)] for y in range(height)]

    # The shipped sheets have wildly different exposure: the Warden's whole
    # body sits between luminance 6 and 21, so copying its luminance literally
    # keeps it a black blob. Normalise each sheet against its own 5th/95th
    # percentile before it enters the actor's ramp.
    measured = sorted(luminance(src[x, y]) for y in range(height) for x in range(width)
                      if opaque[y][x])
    if measured:
        low = measured[int(len(measured) * 0.05)]
        high = measured[int(len(measured) * 0.95)]
        span = max(0.10, high - low)
    else:
        low, span = 0.0, 1.0

    # The shipped sheets are dithered per pixel.  Keeping that dither at
    # gameplay contrast produces television static, so the form is read from a
    # small alpha-weighted blur: broad mass and crevices survive, the dither
    # does not.  Authored motifs supply the high frequency instead.
    levels = [[clamp((luminance(src[x, y]) - low) / span) if opaque[y][x] else 0.0
               for x in range(width)] for y in range(height)]
    weights = [[1.0 if opaque[y][x] else 0.0 for x in range(width)] for y in range(height)]
    form = [[0.0] * width for _ in range(height)]
    radius = 2
    for y in range(height):
        for x in range(width):
            if not opaque[y][x]:
                continue
            total = 0.0
            weight = 0.0
            for dy in range(-radius, radius + 1):
                for dx in range(-radius, radius + 1):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < width and 0 <= ny < height:
                        w = weights[ny][nx]
                        total += levels[ny][nx] * w
                        weight += w
            form[y][x] = total / weight if weight else levels[y][x]

    # Rune positions live only where the source is reasonably flat and bright.
    candidates = [
        (x, y) for y in range(2, height - 3) for x in range(2, width - 3)
        if opaque[y][x] and 0.25 < clamp((luminance(src[x, y]) - low) / span) < 0.85
    ]
    rng.shuffle(candidates)
    rune_cells: dict[tuple[int, int], tuple[int, int, int]] = {}
    placed = 0
    for (cx, cy) in candidates:
        if placed >= spec.runes:
            break
        if any(abs(cx - rx) < 6 and abs(cy - ry) < 6 for rx, ry in rune_cells):
            continue
        glyph = GLYPHS[rng.randrange(len(GLYPHS))]
        color = spec.accent if rng.random() < 0.75 else (spec.accent2 or spec.accent)
        for gy, row in enumerate(glyph):
            for gx, bit in enumerate(row):
                if bit == "1":
                    rune_cells[(cx + gx, cy + gy)] = color
        placed += 1

    for y in range(height):
        for x in range(width):
            r, g, b, a = src[x, y]
            if a <= 8:
                out[x, y] = (0, 0, 0, 0)
                continue
            level = form[y][x]
            patch = smooth_noise(x, y, spec.seed, 11.0) * spec.patchwork
            detail = motif(x, y, rng) * spec.pattern
            value = clamp(0.42 + spec.lift + (level - 0.5) * spec.contrast * 0.20
                          + patch + detail * 1.6)
            color = ramp_color(spec.stops, value)

            # Sparse ember/vein accents inside the darkest and brightest bands.
            roll = smooth_noise(x, y, spec.seed ^ 0x9E37, 5.0)
            if level > 0.72 and roll > 0.55:
                color = mix(color, spec.accent, 0.35 * (roll - 0.55))
            elif level < 0.22 and roll < -0.55:
                color = mix(color, spec.accent, 0.28 * (-roll - 0.55))

            out[x, y] = (color[0], color[1], color[2], a)

    # Rune decals after the base paint so they stay crisp.
    for (x, y), color in rune_cells.items():
        if 0 <= x < width and 0 <= y < height and src[x, y][3] > 8:
            out[x, y] = (color[0], color[1], color[2], 255)
            for dx, dy in ((0, -1), (-1, 0), (1, 0), (0, 1)):
                gx, gy = x + dx, y + dy
                if 0 <= gx < width and 0 <= gy < height and src[gx, gy][3] > 8:
                    base = out[gx, gy]
                    glow = mix((base[0], base[1], base[2]), color, 0.30)
                    out[gx, gy] = (glow[0], glow[1], glow[2], base[3])

    # Silhouette rim: light the outermost opaque pixel so dark actors read.
    rimmed = result.copy()
    rp = rimmed.load()
    for y in range(height):
        for x in range(width):
            if not opaque[y][x]:
                continue
            border = False
            for dx, dy in ((0, -1), (0, 1), (-1, 0), (1, 0)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < width and 0 <= ny < height and not opaque[ny][nx]:
                    border = True
                    break
            if border:
                r0, g0, b0, a0 = rp[x, y]
                light = mix((r0, g0, b0), spec.stops[-1], spec.rim)
                rp[x, y] = (light[0], light[1], light[2], a0)
    return rimmed


# --------------------------------------------------------------------------
# Guardian last-stand stages.
# --------------------------------------------------------------------------

def tear(image: Image.Image, stage: int, seed: int) -> Image.Image:
    """Adds deterministic jagged gashes; stage 0..5 grows monotonically."""
    if stage <= 0:
        return image
    img = image.convert("RGBA").copy()
    px = img.load()
    width, height = img.size
    rng = random.Random(seed)
    gashes = stage * 4
    for _ in range(gashes):
        for _attempt in range(40):
            x, y = rng.randrange(width), rng.randrange(height)
            if px[x, y][3] > 8:
                break
        else:
            continue
        length = 3 + rng.randrange(2 + stage * 2)
        dx = rng.choice((-1, 0, 1))
        for step in range(length):
            gx, gy = x + step * dx, y + step
            if 0 <= gx < width and 0 <= gy < height and px[gx, gy][3] > 8:
                r0, g0, b0, a0 = px[gx, gy]
                # Torn cells expose a pale, bloodless underlayer then go black.
                if step % 3 == 1:
                    px[gx, gy] = (198, 186, 168, a0)
                elif step % 3 == 2:
                    px[gx, gy] = (96, 80, 84, a0)
                else:
                    px[gx, gy] = (12, 9, 16, a0)
    return img


def boss_stage(master: Image.Image, stage: int) -> Image.Image:
    """Gradual per-stage recolor: 0 = whole, 5 = torn and on its last breath."""
    assert 0 <= stage <= 5
    img = master.convert("RGBA").copy()
    px = img.load()
    width, height = img.size
    saturation = 1.0 - stage * 0.16          # 1.00 .. 0.20
    value = 1.0 - stage * 0.055              # 1.00 .. 0.725
    warmth = stage * 0.035
    for y in range(height):
        for x in range(width):
            r, g, b, a = px[x, y]
            if a <= 8:
                continue
            grey = round(0.299 * r + 0.587 * g + 0.114 * b)
            r = round(grey + (r - grey) * saturation)
            g = round(grey + (g - grey) * saturation)
            b = round(grey + (b - grey) * saturation)
            r = round(min(255, r * value + warmth * 40))
            g = round(min(255, g * value))
            b = round(min(255, b * value))
            px[x, y] = (r, g, b, a)
    # Only the final stage is torn open; earlier stages just drain so the
    # decline stays gradual.
    if stage < 5:
        return img
    return tear(img, 1, 0xB055 + stage * 977)


# --------------------------------------------------------------------------
# Entry point.
# --------------------------------------------------------------------------

def paint_spire_atlas(atlas: Image.Image) -> Image.Image:
    """Paint the skull front, faulted ribs and crown trim in the rig's UV islands."""
    pixels = atlas.load()

    def stroke(points: tuple[tuple[int, int], ...], color: str, strength: float) -> None:
        pigment = rgb(color)
        for x, y in points:
            r, g, b, alpha = pixels[x, y]
            if alpha > 8:
                pixels[x, y] = (*mix((r, g, b), pigment, strength), alpha)

    stroke(((9, 9), (10, 9), (13, 9), (14, 9), (9, 10), (14, 10), (11, 11), (12, 11)),
           "#140A1C", .94)
    stroke(((10, 10), (13, 10)), "#FFDCA0", .98)
    stroke(((10, 12), (13, 12), (9, 13), (14, 13), (10, 14), (11, 14), (12, 14), (13, 14)),
           "#CE956F", .67)
    stroke(((12, 8), (11, 9), (12, 10), (11, 12), (12, 13)), "#A250CF", .77)
    for y in (6, 9, 12):
        stroke(((20, y), (21, y), (22, y + 1), (25, y + 1), (26, y), (27, y)),
               "#BF9A83", .60)
    stroke(((24, 5), (24, 6), (23, 7), (24, 8), (24, 9), (23, 10), (24, 11), (24, 12), (23, 13)),
           "#CA75EA", .83)
    stroke(((50, 5), (51, 6), (53, 6), (55, 5), (56, 6), (58, 7), (54, 8), (52, 8)),
           "#F6C174", .81)
    return atlas


def all_outputs() -> dict[str, Image.Image]:
    outputs: dict[str, Image.Image] = {}
    for spec in SPECS:
        atlas = paint(Image.open(SOURCES[spec.name]), spec)
        outputs[spec.name] = paint_spire_atlas(atlas) if spec.name == "end_rift_spire_harbinger" else atlas

    master = paint(Image.open(SOURCES["end_rift_user_boss"]), BOSS)
    for stage in range(6):
        outputs[f"end_rift_user_boss_stage{stage}"] = boss_stage(master, stage)
    return outputs


def encode(image: Image.Image) -> bytes:
    buffer = io.BytesIO()
    image.save(buffer, format="PNG", optimize=True)
    return buffer.getvalue()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true",
                        help="fail instead of writing when a committed sheet drifted")
    parser.add_argument("--preview", type=Path, default=None,
                        help="also write a 4x contact sheet to this path")
    args = parser.parse_args()

    manifest = {}
    drift = []
    for name, image in sorted(all_outputs().items()):
        data = encode(image)
        manifest[name] = hashlib.sha256(data).hexdigest()
        target = CANONICAL / f"{name}.png"
        if args.check:
            if not target.is_file() or target.read_bytes() != data:
                drift.append(name)
        else:
            target.write_bytes(data)

    if args.preview:
        _preview(args.preview)

    print(json.dumps(manifest, indent=2, sort_keys=True))
    if drift:
        print("drifted skins: " + ", ".join(drift))
        return 1
    return 0


def _preview(path: Path) -> None:
    from PIL import ImageDraw

    items = [spec.name for spec in SPECS] + \
            [f"end_rift_user_boss_stage{stage}" for stage in range(6)]
    cell = 360
    columns = 4
    rows = (len(items) + columns - 1) // columns
    sheet = Image.new("RGBA", (cell * columns, cell * rows), (22, 18, 34, 255))
    draw = ImageDraw.Draw(sheet)
    for index, name in enumerate(items):
        image = Image.open(CANONICAL / f"{name}.png").convert("RGBA")
        scale = max(1, (cell - 22) // max(image.size))
        image = image.resize((image.width * scale, image.height * scale), Image.NEAREST)
        x = (index % columns) * cell + 11
        y = (index // columns) * cell + 26
        sheet.alpha_composite(image, (x, y))
        draw.text((x, y - 15), name.replace("end_rift_", ""), fill=(230, 222, 255, 255))
    path.parent.mkdir(parents=True, exist_ok=True)
    sheet.convert("RGB").save(path)


if __name__ == "__main__":
    raise SystemExit(main())