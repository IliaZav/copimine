"""A pending real payment remains resumable after a normal authenticated state refresh."""
import unittest
from unittest.mock import patch
from server.tests import test_payments as fixture


class PaymentViewTests(unittest.TestCase):
    setUp = fixture.PaymentTests.setUp
    tearDown = fixture.PaymentTests.tearDown
    account = fixture.PaymentTests.account
    http = fixture.PaymentTests.http
    configured = fixture.PaymentTests.configured
    provider = fixture.PaymentTests.provider
    create = fixture.PaymentTests.create

    def test_state_and_owner_profile_keep_only_their_resumable_payment_link(self):
        self.configured()
        payment, provider = self.create()
        with patch.object(self.svc, "_provider_request", return_value=provider) as transport:
            refreshed = self.http("/api/v6/payments/" + payment["id"], bundle=self.player)
            self.assertEqual(refreshed["status"], 200)
            self.assertEqual(refreshed["body"]["id"], payment["id"])
            transport.assert_called_once_with("GET", provider["id"])
            denied = self.http("/api/v6/payments/" + payment["id"], bundle=self.other)
            self.assertEqual(denied["status"], 404)
            self.assertEqual(transport.call_count, 1)
        state = self.http("/api/v6/state", bundle=self.player)
        self.assertEqual(state["status"], 200)
        item = state["body"]["payments"][0]
        self.assertEqual(item["checkoutUrl"], payment["checkoutUrl"])
        self.assertEqual(item["method"], "sbp")
        self.assertEqual(item["rubles"], 100)
        self.assertEqual(item["amount"], 100)
        self.assertNotIn("provider_reference", item)
        self.assertNotIn("idempotency_key", item)
        self.assertNotIn("request_hash", item)
        profile = self.http("/api/v6/players/" + self.player["user"]["id"], bundle=self.player)
        self.assertEqual(profile["status"], 200)
        self.assertEqual(profile["body"]["payments"][0], item)
        other = self.http("/api/v6/state", bundle=self.other)
        self.assertEqual(other["body"]["payments"], [])
        public = self.http("/api/v6/state")
        self.assertNotIn(payment["id"].encode(), public["raw"])
        self.assertNotIn(payment["checkoutUrl"].encode(), public["raw"])


if __name__ == "__main__":
    unittest.main()
