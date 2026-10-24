import json
import secrets
import time
import uuid

from fastapi import APIRouter, Request, Response

from .security import COOKIE, current_user, digest, fail, hash_password, public_user, throttle, valid_name, verify_password

router = APIRouter(prefix="/api/v6")


def create_user(database, name, password, email="", role="player", email_verified=False, encoded=None, display_name=None):
    if not valid_name(name):
        fail(400, "invalid_name", "Логин: от 3 до 32 латинских букв, цифр или символов подчёркивания.")
    display_name = str(display_name or name).strip()
    if not 2 <= len(display_name) <= 32 or any(ord(char) < 32 for char in display_name):
        fail(400, "invalid_display_name", "Ник профиля должен содержать от 2 до 32 символов.")
    encoded = encoded or hash_password(password)
    now = int(time.time())
    user_id = uuid.uuid4().hex
    with database.transaction() as db:
        if db.execute("SELECT id FROM cm_site_users WHERE name_norm=%s OR (email_norm IS NOT NULL AND email_norm=%s)", (name.lower(), email.lower() or None)).fetchone():
            fail(409, "account_exists", "Аккаунт с такими данными уже существует.")
        db.execute("INSERT INTO cm_site_users(id,name,name_norm,display_name,email,email_norm,password_hash,role,email_verified,created_s,updated_s) VALUES(%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)", (user_id, name, name.lower(), display_name, email, email.lower() or None, encoded, role, int(email_verified), now, now))
        return dict(db.execute("SELECT * FROM cm_site_users WHERE id=%s", (user_id,)).fetchone())


def issue_session(request, response, user, remember=False):
    now = int(time.time())
    ttl = 30 * 86400 if remember else request.app.state.settings.session_hours * 3600
    token, csrf = secrets.token_urlsafe(32), secrets.token_urlsafe(32)
    with request.app.state.database.transaction() as db:
        db.execute("INSERT INTO cm_site_sessions(token_hash,user_id,csrf,created_s,expires_s,reauth_s,ip,user_agent) VALUES(%s,%s,%s,%s,%s,%s,%s,%s)", (digest(token), user["id"], csrf, now, now + ttl, now, request.client.host if request.client else "", request.headers.get("user-agent", "")[:300]))
        db.execute("UPDATE cm_site_users SET last_login_s=%s WHERE id=%s", (now, user["id"]))
    response.set_cookie(COOKIE, token, max_age=ttl if remember else None, httponly=True, secure=request.app.state.settings.secure_cookies, samesite="lax", path="/")
    return {"ok": True, "user": public_user(user), "csrf": csrf}


@router.get("/session")
def session(request: Request):
    return {"user": public_user(request.state.user) if request.state.user else None, "csrf": request.state.csrf, "production": True}


@router.post("/auth/login")
async def login(request: Request, response: Response):
    data = await request.json()
    name = str(data.get("name", data.get("username", ""))).strip().lower()
    database = request.app.state.database
    ip = request.client.host if request.client else "unknown"
    throttle(database, "login-ip:" + ip, 40)
    throttle(database, "login-name:" + ip + ":" + name, 5)
    with database.transaction() as db:
        row = db.execute("SELECT * FROM cm_site_users WHERE name_norm=%s", (name,)).fetchone()
    if not row or not verify_password(data.get("password", ""), row["password_hash"]):
        fail(401, "invalid_credentials", "Неверный логин или пароль.")
    user = dict(row)
    if not user["enabled"] or (user["blocked_until_s"] and user["blocked_until_s"] > time.time()):
        fail(403, "account_blocked", "Вход в этот аккаунт ограничен. Обратитесь в поддержку.")
    if user["totp_secret"]:
        from .mfa import verify_factor
        if not verify_factor(database, request.app.state.settings.secret_key, user, data.get("totp", "")):
            fail(401, "invalid_credentials", "Укажите действующий код двухфакторной защиты.")
    if request.state.session:
        with database.transaction() as db:
            db.execute("UPDATE cm_site_sessions SET revoked_s=%s WHERE token_hash=%s", (int(time.time()), request.state.session["token_hash"]))
    database.audit(user, "auth.login")
    return issue_session(request, response, user, bool(data.get("remember", data.get("remember_me", False))))


@router.post("/auth/logout")
def logout(request: Request, response: Response):
    if request.state.session:
        with request.app.state.database.transaction() as db:
            db.execute("UPDATE cm_site_sessions SET revoked_s=%s WHERE token_hash=%s", (int(time.time()), request.state.session["token_hash"]))
    response.delete_cookie(COOKIE, path="/", secure=request.app.state.settings.secure_cookies, httponly=True, samesite="lax")
    return {"ok": True}


@router.post("/auth/reauth")
async def reauthenticate(request: Request):
    user = current_user(request)
    data = await request.json()
    throttle(request.app.state.database, "reauth:" + user["id"], 5)
    if not verify_password(data.get("password", ""), user["password_hash"]):
        fail(401, "invalid_credentials", "Неверный пароль.")
    if user["totp_secret"]:
        from .mfa import verify_factor
        if not verify_factor(request.app.state.database, request.app.state.settings.secret_key, user, data.get("totp", "")):
            fail(401, "invalid_credentials", "Неверный код двухфакторной защиты.")
    with request.app.state.database.transaction() as db:
        db.execute("UPDATE cm_site_sessions SET reauth_s=%s WHERE token_hash=%s", (int(time.time()), request.state.session["token_hash"]))
    return {"ok": True}


@router.post("/auth/register")
async def register(request: Request, response: Response):
    data = await request.json()
    database = request.app.state.database
    ip = request.client.host if request.client else "unknown"
    name = str(data.get("name", data.get("username", ""))).strip()
    throttle(database, "register-ip:" + ip, 8, 3600)
    throttle(database, "register-name:" + ip + ":" + name.lower(), 3, 3600)
    if data.get("terms") is False:
        fail(400, "terms_required", "Для регистрации нужно принять правила проекта.")
    user = create_user(database, name, data.get("password", ""), display_name=data.get("displayName"))
    database.audit(user, "auth.register")
    return issue_session(request, response, user, bool(data.get("remember", False)))
