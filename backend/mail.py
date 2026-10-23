"""Expiring verification challenges and a durable SMTP outbox; no public inbox."""
from email.message import EmailMessage
from email.utils import formatdate
import hashlib
import hmac
import json
import re
import secrets
import smtplib
import ssl
import time
import uuid

from fastapi import APIRouter, Request, Response
from .auth import create_user, issue_session
from .security import current_user, fail, hash_password, normalize_email, throttle, valid_name

router = APIRouter(prefix="/api/v7")


def code_digest(settings, identifier, purpose, code):
    return hmac.new(settings.secret_key.encode(), (identifier + ":" + purpose + ":" + code).encode(), "sha256").hexdigest()


def request_challenge(request, purpose, data):
    settings, database = request.app.state.settings, request.app.state.database
    if not settings.smtp_host or not settings.mail_domain_ready:
        fail(503, "mail_not_configured", "Почта ещё не подключена. Войдите с паролем или обратитесь к владельцу проекта.")
    if purpose not in {"register", "login", "reset", "link_email"}:
        fail(400, "invalid_purpose", "Неизвестная операция подтверждения.")
    email = normalize_email(data.get("email"))
    ip = request.client.host if request.client else "unknown"
    throttle(database, "mail-ip:" + ip, 20, 3600)
    throttle(database, "mail-address:" + email, 3, 600)
    user_id, payload, deliver = None, {}, True
    with database.transaction() as db:
        row = db.execute("SELECT * FROM cm_site_users WHERE email_norm=%s", (email,)).fetchone()
    if purpose == "register":
        name = str(data.get("username", data.get("name", ""))).strip()
        if not valid_name(name):
            fail(400, "invalid_name", "Логин: 3–32 латинских буквы, цифры или символ подчёркивания.")
        payload = {"name": name, "password_hash": hash_password(data.get("password", ""))}
        with database.transaction() as db:
            if row or db.execute("SELECT id FROM cm_site_users WHERE name_norm=%s", (name.lower(),)).fetchone():
                fail(409, "account_exists", "Аккаунт с такими данными уже существует.")
    elif purpose == "link_email":
        user = current_user(request, fresh=True)
        user_id = user["id"]
        if row and row["id"] != user_id:
            fail(409, "account_exists", "Этот адрес уже используется другим аккаунтом.")
    else:
        deliver = bool(row and row["enabled"] and row["email_verified"])
        user_id = row["id"] if deliver else None
        if purpose == "reset":
            payload["password_hash"] = hash_password(data.get("password", ""))
    identifier, code, now = uuid.uuid4().hex, str(secrets.randbelow(1000000)).zfill(6), int(time.time())
    with database.transaction() as db:
        db.execute("UPDATE cm_site_challenges SET consumed_s=%s WHERE email=%s AND purpose=%s AND consumed_s=0", (now, email, purpose))
        db.execute("INSERT INTO cm_site_challenges(id,purpose,email,user_id,code_hash,payload,created_s,expires_s) VALUES(%s,%s,%s,%s,%s,%s,%s,%s)", (identifier, purpose, email, user_id, code_digest(settings, identifier, purpose, code), json.dumps(payload), now, now + 600))
        if deliver:
            body = f"Ваш код CopiMine: {code}\n\nКод действует 10 минут и подходит только для запрошенной операции.\nЕсли вы не запрашивали код, проигнорируйте письмо.\nНикому не сообщайте этот код.\n"
            db.execute("INSERT INTO cm_site_mail_outbox(id,recipient,subject,body,created_s,expires_s) VALUES(%s,%s,%s,%s,%s,%s)", (identifier, email, "Код подтверждения CopiMine", body, now, now + 600))
    return {"ok": True, "challenge": identifier, "email": email, "expiresAt": now + 600, "queued": True, "message": "Если адрес подходит для этой операции, письмо будет отправлено. Проверьте также папку «Спам»."}


@router.post("/request-code")
async def request_code(request: Request):
    data = await request.json()
    return request_challenge(request, data.get("purpose"), data)


