"""Content collections: real authorization, concurrent writes, and public asset boundaries."""
from concurrent.futures import ThreadPoolExecutor
import json
import unittest

from server.common import APIError
from server.core import Service
from server.storage import now_ms
from server.tests import test_backend as fixture


class ContentTests(unittest.TestCase):
    tearDown = fixture.BackendTests.tearDown
    account = fixture.BackendTests.account
    http = fixture.BackendTests.http

    def setUp(self):
        fixture.BackendTests.setUp(self)
        self.asset = "assets/img/approved-world.webp"
        seed = {
            "faq": [
                {"id": "public", "question": "Public question", "answer": "Public answer", "published": True},
                {"id": "draft", "question": "Unpublished fixture question", "answer": "Private draft answer", "published": False},
            ],
            "gallery": [{"id": "world", "src": self.asset, "title": "Approved world",
                         "caption": "Saved world", "alt": "World landscape", "position": 0, "published": True}],
        }
        (self.site / "data" / "content-v6-public.json").write_text(json.dumps(seed), encoding="utf-8")
        with self.svc.store.transaction() as conn:
            conn.execute("DELETE FROM settings WHERE key IN ('content_faq','content_gallery')")
        self.svc.init_content_lists()

    def test_collection_access_drafts_csrf_and_role_revocation(self):
        for kind in ("faq", "gallery"):
            endpoint = "/api/v6/admin/" + kind
            self.assertEqual(self.http(endpoint)["status"], 401)
            self.assertEqual(self.http(endpoint, bundle=self.player)["status"], 403)
        public = self.http("/api/v6/state")
        self.assertEqual(public["status"], 200)
        self.assertNotIn(b"Unpublished fixture question", public["raw"])
        self.assertNotIn(b"Private draft answer", public["raw"])
        staff = self.http("/api/v6/admin/faq", bundle=self.owner)
        self.assertEqual(len(staff["body"]["faq"]), 2)
        body = {"version": 1, "faq": staff["body"]["faq"], "reason": "Review a private collection"}
        self.assertEqual(self.http("/api/v6/admin/faq", "PATCH", body, self.owner, csrf=False)["status"], 403)
        with self.svc.store.transaction() as conn:
            conn.execute("UPDATE sessions SET mfa_at=? WHERE token_hash=?",
                         (now_ms() - 301000, self.owner["session"]["token_hash"]))
        denied = self.http("/api/v6/admin/faq", "PATCH", body, self.owner)
        self.assertEqual(denied["body"]["error"]["code"], "reauth_required")
        # A stale, already-authenticated request must still see a role revoked
        # before its write transaction, even if its in-memory user is an owner.
        with self.svc.store.transaction() as conn:
            conn.execute("UPDATE sessions SET mfa_at=? WHERE token_hash=?",
                         (now_ms(), self.owner["session"]["token_hash"]))
            conn.execute("UPDATE users SET role='player' WHERE id=?", (self.owner["user"]["id"],))
        with self.assertRaises(APIError) as error:
            self.svc.update_content_list("faq", body, self.owner["user"], self.owner["session"])
        self.assertEqual(error.exception.status, 403)
        self.assertEqual(self.svc.content_list("faq", True)["version"], 1)

    def test_concurrent_cas_has_one_winner_durable_content_and_private_audit(self):
        def save(index):
            body = {"version": 1, "reason": "Private editorial decision " + str(index), "faq": [
                {"id": "saved", "question": "Saved " + str(index), "answer": "Published answer", "published": True},
                {"id": "hidden", "question": "Hidden question", "answer": "Unreleased answer", "published": False},
            ]}
            return self.http("/api/v6/admin/faq", "PATCH", body, self.owner)
        with ThreadPoolExecutor(max_workers=2) as pool:
            results = list(pool.map(save, range(2)))
        self.assertEqual(sorted(result["status"] for result in results), [200, 409])
        failed = next(result for result in results if result["status"] == 409)
        self.assertEqual(failed["body"]["error"]["code"], "version_conflict")
        winner = next(result["body"] for result in results if result["status"] == 200)
        restarted = Service(self.svc.config)
        self.assertEqual(restarted.content_list("faq", True), winner)
        self.assertEqual(restarted.content_list("faq")["faq"], winner["faq"][:1])
        with self.svc.store.connect() as conn:
            audits = conn.execute("SELECT * FROM audit WHERE kind='content.faq'").fetchall()
        self.assertEqual(len(audits), 1)
        self.assertTrue(audits[0]["reason"].startswith("Private editorial decision"))
        public = self.http("/api/v6/state")
        self.assertNotIn(b"Private editorial decision", public["raw"])
        self.assertNotIn(b"Unreleased answer", public["raw"])

    def test_gallery_accepts_only_exact_packaged_asset_and_typed_metadata(self):
        current = self.svc.content_list("gallery", True)
        approved = current["gallery"][0]
        forbidden = ("https://example.test/foreign.webp", "data:image/svg+xml,<svg/>",
                     "../server/private/icons/hidden.png", "/" + self.asset,
                     self.asset + "?override=1", self.asset + "#fragment", "assets/img/%2e%2e/private.webp")
        for source in forbidden:
            with self.subTest(source=source):
                body = {"version": 1, "reason": "Invalid fixture image", "gallery": [dict(approved, src=source)]}
                result = self.http("/api/v6/admin/gallery", "PATCH", body, self.owner)
                self.assertEqual(result["status"], 400)
                self.assertEqual(result["body"]["error"]["code"], "unapproved_asset")
        for altered in (dict(approved, position=True), dict(approved, published="true"),
                        dict(approved, html="<script>fixture</script>")):
            result = self.http("/api/v6/admin/gallery", "PATCH",
                               {"version": 1, "reason": "Invalid fixture metadata", "gallery": [altered]}, self.owner)
            self.assertEqual(result["status"], 400)
        result = self.http("/api/v6/admin/gallery", "PATCH", {"version": 1,
                           "reason": "Hide approved image temporarily", "gallery": [dict(approved, published=False)]}, self.owner)
        self.assertEqual(result["status"], 200)
        self.assertEqual(result["body"]["version"], 2)
        self.assertEqual(self.svc.content_list("gallery")["gallery"], [])
        self.assertEqual(self.svc.content_list("gallery", True)["gallery"][0]["src"], self.asset)


if __name__ == "__main__":
    unittest.main()
