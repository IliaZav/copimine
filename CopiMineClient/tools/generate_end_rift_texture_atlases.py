"""Create the hand-authored pixel atlases used by the local End Rift client mod.

These are native Minecraft UV sheets, not screenshots or concept thumbnails.  The
script intentionally keeps every sheet opaque at its declared atlas size so the
vanilla model UVs receive a readable surface on every face.  Palette, cracks and
sigils are kept different per entity so a wave mob cannot be mistaken for the
elite, guardian, spider, or shulker.
"""

from __future__ import annotations

import random
from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src" / "main" / "resources" / "assets" / "copimineclient" / "textures" / "entity"


def atlas(size: tuple[int, int], palette: list[tuple[int, int, int]], seed: int) -> Image.Image:
    width, height = size
    rng = random.Random(seed)
    image = Image.new("RGBA", size, (*palette[0], 255))
    pixels = image.load()
    for y in range(height):
        for x in range(width):
            # Broad pixel clusters read as cloth/scale texture in-game while
            # keeping the sheet intentionally pixel-art rather than smooth.
            cluster = ((x // 2) * 31 + (y // 2) * 17 + seed) % len(palette)
            jitter = rng.randrange(0, 3)
            base = palette[(cluster + jitter) % len(palette)]
            if (x + y + seed) % 11 == 0:
                base = palette[-1]
            variation = rng.randrange(-5, 6)
            shaded = tuple(max(0, min(255, channel + variation)) for channel in base)
            pixels[x, y] = (*shaded, 255)
    return image


def panel_lines(draw: ImageDraw.ImageDraw, width: int, height: int, color: tuple[int, int, int]) -> None:
    # UV sheet guides are part of the authored texture and give each mapped
    # cube face a deliberate edge instead of an unbroken flat tint.
    for x in range(0, width, max(8, width // 8)):
        draw.line((x, 0, x, height - 1), fill=(*color, 190), width=1)
    for y in range(0, height, max(8, height // 4)):
        draw.line((0, y, width - 1, y), fill=(*color, 190), width=1)


def sigil(draw: ImageDraw.ImageDraw, origin: tuple[int, int], radius: int,
          colors: list[tuple[int, int, int]], phase: int) -> None:
    ox, oy = origin
    outer, inner, spark = colors
    points = []
    for index in range(8):
        x = ox + ((index * radius + phase) % (radius * 2 + 1)) - radius
        y = oy + (((index * 3 + phase) * radius) % (radius * 2 + 1)) - radius
        points.append((x, y))
    draw.line((ox - radius, oy, ox + radius, oy), fill=(*outer, 255), width=1)
    draw.line((ox, oy - radius, ox, oy + radius), fill=(*outer, 255), width=1)
    draw.rectangle((ox - radius // 2, oy - radius // 2, ox + radius // 2, oy + radius // 2), outline=(*inner, 255), width=1)
    draw.line(points, fill=(*spark, 255), width=1, joint="curve")
    draw.point((ox, oy), fill=(*spark, 255))


def enderman_sheet(name: str, palette: list[tuple[int, int, int]], seed: int,
                   accent: tuple[int, int, int], eye: tuple[int, int, int]) -> None:
    image = atlas((64, 32), palette, seed)
    draw = ImageDraw.Draw(image)
    panel_lines(draw, 64, 32, palette[1])
    # The broad areas follow the normal 64x32 humanoid UV footprint.  The
    # marks intentionally cross several faces so they remain visible after
    # the vanilla model wraps the sheet.
    for offset in (0, 16, 32, 48):
        draw.line((offset + 2, 3, min(offset + 13, 63), 13), fill=(*accent, 255), width=1)
        draw.line((offset + 4, 14, min(offset + 14, 63), 2), fill=(*palette[-1], 255), width=1)
    sigil(draw, (12, 7), 5, [accent, palette[-1], eye], seed % 7)
    sigil(draw, (27, 23), 6, [palette[-1], accent, eye], (seed + 3) % 9)
    # Enderman eyes are kept in the head band, bright enough to survive the
    # game renderer's mip level but small enough to remain pixel art.
    draw.rectangle((8, 5, 11, 6), fill=(*eye, 255))
    draw.rectangle((17, 5, 20, 6), fill=(*eye, 255))
    for x in range(2, 62, 7):
        draw.point((x, (x * 5 + seed) % 31), fill=(*accent, 255))
    image.save(OUT / name, format="PNG", optimize=False)


def spider_sheet() -> None:
    palette = [(36, 8, 37), (67, 12, 54), (103, 19, 69), (17, 39, 72), (31, 76, 105), (184, 44, 106)]
    image = atlas((64, 32), palette, 71)
    draw = ImageDraw.Draw(image)
    panel_lines(draw, 64, 32, (31, 76, 105))
    # Eight legs are represented as angular cyan veins over a crimson shell.
    for x in (4, 12, 20, 28, 36, 44, 52, 60):
        draw.line((x, 18, max(0, x - 6), 30), fill=(31, 76, 105, 255), width=2)
        draw.line((x, 19, min(63, x + 7), 28), fill=(184, 44, 106, 255), width=1)
    draw.rectangle((25, 9, 38, 21), outline=(184, 44, 106, 255), width=2)
    draw.rectangle((29, 12, 34, 17), fill=(17, 39, 72, 255), outline=(96, 224, 255, 255), width=1)
    for eye_x in (27, 34):
        draw.rectangle((eye_x, 10, eye_x + 1, 11), fill=(255, 91, 211, 255))
    image.save(OUT / "end_rift_spider.png", format="PNG", optimize=False)


def shulker_sheet() -> None:
    palette = [(24, 9, 54), (46, 15, 84), (79, 21, 123), (20, 80, 118), (30, 148, 173), (200, 71, 194)]
    image = atlas((64, 64), palette, 113)
    draw = ImageDraw.Draw(image)
    panel_lines(draw, 64, 64, (30, 148, 173))
    # Shell plates, seams, and a luminous core cover all shulker UV faces.
    for y in range(4, 64, 8):
        draw.line((3, y, 60, y + 3), fill=(200, 71, 194, 255), width=1)
        draw.line((4, y + 2, 60, y + 5), fill=(20, 80, 118, 255), width=1)
    draw.rectangle((17, 17, 46, 46), outline=(200, 71, 194, 255), width=2)
    draw.rectangle((24, 24, 39, 39), outline=(30, 148, 173, 255), width=2)
    draw.rectangle((29, 29, 34, 34), fill=(200, 224, 255, 255))
    for x, y in ((8, 8), (55, 10), (10, 54), (54, 55)):
        sigil(draw, (x, y), 4, [(200, 71, 194), (30, 148, 173), (200, 224, 255)], x + y)
    image.save(OUT / "end_rift_shulker.png", format="PNG", optimize=False)


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    enderman_sheet(
        "end_rift_enderman.png",
        [(10, 14, 44), (18, 22, 72), (32, 35, 105), (72, 34, 142), (145, 44, 191), (30, 176, 214)],
        17,
        (145, 44, 191),
        (255, 117, 231),
    )
    enderman_sheet(
        "end_rift_elite.png",
        [(7, 35, 51), (11, 69, 82), (16, 112, 121), (21, 164, 153), (205, 154, 55), (235, 225, 131)],
        29,
        (205, 154, 55),
        (120, 255, 236),
    )
    enderman_sheet(
        "end_rift_guardian.png",
        [(39, 8, 21), (76, 13, 27), (124, 20, 34), (181, 40, 48), (236, 105, 54), (248, 207, 83)],
        43,
        (248, 207, 83),
        (255, 240, 137),
    )
    spider_sheet()
    shulker_sheet()


if __name__ == "__main__":
    main()
