from __future__ import annotations

import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "admin-web"))

from backend.startup_checks import _auth_storage_backend  # noqa: E402


def test_startup_checks_match_runtime_default_without_postgres_secret() -> None:
    assert _auth_storage_backend({}) == "sqlite"
    assert _auth_storage_backend({"POSTGRES_PASSWORD": ""}) == "sqlite"


def test_explicit_backend_selection_is_preserved() -> None:
    assert _auth_storage_backend({"COPIMINE_AUTH_STORAGE": "sqlite", "POSTGRES_PASSWORD": "secret"}) == "sqlite"
    assert _auth_storage_backend({"COPIMINE_AUTH_STORAGE": "postgresql", "POSTGRES_PASSWORD": "secret"}) == "postgresql"
