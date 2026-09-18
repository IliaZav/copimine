from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter


ROOT = Path(__file__).resolve().parent
OUT = ROOT / "src/assets/copimine/textures/item/night_cloak.png"
SIZE = 256


def make_night_cloak() -> Image.Image:
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    glow = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow, "RGBA")

    # The glow is kept behind the silhouette, so the icon remains legible even
    # when Minecraft renders it next to bright event rewards.
    glow_draw.polygon(
        [(128, 24), (83, 63), (40, 212), (128, 244), (216, 212), (173, 63)],
        fill=(88, 21, 244, 110),
    )
    glow_draw.ellipse((91, 25, 165, 99), fill=(32, 221, 255, 125))
    image = Image.alpha_composite(image, glow.filter(ImageFilter.GaussianBlur(16)))

    draw = ImageDraw.Draw(image, "RGBA")
    # A single, folded cloak silhouette; broad planes avoid the dirty checker
    # pattern that made the older End Rift models read as broken pixels.
    draw.polygon(
        [(128, 30), (99, 53), (83, 86), (70, 123), (44, 211),
         (83, 226), (128, 239), (173, 226), (212, 211), (186, 123),
         (173, 86), (157, 53)],
        fill=(13, 10, 31, 255),
        outline=(111, 54, 196, 255),
    )
    draw.polygon([(128, 42), (104, 65), (94, 120), (80, 196), (128, 229)],
                 fill=(26, 16, 57, 255), outline=(73, 34, 123, 255))
    draw.polygon([(128, 42), (152, 65), (162, 120), (176, 196), (128, 229)],
                 fill=(20, 14, 46, 255), outline=(73, 34, 123, 255))
    draw.line((128, 43, 128, 229), fill=(35, 213, 242, 230), width=4)
    draw.line((128, 47, 106, 90, 113, 142, 92, 196),
              fill=(186, 48, 255, 235), width=3, joint="curve")
    draw.line((128, 47, 150, 90, 143, 142, 164, 196),
              fill=(186, 48, 255, 235), width=3, joint="curve")
    draw.line((79, 199, 128, 230, 177, 199), fill=(57, 226, 255, 200), width=3)

    # Crystal clasp: cyan core, violet shell, and a small white-hot glint.
    draw.polygon([(128, 26), (151, 51), (128, 77), (105, 51)],
                 fill=(42, 19, 91, 255), outline=(204, 74, 255, 255))
    draw.polygon([(128, 32), (141, 51), (128, 68), (115, 51)],
                 fill=(22, 192, 225, 255), outline=(151, 247, 255, 255))
    draw.line((128, 35, 128, 64), fill=(224, 255, 255, 255), width=2)

    # Four deliberate hem shards communicate the End Rift identity without
    # adding noise to the cloth surface.
    for points in (
        [(61, 207), (76, 214), (69, 238)],
        [(91, 220), (105, 228), (98, 247)],
        [(195, 207), (180, 214), (187, 238)],
        [(165, 220), (151, 228), (158, 247)],
    ):
        draw.polygon(points, fill=(39, 22, 93, 255), outline=(160, 57, 244, 240))

    return image


def main() -> None:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    make_night_cloak().save(OUT, format="PNG", optimize=True)
    print(f"generated {OUT} ({SIZE}x{SIZE})")


if __name__ == "__main__":
    main()
