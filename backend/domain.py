import hashlib
import hmac
import json
import re
import secrets
import time
import uuid
from urllib.parse import quote

from fastapi import APIRouter, Request, Response
from starlette.concurrency import run_in_threadpool

from .auth import create_user, issue_session, login, logout
from .cms import public_config, public_sections
from .game import catalog_version
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


def add_notification(database, user_id, title, body, identifier=None, href=""):
    notification_id = identifier or uuid.uuid4().hex
    with database.transaction() as db:
        db.execute(
            "INSERT INTO cm_site_notifications(id,user_id,title,body,href,created_s) VALUES(%s,%s,%s,%s,%s,%s) ON CONFLICT(id) DO NOTHING",
            (notification_id, user_id, title[:120], body[:1000], href[:500], int(time.time())),
        )


def notification_feed(database, user_id):
    with database.transaction() as db:
        rows = db.execute(
            "SELECT id,title,body,href,created_s,read_s FROM cm_site_notifications WHERE user_id=%s ORDER BY created_s DESC LIMIT 50",
            (user_id,),
        ).fetchall()
        unread = db.execute(
            "SELECT COUNT(*) AS count FROM cm_site_notifications WHERE user_id=%s AND read_s=0",
            (user_id,),
        ).fetchone()["count"]
    return {
        "unread": int(unread),
        "notifications": [
            {"id": row["id"], "type": str(row["id"]).split(":", 1)[0], "title": row["title"], "body": row["body"], "href": row["href"], "createdAt": int(row["created_s"]) * 1000, "read": bool(row["read_s"])}
            for row in rows
        ],
    }


def transfer_history(database, user, value):
    with database.transaction() as db:
        linked = db.execute(
            "SELECT id,name,display_name,minecraft_uuid,minecraft_name FROM cm_site_users WHERE minecraft_uuid<>''"
        ).fetchall()
    by_uuid = {str(row["minecraft_uuid"]): dict(row) for row in linked}
    own_name = user.get("minecraft_name") or user["name"]
    results = []
    for row in value.get("ledger", []):
        kind = str(row.get("tx_type") or "").upper()
        if kind not in {"TRANSFER_IN", "TRANSFER_OUT"} or (row.get("status") and row["status"] != "COMMITTED"):
            continue
        details = row.get("details") or "{}"
        try:
            details = json.loads(details) if isinstance(details, str) else details
        except (TypeError, ValueError):
            details = {}
        if not isinstance(details, dict):
            details = {}
        counterparty = str(row.get("counterparty_account_id") or "")
        peer_uuid = counterparty.split(":", 1)[1] if ":" in counterparty else ""
        peer = by_uuid.get(peer_uuid, {})
        incoming = kind == "TRANSFER_IN"
        peer_name = row.get("counterparty_name") or peer.get("minecraft_name") or peer.get("name") or "Игрок"
        tx_id = str(row.get("tx_id") or "")
        for suffix in (":in", ":out"):
            if tx_id.endswith(suffix):
                tx_id = tx_id[:-len(suffix)]
                break
        results.append({
            "id": tx_id,
            "sender": peer.get("id", "") if incoming else user["id"],
            "recipient": user["id"] if incoming else peer.get("id", ""),
            "fromName": peer_name if incoming else own_name,
            "toName": own_name if incoming else (details.get("recipient") or peer_name),
            "amount": abs(int(row.get("amount") or 0)),
            "fee": 0,
            "fee_percent": 0,
            "at": int(row.get("created_at") or 0),
            "note": str(details.get("note") or "")[:140],
            "status": "COMMITTED",
            "incoming": incoming,
            "seen": True,
        })
    return sorted(results, key=lambda item: item["at"], reverse=True)[:100]


