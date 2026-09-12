from pathlib import Path
import json
import math
import random

from PIL import Image, ImageDraw, ImageFilter


ROOT = Path(__file__).resolve().parent
SERVER_TEXTURE = ROOT / "src/assets/copimine/textures/item/end_event_rift_tentacle_hd.png"
CLIENT_TEXTURE = ROOT.parent / "CopiMineClient/src/main/resources/assets/copimineclient/textures/entity/end_rift_tentacle_hd.png"
MODEL = ROOT / "src/assets/copimine/models/item/end_event_rift_tentacle.json"
MASTER_TEXTURE = ROOT / "art/end_rift_tentacle_atlas_master.png"
SIZE = 512
LEGACY_SIZE = 256
RIG_BONES = ("seg_01", "seg_02", "seg_03", "seg_04", "seg_05",
             "tip_claw_1", "tip_claw_2", "tip_claw_3", "tip_claw_4", "grab_socket")

# These identifiers mirror the server wire states. The JSON asset retains the
# old aliases for clients that only use the fallback ItemDisplay model, but
# every canonical current state has an explicit animation entry.
CANONICAL_STATES = (
    "ready", "emerging", "telegraph_grab", "grab_success", "hold", "throw",
    "miss_recovery", "hit_recovery", "dying", "dead_respawn", "retract",
    "spawn_under_player", "shield_channel", "recovery")


def make_texture() -> Image.Image:
    # Keep the artist-approved high-resolution atlas as the source of truth.
    # The generated runtime texture is still a power-of-two 512x512 image so
    # the client rig can use crisp UVs without changing any vanilla texture.
    if MASTER_TEXTURE.is_file():
        with Image.open(MASTER_TEXTURE) as source:
            return source.convert("RGBA").resize((SIZE, SIZE), Image.Resampling.LANCZOS)

    rng = random.Random(0xE17EAC)
    image = Image.new("RGBA", (LEGACY_SIZE, LEGACY_SIZE), (10, 7, 24, 255))
    pixels = image.load()
    for y in range(LEGACY_SIZE):
        for x in range(LEGACY_SIZE):
            grain = rng.randrange(-7, 8)
            edge = min(x, y, LEGACY_SIZE - 1 - x, LEGACY_SIZE - 1 - y)
            vignette = max(0, 24 - edge) // 3
            pixels[x, y] = (max(5, 17 + grain - vignette),
                            max(3, 10 + grain // 2 - vignette),
                            max(17, 38 + grain * 2 - vignette), 255)

    draw = ImageDraw.Draw(image, "RGBA")
    # Five readable armour segments, with a dark separation band between them.
    for index in range(5):
        top = 12 + index * 47
        bottom = min(LEGACY_SIZE - 12, top + 39)
        inset = 10 + (index % 2) * 5
        draw.rounded_rectangle((inset, top, SIZE - inset, bottom), radius=9,
                               fill=(22, 13, 48, 255), outline=(72, 35, 115, 255), width=4)
        draw.line((inset + 7, top + 7, SIZE - inset - 8, top + 3),
                  fill=(128, 59, 200, 170), width=3)
        draw.line((inset + 6, bottom - 6, SIZE - inset - 9, bottom - 9),
                  fill=(4, 7, 20, 240), width=5)
        for shard in range(4):
            x = inset + 17 + shard * 34 + (index % 2) * 6
            draw.polygon([(x, top + 9), (x + 8, top + 4), (x + 13, top + 19),
                          (x + 5, top + 27)], fill=(48, 26, 82, 220))
            draw.line((x + 3, top + 8, x + 9, top + 18), fill=(154, 76, 236, 210), width=2)

    # Central cyan energy vein and branching violet cracks.
    glow = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow, "RGBA")
    center_points = [(128, 6), (122, 38), (139, 73), (118, 112), (132, 151), (119, 191), (137, 247)]
    glow_draw.line(center_points, fill=(42, 218, 255, 180), width=13, joint="curve")
    image = Image.alpha_composite(image, glow.filter(ImageFilter.GaussianBlur(10)))
    draw = ImageDraw.Draw(image, "RGBA")
    draw.line(center_points, fill=(43, 226, 255, 255), width=5, joint="curve")
    draw.line([(128, 9), (120, 48), (135, 83), (119, 120), (132, 160), (119, 202), (137, 246)],
              fill=(191, 255, 255, 235), width=2, joint="curve")
    branches = [
        ((123, 37), (75, 24), (57, 42)), ((126, 58), (177, 46), (201, 61)),
        ((125, 98), (75, 112), (49, 98)), ((128, 130), (180, 145), (207, 129)),
        ((126, 175), (76, 188), (48, 173)), ((127, 219), (179, 205), (209, 220)),
    ]
    for start, mid, end in branches:
        draw.line([start, mid, end], fill=(210, 48, 255, 230), width=4, joint="curve")
        draw.line([start, mid, end], fill=(119, 68, 208, 255), width=2, joint="curve")

    # Four crystalline claws across the top edge: each maps to a separate
    # articulated tip_claw bone.  grab_socket is an empty helper bone, not a
    # painted part of the texture.
    for cx, lean in ((42, -1), (100, 0), (156, 0), (214, 1)):
        tip = (cx + lean * 17, 2)
        draw.polygon([(cx - 15, 34), (cx + 11, 30), tip, (cx - 3, 8)],
                     fill=(34, 18, 70, 255), outline=(157, 70, 250, 255))
        draw.line((cx - 3, 8, tip[0], tip[1]), fill=(78, 238, 255, 255), width=4)
        draw.line((cx - 10, 28, cx + 8, 31), fill=(202, 78, 255, 220), width=3)

    # Small cyan motes and purple chips stop the surface from reading as a flat paper card.
    for _ in range(46):
        x = rng.randrange(18, LEGACY_SIZE - 18)
        y = rng.randrange(10, LEGACY_SIZE - 10)
        radius = rng.choice((1, 1, 2, 3))
        color = (70, 235, 255, rng.randrange(120, 245)) if rng.random() < 0.42 else (194, 42, 255, rng.randrange(100, 220))
        draw.ellipse((x - radius, y - radius, x + radius, y + radius), fill=color)
    return image.resize((SIZE, SIZE), Image.Resampling.LANCZOS)


