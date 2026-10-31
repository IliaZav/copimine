"""Чат и команды игрока из CoreProtect: настоящая DuckDB, без заглушек."""
import csv
import io
import tempfile
from pathlib import Path

import pytest

duckdb = pytest.importorskip("duckdb")

from backend import history
from test_auth import client, login  # noqa: F401


def headers(session):
    return {"Origin": "http://testserver", "X-CSRF-Token": session["csrf"]}


@pytest.fixture(autouse=True)
def no_leftover_snapshots(tmp_path):
    """Каждый запрос снимает свою копию и обязан её убрать."""
    before = set(Path(tempfile.gettempdir()).glob("cp-history-*"))
    yield
    leftover = set(Path(tempfile.gettempdir()).glob("cp-history-*")) - before
    assert not leftover, "временная копия базы осталась на диске"


def build(settings, chat=(), commands=()):
    """Мини-база в формате CoreProtect: только те таблицы, которые читает модуль."""
    path = history._source(settings)
    path.parent.mkdir(parents=True, exist_ok=True)
    con = duckdb.connect(str(path))
    con.execute("CREATE TABLE co_user(rowid INTEGER,time INTEGER,user VARCHAR,uuid VARCHAR)")
    con.execute("CREATE TABLE co_world(rowid INTEGER,id INTEGER,world VARCHAR)")
    con.execute("CREATE TABLE co_chat(rowid INTEGER,time BIGINT,user INTEGER,wid INTEGER,x INTEGER,y INTEGER,z INTEGER,message VARCHAR)")
    con.execute("CREATE TABLE co_command(rowid INTEGER,time BIGINT,user INTEGER,wid INTEGER,x INTEGER,y INTEGER,z INTEGER,message VARCHAR)")
    con.execute("INSERT INTO co_world VALUES (1,1,'CopiMine'),(2,2,'CopiMine_nether')")
    con.execute("INSERT INTO co_user VALUES (7,0,'kuuso','uuid-kuuso'),(8,0,'Alvarez_227','uuid-alvarez')")
    for table, rows in (("co_chat", chat), ("co_command", commands)):
        for i, (who, when, world, message) in enumerate(rows, 1):
            con.execute("INSERT INTO " + table + " VALUES (?,?,?,?,?,?,?,?)", (i, when, who, world, 10, 64, -5, message))
    con.close()


def test_chat_page_is_newest_first_and_reports_the_snapshot(client):
    session = login(client)
    build(client.app.state.settings, chat=[(7, 1000, 1, "привет"), (7, 2000, 2, "го в незер")])
    response = client.get("/api/v6/admin/players/kuuso/messages?kind=chat", headers=headers(session))
    assert response.status_code == 200, response.text
    body = response.json()
    assert body["player"] == "kuuso"
    assert body["total"] == 2
    assert body["hasMore"] is False
    assert [e["message"] for e in body["entries"]] == ["го в незер", "привет"]
    assert body["entries"][0]["world"] == "CopiMine_nether"
    assert body["entries"][0]["at"] == 2000
    assert body["snapshotAt"] > 0


def test_paging_never_returns_the_same_row_twice(client):
    session = login(client)
    build(client.app.state.settings, commands=[(7, 100 + n, 1, "/say " + str(n)) for n in range(5)])
    first = client.get("/api/v6/admin/players/kuuso/messages?kind=command&limit=2", headers=headers(session)).json()
    second = client.get("/api/v6/admin/players/kuuso/messages?kind=command&limit=2&offset=2", headers=headers(session)).json()
    assert first["hasMore"] is True
    assert [e["message"] for e in first["entries"]] == ["/say 4", "/say 3"]
    assert [e["message"] for e in second["entries"]] == ["/say 2", "/say 1"]


def test_player_is_found_by_site_account_and_nickname_is_case_insensitive(client):
    session = login(client)
    build(client.app.state.settings, commands=[(8, 500, 1, "/weather sun")])
    account = client.get("/api/v6/state").json()["players"][0]["id"]
    by_id = client.get("/api/v6/admin/players/" + account + "/messages?kind=command", headers=headers(session)).json()
    # Аккаунт без привязки отвечает пустой историей, но не 500 и не чужой перепиской.
    assert by_id["entries"] == []
    lower = client.get("/api/v6/admin/players/alvarez_227/messages?kind=command", headers=headers(session)).json()
    assert [e["message"] for e in lower["entries"]] == ["/weather sun"]


def test_bad_paging_and_kind_are_rejected_before_touching_the_database(client):
    session = login(client)
    build(client.app.state.settings)
    for query, code in (("limit=abc", "invalid_paging"), ("offset=-1", "invalid_paging"), ("kind=voice", "invalid_kind")):
        response = client.get("/api/v6/admin/players/kuuso/messages?" + query, headers=headers(session))
        assert response.status_code == 400, (query, response.text)
        assert response.json()["error"]["code"] == code
    # Обход пути отбивает маршрутизатор: главное — не 200 и не чтение файла.
    assert client.get("/api/v6/admin/players/..%2F..%2Fetc/messages", headers=headers(session)).status_code in (400, 404)


def test_without_the_logs_permission_the_history_is_closed(client):
    from backend.auth import create_user

    session = login(client)
    create_user(client.app.state.database, "PlainPlayer", "plain-player-password-2026")
    build(client.app.state.settings, chat=[(7, 1000, 1, "секрет")])
    client.post("/api/v6/auth/logout", headers=headers(session), json={})
    anonymous = client.get("/api/v6/session").json()
    client.post("/api/v6/auth/login", headers={"Origin": "http://testserver", "X-CSRF-Token": anonymous["csrf"]},
                json={"name": "PlainPlayer", "password": "plain-player-password-2026"})
    assert client.get("/api/v6/admin/players/kuuso/messages", headers={}).status_code in (401, 403)

    client.post("/api/v6/auth/logout", headers={}, json={})
    assert client.get("/api/v6/admin/players/kuuso/messages").status_code in (401, 403)


def test_export_is_excel_safe_utf8_csv_and_is_audited(client):
    session = login(client)
    nasty = '=1+1 "кавычки", запятая'
    build(client.app.state.settings, chat=[(7, 1793378109, 1, nasty), (7, 1793378100, 1, "обычное")])
    response = client.get("/api/v6/admin/players/kuuso/messages/export?kind=chat", headers=headers(session))
    assert response.status_code == 200
    assert response.headers["content-type"].startswith("text/csv")
    assert 'filename="copimine-kuuso-chat.csv"' in response.headers["content-disposition"]
    assert response.content.startswith(b"\xef\xbb\xbf"), "BOM нужен, иначе Excel ломает кириллицу"
    rows = list(csv.reader(io.StringIO(response.content.decode("utf-8-sig"))))
    assert rows[0] == ["time", "player", "kind", "world", "x", "y", "z", "message"]
    assert rows[1][7] == "'" + nasty
    with client.app.state.database.transaction() as db:
        audit = db.execute("SELECT action,target FROM cm_site_audit WHERE action='player.messages_export'").fetchall()
    assert audit and audit[-1]["target"] == "kuuso"


def test_missing_coreprotect_answers_unavailable_instead_of_an_empty_list(client):
    session = login(client)
    for path in (history._source(client.app.state.settings),):
        if path.exists():
            path.unlink()
    response = client.get("/api/v6/admin/players/kuuso/messages", headers=headers(session))
    assert response.status_code == 503
    assert response.json()["error"]["code"] == "history_unavailable"