"""Section 9L wants per-ability visual acceptance assets for the V5.1 casts.

A native F2 capture from a real client is BLOCKED on this host, so the honest
substitute is proof of what the shipped Java actually draws.  ``scripts/
minecraft/RenderSpellVisualPreviews.py`` compiles the real ``SpellVisualGeometry``
source, samples the authored mesh across the seven authored semantic cast keys and
emits one isometric SVG sheet per token.  This test runs its ``--check`` mode, so a
geometry edit can never silently leave the committed sheets describing an older
mesh, and it pins the contract that makes the sheets usable as evidence:

* exactly one sheet per authored token, no orphans and no missing art,
* every sheet shows the seven authored semantic cast keys, in order,
* every sheet is a real, non-trivial SVG rather than a placeholder,
* every sheet says out loud that it is source-derived and not a screenshot,
* the generator itself stays in the repository, so the sheets are reproducible.
"""
from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/minecraft/RenderSpellVisualPreviews.py"
GEOMETRY = ROOT / "tools/minecraft-26.3/client/src/main/java/me/copimine/client/SpellVisualGeometry.java"
SHEETS = ROOT / "docs/end-rift/2026-11-23/spell_previews"


def _authored_tokens() -> list[str]:
    """The cast tokens exactly as the shipped enum spells them."""
    text = GEOMETRY.read_text(encoding="utf-8")
    return re.findall(r'^\s+[A-Z_]+\("([a-z]+)"\)', text, flags=re.MULTILINE)


def test_the_generator_still_describes_the_shipped_geometry(tmp_path: Path) -> None:
    """Re-render and compare: a mesh edit must never leave stale preview art."""
    assert SCRIPT.is_file(), "the reproducible preview generator must live in-repo"
    assert GEOMETRY.is_file(), "the authored spell geometry source must exist"
    assert SHEETS.is_dir(), "the committed preview sheets must exist"

    tokens = _authored_tokens()
    assert len(tokens) == 12, f"expected 12 authored casts, found {tokens}"

    result = subprocess.run(
        [sys.executable, str(SCRIPT), "--check"],
        capture_output=True, text=True, timeout=900,
    )
    combined = result.stdout + result.stderr
    assert result.returncode == 0, "committed preview sheets are stale:\n" + combined
    assert f"{len(tokens)} authored preview sheets match" in result.stdout, combined


def test_every_authored_cast_has_a_labelled_preview_sheet() -> None:
    """One sheet per token, visibly source-derived, never a fake screenshot."""
    tokens = _authored_tokens()
    assert len(tokens) == 12, f"expected 12 authored casts, found {tokens}"
    present = {path.stem for path in SHEETS.glob("*.svg")}
    assert present == set(tokens), (
        "the preview directory and the Spell roster drifted: "
        f"only-on-disk {sorted(present - set(tokens))}, "
        f"only-in-code {sorted(set(tokens) - present)}"
    )

    for token in tokens:
        sheet = SHEETS / f"{token}.svg"
        text = sheet.read_text(encoding="utf-8")
        assert text.startswith("<?xml"), f"{token}.svg is not an SVG document"
        assert "authored SpellVisualGeometry mesh across the seven" in text, (
            f"{token}.svg lost its source-derived provenance note"
        )
        assert "NOT an in-game screenshot; native F2 capture is BLOCKED" in text, (
            f"{token}.svg must keep the honest native-capture caveat"
        )
        # The seven authored semantic cast keys, in Section 9A.03(C) order, each
        # carrying the family's own re-time and material heat.
        for index, beat in enumerate(
            ("dormant", "anticipation", "energized", "release", "peak", "dissipate", "completed")
        ):
            assert f"{index + 1}/7 {beat} " in text, (
                f"{token}.svg is missing semantic cast key {index + 1} ({beat})"
            )
        assert text.count("<line") > 40, f"{token}.svg looks like an empty placeholder"
        assert sheet.stat().st_size > 5_000, f"{token}.svg is suspiciously small"


def test_the_flipbook_plays_the_very_sheets_it_references() -> None:
    """The browsing aid must reuse the committed sheets, not a private copy.

    A playtest viewer needs to *watch* the cast arc, not read a strip of seven
    stills.  ``playtest_reel.html`` steps a single-cell viewport through each
    sheet's seven panels with a CSS ``steps(7)`` animation, so it contains no
    geometry of its own: every frame it plays is the byte-for-byte sheet that
    ``--check`` already pins to the shipped mesh.
    """
    tokens = _authored_tokens()
    reel = SHEETS / "playtest_reel.html"
    assert reel.is_file(), "the playtest flipbook must ship next to the sheets"
    keys = 7

    text = reel.read_text(encoding="utf-8")
    assert text.lstrip().startswith("<!DOCTYPE html>")
    assert "This is not an in-game screenshot" in text, (
        "the flipbook must keep the honest native-capture caveat"
    )
    # Every panel is one of the committed sheets, referenced in roster order.
    refs = re.findall(r'src="([a-z]+)\.svg"', text)
    assert refs == tokens, f"flipbook sheet references drifted: {refs}"
    # A single-cell viewport stepping through all seven keys.
    assert f"steps({keys})" in text, "the flipbook must step through seven keys"
    assert f"width: {keys * 100}%" in text, "the strip must be seven cells wide"
    # Reduced motion turns the loop into a still, matching the client policy.
    assert "prefers-reduced-motion: reduce" in text, (
        "the flipbook must honour reduced-motion like the client does"
    )
    for index, beat in enumerate(
        ("dormant", "anticipation", "energized", "release", "peak", "dissipate", "completed")
    ):
        assert beat in text, f"the flipbook dropped semantic cast key {index + 1} ({beat})"
