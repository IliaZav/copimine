from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PACKAGE = (ROOT / "scripts/package_full_release.ps1").read_text(encoding="utf-8")


def test_full_release_builds_and_carries_end_event_server_jar() -> None:
    assert "copimine-end-event\\build-plugin.ps1" in PACKAGE
    assert "minecraft/server/plugins/CopiMineEndEvent.jar" in PACKAGE
    assert "minecraft\\server\\plugins\\CopiMineEndEvent.jar" in PACKAGE


def test_release_contract_does_not_expand_into_launcher_or_website_changes() -> None:
    assert "launcher" not in PACKAGE.lower()
    assert "admin-web\\frontend" in PACKAGE  # existing release metadata boundary only
