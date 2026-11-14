"""Author the Wave 7 miniboss boss-bar art.

The Guardian bar ships a dedicated frame atlas and a head portrait inside the
client jar, so the End Rift HUD already looks authored instead of vanilla. The
Wave 7 minibosses never received the same treatment: their bars are created by
the server, so without art they fall back to the plain Bukkit bar.

This generator produces one frame and one portrait per miniboss, in the same
pixel-art language as ``end_rift_bossbar_frame.png``:

* ``wave7_bossbar_bone.png`` / ``wave7_portrait_marksman.png`` - a skeleton
  whose bone is infected by the End: clean bone plates split by violet
  fractures and a violet socket glow.
* ``wave7_bossbar_sculk.png`` / ``wave7_portrait_warden.png`` - sculk teal.
* ``wave7_bossbar_rune.png`` / ``wave7_portrait_archmage.png`` - a hooded
  caster with burning pink runes.
* ``wave7_bossbar_mirror.png`` / ``wave7_portrait_echo.png`` - a mirrored copy,
  silver cut by cyan.

Everything is procedural and deterministic: the same inputs always produce the
same bytes, so the pack hash pin cannot drift between machines.
"""

from __future__ import annotations

import hashlib
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

FRAME_SIZE = (256, 32)
PORTRAIT_SIZE = (128, 128)


def rgba(value: int) -> tuple[int, int, int, int]:
    return (
        (value >> 16) & 0xFF,
        (value >> 8) & 0xFF,
        value & 0xFF,
        (value >> 24) & 0xFF,
    )


