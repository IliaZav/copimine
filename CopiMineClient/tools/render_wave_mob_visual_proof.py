"""Render a deterministic visual proof for the wave-mob art contract.

This is intentionally a source/artifact preview, not a claim of a native
Minecraft capture. It puts the supplied reference images, the checked-in UV
atlases, and a front-view schematic of the Java skeleton rig in one image so a
reviewer can inspect palette, opacity, proportions, and joint continuity.
"""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont, ImageOps


ROOT = Path(__file__).resolve().parents[2]
ENTITY = (
    ROOT
    / "CopiMineClient"
    / "src"
    / "main"
    / "resources"
    / "assets"
    / "copimineclient"
    / "textures"
    / "entity"
)
DEFAULT_NORMAL_REFERENCE = Path(
    "C:/Users/zavod/AppData/Local/Temp/"
    "codex-clipboard-cc4c0813-7b2f-4ed3-9fd6-563ffb9c12be.png"
)
DEFAULT_ELITE_REFERENCE = Path(
    "C:/Users/zavod/AppData/Local/Temp/"
    "codex-clipboard-f96b5867-5d10-44a7-9781-6d12fd52d6c6.png"
)
OUTPUT = ROOT / "artifacts" / "end-rift-v3-evidence" / "wave-mob-reference-style-proof-20260916.png"

BACKGROUND = (9, 6, 16, 255)
CARD = (22, 16, 34, 255)
CARD_EDGE = (71, 38, 91, 255)
TEXT = (244, 237, 250, 255)
MUTED = (178, 158, 191, 255)
PURPLE_DARK = (20, 4, 30, 255)
PURPLE = (52, 14, 67, 255)
PURPLE_LIGHT = (112, 24, 142, 255)
MAGENTA = (156, 0, 255, 255)
WHITE = (236, 236, 244, 255)


def font(size: int) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    for candidate in (
        Path("C:/Windows/Fonts/arial.ttf"),
        Path("C:/Windows/Fonts/segoeui.ttf"),
    ):
        if candidate.is_file():
            return ImageFont.truetype(str(candidate), size)
    return ImageFont.load_default()


def fit_on_card(image: Image.Image, width: int, height: int) -> Image.Image:
    image = image.convert("RGBA")
    background = Image.new("RGBA", (width, height), (4, 2, 8, 255))
    contained = ImageOps.contain(image, (width - 24, height - 24), Image.Resampling.NEAREST)
    left = (width - contained.width) // 2
    top = (height - contained.height) // 2
    background.alpha_composite(contained, (left, top))
    return background


def draw_card(canvas: Image.Image, box: tuple[int, int, int, int], title: str, subtitle: str) -> ImageDraw.ImageDraw:
    draw = ImageDraw.Draw(canvas)
    x0, y0, x1, y1 = box
    draw.rounded_rectangle(box, radius=14, fill=CARD, outline=CARD_EDGE, width=2)
    draw.text((x0 + 18, y0 + 14), title, fill=TEXT, font=font(26))
    draw.text((x0 + 18, y0 + 48), subtitle, fill=MUTED, font=font(16))
    return draw


def paste_card_image(canvas: Image.Image, image: Image.Image, box: tuple[int, int, int, int]) -> None:
    x0, y0, x1, y1 = box
    inner = (x0 + 12, y0 + 84, x1 - 12, y1 - 12)
    fitted = fit_on_card(image, inner[2] - inner[0], inner[3] - inner[1])
    canvas.alpha_composite(fitted, (inner[0], inner[1]))


