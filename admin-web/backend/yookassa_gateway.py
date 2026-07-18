"""Small, testable YooKassa HTTP adapter used by the donation payment flow."""
from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal, InvalidOperation
import os
import re
from typing import Any, Callable, Mapping
from urllib.parse import urlparse

try:
    import httpx  # type: ignore
except Exception:  # pragma: no cover
    httpx = None


class YooKassaGatewayError(RuntimeError):
    """Provider error safe to surface as a bounded API response."""

    def __init__(self, code: str, message: str) -> None:
        super().__init__(message)
        self.code = code


@dataclass(frozen=True)
class YooKassaSettings:
    enabled: bool
    shop_id: str
    secret_key: str
    api_base_url: str
    return_url: str

    @property
    def configured(self) -> bool:
        return self.enabled and bool(self.shop_id and self.secret_key and self.return_url)

    @classmethod
    def from_env(cls) -> "YooKassaSettings":
        return cls(
            enabled=os.getenv("YOOKASSA_ENABLED", "0").strip().lower() in {"1", "true", "yes", "on"},
            shop_id=os.getenv("YOOKASSA_SHOP_ID", "").strip(),
            secret_key=os.getenv("YOOKASSA_SECRET_KEY", "").strip(),
            api_base_url=os.getenv("YOOKASSA_API_BASE_URL", "https://api.yookassa.ru/v3").strip().rstrip("/"),
            return_url=os.getenv("YOOKASSA_RETURN_URL", "").strip(),
        )


@dataclass(frozen=True)
class YooKassaPayment:
    payment_id: str
    status: str
    paid: bool
    amount_rub: int
    confirmation_url: str
    payload: dict[str, Any]


