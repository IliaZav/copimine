"""Small cryptographic primitives; secrets never enter public state."""
import base64
import hashlib
import hmac
import re
import secrets
import struct
import time

ROLE_RANK = {"player": 0, "junior_admin": 1, "moderator": 1, "admin": 2, "owner": 3}
PERMISSIONS = {
    "player": set(),
    "junior_admin": {"players.read", "players.moderate", "audit.read"},
    "moderator": {"players.read", "players.moderate", "audit.read"},
    "admin": {"players.read", "players.moderate", "players.manage", "players.fun",
              "inventory.manage", "economy.manage", "catalog.manage", "sessions.manage",
              "map.manage", "analytics.read", "audit.read", "server.manage"},
}
PERMISSIONS["owner"] = PERMISSIONS["admin"] | {"users.manage"}

def canonical_role(value):
    return value if isinstance(value, str) and value in ROLE_RANK else None

def token():
    return secrets.token_urlsafe(32)

def digest(value):
    return hashlib.sha256(value.encode("utf-8")).hexdigest()

def password_hash(password):
    if not isinstance(password, str) or not 12 <= len(password) <= 256 or len(set(password)) < 6:
        raise ValueError("Password must contain 12–256 characters and at least six distinct characters.")
    salt = secrets.token_bytes(16)
    result = hashlib.scrypt(password.encode(), salt=salt, n=32768, r=8, p=1,
                            dklen=32, maxmem=64 * 1024 * 1024)
    return "scrypt$32768$8$1$" + base64.b64encode(salt).decode() + "$" + base64.b64encode(result).decode()

def check_password(password, encoded):
    try:
        name, n, r, p, salt, expected = encoded.split("$")
        if name != "scrypt" or (n, r, p) != ("32768", "8", "1") or not isinstance(password, str) or len(password) > 256:
            return False
        actual = hashlib.scrypt(password.encode(), salt=base64.b64decode(salt, validate=True),
            n=32768, r=8, p=1, dklen=32, maxmem=64 * 1024 * 1024)
        return hmac.compare_digest(actual, base64.b64decode(expected, validate=True))
    except (ValueError, TypeError):
        return False

def new_totp_secret():
    return base64.b32encode(secrets.token_bytes(20)).decode().rstrip("=")

def totp_code(secret, step=None):
    if step is None:
        step = int(time.time()) // 30
    key = base64.b32decode(secret + "=" * ((-len(secret)) % 8), casefold=True)
    data = hmac.new(key, struct.pack(">Q", step), hashlib.sha1).digest()
    offset = data[-1] & 15
    return str((struct.unpack(">I", data[offset:offset + 4])[0] & 0x7fffffff) % 1000000).zfill(6)

def verify_totp(secret, value, last_step=-1, now=None):
    if not secret or not re.fullmatch(r"\d{6}", str(value or "")):
        return None
    step = int(time.time() if now is None else now) // 30
    for candidate in (step, step - 1, step + 1):
        if candidate > (last_step if last_step is not None else -1) and hmac.compare_digest(totp_code(secret, candidate), str(value)):
            return candidate
    return None

def adapter_signature(secret, timestamp, nonce, body):
    return hmac.new(secret.encode(), str(timestamp).encode() + b"\n" + nonce.encode() + b"\n" + body,
                    hashlib.sha256).hexdigest()

