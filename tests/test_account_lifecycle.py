"""Жизненный цикл аккаунта: удаление и истечение временной блокировки."""

import time

from test_auth import client, login  # noqa: F401
from test_ops import headers


def create(client, session, name, role="player"):
    created = client.post(
        "/api/v6/admin/accounts",
        headers=headers(session),
        json={"name": name, "password": "A-real-test-password-" + name, "role": role, "reason": "Lifecycle test"},
    )
    assert created.status_code == 200, created.text
    return created.json()["user"]


def test_deleted_account_anonymises_not_null_columns_and_clears_cookie(client):
    session = login(client)
    victim = create(client, session, "DeletedPlayer")

    client.post("/api/v6/auth/logout", headers=headers(session), json={})
    session = client.get("/api/v6/session").json()
    client.post(
        "/api/v6/auth/login",
        headers=headers(session),
        json={"name": "DeletedPlayer", "password": "A-real-test-password-DeletedPlayer"},
    )
    victim_session = client.get("/api/v6/session").json()
    assert victim_session["user"]["id"] == victim["id"]

    response = client.post(
        "/api/v6/account/delete",
        headers=headers(victim_session),
        json={"confirm": "УДАЛИТЬ", "password": "A-real-test-password-DeletedPlayer"},
    )
    assert response.status_code == 200, response.text
    alias = response.json()["alias"]
    assert alias.startswith("deleted_")
    # Cookie must be cleared by the response, otherwise the browser keeps a dead session.
    assert "cm_site_session" in response.headers.get("set-cookie", "")

    # Session is gone and the anonymised row keeps every NOT NULL column filled.
    assert client.get("/api/v6/session").json()["user"] is None
    stored = client.app.state.database.transaction()
    with stored as db:
        row = dict(db.execute("SELECT * FROM cm_site_users WHERE id=%s", (victim["id"],)).fetchone())
    assert row["name"] == alias
    assert row["email_norm"] == alias + "@deleted.invalid"
    assert row["minecraft_uuid"] == ""
    assert row["totp_secret"] == ""
    assert row["enabled"] == 0
    for column in ("name", "name_norm", "display_name", "email", "email_norm", "password_hash"):
        assert row[column] is not None, column

    # The anonymised name is unusable for login.
    session = client.get("/api/v6/session").json()
    retry = client.post("/api/v6/auth/login", headers=headers(session), json={"name": alias, "password": "anything-at-all"})
    assert retry.status_code in (401, 403)


def test_expired_temporary_block_lifts_itself(client):
    session = login(client)
    victim = create(client, session, "BlockedPlayer")

    banned = client.post(
        "/api/v7/ban",
        headers=headers(session),
        json={"id": victim["id"], "blocked": True, "until": int(time.time()) + 300, "reason": "Temporary lifecycle test"},
    )
    assert banned.status_code == 200, banned.text

    past = int(time.time()) - 60
    with client.app.state.database.transaction() as db:
        db.execute("UPDATE cm_site_users SET enabled=0,blocked_until_s=%s WHERE id=%s", (past, victim["id"]))

    client.post("/api/v6/auth/logout", headers=headers(session), json={})
    session = client.get("/api/v6/session").json()
    result = client.post(
        "/api/v6/auth/login",
        headers=headers(session),
        json={"name": "BlockedPlayer", "password": "A-real-test-password-BlockedPlayer"},
    )
    assert result.status_code == 200, result.text

    with client.app.state.database.transaction() as db:
        row = dict(db.execute("SELECT enabled,blocked_reason,blocked_until_s FROM cm_site_users WHERE id=%s", (victim["id"],)).fetchone())
    assert row["enabled"] == 1
    assert row["blocked_until_s"] == 0