class YooKassaGateway:
    """Verifies provider state server-side instead of trusting browser/webhook data."""

    def __init__(self, settings: YooKassaSettings, client_factory: Callable[[], Any] | None = None) -> None:
        self.settings = settings
        self._client_factory = client_factory or self._default_client

    @staticmethod
    def _default_client() -> Any:
        if httpx is None:
            raise YooKassaGatewayError("http_client_missing", "Платёжный HTTP-клиент недоступен")
        return httpx.Client(timeout=httpx.Timeout(12.0, connect=5.0), follow_redirects=False)

    @staticmethod
    def _is_https_url(value: str) -> bool:
        parsed = urlparse(str(value or "").strip())
        return parsed.scheme.lower() == "https" and bool(parsed.netloc)

    def _require_configured(self) -> None:
        if not self.settings.configured:
            raise YooKassaGatewayError("not_configured", "Оплата через ЮKassa пока не настроена")
        if not self._is_https_url(self.settings.api_base_url):
            raise YooKassaGatewayError("invalid_config", "Адрес API ЮKassa должен быть HTTPS-адресом")
        if not self._is_https_url(self.settings.return_url):
            raise YooKassaGatewayError("invalid_config", "Адрес возврата ЮKassa должен быть HTTPS-адресом")

    @staticmethod
    def _amount_value(amount_rub: int) -> str:
        safe_amount = int(amount_rub or 0)
        if safe_amount <= 0:
            raise YooKassaGatewayError("invalid_amount", "Сумма платежа должна быть больше нуля")
        return f"{safe_amount}.00"

    @staticmethod
    def _extract_amount(payload: Mapping[str, Any], expected_amount_rub: int) -> int:
        amount = payload.get("amount")
        if not isinstance(amount, Mapping):
            raise YooKassaGatewayError("invalid_provider_response", "ЮKassa вернула платёж без суммы")
        if str(amount.get("currency") or "").upper() != "RUB":
            raise YooKassaGatewayError("currency_mismatch", "ЮKassa вернула платёж в неверной валюте")
        try:
            value = Decimal(str(amount.get("value") or ""))
        except (InvalidOperation, ValueError):
            raise YooKassaGatewayError("invalid_provider_response", "ЮKassa вернула некорректную сумму") from None
        if value != Decimal(int(expected_amount_rub)):
            raise YooKassaGatewayError("amount_mismatch", "Сумма платежа ЮKassa не совпадает с сессией")
        return int(value)

    @staticmethod
    def _parse_payment(payload: Any, *, expected_amount_rub: int, expected_session_id: str, require_paid: bool) -> YooKassaPayment:
        if not isinstance(payload, Mapping):
            raise YooKassaGatewayError("invalid_provider_response", "ЮKassa вернула некорректный ответ")
        payment_id = str(payload.get("id") or "").strip()
        if len(payment_id) < 8 or len(payment_id) > 128:
            raise YooKassaGatewayError("invalid_provider_response", "ЮKassa вернула платёж без идентификатора")
        metadata = payload.get("metadata")
        if not isinstance(metadata, Mapping) or str(metadata.get("copimine_session_id") or "") != expected_session_id:
            raise YooKassaGatewayError("metadata_mismatch", "Платёж ЮKassa не принадлежит этой сессии")
        status = str(payload.get("status") or "").strip().lower()
        paid = bool(payload.get("paid"))
        if require_paid and (status != "succeeded" or not paid):
            raise YooKassaGatewayError("not_paid", "ЮKassa ещё не подтвердила оплату")
        amount_rub = YooKassaGateway._extract_amount(payload, expected_amount_rub)
        confirmation = payload.get("confirmation")
        confirmation_url = ""
        if isinstance(confirmation, Mapping):
            confirmation_url = str(confirmation.get("confirmation_url") or "").strip()
        if confirmation_url and not YooKassaGateway._is_https_url(confirmation_url):
            raise YooKassaGatewayError("invalid_provider_response", "ЮKassa вернула небезопасный адрес оплаты")
        if not paid and status in {"pending", "waiting_for_capture"} and not confirmation_url:
            raise YooKassaGatewayError("missing_confirmation", "ЮKassa не вернула ссылку на оплату")
        return YooKassaPayment(payment_id, status, paid, amount_rub, confirmation_url, dict(payload))

    def _request(self, method: str, path: str, *, headers: Mapping[str, str] | None = None, payload: dict[str, Any] | None = None) -> dict[str, Any]:
        self._require_configured()
        client = self._client_factory()
        try:
            response = client.request(
                method,
                f"{self.settings.api_base_url}{path}",
                auth=(self.settings.shop_id, self.settings.secret_key),
                headers={"Accept": "application/json", **dict(headers or {})},
                json=payload,
            )
        except YooKassaGatewayError:
            raise
        except Exception as exc:
            raise YooKassaGatewayError("provider_unavailable", "Не удалось связаться с ЮKassa") from exc
        finally:
            close = getattr(client, "close", None)
            if callable(close):
                close()
        if int(getattr(response, "status_code", 0) or 0) >= 400:
            raise YooKassaGatewayError("provider_rejected", "ЮKassa отклонила запрос оплаты")
        try:
            result = response.json()
        except Exception as exc:
            raise YooKassaGatewayError("invalid_provider_response", "ЮKassa вернула некорректный ответ") from exc
        if not isinstance(result, dict):
            raise YooKassaGatewayError("invalid_provider_response", "ЮKassa вернула некорректный ответ")
        return result

    def create_payment(
        self,
        idempotency_key: str,
        amount_rub: int,
        description: str,
        session_id: str,
        player_uuid: str,
    ) -> YooKassaPayment:
        safe_key = str(idempotency_key or "").strip()
        if len(safe_key) < 8 or len(safe_key) > 120:
            raise YooKassaGatewayError("invalid_idempotency_key", "Некорректный ключ платежа")
        safe_session = str(session_id or "").strip()
        if not safe_session:
            raise YooKassaGatewayError("invalid_session", "Не задана платёжная сессия")
        result = self._request(
            "POST",
            "/payments",
            headers={"Idempotence-Key": safe_key, "Content-Type": "application/json"},
            payload={
                "amount": {"value": self._amount_value(amount_rub), "currency": "RUB"},
                "capture": True,
                "confirmation": {"type": "redirect", "return_url": self.settings.return_url},
                "description": str(description or "Пополнение CopiMine Donation")[:128],
                "metadata": {
                    "copimine_session_id": safe_session,
                    "copimine_player_uuid": str(player_uuid or "")[:64],
                },
            },
        )
        return self._parse_payment(result, expected_amount_rub=int(amount_rub), expected_session_id=safe_session, require_paid=False)

    def verify_succeeded_payment(self, payment_id: str, expected_amount_rub: int, expected_session_id: str) -> YooKassaPayment:
        safe_payment_id = str(payment_id or "").strip()
        if not re.fullmatch(r"[A-Za-z0-9_-]{8,128}", safe_payment_id):
            raise YooKassaGatewayError("invalid_payment", "Некорректный идентификатор платежа")
        result = self._request("GET", f"/payments/{safe_payment_id}")
        payment = self._parse_payment(result, expected_amount_rub=int(expected_amount_rub), expected_session_id=str(expected_session_id or ""), require_paid=True)
        if payment.payment_id != safe_payment_id:
            raise YooKassaGatewayError("payment_mismatch", "ЮKassa вернула другой платёж")
        return payment
