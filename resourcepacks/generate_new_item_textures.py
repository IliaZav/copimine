from __future__ import annotations

import json
from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parent
MODEL_DIR = ROOT / "src" / "assets" / "copimine" / "models" / "item" / "artifacts"
TEXTURE_DIR = ROOT / "src" / "assets" / "copimine" / "textures" / "item" / "artifacts"
REFERENCE_TEXTURE_DIR = ROOT / "reference" / "vanilla" / "item"
LOGICAL_SIZE = 16
OUTPUT_SIZE = 32

# The pull textures contain the vanilla three-pixel projectile at these
# coordinates. The diagonal grey pixels outside this set are the bow string
# and must be retained. Keeping the projectile coordinates explicit prevents
# a broad "remove grey" rule from damaging the animation geometry while still
# letting each custom bow draw its own arrow over the vanilla pose.
BOW_PROJECTILE_COORDS: dict[str, set[tuple[int, int]]] = {
    "bow_pulling_0.png": {(1, 0), (1, 1), (2, 1)},
    "bow_pulling_1.png": {(2, 1), (2, 2), (3, 2)},
    "bow_pulling_2.png": {(3, 2), (3, 3), (4, 3)},
}
CROSSBOW_PROJECTILE_REFERENCE = "crossbow_arrow.png"

PROJECTILE_PALETTES = {
    "teleport": {
        "tip": (190, 255, 255, 255),
        "shaft": (58, 211, 233, 255),
        "fletching": (27, 103, 178, 255),
    },
    "trail": {
        "tip": (242, 239, 215, 255),
        "shaft": (153, 161, 151, 255),
        "fletching": (70, 78, 80, 255),
    },
    "explosive": {
        "tip": (255, 218, 132, 255),
        "shaft": (226, 91, 53, 255),
        "fletching": (100, 29, 37, 255),
    },
}

RECOLOR_PALETTES = {
    "teleport": {
        "dark": (26, 15, 69, 255),
        "shadow": (51, 28, 119, 255),
        "base": (95, 56, 180, 255),
        "light": (151, 106, 230, 255),
        "highlight": (205, 182, 255, 255),
        "metal_dark": (20, 49, 72, 255),
        "metal": (37, 117, 145, 255),
        "metal_light": (82, 203, 215, 255),
        "metal_highlight": (190, 255, 255, 255),
    },
    "trail": {
        "dark": (25, 29, 25, 255),
        "shadow": (54, 67, 60, 255),
        "base": (94, 108, 100, 255),
        "light": (153, 164, 151, 255),
        "highlight": (208, 213, 192, 255),
        "metal_dark": (40, 44, 46, 255),
        "metal": (92, 101, 103, 255),
        "metal_light": (166, 176, 171, 255),
        "metal_highlight": (230, 236, 224, 255),
    },
    "explosive": {
        "dark": (35, 16, 25, 255),
        "shadow": (82, 31, 35, 255),
        "base": (151, 45, 42, 255),
        "light": (210, 92, 54, 255),
        "highlight": (255, 180, 90, 255),
        "metal_dark": (35, 39, 52, 255),
        "metal": (84, 97, 125, 255),
        "metal_light": (157, 180, 213, 255),
        "metal_highlight": (231, 244, 255, 255),
    },
}


def load_vanilla_texture(name: str) -> Image.Image:
    path = REFERENCE_TEXTURE_DIR / name
    if not path.is_file():
        raise FileNotFoundError(f"missing vanilla reference texture: {path}")
    with Image.open(path) as source:
        return source.convert("RGBA").copy()


