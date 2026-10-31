"""Период в истории чата и команд: фильтр серверный, а не фильтр по загруженной части."""
import time

import pytest

duckdb = pytest.importorskip("duckdb")

from backend import history
from test_auth import client, login  # noqa: F401
from test_history import build, headers


def test_days_limit_both_the_page_and_the_total(client):
    session = login(client)
    now = int(time.time())
    build(client.app.state.settings, chat=[
        (7, now - 2 * 86400, 1, "позавчера"),
        (7, now - 40 * 86400, 1, "сорок дней назад"),
        (7, now - 400 * 86400, 1, "год назад"),
    ])
    week = client.get("/api/v6/admin/players/kuuso/messages?kind=chat&days=7", headers=headers(session)).json()
    assert week["days"] == 7
    assert week["total"] == 1, "счётчик обязан применять тот же фильтр, что и страница"
    assert [e["message"] for e in week["entries"]] == ["позавчера"]

    ninety = client.get("/api/v6/admin/players/kuuso/messages?kind=chat&days=90", headers=headers(session)).json()
    assert [e["message"] for e in ninety["entries"]] == ["позавчера", "сорок дней назад"]

    everything = client.get("/api/v6/admin/players/kuuso/messages?kind=chat", headers=headers(session)).json()
    assert everything["days"] == 0
    assert everything["total"] == 3


def test_period_survives_paging(client):
    session = login(client)
    now = int(time.time())
    build(client.app.state.settings, chat=[(7, now - 3600 * n, 1, "свежее " + str(n)) for n in range(1, 5)]
          + [(7, now - 200 * 86400, 1, "старое")])
    first = client.get("/api/v6/admin/players/kuuso/messages?kind=chat&days=7&limit=2", headers=headers(session)).json()
    second = client.get(
        "/api/v6/admin/players/kuuso/messages?kind=chat&days=7&limit=2&offset=2", headers=headers(session)).json()
    assert first["total"] == second["total"] == 4
    assert first["hasMore"] is True
    assert second["hasMore"] is False
    messages = [e["message"] for e in first["entries"] + second["entries"]]
    assert "старое" not in messages
    assert len(messages) == len(set(messages)) == 4


def test_export_honours_the_period_and_records_it_in_the_audit(client):
    session = login(client)
    now = int(time.time())
    build(client.app.state.settings, chat=[(7, now - 3600, 1, "свежее"), (7, now - 200 * 86400, 1, "старое")])
    response = client.get("/api/v6/admin/players/kuuso/messages/export?kind=chat&days=7", headers=headers(session))
    assert response.status_code == 200
    text = response.content.decode("utf-8-sig")
    assert "свежее" in text and "старое" not in text
    with client.app.state.database.transaction() as db:
        row = db.execute(
            "SELECT details FROM cm_site_audit WHERE action='player.messages_export'"
            " ORDER BY created_s DESC").fetchone()
    assert "7" in str(row["details"]), "в аудите должен остаться период выгрузки"


@pytest.mark.parametrize("days", ["abc", "0", "-7", "2", "100000"])
def test_bad_period_is_rejected_instead_of_silently_ignored(client, days):
    session = login(client)
    build(client.app.state.settings)
    for path in ("/messages", "/messages/export"):
        response = client.get("/api/v6/admin/players/kuuso" + path + "?kind=chat&days=" + days,
                              headers=headers(session))
        assert response.status_code == 400, (path, days, response.text)
        assert response.json()["error"]["code"] == "invalid_period"
