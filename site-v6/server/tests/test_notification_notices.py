"""Transactional user notices must follow committed state and verified ownership."""
from concurrent.futures import ThreadPoolExecutor
import unittest

from server.core import Service
from server.storage import identifier
from server.tests import test_contact_auth as fixture
from server.tests.test_backend import PASSWORD


class NoticeTests(unittest.TestCase):
    setUp = fixture.ContactAuthTests.setUp
    account = fixture.ContactAuthTests.account
    http = fixture.ContactAuthTests.http
    verify = fixture.ContactAuthTests.verify
    code = fixture.ContactAuthTests.code

    def ready(self):
        self.assertTrue(callable(getattr(self.svc, "queue_notice", None)),
                        "Committed account events must use the verified-email outbox.")

    def test_notice_is_transactional_deduplicated_and_sent_once_only_to_verified_email(self):
        self.ready()
        reference = identifier()
        with self.svc.store.transaction() as conn:
            self.assertIsNone(self.svc.queue_notice(conn, self.player["user"]["id"], "order", reference,
                                                   {"total": 100, "currency": "AR"}))
        self.verify()
        self.sent.clear()
        with self.assertRaises(RuntimeError):
            with self.svc.store.transaction() as conn:
                self.svc.queue_notice(conn, self.player["user"]["id"], "order", "rolled-back-order",
                                      {"total": 200, "currency": "AR"})
                raise RuntimeError("Roll back the business transaction")
        self.jobs(self.svc)
        self.assertEqual(self.sent, [])
        with self.svc.store.transaction() as conn:
            first = self.svc.queue_notice(conn, self.player["user"]["id"], "order", reference,
                                         {"total": 100, "currency": "AR"})
            repeated = self.svc.queue_notice(conn, self.player["user"]["id"], "order", reference,
                                            {"total": 100, "currency": "AR"})
            self.assertEqual(first, repeated)
        with ThreadPoolExecutor(max_workers=2) as pool:
            list(pool.map(lambda _: self.jobs(self.svc, limit=1), range(2)))
        self.assertEqual(len(self.sent), 1)
        self.assertEqual(self.sent[0]["channel"], "email")
        self.assertEqual(self.sent[0]["target"], "player@example.test")
        self.assertIn("100 AR", self.sent[0]["text"])
        self.assertIn(reference, self.sent[0]["text"])
        self.assertNotIn("rolled-back-order", self.sent[0]["text"])
        self.jobs(Service(self.svc.config))
        self.assertEqual(len(self.sent), 1)

    def test_removed_contact_cancels_notice_and_sms_never_receives_background_messages(self):
        self.ready()
        self.verify(channel="sms", value="+79990000100")
        self.sent.clear()
        with self.svc.store.transaction() as conn:
            self.assertIsNone(self.svc.queue_notice(conn, self.player["user"]["id"], "password", identifier(), {}))
        self.jobs(self.svc)
        self.assertEqual(self.sent, [])
        self.verify()
        self.sent.clear()
        with self.svc.store.transaction() as conn:
            self.svc.queue_notice(conn, self.player["user"]["id"], "password", identifier(), {})
        self.svc.contact_remove({"channel": "email"}, self.player["user"], self.player["session"], "192.0.2.10")
        self.jobs(self.svc)
        self.assertEqual(self.sent, [])

    def test_notice_templates_reject_arbitrary_fields_and_prioritize_login_codes(self):
        self.ready()
        self.verify()
        self.sent.clear()
        for kind, value in (("arbitrary", {}), ("order", {"total": True, "currency": "AR"}),
                            ("order", {"total": 20, "currency": "RUB"}),
                            ("password", {"body": "Attacker supplied text"}),
                            ("ticket", {"status": "<script>"})):
            with self.subTest(kind=kind, value=value), self.assertRaises(ValueError):
                with self.svc.store.transaction() as conn:
                    self.svc.queue_notice(conn, self.player["user"]["id"], kind, identifier(), value)
        with self.svc.store.transaction() as conn:
            self.svc.queue_notice(conn, self.player["user"]["id"], "password", identifier(), {})
        self.clock += 61
        guest = self.svc.guest_session()
        self.svc.auth_code_request({"channel": "email", "contact": "player@example.test"}, guest["session"], "192.0.2.40")
        self.jobs(self.svc, limit=1)
        self.assertEqual(len(self.sent), 1)
        self.assertIn("sign-in code", self.sent[0]["subject"])

    def test_checkout_password_and_support_notice_hooks_do_not_send_in_requests(self):
        self.ready()
        self.verify()
        self.sent.clear()
        checkout = {"items": [{"id": "sword", "qty": 1}], "idempotencyKey": identifier()}
        first = self.svc.checkout(checkout, self.player["user"], self.player["session"])
        self.svc.checkout(checkout, self.player["user"], self.player["session"])
        ticket = self.svc.create_ticket({"title": "Example ticket", "body": "Help with my order", "type": "support"},
                                        self.player["user"], self.player["session"])["ticket"]
        self.svc.update_ticket(ticket["id"], {"reply": "A support response", "status": "awaiting_player",
                                             "reason": "Reply to support request"}, self.owner["user"], self.owner["session"])
        self.svc.change_password({"currentPassword": PASSWORD, "password": "My new secure password! 4382"},
                                  self.player["user"], self.player["session"], "192.0.2.10")
        self.assertEqual(self.sent, [])
        report = self.jobs(self.svc)
        self.assertEqual(report["accepted"], 3)
        self.assertEqual(len(self.sent), 3)
        text = "\n".join(message["text"] for message in self.sent)
        self.assertIn(first["order"]["id"], text)
        self.assertIn("100 AR", text)
        self.assertNotIn("A support response", text)  # Read the private reply after sign-in.
        self.assertNotIn("My new secure password! 4382", text)


if __name__ == "__main__":
    unittest.main()
