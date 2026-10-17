"""Verified contacts, one-use browser-bound OTPs, and an encrypted durable outbox.

No provider I/O runs in an HTTP transaction. OTP verifiers are HMACs; recipients
and queue bodies are encrypted with a separate private key outside SQLite.
Provider acceptance is not delivery. A worker crash or ambiguous network result
never automatically sends the same message again.
"""
import base64
import hashlib
import hmac
import json
import os
import re
import secrets

from cryptography.fernet import Fernet, InvalidToken
from services.notifications import NotificationGateway, DeliveryError
from .common import APIError
from .security import ROLE_RANK, canonical_role, check_password, password_hash, verify_totp
from .storage import encoded, identifier, now_ms

CODE_TTL = 600000
RESEND_MS = 60000
MAX_ATTEMPTS = 5
CHANNELS = {"email", "sms"}
GENERIC_MESSAGE = "Если контакт подтверждён, мы отправим код."
ENV_KEYS = {"CM_NOTIFY_MODE", "CM_SMTP_HOST", "CM_SMTP_PORT", "CM_SMTP_TLS", "CM_SMTP_USER",
            "CM_SMTP_PASSWORD", "CM_SMTP_FROM", "CM_SMSRU_API_ID", "CM_SMSRU_SENDER",
            "CM_NOTIFY_EMAIL_DAILY_LIMIT", "CM_NOTIFY_SMS_DAILY_LIMIT"}


def contact_value(channel, value):
    if not isinstance(channel, str) or channel not in CHANNELS or not isinstance(value, str) or len(value) > 254 or any(ord(c) < 32 for c in value):
        raise APIError(400, "Invalid contact.")
    value = value.strip()
    if channel == "email":
        value = value.lower()
        if not value.isascii() or not re.fullmatch(r"[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?\.[a-z]{2,63}", value):
            raise APIError(400, "Invalid email address.")
    else:
        if not re.fullmatch(r"[+0-9 ()-]+", value):
            raise APIError(400, "Use an international phone number, beginning with +.")
        value = re.sub(r"[ ()-]", "", value)
        if not re.fullmatch(r"\+[1-9][0-9]{8,14}", value):
            raise APIError(400, "Use an international phone number, beginning with +.")
    return value


def masked_contact(channel, value):
    if channel == "sms":
        return value[:2] + "•" * max(3, len(value) - 6) + value[-4:]
    local, domain = value.rsplit("@", 1)
    return local[:1] + "•••@" + domain


def notice_content(kind, reference, payload):
    """A small fixed set of transactional templates; no caller-provided prose."""
    if not isinstance(reference, str) or not re.fullmatch(r"[A-Za-z0-9:_-]{1,128}", reference) or not isinstance(payload, dict):
        raise ValueError("Invalid notice reference or payload.")
    if kind in ("order", "payment"):
        field = "total" if kind == "order" else "amount"
        currency = payload.get("currency")
        if set(payload) != {field, "currency"} or type(payload.get(field)) is not int or not 0 <= payload[field] <= 10**12 or currency not in ("AR", "CM") or (kind == "payment" and currency != "CM"):
            raise ValueError("Invalid financial notice payload.")
        if kind == "order":
            return "CopiMine: заказ оформлен", f"Заказ {reference} оформлен. Списано {payload[field]} {currency}. Выдача предметов ожидает подтверждения игрового сервера."
        return "CopiMine: пополнение подтверждено", f"Платёж {reference} подтверждён. На баланс сайта зачислено {payload[field]} CM."
    if kind == "ticket":
        statuses = {"open": "открыто", "in_progress": "в работе", "awaiting_player": "ожидает ответа игрока", "closed": "закрыто", "rejected": "отклонено"}
        if set(payload) != {"status"} or not isinstance(payload["status"], str) or payload["status"] not in statuses:
            raise ValueError("Invalid support notice payload.")
        return "CopiMine: ответ поддержки", "Ваше обращение обновлено. Статус: " + statuses[payload["status"]] + ". Ответ доступен в личном кабинете после входа."
    if kind == "password" and not payload:
        return "CopiMine: пароль изменён", "Пароль вашего аккаунта CopiMine изменён. Если это сделали не вы, восстановите доступ на сайте и обратитесь в поддержку."
    raise ValueError("Unsupported notice kind or payload.")


