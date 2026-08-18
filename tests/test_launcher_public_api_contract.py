from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def test_public_launcher_and_news_clients_try_api_before_static_fallback() -> None:
    launcher = read("admin-web/frontend/assets/js/public/launcher-data.js")
    patch = read("admin-web/frontend/assets/js/public/patch-data.js")
    assert '"/api/public/launcher"' in launcher
    assert '"/assets/public-data/launcher/latest.json"' in launcher
    assert '"/api/public/news"' in patch
    assert "`/api/public/news/${safe}`" in patch
    assert '"/assets/public-data/patches/index.json"' in patch
    assert "`/assets/public-data/patches/${safe}.json`" in patch


def test_public_contracts_remain_dom_safe() -> None:
    for name in ("launcher-data.js", "patch-data.js", "news-page.js", "patch-detail-page.js"):
        source = read(f"admin-web/frontend/assets/js/public/{name}")
        assert "innerHTML" not in source
        assert "insertAdjacentHTML" not in source