def _recolor_pixel(pixel: tuple[int, int, int, int], palette: dict[str, tuple[int, int, int, int]]) -> tuple[int, int, int, int]:
    red, green, blue, alpha = pixel
    if alpha == 0:
        return (0, 0, 0, 0)

    luminance = (red * 299 + green * 587 + blue * 114) // 1000
    if max(red, green, blue) - min(red, green, blue) <= 8:
        keys = ("metal_dark", "metal", "metal_light", "metal_highlight")
    else:
        keys = ("dark", "shadow", "base", "light", "highlight")
    if luminance < 45:
        key = keys[0]
    elif luminance < 90:
        key = keys[1]
    elif luminance < 145:
        key = keys[2]
    elif luminance < 200:
        key = keys[3]
    else:
        key = keys[-1]
    recolored = palette[key]
    return (recolored[0], recolored[1], recolored[2], alpha)


def recolor_vanilla_texture(
    name: str,
    palette: dict[str, tuple[int, int, int, int]],
    remove_coords: set[tuple[int, int]] | frozenset[tuple[int, int]] = frozenset(),
) -> Image.Image:
    image = load_vanilla_texture(name)
    for y in range(image.height):
        for x in range(image.width):
            if (x, y) in remove_coords:
                image.putpixel((x, y), (0, 0, 0, 0))
            else:
                image.putpixel((x, y), _recolor_pixel(image.getpixel((x, y)), palette))
    return image


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


def draw_custom_bow_projectile(image: Image.Image, kind: str, source_name: str) -> None:
    colors = PROJECTILE_PALETTES[kind]
    ordered_coords = sorted(BOW_PROJECTILE_COORDS.get(source_name, set()))
    for index, (x, y) in enumerate(ordered_coords):
        role = ("tip", "shaft", "fletching")[min(index, 2)]
        image.putpixel((x, y), colors[role])


def draw_bow(kind: str, stage: int) -> Image.Image:
    source_name = "bow.png" if stage == 0 else f"bow_pulling_{stage - 1}.png"
    image = recolor_vanilla_texture(source_name, RECOLOR_PALETTES[kind])
    if stage > 0:
        draw_custom_bow_projectile(image, kind, source_name)
    return image


def draw_charged_explosive_crossbow(frame: int = 0) -> Image.Image:
    """Overlay the TNT accent on a recolored vanilla crossbow with its bolt."""
    image = recolor_vanilla_texture(CROSSBOW_PROJECTILE_REFERENCE, RECOLOR_PALETTES["explosive"])
    draw = ImageDraw.Draw(image)
    red = (185, 35, 26, 255)
    white = (226, 220, 188, 255)
    orange = (248, 118, 22, 255)

    # Keep the TNT animation, but never draw a vanilla/projectile arrow into
    # the custom charged texture.
    tnt_y = 7 + ((frame % 4) - 1 if frame % 4 in (0, 2) else 0)
    rect(draw, (5, tnt_y, 10, tnt_y + 2), red)
    rect(draw, (6, tnt_y, 9, tnt_y + 1), white)
    rect(draw, (5, tnt_y, 6, tnt_y + 1), orange)
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


def draw_crossbow(kind: str, stage: str) -> Image.Image:
    """Recolor the matching vanilla crossbow state without changing its shape."""
    source_name = "crossbow_standby.png" if stage == "base" else f"crossbow_{stage}.png"
    return recolor_vanilla_texture(source_name, RECOLOR_PALETTES[kind])


def draw_charged_teleport_crossbow() -> Image.Image:
    """Use the vanilla loaded-bolt silhouette with the teleport palette."""
    return recolor_vanilla_texture(CROSSBOW_PROJECTILE_REFERENCE, RECOLOR_PALETTES["teleport"])


