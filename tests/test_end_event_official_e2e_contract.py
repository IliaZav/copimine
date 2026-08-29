"""Static guardrails for the non-skipping two-player End Rift run.

The disposable AI probe is useful for a controller smoke test, but it cannot
prove that the official event state machine really reaches every wave and the
boss on its own.  This contract keeps the live driver honest: it may prepare
only the isolated local scene, then it must wait for the real countdown,
objectives, boss phases and victory markers.
"""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DRIVER = ROOT / "tests/RunEndRiftOfficialTwoPlayerLive.ps1"


def test_official_two_player_driver_exists_and_is_local_only() -> None:
    assert DRIVER.is_file()
    source = DRIVER.read_text(encoding="utf-8")
    for marker in (
        "environment: local",
        "EndRiftOfficialA",
        "EndRiftOfficialB",
        "cmend core remove confirm",
        "cmend core setat",
        "cmend resources add DIAMOND",
        "cmend resources add ENDER_EYE",
        "cmend resources add AMETHYST_SHARD",
        "cmend resources add BLAZE_ROD",
        "Start-Process",
        "gamemode survival",
    ):
        assert marker in source


def test_official_driver_does_not_skip_waves_or_boss_phases_with_test_commands() -> None:
    source = DRIVER.read_text(encoding="utf-8")
    forbidden = (
        "cmend test wave",
        "cmend boss damage",
        "cmend boss spawn",
        "cmend boss kill",
    )
    assert all(marker not in source for marker in forbidden)
    for marker in (
        "RITUAL_STARTED",
        "RITUAL_COMPLETED",
        "WAVE_STARTED",
        "WAVE_OBJECTIVE_COMPLETE",
        "WAVE_COMPLETED",
        "BOSS_CINEMATIC_STARTED",
        "BOSS_STAGE_TRANSITION",
        "BOSS_CAST_STATE.*ABSORPTION_CHANNEL",
        "BOSS_ABSORPTION_BUFF",
        "BOSS_CAST_STATE.*JUDGMENT_CAST",
        "END_EVENT_WAVE_COMBAT_CLEANUP",
        "BOSS_DEFEAT_COMMITTED",
        "BOSS_DEFEATED",
    ):
        assert marker in source


def test_official_driver_waits_for_all_five_waves_and_every_boss_stage_in_order() -> None:
    source = DRIVER.read_text(encoding="utf-8")
    ordered_markers = (
        "WAVE_STARTED.*wave=1",
        "WAVE_COMPLETED.*wave=1",
        "WAVE_STARTED.*wave=2",
        "WAVE_COMPLETED.*wave=2",
        "WAVE_STARTED.*wave=3",
        "WAVE_OBJECTIVE_COMPLETE.*wave=3",
        "WAVE_COMPLETED.*wave=3",
        "WAVE_STARTED.*wave=4",
        "WAVE_OBJECTIVE_COMPLETE.*wave=4",
        "WAVE_COMPLETED.*wave=4",
        "WAVE_STARTED.*wave=5",
        "WAVE_OBJECTIVE_COMPLETE.*wave=5",
        "WAVE_COMPLETED.*wave=5",
        "BOSS_CINEMATIC_STARTED",
        "AWAKENING",
        "HUNTER",
        "DISTORTION",
        "ABSORPTION",
        "CATASTROPHE",
        "JUDGMENT",
        "BOSS_DEFEAT_COMMITTED",
    )
    positions = [source.index(marker) for marker in ordered_markers]
    assert positions == sorted(positions)


def test_official_driver_observes_wave_two_mark_and_skeleton_retarget() -> None:
    source = DRIVER.read_text(encoding="utf-8")
    assert "WAVE_OBJECTIVE_MARK.*wave=2" in source
    assert "WAVE_SKELETON_MARKED_TARGET.*wave=2" in source


def test_official_driver_restricts_player_probe_targets_to_the_event_arena() -> None:
    source = DRIVER.read_text(encoding="utf-8")
    bot = (ROOT / "tests/LocalEndRiftMobCombatBot.js").read_text(encoding="utf-8")
    for marker in (
        "arenaX",
        "arenaY",
        "arenaZ",
        "arenaRadius",
        "isConfiguredArenaMob",
        "Format-Coordinate ($coreX + 0.5D)",
        "Format-Coordinate ($coreZ + 0.5D)",
    ):
        assert marker in source or marker in bot


def test_official_driver_sweeps_the_arena_during_tower_defense() -> None:
    source = DRIVER.read_text(encoding="utf-8")
    assert (
        "WAVE_OBJECTIVE_COMPLETE.*wave=4' -WaitSeconds 240 "
        "-DuringWait { Keep-PlayersAtCombatSweep -Core $core }"
    ) in source


def test_official_driver_observes_paced_tower_groups_before_waiting_180_seconds() -> None:
    source = DRIVER.read_text(encoding="utf-8")
    assert "WAVE_TOWER_GROUP_SPAWN.*group=1/4.*spawned=[1-4]" in source
    assert "WAVE_TOWER_GROUP_SPAWN.*group=2/4" in source


def test_official_driver_has_a_local_wave_four_failure_and_clean_retry_probe() -> None:
    source = DRIVER.read_text(encoding="utf-8")
    for marker in (
        "[switch]$TowerFailureProbe",
        "cmend test tower fail",
        "WAVE_TEST_FAILURE_INJECTED",
        "WAVE_OBJECTIVE_FAILED.*wave=4",
        "event-mobs=0",
        "WAVE_RETRY_OBJECTIVE_RESET",
        "WAVE_RETRY_STARTED",
        "OFFICIAL_W4_FAILURE_CLEANUP_PASS",
        "OFFICIAL_W4_RETRY_PASS",
    ):
        assert marker in source


def test_official_driver_reaches_the_outer_spawn_ring_without_shortcuts() -> None:
    source = DRIVER.read_text(encoding="utf-8")
    for marker in (
        "X = $Core[0] + 1.5D; Y = [double]$Core[1]; Z = $Core[2] - 10.5D",
        "X = $Core[0] - 10.5D; Y = [double]$Core[1]; Z = $Core[2] + 1.5D",
        "X = $Core[0] + 1.5D; Y = [double]$Core[1]; Z = $Core[2] + 10.5D",
        "X = $Core[0] + 10.5D; Y = [double]$Core[1]; Z = $Core[2] + 1.5D",
    ):
        assert marker in source


def test_official_survival_players_have_a_real_melee_fixture_for_all_wave_mobs() -> None:
    source = DRIVER.read_text(encoding="utf-8")
    assert "give $name minecraft:netherite_sword" in source
    assert "enchant $name minecraft:sharpness 5" in source
    assert "effect give $name minecraft:strength 1000 20 true" in source
    bot = (ROOT / "tests/LocalEndRiftMobCombatBot.js").read_text(encoding="utf-8")
    assert "attackTimer = setInterval(attackNearest, 400)" in bot


def test_official_driver_can_close_on_live_mob_positions_after_objective_deadline() -> None:
    """The survival probe must reach valid outer-ring mobs, not delete them."""
    source = DRIVER.read_text(encoding="utf-8")
    assert "function Get-CombatMobPositions" in source
    assert "cmend debug ai" in source
    assert "function Keep-PlayersAtCombatMobs" in source
    assert "Keep-PlayersAtCombatMobs" in source
    assert "WAVE_COMPLETED.*wave=4' -WaitSeconds 240 -DuringWait { Keep-PlayersAtCombatMobs" in source
