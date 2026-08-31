"""Regression contracts for defects found during the full-site audit."""

from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def test_cart_account_button_has_a_route_for_buttons_and_links() -> None:
    cart = read("admin-web/frontend/assets/js/public/cart-page.js")
    assert 'const routeTarget = authenticated ? appRouteHref(defaultAppRouteForRole(role)) : "";' in cart
    assert "cabinet.dataset.routeTarget = routeTarget;" in cart
    assert 'cabinet.addEventListener("click"' in cart
    assert "routeTarget" in cart


def test_president_skin_fallback_is_available_to_both_render_and_error_handlers() -> None:
    renderer = read("admin-web/frontend/assets/js/public/site-render.js")
    assert renderer.count('const fallbackAvatar = "/assets/brand/copimine-logo.png";') == 1
    assert "skinImage.src = fallbackAvatar" in renderer


def test_server_copy_status_is_a_live_region() -> None:
    page = read("admin-web/frontend/server.html")
    assert 'id="copyIpStatus"' in page
    assert 'role="status"' in page
    assert 'aria-live="polite"' in page


def test_events_renderer_accepts_safe_editorial_cms_fields_and_local_art() -> None:
    renderer = read("admin-web/frontend/assets/js/public/events-page.js")
    assert "safeEventCopy" in renderer
    assert "editorialRecord.title" in renderer
    assert "editorialRecord.summary" in renderer
    assert "editorialRecord.body" in renderer
    assert "editorialRecord.heroImage" in renderer
    assert "editorialRecord.dragonImage" in renderer
    # Gameplay internals stay private; they must not become public copy by
    # accident when an admin edits an event.
    assert "record.requirements" not in renderer
    assert "record.bossPhases" not in renderer


def test_static_event_contract_keeps_one_live_event_and_two_hidden_slots() -> None:
    renderer = read("admin-web/frontend/assets/js/public/events-page.js")
    assert 'const EVENT_ORDER = ["end-rift", "future-1", "future-2"];' in renderer
    assert 'status: "current"' in renderer
    assert renderer.count('status: "upcoming"') >= 2
    assert 'const editorialRecord = copy.status === "upcoming" ? {} : record;' in renderer
    assert 'const videos = copy.status === "upcoming" ? [] : (Array.isArray(record.videos) ? record.videos : []);' in renderer


def test_admin_search_targets_have_real_panel_anchors() -> None:
    runtime = read("admin-web/frontend/assets/js/cabinet-runtime.js")
    commerce = read("admin-web/frontend/assets/js/admin/commerce-pages.js")
    events = read("admin-web/frontend/assets/js/admin/events-pages.js")
    cms = read("admin-web/frontend/assets/js/admin/cms-pages.js")
    assert 'function panel(title, subtitle, body, actions = "", id = "")' in runtime
    assert 'id ? ` id="${esc(id)}"` : ""' in runtime
    assert '"economy-treasury"' in commerce
    assert '"economy-treasury-pin"' in commerce
    assert '"artifacts-shop"' in runtime
    assert '"events-editor"' in events
    assert '"cms-content"' in cms
    assert '"settings-site"' in runtime


def test_cabinet_header_navigation_has_one_owner() -> None:
    runtime = read("admin-web/frontend/assets/js/cabinet-runtime.js")
    polish = read("admin-web/frontend/assets/js/cabinet-polish.js")
    assert 'publicCabinetBtn' in runtime
    assert '$("publicCabinetBtn")?.addEventListener("click", showCabinetFromPublic);' in runtime
    assert 'cabinet.addEventListener("click"' not in polish
    assert "roleHomeHref" not in polish


def test_admin_never_echoes_treasury_pin() -> None:
    commerce = read("admin-web/frontend/assets/js/admin/commerce-pages.js")
    assert "result.pin" not in commerce
    assert "PIN казны обновлён" in commerce


def test_preview_identity_is_neutral_demo_data() -> None:
    admin = read("admin-web/frontend/preview-admin.html")
    player = read("admin-web/frontend/preview-player.html")
    assert "SudoKillDash9" not in admin
    assert "SudoKillDash9" not in player
    assert "Демо-администратор" in admin
    assert "Демо-игрок" in player


def test_admin_data_failures_are_visible_in_key_workspaces() -> None:
    runtime = read("admin-web/frontend/assets/js/cabinet-runtime.js")
    for module in (
        "admin-web/frontend/assets/js/admin/launcher-pages.js",
        "admin-web/frontend/assets/js/admin/news-pages.js",
        "admin-web/frontend/assets/js/admin/events-pages.js",
        "admin-web/frontend/assets/js/admin/cms-pages.js",
        "admin-web/frontend/assets/js/admin/plugin-registry-pages.js",
        "admin-web/frontend/assets/js/cabinet-runtime.js",
    ):
        assert "apiNotice(" in read(module)
    assert "function apiNotice(" in runtime
    assert 'role="alert"' in runtime
    assert "renderNews(asArray(payload.news), [payload])" in read("admin-web/frontend/assets/js/admin/news-pages.js")
    assert "renderEvents(asArray(payload.events), [payload])" in read("admin-web/frontend/assets/js/admin/events-pages.js")


def test_admin_search_uses_specific_target_before_text_fallback() -> None:
    runtime = read("admin-web/frontend/assets/js/cabinet-runtime.js")
    assert "state.adminSearchTarget" in runtime
    assert 'const targetId = cleanText(state.adminSearchTarget || "");' in runtime
    assert 'document.getElementById(targetId)' in runtime


def test_plugin_registry_keeps_nested_api_failures_for_diagnostics() -> None:
    registry = read("admin-web/frontend/assets/js/admin/plugin-registry-pages.js")
    assert "const errors = [registry, status, schema, config, audit]" in registry
    assert "error: errors.filter(Boolean).join" in registry