class Notifications:
    def init_notifications(self):
        env = getattr(self.config, "notification_env", {})
        if not isinstance(env, dict) or set(env) - ENV_KEYS or any(not isinstance(v, str) for v in env.values()):
            raise ValueError("notificationEnv must contain only the documented string settings.")
        self.notification_env = dict(env)
        with self.store.connect() as conn:
            conn.executescript("""
            CREATE TABLE IF NOT EXISTS notification_metadata(key TEXT PRIMARY KEY,value TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS account_contacts(
                user_id TEXT NOT NULL REFERENCES users(id),channel TEXT NOT NULL,value_cipher TEXT NOT NULL,
                target_hash TEXT NOT NULL,verified_at INTEGER NOT NULL,
                PRIMARY KEY(user_id,channel),UNIQUE(channel,target_hash));
            CREATE TABLE IF NOT EXISTS auth_challenges(
                id TEXT PRIMARY KEY,user_id TEXT,purpose TEXT NOT NULL,channel TEXT NOT NULL,
                target_hash TEXT NOT NULL,target_cipher TEXT NOT NULL,code_hash TEXT NOT NULL,
                session_hash TEXT NOT NULL,auth_epoch INTEGER,created_at INTEGER NOT NULL,expires_at INTEGER NOT NULL,
                attempts INTEGER NOT NULL DEFAULT 0,consumed_at INTEGER,status TEXT NOT NULL DEFAULT 'active');
            CREATE INDEX IF NOT EXISTS challenge_session ON auth_challenges(session_hash,purpose,channel);
            CREATE TABLE IF NOT EXISTS notification_outbox(
                id TEXT PRIMARY KEY,dedupe_key TEXT NOT NULL UNIQUE,user_id TEXT,challenge_id TEXT,
                channel TEXT NOT NULL,purpose TEXT NOT NULL,target_hash TEXT NOT NULL,payload_cipher TEXT,
                status TEXT NOT NULL,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,
                lease_until INTEGER,lease_id TEXT,provider_reference TEXT,error_code TEXT);
            CREATE INDEX IF NOT EXISTS notification_pending ON notification_outbox(status,created_at);
            """)
        with self.store.transaction() as conn:
            configured = getattr(self.config, "notification_key", "")
            if configured:
                if not isinstance(configured, str) or not 32 <= len(configured) <= 4096:
                    raise ValueError("notificationKey must contain at least 32 characters.")
                secret = hashlib.sha256(configured.encode()).digest()
            else:
                key_file = self.store.directory / "notification.key"
                if key_file.is_symlink():
                    raise ValueError("The notification key must be a private regular file.")
                if not key_file.exists():
                    if conn.execute("SELECT 1 FROM notification_metadata WHERE key='key_fingerprint'").fetchone():
                        raise ValueError("The notification key is missing; restore the private key backup.")
                    descriptor = os.open(key_file, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
                    with os.fdopen(descriptor, "wb") as stream:
                        stream.write(secrets.token_bytes(32)); stream.flush(); os.fsync(stream.fileno())
                secret = key_file.read_bytes()
                if len(secret) != 32:
                    raise ValueError("Invalid notification key file; restore the private key backup.")
                try:
                    key_file.chmod(0o600)
                except OSError:
                    pass
            fingerprint = hashlib.sha256(b"copimine-notification-key:" + secret).hexdigest()
            existing = conn.execute("SELECT value FROM notification_metadata WHERE key='key_fingerprint'").fetchone()
            if existing and not hmac.compare_digest(existing[0], fingerprint):
                raise ValueError("Notification key mismatch; restore the key used for existing contacts.")
            conn.execute("INSERT OR IGNORE INTO notification_metadata VALUES('key_fingerprint',?)", (fingerprint,))
        self._notification_box = Fernet(base64.urlsafe_b64encode(hashlib.sha256(secret + b":queue").digest()))
        self._notification_hmac = hashlib.sha256(secret + b":verifiers").digest()
        self.notification_gateway = NotificationGateway(self.notification_env)

    def notification_providers(self):
        e = self.notification_env
        live = e.get("CM_NOTIFY_MODE") == "live"
        email = live and all(e.get(k) for k in ("CM_SMTP_HOST", "CM_SMTP_USER", "CM_SMTP_PASSWORD", "CM_SMTP_FROM"))
        email = email and bool(re.fullmatch(r"[A-Za-z0-9.-]+", e.get("CM_SMTP_HOST", ""))) and e.get("CM_SMTP_TLS", "starttls") in ("starttls", "tls")
        if email:
            try:
                contact_value("email", e["CM_SMTP_FROM"])
                port = int(e.get("CM_SMTP_PORT", "465" if e.get("CM_SMTP_TLS") == "tls" else "587"))
                email = 1 <= port <= 65535
            except (APIError, ValueError):
                email = False
        sms = live and bool(e.get("CM_SMSRU_API_ID"))
        return {"email": {"enabled": bool(email)}, "sms": {"enabled": bool(sms)}}

    def _notification_configured(self, channel):
        if not isinstance(channel, str) or channel not in CHANNELS:
            raise APIError(400, "Unknown contact channel.")
        if not self.notification_providers()[channel]["enabled"]:
            raise APIError(503, "This delivery service is not configured.", "provider_unconfigured")

    def _notification_hash(self, domain, value):
        return hmac.new(self._notification_hmac, (domain + "\0" + value).encode(), hashlib.sha256).hexdigest()

    def _contact_hash(self, channel, value):
        return self._notification_hash("contact:" + channel, value)

    def _notification_encrypt(self, value):
        return self._notification_box.encrypt(encoded(value).encode()).decode()

    def _notification_decrypt(self, value):
        return json.loads(self._notification_box.decrypt(value.encode()))

    def _notification_session(self, conn, session):
        row = conn.execute("SELECT * FROM sessions WHERE token_hash=?", (session.get("token_hash", "") if session else "",)).fetchone()
        if not row or row["revoked_at"] or row["expires_at"] <= now_ms():
            raise APIError(401, "Restart this verification in the same browser.", "invalid_code")
        if row["user_id"]:
            actor = conn.execute("SELECT * FROM users WHERE id=?", (row["user_id"],)).fetchone()
            if not actor or not actor["enabled"] or canonical_role(actor["role"]) is None or actor["auth_epoch"] != row["auth_epoch"]:
                raise APIError(401, "Restart this verification in the same browser.", "invalid_code")
            if ROLE_RANK[actor["role"]] > 0 and (not actor["totp_secret"] or not row["mfa_at"]):
                raise APIError(401, "Restart this verification in the same browser.", "invalid_code")
        return dict(row)

    def _verified_contact(self, conn, user_id, channel, target_hash=None):
        row = conn.execute("SELECT * FROM account_contacts WHERE user_id=? AND channel=?", (user_id, channel)).fetchone()
        user = conn.execute("SELECT * FROM users WHERE id=?", (user_id,)).fetchone()
        if not row or not user or not user["enabled"] or canonical_role(user["role"]) is None:
            return None
        if target_hash and not hmac.compare_digest(row["target_hash"], target_hash):
            return None
        if channel == "email":
            try:
                current = self._contact_hash(channel, contact_value(channel, user["email"]))
            except APIError:
                return None
            if not hmac.compare_digest(current, row["target_hash"]):
                return None
        return dict(row)

    def _contacts_view(self, conn, actor):
        rows = []
        email = str(actor["email"])
        verified = self._verified_contact(conn, actor["id"], "email")
        rows.append({"channel": "email", "value": email,
                     "masked": masked_contact("email", email) if "@" in email else "•••",
                     "verified": bool(verified), "verifiedAt": verified["verified_at"] if verified else None})
        phone = self._verified_contact(conn, actor["id"], "sms")
        if phone:
            value = self._notification_decrypt(phone["value_cipher"])
            rows.append({"channel": "sms", "value": value, "masked": masked_contact("sms", value),
                         "verified": True, "verifiedAt": phone["verified_at"]})
        return {"contacts": rows}

    def contacts(self, user, session):
        self.require(user, session)
        with self.store.transaction() as conn:
            actor = self.actor_current(conn, user, session)
            current = self._notification_session(conn, session)
            if current["user_id"] != actor["id"]:
                raise APIError(403, "Permission denied.", "forbidden")
            return self._contacts_view(conn, actor)

    def _contact_proof(self, conn, body, user, session):
        actor = self.actor_current(conn, user, session)
        current = self._notification_session(conn, session)
        if current["user_id"] != actor["id"]:
            raise APIError(403, "Permission denied.", "forbidden")
        at = now_ms()
        needs_mfa = ROLE_RANK[actor["role"]] > 0 or bool(actor["totp_secret"])
        fresh = max(current["created_at"], current["mfa_at"] or 0) >= at - 300000
        fresh_mfa = bool(current["mfa_at"] and current["mfa_at"] >= at - 300000)
        supplied_password = "password" in body
        if supplied_password and not check_password(body.get("password"), actor["password_hash"]):
            raise APIError(401, "Invalid password or authenticator code.", "invalid_credentials")
        if not supplied_password and not fresh:
            raise APIError(403, "Confirm your identity before changing recovery contacts.", "reauth_required")
        proved_mfa = False
        if needs_mfa and (not fresh_mfa or "totp" in body):
            step = verify_totp(actor["totp_secret"], body.get("totp"), actor["totp_last_step"], now=at / 1000)
            if step is None:
                raise APIError(403, "Confirm your identity with the authenticator code.", "reauth_required")
            conn.execute("UPDATE users SET totp_last_step=? WHERE id=?", (step, actor["id"]))
            proved_mfa = True
        # Reusing a recent proof must never renew that proof indefinitely.
        if (supplied_password and not needs_mfa) or proved_mfa:
            conn.execute("UPDATE sessions SET mfa_at=? WHERE token_hash=?", (at, current["token_hash"]))
        return actor, current

    def _notification_start_rate(self, ip, channel, target_hash):
        ip_key = self._notification_hash("request-ip", str(ip))
        if not self.store.rate("otp-ip:" + ip_key, 10, 900000):
            raise APIError(429, "Too many requests. Try again later.", "rate_limited")
        if not self.store.rate("otp-target:" + channel + ":" + target_hash, 3, 900000):
            raise APIError(429, "Too many requests. Try again later.", "rate_limited")
        if not self.store.rate("otp-resend:" + channel + ":" + target_hash, 1, RESEND_MS):
            raise APIError(429, "Wait one minute before requesting a new code.", "rate_limited")

    def _notification_attempt_rate(self, ip):
        if not self.store.rate("otp-attempt:" + self._notification_hash("attempt-ip", str(ip)), 30, 300000):
            raise APIError(429, "Too many attempts. Try again later.", "rate_limited")

    def _notification_budget(self, conn, channel):
        default = 100 if channel == "sms" else 1000
        try:
            limit = int(self.notification_env.get("CM_NOTIFY_" + channel.upper() + "_DAILY_LIMIT", str(default)))
        except ValueError:
            limit = 0
        if not 1 <= limit <= 100000:
            return False
        count = conn.execute("SELECT count(*) FROM notification_outbox WHERE channel=? AND created_at>?", (channel, now_ms()-86400000)).fetchone()[0]
        return count < limit

    def queue_notice(self, conn, user_id, kind, reference, payload):
        """Insert within the caller's business transaction; never send here."""
        notice_content(kind, reference, payload)
        if not self.notification_providers()["email"]["enabled"]:
            return None
        contact = self._verified_contact(conn, user_id, "email")
        if not contact:
            return None
        dedupe = "notice:" + kind + ":" + user_id + ":" + reference
        previous = conn.execute("SELECT id FROM notification_outbox WHERE dedupe_key=?", (dedupe,)).fetchone()
        if previous:
            return previous[0]
        if not self._notification_budget(conn, "email"):
            return None
        target = self._notification_decrypt(contact["value_cipher"])
        content = self._notification_encrypt({"target": target, "kind": kind, "reference": reference, "values": payload})
        operation, at = identifier(), now_ms()
        conn.execute("INSERT INTO notification_outbox(id,dedupe_key,user_id,channel,purpose,target_hash,payload_cipher,status,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
                     (operation, dedupe, user_id, "email", "notice", contact["target_hash"], content, "queued", at, at))
        return operation

    def _new_challenge(self, conn, session, user, purpose, channel, target):
        at, challenge_id = now_ms(), identifier()
        target_hash = self._contact_hash(channel, target)
        code = str(secrets.randbelow(1000000)).zfill(6)
        # Replacement affects only this browser, never someone else's challenge.
        previous = [row[0] for row in conn.execute("SELECT id FROM auth_challenges WHERE session_hash=? AND purpose=? AND channel=? AND status='active'",
                                                  (session["token_hash"], purpose, channel))]
        for old in previous:
            conn.execute("UPDATE auth_challenges SET status='superseded',consumed_at=? WHERE id=?", (at, old))
            conn.execute("UPDATE notification_outbox SET status='cancelled',payload_cipher=NULL,updated_at=? WHERE challenge_id=? AND status='queued'", (at, old))
        conn.execute("INSERT INTO auth_challenges(id,user_id,purpose,channel,target_hash,target_cipher,code_hash,session_hash,auth_epoch,created_at,expires_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                     (challenge_id, user["id"] if user else None, purpose, channel, target_hash, self._notification_encrypt(target),
                      self._notification_hash("code:" + challenge_id, code), session["token_hash"], user["auth_epoch"] if user else None, at, at + CODE_TTL))
        if user and self._notification_budget(conn, channel):
            payload = self._notification_encrypt({"target": target, "code": code})
            conn.execute("INSERT INTO notification_outbox(id,dedupe_key,user_id,challenge_id,channel,purpose,target_hash,payload_cipher,status,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                         (identifier(), "challenge:" + challenge_id, user["id"], challenge_id, channel, purpose, target_hash, payload, "queued", at, at))
        return {"challengeId": challenge_id, "expiresInMs": CODE_TTL, "resendAfterMs": RESEND_MS,
                "message": "Код поставлен в очередь отправки." if purpose == "verify_contact" else GENERIC_MESSAGE}

    def contact_request(self, body, user, session, ip):
        if set(body) - {"channel", "value", "password", "totp"}:
            raise APIError(400, "Unexpected contact field.")
        self.require(user, session)
        channel = body.get("channel")
        value = contact_value(channel, body.get("value"))
        self._notification_configured(channel)
        self.auth_rate(ip, user["id"])
        # Failed proof should not spend the recipient's resend allowance.
        with self.store.transaction() as conn:
            self._contact_proof(conn, body, user, session)
        self._notification_start_rate(ip, channel, self._contact_hash(channel, value))
        with self.store.transaction() as conn:
            actor = self.actor_current(conn, user, session)
            current = self._notification_session(conn, session)
            if not self._notification_budget(conn, channel):
                raise APIError(429, "Delivery request limit reached. Try again later.", "rate_limited")
            result = self._new_challenge(conn, current, actor, "verify_contact", channel, value)
            self.store.audit(conn, actor["id"], "contact.challenge", actor["id"], "Requested contact verification", {"channel": channel})
        return result

    def _check_challenge(self, conn, body, session, purpose, user_id=None):
        challenge_id = body.get("challengeId")
        row = conn.execute("SELECT * FROM auth_challenges WHERE id=?", (challenge_id if isinstance(challenge_id, str) and len(challenge_id) <= 128 else "",)).fetchone()
        current = self._notification_session(conn, session)
        invalid = APIError(401, "Invalid or already used code.", "invalid_code")
        if not row or row["purpose"] != purpose or row["session_hash"] != current["token_hash"] or (user_id and row["user_id"] != user_id):
            return None, invalid
        if row["status"] != "active" or row["consumed_at"] or row["attempts"] >= MAX_ATTEMPTS:
            return None, invalid
        if row["expires_at"] <= now_ms():
            conn.execute("UPDATE auth_challenges SET status='expired' WHERE id=?", (row["id"],))
            return None, APIError(410, "This code has expired. Request a new one.", "challenge_expired")
        conn.execute("UPDATE auth_challenges SET attempts=attempts+1 WHERE id=?", (row["id"],))
        code = body.get("code")
        if not isinstance(code, str) or not re.fullmatch(r"[0-9]{6}", code) or not hmac.compare_digest(row["code_hash"], self._notification_hash("code:" + row["id"], code)):
            return None, invalid
        actor = conn.execute("SELECT * FROM users WHERE id=?", (row["user_id"],)).fetchone()
        if not actor or not actor["enabled"] or canonical_role(actor["role"]) is None or actor["auth_epoch"] != row["auth_epoch"]:
            return None, invalid
        if purpose != "verify_contact" and not self._verified_contact(conn, actor["id"], row["channel"], row["target_hash"]):
            return None, invalid
        return (dict(row), dict(actor), current), None

    def _consume_challenge(self, conn, challenge):
        at = now_ms()
        conn.execute("UPDATE auth_challenges SET status='used',consumed_at=? WHERE id=?", (at, challenge["id"]))
        conn.execute("UPDATE notification_outbox SET status='cancelled',payload_cipher=NULL,updated_at=? WHERE challenge_id=? AND status='queued'", (at, challenge["id"]))

    def contact_verify(self, body, user, session, ip):
        if set(body) - {"challengeId", "code"}:
            raise APIError(400, "Unexpected verification field.")
        self.require(user, session)
        self._notification_attempt_rate(ip)
        result, failure = None, None
        with self.store.transaction() as conn:
            actor = self.actor_current(conn, user, session)
            checked, failure = self._check_challenge(conn, body, session, "verify_contact", actor["id"])
            if checked:
                challenge, actor, _ = checked
                channel = challenge["channel"]
                value = self._notification_decrypt(challenge["target_cipher"])
                other = conn.execute("SELECT 1 FROM account_contacts WHERE channel=? AND target_hash=? AND user_id<>?", (channel, challenge["target_hash"], actor["id"])).fetchone()
                if channel == "email":
                    other = other or conn.execute("SELECT 1 FROM users WHERE email=? COLLATE NOCASE AND id<>?", (value, actor["id"])).fetchone()
                self._consume_challenge(conn, challenge)
                if other:
                    failure = APIError(409, "This contact cannot be linked to this account.", "contact_unavailable")
                else:
                    conn.execute("INSERT INTO account_contacts VALUES(?,?,?,?,?) ON CONFLICT(user_id,channel) DO UPDATE SET value_cipher=excluded.value_cipher,target_hash=excluded.target_hash,verified_at=excluded.verified_at",
                                 (actor["id"], channel, self._notification_encrypt(value), challenge["target_hash"], now_ms()))
                    if channel == "email":
                        conn.execute("UPDATE users SET email=?,updated_at=? WHERE id=?", (value, now_ms(), actor["id"]))
                        actor["email"] = value
                    self.store.audit(conn, actor["id"], "contact.verified", actor["id"], "Contact ownership confirmed", {"channel": channel})
                    result = self._contacts_view(conn, actor)
        # Raising inside transaction would roll back the failed-attempt counter.
        if failure:
            raise failure
        return result

    def contact_remove(self, body, user, session, ip):
        if set(body) - {"channel", "password", "totp"} or not isinstance(body.get("channel"), str) or body["channel"] not in CHANNELS:
            raise APIError(400, "Unexpected contact field.")
        self.require(user, session)
        self.auth_rate(ip, user["id"])
        channel = body["channel"]
        with self.store.transaction() as conn:
            actor, _ = self._contact_proof(conn, body, user, session)
            conn.execute("DELETE FROM account_contacts WHERE user_id=? AND channel=?", (actor["id"], channel))
            conn.execute("UPDATE auth_challenges SET status='revoked',consumed_at=? WHERE user_id=? AND channel=? AND status='active'", (now_ms(), actor["id"], channel))
            conn.execute("UPDATE notification_outbox SET status='cancelled',payload_cipher=NULL,updated_at=? WHERE user_id=? AND channel=? AND status='queued'", (now_ms(), actor["id"], channel))
            self.store.audit(conn, actor["id"], "contact.removed", actor["id"], "Removed verified recovery contact", {"channel": channel})
            return self._contacts_view(conn, actor)

    def auth_code_request(self, body, session, ip, purpose="login"):
        if set(body) - {"channel", "contact"} or purpose not in ("login", "recovery"):
            raise APIError(400, "Unexpected sign-in code field.")
        channel = body.get("channel")
        value = contact_value(channel, body.get("contact"))
        self._notification_configured(channel)
        target_hash = self._contact_hash(channel, value)
        self._notification_start_rate(ip, channel, target_hash)
        with self.store.transaction() as conn:
            current = self._notification_session(conn, session)
            if current["user_id"]:
                raise APIError(409, "Sign out before starting another sign-in flow.", "already_authenticated")
            contact = conn.execute("SELECT user_id FROM account_contacts WHERE channel=? AND target_hash=?", (channel, target_hash)).fetchone()
            user = None
            if contact and self._verified_contact(conn, contact["user_id"], channel, target_hash):
                user = conn.execute("SELECT * FROM users WHERE id=?", (contact["user_id"],)).fetchone()
            return self._new_challenge(conn, current, user, purpose, channel, value)

    def _finish_auth_code(self, body, session, ip, purpose):
        allowed = {"challengeId", "code", "totp", "remember"} if purpose == "login" else {"challengeId", "code", "totp", "password"}
        if set(body) - allowed or ("remember" in body and not isinstance(body["remember"], bool)):
            raise APIError(400, "Unexpected code confirmation field.")
        self._notification_attempt_rate(ip)
        replacement = None
        if purpose == "recovery":
            try:
                replacement = password_hash(body.get("password"))
            except ValueError as error:
                raise APIError(400, str(error)) from None
        result, failure = None, None
        with self.store.transaction() as conn:
            checked, failure = self._check_challenge(conn, body, session, purpose)
            if checked:
                challenge, actor, current = checked
                needs_mfa = ROLE_RANK[actor["role"]] > 0 or bool(actor["totp_secret"])
                step = verify_totp(actor["totp_secret"], body.get("totp"), actor["totp_last_step"], now=now_ms()/1000) if needs_mfa else None
                if needs_mfa and step is None:
                    failure = APIError(401, "Invalid login or authenticator code.", "invalid_credentials")
                else:
                    if needs_mfa:
                        conn.execute("UPDATE users SET totp_last_step=? WHERE id=?", (step, actor["id"]))
                    if replacement:
                        conn.execute("UPDATE users SET password_hash=?,auth_epoch=auth_epoch+1,updated_at=? WHERE id=?", (replacement, now_ms(), actor["id"]))
                        conn.execute("UPDATE sessions SET revoked_at=? WHERE user_id=?", (now_ms(), actor["id"]))
                        conn.execute("UPDATE auth_challenges SET status='revoked',consumed_at=? WHERE user_id=? AND id<>? AND status='active'", (now_ms(), actor["id"], challenge["id"]))
                    self._consume_challenge(conn, challenge)
                    actor = conn.execute("SELECT * FROM users WHERE id=?", (actor["id"],)).fetchone()
                    result = self._new_session(conn, actor, current, body.get("remember") is True, needs_mfa)
                    if replacement:
                        self.queue_notice(conn, actor["id"], "password", challenge["id"], {})
                    self.store.audit(conn, actor["id"], "auth.code." + purpose, actor["id"], "Verified contact and MFA" if needs_mfa else "Verified contact code", {"channel": challenge["channel"]})
        if failure:
            raise failure
        return result

    def auth_code_login(self, body, session, ip):
        return self._finish_auth_code(body, session, ip, "login")

    def recovery_confirm(self, body, session, ip):
        return self._finish_auth_code(body, session, ip, "recovery")

    def _valid_notification_job(self, conn, job, payload):
        if job["purpose"] == "notice":
            if job["channel"] != "email" or job["created_at"] < now_ms() - 7*86400000 or not isinstance(payload, dict) or set(payload) != {"target", "kind", "reference", "values"} or not isinstance(payload["target"], str):
                return False
            notice_content(payload["kind"], payload["reference"], payload["values"])
            return hmac.compare_digest(job["target_hash"], self._contact_hash("email", payload["target"])) and bool(self._verified_contact(conn, job["user_id"], "email", job["target_hash"]))
        if not isinstance(payload, dict) or set(payload) != {"target", "code"}:
            return False
        challenge = conn.execute("SELECT * FROM auth_challenges WHERE id=?", (job["challenge_id"],)).fetchone()
        if not challenge or challenge["status"] != "active" or challenge["consumed_at"] or challenge["attempts"] >= MAX_ATTEMPTS or challenge["expires_at"] <= now_ms():
            return False
        user = conn.execute("SELECT * FROM users WHERE id=?", (job["user_id"],)).fetchone()
        if not user or not user["enabled"] or canonical_role(user["role"]) is None or user["auth_epoch"] != challenge["auth_epoch"]:
            return False
        try:
            self._notification_session(conn, {"token_hash": challenge["session_hash"]})
        except APIError:
            return False
        if job["purpose"] != challenge["purpose"] or job["channel"] != challenge["channel"] or job["user_id"] != challenge["user_id"] or job["target_hash"] != challenge["target_hash"]:
            return False
        if not isinstance(payload["target"], str) or not isinstance(payload["code"], str):
            return False
        if not hmac.compare_digest(job["target_hash"], self._contact_hash(job["channel"], payload["target"])) or not hmac.compare_digest(challenge["code_hash"], self._notification_hash("code:" + challenge["id"], payload["code"])):
            return False
        return job["purpose"] == "verify_contact" or bool(self._verified_contact(conn, job["user_id"], job["channel"], job["target_hash"]))


def process_notification_jobs(service, limit=10):
    """Run as a separate worker. Returns counts, never addresses or OTPs."""
    if isinstance(limit, bool) or not isinstance(limit, int) or not 1 <= limit <= 100:
        raise ValueError("Notification worker limit must be between 1 and 100.")
    report = {"accepted": 0, "uncertain": 0, "cancelled": 0}
    with service.store.transaction() as conn:
        # A worker may have died after the provider accepted the message.
        report["uncertain"] += conn.execute("UPDATE notification_outbox SET status='uncertain',payload_cipher=NULL,updated_at=?,error_code='worker_interrupted' WHERE status='sending' AND lease_until<=?", (now_ms(), now_ms())).rowcount
        report["cancelled"] += conn.execute("UPDATE notification_outbox SET status='cancelled',payload_cipher=NULL,updated_at=?,error_code='verification_inactive' WHERE status='queued' AND purpose<>'notice' AND NOT EXISTS (SELECT 1 FROM auth_challenges c WHERE c.id=notification_outbox.challenge_id AND c.status='active' AND c.expires_at>?)", (now_ms(), now_ms())).rowcount
        report["cancelled"] += conn.execute("UPDATE notification_outbox SET status='cancelled',payload_cipher=NULL,updated_at=?,error_code='notice_expired' WHERE status='queued' AND purpose='notice' AND created_at<?", (now_ms(), now_ms()-7*86400000)).rowcount
        conn.execute("DELETE FROM auth_challenges WHERE expires_at<?", (now_ms()-86400000,))
    providers = service.notification_providers()
    for _ in range(limit):
        with service.store.transaction() as conn:
            row = conn.execute("SELECT * FROM notification_outbox WHERE status='queued' AND ((channel='email' AND ?) OR (channel='sms' AND ?)) ORDER BY CASE WHEN purpose='notice' THEN 1 ELSE 0 END,created_at,id LIMIT 1",
                               (int(providers["email"]["enabled"]), int(providers["sms"]["enabled"]))).fetchone()
            if not row:
                break
            job = dict(row)
            try:
                payload = service._notification_decrypt(job["payload_cipher"])
                valid = service._valid_notification_job(conn, job, payload)
            except (InvalidToken, ValueError, TypeError, AttributeError):
                valid = False
            if not valid:
                conn.execute("UPDATE notification_outbox SET status='cancelled',payload_cipher=NULL,updated_at=?,error_code='verification_inactive' WHERE id=?", (now_ms(), job["id"]))
                report["cancelled"] += 1
                continue
            if not service.notification_providers()[job["channel"]]["enabled"]:
                break
            lease_id = identifier()
            conn.execute("UPDATE notification_outbox SET status='sending',lease_until=?,lease_id=?,updated_at=? WHERE id=?", (now_ms()+300000, lease_id, now_ms(), job["id"]))
        try:
            if job["purpose"] == "notice":
                subject, message = notice_content(payload["kind"], payload["reference"], payload["values"])
                outcome = service.notification_gateway.send_notice(payload["target"], subject,
                    message + "\n\nЛичный кабинет: " + service.config.origin + "/#account", verified=True)
            else:
                outcome = service.notification_gateway.send_code(job["channel"], payload["target"], payload["code"],
                    verified=job["purpose"] != "verify_contact", purpose=job["purpose"], contact_challenge=job["purpose"] == "verify_contact")
            if not isinstance(outcome, dict) or outcome.get("status") != "accepted":
                raise DeliveryError("Provider did not confirm acceptance")
            reference = str(outcome.get("provider_id", outcome.get("reference", "")))[:128]
            status, error = "accepted", None
        except Exception:
            # Do not echo provider exceptions, recipients, credentials, or codes.
            status, reference, error = "uncertain", None, "delivery_uncertain"
        with service.store.transaction() as conn:
            conn.execute("UPDATE notification_outbox SET status=?,payload_cipher=NULL,provider_reference=?,error_code=?,updated_at=? WHERE id=? AND lease_id=? AND status IN ('sending','uncertain')",
                         (status, reference, error, now_ms(), job["id"], lease_id))
        report[status] += 1
    return report
