from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RUNNER = ROOT / "tests" / "StartEndRiftLocalUserSession.ps1"


def test_local_runner_enables_alternate_keepalive_for_radmin_clients() -> None:
    text = RUNNER.read_text(encoding="utf-8")

    assert "function Set-LocalPurpurProperty" in text
    assert "Set-LocalPurpurProperty -Key 'use-alternate-keepalive' -Value 'true'" in text
    assert "Normalize-LocalPurpurProperties" in text

