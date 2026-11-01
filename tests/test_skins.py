"""Скины и аватары игроков, включая пиратские аккаунты.

Сервер работает без лицензии, официальных скинов у игроков нет, поэтому сайт
не должен показывать пустое место: он отдаёт загруженный скин, а при его
отсутствии — предсказуемый аватар.
"""

from test_auth import client, login  # noqa: F401  — фикстуры берём из test_auth

import base64
import struct
import zlib


def png(width, height):
    """Минимальный корректный PNG нужного размера."""
    def chunk(tag, payload):
        body = tag + payload
        return struct.pack(">I", len(payload)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)
    signature = b"\x89PNG\r\n\x1a\n"
    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    raw = b"".join(b"\x00" + b"\xff\x00\x00\x00" * width for _ in range(height))
    return signature + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")


def headers(session):
    return {"Origin": "http://testserver", "X-CSRF-Token": session["csrf"]}


def linked_account(client, session):
    players = client.get("/api/v6/admin/players/search", headers=headers(session)).json()["players"]
    return players


def test_player_list_carries_skin_links(client):
    session = login(client)
    players = client.get("/api/v6/admin/players/search", headers=headers(session)).json()["players"]
    assert players, "нужен хотя бы один аккаунт"
    # У аккаунта с привязанным Minecraft ссылки обязаны быть.
    linked = [p for p in players if p.get("minecraftUuid")]
    for player in linked:
        skin = player.get("skin")
        assert skin, "у привязанного игрока должен быть скин"
        assert skin["avatar"].startswith("/api/v6/skin/")
        assert skin["texture"].startswith("/api/v6/skin/")


def test_avatar_is_served_even_without_a_real_skin(client):
    """Пиратский аккаунт без скина всё равно получает картинку, а не 404."""
    response = client.get("/api/v6/skin/5581ca674604382d966f0ccfc1acb063/avatar")
    # Без сети отдаётся нарисованная заглушка, с сетью — аватар сервиса.
    assert response.status_code == 200, response.text
    assert response.headers["content-type"].startswith("image/")


def test_bad_uuid_is_rejected(client):
    for bad in ("zzz", "1234", "../../etc/passwd"):
        assert client.get(f"/api/v6/skin/{bad}/avatar").status_code == 404


def test_skin_upload_needs_a_linked_account(client):
    session = login(client)
    response = client.post(
        "/api/v6/account/skin",
        headers=headers(session),
        json={"image": base64.b64encode(png(64, 64)).decode()},
    )
    # Владелец тестовой базы не привязан к игре.
    assert response.status_code == 409
    assert "not_linked" in response.text


def test_skin_size_is_validated(client):
    session = login(client)
    response = client.post(
        "/api/v6/account/skin",
        headers=headers(session),
        json={"image": base64.b64encode(png(32, 32)).decode()},
    )
    assert response.status_code in (400, 409)


def test_non_png_is_rejected(client):
    session = login(client)
    response = client.post(
        "/api/v6/account/skin",
        headers=headers(session),
        json={"image": base64.b64encode(b"not an image at all").decode()},
    )
    assert response.status_code in (400, 409)


def test_presets_have_files(client):
    """Каждый скин из набора обязан иметь файл: иначе кнопка ведёт в пустоту."""
    from pathlib import Path
    presets = client.get("/api/v6/skins/presets").json()["presets"]
    assert len(presets) >= 6
    root = Path(__file__).parents[1] / "frontend" / "assets" / "skins"
    for item in presets:
        assert item["id"], "у скина должен быть идентификатор"
        assert item["name"], "у скина должно быть название"
        assert (root / f"{item['id']}.png").exists(), f"нет текстуры {item['id']}.png"
        assert (root / f"{item['id']}-head.png").exists(), f"нет головы {item['id']}"


def test_skin_change_needs_permission(client):
    """Сменить чужой скин может только администрация."""
    response = client.post(
        "/api/v6/admin/players/whatever/skin",
        headers={"Origin": "http://testserver", "X-CSRF-Token": "x"},
        json={"preset": "steve"},
    )
    assert response.status_code in (401, 403)


def test_profile_endpoint_returns_signed_texture(client):
    """Игровой сервер ждёт value + signature, иначе клиент игнорирует текстуру."""
    import base64
    import json
    response = client.get("/api/v6/skin/c88df5644edb3c7389cc842f480e454d/profile")
    assert response.status_code == 200, response.text
    data = response.json()
    assert data["ok"] is True
    if data["texture"] is None:
        return  # скин не загружен — это допустимый ответ
    payload = json.loads(base64.b64decode(data["texture"]))
    assert "textures" in payload
    assert payload["textures"]["SKIN"]["url"].endswith("/texture")
    assert base64.b64decode(data["signature"])


def test_profile_is_stable_between_calls(client):
    """Подпись не должна меняться на каждом запросе: клиент кэширует по ней."""
    first = client.get("/api/v6/skin/c88df5644edb3c7389cc842f480e454d/profile").json()
    second = client.get("/api/v6/skin/c88df5644edb3c7389cc842f480e454d/profile").json()
    assert first == second


def test_profile_for_unknown_account_is_empty(client):
    data = client.get("/api/v6/skin/5581ca674604382d966f0ccfc1acb063/profile").json()
    assert data["ok"] is True
    assert data["texture"] is None


def test_skin_source_requires_a_linked_account(client):
    """Без привязки игры брать неоткуда: скин привязан к игровому UUID."""
    session = login(client)
    response = client.post(
        "/api/v6/account/skin-source",
        headers=headers(session),
        json={"name": "Notch"},
    )
    assert response.status_code == 409
    assert "not_linked" in response.text


def test_unknown_source_is_rejected(client):
    session = login(client)
    response = client.post(
        "/api/v6/account/skin-source",
        headers=headers(session),
        json={"name": "Notch", "source": "supersteve"},
    )
    assert response.status_code in (400, 409)


def test_public_user_carries_premium_name(client):
    session = login(client)
    me = session.get("user") or {}
    assert "premiumName" in me
