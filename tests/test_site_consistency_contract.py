"""Small consistency contracts for the final site pass.

These checks cover the defects found by the independent route audit: service
pages need the same favicon, preview theme controls need a fallback bootstrap,
admin copy should stay Russian, and the launcher catalog must scroll inside
its panel instead of widening the whole page.
"""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FRONTEND = ROOT / "admin-web" / "frontend"


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def test_service_and_preview_pages_have_the_shared_favicon() -> None:
    for page in ("launcher-feed-index.html", "preview-admin.html", "preview-player.html"):
        source = read(f"admin-web/frontend/{page}")
        assert '/assets/favicon.svg?v=20260723r3' in source, page


def test_preview_pages_have_a_theme_bootstrap_fallback() -> None:
    for page in ("preview-admin.html", "preview-player.html"):
        source = read(f"admin-web/frontend/{page}")
        assert 'theme-autostart.js?v=20260902siteui28' in source, page

    source = read("admin-web/frontend/assets/js/theme/theme-autostart.js")
    assert 'theme-toggle.js?v=20260902siteui28' in source
    assert "initThemeToggle()" in source


def test_preview_surfaces_use_the_shared_type_roles() -> None:
    css = read("admin-web/frontend/assets/css/preview-atmosphere.css")
    assert '--site-display: "Sora"' in css
    assert '--site-body: "Inter"' in css
    assert '--site-data: "Inter"' in css
    assert "font-family: var(--site-body)" in css
    assert "font-family: var(--site-display)" in css
    assert "font-family: var(--site-data)" in css


def test_admin_labels_are_consistent_and_localized() -> None:
    launcher = read("admin-web/frontend/assets/js/admin/launcher-pages.js")
    commerce = read("admin-web/frontend/assets/js/admin/commerce-pages.js")
    runtime = read("admin-web/frontend/assets/js/cabinet-runtime.js")
    legacy = read("admin-web/frontend/assets/js/legacy/app-legacy.js")

    assert "Версия лаунчера" in launcher
    assert "Идентификатор компонента" in launcher
    assert "Добавить donation" not in commerce
    assert "Добавить донаты" in commerce
    assert "First-run готовность" not in runtime
    assert "Готовность первого запуска" in runtime
    assert 'placeholder = "Придумай логин"' not in legacy
    assert 'placeholder = "Введи логин"' not in legacy


def test_launcher_catalog_is_contained_on_narrow_screens() -> None:
    css = read("admin-web/frontend/assets/css/launcher-admin.css")
    assert ".launcher-mod-table {" in css
    assert "max-width: 100%;" in css
    assert "overflow-x: auto;" in css
    assert "@media (max-width: 900px)" in css
    assert ".launcher-mod-table table {" in css
    assert "white-space: normal;" in css
