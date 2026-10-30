"""Authoritative Minecraft adapter; no browser-supplied balances or fake receipts."""
from pathlib import Path
import base64
import hashlib
import hmac
import importlib
import json
import os
import re
import secrets
import socket
import struct
import threading
import time
import uuid

from fastapi import HTTPException
from .security import fail

MC_NAME = re.compile(r"[A-Za-z0-9_]{1,32}\Z")

# One value for the message the plugin prints in game and the expiry the site
# enforces; the shipped JAR says five minutes, so the site must match it.
LINK_TTL_MS = 5 * 60 * 1000


def catalog_version(item):
    fields = ("id", "name", "description", "category", "image", "video", "currency", "price", "limit", "enabled", "visibility", "sourceType", "material", "maxQuantity")
    snapshot = {key: item.get(key) for key in fields}
    return hashlib.sha256(json.dumps(snapshot, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")).hexdigest()[:24]


class GameAdapter:
    def __init__(self, settings, database):
        self.settings, self.database = settings, database
        self.server = settings.game_root / "minecraft" / "server"
        self.requests = self.server / "plugins" / "CopiMineWebsiteBridge" / "requests"
        self._legacy = None
        self._lock = threading.RLock()
        self._health = (0.0, {})

    def legacy(self):
        if self.database.sqlite or not self.server.is_dir():
            fail(503, "game_unavailable", "Игровой сервер ещё не подключён. Операция не выполнена.")
        with self._lock:
            if self._legacy is None:
                self._legacy = importlib.import_module("backend.legacy.main")
                self._legacy.ensure_auth_db()
            return self._legacy

    def rcon(self, command):
        password = os.getenv("RCON_PASSWORD", "")
        if not password:
            fail(503, "game_unavailable", "Подключение к игровому серверу не настроено.")
        def read_exact(conn, length):
            result = b""
            while len(result) < length:
                chunk = conn.recv(length - len(result))
                if not chunk:
                    raise OSError("RCON connection closed")
                result += chunk
            return result
        def send(conn, request_id, kind, text):
            body = struct.pack("<ii", request_id, kind) + text.encode("utf-8") + b"\0\0"
            conn.sendall(struct.pack("<i", len(body)) + body)
        def receive(conn):
            length = struct.unpack("<i", read_exact(conn, 4))[0]
            if not 10 <= length <= 1024 * 1024:
                raise OSError("Invalid RCON response")
            body = read_exact(conn, length)
            return struct.unpack("<ii", body[:8]), body[8:-2]
        try:
            with socket.create_connection((os.getenv("RCON_HOST", "127.0.0.1"), int(os.getenv("RCON_PORT", "25575"))), timeout=5) as conn:
                conn.settimeout(5)
                send(conn, 81, 3, password)
                (request_id, _), _ = receive(conn)
                if request_id == -1:
                    raise OSError("RCON authentication failed")
                send(conn, 82, 2, command)
                result = b""
                for _ in range(128):
                    (request_id, _), chunk = receive(conn)
                    if request_id != 82:
                        raise OSError("Unexpected RCON response")
                    result += chunk
                    if len(chunk) < 4096:
                        break
                else:
                    raise OSError("RCON response too large")
                return result.decode("utf-8", errors="replace")
        except (OSError, ValueError, struct.error):
            fail(503, "game_unavailable", "Игровой сервер не ответил. Проверьте состояние операции в истории.")

    def _parse_rcon(self, output):
        """Разбирает ответ плагина `CMWEB:<base64 json>` в структуру результата."""
        match = re.search(r"CMWEB:([A-Za-z0-9+/=]+)", output)
        if not match:
            fail(503, "game_unavailable", "Игровой мост не подтвердил выполнение операции.")
        try:
            return json.loads(base64.b64decode(match.group(1), validate=True))
        except (ValueError, TypeError):
            fail(503, "game_unavailable", "Получен некорректный ответ игрового сервера.")

    def status(self, name="health"):
        """Запрос без файла: плагин отвечает на `cmwebbridge health` напрямую.

        Опрос состояния идёт на каждый заход на сайт, а файловый запрос остаётся
        навсегда ради защиты от повторов — поэтому health не должен его создавать.
        """
        result = self._parse_rcon(self.rcon("cmwebbridge " + name))
        if not result.get("ok"):
            fail(503, result.get("code", "game_unavailable"), result.get("message", "Игровой сервер не ответил."))
        return result

    def rpc(self, operation, actor, parameters=None, key=None):
        if not self.requests.is_dir():
            fail(503, "game_unavailable", "Защищённый мост с Minecraft ещё не подключён.")
        api_key = os.getenv("PLUGIN_API_KEY", "")
        if len(api_key) < 32:
            fail(503, "game_unavailable", "Ключ игрового подключения не настроен.")
        actor_id = actor.get("id", "system")
        request_id = hashlib.sha256((actor_id + ":" + operation + ":" + (key or uuid.uuid4().hex)).encode()).hexdigest()[:32]
        request_path = self.requests / (request_id + ".json")
        body = {"id": request_id, "operation": operation, "actor": actor.get("name", "system"), "actorId": actor_id, "params": parameters or {}, "createdAt": int(time.time() * 1000)}
        stable = lambda value: {k: v for k, v in value.items() if k != "createdAt"}
        if request_path.exists():
            try:
                old = json.loads(json.loads(request_path.read_text(encoding="utf-8"))["payload"])
            except (OSError, ValueError, KeyError):
                fail(409, "request_conflict", "Не удалось проверить предыдущий запрос. Обновите данные.")
            if stable(old) != stable(body):
                fail(409, "idempotency_conflict", "Этот идентификатор уже использован для другой операции.")
            body = old
        payload = json.dumps(body, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
        signature = hmac.new(api_key.encode(), payload.encode(), "sha256").hexdigest()
        if not request_path.exists():
            temporary = self.requests / ("." + request_id + "." + secrets.token_hex(4))
            fd = os.open(temporary, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o640)
            try:
                with os.fdopen(fd, "w", encoding="utf-8") as file:
                    json.dump({"payload": payload, "signature": signature}, file, ensure_ascii=False)
                    file.flush()
                    os.fsync(file.fileno())
                # os.replace would let two processes with the same id publish different
                # payloads and silently keep the last one; link fails for the loser.
                os.link(temporary, request_path)
            except FileExistsError:
                try:
                    winner = json.loads(json.loads(request_path.read_text(encoding="utf-8"))["payload"])
                except (OSError, ValueError, KeyError):
                    fail(409, "request_conflict", "Не удалось проверить предыдущий запрос. Обновите данные.")
                if stable(winner) != stable(body):
                    fail(409, "idempotency_conflict", "Этот идентификатор уже использован для другой операции.")
            finally:
                try:
                    os.unlink(temporary)
                except OSError:
                    pass
        result = self._parse_rcon(self.rcon("cmwebbridge " + request_id))
        if not result.get("ok"):
            code = result.get("code", "game_action_failed")
            status = 409 if code in {"stale_snapshot", "player_offline", "idempotency_conflict"} else 503 if code == "identity_not_ready" else 400
            fail(status, code, result.get("message", "Игровой сервер отклонил операцию."))
        return result

    def health(self, detailed=False):
        now = time.monotonic()
        if now - self._health[0] < 5 and not detailed:
            return self._health[1]
        result = {"online": False, "ready": False, "adapter": False, "players": 0, "maxPlayers": 50, "version": "26.3"}
        try:
            with socket.create_connection((os.getenv("MC_HOST", "127.0.0.1"), int(os.getenv("MC_PORT", "25565"))), timeout=0.5):
                result["online"] = True
            proof = self.status("health")
            data = proof.get("data", {})
            result.update(data)
            result["onlinePlayers"] = data.get("players", [])
            result["players"] = data.get("online", 0)
            result["online"] = True
            result["adapter"] = True
            required = {"CopiMineEconomyCore", "CopiMineElectionCore", "CopiMineUltimateAdminPlus", "CopiMineArtifacts", "CopiMineWorldCore", "CopiMineNarcotics"}
            result["ready"] = required.issubset(set(result.get("plugins", [])))
        except HTTPException:
            pass
        except OSError:
            pass
        self._health = (now, result)
        return result

    def account(self, user, linked=True):
        if linked and not user["minecraft_uuid"]:
            fail(409, "game_link_required", "Сначала подтвердите привязку Minecraft в настройках аккаунта.")
        legacy = self.legacy()
        now = int(time.time() * 1000)
        with legacy.auth_conn() as db:
            legacy.ensure_v4_schema(db)
            db.execute("INSERT INTO site_accounts(id,username,username_norm,password_hash,role,enabled,minecraft_uuid,minecraft_name,created_at,updated_at,last_login_at,registration_ip) VALUES(%s,%s,%s,%s,'player',1,%s,%s,%s,%s,0,'') ON CONFLICT(id) DO NOTHING", (user["id"], user["name"], user["name_norm"], "external-website-session", user["minecraft_uuid"], user["minecraft_name"], now, now))
            row = db.execute("SELECT * FROM site_accounts WHERE id=%s", (user["id"],)).fetchone()
            db.commit()
        return dict(row)

    def link_start(self, user, name):
        if not MC_NAME.fullmatch(str(name)):
            fail(400, "invalid_player", "Проверьте ник Minecraft.")
        legacy = self.legacy()
        account = self.account(user, linked=False)
        # The bridge checks AuthMe's authenticated Player object before delivery.
        # A connection using a claimed nickname is insufficient proof.
        code = "".join(secrets.choice("23456789ABCDEFGHJKLMNPQRSTUVWXYZ") for _ in range(8))
        result = self.rpc("link.send", user, {"player": name, "code": code})
        identity = result["data"]
        now = int(time.time() * 1000)
        with legacy.auth_conn() as db:
            db.execute("UPDATE one_time_link_codes SET status='EXPIRED' WHERE site_account_id=%s AND status='PENDING'", (user["id"],))
            db.execute("INSERT INTO one_time_link_codes(id,site_account_id,minecraft_name,minecraft_uuid,code_hash,status,created_at,expires_at) VALUES(%s,%s,%s,%s,%s,'PENDING',%s,%s)", (uuid.uuid4().hex, user["id"], identity["name"], identity["uuid"], legacy.sha256_hex(code), now, now + LINK_TTL_MS))
            db.commit()
        return {"ok": True, "deliveredInGame": True, "expiresAt": now + LINK_TTL_MS}

    def link_confirm(self, user, code):
        legacy = self.legacy()
        result = legacy.confirm_link_code_sync(self.account(user, linked=False), code)
        account = result["account"]
        with self.database.transaction() as db:
            duplicate = db.execute("SELECT id FROM cm_site_users WHERE minecraft_uuid=%s AND id<>%s", (account["minecraftUuid"], user["id"])).fetchone()
            if duplicate:
                fail(409, "identity_conflict", "Игровой профиль уже связан с другим аккаунтом.")
            db.execute("UPDATE cm_site_users SET minecraft_uuid=%s,minecraft_name=%s,updated_s=%s WHERE id=%s", (account["minecraftUuid"], account["minecraftName"], int(time.time()), user["id"]))
        self.database.audit(user, "minecraft.link", account["minecraftName"])
        return {"ok": True}

    def wallet(self, user):
        legacy = self.legacy()
        result = legacy.player_bank_overview_sync(self.account(user))
        return result

    def transfer(self, user, recipient, amount, pin, note, key):
        legacy = self.legacy()
        data = legacy.PlayerBankTransferIn(recipient=recipient, amount=amount, pin=pin, note=note, idempotency_key=key)
        return legacy.transfer_player_bank_sync(self.account(user), data)

    def checkout(self, user, item_ids, pin, expected_total, key, currency="AR"):
        legacy = self.legacy()
        account = self.account(user)
        data = legacy.PlayerCartCheckoutIn(item_ids=item_ids, pin=pin, expected_total=expected_total, idempotency_key=key)
        if currency == "AR":
            return legacy.checkout_ar_cart_sync(account, data)
        return legacy.checkout_donation_cart_sync(account["minecraft_uuid"], account["minecraft_name"], data)

    def catalog(self, private=False):
        file = self.settings.private / "catalog-v73.json"
        items = json.loads(file.read_text(encoding="utf-8"))["items"]
        source = self.settings.game_root / "copimine-artifacts" / "items.yml"
        if source.exists():
            import yaml
            try:
                live = yaml.safe_load(source.read_text(encoding="utf-8")) or {}
                definitions=live.get('items',[])
                if not isinstance(definitions,list): raise ValueError('Catalog entries must be a list')
                definitions=[dict(x,currency='AR') for x in definitions if isinstance(x,dict)]
                definitions += [dict(x,id=x.get('item-id'),currency='CM',source='DONATION_SHOP') for x in live.get('donation-catalog',{}).get('items',[]) if isinstance(x,dict)]
                presentation={x['id']:x for x in items}
                parsed=[]
                clean=lambda value:re.sub(r'(?i)[&§][0-9a-fk-or]','',str(value or ''))
                for raw in definitions:
                    identifier=raw.get('id')
                    if not isinstance(identifier,str) or not re.fullmatch('[a-z0-9_]{1,100}',identifier): continue
                    row=dict(presentation.get(identifier,{}))
                    currency=raw['currency'];sellable=raw.get('source') in {'AR_SHOP','DONATION_SHOP'}
                    price=raw.get('price_ar') if currency=='AR' else raw.get('price-donation')
                    if type(price) is not int or price<0:price=0;sellable=False
                    material=str(raw.get('material',raw.get('base-material','PAPER'))).lower()
                    row.update(id=identifier,currency=currency,price=price,maxQuantity=1,
                               name=row.get('name') or clean(raw.get('name',raw.get('display-name',identifier))),
                               description=row.get('description') or clean(raw.get('effect-description',' '.join(raw.get('lore',[])))),
                               category=row.get('category','Предметы'),material='minecraft:'+material.removeprefix('minecraft:'),
                               enabled=raw.get('enabled',True) is True,visibility='public' if sellable else 'private',
                               sourceType=raw.get('source','ADMIN_ONLY'),limit=int(raw.get('per_player_limit',0)),
                               image=row.get('image') or 'assets/materials/'+material+'.png',sourceKind='runtime-config',
                               grantable=identifier not in {'night_cloak','rift_core_shard'} and row.get('grantable',True))
                    parsed.append(row)
                present={x['id'] for x in parsed}
                parsed += [dict(x,enabled=False,grantable=False,visibility='private') for x in items if x['id'] not in present and x.get('visibility')!='public']
                items=parsed
            except (OSError,ValueError,TypeError,yaml.YAMLError):
                fail(503,'catalog_unavailable','Каталог игрового сервера временно недоступен. Покупка не выполнена.')
        overrides = self.database.setting("catalog_overrides", {})
        for item in items:
            item['maxQuantity']=1
            overlay = overrides.get(item.get("id"), {}) if isinstance(overrides, dict) else {}
            if isinstance(overlay, dict):
                for key in ("name", "description", "category", "video", "image"):
                    if key in overlay:
                        item[key] = overlay[key]
            item["version"] = catalog_version(item)
        # adminSubtitle marks internal catalog entries (closed recipes) that must not be sold publicly
        return items if private else [x for x in items if x.get("visibility") == "public" and not x.get("adminSubtitle")]
