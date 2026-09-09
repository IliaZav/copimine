"""Regression contract for the official live driver's log polling loop."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DRIVER = ROOT / "tests/RunEndRiftOfficialTwoPlayerLive.ps1"


def test_wait_log_regex_rechecks_after_a_slow_during_wait_action() -> None:
    """A blocking RCON maintenance action must not hide a marker it caused."""

    source = DRIVER.read_text(encoding="utf-8")
    action_start = source.index("& $DuringWait")
    action_end = source.index("Start-Sleep -Milliseconds 500", action_start)
    action_window = source[action_start:action_end]

    assert "Read-NewPaperLog" in action_window
    assert "LogEvidence.Append" in action_window
    assert "Regex]::IsMatch" in action_window

