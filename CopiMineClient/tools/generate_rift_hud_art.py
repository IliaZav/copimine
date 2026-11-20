"""Export original pixel art for End Rift HUDs with Pillow."""
from pathlib import Path
from random import Random

from PIL import Image, ImageDraw

GUI = Path(__file__).resolve().parents[1] / "src/main/resources/assets/copimineclient/textures/gui"


def surface(draw, bounds, palette, seed):
    x0, y0, x1, y1 = bounds
    rng = Random(seed)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            draw.point((x, y), rng.choice(palette))


def ram_assets():
    frame = Image.new("RGBA", (268, 20))
    d = ImageDraw.Draw(frame)
    d.polygon([(0, 9), (7, 2), (261, 2), (267, 9), (261, 19), (7, 19)], fill="#100f17")
    surface(d, (8, 3, 259, 5), ["#44404f", "#393242", "#504657", "#292833"], 61)
    surface(d, (8, 15, 259, 18), ["#282431", "#342d3f", "#211d2c", "#463a4b"], 62)
    d.rectangle((8, 6, 259, 14), fill="#1d1825")
    for x in (4, 263):
        d.rectangle((x, 8, x + 1, 11), fill="#b6a7a2")
    frame.save(GUI / "rift_ram_forged_rail.png")

    chain = Image.new("RGBA", (26, 16))
    d = ImageDraw.Draw(chain)
    for x, y in [(0, 4), (7, 7), (14, 9), (20, 11)]:
        d.rectangle((x, y, min(x + 5, 25), y + 4), outline="#9b8b87")
        d.line((x, y + 4, min(x + 5, 25), y + 4), fill="#4e444d")
    chain.save(GUI / "rift_ram_chain.png")

    for broken in (False, True):
        plate = Image.new("RGBA", (16, 16))
        d = ImageDraw.Draw(plate)
        d.polygon([(3, 0), (12, 0), (15, 3), (14, 10), (8, 15), (1, 10), (0, 3)],
                  fill="#362c40" if broken else "#15131b")
        d.polygon([(3, 1), (12, 1), (14, 4), (13, 9), (8, 13), (2, 9), (1, 4)],
                  fill="#221b2a" if broken else "#716270")
        if broken:
            d.line([(5, 3), (8, 6), (7, 9), (10, 12)], fill="#100e16", width=2)
        else:
            d.polygon([(4, 3), (11, 3), (12, 7), (8, 12), (3, 7)], fill="#3c3446")
            d.polygon([(8, 4), (10, 7), (8, 10), (6, 7)], fill="#a36ab3")
            d.point((8, 6), fill="#e1b5df")
            d.line((3, 1, 12, 1), fill="#c6b4a7")
        plate.save(GUI / f"rift_ram_plate_{'spent' if broken else 'sealed'}.png")


