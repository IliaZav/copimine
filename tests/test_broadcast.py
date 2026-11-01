"""Рассылка игрокам из админки."""
from dataclasses import replace

from test_auth import client, login


def _head(client):
    session = client.get("/api/v6/session").json()
    return {"Origin": "http://testserver", "X-CSRF-Token": session["csrf"]}


def _ready(client):
    client.app.state.settings = replace(
        client.app.state.settings, smtp_host="127.0.0.1", mail_domain_ready=True
    )


def test_broadcast_requires_permission(client):
    session = client.get("/api/v6/session").json()
    response = client.post(
        "/api/v6/admin/broadcast",
        headers={"Origin": "http://testserver", "X-CSRF-Token": session["csrf"]},
        json={"subject": "Тест", "body": "Привет"},
    )
    assert response.status_code in (401, 403)


def test_broadcast_rejects_empty_fields(client):
    _ready(client)
    login(client)
    response = client.post(
        "/api/v6/admin/broadcast", headers=_head(client), json={"subject": "", "body": ""}
    )
    assert response.status_code == 400


def test_broadcast_rejects_script_markup(client):
    _ready(client)
    login(client)
    response = client.post(
        "/api/v6/admin/broadcast", headers=_head(client),
        json={"subject": "Тест", "body": "Привет <script>alert(1)</script>"},
    )
    assert response.status_code == 400


def test_broadcast_queues_only_verified_addresses(client):
    _ready(client)
    login(client)
    with client.app.state.database.transaction() as db:
        db.execute(
            "UPDATE cm_site_users SET email=%s, email_norm=%s, email_verified=1 WHERE name=%s",
            ("verified@example.invalid", "verified@example.invalid", "TestOwner"),
        )
    response = client.post(
        "/api/v6/admin/broadcast", headers=_head(client),
        json={"subject": "Сервер открыт", "body": "Заходите в игру.\nБудем рады."},
    )
    assert response.status_code == 200, response.text
    assert response.json()["queued"] >= 1
    with client.app.state.database.transaction() as db:
        row = db.execute(
            "SELECT recipient, subject, html FROM cm_site_mail_outbox ORDER BY created_s DESC LIMIT 1"
        ).fetchone()
    assert row["recipient"] == "verified@example.invalid"
    assert "CopiMine" in row["subject"]
    assert "Заходите в игру" in row["html"]


def test_broadcast_neutralises_html_in_body(client):
    _ready(client)
    login(client)
    with client.app.state.database.transaction() as db:
        db.execute(
            "UPDATE cm_site_users SET email=%s, email_norm=%s, email_verified=1 WHERE name=%s",
            ("html@example.invalid", "html@example.invalid", "TestOwner"),
        )
    client.post(
        "/api/v6/admin/broadcast", headers=_head(client),
        json={"subject": "Тема", "body": "Угловые скобки < и > безопасны"},
    )
    with client.app.state.database.transaction() as db:
        html = db.execute(
            "SELECT html FROM cm_site_mail_outbox ORDER BY created_s DESC LIMIT 1"
        ).fetchone()["html"]
    assert "&lt;" in html and "&gt;" in html


def test_test_send_goes_to_one_address_only(client):
    _ready(client)
    login(client)
    response = client.post(
        "/api/v6/admin/broadcast", headers=_head(client),
        json={"subject": "Проверка", "body": "Тест", "test": "owner@example.invalid"},
    )
    assert response.status_code == 200, response.text
    assert response.json()["queued"] == 1
    with client.app.state.database.transaction() as db:
        row = db.execute(
            "SELECT recipient FROM cm_site_mail_outbox ORDER BY created_s DESC LIMIT 1"
        ).fetchone()
    assert row["recipient"] == "owner@example.invalid"


def test_broadcast_is_audited(client):
    _ready(client)
    login(client)
    client.post(
        "/api/v6/admin/broadcast", headers=_head(client),
        json={"subject": "Аудит", "body": "Текст", "test": "a@example.invalid"},
    )
    with client.app.state.database.transaction() as db:
        row = db.execute(
            "SELECT count(*) AS n FROM cm_site_audit WHERE action LIKE %s", ("broadcast.%",)
        ).fetchone()
    assert int(row["n"]) >= 1
