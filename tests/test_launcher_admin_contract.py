from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def test_cabinet_has_first_class_launcher_and_news_routes() -> None:
    runtime = read("admin-web/frontend/assets/js/cabinet-runtime.js")
    assert '"launcher"' in runtime
    assert '"news"' in runtime
    assert "createAdminLauncherPages" in runtime
    assert "createAdminNewsPages" in runtime
    assert "/api/admin/launcher" in read("admin-web/frontend/assets/js/admin/launcher-pages.js")
    assert "/api/admin/launcher/news" in read("admin-web/frontend/assets/js/admin/news-pages.js")


def test_launcher_admin_contract_covers_mod_release_stats_and_safe_content() -> None:
    launcher = read("admin-web/frontend/assets/js/admin/launcher-pages.js")
    news = read("admin-web/frontend/assets/js/admin/news-pages.js")
    for token in ("mods/upload", "release/validate", "release/publish", "release/rollback", "/api/admin/launcher/stats", "FormData"):
        assert token in launcher
    for token in ("summary", "general", "technical", "bugfixes", "items", "iconUrl", "LAUNCHER_NEWS"):
        assert token in news
    for source in (launcher, news):
        assert "innerHTML" not in source
        assert "onclick=" not in source
        assert "data-click" in source


def test_admin_styles_define_launcher_editor_layout_and_responsive_state() -> None:
    cabinet = read("admin-web/frontend/assets/cabinet.css")
    styles = read("admin-web/frontend/assets/css/launcher-admin.css")
    assert "launcher-admin.css" in cabinet
    assert ".launcher-admin-grid" in styles
    assert ".launcher-mod-table" in styles
    assert "@media" in styles
