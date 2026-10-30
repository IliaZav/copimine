"""Production examples must fail closed until operators provide real settings."""
from pathlib import Path
import re

import pytest
from backend.config import Settings
from backend.game import catalog_version
from backend.antivirus import TIMEOUT

ROOT = Path(__file__).resolve().parents[1]


@pytest.mark.parametrize("key", ["REPLACE_WITH_RANDOM_PERSISTENT_48_BYTE_SECRET", "x" * 48, "changeme" * 8, " " * 48])
def test_obvious_template_secret_cannot_start_production(monkeypatch, key):
    monkeypatch.setenv("DATABASE_URL", "postgresql://synthetic@localhost/synthetic")
    monkeypatch.setenv("SECRET_KEY", key)
    with pytest.raises(RuntimeError, match="SECRET_KEY"):
        Settings.from_env()


@pytest.mark.parametrize("origin", ["http://copimine.ru", "https://user:pass@copimine.ru", "https://copimine.ru/path", "https://copimine.ru?x=1", "https://copimine.ru#fragment", "not-an-origin"])
def test_public_origin_must_match_secure_cookie_and_csrf_contract(monkeypatch, origin):
    monkeypatch.setenv("DATABASE_URL", "postgresql://synthetic@localhost/synthetic")
    monkeypatch.setenv("SECRET_KEY", "d82e5c7349f0ab163ee4d7b1ef9064283159c6fe83ba0217")
    monkeypatch.setenv("SITE_PUBLIC_URL", origin)
    with pytest.raises(RuntimeError, match="SITE_PUBLIC_URL"):
        Settings.from_env()


def test_normal_https_production_origin_is_preserved(monkeypatch):
    monkeypatch.setenv("DATABASE_URL", "postgresql://synthetic@localhost/synthetic")
    monkeypatch.setenv("SECRET_KEY", "d82e5c7349f0ab163ee4d7b1ef9064283159c6fe83ba0217")
    monkeypatch.setenv("SITE_PUBLIC_URL", "https://copimine.ru/")
    assert Settings.from_env().public_url == "https://copimine.ru"


def test_changed_catalog_image_changes_optimistic_revision():
    item = {"id": "stone", "price": 10, "image": "assets/a.png"}
    assert catalog_version(item) != catalog_version({**item, "image": "assets/b.png"})
    assert catalog_version(item) == catalog_version(dict(item))


def test_proxy_accepts_backend_maximum_json_upload_and_scan_time():
    config = (ROOT / "deploy/nginx.conf").read_text(encoding="utf-8")
    maximum = re.search(r"client_max_body_size\s+(\d+)m;", config)
    assert maximum and int(maximum.group(1)) >= 12, "8 MiB binary video requires about 11 MiB JSON/base64"
    timeout = re.search(r"proxy_read_timeout\s+(\d+)s;", config)
    assert timeout and int(timeout.group(1)) > TIMEOUT, "Proxy must allow the bounded antivirus scan to return"
