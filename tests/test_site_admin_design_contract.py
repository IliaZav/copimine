from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FRONTEND = ROOT / "admin-web" / "frontend"

PUBLIC_PAGES = (
    "index.html",
    "server.html",
    "elections.html",
    "shops.html",
    "launcher.html",
    "news.html",
    "signin.html",
    "register.html",
    "cart.html",
    "404.html",
    "error.html",
    *(f"news/{path.name}" for path in sorted((FRONTEND / "news").glob("*.html"))),
    "cabinet/demoted.html",
)

COMMON_PUBLIC_IMPORTS = (
    "/assets/css/tokens.css",
    "/assets/css/themes.css",
    "/assets/css/release-ui.css",
)

CANONICAL_NAV = (
    "/index.html",
    "/server.html",
    "/elections.html",
    "/shops.html",
    "/launcher.html",
    "/news.html",
    "/signin.html",
    "/register.html",
)


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def public_header(html: str, page: str) -> str:
    match = re.search(
        r'<header\b[^>]*class="[^"]*\bpublic-nav\b[^"]*"[^>]*>[\s\S]*?</header>',
        html,
        flags=re.IGNORECASE,
    )
    assert match, f"{page} must have a public header"
    return match.group(0)


def header_links(header: str) -> tuple[str, ...]:
    nav = re.search(r"<nav\b[\s\S]*?</nav>", header, flags=re.IGNORECASE)
    assert nav, "public header must have a nav"
    return tuple(
        href
        for href in re.findall(r'<a\b[^>]*\bhref="([^"]+)"', nav.group(0), flags=re.IGNORECASE)
        if href.startswith("/") and href not in {"/cart.html"}
    )[: len(CANONICAL_NAV)]


def test_public_pages_use_one_navigation_and_one_common_css_entrypoint() -> None:
    for page in PUBLIC_PAGES:
        html = read(f"admin-web/frontend/{page}")
        assert header_links(public_header(html, page)) == CANONICAL_NAV, page
        assert 'href="/mods.html"' not in public_header(html, page), page
        for common_import in COMMON_PUBLIC_IMPORTS:
            assert f'href="{common_import}"' not in html, f"duplicate public import {common_import}: {page}"

    style = read("admin-web/frontend/assets/style.css")
    for common_import in COMMON_PUBLIC_IMPORTS:
        assert f'@import url("./css/{common_import.rsplit("/", 1)[-1]}")' in style


def test_compatibility_and_preview_routes_are_not_second_products() -> None:
    mods = read("admin-web/frontend/mods.html")
    assert "location.replace('/launcher.html')" in mods
    assert 'data-page-kind="public-legacy"' in mods
    assert 'class="legacy-redirect-card"' in mods
    assert 'href="/launcher.html"' in mods

    for page in ("preview-admin.html", "preview-player.html"):
        html = read(f"admin-web/frontend/{page}").lower()
        assert "предпросмотр" in html or "демо" in html, page
        assert "data-live-preview=\"true\"" not in html, page


def test_cabinet_boot_does_not_wait_for_csrf_warmup() -> None:
    runtime = read("admin-web/frontend/assets/js/cabinet-runtime.js")
    boot = runtime[runtime.index("async function boot()"):]
    assert "void refreshCsrfCookie()" in boot
    assert "await refreshCsrfCookie()" not in boot


def test_cabinet_site_button_uses_root_route_and_existing_item_artwork() -> None:
    runtime = read("admin-web/frontend/assets/js/cabinet-runtime.js")
    assert 'window.location.href = "/index.html"' in runtime
    assert 'window.location.href = "index.html"' not in runtime
    assert 'src="/assets/mc-icons/item/lectern_front.png"' in runtime
    assert not (FRONTEND / "assets" / "mc-icons" / "item" / "lectern.png").exists()


def test_cabinet_updates_browser_title_when_switching_sections() -> None:
    runtime = read("admin-web/frontend/assets/js/cabinet-runtime.js")
    assert 'document.title = `CopiMine - ${meta.title}`' in runtime


def test_sources_view_explains_database_roles_without_secrets() -> None:
    registry = read("admin-web/frontend/assets/js/admin/plugin-registry-pages.js")
    assert "PostgreSQL" in registry
    assert "CoreProtect" in registry
    assert "authBackend" in registry
    assert "POSTGRES_PASSWORD" not in registry

    backend = read("admin-web/backend/main.py")
    assert '"postgresConfigured": pg_ready()' in backend
    assert '"postgresConnected": startup_check_status("postgres") == "ok"' in backend
    assert '"status": postgres_status' in backend
    assert '"authDb": auth_storage_location()' in backend
    assert '"adminPluginDb": safe_location(resolved_admin_db)' in backend


def test_public_copy_avoids_template_fillers() -> None:
    source = "\n".join(read(f"admin-web/frontend/{page}") for page in PUBLIC_PAGES)
    for phrase in ("комплексная платформа", "бесшовный", "погрузитесь", "Здесь собраны"):
        assert phrase.lower() not in source.lower(), phrase
