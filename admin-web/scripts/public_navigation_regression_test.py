#!/usr/bin/env python3
"""Guard the public mobile header against duplicate navigation toggles."""
from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PUBLIC_NAV = ROOT / "frontend" / "assets" / "js" / "public" / "public-nav.js"


def main() -> None:
    source = PUBLIC_NAV.read_text(encoding="utf-8")

    # Older public pages contain a static #mobileNavToggle.  The public
    # navigation enhancer must reuse and rename it instead of injecting a
    # second indistinguishable hamburger control.
    assert "#publicMobileNavToggle, #mobileNavToggle, .public-mobile-toggle" in source
    assert 'toggle.id = "publicMobileNavToggle"' in source

    public_pages = [
        ROOT / "frontend" / name
        for name in ("index.html", "elections.html", "server.html", "shops.html", "mods.html")
    ]
    for page in public_pages:
        markup = page.read_text(encoding="utf-8")
        if 'id="mobileNavToggle"' in markup:
            assert 'class="public-nav' in markup, f"{page.name}: legacy mobile toggle is not in a public header"

    print("Public navigation regression test OK")


if __name__ == "__main__":
    main()
