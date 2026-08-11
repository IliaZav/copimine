from __future__ import annotations

import json
from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parent
MODEL_DIR = ROOT / "src" / "assets" / "copimine" / "models" / "item" / "artifacts"
TEXTURE_DIR = ROOT / "src" / "assets" / "copimine" / "textures" / "item" / "artifacts"
LOGICAL_SIZE = 16
OUTPUT_SIZE = 32


def canvas() -> tuple[Image.Image, ImageDraw.ImageDraw]:
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    return image, ImageDraw.Draw(image)


def rect(draw: ImageDraw.ImageDraw, xy: tuple[int, int, int, int], color: tuple[int, int, int, int]) -> None:
    draw.rectangle(xy, fill=color)


def poly(draw: ImageDraw.ImageDraw, points: list[tuple[int, int]], color: tuple[int, int, int, int]) -> None:
    draw.polygon(points, fill=color)


def line(draw: ImageDraw.ImageDraw, points: list[tuple[int, int]], color: tuple[int, int, int, int], width: int = 1) -> None:
    draw.line(points, fill=color, width=width, joint="curve")


def save_texture(name: str, image: Image.Image) -> None:
    # Render on a compact pixel grid, then upscale without interpolation so
    # the exported pack remains crisp in first-person hand models.
    if image.width == LOGICAL_SIZE:
        image = image.resize((OUTPUT_SIZE, image.height * 2), Image.Resampling.NEAREST)
    image.save(TEXTURE_DIR / f"{name}.png", format="PNG", optimize=False)


def save_animation_texture(name: str, frames: list[Image.Image], frametime: int = 3) -> None:
    if not frames:
        raise ValueError("at least one animation frame is required")
    atlas = Image.new("RGBA", (LOGICAL_SIZE, LOGICAL_SIZE * len(frames)), (0, 0, 0, 0))
    for index, frame in enumerate(frames):
        atlas.paste(frame, (0, LOGICAL_SIZE * index))
    save_texture(name, atlas)
    metadata_path = TEXTURE_DIR / f"{name}.png.mcmeta"
    metadata_path.write_text(
        json.dumps({"animation": {"frametime": frametime, "interpolate": False}}, indent=2) + "\n",
        encoding="utf-8",
        newline="\n",
    )


def save_model(name: str, texture: str, parent: str = "minecraft:item/generated") -> None:
    payload = {
        "parent": parent,
        "textures": {"layer0": f"copimine:item/artifacts/{texture}"},
    }
    (MODEL_DIR / f"{name}.json").write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")


def draw_repair_kit() -> Image.Image:
    image, draw = canvas()
    outline = (52, 16, 20, 255)
    dark_red = (112, 25, 20, 255)
    red = (190, 42, 24, 255)
    orange = (236, 84, 24, 255)
    brass = (224, 165, 42, 255)
    steel = (220, 226, 220, 255)
    steel_shadow = (126, 139, 139, 255)
    poly(draw, [(2, 5), (12, 5), (14, 7), (14, 13), (12, 15), (3, 15), (1, 13), (1, 7)], outline)
    poly(draw, [(3, 6), (12, 6), (13, 8), (13, 13), (11, 14), (4, 14), (2, 12), (2, 8)], red)
    rect(draw, (3, 6, 11, 8), orange)
    rect(draw, (4, 9, 11, 13), (154, 30, 22, 255))
    rect(draw, (6, 10, 9, 12), brass)
    rect(draw, (7, 10, 8, 11), (102, 65, 25, 255))
    rect(draw, (3, 13, 4, 14), orange)
    rect(draw, (11, 13, 12, 14), orange)
    line(draw, [(4, 7), (6, 5), (10, 5)], dark_red, 1)
    line(draw, [(6, 7), (7, 6), (8, 7), (9, 6), (10, 6)], steel_shadow, 2)
    line(draw, [(6, 7), (7, 7), (8, 8), (10, 8), (11, 7)], steel, 1)
    rect(draw, (10, 5, 11, 6), steel)
    rect(draw, (11, 4, 12, 5), steel_shadow)
    return image


def draw_return_stone() -> Image.Image:
    image, draw = canvas()
    outline = (8, 9, 20, 255)
    black = (16, 17, 34, 255)
    slate = (38, 44, 66, 255)
    slate_light = (67, 75, 104, 255)
    blue = (36, 122, 174, 255)
    cyan = (74, 222, 231, 255)
    poly(draw, [(4, 1), (10, 1), (14, 5), (13, 12), (9, 15), (4, 14), (1, 10), (2, 4)], outline)
    poly(draw, [(4, 3), (9, 2), (12, 5), (11, 11), (8, 13), (4, 12), (3, 9), (3, 5)], slate)
    rect(draw, (5, 3, 8, 5), slate_light)
    rect(draw, (9, 4, 11, 6), black)
    rect(draw, (3, 9, 5, 11), black)
    line(draw, [(10, 3), (9, 5), (8, 6), (8, 8), (6, 9), (6, 12)], cyan, 1)
    rect(draw, (9, 4, 10, 5), cyan)
    rect(draw, (7, 7, 8, 8), cyan)
    rect(draw, (5, 9, 6, 10), blue)
    line(draw, [(3, 6), (4, 7), (4, 9)], blue, 1)
    rect(draw, (12, 8, 12, 10), slate_light)
    return image


