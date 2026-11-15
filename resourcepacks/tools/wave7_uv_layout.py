"""The real UV footprint of every Wave 7 miniboss box, derived from the rigs.

Why this module exists
---------------------
A Minecraft cuboid does not occupy "one rectangle" of an atlas.  ``addBox`` at
``texOffs(u, v)`` with size ``(width, height, depth)`` unwraps into **six** face
rectangles and spans ``2*(width + depth)`` texels wide by ``depth + height``
texels tall::

    u          u+w       u+2w              u+2w+2d
    v  +---------+---------+-----------------+   width x depth strips
       |   top   | bottom  |      unused     |
    v+d+---------+---------+---------+-------+   height strips
       |  right  |  front  |  left   | back  |
       +---------+---------+---------+-------+
             d         w         d        w

The unused corner exists only when ``width != depth``.  Painting "roughly where
the part sits" therefore leaves most faces sampling transparent texels, which is
exactly how the shipped Wave 7 atlases ended up with invisible armour, an
invisible Archmage sigil ring and transparent skeleton legs.

Two consumers must agree on this geometry or the defect returns immediately:

* ``resourcepacks/tools/generate_wave7_actor_skins.py`` paints with it;
* ``tests/test_wave7_actor_skin_coverage.py`` measures with it.

So the footprint maths live here once and the rigs are parsed, never re-typed.
The parser reads the same ``texOffs``/``addBox`` calls the client bakes, so a rig
edit that moves a part also moves the painted island.
"""
from __future__ import annotations

import math
import re
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CLIENT_SOURCE = (
    ROOT / "tools" / "minecraft-26.3" / "client" / "src" / "main" / "java"
    / "me" / "copimine" / "client"
)

WARDEN_MODEL = CLIENT_SOURCE / "Wave7WardenModel.java"
ARCHMAGE_MODEL = CLIENT_SOURCE / "Wave7ArchmageModel.java"
SKELETON_MODEL = CLIENT_SOURCE / "RiftEventSkeletonModel.java"

# Face order follows the vanilla unwrap above; the names are only for reports.
FACE_SIDES = ("top", "bottom", "right", "front", "left", "back")


@dataclass(frozen=True)
class Box:
    """One baked cuboid: its part name and its declared atlas allocation."""

    part: str
    u: float
    v: float
    width: float
    height: float
    depth: float
    inherited: bool = False


@dataclass(frozen=True)
class Face:
    """One of the six texel rectangles a box samples, in atlas texels."""

    part: str
    side: str
    x: int
    y: int
    width: int
    height: int
    inherited: bool = False

    @property
    def right(self) -> int:
        return self.x + self.width

    @property
    def bottom(self) -> int:
        return self.y + self.height

    @property
    def area(self) -> int:
        return self.width * self.height


@dataclass(frozen=True)
class Atlas:
    """One miniboss texture, the boxes that sample it, and its dimensions."""

    name: str
    width: int
    height: int
    boxes: tuple[Box, ...]
    source: Path

    def faces(self) -> tuple[Face, ...]:
        return tuple(
            face
            for box in self.boxes
            for face in face_rects(box, self.width, self.height)
        )

    def boxes_named(self, part: str) -> tuple[Box, ...]:
        return tuple(entry for entry in self.boxes if entry.part == part)

    def faces_named(self, part: str) -> tuple[Face, ...]:
        return tuple(face for face in self.faces() if face.part == part)


# ---------------------------------------------------------------------------
# Footprint maths
# ---------------------------------------------------------------------------

def java_round(value: float) -> int:
    """``Math.round`` halves up; Python's ``round`` rounds halves to even."""
    return int(math.floor(value + 0.5))


