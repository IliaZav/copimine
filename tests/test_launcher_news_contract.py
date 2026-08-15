from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def test_launcher_news_and_legacy_routes_are_static_contracts() -> None:
    launcher = read("admin-web/frontend/launcher.html")
    news = read("admin-web/frontend/news.html")
    legacy = read("admin-web/frontend/mods.html")
    assert 'data-page-kind="public-launcher"' in launcher
    assert 'id="launcherDownloadBtn"' in launcher
    assert 'href="/downloads/CopiMineMods.zip"' not in launcher
    assert 'data-page-kind="public-news"' in news
    assert 'id="newsList"' in news
    assert 'rel="canonical" href="https://copimine.ru/launcher.html"' in legacy
    assert 'href="/launcher.html"' in legacy
    assert "Скачать модпак" not in legacy


def test_public_navigation_exposes_launcher_and_news() -> None:
    for name in ("index.html", "server.html", "elections.html", "shops.html", "signin.html", "register.html", "cart.html", "404.html", "error.html"):
        page = read(f"admin-web/frontend/{name}")
        assert 'href="/launcher.html">Лаунчер</a>' in page, name
        assert 'href="/news.html">Новости</a>' in page, name


def test_generated_patch_feed_points_to_existing_safe_assets() -> None:
    index_path = ROOT / "admin-web/frontend/assets/public-data/patches/index.json"
    index = json.loads(index_path.read_text(encoding="utf-8"))
    assert index["schemaVersion"] == 1
    assert index["patches"]
    for entry in index["patches"]:
        assert entry["detailUrl"].startswith("/news/")
        assert (ROOT / "admin-web/frontend/news" / f"{entry['slug']}.html").is_file()
        detail = json.loads((ROOT / "admin-web/frontend/assets/public-data/patches" / f"{entry['slug']}.json").read_text(encoding="utf-8"))
        for item in detail.get("items", []):
            assert item["iconUrl"].startswith("/assets/patch-items/")
            assert (ROOT / "admin-web/frontend/assets" / item["iconUrl"].removeprefix("/assets/")).is_file()


def test_launcher_metadata_is_publishable_and_points_to_a_versioned_installer() -> None:
    metadata = json.loads(
        (ROOT / "admin-web/frontend/assets/public-data/launcher/latest.json").read_text(encoding="utf-8")
    )
    assert metadata["schemaVersion"] == 1
    assert metadata["channel"] == "stable"
    assert metadata["architecture"] == "x64"
    assert metadata["filename"] == "CopiMineLauncherSetup-1.0.0.exe"
    assert metadata["downloadUrl"] == "/downloads/launcher/CopiMineLauncherSetup-1.0.0.exe"
    assert metadata["releaseNotesUrl"].startswith("/news/")
    assert isinstance(metadata["sizeBytes"], int) and metadata["sizeBytes"] > 0
    assert len(metadata["sha256"]) == 64 and metadata["sha256"] == metadata["sha256"].lower()


def test_network_text_is_not_inserted_as_html() -> None:
    for name in ("launcher-data.js", "launcher-render.js", "patch-data.js", "patch-render.js", "news-page.js", "patch-detail-page.js"):
        source = read(f"admin-web/frontend/assets/js/public/{name}")
        assert ".innerHTML" not in source, name
        assert "insertAdjacentHTML" not in source, name
    public_page = read("admin-web/frontend/assets/js/public/public-page.js")
    assert 'pageKind === "public-launcher"' in public_page
    assert 'pageKind === "public-news"' in public_page
    assert 'pageKind === "public-patch"' in public_page
