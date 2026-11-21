"""Author the 2026-11-20 End Rift HUD redesign.

This replaces the provisional bars with one coherent, tactile pixel-art family:

* C1 - the Wave 6 Rift Ram: a forged rail with a real health track and seven
  chained armour plates.  The plate atlas carries intact / cracked / shattered
  / empty cells so the client can animate one accepted collision at a time.
* C2 - the Guardian: six phase overlays (metal, obsidian, veins, cracks, seal)
  and a matching portrait-aura atlas.  The large authored mouth frame and the
  real rendered head are kept; the overlays add phase identity without
  repainting the portrait's identity.
* C3 - four Wave 7 miniboss bars redrawn from scratch: sculk Warden, bone
  marksman, runic Archmage and fractured Echo, each with a matching portrait.
* C4 - a single 16x16 state-glyph strip so every phase/plate state is legible
  by silhouette as well as by hue.

Two directions are produced for every major bar.  Direction ``a`` ships in the
client jar; both directions are archived as editable previews under
``docs/end-rift/2026-11-21/art-directions``.

The generator is deterministic: the same Pillow input produces byte-identical
PNGs, so the client jar pin stays reproducible.  It deliberately keeps the
track recess fully transparent so the renderer's real health fill is always
visible, which is covered by a golden pixel test.
"""

from __future__ import annotations

import argparse
import hashlib
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OUT = (
    ROOT
    / "CopiMineClient"
    / "src"
    / "main"
    / "resources"
    / "assets"
    / "copimineclient"
    / "textures"
    / "gui"
)
ARCHIVE = ROOT / "docs" / "end-rift" / "2026-11-21" / "art-directions"

# Renderer coordinate spaces.  These are duplicated in the HUD constants and
# pinned by the golden test, so art and sampling can never silently drift.
RAM_SIZE = (300, 56)
RAM_TRACK = (18, 40, 282, 52)
RAM_PLATE = 32
RAM_PLATE_X = 16
RAM_PLATE_STEP = 40
RAM_PLATE_Y = 4

GUARDIAN_FRAME = (360, 140)
GUARDIAN_INNER = (30, 92, 330, 101)
GUARDIAN_ATLAS = (360, 840)
GUARDIAN_AURA = (384, 64)
GUARDIAN_GLYPHS = (96, 16)

WAVE7_FRAME = (256, 32)
WAVE7_INNER = (14, 12, 242, 20)
WAVE7_PORTRAIT = (128, 128)


def rgba(value: int) -> tuple[int, int, int, int]:
    return (
        (value >> 16) & 0xFF,
        (value >> 8) & 0xFF,
        value & 0xFF,
        (value >> 24) & 0xFF,
    )


def blend(base: tuple[int, int, int, int], over: tuple[int, int, int, int]) -> tuple[int, int, int, int]:
    """Straight-alpha source-over for a single pixel."""
    alpha = over[3] / 255.0
    return (
        int(over[0] * alpha + base[0] * (1.0 - alpha)),
        int(over[1] * alpha + base[1] * (1.0 - alpha)),
        int(over[2] * alpha + base[2] * (1.0 - alpha)),
        max(base[3], over[3]),
    )


RAM_A = {
    "edge": 0xFF0E0D12,
    "rail": 0xFF2A2733,
    "steel": 0xFF4A4656,
    "light": 0xFF8E8799,
    "bone": 0xFFD8CDB4,
    "ember": 0xFFA65CFF,
    "hot": 0xFFE7C1FF,
    "danger": 0xFFD9474F,
    "chain": 0xFF6B6475,
    "chain_dark": 0xFF201D27,
    "socket": 0xFF16131C,
}
RAM_B = {
    "edge": 0xFF231E16,
    "rail": 0xFF6B5B44,
    "steel": 0xFF9A8766,
    "light": 0xFFE8DFC9,
    "bone": 0xFFFFF4DA,
    "ember": 0xFFFF8A3D,
    "hot": 0xFFFFD9A6,
    "danger": 0xFFC23B2E,
    "chain": 0xFFB7A488,
    "chain_dark": 0xFF3A2F22,
    "socket": 0xFF2B2318,
}

PHASE_ACCENTS = (
    (0x9A, 0x62, 0xFF),  # 0 Awakening - cocoon violet
    (0x42, 0xC9, 0xFF),  # 1 Hunt - cold cyan
    (0xF3, 0x4C, 0xDB),  # 2 Rift - magenta
    (0xFF, 0xC8, 0x57),  # 3 Overload - amber
    (0xFF, 0x4F, 0x61),  # 4 Rage - red
    (0xF4, 0xF4, 0xFF),  # 5 Last Seal - white
)

WAVE7_STYLES = {
    "bone": {
        "edge": 0xFF2A2233,
        "border": 0xFFE9E2CE,
        "body": 0xFF10101A,
        "accent": 0xFF9A62FF,
        "glow": 0xFF5A2E8C,
        "highlight": 0xFFF6F1E2,
    },
    "sculk": {
        "edge": 0xFF0B2226,
        "border": 0xFF37E0D0,
        "body": 0xFF071417,
        "accent": 0xFF0C6B7A,
        "glow": 0xFF25E0C8,
        "highlight": 0xFF9BF4EA,
    },
    "rune": {
        "edge": 0xFF2A0C33,
        "border": 0xFFF34CDB,
        "body": 0xFF130616,
        "accent": 0xFF7A2BD6,
        "glow": 0xFFFF7AE8,
        "highlight": 0xFFFFD9F8,
    },
    "mirror": {
        "edge": 0xFF15222B,
        "border": 0xFFBFE9FF,
        "body": 0xFF0A1218,
        "accent": 0xFF42C9FF,
        "glow": 0xFF7FE3FF,
        "highlight": 0xFFE8F9FF,
    },
}

