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
    """Draw the front bind-pose proportions represented by the Java rig."""

    image = Image.new("RGBA", (360, 620), (4, 2, 8, 255))
    draw = ImageDraw.Draw(image)
    cx = 180

    def rect(box: tuple[int, int, int, int], fill: tuple[int, int, int, int], outline: tuple[int, int, int, int] = PURPLE_LIGHT) -> None:
        draw.rounded_rectangle(box, radius=4, fill=fill, outline=outline, width=2)

    # Head and body share an edge; limbs overlap at the upper/lower joints.
    rect((cx - 44, 34, cx + 44, 118), PURPLE_DARK)
    rect((cx - 30, 118, cx + 30, 262), PURPLE)
    draw.rectangle((cx - 11, 160, cx + 11, 205), fill=MAGENTA)
    draw.rectangle((cx - 6, 166, cx + 6, 198), fill=(214, 24, 255, 255))

    # Long arms: 9-unit upper segment + overlapping 12-unit forearm.
    for side in (-1, 1):
        shoulder_x = cx + side * 47
        upper = (shoulder_x - 18, 113, shoulder_x + 18, 244)
        forearm = (shoulder_x - 14, 232, shoulder_x + 14, 414)
        rect(upper, PURPLE_DARK)
        rect(forearm, PURPLE)
        if elite:
            rect((shoulder_x - 25, 104, shoulder_x + 25, 143), PURPLE_LIGHT, MAGENTA)
        draw.line((shoulder_x, 237, shoulder_x, 411), fill=PURPLE_LIGHT, width=3)

    # Long legs: 8-unit upper segment + overlapping 13-unit lower leg.
    for side in (-1, 1):
        leg_x = cx + side * 17
        upper = (leg_x - 14, 255, leg_x + 14, 371)
        lower = (leg_x - 11, 360, leg_x + 11, 548)
        rect(upper, PURPLE_DARK)
        rect(lower, PURPLE)
        draw.line((leg_x, 363, leg_x, 545), fill=PURPLE_LIGHT, width=3)

    draw.text((12, 580), "continuous child-part joints", fill=MUTED, font=font(16))
    return image


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--normal-reference", type=Path, default=DEFAULT_NORMAL_REFERENCE)
    parser.add_argument("--elite-reference", type=Path, default=DEFAULT_ELITE_REFERENCE)
    parser.add_argument("--output", type=Path, default=OUTPUT)
    args = parser.parse_args()

    canvas = Image.new("RGBA", (1600, 1120), BACKGROUND)
    draw = ImageDraw.Draw(canvas)
    draw.text((42, 28), "CopiMine End Rift — wave-mob visual proof", fill=TEXT, font=font(38))
    draw.text((44, 75), "Reference style, clean opaque atlases, and the dedicated continuous skeleton rig", fill=MUTED, font=font(20))

    cards = [
        ((36, 125, 390, 635), "User reference · normal", "supplied wave skeleton style", args.normal_reference),
        ((410, 125, 1040, 635), "User reference · elite", "supplied elite silhouette style", args.elite_reference),
        ((1060, 125, 1564, 380), "Generated UV atlas · normal", "64×32 · opaque · dark-purple palette", ENTITY / "end_rift_skeleton.png"),
        ((1060, 400, 1564, 655), "Generated UV atlas · elite", "64×32 · opaque · dark-purple palette", ENTITY / "end_rift_elite_skeleton.png"),
    ]
    for box, title, subtitle, path in cards:
        draw_card(canvas, box, title, subtitle)
        paste_card_image(canvas, load_or_placeholder(path, (300, 240), title), box)

    draw_card(canvas, (36, 670, 780, 1080), "Source rig · ordinary", "front bind-pose schematic · vanilla skeleton hitbox preserved")
    paste_card_image(canvas, skeleton_schematic(False), (36, 670, 780, 1080))
    draw_card(canvas, (804, 670, 1564, 1080), "Source rig · elite", "front bind-pose schematic · shoulder plates are render-only")
    paste_card_image(canvas, skeleton_schematic(True), (804, 670, 1564, 1080))

    draw.text((44, 1090), "Static source/artifact proof — native Minecraft screenshot still requires an available Computer Use surface.", fill=MUTED, font=font(16))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    canvas.convert("RGB").save(args.output, format="PNG", optimize=False)
    print(args.output)


if __name__ == "__main__":
    main()
