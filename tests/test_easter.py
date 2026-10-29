"""Пасхалка, текстуры кастомных предметов и отказ при заражённом файле."""

import base64
import dataclasses
import io

from PIL import Image

from backend import antivirus
from test_auth import client, login


def _headers(client):
    return {"Origin": "http://testserver", "X-CSRF-Token": client.get("/api/v6/session").json()["csrf"]}


def _png(size=(16, 16), colour=(20, 80, 42, 255)):
    out = io.BytesIO()
    Image.new("RGBA", size, colour).save(out, format="PNG")
    return out.getvalue()


def test_teapot_records_hits_and_shows_them_in_admin(client):
    login(client)
    page = client.get("/teapot")
    assert page.status_code == 418
    assert "паста" not in page.text.lower()

    entries = client.get("/api/v6/admin/easter").json()
    assert entries["total"] == 1
    entry = entries["entries"][0]
    assert entry["path"] == "/teapot" and entry["status"] == 418
    assert entry["user"] == "TestOwner" and entry["user_id"]


def test_teapot_stays_hidden_from_the_site(client):
    body = client.get("/").text
    assert "teapot" not in body


def test_admin_easter_requires_logs_permission(client):
    assert client.get("/api/v6/admin/easter").status_code in (401, 403)


def test_clearing_the_easter_log_keeps_the_audit_trail(client):
    login(client)
    client.get("/teapot")
    headers = _headers(client)
    assert client.delete("/api/v6/admin/easter", headers=headers).status_code == 200
    assert client.get("/api/v6/admin/easter").json()["total"] == 0
    assert any(row["action"] == "easter.cleared" for row in client.get("/api/v6/admin/audit").json()["entries"])


def test_custom_texture_is_stored_as_a_sixteen_pixel_pack_file(client, tmp_path):
    login(client)
    client.app.state.settings = dataclasses.replace(client.app.state.settings, private=tmp_path / "private")
    headers = _headers(client)
    item = next(row for row in client.get("/api/v6/admin/catalog/textures").json()["items"]
                if row["id"] == "zmei_gorynych")
    assert item["custom"] is False
    response = client.post("/api/v6/admin/catalog/zmei_gorynych/texture", headers=headers, json={
        "image": base64.b64encode(_png((64, 64))).decode("ascii"), "reason": "Новая текстура на тесте",
    })
    assert response.status_code == 200, response.text
    stored = tmp_path / "private" / "catalog-textures" / "zmei_gorynych.png"
    with Image.open(stored) as image:
        assert image.size == (16, 16)

    listed = client.get("/api/v6/admin/catalog/textures").json()
    assert listed["changed"] == 1

    pack = client.get("/api/v6/admin/catalog/textures/pack.zip")
    assert pack.status_code == 200
    assert b"assets/copimine/textures/item/artifacts/zmei_gorynych.png" in pack.content


def test_texture_upload_rejects_an_unknown_item(client):
    login(client)
    response = client.post("/api/v6/admin/catalog/no_such_item/texture", headers=_headers(client), json={
        "image": base64.b64encode(_png()).decode("ascii"), "reason": "Проверка неизвестного предмета",
    })
    assert response.status_code == 404


def test_infected_upload_is_deleted_and_never_stored(client, tmp_path, monkeypatch):
    login(client)
    client.app.state.settings = dataclasses.replace(client.app.state.settings, private=tmp_path / "private")
    monkeypatch.setattr(antivirus, "scan", lambda path: ("infected", "Eicar-Test-Signature"))
    response = client.post("/api/v6/admin/catalog/zmei_gorynych/texture", headers=_headers(client), json={
        "image": base64.b64encode(_png()).decode("ascii"), "reason": "Проверка заражённого файла",
    })
    assert response.status_code == 422
    assert not (tmp_path / "private" / "catalog-textures" / "zmei_gorynych.png").exists()


def test_upload_is_refused_when_the_scanner_is_down(client, tmp_path, monkeypatch):
    login(client)
    client.app.state.settings = dataclasses.replace(client.app.state.settings, private=tmp_path / "private")
    monkeypatch.setattr(antivirus, "scan", lambda path: ("unavailable", "clamd not running"))
    response = client.post("/api/v6/admin/catalog/zmei_gorynych/texture", headers=_headers(client), json={
        "image": base64.b64encode(_png()).decode("ascii"), "reason": "Проверка недоступного антивируса",
    })
    assert response.status_code == 503
    assert not (tmp_path / "private" / "catalog-textures" / "zmei_gorynych.png").exists()