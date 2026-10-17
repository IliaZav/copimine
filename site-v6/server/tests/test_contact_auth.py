"""Real OTP state machines; only outbound SMTP/SMS transport is replaced."""
from concurrent.futures import ThreadPoolExecutor
import importlib
import json
import re
import time
import unittest
from unittest.mock import patch

from server.common import APIError
from server.core import Service
from server.security import check_password, totp_code
from server.tests import test_backend as fixture
from services.notifications import DeliveryError


class ContactAuthTests(unittest.TestCase):
    account = fixture.BackendTests.account
    http = fixture.BackendTests.http

    def setUp(self):
        self.clock = time.time()
        self.clock_patch = patch("server.storage.time.time", side_effect=lambda: self.clock)
        self.clock_patch.start()
        self.addCleanup(self.clock_patch.stop)
        fixture.BackendTests.setUp(self)
        self.addCleanup(self.temporary.cleanup)
        self.assertTrue(callable(getattr(self.svc, "init_notifications", None)),
                        "The server must implement verified-contact OTP authentication.")
        self.svc.config.notification_env = {
            "CM_NOTIFY_MODE": "live", "CM_SMTP_HOST": "smtp.example.test", "CM_SMTP_PORT": "465",
            "CM_SMTP_TLS": "tls", "CM_SMTP_FROM": "copimine@example.test", "CM_SMTP_USER": "fixture",
            "CM_SMTP_PASSWORD": "not-a-real-provider-password", "CM_SMSRU_API_ID": "fixture-api-id",
        }
        self.svc.init_notifications()
        self.jobs = importlib.import_module("server.notifications").process_notification_jobs
        self.sent = []
        self.fail_delivery = False

        def smtp(gateway, message, reference):
            self.sent.append({"channel": "email", "target": message["To"],
                              "text": message.get_content(), "subject": message["Subject"]})
            if self.fail_delivery:
                raise DeliveryError("Mocked ambiguous network outcome")
            return {"status": "accepted", "reference": reference, "channel": "email"}

        def sms(gateway, target, text):
            self.sent.append({"channel": "sms", "target": target, "text": text})
            if self.fail_delivery:
                raise DeliveryError("Mocked ambiguous network outcome")
            return {"status": "accepted", "provider_id": "fixture-reference", "channel": "sms"}

        self.smtp_patch = patch("services.notifications.NotificationGateway._smtp", smtp)
        self.sms_patch = patch("services.notifications.NotificationGateway._sms", sms)
        self.smtp_patch.start(); self.sms_patch.start()
        self.addCleanup(self.smtp_patch.stop); self.addCleanup(self.sms_patch.stop)

    def code(self):
        self.jobs(self.svc)
        self.assertTrue(self.sent, "A configured challenge must reach the outbound transport.")
        return re.search(r"(?<!\d)(\d{6})(?!\d)", self.sent[-1]["text"])[1]

    def verify(self, bundle=None, channel="email", value=None):
        bundle = bundle or self.player
        value = value or bundle["user"]["email"]
        start = self.svc.contact_request({"channel": channel, "value": value},
                                         bundle["user"], bundle["session"], "192.0.2.10")
        code = self.code()
        return self.svc.contact_verify({"challengeId": start["challengeId"], "code": code},
                                       bundle["user"], bundle["session"], "192.0.2.10")

    def request_login(self, guest, contact=None, purpose="login", channel="email"):
        return self.svc.auth_code_request({"channel": channel, "contact": contact or self.player["user"]["email"]},
                                          guest["session"], "192.0.2.40", purpose=purpose)

    def assert_api(self, code, callback):
        with self.assertRaises(APIError) as error:
            callback()
        self.assertEqual(error.exception.code, code)

    def test_unconfigured_is_explicit_and_existing_email_is_not_implicitly_verified(self):
        contacts = self.svc.contacts(self.player["user"], self.player["session"])["contacts"]
        self.assertEqual(contacts[0]["value"], "player@example.test")
        self.assertFalse(contacts[0]["verified"])
        guest = self.svc.guest_session()
        start = self.request_login(guest)
        self.assertEqual(start["expiresInMs"], 600000)
        self.jobs(self.svc)
        self.assertEqual(self.sent, [])
        self.assert_api("invalid_code", lambda: self.svc.auth_code_login(
            {"challengeId": start["challengeId"], "code": "123456"}, guest["session"], "192.0.2.40"))
        self.svc.config.notification_env = {}
        self.svc.init_notifications()
        self.assertEqual(self.svc.notification_providers(), {"email": {"enabled": False}, "sms": {"enabled": False}})
        self.assert_api("provider_unconfigured", lambda: self.svc.contact_request(
            {"channel": "email", "value": "new@example.test"}, self.player["user"], self.player["session"], "192.0.2.10"))

    def test_verification_uses_encrypted_outbox_and_same_browser_before_contact_ownership(self):
        start = self.svc.contact_request({"channel": "email", "value": "new-address@example.test"},
                                         self.player["user"], self.player["session"], "192.0.2.10")
        self.assertEqual(self.sent, [])  # HTTP did not send synchronously.
        with self.svc.store.connect() as conn:
            dump_before = "\n".join(conn.iterdump())
        self.assertNotIn("new-address@example.test", dump_before)
        code = self.code()
        self.assertNotIn('"code":"' + code + '"', dump_before)
        self.assertNotIn("'" + code + "'", dump_before)
        self.assertEqual(self.svc.contacts(self.player["user"], self.player["session"])["contacts"][0]["value"],
                         "player@example.test")
        self.assert_api("invalid_code", lambda: self.svc.contact_verify(
            {"challengeId": start["challengeId"], "code": code}, self.other["user"], self.other["session"], "192.0.2.11"))
        result = self.svc.contact_verify({"challengeId": start["challengeId"], "code": code},
                                        self.player["user"], self.player["session"], "192.0.2.10")
        contact = result["contacts"][0]
        self.assertTrue(contact["verified"])
        self.assertEqual(contact["value"], "new-address@example.test")
        self.assertNotEqual(contact["masked"], contact["value"])
        restarted = Service(self.svc.config)
        self.assertTrue(restarted.contacts(self.player["user"], self.player["session"])["contacts"][0]["verified"])
        self.assert_api("invalid_code", lambda: restarted.contact_verify(
            {"challengeId": start["challengeId"], "code": code}, self.player["user"], self.player["session"], "192.0.2.10"))
        with self.svc.store.connect() as conn:
            audit = "\n".join(row[0] for row in conn.execute("SELECT payload FROM audit"))
        self.assertNotIn(code, audit)
        self.assertNotIn("new-address@example.test", audit)

    def test_verified_login_is_single_use_under_concurrency_and_requires_same_guest_session(self):
        self.verify()
        self.clock += 61
        guest = self.svc.guest_session()
        wrong_guest = self.svc.guest_session()
        request = self.request_login(guest)
        code = self.code()
        body = {"challengeId": request["challengeId"], "code": code, "remember": True}
        self.assert_api("invalid_code", lambda: self.svc.auth_code_login(body, wrong_guest["session"], "192.0.2.41"))
        def login(_):
            try:
                return self.svc.auth_code_login(body, guest["session"], "192.0.2.40")
            except APIError as error:
                return error
        with ThreadPoolExecutor(max_workers=2) as pool:
            results = list(pool.map(login, range(2)))
        bundles = [result for result in results if isinstance(result, dict)]
        self.assertEqual(len(bundles), 1)
        session, user = self.svc.authenticate(bundles[0]["token"])
        self.assertEqual(user["id"], self.player["user"]["id"])
        self.assertEqual(user["role"], "player")
        self.assertGreater(session["expires_at"] - session["created_at"], 24 * 3600000)
        self.assertIsNone(self.svc.authenticate(guest["token"])[0])

    def test_wrong_attempts_are_durable_and_expired_codes_cannot_authenticate(self):
        self.verify()
        self.clock += 61
        guest = self.svc.guest_session()
        request = self.request_login(guest)
        actual = self.code()
        wrong = "000000" if actual != "000000" else "111111"
        for _ in range(5):
            self.assert_api("invalid_code", lambda: self.svc.auth_code_login(
                {"challengeId": request["challengeId"], "code": wrong}, guest["session"], "192.0.2.40"))
        restarted = Service(self.svc.config)
        self.assert_api("invalid_code", lambda: restarted.auth_code_login(
            {"challengeId": request["challengeId"], "code": actual}, guest["session"], "192.0.2.40"))
        self.clock += 61
        request = self.request_login(guest)
        actual = self.code()
        self.clock += 601
        self.assert_api("challenge_expired", lambda: self.svc.auth_code_login(
            {"challengeId": request["challengeId"], "code": actual}, guest["session"], "192.0.2.40"))

    def test_staff_recovery_keeps_totp_and_revokes_every_old_session_atomically(self):
        self.verify(self.owner)
        self.clock += 61
        second_owner = self.svc.login({"name": "Owner", "password": fixture.PASSWORD,
                                      "totp": totp_code(self.owner["secret"])}, self.svc.guest_session()["session"], "192.0.2.12")
        self.clock += 31
        guest = self.svc.guest_session()
        request = self.request_login(guest, "owner@example.test", "recovery")
        code = self.code()
        body = {"challengeId": request["challengeId"], "code": code, "password": "A changed password! 928743"}
        self.assert_api("invalid_credentials", lambda: self.svc.recovery_confirm(body, guest["session"], "192.0.2.40"))
        with self.svc.store.connect() as conn:
            before = conn.execute("SELECT password_hash FROM users WHERE id=?", (self.owner["user"]["id"],)).fetchone()[0]
        self.assertTrue(check_password(fixture.PASSWORD, before))
        result = self.svc.recovery_confirm(dict(body, totp=totp_code(self.owner["secret"])), guest["session"], "192.0.2.40")
        session, user = self.svc.authenticate(result["token"])
        self.assertEqual(user["role"], "owner")
        self.assertTrue(session["mfa_at"])
        self.assertTrue(check_password(body["password"], user["password_hash"]))
        self.assertEqual(user["totp_secret"], self.owner["secret"])
        for old in (self.owner, second_owner, guest):
            self.assertIsNone(self.svc.authenticate(old["token"])[0])

    def test_contact_change_requires_fresh_proof_and_does_not_transfer_verified_ownership(self):
        self.verify()
        self.clock += 301
        body = {"channel": "sms", "value": "+79990000001"}
        self.assert_api("reauth_required", lambda: self.svc.contact_request(body, self.player["user"], self.player["session"], "192.0.2.10"))
        request = self.svc.contact_request(dict(body, password=fixture.PASSWORD), self.player["user"], self.player["session"], "192.0.2.10")
        code = self.code()
        confirmed = self.svc.contact_verify({"challengeId": request["challengeId"], "code": code},
                                           self.player["user"], self.player["session"], "192.0.2.10")
        self.assertTrue(next(row for row in confirmed["contacts"] if row["channel"] == "sms")["verified"])
        self.clock += 61
        conflict = self.svc.contact_request(dict(body, password=fixture.PASSWORD), self.other["user"], self.other["session"], "192.0.2.11")
        conflict_code = self.code()
        self.assert_api("contact_unavailable", lambda: self.svc.contact_verify(
            {"challengeId": conflict["challengeId"], "code": conflict_code}, self.other["user"], self.other["session"], "192.0.2.11"))
        self.clock += 301
        self.assert_api("reauth_required", lambda: self.svc.contact_remove({"channel": "sms"}, self.player["user"], self.player["session"], "192.0.2.10"))
        removed = self.svc.contact_remove({"channel": "sms", "password": fixture.PASSWORD}, self.player["user"], self.player["session"], "192.0.2.10")
        self.assertFalse(any(row["channel"] == "sms" for row in removed["contacts"]))

    def test_provider_uncertainty_is_not_automatically_resent_and_queue_payload_is_erased(self):
        request = self.svc.contact_request({"channel": "sms", "value": "+79990000002"},
                                          self.player["user"], self.player["session"], "192.0.2.10")
        self.fail_delivery = True
        report = self.jobs(self.svc)
        self.assertEqual(report["uncertain"], 1)
        self.assertEqual(len(self.sent), 1)
        self.clock += 600
        self.jobs(Service(self.svc.config))
        self.assertEqual(len(self.sent), 1)
        with self.svc.store.connect() as conn:
            row = conn.execute("SELECT status,payload_cipher FROM notification_outbox WHERE challenge_id=?",
                               (request["challengeId"],)).fetchone()
        self.assertEqual(row["status"], "uncertain")
        self.assertIsNone(row["payload_cipher"])

    def test_expired_session_epoch_and_profile_email_change_invalidate_pending_login(self):
        self.verify()
        self.clock += 61
        guest = self.svc.guest_session()
        request = self.request_login(guest)
        code = self.code()
        with self.svc.store.transaction() as conn:
            conn.execute("UPDATE users SET email='replacement@example.test' WHERE id=?", (self.player["user"]["id"],))
        self.assert_api("invalid_code", lambda: self.svc.auth_code_login(
            {"challengeId": request["challengeId"], "code": code}, guest["session"], "192.0.2.40"))
        contacts = self.svc.contacts(self.player["user"], self.player["session"])["contacts"]
        self.assertFalse(contacts[0]["verified"])
        self.clock += 61
        request = self.svc.contact_request({"channel": "sms", "value": "+79990000003"},
                                          self.player["user"], self.player["session"], "192.0.2.10")
        with self.svc.store.transaction() as conn:
            conn.execute("UPDATE users SET auth_epoch=auth_epoch+1 WHERE id=?", (self.player["user"]["id"],))
        before = len(self.sent)
        self.jobs(self.svc)
        self.assertEqual(len(self.sent), before)

    def test_resend_throttles_unknown_contacts_too_without_account_enumeration(self):
        guest = self.svc.guest_session()
        first = self.request_login(guest, "nobody@example.test")
        self.assert_api("rate_limited", lambda: self.request_login(guest, "nobody@example.test"))
        self.clock += 61
        second = self.request_login(guest, "player@example.test")
        self.assertEqual(first["message"], second["message"])
        self.assertEqual(first["expiresInMs"], second["expiresInMs"])
        self.jobs(self.svc)
        self.assertEqual(self.sent, [])

    def test_contact_request_does_not_extend_a_cached_staff_mfa_proof(self):
        self.clock += 240
        self.svc.contact_request({"channel": "email", "value": "owner@example.test"},
                                 self.owner["user"], self.owner["session"], "192.0.2.10")
        self.clock += 61
        self.assert_api("reauth_required", lambda: self.svc.contact_request(
            {"channel": "sms", "value": "+79990000009"}, self.owner["user"], self.owner["session"], "192.0.2.10"))

    def test_disabled_email_channel_does_not_hold_back_a_configured_sms_job(self):
        self.svc.contact_request({"channel": "email", "value": "player@example.test"},
                                 self.player["user"], self.player["session"], "192.0.2.10")
        self.clock += 1
        self.svc.contact_request({"channel": "sms", "value": "+79990000004"},
                                 self.other["user"], self.other["session"], "192.0.2.11")
        self.svc.config.notification_env = {"CM_NOTIFY_MODE": "live", "CM_SMSRU_API_ID": "fixture-api-id"}
        self.svc.init_notifications()
        result = self.jobs(self.svc)
        self.assertEqual(result["accepted"], 1)
        self.assertEqual([message["channel"] for message in self.sent], ["sms"])

    def test_http_contact_boundary_rejects_typed_input_csrf_and_contact_enumeration(self):
        for channel in ({}, [], True, 7, None):
            with self.subTest(channel=channel):
                response = self.http("/api/v6/account/contacts/request", "POST",
                                     {"channel": channel, "value": "player@example.test"}, self.player)
                self.assertEqual(response["status"], 400)
        self.assertEqual(self.http("/api/v6/account/contacts")["status"], 401)
        body = {"channel": "email", "value": "player@example.test"}
        self.assertEqual(self.http("/api/v6/account/contacts/request", "POST", body, self.player, csrf=False)["status"], 403)
        self.assertEqual(self.http("/api/v6/account/contacts/request", "POST", body, self.player, origin="https://attacker.test")["status"], 403)
        own = self.http("/api/v6/account/contacts", bundle=self.other)
        self.assertEqual(own["status"], 200)
        self.assertNotIn(b"player@example.test", own["raw"])
        providers = self.http("/api/v6/auth/providers")
        self.assertTrue(providers["body"]["email"]["enabled"])
        self.assertTrue(providers["body"]["sms"]["enabled"])
        self.assertNotIn(b"fixture-api-id", providers["raw"])

    def test_sms_login_and_recovery_use_cookie_sessions_and_key_loss_fails_closed(self):
        self.verify(channel="sms", value="+79990000005")
        self.clock += 61
        guest = self.svc.guest_session()
        request = self.request_login(guest, "+79990000005", channel="sms")
        response = self.http("/api/v6/auth/code/login", "POST", {"challengeId": request["challengeId"], "code": self.code()}, guest)
        self.assertEqual(response["status"], 200)
        self.assertEqual(response["body"]["user"]["id"], self.player["user"]["id"])
        self.assertNotIn(b"password_hash", response["raw"])
        self.assertNotIn(b"token_hash", response["raw"])
        self.assertIn("HttpOnly", response["headers"]["Set-Cookie"])
        self.clock += 61
        guest = self.svc.guest_session()
        request = self.request_login(guest, "+79990000005", "recovery", "sms")
        password = "Recovered by phone! 837249"
        response = self.http("/api/v6/auth/recovery/confirm", "POST", {"challengeId": request["challengeId"], "code": self.code(), "password": password}, guest)
        self.assertEqual(response["status"], 200)
        self.assertIsNone(self.svc.authenticate(self.player["token"])[0])
        key_file = self.svc.store.directory / "notification.key"
        key_file.unlink()
        with self.assertRaises(ValueError):
            Service(self.svc.config)

    def test_profile_email_roundtrip_does_not_restore_old_verification(self):
        self.verify()
        self.svc.reauth({"password": fixture.PASSWORD}, self.player["user"], self.player["session"], "192.0.2.10")
        self.svc.update_profile({"email": "temporary@example.test"}, self.player["user"], self.player["session"])
        self.svc.update_profile({"email": "player@example.test"}, self.player["user"], self.player["session"])
        contact = self.svc.contacts(self.player["user"], self.player["session"])["contacts"][0]
        self.assertEqual(contact["value"], "player@example.test")
        self.assertFalse(contact["verified"])


if __name__ == "__main__":
    unittest.main()
