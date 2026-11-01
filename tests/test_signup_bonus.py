"""Приветственный бонус донат-валюты за подтверждённую регистрацию."""
import re
from dataclasses import replace

from test_auth import client, login


def _register(client, username="BonusPlayer", email="bonus@example.invalid"):
    """Зарегистрировать игрока через почтовый код и вернуть его user_id."""
    client.app.state.settings = replace(
        client.app.state.settings, smtp_host="127.0.0.1", mail_domain_ready=True
    )
    session = client.get("/api/v6/session").json()
    headers = {"Origin": "http://testserver", "X-CSRF-Token": session["csrf"]}
    response = client.post(
        "/api/v7/request-code",
        headers=headers,
        json={"purpose": "register", "username": username, "email": email,
              "password": "CorrectHorseBattery1!", "terms": True, "consent": True},
    )
    assert response.status_code == 200, response.text
    challenge = response.json()["challenge"]
    with client.app.state.database.transaction() as db:
        body = db.execute("SELECT body FROM cm_site_mail_outbox").fetchone()["body"]
    code = re.search(r"\b[0-9]{6}\b", body).group(0)
    confirm = client.post(
        "/api/v7/confirm-code", headers=headers,
        json={"challenge": challenge, "code": code},
    )
    assert confirm.status_code == 200, confirm.text
    with client.app.state.database.transaction() as db:
        return db.execute(
            "SELECT id FROM cm_site_users WHERE name=%s", (username,)
        ).fetchone()["id"]


def test_signup_marks_bonus_for_the_new_account(client):
    user_id = _register(client)
    with client.app.state.database.transaction() as db:
        row = db.execute(
            "SELECT amount, dedup_player FROM cm_site_signup_bonus WHERE id=%s", (user_id,)
        ).fetchone()
    assert row is not None, "бонус не зафиксирован"
    assert int(row["amount"]) == 50
    assert row["dedup_player"] == ""


def test_bonus_is_granted_once_per_account(client):
    user_id = _register(client, "BonusOnce", "bonus2@example.invalid")
    # Повторная вставка не должна перезаписать или удвоить бонус.
    with client.app.state.database.transaction() as db:
        db.execute(
            "INSERT INTO cm_site_signup_bonus(id, amount, granted_s, dedup_player) "
            "VALUES(%s, 999, 0, '') ON CONFLICT (id) DO NOTHING",
            (user_id,),
        )
    with client.app.state.database.transaction() as db:
        row = db.execute(
            "SELECT amount FROM cm_site_signup_bonus WHERE id=%s", (user_id,)
        ).fetchone()
    assert int(row["amount"]) == 50


def test_unconfirmed_registration_grants_nothing(client):
    """Пока код не введён, аккаунта нет — и бонуса тоже."""
    client.app.state.settings = replace(
        client.app.state.settings, smtp_host="127.0.0.1", mail_domain_ready=True
    )
    session = client.get("/api/v6/session").json()
    headers = {"Origin": "http://testserver", "X-CSRF-Token": session["csrf"]}
    client.post(
        "/api/v7/request-code", headers=headers,
        json={"purpose": "register", "username": "NoConfirm", "email": "noconfirm@example.invalid",
              "password": "CorrectHorseBattery1!", "terms": True, "consent": True},
    )
    with client.app.state.database.transaction() as db:
        users = db.execute("SELECT COUNT(*) AS n FROM cm_site_users WHERE name=%s", ("NoConfirm",)).fetchone()
        bonuses = db.execute("SELECT COUNT(*) AS n FROM cm_site_signup_bonus").fetchone()
    assert int(users["n"]) == 0
    assert int(bonuses["n"]) == 0
