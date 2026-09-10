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
        "V2_WAVE_STARTED",
        "V2_WAVE_OBJECTIVE_STARTED",
        "WAVE_OBJECTIVE_COMPLETE",
        "V2_WAVE_COMPLETED",
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
    config = (ROOT / "copimine-end-event/config.yml").read_text(encoding="utf-8")
    if "schema-version: 3" in config:
        # Schema 3 deliberately changes the official numbering to seven
        # waves.  Keep the legacy assertions below for schema-2 fixtures, but
        # make the checked-in driver prove the V3 route rather than requiring
        # retired V2 wave markers.
        ordered_markers = (
            "Wait-EventWaveStarted -Wave 1",
            "Wait-EventWaveCompleted -Wave 1",
            "Wait-V2TransitionToWave -CompletedWave 1 -NextWave 2",
            "Wait-EventWaveCompleted -Wave 2",
            "Wait-V2TransitionToWave -CompletedWave 2 -NextWave 3",
            "Wait-EventWaveCompleted -Wave 3",
            "Wait-V2TransitionToWave -CompletedWave 3 -NextWave 4",
            "V3_WAVE_STARTED.*wave=4.*objective=OBELISK_ASSAULT",
            "OFFICIAL_V3_WAVE4_PASS",
            "Wait-V2TransitionToWave -CompletedWave 4 -NextWave 5",
            "V3_WAVE_STARTED.*wave=5.*objective=BLACK_FOG",
            "OFFICIAL_V3_WAVE5_PASS",
            "Wait-V2TransitionToWave -CompletedWave 5 -NextWave 6",
            "V3_WAVE_STARTED.*wave=6.*objective=COLLAPSE_RINGS",
            "OFFICIAL_V3_WAVE6_PASS",
            "Wait-V2TransitionToWave -CompletedWave 6 -NextWave 7",
            "V3_WAVE_STARTED.*wave=7.*objective=REALITY_SPLIT",
            "OFFICIAL_V3_WAVE7_PASS",
            "BOSS_CINEMATIC_STARTED",
            "Assert-BossStage -Stage 'AWAKENING'",
            "V3_RIFT_FRACTURES_STARTED.*phase=RIFT",
            "OFFICIAL_V3_RIFT_FRACTURE_PASS",
            "Assert-BossStage -Stage 'LAST_SEAL'",
            "Wait-LogRegex -Pattern 'BOSS_DEFEAT_COMMITTED'",
        )
        positions = [source.index(marker) for marker in ordered_markers]
        assert positions == sorted(positions)
        assert "WAVE_COMPLETED.*wave=FINAL" not in source
        v3_rift_block = source[source.index("if ($isV3Flow) {", source.index("Assert-BossStage -Stage 'RIFT'")):source.index("} else {", source.index("Assert-BossStage -Stage 'RIFT'"))]
        assert "Wait-LogRegex -Pattern 'RIFT_OBELISKS_SPAWNED.*stage=RIFT'" not in v3_rift_block
        return
    ordered_markers = (
        "Wait-LogRegex -Pattern 'V2_WAVE_STARTED.*wave=1'",
        "Wait-LogRegex -Pattern 'V2_WAVE_COMPLETED.*wave=1'",
        "Wait-V2TransitionToWave -CompletedWave 1 -NextWave 2",
        "Wait-LogRegex -Pattern 'V2_WAVE_COMPLETED.*wave=2'",
        "Wait-V2TransitionToWave -CompletedWave 2 -NextWave 3",
        "Wait-LogRegex -Pattern 'WAVE_OBJECTIVE_COMPLETE.*wave=3'",
        "Wait-LogRegex -Pattern 'V2_WAVE_COMPLETED.*wave=3'",
        "Wait-V2TransitionToWave -CompletedWave 3 -NextWave 4",
        "Wait-LogRegex -Pattern 'V2_FOG_COMPLETE.*cycles=3'",
        "Wait-LogRegex -Pattern 'V2_WAVE_COMPLETED.*wave=4'",
        "Wait-V2TransitionToWave -CompletedWave 4 -NextWave 5",
        "Wait-LogRegex -Pattern 'V2_RING_COLLAPSED.*ring=3/3'",
        "Wait-LogRegex -Pattern 'V2_WAVE_COMPLETED.*wave=5'",
        "Wait-V2TransitionToWave -CompletedWave 5 -NextWave 6",
        "Wait-LogRegex -Pattern (\"V2_CHAMBERS_COMPLETE.*chambers={0}\" -f $expectedChambers)",
        "Wait-LogRegex -Pattern 'V2_WAVE_COMPLETED.*wave=6.*CHAMBERS'",
        "Wait-LogRegex -Pattern 'BOSS_CINEMATIC_STARTED'",
        "Assert-BossStage -Stage 'AWAKENING'",
        "Assert-BossStage -Stage 'HUNT'",
        "OFFICIAL_TENTACLE_PRE_LAST_SEAL_PASS",
        "Assert-BossStage -Stage 'RIFT'",
        "Assert-BossStage -Stage 'OVERLOAD'",
        "Assert-BossStage -Stage 'RAGE'",
        "Assert-BossStage -Stage 'LAST_SEAL'",
        "BOSS_V2_LAST_SEAL_VISUALS_STARTED",
        "OFFICIAL_TENTACLE_LAST_SEAL_PASS",
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
    assert "V2_WAVE_OBJECTIVE_STARTED.*wave=2.*HUNT_MARK" in source
    assert "V2_HUNT_CYCLE.*cycle=1" in source


def test_official_wave_one_probe_reads_the_authoritative_charge_position() -> None:
    source = DRIVER.read_text(encoding="utf-8")
    assert 'data get entity $chargeUuid Pos' in source
    assert "RCON before Pos" in source


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


def test_official_driver_sweeps_the_arena_during_black_fog() -> None:
    source = DRIVER.read_text(encoding="utf-8")
    assert (
        "V2_FOG_COMPLETE.*cycles=3' -WaitSeconds 180 "
        "-DuringWait { Keep-PlayersAtCombatSweep -Core $core }"
    ) in source


def test_official_driver_observes_paced_v2_wave_groups_before_waiting() -> None:
    source = DRIVER.read_text(encoding="utf-8")
    assert "V2_WAVE_GROUP_SPAWN" in source
    assert "v2WaveSpawnTask" not in source


def test_official_driver_keeps_failure_probe_out_of_the_v2_success_path() -> None:
    source = DRIVER.read_text(encoding="utf-8")
    assert "[switch]$TowerFailureProbe" in source
    assert "V2_FOG_COMPLETE.*cycles=3" in source
    assert "TOWER_DEFENSE" not in source
    assert "RIFT_STORM" not in source


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


def test_official_enderman_roles_cannot_be_lost_to_daylight() -> None:
    """Sunlight must not strand the carrier objective on an open local arena."""
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(
        encoding="utf-8"
    )
    start = source.index("public void onOfficialEndermanSunDamage")
    end = source.index("private Player playerDamageAttacker", start)
    handler = source[start:end]
    for marker in (
        "instanceof Enderman",
        "DamageCause.FIRE_TICK",
        "ownedEntities.containsKey",
        "isOfficialEntity",
        "EVENT_KIND_BOSS.equals(kind)",
        "isWaveCombatKind(kind)",
        "event.setCancelled(true)",
        "enderman.setFireTicks(0)",
    ):
        assert marker in handler


def test_reward_probe_can_freeze_survival_bots_before_measuring_pickup() -> None:
    """The pickup assertion must not be invalidated by post-wave knockback."""
    source = DRIVER.read_text(encoding="utf-8")
    bot = (ROOT / "tests/LocalEndRiftMobCombatBot.js").read_text(encoding="utf-8")
    assert "END_RIFT_PASSIVE" in source
    assert "END_RIFT_PASSIVE" in bot
    assert "knockback_resistance" in source
    assert "clearInterval(attackTimer)" in bot


def test_mass_bot_mode_switch_does_not_overwrite_the_requested_mode() -> None:
    """The ten-player LAST_SEAL pause must leave only the selected bot active."""
    source = DRIVER.read_text(encoding="utf-8")
    bot = (ROOT / "tests/LocalEndRiftMobCombatBot.js").read_text(encoding="utf-8")
    assert "$requestedMode = if ($shouldBeActive)" in source
    assert "$Mode =" not in source[source.index("function Set-OfficialBotCombatMode"):source.index("function Wait-LocalPlayers")]
    assert "END_RIFT_BOT_CONTROL_DIRECTORY" in source
    assert "pollControlMode" in bot
    assert "controlFile" in bot


def test_official_tentacle_probe_uses_the_same_bounded_scaling_as_the_server() -> None:
    source = DRIVER.read_text(encoding="utf-8")
    assert "function Get-OfficialPermanentTentacleCount" in source
    for boundary in ("-le 2", "-le 4", "-le 7", "-le 10", "-le 15"):
        assert boundary in source
    assert "$expectedPermanentTentacles = Get-OfficialPermanentTentacleCount" in source
    assert "Get-GuardianHitboxPositions" in source
    assert "Keep-PlayersAtGuardiansAndBoss" in source
    assert "BOSS_V2_DAMAGE_BLOCKED.*reason=permanent-guardian-shield" in source
    assert "RIFT_TENTACLE_DAMAGE.*health_after=" in source
    assert "RIFT_GUARDIAN_SHIELD_BROKEN" in source


def test_official_multi_player_last_seal_probe_covers_guardians_and_boss() -> None:
    """Large runs must not pause every bot while waiting for the shield log."""
    source = DRIVER.read_text(encoding="utf-8")
    expected = "Get-OfficialPermanentTentacleCount -PlayerCount $PlayerNames.Count"
    assert expected in source
    assert "$guardianProbeNames = @($PlayerNames | Select-Object -First (Get-OfficialPermanentTentacleCount -PlayerCount $PlayerNames.Count))" in source
    assert "$env:END_RIFT_GUARDIAN_PROBE_NAMES = ($guardianProbeNames -join ',')" in source
    last_seal = source[source.index("Wait-LogRegex -Pattern 'BOSS_V2_STAGE_TRANSITION.*to=LAST_SEAL'"):source.index("Wait-LogRegex -Pattern 'BOSS_DEFEAT_COMMITTED'")]
    assert "Set-OfficialBotCombatMode -Mode ACTIVE -ActiveFromIndex 0" in last_seal
    assert "BOSS_V2_DAMAGE_BLOCKED.*reason=permanent-guardian-shield" in last_seal


def test_official_boss_phase_probe_uses_a_bounded_slow_attack_cadence() -> None:
    source = (ROOT / "tests/RunEndRiftOfficialTwoPlayerLive.ps1").read_text(encoding="utf-8")
    assert "+ ' 900'" in source


def test_boss_damage_probe_targets_the_authoritative_boss_uuid() -> None:
    bot = (ROOT / "tests/LocalEndRiftMobCombatBot.js").read_text(encoding="utf-8")
    assert "END_RIFT_BOSS_UUID" in bot
    assert "entity.uuid === configuredBossUuid" in bot
    assert "END_RIFT_GUARDIAN_PROBE_NAMES" in bot
    assert "isGuardianHitbox" in bot
    assert "use_entity" in bot


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
    assert 'expectedChambers = Get-WaveSixChamberCount' in source
    assert 'V2_CHAMBERS_COMPLETE.*chambers={0}' in source
    assert 'chambers=$expectedChambers' in source
    helper = source[source.index("function Get-WaveSixChamberMob"):source.index("function Keep-PlayersAtWaveSixMobs")]
    assert "[Parameter(Mandatory = $true)][object[]]$Mobs" not in helper


def test_official_driver_labels_and_records_the_three_player_matrix() -> None:
    source = DRIVER.read_text(encoding="utf-8")
    assert "3 { 'OFFICIAL_THREE_PLAYER_START'; break }" in source
    assert "3 { 'OFFICIAL_THREE_PLAYER_PASS'; break }" in source
    assert "official-three-player-live.log" in source
