from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PLUGIN = ROOT / "copimine-end-event"
WORLDCORE = ROOT / "copimine-world-core" / "src" / "me" / "copimine" / "worldcore" / "CopiMineWorldCore.java"
ARTIFACTS = ROOT / "copimine-artifacts" / "src" / "me" / "copimine" / "artifacts" / "CopiMineArtifacts.java"


def test_end_event_plugin_and_typed_boundaries_exist() -> None:
    assert (PLUGIN / "plugin.yml").is_file()
    assert (PLUGIN / "config.yml").is_file()
    assert (PLUGIN / "build-plugin.ps1").is_file()
    assert (PLUGIN / "src" / "me" / "copimine" / "endevent" / "CopiMineEndEvent.java").is_file()
    assert (ROOT / "copimine-world-core" / "src" / "me" / "copimine" / "worldcore" / "api" / "WorldAccessService.java").is_file()
    assert (ROOT / "copimine-artifacts" / "src" / "me" / "copimine" / "artifacts" / "api" / "EventArtifactRewardService.java").is_file()


def test_end_event_keeps_world_and_reward_authority_typed() -> None:
    source = "\n".join(path.read_text(encoding="utf-8") for path in PLUGIN.rglob("*.java"))
    assert "Bukkit.dispatchCommand" not in source
    assert "cmworld end open" not in source
    assert "end_locked" not in source
    assert "ClientGateService" not in source
    assert "clientgate" not in source.lower()
    assert "CopiMineEconomyCore" not in source
    assert "artifact_item_id" not in source or "reject" in source.lower()


def test_worldcore_and_artifacts_have_no_end_event_duplicate_truth() -> None:
    worldcore = WORLDCORE.read_text(encoding="utf-8")
    artifacts = ARTIFACTS.read_text(encoding="utf-8")
    assert "world_access.end.enabled" in worldcore
    assert "createOfficialItem" in artifacts
    assert "artifact_unique_item_id" in artifacts
