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


def rift_guardian_phase_sheet(name: str, palette: list[tuple[int, int, int]], seed: int,
                              accent: tuple[int, int, int], core: tuple[int, int, int]) -> None:
    # Keep the 128x128 UV sheet fully opaque, but make the surface itself
    # readable at Minecraft scale.  The previous version filled the atlas
    # with crossing guide lines; on the large torso cuboid that became a red
    # checkerboard instead of armour.  This version uses restrained panels,
    # mirrored cracks, and a few high-contrast emissive areas.
    image = Image.new("RGBA", (128, 128), (*palette[0], 255))
    draw = ImageDraw.Draw(image)

    def rgba(color: tuple[int, int, int], alpha: int = 255) -> tuple[int, int, int, int]:
        return (*color, alpha)

    def panel(box: tuple[int, int, int, int], fill: tuple[int, int, int], edge: tuple[int, int, int], width: int = 1) -> None:
        draw.rectangle(box, fill=rgba(fill), outline=rgba(edge), width=width)

    # A very low-frequency 4px material grain keeps the armour from looking
    # flat without introducing noisy diagonals over every mapped face.
    for y in range(0, 128, 4):
        for x in range(0, 128, 4):
            if ((x // 4) * 3 + (y // 4) * 5 + seed) % 5 == 0:
                draw.rectangle((x, y, x + 3, y + 3), fill=rgba(palette[1], 150))

    # Deliberate UV islands used by the custom torso, shoulders, arms, shards,
    # legs, and head.  Alternating dark plates provide silhouette highlights
    # while leaving the bright lines sparse enough to read in motion.
    uv_panels = (
        ((0, 0, 55, 47), palette[1], palette[2]),
        ((56, 4, 91, 31), palette[2], accent),
        ((88, 4, 109, 61), palette[1], palette[-1]),
        ((4, 64, 37, 96), palette[2], accent),
        ((42, 64, 58, 91), palette[1], palette[-1]),
        ((0, 84, 27, 111), palette[1], accent),
        ((28, 84, 43, 111), palette[2], core),
        ((0, 96, 15, 111), palette[2], accent),
    )
    for index, (box, fill, edge) in enumerate(uv_panels):
        panel(box, fill, edge, 1 if index < 6 else 2)

    # The primary 18x21 torso face: a mirrored collar and central crack make
    # the boss read as a guardian rather than a recoloured Enderman.
    torso_dark = palette[0]
    draw.rectangle((1, 1, 17, 20), fill=rgba(torso_dark), outline=rgba(accent))
    draw.line((2, 3, 5, 7, 3, 11, 6, 15, 5, 19), fill=rgba(accent), width=1, joint="curve")
    draw.line((15, 3, 12, 7, 14, 11, 11, 15, 12, 19), fill=rgba(accent), width=1, joint="curve")
    draw.line((8, 2, 8, 6, 9, 9, 8, 13, 8, 19), fill=rgba(core), width=1)
    draw.line((9, 2, 9, 6, 8, 9), fill=rgba(palette[-1]), width=1)
    draw.rectangle((6, 7, 10, 11), outline=rgba(core), width=1)
    draw.point((8, 9), fill=rgba(palette[-1]))

    # Symmetric armour seams on the larger UV islands.
    for left, right, top, bottom in ((57, 90, 6, 29), (5, 36, 66, 94), (1, 26, 86, 109)):
        mid = (left + right) // 2
        draw.line((mid, top + 2, mid - 3, top + 8, mid, top + 14, mid - 2, bottom - 2),
                  fill=rgba(accent), width=1, joint="curve")
        draw.line((mid, top + 2, mid + 3, top + 8, mid, top + 14, mid + 2, bottom - 2),
                  fill=rgba(palette[-1]), width=1, joint="curve")
        draw.line((left + 2, top + 4, left + 1, bottom - 3), fill=rgba(palette[2], 220), width=1)
        draw.line((right - 2, top + 4, right - 1, bottom - 3), fill=rgba(palette[2], 220), width=1)

    # Chest-rift UV (28..34, 84..94) is intentionally bright: this is the
    # emissive focal point visible on the front of the in-game model.
    draw.rectangle((29, 84, 34, 94), fill=rgba(core), outline=rgba(palette[-1]), width=1)
    draw.line((31, 85, 30, 88, 32, 91, 31, 94), fill=rgba(palette[0]), width=1)
    draw.point((32, 87), fill=rgba(palette[-1]))

    # Head island (0..13, 96..109): two eyes and a compact forehead mark.
    draw.rectangle((1, 97, 13, 109), fill=rgba(palette[0]), outline=rgba(accent))
    draw.rectangle((3, 101, 5, 102), fill=rgba(core))
    draw.rectangle((9, 101, 11, 102), fill=rgba(core))
    draw.line((7, 98, 6, 101, 7, 104, 7, 108), fill=rgba(accent), width=1)

    # A central shard sigil is kept sparse so it remains a crisp accent when
    # the texture is sampled at distance.
    draw.polygon([(64, 50), (72, 64), (64, 78), (56, 64)], fill=rgba(palette[0]), outline=rgba(core))
    draw.line((64, 53, 64, 75), fill=rgba(accent), width=1)
    draw.line((59, 64, 69, 64), fill=rgba(palette[-1]), width=1)
    draw.point((64, 64), fill=rgba(core))
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


def skeleton_sheet(name: str, palette: list[tuple[int, int, int]], seed: int,
                   accent: tuple[int, int, int], eye: tuple[int, int, int]) -> None:
    """Paint a readable 64x32 skeleton UV sheet with bone plates and sigils."""
    image = atlas((64, 32), palette, seed)
    draw = ImageDraw.Draw(image)
    panel_lines(draw, 64, 32, palette[1])
    # Rib and joint bands keep the vanilla skeleton silhouette readable while
    # the angular rift marks make the two server-bound variants distinct.
    for y in (3, 8, 13, 18, 23, 28):
        draw.line((2, y, 19, y + 1), fill=(*accent, 255), width=1)
        draw.line((44, y + 1, 61, y), fill=(*accent, 255), width=1)
    draw.rectangle((24, 3, 39, 15), outline=(*accent, 255), width=2)
    draw.rectangle((27, 6, 30, 9), fill=(*eye, 255))
    draw.rectangle((33, 6, 36, 9), fill=(*eye, 255))
    draw.line((30, 12, 33, 12), fill=(*palette[-1], 255), width=1)
    sigil(draw, (12, 23), 4, [accent, palette[-1], eye], seed % 7)
    sigil(draw, (51, 22), 4, [palette[-1], accent, eye], (seed + 3) % 9)
    for x in range(3, 62, 9):
        draw.point((x, (x * 7 + seed) % 31), fill=(*accent, 255))
    image.save(OUT / name, format="PNG", optimize=False)


def bossbar_frame() -> None:
    """Paint a transparent 256x32 HUD frame around the health fill."""
    gui_out = OUT.parent / "gui"
    gui_out.mkdir(parents=True, exist_ok=True)
    image = Image.new("RGBA", (256, 32), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    shadow = (5, 6, 14, 245)
    steel = (105, 112, 132, 255)
    steel_light = (214, 220, 224, 255)
    steel_dark = (38, 42, 61, 255)
    bone = (211, 193, 162, 255)
    bone_light = (255, 237, 190, 255)
    violet = (130, 72, 210, 255)
    ember = (238, 116, 53, 255)

    # Angular upper and lower rails leave the centre transparent so the Java
    # renderer can draw a phase-coloured health fill underneath this frame.
    draw.line((18, 5, 238, 5), fill=shadow, width=2)
    draw.line((15, 7, 241, 7), fill=steel_dark, width=2)
    draw.line((18, 8, 238, 8), fill=steel_light, width=1)
    draw.line((18, 23, 238, 23), fill=steel_dark, width=2)
    draw.line((15, 25, 241, 25), fill=shadow, width=2)
    draw.line((19, 22, 237, 22), fill=steel, width=1)

    left_end = [(0, 15), (8, 7), (19, 7), (27, 11), (21, 15),
                (27, 19), (19, 25), (8, 25)]
    right_end = [(255 - x, y) for x, y in left_end]
    draw.polygon(left_end, fill=shadow)
    draw.polygon(right_end, fill=shadow)
    draw.line((1, 15, 9, 8, 20, 8), fill=bone_light, width=2, joint="curve")
    draw.line((1, 15, 9, 23, 20, 23), fill=bone, width=2, joint="curve")
    draw.line((254, 15, 246, 8, 235, 8), fill=bone_light, width=2, joint="curve")
    draw.line((254, 15, 246, 23, 235, 23), fill=bone, width=2, joint="curve")

    # Mirrored metal ribs make the silhouette read as an ornate frame rather
    # than a flat paper strip at Minecraft's normal HUD scale.
    for start in (23, 33, 43, 53):
        draw.polygon([(start, 9), (start + 4, 9), (start + 17, 21),
                      (start + 13, 21)], fill=steel_dark)
        draw.line((start + 1, 10, start + 14, 20), fill=steel_light, width=1)
        mirror = 255 - start
        draw.polygon([(mirror, 9), (mirror - 4, 9), (mirror - 17, 21),
                      (mirror - 13, 21)], fill=steel_dark)
        draw.line((mirror - 1, 10, mirror - 14, 20), fill=steel_light, width=1)

    # Bone hooks and small bolts break up the rails without obscuring text.
    for x in (29, 48, 67, 188, 207, 226):
        draw.rectangle((x, 5, x + 3, 8), fill=bone)
        draw.point((x + 1, 6), fill=bone_light)
    for x in (12, 244):
        draw.rectangle((x, 14, x + 2, 17), fill=bone_light)
        draw.point((x + 1, 15), fill=ember)

    # A restrained central rift sigil anchors the title and is deliberately
    # symmetric around the exact midpoint of the 256px sheet.
    draw.polygon([(128, 7), (135, 14), (128, 23), (121, 14)], fill=shadow)
    draw.line((128, 8, 134, 14, 128, 21, 122, 14, 128, 8), fill=violet, width=1, joint="curve")
    draw.line((128, 10, 128, 19), fill=ember, width=1)
    draw.point((128, 14), fill=bone_light)

    image.save(gui_out / "end_rift_bossbar_frame.png", format="PNG", optimize=False)


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
    rift_guardian_phase_sheet(
        "rift_guardian_awakening.png",
        [(20, 18, 50), (44, 30, 83), (83, 43, 119), (132, 56, 151), (207, 93, 167), (255, 188, 115)],
        151,
        (255, 188, 115),
        (255, 232, 179),
    )
    rift_guardian_phase_sheet(
        "rift_guardian_hunter.png",
        [(10, 35, 37), (16, 70, 67), (21, 108, 93), (40, 146, 111), (173, 151, 62), (255, 222, 111)],
        163,
        (173, 151, 62),
        (159, 255, 226),
    )
    rift_guardian_phase_sheet(
        "rift_guardian_distortion.png",
        [(24, 14, 59), (48, 24, 102), (77, 33, 146), (38, 95, 153), (54, 178, 188), (224, 81, 216)],
        179,
        (54, 178, 188),
        (248, 173, 255),
    )
    rift_guardian_phase_sheet(
        "rift_guardian_absorption.png",
        [(8, 26, 56), (16, 55, 91), (23, 92, 132), (37, 129, 165), (90, 208, 188), (197, 255, 214)],
        191,
        (90, 208, 188),
        (223, 255, 237),
    )
    rift_guardian_phase_sheet(
        "rift_guardian_catastrophe.png",
        [(52, 9, 25), (93, 15, 34), (143, 25, 39), (192, 45, 45), (242, 89, 54), (255, 226, 107)],
        211,
        (242, 89, 54),
        (255, 245, 157),
    )
    spider_sheet()
    shulker_sheet()
    skeleton_sheet(
        "end_rift_skeleton.png",
        [(218, 213, 191), (164, 162, 151), (101, 110, 112), (47, 67, 82), (28, 128, 151), (89, 226, 211)],
        227,
        (28, 128, 151),
        (198, 255, 241),
    )
    skeleton_sheet(
        "end_rift_elite_skeleton.png",
        [(240, 226, 194), (186, 151, 90), (111, 68, 72), (52, 19, 49), (208, 46, 134), (255, 117, 231)],
        239,
        (208, 46, 134),
        (255, 220, 246),
    )
    bossbar_frame()


if __name__ == "__main__":
    main()
