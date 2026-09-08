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
        "BOSS_V2_STAGE_TRANSITION",
        "RIFT_OBELISKS_SPAWNED.*stage=RIFT",
        "BOSS_V2_LAST_SEAL_VISUALS_STARTED",
        "END_EVENT_WAVE_COMBAT_CLEANUP",
        "BOSS_DEFEAT_COMMITTED",
        "BOSS_DEFEATED",
    ):
        assert marker in source


def test_official_driver_waits_for_all_six_v2_waves_and_every_boss_stage_in_order() -> None:
    source = DRIVER.read_text(encoding="utf-8")
    ordered_markers = (
        "Wait-LogRegex -Pattern 'WAVE_STARTED.*wave=1'",
        "Wait-LogRegex -Pattern 'WAVE_COMPLETED.*wave=1'",
        "Wait-V2TransitionToWave -CompletedWave 1 -NextWave 2",
        "Wait-LogRegex -Pattern 'WAVE_COMPLETED.*wave=2'",
        "Wait-V2TransitionToWave -CompletedWave 2 -NextWave 3",
        "Wait-LogRegex -Pattern 'WAVE_OBJECTIVE_COMPLETE.*wave=3'",
        "Wait-LogRegex -Pattern 'WAVE_COMPLETED.*wave=3'",
        "Wait-V2TransitionToWave -CompletedWave 3 -NextWave 4",
        "Wait-LogRegex -Pattern 'WAVE_OBJECTIVE_COMPLETE.*wave=4'",
        "Wait-LogRegex -Pattern 'WAVE_COMPLETED.*wave=4'",
        "Wait-V2TransitionToWave -CompletedWave 4 -NextWave 5",
        "Wait-LogRegex -Pattern 'WAVE_OBJECTIVE_COMPLETE.*wave=5'",
        "Wait-LogRegex -Pattern 'WAVE_COMPLETED.*wave=5'",
        "Wait-V2TransitionToWave -CompletedWave 5 -NextWave 6",
        "Wait-LogRegex -Pattern 'WAVE_6_COMPLETED.*passage_open=true'",
        "Wait-LogRegex -Pattern 'WAVE_6_COMPLETED.*passage_open=true'",
        "Wait-LogRegex -Pattern 'BOSS_CINEMATIC_STARTED'",
        "Assert-BossStage -Stage 'AWAKENING'",
        "Assert-BossStage -Stage 'HUNT'",
        "RIFT_TENTACLE_SPAWN",
        "Assert-BossStage -Stage 'RIFT'",
        "Assert-BossStage -Stage 'OVERLOAD'",
        "Assert-BossStage -Stage 'RAGE'",
        "Assert-BossStage -Stage 'LAST_SEAL'",
        "BOSS_V2_LAST_SEAL_VISUALS_STARTED",
        "Wait-LogRegex -Pattern 'BOSS_DEFEAT_COMMITTED'",
    )
    positions = [source.index(marker) for marker in ordered_markers]
    assert positions == sorted(positions)
    assert "FINAL_WAVE_STARTED" not in source
    assert "WAVE_COMPLETED.*wave=FINAL" not in source


def test_official_transition_driver_preserves_the_persisted_pad_array_shape() -> None:
    source = DRIVER.read_text(encoding="utf-8")
    assert "$nextPads = Get-PadCoordinates" in source
    assert "$nextPads = @(Get-PadCoordinates)" not in source


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
    assert "WAVE_TOWER_GROUP_SPAWN.*group=1/\\d+.*spawned=\\d+" in source
    assert "for ($group = 2; $group -le $towerGroupCount; $group++)" in source


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
    assert "give $name minecraft:netherite_sword 1" in source
    assert "enchant $name minecraft:sharpness 5" in source
    assert "effect give $name minecraft:strength 1000 20 true" in source
    bot = (ROOT / "tests/LocalEndRiftMobCombatBot.js").read_text(encoding="utf-8")
    assert "attackIntervalMs" in bot
    assert "process.argv[8]" in bot
    assert "attackTimer = setInterval(attackNearest, attackIntervalMs)" in bot


def test_reward_probe_can_freeze_survival_bots_before_measuring_pickup() -> None:
    """The pickup assertion must not be invalidated by post-wave knockback."""
    source = DRIVER.read_text(encoding="utf-8")
    bot = (ROOT / "tests/LocalEndRiftMobCombatBot.js").read_text(encoding="utf-8")
    assert "END_RIFT_PASSIVE" in source
    assert "END_RIFT_PASSIVE" in bot
    assert "knockback_resistance" in source
    assert "clearInterval(attackTimer)" in bot


def test_official_boss_phase_probe_uses_a_bounded_slow_attack_cadence() -> None:
    source = (ROOT / "tests/RunEndRiftOfficialTwoPlayerLive.ps1").read_text(encoding="utf-8")
    assert "+ ' 900'" in source


def test_boss_damage_probe_targets_the_authoritative_boss_uuid() -> None:
    bot = (ROOT / "tests/LocalEndRiftMobCombatBot.js").read_text(encoding="utf-8")
    assert "END_RIFT_BOSS_UUID" in bot
    assert "entity.uuid === configuredBossUuid" in bot


def test_official_driver_can_close_on_live_mob_positions_after_objective_deadline() -> None:
    """The survival probe must reach valid outer-ring mobs, not delete them."""
    source = DRIVER.read_text(encoding="utf-8")
    assert "function Get-CombatMobPositions" in source
    assert "cmend debug ai" in source
    assert "function Keep-PlayersAtCombatMobs" in source
    assert "Keep-PlayersAtCombatMobs" in source
    assert "WAVE_COMPLETED.*wave=4' -WaitSeconds 240 -DuringWait { Keep-PlayersAtCombatMobs" in source


def test_official_wave_six_probe_keeps_players_inside_their_assigned_chamber() -> None:
    """The survival probe must respect the same closed-room boundary as the server."""
    source = DRIVER.read_text(encoding="utf-8")
    assert "function Keep-PlayersAtWaveSixMobs" in source
    assert "ChamberIsolationPolicy" not in source
    assert "centerAngle" in source
    assert "halfSector" in source
    assert "Keep-PlayersAtWaveSixMobs -Core $core" in source
    assert "WAVE_6_COMPLETED.*passage_open=true' -WaitSeconds 360 `" in source
    helper = source[source.index("function Get-WaveSixChamberMob"):source.index("function Keep-PlayersAtWaveSixMobs")]
    assert "[Parameter(Mandatory = $true)][object[]]$Mobs" not in helper
