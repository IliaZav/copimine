from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RUNNER = ROOT / "tests/RunEndRiftWave6AmplifierLive.ps1"


def test_amplifier_live_runner_requires_the_five_caster_profile():
    source = RUNNER.read_text(encoding="utf-8")
    assert source.count("'EndRiftAmp") == 9
    assert "casters=5" in source
    assert "guards=15" in source
    assert "role -eq 'AMPLIFIER'" in source


def test_amplifier_live_runner_proves_effect_loss_and_cleanup():
    source = RUNNER.read_text(encoding="utf-8")
    assert "minecraft:kill $amplifierUuid" in source
    assert "minecraft:kill $guardUuid" in source
    assert "LIVE_WAVE6_AMPLIFIER_EXPOSED_PASS" in source
    assert "LIVE_WAVE6_AMPLIFIER_KILL_COMMAND" in source
    assert "amplifier_count=1" in source
    assert "effective_projectiles=4" in source
    assert "amplifier_count=0" in source
    assert "effective_projectiles=3" in source
    assert "LIVE_WAVE6_AMPLIFIER_PASS" in source
    assert "scheduler_turn_consumed" in source
    assert "cmend test ritual drain hold" in source
    assert "cmend test ritual drain release" in source
    assert "cmend wave clear" in source


def test_amplifier_live_runner_keeps_drain_level_constant_during_comparison():
    source = RUNNER.read_text(encoding="utf-8")
    assert "LOCAL_TEST_HOOK drain=hold" in source
    assert "LOCAL_TEST_HOOK drain=release" in source
    assert "state=$exposedState" in source
    assert "$exposedSnapshot = Get-AiJson" in source
    assert "minecraft:tp $Name $X $Y $Z" in source
    assert "Wait-RitualPrisonerCapture" in source
