from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parent
SERVER_TEXTURES = ROOT / "src" / "assets" / "copimine" / "textures" / "item"
CLIENT_TEXTURES = (
    ROOT.parent
    / "CopiMineClient"
    / "src"
    / "main"
    / "resources"
    / "assets"
    / "copimineclient"
    / "textures"
    / "entity"
)
SIZE = 32


def mirrored_line(draw: ImageDraw.ImageDraw, points: list[tuple[int, int]], color: tuple[int, int, int, int], width: int = 1) -> None:
    transforms = (
        lambda x, y: (x, y),
        lambda x, y: (31 - x, y),
        lambda x, y: (x, 31 - y),
        lambda x, y: (31 - x, 31 - y),
    )
    for transform in transforms:
        draw.line([transform(x, y) for x, y in points], fill=color, width=width, joint="curve")


def mirrored_rect(draw: ImageDraw.ImageDraw, box: tuple[int, int, int, int], color: tuple[int, int, int, int]) -> None:
    x1, y1, x2, y2 = box
    for left, top, right, bottom in (
        (x1, y1, x2, y2),
        (31 - x2, y1, 31 - x1, y2),
        (x1, 31 - y2, x2, 31 - y1),
        (31 - x2, 31 - y2, 31 - x1, 31 - y1),
    ):
        draw.rectangle((left, top, right, bottom), fill=color)


def obelisk_texture(variant: str) -> Image.Image:
    palettes = {
        "full": {
            "base": (10, 6, 28, 255),
            "base2": (19, 8, 45, 255),
            "frame": (38, 11, 70, 255),
            "magenta": (236, 24, 221, 255),
            "purple": (126, 31, 226, 255),
            "cyan": (37, 225, 255, 255),
            "cyan_dark": (20, 116, 177, 255),
            "white": (188, 255, 255, 255),
        },
        "damaged": {
            "base": (8, 5, 21, 255),
            "base2": (20, 8, 36, 255),
            "frame": (45, 12, 61, 255),
            "magenta": (185, 19, 169, 255),
            "purple": (92, 24, 160, 255),
            "cyan": (28, 165, 190, 255),
            "cyan_dark": (22, 80, 113, 255),
            "white": (131, 224, 230, 255),
        },
        "critical": {
            "base": (13, 4, 20, 255),
            "base2": (38, 7, 38, 255),
            "frame": (63, 11, 65, 255),
            "magenta": (255, 33, 184, 255),
            "purple": (157, 24, 196, 255),
            "cyan": (47, 235, 243, 255),
            "cyan_dark": (25, 116, 135, 255),
            "white": (217, 255, 235, 255),
        },
    }[variant]
    image = Image.new("RGBA", (SIZE, SIZE), palettes["base"])
    draw = ImageDraw.Draw(image)

    # A four-way mirrored microtexture keeps the tile symmetric while still
    # giving the obelisk a layered obsidian surface instead of a flat icon.
    for y in range(16):
        for x in range(16):
            distance = min(x, y, 15 - x, 15 - y)
            if distance <= 1:
                color = palettes["frame"]
            elif (x + y * 3) % 7 == 0:
                color = palettes["base2"]
            else:
                color = palettes["base"]
            for px, py in ((x, y), (31 - x, y), (x, 31 - y), (31 - x, 31 - y)):
                image.putpixel((px, py), color)

    # Obsidian frame and inset face.
    draw.rectangle((1, 1, 30, 30), outline=palettes["frame"], width=2)
    draw.rectangle((4, 4, 27, 27), outline=palettes["purple"], width=1)
    mirrored_rect(draw, (5, 5, 7, 7), palettes["base2"])
    mirrored_rect(draw, (24, 5, 26, 7), palettes["base2"])

    # The central rift seal is four-way symmetric and reads clearly at small
    # in-game scale.  Damaged states dim the seal and expose more fractures.
    draw.polygon([(15, 5), (26, 16), (15, 26), (5, 16)], fill=palettes["purple"])
    draw.polygon([(15, 8), (23, 16), (15, 23), (8, 16)], fill=palettes["base"])
    draw.line([(15, 7), (24, 16), (15, 25), (6, 16), (15, 7)], fill=palettes["cyan"], width=2, joint="curve")
    draw.rectangle((13, 13, 17, 17), fill=palettes["magenta"])
    draw.rectangle((14, 14, 16, 16), fill=palettes["cyan"])
    draw.point((15, 15), fill=palettes["white"])

    mirrored_line(draw, [(7, 10), (10, 12), (11, 15)], palettes["cyan"], 1)
    mirrored_line(draw, [(6, 22), (10, 20), (11, 17)], palettes["magenta"], 1)
    mirrored_line(draw, [(4, 14), (8, 13)], palettes["purple"], 2)
    mirrored_line(draw, [(20, 5), (20, 9), (18, 11)], palettes["cyan_dark"], 1)

    if variant == "damaged":
        # Symmetric dark breaks make the second state visibly different while
        # retaining the same silhouette and model mapping.
        mirrored_rect(draw, (8, 8, 9, 10), palettes["base"])
        mirrored_rect(draw, (21, 21, 22, 23), palettes["base"])
        mirrored_line(draw, [(5, 25), (9, 22), (11, 21)], palettes["magenta"], 2)
    elif variant == "critical":
        mirrored_rect(draw, (7, 7, 9, 9), palettes["base"])
        mirrored_rect(draw, (22, 22, 24, 24), palettes["base"])
        mirrored_line(draw, [(4, 8), (8, 10), (10, 13)], palettes["magenta"], 2)
        mirrored_line(draw, [(4, 24), (8, 22), (10, 19)], palettes["cyan"], 1)

    # Normalize every pixel from the upper-left quadrant.  This final pass is
    # intentional: resource-pack tiles are rendered on all four faces, so a
    # perfectly mirrored state stays symmetrical from every approach.
    for y in range(16):
        for x in range(16):
            color = image.getpixel((x, y))
            for px, py in ((x, y), (31 - x, y), (x, 31 - y), (31 - x, 31 - y)):
                image.putpixel((px, py), color)

    return image