def face_rects(box: Box, atlas_width: int, atlas_height: int) -> tuple[Face, ...]:
    """The six texel rectangles ``EndRiftAtlasUvLayout.fit`` inspects.

    The rounding mirrors that class deliberately: it floors each face origin and
    rounds each extent (clamped to one texel), so the fraction reported here is
    the same fraction the runtime uses to decide whether to repack a face.
    """
    u, v, w, h, d = box.u, box.v, box.width, box.height, box.depth
    raw = (
        ("top", u, v, w, d),
        ("bottom", u + w, v, w, d),
        ("right", u, v + d, d, h),
        ("front", u + d, v + d, w, h),
        ("left", u + d + w, v + d, d, h),
        ("back", u + d + w + d, v + d, w, h),
    )
    return tuple(
        Face(
            part=box.part,
            side=side,
            x=int(math.floor(u0)),
            y=int(math.floor(v0)),
            width=max(1, java_round(fw)),
            height=max(1, java_round(fh)),
            inherited=box.inherited,
        )
        for side, u0, v0, fw, fh in raw
    )


def footprint(box: Box) -> tuple[int, int]:
    """The ``ModelUvBounds.requireStandardBoxFits`` bounds, in texels."""
    return (
        java_round(2.0 * (box.width + box.depth)),
        java_round(box.depth + box.height),
    )


def allocation(part_faces: tuple[Face, ...]) -> tuple[int, int, int, int]:
    """Bounding box of one part's six face rectangles."""
    return (
        min(face.x for face in part_faces),
        min(face.y for face in part_faces),
        max(face.right for face in part_faces),
        max(face.bottom for face in part_faces),
    )


def overlapping_pairs(atlas: Atlas) -> list[tuple[str, str, tuple[int, int, int, int]]]:
    """Parts whose *face* rectangles actually share texels.

    Allocation bounding boxes overlap all the time (a 12-pixel-tall part and the
    one above it touch), so this compares the six real face rectangles instead.
    Where two faces do share texels the painter has to choose an order: both
    parts sample the same pixels and only one design can sit there.
    """
    found: list[tuple[str, str, tuple[int, int, int, int]]] = []
    faces = list(atlas.faces())
    for index, first in enumerate(faces):
        for second in faces[index + 1:]:
            if first.part == second.part:
                continue
            left = max(first.x, second.x)
            top = max(first.y, second.y)
            right = min(first.right, second.right)
            bottom = min(first.bottom, second.bottom)
            if left < right and top < bottom:
                found.append((first.part, second.part, (left, top, right, bottom)))
    return found


# ---------------------------------------------------------------------------
# Model parsing
# ---------------------------------------------------------------------------

def _matching_paren(text: str, open_index: int) -> int:
    """Index of the ``)`` that closes the ``(`` at ``open_index``."""
    depth = 0
    for index in range(open_index, len(text)):
        char = text[index]
        if char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0:
                return index
    raise ValueError("unbalanced parentheses in model source")


def _split_arguments(argument_text: str) -> list[str]:
    """Split a Java argument list on top-level commas."""
    parts: list[str] = []
    depth = 0
    current: list[str] = []
    for char in argument_text:
        if char in "([{":
            depth += 1
        elif char in ")]}":
            depth -= 1
        if char == "," and depth == 0:
            parts.append("".join(current).strip())
            current = []
        else:
            current.append(char)
    tail = "".join(current).strip()
    if tail:
        parts.append(tail)
    return parts


def _call_arguments(text: str, call: str) -> list[list[str]]:
    """Every top-level argument list of ``call`` inside ``text``."""
    found = []
    for match in re.finditer(re.escape(call) + r"\(", text):
        open_index = match.end() - 1
        close_index = _matching_paren(text, open_index)
        found.append(_split_arguments(text[open_index + 1:close_index]))
    return found


def _number(token: str, symbols: dict[str, float]) -> float:
    token = re.sub(r"[fFdDlL]$", "", token.strip())
    try:
        return float(token)
    except ValueError:
        if token in symbols:
            return float(symbols[token])
        raise KeyError(f"unresolved model symbol {token!r}") from None


def _atlas_dimension_token(token: str) -> bool:
    return re.fullmatch(r"TEXTURE_\w+", token.strip()) is not None


