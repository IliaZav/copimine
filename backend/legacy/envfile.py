from __future__ import annotations

import os
import re
from pathlib import Path

ENV_LINE_RE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*=.*$")


def resolve_env_file(default_path: Path | None = None) -> Path:
    configured = str(os.getenv("COPIMINE_ENV_FILE", "")).strip()
    if configured:
        return Path(configured)
    if default_path is not None:
        return default_path
    return Path("/opt/copimine/admin-web/.env")


def parse_env_file(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.exists():
        return values
    for raw in path.read_text(encoding="utf-8", errors="replace").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        if not ENV_LINE_RE.match(line):
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip().strip('"').strip("'")
    return values


def load_env_file_to_os(path: Path | None = None) -> dict[str, str]:
    env_path = resolve_env_file(path)
    values = parse_env_file(env_path)
    for key, value in values.items():
        os.environ.setdefault(key, value)
    return values
