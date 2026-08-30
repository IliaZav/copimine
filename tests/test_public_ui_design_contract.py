"""Behavioral contracts for the public website visual system."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FRONTEND = ROOT / "admin-web" / "frontend"


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def test_public_styles_end_with_one_intentional_polish_layer() -> None:
    style = read("admin-web/frontend/assets/style.css")
    assert '@import url("./css/website-polish.css?v=20260829siteui23");' in style
    assert style.rfind('@import url("./css/website-polish.css?v=20260829siteui23");') > style.rfind('@import url("./css/ui-audit.css");')
    assert style.count('@import url("./css/website-polish.css?v=20260829siteui23");') == 1
    for page in ("index.html", "server.html", "shops.html", "launcher.html", "news.html", "signin.html", "register.html"):
        assert "website-polish.css" not in read(f"admin-web/frontend/{page}")


def test_public_navigation_collapses_before_tablet_overflow_and_preserves_accessible_state() -> None:
    nav = read("admin-web/frontend/assets/js/public/public-nav.js")
    css = read("admin-web/frontend/assets/css/website-polish.css")
    assert 'window.matchMedia("(max-width: 1080px)")' in nav
    assert 'setExpanded(toggle, false)' in nav
    assert '@media (max-width: 1080px)' in css
    assert '.public-nav.public-nav-open nav' in css
    assert '.public-mobile-toggle' in css
    assert '.public-mobile-toggle:hover' in css
    assert '.public-mobile-toggle:focus-visible' in css


def test_public_shell_handles_narrow_viewports_without_a_second_horizontal_scrollbar() -> None:
    css = read("admin-web/frontend/assets/css/website-polish.css")
    assert "@media (max-width: 340px)" in css
    assert "body[data-page-kind^=\"public\"]" in css
    assert "body.auth-screen" in css
    assert "min-width: 0" in css
    assert "overflow-x: clip" in css


def test_cart_route_has_one_static_cart_control_and_runtime_reuses_one_node() -> None:
    cart = read("admin-web/frontend/cart.html")
    nav = read("admin-web/frontend/assets/js/public/public-nav.js")
    assert 'id="shopCartButton" class="shop-cart-button" href="/cart.html" aria-current="page"' in cart
    assert "shop-cart-mobile-shortcut" not in cart
    assert cart.count('href="/cart.html"') == 1
    assert "header.append(cart)" in nav
    assert "nav.append(cart)" in nav


def test_shop_route_has_one_static_cart_control_and_runtime_reuses_one_node() -> None:
    shops = read("admin-web/frontend/shops.html")
    nav = read("admin-web/frontend/assets/js/public/public-nav.js")
    assert shops.count('id="shopCartButton"') == 1
    assert "shop-cart-mobile-shortcut" not in shops
    assert "shop-cart-compact" in nav


def test_launcher_and_news_heroes_use_real_product_assets_without_placeholder_copy() -> None:
    launcher = read("admin-web/frontend/launcher.html")
    news = read("admin-web/frontend/news.html")
    assert 'launcher-home-thumb.jpg' in launcher
    assert 'class="news-hero news-hero-text-only"' in news
    assert 'launcher-update-thumb.jpg' not in news
    lightbox_start = launcher.index('<dialog id="launcherLightbox"')
    lightbox = launcher[lightbox_start:]
    assert 'src="/assets/launcher-screenshots/launcher-home.jpg"' in lightbox
    assert 'figma.com' not in launcher.lower()
    assert 'figma.com' not in news.lower()


def test_news_surface_is_text_first_without_photo_hero_or_media_cards() -> None:
    news = read("admin-web/frontend/news.html")
    launcher_render = read("admin-web/frontend/assets/js/public/launcher-render.js")
    launcher_styles = read("admin-web/frontend/assets/css/launcher-news.css")
    patch_render = read("admin-web/frontend/assets/js/public/patch-render.js")
    polish = read("admin-web/frontend/assets/css/website-polish.css")
    assert 'class="news-hero news-hero-text-only"' in news
    assert "launcher-update-thumb.jpg" not in news
    assert "news-hero-visual" not in news
    assert "news-card-media" not in launcher_render
    assert "news-card-media" not in launcher_styles
    assert "news-card-media" not in patch_render
    assert ".news-hero-text-only" in polish


def test_text_only_news_hero_has_a_useful_release_summary() -> None:
    news = read("admin-web/frontend/news.html")
    patch_render = read("admin-web/frontend/assets/js/public/patch-render.js")
    polish = read("admin-web/frontend/assets/css/website-polish.css")
    assert 'class="news-hero-summary"' in news
    assert 'id="newsHeroCount"' in news
    assert 'id="newsHeroLatest"' in news
    assert "newsHeroCount" in patch_render
    assert "newsHeroLatest" in patch_render
    assert ".news-hero-summary" in polish
    assert "grid-template-columns" in polish


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


def test_auth_autofill_keeps_the_form_inside_the_active_theme() -> None:
    css = read("admin-web/frontend/assets/css/website-polish.css")
    assert ".public-site .auth-card input:-webkit-autofill" in css
    assert ".public-site .auth-card input:-moz-autofill" in css
    assert "-webkit-box-shadow" in css
    assert ':root[data-theme="dark"] .public-site .auth-card input:-webkit-autofill' in css


def test_dynamic_server_skin_stage_has_a_real_fallback_asset() -> None:
    server = read("admin-web/frontend/server.html")
    renderer = read("admin-web/frontend/assets/js/public/site-render.js")
    public_page = read("admin-web/frontend/assets/js/public/public-page.js")
    homepage = read("admin-web/frontend/assets/js/public/homepage.js")
    assert '<img id="presidentSkinImage" src="/assets/brand/copimine-logo.png"' in server
    assert 'skinImage.src = fallbackAvatar;' in renderer
    assert './homepage.js?v=20260825siteui19' in public_page
    assert './site-render.js?v=20260825siteui19' in homepage


def test_public_shell_assets_share_the_current_release_cache_key() -> None:
    style_cache_key = "20260829siteui23"
    launcher_news_cache_key = "20260825siteui19"
    public_script_key = "20260829siteui23"
    public_module_key = "20260825siteui19"
    pages = list(FRONTEND.glob("*.html")) + list((FRONTEND / "news").glob("*.html"))
    for path in pages:
        source = path.read_text(encoding="utf-8")
        if "/assets/style.css" in source:
            assert f"/assets/style.css?v={style_cache_key}" in source, path.name
        if "/assets/js/public/public-page.js" in source:
            expected_key = "20260830events1" if path.name == "events.html" else public_script_key
            assert f"/assets/js/public/public-page.js?v={expected_key}" in source, path.name
        if "/assets/css/launcher-news.css" in source:
            assert f"/assets/css/launcher-news.css?v={launcher_news_cache_key}" in source, path.name

    public_page = read("admin-web/frontend/assets/js/public/public-page.js")
    homepage = read("admin-web/frontend/assets/js/public/homepage.js")
    launcher_page = read("admin-web/frontend/assets/js/public/launcher-page.js")
    assert f'./homepage.js?v={public_module_key}' in public_page
    assert f'./launcher-page.js?v={public_module_key}' in public_page
    assert f'./site-render.js?v={public_module_key}' in homepage
    assert f'./launcher-render.js?v={public_module_key}' in launcher_page


def test_public_shop_descriptions_are_not_truncated_without_an_accessible_reveal() -> None:
    renderer = read("admin-web/frontend/assets/js/public/site-render.js")
    audit_css = read("admin-web/frontend/assets/css/ui-audit.css")
    assert '"shop-product-description"' in renderer
    assert "customerFacingItemDescription(row)" in renderer
    assert "-webkit-line-clamp" not in audit_css
    assert ".shop-product-description" in audit_css


def test_demoted_status_page_uses_the_shared_card_rhythm_without_inline_layout() -> None:
    demoted = read("admin-web/frontend/cabinet/demoted.html")
    polish = read("admin-web/frontend/assets/css/website-polish.css")

    assert 'class="demoted-page"' in demoted
    assert 'class="demoted-card"' in demoted
    assert 'class="demoted-actions"' in demoted
    assert 'style=' not in demoted
    assert ".demoted-page" in polish
    assert ".demoted-card" in polish


def test_launcher_unavailable_state_has_a_keyboard_safe_retry_action() -> None:
    launcher = read("admin-web/frontend/launcher.html")
    render = read("admin-web/frontend/assets/js/public/launcher-render.js")
    page = read("admin-web/frontend/assets/js/public/launcher-page.js")
    assert 'id="launcherRetryBtn"' in launcher
    assert 'id="launcherDownloadStatus"' in launcher
    assert 'aria-describedby="launcherDownloadStatus"' in launcher
    assert 'getElementById("launcherRetryBtn")' in render
    assert 'tabindex", "-1"' in render or 'setAttribute("tabindex", "-1")' in render
    assert 'retryButton.addEventListener("click"' in page
    assert "loadLauncherMetadata" in page


def test_copy_ip_failure_has_a_live_status_instead_of_replacing_the_button_with_raw_ip() -> None:
    index = read("admin-web/frontend/index.html")
    homepage = read("admin-web/frontend/assets/js/public/homepage.js")
    assert 'id="copyIpStatus"' in index
    assert 'role="status"' in index
    assert 'aria-live="polite"' in index
    assert "copyIpStatus" in homepage
    assert "Не удалось скопировать" in homepage
    assert 'button.textContent = ip' not in homepage


def test_final_site_audit_uses_a_compact_hero_and_shared_surface_rhythm() -> None:
    public = read("admin-web/frontend/assets/css/website-polish.css")
    cabinet = read("admin-web/frontend/assets/css/cabinet-shell-polish.css")

    assert ".public-nav > .shop-cart-compact" in public
    assert "grid-template-columns: minmax(0, 1fr) auto auto" in public
    assert ".public-site .news-hero-summary" in public
    assert "align-self: end" in public
    assert ".public-site .news-hero-stat" in public
    assert "border-radius: 12px" in public
    assert "grid-template-columns: 272px minmax(0, 1fr)" in cabinet
    assert "body.panel-admin-mode .workspace" in cabinet
    assert "body.player-mode .workspace" in cabinet
    assert "@media (prefers-reduced-motion: reduce)" in cabinet
