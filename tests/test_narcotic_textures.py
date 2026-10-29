"""Текстуры наркотиков: показываем текстуру пака, а не иконку ванильного материала."""

import base64
import dataclasses
import io
import shutil
from pathlib import Path

from PIL import Image

from test_auth import client, login

SEED = Path(__file__).parents[1] / "private" / "narcotics-config.json"


def _private(client, tmp_path):
    """Копируем эталон плагина: без него раздел наркотиков отвечает 503."""
    root = tmp_path / "private"
    root.mkdir(parents=True, exist_ok=True)
    shutil.copy(SEED, root / "narcotics-config.json")
    client.app.state.settings = dataclasses.replace(client.app.state.settings, private=root)
    return root


def _headers(client):
    return {"Origin": "http://testserver", "X-CSRF-Token": client.get("/api/v6/session").json()["csrf"]}


def _png(size):
    out = io.BytesIO()
    Image.new("RGBA", size, (200, 30, 120, 255)).save(out, format="PNG")
    return base64.b64encode(out.getvalue()).decode("ascii")


def test_default_texture_is_the_pack_texture_not_a_vanilla_item(client):
    login(client)
    response = client.get("/api/v6/admin/narcotics/feta/texture?preview=0")
    assert response.status_code == 200
    assert response.headers["content-type"] == "image/png"
    # В паке текстура 32×32; иконка материала (white_dye) тоже png, но другого размера.
    with Image.open(io.BytesIO(response.content)) as image:
        assert image.size == (32, 32)


def test_admin_can_choose_an_existing_texture_without_uploading(client, tmp_path):
    login(client)
    headers = _headers(client)
    _private(client, tmp_path)
    available = client.get("/api/v6/admin/narcotics/textures").json()["items"]
    assert {row["id"] for row in available} >= {"feta", "kola"}
    picked = client.post("/api/v6/admin/narcotics/feta/texture", headers=headers,
                         json={"source": "kola", "reason": "Проверка выбора готовой текстуры"})
    assert picked.status_code == 200
    own = client.get("/api/v6/admin/narcotics/feta/texture?preview=0")
    assert own.status_code == 200
    with Image.open(io.BytesIO(own.content)) as image:
        assert image.size == (32, 32)
    assert client.get("/api/v6/admin/narcotics/textures").json()["items"]
    rows = {row["id"]: row for row in client.get("/api/v6/admin/narcotics/textures").json()["items"]}
    assert rows["feta"]["own"] is True


def test_unknown_source_texture_is_rejected(client):
    login(client)
    response = client.post("/api/v6/admin/narcotics/feta/texture", headers=_headers(client),
                           json={"source": "nope", "reason": "Такой текстуры нет"})
    assert response.status_code == 404


def test_uploaded_texture_keeps_32_pixel_size(client, tmp_path):
    login(client)
    headers = _headers(client)
    private = _private(client, tmp_path)
    response = client.post("/api/v6/admin/narcotics/sos/texture", headers=headers,
                           json={"image": _png((64, 64)), "reason": "Своя текстура для проверки"})
    assert response.status_code == 200
    stored = private / "narcotics-textures" / "sos.png"
    with Image.open(stored) as image:
        assert image.size == (64, 64)