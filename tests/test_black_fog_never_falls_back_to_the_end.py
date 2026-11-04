"""The fog packet must never name a dimension the arena does not live in.

The contract is explicit: the arena may move to any Overworld world, and the
event may never silently fall back to the End. The fog packet used to fill in
"the_end" whenever the Core anchor was missing, so a restarted or partially
loaded event told every client that the fog belonged to a world nobody was
standing in.

The honest answer to "where is the fog" with no anchor is "nowhere": the packet
carries the CLEAR state instead of a guessed dimension.
"""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MONOLITH = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "CopiMineEndEvent.java"


def _method_body(name: str) -> str:
    source = MONOLITH.read_text(encoding="utf-8")
    declaration = re.search(
        rf"^\s*private [\w<>\[\], .]+\s{re.escape(name)}\s*\(", source, re.MULTILINE
    )
    assert declaration is not None, f"{name} declaration not found"
    index = declaration.end() - 1
    depth = 0
    while index < len(source):
        if source[index] == "(":
            depth += 1
        elif source[index] == ")":
            depth -= 1
            if depth == 0:
                break
        index += 1
    start = source.index("{", index)
    depth = 0
    for position in range(start, len(source)):
        if source[position] == "{":
            depth += 1
        elif source[position] == "}":
            depth -= 1
            if depth == 0:
                return source[start : position + 1]
    raise AssertionError(f"unbalanced braces in {name}")


def test_the_fog_packet_does_not_invent_a_dimension() -> None:
    body = _method_body("sendCurrentBlackFogPacket")
    assert "the_end" not in body, (
        "the fog packet must not fall back to the_end when the anchor is missing"
    )


def test_no_anchor_means_the_fog_is_reported_as_cleared() -> None:
    body = _method_body("sendCurrentBlackFogPacket")
    missing_anchor = re.search(r"if \(core == null\) \{(.*?)\}", body, re.DOTALL)
    assert missing_anchor is not None, "the missing-anchor case must be handled explicitly"
    assert "active = false" in missing_anchor.group(1), (
        "with no anchor the packet must report CLEAR rather than an ACTIVE fog"
    )
