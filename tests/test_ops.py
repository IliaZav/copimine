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
    assert body["intervalSeconds"] >= 30
    assert "history" in body
    assert "requiredPlugins" in body or body["sample"] is None

    releases = client.get("/api/v6/admin/releases")
    assert releases.status_code == 200
    assert "plugins" in releases.json()["installed"]


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