"""Behavioral contracts for the public website visual system."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FRONTEND = ROOT / "admin-web" / "frontend"


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def test_public_styles_end_with_one_intentional_polish_layer() -> None:
    style = read("admin-web/frontend/assets/style.css")
    assert '@import url("./css/website-polish.css");' in style
    assert style.rfind('@import url("./css/website-polish.css");') > style.rfind('@import url("./css/ui-audit.css");')
    assert style.count('@import url("./css/website-polish.css");') == 1
    for page in ("index.html", "server.html", "shops.html", "launcher.html", "news.html", "signin.html", "register.html"):
        assert "website-polish.css" not in read(f"admin-web/frontend/{page}")


def test_public_navigation_collapses_before_tablet_overflow_and_preserves_accessible_state() -> None:
    nav = read("admin-web/frontend/assets/js/public/public-nav.js")
    css = read("admin-web/frontend/assets/css/website-polish.css")
    assert 'window.matchMedia("(max-width: 900px)")' in nav
    assert 'setExpanded(toggle, false)' in nav
    assert '@media (max-width: 900px)' in css
    assert '.public-nav.public-nav-open nav' in css
    assert '.public-mobile-toggle' in css


def test_launcher_and_news_heroes_use_real_product_assets_without_placeholder_copy() -> None:
    launcher = read("admin-web/frontend/launcher.html")
    news = read("admin-web/frontend/news.html")
    assert 'launcher-home-thumb.jpg' in launcher
    assert 'launcher-update-thumb.jpg' in news
    lightbox_start = launcher.index('<dialog id="launcherLightbox"')
    lightbox = launcher[lightbox_start:]
    assert 'src="/assets/launcher-screenshots/launcher-home.jpg"' in lightbox
    assert 'figma.com' not in launcher.lower()
    assert 'figma.com' not in news.lower()


def test_public_polish_layer_covers_focus_motion_and_status_recovery() -> None:
    css = read("admin-web/frontend/assets/css/website-polish.css")
    for selector in (
        ".public-nav",
        ".public-hero",
        ".public-section",
        ".public-actions",
        ".public-status-grid",
        ".launcher-hero",
        ".news-hero",
        ".auth-card",
        ":focus-visible",
        "prefers-reduced-motion",
    ):
        assert selector in css


def test_dynamic_server_skin_stage_has_a_real_fallback_asset() -> None:
    server = read("admin-web/frontend/server.html")
    renderer = read("admin-web/frontend/assets/js/public/site-render.js")
    public_page = read("admin-web/frontend/assets/js/public/public-page.js")
    homepage = read("admin-web/frontend/assets/js/public/homepage.js")
    assert '<img id="presidentSkinImage" src="/assets/brand/copimine-logo.png"' in server
    assert 'skinImage.src = fallbackAvatar;' in renderer
    assert './homepage.js?v=20260825siteui7' in public_page
    assert './site-render.js?v=20260825siteui7' in homepage


def test_public_shell_assets_share_the_current_release_cache_key() -> None:
    cache_key = "20260825siteui7"
    pages = list(FRONTEND.glob("*.html")) + list((FRONTEND / "news").glob("*.html"))
    for path in pages:
        source = path.read_text(encoding="utf-8")
        if "/assets/style.css" in source:
            assert f"/assets/style.css?v={cache_key}" in source, path.name
        if "/assets/js/public/public-page.js" in source:
            assert f"/assets/js/public/public-page.js?v={cache_key}" in source, path.name
        if "/assets/css/launcher-news.css" in source:
            assert f"/assets/css/launcher-news.css?v={cache_key}" in source, path.name

    public_page = read("admin-web/frontend/assets/js/public/public-page.js")
    homepage = read("admin-web/frontend/assets/js/public/homepage.js")
    launcher_page = read("admin-web/frontend/assets/js/public/launcher-page.js")
    assert f'./homepage.js?v={cache_key}' in public_page
    assert f'./launcher-page.js?v={cache_key}' in public_page
    assert f'./site-render.js?v={cache_key}' in homepage
    assert f'./launcher-render.js?v={cache_key}' in launcher_page