def draw_infinite_torch() -> Image.Image:
    image, draw = canvas()
    dark = (49, 26, 16, 255)
    wood = (133, 76, 34, 255)
    wood_light = (186, 111, 50, 255)
    orange = (236, 86, 20, 255)
    gold = (255, 177, 27, 255)
    yellow = (255, 232, 72, 255)
    cyan = (47, 218, 226, 255)
    rect(draw, (6, 8, 9, 15), dark)
    rect(draw, (7, 8, 8, 14), wood)
    rect(draw, (8, 10, 9, 13), wood_light)
    poly(draw, [(6, 8), (5, 6), (5, 4), (6, 4), (6, 2), (8, 1), (10, 3), (10, 5), (9, 8)], orange)
    poly(draw, [(7, 8), (6, 6), (7, 4), (8, 3), (9, 5), (9, 7)], gold)
    rect(draw, (8, 5, 8, 6), cyan)
    rect(draw, (7, 6, 8, 7), yellow)
    rect(draw, (6, 8, 9, 8), dark)
    return image


def draw_arrow(
    draw: ImageDraw.ImageDraw,
    start: tuple[int, int],
    end: tuple[int, int],
    shaft: tuple[int, int, int, int],
    head: tuple[int, int, int, int],
) -> None:
    """Draw a readable vanilla-style arrow over a bow pull frame."""
    line(draw, [start, end], shaft, 1)
    ex, ey = end
    line(draw, [(ex - 2, ey - 1), (ex, ey), (ex - 2, ey + 1)], head, 1)
    rect(draw, (start[0], start[1] - 1, start[0] + 1, start[1]), head)


def draw_bow(kind: str, stage: int) -> Image.Image:
    image, draw = canvas()
    outline = (24, 16, 14, 255)
    wood = (114, 65, 30, 255)
    wood_light = (177, 111, 49, 255)
    grip = (83, 48, 37, 255)
    string = (210, 213, 196, 255)
    if kind == "teleport":
        accent = (91, 65, 163, 255)
        glow = (46, 218, 220, 255)
    else:
        accent = (112, 111, 106, 255)
        glow = (171, 171, 160, 255)

    outer = [(3, 14), (2, 12), (2, 9), (3, 6), (5, 4), (8, 2), (11, 2), (13, 4)]
    line(draw, outer, outline, 3)
    line(draw, outer, wood, 1)
    line(draw, [(4, 13), (4, 10), (5, 7), (7, 5), (9, 3), (11, 3), (12, 4)], wood_light, 1)
    rect(draw, (4, 9, 6, 12), grip)
    rect(draw, (5, 9, 6, 11), accent)
    if kind == "trail":
        rect(draw, (3, 8, 4, 9), accent)
        rect(draw, (6, 8, 7, 9), accent)
        rect(draw, (4, 12, 5, 13), accent)
        rect(draw, (10, 2, 11, 3), accent)
    else:
        rect(draw, (4, 7, 4, 8), glow)
        rect(draw, (8, 3, 8, 4), glow)
        rect(draw, (10, 3, 10, 3), glow)

    string_mid = [(6, 8), (7, 8), (8, 8), (9, 8)][stage]
    line(draw, [(12, 4), string_mid, (4, 13)], string if stage < 2 else glow, 1)
    arrow_shaft = (58, 48, 44, 255)
    arrow_head = glow if kind == "teleport" else (224, 224, 208, 255)
    draw_arrow(draw, (4, 8), (12, 8), arrow_shaft, arrow_head)
    if stage == 2:
        rect(draw, (10, 6, 11, 7), glow)
    return image


