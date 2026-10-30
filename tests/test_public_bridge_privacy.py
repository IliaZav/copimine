"""Public state must not reuse the privileged, cached game-health document."""
from copy import deepcopy

import pytest
from test_auth import client, login  # noqa: F401


BRIDGE = {
    "online": True, "ready": True, "adapter": True, "players": 2,
    "maxPlayers": 50, "version": "26.3",
    "onlinePlayers": [{"uuid": "private-player-uuid", "name": "PrivatePlayer"}],
    "plugins": ["CopiMineNarcotics", "InternalPlugin"],
    "worlds": ["private_world"], "storagePath": "/private/server/root",
    "debug": {"internal": "not-for-public"},
}


@pytest.mark.parametrize("endpoint", ["/api/v6/state", "/api/v7/bootstrap"])
def test_guest_never_receives_privileged_bridge_fields(client, monkeypatch, endpoint):
    cached = deepcopy(BRIDGE)
    monkeypatch.setattr(client.app.state.game, "health", lambda **kwargs: cached)
    result = client.get(endpoint)
    assert result.status_code == 200, result.text
    state = result.json()
    if endpoint.endswith("bootstrap"):
        state = state["workspace"]
    assert state["game"] == {key: BRIDGE[key] for key in ("online", "ready", "players", "maxPlayers", "version")}
    assert cached == BRIDGE, "Sanitizing a response must not mutate the privileged cache"
    assert "private-player-uuid" not in result.text
    assert "CopiMineNarcotics" not in result.text
    assert "/private/server/root" not in result.text


def test_authorized_health_endpoint_keeps_diagnostics_without_public_leak(client, monkeypatch):
    monkeypatch.setattr(client.app.state.game, "health", lambda **kwargs: deepcopy(BRIDGE))
    login(client)
    result = client.get("/api/v6/admin/health")
    assert result.status_code == 200
    assert result.json()["game"]["plugins"] == BRIDGE["plugins"]
    # Administrative diagnostic data has its own permission-gated endpoint.
    state = client.get("/api/v6/state").json()
    assert "storagePath" not in state["game"]
    assert any(row["minecraftUuid"] == "private-player-uuid" for row in state["players"])