def draw_gravedigger_contract() -> Image.Image:
    image, draw = canvas()
    outline = (39, 24, 18, 255)
    parchment = (221, 190, 126, 255)
    parchment_light = (247, 220, 161, 255)
    parchment_shadow = (151, 111, 65, 255)
    ink = (67, 43, 29, 255)
    shovel = (92, 99, 104, 255)
    shovel_dark = (42, 49, 54, 255)
    poly(draw, [(3, 1), (12, 2), (14, 5), (13, 14), (4, 15), (1, 11), (2, 4)], outline)
    poly(draw, [(4, 2), (11, 3), (13, 5), (12, 13), (4, 14), (2, 10), (3, 4)], parchment)
    poly(draw, [(10, 2), (13, 5), (10, 5)], parchment_light)
    line(draw, [(4, 7), (10, 7)], parchment_shadow, 1)
    line(draw, [(4, 9), (9, 9)], parchment_shadow, 1)
    line(draw, [(4, 11), (8, 11)], parchment_shadow, 1)
    line(draw, [(8, 12), (11, 5)], shovel_dark, 2)
    poly(draw, [(9, 4), (12, 4), (13, 6), (12, 7), (10, 6)], shovel)
    rect(draw, (7, 12, 9, 13), ink)
    return image


def draw_signal_bell() -> Image.Image:
    image, draw = canvas()
    outline = (45, 29, 12, 255)
    dark = (108, 62, 18, 255)
    brass = (190, 126, 28, 255)
    gold = (241, 190, 56, 255)
    highlight = (255, 225, 112, 255)
    rect(draw, (5, 2, 10, 3), outline)
    rect(draw, (6, 1, 9, 2), brass)
    rect(draw, (4, 4, 11, 11), outline)
    poly(draw, [(5, 4), (10, 4), (11, 9), (13, 12), (2, 12), (4, 9)], brass)
    rect(draw, (5, 5, 9, 9), gold)
    rect(draw, (6, 5, 7, 8), highlight)
    rect(draw, (3, 11, 12, 13), dark)
    rect(draw, (4, 11, 11, 12), gold)
    rect(draw, (7, 12, 8, 15), outline)
    rect(draw, (6, 14, 9, 15), brass)
    return image


def draw_berserker_heart() -> Image.Image:
    image, draw = canvas()
    glow = (111, 16, 25, 255)
    outline = (45, 8, 15, 255)
    red_dark = (126, 17, 28, 255)
    red = (211, 34, 47, 255)
    red_light = (255, 82, 70, 255)
    white = (255, 191, 133, 255)
    rect(draw, (4, 2, 6, 3), glow)
    rect(draw, (9, 2, 11, 3), glow)
    rect(draw, (2, 4, 4, 6), glow)
    rect(draw, (11, 4, 13, 6), glow)
    poly(draw, [(3, 4), (6, 3), (8, 5), (10, 3), (13, 4), (13, 9), (8, 14), (3, 9)], outline)
    poly(draw, [(4, 5), (6, 4), (8, 6), (10, 4), (12, 5), (12, 8), (8, 12), (4, 8)], red_dark)
    poly(draw, [(5, 5), (7, 5), (8, 7), (9, 5), (11, 5), (11, 8), (8, 11), (5, 8)], red)
    rect(draw, (6, 5, 7, 6), red_light)
    rect(draw, (9, 6, 10, 7), red_light)
    rect(draw, (7, 7, 8, 8), white)
    return image


def draw_zhilorez_pickaxe() -> Image.Image:
    image, draw = canvas()
    outline = (21, 21, 35, 255)
    handle_dark = (66, 37, 24, 255)
    handle = (145, 83, 42, 255)
    metal_dark = (45, 37, 77, 255)
    metal = (104, 77, 166, 255)
    metal_light = (181, 153, 238, 255)
    poly(draw, [(2, 2), (5, 1), (12, 3), (14, 6), (13, 8), (9, 6), (6, 5), (4, 7), (2, 6)], outline)
    poly(draw, [(3, 3), (5, 2), (11, 4), (13, 6), (12, 7), (8, 5), (5, 4), (4, 6), (3, 5)], metal)
    rect(draw, (5, 3, 6, 4), metal_light)
    line(draw, [(8, 6), (5, 11), (3, 14)], outline, 3)
    line(draw, [(8, 6), (5, 11), (3, 14)], handle, 2)
    rect(draw, (2, 13, 4, 15), handle_dark)
    rect(draw, (3, 13, 4, 14), handle)
    return image


