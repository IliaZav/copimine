"""The signed request file must be published atomically.

Two website processes can try to publish the same id at once. The plugin refuses
a second payload for an id, so the site must never leave two different bodies
under one file: the loser has to see the winner's payload and stop.
"""
import json
import os
import threading

import pytest
from fastapi import HTTPException


class FakeGame:
    """Only the file protocol of GameAdapter; the RCON reply is stubbed."""

    def __init__(self, requests):
        self.requests = requests
        self.replies = []

    def rcon(self, command):
        self.replies.append(command)
        body = json.dumps({"ok": True, "data": {"done": True}}).encode()
        import base64
        return "CMWEB:" + base64.b64encode(body).decode()


@pytest.fixture
def adapter(tmp_path, monkeypatch):
    from backend.game import GameAdapter

    requests = tmp_path / "requests"
    requests.mkdir()
    monkeypatch.setenv("PLUGIN_API_KEY", "k" * 40)
    game = GameAdapter.__new__(GameAdapter)
    game.requests = requests
    fake = FakeGame(requests)
    monkeypatch.setattr(GameAdapter, "rcon", lambda self, command: fake.rcon(command))
    return game, fake, requests


def test_same_id_and_payload_is_replayed(adapter):
    game, fake, requests = adapter
    actor = {"id": "a1", "name": "Owner"}
    assert game.rpc("kick", actor, {"player": "Steve"}, key="same")["ok"] is True
    first = len(fake.replies)
    assert game.rpc("kick", actor, {"player": "Steve"}, key="same")["ok"] is True
    assert len(fake.replies) == first + 1
    assert len(list(requests.glob("*.json"))) == 1


def test_same_id_with_another_payload_is_rejected(adapter):
    game, fake, requests = adapter
    actor = {"id": "a1", "name": "Owner"}
    game.rpc("kick", actor, {"player": "Steve"}, key="same")
    with pytest.raises(HTTPException) as error:
        game.rpc("kick", actor, {"player": "Alex"}, key="same")
    assert error.value.status_code == 409
    payload = json.loads(json.loads(next(requests.glob("*.json")).read_text(encoding="utf-8"))["payload"])
    assert payload["params"]["player"] == "Steve"


def test_parallel_publishers_leave_exactly_one_payload(adapter):
    game, fake, requests = adapter
    actor = {"id": "a1", "name": "Owner"}
    outcomes, barrier = [], threading.Barrier(8)

    def publish(name):
        barrier.wait()
        try:
            game.rpc("kick", actor, {"player": name}, key="race")
            outcomes.append("ok")
        except HTTPException as error:
            outcomes.append(error.status_code)

    threads = [threading.Thread(target=publish, args=(f"P{i}",)) for i in range(8)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()

    files = list(requests.glob("*.json"))
    assert len(files) == 1
    assert not [name for name in os.listdir(requests) if name.startswith(".")]
    assert "ok" in outcomes