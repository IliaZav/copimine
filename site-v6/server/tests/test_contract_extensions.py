"""Native game receipts and catalog delivery constraints remain separate from site money."""
import json
import unittest
from server.common import APIError
from server.storage import encoded, identifier, now_ms
from server.tests import test_backend as fixture


class ContractTests(unittest.TestCase):
    setUp = fixture.BackendTests.setUp
    tearDown = fixture.BackendTests.tearDown
    account = fixture.BackendTests.account
    http = fixture.BackendTests.http
    action = fixture.BackendTests.action

    def native_money(self):
        key = identifier()
        with self.svc.store.transaction() as conn:
            operation = self.svc.queue(conn, self.owner["user"]["id"], self.player["user"]["id"],
                                       "game.money.adjust", {"mode": "credit", "amount": 50, "currency": "AR"},
                                       "Native bank fixture", key, key)
            conn.execute("UPDATE actions SET status='dispatched' WHERE id=?", (operation,))
        return {"operationId": operation, "ackId": identifier(), "status": "executed", "executedAt": now_ms(),
                "result": {"balanceAfter": 2050, "currency": "AR", "transactionId": "site-v6-money:" + operation,
                           "authority": "EconomyCore", "message": "Native bank confirmed", "privatePayload": "must be omitted"}}

    def test_native_bank_receipt_is_preserved_without_changing_site_ledger(self):
        ack = self.native_money()
        result = self.svc.acknowledge(ack)["action"]["ack"]["result"]
        self.assertEqual(result["balanceAfter"], 2050)
        self.assertEqual(result["authority"], "EconomyCore")
        self.assertEqual(result["currency"], "AR")
        self.assertEqual(result["transactionId"], ack["result"]["transactionId"])
        self.assertNotIn("privatePayload", result)
        with self.svc.store.connect() as conn:
            player = conn.execute("SELECT ar,cm FROM users WHERE id=?", (self.player["user"]["id"],)).fetchone()
            self.assertEqual(tuple(player), (1000, 1000))
            self.assertEqual(conn.execute("SELECT count(*) FROM ledger").fetchone()[0], 0)
        replay = self.svc.acknowledge(dict(ack, result=dict(ack["result"], balanceAfter=999)))
        self.assertTrue(replay["replayed"])
        self.assertEqual(replay["action"]["ack"]["result"]["balanceAfter"], 2050)

    def test_malformed_or_mismatched_native_bank_fields_do_not_finalize_action(self):
        ack = self.native_money()
        for changed in ({"balanceAfter": True}, {"balanceAfter": -1}, {"balanceAfter": 1.5},
                        {"balanceAfter": 10**12 + 1}, {"currency": "CM"}, {"currency": "RUB"},
                        {"authority": "site-v6-ledger"}, {"transactionId": "x" * 129}, {"transactionId": ""}):
            with self.subTest(changed=changed):
                with self.assertRaises(APIError) as error:
                    self.svc.acknowledge(dict(ack, result=dict(ack["result"], **changed)))
                self.assertEqual(error.exception.status, 400)
        with self.svc.store.connect() as conn:
            row = conn.execute("SELECT status,ack_id FROM actions WHERE id=?", (ack["operationId"],)).fetchone()
        self.assertEqual(tuple(row), ("dispatched", None))

    def test_ack_allows_bounded_game_clock_skew_and_keeps_identity_and_replay_guards(self):
        ack = self.native_money()
        with self.svc.store.connect() as conn:
            created = conn.execute("SELECT created_at FROM actions WHERE id=?", (ack["operationId"],)).fetchone()[0]
        for timestamp in (created - 60001, now_ms() + 120000, True, "1699999999999"):
            with self.subTest(timestamp=timestamp), self.assertRaises(APIError):
                self.svc.acknowledge(dict(ack, executedAt=timestamp))
        accepted = dict(ack, executedAt=created - 60000)
        try:
            result = self.svc.acknowledge(accepted)
        except APIError as error:
            self.fail("A signed receipt must tolerate up to 60 seconds of game-host clock skew: " + error.message)
        self.assertEqual(result["action"]["status"], "executed")
        self.assertEqual(result["action"]["ack"]["executedAt"], created - 60000)
        self.assertTrue(self.svc.acknowledge(accepted)["replayed"])
        with self.assertRaises(APIError) as error:
            self.svc.acknowledge(dict(accepted, ackId=identifier()))
        self.assertEqual(error.exception.status, 409)
        unknown = dict(accepted, operationId=identifier(), ackId=identifier())
        with self.assertRaises(APIError) as error:
            self.svc.acknowledge(unknown)
        self.assertEqual(error.exception.status, 404)

    def test_donation_entitlement_quantity_is_enforced_even_when_not_owner_bound(self):
        with self.svc.store.transaction() as conn:
            payload = json.loads(conn.execute("SELECT payload FROM catalog WHERE id='sword'").fetchone()[0])
            payload.update(sourceType="DONATION_SHOP", ownerBound=False)
            conn.execute("UPDATE catalog SET payload=? WHERE id='sword'", (encoded(payload),))
        public = self.svc.catalog()[0]
        self.assertEqual(public["maxQuantity"], 1)
        self.assertNotIn("sourceType", public)
        with self.assertRaises(APIError) as error:
            self.svc.checkout({"items": [{"id": "sword", "qty": 2}], "idempotencyKey": identifier()},
                              self.player["user"], self.player["session"])
        self.assertEqual(error.exception.code, "item_quantity_limit")
        with self.assertRaises(APIError):
            self.action("give", {"itemId": "sword", "quantity": 2})
        with self.svc.store.connect() as conn:
            self.assertEqual(conn.execute("SELECT count(*) FROM orders").fetchone()[0], 0)
            self.assertEqual(conn.execute("SELECT count(*) FROM actions").fetchone()[0], 0)

    def test_packaged_integration_sources_cannot_be_served_as_static_content(self):
        root = self.site / "integrations" / "game-bridge"
        root.mkdir(parents=True)
        for name in ("actions.json", "SiteBridge.java.inc", "fixture.png"):
            (root / name).write_text("private source fixture", encoding="utf-8")
            response = self.http("/integrations/game-bridge/" + name, bundle=self.owner)
            self.assertEqual(response["status"], 404)
            self.assertNotIn(b"private source fixture", response["raw"])

    def test_actual_overstack_snapshot_is_preserved_without_expanding_mutation_limits(self):
        body = {"playerId": self.player["user"]["id"], "gameUUID": self.player["user"]["game_uuid"],
                "snapshotId": identifier(), "capturedAt": now_ms(),
                "inventory": [{"slot": 0, "id": "minecraft:stone", "quantity": 2_147_483_647}], "ender": []}
        try:
            self.svc.ingest_snapshot(body)
        except APIError as error:
            self.fail("An authoritative overstack snapshot must preserve its actual count: " + error.message)
        with self.svc.store.connect() as conn:
            saved = conn.execute("SELECT inventory FROM inventory_snapshots WHERE id=?", (body["snapshotId"],)).fetchone()
        self.assertEqual(json.loads(saved[0])[0]["quantity"], 2_147_483_647)
        for quantity in (True, 1.5, 0, -1, 2_147_483_648):
            with self.subTest(quantity=quantity), self.assertRaises(APIError):
                self.svc.ingest_snapshot(dict(body, snapshotId=identifier(), inventory=[dict(body["inventory"][0], quantity=quantity)]))
        with self.assertRaises(APIError):
            self.action("give", {"itemId": "stone", "quantity": 65})
        with self.assertRaises(APIError):
            self.action("inventory.remove", {"slot": 0, "itemId": "minecraft:stone", "quantity": 65,
                                              "expectedSnapshotId": body["snapshotId"]})


if __name__ == "__main__":
    unittest.main()
