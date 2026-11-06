"""The Wave 7 Warden's rift armour must occupy its own texture space.

Complaint (11) is that the Warden must emerge beautifully with its own animation. The rig
adds six parts on top of the vanilla Warden (crown, chest plate, back spines, two shoulder
shards, chest core). If two of those parts claim overlapping texOffs regions, or one lands
outside the 128x128 Warden sheet, the armour renders as smeared garbage - and nothing in the
build would notice, because Wave7WardenModel has no behavioural test.

The source is parsed rather than compiled: the class extends the vanilla WardenModel and
cannot be built without the Minecraft client jar, but the geometry is plain literals.
"""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODEL = (
    ROOT
    / "tools/minecraft-26.3/client/src/main/java/me/copimine/client/Wave7WardenModel.java"
)

# Mojang's Warden texture sheet is 128x128.
SHEET = 128

# A part is: addOrReplaceChild("<name>", CubeListBuilder.create().texOffs(U, V)
#                                 .addBox(X, Y, Z, W, H, D), ...)
PART_RE = re.compile(
    r'addOrReplaceChild\("(?P<name>rift_[a-z_]+)"'
    r'.*?texOffs\(\s*(?P<u>-?\d+)\s*,\s*(?P<v>-?\d+)\s*\)'
    r'.*?addBox\(\s*(?P<x>-?[\d.]+)F\s*,\s*(?P<y>-?[\d.]+)F\s*,\s*(?P<z>-?[\d.]+)F'
    r'\s*,\s*(?P<w>[\d.]+)F\s*,\s*(?P<h>[\d.]+)F\s*,\s*(?P<d>[\d.]+)F\s*\)',
    re.DOTALL,
)

# The six parts the model constructor looks up by name; a rename would throw at bake time.
REQUIRED_PARTS = {
    "rift_crown",
    "rift_chest_plate",
    "rift_back_spines",
    "rift_shard_left",
    "rift_shard_right",
    "rift_core",
}


def _source() -> str:
    assert MODEL.is_file(), f"warden model source is missing: {MODEL}"
    return MODEL.read_text(encoding="utf-8")


def _parts() -> list[dict]:
    source = _source()
    parts = []
    for match in PART_RE.finditer(source):
        w, h, d = (float(match.group(k)) for k in ("w", "h", "d"))
        u, v = int(match.group("u")), int(match.group("v"))
        parts.append(
            {
                "name": match.group("name"),
                "u": u,
                "v": v,
                # A box texture uses 2*(w+d) horizontally and (h+d) vertically.
                "u_end": u + int(round(2 * (w + d))),
                "v_end": v + int(round(h + d)),
                "x": float(match.group("x")),
                "y": float(match.group("y")),
                "z": float(match.group("z")),
                "w": w,
                "h": h,
                "d": d,
            }
        )
    return parts


def test_every_required_part_is_defined_exactly_once() -> None:
    names = [part["name"] for part in _parts()]
    assert names, "no rift parts parsed from Wave7WardenModel"
    assert len(names) == len(set(names)), f"duplicate part names: {names}"
    assert set(names) == REQUIRED_PARTS, (
        "the constructor reads a part the geometry never defines (or vice versa): "
        f"defined={sorted(set(names))} required={sorted(REQUIRED_PARTS)}"
    )


def test_parts_fit_inside_the_warden_sheet() -> None:
    for part in _parts():
        assert part["u"] >= 0 and part["v"] >= 0, f"{part['name']} has a negative texOffs"
        assert part["u_end"] <= SHEET, (
            f"{part['name']} runs off the {SHEET}px sheet horizontally: "
            f"u={part['u']} needs {part['u_end']}"
        )
        assert part["v_end"] <= SHEET, (
            f"{part['name']} runs off the {SHEET}px sheet vertically: "
            f"v={part['v']} needs {part['v_end']}"
        )


def test_no_two_parts_share_texture_space() -> None:
    parts = _parts()
    for index, first in enumerate(parts):
        for second in parts[index + 1 :]:
            overlaps = (
                first["u"] < second["u_end"]
                and second["u"] < first["u_end"]
                and first["v"] < second["v_end"]
                and second["v"] < first["v_end"]
            )
            assert not overlaps, (
                f"{first['name']} and {second['name']} claim the same texture region "
                f"({first['u']},{first['v']})-({first['u_end']},{first['v_end']}) vs "
                f"({second['u']},{second['v']})-({second['u_end']},{second['v_end']}); "
                "the armour would render as smeared garbage"
            )


def test_armour_is_parented_to_the_breathing_body() -> None:
    """Parts must hang off body/head, not the root, or they freeze while the Warden turns."""
    source = _source()
    assert 'body.getChild("head").addOrReplaceChild("rift_crown"' in source, (
        "the crown must be a child of the head so it follows head tracking"
    )
    for name in REQUIRED_PARTS - {"rift_crown"}:
        assert f'body.addOrReplaceChild("{name}"' in source, (
            f"{name} must be parented to the body so it inherits the Warden's breathing"
        )


def test_shards_are_mirrored_and_angled_outward() -> None:
    parts = {part["name"]: part for part in _parts()}
    left, right = parts["rift_shard_left"], parts["rift_shard_right"]
    assert left["x"] > 0 > right["x"], (
        "the shoulder shards must sit on opposite sides: "
        f"left.x={left['x']} right.x={right['x']}"
    )
    assert (left["w"], left["h"], left["d"]) == (right["w"], right["h"], right["d"]), (
        "the shoulder shards must be the same size or the mantle looks lopsided"
    )
    source = _source()
    # Each shard is tilted outward; equal-and-opposite zRot is what makes the fan symmetric.
    assert "-0.10F" in source and "0.10F" in source, (
        "the shards must start tilted outward with opposite zRot"
    )


def test_additions_are_small_enough_to_read_as_armour() -> None:
    """A part bigger than the torso would stop reading as something bolted on."""
    for part in _parts():
        assert part["w"] <= 12 and part["h"] <= 12 and part["d"] <= 4, (
            f"{part['name']} is too bulky to read as armour on the Warden: "
            f"{part['w']}x{part['h']}x{part['d']}"
        )
