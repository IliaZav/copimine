#!/usr/bin/env python3
"""Static safety checks for the CopiMine admin package.

This script does not read production .env/data files. It checks only code,
templates and docs that are safe to ship.
"""
from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FRONTEND_FILES = [
    ROOT / "frontend" / "index.html",
    ROOT / "frontend" / "assets" / "app.js",
    ROOT / "frontend" / "assets" / "style.css",
]
SAFE_SHIP_FILES = [
    ROOT / "backend" / "main.py",
    ROOT / ".env.example",
    ROOT / "README_RU.md",
]


def assert_not_contains(path: Path, needles: list[str]) -> None:
    text = path.read_text(encoding="utf-8", errors="replace")
    for needle in needles:
        assert needle not in text, f"{path} contains forbidden marker {needle}"


def main() -> None:
    frontend_forbidden = [
        "DISCORD_BOT_TOKEN",
        "RCON_PASSWORD",
        "SECRET_KEY",
        "PLUGIN_API_KEY",
        "DISCORD_BOT_API_KEY",
        "ADMIN_USERS_JSON",
    ]
    for path in FRONTEND_FILES:
        assert path.exists(), path
        assert_not_contains(path, frontend_forbidden)

    for path in SAFE_SHIP_FILES:
        assert path.exists(), path
    backend = (ROOT / "backend" / "main.py").read_text(encoding="utf-8", errors="replace")
    assert "DEFAULT_ADMIN_USERS: dict[str, dict[str, str]] = {}" in backend
    assert '"password_hash": "sha256:' not in backend
    assert "pbkdf2_sha256$" in backend

    js = (ROOT / "frontend" / "assets" / "app.js").read_text(encoding="utf-8", errors="replace").lower()
    forbidden_economy_actions = ["выдать ары", "забрать ары", "обнулить экономику", "изменить баланс"]
    for phrase in forbidden_economy_actions:
        assert phrase not in js, f"Forbidden economy UI action found: {phrase}"

    print("Security selftest OK")


if __name__ == "__main__":
    main()
