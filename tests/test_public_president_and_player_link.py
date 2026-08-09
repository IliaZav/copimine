from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def test_public_president_payload_is_scoped_to_active_term_and_published_laws():
    source = read("admin-web/backend/main.py")
    start = source.index("def public_president_profile_sync()")
    end = source.index("\ndef transfer_player_bank_sync", start)
    section = source[start:end]

    assert "active_president_term" in section
    assert "president_laws" in section
    assert "status='PUBLISHED'" in section or 'status="PUBLISHED"' in section
    assert '"laws"' in section
    assert "sanitize_public_plain_text" in section


def test_homepage_places_president_and_current_laws_before_server_summary():
    html = read("admin-web/frontend/index.html")
    render = read("admin-web/frontend/assets/js/public/site-render.js")

    assert html.index('id="presidentCardMount"') < html.index('id="publicStatusGrid"')
    assert 'id="presidentCardAvatar"' in html
    assert 'id="presidentLaws"' in html
    assert "renderPresidentLaws" in render
    assert "presidentLaws" in render


def test_public_law_cards_wrap_long_text_and_hide_redundant_laws_intro():
    html = read("admin-web/frontend/index.html")
    styles = read("admin-web/frontend/assets/css/public-elections.css")

    assert "Публичная часть срока" not in html
    assert "Только опубликованные законы действующего президента" not in html
    assert ".election-law-card" in styles
    assert "overflow-wrap: anywhere" in styles
    assert ".election-law-card p" in styles
    assert "min-width: 0" in styles


def test_admin_can_rebind_minecraft_identity_with_whitelist_sync_and_confirmation():
    backend = read("admin-web/backend/main.py")
    frontend = read("admin-web/frontend/assets/js/cabinet-runtime.js")

    assert "class AdminMinecraftLinkRebindIn" in backend
    assert "def remove_player_from_whitelist_sync" in backend
    assert '@app.post("/api/players/{player}/minecraft-link/rebind")' in backend
    route_start = backend.index('@app.post("/api/players/{player}/minecraft-link/rebind")')
    route_end = backend.index("\n\n@app.", route_start + 10)
    route = backend[route_start:route_end]
    assert 'require_sensitive_confirm(request, "PLAYER_MINECRAFT_LINK_REBIND")' in route
    assert "admin_rebind_player_minecraft_sync" in route

    assert "playerAdminMinecraftName" in frontend
    assert "playerRelinkMinecraft" in frontend
    assert "/minecraft-link/rebind" in frontend
    assert "PLAYER_MINECRAFT_LINK_REBIND" in frontend


def test_site_password_control_remains_explicit_and_nonrecoverable():
    frontend = read("admin-web/frontend/assets/js/cabinet-runtime.js")
    backend = read("admin-web/backend/main.py")

    assert "Новый пароль сайта" in frontend
    assert 'autocomplete="new-password"' in frontend
    assert "passwordChanged" in backend
    assert "PLAYER_SITE_ACCOUNT_UPDATE" in backend
    assert "new_password" in backend


def test_admin_can_delete_a_player_site_account_and_its_whitelist_identity_with_two_confirmations():
    backend = read("admin-web/backend/main.py")
    frontend = read("admin-web/frontend/assets/js/cabinet-runtime.js")

    assert "class AdminPlayerAccountDeleteIn" in backend
    assert 'PLAYER_SITE_ACCOUNT_DELETE' in backend
    assert 'DELETE_PLAYER_ACCOUNT' in backend
    assert "def admin_delete_player_account_sync" in backend
    assert "remove_player_from_whitelist_sync" in backend
    assert 'DELETE FROM whitelist_account_links' in backend
    assert 'DELETE FROM site_accounts' in backend
    assert '("password_hashes", "account_id", account_id)' in backend
    assert '("login_sessions", "account_id", account_id)' in backend
    assert '@app.post("/api/players/{player}/site-account/delete")' in backend
    route_start = backend.index('@app.post("/api/players/{player}/site-account/delete")')
    route_end = backend.index("\n\n@app.", route_start + 10)
    route = backend[route_start:route_end]
    assert 'require_sensitive_confirm(request, "PLAYER_SITE_ACCOUNT_DELETE")' in route
    assert "data.confirmation" in route
    assert "admin_delete_player_account_sync" in route

    assert "playerDeleteSiteAccount" in frontend
    assert "PLAYER_SITE_ACCOUNT_DELETE" in frontend
    assert "DELETE_PLAYER_ACCOUNT" in frontend
    assert "/site-account/delete" in frontend


def test_mandate_cleanup_must_not_be_a_broad_text_display_purge():
    election = read("copimine-election-core/src/me/copimine/electioncore/CopiMineElectionCore.java")
    admin_plus = read("copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java")

    assert "isPresidentMandate(item)" in election
    assert "president_mandate" in admin_plus
    assert "purgeManagedFloatingTexts" in admin_plus
    assert "removeAllEntities" not in election
    assert "removeAllEntities" not in admin_plus
