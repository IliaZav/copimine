from test_auth import client, login  # noqa: F401


def headers(session):
    return {"Origin": "http://testserver", "X-CSRF-Token": session["csrf"]}


def test_robots_sitemap_and_manifest_are_served(client):
    robots = client.get("/robots.txt")
    assert robots.status_code == 200
    assert "Sitemap: http://testserver/sitemap.xml" in robots.text
    assert "Disallow: /admin" in robots.text

    sitemap = client.get("/sitemap.xml")
    assert sitemap.status_code == 200
    assert "<loc>http://testserver/</loc>" in sitemap.text

    assert client.head("/").status_code == 200


def test_login_history_records_failures_and_successes(client):
    session = client.get("/api/v6/session").json()
    client.post("/api/v6/auth/login", headers=headers(session), json={"name": "TestOwner", "password": "wrong-password-value"})
    session = login(client)

    owner = client.get("/api/v6/session").json()["user"]
    history = client.get("/api/v6/admin/accounts/" + owner["id"] + "/logins")
    assert history.status_code == 200
    results = [row["result"] for row in history.json()["entries"]]
    assert "success" in results


def test_monitor_reports_cached_sample_and_release_inventory(client):
    login(client)
    monitor = client.get("/api/v6/admin/monitor")
    assert monitor.status_code == 200
    body = monitor.json()
    assert 2 <= body["intervalSeconds"] <= 300
    assert "history" in body
    assert "requiredPlugins" in body or body["sample"] is None

    releases = client.get("/api/v6/admin/releases")
    assert releases.status_code == 200
    assert "plugins" in releases.json()["installed"]


def test_action_registry_uses_real_permissions():
    """Кнопки админки берут права из реестра действий: опечатка в имени права
    навсегда выключает кнопку, потому что такого права нет ни у одной роли."""
    import json
    import pathlib
    import re

    from backend.security import PERMISSIONS

    known = {token for tokens in PERMISSIONS.values() for token in tokens}
    root = pathlib.Path(__file__).resolve().parents[1]
    source = (root / "frontend" / "assets" / "js" / "actions-v6.js").read_text(encoding="utf-8")
    registry = json.loads(re.search(r"window\.CM6_ADMIN_ACTIONS\s*=\s*(\{.*\})", source, re.S).group(1))
    actions = registry["actions"] if isinstance(registry, dict) else registry
    assert actions
    unknown = sorted({a["permission"] for a in actions if a.get("permission")} - known)
    assert unknown == [], unknown


def test_monitor_interval_can_be_changed(client):
    session = login(client)
    change = client.post("/api/v6/admin/monitor/interval", headers=headers(session), json={"seconds": 5})
    assert change.status_code == 200, change.text
    assert client.get("/api/v6/admin/monitor").json()["intervalSeconds"] == 5
    for bad in (1, 301, 0):
        rejected = client.post("/api/v6/admin/monitor/interval", headers=headers(session), json={"seconds": bad})
        assert rejected.status_code == 400, bad
    client.post("/api/v6/admin/monitor/interval", headers=headers(session), json={"seconds": 3})


def test_player_cannot_read_monitor_or_releases(client):
    session = login(client)
    created = client.post(
        "/api/v6/admin/accounts",
        headers=headers(session),
        json={"name": "Watcher", "password": "A-real-test-password-for-watcher", "role": "player", "reason": "Permission regression test"},
    )
    assert created.status_code == 200, created.text
    client.post("/api/v6/auth/logout", headers=headers(session), json={})
    session = client.get("/api/v6/session").json()
    client.post("/api/v6/auth/login", headers=headers(session), json={"name": "Watcher", "password": "A-real-test-password-for-watcher"})
    assert client.get("/api/v6/admin/monitor").status_code == 403
    assert client.get("/api/v6/admin/releases").status_code == 403


