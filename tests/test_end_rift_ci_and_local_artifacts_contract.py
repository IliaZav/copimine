from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CI = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
STACK = (ROOT / "scripts/local-stack.ps1").read_text(encoding="utf-8")
SYNC = ROOT / "scripts/InstallEndRiftLocalArtifacts.ps1"


def test_ci_builds_end_event_after_typed_dependencies_and_runs_its_gate() -> None:
    assert "'copimine-world-core'" in CI
    assert "'copimine-artifacts'" in CI
    assert "'copimine-end-event'" in CI
    assert "RunEndRiftEventChecks.ps1" in CI

    world_index = CI.index("'copimine-world-core'")
    artifacts_index = CI.index("'copimine-artifacts'")
    end_event_index = CI.index("'copimine-end-event'")
    assert world_index < artifacts_index < end_event_index


def test_local_stack_rejects_a_pack_without_event_assets_or_paper_overrides() -> None:
    assert "System.IO.Compression.ZipFile" in STACK
    assert "assets/minecraft/models/item/paper.json" in STACK
    assert "copimine:item/end_event_core" in STACK
    assert "copimine:item/end_event_pad" in STACK
    assert "830001" in STACK
    assert "830003" in STACK


def test_launcher_artifact_sync_is_tracked_and_verifies_the_pack_before_copying() -> None:
    assert SYNC.exists()
    sync = SYNC.read_text(encoding="utf-8")
    for marker in (
        "CopiMineResourcePack.zip",
        "CopiMineResourcePack.sha1",
        "CopiMineResourcePack.sha256",
        "Get-FileHash",
        "assets/minecraft/models/item/paper.json",
        "end_event_core",
        "end_event_pad",
    ):
        assert marker in sync
