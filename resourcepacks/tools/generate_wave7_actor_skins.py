"""Authored entity textures for the End Rift named Wave 7 trial actors.

Generated deterministically by ``resourcepacks/tools/generate_wave7_actor_skins.py``
and written straight into the one resource root that ships them.

Why the atlas is filled before it is decorated
----------------------------------------------
``tools/minecraft-26.3/client`` repacks any baked face whose texels are less than
95 % painted onto unrelated atlas pixels (``EndRiftAtlasUvLayout.fit``), and it
deliberately skips paths containing ``rune``/``sigil`` -- which is why the
Archmage's sigil ring, the only "a spell is coming" telegraph, rendered nothing at
all: its island was 0 % painted and never repaired.  The previous generator also
painted a handful of hand-guessed rectangles instead of the rigs' real footprints,
so ``rift_shard_left``/``rift_shard_right``, the skeleton's lower legs and the
Archmage rune hand were empty.

Every drawing routine below therefore starts from a full-canvas base material and
paints each declared face from the parsed rig (``wave7_uv_layout``).  Nothing on
the atlas can sample transparency, the runtime repack becomes a no-op, and the
sigl/rune parts that the repack excludes are genuinely painted.

Palette follows the authored rift direction already used by the event: blackened
rift hide, corrupted violet cloth, emerald seals, and a pale bone highlight so the
silhouettes read at arena distance.
"""
from __future__ import annotations

import sys
from pathlib import Path
from typing import Callable, NamedTuple

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
import wave7_uv_layout as layout  # noqa: E402  (shares one footprint source of truth)

# ``parents[2]`` is the repository root.  The previous ``parents[1]`` resolved to
# ``resourcepacks/`` and main() then wrote into ``resourcepacks/assets/...``: a
# directory that has never existed, so the generator created a phantom tree and
# the shipped PNGs could not be regenerated from their code.
ROOT = Path(__file__).resolve().parents[2]
TEXTURE_ROOT = (
    ROOT / "CopiMineClient" / "src" / "main" / "resources" / "assets"
    / "copimineclient" / "textures" / "entity"
)

# Rift palette (locked; the same values are used by every actor so the three
# named trials read as one family).
RIFT_BLACK = (13, 10, 17)
RIFT_DEEP = (26, 18, 34)
RIFT_HIDE = (38, 26, 50)
RIFT_HIDE_LIT = (58, 40, 74)
RIFT_VIOLET = (94, 58, 138)
RIFT_VIOLET_LIT = (132, 84, 180)
RIFT_GLOW = (186, 126, 236)
RIFT_EMERALD = (46, 196, 148)
RIFT_EMERALD_LIT = (128, 246, 200)
BONE = (206, 196, 168)
BONE_SHADE = (150, 140, 118)
BONE_DARK = (104, 96, 82)
CLOTH = (58, 44, 78)
CLOTH_LIT = (86, 66, 112)
METAL = (66, 70, 86)
METAL_LIT = (120, 126, 148)
EYE = (240, 110, 90)


# ---------------------------------------------------------------------------
# Drawing primitives (1 texel = 1 pixel, no antialiasing anywhere)
# ---------------------------------------------------------------------------

def _canvas(width: int, height: int) -> Image.Image:
    return Image.new("RGBA", (width, height), (0, 0, 0, 0))


