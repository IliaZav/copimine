import hashlib
import json
import re
import secrets
import time
import uuid

from fastapi import APIRouter, Request, Response
from starlette.concurrency import run_in_threadpool

from .auth import create_user, issue_session, login, logout
from .security import PERMISSIONS, RANK, check_hierarchy, current_user, fail, hash_password, public_user, throttle, verify_password

router = APIRouter()
SUPPORTED_ACTIONS = {"kick", "ban", "unban", "heal", "feed", "kill", "gamemode", "teleport", "freeze", "unfreeze", "inventory.snapshot", "inventory.remove", "whitelist.add", "whitelist.remove", "time.day", "time.night", "weather.clear", "weather.rain", "save"}


def review_user(row):
    value = public_user(row)
    return {**value, "username": row["name"], "display_name": row["display_name"], "email_verified": bool(row["email_verified"]), "vk_id": row["vk_id"], "blocked": not bool(row["enabled"]), "block_reason": row["blocked_reason"], "email_hint_dismissed": True}


def safe_reason(value):
    reason = str(value or "").strip()
    if not 3 <= len(reason) <= 500 or any(c in reason for c in "\r\n\x00"):
        fail(400, "reason_required", "Укажите причину от 3 до 500 символов, одной строкой.")
    return reason


def target_user(database, identifier):
    with database.transaction() as db:
        row = db.execute("SELECT * FROM cm_site_users WHERE id=%s OR name_norm=%s", (identifier, str(identifier).lower())).fetchone()
    if not row:
        fail(404, "not_found", "Аккаунт не найден.")
    return dict(row)


def content(request):
    database, settings = request.app.state.database, request.app.state.settings
    seed = json.loads((settings.frontend / "data" / "content-v6-public.json").read_text(encoding="utf-8"))
    if not isinstance(seed, dict):
        seed = {}
    return {key: database.setting(key, seed.get(key, [])) for key in ("pages", "gallery", "faq")}


def build_state(request):
    database, game = request.app.state.database, request.app.state.game
    info = game.health()
    config = {
        "serverName": "CopiMine", "serverIp": "copimine.ru", "serverAddress": "copimine.ru",
        "serverVersion": "26.3", "online": info["online"], "playersOnline": info["players"],
        "maxPlayers": info["maxPlayers"], "transferFeePercent": 0, "production": True,
        "maintenance": database.setting("maintenance", {"enabled": False}),
    }
    config.update(database.setting("public_config", {}))
    result = {
        "players": [], "catalog": game.catalog(), "orders": [], "payments": [], "ledger": [],
        "tickets": [], "audit": [], "sanctions": [], "playerSnapshots": [], "playerActions": [],
        "purchaseLimits": [], "contactIdentities": [], "siteSessions": [], "eventLog": [],
        "config": config, "game": info, **content(request),
    }
    if not request.state.user:
        return result
    user = request.state.user
    permissions = PERMISSIONS.get(user["role"], [])
    with database.transaction() as db:
        rows = db.execute("SELECT * FROM cm_site_users ORDER BY created_s DESC LIMIT 500").fetchall() if "players.view" in permissions else [user]
        result["players"] = [{**public_user(dict(row)), "AR": None, "CM": None, "online": False, "purchaseLimits": []} for row in rows]
        if "logs.view" in permissions:
            result["audit"] = [{"id": r["id"], "actor": r["actor_name"], "action": r["action"], "target": r["target"], "reason": r["reason"], "at": r["created_s"] * 1000} for r in db.execute("SELECT * FROM cm_site_audit ORDER BY created_s DESC LIMIT 300").fetchall()]
        ticket_rows = db.execute("SELECT * FROM cm_site_tickets ORDER BY updated_s DESC LIMIT 200").fetchall() if "tickets.manage" in permissions else db.execute("SELECT * FROM cm_site_tickets WHERE user_id=%s ORDER BY updated_s DESC LIMIT 100", (user["id"],)).fetchall()
        result["tickets"] = [{**dict(row), "playerId": row["user_id"], "at": row["created_s"] * 1000, "createdAt": row["created_s"] * 1000} for row in ticket_rows]
        result["siteSessions"] = [{"id": row["token_hash"][:20], "playerId": row["user_id"], "createdAt": row["created_s"] * 1000, "expiresAt": row["expires_s"] * 1000, "device": row["user_agent"], "current": row["token_hash"] == request.state.session["token_hash"]} for row in db.execute("SELECT * FROM cm_site_sessions WHERE user_id=%s AND revoked_s=0 AND expires_s>%s ORDER BY created_s DESC", (user["id"], int(time.time()))).fetchall()]
        receipts = db.execute("SELECT * FROM cm_site_receipts WHERE actor_id=%s AND status='COMMITTED' ORDER BY created_s DESC LIMIT 100", (user["id"],)).fetchall()
        for row in receipts:
            saved = json.loads(row["result"])
            if row["operation"] == "checkout":
                result["orders"].extend(saved.get("orders", []))
    if info.get("adapter") and "players.view" in permissions:
        existing = {p.get("minecraftUuid") for p in result["players"] if p.get("minecraftUuid")}
        for player in info.get("onlinePlayers", []):
            match = next((p for p in result["players"] if p.get("minecraftUuid") == player["uuid"]), None)
            if match:
                match["online"] = True
            elif player["uuid"] not in existing:
                result["players"].append({"id": player["uuid"], "name": player["name"], "displayName": player["name"], "role": "player", "enabled": True, "status": "active", "gameUUID": player["uuid"], "minecraftUuid": player["uuid"], "minecraftName": player["name"], "gameLinkVerified": False, "online": True, "AR": None, "CM": None, "purchaseLimits": [], "gameOnly": True})
    return result


