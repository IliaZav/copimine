from dataclasses import replace
import re

from test_auth import client, login


def test_email_challenge_requires_a_ready_transport(client):
    session = client.get("/api/v6/session").json()
    response = client.post("/api/v7/request-code", headers={"Origin": "http://testserver", "X-CSRF-Token": session["csrf"]}, json={"purpose": "register", "username": "MailPlayer", "email": "player@example.invalid", "password": "Example-new-password-long-enough"})
    assert response.status_code == 503
    assert response.json()["error"]["code"] == "mail_not_configured"


def test_code_is_not_returned_and_wrong_attempts_persist(client):
    client.app.state.settings = replace(client.app.state.settings, smtp_host="127.0.0.1", mail_domain_ready=True)
    session = client.get("/api/v6/session").json()
    headers = {"Origin": "http://testserver", "X-CSRF-Token": session["csrf"]}
    response = client.post("/api/v7/request-code", headers=headers, json={"purpose": "register", "username": "MailPlayer", "email": "player@example.invalid", "password": "Example-new-password-long-enough"})
    assert response.status_code == 200, response.text
    assert "code" not in response.json()
    challenge = response.json()["challenge"]
    with client.app.state.database.transaction() as db:
        message = db.execute("SELECT body FROM cm_site_mail_outbox").fetchone()["body"]
    code = re.search(r"\b[0-9]{6}\b", message).group(0)
    wrong = "000001" if code != "000001" else "000002"
    assert client.post("/api/v7/confirm-code", headers=headers, json={"challenge": challenge, "code": wrong}).status_code == 400
    with client.app.state.database.transaction() as db:
        assert db.execute("SELECT attempts FROM cm_site_challenges WHERE id=%s", (challenge,)).fetchone()["attempts"] == 1
    result = client.post("/api/v7/confirm-code", headers=headers, json={"challenge": challenge, "code": code})
    assert result.status_code == 200
    assert result.json()["user"]["username"] == "MailPlayer"
    session = client.get("/api/v6/session").json()
    assert client.post("/api/v7/confirm-code", headers={"Origin": "http://testserver", "X-CSRF-Token": session["csrf"]}, json={"challenge": challenge, "code": code}).status_code == 400


def test_mfa_setup_requires_actual_totp_confirmation(client):
    session = login(client)
    headers = {"Origin": "http://testserver", "X-CSRF-Token": session["csrf"]}
    setup = client.post("/api/v6/auth/totp/setup", headers=headers, json={"password": "This-is-only-a-test-owner-password-2026"})
    assert setup.status_code == 200
    assert setup.json()["secret"]
    assert not client.get("/api/v6/session").json()["user"]["totpEnabled"]
    from backend.mfa import totp
    response = client.post("/api/v6/auth/totp/confirm", headers=headers, json={"totp": totp(setup.json()["secret"])})
    assert response.status_code == 200
    assert client.get("/api/v6/session").json()["user"]["totpEnabled"]
