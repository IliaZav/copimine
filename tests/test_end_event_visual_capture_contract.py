"""Static guardrails for the six-client local visual/effects pass."""

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
    ):
        assert marker in source


def test_visual_driver_exercises_music_every_phase_and_unique_boss_spells() -> None:
    source = DRIVER.read_text(encoding="utf-8")
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