def model_cuboid(from_xyz: tuple[int, int, int], to_xyz: tuple[int, int, int]) -> dict:
    """Return one solid fallback segment in vanilla model coordinates.

    The optional client replaces the carrier with the articulated renderer.
    This model is still deliberately complete: ItemDisplay users must see a
    readable five-segment, four-claw object at the correct world height.
    """
    faces = {
        face: {"texture": "#tentacle"}
        for face in ("down", "up", "north", "south", "west", "east")
    }
    return {"from": list(from_xyz), "to": list(to_xyz), "faces": faces}


def make_fallback_model() -> dict:
    # The server scales this ordinary 0..16 model by 4.75, matching the
    # 4.75-block articulated client rig.  The silhouette is narrow and
    # segmented instead of a flat 1-block plane or an oversized cube.
    elements = [
        model_cuboid((6, 0, 6), (10, 3, 10)),
        model_cuboid((6, 2, 6), (10, 7, 10)),
        model_cuboid((5, 6, 5), (11, 11, 11)),
        model_cuboid((6, 10, 6), (10, 15, 10)),
        model_cuboid((5, 14, 5), (11, 16, 11)),
        model_cuboid((2, 12, 5), (5, 16, 8)),
        model_cuboid((11, 12, 5), (14, 16, 8)),
        model_cuboid((5, 12, 2), (8, 16, 5)),
        model_cuboid((5, 12, 11), (8, 16, 14)),
    ]
    return {
        "parent": "minecraft:block/block",
        "ambientocclusion": False,
        "textures": {
            "particle": "copimine:item/end_event_rift_tentacle_hd",
            "tentacle": "copimine:item/end_event_rift_tentacle_hd",
        },
        "copimine_rig": {
            "texture_size": [SIZE, SIZE],
            "forward_axis": "+Z",
            "bones": ["root", "base", "seg_01", "seg_02", "seg_03", "seg_04",
                      "seg_05", "tip", "tip_claw_1", "tip_claw_2", "tip_claw_3",
                      "tip_claw_4"],
            "grab_socket": {"parent": "tip", "geometry": False, "local": [0.0, 4.75, 0.0]},
        },
        "elements": elements,
    }


def main() -> None:
    texture = make_texture()
    SERVER_TEXTURE.parent.mkdir(parents=True, exist_ok=True)
    CLIENT_TEXTURE.parent.mkdir(parents=True, exist_ok=True)
    MODEL.parent.mkdir(parents=True, exist_ok=True)
    texture.save(SERVER_TEXTURE, format="PNG", optimize=True)
    texture.save(CLIENT_TEXTURE, format="PNG", optimize=True)
    MODEL.write_text(json.dumps(make_fallback_model(), indent=2) + "\n", encoding="utf-8")
    print(f"generated {SERVER_TEXTURE}, {CLIENT_TEXTURE} and {MODEL} ({SIZE}x{SIZE})")


if __name__ == "__main__":
    main()
