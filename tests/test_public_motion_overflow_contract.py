from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]


def test_animated_public_navigation_clips_horizontal_scanline_overflow():
    source = (ROOT / "admin-web/frontend/assets/css/cabinet-launcher-theme.css").read_text(encoding="utf-8")
    assert re.search(
        r'body\[data-page-kind="cabinet"\]\s*>\s*\.public-nav\s*\{[^}]*overflow-x:\s*clip\s*;',
        source,
        re.S,
    ), "the animated navigation scanline must not expand the document horizontally"
