from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def test_admin_content_sections_have_stable_spa_routes() -> None:
    source = (ROOT / "admin-web" / "frontend" / "assets" / "js" / "shared" / "app-routes.js").read_text(encoding="utf-8")

    assert 'launcher: "/cabinet/settings.html?route=launcher"' in source
    assert 'news: "/cabinet/settings.html?route=news"' in source
    assert 'events: "/cabinet/settings.html?route=events"' in source
    assert 'cms: "/cabinet/settings.html?route=cms"' in source


def test_admin_route_map_change_busts_the_import_cache() -> None:
    runtime = (ROOT / "admin-web" / "frontend" / "assets" / "js" / "cabinet-runtime.js").read_text(encoding="utf-8")
    bootstrap = (ROOT / "admin-web" / "frontend" / "assets" / "js" / "bootstrap.js").read_text(encoding="utf-8")

    assert 'shared/app-routes.js?v=20260902route1' in runtime
    assert 'shared/app-routes.js?v=20260902route1' in bootstrap
