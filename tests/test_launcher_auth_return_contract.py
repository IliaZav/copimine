from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
AUTH_PAGE = (ROOT / "admin-web/frontend/assets/js/auth/auth-page.js").read_text(encoding="utf-8")
BOOTSTRAP = (ROOT / "admin-web/frontend/assets/js/bootstrap.js").read_text(encoding="utf-8")


def test_successful_auth_returns_to_the_pending_launcher_binding():
    assert "launcherReturnHrefFromAuthSearch" in AUTH_PAGE
    assert "const launcherReturn = launcherReturnHrefFromAuthSearch(window.location.search);" in AUTH_PAGE
    assert "window.location.replace(launcherReturn);" in AUTH_PAGE
    assert "return;" in AUTH_PAGE


def test_existing_session_on_auth_page_preserves_launcher_return_target():
    assert "if (session?.role)" in AUTH_PAGE
    assert "launcherReturnHrefFromAuthSearch" in AUTH_PAGE


def test_binding_fix_is_cache_busted_on_auth_and_link_pages():
    assert 'from "./auth/auth-page.js?v=20260828launcherlink3"' in BOOTSTRAP
    assert 'from "./shared/app-routes.js?v=20260828launcherlink3"' in BOOTSTRAP
    for relative in ("signin.html", "register.html"):
        html = (ROOT / "admin-web/frontend" / relative).read_text(encoding="utf-8")
        assert '/assets/app.js?v=20260828launcherlink3' in html


def test_launcher_link_runtime_cache_key_changes_when_binding_flow_changes():
    app = (ROOT / "admin-web/frontend/assets/app.js").read_text(encoding="utf-8")
    bootstrap = (ROOT / "admin-web/frontend/assets/js/bootstrap.js").read_text(encoding="utf-8")
    link = (ROOT / "admin-web/frontend/cabinet/link.html").read_text(encoding="utf-8")

    assert 'app.js?v=20260828launcherlink3' in link
    assert 'js/bootstrap.js?v=20260828launcherlink3' in app
    assert 'cabinet-runtime.js?v=20260828launcherlink3' in bootstrap


def test_auth_return_contract_keeps_the_hidden_binding_page_for_login_and_registration():
    routes = (ROOT / "admin-web/frontend/assets/js/shared/app-routes.js").read_text(encoding="utf-8")
    assert 'const safePath = target.pathname === LAUNCHER_BINDING_PATH' in routes
    assert 'target.pathname === LEGACY_LAUNCHER_BINDING_PATH' in routes
    assert 'return query ? `${LAUNCHER_BINDING_PATH}?${query}` : "";' in routes
    binding_runtime = (ROOT / "admin-web/frontend/assets/js/launcher-link-page.js").read_text(encoding="utf-8")
    assert 'authLandingHref("signin", launcherReturn)' in binding_runtime
    assert 'authLandingHref("register", launcherReturn)' in binding_runtime
    assert 'data-auth-switch="register"' in (ROOT / "admin-web/frontend/signin.html").read_text(encoding="utf-8")
    assert 'data-auth-switch="signin"' in (ROOT / "admin-web/frontend/register.html").read_text(encoding="utf-8")