def _uv_and_size(arguments: list[str], symbols: dict[str, float]) -> tuple[float, ...]:
    """``(u, v, width, height, depth)`` from a cuboid/box argument list.

    ``ModelUvBounds.cuboid`` begins with the atlas size and ``cube`` does not;
    stripping the ``TEXTURE_*`` prefix makes both the same eight-slot tuple
    ``(u, v, x, y, z, width, height, depth)``, with an optional trailing
    ``mirrored`` flag that never affects the footprint.
    """
    tokens = [token for token in arguments if not _atlas_dimension_token(token)]
    numbers = [_number(token, symbols) for token in tokens[:8]]
    if len(numbers) < 8:
        raise ValueError(f"incomplete cuboid arguments {arguments!r}")
    return numbers[0], numbers[1], numbers[5], numbers[6], numbers[7]


def _resolve_symbols(source: str, variant_branch: str) -> dict[str, float]:
    """Resolve the variant ternaries for the branch the Marksman actually takes.

    ``RiftEventSkeletonModel`` picks its islands with ``userSkin ? a : b``
    expressions.  Reading the first branch keeps the numbers out of this file, so
    they cannot be typed once and then silently diverge from the rig.
    """
    symbols: dict[str, float] = {}
    for name, first in re.findall(
        rf"int\s+(\w+)\s*=\s*{variant_branch}\s*\?\s*(\d+)\s*:", source
    ):
        symbols[name] = float(first)
    for name, left, offset in re.findall(r"int\s+(\w+)\s*=\s*(\w+)\s*\+\s*(\d+);", source):
        if left in symbols:
            symbols[name] = symbols[left] + float(offset)
    for name, value in re.findall(r"int\s+(\w+)\s*=\s*(\d+);", source):
        symbols.setdefault(name, float(value))
    return symbols


def _parse_builder_boxes(part: str, expression: str,
                         symbols: dict[str, float]) -> list[Box]:
    """Turn one ``addOrReplaceChild`` builder argument into boxes."""
    boxes: list[Box] = []
    for arguments in _call_arguments(expression, "new ModelUvBounds.Box"):
        u, v, width, height, depth = _uv_and_size(arguments, symbols)
        boxes.append(Box(part, u, v, width, height, depth))
    if boxes:
        return boxes

    for arguments in _call_arguments(expression, "ModelUvBounds.boxes"):
        for token in arguments:
            if not token.strip().startswith("new ModelUvBounds.Box"):
                continue
            inner = _split_arguments(token[token.index("(") + 1:token.rindex(")")])
            u, v, width, height, depth = _uv_and_size(inner, symbols)
            boxes.append(Box(part, u, v, width, height, depth))
        if boxes:
            return boxes

    offsets = _call_arguments(expression, ".texOffs")
    if offsets:
        u, v = (_number(token, symbols) for token in offsets[0][:2])
        add_boxes = _call_arguments(expression, ".addBox")
        if not add_boxes:
            raise ValueError(f"{part}: texOffs without addBox")
        numbers = [_number(token, symbols) for token in add_boxes[0][:6]]
        return [Box(part, u, v, numbers[3], numbers[4], numbers[5])]

    for call in ("ModelUvBounds.cuboid", "cube"):
        for arguments in _call_arguments(expression, call):
            u, v, width, height, depth = _uv_and_size(arguments, symbols)
            return [Box(part, u, v, width, height, depth)]
    raise ValueError(f"could not derive a box for part {part!r}")


def _parse_declared_parts(source: str, symbols: dict[str, float]) -> list[Box]:
    """Every ``addOrReplaceChild("name", <builder>, ...)`` in one rig file."""
    boxes: list[Box] = []
    for match in re.finditer(r'addOrReplaceChild\(\s*"(\w+)"\s*,', source):
        part = match.group(1)
        open_index = source.index("(", match.start())
        close_index = _matching_paren(source, open_index)
        for argument in _split_arguments(source[open_index + 1:close_index])[1:]:
            if ("CubeListBuilder" not in argument
                    and "ModelUvBounds" not in argument
                    and not argument.startswith("cube(")):
                continue
            boxes.extend(_parse_builder_boxes(part, argument, symbols))
            break
    return boxes


