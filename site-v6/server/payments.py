"""YooKassa redirect payments. Provider truth is re-fetched before any credit.

Card details never pass through this service. Webhook bodies are hints, not proof.
Secrets and optional receipt settings live only in the private operator config.
"""
import base64
from decimal import Decimal, InvalidOperation
import hashlib
import json
import re
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import Request, build_opener, HTTPRedirectHandler
from .common import APIError, integer, idem
from .security import digest
from .storage import encoded, identifier, now_ms

PROVIDER_API = "https://api.yookassa.ru/v3/payments"
PROVIDER_ID = re.compile(r"[A-Za-z0-9_-]{8,80}")
TERMINAL = {"completed", "cancelled", "test_completed"}


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


def confirmation_url(value):
    if not value:
        return None
    uri = urlsplit(value)
    if uri.scheme != "https" or uri.username or uri.password or uri.hostname not in (
        "yoomoney.ru", "yookassa.ru", "www.yoomoney.ru", "www.yookassa.ru"
    ) or uri.port not in (None, 443):
        raise APIError(502, "Некорректная ссылка платёжного сервиса.", "payment_provider_error")
    return value


class Payments:
    def init_payments(self):
        with self.store.transaction() as conn:
            conn.execute("""CREATE TABLE IF NOT EXISTS payment_intents(
                payment_id TEXT PRIMARY KEY REFERENCES payments(id),
                user_id TEXT NOT NULL,idempotency_key TEXT NOT NULL,request_hash TEXT NOT NULL,
                cm_amount INTEGER NOT NULL,method TEXT NOT NULL,confirmation_url TEXT,
                updated_at INTEGER NOT NULL,last_checked_at INTEGER,
                UNIQUE(user_id,idempotency_key))""")

    def payment_configured(self):
        return bool(self.config.payment_shop_id and self.config.payment_secret)

    def _payment_enabled(self):
        if not self.payment_configured():
            raise APIError(503, "Пополнение временно недоступно: платёжный сервис не настроен. Средства не списаны.",
                           "payments_unconfigured")

    def _provider_request(self, method, path="", payload=None, key=None):
        self._payment_enabled()
        if path and not PROVIDER_ID.fullmatch(path):
            raise APIError(400, "Некорректный номер платежа.")
        auth = base64.b64encode((self.config.payment_shop_id+":"+self.config.payment_secret).encode()).decode()
        headers = {"Authorization": "Basic "+auth, "Accept": "application/json", "Content-Type": "application/json"}
        if key:
            headers["Idempotence-Key"] = key
        request = Request(PROVIDER_API+("/"+path if path else ""),
                          data=encoded(payload).encode() if payload is not None else None,
                          headers=headers, method=method)
        try:
            with build_opener(NoRedirect()).open(request, timeout=20) as response:
                raw = response.read(262145)
            if len(raw) > 262144:
                raise ValueError("Response too large")
            value = json.loads(raw)
            if not isinstance(value, dict):
                raise ValueError("Invalid object")
            return value
        except HTTPError as error:
            # Never reflect authentication headers, provider internals or receipt data.
            raise APIError(502, "Платёжный сервис не подтвердил запрос. Повтори проверку этого же платежа.",
                           "payment_provider_error") from error
        except (URLError, TimeoutError, OSError, ValueError) as error:
            raise APIError(502, "Не удалось получить ответ платёжного сервиса. Повтор не создаст новый платёж.",
                           "payment_provider_error") from error

    def _payment_row(self, conn, payment_id):
        row = conn.execute("""SELECT p.*,i.cm_amount,i.method,i.confirmation_url,
            i.last_checked_at FROM payments p JOIN payment_intents i ON i.payment_id=p.id WHERE p.id=?""",
                           (payment_id,)).fetchone()
        if not row:
            raise APIError(404, "Платёж не найден.")
        return dict(row)

    def public_payment(self, row):
        return {"id":row["id"], "playerId":row["user_id"], "user_id":row["user_id"],
                "amount":row["cm_amount"], "rubles":row["amount"], "currency":"RUB",
                "status":row["status"], "method":row["method"], "at":row["created_at"],
                "created_at":row["created_at"], "checkoutUrl":row["confirmation_url"],
                "test":self.config.payment_test}

    def create_payment(self, body, user, session):
        self.require(user, session)
        self._payment_enabled()
        if set(body)-{"amount","method","idempotencyKey"}:
            raise APIError(400, "Неизвестное поле платежа.")
        cm = integer(body.get("amount"), 1, 100000, "amount")
        method = body.get("method")
        if method not in ("sbp","card"):
            raise APIError(400, "Выбери способ оплаты.")
        key = idem(body)
        request_hash = hashlib.sha256(encoded({"cm":cm,"method":method}).encode()).hexdigest()
        if not self.store.rate("payment-create:"+user["id"], 12, 60000):
            raise APIError(429, "Слишком много запросов оплаты.", "rate_limited")
        at = now_ms()
        with self.store.transaction() as conn:
            actor = self.actor_current(conn,user,session)
            old = conn.execute("SELECT * FROM payment_intents WHERE user_id=? AND idempotency_key=?",
                               (actor["id"], key)).fetchone()
            if old:
                if old["request_hash"] != request_hash:
                    raise APIError(409, "Состав платежа изменился.", "idempotency_conflict")
                payment_id = old["payment_id"]
            else:
                payment_id = identifier()
                conn.execute("INSERT INTO payments VALUES(?,?,?,?,?,?,?)",
                             (payment_id,actor["id"],None,cm,"RUB","creating",at))
                conn.execute("INSERT INTO payment_intents VALUES(?,?,?,?,?,?,?,?,?)",
                             (payment_id,actor["id"],key,request_hash,cm,method,None,at,None))
                self.store.audit(conn,actor["id"],"payment.created",payment_id,"Оформление пополнения",{"cm":cm,"rubles":cm})
            row = self._payment_row(conn,payment_id)
            email = actor["email"]
        if row["provider_reference"] or row["status"] in TERMINAL:
            return self.public_payment(row)
        # YooKassa guarantees a create key for 24h. Never retry an unknown create after that window.
        if now_ms()-row["created_at"] > 23*3600000:
            raise APIError(409, "Старый запрос оплаты требует проверки администрацией. Создай обращение с номером "+payment_id,
                           "payment_unknown")
        payload = {"amount":{"value":str(cm)+".00","currency":"RUB"},
                   "payment_method_data":{"type":"bank_card" if method=="card" else "sbp"},
                   "confirmation":{"type":"redirect","return_url":self.config.origin+"/#payment/"+payment_id},
                   "capture":True,"description":"CopiMine: "+str(cm)+" CM",
                   "metadata":{"copimine_payment_id":payment_id,"copimine_account_id":user["id"]}}
        if self.config.payment_receipt:
            receipt = dict(self.config.payment_receipt)
            tax_system = receipt.pop("tax_system_code", None)
            payload["receipt"] = {"customer":{"email":email}, "items":[{
                "description":"Пополнение CopiMine, "+str(cm)+" CM",
                "quantity":"1.00","amount":{"value":str(cm)+".00","currency":"RUB"}, **receipt}]}
            if tax_system is not None:
                payload["receipt"]["tax_system_code"] = tax_system
        provider = self._provider_request("POST",payload=payload,key=payment_id)
        self._apply_provider(payment_id,provider)
        with self.store.connect() as conn:
            return self.public_payment(self._payment_row(conn,payment_id))

    def _apply_provider(self, payment_id, provider):
        """Apply one authenticated provider response atomically; repeated/out-of-order hooks cannot credit twice."""
        provider_id = provider.get("id")
        if not isinstance(provider_id,str) or not PROVIDER_ID.fullmatch(provider_id):
            raise APIError(502, "Платёжный сервис вернул некорректный ответ.", "payment_provider_error")
        with self.store.transaction() as conn:
            row = self._payment_row(conn,payment_id)
            meta = provider.get("metadata") or {}
            try:
                amount = Decimal(str(provider.get("amount",{}).get("value")))
            except InvalidOperation:
                amount = Decimal("-1")
            valid = (
                amount.is_finite() and amount == Decimal(row["amount"]) and
                provider.get("amount",{}).get("currency") == "RUB" and
                str((provider.get("recipient") or {}).get("account_id")) == self.config.payment_shop_id and
                meta.get("copimine_payment_id") == payment_id and
                meta.get("copimine_account_id") == row["user_id"] and
                provider.get("test") is self.config.payment_test and
                row["provider_reference"] in (None,provider_id))
            if not valid:
                raise APIError(409, "Платёж не прошёл проверку суммы или получателя.", "payment_mismatch")
            status = provider.get("status")
            if status not in ("pending","waiting_for_capture","succeeded","canceled"):
                raise APIError(502, "Неизвестный статус платёжного сервиса.", "payment_provider_error")
            at = now_ms()
            url = confirmation_url((provider.get("confirmation") or {}).get("confirmation_url"))
            if row["status"] in TERMINAL:
                # A final local state cannot be rolled back by an old webhook.
                return self.public_payment(row)
            next_status = "pending"
            if status == "canceled":
                next_status = "cancelled"
            elif status == "succeeded":
                if provider.get("paid") is not True:
                    raise APIError(409,"Платёж ещё не подтверждён.","payment_mismatch")
                next_status = "test_completed" if self.config.payment_test else "completed"
                if not self.config.payment_test:
                    account = conn.execute("SELECT * FROM users WHERE id=?",(row["user_id"],)).fetchone()
                    balance = account["cm"] + row["cm_amount"]
                    if balance > 10**12:
                        raise APIError(409,"Пополнение превышает лимит баланса.","balance_limit")
                    conn.execute("UPDATE users SET cm=?,updated_at=? WHERE id=?",(balance,at,row["user_id"]))
                    conn.execute("INSERT INTO ledger VALUES(?,?,?,?,?,?,?,?,?,?)",
                        (identifier(),row["user_id"],"CM",row["cm_amount"],balance,"donation",payment_id,
                         None,"Подтверждённое пополнение",at))
                    self.store.audit(conn,None,"payment.completed",payment_id,"Подтверждение ЮKassa",
                                     {"cm":row["cm_amount"],"rubles":row["amount"],"userId":row["user_id"]})
                    self.queue_notice(conn,row["user_id"],"payment",payment_id,{"amount":row["cm_amount"],"currency":"CM"})
            conn.execute("UPDATE payments SET provider_reference=?,status=? WHERE id=?",(provider_id,next_status,payment_id))
            conn.execute("UPDATE payment_intents SET confirmation_url=COALESCE(?,confirmation_url),updated_at=?,last_checked_at=? WHERE payment_id=?",
                         (url,at,at,payment_id))
            return self.public_payment(self._payment_row(conn,payment_id))

    def refresh_payment(self, payment_id, user, session):
        self.require(user,session)
        with self.store.connect() as conn:
            row = self._payment_row(conn,payment_id)
        from .security import PERMISSIONS
        if row["user_id"] != user["id"] and "economy.manage" not in PERMISSIONS.get(user["role"],set()):
            raise APIError(404,"Платёж не найден.")
        if row["status"] in TERMINAL:
            return self.public_payment(row)
        if not self.store.rate("payment-check:"+user["id"], 30, 60000):
            raise APIError(429,"Проверка уже выполнялась. Подожди немного.","rate_limited")
        if row["provider_reference"]:
            provider = self._provider_request("GET",row["provider_reference"])
            return self._apply_provider(payment_id,provider)
        return self.public_payment(row)

    def payment_webhook(self, body, ip):
        self._payment_enabled()
        if not self.store.rate("payment-hook:"+digest(str(ip)), 120, 60000):
            raise APIError(429,"Rate limit.","rate_limited")
        if body.get("type") != "notification" or body.get("event") not in (
            "payment.succeeded","payment.canceled","payment.waiting_for_capture"):
            return {"accepted":True}
        object_hint=body.get("object")
        if not isinstance(object_hint,dict):
            raise APIError(400,"Invalid payment notification.")
        provider_id = object_hint.get("id")
        if not isinstance(provider_id,str) or not PROVIDER_ID.fullmatch(provider_id):
            raise APIError(400,"Invalid payment notification.")
        with self.store.connect() as conn:
            row = conn.execute("SELECT id,status FROM payments WHERE provider_reference=?",(provider_id,)).fetchone()
        if not row:
            hint=object_hint.get("metadata")
            candidate=hint.get("copimine_payment_id") if isinstance(hint,dict) else None
            if isinstance(candidate,str) and PROVIDER_ID.fullmatch(candidate):
                with self.store.connect() as conn:
                    row=conn.execute("SELECT id,status FROM payments WHERE id=? AND provider_reference IS NULL",(candidate,)).fetchone()
        if not row or row["status"] in TERMINAL:
            return {"accepted":True}
        # No fields (status, amount, metadata) supplied by the caller are trusted.
        provider = self._provider_request("GET",provider_id)
        self._apply_provider(row["id"],provider)
        return {"accepted":True}

    def reconcile_payments(self, limit=10):
        if not self.payment_configured():
            return {"checked":0,"configured":False}
        with self.store.connect() as conn:
            rows=conn.execute("""SELECT p.id,p.provider_reference FROM payments p JOIN payment_intents i ON i.payment_id=p.id
                WHERE p.status='pending' AND p.provider_reference IS NOT NULL
                AND COALESCE(i.last_checked_at,0)<? ORDER BY COALESCE(i.last_checked_at,0) LIMIT ?""",
                              (now_ms()-60000,limit)).fetchall()
        checked=0
        for row in rows:
            try:
                self._apply_provider(row["id"],self._provider_request("GET",row["provider_reference"]))
                checked+=1
            except APIError:
                with self.store.transaction() as conn:
                    conn.execute("UPDATE payment_intents SET last_checked_at=? WHERE payment_id=?",(now_ms(),row["id"]))
        return {"checked":checked,"configured":True}

