"""Поиск и выгрузка игроков из админки."""
from dataclasses import replace

from test_auth import client, login


def _head(client):
    session = client.get("/api/v6/session").json()
    return {"Origin": "http://testserver", "X-CSRF-Token": session["csrf"]}


def test_search_requires_permission(client):
    response = client.get("/api/v6/admin/players/search?q=a")
    assert response.status_code in (401, 403)


def test_search_finds_by_name_email_and_minecraft_nick(client):
    client.app.state.settings = replace(
        client.app.state.settings, smtp_host="127.0.0.1", mail_domain_ready=True
    )
    login(client)
    with client.app.state.database.transaction() as db:
        db.execute(
            "UPDATE cm_site_users SET minecraft_name=%s, minecraft_uuid=%s WHERE name=%s",
            ("SudoKillDash9", "c88df564-4edb-3c73-89cc-842f480e454d", "TestOwner"),
        )
    found = client.get("/api/v6/admin/players/search?q=SudoKill", headers=_head(client)).json()
    assert found["total"] >= 1
    assert any(p["name"] == "TestOwner" for p in found["players"])

    # Поиск по почте: почту проставляем тестовому аккаунту, чтобы проверить именно ту ветку.
    with client.app.state.database.transaction() as db:
        db.execute(
            "UPDATE cm_site_users SET email=%s, email_norm=%s WHERE name=%s",
            ("owner@example.invalid", "owner@example.invalid", "TestOwner"),
        )
    by_email = client.get("/api/v6/admin/players/search?q=owner@example", headers=_head(client)).json()
    assert any(p["name"] == "TestOwner" for p in by_email["players"])


def test_search_ignores_sql_wildcards(client):
    """Символы % и _ в запросе не должны превращаться в шаблон LIKE."""
    login(client)
    result = client.get("/api/v6/admin/players/search?q=%25", headers=_head(client)).json()
    assert result["total"] == 0


def test_export_returns_csv_with_bom(client):
    login(client)
    response = client.get("/api/v6/admin/players/export", headers=_head(client))
    assert response.status_code == 200, response.text
    assert "text/csv" in response.headers["content-type"]
    assert response.text.startswith("\ufeff")
    assert "Логин" in response.text
    assert "TestOwner" in response.text


def test_export_is_audited(client):
    login(client)
    client.get("/api/v6/admin/players/export", headers=_head(client))
    with client.app.state.database.transaction() as db:
        row = db.execute(
            "SELECT count(*) AS n FROM cm_site_audit WHERE action=%s", ("players.export",)
        ).fetchone()
    assert int(row["n"]) >= 1