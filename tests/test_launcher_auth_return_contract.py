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
    assert 'from "./auth/auth-page.js?v=20260825siteui16"' in BOOTSTRAP
    assert 'from "./shared/app-routes.js?v=20260825siteui16"' in BOOTSTRAP
    for relative in ("signin.html", "register.html", "cabinet/link.html"):
        html = (ROOT / "admin-web/frontend" / relative).read_text(encoding="utf-8")
        assert '/assets/app.js?v=20260825siteui16' in html
