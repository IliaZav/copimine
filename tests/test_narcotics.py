"""Проверки раздела наркотиков: сайт не должен пропускать данные, которые плагин отвергнет."""

import copy

import pytest

from test_auth import client, login


def _session_headers(client):
    token = client.get("/api/v6/session").json()["csrf"]
    return {"Origin": "http://testserver", "X-CSRF-Token": token}


def _document(client):
    login(client)
    response = client.get("/api/v6/admin/narcotics")
    assert response.status_code == 200, response.text
    return response.json()


def test_narcotics_document_comes_from_plugin_config(client):
    payload = _document(client)
    assert payload["version"], "версия документа нужна для оптимистичной блокировки"
    items = payload["document"]["items"]
    assert {"feta", "kola", "zhuzevo"} <= set(items), items.keys()
    assert items["zhuzevo"]["recipe"] == [], "у zhuzevo рецепта быть не может"
    assert all(len(item["recipe"]) >= 3 for item_id, item in items.items() if item_id != "zhuzevo")
    assert set(payload["visuals"]) >= {"DESATURATE", "CHAOS"}
    # WEAVING и псевдонимы плагин отвергает — в пикере их быть не должно.
    assert "WEAVING" not in payload["effects"]
    assert "CONFUSION" not in payload["effects"] and "NAUSEA" in payload["effects"]


def test_recipe_shorter_than_three_ingredients_is_rejected(client):
    payload = _document(client)
    document = copy.deepcopy(payload["document"])
    document["items"]["feta"]["recipe"] = document["items"]["feta"]["recipe"][:2]
    response = client.put(
        "/api/v6/admin/narcotics",
        headers=_session_headers(client),
        json={"document": document, "version": payload["version"], "reason": "тест короткого рецепта"},
    )
    assert response.status_code == 400, response.text
    assert "3 ингредиента" in response.text


def test_duplicate_recipe_between_items_is_rejected(client):
    payload = _document(client)
    document = copy.deepcopy(payload["document"])
    document["items"]["kola"]["recipe"] = copy.deepcopy(document["items"]["feta"]["recipe"])
    response = client.put(
        "/api/v6/admin/narcotics",
        headers=_session_headers(client),
        json={"document": document, "version": payload["version"], "reason": "тест повторного набора"},
    )
    assert response.status_code == 400, response.text
    assert "повторяться" in response.text


def test_zhuzevo_recipe_must_stay_empty(client):
    payload = _document(client)
    document = copy.deepcopy(payload["document"])
    document["items"]["zhuzevo"]["recipe"] = [{"key": "MATERIAL:SUGAR", "count": 3}]
    response = client.put(
        "/api/v6/admin/narcotics",
        headers=_session_headers(client),
        json={"document": document, "version": payload["version"], "reason": "тест рецепта zhuzevo"},
    )
    assert response.status_code == 400, response.text
    assert "zhuzevo" in response.text


def test_unknown_material_and_duplicate_model_data_are_rejected(client):
    payload = _document(client)
    headers = _session_headers(client)

    document = copy.deepcopy(payload["document"])
    document["items"]["feta"]["recipe"][0]["key"] = "MATERIAL:NOT_A_REAL_ITEM"
    response = client.put(
        "/api/v6/admin/narcotics",
        headers=headers,
        json={"document": document, "version": payload["version"], "reason": "тест несуществующего предмета"},
    )
    assert response.status_code == 400, response.text

    document = copy.deepcopy(payload["document"])
    document["items"]["kola"]["material"] = document["items"]["feta"]["material"]
    document["items"]["kola"]["custom_model_data"] = document["items"]["feta"]["custom_model_data"]
    response = client.put(
        "/api/v6/admin/narcotics",
        headers=headers,
        json={"document": document, "version": payload["version"], "reason": "тест занятого model data"},
    )
    assert response.status_code == 400, response.text
    assert "custom_model_data" in response.text


def test_valid_edit_saves_and_bumps_version(client):
    payload = _document(client)
    document = copy.deepcopy(payload["document"])
    document["items"]["feta"]["overdose_weight"] = 42
    response = client.put(
        "/api/v6/admin/narcotics",
        headers=_session_headers(client),
        json={"document": document, "version": payload["version"], "reason": "изменён вес передозировки"},
    )
    assert response.status_code == 200, response.text
    saved = response.json()
    assert saved["version"] != payload["version"]
    assert saved["document"]["items"]["feta"]["overdose_weight"] == 42

    stale = client.put(
        "/api/v6/admin/narcotics",
        headers=_session_headers(client),
        json={"document": document, "version": payload["version"], "reason": "устаревшая версия"},
    )
    assert stale.status_code == 409, stale.text


def test_catalog_media_urls_are_limited_to_site_files():
    # PATCH каталога требует повторного подтверждения входа, поэтому проверяем сами правила адреса.
    from backend.domain import _CATALOG_IMAGE, _CATALOG_MEDIA

    assert _CATALOG_MEDIA.fullmatch("/api/v6/media/" + "a" * 24 + ".mp4")
    assert _CATALOG_MEDIA.fullmatch("/api/v6/media/" + "a" * 24 + ".webm")
    assert _CATALOG_MEDIA.fullmatch("/api/v6/media/" + "a" * 24 + ".webp")
    assert not _CATALOG_MEDIA.fullmatch("javascript:alert(1)")
    assert not _CATALOG_MEDIA.fullmatch("https://evil.example/video.mp4")
    assert _CATALOG_IMAGE.fullmatch("assets/photos/copimine-spawn-day.webp")
    assert not _CATALOG_IMAGE.fullmatch("assets/../../etc/passwd")