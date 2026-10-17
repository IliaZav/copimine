"""VK ID authorization-code flow. Provider tokens never reach the browser or DB.

Protocol reference: VKCOM/vkid-web-sdk, commit
8ff76cfe59ea72e370c4c6c3b40712ecfd43aadd, src/auth/auth.ts.
Identity comes from the authenticated user_info response, never a browser ID,
display name, email match, decoded unsigned JWT, or callback query user_id.
"""
import base64
import hashlib
import hmac
import json
import re
import sqlite3
import urllib.error
import urllib.parse
import urllib.request

from .common import APIError, text
from .security import ROLE_RANK, canonical_role, digest, password_hash, token, verify_totp
from .storage import identifier, now_ms

VK_ORIGIN = "https://id.vk.ru"
FLOW_MS = 10 * 60 * 1000


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


class VKAuth:
    def init_vk_auth(self):
        with self.store.connect() as conn:
            conn.executescript("""
            CREATE TABLE IF NOT EXISTS external_identities(
                provider TEXT NOT NULL, subject TEXT NOT NULL, user_id TEXT NOT NULL REFERENCES users(id),
                display_name TEXT NOT NULL, created_at INTEGER NOT NULL,
                PRIMARY KEY(provider,subject), UNIQUE(provider,user_id));
            CREATE TABLE IF NOT EXISTS oauth_flows(
                cookie_hash TEXT PRIMARY KEY, session_hash TEXT NOT NULL, state_hash TEXT NOT NULL,
                verifier TEXT NOT NULL, mode TEXT NOT NULL, target_id TEXT, status TEXT NOT NULL,
                created_at INTEGER NOT NULL, expires_at INTEGER NOT NULL, provider_id TEXT,
                display_name TEXT, error_code TEXT, attempts INTEGER NOT NULL DEFAULT 0);
            """)

    def vk_enabled(self):
        return bool(self.config.vk_client_id)

    def _vk_configured(self):
        if not self.vk_enabled():
            raise APIError(503, "VK ID is not configured.", "vk_unconfigured")

    def _vk_flow(self, conn, cookie, session):
        if not isinstance(cookie, str) or not 20 <= len(cookie) <= 128 or not session:
            raise APIError(401, "Restart VK sign-in in this browser.", "vk_flow_expired")
        row = conn.execute("SELECT * FROM oauth_flows WHERE cookie_hash=?", (digest(cookie),)).fetchone()
        current = conn.execute("SELECT * FROM sessions WHERE token_hash=?", (session["token_hash"],)).fetchone()
        if not row or not current or current["revoked_at"] or current["expires_at"] <= now_ms() or row["expires_at"] <= now_ms() or not hmac.compare_digest(row["session_hash"], session["token_hash"]):
            raise APIError(401, "Restart VK sign-in in this browser.", "vk_flow_expired")
        if current["user_id"] is not None:
            actor = conn.execute("SELECT * FROM users WHERE id=?", (current["user_id"],)).fetchone()
            if not actor or not actor["enabled"] or canonical_role(actor["role"]) is None or actor["auth_epoch"] != current["auth_epoch"]:
                raise APIError(401, "Restart VK sign-in in this browser.", "vk_flow_expired")
        return dict(row)

    def vk_start(self, body, user, session, ip):
        self._vk_configured()
        if set(body) - {"mode", "remember"} or body.get("mode", "login") not in ("login", "link"):
            raise APIError(400, "Invalid VK sign-in mode.")
        if not session:
            raise APIError(401, "A browser session is required.")
        mode = body.get("mode", "login")
        self.auth_rate(ip, "vk-start:" + session["token_hash"])
        if mode == "login" and user:
            raise APIError(409, "Sign out before using another VK account.", "already_authenticated")
        raw_cookie, state, verifier = token(), token(), token()
        challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip("=")
        with self.store.transaction() as conn:
            target = self.actor_current(conn, user, session, recent=True)["id"] if mode == "link" else None
            conn.execute("DELETE FROM oauth_flows WHERE expires_at<? OR session_hash=?", (now_ms(), session["token_hash"]))
            conn.execute("INSERT INTO oauth_flows(cookie_hash,session_hash,state_hash,verifier,mode,target_id,status,created_at,expires_at) VALUES(?,?,?,?,?,?,?,?,?)",
                         (digest(raw_cookie), session["token_hash"], digest(state), verifier, mode, target, "started", now_ms(), now_ms()+FLOW_MS))
        params = {"client_id": self.config.vk_client_id, "redirect_uri": self.config.origin+"/api/v6/auth/vk/callback",
                  "response_type": "code", "code_challenge": challenge, "code_challenge_method": "S256",
                  "state": state, "scope": "vkid.personal_info", "prompt": "login"}
        return {"url": VK_ORIGIN+"/authorize?"+urllib.parse.urlencode(params), "cookie": raw_cookie}

    def _vk_request(self, endpoint, query, form):
        if endpoint not in ("auth", "user_info"):
            raise ValueError("Unexpected VK API endpoint.")
        request = urllib.request.Request(VK_ORIGIN+"/oauth2/"+endpoint+"?"+urllib.parse.urlencode(query),
            data=urllib.parse.urlencode(form).encode(), method="POST",
            headers={"Content-Type": "application/x-www-form-urlencoded", "Accept": "application/json"})
        try:
            opener = urllib.request.build_opener(NoRedirect())
            with opener.open(request, timeout=12) as response:
                raw = response.read(262145)
                if response.status != 200 or len(raw) > 262144:
                    raise ValueError()
            value = json.loads(raw)
            if not isinstance(value, dict) or value.get("error"):
                raise ValueError()
            return value
        except (OSError, ValueError, urllib.error.URLError):
            raise APIError(502, "VK ID could not verify this sign-in. Try again.", "vk_provider_error") from None

    def vk_callback(self, query, cookie, user, session, ip):
        self._vk_configured()
        self.auth_rate(ip, "vk-callback:"+digest(str(cookie)))
        def value(key):
            entries = query.get(key, [])
            if len(entries) != 1 or not isinstance(entries[0], str):
                raise APIError(400, "Invalid VK callback.", "vk_invalid_callback")
            return entries[0]
        with self.store.transaction() as conn:
            flow = self._vk_flow(conn, cookie, session)
            state = value("state")
            if len(state) > 128 or not hmac.compare_digest(digest(state), flow["state_hash"]):
                raise APIError(403, "VK callback does not match the browser request.", "vk_state_mismatch")
            if flow["status"] != "started":
                raise APIError(409, "VK callback was already consumed.", "vk_flow_consumed")
            if query.get("error"):
                conn.execute("UPDATE oauth_flows SET status='error',error_code='vk_cancelled',verifier='' WHERE cookie_hash=?", (flow["cookie_hash"],))
                return {"status": "error"}
            code, device = value("code"), value("device_id")
            if not 1 <= len(code) <= 4096 or not 1 <= len(device) <= 512 or any(ord(c)<32 for c in code+device):
                raise APIError(400, "Invalid VK callback.", "vk_invalid_callback")
            conn.execute("UPDATE oauth_flows SET status='exchanging',verifier='' WHERE cookie_hash=?", (flow["cookie_hash"],))
        try:
            tokens = self._vk_request("auth", {"grant_type": "authorization_code", "client_id": self.config.vk_client_id,
                "redirect_uri": self.config.origin+"/api/v6/auth/vk/callback", "code_verifier": flow["verifier"],
                "device_id": device, "state": state}, {"code": code})
            subject = str(tokens.get("user_id", ""))
            access = tokens.get("access_token")
            if not re.fullmatch(r"[1-9][0-9]{0,24}", subject) or not isinstance(access, str) or not 10 <= len(access) <= 16384 or not isinstance(tokens.get("state"), str) or not hmac.compare_digest(tokens["state"], state):
                raise APIError(502, "VK ID returned an invalid identity.", "vk_invalid_identity")
            response = self._vk_request("user_info", {"client_id": self.config.vk_client_id}, {"access_token": access})
            info = response.get("user")
            if not isinstance(info, dict) or str(info.get("user_id", "")) != subject:
                raise APIError(502, "VK identity responses disagree.", "vk_invalid_identity")
            display = " ".join(str(info.get(k, ""))[:80] for k in ("first_name", "last_name")).strip()
            display = re.sub(r"[\x00-\x1f\x7f]", "", display)[:120] or "VK ID"
            # Tokens and unverified provider email/phone/avatar are deliberately not persisted.
            with self.store.transaction() as conn:
                current = self._vk_flow(conn, cookie, session)
                if current["status"] != "exchanging":
                    raise APIError(409, "VK callback was already consumed.", "vk_flow_consumed")
                if current["mode"] == "link":
                    actor = self.actor_current(conn, user, session, recent=True)
                    if actor["id"] != current["target_id"]:
                        raise APIError(403, "VK linking account changed.", "vk_binding_changed")
                    existing = conn.execute("SELECT * FROM external_identities WHERE provider='vk' AND (subject=? OR user_id=?)", (subject, actor["id"])).fetchall()
                    if any(r["subject"] != subject or r["user_id"] != actor["id"] for r in existing):
                        raise APIError(409, "This VK account is already linked.", "vk_account_linked")
                    conn.execute("INSERT OR IGNORE INTO external_identities VALUES('vk',?,?,?,?)", (subject, actor["id"], display, now_ms()))
                    self.store.audit(conn, actor["id"], "account.vk.link", actor["id"], "Account owner confirmed VK ID linking", {})
                    status = "linked"
                else:
                    status = "pending"
                conn.execute("UPDATE oauth_flows SET status=?,provider_id=?,display_name=? WHERE cookie_hash=?",
                             (status, subject, display, current["cookie_hash"]))
            return {"status": status}
        except APIError as error:
            with self.store.transaction() as conn:
                conn.execute("UPDATE oauth_flows SET status='error',error_code=?,verifier='' WHERE cookie_hash=? AND status='exchanging'",
                             (error.code, flow["cookie_hash"]))
            return {"status": "error"}

    def vk_status(self, cookie, session):
        with self.store.connect() as conn:
            flow = self._vk_flow(conn, cookie, session)
            if flow["status"] == "error":
                messages = {"vk_cancelled": "Вход через VK отменён.", "vk_account_linked": "Этот VK уже связан с другим аккаунтом. Войди привычным способом.",
                            "reauth_required": "Подтверди вход ещё раз и повтори привязку VK."}
                return {"status": "error", "message": messages.get(flow["error_code"], "VK не подтвердил вход. Попробуй ещё раз."), "code": flow["error_code"]}
            if flow["status"] == "linked":
                return {"status": "linked", "displayName": flow["display_name"]}
            if flow["status"] != "pending":
                return {"status": "error", "message": "Вход не завершён. Начни вход через VK ещё раз."}
            row = conn.execute("SELECT u.* FROM users u JOIN external_identities e ON e.user_id=u.id WHERE e.provider='vk' AND e.subject=?", (flow["provider_id"],)).fetchone()
            if row and (not row["enabled"] or canonical_role(row["role"]) is None):
                return {"status": "error", "message": "Этот аккаунт сейчас недоступен."}
            return {"status": "login" if row else "new", "displayName": flow["display_name"],
                    "requiresTotp": bool(row and (ROLE_RANK[row["role"]]>0 or row["totp_secret"]))}

    def vk_finish(self, body, cookie, session, ip):
        self._vk_configured()
        if set(body) - {"name", "email", "password", "totp", "remember"}:
            raise APIError(400, "Unexpected VK sign-in field.")
        self.auth_rate(ip, "vk-finish:"+digest(str(cookie)))
        with self.store.connect() as conn:
            flow = self._vk_flow(conn, cookie, session)
            existing = conn.execute("SELECT user_id FROM external_identities WHERE provider='vk' AND subject=?", (flow["provider_id"],)).fetchone()
        new_password = None
        if not existing:
            name = text(body.get("name"), 3, 32, "name")
            email = text(body.get("email"), 3, 254, "email").lower()
            if not re.fullmatch(r"[A-Za-z0-9_]+", name) or not re.fullmatch(r"[^\s@]+@[^\s@]+\.[^\s@]+", email):
                raise APIError(400, "Check the account name and email.", "invalid_registration")
            try:
                new_password = password_hash(body.get("password"))
            except ValueError as error:
                raise APIError(400, str(error), "invalid_password") from None
        error = None
        bundle = None
        with self.store.transaction() as conn:
            flow = self._vk_flow(conn, cookie, session)
            if flow["status"] != "pending" or flow["attempts"] >= 5:
                raise APIError(409, "Restart VK sign-in.", "vk_flow_consumed")
            linked = conn.execute("SELECT u.* FROM users u JOIN external_identities e ON e.user_id=u.id WHERE e.provider='vk' AND e.subject=?", (flow["provider_id"],)).fetchone()
            if linked:
                if not linked["enabled"] or canonical_role(linked["role"]) is None:
                    raise APIError(401, "This account is unavailable.", "invalid_credentials")
                mfa = ROLE_RANK[linked["role"]]>0 or bool(linked["totp_secret"])
                if mfa:
                    step = verify_totp(linked["totp_secret"], body.get("totp"), linked["totp_last_step"])
                    if step is None:
                        conn.execute("UPDATE oauth_flows SET attempts=attempts+1 WHERE cookie_hash=?", (flow["cookie_hash"],))
                        error = APIError(401, "Enter the current authenticator code.", "mfa_required")
                    else:
                        conn.execute("UPDATE users SET totp_last_step=? WHERE id=?", (step, linked["id"]))
                user = linked
            else:
                if new_password is None:
                    raise APIError(409, "VK account binding changed. Restart sign-in.", "vk_binding_changed")
                uid, at = identifier(), now_ms()
                try:
                    conn.execute("INSERT INTO users(id,name,email,password_hash,role,created_at,updated_at,display_name) VALUES(?,?,?,?,?,?,?,?)",
                                 (uid, name, email, new_password, "player", at, at, name))
                    conn.execute("INSERT INTO external_identities VALUES('vk',?,?,?,?)", (flow["provider_id"], uid, flow["display_name"], at))
                except sqlite3.IntegrityError:
                    raise APIError(409, "This name or email is already registered. Sign in and link VK in settings.", "vk_account_exists") from None
                user = conn.execute("SELECT * FROM users WHERE id=?", (uid,)).fetchone()
                mfa = False
                self.store.audit(conn, uid, "account.register", uid, "Registration with verified VK ID and separate site password", {})
            if not error:
                bundle = self._new_session(conn, user, session, body.get("remember") is True, mfa)
                conn.execute("UPDATE oauth_flows SET status='consumed',state_hash='',verifier='' WHERE cookie_hash=?", (flow["cookie_hash"],))
                self.store.audit(conn, user["id"], "auth.vk.login", user["id"], "VK ID and MFA login" if mfa else "VK ID login", {})
        if error:
            raise error
        return bundle

    def account_connections(self, user, session):
        self.require(user, session)
        with self.store.connect() as conn:
            row = conn.execute("SELECT display_name,created_at FROM external_identities WHERE provider='vk' AND user_id=?", (user["id"],)).fetchone()
        return {"vk": {"linked": bool(row), "displayName": row["display_name"] if row else None, "linkedAt": row["created_at"] if row else None}}

    def vk_unlink(self, body, user, session, ip):
        if set(body) - {"password", "totp"}:
            raise APIError(400, "Unexpected unlink field.")
        self.require(user, session)
        if body.get("password"):
            self.reauth(body, user, session, ip)
        with self.store.transaction() as conn:
            actor = self.actor_current(conn, user, session, recent=True)
            conn.execute("DELETE FROM external_identities WHERE provider='vk' AND user_id=?", (actor["id"],))
            conn.execute("DELETE FROM oauth_flows WHERE target_id=?", (actor["id"],))
            conn.execute("UPDATE users SET auth_epoch=auth_epoch+1 WHERE id=?", (actor["id"],))
            conn.execute("UPDATE sessions SET revoked_at=? WHERE user_id=? AND token_hash<>?", (now_ms(), actor["id"], session["token_hash"]))
            conn.execute("UPDATE sessions SET auth_epoch=auth_epoch+1 WHERE token_hash=?", (session["token_hash"],))
            self.store.audit(conn, actor["id"], "account.vk.unlink", actor["id"], "Account owner removed VK access and revoked other sessions", {})
        return {"vk": {"linked": False}}