def sync_incoming_transfer_notifications(database, user_id, value):
    now = int(time.time())
    with database.transaction() as db:
        for row in value.get("ledger", []):
            if str(row.get("tx_type") or "").upper() != "TRANSFER_IN" or row.get("status", "COMMITTED") != "COMMITTED":
                continue
            tx_id = str(row.get("tx_id") or "")
            if tx_id.endswith(":in"):
                tx_id = tx_id[:-3]
            if not tx_id:
                continue
            try:
                details = row.get("details") or "{}"
                details = json.loads(details) if isinstance(details, str) else details
            except (TypeError, ValueError):
                details = {}
            if not isinstance(details, dict):
                details = {}
            created_ms = int(row.get("created_at") or now * 1000)
            created_s = max(1, created_ms // 1000)
            sender = str(row.get("counterparty_name") or "Игрок")[:32]
            body = f"{sender} перевёл {abs(int(row.get('amount') or 0))} АР."
            note = str(details.get("note") or "")[:140]
            if note:
                body += f" Сообщение: {note}"
            read_s = 0 if now - 7 * 86400 <= created_s <= now + 300 else created_s
            db.execute(
                "INSERT INTO cm_site_notifications(id,user_id,title,body,href,created_s,read_s) VALUES(%s,%s,%s,%s,%s,%s,%s) ON CONFLICT(id) DO NOTHING",
                (f"transfer:{tx_id}", user_id, "Входящий перевод", body[:1000], f"#account/transfers?history=1&transfer={quote(tx_id, safe='')}", created_s, read_s),
            )


def content(request):
    return public_sections(request)


def build_state(request):
    database, game = request.app.state.database, request.app.state.game
    info = game.health()
    config = {
        "serverName": "CopiMine", "serverIp": "copimine.ru", "serverAddress": "copimine.ru",
        "serverVersion": "26.3", "online": info["online"], "playersOnline": info["players"],
        "maxPlayers": info["maxPlayers"], "transferFeePercent": 0, "production": True,
        "maintenance": database.setting("maintenance", {"enabled": False}),
    }
    config.update(public_config(request))
    config["serverVersion"] = config.get("gameVersion", config["serverVersion"])
    result = {
        "players": [], "catalog": game.catalog(private=bool(request.state.user and "catalog.manage" in PERMISSIONS.get(request.state.user["role"], []))), "orders": [], "payments": [], "ledger": [],
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
    add_notification(request.app.state.database, user["id"], "Пароль изменён", "Пароль аккаунта CopiMine обновлён.", f"password:{user['id']}:{now}", "#settings/security")
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
    add_notification(request.app.state.database, user["id"], "VK ID отвязан", "Вход через VK ID отключён для аккаунта CopiMine.", f"vk-unlink:{user['id']}:{int(time.time())}", "#settings/connections")
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
        ticket = db.execute("SELECT id,user_id,title FROM cm_site_tickets WHERE id=%s", (identifier,)).fetchone()
        if not ticket:
            fail(404, "not_found", "Обращение не найдено.")
        updated = int(time.time())
        db.execute("UPDATE cm_site_tickets SET reply=%s,status=%s,updated_s=%s WHERE id=%s", (reply, status, updated, identifier))
    request.app.state.database.audit(user, "ticket.replied", identifier)
    add_notification(request.app.state.database, ticket["user_id"], "Ответ по обращению", f"Команда ответила на обращение «{ticket['title']}».", f"ticket-reply:{identifier}:{updated}", f"#account/support?ticket={quote(identifier, safe='')}")
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


@router.patch("/api/v6/admin/catalog/{identifier}")
async def update_catalog_item(request: Request, identifier: str):
    user = current_user(request, "catalog.manage", fresh=True)
    data = await request.json()
    allowed = {"version", "name", "description", "category", "price", "limit", "reason"}
    if set(data) - allowed:
        fail(400, "invalid_catalog_update", "В форме есть поля, которые нельзя менять здесь.")
    reason = safe_reason(data.get("reason"))
    database = request.app.state.database
    with database.transaction() as db:
        if not db.sqlite:
            db.execute("SELECT pg_advisory_xact_lock(hashtext(%s))", ("copimine-catalog:" + str(identifier),))
        items = request.app.state.game.catalog(private=True)
        item = next((row for row in items if row.get("id") == identifier), None)
        if not item:
            fail(404, "catalog_item_not_found", "Предмет не найден в игровом каталоге.")
        if item.get("visibility") != "public" or item.get("sourceType") not in {"AR_SHOP", "DONATION_SHOP"}:
            fail(403, "catalog_item_protected", "Этот предмет нельзя менять через магазин.")
        expected = data.get("version")
        if not isinstance(expected, str) or not hmac.compare_digest(catalog_version(item), expected):
            fail(409, "catalog_item_changed", "Каталог изменился. Обнови список и повтори правку.")
        name = str(data.get("name", item.get("name", ""))).strip()
        description = str(data.get("description", item.get("description", ""))).strip()
        category = str(data.get("category", item.get("category", ""))).strip()
        if not 1 <= len(name) <= 120 or not 1 <= len(description) <= 3000 or not 1 <= len(category) <= 80:
            fail(400, "invalid_catalog_update", "Проверь название, описание и категорию товара.")
        if re.search(r"(?i)<\s*/?\s*[a-z!][^>]*>|javascript\s*:|data\s*:\s*text/html", name + "\n" + description + "\n" + category):
            fail(400, "invalid_catalog_update", "HTML и активные ссылки в карточках товара запрещены.")
        price, limit = data.get("price"), data.get("limit")
        if type(price) is not int or not 1 <= price <= 1_000_000_000:
            fail(400, "invalid_catalog_update", "Цена должна быть целым числом от 1 до 1 000 000 000.")
        if type(limit) is not int or not 0 <= limit <= 1000:
            fail(400, "invalid_catalog_update", "Лимит на игрока должен быть от 0 до 1000; 0 снимает лимит.")
        currency = item.get("currency")
        bucket = "AR" if currency == "AR" else "DONATION" if currency == "CM" else ""
        if not bucket:
            fail(409, "catalog_currency_unknown", "Валюта товара не распознана. Покупка не изменена.")
        applied = []
        if price != item.get("price") or limit != item.get("limit", 0):
            legacy = request.app.state.game.legacy()
            try:
                if price != item.get("price"):
                    await run_in_threadpool(legacy.update_shop_price_sync, identifier, bucket, price, user["name"], uuid.uuid4().hex)
                    applied.append(("price", int(item.get("price", 0))))
                if limit != item.get("limit", 0):
                    await run_in_threadpool(legacy.update_shop_limit_sync, identifier, bucket, limit, user["name"], uuid.uuid4().hex)
                    applied.append(("limit", int(item.get("limit", 0))))
            except Exception:
                rollback_failed = False
                for field, old_value in reversed(applied):
                    try:
                        function = legacy.update_shop_price_sync if field == "price" else legacy.update_shop_limit_sync
                        await run_in_threadpool(function, identifier, bucket, old_value, user["name"], uuid.uuid4().hex)
                    except Exception:
                        rollback_failed = True
                if rollback_failed:
                    fail(503, "catalog_partial_update", "Не удалось завершить правку. Проверь цену и лимит товара в игре.")
                raise
        row = db.execute("SELECT value FROM cm_site_settings WHERE key=%s", ("catalog_overrides",)).fetchone()
        overlays = json.loads(row["value"]) if row else {}
        if not isinstance(overlays, dict):
            overlays = {}
        overlays[identifier] = {"name": name, "description": description, "category": category}
        now = int(time.time())
        db.execute("INSERT INTO cm_site_settings(key,value,updated_s,actor) VALUES(%s,%s,%s,%s) ON CONFLICT(key) DO UPDATE SET value=excluded.value,updated_s=excluded.updated_s,actor=excluded.actor", ("catalog_overrides", json.dumps(overlays, ensure_ascii=False), now, user["name"]))
        db.execute("INSERT INTO cm_site_audit(id,actor_id,actor_name,action,target,reason,details,created_s) VALUES(%s,%s,%s,%s,%s,%s,%s,%s)", (uuid.uuid4().hex, user["id"], user["name"], "catalog.updated", identifier, reason, json.dumps({"price": price, "limit": limit, "currency": currency}, ensure_ascii=False), now))
    return {"ok": True, "id": identifier}


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
    updated = target_user(request.app.state.database, user["id"])
    add_notification(request.app.state.database, user["id"], "Minecraft привязан", f"К аккаунту CopiMine привязан профиль {updated['minecraft_name']}.", f"minecraft-link:{user['id']}:{updated['minecraft_uuid']}", "#settings/connections")
    return {**result, "user": review_user(updated)}


@router.get("/api/v7/economy")
def wallet(request: Request):
    user = current_user(request)
    value = request.app.state.game.wallet(user)
    with request.app.state.database.transaction() as db:
        recipients = db.execute("SELECT id,display_name,name FROM cm_site_users WHERE minecraft_uuid<>'' AND id<>%s AND enabled=1 ORDER BY name_norm LIMIT 200", (user["id"],)).fetchall()
    sync_incoming_transfer_notifications(request.app.state.database, user["id"], value)
    feed = notification_feed(request.app.state.database, user["id"])
    transfers = transfer_history(request.app.state.database, user, value)
    return {"balance": value["account"]["balance"], "currency": "AR", "feePercent": 0, "transferFeePercent": 0, "recipients": [{"id": r["id"], "name": r["name"], "displayName": r["display_name"]} for r in recipients], "transfers": transfers, "notifications": feed["notifications"], "unread": feed["unread"], "pin": value["pin"], "ledger": value["ledger"]}


@router.get("/api/v7/notifications")
@router.get("/api/v6/account/notifications")
def account_notifications(request: Request):
    user = current_user(request)
    return notification_feed(request.app.state.database, user["id"])


@router.post("/api/v7/notifications/{identifier}/read")
def read_notification(identifier: str, request: Request):
    user = current_user(request)
    with request.app.state.database.transaction() as db:
        updated = db.execute(
            "UPDATE cm_site_notifications SET read_s=%s WHERE id=%s AND user_id=%s AND read_s=0",
            (int(time.time()), identifier, user["id"]),
        )
    return {"ok": True, "updated": updated.rowcount == 1}


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
    body = f"{user.get('display_name') or user['name']} перевёл {amount} АР."
    if note:
        body += f" Сообщение: {note}"
    add_notification(request.app.state.database, recipient["id"], "Входящий перевод", body, f"transfer:{result['txId']}", f"#account/transfers?history=1&transfer={quote(str(result['txId']), safe='')}")
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
    current_user(request, "content.manage", fresh=True)
    fail(409, "versioned_editor_required", "Открой редактор контента: он проверяет версию перед сохранением.")


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
