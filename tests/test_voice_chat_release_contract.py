"""Release contracts for the server/client Simple Voice Chat pair."""

from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PACKAGE_SCRIPT = ROOT / "scripts" / "package_full_release.ps1"
PLUGIN_VERSIONS = ROOT / "deploy" / "plugin_versions.json"
VOICE_TEMPLATE = ROOT / "deploy" / "templates" / "voicechat-server.properties"


def _properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def test_release_uses_the_same_voicechat_version_as_the_client_mod():
    package = PACKAGE_SCRIPT.read_text(encoding="utf-8")
    versions = json.loads(PLUGIN_VERSIONS.read_text(encoding="utf-8"))

    assert "voicechat-bukkit-2.6.16.jar" in package
    assert "voicechat-bukkit-2.6.11.jar" not in package
    assert versions["plugins"]["voicechat-bukkit-2.6.16.jar"] == "2.6.16"
    assert "voicechat-bukkit-2.6.11.jar" not in versions["plugins"]


def test_managed_voicechat_endpoint_is_explicit_and_uses_the_dedicated_udp_port():
    values = _properties(VOICE_TEMPLATE)

    assert values["port"] == "24454"
    assert values["bind_address"] == "*"
    assert values["voice_host"] == "mc.copimine.ru"
    assert values["force_voice_chat"] == "false"
