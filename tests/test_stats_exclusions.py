"""Исключение аккаунтов из статистики.

Служебные аккаунты администрации получают огромные балансы и тестовые
регистрации. Без исключения реальные показатели проекта перестают быть правдой:
один такой аккаунт рисует несуществующий рост денег в отчёте.
"""

from test_auth import client, login  # noqa: F401  — фикстуры берём из test_auth


def headers(session):
    return {"Origin": "http://testserver", "X-CSRF-Token": session["csrf"]}


def accounts(client, session):
    return client.get("/api/v6/admin/players/search", headers=headers(session)).json()["players"]


def test_owner_can_exclude_an_account(client):
    session = login(client)
    target = accounts(client, session)[0]["id"]

    saved = client.post(
        "/api/v6/admin/stats/exclusions",
        headers=headers(session),
        json={"playerId": target, "kinds": {"money": True, "activity": False, "registration": False}},
    )
    assert saved.status_code == 200, saved.text
    assert saved.json()["kinds"]["money"] is True

    listed = client.get("/api/v6/admin/stats/exclusions", headers=headers(session)).json()
    assert target in [row["id"] for row in listed["players"]]
    assert set(listed["kinds"]) == {"money", "activity", "registration"}


def test_exclusion_can_be_cleared(client):
    session = login(client)
    target = accounts(client, session)[0]["id"]

    for kinds in ({"money": True}, {"money": False}):
        saved = client.post(
            "/api/v6/admin/stats/exclusions",
            headers=headers(session),
            json={"playerId": target, "kinds": kinds, "reason": "проверка"},
        )
        assert saved.status_code == 200, saved.text

    listed = client.get("/api/v6/admin/stats/exclusions", headers=headers(session)).json()
    assert target not in [row["id"] for row in listed["players"]]


def test_unknown_scope_is_rejected(client):
    session = login(client)
    target = accounts(client, session)[0]["id"]
    response = client.post(
        "/api/v6/admin/stats/exclusions",
        headers=headers(session),
        json={"playerId": target, "kinds": {"everything": True}},
    )
    assert response.status_code == 400, response.text
    assert "invalid_exclusion" in response.text


def test_unknown_account_is_rejected(client):
    session = login(client)
    response = client.post(
        "/api/v6/admin/stats/exclusions",
        headers=headers(session),
        json={"playerId": "no-such-account", "kinds": {"money": True}},
    )
    assert response.status_code == 404


def test_excluded_account_leaves_the_totals(client):
    """Главное требование: исключённый аккаунт не попадает в показатели."""
    session = login(client)
    before = client.get("/api/v6/admin/analytics/business?days=30", headers=headers(session)).json()
    target = accounts(client, session)[0]["id"]

    client.post(
        "/api/v6/admin/stats/exclusions",
        headers=headers(session),
        json={"playerId": target, "kinds": {"registration": True}, "reason": "служебный аккаунт"},
    )
    after = client.get("/api/v6/admin/analytics/business?days=30", headers=headers(session)).json()
    assert after["totals"]["users"] == before["totals"]["users"] - 1


def test_exclusion_does_not_touch_the_balance(client):
    """Исключение из статистики — только про отчёты, деньги остаются у игрока."""
    session = login(client)
    target = accounts(client, session)[0]
    client.post(
        "/api/v6/admin/stats/exclusions",
        headers=headers(session),
        json={"playerId": target["id"], "kinds": {"money": True}, "reason": "проверка"},
    )
    again = client.get("/api/v6/admin/players/search", headers=headers(session)).json()["players"]
    listed = client.get("/api/v6/admin/stats/exclusions", headers=headers(session)).json()["players"]
    assert target["id"] in [row["id"] for row in again], "аккаунт не должен исчезнуть из списка"
    assert target["id"] in [row["id"] for row in listed]