# Direction B is intentionally a different material story, not a hue swap:
# pale bone-and-iron restraint versus the shipped dark obsidian-and-steel.
WAVE7_B_PALETTES = {
    "bone": {"edge": 0xFF3A3226, "border": 0xFFFFF3D6, "body": 0xFF1C1710,
             "accent": 0xFFB07B32, "glow": 0xFF7A4A1E, "highlight": 0xFFFFFDF2},
    "sculk": {"edge": 0xFF123033, "border": 0xFF9BF4EA, "body": 0xFF061A1D,
              "accent": 0xFF1F8F86, "glow": 0xFF74FFF1, "highlight": 0xFFD8FFFA},
    "rune": {"edge": 0xFF331B12, "border": 0xFFFFC46B, "body": 0xFF190D08,
             "accent": 0xFFB24B1E, "glow": 0xFFFFD37A, "highlight": 0xFFFFF0D4},
    "mirror": {"edge": 0xFF232733, "border": 0xFFDCE6F5, "body": 0xFF10131B,
               "accent": 0xFF6E8AB8, "glow": 0xFFAEC8F0, "highlight": 0xFFF4F9FF},
}


def _bevel(draw: ImageDraw.ImageDraw, box, fill: int, light: int, dark: int, width: int = 1) -> None:
    x0, y0, x1, y1 = box
    draw.rectangle(box, fill=rgba(fill))
    draw.line([(x0, y0), (x1, y0)], fill=rgba(light))
    draw.line([(x0, y0), (x0, y1)], fill=rgba(light))
    draw.line([(x0, y1), (x1, y1)], fill=rgba(dark))
    draw.line([(x1, y0), (x1, y1)], fill=rgba(dark))
    if width > 1:
        draw.rectangle([x0 + 1, y0 + 1, x1 - 1, y1 - 1], outline=rgba(dark))


def _chain_run(draw: ImageDraw.ImageDraw, x0: int, x1: int, y: int, link: int, shade: int) -> None:
    step = 6
    for index, x in enumerate(range(x0, x1 - 4, step)):
        if index % 2 == 0:
            draw.rectangle([x, y, x + 5, y + 3], fill=rgba(link), outline=rgba(shade))
        else:
            draw.rectangle([x + 1, y + 1, x + 4, y + 4], fill=rgba(shade))
            draw.point((x + 2, y + 2), fill=rgba(link))
            draw.point((x + 3, y + 2), fill=rgba(link))


def _rivet(draw: ImageDraw.ImageDraw, x: int, y: int, light: int, dark: int) -> None:
    draw.point((x, y), fill=rgba(dark))
    draw.point((x + 1, y), fill=rgba(light))
    draw.point((x, y + 1), fill=rgba(light))
    draw.point((x + 1, y + 1), fill=rgba(dark))


