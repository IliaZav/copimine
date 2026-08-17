from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SMOKE = (ROOT / "tests/RunEndRiftRuntimeSmoke.ps1").read_text(encoding="utf-8")
BOT = (ROOT / "tests/LocalEndRiftBot.js").read_text(encoding="utf-8")
START = (ROOT / "tests/StartEndRiftLocal.ps1").read_text(encoding="utf-8")
RECOVERY = (ROOT / "tests/RunEndRiftRecoverySmoke.ps1").read_text(encoding="utf-8")


def test_runtime_smoke_is_local_only_and_does_not_manage_production_processes() -> None:
    assert "end-rift-server" in SMOKE
    assert "environment:\\s*local" in SMOKE
    assert "127.0.0.1" in SMOKE
    assert "25576" in SMOKE
    assert "CopiMineEndEvent services ready" in SMOKE
    assert "Artifacts bridge ready=true postgres=true" in SMOKE
    assert "Stop-Process" not in SMOKE
    assert "Remove-Item" not in SMOKE
    assert "cmend client status" in SMOKE


def test_runtime_smoke_covers_refusal_paths_and_typed_dependencies() -> None:
    for expected in (
        "CopiMineEndEvent", "CopiMineWorldCore", "CopiMineArtifacts",
        "UNCONFIGURED", "UNLOCKED", "requiredPlayers=.*0", "event world", "Core", "cmend cleanup",
        "cmend ritual cancel", "cmend resources reset", "official",
    ):
        assert expected in SMOKE
    assert "clientgate" not in SMOKE
    assert "channel=" in SMOKE


def test_local_bot_can_delay_actions_until_authentication_has_time_to_finish() -> None:
    assert "END_RIFT_BOT_ACTION_DELAY_MS" in BOT
    assert "ACTION ${username} ${command}" in BOT


def test_local_start_script_is_bounded_to_the_isolated_runtime() -> None:
    for expected in ("local-runtime", "25566", "25576", "environment:\\s*local"):
        assert expected in START
    assert "StartsWith($localPrefix" in START
    assert "WindowStyle Hidden" in START
    assert "Stop-Process" not in START
    assert "Remove-Item" not in START


def test_recovery_smoke_requires_unlocked_durable_state_and_stays_local() -> None:
    for expected in (
        "local-runtime", "UNLOCKED", "VICTORY_COMPLETE", "persistent phase=",
        "END_EVENT_STATE forced=UNLOCKED",
        "WorldCore already reports End unlocked; preserving active event",
    ):
        assert expected in RECOVERY
    assert "Stop-Process" not in RECOVERY
    assert "Remove-Item" not in RECOVERY
