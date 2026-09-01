from __future__ import annotations

import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "admin-web"))

from backend.db_config import resolve_postgres_settings  # noqa: E402
from backend.startup_checks import _auth_storage_backend  # noqa: E402


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def test_legacy_mods_redirect_is_external_and_csp_safe() -> None:
    page = read("admin-web/frontend/mods.html")
    script = read("admin-web/frontend/assets/js/public/legacy-mods-redirect.js")

    assert "legacy-mods-redirect.js" in page
    assert "window.location.replace" not in page
    assert "window.location.replace('/launcher.html')" in script


def test_database_url_is_a_supported_runtime_configuration() -> None:
    values = resolve_postgres_settings(
        {"DATABASE_URL": "postgresql://user:pass@example.test:5544/game?schema=public"}
    )

    assert values["POSTGRES_HOST"] == "example.test"
    assert values["POSTGRES_PORT"] == "5544"
    assert values["POSTGRES_DB"] == "game"
    assert values["POSTGRES_USER"] == "user"
    assert values["POSTGRES_PASSWORD"] == "pass"
    assert values["POSTGRES_SCHEMA"] == "public"
    assert _auth_storage_backend({"DATABASE_URL": "postgresql://user:pass@example.test/game"}) == "postgresql"


def test_explicit_postgres_fields_win_over_database_url() -> None:
    values = resolve_postgres_settings(
        {
            "DATABASE_URL": "postgresql://url-user:url-pass@url-host/url-db",
            "POSTGRES_HOST": "explicit-host",
            "POSTGRES_DB": "explicit-db",
        }
    )

    assert values["POSTGRES_HOST"] == "explicit-host"
    assert values["POSTGRES_DB"] == "explicit-db"
    assert values["POSTGRES_USER"] == "url-user"