@router.get("/api/v7/bootstrap")
def bootstrap(request: Request):
    user = request.state.user
    return {"csrf": request.state.csrf, "user": review_user(user) if user else None, "workspace": build_state(request), "personal": {"preferences": json.loads(user["preferences"]) if user else {}}, "maintenance": request.app.state.database.setting("maintenance", {"enabled": False}), "production": True}


@router.post("/api/v7/login")
async def review_login(request: Request, response: Response):
    result = await login(request, response)
    user = target_user(request.app.state.database, result["user"]["id"])
    return {**result, "user": review_user(user)}


@router.post("/api/v7/logout")
def review_logout(request: Request, response: Response):
    return logout(request, response)


@router.post("/api/v7/personal")
async def personal(request: Request):
    user = current_user(request)
    data = await request.json()
    allowed = {"theme", "animations", "reducedMotion", "notifyTransfers", "cookies", "rememberCart"}
    preferences = {key: value for key, value in data.get("preferences", {}).items() if key in allowed and isinstance(value, (str, bool, dict))}
    if len(json.dumps(preferences)) > 4096:
        fail(400, "invalid_preferences", "Настройки слишком большие.")
    with request.app.state.database.transaction() as db:
        db.execute("UPDATE cm_site_users SET preferences=%s,updated_s=%s WHERE id=%s", (json.dumps(preferences), int(time.time()), user["id"]))
    return {"ok": True}


@router.post("/api/v7/workspace")
async def workspace(request: Request):
    current_user(request, "content.manage")
    data = await request.json()
    if any(key not in {"pages", "gallery", "faq"} for key in data):
        fail(400, "authoritative_state", "Игроки, права, деньги и инвентари изменяются только отдельными защищёнными операциями.")
    return await save_content(request, data)


@router.post("/api/v7/profile")
@router.patch("/api/v6/account/profile")
async def profile(request: Request):
    user = current_user(request)
    data = await request.json()
    name = str(data.get("displayName", user["display_name"])).strip()
    if not 1 <= len(name) <= 32:
        fail(400, "invalid_profile", "Отображаемое имя: от 1 до 32 символов.")
    if "email" in data and str(data["email"]).lower() != user["email"].lower():
        fail(400, "email_verification_required", "Измените почту через подтверждение адреса в разделе «Способы входа».")
    with request.app.state.database.transaction() as db:
        db.execute("UPDATE cm_site_users SET display_name=%s,updated_s=%s WHERE id=%s", (name, int(time.time()), user["id"]))
    return {"ok": True, "user": review_user(target_user(request.app.state.database, user["id"]))}


