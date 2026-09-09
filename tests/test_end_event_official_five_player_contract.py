"""Static guardrails for the bounded five-player official End Rift run."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DRIVER = ROOT / "tests/RunEndRiftOfficialFivePlayerLive.ps1"
SHARED_DRIVER = ROOT / "tests/RunEndRiftOfficialTwoPlayerLive.ps1"
TEN_DRIVER = ROOT / "tests/RunEndRiftOfficialTenPlayerLive.ps1"


def test_five_player_driver_is_local_only_and_uses_the_shared_official_flow() -> None:
    source = DRIVER.read_text(encoding="utf-8")
    shared = SHARED_DRIVER.read_text(encoding="utf-8")
    for marker in (
        "environment:\\s*local",
        "RunEndRiftOfficialTwoPlayerLive.ps1",
        "EndRiftFiveA",
        "EndRiftFiveB",
        "EndRiftFiveC",
        "EndRiftFiveD",
        "EndRiftFiveE",
        "AdditionalBotNames",
        "codex/end-rift-event",
    ):
        assert marker in source
    for marker in (
        "RITUAL_STARTED",
        "RITUAL_COMPLETED",
        "V2_WAVE_STARTED.*wave=1",
        "V2_WAVE_COMPLETED.*wave=5",
        "BOSS_V2_STAGE_TRANSITION",
        "BOSS_V2_LAST_SEAL_VISUALS_STARTED",
        "BOSS_DEFEAT_COMMITTED",
        "OFFICIAL_FIVE_PLAYER_PASS",
        "OFFICIAL_BOSS_STAGE_FAST_TRANSITION",
    ):
        assert marker in shared
    assert "V2_PORTALS_READY.*count=3.*sequential=true" in shared
    assert "$portalCount = 3" in shared
    assert "X = $coreX + 0.5D + 8.0D * [Math]::Cos($angle)" in shared
    assert "Z = $coreZ + 0.5D + 8.0D * [Math]::Sin($angle)" in shared
    assert "portals=3'" not in shared


def test_wave_four_asserts_the_bounded_black_fog_objective() -> None:
    source = SHARED_DRIVER.read_text(encoding="utf-8")
    for marker in (
        "V2_WAVE_OBJECTIVE_STARTED.*wave=4.*BLACK_FOG",
        "V2_FOG_SAFE_START.*cycle=1/3",
        "V2_FOG_START.*cycle=1/3",
        "V2_FOG_COMPLETE.*cycles=3",
    ):
        assert marker in source
    assert "TOWER_DEFENSE" not in source
    assert "RIFT_STORM" not in source


def test_shared_driver_accepts_exactly_two_to_five_unique_players() -> None:
    source = SHARED_DRIVER.read_text(encoding="utf-8")
    for marker in (
        "$PlayerNames = @($FirstBotName, $SecondBotName) + @($AdditionalBotNames)",
        "supports two to twenty local players",
        "requires unique local player names",
        "if ($PlayerNames.Count -eq 5)",
        "foreach ($name in $PlayerNames)",
        "Official local player bots did not join",
    ):
        assert marker in source


def test_ten_player_driver_reuses_the_official_local_flow() -> None:
    source = TEN_DRIVER.read_text(encoding="utf-8")
    shared = SHARED_DRIVER.read_text(encoding="utf-8")
    for marker in (
        "environment:\\s*local",
        "RunEndRiftOfficialTwoPlayerLive.ps1",
        "EndRiftTenA",
        "EndRiftTenJ",
        "AdditionalBotNames",
        "codex/end-rift-event",
    ):
        assert marker in source
    for marker in (
        "supports two to twenty local players",
        "if ($PlayerNames.Count -eq 10)",
        "official-ten-player-live.log",
        "official-ten-player-bots",
    ):
        assert marker in shared


def test_shared_driver_prepares_disposable_authme_accounts_before_clients_start() -> None:
    source = SHARED_DRIVER.read_text(encoding="utf-8")
    for marker in (
        "function Prepare-OfficialAuthMeAccounts",
        "authme unregister $name",
        "authme register $name endrift-local",
        "Prepare-OfficialAuthMeAccounts",
    ):
        assert marker in source
    assert source.count("Prepare-OfficialAuthMeAccounts") >= 2
    assert source.index("Prepare-OfficialAuthMeAccounts\n  $node") < source.index("Start-Process -FilePath $node")


def test_official_driver_allows_intermission_and_peer_tunnel_jitter_before_new_waves() -> None:
    source = SHARED_DRIVER.read_text(encoding="utf-8")
    assert 'Wait-LogRegex -Pattern ("V2_WAVE_STARTED.*wave=" + $NextWave) -WaitSeconds 120' in source


def test_official_log_cursor_reads_the_complete_file_tail() -> None:
    source = SHARED_DRIVER.read_text(encoding="utf-8")
    assert "$read = $stream.Read($buffer, $offset, $length - $offset)" in source
    assert "while ($offset -lt $length)" in source
    assert "$script:LogOffset = $stream.Position" in source


def test_official_driver_accepts_a_fast_absorption_threshold_transition() -> None:
    source = SHARED_DRIVER.read_text(encoding="utf-8")
    assert "Assert-BossStage -Stage 'OVERLOAD'" in source
    assert "Assert-BossStage -Stage 'LAST_SEAL'" in source
    assert "BOSS_V2_LAST_SEAL_VISUALS_STARTED" in source


def test_five_player_positions_cover_runes_core_ring_portals_and_boss() -> None:
    source = SHARED_DRIVER.read_text(encoding="utf-8")
    for marker in (
        "function Keep-PlayersAtPads",
        "function Keep-PlayersAtCoreRing",
        "function Keep-PlayersAtCombatSweep",
        "function Keep-PlayersAtPoint",
        "function Keep-PlayersNearBoss",
        "for ($index = 0; $index -lt $PlayerNames.Count; $index++)",
    ):
        assert marker in source


def test_five_player_run_requires_five_persisted_runes_and_assigns_one_each() -> None:
    source = SHARED_DRIVER.read_text(encoding="utf-8")
    for marker in (
        "cmend core setat 8 68 -39 5",
        "if ($matches.Count -ge $expectedRuneCount)",
        "persist $expectedRuneCount rune coordinates",
        "Teleport-Player -Name $PlayerNames[$index] -X ($Pads[$index][0] + 0.5D)",
            "$statusText = ($status -join [Environment]::NewLine) -replace '\\u00A7.', ''",
        "pads=$($pads.Count)",
    ):
        assert marker in source


def test_setup_status_assertion_normalizes_multiline_rcon_as_one_text() -> None:
    source = SHARED_DRIVER.read_text(encoding="utf-8")
    assert "$statusText = ($status -join [Environment]::NewLine) -replace '\\u00A7.', ''" in source
    assert '$hasEmptyPads = [Regex]::IsMatch($statusText, "pads=0/$runeCount")' in source
    assert "if (($pads.Count -ne $runeCount) -or (-not $hasEmptyPads))" in source