def ram_rail(palette: dict) -> Image.Image:
    """The forged rail: chain guide, seven empty plate sockets, health recess."""
    width, height = RAM_SIZE
    image = Image.new("RGBA", RAM_SIZE, (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)

    # Heavy body, deliberately asymmetric: thicker left yoke, tapered right.
    draw.rectangle([0, 6, width - 1, height - 4], fill=rgba(palette["edge"]))
    draw.rectangle([2, 8, width - 3, height - 6], fill=rgba(palette["rail"]))
    draw.line([(2, 8), (width - 3, 8)], fill=rgba(palette["light"]))
    draw.line([(2, 8), (2, height - 6)], fill=rgba(palette["steel"]))
    draw.line([(3, height - 7), (width - 4, height - 7)], fill=rgba(palette["edge"]))
    draw.rectangle([0, 10, 5, height - 8], fill=rgba(palette["steel"]))
    draw.rectangle([width - 6, 12, width - 1, height - 10], fill=rgba(palette["steel"]))

    # Chain guide across the top, then the seven recessed sockets.
    _chain_run(draw, 8, width - 8, 1, palette["chain"], palette["chain_dark"])
    for index in range(7):
        x = RAM_PLATE_X + index * RAM_PLATE_STEP
        draw.rectangle([x - 2, RAM_PLATE_Y - 2, x + RAM_PLATE + 1, RAM_PLATE_Y + 27],
                       fill=rgba(palette["edge"]))
        draw.rectangle([x, RAM_PLATE_Y, x + RAM_PLATE - 1, RAM_PLATE_Y + 25],
                       fill=rgba(palette["socket"]))
        draw.line([(x, RAM_PLATE_Y), (x, RAM_PLATE_Y + 25)], fill=rgba(palette["steel"]))
        draw.line([(x, RAM_PLATE_Y), (x + RAM_PLATE - 1, RAM_PLATE_Y)], fill=rgba(palette["steel"]))
        draw.line([(x + RAM_PLATE - 1, RAM_PLATE_Y + 1), (x + RAM_PLATE - 1, RAM_PLATE_Y + 25)],
                  fill=rgba(palette["chain_dark"]))
        _rivet(draw, x + 3, RAM_PLATE_Y + 22, palette["light"], palette["chain_dark"])

    # Health recess: the fill is drawn by the renderer, so this stays empty.
    left, top, right, bottom = RAM_TRACK
    draw.rectangle([left - 3, top - 3, right + 3, bottom + 3], fill=rgba(palette["edge"]))
    draw.rectangle([left - 2, top - 2, right + 2, bottom + 2], outline=rgba(palette["steel"]))
    draw.line([(left - 2, top - 2), (right + 2, top - 2)], fill=rgba(palette["light"]))

    # Bone inlay at the yoke, restrained ember cracks along the underside.
    draw.rectangle([6, 14, 10, 24], fill=rgba(palette["bone"]))
    draw.rectangle([7, 16, 9, 22], fill=rgba(palette["ember"]))
    for x in (28, 74, 128, 188, 246):
        draw.line([(x, height - 6), (x + 4, height - 2)], fill=rgba(palette["ember"]))
        draw.point((x + 4, height - 2), fill=rgba(palette["hot"]))
    for x in range(24, width - 20, 44):
        _rivet(draw, x, height - 10, palette["light"], palette["chain_dark"])
    # Clear the recess last: rivets and underside cracks above may reach into
    # the track band, and the renderer paints the real HP fill there.
    draw.rectangle([left, top, right, bottom], fill=(0, 0, 0, 0))
    return image


def ram_plate(index: int, variant: int, palette: dict, alternate: bool = False) -> Image.Image:
    """One 32x32 plate cell.

    variant 0 intact, 1 impact flash/crack, 2 shattered shards, 3 empty socket
    with a snapped chain.  The index shifts the ember pattern so seven plates
    do not read as seven copies.
    """
    size = RAM_PLATE
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    rng = random.Random(0x5EA1 + index * 37 + variant * 11)

    if variant == 3:
        # Empty socket: dark throat, snapped chain hanging past the rail.
        draw.rectangle([3, 2, 28, 27], fill=rgba(palette["edge"]))
        draw.rectangle([5, 4, 26, 24], fill=rgba(palette["socket"]))
        draw.rectangle([13, 24, 18, 31], fill=rgba(palette["chain_dark"]))
        _chain_run(draw, 9, 22, 20, palette["chain"], palette["chain_dark"])
        draw.line([(9, 24), (7, 31)], fill=rgba(palette["chain"]))
        draw.point((7, 31), fill=rgba(palette["chain_dark"]))
        return image

    # Base forged plate.
    draw.rectangle([2, 2, 29, 27], fill=rgba(palette["edge"]))
    draw.rectangle([4, 4, 27, 25], fill=rgba(palette["steel"]))
    draw.rectangle([5, 5, 26, 7], fill=rgba(palette["light"]))
    draw.rectangle([5, 22, 26, 24], fill=rgba(palette["rail"]))
    for x, y in ((6, 8), (23, 8), (6, 20), (23, 20)):
        _rivet(draw, x, y, palette["light"], palette["chain_dark"])

    # The restraint motif: a bone brace over an ember-core lock.  Alternating
    # plates carry the brace vertically or diagonally.
    if alternate or index % 2:
        draw.rectangle([14, 7, 18, 22], fill=rgba(palette["bone"]))
        draw.rectangle([15, 9, 17, 20], fill=rgba(palette["ember"]))
    else:
        draw.rectangle([7, 12, 25, 16], fill=rgba(palette["bone"]))
        draw.rectangle([9, 13, 23, 15], fill=rgba(palette["ember"]))
    draw.rectangle([12, 11, 20, 17], fill=rgba(palette["edge"]))
    draw.rectangle([13, 12, 19, 16], fill=rgba(palette["ember"]))
    draw.rectangle([15, 13, 17, 15], fill=rgba(palette["hot"]))
    # Per-plate marks keep the seven intact plates from reading as one copied
    # sprite while preserving the family silhouette.
    if index % 3 == 0:
        draw.point((7, 9), fill=rgba(palette["hot"]))
        draw.point((24, 18), fill=rgba(palette["hot"]))
    elif index % 3 == 1:
        draw.line([(8, 20), (11, 23)], fill=rgba(palette["ember"]))
        draw.point((11, 23), fill=rgba(palette["hot"]))
    else:
        draw.rectangle([6, 7, 8, 9], fill=rgba(palette["ember"]))
        draw.point((7, 8), fill=rgba(palette["hot"]))

    if variant == 1:
        # Impact flash plus fresh cracks.  A bright rim is what makes the
        # 70-120 ms hit read even when the plate is small.
        draw.rectangle([3, 3, 28, 26], outline=rgba(palette["hot"]))
        for _ in range(7):
            x = rng.randint(6, 22)
            y = rng.randint(6, 20)
            draw.line([(x, y), (x + rng.randint(2, 5), y + rng.randint(2, 6))],
                      fill=rgba(palette["hot"]))
        draw.point((16, 14), fill=rgba(palette["danger"]))
    elif variant == 2:
        # Shattered: carve the lower-right away, leave jagged steel and shards.
        draw.polygon([(17, 4), (27, 4), (27, 13), (22, 17), (20, 10)], fill=(0, 0, 0, 0))
        draw.polygon([(5, 18), (13, 18), (16, 25), (6, 25)], fill=(0, 0, 0, 0))
        for cx, cy, dx, dy in ((22, 20, 3, 2), (18, 26, 4, 3), (27, 18, 2, 4)):
            draw.polygon([(cx, cy), (cx + dx, cy + dy), (cx + 1, cy + dy + 1)],
                         fill=rgba(palette["steel"]))
            draw.point((cx, cy), fill=rgba(palette["hot"]))
        draw.line([(4, 5), (13, 14), (10, 22)], fill=rgba(palette["ember"]))
        draw.line([(5, 3), (15, 10)], fill=rgba(palette["danger"]))
    return image


def ram_plate_atlas(palette: dict, direction: str) -> Image.Image:
    atlas = Image.new("RGBA", (RAM_PLATE * 8, RAM_PLATE * 4), (0, 0, 0, 0))
    alternate = direction == "b"
    for index in range(7):
        for variant in range(4):
            atlas.paste(ram_plate(index, variant, palette, alternate),
                        (index * RAM_PLATE, variant * RAM_PLATE))
    # Column 7 is the Ram emblem used beside the title.
    emblem = Image.new("RGBA", (RAM_PLATE, RAM_PLATE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(emblem)
    draw.polygon([(6, 8), (12, 4), (20, 4), (26, 8), (22, 24), (16, 28), (10, 24)],
                 fill=rgba(palette["steel"]))
    draw.polygon([(9, 10), (14, 7), (18, 7), (23, 10), (20, 22), (16, 25), (12, 22)],
                 fill=rgba(palette["rail"]))
    draw.rectangle([11, 12, 14, 15], fill=rgba(palette["ember"]))
    draw.rectangle([18, 12, 21, 15], fill=rgba(palette["ember"]))
    draw.rectangle([11, 12, 12, 13], fill=rgba(palette["hot"]))
    draw.rectangle([18, 12, 19, 13], fill=rgba(palette["hot"]))
    draw.line([(16, 16), (16, 22)], fill=rgba(palette["bone"]))
    draw.line([(9, 6), (6, 2)], fill=rgba(palette["bone"]))
    draw.line([(23, 6), (26, 2)], fill=rgba(palette["bone"]))
    atlas.paste(emblem, (7 * RAM_PLATE, 0))
    return atlas


def guardian_overlay(phase: int, direction: str) -> Image.Image:
    """One 360x140 phase overlay.  The inner health recess stays transparent."""
    width, height = GUARDIAN_FRAME
    image = Image.new("RGBA", GUARDIAN_FRAME, (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    accent = PHASE_ACCENTS[phase]
    light = (min(255, accent[0] + 70), min(255, accent[1] + 70), min(255, accent[2] + 70))
    dark = (accent[0] // 3, accent[1] // 3, accent[2] // 3)
    bone = (216, 205, 180) if direction == "a" else (255, 244, 218)
    rng = random.Random(0x600D + phase * 97 + (1 if direction == "a" else 2))

    # Symmetric corner brackets outside the title and the health recess.
    for x, y, dx in ((6, 6, 1), (width - 7, 6, -1), (6, height - 8, 1), (width - 7, height - 8, -1)):
        draw.line([(x, y), (x + 24 * dx, y)], fill=(*accent, 210))
        draw.line([(x, y), (x, y - 14 if y > 20 else y + 14)], fill=(*accent, 210))
        draw.rectangle([x + (3 if dx > 0 else -7), y + (2 if y < 20 else -7),
                        x + (7 if dx > 0 else -3), y + (6 if y < 20 else -3)],
                       fill=(*dark, 230))

    # Side rails: obsidian bands in direction A, pale bone straps in B.
    for side in (0, 1):
        x0 = 2 if side == 0 else width - 18
        draw.rectangle([x0, 22, x0 + 15, height - 22],
                       fill=(*dark, 150) if direction == "a" else (*bone, 120))
        for y in range(28, height - 24, 12):
            draw.rectangle([x0 + 3, y, x0 + 12, y + 2], fill=(*accent, 190))
            draw.point((x0 + 7, y + 5), fill=(*light, 230))

    # Bottom cracks: every phase gets a different fracture family.
    for _ in range(5 + phase):
        x = rng.randint(24, width - 32)
        y = rng.randint(height - 26, height - 6)
        points = [(x, y)]
        for _ in range(3):
            x += rng.randint(-8, 8)
            y += rng.randint(-5, 4)
            points.append((x, y))
        draw.line(points, fill=(*accent, 200), width=1)
        draw.point(points[-1], fill=(*light, 255))

    # Phase identity: a legible silhouette, not only a tint.
    if phase == 0:  # cocoon strands and a sealed shield
        for offset in range(0, 5):
            draw.arc([20 + offset * 3, 18 + offset, width - 21 - offset * 3, 44 + offset],
                     200, 340, fill=(*accent, 90), width=1)
        _hex(draw, width // 2, 18, 7, accent, light)
    elif phase == 1:  # broken cocoon, swift chevrons
        for x in range(34, width - 34, 18):
            draw.line([(x, 22), (x + 8, 16), (x + 16, 22)], fill=(*accent, 170))
        draw.line([(width // 2 - 9, 24), (width // 2, 17), (width // 2 + 9, 24)],
                  fill=(*light, 240))
    elif phase == 2:  # rift shards
        for x in range(38, width - 30, 26):
            draw.polygon([(x, 26), (x + 5, 16), (x + 10, 26), (x + 5, 32)],
                         fill=(*accent, 170))
    elif phase == 3:  # overload chevrons
        for x in range(width // 2 - 30, width // 2 + 30, 12):
            draw.line([(x, 28), (x + 6, 20), (x + 12, 28)], fill=(*light, 235))
        draw.rectangle([width // 2 - 24, 30, width // 2 + 24, 32], fill=(*accent, 210))
    elif phase == 4:  # rage claw
        for dx in (-18, -9, 0, 9, 18):
            draw.line([(width // 2 + dx, 15), (width // 2 + dx + 3, 30)],
                      fill=(*accent, 220))
    else:  # last seal ring
        draw.arc([width // 2 - 22, 10, width // 2 + 22, 40], 0, 360,
                 fill=(*light, 230), width=2)
        draw.arc([width // 2 - 14, 16, width // 2 + 14, 34], 0, 360,
                 fill=(*accent, 220), width=1)

    # Keep the real health recess and its immediate border clear.
    left, top, right, bottom = GUARDIAN_INNER
    draw.rectangle([left - 2, top - 2, right + 2, bottom + 2], fill=(0, 0, 0, 0))
    return image


def _hex(draw: ImageDraw.ImageDraw, cx: int, cy: int, radius: int, accent, light) -> None:
    points = []
    for index in range(6):
        angle = math.pi / 3.0 * index - math.pi / 6.0
        points.append((cx + radius * math.cos(angle), cy + radius * math.sin(angle)))
    draw.polygon(points, outline=(*light, 240))
    draw.point((cx, cy), fill=(*accent, 255))


def guardian_aura(phase: int, direction: str) -> Image.Image:
    """A 64x64 phase aura drawn over the real 44x44 rendered head.

    The centre stays transparent so the supplied head remains the identity.
    """
    size = 64
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    accent = PHASE_ACCENTS[phase]
    light = (min(255, accent[0] + 80), min(255, accent[1] + 80), min(255, accent[2] + 80))
    rim = 120 if direction == "a" else 90

    # A thin aura rim and four corner ticks, never over the face centre.
    draw.rectangle([2, 2, size - 3, size - 3], outline=(*accent, rim))
    for x, y, dx, dy in ((2, 2, 1, 1), (size - 3, 2, -1, 1), (2, size - 3, 1, -1),
                         (size - 3, size - 3, -1, -1)):
        draw.line([(x, y), (x + 7 * dx, y)], fill=(*light, 230))
        draw.line([(x, y), (x, y + 7 * dy)], fill=(*light, 230))
    # Eye glints sit roughly where the model's sockets are.
    draw.rectangle([24, 29, 26, 31], fill=(*light, 255))
    draw.rectangle([38, 29, 40, 31], fill=(*light, 255))
    # Phase badge: silhouette changes, not just the colour.
    bx, by = 4, 4
    if phase == 0:
        _hex(draw, bx + 6, by + 6, 5, accent, light)
    elif phase == 1:
        draw.line([(bx, by + 8), (bx + 6, by + 2), (bx + 12, by + 8)], fill=(*light, 240))
    elif phase == 2:
        draw.polygon([(bx + 6, by), (bx + 11, by + 6), (bx + 6, by + 12), (bx + 1, by + 6)],
                     fill=(*accent, 220))
    elif phase == 3:
        draw.polygon([(bx + 6, by), (bx + 12, by + 11), (bx, by + 11)], outline=(*light, 240))
    elif phase == 4:
        for dx in (0, 4, 8):
            draw.line([(bx + dx, by + 1), (bx + dx + 2, by + 11)], fill=(*accent, 230))
    else:
        draw.arc([bx, by, bx + 12, by + 12], 0, 360, fill=(*light, 240), width=2)
    return image


def state_glyph(phase: int) -> Image.Image:
    """16x16 silhouette-first state glyph for the Guardian phase row."""
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    accent = PHASE_ACCENTS[phase]
    light = (min(255, accent[0] + 90), min(255, accent[1] + 90), min(255, accent[2] + 90))
    if phase == 0:  # intact shield
        draw.polygon([(8, 1), (14, 4), (13, 11), (8, 15), (3, 11), (2, 4)],
                     outline=(*light, 255))
        draw.point((8, 7), fill=(*accent, 255))
    elif phase == 1:  # broken shield, irreversible
        draw.line([(8, 1), (4, 4), (4, 9), (8, 15)], fill=(*light, 255))
        draw.line([(8, 1), (12, 4), (11, 8)], fill=(*light, 255))
        draw.line([(7, 7), (11, 12)], fill=(*accent, 255))
    elif phase == 2:  # rift diamond
        draw.polygon([(8, 1), (14, 8), (8, 15), (2, 8)], fill=(*accent, 220))
        draw.line([(8, 3), (8, 13)], fill=(*light, 255))
    elif phase == 3:  # overload warning
        draw.polygon([(8, 1), (15, 14), (1, 14)], outline=(*light, 255))
        draw.line([(8, 5), (8, 10)], fill=(*accent, 255))
        draw.point((8, 12), fill=(*accent, 255))
    elif phase == 4:  # rage claw
        for dx in (3, 6, 9, 12):
            draw.line([(dx, 2), (dx - 1, 13)], fill=(*accent, 240))
        draw.line([(2, 13), (14, 13)], fill=(*light, 255))
    else:  # last seal ring
        draw.arc([1, 1, 14, 14], 0, 360, fill=(*light, 255), width=2)
        draw.point((8, 8), fill=(*accent, 255))
    return image


def state_glyph_strip() -> Image.Image:
    strip = Image.new("RGBA", GUARDIAN_GLYPHS, (0, 0, 0, 0))
    for phase in range(6):
        strip.paste(state_glyph(phase), (phase * 16, 0))
    return strip


def wave7_frame(style: str, direction: str) -> Image.Image:
    palette = WAVE7_STYLES[style] if direction == "a" else WAVE7_B_PALETTES[style]
    width, height = WAVE7_FRAME
    image = Image.new("RGBA", WAVE7_FRAME, (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    left, top, right, bottom = WAVE7_INNER
    rng = random.Random(0x7777 + sum(style.encode()) * 13 + (0 if direction == "a" else 1))

    # The plate: a heavy outer bevel, then end caps, then style ornament.  The
    # track recess is left fully transparent so the renderer's fill always shows.
    draw.rectangle([0, 4, width - 1, height - 6], fill=rgba(palette["edge"]))
    draw.rectangle([1, 5, width - 2, height - 7], outline=rgba(palette["border"]))
    draw.rectangle([3, 8, width - 4, height - 10], fill=rgba(palette["body"]))
    draw.polygon([(0, 9), (13, 4), (13, height - 6), (0, height - 11)], fill=rgba(palette["border"]))
    draw.polygon([(width - 1, 9), (width - 14, 4), (width - 14, height - 6), (width - 1, height - 11)],
                 fill=rgba(palette["border"]))
    draw.rectangle([left, top, right, bottom], fill=(0, 0, 0, 0))

    if style == "bone":
        # Vertebra notches above, a violet marrow seam below, arrow fletching
        # at the ends: the marksman reads as bone + arrows, not a coloured line.
        for index, x in enumerate(range(16, right - 6, 18)):
            draw.rectangle([x, 1, x + 9, 4], fill=rgba(palette["highlight"]))
            draw.rectangle([x + 3, 4, x + 5, 7], fill=rgba(palette["border"]))
            if index % 3 == 1:
                draw.line([(x + 1, 7), (x + 7, 11)], fill=rgba(palette["accent"]))
        draw.line([(left + 2, height - 5), (right - 2, height - 5)], fill=rgba(palette["accent"]))
        for x in (6, 10, width - 11, width - 7):
            draw.line([(x, 5), (x - 2, 10)], fill=rgba(palette["highlight"]))
    elif style == "sculk":
        # Asymmetrical sensor: one heavy ring on the left, ribs on the right.
        draw.arc([8, 0, 30, 10], 0, 360, fill=rgba(palette["glow"]), width=2)
        draw.arc([13, 4, 25, 9], 0, 360, fill=rgba(palette["border"]), width=1)
        draw.point((19, 5), fill=rgba(palette["highlight"]))
        for x in range(right - 54, right - 4, 8):
            draw.rectangle([x, height - 9, x + 4, height - 7], fill=rgba(palette["accent"]))
            draw.point((x + 2, height - 10), fill=rgba(palette["glow"]))
        for x in range(16, right - 6, 24):
            draw.line([(x, 1), (x + 4, 5)], fill=rgba(palette["accent"]))
    elif style == "rune":
        # Runic stone ring: deliberate nodes, an arc at each end, and small
        # charge pips that the renderer can pulse during a real cast.
        draw.arc([4, 0, 26, 14], 180, 360, fill=rgba(palette["glow"]), width=2)
        draw.arc([width - 27, 0, width - 5, 14], 180, 360, fill=rgba(palette["glow"]), width=2)
        glyphs = ([(0, 6), (4, 0), (8, 6)], [(0, 0), (8, 0), (8, 6)],
                  [(0, 6), (0, 0), (8, 6)], [(4, 0), (4, 6), (8, 3)])
        for index, x in enumerate(range(30, right - 10, 22)):
            shape = glyphs[index % len(glyphs)]
            draw.line([(x + dx, 1 + dy) for dx, dy in shape], fill=rgba(palette["glow"]))
        draw.rectangle([width // 2 - 7, height - 7, width // 2 + 7, height - 5],
                       fill=rgba(palette["accent"]))
    else:  # mirror
        # A fractured reflection: identical halves, one vertical break, muted
        # silver.  The break is deliberately not a white flash.
        for index, x in enumerate(range(16, right - 10, 24)):
            draw.rectangle([x, 1, x + 6, 4], fill=rgba(palette["border"]))
            draw.rectangle([width - x - 6, 1, width - x, 4], fill=rgba(palette["border"]))
        seam = width // 2
        draw.line([(seam, 0), (seam - 2, 7), (seam + 2, 11)],
                  fill=rgba(palette["highlight"]))
        # The crack is interrupted over the real health track and resumes below.
        draw.line([(seam + 2, 21), (seam - 1, height - 1)],
                  fill=rgba(palette["highlight"]))
        for y in (2, 6, 24, 28):
            draw.point((seam - 3, y), fill=rgba(palette["accent"]))

    # Rivets along the underside; direction B uses fewer, larger rivets.
    step = 18 if direction == "a" else 24
    for x in range(18, width - 16, step):
        _rivet(draw, x, height - 5, palette["highlight"], palette["edge"])
    if direction == "a":
        draw.line([(8, 6), (width - 9, 6)], fill=rgba(palette["accent"]))
    return image


def wave7_portrait(stem: str, direction: str) -> Image.Image:
    style, painter = {
        "marksman": ("bone", _portrait_marksman),
        "warden": ("sculk", _portrait_warden),
        "archmage": ("rune", _portrait_archmage),
        "echo": ("mirror", _portrait_echo),
    }[stem]
    palette = WAVE7_STYLES[style] if direction == "a" else WAVE7_B_PALETTES[style]
    image = Image.new("RGBA", WAVE7_PORTRAIT, (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    painter(draw, palette, 4)
    # A consistent family frame around every portrait.
    draw.rectangle([2, 2, 125, 125], outline=rgba(palette["accent"]))
    for x, y, dx, dy in ((2, 2, 1, 1), (125, 2, -1, 1), (2, 125, 1, -1), (125, 125, -1, -1)):
        draw.line([(x, y), (x + 12 * dx, y)], fill=rgba(palette["highlight"]))
        draw.line([(x, y), (x, y + 12 * dy)], fill=rgba(palette["highlight"]))
    return image


def _rect(draw, x0, y0, x1, y1, palette, key, scale=4):
    draw.rectangle([x0 * scale, y0 * scale, x1 * scale, y1 * scale], fill=rgba(palette[key]))


def _portrait_marksman(draw, palette, scale):
    _rect(draw, 8, 4, 23, 20, palette, "border", scale)
    _rect(draw, 10, 6, 21, 18, palette, "highlight", scale)
    _rect(draw, 11, 19, 20, 26, palette, "border", scale)
    _rect(draw, 12, 9, 15, 14, palette, "body", scale)
    _rect(draw, 17, 9, 20, 14, palette, "body", scale)
    _rect(draw, 13, 11, 15, 13, palette, "glow", scale)
    _rect(draw, 18, 11, 20, 13, palette, "glow", scale)
    _rect(draw, 15, 15, 17, 18, palette, "body", scale)
    for x in range(12, 20, 2):
        _rect(draw, x, 20, x + 1, 25, palette, "highlight", scale)
    # Arrows in the quiver behind the skull.
    for x in (5, 26):
        draw.line([(x * scale, 4 * scale), (x * scale, 26 * scale)], fill=rgba(palette["border"]))
        draw.polygon([(x * scale, 2 * scale), ((x + 2) * scale, 6 * scale),
                      ((x - 2) * scale, 6 * scale)], fill=rgba(palette["accent"]))
    draw.line([(9 * scale, 8 * scale), (13 * scale, 5 * scale)], fill=rgba(palette["accent"]))
    draw.line([(20 * scale, 24 * scale), (24 * scale, 18 * scale)], fill=rgba(palette["accent"]))


def _portrait_warden(draw, palette, scale):
    _rect(draw, 5, 6, 26, 22, palette, "accent", scale)
    _rect(draw, 7, 8, 24, 20, palette, "body", scale)
    for y in range(9, 20, 3):
        _rect(draw, 8, y, 23, y + 1, palette, "accent", scale)
    _rect(draw, 9, 11, 14, 14, palette, "body", scale)
    _rect(draw, 17, 11, 22, 14, palette, "body", scale)
    _rect(draw, 10, 12, 13, 13, palette, "glow", scale)
    _rect(draw, 18, 12, 21, 13, palette, "glow", scale)
    # Asymmetrical sensor pit on the right temple.
    draw.arc([20 * scale, 4 * scale, 30 * scale, 15 * scale], 0, 360,
             fill=rgba(palette["glow"]), width=2)
    draw.point((25 * scale, 9 * scale), fill=rgba(palette["highlight"]))
    draw.line([(7 * scale, 7 * scale), (3 * scale, 2 * scale)], fill=rgba(palette["border"]))
    _rect(draw, 13, 19, 18, 25, palette, "accent", scale)


def _portrait_archmage(draw, palette, scale):
    draw.polygon([(16 * scale, 2 * scale), (29 * scale, 25 * scale), (3 * scale, 25 * scale)],
                 fill=rgba(palette["accent"]))
    draw.polygon([(16 * scale, 5 * scale), (26 * scale, 24 * scale), (6 * scale, 24 * scale)],
                 fill=rgba(palette["body"]))
    _rect(draw, 11, 13, 14, 16, palette, "glow", scale)
    _rect(draw, 18, 13, 21, 16, palette, "glow", scale)
    _rect(draw, 14, 18, 18, 22, palette, "border", scale)
    # Runic stone ring behind the hood.
    draw.arc([2 * scale, 0, 30 * scale, 30 * scale], 20, 340,
             fill=rgba(palette["glow"]), width=2)
    for angle_point in ((4, 8), (28, 8), (3, 22), (29, 22)):
        draw.point((angle_point[0] * scale, angle_point[1] * scale),
                   fill=rgba(palette["highlight"]))


def _portrait_echo(draw, palette, scale):
    _rect(draw, 9, 4, 22, 25, palette, "border", scale)
    _rect(draw, 11, 6, 20, 23, palette, "body", scale)
    _rect(draw, 12, 9, 15, 12, palette, "glow", scale)
    _rect(draw, 17, 9, 20, 12, palette, "glow", scale)
    _rect(draw, 14, 17, 18, 19, palette, "accent", scale)
    # Fracture seam and a muted half-reflection.
    draw.line([(16 * scale, 2 * scale), (14 * scale, 10 * scale),
               (17 * scale, 18 * scale), (15 * scale, 28 * scale)],
              fill=rgba(palette["highlight"]))
    for y in range(6, 24, 4):
        draw.point((13 * scale, y * scale), fill=rgba(palette["glow"]))
    draw.polygon([(25 * scale, 6 * scale), (30 * scale, 10 * scale),
                  (27 * scale, 20 * scale)], fill=rgba(palette["accent"]))


def _save(image: Image.Image, path: Path, written: list[Path]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, optimize=False)
    written.append(path)


def build(out: Path = OUT, archive: Path = ARCHIVE) -> list[Path]:
    written: list[Path] = []

    # C1 - Ram.  Direction A ships; both directions are archived.
    _save(ram_rail(RAM_A), out / "ram_seal_rail.png", written)
    _save(ram_plate_atlas(RAM_A, "a"), out / "ram_seal_plates.png", written)
    _save(ram_rail(RAM_A), archive / "ram_seal_rail_a.png", written)
    _save(ram_rail(RAM_B), archive / "ram_seal_rail_b.png", written)
    _save(ram_plate_atlas(RAM_A, "a"), archive / "ram_seal_plates_a.png", written)
    _save(ram_plate_atlas(RAM_B, "b"), archive / "ram_seal_plates_b.png", written)

    # C2 - Guardian phase overlays, portrait auras and state glyphs.
    atlas = Image.new("RGBA", GUARDIAN_ATLAS, (0, 0, 0, 0))
    atlas_b = Image.new("RGBA", GUARDIAN_ATLAS, (0, 0, 0, 0))
    aura = Image.new("RGBA", GUARDIAN_AURA, (0, 0, 0, 0))
    aura_b = Image.new("RGBA", GUARDIAN_AURA, (0, 0, 0, 0))
    for phase in range(6):
        atlas.paste(guardian_overlay(phase, "a"), (0, phase * GUARDIAN_FRAME[1]))
        atlas_b.paste(guardian_overlay(phase, "b"), (0, phase * GUARDIAN_FRAME[1]))
        aura.paste(guardian_aura(phase, "a"), (phase * 64, 0))
        aura_b.paste(guardian_aura(phase, "b"), (phase * 64, 0))
    _save(atlas, out / "end_rift_guardian_phase_overlay.png", written)
    _save(aura, out / "end_rift_guardian_portrait_aura.png", written)
    _save(state_glyph_strip(), out / "end_rift_guardian_state_glyphs.png", written)
    _save(atlas, archive / "guardian_phase_overlay_a.png", written)
    _save(atlas_b, archive / "guardian_phase_overlay_b.png", written)
    _save(aura, archive / "guardian_portrait_aura_a.png", written)
    _save(aura_b, archive / "guardian_portrait_aura_b.png", written)

    # C3 - four redrawn Wave 7 bars plus portraits, both directions.
    for style in WAVE7_STYLES:
        _save(wave7_frame(style, "a"), out / f"wave7v2_bossbar_{style}.png", written)
        _save(wave7_frame(style, "a"), archive / f"wave7_{style}_frame_a.png", written)
        _save(wave7_frame(style, "b"), archive / f"wave7_{style}_frame_b.png", written)
    for stem in ("marksman", "warden", "archmage", "echo"):
        _save(wave7_portrait(stem, "a"), out / f"wave7v2_portrait_{stem}.png", written)
        _save(wave7_portrait(stem, "a"), archive / f"wave7_{stem}_portrait_a.png", written)
        _save(wave7_portrait(stem, "b"), archive / f"wave7_{stem}_portrait_b.png", written)

    # A human-readable comparison of the two directions.
    note = archive / "README.md"
    note.parent.mkdir(parents=True, exist_ok=True)
    note.write_text(
        "# End Rift HUD art directions (2026-11-21)\n\n"
        "Every major bar was authored twice and compared before shipping.\n\n"
        "- Direction **a** (shipped in the client jar): dark forged obsidian and\n"
        "  steel with dull bone inlays and restrained purple embers.\n"
        "- Direction **b** (archived alternative): pale bone and iron with warm\n"
        "  ember accents; kept so the owner can compare after the playtest.\n\n"
        "The filenames ending in `_a`/`_b` are the editable preview exports.\n"
        "The shipped copies live under\n"
        "`CopiMineClient/src/main/resources/assets/copimineclient/textures/gui`\n"
        "and are produced by `scripts/minecraft/GenerateEndRiftHudRedesign.py`.\n\n"
        "Pairs cover the Ram rail and seven-plate atlas, the Guardian phase\n"
        "overlay and portrait aura, and all four Wave 7 frames and portraits\n"
        "(12 pairs, 24 PNGs). The Guardian state glyphs are silhouette-first and\n"
        "identical in both directions, so they are not duplicated here.\n\n"
        "To rebuild:\n\n"
        "```\n"
        "python scripts/minecraft/GenerateEndRiftHudRedesign.py\n"
        "```\n\n"
        "The generator is deterministic: rebuilding into a scratch directory\n"
        "produces byte-identical PNGs, so the client jar pin stays reproducible.\n"
        "It clears each health recess last, after every river, crack and inlay,\n"
        "so the renderer's real HP fill is never hidden behind the art.\n",
        encoding="utf-8",
    )
    written.append(note)
    return written


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true",
                        help="fail when the committed PNGs differ from a fresh build")
    parser.add_argument("--out", type=Path, default=OUT)
    parser.add_argument("--archive", type=Path, default=ARCHIVE)
    args = parser.parse_args(argv)
    before = {}
    for root in (args.out, args.archive):
        for path in root.rglob("*"):
            if path.is_file():
                before[path] = hashlib.sha256(path.read_bytes()).hexdigest()
    written = build(args.out, args.archive)
    for path in written:
        data = path.read_bytes()
        print(f"{path.name}\t{len(data)}\t{hashlib.sha256(data).hexdigest()[:16]}")
    if args.check:
        changed = [path for path in written
                   if path.suffix == ".png" and before.get(path) != hashlib.sha256(path.read_bytes()).hexdigest()]
        if changed:
            for path in changed:
                print(f"CHANGED {path}")
            return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())