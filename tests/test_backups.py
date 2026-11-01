"""Резервные копии базы из админки."""
from test_auth import client, login


def _head(client):
    session = client.get("/api/v6/session").json()
    return {"Origin": "http://testserver", "X-CSRF-Token": session["csrf"]}


def test_backups_require_owner(client):
    response = client.get("/api/v6/admin/backups")
    assert response.status_code in (401, 403)


def test_backup_list_shape_for_owner(client):
    login(client)
    response = client.get("/api/v6/admin/backups", headers=_head(client))
    assert response.status_code == 200, response.text
    data = response.json()
    assert "backups" in data and "dir" in data
    assert isinstance(data["backups"], list)
    # В тестовом окружении каталога может не быть — список просто пустой.
    for entry in data["backups"]:
        assert entry["name"].endswith(".dump")
        assert entry["bytes"] >= 0


def test_manual_backup_requires_owner(client):
    session = client.get("/api/v6/session").json()
    response = client.post(
        "/api/v6/admin/backups",
        headers={"Origin": "http://testserver", "X-CSRF-Token": session["csrf"]},
        json={},
    )
    assert response.status_code in (401, 403)
