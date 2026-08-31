"""Static guardrails for the six-client local visual/effects pass."""

import re

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DRIVER = ROOT / "tests/RunEndRiftVisualFivePlayerLive.ps1"


def test_visual_driver_is_local_only_and_has_a_real_sixth_viewer() -> None:
    source = DRIVER.read_text(encoding="utf-8")
    for marker in (
        "environment:\\s*local",
        "codex/end-rift-event",
        "SudoKillDash9",
        "EndRiftVisualA",
        "EndRiftVisualB",
        "EndRiftVisualC",
        "EndRiftVisualD",
        "EndRiftVisualE",
        "server-port=25566",
        "rcon\\.port=25576",
        "Assert-ViewerOnline",
        "Assert-OnlinePlayers",
        "Wait-ViewerInArena",
        "data get entity $ViewerName Pos",
    ):
        assert marker in source


def test_full_visual_probe_default_viewer_name_fits_minecraft_username_limit() -> None:
    source = (ROOT / "tests/RunEndRiftBossVisualLive.ps1").read_text(encoding="utf-8")
    match = re.search(r"\[string\]\$ViewerName\s*=\s*'([^']+)'", source)
    assert match, "The full visual probe must declare a deterministic viewer name."
    viewer_name = match.group(1)
    assert 1 <= len(viewer_name) <= 16


def test_visual_driver_exercises_music_every_phase_and_unique_boss_spells() -> None:
    source = DRIVER.read_text(encoding="utf-8")
    for marker in (
        "function Get-LogByteLength",
        "function Get-LogTextSince",
        "[int64]$AfterOffset = 0",
        "Get-LogTextSince -Offset $AfterOffset",
        "$musicOffset = Get-LogByteLength",
        "-AfterOffset $musicOffset",
    ):
        assert marker in source
    for marker in (
        "test music",
        "wave-1",
        "intermission-4",
        "boss-cinematic",
        "final-wave",
        "boss-half",
        "boss-finish",
        "victory",
        "sudo $ViewerName cmend test run creative",
        "boss spell rift_arrows",
        "boss spell arena_inferno",
        "BOSS_SPELL_CAST.*rift_arrows",
        "BOSS_SPELL_CAST.*arena_inferno",
        "CREATIVE_TEST_COMPLETE.*success=true",
        "VISUAL_FIVE_PLAYER_PASS",
    ):
        assert marker in source


def test_visual_driver_waits_for_stages_in_order_from_fresh_log_offsets() -> None:
    source = DRIVER.read_text(encoding="utf-8")
    assert "$creativeStartOffset = Get-LogByteLength" in source
    assert "-AfterOffset $creativeStartOffset" in source
    assert "$stageOffset = Get-LogByteLength" in source
    assert "-AfterOffset $stageOffset" in source
    assert "CREATIVE_TEST_RESOURCES[\\s\\S]*CREATIVE_TEST_RUNES" in source
    assert "$riftArrowsOffset = Get-LogByteLength" in source
    assert "-AfterOffset $riftArrowsOffset" in source
    assert "$arenaInfernoOffset = Get-LogByteLength" in source
    assert "$bossStageStartOffset = Get-LogByteLength" in source
    assert "-AfterOffset $bossStageStartOffset" in source
    assert "-AfterOffset $arenaInfernoOffset" in source


def test_full_visual_probe_waits_for_authme_and_persisted_operator_before_sudo() -> None:
    source = (ROOT / "tests/RunEndRiftBossVisualLive.ps1").read_text(encoding="utf-8")

    for marker in (
        "function Wait-AuthMeLogin",
        "function Wait-OperatorPersisted",
        "Wait-AuthMeLogin -Name $ViewerName",
        "Wait-OperatorPersisted -Name $ViewerName",
        "AuthMe",
        "logged in",
        "ops.json",
    ):
        assert marker in source

    assert source.index("Wait-Online $ViewerName") < source.index("Wait-AuthMeLogin -Name $ViewerName")
    assert source.index("Wait-AuthMeLogin -Name $ViewerName") < source.index("op $ViewerName")
    assert source.index("op $ViewerName") < source.index("Wait-OperatorPersisted -Name $ViewerName")


def test_full_visual_probe_treats_child_output_as_one_report() -> None:
    source = (ROOT / "tests/RunEndRiftBossVisualLive.ps1").read_text(encoding="utf-8")

    assert "if (-not ($visualOutput -match 'VISUAL_FIVE_PLAYER_PASS'))" in source
    assert "if ($visualOutput -notmatch 'VISUAL_FIVE_PLAYER_PASS')" not in source
    assert "if (-not ($diagnosticOutput -match 'LIVE_DIAGNOSTICS_FAILURE_PASS'))" in source
    assert "if (-not ($performanceOutput -match 'PERF_FIVE_PASS'))" in source


def test_visual_driver_always_cleans_the_disposable_clients_and_test_state() -> None:
    source = DRIVER.read_text(encoding="utf-8")
    for marker in (
        "finally",
        "process.Kill()",
        "sudo $ViewerName cmend test run creative cancel",
        "gamemode survival $ViewerName",
        "Visual driver refuses",
    ):
        assert marker in source
