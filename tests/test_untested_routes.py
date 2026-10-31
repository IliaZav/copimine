"""Маршруты, которые вызывает фронт, но без собственных тестов до 2026-10-31."""
import pytest

from backend.auth import create_user
from test_auth import client, login  # noqa: F401


def headers(session):
    return {"Origin": "http://testserver", "X-CSRF-Token": session["csrf"]}


def test_minecraft_link_request_never_reaches_the_bridge_for_a_bad_nickname(client, monkeypatch):
    session = login(client)
    adapter = client.app.state.game
    sent = []
    original_rpc = adapter.rpc

    def guarded(operation, actor, parameters=None, key=None):
        sent.append((operation, parameters))
        return original_rpc(operation, actor, parameters, key)

    monkeypatch.setattr(adapter, "rpc", guarded)
    for name in ("не ник", "", "a" * 33, "Steve!"):
        bad = client.post("/api/v6/account/minecraft/request", headers=headers(session), json={"name": name})
        assert bad.status_code == 400, (name, bad.text)
        assert bad.json()["error"]["code"] == "invalid_player"
    assert sent == [], "ник отклоняется до подписанного запроса к серверу"

    monkeypatch.setattr(adapter, "link_start",
                        lambda user, name: sent.append(name) or {"ok": True, "deliveredInGame": True, "expiresAt": 0})
    good = client.post("/api/v6/account/minecraft/request", headers=headers(session), json={"name": "Steve"})
    assert good.status_code == 200, good.text
    assert sent == ["Steve"]


def test_minecraft_link_request_requires_a_session(client):
    session = client.get("/api/v6/session").json()
    response = client.post("/api/v6/account/minecraft/request",
                           headers={"Origin": "http://testserver", "X-CSRF-Token": session["csrf"]},
                           json={"name": "Steve"})
    assert response.status_code == 401, response.text


@pytest.fixture
def linked(client, monkeypatch):
    session = login(client)
    db = client.app.state.database
    target = create_user(db, "Recipient", "recipient-test-password-2026")
    with db.transaction() as conn:
        for identifier, nickname in ((session["user"]["id"], "TestOwner"), (target["id"], "Recipient")):
            conn.execute("UPDATE cm_site_users SET minecraft_uuid=%s,minecraft_name=%s WHERE id=%s",
                         ("uuid-" + identifier, nickname, identifier))
    calls = []

    def transfer(*args):
        calls.append(args)
        return {"txId": "stable-transfer-id"}

    monkeypatch.setattr(client.app.state.game, "transfer", transfer)
    return session, target, calls


def test_account_transfer_alias_moves_money_exactly_once(client, linked):
    session, target, calls = linked
    data = {"to": target["id"], "amount": 50, "pin": "1234", "note": "тест", "key": "alias-transfer-1"}
    first = client.post("/api/v6/account/transfer", headers=headers(session), json=data)
    assert first.status_code == 200, first.text
    assert first.json()["transfer"]["amount"] == 50
    replay = client.post("/api/v6/account/transfer", headers=headers(session), json=data)
    assert replay.json() == first.json()
    assert len(calls) == 1, "повтор с тем же ключом не проводится второй раз"
    other = client.post("/api/v6/account/transfer", headers=headers(session),
                        json={**data, "amount": 60, "key": "alias-transfer-1"})
    assert other.status_code == 409, other.text
    assert other.json()["error"]["code"] == "idempotency_conflict"


def test_account_transfer_rejects_a_bad_amount_before_dispatch(client, linked):
    session, target, calls = linked
    for amount in (0, -5, 1.5, True, "50", 1000000001):
        response = client.post("/api/v6/account/transfer", headers=headers(session),
                               json={"to": target["id"], "amount": amount, "pin": "1234", "key": "alias-transfer-bad"})
        assert response.status_code == 400, (amount, response.text)
    assert calls == []