@router.post("/api/v7/password")
@router.post("/api/v6/account/password")
async def password(request: Request):
    user = current_user(request)
    data = await request.json()
    throttle(request.app.state.database, "password:" + user["id"], 5)
    if not verify_password(data.get("current", data.get("currentPassword", "")), user["password_hash"]):
        fail(401, "invalid_credentials", "Текущий пароль указан неверно.")
    encoded = hash_password(data.get("password", ""))
    now = int(time.time())
    with request.app.state.database.transaction() as db:
        db.execute("UPDATE cm_site_users SET password_hash=%s,updated_s=%s WHERE id=%s", (encoded, now, user["id"]))
        db.execute("UPDATE cm_site_sessions SET revoked_s=%s WHERE user_id=%s AND token_hash<>%s", (now, user["id"], request.state.session["token_hash"]))
    request.app.state.database.audit(user, "password.changed", user["name"])
    return {"ok": True, "user": review_user(user)}


@router.post("/api/v7/dismiss-hint")
def dismiss_hint(request: Request):
    current_user(request)
    return {"ok": True}


@router.post("/api/v7/unlink")
async def unlink(request: Request):
    user = current_user(request, fresh=True)
    data = await request.json()
    if data.get("kind") not in {"email", "vk"}:
        fail(400, "invalid_connection", "Неизвестный способ входа.")
    if data["kind"] == "email":
        fail(409, "recovery_contact_required", "Сначала подтвердите новый адрес почты. Контакт для восстановления должен сохраняться.")
    with request.app.state.database.transaction() as db:
        db.execute("UPDATE cm_site_users SET vk_id=NULL WHERE id=%s", (user["id"],))
    request.app.state.database.audit(user, "vk.unlinked", user["name"])
    return {"ok": True, "user": review_user(target_user(request.app.state.database, user["id"]))}


@router.post("/api/v6/tickets")
async def create_ticket(request: Request):
    user = current_user(request)
    throttle(request.app.state.database, "ticket:" + user["id"], 10, 3600)
    data = await request.json()
    title, body = str(data.get("title", "")).strip(), str(data.get("body", "")).strip()
    if not 3 <= len(title) <= 160 or not 5 <= len(body) <= 6000:
        fail(400, "invalid_ticket", "Заполните тему и описание обращения.")
    ticket = {"id": uuid.uuid4().hex, "title": title, "body": body, "type": str(data.get("type", "support"))[:32]}
    now = int(time.time())
    with request.app.state.database.transaction() as db:
        db.execute("INSERT INTO cm_site_tickets(id,user_id,title,body,type,status,created_s,updated_s) VALUES(%s,%s,%s,%s,%s,'open',%s,%s)", (ticket["id"], user["id"], title, body, ticket["type"], now, now))
    request.app.state.database.audit(user, "ticket.created", ticket["id"])
    return {"ok": True, "ticket": ticket}


@router.patch("/api/v6/admin/tickets/{identifier}")
async def reply_ticket(identifier: str, request: Request):
    user = current_user(request, "tickets.manage")
    data = await request.json()
    reply, status = str(data.get("reply", "")).strip(), data.get("status", "open")
    if len(reply) > 6000 or status not in {"open", "closed", "in_progress", "resolved", "answered"}:
        fail(400, "invalid_ticket", "Проверьте ответ и статус обращения.")
    with request.app.state.database.transaction() as db:
        if not db.execute("SELECT id FROM cm_site_tickets WHERE id=%s", (identifier,)).fetchone():
            fail(404, "not_found", "Обращение не найдено.")
        db.execute("UPDATE cm_site_tickets SET reply=%s,status=%s,updated_s=%s WHERE id=%s", (reply, status, int(time.time()), identifier))
    request.app.state.database.audit(user, "ticket.replied", identifier)
    return {"ok": True}


@router.post("/api/v6/admin/accounts")
async def admin_create_account(request: Request):
    actor = current_user(request, "accounts.manage", fresh=True)
    data = await request.json()
    reason = safe_reason(data.get("reason"))
    role = data.get("role", "player")
    if role not in RANK or RANK[role] >= RANK[actor["role"]]:
        fail(403, "hierarchy", "Нельзя создать аккаунт с равными или более высокими правами.")
    user = create_user(request.app.state.database, data.get("name", ""), data.get("password", ""), role=role)
    request.app.state.database.audit(actor, "account.created", user["id"], reason, {"role": role})
    return {"ok": True, "user": public_user(user)}


