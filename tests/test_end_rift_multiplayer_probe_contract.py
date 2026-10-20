from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BOT = ROOT / "tests" / "LocalEndRiftBossCombatBot.js"
PROBE = ROOT / "tests" / "RunEndRiftBossMultiPlayerDamageLive.ps1"
GATE = ROOT / "tests" / "RunEndRiftEventChecks.ps1"


def test_same_tick_burst_continues_with_regular_attacks() -> None:
    text = BOT.read_text(encoding="utf-8")

    branch = text.split("if (synchronizedBurstCount > 0) {", 1)[1].split(
        "\n  }\n", 1
    )[0]
    assert "startSynchronizedBurst()" in branch
    assert "attackTimer = setInterval(tryAttack, attackEveryMs)" in branch


def test_five_player_probe_requires_at_least_one_hundred_events() -> None:
    text = PROBE.read_text(encoding="utf-8")

    assert "$MinimumAcceptedEvents = 100" in text
    assert (
        "if ($playerNames.Count -eq 5 -and $bossLines.Count -lt "
        "$MinimumAcceptedEvents)" in text
    )


def test_full_gate_runs_the_new_live_probe_contracts() -> None:
    text = GATE.read_text(encoding="utf-8")

    assert "test_end_rift_multiplayer_probe_contract.py" in text
    assert "test_end_rift_recovery_contract.py" in text
