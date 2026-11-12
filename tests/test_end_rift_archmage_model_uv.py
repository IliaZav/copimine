"""Wave 7 Archmage model: every UV box must fit inside the texture sheet.

The Archmage inherits none of its geometry, so its UV coordinates are authored
by hand. `rift_sigil_ring` was placed at `texOffs(56, 44)` while an 8x0.4x8 cube
needs 32 units of UV width - it ran from u=56 to u=88 on a 64-wide sheet. The
ring therefore sampled wrapping texture data and rendered as a smear, which is
the "poor UV mapping" the user reported seeing on the Wave 7 minibosses.

This test parses the real source literals rather than compiling the class: the
model imports Minecraft client types, but its invariant lives entirely in
numbers, so a regular-expression read is both sufficient and stable.
"""

from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]
MODEL = (ROOT / "tools/minecraft-26.3/client/src/main/java/me/copimine/client"
         / "Wave7ArchmageModel.java")

BOX = re.compile(r"texOffs\((-?\d+),\s*(-?\d+)\)\s*\.addBox\(([^)]*)\)")


NUMBER = re.compile(r"-?\d+(?:\.\d+)?")


def java_floats(text):
    """The first six numbers of an addBox call: origin, then width/height/depth.

    Later arguments such as CubeDeformation are not geometry and are ignored.
    """
    found = [float(value) for value in NUMBER.findall(text)]
    return found[:6]


def sheet_size(source):
    match = re.search(r"LayerDefinition\.create\(mesh,\s*(\d+),\s*(\d+)\)", source)
    assert match, "the model must declare its texture sheet size"
    return int(match.group(1)), int(match.group(2))


def boxes(source):
    found = []
    for match in BOX.finditer(source):
        numbers = java_floats(match.group(3))
        assert len(numbers) >= 6, "addBox takes origin plus width/height/depth"
        u, v = int(match.group(1)), int(match.group(2))
        width, height, depth = numbers[3], numbers[4], numbers[5]
        found.append({
            "u": u,
            "v": v,
            "u1": u + 2 * (width + depth),
            "v1": v + (height + depth),
        })
    return found


def test_no_uv_box_leaves_the_sheet():
    source = MODEL.read_text(encoding="utf-8")
    width, height = sheet_size(source)
    found = boxes(source)
    assert len(found) >= 15, f"expected the authored parts, found {len(found)} boxes"

    overflowing = [box for box in found
                   if box["u1"] > width or box["v1"] > height
                   or box["u"] < 0 or box["v"] < 0]
    assert not overflowing, (
        f"UV region leaves the {width}x{height} sheet: {overflowing}. "
        "A box outside the sheet samples wrapped texture and renders as a smear."
    )


def test_the_sigil_ring_is_inside_the_sheet():
    """The specific defect: a 32-unit-wide ring starting at u=56."""
    source = MODEL.read_text(encoding="utf-8")
    width, _ = sheet_size(source)

    ring = re.search(
        r'rightArm\.addOrReplaceChild\("rift_sigil_ring",\s*'
        r'CubeListBuilder\.create\(\)\.texOffs\((\d+),\s*(\d+)\)\s*'
        r'\.addBox\(([^)]*)\)',
        source,
    )
    assert ring, "the sigil ring must still be authored on the right arm"

    numbers = java_floats(ring.group(3))
    u, v = int(ring.group(1)), int(ring.group(2))
    needed_width = 2 * (numbers[3] + numbers[5])
    assert u + needed_width <= width, (
        f"the sigil ring needs {needed_width} units of UV width but only "
        f"{width - u} remain after u={u}"
    )
    assert v + (numbers[4] + numbers[5]) <= sheet_size(source)[1], (
        "the sigil ring must fit vertically too"
    )


def test_the_model_still_has_every_authored_part():
    """The fix must move a UV offset, never quietly delete a body part."""
    source = MODEL.read_text(encoding="utf-8")
    required = [
        "head", "rift_hood", "rift_hood_rune", "nose",
        "body", "rift_robe", "rift_seal",
        "right_arm", "rift_focus_staff", "rift_focus_orb", "rift_sigil_ring",
        "left_arm", "rift_rune_hand",
        "right_leg", "left_leg",
    ]
    for part in required:
        assert f'"{part}"' in source, f"the Archmage lost its {part} part"
