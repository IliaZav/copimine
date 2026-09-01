"""Contract for the optional local live wave-reward pickup probe."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DRIVER = ROOT / "tests/RunEndRiftOfficialTwoPlayerLive.ps1"


def test_official_driver_exposes_an_explicit_reward_pickup_probe() -> None:
    source = DRIVER.read_text(encoding="utf-8")
    for marker in (
        "[switch]$RewardPickupProbe",
        "function Assert-WaveRewardPickup",
        "WAVE_REWARD_SPAWNED.*wave=1",
        "execute if items entity $Name container.* minecraft:cooked_beef",
        "minecraft:cooked_beef",
        "if ($RewardPickupProbe)",
        "OFFICIAL_WAVE_REWARD_PICKUP_PASS",
    ):
        assert marker in source


def test_reward_probe_checks_each_frozen_participant_and_preserves_the_normal_run() -> None:
    source = DRIVER.read_text(encoding="utf-8")
    assert "foreach ($name in $PlayerNames)" in source
    assert "Assert-WaveRewardPickup -Name $name" in source
    assert "Wait-LogRegex -Pattern 'WAVE_STARTED.*wave=2'" in source
    assert "WAVE_REWARD_PICKUP_PROBE" in source