# Per-kind palettes. Keep them close to the plugin's
# Wave7MinibossBossBarPolicy accents so the HUD fill and the frame agree.
PALETTES = {
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


def _frame(style: str) -> Image.Image:
    """A 256x32 bar frame: bevel, inner track, and a style-specific ornament."""
    palette = PALETTES[style]
    image = Image.new("RGBA", FRAME_SIZE, (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    width, height = FRAME_SIZE

    # Outer bevel and body. The HUD draws the fill itself inside the track, so
    # the middle stays fully transparent except for the ornaments.
    draw.rectangle([0, 4, width - 1, height - 6], fill=rgba(palette["edge"]))
    draw.rectangle([1, 5, width - 2, height - 7], outline=rgba(palette["border"]))
    draw.rectangle([3, 8, width - 4, height - 10], fill=rgba(palette["body"]))

    # Bevelled end caps, so the bar reads as forged rather than drawn.
    draw.polygon([(0, 8), (6, 4), (6, height - 6), (0, height - 12)], fill=rgba(palette["border"]))
    draw.polygon(
        [(width - 1, 8), (width - 7, 4), (width - 7, height - 6), (width - 1, height - 12)],
        fill=rgba(palette["border"]),
    )

    if style == "bone":
        # Vertebra notches: the bar is a spine, and the End corruption is the
        # violet that runs through the seams instead of clean bone.
        for index in range(10):
            x = 12 + index * 24
            draw.rectangle([x, 2, x + 8, 4], fill=rgba(palette["highlight"]))
            draw.rectangle([x + 3, 4, x + 5, 6], fill=rgba(palette["border"]))
            if index % 3 == 1:
                draw.line([(x + 1, 6), (x + 6, 10)], fill=rgba(palette["accent"]))
                draw.point((x + 6, 10), fill=rgba(palette["glow"]))
    elif style == "sculk":
        # Sculk tendrils creep up from the underside of the track.
        for index in range(12):
            x = 10 + index * 20
            draw.rectangle([x, height - 9, x + 4, height - 7], fill=rgba(palette["accent"]))
            draw.point((x + 2, height - 10), fill=rgba(palette["glow"]))
            if index % 4 == 2:
                draw.line([(x + 2, height - 11), (x + 5, height - 14)], fill=rgba(palette["glow"]))
    elif style == "rune":
        # Alternating angular runes above the track.
        glyphs = [
            [(0, 6), (4, 0), (8, 6)],
            [(0, 0), (8, 0), (8, 6)],
            [(0, 6), (0, 0), (8, 6)],
            [(4, 0), (4, 6), (8, 3)],
        ]
        for index in range(11):
            x = 14 + index * 21
            shape = glyphs[index % len(glyphs)]
            draw.line([(x + dx, 2 + dy) for dx, dy in shape], fill=rgba(palette["glow"]))
    else:  # mirror
        # A reflected bar: identical ticks on both halves, cyan at the seam.
        for index in range(8):
            x = 16 + index * 28
            draw.rectangle([x, 2, x + 6, 5], fill=rgba(palette["border"]))
            draw.rectangle([width - x - 6, 2, width - x, 5], fill=rgba(palette["border"]))
        draw.line([(width // 2, 0), (width // 2, height - 1)], fill=rgba(palette["glow"]))

    # Top highlight: one row of the accent keeps the frame from reading flat.
    draw.line([(8, 6), (width - 9, 6)], fill=rgba(palette["accent"]))
    return image


def _skull(draw: ImageDraw.ImageDraw, palette: dict, scale: int) -> None:
    """A skull whose bone is infected by the End."""

    def rect(x0, y0, x1, y1, key):
        draw.rectangle([x0 * scale, y0 * scale, x1 * scale, y1 * scale], fill=rgba(palette[key]))

    # Cranium and jaw.
    rect(7, 5, 24, 20, "border")
    rect(9, 7, 22, 18, "highlight")
    rect(10, 20, 21, 26, "border")
    # Eye sockets with violet glow: the marrow is not bone any more.
    rect(11, 10, 15, 15, "body")
    rect(17, 10, 21, 15, "body")
    rect(12, 12, 14, 14, "glow")
    rect(18, 12, 20, 14, "glow")
    rect(12, 12, 13, 13, "accent")
    rect(18, 12, 19, 13, "accent")
    # Nose and teeth.
    rect(15, 16, 17, 19, "body")
    for x in range(11, 21, 2):
        rect(x, 21, x + 1, 25, "highlight")
    # End fractures running through the bone.
    draw.line([(8 * scale, 9 * scale), (12 * scale, 6 * scale)], fill=rgba(palette["accent"]))
    draw.line([(20 * scale, 24 * scale), (24 * scale, 19 * scale)], fill=rgba(palette["accent"]))
    draw.point((12 * scale, 6 * scale), fill=rgba(palette["glow"]))


def _warden(draw: ImageDraw.ImageDraw, palette: dict, scale: int) -> None:
    """Broad sculk head with the sonic core burning through."""

    def rect(x0, y0, x1, y1, key):
        draw.rectangle([x0 * scale, y0 * scale, x1 * scale, y1 * scale], fill=rgba(palette[key]))

    rect(6, 6, 25, 22, "accent")
    rect(8, 8, 23, 20, "body")
    # Rib plates.
    for y in range(9, 20, 3):
        rect(9, y, 22, y + 1, "accent")
    # Eyes: two burning slits.
    rect(10, 11, 14, 14, "body")
    rect(17, 11, 21, 14, "body")
    rect(11, 12, 13, 13, "glow")
    rect(18, 12, 20, 13, "glow")
    # Antennae of sculk.
    draw.line([(8 * scale, 7 * scale), (4 * scale, 2 * scale)], fill=rgba(palette["border"]))
    draw.line([(23 * scale, 7 * scale), (27 * scale, 2 * scale)], fill=rgba(palette["border"]))
    draw.point((4 * scale, 2 * scale), fill=rgba(palette["glow"]))
    draw.point((27 * scale, 2 * scale), fill=rgba(palette["glow"]))
    rect(14, 19, 17, 24, "accent")


def _archmage(draw: ImageDraw.ImageDraw, palette: dict, scale: int) -> None:
    """Hooded caster, face in shadow, runes at the hood's edge."""

    def rect(x0, y0, x1, y1, key):
        draw.rectangle([x0 * scale, y0 * scale, x1 * scale, y1 * scale], fill=rgba(palette[key]))

    # Hood: a wide triangle over a dark face void.
    draw.polygon(
        [
            (16 * scale, 3 * scale),
            (28 * scale, 24 * scale),
            (4 * scale, 24 * scale),
        ],
        fill=rgba(palette["accent"]),
    )
    draw.polygon(
        [
            (16 * scale, 6 * scale),
            (25 * scale, 23 * scale),
            (7 * scale, 23 * scale),
        ],
        fill=rgba(palette["body"]),
    )
    # Burning eyes and a spell glow under the hood.
    rect(12, 14, 14, 16, "glow")
    rect(18, 14, 20, 16, "glow")
    rect(15, 18, 17, 21, "border")
    # Runes hanging off the hood rim.
    draw.line([(5 * scale, 25 * scale), (10 * scale, 28 * scale)], fill=rgba(palette["glow"]))
    draw.line([(27 * scale, 25 * scale), (22 * scale, 28 * scale)], fill=rgba(palette["glow"]))


def _echo(draw: ImageDraw.ImageDraw, palette: dict, scale: int) -> None:
    """A mirrored copy: silver silhouette, cyan interior, split down the middle."""

    def rect(x0, y0, x1, y1, key):
        draw.rectangle([x0 * scale, y0 * scale, x1 * scale, y1 * scale], fill=rgba(palette[key]))

    rect(8, 5, 23, 24, "border")
    rect(10, 7, 21, 22, "body")
    rect(12, 10, 14, 13, "glow")
    rect(18, 10, 20, 13, "glow")
    rect(14, 17, 18, 19, "accent")
    # The mirror seam: the copy is only half-lit.
    draw.line([(16 * scale, 4 * scale), (16 * scale, 27 * scale)], fill=rgba(palette["highlight"]))
    for y in range(8, 24, 4):
        draw.point((15 * scale, y * scale), fill=rgba(palette["glow"]))


PORTRAIT_PAINTERS = {
    "marksman": ("bone", _skull),
    "warden": ("sculk", _warden),
    "archmage": ("rune", _archmage),
    "echo": ("mirror", _echo),
}


def _portrait(stem: str) -> Image.Image:
    style, painter = PORTRAIT_PAINTERS[stem]
    palette = PALETTES[style]
    image = Image.new("RGBA", PORTRAIT_SIZE, (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    painter(draw, palette, 4)
    return image


def build() -> list[Path]:
    OUT.mkdir(parents=True, exist_ok=True)
    written: list[Path] = []
    for style in PALETTES:
        path = OUT / f"wave7_bossbar_{style}.png"
        _frame(style).save(path)
        written.append(path)
    for stem in PORTRAIT_PAINTERS:
        path = OUT / f"wave7_portrait_{stem}.png"
        _portrait(stem).save(path)
        written.append(path)
    return written


def main() -> int:
    for path in build():
        data = path.read_bytes()
        print(f"{path.name}\t{len(data)}\t{hashlib.sha256(data).hexdigest()[:16]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())