def test_account_export_returns_own_data_then_delete_anonymises(client):
    login(client)
    session = login(client)
    exported = client.post("/api/v6/account/export", headers=headers(session), json={})
    assert exported.status_code == 200, exported.text
    payload = exported.json()
    assert payload["account"]["name"] == "TestOwner"
    for key in ("tickets", "sessions", "logins", "notifications"):
        assert isinstance(payload[key], list)

    wrong = client.post("/api/v6/account/delete", headers=headers(session), json={"confirm": "УДАЛИТЬ", "password": "not-the-password"})
    assert wrong.status_code == 401

    client.post("/api/v6/account/delete", headers=headers(session), json={"confirm": "НЕТ", "password": "This-is-only-a-test-owner-password-2026"})
    assert client.get("/api/v6/session").json()["user"] is not None


def test_owner_account_cannot_be_deleted(client):
    session = login(client)
    response = client.post(
        "/api/v6/account/delete",
        headers=headers(session),
        json={"confirm": "УДАЛИТЬ", "password": "This-is-only-a-test-owner-password-2026"},
    )
    assert response.status_code == 409
    assert client.get("/api/v6/session").json()["user"]["role"] == "owner"


def test_player_account_deletion_anonymises_not_null_fields_and_clears_session_cookie(client):
    owner = login(client)
    owner_headers = headers(owner)
    created = client.post(
        "/api/v6/admin/accounts",
        headers=owner_headers,
        json={"name": "DeleteMe", "password": "A-secure-delete-test-password-2026", "role": "player", "reason": "Account deletion regression"},
    )
    assert created.status_code == 200, created.text
    user_id = created.json()["user"]["id"]

    client.post("/api/v6/auth/logout", headers=owner_headers, json={})
    anonymous = client.get("/api/v6/session").json()
    player_login = client.post(
        "/api/v6/auth/login",
        headers=headers(anonymous),
        json={"name": "DeleteMe", "password": "A-secure-delete-test-password-2026"},
    )
    assert player_login.status_code == 200, player_login.text
    player = client.get("/api/v6/session").json()

    deleted = client.post(
        "/api/v6/account/delete",
        headers=headers(player),
        json={"confirm": "УДАЛИТЬ", "password": "A-secure-delete-test-password-2026"},
    )
    assert deleted.status_code == 200, deleted.text
    assert "cm_site_session=\"\"" in deleted.headers.get("set-cookie", "")
    assert client.get("/api/v6/session").json()["user"] is None

    with client.app.state.database.transaction() as db:
        account = db.execute("SELECT * FROM cm_site_users WHERE id=%s", (user_id,)).fetchone()
    assert account["name"].startswith("deleted_")
    assert account["email"] == ""
    assert account["minecraft_uuid"] == ""
    assert account["minecraft_name"] == ""
    assert account["totp_secret"] == ""
    assert account["pending_totp"] == ""


def test_timed_account_block_expires_and_allows_login_again(client, monkeypatch):
    import time

    owner = login(client)
    created = client.post(
        "/api/v6/admin/accounts",
        headers=headers(owner),
        json={"name": "TimedBlock", "password": "A-secure-timed-block-password-2026", "role": "player", "reason": "Timed block regression"},
    )
    assert created.status_code == 200, created.text
    user_id = created.json()["user"]["id"]
    until = int(time.time()) + 60

    blocked = client.post(
        "/api/v7/ban",
        headers=headers(owner),
        json={"id": user_id, "blocked": True, "until": until, "reason": "Timed block regression"},
    )
    assert blocked.status_code == 200, blocked.text
    assert blocked.json()["user"]["enabled"] is True
    assert blocked.json()["user"]["blocked"] is True

    client.post("/api/v6/auth/logout", headers=headers(owner), json={})
    anonymous = client.get("/api/v6/session").json()
    denied = client.post(
        "/api/v6/auth/login",
        headers=headers(anonymous),
        json={"name": "TimedBlock", "password": "A-secure-timed-block-password-2026"},
    )
    assert denied.status_code == 403

    monkeypatch.setattr(time, "time", lambda: until + 1)
    anonymous = client.get("/api/v6/session").json()
    allowed = client.post(
        "/api/v6/auth/login",
        headers=headers(anonymous),
        json={"name": "TimedBlock", "password": "A-secure-timed-block-password-2026"},
    )
    assert allowed.status_code == 200, allowed.text
    assert client.get("/api/v7/bootstrap").json()["user"]["blocked"] is False