def load_or_placeholder(path: Path, size: tuple[int, int], label: str) -> Image.Image:
    if path.is_file():
        return Image.open(path).convert("RGBA")
    image = Image.new("RGBA", size, (4, 2, 8, 255))
    ImageDraw.Draw(image).text((24, size[1] // 2), f"missing: {label}", fill=TEXT, font=font(18))
    return image


def skeleton_schematic(elite: bool) -> Image.Image:
    """Draw the front silhouette represented by the segmented Java rig."""

    image = Image.new("RGBA", (360, 620), (4, 2, 8, 255))
    draw = ImageDraw.Draw(image)
    cx = 180
    bone = (205, 199, 216, 255)
    bone_light = (246, 243, 250, 255)

    def poly(points: list[tuple[int, int]], fill: tuple[int, int, int, int],
             outline: tuple[int, int, int, int] = PURPLE_LIGHT) -> None:
        draw.polygon(points, fill=fill, outline=outline)

    # The bind pose is deliberately tapered and layered. The overlay pieces
    # are rendered as bone plates/rings, not as a stack of disconnected cubes.
    poly([(cx - 38, 40), (cx - 28, 28), (cx + 28, 28), (cx + 38, 40),
          (cx + 32, 101), (cx + 20, 116), (cx - 20, 116), (cx - 32, 101)], PURPLE_DARK)
    draw.polygon([(cx - 19, 104), (cx + 19, 104), (cx + 14, 117), (cx - 14, 117)], fill=bone)
    draw.rectangle((cx - 17, 57, cx - 7, 67), fill=MAGENTA)
    draw.rectangle((cx + 7, 57, cx + 17, 67), fill=MAGENTA)
    if elite:
        poly([(cx - 27, 31), (cx - 15, 5), (cx - 8, 31)], PURPLE, MAGENTA)
        poly([(cx + 27, 31), (cx + 15, 5), (cx + 8, 31)], PURPLE, MAGENTA)

    poly([(cx - 25, 113), (cx + 25, 113), (cx + 31, 248),
          (cx + 17, 268), (cx - 17, 268), (cx - 31, 248)], PURPLE)
    # Ribs and chest fracture.
    for y, width in ((151, 22), (177, 25), (203, 22), (229, 18)):
        draw.arc((cx - width, y - 11, cx + width, y + 12), 188, 352, fill=bone, width=4)
    draw.line((cx, 142, cx - 5, 170, cx + 4, 198, cx - 2, 232, cx, 258), fill=MAGENTA, width=5, joint="curve")
    draw.polygon([(cx, 171), (cx + 9, 185), (cx, 202), (cx - 9, 185)], fill=MAGENTA, outline=bone_light)

    # Long upper/lower arms overlap at an actual joint ring.
    for side in (-1, 1):
        shoulder = cx + side * 45
        poly([(cx + side * 24, 119), (shoulder + side * 18, 127),
              (shoulder + side * 12, 248), (shoulder - side * 12, 251),
              (shoulder - side * 18, 137)], PURPLE_DARK)
        poly([(shoulder - side * 13, 239), (shoulder + side * 13, 241),
              (shoulder + side * 9, 431), (shoulder - side * 9, 431)], PURPLE)
        draw.ellipse((shoulder - 17, 229, shoulder + 17, 263), fill=bone, outline=bone_light, width=2)
        draw.ellipse((shoulder - 11, 235, shoulder + 11, 257), fill=PURPLE_DARK)
        draw.line((shoulder, 268, shoulder + side * 3, 420), fill=PURPLE_LIGHT, width=4)
        for y in (147, 205, 326, 407):
            draw.line((shoulder - side * 10, y, shoulder + side * 10, y + side * 3), fill=bone, width=4)
        if elite:
            poly([(shoulder - side * 29, 105), (shoulder + side * 26, 108),
                  (shoulder + side * 30, 139), (shoulder - side * 24, 147)], PURPLE_LIGHT, MAGENTA)

    # Long legs use a knee ring, shin plate and ankle plate; the upper/lower
    # sections overlap so animation cannot expose a transparent seam.
    for side in (-1, 1):
        leg = cx + side * 16
        poly([(leg - 13, 260), (leg + 13, 260), (leg + 11, 378),
              (leg - 11, 378)], PURPLE_DARK)
        draw.ellipse((leg - 15, 363, leg + 15, 393), fill=bone, outline=bone_light, width=2)
        poly([(leg - 10, 379), (leg + 10, 379), (leg + 9, 556),
              (leg - 9, 556)], PURPLE)
        draw.line((leg, 390, leg + side * 2, 549), fill=bone, width=5)
        draw.line((leg - 7, 486, leg + 7, 490), fill=bone_light, width=4)
        draw.line((leg - 8, 538, leg + 8, 538), fill=bone, width=5)

    draw.text((12, 580), "layered plates · overlapping joints · native hitbox", fill=MUTED, font=font(15))
    return image


def caster_schematic() -> Image.Image:
    """Draw the passive Wave 6 caster pose and its sphere channel."""
    image = Image.new("RGBA", (360, 620), (4, 2, 8, 255))
    draw = ImageDraw.Draw(image)
    cx = 180
    poly = lambda points, fill, outline=PURPLE_LIGHT: draw.polygon(points, fill=fill, outline=outline)
    poly([(cx - 28, 38), (cx - 18, 25), (cx + 18, 25), (cx + 28, 38),
          (cx + 22, 92), (cx - 22, 92)], PURPLE_DARK)
    draw.rectangle((cx - 13, 54, cx - 5, 63), fill=MAGENTA)
    draw.rectangle((cx + 5, 54, cx + 13, 63), fill=MAGENTA)
    poly([(cx - 19, 91), (cx + 19, 91), (cx + 25, 244),
          (cx - 25, 244)], PURPLE)
    draw.line((cx, 115, cx - 5, 156, cx + 5, 190, cx, 229), fill=MAGENTA, width=5)
    for side in (-1, 1):
        shoulder = cx + side * 22
        hand = cx + side * 79
        poly([(shoulder, 106), (shoulder + side * 15, 120),
              (hand + side * 10, 30), (hand - side * 5, 25)], PURPLE_DARK)
        draw.line((hand, 33, shoulder + side * 3, 113), fill=PURPLE_LIGHT, width=6)
        draw.ellipse((hand - 10, 18, hand + 10, 40), fill=(224, 214, 235, 255), outline=MAGENTA, width=2)
    draw.ellipse((cx - 24, 262, cx + 24, 310), fill=(25, 5, 38, 255), outline=MAGENTA, width=3)
    draw.ellipse((cx - 11, 275, cx + 11, 297), fill=(218, 43, 255, 255))
    draw.line((cx - 62, 285, cx - 23, 285), fill=(177, 70, 255, 255), width=2)
    draw.line((cx + 23, 285, cx + 62, 285), fill=(177, 70, 255, 255), width=2)
    for side in (-1, 1):
        leg = cx + side * 11
        poly([(leg - 9, 239), (leg + 9, 239), (leg + 7, 555), (leg - 7, 555)], PURPLE_DARK)
        draw.line((leg, 255, leg + side * 3, 548), fill=PURPLE_LIGHT, width=4)
    draw.text((12, 580), "raised-arm channel pose · caster-only visual", fill=MUTED, font=font(15))
    return image


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--normal-reference", type=Path, default=DEFAULT_NORMAL_REFERENCE)
    parser.add_argument("--elite-reference", type=Path, default=DEFAULT_ELITE_REFERENCE)
    parser.add_argument("--output", type=Path, default=OUTPUT)
    args = parser.parse_args()

    canvas = Image.new("RGBA", (1600, 1320), BACKGROUND)
    draw = ImageDraw.Draw(canvas)
    draw.text((42, 28), "CopiMine End Rift — wave-mob visual proof", fill=TEXT, font=font(38))
    draw.text((44, 75), "Reference style, clean opaque atlases, and the dedicated continuous skeleton rig", fill=MUTED, font=font(20))

    cards = [
        ((36, 125, 390, 635), "User reference · normal", "supplied wave skeleton style", args.normal_reference),
        ((410, 125, 1040, 635), "User reference · elite", "supplied elite silhouette style", args.elite_reference),
        ((1060, 125, 1564, 380), "Generated UV atlas · normal", "64×32 · opaque · dark-purple palette", ENTITY / "end_rift_skeleton.png"),
        ((1060, 400, 1564, 655), "Generated UV atlas · elite", "64×32 · opaque · dark-purple palette", ENTITY / "end_rift_elite_skeleton.png"),
        ((1060, 675, 1564, 930), "Generated UV atlas · caster", "64×32 · opaque · raised-arm variant", ENTITY / "end_rift_ritual_caster.png"),
    ]
    for box, title, subtitle, path in cards:
        draw_card(canvas, box, title, subtitle)
        paste_card_image(canvas, load_or_placeholder(path, (300, 240), title), box)

    draw_card(canvas, (36, 950, 530, 1280), "Source rig · ordinary", "layered skeleton silhouette")
    paste_card_image(canvas, skeleton_schematic(False), (36, 950, 530, 1280))
    draw_card(canvas, (548, 950, 1042, 1280), "Source rig · elite", "horns, shoulders, bone joints")
    paste_card_image(canvas, skeleton_schematic(True), (548, 950, 1042, 1280))
    draw_card(canvas, (1060, 950, 1564, 1280), "Source rig · caster", "passive raised-arm channel pose")
    paste_card_image(canvas, caster_schematic(), (1060, 950, 1564, 1280))

    draw.text((44, 1290), "Static source/artifact proof — native Minecraft screenshot still requires an available Computer Use surface.", fill=MUTED, font=font(16))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    canvas.convert("RGB").save(args.output, format="PNG", optimize=False)
    print(args.output)


if __name__ == "__main__":
    main()
