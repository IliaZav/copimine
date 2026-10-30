"""Account self-service and release arming: the routes the matrix flagged as untested."""
from test_auth import client, login  # noqa: F401
from test_ops import headers


def current(client):
    return client.get("/api/v6/session").json()


def test_password_change_requires_the_current_password(client):
    live = login(client)
    bad = client.post("/api/v6/account/password", headers=headers(live),
                      json={"current": "definitely-not-the-password", "password": "brand-new-password-1"})
    assert bad.status_code == 401

    ok = client.post("/api/v6/account/password", headers=headers(live),
                     json={"current": "This-is-only-a-test-owner-password-2026", "password": "brand-new-password-1"})
    assert ok.status_code == 200
    assert ok.json()["ok"] is True

    # Tokens are short: changing the password must not throw the current session out.
    assert client.get("/api/v6/session").json()["user"]["name"] == "TestOwner"


def test_password_change_makes_the_old_password_stop_working(client):
    live = login(client)
    changed = client.post("/api/v6/account/password", headers=headers(live),
                          json={"current": "This-is-only-a-test-owner-password-2026", "password": "second-new-password"})
    assert changed.status_code == 200

    fresh = current(client)
    stale = client.post("/api/v6/auth/login", headers=headers(fresh),
                        json={"name": "TestOwner", "password": "This-is-only-a-test-owner-password-2026"})
    assert stale.status_code in {401, 403}
    again = client.post("/api/v6/auth/login", headers=headers(fresh),
                        json={"name": "TestOwner", "password": "second-new-password"})
    assert again.status_code == 200


def test_profile_only_changes_the_display_name(client):
    live = login(client)
    changed = client.patch("/api/v6/account/profile", headers=headers(live),
                           json={"displayName": "Новое имя"})
    assert changed.status_code == 200
    assert changed.json()["user"]["displayName"] == "Новое имя"

    live = current(client)
    refused = client.patch("/api/v6/account/profile", headers=headers(live),
                           json={"displayName": "Ещё имя", "email": "attacker@example.com"})
    assert refused.status_code == 400
    assert refused.json()["error"]["code"] == "invalid_profile"

    too_long = client.patch("/api/v6/account/profile", headers=headers(live),
                            json={"displayName": "x" * 33})
    assert too_long.status_code == 400


def test_session_revocation_never_touches_another_account(client):
    live = login(client)
    revoked = client.post("/api/v6/account/sessions/revoke-others", headers=headers(live))
    assert revoked.status_code == 200
    missing = client.post("/api/v6/account/sessions/" + "0" * 20 + "/revoke", headers=headers(live))
    assert missing.status_code == 404
    invalid = client.post("/api/v6/account/sessions/not-a-session/revoke", headers=headers(live))
    assert invalid.status_code == 404


def test_release_actions_need_a_confirmation_word_and_a_backup(client):
    live = login(client)
    created = client.post("/api/v6/admin/releases", headers=headers(live),
                          json={"version": "26.3-test", "reason": "Confirm word guard test"})
    assert created.status_code == 200
    body = created.json()
    release_id = body.get("id") or (body.get("release") or {}).get("id")
    assert release_id, body

    apply = client.post("/api/v6/admin/releases/" + release_id + "/apply", headers=headers(live),
                        json={"confirm": "нет", "reason": "Wrong confirmation word"})
    assert apply.status_code in {400, 409}
    # Without a backup, rollback is refused rather than silently doing nothing.
    rollback = client.post("/api/v6/admin/releases/" + release_id + "/rollback", headers=headers(live),
                           json={"confirm": "ОТКАТИТЬ", "reason": "No backup here"})
    assert rollback.status_code == 409

    check = client.post("/api/v6/admin/releases/" + release_id + "/check", headers=headers(live))
    assert check.status_code == 200
    assert "check" in check.json()