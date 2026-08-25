from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FRONTEND = ROOT / "admin-web" / "frontend"
PREVIEW_PAGES = ("preview-admin.html", "preview-player.html")
CURRENT_UI_VERSION = "20260825siteui9"


def test_preview_pages_use_the_same_cache_busted_navigation() -> None:
    for page in PREVIEW_PAGES:
        source = (FRONTEND / page).read_text(encoding="utf-8")
        assert f"/assets/js/preview-nav.js?v={CURRENT_UI_VERSION}" in source
        assert f"/assets/css/preview.css?v={CURRENT_UI_VERSION}" in source


def test_preview_navigation_uses_the_current_theme_toggle_module() -> None:
    source = (FRONTEND / "assets" / "js" / "preview-nav.js").read_text(encoding="utf-8")
    assert f'./theme/theme-toggle.js?v={CURRENT_UI_VERSION}' in source


def test_cabinet_runtime_dynamic_modules_use_current_ui_version() -> None:
    source = (FRONTEND / "assets" / "js" / "cabinet-runtime.js").read_text(encoding="utf-8")
    assert f'./admin/commerce-pages.js?v={CURRENT_UI_VERSION}' in source
    assert f'./legacy/app-legacy.js?v={CURRENT_UI_VERSION}' in source


def test_error_pages_use_current_error_script_version() -> None:
    for page in ("404.html", "error.html"):
        source = (FRONTEND / page).read_text(encoding="utf-8")
        assert f"/assets/js/public/error-page.js?v={CURRENT_UI_VERSION}" in source