def trial_assets(output=GUI):
    output.mkdir(parents=True, exist_ok=True)
    written = []
    styles = {
        "bone": ("marksman", "#262032", "#8c759c", "#74635c", "#ddc7aa", "#ad75e0"),
        "sculk": ("warden", "#101f29", "#234652", "#2a6070", "#568889", "#69d7c5"),
        "rune": ("archmage", "#21172e", "#433052", "#593760", "#a783a1", "#ef8fca"),
        "mirror": ("echo", "#1d2638", "#37485b", "#5d6c87", "#a9b3cb", "#c29aee"),
    }
    for index, (style, (actor, base, edge, dark, light, accent)) in enumerate(styles.items()):
        frame = Image.new("RGBA", (256, 32))
        d = ImageDraw.Draw(frame)
        d.polygon([(0, 15), (9, 7), (246, 7), (255, 15), (247, 25), (8, 25)], fill=base)
        surface(d, (13, 8, 242, 10), [edge, base, dark, edge], 701 + index)
        surface(d, (13, 22, 242, 24), [edge, base, dark, edge], 721 + index)
        for side in (0, 1):
            for j in range(6):
                xx = 8 + 3 * j if side == 0 else 247 - 3 * j
                yy = 10 + (j % 3)
                d.line([(xx, yy), (xx + (2 if side == 0 else -2), yy + 3)], fill=light)
        for x in range(32, 230, 16):
            if style == "bone":
                d.line((x, 7, x + 4, 9), fill=light)
            elif style == "sculk":
                d.line((x, 25, x + 2, 22), fill=accent)
            elif style == "rune":
                d.polygon([(x, 7), (x + 2, 5), (x + 4, 7), (x + 2, 9)], outline=accent)
            else:
                d.line([(x, 7), (x + 3, 10), (x + 7, 9)], fill=light)
        frame_path = output / f"wave7_bossbar_{style}.png"
        frame.save(frame_path)
        written.append(frame_path)

        portrait = Image.new("RGBA", (32, 32))
        d = ImageDraw.Draw(portrait)
        d.polygon([(6, 2), (25, 2), (30, 8), (30, 24), (25, 30), (6, 30), (1, 24), (1, 8)],
                  fill=base, outline=edge)
        if style == "bone":
            d.polygon([(8, 7), (24, 7), (25, 18), (20, 19), (20, 25), (11, 25), (11, 20), (7, 18)], fill=light)
            d.rectangle((9, 12, 14, 16), fill=base)
            d.rectangle((18, 12, 23, 16), fill=base)
            d.line((11, 14, 13, 14), fill=accent)
            d.line((19, 14, 21, 14), fill=accent)
            for x in (13, 16, 19):
                d.line((x, 22, x, 25), fill=dark)
        elif style == "sculk":
            d.polygon([(4, 6), (10, 10), (22, 10), (27, 4), (27, 14), (24, 17), (24, 24),
                       (8, 24), (8, 17), (4, 14)], fill=dark)
            d.line([(4, 6), (6, 12), (11, 15)], fill=accent, width=2)
            d.line([(27, 4), (26, 11), (21, 14)], fill=accent, width=2)
            for y in (17, 20, 23):
                d.line((11, y, 21, y), fill=light)
        elif style == "rune":
            d.polygon([(16, 4), (25, 13), (29, 25), (3, 25), (7, 13)], fill=dark)
            d.polygon([(16, 9), (22, 16), (21, 23), (11, 23), (10, 16)], fill=base)
            d.line((12, 17, 14, 17), fill=accent, width=2)
            d.line((18, 17, 20, 17), fill=accent, width=2)
            d.polygon([(16, 6), (18, 9), (16, 12), (14, 9)], fill=light)
        else:
            d.polygon([(6, 7), (24, 5), (26, 24), (10, 26)], fill=dark, outline=light)
            d.polygon([(11, 10), (21, 9), (22, 21), (12, 22)], fill=base)
            d.line((13, 15, 15, 15), fill=accent, width=2)
            d.line((18, 14, 20, 14), fill=accent, width=2)
            d.line([(20, 5), (17, 12), (21, 18), (17, 25)], fill=light)
        portrait_path = output / f"wave7_portrait_{actor}.png"
        portrait.resize((128, 128), Image.Resampling.NEAREST).save(portrait_path)
        written.append(portrait_path)
    return written


def spire_harbinger_skin():
    """Atlas for the editable Spire Harbinger rig in RiftEventSkeletonModel."""
    image = Image.new("RGBA", (64, 32))
    d = ImageDraw.Draw(image)
    bone = ["#a89a83", "#b7a98d", "#c5b798", "#d9c9aa", "#93826c"]
    plate = ["#26212f", "#352c40", "#493c51", "#2e2936", "#514052"]
    surface(d, (0, 0, 31, 15), bone, 264)
    surface(d, (0, 16, 15, 31), bone, 265)
    surface(d, (16, 16, 39, 31), plate, 266)
    surface(d, (40, 16, 49, 31), bone, 267)
    surface(d, (32, 0, 63, 15), plate, 268)
    surface(d, (50, 16, 63, 31), plate, 269)
    # Skull front: split cheekbones, one hot eye and recessed jaw plates.
    d.rectangle((9, 10, 11, 11), fill="#211928")
    d.rectangle((13, 10, 14, 11), fill="#211928")
    d.point((10, 10), fill="#e9a7e8")
    d.point((14, 10), fill="#aa64bb")
    d.line((10, 14, 14, 14), fill="#65564e")
    d.line([(12, 8), (12, 10), (11, 12), (12, 13)], fill="#6c597c")
    # Chest and bow seams are on separate UV islands.
    d.line([(23, 20), (25, 23), (24, 27), (28, 30)], fill="#b16ec5")
    d.line((54, 17, 54, 24), fill="#d684e0")
    d.line((61, 10, 61, 22), fill="#ddc9e0")
    d.line((53, 7, 53, 10), fill="#bc77d0")
    image.save(GUI.parent / "entity/end_rift_spire_harbinger.png")


if __name__ == "__main__":
    ram_assets()
    trial_assets()
    spire_harbinger_skin()