@router.patch("/api/v6/admin/accounts/{identifier}")
async def admin_update_account(identifier: str, request: Request):
    actor = current_user(request, "accounts.manage", fresh=True)
    target = target_user(request.app.state.database, identifier)
    check_hierarchy(actor, target)
    data = await request.json()
    reason = safe_reason(data.get("reason"))
    role = data.get("role", target["role"])
    if role not in RANK or RANK[role] >= RANK[actor["role"]]:
        fail(403, "hierarchy", "Нельзя назначить равные или более высокие права.")
    enabled=data.get('enabled',bool(target['enabled']))
    if type(enabled) is not bool:
        fail(400,'invalid_access','Проверьте переключатель доступа.')
    with request.app.state.database.transaction() as db:
        db.execute("UPDATE cm_site_users SET role=%s,enabled=%s,blocked_reason=%s,blocked_until_s=0,updated_s=%s WHERE id=%s", (role,int(enabled),'' if enabled else reason,int(time.time()),target['id']))
        db.execute("UPDATE cm_site_sessions SET revoked_s=%s WHERE user_id=%s", (int(time.time()), target["id"]))
    request.app.state.database.audit(actor, "account.access_changed", target["id"], reason, {"role": role,'enabled':enabled})
    return {"ok": True}


@router.get("/api/v7/recovery-accounts")
def recovery_accounts(request: Request):
    current_user(request, "recovery.manage")
    with request.app.state.database.transaction() as db:
        rows = db.execute("SELECT * FROM cm_site_users ORDER BY name_norm LIMIT 500").fetchall()
    return {"accounts": [review_user(dict(row)) for row in rows]}


@router.post("/api/v7/recovery")
async def recover_account(request: Request):
    actor = current_user(request, "recovery.manage", fresh=True)
    data = await request.json()
    target = target_user(request.app.state.database, data.get("player", ""))
    check_hierarchy(actor, target)
    reason = safe_reason(data.get("reason"))
    action = data.get("action")
    now = int(time.time())
    if action not in {"password.reset", "sessions.revoke", "login.unlock", "mfa.reset", "pin.reset", "minecraft.relink"}:
        fail(400, "unsupported_action", "Неизвестное действие восстановления.")
    if action in {"pin.reset", "minecraft.relink"}:
        fail(409, "identity_proof_required", "Это действие требует отдельного подтверждения игровой личности. Обратитесь к владельцу сервера.")
    with request.app.state.database.transaction() as db:
        if action == "password.reset":
            if not target["email_verified"]:
                fail(409, "verified_email_required", "У аккаунта нет подтверждённой почты. Сброс без проверки личности запрещён.")
            db.execute("UPDATE cm_site_users SET password_hash=%s WHERE id=%s", (hash_password(secrets.token_urlsafe(48)), target["id"]))
        if action == "mfa.reset":
            if not target["email_verified"]:
                fail(409, "verified_email_required", "Для восстановления второго фактора нужна подтверждённая почта.")
            # Recovery is finalized by a one-time challenge sent to the owner.
            fail(409, "identity_proof_required", "Сброс второго фактора завершается владельцем через подтверждённую почту.")
        if action == "login.unlock":
            db.execute("DELETE FROM cm_site_attempts WHERE key LIKE %s", ("%:" + target["name_norm"],))
        if action in {"sessions.revoke", "password.reset"}:
            db.execute("UPDATE cm_site_sessions SET revoked_s=%s WHERE user_id=%s", (now, target["id"]))
    request.app.state.database.audit(actor, "recovery." + action, target["id"], reason)
    return {"ok": True, "status": "completed", "action": action, "message": "Операция выполнена. Восстановление пароля проходит через подтверждённую почту."}


