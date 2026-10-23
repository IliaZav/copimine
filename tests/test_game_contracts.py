from test_auth import client, login


def test_inventory_mutation_requires_permission_and_fresh_snapshot(client):
    session = login(client)
    response = client.post("/api/v6/admin/players/SomePlayer/inventory/remove", headers={"Origin": "http://testserver", "X-CSRF-Token": session["csrf"]}, json={"kind": "inventory", "slot": 0, "quantity": 1, "reason": "Test inventory precondition", "key": "inventory-test-key"})
    assert response.status_code == 400
    assert response.json()["error"]["code"] == "snapshot_required"


def test_game_action_cannot_execute_arbitrary_command(client):
    session = login(client)
    response = client.post("/api/v6/admin/actions", headers={"Origin": "http://testserver", "X-CSRF-Token": session["csrf"]}, json={"action": "console", "params": {"command": "op arbitrary"}, "reason": "Test arbitrary command rejection"})
    assert response.status_code == 400
    assert response.json()["error"]["code"] == "unsupported_action"


def test_game_unavailable_is_not_successful_action(client):
    session = login(client)
    response = client.post("/api/v6/admin/actions", headers={"Origin": "http://testserver", "X-CSRF-Token": session["csrf"]}, json={"action": "time.day", "params": {}, "reason": "Check unavailable game", "idempotencyKey": "game-action-no-server"})
    assert response.status_code == 503
    assert response.json()["error"]["code"] == "game_unavailable"
