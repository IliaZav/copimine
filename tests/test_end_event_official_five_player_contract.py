"""Static guardrails for the bounded five-player official End Rift run."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DRIVER = ROOT / "tests/RunEndRiftOfficialFivePlayerLive.ps1"
SHARED_DRIVER = ROOT / "tests/RunEndRiftOfficialTwoPlayerLive.ps1"


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
        "WAVE_STARTED.*wave=1",
        "WAVE_COMPLETED.*wave=5",
        "FINAL_WAVE_STARTED",
        "BOSS_STAGE_TRANSITION",
        "BOSS_CAST_STATE.*JUDGMENT_CAST",
        "BOSS_DEFEAT_COMMITTED",
        "OFFICIAL_FIVE_PLAYER_PASS",
        "OFFICIAL_BOSS_STAGE_FAST_TRANSITION",
    ):
        assert marker in shared
    assert "WAVE_OBJECTIVE_STARTED.*wave=3.*portals=\\d+" in shared
    assert "$portalCount = [int]$portalMatch.Groups[1].Value" in shared
    assert "X = $coreX + 0.5D + 8.0D * [Math]::Cos($angle)" in shared
    assert "Z = $coreZ + 0.5D + 8.0D * [Math]::Sin($angle)" in shared
    assert "portals=3'" not in shared


def test_wave_four_asserts_the_roster_scaled_group_count() -> None:
    source = SHARED_DRIVER.read_text(encoding="utf-8")
    for marker in (
        "WAVE_TOWER_GROUP_SPAWN.*group=1/\\d+.*spawned=\\d+",
        "$towerGroupCount = [int]$towerGroupMatch.Groups[1].Value",
        "for ($group = 2; $group -le $towerGroupCount; $group++)",
        "OFFICIAL_W4_GROUPS_PASS",
    ):
        assert marker in source
    assert "group=1/4" not in source
    assert "group=2/4" not in source


def test_shared_driver_accepts_exactly_two_to_five_unique_players() -> None:
    source = SHARED_DRIVER.read_text(encoding="utf-8")
    for marker in (
        "$PlayerNames = @($FirstBotName, $SecondBotName) + @($AdditionalBotNames)",
        "supports two to five local players",
        "requires unique local player names",
        "if ($PlayerNames.Count -eq 5)",
        "foreach ($name in $PlayerNames)",
        "Official local player bots did not join",
    ):
        assert marker in source


def test_official_driver_allows_intermission_and_peer_tunnel_jitter_before_new_waves() -> None:
    source = SHARED_DRIVER.read_text(encoding="utf-8")
    for marker in (
        "WAVE_STARTED.*wave=2' -WaitSeconds 60",
        "WAVE_STARTED.*wave=3' -WaitSeconds 60",
        "WAVE_STARTED.*wave=4' -WaitSeconds 60",
        "WAVE_STARTED.*wave=5' -WaitSeconds 60",
    ):
        assert marker in source


def test_official_log_cursor_reads_the_complete_file_tail() -> None:
    source = SHARED_DRIVER.read_text(encoding="utf-8")
    assert "$read = $stream.Read($buffer, $offset, $length - $offset)" in source
    assert "while ($offset -lt $length)" in source
    assert "$script:LogOffset = $stream.Position" in source


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
