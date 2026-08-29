from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FRONTEND = ROOT / "admin-web" / "frontend"
PREVIEW_PAGES = ("preview-admin.html", "preview-player.html")
CURRENT_UI_VERSION = "20260829launcherlink6"
LEGACY_UI_VERSION = "20260825siteui16"
PREVIEW_UI_VERSION = "20260825siteui16"
ERROR_UI_VERSION = "20260825siteui22"


def test_preview_pages_use_the_same_cache_busted_navigation() -> None:
    for page in PREVIEW_PAGES:
        source = (FRONTEND / page).read_text(encoding="utf-8")
        assert f"/assets/js/preview-nav.js?v={PREVIEW_UI_VERSION}" in source
        assert f"/assets/css/preview.css?v={PREVIEW_UI_VERSION}" in source


def test_preview_navigation_uses_the_current_theme_toggle_module() -> None:
    source = (FRONTEND / "assets" / "js" / "preview-nav.js").read_text(encoding="utf-8")
    assert f'./theme/theme-toggle.js?v={PREVIEW_UI_VERSION}' in source


def test_preview_open_toggle_moves_into_the_drawer_header() -> None:
    css = (FRONTEND / "assets" / "css" / "preview.css").read_text(encoding="utf-8")
    assert ".preview-nav-open .preview-nav-toggle" in css
    assert "position: fixed" in css
    assert "top: 16px" in css


def test_preview_shell_does_not_force_a_horizontal_scrollbar_at_320px() -> None:
    css = (FRONTEND / "assets" / "css" / "preview.css").read_text(encoding="utf-8")
    assert "@media (max-width: 340px)" in css
    assert "body.preview-shell" in css
    assert "min-width: 0" in css
    assert "overflow-x: clip" in css


def test_preview_demo_does_not_present_static_content_as_live_controls() -> None:
    css = (FRONTEND / "assets" / "css" / "preview.css").read_text(encoding="utf-8")
    admin = (FRONTEND / "preview-admin.html").read_text(encoding="utf-8")
    player = (FRONTEND / "preview-player.html").read_text(encoding="utf-8")
    assert 'class="preview-nav" data-preview-static="true"' in admin
    assert 'class="preview-nav" data-preview-static="true"' in player
    assert '.preview-nav[data-preview-static="true"] .preview-nav-item:hover' in css
    assert 'id="previewDemoNotice"' in player
    assert '<button type="button" class="preview-button is-primary" disabled' in player


def test_preview_tables_expose_header_scope_and_a_label() -> None:
    for page, label_id in (("preview-admin.html", "previewAdminEventsTitle"), ("preview-player.html", "previewPlayerOperationsTitle")):
        source = (FRONTEND / page).read_text(encoding="utf-8")
        assert f'aria-labelledby="{label_id}"' in source
        assert source.count('<th scope="col">') >= 4


def test_cabinet_runtime_dynamic_modules_use_current_ui_version() -> None:
    source = (FRONTEND / "assets" / "js" / "cabinet-runtime.js").read_text(encoding="utf-8")
    assert f'./admin/commerce-pages.js?v={CURRENT_UI_VERSION}' in source
    assert f'./legacy/app-legacy.js?v={LEGACY_UI_VERSION}' in source


def test_error_pages_use_current_error_script_version() -> None:
    for page in ("404.html", "error.html"):
        source = (FRONTEND / page).read_text(encoding="utf-8")
        assert f"/assets/js/public/error-page.js?v={ERROR_UI_VERSION}" in source
