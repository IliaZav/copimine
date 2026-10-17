"""VK ID trust-boundary regressions; no requests reach the real provider."""
import base64
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from urllib.parse import parse_qs, urlencode, urlsplit
import unittest
from unittest.mock import patch

from server.common import APIError, Config
from server.http import COOKIE
from server.security import check_password, digest, totp_code
from server.storage import now_ms
from server.tests import test_backend as fixture
from server.vk_auth import FLOW_MS, VK_ORIGIN


class VKTests(unittest.TestCase):
    setUp = fixture.BackendTests.setUp
    tearDown = fixture.BackendTests.tearDown
    account = fixture.BackendTests.account
    http = fixture.BackendTests.http

    def begin(self, bundle=None, mode="login"):
        self.svc.config.vk_client_id = "12345678"
        bundle = bundle or self.svc.guest_session()
        result = self.svc.vk_start({"mode": mode}, bundle["user"], bundle["session"], "198.51.100.1")
        state = parse_qs(urlsplit(result["url"]).query)["state"][0]
        return bundle, result["cookie"], state, result["url"]

    def callback(self, flow, subject="123456", *, identity=None, token_state=None):
        bundle, cookie, state, _ = flow
        def provider(endpoint, query, form):
            if endpoint == "auth":
                self.assertEqual(query["state"], state)
                self.assertEqual(query["device_id"], "browser-device")
                self.assertEqual(form, {"code": "one-time-provider-code"})
                return {"user_id": subject, "access_token": "private-provider-access-token", "refresh_token": "private-refresh-token",
                        "state": state if token_state is None else token_state}
            self.assertEqual(endpoint, "user_info")
            self.assertEqual(form, {"access_token": "private-provider-access-token"})
            return {"user": {"user_id": identity or subject, "first_name": "Тест", "last_name": "VK",
                             "email": "owner@example.test", "phone": "+79990000000"}}
        with patch.object(self.svc, "_vk_request", side_effect=provider):
            return self.svc.vk_callback({"state": [state], "code": ["one-time-provider-code"], "device_id": ["browser-device"]},
                cookie, bundle["user"], bundle["session"], "198.51.100.2")

    def bind(self, subject, bundle):
        with self.svc.store.transaction() as conn:
            conn.execute("INSERT INTO external_identities VALUES('vk',?,?,?,?)", (subject, bundle["user"]["id"], "Fixture", now_ms()))

    def test_disabled_provider_is_explicit_and_cannot_start(self):
        guest = self.svc.guest_session()
        result = self.http("/api/v6/auth/providers", bundle=guest)
        self.assertEqual(result["body"]["vk"], {"enabled": False})
        self.assertEqual(self.http("/api/v6/auth/vk/start", "POST", {}, guest)["status"], 503)

    def test_start_uses_pkce_fixed_callback_secure_cookie_and_csrf(self):
        self.svc.config.vk_client_id = "12345678"
        guest = self.svc.guest_session()
        self.assertEqual(self.http("/api/v6/auth/vk/start", "POST", {}, guest, csrf=False)["status"], 403)
        self.assertEqual(self.http("/api/v6/auth/vk/start", "POST", {}, guest, origin="https://evil.example")["status"], 403)
        result = self.http("/api/v6/auth/vk/start", "POST", {"mode": "login"}, guest)
        self.assertEqual(result["status"], 200)
        self.assertEqual(set(result["body"]), {"url"})
        url = urlsplit(result["body"]["url"])
        self.assertEqual(url.scheme + "://" + url.netloc, VK_ORIGIN)
        params = parse_qs(url.query)
        self.assertEqual(params["redirect_uri"], ["https://copimine.test/api/v6/auth/vk/callback"])
        self.assertEqual(params["code_challenge_method"], ["S256"])
        with self.svc.store.connect() as conn:
            row = conn.execute("SELECT * FROM oauth_flows").fetchone()
            self.assertEqual(len(row["verifier"]), 43)
            expected = base64.urlsafe_b64encode(hashlib.sha256(row["verifier"].encode()).digest()).decode().rstrip("=")
            self.assertEqual(params["code_challenge"], [expected])
            self.assertEqual(row["state_hash"], digest(params["state"][0]))
        for flag in ("HttpOnly", "Secure", "SameSite=Lax", "Path=/api/v6/auth/vk", "Max-Age=600"):
            self.assertIn(flag, result["headers"]["Set-Cookie"])
        self.assertNotIn("private", json.dumps(result["body"]))

    def test_callback_rejects_wrong_state_duplicate_state_and_another_browser(self):
        flow = self.begin()
        bundle, cookie, state, _ = flow
        query = {"state": ["wrong"], "code": ["code"], "device_id": ["device"]}
        with patch.object(self.svc, "_vk_request") as network:
            with self.assertRaises(APIError) as failure:
                self.svc.vk_callback(query, cookie, None, bundle["session"], "ip")
            self.assertEqual(failure.exception.code, "vk_state_mismatch")
            query["state"] = [state, state]
            with self.assertRaises(APIError):
                self.svc.vk_callback(query, cookie, None, bundle["session"], "ip")
            query["state"] = [state]
            alien = self.svc.guest_session()
            with self.assertRaises(APIError):
                self.svc.vk_callback(query, cookie, None, alien["session"], "ip")
            network.assert_not_called()

    def test_identity_disagreement_and_provider_error_never_create_binding(self):
        flow = self.begin()
        self.assertEqual(self.callback(flow, identity="654321"), {"status": "error"})
        status = self.svc.vk_status(flow[1], flow[0]["session"])
        self.assertEqual(status["code"], "vk_invalid_identity")
        flow = self.begin()
        self.assertEqual(self.callback(flow, token_state="other-state"), {"status": "error"})
        with self.svc.store.connect() as conn:
            self.assertEqual(conn.execute("SELECT count(*) FROM external_identities").fetchone()[0], 0)
            self.assertTrue(all(row[0] == "" for row in conn.execute("SELECT verifier FROM oauth_flows")))

    def test_callback_consumed_once_and_provider_secrets_not_persisted(self):
        flow = self.begin()
        self.assertEqual(self.callback(flow), {"status": "pending"})
        with self.assertRaises(APIError) as failure:
            self.callback(flow)
        self.assertEqual(failure.exception.code, "vk_flow_consumed")
        with self.svc.store.connect() as conn:
            dump = "\n".join(conn.iterdump())
        for secret in ("private-provider-access-token", "private-refresh-token", "one-time-provider-code", "+79990000000"):
            self.assertNotIn(secret, dump)
        self.assertEqual(self.svc.vk_status(flow[1], flow[0]["session"])["status"], "new")

    def test_provider_email_and_display_name_cannot_take_over_existing_account(self):
        flow = self.begin()
        self.callback(flow)
        with self.assertRaises(APIError) as failure:
            self.svc.vk_finish({"name": "NewVK", "email": "owner@example.test", "password": fixture.PASSWORD}, flow[1], flow[0]["session"], "ip")
        self.assertEqual(failure.exception.code, "vk_account_exists")
        with self.svc.store.connect() as conn:
            self.assertEqual(conn.execute("SELECT count(*) FROM external_identities").fetchone()[0], 0)
            self.assertEqual(conn.execute("SELECT count(*) FROM users").fetchone()[0], 3)

    def test_new_identity_registers_player_and_rotates_guest_session(self):
        flow = self.begin()
        self.callback(flow)
        body = {"name": "NewVK", "email": "new@example.test", "password": fixture.PASSWORD, "remember": True}
        result = self.svc.vk_finish(body, flow[1], flow[0]["session"], "ip")
        self.assertEqual(result["user"]["role"], "player")
        self.assertIsNone(result["user"]["game_uuid"])
        self.assertFalse(result["user"]["game_link_verified"])
        self.assertTrue(check_password(fixture.PASSWORD, result["user"]["password_hash"]))
        self.assertIsNone(self.svc.authenticate(flow[0]["token"])[0])
        self.assertIsNotNone(self.svc.authenticate(result["token"])[1])
        self.assertGreater(result["session"]["expires_at"]-result["session"]["created_at"], 29*86400000)
        with self.assertRaises(APIError):
            self.svc.vk_finish(body, flow[1], flow[0]["session"], "ip")

    def test_existing_staff_still_requires_totp_and_rejects_replay(self):
        self.bind("123456", self.owner)
        flow = self.begin()
        self.callback(flow)
        self.assertTrue(self.svc.vk_status(flow[1], flow[0]["session"])["requiresTotp"])
        with self.assertRaises(APIError) as failure:
            self.svc.vk_finish({}, flow[1], flow[0]["session"], "ip")
        self.assertEqual(failure.exception.code, "mfa_required")
        code = totp_code(self.owner["secret"])
        result = self.svc.vk_finish({"totp": code}, flow[1], flow[0]["session"], "ip")
        self.assertEqual(result["user"]["id"], self.owner["user"]["id"])
        self.assertTrue(result["session"]["mfa_at"])
        second = self.begin()
        self.callback(second)
        with self.assertRaises(APIError):
            self.svc.vk_finish({"totp": code}, second[1], second[0]["session"], "ip")

    def test_totp_attempt_limit_persists_across_failed_transactions(self):
        self.bind("123456", self.owner)
        flow = self.begin()
        self.callback(flow)
        for i in range(5):
            with self.assertRaises(APIError) as failure:
                self.svc.vk_finish({"totp": "invalid"}, flow[1], flow[0]["session"], "ip")
            self.assertEqual(failure.exception.code, "mfa_required")
        with self.assertRaises(APIError) as failure:
            self.svc.vk_finish({"totp": totp_code(self.owner["secret"])}, flow[1], flow[0]["session"], "ip")
        self.assertEqual(failure.exception.code, "vk_flow_consumed")

    def test_link_requires_fresh_owner_confirmation_and_blocks_cross_account_link(self):
        self.svc.config.vk_client_id = "12345678"
        with self.assertRaises(APIError) as failure:
            self.begin(self.player, "link")
        self.assertEqual(failure.exception.code, "reauth_required")
        self.svc.reauth({"password": fixture.PASSWORD}, self.player["user"], self.player["session"], "ip")
        flow = self.begin(self.player, "link")
        self.assertEqual(self.callback(flow), {"status": "linked"})
        self.assertTrue(self.svc.account_connections(self.player["user"], self.player["session"])["vk"]["linked"])
        self.svc.reauth({"password": fixture.PASSWORD}, self.other["user"], self.other["session"], "ip")
        other = self.begin(self.other, "link")
        self.assertEqual(self.callback(other), {"status": "error"})
        self.assertEqual(self.svc.vk_status(other[1], other[0]["session"])["code"], "vk_account_linked")
        with self.svc.store.connect() as conn:
            self.assertEqual(conn.execute("SELECT user_id FROM external_identities").fetchone()[0], self.player["user"]["id"])

    def test_unlink_revokes_other_sessions_and_future_vk_login(self):
        self.bind("123456", self.player)
        with self.svc.store.transaction() as conn:
            another = self.svc._new_session(conn, self.player["user"])
        pending = self.begin()
        self.callback(pending)
        result = self.svc.vk_unlink({"password": fixture.PASSWORD}, self.player["user"], self.player["session"], "ip")
        self.assertFalse(result["vk"]["linked"])
        self.assertIsNone(self.svc.authenticate(another["token"])[0])
        self.assertIsNotNone(self.svc.authenticate(self.player["token"])[1])
        self.assertEqual(self.svc.vk_status(pending[1], pending[0]["session"])["status"], "new")

    def test_expired_or_logged_out_browser_cannot_complete_callback(self):
        flow = self.begin()
        with self.svc.store.transaction() as conn:
            conn.execute("UPDATE oauth_flows SET expires_at=?", (now_ms()-1,))
        with self.assertRaises(APIError):
            self.callback(flow)
        flow = self.begin()
        self.svc.logout(flow[0]["session"])
        with self.assertRaises(APIError):
            self.callback(flow)

    def test_http_callback_uses_fixed_redirect_and_same_browser_status(self):
        flow = self.begin()
        bundle, cookie, state, _ = flow
        headers = {"HTTP_COOKIE": COOKIE+"="+bundle["token"]+"; cm_v6_oauth="+cookie}
        query = urlencode({"state": state, "error": "access_denied", "redirect_uri": "https://evil.example"})
        result = self.http("/api/v6/auth/vk/callback?"+query, bundle=bundle, extra=headers)
        self.assertEqual(result["status"], 303)
        self.assertEqual(result["headers"]["Location"], "/#auth/vk")
        status = self.http("/api/v6/auth/vk/status", bundle=bundle, extra=headers)
        self.assertEqual(status["body"]["code"], "vk_cancelled")
        self.assertEqual(self.http("/api/v6/auth/vk/status", bundle=bundle)["status"], 401)

    def test_concurrent_finish_creates_only_one_identity_and_account(self):
        flow = self.begin()
        self.callback(flow)
        body = {"name": "OneAccount", "email": "one@example.test", "password": fixture.PASSWORD}
        def finish(_):
            try:
                return self.svc.vk_finish(body, flow[1], flow[0]["session"], "parallel-ip")["user"]["id"]
            except APIError:
                return None
        with ThreadPoolExecutor(max_workers=2) as pool:
            results = list(pool.map(finish, range(2)))
        self.assertEqual(sum(bool(x) for x in results), 1)
        with self.svc.store.connect() as conn:
            self.assertEqual(conn.execute("SELECT count(*) FROM users WHERE name='OneAccount'").fetchone()[0], 1)
            self.assertEqual(conn.execute("SELECT count(*) FROM external_identities").fetchone()[0], 1)


if __name__ == "__main__":
    unittest.main()
