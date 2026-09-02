"""Contracts for the cross-site visual system used by public and cabinet views.

These checks intentionally test the shared layer rather than individual pixel
values. Page modules keep their behavior; the last CSS layer owns the visual
rhythm, motion policy, focus treatment, and route-specific art direction.
"""

from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FRONTEND = ROOT / "admin-web" / "frontend"


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def test_public_shell_loads_one_final_atmosphere_layer() -> None:
    style = read("admin-web/frontend/assets/style.css")
    atmosphere = read("admin-web/frontend/assets/css/site-atmosphere.css")

    assert '@import url("./css/site-atmosphere.css?v=20260902siteui28");' in style
    assert style.count("site-atmosphere.css") == 1
    for selector in (
        "body[data-page-kind^=\"public\"]::before",
        ".public-nav",
        ".public-hero",
        ".public-panel",
        ".public-site :where(a, button, input, select, textarea):focus-visible",
        "@media (prefers-reduced-motion: reduce)",
    ):
        assert selector in atmosphere


def test_public_atmosphere_uses_local_art_and_route_specific_treatment() -> None:
    atmosphere = read("admin-web/frontend/assets/css/site-atmosphere.css")

    for asset in (
        "/assets/showcase/home-light-v2.png",
        "/assets/showcase/server-light-v2.png",
        "/assets/showcase/shops-light-v2.png",
        "/assets/showcase/server-dark-v2.png",
        "/assets/showcase/shops-dark-v2.png",
        "/assets/launcher-screenshots/launcher-home.jpg",
    ):
        assert asset in atmosphere
    assert "public-events" in atmosphere
    assert "background-image" in atmosphere
    assert "@keyframes siteAurora" in atmosphere
    assert "@keyframes siteFloat" in atmosphere


def test_cabinet_shell_loads_a_matching_final_layer() -> None:
    cabinet = read("admin-web/frontend/assets/cabinet.css")
    atmosphere = read("admin-web/frontend/assets/css/cabinet-atmosphere.css")

    assert '@import url("./css/cabinet-atmosphere.css?v=20260902cabinetui29");' in cabinet
    assert "body.player-mode" in atmosphere
    assert "body.panel-admin-mode" in atmosphere
    assert ".sidebar" in atmosphere
    assert ".topbar" in atmosphere
    assert ":focus-visible" in atmosphere
    assert "prefers-reduced-motion" in atmosphere


def test_preview_surfaces_use_the_same_brand_and_do_not_reintroduce_old_copy() -> None:
    for page in ("preview-admin.html", "preview-player.html"):
        source = read(f"admin-web/frontend/{page}")
        assert "/assets/brand/copimine-logo.png" in source
        assert "Модпак" not in source
        assert "комплексная платформа" not in source.lower()
        assert "погрузитесь" not in source.lower()

    preview_css = read("admin-web/frontend/assets/css/preview-atmosphere.css")
    for selector in (".preview-shell", ".preview-nav-item.is-active", ".preview-topbar", "prefers-reduced-motion"):
        assert selector in preview_css


def test_every_public_html_route_keeps_the_shared_style_entrypoint() -> None:
    pages = list(FRONTEND.glob("*.html")) + list((FRONTEND / "news").glob("*.html"))
    for path in pages:
        source = path.read_text(encoding="utf-8")
        if "data-page-kind=\"public" in source or "class=\"public-site" in source:
            assert "/assets/style.css?v=20260903constellation4" in source, path.name


def test_new_visual_layer_has_small_screen_and_keyboard_guards() -> None:
    public = read("admin-web/frontend/assets/css/site-atmosphere.css")
    cabinet = read("admin-web/frontend/assets/css/cabinet-atmosphere.css")

    for css in (public, cabinet):
        assert "@media (max-width: 980px)" in css
        assert "@media (max-width: 640px)" in css or "@media (max-width: 620px)" in css
        assert "outline-offset" in css
        assert "animation: none" in css


def test_public_copy_audit_does_not_add_generic_ai_marketing_phrases() -> None:
    pages = list(FRONTEND.glob("*.html")) + list((FRONTEND / "news").glob("*.html"))
    source = "\n".join(path.read_text(encoding="utf-8") for path in pages)
    forbidden = (
        "погрузитесь",
        "бесшовный",
        "комплексная платформа",
        "уникальная экосистема",
        "раскройте потенциал",
        "next level",
    )
    for phrase in forbidden:
        assert phrase not in source.lower(), phrase


def test_brand_palette_and_font_pairing_are_applied_to_the_shared_layer() -> None:
    atmosphere = read("admin-web/frontend/assets/css/site-atmosphere.css")
    assert '--site-display: "Sora"' in atmosphere
    assert '--site-body: "Inter"' in atmosphere
    assert '--site-data: "Inter"' in atmosphere
    assert "font-family: var(--site-body)" in atmosphere
    assert "font-family: var(--site-display)" in atmosphere
    assert "font-family: var(--site-data)" in atmosphere
    for color in ("#9d8cff", "#59d6d0", "#ffc46b"):
        assert color in atmosphere
