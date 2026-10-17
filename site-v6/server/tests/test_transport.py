"""Reverse proxy trust must separate clients without trusting caller-supplied IPs."""
import unittest
from unittest.mock import patch
from server.tests import test_backend as fixture


class TransportTests(unittest.TestCase):
    setUp = fixture.BackendTests.setUp
    tearDown = fixture.BackendTests.tearDown
    account = fixture.BackendTests.account
    http = fixture.BackendTests.http

    def test_only_explicit_loopback_proxy_can_supply_the_client_address(self):
        self.svc.config.trust_loopback_proxy = True
        body = {"consent": True, "events": [{"type": "page_view", "page": "home", "visitor": "fixture-visitor-0001"}]}
        with patch.object(self.svc, "analytics_events", return_value={"accepted": 1}) as event:
            for address in ("198.51.100.7", "2001:db8::8"):
                response = self.http("/api/v6/analytics/events", "POST", body, self.player,
                                     extra={"HTTP_X_CM_CLIENT_IP": address, "HTTP_X_FORWARDED_FOR": "203.0.113.99"})
                self.assertEqual(response["status"], 200)
                self.assertEqual(event.call_args.args[1], address)
            response = self.http("/api/v6/analytics/events", "POST", body, self.player,
                                 extra={"REMOTE_ADDR": "198.51.100.9", "HTTP_X_CM_CLIENT_IP": "203.0.113.99"})
            self.assertEqual(response["status"], 200)
            self.assertEqual(event.call_args.args[1], "198.51.100.9")
            self.svc.config.trust_loopback_proxy = False
            response = self.http("/api/v6/analytics/events", "POST", body, self.player,
                                 extra={"HTTP_X_CM_CLIENT_IP": "203.0.113.99"})
            self.assertEqual(response["status"], 200)
            self.assertEqual(event.call_args.args[1], "127.0.0.1")

    def test_malformed_trusted_proxy_header_is_rejected_without_using_forwarded_for(self):
        self.svc.config.trust_loopback_proxy = True
        for address in ("198.51.100.1, 198.51.100.2", "not-an-ip", "::1%eth0", "198.51.100.1:443"):
            with self.subTest(address=address):
                response = self.http("/api/v6/session", extra={"HTTP_X_CM_CLIENT_IP": address,
                                                              "HTTP_X_FORWARDED_FOR": "203.0.113.99"})
                self.assertEqual(response["status"], 400)
                self.assertEqual(response["body"]["error"]["code"], "invalid_proxy_address")


if __name__ == "__main__":
    unittest.main()
