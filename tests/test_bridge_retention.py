"""Мост не должен создавать файл на каждый опрос состояния и не должен расти вечно."""
import json
import os
import time

from fastapi import HTTPException
from test_auth import client


def test_health_polls_do_not_write_bridge_requests(client):
    """`cmwebbridge health` отвечает без файла: иначе каждый заход на сайт добавляет запись."""
    from backend.game import GameAdapter
    game = client.app.state.game
    calls = []
    game.rcon = lambda command: calls.append(command) or (
        "CMWEB:" + __import__("base64").b64encode(
            json.dumps({"ok": True, "data": {"online": 0, "players": [], "ready": True, "plugins": []}}).encode()
        ).decode()
    )
    before = set(os.listdir(game.requests)) if game.requests.is_dir() else set()
    proof = game.status("health")
    assert proof["ok"] is True
    assert calls == ["cmwebbridge health"]
    after = set(os.listdir(game.requests)) if game.requests.is_dir() else set()
    assert after == before, "health не должен публиковать файловый запрос"
    assert isinstance(game, GameAdapter)


def test_bridge_rejects_unsuccessful_status(client):
    game = client.app.state.game
    game.rcon = lambda command: "CMWEB:" + __import__("base64").b64encode(
        json.dumps({"ok": False, "code": "bridged_off", "message": "выключено"}).encode()
    ).decode()
    try:
        game.status("health")
    except HTTPException as error:
        assert error.status_code == 503
        assert error.detail["code"] == "bridged_off"
    else:
        raise AssertionError("неуспешный ответ моста должен возвращать ошибку")


def test_bridge_retention_removes_only_old_files(client, tmp_path):
    from backend.worker import prune_bridge
    from dataclasses import replace
    root = tmp_path / "minecraft" / "server" / "plugins" / "CopiMineWebsiteBridge"
    (root / "requests").mkdir(parents=True)
    (root / "responses").mkdir()
    old = root / "requests" / ("a" * 32 + ".json")
    fresh = root / "requests" / ("b" * 32 + ".json")
    receipt = root / "responses" / ("c" * 32 + ".json")
    for path in (old, fresh, receipt):
        path.write_text("{}", encoding="utf-8")
    stale = time.time() - 3 * 86400
    os.utime(old, (stale, stale))
    os.utime(receipt, (stale, stale))
    settings = replace(client.app.state.settings, game_root=tmp_path)
    assert prune_bridge(client.app.state.database, settings) == 2
    assert fresh.exists() and not old.exists() and not receipt.exists()