"""Create the hand-authored pixel atlases used by the local End Rift client mod.

These are native Minecraft UV sheets, not screenshots or concept thumbnails.  The
script intentionally keeps every sheet opaque at its declared atlas size so the
vanilla model UVs receive a readable surface on every face.  Palette, cracks and
sigils are kept different per entity so a wave mob cannot be mistaken for the
elite, guardian, spider, or skeleton.
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src" / "main" / "resources" / "assets" / "copimineclient" / "textures" / "entity"


def rgba(color: tuple[int, int, int]) -> tuple[int, int, int, int]:
    return (*color, 255)


def clean_surface(size: tuple[int, int], base: tuple[int, int, int]) -> Image.Image:
    """Create an opaque sheet without generated checkerboards or guide lines."""
    return Image.new("RGBA", size, rgba(base))


def angular(draw: ImageDraw.ImageDraw, points: tuple[tuple[int, int], ...],
            color: tuple[int, int, int], width: int = 1) -> None:
    draw.line(points, fill=rgba(color), width=width, joint="curve")


def enderman_sheet(name: str, palette: list[tuple[int, int, int]], seed: int,
                   accent: tuple[int, int, int], eye: tuple[int, int, int]) -> None:
    """Paint a restrained hand-authored Enderman UV sheet.

    The old generator filled the UV sheet with a regular 4×3 grid.  That grid
    was visible as twelve unrelated blocks on the mob.  These marks follow the
    humanoid islands instead: a dark shell, one chest seam, short limb seams,
    and a single controlled eye/core colour.
    """
    dark, shadow, mid, light = palette[:4]
    image = clean_surface((64, 32), dark)
    draw = ImageDraw.Draw(image)

    # Head and neck: one broad face with a broken forehead seam.
    draw.rectangle((1, 1, 14, 8), fill=rgba(shadow))
    draw.rectangle((3, 2, 12, 7), fill=rgba(mid))
    angular(draw, ((4, 2), (6, 4), (5, 7), (9, 7), (11, 4)), light)
    draw.rectangle((5, 4, 7, 5), fill=rgba(eye))
    draw.rectangle((10, 4, 12, 5), fill=rgba(eye))

    # Torso island: the chest seam is deliberately asymmetrical rather than a
    # tiled panel, so it reads as a rift fracture once wrapped around the body.
    draw.rectangle((16, 1, 27, 14), fill=rgba(shadow))
    angular(draw, ((21, 2), (20, 5), (22, 8), (21, 12), (24, 14)), accent, 1)
    draw.rectangle((20, 7, 22, 9), fill=rgba(eye))
    draw.point((23, 5), fill=rgba(light))

    # Arms and legs use short, offset seams. They are not full-height stripes,
    # which prevents mipmapping from turning the sheet into a barcode.
    for start in (32, 48):
        draw.rectangle((start + 1, 1, start + 7, 14), fill=rgba(shadow))
        angular(draw, ((start + 2, 3), (start + 5, 6), (start + 3, 10),
                       (start + 6, 13)), light)
    for start in (0, 8, 40, 56):
        draw.rectangle((start + 1, 17, min(start + 6, 63), 30), fill=rgba(shadow))
        angular(draw, ((start + 2, 19), (start + 5, 22), (start + 3, 26),
                       (min(start + 5, 63), 29)), mid)
    for x in (3, 12, 51):
        draw.rectangle((x, 27, min(x + 1, 63), 28), fill=rgba(accent))

    # jaw_patch: dedicated lower island for the jaw plate at UV (50, 30). Keeping this
    # patch out of the horn island prevents the face from borrowing horn
    # pixels when the standard cuboid footprint is sampled.
    draw.rectangle((50, 30, 63, 31), fill=rgba(shadow))
    angular(draw, ((51, 30), (55, 31), (59, 30), (63, 31)), light)

    # Keep the seed as a stable variant knob without introducing random noise.
    if seed % 2 == 0:
        draw.point((29, 12), fill=rgba(accent))
    image.save(OUT / name, format="PNG", optimize=False)


def rift_guardian_phase_sheet(name: str, palette: list[tuple[int, int, int]], seed: int,
                              accent: tuple[int, int, int], core: tuple[int, int, int]) -> None:
    # Paint a clean 128px logical UV sheet first, then export a 512px atlas.
    # The client model scales the same UV coordinates by four. This keeps the
    # broad armour plates crisp at close range without returning to the old
    # noisy checkerboard/guide-line look.
    image = Image.new("RGBA", (128, 128), (*palette[0], 255))
    draw = ImageDraw.Draw(image)

    def rgba(color: tuple[int, int, int], alpha: int = 255) -> tuple[int, int, int, int]:
        return (*color, alpha)

    def panel(box: tuple[int, int, int, int], fill: tuple[int, int, int], edge: tuple[int, int, int], width: int = 1) -> None:
        draw.rectangle(box, fill=rgba(fill), outline=rgba(edge), width=width)

    # Deliberate UV islands used by the custom torso, shoulders, arms, shards,
    # legs, and head.  Alternating dark plates provide silhouette highlights
    # while leaving the bright lines sparse enough to read in motion.
    uv_panels = (
        ((0, 0, 55, 47), palette[1], palette[2]),
        ((56, 4, 91, 31), palette[2], accent),
        ((88, 4, 109, 61), palette[1], palette[-1]),
        ((4, 64, 37, 96), palette[2], accent),
        ((42, 64, 58, 91), palette[1], palette[-1]),
        ((0, 84, 27, 111), palette[1], accent),
        ((28, 84, 43, 111), palette[2], core),
        ((0, 96, 15, 111), palette[2], accent),
    )
    for index, (box, fill, edge) in enumerate(uv_panels):
        panel(box, fill, edge, 1 if index < 6 else 2)

    # The primary 14x25 torso face: a mirrored collar and central crack make
    # the boss read as a guardian rather than a recoloured Enderman.
    torso_dark = palette[0]
    draw.rectangle((1, 1, 13, 25), fill=rgba(torso_dark), outline=rgba(accent))
    draw.line((2, 3, 4, 7, 3, 12, 5, 17, 4, 24), fill=rgba(accent), width=1, joint="curve")
    draw.line((12, 3, 10, 7, 11, 12, 9, 17, 10, 24), fill=rgba(accent), width=1, joint="curve")
    draw.line((6, 2, 6, 7, 7, 12, 6, 17, 6, 24), fill=rgba(core), width=1)
    draw.line((7, 2, 7, 7, 6, 12), fill=rgba(palette[-1]), width=1)
    draw.rectangle((4, 8, 8, 12), outline=rgba(core), width=1)
    draw.point((6, 10), fill=rgba(palette[-1]))

    # Symmetric armour seams on the larger UV islands.
    for left, right, top, bottom in ((57, 90, 6, 29), (5, 36, 66, 94), (1, 26, 86, 109)):
        mid = (left + right) // 2
        draw.line((mid, top + 2, mid - 3, top + 8, mid, top + 14, mid - 2, bottom - 2),
                  fill=rgba(accent), width=1, joint="curve")
        draw.line((mid, top + 2, mid + 3, top + 8, mid, top + 14, mid + 2, bottom - 2),
                  fill=rgba(palette[-1]), width=1, joint="curve")
        draw.line((left + 2, top + 4, left + 1, bottom - 3), fill=rgba(palette[2], 220), width=1)
        draw.line((right - 2, top + 4, right - 1, bottom - 3), fill=rgba(palette[2], 220), width=1)

    # Chest-rift UV (28..34, 84..94) is intentionally bright: this is the
    # emissive focal point visible on the front of the in-game model.
    draw.rectangle((29, 84, 34, 94), fill=rgba(core), outline=rgba(palette[-1]), width=1)
    draw.line((31, 85, 30, 88, 32, 91, 31, 94), fill=rgba(palette[0]), width=1)
    draw.point((32, 87), fill=rgba(palette[-1]))

    # Head island (0..11, 96..109): two eyes and a compact forehead mark.
    draw.rectangle((1, 97, 11, 109), fill=rgba(palette[0]), outline=rgba(accent))
    draw.rectangle((3, 101, 5, 102), fill=rgba(core))
    draw.rectangle((7, 101, 9, 102), fill=rgba(core))
    draw.line((6, 98, 5, 101, 6, 104, 6, 108), fill=rgba(accent), width=1)

    # A central shard sigil is kept sparse so it remains a crisp accent when
    # the texture is sampled at distance.
    draw.polygon([(64, 50), (72, 64), (64, 78), (56, 64)], fill=rgba(palette[0]), outline=rgba(core))
    draw.line((64, 53, 64, 75), fill=rgba(accent), width=1)
    draw.line((59, 64, 69, 64), fill=rgba(palette[-1]), width=1)
    draw.point((64, 64), fill=rgba(core))
    image.resize((512, 512), Image.Resampling.NEAREST).save(
        OUT / name, format="PNG", optimize=False)


def spider_sheet(name: str = "end_rift_spider.png", role: str = "ordinary") -> None:
    """Paint a clean 64x32 spider atlas for one gameplay role.

    The UV layout stays stable across the four spider roles, while the sparse
    crest/seal marks give each role a readable material identity.  Keeping the
    layout stable is important: the renderer can swap a role model and texture
    atomically without exposing a checkerboard or an untextured limb.
    """
    dark = (10, 2, 16)
    shell = (31, 9, 42)
    mid = (60, 17, 79)
    edge = (103, 24, 127)
    eye = (208, 37, 255)
    if role == "elite":
        shell, mid, edge, eye = (25, 5, 38), (72, 12, 102), (151, 26, 207), (230, 52, 255)
    elif role == "wave_guardian":
        shell, mid, edge, eye = (18, 5, 31), (61, 17, 91), (174, 45, 229), (230, 70, 255)
    elif role == "ritual_guard":
        shell, mid, edge, eye = (27, 4, 45), (83, 15, 110), (190, 50, 218), (238, 70, 255)
    image = clean_surface((64, 32), dark)
    draw = ImageDraw.Draw(image)
    # One shell island and eight tapered leg marks; no artificial atlas grid.
    draw.polygon([(24, 8), (39, 8), (43, 13), (40, 21), (23, 21), (20, 14)],
                 fill=rgba(shell), outline=rgba(edge))
    angular(draw, ((25, 10), (31, 13), (38, 10)), mid)
    draw.rectangle((28, 13, 35, 17), fill=rgba(dark), outline=rgba(edge))
    draw.rectangle((29, 13, 31, 14), fill=rgba(eye))
    draw.rectangle((33, 13, 35, 14), fill=rgba(eye))
    for x, bend in ((4, -4), (12, 4), (20, -3), (28, 4),
                    (36, -4), (44, 4), (52, -3), (60, 4)):
        angular(draw, ((x, 19), (x + bend, 24), (x + bend // 2, 30)), edge, 2)
        draw.point((x + bend // 2, 28), fill=rgba(mid))
    # shared spider shell island: the large shell, elite carapace, and body extension intentionally share
    # one dark material island at UV (0, 16). Paint it as one coherent plate
    # so those authored boxes do not pick up unrelated leg pixels.
    draw.rectangle((0, 12, 47, 31), fill=rgba(shell))
    angular(draw, ((3, 15), (12, 18), (20, 15), (29, 20), (40, 16), (46, 19)), mid)
    angular(draw, ((5, 29), (13, 25), (22, 29), (31, 24), (42, 28)), edge)
    if role == "elite":
        angular(draw, ((22, 8), (27, 5), (32, 8), (37, 5), (42, 8)), edge, 1)
        draw.rectangle((23, 18, 25, 20), fill=rgba(mid))
        draw.rectangle((38, 18, 40, 20), fill=rgba(mid))
    elif role == "wave_guardian":
        angular(draw, ((31, 8), (29, 5), (31, 3), (33, 5), (31, 8)), eye, 1)
        draw.rectangle((21, 12, 23, 16), fill=rgba(mid))
        draw.rectangle((41, 12, 43, 16), fill=rgba(mid))
    elif role == "ritual_guard":
        draw.rectangle((27, 18, 36, 19), fill=rgba(edge))
        draw.rectangle((30, 18, 33, 20), fill=rgba(eye))
        angular(draw, ((24, 9), (27, 7), (30, 9)), edge, 1)
        angular(draw, ((33, 9), (36, 7), (39, 9)), edge, 1)
    image.save(OUT / name, format="PNG", optimize=False)


def skeleton_sheet(name: str, palette: list[tuple[int, int, int]], seed: int,
                   accent: tuple[int, int, int], eye: tuple[int, int, int]) -> None:
    """Paint an authored skeleton UV sheet with bone accents and no grid."""
    dark, shell, mid, light = palette[:4]
    bone_shadow = (94, 84, 108)
    bone = (205, 199, 216)
    bone_light = (246, 243, 250)
    image = clean_surface((64, 32), dark)
    draw = ImageDraw.Draw(image)

    # Head: a compact mask, two controlled eyes and a visible jaw line.
    draw.rectangle((1, 1, 14, 8), fill=rgba(shell))
    draw.rectangle((3, 2, 12, 7), fill=rgba(mid))
    angular(draw, ((4, 2), (6, 4), (5, 6), (9, 6), (11, 3)), light)
    draw.rectangle((5, 4, 7, 5), fill=rgba(eye))
    draw.rectangle((10, 4, 12, 5), fill=rgba(eye))
    draw.rectangle((5, 7, 11, 8), fill=rgba(bone_shadow))
    draw.point((7, 7), fill=rgba(bone_light))

    # Torso: ribs are short bone strokes around one chest rift, not a tiled
    # rectangle. The same rhythm survives on the front and side faces.
    draw.rectangle((16, 1, 27, 15), fill=rgba(shell))
    draw.rectangle((19, 2, 24, 14), fill=rgba(mid))
    for y, span in ((4, 2), (7, 3), (10, 2), (13, 1)):
        angular(draw, ((17, y), (20, y + 1), (21, y)), bone)
        angular(draw, ((23, y), (24, y + 1), (26, y)), bone)
    angular(draw, ((21, 2), (20, 5), (22, 8), (21, 11), (24, 14)), accent)
    draw.rectangle((21, 7, 22, 9), fill=rgba(eye))

    # Arms: alternating violet shell and deliberate bone cuffs echo the
    # supplied long-limbed reference without making every pixel bright.
    for start in (32, 48):
        draw.rectangle((start + 1, 1, min(start + 7, 63), 15), fill=rgba(shell))
        angular(draw, ((start + 2, 2), (start + 6, 5), (start + 3, 9),
                       (start + 7, 13)), light)
        draw.rectangle((start + 2, 11, min(start + 6, 63), 13), fill=rgba(bone_shadow))
        draw.point((start + 3, 11), fill=rgba(bone_light))
    if seed % 2 == 0:
        draw.rectangle((42, 5, 45, 7), fill=rgba(bone))
        draw.rectangle((58, 5, 61, 7), fill=rgba(bone))

    # Legs: two narrow vertical bone columns, broken by dark knee and ankle
    # joints to match the reference silhouette.
    for start in (0, 8):
        draw.rectangle((start + 1, 17, start + 6, 31), fill=rgba(shell))
        draw.rectangle((start + 2, 18, start + 5, 26), fill=rgba(bone_shadow))
        draw.rectangle((start + 2, 19, start + 4, 22), fill=rgba(bone_light))
        draw.rectangle((start + 3, 24, start + 5, 25), fill=rgba(dark))
        draw.rectangle((start + 2, 27, start + 5, 30), fill=rgba(bone))
    # The remaining UV island carries the rift core and small shin shards.
    draw.rectangle((24, 17, 31, 24), fill=rgba(shell))
    angular(draw, ((27, 17), (26, 20), (28, 22), (27, 24)), accent)
    draw.rectangle((26, 19, 28, 21), fill=rgba(eye))
    for x in (40, 56):
        draw.rectangle((x, 24, min(x + 2, 63), 29), fill=rgba(bone_shadow))
        draw.point((x + 1, 25), fill=rgba(bone_light))
    # elite_shoulder_patch: dedicated lower shoulder island at UV (40, 16). It stays separate from
    # the arm islands so the elite silhouette does not inherit cuff pixels.
    draw.rectangle((40, 16, 59, 23), fill=rgba(shell))
    angular(draw, ((41, 17), (45, 19), (49, 17), (54, 21), (58, 18)), bone)
    draw.rectangle((47, 20, 52, 22), fill=rgba(bone_shadow))
    image.save(OUT / name, format="PNG", optimize=False)


def ritual_caster_sheet() -> None:
    """Paint the raised-arm caster surface used by the Wave 6 Enderman rig."""
    dark = (9, 2, 17)
    shell = (25, 5, 38)
    mid = (54, 11, 77)
    violet = (112, 19, 149)
    rift = (218, 43, 255)
    bone = (224, 214, 235)
    image = clean_surface((64, 32), dark)
    draw = ImageDraw.Draw(image)
    draw.rectangle((1, 1, 14, 8), fill=rgba(shell))
    draw.rectangle((4, 2, 11, 7), fill=rgba(mid))
    angular(draw, ((5, 2), (7, 5), (6, 7), (10, 7)), violet)
    draw.rectangle((5, 4, 7, 5), fill=rgba(rift))
    draw.rectangle((10, 4, 12, 5), fill=rgba(rift))
    draw.rectangle((16, 1, 27, 15), fill=rgba(shell))
    draw.rectangle((19, 2, 24, 14), fill=rgba(mid))
    angular(draw, ((21, 2), (20, 5), (22, 8), (21, 12), (24, 14)), violet)
    draw.rectangle((20, 7, 22, 10), fill=rgba(rift))
    # Two raised-arm islands with pale cuffs: the texture reinforces the pose
    # instead of fighting it with a horizontal checkerboard.
    for start in (32, 48):
        draw.rectangle((start + 1, 1, min(start + 7, 63), 15), fill=rgba(shell))
        angular(draw, ((start + 2, 14), (start + 5, 10), (start + 3, 6),
                       (start + 6, 2)), violet)
        draw.rectangle((start + 2, 2, min(start + 5, 63), 4), fill=rgba(bone))
        draw.point((start + 4, 2), fill=rgba(rift))
    for start in (0, 8, 40, 56):
        draw.rectangle((start + 1, 17, min(start + 6, 63), 30), fill=rgba(shell))
        angular(draw, ((start + 2, 19), (start + 5, 23), (start + 3, 28)), mid)
    draw.rectangle((29, 18, 34, 23), fill=rgba(shell), outline=rgba(violet))
    draw.rectangle((31, 19, 32, 22), fill=rgba(rift))
    image.save(OUT / "end_rift_ritual_caster.png", format="PNG", optimize=False)


def bossbar_frame() -> None:
    """Paint a transparent 256x32 HUD frame around the health fill."""
    gui_out = OUT.parent / "gui"
    gui_out.mkdir(parents=True, exist_ok=True)
    image = Image.new("RGBA", (256, 32), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    shadow = (5, 6, 14, 245)
    steel = (105, 112, 132, 255)
    steel_light = (214, 220, 224, 255)
    steel_dark = (38, 42, 61, 255)
    bone = (211, 193, 162, 255)
    bone_light = (255, 237, 190, 255)
    violet = (130, 72, 210, 255)
    ember = (238, 116, 53, 255)

    # Angular upper and lower rails leave the centre transparent so the Java
    # renderer can draw a phase-coloured health fill underneath this frame.
    draw.line((18, 5, 238, 5), fill=shadow, width=2)
    draw.line((15, 7, 241, 7), fill=steel_dark, width=2)
    draw.line((18, 8, 238, 8), fill=steel_light, width=1)
    draw.line((18, 23, 238, 23), fill=steel_dark, width=2)
    draw.line((15, 25, 241, 25), fill=shadow, width=2)
    draw.line((19, 22, 237, 22), fill=steel, width=1)

    left_end = [(0, 15), (8, 7), (19, 7), (27, 11), (21, 15),
                (27, 19), (19, 25), (8, 25)]
    right_end = [(255 - x, y) for x, y in left_end]
    draw.polygon(left_end, fill=shadow)
    draw.polygon(right_end, fill=shadow)
    draw.line((1, 15, 9, 8, 20, 8), fill=bone_light, width=2, joint="curve")
    draw.line((1, 15, 9, 23, 20, 23), fill=bone, width=2, joint="curve")
    draw.line((254, 15, 246, 8, 235, 8), fill=bone_light, width=2, joint="curve")
    draw.line((254, 15, 246, 23, 235, 23), fill=bone, width=2, joint="curve")

    # Mirrored metal ribs make the silhouette read as an ornate frame rather
    # than a flat paper strip at Minecraft's normal HUD scale.
    for start in (23, 33, 43, 53):
        draw.polygon([(start, 9), (start + 4, 9), (start + 17, 21),
                      (start + 13, 21)], fill=steel_dark)
        draw.line((start + 1, 10, start + 14, 20), fill=steel_light, width=1)
        mirror = 255 - start
        draw.polygon([(mirror, 9), (mirror - 4, 9), (mirror - 17, 21),
                      (mirror - 13, 21)], fill=steel_dark)
        draw.line((mirror - 1, 10, mirror - 14, 20), fill=steel_light, width=1)

    # Bone hooks and small bolts break up the rails without obscuring text.
    for x in (29, 48, 67, 188, 207, 226):
        draw.rectangle((x, 5, x + 3, 8), fill=bone)
        draw.point((x + 1, 6), fill=bone_light)
    for x in (12, 244):
        draw.rectangle((x, 14, x + 2, 17), fill=bone_light)
        draw.point((x + 1, 15), fill=ember)

    # A restrained central rift sigil anchors the title and is deliberately
    # symmetric around the exact midpoint of the 256px sheet.
    draw.polygon([(128, 7), (135, 14), (128, 23), (121, 14)], fill=shadow)
    draw.line((128, 8, 134, 14, 128, 21, 122, 14, 128, 8), fill=violet, width=1, joint="curve")
    draw.line((128, 10, 128, 19), fill=ember, width=1)
    draw.point((128, 14), fill=bone_light)

    image.save(gui_out / "end_rift_bossbar_frame.png", format="PNG", optimize=False)


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    enderman_sheet(
        "end_rift_enderman.png",
        [(10, 2, 16), (20, 4, 30), (33, 11, 41), (52, 14, 67), (78, 20, 98), (112, 24, 142)],
        17,
        (136, 0, 255),
        (174, 0, 255),
    )
    enderman_sheet(
        "end_rift_elite.png",
        [(8, 2, 14), (18, 3, 27), (30, 6, 45), (48, 9, 67), (76, 14, 103), (116, 20, 151)],
        29,
        (156, 0, 255),
        (214, 24, 255),
    )
    enderman_sheet(
        "end_rift_guardian.png",
        [(39, 8, 21), (76, 13, 27), (124, 20, 34), (181, 40, 48), (236, 105, 54), (248, 207, 83)],
        43,
        (248, 207, 83),
        (255, 240, 137),
    )
    rift_guardian_phase_sheet(
        "rift_guardian_awakening.png",
        [(20, 18, 50), (44, 30, 83), (83, 43, 119), (132, 56, 151), (207, 93, 167), (255, 188, 115)],
        151,
        (255, 188, 115),
        (255, 232, 179),
    )
    rift_guardian_phase_sheet(
        "rift_guardian_hunt.png",
        [(10, 35, 37), (16, 70, 67), (21, 108, 93), (40, 146, 111), (173, 151, 62), (255, 222, 111)],
        163,
        (173, 151, 62),
        (159, 255, 226),
    )
    rift_guardian_phase_sheet(
        "rift_guardian_rift.png",
        [(24, 14, 59), (48, 24, 102), (77, 33, 146), (38, 95, 153), (54, 178, 188), (224, 81, 216)],
        179,
        (54, 178, 188),
        (248, 173, 255),
    )
    rift_guardian_phase_sheet(
        "rift_guardian_overload.png",
        [(8, 26, 56), (16, 55, 91), (23, 92, 132), (37, 129, 165), (90, 208, 188), (197, 255, 214)],
        191,
        (90, 208, 188),
        (223, 255, 237),
    )
    rift_guardian_phase_sheet(
        "rift_guardian_rage.png",
        [(52, 9, 25), (93, 15, 34), (143, 25, 39), (192, 45, 45), (242, 89, 54), (255, 226, 107)],
        211,
        (242, 89, 54),
        (255, 245, 157),
    )
    rift_guardian_phase_sheet(
        "rift_guardian_last_seal.png",
        [(12, 5, 24), (36, 9, 48), (83, 12, 76), (142, 18, 117), (230, 52, 177), (255, 196, 235)],
        217,
        (230, 52, 177),
        (255, 196, 235),
    )
    rift_guardian_phase_sheet(
        "rift_guardian_final_strike.png",
        [(12, 5, 24), (36, 9, 48), (83, 12, 76), (142, 18, 117), (230, 52, 177), (255, 196, 235)],
        223,
        (230, 52, 177),
        (255, 196, 235),
    )
    spider_sheet()
    skeleton_sheet(
        "end_rift_skeleton.png",
        [(10, 2, 16), (20, 4, 30), (33, 11, 41), (52, 14, 67), (78, 20, 98), (112, 24, 142)],
        227,
        (136, 0, 255),
        (174, 0, 255),
    )
    skeleton_sheet(
        "end_rift_elite_skeleton.png",
        [(8, 2, 14), (18, 3, 27), (30, 6, 45), (48, 9, 67), (76, 14, 103), (116, 20, 151)],
        239,
        (156, 0, 255),
        (214, 24, 255),
    )
    ritual_caster_sheet()
    enderman_sheet(
        "end_rift_wave_guardian_enderman.png",
        [(7, 1, 15), (17, 2, 29), (32, 7, 55), (58, 11, 88), (111, 20, 167), (168, 30, 226)],
        251,
        (173, 24, 255),
        (238, 76, 255),
    )
    enderman_sheet(
        "end_rift_ritual_guard_enderman.png",
        [(11, 1, 20), (27, 3, 43), (51, 8, 72), (82, 15, 106), (164, 33, 192), (238, 91, 229)],
        263,
        (204, 74, 228),
        (238, 70, 255),
    )
    skeleton_sheet(
        "end_rift_wave_guardian_skeleton.png",
        [(7, 1, 15), (17, 2, 29), (32, 7, 55), (58, 11, 88), (111, 20, 167), (168, 30, 226)],
        271,
        (173, 24, 255),
        (238, 76, 255),
    )
    skeleton_sheet(
        "end_rift_ritual_guard_skeleton.png",
        [(11, 1, 20), (27, 3, 43), (51, 8, 72), (82, 15, 106), (164, 33, 192), (238, 91, 229)],
        277,
        (204, 74, 228),
        (238, 70, 255),
    )
    spider_sheet("end_rift_elite_spider.png", "elite")
    spider_sheet("end_rift_wave_guardian_spider.png", "wave_guardian")
    spider_sheet("end_rift_ritual_guard_spider.png", "ritual_guard")
    bossbar_frame()


if __name__ == "__main__":
    main()
