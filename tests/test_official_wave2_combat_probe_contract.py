from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def test_wave1_live_probe_positions_one_picker_at_the_selected_carrier() -> None:
    runner = (ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1").read_text(encoding="utf-8")
    carrier_wait = runner.split("function Wait-CarrierDelivery {", 1)[1].split("function Get-Status {", 1)[0]
    selection = carrier_wait.split("$carrierMatches = [Regex]::Matches($tail,", 1)[1].split(
        "$chargeMatches = [Regex]::Matches($tail,", 1
    )[0]
    assert "Parse-LoggedLocation $match.Value" in selection, (
        "Wave 1 probe must use the authoritative selected-carrier location from the server log"
    )
    assert "$null -eq $activeCharge" in selection
    assert re.search(r"Teleport-Player \$PickupPlayer [\s\S]*?\$carrierLocation", selection), (
        "Before a charge exists, position only the designated disposable picker to attack the selected carrier"
    )
    assert "Set-PlayerBotMode -Mode 'ACTIVE'" in selection


def test_wave2_live_probe_recovers_only_players_outside_real_melee_range() -> None:
    runner = (ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1").read_text(encoding="utf-8")
    wave_two = re.search(
        r"\$waveTwoTransitionOffset\s*=\s*Wait-WaveComplete[\s\S]*?"
        r"Write-Evidence \"CURRENT_WAVE_PASS event=\$eventId wave=2",
        runner,
    )
    assert wave_two is not None, "Wave 2 live probe block is missing"
    assert re.search(
        r"-Action\s*\{\s*Teleport-PlayersToNearestWaveMobIfOutOfMeleeRange\s*\}",
        wave_two.group(0),
    ), (
        "Wave 2 bot recovery must run during its completion wait so pathing stalls do not hide real combat"
    )

    recovery = runner.split("function Teleport-PlayersToNearestWaveMobIfOutOfMeleeRange {", 1)[1].split(
        "function Teleport-PlayersToObeliskRing", 1
    )[0]
    assert "$meleeRangeSquared = 2.75D * 2.75D" in recovery
    assert "$target.DistanceSquared -le $meleeRangeSquared" in recovery
    assert "tag=copimine_end_event" in recovery
    assert "Teleport-Player $name $target.Position[0] $target.Position[1] $target.Position[2]" in recovery


def test_wave2_elite_skeleton_diagnostic_reports_the_configured_spell() -> None:
    source = (ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent"
              / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    spawn_skeleton = re.search(
        r"private Skeleton spawnSkeleton\([\s\S]*?\n    private Skeleton spawnWaveTwoFinalEliteSkeleton",
        source,
    )
    assert spawn_skeleton is not None, "spawnSkeleton implementation is missing"
    body = spawn_skeleton.group(0)
    assert "EndRiftAiPolicy.MiniBossSpell configuredSpell = null;" in body
    assert "configuredSpell = configureEliteAbility(" in body
    assert '" spell=" + (configuredSpell == null' in body
    assert "configuredSpell.id()" in body
    assert '"spell=" + (miniBoss ? "ARROW_SALVO" : "VANILLA_BOW")' not in body


def test_wave2_live_acceptance_requires_elite_arrow_rain_release() -> None:
    runner = (ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1").read_text(encoding="utf-8")
    wave_two = runner.split("$waveTwoStartOffset = Wait-Transition", 1)[1].split(
        "$pads = Get-Pads", 1
    )[0]
    assert "Wait-Log -AfterOffset $waveTwoStartOffset -Pattern 'ELITE_ARROW_RAIN_RELEASE.*player_only=true'" in wave_two
    assert "CURRENT_WAVE2_ELITE_SPELL_PASS" in wave_two
