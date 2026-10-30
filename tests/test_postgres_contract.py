"""Те же критичные пути, но на PostgreSQL: SQLite скрывает ошибки %s/%% и типов.

Локально модуль скипается. На сервере:
    TEST_POSTGRES_URL=postgresql://user:pass@127.0.0.1:5432/db pytest tests/test_postgres_contract.py
"""

import os
import time
import uuid
from pathlib import Path

import pytest

URL = os.environ.get("TEST_POSTGRES_URL", "")
pytestmark = pytest.mark.skipif(not URL, reason="TEST_POSTGRES_URL не задан")


@pytest.fixture
def client():
    from fastapi.testclient import TestClient
    from backend.app import create_app
    from backend.config import Settings

    settings = Settings(
        database_url=URL,
        secret_key="test-only-signing-secret-" * 3,
        public_url="http://testserver",
        secure_cookies=False,
        bootstrap_owner="TestOwner",
        bootstrap_password="This-is-only-a-test-owner-password-2026",
        frontend=Path(os.environ.get("APP_ROOT", "/opt/copimine-site")) / "frontend",
        private=Path(os.environ.get("APP_ROOT", "/opt/copimine-site")) / "private",
    )
    with TestClient(create_app(settings)) as session:
        yield session


def headers(client):
    session = client.get("/api/v6/session").json()
    result = client.post(
        "/api/v6/auth/login",
        headers={"Origin": "http://testserver", "X-CSRF-Token": session["csrf"]},
        json={"name": "TestOwner", "password": "This-is-only-a-test-owner-password-2026", "remember": False},
    )
    assert result.status_code == 200, result.text
    session = client.get("/api/v6/session").json()
    return {"Origin": "http://testserver", "X-CSRF-Token": session["csrf"]}


def test_like_buckets_and_paging_run_on_postgres(client):
    h = headers(client)
    now = int(time.time())
    with client.app.state.database.transaction() as db:
        for index, action in enumerate(("auth.login", "game.kick", "shop.buy", "ticket.created", "map.published")):
            db.execute("INSERT INTO cm_site_audit(id,actor_id,actor_name,action,target,reason,created_s)"
                       " VALUES(%s,%s,%s,%s,%s,%s,%s)",
                       (uuid.uuid4().hex, "system", "System", action, "probe-target", "проверка", now + index))

    for bucket, expected in (("account", "auth.login"), ("moderation", "game.kick"),
                             ("economy", "shop.buy"), ("content", "ticket.created"),
                             ("server", "map.published")):
        response = client.get("/api/v6/admin/audit", params={"bucket": bucket, "limit": 5}, headers=h)
        assert response.status_code == 200, response.text
        codes = {entry["kind"] for entry in response.json()["entries"]}
        assert expected in codes
        assert codes <= {expected}, (bucket, codes)

    first = client.get("/api/v6/admin/audit", params={"limit": 2, "offset": 0}, headers=h).json()
    second = client.get("/api/v6/admin/audit", params={"limit": 2, "offset": 2}, headers=h).json()
    assert len(first["entries"]) == 2 and first["hasMore"] is True
    assert {e["id"] for e in first["entries"]}.isdisjoint({e["id"] for e in second["entries"]})
    assert first["total"] >= 5

    likes = client.get("/api/v6/admin/audit", params={"q": "проверка"}, headers=h).json()
    assert likes["total"] >= 5


def test_one_notification_per_admin_and_templates_on_postgres(client):
    h = headers(client)
    # Автор обращения не получает уведомление, поэтому нужен второй админ.
    created_admin = client.post("/api/v6/admin/accounts", headers=h, json={
        "name": "PostgresHelper", "password": "Helper-only-password-2026",
        "role": "moderator", "reason": "Проверка уведомлений на PostgreSQL",
    })
    assert created_admin.status_code == 200, created_admin.text
    with client.app.state.database.transaction() as db:
        admins = db.execute("SELECT id FROM cm_site_users WHERE enabled=1").fetchall()
    assert len(admins) == 2

    ticket = client.post("/api/v6/tickets", headers=h,
                         json={"title": "Проверка PostgreSQL", "body": "Тело обращения для проверки."})
    assert ticket.status_code == 200, ticket.text
    ticket_id = ticket.json()["ticket"]["id"]

    with client.app.state.database.transaction() as db:
        rows = db.execute("SELECT id,user_id FROM cm_site_notifications WHERE id LIKE %s",
                          ("ticket-new:" + ticket_id + ":%",)).fetchall()
        author = db.execute("SELECT id FROM cm_site_users WHERE name_norm=%s", ("testowner",)).fetchone()
    assert len(rows) == 1, "уведомление должно получить ровно одно не-авторское лицо"
    assert rows[0]["user_id"] != author["id"]
    assert len({row["id"] for row in rows}) == len(rows)

    created = client.post("/api/v6/admin/ticket-templates", headers=h,
                          json={"title": "Шаблон PG", "body": "Готовый ответ из PostgreSQL."})
    assert created.status_code == 200, created.text
    template_id = created.json()["template"]["id"]
    listed = client.get("/api/v6/admin/ticket-templates", headers=h).json()["templates"]
    assert any(item["id"] == template_id for item in listed)
    assert client.delete("/api/v6/admin/ticket-templates/" + template_id, headers=h).status_code == 200
    assert client.delete("/api/v6/admin/ticket-templates/" + template_id, headers=h).status_code == 404


def test_roles_and_rollback_on_postgres(client):
    h = headers(client)
    roles = client.get("/api/v6/admin/roles", headers=h).json()["roles"]
    assert {role["id"] for role in roles} >= {"owner", "player"}
    owner = next(role for role in roles if role["id"] == "owner")
    assert owner["rank"] == 100 and "server.manage" in owner["permissions"]

    from backend.security import fail

    with pytest.raises(Exception):
        with client.app.state.database.transaction() as db:
            db.execute("INSERT INTO cm_site_ticket_templates(id,title,body,actor_id,actor_name,created_s)"
                       " VALUES(%s,%s,%s,%s,%s,%s)", ("rollback-probe", "t", "b", "a", "A", 1))
            fail(400, "probe", "Откат транзакции на PostgreSQL.")
    with client.app.state.database.transaction() as db:
        assert not db.execute("SELECT id FROM cm_site_ticket_templates WHERE id=%s", ("rollback-probe",)).fetchone()