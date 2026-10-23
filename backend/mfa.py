import base64
import hashlib
import hmac
import io
import re
import secrets
import struct
import time
import uuid
from urllib.parse import quote

from fastapi import APIRouter, Request
from .security import current_user, digest, fail, throttle, verify_password

router = APIRouter(prefix="/api/v6/auth/totp")


def cipher(secret):
    from cryptography.fernet import Fernet
    return Fernet(base64.urlsafe_b64encode(hmac.new(secret.encode(), b"copimine-totp-encryption", "sha256").digest()))


def totp(secret, now=None):
    counter = int((time.time() if now is None else now) // 30)
    value = hmac.new(base64.b32decode(secret + "=" * (-len(secret) % 8)), struct.pack(">Q", counter), "sha1").digest()
    offset = value[-1] & 15
    return str((struct.unpack(">I", value[offset:offset + 4])[0] & 0x7FFFFFFF) % 1000000).zfill(6)


def verify_totp(key, encoded, code):
    if not re.fullmatch(r"[0-9]{6}", str(code)):
        return False
    try:
        secret = cipher(key).decrypt(encoded.encode()).decode()
        return any(hmac.compare_digest(str(code), totp(secret, time.time() + offset)) for offset in (-30, 0, 30))
    except (ValueError, TypeError, Exception):
        return False


def verify_factor(database, key, user, code):
    if verify_totp(key, user["totp_secret"], code):
        return True
    hashed = hmac.new(key.encode(), (user["id"] + ":backup:" + str(code).strip().lower()).encode(), "sha256").hexdigest()
    with database.transaction() as db:
        row = db.execute("SELECT * FROM cm_site_backup_codes WHERE user_id=%s AND code_hash=%s AND consumed_s=0 FOR UPDATE", (user["id"], hashed)).fetchone()
        if row:
            db.execute("UPDATE cm_site_backup_codes SET consumed_s=%s WHERE id=%s", (int(time.time()), row["id"]))
            return True
    return False


@router.post("/setup")
async def setup(request: Request):
    user = current_user(request, fresh=True)
    data = await request.json()
    throttle(request.app.state.database, "totp-setup:" + user["id"], 5)
    if user["totp_secret"]:
        fail(409, "mfa_enabled", "Двухфакторная защита уже включена.")
    if not verify_password(data.get("password", ""), user["password_hash"]):
        fail(401, "invalid_credentials", "Неверный пароль.")
    secret = base64.b32encode(secrets.token_bytes(20)).decode().rstrip("=")
    encrypted = cipher(request.app.state.settings.secret_key).encrypt(secret.encode()).decode()
    with request.app.state.database.transaction() as db:
        db.execute("UPDATE cm_site_users SET pending_totp=%s WHERE id=%s", (encrypted, user["id"]))
    uri = "otpauth://totp/" + quote("CopiMine:" + user["name"]) + "?secret=" + secret + "&issuer=CopiMine&algorithm=SHA1&digits=6&period=30"
    import qrcode
    image = qrcode.make(uri)
    buffer = io.BytesIO(); image.save(buffer, format="PNG")
    return {"ok": True, "secret": secret, "uri": uri, "qr": "data:image/png;base64," + base64.b64encode(buffer.getvalue()).decode()}


@router.post("/confirm")
async def confirm(request: Request):
    user = current_user(request, fresh=True)
    data = await request.json()
    throttle(request.app.state.database, "totp-confirm:" + user["id"], 6)
    if not user["pending_totp"] or not verify_totp(request.app.state.settings.secret_key, user["pending_totp"], data.get("totp", "")):
        fail(400, "invalid_code", "Проверьте код приложения и время на устройстве.")
    backup_codes = [secrets.token_hex(4) + "-" + secrets.token_hex(4) for _ in range(8)]
    with request.app.state.database.transaction() as db:
        db.execute("UPDATE cm_site_users SET totp_secret=pending_totp,pending_totp='' WHERE id=%s", (user["id"],))
        db.execute("DELETE FROM cm_site_backup_codes WHERE user_id=%s", (user["id"],))
        for code in backup_codes:
            hashed = hmac.new(request.app.state.settings.secret_key.encode(), (user["id"] + ":backup:" + code).encode(), "sha256").hexdigest()
            db.execute("INSERT INTO cm_site_backup_codes(id,user_id,code_hash) VALUES(%s,%s,%s)", (uuid.uuid4().hex, user["id"], hashed))
    request.app.state.database.audit(user, "mfa.enabled", user["id"])
    return {"ok": True, "backupCodes": backup_codes}


@router.post("/disable")
async def disable(request: Request):
    user = current_user(request, fresh=True)
    data = await request.json()
    throttle(request.app.state.database, "totp-disable:" + user["id"], 5)
    if not verify_password(data.get("password", ""), user["password_hash"]) or not verify_factor(request.app.state.database, request.app.state.settings.secret_key, user, data.get("totp", "")):
        fail(401, "invalid_credentials", "Проверьте пароль и код двухфакторной защиты.")
    with request.app.state.database.transaction() as db:
        db.execute("UPDATE cm_site_users SET totp_secret='',pending_totp='' WHERE id=%s", (user["id"],))
        db.execute("DELETE FROM cm_site_backup_codes WHERE user_id=%s", (user["id"],))
        db.execute("UPDATE cm_site_sessions SET revoked_s=%s WHERE user_id=%s AND token_hash<>%s", (int(time.time()), user["id"], request.state.session["token_hash"]))
    request.app.state.database.audit(user, "mfa.disabled", user["id"])
    return {"ok": True}