def draw_night_cloak() -> Image.Image:
    image, draw = canvas()
    outline = (14, 16, 42, 255)
    blue_dark = (26, 31, 78, 255)
    blue = (46, 63, 132, 255)
    blue_light = (79, 102, 194, 255)
    violet = (117, 90, 198, 255)
    poly(draw, [(5, 2), (10, 2), (12, 5), (14, 14), (10, 15), (8, 12), (6, 15), (2, 14), (4, 5)], outline)
    poly(draw, [(6, 3), (9, 3), (11, 6), (12, 13), (9, 14), (8, 10), (6, 14), (3, 13), (5, 6)], blue_dark)
    poly(draw, [(6, 4), (8, 4), (9, 7), (8, 12), (6, 13), (5, 7)], blue)
    poly(draw, [(9, 4), (10, 6), (11, 12), (9, 13), (8, 9)], blue_light)
    rect(draw, (7, 3, 8, 5), violet)
    rect(draw, (4, 10, 5, 12), violet)
    rect(draw, (11, 9, 12, 11), violet)
    return image


def main() -> None:
    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    TEXTURE_DIR.mkdir(parents=True, exist_ok=True)

    for name, image in {
        "repair_kit": draw_repair_kit(),
        "return_stone": draw_return_stone(),
        "infinite_torch": draw_infinite_torch(),
        "recipe_note": draw_recipe_note(),
        "gravedigger_contract": draw_gravedigger_contract(),
        "signal_bell": draw_signal_bell(),
        "berserker_heart": draw_berserker_heart(),
        "zhilorez_pickaxe": draw_zhilorez_pickaxe(),
        "night_cloak": draw_night_cloak(),
    }.items():
        save_texture(name, image)
        save_model(name, name)

    for stage in ("base", "pulling_0", "pulling_1", "pulling_2"):
        suffix = "" if stage == "base" else f"_{stage}"
        name = f"teleport_bow{suffix}"
        save_texture(name, draw_crossbow("teleport", stage))
        parent = "minecraft:item/crossbow" if stage == "base" else f"minecraft:item/crossbow_{stage}"
        save_model(name, name, parent)

    for stage in ("charged", "charged_firework"):
        name = f"teleport_bow_{stage}"
        save_texture(name, draw_charged_teleport_crossbow())
        parent = "minecraft:item/crossbow_arrow" if stage == "charged" else "minecraft:item/crossbow_firework"
        save_model(name, name, parent)

    for kind, prefix in (("trail", "cobblestone_trail_bow"),):
        for stage in range(4):
            suffix = "" if stage == 0 else f"_pulling_{stage - 1}"
            name = f"{prefix}{suffix}"
            save_texture(name, draw_bow(kind, stage))
            parent = "minecraft:item/bow" if stage == 0 else f"minecraft:item/bow_pulling_{stage - 1}"
            save_model(name, name, parent)

    for stage in ("base", "pulling_0", "pulling_1", "pulling_2", "charged_firework"):
        suffix = "" if stage == "base" else f"_{stage}"
        name = f"explosive_crossbow{suffix}"
        if stage == "base":
            image = recolor_vanilla_texture("crossbow_standby.png", RECOLOR_PALETTES["explosive"])
        elif stage.startswith("pulling"):
            image = recolor_vanilla_texture(f"crossbow_{stage}.png", RECOLOR_PALETTES["explosive"])
        else:
            image = draw_charged_explosive_crossbow()
        save_texture(name, image)
        if stage == "base":
            parent = "minecraft:item/crossbow"
        elif stage.startswith("pulling"):
            parent = f"minecraft:item/crossbow_{stage}"
        else:
            parent = "minecraft:item/crossbow_firework"
        save_model(name, name, parent)

    save_animation_texture(
        "explosive_crossbow_charged",
        [draw_charged_explosive_crossbow(frame) for frame in range(4)],
        frametime=3,
    )
    save_model("explosive_crossbow_charged", "explosive_crossbow_charged", "minecraft:item/crossbow_arrow")


if __name__ == "__main__":
    main()
