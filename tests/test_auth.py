from pathlib import Path
import importlib.util

import pytest


def test_production_application_exists():
    assert Path(__file__).parents[1].joinpath("backend", "app.py").exists(), "Production API has not been implemented"


@pytest.fixture
def client(tmp_path):
    if importlib.util.find_spec("backend.app") is None:
        pytest.fail("Production application factory is missing")
    from fastapi.testclient import TestClient
    from backend.app import create_app
    from backend.config import Settings

    settings = Settings(
        database_url="sqlite:///" + str(tmp_path / "website.sqlite3"),
        secret_key="test-only-signing-secret-" * 3,
        public_url="http://testserver",
        secure_cookies=False,
        bootstrap_owner="TestOwner",
        bootstrap_password="This-is-only-a-test-owner-password-2026",
    )
    with TestClient(create_app(settings)) as session:
        yield session


def login(client):
    session = client.get("/api/v6/session").json()
    result = client.post(
        "/api/v6/auth/login",
        headers={"Origin": "http://testserver", "X-CSRF-Token": session["csrf"]},
        json={"name": "TestOwner", "password": "This-is-only-a-test-owner-password-2026", "remember": False},
    )
    assert result.status_code == 200, result.text
    return client.get("/api/v6/session").json()


def test_guest_state_never_contains_users_or_private_catalog(client):
    response = client.get("/api/v6/state")
    assert response.status_code == 200
    state = response.json()
    assert state["players"] == []
    assert state["audit"] == []
    assert all(x.get("visibility") == "public" for x in state["catalog"])
    assert client.get("/api/v7/catalog").status_code == 401
    assert client.get("/review/inbox").status_code == 404


def test_login_cookie_and_server_issued_owner_role(client):
    session = login(client)
    assert session["user"]["role"] == "owner"
    assert "server.manage" in session["user"]["permissions"]
    assert "HttpOnly" in client.get("/api/v6/session").headers.get("set-cookie", "") or client.cookies.get("cm_site_session")


def test_mutation_rejects_missing_csrf_and_foreign_origin(client):
    session = login(client)
    for headers in ({"Origin": "http://testserver"}, {"Origin": "https://attacker.invalid", "X-CSRF-Token": session["csrf"]}):
        response = client.post("/api/v6/auth/logout", json={}, headers=headers)
        assert response.status_code == 403
    assert client.get("/api/v6/session").json()["user"]["role"] == "owner"


def test_revoked_cookie_cannot_restore_session(client):
    session = login(client)
    old_cookie = client.cookies.get("cm_site_session")
    response = client.post("/api/v6/auth/logout", headers={"Origin": "http://testserver", "X-CSRF-Token": session["csrf"]}, json={})
    assert response.status_code == 200
    client.cookies.set("cm_site_session", old_cookie, domain="testserver.local", path="/")
    assert client.get("/api/v6/session").json()["user"] is None


def test_unconfigured_providers_do_not_simulate_success(client):
    providers = client.get("/api/v6/providers").json()
    assert providers["vk"]["enabled"] is False
    assert providers["mail"]["ready"] is False
    assert client.get("/api/v6/auth/vk/start").status_code == 503


def test_failed_login_never_accepts_demo_credentials(client):
    session = client.get("/api/v6/session").json()
    for name in ("1", "2", "3"):
        response = client.post("/api/v6/auth/login", headers={"Origin": "http://testserver", "X-CSRF-Token": session["csrf"]}, json={"name": name, "password": name})
        assert response.status_code == 401


def test_registration_uses_only_username_and_password(client):
    session = client.get("/api/v6/session").json()
    response = client.post(
        "/api/v6/auth/register",
        headers={"Origin": "http://testserver", "X-CSRF-Token": session["csrf"]},
        json={"username": "SimplePlayer", "password": "A-simple-test-password-2026"},
    )
    assert response.status_code == 200, response.text
    current = client.get("/api/v6/session").json()["user"]
    assert current["username"] == "SimplePlayer"
    assert current["email"] == ""
    assert current["emailVerified"] is False


def test_email_cannot_be_used_as_login_name(client):
    from backend.auth import create_user

    create_user(client.app.state.database, "MailAddressLogin", "A-simple-test-password-2026", email="player@example.test", email_verified=True)
    session = client.get("/api/v6/session").json()
    response = client.post(
        "/api/v6/auth/login",
        headers={"Origin": "http://testserver", "X-CSRF-Token": session["csrf"]},
        json={"username": "player@example.test", "password": "A-simple-test-password-2026"},
    )
    assert response.status_code == 401
