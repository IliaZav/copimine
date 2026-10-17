"""Queued work respects elapsed sanctions and refunds release only proven undelivered units."""
import json
import unittest
from unittest.mock import MagicMock, patch
from server.common import APIError
from server.storage import encoded, identifier, now_ms
from server.tests import test_backend as fixture
from server.workers import claim_actions, dispatch_actions


class DeliveryLifecycleTests(unittest.TestCase):
    setUp = fixture.BackendTests.setUp
    tearDown = fixture.BackendTests.tearDown
    account = fixture.BackendTests.account
    http = fixture.BackendTests.http
    action = fixture.BackendTests.action

    def test_expired_queued_sanction_is_not_sent_after_a_slow_previous_operation(self):
        first = self.action("give", {"itemId": "stone", "quantity": 1})["action"]["id"]
        short = self.action("mute", {"durationMs": 1000})["action"]["id"]
        with self.svc.store.transaction() as conn:
            conn.execute("UPDATE actions SET created_at=created_at-1000 WHERE id=?", (first,))
        clock = [now_ms()]
        sent = []
        def transport(request, timeout):
            sent.append(json.loads(request.data)["operationId"])
            clock[0] += 2000
            response = MagicMock()
            response.__enter__.return_value.status = 202
            return response
        opener = MagicMock()
        opener.open.side_effect = transport
        with patch("server.workers.urllib.request.build_opener", return_value=opener), \
             patch("server.workers.now_ms", side_effect=lambda: clock[0]), \
             patch("server.integration.now_ms", side_effect=lambda: clock[0]):
            dispatch_actions(self.svc)
        self.assertEqual(sent, [first])
        with self.svc.store.connect() as conn:
            self.assertEqual(conn.execute("SELECT status FROM actions WHERE id=?", (short,)).fetchone()[0], "cancelled")
            self.assertEqual(conn.execute("SELECT status FROM sanctions WHERE action_id=?", (short,)).fetchone()[0], "expired")

    def test_proven_partial_refund_releases_exact_line_caps_and_preserves_delivered_sales(self):
        other = dict(self.public, id="other", name="Other", price=10, limit=1)
        with self.svc.store.transaction() as conn:
            conn.execute("UPDATE catalog SET per_player_limit=1,supply_limit=1 WHERE id='sword'")
            conn.execute("INSERT INTO catalog(id,payload,price,currency,visibility,enabled,per_player_limit,supply_limit) VALUES(?,?,?,?,?,?,?,?)",
                         ("other", encoded(other), 10, "AR", "public", 1, 1, 1))
        self.svc.public_source_ids.add("other")
        self.action("purchase.limit", {"itemId": None, "maximum": 2, "period": "lifetime"})
        result = self.svc.checkout({"items": [{"id": "sword", "qty": 1}, {"id": "other", "qty": 1}],
                                    "idempotencyKey": identifier()}, self.player["user"], self.player["session"])
        operations = claim_actions(self.svc)
        for operation in operations:
            status = "failed" if operation["params"]["itemId"] == "sword" else "executed"
            ack = {"operationId": operation["operationId"], "ackId": identifier(), "status": status, "executedAt": now_ms()}
            self.svc.acknowledge(ack)
            self.svc.acknowledge(ack)
        state = self.svc.state(self.player["user"], self.player["session"])
        order = next(row for row in state["orders"] if row["id"] == result["order"]["id"])
        self.assertEqual(order["status"], "partially_failed")
        self.assertEqual(order["refundedTotal"], 100)
        failed = next(row for row in order["items"] if row["id"] == "sword")
        delivered = next(row for row in order["items"] if row["id"] == "other")
        self.assertEqual((failed["status"], failed["refundedQuantity"], failed["refundedAmount"]), ("refunded", 1, 100))
        self.assertEqual((delivered["status"], delivered["refundedQuantity"]), ("delivered", 0))
        summary = self.svc.analytics(self.owner["user"], self.owner["session"])
        self.assertEqual(summary["revenue"]["AR"], 10)
        self.assertEqual(summary["orders"], 1)
        self.assertEqual(summary["items"], 1)
        # Personal global, item, and supply caps all release the refunded line.
        self.svc.checkout({"items": [{"id": "sword", "qty": 1}], "idempotencyKey": identifier()},
                          self.player["user"], self.player["session"])
        with self.assertRaises(APIError):
            self.svc.checkout({"items": [{"id": "other", "qty": 1}], "idempotencyKey": identifier()},
                              self.player["user"], self.player["session"])
        with self.svc.store.connect() as conn:
            self.assertEqual(conn.execute("SELECT ar FROM users WHERE id=?", (self.player["user"]["id"],)).fetchone()[0], 890)
            self.assertEqual(conn.execute("SELECT count(*) FROM ledger WHERE kind='delivery.failed.refund'").fetchone()[0], 1)

    def test_unproven_failed_delivery_keeps_reservation_but_verified_full_failure_is_refunded(self):
        with self.svc.store.transaction() as conn:
            conn.execute("UPDATE catalog SET per_player_limit=1 WHERE id='sword'")
        self.svc.checkout({"items": [{"id": "sword", "qty": 1}], "idempotencyKey": identifier()},
                          self.player["user"], self.player["session"])
        operation = claim_actions(self.svc)[0]
        with self.svc.store.transaction() as conn:
            conn.execute("UPDATE actions SET status='failed' WHERE id=?", (operation["operationId"],))
        with self.assertRaises(APIError):
            self.svc.checkout({"items": [{"id": "sword", "qty": 1}], "idempotencyKey": identifier()},
                              self.player["user"], self.player["session"])
        with self.svc.store.transaction() as conn:
            conn.execute("UPDATE actions SET status='dispatched' WHERE id=?", (operation["operationId"],))
        self.svc.acknowledge({"operationId": operation["operationId"], "ackId": identifier(), "status": "failed", "executedAt": now_ms()})
        state = self.svc.state(self.player["user"], self.player["session"])
        self.assertEqual(state["orders"][0]["status"], "refunded")
        self.assertEqual(state["orders"][0]["items"][0]["refundedQuantity"], 1)
        self.svc.checkout({"items": [{"id": "sword", "qty": 1}], "idempotencyKey": identifier()},
                          self.player["user"], self.player["session"])


if __name__ == "__main__":
    unittest.main()
