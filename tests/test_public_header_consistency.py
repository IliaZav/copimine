from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FRONTEND = ROOT / "admin-web" / "frontend"


PUBLIC_PAGES = (
    FRONTEND / "index.html",
    FRONTEND / "server.html",
    FRONTEND / "elections.html",
    FRONTEND / "shops.html",
    FRONTEND / "launcher.html",
    FRONTEND / "news.html",
    FRONTEND / "signin.html",
    FRONTEND / "register.html",
    FRONTEND / "cart.html",
    FRONTEND / "404.html",
    FRONTEND / "error.html",
    *sorted((FRONTEND / "news").glob("*.html")),
    *sorted((FRONTEND / "cabinet").glob("*.html")),
)

EXPECTED_PUBLIC_LINKS = (
    "/index.html",
    "/server.html",
    "/elections.html",
    "/shops.html",
    "/launcher.html",
    "/news.html",
)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def header_markup(html: str, page: Path) -> str:
    match = re.search(
        r'<header\b[^>]*class="[^"]*\bpublic-nav\b[^"]*"[^>]*>[\s\S]*?</header>',
        html,
        flags=re.IGNORECASE,
    )
    assert match, f"{page.relative_to(ROOT)} has no public header"
    return match.group(0)


def nav_links(header: str) -> tuple[str, ...]:
    nav = re.search(r"<nav\b[\s\S]*?</nav>", header, flags=re.IGNORECASE)
    assert nav, "public header has no navigation element"
    return tuple(
        href
        for href in re.findall(r'<a\b[^>]*\bhref="([^"]+)"', nav.group(0), flags=re.IGNORECASE)
        if href.startswith("/")
    )


def test_all_site_headers_use_the_same_primary_navigation_order() -> None:
    for page in PUBLIC_PAGES:
        header = header_markup(read(page), page)
        assert nav_links(header)[: len(EXPECTED_PUBLIC_LINKS)] == EXPECTED_PUBLIC_LINKS, page
        assert 'href="/mods.html"' not in header, page
        assert header.count('href="/launcher.html"') == 1, page
        assert "<small>Сайт сервера</small>" in header, page


def test_runtime_public_header_keeps_one_canonical_cart_link() -> None:
    nav = read(FRONTEND / "assets" / "js" / "public" / "public-nav.js")
    assert "getShopCartCount" in nav
    assert "shop-cart-button" in nav
    assert "shop-cart-mobile-shortcut" not in nav
    assert "createCartLink(true)" not in nav
    assert "ensureCartButton" in nav
    assert "shop-cart-compact" in nav
    assert "header.append(cart)" in nav
    assert "nav.append(cart)" in nav
    assert 'media.addEventListener("change", syncCartPlacement)' in nav
    assert "querySelectorAll(`a.shop-cart-button[href=\"${CART_PATH}\"]`)" in nav
    assert "link.remove()" in nav


def test_static_headers_do_not_ship_duplicate_cart_controls() -> None:
    for page in PUBLIC_PAGES:
        header = header_markup(read(page), page)
        assert header.count('class="shop-cart-button"') <= 1, page


def test_demoted_public_shell_loads_the_common_navigation_runtime() -> None:
    source = read(FRONTEND / "cabinet" / "demoted.html")
    assert '/assets/app.js?v=20260901motion1' in source


def test_legacy_modpack_route_redirects_with_a_branded_legacy_fallback() -> None:
    mods = read(FRONTEND / "mods.html")
    assert 'http-equiv="refresh"' not in mods.lower()
    assert re.search(r"location\.replace\(['\"]/launcher\.html['\"]\)", mods)
    assert 'href="/launcher.html"' in mods
    assert 'data-page-kind="public-legacy"' in mods
    assert 'class="legacy-redirect-card"' in mods
    assert 'display: none' not in mods


def test_legacy_hash_routes_skip_the_compatibility_page() -> None:
    bootstrap = read(FRONTEND / "assets" / "js" / "bootstrap.js")
    assert '["mods", "launcher.html"]' in bootstrap
    assert '["join", "launcher.html"]' in bootstrap