@router.post("/api/v7/ban")
async def web_ban(request: Request):
    actor = current_user(request, "accounts.manage", fresh=True)
    data = await request.json()
    target = target_user(request.app.state.database, data.get("id", ""))
    check_hierarchy(actor, target)
    reason = safe_reason(data.get("reason"))
    blocked = data.get("blocked") is True
    until = int(data.get("until") or 0)
    if until and not int(time.time()) < until <= int(time.time()) + 366 * 86400:
        fail(400, "invalid_duration", "Проверьте срок блокировки.")
    with request.app.state.database.transaction() as db:
        db.execute("UPDATE cm_site_users SET enabled=%s,blocked_reason=%s,blocked_until_s=%s WHERE id=%s", (int(not blocked), reason if blocked else "", until if blocked else 0, target["id"]))
        if blocked:
            db.execute("UPDATE cm_site_sessions SET revoked_s=%s WHERE user_id=%s", (int(time.time()), target["id"]))
    request.app.state.database.audit(actor, "account.blocked" if blocked else "account.unblocked", target["id"], reason)
    return {"ok": True, "user": review_user(target_user(request.app.state.database, target["id"]))}


@router.get("/api/v6/admin/health")
def admin_health(request: Request):
    current_user(request, "server.manage")
    settings = request.app.state.settings
    with request.app.state.database.transaction() as db:
        pending = db.execute("SELECT count(*) AS n FROM cm_site_mail_outbox WHERE sent_s=0 AND expires_s>%s", (int(time.time()),)).fetchone()["n"]
    return {"website": "ok", "database": "postgresql" if not request.app.state.database.sqlite else "sqlite-test", "game": request.app.state.game.health(detailed=True), "mail": {"configured": bool(settings.smtp_host), "ready": settings.mail_domain_ready, "pending": pending}, "vk": {"configured": bool(settings.vk_client_id)}, "payments": {"configured": False}}


@router.get("/api/v6/admin/actions")
def admin_actions(request: Request):
    current_user(request, "players.view")
    path = request.app.state.settings.frontend / "data" / "admin-actions.json"
    definitions = json.loads(path.read_text(encoding="utf-8"))["actions"]
    return {"actions": [row for row in definitions if row["id"] in SUPPORTED_ACTIONS], "unsupportedReason": "В игровом адаптере нет подтверждённого обработчика этой операции."}


@router.get("/api/v6/game/status")
def game_status(request: Request):
    value = request.app.state.game.health()
    return {key: value[key] for key in ("online", "ready", "players", "maxPlayers", "version")}


@router.post("/api/v6/account/minecraft/request")
async def link_start(request: Request):
    user = current_user(request)
    throttle(request.app.state.database, "link:" + user["id"], 6, 600)
    data = await request.json()
    return await run_in_threadpool(request.app.state.game.link_start, user, str(data.get("name", "")))


@router.post("/api/v6/account/minecraft/confirm")
async def link_confirm(request: Request):
    user = current_user(request)
    throttle(request.app.state.database, "link-confirm:" + user["id"], 8, 600)
    data = await request.json()
    result = await run_in_threadpool(request.app.state.game.link_confirm, user, str(data.get("code", "")))
    return {**result, "user": review_user(target_user(request.app.state.database, user["id"]))}


@router.get("/api/v7/economy")
def wallet(request: Request):
    user = current_user(request)
    value = request.app.state.game.wallet(user)
    with request.app.state.database.transaction() as db:
        recipients = db.execute("SELECT id,display_name,name FROM cm_site_users WHERE minecraft_uuid<>'' AND id<>%s AND enabled=1 ORDER BY name_norm LIMIT 200", (user["id"],)).fetchall()
        transfers = db.execute("SELECT result FROM cm_site_receipts WHERE actor_id=%s AND operation='transfer' AND status='COMMITTED' ORDER BY created_s DESC LIMIT 100", (user["id"],)).fetchall()
    return {"balance": value["account"]["balance"], "currency": "AR", "feePercent": 0, "transferFeePercent": 0, "recipients": [{"id": r["id"], "name": r["name"], "displayName": r["display_name"]} for r in recipients], "transfers": [json.loads(r["result"])["transfer"] for r in transfers], "unread": 0, "pin": value["pin"], "ledger": value["ledger"]}


