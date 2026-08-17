from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MODPACK_MANIFEST = ROOT / "thirdparty/modpack_manifest.json"
MODPACK_BUILDER = ROOT / "scripts/thirdparty/build_modpack.ps1"
MODPACK_BUILDER_BASH = ROOT / "scripts/thirdparty/build_modpack.sh"


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


def test_modpack_builder_keeps_the_manifest_and_user_facing_install_documents() -> None:
    builder = MODPACK_BUILDER.read_text(encoding="utf-8")
    builder_bash = MODPACK_BUILDER_BASH.read_text(encoding="utf-8")

    for relative_path in (
        "thirdparty\\modpack_manifest.json",
        "thirdparty\\README_RU.txt",
        "thirdparty\\VOICE_CHAT_OFFICIAL_DOWNLOAD.txt",
    ):
        assert relative_path in builder

    for relative_path in (
        "thirdparty/modpack_manifest.json",
        "thirdparty/README_RU.txt",
        "thirdparty/VOICE_CHAT_OFFICIAL_DOWNLOAD.txt",
    ):
        assert relative_path in builder_bash
