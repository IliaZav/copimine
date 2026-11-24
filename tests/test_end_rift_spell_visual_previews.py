"""Section 9L wants per-ability visual acceptance assets for the V5.1 casts.

A native F2 capture from a real client is BLOCKED on this host, so the honest
substitute is proof of what the shipped Java actually draws.  ``scripts/
minecraft/RenderSpellVisualPreviews.py`` compiles the real ``SpellVisualGeometry``
source, samples the authored mesh at three cast instants and emits one isometric
SVG sheet per token.  This test runs its ``--check`` mode, so a geometry edit can
never silently leave the committed sheets describing an older mesh, and it pins
the contract that makes the sheets usable as evidence:

* exactly one sheet per authored token, no orphans and no missing art,
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
        assert "authored SpellVisualGeometry mesh at three cast" in text, (
            f"{token}.svg lost its source-derived provenance note"
        )
        assert "NOT an in-game screenshot; native F2 capture is BLOCKED" in text, (
            f"{token}.svg must keep the honest native-capture caveat"
        )
        # Three authored instants, each carrying real mesh strokes.
        assert text.count("t = 0 ms") == 1 and "t = 1200 ms" in text and "t = 2600 ms" in text, (
            f"{token}.svg does not show the three authored cast instants"
        )
        assert text.count("<line") > 40, f"{token}.svg looks like an empty placeholder"
        assert sheet.stat().st_size > 5_000, f"{token}.svg is suspiciously small"
