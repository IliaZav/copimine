"""Бизнес-показатели админки по дням."""
from test_auth import client, login


def _head(client):
    session = client.get("/api/v6/session").json()
    return {"Origin": "http://testserver", "X-CSRF-Token": session["csrf"]}


def test_business_analytics_requires_permission(client):
    assert client.get("/api/v6/admin/analytics/business").status_code in (401, 403)


def test_business_analytics_returns_totals_and_series(client):
    login(client)
    response = client.get("/api/v6/admin/analytics/business?days=7", headers=_head(client))
    assert response.status_code == 200, response.text
    data = response.json()
    assert data["days"] == 7
    for key in ("users", "verified", "linked", "active", "open_tickets"):
        assert isinstance(data["totals"][key], int)
    for key in ("registrations", "verified", "linked", "tickets"):
        assert isinstance(data["series"][key], list)
    # Тестовый владелец точно существует.
    assert data["totals"]["users"] >= 1


def test_business_analytics_clamps_period(client):
    login(client)
    too_long = client.get("/api/v6/admin/analytics/business?days=9999", headers=_head(client))
    assert too_long.status_code == 200
    assert too_long.json()["days"] == 365
    negative = client.get("/api/v6/admin/analytics/business?days=-5", headers=_head(client))
    assert negative.status_code == 200
    assert negative.json()["days"] == 1