def fireball_texture() -> Image.Image:
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.polygon([(15, 3), (22, 7), (28, 15), (24, 24), (15, 29), (7, 24), (3, 15), (9, 7)],
                 fill=(38, 6, 69, 190))
    draw.polygon([(15, 6), (21, 9), (25, 15), (21, 22), (15, 26), (10, 22), (6, 15), (10, 9)],
                 fill=(119, 16, 185, 235))
    draw.polygon([(15, 9), (20, 12), (22, 16), (19, 21), (15, 23), (11, 20), (9, 16), (11, 12)],
                 fill=(13, 29, 55, 255))
    draw.polygon([(15, 11), (19, 14), (20, 17), (17, 20), (13, 20), (11, 16), (13, 13)],
                 fill=(36, 223, 255, 255))
    draw.rectangle((14, 14, 17, 17), fill=(198, 255, 255, 255))
    draw.point((15, 15), fill=(255, 255, 255, 255))
    mirrored_line(draw, [(7, 14), (10, 15), (11, 17)], (242, 24, 221, 255), 1)
    mirrored_line(draw, [(8, 21), (11, 20), (13, 18)], (60, 235, 255, 255), 1)
    return image


def save_pair(name: str, image: Image.Image) -> None:
    SERVER_TEXTURES.mkdir(parents=True, exist_ok=True)
    CLIENT_TEXTURES.mkdir(parents=True, exist_ok=True)
    image.save(SERVER_TEXTURES / f"{name}.png", format="PNG", optimize=False)
    image.save(CLIENT_TEXTURES / f"{name}.png", format="PNG", optimize=False)


def main() -> None:
    for variant in ("full", "damaged", "critical"):
        save_pair(f"end_event_rift_obelisk_{variant}", obelisk_texture(variant))
    save_pair("end_event_rift_fireball", fireball_texture())


if __name__ == "__main__":
    main()