def _noise(seed: int, x: int, y: int) -> float:
    """Deterministic 0..1 hash noise; keeps regeneration byte-identical."""
    v = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    v = (v ^ (v >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((v ^ (v >> 16)) & 0xFFFF) / 65535.0


def _clamp(color: tuple[int, int, int], shade: float, jitter: int = 0) -> tuple[int, int, int, int]:
    return tuple(max(0, min(255, int(c * shade) + jitter)) for c in color) + (255,)


def fill_rect(draw: ImageDraw.ImageDraw, x: int, y: int, w: int, h: int,
              base: tuple[int, int, int], seed: int, spread: int = 12,
              edge: tuple[int, int, int] | None = None) -> None:
    """Fill a UV island with rift hide that darkens toward its own border."""
    for yy in range(h):
        for xx in range(w):
            n = _noise(seed, x + xx, y + yy)
            gx = min(xx, w - 1 - xx) / max(1.0, (w - 1) / 2.0)
            gy = min(yy, h - 1 - yy) / max(1.0, (h - 1) / 2.0)
            shade = 0.62 + 0.38 * min(1.0, min(gx, gy) * 1.7)
            draw.point((x + xx, y + yy), fill=_clamp(base, shade, int((n - 0.5) * spread)))
    if edge is not None:
        rim(draw, x, y, w, h, edge)


def rim(draw: ImageDraw.ImageDraw, x: int, y: int, w: int, h: int,
        color: tuple[int, int, int]) -> None:
    draw.rectangle([x, y, x + w - 1, y + h - 1], outline=color + (255,))


def band(draw: ImageDraw.ImageDraw, x: int, y: int, w: int, h: int,
         color: tuple[int, int, int]) -> None:
    draw.rectangle([x, y, x + w - 1, y + h - 1], fill=color + (255,))


def dot(draw: ImageDraw.ImageDraw, x: int, y: int, color: tuple[int, int, int]) -> None:
    draw.point((x, y), fill=color + (255,))


def glowing_crack(draw: ImageDraw.ImageDraw, x0: int, y0: int, x1: int, y1: int,
                  color: tuple[int, int, int], seed: int) -> None:
    """A jagged vertical seam with a pair of brighter core texels."""
    steps = max(1, y1 - y0)
    for i in range(steps + 1):
        t = i / steps
        xx = x0 + (x1 - x0) * t + (0.6 if _noise(seed, i, 3) > 0.5 else -0.6)
        yy = y0 + i
        dot(draw, round(xx), yy, color)
        if i % 3 == 0:
            dot(draw, round(xx) + 1, yy, tuple(min(255, c + 40) for c in color))


def rune(draw: ImageDraw.ImageDraw, cx: int, cy: int, size: int,
         color: tuple[int, int, int]) -> None:
    """Four-point rift rune; readable at one texel per pixel."""
    for dx, dy in ((0, 0), (-size, 0), (size, 0), (0, -size), (0, size)):
        dot(draw, cx + dx, cy + dy, color)


def paint_part(draw: ImageDraw.ImageDraw, atlas: layout.Atlas, part: str,
               base: tuple[int, int, int], seed: int, spread: int = 12,
               edge: tuple[int, int, int] | None = None) -> dict[str, layout.Face]:
    """Fill all six faces of one part and return them keyed by side."""
    faces = {}
    for face in atlas.faces_named(part):
        fill_rect(draw, face.x, face.y, face.width, face.height,
                  base, seed + layout.FACE_SIDES.index(face.side), spread, edge)
        faces[face.side] = face
    return faces


def _assert_fully_painted(image: Image.Image, atlas: layout.Atlas) -> None:
    """Fail closed: an unpainted texel is what made faces disappear at runtime."""
    alpha = image.getchannel("A").tobytes()
    for face in atlas.faces():
        for y in range(face.y, face.bottom):
            row = y * image.width
            for x in range(face.x, face.right):
                if alpha[row + x] < 255:
                    raise AssertionError(
                        f"{atlas.name}: {face.part} {face.side} face has an unpainted texel "
                        f"at ({x}, {y})"
                    )


# ---------------------------------------------------------------------------
# Warden (Ghost Warden / Ripula of the Rift) - vanilla Warden atlas 128x128
# ---------------------------------------------------------------------------

def _warden_hide(image: Image.Image) -> None:
    """Base hide for the whole atlas, including the vanilla body islands."""
    draw = ImageDraw.Draw(image)
    for y in range(128):
        for x in range(128):
            n = _noise(7, x, y)
            shade = 0.72 + 0.28 * (1.0 - y / 127.0) + (n - 0.5) * 0.16
            draw.point((x, y), fill=_clamp(RIFT_HIDE, shade, int((n - 0.5) * 9)))
    # Segmented plates: the colossus reads as armoured hide rather than as a
    # single flat colour, at every distance.
    for y in range(4, 128, 8):
        for x in range(128):
            draw.point((x, y), fill=_clamp(RIFT_DEEP, 1.0 + (_noise(8, x, y) - 0.5) * 0.2))
    for x in range(6, 128, 24):
        for y in range(128):
            draw.point((x, y), fill=_clamp(RIFT_DEEP, 1.12))


def _warden_body_accents(draw: ImageDraw.ImageDraw) -> None:
    """Emerald rib lanterns and the violet heart on the vanilla body island."""
    for col in (4, 8, 12):
        glowing_crack(draw, col, 64, col, 79, RIFT_EMERALD, 100 + col)
    for row in range(66, 79, 4):
        dot(draw, 8, row, RIFT_EMERALD_LIT)
    for px, py in ((7, 70), (8, 70), (9, 70), (7, 71), (9, 71), (8, 72),
                   (7, 73), (8, 73), (9, 73)):
        dot(draw, px, py, RIFT_VIOLET_LIT)
    for px, py in ((4, 3), (5, 3), (10, 3), (11, 3), (19, 4), (27, 4)):
        dot(draw, px, py, RIFT_GLOW)


def _warden_crown(draw: ImageDraw.ImageDraw, atlas: layout.Atlas) -> None:
    faces = paint_part(draw, atlas, "rift_crown", RIFT_VIOLET, 141, 8, RIFT_BLACK)
    for side in ("top", "bottom"):
        face = faces[side]
        band(draw, face.x, face.y, face.width, face.height, RIFT_DEEP)
    front = faces["front"]
    for offset in (1, front.width // 2, front.width - 2):
        band(draw, front.x + offset, front.y, 1, front.height - 1, RIFT_VIOLET_LIT)
        dot(draw, front.x + offset, front.y, RIFT_GLOW)
    for side in ("left", "right", "back"):
        face = faces[side]
        band(draw, face.x, face.y, face.width, 1, RIFT_GLOW)


def _warden_chest_plate(draw: ImageDraw.ImageDraw, atlas: layout.Atlas) -> None:
    faces = paint_part(draw, atlas, "rift_chest_plate", METAL, 151, 10, RIFT_BLACK)
    for side in ("top", "bottom"):
        face = faces[side]
        band(draw, face.x, face.y, face.width, face.height, METAL_LIT)
    for side in ("front", "back"):
        face = faces[side]
        for cx, cy in ((face.x + 1, face.y + 1), (face.right - 2, face.y + 1),
                       (face.x + 1, face.bottom - 2), (face.right - 2, face.bottom - 2)):
            dot(draw, cx, cy, METAL_LIT)
        cx, cy = face.x + face.width // 2, face.y + face.height // 2
        rune(draw, cx, cy, 2, RIFT_EMERALD_LIT)
        band(draw, cx - 1, cy - 1, 2, 2, RIFT_EMERALD)
        dot(draw, cx, cy, RIFT_VIOLET_LIT)


def _warden_back_spines(draw: ImageDraw.ImageDraw, atlas: layout.Atlas) -> None:
    faces = paint_part(draw, atlas, "rift_back_spines", RIFT_DEEP, 161, 8, RIFT_BLACK)
    for side in ("top", "bottom"):
        face = faces[side]
        band(draw, face.x, face.y, face.width, face.height, RIFT_BLACK)
    for side in ("front", "back"):
        face = faces[side]
        for column in range(1, face.width, 3):
            band(draw, face.x + column, face.y + 1, 1, face.height - 2, RIFT_VIOLET)
            dot(draw, face.x + column, face.y + 1, RIFT_GLOW)
            dot(draw, face.x + column, face.bottom - 2, RIFT_VIOLET_LIT)
    for side in ("left", "right"):
        face = faces[side]
        band(draw, face.x, face.y + 1, face.width, face.height - 2, RIFT_VIOLET_LIT)


def _warden_shard(draw: ImageDraw.ImageDraw, atlas: layout.Atlas, part: str,
                  seed: int) -> None:
    faces = paint_part(draw, atlas, part, RIFT_VIOLET, seed, 8, RIFT_BLACK)
    band(draw, faces["top"].x, faces["top"].y, faces["top"].width,
         faces["top"].height, RIFT_VIOLET_LIT)
    band(draw, faces["bottom"].x, faces["bottom"].y, faces["bottom"].width,
         faces["bottom"].height, RIFT_DEEP)
    for side in ("front", "back", "left", "right"):
        face = faces[side]
        # A single bright core seam marks which edge of the crystal is sharp.
        band(draw, face.x + face.width // 2, face.y + 1, 1, face.height - 3, RIFT_GLOW)
        band(draw, face.x + face.width // 2, face.bottom - 3, 1, 2, RIFT_EMERALD_LIT)


def _warden_core(draw: ImageDraw.ImageDraw, atlas: layout.Atlas) -> None:
    faces = paint_part(draw, atlas, "rift_core", RIFT_VIOLET_LIT, 171, 10, RIFT_BLACK)
    for side in ("front", "back"):
        face = faces[side]
        band(draw, face.x + 1, face.y + 1, face.width - 2, face.height - 2, RIFT_GLOW)
        band(draw, face.x + face.width // 2, face.y + 1, 1, face.height - 2, RIFT_EMERALD_LIT)
        band(draw, face.x + 1, face.y + face.height // 2, face.width - 2, 1, RIFT_EMERALD_LIT)
    for side in ("left", "right"):
        face = faces[side]
        band(draw, face.x, face.y + 1, face.width, face.height - 2, RIFT_GLOW)
    for side in ("top", "bottom"):
        face = faces[side]
        band(draw, face.x + 1, face.y, face.width - 2, face.height, RIFT_GLOW)


def build_warden() -> Image.Image:
    atlas = layout.warden_atlas()
    image = _canvas(atlas.width, atlas.height)
    _warden_hide(image)
    draw = ImageDraw.Draw(image)
    _warden_body_accents(draw)
    _warden_crown(draw, atlas)
    _warden_chest_plate(draw, atlas)
    _warden_back_spines(draw, atlas)
    _warden_shard(draw, atlas, "rift_shard_left", 181)
    _warden_shard(draw, atlas, "rift_shard_right", 191)
    _warden_core(draw, atlas)
    _assert_fully_painted(image, atlas)
    return image


# ---------------------------------------------------------------------------
# Archmage - custom rig UV layout (64x64), see Wave7ArchmageModel
# ---------------------------------------------------------------------------

def _archmage_robe_cloth(image: Image.Image) -> None:
    """Base robe cloth for the whole atlas."""
    draw = ImageDraw.Draw(image)
    for y in range(64):
        for x in range(64):
            n = _noise(17, x, y)
            shade = 0.80 + 0.20 * (1.0 - y / 63.0) + (n - 0.5) * 0.14
            draw.point((x, y), fill=_clamp(CLOTH, shade, int((n - 0.5) * 9)))
    # Woven folds, one texel wide, so a moving robe still reads as cloth.
    for x in range(2, 64, 4):
        for y in range(64):
            draw.point((x, y), fill=_clamp(CLOTH_LIT, 0.86))


def _archmage_head(draw: ImageDraw.ImageDraw, atlas: layout.Atlas) -> None:
    """The hood encases the head, so the readable face lives on the hood."""
    faces = paint_part(draw, atlas, "head", RIFT_DEEP, 201, 8, RIFT_BLACK)
    front = faces["front"]
    band(draw, front.x + 2, front.y + 4, 2, 1, RIFT_GLOW)
    band(draw, front.x + front.width - 4, front.y + 4, 2, 1, RIFT_GLOW)
    for side in ("top", "left", "right", "back"):
        face = faces[side]
        rim(draw, face.x, face.y, face.width, face.height, RIFT_BLACK)


def _archmage_hood(draw: ImageDraw.ImageDraw, atlas: layout.Atlas) -> None:
    faces = paint_part(draw, atlas, "rift_hood", CLOTH_LIT, 211, 9, RIFT_BLACK)
    band(draw, faces["top"].x, faces["top"].y, faces["top"].width,
         faces["top"].height, CLOTH)
    for side in ("front",):
        face = faces[side]
        # A void under the cowl with two violet eyes and an emerald rune slit.
        band(draw, face.x + 1, face.y + 2, face.width - 2, face.height - 5, RIFT_BLACK)
        for offset in (2, face.width - 4):
            band(draw, face.x + offset, face.y + 4, 2, 2, RIFT_GLOW)
            dot(draw, face.x + offset, face.y + 4, RIFT_VIOLET_LIT)
        band(draw, face.x + face.width // 2, face.y + 7, 1, 3, RIFT_EMERALD_LIT)
        # Cowl rim, so the hood keeps a silhouette against the arena floor.
        band(draw, face.x, face.y, face.width, 2, CLOTH_LIT)
        band(draw, face.x, face.bottom - 2, face.width, 2, RIFT_VIOLET)
        for px, py in ((face.x, face.bottom - 3), (face.right - 1, face.bottom - 3)):
            dot(draw, px, py, RIFT_VIOLET_LIT)
    for side in ("left", "right", "back"):
        face = faces[side]
        band(draw, face.x, face.y + 1, face.width, 2, CLOTH)
        band(draw, face.x, face.bottom - 1, face.width, 1, RIFT_VIOLET)
        band(draw, face.x + face.width // 2, face.y + 3, 1, face.height - 5, RIFT_VIOLET)
    band(draw, faces["bottom"].x, faces["bottom"].y, faces["bottom"].width,
         faces["bottom"].height, RIFT_DEEP)


def _archmage_hood_rune(draw: ImageDraw.ImageDraw, atlas: layout.Atlas) -> None:
    """Excluded from the runtime repack, so this glyph must be authored."""
    faces = paint_part(draw, atlas, "rift_hood_rune", RIFT_GLOW, 221, 6, RIFT_GLOW)
    front = faces["front"]
    band(draw, front.x, front.y, front.width, front.height, RIFT_GLOW)
    band(draw, front.x, front.y + 1, front.width, 1, RIFT_EMERALD_LIT)
    for side in ("top", "bottom", "left", "right", "back"):
        face = faces[side]
        band(draw, face.x, face.y, face.width, face.height, RIFT_GLOW)


def _archmage_nose(draw: ImageDraw.ImageDraw, atlas: layout.Atlas) -> None:
    paint_part(draw, atlas, "nose", RIFT_DEEP, 231, 6, RIFT_BLACK)


def _archmage_body(draw: ImageDraw.ImageDraw, atlas: layout.Atlas) -> None:
    faces = paint_part(draw, atlas, "body", CLOTH, 241, 10, RIFT_BLACK)
    band(draw, faces["top"].x, faces["top"].y, faces["top"].width,
         faces["top"].height, CLOTH_LIT)
    band(draw, faces["bottom"].x, faces["bottom"].y, faces["bottom"].width,
         faces["bottom"].height, RIFT_DEEP)
    for side in ("front", "back", "left", "right"):
        face = faces[side]
        for column in range(2, face.width, 3):
            band(draw, face.x + column, face.y + 1, 1, face.height - 2, CLOTH_LIT)
        band(draw, face.x, face.y + face.height // 3, face.width, 1, RIFT_VIOLET)
        band(draw, face.x, face.bottom - 2, face.width, 1, RIFT_DEEP)
    front = faces["front"]
    dot(draw, front.x + front.width // 2, front.y + front.height // 3, RIFT_EMERALD_LIT)


def _archmage_robe(draw: ImageDraw.ImageDraw, atlas: layout.Atlas) -> None:
    faces = paint_part(draw, atlas, "rift_robe", RIFT_VIOLET, 251, 10, RIFT_BLACK)
    band(draw, faces["top"].x, faces["top"].y, faces["top"].width,
         faces["top"].height, CLOTH)
    band(draw, faces["bottom"].x, faces["bottom"].y, faces["bottom"].width,
         faces["bottom"].height, RIFT_DEEP)
    for side in ("front", "back", "left", "right"):
        face = faces[side]
        for column in range(1, face.width, 2):
            band(draw, face.x + column, face.y, 1, face.height - 1, CLOTH_LIT)
        band(draw, face.x, face.bottom - 1, face.width, 1, RIFT_EMERALD)
        dot(draw, face.x + 1, face.bottom - 1, RIFT_EMERALD_LIT)
        dot(draw, face.right - 2, face.bottom - 1, RIFT_EMERALD_LIT)


def _archmage_seal(draw: ImageDraw.ImageDraw, atlas: layout.Atlas) -> None:
    faces = paint_part(draw, atlas, "rift_seal", RIFT_EMERALD, 261, 8, RIFT_BLACK)
    for side in ("front", "back"):
        face = faces[side]
        rune(draw, face.x + face.width // 2, face.y + face.height // 2, 1, RIFT_EMERALD_LIT)
        band(draw, face.x + 1, face.y + 1, face.width - 2, 1, RIFT_EMERALD_LIT)


def _archmage_arm(draw: ImageDraw.ImageDraw, atlas: layout.Atlas, part: str,
                  seed: int) -> None:
    faces = paint_part(draw, atlas, part, CLOTH_LIT, seed, 8, RIFT_BLACK)
    band(draw, faces["top"].x, faces["top"].y, faces["top"].width,
         faces["top"].height, CLOTH)
    band(draw, faces["bottom"].x, faces["bottom"].y, faces["bottom"].width,
         faces["bottom"].height, RIFT_BLACK)
    for side in ("front", "back", "left", "right"):
        face = faces[side]
        band(draw, face.x, face.y, face.width, 2, CLOTH)
        band(draw, face.x, face.bottom - 3, face.width, 2, RIFT_VIOLET)
        band(draw, face.x + face.width // 2, face.y + 3, 1, face.height - 7, CLOTH)


def _archmage_staff(draw: ImageDraw.ImageDraw, atlas: layout.Atlas) -> None:
    faces = paint_part(draw, atlas, "rift_focus_staff", BONE_DARK, 271, 6, RIFT_BLACK)
    for side in ("front", "back", "left", "right"):
        face = faces[side]
        band(draw, face.x, face.y, face.width, face.height, BONE_DARK)
        for row in range(1, face.height - 2, 4):
            band(draw, face.x, face.y + row, face.width, 1, RIFT_GLOW)
        band(draw, face.x, face.y, face.width, 2, METAL_LIT)
        band(draw, face.x, face.bottom - 3, face.width, 3, METAL)
    for side in ("top", "bottom"):
        face = faces[side]
        band(draw, face.x, face.y, face.width, face.height, METAL_LIT)


def _archmage_orb(draw: ImageDraw.ImageDraw, atlas: layout.Atlas) -> None:
    faces = paint_part(draw, atlas, "rift_focus_orb", RIFT_VIOLET_LIT, 281, 10, RIFT_BLACK)
    for side, face in faces.items():
        if face.width >= 3 and face.height >= 3:
            band(draw, face.x + 1, face.y + 1, face.width - 2, face.height - 2, RIFT_GLOW)
            band(draw, face.x + 1, face.y + 1, 1, 1, RIFT_EMERALD_LIT)
        else:
            band(draw, face.x, face.y, face.width, face.height, RIFT_GLOW)


def _archmage_sigil_ring(draw: ImageDraw.ImageDraw, atlas: layout.Atlas) -> None:
    """The only "a spell is coming" telegraph, scaled 3.4x while casting.

    ``EndRiftAtlasUvLayout`` skips this path on purpose, so nothing repaints it:
    before this pass the island was 0 % painted and the ring rendered as an
    invisible mesh. It is now an opaque dark disc carrying a bright emerald ring
    with four cardinal runes, and the 0.4-texel rim strips are a solid band --
    both the top/bottom plates and the rim stay readable at 3.4x.
    """
    faces = paint_part(draw, atlas, "rift_sigil_ring", RIFT_BLACK, 291, 6, RIFT_DEEP)
    for side in ("top", "bottom"):
        face = faces[side]
        size = min(face.width, face.height)
        inset = 1 if size >= 6 else 0
        for index in range(size - 2 * inset):
            if index in (0, size - 2 * inset - 1):
                band(draw, face.x + inset + index, face.y + inset, 1, size - 2 * inset,
                     RIFT_EMERALD_LIT)
                band(draw, face.x + inset, face.y + inset + index, size - 2 * inset, 1,
                     RIFT_EMERALD_LIT)
        mid = size // 2
        band(draw, face.x + mid - 1, face.y + mid - 1, 2, 2, RIFT_GLOW)
        # Cardinal runes sit on the plate's outer edge, outside the emerald ring.
        for offset in (mid - 1, mid):
            dot(draw, face.x + offset, face.y, RIFT_GLOW)
            dot(draw, face.x + offset, face.bottom - 1, RIFT_GLOW)
            dot(draw, face.x, face.y + offset, RIFT_GLOW)
            dot(draw, face.right - 1, face.y + offset, RIFT_GLOW)
    for side in ("front", "back", "left", "right"):
        face = faces[side]
        band(draw, face.x, face.y, face.width, face.height, RIFT_EMERALD)
        band(draw, face.x, face.y, face.width, 1, RIFT_GLOW)


def _archmage_rune_hand(draw: ImageDraw.ImageDraw, atlas: layout.Atlas) -> None:
    """A gloved hand with an emerald seal; also excluded from the repack."""
    faces = paint_part(draw, atlas, "rift_rune_hand", CLOTH_LIT, 301, 8, RIFT_BLACK)
    for side in ("top", "bottom"):
        face = faces[side]
        band(draw, face.x, face.y, face.width, face.height, CLOTH)
    for side in ("front", "back", "left", "right"):
        face = faces[side]
        band(draw, face.x, face.y, face.width, 1, BONE_DARK)
        band(draw, face.x + 1, face.y + 1, 1, 1, BONE_DARK)
        band(draw, face.right - 1, face.y + 1, 1, 1, BONE_DARK)
    front = faces["front"]
    band(draw, front.x + 1, front.y + 1, 1, 1, RIFT_EMERALD_LIT)


def _archmage_leg(draw: ImageDraw.ImageDraw, atlas: layout.Atlas, part: str,
                  seed: int) -> None:
    faces = paint_part(draw, atlas, part, CLOTH, seed, 8, RIFT_BLACK)
    band(draw, faces["top"].x, faces["top"].y, faces["top"].width,
         faces["top"].height, CLOTH_LIT)
    for side in ("front", "back", "left", "right"):
        face = faces[side]
        band(draw, face.x, face.y, face.width, 1, CLOTH_LIT)
        band(draw, face.x, face.bottom - 3, face.width, 3, RIFT_BLACK)
        band(draw, face.x, face.bottom - 4, face.width, 1, RIFT_VIOLET)
    band(draw, faces["bottom"].x, faces["bottom"].y, faces["bottom"].width,
         faces["bottom"].height, RIFT_BLACK)


def build_archmage() -> Image.Image:
    """Paint order resolves the rig's overlapping islands.

    ``rift_robe`` is painted after both arms so the robe's back face is not
    replaced by sleeve art; the hand, orb and sigil ring are painted last so the
    hero elements (and the two the runtime repack refuses to repair) always win.
    """
    atlas = layout.archmage_atlas()
    image = _canvas(atlas.width, atlas.height)
    _archmage_robe_cloth(image)
    draw = ImageDraw.Draw(image)
    _archmage_head(draw, atlas)
    _archmage_hood(draw, atlas)
    _archmage_hood_rune(draw, atlas)
    _archmage_nose(draw, atlas)
    _archmage_body(draw, atlas)
    _archmage_arm(draw, atlas, "right_arm", 311)
    _archmage_arm(draw, atlas, "left_arm", 321)
    _archmage_robe(draw, atlas)
    _archmage_seal(draw, atlas)
    _archmage_staff(draw, atlas)
    _archmage_leg(draw, atlas, "right_leg", 331)
    _archmage_leg(draw, atlas, "left_leg", 341)
    _archmage_rune_hand(draw, atlas)
    _archmage_orb(draw, atlas)
    _archmage_sigil_ring(draw, atlas)
    _assert_fully_painted(image, atlas)
    return image


# ---------------------------------------------------------------------------
# Marksman - skeleton atlas 64x32, same islands as RiftEventSkeletonModel
# ---------------------------------------------------------------------------

def _marksman_bone(image: Image.Image) -> None:
    draw = ImageDraw.Draw(image)
    for y in range(32):
        for x in range(64):
            n = _noise(27, x, y)
            shade = 0.82 + 0.18 * (1.0 - y / 31.0) + (n - 0.5) * 0.13
            draw.point((x, y), fill=_clamp(BONE_SHADE, shade, int((n - 0.5) * 10)))


def _marksman_head(draw: ImageDraw.ImageDraw, atlas: layout.Atlas) -> None:
    """Skull under a violet hood: the hood is painted on the head's own faces."""
    faces = paint_part(draw, atlas, "head", CLOTH, 401, 9, RIFT_BLACK)
    for side in ("top", "left", "right", "back"):
        face = faces[side]
        band(draw, face.x + 1, face.y + 1, face.width - 2, face.height - 2, CLOTH_LIT)
        band(draw, face.x, face.bottom - 2, face.width, 2, RIFT_VIOLET)
        band(draw, face.x + face.width // 2, face.y + 2, 1, face.height - 4, RIFT_VIOLET)
    band(draw, faces["bottom"].x, faces["bottom"].y, faces["bottom"].width,
         faces["bottom"].height, RIFT_BLACK)
    front = faces["front"]
    # Bone face inside a cloth frame, emerald sockets exactly on the front face.
    band(draw, front.x, front.y, front.width, front.height, BONE)
    band(draw, front.x, front.y, front.width, 1, CLOTH)
    band(draw, front.x, front.y, 1, front.height, CLOTH)
    band(draw, front.right - 1, front.y, 1, front.height, CLOTH)
    for offset in (2, front.width - 4):
        band(draw, front.x + offset, front.y + 3, 2, 2, RIFT_EMERALD_LIT)
        dot(draw, front.x + offset, front.y + 4, RIFT_EMERALD)
    band(draw, front.x + front.width // 2, front.y + 6, 1, 2, BONE_DARK)
    for column in range(2, front.width - 2, 2):
        dot(draw, front.x + column, front.bottom - 2, BONE_DARK)


def _marksman_body(draw: ImageDraw.ImageDraw, atlas: layout.Atlas) -> None:
    faces = paint_part(draw, atlas, "body", BONE, 411, 10, BONE_DARK)
    band(draw, faces["top"].x, faces["top"].y, faces["top"].width,
         faces["top"].height, BONE_SHADE)
    band(draw, faces["bottom"].x, faces["bottom"].y, faces["bottom"].width,
         faces["bottom"].height, BONE_DARK)
    for side in ("front", "back", "left", "right"):
        face = faces[side]
        for row in range(2, face.height - 2, 3):
            band(draw, face.x + 1, face.y + row, face.width - 2, 1, BONE_SHADE)
        band(draw, face.x, face.y + 1, face.width, 1, CLOTH_LIT)
        band(draw, face.x, face.bottom - 2, face.width, 1, RIFT_VIOLET)
        band(draw, face.x + face.width // 2, face.y + 2, 1, 2, RIFT_VIOLET_LIT)


def _marksman_chest_rift(draw: ImageDraw.ImageDraw, atlas: layout.Atlas) -> None:
    paint_part(draw, atlas, "chest_rift", RIFT_VIOLET_LIT, 421, 8, RIFT_BLACK)


def _marksman_limb(draw: ImageDraw.ImageDraw, atlas: layout.Atlas, part: str,
                   seed: int, wrap_at: str) -> None:
    faces = paint_part(draw, atlas, part, BONE, seed, 8, BONE_DARK)
    band(draw, faces["top"].x, faces["top"].y, faces["top"].width,
         faces["top"].height, BONE_SHADE)
    band(draw, faces["bottom"].x, faces["bottom"].y, faces["bottom"].width,
         faces["bottom"].height, BONE_SHADE)
    for side in ("front", "back", "left", "right"):
        face = faces[side]
        band(draw, face.x, face.y, face.width, 1, BONE_SHADE)
        band(draw, face.x + 1, face.y + 2, 1, max(1, face.height - 4), BONE_DARK)
        if wrap_at == "bottom":
            band(draw, face.x, face.bottom - 2, face.width, 2, CLOTH_LIT)
            dot(draw, face.x, face.bottom - 1, RIFT_EMERALD)
        else:
            band(draw, face.x, face.y + 1, face.width, 2, CLOTH_LIT)
            dot(draw, face.x, face.y + 1, RIFT_EMERALD)


def _marksman_sight(draw: ImageDraw.ImageDraw, atlas: layout.Atlas) -> None:
    faces = paint_part(draw, atlas, "marksman_sight", RIFT_VIOLET, 431, 8, RIFT_BLACK)
    for side in ("front", "back"):
        face = faces[side]
        band(draw, face.x, face.y, face.width, face.height, RIFT_VIOLET)
        band(draw, face.x + 1, face.y, face.width - 2, 1, RIFT_GLOW)
        dot(draw, face.x + face.width // 2, face.y + 1, RIFT_EMERALD_LIT)
    for side in ("top", "bottom", "left", "right"):
        face = faces[side]
        band(draw, face.x, face.y, face.width, face.height, RIFT_VIOLET_LIT)


def _marksman_quiver(draw: ImageDraw.ImageDraw, atlas: layout.Atlas) -> None:
    faces = paint_part(draw, atlas, "marksman_quiver", RIFT_DEEP, 441, 8, RIFT_BLACK)
    for side in ("front", "back", "left", "right"):
        face = faces[side]
        band(draw, face.x, face.y, face.width, face.height, RIFT_DEEP)
        for column in range(1, face.width, 2):
            band(draw, face.x + column, face.y + 1, 1, 2, BONE)
            dot(draw, face.x + column, face.y + 2, RIFT_EMERALD)
        band(draw, face.x, face.bottom - 2, face.width, 1, CLOTH)
    for side in ("top", "bottom"):
        face = faces[side]
        band(draw, face.x, face.y, face.width, face.height, RIFT_BLACK)


def _marksman_hat(draw: ImageDraw.ImageDraw, atlas: layout.Atlas) -> None:
    """Set invisible by the rig, but it still samples texels: keep it a hood."""
    faces = paint_part(draw, atlas, "hat", CLOTH, 451, 9)
    for side, face in faces.items():
        band(draw, face.x + 1, face.y + 1, face.width - 2, face.height - 2, CLOTH_LIT)
        band(draw, face.x, face.y, face.width, 2, RIFT_VIOLET)


def build_marksman() -> Image.Image:
    """Legs before arms, quiver last: its islands overlap both arm boxes."""
    atlas = layout.marksman_atlas()
    image = _canvas(atlas.width, atlas.height)
    _marksman_bone(image)
    draw = ImageDraw.Draw(image)
    _marksman_head(draw, atlas)
    _marksman_body(draw, atlas)
    _marksman_chest_rift(draw, atlas)
    _marksman_limb(draw, atlas, "left_leg", 501, "top")
    _marksman_limb(draw, atlas, "right_leg", 511, "top")
    _marksman_limb(draw, atlas, "left_lower_leg", 521, "bottom")
    _marksman_limb(draw, atlas, "right_lower_leg", 531, "bottom")
    _marksman_limb(draw, atlas, "left_arm", 461, "bottom")
    _marksman_limb(draw, atlas, "right_arm", 471, "bottom")
    _marksman_limb(draw, atlas, "left_forearm", 481, "bottom")
    _marksman_limb(draw, atlas, "right_forearm", 491, "bottom")
    _marksman_sight(draw, atlas)
    _marksman_quiver(draw, atlas)
    _marksman_hat(draw, atlas)
    _assert_fully_painted(image, atlas)
    return image


# ---------------------------------------------------------------------------
# Outputs
# ---------------------------------------------------------------------------

class ActorSkin(NamedTuple):
    name: str
    builder: Callable[[], Image.Image]


SKINS: tuple[ActorSkin, ...] = (
    ActorSkin("end_rift_wave7_warden.png", build_warden),
    ActorSkin("end_rift_wave7_archmage.png", build_archmage),
    ActorSkin("end_rift_wave7_marksman.png", build_marksman),
)


def output_paths() -> list[Path]:
    """Every file this generator writes; all of them live in the client root."""
    return [TEXTURE_ROOT / skin.name for skin in SKINS]


def generate(directory: Path | None = None) -> list[Path]:
    """Write every actor skin, returning the paths actually written."""
    target_root = TEXTURE_ROOT if directory is None else Path(directory)
    written: list[Path] = []
    for skin in SKINS:
        image = skin.builder()
        assert image.mode == "RGBA", skin.name
        target = target_root / skin.name
        target.parent.mkdir(parents=True, exist_ok=True)
        image.save(target, optimize=True)
        written.append(target)
        print("wrote", target, image.size)
    return written


def main() -> None:
    generate()


if __name__ == "__main__":
    main()