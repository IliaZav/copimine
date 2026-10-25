import base64
import hashlib
import hmac
import json
import re
import secrets
import time

from fastapi import HTTPException

COOKIE = "cm_site_session"
ANON_COOKIE = "cm_site_anon"
RANK = {"player": 0, "helper": 10, "junior_admin": 30, "moderator": 40, "admin": 70, "owner": 100}
PERMISSIONS = {
    "player": [],
    "helper": ["players.view", "tickets.manage"],
    "junior_admin": ["players.view", "players.moderate", "tickets.manage"],
    "moderator": ["players.view", "players.moderate", "inventory.view", "tickets.manage"],
    "admin": ["players.view", "players.moderate", "inventory.view", "inventory.manage", "tickets.manage", "catalog.manage", "content.manage", "logs.view", "analytics.read"],
}
PERMISSIONS["admin"] += ["map.view", "map.manage"]
PERMISSIONS["owner"] = PERMISSIONS["admin"] + ["server.manage", "accounts.manage", "recovery.manage", "economy.manage", "settings.manage"]


def fail(status, code, message):
    raise HTTPException(status_code=status, detail={"code": code, "message": message})


def digest(value):
    return hashlib.sha256(value.encode()).hexdigest()


def hash_password(password):
    if not isinstance(password, str) or not 10 <= len(password) <= 256:
        fail(400, "password_policy", "Пароль должен содержать от 10 до 256 символов.")
    salt = secrets.token_bytes(16)
    value = hashlib.scrypt(password.encode(), salt=salt, n=32768, r=8, p=1, maxmem=64 * 1024 * 1024)
    return "scrypt$" + base64.b64encode(salt).decode() + "$" + base64.b64encode(value).decode()


def verify_password(password, encoded):
    try:
        if not isinstance(password, str) or len(password) > 256:
            return False
        method, salt, expected = encoded.split("$")
        if method != "scrypt":
            return False
        actual = hashlib.scrypt(password.encode(), salt=base64.b64decode(salt), n=32768, r=8, p=1, maxmem=64 * 1024 * 1024)
        return hmac.compare_digest(actual, base64.b64decode(expected))
    except (ValueError, TypeError):
        return False


def signed_anonymous(secret):
    value = str(int(time.time())) + "." + secrets.token_urlsafe(24)
    return value + "." + hmac.new(secret.encode(), value.encode(), "sha256").hexdigest()


def valid_anonymous(value, secret):
    try:
        timestamp, nonce, signature = value.split(".")
        age = time.time() - int(timestamp)
        expected = hmac.new(secret.encode(), (timestamp + "." + nonce).encode(), "sha256").hexdigest()
        return 0 <= age < 86400 and len(nonce) >= 20 and hmac.compare_digest(signature, expected)
    except (AttributeError, ValueError):
        return False


def anonymous_csrf(value, secret):
    return hmac.new(secret.encode(), ("csrf:" + value).encode(), "sha256").hexdigest()


def public_user(row):
    return {
        "id": row["id"], "name": row["name"], "username": row["name"],
        "displayName": row["display_name"], "email": row["email"],
        "emailVerified": bool(row["email_verified"]), "role": row["role"],
        "permissions": PERMISSIONS.get(row["role"], []), "enabled": bool(row["enabled"]),
        "status": "active" if row["enabled"] else "banned", "createdAt": row["created_s"] * 1000,
        "gameUUID": row["minecraft_uuid"], "minecraftUuid": row["minecraft_uuid"],
        "minecraftName": row["minecraft_name"], "gameLinkVerified": bool(row["minecraft_uuid"]),
        "totpEnabled": bool(row["totp_secret"]), "mfaEnabled": bool(row["totp_secret"]),
        "preferences": json.loads(row["preferences"]),
    }


def current_user(request, permission=None, fresh=False):
    user = request.state.user
    if not user:
        fail(401, "unauthenticated", "Войдите в аккаунт, чтобы продолжить.")
    if permission and permission not in PERMISSIONS.get(user["role"], []):
        fail(403, "forbidden", "Для этого действия недостаточно прав.")
    if fresh and int(time.time()) - request.state.session["reauth_s"] > 600:
        fail(403, "reauth_required", "Подтвердите пароль перед этим действием.")
    return user


def check_hierarchy(actor, target):
    if actor["id"] == target["id"] or RANK.get(actor["role"], 0) <= RANK.get(target["role"], 0):
        fail(403, "hierarchy", "Нельзя изменять собственный аккаунт или аккаунт с равными и более высокими правами.")


def valid_name(value):
    return isinstance(value, str) and re.fullmatch(r"[A-Za-z0-9_]{3,32}", value) is not None


def normalize_email(value):
    value = str(value or "").strip()
    if len(value) > 254 or not re.fullmatch(r"[^\s@\r\n]+@[^\s@\r\n]+\.[^\s@\r\n]+", value):
        fail(400, "invalid_email", "Укажите действующий адрес почты.")
    return value.lower()


def throttle(database, key, limit=5, seconds=900):
    now = int(time.time())
    with database.transaction() as db:
        db.execute("INSERT INTO cm_site_attempts(key,times) VALUES(%s,'[]') ON CONFLICT(key) DO NOTHING", (key,))
        row = db.execute("SELECT times FROM cm_site_attempts WHERE key=%s FOR UPDATE", (key,)).fetchone()
        times = [t for t in json.loads(row["times"]) if t > now - seconds]
        if len(times) >= limit:
            fail(429, "rate_limited", "Слишком много попыток. Подождите несколько минут.")
        times.append(now)
        db.execute("UPDATE cm_site_attempts SET times=%s WHERE key=%s", (json.dumps(times), key))