def _method_body(source: str, signature: str) -> str:
    """The braced body of the first method matching ``signature``."""
    match = re.search(signature, source)
    if match is None:
        return ""
    open_brace = source.index("{", match.end())
    depth = 0
    for index in range(open_brace, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[open_brace:index]
    return ""


def _parse_biped_helper_boxes(source: str, helper: str,
                              symbols: dict[str, float]) -> list[Box]:
    """Materialise ``addBipedSkeletonArm``/``addBipedSkeletonLeg`` call sites.

    Both helpers build one upper half and one lower half at the same ``u`` with
    ``v`` advanced by six rows, so the twelve-pixel arm/leg island is covered by
    two six-pixel boxes instead of one.
    """
    body = _method_body(source, rf"static void {helper}\(")
    if not body:
        return []
    local = dict(symbols)
    for name, first in re.findall(r"int\s+(\w+)\s*=\s*userSkin\s*\?\s*(\d+)\s*:", body):
        local[name] = float(first)
    for name, left, offset in re.findall(r"int\s+(\w+)\s*=\s*(\w+)\s*\+\s*(\d+);", body):
        if left in local:
            local[name] = local[left] + float(offset)
    for name, value in re.findall(r"int\s+(\w+)\s*=\s*(\d+);", body):
        local.setdefault(name, float(value))

    halves = _call_arguments(body, "ModelUvBounds.cuboid")
    if len(halves) < 2:
        return []

    boxes: list[Box] = []
    for match in re.finditer(rf'{helper}\(root,\s*"(\w+)",\s*"(\w+)",', source):
        for part, arguments in zip(match.groups(), halves[:2]):
            u, v, width, height, depth = _uv_and_size(arguments, local)
            boxes.append(Box(part, u, v, width, height, depth))
    return boxes


# ``HumanoidModel.createMesh`` lives in the pinned 26.3 client jar, not in this
# tree, so these two allocations cannot be parsed.  They are the well-known
# vanilla humanoid head islands, listed only so the report is complete; ``hat``
# is set invisible by the Marksman rig.
INHERITED_HUMANOID_BOXES = (
    Box("head", 0, 0, 8, 8, 8, inherited=True),
    Box("hat", 32, 0, 8, 8, 8, inherited=True),
)


def warden_atlas() -> Atlas:
    """The authored add-ons: the vanilla Warden body comes from the client jar."""
    source = WARDEN_MODEL.read_text(encoding="utf-8")
    return Atlas("end_rift_wave7_warden.png", 128, 128,
                 tuple(_parse_declared_parts(source, {})), WARDEN_MODEL)


def archmage_atlas() -> Atlas:
    source = ARCHMAGE_MODEL.read_text(encoding="utf-8")
    return Atlas("end_rift_wave7_archmage.png", 64, 64,
                 tuple(_parse_declared_parts(source, {})), ARCHMAGE_MODEL)


def marksman_atlas() -> Atlas:
    source = SKELETON_MODEL.read_text(encoding="utf-8")
    symbols = _resolve_symbols(source, "userSkin")
    boxes = _parse_declared_parts(source, symbols)
    boxes.extend(_parse_biped_helper_boxes(source, "addBipedSkeletonArm", symbols))
    boxes.extend(_parse_biped_helper_boxes(source, "addBipedSkeletonLeg", symbols))
    boxes.extend(INHERITED_HUMANOID_BOXES)
    return Atlas("end_rift_wave7_marksman.png", 64, 32, tuple(boxes), SKELETON_MODEL)


def atlases() -> dict[str, Atlas]:
    """The three Wave 7 miniboss atlases keyed by their shipped file name."""
    return {
        atlas.name: atlas
        for atlas in (warden_atlas(), archmage_atlas(), marksman_atlas())
    }