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
