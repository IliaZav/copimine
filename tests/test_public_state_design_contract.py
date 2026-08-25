"""Contracts for public empty states, editorial grids, and media previews."""

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def test_shop_unavailable_state_is_explicit_full_width_and_retryable() -> None:
    renderer = read("admin-web/frontend/assets/js/public/site-render.js")
    css = read("admin-web/frontend/assets/css/website-polish.css")
    assert "function buildShopEmptyState" in renderer
    assert 'setAttribute("data-shop-retry", "true")' in renderer
    assert 'setAttribute("role", "status")' in renderer
    assert ".public-site .shop-empty-state" in css
    assert "grid-column: 1 / -1" in css


def test_news_list_uses_content_aware_columns() -> None:
    css = read("admin-web/frontend/assets/css/launcher-news.css")
    assert ".news-list { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 1rem; }" in css


def test_launcher_lightbox_restores_a_real_fallback_after_close() -> None:
    renderer = read("admin-web/frontend/assets/js/public/launcher-render.js")
    assert 'const fallbackSource = "/assets/launcher-screenshots/launcher-home.jpg"' in renderer
    assert "image.src = fallbackSource;" in renderer
    assert 'image.alt = "Предварительный просмотр скриншота CopiMine Launcher"' in renderer


def test_launcher_gallery_buttons_have_explicit_accessible_names() -> None:
    html = read("admin-web/frontend/launcher.html")
    buttons = re.findall(r'<button\b[^>]*data-lightbox-src="[^"]+"[^>]*>', html)
    assert len(buttons) == 3
    assert all('aria-label="Открыть' in button for button in buttons)


def test_patch_detail_dates_are_localized_for_readable_russian_copy() -> None:
    renderer = read("admin-web/frontend/assets/js/public/patch-detail-page.js")
    assert "function localizePatchDate" in renderer
    assert 'dateStyle: "long"' in renderer
    assert 'timeStyle: "short"' in renderer
