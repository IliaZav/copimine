from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MODPACK_MANIFEST = ROOT / "thirdparty/modpack_manifest.json"


def test_default_launcher_mod_policy_requires_only_the_seven_official_mods() -> None:
    document = json.loads(MODPACK_MANIFEST.read_text(encoding="utf-8"))
    entries = {entry["component"]: entry for entry in document["files"]}

    required = {
        "CopiMineClient",
        "CustomSkinLoader",
        "Emotecraft",
        "Fabric API",
        "Simple Voice Chat",
        "Iris",
        "Sodium",
    }

    assert set(entries) == required | {"Mod Menu"}
    assert {name for name, entry in entries.items() if entry.get("required") is True} == required
    assert entries["Mod Menu"]["required"] is False


def test_default_launcher_mod_policy_has_no_other_managed_client_mods() -> None:
    document = json.loads(MODPACK_MANIFEST.read_text(encoding="utf-8"))

    assert all(entry["path"].startswith("mods/") for entry in document["files"])
    assert len(document["files"]) == 8
    assert document["requiredExternal"] == []
