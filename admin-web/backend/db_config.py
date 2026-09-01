"""Resolve the supported PostgreSQL environment shapes in one place."""

from __future__ import annotations

from collections.abc import Mapping
from urllib.parse import parse_qs, unquote, urlsplit


_DEFAULTS = {
    "POSTGRES_HOST": "127.0.0.1",
    "POSTGRES_PORT": "5432",
    "POSTGRES_DB": "copimine",
    "POSTGRES_USER": "copimine",
    "POSTGRES_SCHEMA": "copimine",
}


def _url_values(raw_url: str) -> dict[str, str]:
    if not raw_url.strip():
        return {}
    try:
        parsed = urlsplit(raw_url.strip())
        if parsed.scheme.lower() not in {"postgres", "postgresql"} or not parsed.hostname:
            return {}
        port = parsed.port
    except ValueError:
        return {}

    query = parse_qs(parsed.query, keep_blank_values=True)
    database = unquote(parsed.path.lstrip("/")).strip()
    values = {
        "POSTGRES_HOST": parsed.hostname,
        "POSTGRES_PORT": str(port or 5432),
        "POSTGRES_DB": database,
        "POSTGRES_USER": unquote(parsed.username or "").strip(),
        "POSTGRES_PASSWORD": unquote(parsed.password or ""),
        "POSTGRES_SCHEMA": (query.get("schema") or [""])[0].strip(),
    }
    return {key: value for key, value in values.items() if value}


def resolve_postgres_settings(values: Mapping[str, object]) -> dict[str, str]:
    """Return explicit POSTGRES_* settings, filling gaps from DATABASE_URL.

    Explicit fields always win. This lets existing production environments keep
    their current precedence while making the documented DATABASE_URL shape
    actually usable for local and staging installations.
    """

    resolved = {key: str(value or "").strip() for key, value in values.items()}
    url_values = _url_values(resolved.get("DATABASE_URL", ""))
    for key, value in url_values.items():
        if not resolved.get(key):
            resolved[key] = value
    for key, default in _DEFAULTS.items():
        if not resolved.get(key):
            resolved[key] = default
    return resolved
