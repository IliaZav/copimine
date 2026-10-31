import json
import pathlib
import re

import pytest

from test_auth import client, login  # noqa: F401

ROOT = pathlib.Path(__file__).resolve().parents[1]
BRIDGE = ROOT / "game-bridge" / "src" / "me" / "copimine" / "website" / "CopiMineWebsiteBridge.java"

# Что мост реально читает из params для каждой операции. Если запись каталога
# объявит другое, кнопка отправит запрос, который мост отвергнет.
REQUIRED_PARAMS = {
    "kill": set(),
    "unban": set(),
    "teleport": {"world", "x", "y", "z"},
    "whitelist.add": set(),
    "whitelist.remove": set(),
    "time.day": {"world"},
    "time.night": {"world"},
    "weather.clear": {"world"},
    "weather.rain": {"world"},
    "save": set(),
    "gamemode": {"mode"},
    "ban": set(),
    "kick": set(),
    "heal": set(),
    "feed": set(),
    "freeze": set(),
    "unfreeze": set(),
}

# Миры берутся из level-name на живом сервере; выдуманное «overworld» мост
# отвергает как world_unavailable.
WORLDS = {"CopiMine", "CopiMine_nether", "CopiMine_the_end"}


def catalogue():
    return json.loads((ROOT / "private" / "admin-actions.json").read_text(encoding="utf-8"))["actions"]


def supported():
    source = (ROOT / "backend" / "domain.py").read_text(encoding="utf-8")
    block = re.search(r"SUPPORTED_ACTIONS\s*=\s*\{(.*?)\}", source, re.S).group(1)
    return {item for item in re.findall(r'"([\w.]+)"', block)}


def test_every_supported_action_is_offered_by_the_admin_screen():
    """Экран рисует пересечение SUPPORTED_ACTIONS и каталога, поэтому действие
    без записи в каталоге недоступно навсегда, хотя мост его умеет."""
    defined = {row["id"] for row in catalogue()}
    missing = sorted(supported() - defined)
    assert missing == [], missing


@pytest.mark.parametrize("action", sorted(REQUIRED_PARAMS))
def test_catalogue_params_match_the_bridge_handler(action):
    row = next(item for item in catalogue() if item["id"] == action)
    declared = set(row["params"])
    # Срок бана и заморозки необязателен и обрабатывается сайтом, а не мостом,
    # поэтому он может быть объявлен сверх обязательного набора.
    required = {name for name, spec in row["params"].items() if spec.get("required")}
    assert required == REQUIRED_PARAMS[action], (action, required)


def test_world_parameters_offer_only_loaded_worlds():
    """Мост знает только загруженные миры, поэтому выпадающий список должен
    предлагать ровно их, иначе запрос упадёт с world_unavailable."""
    offered = supported()
    for row in catalogue():
        if row["id"] not in offered:
            continue
        for name, spec in row["params"].items():
            if name == "world":
                assert set(spec.get("enum", [])) == WORLDS, (row["id"], spec)


def test_the_frontend_offers_the_same_worlds():
    source = (ROOT / "frontend" / "assets" / "js" / "players-v6.js").read_text(encoding="utf-8")
    options = set(re.findall(r"\['(CopiMine[\w]*|overworld|nether|end)','", source))
    assert options and options <= WORLDS | {"overworld", "nether", "end"}, options
    assert not options & {"overworld", "nether", "end"}, "мост не знает таких миров"


def headers(session):
    return {"Origin": "http://testserver", "X-CSRF-Token": session["csrf"]}


def test_world_wide_actions_need_the_owner_permission(client):
    """Время, погода, сохранение и белый список меняют сервер для всех, поэтому
    они владельческие, а не модераторские."""
    owner = login(client)
    moderator = client.post("/api/v6/admin/accounts", headers=headers(owner), json={
        "name": "CatalogueMod", "password": "Moderator-only-password-2026",
        "role": "moderator", "reason": "Проверка прав каталога действий",
    })
    assert moderator.status_code == 200, moderator.text

    client.post("/api/v6/auth/logout", headers=headers(client.get("/api/v6/session").json()), json={})
    guest = client.get("/api/v6/session").json()
    signed = client.post("/api/v6/auth/login", headers=headers(guest), json={
        "name": "CatalogueMod", "password": "Moderator-only-password-2026",
    })
    assert signed.status_code == 200, signed.text
    session = client.get("/api/v6/session").json()

    for action in ("time.day", "weather.rain", "save", "whitelist.add"):
        response = client.post("/api/v6/admin/actions", headers=headers(session), json={
            "action": action, "params": {"world": "CopiMine", "player": "Steve"},
            "reason": "Проверка границы прав", "idempotencyKey": "catalogue-perm-" + action,
        })
        assert response.status_code == 403, (action, response.text)
        assert response.json()["error"]["code"] == "forbidden"


def test_whitelist_action_requires_a_target(client):
    """Белый список меняется по нику игрока, а не по всему серверу: пустой запрос
    не должен дойти до моста."""
    session = login(client)
    response = client.post("/api/v6/admin/actions", headers=headers(session), json={
        "action": "whitelist.add", "params": {}, "reason": "Проверка обязательной цели",
        "idempotencyKey": "catalogue-nobody",
    })
    assert response.status_code == 400, response.text
    assert response.json()["error"]["code"] == "invalid_player"