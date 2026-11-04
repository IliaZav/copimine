"""A boss cue must be one picture per stage, and the cue table must be authored.

Two separate defects live behind these tests.

The first is double drawing. Every boss spell already renders an authored
signature (a converging vortex, an expanding shell) and then the cue renderer
drew a second, shared picture on top of it: a generic particle puff, a beam
whose colour was a hard-coded hex that matched no spell, and an END_ROD ring on
impact. The tell a player had to read arrived buried in noise.

The second is the table itself. Cue sounds and particles came from shared
arrays indexed by position so adding one spell silently reassigned every later
spell's sound, and the particle arrays wrapped, meaning different spells could
share a primary particle. On top of that every stage of a spell produced the
same animation id, so the client could not tell a windup from a hit.
"""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MONOLITH = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "CopiMineEndEvent.java"
POLICY = (
    ROOT
    / "copimine-end-event"
    / "src"
    / "me"
    / "copimine"
    / "endevent"
    / "domain"
    / "BossVisualCuePolicy.java"
)


def _method_body(name: str) -> str:
    source = MONOLITH.read_text(encoding="utf-8")
    declaration = re.search(rf"^\s*private [\w<>\[\], .]+\s{re.escape(name)}\s*\(", source, re.MULTILINE)
    assert declaration is not None, f"{name} declaration not found"
    depth = 0
    index = declaration.end() - 1
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


def test_a_spell_with_an_authored_signature_is_not_drawn_twice() -> None:
    body = _method_body("playBossVisualCue")
    assert "isAuthored(" in body, (
        "playBossVisualCue must consult the authored composition before drawing, "
        "or it paints a second tell over the authored signature"
    )
    # The viewer loop is where a second picture would be drawn.
    loop = body.index("for (Player viewer : eventAudience())")
    render = body.index("renderBossVisualCue(viewer", loop)
    guard = body.rindex("authoredSpell", loop, render)
    assert loop < guard < render, "the authored-spell guard must run inside the loop, before the renderer"
    # Braces of the guard block follow the condition on the SAME line, so the
    # pattern must not require a newline between them.
    assert re.search(r"if\s*\(\s*authoredSpell\s*\)\s*\{[^}]*\bcontinue;", body[guard - len("if ("):]), (
        "an authored spell must skip the generic cue renderer for that viewer"
    )


def test_the_cue_renderer_never_resolves_the_stage_out_of_its_own_id() -> None:
    body = _method_body("renderBossVisualCue")
    assert "bossCueStage(cue.id())" not in body, (
        "the stage is an input, not something to re-parse out of the cue id: "
        "re-parsing lets the caller draw a stage it did not mean"
    )
    assert "switch (stage)" in body, "the renderer must branch on the stage it was given"


def test_no_cue_beam_colour_is_a_hard_coded_hex() -> None:
    body = _method_body("renderBossVisualCue")
    literals = re.findall(r"0x[0-9A-Fa-f]{6}", body)
    assert not literals, f"cue beam colours must come from the spell palette, found {literals}"


def test_the_cue_table_contains_no_index_arithmetic() -> None:
    policy = POLICY.read_text(encoding="utf-8")
    forbidden = [
        "spellIndex * CueStage.values().length",
        "spellIndex % primaryParticles.length",
        "(spellIndex + stageIndex) % accentParticles.length",
        "String[] sounds = {",
        "String[] primaryParticles = {",
        "String[] accentParticles = {",
    ]
    for needle in forbidden:
        assert needle not in policy, f"index-based cue authoring came back: {needle}"


def test_every_cue_spell_has_its_own_authored_bank() -> None:
    policy = POLICY.read_text(encoding="utf-8")
    spells = re.findall(r'^\s*"(\w+)",?$', policy, re.MULTILINE)
    banks = re.findall(r'banks\.put\("(\w+)", new Bank\(', policy)
    assert banks, "the authored bank table is missing"
    missing = [spell for spell in spells if spell not in banks]
    assert not missing, f"spells without an authored cue bank: {missing}"
    assert len(set(banks)) == len(banks), "a cue bank is declared twice"


def test_cue_animations_name_both_the_spell_and_the_stage() -> None:
    # Strip comments first: the class documents the old build inside a javadoc
    # block, and the quote there must not defeat the check.
    policy = re.sub(r"/\*.*?\*/", "", POLICY.read_text(encoding="utf-8"), flags=re.DOTALL)
    assert 'baseId + ":animation"' not in policy, (
        "the animation id must carry the spell and the stage; the old "
        "build gave all three stages of a spell the same pose"
    )
    assert "spellAnimation(spell, stage)" in policy, "the catalog must resolve the animation per stage"