def draw_explosive_crossbow(stage: str, frame: int = 0) -> Image.Image:
    image, draw = canvas()
    outline = (18, 18, 21, 255)
    iron = (92, 99, 105, 255)
    iron_light = (158, 166, 166, 255)
    wood = (109, 61, 28, 255)
    wood_light = (161, 96, 39, 255)
    red = (185, 35, 26, 255)
    white = (226, 220, 188, 255)
    orange = (248, 118, 22, 255)

    rect(draw, (5, 6, 10, 10), outline)
    rect(draw, (6, 7, 9, 9), iron)
    rect(draw, (7, 10, 8, 14), outline)
    rect(draw, (7, 11, 8, 14), wood)
    rect(draw, (8, 12, 9, 13), wood_light)
    line(draw, [(3, 4), (6, 7), (7, 8), (6, 9), (3, 12)], outline, 3)
    line(draw, [(3, 4), (6, 7), (7, 8), (6, 9), (3, 12)], iron_light, 1)
    line(draw, [(12, 4), (9, 7), (8, 8), (9, 9), (12, 12)], outline, 3)
    line(draw, [(12, 4), (9, 7), (8, 8), (9, 9), (12, 12)], iron_light, 1)

    string_y = {"base": 6, "pulling_0": 7, "pulling_1": 8, "pulling_2": 9}.get(stage, 8)
    line(draw, [(3, 4), (13, 4)], iron, 1)
    line(draw, [(3, 12), (13, 12)], iron, 1)
    line(draw, [(3, 4), (3, string_y), (13, string_y), (13, 4)], iron_light, 1)
    if stage in {"charged", "charged_firework"}:
        line(draw, [(2, 8), (13, 8)], iron_light, 1)
    if stage in {"base", "pulling_0", "pulling_1", "pulling_2"}:
        rect(draw, (6, 5, 9, 7), red)
        rect(draw, (7, 6, 8, 6), white)
        rect(draw, (6, 4, 6, 5), orange)
    elif stage == "charged":
        # The charged texture is exported as an atlas. Move the TNT vertically
        # by one logical pixel per frame so the fuse visibly flickers while the
        # crossbow remains symmetric in the player's hand.
        tnt_y = 7 + ((frame % 4) - 1 if frame % 4 in (0, 2) else 0)
        rect(draw, (5, tnt_y, 10, tnt_y + 2), red)
        rect(draw, (6, tnt_y, 9, tnt_y + 1), white)
        rect(draw, (5, tnt_y, 6, tnt_y + 1), orange)
    else:
        rect(draw, (6, 6, 9, 10), red)
        rect(draw, (7, 7, 8, 9), white)
        rect(draw, (7, 5, 7, 6), orange)
        rect(draw, (7, 4, 7, 4), orange)

    # Keep every state exactly mirrored around the two center pixels.  This
    # avoids a one-pixel asymmetry as the item switches between pull frames.
    for x in range(8):
        for y in range(16):
            image.putpixel((15 - x, y), image.getpixel((x, y)))
    return image


def draw_recipe_note() -> Image.Image:
    image, draw = canvas()
    outline = (42, 25, 20, 255)
    cover = (91, 51, 31, 255)
    cover_light = (138, 83, 43, 255)
    page = (224, 209, 164, 255)
    page_shadow = (166, 143, 99, 255)
    feather = (45, 147, 145, 255)
    feather_light = (89, 200, 182, 255)
    poly(draw, [(2, 4), (10, 3), (13, 6), (12, 14), (4, 15), (1, 11)], outline)
    poly(draw, [(3, 5), (10, 4), (12, 6), (11, 13), (4, 14), (2, 11)], cover)
    line(draw, [(4, 5), (10, 5), (11, 7)], cover_light, 1)
    poly(draw, [(4, 9), (10, 8), (11, 12), (5, 13)], page)
    line(draw, [(5, 11), (10, 10)], page_shadow, 1)
    line(draw, [(5, 13), (11, 12)], page_shadow, 1)
    line(draw, [(9, 11), (12, 4)], feather, 2)
    poly(draw, [(10, 7), (11, 3), (13, 2), (12, 6)], feather)
    poly(draw, [(11, 5), (12, 2), (14, 1), (13, 5)], feather_light)
    rect(draw, (8, 11, 9, 12), page_shadow)
    return image


def main() -> None:
    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    TEXTURE_DIR.mkdir(parents=True, exist_ok=True)

    for name, image in {
        "repair_kit": draw_repair_kit(),
        "return_stone": draw_return_stone(),
        "infinite_torch": draw_infinite_torch(),
        "recipe_note": draw_recipe_note(),
    }.items():
        save_texture(name, image)
        save_model(name, name)

    for kind, prefix in (("teleport", "teleport_bow"), ("trail", "cobblestone_trail_bow")):
        for stage in range(4):
            suffix = "" if stage == 0 else f"_pulling_{stage - 1}"
            name = f"{prefix}{suffix}"
            save_texture(name, draw_bow(kind, stage))
            parent = "minecraft:item/bow" if stage == 0 else f"minecraft:item/bow_pulling_{stage - 1}"
            save_model(name, name, parent)

    for stage in ("base", "pulling_0", "pulling_1", "pulling_2", "charged_firework"):
        suffix = "" if stage == "base" else f"_{stage}"
        name = f"explosive_crossbow{suffix}"
        save_texture(name, draw_explosive_crossbow(stage))
        if stage == "base":
            parent = "minecraft:item/crossbow"
        elif stage.startswith("pulling"):
            parent = f"minecraft:item/crossbow_{stage}"
        else:
            parent = "minecraft:item/crossbow_firework"
        save_model(name, name, parent)

    save_animation_texture(
        "explosive_crossbow_charged",
        [draw_explosive_crossbow("charged", frame) for frame in range(4)],
        frametime=3,
    )
    save_model("explosive_crossbow_charged", "explosive_crossbow_charged", "minecraft:item/crossbow_arrow")


if __name__ == "__main__":
    main()
