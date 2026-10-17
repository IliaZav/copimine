"""Validated configuration and request primitives."""
from dataclasses import dataclass, field
from pathlib import Path
import math
import re
from urllib.parse import urlsplit
from .storage import now_ms

class APIError(Exception):
    def __init__(self, status, message, code="invalid_request"):
        super().__init__(message)
        self.status, self.message, self.code = status, message, code

def integer(value, minimum=0, maximum=10**12, label="value"):
    if isinstance(value, bool) or not isinstance(value, int) or not minimum <= value <= maximum:
        raise APIError(400, f"{label}: expected integer from {minimum} to {maximum}.")
    return value

def text(value, minimum=1, maximum=1000, label="value"):
    if not isinstance(value, str) or not minimum <= len(value.strip()) <= maximum or "\x00" in value:
        raise APIError(400, f"{label}: invalid text.")
    return value.strip()

def reason(body):
    return text(body.get("reason"), 3, 1000, "reason")

def idem(body):
    value = text(body.get("idempotencyKey"), 8, 128, "idempotencyKey")
    if not re.fullmatch(r"[A-Za-z0-9:_-]+", value):
        raise APIError(400, "Invalid idempotencyKey.")
    return value

def expiry(params):
    if params.get("expiresAt") is not None:
        return integer(params["expiresAt"], now_ms()+1000, now_ms()+366*86400000, "expiresAt")
    duration = params.get("durationMs")
    if duration in (None, 0):
        return None
    return now_ms() + integer(duration, 1000, 366*86400000, "durationMs")

@dataclass
class Config:
    site_dir: Path
    data_dir: Path
    origin: str = "https://copimine.ru"
    insecure_local: bool = False
    trust_loopback_proxy: bool = False
    adapter_url: str = ""
    adapter_secret: str = ""
    adapter_capabilities: set = field(default_factory=set)
    vk_client_id: str = ""
    notification_env: dict = field(default_factory=dict)
    notification_key: str = ""
    payment_shop_id: str = ""
    payment_secret: str = ""
    payment_test: bool = False
    payment_receipt: dict = field(default_factory=dict)
    map_source: str = ""
    worlds: dict = field(default_factory=lambda: {"overworld":"world","nether":"world_nether","end":"world_the_end"})
    def __post_init__(self):
        self.site_dir, self.data_dir = Path(self.site_dir).resolve(), Path(self.data_dir).resolve()
        uri = urlsplit(self.origin)
        if uri.scheme not in ("https","http") or not uri.hostname or uri.path not in ("","/") or uri.username or uri.query or uri.fragment:
            raise ValueError("origin must be a single HTTP(S) origin.")
        if uri.scheme == "http" and not (self.insecure_local and uri.hostname in ("localhost","127.0.0.1","::1")):
            raise ValueError("Plain HTTP is allowed only with explicit insecure_local on loopback.")
        self.origin = self.origin.rstrip("/")
        self.adapter_capabilities = set(self.adapter_capabilities)
        if self.adapter_url:
            endpoint = urlsplit(self.adapter_url)
            if endpoint.scheme != "https" and not (endpoint.scheme=="http" and endpoint.hostname in ("127.0.0.1","localhost","::1")):
                raise ValueError("Adapter must use HTTPS or loopback HTTP.")
            if endpoint.username or endpoint.query or endpoint.fragment or not endpoint.hostname or len(self.adapter_secret) < 32:
                raise ValueError("Adapter URL/secret is invalid.")


        if self.vk_client_id and (not isinstance(self.vk_client_id,str) or not re.fullmatch(r"[1-9][0-9]{0,19}",self.vk_client_id)):
            raise ValueError("VK ID client ID must be a numeric application identifier.")
        notification_keys = {"CM_NOTIFY_MODE", "CM_SMTP_HOST", "CM_SMTP_PORT", "CM_SMTP_TLS", "CM_SMTP_USER", "CM_SMTP_PASSWORD", "CM_SMTP_FROM", "CM_SMSRU_API_ID", "CM_SMSRU_SENDER", "CM_NOTIFY_EMAIL_DAILY_LIMIT", "CM_NOTIFY_SMS_DAILY_LIMIT"}
        if not isinstance(self.notification_env, dict) or set(self.notification_env) - notification_keys or any(not isinstance(v, str) or len(v)>16384 for v in self.notification_env.values()):
            raise ValueError("Invalid notification provider configuration.")
        if self.notification_env.get("CM_NOTIFY_MODE", "disabled") not in ("disabled", "live"):
            raise ValueError("The server notification mode must be disabled or live.")
        if not isinstance(self.notification_key, str) or (self.notification_key and not 32<=len(self.notification_key)<=4096):
            raise ValueError("Use a separate notification encryption key of at least 32 characters.")
        if not isinstance(self.payment_test,bool):
            raise ValueError("paymentTest must be boolean.")
        if bool(self.payment_shop_id)!=bool(self.payment_secret):
            raise ValueError("Set both paymentShopId and paymentSecret.")
        if self.payment_shop_id and (not re.fullmatch(r"[0-9]{1,30}",self.payment_shop_id) or len(self.payment_secret)<16):
            raise ValueError("Invalid payment credentials.")
        if not isinstance(self.payment_receipt,dict) or set(self.payment_receipt)-{"vat_code","payment_subject","payment_mode","tax_system_code"}:
            raise ValueError("Invalid receipt settings.")
        if self.payment_receipt:
            if type(self.payment_receipt.get("vat_code")) is not int or not 1<=self.payment_receipt["vat_code"]<=12:
                raise ValueError("Receipt vat_code must be explicitly configured.")
            if self.payment_receipt.get("payment_subject") not in ("service","commodity","payment","another") or self.payment_receipt.get("payment_mode") not in ("full_payment","full_prepayment","advance"):
                raise ValueError("Configure receipt payment_subject and payment_mode.")
            if "tax_system_code" in self.payment_receipt and (type(self.payment_receipt["tax_system_code"]) is not int or not 1<=self.payment_receipt["tax_system_code"]<=6):
                raise ValueError("Invalid receipt tax_system_code.")