def receipt_lookup(database, actor, key, operation, data):
    if not isinstance(key, str) or not 8 <= len(key) <= 120 or not re.fullmatch(r"[A-Za-z0-9:_-]+", key):
        fail(400, "idempotency_required", "У операции должен быть уникальный идентификатор. Обновите форму.")
    fingerprint = hashlib.sha256(json.dumps(data, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
    with database.transaction() as db:
        inserted=db.execute("INSERT INTO cm_site_receipts(actor_id,key,operation,fingerprint,status,created_s) VALUES(%s,%s,%s,%s,'PENDING',%s) ON CONFLICT(actor_id,key) DO NOTHING RETURNING actor_id",(actor['id'],key,operation,fingerprint,int(time.time()))).fetchone()
        row = db.execute("SELECT * FROM cm_site_receipts WHERE actor_id=%s AND key=%s FOR UPDATE", (actor["id"], key)).fetchone()
    if inserted:
        return fingerprint,None
    if row:
        if row["operation"] != operation or row["fingerprint"] != fingerprint:
            fail(409, "idempotency_conflict", "Идентификатор уже использован для другой операции.")
        if row["status"] == "COMMITTED":
            return fingerprint, json.loads(row["result"])
        fail(409,'operation_pending','Операция уже отправлена. Проверьте её фактическое состояние перед повтором; используйте историю или обратитесь к администрации.')
    raise RuntimeError('Missing operation reservation')


def save_receipt(database, user, key, operation, fingerprint, result):
    with database.transaction() as db:
        updated=db.execute("UPDATE cm_site_receipts SET status='COMMITTED',result=%s WHERE actor_id=%s AND key=%s AND operation=%s AND fingerprint=%s AND status='PENDING'",(json.dumps(result,ensure_ascii=False),user['id'],key,operation,fingerprint))
        if updated.rowcount!=1:raise RuntimeError('Operation reservation changed')


@router.post("/api/v7/transfer")
@router.post("/api/v6/account/transfer")
async def transfer(request: Request):
    user = current_user(request)
    if not user["minecraft_uuid"]:
        fail(409, "game_link_required", "Сначала подтвердите привязку Minecraft в настройках.")
    data = await request.json()
    amount = data.get("amount")
    if isinstance(amount, bool) or not isinstance(amount, int) or not 1 <= amount <= 1000000000:
        fail(400, "invalid_amount", "Укажите целое положительное количество АР.")
    key = data.get("key", data.get("idempotencyKey", ""))
    recipient = target_user(request.app.state.database, data.get("to", ""))
    if recipient["id"] == user["id"] or not recipient["minecraft_uuid"]:
        fail(400, "invalid_recipient", "Выберите другого игрока с подтверждённым Minecraft.")
    note = str(data.get("note", ""))[:140]
    fingerprint, replay = receipt_lookup(request.app.state.database, user, key, "transfer", {"to": recipient["id"], "amount": amount, "note": note})
    if replay:
        return replay
    if data.get("expectedFee", 0) != 0:
        fail(409, "quote_changed", "Комиссия изменилась. Обновите условия перевода.")
    pin = str(data.get("pin", ""))
    if not re.fullmatch(r"[0-9]{4,8}", pin):
        fail(400, "pin_required", "Введите банковский PIN из 4–8 цифр.")
    result = await run_in_threadpool(request.app.state.game.transfer, user, recipient["minecraft_name"], amount, pin, note, "site-" + hashlib.sha256((user["id"] + key).encode()).hexdigest())
    value = {"ok": True, "transfer": {"id": result["txId"], "sender": user["id"], "recipient": recipient["id"], "fromName": user["name"], "toName": recipient["name"], "amount": amount, "fee": 0, "fee_percent": 0, "created": time.time(), "note": note, "status": "COMMITTED"}}
    save_receipt(request.app.state.database, user, key, "transfer", fingerprint, value)
    request.app.state.database.audit(user, "bank.transfer", recipient["id"], details={"amount": amount, "txId": result["txId"]})
    return value


@router.post("/api/v7/transfers-read")
def read_transfers(request: Request):
    user = current_user(request)
    with request.app.state.database.transaction() as db:
        db.execute("UPDATE cm_site_notifications SET read_s=%s WHERE user_id=%s AND read_s=0", (int(time.time()), user["id"]))
    return {"ok": True}


@router.post("/api/v6/checkout")
async def checkout(request: Request):
    user = current_user(request)
    data = await request.json()
    rows = data.get("items", data.get("cart", []))
    if not isinstance(rows, list) or not 1 <= len(rows) <= 12:
        fail(400, "invalid_cart", "Корзина должна содержать от 1 до 12 разных товаров.")
    catalog = {x["id"]: x for x in request.app.state.game.catalog() if x.get("enabled", True)}
    items, currencies, total = [], set(), 0
    for row in rows:
        item = catalog.get(row.get("id", row.get("itemId", "")))
        if not item or row.get("qty", row.get("quantity", 1)) != 1 or item["id"] in items:
            fail(400, "invalid_cart", "Игровая выдача принимает по одному экземпляру каждого товара за заказ.")
        items.append(item["id"]); currencies.add(item["currency"]); total += int(item["price"])
    if len(currencies) != 1:
        fail(400, "mixed_currencies", "Оформите покупки за АР и CM отдельными заказами.")
    if data.get("expectedTotal", total) != total:
        fail(409, "quote_changed", "Цена изменилась. Обновите корзину перед оплатой.")
    key = data.get("idempotencyKey", data.get("key", ""))
    fingerprint, replay = receipt_lookup(request.app.state.database, user, key, "checkout", {"items": items, "total": total, "currency": sorted(currencies)})
    if replay:
        return replay
    value = await run_in_threadpool(request.app.state.game.checkout, user, items, str(data.get("pin", "")), total, "site-" + hashlib.sha256((user["id"] + key).encode()).hexdigest(), next(iter(currencies)))
    orders = [{"id": row["purchaseId"], "playerId": user["id"], "itemId": row["itemId"], "qty": 1, "status": row["status"], "total": row.get("priceAr", catalog[row["itemId"]]["price"]), "currency": next(iter(currencies)), "at": int(time.time() * 1000)} for row in value["items"]]
    result = {"ok": True, "orders": orders, "receipt": value, "message": value.get("pickupHint", "Покупка записана. Получите предмет в игре.")}
    save_receipt(request.app.state.database, user, key, "checkout", fingerprint, result)
    request.app.state.database.audit(user, "shop.checkout", details={"items": items, "total": total})
    return result


@router.post("/api/v7/maintenance")
async def maintenance(request: Request):
    user = current_user(request, "settings.manage", fresh=True)
    data = await request.json()
    reason = safe_reason(data.get("reason"))
    value = {"enabled": data.get("enabled") is True, "message": str(data.get("message", ""))[:500], "until": str(data.get("until", ""))[:32]}
    request.app.state.database.set_setting("maintenance", value, user["name"])
    request.app.state.database.audit(user, "site.maintenance", reason=reason, details={"enabled": value["enabled"]})
    return {"ok": True, "maintenance": value}


async def save_content(request, data):
    user = current_user(request, "content.manage", fresh=True)
    for key, value in data.items():
        if key not in {"pages", "gallery", "faq"} or not isinstance(value, list) or len(value) > 100 or len(json.dumps(value)) > 64000:
            fail(400, "invalid_content", "Проверьте содержимое и размер раздела.")
        # Content is displayed through escaping frontend renderers; active URLs
        # and embedded markup are not accepted as stored CMS content.
        if re.search(r"(?i)<\s*(script|iframe|object)|javascript:|data:text/html", json.dumps(value)):
            fail(400, "invalid_content", "Активное содержимое запрещено.")
        request.app.state.database.set_setting(key, value, user["name"])
    request.app.state.database.audit(user, "content.updated", details={"sections": list(data)})
    return {"ok": True}


@router.post("/api/v6/admin/content")
async def update_content(request: Request):
    return await save_content(request, await request.json())


def game_target(request, identifier, mutation=False):
    actor = current_user(request, "players.moderate" if mutation else "players.view", fresh=mutation)
    with request.app.state.database.transaction() as db:
        row = db.execute("SELECT * FROM cm_site_users WHERE id=%s OR name_norm=%s OR minecraft_uuid=%s OR lower(minecraft_name)=%s", (identifier, str(identifier).lower(), identifier, str(identifier).lower())).fetchone()
    if row:
        target = dict(row)
        if mutation:
            check_hierarchy(actor, target)
        if not target["minecraft_uuid"]:
            fail(409, "game_link_required", "У аккаунта ещё нет подтверждённой привязки Minecraft.")
        return {"uuid": target["minecraft_uuid"], "player": target["minecraft_name"]}
    if not re.fullmatch(r"[A-Za-z0-9_]{1,32}|[a-fA-F0-9-]{36}", str(identifier)):
        fail(400, "invalid_player", "Проверьте игровой профиль.")
    return {"uuid": identifier} if len(identifier) == 36 else {"player": identifier}


@router.get("/api/v6/admin/players/{identifier}/inventory")
def inventory(identifier: str, request: Request):
    user = current_user(request, "inventory.view")
    target = game_target(request, identifier)
    value = request.app.state.game.rpc("inventory.snapshot", user, target)
    return {"ok": True, "snapshot": value["data"], "source": "minecraft-main-thread"}


@router.post("/api/v6/admin/players/{identifier}/inventory/remove")
async def remove_inventory(identifier: str, request: Request):
    user = current_user(request, "inventory.manage", fresh=True)
    data = await request.json()
    if not re.fullmatch(r"[a-f0-9]{64}", str(data.get("expectedVersion", ""))):
        fail(400, "snapshot_required", "Обновите инвентарь: нужен актуальный снимок игрового сервера.")
    kind, slot, quantity = data.get("kind"), data.get("slot"), data.get("quantity")
    if kind not in {"inventory", "ender"} or isinstance(slot, bool) or not isinstance(slot, int) or not 0 <= slot < (41 if kind == "inventory" else 27):
        fail(400, "invalid_slot", "Выберите ячейку в инвентаре.")
    if isinstance(quantity, bool) or not isinstance(quantity, int) or not 1 <= quantity <= 99:
        fail(400, "invalid_quantity", "Проверьте количество предметов.")
    reason = safe_reason(data.get("reason"))
    target = game_target(request, identifier, mutation=True)
    key = data.get("key", "")
    params = {**target, "kind": kind, "slot": slot, "quantity": quantity, "expectedVersion": data["expectedVersion"], "reason": reason}
    fingerprint, replay = receipt_lookup(request.app.state.database, user, key, "inventory.remove", params)
    if replay:
        return replay
    value = await run_in_threadpool(request.app.state.game.rpc, "inventory.remove", user, params, key)
    save_receipt(request.app.state.database, user, key, "inventory.remove", fingerprint, value)
    request.app.state.database.audit(user, "inventory.remove", identifier, reason, {"slot": slot, "kind": kind, "quantity": quantity, "requestId": key})
    return value


@router.post("/api/v6/admin/actions")
async def execute_action(request: Request):
    data = await request.json()
    action = data.get("action")
    if action not in SUPPORTED_ACTIONS or action.startswith("inventory."):
        current_user(request, "players.view")
        fail(400, "unsupported_action", "Это действие не поддерживается защищённым игровым адаптером.")
    server = action.startswith(("time.", "weather.")) or action == "save"
    permission = "server.manage" if server or action.startswith("whitelist.") else "players.moderate"
    user = current_user(request, permission, fresh=True)
    reason = safe_reason(data.get("reason"))
    params = data.get("params", {})
    if not isinstance(params, dict):
        fail(400, "invalid_parameters", "Проверьте параметры действия.")
    allowed = {"player", "uuid", "target", "mode", "x", "y", "z", "world", "durationMs"}
    params = {key: value for key, value in params.items() if key in allowed}
    if not server:
        identifier = str(data.get("player", params.get("player", params.get("uuid", ""))))
        params.update(game_target(request, identifier, mutation=True))
    params["reason"] = reason
    key = data.get("idempotencyKey", data.get("key", ""))
    fingerprint, replay = receipt_lookup(request.app.state.database, user, key, action, params)
    if replay:
        return replay
    value = await run_in_threadpool(request.app.state.game.rpc, action, user, params, key)
    save_receipt(request.app.state.database, user, key, action, fingerprint, value)
    request.app.state.database.audit(user, "game." + action, str(params.get("player", "server")), reason, {"requestId": key})
    return value