def test_admin_mapart_lists_uploads_newest_first(client):
    session = login(client)
    with client.app.state.database.transaction() as db:
        for created, name in ((100, "Старая"), (200, "Новая")):
            db.execute("INSERT INTO cm_site_mapart_uploads(id,image,url,name,width,height,command,author_id,"
                       "author_name,world,x,y,z,created_s) VALUES(%s,'','https://example.invalid/a.png',%s,1,1,'',"
                       "'','гость','',NULL,NULL,NULL,%s)",
                       ("mapart-" + str(created), name, created))
    response = client.get("/api/v6/admin/mapart?limit=1", headers=headers(session))
    assert response.status_code == 200, response.text
    items = response.json()["items"]
    assert len(items) == 1 and items[0]["name"] == "Новая", "лимит и порядок сортировки"
    assert client.get("/api/v6/admin/mapart?limit=abc", headers=headers(session)).status_code == 200
    assert client.get("/api/v6/admin/mapart?limit=99999", headers=headers(session)).status_code == 200


def test_admin_mapart_requires_content_permission(client):
    session = login(client)
    db = client.app.state.database
    player = create_user(db, "MapArtViewer", "mapart-viewer-password-2026")
    session = login(client)
    with db.transaction() as conn:
        conn.execute("UPDATE cm_site_sessions SET user_id=%s WHERE user_id=%s",
                     (player["id"], session["user"]["id"]))
    response = client.get("/api/v6/admin/mapart", headers=headers(session))
    assert response.status_code == 403, response.text
    assert response.json()["error"]["code"] == "forbidden"


def test_narcotics_export_is_a_plugin_config_fragment(client):
    session = login(client)
    response = client.get("/api/v6/admin/narcotics/export", headers=headers(session))
    assert response.status_code == 200, response.text
    yaml_text = response.json()["yaml"]
    assert "textures:" in yaml_text and "mode: CUSTOM" in yaml_text
    assert "zhuzevo" in yaml_text, "экспорт идёт из документа плагина, а не из пустого шаблона"
    assert "narcotics" in yaml_text.lower() or "display_name" in yaml_text


def test_release_report_records_what_the_deploy_tool_said(client):
    session = login(client)
    ok = client.post("/api/v6/admin/releases/report", headers=headers(session),
                     json={"commit": "7310c3aa", "ci": {"status": "pass", "url": "https://example.invalid/run"}})
    assert ok.status_code == 200, ok.text
    assert client.app.state.database.setting("release.commit") == "7310c3aa"
    bad = client.post("/api/v6/admin/releases/report", headers=headers(session), json={"commit": "не-хеш"})
    assert bad.status_code == 400, bad.text
    assert bad.json()["error"]["code"] == "invalid_commit"
    bad_ci = client.post("/api/v6/admin/releases/report", headers=headers(session),
                         json={"ci": {"status": "green"}})
    assert bad_ci.status_code == 400, bad_ci.text
    assert bad_ci.json()["error"]["code"] == "invalid_ci"


def test_release_report_needs_release_permission(client):
    login(client)
    db = client.app.state.database
    player = create_user(db, "ReleaseViewer", "release-viewer-password-2026")
    session = login(client)
    with db.transaction() as conn:
        conn.execute("UPDATE cm_site_sessions SET user_id=%s WHERE user_id=%s",
                     (player["id"], session["user"]["id"]))
    response = client.post("/api/v6/admin/releases/report", headers=headers(session), json={"commit": "7310c3aa"})
    assert response.status_code in {401, 403}, response.text


def test_reauth_extends_the_fresh_window_only_with_the_right_password(client):
    session = login(client)
    wrong = client.post("/api/v6/auth/reauth", headers=headers(session), json={"password": "не тот пароль"})
    assert wrong.status_code == 401, wrong.text
    assert wrong.json()["error"]["code"] == "invalid_credentials"
    good = client.post("/api/v6/auth/reauth", headers=headers(session),
                       json={"password": "This-is-only-a-test-owner-password-2026"})
    assert good.status_code == 200, good.text
    assert good.json() == {"ok": True}
    session = client.get("/api/v6/session").json()
    assert client.post("/api/v6/auth/reauth", headers={"Origin": "http://testserver"}, json={"password": "x"}).status_code == 403, "без CSRF токена не пускаем"