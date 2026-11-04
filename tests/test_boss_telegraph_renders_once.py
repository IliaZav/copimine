"""A boss cast must draw one tell, not two.

The authored signature and the legacy ring renderer are two different pictures
of the same wind-up. Both used to run for the same cast: the authored branch
drew its shapes and then fell through to the generic burst and the legacy
modules, so the shape the player was supposed to read arrived buried under a
second, differently shaped tell. A player cannot dodge what looks like noise.

These tests read the shipped source and pin the structure that makes the tells
mutually exclusive, so the mistake cannot quietly come back.
"""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MONOLITH = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "CopiMineEndEvent.java"


def _method_body(name: str) -> str:
    """Braces-balanced body of a method, so edits elsewhere cannot shift it."""
    source = MONOLITH.read_text(encoding="utf-8")
    # Anchor on the declaration: the name preceded by a return type and modifier,
    # never on the first call site that happens to mention it.
    declaration = re.search(rf"^\s*private [\w<>\[\], .]+\s{re.escape(name)}\s*\(", source, re.MULTILINE)
    assert declaration is not None, f"{name} declaration not found in {MONOLITH.name}"
    params_start = declaration.end() - 1
    depth = 0
    index = params_start
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
    for index in range(start, len(source)):
        char = source[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return source[start : index + 1]
    raise AssertionError(f"unbalanced braces in {name}")


def _telegraph_tick_block() -> str:
    body = _method_body("telegraphBossSpell")
    # The per-tick callback starts at the scheduler call and ends at the end of
    # the lambda; slicing there keeps this test independent of unrelated code.
    start = body.index("runTaskTimer")
    end = body.index("}, 1L, 1L);", start)
    block = body[start:end]
    assert "renderAuthoredBossTelegraph" in block and "renderSpellTelegraphVisual" in block, (
        "both renderers must live in the same per-tick callback for this check"
    )
    return block


def test_the_authored_signature_and_the_legacy_ring_never_both_run() -> None:
    block = _telegraph_tick_block()
    authored = block.index("renderAuthoredBossTelegraph")
    legacy = block.index("renderSpellTelegraphVisual")
    assert authored < legacy, "the authored branch should come first"
    between = block[authored:legacy]
    # The authored branch must hand control to the NEXT iteration rather than
    # falling through to the legacy renderer below it.
    assert re.search(r"\}\s*else\s*\{", between), (
        "the authored telegraph and renderSpellTelegraphVisual must sit in "
        "mutually exclusive branches, or a single cast draws two different tells"
    )


def test_the_generic_burst_belongs_to_the_legacy_path_only() -> None:
    block = _telegraph_tick_block()
    burst = block.index("spawnEventParticle(effect, particle, 12")
    legacy = block.index("renderSpellTelegraphVisual")
    authored = block.index("renderAuthoredBossTelegraph")
    assert burst > authored, (
        "the generic burst must not run before the authored branch, or it draws "
        "over the authored signature that replaced it"
    )
    assert burst < legacy, "the generic burst belongs with the legacy renderer"


def test_every_authored_boss_spell_still_has_a_signature_to_draw() -> None:
    # If an authored spell lost its composition the telegraph would silently
    # degrade to the fallback, which is exactly what this pass removed.
    policy = (
        ROOT
        / "copimine-end-event"
        / "src"
        / "me"
        / "copimine"
        / "endevent"
        / "domain"
        / "BossSpellVisualCompositionPolicy.java"
    ).read_text(encoding="utf-8")
    authored = len(re.findall(r"^\s*put\(result, \"", policy, re.MULTILINE))
    assert authored >= 10, f"only {authored} authored compositions remain"