@router.post("/confirm-code")
async def confirm_code(request: Request, response: Response):
    data = await request.json()
    identifier, code = str(data.get("challenge", "")), str(data.get("code", ""))
    if not re.fullmatch(r"[a-f0-9]{32}", identifier) or not re.fullmatch(r"[0-9]{6}", code):
        fail(400, "invalid_code", "Код неверный или истёк.")
    settings, database, now = request.app.state.settings, request.app.state.database, int(time.time())
    accepted, challenge = False, None
    with database.transaction() as db:
        row = db.execute("SELECT * FROM cm_site_challenges WHERE id=%s FOR UPDATE", (identifier,)).fetchone()
        if row and row["consumed_s"] == 0 and row["expires_s"] > now and row["attempts"] < 5:
            challenge = dict(row)
            accepted = hmac.compare_digest(row["code_hash"], code_digest(settings, identifier, row["purpose"], code))
            if row["purpose"] == "link_email" and (not request.state.user or request.state.user["id"] != row["user_id"]):
                accepted = False
            db.execute("UPDATE cm_site_challenges SET attempts=attempts+1,consumed_s=%s WHERE id=%s", (now if accepted else 0, identifier))
    if not accepted:
        fail(400, "invalid_code", "Код неверный или истёк. Запросите новый код при необходимости.")
    payload, purpose = json.loads(challenge["payload"]), challenge["purpose"]
    if purpose == "register":
        user = create_user(database, payload["name"], None, email=challenge["email"], email_verified=True, encoded=payload["password_hash"])
    else:
        with database.transaction() as db:
            row = db.execute("SELECT * FROM cm_site_users WHERE id=%s AND enabled=1", (challenge["user_id"],)).fetchone()
        if not row:
            fail(400, "invalid_code", "Подтверждение недоступно. Проверьте данные и повторите запрос.")
        user = dict(row)
        if user["totp_secret"]:
            from .mfa import verify_factor
            if not verify_factor(database, settings.secret_key, user, data.get("totp", "")):
                fail(401, "mfa_required", "Для входа также нужен код двухфакторной защиты. Запросите новый почтовый код и укажите оба кода.")
        with database.transaction() as db:
            if purpose == "reset":
                db.execute("UPDATE cm_site_users SET password_hash=%s,updated_s=%s WHERE id=%s", (payload["password_hash"], now, user["id"]))
                db.execute("UPDATE cm_site_sessions SET revoked_s=%s WHERE user_id=%s", (now, user["id"]))
            elif purpose == "link_email":
                db.execute("UPDATE cm_site_users SET email=%s,email_norm=%s,email_verified=1,updated_s=%s WHERE id=%s", (challenge["email"], challenge["email"], now, user["id"]))
    with database.transaction() as db:
        user = dict(db.execute("SELECT * FROM cm_site_users WHERE id=%s", (user["id"],)).fetchone())
    database.audit(user, "email." + purpose, user["id"])
    result = issue_session(request, response, user)
    from .domain import review_user
    return {**result, "user": review_user(user)}


def pump_outbox(database, settings, limit=20):
    if not settings.smtp_host or not settings.mail_domain_ready:
        return {"sent": 0, "ready": False}
    sent = 0
    for _ in range(limit):
        now = int(time.time())
        with database.transaction() as db:
            lock = "" if database.sqlite else " FOR UPDATE SKIP LOCKED"
            row = db.execute("SELECT * FROM cm_site_mail_outbox WHERE sent_s=0 AND attempts<5 AND expires_s>%s AND next_attempt_s<=%s ORDER BY created_s LIMIT 1" + lock, (now, now)).fetchone()
            if not row:
                break
            message = dict(row)
            db.execute("UPDATE cm_site_mail_outbox SET attempts=attempts+1,next_attempt_s=%s WHERE id=%s", (now + 120, message["id"]))
        try:
            value = EmailMessage()
            value["From"], value["To"], value["Subject"] = settings.smtp_from, message["recipient"], message["subject"]
            value["Date"], value["Message-ID"] = formatdate(localtime=False), f"<{message['id']}@copimine.ru>"
            value.set_content(message["body"])
            with smtplib.SMTP(settings.smtp_host, settings.smtp_port, timeout=10) as smtp:
                smtp.ehlo()
                if settings.smtp_starttls:
                    smtp.starttls(context=ssl.create_default_context()); smtp.ehlo()
                elif settings.smtp_host not in {"127.0.0.1", "localhost", "::1"}:
                    raise RuntimeError("Remote SMTP requires STARTTLS")
                if settings.smtp_username:
                    smtp.login(settings.smtp_username, settings.smtp_password)
                smtp.send_message(value)
            with database.transaction() as db:
                db.execute("UPDATE cm_site_mail_outbox SET sent_s=%s,body='',last_error='' WHERE id=%s", (int(time.time()), message["id"]))
            sent += 1
        except (OSError, smtplib.SMTPException, RuntimeError) as error:
            with database.transaction() as db:
                db.execute("UPDATE cm_site_mail_outbox SET last_error=%s,next_attempt_s=%s WHERE id=%s", (type(error).__name__, int(time.time()) + min(300, 15 * 2 ** message["attempts"]), message["id"]))
    return {"sent": sent, "ready": True}


if __name__ == "__main__":
    from .config import Settings
    from .db import Database
    settings = Settings.from_env()
    print(json.dumps(pump_outbox(Database(settings.database_url), settings)))
