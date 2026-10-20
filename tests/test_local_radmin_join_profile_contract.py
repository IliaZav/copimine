from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RUNNER = ROOT / "tests" / "StartEndRiftLocalUserSession.ps1"


def test_local_runner_applies_radmin_join_network_profile_to_runtime_only() -> None:
    text = RUNNER.read_text(encoding="utf-8")

    assert "function Normalize-LocalRadminJoinNetworkProperties" in text
    assert "Set-LocalServerProperty -Key 'view-distance' -Value '6'" in text
    assert "Set-LocalServerProperty -Key 'simulation-distance' -Value '4'" in text
    assert "Set-LocalServerProperty -Key 'entity-broadcast-range-percentage' -Value '75'" in text
    assert "Set-LocalServerProperty -Key 'network-compression-threshold' -Value '256'" in text
    assert "Set-LocalServerProperty -Key 'allow-flight' -Value 'true'" in text
    assert "Normalize-LocalRadminJoinNetworkProperties" in text


def test_local_runner_does_not_edit_tracked_production_properties_for_join_profile() -> None:
    text = RUNNER.read_text(encoding="utf-8")

    profile_start = text.index("function Normalize-LocalRadminJoinNetworkProperties")
    profile_end = text.index("function Set-LocalPurpurProperty")
    profile = text[profile_start:profile_end]

    assert "minecraft\\server\\server.properties" not in profile.lower()
    assert "Copy-Item" not in profile
