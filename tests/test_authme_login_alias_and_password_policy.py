from __future__ import annotations

import json
import re
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
AUTHME_CONFIG = ROOT / "minecraft" / "server" / "plugins" / "AuthMe" / "config.yml"
AUTHME_JAR = next((ROOT / "minecraft" / "server" / "plugins").glob("AuthMe-*.jar"))
HARDENING_POLICY = ROOT / "deploy" / "templates" / "game-runtime-hardening.json"
HARDENING_SCRIPT = ROOT / "deploy" / "shared" / "harden_game_runtime.py"


def _authme_min_password_length() -> int:
    text = AUTHME_CONFIG.read_text(encoding="utf-8")
    match = re.search(r"(?m)^\s*minPasswordLength:\s*(\d+)\s*$", text)
    assert match, "AuthMe security.minPasswordLength is missing"
    return int(match.group(1))


def test_authme_uses_the_five_character_minimum_in_runtime_and_managed_policy() -> None:
    assert _authme_min_password_length() == 5
    policy = json.loads(HARDENING_POLICY.read_text(encoding="utf-8"))
    assert policy["authme"]["settings"]["security"]["minPasswordLength"] == 5

    hardening = HARDENING_SCRIPT.read_text(encoding="utf-8")
    assert 'get("minPasswordLength") != 5' in hardening
    assert '"minPasswordLength: 5"' in hardening


def test_authme_exposes_log_as_the_login_alias_and_allows_it_before_login() -> None:
    with zipfile.ZipFile(AUTHME_JAR) as archive:
        plugin_yml = archive.read("plugin.yml").decode("utf-8")

    login_block = re.search(
        r"(?ms)^  login:\s*\n(.*?)(?=^  \S|\Z)",
        plugin_yml,
    )
    assert login_block, "AuthMe login command descriptor is missing"
    assert re.search(r"(?m)^\s*-\s*log\s*$", login_block.group(1))

    config = AUTHME_CONFIG.read_text(encoding="utf-8")
    assert re.search(r"(?m)^\s{8}-\s*/log\s*$", config), (
        "AuthMe must allow the /log alias before authentication"
    )
