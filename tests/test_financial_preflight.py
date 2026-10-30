"""Locally rejected requests must not strand or dispatch financial operations."""
import pytest
from backend.auth import create_user
from test_auth import client, login  # noqa: F401


def headers(session):
    return {"Origin": "http://testserver", "X-CSRF-Token": session["csrf"]}


def receipts(client):
    with client.app.state.database.transaction() as db:
        return [dict(row) for row in db.execute("SELECT * FROM cm_site_receipts").fetchall()]


@pytest.fixture
def linked(client, monkeypatch):
    session = login(client)
    db = client.app.state.database
    target = create_user(db, "Recipient", "recipient-test-password-2026")
    with db.transaction() as conn:
        for identifier, nickname in ((session["user"]["id"], "TestOwner"), (target["id"], "Recipient")):
            conn.execute("UPDATE cm_site_users SET minecraft_uuid=%s,minecraft_name=%s WHERE id=%s", ("uuid-" + identifier, nickname, identifier))
    calls = []
    def transfer(*args):
        calls.append(("transfer", args))
        return {"txId": "stable-transfer-id"}
    def checkout(*args):
        calls.append(("checkout", args))
        return {"items": [{"purchaseId": "stable-purchase-id", "itemId": "test_item", "status": "PENDING_DELIVERY", "priceAr": 50}]}
    monkeypatch.setattr(client.app.state.game, "transfer", transfer)
    monkeypatch.setattr(client.app.state.game, "checkout", checkout)
    monkeypatch.setattr(client.app.state.game, "catalog", lambda **kwargs: [{"id": "test_item", "enabled": True, "currency": "AR", "price": 50}])
    return session, target, calls


@pytest.mark.parametrize("invalid", [{"pin": "x"}, {"pin": None}, {"expectedFee": 3}])
def test_transfer_preflight_does_not_reserve_key(client, linked, invalid):
    session, target, calls = linked
    valid = {"to": target["id"], "amount": 50, "pin": "1234", "key": "preflight-transfer"}
    rejected = client.post("/api/v7/transfer", headers=headers(session), json={**valid, **invalid})
    assert rejected.status_code in {400, 409}, rejected.text
    assert calls == []
    assert receipts(client) == [], "Local validation must precede request-key reservation"
    accepted = client.post("/api/v7/transfer", headers=headers(session), json=valid)
    assert accepted.status_code == 200, accepted.text
    replay = client.post("/api/v7/transfer", headers=headers(session), json=valid)
    assert replay.json() == accepted.json()
    assert len(calls) == 1


@pytest.mark.parametrize("row", [None, "test_item", [], {"id": []}, {"id": "test_item", "qty": True}, {"id": "test_item", "qty": 1.0}])
def test_checkout_rejects_malformed_rows_before_dispatch(client, linked, row):
    session, target, calls = linked
    result = client.post("/api/v6/checkout", headers=headers(session), json={"items": [row], "pin": "1234", "key": "invalid-cart-row"})
    assert result.status_code == 400, result.text
    assert calls == []
    assert receipts(client) == []


@pytest.mark.parametrize("bad_pin", ["", None, "abc", True, "123456789"])
def test_checkout_pin_is_validated_before_reservation(client, linked, bad_pin):
    session, target, calls = linked
    data = {"items": [{"id": "test_item", "qty": 1}], "pin": bad_pin, "expectedTotal": 50, "key": "invalid-cart-pin"}
    response = client.post("/api/v6/checkout", headers=headers(session), json=data)
    assert response.status_code == 400, response.text
    assert calls == []
    assert receipts(client) == []


def test_unlinked_checkout_does_not_reserve_or_dispatch(client, linked):
    session, target, calls = linked
    with client.app.state.database.transaction() as db:
        db.execute("UPDATE cm_site_users SET minecraft_uuid='',minecraft_name='' WHERE id=%s", (session["user"]["id"],))
    response = client.post("/api/v6/checkout", headers=headers(session), json={"items": [{"id": "test_item"}], "pin": "1234", "key": "unlinked-cart"})
    assert response.status_code == 409, response.text
    assert response.json()["error"]["code"] == "game_link_required"
    assert calls == [] and receipts(client) == []


def test_checkout_replay_never_dispatches_twice(client, linked):
    session, target, calls = linked
    data = {"items": [{"id": "test_item"}], "pin": "1234", "expectedTotal": 50, "key": "valid-cart-replay"}
    first = client.post("/api/v6/checkout", headers=headers(session), json=data)
    second = client.post("/api/v6/checkout", headers=headers(session), json=data)
    assert first.status_code == second.status_code == 200
    assert first.json() == second.json()
    assert len(calls) == 1
